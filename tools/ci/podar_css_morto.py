"""Poda CSS morto: remove seletor cujo .classe ou #id não aparece em NENHUM HTML/JS da interface (nem como parte de texto, para
não quebrar nomes montados em JS). Atributos [data-*] e pseudo-classes nunca são julgados (dataset.camelCase não aparece
literalmente). Uso: python3 tools/ci/podar_css_morto.py [--aplicar]  (sem --aplicar só relata)"""
import glob, os, re, sys
UI = os.path.join(os.path.dirname(__file__), '..', '..', 'app', 'src', 'main', 'assets', 'ui')
sources = ''
for pattern in ('**/*.js', '*.html'):
    for f in glob.glob(os.path.join(UI, pattern), recursive=True):
        sources += open(f, encoding='utf-8').read() + '\n'
TOKEN = re.compile(r'(?<![\w-])[.#]([A-Za-z_][\w-]*)')

def dead(selector):
    s = re.sub(r'\[[^\]]*\]', '', selector)           # tira atributos
    s = re.sub(r'::?[\w-]+(\([^)]*\))?', '', s)       # tira pseudo
    return [t for t in TOKEN.findall(s) if t not in sources]

def split_top(css):
    out, i, n = [], 0, len(css)
    while i < n:
        if css.startswith('/*', i):
            j = css.index('*/', i) + 2; out.append(('c', css[i:j])); i = j; continue
        if css[i].isspace():
            j = i
            while j < n and css[j].isspace(): j += 1
            out.append(('w', css[i:j])); i = j; continue
        j = css.index('{', i); head = css[i:j]; depth = 1; k = j + 1
        if head.strip().startswith('@') and ';' in head and '{' not in head: pass
        while depth:
            if css[k] == '{': depth += 1
            elif css[k] == '}': depth -= 1
            k += 1
        out.append(('r', head, css[j + 1:k - 1])); i = k
    return out

def filt(css, report):
    res, removed = [], 0
    for t in split_top(css):
        if t[0] != 'r': res.append(t[1]); continue
        head, body = t[1], t[2]
        if head.strip().startswith('@'):
            if head.strip().startswith(('@media', '@supports')):
                inner, r = filt(body, report); removed += r
                if inner.strip(): res.append(head + '{' + inner + '}')
                else: removed += 1
            else: res.append(head + '{' + body + '}')
            continue
        sels = [x.strip() for x in head.split(',')]
        keep = [x for x in sels if not dead(x)]
        for x in sels:
            if dead(x): report.append(x[:90])
        if len(keep) == len(sels): res.append(head + '{' + body + '}')
        elif keep: res.append(','.join(keep) + '{' + body + '}'); removed += len(sels) - len(keep)
        else: removed += 1
    return ''.join(res), removed

apply = '--aplicar' in sys.argv
total = 0
for f in sorted(glob.glob(os.path.join(UI, '*.css'))):
    s = open(f, encoding='utf-8').read(); report = []
    new, r = filt(s, report)
    total += r
    print(f"{os.path.basename(f)}: {r} seletores/regras mortos, {len(s)} -> {len(new)} bytes")
    if apply: open(f, 'w', encoding='utf-8').write(new)
print('total', total, '(aplicado)' if apply else '(só relatório)')
