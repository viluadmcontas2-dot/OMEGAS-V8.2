# TAutoCalUI (recurso `TAUTOCALUI`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `AutoCalUI`; unidade: `@:AutoCal_UI`; ancestral: `TFrame`; tamanho de instância: 0x4EC; componentes no DFM: 98.
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

## Menus (17)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| MenuChartK/ExportKchart1 | TMenuItem | Export to file... |  | ActionExportKChart.OnExecute→`ActionExportKChartExecute` (0x005185FC) |
| MenuChartK/Resetkfactor1 | TMenuItem | Reset k-factor... |  | ActionResetKFactor.OnExecute→`ActionResetKFactorExecute` (0x0051A070) |
| MenuChartData/Exporttofile1 | TMenuItem | Export to file... |  | ActionExportChartData.OnExecute→`ActionExportChartDataExecute` (0x0051878C) |
| MenuChartData/Deleteselectedpoints1 | TMenuItem | Delete selected points |  | ActionDeleteSelectedPoints.OnExecute→`ActionDeleteSelectedPointsExecute` (0x00518E6C) |
| MenuChartData/Resetpetrolpoint1 | TMenuItem | Reset petrol point |  | ActionResetPetrol.OnExecute→`ActionResetPetrolExecute` (0x005189C0) |
| MenuChartData/Resetgaspoint1 | TMenuItem | Reset gas point |  | ActionResetGas.OnExecute→`ActionResetGasExecute` (0x005189CC) |
| MenuChartData/Resetall1 | TMenuItem | Reset all |  | ActionResetAll.OnExecute→`ActionResetAllExecute` (0x005189D8) |
| MenuChartData/Modifymaprefs2 | TMenuItem | Modify map refs |  | ActionAutoCalRif.OnExecute→`ActionAutoCalRifExecute` (0x005187A0) |
| MenuChartData/Customizechartcolors1 | TMenuItem | Customize chart colors... |  | ActionChangeColor.OnExecute→`ActionChangeColorExecute` (0x0051AE78) |
| MenuChartPoint/Exporttofile2 | TMenuItem | Export to file... |  | ActionExportChartPoint.OnExecute→`ActionExportChartPointExecute` (0x00519120) |
| MenuAutoCal/Modifymaprefs1 | TMenuItem | Modify map refs |  | ActionAutoCalRif.OnExecute→`ActionAutoCalRifExecute` (0x005187A0) |
| MenuAutoCal/Gotomincalibration1 | TMenuItem | Go to min calibration |  | ActionGoToMinCalibration.OnExecute→`ActionGoToMinCalibrationExecute` (0x005189E4) |
| MenuAutoCal/Manualautomatch1 | TMenuItem | Manual automatch |  | ActionAutoMatch.OnExecute→`ActionAutoMatchExecute` (0x005189B4) |
| MenuAutoCal/Resetpetrolpoint2 | TMenuItem | Reset petrol point |  | ActionResetPetrol.OnExecute→`ActionResetPetrolExecute` (0x005189C0) |
| MenuAutoCal/Resetgaspoint2 | TMenuItem | Reset gas point |  | ActionResetGas.OnExecute→`ActionResetGasExecute` (0x005189CC) |
| MenuAutoCal/Resetall2 | TMenuItem | Reset all |  | ActionResetAll.OnExecute→`ActionResetAllExecute` (0x005189D8) |
| MenuAutoCal/FinishAutocal1 | TMenuItem | FinishAutocal |  | ActionFinishAutocal.OnExecute→`ActionFinishAutocalExecute` (0x0051A390) |

