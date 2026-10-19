#!/usr/bin/env python3
"""Graphify ESPECÍFICO do ProgBase: formulário/aba -> controle -> handler (endereço/função) -> vetor (SerialCode) -> comando/opcode
-> resposta esperada -> captura (linha do log) -> evidência (doc, ev_id) -> equivalente no app OMEGAS (arquivo).
Lê, de forma TOLERANTE (o que não existir é ignorado e contado em 'ausentes'):
  docs/progbase/decomp/funcoes.json.gz        (funções, nomes RTTI, chamadores/chamados, pseudocódigo)
  docs/progbase/inventario/telas.json         (D1: telas -> controles -> eventos -> handler)
  docs/progbase/protocolo/{comandos,vetores,matriz-comando-resposta}.json   (D2)
  docs/progbase/abas/*.json                   (D3: itens com controle/vetor/handler; ingestão genérica)
  docs/progbase/grafo/ligacoes_manuais.json   (opcional: [{de,relacao,para,marca,evidencia}])
  docs/progbase/**/*.md  (+ docs/evidence/2026-10-18-{cpu-setter-original,flags-do-original,ecu-emulada}.md)
Saída: docs/progbase/grafo/graph.json (formato graphify: nodes/links) + GRAPH_REPORT.md. Determinístico (sem relógio), idempotente.
Marcas nas arestas: OBS-COD / OBS-FIO / DERIVADO / INFERIDO / DESCONHECIDO (campo 'marca').
Uso: python3 tools/progbase/grafo_progbase.py [--check]   (--check: valida integridade e falha se graph.json está desatualizado)
Consulta: graphify query "TFormDiagnosi" --graph docs/progbase/grafo/graph.json ; graphify path "A" "B" --graph ..."""
import gzip, json, os, re, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
PB = os.path.join(ROOT, 'docs', 'progbase')
OUT = os.path.join(PB, 'grafo')
ORIGEM = 'progbase'
EV = re.compile(r'\bev_[a-f0-9]{64}\b')
MAN = re.compile(r'\bMAN-(?:2\.16\.4-p\d{3}|ABAS-B-[A-Za-z0-9_]+(?:-[A-Za-z0-9_]+)*|PROTOCOLO-CONNECT-[A-F0-9]{8})\b')
ADDR = re.compile(r'\b0x([0-9A-Fa-f]{6,8})\b')
ARQ = re.compile(r'[\w./-]+\.(?:kt|js|cjs|py|md|json)\b')

nos, arestas, ausentes = {}, set(), []


def no(id_, label, tipo, fonte='', **kw):
    n = nos.setdefault(id_, {'id': id_, 'label': label, 'tipo': tipo, 'file_type': 'document' if tipo in ('doc', 'evidencia') else 'code',
                             'source_file': fonte, '_origin': ORIGEM, 'norm_label': label.lower()})
    for k, v in kw.items():
        if v not in (None, '', [], {}):
            n[k] = v
    return id_


def lig(a, r, b, marca='OBS-COD', ev=None):
    if a in nos and b in nos and a != b:
        arestas.add((a, b, r, marca, ev or ''))


def abrir(rel):
    p = os.path.join(ROOT, rel)
    if not os.path.exists(p):
        ausentes.append(rel)
        return None
    with open(p, encoding='utf-8') as fh:
        return json.load(fh)


def norm(a):
    return '0x%08X' % int(a, 16)


def indice_arquivos():
    idx = {}
    for base in ('app', 'tools', 'docs', 'tests', 'scripts'):
        for d, ds, fs in os.walk(os.path.join(ROOT, base)):
            ds[:] = [x for x in ds if x not in ('build', 'node_modules', '__pycache__', 'pseudocodigo', 'dfm')]
            for f in fs:
                if f.endswith(('.kt', '.js', '.cjs', '.py', '.md')):
                    idx.setdefault(f, []).append(os.path.relpath(os.path.join(d, f), ROOT))
    return idx


