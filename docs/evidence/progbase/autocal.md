# Tema 4 — AutoCal nativo (`TAutoCalDM` / `TAutoCalUI`)

Fontes: DFM do DUMP (`DUMP/RT_RCDATA(10)__TAUTOCALDM__0.bin`, `__TAUTOCALDM_EE__0.bin`, `__TAUTOCALUI__0.bin`, `__TAUTOCALSETTINGS__0.bin`; offsets em `fontes/parametros-dfm-inventario.json`), desmontagem por VA (estudos anteriores sobre `DUMP/ProgBase.exe.Dump.bin`, não reexecutados nesta branch: `lacunas.md` L-12) e o LN (`LN seq`, `protocolo.md`). Escalas dos vetores (`/512` ms, `/1024` bar, `/16384` fator) vêm das propriedades `Coeffs`/precisão do DFM `TAUTOCALDM`.

## 4.1 Objetos, comandos e valores reais

Todos os objetos abaixo são `TAebVector`/`TAebNumber` com `Connection = StreamDati.AebConnection`; o DFM declara `ArrayDimension = 18`, mas esta ECU (`MODULE_VERSION` = 4) devolve **30** elementos em `PETR_INJ_TBP`, `MUL_ACT`, `PETR/GAS_MNFLD_PRESS_RV` e **18** nos buffers de aquisição. `PROVADO` (LN `len 3C` vs `len 24`).

