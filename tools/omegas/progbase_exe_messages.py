#!/usr/bin/env python3
"""Extrai do ProgBase.exe (dump PE) as mensagens do fabricante sobre AutoCal.

O ProgBase 4.2.0.6 embute uma tabela de traduções em JSON (`{"LBL": [{"id", "ita",
"eng", ...}]}`) dentro do próprio executável. Este relatório passivo localiza essa
tabela, imprime as mensagens de AutoCal/Export to K e confere por padrão de bytes
que o código do `TAutoCalUI` empilha os índices dessas mensagens (`push imm32`).

Fonte do incidente: docs/incidents/2026-10-03-curva-k-perde-efeito-ao-pausar-ou-desconectar.md
Nunca abre porta serial, USB ou rede. Entrada: `ProgBase.exe.Dump.bin` (pasta DUMP).
"""
from __future__ import annotations

import argparse
import hashlib
import json
import struct
import sys
from pathlib import Path

KEY = b'{\n  "LBL": ['
# Mensagens citadas no incidente e o campo de texto que o TAutoCalUI preenche com elas.
MESSAGES = (
    "CHECKAUTOCALENABLE",
    "AUTOCALWARNINGDISABLE",
    "AUTOCALFORCEFINISH",
    "MESSEXPORTKTOMAP",
    "AUTOCALRESETKFACTOR",
    "AUTOCALEXPORTTOFILE",
    "AUTOCALSWITCHTOGAS",
    "AUTOCALWARNINGDOWNLOAD",
)
# Região do TAutoCalUI no .text (VA) onde as mensagens são carregadas nos campos.
UI_VA = (0x512000, 0x51B300)


def pe_sections(data: bytes):
    pe = struct.unpack_from("<I", data, 0x3C)[0]
    count = struct.unpack_from("<H", data, pe + 6)[0]
    opt_size = struct.unpack_from("<H", data, pe + 20)[0]
    image_base = struct.unpack_from("<I", data, pe + 24 + 28)[0]
    table = pe + 24 + opt_size
    rows = []
    for index in range(count):
        name, vsize, va, rsize, raw = struct.unpack_from("<8sIIII", data, table + 40 * index)[:5]
        rows.append((name.rstrip(b"\0").decode(), image_base + va, max(vsize, rsize), raw))
    return rows


def va_to_offset(sections, va: int):
    for _, base, size, raw in sections:
        if base <= va < base + size:
            return raw + va - base
    return None


def load_table(data: bytes) -> tuple[int, list[dict]]:
    start = data.find(KEY)
    if start < 0:
        raise SystemExit("tabela LBL não encontrada neste arquivo")
    text = data[start : start + 4_000_000].decode("utf-8", "replace")
    depth, in_string, escaped = 0, False, False
    for position, char in enumerate(text):
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return start, json.loads(text[: position + 1])["LBL"]
    raise SystemExit("JSON da tabela LBL não fechou")


def report(path: Path) -> dict:
    data = path.read_bytes()
    start, table = load_table(data)
    ids = [str(entry.get("id")) for entry in table]
    sections = pe_sections(data)
    text_section = next(row for row in sections if row[0] == ".text")
    low = va_to_offset(sections, UI_VA[0])
    high = va_to_offset(sections, UI_VA[1])
    ui_code = data[low:high]
    rows = []
    for message_id in MESSAGES:
        if message_id not in ids:
            continue
        index = ids.index(message_id)
        entry = table[index]
        pattern = b"\x68" + struct.pack("<I", index)  # push imm32 <índice>
        rows.append(
            {
                "id": message_id,
                "index": index,
                "ita": entry.get("ita"),
                "eng": entry.get("eng"),
                "pushedInTAutoCalUI": [hex(UI_VA[0] + hit) for hit in _find_all(ui_code, pattern)],
            }
        )
    return {
        "schema": "omegas-progbase-exe-messages-v1",
        "file": path.name,
        "sha256": hashlib.sha256(data).hexdigest(),
        "tableOffset": hex(start),
        "tableEntries": len(table),
        "textSection": {"va": hex(text_section[1]), "size": hex(text_section[2])},
        "messages": rows,
    }


def _find_all(blob: bytes, pattern: bytes):
    position = blob.find(pattern)
    while position >= 0:
        yield position
        position = blob.find(pattern, position + 1)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("dump", type=Path, help="ProgBase.exe.Dump.bin")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    result = report(args.dump)
    if args.json:
        print(json.dumps(result, indent=2, ensure_ascii=False))
        return 0
    print(f"{result['file']} sha256={result['sha256']}")
    print(f"tabela LBL em {result['tableOffset']} com {result['tableEntries']} entradas")
    for row in result["messages"]:
        print(f"\n[{row['id']}] índice {row['index']} ({hex(row['index'])}) push no TAutoCalUI: {row['pushedInTAutoCalUI'] or '-'}")
        print(f"  ita: {row['ita']}")
        print(f"  eng: {row['eng']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
