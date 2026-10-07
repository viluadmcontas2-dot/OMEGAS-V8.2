# TEIA — achados (atualizado em 2026-10-06)

Base: 79 sessões únicas, 155.837 leituras de telemetria (após higienização das cópias crescentes: todas as 1908 cópias menores eram prefixo byte-a-byte da maior).

## F1 — `dynamic_correction` (= unknown_raw_19)  [classe 3: replay real]
- Byte 0–232. **É 0 exatamente quando fuel ∈ {GASOLINA, TRANSICAO, DESCONHECIDO}**; só existe em GNV/CUTOFF (variável do modo gás).
- Em GNV, cai com a carga: ~224 em marcha lenta, ~216 em MAP 0,85–0,90 bar. Segue a carga **com atraso** (melhor correlação com média móvel exponencial τ≈12 amostras ≈ 3,4 s: r=−0,50 nas leituras).
- Por sessão (n=54 sessões GNV, rpm>600): 53/54 negativas, r mediano −0,38, IC95 bootstrap por sessão [−0,44; −0,34], inclinação mediana ≈ −16 unid./bar.
- **Limite:** R² ≈ 0,25. Não é explicação completa; hipótese viva = filtro/integrador do gás (pressão/temperatura/tensão). Contra-teste pendente: degrau de carga isolado (event-study).
- Não é: função determinística do MAP (R² 0,19), nem de rpm, gas_c, water_c, pressão do gás (resíduos |r|<0,12).

Scripts: tools/autocal_refine/teia_stage.py, teia_dedup.py, teia_census.py.

## F2 — `MUL_ACT` INVALID é artefato de build antiga, recuperável do hex  [classe 3]
- 259/415 snapshots (62%) trazem `MUL_ACT`, `PETR_INJ_TBP`, `*_MNFLD_PRESS_RV` com status INVALID ("30 elementos; esperado 18 para MODULE_VERSION 100") e `rawValues` vazio.
- 100% dos INVALID são de 24–28/set (snapshot com 22 campos); 100% dos snapshots de 29/set em diante (34 campos) são VALID. Ou seja: **não é bug vivo**, mas qualquer análise que leia só `rawValues` perde 62% do aprendizado nativo. Recuperado decodificando `rawPayloadHex` como 30×u16 LE (÷16384). Layout confirmado: 30 pontos.
- Pontos 19–29 são idênticos entre si em cada snapshot (cauda repetida): ~19 pontos efetivos, não 30. (A confirmar com mais amostras.)

## F3 — o "teto 1,50" do MUL_ACT não é teto duro  [classe 3, causa NÃO provada]
- 41% dos snapshots têm ≥1 banda exatamente em 24576 (1,50); 21% dos pontos 0–18 estão ≥1,45.
- Mas 1,4% dos pontos 0–18 passam de 1,50 (máx. observado 1,70, ponto 18). A premissa "teto = 24576" da missão está **refutada** como limite absoluto; pode ser limite só do aprendizado automático (valores acima poderiam vir de gravação manual/Curva K — não verificado).
- Saturação não cresce dentro da sessão (dentre 41 sessões com ≥3 snapshots, bandas no teto: 6 desceram, 1 subiu) — sugere reset/regravação entre sessões; n pequeno.
- (Retirado por decisão do dono: o teto é da ECU; o app não alerta.)

## Lacunas dos dados (honesto)
Não existem nestes 79 sessões: `engine_stall`, `refinement_phase/verdict`. Só 4 epochs de automatch e 4 de calibração. Stalls terão de ser inferidos da telemetria (rpm→0 sem session_stopped).

## F4 — pontos fora da curva na gasolina nativa (PETR_INJ_TBUF × MNFLD_PRESS_BUF)  [classe 3, causa NÃO provada]
- Base: último snapshot de cada uma das 71 sessões com buffer (n=898 pontos internos), 16 bandas úteis (as 2 últimas vêm 0).
- Teste local (vizinhos, sem modelo global): piso de ruído MAD ≈ 2,9%. >6% do esperado: 17,1%; >10%: 7,3%; >15%: 1,3%.
- **Assimétrico:** 12,0% abaixo do esperado vs 5,1% acima → ponto "baixo" é o modo de falha dominante (hipótese: aquisição em desaceleração/transiente; NÃO testada ainda).
- Modelo-livre: 7,4% dos pares consecutivos têm ms DECRESCENDO com a MAP subindo (74/1006).
- **Medida descartada:** ajuste de parábola global ms×MAP marcou 53% como anomalia; é erro de modelo (bandas 8–11 e 14–15 com 76–96%), não de dados. Não usar.
- Limites: interpolação linear com bandas de espaçamento irregular; mistura de builds; não separa efeito real de rpm de contaminação. Falta cruzar com telemetria no instante da atualização do contador.
- Dado útil p/ algoritmo: NUM_BUF_UPD_* satura em 10; *_PREV guarda o valor anterior da banda (permite ver o que mudou em cada atualização).

