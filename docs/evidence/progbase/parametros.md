# Tema 3 — Parâmetros (SerialCodes) relevantes para protocolo, telemetria, curvas/mapas e nível

Só entram os SC que mudam como se conversa com a ECU nessas superfícies. Forma e encoding vêm do DFM do DUMP (`fontes/parametros-dfm-inventario.json`, gerado nesta branch a partir de `DUMP/RT_RCDATA(10)__TSTREAMDATI__0.bin` e `__TAUTOCALDM__0.bin`, com offset de cada componente); comandos e **valores reais desta ECU** vêm do LN (dump de conexão seq 31–410; ver `protocolo.md` 1.4). "Consumidor" = tela/rotina do ProgBase que usa o objeto (`FileSection/FileKeyName` do DFM ou RTTI/desmontagem). R/W = o que o ProgBase faz na fiação (`R` lê no dump; `W` escreve em alguma captura; `w` = escrita só provada por desmontagem).

Encodings: U8/U16/S16 little-endian; `Q14` = U16 com 1,0 = 16384. "Tempo" = contagens × `BASE_TEMPI_GLOBALE`/1e6 ms (ver 3.2).

## 3.1 Identidade, sessão e base de tempo

| SC | Hex | Nome (DFM) | Enc. | Forma | Tam. | Escala | R/W | Consumidor no ProgBase | Valor nesta ECU | Selo |
|---:|---|---|---|---|---:|---|---|---|---|---|
| 2 | 0x0002 | `REGISTRO_EE` | U8 | escalar | 1 | identidade | R | primeira leitura após `00 25` | `AC` (LN seq 13) | PROVADO |
| 3 | 0x0003 | `FLAG_CONF1` | U16 | vetor | 2 | bitfield | R/W | `TFormConfig` flags; bit 0x0800 = edição do Mapa K (`protocolo.md` 1.6) | `0x2486, 0x1051` (seq 16) | PROVADO |
| 21 | 0x0015 | `MODELLO_HARDWARE` | U8 | escalar | 1 | enum | R | seleção de família LR/MP48 nas telas | `4` (seq 115) | PROVADO |
| 53 | 0x0035 | `IDENTIFICATIVO` | U8 | vetor | 30 | ASCII | R/w | `TFormConfig` descrição do veículo | `"208 01 08 2"` (seq 138) | PROVADO |
| 75 | 0x004B | `CILINDRATA` | U16 | escalar | 1 | cc | R/w | `TFormConfig` | `1000` (seq 149) | PROVADO |
| 76 / 91 | 0x004C / 0x005B | `MINIMA_VERSIONE_INTERFACCIA` / `WARNING_VERSIONE_INTERFACCIA` | U16 | escalar | 1 | versão | R | gate de compatibilidade do ProgBase na conexão (seq 8–9, antes do dump) | `0x0401` ambos | PROVADO |
| 121 | 0x0079 | `BASE_TEMPI_GLOBALE` | U16 | escalar | 1 | 1e-6 ms por contagem de tempo | R | cache `TStreamDati+0x2980` usado nos tempos de injeção de Timer1 (`telemetria.md` 2.3) | `2560` → 0,00256 ms/cont. (seq 28, lido antes do dump) | PROVADO (valor, vínculo ao cache e escala de Timer1; telemetria.md 2.3) |
| 371 | 0x0173 | `MODULE_VERSION` (AutoCal) | U8 | elemento 0 | 1 | identidade | R | `TAutoCalDM`, decide 18/30 pontos | `4` (seq 19, lido 8×) | PROVADO |
| — | 0x0000 | (sem componente DFM) | ASCII | vetor | 20 | — | R | identificador da ECU lido em toda conexão (`29 00 00`) | 20 caracteres (seq 24) | PROVADO (existe) / DESCONHECIDO (semântica) |

## 3.2 Mapa K e seus eixos (detalhe em `curvas-mapas.md`)

