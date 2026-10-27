#!/usr/bin/env python3
"""Grafo de PRODUÇÃO (só o que está em app/): aba -> arquivo JS -> ponte nativa -> manager Kotlin -> teste -> evidência.
Determinístico (sem LLM, sem relógio, sem rede) e idempotente. O grafo de PESQUISA (ProgBase) é outro: tools/grafo_conhecimento.py.
Heurística de texto (não há análise de AST): um arquivo "chama" uma ponte se cita o nome do método como `.nome` ou 'nome'.
Uso: python3 -B tools/grafo_producao.py [--check]   (--check falha se docs/grafo/ estiver desatualizado ou houver elo ESSENCIAL quebrado)
Saída: docs/grafo/producao.json e docs/grafo/PRODUCAO.md.
Elo ESSENCIAL quebrado (falha a CI): (E1) UI chama método de ponte que não existe (OmegasX.nome direto ou invoke(alvo,'nome') dos wrappers); (E2) evidência cita arquivo (usado_por) que não existe;
(E3) ponte que muda a ECU/arquivos (Write|Erase|Delete|Restore|Reset|Apply|Clear|Send|Confirm|Execute no nome) e é chamada pela UI, mas nenhum teste cita o método. Os demais são informativos."""
import json, os, re, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
UI = 'app/src/main/assets/ui'
BRIDGES = {'AutoCalJavascriptBridge.kt': 'OmegasAutoCal', 'HubJavascriptBridge.kt': 'OmegasNative',
           'CalibrationJavascriptBridge.kt': 'OmegasCalibration', 'PowerJavascriptBridge.kt': 'OmegasPower'}
ABAS = [('agora', 'Agora', None), ('map', 'Mapa K', 'map'), ('curve', 'Curva K', 'curve'), ('autocal', 'AutoCal', 'autocal'),
        ('refino', 'Ajuste GNV', 'refino'), ('sessions', 'Sessões', 'sessions'), ('tools', 'Ferramentas', 'tools'), ('diag', 'Diagnóstico', None)]
HOJE = {
    'agora': 'Sem aba própria: só a barra viva (tensão, MAP, injeção, RPM) no topo.',
    'map': 'Grade 12x12 do Mapa K, edição e correção de células, região de apagão.',
    'curve': 'Curva K: edição, suavização, conversão em mapa, gravação com leitura de volta.',
    'autocal': 'AutoCal: calibração automática, pontos por zona, apagar fora da curva.',
    'refino': 'Ajuste GNV (rota "refino"): referência de gasolina, recomendações do refino.',
    'sessions': 'Sessões gravadas, exportação em ZIP, resumo.',
    'tools': 'Ferramentas: cartões do motor, bits da ECU, anti-apagão, marcha lenta.',
    'diag': 'Sem aba própria: estados na barra e cartões de Ferramentas.'}
SEEDS = {  # arquivo (relativo a UI) -> abas; o que não consta vai para "base" (transversal)
    'screens/map.js': ['map'], 'map-editor.js': ['map'], 'core/curve-to-map.js': ['map'], 'core/map-fix.js': ['map'], 'core/stall-region.js': ['map'],
    'screens/curve.js': ['curve'], 'core/curve-smoothing.js': ['curve'],
    'screens/autocal-original.js': ['autocal'], 'screens/autocal-cockpit.js': ['autocal'], 'core/autocal-api.js': ['autocal'],
    'core/balance-runner.js': ['autocal'], 'core/manual-automatch-advice.js': ['autocal'],
    'screens/refino.js': ['refino'], 'core/gasoline-reference.js': ['refino'],
    'components/drawers.js': ['sessions', 'tools'], 'components/advanced.js': ['tools'], 'core/advanced-flags.js': ['tools'],
    'core/advanced-flags-data.js': ['tools'], 'core/anti-apagao-valores.js': ['tools'], 'core/idle-mode.js': ['tools'], 'core/cartoes-motor.js': ['tools'],
    'components/live-bar.js': ['agora']}
