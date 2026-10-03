# Prompt — OMEGAS HUB: app novo a partir da Platina, enxuto e premium

> Cole este prompt inteiro no Claude Code aberto no repositório **OMEGAS Hub** (`viluadmcontas2-dot/-megas-Hub-`; recomendo renomear para `omegas-hub` em Settings → General antes de começar). Adicione também o repositório-mãe `viluadmcontas2-dot/OMEGAS-V8.2` à sessão: é de lá que o código vem.

---

Você vai criar o **OMEGAS HUB** do zero neste repositório, a partir do **OMEGAS Platina** (`OMEGAS-V8.2`, branch `OmegasPlatina` — a base mãe, o código que roda no carro do dono). Não é um fork: é um app novo, com arquitetura enxuta, que **reaproveita o motor provado e a UI/UX atual**, e descarta todo o resto. Nada de nomes `v7`, `prohub`, `Verde`, `Platina`, `learning`, `Predictor`.

## 0. Meta e regras (não negociáveis)

**Meta:** o motor, no GNV, se comporta como na gasolina. O app sabe quão perto está, onde falta e qual é a única próxima ação. Observa sozinho; só muda algo quando o dono toca.

1. Observar é automático; **mudar a ECU é sempre o dono**. Nada grava K, zera, restaura ou aplica sozinho.
2. Todo botão é **um toque**: sem diálogo de confirmação, sem segurar. Proteção = **foto antes + Desfazer** (Desfazer = restaurar o último backup da Curva K / a leitura anterior do Mapa K; nenhum comando novo na ECU).
3. O fim de toda gravação é o **readback** da ECU. "Gravado" só depois dele.
4. Nenhuma falha derruba o app: toda exceção vira estado `✗` legível com próxima ação. Erro de transporte (cabo/USB) ≠ erro da ECU (NACK/readback).
5. **Os comandos de leitura e escrita da ECU não mudam.** Os arquivos abaixo são copiados byte a byte (só muda o `package`): `UsbSerialManager`, `ResponseDrivenEcuEngine`, `Mp48Protocol`, `Mp48SerialScheduler`, `AutoCalProtocol`, `AutoCalPointDeleteProtocol`, `KFactorProtocol`, `KFactorManager`, `KWriteManager`, `AutoCalNativeActionManager`.
6. Dois níveis em toda tela: frase humana primeiro; comando, bytes e readback em **"Detalhes técnicos"** (nome único).
7. Multimídia fraca, 1280×720, Android 8+ (`minSdk 26`): sem framework JS, sem bundler, sem web font, sem `backdrop-filter`/sombras pesadas, sem timer de UI (redesenho por revisão, `requestAnimationFrame` só quando há dado novo).
8. **Texto de UI em português.** Código e comentários didáticos, curtos; nada de camadas "por via das dúvidas".
9. Validação física (classe 5) só com o dono no carro. Nunca escreva "validado" sem isso.

## 1. Decisões de engenharia (já tomadas — execute)

| Tema | Decisão | Por quê |
|---|---|---|
| `applicationId` | **`com.omegas.hub`** (novo) | App novo = instalação limpa; some o problema de dados antigos. O estado real vive na ECU e as sessões estão no Drive; nada se perde. Convive com o Platina até o dono desinstalar. |
| Pacote Kotlin | `com.omegas.hub.<camada>` | `ecu`, `usb`, `autocal`, `calibration`, `equivalence`, `session`, `service`, `bridge`, `ui` (assets). |
| Ponte JS↔Kotlin | **Uma só: `Omegas`**, com `snapshot(sinceRevision): String` e `request(intentJson): String`, mais o evento `window.OmegasOnRevision(revision)` | Hoje são 4 pontes e 57 métodos (`OmegasNative` 20, `OmegasAutoCal` 22, `OmegasCalibration` 9, `OmegasPower` 6). Em app novo isso é a única reescrita que paga: 57 → 2. Os motores Kotlin continuam sendo chamados como são, por trás da ponte. |
| Telas JS | **Ficam as atuais**; `core/native-api.js` e `core/autocal-api.js` viram **adaptadores** sobre `Omegas` com as mesmas funções que as telas chamam hoje | A UI muda só o necessário; as telas não são reescritas. |
| Cérebro | **`EquivalenceEngine`** (pacote `equivalence`): Referência congelada, Curva Própria, 30 pontos com estado, índice "% da condução equivalente à gasolina", próxima ação | É a única peça nova. Colha de `OMEGAS-V8.2` branch `work/platina-f4` se existir/estiver mesclada em `OmegasDiamante`; senão implemente pelo plano `docs/superpowers/plans/2026-10-03-f4-cerebro-unico.md` + spec §1. |
| Harvest | **Determinístico**: `tools/harvest.py` copia a lista de arquivos de um SHA fixo da `OmegasPlatina`, renomeando pacotes. Nada é copiado à mão. | Reproduzível, diffável, auditável. |
| CI | `ci.yml` desde o primeiro commit: `python3 -B tools/run_checks.py` (Python + `node --test`) → `./gradlew testDebugUnitTest lintDebug`. Emulador **uma vez**, no fechamento de UI/UX. APK **um só**, no fim, com SHA-256. | Kotlin só compila no CI (não há SDK na sessão). |
| Execução | Lotes grandes; teste só quando decide algo (Python/JS local; Kotlin no PR); uma branch de trabalho por vez; canônica = `main`; PR entra com CI verde no SHA. | Custo em tokens decide onde escrever e testar. |

