#!/usr/bin/env python3
"""Gera docs/progbase/decomp/COBERTURA.md, funcoes-app.md e backlog.json a partir de funcoes.json.gz (+ fragmentos de pseudo-C).
Determinístico. Marcas: OBS-COD (contagens do Ghidra/RTTI), INFERIDO (unidade por intervalo; lacunas aproximadas), DESCONHECIDO (backlog)."""
import gzip, json, os
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
DEC = os.path.join(ROOT, 'docs', 'progbase', 'decomp')
TEXT = 0x650000


def pct(a, b):
    return f'{100 * a / b:.1f}%' if b else '-'


def main():
    F = json.load(gzip.open(os.path.join(DEC, 'funcoes.json.gz')))['funcoes']
    por = {f['a']: f for f in F}
    fail = []
    for fr in sorted(os.listdir(os.path.join(DEC, 'pseudocodigo'))):
        for l in gzip.open(os.path.join(DEC, 'pseudocodigo', fr), 'rt', encoding='utf-8'):
            r = json.loads(l)
            if not r['ok']:
                fail.append(r['a'])
    tot = sum(f['tam'] for f in F)
    nom = [f for f in F if f['nome']]
    dec = [f for f in F if f.get('frag')]
    uni = [f for f in F if f['unidade']]
    inf = [f for f in F if f['un_fonte'] == 'INFERIDO']
    sem = [f for f in F if not f['nome'] and not f['unidade']]
    gig = [f for f in F if f['tam'] > 60000]
    app = [f for f in F if f['unidade'] and f['unidade'].startswith('@')]
    L = ['# Cobertura da decompilação do ProgBase.exe', '',
         'Gerado por `tools/progbase/decomp_cobertura.py` (determinístico) a partir de `funcoes.json.gz`. Ferramentas: Ghidra 12.1.4 headless (análise + decompilador), RTTI Delphi lido por `decomp_rtti.py`. Regenerar tudo: `tools/progbase/decomp_tudo.sh`.', '',
         '## Resumo (`.text` = 6.619.136 bytes virtuais)', '',
         '| Métrica | Funções | Bytes | % de .text | Marca |', '|---|---|---|---|---|',
         f'| Em função reconhecida pelo Ghidra | {len(F)} | {tot} | {pct(tot, TEXT)} | OBS-COD |',
         f'| Função com NOME (RTTI publicados/propriedades/dinâmicos/virtuais + símbolos de import) | {len(nom)} | {sum(f["tam"] for f in nom)} | {pct(sum(f["tam"] for f in nom), TEXT)} | OBS-COD |',
         f'| Com UNIDADE (âncora RTTI ou intervalo contíguo) | {len(uni)} | {sum(f["tam"] for f in uni)} | {pct(sum(f["tam"] for f in uni), TEXT)} | OBS-COD + INFERIDO ({len(inf)} por intervalo) |',
         f'| Com pseudo-C (decompilador) | {len(dec)} | {sum(f["tam"] for f in dec)} | {pct(sum(f["tam"] for f in dec), TEXT)} | OBS-COD |',
         f'| Funções das units do PROGRAMA (prefixo `@` no RTTI) | {len(app)} | {sum(f["tam"] for f in app)} | {pct(sum(f["tam"] for f in app), TEXT)} | OBS-COD/INFERIDO |',
         f'| Sem nome e sem unidade | {len(sem)} | {sum(f["tam"] for f in sem)} | {pct(sum(f["tam"] for f in sem), TEXT)} | DESCONHECIDO |',
         f'| Bytes de .text fora de qualquer função (dados Delphi, RTTI, tabelas, padding ou código não alcançado) | - | {TEXT - tot} | {pct(TEXT - tot, TEXT)} | DESCONHECIDO |', '',
         'Leitura honesta: "analisado" aqui = o Ghidra reconhece a função E ela tem pseudo-C. Nome vem só do RTTI; o resto (RTL/VCL/TeeChart sem RTTI) fica sem nome e entra no backlog. Pseudo-C é bruto do Ghidra (pode conter erro; marca OBS-COD só para o que está no binário, a leitura do pseudo-C é INFERIDO).', '',
         '## Por unit (top 40 por bytes)', '', '| Unit | Funções | Bytes | Com pseudo-C |', '|---|---|---|---|']
    pu = {}
    for f in uni:
        d = pu.setdefault(f['unidade'], [0, 0, 0])
        d[0] += 1; d[1] += f['tam']; d[2] += 1 if f.get('frag') else 0
    for u, d in sorted(pu.items(), key=lambda x: (-x[1][1], x[0]))[:40]:
        L.append(f'| {u} | {d[0]} | {d[1]} | {d[2]} |')
    L += ['', '## Backlog explícito (o que FALTA)', '',
          f'1. **{len(sem)} funções sem nome nem unit** ({pct(sum(f["tam"] for f in sem), TEXT)} de .text) — RTL/VCL/TeeChart/Indy ligados estaticamente e funções privadas sem RTTI. Maiores 40 abaixo; lista completa em `backlog.json` (campo `sem_nome`). Caminho: assinaturas FLIRT/BSim de Delphi 10.x ou nomear por XREF de strings (`pistas` em `funcoes.json.gz`).',
          f'2. **{len(gig)} função(ões) gigante(s)** (>60 KB; o Ghidra pode ter fundido regiões): {", ".join(f["a"] + " (" + str(f["tam"]) + " B)" for f in gig)}. Pseudo-C só se coube no tempo; revisar manualmente (SendCommand 0x04 é chamado de 0x0043C761 dentro dela, ver `docs/progbase/protocolo/comandos.md`).',
          f'3. **{len(fail)} funções cujo pseudo-C falhou/expirou**: {", ".join(fail[:30])}{" ..." if len(fail) > 30 else ""}.',
          f'4. **{TEXT - tot} bytes de .text fora de função** — não classificados (provável mistura de VMT/typeinfo/tabelas de salto/padding e código só alcançado por ponteiro). Maiores lacunas aproximadas abaixo (INFERIDO: endereço+tamanho do corpo).',
          '5. **Resources.dll (3,2 MB) não decompilada** — só recursos; ver `docs/progbase/inventario`. **.rsrc/.reloc/.data** não entram em "bytes de .text".',
          '6. Nomes de **locais/parâmetros/tipos** não foram recuperados (o Ghidra usa param_1...). Estruturas (TStreamDati etc.) só por offsets em `docs/progbase/protocolo`.',
          '7. Fluxos por **ponteiro de método/evento em runtime** (Delphi) que o Ghidra não vê como chamada: cobertos só quando o DFM liga o evento (1376 eventos, `rtti.json`).', '',
          '### 40 maiores funções sem nome', '', '| Endereço | Bytes | Chamadores | Estado |', '|---|---|---|---|']
    for f in sorted(sem, key=lambda f: (-f['tam'], f['a']))[:40]:
        L.append(f'| {f["a"]} | {f["tam"]} | {len(f["cr"])} | {"pseudo-C" if f.get("frag") else "sem pseudo-C"} |')
    gaps = []
    ordf = sorted(F, key=lambda f: int(f['a'], 16))
    for a, b in zip(ordf, ordf[1:]):
        g = int(b['a'], 16) - (int(a['a'], 16) + a['tam'])
        if g > 0:
            gaps.append((g, int(a['a'], 16) + a['tam']))
    L += ['', '### 40 maiores lacunas entre funções', '', '| Início (aprox.) | Bytes |', '|---|---|']
    for g, a in sorted(gaps, reverse=True)[:40]:
        L.append(f'| 0x{a:08X} | {g} |')
    open(os.path.join(DEC, 'COBERTURA.md'), 'w', encoding='utf-8').write('\n'.join(L) + '\n')
    # funcoes-app.md: nome, propósito, chamadores
    M = ['# Funções das units do programa (prefixo @ no RTTI)', '',
         'Gerado por `decomp_cobertura.py`. Colunas: endereço · nome · bytes · propósito (derivado do nome RTTI; INFERIDO se vier de strings) · chamadores (até 6) · pseudo-C.', '',
         '| Endereço | Nome | Bytes | Propósito | Chamadores | Pseudo-C |', '|---|---|---|---|---|---|']
    def nm(a):
        x = por.get(a)
        return (x['nome'] or a) if x else a
    for f in sorted(app, key=lambda f: f['a']):
        M.append(f'| {f["a"]} | {f["nome"] or "(sem nome)"} | {f["tam"]} | {f.get("proposito") or ""} | {", ".join(nm(c) for c in f["cr"][:6])} | {f.get("frag") or ""} |')
    open(os.path.join(DEC, 'funcoes-app.md'), 'w', encoding='utf-8').write('\n'.join(M) + '\n')
    bk = {'marca': 'DESCONHECIDO', 'sem_nome': [{'a': f['a'], 'tam': f['tam'], 'cr': len(f['cr'])} for f in sorted(sem, key=lambda f: (-f['tam'], f['a']))],
          'gigantes': [f['a'] for f in gig], 'pseudoc_falhou': fail, 'lacunas_top200': [{'a': '0x%08X' % a, 'bytes': g} for g, a in sorted(gaps, reverse=True)[:200]]}
    with gzip.GzipFile(os.path.join(DEC, 'backlog.json.gz'), 'wb', mtime=0) as g:
        g.write(json.dumps(bk, separators=(',', ':')).encode())
    print('ok', len(F), len(dec), len(fail))


if __name__ == '__main__':
    main()