## Ações (16)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| AzioniAutoCal/ActionExportToK | TAction | ExportToK... |  | OnExecute→`ActionExportToKExecute` (0x00518514) |
| AzioniAutoCal/ActionPreview | TAction | Chart preview |  |  |
| AzioniAutoCal/ActionExportKChart | TAction | Export to file... |  | OnExecute→`ActionExportKChartExecute` (0x005185FC) |
| AzioniAutoCal/ActionExportChartData | TAction | Export to file... |  | OnExecute→`ActionExportChartDataExecute` (0x0051878C) |
| AzioniAutoCal/ActionAutoCalRif | TAction | Modify map refs |  | OnExecute→`ActionAutoCalRifExecute` (0x005187A0) |
| AzioniAutoCal/ActionAutoMatch | TAction | Manual automatch |  | OnExecute→`ActionAutoMatchExecute` (0x005189B4) |
| AzioniAutoCal/ActionResetPetrol | TAction | Reset petrol point |  | OnExecute→`ActionResetPetrolExecute` (0x005189C0) |
| AzioniAutoCal/ActionResetGas | TAction | Reset gas point |  | OnExecute→`ActionResetGasExecute` (0x005189CC) |
| AzioniAutoCal/ActionResetAll | TAction | Reset all |  | OnExecute→`ActionResetAllExecute` (0x005189D8) |
| AzioniAutoCal/ActionGoToMinCalibration | TAction | Go to min calibration |  | OnExecute→`ActionGoToMinCalibrationExecute` (0x005189E4) |
| AzioniAutoCal/ActionDeleteSelectedPoints | TAction | Delete selected points |  | OnExecute→`ActionDeleteSelectedPointsExecute` (0x00518E6C) |
| AzioniAutoCal/ActionExportChartPoint | TAction | Export to file... |  | OnExecute→`ActionExportChartPointExecute` (0x00519120) |
| AzioniAutoCal/SettingsAutocal | TAction | Settings |  |  |
| AzioniAutoCal/ActionResetKFactor | TAction | Reset k-factor... |  | OnExecute→`ActionResetKFactorExecute` (0x0051A070) |
| AzioniAutoCal/ActionFinishAutocal | TAction | FinishAutocal |  | OnExecute→`ActionFinishAutocalExecute` (0x0051A390) |
| AzioniAutoCal/ActionChangeColor | TAction | Customize chart colors... |  | OnExecute→`ActionChangeColorExecute` (0x0051AE78) |

## Controles com evento/ação (22)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| PanelBackground/PanelTop/SettingsImg | TImage |  |  | OnClick→`SettingsImgClick` (0x00519708); OnMouseEnter→`SettingsImgMouseEnter` (0x005196C0); OnMouseLeave→`SettingsImgMouseLeave` (0x005196E4) |
| PanelBackground/PanelTop/SettingsImgOut | TImage |  |  | OnMouseEnter→`SettingsImgMouseEnter` (0x005196C0) |
| PanelBackground/PanelTop/Panel3/CheckAutoCalEnable | TAebCheckBox | Abilita autocalibrazione__ |  | OnBeforeSetData→`CheckAutoCalEnableBeforeSetData` (0x0051A474) |
| PanelBackground/PanelTop/BtnMenu | TBitBtn |  |  | OnClick→`BtnMenuClick` (0x0051A038) |
| PanelBackground/PanelTop/Panel2/ButtonAutoMatch | TButton | Manual automatch |  | ActionAutoMatch.OnExecute→`ActionAutoMatchExecute` (0x005189B4) |
| PanelBackground/PanelBottom/PanelDbg/BtnDump | TButton | DUMP |  | OnClick→`BtnDumpClick` (0x005184D4) |
| PanelBackground/PanelBottom/PanelDbg/BtnDumpEE | TButton | DUMP EE |  | OnClick→`BtnDumpEEClick` (0x005184F4) |
| PanelBackground/PanelBottom/PanelDbg/DbgBtnRandom | TButton | Punti Random |  | OnClick→`DbgBtnRandomClick` (0x005189F0) |
| PanelBackground/PanelBottom/PanelDbg/TestAreas | TButton | Zone acquisite random |  | OnClick→`TestAreasClick` (0x00519538) |
| PanelBackground/PanelBottom/PanelDbg/BtnDeleteRandomPoint | TButton | Cancella / Delete / Cancelar |  | OnClick→`BtnDeleteRandomPointClick` (0x0051A27C) |
| PanelBackground/PanelBottom/PanelDbg/BtnFinishAutomatch | TButton | Finish automatch |  | OnClick→`BtnFinishAutomatchClick` (0x0051A454) |
| PanelBackground/PanelBottom/PanelDbg/BtnShowBand | TButton | ShowBand |  | OnClick→`BtnShowBandClick` (0x0051A74C) |
| PanelBackground/PanelActions/ChartPoint | TChart |  |  | OnMouseMove→`ChartPointMouseMove` (0x00518390) |
| PanelBackground/PanelActions/ChartData | TChart |  |  | OnClickAxis→`ChartDataClickAxis` (0x0051A008); OnClickSeries→`ChartDataClickSeries` (0x00518C3C); OnAfterDraw→`ChartDataAfterDraw` (0x0051A998); OnDblClick→`ChartDataDblClick` (0x00518E3C); OnKeyDown→`ChartDataKeyDown` (0x0051939C); OnKeyUp→`ChartDataKeyUp` (0x00519528); OnMouseMove→`ChartDataMouseMove` (0x00517F60); OnMouseUp→`ChartDataMouseUp` (0x00519134) |
| PanelBackground/PanelActions/Panel1/ButtonResetAll | TButton | Reset all |  | ActionResetAll.OnExecute→`ActionResetAllExecute` (0x005189D8) |
| PanelBackground/PanelActions/Panel1/ButtonResetGas | TButton | Reset gas point |  | ActionResetGas.OnExecute→`ActionResetGasExecute` (0x005189CC) |
| PanelBackground/PanelActions/Panel1/ButtonResetPetrol | TButton | Reset petrol point |  | ActionResetPetrol.OnExecute→`ActionResetPetrolExecute` (0x005189C0) |
| PanelBackground/PanelActions/Panel1/BtnMenuPanelLeft | TBitBtn |  |  | OnClick→`BtnMenuPanelLeftClick` (0x0051A24C) |
| PanelBackground/PanelActions/Panel1/ButtonResetK | TButton | Reset K |  | OnClick→`ActionResetKFactorExecute` (0x0051A070); ActionResetAll.OnExecute→`ActionResetAllExecute` (0x005189D8) |
| PanelBackground/PanelActions/ChartKLine | TChart |  |  | OnClickSeries→`ChartKLineClickSeries` (0x005176D0); OnDragOver→`ChartKLineDragOver` (0x005178CC); OnEndDrag→`ChartKLineEndDrag` (0x00517980); OnDblClick→`ChartKLineDblClick` (0x00517834); OnKeyDown→`ChartKLineKeyDown` (0x005179E0); OnKeyUp→`ChartKLineKeyUp` (0x00517DD0); OnMouseDown→`ChartKLineMouseDown` (0x00517DE8); OnMouseMove→`ChartKLineMouseMove` (0x00517E80) |
| PanelBackground/PanelActions/ChartKLine/KLine | TLineSeries |  |  | OnGetMarkText→`KLineGetMarkText` (0x005177C8) |
| MenuChartData | TPopupMenu |  |  | OnPopup→`MenuChartDataPopup` (0x005190A0) |

