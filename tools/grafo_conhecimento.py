#!/usr/bin/env python3
"""Liga documentos, evidências e código num só grafo (graphify-out/graph.json), sem LLM e sem relógio.
Nós: cada .md de docs/ (documento) e cada entidade de docs/mapa-de-fontes/entidades.json (conceito).
Arestas (EXTRACTED, só o que está escrito): documento --menciona--> entidade; documento --cita--> arquivo do repo (caminho entre crases
ou em lista que existe no disco) e, quando o grafo de código tem nós desse arquivo, documento --documenta--> esses nós.
Idempotente: remove os nós/arestas de origem 'conhecimento' antes de gravar e ordena tudo. Uso: python3 tools/grafo_conhecimento.py [--check]"""
import json, os, re, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
GRAFO = os.path.join(ROOT, 'graphify-out', 'graph.json')
ORIGEM = 'conhecimento'
CAMINHO = re.compile(r'`([\w./-]+\.(?:md|kt|js|py|json|cjs|yml|txt|html))`|\b((?:docs|tools|tests|app)/[\w./-]+\.(?:md|kt|js|py|json|cjs|yml|txt|html))\b')


def docs():
    for base, _, arquivos in os.walk(os.path.join(ROOT, 'docs')):
        if os.sep + 'dfm-original' in base or os.sep + 'img' in base:
            continue
        for nome in sorted(arquivos):
            if nome.endswith('.md'):
                yield os.path.relpath(os.path.join(base, nome), ROOT)


def construir(grafo):
    entidades = json.load(open(os.path.join(ROOT, 'docs', 'mapa-de-fontes', 'entidades.json'), encoding='utf-8'))['entidades']
    por_arquivo = {}
    for n in grafo['nodes']:
        if n.get('_origin') == ORIGEM:
            continue
        sf = n.get('source_file')
        if sf and n.get('file_type') == 'code':
            por_arquivo.setdefault(sf, []).append(n['id'])
    nos, arestas = [], []
    for e in entidades:
        nos.append({'id': 'conceito:' + e['id'], 'label': e['rotulo'], 'file_type': 'concept', 'source_file': '', '_origin': ORIGEM, 'norm_label': e['rotulo'].lower()})
    for doc in sorted(docs()):
        texto = open(os.path.join(ROOT, doc), encoding='utf-8', errors='replace').read()
        did = 'doc:' + doc
        nos.append({'id': did, 'label': os.path.basename(doc), 'file_type': 'document', 'source_file': doc, '_origin': ORIGEM, 'norm_label': os.path.basename(doc).lower()})
        for e in entidades:
            if re.search(e['padrao'], texto):
                arestas.append((did, 'conceito:' + e['id'], 'menciona'))
        citados = sorted({a or b for a, b in CAMINHO.findall(texto) if os.path.exists(os.path.join(ROOT, a or b)) and (a or b) != doc})
        for caminho in citados:
            alvo = 'doc:' + caminho if caminho.endswith('.md') else None
            if alvo:
                arestas.append((did, alvo, 'cita'))
            for nid in sorted(por_arquivo.get(caminho, []))[:3]:
                arestas.append((did, nid, 'documenta'))
    return nos, sorted(set(arestas))


def aplicar(grafo, nos, arestas):
    grafo['nodes'] = [n for n in grafo['nodes'] if n.get('_origin') != ORIGEM] + nos
    grafo['links'] = [l for l in grafo['links'] if l.get('_origin') != ORIGEM]
    ids = {n['id'] for n in grafo['nodes']}
    for s, t, r in arestas:
        if s in ids and t in ids:
            grafo['links'].append({'source': s, 'target': t, 'relation': r, '_origin': ORIGEM, 'confidence': 'EXTRACTED', 'confidence_score': 1.0, 'source_file': s[4:] if s.startswith('doc:') else ''})
    return grafo


def main():
    if not os.path.exists(GRAFO):
        print('sem graphify-out/graph.json: rode `graphify update .` antes', file=sys.stderr)
        return 1
    grafo = json.load(open(GRAFO, encoding='utf-8'))
    nos, arestas = construir(grafo)
    novo = aplicar(grafo, nos, arestas)
    n_doc = sum(1 for n in nos if n['file_type'] == 'document')
    n_lig = sum(1 for l in novo['links'] if l.get('_origin') == ORIGEM)
    if '--check' in sys.argv:
        print(f'{n_doc} documentos, {len(nos) - n_doc} entidades, {n_lig} ligações')
        return 0
    with open(GRAFO, 'w', encoding='utf-8') as fh:
        json.dump(novo, fh, ensure_ascii=False)
    print(f'grafo de conhecimento: {n_doc} documentos, {len(nos) - n_doc} entidades, {n_lig} ligações')
    return 0


if __name__ == '__main__':
    sys.exit(main())
