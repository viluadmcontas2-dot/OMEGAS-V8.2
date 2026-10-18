# TFormMain (recurso `TFORMMAIN`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormMain`; unidade: `@:Main`; ancestral: `TFormMainBase`; tamanho de instância: 0x63C; componentes no DFM: 84.
- Título: "Initializing..."
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` (0x004111F8)

## Menus (24)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| MainMenu/MenuFile | TMenuItem | &File |  |  |
| MainMenu/MenuFile/MenuApri | TMenuItem | &Apri configurazione... / &Open configuration... / &Abrir configuração... |  | ActionApri.OnExecute→`ActionApriExecute` (0x004FE618) |
| MainMenu/MenuFile/MenuBloccoNote | TMenuItem | Apri configurazione in &blocco note... |  | ActionBloccoNote.OnExecute→`ActionBloccoNoteExecute` (0x004FFE18) |
| MainMenu/MenuFile/MenuSalva | TMenuItem | &Salva configurazione... / &Save configuration... / &Guardar configuração... |  | ActionSalva.OnExecute→`ActionSalvaExecute` (0x004FEA54) |
| MainMenu/MenuFile/MenuSalvaMappa | TMenuItem | Salva solo mappa in configurazione... |  | ActionSalvaMappa.OnExecute→`ActionSalvaMappaExecute` (0x004FFE20) |
| MainMenu/MenuFile/MenuFileHcf | TMenuItem | Salva configurazione su file hcf... |  | ActionFileHcf.OnExecute→`ActionFileHcfExecute` (0x004FFE1C) |
| MainMenu/MenuFile/N3 | TMenuItem | - |  |  |
| MainMenu/MenuFile/Plot1 | TMenuItem | Graph |  | OnClick→`Plot1Click` (0x0050417C) |
| MainMenu/MenuFile/N4 | TMenuItem | - |  |  |
| MainMenu/MenuFile/MenuArchivio | TMenuItem | Istruzioni di montaggio / Installation instructions / Instruções de montagem |  | ActionArchivio.OnExecute→`ActionArchivioExecute` (0x004FF6C4) |
| MainMenu/MenuFile/N1 | TMenuItem | - |  |  |
| MainMenu/MenuFile/MenuEsci | TMenuItem | Esci / Exit / Sair |  | ActionEsci.OnExecute→`ActionEsciExecute` (0x004F8B74) |
| MainMenu/MenuCpu | TMenuItem | &Connessione / &Connection / &Conexão |  |  |
| MainMenu/MenuCpu/MenuConnetti | TMenuItem | &Connetti / &Connect / &Ligar |  | ActionConnetti.OnExecute→`ActionConnettiExecute` (0x004FB840) |
| MainMenu/MenuCpu/MenuDisconnetti | TMenuItem | &Disconnetti / &Disconnect / &Desconectar |  | ActionDisconnetti.OnExecute→`ActionDisconnettiExecute` (0x004FB858) |
| MainMenu/MenuCpu/N2 | TMenuItem | - |  |  |
| MainMenu/MenuCpu/MenuResetCpu | TMenuItem | &Reset centralina / &Reset ECU / &Reset ECU |  | ActionResetCentralina.OnExecute→`ActionResetCentralinaExecute` (0x00501244) |
| MainMenu/MenuCpu/N5 | TMenuItem | - |  |  |
| MainMenu/MenuCpu/MenuClientPers | TMenuItem | Personalizzazione / Personalization / Personalization |  |  |
| MainMenu/MenuConfig | TMenuItem | Impostazioni / Settings / None |  |  |
| MainMenu/MenuConfig/MenuSelLingua | TMenuItem | Lingua / Language / Idioma |  |  |
| MainMenu/MenuAiuto | TMenuItem | &Aiuto / &Help / &Ajuda |  |  |
| MainMenu/MenuAiuto/MenuAbout | TMenuItem | &Informazioni su... / &About... / &Informações sobre... |  | OnClick→`MenuAboutClick` (0x004F7808) |
| MainMenu/MenuMarket | TMenuItem | Market |  |  |

## Ações (22)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| MainActionList/ActionArchivio | TAction | Archivio schede |  | OnExecute→`ActionArchivioExecute` (0x004FF6C4) |
| MainActionList/ActionVisualizza | TAction |  |  | OnExecute→`ActionVisualizzaExecute` (0x00501568) |
| MainActionList/ActionConf | TAction |  |  | OnExecute→`ActionConfExecute` (0x004F95F4) |
| MainActionList/ActionDiagnosi | TAction |  |  | OnExecute→`ActionDiagnosiExecute` (0x004FFE24) |
| MainActionList/ActionEsci | TAction |  |  | OnExecute→`ActionEsciExecute` (0x004F8B74) |
| MainActionList/ActionApri | TAction | &Apri |  | OnExecute→`ActionApriExecute` (0x004FE618) |
| MainActionList/ActionSalva | TAction | &Salva |  | OnExecute→`ActionSalvaExecute` (0x004FEA54) |
| MainActionList/ActionConnetti | TAction | &Connetti / &Connect / &Ligar | Ctrl+C | OnExecute→`ActionConnettiExecute` (0x004FB840) |
| MainActionList/ActionDisconnetti | TAction | &Disconnetti / &Disconnect / &Desconectar | Ctrl+D | OnExecute→`ActionDisconnettiExecute` (0x004FB858) |
| MainActionList/ActionProgramma | TAction |  |  | OnExecute→`ActionProgrammaExecute` (0x004FDBD4) |
| MainActionList/ActionBloccoNote | TAction | Apri configurazione in &blocco note |  | OnExecute→`ActionBloccoNoteExecute` (0x004FFE18) |
| MainActionList/ActionSalvaConf | TAction |  |  | OnExecute→`ActionSalvaConfExecute` (0x005000CC) |
| MainActionList/ActionApriConf | TAction |  |  | OnExecute→`ActionApriConfExecute` (0x00500314) |
| MainActionList/ActionUpdate | TAction |  |  | OnExecute→`ActionUpdateExecute` (0x005035D4) |
| MainActionList/ActionAggiorna | TAction |  |  | OnExecute→`ActionAggiornaExecute` (0x005035DC) |
| MainActionList/ActionResetCentralina | TAction | &Reset centralina / &Reset ECU / &Reset ECU |  | OnExecute→`ActionResetCentralinaExecute` (0x00501244) |
| MainActionList/ActionTara | TAction | ActionTara |  | OnExecute→`ActionTaraExecute` (0x00500DDC) |
| MainActionList/ActionCorrTara | TAction | ActionCorrTara |  | OnExecute→`ActionCorrTaraExecute` (0x00500DE4) |
| MainActionList/ActionResetDaPc | TAction | ActionResetDaPc |  | OnExecute→`ActionResetDaPcExecute` (0x00501248) |
| MainActionList/ActionFileHcf | TAction | Salva configurazione su file hcf... |  | OnExecute→`ActionFileHcfExecute` (0x004FFE1C) |
| MainActionList/ActionSalvaMappa | TAction | Salva solo mappa in configurazione... |  | OnExecute→`ActionSalvaMappaExecute` (0x004FFE20) |
| MainActionList/ActionAzzeraId | TAction | ActionAzzeraId | Ctrl+A | OnExecute→`ActionAzzeraIdExecute` (0x005039A0) |

## Controles com evento/ação (19)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| FormMain | TFormMain | Initializing... |  | OnClose→`FormClose` (0x004F35C4); OnCloseQuery→`FormCloseQuery` (0x004FBDE8); OnPaint→`FormPaint` (0x004F3C74); OnResize→`FormResize` (0x005037F8); OnShow→`FormShow` (0x004F3E14) |
| StatusBarMain | TStatusBar |  |  | OnDrawPanel→`StatusBarMainDrawPanel` (0x004FF7D4) |
| BitBtnConfig | TBitBtn |  |  | OnClick→`ActionConfExecute` (0x004F95F4) |
| BitBtnVisualizzazione | TBitBtn |  |  | OnClick→`ActionVisualizzaExecute` (0x00501568) |
| BitBtnDiagnosi | TBitBtn |  |  | OnClick→`ActionDiagnosiExecute` (0x004FFE24) |
| BitBtnApriConfig | TBitBtn |  |  | OnClick→`ActionApriConfExecute` (0x00500314) |
| BitBtnRiprogramma | TBitBtn |  |  | OnClick→`ActionProgrammaExecute` (0x004FDBD4) |
| BitBtnEsci | TBitBtn |  |  | OnClick→`ActionEsciExecute` (0x004F8B74) |
| BitBtnTara | TBitBtn |  |  | OnClick→`ActionTaraExecute` (0x00500DDC) |
| BitBtnCorrTara | TBitBtn |  |  | OnClick→`ActionCorrTaraExecute` (0x00500DE4) |
| BitBtnAggiorna | TBitBtn |  |  | OnClick→`ActionAggiornaExecute` (0x005035DC) |
| BitBtnSalvaConfig | TBitBtn |  |  | OnClick→`ActionSalvaConfExecute` (0x005000CC) |
| Panel1/ImageLandiConnect | TImage |  |  | OnClick→`ImageLandiConnectClick` (0x005040EC) |
| Panel1/ImageBee | TImage |  |  | OnClick→`ImageBeeClick` (0x004F917C) |
| Panel1/ImageServiceOff | TImage |  |  | OnClick→`ImageServiceClick` (0x00503FB0) |
| Panel1/ImageServiceOn | TImage |  |  | OnClick→`ImageServiceClick` (0x00503FB0) |
| Panel1/ImageServiceOnAlrt | TImage |  |  | OnClick→`ImageServiceClick` (0x00503FB0) |
| Panel1/ImageServiceOnPswd | TImage |  |  | OnClick→`ImageServiceClick` (0x00503FB0) |
| Panel1/ImageServiceOnPswdAlrt | TImage |  |  | OnClick→`ImageServiceClick` (0x00503FB0) |

## Caixas de diálogo (componentes) (3)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) | Filtro |
|---|---|---|---|---|---|
| SaveDialog | TSaveDialog |  |  |  |  |
| OpenDialog | TOpenDialog |  |  |  |  |
| PrintDialog | TPrintDialog |  |  |  |  |

## Temporizadores (2)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) | Intervalo ms |
|---|---|---|---|---|---|
| TimerExpired | TTimer |  |  | OnTimer→`TimerExpiredTimer` (0x004F3774) |  |
| TimerUpdate | TTimer |  |  | OnTimer→`TimerUpdateTimer` (0x004F37F4) | 10000 |

## Handlers (38): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `ActionAggiornaExecute` | 0x005035DC | 537 | INFERIDO | chama APIs do Windows CreateProcessW, FormatMessageW, GetLastError |  | CreateProcessW, FormatMessageW, GetLastError | MESSCONTACT, MESSDAYS, MESSEXPIRED, MESSWILLEXPIRE, IsDebuggerPresent |
| `ActionApriConfExecute` | 0x00500314 | 712 | INFERIDO | chama APIs do Windows PostMessageW; chama TAebProtocol::IsConnect |  | PostMessageW | BUTTONANNULLA, BUTTONOK, COMBOCARBURANTE1, COMBOCARBURANTE2, IsDebuggerPresent, DeleteMenu, GetSystemMenu, GetSystemMetrics |
| `ActionApriExecute` | 0x004FE618 | 1082 | INFERIDO | chama APIs do Windows Sleep, PostMessageW; chama TAebProtocol::IsConnect, TFormAttesa.Create; toca OpenDialog, StatusBarMain |  | Sleep, PostMessageW | FINESTRACONFIG, LABELMODELLO34568CILINDRI, LABELMODELLO34CILINDRI, DeleteFileW, FindResourceW, GetTempFileNameW, GetTempPathW |
| `ActionArchivioExecute` | 0x004FF6C4 | 239 | INFERIDO | sem fatos diretos extraíveis (239 bytes; chama 9 funções sem nome) |  |  | BUTTONANNULLA, BUTTONOK, COMBOCARBURANTE1, COMBOCARBURANTE2, IsDebuggerPresent, DeleteMenu, GetSystemMenu, GetSystemMetrics |
| `ActionAzzeraIdExecute` | 0x005039A0 | 1036 | INFERIDO | chama TAebProtocol::IsConnect; toca ImageLandiConnect, LabelEcuType |  |  | DeleteFileW, FindResourceW, GetTempFileNameW, GetTempPathW |
| `ActionBloccoNoteExecute` | 0x004FFE18 | 1 | OBS-COD | Handler vazio: o corpo é só RET (nada acontece neste evento). |  |  |  |
| `ActionConfExecute` | 0x004F95F4 | 8 | INFERIDO | chama TFormMain.OpenConfigWindow |  |  |  |
| `ActionConnettiExecute` | 0x004FB840 | 24 | INFERIDO | sem fatos diretos extraíveis (24 bytes; chama 2 funções sem nome) |  |  |  |
| `ActionCorrTaraExecute` | 0x00500DE4 | 1080 | INFERIDO | usa textos FINESTRACORRTARATURA; chama TAebProtocol::IsConnect, TFormCorrCalibra.Create, TTranslatorExt::GetString | FINESTRACORRTARATURA |  | MESSCONTACT, MESSDAYS, MESSEXPIRED, MESSWILLEXPIRE, IsDebuggerPresent |
| `ActionDiagnosiExecute` | 0x004FFE24 | 677 | INFERIDO | usa textos FINESTRADIAGNOSI; chama TFormDiagnosi.Create, TTranslatorExt::GetString | FINESTRADIAGNOSI |  | MESSCONTACT, MESSDAYS, MESSEXPIRED, MESSWILLEXPIRE, IsDebuggerPresent |
| `ActionDisconnettiExecute` | 0x004FB858 | 485 | INFERIDO | chama TAebProtocol::Disconnect, TAebProtocol::OffLine; toca ImageLandiConnect |  |  |  |
| `ActionEsciExecute` | 0x004F8B74 | 6 | INFERIDO | sem fatos diretos extraíveis (6 bytes; chama 1 funções sem nome) |  |  |  |
| `ActionFileHcfExecute` | 0x004FFE1C | 1 | OBS-COD | Handler vazio: o corpo é só RET (nada acontece neste evento). |  |  |  |
| `ActionProgrammaExecute` | 0x004FDBD4 | 24 | INFERIDO | sem fatos diretos extraíveis (24 bytes; chama 2 funções sem nome) |  |  | BUTTONANNULLA, BUTTONCAMBIA, BUTTONRIPROGR, LABELAGGCENTRALINA, FindClose, FindFirstFileW, FindNextFileW, IsDebuggerPresent |
| `ActionResetCentralinaExecute` | 0x00501244 | 1 | OBS-COD | Handler vazio: o corpo é só RET (nada acontece neste evento). |  |  |  |
| `ActionResetDaPcExecute` | 0x00501248 | 798 | INFERIDO | chama APIs do Windows Sleep; chama TAebNumber::GetEcuData, TAebNumber::SetEcuData, TAebProtocol::Disconnect, TAebProtocol::IsConnect, TAebProtocol::OffLine |  | Sleep | LABELSCRITTURACONFIG, DeleteFileW, FindResourceW, GetTempFileNameW, GetTempPathW |
| `ActionSalvaConfExecute` | 0x005000CC | 582 | INFERIDO | chama APIs do Windows PostMessageW; chama TAebProtocol::IsConnect |  | PostMessageW | BUTTONANNULLA, BUTTONOK, COMBOCARBURANTE1, COMBOCARBURANTE2, IsDebuggerPresent, DeleteMenu, GetSystemMenu, GetSystemMetrics |
| `ActionSalvaExecute` | 0x004FEA54 | 3181 | INFERIDO | usa textos FINESTRACONFIG, LABELMODELLO34568CILINDRI, LABELMODELLO34CILINDRI; chama APIs do Windows PostMessageW; chama TFormAttesa.Create, TTranslatorExt::GetString; toca SaveDialog, StatusBarMain | FINESTRACONFIG, LABELMODELLO34568CILINDRI, LABELMODELLO34CILINDRI | PostMessageW | DeleteFileW, FindResourceW, GetTempFileNameW, GetTempPathW |
| `ActionSalvaMappaExecute` | 0x004FFE20 | 1 | OBS-COD | Handler vazio: o corpo é só RET (nada acontece neste evento). |  |  |  |
| `ActionTaraExecute` | 0x00500DDC | 8 | INFERIDO | chama TFormMain.OpenFormAutotaratura |  |  |  |
| `ActionUpdateExecute` | 0x005035D4 | 6 | INFERIDO | sem fatos diretos extraíveis (6 bytes; chama 1 funções sem nome) |  |  | LABELAGGCENTRALINA, LABELAGGCONFIG, LABELCONNCENTRALINA, LABELPREPAGGCONFIG, FindClose, FindFirstFileW, FindNextFileW, SetLastError |
| `ActionVisualizzaExecute` | 0x00501568 | 677 | INFERIDO | usa textos FINESTRAVISUALIZZA; chama TFormVisualizza.Create, TTranslatorExt::GetString | FINESTRAVISUALIZZA |  | MESSCONTACT, MESSDAYS, MESSEXPIRED, MESSWILLEXPIRE, IsDebuggerPresent |
| `AfterCalibraOpenedFromAutocal` (sem evento no DFM) | 0x00500DA8 | 51 | INFERIDO | chama TFormMain.OpenConfigWindow |  |  |  |
| `FormClose` | 0x004F35C4 | 429 | INFERIDO | chama TAebProtocol::IsConnect, TAebProtocol::ReadPort, TAebProtocol::SetWindowHandle, TAebRegistryData::SetIntData; toca ActionDisconnetti |  |  |  |
| `FormCloseQuery` | 0x004FBDE8 | 52 | INFERIDO | sem fatos diretos extraíveis (52 bytes; chama 2 funções sem nome) |  |  |  |
| `FormPaint` | 0x004F3C74 | 395 | INFERIDO | toca StatusBarMain |  |  |  |
| `FormResize` | 0x005037F8 | 414 | INFERIDO | toca StatusBarMain |  |  |  |
| `FormShow` | 0x004F3E14 | 2614 | INFERIDO | chama APIs do Windows Sleep; toca OpenDialog, SaveDialog |  | Sleep |  |
| `ImageBeeClick` | 0x004F917C | 19 | INFERIDO | chama TAebProtocol::ShowAebBeeSettings |  |  |  |
| `ImageLandiConnectClick` | 0x005040EC | 101 | INFERIDO | chama TFormLandiConnect.Create |  |  |  |
| `ImageServiceClick` | 0x00503FB0 | 279 | INFERIDO | chama TAebProtocol::IsConnect, TFormService.Create; toca ActionApri |  |  |  |
| `MenuAboutClick` | 0x004F7808 | 147 | INFERIDO | toca LabelConfig |  |  | BUTTONESCI, BUTTON_DELETE_LICENSE, BUTTON_LOAD_LICENSE, BUTTON_REQUEST_LICENSE |
| `OpenConfigWindow` (sem evento no DFM) | 0x004F9190 | 1121 | INFERIDO | usa textos FINESTRACONFIG; chama TAebProtocol::IsConnect, TTranslatorExt::GetString | FINESTRACONFIG |  | MESSCONTACT, MESSDAYS, MESSEXPIRED, MESSWILLEXPIRE, IsDebuggerPresent |
| `OpenFormAutotaratura` (sem evento no DFM) | 0x0050093C | 1093 | INFERIDO | usa textos FINESTRATARATURA; chama TAebProtocol::IsConnect, TFormCalibra.Create, TTranslatorExt::GetString | FINESTRATARATURA |  | MESSCONTACT, MESSDAYS, MESSEXPIRED, MESSWILLEXPIRE, IsDebuggerPresent |
| `Plot1Click` | 0x0050417C | 160 | INFERIDO | sem fatos diretos extraíveis (160 bytes; chama 6 funções sem nome) |  |  |  |
| `StatusBarMainDrawPanel` | 0x004FF7D4 | 1098 | INFERIDO | chama TAebProtocol::IsConnect |  |  |  |
| `TimerExpiredTimer` | 0x004F3774 | 127 | INFERIDO | toca TimerExpired |  |  |  |
| `TimerUpdateTimer` | 0x004F37F4 | 1113 | INFERIDO | chama APIs do Windows GetTickCount, Sleep; toca TimerUpdate |  | GetTickCount, Sleep | GetUserNameExW, InternetAttemptConnect, InternetConnectW, InternetGetConnectedState |
