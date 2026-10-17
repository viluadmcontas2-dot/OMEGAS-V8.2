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

**O que a ECU faz num AutoMatch (observado nos AutoMatches nativos, LANDI 17).** Copia buffers do GNV para `*_PREV`; zera buffers/contadores/zonas do GNV; reescreve `MUL_ACT` multiplicando por fator por nó com limite de passo (+25/+12/+6 %…); incrementa `NUM_AUTOMATCH_EXECUTED`; buffers da gasolina ficam. Para sozinha ao chegar em `MAX_AUTOMATCH` (3).

**Nosso app (já igual ao original no fluxo).** `AutoCalNativeActionManager.executeFixedAction`: envia → exige ACK → espera 1 s (`HOST_MODE_SETTLE_MS`) → relê `MUL_ACT` (testemunha) → confirma só com a leitura de volta. Botão escondido até `autoMatchQuotaReached`. Sessão grava o recibo e a curva nova.

**Não provado (UNKNOWN).** (1) Nunca houve captura de modo 8 no fio. (2) Efeito real de um 4º AutoMatch (limite de passo, se a ECU recusa acima da cota). (3) Condição que dispara o AutoMatch nativo. (4) Regra de habilitação do botão no original. Primeiro uso no carro deve ser com a sessão ligada para fechar (1) e (2).

**Diferença deliberada.** O original pede caixa de confirmação; o app usa um toque (decisão do dono 2026-10-13: ações em um toque, proteção = foto da preparação + readback).
