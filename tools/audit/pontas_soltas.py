#!/usr/bin/env python3
"""Auditoria de pontas soltas (dono, 2026-10-13: "nenhuma ponta pode ficar solta").
1) JS chama uma função da ponte Android que NÃO existe (quebra em silêncio no aparelho).
2) Função da ponte Android que NENHUM JS chama (superfície morta).
3) Arquivo JS que nenhuma página/roteador carrega (código morto).
4) getElementById('x') no JS sem id="x" no HTML nem criado por JS (tela que nunca acha o elemento).
Saída: lista; código 1 se houver item do tipo 1 ou 3 (os que quebram ou sobram de verdade)."""
import re, sys, pathlib
ROOT = pathlib.Path(__file__).resolve().parents[2]
UI = ROOT / 'app/src/main/assets/ui'
KT = ROOT / 'app/src/main/java'

js_files = sorted(p for p in UI.rglob('*.js'))
js_text = {p: p.read_text('utf-8') for p in js_files}
html = (UI / 'index.html').read_text('utf-8')
all_js = '\n'.join(js_text.values())

# ---- 1/2: ponte
bridge_funs = {}
for p in KT.rglob('*.kt'):
    t = p.read_text('utf-8')
    for m in re.finditer(r'@JavascriptInterface\s*(?:\n\s*)?fun\s+(\w+)', t):
        bridge_funs.setdefault(m.group(1), p.name)
called = set()
for m in re.finditer(r"invoke\(\s*[\w.]+\s*,\s*'(\w+)'", all_js): called.add(m.group(1))
for m in re.finditer(r"(?:bridge|window\.Omegas\w*|this\.bridge|native)\??\.(\w+)\(", all_js): called.add(m.group(1))
for name in list(bridge_funs):
    if re.search(r'\b' + re.escape(name) + r'\b', all_js): called.add(name)
missing = sorted(n for n in set(re.findall(r"invoke\(\s*[\w.]+\s*,\s*'(\w+)'", all_js)) if n not in bridge_funs)
unused = sorted(n for n in bridge_funs if not re.search(r'\b' + re.escape(n) + r'\b', all_js))

# ---- 3: arquivos JS não carregados
loaded_text = html + '\n' + all_js
orphans = []
for p in js_files:
    rel = p.relative_to(UI).as_posix()
    if rel == 'index.html': continue
    if not re.search(re.escape(rel), loaded_text.replace('\\', '/')):
        orphans.append(rel)

# ---- 4: ids
html_ids = set(re.findall(r'id="([^"]+)"', html))
created = set(re.findall(r'id="([\w-]+)"', all_js)) | set(re.findall(r"\.id\s*=\s*'([\w-]+)'", all_js))
needed = set(re.findall(r"getElementById\('([\w-]+)'\)", all_js))
dangling = sorted(n for n in needed if n not in html_ids and n not in created)

invoked = set(re.findall(r"invoke\(\s*[\w.]+\s*,\s*'(\w+)'", all_js))
print(f"Ponte Android: {len(bridge_funs)} funções expostas; JS chama {len(invoked)} nomes via invoke().")
print(f"\n[1] JS chama função INEXISTENTE na ponte ({len(missing)}):"); [print('   ', n) for n in missing]
print(f"\n[2] Função da ponte que NENHUM JS usa ({len(unused)}):"); [print('   ', f'{n}  ({bridge_funs[n]})') for n in unused]
print(f"\n[3] Arquivo JS que ninguém carrega ({len(orphans)}):"); [print('   ', n) for n in orphans]
print(f"\n[4] getElementById sem elemento ({len(dangling)}):"); [print('   ', n) for n in dangling]
sys.exit(1 if (missing or orphans) else 0)
