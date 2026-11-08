# A2 — Auditoria do fluxo de Conexão (2026-10-27, branch claude/omega-aura)

Só leitura. Caminhos relativos a `app/src/main/java/com/omegas/prohub/` (K) e `app/src/main/assets/ui/` (JS). "inferido" = não verifiquei rodando.

## 0. Mapa curto do caminho (do app à telemetria)
1. `AndroidManifest.xml:50-53` filtra `USB_DEVICE_ATTACHED` (10C4:EA60, `usb/OmegasUsbIdentity.kt`). O Android abre a `MainActivity` ao plugar o cabo.
2. `MainActivity.kt:162` (ao ligar no serviço) e `:236` (`onNewIntent`) chamam `service.connectUsb()` quando a intenção é ATTACHED.
3. O serviço em primeiro plano (`service/TelemetryForegroundService.kt:180 onCreate`) chama `startForegroundCompat()` (`:370`) e `usb.connect()` se `autoConnectUsb` (`:378`). `onStartCommand` devolve `START_STICKY` (`:398`); o caso padrão tenta `usb.connect()` (`:395`).
4. `usb/UsbSerialManager.kt:161 connect()`: escolhe o aparelho (prefere `settings.preferredDeviceName`); sem permissão chama `requestPermission` (`:196`); com permissão, `open()` (`:205`) cria um novo `connectionSessionId`.
5. `healthTick()` (`:935`) chama `handleUsbTransition()` (`:796`): CONNECTED/GENERATION_CHANGED zera Store, AutoCal, runtime, writers e inicia gravador de sessão e núcleo (`startEngine`, `:845`). O núcleo faz o handshake em `ecu/ResponseDrivenEcuEngine.kt:356` (estado HANDSHAKE; falhas com espera de 250 ms até 5 s, aviso forte após 20 falhas, `:35-37`, `:264`).
6. A UI enxerga só `status.usbConnected` (`app.js:154-163`): "Central conectada" / "Sem central". Não há tela de Conectar hoje (`grep Conectar` em `screens/` e `components/` não acha).

## 1. Estados e transições (hoje)
| Estado | Evento | Próximo estado | O que o usuário vê |
|---|---|---|---|
| Sem cabo | app abre | espera (`usb.connect` loga "Aguardando interface OMEGAS 10C4:EA60", `UsbSerialManager.kt:177`) | "Sem central" (`app.js:163`); nada explica o que fazer |
| Sem cabo | cabo plugado (ATTACHED) | pedindo permissão ou abrindo | nada próprio; só o diálogo do Android |
| Pedindo permissão | usuário aceita | `open()` → conectado | "Central conectada" |
| Pedindo permissão | usuário nega | NEGADA: sem retry automático até desplugar/replugar ou toque manual (`:94-97`, `:186`) | "Sem central", sem motivo na tela (inferido: o motivo só vai ao log e a `usbPermissionPending`) |
| Conectado | handshake falha / ECU recusa (CA 01 10) | núcleo trava ("retry bloqueado até nova sessão USB", `ResponseDrivenEcuEngine.kt:615`); `restartBudget` permite no máx. 3 reinícios sem quadro (`ServiceEventPolicy.kt:19`) | "Central conectada" mesmo sem dados (o selo só olha o cabo) |
| Conectado | erro de I/O (`onRunError`, `:448`) | `beginTransientRecovery` → `UsbRecoveryPolicy` sempre HARD_DISCONNECT (`UsbRecoveryPolicy.kt:36`) → desconecta e agenda reconexão se há aparelho e `autoReconnectUsb` | "Sem central" e volta sozinho |
| Conectado | cabo arrancado (DETACHED) | `hardDisconnect(scheduleReconnect=false)` (`:105`) | "Sem central"; Store invalidado (`TelemetryForegroundService.kt:853`) |
| Desconectado, aparelho presente | `scheduleReconnect` espera 3 s e chama `connect()` (`:546-551`) e/ou `healthTick` chama `connectUsb()` (`:942`) | pedindo permissão/abrindo | sem aviso de "reconectando" |
| Desconectado | `monitoringPausedByUser` ou `!autoReconnectUsb` | `stopSelf()` (`:857`) — o serviço morre | tela sem central; nenhuma notificação de retomada (inferido) |
| Desconectado | usuário toca desconectar | `disconnectUsb()` (`:506`): pausa monitoramento, `stopSelf()` | "Sem central" |
| Reconectado | nova geração | `GENERATION_CHANGED`: encerra gravação da sessão, invalida tudo, recomeça (`:813-832`) | `app.js:267` relê Mapa/Curva sozinho (só nas abas `curve`/`map`) |
| Ignição desliga / central reinicia | a porta serial falha ou some | igual a erro de I/O ou DETACHED (inferido; não há tratamento específico) | "Sem central" |
| App fechado / serviço morto | `START_STICKY` reinicia o serviço; `onCreate` reconecta (`:378`) | conecta de novo | só ao reabrir a tela |

