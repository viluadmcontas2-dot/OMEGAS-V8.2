# TFormService (recurso `TFORMSERVICE`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormService`; unidade: `@:Service`; ancestral: `TForm`; tamanho de instância: 0x494; componentes no DFM: 36.
- Título: "Gestione tagliandi" — eng: "Service" / por: "Service"
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

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

| Handler | Endereço | Bytes | Evidência | O que faz | Textos (chaves i18n) | APIs | Chama |
|---|---|---:|---|---|---|---|---|
| `ButtonAzzeraNumeroTagliandiEffettuatiClick` | 0x00508B44 | 483 | INFERIDO | Handler `ButtonAzzeraNumeroTagliandiEffettuatiClick`; toca campos/controles: LabelNumTagliandiEffettuatiData, LabelNumTagliandiEffettuatiService |  |  |  |
| `ButtonExitClick` | 0x005078D0 | 6 | INFERIDO | Handler `ButtonExitClick`; sem fatos extraíveis automaticamente (6 bytes) |  |  |  |
| `ButtonModifyPinClick` (sem evento no DFM) | 0x00508720 | 305 | INFERIDO | Handler `ButtonModifyPinClick`; toca campos/controles: ButtonExit, EditInsPin, EditPin, EditPin2, PanelData, PanelNewPin, PanelService |  |  |  |
| `ButtonNewPinClick` | 0x005088E8 | 438 | INFERIDO | Handler `ButtonNewPinClick`; toca campos/controles: ButtonExit, EditInsPin, EditPin, EditPin2, PanelNewPin, PanelService |  |  |  |
| `ButtonOldPinClick` | 0x0050886C | 124 | INFERIDO | Handler `ButtonOldPinClick`; toca campos/controles: ButtonExit, EditPin, EditPin2, PanelNewPin, PanelService |  |  |  |
| `ButtonResetPinClick` | 0x005075B4 | 332 | INFERIDO | Handler `ButtonResetPinClick`; chama APIs do Windows: ADVAPI32.DLL!CryptAcquireContextW, ADVAPI32.DLL!CryptCreateHash, ADVAPI32.DLL!CryptDeriveKey, ADVAPI32.DLL!CryptDestroyHash, ADVAPI32.DLL!CryptDestroyKey, ADVAPI32.DLL!CryptEncrypt; toca campos/controles: EditInsPin |  | ADVAPI32.DLL!CryptAcquireContextW, ADVAPI32.DLL!CryptCreateHash, ADVAPI32.DLL!CryptDeriveKey, ADVAPI32.DLL!CryptDestroyHash, ADVAPI32.DLL!CryptDestroyKey |  |
| `CheckDisableGasClick` | 0x005085CC | 111 | INFERIDO | Handler `CheckDisableGasClick`; toca campos/controles: CheckDisableGas, EditPartenzeEmergenza, LabelPartenzeGasConsentite, LabelPartenzeGasEffettuate |  |  |  |
| `CheckEnableServiceClick` (sem evento no DFM) | 0x0050843C | 95 | INFERIDO | Handler `CheckEnableServiceClick`; toca campos/controles: CheckEnableService, EditCurrHours, MemoData |  |  |  |
| `CheckServicePinClick` (sem evento no DFM) | 0x0050863C | 227 | INFERIDO | Handler `CheckServicePinClick`; toca campos/controles: ButtonModifyPin, CheckServicePin, EditInsPin |  |  |  |
| `EditDataChange` (sem evento no DFM) | 0x00508854 | 23 | INFERIDO | Handler `EditDataChange`; sem fatos extraíveis automaticamente (23 bytes) |  |  |  |
| `EditInsPinChange` | 0x00507700 | 208 | INFERIDO | Handler `EditInsPinChange`; chama APIs do Windows: ADVAPI32.DLL!CryptAcquireContextW, ADVAPI32.DLL!CryptCreateHash, ADVAPI32.DLL!CryptDeriveKey, ADVAPI32.DLL!CryptDestroyHash, ADVAPI32.DLL!CryptDestroyKey, ADVAPI32.DLL!CryptEncrypt; toca campos/controles: EditInsPin |  | ADVAPI32.DLL!CryptAcquireContextW, ADVAPI32.DLL!CryptCreateHash, ADVAPI32.DLL!CryptDeriveKey, ADVAPI32.DLL!CryptDestroyHash, ADVAPI32.DLL!CryptDestroyKey |  |
| `EditPin2Change` (sem evento no DFM) | 0x00507850 | 127 | INFERIDO | Handler `EditPin2Change`; toca campos/controles: ButtonNewPin, EditPin2 |  |  |  |
| `EditPinChange` (sem evento no DFM) | 0x005077D0 | 127 | INFERIDO | Handler `EditPinChange`; toca campos/controles: EditPin, EditPin2 |  |  |  |
| `EditPinKeyPress` | 0x00508AA0 | 32 | INFERIDO | Handler `EditPinKeyPress`; sem fatos extraíveis automaticamente (32 bytes) |  |  |  |
| `EditStandardAKeyPress` (sem evento no DFM) | 0x00506E84 | 1 | INFERIDO | Handler `EditStandardAKeyPress`; sem fatos extraíveis automaticamente (1 bytes) |  |  |  |
| `EditStandardEnter` | 0x00506B68 | 534 | INFERIDO | Handler `EditStandardEnter`; sem fatos extraíveis automaticamente (534 bytes) |  |  |  |
| `EditStandardExit` | 0x00506D80 | 28 | INFERIDO | Handler `EditStandardExit`; sem fatos extraíveis automaticamente (28 bytes) |  |  |  |
| `EditStandardKeyPress` | 0x00506D9C | 231 | INFERIDO | Handler `EditStandardKeyPress`; chama APIs do Windows: KERNEL32.DLL!GetTickCount, KERNEL32.DLL!Sleep |  | KERNEL32.DLL!GetTickCount, KERNEL32.DLL!Sleep |  |
| `FormClose` | 0x00508D28 | 1494 | INFERIDO | Handler `FormClose`; chama APIs do Windows: ADVAPI32.DLL!CryptAcquireContextW, ADVAPI32.DLL!CryptCreateHash, ADVAPI32.DLL!CryptDeriveKey, ADVAPI32.DLL!CryptDestroyHash, ADVAPI32.DLL!CryptDestroyKey, ADVAPI32.DLL!CryptEncrypt; chama: thunk -> ADVAPI32.DLL!CryptAcquireContextW, thunk -> ADVAPI32.DLL!CryptCreateHash, thunk -> ADVAPI32.DLL!CryptDeriveKey, thunk -> ADVAPI32.DLL!CryptDestroyHash, thunk -> ADVAPI32.DLL!CryptDestroyKey, thunk -> ADVAPI32.DLL!CryptEncrypt; toca campos/controles: MemoData |  | ADVAPI32.DLL!CryptAcquireContextW, ADVAPI32.DLL!CryptCreateHash, ADVAPI32.DLL!CryptDeriveKey, ADVAPI32.DLL!CryptDestroyHash, ADVAPI32.DLL!CryptDestroyKey | thunk -> ADVAPI32.DLL!CryptAcquireContextW, thunk -> ADVAPI32.DLL!CryptCreateHash, thunk -> ADVAPI32.DLL!CryptDeriveKey, thunk -> ADVAPI32.DLL!CryptDestroyHash, thunk -> ADVAPI32.DLL!CryptDestroyKey |
| `FormKeyPress` | 0x00507544 | 112 | INFERIDO | Handler `FormKeyPress`; sem fatos extraíveis automaticamente (112 bytes) |  |  |  |
| `MemoDataKeyPress` | 0x00508AC0 | 58 | INFERIDO | Handler `MemoDataKeyPress`; toca campos/controles: MemoData |  |  |  |
