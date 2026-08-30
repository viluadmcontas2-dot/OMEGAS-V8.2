#!/usr/bin/env python3
"""Reconstruct and fail-closed validate the split WU-006 episode fixture."""
from __future__ import annotations
import argparse
import base64
import gzip
import hashlib
import json
from pathlib import Path


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def reconstruct_fixture(parts_dir: Path, index_path: Path, output_path: Path) -> dict:
    index = json.loads(index_path.read_text(encoding="utf-8"))
    if index.get("schema") != "omegas-science-episode-fixture-index-v1":
        raise ValueError("unsupported fixture index schema")
    expected = index.get("parts") or []
    actual = sorted(parts_dir.glob(index["part_glob"]))
    if len(actual) != index["part_count"] or len(expected) != index["part_count"]:
        raise ValueError("fixture part count mismatch")
    chunks = []
    for path, spec in zip(actual, expected):
        if path.name != spec["name"]:
            raise ValueError("fixture part order/name mismatch")
        raw = path.read_bytes()
        if len(raw) != index["part_chars"]:
            raise ValueError(f"fixture part length mismatch: {path.name}")
        if _sha256(raw) != spec["sha256"]:
            raise ValueError(f"fixture part sha256 mismatch: {path.name}")
        chunks.append(raw)
    compressed = base64.b64decode(b"".join(chunks), validate=True)
    if len(compressed) != index["compressed_bytes"] or _sha256(compressed) != index["compressed_sha256"]:
        raise ValueError("reconstructed compressed fixture mismatch")
    uncompressed = gzip.decompress(compressed)
    if len(uncompressed) != index["uncompressed_bytes"] or _sha256(uncompressed) != index["uncompressed_sha256"]:
        raise ValueError("reconstructed uncompressed fixture mismatch")
    lines = [line for line in uncompressed.splitlines() if line]
    if len(lines) != index["episode_lines"]:
        raise ValueError("fixture episode line count mismatch")
    for line in lines:
        json.loads(line)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(compressed)
    return {
        "compressed_bytes": len(compressed),
        "compressed_sha256": _sha256(compressed),
        "uncompressed_bytes": len(uncompressed),
        "uncompressed_sha256": _sha256(uncompressed),
        "episode_lines": len(lines),
    }


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("parts_dir", type=Path)
    p.add_argument("index", type=Path)
    p.add_argument("output", type=Path)
    a = p.parse_args()
    print(json.dumps(reconstruct_fixture(a.parts_dir, a.index, a.output), sort_keys=True, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