## Caixas de diálogo (componentes) (1)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) | Filtro |
|---|---|---|---|---|---|
| SaveDialogChart | TSaveDialog |  |  |  | Bitmap image\|*.bmp\|Windows metafile\|*.wmf |

## Temporizadores (1)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) | Intervalo ms |
|---|---|---|---|---|---|
| TimerMessageSwitchToGas | TTimer |  |  | OnTimer→`TimerMessageSwitchToGasTimer` (0x0051A168) |  |

## Handlers (47): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `ActionAutoCalRifExecute` | 0x005187A0 | 306 | INFERIDO | toca ChartKLine |  |  | BUTTONANNULLA, BUTTONOK, FINESTRACENTRICELLEAUTOCAL, MESSBADRIFMAP |
| `ActionAutoMatchExecute` | 0x005189B4 | 12 | INFERIDO | sem fatos diretos extraíveis (12 bytes; chama 1 funções sem nome) |  |  | Sleep |
| `ActionChangeColorExecute` | 0x0051AE78 | 111 | INFERIDO | chama TAutocalColorSettings.Create |  |  |  |
| `ActionDeleteSelectedPointsExecute` | 0x00518E6C | 562 | INFERIDO | chama APIs do Windows Sleep; toca GasPoint, PetrolPoint |  | Sleep |  |
| `ActionExportChartDataExecute` | 0x0051878C | 17 | INFERIDO | sem fatos diretos extraíveis (17 bytes; chama 1 funções sem nome) |  |  |  |
| `ActionExportChartPointExecute` | 0x00519120 | 17 | INFERIDO | sem fatos diretos extraíveis (17 bytes; chama 1 funções sem nome) |  |  |  |
| `ActionExportKChartExecute` | 0x005185FC | 17 | INFERIDO | sem fatos diretos extraíveis (17 bytes; chama 1 funções sem nome) |  |  |  |
| `ActionExportToKExecute` | 0x00518514 | 189 | INFERIDO | sem fatos diretos extraíveis (189 bytes; chama 9 funções sem nome) |  |  |  |
| `ActionFinishAutocalExecute` | 0x0051A390 | 195 | INFERIDO | chama APIs do Windows Sleep; chama TAebNumber::GetInt, TAebNumber::SetInt |  | Sleep |  |
| `ActionGoToMinCalibrationExecute` | 0x005189E4 | 12 | INFERIDO | sem fatos diretos extraíveis (12 bytes; chama 1 funções sem nome) |  |  |  |
| `ActionResetAllExecute` | 0x005189D8 | 12 | INFERIDO | sem fatos diretos extraíveis (12 bytes; chama 1 funções sem nome) |  |  | Sleep |
| `ActionResetGasExecute` | 0x005189CC | 12 | INFERIDO | sem fatos diretos extraíveis (12 bytes; chama 1 funções sem nome) |  |  | Sleep |
| `ActionResetKFactorExecute` | 0x0051A070 | 246 | INFERIDO | chama TAebVector::SetDouble |  |  |  |
| `ActionResetPetrolExecute` | 0x005189C0 | 12 | INFERIDO | sem fatos diretos extraíveis (12 bytes; chama 1 funções sem nome) |  |  | Sleep |
| `BtnDeleteRandomPointClick` | 0x0051A27C | 274 | INFERIDO | sem fatos diretos extraíveis (274 bytes; chama 0 funções sem nome) |  |  |  |
| `BtnDumpClick` | 0x005184D4 | 31 | INFERIDO | sem fatos diretos extraíveis (31 bytes; chama 1 funções sem nome) |  |  |  |
| `BtnDumpEEClick` | 0x005184F4 | 31 | INFERIDO | sem fatos diretos extraíveis (31 bytes; chama 1 funções sem nome) |  |  |  |
| `BtnFinishAutomatchClick` | 0x0051A454 | 31 | INFERIDO | chama TAebNumber::GetInt, TAebNumber::SetInt |  |  |  |
| `BtnMenuClick` | 0x0051A038 | 53 | INFERIDO | toca BtnMenu |  |  |  |
| `BtnMenuPanelLeftClick` | 0x0051A24C | 48 | INFERIDO | toca BtnMenu, BtnMenuPanelLeft |  |  |  |
| `BtnShowBandClick` | 0x0051A74C | 108 | INFERIDO | toca DbgEdit1 |  |  |  |
| `ChartDataAfterDraw` | 0x0051A998 | 527 | INFERIDO | toca ChartData |  |  |  |
| `ChartDataClickAxis` | 0x0051A008 | 45 | INFERIDO | toca ActionAutoCalRif |  |  |  |
| `ChartDataClickSeries` | 0x00518C3C | 292 | INFERIDO | toca CursorLimitMAP_MAX, CursorLimitMAP_MIN, DbgEdit1, GasPoint, PetrolPoint |  |  |  |
| `ChartDataDblClick` | 0x00518E3C | 45 | INFERIDO | toca GasPoint, PetrolPoint |  |  |  |
| `ChartDataKeyDown` | 0x0051939C | 380 | INFERIDO | toca ChartData, CursorLimitMAP_MAX, CursorLimitMAP_MIN, DbgEdit1, Resetpetrolpoint2 |  |  |  |
| `ChartDataKeyUp` | 0x00519528 | 14 | INFERIDO | sem fatos diretos extraíveis (14 bytes; chama 1 funções sem nome) |  |  |  |
| `ChartDataMouseMove` | 0x00517F60 | 1071 | INFERIDO | toca ChartData |  |  |  |
| `ChartDataMouseUp` | 0x00519134 | 14 | INFERIDO | sem fatos diretos extraíveis (14 bytes; chama 1 funções sem nome) |  |  |  |
| `ChartKLineClickSeries` | 0x005176D0 | 154 | INFERIDO | toca KLine |  |  |  |
| `ChartKLineDblClick` | 0x00517834 | 143 | INFERIDO | toca ChartKLine, KLine |  |  |  |
| `ChartKLineDragOver` | 0x005178CC | 167 | INFERIDO | toca ChartKLine, KLine |  |  |  |
| `ChartKLineEndDrag` | 0x00517980 | 95 | INFERIDO | chama TAebVector::SetDouble; toca ChartKLine, KLine |  |  |  |
| `ChartKLineKeyDown` | 0x005179E0 | 970 | INFERIDO | chama TAebVector::SetDouble; toca KLine |  |  |  |
| `ChartKLineKeyUp` | 0x00517DD0 | 21 | INFERIDO | sem fatos diretos extraíveis (21 bytes; chama 0 funções sem nome) |  |  |  |
| `ChartKLineMouseDown` | 0x00517DE8 | 150 | INFERIDO | toca ChartKLine, KLine |  |  |  |
| `ChartKLineMouseMove` | 0x00517E80 | 222 | INFERIDO | toca ChartKLine, KLine |  |  |  |
| `ChartPointMouseMove` | 0x00518390 | 323 | INFERIDO | chama TAebVector::GetInt; toca ChartPoint |  |  |  |
| `CheckAutoCalEnableBeforeSetData` | 0x0051A474 | 166 | INFERIDO | sem fatos diretos extraíveis (166 bytes; chama 5 funções sem nome) |  |  |  |
| `DbgBtnRandomClick` | 0x005189F0 | 381 | INFERIDO | chama TAebVector::SetDouble |  |  |  |
| `KLineGetMarkText` | 0x005177C8 | 107 | INFERIDO | sem fatos diretos extraíveis (107 bytes; chama 4 funções sem nome) |  |  |  |
| `MenuChartDataPopup` | 0x005190A0 | 62 | INFERIDO | toca ActionDeleteSelectedPoints, GasPoint, PetrolPoint |  |  |  |
| `SettingsImgClick` | 0x00519708 | 161 | INFERIDO | sem fatos diretos extraíveis (161 bytes; chama 5 funções sem nome) |  |  | AUTOCALCHANGECOLOR, AUTOCALSETTINGSCHARTK, AUTOCALSETTINGSDISABLEHBAND, AUTOCALSETTINGSRPMMAX |
| `SettingsImgMouseEnter` | 0x005196C0 | 34 | INFERIDO | sem fatos diretos extraíveis (34 bytes; chama 0 funções sem nome) |  |  |  |
| `SettingsImgMouseLeave` | 0x005196E4 | 34 | INFERIDO | sem fatos diretos extraíveis (34 bytes; chama 0 funções sem nome) |  |  |  |
| `TestAreasClick` | 0x00519538 | 392 | INFERIDO | toca AcqusitionAreas |  |  |  |
| `TimerMessageSwitchToGasTimer` | 0x0051A168 | 226 | INFERIDO | toca TimerMessageSwitchToGas, UserMessageLabel, UserMessageLabel2 |  |  |  |

