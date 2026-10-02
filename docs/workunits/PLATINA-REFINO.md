# Platina + Refino
Base remota: b185e80ab68e203e65a79806983bf4210310582c (OmegasPlatina).
Fonte autorizada: 63ba6c37881a97c7d8a2064ac7e11f7779e4300f (somente cérebro/testes/oráculos).
Nenhum commit na branch fonte. Sem PR. PHYSICAL_VALIDATION=false.

## Investigação antes do porte
Foram lidos AGENTS, PROJECT, STATUS, router, index, app, cockpit e CSS AutoCal, bridge, monitor, análise, rascunho, serviço, protocolo, gate e workflows da Platina. Gate da base: QUALITY_GATE_FAST=PASS; testes dependentes de kotlinc foram explicitamente pulados na base.

| Caminho existente | Quem chama / função | Thread e frequência |
|---|---|---|
| ui/index.html → core/router.js | toque data-route → Router.navigate → Store.patch; restore usa localStorage | WebView, somente navegação |
| ui/app.js | activateRoute → AutoCalCockpit.enter/refresh após paint | WebView, entrada/visibilidade/evento |
| core/scheduler.js → app.js | refreshFast → NativeApi.presentSnapshot; refreshStatus → status/obd; refreshContext → scienceSnapshotSince | WebView, fast 200 ms (50 ms AutoCal), status 1 s, contexto 2 s |
| screens/autocal-cockpit.js | Scheduler status → refresh → AutoCalApi.projection → bridge.getUiProjection → AutoCalUiProjection.project | WebView/bridge síncrono, status 1 s; cursor no fast |
| autocal/AutoCalJavascriptBridge.kt | projeção escolhe fonte atual nativa/manual; prepare/execute → NativeAutoCalManager | bridge WebView; comando manual assíncrono |
| service/TelemetryForegroundService.kt | onCreate agenda healthTick e autoCalTick → NativeAutoCalMonitor.tick | omegas-native-service, 3 s/1 s, fixed delay |
| autocal/NativeAutoCalMonitor.kt | tick → probe; acquisition/reference refresh em serial.unit; readFullSnapshot cerca contador antes/depois | serviço/serial único; aquisição ~1 s, referência ~4 s; dump por evento |
| ecu/AutoCalProtocol.kt | read/decode → AutoCalSnapshotBuilder → monitor.latestSnapshotJson → projeção → renderReferenceChart | serial MP48 único; sem nova autoridade USB |
| ecu/NativeRuntimeManager.kt → serviço | evento → telemetryStore.updateFromEngineEvent → consumeEngineEvent → estado/sessões | callback nativo por quadro aceito |
| ui/screens/curve.js | onEnter → readCurve → prepareSuggestion → previewCurvePoint → proposals → writePrepared | toque manual/fast só enquanto lendo/escrevendo |
| service/TelemetryForegroundService.kt | startKFactorWrite → kFactor.startBatchWrite → ACK/readback → onConfirmedBatch | writer serial; apenas confirmação humana |
| service/TelemetryForegroundService.kt | startKBatchWrite → kWriter → ACK/readback → onConfirmedBatch | writer serial; apenas confirmação humana |
| monitor | onNativeAutoMatchObserved / onNativeCalibrationObserved → serviço/runtime | evento de contador / mudança confirmada; sem escrita do app |

## APIs comparadas
AutoCalScale: injeção 512, MAP S16LE/1024, MUL_ACT Q14/16384; iguais aos valores congelados do motor.
AutoCalFieldStatus.VALID, campos rawValues/physicalValues/capturedAtMs e grupos AUTOMATCH_CURVES/ACQUISITION_CURRENT existem.
Protocolos: eixo e MUL_ACT 30; aquisição 18; MAX_AUTOMATCH indexed 0x0165:2; contador compacto/fallback 0x0174.
Não se substituem bridge, serviço ou monitor por arquivos da fonte. analyzeRefined é adição; createRefined usa create da Platina preservando o comportamento antigo.
PresentationMedian e TypicalInjectionBands são dependências puras necessárias do ledger; não são interface.
tools/run_checks.py descobre automaticamente test_*.py e ui/*.test.cjs; os novos testes entram por descoberta.

## Causas e fronteiras
- Cota AutoMatch atingida não significa aquisição encerrada; a Platina distingue AUTO_CAL_ENABLE e contador. Apresentação do Refino deve manter esta verdade mesmo que o piloto portado tenha texto antigo.
- Ledger.resetGas preserva janela de três quadros: integração deve limpar janela com Frame UNKNOWN antes de reset, sem mudar matemática.
- Reinício/troca USB não pode juntar GNV antigo à curva atual. Integrador deve invalidar cache/propostas e interromper experimento antes de coletar novamente.
- CoherenceGroups do snapshot completo podem ficar antigos após merge operacional. Refino precisa validar timestamps dos campos realmente consumidos e geração nativa, sem modificar monitor/projeção existentes.
- Os 30 pontos do solver têm custo fixo; crescimento da condução é limitado pelo ledger. Pareamento e ciência ficam fora da WebView e do bridge síncrono.
- Gráfico será renderReferenceChart da Platina com host opcional, estado isolado; nenhuma UI/CSS/JS da fonte é portável.

## Entrega em blocos
1. Cérebro exato + auxiliares puros/testes/oráculos/fixtures; análise e rascunho aditivos.
2. Integração em worker dedicado, revisão manual usando writer Platina e cercas de sessão/evidência.
3. Uma aba Refino após AutoCal; mesmo componente de gráfico, CSS novo restrito ao novo destino.
4. Gate/JVM/paridade/evidência visual e único workflow verde-apk-now.yml no SHA final.
Nenhuma conclusão sobre dirigibilidade, consumo ou igualdade no veículo pode ser provada por replay.
