# Missão TEIA — raio-X total das sessões OMEGAS

> Cole isto no Claude Code do PC, aberto na pasta do repositório OMEGAS-V8.2, depois de `git fetch origin ccr-9f6609f2-1f4p5e && git checkout ccr-9f6609f2-1f4p5e`.
> Pasta de dados: `G:\Meu Drive\OMEGAS\SESSOES OMEGAS` (ou a que o dono indicar). Leia TUDO que estiver nela, recursivamente, incluindo .zip dentro de .zip.

## Quem você é nesta missão

Você é o cientista-chefe de dados de calibração do OMEGAS: engenheiro de injeção GNV, estatístico e detetive ao mesmo tempo. O dono é leigo e quer **inteligência nova para o app**, não um resumo. Seja ambicioso: olhe cada milissegundo, cada campo, cada evento, e **cruze tudo com tudo**. Duvide de cada hipótese (inclusive das minhas abaixo) e tente derrubá-la com os dados antes de aceitá-la.

## Regras invioláveis

1. Passivo: nunca abra porta serial/USB, nunca fale com a ECU, nunca grave nada no carro.
2. Nunca faça commit de sessões brutas (privacidade e tamanho). Pode commitar ferramentas, resultados agregados e fixtures pequenas (< 300 KB, recortes) em `fixtures/autocal/real/`.
3. Toda afirmação tem: n de leituras, n de sessões independentes, tamanho do efeito, intervalo de confiança (bootstrap **por sessão**, não por leitura) e um contra-teste que poderia tê-la derrubado.
4. Nada de "provado" sem replay. Classe de prova: 2 sintético · 3 replay real · 5 físico (só o dono no carro).
5. Branch de trabalho: `work/platina-teia-sessoes` a partir de `ccr-9f6609f2-1f4p5e`. Commits pequenos, push a cada entrega. Não abra PR.
6. Use Python com `polars` ou `duckdb` (rápido) e `numpy/scipy`. Paraleliza por sessão.

## O formato dos dados (já mapeado — não perca tempo redescobrindo)

Cada sessão: `session_<data>_<hora>_<id>/events_0001.jsonl` (no Drive às vezes `events_0001.jsonl (N).json`: cópias crescentes; use a MAIOR de cada pasta). Também há `RESUMO.md`, `session_summary.json`, `manifest.json`. Cada linha: `{type, recordedAtMs, recordedAtUtc, sequence, source, data}`.

Tipos e o que importa:
- `telemetry` (~3 Hz): `rpm`, `load_bar` (MAP), `petrol_ms`, `petrol_2_ms_diagnostic` (banco 2), `gas_ms_diagnostic`, `gas_2_ms_diagnostic`, `fuel` (GNV/GASOLINA/TRANSICAO/CUTOFF/DESLIGADO), `water_c`, `gas_c` (temperatura do gás), `gas_pressure_abs_bar`, `pressure_diff_bar`, `dynamic_correction` (= `unknown_raw_19`, significado desconhecido: DESCUBRA), `sample_state`, `cell_row/cell_column`, `k_interpolated`, `plausible`.
- `autocal_native_snapshot`: `fields[]` com `key`, `rawValues`, `physicalValues`, `status`. Bolinhas: `MNFLD_PRESS_BUF` / `_GAS` / `_GAS_PREV` (raw/1024 = bar), `PETR_INJ_TBUF` / `_GAS` / `_GAS_PREV` (raw/512 = ms), `NUM_BUF_UPD_GAS` / `_PETR` (contador por banda: quando sobe, a ECU aprendeu ali), `MUL_ACT` (30 pontos, raw/16384; **teto 24576 = 1,50**), `MNFLD_PRESS_THD` (limites das 18 bandas), `ACQUIRED_ZONES_*`. Filtros nativos de aprendizado: `DIFF_ENG_SPD_THD`=400, `DELTA_ENG_SPD_THD`=200, `DIFF_MNFLD_PRESS_THD`=0,5, `DELTA_MNFLD_PRESS_THD`=0,05, `DIFF_PETR_TINJ_T_THD`=4, `DELTA_PETR_INJ_T_THD`=1, `MAX_RPM_FOR_AUTOCAL`=3000, `MAX_AUTOMATCH`=3. Também `nativeMaturityEvents[]` (bandIndex, fuel, rpm, correlatedMapBar…).
- `autocal_native_automatch_epoch` / `_calibration_epoch`: `evidence.beforeRaw` / `afterRaw` do MUL_ACT (30 pontos).
- `refinement_phase`, `refinement_verdict` (bandas fromMs/toMs, ratioBefore/After, veredicto).
- `k_factor_batch_confirmed`, `k_batch_confirmed` (gravações de Curva K / Mapa K com readback), `full_snapshot`, `engine_stall` (APAGOU/QUASE_APAGOU, rpmBefore, rpmMin, petrolMs, mapBar, decelerating), `app_log`.
- Mapa K: 12 colunas de rpm `850,1350,1850,2500,3000,3500,4000,4500,5000,5500,6000,6500` × 12 linhas de tempo de gasolina `2,2.5,3,3.5,4.5,6,8,10,12,14,16,18 ms`.
- Logs Portmon (`PortmonLOGNOVO`, `PortmonAUTOCAL`, 150 MB): parser pronto em `scripts/omegas/portmon_parser.py`; telemetria = comando `48 01 49`, payload decodificado como em `app/src/main/java/com/omegas/prohub/ecu/Mp48Protocol.kt` (`decodeStrict`).

