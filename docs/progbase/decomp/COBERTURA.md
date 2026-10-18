# Cobertura da decompilação do ProgBase.exe

Gerado por `tools/progbase/decomp_cobertura.py` (determinístico) a partir de `funcoes.json.gz`. Ferramentas: Ghidra 12.1.4 headless (análise + decompilador), RTTI Delphi lido por `decomp_rtti.py`. Regenerar tudo: `tools/progbase/decomp_tudo.sh`.

## Resumo (`.text` = 6.619.136 bytes virtuais)

| Métrica | Funções | Bytes | % de .text | Marca |
|---|---|---|---|---|
| Em função reconhecida pelo Ghidra | 29927 | 4173203 | 63.0% | OBS-COD |
| Função com NOME (RTTI publicados/propriedades/dinâmicos/virtuais + símbolos de import) | 9818 | 1341264 | 20.3% | OBS-COD |
| Com UNIDADE (âncora RTTI ou intervalo contíguo) | 21486 | 2637386 | 39.8% | OBS-COD + INFERIDO (13294 por intervalo) |
| Com pseudo-C (decompilador) | 25202 | 4148788 | 62.7% | OBS-COD |
| Funções das units do PROGRAMA (prefixo `@` no RTTI) | 2329 | 934950 | 14.1% | OBS-COD/INFERIDO |
| Sem nome e sem unidade | 7132 | 1463146 | 22.1% | DESCONHECIDO |
| Bytes de .text fora de qualquer função (dados Delphi, RTTI, tabelas, padding ou código não alcançado) | - | 2445933 | 37.0% | DESCONHECIDO |

Leitura honesta: "analisado" aqui = o Ghidra reconhece a função E ela tem pseudo-C. Nome vem só do RTTI; o resto (RTL/VCL/TeeChart sem RTTI) fica sem nome e entra no backlog. Pseudo-C é bruto do Ghidra (pode conter erro; marca OBS-COD só para o que está no binário, a leitura do pseudo-C é INFERIDO).

## Por unit (top 40 por bytes)

| Unit | Funções | Bytes | Com pseudo-C |
|---|---|---|---|
| System.Generics.Collections | 5251 | 338314 | 4479 |
| @:Config | 327 | 266875 | 327 |
| Vcl.ComCtrls | 1749 | 173199 | 1510 |
| Vcl.Styles | 790 | 104568 | 671 |
| @:Main | 70 | 90170 | 70 |
| VCLTee.TeEngine | 982 | 89488 | 899 |
| Vcl.Forms | 697 | 87577 | 613 |
| System.Classes | 1176 | 86732 | 818 |
| Vcl.Controls | 854 | 81363 | 752 |
| VCLTee.TeCanvas | 618 | 74526 | 571 |
| Vcl.StdCtrls | 616 | 65306 | 514 |
| @:iodata | 269 | 47797 | 269 |
| System.Rtti | 564 | 47444 | 428 |
| VCLTee.Series | 313 | 47166 | 301 |
| @:SerialData | 175 | 45031 | 175 |
| @:Diagnosi | 29 | 44830 | 29 |
| @:GraficoScope | 71 | 38538 | 71 |
| @:AebGrid | 103 | 37299 | 103 |
| Vcl.Graphics | 450 | 36873 | 358 |
| Vcl.Grids | 322 | 35675 | 281 |
| VCLTee.Chart | 342 | 34849 | 326 |
| Vcl.ExtCtrls | 340 | 33678 | 288 |
| Vcl.Themes | 340 | 33049 | 286 |
| @Shellctrls:AebShellCtrls | 169 | 29567 | 169 |
| @:AebBeeLight | 51 | 28274 | 51 |
| @:Aeb3DMap | 53 | 27595 | 53 |
| @:AutoCal_UI | 104 | 26783 | 104 |
| Vcl.Imaging.pngimage | 193 | 26412 | 164 |
| Vcl.CategoryButtons | 204 | 25584 | 187 |
| Vcl.Menus | 207 | 25517 | 179 |
| System.SysUtils | 335 | 23512 | 274 |
| Xml.XMLDoc | 257 | 21452 | 163 |
| Vcl.Dialogs | 252 | 20534 | 199 |
| @:File | 14 | 18337 | 14 |
| @:Visualizza | 11 | 18030 | 11 |
| VCLTee.TeeProcs | 176 | 17037 | 155 |
| @:Calibra | 9 | 16927 | 9 |
| System.Generics.Defaults | 233 | 15672 | 221 |
| System.Win.Sensors | 236 | 15463 | 201 |
| VCLTee.TeeFilters | 122 | 15136 | 100 |