def carregar_funcoes():
    p = os.path.join(PB, 'decomp', 'funcoes.json.gz')
    if not os.path.exists(p):
        ausentes.append('docs/progbase/decomp/funcoes.json.gz')
        return {}
    F = json.load(gzip.open(p))['funcoes']
    por = {f['a']: f for f in F}
    fonte = 'docs/progbase/decomp/funcoes.json.gz'
    for f in F:
        if f['nome'] or (f['unidade'] and f['unidade'].startswith('@')):
            rot = f['nome'] or f"FUN_{f['a'][2:]}"
            no('fn:' + f['a'], f"{rot} @{f['a']}", 'funcao', fonte, endereco=f['a'], tamanho=f['tam'], unidade=f['unidade'],
               marca_nome=f['fonte'], proposito=f.get('proposito'),
               pseudocodigo=('docs/progbase/decomp/pseudocodigo/' + f['frag']) if f.get('frag') else None)
    for f in F:
        a = 'fn:' + f['a']
        if a in nos:
            for c in f['cc']:
                lig(a, 'chama', 'fn:' + c, 'OBS-COD')
    return por


def garantir_fn(por, a, nome=None):
    a = norm(a)
    id_ = 'fn:' + a
    if id_ not in nos:
        f = por.get(a, {})
        no(id_, f"{nome or f.get('nome') or 'FUN_' + a[2:]} @{a}", 'funcao', 'docs/progbase/decomp/funcoes.json.gz', endereco=a,
           tamanho=f.get('tam'), unidade=f.get('unidade'), marca_nome=f.get('fonte', 'DESCONHECIDO'),
           pseudocodigo=('docs/progbase/decomp/pseudocodigo/' + f['frag']) if f.get('frag') else None)
    return id_


def _txt(x):
    if isinstance(x, dict):
        return str(x.get('ita') or x.get('dfm') or x.get('eng') or '')
    return str(x or '')


def telas(por):
    d = abrir('docs/progbase/inventario/telas.json')
    if not d:
        return
    for t in d['telas']:
        fid = no('form:' + t['classe'], f"{t['classe']} ({_txt(t.get('titulo')) or t.get('nome_instancia') or ''})", 'formulario',
                 'docs/progbase/inventario/telas.json', recurso=t.get('recurso'), unidade=t.get('unidade'))
        if t.get('unidade'):
            uid = no('uni:' + str(t['unidade']), 'unit ' + str(t['unidade']), 'unidade', 'docs/progbase/inventario/telas.json')
            lig(fid, 'definida_em', uid, 'OBS-COD')
        for ab in t.get('aberta_por', []):
            lig(garantir_fn(por, ab['funcao'], ab.get('nome')), 'abre', fid, 'OBS-COD')
        for c in t.get('controles', []):
            cid = no(f"ctl:{t['classe']}.{c['caminho']}", f"{t['classe']}.{c['componente']} [{c['classe']}] {c.get('legenda') or ''}".strip(), 'controle',
                     'docs/progbase/inventario/telas.json', legenda=c.get('legenda'), legenda_por=(c.get('traducao') or {}).get('por'))
            lig(fid, 'contem', cid, 'OBS-COD')
            for e in c.get('eventos', []):
                if e.get('endereco'):
                    lig(cid, 'dispara:' + e['evento'], garantir_fn(por, e['endereco'], f"{t['classe']}.{e['handler']}"), 'OBS-COD')


