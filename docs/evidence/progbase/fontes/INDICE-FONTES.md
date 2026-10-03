# Índice de fontes

Escopo vinculante confirmado no checkpoint 9: **somente a pasta DUMP e PortmonLOGNOVO (1).zip**. O prompt histórico em PROMPT-EVIDENCIA-PROGBASE.md contém fontes anteriores; a decisão de 2026-10-02 prevalece.

## DUMP

Pasta Drive: `1SMZqx2Sd1HKooJ5GVCSuYDPvkCn6R0kH`. Downloads autenticados completos, tamanho e SHA-256 recalculados nesta continuação. `fontes-manifest.json` conserva os identificadores e hashes de máquina. Nenhum binário original foi publicado.

| Arquivo | Drive id | Bytes | SHA-256 |
|---|---|---:|---|
| `RT_RCDATA(10)__TAUTOCALDM__0.bin` | `1RFMYjmAQRnF1XdSrecIiKEjAt_UdZBkk` | 14428 | `96ba3d934bf66593f919e22e37e2a90a171be5c765530e39d484ecfc16c1ee86` |
| `ProgBase.exe.ExportFunctions.txt` | `1Mx8J0vwTmxdwplhiYPr-fsKD59QJ4y7Q` | 82854 | `c9356a8775d37dff5f7722723d4cb49601efe3fd79d851448ce9b9cf017f357e` |
| `ProgBase.exe.Resources_StringTable.txt` | `1npbIYSwHk31EwOP3ozqyBcP-_jGRiHOU` | 21806 | `7f42578ea6fcd41c732404a1f7f059d3842ce4592b5a8d5fcd0c69f637a40912` |
| `Strings.txt` | `1GGw8QjV9NvIjbzw3lLIId8WxTjOEhid_` | 41408 | `f58de14751f352039bcaa501a6db024a1bda5279c83d79b1d5ded49d994c72a5` |
| `RT_RCDATA(10)__TAUTOCALUI__0.bin` | `1_9M_BHpXXqjeMnhrP8HGyLopB3N4SMvu` | 184604 | `449dda772b16b0d6a79c7bbd315754b37e4dbab26c129cb9346307daa2e954a7` |
| `RT_RCDATA(10)__TAUTOCALSETTINGS__0.bin` | `1z-C2DQz_VP1tCWAVJHBXTlC2p2HjKgha` | 59366 | `852de437e29e580936ff000aa49644289e455c1242d8ec9a64f35baa005e8f03` |
| `RT_RCDATA(10)__TSTREAMDATI__0.bin` | `1Zofwv5SgIIAl1fqfovkrlTKbsmoH2Tmz` | 109011 | `d55e310ec32e2154297c84ecc986f085647cd491c9d3ff2293c0de5b595b2250` |
| `RT_RCDATA(10)__TSTRATEGIATEMPIMORTIDM__0.bin` | `1rOUwebnPzo-UAZyBCRz9Prt6ZJwP_AS2` | 890 | `7d95a63dfe34469e7bd3e371176b2c22f8934e097681fb89feb27c129a790fbc` |
| `RT_RCDATA(10)__TFORMVISUALIZZA__0.bin` | `1fOwCidv2LbelVPOBmZawTPxMTiUkI7Nh` | 84613 | `a4f17519474258c01b3325666909f88d05db7a40f5bbd7c19d35c1a97df33e2f` |
| `RT_RCDATA(10)__TFORMRIFAUTOCAL__0.bin` | `1_GMhUzdxByV3-jEMMm5ajjj2vnHGKYHX` | 11172 | `52598aa3b6d4966167278dde5d35bedcb5eb90ecf47b12c5e0e0613d63a05e03` |
| `RT_RCDATA(10)__TFORMCONFIG__0.bin` | `1xfJWknmzO6D8uY_yAC7usyljjoE9YRZ_` | 334188 | `a09f4cb6780f1a9737b7ef2b2d0c3328640c34f092648fc96fe47b026afe80f0` |
| `RT_RCDATA(10)__TFORMCENTRICELLEK__0.bin` | `1HpSEPwiitYh6zoVqYvwRxeBicRgJXBgC` | 7616 | `e11be58cd4096186e52e6a53fd26a2e0e523a9dbc7a8e7c063047a74f270c7f2` |
| `RT_RCDATA(10)__TAUTOCALDM_EE__0.bin` | `1u9BPccpFmsrN7p4_3AOEBrjryvgfwxiE` | 6179 | `985db334af7211c5136404444131606445ef37831f9fe3ec0d512c81128d2a10` |
| `ProgBase.exe.Seções.txt` | `1DBR5jvWSc5ouJNW__a-53L3gWNDrghJ3` | 907 | `8cdec9c270b30300499a6c145c8ef5a9fef5a276ebe36ed4ef6ff09bd10a405e` |
| `ProgBase.exe.Dump.bin` | `1mnya31-24dyInxs77KcVRla24kRptzdP` | 12643840 | `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4` |
| `Seção_0_.text.bin` | `1MexhE01dT3atJTTA4dE9CEU6hAJT33uJ` | 6618624 | `979aef013c6b507cf3153c9f7d374261a93ad1c1fa3592de033681d79bccc772` |
| `Seção_1_.data.bin` | `1stWWHUe1xKB1TlfpDf6jUn9KmLwr421n` | 638464 | `4ef7cfe00254f2ff80533da88dd0633d33a0a761f903dff3c5249ef2e5e9d9b0` |

