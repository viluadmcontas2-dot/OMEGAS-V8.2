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

**[SUPERADA em 2026-10-18 — ver `2026-10-18-pares-do-ledger-nao-seguem-o-k.md`: o par do Ledger (`petrol_ms` em GNV) não acompanha o K; a re-escala não foi implementada.]** **Decisão de método (anterior):** re-escalar os pares GNV existentes por K_novo/K_velho (por nó) em vez de apagá-los; peso reduzido e nunca como único critério para gravar; substituir por leituras novas conforme chegam. A hipótese continua válida só para a razão de ms até haver mais gravações (precisa de n ≥ 30 por faixa).

**Tentativa anterior (Codex, `wip/codex-fatia1b`) NÃO mesclada:** quebrou 19 de 667 testes no CI (EquivalenceLedgerTest, RefinementCycleTest, RefinementReviewTest, LiveOnlyLearningStoreTest). Refazer em passos pequenos, cada um verde no CI.

## Teste "pares da ECU × curva suave do app" — INCONCLUSIVO (não repetir assim)

Tentativa: comparar a rugosidade entre bandas vizinhas dos pares da ECU (buffers `*_BUF`/`*_BUF_GAS`) com a mediana de `gas_ms/petrol_ms` dos quadros do app, em 464 snapshots do corpus.
- Só 3 snapshots tinham bandas suficientes (≥9 bandas cheias nos 4 buffers) e janela de quadros: 60 têm 16 bandas, mas a janela de 120 s antes não tinha ≥15 amostras por banda; 363 estavam com aquisição parcial.
- **Unidades diferentes:** a razão dos buffers da ECU ficou em ≈0,93 e a razão gas_ms/petrol_ms dos quadros em ≈2,3. Os buffers da ECU parecem já normalizados; a comparação direta não vale.
- Rugosidade ECU 0,058 × app 0,061 (n=3): sem diferença. **Nada foi provado nem refutado.**
- Para refazer: primeiro descobrir a normalização dos buffers (PROTOCOLO-LANDI `17`–`20`) e usar a janela de aquisição completa, não 120 s fixos.

## Apagões × retomada após corte (corpus inteiro + sessões 18-07 e 18-38)

Definição: corte = petrol_ms < 0,3 com rpm > 900; retomada = primeiro quadro seguinte com petrol_ms ≥ 0,3; "quase-apagou" = rpm mínimo < 550 nos 3,5 s seguintes. Só retomadas com MAP < 0,45 bar e gás real (gas_ms > 0,5). Fonte: `tests/fixtures/sessoes-uteis` (≈55 sessões) + 2 sessões novas.

| rpm na retomada | retomadas GNV | quase-apagou |
|---|---|---|
| < 1550 | 240 | 24 (10,0%) |
| 1550–1700 | 136 | 8 (5,9%) |
| ≥ 1700 | 635 | 4 (0,6%) |

Nas 2 sessões novas: 5 de 10 retomadas quase apagaram, todas com rpm de retomada entre 1510 e 1633; as 5 que passaram retomaram em 1715–1865.

**Provado (n=1011):** o risco de apagar cai ~16× entre retomar abaixo de 1550 rpm e acima de 1700 rpm, em 50+ sessões.

**Não separa:** a razão gás/gasolina (≈K) no corte e na retomada é a mesma para quem apagou e quem passou (2,15 × 2,01 antes; 2,15 × 2,15 depois; petrol_ms 2,22 × 2,13). A curva toda estava em ≈1,2–1,3 nesse corpus, então **não há variação de K suficiente para provar que mais K evita o apagão** (nem que não evita). Na sessão 18-07 apagou/quase apagou com nós 0–3 em 1,30, 1,03 e 1,2.

**Consequência:** o gatilho é a rotação em que a ECU volta a injetar, não só o valor da curva. Falta: (1) saber se a ECU expõe esse limiar de retomada (o app não o altera hoje); (2) um experimento controlado de K nos nós 0–3 (ex.: 3 valores, 20 retomadas cada) para medir o efeito real, só com o dono dirigindo.

