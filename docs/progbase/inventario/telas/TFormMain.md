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

### Cadeia de subfunções sem nome alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `ActionAggiornaExecute` (0x005035DC):
  - sub_40487C (45 B)
  - sub_404C40 (81 B)
  - sub_405754 (48 B)
  - sub_489034 (110 B)
  - sub_489320 (60 B)
  - sub_501838 (364 B): APIs IsDebuggerPresent
  -   sub_405784 (59 B)
  -   sub_414B8C (15681 B): textos MESSCONTACT,MESSDAYS,MESSEXPIRED,MESSWILLEXPIRE; chama TFormAggKey.Create,TTranslatorExt::GetString; toca LabelConfig
  -   sub_48944C (154 B)
  -   sub_4F3C50 (34 B)
  -     sub_4047FC (125 B)
  -     sub_404C94 (59 B)
  - … +4 (ver telas.json)
- `ActionApriConfExecute` (0x00500314):
  - sub_404C40 (81 B)
  - sub_405754 (48 B)
  - sub_4057C0 (139 B)
  - sub_425514 (134 B): toca ImageList1,Timer,btnExportImage,btnLoad
  - sub_489034 (110 B)
  - sub_489320 (60 B)
  - sub_48935C (60 B)
  - sub_4D83CC (5497 B): textos BUTTONANNULLA,BUTTONOK,COMBOCARBURANTE1,COMBOCARBURANTE2; APIs DeleteMenu,GetSystemMenu,GetSystemMetrics; chama TAboutBox.Create,TAebProtocol::IsConnect,TTranslatorExt::GetString
  - sub_501838 (364 B): APIs IsDebuggerPresent
  -   sub_50FCF8 (23 B)
  -   sub_40487C (45 B)
  -   sub_41E8D0 (96 B)
  - … +12 (ver telas.json)
- `ActionApriExecute` (0x004FE618):
  - sub_40487C (45 B)
  - sub_404C40 (81 B)
  - sub_404C94 (59 B)
  - sub_42B964 (428 B)
  - sub_439E94 (288664 B): APIs Sleep,SendMessageW; chama TAebMatrix::GetEcuData,TAebMatrix::SetColumnDimension,TAebMatrix::SetEcuData,TAebNumber::GetEcuData; toca EditAnticipoEV2,EditPressRid
  - sub_48A228 (2561 B)
  - sub_4F6CC8 (2878 B): textos FINESTRACONFIG,LABELMODELLO34568CILINDRI,LABELMODELLO34CILINDRI; chama TAebProtocol::IsConnect,TTranslatorExt::GetString; toca OpenDialog,SaveDialog
  - sub_4FBA40 (933 B): APIs Sleep; chama TAebProtocol::SendCommand,TAebVector::GetEcuData; toca ButtonAnnulla,ButtonOk,EditName
  - sub_4FFC20 (503 B): toca ActionSalva
  -   sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  - … +48 (ver telas.json)
- `ActionArchivioExecute` (0x004FF6C4):
  - sub_404C40 (81 B)
  - sub_405754 (48 B)
  - sub_489034 (110 B)
  - sub_489320 (60 B)
  - sub_4D83CC (5497 B): textos BUTTONANNULLA,BUTTONOK,COMBOCARBURANTE1,COMBOCARBURANTE2; APIs DeleteMenu,GetSystemMenu,GetSystemMetrics; chama TAboutBox.Create,TAebProtocol::IsConnect,TTranslatorExt::GetString
  - sub_501838 (364 B): APIs IsDebuggerPresent
  -   sub_40487C (45 B)
  -   sub_41E8D0 (96 B)
  -   sub_42CFB8 (839 B)
  -   sub_405784 (59 B)
  -   sub_414B8C (15681 B): textos MESSCONTACT,MESSDAYS,MESSEXPIRED,MESSWILLEXPIRE; chama TFormAggKey.Create,TTranslatorExt::GetString; toca LabelConfig
  -   sub_48944C (154 B)
  - … +8 (ver telas.json)
- `ActionAzzeraIdExecute` (0x005039A0):
  - sub_404C40 (81 B)
  - sub_405754 (48 B)
  - sub_42B964 (428 B)
  - sub_434C78 (34 B)
  - sub_439E94 (288664 B): APIs Sleep,SendMessageW; chama TAebMatrix::GetEcuData,TAebMatrix::SetColumnDimension,TAebMatrix::SetEcuData,TAebNumber::GetEcuData; toca EditAnticipoEV2,EditPressRid
  - sub_489034 (110 B)
  - sub_489320 (60 B)
  - sub_5034CC (262 B): toca StatusBarMain
  -   sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  -   sub_40487C (45 B)
  -   sub_404C94 (59 B)
  -   sub_42A698 (235 B)
  - … +48 (ver telas.json)
