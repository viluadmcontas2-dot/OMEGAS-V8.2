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