## Backlog explícito (o que FALTA)

1. **7132 funções sem nome nem unit** (22.1% de .text) — RTL/VCL/TeeChart/Indy ligados estaticamente e funções privadas sem RTTI. Maiores 40 abaixo; lista completa em `backlog.json` (campo `sem_nome`). Caminho: assinaturas FLIRT/BSim de Delphi 10.x ou nomear por XREF de strings (`pistas` em `funcoes.json.gz`).
2. **1 função(ões) gigante(s)** (>60 KB; o Ghidra pode ter fundido regiões): 0x00439E94 (288956 B). Pseudo-C só se coube no tempo; revisar manualmente (SendCommand 0x04 é chamado de 0x0043C761 dentro dela, ver `docs/progbase/protocolo/comandos.md`).
3. **3 funções cujo pseudo-C falhou/expirou**: 0x00439E94, 0x004925B4, 0x004EDE60.
4. **2445933 bytes de .text fora de função** — não classificados (provável mistura de VMT/typeinfo/tabelas de salto/padding e código só alcançado por ponteiro). Maiores lacunas aproximadas abaixo (INFERIDO: endereço+tamanho do corpo).
5. **Resources.dll (3,2 MB) não decompilada** — só recursos; ver `docs/progbase/inventario`. **.rsrc/.reloc/.data** não entram em "bytes de .text".
6. Nomes de **locais/parâmetros/tipos** não foram recuperados (o Ghidra usa param_1...). Estruturas (TStreamDati etc.) só por offsets em `docs/progbase/protocolo`.
7. Fluxos por **ponteiro de método/evento em runtime** (Delphi) que o Ghidra não vê como chamada: cobertos só quando o DFM liga o evento (1376 eventos, `rtti.json`).

### 40 maiores funções sem nome

| Endereço | Bytes | Chamadores | Estado |
|---|---|---|---|
| 0x00439E94 | 288956 | 15 | pseudo-C |
| 0x004DF6FC | 22374 | 1 | pseudo-C |
| 0x00414B8C | 15648 | 2 | pseudo-C |
| 0x0041A680 | 10701 | 1 | pseudo-C |
| 0x00A2DFB4 | 10490 | 1 | pseudo-C |
| 0x00A35BD4 | 10470 | 1 | pseudo-C |
| 0x00A2AC14 | 8293 | 10 | pseudo-C |
| 0x009E785B | 8129 | 2 | pseudo-C |
| 0x00A079DB | 6158 | 0 | pseudo-C |
| 0x0040C4B8 | 6097 | 1 | pseudo-C |
| 0x00909A88 | 5979 | 1 | pseudo-C |
| 0x004D83CC | 5497 | 3 | pseudo-C |
| 0x00487B4C | 5351 | 3 | pseudo-C |
| 0x005ABCB0 | 5307 | 5 | pseudo-C |
| 0x00405DCC | 5162 | 1 | pseudo-C |
| 0x009F088B | 4664 | 1 | pseudo-C |
| 0x00913C2C | 4443 | 1 | pseudo-C |
| 0x0051DB24 | 4247 | 2 | pseudo-C |
| 0x0043834C | 4108 | 2 | pseudo-C |
| 0x00409C70 | 4086 | 1 | pseudo-C |
| 0x0090EFAC | 3963 | 0 | pseudo-C |
| 0x00A0D5AB | 3736 | 1 | pseudo-C |
| 0x009D0D4B | 3423 | 1 | pseudo-C |
| 0x009E01CB | 3180 | 0 | pseudo-C |
| 0x00418950 | 3116 | 1 | pseudo-C |
| 0x0051F2A0 | 2874 | 1 | pseudo-C |
| 0x009EFCFB | 2801 | 3 | pseudo-C |
| 0x0041F068 | 2706 | 2 | pseudo-C |
| 0x00A3233C | 2653 | 9 | pseudo-C |
| 0x00A334B8 | 2541 | 19 | pseudo-C |
| 0x00A345BC | 2541 | 16 | pseudo-C |
| 0x0041D060 | 2532 | 1 | pseudo-C |
| 0x00439358 | 2513 | 4 | pseudo-C |
| 0x00A41B80 | 2392 | 6 | pseudo-C |
| 0x0066EED0 | 2391 | 3 | pseudo-C |
| 0x005A9B54 | 2377 | 9 | pseudo-C |
| 0x00A410B8 | 2376 | 7 | pseudo-C |
| 0x0055FAA8 | 2319 | 1 | pseudo-C |
| 0x004142B0 | 2261 | 1 | pseudo-C |
| 0x009EF40B | 2254 | 1 | pseudo-C |

