# TAboutBox (recurso `TABOUTBOX`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `AboutBox`; unidade: `@:About`; ancestral: `TForm`; tamanho de instância: 0x43C; componentes no DFM: 23.
- Título: "About"
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

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

| Handler | Endereço | Bytes | Evidência | O que faz | Textos (chaves i18n) | APIs | Chama |
|---|---|---:|---|---|---|---|---|
| `AboutImageDblClick` | 0x00481350 | 124 | INFERIDO | Handler `AboutImageDblClick`; toca campos/controles: Copyright, LabelDaysLeft, TabLicense, TabUserCode |  |  |  |
| `ButtonDeleteLicenseClick` | 0x00481FF8 | 602 | INFERIDO | Handler `ButtonDeleteLicenseClick`; sem fatos extraíveis automaticamente (602 bytes) |  |  |  |
| `ButtonLoadLicenseClick` | 0x00481D40 | 693 | INFERIDO | Handler `ButtonLoadLicenseClick`; chama APIs do Windows: KERNEL32.DLL!CopyFileW; chama: thunk -> KERNEL32.DLL!CopyFileW; toca campos/controles: OpenDialog |  | KERNEL32.DLL!CopyFileW | thunk -> KERNEL32.DLL!CopyFileW |
| `ButtonRequestClick` | 0x00482254 | 742 | INFERIDO | Handler `ButtonRequestClick`; chama APIs do Windows: SHELL32.DLL!ShellExecuteW, KERNEL32.DLL!GetComputerNameW, WININET.DLL!InternetAttemptConnect, WININET.DLL!InternetConnectW, WININET.DLL!InternetGetConnectedState, WININET.DLL!InternetOpenW; chama: thunk -> SHELL32.DLL!ShellExecuteW |  | SHELL32.DLL!ShellExecuteW, KERNEL32.DLL!GetComputerNameW, WININET.DLL!InternetAttemptConnect, WININET.DLL!InternetConnectW, WININET.DLL!InternetGetConnectedState | thunk -> SHELL32.DLL!ShellExecuteW |
| `ButtonSendByMailClick` | 0x00481770 | 1487 | INFERIDO | Handler `ButtonSendByMailClick`; toca campos/controles: MemoUserCode |  |  |  |
| `EditRegCodeKeyPress` | 0x00481744 | 41 | INFERIDO | Handler `EditRegCodeKeyPress`; sem fatos extraíveis automaticamente (41 bytes) |  |  |  |
| `EditRegCodeKeyUp` | 0x004815A8 | 411 | INFERIDO | Handler `EditRegCodeKeyUp`; toca campos/controles: EditRegCode |  |  |  |
| `FormKeyPress` | 0x004813CC | 14 | INFERIDO | Handler `FormKeyPress`; sem fatos extraíveis automaticamente (14 bytes) |  |  |  |
| `OkButtonClick` | 0x004813DC | 458 | INFERIDO | Handler `OkButtonClick`; toca campos/controles: EditRegCode |  |  |  |
