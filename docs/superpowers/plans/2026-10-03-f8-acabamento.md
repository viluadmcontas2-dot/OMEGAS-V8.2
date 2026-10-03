# Fatia 8 — Acabamento Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fechar a estrutura do Norte Único: um `tokens.css` com cor semântica e contraste provado, CSS consolidado (um por tela), orçamento de desempenho medido no emulador, balão com índice e mini AGORA, protocolo físico das 7 abas e o contrato único de "fechado" (spec §8 itens 1–6), terminando com o APK final.

**Architecture:** Um codemod determinístico (`tools/ui/css_tokens.py`) classifica cada cor literal e cada tamanho em um token; os testes de contrato usam o mesmo módulo, então a regra que migra é a regra que vigia. A cor de estado sai de um só lugar no JS (`core/state-colors.js`) e no Kotlin (`OverlayTokens`), ambos conferidos contra `tokens.css`. O desempenho é medido por marcas (`core/perf.js`) lidas por uma classe nova de androidTest dentro do workflow de render existente.

**Tech Stack:** CSS custom properties (Chromium WebView), JS vanilla (`node --test`), Python 3 (contratos e codemod), Kotlin/JUnit (JVM e androidTest), GitHub Actions, `gh`.

**Spec:** docs/superpowers/specs/2026-10-03-omegas-platina-norte-unico-design.md  ·  **Índice/contrato:** docs/superpowers/plans/2026-10-03-00-norte-unico-index.md


> **Antes de começar:** leia a seção **Reconciliação entre planos** do índice (`2026-10-03-00-norte-unico-index.md`). Onde este plano divergir dela (intents, payloads, formato do snapshot, comando do emulador), vale o índice.
## Global Constraints

Todas as do índice, mais:

- Branch `work/platina-f8-acabamento` a partir de `OmegasPlatina` com F1–F7 mesclados. Todo commit termina com as duas linhas abaixo (não repetidas nas tarefas):
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
  `Claude-Session: https://claude.ai/code/session_01YN94YqPpeRPSaeNkh2QkF7`
- Linhas citadas em "Modify:" são da `OmegasPlatina` de 2026-10-03 (antes de F1–F7). F6/F7 reescrevem telas: o primeiro passo de cada tarefa reconfirma com o `grep` indicado e trabalha sobre o que ele devolver.
- Lista final de CSS: `tokens.css`, `styles.css` (base: casca, trilho, faixa de status, split, `subpage-tabs`, `operation-overlay`, `<details>`) e `styles-<route>.css` só para rotas de `ROUTES` que têm regra própria. Nada de `<link>` criado por JS.
- Fora de `tokens.css` nenhum CSS/JS tem hex, `rgb()`, `rgba()`, `hsl()`, `hsla()`, `white` ou `black`; a única exceção é `<meta name="theme-color" content="#080c12">` em `index.html` (meta não aceita `var()`).
- Azul (gasolina/info) deixa de ser cor: o codemod o mapeia para neutros. Gasolina se distingue por traço (Referência tracejada, Própria sólida), não por cor (spec §3.2 "cor é estado").
- `--accent` só em `.primary-action`, `.now-marker` e `:focus-visible`. `--accent` sobre `--bg` dá 4,40:1: nunca vira cor de texto.
- Fonte só cresce na migração (arredondamento para cima); padding/margin ficam em px locais (geometria da tela), só `font-size`, `border-radius` e `gap` viram token.
- Testes Android não passam pelo `remote-test.sh` (não há kind android). "Run render" neste plano significa:
  `gh workflow run verde-android-render-evidence.yml --ref work/platina-f8-acabamento && sleep 15 && gh run watch "$(gh run list --workflow verde-android-render-evidence.yml --branch work/platina-f8-acabamento -L1 --json databaseId -q '.[0].databaseId')" --exit-status`
  e lê-se `SCENARIO_RESULT=<cenário>:PASS|FAIL` no log (`gh run view <id> --log-failed` na falha).

## Review Focus

1. **Fonte de 9–11 px vira 12 px na matriz do AutoCal (18 bandas × 2)**: nenhuma rota/subpágina transborda em 1280×720 (`scrollWidth ≤ clientWidth`). Teste: `AcabamentoRenderTest.rotasSemTransbordo` (Task 8.6).
2. **`rgba(91,184,255,calc(.12 + var(--trace-fade,0)*…))` (alfa dinâmico)**: o codemod não chuta; devolve a linha na lista `MANUAL` e sai com código 1. Teste: `test_css_token_classifier.py` caso `dynamic_alpha` (Task 8.2).
3. **Ponto `POBRE` com `mixture = 0.08` exato** → `--warn`; `0.0801` → `--danger`; `mixture = null` → `--warn`; `SEM_DADOS` → oco. Teste: `tests/ui/state-colors.test.cjs` (Task 8.7).
4. **Balão com `index.value = null` e telemetria velha**: mostra `—%` (nunca `0%`), esconde o ▲ e o chip diz `SEM DADO`. Teste: `OverlayBalloonModelTest` (Task 8.11).
5. **Sessão longa trocando de aba**: 12 min de telemetria a 80 ms com troca de rota a cada 30 s não fazem crescer nós do DOM (> 5 %) nem heap JS (> 25 %), e o p95 de pintura por revisão fica < 100 ms no último minuto. Teste: `AcabamentoRenderTest.soakSemVazamento` (Task 8.10).

---

### Task 8.1: `tokens.css` com os valores da spec + contraste WCAG

**Files:**
- Create: `app/src/main/assets/ui/tokens.css`
- Modify: `app/src/main/assets/ui/index.html:6-9` (`theme-color` → `#080c12`; `<link href="tokens.css">` como primeiro stylesheet)
- Test: `tests/test_token_contrast.py`

**Interfaces:**
- Produces (CSS custom properties, únicas definições de cor do app):
  - Canais: `--bg-rgb: 8 12 18` · `--surface-rgb: 16 23 33` · `--surface-2-rgb: 22 31 44` · `--stroke-rgb: 41 55 73` · `--text-rgb: 245 248 252` · `--text-2-rgb: 150 166 187` · `--accent-rgb: 116 92 255` · `--ok-rgb: 56 211 159` · `--warn-rgb: 247 185 85` · `--danger-rgb: 255 107 107`; cada `--X: rgb(var(--X-rgb))`.
  - Derivados: `--{ok,warn,danger,accent}-soft: rgb(var(--X-rgb) / .14)`, `--{ok,warn,danger,accent}-line: rgb(var(--X-rgb) / .45)`, `--veil-1/2/3: rgb(var(--text-rgb) / .03|.06|.12)`, `--scrim: rgb(var(--bg-rgb) / .82)`.
  - Espaço: `--sp-1 4px · --sp-2 8px · --sp-3 12px · --sp-4 16px · --sp-5 24px · --sp-6 32px` (gaps usados hoje: 2–14 px, moda 6–8).
  - Raio: `--r-sm 6px · --r-md 10px · --r-lg 16px · --r-pill 999px` (moda atual 10 px; 7–9 px e 12 px colapsam em `--r-md`).
  - Tipo: `--fs-xs 12px · --fs-sm 14px · --fs-md 16px · --fs-lg 20px · --fs-crit 24px · --fs-xl 32px · --fs-display 64px` (usados hoje: 7–25 px; `--fs-crit` = texto crítico da spec §3.2).
  - Toque: `--touch 76px`.

- [ ] **Step 1: Write the failing test** `tests/test_token_contrast.py`

```python
import pathlib, re
root = pathlib.Path(__file__).resolve().parents[1]
css = (root / "app/src/main/assets/ui/tokens.css").read_text(encoding="utf-8")
SPEC = {"bg": (8,12,18), "surface": (16,23,33), "surface-2": (22,31,44), "stroke": (41,55,73), "text": (245,248,252),
        "text-2": (150,166,187), "accent": (116,92,255), "ok": (56,211,159), "warn": (247,185,85), "danger": (255,107,107)}
got = {m[0]: tuple(map(int, m[1:])) for m in re.findall(r"--([a-z0-9-]+)-rgb:\s*(\d+)\s+(\d+)\s+(\d+)\s*;", css)}
assert got == SPEC, got
for name in SPEC: assert re.search(rf"--{name}:\s*rgb\(var\(--{name}-rgb\)\)\s*;", css), name
def lum(c):
    ch = [(v/255)/12.92 if v/255 <= 0.03928 else ((v/255 + 0.055)/1.055) ** 2.4 for v in c]
    return 0.2126*ch[0] + 0.7152*ch[1] + 0.0722*ch[2]
def ratio(a, b):
    hi, lo = sorted((lum(SPEC[a]), lum(SPEC[b])), reverse=True); return (hi + .05) / (lo + .05)
for fg in ("text", "text-2"):
    for bg in ("bg", "surface", "surface-2"):
        assert ratio(fg, bg) >= 4.5, (fg, bg, round(ratio(fg, bg), 2))
assert round(ratio("text-2", "surface-2"), 2) == 6.69           # pior caso de texto
for st in ("ok", "warn", "danger"):                              # WCAG 1.4.11: componente não-texto ≥ 3:1
    for bg in ("bg", "surface", "surface-2"): assert ratio(st, bg) >= 3.0, (st, bg)
assert ratio("accent", "bg") < 4.5                               # 4.40: accent nunca é cor de texto
assert ratio("text", "accent") >= 3.0                            # 4.18: só texto grande (≥ 24 px) sobre accent
for tok, px in {"fs-crit": 24, "touch": 76, "fs-xs": 12}.items():
    assert re.search(rf"--{tok}:\s*{px}px", css), tok
html = (root / "app/src/main/assets/ui/index.html").read_text(encoding="utf-8")
assert '<meta name="theme-color" content="#080c12">' in html
links = re.findall(r'<link rel="stylesheet" href="([^"]+)">', html)
assert links[0] == "tokens.css", links
print("TOKEN_CONTRAST=PASS")
```

