# TFormDiagnosi (recurso `TFORMDIAGNOSI`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormDiagnosi`; unidade: `@:Diagnosi`; ancestral: `TForm`; tamanho de instância: 0x6AC; componentes no DFM: 129.
- Título: "Diagnosi centralina" — eng: "ECU diagnosis" / por: "Diagnóstico central de comando"
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `TFormConfig.LabelDiagnosiClick` (0x004CB060), `TFormMain.ActionDiagnosiExecute` (0x004FFE24)

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

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `ButtonAzzeraErroriClick` | 0x004D6694 | 1094 | INFERIDO | sem fatos diretos extraíveis (1094 bytes; chama 12 funções sem nome) |  |  |  |
| `ButtonAzzeraTempiClick` | 0x004D6ADC | 375 | INFERIDO | toca LabelTBenz, LabelTGas |  |  |  |
| `ButtonResetFFClick` | 0x004D8360 | 25 | INFERIDO | sem fatos diretos extraíveis (25 bytes; chama 1 funções sem nome) |  |  |  |
| `CheckListBoxFFClickCheck` | 0x004D82C0 | 157 | INFERIDO | toca CheckListBoxFF |  |  |  |
| `CheckSwitchOnDiagnosisClick` | 0x004D7E48 | 48 | INFERIDO | toca CheckSwitchOnDiagnosis |  |  |  |
| `ComboAzioneChange` | 0x004D736C | 313 | INFERIDO | toca ComboAzione, ListDiagnosi |  |  |  |
| `ComboAzioneEnter` | 0x004D74A8 | 52 | INFERIDO | toca ComboAzione |  |  |  |
| `ComboAzioneKeyDown` | 0x004D7164 | 518 | INFERIDO | toca ComboAzione, EditTempAlta, EditTempAltaMot, EditTempBassa, EditTempBassaMot, ListDiagnosi |  |  |  |
| `EditTempBassaMotEnter` | 0x004D75B0 | 178 | INFERIDO | toca ListDiagnosi |  |  |  |
| `EditTempBassaMotKeyDown` | 0x004D74DC | 79 | INFERIDO | sem fatos diretos extraíveis (79 bytes; chama 0 funções sem nome) |  |  |  |
| `EditTempBassaMotKeyPress` | 0x004D752C | 130 | INFERIDO | toca ListDiagnosi |  |  |  |
| `FormActivate` | 0x004CD6A8 | 16307 | INFERIDO | usa textos BUTTONAZZERAERRORI, BUTTONAZZERATEMPI, BUTTONESCI, CHECKDIAGNOSISTARTUP, DIAGNOSICOLLEGAMENTOINIETTORI, DIAGNOSIESCLUSIONEINIETTORI; chama TTranslatorExt::GetString; toca ButtonAzzeraErrori, ButtonAzzeraTempi, ButtonEsci, CheckSwitchOnDiagnosis, ComboAzione, EditTempAlta, EditTempAltaMot, EditTempBassa | BUTTONAZZERAERRORI, BUTTONAZZERATEMPI, BUTTONESCI, CHECKDIAGNOSISTARTUP, DIAGNOSICOLLEGAMENTOINIETTORI, DIAGNOSIESCLUSIONEINIETTORI |  | FF_CANSTATUS_OBD, FF_ECUFUELFUNCTIONING, FF_EV1, FF_EV2 |
| `FormClose` | 0x004D1B18 | 366 | INFERIDO | chama TFormDiagnosi.PanelTestOffClick; toca ListDiagnosi, PanelOffElv1, PanelOffElv2, PanelOffSwitch |  |  |  |
| `ListDiagnosiChange` (sem evento no DFM) | 0x004D6E38 | 558 | INFERIDO | toca ListDiagnosi |  |  |  |
| `ListDiagnosiClick` | 0x004D7068 | 100 | INFERIDO | chama APIs do Windows GetCursorPos, ScreenToClient; toca ListDiagnosi |  | GetCursorPos, ScreenToClient |  |
| `ListDiagnosiKeyDown` | 0x004D6CB8 | 384 | INFERIDO | toca ComboAzione, EditTempAlta, EditTempAltaMot, EditTempBassa, EditTempBassaMot, ListDiagnosi |  |  |  |
| `ListDiagnosiKeyPress` | 0x004D6C54 | 97 | INFERIDO | toca ListDiagnosi |  |  |  |
| `ListDiagnosiSelectItem` | 0x004D70CC | 152 | INFERIDO | toca ComboAzione, ListDiagnosi |  |  |  |
| `PanelOffClick` | 0x004D7664 | 272 | INFERIDO | chama TAebNumber::GetEcuData, TAebNumber::SetEcuData, TAebProtocol::IsConnect |  |  |  |
| `PanelOnClick` | 0x004D7774 | 274 | INFERIDO | chama TAebNumber::GetEcuData, TAebNumber::SetEcuData, TAebProtocol::IsConnect |  |  |  |
| `PanelTestOffClick` | 0x004D7C78 | 461 | INFERIDO | usa textos MESSERRORRESETDIAGNOSYSTEST; chama TAebProtocol::IsConnect, TAebProtocol::SendCommand, TTranslatorExt::GetString; toca LabelElv1, LabelSwitch, PanelOffElv1, PanelOffElv2, PanelOffSwitch, PanelOnElv1, PanelOnElv2, PanelOnSwitch | MESSERRORRESETDIAGNOSYSTEST |  |  |
| `PanelTestOnClick` | 0x004D7888 | 1006 | INFERIDO | usa textos LABELDIAGNOSIATTENTION, MESSERRORSETDIAGNOSYSTEST; chama APIs do Windows Sleep; chama TAebProtocol::IsConnect, TAebProtocol::SendCommand, TStreamDati.TimerIOTimer, TTranslatorExt::GetString; toca PanelOffElv1, PanelOffElv2, PanelOffSwitch, PanelOnElv1, PanelOnElv2, PanelOnSwitch | LABELDIAGNOSIATTENTION, MESSERRORSETDIAGNOSYSTEST | Sleep |  |
| `TimerTempiTimer` | 0x004D4BDC | 6244 | INFERIDO | chama TAebProtocol::GetPacket, TAebProtocol::IsConnect; toca LabelA, LabelB, LabelC, LabelD, LabelE, LabelF, LabelG, LabelH |  |  |  |
| `TimerTimer` | 0x004D4B20 | 186 | INFERIDO | chama APIs do Windows GetTickCount, Sleep; chama TAebProtocol::Connect, TAebProtocol::DisableErrorHandling, TAebProtocol::EnableErrorHandling, TAebProtocol::IsConnect, TStreamDati.TimerIOTimer |  | GetTickCount, Sleep |  |
| `TimerUpdateTimer` | 0x004D6454 | 573 | INFERIDO | toca ComboAzione, EditTempAlta, EditTempAltaMot, EditTempBassa, EditTempBassaMot, ListDiagnosi, TimerUpdate |  |  |  |

