> LEIA PRIMEIRO: docs/handoff/CONTEXTO-DO-DONO.md (quem é o dono, o que já pediu, como trabalhar) e docs/handoff/PENDENTE.md (ledger único); grafo de produção (aba→ponte→teste→evidência, falta): `docs/grafo/PRODUCAO.md`.
# OMEGAS — contrato operacional estável
Este arquivo guarda só regras, um mapa e fatos já provados. Estado vivo fica no checkpoint; evidência, em `docs/`. Não duplicar aqui.

## Autoridade e boot

- **GitHub remoto é a autoridade técnica do OMEGAS:** código, Issues, specs, planos, WorkUnits, STATUS e evidências versionadas. Chat, Brainbase, AgentRed e MMMACHINE são superfícies de operação.
- Boot obrigatório: `AGENTS.md` → `PROJECT.md` → `STATUS.md` → Spec Kit ativo → WorkUnit ativa → Issues ligadas. Trabalho em andamento: `docs/handoff/PENDENTE.md`.
- Notion só como referência read-only de UX quando o dono pedir. Notion/Linear não controlam a execução.
- `WORK_SURFACE=REMOTE` · `SOURCE_MUTATION_TARGET=GITHUB_REMOTE_API` · `LOCAL_SOURCE_MUTATION=DENIED` · `TEST_SURFACE=EPHEMERAL_RUNTIME|REMOTE_CI|DEVICE_WHEN_AUTHORIZED`
- Antes de escrever e antes de concluir: resolver HEAD remoto de `OMEGASCANONICO` (branch canônica e padrão do GitHub desde 2026-10-20, criada a partir de `claude/carregar-sessao-r7j85r`; as outras são velhas — NÃO partir delas; tabela em docs/archive/HANDOFF-2026-10.md), reconciliar movimento concorrente, nunca sobrescrever trabalho alheio. Runtime local/MMMACHINE pode testar ou inspecionar o SHA remoto exato, mas não é fonte de autoridade. Regras permanentes do dono (Graphify sempre, eficiência sem perder função, UI, travas de escrita): `docs/REGRAS-DO-DONO.md`.

## Método

`@Codex Engineering Guardrails` + Superpowers aplicável. Mudança de comportamento: `evidência → RED válido → correção mínima → GREEN focado → revisão do diff → verificação ampla proporcional → CI remota`. Não chamar de PASS o teste que não exercitou o comportamento observado.

**Gate global de realidade** (tudo que depende de ECU/MP48/telemetria): teste estático é apoio, não prova. Cadeia alvo: `corpus real → replay determinístico → runtime/bridge real → WebView renderizado → evidência visual`. Viewport canônico `1280x720`. Validação física só com ECU real e o dono.

## Invariantes (cada um tem teste)

- Nenhuma escrita automática de Map K/Curve K (dono, 2026-10-18: "não é para gravar sozinho"; supera a exceção D1 de 2026-10-16). O D1 só SUGERE: após cada AutoMatch da ECU registra `d1_decision` na sessão (modo SOMBRA ou DESLIGADO; não existe modo que grave). O AutoMatch manual continua só por ação do operador. Escrita manual exige intenção explícita + ACK + readback. Evidência: `docs/evidence/2026-10-16-evidencia-arrancada-e-automatch.md` (Q1: a ECU aplica o AutoMatch sobre a curva viva).
- `RESET_ALL` não é ação exposta; só o reset do GNV (ação AutoCal `02 24 04 02`) pode ser pedido à mão, em um toque, com backup completo antes e readback da ECU.
- Teto de escrita da curva K = **1,8** (dono, 2026-10-13), como o piso de 100 do Mapa K: `KFactorProtocol.MAX_WRITE_FACTOR`/`MAX_WRITE_RAW` valem para escrita manual, restauração de backup e AutoMatch; a leitura segue até o limite do protocolo. Testes: `KFactorManualPlannerTest.kt`, `AutoMatchKFactorDraftTest.kt`, `tests/ui/curva-k-arrasto.test.cjs`.
- `reset-nunca-pausa-aprendizado` (dono, 2026-10-08): após qualquer reset, `AUTO_CAL_ENABLE` é conferido e religado até readback = 1, mesmo em timeout/NAK; sem isso o reset não está concluído. Teste: `ResetNuncaPausaAprendizadoTest.kt` + `tests/test_reset_nunca_pausa_aprendizado.py`.
- `autocal-sem-leitura-anterior` (dono, 2026-10-11): o AutoCal não mostra leitura anterior nem comparação antes × depois; tudo em tempo real. Teste: `tests/ui/autocal-sem-leitura-anterior.test.cjs` + `tests/test_autocal_sem_leitura_anterior.py`.
- Ciência e protocolo críticos ficam em Kotlin/native. AGORA fica no mesmo contexto da referência AutoCal. LEVELS fica RAW até haver calibração física separada.
- Desconhecido é "—", nunca 0. Erro de transporte não é erro da ECU. "Gravado" só após readback igual. **Sem atrito** (dono): interruptor/ação simples = 1 toque, sem pergunta nem janela; conferência em segundo plano, Desfazer no cartão (teste `tests/ui/anti-apagao-toque-real.test.cjs`; regra completa em `docs/handoff/CONTEXTO-DO-DONO.md`).

