# Análise do corpus OMEGAS

Leitura local; nenhum arquivo do repositório alterado. 99 sessões: 93 antigas e 6 novas; 202,441 quadros. O catálogo tem 93, não 85 sessões antigas; o LEIA-ME também cita 93. 5 arquivos JSONL repetidos nos ZIPs descartados por SHA-256. Todos os segmentos encontrados recursivamente foram lidos. Não se duplicou telemetria dos full_snapshot.

Tempo observado por combustível (intervalos >0 e ≤3,6 s): GNV: 11.433 h, DESCONHECIDO: 0.855 h, DESLIGADO: 1.481 h, TRANSICAO: 0.771 h, CUTOFF: 0.763 h, GASOLINA: 2.484 h. Lacunas maiores não entram. IDs curtos e caminhos completos: `sessoes.csv`; hashes: `entradas.json`.

Reprodução: `python3 -I codex/analisar_corpus.py`. Só biblioteca padrão. CSVs preservam resultados completos; tabelas abaixo são resumos. Fontes de método: `tools/sessoes/corpus.py`, `analise_automatch.py`, `analise_quedas.py`, `analise_equivalencia_celulas.py`; unidades: `AutoCalProtocol.kt`/`AutoCalScale`.

## 1. Curva da gasolina por AutoCal

518 snapshots nativos em 87 sessões; 138 estados distintos de referência em 87 sessões. Buffer de injeção = u16 LE/512 ms; MAP = s16 LE/1024 bar. Não usei physicalValues antigo (/500 e /1000). Pares positivos e NUM_BUF_UPD_PETR>0, quando presente. Não usei buffers *_GAS como gasolina.

**Aquisição não é sinônimo de snapshot.** Sem ID persistente comum a todas as versões, reporto todas as referências observadas: par de buffers distinto por sessão e época observada (contador diminuiu). Repetição idêntica na mesma época conta uma vez. Referência herdada de sessão anterior continua sendo referência observada, não prova de coleta nova. A tabela completa por estado/faixa está em `referencias_por_aquisicao_observada.csv`.

Cada estado contribui uma mediana por faixa [MAP, MAP+0,1). p10/p90 são percentis dessas medianas, não intervalos de confiança. A última coluna dá dispersão entre medianas de sessões, para não dar peso maior a sessões com mais polling.


| MAP bar | Estados | Sessões | Mediana ms | p10 | p90 | p10–p90 entre sessões |
| --- | --- | --- | --- | --- | --- | --- |
| 0.1–0.2 | 130 | 86 | 1.942 | 1.777 | 2.232 | 1.774–2.231 |
| 0.2–0.3 | 135 | 85 | 2.658 | 2.379 | 2.807 | 2.438–2.807 |
| 0.3–0.4 | 137 | 87 | 3.256 | 3.129 | 3.411 | 3.153–3.411 |
| 0.4–0.5 | 137 | 87 | 4.001 | 3.834 | 4.222 | 3.872–4.221 |
| 0.5–0.6 | 137 | 87 | 4.611 | 4.185 | 4.913 | 4.248–4.890 |
| 0.6–0.7 | 138 | 87 | 5.036 | 4.459 | 5.731 | 4.459–5.563 |
| 0.7–0.8 | 138 | 87 | 6.181 | 5.864 | 6.494 | 5.900–6.453 |
| 0.8–0.9 | 131 | 81 | 8.086 | 7.773 | 8.527 | 7.847–8.527 |
| 0.9–1.0 | 138 | 87 | 9.612 | 9.172 | 10.069 | 9.172–10.069 |


As 138 referências observadas contêm 110 conjuntos distintos de pares válidos no corpus inteiro; repetições entre sessões podem ser herança da ECU, não replicações independentes.

Não há evidência de uma curva canônica estável a ±4%: por exemplo, em 0,6–0,7 bar o p10–p90 entre sessões é 4,459–5,563 ms (amplitude de 24,8% sobre o p10). Uma curva mediana descritiva é possível. A mistura de RPM, temperatura, referências herdadas e épocas impede interpretar esta tabela como calibração universal. Não há identificação inequívoca de TODAS as aquisições físicas.

## 2. Curva K por AutoMatch