### Cadeia de subfunções sem nome alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `ActionAutoCalRifExecute` (0x005187A0):
  - sub_515D00 (830 B): chama TAebNumber::GetDouble,TAebVector::GetDouble; toca ChartData,ChartKLine,ChartPoint,CursorLimitMAP_MAX
  - sub_5162F8 (1742 B): chama TAebProtocol::GetComFail,TAebProtocol::IsConnect,TAebVector::GetDouble; toca ChartData,GasPoint,GasPointPrev,KLine
  - sub_5188D4 (46 B)
  - sub_518904 (42 B)
  - sub_51AE10 (16 B)
  - sub_51B604 (779 B): textos BUTTONANNULLA,BUTTONOK,FINESTRACENTRICELLEAUTOCAL,MESSBADRIFMAP; chama TAboutBox.Create,TAebVector::GetDouble,TTranslatorExt::GetString; toca NRunAxes
  -   sub_41FAFC (16 B)
  -   sub_516044 (106 B): chama TAebVector::GetDouble; toca KLine
  -   sub_51745C (265 B): toca ChartData,ChartKLine,ChartPoint,KRunAxes
  -   sub_518B80 (185 B): toca CursorLimitMAP_MAX,CursorLimitMAP_MIN
  -   sub_40487C (45 B)
  -   sub_512254 (15 B)
  - … +22 (ver telas.json)
