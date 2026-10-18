# TFormDiagnosi (recurso `TFORMDIAGNOSI`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormDiagnosi`; unidade: `@:Diagnosi`; ancestral: `TForm`; tamanho de instância: 0x6AC; componentes no DFM: 129.
- Título: "Diagnosi centralina" — eng: "ECU diagnosis" / por: "Diagnóstico central de comando"
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

## Controles com evento/ação (39)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| FormDiagnosi | TFormDiagnosi | Diagnosi centralina / ECU diagnosis / Diagnóstico central de comando |  | OnActivate→`FormActivate` (0x004CD6A8); OnClose→`FormClose` (0x004D1B18) |
| ButtonAzzeraErrori | TButton | Azzera e&rrori / &Reset errors / Reset e&rros |  | OnClick→`ButtonAzzeraErroriClick` (0x004D6694) |
| PageTempi/TabTempi/ButtonAzzeraTempi | TButton | Azzera &tempi / Reset &times / Reset &tempos |  | OnClick→`ButtonAzzeraTempiClick` (0x004D6ADC) |
| PageTempi/TabEsclusione/PanelOffA | TPanel | Off |  | OnClick→`PanelOffClick` (0x004D7664) |
| PageTempi/TabEsclusione/PanelOnA | TPanel | On |  | OnClick→`PanelOnClick` (0x004D7774) |
| PageTempi/TabEsclusione/PanelOffB | TPanel | Off |  | OnClick→`PanelOffClick` (0x004D7664) |
| PageTempi/TabEsclusione/PanelOnB | TPanel | On |  | OnClick→`PanelOnClick` (0x004D7774) |
| PageTempi/TabEsclusione/PanelOnC | TPanel | On |  | OnClick→`PanelOnClick` (0x004D7774) |
| PageTempi/TabEsclusione/PanelOffC | TPanel | Off |  | OnClick→`PanelOffClick` (0x004D7664) |
| PageTempi/TabEsclusione/PanelOffD | TPanel | Off |  | OnClick→`PanelOffClick` (0x004D7664) |
| PageTempi/TabEsclusione/PanelOnD | TPanel | On |  | OnClick→`PanelOnClick` (0x004D7774) |
| PageTempi/TabEsclusione/PanelOnI | TPanel | On |  | OnClick→`PanelOnClick` (0x004D7774) |
| PageTempi/TabEsclusione/PanelOffI | TPanel | Off |  | OnClick→`PanelOffClick` (0x004D7664) |
| PageTempi/TabEsclusione/PanelOffE | TPanel | Off |  | OnClick→`PanelOffClick` (0x004D7664) |
| PageTempi/TabEsclusione/PanelOnE | TPanel | On |  | OnClick→`PanelOnClick` (0x004D7774) |
| PageTempi/TabEsclusione/PanelOnF | TPanel | On |  | OnClick→`PanelOnClick` (0x004D7774) |
| PageTempi/TabEsclusione/PanelOffF | TPanel | Off |  | OnClick→`PanelOffClick` (0x004D7664) |
| PageTempi/TabEsclusione/PanelOffG | TPanel | Off |  | OnClick→`PanelOffClick` (0x004D7664) |
| PageTempi/TabEsclusione/PanelOnG | TPanel | On |  | OnClick→`PanelOnClick` (0x004D7774) |
| PageTempi/TabEsclusione/PanelOnH | TPanel | On |  | OnClick→`PanelOnClick` (0x004D7774) |
| PageTempi/TabEsclusione/PanelOffH | TPanel | Off |  | OnClick→`PanelOffClick` (0x004D7664) |
| PageTempi/TabEsclusione/PanelOffL | TPanel | Off |  | OnClick→`PanelOffClick` (0x004D7664) |
| PageTempi/TabEsclusione/PanelOnL | TPanel | On |  | OnClick→`PanelOnClick` (0x004D7774) |
| PageTempi/TabTest/PanelOffSwitch | TPanel | Off |  | OnClick→`PanelTestOffClick` (0x004D7C78) |
| PageTempi/TabTest/PanelOnSwitch | TPanel | On |  | OnClick→`PanelTestOnClick` (0x004D7888) |
| PageTempi/TabTest/PanelOffElv2 | TPanel | Off |  | OnClick→`PanelTestOffClick` (0x004D7C78) |
| PageTempi/TabTest/PanelOffElv1 | TPanel | Off |  | OnClick→`PanelTestOffClick` (0x004D7C78) |
| PageTempi/TabTest/PanelOnElv2 | TPanel | On |  | OnClick→`PanelTestOnClick` (0x004D7888) |
| PageTempi/TabTest/PanelOnElv1 | TPanel | On |  | OnClick→`PanelTestOnClick` (0x004D7888) |
| PageTempi/TabFF_View/ButtonResetFF | TButton | Reset |  | OnClick→`ButtonResetFFClick` (0x004D8360) |
| PageTempi/TabFF_En/CheckListBoxFF | TCheckListBox |  |  | OnClickCheck→`CheckListBoxFFClickCheck` (0x004D82C0) |
| ListDiagnosi | TListView |  |  | OnClick→`ListDiagnosiClick` (0x004D7068); OnKeyDown→`ListDiagnosiKeyDown` (0x004D6CB8); OnKeyPress→`ListDiagnosiKeyPress` (0x004D6C54); OnSelectItem→`ListDiagnosiSelectItem` (0x004D70CC) |
| EditTempAlta | TNumericEdit |  |  | OnEnter→`EditTempBassaMotEnter` (0x004D75B0); OnKeyDown→`EditTempBassaMotKeyDown` (0x004D74DC); OnKeyPress→`EditTempBassaMotKeyPress` (0x004D752C) |
| EditTempBassa | TNumericEdit |  |  | OnEnter→`EditTempBassaMotEnter` (0x004D75B0); OnKeyDown→`EditTempBassaMotKeyDown` (0x004D74DC); OnKeyPress→`EditTempBassaMotKeyPress` (0x004D752C) |
| EditTempAltaMot | TNumericEdit |  |  | OnEnter→`EditTempBassaMotEnter` (0x004D75B0); OnKeyDown→`EditTempBassaMotKeyDown` (0x004D74DC); OnKeyPress→`EditTempBassaMotKeyPress` (0x004D752C) |
| EditTempBassaMot | TNumericEdit |  |  | OnEnter→`EditTempBassaMotEnter` (0x004D75B0); OnKeyDown→`EditTempBassaMotKeyDown` (0x004D74DC); OnKeyPress→`EditTempBassaMotKeyPress` (0x004D752C) |
| ComboAzione | TComboBox |  |  | OnChange→`ComboAzioneChange` (0x004D736C); OnEnter→`ComboAzioneEnter` (0x004D74A8); OnKeyDown→`ComboAzioneKeyDown` (0x004D7164) |
| CheckSwitchOnDiagnosis | TCheckBox | Abilita diagnosi all'avviamento / Enable switch-on diagnosis / Enable switch-on diagnosis |  | OnClick→`CheckSwitchOnDiagnosisClick` (0x004D7E48) |
| ButtonEsci | TButton | &Esci / &Exit / &Sair |  |  |