### 40 maiores lacunas entre funções

| Início (aprox.) | Bytes |
|---|---|
| 0x00A50D7D | 637307 |
| 0x0080085C | 136744 |
| 0x0088A291 | 122571 |
| 0x005DA21A | 97670 |
| 0x006F3996 | 95286 |
| 0x006A30A5 | 78563 |
| 0x00726B7C | 67032 |
| 0x0062F572 | 58462 |
| 0x0074397C | 55332 |
| 0x005989F6 | 51798 |
| 0x008E69A4 | 48968 |
| 0x006DD2E1 | 47067 |
| 0x00870961 | 46643 |
| 0x007DE1C8 | 43540 |
| 0x007BCCDA | 43322 |
| 0x0079AA2E | 42966 |
| 0x00547B30 | 41808 |
| 0x008CD6C6 | 41498 |
| 0x00562285 | 35943 |
| 0x006646E5 | 29851 |
| 0x0067C022 | 27962 |
| 0x0085CFD5 | 27879 |
| 0x0069AF71 | 24895 |
| 0x007885AB | 24589 |
| 0x00655751 | 24311 |
| 0x005C1398 | 21656 |
| 0x007D0D66 | 21502 |
| 0x0055936A | 20726 |
| 0x0057A8F9 | 17351 |
| 0x009BBC91 | 15875 |
| 0x005CD26D | 15847 |
| 0x0090B1E3 | 15817 |
| 0x0068B0E4 | 15028 |
| 0x005F206A | 14566 |
| 0x00992068 | 14312 |
| 0x0056FDCF | 14293 |
| 0x008DF16F | 13497 |
| 0x00779B23 | 13085 |
| 0x0064495B | 12969 |
| 0x0065EBCC | 11340 |

### Lacunas classificadas por conteúdo (`lacunas.json`, INFERIDO)

| Estado Ghidra : conteúdo | Faixas | Bytes |
|---|---|---|
| dado_definido:candidato_codigo | 1 | 1352 |
| dado_definido:padding | 11 | 72 |
| dado_definido:resto | 195 | 113221 |
| dado_definido:tabela_ponteiros | 273 | 15295 |
| dado_definido:texto | 24 | 1080 |
| indefinido:candidato_codigo | 41 | 23177 |
| indefinido:padding | 14591 | 34519 |
| indefinido:resto | 6962 | 2250493 |
| indefinido:tabela_ponteiros | 29 | 795 |
| indefinido:texto | 47 | 5945 |
| instrucao_sem_funcao:candidato_codigo | 1 | 64 |
| instrucao_sem_funcao:resto | 17 | 230 |

Observação (OBS-FIO da execução do Ghidra): ~3,9 mil faixas (941 KB) de instruções desmontadas sem função foram submetidas a `CriarFuncoesOrfas.java`; só ~25 KB viraram corpo de função e o restante voltou a "indefinido" — indício de que eram dados (literais Delphi) desmontados por varredura, não código (INFERIDO). O grosso do "resto" indefinido (≈2,25 MB) é o maior item do backlog para triagem: literais, tabelas de salto e talvez código só alcançado por ponteiro.
