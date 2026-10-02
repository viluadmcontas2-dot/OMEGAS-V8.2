# ProgBase: catálogo de parâmetros e funções

Estado: levantamento estrutural, 2026-10-02. **CONFIRMADO** significa nome, SC e forma extraídos diretamente do DFM, NÃO demonstra uso efetivo em transação ECU. Fórmulas e direção de acesso não verificadas. DFM usa SerialCode em propriedades de TAeb*; o vínculo protocolo é corroborado pelo app para SC 330 = 0x014A.

## NIVEL (13 componentes)

Referências do sensor, limites, LEDs, volume e filtros. Consumo e reconhecimento de cheio precisam de provas dinâmicas.

| SC | Componente | Forma DFM | Fonte |
|---:|---|---|---|
| 36 | TIPO_SENSORE | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x25B8 |
| 37 | RIF_SENSORE | tipo não expresso, 4 elem. | TSTREAMDATI @ 0x2672 |
| 71 | LIVELLO_EMUL_ALTO | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x41A2 |
| 72 | LIVELLO_EMUL_BASSO | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x4262 |
| 134 | TIPO_SENSORE_TEMPERATURA | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x844C |
| 276 | LO_PASS_FILT_CON_FAST | U16, 1 elem. | TSTREAMDATI @ 0x16F1B |
| 276 | LO_PASS_FILT_CON_SLOW | U16, 1 elem. | TSTREAMDATI @ 0x1709A |
| 300 | ISTERESI_RIACCENSIONE | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x17224 |
| 300 | SOGLIA_LED_1 | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x17376 |
| 300 | SOGLIA_LED_2 | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x174CA |
| 300 | SOGLIA_LED_3 | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x1761E |
| 300 | SOGLIA_LED_4 | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x17772 |
| 313 | TANK_VOL | U16, 1 elem. | TSTREAMDATI @ 0x18FD7 |

## INJETOR (95 componentes)

Parâmetros de injetores, temperaturas, pressões e offsets. Um nome de componente não prova a fórmula interna da ECU.

