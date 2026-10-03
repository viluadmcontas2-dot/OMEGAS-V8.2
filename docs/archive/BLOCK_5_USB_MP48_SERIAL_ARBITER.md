# Bloco 05 — USB, MP48 e árbitro serial — micropassos 041–050

Branch operacional: `work/v8.2-clean`.

## Evidência de implementação

- **041** — `ResponseDrivenEcuEngine` permanece a autoridade única da sessão MP48; `Mp48SerialScheduler` é a fronteira entregue aos clientes.
- **042** — classes semânticas preservadas: `SAFETY`, `MANUAL_WRITE`, `READ_ONLY`.
- **043** — cada trabalho serial roda em executor único; `unit()` mantém sequências inseparáveis como write + readback sem preempção.
- **044 / 050** — leituras secundárias devolvem oportunidade à telemetria por `telemetryAfter`; o harness `test_mp48_serial_scheduler_behavior.py` cobre leitura/leitura e write+readback atômico.
- **045 / 046** — `connectionSessionId` é geração física. `true → true` com ID novo é `GENERATION_CHANGED` e o serviço invalida runtime, AutoCal e telemetria antes de iniciar Store/writers/AutoCal na geração nova. Falha de transporte agora é fail-closed: não é permitido reabrir fisicamente a porta preservando a geração lógica antiga.
- **047** — timeout/frame inválido/ACK divergente/sessão trocada continuam retornando falha; sessão física trocada é rejeitada por `expectedSessionId` antes e depois da transação.
- **048** — purge permanece restrito a fronteiras de sincronização controladas (abertura física/handshake/recuperação), não como rotina observacional por frame.
- **049** — a arquitetura já expõe `elapsedMs` por transação e métricas da engine, mas a ocupação serial real sob AutoCal + Mapa K + Curva K + telemetria exige medição física posterior; não é declarada como provada neste bloco.

## Delta deste bloco

Foi corrigida uma inconsistência concreta: a recuperação transitória podia fechar e reabrir a porta física mantendo `connected=true` e o mesmo `connectionSessionId`. Isso contrariava a regra de geração USB. A política passou a exigir hard disconnect; o auto-reconnect normal volta por `open()`, que cria um ID novo e aciona a invalidação integral já existente no serviço.

## Validação

- Provas estruturais existentes: `tests/test_mp48_serial_scheduler_contract.py`, `tests/test_mp48_serial_scheduler_behavior.py`, `UsbSessionTransitionPolicyTest.kt`.
- Regressão atualizada: `UsbPoliciesTest.kt` e `tests/test_usb_permission_identity_contract.py`.
- GitHub Actions não executadas.
- APK não gerado.
- Testes não executados nesta superfície; estado: `TEST_NOT_AVAILABLE` para execução, com evidência estática remota fresca.
- Medição física do micropasso 049 permanece para a fase de validação física prevista no Programa Mestre.