ESCRITA = re.compile(r'^(?:write|erase|delete|restore|reset|apply|clear|send|confirm|execute)|(?:Write|Erase|Delete|Restore|Reset|Apply|Clear|Send|Confirm|Execute)')  # método que muda ECU/arquivos
KT_ALVO = re.compile(r'\b([A-Z]\w*(?:Manager|Core|Protocol|Engine|Service|Store|Gate|Writer|Resolver|Repository|Exporter|Controller|Session))\b')
EV = re.compile(r'\bEV-[A-Z0-9]+(?:-[A-Z0-9]+)*\b')


def ler(rel):
    with open(os.path.join(ROOT, rel), encoding='utf-8', errors='replace') as f:
        return f.read()


def listar(base, exts):
    saida = []
    for d, ds, fs in os.walk(os.path.join(ROOT, base)):
        ds[:] = sorted(x for x in ds if x not in ('fixtures', 'node_modules', '__pycache__'))
        for n in sorted(fs):
            if n.endswith(exts):
                saida.append(os.path.relpath(os.path.join(d, n), ROOT).replace(os.sep, '/'))
    return sorted(saida)


def cita(texto, nome):
    return re.search(r"(?:\.|['\"`])" + re.escape(nome) + r"(?![\w])", texto) is not None


def cita_arquivo(texto, rel):
    base = os.path.basename(rel)
    return re.search(r'(?<![\w.-])' + re.escape(base) + r'(?![\w-])', texto) is not None


def construir():
    ui_js = listar(UI, ('.js',))
    textos = {f: ler(f) for f in ui_js}
    pontes, kt_def = {}, {}
    kt_main = listar('app/src/main/java', ('.kt',))
    ktext = {f: ler(f) for f in kt_main}
    for f in kt_main:
        for m in re.finditer(r'\b(?:class|object|interface)\s+([A-Z]\w*)', ktext[f]):
            kt_def.setdefault(m.group(1), f)
    for f in kt_main:
        nome = os.path.basename(f)
        if nome not in BRIDGES:
            continue
        for p in ktext[f].split('@JavascriptInterface')[1:]:
            m = re.search(r'fun\s+(\w+)\s*\(', p)
            if not m:
                continue
            corpo = p.split('\n    @', 1)[0][m.end():]
            alvos = sorted(set(KT_ALVO.findall(corpo)))
            pontes[BRIDGES[nome] + '.' + m.group(1)] = {'interface': BRIDGES[nome], 'metodo': m.group(1), 'arquivo': f, 'alvos': alvos}
    cjs = listar('tests/ui', ('.cjs', '.js'))
    py = [f for f in listar('tests', ('.py',)) if os.path.basename(f).startswith('test_')]
    kts = listar('app/src/test', ('.kt',))
    testes = cjs + py + kts
    ttext = {f: ler(f) for f in testes}
    nos, arestas = {}, set()

    def no(i, tipo, rotulo, **extra):
        nos[i] = dict(tipo=tipo, rotulo=rotulo, **extra)

    for aid, rot, rota in ABAS:
        no('aba:' + aid, 'aba', rot, rota=rota or '', existe=bool(rota))
    no('aba:base', 'aba', 'Base (todas as abas)', rota='', existe=True)
    for f in ui_js:
        no('ui:' + f, 'ui', f[len(UI) + 1:])
        for a in SEEDS.get(f[len(UI) + 1:], ['base']):
            arestas.add(('aba:' + a, 'ui:' + f, 'exibe'))
    chamadores = {}
    for p, d in sorted(pontes.items()):
        no('ponte:' + p, 'ponte', p, arquivo=d['arquivo'], escrita=bool(ESCRITA.search(d['metodo'])))
        for f in ui_js:
            if cita(textos[f], d['metodo']):
                arestas.add(('ui:' + f, 'ponte:' + p, 'chama'))
                chamadores.setdefault(p, []).append(f)
        for a in d['alvos']:
            if a in kt_def:
                no('kt:' + a, 'kt', a, arquivo=kt_def[a])
                arestas.add(('ponte:' + p, 'kt:' + a, 'usa'))
    kt_nos = sorted(i for i in nos if i.startswith('kt:'))
    for t in testes:
        no('teste:' + t, 'teste', t)
        tx = ttext[t]
        palavras = set(re.findall(r'\w+', tx))
        for f in ui_js:
            if cita_arquivo(tx, f):
                arestas.add(('teste:' + t, 'ui:' + f, 'cobre'))
        for p, d in pontes.items():
            if len(d['metodo']) >= 6 and d['metodo'] in palavras:
                arestas.add(('teste:' + t, 'ponte:' + p, 'cobre'))
        for i in kt_nos:
            if nos[i]['rotulo'] in palavras:
                arestas.add(('teste:' + t, i, 'cobre'))
    indice = json.loads(ler('docs/progbase/evidencias/INDEX.json'))
    ids = {e['id'] for e in indice}
    citacoes = {}
    for grupo in (ktext, textos, ttext):
        for f, tx in grupo.items():
            for i in set(EV.findall(tx)) & ids:
                citacoes.setdefault(i, set()).add(f)
    ev_info = {}
    for e in sorted(indice, key=lambda x: x['id']):
        no('ev:' + e['id'], 'ev', e['id'], marca=e.get('marca', ''), afirmacao=e.get('afirmacao', '')[:90])
        usa = sorted(set(e.get('usado_por', [])))
        faltam = [u for u in usa if not os.path.exists(os.path.join(ROOT, u))]
        ligados = set()
        for u in list(usa) + sorted(citacoes.get(e['id'], [])):
            for alvo in ('ui:' + u, 'teste:' + u):
                if alvo in nos:
                    ligados.add(alvo)
            for k in kt_nos:
                if nos[k]['arquivo'] == u:
                    ligados.add(k)
        for alvo in sorted(ligados):
            arestas.add(('ev:' + e['id'], alvo, 'sustenta'))
        ev_info[e['id']] = {'usado_por': usa, 'faltam': faltam, 'citada_em': sorted(citacoes.get(e['id'], [])),
                            'ligada': bool(ligados)}
    return dict(nos=nos, arestas=sorted(arestas), pontes=pontes, chamadores=chamadores, ev=ev_info, indice=indice,
                ui_js=ui_js, textos=textos)


