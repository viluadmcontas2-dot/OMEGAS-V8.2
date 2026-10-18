# TFormFile (recurso `TFORMFILE`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormFile`; unidade: `@:File`; ancestral: `TForm`; tamanho de instância: 0x454; componentes no DFM: 12.
- Título: "FormFile"
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `TFormMain.ActionArchivioExecute` (0x004FF6C4), `TFormMain.ActionSalvaConfExecute` (0x005000CC), `TFormMain.ActionApriConfExecute` (0x00500314)

## Controles com evento/ação (6)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| FormFile | TFormFile | FormFile |  | OnActivate→`FormActivate` (0x004D9968); OnClose→`FormClose` (0x004D9DD8) |
| ButtonOk | TButton | &OK |  | OnClick→`ButtonOkClick` (0x004D9F30) |
| EditName | TEdit |  |  | OnKeyDown→`EditNameKeyDown` (0x004D9F14) |
| ButtonAggiorna | TButton | A&ggiorna |  | OnClick→`ButtonAggiornaClick` (0x004DE1A4) |
| TreeViewFile | TTreeView |  |  | OnChange→`TreeViewFileChange` (0x004DDBC0); OnClick→`TreeViewFileClick` (0x004DDC04); OnCompare→`TreeViewFileCompare` (0x004DDAE0); OnDblClick→`TreeViewFileDblClick` (0x004DDBC8); OnDragDrop→`TreeViewFileDragDrop` (0x004DE1A8); OnKeyDown→`TreeViewFileKeyDown` (0x004DE120) |
| ButtonAnnulla | TButton | &Annulla / &Cancel / &Cancelar |  |  |

## Handlers (11): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `ButtonAggiornaClick` | 0x004DE1A4 | 1 | OBS-COD | Ao clicar em ButtonAggiorna «A&ggiorna». Corpo é só RET: nada acontece neste evento (OBS-COD). |  |  |  |
| `ButtonOkClick` | 0x004D9F30 | 7251 | INFERIDO | Ao clicar em ButtonOk «&OK !»: chama APIs Sleep, SendMessageW; chama TAebProtocol::IsConnect, TFormAttesa.Create, TFormPdf.Create; mexe em ButtonAnnulla, EditName, EditPath. |  | Sleep, SendMessageW | DeleteFileW, FindResourceW, GetTempFileNameW, GetTempPathW |
| `EditNameKeyDown` | 0x004D9F14 | 27 | INFERIDO | Ao pressionar tecla em EditName: chama TFormFile.ButtonOkClick. |  |  |  |
| `FormActivate` | 0x004D9968 | 1127 | INFERIDO | Ao ativar o formulário FormFile «FormFile»: mexe em ButtonOk, EditName, EditPath, TreeViewFile. |  |  |  |
| `FormClose` | 0x004D9DD8 | 313 | INFERIDO | Ao fechar o formulário FormFile «FormFile»: mexe em TreeViewFile. |  |  |  |
| `TreeViewFileChange` | 0x004DDBC0 | 6 | INFERIDO | Quando muda o valor de TreeViewFile: chama TFormFile.TreeViewFileClick. |  |  |  |
| `TreeViewFileClick` | 0x004DDC04 | 1307 | INFERIDO | Ao clicar em TreeViewFile: mexe em EditName, EditPath, TreeViewFile. |  |  |  |
| `TreeViewFileCompare` | 0x004DDAE0 | 224 | INFERIDO | Evento OnCompare de TreeViewFile: corpo de 224 bytes sem fatos diretos (chama 5 funções sem nome). |  |  |  |
| `TreeViewFileDblClick` | 0x004DDBC8 | 59 | INFERIDO | Ao dar duplo clique em TreeViewFile: chama TFormFile.ButtonOkClick, TFormFile.TreeViewFileClick; mexe em TreeViewFile. |  |  |  |
| `TreeViewFileDragDrop` | 0x004DE1A8 | 7 | INFERIDO | Evento OnDragDrop de TreeViewFile: corpo de 7 bytes sem fatos diretos (chama 0 funções sem nome). |  |  |  |
| `TreeViewFileKeyDown` | 0x004DE120 | 132 | INFERIDO | Ao pressionar tecla em TreeViewFile: chama TFormFile.TreeViewFileDblClick; mexe em TreeViewFile. |  |  |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `ButtonOkClick` (0x004D9F30):
  - sub_4315B0 (316 B): APIs PostMessageW; chama TAebVector::SetEcuData
  - sub_4316EC (75 B): chama TAebVector::SetEcuData
  - sub_439E94 (288664 B): APIs Sleep,SendMessageW; chama TAebMatrix::GetEcuData,TAebMatrix::SetColumnDimension,TAebMatrix::SetEcuData,TAebNumber::GetEcuData
  - sub_4FBA40 (933 B): APIs Sleep; chama TAebProtocol::SendCommand,TAebVector::GetEcuData
  -   sub_42C1E8 (453 B): APIs DeleteFileW,GetTempFileNameW,GetTempPathW
  -   sub_42C4B4 (564 B): APIs DeleteFileW
  -   sub_42C708 (647 B): APIs DeleteFileW,FindResourceW,GetTempFileNameW,GetTempPathW
  -   sub_42C990 (212 B): APIs DeleteFileW
  -   sub_438204 (205 B): APIs GetTickCount,Sleep; chama TAebProtocol::Connect,TAebProtocol::DisableErrorHandling,TAebProtocol::Disconnect,TAebProtocol::EnableErrorHandling
  -   sub_43834C (4108 B): APIs FindResourceW,LoadResource,LockResource,SizeofResource
  -   sub_439358 (2592 B): chama TAebMatrix::SetDefValue,TAebVector::SetDefValue
  -   sub_439DDC (184 B): APIs SendMessageW
  - (+49 subfunções sem fatos diretos ou fora da lista; telas.json)
- `FormActivate` (0x004D9968):
  - (+3 subfunções sem fatos diretos ou fora da lista; telas.json)
