# Índice de fontes

Tudo o que os temas citam está aqui, com identificador do Drive, tamanho/SHA-256 quando conhecidos, e caminho quando já está versionado no repositório. Hashes abreviados nos temas (`8a2d…36f4`) referem-se aos completos desta lista.

## A. Capturas Portmon do ProgBase real (valem mais que inferência)

| Nome | Drive id | Tamanho | SHA-256 | Uso |
|---|---|---|---|---|
| `PortmonAUTOCAL (1).LOG` | `1S80z7rWlXmczilxoR5G0X8qqiisH7TyA` | 162.700.984 B | `4a70f5ae79b1d688c05bd169f3e6a588b52105580d24b8a72a5cff398a384c0b` | ciclo AutoCal completo; 36.463 transações, 21 comandos distintos |
| `PortmonLOGNOVO.LOG` | `1SdGax-7xhA2TOAz-KonpEzgPkt8qoTMS` | 149.911.521 B | `43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64` | Reset All, três épocas de AutoMatch nativo, leituras de referência, escritas K |
| `PortmonAUTOCAL (1).zip` | `1mzuPeWlKbV2NX0J2FuIECFYzase5kso3` | 15.150.667 B | `a927795da5800baef53d498273f4e210bc814c2e0e3649572e6de42781073b47` (cópia `(1)(1)(1)`) | mesmo conteúdo compactado |
| `PortmonLOGNOVO (1)(2).zip` | `15wBA16wiwY052wsgz0cUE-QtLI6I-O-v` | 14.170.842 B | `6879fa2a7931d22c207cd7fa47dffb59e1df0fe1de216e34e3f11e0c08cc1c17` | mesmo conteúdo compactado |
| `PortmonLOGNOVO.zip` | `1idvIhV4eFGXv2VVNsU0CT6ewdtTBqNFp` | 6.029.222 B | ver `protocolo.md` | cópia menor usada nesta branch |
| `1.LOG`, `2.LOG`, `3.LOG` | `1V9jL1Dx4RYvRw6yLDXOmdBq0NSakVPNp`, `1fBOKbzwo7m1_OnZo22kj_aOoLfLpIy2Z`, `19_Kt5V0AYgAr7zqHSdCUy4NkNd5hxVT_` | 33.370 / 23.326 / 19.978 B | ver `telemetria.md` | telemetria `48 01 49` com motor em GNV; timeouts seriais do ProgBase |
| `logoff.LOG`, `SILVDOWN-58C615.LOG` | `1tXXXI4QUHK4dmGo0tBqsSxFDPxF7h5DS`, `1ZWmNngDhlC-O_5j-OVEzN1el0z2AJRIj` | 553.942 / 544.736 B | não lidos | candidatos a sequência de desconexão (ver `lacunas.md`) |

Parser canônico: `scripts/omegas/portmon_parser.py` (Platina). Manifesto do corpus AUTOCAL: `evidence/portmon/full-corpus-manifest.json`.

## B. DUMP do ProgBase 4.2.0.6 (pasta Drive `DUMP` = `1SMZqx2Sd1HKooJ5GVCSuYDPvkCn6R0kH`)