def protocolo(por, arq):
    cmds = abrir('docs/progbase/protocolo/comandos.json')
    por_codigo = {}
    if cmds:
        for c in cmds['comandos']:
            cid = no('cmd:' + c['id'], f"{c['id']}: {c['nome']}", 'comando', 'docs/progbase/protocolo/comandos.json', grupo=c.get('grupo'), quadro=c.get('quadro'),
                     marca_cmd=c.get('marca'))
            m = re.search(r'\b(?:op|ProtocolCommand|byte do comando[^0-9A-Fa-f]*)\s*0x([0-9A-Fa-f]{2})\b', c['nome'] + ' ' + c.get('quadro', ''))
            if m:
                por_codigo.setdefault(m.group(1).upper(), []).append(cid)
            for i, r in enumerate(c.get('resposta_esperada', [])):
                rid = no(f"resp:{c['id']}:{i}", r[:160], 'resposta', 'docs/progbase/protocolo/comandos.json')
                lig(cid, 'espera_resposta', rid, 'OBS-FIO' if 'observado' in r else 'OBS-COD')
            for a in ADDR.findall(c.get('rotina', '') or ''):
                lig(cid, 'implementado_em', garantir_fn(por, a), 'OBS-COD')
            nl = c.get('no_log')
            if nl and nl.get('primeira_linha'):
                kid = no(f"cap:LN:{nl['primeira_linha']}", f"captura LN linha {nl['primeira_linha']}: {nl.get('exemplo', {}).get('req', '')}", 'captura',
                         'docs/progbase/protocolo/matriz-comando-resposta.json', linha=nl['primeira_linha'], exemplo=nl.get('exemplo'))
                lig(cid, 'visto_em', kid, 'OBS-FIO')
            om = c.get('omegas') or {}
            for nome in ARQ.findall(str(om.get('ref', ''))):
                for caminho in arq.get(os.path.basename(nome), [])[:2]:
                    aid = no('app:' + caminho, caminho, 'arquivo_omegas', caminho)
                    lig(cid, 'equivalente_no_omegas', aid, 'DERIVADO')
        for ch in cmds.get('sendcommand_chamadores', []):
            cod = ch['codigo'].replace('0x', '').upper()
            for cid in por_codigo.get(cod, []):
                lig(garantir_fn(por, ch['endereco']), 'envia', cid, 'OBS-COD')
    v = abrir('docs/progbase/protocolo/vetores.json')
    if v:
        for it in v['itens']:
            vid = no(f"vec:{it['modulo']}.{it['nome']}", f"{it['nome']} [{it['classe']}] SerialCode {it.get('serial_hex')}", 'vetor',
                     'docs/progbase/protocolo/vetores.json', serial_code=it.get('serial_code'), serial_hex=it.get('serial_hex'), forma=it.get('forma'),
                     modulo=it['modulo'])
            w = it.get('fio_v2') or {}
            for lado, rel in (('leitura', 'le_com'), ('escrita', 'escreve_com')):
                x = w.get(lado)
                if x and x.get('op'):
                    oid = no('op:' + x['op'].upper(), f"opcode 0x{x['op'].upper()}", 'opcode', 'docs/progbase/protocolo/vetores.json')
                    lig(vid, rel, oid, 'OBS-COD')
                    if x.get('rotina'):
                        for a in ADDR.findall(x['rotina']):
                            lig(oid, 'rotina', garantir_fn(por, a), 'OBS-COD')
    for k, cod in (('LN', 'LN'), ('AC', 'AC')):
        m = abrir('docs/progbase/protocolo/matriz-comando-resposta.json') if k == 'LN' else None
        if not m:
            continue
        for ch, val in m.get('LN', {}).get('matriz', {}).items():
            pr = val.get('pedidos') or [{}]
            linha = pr[0].get('primeira_linha')
            if linha:
                kid = no(f'cap:LN:{linha}', f'captura LN linha {linha}: {pr[0].get("req", ch)}', 'captura',
                         'docs/progbase/protocolo/matriz-comando-resposta.json', linha=linha)
                mo = re.match(r'([0-9A-Fa-f]{2})\b', ch)
                if mo:
                    oid = 'op:' + mo.group(1).upper()
                    if oid in nos:
                        lig(oid, 'visto_em', kid, 'OBS-FIO')


