#!/usr/bin/env python3
"""Separa as funções do PROGRAMA (código do fabricante: forms Landi + componentes AEB) das de BIBLIOTECA
(RTL/VCL Embarcadero, TeeChart, Indy, CRT do C++Builder, imports do Windows) nas funções sem nome e sem unit.

Entrada: docs/progbase/decomp/funcoes.json.gz (Ghidra + RTTI, já versionado). Não precisa do .exe.
Saída:   docs/progbase/decomp/classificacao-funcoes.json (+ .md curto). Determinístico/idempotente.

Critério (em ordem; cada linha leva marca):
 1. Âncoras = funções com categoria própria:
    - unit do RTTI (OBS-COD) ou unit por intervalo (INFERIDO, de decomp_funcoes.py);
    - nome do Ghidra: mangling Borland '@Ns@Unit@...' / '@@Unit@Initialize' / '@TClasse@...' (OBS-COD) e
      imports/CRT sem '@' (OBS-COD: símbolo do import/biblioteca).
    Unit com namespace Embarcadero/terceiros => biblioteca; unit sem namespace ('@:X', '@ns:X') => programa.
    System.Generics.* NÃO serve de âncora de vizinhança: instâncias genéricas são emitidas dentro da unit
    que as usa (espalhadas por todo o .text) — DERIVADO das faixas de endereço.
 2. Alvo (sem nome e sem unit): vizinhos (âncora anterior e seguinte, na ordem de ligação). Ambos na mesma
    categoria => essa categoria (INFERIDO; o linker põe cada .obj/unit contíguo). Confiança 'alta' se as duas
    âncoras são da MESMA unit, 'media' se só da mesma categoria.
 3. Vizinhos discordam => voto dos chamadores (cr) com categoria conhecida: unânime => categoria (INFERIDO,
    confiança 'baixa'). Senão => 'desconhecida' (DESCONHECIDO).
    Endereço fora de .text (seção não executável) => 'desconhecida' (OBS-COD da seção; provável dado).
Taxa de confiança: validação deixa-um-de-fora do passo 2 sobre âncoras OBS-COD (DERIVADO).
Uso: classificar_funcoes.py [funcoes.json.gz] [pasta_saida]
"""
import gzip, json, os, re, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
DEC = os.path.join(ROOT, 'docs', 'progbase', 'decomp')
TEXT_LO, TEXT_HI = 0x401000, 0x401000 + 0x650000   # .text (única seção executável; OBS-COD via pefile)

LIB_NS = ('System', 'Sysinit', 'Vcl', 'Vcltee', 'Xml', 'Data', 'Winapi', 'Soap', 'Web', 'Datasnap', 'Rest',
          'Fmx', 'Ole', 'Idglobal', 'Pngimage', 'Jpeg', 'Zlib', 'Midas', 'Dbclient', 'Bde', 'Teeprocs')
LIB_UNIT_RE = re.compile(r'^(System|Vcl|VCLTee|Xml|Data|Winapi|Soap|Web|Datasnap|REST|FMX|Id[A-Z])')


def cat_unidade(u):
    """'programa' | 'biblioteca' para um nome de unit do RTTI."""
    if u.startswith('@'):
        return 'programa'          # unit sem namespace Embarcadero (RTTI '@:X' / '@ns:X')
    return 'biblioteca' if LIB_UNIT_RE.match(u) else 'programa'


def eh_generico(u):
    return u.startswith('System.Generics.')


def cat_nome(nome, classes):
    """Categoria por nome de símbolo do Ghidra -> (categoria, chave_unit) ou (None, None)."""
    if not nome:
        return None, None
    if not nome.startswith('@'):
        if nome.startswith(('FUN_', 'thunk_FUN_', 'entry')):
            return None, None
        return 'biblioteca', 'imports/crt'          # import Win32 / CRT (__allmul, Ordinal_n, DelayLoad_...)
    partes = [p for p in nome.split('$')[0].split('@') if p]
    if not partes:
        return None, None
    p0 = partes[0]
    if p0.capitalize() in LIB_NS or p0 in LIB_NS or re.match(r'^Id[A-Z]', p0):
        return 'biblioteca', p0
    if p0.startswith('T') and p0 in classes:
        u = classes[p0]
        return (cat_unidade(u), u) if u else ('programa', p0)
    # '@@Unit@Initialize', '@Unit@func', '@TClasseCpp@...' sem namespace => código do projeto (C++Builder)
    return 'programa', p0


