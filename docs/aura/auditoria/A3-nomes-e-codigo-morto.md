# A3 - Nomes legados e código morto (Omega Aura, unidade I8)

Medido com `git grep` em 2026-10-27, branch `claude/omega-aura`, sem `docs/archive`, `docs/evidence`, `docs/reverse`, `tests/fixtures`, `graphify-out`. "inferido" = não verifiquei linha a linha. Nada foi alterado no código.

## 1. Inventário (linhas / arquivos)
| Termo | Linhas | Arquivos | Observação |
|---|---|---|---|
| V7/v7 | 1287 | 179 | núcleo do problema |
| V8/v8 | 192 | 130 | em grande parte `arm64-v8a` (falso positivo, manter) |
| platina/Platina | 112 | 34 | docs/platinum, `omegas-platina-final`, fixtures `platinum-*` |
| landi/LANDI | 1725 | 123 | `landi-k-13x12-v1`, `landi-kfactor-...` são ESQUEMAS; resto é link p/ PROTOCOLO-LANDI (nome de repo externo, manter) |
| aeb/AEB | 10618 | 146 | nome do protocolo da central (fabricante): manter. Só `AebProtocolFramesV7` tem sufixo legado |
| vermelho | 61 | 40 | tema/cor "red" nos testes (`test_red_*`); só nome |
| legado/legacy | 162 | 62 | `docs/progbase/legado.md` é doc vivo; resto comentários |
| diamante | 2 | 2 | irrelevante |

Arquivos Kotlin com V7 no nome (principal): 16 em `app/src/main/java` + 14 em `app/src/test/java`:
- Pacote `com.omegas.v7.{runtime,protocol}`: 10 + 1 classes em main, 9 + 1 testes; 28 arquivos importam `com.omegas.v7`.
- Em `prohub/`: `V7CalibrationCoordinator`, `V7CalibrationAccess` (service/), `V7JavascriptBridge` (web/), `AdvisorSuggestionAdapterV7`, `CalibrationWriterReadBackV7` (calibration/).
- Classes: `V7SessionRuntime`, `V7SessionFileStore`, `V7SessionSnapshotCodec`, `V7UiProjection`, `V7EquivalenceEngine`, `LearningStabilityV7/JsonV7/ProjectionV7`, `CalibrationShapeV7`, `CalibrationCausalTransitionV7`, `AebProtocolFramesV7`.
- Python: `tests/test_v7_map_batch_contract.py`, `tests/test_v82_integral_regression_contract.py`; 30 arquivos em tests/tools citam v7 (16 `tests/test_*.py`, 7 `tests/ui/*`, `tools/grafo_producao.py`, `tools/portmon/...`, `tools/progbase/protocolo_comandos.py`).
- JS: só 2 arquivos (`ui/app.js`, `ui/core/native-api.js:169` `root.OmegasV7`).

Strings persistidas / contratos (grupo B/D):
- `config/omegas-release.json`: product "OMEGAS V8", appLabel "OMEGAS V8 TEST", generation "V8", versionName 8.0.0-test, versionCode 800, applicationId `com.omegas.v7.test`, artifactName `OMEGAS-V8-TEST`, engine `mp48-v8`, learningSchema `omegas-learning-v7-live-only-v1`, learningDataRevision 7, mapSchema `landi-k-13x12-v1`, kFactorSchema `landi-kfactor-30-q14-trunc-v3`. Lido por `app/build.gradle.kts:55`, `LiveOnlyLearningStore.kt`, `HubJavascriptBridge.kt`, `apk.yml`, `tests/test_v7_map_batch_contract.py` (inferido: mais testes contratuais).
- Arquivos/prefs: `"v7_sessions"` (pasta de sessões), `"omegas-v7-web-operations"`, prefs `omegas_learning_v5` (LearningToleranceSettings.kt:196), `omegas_learning_native`, `power_policy`, `crash_logs`. Os 3 primeiros têm nome legado; os dois últimos já são neutros.
- Interfaces JS<->Kotlin (MainActivity.kt:315-317): `OmegasNative` (16 arquivos citam), `OmegasV7` (7 arquivos: native-api.js, MainActivity, 4 testes UI, tools/grafo_producao.py), `OmegasPower`; AutoCal usa `INTERFACE_NAME` próprio.
- Módulo gradle `namespace = com.omegas.prohub` (neutro). Workflows `apk.yml`/`ci.yml`: v8 só em `arm64-v8a` (falso positivo); apk.yml lê o release json e o artefato "omegas-platina-final".
- Documentos vivos: `STATUS.md` ("v8", "v7: o app NÃO grava sozinho"), `AGENTS.md`, `PROJECT.md`, `README.md`, nome do repo `OMEGAS-V8.2` (só documentar; não dá para renomear daqui).