def abas():
    d = os.path.join(PB, 'abas')
    if not os.path.isdir(d):
        return
    for f in sorted(os.listdir(d)):
        if not f.endswith('.json'):
            continue
        rel = f'docs/progbase/abas/{f}'
        try:
            x = json.load(open(os.path.join(d, f), encoding='utf-8'))
        except ValueError:
            ausentes.append(rel + ' (JSON inválido)')
            continue

        def varre(o):
            if isinstance(o, dict):
                ctl = o.get('controle') or o.get('componente')
                vet = o.get('vetor') or o.get('serial_vetor')
                if ctl and vet:
                    cands = [n for n in nos if n.startswith('ctl:') and n.endswith('.' + str(ctl))]
                    vcs = [n for n in nos if n.startswith('vec:') and n.endswith('.' + str(vet))]
                    for c in cands:
                        for vc in vcs:
                            lig(c, 'liga_vetor', vc, o.get('marca', 'DERIVADO'))
                for v in o.values():
                    varre(v)
            elif isinstance(o, list):
                for v in o:
                    varre(v)
        varre(x)


def documentos(por, arq):
    docs = []
    for d, ds, fs in os.walk(PB):
        ds[:] = [x for x in ds if x not in ('pseudocodigo', 'dfm', 'execucao')]
        for f in fs:
            if f.endswith('.md'):
                docs.append(os.path.relpath(os.path.join(d, f), ROOT))
    for extra in ('docs/evidence/2026-10-18-cpu-setter-original.md', 'docs/evidence/2026-10-18-flags-do-original.md', 'docs/evidence/2026-10-18-ecu-emulada.md'):
        if os.path.exists(os.path.join(ROOT, extra)):
            docs.append(extra)
    handlers = {n['label'].split(' @')[0]: i for i, n in nos.items() if n['tipo'] == 'funcao' and '.' in n['label'].split(' @')[0]}
    for rel in sorted(set(docs)):
        did = no('doc:' + rel, os.path.basename(rel), 'doc', rel)
        texto = open(os.path.join(ROOT, rel), encoding='utf-8', errors='replace').read()
        for ln, linha in enumerate(texto.split('\n'), 1):
            for e in EV.findall(linha):
                eid = no('ev:' + e, e[:15] + '…', 'evidencia', rel, evidence_id=e, arquivo=rel, linha=ln)
                lig(did, 'cita_evidencia', eid, 'OBS-COD')
            for e in MAN.findall(linha):
                if 'ev:' + e in nos:
                    lig(did, 'cita_evidencia', 'ev:' + e, 'DERIVADO')
        for forma in sorted(set(re.findall(r'\bT[A-Za-z0-9_]+\b', texto))):
            if 'form:' + forma in nos:
                lig(did, 'menciona', 'form:' + forma, 'OBS-COD')
        for caminho in sorted(set(re.findall(r'docs/progbase/[\w./-]+\.md', texto))):
            if 'doc:' + caminho in nos or os.path.exists(os.path.join(ROOT, caminho)):
                alvo = no('doc:' + caminho, os.path.basename(caminho), 'doc', caminho) if os.path.exists(os.path.join(ROOT, caminho)) else None
                if alvo:
                    lig(did, 'cita', alvo, 'OBS-COD')
        for a in sorted(set(ADDR.findall(texto))):
            h = norm(a)
            if 'fn:' + h in nos:
                lig(did, 'menciona', 'fn:' + h, 'OBS-COD')
        for nome in sorted(set(re.findall(r'\b(T[A-Za-z0-9_]+\.[A-Za-z0-9_]+)\b', texto))):
            if nome in handlers:
                lig(did, 'menciona', handlers[nome], 'OBS-COD')
        for nome in sorted(set(ARQ.findall(texto))):
            for caminho in arq.get(os.path.basename(nome), [])[:1]:
                if nome.endswith(('.kt', '.js', '.cjs')) and ('/' not in nome or caminho.endswith(nome)):
                    aid = no('app:' + caminho, caminho, 'arquivo_omegas', caminho)
                    lig(did, 'cita', aid, 'OBS-COD')