def lacunas():
    t = ler('docs/progbase/capturas/originais/CATALOGO.md')
    sec = t.split('## LACUNAS', 1)[1].split('\n## ', 1)[0] if '## LACUNAS' in t else ''
    saida = []
    for linha in sec.splitlines():
        c = [x.strip() for x in linha.strip().strip('|').split('|')]
        if len(c) >= 4 and c[0].isdigit():
            falta = bool(re.search(r'FALTA|DIFERENTE|Parcial', c[3]))
            saida.append({'n': int(c[0]), 'funcao': re.sub(r'[*]', '', c[1])[:70], 'falta': falta, 'veredito': re.sub(r'[*]', '', c[3])[:60]})
    return saida


def pendencias():
    abertas = []
    for linha in ler('docs/handoff/PENDENTE.md').splitlines():
        m = re.match(r'\s*- \[ \]\s*(.+)', linha)
        if m:
            abertas.append(re.sub(r'[*`]', '', m.group(1))[:90])
    return abertas


def analisar(g):
    pontes, nos, ar = g['pontes'], g['nos'], g['arestas']
    cobertas = {t for s, t, r in ar if r == 'cobre' and t.startswith('ponte:')}
    existentes = {d['metodo'] for d in pontes.values()}
    e1 = []
    direto = re.compile(r'\bOmegas(?:AutoCal|Native|V7|Power)\??\.(\w+)')
    envolto = re.compile(r"\binvoke\(\s*[\w.]+\s*,\s*'(\w+)'")  # native-api.js / autocal-api.js chamam a ponte por nome
    for f in g['ui_js']:
        limpo = re.sub(r'/\*.*?\*/', '', g['textos'][f], flags=re.S)
        limpo = re.sub(r'(?m)^\s*//.*$', '', limpo)
        for m in sorted(set(direto.findall(limpo)) | set(envolto.findall(limpo))):
            if m not in existentes:
                e1.append(f'{f[len(UI) + 1:]} -> {m}')
    e2 = sorted(f'{i}: {u}' for i, v in g['ev'].items() for u in v['faltam'])
    sem_teste = sorted(p for p in pontes if 'ponte:' + p not in cobertas)
    sem_chamador = sorted(p for p in pontes if p not in g['chamadores'])
    e3 = sorted(p for p in sem_teste if ESCRITA.search(pontes[p]['metodo']) and p in g['chamadores'])
    orfas = sorted(i for i, v in g['ev'].items() if not v['ligada'] and not v['citada_em'])
    com_aba = {t for s, t, r in ar if r == 'exibe' and s not in ('aba:base',)}
    chama = {s for s, t, r in ar if r == 'chama'}
    ui_sem_ponte = sorted(t[3 + len(UI) + 1:] for t in com_aba if t not in chama)
    return {'essenciais': {'E1_ui_chama_ponte_inexistente': sorted(set(e1)), 'E2_evidencia_aponta_arquivo_inexistente': e2,
                           'E3_ponte_de_escrita_chamada_sem_teste': e3},
            'informativos': {'ponte_sem_teste': sem_teste, 'ponte_sem_chamador': sem_chamador, 'ui_de_aba_sem_ponte': ui_sem_ponte,
                             'evidencia_orfa': orfas}}


