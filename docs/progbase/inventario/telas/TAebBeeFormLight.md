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
| `ButtonAbortClick` | 0x00919978 | 26 | INFERIDO | sem fatos diretos extraíveis (26 bytes; chama 0 funções sem nome) |  |  |  |
| `ButtonChangePinClick` | 0x00918158 | 1267 | INFERIDO | sem fatos diretos extraíveis (1267 bytes; chama 16 funções sem nome) |  |  |  |
| `ButtonConnectMcuClick` | 0x009199C8 | 777 | INFERIDO | sem fatos diretos extraíveis (777 bytes; chama 13 funções sem nome) |  |  |  |
| `ButtonDisconnectMcuClick` | 0x00919CD4 | 303 | INFERIDO | sem fatos diretos extraíveis (303 bytes; chama 10 funções sem nome) |  |  |  |
| `ButtonProgramDongleClick` | 0x00919F80 | 3409 | INFERIDO | chama APIs do Windows Sleep |  | Sleep |  |
| `ButtonProgramMcuClick` | 0x0091AE74 | 3685 | INFERIDO | chama APIs do Windows Sleep |  | Sleep |  |
| `ButtonReadPinClick` | 0x00917FA8 | 398 | INFERIDO | sem fatos diretos extraíveis (398 bytes; chama 11 funções sem nome) |  |  |  |
| `ButtonRemovePinClick` | 0x0091864C | 1231 | INFERIDO | sem fatos diretos extraíveis (1231 bytes; chama 17 funções sem nome) |  |  |  |
| `ButtonScanChannelClick` (sem evento no DFM) | 0x00915578 | 3829 | INFERIDO | sem fatos diretos extraíveis (3829 bytes; chama 16 funções sem nome) |  |  |  |
| `ButtonScanClick` | 0x00918BF4 | 2108 | INFERIDO | sem fatos diretos extraíveis (2108 bytes; chama 29 funções sem nome) |  |  |  |
| `ButtonScanEnergyClick` | 0x00915258 | 798 | INFERIDO | chama APIs do Windows GetTickCount |  | GetTickCount |  |
| `ButtonSfogliaDongleClick` | 0x00919E04 | 380 | INFERIDO | sem fatos diretos extraíveis (380 bytes; chama 11 funções sem nome) |  |  |  |
| `ButtonSfogliaMcuClick` | 0x0091ACF8 | 380 | INFERIDO | sem fatos diretos extraíveis (380 bytes; chama 11 funções sem nome) |  |  |  |
| `ComboCanaleChange` | 0x00917E88 | 287 | INFERIDO | sem fatos diretos extraíveis (287 bytes; chama 10 funções sem nome) |  |  |  |
| `ComboPotenzaDongleChange` | 0x00917258 | 323 | INFERIDO | sem fatos diretos extraíveis (323 bytes; chama 10 funções sem nome) |  |  |  |
| `ComboPotenzaMcuChange` | 0x00917D24 | 323 | INFERIDO | sem fatos diretos extraíveis (323 bytes; chama 10 funções sem nome) |  |  |  |
| `DongleExit` | 0x00916DC0 | 56 | INFERIDO | sem fatos diretos extraíveis (56 bytes; chama 2 funções sem nome) |  |  |  |
| `EditIndirizzoDongleKeyPress` | 0x00916FA8 | 687 | INFERIDO | sem fatos diretos extraíveis (687 bytes; chama 15 funções sem nome) |  |  |  |
| `EditIndirizzoMcuKeyPress` | 0x00917BC8 | 347 | INFERIDO | sem fatos diretos extraíveis (347 bytes; chama 12 funções sem nome) |  |  |  |
| `EditNomeDongleKeyPress` | 0x00916DF8 | 331 | INFERIDO | sem fatos diretos extraíveis (331 bytes; chama 11 funções sem nome) |  |  |  |
| `EditNomeMcuKeyPress` | 0x00917A14 | 331 | INFERIDO | sem fatos diretos extraíveis (331 bytes; chama 11 funções sem nome) |  |  |  |
| `EditPinKeyPress` | 0x00918B1C | 30 | INFERIDO | sem fatos diretos extraíveis (30 bytes; chama 1 funções sem nome) |  |  |  |
| `FormClose` | 0x00914DD0 | 144 | INFERIDO | sem fatos diretos extraíveis (144 bytes; chama 1 funções sem nome) |  |  |  |
| `FormCloseQuery` | 0x00914DA8 | 37 | INFERIDO | sem fatos diretos extraíveis (37 bytes; chama 0 funções sem nome) |  |  |  |
| `FormKeyPress` | 0x00914E80 | 38 | INFERIDO | sem fatos diretos extraíveis (38 bytes; chama 1 funções sem nome) |  |  |  |
| `ListDeviceDblClick` | 0x00919994 | 50 | INFERIDO | sem fatos diretos extraíveis (50 bytes; chama 0 funções sem nome) |  |  |  |
| `McuExit` | 0x009179DC | 56 | INFERIDO | sem fatos diretos extraíveis (56 bytes; chama 2 funções sem nome) |  |  |  |
| `TabScansioneShow` | 0x00918B3C | 184 | INFERIDO | sem fatos diretos extraíveis (184 bytes; chama 2 funções sem nome) |  |  |  |
| `TimerUpdateTimer` | 0x00914EA8 | 912 | INFERIDO | sem fatos diretos extraíveis (912 bytes; chama 16 funções sem nome) |  |  |  |
