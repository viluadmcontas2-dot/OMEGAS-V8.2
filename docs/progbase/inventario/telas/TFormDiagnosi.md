# TFormDiagnosi (recurso `TFORMDIAGNOSI`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

**Papel:** tela de função (OBS-COD). Diagnóstico da central: falhas, tempos de funcionamento, exclusão de injetores de gás e teste de saídas.

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
| `ButtonAzzeraErroriClick` | 0x004D6694 | 1094 | INFERIDO | Botão "Zerar erros": envia à ECU o comando de apagar a memória de falhas (1094 B; usa TAebNumber/SetEcuData e textos de confirmação). Escreve na ECU. INFERIDO pelo nome+caption. |  |  |  |
| `ButtonAzzeraTempiClick` | 0x004D6ADC | 375 | INFERIDO | Botão "Zerar tempos": zera os contadores de tempo a gasolina e a gás (LabelTBenz/LabelTGas passam a "0:00"). Escreve na ECU: INFERIDO. OBS-COD (literal "0:00 "). |  |  |  |
| `ButtonResetFFClick` | 0x004D8360 | 25 | INFERIDO | Botão Reset do quadro de congelamento (Freeze Frame): 25 B, chama rotina sem nome. INFERIDO. |  |  |  |
| `CheckListBoxFFClickCheck` | 0x004D82C0 | 157 | INFERIDO | Marca/desmarca itens da lista de congelamento (freeze frame) a registrar. INFERIDO. |  |  |  |
| `CheckSwitchOnDiagnosisClick` | 0x004D7E48 | 48 | INFERIDO | Caixa "Habilitar diagnóstico ao ligar" (switch-on diagnosis). 48 B; grava a opção na estrutura de configuração. INFERIDO. |  |  |  |
| `ComboAzioneChange` | 0x004D736C | 313 | INFERIDO | Quando muda o valor de ComboAzione: mexe em ComboAzione, ListDiagnosi. |  |  |  |
| `ComboAzioneEnter` | 0x004D74A8 | 52 | INFERIDO | Ao entrar no campo ComboAzione: mexe em ComboAzione. |  |  |  |
| `ComboAzioneKeyDown` | 0x004D7164 | 518 | INFERIDO | Ao pressionar tecla em ComboAzione: mexe em ComboAzione, EditTempAlta, EditTempAltaMot, EditTempBassa, EditTempBassaMot, ListDiagnosi. |  |  |  |
| `EditTempBassaMotEnter` | 0x004D75B0 | 178 | INFERIDO | Ao entrar no campo EditTempAlta (+3 componentes): mexe em ListDiagnosi. |  |  |  |
| `EditTempBassaMotKeyDown` | 0x004D74DC | 79 | INFERIDO | Ao pressionar tecla em EditTempAlta (+3 componentes): corpo de 79 bytes sem fatos diretos (chama 0 funções sem nome). |  |  |  |
| `EditTempBassaMotKeyPress` | 0x004D752C | 130 | INFERIDO | Ao digitar (filtro de teclas) em EditTempAlta (+3 componentes): mexe em ListDiagnosi. |  |  |  |
| `FormActivate` | 0x004CD6A8 | 16307 | OBS-COD | Ao ativar a tela de Diagnóstico (16 KB): aplica traduções (BUTTONAZZERAERRORI, DIAGNOSI*…), carrega a lista de falhas ("(Código) descrição do erro"), códigos EPR (Open Load, Overcurrent…) e configura os painéis de teste. OBS-COD (textos/literais). | BUTTONAZZERAERRORI, BUTTONAZZERATEMPI, BUTTONESCI, CHECKDIAGNOSISTARTUP, DIAGNOSICOLLEGAMENTOINIETTORI, DIAGNOSIESCLUSIONEINIETTORI |  | FF_CANSTATUS_OBD, FF_ECUFUELFUNCTIONING, FF_EV1, FF_EV2 |
| `FormClose` | 0x004D1B18 | 366 | OBS-COD | Ao fechar: desliga os testes de saída ativos (chama PanelTestOffClick) e restaura a lista. OBS-COD. |  |  |  |
| `ListDiagnosiChange` (sem evento no DFM) | 0x004D6E38 | 558 | INFERIDO | Rotina publicada sem evento ligado no DFM (chamada pelo código): mexe em ListDiagnosi. |  |  |  |
| `ListDiagnosiClick` | 0x004D7068 | 100 | INFERIDO | Ao clicar em ListDiagnosi: chama APIs GetCursorPos, ScreenToClient; mexe em ListDiagnosi. |  | GetCursorPos, ScreenToClient |  |
| `ListDiagnosiKeyDown` | 0x004D6CB8 | 384 | INFERIDO | Ao pressionar tecla em ListDiagnosi: mexe em ComboAzione, EditTempAlta, EditTempAltaMot, EditTempBassa, EditTempBassaMot, ListDiagnosi. |  |  |  |
| `ListDiagnosiKeyPress` | 0x004D6C54 | 97 | INFERIDO | Ao digitar (filtro de teclas) em ListDiagnosi: mexe em ListDiagnosi. |  |  |  |
| `ListDiagnosiSelectItem` | 0x004D70CC | 152 | INFERIDO | Ao selecionar item em ListDiagnosi: mexe em ComboAzione, ListDiagnosi. |  |  |  |
| `PanelOffClick` | 0x004D7664 | 272 | INFERIDO | Botões "Off" da seção "Esclusione iniettori gas": desliga (exclui) o injetor escolhido (GetEcuData/SetEcuData). Escreve na ECU: PERIGOSO. INFERIDO. |  |  |  |
| `PanelOnClick` | 0x004D7774 | 274 | INFERIDO | Botões "On" da seção "Esclusione iniettori gas" (injetores A..I): lê (TAebNumber::GetEcuData) e regrava (SetEcuData) um valor da ECU ligado ao injetor escolhido, se conectado. Escreve na ECU: PERIGOSO (afeta a injeção de gás). Chamadas: OBS-COD; efeito sobre o injetor: INFERIDO pelo caption. |  |  |  |
| `PanelTestOffClick` | 0x004D7C78 | 461 | DERIVADO | Botões "Off" do teste de saídas: envia comando de parada do teste; falha mostra MESSERRORRESETDIAGNOSYSTEST. DERIVADO. | MESSERRORRESETDIAGNOSYSTEST |  |  |
| `PanelTestOnClick` | 0x004D7888 | 1006 | DERIVADO | Botões "On" do teste de saídas (Elv1, Elv2, Switch): envia comando de teste (TAebProtocol::SendCommand), mostra aviso LABELDIAGNOSIATTENTION e MESSERRORSETDIAGNOSYSTEST em caso de falha. Aciona eletroválvulas/LED da ECU: PERIGOSO. DERIVADO. | LABELDIAGNOSIATTENTION, MESSERRORSETDIAGNOSYSTEST | Sleep |  |
| `TimerTempiTimer` | 0x004D4BDC | 6244 | DERIVADO | Atualização periódica (6244 B): lê pacotes da ECU (TAebProtocol::GetPacket) e atualiza os tempos por injetor A..I (gas/benzina), tensões e estados na tela. OBS-COD (chamadas/campos). |  |  |  |
| `TimerTimer` | 0x004D4B20 | 186 | DERIVADO | Temporizador de reconexão: tenta Connect/DisableErrorHandling/EnableErrorHandling quando a ECU não responde. OBS-COD. |  | GetTickCount, Sleep |  |
| `TimerUpdateTimer` | 0x004D6454 | 573 | OBS-COD | Atualiza o painel de edição de limites de temperatura do item selecionado de ListDiagnosi. OBS-COD (campos). |  |  |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `ButtonAzzeraErroriClick` (0x004D6694):
  - sub_431878 (110 B): chama TAebVector::GetEcuData
  - sub_4318F8 (244 B): chama TAebVector::SetEcuData
  - sub_4D1C88 (11926 B): chama TAebNumber::GetEcuData,TAebProtocol::GetComFail,TAebProtocol::IsConnect
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -   sub_431C38 (50 B): chama TAebVector::GetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+11 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonAzzeraTempiClick` (0x004D6ADC):
  - sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+5 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonResetFFClick` (0x004D8360):
  -   sub_51ECAC (29 B): chama TAebNumber::SetInt
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckListBoxFFClickCheck` (0x004D82C0):
  - sub_4319EC (84 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckSwitchOnDiagnosisClick` (0x004D7E48):
  - sub_42E16C (1512 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboAzioneChange` (0x004D736C):
  - sub_431A5C (153 B): chama TAebMatrix::SetEcuData
  - sub_4D1C88 (11926 B): chama TAebNumber::GetEcuData,TAebProtocol::GetComFail,TAebProtocol::IsConnect
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431878 (110 B): chama TAebVector::GetEcuData
  -   sub_431C38 (50 B): chama TAebVector::GetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+8 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboAzioneEnter` (0x004D74A8):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ComboAzioneKeyDown` (0x004D7164):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditTempBassaMotKeyPress` (0x004D752C):
  - sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+18 subfunções sem fatos diretos ou fora da lista; telas.json)