def resumo_abas(g):
    ar = g['arestas']
    linhas = []
    for aid, rot, rota in ABAS:
        a = 'aba:' + aid
        uis = sorted(t for s, t, r in ar if s == a and r == 'exibe')
        pts = sorted({t for s, t, r in ar if s in uis and r == 'chama'})
        ts = sorted({s for s, t, r in ar if r == 'cobre' and t in uis})
        evs = sorted({s for s, t, r in ar if r == 'sustenta' and (t in uis or t in ts)})
        linhas.append({'id': aid, 'aba': rot, 'rota': rota or '-', 'ui': [u[3:] for u in uis], 'pontes': [p[6:] for p in pts],
                       'testes': [x[6:] for x in ts], 'evidencias': [e[3:] for e in evs]})
    return linhas


def montar(g, an, ab, lac, pend):
    cont, rel = {}, {}
    for v in g['nos'].values():
        cont[v['tipo']] = cont.get(v['tipo'], 0) + 1
    for s, t, r in g['arestas']:
        rel[r] = rel.get(r, 0) + 1
    ev_desc = sorted(e['id'] for e in g['indice'] if e.get('marca') == 'DESCONHECIDO'
                     and not g['ev'][e['id']]['ligada'] and not g['ev'][e['id']]['citada_em'])
    return {'nos': dict(sorted(g['nos'].items())), 'arestas': [list(a) for a in g['arestas']], 'contagem_nos': dict(sorted(cont.items())),
            'contagem_arestas': dict(sorted(rel.items())), 'abas': ab, 'elos_quebrados': an, 'lacunas': lac,
            'pendencias_abertas': pend, 'desconhecido_sem_uso': ev_desc}


