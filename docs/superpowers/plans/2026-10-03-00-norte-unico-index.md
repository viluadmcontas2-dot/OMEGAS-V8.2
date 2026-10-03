# Norte Único — Índice dos planos + Fatia 0 (docs e laço de teste remoto)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Levar a `OmegasPlatina` ao Norte Único (spec abaixo) em 9 fatias, cada uma um PR verde no GitHub.

**Architecture:** Este arquivo fixa o que todas as fatias compartilham: o laço de teste remoto, a ordem, e o **contrato de nomes entre fatias** (tipos, pacotes, rotas, intents). Cada fatia tem plano próprio; nenhum plano de fatia redefine um nome daqui.

**Tech Stack:** Kotlin (Android, JVM 17, JUnit), WebView + JS vanilla (testes `node --test`), contratos Python, GitHub Actions, `gh` CLI.

**Spec:** `docs/superpowers/specs/2026-10-03-omegas-platina-norte-unico-design.md`

## Global Constraints

- Base de toda branch: `OmegasPlatina`. Nome: `work/platina-f<N>-<slug>`. Um PR por fatia, alvo `OmegasPlatina`; merge só com `OMEGAS PLATINA CI` verde no SHA do PR.
- **Nenhum teste, build ou lint roda na sessão de execução.** Todo teste roda no GitHub Actions pelo laço da Task 0.1. O executor edita, commita, empurra e lê o resultado.
- Comandos de leitura/escrita da ECU não mudam: `UsbSerialManager`, `ResponseDrivenEcuEngine`, `AutoCalProtocol`, `KFactorManager`, `KWriteManager`, `AutoCalNativeActionManager` (exceto remover `MANUAL_AUTOMATCH` na F2).
- `applicationId` permanece `com.omegas.v7.test`.
- Todo botão é um toque: sem diálogo de confirmação, sem segurar, sem repetir. Proteção = foto antes + `Desfazer`.
- Observar é automático; mudar a ECU só por intent pedido pelo dono.
- O fim de toda mutação é o readback; "Gravado" só depois dele.
- Nenhuma exceção derruba o app: vira estado `FALHOU` com motivo humano.
- Texto de UI em português; o nível técnico se chama **"Detalhes técnicos"** em toda tela.
- Viewport canônico 1280×720; alvo de toque ≥ 76 px; texto crítico ≥ 24 px.
- Cores só via `app/src/main/assets/ui/tokens.css` (a partir da F8; antes, não introduzir cor nova).
- Cada PR descreve: o que mudou · classe de prova (1 contrato · 2 sintético · 3 replay real · 4 APK no emulador · 5 físico) · o que ficou não provado.

## Review Focus

1. **Sessão gravada por APK antigo aberta pelo app novo** (formatos `omegas-equivalence-ledger-v1`, `omegas-refinement-autopilot-v1`, eventos com `sample_state`/`sample{}`): o app abre, lê o que conhece e ignora o resto. Dono: F1 Task "EquivalencePhases" e F3 Task "limpeza de órfãos".
2. **Cabo USB cai no meio de `CURVE_WRITE`**: a operação termina em `FALHOU` com `FailureKind.TRANSPORTE`, a foto continua disponível, `Desfazer` funciona quando o cabo volta. Dono: F5 Task "fila".
3. **ECU reaprende gasolina depois do congelamento** (aquisição muda sem o dono pedir): a Referência não muda, `ecuDrift` aparece, nada é apagado. Dono: F4 Task "ReferenceStore".
4. **App recém-instalado com ECU já madura e sem nenhuma telemetria de gasolina**: Curva Própria = Referência (prior puro), estados `APRENDENDO`, próxima ação "Congelar". Dono: F4 Task "EquivalenceEngine".
5. **`portmon-*.js` são ferramenta de teste, não código morto**: `tests/ui/portmon-*.test.cjs` os carregam. Movê-los para `tools/portmon/` e atualizar os `require`, nunca apagar. Dono: F3 Task "órfãos JS/CSS".

---

## Ordem e arquivos de plano

| # | Plano | Depende de |
|---|---|---|
| 0 | este arquivo (Tasks 0.1–0.3) | — |
| 1 | `2026-10-03-f1-extracoes.md` | 0 |
| 2 | `2026-10-03-f2-poda-1.md` | 1 |
| 3 | `2026-10-03-f3-poda-2.md` | 2 |
| 4 | `2026-10-03-f4-cerebro-unico.md` | 3 |
| 5 | `2026-10-03-f5-autoridade-unica.md` | 4 |
| 6 | `2026-10-03-f6-ui-agora-curva-refino.md` | 5 |
| 7 | `2026-10-03-f7-ui-autocal-mapa-sessoes-ferramentas.md` | 6 |
| 8 | `2026-10-03-f8-acabamento.md` | 7 |

