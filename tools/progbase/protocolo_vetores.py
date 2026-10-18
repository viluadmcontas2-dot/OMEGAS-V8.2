#!/usr/bin/env python3
"""Catálogo de vetores/parâmetros do ProgBase (TStreamDati e demais módulos de dados) — D2 PROTOCOLO.

Entradas: ProgBase.exe (obrigatório; só leitura de recursos) e, opcionalmente, uma captura Portmon.
Saídas determinísticas (ordenadas, sem datas, sem caminhos locais): vetores.json e vetores.md.

Marcas por afirmação: OBS-FIO (visto na captura), OBS-COD (visto no executável/DFM), DERIVADO (cálculo sobre
OBS-*), INFERIDO, DESCONHECIDO. Nada aqui contorna licença/criptografia: só descreve parâmetros de calibração.

Uso: protocolo_vetores.py --exe ProgBase.exe [--log PortmonLOGNOVO.LOG] --json vetores.json --md vetores.md
"""
import argparse
import collections
import hashlib
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import protocolo_dfm as dfm  # noqa: E402
import protocolo_log as plog  # noqa: E402

CLASSES_DADO = ('TAebNumber', 'TAebVector', 'TAebMatrix')
# Padrões de propriedade quando o DFM omite (OBS-COD + OBS-FIO): DataLength=1 confirmado no fio em 346 leituras.
PADRAO_DATALENGTH = 1

# Unidades/rotulos: só onde há prova no fio (docs do protocolo-landi 18, OBS-FIO) ou no DFM (ValueMin/Max não
# trazem unidade). Tudo mais fica `null` = DESCONHECIDO (nunca 0).
UNIDADES_OBS_FIO = {
    'PETR_INJ_TBP': 'ms', 'PETR_INJ_TBUF': 'ms', 'PETR_INJ_TBUF_GAS': 'ms', 'PETR_INJ_TBUF_GAS_PREV': 'ms',
    'MNFLD_PRESS_THD': 'bar', 'MNFLD_PRESS_BUF': 'bar', 'MNFLD_PRESS_BUF_GAS': 'bar',
    'MNFLD_PRESS_BUF_GAS_PREV': 'bar', 'MUL_ACT': 'fator K (adimensional)',
}


def _tipo(dl, signed):
    return ('s' if signed else 'u') + str(8 * dl)


def _hex(sc):
    return None if sc is None else '0x%04X' % sc


def _formula(coef):
    a, b, c, d = coef
    return {
        'raw_para_fisico': '(a*raw + b) / (c*raw + d)',
        'fisico_para_raw': '(b - fis*d) / (fis*c - a)',
        'coef': [a, b, c, d],
        'proveniencia': 'OBS-COD rotinas 0x009757DC (raw->fis) e 0x009752C8 (fis->raw), x87',
    }


def objetos_de_dados(exe):
    """Todos os TAebNumber/Vector/Matrix de todos os formulários, na ordem (recurso, pré-ordem)."""
    out = []
    for nome_rec, blob in sorted(dfm.recursos_dfm(exe).items()):
        objs, _ = dfm.parse_tpf0(blob)
        for o in objs:
            if o['classe'] in CLASSES_DADO:
                out.append((nome_rec, o))
    return out


def _forma(o):
    p, c = o['props'], o['classe']
    tipo = (p.get('Type') or {}).get('id')
    if c == 'TAebNumber':
        return {'ntVectorElement': 'elemento_de_vetor', 'ntMatrixElement': 'elemento_de_matriz'}.get(tipo, 'numero')
    if c == 'TAebVector':
        return 'linha_de_matriz' if tipo == 'mtMatrix' else 'vetor'
    return 'matriz'


