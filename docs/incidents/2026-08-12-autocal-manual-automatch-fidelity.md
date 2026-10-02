# Incidente corrigido — AutoCal Manual AutoMatch e identidade dos modos

## Status
**SUPERADO POR EVIDÊNCIA BYTE-GROUNDED MAIS FORTE EM 2026-09-28.**

Este arquivo registrava uma interpretação intermediária que trocava as identidades das ações AutoCal. Ele não deve mais ser usado como autoridade.

## Mapeamento canônico atual

A linha limpa `progbase-forensics/forensics/chat-evidence-20260928` provou o wrapper comum do ProgBase:

| ação | modo | frame |
|---|---:|---|
| `ActionResetPetrolExecute` | `0x01` | `02 24 04 01 2B` |
| `ActionResetGasExecute` | `0x02` | `02 24 04 02 2C` |
| `ActionResetAllExecute` | `0x04` | `02 24 04 04 2E` |
| `ActionAutoMatchExecute` | `0x08` | `02 24 04 08 32` |

O helper monta `command=0x24`, payload `[0x04, mode]`. O `ResetAll` também foi observado no Portmon autorizado com echo exato e resposta `53 00 53`.

`ActionAutoCalRifExecute` e `ActionResetKFactorExecute` são caminhos separados.

## Separação essencial

Duas coisas coexistem:

1. a ECU executa AutoMatch nativo e altera `MUL_ACT`/contador sem o PC enviar o frame manual em cada época observada;
2. o ProgBase possui uma ação explícita **Manual AutoMatch** que envia modo `0x08`.

Logo o Ômegas observa o AutoMatch nativo sem escrita automática, mas pode oferecer Manual AutoMatch como ação explícita, com confirmação, ACK e readback.

## Dimensão 18/30

A regra histórica `MODULE_VERSION == 4 ? 30 : 18` foi refutada como regra de protocolo. O branch `selector==4` pertence a `TAebNumber` genérico.

Os objetos de referência/K já comprovados como 30 pontos são:
- `PETR_INJ_TBP`;
- `MUL_ACT`;
- `PETR_MNFLD_PRESS_RV`;
- `GAS_MNFLD_PRESS_RV`.

Isso inclui captura com `MODULE_VERSION=100`.

## ENABLE/DISABLE

Os writes `AUTO_CAL_ENABLE=1/0` são provados e o readback segue obrigatório. O app não deve prometer que `0` é apenas uma pausa; o efeito operacional completo continua pertencendo à ECU.

## Regressão obrigatória

- quatro frames de ação exatos;
- Manual AutoMatch humano, nunca automático;
- 30 pontos independentes de `MODULE_VERSION` nos objetos acima;
- ACK/readback/session fencing para mutações.