| SC | Componente | Forma DFM | Fonte |
|---:|---|---|---|
| 15 | TEMP_GAS_CAMBIO_LR | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x11F0D |
| 22 | TAGLIA_INIETTORE_LR | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x11FCD |
| 32 | DIAGNOSI_INJ_GAS | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x22BC |
| 33 | DIAGNOSI_INJ_BENZ | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x237A |
| 34 | DIAGNOSI_INJ_GAS2 | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x2439 |
| 35 | DIAGNOSI_INJ_BENZ2 | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x24F8 |
| 42 | RIF_TEMP_GAS_LR | tipo não expresso, 10 elem. | TSTREAMDATI @ 0x12158 |
| 43 | COEFF_TEMP_GAS_LR | tipo não expresso, 9 elem. | TSTREAMDATI @ 0x122A6 |
| 43 | COEFF_TEMP_RID | tipo não expresso, 9 elem. | TSTREAMDATI @ 0x2B3D |
| 46 | MAPPA_DELAY_GAS_TEMP | tipo não expresso, 18 elem. | TSTREAMDATI @ 0x3028 |
| 77 | DIAGNOSI_INJ_GAS_LR | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x128FB |
| 79 | PARAM_INJ | U16, 1 elem. | TSTREAMDATI @ 0x4762 |
| 82 | TEMPO_CHIUSURA_INIETTORE | U16, 1 elem. | TSTREAMDATI @ 0x4933 |
| 83 | TEMPO_APERTURA_INIETTORE | U16, 1 elem. | TSTREAMDATI @ 0x4A09 |
| 92 | RIF_TEMP_GAS | tipo não expresso, 10 elem. | TSTREAMDATI @ 0x55EE |
| 93 | COEFF_TEMP_GAS | tipo não expresso, 9 elem. | TSTREAMDATI @ 0x5738 |
| 93 | COEFF_TEMP_RID_LR | tipo não expresso, 9 elem. | TSTREAMDATI @ 0x12B0B |
| 94 | DIAGNOSI_INJ_BENZ_LR | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x12C50 |
| 95 | RIF_PRESS_COLL | U16, 15 elem. | TSTREAMDATI @ 0x5879 |
| 96 | COEFF_PRESS_COLL | S16, 15 elem. | TSTREAMDATI @ 0x5A0B |
| 103 | PRESS_RETROPASSAGGIO_LR | tipo não expresso, 12 elem. | TSTREAMDATI @ 0x12D13 |
| 104 | RIF_PRESS_ASS_LR | tipo não expresso, 15 elem. | TSTREAMDATI @ 0x12E7F |
| 105 | COEFF_PRESS_ASS_LR | S16, 15 elem. | TSTREAMDATI @ 0x13005 |
| 106 | NUMERO_INJ_BENZINA_CUTOFF | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x6175 |
| 107 | TAGLIA_INIETTORI_LR | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x1326C |
| 117 | TEMP_GAS_CAMBIO | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x72D7 |
| 118 | IMPEDENZA_INIETTORI | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x7395 |
| 123 | RIF_PRESS_DIFF | U16, 15 elem. | TSTREAMDATI @ 0x7723 |
| 124 | COEFF_PRESS_DIFF | S16, 15 elem. | TSTREAMDATI @ 0x78B6 |
| 125 | TEMPO_MORTO_INIETTORI_BENZINA | U16, 1 elem. | TSTREAMDATI @ 0x7A53 |
| 126 | TEMPO_MORTO_INIETTORI_GAS | U16, 1 elem. | TSTREAMDATI @ 0x7B2E |
| 137 | NUMERO_INIETTATE_SMAGRIMENTO | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x86A2 |
| 143 | TINJ_3000RPM | U16, 16 elem. | TSTREAMDATI @ 0x8B88 |
| 146 | T_INJ_BENZ_MAX_CAMBIO | U16, 1 elem. | TSTREAMDATI @ 0x8EF3 |
| 148 | TIPO_INIETTORE | tipo não expresso, 2 elem. | TSTREAMDATI @ 0x909A |
| 152 | PRESS_BASSA_RETROPAS_LR | U16, 1 elem. | TSTREAMDATI @ 0x14121 |
| 152 | TINJ_FILTRO | U16, 8 elem. | TSTREAMDATI @ 0x9502 |
| 155 | TEMPI_EXTRAINIETTATE | U16, 3 elem. | TSTREAMDATI @ 0x9903 |
| 156 | TEMPO_MAX_EXTRAINJ_BENZ | U16, 1 elem. | TSTREAMDATI @ 0x9A18 |
| 157 | TEMPO_MAX_INJ_BENZ_LR | U16, 1 elem. | TSTREAMDATI @ 0x141F7 |
| 160 | SEQUENZA_INJ_LR | tipo não expresso, 16 elem. | TSTREAMDATI @ 0x14558 |
| 161 | SEQUENZA_INJ_BENZ_LR | tipo não expresso, 8 elem. | TSTREAMDATI @ 0x146E9 |
| 163 | INIETTATE_PER_BENZINA | tipo não expresso, 2 elem. | TSTREAMDATI @ 0x9E82 |
| 171 | RIF_TEMP_GAS_OFFSET_LR | tipo não expresso, 16 elem. | TSTREAMDATI @ 0x14EB7 |
| 172 | MASK_INIETTORI_BENZINA | U16, 1 elem. | TSTREAMDATI @ 0xA13D |
| 172 | TEMPO_MORTO_INIETTORI_LR | S16, 16 elem. | TSTREAMDATI @ 0x1504F |
| 176 | LAMBDA_OFFSET | tipo não expresso, 1 elem. | TSTREAMDATI @ 0xA3B6 |
| 178 | MASK_INIETTORI_BENZINA_LR | U16, 1 elem. | TSTREAMDATI @ 0x156A1 |
| 189 | PRESS_RETROPASSAGGIO | U16, 12 elem. | TSTREAMDATI @ 0xAAFA |
| 195 | TEMP_GAS_CALDO | tipo não expresso, 1 elem. | TSTREAMDATI @ 0xB1E1 |
| 196 | RIF_PRESS_SPLIT_FUEL | tipo não expresso, 10 elem. | TSTREAMDATI @ 0xB29F |
| 197 | COEFF_PRESS_SPLIT_FUEL | tipo não expresso, 10 elem. | TSTREAMDATI @ 0xB3F3 |
| 201 | OVER_PRESSURE_DIAGNOSYS | U16, 1 elem. | TSTREAMDATI @ 0xB735 |
| 204 | PETROL_RPM_MAP_INJ | U16, 324 elem. | TSTREAMDATI @ 0xB9BC |
| 207 | RITARDO_ATTIVAZIONE_INIETTORI | U16, 1 elem. | TSTREAMDATI @ 0xD8DA |
| 219 | ADVANCED_PARAM_INJ | U16, 6 elem. | TSTREAMDATI @ 0xF7AA |
| 223 | RIF_PRESS_ASS | tipo não expresso, 15 elem. | TSTREAMDATI @ 0xFC2C |
| 224 | COEFF_PRESS_ASS | S16, 15 elem. | TSTREAMDATI @ 0xFDB0 |
| 225 | TAGLIA_INIETTORE | tipo não expresso, 1 elem. | TSTREAMDATI @ 0xFF4D |
| 232 | ADVANCED_PRESS_BACK | S16, 2 elem. | TSTREAMDATI @ 0x10500 |
| 241 | ADV_PARAM_INJ | U16, 12 elem. | TSTREAMDATI @ 0x11732 |
| 242 | ADV_OFFSET_INJ_PETROL | tipo não expresso, 10 elem. | TSTRATEGIATEMPIMORTIDM @ 0x55 |
| 243 | ADV_OFFSET_INJ_GAS | tipo não expresso, 10 elem. | TSTRATEGIATEMPIMORTIDM @ 0x1E8 |
| 291 | PRESS_INSUL_DIAG_MIN_TANK_LVL_THD | S16, 1 elem. | TSTREAMDATI @ 0x16768 |
| 292 | LO_PRESS_INSUL_DIAG_FALL_THD | S16, 1 elem. | TSTREAMDATI @ 0x16437 |
| 293 | PRESS_INSUL_DIAG_AFT_CRK_DLY | U16, 1 elem. | TSTREAMDATI @ 0x165DF |
| 293 | PRESS_INSUL_DIAG_PRESS_ZNT | U16, 1 elem. | TSTREAMDATI @ 0x16912 |
| 293 | PRESS_INSUL_DIAG_PRESS_ZNT_OUT | U16, 1 elem. | TSTREAMDATI @ 0x16A99 |
| 295 | PRESS_INSUL_DIAG_WAT_TEMP_HI_THD | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x16C24 |
| 295 | PRESS_INSUL_DIAG_WAT_TEMP_LO_THD | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x16D9A |
| 301 | VH34_PARAM_INJ | tipo não expresso, 20 elem. | TSTREAMDATI @ 0x17D3E |
| 306 | WARMUP_PARAM_INJ | tipo não expresso, 8 elem. | TSTREAMDATI @ 0x17EFA |
| 313 | INJR_GAS_FLOW | U16, 1 elem. | TSTREAMDATI @ 0x1981B |
| 315 | NORM_PRESS | S16, 1 elem. | TSTREAMDATI @ 0x19699 |
| 316 | INJR_TOFS_PTR_H | S16, 4 elem. | TSTREAMDATI @ 0x19970 |
| 317 | INJR_TOFS_PTR_V | S16, 5 elem. | TSTREAMDATI @ 0x19B30 |
| 318 | INJR_TOFS_TBL | S16, 20 elem. | TSTREAMDATI @ 0x19CFB |
| 320 | MGSS_MAX_MNFLD_PRESS | S16, 1 elem. | TSTREAMDATI @ 0x193BC |
| 321 | DHLP_TANK_PRESS | S16, 1 elem. | TSTREAMDATI @ 0x1885D |
| 326 | MGLEV_TANK_PRESS | S16, 1 elem. | TSTREAMDATI @ 0x18DEC |
| 332 | MNFLD_PRESS_THD | S16, 18 elem. | TAUTOCALDM @ 0x3D |
| 349 | PETR_INJ_TBUF_GAS_PREV | U16, 18 elem. | TAUTOCALDM @ 0x102D |
| 351 | PETR_INJ_TBUF_GAS | U16, 18 elem. | TAUTOCALDM @ 0x1459 |
| 354 | PETR_INJ_TBUF | U16, 18 elem. | TSTREAMDATI @ 0x1A088 |
| 354 | PETR_INJ_TBUF | U16, 18 elem. | TAUTOCALDM @ 0xA22 |
| 361 | LIMIT_PRESSURE_MIN | S16, 1 elem. | TAUTOCALDM @ 0x1C95 |
| 362 | LIMIT_PRESSURE_MAX | S16, 1 elem. | TAUTOCALDM @ 0x212D |
| 389 | DIFF_MNFLD_PRESS_THD | U16, 1 elem. | TAUTOCALDM @ 0x347A |
| 390 | DELTA_MNFLD_PRESS_THD | U16, 1 elem. | TAUTOCALDM @ 0x30A2 |
| 391 | DIFF_PETR_TINJ_T_THD | U16, 1 elem. | TAUTOCALDM @ 0x35D0 |
| 392 | DELTA_PETR_INJ_T_THD | U16, 1 elem. | TAUTOCALDM @ 0x3200 |
| 397 | PETR_MNFLD_PRESS_RV | S16, 18 elem. | TAUTOCALDM @ 0x2B8C |
| 398 | GAS_MNFLD_PRESS_RV | S16, 18 elem. | TAUTOCALDM @ 0x2D99 |
| 400 | PREHEAT_SYNC_INJ_NUM | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x11DE |
| 414 | PARAMETRI_EXTRA_INJ | U16, 5 elem. | TSTREAMDATI @ 0x1A873 |