Persistido hoje: `AppSettings` (prefs: `autoConnectUsb`, `autoReconnectUsb`, `preferredDeviceName`, `settings/AppSettings.kt:43-48`); gravador de sessão (arquivos, `diagnostics/SessionRecorder.kt`); backups da Curva K (`calibration/KFactorBackupRetention.kt`, `storage/AppPaths.kt:11`); rota atual da UI (`localStorage`, `ui/core/router.js:19`). NÃO persistido: qualquer plano de gravação em etapas (ver §2-L1).

## 2. Lacunas contra "automático, persistente, recuperável"
- **L1 (grave) — Gravação em etapas vive só na memória do WebView.** `this.curvePlan` (`ui/screens/map.js:313`, `:336`) guarda o plano "Curva → Mapa K". Se o app fechar, o WebView recarregar ou o celular reiniciar depois do Mapa K gravado e antes da Curva K, o plano some e o botão "Concluir curva" (`map.js:365`, `index.html:54`) some junto. O próprio comentário do código diz que a correção fica "em dobro" até a curva ser gravada. A regra 6 do contrato NÃO é cumprida hoje. Estado: o `balance-runner.js` só lê "cabo soltou" e devolve erro (`:20`), não retoma.
- **L2 (grave) — "Interrompido" só existe como texto de erro.** `refino.js:543`, `balance-runner.js:20` e `map.js:656` dizem "Confira a Curva K ao reconectar", mas ninguém lembra isso após reconectar. Não há registro de intenção/marco no disco nem na ponte.
- **L3 — Tela de primeiro uso/Conectar não existe.** Só há o selo "Sem central / Central conectada" (`app.js:163`). Permissão negada, cabo errado, USB sem aparelho compatível e handshake travado aparecem iguais ("Sem central"). Falta passo seguinte (contrato, regra 5).
- **L4 — "Conectado" não quer dizer "falando com a central".** `usbConnected` vem do cabo, não do handshake/quadros válidos. Núcleo travado (`runtime.stuck`) aparece como conectado. Falta um estado intermediário "cabo ok, central não responde".
- **L5 — Reconexão mistura relógio e polling.** `scheduleReconnect` usa 3 s fixos (`UsbSerialManager.kt:551`), contra a regra "sem relógio". Sem contagem de tentativas nem motivo registrado; `UsbRecoveryPolicy` ignora `attempt` (`:30-34`). Não há ciência de ignição vs cabo vs central reiniciando.
- **L6 — Permissão negada trava em silêncio.** Retry suspenso até replugar ou toque (`:186`); o toque manual existe só como `connectUsb(userInitiated=true)` no botão da notificação (`:395`). Nenhuma tela chama isso (inferido: `grep` em `ui/` por permissão acha só `native-api.js:183`, demo).
- **L7 — `stopSelf()` ao soltar o cabo com pausa do usuário** (`:857`) mata o serviço: depois disso só o ATTACHED do Android ou o usuário religam. Aceitável para "Desconectar", mas é o mesmo caminho se `autoReconnectUsb` estiver falso.
- **L8 — Sem registro do porquê da queda.** O motivo vai ao log (`log.add`) e a `noteLifecycle` do gravador, não à UI nem a um arquivo de estado.
- **L9 — Rota de abertura por ATTACHED.** `MainActivity.kt:162` testa a intenção original e `connectUsb()` sem `userInitiated`; a primeira vez sem permissão cai no diálogo do Android sem explicação (inferido).