def _cmd_fio(o, forma):
    """Quadros v2 de leitura e escrita derivados do código (OBS-COD: Number.Read 0x009771B4, Vector.Read
    0x00979898, Matrix.Read 0x0097BDD4 e as escritas espelhadas). `lo hi` = SerialCode little-endian."""
    p = o['props']
    sc = p.get('SerialCode', 0)
    lo, hi = sc & 0xFF, sc >> 8
    row, col = p.get('RowIndex', -1), p.get('ColIndex', -1)
    dl = p.get('DataLength', PADRAO_DATALENGTH)
    r, e = {}, {}
    if o['classe'] == 'TAebNumber':
        # OBS-COD Number.Read 0x009771B4: a escolha depende só de RowIndex/ColIndex (não do Type).
        if row < 0:
            r = {'op': '09', 'quadro': '09 %02X %02X ck' % (lo, hi), 'rotina': 'GetNumber 0x00924994'}
            e = {'op': '%02X' % (0x10 | (dl + 1)), 'quadro': '%02X %02X %02X <valor %dB> ck' % (
                0x10 | (dl + 1), lo, hi, dl), 'rotina': 'SetNumber 0x00925AFC'}
        elif col < 0:
            r = {'op': '0A', 'quadro': '0A %02X %02X %02X ck' % (lo, hi, row & 0xFF),
                 'rotina': 'GetNumber+idx 0x00924C74'}
            e = {'op': '%02X' % (0x10 | (dl + 2)), 'quadro': '%02X %02X %02X %02X <valor %dB> ck' % (
                0x10 | (dl + 2), lo, hi, row & 0xFF, dl), 'rotina': 'SetNumber+idx 0x00925D18'}
        else:
            r = {'op': '0B', 'quadro': '0B %02X %02X %02X %02X ck' % (lo, hi, row & 0xFF, col & 0xFF),
                 'rotina': 'GetNumber+linha+coluna 0x00924F60'}
            e = {'op': '%02X' % (0x10 | (dl + 3)), 'quadro': '%02X %02X %02X %02X %02X <valor %dB> ck' % (
                0x10 | (dl + 3), lo, hi, row & 0xFF, col & 0xFF, dl), 'rotina': 'SetNumber+linha+coluna 0x00925F44'}
    elif forma == 'vetor':
        n = p.get('ArrayDimension', 1)
        r = {'op': '29', 'quadro': '29 %02X %02X ck' % (lo, hi), 'rotina': 'GetVector 0x00925258',
             'resposta_bytes': n * dl}
        pay = n * dl + 1
        e = {'op': '%02X' % (0x30 | pay) if pay < 7 else '37',
             'quadro': ('%02X' % (0x30 | pay) if pay < 7 else '37 <n+1>') + ' %02X %02X <%d B> ck' % (lo, hi, n * dl),
             'rotina': 'SetVector 0x0092617C'}
    elif forma == 'linha_de_matriz':
        r = {'op': '2A', 'quadro': '2A %02X %02X <linha> ck' % (lo, hi), 'rotina': 'GetVector+linha 0x00925570'}
        e = {'op': '3x', 'quadro': '3x %02X %02X <linha> <dados> ck' % (lo, hi), 'rotina': 'SetVector+linha 0x009263B0'}
    else:
        r = {'op': '2A', 'quadro': '2A %02X %02X <linha> ck (uma por linha)' % (lo, hi),
             'rotina': 'GetVector+linha 0x00925570 (linhas>1 e colunas>1)'}
        e = {'op': '1x', 'quadro': '%02X %02X %02X <linha> <coluna> <valor> ck (por elemento)' % (
            0x10 | (dl + 3), lo, hi), 'rotina': 'SetNumber+linha+coluna 0x00925F44'}
    return {'leitura': r, 'escrita': e}


def _conversao(p):
    tr = (p.get('Transformation') or {}).get('id', 'ttNone')
    coef = p.get('Coeffs') or [1.0, 0.0, 0.0, 1.0]
    out = {'tipo': tr, 'coef': coef, 'precisao': p.get('Precision'), 'tabela': p.get('ConversionTable')}
    if tr == 'ttFormula':
        out['formula'] = _formula(coef)
        a, b, c, d = coef
        if (a, b, c) == (1.0, 0.0, 0.0):
            out['resumo'] = 'fisico = raw / %g' % d
    elif tr == 'ttNone':
        out['resumo'] = 'fisico = raw (identidade)'
    return out


