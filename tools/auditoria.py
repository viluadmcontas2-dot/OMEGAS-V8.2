#!/usr/bin/env python3
"""Auditoria offline e determinística. Integridade não é teste funcional nem prova de ECU."""
import argparse
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
REA = re.compile(r'\bev_[a-f0-9]{64}\b')
MAN = re.compile(r'\bMAN-(?:2\.16\.4-p\d{3}|ABAS-B-[A-Za-z0-9_]+(?:-[A-Za-z0-9_]+)*|PROTOCOLO-CONNECT-[A-F0-9]{8})\b')


def ler(path, default):
    return json.loads(path.read_text()) if path.exists() else default


def auditar(root):
    root = Path(root)
    pb = root / 'docs/progbase'
    bundle = ler(pb / 'decomp/evidence-bundle.json', {'records': []})
    existentes = {r['evidence_id'] for r in bundle['records']}
    citados = set()
    man_citados = set()
    locais = ler(pb / 'evidencias-locais.json', {'records': []})['records']
    paginas = ler(pb / 'manual/paginas.json', {'paginas': {}})['paginas']
    man_existentes = {r['evidence_id'] for r in locais} | set(paginas)
    for path in pb.rglob('*.md'):
        citados.update(REA.findall(path.read_text()))
        man_citados.update(MAN.findall(path.read_text()))
    graph = ler(pb / 'grafo/graph.json', {'nodes': [], 'links': []})
    ids = {n['id'] for n in graph['nodes']}
    arquivos = sorted({n['source_file'] for n in graph['nodes'] if n.get('source_file')})
    ausentes = [p for p in arquivos if not (root / p).is_file()]
    pontas = [link for link in graph['links'] if link['source'] not in ids or link['target'] not in ids]
    ledger = root / 'docs/handoff/PENDENTE.md'
    texto = ledger.read_text() if ledger.exists() else ''
    errors = []
    if not (pb / 'grafo/graph.json').exists():
        errors.append('grafo ausente')
    if not ledger.exists():
        errors.append('ledger ausente')
    errors.extend('arquivo ausente: ' + p for p in ausentes)
    errors.extend('evidência REA ausente: ' + e for e in sorted(citados - existentes))
    errors.extend('evidência MAN ausente: ' + e for e in sorted(man_citados - man_existentes))
    errors.extend('fonte MAN ausente: ' + r['source_file'] for r in locais
                  if not (root / r['source_file']).is_file())
    if paginas and not (pb / 'manual/manual-2.16.4C.txt').is_file():
        errors.append('texto do manual ausente')
    if pontas:
        errors.append(f'{len(pontas)} arestas com ponta inexistente')
    return {
        'integridade_ok': not errors, 'erros': errors,
        'limite': 'Integridade referencial; não comprova reconstrução funcional ou execução no carro. Bundle validado separadamente pelo REA.',
        'evidencias': {'rea_citadas': len(citados), 'rea_existentes': len(existentes), 'rea_ausentes': sorted(citados - existentes),
                      'man_citadas': len(man_citados), 'man_existentes': len(man_existentes),
                      'man_ausentes': sorted(man_citados - man_existentes)},
        'grafo': {'nos': len(ids), 'arquivos': len(arquivos), 'arquivos_ausentes': ausentes, 'arestas_invalidas': len(pontas)},
        'pendencias': {'abertas': len(re.findall(r'^- \[ \]', texto, re.M)), 'feitas': len(re.findall(r'^- \[[xX]\]', texto, re.M))},
        'cobertura_estatica': ler(pb / 'decomp/cobertura.json', None),
        'testes': {'estado': 'DESCONHECIDO', 'motivo': 'Este comando não executa testes nem presume resultados de outros jobs.'},
        'actions': {'estado': 'DESCONHECIDO', 'minutos': None, 'orcamento_mensal': 10000, 'alerta_70_porcento': None},
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='falha se referências não existirem')
    args = parser.parse_args()
    report = auditar(ROOT)
    report['source_sha'] = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    output = ROOT / 'docs/handoff/AUDITORIA.json'
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + '\n')
    print(json.dumps({'integridade_ok': report['integridade_ok'], 'erros': report['erros']}, ensure_ascii=False))
    return 1 if args.check and not report['integridade_ok'] else 0


if __name__ == '__main__':
    raise SystemExit(main())
