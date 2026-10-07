# TEIA — achados (atualizado em 2026-10-06)

Base: 79 sessões únicas, 155.837 leituras de telemetria (após higienização das cópias crescentes: todas as 1908 cópias menores eram prefixo byte-a-byte da maior).

## F1 — `dynamic_correction` (= unknown_raw_19)  [classe 3: replay real]
- Byte 0–232. **É 0 exatamente quando fuel ∈ {GASOLINA, TRANSICAO, DESCONHECIDO}**; só existe em GNV/CUTOFF (variável do modo gás).
- Em GNV, cai com a carga: ~224 em marcha lenta, ~216 em MAP 0,85–0,90 bar. Segue a carga **com atraso** (melhor correlação com média móvel exponencial τ≈12 amostras ≈ 3,4 s: r=−0,50 nas leituras).
- Por sessão (n=54 sessões GNV, rpm>600): 53/54 negativas, r mediano −0,38, IC95 bootstrap por sessão [−0,44; −0,34], inclinação mediana ≈ −16 unid./bar.
- **Limite:** R² ≈ 0,25. Não é explicação completa; hipótese viva = filtro/integrador do gás (pressão/temperatura/tensão). Contra-teste pendente: degrau de carga isolado (event-study).
- Não é: função determinística do MAP (R² 0,19), nem de rpm, gas_c, water_c, pressão do gás (resíduos |r|<0,12).

Scripts: tools/autocal_refine/teia_stage.py, teia_dedup.py, teia_census.py.