| Arquivo | Drive id | Tamanho | SHA-256 |
|---|---|---|---|
| `ProgBase.exe.Dump.bin` (= `ProgBase (3).exe`) | `1mnya31-24dyInxs77KcVRla24kRptzdP` | 12.643.840 B | `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4` |
| `RT_RCDATA(10)__TSTREAMDATI__0.bin` | `1Zofwv5SgIIAl1fqfovkrlTKbsmoH2Tmz` | 109.011 B | `d55e310ec32e2154297c84ecc986f085647cd491c9d3ff2293c0de5b595b2250` |
| `RT_RCDATA(10)__TAUTOCALDM__0.bin` | `1RFMYjmAQRnF1XdSrecIiKEjAt_UdZBkk` | 14.428 B | `96ba3d934bf66593f919e22e37e2a90a171be5c765530e39d484ecfc16c1ee86` |
| `RT_RCDATA(10)__TAUTOCALDM_EE__0.bin` | `1u9BPccpFmsrN7p4_3AOEBrjryvgfwxiE` | 6.179 B | `985db334af7211c5136404444131606445ef37831f9fe3ec0d512c81128d2a10` |
| `RT_RCDATA(10)__TAUTOCALUI__0.bin` | `1_9M_BHpXXqjeMnhrP8HGyLopB3N4SMvu` | 184.604 B | `449dda772b16b0d6a79c7bbd315754b37e4dbab26c129cb9346307daa2e954a7` |
| `RT_RCDATA(10)__TAUTOCALSETTINGS__0.bin` | `1z-C2DQz_VP1tCWAVJHBXTlC2p2HjKgha` | 59.366 B | `852de437e29e580936ff000aa49644289e455c1242d8ec9a64f35baa005e8f03` |
| `RT_RCDATA(10)__TFORMRIFAUTOCAL__0.bin` | `1_GMhUzdxByV3-jEMMm5ajjj2vnHGKYHX` | 11.172 B | `52598aa3b6d4966167278dde5d35bedcb5eb90ecf47b12c5e0e0613d63a05e03` |
| `RT_RCDATA(10)__TSTRATEGIATEMPIMORTIDM__0.bin` | `1rOUwebnPzo-UAZyBCRz9Prt6ZJwP_AS2` | 890 B | `7d95a63dfe34469e7bd3e371176b2c22f8934e097681fb89feb27c129a790fbc` |
| `RT_RCDATA(10)__TFORMCONFIG__0.bin` | `1xfJWknmzO6D8uY_yAC7usyljjoE9YRZ_` | 334.188 B | não registrado |
| `RT_RCDATA(10)__TFORMVISUALIZZA__0.bin` | `1fOwCidv2LbelVPOBmZawTPxMTiUkI7Nh` | 84.613 B | não registrado |
| `RT_RCDATA(10)__TFORMCENTRICELLEK__0.bin` | `1HpSEPwiitYh6zoVqYvwRxeBicRgJXBgC` | 7.616 B | não registrado |
| `RT_RCDATA(10)__TFORMCALIBRA__0.bin` | `1xSUygJP3V8CMOQJfx9sn8Wq9vFfeBG54` | 13.159 B | não registrado |
| `Seção_0_.text.bin` (código) | `1MexhE01dT3atJTTA4dE9CEU6hAJT33uJ` | 6.618.624 B | não registrado |
| `ProgBase.exe.String5s.txt` | `1dBl0wEx25BOXMrCWHEM1Bbc8UhigJIly` | 11.796.863 B | não registrado |
| `ProgBase.exe.ExportFunctions.txt` | `1Mx8J0vwTmxdwplhiYPr-fsKD59QJ4y7Q` | 82.854 B | não registrado |
| `Resources.dll` | `1cqhbK0bCUf4M06GidwBa_UhgIQ6XO_mm` | 3.289.600 B | `8c6b629cf1d69e13074ea8fd3203d48343d002a2ffc5503f20b6ce10ec564b15` |

Endereços de desmontagem citados nos temas são VA (ImageBase `0x00400000`); `.text` RVA `0x1000`, raw `0x600`. Offsets de DFM são posições zero-based dentro do recurso binário.

## C. DFM convertidos em texto (pasta `engenharia_reversa_omegas_4.2.0.6/reverse_report/dfm_text`)

| Arquivo | Drive id |
|---|---|
| `RCDATA_TSTREAMDATI_0.dfm.txt` | `1LL-yIuTgghHbtl-3GW5lFf2LOUYJXuf8` |
| `RCDATA_TAUTOCALDM_0.dfm.txt` | `1OgwRkmKbjxmNDLfbsvHwh_ghYH8CKNQb` |
| `RCDATA_TAUTOCALDM_EE_0.dfm.txt` | `1HfyhRBbA57s_YFd6_gdIrnnTUF0-GDpe` |
| `RCDATA_TAUTOCALUI_0.dfm.txt` | `1P36suAsj4Jgvrg6nYzlTq-IOkXIq2qYf` |
| `RCDATA_TAUTOCALSETTINGS_0.dfm.txt` | `1VlC4-ZTngownSnUHFPRxDpJ2qR9Pl0-X` |
| `RCDATA_TFORMCONFIG_0.dfm.txt` | `13r-o5iKrDZtzSaOFPU_tZevDTYo-N0P8` |
| `RCDATA_TFORMVISUALIZZA_0.dfm.txt` | `16PYL04ZUJsRfiexylysYtfuVN7CAi0Rc` |
| `RCDATA_TFORMRIFAUTOCAL_0.dfm.txt` | `1GRh2jguWREwu4XpDka0KrNfPV1Dj1sZv` |
| `RCDATA_TFORMCENTRICELLEK_0.dfm.txt` | `1XE8pV9AE17u4OZVIdTuJ42l7l2AMAGFY` |
| `RCDATA_TFORMCALIBRA_0.dfm.txt` | `15I2R-lkYWkkXYeY1PfaQs1i-IQHX4GHW` |
| `RCDATA_TSTRATEGIATEMPIMORTIDM_0.dfm.txt` | `1GJu64dlt1SR0tay_GSxs_d7dsJaWsu8-` |

Relatório estático e catálogos da mesma pasta (`1KRKzecLKdCPVs41rXVvMZ9VPoKDvoC_O`): `README_RELATORIO.md` `1XKjCR1MP9vlHjBS0Z2DE1csTiSzqTGj9`, `formula_catalog.csv` `1ofgq1_X4bSiUxBoNji4L8H2PBCNNl74r`, `exports_demangled.csv` `1CQdpmIa17h_QCBsSVdgQ1d31fNgaz10u`, `ui_event_bindings.csv` `1a3v01q2wiY2JhSM_8k4THk9LpWBBz-4t`, `dfm_components.csv` `1UnwKHzpRCgtrDzMzLVHnDclgg7xzQnCL`, `user_to_ecu.asm` `1a1U6DrnWr2G1wsrAI677OfhHX0jp7QLO`, `grid_interp_full.asm` `1w6PYxEufTLp2cqDcjEsXNbkMGayMSaR6`, `export_disassembly_previews.txt` `1tWEU2bv6P4TNywBvOYRdTOcRSJ5C8SZ5`.

