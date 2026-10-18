# TFormService (recurso `TFORMSERVICE`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

**Papel:** tela de função (DERIVADO). Gestão de revisões (tagliandi) com PIN e dados do instalador; inibição da passagem a gás.

- Instância DFM: `FormService`; unidade: `@:Service`; ancestral: `TForm`; tamanho de instância: 0x494; componentes no DFM: 36.
- Título: "Gestione tagliandi" — eng: "Service" / por: "Service"
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `TFormMain.ImageServiceClick` (0x00503FB0)

## Controles com evento/ação (12)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| FormService | TFormService | Gestione tagliandi / Service / Service |  | OnClose→`FormClose` (0x00508D28); OnKeyPress→`FormKeyPress` (0x00507544) |
| PanelPin/EditInsPin | TEdit |  |  | OnChange→`EditInsPinChange` (0x00507700); OnKeyPress→`EditPinKeyPress` (0x00508AA0) |
| PanelPin/ButtonResetPin | TButton | Reset |  | OnClick→`ButtonResetPinClick` (0x005075B4) |
| PanelService/MemoData | TMemo |  |  | OnKeyPress→`MemoDataKeyPress` (0x00508AC0) |
| PanelService/CheckDisableGas | TCheckBox | Inibisci passaggio a gas / Inhibit petrol to gas switching / Inhibit petrol to gas switching |  | OnClick→`CheckDisableGasClick` (0x005085CC) |
| PanelService/ButtonAzzeraNumeroTagliandiEffettuati | TButton | Azzera / Reset / Reset |  | OnClick→`ButtonAzzeraNumeroTagliandiEffettuatiClick` (0x00508B44) |
| PanelService/EditPartenzeEmergenza | TNumericEdit |  |  | OnEnter→`EditStandardEnter` (0x00506B68); OnExit→`EditStandardExit` (0x00506D80); OnKeyPress→`EditStandardKeyPress` (0x00506D9C) |
| PanelNewPin/EditPin | TEdit |  |  | OnKeyPress→`EditPinKeyPress` (0x00508AA0) |
| PanelNewPin/EditPin2 | TEdit |  |  | OnKeyPress→`EditPinKeyPress` (0x00508AA0) |
| PanelNewPin/ButtonNewPin | TButton | OK |  | OnClick→`ButtonNewPinClick` (0x005088E8) |
| PanelNewPin/ButtonOldPin | TButton | Cancella / Delete / Cancelar |  | OnClick→`ButtonOldPinClick` (0x0050886C) |
| ButtonExit | TButton | Esci / Exit / Sair |  | OnClick→`ButtonExitClick` (0x005078D0) |

