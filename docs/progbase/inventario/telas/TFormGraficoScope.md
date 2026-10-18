# TFormGraficoScope (recurso `TFORMGRAFICOSCOPE`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormGraficoScope`; unidade: `@:GraficoScope`; ancestral: `TForm`; tamanho de instância: 0x734; componentes no DFM: 81.
- Título: "Grafico" — eng: "Graph" / por: "Gráfico"
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` (0x004111F8), `TFormMain.Plot1Click` (0x0050417C)

## Menus (5)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| MainMenu/MenuRimuoviMarker | TMenuItem | Rimuovi marker |  | OnClick→`MenuRimuoviMarkerClick` (0x00428B1C) |
| MainMenu/MenuRimuoviCursore | TMenuItem | Rimuovi Cursore |  | ActionRimuoviCursore.OnExecute→`ActionRimuoviCursoreExecute` (0x00428C68) |
| MainMenu/MenuMarker | TMenuItem | Marker |  |  |
| MainMenu/MenuMarker/MenuPreviousMarker | TMenuItem | Marker precedente / Previous marker / None |  | ActionPreviousMarker.OnExecute→`ActionPreviousMarkerExecute` (0x00428B68) |
| MainMenu/MenuMarker/MenuMarkerSuccessivo | TMenuItem | Marker successivo / Next marker / None |  | ActionNextMarker.OnExecute→`ActionNextMarkerExecute` (0x00428B48) |

## Ações (9)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| ScopeActionList/ActionAddMarker | TAction | AddMarker | F12 | OnExecute→`ActionAddMarkerExecute` (0x004281FC) |
| ScopeActionList/ActionZoomIn | TAction |  |  |  |
| ScopeActionList/ActionZoomOut | TAction |  |  |  |
| ScopeActionList/ActionUndoZoom | TAction | ActionUndoZoom |  |  |
| ScopeActionList/ActionNextMarker | TAction | ActionNextMarker |  | OnExecute→`ActionNextMarkerExecute` (0x00428B48) |
| ScopeActionList/ActionPreviousMarker | TAction | ActionPreviousMarker |  | OnExecute→`ActionPreviousMarkerExecute` (0x00428B68) |
| ScopeActionList/ActionRimuoviCursore | TAction | ActionRimuoviCursore |  | OnExecute→`ActionRimuoviCursoreExecute` (0x00428C68) |
| ScopeActionList/ActionTestEcuMarker | TAction | ActionTestEcuMarker |  |  |
| ScopeActionList/ActionApriHelp | TAction | ActionApriHelp |  |  |

## Controles com evento/ação (21)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| FormGraficoScope | TFormGraficoScope | Grafico / Graph / Gráfico |  | OnClose→`FormClose` (0x00423858); OnCloseQuery→`FormCloseQuery` (0x00423848); OnKeyPress→`FormKeyPress` (0x0042719C); OnMouseWheelDown→`FormMouseWheelDown` (0x00428C50); OnMouseWheelUp→`FormMouseWheelUp` (0x00428C40); OnResize→`FormResize` (0x00427278); OnShow→`FormShow` (0x00428DA4) |
| panelChart/ChartDrt | TChart |  |  | OnClickAxis→`ChartDrtClickAxis` (0x00428A98); OnZoom→`ChartDrtZoom` (0x00428C60); OnMouseMove→`ChartDrtMouseMove` (0x00429334); OnMouseUp→`ChartDrtMouseUp` (0x00429324) |
| panelTop/btnRun | TSpeedButton | run |  | OnClick→`btnRunClick` (0x004296C0) |
| panelTop/btnLoad | TSpeedButton | load |  | OnClick→`btnLoadClick` (0x0042996C) |
| panelTop/btnSave | TSpeedButton | save |  | OnClick→`btnSaveClick` (0x004296EC) |
| panelTop/btnExportImage | TSpeedButton | export |  | OnClick→`btnExportImageClick` (0x00429A98) |
| panelTop/SpeedButton4 | TSpeedButton |  |  | OnClick→`btnLoadClick` (0x0042996C) |
| panelTop/btnZoomIn | TSpeedButton | zoom in |  | OnClick→`btnZoomInClick` (0x00429C34) |
| panelTop/btnZoomOut | TSpeedButton | zoom out |  | OnClick→`btnZoomOutClick` (0x00429C68) |
| panelTop/btnRestoreZoom | TSpeedButton | restore zoom |  | OnClick→`btnRestoreZoomClick` (0x00429DB4) |
| panelTop/SpeedButton8 | TSpeedButton |  |  | OnClick→`btnLoadClick` (0x0042996C) |
| panelTop/btnAutoRange | TSpeedButton | auto range |  | OnClick→`btnAutoRangeClick` (0x00429DD8) |
| panelTop/btnLegend | TSpeedButton | legend |  | OnClick→`btnLegendClick` (0x00429F00) |
| panelTop/panelParams/EditMinRange | TNumericEdit |  |  | OnEnter→`EditMaxRangeEnter` (0x00428E04); OnExit→`EditMaxRangeExit` (0x0042901C); OnKeyPress→`EditRangeKeyPress` (0x004270C8) |
| panelTop/panelParams/EditMaxRange | TNumericEdit |  |  | OnEnter→`EditMaxRangeEnter` (0x00428E04); OnExit→`EditMaxRangeExit` (0x0042901C); OnKeyPress→`EditRangeKeyPress` (0x004270C8) |
| panelTop/panelParams/EditResolution | TNumericEdit |  |  | OnEnter→`EditMaxRangeEnter` (0x00428E04); OnExit→`EditMaxRangeExit` (0x0042901C); OnKeyPress→`EditResolutionKeyPress` (0x00427130) |
| panelTop/SpeedButton2 | TSpeedButton |  |  | OnClick→`btnLoadClick` (0x0042996C) |
| panelTest/ButtonPrevMarker | TButtonImage | ActionPreviousMarker |  | ActionPreviousMarker.OnExecute→`ActionPreviousMarkerExecute` (0x00428B68) |
| panelTest/ButtonNextMarker | TButtonImage | ActionNextMarker |  | ActionNextMarker.OnExecute→`ActionNextMarkerExecute` (0x00428B48) |
| panelTest/ButtonRimuoviCursore | TButtonImage | ActionRimuoviCursore |  | ActionRimuoviCursore.OnExecute→`ActionRimuoviCursoreExecute` (0x00428C68) |
| panelTest/CheckAutoMarker | TCheckBox | Auto marker |  | OnClick→`CheckAutoMarkerClick` (0x00428D30) |

## Caixas de diálogo (componentes) (4)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) | Filtro |
|---|---|---|---|---|---|
| PrintDialog | TPrintDialog |  |  |  |  |
| OpenDialogDrt | TOpenDialog |  |  |  | Grafico dati in tempo reale\|*.txt |
| SaveDialogDrt | TSaveDialog |  |  |  | Acquisizioni in tempo reale\|*.txt |
| EsportDialogDrt | TSaveDialog |  |  |  | File bitmap \|*.bmp |

## Temporizadores (1)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) | Intervalo ms |
|---|---|---|---|---|---|
| Timer | TTimer |  |  | OnTimer→`TimerTimer` (0x004257E4) | 20 |

## Handlers (34): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `ActionAddMarkerExecute` | 0x004281FC | 28 | INFERIDO | sem fatos diretos extraíveis (28 bytes; chama 1 funções sem nome) |  |  |  |
| `ActionNextMarkerExecute` | 0x00428B48 | 32 | INFERIDO | sem fatos diretos extraíveis (32 bytes; chama 1 funções sem nome) |  |  |  |
| `ActionPreviousMarkerExecute` | 0x00428B68 | 42 | INFERIDO | sem fatos diretos extraíveis (42 bytes; chama 1 funções sem nome) |  |  |  |
| `ActionRimuoviCursoreExecute` | 0x00428C68 | 200 | INFERIDO | toca ChartDrt, SeriesCursoreNeg, SeriesCursorePos |  |  |  |
| `ActionSwitchExecute` (sem evento no DFM) | 0x004271B4 | 196 | INFERIDO | chama APIs do Windows Sleep; chama TStreamDati.TimerIOTimer; toca Timer |  | Sleep | PostMessageW |
| `ChartDrtClickAxis` | 0x00428A98 | 27 | INFERIDO | sem fatos diretos extraíveis (27 bytes; chama 1 funções sem nome) |  |  |  |
| `ChartDrtMouseMove` | 0x00429334 | 35 | INFERIDO | sem fatos diretos extraíveis (35 bytes; chama 1 funções sem nome) |  |  |  |
| `ChartDrtMouseUp` | 0x00429324 | 14 | INFERIDO | sem fatos diretos extraíveis (14 bytes; chama 0 funções sem nome) |  |  |  |
| `ChartDrtZoom` | 0x00428C60 | 8 | INFERIDO | sem fatos diretos extraíveis (8 bytes; chama 0 funções sem nome) |  |  |  |
| `CheckAutoMarkerClick` | 0x00428D30 | 33 | INFERIDO | toca CheckAutoMarker |  |  |  |
| `EditMaxRangeEnter` | 0x00428E04 | 534 | INFERIDO | sem fatos diretos extraíveis (534 bytes; chama 11 funções sem nome) |  |  |  |
| `EditMaxRangeExit` | 0x0042901C | 28 | INFERIDO | sem fatos diretos extraíveis (28 bytes; chama 2 funções sem nome) |  |  |  |
| `EditRangeKeyPress` | 0x004270C8 | 68 | INFERIDO | toca ChartDrt, EditMaxRange, EditMinRange, btnAutoRange |  |  |  |
| `EditResolutionKeyPress` | 0x00427130 | 108 | INFERIDO | toca ChartDrt, EditResolution |  |  |  |
| `FormClose` | 0x00423858 | 226 | INFERIDO | sem fatos diretos extraíveis (226 bytes; chama 3 funções sem nome) |  |  |  |
| `FormCloseQuery` | 0x00423848 | 16 | INFERIDO | sem fatos diretos extraíveis (16 bytes; chama 0 funções sem nome) |  |  |  |
| `FormKeyPress` | 0x0042719C | 24 | INFERIDO | sem fatos diretos extraíveis (24 bytes; chama 1 funções sem nome) |  |  |  |
| `FormMouseWheelDown` | 0x00428C50 | 13 | INFERIDO | sem fatos diretos extraíveis (13 bytes; chama 0 funções sem nome) |  |  |  |
| `FormMouseWheelUp` | 0x00428C40 | 13 | INFERIDO | sem fatos diretos extraíveis (13 bytes; chama 0 funções sem nome) |  |  |  |
| `FormResize` | 0x00427278 | 87 | INFERIDO | toca ChartDrt, panelChart |  |  |  |
| `FormShow` | 0x00428DA4 | 93 | INFERIDO | sem fatos diretos extraíveis (93 bytes; chama 4 funções sem nome) |  |  |  |
| `GenericOmvlButtonMouseEnter` (sem evento no DFM) | 0x00429040 | 14 | INFERIDO | sem fatos diretos extraíveis (14 bytes; chama 1 funções sem nome) |  |  |  |
| `GenericOmvlButtonMouseLeave` (sem evento no DFM) | 0x00429050 | 14 | INFERIDO | sem fatos diretos extraíveis (14 bytes; chama 1 funções sem nome) |  |  |  |
| `MenuRimuoviMarkerClick` | 0x00428B1C | 44 | INFERIDO | toca SeriesMarkersNeg, SeriesMarkersPos |  |  |  |
| `TimerTimer` | 0x004257E4 | 5086 | INFERIDO | chama APIs do Windows GetTickCount; chama TAebProtocol::GetPacket, TAebProtocol::IsConnect, TStreamDati.TimerIOTimer; toca ChartDrt, SeriesAutoMarkersNeg, SeriesAutoMarkersPos, SeriesEcuMarkersNeg, SeriesEcuMarkersPos, SeriesMarkersNeg, SeriesMarkersPos, Timer |  | GetTickCount |  |
| `btnAutoRangeClick` | 0x00429DD8 | 64 | INFERIDO | toca ChartDrt, EditMaxRange, EditMinRange, btnAutoRange |  |  |  |
| `btnExportImageClick` | 0x00429A98 | 410 | INFERIDO | toca ChartDrt, EsportDialogDrt |  |  |  |
| `btnLegendClick` | 0x00429F00 | 154 | INFERIDO | toca ChartDrt, ImageList1, btnLegend |  |  |  |
| `btnLoadClick` | 0x0042996C | 300 | INFERIDO | toca ChartDrt, OpenDialogDrt, btnAutoRange, btnSave |  |  |  |
| `btnRestoreZoomClick` | 0x00429DB4 | 33 | INFERIDO | toca ChartDrt |  |  |  |
| `btnRunClick` | 0x004296C0 | 44 | INFERIDO | sem fatos diretos extraíveis (44 bytes; chama 4 funções sem nome) |  |  | GetTickCount |
| `btnSaveClick` | 0x004296EC | 638 | INFERIDO | chama APIs do Windows CopyFileW; toca SaveDialogDrt |  | CopyFileW |  |
| `btnZoomInClick` | 0x00429C34 | 50 | INFERIDO | sem fatos diretos extraíveis (50 bytes; chama 1 funções sem nome) |  |  |  |
| `btnZoomOutClick` | 0x00429C68 | 329 | INFERIDO | toca ChartDrt, EditResolution |  |  |  |
