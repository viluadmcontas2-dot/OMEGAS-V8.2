# Inventário observável de superfícies — OMEGAS 8.2 (C0 / Task 3, etapa 1 parcial)

> **OBS-COD**, fotografado do código da branch `feat/premium-adaptive-ui-v8-2` no SHA `2b32043020ad5518fb282d03c7bfc04b70e897af` (2026-11-17). Este documento é **inventário estrutural, NÃO matriz completa de capacidades**, NÃO prova de suporte pela central e NÃO homologação física. Fonte principal de fluxos: `docs/qualidade/matriz-funcional.json`, ainda `PARTIAL_SEED_NOT_COMPLETE` com 12 linhas. Uma API nativa exposta não significa um botão utilizável.

## Escopo e método

Leitura estática de `index.html` (tags `button`) e das três classes Java/Kotlin com métodos públicos anotados `@JavascriptInterface`. Não inclui botões gerados por JavaScript, outros arquivos HTML, pontes secundárias que possam existir, comandos serial internos nem callbacks indiretos. **Não calcular percentual de cobertura** a partir destes totais. Cada método exige inspeção de consumidores, estado de conexão, prova de protocolo, teste e, quando cabível, resultado físico.

- `index.html`: **33** tags `button` estáticas, **16** com `id`, **17** sem `id`; cinco delas são rotas primárias, não ações da central.
- Pontes Kotlin lidas: **71** métodos `@JavascriptInterface` (HubJavascriptBridge.kt: 48; V7JavascriptBridge.kt: 18; PowerJavascriptBridge.kt: 5).
- A matriz funcional registra **12 fluxos iniciais** (não comparáveis 1:1 a métodos ou botões).
- Proveniência: **OBS-COD** para nomes e presença no código; **DESCONHECIDO** para vínculo individual de cada método a capacidade real do perfil e para validação física; não há **OBS-FIO** novo nesta rodada.

## Botões HTML com ID (16)

`liveVerify · mapSelectAll · mapClearSelection · mapFromCurve · mapFinishCurve · mapReviewButton · curvePreparePoint · curveClearProposals · curveReviewButton · curveReadButton · curveSmoothButton · curveBackupSave · curveBackupRestore · curveResetButton · toolsPageNext · toolsPagePrev`

Os cinco `data-route` são `autocal`, `refino`, `map`, `curve`, `tools`. Os 17 botões sem ID incluem controles por atributo e botões sem seletor próprio: devem ser cruzados com listeners e componentes JS antes de contabilizar ações distintas.

## Métodos expostos pelas pontes

### `app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt` (48)

`getReleaseIdentity · getPresentSnapshot · getPresentSnapshotIfChanged · getScienceSnapshotSince · getStatus · getLearningCheckpointStatus · getLearningMaps · getLearningTemperatureSettings · setLearningMinimumWaterC · getLearningToleranceSettings · setLearningToleranceSettings · resetLearningToleranceSettings · restartEngine · connectUsb · disconnectUsb · listUsbDevices · getFullEngineSnapshot · getLiveTelemetry · getEngineMetrics · runEngineSelfTests · runProtocolLab · startKMapRead · getKMapReadResult · previewKMapCell · getKWriteStatus · getKWriteHistory · recoverKInsertionState · previewKFactorPoint · getKFactorStatus · getKFactorHistory · getLearningSyncStatus · getSessionRecorderStatus · listRecordedSessions · setSessionRecorderSettings · startSessionRecording · stopSessionRecording · exportSession · exportSessionSummary · getLogs · exportLogs · exportData · recordUiUsage · setGpsEnabled · setCarInfoEnabled · getCarInfo · openAppSettings · registerRefuel · setGnvSettings`

### `app/src/main/java/com/omegas/prohub/web/V7JavascriptBridge.kt` (18)

`getState · listSessionFiles · getLastOperation · getWriteVerify · previewMapAdjustment · synchronizeAdvisorSuggestions · ingestLearningSnapshot · synchronizeFromEcu · applySuggestion · startCurveRead · startCurveBackup · listCurveBackups · startCurveRestorePrepare · startCurveReset · startCurveBatchWrite · startMapBatchWrite · saveSession · loadSession`

### `app/src/main/java/com/omegas/prohub/web/PowerJavascriptBridge.kt` (5)

`getBatteryOptimizationStatus · requestBatteryOptimizationExemption · getOverlayStatus · requestOverlayPermissionAndEnable · setOverlayEnabled`

## Próximos gates — não resolvidos

1. **BLOCKED para marcar inventário completo:** varrer controles criados dinamicamente em `screens/`, `components/`, `core/` e `app.js`; cruzar listeners/CTAs reais e chamadas `OmegasNative`, `OmegasV7`, `OmegasPower` para eliminar falsos positivos.
2. **PENDING:** associar cada CTA real a uma linha de `matriz-funcional.json` com classificação `ORIGINAL_OBSERVADA`, `ORIGINAL_SOMENTE_CODIGO`, `APP_LOCAL`, `INVESTIGAR` ou `PROIBIDA`; exigir origem, comando/resposta, escalas, trava, ponte, estado de interface e teste. Não criar escrita nem UI por causa de método listado.
3. **BLOQUEIO_DE_DECISÃO:** semântica de “Gravado” do Mapa K após ACK/saída de K insertion versus readback. Preservar código e autocorreção existentes até autorização expressa; ACK **não** é readback nem prova física.
4. **VALIDACAO_FISICA_PENDENTE:** nenhuma função de escrita foi testada na central real nesta rodada; hardware e consentimento exigidos. Não gerar APK, não fazer merge.
5. **PENDING / RED→GREEN:** automatizar comparação de inventário de botões/bridges com o código para detectar novos métodos/controles sem rastreio; a suíte atual verifica integridade das 12 linhas, mas não completude.

## Estado de execução

**DONE:** coleta estática de `index.html` e das três pontes listadas, com contagens reproduzíveis e sem mutação de comportamento. **PARTIAL:** Task 3 Step 1; falta inventário de ações JS dinâmicas e mapeamento funcional. **BLOCKED:** alteração ACK/readback e homologação física. O CI do commit que introduz este documento deve ser verificado no SHA exato antes de declarar integração aprovada.
