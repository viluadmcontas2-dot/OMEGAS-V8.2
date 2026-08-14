#!/usr/bin/env python3
"""Build a Netlify-ready directory containing only OMEGAS NEXT UI assets.

This tool never deploys. It only copies the approved ui-next tree after verifying
that Android/native/protocol/APK/credential/log files are absent from the package.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/assets/ui-next"
SCOPE = SOURCE / "netlify-scope.json"
DEFAULT_OUTPUT = ROOT / "build/netlify-ui-next"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description="Package OMEGAS NEXT UI for a directory-only Netlify deploy")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    scope = json.loads(SCOPE.read_text(encoding="utf-8"))
    forbidden_ext = {item.lower() for item in scope["forbiddenExtensions"]}
    forbidden_names = {item.lower() for item in scope["forbiddenNames"]}
    allowed_ext = {item.lower() for item in scope["allowedExtensions"]}

    candidates = [path for path in SOURCE.rglob("*") if path.is_file()]
    violations: list[str] = []
    for path in candidates:
        relative = path.relative_to(SOURCE)
        suffix = path.suffix.lower()
        if path.name.lower() in forbidden_names:
            violations.append(f"forbidden-name:{relative}")
        if suffix in forbidden_ext:
            violations.append(f"forbidden-extension:{relative}")
        if suffix not in allowed_ext:
            violations.append(f"not-allowlisted:{relative}")

    if violations:
        raise SystemExit("Netlify package blocked:\n" + "\n".join(sorted(violations)))

    output = args.output.resolve()
    if output == ROOT or ROOT in output.parents and output == SOURCE.resolve():
        raise SystemExit("Output must be a separate package directory, never the repository root/source tree")
    if output.exists():
        shutil.rmtree(output)
    shutil.copytree(SOURCE, output)

    manifest = {
        "schema": "omegas-next-netlify-package-v1",
        "sourceRoot": str(SOURCE.relative_to(ROOT)),
        "servedContent": "UI_AND_FICTIONAL_FIXTURES_ONLY",
        "deploymentMode": scope["deploymentMode"],
        "fileCount": len(candidates),
        "files": [
            {
                "path": str(path.relative_to(SOURCE)).replace("\\", "/"),
                "bytes": path.stat().st_size,
                "sha256": sha256(path),
            }
            for path in sorted(candidates)
        ],
    }
    (output / "package-manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"ok": True, "output": str(output), "fileCount": len(candidates)}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
