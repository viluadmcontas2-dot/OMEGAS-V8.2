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
| `ButtonAggiornaClick` | 0x004DE1A4 | 1 | OBS-COD | Handler vazio: o corpo é só RET (nada acontece neste evento). |  |  |  |
| `ButtonOkClick` | 0x004D9F30 | 7251 | INFERIDO | chama APIs do Windows Sleep, SendMessageW; chama TAebProtocol::IsConnect, TFormAttesa.Create, TFormPdf.Create; toca ButtonAnnulla, EditName, EditPath |  | Sleep, SendMessageW | DeleteFileW, FindResourceW, GetTempFileNameW, GetTempPathW |
| `EditNameKeyDown` | 0x004D9F14 | 27 | INFERIDO | chama TFormFile.ButtonOkClick |  |  |  |
| `FormActivate` | 0x004D9968 | 1127 | INFERIDO | toca ButtonOk, EditName, EditPath, TreeViewFile |  |  |  |
| `FormClose` | 0x004D9DD8 | 313 | INFERIDO | toca TreeViewFile |  |  |  |
| `TreeViewFileChange` | 0x004DDBC0 | 6 | INFERIDO | chama TFormFile.TreeViewFileClick |  |  |  |
| `TreeViewFileClick` | 0x004DDC04 | 1307 | INFERIDO | toca EditName, EditPath, TreeViewFile |  |  |  |
| `TreeViewFileCompare` | 0x004DDAE0 | 224 | INFERIDO | sem fatos diretos extraíveis (224 bytes; chama 5 funções sem nome) |  |  |  |
| `TreeViewFileDblClick` | 0x004DDBC8 | 59 | INFERIDO | chama TFormFile.ButtonOkClick, TFormFile.TreeViewFileClick; toca TreeViewFile |  |  |  |
| `TreeViewFileDragDrop` | 0x004DE1A8 | 7 | INFERIDO | sem fatos diretos extraíveis (7 bytes; chama 0 funções sem nome) |  |  |  |
| `TreeViewFileKeyDown` | 0x004DE120 | 132 | INFERIDO | chama TFormFile.TreeViewFileDblClick; toca TreeViewFile |  |  |  |
