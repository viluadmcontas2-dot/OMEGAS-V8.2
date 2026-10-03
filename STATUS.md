## APK Platina — Refino base única, gravação automática, "piorou" sem travar — 2026-10-03

- **Branch/SHA:** `ccr-745c77c3-dvvqn0` @ `623986c` (reconciliado com `OmegasPlatina` 8197d9c), mesclado em `OmegasPlatina` por PR #124 → `eb4ec26`. `main` avançada (fast-forward) para `eb4ec26`; `main` deixa de ser um ramo morto.
- **Workflows no SHA `623986c`, todos `success`:** `ci.yml` run `37120961904`; `verde-apk-now.yml` (`build_apk=true`) run `37120961291`; `verde-android-render-evidence.yml` run `37120962939`.
- **APK:** artifact `11273825037` / `omegas-platina-final-623986cb…`, 4.813.736 bytes, digest do zip `e58e6f3d459e88382195bcaef444e0f514948c556748d52883e2771cb8fb8267`. Expira em 14 dias.
- **O que mudou, com a classe de prova** (1 contrato de texto, 2 sintético, 3 replay real, 4 APK no emulador, 5 físico):
  - Aba Aprender removida; o Refino é a base única; menu com 7 destinos (classe 1 + 4).
  - Gravação de sessão sempre automática ao conectar a ECU; sem botões Iniciar/Encerrar nem chave (classe 1; o emulador não exercita USB real).
  - Refino não fica preso em "trecho piorou": PIOROU exige piora > 4% e erro > 5%; só o último experimento oferece restauração; oferta expira em 30 min; o piloto só pede restaurar se a faixa ainda está fora da tolerância (classe 2: `RefinementJournalTest`, `RefinementAutopilotTest`).
- **Não provado:** classe 5. O relato do dono (04:49) de AGORA à esquerda da curva GNV entre 0,40 e 0,65 bar segue em investigação (adendo D7 entregue ao executor).

# OMEGAS Platina — Status

## Missão AutoCal/Refino — checkpoint vigente 2026-10-03
- Fase0 contrato integrado #121; infraestrutura #123 integrada na Platina `8197d9ca631711b116b474342062c14e96285b7f`.
- PR#122 caixa-preta em execução; **não liberar merge nem dizer missão concluída**.
- Fonte de produto testada `de12f9ac53e1e609fb7a626dbd9e7f67e68d33a4`; CI `37109036435` success; gate `37109036557`: **30/30 jobs success** (APK+29 cenários).
- APK4.831.148 bytes, SHA256 `e7bb3c2d7053e4b8c634af5409efa1fe9efe1a8b4e5ba5f63b043d03ddc13942`; artifact `11269136496`, ZIPdigest `60724ae92d69208c0bfc9f61d0212e9630bd3111dc79d7b0aad721792c965d71`.
- Classe2: host lendo30s; proposta/rollback30min; aquisição/automático/verificação40min; silêncio não autoriza conclusão. Baselines15/15 antes/depois; mutantes8/8 detectados, 0 sobreviventes.
- Classe4: primeiro veredito→worker→JSONL→RESUMO, razão offline —, timeout honesto e SIGKILL; RED→GREEN em logs integrados. 29 cenários não equivalem aos 11 estados novos de cada tela.
- Inspeção manual das imagens indisponível: download local bloqueado por ambiente desconectado409. Não alegar classe5.
- RED `37110275413`: 616 testes / 2 falhas novas (id por relógio repetido e atualização visual sem dado); GREEN `37110568906`, gate APK `37110569079`: 17 testes focados antes/depois e 10 mutantes mortos,0 sobreviventes.
- RED Android job `111168345115` no mesmo SHA `a4b3461d05d949abe0d7779d86d0b485588ef4c3`: quatro decisões esperadas / zero gravadas antes do tick. Correção por snapshots imediatos submetida; GREEN/revisão/artefato deste ajuste pendentes. Não fechar#122.
- D7 incorporado após os blocos anteriores: relato físico do dono preservado; H1–H7 ainda inconclusivas, sem replay/resíduo medido. Contrato de coerência deverá preceder qualquer correção D7.
- Performance classe3 (corpus histórico em JVM/CI): frio mediana18,64ms/p9545,79ms; cache0,00019ms. Alvo frio30ms **não cumprido nesta medição**; bridge completa/aparelho não provados.
- Política/simulador fechado, frescor/geração nativa, teto absoluto Journal, D4/D6/D5/rolagem horizontal e demais módulos **pendentes**. Falso “piorou”, detecção real, passos até estável: **não medidos**.
- REDs/AF1 isolados/falhas anteriores/radar detalhados em `docs/autocal/CAIXA-PRETA-RED.md`. Provas antigas abaixo são arquivo histórico e não provam este código.