## 2. O que vem da Platina (harvest) e o que fica de fora

**SHA mãe:** `OmegasPlatina` @ `a125436` (código da Platina + docs da F0; anote em `docs/HARVEST.md`). Referência de poda: `OmegasDiamante` (fatias F1–F3 provam o que é seguro apagar).

### 2.1 Motor Kotlin — copiar (renomeando `com.omegas.prohub` → `com.omegas.hub`)

- **`ecu/`**: `UsbSerialManager`→`usb/`, `OmegasUsbIdentity`, `UsbProtocolReply`, `UsbRecoveryPolicy`, `ResponseDrivenEcuEngine`, `Mp48Protocol`, `Mp48SerialScheduler`, `Mp48BackpressureScheduler`, `Mp48TelemetryScale`, `AutoCalProtocol`, `AutoCalPointDeleteProtocol`, `AutoCalScale`, `KFactorProtocol`, `FuelStateResolver`, `MotorSampleAnalyzer` (só confirmação de combustível + validade), `NativeRuntimeManager` (enxugado: sem aprendizado), `AdaptiveSampleWindow`, `LearningControlModel`/`LearningToleranceSettings`/`LearningTemperatureSettings` → **renomear** para `EcuControlModel`, `EcuToleranceSettings`, `EcuTemperatureSettings` (são do motor da ECU, não "aprendizado").
- **`autocal/`**: `AutoCalNativeActionManager` (sem `MANUAL_AUTOMATCH`), `NativeAutoCalMonitor`, `NativeAutoCalAcquisitionEpoch`, `NativeAutoCalEpochGuard`, `NativeAutoCalMaturityTracker`, `NativeAutoCalRefreshPlanner`, `NativeAutoMatchCounterTracker`, `NativeAutoMatchEvidenceBracket`, `AutoCalSnapshot`, `AutoCalSnapshotManager`, `AutoCalAcquisition`, `AutoCalRecoveryPolicy`, `AutoCalUiProjection`, `EcuPetrolReference`, `EquivalenceLedger`, `EquivalencePhases`, `EquivalenceView`, `AutoMatchRefinedEngine`, `AutoMatchV5Engine`, `AutoMatchSnapshotAnalysis`, `RefinementJournal`, `StallWatch`, `TypicalInjectionBands`, `PresentationMedian`, `BackgroundMemo`.
- **`calibration/`**: `KFactorManager`, `KWriteManager`, `KFactorBackupRetention`, `KFactorManualPlanner`, `MapKManualPlanner`, `MapBatchPlan`, `CalibrationWriteSafetyPolicy`, `KMapPhysicalAxes`, `LiveCellProjection`, `ContinuousLearningMath` → **renomear** `CellMath`.
- **`session/`** (hoje `diagnostics/`): `SessionRecorder`, `SessionPartPlanner`, `SessionResumo`, `SessionSemanticLedger`, `DocumentsSessionMirror`. Formato `omegas-session-log-v1` **inalterado** (o Drive e as ferramentas dependem dele).
- **`service/`**: `TelemetryForegroundService`, `TelemetryOverlayController` (balão), `NotificationController`, `UsbSessionTransitionPolicy`. **`telemetry/`**: `TelemetryStateStore`, `ConsumptionTracker`. **`storage/AppPaths`**, **`settings/AppSettings`**, **`gps/GpsTelemetryManager`**, **`util/`** (`RingLog`, `LatestOnlyBackgroundPipeline`, `RuntimeBackpressurePolicy`), **`MainActivity`** (reescrita curta: WebView + uma ponte).

### 2.2 Fica de fora (não copiar)

`HubJavascriptBridge`, `AutoCalJavascriptBridge`, `CalibrationOperationsBridge`, `PowerJavascriptBridge`, `AutoCalBridgeProvider` (substituídos pela ponte única) · `LinkProtocol`/`OmegasLinkManager`/`LanPanelServer` (LAN: sem botão útil) · `LegacyDataSweeper`, `DataArchiveManager` (app novo não tem legado) · `RuntimeSnapshotBus` (vira o `StateStore` da ponte) · `HubStatus` · tudo que a poda F1–F3 da `OmegasDiamante` já apagou (Predictor, V7, learning, AutoMatch manual, órfãos). Teste de contrato: **zero** ocorrências de `v7|V7|prohub|Verde|Platina|Predictor|MANUAL_AUTOMATCH|learning` em `app/` e `assets/` (exceto o `docs/HARVEST.md`).

### 2.3 UI/UX — copiar e refinar (não reescrever)

Copiar `app/src/main/assets/ui/` inteira: `index.html`, `app.js`, `core/` (`router`, `store`, `scheduler`, `display-rules`, `native-api`, `autocal-api`), `components/` (`drawers`, `split-layout`, `vehicle-status-strip`), `screens/` (`dashboard`, `map`, `curve`, `autocal-cockpit`, `refino`), CSS. Depois refinar, nesta ordem e **só isto**:

1. **Trilho de 7 itens**: `01 Agora · 02 Mapa K · 03 Curva K · 04 AutoCal · 05 Refino · 06 Sessões · 07 Ferramentas`. Sai **Sugestões** (nada a alimenta); entra **Sessões** (a lista que hoje vive em Ferramentas: duração, apagões, índice início→fim, exportar ZIP). Bugs de navegação saem junto: rota `learning` inexistente, `routeMeta.predictor`, botões órfãos em `drawers.js`, `data-omegas-route` nunca setado.
2. **Agora**: índice grande "% equivalente à gasolina" + **uma** próxima ação com um botão que abre a aba certa já posicionada + curva mini com ▲ AGORA + combustível + conexão. Nunca executa nada.
3. **`tokens.css`**: uma paleta só (fundo, superfície, texto, acento, e as 5 cores de estado: ok / atenção / erro / neutro / em prova). Todo CSS usa tokens; cor é estado. `styles-autocal-cockpit.css` tem 2.324 linhas — reduzir ao que a tela realmente usa (meta: < 800).
4. **Gráficos premium**: curva K e equivalência em `<canvas>` com antialias, grade discreta, Referência tracejada, Própria sólida, 30 pontos coloridos por estado, ▲ AGORA com redesenho parcial. Mapa K: células ≥ 44 px (única exceção ao piso de 76 px).
5. **Pisos**: toque ≥ 76 px, texto crítico ≥ 24 px, `<details>` "Detalhes técnicos" sempre o último bloco. Overlay de operação único para toda gravação: etapa → resultado humano → `Desfazer` / `Voltar`.
6. **Zero timers**: remover os 13 `setInterval/setTimeout` de UI; tudo redesenha por `OmegasOnRevision`.

### 2.4 Evidência que vem junto

- `fixtures/autocal/real/*.json.gz` (sessões reais do dono) + `tools/autocal_refine/` (extrator e oráculo Python) + os testes que os leem.
- `tests/` Python e `tests/ui/*.test.cjs` que cobrem o que foi copiado (os que cobrem o que ficou de fora **não** vêm). `tools/run_checks.py`.
- `app/src/test/` Kotlin dos arquivos copiados.
- Workflows: só `ci.yml`, `apk.yml` (manual, `build_apk=true`, SHA-256) e `render.yml` (emulador 1280×720, manual). Os 9 workflows `verde-*`/`autocal-*` **não** vêm.

## 3. Entrega em 4 PRs (cada um entra em `main` com CI verde)

| PR | Entrega | Prova |
|---|---|---|
| **P0 · Esqueleto** | repo, `AGENTS.md` (uma tela: meta, 9 regras, 7 abas, como trabalhar), Gradle (`com.omegas.hub`, minSdk 26, compileSdk 35), `ci.yml`, `tools/harvest.py` + `docs/HARVEST.md` (SHA mãe, tabela arquivo→destino→motivo), teste de nomes proibidos | CI verde; `harvest.py` idempotente |
| **P1 · Motor** | harvest Kotlin (§2.1), renomes, `EquivalenceEngine`, `StateStore` (seções `connection, live, now, reference, points, index, nextAction, operation, session, autocal, settings`, `revision` monotônica) | compila; testes JVM; **replay das sessões reais**: índice sobe após ajuste, Curva Própria prevê melhor que a Referência, paridade Python↔Kotlin |
| **P2 · Ponte + telas** | `Omegas` (`snapshot`/`request`), fila de operações (uma mutação por vez, foto antes, readback, recibo), adaptadores JS, telas rodando sobre a ponte nova, teste "nenhum intent sem handler, nenhum método sem chamador" | CI verde; `node --test` das telas |
| **P3 · Refino + APK** | §2.3 itens 1–6, `STATUS.md` (só o APK: SHA, run, classe de prova, não provado), protocolo de teste físico das 7 abas | emulador 1280×720 uma vez (cada aba × estados sem cabo/conectando/conectado); **APK final** com SHA-256 |

**Em cada PR diga:** o que mudou · classe de prova (1 contrato · 2 sintético · 3 replay real · 4 emulador · 5 físico) · o que ficou **não provado**. Plano não bate com o código: decida, registre em uma linha na Issue do PR e siga; se muda o que o dono vê ou o que a ECU recebe, pare e pergunte.

## 4. Orçamentos (metas, não portões — só o teste de nomes proibidos e o de ponte sem órfão falham o CI)

Kotlin: 20.7k linhas hoje → **≤ 14k**. UI: 8.8k linhas → **≤ 6k**. Métodos de ponte: 57 → **2**. Timers de UI: 13 → **0**. Primeira pintura < 300 ms e toque < 100 ms na multimídia (medir no emulador, registrar no STATUS como classe 4).

## 5. Como relatar ao dono

Português simples, ao fim de cada PR: o que mudou na tela, número do PR, link do run que provou, o que ficou não provado. Sem jargão.

Comece pelo P0.