def ancoras(F, classes):
    out = []
    for i, f in enumerate(F):
        u = f.get('unidade')
        if u and not eh_generico(u):
            out.append((i, cat_unidade(u), u, f.get('un_fonte') or 'INFERIDO'))
            continue
        if u:          # genérico: categoria conhecida (biblioteca) mas não é âncora de vizinhança
            continue
        c, k = cat_nome(f.get('nome'), classes)
        if c:
            out.append((i, c, k, 'OBS-COD'))
    return out


def classificar(F, classes):
    """F ordenada por endereço. Devolve (linhas_alvo, resumo)."""
    anc = ancoras(F, classes)
    cat_de = {}
    for i, f in enumerate(F):
        u = f.get('unidade')
        if u:
            cat_de[f['a']] = cat_unidade(u)
    for i, c, k, _ in anc:
        cat_de[F[i]['a']] = c
    idx = [a[0] for a in anc]
    import bisect
    linhas = []
    for i, f in enumerate(F):
        if f.get('nome') is not None or f.get('unidade') is not None:
            continue
        j = bisect.bisect_left(idx, i)
        ant = anc[j - 1] if j > 0 else None
        seg = anc[j] if j < len(anc) else None
        cat, conf, marca, crit, uprov = 'desconhecida', None, 'DESCONHECIDO', 'sem_consenso', None
        if not TEXT_LO <= int(f['a'], 16) < TEXT_HI:
            linhas.append([f['a'], cat, conf, marca, 'fora_do_text_provavel_dado', uprov])
            continue
        if ant and seg and ant[1] == seg[1]:
            cat, marca = ant[1], 'INFERIDO'
            if ant[2] == seg[2]:
                conf, crit, uprov = 'alta', 'vizinhos_mesma_unit', ant[2]
            else:
                conf, crit = 'media', 'vizinhos_mesma_categoria'
        if cat == 'desconhecida':
            votos = sorted({cat_de[c] for c in f.get('cr', []) if c in cat_de})
            if len(votos) == 1:
                cat, conf, marca, crit = votos[0], 'baixa', 'INFERIDO', 'chamadores_unanimes'
        linhas.append([f['a'], cat, conf, marca, crit, uprov])
    return linhas, anc


def validar(F, classes, anc):
    """Deixa-um-de-fora sobre âncoras OBS-COD: o passo 2 acertaria a categoria delas?"""
    ok = tot = 0
    for n in range(1, len(anc) - 1):
        i, c, k, fonte = anc[n]
        if fonte != 'OBS-COD':
            continue
        a, s = anc[n - 1], anc[n + 1]
        if a[1] == s[1]:
            tot += 1
            ok += a[1] == c
    return ok, tot