def md(d):
    L = ['# Grafo de PRODUÇÃO (só `app/`)', '',
         'Gerado por `python3 -B tools/grafo_producao.py` (determinístico, sem LLM). Não editar à mão. Dados: `docs/grafo/producao.json`.',
         'O grafo de PESQUISA (ProgBase/evidências) é outro: `tools/grafo_conhecimento.py`. Ligação por heurística de texto (nome citado).', '',
         'Nós: ' + ', '.join(f'{k} {v}' for k, v in d['contagem_nos'].items()) + '. Arestas: '
         + ', '.join(f'{k} {v}' for k, v in d['contagem_arestas'].items()) + '.', '',
         '## (a) Por aba', '', '| Aba (rota) | O que faz hoje | UI | Pontes | Testes | Evid. |', '|---|---|---|---|---|---|']
    for a in d['abas']:
        L.append(f"| {a['aba']} (`{a['rota']}`) | {HOJE[a['id']]} | {len(a['ui'])} | {len(a['pontes'])} | {len(a['testes'])} | {len(a['evidencias'])} |")
    L += ['', 'Pontes e evidências por aba (amostra):']
    for a in d['abas']:
        if a['pontes'] or a['evidencias']:
            L.append(f"- **{a['aba']}**: {', '.join(p.split('.')[-1] for p in a['pontes'][:6]) or '-'}{' …' if len(a['pontes']) > 6 else ''}"
                     f"; {', '.join(a['evidencias'][:3]) or '-'}{' …' if len(a['evidencias']) > 3 else ''}")
    falt = [x for x in d['lacunas'] if x['falta']]
    impl = [x for x in d['lacunas'] if not x['falta']]
    n = lambda p: sum(1 for k in d['nos'] if k.startswith(p))
    L += ['', '## (b) IMPLEMENTADO × FALTA', '',
          f"**IMPLEMENTADO:** {n('ponte:')} pontes, {n('ui:')} arquivos de UI, {n('teste:')} testes. Sem rota no app (contrato AGENTS regra 11): "
          + ', '.join(a['aba'] for a in d['abas'] if a['rota'] == '-') + '.',
          f"Lacunas do CATALOGO já resolvidas: {', '.join('#%d' % x['n'] for x in impl) or 'nenhuma'}.", '',
          f"**FALTA — lacunas do original (CATALOGO, {len(falt)}):**"]
    L += [f"- #{x['n']} {x['funcao']}" for x in falt]
    L += ['', f"**FALTA — pendências abertas (PENDENTE.md, {len(d['pendencias_abertas'])}; 12 primeiras):**"]
    L += [f"- {p}" for p in d['pendencias_abertas'][:12]]
    L += ['', f"**FALTA — evidências DESCONHECIDO sem uso ({len(d['desconhecido_sem_uso'])}):** {', '.join(d['desconhecido_sem_uso']) or 'nenhuma'}", '',
          '## (c) Elos quebrados', '', 'ESSENCIAIS (a CI falha se houver; critério no cabeçalho de `tools/grafo_producao.py`):']
    for k, v in d['elos_quebrados']['essenciais'].items():
        L.append(f"- {k}: {len(v)}" + (' — ' + '; '.join(v[:5]) if v else ''))
    L += ['', 'INFORMATIVOS (só relatam):']
    for k, v in d['elos_quebrados']['informativos'].items():
        L.append(f"- {k}: {len(v)}" + (' — ' + ', '.join(x.split('/')[-1] for x in v[:6]) + (' …' if len(v) > 6 else '') if v else ''))
    return '\n'.join(L) + '\n'


def gerar():
    g = construir()
    d = montar(g, analisar(g), resumo_abas(g), lacunas(), pendencias())
    return json.dumps(d, ensure_ascii=False, indent=1, sort_keys=True) + '\n', md(d), d


def main():
    j, m, d = gerar()
    pj, pm = os.path.join(ROOT, 'docs/grafo/producao.json'), os.path.join(ROOT, 'docs/grafo/PRODUCAO.md')
    if '--check' in sys.argv:
        ok = all(os.path.exists(p) for p in (pj, pm)) and open(pj, encoding='utf-8').read() == j and open(pm, encoding='utf-8').read() == m
        quebrados = {k: v for k, v in d['elos_quebrados']['essenciais'].items() if v}
        if not ok:
            print('docs/grafo desatualizado: rode python3 -B tools/grafo_producao.py')
        if quebrados:
            print('elos ESSENCIAIS quebrados:', json.dumps(quebrados, ensure_ascii=False))
        return 0 if ok and not quebrados else 1
    os.makedirs(os.path.dirname(pj), exist_ok=True)
    with open(pj, 'w', encoding='utf-8', newline='\n') as f:
        f.write(j)
    with open(pm, 'w', encoding='utf-8', newline='\n') as f:
        f.write(m)
    print('nos', d['contagem_nos'], 'arestas', d['contagem_arestas'])
    return 0


if __name__ == '__main__':
    sys.exit(main())