- `FormActivate` (0x004CD6A8):
  - sub_431878 (110 B): chama TAebVector::GetEcuData
  - sub_4D7E78 (1045 B): textos FF_TAB_EN,FF_TAB_VIEW; chama TTranslatorExt::GetString
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_51DB24 (4247 B): textos FF_CANSTATUS_OBD,FF_ECUFUELFUNCTIONING,FF_EV1,FF_EV2; chama TTranslatorExt::GetString
  - (+30 subfunções sem fatos diretos ou fora da lista; telas.json)
- `FormClose` (0x004D1B18):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ListDiagnosiChange` (0x004D6E38):
  - sub_431878 (110 B): chama TAebVector::GetEcuData
  - sub_4318F8 (244 B): chama TAebVector::SetEcuData
  - sub_4D1C88 (11926 B): chama TAebNumber::GetEcuData,TAebProtocol::GetComFail,TAebProtocol::IsConnect
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431C38 (50 B): chama TAebVector::GetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+8 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ListDiagnosiKeyDown` (0x004D6CB8):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ListDiagnosiKeyPress` (0x004D6C54):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `PanelOffClick` (0x004D7664):
  - sub_4D1C88 (11926 B): chama TAebNumber::GetEcuData,TAebProtocol::GetComFail,TAebProtocol::IsConnect
  -   sub_431878 (110 B): chama TAebVector::GetEcuData
  -   sub_431C38 (50 B): chama TAebVector::GetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+8 subfunções sem fatos diretos ou fora da lista; telas.json)
- `PanelOnClick` (0x004D7774):
  - sub_4D1C88 (11926 B): chama TAebNumber::GetEcuData,TAebProtocol::GetComFail,TAebProtocol::IsConnect
  -   sub_431878 (110 B): chama TAebVector::GetEcuData
  -   sub_431C38 (50 B): chama TAebVector::GetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+8 subfunções sem fatos diretos ou fora da lista; telas.json)
- `PanelTestOnClick` (0x004D7888):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TimerTempiTimer` (0x004D4BDC):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TimerTimer` (0x004D4B20):
  - sub_4D1C88 (11926 B): chama TAebNumber::GetEcuData,TAebProtocol::GetComFail,TAebProtocol::IsConnect
  -   sub_431878 (110 B): chama TAebVector::GetEcuData
  -   sub_431C38 (50 B): chama TAebVector::GetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  - (+10 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TimerUpdateTimer` (0x004D6454):
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
