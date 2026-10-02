#!/usr/bin/env python3
"""Gera corpus determinístico do PortmonAUTOCAL.LOG, sem acessar hardware."""
from __future__ import annotations
import argparse, gzip, hashlib, json
from dataclasses import asdict
from pathlib import Path
from scripts.omegas.portmon_parser import parse

def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()

def response_checksum_valid(request_hex: str, response_hex: str) -> bool:
    request, response = bytes.fromhex(request_hex), bytes.fromhex(response_hex)
    if not response.startswith(request) or len(response) <= len(request) + 1:
        return False
    body = response[len(request):]
    return sum(body[:-1]) & 0xFF == body[-1]

def build(input_path: Path, output_dir: Path, chunk_size: int = 10_000) -> dict:
    output_dir.mkdir(parents=True, exist_ok=True)
    transactions = [asdict(item) for item in parse(input_path)]
    jsonl = output_dir / 'transactions.jsonl'
    with jsonl.open('w', encoding='utf-8', newline='\n') as stream:
        for item in transactions:
            stream.write(json.dumps(item, sort_keys=True, separators=(',', ':')) + '\n')
    lines, chunks = jsonl.read_bytes().splitlines(keepends=True), []
    for offset in range(0, len(lines), chunk_size):
        path = output_dir / f'portmon-transactions-{offset // chunk_size + 1:04d}.jsonl.gz'
        with path.open('wb') as target:
            with gzip.GzipFile(filename='', mode='wb', fileobj=target, mtime=0) as compressed:
                compressed.write(b''.join(lines[offset:offset + chunk_size]))
        chunks.append({'file':path.name,'first':offset+1,'last':min(len(lines),offset+chunk_size),'transactions':min(chunk_size,len(lines)-offset),'bytes':path.stat().st_size,'sha256':sha256(path)})
    valid = sum(response_checksum_valid(item['request_hex'], item['response_hex']) for item in transactions)
    manifest = {'schema':'omegas-portmon-full-corpus-v1','source':{'file':input_path.name,'bytes':input_path.stat().st_size,'sha256':sha256(input_path)},'transactions':len(transactions),'validResponseChecksum':valid,'exceptions':len(transactions)-valid,'checksumRule':'sum(response bytes after echoed request, excluding final checksum) modulo 256','chunks':chunks}
    (output_dir / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    return manifest

def main() -> int:
    parser = argparse.ArgumentParser(); parser.add_argument('input', type=Path); parser.add_argument('output', type=Path); parser.add_argument('--chunk-size', type=int, default=10_000)
    args = parser.parse_args(); print(json.dumps(build(args.input,args.output,args.chunk_size), indent=2)); return 0

if __name__ == '__main__': raise SystemExit(main())