## Temporizadores (3)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) | Intervalo ms |
|---|---|---|---|---|---|
| Timer | TTimer |  |  | OnTimer→`TimerTimer` (0x004D4B20) | 2000 |
| TimerTempi | TTimer |  |  | OnTimer→`TimerTempiTimer` (0x004D4BDC) | 50 |
| TimerUpdate | TTimer |  |  | OnTimer→`TimerUpdateTimer` (0x004D6454) | 50 |

## Handlers (25): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos (chaves i18n) | APIs | Chama |
|---|---|---:|---|---|---|---|---|
| `ButtonAzzeraErroriClick` | 0x004D6694 | 1094 | INFERIDO | Handler `ButtonAzzeraErroriClick`; sem fatos extraíveis automaticamente (1094 bytes) |  |  |  |
| `ButtonAzzeraTempiClick` | 0x004D6ADC | 375 | INFERIDO | Handler `ButtonAzzeraTempiClick`; toca campos/controles: LabelTBenz, LabelTGas |  |  |  |
| `ButtonResetFFClick` | 0x004D8360 | 25 | INFERIDO | Handler `ButtonResetFFClick`; sem fatos extraíveis automaticamente (25 bytes) |  |  |  |
| `CheckListBoxFFClickCheck` | 0x004D82C0 | 157 | INFERIDO | Handler `CheckListBoxFFClickCheck`; toca campos/controles: CheckListBoxFF |  |  |  |
| `CheckSwitchOnDiagnosisClick` | 0x004D7E48 | 48 | INFERIDO | Handler `CheckSwitchOnDiagnosisClick`; toca campos/controles: CheckSwitchOnDiagnosis |  |  |  |
| `ComboAzioneChange` | 0x004D736C | 313 | INFERIDO | Handler `ComboAzioneChange`; toca campos/controles: ComboAzione, ListDiagnosi |  |  |  |
| `ComboAzioneEnter` | 0x004D74A8 | 52 | INFERIDO | Handler `ComboAzioneEnter`; toca campos/controles: ComboAzione |  |  |  |
| `ComboAzioneKeyDown` | 0x004D7164 | 518 | INFERIDO | Handler `ComboAzioneKeyDown`; toca campos/controles: ComboAzione, EditTempAlta, EditTempAltaMot, EditTempBassa, EditTempBassaMot, ListDiagnosi |  |  |  |
| `EditTempBassaMotEnter` | 0x004D75B0 | 178 | INFERIDO | Handler `EditTempBassaMotEnter`; toca campos/controles: ListDiagnosi |  |  |  |
| `EditTempBassaMotKeyDown` | 0x004D74DC | 79 | INFERIDO | Handler `EditTempBassaMotKeyDown`; sem fatos extraíveis automaticamente (79 bytes) |  |  |  |
| `EditTempBassaMotKeyPress` | 0x004D752C | 130 | INFERIDO | Handler `EditTempBassaMotKeyPress`; toca campos/controles: ListDiagnosi |  |  |  |
| `FormActivate` | 0x004CD6A8 | 16307 | INFERIDO | Handler `FormActivate`; mostra/usa textos BUTTONAZZERAERRORI, BUTTONAZZERATEMPI, BUTTONESCI, CHECKDIAGNOSISTARTUP, DIAGNOSICOLLEGAMENTOINIETTORI, DIAGNOSIESCLUSIONEINIETTORI; chama: TTranslatorExt::GetString; toca campos/controles: ButtonAzzeraErrori, ButtonAzzeraTempi, ButtonEsci, CheckSwitchOnDiagnosis, ComboAzione, EditTempAlta, EditTempAltaMot, EditTempBassa | BUTTONAZZERAERRORI, BUTTONAZZERATEMPI, BUTTONESCI, CHECKDIAGNOSISTARTUP, DIAGNOSICOLLEGAMENTOINIETTORI, DIAGNOSIESCLUSIONEINIETTORI |  | TTranslatorExt::GetString |
| `FormClose` | 0x004D1B18 | 366 | INFERIDO | Handler `FormClose`; chama: TFormDiagnosi.PanelTestOffClick; toca campos/controles: ListDiagnosi, PanelOffElv1, PanelOffElv2, PanelOffSwitch |  |  | TFormDiagnosi.PanelTestOffClick |
| `ListDiagnosiChange` (sem evento no DFM) | 0x004D6E38 | 558 | INFERIDO | Handler `ListDiagnosiChange`; toca campos/controles: ListDiagnosi |  |  |  |
| `ListDiagnosiClick` | 0x004D7068 | 100 | INFERIDO | Handler `ListDiagnosiClick`; chama APIs do Windows: USER32.DLL!GetCursorPos, USER32.DLL!ScreenToClient; chama: thunk -> USER32.DLL!GetCursorPos, thunk -> USER32.DLL!ScreenToClient; toca campos/controles: ListDiagnosi |  | USER32.DLL!GetCursorPos, USER32.DLL!ScreenToClient | thunk -> USER32.DLL!GetCursorPos, thunk -> USER32.DLL!ScreenToClient |
| `ListDiagnosiKeyDown` | 0x004D6CB8 | 384 | INFERIDO | Handler `ListDiagnosiKeyDown`; toca campos/controles: ComboAzione, EditTempAlta, EditTempAltaMot, EditTempBassa, EditTempBassaMot, ListDiagnosi |  |  |  |
| `ListDiagnosiKeyPress` | 0x004D6C54 | 97 | INFERIDO | Handler `ListDiagnosiKeyPress`; toca campos/controles: ListDiagnosi |  |  |  |
| `ListDiagnosiSelectItem` | 0x004D70CC | 152 | INFERIDO | Handler `ListDiagnosiSelectItem`; toca campos/controles: ComboAzione, ListDiagnosi |  |  |  |
| `PanelOffClick` | 0x004D7664 | 272 | INFERIDO | Handler `PanelOffClick`; chama: TAebNumber::GetEcuData, TAebNumber::SetEcuData, TAebProtocol::IsConnect |  |  | TAebNumber::GetEcuData, TAebNumber::SetEcuData, TAebProtocol::IsConnect |
| `PanelOnClick` | 0x004D7774 | 274 | INFERIDO | Handler `PanelOnClick`; chama: TAebNumber::GetEcuData, TAebNumber::SetEcuData, TAebProtocol::IsConnect |  |  | TAebNumber::GetEcuData, TAebNumber::SetEcuData, TAebProtocol::IsConnect |
| `PanelTestOffClick` | 0x004D7C78 | 461 | INFERIDO | Handler `PanelTestOffClick`; mostra/usa textos MESSERRORRESETDIAGNOSYSTEST; chama: TAebProtocol::IsConnect, TAebProtocol::SendCommand, TTranslatorExt::GetString; toca campos/controles: LabelElv1, LabelSwitch, PanelOffElv1, PanelOffElv2, PanelOffSwitch, PanelOnElv1, PanelOnElv2, PanelOnSwitch | MESSERRORRESETDIAGNOSYSTEST |  | TAebProtocol::IsConnect, TAebProtocol::SendCommand, TTranslatorExt::GetString |
| `PanelTestOnClick` | 0x004D7888 | 1006 | INFERIDO | Handler `PanelTestOnClick`; mostra/usa textos LABELDIAGNOSIATTENTION, MESSERRORSETDIAGNOSYSTEST; chama APIs do Windows: KERNEL32.DLL!Sleep; chama: TAebProtocol::IsConnect, TAebProtocol::SendCommand, TStreamDati.TimerIOTimer, TTranslatorExt::GetString, thunk -> KERNEL32.DLL!Sleep; toca campos/controles: PanelOffElv1, PanelOffElv2, PanelOffSwitch, PanelOnElv1, PanelOnElv2, PanelOnSwitch | LABELDIAGNOSIATTENTION, MESSERRORSETDIAGNOSYSTEST | KERNEL32.DLL!Sleep | TAebProtocol::IsConnect, TAebProtocol::SendCommand, TStreamDati.TimerIOTimer, TTranslatorExt::GetString, thunk -> KERNEL32.DLL!Sleep |
| `TimerTempiTimer` | 0x004D4BDC | 6244 | INFERIDO | Handler `TimerTempiTimer`; chama: TAebProtocol::GetPacket, TAebProtocol::IsConnect; toca campos/controles: LabelA, LabelB, LabelC, LabelD, LabelE, LabelF, LabelG, LabelH |  |  | TAebProtocol::GetPacket, TAebProtocol::IsConnect |
| `TimerTimer` | 0x004D4B20 | 186 | INFERIDO | Handler `TimerTimer`; chama APIs do Windows: KERNEL32.DLL!GetTickCount, KERNEL32.DLL!Sleep; chama: TAebProtocol::Connect, TAebProtocol::DisableErrorHandling, TAebProtocol::EnableErrorHandling, TAebProtocol::IsConnect, TStreamDati.TimerIOTimer, thunk -> KERNEL32.DLL!GetTickCount |  | KERNEL32.DLL!GetTickCount, KERNEL32.DLL!Sleep | TAebProtocol::Connect, TAebProtocol::DisableErrorHandling, TAebProtocol::EnableErrorHandling, TAebProtocol::IsConnect, TStreamDati.TimerIOTimer |
| `TimerUpdateTimer` | 0x004D6454 | 573 | INFERIDO | Handler `TimerUpdateTimer`; toca campos/controles: ComboAzione, EditTempAlta, EditTempAltaMot, EditTempBassa, EditTempBassaMot, ListDiagnosi, TimerUpdate |  |  |  |
