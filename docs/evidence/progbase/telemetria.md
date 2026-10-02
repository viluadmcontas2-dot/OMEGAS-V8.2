# Tema 2 — Telemetria ao vivo (`48 01` → 34 bytes)

Fontes: LN (9.408 quadros `48 01` no prefixo parseado; notação em `protocolo.md`) e DUMP (DFM `DUMP/RT_RCDATA(10)__TFORMVISUALIZZA__0.bin`, `__TAUTOCALUI__0.bin`; desmontagem do consumidor em `DUMP/ProgBase.exe.Dump.bin`, VAs `0x004381FC`, `0x004A307C`, `0x005158F4`, registradas em estudo anterior e **não reexecutadas nesta branch**, ver `lacunas.md` L-12). Selos em `README.md`.

## 2.1 Quadro

`48 01 49` → `48 01 49 | 53 | 22 | payload[34] | cs`. `PROVADO` (LN; `len` sempre `0x22`). O ProgBase copia o payload para `TStreamDati+0x293C` e as telas (`TFormVisualizza.Timer1`, `TFormConfig.TimerDati`, `TAutoCalUI`) leem dali a cada 75 ms (`Timer1.Interval = 75`, DFM `TFORMVISUALIZZA`).

## 2.2 Tabela offset → campo → escala → validade → exibição

| Off | Larg. | Nome | Encoding | Escala | Validade | Exibição no ProgBase | Selo | Fonte |
|---:|---:|---|---|---|---|---|---|---|
| 0 | 2 | RPM | u16 LE | identidade (rpm) | 0 com motor parado | `LabelGiri` + ponteiro `ChartGiri` ("rpm x 1000") | PROVADO | desmontagem (`rpm`); LN 0…2877 |
| 2 | 2 | pressão MAP bruta do sensor (pré-linearização) | u16 LE | não exibida; regressão no LN: `MAP_mbar = 96,4 + raw × 0,03824` (r = 0,9999, 9.408 quadros) | satura ~23,4k com motor parado (1 atm) | não exibida | INFERIDO (identidade pela correlação; nome original desconhecido) | LN: 23389↔992 mbar, 20883↔894, 6288↔335, 2601↔198 |
| 4 | 2 | sempre 0 | u16 | — | — | — | DESCONHECIDO | LN: único valor 0 |
| 6 | 2 | tempo de injeção **gás** (banco 1) | u16 LE | `ms = raw × BASE_TEMPI_GLOBALE × 1e-6` = `raw × 0,00256` (2.3) | 0 quando não injeta gás; satura 13382 (0x3446) na partida | `LabTempoIniezioneGas` | PROVADO (campo) / INFERIDO (escala) | desmontagem (`gas_injection_raw`); LN seq 6109: 7755 → 19,85 ms |
| 8 | 2 | tempo de injeção **gasolina** (banco 1) | u16 LE | idem | 0 em cutoff | `LabTempoIniezioneBenzina`; eixo X do AutoCal (`RunPoint.x`) | PROVADO (campo e uso) / INFERIDO (escala) | desmontagem: `x = u16(payload[8:10]) × TStreamDati[0x2980] × 0,00025 × 0,004` |
| 10 | 1 | bits desconhecidos | u8 | — | 0 normalmente; 4 e 5 com motor parado ou na partida | — | DESCONHECIDO | LN: valores {0,4,5} (seq 15791, 15808) |
| 11 | 1 | estado de combustível (bitfield) | u8 | ver 2.4 | — | ícones `Sotto chiave`, `Cutoff`, `Gas`, `Benzina` | PROVADO (byte) / INFERIDO (bits) | desmontagem (`fuel_state`); LN distribuição |
| 12 | 1 | temperatura da água (motor) | u8 | hipótese `°C = 109 − raw` (não provada no original) | — | `LabTempMotore` | PROVADO (campo) / INFERIDO (escala) | LN 34…43 (→ 66…75 °C com a hipótese, plausível para motor aquecido) |
| 13 | 1 | **LEVEL raw** (nível do cilindro) | u8 | nenhuma conversão no quadro; ver `level.md` | — | `LabLivello` (valor bruto) | PROVADO | desmontagem (`levels_raw`, `TFormConfig+0x1800`); LN 177 (0xB1) em regime, 51→21 com chave desligada (seq 2055–2064) |
| 14 | 2 | pressão do gás (trilho) | u16 LE | hipótese `bar = raw/800` (não provada no original) | — | `LabPressione` "0,00bar" | PROVADO (campo) / INFERIDO (escala) | LN 1718…3121 (→ 2,15…3,90 bar); motor parado 2168 |
| 16 | 1 | temperatura do gás (redutor) | u8 | hipótese `°C = raw − 20` (não provada no original); mesma escala bruta do eixo `RIF_TEMP_GAS` SC 92 (0…255) | — | `LabTempRiduttore` | PROVADO (campo) / INFERIDO (escala) | LN 58…75 |
| 17 | 2 | MAP (pressão do coletor) | **s16 LE** | `bar = trunc(raw/10) × 0,01` = `raw/1000` com resolução 0,01 | 992 (0,99 bar) com motor parado = atmosférica | `LabMap` "0,00bar"; eixo Y do AutoCal (`RunPoint.y`) | PROVADO | desmontagem (`y_transform`); LN 168…992 |
| 19 | 1 | tensão nos injetores de gás | u8 | `V ≈ raw/16` | 0 em gasolina; 209…227 em GNV | `LabTensioneIniettori` | INFERIDO (identidade pelo padrão 0-em-gasolina e pela escala 13–14 V; rótulo da tela) | LN: 0 em todos os quadros `0x80/0x88`, 209–227 nos `0x90/0x94` |
| 20 | 4 | sempre 0 | — | — | — | — | DESCONHECIDO | LN |
| 24 | 2 | tempo de injeção gás, banco 2 | u16 LE | idem offset 6 | 0 quando banco 2 inexistente | `LabTempoIniezioneGas2` | PROVADO (campo) | desmontagem; LN ≈ offset 6 ±0,5 % |
| 26 | 2 | sempre 0 | — | — | — | — | DESCONHECIDO | LN |
| 28 | 2 | tempo de injeção gasolina, banco 2 | u16 LE | idem offset 8 | — | `LabTempoIniezioneBenzina2` | PROVADO (campo) | desmontagem; LN ≈ offset 8 |
| 30 | 4 | sempre 0 | — | — | — | — | DESCONHECIDO | LN |

