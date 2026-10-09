#!/usr/bin/env python3
"""LOGNOVO original -> replay passivo de telemetria MP48.

Usa o parser canônico do repositório, sem abrir serial/USB. A ordem dos eventos
e os valores RPM, MAP, injeções e combustível são reais; a cadência é SIMULADA
porque os timestamps Portmon deste formato não demonstram tempo de condução.
Nunca publica o LOG bruto nem gera comandos de ECU.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from scripts.omegas.portmon_parser import iter_events, iter_transactions, parse  # noqa: E402

RAW_SHA = "43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64"
ZIP_SHA = "6879fa2a7931d22c207cd7fa47dffb59e1df0fe1de216e34e3f11e0c08cc1c17"
SCHEMA = "omegas.lognovo.real-live.v1"
TELEMETRY_REQUEST = "48 01 49"
STATUS_REQUEST = "48 0B 53"
RESET_REQUEST = "02 24 04 04 2E"


def hash_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def payload(request: str, response: str, required_size: int) -> bytes | None:
    try:
        req, res = bytes.fromhex(request), bytes.fromhex(response)
    except ValueError:
        return None
    if not res.startswith(req):
        return None
    suffix = res[len(req):]
    if (len(suffix) != required_size + 3 or suffix[0] != 0x53
        or suffix[1] != required_size or sum(suffix[:-1]) & 0xFF != suffix[-1]):
        return None
    return suffix[2:-1]


def decode_live(request: str, response: str) -> dict | None:
    if request != TELEMETRY_REQUEST:
        return None
    raw = payload(request, response, 34)
    if raw is None:
        return None
    u16 = lambda off: int.from_bytes(raw[off:off+2], "little")
    rpm = u16(0)
    petrol = u16(8) * 0.00256
    gas_raw = u16(6)
    gas = gas_raw * 0.00256 if gas_raw > 0 else None
    mapbar = int.from_bytes(raw[17:19], "little", signed=True) / 1000.0
    fuel_byte = raw[11]
    if rpm <= 0 or fuel_byte == 0:
        fuel = "DESLIGADO"
    elif rpm >= 1200 and petrol < 0.7 and gas_raw == 0 and mapbar < 0.35:
        fuel = "CUTOFF"
    else:
        fuel = {0x80:"GASOLINA", 0xA0:"GASOLINA", 0x90:"GNV",
                0xB0:"GNV", 0x88:"TRANSICAO", 0xA8:"TRANSICAO"}.get(
                    fuel_byte, "DESCONHECIDO")
    return {
        "rpm": rpm, "petrol_ms": round(petrol, 5),
        "gas_ms_diagnostic": round(gas, 5) if gas is not None else None,
        "load_bar": round(mapbar, 4), "fuel": fuel,
        "gas_c": raw[16] - 20, "water_c": 109 - raw[12],
    }


def transactions(source: Path):
    if source.suffix.lower() == ".zip":
        if hash_file(source) != ZIP_SHA:
            raise ValueError("Arquivo ZIP diferente do LOGNOVO canônico: abortando")
        with zipfile.ZipFile(source) as archive:
            names = [item.filename for item in archive.infolist() if not item.is_dir()]
            if len(names) != 1 or Path(names[0]).name not in ("PortmonLOGNOVO.LOG",):
                raise ValueError("ZIP não contém exatamente PortmonLOGNOVO.LOG")
            with archive.open(names[0]) as stream:
                digest = hashlib.sha256()
                def lines():
                    for raw in stream:
                        digest.update(raw)
                        yield raw.decode("utf-8", errors="replace")
                yield from iter_transactions(iter_events(lines()))
                if digest.hexdigest() != RAW_SHA:
                    raise ValueError("Conteúdo bruto LOGNOVO não confere com SHA canônico")
    elif source.suffix.lower() == ".log":
        if hash_file(source) != RAW_SHA:
            raise ValueError("LOG original não confere com SHA canônico")
        yield from parse(source)
    else:
        raise ValueError("Entrada deve ser PortmonLOGNOVO.LOG ou seu ZIP original")


def build(source: Path, *, max_frames: int | None = None, cadence_ms: int = 300) -> dict:
    if cadence_ms < 1 or max_frames is not None and max_frames < 2:
        raise ValueError("Parâmetros de replay inválidos")
    frames, counts, counter_changes = [], Counter(), []
    telemetry_bad = 0
    old_count = None
    full_transactions = 0
    for tx in transactions(source):
        full_transactions += 1
        if tx.request_hex == TELEMETRY_REQUEST:
            decoded = decode_live(tx.request_hex, tx.response_hex)
            if decoded is None:
                telemetry_bad += 1
            else:
                counts[decoded["fuel"]] += 1
                frames.append({"source_sequence": tx.sequence,
                               "source_event": tx.request_index, **decoded})
        elif tx.request_hex == STATUS_REQUEST:
            buf = payload(tx.request_hex, tx.response_hex, 14)
            if buf is not None:
                n = buf[13]
                if old_count is not None and n != old_count:
                    counter_changes.append({"source_sequence": tx.sequence,
                                            "before": old_count, "after": n})
                old_count = n
    total = len(frames)
    if total < 2:
        raise ValueError("Captura sem dois quadros reais de telemetria")
    if max_frames and total > max_frames:
        positions = [(i * (total - 1)) // (max_frames - 1) for i in range(max_frames)]
        frames = [frames[i] for i in positions]
    for i, frame in enumerate(frames):
        # Tempo de reprodução, explicitamente NÃO medição de tempo do Portmon.
        frame["dt"] = i * cadence_ms
        frame["t"] = frame["dt"]
    return {
        "schema": SCHEMA,
        "classification": "ORIGINAL_VALUES_SIMULATED_CLOCK",
        "source": {"rawSha256": RAW_SHA, "zipSha256": ZIP_SHA, "sourceKind":"ProgBase MP48 PortmonLOGNOVO"},
        "timebase": {"kind":"SYNTHETIC_FIXED_CADENCE", "cadenceMs":cadence_ms,
                     "warning":"A ordem é real; o tempo entre leituras não está comprovado pelo Portmon."},
        "transactions": full_transactions, "telemetryValid":total,
        "telemetryInvalid":telemetry_bad, "fuelCounts":dict(counts),
        "nativeAutoMatchCountTransitions":counter_changes,
        "framesRetained":len(frames), "downsampled":len(frames)!=total,
        "frames":frames,
    }


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("source", type=Path)
    p.add_argument("output", type=Path)
    p.add_argument("--max-frames", type=int, default=None)
    p.add_argument("--cadence-ms", type=int, default=300)
    args = p.parse_args()
    result = build(args.source, max_frames=args.max_frames, cadence_ms=args.cadence_ms)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, separators=(",", ":"))+"\n", encoding="utf-8")
    print(json.dumps({key:result[key] for key in ("schema","transactions","telemetryValid","telemetryInvalid","fuelCounts","framesRetained","downsampled","nativeAutoMatchCountTransitions")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
