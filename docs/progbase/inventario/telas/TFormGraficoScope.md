# TFormGraficoScope (recurso `TFORMGRAFICOSCOPE`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

**Papel:** tela de função (DERIVADO). Osciloscópio em tempo real com marcadores, zoom, legenda, carregar/salvar/exportar.

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
| `ActionAddMarkerExecute` | 0x004281FC | 28 | INFERIDO | Ação AddMarker (F12): adiciona marcador. 28 B. INFERIDO. |  |  |  |
| `ActionNextMarkerExecute` | 0x00428B48 | 32 | INFERIDO | Vai ao marcador seguinte. INFERIDO. |  |  |  |
| `ActionPreviousMarkerExecute` | 0x00428B68 | 42 | INFERIDO | Vai ao marcador anterior. INFERIDO. |  |  |  |
| `ActionRimuoviCursoreExecute` | 0x00428C68 | 200 | DERIVADO | Remove o cursor (séries SeriesCursorePos/Neg). OBS-COD (campos). |  |  |  |
| `ActionSwitchExecute` (sem evento no DFM) | 0x004271B4 | 196 | INFERIDO | Rotina publicada sem evento ligado no DFM (chamada pelo código): chama APIs Sleep; chama TStreamDati.TimerIOTimer; mexe em Timer. |  | Sleep | PostMessageW |
| `ChartDrtClickAxis` | 0x00428A98 | 27 | INFERIDO | Ao clicar num eixo de ChartDrt: corpo de 27 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `ChartDrtMouseMove` | 0x00429334 | 35 | INFERIDO | Ao mover o mouse sobre ChartDrt: corpo de 35 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `ChartDrtMouseUp` | 0x00429324 | 14 | INFERIDO | Ao soltar o mouse em ChartDrt: corpo de 14 bytes sem fatos diretos (chama 0 funções sem nome). |  |  |  |
| `ChartDrtZoom` | 0x00428C60 | 8 | INFERIDO | Evento OnZoom de ChartDrt: corpo de 8 bytes sem fatos diretos (chama 0 funções sem nome). |  |  |  |
| `CheckAutoMarkerClick` | 0x00428D30 | 33 | DERIVADO | Caixa "Auto marker": liga marcadores automáticos. OBS-COD (campo). |  |  |  |
| `EditMaxRangeEnter` | 0x00428E04 | 534 | INFERIDO | Ao entrar no campo EditMinRange (+2 componentes): corpo de 534 bytes sem fatos diretos (chama 11 funções sem nome). |  |  |  |
| `EditMaxRangeExit` | 0x0042901C | 28 | INFERIDO | Ao sair do campo EditMinRange (+2 componentes): corpo de 28 bytes sem fatos diretos (chama 2 funções sem nome). |  |  |  |
| `EditRangeKeyPress` | 0x004270C8 | 68 | INFERIDO | Ao digitar (filtro de teclas) em EditMinRange (+1 componentes): mexe em ChartDrt, EditMaxRange, EditMinRange, btnAutoRange. |  |  |  |
| `EditResolutionKeyPress` | 0x00427130 | 108 | INFERIDO | Ao digitar (filtro de teclas) em EditResolution: mexe em ChartDrt, EditResolution. |  |  |  |
| `FormClose` | 0x00423858 | 226 | INFERIDO | Ao fechar o formulário FormGraficoScope «Gráfico»: corpo de 226 bytes sem fatos diretos (chama 3 funções sem nome). |  |  |  |
| `FormCloseQuery` | 0x00423848 | 16 | INFERIDO | Ao pedir para fechar o formulário FormGraficoScope «Gráfico»: corpo de 16 bytes sem fatos diretos (chama 0 funções sem nome). |  |  |  |
| `FormKeyPress` | 0x0042719C | 24 | INFERIDO | Ao digitar (filtro de teclas) em FormGraficoScope «Gráfico»: corpo de 24 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `FormMouseWheelDown` | 0x00428C50 | 13 | INFERIDO | Evento OnMouseWheelDown de FormGraficoScope «Gráfico»: corpo de 13 bytes sem fatos diretos (chama 0 funções sem nome). |  |  |  |
| `FormMouseWheelUp` | 0x00428C40 | 13 | INFERIDO | Evento OnMouseWheelUp de FormGraficoScope «Gráfico»: corpo de 13 bytes sem fatos diretos (chama 0 funções sem nome). |  |  |  |
| `FormResize` | 0x00427278 | 87 | INFERIDO | Ao redimensionar FormGraficoScope «Gráfico»: mexe em ChartDrt, panelChart. |  |  |  |
| `FormShow` | 0x00428DA4 | 93 | INFERIDO | Ao exibir o formulário FormGraficoScope «Gráfico»: corpo de 93 bytes sem fatos diretos (chama 4 funções sem nome). |  |  |  |
| `GenericOmvlButtonMouseEnter` (sem evento no DFM) | 0x00429040 | 14 | INFERIDO | Rotina publicada sem evento ligado no DFM (chamada pelo código): corpo de 14 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `GenericOmvlButtonMouseLeave` (sem evento no DFM) | 0x00429050 | 14 | INFERIDO | Rotina publicada sem evento ligado no DFM (chamada pelo código): corpo de 14 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `MenuRimuoviMarkerClick` | 0x00428B1C | 44 | DERIVADO | Remove o marcador selecionado (SeriesMarkersPos/Neg). OBS-COD (campos). |  |  |  |
| `TimerTimer` | 0x004257E4 | 5086 | DERIVADO | Laço de aquisição do osciloscópio (5086 B): lê pacotes (TAebProtocol::GetPacket), alimenta as séries de cursor/marcadores (automáticos, ECU) em ChartDrt. OBS-COD. |  | GetTickCount |  |
| `btnAutoRangeClick` | 0x00429DD8 | 64 | DERIVADO | Botão "auto range": ajusta automaticamente mín/máx do eixo. OBS-COD (campos). |  |  |  |
| `btnExportImageClick` | 0x00429A98 | 410 | DERIVADO | Botão "exportar": exporta a imagem do gráfico (EsportDialogDrt, nome padrão "Acquisition"). OBS-COD. |  |  |  |
| `btnLegendClick` | 0x00429F00 | 154 | DERIVADO | Botão legenda: mostra/oculta legenda e troca a imagem do botão (ImageList1). OBS-COD. |  |  |  |
| `btnLoadClick` | 0x0042996C | 300 | DERIVADO | Botão "carregar": abre OpenDialogDrt e carrega uma aquisição salva no gráfico. OBS-COD (campos). |  |  |  |
| `btnRestoreZoomClick` | 0x00429DB4 | 33 | DERIVADO | Botão restaurar zoom. OBS-COD (campo). |  |  |  |
| `btnRunClick` | 0x004296C0 | 44 | INFERIDO | Botão "run": inicia/pausa a aquisição (usa GetTickCount). INFERIDO. |  |  | GetTickCount |
| `btnSaveClick` | 0x004296EC | 638 | DERIVADO | Botão "salvar": abre SaveDialogDrt e copia o arquivo temporário da aquisição para o destino (CopyFileW). OBS-COD. |  | CopyFileW |  |
| `btnZoomInClick` | 0x00429C34 | 50 | INFERIDO | Botão zoom in. INFERIDO pelo nome. |  |  |  |
| `btnZoomOutClick` | 0x00429C68 | 329 | DERIVADO | Botão zoom out (reajusta resolução em EditResolution). OBS-COD (campos). |  |  |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `ActionAddMarkerExecute` (0x004281FC):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ActionNextMarkerExecute` (0x00428B48):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ActionPreviousMarkerExecute` (0x00428B68):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ActionRimuoviCursoreExecute` (0x00428C68):
  - (+7 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ActionSwitchExecute` (0x004271B4):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  - (+23 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ChartDrtClickAxis` (0x00428A98):
  - (+9 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ChartDrtMouseMove` (0x00429334):
  - (+9 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditMaxRangeEnter` (0x00428E04):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditRangeKeyPress` (0x004270C8):
  - (+9 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditResolutionKeyPress` (0x00427130):
  - (+9 subfunções sem fatos diretos ou fora da lista; telas.json)
- `FormClose` (0x00423858):
  - (+3 subfunções sem fatos diretos ou fora da lista; telas.json)
- `MenuRimuoviMarkerClick` (0x00428B1C):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TimerTimer` (0x004257E4):
  - sub_437C00 (106 B): chama TAebProtocol::GetPacket,TAebProtocol::IsConnect
  - (+11 subfunções sem fatos diretos ou fora da lista; telas.json)
- `btnAutoRangeClick` (0x00429DD8):
  - (+9 subfunções sem fatos diretos ou fora da lista; telas.json)
- `btnLoadClick` (0x0042996C):
  - (+20 subfunções sem fatos diretos ou fora da lista; telas.json)
- `btnRunClick` (0x004296C0):
  - sub_42559C (352 B): APIs GetTickCount; chama TFormGraficoScope.ActionRimuoviCursoreExecute
  -     sub_937604 (1168 B): APIs CryptAcquireContextW,CryptCreateHash,CryptDecrypt,CryptDeriveKey
  -     sub_937A94 (1194 B): APIs CryptAcquireContextW,CryptCreateHash,CryptDeriveKey,CryptDestroyHash
  -     sub_50FEAC (318 B): APIs SHGetPathFromIDListW,SHGetSpecialFolderLocation
  - (+17 subfunções sem fatos diretos ou fora da lista; telas.json)
- `btnSaveClick` (0x004296EC):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `btnZoomOutClick` (0x00429C68):
  - (+9 subfunções sem fatos diretos ou fora da lista; telas.json)
