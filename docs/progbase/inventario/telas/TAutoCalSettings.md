# TAutoCalSettings (recurso `TAUTOCALSETTINGS`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `AutoCalSettings`; unidade: `@:AutoCal_Settings`; ancestral: `TForm`; tamanho de instância: 0x4A4; componentes no DFM: 25.
- Título: "Autocal settings"
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

## Controles com evento/ação (5)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| AutoCalSettings | TAutoCalSettings | Autocal settings |  | OnClose→`FormClose` (0x00513E68); OnShow→`FormShow` (0x00513DA8) |
| GridPanel1/BitBtn1 | TBitBtn |  |  | OnClick→`BitBtn1Click` (0x005140E8) |
| CalibrationVal | TAebVectorItems |  |  | OnWriteItem→`CalibrationValWriteItem` (0x005140F0) |
| PanelTempoRefresh/RefreshTime | TTrackBar |  |  | OnChange→`RefreshTimeChange` (0x005141A4) |
| PanelRPM/BtnChangeColor | TButton | Setup colors |  | OnClick→`BtnChangeColorClick` (0x0051420C) |

## Handlers (6): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos (chaves i18n) | APIs | Chama |
|---|---|---:|---|---|---|---|---|
| `BitBtn1Click` | 0x005140E8 | 6 | INFERIDO | Handler `BitBtn1Click`; sem fatos extraíveis automaticamente (6 bytes) |  |  |  |
| `BtnChangeColorClick` | 0x0051420C | 101 | INFERIDO | Handler `BtnChangeColorClick`; chama: TAutocalColorSettings.vmt[0x38] |  |  | TAutocalColorSettings.vmt[0x38] |
| `CalibrationValWriteItem` | 0x005140F0 | 177 | INFERIDO | Handler `CalibrationValWriteItem`; chama: TAebNumber::SetInt, TAebVector::SetInt |  |  | TAebNumber::SetInt, TAebVector::SetInt |
| `FormClose` | 0x00513E68 | 640 | INFERIDO | Handler `FormClose`; toca campos/controles: CheckShowChartKLine, CheckShowChartPoint, CheckShowChartZones, CheckShowStatusMessages, RefreshTime |  |  |  |
| `FormShow` | 0x00513DA8 | 8 | INFERIDO | Handler `FormShow`; sem fatos extraíveis automaticamente (8 bytes) |  |  |  |
| `RefreshTimeChange` | 0x005141A4 | 104 | INFERIDO | Handler `RefreshTimeChange`; toca campos/controles: LabelRefreshValue, RefreshTime |  |  |  |
