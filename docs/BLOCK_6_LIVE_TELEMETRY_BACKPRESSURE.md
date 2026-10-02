# Bloco 06 — Telemetria ao vivo e backpressure — micropassos 051–060

Base revalidada antes do bloco: `work/v8.2-clean` em `e38f5d59361b1c69d2273fd0dea691b7b3672642`.

## Evidência de implementação

- **051** — `LatestOnlyBackgroundPipeline` já mantém exatamente um quadro ativo e no máximo um pendente substituível. Não existe `ArrayDeque` nem executor com fila histórica na entrega visual.
- **052** — Learning usa `RealtimeLearningBuffer`, separado da entrega visual `LatestOnlyBackgroundPipeline`; descarte/coalescência visual não apaga a evidência científica.
- **053** — falhas e eventos críticos seguem `RingLog`/callbacks próprios; `TelemetryVisualLifecyclePolicy` congela explicitamente que eventos críticos não usam a fila visual descartável.
- **054** — `TelemetryStateStore` mantém uma única `AtomicLong sequence` monotônica e publica a sequência no snapshot entregue à UI.
- **055** — `TelemetryStateStore.liveJson()` publica `updatedAt` e `ageMs`, calculados nativamente; a tela não precisa inferir frescor.
- **056** — o pipeline visual substitui o pendente antigo pelo snapshot mais recente quando o consumidor está lento.
- **057** — `TelemetryVisualLifecyclePolicy.primaryTelemetryFields` congela RPM, Petrol Inj. e combustível como caminho primário; gráficos/Predictor ficam classificados como trabalho visual secundário. Resposta ao toque não depende de replay de backlog visual.
- **058** — `LatestOnlyBackgroundPipeline.metricsJson()` expõe contadores bounded (`pending`, `active`, `coalesced`, delays e tempos máximos) sem log por frame.
- **059** — política explícita: `HIDDEN` não renderiza telemetria visual, mas o Store continua latest-only; `SPLIT_SCREEN` mantém telemetria primária e suspende visual secundário; ao voltar não há replay histórico.
- **060** — `LatestOnlyBackgroundPipelineTest.slowConsumerKeepsOnlyNewestPendingState` bloqueia artificialmente o consumidor, injeta sequências 2..100 e prova que somente 1 e 100 são processadas, com backlog pendente máximo de 1.

## Limites deste bloco

Este bloco não reconstrói a UI legada. A nova UI, quando chegar à fase própria, deverá consumir estes contratos sem criar fila paralela em JavaScript. Nenhuma mudança automática de ECU foi adicionada.

## Validação

A evidência comportamental relevante já existe no teste Kotlin de saturação e foi complementada pelo teste da política de lifecycle. Neste bloco não foi executado GitHub Actions nem gerado APK. Qualquer medição física de frame-time/CPU em multimídia permanece para a fase de validação física prevista no Programa Mestre.