## Contrato de nomes entre fatias

Qualquer plano que use estes nomes usa **exatamente** estes. Quem cria está na coluna "Nasce em".

### Laço de teste remoto (F0)

| Nome | Forma |
|---|---|
| `.github/workflows/task-check.yml` | `workflow_dispatch` em qualquer ref; inputs `kind` ∈ `gradle\|node\|python\|checks`, `target` (string) |
| `remote-test.sh android <cenario>` | não usa `task-check.yml`: dispara `verde-android-render-evidence.yml` com `source_sha` = HEAD (ver R6) |
| `tools/ci/remote-test.sh` | `remote-test.sh <kind> <target>` → dispara, espera, imprime `REMOTE_TEST=PASS` ou `REMOTE_TEST=FAIL` + log das falhas; exit 0/1 |

`kind=gradle` → `./gradlew testDebugUnitTest --tests "<target>"`; `kind=node` → `node --test <target>`; `kind=python` → `python3 -B <target>`; `kind=checks` → `python3 -B tools/run_checks.py` + `./gradlew testDebugUnitTest lintDebug` (o `target` é ignorado). `kind=android` → ver R6.

### Extrações (F1)

| Nome | Pacote / arquivo | Forma |
|---|---|---|
| `LiveCellProjection` | `com.omegas.prohub.calibration` | `object`; `fun liveInterpolationJson(...)` e `fun cellFor(...)` com **as mesmas assinaturas e saída** de `LearningGridProjection` hoje; `val rpmBins`, `val petrolBins` |
| `MotorSampleAnalyzer`, `SampleDecision` | movidos para `com.omegas.prohub.ecu` | mesmo nome e API |
| `EquivalencePhases` | `com.omegas.prohub.autocal` (renomeia `RefinementAutopilot`) | mesma API (`observe`, `takeAlert`, `json`), `FORMAT` e nome de arquivo inalterados |
| `CalibrationOperationsBridge` | `com.omegas.prohub.web`, JS name `OmegasCalibration` | os 9 endpoints de curva/mapa hoje em `V7JavascriptBridge` (`startCurveRead`, `startCurveBackup`, `listCurveBackups`, `startCurveRestorePrepare`, `startCurveReset`, `startCurveBatchWrite`, `startMapBatchWrite`, `previewMapAdjustment`, `getLastOperation`), mesmas assinaturas, sem `v7Reconcile…` |

### Cérebro (F4) — pacote `com.omegas.prohub.equivalence`

```kotlin
enum class Fuel { GASOLINA, GNV }
data class RefPoint(val mapBar: Double, val petrolMs: Double, val maturity: Int)
data class Reference(val id: String, val frozenAt: Long, val ecuAcquisitionFingerprint: String, val points: List<RefPoint>)
enum class CellSource { REFERENCE, BLENDED, OWN }
data class OwnCell(val mapBar: Double, val petrolMs: Double?, val samples: Int, val dispersion: Double, val source: CellSource, val divergence: Double?)
class OwnCurve(val fuel: Fuel, val cells: List<OwnCell>) { fun at(mapBar: Double): Double? }
enum class PointState { SEM_DADOS, APRENDENDO, MEDIDO, EQUIVALENTE, POBRE, RICO, EM_PROVA, CONFIRMADO, CONTESTADO, INCONCLUSIVO }
data class EquivalencePoint(
  val index: Int, val axisMs: Double, val kCurrent: Double, val kTarget: Double?,
  val mixture: Double?,          // kTarget/kCurrent - 1 (fração; + = pobre)
  val tolerance: Double,         // EquivalenceTolerances.tolerance(dispersion)
  val roughnessRatio: Double?,   // tremor GNV / tremor gasolina no mesmo MAP equivalente
  val nearStallRatio: Double?,   // quase-apagões/h GNV / gasolina
  val slope: Double?,            // |Δ ln K / Δ ln t| com o vizinho
  val usage: Double,             // fração do tempo de condução
  val samples: Int, val sources: Set<String>, val state: PointState)
enum class NextActionKind { OPERATION, FREEZE_REFERENCE, CONTESTED, APPLY, PROVING, COLLECT, NOTHING }
data class NextAction(val kind: NextActionKind, val text: String, val route: String?, val subpage: String?, val pointIndexes: List<Int>)
data class EquivalenceResult(val points: List<EquivalencePoint>, val index: Double?, val coverage: Int,
  val provisional: Boolean, val nextAction: NextAction, val proposal: AutoMatchRefinedEngine.Result?,
  val ownPetrol: OwnCurve, val ownGas: OwnCurve)
object EquivalenceTolerances { const val MIN = 0.04; const val LIGHT = 0.08; const val DIVERGENCE_ALARM = 0.08
  const val WORSE_DELTA = 0.04; const val WORSE_ERROR = 0.05; const val CELL_BAR = 0.02; const val USAGE_SESSIONS = 10
  fun tolerance(dispersion: Double): Double }   // max(MIN, 2 * dispersion)
```