## 2. Classes de risco
- A (renome seguro, só identificador): ~25 classes `*V7`/`V7*` fora da lista protegida; pacote `com.omegas.v7.*`; 2 testes Python; textos de docs/STATUS; `appLabel`/`product`/`artifactName`/`generation`/versionName (só texto exibido; ver D para applicationId).
- B (dado persistido/contrato): `learningSchema`, `learningDataRevision`, `"v7_sessions"`, `omegas_learning_v5`, `omegas-v7-web-operations`, `engine mp48-v8`, `mapSchema`/`kFactorSchema` (nomes `landi-*`), interface JS `OmegasV7`/`OmegasNative` (A se JS e Kotlin mudam juntos no mesmo commit; B para sessões gravadas no aparelho). Exigem migração (ler nome antigo, gravar novo) ou versão nova de esquema.
- C (protegida): `UsbSerialManager`, `ResponseDrivenEcuEngine`, `AutoCalProtocol`, `KFactorManager`, `KWriteManager`, `AutoCalNativeActionManager`. Nenhuma tem V7 no nome. MAS tocam em V7 por dentro: `AebProtocolFramesV7` (frames da central, vizinha), `KFactorManager.kt:480` e a ponte (`V7JavascriptBridge.kt:302,509`) dependem do ACK/readback. Regra: renomear identificador/import sim, bytes e fluxo não; marcar "protegida" no PR e rodar `AebProtocolFramesV7Test` antes e depois com bytes idênticos.
- D (identidade Android): `applicationId` `com.omegas.v7.test`. Mudar = OUTRO app (dados, permissão USB e "sempre abrir este app" não migram; os dois convivem). Decisão do dono (PLANO.md recomenda mudar para o Aura). `namespace` já é `com.omegas.prohub` e NÃO precisa mudar.

## 3. Dicionário proposto (sem V7/V8)
Prefixo neutro `Session*`/`Calibration*`; inglês no código, como hoje.
| Antigo | Novo |
|---|---|
| pacote `com.omegas.v7.runtime` / `.protocol` | `com.omegas.prohub.session` / `com.omegas.prohub.protocol` (ou `com.omegas.runtime`; escolher 1) |
| V7SessionRuntime / V7SessionFileStore / V7SessionSnapshotCodec | SessionRuntime / SessionFileStore / SessionSnapshotCodec |
| V7UiProjection / V7EquivalenceEngine | UiProjection / EquivalenceEngine (cuidado: já existe `autocal/EquivalenceAttribution`; usar `SessionEquivalenceEngine`) |
| LearningStabilityV7(+Json/Projection) | LearningStability(+Json/Projection) |
| CalibrationShapeV7 / CalibrationCausalTransitionV7 | CalibrationShape / CalibrationCausalTransition |
| AebProtocolFramesV7 | AebProtocolFrames |
| V7CalibrationCoordinator / V7CalibrationAccess | CalibrationCoordinator / CalibrationAccess |
| AdvisorSuggestionAdapterV7 / CalibrationWriterReadBackV7 | AdvisorSuggestionAdapter / CalibrationWriterReadBack |
| V7JavascriptBridge, JS `OmegasV7` | CalibrationJavascriptBridge, `OmegasCalibration` |
| `OmegasNative` | manter (já neutro; 16 arquivos) |
| learningSchema `omegas-learning-v7-live-only-v1` | `omegas-learning-live-only-v2` (+ leitor do v1) |
| `v7_sessions` / `omegas-v7-web-operations` / `omegas_learning_v5` | `sessions` / `omegas-web-operations` / `omegas_learning` (com migração) |
| product / appLabel / artifactName / generation | "OMEGAS Aura" / "OMEGAS Aura TEST" / `OMEGAS-AURA-TEST` / "Aura" |
| versionName `8.0.0-test` | `9.0.0-aura` (inferido: versionCode > 800) |
| engine `mp48-v8` | `mp48` |
| `landi-k-13x12-v1`, `landi-kfactor-...` | manter nome no dado; alias novo `k-map-13x12-v1`/`k-curve-30-q14-v3` só com migração (baixo ganho, deixar para o fim) |
| tests `test_v7_map_batch_contract.py`, `test_v82_integral_regression_contract.py` | `test_map_batch_contract.py`, `test_integral_regression_contract.py` |

