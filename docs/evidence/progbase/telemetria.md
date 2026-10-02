# Tema 2 — Telemetria ao vivo (`48 01` → 34 bytes)

Fonte primária: capturas Portmon (`LN` = Lognovo 63 MB, 9.408 quadros; `1.LOG`/`3.LOG`; `PortmonAUTOCAL` 21.167 quadros) e desmontagem do consumidor original (`tests/fixtures/progbase-autocal-consumer-map-v1.json#live_telemetry`, VAs `0x004381FC`, `0x004A307C`, `0x005158F4`). Convenções de selo em `README.md`.

## 2.1 Quadro

`48 01 49` → `48 01 49 | 53 | 22 | payload[34] | cs`. `PROVADO` (todas as capturas; `len` sempre `0x22`). O ProgBase copia o payload para `TStreamDati+0x293C` e a tela (`TFormVisualizza`, `TFormConfig.TimerDati`, `TAutoCalUI`) lê dali a cada 75 ms.

## 2.2 Tabela offset → campo → escala → validade → exibição

| Off | Larg. | Nome | Encoding | Escala usada pelo original | Validade | Exibição no ProgBase | Selo | Fonte |
|---:|---:|---|---|---|---|---|---|---|
| 0 | 2 | RPM | u16 LE | identidade (rpm) | 0 com motor parado | `LabelGiri` "0000" + ponteiro (`ChartGiri`, "rpm x 1000") | PROVADO | consumer-map `rpm_raw`; LN: 0…2877; `1.LOG` 866 |
| 2 | 2 | pressão MAP bruta do sensor (pré-linearização) | u16 LE | não exibida; `MAP_mbar ≈ 100 + raw/26,2` nos dados | satura ~23.4k com motor parado (1 atm) | não exibida | INFERIDO (identidade e relação linear; nome original desconhecido) | LN: 23389↔992 mbar, 20883↔894, 13203↔600, 7214↔374, 2601↔198; `.lec` `OffsetMapInterno=100` |
| 4 | 2 | sempre 0 | u16 | — | — | — | DESCONHECIDO | LN: único valor 0 em 9.408 quadros |
| 6 | 2 | tempo de injeção **gás** (banco 1) | u16 LE | `ms = raw × BASE_TEMPI_GLOBALE × 1e-6` = `raw × 0,00256` | 0 quando não injeta gás; satura 13382 (0x3446) na partida | `LabTempoIniezioneGas` "0ms" | PROVADO (campo) / INFERIDO (escala, ver 2.3) | consumer-map `gas_injection_raw`; `1.LOG` 4350 → 11,1 ms em marcha lenta GNV |
| 8 | 2 | tempo de injeção **gasolina** (banco 1) | u16 LE | idem | 0 em cutoff | `LabTempoIniezioneBenzina`; eixo X do AutoCal (`RunPoint.x`) | PROVADO (campo e uso) / INFERIDO (escala) | consumer-map `x_transform = u16(payload[8:10]) × TStreamDati[0x2980] × 0.00025 × 0.004` |
| 10 | 1 | bits desconhecidos | u8 | — | 0 normalmente; 4 e 5 vistos com motor parado | — | DESCONHECIDO | LN: valores {0,4,5} |
| 11 | 1 | estado de combustível (bitfield) | u8 | ver 2.4 | — | ícones `Sotto chiave`, `Cutoff`, `Gas`, `Benzina` | PROVADO (byte) / INFERIDO (bits) | consumer-map `fuel_state`; LN distribuição |
| 12 | 1 | temperatura da água (motor) | u8 | `°C = 109 − raw` (OMEGAS) | — | `LabTempMotore` "0°C" | INFERIDO (escala) | LN 34…43 → 66…75 °C; `1.LOG` 45 → 64 °C; `.lec` `EcuTempMotore[30]` sugere tabela, não reta |
| 13 | 1 | **LEVEL raw** (nível do cilindro) | u8 | nenhuma conversão no quadro; ver `level.md` | — | `LabLivello` "0" (valor bruto) | PROVADO | consumer-map `levels_raw`; `TFormConfig+0x1800`; LN 177 (0xB1) quase constante, `1.LOG` 251 |
| 14 | 2 | pressão do gás (trilho) | u16 LE | `bar = raw/800` (OMEGAS) | — | `LabPressione` "0,00bar" | INFERIDO (escala) | LN 1718…3121 → 2,15…3,90 bar; motor parado 2168 → 2,71 bar |
| 16 | 1 | temperatura do gás (redutor) | u8 | `°C = raw − 20` (OMEGAS) | — | `LabTempRiduttore` "0°C" | INFERIDO (escala) | LN 58…75 → 38…55 °C; `.lec` `RifMot` 0…255 é a mesma escala bruta do SC 92 |
| 17 | 2 | MAP (pressão do coletor) | **s16 LE** | `bar = trunc(raw/10) × 0,01` = `raw/1000` com resolução 0,01 | 992 (0,99 bar) com motor parado = atmosférica | `LabMap` "0,00bar"; eixo Y do AutoCal (`RunPoint.y`) | PROVADO | consumer-map `y_transform`; LN 168…992; `1.LOG` 462 → 0,46 bar |
| 19 | 1 | tensão nos injetores de gás | u8 | `V ≈ raw/16` | 0 em gasolina; 209…227 em GNV | `LabTensioneIniettori` "0V" | INFERIDO (identidade pelo padrão 0-em-gasolina e pela escala 13–14 V; nome do rótulo da tela) | LN: 0 em todos os quadros `0x80/0x88`, 209–227 nos `0x90/0x94` |
| 20 | 4 | sempre 0 | — | — | — | — | DESCONHECIDO | LN |
| 24 | 2 | tempo de injeção gás, banco 2 | u16 LE | idem offset 6 | 0 quando banco 2 inexistente | `LabTempoIniezioneGas2` | PROVADO (campo) | consumer-map `gas_injection_bank2_raw`; LN ≈ offset 6 ±0,5 % |
| 26 | 2 | sempre 0 | — | — | — | — | DESCONHECIDO | LN |
| 28 | 2 | tempo de injeção gasolina, banco 2 | u16 LE | idem offset 8 | — | `LabTempoIniezioneBenzina2` | PROVADO (campo) | consumer-map; LN ≈ offset 8 |
| 30 | 4 | sempre 0 | — | — | — | — | DESCONHECIDO | LN |