def _evidencia_log(item_props, forma, trans_idx):
    """Cruza o objeto com as leituras v2 da captura que o endereçam exatamente (op + SerialCode + índices)."""
    sc = item_props.get('SerialCode', 0)
    if trans_idx is None:
        return None
    lo, hi = sc & 0xFF, sc >> 8
    row, col = item_props.get('RowIndex', -1), item_props.get('ColIndex', -1)
    if item_props.get('_classe') == 'TAebNumber':
        if row < 0:
            alvo = [(0x09, '')]
        elif col < 0:
            alvo = [(0x0A, '%02x' % (row & 0xFF))]
        else:
            alvo = [(0x0B, '%02x%02x' % (row & 0xFF, col & 0xFF))]
        casa = lambda op, ex: (op, ex) in alvo  # noqa: E731
    elif forma in ('vetor',):
        casa = lambda op, ex: op == 0x29  # noqa: E731
    else:
        casa = lambda op, ex: op == 0x2A  # noqa: E731
    n_ack = n_nak = 0
    primeira = None
    tam = collections.Counter()
    for op, tab in trans_idx.items():
        if op == 'W':
            continue
        for (a, b, ex), lst in tab.items():
            if (a, b) != (lo, hi) or not casa(op, ex):
                continue
            for (cl, line, nresp) in lst:
                primeira = line if primeira is None or line < primeira else primeira
                if cl == 'ACK_53':
                    n_ack += 1
                    tam[nresp] += 1
                elif cl == 'NAK_CA':
                    n_nak += 1
    w = trans_idx.get('W', {}).get((lo, hi), [])
    escritas = {'escritas_no_log': len(w), 'escritas_ack': sum(1 for x in w if x[0] == 'ACK_53'),
                'primeira_escrita_linha': min((x[1] for x in w), default=None)}
    if n_ack + n_nak == 0:
        return dict({'aparece': False}, **escritas)
    dl = item_props.get('DataLength', PADRAO_DATALENGTH)
    if item_props.get('_classe') == 'TAebNumber':
        esperado = dl
    elif forma == 'vetor':
        esperado = item_props.get('ArrayDimension', 1) * dl
    else:
        esperado = (item_props.get('ColCount') or item_props.get('ArrayDimension') or 1) * dl
    return {'aparece': True, 'leituras_ack': n_ack, 'leituras_nak': n_nak, 'primeira_linha': primeira,
            'tamanhos_resposta': dict(sorted(tam.items())), 'tamanho_esperado_dfm': esperado,
            'tamanho_confere_com_dfm': (all(k == esperado for k in tam) if tam else None), **escritas}


def _indice_log(trans):
    """{op: {(lo,hi,extra): [(classe, linha, n_dados)]}} só para leituras v2 (op 09/0A/0B/29/2A)."""
    idx = collections.defaultdict(lambda: collections.defaultdict(list))
    esc = collections.defaultdict(list)
    for t in trans:
        r = t['req']
        if len(r) < 4:
            continue
        if r[0] in (0x09, 0x0A, 0x0B, 0x29, 0x2A):
            if len(r) != (r[0] & 7) + 3:
                continue
            cl, _ = plog.classificar(t)
            n = t['resp'][len(r) + 1] if cl == 'ACK_53' else None
            idx[r[0]][(r[1], r[2], r[3:-1].hex())].append((cl, t['line'], n))
        elif 0x10 <= r[0] <= 0x16 or 0x30 <= r[0] <= 0x36:
            esc[(r[1], r[2])].append((plog.classificar(t)[0], t['line'], r[0]))
        elif r[0] in (0x17, 0x37) and len(r) > 4:
            esc[(r[2], r[3])].append((plog.classificar(t)[0], t['line'], r[0]))
    idx['W'] = esc
    return idx


