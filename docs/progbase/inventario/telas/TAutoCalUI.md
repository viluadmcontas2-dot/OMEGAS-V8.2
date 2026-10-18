# TAutoCalUI (recurso `TAUTOCALUI`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

**Papel:** frame (OBS-COD). Frame da calibração automática (AutoCal): gráficos de dados e K, painel de pontos, menus de reset/exportação, mensagens ao usuário (detalhe: abas 17-33).

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
| `ActionAutoCalRifExecute` | 0x005187A0 | 306 | DERIVADO | Menu "Modify map refs": abre TFormRifAutocal (referências do AutoCal). OBS-COD. |  |  | BUTTONANNULLA, BUTTONOK, FINESTRACENTRICELLEAUTOCAL, MESSBADRIFMAP |
| `ActionAutoMatchExecute` | 0x005189B4 | 12 | OBS-COD | Atalho de 12 B que encaminha para a rotina interna do AutoCal (detalhe é da tarefa de abas 17-33/protocolo). OBS-COD (salto). |  |  | Sleep |
| `ActionChangeColorExecute` | 0x0051AE78 | 111 | OBS-COD | Menu "Customize chart colors…": abre TAutocalColorSettings. OBS-COD. |  |  |  |
| `ActionDeleteSelectedPointsExecute` | 0x00518E6C | 562 | DERIVADO | Menu "Delete selected points": remove pontos selecionados de gás/gasolina das séries do gráfico. OBS-COD (campos). |  | Sleep |  |
| `ActionExportChartDataExecute` | 0x0051878C | 17 | OBS-COD | Exporta os dados da série do gráfico para arquivo (17 B: encaminha para a rotina de exportação). OBS-COD (salto). |  |  |  |
| `ActionExportChartPointExecute` | 0x00519120 | 17 | OBS-COD | Exporta os dados da série do gráfico para arquivo (17 B: encaminha para a rotina de exportação). OBS-COD (salto). |  |  |  |
| `ActionExportKChartExecute` | 0x005185FC | 17 | OBS-COD | Exporta os dados da série do gráfico para arquivo (17 B: encaminha para a rotina de exportação). OBS-COD (salto). |  |  |  |
| `ActionExportToKExecute` | 0x00518514 | 189 | INFERIDO | Exporta os pontos para a tabela K (ExportToK). INFERIDO. |  |  |  |
| `ActionFinishAutocalExecute` | 0x0051A390 | 195 | DERIVADO | Menu/ação "Finalizar AutoCal": lê/escreve flags do módulo (TAebNumber GetInt/SetInt) para encerrar a calibração automática. Escreve na ECU. OBS-COD (chamadas). |  | Sleep |  |
| `ActionGoToMinCalibrationExecute` | 0x005189E4 | 12 | OBS-COD | Atalho de 12 B que encaminha para a rotina interna do AutoCal (detalhe é da tarefa de abas 17-33/protocolo). OBS-COD (salto). |  |  |  |
| `ActionResetAllExecute` | 0x005189D8 | 12 | OBS-COD | Atalho de 12 B que encaminha para a rotina interna do AutoCal (detalhe é da tarefa de abas 17-33/protocolo). OBS-COD (salto). |  |  | Sleep |
| `ActionResetGasExecute` | 0x005189CC | 12 | OBS-COD | Atalho de 12 B que encaminha para a rotina interna do AutoCal (detalhe é da tarefa de abas 17-33/protocolo). OBS-COD (salto). |  |  | Sleep |
| `ActionResetKFactorExecute` | 0x0051A070 | 246 | DERIVADO | Menu "Reset k-factor…": regrava o fator K (TAebVector::SetDouble). Escreve na ECU. OBS-COD. |  |  |  |
| `ActionResetPetrolExecute` | 0x005189C0 | 12 | OBS-COD | Atalho de 12 B que encaminha para a rotina interna do AutoCal (detalhe é da tarefa de abas 17-33/protocolo). OBS-COD (salto). |  |  | Sleep |
| `BtnDeleteRandomPointClick` | 0x0051A27C | 274 | INFERIDO | Ao clicar em BtnDeleteRandomPoint «Cancelar»: corpo de 274 bytes sem fatos diretos (chama 0 funções sem nome). |  |  |  |
| `BtnDumpClick` | 0x005184D4 | 31 | INFERIDO | Ao clicar em BtnDump «DUMP»: corpo de 31 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `BtnDumpEEClick` | 0x005184F4 | 31 | INFERIDO | Ao clicar em BtnDumpEE «DUMP EE»: corpo de 31 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `BtnFinishAutomatchClick` | 0x0051A454 | 31 | DERIVADO | Botão "Finish automatch": encerra o automatch (GetInt/SetInt em TAebNumber). OBS-COD. |  |  |  |
| `BtnMenuClick` | 0x0051A038 | 53 | INFERIDO | Ao clicar em BtnMenu: mexe em BtnMenu. |  |  |  |
| `BtnMenuPanelLeftClick` | 0x0051A24C | 48 | INFERIDO | Ao clicar em BtnMenuPanelLeft: mexe em BtnMenu, BtnMenuPanelLeft. |  |  |  |
| `BtnShowBandClick` | 0x0051A74C | 108 | INFERIDO | Ao clicar em BtnShowBand «ShowBand»: mexe em DbgEdit1. |  |  |  |
| `ChartDataAfterDraw` | 0x0051A998 | 527 | INFERIDO | Após desenhar ChartData: mexe em ChartData. |  |  |  |
| `ChartDataClickAxis` | 0x0051A008 | 45 | INFERIDO | Ao clicar num eixo de ChartData: mexe em ActionAutoCalRif. |  |  |  |
| `ChartDataClickSeries` | 0x00518C3C | 292 | INFERIDO | Ao clicar numa série de ChartData: mexe em CursorLimitMAP_MAX, CursorLimitMAP_MIN, DbgEdit1, GasPoint, PetrolPoint. |  |  |  |
| `ChartDataDblClick` | 0x00518E3C | 45 | INFERIDO | Ao dar duplo clique em ChartData: mexe em GasPoint, PetrolPoint. |  |  |  |
| `ChartDataKeyDown` | 0x0051939C | 380 | INFERIDO | Ao pressionar tecla em ChartData: mexe em ChartData, CursorLimitMAP_MAX, CursorLimitMAP_MIN, DbgEdit1, Resetpetrolpoint2. |  |  |  |
| `ChartDataKeyUp` | 0x00519528 | 14 | INFERIDO | Ao soltar tecla em ChartData: corpo de 14 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `ChartDataMouseMove` | 0x00517F60 | 1071 | INFERIDO | Ao mover o mouse sobre ChartData: mexe em ChartData. |  |  |  |
| `ChartDataMouseUp` | 0x00519134 | 14 | INFERIDO | Ao soltar o mouse em ChartData: corpo de 14 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `ChartKLineClickSeries` | 0x005176D0 | 154 | INFERIDO | Ao clicar numa série de ChartKLine: mexe em KLine. |  |  |  |
| `ChartKLineDblClick` | 0x00517834 | 143 | INFERIDO | Ao dar duplo clique em ChartKLine: mexe em ChartKLine, KLine. |  |  |  |
| `ChartKLineDragOver` | 0x005178CC | 167 | INFERIDO | Evento OnDragOver de ChartKLine: mexe em ChartKLine, KLine. |  |  |  |
| `ChartKLineEndDrag` | 0x00517980 | 95 | INFERIDO | Evento OnEndDrag de ChartKLine: chama TAebVector::SetDouble; mexe em ChartKLine, KLine. |  |  |  |
| `ChartKLineKeyDown` | 0x005179E0 | 970 | INFERIDO | Ao pressionar tecla em ChartKLine: chama TAebVector::SetDouble; mexe em KLine. |  |  |  |
| `ChartKLineKeyUp` | 0x00517DD0 | 21 | INFERIDO | Ao soltar tecla em ChartKLine: corpo de 21 bytes sem fatos diretos (chama 0 funções sem nome). |  |  |  |
| `ChartKLineMouseDown` | 0x00517DE8 | 150 | INFERIDO | Ao pressionar o mouse em ChartKLine: mexe em ChartKLine, KLine. |  |  |  |
| `ChartKLineMouseMove` | 0x00517E80 | 222 | INFERIDO | Ao mover o mouse sobre ChartKLine: mexe em ChartKLine, KLine. |  |  |  |
| `ChartPointMouseMove` | 0x00518390 | 323 | INFERIDO | Ao mover o mouse sobre ChartPoint: chama TAebVector::GetInt; mexe em ChartPoint. |  |  |  |
| `CheckAutoCalEnableBeforeSetData` | 0x0051A474 | 166 | INFERIDO | Evento OnBeforeSetData de CheckAutoCalEnable «Abilita autocalibrazione__»: corpo de 166 bytes sem fatos diretos (chama 5 funções sem nome). |  |  |  |
| `DbgBtnRandomClick` | 0x005189F0 | 381 | INFERIDO | Ao clicar em DbgBtnRandom «Punti Random»: chama TAebVector::SetDouble. |  |  |  |
| `KLineGetMarkText` | 0x005177C8 | 107 | INFERIDO | Evento OnGetMarkText de KLine: corpo de 107 bytes sem fatos diretos (chama 4 funções sem nome). |  |  |  |
| `MenuChartDataPopup` | 0x005190A0 | 62 | INFERIDO | Ao abrir o menu de contexto MenuChartData: mexe em ActionDeleteSelectedPoints, GasPoint, PetrolPoint. |  |  |  |
| `SettingsImgClick` | 0x00519708 | 161 | DERIVADO | Ícone de configurações: abre TAutoCalSettings (cor do gráfico, k, banda, rpm, pontos). OBS-COD. |  |  | AUTOCALCHANGECOLOR, AUTOCALSETTINGSCHARTK, AUTOCALSETTINGSDISABLEHBAND, AUTOCALSETTINGSRPMMAX |
| `SettingsImgMouseEnter` | 0x005196C0 | 34 | INFERIDO | Ao entrar com o mouse em SettingsImg (+1 componentes): corpo de 34 bytes sem fatos diretos (chama 0 funções sem nome). |  |  |  |
| `SettingsImgMouseLeave` | 0x005196E4 | 34 | INFERIDO | Ao sair com o mouse de SettingsImg: corpo de 34 bytes sem fatos diretos (chama 0 funções sem nome). |  |  |  |
| `TestAreasClick` | 0x00519538 | 392 | INFERIDO | Ao clicar em TestAreas «Zone acquisite random»: mexe em AcqusitionAreas. |  |  |  |
| `TimerMessageSwitchToGasTimer` | 0x0051A168 | 226 | DERIVADO | Temporizador da mensagem "mudou para gás" (UserMessageLabel). OBS-COD (campos). |  |  |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `ActionAutoCalRifExecute` (0x005187A0):
  - sub_515D00 (830 B): chama TAebNumber::GetDouble,TAebVector::GetDouble
  - sub_5162F8 (1742 B): chama TAebProtocol::GetComFail,TAebProtocol::IsConnect,TAebVector::GetDouble
  - sub_51B604 (779 B): textos BUTTONANNULLA,BUTTONOK,FINESTRACENTRICELLEAUTOCAL,MESSBADRIFMAP; chama TAboutBox.Create,TAebVector::GetDouble,TTranslatorExt::GetString
  -   sub_516044 (106 B): chama TAebVector::GetDouble
  -   sub_516D7F (484 B): chama TAebNumber::GetInt,TAebNumber::SetInt
  -   sub_516D8C (471 B): chama TAebNumber::GetInt,TAebNumber::SetInt
  -   sub_516F64 (390 B): chama TAebNumber::GetInt,TAebVector::GetDouble,TAebVector::GetInt
  -   sub_5171EC (102 B): chama TAebProtocol::GetComFail
  -   sub_51BD3C (437 B): chama TAebVector::GetDouble,TNumericEdit.Create
  -     sub_41FB40 (34 B): chama TAebProtocol::GetPacket
  -     sub_517254 (518 B): chama TAebVector::GetDouble,TAebVector::GetInt
  -     sub_51BEF4 (87 B): chama TChildLabel.Create
  - (+21 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ActionAutoMatchExecute` (0x005189B4):
  - sub_517568 (359 B): APIs Sleep
  -   sub_512264 (25 B): chama TAebProtocol::IsConnect
  -   sub_512280 (125 B): chama TAebProtocol::SendCommand
  -   sub_5162F8 (1742 B): chama TAebProtocol::GetComFail,TAebProtocol::IsConnect,TAebVector::GetDouble
  -     sub_516D7F (484 B): chama TAebNumber::GetInt,TAebNumber::SetInt
  -     sub_516D8C (471 B): chama TAebNumber::GetInt,TAebNumber::SetInt
  -     sub_516F64 (390 B): chama TAebNumber::GetInt,TAebVector::GetDouble,TAebVector::GetInt
  -     sub_5171EC (102 B): chama TAebProtocol::GetComFail
  - (+5 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ActionChangeColorExecute` (0x0051AE78):
  -   sub_51995A (1420 B): chama TAutocalColorSettings.Create
  - (+10 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ActionDeleteSelectedPointsExecute` (0x00518E6C):
  - sub_512858 (275 B): chama TAebVector::SetDefValue
  - sub_5162F8 (1742 B): chama TAebProtocol::GetComFail,TAebProtocol::IsConnect,TAebVector::GetDouble
  -   sub_512300 (119 B): chama TAebProtocol::SendCommand
  -   sub_516D7F (484 B): chama TAebNumber::GetInt,TAebNumber::SetInt
  -   sub_516D8C (471 B): chama TAebNumber::GetInt,TAebNumber::SetInt
  -   sub_516F64 (390 B): chama TAebNumber::GetInt,TAebVector::GetDouble,TAebVector::GetInt
  -   sub_5171EC (102 B): chama TAebProtocol::GetComFail
  -     sub_512264 (25 B): chama TAebProtocol::IsConnect
  -     sub_41FB40 (34 B): chama TAebProtocol::GetPacket
  -     sub_517254 (518 B): chama TAebVector::GetDouble,TAebVector::GetInt
  - (+10 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ActionExportChartDataExecute` (0x0051878C):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ActionExportChartPointExecute` (0x00519120):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ActionExportKChartExecute` (0x005185FC):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ActionExportToKExecute` (0x00518514):
  - sub_512624 (226 B): chama TAebVector::GetDouble
  - sub_516044 (106 B): chama TAebVector::GetDouble
  - sub_5162F8 (1742 B): chama TAebProtocol::GetComFail,TAebProtocol::IsConnect,TAebVector::GetDouble
  -   sub_512708 (167 B): chama TAebVector::GetDouble
  -   sub_516D7F (484 B): chama TAebNumber::GetInt,TAebNumber::SetInt
  -   sub_516D8C (471 B): chama TAebNumber::GetInt,TAebNumber::SetInt
  -   sub_516F64 (390 B): chama TAebNumber::GetInt,TAebVector::GetDouble,TAebVector::GetInt
  -   sub_5171EC (102 B): chama TAebProtocol::GetComFail
  -     sub_42ECD8 (181 B): chama TAebMatrix::SetEcuData
  -     sub_41FB40 (34 B): chama TAebProtocol::GetPacket
  -     sub_517254 (518 B): chama TAebVector::GetDouble,TAebVector::GetInt
  - (+16 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ActionFinishAutocalExecute` (0x0051A390):
  - sub_5162F8 (1742 B): chama TAebProtocol::GetComFail,TAebProtocol::IsConnect,TAebVector::GetDouble
  -   sub_516D7F (484 B): chama TAebNumber::GetInt,TAebNumber::SetInt
  -   sub_516D8C (471 B): chama TAebNumber::GetInt,TAebNumber::SetInt
  -   sub_516F64 (390 B): chama TAebNumber::GetInt,TAebVector::GetDouble,TAebVector::GetInt
  -   sub_5171EC (102 B): chama TAebProtocol::GetComFail
  -     sub_41FB40 (34 B): chama TAebProtocol::GetPacket
  -     sub_517254 (518 B): chama TAebVector::GetDouble,TAebVector::GetInt
  - (+8 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ActionGoToMinCalibrationExecute` (0x005189E4):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ActionResetAllExecute` (0x005189D8):
  - sub_517568 (359 B): APIs Sleep
  -   sub_512264 (25 B): chama TAebProtocol::IsConnect
  -   sub_512280 (125 B): chama TAebProtocol::SendCommand
  -   sub_5162F8 (1742 B): chama TAebProtocol::GetComFail,TAebProtocol::IsConnect,TAebVector::GetDouble
  -     sub_516D7F (484 B): chama TAebNumber::GetInt,TAebNumber::SetInt
  -     sub_516D8C (471 B): chama TAebNumber::GetInt,TAebNumber::SetInt
  -     sub_516F64 (390 B): chama TAebNumber::GetInt,TAebVector::GetDouble,TAebVector::GetInt
  -     sub_5171EC (102 B): chama TAebProtocol::GetComFail
  - (+5 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ActionResetGasExecute` (0x005189CC):
  - sub_517568 (359 B): APIs Sleep
  -   sub_512264 (25 B): chama TAebProtocol::IsConnect
  -   sub_512280 (125 B): chama TAebProtocol::SendCommand
  -   sub_5162F8 (1742 B): chama TAebProtocol::GetComFail,TAebProtocol::IsConnect,TAebVector::GetDouble
  -     sub_516D7F (484 B): chama TAebNumber::GetInt,TAebNumber::SetInt
  -     sub_516D8C (471 B): chama TAebNumber::GetInt,TAebNumber::SetInt
  -     sub_516F64 (390 B): chama TAebNumber::GetInt,TAebVector::GetDouble,TAebVector::GetInt
  -     sub_5171EC (102 B): chama TAebProtocol::GetComFail
  - (+5 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ActionResetKFactorExecute` (0x0051A070):
  - sub_516044 (106 B): chama TAebVector::GetDouble
  - sub_5162F8 (1742 B): chama TAebProtocol::GetComFail,TAebProtocol::IsConnect,TAebVector::GetDouble
  -   sub_516D7F (484 B): chama TAebNumber::GetInt,TAebNumber::SetInt
  -   sub_516D8C (471 B): chama TAebNumber::GetInt,TAebNumber::SetInt
  -   sub_516F64 (390 B): chama TAebNumber::GetInt,TAebVector::GetDouble,TAebVector::GetInt
  -   sub_5171EC (102 B): chama TAebProtocol::GetComFail
  -     sub_41FB40 (34 B): chama TAebProtocol::GetPacket
  -     sub_517254 (518 B): chama TAebVector::GetDouble,TAebVector::GetInt
  - (+8 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ActionResetPetrolExecute` (0x005189C0):
  - sub_517568 (359 B): APIs Sleep
  -   sub_512264 (25 B): chama TAebProtocol::IsConnect
  -   sub_512280 (125 B): chama TAebProtocol::SendCommand
  -   sub_5162F8 (1742 B): chama TAebProtocol::GetComFail,TAebProtocol::IsConnect,TAebVector::GetDouble
  -     sub_516D7F (484 B): chama TAebNumber::GetInt,TAebNumber::SetInt
  -     sub_516D8C (471 B): chama TAebNumber::GetInt,TAebNumber::SetInt
  -     sub_516F64 (390 B): chama TAebNumber::GetInt,TAebVector::GetDouble,TAebVector::GetInt
  -     sub_5171EC (102 B): chama TAebProtocol::GetComFail
  - (+5 subfunções sem fatos diretos ou fora da lista; telas.json)
- `BtnDumpClick` (0x005184D4):
  - sub_41F068 (2706 B): chama TAebMatrix::GetDefValue,TAebMatrix::GetDouble,TAebNumber::GetDouble,TAebVector::GetDefValue
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `BtnDumpEEClick` (0x005184F4):
  - sub_41F068 (2706 B): chama TAebMatrix::GetDefValue,TAebMatrix::GetDouble,TAebNumber::GetDouble,TAebVector::GetDefValue
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `BtnShowBandClick` (0x0051A74C):
  - sub_51A614 (311 B): chama TAebVector::GetDouble
  -   sub_51A51C (247 B): chama TAebVector::GetDouble
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ChartDataAfterDraw` (0x0051A998):
  - sub_51A7B8 (162 B): chama TAebVector::GetDouble
  - sub_51A85C (184 B): chama TAebVector::GetDouble
  - sub_51A918 (127 B): chama TAebNumber::GetInt,TAebVector::GetInt
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ChartDataClickSeries` (0x00518C3C):
  - (+3 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ChartDataDblClick` (0x00518E3C):
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ChartDataKeyDown` (0x0051939C):
  - (+3 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ChartDataKeyUp` (0x00519528):
  - sub_519144 (221 B): chama TAebNumber::SetDouble
  - (+0 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ChartDataMouseMove` (0x00517F60):
  - (+4 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ChartDataMouseUp` (0x00519134):
  - sub_519144 (221 B): chama TAebNumber::SetDouble
  - (+0 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ChartKLineClickSeries` (0x005176D0):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ChartKLineDragOver` (0x005178CC):
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ChartKLineEndDrag` (0x00517980):
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ChartKLineKeyDown` (0x005179E0):
  - (+3 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ChartKLineMouseDown` (0x00517DE8):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ChartKLineMouseMove` (0x00517E80):
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DbgBtnRandomClick` (0x005189F0):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `MenuChartDataPopup` (0x005190A0):
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `SettingsImgClick` (0x00519708):
  - sub_513630 (1880 B): textos AUTOCALCHANGECOLOR,AUTOCALSETTINGSCHARTK,AUTOCALSETTINGSDISABLEHBAND,AUTOCALSETTINGSRPMMAX; chama TAboutBox.Create,TTranslatorExt::GetString
  -   sub_51995A (1420 B): chama TAutocalColorSettings.Create
  - (+19 subfunções sem fatos diretos ou fora da lista; telas.json)