Campos da tela `Visualizza` sem offset identificado: `Lambda`, `Lambda 2`, `TPS` (ocultos por padrão, `Visible=False`). `DESCONHECIDO`; provar com captura de carro com sonda lambda ligada à MP48.

## 2.3 Escala do tempo de injeção: constante do app × parâmetro da ECU

- `PROVADO` (desmontagem): o ProgBase calcula `X = u16(payload[8:10]) × TStreamDati[+0x2980] × 0,00025 × 0,004`, isto é `raw × campo × 1e-6`.
- `PROVADO` (fiação): no dump de conexão o ProgBase lê `09 79 00 82 → 53 02 00 0A` = **`BASE_TEMPI_GLOBALE` (SC 121) = 2560** (LN seq 28; `parametros.md`).
- `INFERIDO`: o campo `+0x2980` é o cache de `BASE_TEMPI_GLOBALE`; `2560 × 1e-6 = 0,00256 ms/contagem`, exatamente a constante `Mp48TelemetryScale.INJECTION_MS_PER_COUNT` do OMEGAS. A escala **é um parâmetro da ECU**, não uma constante. Valores conferidos: `1.LOG` gasolina 1941 → 4,97 ms e gás 4350 → 11,14 ms em marcha lenta GNV; `3.LOG` 5495 → 14,07 ms e 11184 → 28,6 ms a 1853 rpm/0,89 bar (plena carga).
- Mesma base vale para `TEMPI_PER_K` (SC 55) e tempos mortos (SC 125/126): `parametros.md`.
- Os vetores AutoCal usam outra representação (`/512` ms, `/1024` bar): `autocal.md`.

## 2.4 Byte 11: estado de combustível