17 incrementos exatamente +1 observados em 15 sessões. Saltos >1: 0; reduções do contador: 15. Não inventei os AutoMatches intermediários. 0 intervalos têm escrita do app; 10 snapshots pós-evento não declaram coerência temporal. São curvas observadas depois do incremento, não necessariamente imediatamente depois da escrita da ECU.

`automatches_por_no.csv` contém cada AutoMatch × 30 nós, contador, instante, intervalo de observação e possível intervenção. Decodificação de MUL_ACT aceita 60 bytes mesmo quando o app antigo marcava INVALID por esperar 18 nós.


| Zona (índices) | Mediana K¹ | p10 | p90 |
| --- | --- | --- | --- |
| 0–7 | 1.096 | 0.925 | 1.178 |
| 8–15 | 1.395 | 1.080 | 1.500 |
| 16–23 | 1.344 | 1.200 | 1.460 |
| 24–29 | 1.344 | 1.200 | 1.449 |


¹ Mediana entre AutoMatches das medianas dos nós de cada zona; cada incremento tem o mesmo peso.

Diferença = 100 × (mediana K[0:4] / mediana K[8:23] − 1). Mediana **-22.4%**; nós baixos inferiores em **17/17** incrementos. Índices 0–3 são 0,5–2 ms; “0–3 ms” incluiria também índices 4–5. Índices 8–22 são 4,5–13 ms neste eixo, não 8–22 ms.


| AutoMatch | Sessão | Contador | K 0–3 | K 8–22 | Δ % | Janela s |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | A02 | 2 | 1.067 | 1.439 | -25.8 | 81.2 |
| 2 | A04 | 2 | 0.944 | 1.500 | -37.1 | 217.0 |
| 3 | A08 | 2 | 0.897 | 1.440 | -37.8 | 45.4 |
| 4 | A03 | 1 | 0.898 | 1.187 | -24.4 | 21.3 |
| 5 | A09 | 3 | 1.128 | 1.388 | -18.8 | 27.8 |
| 6 | A14 | 2 | 1.155 | 1.500 | -23.0 | 36.6 |
| 7 | A23 | 2 | 1.086 | 1.400 | -22.4 | 49.4 |
| 8 | A24 | 1 | 0.979 | 1.003 | -2.4 | 39.2 |
| 9 | A31 | 1 | 0.937 | 1.238 | -24.4 | 44.6 |
| 10 | A40 | 1 | 0.800 | 0.971 | -17.6 | 109.7 |
| 11 | A54 | 1 | 1.098 | 1.500 | -26.8 | 14.6 |
| 12 | A60 | 1 | 1.096 | 1.250 | -12.3 | 30.2 |
| 13 | A53 | 1 | 1.055 | 1.500 | -29.6 | 30.4 |
| 14 | A61 | 3 | 1.176 | 1.316 | -10.6 | 73.2 |
| 15 | N06 | 1 | 1.100 | 1.250 | -12.0 | 30.8 |
| 16 | N06 | 2 | 1.028 | 1.311 | -21.5 | 17.2 |
| 17 | N06 | 3 | 1.129 | 1.305 | -13.5 | 29.5 |



| Nó | Eixo ms (mediana) | K mediano | p10 | p90 |
| --- | --- | --- | --- | --- |
| 0 | 0.5 | 1.067 | 0.897 | 1.153 |
| 1 | 1.0 | 1.067 | 0.897 | 1.139 |
| 2 | 1.5 | 1.067 | 0.897 | 1.139 |
| 3 | 2.0 | 1.067 | 0.897 | 1.149 |
| 4 | 2.5 | 1.096 | 0.932 | 1.212 |
| 5 | 3.0 | 1.205 | 0.966 | 1.261 |
| 6 | 3.5 | 1.244 | 0.952 | 1.372 |
| 7 | 4.0 | 1.298 | 0.913 | 1.500 |
| 8 | 4.5 | 1.400 | 0.974 | 1.500 |
| 9 | 5.0 | 1.307 | 1.101 | 1.495 |
| 10 | 5.5 | 1.283 | 1.067 | 1.500 |
| 11 | 6.0 | 1.400 | 1.148 | 1.500 |
| 12 | 6.5 | 1.371 | 1.094 | 1.500 |
| 13 | 7.0 | 1.339 | 1.032 | 1.500 |
| 14 | 7.5 | 1.325 | 1.070 | 1.500 |
| 15 | 8.0 | 1.295 | 1.012 | 1.500 |
| 16 | 8.5 | 1.293 | 1.041 | 1.498 |
| 17 | 9.0 | 1.328 | 1.071 | 1.500 |
| 18 | 9.5 | 1.344 | 1.184 | 1.500 |
| 19 | 10.0 | 1.344 | 1.200 | 1.481 |
| 20 | 11.0 | 1.344 | 1.200 | 1.449 |
| 21 | 12.0 | 1.344 | 1.200 | 1.449 |
| 22 | 13.0 | 1.344 | 1.200 | 1.449 |
| 23 | 14.0 | 1.344 | 1.200 | 1.449 |
| 24 | 15.0 | 1.344 | 1.200 | 1.449 |
| 25 | 16.0 | 1.344 | 1.200 | 1.449 |
| 26 | 17.0 | 1.344 | 1.200 | 1.449 |
| 27 | 18.0 | 1.344 | 1.200 | 1.449 |
| 28 | 20.0 | 1.344 | 1.200 | 1.449 |
| 29 | 22.0 | 1.344 | 1.200 | 1.449 |


