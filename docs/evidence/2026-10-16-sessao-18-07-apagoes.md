# Sessão 18-07 (Novas) — 1 apagão + 3 quase-apagões

Fonte: Drive `OMEGAS/Novas/OMEGAS_Sessao_2026-10-16_18-07.zip` (`events_0001.jsonl`, 1398 quadros, 4 `engine_stall`, 3 `k_factor_batch_confirmed`). Lido por script, sem inferir além dos dados.

| Hora (UTC) | Tipo | rpm antes | Petrol ms | MAP bar | Detalhe |
|---|---|---|---|---|---|
| 21:09:27 | STALL | 1339 | 2,04 | 0,198 | desacelerando; mín. 1,53 ms |
| 21:10:58 | NEAR_STALL | 1597 | 1,73 | 0,188 | corte de combustível, caiu a 476 rpm |
| 21:11:55 | NEAR_STALL | 1615 | 1,82 | 0,198 | corte, caiu a 434 rpm |
| 21:13:05 | NEAR_STALL | 1474 | 1,56 | 0,188 | corte, caiu a 453 rpm |

**Curva K nos nós 0–3 durante os eventos** (gravações do dono): 1,27–1,31 no apagão (21:08:37); depois 1,028 (21:09:47, 20 s depois do apagão); depois 1,19–1,21 (21:11:30).

**O que está provado:** os 4 eventos têm a mesma assinatura: desaceleração em carga mínima (MAP ≈ 0,19 bar), injeção da gasolina de 1,5–2,0 ms, rpm entre 1340 e 1615, 3 deles logo após corte de combustível (petrol_ms chegou a 0). Aconteceram com os nós 0–3 em ≈1,30, em 1,03 e em ≈1,2: o valor do K nesses nós, sozinho, **não** separa quem apaga de quem não apaga.

**Hipótese (não provada):** o problema é a **transição** (corte de desaceleração → retomada de injeção perto de 1,5–2 ms), dinâmica, não o valor estático da curva. Só 4 eventos, 1 carro: não generalizar.

**Consequência de método:** o detector precisa registrar a janela de ±4 s em torno do evento (rpm, petrol_ms, gas_ms, MAP, corte sim/não) e comparar com as retomadas da gasolina sem apagão, na mesma faixa (MAP < 0,25 bar, 1,0–2,5 ms). Só então decidir nós/mapa.

## Teste da re-escala K_novo/K_velho (3 gravações da mesma sessão)

Método: razão gas_ms/petrol_ms (mediana) nos 60 s antes e 3–60 s depois de cada gravação, por faixa de petrol_ms; comparada com K_novo/K_velho dos nós gravados.

| Gravação | Nós | Previsto | Medido (0,5–2,5 ms) | n antes/depois |
|---|---|---|---|---|
| seq 410 (21:09:47) | 0–3: 1,30 → 1,03 | ×0,79 | ×0,776 (3,121 → 2,421) | 6/6 |
| seq 733 (21:11:30) | 0–3: 1,03 → 1,19 | ×1,16 | ×1,134 (2,409 → 2,732) | 15/7 |
| seq 181 (21:08:37) | 2–4: +2,6% | ×1,026 | ×1,09 (2,862 → 3,121); faixa 2,5–4 ms: ×1,028 | 8/6 |

**Leitura:** nas duas gravações grandes a previsão ficou dentro de ~3%; a pequena é ruidosa (n=6–8). **Limite importante:** gas_ms_diagnostic é o tempo comandado, então escalar com o K é em parte aritmética; isto NÃO prova que o motor se comporta de forma equivalente. Vale para a métrica de pares do Ledger (que usa exatamente essa razão), não para "o carro anda igual".

**Decisão de método:** re-escalar os pares GNV existentes por K_novo/K_velho (por nó) em vez de apagá-los; peso reduzido e nunca como único critério para gravar; substituir por leituras novas conforme chegam. A hipótese continua válida só para a razão de ms até haver mais gravações (precisa de n ≥ 30 por faixa).

**Tentativa anterior (Codex, `wip/codex-fatia1b`) NÃO mesclada:** quebrou 19 de 667 testes no CI (EquivalenceLedgerTest, RefinementCycleTest, RefinementReviewTest, LiveOnlyLearningStoreTest). Refazer em passos pequenos, cada um verde no CI.