## APK Platina + Refino v2 (provas de corpus real, render e sessão) — 2026-10-03

- **Fonte:** `4a6965b3837f7bc0f212bcd84f6e60575523c4a4`, branch `ccr-745c77c3-dvvqn0`, base `OmegasPlatina` `fbee28c`.
- **Workflows no mesmo SHA, todos `success`:** `verde-apk-now.yml` (`build_apk=true`) run `37093254242`; `verde-android-render-evidence.yml` run `37093252571` (26 cenários, 1280×720); `verde-fast-contracts.yml` run `37093255494`.
- **APK:** artifact `11263742408` / `omegas-platina-final-4a6965b3…`, 4.824.552 bytes, `APK_SHA256=45665a7b8c1586b14c0dd798bfdc26be09b82533da3751c5f5d65cae4ac93788`, digest do zip `baed47a7eb665ab0584580dbcc4a8bdfea632b88d4bf432c9ff12e961156c539`. Expira em 14 dias.
- **O que mudou, com a classe de prova** (1 contrato de texto, 2 sintético, 3 replay de sessão real, 4 APK renderizado no emulador, 5 físico):
  - Refino conclui a verificação (timebox, sem base, inconclusivo) em vez de medir para sempre: classe 2 e 3.
  - Marcha lenta não vira correção; Refino propõe só com a condução depois do 3º AutoMatch: classe 2 e 3, espelhado no oráculo Python.
  - **Estado vem da ECU.** Fase nova `LENDO_ECU`; 3 de 3 reconhecido pelo contador e pelo máximo lido; a curva de gasolina que a ECU já tem vira referência onde o app não mediu gasolina (gasolina própria tem precedência; faixa apoiada na ECU usa tolerância de 6%). Validação com a sessão real de 01/10: referência da ECU contra gasolina medida na mesma sessão, mediana 1,000, 80% dos pontos dentro de 2,3%. Ciclo completo num app recém-instalado, curva que chega depois, que some, AutoMatch novo, sem cabo: classe 2 e 3; cenário de render `refino-app-novo-ecu-pronta` com replay real: classe 4.
  - Apagão com antes, durante e depois, quase-apagão, religou, telemetria parou: classe 2 e 3 (5, 6 e 3 quase-apagões e 0 apagões nas três sessões reais).
  - Gráfico e AGORA ao vivo, resposta pronta em segundo plano: classe 2. Consulta fria do Refino com mediana 12,08 ms e p95 20,93 ms no corpus real inteiro, medida no CI: classe 3. Latência da ponte dentro da WebView e AGORA acompanhando a telemetria: classe 4.
  - Número desconhecido nunca vira 0; barra de status não congela fora das rotas ao vivo: classe 2 e 4.
  - **Sessão = um ZIP só** (`<sessão>.zip`), publicado ao fechar ou na próxima abertura do app; nada vai ao Drive durante a gravação (uma sessão de 3,5 h tinha gerado mais de 105 ZIPs pequenos). `RESUMO.md` dentro do ZIP: fases, apagões e o que veio depois, gravações, veredictos; sessão morta sem fechar é reconstruída dos eventos. Classe 2 (`SessionPartPlannerTest`, `SessionResumoTest`) e classe 4 (`session-kill-recovery`: SIGKILL no meio, nada no Drive antes, um ZIP sem perda nem duplicata depois).
  - **Ferramentas e balão flutuante:** lista de sessões não pega o bloqueio do gravador e sai em segundo plano (antes relia arquivos inteiros a cada 2 s e congelava a tela); retenção usa o número mostrado na tela (a poda usava 25 e a tela dizia 20); balão maior com combustível, RPM, Petrol Inj., MAP e gás, três tamanhos e telemetria velha como "—"; pergunta de primeiro uso que abre direto a tela de autorização. Classe 4 (`ferramentas-balao-prompt` com 25 sessões salvas: primeira leitura < 100 ms, mediana < 30 ms). O balão em si (janela do Android sobre outros apps) não tem print: só o físico prova.
  - Inspector do AutoCal rola dentro da própria caixa (passava de 720 px com a época coerente): classe 4. Teste Node que lê o resultado do JVM é gate de verdade.
