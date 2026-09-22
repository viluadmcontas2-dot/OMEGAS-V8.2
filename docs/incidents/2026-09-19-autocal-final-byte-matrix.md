# AutoCal final — mapa byte a byte

Fontes reconciliadas: `AutoCalProtocol.kt`, testes de protocolo, `byte-matrix.json` da deep audit, Portmon AutoCal e Portmon LogNovo já preparados. Escalas canônicas: injection **500 counts/ms**; MAP **1000 counts/bar**; Q14 **16384**.

`CONFIRMADO` em “byte” significa endereço/request/encoding/shape sustentados pelo contrato de código/teste; Portmon informa observação no corpus. A confiança semântica é separada para não promover nome de campo a prova.

| Campo | Endereço / request | Encoding / shape | Portmon AutoCal / LogNovo | Byte | Semântica |
|---|---|---|---:|---|---|
| AUTO_CAL_ENABLE | 0x014A · 09 4A 01 54 | U8 scalar | 0 / 3 | CONFIRMADO | CONFIRMADO para enable/readback |
| PETR_INJ_TBP | 0x014B · 29 4B 01 75 | U16LE vector, ms | 0 / 3 | CONFIRMADO | CONFIRMADO como referência temporal usada |
| MNFLD_PRESS_THD | 0x014C · 29 4C 01 76 | S16LE vector, bar | 0 / 3 | CONFIRMADO | HIPÓTESE além do uso atual |
| NUM_BUF_UPD_PETR | 0x015B · 29 5B 01 85 | U16LE vector18 | 745 / 437 | CONFIRMADO | CONFIRMADO como contador de aquisição gasolina |
| NUM_BUF_UPD_GAS | 0x015C · 29 5C 01 86 | U16LE vector18 | 747 / 440 | CONFIRMADO | CONFIRMADO como contador/maturidade GNV |
| PETR_INJ_TBUF_GAS_PREV | 0x015D · 29 5D 01 87 | U16LE vector18, ms | 744 / 437 | CONFIRMADO | CONFIRMADO pelo consumer atual |
| MNFLD_PRESS_BUF_GAS_PREV | 0x015E · 29 5E 01 88 | S16LE vector18, bar | 744 / 437 | CONFIRMADO | CONFIRMADO pelo consumer atual |
| PETR_INJ_TBUF_GAS | 0x015F · 29 5F 01 89 | U16LE vector18, ms | 747 / 440 | CONFIRMADO | CONFIRMADO pelo consumer atual |
| MNFLD_PRESS_BUF_GAS | 0x0160 · 29 60 01 8A | S16LE vector18, bar | 747 / 440 | CONFIRMADO | CONFIRMADO pelo consumer atual |
| MUL_ACT | 0x0161 · 29 61 01 8B | Q14 U16LE vector | 747 / 439 | CONFIRMADO | CONFIRMADO como fator nativo observado |
| PETR_INJ_TBUF | 0x0162 · 29 62 01 8C | U16LE vector18, ms | 745 / 437 | CONFIRMADO | CONFIRMADO pelo consumer atual |
| MNFLD_PRESS_BUF | 0x0163 · 29 63 01 8D | S16LE vector18, bar | 745 / 437 | CONFIRMADO | CONFIRMADO pelo consumer atual |
| VECT_AUTOCAL_U8_1 | 0x0165 idx1 · 0A 65 01 01 71 | U8 indexed | 0 / 3 | CONFIRMADO | DFM original: `FileKeyName=!AUTOCAL_IDLE_MIN_BUF_UPD_PETR_THD`; não interpretar o prefixo `!` além do que o binário prova |
| MAX_AUTOMATCH | 0x0165 idx2 · 0A 65 01 02 72 | U8 indexed | 0 / 3 | CONFIRMADO | CONFIRMADO no DFM original: `VECT_AUTOCAL_U8_2.FileKeyName=MaxAutomatch` |
| ACQUIRED_ZONES_PETROL | 0x016F · 29 6F 01 99 | U8 vector4 | 244 / 111 | CONFIRMADO | CONFIRMADO como quatro zonas gasolina |
| ACQUIRED_ZONES_GAS | 0x0170 · 29 70 01 9A | U8 vector4 | 501 / 325 | CONFIRMADO | CONFIRMADO como quatro zonas GNV |
| CALIBRATION_VAL_1 | 0x0172 · 29 72 01 9C | U8 vector10 | 0 / 3 | CONFIRMADO | DESCONHECIDO além do contrato observado |
| MODULE_VERSION | 0x0173 · 09 73 01 7D | U8 scalar | 0 / 0 | CONFIRMADO por código/teste | HIPÓTESE no corpus atual |
| NUM_AUTOMATCH_EXECUTED | 0x0174 · 09 74 01 7E | **U8_OR_U16_LE scalar** | 3 / 7 | CONFIRMADO | CONFIRMADO; payload histórico de 1 byte aceito |
| MAX_RPM_FOR_AUTOCAL | 0x017A · 09 7A 01 84 | U16LE scalar, rpm | 0 / 3 | CONFIRMADO | CONFIRMADO pelo contrato |
| PETR_MNFLD_PRESS_RV | 0x018D · 29 8D 01 B7 | S16LE vector, bar | 373 / 218 | CONFIRMADO | CONFIRMADO como referência MAP gasolina usada no gráfico |
| GAS_MNFLD_PRESS_RV | 0x018E · 29 8E 01 B8 | S16LE vector, bar | 373 / 220 | CONFIRMADO | CONFIRMADO como resposta MAP GNV usada no gráfico |

## Ações nativas — identidade original e observação de wire

- Enable AutoCal: `12 4A 01 01 5E`, observado no Lognovo.
- Disable AutoCal: `12 4A 01 00 5D`, observado no Lognovo.
- Manual AutoMatch: modo `0x08` → `02 24 04 08 32`, identidade provada pelo EXE; ausente nos dois Portmons fornecidos e não exposto pelo OMEGAS.
- Reset gasolina: modo `0x01` → `02 24 04 01 2B`, identidade provada pelo EXE; ausente nos dois Portmons fornecidos e atualmente intertravado no OMEGAS.
- Reset GNV: modo `0x02` → `02 24 04 02 2C`, identidade provada pelo EXE; ausente nos dois Portmons fornecidos e atualmente intertravado no OMEGAS.
- Reset all: modo `0x04` → `02 24 04 04 2E`, provado pelo EXE e observado uma vez em cada Portmon; não exposto pelo OMEGAS.
- `Modify map refs` é a ação separada `ActionAutoCalRifExecute`; este corpus não autoriza atribuir a ela o modo `0x08`.

O EXE prova identidade/mode; Portmon prova apenas o tráfego efetivamente capturado. A ausência de `0x01/0x02/0x08` nos captures significa que o efeito físico seletivo desses três comandos não deve ser inventado.

Screenshot não é prova de byte; nome de campo não é prova de semântica. Lacunas permanecem **DESCONHECIDO/HIPÓTESE**.