## D. Configuração do carro do dono (Peugeot 208) salva pelo ProgBase

| Arquivo | Drive id | Tamanho |
|---|---|---|
| `ConfigCNG/208 pegeot.lec` | `12xtc5S0PpVSHLKp5Xo_rMQqDuIMPhIH_` | 28.970 B |
| `ConfigCNG/208 pegeot pos cali.lec` | `1soXrqmU-3ASHNSlxBNrlslVhN2DAS6k-` | 28.988 B |
| `m_autocal/autocalcfg.ini` | `1Zv1uvXs4xYHoS4ayao1N0xEMEyPnZjYZ` | 418 B |

Pasta `Firmware` (`14uCW04KN-wGhkwxHOZpYh76dNehpNwjh`, imagens `.ple/.pld/.pln/.plr`): ignorada de propósito (atualização de firmware está fora do escopo).

## E. Fixtures `ORIGINAL_DERIVED` já versionadas (branch `OmegasPlatina`, salvo indicação)

| Caminho | Conteúdo |
|---|---|
| `tests/fixtures/progbase-autocal-consumer-map-v1.json` | telemetria (offsets, consumidores, VAs), família AutoCal (comandos, cadências), nível (canal `0x0E`, VAs) |
| `tests/fixtures/progbase-autocal-action-map-v1.json` | ações `02 24 04 xx`, Reset K, handlers e VAs |
| `tests/fixtures/portmon-lognovo-autocal-reference-v1.json` | bytes exatos de `0x014B`, `0x014C`, `0x0161`, `0x018D`, `0x018E` (Lognovo, linhas 10050–14404) |
| `tests/fixtures/portmon-lognovo-autocal-epochs-v1.json` | Reset All e três épocas de AutoMatch nativo com bytes exatos (Lognovo) |
| `tests/fixtures/portmon-autocal-cycle-v1.json`, `portmon-autocal-real-sample.json` | seleção compacta e amostra do `PortmonAUTOCAL` |
| `tests/fixtures/progbase-autocal-scale-dfm-v1.json` | coeficientes `ttFormula` (`/512`, `/1024`, `/16384`) |
| `tests/fixtures/progbase-autocal-resource-defaults-v1.json` | recursos `.lec/.lrc` embutidos, chave MLTP, defaults AutoCal, grade de 12 linhas |
| `tests/fixtures/progbase-autocal-dump-contract-v1.json`, `-v2.json` | gramática `0x37`, Reset K (v2 supera v1), apagar pontos |
| `tests/fixtures/platinum-progbase-dump-autocal-v1.json` | identidade dos recursos, seletores de maturidade (`0x00516F64`, `05 09 0D`) |
| `tests/fixtures/platinum-autocal-action-parity-v1.json` | wire de cada ação com readback |
| `tests/fixtures/progbase-tautocaldm-dfm.bin.b64`, `progbase-autocal-grid-510df8.bin.b64`, `progbase-finish-text-51a390.bin.b64` | trechos mínimos do DUMP (DFM e código) |
| `config/mp48-k-map-physical-axes.lock.json` | eixos do Mapa K usados pelo OMEGAS (não lidos da ECU) |
| `docs/evidence/2026-09-21-progbase-autocal-byte-consumer-matrix.md` e `…-omegas-autocal-progbase-parity-matrix.md` | leitura humana do consumer map e da paridade |
| `docs/evidence/2026-09-22-autocal-fixture-provenance.md`, `2026-09-28-progbase-autocal-porting-readiness.md` | proveniência e mapa de rotinas |
| `docs/platinum/evidence/reset-k-factor-progbase-2026-09-28.md`, `autocal-maturity-boundary-2026-09-28.md` | provas do Reset K e dos seletores de maturidade |
| `docs/incidents/2026-08-12-mp48-serial-authority-fragmented.md` | observação do Lognovo: telemetria intercalada; bloco de 144 escritas K |
| (`OmegasVerde`) `tests/fixtures/progbase-autocal-ui-handler-map-v1.json`, `progbase-classic-calibration-v1.json`, `progbase-autocal-host-editor-v1.json`, `docs/autocal/progbase-host-parity-v0.md` | 53 handlers do `TAutoCalUI`, `TFormCalibra` (`0x14`), editor de referências |

## F. Código do OMEGAS consultado (só para saber o que o app usa)

`app/src/main/java/com/omegas/prohub/ecu/{Mp48Protocol,AutoCalProtocol,KFactorProtocol,Mp48TelemetryScale,AutoCalScale,AutoCalPointDeleteProtocol,ResponseDrivenEcuEngine}.kt`, `usb/{UsbSerialManager,UsbProtocolReply}.kt`, `calibration/{KWriteManager,KFactorManager}.kt`, `autocal/NativeAutoCalMonitor.kt`, em `origin/OmegasPlatina` `b185e80`.
