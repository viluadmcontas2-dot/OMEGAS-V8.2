#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, re
from pathlib import Path

def is_target_class(name: str) -> bool:
    return "autocal" in name.lower()

CLASS_RE = re.compile(r"^  (T\w+) — size=(\d+)B, vmt=(0x[0-9a-fA-F]+)")
METHOD_RE = re.compile(r"^      (0x[0-9a-fA-F]+)\s+(.+?)\s*$")
SECTION_END_RE = re.compile(r"^    (?:virtual methods|instance layout|published properties|interfaces)")

def extract(text: str):
    current = None
    in_published = False
    classes = {}
    methods = []
    for line in text.splitlines():
        m = CLASS_RE.match(line)
        if m:
            current = m.group(1)
            in_published = False
            if is_target_class(current):
                classes[current] = {
                    "size": int(m.group(2)),
                    "vmt": int(m.group(3), 16),
                    "vmt_hex": m.group(3).lower(),
                    "published_methods": [],
                }
            continue
        if not is_target_class(current):
            continue
        if "published methods (" in line:
            in_published = True
            continue
        if SECTION_END_RE.match(line):
            in_published = False
        if in_published:
            mm = METHOD_RE.match(line)
            if mm:
                va = int(mm.group(1), 16)
                name = mm.group(2)
                item = {
                    "class": current,
                    "name": name,
                    "va": va,
                    "va_hex": f"0x{va:08x}",
                }
                classes[current]["published_methods"].append(item)
                methods.append(item)
    return classes, methods

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", type=Path, required=True)
    ap.add_argument("--seeds", type=Path, required=True)
    ap.add_argument("--json", type=Path, required=True)
    args = ap.parse_args()
    classes, methods = extract(args.input.read_text(encoding="utf-8", errors="replace"))
    required = {"TAutoCalDM", "TAutoCalUI", "TFormRifAutocal"}
    missing = sorted(required - set(classes))
    if missing:
        raise SystemExit("undelphi missing required AutoCal classes: " + ", ".join(missing))
    required_methods = {"ActionAutoCalRifExecute", "ActionAutoMatchExecute", "ActionFinishAutocalExecute", "ChartDataAfterDraw"}
    names = {m["name"] for m in methods}
    missing_methods = sorted(required_methods - names)
    if missing_methods:
        raise SystemExit("undelphi missing required AutoCal handlers: " + ", ".join(missing_methods))
    args.seeds.parent.mkdir(parents=True, exist_ok=True)
    with args.seeds.open("w", encoding="utf-8") as f:
        for va in sorted({m["va"] for m in methods}):
            f.write(f"0x{va:08x}\n")
    payload = {
        "schema": "omegas.atlas.undelphi-seeds.v1",
        "classes": classes,
        "method_count": len(methods),
        "seed_count": len({m["va"] for m in methods}),
    }
    args.json.parent.mkdir(parents=True, exist_ok=True)
    args.json.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({
        "classes": sorted(classes),
        "method_count": len(methods),
        "seed_count": payload["seed_count"],
        "required_handlers": sorted(required_methods),
    }, indent=2))

if __name__ == "__main__":
    main()