## 3. Hipótese dos nós baixos e apagões

Curva vigente estimada por última leitura disponível, atualizada por snapshot nativo, curva completa confirmada pelo app e mudanças de cache k_factor em full_snapshot (updatedAt mudou). Sem retroagir o primeiro snapshot e sem transportar curva entre sessões. A curva pode mudar pela ECU entre leituras: a continuidade é uma hipótese, não observação contínua. Diferença por sessão = mediana ponderada pelo tempo de GNV coberto.

Afundamento: rpm <600, com ≥800 nos 2,5 s anteriores, combustível continuamente GNV na janela. Inclui rpm=0. Episódios separados por até 5 s são agrupados; engine_stall entra somente com telemetria GNV até 2,5 s antes. União elimina sobreposição a 5 s. Não distingue parada voluntária. Denominador GNV exige ambos os extremos GNV e intervalo ≤3,6 s.

Tabela só de sessões com GNV; CSV inclui todas. Taxa comparável entre versões = quedas de RPM; união inclui também eventos do detector nativo, ausentes do corpus antigo. A correlação principal usa apenas eventos e tempo com curva conhecida.


| ID | Sessão nova |
| --- | --- |
| N01 | session_2026-10-15_10-07-28_1206d545 |
| N02 | session_2026-10-15_10-22-05_1206d545 |
| N03 | session_2026-10-16_13-04-06_1206d545 |
| N04 | session_2026-10-16_13-21-51_1206d545 |
| N05 | session_2026-10-16_13-37-45_1206d545 |
| N06 | session_2026-10-16_13-42-32_1206d545 |



| Sessão | GNV h | Cobertura K % | ΔK % | RPM / nativo / união | União/h GNV | União/h com K |
| --- | --- | --- | --- | --- | --- | --- |
| N01 | 0.094 | 0.0 | — | 1/0/1 | 10.6 | — |
| N02 | 0.241 | 0.0 | — | 6/0/6 | 24.9 | — |
| N03 | 0.096 | 57.2 | 0.0 | 1/1/1 | 10.4 | 0.0 |
| N04 | 0.194 | 100.0 | 0.0 | 5/1/5 | 25.7 | 25.7 |
| N05 | 0.033 | 0.0 | — | 2/0/2 | 59.9 | — |
| N06 | 1.392 | 90.2 | -13.5 | 14/6/15 | 10.8 | 9.6 |


<details><summary>Tabela completa das sessões antigas com GNV</summary>