- [ ] **Step 2: Run to see it fail**
Run: `tools/ci/remote-test.sh python tests/test_token_contrast.py`
Expected: `REMOTE_TEST=FAIL` com `FileNotFoundError: ... tokens.css`.

- [ ] **Step 3: Implement** — criar `tokens.css` só com `:root{…}` dos valores acima (nenhum seletor além de `:root`); em `index.html` trocar `theme-color` e inserir o `<link>` antes de `styles.css`. Ainda não remover as variáveis antigas de `styles.css:1-24` (a Task 8.5 faz isso).

- [ ] **Step 4: Run to verify**
Run: `tools/ci/remote-test.sh python tests/test_token_contrast.py`
Expected: `REMOTE_TEST=PASS`, log `TOKEN_CONTRAST=PASS`.

- [ ] **Step 5: Commit** `feat(ui): tokens.css com os valores da spec e contraste WCAG provado`

---

### Task 8.2: Classificador e codemod de tokens

**Files:**
- Create: `tools/ui/css_tokens.py`
- Test: `tests/test_css_token_classifier.py`

**Interfaces:**
- Produces (`tools/ui/css_tokens.py`, importável pelos testes via `sys.path.insert(0, "tools/ui")`):
  - `SPEC_TOKENS: dict[str, tuple[int, int, int]]` (os 10 da Task 8.1)
  - `classify(r: int, g: int, b: int, a: float = 1.0) -> str` → nome de token sem `--`
  - `snap_font(px: float) -> str` · `snap_radius(px: float) -> str` · `snap_gap(px: float) -> str`
  - `literal_colors(text: str) -> list[tuple[int, str]]` → (linha, literal) dentro de valores de declaração (`prop: valor` seguido de `;` ou `}`), ignorando comentários e seletores (`#app` não é cor)
  - `size_literals(text: str) -> list[tuple[int, str]]` → `font-size`, `border-radius`, `gap`/`row-gap`/`column-gap` com `px`
  - `rewrite(text: str, sizes: bool) -> tuple[str, list[str]]` → (texto novo, lista `MANUAL` com `linha: literal`)
  - CLI: `python3 tools/ui/css_tokens.py --rewrite [--sizes] <arquivos…>` reescreve no lugar, imprime `MANUAL <arquivo>:<linha> <literal>` e sai 1 se houver algum.

`classify` (o algoritmo que os testes não determinam sozinhos): HLS de `colorsys`; **neutro** se `s < .25` ou `l < .12` ou matiz em [180, 240) (azul): opaco → token neutro mais próximo em distância RGB entre `bg, surface, surface-2, stroke, text-2, text`; translúcido → `l ≥ .75`: `veil-1` (a ≤ .03), `veil-2` (a ≤ .07), `veil-3`; `l ≤ .2`: `scrim`; senão `stroke` (a ≥ .3) ou `veil-2`. **Família** por matiz: [90,180) `ok`, [20,90) `warn`, <20 ou ≥330 `danger`, [240,330) `accent`; sufixo `-soft` se `l < .3` ou `a < .3`, `-line` se `a < .6`, senão a base. `snap_font` = menor token ≥ px (acima de 32 → `fs-display` se ≥ 48, senão `fs-xl`); `snap_radius` e `snap_gap` = token mais próximo (empate sobe; raio ≥ 99 → `r-pill`); `0`, `50%`, `%`, `em`, `rem`, `var()` ficam como estão. Alfa não numérico (`calc`, `var`) → `MANUAL`.

- [ ] **Step 1: Write the failing test** `tests/test_css_token_classifier.py`

```python
import sys, pathlib
root = pathlib.Path(__file__).resolve().parents[1]; sys.path.insert(0, str(root / "tools/ui"))
from css_tokens import classify, snap_font, snap_radius, snap_gap, literal_colors, rewrite
CASES = {(7,10,15,1): "bg", (11,16,23,1): "bg", (16,23,32,1): "surface", (21,30,41,1): "surface-2", (27,38,51,1): "surface-2",
  (38,51,66,1): "stroke", (89,98,122,1): "stroke", (244,247,251,1): "text", (154,169,188,1): "text-2", (111,127,146,1): "text-2",
  (118,168,255,1): "text-2", (124,108,255,1): "accent", (38,34,72,1): "accent-soft", (56,211,159,1): "ok", (22,138,107,1): "ok",
  (93,211,158,.13): "ok-soft", (93,211,158,.5): "ok-line", (30,103,75,.32): "ok-soft", (243,186,89,1): "warn", (183,121,31,1): "warn",
  (104,78,26,.32): "warn-soft", (255,107,107,1): "danger", (226,94,94,.55): "danger-line", (80,26,32,.46): "danger-soft",
  (255,255,255,.025): "veil-1", (255,255,255,.05): "veil-2", (255,255,255,.12): "veil-3", (0,0,0,.55): "scrim",
  (9,19,29,.82): "scrim", (8,34,61,.94): "scrim", (35,47,58,.45): "scrim", (79,163,255,.24): "veil-2", (120,183,255,.42): "stroke"}
for rgba, want in CASES.items(): assert classify(*rgba) == want, (rgba, classify(*rgba), want)
assert [snap_font(p) for p in (7, 9, 12, 13, 15, 18, 22, 25, 40, 56)] == ["fs-xs","fs-xs","fs-xs","fs-sm","fs-md","fs-lg","fs-crit","fs-xl","fs-xl","fs-display"]
assert [snap_radius(p) for p in (2, 7, 8, 12, 14, 20, 99)] == ["r-sm","r-sm","r-md","r-md","r-lg","r-lg","r-pill"]
assert [snap_gap(p) for p in (1, 5, 6, 7, 10, 14, 20)] == ["sp-1","sp-1","sp-2","sp-2","sp-3","sp-4","sp-5"]
assert literal_colors("#app{color:#fff}\n/* #000 */a:hover{background:rgba(0,0,0,.5)}") == [(1, "#fff"), (2, "rgba(0,0,0,.5)")]
out, manual = rewrite(".a{color:#38d39f;border:1px solid rgba(255,255,255,.05);font-size:9px;gap:7px;border-radius:50%}", sizes=True)
assert out == ".a{color:var(--ok);border:1px solid var(--veil-2);font-size:var(--fs-xs);gap:var(--sp-2);border-radius:50%}", out
out, manual = rewrite(".t{fill:rgba(91,184,255,calc(.12 + var(--trace-fade,0)*.3))}", sizes=False)   # dynamic_alpha
assert manual == ["1: rgba(91,184,255,calc(.12 + var(--trace-fade,0)*.3))"] and "rgba(" in out
print("CSS_TOKEN_CLASSIFIER=PASS")
```

- [ ] **Step 2: Run to see it fail**
Run: `tools/ci/remote-test.sh python tests/test_css_token_classifier.py`
Expected: `REMOTE_TEST=FAIL` com `ModuleNotFoundError: No module named 'css_tokens'`.

- [ ] **Step 3: Implement** `tools/ui/css_tokens.py` com as assinaturas e regras acima (stdlib apenas: `re`, `colorsys`, `argparse`).

- [ ] **Step 4: Run to verify**
Run: `tools/ci/remote-test.sh python tests/test_css_token_classifier.py`
Expected: `REMOTE_TEST=PASS`, log `CSS_TOKEN_CLASSIFIER=PASS`.

- [ ] **Step 5: Commit** `feat(tools): classificador e codemod de tokens CSS`

---

### Task 8.3: CSS base consolidado e carregado só pelo `index.html`

**Files:**
- Modify: `app/src/main/assets/ui/styles.css` (recebe o conteúdo de `styles-shell-status.css` e `styles-split-layout.css`, e os estilos de `components/subpage-tabs.js`/`components/operation-overlay.js` se F6/F7 os puseram em arquivo próprio)
- Modify: `app/src/main/assets/ui/components/vehicle-status-strip.js:38-45`, `components/split-layout.js:16-23` (remover `inject()/injectStyle()` de `<link>`)
- Delete: `app/src/main/assets/ui/styles-shell-status.css`, `styles-split-layout.css`
- Modify (referências de teste): `tests/ui/split-layout.test.cjs:7` → `styles.css`
- Test: `tests/test_ui_css_manifest.py`

**Interfaces:**
- Produces: `tests/test_ui_css_manifest.py` (estendido na Task 8.4); `styles.css` como único CSS de componentes compartilhados.

- [ ] **Step 1: Write the failing test** `tests/test_ui_css_manifest.py`