## 3b. Ordem segura (cada passo = 1 commit, CI verde antes do próximo)
0. Baseline: rodar `node --test tests/ui/*.test.cjs`, `python3 -B -m unittest discover -s tests -p "test_*.py"`, CI Gradle (`testDebugUnitTest lintDebug`) no SHA atual.
1. Testes Python/docs (A): `git mv tests/test_v7_map_batch_contract.py tests/test_map_batch_contract.py` e idem v82; `sed` nas referências (`git grep -l test_v7_map_batch`). Prova: unittest discover.
2. Pacote Kotlin (A): `git mv app/src/main/java/com/omegas/v7 app/src/main/java/com/omegas/prohub/session` (e o mesmo em src/test); `git grep -l 'com\.omegas\.v7' -- app | xargs sed -i 's/com\.omegas\.v7\./com.omegas.prohub.session./g'` e ajustar `package`. Prova: CI Gradle `compileDebugKotlin testDebugUnitTest`. Só imports/package; zero lógica.
3. Classes sem persistência (A): para cada linha do dicionário, `git mv` + `sed -i 's/\bV7SessionRuntime\b/SessionRuntime/g'` em app e tests. Em lotes: (a) runtime, (b) calibration/service. Prova: Gradle + `tests/test_*` que citam classes (16 py; conferir `git grep V7` = só o que sobrou).
4. Ponte JS (A, atômico): `V7JavascriptBridge`->`CalibrationJavascriptBridge`, `"OmegasV7"`->`"OmegasCalibration"` em MainActivity.kt:316, `native-api.js:169` e 4 testes UI + `tools/grafo_producao.py` no MESMO commit. Prova: `node --test tests/ui/*.test.cjs` (app-shell-runtime, didactic-expansion).
5. Marcação C: PR separado só se tocar nas 6 protegidas (imports); diff deve mostrar zero byte de comando; rodar `AutoCalProtocol*Test`, `KFactor*Test`, `AebProtocolFramesTest`.
6. Persistência (B): esquema novo + leitor do antigo (ver `LiveOnlyLearningStore.kt`, `OmegasMigrationService`, que já existe para isso). RED primeiro: teste que lê dado `v7_sessions`/learning v1 e acha no novo local. Só depois trocar `omegas-release.json` (learningSchema, learningDataRevision) e `tests/test_v7_map_batch_contract.py` (que lê o json). Prova: `LiveOnlyLearningStoreTest` (inferido) + `OmegasMigrationServiceTest` + python contratos.
7. Texto de produto (A): `omegas-release.json` product/appLabel/artifactName/generation/versionName/engine; STATUS/README/PROJECT/AGENTS. Antes: `git grep -l 'OMEGAS-V8-TEST\|omegas-platina-final' .github tools tests`, atualizar apk.yml. Prova: `ci.yml` + `python3 -B tools/run_checks.py`.
8. Identidade (D) só com OK do dono: applicationId novo (`com.omegas.aura`). Releva: dados e USB do app velho não passam; avisar o dono e oferecer exportar sessão antes. Prova: assembleDebug em CI e instalação lado a lado (aparelho, dono).

