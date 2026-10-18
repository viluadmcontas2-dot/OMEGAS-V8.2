# TFormSwitch (recurso `TFORMSWITCH`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormSwitch`; unidade: `@:CalibraSwitch`; ancestral: `TForm`; tamanho de instância: 0x404; componentes no DFM: 11.
- Título: "Switch settings" — eng: "Switch settings" / por: "Switch settings"
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `TFormConfig.ButtonSwitchSettingsClick` (0x004C90E0)

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

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `ButtonExitClick` | 0x00412B30 | 6 | INFERIDO | sem fatos diretos extraíveis (6 bytes; chama 1 funções sem nome) |  |  |  |
| `ComboBuzzerEnter` | 0x00412E40 | 25 | INFERIDO | toca ComboBuzzer |  |  |  |
| `ComboBuzzerKeyPress` | 0x00412E5C | 188 | INFERIDO | toca EditBuzzer |  |  |  |
| `EditSwitchEnter` | 0x00412B38 | 50 | INFERIDO | sem fatos diretos extraíveis (50 bytes; chama 1 funções sem nome) |  |  |  |
| `EditSwitchKeyPress` | 0x00412B6C | 361 | INFERIDO | toca ComboBuzzer, TrackBarLed |  |  |  |
| `FormClose` | 0x00412AB8 | 102 | INFERIDO | chama TAebProtocol::SendCommand |  |  |  |
| `FormKeyPress` | 0x00412B20 | 14 | INFERIDO | sem fatos diretos extraíveis (14 bytes; chama 1 funções sem nome) |  |  |  |
| `TrackBarLedChange` | 0x00412CEC | 338 | INFERIDO | chama APIs do Windows GetKeyState; toca EditLed, TrackBarLed |  | GetKeyState |  |
| `TrackBarLedEnter` | 0x00412CD8 | 19 | INFERIDO | sem fatos diretos extraíveis (19 bytes; chama 0 funções sem nome) |  |  |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `ComboBuzzerKeyPress` (0x00412E5C):
  - sub_412FBC (254 B): chama TAebProtocol::IsConnect,TAebProtocol::SendCommand
  - (+0 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditSwitchEnter` (0x00412B38):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditSwitchKeyPress` (0x00412B6C):
  - sub_412FBC (254 B): chama TAebProtocol::IsConnect,TAebProtocol::SendCommand
  - (+11 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TrackBarLedChange` (0x00412CEC):
  - sub_412FBC (254 B): chama TAebProtocol::IsConnect,TAebProtocol::SendCommand
  - (+10 subfunções sem fatos diretos ou fora da lista; telas.json)