def reconstrucoes(por):
    spec = {'cpu_setter_original': ('tools/emulacao/cpu_setter_original.py', ['0x0042E8D8', '0x00978F8C', '0x0092617C', '0x00923608', '0x0091DF00', '0x00923860'],
                                     'docs/evidence/2026-10-18-cpu-setter-original.md'),
            'flags_do_original': ('tools/emulacao/flags_do_original.py', ['0x0042CFB8', '0x0042D300', '0x00978F8C'], 'docs/evidence/2026-10-18-flags-do-original.md'),
            'ecu_emulator': ('tools/emulacao/ecu_emulator.py', [], 'docs/evidence/2026-10-18-ecu-emulada.md')}
    for nome, (arqv, ends, doc) in spec.items():
        if not os.path.exists(os.path.join(ROOT, arqv)):
            continue
        rid = no('rec:' + nome, f'reconstrução {nome}', 'reconstrucao', arqv)
        for a in ends:
            lig(rid, 'reconstroi', garantir_fn(por, a), 'OBS-COD')
        if 'doc:' + doc in nos:
            lig(rid, 'documentada_em', 'doc:' + doc, 'OBS-COD')


def manuais():
    d = abrir('docs/progbase/grafo/ligacoes_manuais.json')
    for x in d or []:
        lig(x['de'], x['relacao'], x['para'], x.get('marca', 'INFERIDO'), x.get('evidencia'))


def evidencias():
    """Registros REA permanecem separados das análises locais e páginas do manual."""
    bundle_path = 'docs/progbase/decomp/evidence-bundle.json'
    bundle = abrir(bundle_path) or {}
    for record in bundle.get('records', []):
        eid = record['evidence_id']
        no('ev:' + eid, record['operation'], 'evidencia', bundle_path,
           evidence_id=eid, authority=record.get('authority'), confidence=record.get('confidence'))
    local = abrir('docs/progbase/evidencias-locais.json') or {}
    for record in local.get('records', []):
        eid = record['evidence_id']
        no('ev:' + eid, eid, 'evidencia_manual', record['source_file'], evidence_id=eid,
           marca=record.get('marca'), limite=record.get('limite'))
    manual = abrir('docs/progbase/manual/paginas.json') or {}
    for eid, page in sorted(manual.get('paginas', {}).items()):
        page_id = 'manual:2.16.4:p' + str(page['pagina'])
        no(page_id, 'Manual 2.16.4, página ' + str(page['pagina']), 'pagina_manual',
           'docs/progbase/manual/manual-2.16.4C.txt', pagina=page['pagina'], sha256=page['sha256'])
        no('ev:' + eid, eid, 'evidencia_manual', 'docs/progbase/manual/paginas.json',
           evidence_id=eid, marca='OBS-DOC', limite=manual.get('limite'))
        lig('ev:' + eid, 'identifica_pagina', page_id, 'DERIVADO')


def montar():
    nos.clear(); arestas.clear(); ausentes.clear()
    arq = indice_arquivos()
    por = carregar_funcoes()
    telas(por); protocolo(por, arq); abas(); evidencias(); documentos(por, arq); reconstrucoes(por); manuais()
    return por


