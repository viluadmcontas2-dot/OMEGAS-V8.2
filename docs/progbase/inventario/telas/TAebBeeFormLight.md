# TAebBeeFormLight (recurso `TAEBBEEFORMLIGHT`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `AebBeeFormLight`; unidade: `@:AebBeeLight`; ancestral: `TForm`; tamanho de instância: 0x554; componentes no DFM: 98.
- Título: "Wireless Communication"
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `TAebProtocol::ShowAebBeeSettings` (0x0092215C)

## Controles com evento/ação (24)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| AebBeeFormLight | TAebBeeFormLight | Wireless Communication |  | OnClose→`FormClose` (0x00914DD0); OnCloseQuery→`FormCloseQuery` (0x00914DA8); OnKeyPress→`FormKeyPress` (0x00914E80) |
| PageControl/TabDevices/PageDongle/TabSettingsDongle/GroupPropertyDongle/EditNomeDongle | TEdit |  |  | OnExit→`DongleExit` (0x00916DC0); OnKeyPress→`EditNomeDongleKeyPress` (0x00916DF8) |
| PageControl/TabDevices/PageDongle/TabSettingsDongle/GroupPropertyDongle/EditIndirizzoDongle | TEdit |  |  | OnExit→`DongleExit` (0x00916DC0); OnKeyPress→`EditIndirizzoDongleKeyPress` (0x00916FA8) |
| PageControl/TabDevices/PageDongle/TabSettingsDongle/GroupPropertyDongle/ComboPotenzaDongle | TComboBox |  |  | OnChange→`ComboPotenzaDongleChange` (0x00917258); OnExit→`DongleExit` (0x00916DC0) |
| PageControl/TabDevices/PageDongle/TabSettingsDongle/GroupProgramDongle/ButtonSfogliaDongle | TButton | Sfoglia... |  | OnClick→`ButtonSfogliaDongleClick` (0x00919E04) |
| PageControl/TabDevices/PageDongle/TabSettingsDongle/GroupProgramDongle/ButtonProgramDongle | TButton | Programma file |  | OnClick→`ButtonProgramDongleClick` (0x00919F80) |
| PageControl/TabDevices/PageRemote/TabSettingsMcu/GroupPropertyMcu/EditNomeMcu | TEdit |  |  | OnExit→`McuExit` (0x009179DC); OnKeyPress→`EditNomeMcuKeyPress` (0x00917A14) |
| PageControl/TabDevices/PageRemote/TabSettingsMcu/GroupPropertyMcu/EditIndirizzoMcu | TEdit |  |  | OnExit→`McuExit` (0x009179DC); OnKeyPress→`EditIndirizzoMcuKeyPress` (0x00917BC8) |
| PageControl/TabDevices/PageRemote/TabSettingsMcu/GroupPropertyMcu/ComboPotenzaMcu | TComboBox |  |  | OnChange→`ComboPotenzaMcuChange` (0x00917D24); OnExit→`McuExit` (0x009179DC) |
| PageControl/TabDevices/PageRemote/TabSettingsMcu/GroupProgramMcu/ButtonSfogliaMcu | TButton | Sfoglia... |  | OnClick→`ButtonSfogliaMcuClick` (0x0091ACF8) |
| PageControl/TabDevices/PageRemote/TabSettingsMcu/GroupProgramMcu/ButtonProgramMcu | TButton | Programma file |  | OnClick→`ButtonProgramMcuClick` (0x0091AE74) |
| PageControl/TabDevices/PagePin/TabPin/ButtonChangePin | TButton | Cambia PIN |  | OnClick→`ButtonChangePinClick` (0x00918158) |
| PageControl/TabDevices/PagePin/TabPin/ButtonRemovePin | TButton | Rimuovi PIN |  | OnClick→`ButtonRemovePinClick` (0x0091864C) |
| PageControl/TabDevices/PagePin/TabPin/ButtonReadPin | TButton | Leggi PIN |  | OnClick→`ButtonReadPinClick` (0x00917FA8) |
| PageControl/TabDevices/PagePin/TabPin/EditPin | TEdit |  |  | OnKeyPress→`EditPinKeyPress` (0x00918B1C) |
| PageControl/TabDevices/PageLink/TabLink/ComboCanale | TComboBox |  |  | OnChange→`ComboCanaleChange` (0x00917E88) |
| PageControl/TabScansione | TTabSheet | Gestione rete |  | OnShow→`TabScansioneShow` (0x00918B3C) |
| PageControl/TabScansione/GroupScan/ButtonAbort | TButton | Interrompi |  | OnClick→`ButtonAbortClick` (0x00919978) |
| PageControl/TabScansione/GroupScan/ButtonScan | TButton | Esegui scansione |  | OnClick→`ButtonScanClick` (0x00918BF4) |
| PageControl/TabScansione/GroupDevice/ListDevice | TListView |  |  | OnDblClick→`ListDeviceDblClick` (0x00919994) |
| PageControl/TabScansione/GroupDevice/ButtonConnectMcu | TButton | Connetti dispositivo remoto selezionato |  | OnClick→`ButtonConnectMcuClick` (0x009199C8) |
| PageControl/TabScansione/GroupDevice/ButtonDisconnectMcu | TButton | Disconnetti dispositivo remoto corrente |  | OnClick→`ButtonDisconnectMcuClick` (0x00919CD4) |
| PageControl/TabTest/ButtonScanEnergy | TButton | Esegui scansione |  | OnClick→`ButtonScanEnergyClick` (0x00915258) |
| PageControl/TabTest/ButtonStopEnergy | TButton | Interrompi |  | OnClick→`ButtonAbortClick` (0x00919978) |