## COMUTACAO (41 componentes)

Comutação e retornos a gasolina, atraso, cutoff e partida; condições lógicas carecem de código ou captura.

| SC | Componente | Forma DFM | Fonte |
|---:|---|---|---|
| 6 | RITARDO_SONDA | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x793 |
| 13 | DIAGNOSI_SWITCHON_LR | tipo não expresso, 5 elem. | TSTREAMDATI @ 0x11DF2 |
| 14 | SEQUENZA_INIEZIONE_BENZINA | tipo não expresso, 8 elem. | TSTREAMDATI @ 0xF13 |
| 15 | TEMP_RID_CAMBIO | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x1055 |
| 16 | GIRI_MIN_CAMBIO | U16, 1 elem. | TSTREAMDATI @ 0x1112 |
| 17 | RITARDO_CAMBIO | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x12A1 |
| 50 | GIRI_CUTOFF_LR | U16, 1 elem. | TSTREAMDATI @ 0x12531 |
| 50 | GIRI_TEMPO_CUTOFF | U16, 2 elem. | TSTREAMDATI @ 0x3319 |
| 51 | TEMPO_CUTOFF_LR | U16, 1 elem. | TSTREAMDATI @ 0x125FD |
| 60 | TEMPO_BENZINA | U16, 1 elem. | TSTREAMDATI @ 0x3B9C |
| 62 | TEMPO_RITORNO_BENZINA | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x3DDD |
| 73 | TEMPO_CORRENTE_CUTOFF | U16, 1 elem. | TSTREAMDATI @ 0x4323 |
| 78 | AVVIAMENTI_EMERGENZA | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x469F |
| 100 | GIRI_SUP_BENZINA | U16, 1 elem. | TSTREAMDATI @ 0x5CAD |
| 107 | RITARDO_GIRI_EMULAZIONE_HIGH | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x623D |
| 111 | RITARDO_PASSAGGIO_GAS_LR | tipo não expresso, 8 elem. | TSTREAMDATI @ 0x13539 |
| 112 | RIF_GIRI_BENZINA_LR | U16, 8 elem. | TSTREAMDATI @ 0x1367A |
| 113 | MAPPA_FILTRO_BENZINA | tipo não expresso, 144 elem. | TSTREAMDATI @ 0x681B |
| 114 | MAP_BENZINA_LR | U16, 64 elem. | TSTREAMDATI @ 0x138D0 |
| 114 | MAP_FILTRO_BENZINA | U16, 12 elem. | TSTREAMDATI @ 0x6F2F |
| 115 | RIF_MAP_BENZINA_LR | U16, 8 elem. | TSTREAMDATI @ 0x13C7E |
| 115 | SGANCIO_FILTRO_BENZINA | tipo não expresso, 12 elem. | TSTREAMDATI @ 0x70A4 |
| 117 | TEMP_RID_CAMBIO_LR | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x13E8E |
| 135 | RITARDO_GIRI_EMULAZIONE | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x8513 |
| 136 | SMAGRIMENTO_RIENTRO_CUTOFF | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x85D9 |
| 159 | RITARDO_PASSAGGIO_LR | tipo não expresso, 8 elem. | TSTREAMDATI @ 0x1441A |
| 160 | TEMPI_PER_BENZINA | U16, 4 elem. | TSTREAMDATI @ 0x9C5D |
| 161 | GIRI_PER_BENZINA | U16, 2 elem. | TSTREAMDATI @ 0x9D7B |
| 175 | TEMPI_PER_BENZINA_LR | U16, 4 elem. | TSTREAMDATI @ 0x153A4 |
| 176 | GIRI_PER_BENZINA_LR | U16, 2 elem. | TSTREAMDATI @ 0x154C5 |
| 177 | GIRI_SUP_BENZINA_LR | U16, 1 elem. | TSTREAMDATI @ 0x155CF |
| 178 | RITARDO_CAMBIO_HIGH | tipo não expresso, 1 elem. | TSTREAMDATI @ 0xA473 |
| 203 | PETROL_MAP | U16, 18 elem. | TSTREAMDATI @ 0xB80B |
| 205 | PETROL_RPM_MAP_NUM | tipo não expresso, 324 elem. | TSTREAMDATI @ 0xC89B |
| 206 | PETROL_RPM | U16, 12 elem. | TSTREAMDATI @ 0xD76B |
| 208 | MAPPA_CONTRIBUTI_BENZINA | tipo não expresso, 144 elem. | TSTREAMDATI @ 0xD9B6 |
| 215 | MAPPA_RIF_TRANSITORI_BENZINA | U16, 18 elem. | TSTREAMDATI @ 0xEB8C |
| 221 | RITARDO_PASSAGGIO | tipo não expresso, 8 elem. | TSTREAMDATI @ 0xFA2A |
| 236 | SWITCH_TO_PETROL_PARAM_LR | U16, 9 elem. | TSTREAMDATI @ 0x15DE8 |
| 268 | TAGLIO_POMPA_BENZINA | U16, 5 elem. | TSTREAMDATI @ 0x11B8B |
| 365 | PETROL_POINT_2DELETE | tipo não expresso, 18 elem. | TAUTOCALDM @ 0x1F8A |

