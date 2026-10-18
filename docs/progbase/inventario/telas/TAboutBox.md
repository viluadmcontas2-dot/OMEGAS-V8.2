# TAboutBox (recurso `TABOUTBOX`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `AboutBox`; unidade: `@:About`; ancestral: `TForm`; tamanho de instância: 0x43C; componentes no DFM: 23.
- Título: "About"
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `TFormMain.Create` (0x004EDE60), `TFormMain.MenuAboutClick` (0x004F7808)

## Controles com evento/ação (8)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| AboutBox | TAboutBox | About |  | OnKeyPress→`FormKeyPress` (0x004813CC) |
| OkButton | TButton | &Exit |  | OnClick→`OkButtonClick` (0x004813DC) |
| PanelAbout/AboutImage | TImage |  |  | OnDblClick→`AboutImageDblClick` (0x00481350) |
| PanelAbout/PageControl/TabUserCode/ButtonSendByMail | TButton | Send by mail |  | OnClick→`ButtonSendByMailClick` (0x00481770) |
| PanelAbout/PageControl/TabUserCode/ButtonDeleteLicense | TButton | Delete current license file |  | OnClick→`ButtonDeleteLicenseClick` (0x00481FF8) |
| PanelAbout/PageControl/TabLicense/ButtonRequest | TButton | Request a new license |  | OnClick→`ButtonRequestClick` (0x00482254) |
| PanelAbout/PageControl/TabLicense/ButtonLoadLicense | TButton | Load a license file |  | OnClick→`ButtonLoadLicenseClick` (0x00481D40) |
| PanelAbout/PageControl/TabLicense/EditRegCode | TEdit |  |  | OnKeyPress→`EditRegCodeKeyPress` (0x00481744); OnKeyUp→`EditRegCodeKeyUp` (0x004815A8) |

## Caixas de diálogo (componentes) (1)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) | Filtro |
|---|---|---|---|---|---|
| OpenDialog | TOpenDialog |  |  |  | License file\|*.dat |

## Handlers (9): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `AboutImageDblClick` | 0x00481350 | 124 | INFERIDO | toca Copyright, LabelDaysLeft, TabLicense, TabUserCode |  |  |  |
| `ButtonDeleteLicenseClick` | 0x00481FF8 | 602 | INFERIDO | sem fatos diretos extraíveis (602 bytes; chama 9 funções sem nome) |  |  |  |
| `ButtonLoadLicenseClick` | 0x00481D40 | 693 | INFERIDO | chama APIs do Windows CopyFileW; toca OpenDialog |  | CopyFileW |  |
| `ButtonRequestClick` | 0x00482254 | 742 | INFERIDO | chama APIs do Windows ShellExecuteW |  | ShellExecuteW | GetComputerNameW, InternetAttemptConnect, InternetConnectW, InternetGetConnectedState |
| `ButtonSendByMailClick` | 0x00481770 | 1487 | INFERIDO | toca MemoUserCode |  |  |  |
| `EditRegCodeKeyPress` | 0x00481744 | 41 | INFERIDO | sem fatos diretos extraíveis (41 bytes; chama 0 funções sem nome) |  |  |  |
| `EditRegCodeKeyUp` | 0x004815A8 | 411 | INFERIDO | toca EditRegCode |  |  |  |
| `FormKeyPress` | 0x004813CC | 14 | INFERIDO | sem fatos diretos extraíveis (14 bytes; chama 1 funções sem nome) |  |  |  |
| `OkButtonClick` | 0x004813DC | 458 | INFERIDO | toca EditRegCode |  |  |  |
