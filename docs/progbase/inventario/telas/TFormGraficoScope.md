# TFormGraficoScope (recurso `TFORMGRAFICOSCOPE`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormGraficoScope`; unidade: `@:GraficoScope`; ancestral: `TForm`; tamanho de instância: 0x734; componentes no DFM: 81.
- Título: "Grafico" — eng: "Graph" / por: "Gráfico"
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

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

| Handler | Endereço | Bytes | Evidência | O que faz | Textos (chaves i18n) | APIs | Chama |
|---|---|---:|---|---|---|---|---|
| `ActionAddMarkerExecute` | 0x004281FC | 28 | INFERIDO | Handler `ActionAddMarkerExecute`; sem fatos extraíveis automaticamente (28 bytes) |  |  |  |
| `ActionNextMarkerExecute` | 0x00428B48 | 32 | INFERIDO | Handler `ActionNextMarkerExecute`; sem fatos extraíveis automaticamente (32 bytes) |  |  |  |
| `ActionPreviousMarkerExecute` | 0x00428B68 | 42 | INFERIDO | Handler `ActionPreviousMarkerExecute`; sem fatos extraíveis automaticamente (42 bytes) |  |  |  |
| `ActionRimuoviCursoreExecute` | 0x00428C68 | 200 | INFERIDO | Handler `ActionRimuoviCursoreExecute`; toca campos/controles: ChartDrt, SeriesCursoreNeg, SeriesCursorePos |  |  |  |
| `ActionSwitchExecute` (sem evento no DFM) | 0x004271B4 | 196 | INFERIDO | Handler `ActionSwitchExecute`; chama APIs do Windows: KERNEL32.DLL!Sleep, USER32.DLL!PostMessageW; chama: TStreamDati.TimerIOTimer, thunk -> KERNEL32.DLL!Sleep; toca campos/controles: Timer |  | KERNEL32.DLL!Sleep, USER32.DLL!PostMessageW | TStreamDati.TimerIOTimer, thunk -> KERNEL32.DLL!Sleep |
| `ChartDrtClickAxis` | 0x00428A98 | 27 | INFERIDO | Handler `ChartDrtClickAxis`; sem fatos extraíveis automaticamente (27 bytes) |  |  |  |
| `ChartDrtMouseMove` | 0x00429334 | 35 | INFERIDO | Handler `ChartDrtMouseMove`; sem fatos extraíveis automaticamente (35 bytes) |  |  |  |
| `ChartDrtMouseUp` | 0x00429324 | 14 | INFERIDO | Handler `ChartDrtMouseUp`; sem fatos extraíveis automaticamente (14 bytes) |  |  |  |
| `ChartDrtZoom` | 0x00428C60 | 8 | INFERIDO | Handler `ChartDrtZoom`; sem fatos extraíveis automaticamente (8 bytes) |  |  |  |
| `CheckAutoMarkerClick` | 0x00428D30 | 33 | INFERIDO | Handler `CheckAutoMarkerClick`; toca campos/controles: CheckAutoMarker |  |  |  |
| `EditMaxRangeEnter` | 0x00428E04 | 534 | INFERIDO | Handler `EditMaxRangeEnter`; sem fatos extraíveis automaticamente (534 bytes) |  |  |  |
| `EditMaxRangeExit` | 0x0042901C | 28 | INFERIDO | Handler `EditMaxRangeExit`; sem fatos extraíveis automaticamente (28 bytes) |  |  |  |
| `EditRangeKeyPress` | 0x004270C8 | 68 | INFERIDO | Handler `EditRangeKeyPress`; toca campos/controles: ChartDrt, EditMaxRange, EditMinRange, btnAutoRange |  |  |  |
| `EditResolutionKeyPress` | 0x00427130 | 108 | INFERIDO | Handler `EditResolutionKeyPress`; toca campos/controles: ChartDrt, EditResolution |  |  |  |
| `FormClose` | 0x00423858 | 226 | INFERIDO | Handler `FormClose`; sem fatos extraíveis automaticamente (226 bytes) |  |  |  |
| `FormCloseQuery` | 0x00423848 | 16 | INFERIDO | Handler `FormCloseQuery`; sem fatos extraíveis automaticamente (16 bytes) |  |  |  |
| `FormKeyPress` | 0x0042719C | 24 | INFERIDO | Handler `FormKeyPress`; sem fatos extraíveis automaticamente (24 bytes) |  |  |  |
| `FormMouseWheelDown` | 0x00428C50 | 13 | INFERIDO | Handler `FormMouseWheelDown`; sem fatos extraíveis automaticamente (13 bytes) |  |  |  |
| `FormMouseWheelUp` | 0x00428C40 | 13 | INFERIDO | Handler `FormMouseWheelUp`; sem fatos extraíveis automaticamente (13 bytes) |  |  |  |
| `FormResize` | 0x00427278 | 87 | INFERIDO | Handler `FormResize`; toca campos/controles: ChartDrt, panelChart |  |  |  |
| `FormShow` | 0x00428DA4 | 93 | INFERIDO | Handler `FormShow`; sem fatos extraíveis automaticamente (93 bytes) |  |  |  |
| `GenericOmvlButtonMouseEnter` (sem evento no DFM) | 0x00429040 | 14 | INFERIDO | Handler `GenericOmvlButtonMouseEnter`; sem fatos extraíveis automaticamente (14 bytes) |  |  |  |
| `GenericOmvlButtonMouseLeave` (sem evento no DFM) | 0x00429050 | 14 | INFERIDO | Handler `GenericOmvlButtonMouseLeave`; sem fatos extraíveis automaticamente (14 bytes) |  |  |  |
| `MenuRimuoviMarkerClick` | 0x00428B1C | 44 | INFERIDO | Handler `MenuRimuoviMarkerClick`; toca campos/controles: SeriesMarkersNeg, SeriesMarkersPos |  |  |  |
| `TimerTimer` | 0x004257E4 | 5086 | INFERIDO | Handler `TimerTimer`; chama APIs do Windows: KERNEL32.DLL!GetTickCount; chama: TAebProtocol::GetPacket, TAebProtocol::IsConnect, TStreamDati.TimerIOTimer, thunk -> KERNEL32.DLL!GetTickCount; toca campos/controles: ChartDrt, SeriesAutoMarkersNeg, SeriesAutoMarkersPos, SeriesEcuMarkersNeg, SeriesEcuMarkersPos, SeriesMarkersNeg, SeriesMarkersPos, Timer |  | KERNEL32.DLL!GetTickCount | TAebProtocol::GetPacket, TAebProtocol::IsConnect, TStreamDati.TimerIOTimer, thunk -> KERNEL32.DLL!GetTickCount |
| `btnAutoRangeClick` | 0x00429DD8 | 64 | INFERIDO | Handler `btnAutoRangeClick`; toca campos/controles: ChartDrt, EditMaxRange, EditMinRange, btnAutoRange |  |  |  |
| `btnExportImageClick` | 0x00429A98 | 410 | INFERIDO | Handler `btnExportImageClick`; toca campos/controles: ChartDrt, EsportDialogDrt |  |  |  |
| `btnLegendClick` | 0x00429F00 | 154 | INFERIDO | Handler `btnLegendClick`; toca campos/controles: ChartDrt, ImageList1, btnLegend |  |  |  |
| `btnLoadClick` | 0x0042996C | 300 | INFERIDO | Handler `btnLoadClick`; toca campos/controles: ChartDrt, OpenDialogDrt, btnAutoRange, btnSave |  |  |  |
| `btnRestoreZoomClick` | 0x00429DB4 | 33 | INFERIDO | Handler `btnRestoreZoomClick`; toca campos/controles: ChartDrt |  |  |  |
| `btnRunClick` | 0x004296C0 | 44 | INFERIDO | Handler `btnRunClick`; chama APIs do Windows: KERNEL32.DLL!GetTickCount |  | KERNEL32.DLL!GetTickCount |  |
| `btnSaveClick` | 0x004296EC | 638 | INFERIDO | Handler `btnSaveClick`; chama APIs do Windows: KERNEL32.DLL!CopyFileW; chama: thunk -> KERNEL32.DLL!CopyFileW; toca campos/controles: SaveDialogDrt |  | KERNEL32.DLL!CopyFileW | thunk -> KERNEL32.DLL!CopyFileW |
| `btnZoomInClick` | 0x00429C34 | 50 | INFERIDO | Handler `btnZoomInClick`; sem fatos extraíveis automaticamente (50 bytes) |  |  |  |
| `btnZoomOutClick` | 0x00429C68 | 329 | INFERIDO | Handler `btnZoomOutClick`; toca campos/controles: ChartDrt, EditResolution |  |  |  |