## MAPA (45 componentes)

Mapas e respectivos eixos; verificar orientação, tipo e escala antes de editar.

| SC | Componente | Forma DFM | Fonte |
|---:|---|---|---|
| 12 | RIF_GIRI | U16, 12 elem. | TSTREAMDATI @ 0xC16 |
| 22 | SMAGRIMENTO_EXTRA | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x169E |
| 38 | GIRI_ANTICIPO | U16, 8 elem. | TSTREAMDATI @ 0x2779 |
| 39 | COEFF_ANTICIPO | tipo não expresso, 8 elem. | TSTREAMDATI @ 0x28BD |
| 44 | MAPPA_ANTICIPO | tipo não expresso, 40 elem. | TSTREAMDATI @ 0x2C7E |
| 45 | MAP_ANTICIPO | tipo não expresso, 5 elem. | TSTREAMDATI @ 0x2F14 |
| 55 | TEMPI_PER_K | S16, 12 elem. | TSTREAMDATI @ 0x389A |
| 61 | GIRI_PER_K | S16, 12 elem. | TSTREAMDATI @ 0x3C67 |
| 84 | MAP_K | tipo não expresso, 156 elem. | TSTREAMDATI @ 0x4ADF |
| 103 | MAP_DELAY_LAMBDA | U16, 48 elem. | TSTREAMDATI @ 0x5D7B |
| 111 | RIF_MAP_SONDA_LAMBDA | U16, 4 elem. | TSTREAMDATI @ 0x648A |
| 112 | MAP_RIF_SONDA_LAMBDA | tipo não expresso, 36 elem. | TSTREAMDATI @ 0x65AA |
| 113 | ADATTA_PARAMETRI_K_LR | U16, 2 elem. | TSTREAMDATI @ 0x137C5 |
| 116 | CORR_ARRICCHIMENTO | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x720E |
| 120 | MAP_ESTERNO | S16, 6 elem. | TSTREAMDATI @ 0x751E |
| 122 | K_MAPPA_NEUTRO_LR | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x13F4F |
| 127 | TEMPI_K_OPENLOOP | U16, 12 elem. | TSTREAMDATI @ 0x7C05 |
| 129 | K_FILTRO | tipo não expresso, 3 elem. | TSTREAMDATI @ 0x7E50 |
| 131 | RIT_EMUL_RICCA | U16, 12 elem. | TSTREAMDATI @ 0x80B1 |
| 140 | PARAM_MAP_ESTERNO_LR | tipo não expresso, 4 elem. | TSTREAMDATI @ 0x1400F |
| 141 | GIRI_INTERVENTO_K_FILTRO | U16, 1 elem. | TSTREAMDATI @ 0x8AB1 |
| 149 | MAPPA_CORR_TARATURA | tipo não expresso, 16 elem. | TSTREAMDATI @ 0x9190 |
| 150 | GIRI_AUTOTARATURA | U16, 1 elem. | TSTREAMDATI @ 0x932D |
| 163 | RIF_MAP_ANTICIPO_LR | tipo não expresso, 5 elem. | TSTREAMDATI @ 0x148F2 |
| 164 | RIF_GIRI_ANTICIPO_LR | U16, 8 elem. | TSTREAMDATI @ 0x14A0D |
| 165 | MAPPA_ANTICIPO_LR | tipo não expresso, 40 elem. | TSTREAMDATI @ 0x14B59 |
| 170 | EMULAZIONE_POSTERIORE | tipo não expresso, 2 elem. | TSTREAMDATI @ 0x9F7F |
| 173 | GIRI_ALTI_INTERVENTO_K_FILTRO_LR | U16, 1 elem. | TSTREAMDATI @ 0x15200 |
| 174 | K_FILTRO_GIRI_ALTI_LR | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x152DF |
| 180 | SMAGRIMENTO_MIN | tipo não expresso, 1 elem. | TSTREAMDATI @ 0xA638 |
| 188 | K_MAPPA_NEUTRO | tipo não expresso, 1 elem. | TSTREAMDATI @ 0xAA3D |
| 194 | MASK_FUNCTION | tipo não expresso, 30 elem. | TSTREAMDATI @ 0xAFB8 |
| 198 | K_FACTOR_PARAM | U16, 4 elem. | TSTREAMDATI @ 0xB549 |
| 200 | RPM_FOR_SPLIT_FUEL | U16, 1 elem. | TSTREAMDATI @ 0xB664 |
| 209 | MAPPA_TRANSITORI_POSITIVI | tipo não expresso, 36 elem. | TSTREAMDATI @ 0xE0CF |
| 210 | MAPPA_TRANSITORI_NEGATIVI | tipo não expresso, 36 elem. | TSTREAMDATI @ 0xE345 |
| 211 | MAPPA_CORREZIONI_ACCELERAZIONE | tipo não expresso, 36 elem. | TSTREAMDATI @ 0xE5BB |
| 212 | MAPPA_CORREZIONI_DECELERAZIONE | tipo não expresso, 36 elem. | TSTREAMDATI @ 0xE83F |
| 214 | SOGLIA_LETTURA_GIRI_LR | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x15978 |
| 216 | MAPPA_DIFFERENZE_K | tipo não expresso, 156 elem. | TSTREAMDATI @ 0xED4F |
| 229 | FREST_COEFFICIENT_LR | U16, 6 elem. | TSTREAMDATI @ 0x15CB1 |
| 234 | RIGHE_MAPPAK_CALIBRATE | tipo não expresso, 12 elem. | TSTREAMDATI @ 0x1075F |
| 235 | MAPPA_ADATTA | tipo não expresso, 72 elem. | TSTREAMDATI @ 0x108CB |
| 239 | SOGLIA_GIRI_ADATTATIVITA_WORD | U16, 1 elem. | TSTREAMDATI @ 0x10EC8 |
| 240 | MAP_FLEX | tipo não expresso, 156 elem. | TSTREAMDATI @ 0x10FA4 |

