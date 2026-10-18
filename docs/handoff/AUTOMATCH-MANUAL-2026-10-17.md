# AutoMatch manual — de volta (decisão do dono, 2026-10-17)

**Ideia do dono:** a ECU faz 3 AutoMatches sozinha (cota `MAX_AUTOMATCH`). Depois disso as bandas (os quadradinhos = médias de MAP × Petrol Inj. de cada banda, `*_BUF`/`*_BUF_GAS` + contadores) continuam acumulando. O **AutoMatch manual** manda a ECU aplicar o AutoMatch de novo sobre essas médias já mais maduras; por isso o botão só faz sentido depois da cota automática.

## Evidência no repositório (nada novo no fio)
- Comando: `ActionAutoMatchExecute` → modo `0x08` → `02 24 04 08 32` (`docs/incidents/2026-08-12-…`, `2026-09-19-autocal-final-byte-matrix.md`; `AutoCalProtocolTest`).
- Backend já existia e está testado: `AutoCalNativeActionManager.Action.MANUAL_AUTOMATCH` (prepara → confirma → ACK → leitura de volta de `MUL_ACT`), exposto na ponte (`prepareNativeAction("MANUAL_AUTOMATCH")`, `manualAutoMatchExposed: true`). `NativeAutoCalAcquisitionEpoch` e `RefinementAutopilot` já tratam a readquisição depois dele.
- O que faltava era só o botão na tela (removido em 2026-10-13 por decisão anterior; revogada hoje).

## O que mudou
- Botão **AutoMatch manual (aplica as médias)** no rodapé do AutoCal, nasce escondido; aparece com `autoMatchQuotaReached` (NUM_AUTOMATCH_EXECUTED ≥ MAX_AUTOMATCH) e aprendizado ligado. Um toque, como as outras ações (prepara+executa; proteção = foto da preparação + readback).
- Testes antigos que proibiam o botão foram invertidos (`autocal-cockpit`, `autocal-reacquisition-ux`, `test_v82_integral_regression_contract`) — mudança de regra do dono, não enfraquecimento.
- A sessão registra o resultado: `autocal_native_action` (recibo), `k_curve_state` (curva nova, antes, `engineReview`).

## Ainda aberto (precisa do carro)
- Quando vale apertar: critério por evidência (ex.: nº de bandas maduras e erro do `engineReview` maior que X). Hoje o botão aparece com a cota atingida e o dono decide.
- Efeito físico em motor real (primeiro uso no carro, observando a curva antes/depois na sessão).

## Ponta a ponta (levantamento 2026-10-17, PROTOCOLO-LANDI + Ghidra do ProgBase + nosso código)

**Fio.** Quadro `02 24 04 08 32` (comando AutoCal `0x24`, carga `[04, modo]`, modo 8; checksum `32`). Resposta esperada: ACK `53 00 53`. Mesmo molde dos Reset Gas/Reset All (`…04 02 2C`, `…04 04 2E`), esses sim vistos em captura real.

**Original (ProgBase).** Botão `ButtonAutoMatch` (Tag=8) ao lado de "Num automatch N / [M]". `ActionAutoMatchExecute` → `CommonAction`: confere conexão → caixa de confirmação → ampulheta → envia `[04, Tag]` → sucesso: `Sleep(1000)` + `RefreshAll`; falha: caixa de erro. O `[M]` é editável (`MAX_AUTOMATCH`, `0x0165:2`, ligado a `VECT_AUTOCAL_U8_2`).

**O que a ECU faz num AutoMatch (observado nos AutoMatches nativos, LANDI 17).** Copia buffers do GNV para `*_PREV`; zera buffers/contadores/zonas do GNV; reescreve `MUL_ACT` multiplicando cada nó por uma razão (K novo = K antigo × razão, OBSERVADO; é correção sobre correção). Razões repetidas (1,25/1,12/1,06; 0,88) sugerem um limite de passo, mas é INFERÊNCIA de 7 eventos: AC deu +25/+12/+6 %, LN deu +25/−12/+6 %, K passou de 1,25 (1,40/1,48). Regra DESCONHECIDA; incrementa `NUM_AUTOMATCH_EXECUTED`; buffers da gasolina ficam. Para sozinha ao chegar em `MAX_AUTOMATCH` (3).

**Nosso app (já igual ao original no fluxo).** `AutoCalNativeActionManager.executeFixedAction`: envia → exige ACK → espera 1 s (`HOST_MODE_SETTLE_MS`) → relê `MUL_ACT` (testemunha) → confirma só com a leitura de volta. Botão escondido até `autoMatchQuotaReached`. Sessão grava o recibo e a curva nova.

**Não provado (UNKNOWN).** (1) Nunca houve captura de modo 8 no fio. (2) Efeito real de um 4º AutoMatch (limite de passo, se a ECU recusa acima da cota). (3) Condição que dispara o AutoMatch nativo. (4) Regra de habilitação do botão no original. Primeiro uso no carro deve ser com a sessão ligada para fechar (1) e (2).

**Diferença deliberada.** O original pede caixa de confirmação; o app usa um toque (decisão do dono 2026-10-13: ações em um toque, proteção = foto da preparação + readback).

