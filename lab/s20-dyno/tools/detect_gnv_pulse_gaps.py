#!/usr/bin/env python3
"""Somente-leitura: detecta janelas de pulso GNV ausente com o estado GNV ativo.

O detector é investigativo. Nunca diagnostica defeito nem ordena escrita à ECU.
Não inclui nem exporta quadros rawHex no relatório produzido.
"""
from __future__ import annotations

import argparse
import json
import statistics
import zipfile

MAX_GAP_NS = 150_000_000
MIN_WINDOW_NS = 180_000_000
MIN_FRAMES = 5


def median(values):
    return round(statistics.median(values), 3)


def recognize(rows):
    frames = []
    for item in rows:
        if item.get("source") != "mp48":
            continue
        data = item.get("data") or {}
        stamp = item.get("mono_ns")
        if not isinstance(stamp, int):
            continue
        try:
            raw = bytes.fromhex(data["rawHex"])
        except (ValueError, KeyError, TypeError):
            continue
        if len(raw) != 34:
            continue
        frames.append((stamp, data, raw))
    frames.sort(key=lambda x: x[0])
    return frames


def _category(rpm, map_bar, petrol_ms):
    # SOMENTE uma hipótese descritiva: jamais interpretar como diagnóstico.
    if rpm >= 1500 and map_bar < 0.30 and petrol_ms < 2.2:
        return "COMPATIVEL_COM_CORTE_EM_DESACELERACAO"
    if rpm <= 1100 and map_bar >= 0.50 and petrol_ms >= 4.0:
        return "REVISAR_INTERRUPCAO_GNV_PROXIMA_DA_MARCHA_LENTA"
    return "INTERRUPCAO_SEM_CAUSA_CLASSIFICADA"


def _summary(segment, first_timestamp):
    timestamps = [t for t, _, _ in segment]
    data = [d for _, d, _ in segment]
    rpm = [d["rpm"] for d in data]
    map_bar = [d["mapBar"] for d in data]
    petrol_ms = [d["petrolMs"] for d in data]
    pressure = [d["gasPressureBar"] for d in data]
    return {
        "start_after_first_mp48_seconds": round((timestamps[0] - first_timestamp) / 1e9, 3),
        "duration_seconds": round((timestamps[-1] - timestamps[0]) / 1e9, 3),
        "frames_both_gnv_banks_zero": len(segment),
        "fuel_status": "0x90/0xB0",
        "rpm_min": min(rpm),
        "rpm_median": median(rpm),
        "rpm_max": max(rpm),
        "map_bar_median": median(map_bar),
        "petrol_command_ms_median": median(petrol_ms),
        "gas_pressure_bar_first": round(pressure[0], 3),
        "gas_pressure_bar_last": round(pressure[-1], 3),
        "interpretation": _category(median(rpm), median(map_bar), median(petrol_ms)),
        "certainty": "HIPOTESE; sem prova de causa ou defeito",
    }


def detect(rows, *, max_gap_ns=MAX_GAP_NS, min_window_ns=MIN_WINDOW_NS, min_frames=MIN_FRAMES):
    frames = recognize(rows)
    if not frames:
        return {"schema": "omegas.s20.pulse-windows.v1", "windows": [], "mp48_frames": 0}
    first_time = frames[0][0]
    segments = []
    running = []
    previous_t = None

    def finalize():
        if (len(running) >= min_frames and
            running[-1][0] - running[0][0] >= min_window_ns):
            segments.append(_summary(running, first_time))

    for t, d, b in frames:
        state_gnv = b[11] in (0x90, 0xB0)
        zero_both = b[6] == 0 and b[7] == 0 and b[24] == 0 and b[25] == 0
        valid_data = all(isinstance(d.get(k), (int, float)) for k in
                         ("rpm", "mapBar", "petrolMs", "gasPressureBar"))
        active = state_gnv and zero_both and valid_data
        if running and (not active or previous_t is None or t - previous_t > max_gap_ns):
            finalize()
            running = []
        if active:
            running.append((t, d, b))
        previous_t = t
    if running:
        finalize()
    return {
        "schema": "omegas.s20.pulse-windows.v1",
        "source": "captura real MP48; preservados apenas agregados",
        "logic": "GNV ativo, bancos GNV 1 e 2 com pulso zero, frames consecutivos",
        "warning": "Classe é HEURISTICA de triagem; não comprova apagão ou causa; não aplicar calibração",
        "mp48_frames": len(frames),
        "windows": segments,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("exported_zip", help="Arquivo ZIP do app preservado no S20")
    parser.add_argument("--out", help="JSON apenas com agregados (sem dados brutos)")
    args = parser.parse_args()
    with zipfile.ZipFile(args.exported_zip) as zipfile_obj:
        assert zipfile_obj.testzip() is None, "ZIP inválido"
        data = json.loads(zipfile_obj.read("dyno-vault.json"))
    out = json.dumps(detect(data.get("observations", [])), ensure_ascii=False, indent=2)
    if args.out:
        from pathlib import Path
        path = Path(args.out)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(out + "\n", encoding="utf-8")
    print(out)


if __name__ == "__main__":
    main()
