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

### Cadeia de subfunções sem nome alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `ButtonChangePinClick` (0x00918158):
  - sub_40487C (45 B)
  - sub_910F38 (25 B)
  - sub_912948 (773 B)
  - sub_917554 (1157 B)
  - sub_918138 (29 B)
  -   sub_911A50 (106 B)
  -   sub_911BD8 (41 B)
  -   sub_912C50 (142 B)
  -   sub_912CE0 (142 B)
  -   sub_9137AC (60 B)
  -   sub_913524 (621 B): toca BarraCanali
  -   sub_9173BC (250 B)
  - … +6 (ver telas.json)
- `ButtonConnectMcuClick` (0x009199C8):
  - sub_40487C (45 B)
  - sub_910F38 (25 B)
  - sub_91185C (353 B): toca ButtonAbort
  - sub_911ABC (111 B)
  - sub_911BD8 (41 B)
  - sub_9130D4 (74 B)
  - sub_9137AC (60 B)
  - sub_917554 (1157 B)
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  -   sub_9119C0 (141 B)
  -   sub_911A50 (106 B)
  -   sub_911B2C (69 B)
  - … +9 (ver telas.json)
- `ButtonDisconnectMcuClick` (0x00919CD4):
  - sub_40487C (45 B)
  - sub_910F38 (25 B)
  - sub_911ABC (111 B)
  - sub_911BD8 (41 B)
  - sub_917554 (1157 B)
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  -   sub_911A50 (106 B)
  -   sub_913524 (621 B): toca BarraCanali
  -   sub_9173BC (250 B)
  -   sub_9174B8 (156 B)
  -     sub_911464 (190 B)
  -     sub_9115E4 (65 B)
  - … +3 (ver telas.json)
- `ButtonProgramDongleClick` (0x00919F80):
  - sub_40487C (45 B)
  - sub_910F38 (25 B)
  - sub_912E00 (40 B)
  - sub_916534 (364 B)
  - sub_9166A0 (340 B)
  - sub_9167F4 (1483 B)
  - sub_9173BC (250 B)
  - sub_9174B8 (156 B)
  - sub_917554 (1157 B)
  - sub_91BF5C (130 B)
  - sub_91C038 (524 B): APIs Sleep
  - sub_91C878 (711 B)
  - … +27 (ver telas.json)
- `ButtonProgramMcuClick` (0x0091AE74):
  - sub_40487C (45 B)
  - sub_910F38 (25 B)
  - sub_912E28 (66 B)
  - sub_9137AC (60 B)
  - sub_9167F4 (1483 B)
  - sub_9173BC (250 B)
  - sub_9174B8 (156 B)
  - sub_917554 (1157 B)
  - sub_91BCDC (23 B)
  - sub_91BF5C (130 B)
  - sub_91C038 (524 B): APIs Sleep
  - sub_91C878 (711 B)
  - … +30 (ver telas.json)
- `ButtonReadPinClick` (0x00917FA8):
  - sub_40487C (45 B)
  - sub_910F38 (25 B)
  - sub_918138 (29 B)
  -   sub_91283C (268 B)
  -     sub_4048AC (68 B)
  -     sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
- `ButtonRemovePinClick` (0x0091864C):
  - sub_40487C (45 B)
  - sub_910F38 (25 B)
  - sub_912D70 (143 B)
  - sub_917554 (1157 B)
  - sub_918138 (29 B)
  -   sub_912948 (773 B)
  -   sub_913524 (621 B): toca BarraCanali
  -   sub_9173BC (250 B)
  -   sub_9174B8 (156 B)
  -   sub_91283C (268 B)
  -     sub_911A50 (106 B)
  -     sub_911BD8 (41 B)
  - … +6 (ver telas.json)
- `ButtonScanChannelClick` (0x00915578):
  - sub_40487C (45 B)
  - sub_910F38 (25 B)
  - sub_911B2C (69 B)
  - sub_91394C (291 B)
  - sub_915238 (32 B)
  - sub_916470 (29 B)
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  -   sub_911B74 (99 B)
  -   sub_911BD8 (41 B)
  -   sub_912244 (23 B)
  -   sub_913524 (621 B): toca BarraCanali
  -   sub_9137AC (60 B)
  - … +8 (ver telas.json)
- `ButtonScanClick` (0x00918BF4):
  - sub_40487C (45 B)
  - sub_910F38 (25 B)
  - sub_91185C (353 B): toca ButtonAbort
  - sub_913120 (300 B)
  - sub_91324C (87 B)
  - sub_919430 (28 B)
  - sub_91944C (19 B)
  - sub_919460 (33 B)
  - sub_919484 (33 B)
  - sub_9194A8 (847 B)
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  -   sub_9119C0 (141 B)
  - … +15 (ver telas.json)
- `ButtonScanEnergyClick` (0x00915258):
  - sub_40487C (45 B)
  - sub_911D18 (101 B)
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  -     sub_911464 (190 B)
  -     sub_9115E4 (65 B)
  -     sub_911628 (21 B)
  -     sub_911640 (21 B)
- `ButtonSfogliaDongleClick` (0x00919E04):
  - sub_40487C (45 B)
- `ButtonSfogliaMcuClick` (0x0091ACF8):
  - sub_40487C (45 B)