| Sessão | GNV h | Cobertura K % | ΔK % | RPM / nativo / união | União/h GNV | União/h com K |
| --- | --- | --- | --- | --- | --- | --- |
| A02 | 0.239 | 98.9 | -10.8 | 2/0/2 | 8.4 | 8.4 |
| A04 | 0.200 | 100.0 | -37.1 | 1/0/1 | 5.0 | 5.0 |
| A06 | 0.209 | 100.0 | -38.9 | 2/0/2 | 9.6 | 9.6 |
| A08 | 0.181 | 98.7 | -39.5 | 0/0/0 | 0.0 | 0.0 |
| A05 | 0.162 | 100.0 | -17.3 | 5/0/5 | 30.9 | 30.9 |
| A01 | 0.008 | 65.5 | -5.0 | 0/0/0 | 0.0 | 0.0 |
| A03 | 0.197 | 100.0 | -24.2 | 6/0/6 | 30.4 | 30.4 |
| A07 | 0.222 | 100.0 | -22.6 | 2/0/2 | 9.0 | 9.0 |
| A09 | 0.153 | 100.0 | -24.2 | 0/0/0 | 0.0 | 0.0 |
| A10 | 0.188 | 100.0 | -24.4 | 1/0/1 | 5.3 | 5.3 |
| A12 | 0.220 | 100.0 | -18.8 | 2/0/2 | 9.1 | 9.1 |
| A11 | 0.165 | 98.5 | -38.8 | 0/0/0 | 0.0 | 0.0 |
| A13 | 0.173 | 100.0 | -40.5 | 1/0/1 | 5.8 | 5.8 |
| A14 | 0.149 | 100.0 | -23.0 | 9/0/9 | 60.3 | 60.3 |
| A15 | 0.245 | 100.0 | -33.3 | 2/0/2 | 8.2 | 8.2 |
| A16 | 0.193 | 99.2 | 0.0 | 4/0/4 | 20.7 | 20.9 |
| A21 | 0.230 | 98.9 | -18.8 | 3/0/3 | 13.0 | 8.8 |
| A17 | 0.168 | 99.1 | -30.8 | 0/0/0 | 0.0 | 0.0 |
| A19 | 0.181 | 98.8 | -33.5 | 1/0/1 | 5.5 | 5.6 |
| A23 | 0.176 | 100.0 | -12.3 | 0/0/0 | 0.0 | 0.0 |
| A20 | 0.214 | 98.8 | -40.5 | 0/0/0 | 0.0 | 0.0 |
| A22 | 0.170 | 100.0 | -16.5 | 0/0/0 | 0.0 | 0.0 |
| A18 | 0.197 | 100.0 | -24.2 | 3/0/3 | 15.2 | 15.2 |
| A26 | 0.167 | 98.5 | -38.9 | 2/0/2 | 11.9 | 12.1 |
| A28 | 0.203 | 98.8 | 0.0 | 0/0/0 | 0.0 | 0.0 |
| A29 | 0.196 | 100.0 | -33.3 | 0/0/0 | 0.0 | 0.0 |
| A25 | 0.207 | 100.0 | -18.2 | 8/0/8 | 38.6 | 38.6 |
| A24 | 0.168 | 99.1 | 0.0 | 3/0/3 | 17.9 | 18.0 |
| A27 | 0.159 | 100.0 | 0.0 | 0/0/0 | 0.0 | 0.0 |
| A30 | 0.195 | 98.7 | -18.9 | 13/0/13 | 66.7 | 67.6 |
| A31 | 0.157 | 98.3 | -24.4 | 0/0/0 | 0.0 | 0.0 |
| A33 | 0.171 | 100.0 | -21.5 | 0/0/0 | 0.0 | 0.0 |
| A32 | 0.177 | 100.0 | -21.7 | 5/0/5 | 28.2 | 28.2 |
| A34 | 0.209 | 100.0 | -21.7 | 1/0/1 | 4.8 | 4.8 |
| A35 | 0.187 | 98.5 | -2.3 | 8/0/8 | 42.7 | 43.3 |
| A36 | 0.208 | 100.0 | -2.3 | 0/0/0 | 0.0 | 0.0 |
| A43 | 0.131 | 100.0 | -21.5 | 2/0/2 | 15.3 | 15.3 |
| A37 | 0.133 | 91.3 | 0.0 | 4/0/4 | 30.1 | 24.7 |
| A39 | 0.195 | 100.0 | 0.0 | 3/0/3 | 15.4 | 15.4 |
| A41 | 0.234 | 100.0 | -21.7 | 1/0/1 | 4.3 | 4.3 |
| A40 | 0.127 | 98.0 | 0.0 | 6/0/6 | 47.3 | 48.3 |
| A38 | 0.209 | 99.0 | -2.3 | 9/0/9 | 43.0 | 43.4 |
| A42 | 0.148 | 98.6 | -2.3 | 1/0/1 | 6.8 | 6.9 |
| A44 | 0.149 | 98.7 | -18.9 | 1/0/1 | 6.7 | 6.8 |
| A47 | 0.130 | 100.0 | -18.9 | 4/0/4 | 30.8 | 30.8 |
| A45 | 0.110 | 97.5 | -21.5 | 3/0/3 | 27.3 | 28.0 |
| A48 | 0.119 | 80.8 | 0.0 | 2/0/2 | 16.8 | 10.4 |
| A46 | 0.069 | 100.0 | 0.0 | 2/0/2 | 29.0 | 29.0 |
| A49 | 0.064 | 100.0 | -16.3 | 1/0/1 | 15.6 | 15.6 |
| A51 | 0.074 | 100.0 | 0.0 | 2/0/2 | 27.0 | 27.0 |
| A50 | 0.092 | 100.0 | -2.3 | 2/0/2 | 21.6 | 21.6 |
| A56 | 0.056 | 94.9 | -18.9 | 2/0/2 | 35.7 | 37.7 |
| A53 | 0.077 | 100.0 | -29.6 | 3/0/3 | 39.0 | 39.0 |
| A55 | 0.072 | 81.1 | 0.0 | 0/0/0 | 0.0 | 0.0 |
| A58 | 0.036 | 94.7 | -18.2 | 3/0/3 | 83.0 | 58.4 |
| A57 | 0.024 | 100.0 | -7.6 | 1/0/1 | 41.6 | 41.6 |
| A52 | 0.073 | 98.5 | -11.2 | 3/0/3 | 40.9 | 41.5 |
| A59 | 0.034 | 100.0 | -8.9 | 0/0/0 | 0.0 | 0.0 |
| A70 | 0.033 | 93.0 | -33.5 | 0/0/0 | 0.0 | 0.0 |
| A62 | 0.055 | 93.3 | 0.0 | 0/0/0 | 0.0 | 0.0 |
| A61 | 0.046 | 100.0 | -10.6 | 0/0/0 | 0.0 | 0.0 |
| A69 | 0.012 | 78.2 | 0.0 | 0/0/0 | 0.0 | 0.0 |
| A73 | 0.028 | 94.4 | 1.4 | 0/0/0 | 0.0 | 0.0 |
| A75 | 0.009 | 100.0 | -5.9 | 0/0/0 | 0.0 | 0.0 |
| A77 | 0.000 | 100.0 | -32.3 | 0/0/0 | 0.0 | 0.0 |
| A78 | 0.016 | 35.1 | 0.0 | 0/0/0 | 0.0 | 0.0 |
| A79 | 0.012 | 100.0 | -18.9 | 2/0/2 | 162.4 | 162.4 |
| A80 | 0.016 | 100.0 | -2.3 | 1/0/1 | 62.7 | 62.7 |
| A82 | 0.002 | 100.0 | 0.0 | 3/0/3 | 1272.1 | 1272.1 |


