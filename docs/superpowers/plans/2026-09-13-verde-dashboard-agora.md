# OmegasVerde Dashboard Agora Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transplantar somente a organização visual da tela Agora do Blue mais recente para o OmegasVerde, preservando integralmente os contratos e a lógica Red/Verde.

**Architecture:** O dashboard continuará sendo um consumidor puro do Store existente. A estrutura visual do blob Blue será portada para `dashboard.js`, enquanto apenas os seletores `now-*` serão isolados em uma nova folha de estilo; nenhum módulo Blue entra no runtime.

**Tech Stack:** Android WebView assets, JavaScript compatível com WebView legado, CSS 16:9, Node test runner, Python unittest, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-13-omegas-verde-design.md`

## Global Constraints

- Branch única: `OmegasVerde`.
- Base funcional: `eb9791b9341aac85dfe19fccc50e42957ee4b16f`.
- Fonte visual congelada: `work/omegas-blue-causal-engine@08c6dc79c829851c7ee52cfdf8bea280de75df59`.
- Dashboard Blue: blob `fa36673d948f73133afd7a112ccd897803a885c6`.
- CSS Blue: blob `f2245e80a1b9a7dad0ffee199c4d3c64ba141e10`.
- Não importar qualquer arquivo de `app/src/main/java/com/omegas/prohub/blue/`.
- Não alterar aprendizado, Predictor, Mapa K, Curva K, writer ou persistência.
- STFT/OBD permanecem opcionais e apenas visuais.
- Nenhum caminho de escrita na ECU pode aparecer no dashboard.
- Escritas são feitas pelo GitHub remoto; estado local não é autoridade.
- Cada mutação exige readback do blob e SHA resultantes.

---

### Task 1: Fixar o contrato visual Verde

**Files:**
- Create: `tests/ui/verde-dashboard-now.test.cjs`
- Modify: `tools/run_checks.py`
- Modify: `tests/test_clean_ui_contract.py`
- Modify: `tests/test_block1_session_contract.py`

**Interfaces:**
- Consumes: assets em `app/src/main/assets/ui/screens/dashboard.js` e `styles-dashboard-now.css`.
- Produces: contrato executável de isolamento, legibilidade 16:9 e compatibilidade WebView.

- [ ] **Step 1: Criar o teste que falha na base Red**

Criar `tests/ui/verde-dashboard-now.test.cjs`:

```javascript
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const ROOT = path.join(__dirname, '../..');

function normalizeJsSource(text) {
  return text
    .replace(/\\x([0-9A-Fa-f]{2})/g, (_, hex) =>
      String.fromCharCode(parseInt(hex, 16)))
    .replace(/\\u([0-9A-Fa-f]{4})/g, (_, hex) =>
      String.fromCharCode(parseInt(hex, 16)));
}

const dashboard = normalizeJsSource(fs.readFileSync(
  path.join(ROOT, 'app/src/main/assets/ui/screens/dashboard.js'),
  'utf8',
));
const stylePath = path.join(
  ROOT,
  'app/src/main/assets/ui/styles-dashboard-now.css',
);
const styles = fs.existsSync(stylePath)
  ? fs.readFileSync(stylePath, 'utf8')
  : '';

function occurrences(text, token) {
  return (text.match(new RegExp(token, 'g')) || []).length;
}

test('Agora Verde preserva a hierarquia multimídia Blue', () => {
  for (const marker of [
    'multimedia-now-screen',
    'now-dashboard-shell',
    'now-hero-card',
    'dashHeroPetrol',
    'dashHeroStatus',
    'dashRpm',
    'dashMap',
    'dashFuel',
    'dashStft',
    'dashCell',
    'dashHealth',
  ]) {
    assert.match(dashboard, new RegExp(marker), `missing ${marker}`);
  }

  assert.equal(occurrences(dashboard, '>RPM<'), 1);
  assert.equal(occurrences(dashboard, '>MAP<'), 1);
  assert.equal(occurrences(dashboard, '>COMBUSTÍVEL<'), 1);
  assert.equal(occurrences(dashboard, '>STFT<'), 1);
  assert.equal(occurrences(dashboard, '>CÉLULA<'), 1);
  assert.doesNotMatch(dashboard, /dashHeroRpm|dashLtft|GAS INJ\./);
});

