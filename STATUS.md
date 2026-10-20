# STATUS

## v8 — APK "versão nova" (anti-apagão) — 2026-10-18
- Commit: eb8c9e8 · Run APK: 39174963145 (verde) · Artefato: omegas-vermelho-apk-eb8c9e8… (id 12153747842)
- SHA-256 do APK: 476db1ce577c332ed4228d48412a9d8ccebaab0f0625e3d620f4eee6498dbf27 (4,8 MB)
- Novidades: botão manual do anti-apagão (prévia → 2º toque → confirmação da central → Desfazer), 5 números editáveis, cartões de Ferramentas em linguagem simples, aba Sessões, Marcha lenta (leitura+sugestão), app -52% de código no arranque.
- Testes: 431 UI verdes locais; CI do commit em execução no momento do corte. O app continua NUNCA gravando sozinho.

# OMEGAS — Status

Canônica: **`OMEGASCANONICO`** (padrão do GitHub desde 2026-10-20). Plano vigente: `docs/handoff/REFINO.md` (plano 10-16 incluído; D1 superado). Evidência: `docs/evidence/2026-10-16-evidencia-arrancada-e-automatch.md`.

## Último APK (2026-10-18, v7: o app NÃO grava sozinho)

- Decisão do dono (2026-10-18): gravar é sempre manual; o AutoMatch manual só roda por toque do operador. O modo LIGADO do D1 foi removido (serviço recusa `ON`; valor antigo vira SOMBRA).
- `ci.yml` run `39147470313` (SUCCESS) e `verde-apk-now.yml` run `39147499050` (SUCCESS), commit `b725ed79dc6a37ec014c03ea52bc696c8ef09883`.
- Artifact `omegas-vermelho-apk-b725ed79…` (id `12144171414`), 4.856.222 bytes, digest do ZIP `sha256:08a4ac795aeb636f55575ec39aafae2247ae6f3f740b65bb1b58bd99c3102eb3`. SHA do `app-debug.apk` interno: UNKNOWN.
- Contém: D1 só SUGERE (`d1_decision` na sessão; interruptor SOMBRA/DESLIGADO em Ferramentas); sonda de escrita; `param_snapshot`; `autocal_pre_automatch_state`; conselho do AutoMatch manual; tudo do v6. Não validado no carro.
- O v6 (abaixo) ainda tem o modo LIGADO: não usar.

## Último APK (2026-10-18, v6: refino automático D1 em modo SOMBRA)

- Workflow `verde-apk-now.yml`, run `39143498352` (SUCCESS), commit `bb7de818ef83724f92453550e66d6fb0925da8a3`.
- Artifact `omegas-vermelho-apk-bb7de818…` (id `12142657461`), 4.856.786 bytes, digest do ZIP `sha256:71284f041496a2d66153fe7831a6c7db969766591444bd006ec6f3cfd9a7e48a`. SHA do `app-debug.apk` interno: UNKNOWN.
- Contém: D1 (refino automático após AutoMatch) com padrão **SOMBRA** (só calcula e registra `d1_decision`, nunca grava) e interruptor DESLIGADO/SOMBRA/LIGADO em Ferramentas (LIGADO exige toque duplo); sonda de escrita; `param_snapshot`; `autocal_pre_automatch_state`; conselho do AutoMatch manual; tudo do v5.
- NÃO contém: correção do Mapa K por faixa de RPM, suavização separada, re-escala do Ledger.
- Não validado no carro. Detalhes: `docs/handoff/REFINO.md`.

## Último APK (2026-10-18, v5: sonda de escrita + parâmetros reais + conselho do AutoMatch manual)

- Workflow `verde-apk-now.yml`, run `39095634820` (SUCCESS: contratos, testes JVM, lint e build), commit `015c33b6a860a577a5523fb78520dd6fa7789e1d`; `ci.yml` run `39095595581` (SUCCESS) no commit anterior `dda66a9`, só docs de diferença.
- Artifact `omegas-vermelho-apk-015c33b6…` (id `12124129704`), 4.847.822 bytes, digest do ZIP `sha256:6b0a83cd9affc0604cd6a6e748ec89488a64f7be98d223f9884a02f10aa7e814`. O SHA-256 do `app-debug.apk` de dentro do ZIP não foi lido por este agente (UNKNOWN até alguém baixar o artefato).
- Contém: sonda de escrita única nos pontos do GNV (Ferramentas, toque duplo); `param_snapshot` (33 parâmetros, só leitura) por conexão; `autocal_pre_automatch_state` antes de cada AutoMatch; conselho na nota do botão AutoMatch manual (regra provisória); tudo do v4.
- NÃO contém: re-escala do Ledger por K (evidência contra: `docs/evidence/2026-10-18-pares-do-ledger-nao-seguem-o-k.md`), nem gravação automática (D1).
- Não validado no carro: efeito da sonda, do AutoMatch manual e dos conselhos. Detalhes: `docs/handoff/REFINO.md`.

## Último APK (2026-10-17, v4: AutoMatch manual de volta + sessão completa)

- Workflow `verde-apk-now.yml`, run `39002502126` (SUCCESS, testes + lint + build) e `ci.yml` run `39002494915` (SUCCESS), commit `a0a1953061f51480e53801448cf9916e1e4498e3` (branch `OMEGASCANONICO`).
- Artifact `omegas-platina-final-a0a19530…` (id `12089269595`), 4.824.592 bytes, digest do ZIP `sha256:c9002b8cb18195ab9afa73a0d43c0531a5b18084734a7b7850c024d00a8878ef`; expira 2026-10-31. Link: https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/39002502126/artifacts/12089269595
- **SHA-256 do APK** (`app-debug.apk`, 4.823.809 bytes): `970ba72179808e2f0fc5a2124bac152f8eabf549be516fc63bac348306740970` (`APK_ZIP_INTEGRITY=PASS`).
- Contém: botão **AutoMatch manual** no AutoCal (modo `0x08`, escondido até a cota automática da ECU; um toque, ACK + leitura de volta); tudo do v3 (leitura automática de curva e Mapa K, `k_read_*`/`k_*_state`/`engineReview`/`ecu_command`, Curva→Mapa parcial 50% e mapa ≤ 200, prior dos nós baixos). Substitui `1e5d749`. NÃO instalado no carro (classe 5).

## Em andamento (2026-10-16)

Fase 1 do plano (porta de gravação no toque, Curva→Mapa recuperável, persistência à prova de queda, "piorou" honesto, apagar Wi-Fi/Link e limite de passo, sessões e aprendizado leves) na branch `OMEGASCANONICO`; entra na Vermelho por PR com CI verde no SHA. Decisões do dono: D1 = Refino grava sozinho após cada AutoMatch (só travas absolutas); D2 = sem API do Claude no app.

---
Histórico (Platina, até 2026-10-02):
## Active control surface

- Branch: `OMEGASCANONICO`
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
