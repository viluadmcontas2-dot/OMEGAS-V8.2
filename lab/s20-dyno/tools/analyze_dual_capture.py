#!/usr/bin/env python3
"""Auditoria somente-leitura do ZIP exportado pelo laboratório S20.

Sem dependências externas. Emite apenas agregados; jamais publica amostras brutas.
A medição temporal correlaciona chegada de respostas, não instantaneidade dos sensores.
"""
from __future__ import annotations
import argparse
import bisect
import collections
import json
from pathlib import Path
import statistics
import zipfile

MAX_PAIR_NS = 200_000_000
FIELDS = (
    ("rpm", "rpm", lambda data, raw: data.get("rpm"), "rpm"),
    ("mapKpa", "mapBar", lambda data, raw: data.get("mapBar", 0) * 100, "kPa"),
    ("ect", "waterCandidate", lambda data, raw: 109 - raw[12], "°C"),
)


def median(nums):
    return round(statistics.median(nums), 3) if nums else None


def percentile(nums, portion):
    s = sorted(nums)
    return round(s[int((len(s) - 1) * portion)], 3) if s else None


def summarize_numeric(nums):
    if not nums:
        return {"n": 0}
    return {
        "n": len(nums), "min": round(min(nums), 3),
        "median": median(nums), "max": round(max(nums), 3),
    }


def decode_rows(zipped):
    with zipfile.ZipFile(zipped) as archive:
        assert archive.testzip() is None, "Arquivo ZIP danificado"
        root = json.loads(archive.read("dyno-vault.json"))
    assert root.get("schemaVersion") == 1, "Versão do formato desconhecida"
    rows = root.get("observations")
    assert isinstance(rows, list), "Sem observações"
    obd = []
    mp48 = []
    invalid_mp48 = 0
    for row in rows:
        source = row.get("source")
        if source not in ("obd", "mp48"):
            continue
        data = row.get("data") or {}
        stamp = row.get("mono_ns")
        if not isinstance(stamp, int):
            continue
        if source == "obd":
            obd.append((stamp, data))
        else:
            try:
                raw = bytes.fromhex(data["rawHex"])
                if len(raw) != 34:
                    raise ValueError("Tamanho inesperado")
                mp48.append((stamp, data, raw))
            except (KeyError, ValueError, TypeError):
                invalid_mp48 += 1
    return root, sorted(obd), sorted(mp48), invalid_mp48


def closest(mp48, times, stamp):
    idx = bisect.bisect_left(times, stamp)
    candidate = min(
        (j for j in (idx - 1, idx) if 0 <= j < len(times)),
        key=lambda j: abs(times[j] - stamp), default=None,
    )
    if candidate is None or abs(times[candidate] - stamp) > MAX_PAIR_NS:
        return None
    return mp48[candidate]


def analyze(zipped):
    root, obd, mp48, invalid = decode_rows(zipped)
    report = {
        "schema": "omegas.s20.dual-evidence.v1",
        "classification": "ANALISE_LOCAL; nao é comando à ECU",
        "source_file": Path(zipped).name,
        "sessions": len(root.get("sessions", [])),
        "total_observations": len(root["observations"]),
        "obd_observations": len(obd),
        "mp48_observations": len(mp48),
        "invalid_mp48_raw": invalid,
        "caution": [
            "timestamps correspondem à recepção no S20, não ao instante físico da amostra",
            "correlação de bytes não prova semântica",
            "não executar escrita, reset, autocal ou ajuste na central",
        ],
    }
    if mp48:
        times = [t for t, _, _ in mp48]
        dt_ms = [(b - a) / 1e6 for a, b in zip(times, times[1:])]
        report["mp48_cadence_ms"] = {
            "median": median(dt_ms), "p95": percentile(dt_ms, 0.95),
            "max": max(dt_ms) if dt_ms else None,
        }
        flags = collections.defaultdict(list)
        for t, data, raw in mp48:
            flag = raw[11]
            flags[flag].append((t, data, raw))
        report["fuel_states"] = {}
        for flag, rows in sorted(flags.items()):
            gas_pulse = sum(int(raw[6] != 0 or raw[7] != 0) for _, _, raw in rows)
            report["fuel_states"][f"0x{flag:02X}"] = {
                "frames": len(rows), "gas_pulse_frames": gas_pulse,
                "first_mono_ns": rows[0][0], "last_mono_ns": rows[-1][0],
            }
        shifts = []
        for a, b in zip(mp48, mp48[1:]):
            if a[2][11] != b[2][11]:
                shifts.append({"from": f"0x{a[2][11]:02X}",
                               "to": f"0x{b[2][11]:02X}",
                               "after_first_s": round((b[0] - times[0]) / 1e9, 3)})
        report["fuel_state_changes"] = shifts
        for name, extractor in (
            ("byte12_water_candidate_C", lambda raw: 109 - raw[12]),
            ("byte13_level_raw", lambda raw: raw[13]),
            ("byte16_gas_candidate_C", lambda raw: raw[16] - 20),
            ("byte19_unknown_raw", lambda raw: raw[19]),
        ):
            report[name] = summarize_numeric([extractor(raw) for _, _, raw in mp48])
        report["byte19_by_fuel_state"] = {
            f"0x{flag:02X}": summarize_numeric([raw[19] for _, _, raw in rows])
            for flag, rows in sorted(flags.items())
        }
        comparisons = {}
        for obd_field, display_name, mp_decoder, units in FIELDS:
            pairs = []
            for t, data in obd:
                measured = data.get(obd_field)
                if not isinstance(measured, (int, float)):
                    continue
                near = closest(mp48, times, t)
                if near is None:
                    continue
                mt, md, raw = near
                pairs.append((float(measured), float(mp_decoder(md, raw)), abs(t - mt) / 1e6))
            differences = [b - a for a, b, _ in pairs]
            comparisons[display_name] = {
                "units": units, "pairs": len(pairs),
                "signed_difference_median": median(differences),
                "absolute_difference_median": median([abs(x) for x in differences]),
                "absolute_difference_p90": percentile([abs(x) for x in differences], 0.90),
                "arrival_alignment_ms_median": median([x for _, _, x in pairs]),
            }
        report["cross_source_comparisons"] = comparisons
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("zip", type=Path, help="ZIP já exportado do Dinamômetro")
    parser.add_argument("--out", type=Path, help="JSON agregado; nunca bytes crus")
    args = parser.parse_args()
    result = analyze(args.zip)
    rendered = json.dumps(result, ensure_ascii=False, indent=2)
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)


if __name__ == "__main__":
    main()