## 4. Código morto e duplicado (ranqueado por impacto)
| # | Item | Evidência | Decisão | Prova de segurança |
|---|---|---|---|---|
| 1 | Fase `preview` + "Toque de novo para gravar" | `advanced-flags.js:182-204` (ainda ativo em 4 linhas) | apagar (PENDENTE.md:60) | `tests/ui/anti-apagao-*.test.cjs`, `avancado-leitura.test.cjs` + `anti-apagao-toque-real` (1 toque grava) |
| 2 | Superfície prepare/confirm no nativo | PENDENTE.md:7 ("só reserva da ponte") | apagar o par no Kotlin + `native-api.js`; ATENÇÃO: perto de KWriteManager (C): só tirar métodos da ponte, não o gravador | Gradle + grep `prepare\w*`/`confirm\w*` sem chamador JS + `tests/ui/gravacao-conferencia-segundo-plano` |
| 3 | `FuelStateResolver.kt` solto na raiz | difere de `app/.../ecu/FuelStateResolver.kt` (usado por `MotorSampleAnalyzer`, tem `FuelStateResolverTest`) | apagar o da raiz (git: sem referência de build) | `testDebugUnitTest`; confirmar com `git grep` que nada aponta p/ a raiz |
| 4 | `PENDENTE.md` na raiz | 2 linhas, só aponta p/ `docs/handoff/PENDENTE.md` | manter como ponteiro OU apagar e ajustar AGENTS (lê handoff direto) | `test_governance_contract.py` (inferido: pode checar o arquivo) |
| 5 | Classes em main sem nenhum uso em main (verificado por nome) | `AutoMatchDraftReviewValidator`, `AutoMatchResidualPlanner`, `SessionCarMap`, `SessionEventNotes`, `SummaryTotals`, `CurveKComparison`, `LearningEvidenceModels`, `NativeAutoCalEvidenceSummary`, `OmegasMigrationService`, `ArchitectureContracts`, `StartupLifecyclePolicy`, `WeightedStat`, `TelemetryVisualLifecyclePolicy`, `OrderedBackgroundPipeline`, `V7CalibrationAccess`, `CalibrationWriterReadBackV7`, `AebProtocolFramesV7`, `CalibrationCausalTransitionV7` | A busca por nome pode errar (reflexão, mesmo arquivo). Casos fortes: `V7CalibrationAccess`, `CalibrationWriterReadBackV7`, `AebProtocolFramesV7`, `CalibrationCausalTransitionV7` só têm teste => decidir "ligar no app" ou "apagar com o teste". `OmegasMigrationService` tem motivo de existir (migração, passo 6): NÃO apagar | por classe: `git grep -w Nome -- app/src/main` vazio + Gradle verde |
| 6 | `fixtures/` (raiz) x `tests/fixtures/` | raiz: autocal(3+3 real), learning, replays; usada por build.gradle.kts e 3 testes Kotlin | mesclar em `app/src/test/resources` ou `tests/fixtures`; mover com `git mv` e ajustar `build.gradle.kts` + `RealSessionReplaySupport.kt` | Gradle `testDebugUnitTest` (3 testes AutoMatch/Refinement) |
| 7 | `lab/contracts/` | 4 arquivos, só docs/legado o citam; sem CI (inferido) | mesclar em `tests/` ou apagar se não roda no `ci.yml` | conferir `ci.yml`; rodar `python3 lab/contracts/test_obd_mp48_contract.py` antes |
| 8 | `scripts/omegas/portmon_parser.py` e `evidence/portmon/` | só 1 script; ref. em docs | mesclar em `tools/` | `git grep scripts/omegas` |
| 9 | `fixtures/replays/autocal_failure_matrix_v6.json` | nome v6 | verificar uso (inferido: sem uso) | `git grep autocal_failure_matrix` |
| 10 | `decodeFlags`, `data-adv-save` | sem código vivo: só comentário em `avancado-leitura.test.cjs:74` e asserção `count()==0` em `anti-apagao-valores-didatico.test.cjs:85`; "Gravar estes valores" 0 ocorrências em app/tests | JÁ removido; tirar de PENDENTE.md:60 e deixar as asserções (guardam regressão) | node tests/ui |
| 11 | JS em `ui/` sem referência | varredura por nome: nenhum achado | manter | - |
| 12 | `docs/platinum/`, `docs/evidence/PLATINA-*`, fixtures `platinum-*` | nome de produto antigo | manter (evidência); só renomear se o dono quiser; teste `test_platinum_autocal_action_parity_contract.py` trava o nome | python |

## 5. Testes que travam nomes antigos (mudar no mesmo passo)
- Python (16 `tests/test_*.py` citam v7; ver lista no passo 2-3): `test_v7_map_batch_contract.py`, `test_v82_integral_regression_contract.py`, `test_governance_contract.py`, `test_final_pre_apk_product_contract.py`, `test_block1_session_contract.py`, `test_verde_scientific_runtime_contract.py`, `test_red_*`, `test_missao_final_contract.py` etc. Eles leem caminhos de arquivo Kotlin e nomes de classe como texto: passos 2/3 quebram aqui primeiro. Rodar `git grep -l V7 tests/*.py` após cada lote.
- JS: `tests/ui/app-shell-runtime`, `didactic-expansion`, `helpers/browser.cjs`, `helpers/ecu-bench.js` fazem stub de `OmegasV7`; mais `map-editor-flow`, `map-workflow-e2e`, `gravacao-conferencia-segundo-plano`, `telemetry-screen-gates` (citam v7).
- Kotlin: 14 testes em `app/src/test/java/.../v7` e `calibration/*V7*`; `LearningStabilityVolatilityTest` e `V7EvidenceImmutabilityRegressionTest` moram no pacote v7 (mover junto).
- Config: `test_v7_map_batch_contract.py` lê `omegas-release.json`; mudar json (passos 6-7) exige mudar o teste junto.
- Fixtures `platinum-*` e `docs/platinum`: travados por `test_platinum_autocal_action_parity_contract.py`.

## Maiores riscos
1. applicationId (D): app novo, perde dados e permissão USB.
2. learningSchema/`v7_sessions`: renomear sem migração apaga o aprendizado gravado.
3. Ponte `OmegasV7` (JS+Kotlin+5 testes): se um lado ficar para trás a tela morre sem erro claro.
4. Testes Python leem Kotlin como texto: rename em massa quebra 16 contratos.
5. Remover prepare/confirm e `AebProtocolFramesV7` perto de classes protegidas (KWriteManager/KFactorManager): risco de mexer em gravação sem querer.
