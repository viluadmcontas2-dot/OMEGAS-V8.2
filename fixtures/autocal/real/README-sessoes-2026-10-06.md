# Fixtures reais de 06/10/2026 (recortes de sessões gravadas pelo app)

Origem: sessões do OMEGAS gravadas no carro e analisadas na noite de 06→07/10 (achados em `docs/teia/ACHADOS.md`). Recortes mínimos para replay; as sessões completas não estão no repositório.

| Arquivo | Sessão | O que tem | Para que serve |
| --- | --- | --- | --- |
| `pista_2026-10-06_2030.jsonl.gz` | 20:30 (pista, 53 min) | 8.937 leituras, 93 snapshots nativos, 2 RESET_GAS, 9 eventos (quase-apagões, início/fim) | Ciclo automatch → refino; 6 automatches em 30 min; rpm de cruzeiro |
| `util_2026-10-06_1921.jsonl.gz` | 19:21 (a "sessão útil") | 4.303 leituras, 15 snapshots, 16 ações (15 `DELETE_POINT` manuais do dono) | Comparar o que o detector apagaria com o que o dono apagou à mão; carro quase todo em marcha lenta (rpm mediano ~870) |

## Formato (um JSON por linha, gzip)
- `telemetry`: `{type, t, data:{rpm, load_bar, petrol_ms, gas_ms_diagnostic, fuel, water_c, gas_c, dynamic_correction}}`, `t` = `recordedAtMs`.
- `autocal_native_snapshot`: `{type, t, data:{capturedAtMs, fields:[{key, status, rawValues, rawPayloadHex}]}}` só com: `MNFLD_PRESS_BUF`, `PETR_INJ_TBUF`, `MNFLD_PRESS_BUF_GAS`, `PETR_INJ_TBUF_GAS`, `NUM_BUF_UPD_PETR`, `NUM_BUF_UPD_GAS`, `MUL_ACT` (use `rawPayloadHex`: 30×u16 LE ÷ 16384), `AUTO_CAL_ENABLE`, `MNFLD_PRESS_THD`, `ACQUIRED_ZONES_*`. Escalas: pressão raw/1024 bar, tempo raw/512 ms, 18 bandas (16 úteis).
- `autocal_native_action`: `{action, ackStatus, startedAtMs, finishedAtMs, outcome, automatic, details}` (alvos do `DELETE_POINT` em `details.targets` quando a build registrava).
- `engine_stall`, `session_started`, `session_stopped`: `data` original.

Leitura: `gzip.open(path,'rt')` e `json.loads` por linha (Python), ou `GZIPInputStream` + `BufferedReader` (JVM).