```python
import pathlib, re
root = pathlib.Path(__file__).resolve().parents[1]
ui = root / "app/src/main/assets/ui"
html = (ui / "index.html").read_text(encoding="utf-8")
links = re.findall(r'<link rel="stylesheet" href="([^"]+)">', html)
assert links[:2] == ["tokens.css", "styles.css"], links
for gone in ("styles-shell-status.css", "styles-split-layout.css"):
    assert not (ui / gone).exists(), gone
js = {p: p.read_text(encoding="utf-8") for p in ui.rglob("*.js")}
for p, src in js.items():
    for name in ("styles-shell-status.css", "styles-split-layout.css"): assert name not in src, (p, name)
base = (ui / "styles.css").read_text(encoding="utf-8")
for selector in (".vehicle-status-strip", ".map-workspace"): assert selector in base, selector
print("UI_CSS_MANIFEST=PASS")
```

- [ ] **Step 2: Run to see it fail**
Run: `tools/ci/remote-test.sh python tests/test_ui_css_manifest.py`
Expected: `REMOTE_TEST=FAIL` com `AssertionError: styles-shell-status.css`.

- [ ] **Step 3: Implement** — `grep -rn "createElement('link')\|createElement(\"link\")" app/src/main/assets/ui` para inventário; colar o conteúdo dos dois CSS no fim de `styles.css` (a regra `--rail-width: 72px` de `styles-split-layout.css:2` fica sob o mesmo seletor de layout compacto); apagar os dois métodos que injetam `<link>` e suas chamadas; atualizar `tests/ui/split-layout.test.cjs:7`.

- [ ] **Step 4: Run to verify**
Run: `tools/ci/remote-test.sh python tests/test_ui_css_manifest.py` → `REMOTE_TEST=PASS`
Run: `tools/ci/remote-test.sh node tests/ui/split-layout.test.cjs` → `REMOTE_TEST=PASS`

- [ ] **Step 5: Commit** `refactor(ui): CSS base único carregado pelo index.html`

---

### Task 8.4: Um CSS por tela, lista fechada

**Files:**
- Rename (git mv): `styles-dashboard-now.css` → `styles-dashboard.css`; `styles-autocal-cockpit.css` → `styles-autocal.css`; `styles-refine.css` → `styles-refino.css`; `styles-curve-prediction.css` → conteúdo anexado a `styles-curve.css` (criado se F6 não criou)
- Modify: `app/src/main/assets/ui/index.html:8-9` (links estáticos), `app.js:10-13`, `screens/dashboard.js:31-38`, `screens/autocal-cockpit.js:653-660`, `components/curve-prediction-state.js:29-35` (remover injeção de `<link>`; se F6/F7 apagaram algum, nada a fazer nele)
- Modify (referências): `tools/ci/autocal_blueprint_layout.cjs:15`, `.github/workflows/autocal-blueprint-layout.yml:7`, e todo arquivo de `tests/` devolvido por `git grep -l -E "styles-(dashboard-now|autocal-cockpit|refine|curve-prediction)\.css" -- tests tools .github`
- Test: `tests/test_ui_css_manifest.py` (estender)

**Interfaces:**
- Consumes: `ROUTES` de `core/router.js` (índice).
- Produces: `CSS_ALLOWED = ["tokens.css", "styles.css"] + [f"styles-{r}.css" for r in ROUTES]`; `index.html` liga exatamente os existentes, nessa ordem.

- [ ] **Step 1: Write the failing test** — acrescentar a `tests/test_ui_css_manifest.py`, antes do `print`:

```python
router = (ui / "core/router.js").read_text(encoding="utf-8")
ROUTES = re.findall(r"'([a-z]+)'", re.search(r"const ROUTES = \[([^\]]+)\]", router).group(1))
assert ROUTES == ['dashboard','map','curve','autocal','refino','sessions','tools'], ROUTES
allowed = ["tokens.css", "styles.css"] + [f"styles-{r}.css" for r in ROUTES]
present = sorted(p.name for p in ui.glob("*.css")); assert not list(ui.glob("**/*/*.css")), "CSS fora da raiz de ui/"
assert set(present) <= set(allowed), sorted(set(present) - set(allowed))
assert links == [f for f in allowed if f in present], (links, present)
for p, src in js.items():
    assert not re.search(r"""createElement\(\s*['"]link['"]\s*\)""", src), p
    assert not re.search(r"""['"][\w./-]+\.css['"]""", src), p
for f in present:
    if f.startswith("styles-"): assert (ui / f).read_text(encoding="utf-8").strip(), f"{f} vazio"
for d in ("tests", "tools", ".github"):
    for p in (root / d).rglob("*"):
        if p.is_file() and p.suffix in (".py", ".cjs", ".yml", ".sh") and p.name != "test_ui_css_manifest.py":
            assert not re.search(r"styles-(dashboard-now|autocal-cockpit|refine|curve-prediction)\.css", p.read_text(encoding="utf-8", errors="ignore")), p
```

- [ ] **Step 2: Run to see it fail**
Run: `tools/ci/remote-test.sh python tests/test_ui_css_manifest.py`
Expected: `REMOTE_TEST=FAIL` com o conjunto excedente (ex.: `['styles-autocal-cockpit.css', 'styles-dashboard-now.css', ...]`).

- [ ] **Step 3: Implement** — renomear com `git mv`; se `styles-calibration-obd.css` ainda existir (F3 decidia por grep), mover as regras vivas para `styles.css` e apagá-lo; `index.html` com um `<link>` por arquivo existente, na ordem de `allowed`; apagar os injetores de `<link>`; atualizar referências com `git grep` + edição. Rotas sem regra própria não ganham arquivo.

- [ ] **Step 4: Run to verify**
Run: `tools/ci/remote-test.sh python tests/test_ui_css_manifest.py` → `REMOTE_TEST=PASS`
Run: `tools/ci/remote-test.sh checks ""` → `REMOTE_TEST=PASS` (pega todo teste Node/Python que lia nome antigo)

- [ ] **Step 5: Commit** `refactor(ui): um CSS por tela, links estáticos, sem injeção por JS`

---

### Task 8.5: Migrar todas as cores para tokens

**Files:**
- Modify (codemod, mecânico): todo `app/src/main/assets/ui/*.css` exceto `tokens.css`; `styles.css:1-24` (apagar o bloco `:root` antigo: `--rail`, `--surface-3`, `--line`, `--line-soft`, `--muted`, `--dim`, `--accent-soft`, `--petrol`, `--cng`, `--radius`, `--gap`; manter `--rail-width` e `color-scheme`)
- Test: `tests/test_ui_no_literal_colors.py`

**Interfaces:**
- Consumes: `literal_colors`, `rewrite` (Task 8.2).
- Produces: renomeação de variáveis antigas aplicada pelo mesmo commit: `--line`→`--stroke`, `--line-soft`→`--stroke`, `--muted`→`--text-2`, `--dim`→`--text-2` (4,05:1 sobre `--surface-2`, reprovava AA), `--rail`→`--bg`, `--surface-3`→`--surface-2`, `--petrol`→`--text-2`, `--cng`→`--ok`, `--radius`→`--r-md`, `--gap`→`--sp-3`.

- [ ] **Step 1: Write the failing test** `tests/test_ui_no_literal_colors.py`

```python
import sys, re, pathlib
root = pathlib.Path(__file__).resolve().parents[1]; sys.path.insert(0, str(root / "tools/ui"))
from css_tokens import literal_colors
ui = root / "app/src/main/assets/ui"
bad = []
for p in sorted(ui.rglob("*.css")):
    if p.name != "tokens.css": bad += [f"{p.relative_to(ui)}:{n} {lit}" for n, lit in literal_colors(p.read_text(encoding="utf-8"))]
for p in sorted(ui.rglob("*.js")):
    for n, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
        if re.search(r"""['"`]#[0-9a-fA-F]{3,8}['"`]|['"`]\s*(rgba?|hsla?)\(|['"`](white|black)['"`]""", line): bad.append(f"{p.relative_to(ui)}:{n}")
html = (ui / "index.html").read_text(encoding="utf-8")
assert re.findall(r"#[0-9a-fA-F]{6}\b", html) == ["#080c12"], "só o theme-color"
assert not re.search(r"style=\"[^\"]*(#[0-9a-fA-F]{3}|rgb)", html)
for old in ("--line)", "--muted)", "--dim)", "--rail)", "--surface-3)", "--petrol)", "--cng)", "--radius)", "--gap)", "--accent-soft)"):
    for p in ui.rglob("*.css"): assert f"var({old}" not in p.read_text(encoding="utf-8"), (p.name, old)
assert not bad, "\n".join(bad[:40])
print("UI_NO_LITERAL_COLORS=PASS")
```

- [ ] **Step 2: Run to see it fail**
Run: `tools/ci/remote-test.sh python tests/test_ui_no_literal_colors.py`
Expected: `REMOTE_TEST=FAIL` listando dezenas de `styles-autocal.css:<n> #...`.

- [ ] **Step 3: Implement** — `python3 tools/ui/css_tokens.py --rewrite app/src/main/assets/ui/styles*.css` (codemod, não é teste); cada `MANUAL` vira token fixo + `opacity` controlada pela variável (ex.: `stroke: var(--text-2); opacity: calc(.12 + var(--trace-fade,0)*.3)`), nunca `color-mix()` (WebView do carro pode ser < 111); aplicar a tabela de renomeação com `sed`; apagar o `:root` antigo de `styles.css`. Revisar o diff de `styles-autocal.css` antes do commit (é o maior).

