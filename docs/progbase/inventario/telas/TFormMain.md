# TFormMain (recurso `TFORMMAIN`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormMain`; unidade: `@:Main`; ancestral: `TFormMainBase`; tamanho de instância: 0x63C; componentes no DFM: 84.
- Título: "Initializing..."
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

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

| Handler | Endereço | Bytes | Evidência | O que faz | Textos (chaves i18n) | APIs | Chama |
|---|---|---:|---|---|---|---|---|
| `ActionAggiornaExecute` | 0x005035DC | 537 | INFERIDO | Handler `ActionAggiornaExecute`; mostra/usa textos MESSCONTACT, MESSDAYS, MESSEXPIRED, MESSWILLEXPIRE; chama APIs do Windows: KERNEL32.DLL!CreateProcessW, KERNEL32.DLL!FormatMessageW, KERNEL32.DLL!GetLastError, KERNEL32.DLL!IsDebuggerPresent; chama: thunk -> KERNEL32.DLL!CreateProcessW, thunk -> KERNEL32.DLL!FormatMessageW, thunk -> KERNEL32.DLL!GetLastError | MESSCONTACT, MESSDAYS, MESSEXPIRED, MESSWILLEXPIRE | KERNEL32.DLL!CreateProcessW, KERNEL32.DLL!FormatMessageW, KERNEL32.DLL!GetLastError, KERNEL32.DLL!IsDebuggerPresent | thunk -> KERNEL32.DLL!CreateProcessW, thunk -> KERNEL32.DLL!FormatMessageW, thunk -> KERNEL32.DLL!GetLastError |
| `ActionApriConfExecute` | 0x00500314 | 712 | INFERIDO | Handler `ActionApriConfExecute`; mostra/usa textos BUTTONANNULLA, BUTTONOK, COMBOCARBURANTE1, COMBOCARBURANTE2, FINESTRACARICACONF, FINESTRACARICADATI; chama APIs do Windows: USER32.DLL!PostMessageW, KERNEL32.DLL!IsDebuggerPresent, USER32.DLL!DeleteMenu, USER32.DLL!GetSystemMenu, USER32.DLL!GetSystemMetrics; chama: TAebProtocol::IsConnect, thunk -> USER32.DLL!PostMessageW | BUTTONANNULLA, BUTTONOK, COMBOCARBURANTE1, COMBOCARBURANTE2, FINESTRACARICACONF, FINESTRACARICADATI | USER32.DLL!PostMessageW, KERNEL32.DLL!IsDebuggerPresent, USER32.DLL!DeleteMenu, USER32.DLL!GetSystemMenu, USER32.DLL!GetSystemMetrics | TAebProtocol::IsConnect, thunk -> USER32.DLL!PostMessageW |
| `ActionApriExecute` | 0x004FE618 | 1082 | INFERIDO | Handler `ActionApriExecute`; mostra/usa textos FINESTRACONFIG, LABELMODELLO34568CILINDRI, LABELMODELLO34CILINDRI; chama APIs do Windows: KERNEL32.DLL!Sleep, USER32.DLL!PostMessageW, KERNEL32.DLL!DeleteFileW, KERNEL32.DLL!FindResourceW, KERNEL32.DLL!GetTempFileNameW, KERNEL32.DLL!GetTempPathW; chama: TAebProtocol::IsConnect, TFormAttesa.vmt[0x38], thunk -> KERNEL32.DLL!Sleep, thunk -> USER32.DLL!PostMessageW; toca campos/controles: OpenDialog, StatusBarMain | FINESTRACONFIG, LABELMODELLO34568CILINDRI, LABELMODELLO34CILINDRI | KERNEL32.DLL!Sleep, USER32.DLL!PostMessageW, KERNEL32.DLL!DeleteFileW, KERNEL32.DLL!FindResourceW, KERNEL32.DLL!GetTempFileNameW | TAebProtocol::IsConnect, TFormAttesa.vmt[0x38], thunk -> KERNEL32.DLL!Sleep, thunk -> USER32.DLL!PostMessageW |
| `ActionArchivioExecute` | 0x004FF6C4 | 239 | INFERIDO | Handler `ActionArchivioExecute`; mostra/usa textos BUTTONANNULLA, BUTTONOK, COMBOCARBURANTE1, COMBOCARBURANTE2, FINESTRACARICACONF, FINESTRACARICADATI; chama APIs do Windows: KERNEL32.DLL!IsDebuggerPresent, USER32.DLL!DeleteMenu, USER32.DLL!GetSystemMenu, USER32.DLL!GetSystemMetrics | BUTTONANNULLA, BUTTONOK, COMBOCARBURANTE1, COMBOCARBURANTE2, FINESTRACARICACONF, FINESTRACARICADATI | KERNEL32.DLL!IsDebuggerPresent, USER32.DLL!DeleteMenu, USER32.DLL!GetSystemMenu, USER32.DLL!GetSystemMetrics |  |
| `ActionAzzeraIdExecute` | 0x005039A0 | 1036 | INFERIDO | Handler `ActionAzzeraIdExecute`; chama APIs do Windows: KERNEL32.DLL!DeleteFileW, KERNEL32.DLL!FindResourceW, KERNEL32.DLL!GetTempFileNameW, KERNEL32.DLL!GetTempPathW, KERNEL32.DLL!GetTickCount, KERNEL32.DLL!LoadResource; chama: TAebProtocol::IsConnect; toca campos/controles: ImageLandiConnect, LabelEcuType |  | KERNEL32.DLL!DeleteFileW, KERNEL32.DLL!FindResourceW, KERNEL32.DLL!GetTempFileNameW, KERNEL32.DLL!GetTempPathW, KERNEL32.DLL!GetTickCount | TAebProtocol::IsConnect |
| `ActionBloccoNoteExecute` | 0x004FFE18 | 1 | INFERIDO | Handler `ActionBloccoNoteExecute`; sem fatos extraíveis automaticamente (1 bytes) |  |  |  |
| `ActionConfExecute` | 0x004F95F4 | 8 | INFERIDO | Handler `ActionConfExecute`; chama: TFormMain.OpenConfigWindow |  |  | TFormMain.OpenConfigWindow |
| `ActionConnettiExecute` | 0x004FB840 | 24 | INFERIDO | Handler `ActionConnettiExecute`; sem fatos extraíveis automaticamente (24 bytes) |  |  |  |
| `ActionCorrTaraExecute` | 0x00500DE4 | 1080 | INFERIDO | Handler `ActionCorrTaraExecute`; mostra/usa textos FINESTRACORRTARATURA, MESSCONTACT, MESSDAYS, MESSEXPIRED, MESSWILLEXPIRE; chama APIs do Windows: KERNEL32.DLL!IsDebuggerPresent; chama: TAebProtocol::IsConnect, TFormCorrCalibra.vmt[0x38], TTranslatorExt::GetString | FINESTRACORRTARATURA, MESSCONTACT, MESSDAYS, MESSEXPIRED, MESSWILLEXPIRE | KERNEL32.DLL!IsDebuggerPresent | TAebProtocol::IsConnect, TFormCorrCalibra.vmt[0x38], TTranslatorExt::GetString |
| `ActionDiagnosiExecute` | 0x004FFE24 | 677 | INFERIDO | Handler `ActionDiagnosiExecute`; mostra/usa textos FINESTRADIAGNOSI, MESSCONTACT, MESSDAYS, MESSEXPIRED, MESSWILLEXPIRE; chama APIs do Windows: KERNEL32.DLL!IsDebuggerPresent; chama: TFormDiagnosi.vmt[0x38], TTranslatorExt::GetString | FINESTRADIAGNOSI, MESSCONTACT, MESSDAYS, MESSEXPIRED, MESSWILLEXPIRE | KERNEL32.DLL!IsDebuggerPresent | TFormDiagnosi.vmt[0x38], TTranslatorExt::GetString |
| `ActionDisconnettiExecute` | 0x004FB858 | 485 | INFERIDO | Handler `ActionDisconnettiExecute`; chama: TAebProtocol::Disconnect, TAebProtocol::OffLine; toca campos/controles: ImageLandiConnect |  |  | TAebProtocol::Disconnect, TAebProtocol::OffLine |
| `ActionEsciExecute` | 0x004F8B74 | 6 | INFERIDO | Handler `ActionEsciExecute`; sem fatos extraíveis automaticamente (6 bytes) |  |  |  |
| `ActionFileHcfExecute` | 0x004FFE1C | 1 | INFERIDO | Handler `ActionFileHcfExecute`; sem fatos extraíveis automaticamente (1 bytes) |  |  |  |
| `ActionProgrammaExecute` | 0x004FDBD4 | 24 | INFERIDO | Handler `ActionProgrammaExecute`; mostra/usa textos BUTTONANNULLA, BUTTONCAMBIA, BUTTONRIPROGR, LABELAGGCENTRALINA, LABELAGGCONFIG, LABELCONNCENTRALINA; chama APIs do Windows: KERNEL32.DLL!FindClose, KERNEL32.DLL!FindFirstFileW, KERNEL32.DLL!FindNextFileW, KERNEL32.DLL!IsDebuggerPresent, KERNEL32.DLL!SetLastError, KERNEL32.DLL!Sleep | BUTTONANNULLA, BUTTONCAMBIA, BUTTONRIPROGR, LABELAGGCENTRALINA, LABELAGGCONFIG, LABELCONNCENTRALINA | KERNEL32.DLL!FindClose, KERNEL32.DLL!FindFirstFileW, KERNEL32.DLL!FindNextFileW, KERNEL32.DLL!IsDebuggerPresent, KERNEL32.DLL!SetLastError |  |
| `ActionResetCentralinaExecute` | 0x00501244 | 1 | INFERIDO | Handler `ActionResetCentralinaExecute`; sem fatos extraíveis automaticamente (1 bytes) |  |  |  |
| `ActionResetDaPcExecute` | 0x00501248 | 798 | INFERIDO | Handler `ActionResetDaPcExecute`; mostra/usa textos LABELSCRITTURACONFIG; chama APIs do Windows: KERNEL32.DLL!Sleep, KERNEL32.DLL!DeleteFileW, KERNEL32.DLL!FindResourceW, KERNEL32.DLL!GetTempFileNameW, KERNEL32.DLL!GetTempPathW, KERNEL32.DLL!GetTickCount; chama: TAebNumber::GetEcuData, TAebNumber::SetEcuData, TAebProtocol::Disconnect, TAebProtocol::IsConnect, TAebProtocol::OffLine, thunk -> KERNEL32.DLL!Sleep | LABELSCRITTURACONFIG | KERNEL32.DLL!Sleep, KERNEL32.DLL!DeleteFileW, KERNEL32.DLL!FindResourceW, KERNEL32.DLL!GetTempFileNameW, KERNEL32.DLL!GetTempPathW | TAebNumber::GetEcuData, TAebNumber::SetEcuData, TAebProtocol::Disconnect, TAebProtocol::IsConnect, TAebProtocol::OffLine |
| `ActionSalvaConfExecute` | 0x005000CC | 582 | INFERIDO | Handler `ActionSalvaConfExecute`; mostra/usa textos BUTTONANNULLA, BUTTONOK, COMBOCARBURANTE1, COMBOCARBURANTE2, FINESTRACARICACONF, FINESTRACARICADATI; chama APIs do Windows: USER32.DLL!PostMessageW, KERNEL32.DLL!IsDebuggerPresent, USER32.DLL!DeleteMenu, USER32.DLL!GetSystemMenu, USER32.DLL!GetSystemMetrics; chama: TAebProtocol::IsConnect, thunk -> USER32.DLL!PostMessageW | BUTTONANNULLA, BUTTONOK, COMBOCARBURANTE1, COMBOCARBURANTE2, FINESTRACARICACONF, FINESTRACARICADATI | USER32.DLL!PostMessageW, KERNEL32.DLL!IsDebuggerPresent, USER32.DLL!DeleteMenu, USER32.DLL!GetSystemMenu, USER32.DLL!GetSystemMetrics | TAebProtocol::IsConnect, thunk -> USER32.DLL!PostMessageW |
| `ActionSalvaExecute` | 0x004FEA54 | 3181 | INFERIDO | Handler `ActionSalvaExecute`; mostra/usa textos FINESTRACONFIG, LABELMODELLO34568CILINDRI, LABELMODELLO34CILINDRI; chama APIs do Windows: USER32.DLL!PostMessageW, KERNEL32.DLL!DeleteFileW, KERNEL32.DLL!FindResourceW, KERNEL32.DLL!GetTempFileNameW, KERNEL32.DLL!GetTempPathW, KERNEL32.DLL!GetTickCount; chama: TFormAttesa.vmt[0x38], TTranslatorExt::GetString, thunk -> USER32.DLL!PostMessageW; toca campos/controles: SaveDialog, StatusBarMain | FINESTRACONFIG, LABELMODELLO34568CILINDRI, LABELMODELLO34CILINDRI | USER32.DLL!PostMessageW, KERNEL32.DLL!DeleteFileW, KERNEL32.DLL!FindResourceW, KERNEL32.DLL!GetTempFileNameW, KERNEL32.DLL!GetTempPathW | TFormAttesa.vmt[0x38], TTranslatorExt::GetString, thunk -> USER32.DLL!PostMessageW |
| `ActionSalvaMappaExecute` | 0x004FFE20 | 1 | INFERIDO | Handler `ActionSalvaMappaExecute`; sem fatos extraíveis automaticamente (1 bytes) |  |  |  |
| `ActionTaraExecute` | 0x00500DDC | 8 | INFERIDO | Handler `ActionTaraExecute`; chama: TFormMain.OpenFormAutotaratura |  |  | TFormMain.OpenFormAutotaratura |
| `ActionUpdateExecute` | 0x005035D4 | 6 | INFERIDO | Handler `ActionUpdateExecute`; mostra/usa textos LABELAGGCENTRALINA, LABELAGGCONFIG, LABELCONNCENTRALINA, LABELPREPAGGCONFIG, LABELPREPAGGFIRMWARE, LABELRICERCAAGGCONFIG; chama APIs do Windows: KERNEL32.DLL!FindClose, KERNEL32.DLL!FindFirstFileW, KERNEL32.DLL!FindNextFileW, KERNEL32.DLL!SetLastError, KERNEL32.DLL!Sleep, USER32.DLL!SendMessageW | LABELAGGCENTRALINA, LABELAGGCONFIG, LABELCONNCENTRALINA, LABELPREPAGGCONFIG, LABELPREPAGGFIRMWARE, LABELRICERCAAGGCONFIG | KERNEL32.DLL!FindClose, KERNEL32.DLL!FindFirstFileW, KERNEL32.DLL!FindNextFileW, KERNEL32.DLL!SetLastError, KERNEL32.DLL!Sleep |  |
| `ActionVisualizzaExecute` | 0x00501568 | 677 | INFERIDO | Handler `ActionVisualizzaExecute`; mostra/usa textos FINESTRAVISUALIZZA, MESSCONTACT, MESSDAYS, MESSEXPIRED, MESSWILLEXPIRE; chama APIs do Windows: KERNEL32.DLL!IsDebuggerPresent; chama: TFormVisualizza.vmt[0x38], TTranslatorExt::GetString | FINESTRAVISUALIZZA, MESSCONTACT, MESSDAYS, MESSEXPIRED, MESSWILLEXPIRE | KERNEL32.DLL!IsDebuggerPresent | TFormVisualizza.vmt[0x38], TTranslatorExt::GetString |
| `AfterCalibraOpenedFromAutocal` (sem evento no DFM) | 0x00500DA8 | 51 | INFERIDO | Handler `AfterCalibraOpenedFromAutocal`; chama: TFormMain.OpenConfigWindow |  |  | TFormMain.OpenConfigWindow |
| `FormClose` | 0x004F35C4 | 429 | INFERIDO | Handler `FormClose`; chama: TAebProtocol::IsConnect, TAebProtocol::ReadPort, TAebProtocol::SetWindowHandle, TAebRegistryData::SetIntData; toca campos/controles: ActionDisconnetti |  |  | TAebProtocol::IsConnect, TAebProtocol::ReadPort, TAebProtocol::SetWindowHandle, TAebRegistryData::SetIntData |
| `FormCloseQuery` | 0x004FBDE8 | 52 | INFERIDO | Handler `FormCloseQuery`; sem fatos extraíveis automaticamente (52 bytes) |  |  |  |
| `FormPaint` | 0x004F3C74 | 395 | INFERIDO | Handler `FormPaint`; toca campos/controles: StatusBarMain |  |  |  |
| `FormResize` | 0x005037F8 | 414 | INFERIDO | Handler `FormResize`; toca campos/controles: StatusBarMain |  |  |  |
| `FormShow` | 0x004F3E14 | 2614 | INFERIDO | Handler `FormShow`; chama APIs do Windows: KERNEL32.DLL!Sleep; chama: thunk -> KERNEL32.DLL!Sleep; toca campos/controles: OpenDialog, SaveDialog |  | KERNEL32.DLL!Sleep | thunk -> KERNEL32.DLL!Sleep |
| `ImageBeeClick` | 0x004F917C | 19 | INFERIDO | Handler `ImageBeeClick`; chama: TAebProtocol::ShowAebBeeSettings |  |  | TAebProtocol::ShowAebBeeSettings |
| `ImageLandiConnectClick` | 0x005040EC | 101 | INFERIDO | Handler `ImageLandiConnectClick`; chama: TFormLandiConnect.vmt[0x38] |  |  | TFormLandiConnect.vmt[0x38] |
| `ImageServiceClick` | 0x00503FB0 | 279 | INFERIDO | Handler `ImageServiceClick`; chama: TAebProtocol::IsConnect, TFormService.vmt[0x38]; toca campos/controles: ActionApri |  |  | TAebProtocol::IsConnect, TFormService.vmt[0x38] |
| `MenuAboutClick` | 0x004F7808 | 147 | INFERIDO | Handler `MenuAboutClick`; mostra/usa textos BUTTONESCI, BUTTON_DELETE_LICENSE, BUTTON_LOAD_LICENSE, BUTTON_REQUEST_LICENSE, BUTTON_SEND_BY_MAIL, LABEL_PC_REGISTERED_LICENSE; toca campos/controles: LabelConfig | BUTTONESCI, BUTTON_DELETE_LICENSE, BUTTON_LOAD_LICENSE, BUTTON_REQUEST_LICENSE, BUTTON_SEND_BY_MAIL, LABEL_PC_REGISTERED_LICENSE |  |  |
| `OpenConfigWindow` (sem evento no DFM) | 0x004F9190 | 1121 | INFERIDO | Handler `OpenConfigWindow`; mostra/usa textos FINESTRACONFIG, MESSCONTACT, MESSDAYS, MESSEXPIRED, MESSWILLEXPIRE; chama APIs do Windows: KERNEL32.DLL!IsDebuggerPresent; chama: TAebProtocol::IsConnect, TTranslatorExt::GetString | FINESTRACONFIG, MESSCONTACT, MESSDAYS, MESSEXPIRED, MESSWILLEXPIRE | KERNEL32.DLL!IsDebuggerPresent | TAebProtocol::IsConnect, TTranslatorExt::GetString |
| `OpenFormAutotaratura` (sem evento no DFM) | 0x0050093C | 1093 | INFERIDO | Handler `OpenFormAutotaratura`; mostra/usa textos FINESTRATARATURA, MESSCONTACT, MESSDAYS, MESSEXPIRED, MESSWILLEXPIRE; chama APIs do Windows: KERNEL32.DLL!IsDebuggerPresent; chama: TAebProtocol::IsConnect, TFormCalibra.vmt[0x38], TTranslatorExt::GetString | FINESTRATARATURA, MESSCONTACT, MESSDAYS, MESSEXPIRED, MESSWILLEXPIRE | KERNEL32.DLL!IsDebuggerPresent | TAebProtocol::IsConnect, TFormCalibra.vmt[0x38], TTranslatorExt::GetString |
| `Plot1Click` | 0x0050417C | 160 | INFERIDO | Handler `Plot1Click`; sem fatos extraíveis automaticamente (160 bytes) |  |  |  |
| `StatusBarMainDrawPanel` | 0x004FF7D4 | 1098 | INFERIDO | Handler `StatusBarMainDrawPanel`; chama: TAebProtocol::IsConnect |  |  | TAebProtocol::IsConnect |
| `TimerExpiredTimer` | 0x004F3774 | 127 | INFERIDO | Handler `TimerExpiredTimer`; toca campos/controles: TimerExpired |  |  |  |
| `TimerUpdateTimer` | 0x004F37F4 | 1113 | INFERIDO | Handler `TimerUpdateTimer`; chama APIs do Windows: KERNEL32.DLL!GetTickCount, KERNEL32.DLL!Sleep, SECUR32.DLL!GetUserNameExW, WININET.DLL!InternetAttemptConnect, WININET.DLL!InternetConnectW, WININET.DLL!InternetGetConnectedState; chama: thunk -> KERNEL32.DLL!GetTickCount, thunk -> KERNEL32.DLL!Sleep; toca campos/controles: TimerUpdate |  | KERNEL32.DLL!GetTickCount, KERNEL32.DLL!Sleep, SECUR32.DLL!GetUserNameExW, WININET.DLL!InternetAttemptConnect, WININET.DLL!InternetConnectW | thunk -> KERNEL32.DLL!GetTickCount, thunk -> KERNEL32.DLL!Sleep |
