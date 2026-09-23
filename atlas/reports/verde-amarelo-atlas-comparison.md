# Verde × Amarelo × Atlas — comparação canônica

Snapshot: 2026-09-23

## Papel correto de cada linha

**Atlas — autoridade científica.** Mantém o ProgBase 4.2.0.6 e os Portmons canônicos hash-bound, fechou 1417/1417 alvos e 14/14 gates comportamentais, e resolve conflitos pela evidência original.

**Verde — consumidor de produto.** É a superfície mais avançada de implementação, UX e segurança operacional. Serve como fonte de leads e regressões reais, mas não pode substituir o EXE/logs como oracle.

**Amarelo — challenger de replay/runtime.** A maior contribuição é o replay canônico do LOGNOVO atravessando scheduler/runtime/WebView, com proveniência explícita e separação entre fixture visual e inferência científica.

## Diferenças atuais

- Atlas tem fechamento científico reproduzível e frontier zero.
- Verde atual ainda contém deriva no fixture de action map em relação ao RTTI bruto/prova Atlas; além disso, os runs CI/fast do HEAD observado estão vermelhos.
- Amarelo atual mantém fast contracts verdes, mas o render runtime do HEAD observado está vermelho por compile-time access a `nativeAutoCal` privado (além de ruído ADB). A ideia do replay continua valiosa; o harness atual não é autoridade científica.
- Atlas já absorveu do Verde as pistas válidas de zonas, indexed `0x0165`, segurança e topologia, sem promover escolhas de produto como semântica nativa.

## Refinamento incorporado

Atlas passa a ter dois challengers pós-closure:

1. **Canonical consumer replay validation:** confirma que cada transação escolhida pelo Amarelo existe byte-a-byte no LOGNOVO canônico, no mesmo índice Portmon e com a mesma regra de tempo acumulado.
2. **Consumer drift detector:** compara fixtures/oracles de consumidores com o mapa canônico do Atlas e registra conflito sem contaminar a verdade nativa.

A hierarquia permanece:

`ProgBase EXE + raw Portmon/LOGNOVO → Atlas proof → consumer compatibility (Verde/Amarelo)`.

Consumer compatibility melhora a confiança de integração; nunca substitui a evidência original.