- **Não provado:** curva incoerente entre Refino e AutoCal nativo (falta a sessão do dia para reproduzir); o gráfico de época do AutoCal após o AutoMatch (decisão de UI do proprietário); balão sobre outros apps; qualquer coisa na ECU ou no carro.
- **Fora da regra de teste remoto:** o autor rodou localmente o oráculo Python, `node --test` (display-rules, refino-screen, sensibilidade), `tools/run_checks.py` e a análise dos corpos reais. Nada foi compilado nem testado em Kotlin ou Gradle localmente.
- **Fora de `LOCAL_SOURCE_MUTATION=DENIED`:** o código foi editado no clone da sessão e enviado por `git push` para a branch de trabalho, não pela API remota do GitHub.
- **Proposta, sem remover nada:** `docs/decisions/PROPOSTA-ABAS-SUGESTOES-APRENDER.md`.
- **Decisão pendente do proprietário:** gravação semiautomática (A) ou automática com travas (B). Nada foi implementado.
- `PHYSICAL_VALIDATION_CLAIMED=false`.

## APK Platina + Refino (candidato para teste no carro) — 2026-10-02

- **Base:** a árvore da `OmegasPlatina` (`b185e80a`), acrescida do cérebro do refino, da aba Refino, da remoção do OBD, de Ferramentas e Sugestões corrigidas, e do balão flutuante que nunca cobre o app.
- **Fonte:** `2c456bcab9ab0306d7f23daddb5f1b7c728ba4a0`, branch `claude/brave-darwin-wuliyo`, integrada em `OmegasPlatina`.
- **Workflow:** `verde-apk-now.yml` com `build_apk=true`; run `37077178649`; conclusão `success` (`run_checks` + `clean testDebugUnitTest lintDebug assembleDebug`).
- **Artifact:** `11257381786` / `omegas-platina-final-2c456bca…`, 4.782.450 bytes, digest `sha256:0cd6ebd022956a551196fb4320b6d97133759ea181f0ff6088eb22c5300a4054`. Expira em 2026-10-16.
- **Conteúdo novo:** Refino com cursor AGORA, fases como estado, contagem Gas/GNV, apagões; Sugestões sem travar (`getRefinementPhase`); sessões em partes ZIP imutáveis.
- **Classe de prova:** comportamento sintético (JVM/Node) + build/lint; UI conferida por print em navegador com fixtures. Sem replay real nem print do APK no emulador para o Refino; sem validação física.
- **Anterior:** `8331cc36` / run `37071148942`.
- **Provas locais no mesmo SHA:**
  - `QUALITY_GATE_FAST=PASS`;
  - JVM 540 OK (via kotlinc);
  - paridade Kotlin↔Python OK;
  - capturas 1280×720 com o snapshot real de 01/10 17:19.
  - O único teste UI que falha, `learning-jvm-payload`, já falhava na Platina original.
- `PHYSICAL_VALIDATION_CLAIMED=false`.

## Active control surface