- [ ] **Step 4: Run to verify**
Run: `tools/ci/remote-test.sh python tests/test_ui_no_literal_colors.py` → `REMOTE_TEST=PASS`
Run: `tools/ci/remote-test.sh checks ""` → `REMOTE_TEST=PASS`

- [ ] **Step 5: Commit** `refactor(ui): toda cor vem de tokens.css`

---

### Task 8.6: Escala de tipo/raio/espaço + prova de que nada transborda

**Files:**
- Modify (codemod `--sizes`): `app/src/main/assets/ui/styles*.css`
- Create: `app/src/androidTest/java/com/omegas/prohub/RenderHarness.kt`, `app/src/androidTest/java/com/omegas/prohub/AcabamentoRenderTest.kt`
- Modify: `.github/workflows/verde-android-render-evidence.yml:44-108` (matrix) e `:111` (`timeout-minutes: 45`)
- Test: `tests/test_ui_size_tokens.py`, `AcabamentoRenderTest.rotasSemTransbordo`

**Interfaces:**
- Consumes: `size_literals` (Task 8.2); `ROUTES` e subpáginas (índice); `SubpageTabs` (`components/subpage-tabs.js`, botões com `data-subpage="<id>"` — confirmar o atributo no F6 por `grep -n "data-" components/subpage-tabs.js`).
- Produces (`RenderHarness`, `internal object`, cópia enxuta dos utilitários privados de `RefinoRenderTest.kt:48-60, 647-690`):
  `fun launch(): ActivityScenario<MainActivity>` · `fun service(s): TelemetryForegroundService` · `fun evalRaw(s, js: String): String` · `fun evalJson(s, js: String): JSONObject` · `fun saveEvidence(name: String, dom: JSONObject, s, provenance: JSONObject)` · `fun waitFor(timeoutMs: Long, cond: () -> Boolean)` · `fun navigate(s, route: String, subpage: String?)` (clica no botão do trilho e na aba) · `fun tap(s, selector: String): Long` (injeta `MotionEvent` DOWN/UP no centro do elemento via `uiAutomation.injectInputEvent`, devolve `uptimeMillis` do DOWN).
- Cenário de workflow: `acabamento-sem-transbordo` → `rotasSemTransbordo`, `class: AcabamentoRenderTest`.

- [ ] **Step 1: Write the failing tests**

`tests/test_ui_size_tokens.py`:
```python
import sys, pathlib
root = pathlib.Path(__file__).resolve().parents[1]; sys.path.insert(0, str(root / "tools/ui"))
from css_tokens import size_literals
ui = root / "app/src/main/assets/ui"
bad = [f"{p.name}:{n} {lit}" for p in sorted(ui.glob("*.css")) if p.name != "tokens.css" for n, lit in size_literals(p.read_text(encoding="utf-8"))]
assert not bad, "\n".join(bad[:40])
print("UI_SIZE_TOKENS=PASS")
```

`AcabamentoRenderTest.rotasSemTransbordo` (classe 4): para cada par `(route, subpage)` em `dashboard·—, map·—, curve·equivalencia, curve·editar, curve·backups, autocal·aquisicao, autocal·referencia, autocal·epocas, refino·fases, refino·pontos, sessions·evolucao, sessions·lista, tools·—`: `navigate`, espera 600 ms, avalia
```js
JSON.stringify((() => { const s = document.querySelector('.screen.active');
  const small = [...s.querySelectorAll('*')].filter(e => e.offsetParent && e.textContent.trim() && parseFloat(getComputedStyle(e).fontSize) < 12).length;
  return { route: s.dataset.screen, sw: s.scrollWidth, cw: s.clientWidth, docSw: document.documentElement.scrollWidth, vw: innerWidth, small }; })())
```
e afirma `assertTrue("$route/$sub transborda", dom.getInt("sw") <= dom.getInt("cw") + 1 && dom.getInt("docSw") <= dom.getInt("vw"))`, `assertEquals("texto < 12 px em $route/$sub", 0, dom.getInt("small"))`; `saveEvidence("acabamento-$route-${sub ?: "raiz"}", …)` com proveniência `SYNTHETIC_EMPTY`.

- [ ] **Step 2: Run to see it fail**
Run: `tools/ci/remote-test.sh python tests/test_ui_size_tokens.py` → `REMOTE_TEST=FAIL` listando `styles-autocal.css:<n> font-size:9px` …
Run render (ver Global Constraints) → `SCENARIO_RESULT=acabamento-sem-transbordo:FAIL` com `texto < 12 px em autocal/aquisicao`.

- [ ] **Step 3: Implement** — `python3 tools/ui/css_tokens.py --rewrite --sizes app/src/main/assets/ui/styles*.css`; adicionar a entrada da matrix e o timeout. Se o render acusar transbordo, corrigir **layout** (colunas `minmax(0,1fr)`, `overflow-wrap`, rótulo abreviado), nunca reduzir a fonte abaixo do token.

- [ ] **Step 4: Run to verify**
Run: `tools/ci/remote-test.sh python tests/test_ui_size_tokens.py` → `REMOTE_TEST=PASS`
Run render → `SCENARIO_RESULT=acabamento-sem-transbordo:PASS` e todos os cenários anteriores `PASS`.

- [ ] **Step 5: Commit** `refactor(ui): escala única de tipo, raio e espaço; prova de não transbordo`

---

### Task 8.7: Cor é estado — `StateColors` único e accent disciplinado

**Files:**
- Create: `app/src/main/assets/ui/core/state-colors.js`
- Modify: `app/src/main/assets/ui/index.html` (script antes das telas), o renderizador de curva do F6 (`grep -rn "PointState\|state ===\|\.state)" app/src/main/assets/ui/screens/curve.js app/src/main/assets/ui/screens/dashboard.js app/src/main/assets/ui/screens/refino.js`) passa a chamar `StateColors.forPoint`
- Modify: `styles.css` (classes `.primary-action`, `.now-marker`; trilho ativo passa de `--accent-soft` para `--surface-2` + `--text`)
- Test: `tests/ui/state-colors.test.cjs`

**Interfaces:**
- Consumes: `points[i]` do snapshot com `state` (nome de `PointState`) e `mixture` (fração, + = pobre) — índice F4/F5; `OpStage` (índice F5).
- Produces (`window.OmegasUi.StateColors`):
  - `forPoint(point: {state: string, mixture: number|null}) -> { token: string|null, hollow: boolean }`
  - `forStage(stage: string) -> string` (`CONCLUIDO`→`--ok`, `FALHOU`→`--danger`, demais→`--text-2`)
  - `NOW = '--accent'`, `PRIMARY = '--accent'`, `LIGHT = 0.08` (igual a `EquivalenceTolerances.LIGHT`)
  - `resolve(token: string) -> string` (`getComputedStyle(document.documentElement).getPropertyValue(token).trim()`, para `<canvas>`)
- CSS: `.primary-action` (`background: var(--accent); color: var(--text); font-size: var(--fs-crit); font-weight: 800; min-height: var(--touch)`), `.now-marker` (`fill`/`background: var(--accent)`).

Tabela pinada: `EQUIVALENTE`, `CONFIRMADO` → `--ok` · `POBRE`/`RICO` com `|mixture| ≤ 0.08` ou `mixture` nulo → `--warn` · `POBRE`/`RICO` com `|mixture| > 0.08` → `--danger` · `CONTESTADO`, `INCONCLUSIVO` → `--warn` · `APRENDENDO`, `MEDIDO`, `EM_PROVA` → `--text-2` · `SEM_DADOS` → `{token: null, hollow: true}` · desconhecido → `{token: null, hollow: true}`.

- [ ] **Step 1: Write the failing test** `tests/ui/state-colors.test.cjs`

