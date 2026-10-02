# WU-006 — Replay da Equivalência Refinada nas sessões reais

Comando de reprodução, em `tools/autocal_refine`:
`python3 refined_oracle.py ../../fixtures/autocal/real/<fixture>.json.gz <seq>`.
A versão Kotlin produz os mesmos valores, com diferença de no máximo 1 LSB (`tests/test_refined_autocal_kotlin_parity.py`).

As métricas cobrem os índices 2–22 do eixo (0,5–13 ms), a faixa usada em GNV. Nas colunas "antes → depois":
- **Elasticidade:** |Δ ln K / Δ ln t| máxima. O limite seguro é 0,35.
- **Rugosidade:** Σ (Δ² ln K)².
- **Degrau:** maior variação entre pontos vizinhos.
- **Faixas:** faixas comuns maduras entre gasolina e GNV.
- **Pontos:** pontos alterados.

| Fixture | Seq | Modo | Faixas | Pontos | Elasticidade | Rugosidade | Degrau | 2ª passada |
|---|---|---|---|---|---|---|---|---|
| ref_2026-10-01_1719 | 95 | EQUIVALENCE | 8 | 17 | 1.92 → 0.35 | 0.0977 → 0.0046 | 12.4% → 4.2% | não |
| ref_2026-10-01_1719 | 253 | EQUIVALENCE | 8 | 17 | 1.92 → 0.35 | 0.0977 → 0.0044 | 12.4% → 4.2% | não |
| ref_2026-10-01_1719 | 962 | POLISH | 0 | 17 | 1.92 → 0.35 | 0.0977 → 0.0086 | 12.4% → 4.4% | não |
| ref_2026-10-01_1719 | 2183 | POLISH | 0 | 17 | 1.92 → 0.35 | 0.0977 → 0.0086 | 12.4% → 4.4% | não |
| ref_2026-10-01_1719 | 2550 | POLISH | 3 | 0 | 0.00 → 0.00 | 0 → 0 | 0% → 0% | não |
| automatch_2026-10-01_1301 | 53 | EQUIVALENCE | 9 | 17 | 3.73 → 0.96 | 0.6769 → 0.0399 | 23.4% → 9.5% | sim |
| automatch_2026-10-01_1301 | 1401 | POLISH | 1 | 0 | 0.00 → 0.00 | 0 → 0 | 0% → 0% | não |
| automatch_2026-10-01_1301 | 1716 | POLISH | 0 | 17 | 2.44 → 0.48 | 0.1669 → 0.0185 | 18.4% → 5.8% | sim |
| automatch_2026-10-01_1301 | 2262 | EQUIVALENCE | 4 | 21 | 2.44 → 0.48 | 0.1669 → 0.0131 | 18.4% → 6.1% | sim |
| gnv_only_2026-09-30_0931 | 37 | EQUIVALENCE | 14 | 20 | 1.88 → 0.35 | 0.2282 → 0.0078 | 22.9% → 6.1% | não |
| gnv_only_2026-09-30_0931 | 1856 | POLISH | 0 | 19 | 1.88 → 0.35 | 0.2282 → 0.0130 | 22.9% → 6.5% | não |
| gnv_only_2026-09-30_0931 | 2336 | EQUIVALENCE | 7 | 20 | 1.88 → 0.35 | 0.2282 → 0.0099 | 22.9% → 7.6% | não |

As demais linhas de cada sessão são iguais às vizinhas e podem ser reproduzidas com o mesmo comando.

## Curva de referência do proprietário (seq 95)

```
atual   0.843 ×4, 0.909, 0.884, 0.936, 0.949, 0.919, 0.865, 0.950, 0.926, 0.926, 0.889, 0.900, 0.925, 1.039, 1.087, 1.032, 0.978 ×11
refinada 0.843 ×3, 0.848, 0.880, 0.917, 0.940, 0.941, 0.933, 0.933, 0.936, 0.930, 0.919, 0.916, 0.939, 0.960, 0.981, 1.001, 1.017, 0.999, 0.978 ×10
```

O nível médio nos índices 4–18 fica a menos de 4% do original:
- o buraco do índice 9 sobe de 0,865 para 0,933, depois que a banda 9 foi descartada como outlier nos dois combustíveis;
- a corcova dos índices 16–17 (1,039/1,087) vira uma rampa (0,981/1,001);
- a cabeça e a cauda sem evidência não mudam.

## Simulador de laço fechado (seq 95)

| Modelo | α calibrado (reproduz o tranco 8,0–8,5 ms) | Curva atual | Curva refinada |
|---|---|---|---|
| sem atraso | 0,74 | ciclo-limite 8,05↔8,61 ms | estável até α = 1,48 (margem de 2,0×) |
| atraso de 1 frame | 0,37 | ciclo-limite (0,61 ms) | estável até α = 0,73 (margem de 1,97×) |

## Reprodutibilidade (gnv_only, aquisições de GNV independentes)

Curvas refinadas a partir da seq 37 e da seq 2336, antes e depois do RESET_GAS: diferença máxima de menos de 4% nos índices 4–19 (`test_independent_gas_acquisitions_agree`).

## AutoMatch nativo (automatch, 16:10:32Z)

`native_automatch_replica` sobre o snapshot seq 1716, partindo de MUL_ACT = 1,0, coincide com a ECU com diferença de no máximo 10 LSB (0,06%) nos índices 4–29. O piso é 12288 (0,75) e o teto é 19661 (1,20). A cabeça (índices 0–3 = 13107, 0,80) não foi explicada.

Limite: tudo isto é replay e simulação. Nenhuma escrita na ECU nem validação veicular foi feita.