- `ActionAutoMatchExecute` (0x005189B4):
  - sub_517568 (359 B): APIs Sleep
  -   sub_40487C (45 B)
  -   sub_512264 (25 B): chama TAebProtocol::IsConnect
  -   sub_512280 (125 B): chama TAebProtocol::SendCommand
  -   sub_5162F8 (1742 B): chama TAebProtocol::GetComFail,TAebProtocol::IsConnect,TAebVector::GetDouble; toca ChartData,GasPoint,GasPointPrev,KLine
  -     sub_512254 (15 B)
  -     sub_41FAFC (16 B)
  -     sub_513208 (10 B)
  -     sub_516D7F (484 B): chama TAebNumber::GetInt,TAebNumber::SetInt; toca ActionAutoMatch,ActionFinishAutocal,LabNumAutoMatch
  -     sub_516D8C (471 B): chama TAebNumber::GetInt,TAebNumber::SetInt; toca ActionAutoMatch,ActionFinishAutocal,LabNumAutoMatch
  -     sub_516F64 (390 B): chama TAebNumber::GetInt,TAebVector::GetDouble,TAebVector::GetInt; toca NumGasPt,NumPetrPt
  -     sub_5170EC (255 B): toca ChartKLine,KLine,TimerMessageSwitchToGas
  - … +2 (ver telas.json)
- `ActionChangeColorExecute` (0x0051AE78):
  - sub_51988C (85 B)
  -   sub_510630 (46 B)
  -   sub_510660 (37 B)
  -   sub_51296C (543 B)
  -   sub_51995A (1420 B): chama TAutocalColorSettings.Create; toca AcqusitionAreas,ActionFinishAutocal,BtnDeleteRandomPoint,BtnFinishAutomatch
  -     sub_40487C (45 B)
  -     sub_4188D0 (90 B)
  -     sub_41FC9C (69 B)
  -     sub_42C3F0 (193 B)
  -     sub_510688 (42 B)
  -     sub_519EF4 (274 B): toca GasCurve,GasLine,NRunAxes,NumGasPt
  -     sub_8F3C14 (14 B)