Ponto de partida já escrito: `tools/autocal_refine/session_garimpo.py` (agrega 62 sessões), `tools/autocal_refine/rpm_separation_sim.py` (simulador), `tools/autocal_refine/raio_x_template.html` (painel). Melhore-os; não recomece do zero.

## O que já achamos (hipóteses para CONFIRMAR OU DERRUBAR)

- H1. No mesmo MAP, a gasolina parada injeta +27 a +34% vs 2000–2600 rpm → bolinha parada fora da curva é física, não erro.
- H2. GNV pobre em 1100–1500 rpm (+4 a +21%) comparando GNV × gasolina na mesma célula giro × MAP.
- H3. 9/9 quase-apagões no GNV entre 987 e 1503 rpm — mesma faixa de H2.
- H4. 44% dos pontos do MUL_ACT terminam no teto 1,50 após AutoMatch.
- H5. 6 de 16 bandas GNV são aprendidas parado E andando (regime misturado) → vicia o multiplicador.
- H6. 229 bolinhas destoam > 6%; 42% são da gasolina → a bolinha sozinha não é régua.
- H7. Tempo de injeção GNV +6% na sessão de melhor consumo (`Sessaoutil`, 06/10) explicado por gás a 73 °C vs 56 °C (densidade).
- H8. Simulação: corrigir Mapa K na célula giro×MAP leva erro a ~2,5%; apagar bolinha não muda nada; alinhar bolinha à curva piora a lenta.

## Ângulos obrigatórios (cada um vira um nó da teia)