## Decisões de campo do dono (2026-10-13)

- Voltar a curva K a 1,0 só é perigoso se a curva estava exercendo uma **correção grande** (ex.: ~1,5): aí o motor precisa se readaptar de uma vez, a correção da ECU explode, a tensão sobe e, **acima de 14,1 V, o GNV desliga** (a ECU volta ao último estado seguro do mapa e da curva). O dono resolveu isso passando a correção grande para o **Mapa K (~220)** e deixando a curva em 1,0: com a curva neutra, um reset dela é irrelevante, e o AutoCal só faz refino proporcional. Detalhe: PROTOCOLO-LANDI `17` (relato de campo). Regra de projeto: avisar antes de resetar a curva **só quando ela estiver longe de 1,0**.
- O `ExportToK` do original **não é usado**. A curva K vira Mapa K por rotina própria (`app/src/main/assets/ui/core/curve-to-map.js`, botão "Curva K → Mapa K" na aba Mapa K): relê mapa e curva na hora (nenhum valor fixo), **refatora o produto Mapa×Curva** e só **propõe**; gravar = mapa, depois curva neutra (`app/src/main/assets/ui/core/balance-runner.js`), cada etapa com ACK + readback. Fórmula da curva do original: PROTOCOLO-LANDI `26`. Teste: `tests/ui/curva-k-para-mapa-k.test.cjs`.
- A Curva K aceita arrasto de nó (aba Ajuste global): soltar só prepara a prévia; gravar exige revisão e leitura de volta. Provas: `tests/ui/curva-k-arrasto.test.cjs`, `tools/ci/curva_k_arrasto.cjs`.
- **Sessões reais do dono** (Drive `OMEGAS/Sessoes Uteis`, fonte de informação, não de regras): corpus compacto em `tests/fixtures/sessoes-uteis/` (LEIA-ME.md, IDs do Drive, carregador `tools/sessoes/corpus.py`); o bruto fica no Drive.
- Barra do topo **persistente em todas as abas** (`app/src/main/assets/ui/components/live-bar.js`): tensão da ECU (byte 19 do `48 01` ÷ 16, **escala provisória**, alerta em 14,1 V), MAP, Petrol Inj. e RPM. Abas: AutoCal, Refino, Ajuste local, Ajuste global, Ferramentas (Agora, Sugestões e Refino local podadas). Teste: `tests/ui/global-live-bar.test.cjs`. Medidor de uso: `app/src/main/assets/ui/core/usage-meter.js` (`tools/ci/usage_meter.cjs`); verificação do protocolo: `tools/sessoes/verificar_protocolo_autocal.py`. Kotlin SEM Android: `tools/jvm/rodar_aquisicao_emulada.sh` (NativeAutoCalMonitor real × ECU falsa, gasolina/GNV em qualquer ordem); estresse da UI: `tools/ci/estresse_interface.cjs`; porta de gravação (só com a ECU concluída): `app/src/main/assets/ui/core/write-gate.js` (ADR em `.10x/adrs/`); pontas soltas: `tools/audit/pontas_soltas.py`. **Só evidência, nunca relógio (dono, 2026-10-14):** nenhuma decisão do app depende de minutos (ECU terminou, verificação, restaurar, visita, "piorou"): vale o que a ECU mostra e a quantidade de leituras. Sem botão de pausar a ECU (desliga a curva); existe "Refinar por minha conta" (interruptor do Refino, o dono grava quando quer e manda a ECU reler o GNV).

## Mapa do código (entrada → teste)

