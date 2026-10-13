# OMEGAS — contrato operacional estável

Este arquivo guarda só regras, um mapa e fatos já provados. Estado vivo fica no checkpoint; evidência, em `docs/`. Não duplicar aqui.

## Autoridade e boot

- **GitHub remoto é a autoridade técnica do OMEGAS:** código, Issues, specs, planos, WorkUnits, STATUS e evidências versionadas. Chat, Brainbase, AgentRed e MMMACHINE são superfícies de operação.
- Boot obrigatório: `AGENTS.md` → `PROJECT.md` → `STATUS.md` → Spec Kit ativo → WorkUnit ativa → Issues ligadas. Trabalho em andamento: `docs/handoff/OBSIDIAN-ENXUTO-CHECKPOINT-20261011.md`.
- Notion só como referência read-only de UX quando o dono pedir. Notion/Linear não controlam a execução.
- `WORK_SURFACE=REMOTE` · `SOURCE_MUTATION_TARGET=GITHUB_REMOTE_API` · `LOCAL_SOURCE_MUTATION=DENIED` · `TEST_SURFACE=EPHEMERAL_RUNTIME|REMOTE_CI|DEVICE_WHEN_AUTHORIZED`
- Antes de escrever e antes de concluir: resolver HEAD remoto de `OmegasPlatina`, reconciliar movimento concorrente, nunca sobrescrever trabalho alheio. Runtime local/MMMACHINE pode testar ou inspecionar o SHA remoto exato, mas não é fonte de autoridade.

## Método

`@Codex Engineering Guardrails` + Superpowers aplicável. Mudança de comportamento: `evidência → RED válido → correção mínima → GREEN focado → revisão do diff → verificação ampla proporcional → CI remota`. Não chamar de PASS o teste que não exercitou o comportamento observado.

**Gate global de realidade** (tudo que depende de ECU/MP48/telemetria): teste estático é apoio, não prova. Cadeia alvo: `corpus real → replay determinístico → runtime/bridge real → WebView renderizado → evidência visual`. Viewport canônico `1280x720`. Validação física só com ECU real e o dono.

## Invariantes (cada um tem teste)

- Nenhuma escrita automática de Map K/Curve K. Escrita manual exige intenção explícita + ACK + readback.
- `RESET_ALL` não é ação exposta; só o reset do GNV (ação AutoCal `02 24 04 02`) pode ser pedido à mão, em um toque, com backup completo antes e readback da ECU.
- `reset-nunca-pausa-aprendizado` (dono, 2026-10-08): após qualquer reset, `AUTO_CAL_ENABLE` é conferido e religado até readback = 1, mesmo em timeout/NAK; sem isso o reset não está concluído. Teste: `ResetNuncaPausaAprendizadoTest.kt` + `tests/test_reset_nunca_pausa_aprendizado.py`.
- `autocal-sem-leitura-anterior` (dono, 2026-10-11): o AutoCal não mostra leitura anterior nem comparação antes × depois; tudo em tempo real. Teste: `tests/ui/autocal-sem-leitura-anterior.test.cjs` + `tests/test_autocal_sem_leitura_anterior.py`.
- Ciência e protocolo críticos ficam em Kotlin/native. AGORA fica no mesmo contexto da referência AutoCal. LEVELS fica RAW até haver calibração física separada.
- Desconhecido é "—", nunca 0. Erro de transporte não é erro da ECU. "Gravado" só após readback igual.

## Decisões de campo do dono (2026-10-13)

- Zerar a curva K faz a correção explodir e a tensão da ECU subir; acima de **14,1 V o GNV desliga** e a ECU volta ao último estado seguro. O dono usa Mapa K ~220 e curva K em 1,0. Detalhe: PROTOCOLO-LANDI `17` (relato de campo).
- O `ExportToK` do original **não será usado**. A curva K vira Mapa K por rotina própria, só com escritas manuais do Mapa K (ACK + readback). Entrada: `calibration/MapKManualPlanner.kt`.
- A faixa de status mostra a tensão da ECU (byte 19 do `48 01` ÷ 16, **escala provisória**) com alerta em 14,1 V. Teste: `tests/ui/status-tensao-ecu.test.cjs`.

## Mapa do código (entrada → teste)