Os dez recursos DFM foram lidos até o EOF; inventário regenerado diretamente dos quatro modelos com SerialCode: 313 TSTREAMDATI + 35 TAUTOCALDM + 14 TAUTOCALDM_EE + 2 TSTRATEGIATEMPIMORTIDM = **364 componentes**. Propriedades ausentes permanecem ausentes, sem inferir defaults da classe.

Notação: offset zero-based dentro do arquivo DFM; VA no código. A seção .text tem VMA `0x00401000`; offset = VA − VMA. O Dump.bin é PE com ImageBase `0x00400000`; para .text, offset no PE = `0x600 + VA − 0x00401000`. A seção baixada coincide byte a byte com os 6.618.624 bytes da seção do Dump.bin.

## Captura LN

Fonte única: `PortmonLOGNOVO (1).zip`, Drive id `10s07RSG4Clg1wC0JclzL1azHKIJ7UZE0`, 14.170.842 B; SHA-256 `6879fa2a7931d22c207cd7fa47dffb59e1df0fe1de216e34e3f11e0c08cc1c17`.
Conteúdo: `PortmonLOGNOVO.LOG`, **149.911.521 B**, 2.631.711 linhas; SHA-256 `43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64`.

O prefixo anteriormente analisado (63.424.275 B) foi conferido no arquivo permitido: SHA-256 `341542e8790594e0ee640d2e80d131214f63c98199f16e36b061760fecdba1ce`. As seq/idx anteriores preservam a numeração. O download completo supera o bloqueio histórico L-11; a extensão das conclusões ao trecho novo é registrada no checkpoint seguinte.

## Ferramentas, não fontes

- `dfm2txt.py`: decodificador passivo de streams TPF0; saída JSON com offsets e EOF obrigatório. Uso: `python dfm2txt.py arquivo.bin`.
- `scripts/omegas/portmon_parser.py` no SHA de retomada `9c6d33b`: numeração LN (`seq` = escritas agrupadas, inclusive a sonda; `idx` = IRP da escrita). O código do app não é evidência do original.
- `dump-listing.tsv`: snapshot da listagem remota da pasta, sem conteúdo binário.

Estudos antigos, sessões OMEGAS, fixtures, .lec, outras capturas e reverse_report/dfm_text não sustentam novas provas.
