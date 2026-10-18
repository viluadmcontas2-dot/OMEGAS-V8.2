#!/usr/bin/env python3
"""Backlog explícito do que ainda NÃO foi decompilado/analisado no ProgBase.exe.

Entradas: funções descobertas (inventario_codigo.funcoes), lacunas de código, telas.json, i18n.json, classes.json.
Saída: funcoes.json (funções das unidades do programa), funcoes.md (resumo por unidade),
backlog.md (resumo + prioridades), backlog_funcoes.md (lista completa de funções sem nome, com endereço),
backlog_lacunas.md (trechos nunca alcançados)."""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from inventario_comum import SAIDA, escrever, escrever_json, h  # noqa: E402
import inventario_codigo as cdg  # noqa: E402


def alcance_por_handler(telas):
    """{endereço hex da subfunção: [Classe.Handler...]} a partir de telas.json (subfuncoes de cada handler)."""
    out = {}
    for t in telas:
        for n, hd in t['handlers'].items():
            for sf in hd.get('subfuncoes', []):
                out.setdefault(sf['endereco'], set()).add('%s.%s' % (t['classe'], n))
    return out


def gerar(b, cl, cod, funcs, meta, un, grupos, saida=SAIDA):
    telas = json.load(open(os.path.join(saida, 'telas.json'), encoding='utf-8'))['telas']
    i18n = json.load(open(os.path.join(saida, 'i18n.json'), encoding='utf-8'))
    por_handler = alcance_por_handler(telas)
    app = [f for f in funcs if f['regiao'] == 'programa']
    for f in app:
        f['alcancada_por_handlers'] = sorted(por_handler.get(f['endereco'], []))[:6]
        if 'nome' in f:
            f['status'] = 'nomeada'
        elif f['alcancada_por_handlers']:
            f['status'] = 'sem_nome_resumida_por_handler'
        elif f.get('citada_em'):
            f['status'] = 'sem_nome_citada_em_doc'
        else:
            f['status'] = 'sem_nome_sem_analise'
    lac = cdg.lacunas(cod, grupos)
    fins = [u[0] for u in un]
    # ---- funcoes.json / funcoes.md
    meta = dict(meta)
    meta['status'] = {s: sum(1 for f in app if f['status'] == s) for s in ('nomeada', 'sem_nome_resumida_por_handler', 'sem_nome_citada_em_doc', 'sem_nome_sem_analise')}
    meta['fonte_nome'] = {}
    for f in app:
        if 'fonte_nome' in f:
            meta['fonte_nome'][f['fonte_nome']] = meta['fonte_nome'].get(f['fonte_nome'], 0) + 1
    meta['lacunas'] = {'n': len(lac), 'bytes': sum(x[1] for x in lac)}
    escrever_json(os.path.join(saida, 'funcoes.json'), {'meta': meta, 'funcoes': app})
    por_un = {}
    for f in app:
        u = por_un.setdefault(f['unidade'] or '(?)', {'n': 0, 'nome': 0, 'bytes': 0, 'bytes_sem_nome': 0, 'sem_nome': 0})
        u['n'] += 1
        u['bytes'] += f['tamanho_ate_ultima_instrucao']
        if 'nome' in f:
            u['nome'] += 1
        else:
            u['sem_nome'] += 1
            u['bytes_sem_nome'] += f['tamanho_ate_ultima_instrucao']
    L = ['# Funções do ProgBase.exe nas unidades do programa', '',
         'Gerado por `tools/progbase/inventario_backlog.py` (SHA-256 `%s`).' % b.sha256, '',
         '- **Método (OBS-COD):** descida recursiva a partir do ponto de entrada, de todos os nomes conhecidos (RTTI, exportações PE, vtables) e das chamadas diretas; '
         'varredura de lacunas que começam com prólogo de função. **Não segue** chamadas virtuais/indiretas nem tabelas de despacho não reconhecidas.',
         '- **Unidade (DERIVADO):** fronteiras dadas pelos marcadores `@@Unidade@Initialize/Finalize` exportados; faixas do programa: %s. '
         'Fora delas há RTL/VCL/TeeChart/Indy (não inventariadas: só %d funções de biblioteca foram alcançadas).' % (
             ', '.join('%s–%s' % tuple(g) for g in meta['faixas_programa']), meta['n_funcoes_biblioteca_alcancadas']),
         '- **Funções nas unidades do programa:** %d; por status: %s.' % (len(app), ', '.join('%s=%d' % kv for kv in meta['status'].items())),
         '- Fonte dos nomes: %s.' % ', '.join('%s=%d' % kv for kv in sorted(meta['fonte_nome'].items())), '',
         '| Unidade | Funções | Nomeadas | Sem nome | Bytes (últ. instrução) | Bytes sem nome |', '|---|---:|---:|---:|---:|---:|']
    for u in sorted(por_un):
        x = por_un[u]
        L.append('| %s | %d | %d | %d | %d | %d |' % (u, x['n'], x['nome'], x['sem_nome'], x['bytes'], x['bytes_sem_nome']))
    escrever(os.path.join(saida, 'funcoes.md'), '\n'.join(L))
    # ---- backlog_funcoes.md (todas as sem nome)
    sem = [f for f in app if 'nome' not in f]
    sem.sort(key=lambda f: int(f['endereco'], 16))
    B = ['# Funções sem nome nas unidades do programa (lista completa)', '',
         'Gerado por `tools/progbase/inventario_backlog.py`. %d funções. "Tam." = bytes até a última instrução alcançada (limite inferior); '
         '"Status" diz se algum handler de tela já as resume (`telas.json`) ou se algum documento as cita.' % len(sem), '',
         '| Endereço | Tam. | Unidade | Origem | Status | Alcançada por handler | Citada em |', '|---|---:|---|---|---|---|---|']
    for f in sem:
        B.append('| %s | %d | %s | %s | %s | %s | %s |' % (f['endereco'], f['tamanho_ate_ultima_instrucao'], f['unidade'], f['origem'], f['status'].replace('sem_nome_', ''),
                                                           ', '.join(f['alcancada_por_handlers'][:2]), ', '.join(f.get('citada_em', [])[:2])))
    escrever(os.path.join(saida, 'backlog_funcoes.md'), '\n'.join(B))
    # ---- lacunas
    import collections
    por_cl = collections.Counter()
    bytes_cl = collections.Counter()
    for a, n, c in lac:
        por_cl[c] += 1
        bytes_cl[c] += n
    Z = ['# Trechos de código nunca alcançados (faixas do programa)', '',
         'Gerado por `tools/progbase/inventario_backlog.py`. Trecho = ≥ 16 bytes consecutivos nunca decodificados pela descida recursiva. '
         'Classe (INFERIDO): preenchimento / texto / tabela de ponteiros / **desconhecido** (dados ou código só alcançável por chamada indireta).', '',
         '| Classe | Trechos | Bytes |', '|---|---:|---:|']
    for c in sorted(por_cl):
        Z.append('| %s | %d | %d |' % (c, por_cl[c], bytes_cl[c]))
    Z += ['', '## Trechos "desconhecido" (todos)', '', '| Endereço | Bytes | Unidade |', '|---|---:|---|']
    for a, n, c in lac:
        if c == 'desconhecido':
            Z.append('| %s | %d | %s |' % (h(a), n, cdg.unidade_de(a, un, fins)))
    escrever(os.path.join(saida, 'backlog_lacunas.md'), '\n'.join(Z))
    # ---- backlog.md
    ch_sem = [k for k in i18n['chaves'] if not k['componentes'] and not k['usado_em_codigo']]
    nao_res = [(t['classe'], e['componente'], e['evento'], e['handler']) for t in telas for k in ('menus', 'acoes', 'controles', 'dialogos', 'timers')
               for e in [ev | {'componente': it['componente']} for it in t[k] for ev in it['eventos']] if not e['resolvido']]
    n_hand = sum(len(t['handlers']) for t in telas)
    n_cur = sum(1 for t in telas for hd in t['handlers'].values() if hd['o_que_faz']['origem'] == 'curado')
    n_auto_cfg = sum(1 for t in telas if t['classe'] == 'TFormConfig' for hd in t['handlers'].values() if hd['o_que_faz']['origem'] != 'curado')
    K = ['# Backlog: o que ainda NÃO foi decompilado / analisado do ProgBase.exe', '',
         'Gerado por `tools/progbase/inventario_backlog.py` (SHA-256 `%s`). Este arquivo é a lista de trabalho; os números vêm da extração e mudam se alguém nomear funções '
         '(`tools/progbase/dados/nomes_manuais.json`), curar handlers (`descricoes_handlers.json`) ou atualizar a cobertura (`dados/cobertura_externa.json`).' % b.sha256, '',
         '## 1. Resumo', '',
         '| Item | Quantidade | Marca |', '|---|---:|---|',
         '| Funções alcançadas nas unidades do programa | %d | OBS-COD |' % len(app),
         '| …com nome (RTTI/exportação/vtable/manual) | %d | OBS-COD (nome) |' % meta['status']['nomeada'],
         '| …sem nome, mas resumidas por algum handler de tela (cadeia ≤ 3) | %d | OBS-COD (fatos); sentido INFERIDO |' % meta['status']['sem_nome_resumida_por_handler'],
         '| …sem nome, citadas em documento já existente | %d | OBS-COD |' % meta['status']['sem_nome_citada_em_doc'],
         '| …**sem nome e sem nenhuma análise** | **%d** | DESCONHECIDO |' % meta['status']['sem_nome_sem_analise'],
         '| Handlers de tela inventariados | %d (curados: %d; automáticos: %d, dos quais %d em TFormConfig) | — |' % (n_hand, n_cur, n_hand - n_cur, n_auto_cfg),
         '| Eventos do DFM sem método publicado correspondente | %d | OBS-COD |' % len(nao_res),
         '| Chaves de texto (i18n) sem ligação a componente nem a chamada | %d de %d | DESCONHECIDO |' % (len(ch_sem), len(i18n['chaves'])),
         '| Trechos nunca alcançados nas faixas do programa | %d (%d bytes), dos quais "desconhecido": %d | INFERIDO |' % (
             len(lac), sum(x[1] for x in lac), por_cl.get('desconhecido', 0)), '',
         '## 2. Prioridade A: funções sem nome que handlers de tela já alcançam (maiores primeiro)', '',
         'São o caminho mais curto para entender uma tela: o handler chama e a função não tem nome. Top 150 por tamanho (lista completa em `backlog_funcoes.md`).', '',
         '| Endereço | Tam. | Unidade | Handler(s) que a alcançam |', '|---|---:|---|---|']
    pa = sorted([f for f in sem if f['status'] == 'sem_nome_resumida_por_handler'], key=lambda f: -f['tamanho_ate_ultima_instrucao'])[:150]
    for f in pa:
        K.append('| %s | %d | %s | %s |' % (f['endereco'], f['tamanho_ate_ultima_instrucao'], f['unidade'], ', '.join('`%s`' % x for x in f['alcancada_por_handlers'][:3])))
    K += ['', '## 3. Prioridade B: maiores funções sem nome e sem análise (top 150)', '',
          '| Endereço | Tam. | Unidade | Origem |', '|---|---:|---|---|']
    pb = sorted([f for f in sem if f['status'] == 'sem_nome_sem_analise'], key=lambda f: -f['tamanho_ate_ultima_instrucao'])[:150]
    for f in pb:
        K.append('| %s | %d | %s | %s |' % (f['endereco'], f['tamanho_ate_ultima_instrucao'], f['unidade'], f['origem']))
    K += ['', '## 4. Por unidade: onde há mais função sem nome (bytes)', '', '| Unidade | Sem nome | Bytes sem nome |', '|---|---:|---:|']
    for u, x in sorted(por_un.items(), key=lambda kv: -kv[1]['bytes_sem_nome'])[:30]:
        K.append('| %s | %d | %d |' % (u, x['sem_nome'], x['bytes_sem_nome']))
    K += ['', '## 5. Handlers de tela só com descrição automática (INFERIDO), fora de TFormConfig', '',
          '| Tela | Handler | Endereço | Bytes |', '|---|---|---|---:|']
    for t in telas:
        if t['classe'] == 'TFormConfig':
            continue
        for n, hd in t['handlers'].items():
            if hd['o_que_faz']['origem'] != 'curado' and not hd['vazio']:
                K.append('| %s | `%s` | %s | %d |' % (t['classe'], n, hd['endereco'], hd['tamanho_bytes']))
    K += ['', '## 6. Chaves de texto sem ligação identificada', '', ', '.join('`%s`' % k['id'] for k in ch_sem) or '(nenhuma)', '',
          '## 7. Eventos do DFM sem método publicado', '']
    K += ['- %s.%s %s → `%s`' % x for x in nao_res] or ['(nenhum)']
    K += ['', '## 8. Estruturas e recursos ainda não decodificados', '',
          '- `TEELCDFONT` (267 720 B) e `TEELEDFONT` (24 448 B): fontes TrueType embutidas; reconhecidas pelo cabeçalho sfnt, **não extraídas** como arquivo (o repositório não versiona o conteúdo). DESCONHECIDO o uso exato.',
          '- `DVCLAL` (16 B): marcador de licença da VCL do Delphi; conteúdo opaco, irrelevante para o app.',
          '- Imagens/ícones/cursores/versão em RT_BITMAP/RT_ICON/RT_GROUP_ICON/RT_VERSION: **não** catalogados por este inventário (só RT_RCDATA e RT_STRING).',
          '- Tabelas de interface (vmt-84), de automação (vmt-80) e de inicialização (vmt-76) das classes: não decodificadas; os campos existem no `classes.json` só se vierem de RTTI de campos/métodos/propriedades.',
          '- Propriedades publicadas de componentes: extraídas do TypeInfo (`classes.json`); valores ligados por DFM foram usados, mas **identificadores de evento sem método** aparecem na seção 7.',
          '- Vetores/escalares da ECU (`TStreamDati`, 320 campos) e protocolo: pertencem ao inventário de protocolo (docs/progbase/protocolo).',
          '- Corpo da maioria dos 258 handlers de `TFormConfig`: pertence a docs/progbase/abas.', '',
          '## 9. Limitações conhecidas do método', '',
          '- A descida recursiva não resolve `call [reg+off]` (virtuais) nem despachos além de `jmp [reg*4+tab]` com `cmp` anterior: funções só alcançáveis assim ficam fora ou entram como "varredura" (origem). INFERIDO.',
          '- O limite superior de cada função é o próximo início conhecido; funções com dados no meio têm o tamanho superestimado ("tamanho_ate_proximo") — por isso o resumo usa só instruções realmente alcançadas.',
          '- Os rótulos de "regiao" separam programa × biblioteca pelos marcadores `@@Unidade@Initialize`; o limite inferior 0x405DC0 (antes dele: startup/RTL) é INFERIDO.']
    escrever(os.path.join(saida, 'backlog.md'), '\n'.join(K))
    return meta, len(sem), len(lac)