</details>

Com ≥5 min de exposição com curva: n=49, **Pearson r=0.292, Spearman ρ=0.344** (ΔK × união/h com K). Exigindo também cobertura ≥80%: n=49, r=0.292, ρ=0.344. Correlações entre sessões, sem ajuste por trajeto, mapa, temperatura ou motorista; não demonstram causalidade.

Sensibilidade: leitura da curva com idade ≤60 s e ≥5 min cobertos: n=21, r=0.248, ρ=0.485; diferença e taxa recalculados só nessa exposição. O sinal positivo da correlação principal não sustenta a previsão simples de mais eventos quando ΔK é mais negativo. Isso também não refuta benefício em uma situação específica.

A hipótese “subir os nós baixos elimina apagões” exige comparação antes/depois sob condições semelhantes, curva e mapa sincronizados e exposição suficiente. Estes registros não isolam essa intervenção. Não se pode atribuir os episódios só à Curva K nem inferir mistura rica/pobre apenas pelo tempo de pulso. O CSV traz sensibilidade de cobertura quando a leitura tem no máximo 60 s.

## 4. Razões por RPM e equivalência GNV/gasolina

Faixas RPM herdadas do script existente: 850=[700,1100), 1350=[1100,1600), 1850=[1600,2200), 2500=[2200,2750), 3000=[2750,3250), 3500=[3250,4000). Estabilidade: quadro e vizinhos no mesmo combustível, pulsos gasolina positivos, intervalo adjacente ≤1,5 s, |Δrpm|/Δt ≤300 rpm/s em ambos os lados, amplitude MAP ≤0,03 bar nos três quadros. Não usa filtro térmico; temperaturas/adaptação continuam confundidores. Quadros estáveis: GNV=68,239; gasolina=14,611.