| SC (hex) | Nome | Enc. × n | Escala → unidade | Comando | Cadência no ProgBase | Consumidor (`TAutoCalUI`) | Valor nesta ECU (LN, dump de conexão) | Selo |
|---|---|---|---|---|---|---|---|---|
| 330 (0x014A) | `AUTO_CAL_ENABLE` | U8 | 0/1 | `09 4A 01` / `12 4A 01 v` | lido no dump; escrito pelo usuário | `CheckAutoCalEnable` | `1`; `12 4A 01 00` (seq 12615), `12 4A 01 01` (seq 12691) | PROVADO |
| 331 (0x014B) | `PETR_INJ_TBP` | U16 × 30 | `/512` ms | `29 4B 01` | só no dump (3×) | `KLine.x`, `PetrolCurve.x`, `GasCurve.x`, `AcqusitionAreas` | `0,5 1,0 1,5 … 10,0 11 12 13 14 15 16 17 18 20 22` ms | PROVADO |
| 332 (0x014C) | `MNFLD_PRESS_THD` | S16 × 18 | `/1024` bar | `29 4C 01` | só no dump | `CurrentBand`, `AcqusitionAreas` (limites das 18 bandas de MAP) | `0,150 0,25 0,30 0,35 0,40 0,45 0,50 0,55 0,60 0,65 0,70 0,75 0,80 0,85 0,90 0,95 1,00 1,10` bar | PROVADO |
| 347 (0x015B) | `NUM_BUF_UPD_PETR` | U16 × 18 | contagem (satura em 10) | `29 5B 01` | ~2,0 s | `NumPetrPt`/maturidade | 14 bandas em 10, depois 8, 4, 0, 0 | PROVADO |
| 348 (0x015C) | `NUM_BUF_UPD_GAS` | U16 × 18 | idem | `29 5C 01` | ~2,0 s | `NumGasPt` | 16 bandas em 10, duas em 0 | PROVADO |
| 349/350 (0x015D/0x015E) | `PETR_INJ_TBUF_GAS_PREV` / `MNFLD_PRESS_BUF_GAS_PREV` | U16/S16 × 18 | `/512` ms, `/1024` bar | `29 5D 01`, `29 5E 01` | ~2,0 s | `GasPointPrev` (x, y) | ex. `0x04C0`→2,375 ms, `0x00E8`→0,227 bar | PROVADO |
| 351/352 (0x015F/0x0160) | `PETR_INJ_TBUF_GAS` / `MNFLD_PRESS_BUF_GAS` | idem | idem | `29 5F 01`, `29 60 01` | ~2,0 s | `GasPoint` | ex. `0x041C`→2,05 ms, `0x00CA`→0,197 bar | PROVADO |
| 353 (0x0161) | `MUL_ACT` | U16 Q14 × 30 | `/16384` fator | `29 61 01` / `14 61 01 i lo hi` | ~2,0 s | `KLine.y` (curva K azul) | `0x35B1`→0,840 … `0x4591`→1,087 (antes do Reset All); `0x4000`→1,000 ×30 depois | PROVADO |
| 354/355 (0x0162/0x0163) | `PETR_INJ_TBUF` / `MNFLD_PRESS_BUF` | idem | idem | `29 62 01`, `29 63 01` | ~2,0 s | `PetrolPoint` | ex. `0x0418`→2,05 ms, `0x00AD`→0,169 bar | PROVADO |
| 357 (0x0165) | `VECT_AUTOCAL_U8_0/1/2` | U8 × 3 (indexado) | idx 0 `AUTOCAL_IDLE_MIN_BUF_PETR_THD`; idx 1 `!AUTOCAL_IDLE_MIN_BUF_UPD_PETR_THD`; idx 2 `MaxAutomatch` | `0A 65 01 i` | dump | maturidade (idx 1) e `Finish` (idx 2) | `3`, `3`, `3` | PROVADO (DFM `FileKeyName` + LN) |
| 359 (0x0167) | `EN_CDN_T_THD` | U16 (idx 1) | `/1024` | `0A 67 01 01` | dump | `TAutoCalSettings` | `0x0400`→1,0 | PROVADO |
| 361/362 (0x0169/0x016A) | `LIMIT_PRESSURE_MIN/MAX` | S16 | `/1024` bar | `09 69 01`, `09 6A 01` | dump | limites de MAP (`CursorLimitMAP_MIN/MAX` no gráfico) | `0x009A`→0,150; `0x0466`→1,10 bar (= primeiro e último `MNFLD_PRESS_THD`) | PROVADO |
| 365/366 (0x016D/0x016E) | `PETROL/GAS_POINT_2DELETE` | U8 × 18 | 0 = apagar, 1 = manter | `SetVector` + `01 24 05` | nunca lidos no LN | `ActionDeleteSelectedPointsExecute` | — | PROVADO (DFM/desmontagem) / INFERIDO (fiação) |
| 367/368 (0x016F/0x0170) | `ACQUIRED_ZONES_PETROL/GAS` | U8 × 4 | 1 = zona adquirida | `29 6F 01`, `29 70 01` | ~2,0 s | `AcqusitionAreas` (Z1…Z4) | gasolina `1 1 1 1`; GNV `0 0 0 0` → `1 1 0 0` | PROVADO |
| 370 (0x0172) | `CALIBRATION_VAL_1` | U8 × 10 | limiares de maturidade | `29 72 01` | dump | seletores `0x00516F64` | `1 3 3 1 3 3 1 3 3 1` | PROVADO |
| 372 (0x0174) | `NUM_ATUOMATCH_EXECUTED` | U8 (U16 noutras ECU) | contagem | `09 74 01` | esporádico | `Finish`, "Num automatch 0/3" | `3` → `0` após Reset All | PROVADO |
| 378 (0x017A) | `MAX_RPM_FOR_AUTOCAL` | U16 | rpm | `09 7A 01` | dump | `TAutoCalSettings` | `3000` | PROVADO |
| 387/388 (0x0183/0x0184) | `DIFF/DELTA_ENG_SPD_THD` | U16 | rpm | `09 83 01`, `09 84 01` | dump | gates de aquisição | `400` / `200` | PROVADO |
| 389/390 (0x0185/0x0186) | `DIFF/DELTA_MNFLD_PRESS_THD` | U16 | `/1024` bar | `09 85 01`, `09 86 01` | dump | gates | `0x0200`→0,50 / `0x33`→0,050 | PROVADO |
| 391/392 (0x0187/0x0188) | `DIFF/DELTA_PETR_TINJ_T_THD` | U16 | `/512` ms | `09 87 01`, `09 88 01` | dump | gates | `0x0800`→4,0 / `0x0200`→1,0 ms | PROVADO |
| 395 (0x018B) | `DISABLE_ACQ_BAND` | U8 | 0/1 | `09 8B 01` | dump | "Disable highest acquisition band" | `0` | PROVADO |
| 397/398 (0x018D/0x018E) | `PETR/GAS_MNFLD_PRESS_RV` | S16 × 30 | `/1024` bar | `29 8D 01`, `29 8E 01` | ~4,0 s | `PetrolCurve.y`, `GasCurve.y` | gasolina `0,010 … 1,456` bar; GNV `0,088 … 1,24` bar (MAP de referência por ponto de `PETR_INJ_TBP`) | PROVADO |
| 334–346 (0x014E…0x015A) | `TAutoCalDM_EE` (`*_EE`) | iguais aos vivos | idem | `29 4E…5A 01` | dump (3×) | espelho EEPROM (debug `BtnDumpEE`) | `MUL_ACT_EE` = `MUL_ACT`; `MUL_PREV_EE` `0x3D0C`→0,954…; `MUL_UPD_CALL_CNTR_EE` = 3 | PROVADO |
| 356 (0x0164) | `VECT_AUTOCAL_EE` | U16 × 4 | — | `29 64 01` | — | — | `CA 01 10` (não suportado) | PROVADO |