- `ActionConnettiExecute` (0x004FB840):
  - sub_425514 (134 B): toca ImageList1,Timer,btnExportImage,btnLoad
  - sub_4F9C34 (11 B)
  -   sub_50FCF8 (23 B)
  -   sub_4F9C40 (7167 B): chama TAebNumber::GetEcuData,TAebNumber::SetEcuData,TAebProtocol::Connect,TAebProtocol::Disconnect; toca ActionApri,ActionApriConf,ActionConf,ActionCorrTara
  -     sub_40487C (45 B)
  -     sub_404C40 (81 B)
  -     sub_405754 (48 B)
  -     sub_4057C0 (139 B)
  -     sub_4082A0 (88 B)
  -     sub_42B8D4 (144 B): chama TAebNumber::GetEcuData,TAebProtocol::GetComFail,TAebProtocol::GetIdentification
  -     sub_42B964 (428 B)
  -     sub_42BB40 (41 B): chama TAebNumber::SetEcuData
  - … +24 (ver telas.json)
- `ActionCorrTaraExecute` (0x00500DE4):
  - sub_40487C (45 B)
  - sub_404C40 (81 B)
  - sub_405754 (48 B)
  - sub_4057C0 (139 B)
  - sub_431578 (56 B)
  - sub_489034 (110 B)
  - sub_489320 (60 B)
  - sub_48935C (60 B)
  - sub_501838 (364 B): APIs IsDebuggerPresent
  -   sub_405784 (59 B)
  -   sub_414B8C (15681 B): textos MESSCONTACT,MESSDAYS,MESSEXPIRED,MESSWILLEXPIRE; chama TFormAggKey.Create,TTranslatorExt::GetString; toca LabelConfig
  -   sub_48944C (154 B)
  - … +7 (ver telas.json)
- `ActionDiagnosiExecute` (0x004FFE24):
  - sub_40487C (45 B)
  - sub_404C40 (81 B)
  - sub_405754 (48 B)
  - sub_431578 (56 B)
  - sub_489034 (110 B)
  - sub_489320 (60 B)
  - sub_501838 (364 B): APIs IsDebuggerPresent
  -   sub_405784 (59 B)
  -   sub_414B8C (15681 B): textos MESSCONTACT,MESSDAYS,MESSEXPIRED,MESSWILLEXPIRE; chama TFormAggKey.Create,TTranslatorExt::GetString; toca LabelConfig
  -   sub_48944C (154 B)
  -   sub_4F3C50 (34 B)
  -     sub_4047FC (125 B)
  - … +5 (ver telas.json)
- `ActionDisconnettiExecute` (0x004FB858):
  - sub_404C40 (81 B)
  - sub_405754 (48 B)
  - sub_425514 (134 B): toca ImageList1,Timer,btnExportImage,btnLoad
  - sub_433280 (132 B): chama TAebVector::SetEcuData
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  - sub_489034 (110 B)
  - sub_489320 (60 B)
  - sub_503DAC (513 B): chama TAebProtocol::IsConnect; toca BitBtnRiprogramma,ImageServiceOff,ImageServiceOn,ImageServiceOnAlrt
  -   sub_50FCF8 (23 B)
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  - … +7 (ver telas.json)
- `ActionProgrammaExecute` (0x004FDBD4):
  - sub_425514 (134 B): toca ImageList1,Timer,btnExportImage,btnLoad
  - sub_4FD80C (966 B): chama TAebProtocol::IsConnect,TFormAttesa.Create
  -   sub_50FCF8 (23 B)
  -   sub_40487C (45 B)
  -   sub_404C40 (81 B)
  -   sub_405754 (48 B)
  -   sub_4057C0 (139 B)
  -   sub_433240 (61 B)
  -   sub_489034 (110 B)
  -   sub_489320 (60 B)
  -   sub_48935C (60 B)
  -   sub_48944C (154 B)
  - … +44 (ver telas.json)
- `ActionResetDaPcExecute` (0x00501248):
  - sub_40487C (45 B)
  - sub_42B244 (15 B)
  - sub_42B8D4 (144 B): chama TAebNumber::GetEcuData,TAebProtocol::GetComFail,TAebProtocol::GetIdentification
  - sub_42B964 (428 B)
  - sub_42CFB8 (839 B)
  - sub_42FFC8 (16 B)
  - sub_43362C (14 B)
  - sub_439E94 (288664 B): APIs Sleep,SendMessageW; chama TAebMatrix::GetEcuData,TAebMatrix::SetColumnDimension,TAebMatrix::SetEcuData,TAebNumber::GetEcuData; toca EditAnticipoEV2,EditPressRid
  - sub_4F9C40 (7167 B): chama TAebNumber::GetEcuData,TAebNumber::SetEcuData,TAebProtocol::Connect,TAebProtocol::Disconnect; toca ActionApri,ActionApriConf,ActionConf,ActionCorrTara
  -   sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  -   sub_404C40 (81 B)
  -   sub_404C94 (59 B)
  - … +57 (ver telas.json)