Campos da tela `Visualizza` sem offset identificado: `Lambda`, `Lambda 2`, `TPS` (ocultos por padrão, `Visible = False` no DFM). `DESCONHECIDO`; provar com captura de carro com sonda lambda ligada à MP48.

## 2.3 Escala do tempo de injeção: constante do programa × parâmetro da ECU

- `PROVADO` (desmontagem): o ProgBase calcula `X = u16(payload[8:10]) × TStreamDati[+0x2980] × 0,00025 × 0,004`, isto é `raw × campo × 1e-6`.
- `PROVADO` (fiação): no dump de conexão o ProgBase lê `09 79 00 82 → 53 02 00 0A` = **`BASE_TEMPI_GLOBALE` (SC 121) = 2560** (LN seq 28; `parametros.md`).
- `INFERIDO`: o campo `+0x2980` é o cache de `BASE_TEMPI_GLOBALE`; `2560 × 1e-6 = 0,00256 ms/contagem`. A escala **é um parâmetro da ECU**, não uma constante do programa.
- Valores conferidos no LN: seq 6073 (2877 rpm, MAP 0,335 bar) gasolina 1213 → 3,11 ms e gás 2437 → 6,24 ms; seq 6109 (1006 rpm, carga) gasolina 3999 → 10,24 ms e gás 7755 → 19,85 ms; seq 715 (marcha lenta GNV) gasolina 1652 → 4,23 ms e gás 3298 → 8,44 ms. Razão gás/gasolina 1,9–2,0 em todos, coerente com GNV.
- Mesma base vale para `TEMPI_PER_K` (SC 55) e tempos mortos (SC 125/126): `parametros.md`.
- Os vetores AutoCal usam outra representação (`/512` ms, `/1024` bar): `autocal.md`.

