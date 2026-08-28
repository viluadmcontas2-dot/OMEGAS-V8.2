# 102B — AutoCal Native Observer + MAP × Tpet contract freeze

Status: `PASS_CONTRACT_FREEZE`

Este documento congela o contrato antes do owner 103. Ele não implementa uma segunda AutoCal e não reproduz a fórmula proprietária do AutoMatch.

## Autoridades

- `ECU_NATIVE_AUTOCAL`: autoridade apenas sobre progressão/maturidade/counters/AutoMatch nativos observados.
- `OMEGAS_CORRELATION`: autoridade somente sobre a correlação observacional entre evento nativo e `CanonicalEvidence`/telemetria tipada.
- `ECU_MATURED != OMEGAS_EQUIVALENT`.
- Nenhum anchor AutoCal escreve ECU, escolhe K diretamente ou substitui `GasolineOracle → KStarEstimator → Target/Step`.

## Famílias físicas que não podem ser misturadas

### Aquisição AutoCal — 18 bandas

- `NUM_BUF_UPD_PETR`
- `NUM_BUF_UPD_GAS`
- `PETR_INJ_TBUF`
- `MNFLD_PRESS_BUF`
- `PETR_INJ_TBUF_GAS`
- `MNFLD_PRESS_BUF_GAS`
- `PETR_INJ_TBUF_GAS_PREV`
- `MNFLD_PRESS_BUF_GAS_PREV`

Shape: **18** elementos. Zonas: `0–5 | 6–9 | 10–13 | 14–17`.

### Vetores/curva/referência — 30 pontos

- `MUL_ACT`
- `PETR_INJ_TBP`
- `PETR_MNFLD_PRESS_RV`
- `GAS_MNFLD_PRESS_RV`

Shape: **30** elementos quando o próprio field físico é conhecido como família 30. `moduleVersion` não pode reduzir essa família globalmente para 18. Evidência real registrada: `moduleVersion=100` com 30 elementos válidos nesses vetores.

## Projection futura

- gráfico de aquisição: `X = Tpet nativo`, `Y = MAP nativo`;
- 18 pontos/bandas por família de aquisição, não 30 bolinhas;
- ponto sem Tpet/MAP comprovados = `UNPOSITIONED`;
- RPM nunca é derivado do índice da banda;
- janela sem correlação suficiente = `INCONCLUSIVE`, `rpm=null`;
- vetores de 30 pontos permanecem namespace de curva/eixo/linha de referência.

## Eventos

- progressão gasolina e GNV são observadas separadamente;
- maturity preserva counter, zone, Tpet/MAP, session, timestamps e payload/hash;
- anchor correlacionado preserva overlap/provenance para anti-double-count;
- AutoMatch count/MUL_ACT material invalida a identidade anterior e exige reconcile;
- AutoMatch observado nunca é apresentado como “melhorou” sem evidência posterior.

## Runtime/performance

- usa o `Mp48SerialScheduler` existente;
- usa o health tick/owner existente;
- abrir/fechar tela não cria probe;
- snapshot pesado apenas por evento material/reconcile explícito;
- UI é projection revision-driven e não fonte científica.

## Falsificadores

FAIL se qualquer um ocorrer:

1. 30-point vector virar 30 bandas/bolinhas de aquisição;
2. `moduleVersion` converter indiscriminadamente 30 → 18;
3. índice da banda fabricar RPM;
4. janela vazia/stale produzir RPM;
5. UI abrir segundo monitor/probe;
6. render disparar full snapshot;
7. anchor + mesmos frames contarem duas vezes;
8. maturidade ECU virar equivalência OMEGAS automaticamente;
9. GNV anchor bypassar estimadores e gerar K/write;
10. AutoMatch count ser rotulado como melhora sem revalidação.

## Owners materiais

A implementação permanece nos owners `103/103A`, `110`, `117/117A`, `119/119A`, `121`, `122/122A`. Este arquivo só congela o contrato que esses owners devem satisfazer.