Os defaults do DFM `TAUTOCALDM` (400/200/0,5/0,05/4,0/1,0/0,05/0,95) coincidem com os lidos desta ECU, exceto `LIMIT_PRESSURE_MIN/MAX` (0,150/1,10 lidos vs 0,05/0,95 DFM). Os recursos de configuração embutidos no executável (`.lec/.lrc` de fábrica, seção `.rsrc`) não foram reabertos nesta branch.

## 4.2 Maturidade e zonas (`PROVADO`, desmontagem `0x00516F64` + DFM)

- 18 bandas de MAP (índices 0–17) agrupadas em 4 zonas com fronteiras inclusivas `5 / 9 / 13` (tabela estática `05 09 0D` em `0x00A9DA1A`).
- Limiar de maturidade por banda: gasolina bandas 0–5 → `VECT_AUTOCAL_U8_1`; gasolina 6–17 → `CALIBRATION_VAL_1[2]`; GNV 0–5 → `CALIBRATION_VAL_1[5]`; GNV 6–17 → `CALIBRATION_VAL_1[8]`. Nesta ECU todos = 3.
- `MaxAutomatch` (`VECT_AUTOCAL_U8_2`) **não** é limiar de maturidade; é a cota de AutoMatch (3).
- A ECU satura `NUM_BUF_UPD_*` em 10 por banda (LN: nunca acima de `0x0A`).

## 4.3 Ações (bytes exatos)

| Ação | Handler (VA) | Frame | Efeito observado | Selo | Fonte |
|---|---|---|---|---|---|
| Ligar/desligar AutoCal | `CheckAutoCalEnableBeforeSetData` `0x0051A474` | `12 4A 01 01 5E` / `12 4A 01 00 5D` → `53 00` | byte 12 de `48 0B` acompanha | PROVADO | LN seq 12691 / 12615 |
| Reset gasolina | `ActionResetPetrolExecute` `0x005189C0` | `02 24 04 01 2B` | — | PROVADO (frame, desmontagem) / DESCONHECIDO (efeito) | desmontagem (L-12); nunca enviado no prefixo do LN |
| Reset GNV | `ActionResetGasExecute` `0x005189CC` | `02 24 04 02 2C` | — | idem | idem |
| **Reset all** | `ActionResetAllExecute` `0x005189D8` | `02 24 04 04 2E` → `53 00` | imediatamente: `PETR_INJ_TBUF`, `MNFLD_PRESS_BUF`, `NUM_BUF_UPD_*`, `*_GAS_PREV`, zonas → zeros; `MUL_ACT` → `0x4000` ×30 (seq 1496); `NUM_ATUOMATCH_EXECUTED` 3 → 0 (seq 1502); `48 0B` byte 12 `01` → `00` (AutoCal fica **desligado**); curvas `*_RV` zeradas (seq 12614 ainda zerada) | PROVADO | LN seq 1487–1502 |
| AutoMatch manual | `ActionAutoMatchExecute` `0x005189B4` | `02 24 04 08 32` | — | PROVADO (frame, desmontagem) / DESCONHECIDO (efeito; nunca enviado no prefixo do LN) | desmontagem (L-12) |
| Reset Curva K (K factor) | `ActionResetKFactorExecute` `0x0051A070` | 30 × `14 61 01 i 00 40 cs` (`MUL_ACT[i] = 1,0`) | — | PROVADO (desmontagem: IEEE `1.0` → `SetDouble` em `TAutoCalDM+0xA0` = `MUL_ACT`, contador `0x00513208`) / não visto na fiação | desmontagem (L-12) |
| Finish AutoCal / Finish AutoMatch | `ActionFinishAutocalExecute` `0x0051A390` / `BtnFinishAutomatchClick` `0x0051A454` | copia `MaxAutomatch` (0x0165:2) para `NUM_ATUOMATCH_EXECUTED` (0x0174) via `SetNumber` (`12 74 01 v` ou 2 bytes); Finish AutoCal dorme 100 ms e refaz a tela | — | PROVADO (desmontagem + layout `0x00510DF8`) / não visto na fiação | desmontagem (L-12) |
| Apagar pontos selecionados | `ActionDeleteSelectedPointsExecute` `0x00518E6C` | `SetVector` de `GAS_POINT_2DELETE` (18 bytes) + `PETROL_POINT_2DELETE` (18 bytes) + `01 24 05 2A` | — | PROVADO (desmontagem) / não visto na fiação | desmontagem (L-12) |
| Editar referências (`TFormRifAutocal`) e ExportToK | `ActionAutoCalRifExecute` `0x005187A0`, `ActionExportToKExecute` `0x00518514` | sem `SendCommand` direto nos handlers | só matemática no PC (`0x00512708` bracket, `0x0051280C` interpolação linear) | PROVADO (host-only) | desmontagem (L-12) |