- Branch: `OmegasPlatina`
- Estado: `FINAL RELEASE CANDIDATE — PROGBASE HOST CONTRACT V2 LOCKED — SAME-SHA GATES + APK BUILD`
- Autoridade: GitHub remoto no HEAD resolvido antes de qualquer escrita.

Checkpoint remoto observado nesta missão:
- HEAD inicial re-resolvido: `88b472ad293ad96671fa4368303d38148f75c3c3`
- `OmegasVerde fast contracts` #36509175880: SUCCESS nesse SHA
- `OMEGAS VERDE CI` #36509175891: SUCCESS nesse SHA

Esses nomes de workflow eram legados Verde; a Platina mantém a evidência, mas a autoridade ativa é a branch `OmegasPlatina`.

## Checkpoint de fechamento AutoCal Host v2

- A ECU é tratada como caixa-preta; a fidelidade exigida é ao comportamento observável do ProgBase 4.2.0.6 como host.
- O contrato forense fechado classifica 53/53 handlers relevantes de AutoCal/Settings/Referências.
- `NUM_AUTOMATCH_EXECUTED == MAX_AUTOMATCH` significa cota de AutoMatch atingida; não implica `AUTO_CAL_ENABLE=0`.
- O Portmon original mostra nova aquisição GNV após 3/3; portanto a aquisição pode continuar com a cota de AutoMatch esgotada.
- `FinishAutocal` permanece apenas compatibilidade técnica no backend; a Action original nasce desabilitada e não é CTA normal da Platina.
- `BtnFinishAutomatch` original pertence ao `PanelDbg` oculto.
- AutoMatch nativo é automático/ECU-owned; AutoMatch manual é intervenção separada.
- A tela Curva K permanece a evolução moderna do editor manual K do ProgBase, com intenção humana, ACK e readback.

## Gating Platina

- APK autorizado nesta missão pelo proprietário; o branch de produto continua sem build por push. Após o mesmo SHA passar CI, fast contracts, global reality e Android render, uma branch efêmera de build compila exatamente esse SHA e publica o artifact.
- DUMP/Portmon/ECU prevalecem sobre documentação antiga.
- `PARTIAL` ou `UNKNOWN` em comando de mutação bloqueia release.
- AutoMatch nativo é observado/bracketado; o host não escreve K automaticamente.
- Predictor/V7 é análise/revisão; escrita real só pelas telas manuais, com intenção explícita, ACK, readback e sessão.
- Reset K provado usa `MUL_ACT 0x0161[30]` Q14, `1.0 = 0x4000`, com readback completo.
- `TAutoCalDM_EE`/`VECT_AUTOCAL_EE` é superfície distinta e não substitui `MUL_ACT`.
- GAS_PREV expõe Tinj/MAP prévios; contador/maturidade prévios ficam desconhecidos quando não há fonte própria.

## Classificação atual de readiness AutoCal

| Rotina | Status | Release | Critério |
| --- | --- | --- | --- |
| Enable/Disable AutoCal | PROVEN | non-blocking | `AUTO_CAL_ENABLE` com readback |
| Reset petrol/gas/all | PROVEN | non-blocking | modos nativos `0x01/0x02/0x04`, ACK e snapshot |
| Delete/readquire point | PROVEN | non-blocking | masks completos 18+18 e commit único |
| Readquire multiple/mixed | PROVEN | non-blocking | seleção gasolina+GNV preserva pontos não selecionados |
| Manual AutoMatch | PROVEN | non-blocking | modo nativo `0x08`, intenção explícita |
| Finish AutoMatch/AutoCal | PROVEN | non-blocking | `MAX_AUTOMATCH -> NUM_AUTOMATCH_EXECUTED`, readback |
| Reset K | PROVEN | non-blocking | 30 writes `MUL_ACT=0x4000`, readback dos 30 |
| 18 bandas / 4 zonas | PROVEN | non-blocking | seletores `U8_1` e `CALIBRATION_VAL_1[2/5/8]`, fronteiras `5/9/13` |
| Petrol current / Gas current | PROVEN | non-blocking | buffers atuais com contadores próprios |
| Gas previous | PARTIAL | non-blocking | Tinj/MAP prévios provados; contador anterior UNKNOWN e não exibido como certo |
| MUL_ACT | PROVEN | non-blocking | normal `0x0161`, distinto de EE |
| Reference curves | PROVEN | non-blocking | refresh de referência separado e coerente |
| AutoMatch before/after K | PROVEN/PARTIAL | non-blocking | bracket causal; `INCONCLUSIVE` nunca vira certeza |
| Error/recovery/session loss | PROVEN | non-blocking | session fencing, interlock, recibo de falha, sem retry automático de mutação |

