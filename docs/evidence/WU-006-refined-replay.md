# WU-006 — Evidência da Equivalência Refinada (sessões reais)

**Reprodução**, a partir de `tools/autocal_refine`:
- `python3 refined_oracle.py ../../fixtures/autocal/real/<fixture>.json.gz <seq>`
- `python3 blind_telemetry_test.py <fixture> <seq>`
- `python3 calibrate.py`

A paridade Kotlin↔Python fica em até 1 LSB (`tests/test_refined_autocal_kotlin_parity.py`).

## Critério: funcional, não estético

O serrilhado em si não é defeito. A curva só deve mudar quando isso aproxima o GNV do que a gasolina pede. Toda constante do motor foi julgada por um destes dois testes:

1. **Validação cruzada:** tira-se uma faixa de GNV medida pela ECU, refina-se sem ela e mede-se o erro ao prever o K que essa faixa pede.
2. **Teste cego de telemetria:** cada leitura estável em GNV é comparada com leituras estáveis em gasolina no mesmo RPM (±150) e MAP (±0,02 bar), na sessão inteira. Isso dá o K que a condução real pede. O motor não usa telemetria, então ela funciona como juiz independente.

## Teste cego de telemetria

| Sessão (snapshot) | Leituras | Erro da curva atual | Erro da curva refinada |
|---|---|---|---|
| 01/10 17:19 (seq 95, a curva "boa") | 135 | 7,7% | **5,7%** |
| 01/10 13:01 (seq 53, curva após AutoMatches nativos) | 43 | 8,4% | **3,3%** |

Por faixa, na sessão das 17:19:

| Faixa (ms) | n | Erro atual | Erro refinada | Viés atual | Viés refinada |
|---|---|---|---|---|---|
| 1,5–3,0 | 7 | 17,0% | 15,5% | +15,6% | +14,2% |
| 3,0–4,5 | 40 | 3,5% | 3,7% | −0,8% | −1,0% |
| 4,5–6,0 | 21 | 3,4% | 4,5% | +1,1% | +3,9% |
| 6,0–7,5 | 4 | 4,4% | 4,2% | +0,4% | −0,4% |
| 7,5–9,0 | 18 | 9,4% | **5,8%** | +6,6% | **−0,4%** |
| 9,0–12,0 | 45 | 8,9% | **4,8%** | +6,6% | **+1,7%** |

Leitura:
- **Entre 7,5 e 12 ms**, a curva atual está cerca de 6,6% rica, por causa do degrau de 8 a 9 ms. A refinada remove quase todo esse viés. É a faixa de carga alta onde você sente a puxada.
- **Entre 4,5 e 6 ms**, a refinada fica 3,9% rica. O motor descartou a banda 9 como outlier e levantou o buraco de 5 ms, mas a telemetria indica que parte desse buraco era real. Isso fica registrado como limitação.
- **Entre 1,5 e 3 ms**, as duas curvas erram cerca de 15%: há poucas leituras e nenhuma faixa nativa madura ali. O motor mantém a curva, como deve.

## Seleção das constantes

| Constante | Valores testados | Escolha e evidência |
|---|---|---|
| λ (rigidez) | 0 … 10 | 0,3. Na validação cruzada, o erro ao prever faixa omitida é 3,2%; com 0, 3,7%; sem corrigir a curva, 6,0% |
| E_MAX (inclinação) | 0,35 / 0,6 / 1,0 / sem trava | 0,35. No teste cego dá 5,7%, contra 6,0–6,1% com a trava mais frouxa ou sem trava; na sessão 13:01, empate em 3,3% |
| Rejeição de outlier | com / sem | Neutra no teste cego (5,7% vs 5,7%). Mantida porque um tempo de injeção que cai quando o MAP sobe é fisicamente impossível |

## Hipótese descartada

A alternância de 8,0↔8,9 ms a cada leitura **não é o tranco**. Ela aparece também na gasolina, em 25–33% das leituras estáveis acima de 7,5 ms (`test_petrol_itself_zigzags_so_alternation_is_not_the_jolt`).

O simulador de "ciclo-limite" usado na primeira versão foi removido. A trava de inclinação continua porque o teste cego a confirma.

O tranco descrito pelo proprietário é a não linearidade da puxada. Um degrau de K faz o gás entregue variar fora de proporção com o pedido da gasolina. Isso aparece como viés que muda de sinal ou de tamanho entre faixas vizinhas (por exemplo, +1% em 6 ms e +6,6% em 8–9 ms).

## Replay por snapshot

| Fixture | Seq | Modo | Faixas | Pontos | Maior mudança | Inclinação máx. | Erro vs medição nativa | 2ª passada |
|---|---|---|---|---|---|---|---|---|
| ref_2026-10-01_1719 | 95 | EQUIVALENCE | 8 | 17 | 8,2% | 1,92 → 0,35 | 2,1% → 0,5% | não |
| ref_2026-10-01_1719 | 962 | POLISH | 0 | 17 | 8,6% | 1,92 → 0,35 | — | não |
| ref_2026-10-01_1719 | 2550 | POLISH | 3 | 0 | 0% | 0 → 0 | — | não |
| automatch_2026-10-01_1301 | 53 | EQUIVALENCE | 9 | 18 | 15,0% | 3,73 → 0,96 | 11,2% → 3,6% | sim |
| automatch_2026-10-01_1301 | 1401 | POLISH | 1 | 0 | 0% | 0 → 0 | — | não |
| automatch_2026-10-01_1301 | 1716 | POLISH | 0 | 19 | 15,0% | 2,44 → 0,48 | — | sim |
| automatch_2026-10-01_1301 | 2262 | EQUIVALENCE | 4 | 21 | 15,0% | 2,44 → 0,48 | 24,1% → 8,5% | sim |
| gnv_only_2026-09-30_0931 | 37 | EQUIVALENCE | 14 | 20 | 12,9% | 1,88 → 0,35 | 4,4% → 2,0% | não |
| gnv_only_2026-09-30_0931 | 1856 | POLISH | 0 | 19 | 12,9% | 1,88 → 0,35 | — | não |
| gnv_only_2026-09-30_0931 | 2336 | EQUIVALENCE | 7 | 22 | 11,7% | 1,88 → 0,35 | 8,0% → 4,3% | não |

Curva de referência (seq 95):

```
atual    0.843 ×4, 0.909, 0.884, 0.936, 0.949, 0.919, 0.865, 0.950, 0.926, 0.926, 0.889, 0.900, 0.925, 1.039, 1.087, 1.032, 0.978 ×11
refinada 0.843 ×3, 0.853, 0.882, 0.916, 0.938, 0.941, 0.935, 0.935, 0.935, 0.929, 0.916, 0.914, 0.936, 0.958, 0.978, 0.998, 1.017, 1.001, 0.978 ×10
```

## AutoMatch nativo (01/10, 16:10:32Z)

`native_automatch_replica` sobre o snapshot seq 1716, partindo de MUL_ACT = 1,0, coincide com a ECU em até 10 LSB (0,06%) nos índices 4–29: ganho total, piso 0,75, teto 1,20. A cabeça (índices 0–3 = 0,80) não foi explicada.

**Limite:** isto é replay de dados reais e teste cego offline. Nenhuma escrita na ECU nem validação veicular foi feita.
