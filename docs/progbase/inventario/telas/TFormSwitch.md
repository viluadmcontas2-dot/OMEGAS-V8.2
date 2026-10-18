# TFormSwitch (recurso `TFORMSWITCH`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormSwitch`; unidade: `@:CalibraSwitch`; ancestral: `TForm`; tamanho de instância: 0x404; componentes no DFM: 11.
- Título: "Switch settings" — eng: "Switch settings" / por: "Switch settings"
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

## Controles com evento/ação (6)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| FormSwitch | TFormSwitch | Switch settings |  | OnClose→`FormClose` (0x00412AB8); OnKeyPress→`FormKeyPress` (0x00412B20) |
| ButtonExit | TButton | Exit |  | OnClick→`ButtonExitClick` (0x00412B30) |
| PanelSettings/ComboBuzzer | TKeyComboBox |  |  | OnEnter→`ComboBuzzerEnter` (0x00412E40); OnKeyPress→`ComboBuzzerKeyPress` (0x00412E5C) |
| PanelSettings/EditLed | TNumericEdit |  |  | OnEnter→`EditSwitchEnter` (0x00412B38); OnKeyPress→`EditSwitchKeyPress` (0x00412B6C) |
| PanelSettings/EditBuzzer | TNumericEdit |  |  | OnEnter→`EditSwitchEnter` (0x00412B38); OnKeyPress→`EditSwitchKeyPress` (0x00412B6C) |
| PanelSettings/TrackBarLed | TTrackBar |  |  | OnChange→`TrackBarLedChange` (0x00412CEC); OnEnter→`TrackBarLedEnter` (0x00412CD8) |

## Handlers (9): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos (chaves i18n) | APIs | Chama |
|---|---|---:|---|---|---|---|---|
| `ButtonExitClick` | 0x00412B30 | 6 | INFERIDO | Handler `ButtonExitClick`; sem fatos extraíveis automaticamente (6 bytes) |  |  |  |
| `ComboBuzzerEnter` | 0x00412E40 | 25 | INFERIDO | Handler `ComboBuzzerEnter`; toca campos/controles: ComboBuzzer |  |  |  |
| `ComboBuzzerKeyPress` | 0x00412E5C | 188 | INFERIDO | Handler `ComboBuzzerKeyPress`; toca campos/controles: EditBuzzer |  |  |  |
| `EditSwitchEnter` | 0x00412B38 | 50 | INFERIDO | Handler `EditSwitchEnter`; sem fatos extraíveis automaticamente (50 bytes) |  |  |  |
| `EditSwitchKeyPress` | 0x00412B6C | 361 | INFERIDO | Handler `EditSwitchKeyPress`; toca campos/controles: ComboBuzzer, TrackBarLed |  |  |  |
| `FormClose` | 0x00412AB8 | 102 | INFERIDO | Handler `FormClose`; chama: TAebProtocol::SendCommand |  |  | TAebProtocol::SendCommand |
| `FormKeyPress` | 0x00412B20 | 14 | INFERIDO | Handler `FormKeyPress`; sem fatos extraíveis automaticamente (14 bytes) |  |  |  |
| `TrackBarLedChange` | 0x00412CEC | 338 | INFERIDO | Handler `TrackBarLedChange`; chama APIs do Windows: USER32.DLL!GetKeyState; chama: thunk -> USER32.DLL!GetKeyState; toca campos/controles: EditLed, TrackBarLed |  | USER32.DLL!GetKeyState | thunk -> USER32.DLL!GetKeyState |
| `TrackBarLedEnter` | 0x00412CD8 | 19 | INFERIDO | Handler `TrackBarLedEnter`; sem fatos extraíveis automaticamente (19 bytes) |  |  |  |
