# Eventos sem tempo (2026-10-18)

Regra do dono: nenhuma decisão do app depende de relógio. Esperar a ECU é esperar a RESPOSTA.

## Gatilhos (evento -> ação)
| Evento | Ação |
|---|---|
| Sessão USB nova + primeiro quadro válido | AUTO_START: lê curva/mapa/param_snapshot uma vez (núcleo livre) |
| Quadro válido seguinte após falha de leitura | Nova tentativa (até 4 por evento); contada por quadro, não por segundo |
| AutoMatch detectado (mudança de contagem) | Enfileira releitura de curva/mapa |
| Curva/mapa alterado pela ECU (MUL_ACT diferente) | Enfileira releitura e registra origem |
| Escrita confirmada (ACK + readback) | Enfileira releitura |
| Toque do operador | Única origem de gravação na ECU; o app só sugere |
| Reconexão da ECU (USB passa a online) | Telas Curva/Mapa chamam autoSync uma vez |
| Entrar na aba Ferramentas / gravação começa ou para / `omegas-refresh` | Relista gravações |
| Mudança de rota, toque, aba oculta | Envia contagens de uso (usage-meter) |
| `omegas-app-ready` | Módulos de tela iniciam (sem espera por polling) |
| Amostra da bridge (hook `fast`) | Operações JS pendentes (MapFix, Refino, Curva->Mapa, sonda) verificam a resposta |
| Mudança de estado USB / criação do serviço / queda do núcleo | `healthTick` (reconexão, reinício do motor, snapshot) |
| Sonda de escrita: leitura de conferência | 1 readback inicial + 3 consecutivos; cada um é um round-trip da ECU |

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

## Pendências fora do escopo (anotadas, não editadas)
- `MainActivity.kt` `postDelayed(..., 800L)` ao abrir rota pela notificação.
- `web/V7JavascriptBridge.kt` `Thread.sleep(80L)` em esperas por estado de gravação.
- `NativeAutoCalRefreshPlanner` (`ACQUISITION_INTERVAL_MS`): espelho a 1 Hz do AutoCal.