- `ActionDeleteSelectedPointsExecute` (0x00518E6C):
  - sub_40487C (45 B)
  - sub_512858 (275 B): chama TAebVector::SetDefValue
  - sub_5162F8 (1742 B): chama TAebProtocol::GetComFail,TAebProtocol::IsConnect,TAebVector::GetDouble; toca ChartData,GasPoint,GasPointPrev,KLine
  - sub_518D94 (98 B)
  - sub_518DF8 (65 B)
  -   sub_512300 (119 B): chama TAebProtocol::SendCommand
  -   sub_975FD0 (74 B)
  -   sub_41FAFC (16 B)
  -   sub_512254 (15 B)
  -   sub_513208 (10 B)
  -   sub_516D7F (484 B): chama TAebNumber::GetInt,TAebNumber::SetInt; toca ActionAutoMatch,ActionFinishAutocal,LabNumAutoMatch
  -   sub_516D8C (471 B): chama TAebNumber::GetInt,TAebNumber::SetInt; toca ActionAutoMatch,ActionFinishAutocal,LabNumAutoMatch
  - … +9 (ver telas.json)
- `ActionExportChartDataExecute` (0x0051878C):
  - sub_518610 (377 B): toca SaveDialogChart
  -   sub_40487C (45 B)
- `ActionExportChartPointExecute` (0x00519120):
  - sub_518610 (377 B): toca SaveDialogChart
  -   sub_40487C (45 B)
- `ActionExportKChartExecute` (0x005185FC):
  - sub_518610 (377 B): toca SaveDialogChart
  -   sub_40487C (45 B)
- `ActionExportToKExecute` (0x00518514):
  - sub_40487C (45 B)
  - sub_512624 (226 B): chama TAebVector::GetDouble
  - sub_516044 (106 B): chama TAebVector::GetDouble; toca KLine
  - sub_5162F8 (1742 B): chama TAebProtocol::GetComFail,TAebProtocol::IsConnect,TAebVector::GetDouble; toca ChartData,GasPoint,GasPointPrev,KLine
  - sub_5185D4 (39 B)
  -   sub_42EE14 (100 B)
  -   sub_42F338 (7 B)
  -   sub_512708 (167 B): chama TAebVector::GetDouble
  -   sub_5127B0 (90 B)
  -   sub_51280C (72 B)
  -   sub_513208 (10 B)
  -   sub_41FAFC (16 B)
  - … +16 (ver telas.json)
- `ActionFinishAutocalExecute` (0x0051A390):
  - sub_40487C (45 B)
  - sub_5162F8 (1742 B): chama TAebProtocol::GetComFail,TAebProtocol::IsConnect,TAebVector::GetDouble; toca ChartData,GasPoint,GasPointPrev,KLine
  -   sub_41FAFC (16 B)
  -   sub_512254 (15 B)
  -   sub_513208 (10 B)
  -   sub_516D7F (484 B): chama TAebNumber::GetInt,TAebNumber::SetInt; toca ActionAutoMatch,ActionFinishAutocal,LabNumAutoMatch
  -   sub_516D8C (471 B): chama TAebNumber::GetInt,TAebNumber::SetInt; toca ActionAutoMatch,ActionFinishAutocal,LabNumAutoMatch
  -   sub_516F64 (390 B): chama TAebNumber::GetInt,TAebVector::GetDouble,TAebVector::GetInt; toca NumGasPt,NumPetrPt
  -   sub_5170EC (255 B): toca ChartKLine,KLine,TimerMessageSwitchToGas
  -   sub_5171EC (102 B): chama TAebProtocol::GetComFail
  -   sub_51ABA8 (147 B)
  -     sub_41FB40 (34 B): chama TAebProtocol::GetPacket
  - … +4 (ver telas.json)
- `ActionGoToMinCalibrationExecute` (0x005189E4):
  - sub_5185D4 (39 B)
- `ActionResetAllExecute` (0x005189D8):
  - sub_517568 (359 B): APIs Sleep
  -   sub_40487C (45 B)
  -   sub_512264 (25 B): chama TAebProtocol::IsConnect
  -   sub_512280 (125 B): chama TAebProtocol::SendCommand
  -   sub_5162F8 (1742 B): chama TAebProtocol::GetComFail,TAebProtocol::IsConnect,TAebVector::GetDouble; toca ChartData,GasPoint,GasPointPrev,KLine
  -     sub_512254 (15 B)
  -     sub_41FAFC (16 B)
  -     sub_513208 (10 B)
  -     sub_516D7F (484 B): chama TAebNumber::GetInt,TAebNumber::SetInt; toca ActionAutoMatch,ActionFinishAutocal,LabNumAutoMatch
  -     sub_516D8C (471 B): chama TAebNumber::GetInt,TAebNumber::SetInt; toca ActionAutoMatch,ActionFinishAutocal,LabNumAutoMatch
  -     sub_516F64 (390 B): chama TAebNumber::GetInt,TAebVector::GetDouble,TAebVector::GetInt; toca NumGasPt,NumPetrPt
  -     sub_5170EC (255 B): toca ChartKLine,KLine,TimerMessageSwitchToGas
  - … +2 (ver telas.json)
