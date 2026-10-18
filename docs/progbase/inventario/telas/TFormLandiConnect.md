# TFormLandiConnect (recurso `TFORMLANDICONNECT`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormLandiConnect`; unidade: `@:LandiConnect`; ancestral: `TForm`; tamanho de instância: 0x48C; componentes no DFM: 40.
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

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

| Handler | Endereço | Bytes | Evidência | O que faz | Textos (chaves i18n) | APIs | Chama |
|---|---|---:|---|---|---|---|---|
| `ButtonResetClick` | 0x004EDA24 | 126 | INFERIDO | Handler `ButtonResetClick`; chama: TAebProtocol::IsConnect, TAebProtocol::SendCommand |  |  | TAebProtocol::IsConnect, TAebProtocol::SendCommand |
| `CheckCambioAutomaticoClick` | 0x004ED8FC | 247 | INFERIDO | Handler `CheckCambioAutomaticoClick`; toca campos/controles: CheckCambioAutomatico, CheckEnableGear, EditGearMaxNo |  |  |  |
| `CheckEnableGearClick` | 0x004ED8B0 | 74 | INFERIDO | Handler `CheckEnableGearClick`; chama: TAebNumber::SetInt |  |  | TAebNumber::SetInt |
| `CheckEnableLandiConnectClick` | 0x004ED424 | 370 | INFERIDO | Handler `CheckEnableLandiConnectClick`; toca campos/controles: CheckCambioAutomatico, CheckEnableGear, CheckEnableLandiConnect, DrawGridInjrTOfsTbl, EditGearMaxNo, EditInjrGasFlow, EditNormPress, EditNormTemp |  |  |  |
| `DrawGridInjrTOfsTblDblClick` | 0x004EC8F8 | 97 | INFERIDO | Handler `DrawGridInjrTOfsTblDblClick`; chama APIs do Windows: USER32.DLL!GetCursorPos, USER32.DLL!ScreenToClient; chama: thunk -> USER32.DLL!GetCursorPos, thunk -> USER32.DLL!ScreenToClient; toca campos/controles: DrawGridInjrTOfsTbl |  | USER32.DLL!GetCursorPos, USER32.DLL!ScreenToClient | thunk -> USER32.DLL!GetCursorPos, thunk -> USER32.DLL!ScreenToClient |
| `DrawGridInjrTOfsTblDrawCell` | 0x004EC96C | 1071 | INFERIDO | Handler `DrawGridInjrTOfsTblDrawCell`; chama: TAebMatrix::GetDouble, TAebVector::GetDouble; toca campos/controles: DrawGridInjrTOfsTbl |  |  | TAebMatrix::GetDouble, TAebVector::GetDouble |
| `DrawGridInjrTOfsTblKeyPress` | 0x004EC95C | 14 | INFERIDO | Handler `DrawGridInjrTOfsTblKeyPress`; sem fatos extraíveis automaticamente (14 bytes) |  |  |  |
| `EditEnter` | 0x004EC8B8 | 30 | INFERIDO | Handler `EditEnter`; sem fatos extraíveis automaticamente (30 bytes) |  |  |  |
| `EditExit` | 0x004EC8D8 | 30 | INFERIDO | Handler `EditExit`; sem fatos extraíveis automaticamente (30 bytes) |  |  |  |
| `FormKeyPress` | 0x004EC240 | 14 | INFERIDO | Handler `FormKeyPress`; sem fatos extraíveis automaticamente (14 bytes) |  |  |  |
| `TimerUpdateTimer` | 0x004EC860 | 86 | INFERIDO | Handler `TimerUpdateTimer`; chama: TAebVector::GetEcuData |  |  | TAebVector::GetEcuData |