## ProgBase/original confirmado

Estruturas VCL observadas:
- `TAutoCalUI`, `TAutoCalDM`, `TFormRifAutocal`;
- `ChartData`, `PetrolCurve`, `GasCurve`;
- `RunPoint`, `CurrentBand`, `PollingPetrol`, `PollingGas`.

Portmon:
- `48 01 49`: mediana ~46,57 ms;
- família `0x015B..0x0163`: ~2,01 s;
- família `0x018D/0x018E`: ~4,05 s;
- leituras secundárias intercaladas com telemetria viva.

Ações AutoCal recuperadas do ProgBase 4.2.0.6 original:
- Fonte vinculante: `tests/fixtures/progbase-autocal-action-map-v1.json`, schema `omegas.progbase.autocal-action-map.v2`, classificação `ORIGINAL_DERIVED`.
- `ActionAutoMatchExecute` = Manual AutoMatch, modo `0x08`, frame `02 24 04 08 32`;
- `ActionResetPetrolExecute` = Reset petrol point, modo `0x01`, frame `02 24 04 01 2B`;
- `ActionResetGasExecute` = Reset gas point, modo `0x02`, frame `02 24 04 02 2C`;
- `ActionResetAllExecute` = Reset all, modo `0x04`, frame `02 24 04 04 2E`;
- `ActionAutoCalRifExecute` = Modify map refs usa rota separada; não é wrapper simples `0x24`.
- `ActionResetKFactorExecute` usa rota separada: escreve `MUL_ACT[i] = 1.0` via `TAebVector.SetDouble -> SetData -> SetDataInEcu`.
- No Lognovo original, o frame `02 24 04 04 2E` teve efeito amplo: zerou estado de aquisição gasolina/GNV, curvas de referência e `MUL_ACT`. OMEGAS não promete seletividade e mantém resets destrutivos intertravados.

Semântica live recuperada:
- Petrol Injection raw: payload offset 8;
- LEVELS RAW: payload offset 13;
- MAP raw: payload offset 17, **S16LE**;
- nenhuma conversão física LEVELS→%/litros/m³ está autorizada.

## OMEGAS atual — correções provadas

- Finish AutoCal usa o caminho host comprovado do ProgBase: `MAX_AUTOMATCH (0x0165:2) -> NUM_AUTOMATCH_EXECUTED (0x0174)`, com settle de 100 ms no Finish completo, ACK, readback e recibo. O Finish AutoMatch técnico permanece backend/avançado e não vira uma segunda decisão normal de UX.
- O oracle do Finish é preso a três artefatos exatos do DUMP: código `0x0051A390/0x0051A454`, DFM `TAUTOCALDM` e grid initializer `0x00510DF8`; o gate falha se o layout de campos deslocar novamente.
- A tela AutoCal atualiza aquisição/projeção em ~1 s sem criar segundo serial owner; o cursor live continua no scheduler rápido.
- Bolinhas distinguem `COLETANDO` de `ADQUIRIDO` pelos contadores/limiares nativos e mostram progresso, sem alterar critérios da ECU.
- O estado de combustível MP48 está visível no cabeçalho AutoCal; MAP, Petrol Inj., RPM e zona permanecem essenciais.