## Correção de escopo (2026-10-06, 2ª rodada)
O censo inicial leu só as pastas `session_*` e ignorou o que veio em zip. Agora entram: Sessaoutil (5 sessões de 06/10), a sessão 2026-10-02_20-57-58 reconstruída de 106 partes e os Portmon. Base atual: **85 sessões, 190.592 leituras de telemetria**, 643 snapshots nativos, 48 ações nativas (**18 DELETE_POINT**, 15 na sessão 2026-10-06_19-21-14), 10 engine_stall, refinement_*/equivalence_result.
- **Lacuna no Drive:** falta a parte 95 da sessão de 02/10 (partes 94→96). Perdidos ~0,6 MB de eventos; a junção tem uma falha de contiguidade e linhas cortadas na fronteira são ignoradas pelo parser.
- Os números F2–F4 acima foram calculados com 79 sessões; serão recalculados com 85.

## F5 — apagar ponto maduro e readquirir devolve o MESMO ponto  [classe 3 — mas 1 sessão]
Sessão 2026-10-06_19-21-14, DELETE_POINT em GNV, snapshot antes vs primeiro snapshot depois com contador ≥3:
- b7 (5 apagamentos): 4,98 → 5,03 ms (+1,0%); desvio vs vizinhos +16,8% → +16,2%. O desvio **não mudou**.
- b5: 4,09 → 4,09 (0%); 4,00 → 4,01 (+0,25%); 3,95 → 3,99 (+1%). b4: 3,83 → 3,88 (+1,3%).
- Pontos **maduros** (contador 9–10) voltaram em ≤1,3% do valor apagado; o limiar de "anomalia" do app é ≥5% → apagar não muda nada.
- Único caso que "melhorou": b8 com contador 1 (imaturo): 5,45 → 5,25 (desvio +7,7% → +0,1%). Ponto imaturo converge sozinho, não precisa apagar.
- **Leitura:** desvio reprodutível de uma banda (b7 ≈ +16% sempre) é FORMA real da curva (efeito rpm/regime na aquisição), não ruído. Detector que compara só com vizinhos vai marcar a mesma banda para sempre e o apagar-ponto repetiria o mesmo ponto.
- Limites: n=1 sessão com repetição (5+ apagamentos nas mesmas 2–3 bandas); builds antigas não registram o alvo do DELETE_POINT. Efeito físico/regime NÃO provado.
- **Implicação de projeto (decisão do dono: apagar sozinho, mas só com persistência):** critério precisa de (a) contador maduro, (b) desvio contra o formato típico da banda aprendido de TODAS as sessões (não só vizinhos), (c) desvio que NÃO se reproduz entre readquisições/sessões, (d) limite de tentativas por banda.

## Cobertura (livro-razão, tools/autocal_refine/teia_ledger.py)
992 arquivos no Drive (incl. dentro de zips): 194 events, 7 backups de Curva K (`MANUAL-*.json`), 593 meta (manifest/summary/README/RESUMO/parte), 2 Portmon, 109 zips, 87 desktop.ini ignorados. **0 sem categoria.** A 1ª varredura tinha perdido: Sessaoutil, sessão 02/10 em 106 partes, os 7 MANUAL-*.json.
Cópias da mesma sessão em pastas diferentes (21-06-38_ae619822): a menor é prefixo byte-a-byte da maior (conferido).
**Consumido até agora:** events (85 sessões). **Ainda NÃO consumido:** k_backup (7), RESUMO.md/summary (344+), Portmon (2).

