# TFormService (recurso `TFORMSERVICE`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

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
| `ButtonAzzeraNumeroTagliandiEffettuatiClick` | 0x00508B44 | 483 | INFERIDO | toca LabelNumTagliandiEffettuatiData, LabelNumTagliandiEffettuatiService |  |  |  |
| `ButtonExitClick` | 0x005078D0 | 6 | INFERIDO | sem fatos diretos extraíveis (6 bytes; chama 1 funções sem nome) |  |  |  |
| `ButtonModifyPinClick` (sem evento no DFM) | 0x00508720 | 305 | INFERIDO | toca ButtonExit, EditInsPin, EditPin, EditPin2, PanelData, PanelNewPin, PanelService |  |  |  |
| `ButtonNewPinClick` | 0x005088E8 | 438 | INFERIDO | toca ButtonExit, EditInsPin, EditPin, EditPin2, PanelNewPin, PanelService |  |  |  |
| `ButtonOldPinClick` | 0x0050886C | 124 | INFERIDO | toca ButtonExit, EditPin, EditPin2, PanelNewPin, PanelService |  |  |  |
| `ButtonResetPinClick` | 0x005075B4 | 332 | INFERIDO | toca EditInsPin |  |  | CryptAcquireContextW, CryptCreateHash, CryptDeriveKey, CryptDestroyHash |
| `CheckDisableGasClick` | 0x005085CC | 111 | INFERIDO | toca CheckDisableGas, EditPartenzeEmergenza, LabelPartenzeGasConsentite, LabelPartenzeGasEffettuate |  |  |  |
| `CheckEnableServiceClick` (sem evento no DFM) | 0x0050843C | 95 | INFERIDO | toca CheckEnableService, EditCurrHours, MemoData |  |  |  |
| `CheckServicePinClick` (sem evento no DFM) | 0x0050863C | 227 | INFERIDO | toca ButtonModifyPin, CheckServicePin, EditInsPin |  |  |  |
| `EditDataChange` (sem evento no DFM) | 0x00508854 | 23 | INFERIDO | sem fatos diretos extraíveis (23 bytes; chama 0 funções sem nome) |  |  |  |
| `EditInsPinChange` | 0x00507700 | 208 | INFERIDO | toca EditInsPin |  |  | CryptAcquireContextW, CryptCreateHash, CryptDeriveKey, CryptDestroyHash |
| `EditPin2Change` (sem evento no DFM) | 0x00507850 | 127 | INFERIDO | toca ButtonNewPin, EditPin2 |  |  |  |
| `EditPinChange` (sem evento no DFM) | 0x005077D0 | 127 | INFERIDO | toca EditPin, EditPin2 |  |  |  |
| `EditPinKeyPress` | 0x00508AA0 | 32 | INFERIDO | sem fatos diretos extraíveis (32 bytes; chama 1 funções sem nome) |  |  |  |
| `EditStandardAKeyPress` (sem evento no DFM) | 0x00506E84 | 1 | OBS-COD | Handler vazio: o corpo é só RET (nada acontece neste evento). |  |  |  |
| `EditStandardEnter` | 0x00506B68 | 534 | INFERIDO | sem fatos diretos extraíveis (534 bytes; chama 11 funções sem nome) |  |  |  |
| `EditStandardExit` | 0x00506D80 | 28 | INFERIDO | sem fatos diretos extraíveis (28 bytes; chama 2 funções sem nome) |  |  |  |
| `EditStandardKeyPress` | 0x00506D9C | 231 | INFERIDO | sem fatos diretos extraíveis (231 bytes; chama 7 funções sem nome) |  |  | GetTickCount, Sleep |
| `FormClose` | 0x00508D28 | 1494 | INFERIDO | chama APIs do Windows CryptAcquireContextW, CryptCreateHash, CryptDeriveKey, CryptDestroyHash, CryptDestroyKey, CryptEncrypt; toca MemoData |  | CryptAcquireContextW, CryptCreateHash, CryptDeriveKey, CryptDestroyHash, CryptDestroyKey, CryptEncrypt |  |
| `FormKeyPress` | 0x00507544 | 112 | INFERIDO | sem fatos diretos extraíveis (112 bytes; chama 0 funções sem nome) |  |  |  |
| `MemoDataKeyPress` | 0x00508AC0 | 58 | INFERIDO | toca MemoData |  |  |  |
