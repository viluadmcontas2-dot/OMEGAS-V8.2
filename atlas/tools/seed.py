#!/usr/bin/env python3
import argparse, hashlib, json, re, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "atlas/manifests/canonical-inputs.json"

SEEDS = [
    "TAutoCalUI", "TAutoCalDM", "TFormRifAutocal",
    "ActionAutoCalRifExecute", "ActionAutoMatchExecute",
    "ActionFinishAutocalExecute", "CheckAutoCalEnableBeforeSetData",
    "MUL_ACT", "ACQUIRED_ZONES_PETROL", "ACQUIRED_ZONES_GAS",
    "VECT_AUTOCAL_U8_0", "VECT_AUTOCAL_U8_1", "VECT_AUTOCAL_U8_2",
    "MAX_RPM_FOR_AUTOCAL", "LabelNumAutoMatch", "EditNumAutomatch",
]

def sha256_path(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()

def verify_inputs():
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8-sig"))
    verified = []
    for row in manifest["inputs"]:
        path = ROOT / row["repo_path"]
        if not path.is_file():
            raise SystemExit(f"missing canonical input: {row['repo_path']}")
        expected = row.get("sha256") or row.get("archive_sha256")
        actual = sha256_path(path)
        if actual.lower() != expected.lower():
            raise SystemExit(f"hash mismatch: {row['id']} expected={expected} actual={actual}")
        item = {"id": row["id"], "repo_path": row["repo_path"], "sha256": actual, "size": path.stat().st_size}
        if row["kind"] == "zip+raw-log":
            with zipfile.ZipFile(path) as zf:
                info = zf.getinfo(row["raw_entry"])
                h = hashlib.sha256()
                with zf.open(info) as f:
                    for chunk in iter(lambda: f.read(1024 * 1024), b""):
                        h.update(chunk)
                raw_hash = h.hexdigest()
                if raw_hash.lower() != row["raw_sha256"].lower() or info.file_size != row["raw_size"]:
                    raise SystemExit(f"raw zip member mismatch: {row['id']}")
                item["raw_entry"] = row["raw_entry"]
                item["raw_sha256"] = raw_hash
                item["raw_size"] = info.file_size
        verified.append(item)
    return manifest, verified

def occurrences(data, needle):
    out = []
    pos = 0
    while True:
        pos = data.find(needle, pos)
        if pos < 0:
            return out
        out.append(pos)
        pos += max(1, len(needle))

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--output", default="atlas-out/seed.json")
    args = ap.parse_args()
    manifest, verified = verify_inputs()
    binary_row = next(x for x in manifest["inputs"] if x["kind"] == "executable")
    binary = ROOT / binary_row["repo_path"]
    data = binary.read_bytes()

    anchors = []
    for term in SEEDS:
        for encoding, needle in (("ascii", term.encode("ascii")), ("utf16le", term.encode("utf-16le"))):
            for off in occurrences(data, needle):
                anchors.append({"term": term, "encoding": encoding, "file_offset": off, "file_offset_hex": hex(off)})

    required = {"TAutoCalUI", "TAutoCalDM", "ActionAutoCalRifExecute", "ActionAutoMatchExecute", "MUL_ACT"}
    found = {x["term"] for x in anchors}
    missing = sorted(required - found)
    if missing:
        raise SystemExit("canonical AutoCal roots absent: " + ", ".join(missing))

    out = {
        "schema": "omegas.atlas.seed.v1",
        "binary_sha256": binary_row["sha256"],
        "verified_inputs": verified,
        "anchors": anchors,
        "required_roots": sorted(required),
        "required_roots_found": True,
    }
    path = ROOT / args.output
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(out, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"anchors": len(anchors), "required_roots": sorted(required)}, indent=2))

if __name__ == "__main__":
    main()
