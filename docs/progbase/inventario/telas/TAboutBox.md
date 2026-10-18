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
| `AboutImageDblClick` | 0x00481350 | 124 | INFERIDO | Duplo clique na imagem: alterna a visibilidade das abas ocultas (Licença/Código do usuário) e do rótulo de dias restantes. OBS-COD (campos tocados); propósito INFERIDO. |  |  |  |
| `ButtonDeleteLicenseClick` | 0x00481FF8 | 602 | DERIVADO | Botão "Apagar arquivo de licença atual": remove "\License.dat". Só descrição da função. OBS-COD (literal). |  |  |  |
| `ButtonLoadLicenseClick` | 0x00481D40 | 693 | OBS-COD | Botão "Carregar arquivo de licença": abre diálogo e copia o arquivo escolhido para "\License.dat" (CopyFileW). Só descrição da função. OBS-COD. |  | CopyFileW |  |
| `ButtonRequestClick` | 0x00482254 | 742 | OBS-COD | Botão "Solicitar nova licença": abre o navegador (ShellExecuteW) numa página do servidor do fabricante (…LastVersion.aspx?Mode=Ext&ProgramSelector=…&VerSw=…&UserCode=…), enviando versão e código do usuário. Só descrição da função. OBS-COD (APIs/literais). |  | ShellExecuteW | InternetAttemptConnect, InternetConnectW, InternetGetConnectedState, InternetOpenW |
| `ButtonSendByMailClick` | 0x00481770 | 1487 | OBS-COD | Botão "Enviar por e-mail": monta uma mensagem (MAPI) para info@serviceapplications.net com o código do usuário (assunto "Omegas Landi Renzo User Code"); avisa se o anexo não for encontrado. OBS-COD (literais). |  |  |  |
| `EditRegCodeKeyPress` | 0x00481744 | 41 | INFERIDO | Ao digitar (filtro de teclas) em EditRegCode: corpo de 41 bytes sem fatos diretos (chama 0 funções sem nome). |  |  |  |
| `EditRegCodeKeyUp` | 0x004815A8 | 411 | INFERIDO | Valida/formata o código de registro conforme é digitado (campo EditRegCode). INFERIDO (nome+campo). |  |  |  |
| `FormKeyPress` | 0x004813CC | 14 | INFERIDO | Ao digitar (filtro de teclas) em AboutBox «About»: corpo de 14 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `OkButtonClick` | 0x004813DC | 458 | DERIVADO | Botão Sair/OK: grava o código de registro digitado (EditRegCode) no registro do Windows (\Software\…\RegCode) e fecha. OBS-COD (literais). |  |  |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `ButtonRequestClick` (0x00482254):
  - sub_4EA9F8 (772 B): APIs InternetAttemptConnect,InternetConnectW,InternetGetConnectedState,InternetOpenW
  - (+0 subfunções sem fatos diretos ou fora da lista; telas.json)