Razão gas_ms_diagnostic/petrol_ms só com pulso de gás presente e positivo. Faixa de petrol [inteiro, inteiro+1) ms. Cada célula mostra mediana (n); mínimo 15 quadros. Todos os valores, inclusive células pequenas e p10/p90, estão em `razao_gas_petrol.csv`. Não são fatores K nem medição de massa de combustível.


| Petrol ms | 850 | 1350 | 1850 | 2500 | 3000 | 3500 |
| --- | --- | --- | --- | --- | --- | --- |
| 1–2 | 2.037 (32) | 1.948 (191) | 2.154 (275) | 2.124 (30) | — | — |
| 2–3 | 2.659 (577) | 2.106 (283) | 2.121 (257) | 2.251 (61) | — | — |
| 3–4 | 2.782 (22594) | 2.496 (177) | 2.329 (337) | 2.219 (269) | 2.144 (34) | — |
| 4–5 | 2.426 (24869) | 2.544 (198) | 2.402 (476) | 2.314 (101) | 2.490 (18) | — |
| 5–6 | 1.800 (5895) | 2.433 (193) | 2.383 (410) | 2.376 (179) | — | — |
| 6–7 | 2.137 (497) | 2.409 (135) | 2.356 (570) | 2.272 (203) | 2.330 (17) | — |
| 7–8 | 2.350 (189) | 2.367 (127) | 2.276 (533) | 2.175 (238) | 2.379 (39) | — |
| 8–9 | 2.421 (114) | 2.252 (219) | 2.209 (948) | 2.099 (945) | 2.304 (79) | 2.416 (19) |
| 9–10 | 2.249 (28) | 2.322 (441) | 2.220 (1416) | 2.079 (1428) | 2.310 (162) | 2.385 (21) |
| 10–11 | 2.091 (22) | 2.260 (210) | 2.113 (656) | 2.085 (459) | 2.269 (193) | 2.414 (43) |
| 11–12 | — | 1.936 (72) | 1.907 (255) | 1.886 (118) | 2.019 (66) | 2.272 (35) |
| 12–13 | — | 1.859 (26) | 1.695 (53) | 1.844 (21) | 1.982 (31) | — |


Equivalência = mediana petrol_ms(GNV) / mediana petrol_ms(gasolina), mesma célula RPM × MAP de 0,1 bar; ≥15 quadros de cada combustível. Agregado usa todo o corpus. Pareado calcula primeiro a razão dentro de cada sessão. “Consistente” exige ≥3 sessões pareadas e ≥80% delas fora de ±4% no mesmo sentido. Critério descritivo, não teste estatístico; quadros vizinhos são dependentes.