def catalogo(exe, log=None):
    trans = plog.transacoes(log) if log else None
    idx = _indice_log(trans) if trans is not None else None
    por_sc = collections.defaultdict(list)
    itens = []
    for modulo, o in objetos_de_dados(exe):
        p = o['props']
        forma = _forma(o)
        sc = p.get('SerialCode')
        dl = p.get('DataLength', PADRAO_DATALENGTH)
        signed = bool(p.get('Signed', False))
        dim = p.get('ArrayDimension')
        item = collections.OrderedDict()
        item['modulo'] = modulo
        item['nome'] = o['nome']
        item['classe'] = o['classe']
        item['forma'] = forma
        item['serial_code'] = sc if sc is not None else (0 if modulo == 'TSTREAMDATI' else None)
        item['serial_hex'] = _hex(item['serial_code'])
        item['serial_code_omitido_no_dfm'] = sc is None
        item['familia_ecu'] = 'LR' if o['nome'].endswith('_LR') else 'padrao'
        item['tipo_dado'] = _tipo(dl, signed)
        item['tamanho_elemento'] = dl
        item['data_length_omitido_no_dfm'] = 'DataLength' not in p
        item['mascara'] = p.get('DataMask')
        if forma in ('vetor', 'linha_de_matriz'):
            item['dimensoes'] = {'n': dim}
        elif forma == 'matriz':
            item['dimensoes'] = {'linhas': p.get('RowCount'), 'colunas': p.get('ColCount')}
        else:
            item['dimensoes'] = {'n': 1}
        item['indices'] = {'linha': p.get('RowIndex', -1), 'coluna': p.get('ColIndex', -1)}
        item['conversao'] = _conversao(p)
        item['unidade'] = UNIDADES_OBS_FIO.get(o['nome'])
        item['unidade_marca'] = 'OBS-FIO (doc 18 do protocolo-landi)' if item['unidade'] else 'DESCONHECIDO'
        item['min'] = p.get('ValueMin')
        item['max'] = p.get('ValueMax')
        item['padrao_fabrica'] = p.get('DefaultValue')
        item['padrao_fabrica_marca'] = 'OBS-COD (DefaultValue do DFM)' if 'DefaultValue' in p else 'DESCONHECIDO'
        item['arquivo_ini'] = ({'secao': p.get('FileSection'), 'chave': p.get('FileKeyName')}
                               if 'FileKeyName' in p else None)
        item['somente_leitura'] = bool(p.get('ReadOnly', False))
        item['fio_v2'] = _cmd_fio(o, forma)
        item['log'] = _evidencia_log(dict(p, SerialCode=item['serial_code'] or 0, _classe=o['classe']), forma, idx)
        lg = item['log']
        if lg is None:
            item['presenca_na_ecu_capturada'] = 'sem_captura'
        elif not lg['aparece']:
            item['presenca_na_ecu_capturada'] = 'nao_lida'
        elif lg['leituras_ack'] == 0:
            item['presenca_na_ecu_capturada'] = 'so_nak'
        elif lg['tamanho_confere_com_dfm']:
            item['presenca_na_ecu_capturada'] = 'confirmada'
        else:
            item['presenca_na_ecu_capturada'] = 'ack_com_tamanho_diferente'
        itens.append(item)
        if item['serial_code'] is not None:
            por_sc[item['serial_code']].append(len(itens) - 1)
    # colisões: mesmo SerialCode em mais de um objeto (famílias padrão x LR ou módulos diferentes)
    for sc, ids in por_sc.items():
        outros = sorted('%s/%s' % (itens[i]['modulo'], itens[i]['nome']) for i in ids)
        for i in ids:
            itens[i]['colide_com'] = [x for x in outros if x != '%s/%s' % (itens[i]['modulo'], itens[i]['nome'])]
    itens.sort(key=lambda x: (x['modulo'] != 'TSTREAMDATI', x['modulo'], x['serial_code'] if x['serial_code'] is not None
                              else 10 ** 6, x['nome']))
    resumo = collections.OrderedDict()
    resumo['total'] = len(itens)
    resumo['por_modulo'] = dict(sorted(collections.Counter(i['modulo'] for i in itens).items()))
    resumo['por_forma'] = dict(sorted(collections.Counter(i['forma'] for i in itens).items()))
    resumo['presenca'] = dict(sorted(collections.Counter(i['presenca_na_ecu_capturada'] for i in itens
                                                         if i['modulo'] == 'TSTREAMDATI').items()))
    resumo['por_transformacao'] = dict(sorted(collections.Counter(i['conversao']['tipo'] for i in itens).items()))
    if idx is not None:
        so = [i for i in itens if i['modulo'] == 'TSTREAMDATI']
        resumo['tstreamdati_total'] = len(so)
        resumo['tstreamdati_no_log'] = sum(1 for i in so if i['log'] and i['log']['aparece'])
        resumo['tstreamdati_so_nak'] = sum(1 for i in so if i['log'] and i['log']['aparece']
                                           and i['log']['leituras_ack'] == 0)
    saida = collections.OrderedDict()
    saida['versao_esquema'] = 1
    saida['exe_sha256'] = hashlib.sha256(open(exe, 'rb').read()).hexdigest()
    saida['log_sha256'] = plog.sha256_arquivo(log) if log else None
    saida['legenda'] = {'OBS-FIO': 'visto na captura', 'OBS-COD': 'visto no executável/DFM', 'DERIVADO':
                        'calculado sobre OBS', 'INFERIDO': 'dedução', 'DESCONHECIDO': 'sem prova'}
    saida['resumo'] = resumo
    saida['itens'] = itens
    return saida