Classes: `ReferenceStore`, `OwnCurveFitter`, `ExperienceMeter`, `UsageMeter`, `EquivalenceEngine`. Assinaturas exatas no plano F4.

### Autoridade (F5) — pacote `com.omegas.prohub.state`

```kotlin
enum class Intent { CURVE_WRITE, CURVE_RESET, CURVE_RESTORE, CURVE_BACKUP_SAVE, MAP_WRITE, REFERENCE_FREEZE, AUTOCAL_RELEARN,
  AUTOCAL_PAUSE, AUTOCAL_RESUME, AUTOCAL_RESET_GAS, AUTOCAL_RESET_PETROL, SESSION_EXPORT, OVERLAY_TOGGLE, SETTINGS_SET, UNDO }
enum class OpStage { RECEBIDO, PREPARANDO, EXECUTANDO, CONFERINDO, CONCLUIDO, FALHOU }
enum class FailureKind { TRANSPORTE, ECU, APP }
```

Classes: `StateStore` (seções JSON com `revision: Long` monotônica), `OperationQueue`, `Snapshotter` (fotos para `UNDO`), `OmegasBridge` (JS name **`Omegas`**, métodos `snapshot(sinceRevision: Long): String`, `request(intentJson: String): String`). Evento para a página: `window.OmegasOnRevision(revision)`. JS: `app/src/main/assets/ui/core/omegas.js` exporta `Omegas.snapshot`, `Omegas.request`, `Omegas.onRevision(cb)`.

Seções do snapshot (chaves exatas): `connection, live, now, reference, points, index, nextAction, operation, session, autocal, settings`.

### UI (F6–F8)

| Nome | Valor |
|---|---|
| `ROUTES` (`core/router.js`) | `['dashboard','map','curve','autocal','refino','sessions','tools']` |
| Rótulos do trilho | `01 Agora · 02 Mapa K · 03 Curva K · 04 AutoCal · 05 Refino · 06 Sessões · 07 Ferramentas` |
| Subpáginas | `curve: equivalencia, editar, backups` · `autocal: aquisicao, referencia, epocas` · `refino: fases, pontos` · `sessions: evolucao, lista` |
| `components/subpage-tabs.js` | `SubpageTabs.mount(host, { route, items: [{id,label}], onChange(id) })`; lembra a última em `store` chave `subpage.<route>` |
| `components/operation-overlay.js` | `OperationOverlay.open(receiptId)`; renderiza `OpStage` em português; botões `Desfazer` / `Voltar` |
| `tokens.css` | nasce na F8 com os valores de spec §3.2 |

---

## Reconciliação entre planos (prevalece sobre os planos de fatia)

Os planos de fatia foram escritos em paralelo; onde assumiram formatos diferentes, vale o que está aqui. Cada plano de fatia aponta para esta seção no cabeçalho.

**R1. Intents (enum `Intent`, lista final).** Os 14 do contrato **+ `CURVE_BACKUP_SAVE`** (verde: lê a curva e salva arquivo; não muta a ECU; não tem Desfazer). Payloads:

| Intent | Payload |
|---|---|
| `CURVE_WRITE` | `{points:[{index, currentRaw, targetRaw}], reason}` |
| `CURVE_RESET` | `{}` |
| `CURVE_RESTORE` | `{backupId}` |
| `CURVE_BACKUP_SAVE` | `{label}` |
| `MAP_WRITE` | `{cells:[{rpmIndex, mapIndex, currentRaw, targetRaw}], reason}` |
| `REFERENCE_FREEZE` | `{}` |
| `AUTOCAL_RELEARN` | `{points:[{fuel, band}]}` — **não é desfazível** (`undoable:false`): não existe comando provado que devolva pontos apagados à ECU; a UI diz "a ECU vai reaprender estes pontos" |
| `AUTOCAL_PAUSE` / `AUTOCAL_RESUME` / `AUTOCAL_RESET_GAS` / `AUTOCAL_RESET_PETROL` | `{}` (reset: Desfazer devolve só a Curva K da foto) |
| `SESSION_EXPORT` | `{scope: "session"\|"logs"\|"full", sessionId?}` |
| `OVERLAY_TOGGLE` | `{enabled}` |
| `SETTINGS_SET` | `{key, value?}` com `key` ∈ `recorder.retention`, `overlay.scale`, `gps.enabled`, `lan.enabled`, `power.batteryExemption` (abre o pedido do Android), `overlay.permission` (abre o pedido do Android), `diagnostics.selfTest` (roda o autoteste) |
| `UNDO` | `{receiptId?}`; ausente = a última operação desfazível desta vida do serviço. O id da foto **é** o `receiptId` da operação que a tirou. A foto vive enquanto o serviço vive (não acaba com a sessão gravada: cabo caído não pode matar o Desfazer). |

`REFERENCE_FREEZE` desfaz por `ReferenceStore.restorePrevious()` (F4). F5 **não** cria `restore()`.

**R2. Seções do snapshot (forma exata; F5 publica, F6–F8 leem).**

```
connection = { state: "SEM_CABO"|"CONECTANDO"|"CONECTADO", safety, release, selfTest,
               ecu: { curve, map: { grid[12][12], rpmAxis[], mapAxis[], state, readAtMs }, curveBackups: [] } }
live       = { valid, ageMs, petrolMs, gasMs, mapBar, rpm, fuel }
now        = { pointIndex, axisMs, state, mixture, fresh, fuel, rpm, mapBar, petrolMs, gasMs }
reference  = EquivalenceJson.reference (F4) + { frozen, ecuCurrent: [{mapBar, petrolMs}], ecuChangedSinceFreeze,
               maturePoints, canFreeze, provisional, ownPetrol, ownGas }
points     = EquivalenceJson.points (F4), 30 itens
index      = { value: Double|null, coverage, provisional, trend: Double|null }
nextAction = EquivalenceJson.nextAction (F4) + { proposal: { mode, telemetryOnly, currentRaw[30], refinedRaw[30], origins[] } | null }
operation  = { current: Receipt|null, last: Receipt|null, history: Receipt[≤20] }
             Receipt = { receiptId, intent, stage, text, failureKind|null, startedAtMs, finishedAtMs|null, undoable, details }
session    = { status, history: [ver F7 "Contrato de consumo"], logs: [] }
autocal    = { enabled, projection, phases, equivalenceView, refined }
settings   = { retention, overlay, power, gps, lan, recorder, battery, diagnostics: { logs: [], selfTest } }
```

Onde um plano de fatia usar outro caminho (ex.: F7 `now.mapK`, F6 `session.curveBackups`, F7 `operation.receiptId`), troque pelo de cima: `connection.ecu.map`, `connection.ecu.curveBackups`, `operation.current.receiptId`.

**R3. JS.** Acesso único: `OmegasUi.Omegas` (F5) com `available, snapshot, request(intent, payload), onRevision, receipt`. `OmegasUi.omegas()` (F6) só devolve esse objeto. `IntentAction.send(intent, payload, button)` (F7) chama `request`. O teste de fechamento (F8) reconhece um intent usado na UI por qualquer uma das formas `request('X'`, `send('X'`, `intent: 'X'`.

**R4. Abas e subpáginas.** Botões de subpágina carregam `data-subpage="<id>"`; a tablist é `SubpageTabs` (F6) com `hint` opcional.

**R5. `setInterval`.** F6 permite só em `core/scheduler.js`. **F7 remove essa exceção** (faixa de status, Mapa K e AutoCal passam a redesenhar por revisão) e o teste de F6 passa a exigir zero. F8 confirma zero.

**R6. Testes de render no emulador** fazem parte do laço: `tools/ci/remote-test.sh android <cenario|"">` dispara `verde-android-render-evidence.yml` com `-f source_sha=$(git rev-parse HEAD)` na branch e espera do mesmo jeito. F1, F3, F6, F7 e F8 usam este comando onde hoje escrevem `gh workflow run verde-android-render-evidence.yml`.

**R7. Mapa K.** As 144 células e cabeçalhos da grade têm alvo ≥ 44 px (não cabem 76 px em 720 px); todo o resto ≥ 76 px. É a única exceção.

**R8. Modo dirigindo não existe** (spec §3.1). Onde um plano o mencionar, ignore.