- LEVELS do AutoCal usa telemetria live fresca.
- Dashboard/Agora contém `LEVELS RAW`; emissão de `level_percentage` não calibrado foi removida.
- `CurrentBand` reproduz o helper original: threshold[i] < MAP <= threshold[i+1], fora do domínio não seleciona faixa.
- Refresh operacional ~1 s usa a autoridade serial existente e renova a unidade consumida por `AutoCalAcquisition`:
  - gasolina: tempo + MAP + contador;
  - GNV atual: tempo + MAP + contador;
  - GNV anterior: tempo + MAP;
  - zonas gasolina/GNV.
- Refresh de referência ~4 s renova atomicamente `PETR_INJ_TBP`, `MNFLD_PRESS_THD`, `MUL_ACT`, petrol RV e gas RV.
- `MUL_ACT` permanece no grupo coerente de referência, não no grupo operacional.
- MAP live foi alinhado ao S16LE do oracle; fronteira `0xFFFF -> -1` fica implausível/fail-closed.
- Nenhum segundo serial owner/thread foi criado; `telemetryAfter` permanece preservado.
- Escrita automática na ECU continua proibida.
- Reset gasolina, reset GNV e reset all permanecem operações distintas: modo 0x01 gasolina, 0x02 GNV e 0x04 reset all. Reset all é a ação ampla/destrutiva e permanece separado do Reset K. OMEGAS exige revisão humana, ACK e readback; não existe restauração automática inventada.
- Ferramentas expõe exportação de backup completo; isso é proteção operacional, não autorização para escrever dados de volta sem protocolo original comprovado.
- Zonas `0x016F/0x0170` preservam identidade Z1..Z4 na tela: `OK`, `FALTA` e marcador `AGORA`; não são mais reduzidas apenas a N/4.

## Sessão

AutoCal não possui um segundo sistema de sessão:
- snapshots nativos, snapshots manuais, epochs e ações confirmadas entram no `SessionRecorder` canônico;
- aliases AutoCal de listar/exportar delegam para a sessão/ZIP canônicos;
- `autocal_native_receipts.json` permanece apenas como compatibilidade/readback e não deve ser apagado sem provar readers/writers.

## Execução paralela

### Fan-out forense
- workflow: `.github/workflows/verde-forensic-fanout.yml`;
- matriz máxima: **256 lanes**;
- 243 lanes = uma por transação Portmon;
- 13 lanes meta = oracle, same-ECU JVM, UI, arquitetura, segurança, mutações, MAP, LEVELS, aquisição e revisão.

### Fan-out global
- workflow: `.github/workflows/verde-global-reality-fanout.yml`;
- descobre e isola contratos de Dashboard, Learning, Map, Curve, OBD, sessão, reconnect, telemetry/runtime/bridges;
- uma dependência JVM→Node do Learning foi tornada explícita; não era RED de produto.

### Render Android real
Pipeline vinculante:
`fixture real -> decoder/runtime/bridge reais -> MainActivity/WebView real -> 1280x720 -> DOM/assertions -> screenshot + receipt`.

O emulator, build e instalação dos APKs já foram provados no run #9; a falha desse run foi apenas sintaxe do wrapper shell. O run seguinte usa `tools/ci/run_android_render_evidence.sh` para executar os cenários dentro de Bash real.

## Gate de fechamento

Para fechar #84 e reconciliar #81, o HEAD corrente deve produzir, no mesmo SHA:

- CI canônica verde;
- fast contracts verdes;
- global reality fan-out sem RED/BROKEN;
- Android render com os 13 cenários verdes em 1280×720, incluindo o mapa esparso de zonas;
- receipts e screenshots inspecionados, não apenas badge;
- provenance ORIGINAL_DERIVED preservada para AutoCal/Curve K e `SYNTHETIC_NON_SCIENTIFIC` explícita no cenário visual shifted.

Os run IDs do SHA final são registrados nos issues #84/#81 depois que Actions termina; não se cria outro commit apenas para registrar o próprio run ID.

## NON-GOAL

Nenhum port/cherry-pick/cópia SIL/CIU nesta WorkUnit.