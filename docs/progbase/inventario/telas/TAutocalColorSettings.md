# TAutocalColorSettings (recurso `TAUTOCALCOLORSETTINGS`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `AutocalColorSettings`; unidade: `@:AutoCal_ColorSettings`; ancestral: `TForm`; tamanho de instância: 0x494; componentes no DFM: 54.
- Título: "AutocalColorSettings"
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` (0x004111F8), `TAutoCalSettings.BtnChangeColorClick` (0x0051420C), `0x005198FD` (0x005198FD), `TAutoCalUI.ActionChangeColorExecute` (0x0051AE78)

## Controles com evento/ação (17)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| ChartData | TChart |  |  | OnAfterDraw→`ChartDataAfterDraw` (0x0051087C) |
| OK | TButton | OK |  | OnClick→`OKClick` (0x005109BC) |
| PanelCustomColors/Image1 | TImage |  |  | OnClick→`Image1Click` (0x00510AAC) |
| PanelCustomColors/_AcqusitionAreas0 | TPanel |  |  | OnClick→`_AcqusitionAreas0Click` (0x00510954) |
| PanelCustomColors/_AcqusitionAreas1 | TPanel |  |  | OnClick→`_AcqusitionAreas0Click` (0x00510954) |
| PanelCustomColors/_CurrentBand | TPanel |  |  | OnClick→`_AcqusitionAreas0Click` (0x00510954) |
| PanelCustomColors/_CursorLimitMAP_MAX | TPanel |  |  | OnClick→`_AcqusitionAreas0Click` (0x00510954) |
| PanelCustomColors/_CursorLimitMAP_MIN | TPanel |  |  | OnClick→`_AcqusitionAreas0Click` (0x00510954) |
| PanelCustomColors/_GasLine | TPanel |  |  | OnClick→`_AcqusitionAreas0Click` (0x00510954) |
| PanelCustomColors/_GasPoint | TPanel |  |  | OnClick→`_AcqusitionAreas0Click` (0x00510954) |
| PanelCustomColors/_GasPointPrev | TPanel |  |  | OnClick→`_AcqusitionAreas0Click` (0x00510954) |
| PanelCustomColors/_PetrolLine | TPanel |  |  | OnClick→`_AcqusitionAreas0Click` (0x00510954) |
| PanelCustomColors/_PetrolPoint | TPanel |  |  | OnClick→`_AcqusitionAreas0Click` (0x00510954) |
| PanelCustomColors/_RunAxes | TPanel |  |  | OnClick→`_AcqusitionAreas0Click` (0x00510954) |
| PanelCustomColors/_KLine | TPanel |  |  | OnClick→`_AcqusitionAreas0Click` (0x00510954) |
| PanelCustomColors/_Text | TPanel |  |  | OnClick→`_AcqusitionAreas0Click` (0x00510954) |
| Cancel | TButton | Cancel |  |  |

## Caixas de diálogo (componentes) (1)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) | Filtro |
|---|---|---|---|---|---|
| MyColorDialog | TColorDialog |  |  |  |  |

## Handlers (5): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `ChartDataAfterDraw` | 0x0051087C | 213 | INFERIDO | Após desenhar ChartData: mexe em ChartData. |  |  |  |
| `CheckCustomColorsClick` (sem evento no DFM) | 0x00510998 | 35 | INFERIDO | Rotina publicada sem evento ligado no DFM (chamada pelo código): mexe em PanelCustomColors. |  |  |  |
| `Image1Click` | 0x00510AAC | 243 | DERIVADO | Imagem: carrega as cores padrão (AUTOCALSETTINGSLOADDEFCOLOR). OBS-COD. | AUTOCALSETTINGSLOADDEFCOLOR |  |  |
| `OKClick` | 0x005109BC | 239 | DERIVADO | OK: salva as cores e fecha (texto AUTOCALSETTINGSSAVEEXIT). OBS-COD. | AUTOCALSETTINGSSAVEEXIT |  |  |
| `_AcqusitionAreas0Click` | 0x00510954 | 66 | INFERIDO | Ao clicar em _AcqusitionAreas0 (+12 componentes): mexe em MyColorDialog. |  |  |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `Image1Click` (0x00510AAC):
  - (+11 subfunções sem fatos diretos ou fora da lista; telas.json)
- `OKClick` (0x005109BC):
  - (+9 subfunções sem fatos diretos ou fora da lista; telas.json)
- `_AcqusitionAreas0Click` (0x00510954):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