## 3. Proposta mínima
### 3.1 SupervisorDeConexao (classe nova, Kotlin, sem tocar as protegidas)
Arquivo novo: `service/ConnectionSupervisor.kt` + `service/ConnectionPhase.kt` (enum) + política pura `service/ConnectionPolicy.kt` (testável na JVM, sem relógio).
- Entrada (API pública já existente): `usb.connected`, `usb.connectionSessionId`, `usb.hasCompatibleDevice()`, `usb.permissionDeniedDeviceName`, `usb.permissionPending`, `runtime.running/stuck`, quadros válidos (`ServiceEventPolicy`/`restartBudget`), `kWriter.isBusy()`, `kFactor.isBusy()`.
- Saída: uma fase (`SEM_CABO`, `PEDINDO_PERMISSAO`, `PERMISSAO_NEGADA`, `ABRINDO`, `HANDSHAKE`, `AO_VIVO`, `RECONECTANDO`, `CABO_OK_CENTRAL_MUDA`, `PROBLEMA`) + `motivo` + `proximoPasso` + `tentativas` (contagem) em JSON para a ponte (ex.: campo `connection` no status).
- Critério de reconexão por evidência: reconectar a cada borda (`DETACHED→ATTACHED`, `onRunError`, quadro inválido repetido), com orçamento por contagem de leituras sem quadro válido (reusa `EngineRestartBudget`), nunca por minutos. Ao esgotar, fase PROBLEMA com próximo passo ("Tire e recoloque o cabo" / "Ligue a ignição").
- Chamadas: só `connectUsb(userInitiated=...)`, `startEngine`, `restartEngine` do serviço (já públicas) e `usb.connect(allowPermissionRetry=true)` quando o usuário tocar "Tentar de novo".
- Classes existentes tocadas (mínimo): `TelemetryForegroundService.kt` (instanciar o supervisor, alimentar em `healthTick` e `handleUsbTransition`, expor JSON; NÃO é protegida); `model/HubStatus.kt` (campo novo); `web/HubJavascriptBridge.kt` (expor); `MainActivity.kt` (ATTACHED passa por ele). **`UsbSerialManager` fica intocada**, inclusive o `scheduleReconnect` de 3 s (L5): o supervisor assume a decisão e ele permanece como está (protegida; qualquer remoção do timer exige o dono).

### 3.2 DiarioDeIntencao (classe nova, Kotlin, + JS fino)
Arquivo novo: `storage/WriteJournal.kt` (JSON em `AppPaths`, escrita atômica `.tmp` → rename) e `ui/core/diario-gravacao.js` (via ponte).
- Fluxo: `abrir(id, etapas[], plano, base)` ANTES da primeira gravação → `marcar(id, etapa, "concluida", readbackOk)` DEPOIS do ACK+readback de cada etapa → `fechar(id)`. Uma entrada aberta no boot = gravação interrompida.
- Plano guardado: nós da curva (`index, currentRaw, targetRaw`) e o resumo do Mapa K. Ao retomar, o `writeCurveStage` já relê e trata "curva já no alvo" como concluída (`balance-runner.js:47`) e recusa se a curva mudou (`stale`, `:48`): a retomada é segura por construção.
- Tocar: `map.js` (`curvePlan` passa a ler/gravar no diário; `renderCurveStage` mostra "Concluir" também após reabrir), `balance-runner.js` (nada além de reusar), `refino.js` (mesmo para a etapa do mapa do Refino, `:594`). Kotlin: ponte nova (`HubJavascriptBridge.kt`, `AppPaths.kt` para o caminho). **Não toca** `KWriteManager`, `KFactorManager` nem `AutoCalNativeActionManager` (protegidas): o diário fica acima, na camada que as chama. Escritas em si não mudam.
- Regras: nunca retomar sozinho a gravação (invariante "sem escrita automática"); o app só OFERECE "Concluir" e o toque do operador dispara a etapa. Ao oferecer, relê o estado da central (nunca valor guardado).