- `ComboCanaleChange` (0x00917E88):
  - sub_40487C (45 B)
  - sub_910F38 (25 B)
  - sub_91394C (291 B)
  - sub_9167F4 (1483 B)
  - sub_917554 (1157 B)
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  -   sub_911B74 (99 B)
  -   sub_911BD8 (41 B)
  -   sub_912244 (23 B)
  -   sub_913524 (621 B): toca BarraCanali
  -   sub_9137AC (60 B)
  -   sub_911658 (495 B)
  - … +13 (ver telas.json)
- `ComboPotenzaDongleChange` (0x00917258):
  - sub_40487C (45 B)
  - sub_910F38 (25 B)
  - sub_911BD8 (41 B)
  - sub_9167F4 (1483 B)
  - sub_91739C (29 B)
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  -   sub_911658 (495 B)
  -   sub_916534 (364 B)
  -   sub_9166A0 (340 B)
  -   sub_912284 (80 B)
  -     sub_911464 (190 B)
  -     sub_9115E4 (65 B)
  - … +5 (ver telas.json)
- `ComboPotenzaMcuChange` (0x00917D24):
  - sub_40487C (45 B)
  - sub_910F38 (25 B)
  - sub_9137AC (60 B)
  - sub_917554 (1157 B)
  - sub_917E68 (32 B)
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  -   sub_913524 (621 B): toca BarraCanali
  -   sub_9173BC (250 B)
  -   sub_9174B8 (156 B)
  -   sub_912284 (80 B)
  -     sub_911464 (190 B)
  -     sub_9115E4 (65 B)
  - … +3 (ver telas.json)
- `DongleExit` (0x00916DC0):
  - sub_9167F4 (1483 B)
  -   sub_40487C (45 B)
  -   sub_910F38 (25 B)
  -   sub_911658 (495 B)
  -   sub_916534 (364 B)
  -   sub_9166A0 (340 B)
  -     sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  -     sub_911848 (20 B)
  -     sub_911A50 (106 B)
  -     sub_911B2C (69 B)
- `EditIndirizzoDongleKeyPress` (0x00916FA8):
  - sub_40487C (45 B)
  - sub_910F38 (25 B)
  - sub_91185C (353 B): toca ButtonAbort
  - sub_911BD8 (41 B)
  - sub_912EA8 (127 B)
  - sub_9137AC (60 B)
  - sub_916490 (62 B)
  - sub_9167F4 (1483 B)
  - sub_917554 (1157 B)
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  -   sub_9119C0 (141 B)
  -   sub_911A50 (106 B)
  - … +15 (ver telas.json)
- `EditIndirizzoMcuKeyPress` (0x00917BC8):
  - sub_40487C (45 B)
  - sub_910F38 (25 B)
  - sub_913808 (322 B)
  - sub_916490 (62 B)
  - sub_9167F4 (1483 B)
  - sub_917554 (1157 B)
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  -   sub_9119C0 (141 B)
  -   sub_911BD8 (41 B)
  -   sub_912FBC (37 B)
  -   sub_9137AC (60 B)
  -   sub_911658 (495 B)
  - … +13 (ver telas.json)
- `EditNomeDongleKeyPress` (0x00916DF8):
  - sub_40487C (45 B)
  - sub_910F38 (25 B)
  - sub_911BD8 (41 B)
  - sub_9164D0 (98 B)
  - sub_9167F4 (1483 B)
  - sub_916F44 (100 B)
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  -   sub_911658 (495 B)
  -   sub_916534 (364 B)
  -   sub_9166A0 (340 B)
  -   sub_911C04 (247 B)
  -     sub_911464 (190 B)
  - … +9 (ver telas.json)
- `EditNomeMcuKeyPress` (0x00917A14):
  - sub_40487C (45 B)
  - sub_910F38 (25 B)
  - sub_9137AC (60 B)
  - sub_9164D0 (98 B)
  - sub_917554 (1157 B)
  - sub_917B60 (103 B)
  -   sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  -   sub_913524 (621 B): toca BarraCanali
  -   sub_9173BC (250 B)
  -   sub_9174B8 (156 B)
  -   sub_911C04 (247 B)
  -     sub_911464 (190 B)
  - … +7 (ver telas.json)
- `EditPinKeyPress` (0x00918B1C):
  - sub_9164D0 (98 B)
- `McuExit` (0x009179DC):
  - sub_917554 (1157 B)
  -   sub_40487C (45 B)
  -   sub_913524 (621 B): toca BarraCanali
  -   sub_9173BC (250 B)
  -   sub_9174B8 (156 B)
  -     sub_911288 (473 B): chama TAebFtdi.vmt[0x2C],TAebFtdi.vmt[0x30]
  -     sub_913794 (23 B)
- `TabScansioneShow` (0x00918B3C):
  - sub_9194A8 (847 B)
  -   sub_40487C (45 B)
- `TimerUpdateTimer` (0x00914EA8):
  - sub_40487C (45 B)
  - sub_91225C (19 B)
  - sub_912270 (19 B)
  - sub_915238 (32 B)
  - sub_9167F4 (1483 B)
  - sub_917554 (1157 B)
  -   sub_911D80 (90 B)
  -   sub_910F38 (25 B)
  -   sub_911658 (495 B)
  -   sub_916534 (364 B)
  -   sub_9166A0 (340 B)
  -   sub_913524 (621 B): toca BarraCanali
  - … +7 (ver telas.json)
