# TAutoCalSettings (recurso `TAUTOCALSETTINGS`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `AutoCalSettings`; unidade: `@:AutoCal_Settings`; ancestral: `TForm`; tamanho de instância: 0x4A4; componentes no DFM: 25.
- Título: "Autocal settings"
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `TAutoCalUI.SettingsImgClick` (0x00519708)

## Controles com evento/ação (5)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| AutoCalSettings | TAutoCalSettings | Autocal settings |  | OnClose→`FormClose` (0x00513E68); OnShow→`FormShow` (0x00513DA8) |
| GridPanel1/BitBtn1 | TBitBtn |  |  | OnClick→`BitBtn1Click` (0x005140E8) |
| CalibrationVal | TAebVectorItems |  |  | OnWriteItem→`CalibrationValWriteItem` (0x005140F0) |
| PanelTempoRefresh/RefreshTime | TTrackBar |  |  | OnChange→`RefreshTimeChange` (0x005141A4) |
| PanelRPM/BtnChangeColor | TButton | Setup colors |  | OnClick→`BtnChangeColorClick` (0x0051420C) |

## Handlers (6): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `BitBtn1Click` | 0x005140E8 | 6 | INFERIDO | Ao clicar em BitBtn1: corpo de 6 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `BtnChangeColorClick` | 0x0051420C | 101 | INFERIDO | Ao clicar em BtnChangeColor «Setup colors»: chama TAutocalColorSettings.Create. |  |  |  |
| `CalibrationValWriteItem` | 0x005140F0 | 177 | INFERIDO | Evento OnWriteItem de CalibrationVal: chama TAebNumber::SetInt, TAebVector::SetInt. |  |  |  |
| `FormClose` | 0x00513E68 | 640 | INFERIDO | Ao fechar o formulário AutoCalSettings «Autocal settings»: mexe em CheckShowChartKLine, CheckShowChartPoint, CheckShowChartZones, CheckShowStatusMessages, RefreshTime. |  |  |  |
| `FormShow` | 0x00513DA8 | 8 | INFERIDO | Ao exibir o formulário AutoCalSettings «Autocal settings»: corpo de 8 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `RefreshTimeChange` | 0x005141A4 | 104 | INFERIDO | Quando muda o valor de RefreshTime: mexe em LabelRefreshValue, RefreshTime. |  |  |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `FormClose` (0x00513E68):
  - (+4 subfunções sem fatos diretos ou fora da lista; telas.json)
- `FormShow` (0x00513DA8):
  - sub_513DB0 (183 B): chama TAebNumber::GetInt,TAebVector::GetInt,TAebVectorItems::SetValues
  - (+0 subfunções sem fatos diretos ou fora da lista; telas.json)