| RPM | MAP | n G/P | Razão agregada | Sessões pareadas | Mediana pareada | p10–p90 pareado | Consistente |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 850 | 0.2–0.3 | 128/29 | 1.059 | 0 | — | —–— | não |
| 850 | 0.3–0.4 | 1965/4805 | 0.947 | 6 | 1.004 | 0.834–1.099 | não |
| 850 | 0.4–0.5 | 43777/5781 | 1.085 | 12 | 1.081 | 0.984–1.153 | não |
| 850 | 0.5–0.6 | 7652/270 | 1.110 | 0 | — | —–— | não |
| 850 | 0.6–0.7 | 956/26 | 1.000 | 0 | — | —–— | não |
| 850 | 0.8–0.9 | 204/16 | 1.003 | 0 | — | —–— | não |
| 1350 | 0.1–0.2 | 142/381 | 0.950 | 0 | — | —–— | não |
| 1350 | 0.2–0.3 | 322/77 | 0.968 | 0 | — | —–— | não |
| 1350 | 0.3–0.4 | 81/41 | 1.083 | 0 | — | —–— | não |
| 1350 | 0.4–0.5 | 167/27 | 1.005 | 0 | — | —–— | não |
| 1350 | 0.5–0.6 | 100/39 | 1.063 | 0 | — | —–— | não |
| 1350 | 0.6–0.7 | 243/41 | 0.958 | 0 | — | —–— | não |
| 1350 | 0.7–0.8 | 204/20 | 1.082 | 0 | — | —–— | não |
| 1350 | 0.8–0.9 | 255/33 | 0.996 | 0 | — | —–— | não |
| 1350 | 0.9–1.0 | 777/24 | 1.011 | 0 | — | —–— | não |
| 1850 | 0.1–0.2 | 165/950 | 0.955 | 2 | 0.972 | 0.912–1.032 | não |
| 1850 | 0.2–0.3 | 276/81 | 1.006 | 1 | 0.941 | 0.941–0.941 | não |
| 1850 | 0.4–0.5 | 132/37 | 1.026 | 0 | — | —–— | não |
| 1850 | 0.5–0.6 | 210/36 | 1.000 | 0 | — | —–— | não |
| 1850 | 0.6–0.7 | 512/93 | 0.980 | 1 | 0.915 | 0.915–0.915 | não |
| 1850 | 0.7–0.8 | 1019/225 | 1.038 | 2 | 0.931 | 0.915–0.947 | não |
| 1850 | 0.8–0.9 | 1564/305 | 1.001 | 1 | 0.890 | 0.890–0.890 | não |
| 1850 | 0.9–1.0 | 2236/260 | 1.017 | 4 | 0.966 | 0.907–1.111 | não |
| 2500 | 0.1–0.2 | 29/353 | 0.959 | 0 | — | —–— | não |
| 2500 | 0.5–0.6 | 175/45 | 1.066 | 0 | — | —–— | não |
| 2500 | 0.7–0.8 | 348/63 | 1.026 | 0 | — | —–— | não |
| 2500 | 0.8–0.9 | 1970/165 | 1.064 | 1 | 0.944 | 0.944–0.944 | não |
| 2500 | 0.9–1.0 | 1268/183 | 1.011 | 2 | 0.990 | 0.936–1.043 | não |
| 3000 | 0.8–0.9 | 159/22 | 1.044 | 0 | — | —–— | não |
| 3000 | 0.9–1.0 | 379/23 | 0.992 | 0 | — | —–— | não |


0/30 células cumprem consistência; 14 ficam fora de ±4% no agregado. Em 850 RPM × 0,4–0,5 bar, 9/12 sessões estão acima de +4% (75%): sinal frequente, abaixo do critério de 80%. Não confundir divergência agregada com repetição entre sessões. Sem célula comparável a 3500 RPM por falta de ≥15 quadros de gasolina. CSVs: `equivalencia_celulas.csv` e `equivalencia_por_sessao.csv`.

## 5. Melhorias na exportação (até 8)

1. `acquisition_id` persistente, `acquisition_started/finished/reset`, combustível, motivo e referência de origem; distinguir coleta nova de buffer herdado.

2. `calibration_changed`: contador antes/depois, causa ECU/app/reset, curvas completas antes/depois, eixo, hash, ACK/readback e instante efetivo; inclusive a leitura inicial.

3. `calibration_state_id` em cada telemetry: hashes e versões da Curva K e Mapa K, com mapa/eixos completos no início e em toda mudança.

4. Schema versionado por campo: raw, encoding, divisor, unidade e versão do decodificador; preservar 30 nós independentemente da validação antiga.

5. Em buffers: número de amostras por posição, início/fim da coleta, RPM/temperatura e instante por campo, máscara de validade/coerência.

6. Relógio monotônico comum, timestamp de captura e gravação, sequência do frame, perdas e lacunas; manter identidade da sessão em todos os segmentos.

7. `engine_stall` com ID do episódio, combustível, janela de telemetria antes/depois, curva/mapa vigentes, cutoff, recuperação e distinção de desligamento intencional. Preservar esse evento no corpus compacto.

8. Exportar condições de comparação: janela estável e motivo de rejeição, temperatura/pressão, velocidade/carga e, quando disponíveis, lambda/STFT/LTFT; usar null e origem quando indisponíveis.

Verificação: `python3 -I codex/verificar_resultados.py` confere hashes, IDs, totais, limites de exposição e as três curvas novas contra os eventos nativos de AutoMatch, nó a nó. O analisador legado confirma independentemente os 14 incrementos antigos. A telemetria compacta antiga não conserva todos os raw/schema originais; as razões usam as unidades exportadas, sem recalibrar retrospectivamente esses frames.