- `ActionSalvaConfExecute` (0x005000CC):
  - sub_404C40 (81 B)
  - sub_405754 (48 B)
  - sub_4057C0 (139 B)
  - sub_425514 (134 B): toca ImageList1,Timer,btnExportImage,btnLoad
  - sub_489034 (110 B)
  - sub_489320 (60 B)
  - sub_48935C (60 B)
  - sub_4D83CC (5497 B): textos BUTTONANNULLA,BUTTONOK,COMBOCARBURANTE1,COMBOCARBURANTE2; APIs DeleteMenu,GetSystemMenu,GetSystemMetrics; chama TAboutBox.Create,TAebProtocol::IsConnect,TTranslatorExt::GetString
  - sub_501838 (364 B): APIs IsDebuggerPresent
  -   sub_50FCF8 (23 B)
  -   sub_40487C (45 B)
  -   sub_41E8D0 (96 B)
  - … +12 (ver telas.json)
- `ActionSalvaExecute` (0x004FEA54):
  - sub_4047FC (125 B)
  - sub_40487C (45 B)
  - sub_404C40 (81 B)
  - sub_404C94 (59 B)
  - sub_42CA64 (76 B)
  - sub_42CFB8 (839 B)
  - sub_431578 (56 B)
  - sub_4315B0 (316 B): APIs PostMessageW; chama TAebVector::SetEcuData
  - sub_433B98 (1074 B)
  - sub_439E94 (288664 B): APIs Sleep,SendMessageW; chama TAebMatrix::GetEcuData,TAebMatrix::SetColumnDimension,TAebMatrix::SetEcuData,TAebNumber::GetEcuData; toca EditAnticipoEV2,EditPressRid
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - … +48 (ver telas.json)
- `ActionUpdateExecute` (0x005035D4):
  - sub_501E5C (5744 B): textos LABELAGGCENTRALINA,LABELAGGCONFIG,LABELCONNCENTRALINA,LABELPREPAGGCONFIG; APIs FindClose,FindFirstFileW,FindNextFileW,SetLastError; chama TAebProtocol::Connect,TAebProtocol::Disconnect,TAebProtocol::FindPort,TAebProtocol::OffLine
  -   sub_4047FC (125 B)
  -   sub_40487C (45 B)
  -   sub_404C40 (81 B)
  -   sub_404C94 (59 B)
  -   sub_42B8D4 (144 B): chama TAebNumber::GetEcuData,TAebProtocol::GetComFail,TAebProtocol::GetIdentification
  -   sub_42BB10 (45 B): chama TAebNumber::SetEcuData
  -   sub_42CFB8 (839 B)
  -   sub_431578 (56 B)
  -   sub_439E94 (288664 B): APIs Sleep,SendMessageW; chama TAebMatrix::GetEcuData,TAebMatrix::SetColumnDimension,TAebMatrix::SetEcuData,TAebNumber::GetEcuData; toca EditAnticipoEV2,EditPressRid
  -   sub_4828E4 (390 B): toca LabelRiprogramma
  -   sub_4F95FC (1501 B): APIs Sleep; chama TAebProtocol::Disconnect,TAebProtocol::FindPort,TAebProtocol::GetIdentification,TAebProtocol::IsConnect; toca MenuMarket
  - … +50 (ver telas.json)
- `ActionVisualizzaExecute` (0x00501568):
  - sub_40487C (45 B)
  - sub_404C40 (81 B)
  - sub_405754 (48 B)
  - sub_431578 (56 B)
  - sub_489034 (110 B)
  - sub_489320 (60 B)
  - sub_501838 (364 B): APIs IsDebuggerPresent
  -   sub_405784 (59 B)
  -   sub_414B8C (15681 B): textos MESSCONTACT,MESSDAYS,MESSEXPIRED,MESSWILLEXPIRE; chama TFormAggKey.Create,TTranslatorExt::GetString; toca LabelConfig
  -   sub_48944C (154 B)
  -   sub_4F3C50 (34 B)
  -     sub_4047FC (125 B)
  - … +5 (ver telas.json)
- `FormClose` (0x004F35C4):
  - sub_407D74 (168 B)
  - sub_50E6B8 (105 B)
- `FormCloseQuery` (0x004FBDE8):
  - sub_48944C (154 B)
  - sub_4FFC20 (503 B): toca ActionSalva
  -   sub_40487C (45 B)
  -   sub_404C40 (81 B)
  -   sub_405754 (48 B)
  -   sub_4057C0 (139 B)
  -   sub_42B234 (14 B)
  -   sub_489034 (110 B)
  -   sub_489320 (60 B)
  -   sub_48935C (60 B)