| SC | Hex | Nome | Enc. | Forma | Tam. | Escala | R/W | Consumidor | Valor nesta ECU | Selo |
|---:|---|---|---|---|---:|---|---|---|---|---|
| 84 | 0x0054 | `MAP_K` | U8 | matriz | 13×12 | exibição identidade (DFM); neutro: ver `curvas-mapas.md` 5.3 | R (`2A`) / W (`14`) | `TFormCentriCelleK`, `TAebGrid` (mapa de coeficientes K) | linhas 0–11: 0xA2…0xB5; linha 12: `89 89 89 89 89 89 8B 8C 8D 8E 8F 8F` (seq 36–48) | PROVADO |
| 55 | 0x0037 | `TEMPI_PER_K` | S16 | vetor | 12 | tempo (×0,00256 ms) | R/W (`14 37 00 i lo hi`) | eixo de linhas do Mapa K ("T. inj (ms)") | `781 977 1172 1367 1758 2344 3125 3906 4687 5469 6250 7031` = 2,0 2,5 3,0 3,5 4,5 6,0 8,0 10,0 12,0 14,0 16,0 18,0 ms (seq 33) | PROVADO (valor) / INFERIDO (escala = base de tempo, `telemetria.md` 2.3) |
| 61 | 0x003D | `GIRI_PER_K` | S16 | vetor | 12 | rpm | R/W (`14 3D 00 i lo hi`) | eixo de colunas do Mapa K ("Colonne dei GIRI (rpm)") | inicial `1000 1500 2000 2500 3000 3500 4000 4500 5000 5500 6000 6500`; editado na sessão para `850 1350 1850 2500 …` (seq 15226–15228) | PROVADO |
| 12 | 0x000C | `RIF_GIRI` | U16 | vetor | 12 | rpm | R | eixo de rotação do mapa de carburação (`TFormCentriCelle`), não do Mapa K | `500 1000 1500 … 6000` (seq 108) | PROVADO |
| 188 | 0x00BC | `K_MAPPA_NEUTRO` | U8 | escalar | 1 | identidade | R | `TStreamDati`; uso na UI não localizado | `0x55` = 85 (seq 238) | PROVADO (valor) / DESCONHECIDO (semântica) |

## 3.3 Curva K e AutoCal (detalhe e valores em `autocal.md` e `curvas-mapas.md`)

Família `0x014A…0x018E` do `TAutoCalDM`: `AUTO_CAL_ENABLE` 330, `PETR_INJ_TBP` 331, `MNFLD_PRESS_THD` 332, `NUM_BUF_UPD_PETR/GAS` 347/348, `PETR_INJ_TBUF_GAS_PREV` 349, `MNFLD_PRESS_BUF_GAS_PREV` 350, `PETR_INJ_TBUF_GAS` 351, `MNFLD_PRESS_BUF_GAS` 352, `MUL_ACT` 353, `PETR_INJ_TBUF` 354, `MNFLD_PRESS_BUF` 355, `VECT_AUTOCAL_EE` 356, `VECT_AUTOCAL_U8_0/1/2` 357, `EN_CDN_T_THD` 359, `LIMIT_PRESSURE_MIN/MAX` 361/362, `PETROL/GAS_POINT_2DELETE` 365/366, `ACQUIRED_ZONES_PETROL/GAS` 367/368, `CALIBRATION_VAL_1` 370, `MODULE_VERSION` 371, `NUM_ATUOMATCH_EXECUTED` 372, `MAX_RPM_FOR_AUTOCAL` 378, `DIFF/DELTA_ENG_SPD_THD` 387/388, `DIFF/DELTA_MNFLD_PRESS_THD` 389/390, `DIFF/DELTA_PETR_TINJ_T_THD` 391/392, `DISABLE_ACQ_BAND` 395, `PETR/GAS_MNFLD_PRESS_RV` 397/398, e a superfície EEPROM `TAutoCalDM_EE` (`MUL_ACT_EE` 344, `MUL_PREV_EE` 345, `MUL_UPD_CALL_CNTR_EE` 346…). Todas lidas no dump de conexão (LN seq 31–410) exceto 365/366 (nunca lidas) e 356 (`CA 01 10`).

## 3.4 Nível do cilindro (detalhe em `level.md`)

