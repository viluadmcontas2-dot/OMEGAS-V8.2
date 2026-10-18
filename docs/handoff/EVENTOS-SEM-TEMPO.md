# Eventos sem tempo (2026-10-18)

Regra do dono: nenhuma decisão do app depende de relógio. Esperar a ECU é esperar a RESPOSTA.

## Gatilhos (evento -> ação)
| Evento | Ação |
|---|---|
| Sessão USB nova + primeiro quadro válido | AUTO_START: lê curva/mapa/param_snapshot uma vez (núcleo livre) |
| Quadro válido seguinte após falha de leitura | Nova tentativa (até 4 por evento); contada por quadro, não por segundo |
| AutoMatch detectado (mudança de contagem) | Enfileira releitura de curva/mapa |
| Curva/mapa alterado pela ECU (MUL_ACT diferente) | Enfileira releitura e registra origem |
| Escrita confirmada (ACK + readback) | **Não** enfileira releitura: a gravação já termina com leitura de volta e atualiza o cache. `KWriteManager.readFullMap` chama o mesmo `onConfirmedWrite` ao fim de toda leitura; pedir releitura ali gerava laço infinito (`CalibrationReadEvents` ignora `WRITE_CONFIRMED`; teste `CalibrationReadEventsTest`) |
| Fim da leitura automática (AUTO_START/AutoMatch/D1) | Leitura pedida pela tela (Mapa K/Curva K) prossegue; até lá ela espera (`AutoReadGate`), sem prazo e sem "ocupado". Da espera até o fim da leitura da tela a serial fica reservada (`AutoReadGate.forUi`/`uiReadPending`): quadro válido nesse meio não abre nova leitura automática (fica pendente para o próximo quadro) |
| A cada `AUTOCAL_TICK_FRAMES` = 4 quadros válidos aceitos | Sonda do AutoCal (`NativeAutoCalMonitor.tick`, uma transação `CMD_NATIVE_STATUS`). Conta: antes era 1 por segundo; a telemetria anda a ~4 quadros/s, então 4 quadros ≈ o ritmo antigo, sem relógio e sem roubar a serial a cada quadro |
| A cada `FULL_SNAPSHOT_FRAMES` = 12 quadros válidos (≈ os 3 s antigos) e em cada `healthTick` | `full_snapshot` na sessão gravada |
| Borda ocupado → livre (`KWriteManager`/`KFactorManager`) | `healthTick` (reinício do núcleo que esperava a calibração) e D1 que perdeu a serial (até 3 tentativas por AutoMatch; a época só é gasta quando a curva viva foi lida). Se a borda já passou quando o D1 guarda o pendente (o próprio `readCurve` do D1 gera a borda), ele roda na hora (`AutoRefineRetry`), dentro do mesmo limite de 3 |
| Primeiro quadro válido | Devolve o saldo de reinícios automáticos do núcleo (máx. 3 por sessão USB sem quadro válido entre eles) |
| USB passa a desconectado | Esperas JS sem resposta final terminam (o status é lido antes: resposta final que chegou junto com a queda vale) (sonda, MapFix, Refino, Curva K → Mapa K); sonda armada desarma (também em outro toque e na troca de rota) |
| Rota Mapa K + amostra de status da bridge / fim da leitura do mapa | Sugestão do Mapa K redesenhada (antes só durante leitura/gravação) pela ponte barata `getMapSuggestion` (memorizada por revisão + sessão + arquivo do mapa), não pela `getEquivalence` inteira |
| Toque do operador | Única origem de gravação na ECU; o app só sugere |
| Reconexão da ECU (USB passa a online) | Telas Curva/Mapa chamam autoSync uma vez |
| Entrar na aba Ferramentas / gravação começa ou para / `omegas-refresh` | Relista gravações |
| Mudança de rota, toque, aba oculta | Envia contagens de uso (usage-meter) |
| `omegas-app-ready` | Módulos de tela iniciam (sem espera por polling) |
| Amostra da bridge (hook `fast`) | Operações JS pendentes (MapFix, Refino, Curva->Mapa, sonda) verificam a resposta |
| Mudança de estado USB / criação do serviço / queda do núcleo / borda ocupado→livre | `healthTick` (reconexão, reinício do motor limitado por contagem, snapshot) |
| Sonda de escrita: leitura de conferência | 1 readback inicial + 3 conferências; entre cada uma, `FRAMES_BETWEEN_READS` = 4 quadros de telemetria novos (pedido `48 01` dentro da unidade da sonda): a ECU roda o próprio ciclo e pode sobrescrever (`ACCEPTED_THEN_OVERWRITTEN` passa a ser detectável) |

Limite conhecido: mudança externa de Mapa K não tem notificação própria no monitor; não foi criado polling.

## Exceções inerentes (fluxo de dados ou UX visual, não decisão)
| Onde | Por quê |
|---|---|
| `ui/core/scheduler.js` `setInterval` | Bomba de amostragem da bridge (fluxo de dados da telemetria); todo o resto pega carona nos seus ganchos |
| `ui/app.js` `setTimeout(task, 0)` / rAF | Ceder o quadro de desenho; sem atraso |
| `ui/app.js` `toastTimer` | Esconder o aviso (visual) |
| `ui/screens/curve.js` rAF/`setTimeout(16)` | Animação de desenho |
| `ui/screens/refino.js`, `autocal-cockpit.js` `setTimeout(...,0)` | Adiar para o fim da pilha; sem atraso |
| `UsbSerialManager`, `ResponseDrivenEcuEngine`, `NativeRuntimeManager`, `AutoCalNativeActionManager` (sleep/schedule) | Protocolo serial; proibido alterar (regra 3) |
| `RealtimeLearningBuffer`, `LatestOnlyBackgroundPipeline`, `OrderedBackgroundPipeline`, `CoalescedSnapshotWriter`, `MotorLearningMemory` (`wait`/`awaitTermination`) | Filas de gravação em segundo plano; encerramento |
| `TelemetryForegroundService` `currentTimeMillis` | Só carimbo de data e limite de redesenho da notificação, sem decisão |

