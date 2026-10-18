# TFormLandiConnect (recurso `TFORMLANDICONNECT`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

**Papel:** tela de função (DERIVADO). Configuração do módulo Landi Connect (aprendizado de marchas, tanque, injetores) e zerar viagem/totais.

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
| `ButtonResetClick` | 0x004EDA24 | 126 | DERIVADO | Botões "Reset trip" e "Reset totals": enviam à ECU o comando de zerar contadores de viagem/totais (TAebProtocol::SendCommand). Escreve na ECU. OBS-COD. |  |  |  |
| `CheckCambioAutomaticoClick` | 0x004ED8FC | 247 | DERIVADO | Caixa "Veículo com câmbio automático": desliga o aprendizado de marchas e o número máximo. OBS-COD (campos). |  |  |  |
| `CheckEnableGearClick` | 0x004ED8B0 | 74 | DERIVADO | Caixa "Habilitar aprendizado de marchas": grava o flag na ECU (TAebNumber::SetInt). OBS-COD. |  |  |  |
| `CheckEnableLandiConnectClick` | 0x004ED424 | 370 | DERIVADO | Caixa "Habilitar comunicação Landi Connect": habilita/desabilita os campos (apr. de marchas, volume do tanque, vazão estática dos injetores, pressão/temperatura de normalização, tabela de offset dos injetores). OBS-COD (campos). |  |  |  |
| `DrawGridInjrTOfsTblDblClick` | 0x004EC8F8 | 97 | INFERIDO | Duplo clique numa célula da tabela de offset dos injetores: edita o valor. INFERIDO. |  | GetCursorPos, ScreenToClient |  |
| `DrawGridInjrTOfsTblDrawCell` | 0x004EC96C | 1071 | DERIVADO | Desenha a tabela de offsets dos injetores (ms) lendo TAebMatrix/TAebVector. OBS-COD. |  |  |  |
| `DrawGridInjrTOfsTblKeyPress` | 0x004EC95C | 14 | INFERIDO | Ao digitar (filtro de teclas) em DrawGridInjrTOfsTbl: corpo de 14 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `EditEnter` | 0x004EC8B8 | 30 | INFERIDO | Ao entrar no campo EditTankVol (+5 componentes): corpo de 30 bytes sem fatos diretos (chama 2 funções sem nome). |  |  |  |
| `EditExit` | 0x004EC8D8 | 30 | INFERIDO | Ao sair do campo EditTankVol (+5 componentes): corpo de 30 bytes sem fatos diretos (chama 2 funções sem nome). |  |  |  |
| `FormKeyPress` | 0x004EC240 | 14 | INFERIDO | Ao digitar (filtro de teclas) em FormLandiConnect: corpo de 14 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `TimerUpdateTimer` | 0x004EC860 | 86 | OBS-COD | Atualiza periodicamente os valores lidos da ECU (TAebVector::GetEcuData). OBS-COD. |  |  |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `CheckCambioAutomaticoClick` (0x004ED8FC):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+3 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckEnableLandiConnectClick` (0x004ED424):
  - sub_42E8D8 (956 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+3 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridInjrTOfsTblDblClick` (0x004EC8F8):
  - sub_4ECD9C (1656 B): chama TAebMatrix::GetDouble,TAebMatrix::SetDouble,TAebVector::GetDouble,TAebVector::SetDouble
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridInjrTOfsTblDrawCell` (0x004EC96C):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DrawGridInjrTOfsTblKeyPress` (0x004EC95C):
  - sub_4ECD9C (1656 B): chama TAebMatrix::GetDouble,TAebMatrix::SetDouble,TAebVector::GetDouble,TAebVector::SetDouble
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TimerUpdateTimer` (0x004EC860):
  - sub_4EC250 (1549 B): chama TAebNumber::GetInt
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