- `ActionResetGasExecute` (0x005189CC):
  - sub_517568 (359 B): APIs Sleep
  -   sub_40487C (45 B)
  -   sub_512264 (25 B): chama TAebProtocol::IsConnect
  -   sub_512280 (125 B): chama TAebProtocol::SendCommand
  -   sub_5162F8 (1742 B): chama TAebProtocol::GetComFail,TAebProtocol::IsConnect,TAebVector::GetDouble; toca ChartData,GasPoint,GasPointPrev,KLine
  -     sub_512254 (15 B)
  -     sub_41FAFC (16 B)
  -     sub_513208 (10 B)
  -     sub_516D7F (484 B): chama TAebNumber::GetInt,TAebNumber::SetInt; toca ActionAutoMatch,ActionFinishAutocal,LabNumAutoMatch
  -     sub_516D8C (471 B): chama TAebNumber::GetInt,TAebNumber::SetInt; toca ActionAutoMatch,ActionFinishAutocal,LabNumAutoMatch
  -     sub_516F64 (390 B): chama TAebNumber::GetInt,TAebVector::GetDouble,TAebVector::GetInt; toca NumGasPt,NumPetrPt
  -     sub_5170EC (255 B): toca ChartKLine,KLine,TimerMessageSwitchToGas
  - … +2 (ver telas.json)
- `ActionResetKFactorExecute` (0x0051A070):
  - sub_40487C (45 B)
  - sub_513208 (10 B)
  - sub_516044 (106 B): chama TAebVector::GetDouble; toca KLine
  - sub_5162F8 (1742 B): chama TAebProtocol::GetComFail,TAebProtocol::IsConnect,TAebVector::GetDouble; toca ChartData,GasPoint,GasPointPrev,KLine
  -   sub_41FAFC (16 B)
  -   sub_512254 (15 B)
  -   sub_516D7F (484 B): chama TAebNumber::GetInt,TAebNumber::SetInt; toca ActionAutoMatch,ActionFinishAutocal,LabNumAutoMatch
  -   sub_516D8C (471 B): chama TAebNumber::GetInt,TAebNumber::SetInt; toca ActionAutoMatch,ActionFinishAutocal,LabNumAutoMatch
  -   sub_516F64 (390 B): chama TAebNumber::GetInt,TAebVector::GetDouble,TAebVector::GetInt; toca NumGasPt,NumPetrPt
  -   sub_5170EC (255 B): toca ChartKLine,KLine,TimerMessageSwitchToGas
  -   sub_5171EC (102 B): chama TAebProtocol::GetComFail
  -   sub_51ABA8 (147 B)
  - … +5 (ver telas.json)
- `ActionResetPetrolExecute` (0x005189C0):
  - sub_517568 (359 B): APIs Sleep
  -   sub_40487C (45 B)
  -   sub_512264 (25 B): chama TAebProtocol::IsConnect
  -   sub_512280 (125 B): chama TAebProtocol::SendCommand
  -   sub_5162F8 (1742 B): chama TAebProtocol::GetComFail,TAebProtocol::IsConnect,TAebVector::GetDouble; toca ChartData,GasPoint,GasPointPrev,KLine
  -     sub_512254 (15 B)
  -     sub_41FAFC (16 B)
  -     sub_513208 (10 B)
  -     sub_516D7F (484 B): chama TAebNumber::GetInt,TAebNumber::SetInt; toca ActionAutoMatch,ActionFinishAutocal,LabNumAutoMatch
  -     sub_516D8C (471 B): chama TAebNumber::GetInt,TAebNumber::SetInt; toca ActionAutoMatch,ActionFinishAutocal,LabNumAutoMatch
  -     sub_516F64 (390 B): chama TAebNumber::GetInt,TAebVector::GetDouble,TAebVector::GetInt; toca NumGasPt,NumPetrPt
  -     sub_5170EC (255 B): toca ChartKLine,KLine,TimerMessageSwitchToGas
  - … +2 (ver telas.json)
- `BtnDumpClick` (0x005184D4):
  - sub_41F068 (2706 B): chama TAebMatrix::GetDefValue,TAebMatrix::GetDouble,TAebNumber::GetDouble,TAebVector::GetDefValue
  -   sub_40487C (45 B)
  -   sub_41E5D0 (450 B)
  -     sub_41E8D0 (96 B)