## Inventário de constantes de tempo (varredura de `tests/test_sem_tempo_como_gatilho.py`)
Toda constante (ou literal) `Long` comparada num arquivo que lê relógio aparece aqui como `Arquivo.kt` `NOME`; renomear exige atualizar esta lista.

| Constante | Classificação |
|---|---|
| `EquivalenceLedger.kt` `SAVE_INTERVAL_MS` | Exceção: limita a gravação do arquivo (E/S), não decide nada |
| `RefinementJournal.kt` `SAVE_EVERY_MS` | Exceção: limita a gravação do arquivo (E/S) |
| `RefinementAutopilot.kt` `60_000L` | Exceção: limita a gravação do arquivo (E/S) |
| `SessionRecorder.kt` `DOCUMENTS_MIRROR_INTERVAL_MS` | Exceção: espelho da sessão em Documentos (E/S) |
| `SessionRecorder.kt` `DURABLE_EVENT_MIN_GAP_MS` | Exceção: espelho da sessão em Documentos (E/S) |
| `RecordingSerialScheduler.kt` `FLUSH_MS` | Exceção: resumo das leituras na trilha da sessão (registro) |
| `TelemetryForegroundService.kt` `900L` | Exceção: limite de redesenho da notificação (visual) |
| `TelemetryOverlayController.kt` `250L` | Exceção: limite de redesenho do overlay (visual) |
| `MotorSampleAnalyzer.kt` `1L` | Não é tempo: contagem de quadros recebidos (falso positivo aceito da varredura) |
| `NativeAutoCalMonitor.kt` `SESSION_SETTLE_MS` | TEMPO-A-REMOVER (pendente): `NativeAutoCalMonitor.kt:201`, espera 8 s após a sessão antes da primeira sonda. Troca segura: N ticks (cada tick já é 4 quadros válidos); não feita porque `tools/jvm/AquisicaoEmulada.kt` (emulador do monitor real) simula essa espera pelo relógio — precisa ajustar e rodar o emulador junto |
| `NativeAutoCalRefreshPlanner.kt` `ACQUISITION_INTERVAL_MS` | TEMPO-A-REMOVER (pendente): `NativeAutoCalRefreshPlanner.kt:50`, espelho a 1 Hz das bandas do AutoCal; trocar por "a cada N ticks" |
| `NativeAutoCalRefreshPlanner.kt` `REFERENCE_INTERVAL_MS` | TEMPO-A-REMOVER (pendente): `NativeAutoCalRefreshPlanner.kt:51`, referência a cada 4 s; trocar por "a cada N ticks" |
| `RefinementCycle.kt` `ANALYSIS_MIN_INTERVAL_MS` | TEMPO-A-REMOVER (pendente): `RefinementCycle.kt:189`, análise vale 15 s mesmo com revisão nova; trocar por invalidação só por revisão (como o ledger) exige medir o custo do recálculo no aparelho |
| `EquivalenceLedger.kt` `STABLE_WINDOW_MS` | TEMPO-A-REMOVER (pendente): `EquivalenceLedger.kt:305`, 3 quadros só formam leitura estável dentro da janela de tempo; trocar por "3 quadros consecutivos sem lacuna de sequência" exige número de sequência no quadro |
| `AutoCalSnapshot.kt` `MAX_FIELD_SKEW_MS` | TEMPO-A-REMOVER (pendente): `AutoCalSnapshot.kt:259`, retrato inválido se os campos foram lidos com diferença maior que o limite; trocar por "mesma unidade serial / mesma contagem de AutoMatch" |

Removidos nesta rodada: `EquivalenceLedger.kt` `EVIDENCE_CACHE_MS` (cache de pares agora só por revisão: mesmo retrato ⇒ mesma saída), `RefinementAutopilot.kt`/`RefinementJournal.kt` `MAX_TICK_MS` (não usados).

## Pendências fora do escopo (anotadas, não editadas)
- Curva K → Mapa K / reconexão: a leitura da tela agora espera a leitura automática terminar (`AutoReadGate` no serviço); continua possível o caso inverso (a tela começou primeiro e a leitura automática acha ocupado): ela fica pendente e tenta no próximo quadro válido (até 4). Gravações pedidas pela tela durante uma leitura automática ainda recebem "ocupado" (não esperam, para não travar a thread da ponte).
- Núcleo que não sobe (`runtime.start()` falso) ou USB sem permissão: nova tentativa só no próximo evento (mudança de USB, borda ocupado→livre, reinício manual); não há relógio de nova tentativa.
- `MainActivity.kt` `postDelayed(..., 800L)` ao abrir rota pela notificação.
- `web/V7JavascriptBridge.kt` `Thread.sleep(80L)` em esperas por estado de gravação.
- `NativeAutoCalRefreshPlanner` (`ACQUISITION_INTERVAL_MS`): espelho a 1 Hz do AutoCal.
