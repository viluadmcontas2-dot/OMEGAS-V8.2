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

| Handler | Endereço | Bytes | Evidência | O que faz | Textos (chaves i18n) | APIs | Chama |
|---|---|---:|---|---|---|---|---|
| `ActionAutoCalRifExecute` | 0x005187A0 | 306 | INFERIDO | Handler `ActionAutoCalRifExecute`; mostra/usa textos BUTTONANNULLA, BUTTONOK, FINESTRACENTRICELLEAUTOCAL, MESSBADRIFMAP, MESSBADRIFTEMPI; toca campos/controles: ChartKLine | BUTTONANNULLA, BUTTONOK, FINESTRACENTRICELLEAUTOCAL, MESSBADRIFMAP, MESSBADRIFTEMPI |  |  |
| `ActionAutoMatchExecute` | 0x005189B4 | 12 | INFERIDO | Handler `ActionAutoMatchExecute`; chama APIs do Windows: KERNEL32.DLL!Sleep |  | KERNEL32.DLL!Sleep |  |
| `ActionChangeColorExecute` | 0x0051AE78 | 111 | INFERIDO | Handler `ActionChangeColorExecute`; chama: TAutocalColorSettings.vmt[0x38] |  |  | TAutocalColorSettings.vmt[0x38] |
| `ActionDeleteSelectedPointsExecute` | 0x00518E6C | 562 | INFERIDO | Handler `ActionDeleteSelectedPointsExecute`; chama APIs do Windows: KERNEL32.DLL!Sleep; chama: thunk -> KERNEL32.DLL!Sleep; toca campos/controles: GasPoint, PetrolPoint |  | KERNEL32.DLL!Sleep | thunk -> KERNEL32.DLL!Sleep |
| `ActionExportChartDataExecute` | 0x0051878C | 17 | INFERIDO | Handler `ActionExportChartDataExecute`; sem fatos extraíveis automaticamente (17 bytes) |  |  |  |
| `ActionExportChartPointExecute` | 0x00519120 | 17 | INFERIDO | Handler `ActionExportChartPointExecute`; sem fatos extraíveis automaticamente (17 bytes) |  |  |  |
| `ActionExportKChartExecute` | 0x005185FC | 17 | INFERIDO | Handler `ActionExportKChartExecute`; sem fatos extraíveis automaticamente (17 bytes) |  |  |  |
| `ActionExportToKExecute` | 0x00518514 | 189 | INFERIDO | Handler `ActionExportToKExecute`; sem fatos extraíveis automaticamente (189 bytes) |  |  |  |
| `ActionFinishAutocalExecute` | 0x0051A390 | 195 | INFERIDO | Handler `ActionFinishAutocalExecute`; chama APIs do Windows: KERNEL32.DLL!Sleep; chama: TAebNumber::GetInt, TAebNumber::SetInt, thunk -> KERNEL32.DLL!Sleep |  | KERNEL32.DLL!Sleep | TAebNumber::GetInt, TAebNumber::SetInt, thunk -> KERNEL32.DLL!Sleep |
| `ActionGoToMinCalibrationExecute` | 0x005189E4 | 12 | INFERIDO | Handler `ActionGoToMinCalibrationExecute`; sem fatos extraíveis automaticamente (12 bytes) |  |  |  |
| `ActionResetAllExecute` | 0x005189D8 | 12 | INFERIDO | Handler `ActionResetAllExecute`; chama APIs do Windows: KERNEL32.DLL!Sleep |  | KERNEL32.DLL!Sleep |  |
| `ActionResetGasExecute` | 0x005189CC | 12 | INFERIDO | Handler `ActionResetGasExecute`; chama APIs do Windows: KERNEL32.DLL!Sleep |  | KERNEL32.DLL!Sleep |  |
| `ActionResetKFactorExecute` | 0x0051A070 | 246 | INFERIDO | Handler `ActionResetKFactorExecute`; chama: TAebVector::SetDouble |  |  | TAebVector::SetDouble |
| `ActionResetPetrolExecute` | 0x005189C0 | 12 | INFERIDO | Handler `ActionResetPetrolExecute`; chama APIs do Windows: KERNEL32.DLL!Sleep |  | KERNEL32.DLL!Sleep |  |
| `BtnDeleteRandomPointClick` | 0x0051A27C | 274 | INFERIDO | Handler `BtnDeleteRandomPointClick`; sem fatos extraíveis automaticamente (274 bytes) |  |  |  |
| `BtnDumpClick` | 0x005184D4 | 31 | INFERIDO | Handler `BtnDumpClick`; sem fatos extraíveis automaticamente (31 bytes) |  |  |  |
| `BtnDumpEEClick` | 0x005184F4 | 31 | INFERIDO | Handler `BtnDumpEEClick`; sem fatos extraíveis automaticamente (31 bytes) |  |  |  |
| `BtnFinishAutomatchClick` | 0x0051A454 | 31 | INFERIDO | Handler `BtnFinishAutomatchClick`; chama: TAebNumber::GetInt, TAebNumber::SetInt |  |  | TAebNumber::GetInt, TAebNumber::SetInt |
| `BtnMenuClick` | 0x0051A038 | 53 | INFERIDO | Handler `BtnMenuClick`; toca campos/controles: BtnMenu |  |  |  |
| `BtnMenuPanelLeftClick` | 0x0051A24C | 48 | INFERIDO | Handler `BtnMenuPanelLeftClick`; toca campos/controles: BtnMenu, BtnMenuPanelLeft |  |  |  |
| `BtnShowBandClick` | 0x0051A74C | 108 | INFERIDO | Handler `BtnShowBandClick`; toca campos/controles: DbgEdit1 |  |  |  |
| `ChartDataAfterDraw` | 0x0051A998 | 527 | INFERIDO | Handler `ChartDataAfterDraw`; toca campos/controles: ChartData |  |  |  |
| `ChartDataClickAxis` | 0x0051A008 | 45 | INFERIDO | Handler `ChartDataClickAxis`; toca campos/controles: ActionAutoCalRif |  |  |  |
| `ChartDataClickSeries` | 0x00518C3C | 292 | INFERIDO | Handler `ChartDataClickSeries`; toca campos/controles: CursorLimitMAP_MAX, CursorLimitMAP_MIN, DbgEdit1, GasPoint, PetrolPoint |  |  |  |
| `ChartDataDblClick` | 0x00518E3C | 45 | INFERIDO | Handler `ChartDataDblClick`; toca campos/controles: GasPoint, PetrolPoint |  |  |  |
| `ChartDataKeyDown` | 0x0051939C | 380 | INFERIDO | Handler `ChartDataKeyDown`; toca campos/controles: ChartData, CursorLimitMAP_MAX, CursorLimitMAP_MIN, DbgEdit1, Resetpetrolpoint2 |  |  |  |
| `ChartDataKeyUp` | 0x00519528 | 14 | INFERIDO | Handler `ChartDataKeyUp`; sem fatos extraíveis automaticamente (14 bytes) |  |  |  |
| `ChartDataMouseMove` | 0x00517F60 | 1071 | INFERIDO | Handler `ChartDataMouseMove`; toca campos/controles: ChartData |  |  |  |
| `ChartDataMouseUp` | 0x00519134 | 14 | INFERIDO | Handler `ChartDataMouseUp`; sem fatos extraíveis automaticamente (14 bytes) |  |  |  |
| `ChartKLineClickSeries` | 0x005176D0 | 154 | INFERIDO | Handler `ChartKLineClickSeries`; toca campos/controles: KLine |  |  |  |
| `ChartKLineDblClick` | 0x00517834 | 143 | INFERIDO | Handler `ChartKLineDblClick`; toca campos/controles: ChartKLine, KLine |  |  |  |
| `ChartKLineDragOver` | 0x005178CC | 167 | INFERIDO | Handler `ChartKLineDragOver`; toca campos/controles: ChartKLine, KLine |  |  |  |
| `ChartKLineEndDrag` | 0x00517980 | 95 | INFERIDO | Handler `ChartKLineEndDrag`; chama: TAebVector::SetDouble; toca campos/controles: ChartKLine, KLine |  |  | TAebVector::SetDouble |
| `ChartKLineKeyDown` | 0x005179E0 | 970 | INFERIDO | Handler `ChartKLineKeyDown`; chama: TAebVector::SetDouble; toca campos/controles: KLine |  |  | TAebVector::SetDouble |
| `ChartKLineKeyUp` | 0x00517DD0 | 21 | INFERIDO | Handler `ChartKLineKeyUp`; sem fatos extraíveis automaticamente (21 bytes) |  |  |  |
| `ChartKLineMouseDown` | 0x00517DE8 | 150 | INFERIDO | Handler `ChartKLineMouseDown`; toca campos/controles: ChartKLine, KLine |  |  |  |
| `ChartKLineMouseMove` | 0x00517E80 | 222 | INFERIDO | Handler `ChartKLineMouseMove`; toca campos/controles: ChartKLine, KLine |  |  |  |
| `ChartPointMouseMove` | 0x00518390 | 323 | INFERIDO | Handler `ChartPointMouseMove`; chama: TAebVector::GetInt; toca campos/controles: ChartPoint |  |  | TAebVector::GetInt |
| `CheckAutoCalEnableBeforeSetData` | 0x0051A474 | 166 | INFERIDO | Handler `CheckAutoCalEnableBeforeSetData`; sem fatos extraíveis automaticamente (166 bytes) |  |  |  |
| `DbgBtnRandomClick` | 0x005189F0 | 381 | INFERIDO | Handler `DbgBtnRandomClick`; chama: TAebVector::SetDouble |  |  | TAebVector::SetDouble |
| `KLineGetMarkText` | 0x005177C8 | 107 | INFERIDO | Handler `KLineGetMarkText`; sem fatos extraíveis automaticamente (107 bytes) |  |  |  |
| `MenuChartDataPopup` | 0x005190A0 | 62 | INFERIDO | Handler `MenuChartDataPopup`; toca campos/controles: ActionDeleteSelectedPoints, GasPoint, PetrolPoint |  |  |  |
| `SettingsImgClick` | 0x00519708 | 161 | INFERIDO | Handler `SettingsImgClick`; mostra/usa textos AUTOCALCHANGECOLOR, AUTOCALSETTINGSCHARTK, AUTOCALSETTINGSDISABLEHBAND, AUTOCALSETTINGSRPMMAX, AUTOCALSETTINGSSHOWPOINT, AUTOCALSETTINGSSHOWSTATUS | AUTOCALCHANGECOLOR, AUTOCALSETTINGSCHARTK, AUTOCALSETTINGSDISABLEHBAND, AUTOCALSETTINGSRPMMAX, AUTOCALSETTINGSSHOWPOINT, AUTOCALSETTINGSSHOWSTATUS |  |  |
| `SettingsImgMouseEnter` | 0x005196C0 | 34 | INFERIDO | Handler `SettingsImgMouseEnter`; sem fatos extraíveis automaticamente (34 bytes) |  |  |  |
| `SettingsImgMouseLeave` | 0x005196E4 | 34 | INFERIDO | Handler `SettingsImgMouseLeave`; sem fatos extraíveis automaticamente (34 bytes) |  |  |  |
| `TestAreasClick` | 0x00519538 | 392 | INFERIDO | Handler `TestAreasClick`; toca campos/controles: AcqusitionAreas |  |  |  |
| `TimerMessageSwitchToGasTimer` | 0x0051A168 | 226 | INFERIDO | Handler `TimerMessageSwitchToGasTimer`; toca campos/controles: TimerMessageSwitchToGas, UserMessageLabel, UserMessageLabel2 |  |  |  |
