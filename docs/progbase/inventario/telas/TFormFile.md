# TFormFile (recurso `TFORMFILE`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormFile`; unidade: `@:File`; ancestral: `TForm`; tamanho de instância: 0x454; componentes no DFM: 12.
- Título: "FormFile"
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

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

| Handler | Endereço | Bytes | Evidência | O que faz | Textos (chaves i18n) | APIs | Chama |
|---|---|---:|---|---|---|---|---|
| `ButtonAggiornaClick` | 0x004DE1A4 | 1 | INFERIDO | Handler `ButtonAggiornaClick`; sem fatos extraíveis automaticamente (1 bytes) |  |  |  |
| `ButtonOkClick` | 0x004D9F30 | 7251 | INFERIDO | Handler `ButtonOkClick`; chama APIs do Windows: KERNEL32.DLL!Sleep, USER32.DLL!SendMessageW, KERNEL32.DLL!DeleteFileW, KERNEL32.DLL!FindResourceW, KERNEL32.DLL!GetTempFileNameW, KERNEL32.DLL!GetTempPathW; chama: TAebProtocol::IsConnect, TFormAttesa.vmt[0x38], TFormPdf.vmt[0x38], thunk -> KERNEL32.DLL!Sleep, thunk -> USER32.DLL!SendMessageW; toca campos/controles: ButtonAnnulla, EditName, EditPath |  | KERNEL32.DLL!Sleep, USER32.DLL!SendMessageW, KERNEL32.DLL!DeleteFileW, KERNEL32.DLL!FindResourceW, KERNEL32.DLL!GetTempFileNameW | TAebProtocol::IsConnect, TFormAttesa.vmt[0x38], TFormPdf.vmt[0x38], thunk -> KERNEL32.DLL!Sleep, thunk -> USER32.DLL!SendMessageW |
| `EditNameKeyDown` | 0x004D9F14 | 27 | INFERIDO | Handler `EditNameKeyDown`; chama: TFormFile.ButtonOkClick |  |  | TFormFile.ButtonOkClick |
| `FormActivate` | 0x004D9968 | 1127 | INFERIDO | Handler `FormActivate`; toca campos/controles: ButtonOk, EditName, EditPath, TreeViewFile |  |  |  |
| `FormClose` | 0x004D9DD8 | 313 | INFERIDO | Handler `FormClose`; toca campos/controles: TreeViewFile |  |  |  |
| `TreeViewFileChange` | 0x004DDBC0 | 6 | INFERIDO | Handler `TreeViewFileChange`; chama: TFormFile.TreeViewFileClick |  |  | TFormFile.TreeViewFileClick |
| `TreeViewFileClick` | 0x004DDC04 | 1307 | INFERIDO | Handler `TreeViewFileClick`; toca campos/controles: EditName, EditPath, TreeViewFile |  |  |  |
| `TreeViewFileCompare` | 0x004DDAE0 | 224 | INFERIDO | Handler `TreeViewFileCompare`; sem fatos extraíveis automaticamente (224 bytes) |  |  |  |
| `TreeViewFileDblClick` | 0x004DDBC8 | 59 | INFERIDO | Handler `TreeViewFileDblClick`; chama: TFormFile.ButtonOkClick, TFormFile.TreeViewFileClick; toca campos/controles: TreeViewFile |  |  | TFormFile.ButtonOkClick, TFormFile.TreeViewFileClick |
| `TreeViewFileDragDrop` | 0x004DE1A8 | 7 | INFERIDO | Handler `TreeViewFileDragDrop`; sem fatos extraíveis automaticamente (7 bytes) |  |  |  |
| `TreeViewFileKeyDown` | 0x004DE120 | 132 | INFERIDO | Handler `TreeViewFileKeyDown`; chama: TFormFile.TreeViewFileDblClick; toca campos/controles: TreeViewFile |  |  | TFormFile.TreeViewFileDblClick |
