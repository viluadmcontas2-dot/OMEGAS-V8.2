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
- Implicação para o app: bandas com 1,50 sustentado = ECU pedindo mais combustível que o mapa permite → candidato a alerta "mapa K/regulagem insuficiente nessa faixa".

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