## F6 — gasolina estacionária é quase linear em MAP; marcha lenta é um regime à parte  [classe 3]
- GASOLINA, estacionário (|ΔMAP|<0,015, |Δrpm|<60, >600 rpm), n=8401 leituras, 30 sessões: `ms ≈ 9,44·MAP − 0,09`, R²=0,934, resíduo sd 0,56 ms. Acrescentar rpm quase não muda (R²=0,935; +0,15 ms por 1000 rpm). Hipótese "o ponto anômalo é só rpm diferente" **não se sustenta na forma linear simples**.
- Porém, no mesmo MAP, rpm<1200 fica acima do cruzeiro: MAP 0,45: 4,15 ms vs 3,3 (>1200 rpm); MAP 0,50: 4,56 vs 3,46 (rpm>2000) → +20–30%. Efeito de regime (marcha lenta), não linear em rpm. Causa não provada.

## F7 — a razão GNV/gasolina dos buffers nativos é ≈1,00 (medida direta de equivalência)  [classe 3; semântica inferida]
- n=2032 pares (banda GNV madura, contador ≥5, gasolina interpolada no mesmo MAP), 64 sessões: razão mediana 1,01, IQR [0,97; 1,06].
- Por faixa de ms da gasolina: 1–2,5 ms: 0,94 (GNV abaixo); 2,5–3,5: 0,99; 3,5–5: 1,03; 5–7: 1,00; 7–12: 1,01.
- MUL_ACT (decodificado do hex) vai de 1,00 a 1,43 e NÃO acompanha a razão (corr −0,23). Inferência: o buffer `*_GAS` já está no domínio equivalente à gasolina; MUL_ACT é o fator de conversão, não o resíduo. **Semântica não confirmada** — validar com o código da ECU/Portmon.
- Eixo do MUL_ACT (dos backups MANUAL): 20 pontos em ms de gasolina 0,5…10,0 (passo 0,5) + 10 pontos de 11 a 22 ms (a cauda repetida de F2 é região não treinada).
- Uso: erro de equivalência por banda `e_b = razão − 1` com ruído ≈ ±3%; a faixa <2,5 ms (marcha lenta/desaceleração) é a pior e é onde ocorrem os "quase apagou".

## F8 — "coleta estável/cruzeiro → curva melhor" (hipótese do dono)  [classe 3; NÃO provada, direção a favor]
**Nível banda** (banda cujo valor mudou dentro da sessão; qualidade = |desvio vs vizinhos| no fim; estabilidade = fração de leituras perto da MAP da banda com |ΔMAP|<0,01 e |Δrpm|<50):
- GNV, n=231 bandas, 39 sessões: bruto ρ=−0,19, IC95 por sessão [−0,31; −0,06]; mediana 3,5% (instável) vs 1,6% (estável).
- **Contra-teste derrubou:** controlando banda + nº de amostras, ρ=+0,07 [−0,03; +0,17]. O efeito bruto era confusão: bandas baixas são mais "estáveis" (ρ=−0,41) e bandas com mais amostras têm menos desvio (ρ=−0,26).
- Quantidade de coleta na banda, controlando a banda: ρ=−0,09 [−0,21; +0,02] — marginal. Bruto por tercil de amostras: 3,5% → 2,4% → 1,5%.
- Gasolina: só 34 bandas/7 sessões aprendidas na sessão — inconclusivo.
**Nível sessão** (n=36 sessões GNV com curva final julgável): fração de cruzeiro (1500–3000 rpm, desvio-padrão MAP<0,01 e rpm<40 em 3 amostras) vs erro de equivalência mediano final (|razão GNV/gasolina − 1|, bandas com contador ≥5):
- ρ=−0,32, p=0,054. Erro por tercil de cruzeiro: 4,7% → 3,9% → 3,6%. Duração da sessão: ρ=−0,18 (mais fraco que cruzeiro).
- **Cruzeiro é raro nos dados:** fração mediana 4,5%. A maioria das sessões é cidade/parado. Falta poder estatístico, não necessariamente efeito.
- Limite: a curva final carrega aprendizado de sessões anteriores (buffers persistem entre sessões).
**O que provaria:** sessões com autocal dedicado em pista/cruzeiro (o dono descreve isso); hoje não há o bastante delas para separar.