## Buffers e tela de configurações do original (levantamento 2026-10-17, LANDI 19/22 + DFM)

- **Quem escreve os pontos de aquisição:** a ECU, sozinha. O app só lê (`29 xx 01`). No DFM do original (`TAUTOCALDM`) `PETR_INJ_TBUF`, `MNFLD_PRESS_BUF`, `NUM_BUF_UPD_*`, `*_GAS`, `*_GAS_PREV` e as zonas são **`ReadOnly = True`**; `MUL_ACT`, `GAS_POINT_2DELETE` e `PETROL_POINT_2DELETE` não são. O original nunca escreve nos buffers (nenhum `0x30–0x37` nas 75 985 transações do LANDI).
- **Aprendizado persistente:** existem cópias EEPROM (`TAUTOCALDM_EE`, `0x014E–0x015A`: buffers GNV/gasolina, `BUF_UPD_*_EE`, `MUL_ACT_EE`, **`MUL_PREV_EE`** = curva anterior, `MUL_UPD_CALL_CNTR_EE`). O app não lê essas cópias.
- **`SetVector` vem do original** (`TAebProtocol::SetVector`, VA `0x0092617C`, conferido contra as instruções): `0x30|n` até 5 bytes de dados, `0x37`+tamanho acima. Mas é só paridade de codificação: nenhum chamador de produção no app e nenhum uso do original nos buffers.
- **Tela "Autocal settings" do original (editável):** `MAX_RPM_FOR_AUTOCAL` (400–8000, padrão 3000), `DELTA_ENG_SPD_THD`, `DELTA_MNFLD_PRESS_THD`, `DELTA_PETR_INJ_T_THD`, `DIFF_*` (3), `EN_CDN_T_THD`, "Disable highest acquisition band" (`0x018B`), período de atualização (200–2000 ms) e o vetor `CALIBRATION_VAL_1` de 12 itens (mínimos/soma de amostras por buffer, parado × andando, gasolina × GNV). O app **lê** esses campos e nunca os escreve. Eles controlam o que a ECU aceita como amostra boa (qualidade do aprendizado) — ponto aberto, precisa de decisão do dono e de captura de como o original grava.
- **Sonda de escrita única (APK):** `BufferWriteProbe(Core)`, botão "Testar escrita nos pontos" em Ferramentas (toque duplo). Banda do GNV com contador 10, +8/512 ms em um ponto, quadro `14 5F 01 idx lo hi` (mesmo tipo que grava a curva K), 4 leituras, restaura o original e confere. Desfechos: recusou / aceitou e ficou / aceitou e desfez / aceitou e guardou outro valor / sem banda cheia. Sessão grava `buffer_write_probe` + `ecu_command`. Resultado real: UNKNOWN até rodar no carro.

## Rodada de 2026-10-18 (TDD; cada item com teste e evidência)

| Item | Estado | Evidência / teste |
|---|---|---|
| Estado das bandas antes de cada AutoMatch (`autocal_pre_automatch_state`) | Feito | `PreAutoMatchStateTest.kt`; corpus: gatilho "desconhecido" porque as leituras ficavam 15–217 s longe do evento |
| Leitor de parâmetros só-leitura (`param_snapshot`, 33 parâmetros) | Feito | `EcuParameterSnapshotTest.kt`; cada endereço/largura conferido contra o download de conexão do ProgBase (PortmonLOGNOVO); 6 entradas retiradas por NACK/sem prova |
| Conselho no botão do AutoMatch manual | Feito, regra **provisória** | `tests/ui/manual-automatch-advice.test.cjs`; mediana de 9 bandas com ≥ 3 amostras nos 14 AutoMatches da ECU |
| Sonda de escrita única nos buffers | Feita (Ferramentas, toque duplo) | `BufferWriteProbeCoreTest.kt`; resultado real: **UNKNOWN** até rodar no carro |
| Re-escala dos pares do Ledger por K_novo/K_velho | **NÃO feita** | `docs/evidence/2026-10-18-pares-do-ledger-nao-seguem-o-k.md` (inclinação −0,15; esperado −1) |
| D1 (gravar sozinho após cada AutoMatch) | **NÃO feita** | decisão de 16/10 conflita com "o app nunca aplica sozinho" (17/10); aguarda o dono |
| Equivalência % por nó × RPM; mockups 1/2/3 | Pendentes | dependem de decisão do dono / dos pares do Ledger acima |

Valores reais da ECU (PortmonLOGNOVO, 12/08/2026): `ANTI_STALLO` = 1600·100·1500·2·20 (padrão de fábrica), `GIRI_MIN_CAMBIO` = 1200 rpm, `SMAGRIMENTO_RIENTRO_CUTOFF` = 100, `NUMERO_INIETTATE_SMAGRIMENTO` = 0, aquisição do AutoCal toda no padrão (400/200 rpm, 0,5/0,05 bar, 4/1 ms, 3000 rpm). O original também usa SetVector (`35 03 00 …`) em `FLAG_CONF1` (0x0003): o formato está provado no fio, mas nunca em buffers de aquisição.