## AUTOCAL (17 componentes)

Referências e buffers da aquisição/AutoMatch; não substituir a semântica demonstrada pelos logs reais.

| SC | Componente | Forma DFM | Fonte |
|---:|---|---|---|
| 217 | AUTO_CALIBR_BYTE_ARRAY | tipo não expresso, 10 elem. | TSTREAMDATI @ 0xF4EF |
| 218 | AUTO_CALIBR_WORD_ARRAY | U16, 10 elem. | TSTREAMDATI @ 0xF645 |
| 330 | AUTO_CAL_ENABLE | tipo não expresso, 1 elem. | TAUTOCALDM @ 0x49F |
| 331 | PETR_INJ_TBP | U16, 18 elem. | TAUTOCALDM @ 0x275 |
| 347 | NUM_BUF_UPD_PETR | U16, 18 elem. | TAUTOCALDM @ 0xE3C |
| 348 | NUM_BUF_UPD_GAS | U16, 18 elem. | TAUTOCALDM @ 0x1665 |
| 350 | MNFLD_PRESS_BUF_GAS_PREV | S16, 18 elem. | TAUTOCALDM @ 0x123E |
| 352 | MNFLD_PRESS_BUF_GAS | S16, 18 elem. | TAUTOCALDM @ 0x1A7E |
| 353 | MUL_ACT | U16, 18 elem. | TAUTOCALDM @ 0x1855 |
| 355 | MNFLD_PRESS_BUF | S16, 18 elem. | TAUTOCALDM @ 0xC2A |
| 357 | VECT_AUTOCAL_U8_0 | tipo não expresso, 1 elem. | TAUTOCALDM @ 0x278E |
| 357 | VECT_AUTOCAL_U8_1 | tipo não expresso, 1 elem. | TAUTOCALDM @ 0x5E0 |
| 357 | VECT_AUTOCAL_U8_2 | tipo não expresso, 1 elem. | TAUTOCALDM @ 0x72E |
| 367 | ACQUIRED_ZONES_PETROL | tipo não expresso, 4 elem. | TAUTOCALDM @ 0x2282 |
| 368 | ACQUIRED_ZONES_GAS | tipo não expresso, 4 elem. | TAUTOCALDM @ 0x23BB |
| 370 | CALIBRATION_VAL_1 | tipo não expresso, 10 elem. | TAUTOCALDM @ 0x24F1 |
| 378 | MAX_RPM_FOR_AUTOCAL | U16, 1 elem. | TAUTOCALDM @ 0x2A3D |