## Handlers (21): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `ButtonAzzeraNumeroTagliandiEffettuatiClick` | 0x00508B44 | 483 | INFERIDO | Botão "Zerar" revisões efetuadas: zera contador (LabelNumTagliandiEffettuati*) após confirmação. Escreve na ECU. INFERIDO. |  |  |  |
| `ButtonExitClick` | 0x005078D0 | 6 | INFERIDO | Ao clicar em ButtonExit «Sair»: corpo de 6 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `ButtonModifyPinClick` (sem evento no DFM) | 0x00508720 | 305 | DERIVADO | Botão "Modificar PIN": mostra o painel de novo PIN (PanelNewPin). OBS-COD (campos). |  |  |  |
| `ButtonNewPinClick` | 0x005088E8 | 438 | INFERIDO | Botão OK de novo PIN: usa EditPin/EditPin2 (PIN e confirmação) e volta ao painel de serviço (PanelService). OBS-COD (campos); gravação do PIN: INFERIDO. |  |  |  |
| `ButtonOldPinClick` | 0x0050886C | 124 | DERIVADO | Botão Cancelar do painel de novo PIN: volta ao painel de serviço. OBS-COD (campos). |  |  |  |
| `ButtonResetPinClick` | 0x005075B4 | 332 | INFERIDO | Botão "Reset" do PIN: deriva chave por hash (CryptCreateHash/CryptDeriveKey/CryptEncrypt) do PIN digitado em EditInsPin (propósito: validar/limpar o PIN de serviço; INFERIDO). Descrição da função apenas. APIs: OBS-COD. |  |  | CryptAcquireContextW, CryptCreateHash, CryptDeriveKey, CryptDestroyHash |
| `CheckDisableGasClick` | 0x005085CC | 111 | INFERIDO | Caixa "Inibir passagem a gás": marca que a ECU deve ficar em gasolina; habilita campo "partidas a gás permitidas" (EditPartenzeEmergenza) e mostra efetuadas. Escreve na ECU: PERIGOSO (impede o uso do gás). OBS-COD (campos); efeito INFERIDO. |  |  |  |
| `CheckEnableServiceClick` (sem evento no DFM) | 0x0050843C | 95 | DERIVADO | Caixa "Habilitar gestão de revisões (tagliandi)": liga o campo "horas a gás restantes até a revisão" (EditCurrHours) e o memo de dados do instalador. OBS-COD (campos). |  |  |  |
| `CheckServicePinClick` (sem evento no DFM) | 0x0050863C | 227 | DERIVADO | Caixa "Habilitar proteção de dados com PIN": mostra o botão "Modificar PIN" e o campo de PIN. OBS-COD (campos). |  |  |  |
| `EditDataChange` (sem evento no DFM) | 0x00508854 | 23 | INFERIDO | Rotina publicada sem evento ligado no DFM (chamada pelo código): corpo de 23 bytes sem fatos diretos (chama 0 funções sem nome). |  |  |  |
| `EditInsPinChange` | 0x00507700 | 208 | INFERIDO | Ao digitar o PIN: calcula o hash (CryptoAPI) e compara; quando confere, libera os painéis de dados do instalador. OBS-COD (APIs); comparação: INFERIDO. |  |  | CryptAcquireContextW, CryptCreateHash, CryptDeriveKey, CryptDestroyHash |
| `EditPin2Change` (sem evento no DFM) | 0x00507850 | 127 | INFERIDO | Rotina publicada sem evento ligado no DFM (chamada pelo código): mexe em ButtonNewPin, EditPin2. |  |  |  |
| `EditPinChange` (sem evento no DFM) | 0x005077D0 | 127 | INFERIDO | Rotina publicada sem evento ligado no DFM (chamada pelo código): mexe em EditPin, EditPin2. |  |  |  |
| `EditPinKeyPress` | 0x00508AA0 | 32 | INFERIDO | Ao digitar (filtro de teclas) em EditInsPin (+2 componentes): corpo de 32 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `EditStandardAKeyPress` (sem evento no DFM) | 0x00506E84 | 1 | OBS-COD | Rotina publicada sem evento ligado no DFM (chamada pelo código). Corpo é só RET: nada acontece neste evento (OBS-COD). |  |  |  |
| `EditStandardEnter` | 0x00506B68 | 534 | INFERIDO | Ao entrar no campo numérico (partidas de emergência): prepara o campo para edição (valor de/para a ECU). INFERIDO. |  |  |  |
| `EditStandardExit` | 0x00506D80 | 28 | INFERIDO | Ao sair do campo EditPartenzeEmergenza: corpo de 28 bytes sem fatos diretos (chama 2 funções sem nome). |  |  |  |
| `EditStandardKeyPress` | 0x00506D9C | 231 | INFERIDO | Ao digitar (filtro de teclas) em EditPartenzeEmergenza: corpo de 231 bytes sem fatos diretos (chama 7 funções sem nome). |  |  | GetTickCount, Sleep |
| `FormClose` | 0x00508D28 | 1494 | INFERIDO | Ao fechar: criptografa os dados do instalador (MemoData) com CryptoAPI (hash+derivação de chave+CryptEncrypt, CryptGenRandom) e grava na ECU. Escreve na ECU. OBS-COD (APIs); destino INFERIDO. |  | CryptAcquireContextW, CryptCreateHash, CryptDeriveKey, CryptDestroyHash, CryptDestroyKey, CryptEncrypt |  |
| `FormKeyPress` | 0x00507544 | 112 | INFERIDO | Ao digitar (filtro de teclas) em FormService «Service»: corpo de 112 bytes sem fatos diretos (chama 0 funções sem nome). |  |  |  |
| `MemoDataKeyPress` | 0x00508AC0 | 58 | INFERIDO | Ao digitar (filtro de teclas) em MemoData: mexe em MemoData. |  |  |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `ButtonAzzeraNumeroTagliandiEffettuatiClick` (0x00508B44):
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+11 subfunções sem fatos diretos ou fora da lista; telas.json)
- `ButtonResetPinClick` (0x005075B4):
  - sub_42B834 (160 B): chama TAebProtocol::IsConnect,TAebProtocol::SendCommand
  - sub_43338C (228 B): chama TAebVector::SetEcuData
  - sub_5078D8 (1810 B): APIs CryptAcquireContextW,CryptCreateHash,CryptDeriveKey,CryptDestroyHash
  -   sub_438204 (205 B): APIs GetTickCount,Sleep; chama TAebProtocol::Connect,TAebProtocol::DisableErrorHandling,TAebProtocol::Disconnect,TAebProtocol::EnableErrorHandling
  -     sub_4382D4 (119 B): APIs GetTickCount,Sleep; chama TAebNumber::GetEcuData
  - (+8 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckDisableGasClick` (0x005085CC):
  - sub_433280 (132 B): chama TAebVector::SetEcuData
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `CheckEnableServiceClick` (0x0050843C):
  - sub_433280 (132 B): chama TAebVector::SetEcuData
  - (+0 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditInsPinChange` (0x00507700):
  - sub_5078D8 (1810 B): APIs CryptAcquireContextW,CryptCreateHash,CryptDeriveKey,CryptDestroyHash
  - (+8 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditStandardEnter` (0x00506B68):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `EditStandardKeyPress` (0x00506D9C):
  - sub_42B834 (160 B): chama TAebProtocol::IsConnect,TAebProtocol::SendCommand
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_438204 (205 B): APIs GetTickCount,Sleep; chama TAebProtocol::Connect,TAebProtocol::DisableErrorHandling,TAebProtocol::Disconnect,TAebProtocol::EnableErrorHandling
  -   sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -   sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  -   sub_4372F2 (2254 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_4382D4 (119 B): APIs GetTickCount,Sleep; chama TAebNumber::GetEcuData
  - (+20 subfunções sem fatos diretos ou fora da lista; telas.json)
- `FormClose` (0x00508D28):
  - sub_433280 (132 B): chama TAebVector::SetEcuData
  - sub_435C6C (1903 B): chama TAebVector::GetEcuData
  - sub_43734C (2164 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+8 subfunções sem fatos diretos ou fora da lista; telas.json)
