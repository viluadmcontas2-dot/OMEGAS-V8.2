#!/usr/bin/env python3
"""DERIVADO: regenera o protocolo usando os extratores existentes, sem executar o programa.

Entradas privadas explícitas; saídas determinísticas para as mesmas entradas e revisão do app.
A validação das instruções ocorre antes de qualquer catálogo dependente.
"""
import argparse
from pathlib import Path
import subprocess
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]


def gerar(exe, logs, destino, repo=ROOT):
    exe, destino, repo = Path(exe), Path(destino), Path(repo)
    logs = [(label, Path(path)) for label, path in logs]
    if not logs:
        raise ValueError('Informe ao menos uma captura ROTULO=caminho')
    labels = [label for label, _ in logs]
    if any(not label for label in labels) or len(set(labels)) != len(labels):
        raise ValueError('Rótulos de captura devem ser únicos e não vazios')
    for path in [exe, *(path for _, path in logs)]:
        if not path.is_file():
            raise FileNotFoundError(path)
    destino.mkdir(parents=True, exist_ok=True)

    def executar(script, args, stem):
        subprocess.run([sys.executable, str(HERE / (script + '.py')), *map(str, args),
                        '--json', str(destino / (stem + '.json')),
                        '--md', str(destino / (stem + '.md'))], check=True)

    executar('protocolo_comandos', ['--exe', exe, '--log', logs[0][1]], 'comandos')
    executar('protocolo_vetores', ['--exe', exe, '--log', logs[0][1]], 'vetores')
    executar('protocolo_matriz', [arg for label, path in logs for arg in ('--log', f'{label}={path}')],
             'matriz-comando-resposta')
    executar('protocolo_backlog', ['--repo', repo, '--vetores', destino / 'vetores.json',
                                  '--comandos', destino / 'comandos.json'], 'backlog')


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--exe', required=True)
    parser.add_argument('--log', action='append', required=True, help='ROTULO=caminho; primeiro alimenta vetores/comandos')
    parser.add_argument('--destino', type=Path, default=ROOT / 'docs/progbase/protocolo')
    parser.add_argument('--repo', type=Path, default=ROOT)
    args = parser.parse_args(argv)
    logs = []
    for item in args.log:
        label, separator, path = item.partition('=')
        if not separator or not label or not path:
            parser.error('--log requer ROTULO=caminho')
        logs.append((label, path))
    gerar(args.exe, logs, args.destino, args.repo)
    return 0


if __name__ == '__main__':
    sys.exit(main())
