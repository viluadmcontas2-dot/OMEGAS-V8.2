# Índice de fontes

Por decisão do owner (2026-10-02) esta branch admite **duas fontes de evidência e nada mais**:

1. a pasta **DUMP** do Drive (`1SMZqx2Sd1HKooJ5GVCSuYDPvkCn6R0kH`): dump do `ProgBase.exe` 4.2.0.6 e seus recursos;
2. a captura **`PortmonLOGNOVO (1).zip`** (`10s07RSG4Clg1wC0JclzL1azHKIJ7UZE0`, 14.170.842 B), do ProgBase real falando com a MP48 do carro.

Nada gerado pelo OMEGAS (sessões, fixtures, código, documentos de evidência de outras branches) é citado como prova. Arquivos de configuração do carro (`.lec`), outras capturas Portmon (`PortmonAUTOCAL`, `1/2/3.LOG`) e a pasta `reverse_report/dfm_text` ficam fora por decisão do owner.

## A. Captura Portmon (notação `LN`)

| Item | Valor |
|---|---|
| Arquivo | `PortmonLOGNOVO (1).zip` → `PortmonLOGNOVO.LOG` |
| Drive id | `10s07RSG4Clg1wC0JclzL1azHKIJ7UZE0` (14.170.842 B, modificado 2026-08-13) |
| Trecho parseado nesta branch | **prefixo** de 63.424.275 B / 1.117.269 linhas / 20.288 transações (idx IRP ≤ 558.633); SHA-256 do prefixo `341542e8790594e0ee640d2e80d131214f63c98199f16e36b061760fecdba1ce` |
| Por que só o prefixo | a ferramenta do Drive deste ambiente recusa arquivos > 10 MB e o acesso HTTP direto é bloqueado; o prefixo veio de `PortmonLOGNOVO.zip` (`1idvIhV4eFGXv2VVNsU0CT6ewdtTBqNFp`, 6.029.222 B), que é o mesmo LOG truncado: sequências e índices IRP coincidem (ex. Reset All seq 1487 / idx 34971; `29 4B 01` seq 20200 / idx 556102). O restante (seq 20.289 em diante, com as três épocas de AutoMatch nativo) é lacuna L-11 |
| Parser | `scripts/omegas/portmon_parser.py` (ferramenta, não evidência): `seq` = ordem das escritas com resposta; `idx` = número IRP da linha `IRP_MJ_WRITE` |

## B. DUMP (pasta `1SMZqx2Sd1HKooJ5GVCSuYDPvkCn6R0kH`)

Notação nos temas: `DUMP/<título>`; `@0x…` = offset zero-based dentro do recurso; `VA` = endereço virtual (ImageBase `0x00400000`, `.text` VMA `0x00401000`).

