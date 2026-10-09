#!/usr/bin/env python3
"""Read-only local preview server for the real OMEGAS UI (not the APK).

Never listens on a public interface and never opens serial/USB devices.
All API data comes from the repository's existing render fixtures.
"""
from __future__ import annotations

import argparse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import subprocess
import sys
import tempfile
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[2]
STATIC = (
    "tools/ui_studio/",
    "app/src/main/assets/ui/",
    "tests/ui/render/mock-bridge.js",
)


def load_fixture() -> bytes:
    with tempfile.TemporaryDirectory(prefix="omegascinza-studio-") as tmp:
        out = Path(tmp) / "preview.json"
        subprocess.run(
            [sys.executable, str(ROOT / "tests/ui/render/prep.py"), str(out)],
            cwd=ROOT, stdout=subprocess.DEVNULL, check=True, timeout=30,
        )
        data = json.loads(out.read_text(encoding="utf-8"))
        if not data.get("frames") or not data.get("snapshot"):
            raise RuntimeError("Fixture do render original está incompleta")
        return json.dumps(data, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


class Handler(SimpleHTTPRequestHandler):
    fixture = b""

    def do_GET(self):
        path = unquote(urlsplit(self.path).path)
        if path == "/__studio/health":
            return self._json(b'{"ok":true,"mode":"ECU_SIMULATED","readOnly":true}')
        if path == "/__studio/data.json":
            return self._json(self.fixture)
        if path in ("/", "/tools/ui_studio"):
            path = "/tools/ui_studio/index.html"
        relative = path.lstrip("/")
        if not any(relative.startswith(prefix) for prefix in STATIC):
            self.send_error(404, "Fora do ambiente de prévia")
            return
        resolved = (ROOT / relative).resolve()
        allowed = (
            resolved.is_relative_to(ROOT / "tools/ui_studio")
            or resolved.is_relative_to(ROOT / "app/src/main/assets/ui")
            or resolved == ROOT / "tests/ui/render/mock-bridge.js"
        )
        if not allowed or ".git" in resolved.parts:
            self.send_error(403, "Caminho indisponível")
            return
        self.path = "/" + relative
        super().do_GET()

    def do_HEAD(self):
        self.send_error(405, "Use GET dentro das rotas permitidas")

    def _json(self, body: bytes):
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def end_headers(self):
        self.send_header("X-Content-Type-Options", "nosniff")
        super().end_headers()


def main():
    parser = argparse.ArgumentParser(description="OMEGASCINZA oficina visual local")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    Handler.fixture = load_fixture()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), partial(Handler, directory=str(ROOT)))
    print(f"OMEGASCINZA_STUDIO_READY http://127.0.0.1:{args.port}/tools/ui_studio/index.html", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