def gravar():
    ids = sorted(nos)
    grau = {i: 0 for i in ids}
    for a, b, *_ in arestas:
        grau[a] += 1; grau[b] += 1
    grafo = {'directed': True, 'multigraph': False, 'graph': {'nome': 'graphify-progbase', 'origem': ORIGEM}, 'nodes': [nos[i] for i in ids],
             'links': [{'source': a, 'target': b, 'relation': r, 'marca': m, 'confidence': 'EXTRACTED' if m.startswith('OBS') else 'INFERRED',
                        'confidence_score': 1.0 if m.startswith('OBS') else 0.7, '_origin': ORIGEM, 'source_file': nos[a]['source_file'],
                        **({'evidence_id': e} if e else {})} for a, b, r, m, e in sorted(arestas)],
             'hyperedges': [], 'ausentes': sorted(ausentes)}
    texto = json.dumps(grafo, ensure_ascii=False, sort_keys=True, separators=(',', ':'))
    tipos = {}
    for n in nos.values():
        tipos[n['tipo']] = tipos.get(n['tipo'], 0) + 1
    rels = {}
    for _, _, r, *_ in arestas:
        k = r.split(':')[0]
        rels[k] = rels.get(k, 0) + 1
    orfaos = [i for i in ids if grau[i] == 0]
    criticos = [i for i in orfaos if nos[i]['tipo'] in ('formulario', 'comando', 'vetor', 'opcode', 'evidencia', 'doc', 'reconstrucao')]
    hubs = sorted(ids, key=lambda i: (-grau[i], i))[:15]
    rel = ['# Graphify do ProgBase — relatório', '',
           'Gerado por `tools/progbase/grafo_progbase.py` (determinístico). Consulta: `graphify query "TFormDiagnosi" --graph docs/progbase/grafo/graph.json`; '
           '`graphify path "TFormDiagnosi" "SendCommand" --graph docs/progbase/grafo/graph.json`; índice humano: `docs/progbase/INDEX.md`.', '',
           f'- Nós: {len(nos)} · Arestas: {len(arestas)} · Órfãos: {len(orfaos)} (críticos: {len(criticos)})', '', '## Nós por tipo']
    rel += [f'- {k}: {v}' for k, v in sorted(tipos.items())]
    rel += ['', '## Arestas por relação'] + [f'- {k}: {v}' for k, v in sorted(rels.items())]
    rel += ['', '## Mais conectados'] + [f'- {nos[i]["label"]} ({grau[i]})' for i in hubs]
    rel += ['', '## Fontes ausentes na geração (tolerado; rode de novo quando existirem)'] + ([f'- {a}' for a in sorted(ausentes)] or ['- nenhuma'])
    rel += ['', '## Órfãos críticos'] + ([f'- {i}' for i in criticos[:50]] or ['- nenhum'])
    return texto, '\n'.join(map(str, rel)) + '\n', criticos


def checar(texto):
    g = json.loads(texto)
    ids = {n['id'] for n in g['nodes']}
    erros = [f"aresta com ponta inexistente: {l['source']}->{l['target']}" for l in g['links'] if l['source'] not in ids or l['target'] not in ids]
    for n in g['nodes']:
        sf = n.get('source_file')
        if sf and not os.path.exists(os.path.join(ROOT, sf)):
            erros.append(f"nó {n['id']} aponta para arquivo inexistente {sf}")
    return erros


def main():
    montar()
    texto, rel, criticos = gravar()
    erros = checar(texto)
    os.makedirs(OUT, exist_ok=True)
    gp, rp = os.path.join(OUT, 'graph.json'), os.path.join(OUT, 'GRAPH_REPORT.md')
    if '--check' in sys.argv:
        atual = open(gp, encoding='utf-8').read() if os.path.exists(gp) else ''
        for e in erros:
            print(e)
        if atual != texto:
            print('graph.json desatualizado: rode python3 tools/progbase/grafo_progbase.py')
        sys.exit(1 if erros or atual != texto else 0)
    open(gp, 'w', encoding='utf-8').write(texto)
    open(rp, 'w', encoding='utf-8').write(rel)
    print(f'{len(nos)} nós, {len(arestas)} arestas, {len(criticos)} órfãos críticos, {len(erros)} erros, {os.path.getsize(gp)/1e6:.1f} MB')
    for e in erros[:10]:
        print(' ', e)


if __name__ == '__main__':
    main()
