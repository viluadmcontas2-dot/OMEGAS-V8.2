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