## ERRATA (2026-10-06, 3ª rodada) — F7 e F8 recalculados com as 85 sessões
O `snaps.pkl` usado em F7/F8 tinha sido gerado ANTES de entrarem as sessões dos zips (inclusive as de pista de 06/10). Refeito com `teia_snaps.py` (691 registros, 78 sessões com buffer).
- **F7 (refeito):** n=2876 pares, 68 sessões: razão GNV/gasolina mediana 1,013, IQR [0,975; 1,062]. Faixa 1–2,5 ms: **0,894** (pior que antes: 0,94); 3,5–5 ms: 1,042; demais ≈1,00. Conclusão mantida.
- **F8 nível sessão (refeito):** n=39: ρ(cruzeiro, erro)=−0,14, p=0,39 → **sem efeito detectável** (antes −0,32, p=0,054). Pista (2 sessões julgáveis de 06/10) terminou com erro MAIOR (7,9% vs 4,2%), mas o "erro no fim" é medida ruim para sessão com refino em andamento — ver F9.

## F9 — o ciclo autocal→automatch→refino visto por dentro (sessão de pista 2026-10-06 20:30, 53 min)  [classe 3; n=1 sessão]
Erro de equivalência = mediana |GNV/gasolina − 1| nas bandas GNV com contador ≥5 (série completa: teia_h3_ciclos.py):
- 20:31 Mapa K gravado → GNV reaprende de 8 para 13 bandas e o erro cai **9,6% → 2,5%** em 9 min. Melhor estado: **~1,5% com 6–7 bandas** após a 2ª Curva K (20:52–20:55).
- **Cada automatch da ECU zera os buffers de GNV** (bandas maduras → 0) e o GNV reaprende do zero; foram 6 automatches em 30 min (20:43, 20:46, 20:49, 21:02, 21:08, 21:13). Logo após cada um, o erro se apoia em 1–3 bandas e oscila.
- **Último automatch (21:13:51) piorou:** erro +16% → +6,5% (viés positivo, GNV acima da gasolina) e 4 dos 7 "quase apagou" da sessão vieram nos 10 min seguintes (21:15, 21:17, 21:21, 21:23; MAP 0,20–0,38). O app registrou "um trecho piorou, restaure só esse trecho" às 21:14:19.
- Esse automatch deixou **11/20 pontos do MUL_ACT no teto 1,50** — o maior número dos 14 automatches registrados (os outros: 0–9). (Descrição apenas; o teto é decisão da ECU — sem recomendação para o app.)

## F10 — o que cada automatch nativo faz no MUL_ACT (14 épocas, 6 dias)  [classe 3, dado cru]
- Muda 12–20 dos 20 pontos úteis por época; mudança média −10,6% … +2,2%.
- Maior mudança por ponto **sempre em valores redondos: 4,9–7,5%, 12,0%, 12,9–13,6%, 17,1%, 25,0%**; em 3 épocas o máximo é exatamente 6,0%, 12,0% ou 25,0% (vários pontos iguais) → sugere passo máximo/quantização da ECU por época. Útil para prever o próximo automatch. Mecanismo não confirmado.

## Decisão do dono (2026-10-06): o teto 1,50 do MUL_ACT é da ECU
O app NÃO alerta nem age sobre pontos em 1,50. As observações de teto em F3/F9/F10 ficam só como descrição do dado, sem recomendação.

## F11 — aquisição em marcha lenta infla o ponto do GNV em +11% a +26%  [classe 3; forte]
Método cru (sem rótulos de correlação do app): para cada incremento de NUM_BUF_UPD entre dois snapshots consecutivos (≤2 min, sem reset no meio), telemetria daquela janela no combustível certo e |MAP − MAP da banda| < 0,03. "Lenta" = ≥80% dessas leituras com rpm<1000; "andando" = ≤20%.
- 920 incrementos, 860 com telemetria, 43 sessões.
- GNV, bandas 2–9 (MAP 0,33–0,67): **16–43% das aquisições acontecem em marcha lenta.** Bandas 0–1 e 10–15: ~0%.
- ms aprendido lenta/andando (IC95 bootstrap por sessão): b2 1,11 [0,99; 1,17] · b3 1,12 [0,96; 1,25] · b4 1,20 [1,00; 1,35] · b5 1,17 [1,06; 1,45] · b6 1,17 [1,03; 1,32] · **b7 1,26 [1,10; 1,40]** · b8 1,18 [1,07; 1,30] · b9 1,21 [1,10; 1,54].
- **Direção sempre para CIMA** no GNV (a lenta nunca puxou para baixo nestes dados).
- Coerente com F6: na GASOLINA estacionária, mesma MAP em rpm<1200 também dá +20–30% de ms → é física real do regime, não "erro". O problema não é a lenta: é **misturar regimes** (banda da gasolina aprendida andando × banda do GNV aprendida na lenta → falsa diferença de equivalência).
- **Gasolina quase não aprende nestes dados:** só 3 incrementos de NUM_BUF_UPD_PETR em 43 sessões. A base (gasolina) está praticamente congelada; o regime em que foi aprendida é desconhecido aqui.
- Explica F5: b7 (o ponto mais apagado à mão, +16% vs vizinhos) é a banda com maior inflação de lenta (1,26). Apagar parado = readquirir na lenta = mesmo ponto.

