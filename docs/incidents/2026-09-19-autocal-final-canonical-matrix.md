# AutoCal final — matriz canônica de controles e consumidores

Baseline de reconciliação: `1807b42c43ab084c2efb8c59a439aa7b270db4ee`. Esta matriz descreve o fluxo implementado para #57. GitHub remoto continua a autoridade. Nenhuma linha abaixo autoriza validação física ou escrita automática.

| Ação humana | Superfície | Handler JS | API / bridge | Autoridade nativa | Protocolo / efeito | Estado consumido pela UI | Feedback / teste |
|---|---|---|---|---|---|---|---|
| Abrir AutoCal | `data-route="autocal"` | Router / shell | — | — | somente navegação | Store.route | rota própria acima de OBD; runtime + product contract |
| Consultar ECU | `data-autocal-read` | `requestRead()` | `startRead()` → bridge `startRead()` | `AutoCalSnapshotManager` | lê `READ_ONLY_FIELDS` via scheduler MP48 | **readerStatus + readerSnapshot** | QUEUED/READING/READY/PARTIAL/FAILED/TIMEOUT |
| Cancelar leitura | `data-autocal-cancel-read` | `cancelRead()` | bridge `cancelRead()` | `AutoCalSnapshotManager.cancel()` | incrementa generation; não escreve ECU | reader state | CANCEL_REQUESTED → CANCELLED |
| Iniciar aquisição | CTA contextual | `prepare(ENABLE_AUTO_CAL)` | prepare → revisão → execute | `AutoCalNativeActionManager` | `12 4A 01 01 5E`; readback enable=1 | **acquisitionStatus + acquisitionSnapshot** | gate WebView + confirmação Android + receipt |
| Pausar aquisição | mesmo CTA | `prepare(DISABLE_AUTO_CAL)` | prepare → revisão → execute | `AutoCalNativeActionManager` | `12 4A 01 00 5D`; readback enable=0 | acquisition state | PAUSED após readback/monitor |
| Zoom − | `zoom-out` | `updateChartView` | — | — | visual apenas | chartView | sem write |
| Zoom + | `zoom-in` | `updateChartView` | — | — | visual apenas | chartView | sem write |
| Ver tudo | `fit` | `updateChartView` | — | — | visual apenas | chartView | linguagem não ambígua |
| Leitura anterior | `data-autocal-history` | toggle histórico | — | — | visual apenas | previousReferencePoints | não altera ECU |
| Selecionar ponto | `data-autocal-ref-index` | `inspectReferencePoint` | — | — | inspeção visual | pontos nativos | mostra Petrol Inj. e MAP |
| Cursor AGORA | `autocal-live-layer` | `renderLiveCursor()` | PresentSnapshot global | telemetria MP48 | nenhum write/persist | Store.telemetry | efêmero; some quando telemetria fica inválida |
| Selecionar região | `data-autocal-band-index` | `inspectBand` | — | maturity/snapshot nativo | nenhum write | counters + zonas + eventos | superfície humana sem Bxx/contador/limiar |
| Detalhes técnicos | `autocalTechnicalDetails` | disclosure | — | snapshots | leitura apenas | RAW/hash/eventos | Bxx/contador/threshold sob demanda |
| Reset gasolina | `RESET_PETROL` | bloqueado | bridge rejeita antes de prepare | `AutoCalNativeActionManager` | identidade original `02 24 04 01 2B`; efeito seletivo não observado nos Portmons fornecidos | — | interlock destrutivo ativo até validação física específica |
| Reset GNV | `RESET_GAS` | bloqueado | bridge rejeita antes de prepare | `AutoCalNativeActionManager` | identidade original `02 24 04 02 2C`; efeito seletivo não observado nos Portmons fornecidos | — | interlock destrutivo ativo até validação física específica |

## Autoridades separadas

1. **Leitura manual:** `AutoCalSnapshotManager`. Quando a UI inicia `startRead()`, acompanha `getStatus()/getSnapshot()`.
2. **Aquisição nativa contínua:** `NativeAutoCalMonitor`. Enable/pause e estado operacional vêm de `getNativeMonitorStatus()/getNativeMonitorSnapshot()`.
3. **Telemetria AGORA:** Store global alimentado por PresentSnapshot. O cursor nunca vira evidência adquirida.
4. **Dados adquiridos:** snapshot nativo, contadores, zonas e eventos de maturidade. Não se inventa porcentagem.
5. **Escrita:** somente `AutoCalNativeActionManager`, com prepare, revisão WebView, confirmação Android, ACK/readback e receipt.

## Invariantes

- AutoMatch manual (`0x08`) e RESET_ALL (`0x04`) não são expostos.
- RESET_PETROL (`0x01`) e RESET_GAS (`0x02`) estão temporariamente intertravados na UI/bridge; identidade de comando não equivale a efeito físico seletivo já validado.
- Snapshot manual antigo nunca decide enable/pause.
- Falha/timeout não vira READY silencioso.
- Telemetria inválida remove o cursor AGORA anterior.
- Contador/limiar/Bxx ficam no diagnóstico técnico.
- QA automatizado não chama `executeNativeAction`; writes devem permanecer zero.
