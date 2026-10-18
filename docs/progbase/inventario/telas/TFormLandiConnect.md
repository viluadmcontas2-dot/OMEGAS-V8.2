# TFormLandiConnect (recurso `TFORMLANDICONNECT`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormLandiConnect`; unidade: `@:LandiConnect`; ancestral: `TForm`; tamanho de instância: 0x48C; componentes no DFM: 40.
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `TFormMain.ImageLandiConnectClick` (0x005040EC)

## Controles com evento/ação (13)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| FormLandiConnect | TFormLandiConnect |  |  | OnKeyPress→`FormKeyPress` (0x004EC240) |
| PageControl/TabStandard/EditTankVol | TValueEditEx |  |  | OnEnter→`EditEnter` (0x004EC8B8); OnExit→`EditExit` (0x004EC8D8) |
| PageControl/TabStandard/EditGearMaxNo | TValueEditEx |  |  | OnEnter→`EditEnter` (0x004EC8B8); OnExit→`EditExit` (0x004EC8D8) |
| PageControl/TabStandard/EditMgssMaxMnfldPress | TValueEditEx |  |  | OnEnter→`EditEnter` (0x004EC8B8); OnExit→`EditExit` (0x004EC8D8) |
| PageControl/TabStandard/CheckEnableLandiConnect | TCheckBox | Enable Landi Connect communication |  | OnClick→`CheckEnableLandiConnectClick` (0x004ED424) |
| PageControl/TabStandard/ButtonResetTrip | TBitBtn | Reset trip |  | OnClick→`ButtonResetClick` (0x004EDA24) |
| PageControl/TabStandard/ButtonResetTotal | TBitBtn | Reset totals |  | OnClick→`ButtonResetClick` (0x004EDA24) |
| PageControl/TabStandard/CheckEnableGear | TCheckBox | Abilita apprendimento marce / Enable gear learning / Enable gear learning |  | OnClick→`CheckEnableGearClick` (0x004ED8B0) |
| PageControl/TabStandard/CheckCambioAutomatico | TCheckBox | Vettura con cambio automatico |  | OnClick→`CheckCambioAutomaticoClick` (0x004ED8FC) |
| PageControl/TabAdvanced/EditInjrGasFlow | TValueEditEx |  |  | OnEnter→`EditEnter` (0x004EC8B8); OnExit→`EditExit` (0x004EC8D8) |
| PageControl/TabAdvanced/DrawGridInjrTOfsTbl | TDrawGrid |  |  | OnDblClick→`DrawGridInjrTOfsTblDblClick` (0x004EC8F8); OnDrawCell→`DrawGridInjrTOfsTblDrawCell` (0x004EC96C); OnKeyPress→`DrawGridInjrTOfsTblKeyPress` (0x004EC95C) |
| PageControl/TabAdvanced/EditNormPress | TValueEditEx |  |  | OnEnter→`EditEnter` (0x004EC8B8); OnExit→`EditExit` (0x004EC8D8) |
| PageControl/TabAdvanced/EditNormTemp | TValueEditEx |  |  | OnEnter→`EditEnter` (0x004EC8B8); OnExit→`EditExit` (0x004EC8D8) |

## Temporizadores (1)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) | Intervalo ms |
|---|---|---|---|---|---|
| TimerUpdate | TTimer |  |  | OnTimer→`TimerUpdateTimer` (0x004EC860) | 500 |

## Handlers (11): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `ButtonResetClick` | 0x004EDA24 | 126 | INFERIDO | chama TAebProtocol::IsConnect, TAebProtocol::SendCommand |  |  |  |
| `CheckCambioAutomaticoClick` | 0x004ED8FC | 247 | INFERIDO | toca CheckCambioAutomatico, CheckEnableGear, EditGearMaxNo |  |  |  |
| `CheckEnableGearClick` | 0x004ED8B0 | 74 | INFERIDO | chama TAebNumber::SetInt |  |  |  |
| `CheckEnableLandiConnectClick` | 0x004ED424 | 370 | INFERIDO | toca CheckCambioAutomatico, CheckEnableGear, CheckEnableLandiConnect, DrawGridInjrTOfsTbl, EditGearMaxNo, EditInjrGasFlow, EditNormPress, EditNormTemp |  |  |  |
| `DrawGridInjrTOfsTblDblClick` | 0x004EC8F8 | 97 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; toca DrawGridInjrTOfsTbl |  | GetCursorPos, ScreenToClient |  |
| `DrawGridInjrTOfsTblDrawCell` | 0x004EC96C | 1071 | INFERIDO | chama TAebMatrix::GetDouble, TAebVector::GetDouble; toca DrawGridInjrTOfsTbl |  |  |  |
| `DrawGridInjrTOfsTblKeyPress` | 0x004EC95C | 14 | INFERIDO | sem fatos diretos extraíveis (14 bytes; chama 1 funções sem nome) |  |  |  |
| `EditEnter` | 0x004EC8B8 | 30 | INFERIDO | sem fatos diretos extraíveis (30 bytes; chama 2 funções sem nome) |  |  |  |
| `EditExit` | 0x004EC8D8 | 30 | INFERIDO | sem fatos diretos extraíveis (30 bytes; chama 2 funções sem nome) |  |  |  |
| `FormKeyPress` | 0x004EC240 | 14 | INFERIDO | sem fatos diretos extraíveis (14 bytes; chama 1 funções sem nome) |  |  |  |
| `TimerUpdateTimer` | 0x004EC860 | 86 | INFERIDO | chama TAebVector::GetEcuData |  |  |  |

### Cadeia de subfunções sem nome alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `CheckCambioAutomaticoClick` (0x004ED8FC):
  - sub_42E754 (385 B)
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckEnableLandiConnectClick` (0x004ED424):
  - sub_42E754 (385 B)
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `DrawGridInjrTOfsTblDblClick` (0x004EC8F8):
  - sub_4ECD9C (1656 B): chama TAebMatrix::GetDouble,TAebMatrix::SetDouble,TAebVector::GetDouble,TAebVector::SetDouble; toca DrawGridInjrTOfsTbl
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_40487C (45 B)
- `DrawGridInjrTOfsTblDrawCell` (0x004EC96C):
  - sub_40487C (45 B)
  - sub_42E754 (385 B)
- `DrawGridInjrTOfsTblKeyPress` (0x004EC95C):
  - sub_4ECD9C (1656 B): chama TAebMatrix::GetDouble,TAebMatrix::SetDouble,TAebVector::GetDouble,TAebVector::SetDouble; toca DrawGridInjrTOfsTbl
  -   sub_50ED48 (173 B): toca MenuTest,PanelBarra,TimerDati
  -     sub_40487C (45 B)
- `TimerUpdateTimer` (0x004EC860):
  - sub_4EC250 (1549 B): chama TAebNumber::GetInt; toca ImageLedGreen0,ImageLedGreen1,ImageLedGreen2,ImageLedGreen3
  -   sub_42E754 (385 B)