## F12 — confirmação externa da semântica do buffer GNV (fonte do fabricante)  [fonte forte]
Release notes da AEB sobre a autocalibração MP32/MP48 (https://intergasservice.ru/upload/iblock/e42/e42b906b0b85ee2965685789d6787eea.pdf):
- O método do fabricante compara o **tempo de injeção da gasolina (Ti) calculado pela ECU original em modo gás** com o Ti em modo gasolina, no mesmo MAP. Com calibração certa, o Ti não muda ao trocar de combustível.
- Logo `PETR_INJ_TBUF_GAS` é o Ti da gasolina medido rodando em GNV, e a razão GNV/gasolina dos buffers (F7, mediana 1,013) **é** a medida de equivalência da própria ECU. Deixa de ser inferência.
- AEB: subzona válida com ≥7 amostras; linha traçada com 3 de 4 faixas completas; K limitado a [0,5; 1,5]; cada automatch muda K no máximo ±20% sobre o anterior; dados acima de 3000 rpm descartados. Compare F10 (passos observados de 6%, 12%, 25%).

## F13 — Portmon (2 logs do ProgBase, 36.463 + ~39.500 transações)
Parser `scripts/omegas/portmon_parser.py`. Os logs são do ProgBase (fabricante) lendo a ECU: telemetria `48 01 49` (21.167 no AUTOCAL), leituras `29 xx 01` dos campos AutoCal e um único reset manual `02 24 04 04 2E` (RESET_ALL). **Não há nenhuma escrita das máscaras de apagar ponto (0x016D/0x016E) nem do commit `01 24 05`.** A ordem real desses bytes NÃO pode ser confirmada por estes logs; fica coberta por teste de contrato no app e pela prova física do dono.

## F14 — temperatura da água muda a equivalência medida; escalas de injeção batem  [classe 3]
Telemetria GNV estacionária (|ΔMAP|<0,015, |Δrpm|<60, rpm≥1200, petrol ≥3 ms), n=16.301 leituras, 56 sessões. Script: tools/autocal_refine/teia_temperatura.py.
- **Escalas OK:** ms da telemetria (gasolina, estacionário) ÷ ms do buffer nativo (÷512) na mesma banda e MAP = 0,993 (IQR 0,961–1,008, n=27 pares banda×sessão). Não existe o viés de 31% que as constantes (0,00256 ms/contagem vs 1/512) sugeriam.
- **Razão instantânea gas_ms_diagnostic ÷ petrol_ms** (mediana 2,22): sobe com a água de 1,96 (35–40 °C) a 2,39 (75–80 °C), +22%, e estabiliza a partir de 65–70 °C. Cai com o gás de 2,38 (30–50 °C) a 2,05 (90–100 °C), −14%. Spearman: água +0,49, gás −0,47, pressão abs. −0,17, dynamic_correction +0,11, MAP −0,11. R² só MAP = 0,01; com dc+gas_c+pressão+MAP = 0,26.
- **Confusão:** água e gás aquecem juntos na sessão; não separa qual dos dois causa. Hipótese física (não provada): a ECU original enriquece a gasolina a frio e o GNV não acompanha.
- **87% do tempo registrado é com água <70 °C** (44% <60 °C, 14% <50 °C): um portão fixo de aquecimento apagaria quase tudo. A conduta é PAREAR GNV×gasolina na mesma faixa de água (como já se faz com rpm), não filtrar.
- `dynamic_correction` não ajuda a normalizar a razão (ρ=+0,11); só serve para validar combustível (0 em gasolina).