test('CSS contém somente o recorte Agora', () => {
  assert.match(styles, /grid-template-columns:\s*repeat\(5/);
  assert.match(styles, /\.now-hero-value strong[\s\S]*font-size:\s*118px/);
  assert.match(styles, /@media \(max-width:\s*1050px\), \(max-height:\s*650px\)/);
  assert.doesNotMatch(styles, /witness-|multimedia-obd|map-screen|curve-screen|learning-screen/);
  assert.doesNotMatch(styles, /@keyframes|animation:|backdrop-filter/);
});

test('dashboard é consumidor Red/Verde e não carrega Blue', () => {
  assert.doesNotMatch(dashboard, /Blue|Causal|writeMap|writeCurve|startKWrite|startKBatchWrite|startKFactorWrite/);
  assert.doesNotMatch(dashboard, /\?\.|\?\?|replaceAll\(/);
  assert.match(dashboard, /const obd = state\.obd \|\| \{\}/);
  assert.match(dashboard, /styles-dashboard-now\.css/);
});
```

- [ ] **Step 2: Confirmar por inspeção que o teste detecta a lacuna**

A base não possui `styles-dashboard-now.css`, `multimedia-now-screen` nem `now-dashboard-shell`. O teste deve falhar nesses marcadores antes da alteração funcional.

- [ ] **Step 3: Incluir o teste no gate rápido**

Adicionar a `tools/run_checks.py`:

```python
["node", "--test", "tests/ui/verde-dashboard-now.test.cjs"],
```

- [ ] **Step 4: Atualizar somente o contrato antigo do dashboard**

Em `test_dashboard_prioritizes_petrol_injection_and_groups_context`, substituir expectativas da tela antiga por:

```python
self.assertIn('PETROL INJECTION', self.dashboard)
self.assertIn('dashHeroPetrol', self.dashboard)
self.assertIn('now-dashboard-shell', self.dashboard)
for marker in ('dashRpm', 'dashMap', 'dashFuel', 'dashStft', 'dashCell'):
    self.assertIn(marker, self.dashboard)
self.assertNotIn('dashHeroRpm', self.dashboard)
self.assertNotIn('dashGas', self.dashboard)
```

Os asserts gerais de arquitetura, Mapa K, Curva K e writer permanecem intactos.

Normalizar apenas escapes `\\xNN` e `\\uNNNN` ao carregar `dashboard.js` em `test_block1_session_contract.py`, preservando as frases verificadas:

```python
def normalize_js_source(text: str) -> str:
    text = re.sub(
        r"\\\\x([0-9A-Fa-f]{2})",
        lambda match: chr(int(match.group(1), 16)),
        text,
    )
    return re.sub(
        r"\\\\u([0-9A-Fa-f]{4})",
        lambda match: chr(int(match.group(1), 16)),
        text,
    )
```

- [ ] **Step 5: Commit remoto e readback**

Mensagem:

```text
test(verde): lock isolated Blue Agora visual contract (#45)
```

Readback obrigatório: os dois testes modificados e `tools/run_checks.py`.

- [ ] **Step 6: Criar gate RED provisório e observar a falha**

Criar `.github/workflows/verde-fast-contracts.yml` inicialmente com trigger somente para o próprio arquivo e comando direto:

```yaml
name: OmegasVerde fast contracts
on:
  push:
    branches: [OmegasVerde]
    paths: [".github/workflows/verde-fast-contracts.yml"]
permissions:
  contents: read
concurrency:
  group: omegas-verde-fast-contracts
  cancel-in-progress: true
jobs:
  contracts:
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps:
      - uses: actions/checkout@v5
      - run: node --test tests/ui/verde-dashboard-now.test.cjs
```

A criação dispara um único run. Aguardar `completed/failure` e confirmar que a falha decorre da ausência de `styles-dashboard-now.css` ou `multimedia-now-screen`. Nenhum código de produção é criado antes dessa prova.

---

### Task 2: Isolar os estilos da tela Agora

**Files:**
- Create: `app/src/main/assets/ui/styles-dashboard-now.css`

**Interfaces:**
- Consumes: seletores de dashboard do blob Blue `f2245e80a1b9a7dad0ffee199c4d3c64ba141e10`.
- Produces: folha visual carregada exclusivamente por `DashboardScreen.ensureStyles()`.

- [ ] **Step 1: Extrair apenas seletores de rota dashboard e `now-*`**

A folha começa com:

```css
/* OMEGAS Verde · recorte visual da tela Agora Blue */

body[data-omegas-route="dashboard"] .workspace-head {
  display: none;
}

body[data-omegas-route="dashboard"] .screen-host {
  padding: 14px 18px 16px;
  overflow: hidden;
}

.multimedia-now-screen {
  height: 100%;
  min-height: 0;
  color: #f6fbff;
}
```

Portar integralmente os blocos:

- `.now-page-intro`;
- `.now-dashboard-shell`;
- `.now-hero-card`, `.now-hero-copy`, `.now-hero-value`, `.now-hero-visual`;
- `.now-metric-grid`, `.now-metric-card`, `.now-stft-card`;
- `.now-session-card`, `.now-session-copy`, `.now-session-facts`;
- media query até 650 px apenas com regras `now-*`.

- [ ] **Step 2: Excluir explicitamente estilos alheios**

A folha não contém:

```text
witness-
multimedia-obd
map-screen
curve-screen
learning-screen
```

- [ ] **Step 3: Preservar orçamento visual**

Manter ausência de animações e backdrop filter. Gradientes estáticos ficam confinados ao dashboard aprovado.

- [ ] **Step 4: Commit remoto e readback**

Mensagem:

```text
style(verde): isolate Blue Agora multimedia layout (#45)
```

Readback obrigatório: blob criado e busca negativa dos seletores proibidos.

---

### Task 3: Transplantar o dashboard sem contratos Blue

**Files:**
- Modify: `app/src/main/assets/ui/screens/dashboard.js`

**Interfaces:**
- Consumes: `state.telemetry`, `state.status`, `state.obd` e `telemetry.interpolation.cell`.
- Produces: `OmegasUi.DashboardScreen` com o mesmo construtor e método `render(state)` usados por `app.js`.

- [ ] **Step 1: Portar a estrutura atual do blob Blue**

Usar o conteúdo do blob `fa36673d948f73133afd7a112ccd897803a885c6`, mantendo:

```javascript
function ensureStyles() {
  if (document.querySelector('link[data-dashboard-now]')) return;
  const link = document.createElement('link');
  link.rel = 'stylesheet';
  link.href = 'styles-dashboard-now.css';
  link.dataset.dashboardNow = 'true';
  document.head.appendChild(link);
}
```

O construtor mantém:

```javascript
constructor() {
  ensureStyles();
  this.root = document.querySelector('[data-screen="dashboard"]');
  this.lastHealthSignature = '';
  this.installLayout();
}
```

- [ ] **Step 2: Preservar o template visual aprovado**

O template contém um único hero de Petrol Injection, cinco cards e uma faixa de sessão. IDs públicos:

```text
dashHeroStatus
dashHeroPetrol
dashRpm
dashMap
dashFuel
dashStft
dashStftState
dashCell
dashHealth
dashEcuStatus
dashObdStatus
dashAge
```

- [ ] **Step 3: Manter o adaptador de dados defensivo**

```javascript
const data = live(state);
const status = state.status || {};
const obd = state.obd || {};
const interpolation = (state.telemetry && state.telemetry.interpolation) || {};
```

A função `fuelLabel` apenas normaliza nomes explícitos. `DESCONHECIDO` permanece visível até #40 resolver a origem; a UI não inventa o combustível.

- [ ] **Step 4: Preservar estados de saúde**

Limites existentes:

```javascript
const stale = connected && age !== null && age > 2500;
const expired = connected && age !== null && age > 8000;
const stuck = status.engineStuck === true;
```

Ordem: desconectado → travado → expirado → atrasado → leitura normal.

- [ ] **Step 5: Verificar ausência de escrita e Blue**

Busca negativa no dashboard para:

```text
Blue
Causal
writeMap
writeCurve
startKWrite
startKBatchWrite
startKFactorWrite
```

- [ ] **Step 6: Commit remoto e readback**

Mensagem:

```text
feat(verde): transplant Blue Agora presentation only (#45)
```

Readback obrigatório: novo blob, IDs públicos e ausência dos tokens proibidos.

---

### Task 4: Criar e executar o gate remoto econômico

**Files:**
- Modify: `.github/workflows/verde-fast-contracts.yml`

**Interfaces:**
- Consumes: branch `OmegasVerde`, testes Python/Node e `tools/run_checks.py`.
- Produces: run remoto sem Gradle, assemble ou APK.

- [ ] **Step 1: Promover o workflow provisório a gate rápido definitivo**

```yaml
name: OmegasVerde fast contracts

on:
  push:
    branches:
      - OmegasVerde
    paths:
      - ".github/workflows/verde-fast-contracts.yml"
      - "app/src/main/assets/ui/**"
      - "tests/**"
      - "tools/run_checks.py"
      - "docs/superpowers/**"
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: omegas-verde-fast-contracts
  cancel-in-progress: true

jobs:
  contracts:
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps:
      - uses: actions/checkout@v5
      - name: Prove source identity
        run: |
          set -euo pipefail
          test "$(git rev-parse HEAD)" = "$GITHUB_SHA"
          echo "SOURCE_SHA=$GITHUB_SHA"
          echo "SOURCE_TREE=$(git rev-parse HEAD^{tree})"
      - name: Run fast contracts
        run: |
          set -euo pipefail
          python3 -B tools/run_checks.py
```

- [ ] **Step 2: Confirmar o disparo remoto**

A atualização final do workflow é a última mutação do conjunto e dispara um único run verde. Os commits intermediários não disparam Actions porque o gate provisório observava apenas o próprio arquivo.

- [ ] **Step 3: Polling remoto**

Repetir até conclusão:

```text
EXECUTE → SLEEP → CHECK REMOTE → EVALUATE
```

Estado aceito: `completed/success`. Estado `queued` ou `in_progress` não é conclusão.

- [ ] **Step 4: Inspecionar o diff integrado**

Comparar o SHA final contra `d4b544758d6508a92c8d73e0e99eae406f446eb2`, último commit antes da correção textual deste plano. O diff permitido contém a correção do plano e somente estes arquivos de execução: dashboard, CSS isolado, contrato Node, dois contratos Python, `tools/run_checks.py`, workflow rápido e correções do próprio plano.

- [ ] **Step 5: Atualizar #45 e a epic**

Registrar SHA, tree, run, blobs, resultado dos testes e limitação: inspeção física 1280×720 ainda não realizada.

## Self-review result

- Spec coverage: tela Agora, isolamento Blue, WebView legado, Store Red/Verde, STFT opcional, saúde e CI econômico cobertos.
- Placeholder scan: nenhuma instrução vaga ou decisão em aberto.
- Type consistency: `DashboardScreen`, `render(state)`, IDs DOM, caminho CSS e normalização de escapes são consistentes entre tarefas.
- Safety: nenhum arquivo Kotlin, engine, writer, Mapa K ou Curva K entra no conjunto.