- `FormShow` (0x004F3E14):
  - sub_40487C (45 B)
  - sub_404C40 (81 B)
  - sub_405754 (48 B)
  - sub_4057C0 (139 B)
  - sub_420180 (232 B): chama TAebRegistryData::GetIntData,TTranslatorExt::CreatePopupMenu
  - sub_4808CC (57 B)
  - sub_489034 (110 B)
  - sub_489398 (60 B)
  - sub_4893D4 (60 B)
  - sub_489410 (60 B)
  - sub_4F484C (287 B): toca MenuClientPers
- `ImageServiceClick` (0x00503FB0):
  - sub_40487C (45 B)
  - sub_408248 (88 B)
  - sub_503DAC (513 B): chama TAebProtocol::IsConnect; toca BitBtnRiprogramma,ImageServiceOff,ImageServiceOn,ImageServiceOnAlrt
  -   sub_408120 (208 B)
  -   sub_433240 (61 B)
  -   sub_433304 (38 B)
  -   sub_43332C (25 B)
- `MenuAboutClick` (0x004F7808):
  - sub_40487C (45 B)
  - sub_480D60 (1496 B): textos BUTTONESCI,BUTTON_DELETE_LICENSE,BUTTON_LOAD_LICENSE,BUTTON_REQUEST_LICENSE; chama TAboutBox.Create,TTranslatorExt::GetString
- `OpenConfigWindow` (0x004F9190):
  - sub_40487C (45 B)
  - sub_404C40 (81 B)
  - sub_405754 (48 B)
  - sub_4057C0 (139 B)
  - sub_431578 (56 B)
  - sub_489034 (110 B)
  - sub_489320 (60 B)
  - sub_48935C (60 B)
  - sub_501838 (364 B): APIs IsDebuggerPresent
  -   sub_405784 (59 B)
  -   sub_414B8C (15681 B): textos MESSCONTACT,MESSDAYS,MESSEXPIRED,MESSWILLEXPIRE; chama TFormAggKey.Create,TTranslatorExt::GetString; toca LabelConfig
  -   sub_48944C (154 B)
  - … +7 (ver telas.json)
- `OpenFormAutotaratura` (0x0050093C):
  - sub_40487C (45 B)
  - sub_404C40 (81 B)
  - sub_405754 (48 B)
  - sub_4057C0 (139 B)
  - sub_431578 (56 B)
  - sub_489034 (110 B)
  - sub_489320 (60 B)
  - sub_48935C (60 B)
  - sub_501838 (364 B): APIs IsDebuggerPresent
  -   sub_405784 (59 B)
  -   sub_414B8C (15681 B): textos MESSCONTACT,MESSDAYS,MESSEXPIRED,MESSWILLEXPIRE; chama TFormAggKey.Create,TTranslatorExt::GetString; toca LabelConfig
  -   sub_48944C (154 B)
  - … +7 (ver telas.json)
- `Plot1Click` (0x0050417C):
  - sub_420448 (523 B): chama TAboutBox.Create
  -   sub_40487C (45 B)
  -   sub_42084C (2710 B): toca ActionAggiorna,ActionArchivio,ActionResetDaPc,ActionTara
  -     sub_41E8D0 (96 B)
  -     sub_4213E6 (3272 B)
  -     sub_4236FC (332 B): chama TAebRegistryData::GetIntData
  -     sub_4256FC (119 B): toca ChartDrt
  -     sub_4272D0 (3882 B): textos BUTTONAVVIA,BUTTONCARICA,BUTTONESPORTA,BUTTONFERMA; chama TTranslatorExt::GetString; toca ActionFileHcf,ActionResetCentralina,ActionResetDaPc,ActionSalvaMappa
  -     sub_429038 (5 B)
  -     sub_50F208 (141 B)
  -     sub_96BA2C (343 B)
- `StatusBarMainDrawPanel` (0x004FF7D4):
  - sub_40487C (45 B)
  - sub_431578 (56 B)
  - sub_431750 (53 B)
  - sub_5034CC (262 B): toca StatusBarMain
- `TimerExpiredTimer` (0x004F3774):
  - sub_40487C (45 B)
- `TimerUpdateTimer` (0x004F37F4):
  - sub_4047FC (125 B)
  - sub_40487C (45 B)
  - sub_404C40 (81 B)
  - sub_404C94 (59 B)
  - sub_405178 (192 B)
  - sub_4EA9F8 (772 B): APIs InternetAttemptConnect,InternetConnectW,InternetGetConnectedState,InternetOpenW
  -   sub_403568 (1449 B)
  -   sub_403D8C (385 B): APIs GetUserNameExW
  -     sub_40323C (528 B): APIs GetAdaptersAddresses
  -     sub_403458 (271 B)
  -     sub_404FBC (412 B)