### Cadeia de subfunções sem nome alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `ButtonAzzeraErroriClick` (0x004D6694):
  - sub_40487C (45 B)
  - sub_431878 (110 B): chama TAebVector::GetEcuData; toca MenuCopia
  - sub_4318F8 (244 B): chama TAebVector::SetEcuData; toca MenuCopia,MenuIncolla
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  - sub_48944C (154 B)
  - sub_4D1C88 (11926 B): chama TAebNumber::GetEcuData,TAebProtocol::GetComFail,TAebProtocol::IsConnect; toca ComboAzione,LabelOkA,LabelOkB,LabelOkC
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  - … +10 (ver telas.json)
- `ButtonAzzeraTempiClick` (0x004D6ADC):
  - sub_40487C (45 B)
  - sub_436444 (1384 B): toca LabelNumeroSonde,LabelTipoLambda
  - sub_43734C (2164 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData; toca DrawGridK,EditRifLambda,LabelV4,PanelArr
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `ButtonResetFFClick` (0x004D8360):
  - sub_51EE7C (95 B)
  -   sub_51ECAC (29 B): chama TAebNumber::SetInt
- `CheckListBoxFFClickCheck` (0x004D82C0):
  - sub_4319EC (84 B): chama TAebVector::SetEcuData
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `CheckSwitchOnDiagnosisClick` (0x004D7E48):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData; toca ButtonTaratura
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
- `ComboAzioneChange` (0x004D736C):
  - sub_431A5C (153 B): chama TAebMatrix::SetEcuData; toca Image1
  - sub_48944C (154 B)
  - sub_4D1C88 (11926 B): chama TAebNumber::GetEcuData,TAebProtocol::GetComFail,TAebProtocol::IsConnect; toca ComboAzione,LabelOkA,LabelOkB,LabelOkC
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_4047FC (125 B)
  -   sub_40487C (45 B)
  -   sub_431878 (110 B): chama TAebVector::GetEcuData; toca MenuCopia
  -   sub_431C38 (50 B): chama TAebVector::GetEcuData
  -   sub_434CC0 (834 B)
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - … +5 (ver telas.json)
- `ComboAzioneEnter` (0x004D74A8):
  - sub_48944C (154 B)
- `ComboAzioneKeyDown` (0x004D7164):
  - sub_48944C (154 B)
- `EditTempBassaMotKeyPress` (0x004D752C):
  - sub_431AF8 (124 B)
  - sub_431B7C (186 B): chama TAebVector::SetEcuData
  - sub_48944C (154 B)
  - sub_96BA2C (343 B)
  - sub_96BB88 (321 B)
  -   sub_42A52C (358 B)
  -   sub_42A788 (358 B)
  -   sub_42A698 (235 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  - … +9 (ver telas.json)
- `FormActivate` (0x004CD6A8):
  - sub_40487C (45 B)
  - sub_404C40 (81 B)
  - sub_405754 (48 B)
  - sub_42DF14 (599 B)
  - sub_42E754 (385 B)
  - sub_431878 (110 B): chama TAebVector::GetEcuData; toca MenuCopia
  - sub_431A40 (27 B)
  - sub_431AF8 (124 B)
  - sub_43400C (7 B)
  - sub_43478C (34 B)
  - sub_434B10 (34 B)
  - sub_434CC0 (834 B)
  - … +26 (ver telas.json)
- `FormClose` (0x004D1B18):
  - sub_4381F4 (7 B)
- `ListDiagnosiChange` (0x004D6E38):
  - sub_40487C (45 B)
  - sub_431878 (110 B): chama TAebVector::GetEcuData; toca MenuCopia
  - sub_4318F8 (244 B): chama TAebVector::SetEcuData; toca MenuCopia,MenuIncolla
  - sub_48944C (154 B)
  - sub_4D1C88 (11926 B): chama TAebNumber::GetEcuData,TAebProtocol::GetComFail,TAebProtocol::IsConnect; toca ComboAzione,LabelOkA,LabelOkB,LabelOkC
  -   sub_42B224 (15 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_4047FC (125 B)
  -   sub_431C38 (50 B): chama TAebVector::GetEcuData
  -   sub_434CC0 (834 B)
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - … +5 (ver telas.json)
- `ListDiagnosiKeyDown` (0x004D6CB8):
  - sub_48944C (154 B)
- `ListDiagnosiKeyPress` (0x004D6C54):
  - sub_48944C (154 B)
- `PanelOffClick` (0x004D7664):
  - sub_42CFB8 (839 B)
  - sub_43481C (34 B)
  - sub_4D1C88 (11926 B): chama TAebNumber::GetEcuData,TAebProtocol::GetComFail,TAebProtocol::IsConnect; toca ComboAzione,LabelOkA,LabelOkB,LabelOkC
  -   sub_4047FC (125 B)
  -   sub_40487C (45 B)
  -   sub_431878 (110 B): chama TAebVector::GetEcuData; toca MenuCopia
  -   sub_431C38 (50 B): chama TAebVector::GetEcuData
  -   sub_434CC0 (834 B)
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_48944C (154 B)
  -     sub_42A788 (358 B)
  -     sub_433FCC (47 B)
  - … +3 (ver telas.json)
- `PanelOnClick` (0x004D7774):
  - sub_42CFB8 (839 B)
  - sub_43481C (34 B)
  - sub_4D1C88 (11926 B): chama TAebNumber::GetEcuData,TAebProtocol::GetComFail,TAebProtocol::IsConnect; toca ComboAzione,LabelOkA,LabelOkB,LabelOkC
  -   sub_4047FC (125 B)
  -   sub_40487C (45 B)
  -   sub_431878 (110 B): chama TAebVector::GetEcuData; toca MenuCopia
  -   sub_431C38 (50 B): chama TAebVector::GetEcuData
  -   sub_434CC0 (834 B)
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_48944C (154 B)
  -     sub_42A788 (358 B)
  -     sub_433FCC (47 B)
  - … +3 (ver telas.json)
- `PanelTestOffClick` (0x004D7C78):
  - sub_40487C (45 B)
- `PanelTestOnClick` (0x004D7888):
  - sub_40487C (45 B)
  - sub_4381FC (6 B)
- `TimerTempiTimer` (0x004D4BDC):
  - sub_40487C (45 B)
  - sub_433FFC (14 B)
- `TimerTimer` (0x004D4B20):
  - sub_4D1C88 (11926 B): chama TAebNumber::GetEcuData,TAebProtocol::GetComFail,TAebProtocol::IsConnect; toca ComboAzione,LabelOkA,LabelOkB,LabelOkC
  - sub_4D8290 (48 B)
  -   sub_4047FC (125 B)
  -   sub_40487C (45 B)
  -   sub_431878 (110 B): chama TAebVector::GetEcuData; toca MenuCopia
  -   sub_431C38 (50 B): chama TAebVector::GetEcuData
  -   sub_434CC0 (834 B)
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_48944C (154 B)
  -   sub_51ED04 (373 B)
  -     sub_42A788 (358 B)
  -     sub_433FCC (47 B)
  - … +5 (ver telas.json)
- `TimerUpdateTimer` (0x004D6454):
  - sub_48944C (154 B)
  - sub_96C73C (114 B)
