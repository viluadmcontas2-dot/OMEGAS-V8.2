#!/usr/bin/env python3
"""Backlog classificado: o que o ProgBase original envia/lê e o app OMEGAS não usa (D2 PROTOCOLO).

Entradas: vetores.json e comandos.json (gerados pelas outras ferramentas) + varredura textual de app/src/main.
Saídas determinísticas: backlog.json e backlog.md. Marca: DERIVADO (cruzamento de OBS-FIO/OBS-COD com busca de texto
no código do app; "usa" = o nome ou o SerialCode aparece no código-fonte do app — não prova uso em tempo de execução).

Uso: protocolo_backlog.py --repo RAIZ --vetores vetores.json --comandos comandos.json --json backlog.json --md backlog.md
"""
import argparse
import collections
import json
import os
import re
import sys

EXT = ('.kt', '.js', '.json', '.html')


def texto_do_app(raiz):
    base = os.path.join(raiz, 'app', 'src', 'main')
    partes = []
    for d, _, fs in sorted(os.walk(base)):
        for f in sorted(fs):
            if f.endswith(EXT):
                with open(os.path.join(d, f), encoding='utf-8', errors='replace') as fh:
                    partes.append(fh.read())
    return '\n'.join(partes)


def app_menciona(txt, nome, sc):
    if re.search(r'\b%s\b' % re.escape(nome), txt):
        return True
    if sc is not None and sc >= 0x100:
        return re.search(r'0x0*%X\b' % sc, txt, re.I) is not None
    return False


def montar(raiz, vetores, comandos):
    txt = texto_do_app(raiz)
    itens = [i for i in vetores['itens'] if i['modulo'] == 'TSTREAMDATI']
    grupos = collections.OrderedDict((k, []) for k in ('lido_e_usado', 'lido_e_nao_usado', 'so_nak', 'nao_lido_na_captura'))
    for i in itens:
        usa = app_menciona(txt, i['nome'], i['serial_code'])
        pres = i['presenca_na_ecu_capturada']
        reg = dict(nome=i['nome'], sc=i['serial_hex'], forma=i['forma'], tipo=i['tipo_dado'], dim=i['dimensoes'],
                   familia=i['familia_ecu'], somente_leitura=i['somente_leitura'], app_menciona=usa,
                   leituras_ack=(i['log'] or {}).get('leituras_ack', 0),
                   escritas_do_original=(i['log'] or {}).get('escritas_no_log', 0))
        if pres in ('confirmada', 'ack_com_tamanho_diferente'):
            grupos['lido_e_usado' if usa else 'lido_e_nao_usado'].append(reg)
        elif pres == 'so_nak':
            grupos['so_nak'].append(reg)
        else:
            grupos['nao_lido_na_captura'].append(reg)
    cmds = [c for c in comandos['comandos'] if c['omegas']['usa'] in ('nao', 'parcial')]
    por_classe = collections.Counter(c.get('backlog', {}).get('classe', '?') for c in cmds)
    return collections.OrderedDict(
        versao_esquema=1,
        criterio='app_menciona = o nome ou o SerialCode (>= 0x100) aparece em app/src/main (*.kt, *.js, *.json, *.html)',
        comandos_nao_usados=[dict(id=c['id'], nome=c['nome'], quadro=c['quadro'], usa=c['omegas']['usa'],
                                  no_log=(c.get('no_log') or {}).get('total'), **c.get('backlog', {})) for c in cmds],
        comandos_por_classe=dict(sorted(por_classe.items())),
        enlace=comandos['enlace_original_x_omegas'],
        parametros={k: dict(total=len(v), itens=v) for k, v in grupos.items()},
    )


def para_markdown(b):
    L = ['# Backlog: o que o original envia e o OMEGAS não usa (gerado — não editar à mão)', '',
         'Critério de "usa": ' + b['criterio'] + '. Recomendações são INFERIDO (decisão de projeto), os fatos vêm de '
         '`comandos.json`/`vetores.json`.', '',
         'Classes de comando: **A** leitura/diagnóstico sem risco · **B** escrita de calibração · **C** atuadores/bancada · '
         '**D** firmware/boot/baud (nunca no app) · **E** legado/sem valor. Contagem: %s.' % ', '.join(
             '%s=%d' % kv for kv in b['comandos_por_classe'].items()), '',
         '## 1. Comandos', '', '| id | comando | quadro | no log | classe | prioridade | risco | recomendação |',
         '|---|---|---|---|---|---|---|---|']
    for c in b['comandos_nao_usados']:
        L.append('| `%s` | %s | %s | %s | %s | %s | %s | %s |' % (
            c['id'], c['nome'], c['quadro'], '—' if c['no_log'] is None else c['no_log'], c.get('classe', '?'),
            c.get('prioridade', '?'), c.get('risco', '?'), c.get('recomendacao', '')))
    L += ['', '## 2. Comportamentos de enlace: original x OMEGAS', '', '| item | original | OMEGAS | referência | situação |',
          '|---|---|---|---|---|']
    for e in b['enlace']:
        L.append('| %s | %s | %s | %s | %s |' % (e['item'], e['original'], e['omegas'], e['ref'], e['classe']))
    p = b['parametros']
    L += ['', '## 3. Parâmetros (TStreamDati, 314)', '',
          '| grupo | quantidade | leitura |', '|---|---|---|',
          '| lido pelo original e mencionado no app | %d | o app já conhece |' % p['lido_e_usado']['total'],
          '| **lido pelo original e NÃO mencionado no app** | %d | candidatos de backlog (lista abaixo) |' % p['lido_e_nao_usado']['total'],
          '| só NAK na captura | %d | não existe nesta ECU/firmware |' % p['so_nak']['total'],
          '| nunca pedido na captura | %d | telas/ações que o dono não abriu; DESCONHECIDO se existem na ECU |' % p['nao_lido_na_captura']['total'], '',
          '### 3.1 Lidos pelo original e não mencionados no app', '',
          '| SC | nome | forma | tipo | dim | leituras | escritas do original | só leitura |', '|---|---|---|---|---|---|---|---|']
    for r in p['lido_e_nao_usado']['itens']:
        d = r['dim']
        dim = ('%sx%s' % (d['linhas'], d['colunas'])) if 'linhas' in d else str(d['n'])
        L.append('| %s | `%s` | %s | %s | %s | %d | %d | %s |' % (r['sc'], r['nome'], r['forma'], r['tipo'], dim, r['leituras_ack'],
                                                               r['escritas_do_original'], 'sim' if r['somente_leitura'] else 'não'))
    return '\n'.join(L) + '\n'


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument('--repo', required=True)
    ap.add_argument('--vetores', required=True)
    ap.add_argument('--comandos', required=True)
    ap.add_argument('--json')
    ap.add_argument('--md')
    a = ap.parse_args(argv)
    with open(a.vetores, encoding='utf-8') as fh:
        v = json.load(fh)
    with open(a.comandos, encoding='utf-8') as fh:
        c = json.load(fh)
    b = montar(a.repo, v, c)
    if a.json:
        with open(a.json, 'w', encoding='utf-8') as fh:
            json.dump(b, fh, ensure_ascii=False, indent=1)
            fh.write('\n')
    if a.md:
        with open(a.md, 'w', encoding='utf-8') as fh:
            fh.write(para_markdown(b))
    print({k: v['total'] for k, v in b['parametros'].items()}, b['comandos_por_classe'])
    return 0


if __name__ == '__main__':
    sys.exit(main())