| SC | Hex | Nome | Enc. | Forma | Tam. | Escala (DFM) | R/W | Consumidor | Valor nesta ECU | Selo |
|---:|---|---|---|---|---:|---|---|---|---|---|
| 36 | 0x0024 | `TIPO_SENSORE` | U8 | escalar | 1 | enum (`ComboSensore`) | R/w | `TFormConfig/GroupRifSensore` | `0x81` (seq 125) | PROVADO |
| 37 | 0x0025 | `RIF_SENSORE` | U8 | vetor | 4 | raw do sensor (reserva, 1/4, 2/4, 3/4) | R/w | idem; `ButtonDoLevelClick` | `39 93 143 219` (seq 126) | PROVADO |
| 71 / 72 | 0x0047 / 0x0048 | `LIVELLO_EMUL_ALTO` / `_BASSO` | U8 | escalar | 1 | raw | R | emulação do indicador de nível original | `41` / `5` | PROVADO (valor) |
| 276 | 0x0114 | `LO_PASS_FILT_CON_FAST` (idx 0) / `_SLOW` (idx 1) | U16 | vetor | 2 | `raw/32768` | R | filtro do nível (`VectAgaslevKFilter`) | `32768` → 1,0 ; `4915` → 0,15 (seq 285–286; lido com `0A 14 01 i`) | PROVADO |
| 300 | 0x012C | `ISTERESI_RIACCENSIONE` (idx 0), `SOGLIA_LED_1..4` (idx 1..4) | U8 | vetor | 5 | identidade | R | LEDs do comutador (`VectRiaccLed`) | `3 10 37 62 90` (seq 298–302) | PROVADO |
| 313 | 0x0139 | `TANK_VOL` (idx 0) / `INJR_GAS_FLOW` (idx 1) | U16 | vetor | 2 | `1000·raw/32768` L / `raw/4096` | — | Landi Connect | **`CA 01 10`** = não suportado nesta ECU (seq 315–316) | PROVADO (ausência) |

## 3.5 Tempos mortos e compensações de pressão/temperatura

| SC | Hex | Nome | Enc. | Forma | Tam. | Escala | R/W | Consumidor | Valor nesta ECU | Selo |
|---:|---|---|---|---|---:|---|---|---|---|---|
| 125 | 0x007D | `TEMPO_MORTO_INIETTORI_BENZINA` | U16 | escalar | 1 | tempo (×0,00256 ms) | R/w | `TFormConfig` injetores | `234` → 0,60 ms (seq 194) | PROVADO (valor) / INFERIDO (escala) |
| 126 | 0x007E | `TEMPO_MORTO_INIETTORI_GAS` | U16 | escalar | 1 | tempo | R/w | idem | `391` → 1,00 ms (seq 195) — valor igual a 1,00 ms | PROVADO (valor) / INFERIDO (escala e vínculo) |
| 82 / 83 | 0x0052 / 0x0053 | `TEMPO_CHIUSURA_INIETTORE` / `TEMPO_APERTURA_INIETTORE` | U16 | escalar | 1 | tempo | R | injetores de gás | `781` / `977` (seq 153–154) | PROVADO (valor) |
| 242 / 243 | 0x00F2 / 0x00F3 | `ADV_OFFSET_INJ_PETROL` / `_GAS` | S8 | vetor | 10 | identidade | R | `TStrategiaTempiMortiDM` | todos `0` (seq 396–397) | PROVADO (valor) |
| 92 / 93 | 0x005C / 0x005D | `RIF_TEMP_GAS` / `COEFF_TEMP_GAS` | U8 | vetor | 10 / 9 | raw de temperatura (vínculo com canal e tipo de sensor ainda a conferir) / **percentual, 100 = neutro** | R/w | compensação por temperatura do gás | `0 24 38 50 67 88 113 155 195 255` / `107 105 103 101 100 98 97 94 92` (seq 157–158) | PROVADO |
| 42 / 43 | 0x002A / 0x002B | `RIF_TEMP_RID` / `COEFF_TEMP_RID` | U8 | vetor | 10 / 9 | idem (100 = neutro) | R/w | compensação por temperatura do redutor | `0 38 50 58 67 77 83 88 100 255` / `100 99 97 94 91 89 86 82 80` (seq 129–130) | PROVADO |
| 95 / 96 | 0x005F / 0x0060 | `RIF_PRESS_COLL` / `COEFF_PRESS_COLL` | U16 / S16 | vetor | 15 | mbar de MAP / coeficiente | R | compensação por pressão do coletor | `299 400 500 599 699 800 900 1000 1100 1199 1300 1399 1500 1600 1699` / todos `0` (seq 159–160) | PROVADO (valor; eixo em mbar bate com MAP/1000) |
| 123 / 124 | 0x007B / 0x007C | `RIF_PRESS_DIFF` / `COEFF_PRESS_DIFF` | U16 / S16 | vetor | 15 | mbar de ΔP / coeficiente com sinal | R | compensação por pressão diferencial do gás | `1250 1324 1399 1475 1550 1625 1699 1774 1850 1925 2000 2083 2167 2251 2335` / `2617 2325 2041 1765 1495 1232 975 724 477 236 0 -259 -513 -762 -1007` (seq 191–193) | PROVADO (valor) / DESCONHECIDO (escala do coeficiente: `/8192` ou `/10000` são candidatos; zero em 2000 mbar) |
| 223 / 224 | 0x00DF / 0x00E0 | `RIF_PRESS_ASS` / `COEFF_PRESS_ASS` | U8 / S16 | vetor | 15 | raw / coeficiente | R | compensação por pressão absoluta | `58 61 65 68 70 72 73 76 78 80 81 82 83 84 85` / `0 140 270 459 621 819 1050 1319 1650 2039 2260 2500 2780 3100 3420` (seq 266–267) | PROVADO (valor) / DESCONHECIDO (unidades) |
| 134 | 0x0086 | `TIPO_SENSORE_TEMPERATURA` | U8 | escalar | 1 | enum | R | `TFormConfig` | `0` | PROVADO |
| 59 / 85 | 0x003B / 0x0055 | `TEMPO_GAS` / `TEMPO_GAS_PARZIALE` | U16 | escalar | 1 | horas? (unidade não provada) | R | `TFormDiagnosi` "Tempo a gas" / `TFormService` | `1252` / `2703` | PROVADO (valor) / DESCONHECIDO (unidade) |

