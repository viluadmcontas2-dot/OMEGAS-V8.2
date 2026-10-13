# Sessões Úteis (Drive) — quedas de rotação e comportamento do AutoMatch, 2026-10-13

Fonte: corpus compacto `tests/fixtures/sessoes-uteis/` (93 sessões com telemetria, 177 457 amostras, 110 390 em GNV ≈ 9,4 h de GNV; 2026-09-24 → 2026-10-12, várias versões do app).
Scripts (reproduzíveis): `tools/sessoes/analise_quedas.py`, `replay_stallwatch.py`, `analise_automatch.py`. Cobertura: 31 dos 33 zips do Drive; `rank074` e o zip de 28 MB não passam no conector (0 GNV nos dois).

## O que os dados mostram (OBSERVADO)
1. **Apagões reais (rpm < 300 por 800 ms, GNV):** a regra antiga registraria 18; **5 eram a chave sendo desligada na lenta** (motor nunca passou de 1000 rpm nos últimos segundos). Restam **13 apagões**; em 12 houve desaceleração forte antes (Petrol Inj. mínimo ≈ 0,2–0,5 ms = corte) e o motor morre entre 650 e 850 rpm com MAP alto.
2. **Tropeços (rpm < 500 por ≥ 300 ms e o motor volta): 26** — mais que o dobro dos apagões, e a regra antiga não via nenhum. **88% tiveram o Petrol Inj. mínimo entre 1,5 e 3,5 ms**; 10 vieram logo depois do corte; rpm antes mediana ≈ 1400 (não é marcha lenta). Taxa: ≈ 3 por hora de GNV (gasolina: 2 em 2,2 h, amostra pequena).
3. **AutoMatch (14 eventos observados, contador +1):** a ECU **zera as zonas adquiridas do GNV em 14/14** e mantém as da gasolina em 14/14 (é o "remarcar das bolinhas": comportamento da ECU, não do app). O passo de K **não é só para baixo**: mediana ≈ −1% nos nós 0–7 (0,5–4 ms), mas de −21% a +15% (negativo em 8/14); nós 8–15: −13% a +24% (8/14 negativos); nós 16–23: −5% a +23% (5/14 negativos). O primeiro AutoMatch depois de zerar a curva sobe os nós altos ~20–25% e **desce os baixos 10–20%** em vários casos — justamente a faixa onde acontecem os tropeços.
4. Entre 09-25 e 10-01 o K médio caiu de ≈1,45 para ≈1,0–1,1: é a correção grande migrando para o Mapa K, como o dono descreveu.

## Limites (INFERIDO / DESCONHECIDO)
- A razão gás/gasolina bruta mistura sessões com curvas K diferentes: serve para a forma, não para o valor absoluto da correção.
- Não prova causa (embreagem, quebra-molas): só onde (1,5–3,5 ms, MAP ≈ 0,2 bar, logo após o corte) e com que frequência.
- O app antigo marcava `MUL_ACT` como inválido (esperava 18 nós); aqui foi decodificado do `rawPayloadHex` (30 × u16 Q14).

## Sessão nova do APK de teste (Drive `OMEGAS`, 2026-10-13 16:13, versão 8.0.0-test-debug)
Apagão gravado pelo próprio app (`engine_stall`, 19:13:49 UTC): desaceleração em **corte** (injeção 0) de 2300 → 1550 rpm por ~3,4 s (MAP ≈ 0,18 bar); volta a injetar GNV com **1,8–2,3 ms** (MAP ≈ 0,2 bar) a 1489 rpm; em 1,2 s o giro cai para 749 → 484 rpm, a ECU responde com injeção 5,8 ms / MAP 0,98 e o motor apaga (`DESLIGADO`). Fixture: `tests/fixtures/sessoes-uteis/apagao-2026-10-13-1613.json`; teste `tests/test_apagao_real_2026_10_13.py`.
Achado no detector: o tempo em CORTE contava contra o GNV (lookback de 4 s) e podia esconder esse tipo de apagão; corrigido (CORTE conta como GNV). Replay do corpus inteiro: 14 apagões (13 + este tipo antes escondido) e 26 tropeços.
Após esse apagão a gravação termina (cabo USB removido às 19:15:42: "USB get_status request failed").