def main(argv):
    src = argv[1] if len(argv) > 1 else os.path.join(DEC, 'funcoes.json.gz')
    out = argv[2] if len(argv) > 2 else DEC
    F = json.load(gzip.open(src))['funcoes']
    F.sort(key=lambda f: int(f['a'], 16))
    rt = os.path.join(os.path.dirname(src), 'rtti.json')
    classes = {}
    if os.path.exists(rt):
        classes = {c['nome']: c['unidade'] for c in json.load(open(rt, encoding='utf-8'))['classes']}
    linhas, anc = classificar(F, classes)
    ok, tot = validar(F, classes, anc)
    cont = {}
    for l in linhas:
        cont[l[1]] = cont.get(l[1], 0) + 1
    porconf = {}
    for l in linhas:
        k = '%s/%s' % (l[1], l[2])
        porconf[k] = porconf.get(k, 0) + 1
    # visão do programa inteiro: funções com unit/nome + alvo classificado
    tot_cat = {'programa': 0, 'biblioteca': 0, 'desconhecida': 0}
    alvo = {l[0]: l[1] for l in linhas}
    for f in F:
        if f['a'] in alvo:
            tot_cat[alvo[f['a']]] += 1
        elif f.get('unidade'):
            tot_cat[cat_unidade(f['unidade'])] += 1
        else:
            c, _ = cat_nome(f.get('nome'), classes)
            tot_cat[c or 'desconhecida'] += 1
    taxa = round(100.0 * ok / tot, 2) if tot else None
    doc = {
        'marca': 'INFERIDO (categoria por vizinhança de ligação/chamadores); DERIVADO (contagens e taxa)',
        'fonte': 'docs/progbase/decomp/funcoes.json.gz + rtti.json; script tools/progbase/classificar_funcoes.py',
        'criterio': __doc__.split('Critério')[1].split('Uso:')[0].strip(),
        'alvo_n': len(linhas),
        'alvo_por_categoria': dict(sorted(cont.items())),
        'alvo_por_categoria_confianca': dict(sorted(porconf.items())),
        'todas_funcoes_por_categoria': tot_cat,
        'validacao_deixa_um_de_fora': {'acertos': ok, 'casos': tot, 'taxa_pct': taxa, 'marca': 'DERIVADO'},
        'colunas': ['endereco', 'categoria', 'confianca', 'marca', 'criterio', 'unit_provavel'],
        'linhas': linhas,
    }
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, 'classificacao-funcoes.json'), 'w', encoding='utf-8') as fh:
        fh.write(json.dumps(doc, ensure_ascii=False, sort_keys=False, separators=(',', ':')).replace('],[', '],\n['))
        fh.write('\n')
    md = ['# Classificação das funções sem nome do ProgBase.exe (programa × biblioteca)', '',
          'Gerado por `tools/progbase/classificar_funcoes.py` (idempotente) a partir de `funcoes.json.gz` + `rtti.json`.', '',
          '## Resultado (DERIVADO)', '',
          '- Alvo: **%d** funções sem nome e sem unit.' % len(linhas)]
    for k, v in sorted(cont.items()):
        md.append('- %s: **%d**' % (k, v))
    md += ['', 'Por confiança: ' + ', '.join('%s=%d' % kv for kv in sorted(porconf.items())), '',
           'Todas as %d funções (com nome/unit + alvo): programa=%d, biblioteca=%d, desconhecida=%d.' % (
               len(F), tot_cat['programa'], tot_cat['biblioteca'], tot_cat['desconhecida']), '',
           '## Critério', '',
           '1. Âncoras: unit do RTTI (OBS-COD) ou por intervalo (INFERIDO); nome Ghidra com mangling Borland '
           '(`@Ns@Unit@…`, `@@Unit@Initialize`, `@TClasse@…`) ou import/CRT (OBS-COD). Namespace Embarcadero/'
           'terceiros (System, Vcl, VCLTee, Xml, Id…) = biblioteca; unit sem namespace (forms Landi, componentes Aeb*) = programa.',
           '2. `System.Generics.*` não é âncora: instâncias genéricas ficam dentro da unit que as usa (DERIVADO das faixas).',
           '3. Alvo herda a categoria das âncoras vizinhas quando concordam (INFERIDO; alta = mesma unit, media = mesma categoria).',
           '4. Vizinhos discordam: chamadores unânimes (INFERIDO, baixa). Senão DESCONHECIDO.', '',
           '## Taxa de confiança (DERIVADO)', '',
           'Deixa-um-de-fora do passo 3 sobre %d âncoras OBS-COD: %d acertos = **%s%%**. '
           'Mede só a regra de vizinhança; a regra de chamadores (baixa) não tem validação.' % (tot, ok, taxa), '',
           'Limites: "programa" inclui componentes AEB (fornecedor do ProgBase), não só os forms Landi (INFERIDO). '
           'Funções na fronteira entre .obj de categorias diferentes ficam desconhecidas por construção.', '']
    with open(os.path.join(out, 'classificacao-funcoes.md'), 'w', encoding='utf-8') as fh:
        fh.write('\n'.join(md))
    print(json.dumps({k: doc[k] for k in ('alvo_n', 'alvo_por_categoria', 'alvo_por_categoria_confianca',
                                          'todas_funcoes_por_categoria', 'validacao_deixa_um_de_fora')}, ensure_ascii=False))


if __name__ == '__main__':
    main(sys.argv)