| Assunto | Entrada | Teste |
|---|---|---|
| Refino (fases, proposta) | `app/src/main/java/com/omegas/prohub/autocal/RefinementAutopilot.kt`, `app/src/main/assets/ui/screens/refino.js` (gráfico = o do AutoCal), `app/src/main/assets/ui/core/map-fix.js` (gravar no Mapa K sem sair do Refino), `autocal/StallWatch.kt`, `app/src/main/assets/ui/core/stall-region.js` | `tests/ui/tropecos-para-mapa.test.cjs`, `tests/ui/map-fix.test.cjs`, `tests/ui/refino-mesmo-grafico.test.cjs`, `tools/ci/refino_mapa.cjs`, `tools/ci/comparar_com_original.cjs`, `StallWatchTest.kt` |
| Tela AutoCal do original (zonas, `#n/d`, eixos, Acquisition, curva K) | `app/src/main/assets/ui/screens/autocal-original.js` (usado por `autocal-cockpit.js`) | `tests/ui/autocal-original-parity.test.cjs` |
| Global × local (por RPM) | `autocal/EquivalenceAttribution.kt`, `autocal/LocalMapPlanner.kt` | `tests/test_rpm_residual_replay.py` |
| Época de aquisição do AutoCal | `autocal/NativeAutoCalAcquisitionEpoch.kt` | `NativeAutoCalAcquisitionEpochTest.kt` |
| Mapa K manual e ajuste local | `calibration/MapKManualPlanner.kt`, `app/src/main/assets/ui/screens/map.js` | `tests/ui/mapa-k-ajuste-local.test.cjs` |
| Telemetria e ponte | `service/TelemetryForegroundService.kt`, `web/HubJavascriptBridge.kt` | `tests/test_governance_contract.py` |
| Layout sem rolagem | `ui/screens/*.js` | `tools/ci/sem_rolagem_abas.cjs` |

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

**Ordem de consulta:** 0) `docs/mapa-de-fontes/MAPA.md` (onde está cada arquivo: repo, PROTOCOLO-LANDI, Drive; nunca peça ao dono o que está lá); 1) `docs/reverse/` e este mapa; 2) `PROTOCOLO-LANDI`, lendo só o arquivo do assunto pela API do GitHub (privado, exige `add_repo`; não clonar); 3) só então o `ProgBase.exe`. O relatório publicado do `PROTOCOLO-LANDI` (artefato do dono) é gerado por `tools/protocolo-artifact/build.py`; só republicar na mesma URL quando o dono mandar. O `PROTOCOLO-LANDI` é evidência derivada: para byte de comando que vá para o código, confirmar no log real.

**Reabertura:** só reabrir o `ProgBase.exe` se a pergunta não estiver em `docs/reverse/` nem no `PROTOCOLO-LANDI`. Use o CLI do REA em segundo plano com instantâneo (receita no checkpoint); o MCP estoura 60 s nesse binário. Antes de varrer código, consulte o mapa acima ou use a Serena.

## Regra do APK (dono, 2026-10-13)

Não gerar APK sem aprovação explícita do dono. Ordem: mexer → testar → mandar as prévias (imagens) → dono aprova → só então `verde-apk-now.yml`. Aprovação de uma rodada não vale para a seguinte.

## Fronteiras

- **Codex primeiro (dono, 2026-10-16):** tarefa delegável vai ao Codex (`tools/codex-task.sh`, hook `.claude/hooks/regras-do-dono.txt`); o Claude fica com decisão, junção/push e CI; relato do Codex não é prova.
- Não alterar bytes nem classes de comando da ECU (`UsbSerialManager`, `ResponseDrivenEcuEngine`, `AutoCalProtocol`, `KFactorManager`, `KWriteManager`, `AutoCalNativeActionManager`) sem autorização do dono.
- Alvo que não roda aqui (ECU, programa Windows): skill `.claude/skills/emular-por-observacao/SKILL.md` (capturas → parser → esquema → decompilação mínima → emulador → tela renderizada → app real).
- O programa ativo compara o OMEGAS Platina com o **ProgBase original e seus logs reais**.

## Execução paralela

Só quando o dono autorizar e as tarefas forem independentes: um dono por superfície de escrita; exploradores e falsificadores em paralelo; integração serializada; relato de worker não é prova, verificar o estado integrado. **Sincronia entre agentes (dono, 2026-10-27):** antes de escrever `tools/sync-agentes.sh --so-ver`; depois de cada marco, commit pequeno e `tools/sync-agentes.sh`; só diga "publicado" depois da leitura de volta. Detalhes: `docs/handoff/SINCRONIA-AGENTES.md`.