Valores vistos no LN (9.408 quadros): `0x90` 5.349, `0x80` 3.387, `0x00` 320, `0x88` 318, `0x10` 16, `0x94` 9, `0x08` 7, `0x91` 2. O OMEGAS também registrou `0xA0/0xA8/0xB0` em sessões próprias (`Mp48Protocol.decodeStrict`).

| Bit | Significado | Selo | Evidência |
|---|---|---|---|
| `0x80` | motor em funcionamento / injeção ativa | INFERIDO | ausente em todos os quadros com RPM 0 (`0x00`, `0x08`, `0x10`) |
| `0x10` | modo gás selecionado | PROVADO | desmontagem: seletor 0/1/2 pelo bit `0x10` (`consumer-map#state_selector`); LN: `0x90/0x94/0x91` só com gás injetando |
| `0x08` | comutação gasolina→gás em curso | INFERIDO | `0x88` aparece com gás = 0 e gasolina > 0, pouco antes de `0x90` |
| `0x04` | cutoff (injeções 0 com motor girando) | INFERIDO | `0x94`: 2695 rpm, gás 0, gasolina 0 |
| `0x20` | desconhecido | DESCONHECIDO | só nas sessões do OMEGAS (`0xA0…`) |
| `0x01` | desconhecido (partida com gás saturado) | DESCONHECIDO | `0x91`: 475 rpm, gás 13382 |

## 2.5 Validação com números reais

| Quadro (fonte) | RPM | Petrol raw → ms | Gas raw → ms | MAP raw → bar | P gás raw → bar | Água → °C | T gás → °C | Level | b11 | b19 |
|---|---:|---|---|---|---|---|---|---:|---|---:|
| `1.LOG` ev. 1–43 (`62 03 96 25 00 00 FE 10 95 07 00 90 2D FB 48 07 48 CE 01 E2 …`) | 866 | 1941 → 4,97 | 4350 → 11,14 | 462 → 0,462 | 1864 → 2,33 | 45 → 64 | 72 → 52 | 251 | `90` | 226 |
| `3.LOG` ev. 1–43 (`3D 07 93 51 00 00 B0 2B 77 15 00 90 2B FC D3 06 47 7E 03 DF …`) | 1853 | 5495 → 14,07 | 11184 → 28,63 | 894 → 0,894 | 1747 → 2,18 | 43 → 66 | 71 → 51 | 252 | `90` | 223 |
| LN seq 715 (`42 03 95 1C 00 00 E2 0C 74 06 00 90 2A B1 72 07 4B 76 …`) | 834 | 1652 → 4,23 | 3298 → 8,44 | 374 → 0,374 | 1906 → 2,38 | 42 → 67 | 75 → 55 | 177 | `90` | … |
| LN gasolina (`03 07 29 0A 00 00 00 00 29 03 00 80 24 B1 1B 09 42 C6 00 00 …`) | 1795 | 809 → 2,07 | 0 | 198 → 0,198 | 2331 → 2,91 | 36 → 73 | 66 → 46 | 177 | `80` | 0 |
| LN motor parado (`00 00 5D 5B 00 00 00 00 00 00 00 00 29 B1 78 08 49 E0 03 …`) | 0 | 0 | 0 | 992 → 0,992 (atmosférica) | 2168 → 2,71 | 41 → 68 | 73 → 53 | 177 | `00` | 0 |

Checksums de resposta conferidos pelo parser em todos os quadros acima. A linha "motor parado" confirma a escala do MAP (992 mbar ≈ pressão atmosférica ao nível do mar) e a convenção s16.

## 2.6 Anotações para o contraste com o OMEGAS

- Escala de injeção fixa (0,00256) em vez de `BASE_TEMPI_GLOBALE` lido da ECU (2.3).
- `pressureDiffBar = P gás − MAP` só faz sentido se as duas estiverem na mesma unidade; a escala `/800` da pressão do gás continua `INFERIDO` (2.2).
- Offsets 2–3 e 19 têm identidade provável (MAP bruto; tensão dos injetores de gás) e hoje são ignorados ou chamados de `unknownRaw19`.
- O decodificador do OMEGAS procura o payload "deslizando" dentro de buffers maiores (`decodeTelemetry`); o original lê posição fixa após `53 22`.