## TELEMETRIA (2 componentes)

Itens de tempo expostos no modelo de dados; mapeamento do pacote contínuo deve ser confirmado separadamente.

| SC | Componente | Forma DFM | Fonte |
|---:|---|---|---|
| 59 | TEMPO_GAS | U16, 1 elem. | TSTREAMDATI @ 0x3AD5 |
| 85 | TEMPO_GAS_PARZIALE | U16, 1 elem. | TSTREAMDATI @ 0x5269 |

## DIAGNOSTICO (12 componentes)

Parâmetros de estado/habilitação e ações; leitura passiva, sem acionar diagnóstico.

| SC | Componente | Forma DFM | Fonte |
|---:|---|---|---|
| 28 | ABIL_DIAGNOSI | tipo não expresso, 5 elem. | TSTREAMDATI @ 0x1D27 |
| 29 | STATO_DIAGNOSI | tipo não expresso, 5 elem. | TSTREAMDATI @ 0x1F53 |
| 31 | ACTION_DIAGNOSI | tipo não expresso, 15 elem. | TSTREAMDATI @ 0x2138 |
| 49 | ACTION_DIAGNOSI_LR | tipo não expresso, 9 elem. | TSTREAMDATI @ 0x123EB |
| 57 | STATO_DIAGNOSI_LR | tipo não expresso, 5 elem. | TSTREAMDATI @ 0x126CA |
| 58 | ABIL_DIAGNOSI_LR | tipo não expresso, 5 elem. | TSTREAMDATI @ 0x127E3 |
| 81 | TEMP_DIAGNOSI | tipo não expresso, 4 elem. | TSTREAMDATI @ 0x4829 |
| 97 | TEMPO_DA_ERRORE | U16, 2 elem. | TSTREAMDATI @ 0x5BA8 |
| 303 | DELTA_T_RAIL_BLOCCO_DHLP | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x17BCD |
| 325 | MGLEV_DIAG_ERR | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x18D2E |
| 327 | MGLEV_DATA | S16, 3 elem. | TSTREAMDATI @ 0x18EC3 |
| 328 | MGLEV_ERR_ST | tipo não expresso, 3 elem. | TSTREAMDATI @ 0x1875E |

## OUTRO (119 componentes)

Identificação, configuração, versões e parâmetros restantes.