```js
'use strict';
const test = require('node:test'); const assert = require('node:assert/strict');
const fs = require('node:fs'); const path = require('node:path'); const vm = require('node:vm');
const UI = f => path.join(__dirname, '../../app/src/main/assets/ui', f);
const ctx = { console }; ctx.window = ctx; vm.createContext(ctx);
vm.runInContext(fs.readFileSync(UI('core/state-colors.js'), 'utf8'), ctx);
const C = ctx.OmegasUi.StateColors;
const tok = (state, mixture = null) => C.forPoint({ state, mixture });
test('verde equivalente, âmbar leve/contestado, vermelho grande, oco sem dado', () => {
  for (const s of ['EQUIVALENTE', 'CONFIRMADO']) assert.deepEqual({ ...tok(s) }, { token: '--ok', hollow: false });
  for (const [s, m] of [['POBRE', 0.08], ['RICO', -0.08], ['POBRE', 0.03], ['POBRE', null], ['CONTESTADO', 0.2], ['INCONCLUSIVO', null]])
    assert.equal(tok(s, m).token, '--warn', `${s} ${m}`);
  for (const [s, m] of [['POBRE', 0.0801], ['RICO', -0.12]]) assert.equal(tok(s, m).token, '--danger', `${s} ${m}`);
  for (const s of ['APRENDENDO', 'MEDIDO', 'EM_PROVA']) assert.equal(tok(s, 0.2).token, '--text-2', s);
  assert.deepEqual({ ...tok('SEM_DADOS') }, { token: null, hollow: true });
  assert.deepEqual({ ...tok('XYZ') }, { token: null, hollow: true });
  assert.equal(C.forStage('FALHOU'), '--danger'); assert.equal(C.forStage('CONCLUIDO'), '--ok'); assert.equal(C.forStage('EXECUTANDO'), '--text-2');
  assert.equal(C.NOW, '--accent'); assert.equal(C.PRIMARY, '--accent'); assert.equal(C.LIGHT, 0.08);
});
test('nenhuma outra fonte decide cor de estado', () => {
  const js = p => fs.readFileSync(UI(p), 'utf8');
  for (const f of fs.readdirSync(UI('screens')).map(f => `screens/${f}`).concat(fs.readdirSync(UI('components')).map(f => `components/${f}`)))
    assert.doesNotMatch(js(f), /['"`]--(ok|warn|danger|accent)['"`]/, f);
  assert.match(js('screens/curve.js'), /StateColors\.forPoint\(/);
});
test('accent só em ação primária, AGORA e foco', () => {
  for (const f of fs.readdirSync(UI('.')).filter(f => f.endsWith('.css') && f !== 'tokens.css')) {
    const css = fs.readFileSync(UI(f), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
    for (const [, sel, body] of css.matchAll(/([^{}]+)\{([^{}]*)\}/g))
      if (/var\(--accent/.test(body)) for (const s of sel.split(','))
        assert.match(s.trim(), /\.primary-action|\.now-marker|:focus-visible/, `${f}: ${s.trim()}`);
  }
});
```

- [ ] **Step 2: Run to see it fail**
Run: `tools/ci/remote-test.sh node tests/ui/state-colors.test.cjs`
Expected: `REMOTE_TEST=FAIL` com `ENOENT ... core/state-colors.js`.

- [ ] **Step 3: Implement** — `core/state-colors.js` (IIFE no padrão de `core/display-rules.js`); trocar no renderizador da curva (e Refino › Pontos, Agora mini curva) o mapa local de estado→classe por `StateColors.forPoint`; botões primários de F6/F7 (`grep -rn "class=\"primary\|classList.add('primary" app/src/main/assets/ui`) passam a `.primary-action`; marcador AGORA a `.now-marker`; regras de `--accent` fora disso trocam para `--text`/`--surface-2`.

- [ ] **Step 4: Run to verify**
Run: `tools/ci/remote-test.sh node tests/ui/state-colors.test.cjs` → `REMOTE_TEST=PASS`
Run: `tools/ci/remote-test.sh checks ""` → `REMOTE_TEST=PASS`

- [ ] **Step 5: Commit** `feat(ui): cor de estado em um lugar só; accent restrito a ação primária e AGORA`

---

### Task 8.8: Nenhum timer de UI

**Files:**
- Modify/Delete: `app/src/main/assets/ui/core/scheduler.js:28-30` (`armTimer` com `setInterval`) — se ainda existir após F5–F7, apagar o arquivo e trocar cada `scheduler.addHook(...)` por `Omegas.onRevision(cb)`; `screens/refino.js:330-345` (laço `setTimeout(tick, POLL_MS)`) idem
- Modify: `app/src/main/assets/ui/index.html` (tirar `<script src="core/scheduler.js">` se apagado)
- Test: `tests/test_ui_no_polling.py`

**Interfaces:**
- Consumes: `Omegas.onRevision(cb)` (`core/omegas.js`, índice F5).

- [ ] **Step 1: Write the failing test** `tests/test_ui_no_polling.py`

```python
import re, pathlib
root = pathlib.Path(__file__).resolve().parents[1]
ui = root / "app/src/main/assets/ui"
for p in sorted(ui.rglob("*.js")):
    src = p.read_text(encoding="utf-8")
    assert "setInterval" not in src, p
    assert not re.search(r"setTimeout\([^;]*\b[A-Z_]*POLL[A-Z_]*\b", src), f"polling por setTimeout em {p}"
    assert not re.search(r"setTimeout\(\s*(tick|poll|loop|refresh)\s*,", src), f"laço de setTimeout em {p}"
print("UI_NO_POLLING=PASS")
```

- [ ] **Step 2: Run to see it fail**
Run: `tools/ci/remote-test.sh python tests/test_ui_no_polling.py`
Expected: `REMOTE_TEST=FAIL` com `core/scheduler.js` (ou, se F5–F7 já removeram tudo, `PASS` de primeira: registrar no PR "já satisfeito pelo F5–F7" e commitar só o teste).

- [ ] **Step 3: Implement** — revisão nova chega por `Omegas.onRevision`; dentro do callback, coalescer em um `requestAnimationFrame` (um redesenho por quadro, nunca por timer).

- [ ] **Step 4: Run to verify**
Run: `tools/ci/remote-test.sh python tests/test_ui_no_polling.py` → `REMOTE_TEST=PASS`
Run: `tools/ci/remote-test.sh checks ""` → `REMOTE_TEST=PASS`

- [ ] **Step 5: Commit** `refactor(ui): render só por revisão, sem setInterval nem laço de setTimeout`

---

### Task 8.9: Orçamento medido — primeira pintura < 300 ms, toque < 100 ms

**Files:**
- Create: `app/src/main/assets/ui/core/perf.js`
- Modify: `app/src/main/assets/ui/index.html` (script antes de `app.js`), `app/src/main/assets/ui/app.js` (callback de `Omegas.onRevision`: `Perf.snapshotTaken(rev)` logo após `Omegas.snapshot(...)` retornar; `requestAnimationFrame(() => Perf.painted(rev))` após aplicar o render)
- Modify: `app/src/androidTest/java/com/omegas/prohub/AcabamentoRenderTest.kt`, `.github/workflows/verde-android-render-evidence.yml` (cenários `perf-primeira-pintura`, `perf-toque`)
- Test: `tests/ui/perf-marks.test.cjs`, `AcabamentoRenderTest.primeiraPinturaAposSnapshot`, `AcabamentoRenderTest.respostaAoToque`

**Interfaces:**
- Produces (`window.OmegasUi.Perf`, anel de 256 entradas, sem crescimento):
  `snapshotTaken(revision: number): void` · `painted(revision: number): void` · `entries(): Array<{revision, snapshotAt, paintedAt}>` (só pares completos, `performance.now()`) · `installTouchProbe(): void` (listener `pointerdown` em captura guarda `event.timeStamp`; no `click` em captura agenda `requestAnimationFrame(() => requestAnimationFrame(() => …))` e guarda o fim) · `touches(): Array<{selector, downAt, paintedAt}>` · `clear(): void`.

- [ ] **Step 1: Write the failing tests**

`tests/ui/perf-marks.test.cjs`: com `performance.now` falso (0, 10, 20, …) e `requestAnimationFrame` síncrono no contexto `vm`: `snapshotTaken(5); painted(5)` → `entries()` = `[{revision:5, snapshotAt:0, paintedAt:10}]`; `painted(6)` sem `snapshotTaken(6)` não gera entrada; 300 pares → `entries().length === 256` e a primeira revisão é 44; `clear()` zera.

`AcabamentoRenderTest.primeiraPinturaAposSnapshot` (classe 4): `launch()`; `waitFor(5_000) { Perf.entries().length >= 1 }`; `first = entries()[0]`; `assertTrue("primeira pintura ${d} ms (limite 300)", first.paintedAt - first.snapshotAt < 300.0)`; depois injeta 60 quadros de `livePayloads()` (fixture `portmon-autocal-cycle-v1.json`, mesmo método de `RefinoRenderTest.kt:462-483`, via `service.telemetryStore.updateFromEngineEvent`) a cada 80 ms e afirma p95 de `paintedAt - snapshotAt` < 100 ms sobre as entradas novas (≥ 10). Evidência `perf-primeira-pintura` com `{first, p95, n}`.

`AcabamentoRenderTest.respostaAoToque`: `evalRaw("OmegasUi.Perf.installTouchProbe()")`; `RenderHarness.tap` em cada um dos 7 botões do trilho (`.side-nav button[data-route="<r>"]`) e em cada aba de subpágina de `curve`, `autocal`, `refino`, `sessions` (10 abas), 2 voltas = 34 toques, 250 ms entre eles; `lat = touches().map { paintedAt - downAt }`; `assertTrue("toque p95 ${p95} ms (limite 100)", p95 < 100.0)`; `assertTrue("toque máx ${max} ms (limite 200)", max < 200.0)`; `assertEquals(34, lat.size)`.

- [ ] **Step 2: Run to see it fail**
Run: `tools/ci/remote-test.sh node tests/ui/perf-marks.test.cjs` → `REMOTE_TEST=FAIL` (`ENOENT core/perf.js`)
Run render → `SCENARIO_RESULT=perf-primeira-pintura:FAIL` (`condition timeout`), `perf-toque:FAIL`.

- [ ] **Step 3: Implement** `core/perf.js` (IIFE, sem dependências) e as duas chamadas em `app.js`; entradas da matrix com `class: AcabamentoRenderTest`.

- [ ] **Step 4: Run to verify**
Run: `tools/ci/remote-test.sh node tests/ui/perf-marks.test.cjs` → `REMOTE_TEST=PASS`
Run render → `perf-primeira-pintura:PASS`, `perf-toque:PASS`, `session-kill-recovery:PASS` (mantido).

- [ ] **Step 5: Commit** `test(perf): primeira pintura e resposta ao toque medidas no emulador`

---

### Task 8.10: Sessão longa sem vazamento nem jank (proxy das 4 h)

**Files:**
- Modify: `app/src/androidTest/java/com/omegas/prohub/AcabamentoRenderTest.kt`, `.github/workflows/verde-android-render-evidence.yml` (cenário `perf-soak`)
- Test: `AcabamentoRenderTest.soakSemVazamento`, `tests/test_render_workflow_contract.py`

**Interfaces:**
- Consumes: `RenderHarness`, `OmegasUi.Perf` (Task 8.9).

- [ ] **Step 1: Write the failing tests**

`tests/test_render_workflow_contract.py`:
```python
import pathlib, re
root = pathlib.Path(__file__).resolve().parents[1]
wf = (root / ".github/workflows/verde-android-render-evidence.yml").read_text(encoding="utf-8")
for scenario, method in {"session-kill-recovery": "killProof", "acabamento-sem-transbordo": "rotasSemTransbordo",
                         "perf-primeira-pintura": "primeiraPinturaAposSnapshot", "perf-toque": "respostaAoToque", "perf-soak": "soakSemVazamento"}.items():
    assert re.search(rf"scenario: {scenario}\s+method: {method}", wf), scenario
assert int(re.search(r"timeout-minutes: (\d+)", wf).group(1)) >= 45
print("RENDER_WORKFLOW_CONTRACT=PASS")
```

`AcabamentoRenderTest.soakSemVazamento` (classe 4, 12 min ≈ 9 000 quadros): quadros de `livePayloads()` em ciclo a cada 80 ms (`session` fixo); a cada 30 s `navigate` para a próxima de `ROUTES` (24 trocas); amostras nos minutos 2 e 12 de `{nodes: document.getElementsByTagName('*').length, heap: performance.memory ? performance.memory.usedJSHeapSize : -1, listeners: OmegasUi.Perf.entries().length}` e, no Kotlin, `Runtime.getRuntime().let { System.gc(); it.totalMemory() - it.freeMemory() }`. Antes de cada amostra, voltar a `dashboard` e esperar 1 s (mesma tela nas duas amostras). Afirmações:
- `nodes12 <= nodes2 * 1.05`
- `heap12 <= heap2 * 1.25` (pular com `assumeTrue` só se `heap == -1`)
- `java12 <= java2 * 1.25 + 8 MiB`
- p95 de `paintedAt - snapshotAt` no último minuto < 100 ms e ≤ 1,5 × o p95 do minuto 2
- quadros longos: `rAF` com intervalo > 50 ms no último minuto ≤ 30 (medido por um laço `requestAnimationFrame` do próprio teste, instalado e removido dentro do `evalRaw`)
Evidência `perf-soak` com as duas amostras e a proveniência `SYNTHETIC_REPLAY portmon-autocal-cycle-v1`.

- [ ] **Step 2: Run to see it fail**
Run: `tools/ci/remote-test.sh python tests/test_render_workflow_contract.py` → `REMOTE_TEST=FAIL` (`AssertionError: perf-soak`)
Run render → sem o cenário ainda; depois de adicionar a matrix e antes de corrigir qualquer vazamento achado: `SCENARIO_RESULT=perf-soak:FAIL` com o limite que estourou (se passar de primeira, registrar no PR como "sem vazamento detectado").

- [ ] **Step 3: Implement** — entrada da matrix; se houver vazamento, a causa típica é listener/elemento recriado por revisão: corrigir no componente apontado pela diferença de `nodes` (por `querySelectorAll` contado por classe na evidência).

- [ ] **Step 4: Run to verify**
Run: `tools/ci/remote-test.sh python tests/test_render_workflow_contract.py` → `REMOTE_TEST=PASS`
Run render → `perf-soak:PASS` e `session-kill-recovery:PASS`.

- [ ] **Step 5: Commit** `test(perf): sessão longa comprimida sem vazamento nem jank`

---

### Task 8.11: Balão com índice % e mini AGORA vindos do `StateStore`

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/service/OverlayBalloonModel.kt`
- Modify: `app/src/main/java/com/omegas/prohub/service/TelemetryOverlayController.kt:58-67` (`Snapshot` ganha campos), `:127-233` (`show()`: linha do índice + faixa mini AGORA acima dos números; cores literais `0xFF…` em `:151,:162,:164,:169,:300,:314,:319` viram `OverlayTokens`), `:286-302` (`render`)
- Modify: `app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt:1019-1035` (`updateOverlay()` lê as seções `index` e `now`)
- Test: `app/src/test/java/com/omegas/prohub/service/OverlayBalloonModelTest.kt`, `tests/test_overlay_tokens_contract.py`

**Interfaces:**
- Consumes (F5): leitura de seção do `StateStore` pelo nome de chave do snapshot (`"index"`, `"now"`) — usar o acessor exato que o plano F5 definir (aqui escrito `stateStore.section(key): JSONObject?`). Campos lidos: `index.value: Double?` (0..1), `index.coverage: Int`; `now.pointIndex: Int?` (0..29), `now.state: String?` (nome de `PointState`), `now.mixture: Double?`, `now.fresh: Boolean`, `now.fuel: String?`, `now.rpm`, `now.mapBar`, `now.petrolMs`, `now.gasMs`. Se o F5 nomear diferente, adaptar só `OverlayBalloonModel.from`.
- Produces:
  - `object OverlayTokens { val BG: Int; val SURFACE: Int; val SURFACE_2: Int; val STROKE: Int; val TEXT: Int; val TEXT_2: Int; val ACCENT: Int; val OK: Int; val WARN: Int; val DANGER: Int; fun forState(state: String?, mixture: Double?): Int? }` (`forState` espelha `StateColors.forPoint`; `null` = oco)
  - `object OverlayBalloonModel { fun from(index: JSONObject?, now: JSONObject?): TelemetryOverlayController.Snapshot; fun indexLabel(value: Double?): String }`
  - `TelemetryOverlayController.Snapshot` + `val indexPercent: Int? = null, val coverage: Int = 0, val nowSlot: Int? = null, val nowColor: Int? = null` (campos antigos mantidos).

- [ ] **Step 1: Write the failing tests**

`OverlayBalloonModelTest` (JUnit, JVM puro com `org.json` do classpath de teste já usado pelos testes de `autocal/`):
```kotlin
@Test fun indiceArredondaEMostraTracoSemCobertura() {
  assertEquals("87%", OverlayBalloonModel.indexLabel(0.8649))
  assertEquals("—%", OverlayBalloonModel.indexLabel(null))
  assertEquals("0%", OverlayBalloonModel.indexLabel(0.0))
}
@Test fun agoraNoSlotDoPontoComCorDeEstado() {
  val s = OverlayBalloonModel.from(JSONObject("""{"value":0.62,"coverage":18}"""),
    JSONObject("""{"pointIndex":12,"state":"POBRE","mixture":0.05,"fresh":true,"fuel":"GNV","rpm":2100,"mapBar":0.61,"petrolMs":5.8,"gasMs":6.4}"""))
  assertEquals(62, s.indexPercent); assertEquals(18, s.coverage); assertEquals(12, s.nowSlot)
  assertEquals(OverlayTokens.WARN, s.nowColor); assertTrue(s.live); assertEquals("GNV", s.fuel); assertEquals(2100.0, s.rpm!!, 0.0)
}
@Test fun semDadoNuncaViraZero() {
  val s = OverlayBalloonModel.from(null, JSONObject("""{"pointIndex":null,"fresh":false,"rpm":0}"""))
  assertNull(s.indexPercent); assertNull(s.nowSlot); assertFalse(s.live); assertNull(s.rpm)
}
@Test fun corDeEstadoIgualAoJs() {
  assertEquals(OverlayTokens.OK, OverlayTokens.forState("CONFIRMADO", null))
  assertEquals(OverlayTokens.WARN, OverlayTokens.forState("RICO", -0.08))
  assertEquals(OverlayTokens.DANGER, OverlayTokens.forState("POBRE", 0.0801))
  assertNull(OverlayTokens.forState("SEM_DADOS", null))
}
```

`tests/test_overlay_tokens_contract.py`: lê `tokens.css` (canais `--X-rgb`) e `OverlayBalloonModel.kt`; para cada um dos 10 nomes, `val <NOME> = 0xFF<rrggbb>.toInt()` bate com o canal (`BG`↔`bg`, `SURFACE_2`↔`surface-2`, `TEXT_2`↔`text-2`, …); afirma que `TelemetryOverlayController.kt` não contém `0xFF` nem `Color.WHITE` (só `OverlayTokens.*`); mantém os marcadores de `tests/test_background_power_overlay_contract.py:42` (`RPM`, `PETROL INJ.`, `MAP`, `GÁS`, `SEM DADO`). Imprime `OVERLAY_TOKENS=PASS`.

- [ ] **Step 2: Run to see it fail**
Run: `tools/ci/remote-test.sh gradle "com.omegas.prohub.service.OverlayBalloonModelTest"` → `REMOTE_TEST=FAIL` (`Unresolved reference: OverlayBalloonModel`)
Run: `tools/ci/remote-test.sh python tests/test_overlay_tokens_contract.py` → `REMOTE_TEST=FAIL`

- [ ] **Step 3: Implement** — `OverlayTokens` e `OverlayBalloonModel` no arquivo novo (`indexLabel` = `"%d%%".format(round(value*100))`, `—%` se nulo; `live = now.fresh == true`; números ≤ 0 viram `null`, como hoje em `TelemetryForegroundService.kt:1028-1032`); no controller, uma linha `índice 87% · 18/30` (texto `--fs-crit` equivalente: 24 sp × escala) e uma `View` privada `MiniNowStrip` (30 traços `STROKE`; o slot `nowSlot` desenhado com `nowColor` e um ▲ `ACCENT` acima; sem slot → só os traços); `updateOverlay()` passa a `overlay.update(OverlayBalloonModel.from(stateStore.section("index"), stateStore.section("now")))`. O throttle de 250 ms em `update()` (`:122`) fica.

- [ ] **Step 4: Run to verify**
Run: `tools/ci/remote-test.sh gradle "com.omegas.prohub.service.OverlayBalloonModelTest"` → `REMOTE_TEST=PASS`
Run: `tools/ci/remote-test.sh python tests/test_overlay_tokens_contract.py` → `REMOTE_TEST=PASS`
Run: `tools/ci/remote-test.sh python tests/test_background_power_overlay_contract.py` → `REMOTE_TEST=PASS`

- [ ] **Step 5: Commit** `feat(balão): índice % e mini AGORA lidos do StateStore, cores dos tokens`

---

### Task 8.12: Protocolo físico das 7 abas + `STATUS.md` modelo

**Files:**
- Modify (reescrever): `docs/V82_PHYSICAL_VALIDATION_PROTOCOL.md`
- Modify: `STATUS.md`
- Test: `tests/test_physical_protocol_contract.py`

**Interfaces:**
- Produces: chaves de `STATUS.md` que a Task 8.14 preenche: `APK_SOURCE_SHA=`, `APK_SHA256=`, `APK_RUN=`, `PROOF_CLASS=`, `PHYSICAL_VALIDATION_CLAIMED=false`, seções `## O que o dono viu no carro` e `## Não provado`.

Conteúdo do protocolo (o dono lê no carro, frase curta, uma caixa por item): **Antes** (APK e SHA-256 conferidos, cabo, balão autorizado, sessão anterior exportada); **01 Agora** (índice aparece ou `—` com cobertura; próxima ação é uma só e o botão abre a aba certa já posicionada; ▲ AGORA anda com o motor); **02 Mapa K** (célula ao vivo segue RPM×MAP; Gravar → overlay `Conferindo na ECU` → `✓` → `Desfazer` volta); **03 Curva K** (Equivalência: Referência tracejada, Própria sólida, 30 pontos com cor de estado, Aplicar com fantasma; Editar; Backups: salvar/restaurar); **04 AutoCal** (Aquisição: matriz 18×2 viva, Pausar/Retomar; Referência: Congelar, depois "Congelar de novo" mostra a diferença; Épocas); **05 Refino** (Fases com estado; Pontos: 30 linhas, tocar expande); **06 Sessões** (Evolução do índice; Lista; Exportar ZIP abre no `Download/Omegas`); **07 Ferramentas** (retenção, log, autoteste, balão, GPS); **Referência** (congelar com ECU madura; ECU reaprende → Referência não muda e aparece "a ECU reaprendeu… diferença máx. n%"); **Curva Própria** (depois de ~30 min de gasolina, células maduras ficam sólidas; discordância > 8 % aparece em âmbar com "Reaprender ponto na ECU"); **Balão** (sobre outro app: índice %, mini AGORA, combustível; some ao voltar ao OMEGAS); **Sensação** (GNV × gasolina no mesmo trecho: tremor, quase-apagão, retomada); **Cabo** (puxar no meio de Aplicar → `✗ Cabo` com próxima ação; religar → Desfazer funciona); **Registro** (o que anotar em `STATUS.md`, inclusive o que não gostou).

- [ ] **Step 1: Write the failing test** `tests/test_physical_protocol_contract.py`

```python
import pathlib, re
root = pathlib.Path(__file__).resolve().parents[1]
doc = (root / "docs/V82_PHYSICAL_VALIDATION_PROTOCOL.md").read_text(encoding="utf-8")
for h in ("## Antes", "## 01 Agora", "## 02 Mapa K", "## 03 Curva K", "## 04 AutoCal", "## 05 Refino", "## 06 Sessões",
          "## 07 Ferramentas", "## Referência", "## Curva Própria", "## Balão", "## Sensação", "## Cabo", "## Registro"):
    assert h in doc, h
assert doc.count("- [ ]") >= 40
for gone in ("Learning", "AutoMatch manual", "Sugestões", "LEARNING_RESTORING", "confirmação Android"): assert gone not in doc, gone
status = (root / "STATUS.md").read_text(encoding="utf-8")
for k in ("APK_SOURCE_SHA=", "APK_SHA256=", "APK_RUN=", "PROOF_CLASS=", "PHYSICAL_VALIDATION_CLAIMED=", "## O que o dono viu no carro", "## Não provado"):
    assert k in status, k
claimed = re.search(r"PHYSICAL_VALIDATION_CLAIMED=(true|false)", status).group(1)
if claimed == "false": assert not re.search(r"\bvalidad[oa]s?\b", status, re.I), "nada é 'validado' sem o carro (spec §6)"
sha = re.search(r"APK_SHA256=(\S+)", status).group(1)
assert sha == "PENDENTE" or re.fullmatch(r"[0-9a-f]{64}", sha), sha
assert len(status.splitlines()) <= 40
print("PHYSICAL_PROTOCOL=PASS")
```

- [ ] **Step 2: Run to see it fail**
Run: `tools/ci/remote-test.sh python tests/test_physical_protocol_contract.py`
Expected: `REMOTE_TEST=FAIL` com `AssertionError: ## Antes`.

- [ ] **Step 3: Implement** — reescrever o protocolo do zero com as seções acima (≥ 40 caixas); `STATUS.md` com as chaves em `PENDENTE`, `PROOF_CLASS=4`, `PHYSICAL_VALIDATION_CLAIMED=false` (≤ 40 linhas, compatível com `tests/test_governance_contract.py` do F0).

- [ ] **Step 4: Run to verify**
Run: `tools/ci/remote-test.sh python tests/test_physical_protocol_contract.py` → `REMOTE_TEST=PASS`
Run: `tools/ci/remote-test.sh python tests/test_governance_contract.py` → `REMOTE_TEST=PASS`

- [ ] **Step 5: Commit** `docs: protocolo físico das 7 abas, Referência e Curva Própria; STATUS modelo`

---

### Task 8.13: Contrato único de "fechado" (spec §8 itens 1–6)

**Files:**
- Create: `tests/test_norte_unico_fechado.py`
- Modify: o que o teste apontar (cada violação corrigida no mesmo commit, ou PR para em `FAIL` e volta à fatia dona)

**Interfaces:**
- Consumes: `OmegasBridge`, `StateStore`, `OperationQueue`, `Intent` (F5), `ReferenceStore`, `EquivalenceEngine` (F4), `tokens.css`, `index.html`, `AGENTS.md`.

- [ ] **Step 1: Write the failing test** `tests/test_norte_unico_fechado.py` — um arquivo, seis blocos, cada `assert` com mensagem que diz o item da spec:

```python
import re, pathlib
root = pathlib.Path(__file__).resolve().parents[1]
main = root / "app/src/main/java/com/omegas/prohub"; ui = root / "app/src/main/assets/ui"
kt = {p: p.read_text(encoding="utf-8") for p in main.rglob("*.kt")}
def declared(kind_name):  # arquivos que declaram exatamente esse tipo
    return [p for p, s in kt.items() if re.search(rf"^\s*(?:(?:internal|private|public|data|enum|sealed|abstract|open|value|fun)\s+)*(?:class|object|interface)\s+{kind_name}\b", s, re.M)]
# §8.1 uma ponte, um StateStore, uma fila, uma Referência, um cérebro, um tokens.css
bridges = [p for p, s in kt.items() if "@JavascriptInterface" in s]
assert len(bridges) == 1 and re.search(r"class OmegasBridge\b", kt[bridges[0]]), f"§8.1 ponte única: {bridges}"
adds = [m for s in kt.values() for m in re.findall(r'addJavascriptInterface\([^,]+,\s*"(\w+)"\)', s)]
assert adds == ["Omegas"], f"§8.1 nome JS único: {adds}"
for t in ("StateStore", "OperationQueue", "ReferenceStore", "EquivalenceEngine"): assert len(declared(t)) == 1, f"§8.1 {t}: {declared(t)}"
for dead in ("MotorLearningMemory", "V7JavascriptBridge", "PredictorSurface", "AssistedCalibrationAdvisor", "HubJavascriptBridge", "AutoCalJavascriptBridge"):
    assert not any(re.search(rf"\b{dead}\b", s) for s in kt.values()), f"§8.1 cérebro/ponte morta ainda citada: {dead}"
assert not (main / "learning").exists() and not (root / "app/src/main/java/com/omegas/v7").exists(), "§8.1 pacotes mortos"
assert [p.name for p in (root / "app/src/main").rglob("tokens.css")] == ["tokens.css"], "§8.1 um tokens.css"
# §8.2 todo Intent tem handler no Kotlin e chamador no JS; todo intent do JS existe
enum_file = declared("Intent")[0]
values = re.findall(r"\b([A-Z][A-Z_]+)\b", re.search(r"enum class Intent\s*\{([^}]*)\}", kt[enum_file]).group(1))
assert values == ["CURVE_WRITE","CURVE_RESET","CURVE_RESTORE","MAP_WRITE","REFERENCE_FREEZE","AUTOCAL_RELEARN","AUTOCAL_PAUSE",
                  "AUTOCAL_RESUME","AUTOCAL_RESET_GAS","AUTOCAL_RESET_PETROL","SESSION_EXPORT","OVERLAY_TOGGLE","SETTINGS_SET","UNDO"], values
# arquivos JS/CSS alcançáveis a partir do index.html (scripts, links e loadOptionalScript recursivo)
html = (ui / "index.html").read_text(encoding="utf-8")
reach, todo = set(), re.findall(r'(?:src|href)="([^"]+\.(?:js|css))"', html)
while todo:
    f = todo.pop()
    if f in reach or not (ui / f).is_file(): continue
    reach.add(f)
    if f.endswith(".js"): todo += re.findall(r"""loadOptionalScript\(\s*['"]([^'"]+\.js)['"]""", (ui / f).read_text(encoding="utf-8"))
js = "\n".join((ui / f).read_text(encoding="utf-8") for f in reach if f.endswith(".js"))
for v in values:
    assert any(re.search(rf"\bIntent\.{v}\b", s) for p, s in kt.items() if p != enum_file), f"§8.2 Intent.{v} sem handler"
    assert re.search(rf"""['"]{v}['"]""", js), f"§8.2 Intent.{v} sem botão no JS"
for v in set(re.findall(r"""intent\s*:\s*['"]([A-Z_]+)['"]""", js)): assert v in values, f"§8.2 intent JS desconhecido: {v}"
# §8.3 nenhum método da ponte, arquivo JS/CSS ou classe sem consumidor
for m in re.findall(r"@JavascriptInterface\s+fun\s+(\w+)", kt[bridges[0]]): assert re.search(rf"\.{m}\(", js), f"§8.3 Omegas.{m} sem chamador"
files = {str(p.relative_to(ui)) for p in ui.rglob("*") if p.suffix in (".js", ".css")}
assert files == reach, f"§8.3 órfãos: {sorted(files - reach)}; faltando: {sorted(reach - files)}"
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
for p in kt:
    stem = p.stem
    if p not in declared(stem): continue   # arquivo só de funções de topo: não é classe
    if not any(re.search(rf"\b{stem}\b", s) for q, s in kt.items() if q != p) and stem not in manifest:
        raise AssertionError(f"§8.3 classe sem consumidor: {p.relative_to(root)}")
# §8.4 índice no snapshot e guardado por sessão
assert any('"index"' in s for s in kt.values()), "§8.4 seção index no snapshot"
# §8.5 7 rotas com as subpáginas do índice
router = (ui / "core/router.js").read_text(encoding="utf-8")
assert "['dashboard','map','curve','autocal','refino','sessions','tools']" in router.replace(" ", "").replace('"', "'"), "§8.5 ROUTES"
for sub in ("equivalencia", "editar", "backups", "aquisicao", "referencia", "epocas", "fases", "pontos", "evolucao", "lista"):
    assert re.search(rf"""['"]{sub}['"]""", js), f"§8.5 subpágina {sub}"
# §8.6 AGENTS.md cabe numa tela
assert len((root / "AGENTS.md").read_text(encoding="utf-8").splitlines()) <= 60, "§8.6 AGENTS.md ≤ 60 linhas"
print("NORTE_UNICO_FECHADO=PASS")
```

- [ ] **Step 2: Run to see it fail**
Run: `tools/ci/remote-test.sh python tests/test_norte_unico_fechado.py`
Expected: `REMOTE_TEST=FAIL` com a primeira violação real (ex.: `§8.3 órfãos: ['core/learning-model.js']` ou `§8.3 classe sem consumidor: …`). Se passar de primeira, registrar no PR que F1–F7 já fecharam o item.

- [ ] **Step 3: Implement** — corrigir cada violação apontada: órfão apagado (ou carregado se tiver uso real), classe sem consumidor apagada, intent sem botão ligado ao botão que a spec §3.1 prevê. Mudança fora de 1–3 arquivos de produção vira commit separado por violação.

- [ ] **Step 4: Run to verify**
Run: `tools/ci/remote-test.sh python tests/test_norte_unico_fechado.py` → `REMOTE_TEST=PASS`, log `NORTE_UNICO_FECHADO=PASS`

- [ ] **Step 5: Commit** `test(contrato): Norte Único fechado (spec §8 itens 1–6)`

---

### Task 8.14: Gate completo, render, APK final, STATUS e merge

**Files:**
- Modify: `STATUS.md` (chaves da Task 8.12)

- [ ] **Step 1: Gate completo**
Run: `tools/ci/remote-test.sh checks ""` → `REMOTE_TEST=PASS`
Run render → todos os cenários `SCENARIO_RESULT=…:PASS` (incluindo `session-kill-recovery`, `acabamento-sem-transbordo`, `perf-primeira-pintura`, `perf-toque`, `perf-soak`).

- [ ] **Step 2: PR pronto e CI no SHA**
Run: `gh pr ready && gh pr checks --watch` → `OMEGAS PLATINA CI` verde. Guardar `X=$(git rev-parse HEAD)`.

- [ ] **Step 3: APK do SHA exato**
Run: `gh workflow run verde-apk-now.yml --ref work/platina-f8-acabamento -f build_apk=true && sleep 15 && RUN=$(gh run list --workflow verde-apk-now.yml --branch work/platina-f8-acabamento -L1 --json databaseId -q '.[0].databaseId') && gh run watch "$RUN" --exit-status && gh run download "$RUN" -n "omegas-platina-final-$X" -D build/apk && cat build/apk/build/evidence/omegas-platina-apk-evidence.txt`
Expected: `OMEGAS_PLATINA_APK=PASS`, `SOURCE_SHA=$X`, `APK_SHA256=<64 hex>`, `PHYSICAL_INSTALL_NOT_YET_CLAIMED=true`.

- [ ] **Step 4: STATUS.md e commit** — `APK_SOURCE_SHA=$X`, `APK_SHA256=<valor>`, `APK_RUN=$RUN`, `PROOF_CLASS=4`, `PHYSICAL_VALIDATION_CLAIMED=false`; em `## Não provado`: sensação no carro, balão sobre outros apps, cabo USB real, ECU física, 4 h reais (só o proxy de 12 min); nota "o commit seguinte a `$X` muda só `STATUS.md`".
Run: `tools/ci/remote-test.sh python tests/test_physical_protocol_contract.py` → `REMOTE_TEST=PASS`
Commit: `docs(status): APK final da Fatia 8 (SHA-256 e run)`

- [ ] **Step 5: Merge**
Run: `git push && gh pr checks --watch && gh pr merge --merge`
Expected: PR mesclado em `OmegasPlatina`. Corpo do PR: o que mudou · classe de prova 4 (render/APK no emulador) · não provado (lista do STATUS; spec §8 item 7 fica com o dono, pelo protocolo).

---

## Cobertura da spec

| Requisito (spec) | Task |
|---|---|
| §3.2 tokens com os valores exatos, um `tokens.css`, nenhum CSS define cor fora dele | 8.1, 8.2, 8.5 |
| §3.2 cor é estado (verde/âmbar/vermelho/oco; accent só ação primária e AGORA) | 8.7 (JS), 8.11 (balão) |
| §3.2 legível a ~70 cm / texto crítico ≥ 24 px / alvo ≥ 76 px (tokens `--fs-crit`, `--touch`; sem texto < 12 px, sem transbordo) | 8.1, 8.6 |
| §3.2 cards só com unidade semântica | sem tarefa nova: é conteúdo das telas (F6/F7); 8.5–8.6 não criam cards |
| Contraste AA de `--text`/`--text-2` | 8.1 |
| Limpeza de CSS/JS (um por tela, sem injeção, sem órfão) | 8.3, 8.4, 8.13 |
| §3.3 render só por revisão, nenhum `setInterval` de UI | 8.8 |
| §3.3 primeira pintura < 300 ms, toque < 100 ms | 8.9 |
| §3.3 sem jank em 4 h; SIGKILL + retomada mantido | 8.10 (proxy 12 min), 8.9/8.10 (cenário mantido no workflow) |
| §7 F8 balão | 8.11 |
| §6 / §5 protocolo físico atualizado; nada "validado" sem o carro | 8.12 |
| §8 itens 1–6 em um contrato | 8.13 |
| §7 APK com SHA-256 ao fim da fatia 8 | 8.14 |
| §8 item 7 (dono instalou e STATUS diz o que viu) | fora do CI: protocolo 8.12, registro manual depois do merge |
| §7 linha 8 "modo dirigindo" | **não colocado**: contradiz §3.1 ("a interface é a mesma parado e em movimento; não há modo"). Segue §3.1; decisão do dono se quiser o modo |