## Caixas de diálogo (componentes) (1)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) | Filtro |
|---|---|---|---|---|---|
| OpenDialog | TOpenDialog |  |  |  |  |

## Temporizadores (1)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) | Intervalo ms |
|---|---|---|---|---|---|
| TimerUpdate | TTimer |  |  | OnTimer→`TimerUpdateTimer` (0x00914EA8) | 100 |

## Handlers (29): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `ButtonAbortClick` | 0x00919978 | 26 | INFERIDO | Ao clicar em ButtonAbort «Interrompi» (+1 componentes): corpo de 26 bytes sem fatos diretos (chama 0 funções sem nome). |  |  |  |
| `ButtonChangePinClick` | 0x00918158 | 1267 | INFERIDO | Ao clicar em ButtonChangePin «Cambia PIN»: corpo de 1267 bytes sem fatos diretos (chama 16 funções sem nome). |  |  |  |
| `ButtonConnectMcuClick` | 0x009199C8 | 777 | INFERIDO | Ao clicar em ButtonConnectMcu «Connetti dispositivo remoto selezionato»: corpo de 777 bytes sem fatos diretos (chama 13 funções sem nome). |  |  |  |
| `ButtonDisconnectMcuClick` | 0x00919CD4 | 303 | INFERIDO | Ao clicar em ButtonDisconnectMcu «Disconnetti dispositivo remoto corrente»: corpo de 303 bytes sem fatos diretos (chama 10 funções sem nome). |  |  |  |
| `ButtonProgramDongleClick` | 0x00919F80 | 3409 | INFERIDO | Ao clicar em ButtonProgramDongle «Programma file»: chama APIs Sleep. |  | Sleep |  |
| `ButtonProgramMcuClick` | 0x0091AE74 | 3685 | INFERIDO | Ao clicar em ButtonProgramMcu «Programma file»: chama APIs Sleep. |  | Sleep |  |
| `ButtonReadPinClick` | 0x00917FA8 | 398 | INFERIDO | Ao clicar em ButtonReadPin «Leggi PIN»: corpo de 398 bytes sem fatos diretos (chama 11 funções sem nome). |  |  |  |
| `ButtonRemovePinClick` | 0x0091864C | 1231 | INFERIDO | Ao clicar em ButtonRemovePin «Rimuovi PIN»: corpo de 1231 bytes sem fatos diretos (chama 17 funções sem nome). |  |  |  |
| `ButtonScanChannelClick` (sem evento no DFM) | 0x00915578 | 3829 | INFERIDO | Rotina publicada sem evento ligado no DFM (chamada pelo código): corpo de 3829 bytes sem fatos diretos (chama 16 funções sem nome). |  |  |  |
| `ButtonScanClick` | 0x00918BF4 | 2108 | INFERIDO | Ao clicar em ButtonScan «Esegui scansione»: corpo de 2108 bytes sem fatos diretos (chama 29 funções sem nome). |  |  |  |
| `ButtonScanEnergyClick` | 0x00915258 | 798 | INFERIDO | Ao clicar em ButtonScanEnergy «Esegui scansione»: chama APIs GetTickCount. |  | GetTickCount |  |
| `ButtonSfogliaDongleClick` | 0x00919E04 | 380 | INFERIDO | Ao clicar em ButtonSfogliaDongle «Sfoglia...»: corpo de 380 bytes sem fatos diretos (chama 11 funções sem nome). |  |  |  |
| `ButtonSfogliaMcuClick` | 0x0091ACF8 | 380 | INFERIDO | Ao clicar em ButtonSfogliaMcu «Sfoglia...»: corpo de 380 bytes sem fatos diretos (chama 11 funções sem nome). |  |  |  |
| `ComboCanaleChange` | 0x00917E88 | 287 | INFERIDO | Quando muda o valor de ComboCanale: corpo de 287 bytes sem fatos diretos (chama 10 funções sem nome). |  |  |  |
| `ComboPotenzaDongleChange` | 0x00917258 | 323 | INFERIDO | Quando muda o valor de ComboPotenzaDongle: corpo de 323 bytes sem fatos diretos (chama 10 funções sem nome). |  |  |  |
| `ComboPotenzaMcuChange` | 0x00917D24 | 323 | INFERIDO | Quando muda o valor de ComboPotenzaMcu: corpo de 323 bytes sem fatos diretos (chama 10 funções sem nome). |  |  |  |
| `DongleExit` | 0x00916DC0 | 56 | INFERIDO | Ao sair do campo EditNomeDongle (+2 componentes): corpo de 56 bytes sem fatos diretos (chama 2 funções sem nome). |  |  |  |
| `EditIndirizzoDongleKeyPress` | 0x00916FA8 | 687 | INFERIDO | Ao digitar (filtro de teclas) em EditIndirizzoDongle: corpo de 687 bytes sem fatos diretos (chama 15 funções sem nome). |  |  |  |
| `EditIndirizzoMcuKeyPress` | 0x00917BC8 | 347 | INFERIDO | Ao digitar (filtro de teclas) em EditIndirizzoMcu: corpo de 347 bytes sem fatos diretos (chama 12 funções sem nome). |  |  |  |
| `EditNomeDongleKeyPress` | 0x00916DF8 | 331 | INFERIDO | Ao digitar (filtro de teclas) em EditNomeDongle: corpo de 331 bytes sem fatos diretos (chama 11 funções sem nome). |  |  |  |
| `EditNomeMcuKeyPress` | 0x00917A14 | 331 | INFERIDO | Ao digitar (filtro de teclas) em EditNomeMcu: corpo de 331 bytes sem fatos diretos (chama 11 funções sem nome). |  |  |  |
| `EditPinKeyPress` | 0x00918B1C | 30 | INFERIDO | Ao digitar (filtro de teclas) em EditPin: corpo de 30 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `FormClose` | 0x00914DD0 | 144 | INFERIDO | Ao fechar o formulário AebBeeFormLight «Wireless Communication»: corpo de 144 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `FormCloseQuery` | 0x00914DA8 | 37 | INFERIDO | Ao pedir para fechar o formulário AebBeeFormLight «Wireless Communication»: corpo de 37 bytes sem fatos diretos (chama 0 funções sem nome). |  |  |  |
| `FormKeyPress` | 0x00914E80 | 38 | INFERIDO | Ao digitar (filtro de teclas) em AebBeeFormLight «Wireless Communication»: corpo de 38 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `ListDeviceDblClick` | 0x00919994 | 50 | INFERIDO | Ao dar duplo clique em ListDevice: corpo de 50 bytes sem fatos diretos (chama 0 funções sem nome). |  |  |  |
| `McuExit` | 0x009179DC | 56 | INFERIDO | Ao sair do campo EditNomeMcu (+2 componentes): corpo de 56 bytes sem fatos diretos (chama 2 funções sem nome). |  |  |  |
| `TabScansioneShow` | 0x00918B3C | 184 | INFERIDO | Ao exibir o formulário TabScansione «Gestione rete»: corpo de 184 bytes sem fatos diretos (chama 2 funções sem nome). |  |  |  |
| `TimerUpdateTimer` | 0x00914EA8 | 912 | INFERIDO | A cada tick do temporizador TimerUpdate (intervalo 100 ms): corpo de 912 bytes sem fatos diretos (chama 16 funções sem nome). |  |  |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `ButtonChangePinClick` (0x00918158):
  -     sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  - (+15 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonConnectMcuClick` (0x009199C8):
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  - (+19 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonDisconnectMcuClick` (0x00919CD4):
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  - (+13 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonProgramDongleClick` (0x00919F80):
  - sub_91C038 (524 B): APIs Sleep
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  -   sub_91C43C (404 B): APIs Sleep
  - (+34 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonProgramMcuClick` (0x0091AE74):
  - sub_91C038 (524 B): APIs Sleep
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  -   sub_91C43C (404 B): APIs Sleep
  - (+37 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonReadPinClick` (0x00917FA8):
  -     sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  - (+3 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonRemovePinClick` (0x0091864C):
  -     sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  - (+15 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonScanChannelClick` (0x00915578):
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  - (+18 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonScanClick` (0x00918BF4):
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  - (+23 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonScanEnergyClick` (0x00915258):
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  - (+5 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboCanaleChange` (0x00917E88):
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  - (+23 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboPotenzaDongleChange` (0x00917258):
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  - (+15 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboPotenzaMcuChange` (0x00917D24):
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  - (+13 subfunções sem fatos diretos ou fora da lista; telas.json)
- `DongleExit` (0x00916DC0):
  -     sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  - (+8 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditIndirizzoDongleKeyPress` (0x00916FA8):
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  - (+25 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditIndirizzoMcuKeyPress` (0x00917BC8):
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  - (+23 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditNomeDongleKeyPress` (0x00916DF8):
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  - (+17 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditNomeMcuKeyPress` (0x00917A14):
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  - (+15 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditPinKeyPress` (0x00918B1C):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `McuExit` (0x009179DC):
  -     sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  - (+5 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TabScansioneShow` (0x00918B3C):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TimerUpdateTimer` (0x00914EA8):
  -     sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  - (+17 subfunções sem fatos diretos ou fora da lista; telas.json)