## 3.6 Não suportados por esta ECU (resposta `CA 01 10` a toda leitura)

SC 181 `TIPO_CARBURANTE`, 313 `TANK_VOL`/`INJR_GAS_FLOW`, 314 `NORM_TEMP`, 315 `NORM_PRESS`, 316/317/318 `INJR_TOFS_*`, 356 `VECT_AUTOCAL_EE`, 400 `PREHEAT_SYNC_INJ_NUM`, 44 `MAPPA_ANTICIPO`, e os SC 291–299 (`PRESS_INSUL_DIAG_*`, `PARAM_PROGRESS_*`) nos índices lidos. `PROVADO` (LN). `MGLEV_*` 325–328 e `DHLP_TANK_PRESS` 321 nem sequer são lidos pelo ProgBase nesta ECU. Limite: CA prova rejeição dos endereços/índices consultados nesta sessão. A ausência de consultas a MGLEV não prova ausência no hardware ou firmware (`MODELLO_HARDWARE` 4, `MODULE_VERSION` 4).

## 3.7 Ignorados de propósito (só nomes)

Lambda: SC 4–11, 103, 111/112, 130–133, 167/171, 176. Comutação/partida/cutoff: 6, 13–17, 50/51, 54, 56, 60, 62, 73, 78, 100, 106–116, 135–137, 160–163, 178/179, 203–215, 221, 226, 268. Avanço/anticipo: 38/39, 44/45, 238. Diagnóstico e estados: 1, 18–20, 23–25, 28–35, 49, 57/58, 81, 97, 139, 153/154, 377. Serviço/tagliandi/licença: 30, 51, 64–66, 88, 237. Mapas de carburação e adaptatividade: 46/47, 113–116, 120, 127, 129, 131, 140–152, 158, 170, 173/174, 180, 182, 185, 189, 194, 198–201, 216, 230–236, 239–241, 248–250, 299, 301, 303, 306–308, 312. OBD/Landi Connect/MGLEV/DHLP: 116, 187, 190, 269, 291–295, 319–329, 373. Perfis `*_LR` (família Landi Renzo antiga, mesma SC com semântica diferente): todos. Calibração clássica `TFormCalibra` (`00 13`/`00 14`): fora do produto.

## 3.8 Revalidação dirigida nesta continuação

Inventário reextraído de quatro DFM: 364 componentes, propriedades explícitas e offsets; não assumir defaults quando omitidos. O vínculo SC121→cache→escala de Timer1 está fechado, mas isso não promove automaticamente as escalas de eixos, tempos mortos ou outros consumidores. Pressão de apresentação e tensão dos injetores foram reabertas em telemetria.md; os coeficientes SC123/124/223/224 continuam sem unidade física fechada (L-09).
