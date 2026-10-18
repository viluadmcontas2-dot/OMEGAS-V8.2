# TAebBeeFormLight (recurso `TAEBBEEFORMLIGHT`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `AebBeeFormLight`; unidade: `@:AebBeeLight`; ancestral: `TForm`; tamanho de instância: 0x554; componentes no DFM: 98.
- Título: "Wireless Communication"
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

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

| Handler | Endereço | Bytes | Evidência | O que faz | Textos (chaves i18n) | APIs | Chama |
|---|---|---:|---|---|---|---|---|
| `ButtonAbortClick` | 0x00919978 | 26 | INFERIDO | Handler `ButtonAbortClick`; sem fatos extraíveis automaticamente (26 bytes) |  |  |  |
| `ButtonChangePinClick` | 0x00918158 | 1267 | INFERIDO | Handler `ButtonChangePinClick`; sem fatos extraíveis automaticamente (1267 bytes) |  |  |  |
| `ButtonConnectMcuClick` | 0x009199C8 | 777 | INFERIDO | Handler `ButtonConnectMcuClick`; sem fatos extraíveis automaticamente (777 bytes) |  |  |  |
| `ButtonDisconnectMcuClick` | 0x00919CD4 | 303 | INFERIDO | Handler `ButtonDisconnectMcuClick`; sem fatos extraíveis automaticamente (303 bytes) |  |  |  |
| `ButtonProgramDongleClick` | 0x00919F80 | 3409 | INFERIDO | Handler `ButtonProgramDongleClick`; chama APIs do Windows: KERNEL32.DLL!Sleep; chama: thunk -> KERNEL32.DLL!Sleep |  | KERNEL32.DLL!Sleep | thunk -> KERNEL32.DLL!Sleep |
| `ButtonProgramMcuClick` | 0x0091AE74 | 3685 | INFERIDO | Handler `ButtonProgramMcuClick`; chama APIs do Windows: KERNEL32.DLL!Sleep; chama: thunk -> KERNEL32.DLL!Sleep |  | KERNEL32.DLL!Sleep | thunk -> KERNEL32.DLL!Sleep |
| `ButtonReadPinClick` | 0x00917FA8 | 398 | INFERIDO | Handler `ButtonReadPinClick`; sem fatos extraíveis automaticamente (398 bytes) |  |  |  |
| `ButtonRemovePinClick` | 0x0091864C | 1231 | INFERIDO | Handler `ButtonRemovePinClick`; sem fatos extraíveis automaticamente (1231 bytes) |  |  |  |
| `ButtonScanChannelClick` (sem evento no DFM) | 0x00915578 | 3829 | INFERIDO | Handler `ButtonScanChannelClick`; sem fatos extraíveis automaticamente (3829 bytes) |  |  |  |
| `ButtonScanClick` | 0x00918BF4 | 2108 | INFERIDO | Handler `ButtonScanClick`; sem fatos extraíveis automaticamente (2108 bytes) |  |  |  |
| `ButtonScanEnergyClick` | 0x00915258 | 798 | INFERIDO | Handler `ButtonScanEnergyClick`; chama APIs do Windows: KERNEL32.DLL!GetTickCount; chama: thunk -> KERNEL32.DLL!GetTickCount |  | KERNEL32.DLL!GetTickCount | thunk -> KERNEL32.DLL!GetTickCount |
| `ButtonSfogliaDongleClick` | 0x00919E04 | 380 | INFERIDO | Handler `ButtonSfogliaDongleClick`; sem fatos extraíveis automaticamente (380 bytes) |  |  |  |
| `ButtonSfogliaMcuClick` | 0x0091ACF8 | 380 | INFERIDO | Handler `ButtonSfogliaMcuClick`; sem fatos extraíveis automaticamente (380 bytes) |  |  |  |
| `ComboCanaleChange` | 0x00917E88 | 287 | INFERIDO | Handler `ComboCanaleChange`; sem fatos extraíveis automaticamente (287 bytes) |  |  |  |
| `ComboPotenzaDongleChange` | 0x00917258 | 323 | INFERIDO | Handler `ComboPotenzaDongleChange`; sem fatos extraíveis automaticamente (323 bytes) |  |  |  |
| `ComboPotenzaMcuChange` | 0x00917D24 | 323 | INFERIDO | Handler `ComboPotenzaMcuChange`; sem fatos extraíveis automaticamente (323 bytes) |  |  |  |
| `DongleExit` | 0x00916DC0 | 56 | INFERIDO | Handler `DongleExit`; sem fatos extraíveis automaticamente (56 bytes) |  |  |  |
| `EditIndirizzoDongleKeyPress` | 0x00916FA8 | 687 | INFERIDO | Handler `EditIndirizzoDongleKeyPress`; sem fatos extraíveis automaticamente (687 bytes) |  |  |  |
| `EditIndirizzoMcuKeyPress` | 0x00917BC8 | 347 | INFERIDO | Handler `EditIndirizzoMcuKeyPress`; sem fatos extraíveis automaticamente (347 bytes) |  |  |  |
| `EditNomeDongleKeyPress` | 0x00916DF8 | 331 | INFERIDO | Handler `EditNomeDongleKeyPress`; sem fatos extraíveis automaticamente (331 bytes) |  |  |  |
| `EditNomeMcuKeyPress` | 0x00917A14 | 331 | INFERIDO | Handler `EditNomeMcuKeyPress`; sem fatos extraíveis automaticamente (331 bytes) |  |  |  |
| `EditPinKeyPress` | 0x00918B1C | 30 | INFERIDO | Handler `EditPinKeyPress`; sem fatos extraíveis automaticamente (30 bytes) |  |  |  |
| `FormClose` | 0x00914DD0 | 144 | INFERIDO | Handler `FormClose`; sem fatos extraíveis automaticamente (144 bytes) |  |  |  |
| `FormCloseQuery` | 0x00914DA8 | 37 | INFERIDO | Handler `FormCloseQuery`; sem fatos extraíveis automaticamente (37 bytes) |  |  |  |
| `FormKeyPress` | 0x00914E80 | 38 | INFERIDO | Handler `FormKeyPress`; sem fatos extraíveis automaticamente (38 bytes) |  |  |  |
| `ListDeviceDblClick` | 0x00919994 | 50 | INFERIDO | Handler `ListDeviceDblClick`; sem fatos extraíveis automaticamente (50 bytes) |  |  |  |
| `McuExit` | 0x009179DC | 56 | INFERIDO | Handler `McuExit`; sem fatos extraíveis automaticamente (56 bytes) |  |  |  |
| `TabScansioneShow` | 0x00918B3C | 184 | INFERIDO | Handler `TabScansioneShow`; sem fatos extraíveis automaticamente (184 bytes) |  |  |  |
| `TimerUpdateTimer` | 0x00914EA8 | 912 | INFERIDO | Handler `TimerUpdateTimer`; sem fatos extraíveis automaticamente (912 bytes) |  |  |  |