## 2.4 Byte 11: estado de combustível

Valores vistos no LN (9.408 quadros): `0x90` 5.349, `0x80` 3.387, `0x00` 320, `0x88` 318, `0x10` 16, `0x94` 9, `0x08` 7, `0x91` 2.

| Bit | Significado | Selo | Evidência |
|---|---|---|---|
| `0x80` | motor em funcionamento / injeção ativa | INFERIDO | ausente em todos os quadros com RPM 0 (`0x00`, `0x08`, `0x10`) |
| `0x10` | modo gás selecionado | PROVADO | desmontagem: seletor 0/1/2 pelo bit `0x10` (`state_selector`); LN: `0x90/0x94/0x91` só com gás injetando; `0x10` com motor parado (seq 15808) |
| `0x08` | comutação gasolina→gás em curso | INFERIDO | `0x88` (seq 2601): gás = 0, gasolina 1534, pouco antes de `0x90` |
| `0x04` | cutoff (injeções 0 com motor girando) | INFERIDO | `0x94` (seq 6087): 2181 rpm, gás 0, gasolina 0 |
| `0x01` | desconhecido (partida com gás saturado) | DESCONHECIDO | `0x91` (seq 15791): 475 rpm, gás 13382 |
| outros | não vistos no LN | DESCONHECIDO | — |

## 2.5 Validação com números reais (todos do LN)

| Quadro | RPM | Petrol raw → ms | Gas raw → ms | MAP raw → bar | P gás raw (→ bar, hipótese /800) | Água raw (→ °C, hipótese) | T gás raw (→ °C, hipótese) | Level | b11 | b19 |
|---|---:|---|---|---|---|---|---|---:|---|---:|
| seq 6073 / idx 176512 (`3D 0B 90 18 00 00 85 09 BD 04 00 90 28 B1 DB 06 44 4F 01 DD …`) | 2877 | 1213 → 3,11 | 2437 → 6,24 | 335 → 0,335 | 1755 (2,19) | 40 (69) | 68 (48) | 177 | `90` | 221 |
| seq 6109 / idx 177520 (`EE 03 8F 28 00 00 4B 1E 9F 0F 00 90 29 B1 E7 06 44 EC 01 DF …`) | 1006 | 3999 → 10,24 | 7755 → 19,85 | 492 → 0,492 | 1767 (2,21) | 41 (68) | 68 (48) | 177 | `90` | 223 |
| seq 715 (`42 03 95 1C 00 00 E2 0C 74 06 00 90 2A B1 72 07 4B 76 …`) | 834 | 1652 → 4,23 | 3298 → 8,44 | 374 → 0,374 | 1906 (2,38) | 42 (67) | 75 (55) | 177 | `90` | … |
| seq 9822 gasolina (`03 07 29 0A 00 00 00 00 29 03 00 80 24 B1 1B 09 42 C6 00 00 …`) | 1795 | 809 → 2,07 | 0 | 198 → 0,198 | 2331 (2,91) | 36 (73) | 66 (46) | 177 | `80` | 0 |
| seq 2782 motor parado (`00 00 30 5B 00 00 00 00 00 00 00 00 29 15 C6 07 48 E0 03 …`) | 0 | 0 | 0 | 992 → 0,992 (atmosférica) | 1990 (2,49) | 41 (68) | 72 (52) | 21 | `00` | 0 |

Checksums de resposta conferidos pelo parser em todos os quadros acima. A linha "motor parado" confirma a escala do MAP (992 mbar ≈ pressão atmosférica ao nível do mar) e a convenção s16. As colunas "hipótese" só mostram que as três escalas candidatas produzem valores fisicamente plausíveis; nenhuma delas tem prova no DUMP ou no LN (`lacunas.md` L-04).