| SC | Componente | Forma DFM | Fonte |
|---:|---|---|---|
| 1 | REGISTRO_INIT | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x3B1 |
| 2 | REGISTRO_EE | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x46B |
| 3 | FLAG_CONF1 | U16, 2 elem. | TSTREAMDATI @ 0x523 |
| 4 | TIPO_LAMBDA | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x622 |
| 5 | RIF_LAMBDA | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x6DB |
| 7 | TEMPO_LBD_FREDDA | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x84E |
| 8 | RIF_SUP_LAMBDA_FREDDA | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x90C |
| 9 | RIF_INF_LAMBDA_FREDDA | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x9CF |
| 10 | RIF_SUP_LAMBDA_CALDA | tipo não expresso, 1 elem. | TSTREAMDATI @ 0xA92 |
| 11 | RIF_INF_LAMBDA_CALDA | tipo não expresso, 1 elem. | TSTREAMDATI @ 0xB54 |
| 13 | SEQUENZA_INIEZIONE | tipo não expresso, 16 elem. | TSTREAMDATI @ 0xD81 |
| 18 | TEST_WORD | U16, 1 elem. | TSTREAMDATI @ 0x135D |
| 19 | CS_PCB_WORD_ARRAY | U16, 2 elem. | TSTREAMDATI @ 0x1423 |
| 20 | TIPO_ACCENS | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x1529 |
| 21 | MODELLO_HARDWARE | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x15E1 |
| 23 | TEST_BYTE | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x175C |
| 24 | TEST_TEMPO | U16, 1 elem. | TSTREAMDATI @ 0x1813 |
| 25 | TEST_BYTE_ARRAY | tipo não expresso, 10 elem. | TSTREAMDATI @ 0x18DA |
| 27 | NOTE_CONFIG | tipo não expresso, 48 elem. | TSTREAMDATI @ 0x1A27 |
| 30 | DATA_ULTIMO_SCARICO | U16, 1 elem. | TSTREAMDATI @ 0x2068 |
| 31 | TEMPO_CICCHETTO_LR | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x12097 |
| 32 | PARAM_VARI_LR | U16, 10 elem. | TSTREAMDATI @ 0x185C4 |
| 42 | RIF_TEMP_RID | tipo não expresso, 10 elem. | TSTREAMDATI @ 0x29F3 |
| 47 | RIF_DELAY_GAS_TEMP | tipo não expresso, 9 elem. | TSTREAMDATI @ 0x31D3 |
| 51 | SUB_CLIENT_CODE | U16, 1 elem. | TSTREAMDATI @ 0x3420 |
| 52 | TIPO_INIEZIONE | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x34ED |
| 53 | IDENTIFICATIVO | tipo não expresso, 30 elem. | TSTREAMDATI @ 0x35AA |
| 54 | TEMPO_INIEZIONE_CONTINUA | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x37D3 |
| 56 | CORRENTE_MANTENIMENTO | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x3A11 |
| 64 | CODICE_CLIENTE | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x3EA1 |
| 65 | CODICE_MODELLO | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x3F5D |
| 66 | CODICE_PERSONALIZZAZIONE | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x4019 |
| 70 | SOGLIA_RICCO_FORZATO | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x40DF |
| 74 | TEMPO_MAX_CORRENTE | U16, 2 elem. | TSTREAMDATI @ 0x43F6 |
| 75 | CILINDRATA | U16, 1 elem. | TSTREAMDATI @ 0x44FE |
| 76 | MINIMA_VERSIONE_INTERFACCIA | U16, 1 elem. | TSTREAMDATI @ 0x45C6 |
| 86 | TEMPI_SECONDI | U16, 3 elem. | TSTREAMDATI @ 0x5339 |
| 88 | TEMPO_TAGLIANDI | U16, 1 elem. | TSTREAMDATI @ 0x5447 |
| 91 | WARNING_VERSIONE_INTERFACCIA | U16, 1 elem. | TSTREAMDATI @ 0x5514 |
| 92 | RIF_TEMP_RID_LR | tipo não expresso, 10 elem. | TSTREAMDATI @ 0x129BD |
| 104 | TEMP_ACQUA_MONOFUEL | tipo não expresso, 2 elem. | TSTREAMDATI @ 0x607B |
| 106 | SOGLIA_FLUSSO_SUBSONICO_LR | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x131A4 |
| 108 | LITRI_SERBATOIO_LR | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x1332D |
| 109 | TEMPO_SOVRAPPOSIZIONE | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x6308 |
| 110 | TEMPERATURA_GAS_AVVIO_LR | tipo não expresso, 9 elem. | TSTREAMDATI @ 0x133ED |
| 110 | TEMPO_CICCHETTO | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x63CC |
| 116 | TIPO_CONNESSIONE_OBD_LR | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x13DC8 |
| 119 | VAL_PERC_HOLDING_CURRENT | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x7457 |
| 121 | BASE_TEMPI_GLOBALE | U16, 1 elem. | TSTREAMDATI @ 0x7653 |
| 128 | TEMPO_FUORI_STABILIZZATO | U16, 1 elem. | TSTREAMDATI @ 0x7D79 |
| 130 | RIF_SONDA_LAMBDA | tipo não expresso, 12 elem. | TSTREAMDATI @ 0x7F4B |
| 132 | RIF_DELAY_LAMBDA | U16, 4 elem. | TSTREAMDATI @ 0x8224 |
| 133 | CORRETTORE_INTEGRALE | U16, 2 elem. | TSTREAMDATI @ 0x8341 |
| 138 | PARAMETRI_TEMP | tipo não expresso, 5 elem. | TSTREAMDATI @ 0x876E |
| 139 | ECU_TEMP | tipo não expresso, 30 elem. | TSTREAMDATI @ 0x888D |
| 144 | TEMP_AUTOTARATURA | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x8D25 |
| 145 | SCARTO_MINIMO_TARATURA | U16, 2 elem. | TSTREAMDATI @ 0x8DE6 |
| 147 | SPOSTAMENTO_TARATURA | U16, 1 elem. | TSTREAMDATI @ 0x8FC7 |
| 151 | PARAM_AUTOTARATURA | tipo não expresso, 3 elem. | TSTREAMDATI @ 0x93FD |
| 153 | TEST_WORD_ARRAY | U16, 10 elem. | TSTREAMDATI @ 0x9646 |
| 154 | TEST_TEMPO_ARRAY | U16, 10 elem. | TSTREAMDATI @ 0x97A4 |
| 158 | CORRETTORE_BANCATA2 | tipo não expresso, 12 elem. | TSTREAMDATI @ 0x9AED |
| 158 | TEMPERATURA_ACQUA_AVVIO_LR | tipo não expresso, 9 elem. | TSTREAMDATI @ 0x142CB |
| 162 | TEMPO_INIEZIONE_CONTINUA_LR | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x14827 |
| 167 | TIPI_SONDA_LAMBDA_LR | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x14DF3 |
| 171 | TIPI_SONDA_LAMBDA | tipo não expresso, 1 elem. | TSTREAMDATI @ 0xA07C |
| 175 | CONTROL_CODE | tipo não expresso, 18 elem. | TSTREAMDATI @ 0xA212 |
| 179 | CHANGE_OVER | U16, 2 elem. | TSTREAMDATI @ 0xA536 |
| 181 | TIPO_CARBURANTE | tipo não expresso, 1 elem. | TSTREAMDATI @ 0xA6F7 |
| 182 | SMP_CALIBRATO | U16, 1 elem. | TSTREAMDATI @ 0xA7B6 |
| 185 | CONFIGURA_ADATTA | tipo não expresso, 2 elem. | TSTREAMDATI @ 0xA882 |
| 187 | CORRETTORE_BANCATA2_LR | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x1835E |
| 187 | TIPO_CONNESSIONE_OBD | tipo não expresso, 1 elem. | TSTREAMDATI @ 0xA97A |
| 189 | CONFIGURA_ADATTA_LR | tipo não expresso, 2 elem. | TSTREAMDATI @ 0x15779 |
| 190 | IDENT_OBD | tipo não expresso, 16 elem. | TSTREAMDATI @ 0xAC72 |
| 191 | SPLIT_FUEL | tipo não expresso, 1 elem. | TSTREAMDATI @ 0xADFD |
| 192 | FLAG_CONF2 | U16, 2 elem. | TSTREAMDATI @ 0xAEB7 |
| 193 | FLAG_CONF2_LR | U16, 2 elem. | TSTREAMDATI @ 0x15874 |
| 213 | NUMERO_PARTENZE_EMERGENZA | tipo não expresso, 1 elem. | TSTREAMDATI @ 0xEAC3 |
| 220 | TEMPERATURA_ACQUA_AVVIO | tipo não expresso, 9 elem. | TSTREAMDATI @ 0xF8DE |
| 222 | SOGLIA_FLUSSO_SUBSONICO | tipo não expresso, 1 elem. | TSTREAMDATI @ 0xFB65 |
| 226 | CHANGE_OVER_CILYNDER_DELAY | U16, 4 elem. | TSTREAMDATI @ 0x10015 |
| 227 | FREST_PARAMETER_LR | U16, 6 elem. | TSTREAMDATI @ 0x15A3E |
| 227 | SERVICE_DATA | tipo não expresso, 25 elem. | TSTREAMDATI @ 0x1013C |
| 228 | FREST_BREAKPOINT_LR | S16, 6 elem. | TSTREAMDATI @ 0x15B73 |
| 230 | SOGLIA_CORRETTORE_GAS | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x1032D |
| 231 | ADVANCED_TEMP_RID | tipo não expresso, 4 elem. | TSTREAMDATI @ 0x103F2 |
| 233 | SOGLIE_SESTANTI | tipo não expresso, 10 elem. | TSTREAMDATI @ 0x10611 |
| 237 | PARAMETRI_TAGLIANDI | tipo não expresso, 3 elem. | TSTREAMDATI @ 0x10CC9 |
| 238 | TEMPI_ANTICIPI_EV | tipo não expresso, 2 elem. | TSTREAMDATI @ 0x10DCF |
| 248 | FLASH_LUBE_PARAMETER | U16, 4 elem. | TSTREAMDATI @ 0x118A3 |
| 249 | NUM_DENTI_ALBERO_CAMME | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x119C4 |
| 250 | FLAG_CONF3 | U16, 2 elem. | TSTREAMDATI @ 0x11A8A |
| 269 | OBD_PARAMETER_PID | tipo não expresso, 8 elem. | TSTREAMDATI @ 0x11CB7 |
| 299 | PARAM_PROGRESS_0 | U16, 1 elem. | TSTREAMDATI @ 0x15F45 |
| 299 | PARAM_PROGRESS_1 | U16, 1 elem. | TSTREAMDATI @ 0x16079 |
| 299 | PARAM_PROGRESS_2 | U16, 1 elem. | TSTREAMDATI @ 0x161B8 |
| 299 | PARAM_PROGRESS_3 | U16, 1 elem. | TSTREAMDATI @ 0x162F7 |
| 303 | ANTICIPO_INTERRUZIONE_WARMUP | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x17A58 |
| 303 | DELTA_AD_PER_WARMUP | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x1790B |
| 307 | ANTI_STALLO | U16, 5 elem. | TSTREAMDATI @ 0x181FF |
| 308 | PARAM_PROGRESSIONI | U16, 12 elem. | TSTREAMDATI @ 0x18034 |
| 312 | PARAM_VARI | U16, 10 elem. | TSTREAMDATI @ 0x1842C |
| 314 | NORM_TEMP | S16, 1 elem. | TSTREAMDATI @ 0x19550 |
| 319 | GEAR_MAX_NO | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x19143 |
| 319 | GEAR_RAT_ADPY_EN | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x1928B |
| 322 | MDSB_INFO | U32, 14 elem. | TSTREAMDATI @ 0x18932 |
| 323 | EGEAR_RAT_BUF | U16, 7 elem. | TSTREAMDATI @ 0x18AB3 |
| 324 | MECO_INDEX | S16, 7 elem. | TSTREAMDATI @ 0x18BEE |
| 329 | EGEAR_RAT_BUF_INST | S16, 7 elem. | TSTREAMDATI @ 0x19F40 |
| 359 | EN_CDN_T_THD | U16, 1 elem. | TAUTOCALDM @ 0x8B3 |
| 366 | GAS_POINT_2DELETE | tipo não expresso, 18 elem. | TAUTOCALDM @ 0x1DEA |
| 371 | MODULE_VERSION | tipo não expresso, 1 elem. | TAUTOCALDM @ 0x2681 |
| 372 | NUM_ATUOMATCH_EXECUTED | tipo não expresso, 1 elem. | TAUTOCALDM @ 0x28EE |
| 373 | EN_LAMBDA_OVER_LVL_SENSOR | tipo não expresso, 1 elem. | TSTREAMDATI @ 0x1A27C |
| 377 | ABIL_FREEZEFRAME | tipo não expresso, 5 elem. | TSTREAMDATI @ 0x1E3B |
| 387 | DIFF_ENG_SPD_THD | U16, 1 elem. | TAUTOCALDM @ 0x3347 |
| 388 | DELTA_ENG_SPD_THD | U16, 1 elem. | TAUTOCALDM @ 0x3726 |
| 395 | DISABLE_ACQ_BAND | tipo não expresso, 1 elem. | TAUTOCALDM @ 0x2FA5 |

## Limite da inferência

O catálogo mostra estruturas Delphi, não prova equações do firmware, sequências de escrita nem conversões físicas. SCs repetidos entre perfis LR/MP48 não devem ser unificados sem identificação de família. No OMEGAS, `Mp48Protocol.kt` lê MAP_K SC 84 (13×12), TEMPI_PER_K SC 55 e GIRI_PER_K SC 61; `KFactorProtocol.kt` modela MUL_ACT SC 353 em 30 valores Q14 e eixo PETR_INJ_TBP SC 331. Essas interpretações são **corroboração pelo código do app**, não foram recalculadas por desmontagem do ProgBase nesta etapa.