**R9. Linhas citadas nos planos** são da `OmegasPlatina` de 2026-10-03, antes da F1. Sempre que um plano dá uma âncora de texto, a âncora vale mais que o número.

**R10. Work Units: nome `NORTE-WU-0<N>` e binding versionado** (decisão do dono, 2026-10-03). O repo já usa `docs/workunits/` (ex.: `OMEGAS-WU-006`), então "WU-0<N>" sozinho colide. Onde um plano disser `WU-0<N>`, leia `NORTE-WU-0<N>`:
- título da Issue começa com `NORTE-WU-0<N> · `; label `wu` continua (Work Unit);
- cada fatia tem binding `docs/workunits/NORTE-WU-0<N>.md` (épico, Issue, plano, branch, PR, estado); a Issue é a checklist autoritativa, o arquivo é a ligação versionada;
- commits levam no corpo `NORTE-WU-0<N> · Tarefa <N>.<M>`;
- o portão (Task 0.0) exige, além das regras do índice, que o binding exista na head do PR e cite `#<issue>` e a branch;
- a Task 0.3 **não** arquiva `docs/workunits/` inteira: move só `OMEGAS-WU-006.md` e `PLATINA-REFINO.md` para `docs/archive/workunits/`; a pasta continua viva com as `NORTE-WU-*`.

Issues: épico #131; NORTE-WU-00…08 = #132…#140.