1. **Engenharia reversa do aprendizado nativo**: em cada subida de `NUM_BUF_UPD_*`, reconstrua os 10 s anteriores de telemetria. Que condições (estabilidade de rpm/MAP/tempo vs os limites DIFF/DELTA) a ECU exige? Qual a janela? Ela faz média? Peso? Ache a regra e escreva-a como função testável.
2. **Gênese de cada bolinha anômala**: o que aconteceu antes dela nascer? (saída de corte, transição gasolina→GNV, desaceleração, gás frio, ventoinha, marcha lenta instável, retomada). Classifique causas e frequência.
3. **Dinâmica do AutoMatch**: antes/depois por ponto, saturação no teto, dente de serra, quais bandas puxaram cada ponto, regime das amostras que alimentaram cada banda. Prever o resultado do AutoMatch a partir das bolinhas (modelo) e medir o erro.
4. **Separação curva × giro × temperatura × ruído**: modelo multiplicativo log(GNV/gasolina) = c(MAP) + r(rpm) + t(gás °C) + e; mostre a variância explicada por fator e por sessão.
5. **Densidade do gás**: corrija GNV por pressão absoluta e temperatura (lei dos gases) e refaça H2/H7. A ECU já compensa? Quanto?
6. **Bancos 1 × 2**: `petrol_2`/`gas_2` vs banco 1 — desequilíbrio de bico? Por giro/MAP?
7. **O byte `dynamic_correction`**: correlacione com mistura, sonda, transições; descubra o que é.
8. **Efeito causal de cada gravação K** (Mapa e Curva): diferença-em-diferenças nas células tocadas vs não tocadas, antes/depois, mesma sessão e sessões seguintes.
9. **Veredictos do Refino**: estavam certos? Reavalie cada um com os dados brutos.
10. **Quase-apagões**: precursores nos 3 s antes (queda de rpm, tempo de gás, MAP, corte, transição). Construa um detector que preveja com ≥ 1 s de antecedência; meça precisão/recall com sessões de fora.
11. **Consumo**: proxy de massa de gás = tempo_gás × pulsos (rpm/2 × cilindros) × densidade(P,T); por sessão, por minuto de cruzeiro equivalente. Compare tudo com `Sessaoutil` (referência de melhor consumo do dono). Separe efeito de trajeto (tempo em lenta, cruzeiro) do efeito de calibração.
12. **Deriva no tempo**: dia a dia, a curva/K/mistura deriva? Aquecimento (água < 60 °C) muda o quê?
13. **Qualidade do dado**: lacunas, jitter de tempo, leituras implausíveis, latência entre snapshot nativo e telemetria. Onde o app pode estar se enganando?
14. **Ângulos que eu não pensei**: procure ativamente padrões inesperados (clusters, correlações fortes entre campos que ninguém usa). Liste pelo menos 5.

## A TEIA (entrega principal)

Crie `analise-teia/` (fora do git, exceto o que for agregado):
- `teia.json`: nós `{id, angulo, afirmacao, numeros:{n_leituras, n_sessoes, efeito, ic95}, confianca: alta|media|baixa, sessoes, contra_teste}` e arestas `{de, para, tipo: explica|causa_provavel|contradiz|reforca|depende_de, peso}`.
- `teia.html`: grafo interativo (d3 force via `https://cdnjs.cloudflare.com/ajax/libs/d3/7.9.0/d3.min.js`), tema escuro, nó colorido por confiança, clique abre a evidência e o mini-gráfico. Sem rolagem horizontal.
- `RELATORIO.md`: no máximo 2 páginas, português simples para leigo: os 10 achados mais fortes, o que derrubou, o que ficou em aberto.
- `algoritmos.md`: cada inteligência proposta para o app (AutoCal, Refino, Mapa K, alertas): entrada, regra, travas (teto 4%/passo, foto + Desfazer, nunca na janela do AutoMatch), benefício medido em replay, e onde entra no código (`app/src/main/java/com/omegas/prohub/autocal/…`).
- Atualize `tools/autocal_refine/raio_x_template.html` com os ângulos novos e gere `analise-teia/raio-x.html`.
- Exporte 3–5 recortes pequenos e representativos como fixtures de teste (com proveniência: sessão, intervalo, SHA-256 do original).

## Como trabalhar

1. Inventário primeiro: liste todas as sessões, tamanhos, datas, duração, combustíveis, versões de app; ache duplicatas por SHA-256 e cópias crescentes.
2. Monte uma base única colunar (`analise-teia/base.parquet`) com tudo, normalizada (uma linha por leitura + tabelas de eventos).
3. Rode os 14 ângulos em paralelo (subagentes com escopo fechado se ajudar), cada um gravando seus nós na teia.
4. Cruze: para cada par de nós, há relação? Registre a aresta com o número que a sustenta.
5. Feche com o relatório e me diga em 10 linhas: os 3 achados que mais mudam o app, e o que só o carro pode provar.