## Cruzamento rpm × tempo de injeção (corpus, ≈80 mil quadros estáveis de GNV)

Razão gás/gasolina (gas_ms/petrol_ms) relativa à referência de cada sessão (mediana em 8–12 ms), só quadros estáveis (|Δrpm| < 60), por faixa de rpm × bin de 1 ms. Mediana (n ≥ 40 por célula):

| ms | 700–1100 | 1100–1600 | 1600–2200 | 2200–2750 | 2750–3250 |
|---|---|---|---|---|---|
| 1–2 | — | 0,88 | 0,88 | 0,90 | 0,94 |
| 2–3 | 1,01 | 0,91 | 0,89 | 0,92 | 0,91 |
| 3–4 | **1,10** | 1,00 | 0,98 | 1,00 | 1,00 |
| 4–5 | **1,08** | 1,08 | 1,05 | 1,06 | 1,11 |
| 8–10 | 1,02–1,04 | 1,00 | 1,00 | 1,00 | 1,01 |
| 12–13 | 0,96 | 0,98 | 0,96 | 0,96 | 0,96 |

**Leitura (aplicada = curva K × Mapa K reais do dono, não a ideal):**
1. O formato é quase igual em todas as faixas de rpm acima de 1100: o **afundamento de 1–3 ms (≈ −10%)** é função do tempo de injeção, não da rotação → trata-se na **Curva K por nó** (o prior dos nós baixos já levanta esses nós).
2. A rotação só muda o quadro em **700–1100 rpm (lenta)**: +8–10% em 3–6 ms (lenta rica, coerente com Q2 −14%) → trata-se no **Mapa K por rpm**, não na curva.
3. Acima de 11 ms a razão cai ≈ −3% em todas as faixas (cauda): candidato a nó 19–22.
Limite: é o que foi aplicado (inclui o que o dono ajustou), não prova do ideal; a lenta tem n enorme (22 mil em 4–5 ms) e domina.

## Curva "ideal" da sessão 18-38 (último snapshot AutoMatch, 21:39:13) — motor do app sobre os buffers reais da ECU

Fonte: `autocal_native_snapshot` (1 por sessão; traz as 18 bandas de gasolina e GNV: MAP, ms, contagem; `*_PREV`; `MUL_ACT` com os 30 nós; `PETR_/GAS_MNFLD_PRESS_RV`; limiares; `NUM_AUTOMATCH_EXECUTED=3`). `AutoMatchRefinedEngine.refine` com esses buffers: modo EQUIVALENCE, 24 pontos comuns maduros, erro ponderado 9,0% → 7,9%.

| nó (ms) | atual | ideal (equivalência) | Δ |
|---|---|---|---|
| 1,5 | 1,206 | 1,160 | −3,8% |
| 2,0 | 1,212 | 1,105 | −8,8% |
| 2,5 | 1,196 | 1,066 | −10,8% |
| 3,0 | 1,178 | 1,096 | −7,0% |
| 5,0 | 1,249 | 1,311 | +5,0% |
| 5,5 | 1,237 | 1,355 | +9,6% |
| 6,0 | 1,251 | 1,315 | +5,1% |
| ≥ 11 | 1,344 | 1,344 | 0 (sem evidência) |

**Conflito a resolver:** a equivalência de regime (buffers da ECU) manda BAIXAR os nós de 2–3 ms (−7 a −11%); a prática do dono para evitar apagão é LEVANTAR os nós 0–3. Objetivos diferentes (equivalência estável × sobrevivência na retomada). Os buffers têm ~10 amostras por banda e ruído de ±15% (bandas 3 e 7 não monotônicas). Não resolver sem experimento controlado.

**Exportação (lacunas):** Mapa K NÃO está em nenhuma sessão exportada (`k_write: MAP_PENDING`, sem matriz); o snapshot do AutoMatch sai 1× por sessão (3× na 18-07), não a cada escrita da ECU; a curva K sai a cada 5 s no `full_snapshot`.