def para_markdown(cat):
    L = []
    L.append('# Catálogo de vetores e parâmetros (gerado — não editar à mão)')
    L.append('')
    L.append('Gerado por `tools/progbase/protocolo_vetores.py` a partir do `ProgBase.exe` (sha256 `%s`)%s.' % (
        cat['exe_sha256'], (' e da captura Portmon (sha256 `%s`)' % cat['log_sha256']) if cat['log_sha256'] else ''))
    L.append('')
    r = cat['resumo']
    L.append('- Objetos de dados: **%d** (por módulo: %s).' % (r['total'], ', '.join(
        '%s=%d' % kv for kv in r['por_modulo'].items())))
    L.append('- Formas: %s. Transformações: %s.' % (', '.join('%s=%d' % kv for kv in r['por_forma'].items()),
                                                  ', '.join('%s=%d' % kv for kv in r['por_transformacao'].items())))
    if 'tstreamdati_no_log' in r:
        L.append('- `TStreamDati`: %d objetos; %d aparecem na captura (%d só com NAK).' % (
            r['tstreamdati_total'], r['tstreamdati_no_log'], r['tstreamdati_so_nak']))
    L.append('')
    L.append('Colunas: **SC** = SerialCode (hex, little-endian no fio v2); **tipo** = u/s + bits do elemento (OBS-COD, '
             '`DataLength` omitido = 1, confirmado no fio); **dim** = elementos (vetor) ou LxC (matriz); **conversão** = '
             '`fisico = (a·raw+b)/(c·raw+d)` com `Coeffs=(a,b,c,d)` quando `ttFormula`; **padrão** = `DefaultValue` do '
             'DFM; **log** = leituras ACK/NAK e primeira linha na captura. Unidade `—` = DESCONHECIDO.')
    L.append('')
    for modulo in sorted({i['modulo'] for i in cat['itens']}, key=lambda m: (m != 'TSTREAMDATI', m)):
        L.append('## %s' % modulo)
        L.append('')
        L.append('| SC | nome | forma | tipo | dim | conversão | un. | padrão | leitura v2 | log | fam. |')
        L.append('|---|---|---|---|---|---|---|---|---|---|---|')
        for i in cat['itens']:
            if i['modulo'] != modulo:
                continue
            d = i['dimensoes']
            dim = ('%sx%s' % (d['linhas'], d['colunas'])) if 'linhas' in d else str(d['n'])
            cv = i['conversao'].get('resumo') or i['conversao']['tipo']
            pf = i['padrao_fabrica']
            if pf is None:
                pf = '—'
            elif isinstance(pf, list):
                pf = str(pf[:6]) + ('…' if len(pf) > 6 else '')
            else:
                pf = str(pf)
            lg = i['log']
            lgt = '—' if lg is None else ('não' if not lg['aparece'] else 'ACK %d/NAK %d @%d' % (
                lg['leituras_ack'], lg['leituras_nak'], lg['primeira_linha']))
            L.append('| %s | `%s` | %s | %s | %s | %s | %s | %s | `%s` | %s | %s |' % (
                i['serial_hex'] or '—', i['nome'], i['forma'], i['tipo_dado'], dim, cv, i['unidade'] or '—', pf,
                i['fio_v2']['leitura']['quadro'], lgt, i['familia_ecu']))
        L.append('')
    return '\n'.join(L) + '\n'


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument('--exe', required=True)
    ap.add_argument('--log')
    ap.add_argument('--json')
    ap.add_argument('--md')
    a = ap.parse_args(argv)
    cat = catalogo(a.exe, a.log)
    if a.json:
        with open(a.json, 'w', encoding='utf-8') as fh:
            json.dump(cat, fh, ensure_ascii=False, indent=1)
            fh.write('\n')
    if a.md:
        with open(a.md, 'w', encoding='utf-8') as fh:
            fh.write(para_markdown(cat))
    print('itens:', cat['resumo']['total'], cat['resumo'].get('tstreamdati_no_log'))
    return 0


if __name__ == '__main__':
    sys.exit(main())