| Assunto | Entrada | Teste |
|---|---|---|
| Refino (fases, proposta) | `app/src/main/java/com/omegas/prohub/autocal/RefinementAutopilot.kt`, `app/src/main/assets/ui/screens/refino.js` | `tests/ui/sugestoes-refino.test.cjs` |
| Tela AutoCal do original (zonas, `#n/d`, eixos, Acquisition, curva K) | `app/src/main/assets/ui/screens/autocal-original.js` (usado por `autocal-cockpit.js`) | `tests/ui/autocal-original-parity.test.cjs` |
| Global × local (por RPM) | `autocal/EquivalenceAttribution.kt`, `autocal/LocalMapPlanner.kt` | `tests/test_rpm_residual_replay.py` |
| Época de aquisição do AutoCal | `autocal/NativeAutoCalAcquisitionEpoch.kt` | `NativeAutoCalAcquisitionEpochTest.kt` |
| Mapa K manual e ajuste local | `calibration/MapKManualPlanner.kt`, `app/src/main/assets/ui/screens/map.js` | `tests/ui/mapa-k-ajuste-local.test.cjs` |
| Telemetria e ponte | `service/TelemetryForegroundService.kt`, `web/HubJavascriptBridge.kt` | `tests/test_governance_contract.py` |
| Layout sem rolagem | `ui/screens/*.js` | `tools/ci/sem_rolagem_8_abas.cjs` |

Comandos de verificação: `node --test tests/ui/*.test.cjs` · `python3 -B -m unittest discover -s tests -p "test_*.py"` · `python3 -B tools/run_checks.py` · `sh ./gradlew testDebugUnitTest lintDebug`.

## Fatos já provados (não reabrir)

| Pergunta | Resposta | Onde |
|---|---|---|
| A ECU zera os contadores no `RESET_ALL`? | Sim, na hora. O ProgBase nunca usa reset seletivo. | `docs/reverse/PROGBASE-LOGNOVO-RESET-2026-10-12.md` |
| Quais strings e unidades do ProgBase existem? | Só RTTI e exportações; o AutoMatch não tem função nomeada. | `docs/reverse/evidence/` |
| O residual por RPM separa curva global de Mapa K? | Replay causal já feito. | `docs/evidence/2026-10-13-sessaoutil-rpm-residual-investigation.md` |
| Modos da ação AutoCal `02 24 04 <modo>`? | 1 = gasolina, 2 = GNV, 4 = **Reset all** (zera tudo, K=1,0; visto no fio), 8 = automatch manual. O antigo "0x04 = reset de gás" estava errado. | PROTOCOLO-LANDI `17`, `23`; `docs/evidence/2026-10-13-autocal-original-vs-omegas.md` |
| Zonas, rótulos, eixos e fluxo gráfico do AutoCal original? | Provados no código e na captura (zonas 0–4/5–8/9–12/13–17). | PROTOCOLO-LANDI `20`, `21`, `23`; emulador em `tools/` do LANDI |
| Como é o protocolo serial AEB (framing, checksum, opcodes, handshake, flash)? | Documentado a partir do `ProgBase.exe` e do Portmon. | repositório `viluadmcontas2-dot/PROTOCOLO-LANDI` (índice `00-LEIA-ME.md`; opcodes em `12-MAPA-OPCODES.md`) |
| Como a tela AutoCal conversa com a ECU (polling, reset, automatch, fim, escalas, layout)? | Observado em duas capturas reais + telas do original. | `PROTOCOLO-LANDI` 17–20; divergências do OMEGAS em `docs/evidence/2026-10-13-autocal-original-vs-omegas.md` |

**Ordem de consulta:** 1) `docs/reverse/` e este mapa; 2) `PROTOCOLO-LANDI`, lendo só o arquivo do assunto pela API do GitHub (privado, exige `add_repo`; não clonar); 3) só então o `ProgBase.exe`. O relatório publicado do `PROTOCOLO-LANDI` (artefato do dono) é gerado por `tools/protocolo-artifact/build.py`; só republicar na mesma URL quando o dono mandar. O `PROTOCOLO-LANDI` é evidência derivada: para byte de comando que vá para o código, confirmar no log real.

**Reabertura:** só reabrir o `ProgBase.exe` se a pergunta não estiver em `docs/reverse/` nem no `PROTOCOLO-LANDI`. Use o CLI do REA em segundo plano com instantâneo (receita no checkpoint); o MCP estoura 60 s nesse binário. Antes de varrer código, consulte o mapa acima ou use a Serena.

## Fronteiras

- Não alterar bytes nem classes de comando da ECU (`UsbSerialManager`, `ResponseDrivenEcuEngine`, `AutoCalProtocol`, `KFactorManager`, `KWriteManager`, `AutoCalNativeActionManager`) sem autorização do dono.
- O programa ativo compara o OMEGAS Platina com o **ProgBase original e seus logs reais**.

## Execução paralela

Só quando o dono autorizar e as tarefas forem independentes: um dono por superfície de escrita; exploradores e falsificadores em paralelo; integração serializada; relato de worker não é prova, verificar o estado integrado.
