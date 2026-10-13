# Sessões Úteis — corpus compacto (fonte: Drive `Meu Drive/OMEGAS/Sessoes Uteis`)

Gravações reais do app OMEGAS (central Android ligada à ECU Omegas/MP48), 2026-09-24 → 2026-10-12, de várias versões do app.
**Só fonte de informação:** não substitui algoritmos nem correções já feitas.

| Arquivo | Conteúdo |
|---|---|
| `telemetria.csv.gz` | 177 mil amostras (≈250 ms) de 93 sessões: rpm, gas_ms, petrol_ms, fuel_byte, fuel, state, map_bar, dyn (byte 19), pressão do gás, temperaturas |
| `eventos-calibracao.jsonl.gz` | `k_factor_batch_confirmed` (escritas reais da curva K, antes/depois), `autocal_native_snapshot` (curva, contadores, zonas), logs de K/mapa/reset |
| `sessoes.json` | resumo por sessão (`session_summary.json` original + contagens) |
| `drive-ids.json` | IDs dos zips originais no Drive e o que NÃO deu para baixar |
| `integridade.json` | sha256 e contagens (conferidos por `tests/test_sessoes_uteis_corpus.py`) |

Regenerar a partir dos zips: `python3 -I tools/sessoes/extrair_corpus.py <pasta extraída> tests/fixtures/sessoes-uteis`.
Carregar em análise: `tools/sessoes/corpus.py`. Dados brutos completos (≈540 MB extraídos) ficam só no Drive: baixar com o conector **um zip por vez** (paralelo derruba a conexão; zips >~6 MB falham).
Limites honestos: relógio da central desacertado em parte das sessões (README do Drive); GNV real só em ~14 pacotes (rank001–092); sessões `cng=0` são gasolina.