| Título | Drive id | Bytes | SHA-256 | Uso nesta branch |
|---|---|---:|---|---|
| `ProgBase.exe.Dump.bin` | `1mnya31-24dyInxs77KcVRla24kRptzdP` | 12.643.840 | `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4` (registrado em estudo anterior; não rebaixado nesta branch) | alvo dos VAs de desmontagem (L-12) |
| `Seção_0_.text.bin` | `1MexhE01dT3atJTTA4dE9CEU6hAJT33uJ` | 6.618.624 | — (download falhou: excede o transporte da ferramenta) | L-12 |
| `Seção_1_.data.bin` | `1stWWHUe1xKB1TlfpDf6jUn9KmLwr421n` | 638.464 | `4ef7cfe00254f2ff80533da88dd0633d33a0a761f903dff3c5249ef2e5e9d9b0` | baixado; não usado em prova |
| `Seção_6_.edata.bin` | `1-PHAWKBK7UPfJWkrP9wSwGTj7cb3Zxwr` | 65.024 | `d8f7ce2ad24d89862f751190d89a2e1052eedceb54e581a0c67c62aab6c4f833` | tabela de exportação (nomes `TAeb*`) |
| `ProgBase.exe.ExportFunctions.txt` | `1Mx8J0vwTmxdwplhiYPr-fsKD59QJ4y7Q` | 82.854 | SHA_EXPORTS | nomes/VAs exportados (`TAebProtocol.*`, `TAeb*.WriteToFile`) |
| `ProgBase.exe.Resources_StringTable.txt` | `1npbIYSwHk31EwOP3ozqyBcP-_jGRiHOU` | 21.806 | SHA_STRTAB | textos de UI |
| `Strings.txt` | `1GGw8QjV9NvIjbzw3lLIId8WxTjOEhid_` | 41.408 | SHA_STRINGS | textos |
| `RT_RCDATA(10)__TSTREAMDATI__0.bin` | `1Zofwv5SgIIAl1fqfovkrlTKbsmoH2Tmz` | 109.011 | SHA_TSTREAMDATI | todos os `TAeb*` de parâmetros (SC, forma, escala) |
| `RT_RCDATA(10)__TAUTOCALDM__0.bin` | `1RFMYjmAQRnF1XdSrecIiKEjAt_UdZBkk` | 14.428 | SHA_TAUTOCALDM | objetos AutoCal 0x014A–0x018E |
| `RT_RCDATA(10)__TAUTOCALDM_EE__0.bin` | `1u9BPccpFmsrN7p4_3AOEBrjryvgfwxiE` | 6.179 | SHA_TAUTOCALDM_EE | espelho EEPROM |
| `RT_RCDATA(10)__TAUTOCALUI__0.bin` | `1_9M_BHpXXqjeMnhrP8HGyLopB3N4SMvu` | 184.604 | SHA_TAUTOCALUI | séries do gráfico, controles |
| `RT_RCDATA(10)__TAUTOCALSETTINGS__0.bin` | `1z-C2DQz_VP1tCWAVJHBXTlC2p2HjKgha` | 59.366 | SHA_TAUTOCALSETTINGS | tela de limiares |
| `RT_RCDATA(10)__TFORMCENTRICELLEK__0.bin` | `1HpSEPwiitYh6zoVqYvwRxeBicRgJXBgC` | 7.616 | SHA_TFORMCENTRICELLEK | eixos do Mapa K |
| `RT_RCDATA(10)__TFORMVISUALIZZA__0.bin` | `1fOwCidv2LbelVPOBmZawTPxMTiUkI7Nh` | 84.613 | SHA_TFORMVISUALIZZA | rótulos da telemetria, `Timer1` |
| `RT_RCDATA(10)__TFORMCONFIG__0.bin` | `1xfJWknmzO6D8uY_yAC7usyljjoE9YRZ_` | 334.188 | SHA_TFORMCONFIG | nível (`ComboSensore`, `GroupRifSensore`), `TimerDati` |
| `RT_RCDATA(10)__TFORMCALIBRA__0.bin` | `1xSUygJP3V8CMOQJfx9sn8Wq9vFfeBG54` | 13.159 | SHA_TFORMCALIBRA | calibração clássica (ignorada) |
| `RT_RCDATA(10)__TFORMRIFAUTOCAL__0.bin` | `1_GMhUzdxByV3-jEMMm5ajjj2vnHGKYHX` | 11.172 | SHA_TFORMRIFAUTOCAL | referências AutoCal |
| `RT_RCDATA(10)__TSTRATEGIATEMPIMORTIDM__0.bin` | `1rOUwebnPzo-UAZyBCRz9Prt6ZJwP_AS2` | 890 | SHA_TSTRATEGIATEMPIMORTIDM | tempos mortos |

A listagem completa da pasta (todos os recursos `RT_*`, seções e textos) está em `fontes/dump-listing.tsv`. Decodificador usado para os DFM binários: `fontes/dfm2txt.py` (ferramenta desta branch; imprime offset de cada objeto/propriedade).

## C. Ferramentas (não são evidência)

- `scripts/omegas/portmon_parser.py` (branch `OmegasPlatina`) para o LOG.
- `fontes/dfm2txt.py` para os recursos DFM.
- `fontes/gerar-inventario.py` para `fontes/parametros-dfm-inventario.json`.