## 4. Testes a escrever primeiro (RED)
Kotlin (JVM, `app/src/test/java/com/omegas/prohub/service/`):
1. `ConnectionPolicyTest`: tabela fase×evento da §1 (permissão negada não tenta sozinha; ATTACHED limpa a negada; cabo ok + núcleo travado = `CABO_OK_CENTRAL_MUDA`, nunca `AO_VIVO`).
2. `ConnectionPolicyTest`: orçamento por contagem — N leituras sem quadro válido esgotam e vão a `PROBLEMA` com próximo passo; um quadro válido zera.
3. `ConnectionPolicyTest`: nenhuma API de tempo (`System.currentTimeMillis`, `delay`) no arquivo (como `ServiceEventPolicy`).
4. `WriteJournalTest`: abrir→marcar→fechar; arquivo truncado/corrompido (`.tmp` sobrando) não derruba e vira "sem pendência"; escrita atômica.
5. `WriteJournalTest`: simular morte entre etapa 1 e 2 → ao reabrir `pendentes()` devolve a etapa 2 com o plano.
6. `ConnectionSupervisorRecoveryTest`: queda no meio de gravação com diário aberto → ao voltar a `AO_VIVO`, oferta "Concluir"; sem reler a central não oferece.
JS (`tests/ui/`):
7. `conectar-estados.test.cjs`: cada fase renderiza texto + botão fixo ("Conectar"/"Tentar de novo"); o botão não some nem muda de lugar (contrato regra 1/4).
8. `curva-para-mapa-retomada.test.cjs`: com `curvePlan` vindo do diário, "Concluir curva" aparece após recarregar a tela; completar apaga a pendência; curva já no alvo fecha sem gravar. (Estende `curva-para-mapa-concluir-curva.test.cjs`, hoje só cobre memória.)
9. `cabo-solto-meio-gravacao.test.cjs`: `waitFor` devolve erro e o diário mantém a etapa 2 pendente (hoje só o erro, `balance-runner.js:20`).
10. Contrato: um teste de arquitetura (padrão `ArchitectureContractsTest.kt`) falhando se `ConnectionSupervisor` importar `UsbSerialManager` internals ou se alguém editar bytes de comando.

Testes que travam o comportamento atual (manter verdes): `UsbPoliciesTest.kt` (HARD_DISCONNECT), `UsbSessionTransitionPolicyTest.kt`, `ServiceEventPolicyTest.kt`, `curva-para-mapa-concluir-curva.test.cjs`, `curva-k-releitura-e-rodape.test.cjs`, `ResetNuncaPausaAprendizadoTest.kt`.

## 5. Estados de tela do mockup Conectar (01, 01b–01d)
Um só leiaute: ícone da central + título + 1 linha + botão fixo no fundo (mesmo lugar, texto muda) + "Ver como funciona".
1. **Primeira vez**: "Ligue a central ao celular" · passos 1-2-3 (cabo, ignição, permitir USB) · botão "Conectar" · "Ver demonstração".
2. **Procurando**: "Procurando a central…" · mostra o que a leitura vê ("Cabo detectado" / "Sem cabo") · botão cinza "Procurando" (motivo dentro).
3. **Pedindo permissão** (novo): "Toque em Permitir na janela do Android" · se negada: "Sem permissão. Sem ela não dá para ler" · botão "Pedir de novo".
4. **Conectada (ao vivo)**: selo "Central conectada" + barra do topo normal (tensão, MAP, RPM) · sem botão de conectar; o botão fixo vira "Ver o AutoCal".
5. **Reconectando**: "Cabo solto. Tentando de novo" · contagem de tentativas · nada de gravação mexe · botão cinza "Reconectando…" com "Parar".
6. **Retomada**: "Mapa K gravado. Curva K ainda não." · botão "Concluir curva" · "Deixar assim" (avisa que a correção fica em dobro) · "Ver detalhes".
7. **Problema com próximo passo**: texto por causa — sem permissão ("Permitir USB"), cabo sem central ("Ligue a ignição e tente"), central não responde ("Tire e recoloque o cabo") · botão "Tentar de novo" · "Ver detalhes" (log).
8. **Sem aparelho compatível**: "Esse cabo não é da OMEGAS (10C4:EA60)" · botão "Tentar de novo".