## 4.4 AutoMatch nativo: o que a fiação mostra

- `PROVADO` (LN, prefixo): antes do Reset All a ECU já tinha `NUM_ATUOMATCH_EXECUTED` = 3 e `MUL_UPD_CALL_CNTR_EE` = 3 com `MUL_ACT` ≠ 1,0 e `MUL_PREV_EE` ≠ `MUL_ACT` (seq 342, 368–370): três AutoMatch já haviam ocorrido com o PC apenas lendo (nenhum `02 24 04 08` em todo o prefixo). As três épocas **seguintes** ao Reset All (contador 0→1→2→3, rolagem dos buffers `*_GAS` para `*_GAS_PREV`, novo `MUL_ACT`) estão depois da seq 20.288 e ficam `DESCONHECIDO` nesta branch até o LOG completo ser parseado (`lacunas.md` L-06/L-11).
- `PROVADO` (LN): `NUM_BUF_UPD_GAS` continua a crescer após o contador chegar a 3 (aquisição não para na cota).
- `INFERIDO`: `MUL_ACT[i]` novo = função de (`PETR_INJ_TBUF`, `MNFLD_PRESS_BUF`) × (`PETR_INJ_TBUF_GAS`, `MNFLD_PRESS_BUF_GAS`) nas mesmas bandas de MAP — as duas curvas de referência são MAP(Tinj gasolina) e MAP(Tinj GNV) sobre o mesmo eixo `PETR_INJ_TBP`, e o nome `MUL_ACT` ("multiplicador atual") mais a convenção 1,0 = neutro apontam para `K = Tinj_gasolina_equivalente / Tinj_atual` por banda. A aritmética exata está no firmware: `DESCONHECIDO` (o PC não a contém; ver `lacunas.md` L-05).
- `PROVADO`: `MUL_PREV_EE` guarda o `MUL_ACT` da época anterior; `MUL_UPD_CALL_CNTR_EE` = número de AutoMatch executados.

## 4.5 O gráfico `ChartData` do `TAutoCalUI`: camadas e origem

| Camada (DFM) | Classe | Fonte dos dados |
|---|---|---|
| `RunPoint` | ponto | telemetria `48 01`: x = byte 8 (gasolina, ms), y = byte 17 (MAP, bar); atualizado a 75 ms |
| `RunAxes` / `KRunAxes` / `NRunAxes` | linhas de cursor | mesmo ponto vivo |
| `CurrentBand` | `THorizAreaSeries` | banda `i` com `MNFLD_PRESS_THD[i] < MAP ≤ THD[i+1]` (helper `0x0051A614`) |
| `AcqusitionAreas` | `THorizAreaSeries` | `ACQUIRED_ZONES_*` × eixos (`0x005171EC`) |
| `PetrolPoint` / `PetrolLine` | pontos/linha | `PETR_INJ_TBUF` × `MNFLD_PRESS_BUF` |
| `GasPoint` / `GasLine` / `GasPointPrev` | idem | `PETR_INJ_TBUF_GAS` × `MNFLD_PRESS_BUF_GAS`; `*_GAS_PREV` |
| `PetrolCurve` / `GasCurve` | `TLineSeries` | `PETR_INJ_TBP` × `PETR_MNFLD_PRESS_RV` / `× GAS_MNFLD_PRESS_RV` |
| `KLine` (`ChartKLine`) | linha | `PETR_INJ_TBP` × `MUL_ACT` (eixo 0,1…10,0 editável por arrasto/teclado em passos de 0,05; commit por `SetDouble`) |
| `CursorLimitMAP_MIN/MAX` | linhas | `LIMIT_PRESSURE_MIN/MAX` |
| `PollingPetrol` / `PollingGas` | `TShape` vermelho/verde | indicador de polling, sem dado da ECU |

`PROVADO` (DFM `DUMP/RT_RCDATA(10)__TAUTOCALUI__0.bin` para as séries; desmontagem para a origem dos dados, L-12). A tela ainda mostra "Num automatch N / MaxAutomatch" e habilita "Abilita autocalibrazione". As cores padrão vêm de um arquivo externo `autocalcfg.ini` (fora do DUMP) e do DFM `TAUTOCALCOLORSETTINGS`; não entram aqui.