**R11. Legado apagado, não arquivado** (decisão do dono, 2026-10-03). Onde a spec §5 e a Task 0.3 dizem "arquivar", vale **apagar** (o histórico git guarda tudo):
- saem de vez: `LEARNING_RULES.md`, `docs/BLOCK_*.md`, `docs/decisions/`, `docs/spec-kits/`, `docs/handoff/`, `docs/incidents/` (exceto o abaixo), `docs/MODELO_EQUIVALENCIA_CAUSAL.md`, `docs/OBD_*.md`, `docs/PENTE_FINO_VERDE.md`, `docs/MIGRATION.md`, as specs e planos anteriores ao Norte Único e as Work Units antigas (`OMEGAS-WU-006`, `PLATINA-REFINO`);
- `docs/archive/` guarda só o que um teste ainda lê como prova de protocolo: `STATUS-ate-2026-10-03.md` e `incidents/2026-09-19-autocal-final-byte-matrix.md`; a fatia que apagar o teste apaga o arquivo;
- Issues e PRs legados (anteriores ao épico #131) são fechados como "não planejado", com a lista registrada no épico;
- a Fatia 0 roda na branch nova `work/platina-f0-norte` (PR novo); a `work/platina-f0-docs-laco` e o PR #130 ficam substituídos.

## Questões abertas para o dono (não bloqueiam F0–F3)

1. **A Referência da ECU contra a gasolina medida.** Na F4, o planejador mediu por cima, nas sessões reais, a curva da ECU 10–16% fora da gasolina lida pela telemetria na mesma sessão. O STATUS antigo dizia "mediana 1,000, 80% dentro de 2,3%". Pode ser diferença de método (célula vs. banda). A Task de validação cruzada da F4 vai medir de verdade; se a diferença se confirmar, é mais um motivo para a Curva Própria existir, e o resultado vai para o STATUS.
2. **A sessão `ref_2026-10-01_1719` piora depois da gravação das 20:31:57** (índice ~0,31 → ~0,18 na conta rápida). O teste da F4 afirma essa piora em vez de esconder; se você lembrar o que foi gravado ali, ajuda a interpretar.

---

## Fatia 0: docs do zero + laço de teste remoto

Branch: `work/platina-f0-norte` (R11).

**Rastreabilidade (vale para todas as fatias):** Épico `Norte Único — GNV equivalente à gasolina` (label `epico`) + uma Issue por fatia com label `wu` e título `WU-0<N> · <nome humano>` (lista no prompt de execução e em `docs/superpowers/plans/README-issues.md`, criado nesta task). Cada Issue lista as Tasks do plano como caixas. Todo commit leva no corpo `WU-0<N> · Tarefa <N>.<M>`. Todo PR leva `Fecha #<n>`.

### Task 0.0: Portão de Issues

**Files:**
- Create: `.github/workflows/issue-gate.yml`
- Create: `tools/ci/issue_gate.py`
- Create: `docs/superpowers/plans/README-issues.md` (tabela WU → título → plano → branch)
- Test: `tests/test_issue_gate.py`

**Interfaces:**
- Produces: check obrigatório `Issue gate` em todo PR para `OmegasPlatina`. `issue_gate.py check(pr_body: str, branch: str, issue: dict, commits: list[dict]) -> list[str]` (lista de violações; vazia = passa). O workflow monta `issue` via `gh issue view <n> --json state,labels,title,body` e `commits` via `gh pr view --json commits`.

- [ ] **Step 1: Write the failing test** `tests/test_issue_gate.py`

```python
import sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "tools/ci"))
from issue_gate import check
ok_issue = {"state": "OPEN", "labels": [{"name": "wu"}], "title": "WU-02 · Tirar o Predictor, o AutoMatch manual e o cérebro V7",
            "body": "## Tarefas\n- [x] 2.1 a\n- [x] 2.2 b\n\n## Não provado (preencher ao fechar)\nBalão sobre outros apps."}
commit = [{"messageHeadline": "feat: x", "messageBody": "WU-02 · Tarefa 2.1"}]
assert check("Fecha #12", "work/platina-f2-poda-1", ok_issue, commit) == []
assert "sem 'Fecha #n'" in " ".join(check("corpo", "work/platina-f2-poda-1", ok_issue, commit))
assert "label wu" in " ".join(check("Fecha #12", "work/platina-f2-poda-1", {**ok_issue, "labels": []}, commit))
assert "fechada" in " ".join(check("Fecha #12", "work/platina-f2-poda-1", {**ok_issue, "state": "CLOSED"}, commit))
assert "WU-02" in " ".join(check("Fecha #12", "work/platina-f3-poda-2", ok_issue, commit))
assert "2.2" in " ".join(check("Fecha #12", "work/platina-f2-poda-1", {**ok_issue, "body": ok_issue["body"].replace("[x] 2.2", "[ ] 2.2")}, commit))
assert "Não provado" in " ".join(check("Fecha #12", "work/platina-f2-poda-1", {**ok_issue, "body": "## Tarefas\n- [x] 2.1 a\n\n## Não provado (preencher ao fechar)\n"}, commit))
assert "Tarefa" in " ".join(check("Fecha #12", "work/platina-f2-poda-1", ok_issue, [{"messageHeadline": "fix", "messageBody": ""}]))
assert check("Fecha #12", "work/platina-f2-poda-1", ok_issue, commit + [{"messageHeadline": "Merge branch 'OmegasPlatina'", "messageBody": ""}]) == []
print("ISSUE_GATE_CONTRACT=PASS")
```

- [ ] **Step 2: Push and see it fail in the existing CI**

Run: `git push -u origin work/platina-f0-docs-laco && gh pr checks --watch`
Expected: `OMEGAS PLATINA CI` FAIL at "Fast contracts" with `ModuleNotFoundError: issue_gate`.

- [ ] **Step 3: Implement `tools/ci/issue_gate.py` and `issue-gate.yml`**

Mensagens de violação em português, uma por regra (os trechos que o teste procura: `sem 'Fecha #n'`, `label wu`, `fechada`, o prefixo `WU-0<N>` esperado pela branch, o número da Task desmarcada, `Não provado`, `Tarefa`). Workflow: `on: pull_request` (branches `OmegasPlatina`, types `opened, edited, synchronize, reopened`) e `issues` (types `edited`) para reavaliar quando uma caixa é marcada; job com nome exato `Issue gate`; imprime cada violação e sai com 1 se houver alguma. `README-issues.md`: a tabela de WUs do prompt de execução.

- [ ] **Step 4: Push and verify**

Run: `git push && gh pr checks --watch`
Expected: `OMEGAS PLATINA CI` PASS (log `ISSUE_GATE_CONTRACT=PASS`); `Issue gate` FAIL no próprio PR da F0 enquanto a WU-00 tiver caixas abertas (é o comportamento certo).

- [ ] **Step 5: Proteger a branch e commit**

Run: `gh api -X PUT repos/viluadmcontas2-dot/OMEGAS-V8.2/branches/OmegasPlatina/protection -f "required_status_checks[strict]=true" -f "required_status_checks[contexts][]=build_and_test" -f "required_status_checks[contexts][]=Issue gate" -F enforce_admins=true -F "required_pull_request_reviews=null" -F "restrictions=null"`
Expected: HTTP 200. Sem permissão de admin: parar e avisar o dono (sem proteção o portão é só aviso).
Commit: `ci: portão de Issues (WU obrigatória em todo PR)` com `WU-00 · Tarefa 0.0` no corpo.

### Task 0.1: Laço de teste remoto

**Files:**
- Create: `.github/workflows/task-check.yml`
- Create: `tools/ci/remote-test.sh`
- Test: `tests/test_task_check_workflow.py`

**Interfaces:**
- Produces: `tools/ci/remote-test.sh <kind> <target>` (contrato acima). Todas as fatias usam isto como "Run".

- [ ] **Step 1: Write the failing test** `tests/test_task_check_workflow.py`

```python
import re, pathlib
root = pathlib.Path(__file__).resolve().parents[1]
wf = (root / ".github/workflows/task-check.yml").read_text(encoding="utf-8")
sh = (root / "tools/ci/remote-test.sh").read_text(encoding="utf-8")
assert "workflow_dispatch" in wf and "push:" not in wf
for k in ("gradle", "node", "python", "checks"): assert k in wf, k
assert "verde-android-render-evidence.yml" in sh and "source_sha" in sh  # kind=android (R6)
assert '--tests "${{ inputs.target }}"' in wf
assert "gh workflow run task-check.yml" in sh and "gh run watch" in sh and "--exit-status" in sh
assert "REMOTE_TEST=PASS" in sh and "REMOTE_TEST=FAIL" in sh
assert "--log-failed" in sh
print("TASK_CHECK_CONTRACT=PASS")
```

- [ ] **Step 2: Push and run it through the existing CI to see it fail**

Run: `git push -u origin work/platina-f0-docs-laco && gh pr create --draft --base OmegasPlatina --title "F0: docs + laço remoto" --body "WIP" && gh pr checks --watch`
Expected: `OMEGAS PLATINA CI` FAIL at "Fast contracts" with `FileNotFoundError` for `task-check.yml`.

- [ ] **Step 3: Create `task-check.yml` and `remote-test.sh`**

Workflow: `ubuntu-latest`, `actions/checkout@v4`, `actions/setup-java@v4` (17, temurin, cache gradle), `actions/setup-node@v4` (20), one `run` step with a `case "${{ inputs.kind }}"` per the contract. Script: `set -euo pipefail`; branch = `git rev-parse --abbrev-ref HEAD`; `git push` first; `gh workflow run task-check.yml --ref "$branch" -f kind="$1" -f target="${2:-}"`; poll `gh run list --workflow task-check.yml --branch "$branch" -L1 --json databaseId,headSha` until the run's `headSha` equals `git rev-parse HEAD`; `gh run watch <id> --exit-status`; on failure `gh run view <id> --log-failed | tail -200`. Kind `android`: same flow but `gh workflow run verde-android-render-evidence.yml --ref "$branch" -f source_sha="$(git rev-parse HEAD)"` and poll that workflow.

- [ ] **Step 4: Push and verify**

Run: `git push && gh pr checks --watch`
Expected: `OMEGAS PLATINA CI` PASS; log contains `TASK_CHECK_CONTRACT=PASS`.
Then: `tools/ci/remote-test.sh python tests/test_task_check_workflow.py`
Expected: `REMOTE_TEST=PASS`.

- [ ] **Step 5: Commit** (já empurrado nos passos acima; commits separados para teste e implementação)

### Task 0.2: `AGENTS.md`, `PROJECT.md`, `STATUS.md`, `docs/ARCHITECTURE.md` do zero

**Files:**
- Modify (reescrever): `AGENTS.md`, `PROJECT.md`, `STATUS.md`, `docs/ARCHITECTURE.md`, `docs/TEST_STRATEGY.md`
- Create: `docs/archive/STATUS-ate-2026-10-03.md` (conteúdo atual de `STATUS.md`, sem edição)
- Modify (reescrever): `tests/test_governance_contract.py`

**Interfaces:**
- Produces: o `test_governance_contract.py` novo, que as fatias seguintes mantêm verde.

- [ ] **Step 1: Write the failing test** — reescrever `tests/test_governance_contract.py` inteiro:

```python
import pathlib
root = pathlib.Path(__file__).resolve().parents[1]
agents = (root / "AGENTS.md").read_text(encoding="utf-8")
assert len(agents.splitlines()) <= 60, "AGENTS.md cabe numa tela"
for must in ("2026-10-03-omegas-platina-norte-unico-design.md", "Observar é automático", "um toque",
             "Desfazer", "readback", "GitHub Actions", "Agora", "Mapa K", "Curva K", "AutoCal", "Refino", "Sessões", "Ferramentas",
             "3b68ee52ac5481839046f36b482aab44", "3b78ee52ac548170b5c1fb69606ced21"):
    assert must in agents, must
for gone in ("LOCAL_SOURCE_MUTATION", "SOURCE_MUTATION_TARGET", "MMMACHINE", "Brainbase", "AgentRed", "RESET_ALL"):
    assert gone not in agents, gone
status = (root / "STATUS.md").read_text(encoding="utf-8")
assert len(status.splitlines()) <= 40 and "PHYSICAL_VALIDATION_CLAIMED" in status
assert (root / "docs/archive/STATUS-ate-2026-10-03.md").is_file()
assert (root / "docs/superpowers/specs/2026-10-03-omegas-platina-norte-unico-design.md").is_file()
print("GOVERNANCE_CONTRACT=PASS")
```

- [ ] **Step 1b: Find the other tests that read the old governance docs**

Run: `git grep -nE "AGENTS\.md|STATUS\.md|PROJECT\.md|docs/incidents" -- tests`
Expected: hits in at least `tests/test_progbase_autocal_action_map_contract.py`, `tests/test_platina_final_mission_contract.py`, `tests/test_platinum_autocal_command_specific_readback_contract.py`. In this task, delete from those tests only the assertions about the text of AGENTS/STATUS/PROJECT (the new governance test covers them) and repoint `docs/incidents/...` reads to `docs/archive/incidents/...`.

- [ ] **Step 2: Run to see it fail**

Run: `tools/ci/remote-test.sh python tests/test_governance_contract.py`
Expected: `REMOTE_TEST=FAIL` with `AssertionError: AGENTS.md cabe numa tela` (ou o primeiro `must` ausente).

- [ ] **Step 3: Rewrite the documents**

`AGENTS.md`: a meta (spec §0), as 10 regras (spec §0.2) em uma linha cada, as 7 abas, o laço `tools/ci/remote-test.sh`, a ordem dos planos (tabela deste índice), links da spec e das duas páginas do Notion. `PROJECT.md`: ≤ 10 linhas. `STATUS.md`: último APK conhecido (copiar o bloco "APK Platina — Refino base única…" do STATUS atual) + `PHYSICAL_VALIDATION_CLAIMED=false`. `docs/ARCHITECTURE.md`: spec §2 e §4.1 como diagrama ASCII + tabela "quem escreve no `StateStore`". `docs/TEST_STRATEGY.md`: spec §4.6 + o laço remoto.

- [ ] **Step 4: Run to verify**

Run: `tools/ci/remote-test.sh python tests/test_governance_contract.py`
Expected: `REMOTE_TEST=PASS`, log `GOVERNANCE_CONTRACT=PASS`.

- [ ] **Step 5: Commit** `docs: AGENTS/PROJECT/STATUS/ARCHITECTURE do zero (Norte Único)`

### Task 0.3: Arquivar o obsoleto e trazer spec + planos

**Files:**
- Move (git mv, sem edição) para `docs/archive/`: `LEARNING_RULES.md`, `docs/BLOCK_*.md`, `docs/decisions/`, `docs/spec-kits/`, `docs/workunits/`, `docs/handoff/`, `docs/incidents/`, `docs/MODELO_EQUIVALENCIA_CAUSAL.md`, `docs/OBD_*.md`, `docs/PENTE_FINO_VERDE.md`, `docs/MIGRATION.md`, e todo `docs/superpowers/specs/*` **exceto** `2026-10-03-omegas-platina-norte-unico-design.md`
- Add: a spec e os 9 planos em `docs/superpowers/plans/`

- [ ] **Step 1: Grep para referências de caminho aos arquivos movidos em testes e workflows**

Run: `git grep -nE "LEARNING_RULES|BLOCK_[0-9]|spec-kits|workunits|MODELO_EQUIVALENCIA|PENTE_FINO|docs/decisions|docs/handoff|docs/incidents|docs/MIGRATION|docs/OBD_" -- tests tools .github app`
Expected: lista (possivelmente vazia). Cada ocorrência vira o caminho `docs/archive/...` no mesmo commit.

- [ ] **Step 2: Mover e corrigir as referências**

- [ ] **Step 3: Run the full gate**

Run: `tools/ci/remote-test.sh checks ""`
Expected: `REMOTE_TEST=PASS`.

- [ ] **Step 4: Commit** `docs: arquiva governança obsoleta; spec e planos do Norte Único`

- [ ] **Step 5: Marcar o PR pronto e mesclar após `OMEGAS PLATINA CI` verde**

Run: `gh pr ready && gh pr checks --watch && gh pr merge --merge`
Expected: PR mesclado em `OmegasPlatina`.
