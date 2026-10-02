# Bloco 03 — contratos arquiteturais V8.2 (021–030)

Este bloco congela as fronteiras arquiteturais antes dos próximos trabalhos de lifecycle, integração e UI. Ele não altera protocolo MP48, matemática científica, writers, telas ou comportamento físico da ECU.

## Contratos

- **021 — autoridade serial MP48 única:** `Mp48SerialScheduler` permanece a porta contratual fora da engine; managers não recebem autoridade USB paralela.
- **022 — estado de aplicação:** novas superfícies devem consumir projeções do estado nativo existente; não podem criar uma segunda verdade operacional/científica na WebView.
- **023 — domínios de estado:** `AppStateDomain` separa explicitamente `SCIENTIFIC`, `OPERATIONAL` e `VISUAL`.
- **024 — eventos canônicos:** `ProductEvent` define eventos de telemetria, Learning, AutoCal, Predictor e calibração sem acoplar a camada visual aos produtores.
- **025 — freshness:** `Freshness` produz `AVAILABLE`, `STALE` ou `UNAVAILABLE` a partir do instante real de produção, nunca do instante em que a UI consultou o dado.
- **026 — indisponibilidade:** ausência e stale são estados explícitos; fallback silencioso para valor antigo não satisfaz este contrato.
- **027 — revisão científica:** `ScientificRevision` só acompanha mudança científica/semântica. Refresh visual não cria revisão. `AdvisorRevisionGate` já segue este princípio.
- **028 — intenção humana:** `HumanIntent.requireConfirmed()` torna confirmação explícita pré-condição do contrato para mutação de ECU. A `CalibrationWriteSafetyPolicy` continua sendo a política operacional de segurança complementar.
- **029 — erro em dois níveis:** `HumanFacingError` exige resumo humano e detalhe técnico, além de código estável.
- **030 — fronteira da UI:** `UiBoundaryContract` permite renderizar estado e emitir intenção; proíbe USB direto, parser MP48 direto, writer próprio e matemática científica na UI.

## Evidência já existente preservada

- `TelemetryStateStore` mantém estado nativo thread-safe, `sessionId`, validade e idade real de telemetria.
- `Mp48SerialScheduler` modela a autoridade serial e unidades transacionais.
- `AdvisorRevisionGate` avança revisão por token semântico, não por timer/frame.
- `CalibrationWriteSafetyPolicy` bloqueia escrita por estado operacional inseguro e declara confirmação humana obrigatória como etapa separada.

## Delta deste bloco

- `app/src/main/java/com/omegas/prohub/model/ArchitectureContracts.kt`
- `app/src/test/java/com/omegas/prohub/model/ArchitectureContractsTest.kt`
- este documento de rastreabilidade.

Os próximos blocos devem **usar** estes contratos; este bloco deliberadamente não migra consumidores nem redesenha UI.