- `BtnDumpEEClick` (0x005184F4):
  - sub_41F068 (2706 B): chama TAebMatrix::GetDefValue,TAebMatrix::GetDouble,TAebNumber::GetDouble,TAebVector::GetDefValue
  -   sub_40487C (45 B)
  -   sub_41E5D0 (450 B)
  -     sub_41E8D0 (96 B)
- `BtnShowBandClick` (0x0051A74C):
  - sub_40487C (45 B)
  - sub_51A614 (311 B): chama TAebVector::GetDouble
  -   sub_41FAFC (16 B)
  -   sub_51A51C (247 B): chama TAebVector::GetDouble; toca CurrentBand
- `ChartDataAfterDraw` (0x0051A998):
  - sub_40487C (45 B)
  - sub_51A7B8 (162 B): chama TAebVector::GetDouble; toca ChartData
  - sub_51A85C (184 B): chama TAebVector::GetDouble
  - sub_51A918 (127 B): chama TAebNumber::GetInt,TAebVector::GetInt
  -   sub_41FAFC (16 B)
- `ChartDataClickSeries` (0x00518C3C):
  - sub_518B80 (185 B): toca CursorLimitMAP_MAX,CursorLimitMAP_MIN
  - sub_518D94 (98 B)
  - sub_518DF8 (65 B)
- `ChartDataDblClick` (0x00518E3C):
  - sub_518D60 (52 B)
  -   sub_518D94 (98 B)
- `ChartDataKeyDown` (0x0051939C):
  - sub_518B80 (185 B): toca CursorLimitMAP_MAX,CursorLimitMAP_MIN
  - sub_519354 (72 B)
  -   sub_519224 (291 B): toca ChartData,CursorLimitMAP_MAX,CursorLimitMAP_MIN
- `ChartDataKeyUp` (0x00519528):
  - sub_519144 (221 B): chama TAebNumber::SetDouble; toca ChartData,CursorLimitMAP_MAX,DbgEdit1
- `ChartDataMouseMove` (0x00517F60):
  - sub_40487C (45 B)
  - sub_428774 (150 B)
  - sub_519354 (72 B)
  -   sub_4288CC (24 B)
  -   sub_519224 (291 B): toca ChartData,CursorLimitMAP_MAX,CursorLimitMAP_MIN
- `ChartDataMouseUp` (0x00519134):
  - sub_519144 (221 B): chama TAebNumber::SetDouble; toca ChartData,CursorLimitMAP_MAX,DbgEdit1
- `ChartKLineClickSeries` (0x005176D0):
  - sub_513208 (10 B)
- `ChartKLineDragOver` (0x005178CC):
  - sub_428774 (150 B)
  -   sub_4288CC (24 B)
- `ChartKLineEndDrag` (0x00517980):
  - sub_428774 (150 B)
  -   sub_4288CC (24 B)
- `ChartKLineKeyDown` (0x005179E0):
  - sub_428774 (150 B)
  - sub_513208 (10 B)
  -   sub_4288CC (24 B)
- `ChartKLineMouseDown` (0x00517DE8):
  - sub_513208 (10 B)
- `ChartKLineMouseMove` (0x00517E80):
  - sub_40487C (45 B)
  - sub_428774 (150 B)
  -   sub_4288CC (24 B)
- `ChartPointMouseMove` (0x00518390):
  - sub_40487C (45 B)
- `CheckAutoCalEnableBeforeSetData` (0x0051A474):
  - sub_40487C (45 B)
- `DbgBtnRandomClick` (0x005189F0):
  - sub_513208 (10 B)
- `MenuChartDataPopup` (0x005190A0):
  - sub_5190E0 (63 B)
  -   sub_518DF8 (65 B)
- `SettingsImgClick` (0x00519708):
  - sub_513630 (1880 B): textos AUTOCALCHANGECOLOR,AUTOCALSETTINGSCHARTK,AUTOCALSETTINGSDISABLEHBAND,AUTOCALSETTINGSRPMMAX; chama TAboutBox.Create,TTranslatorExt::GetString
  - sub_5197AC (46 B)
  - sub_5197DC (42 B)
  - sub_51988C (85 B)
  -   sub_40487C (45 B)
  -   sub_41E930 (243 B)
  -   sub_41EA40 (243 B)
  -   sub_512244 (15 B)
  -   sub_51296C (543 B)
  -   sub_51AF7C (37 B)
  -   sub_510630 (46 B)
  -   sub_510660 (37 B)
  - … +10 (ver telas.json)
- `TimerMessageSwitchToGasTimer` (0x0051A168):
  - sub_40487C (45 B)
