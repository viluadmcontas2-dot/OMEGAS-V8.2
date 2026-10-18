# TFormScarica (recurso `TFORMSCARICA`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormScarica`; unidade: `@:ScaricaFile`; ancestral: `TForm`; tamanho de instância: 0x3D4; componentes no DFM: 9.
- Título: "Programmazione centralina" — eng: "Ecu programming" / por: "Programação da central de comando"
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

## Controles com evento/ação (3)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| ButtonCambia | TButton | &Cambia file / &Change file / &Mudar arquivo |  | OnClick→`ButtonCambiaClick` (0x0050523C) |
| ButtonRiprogr | TButton | &Programma / &Programme / &Programação |  |  |
| ButtonAnnulla | TButton | &Annulla / &Cancel / &Cancelar |  |  |

## Caixas de diálogo (componentes) (1)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) | Filtro |
|---|---|---|---|---|---|
| OpenDialogFile | TOpenDialog |  |  |  |  |

## Handlers (1): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos (chaves i18n) | APIs | Chama |
|---|---|---:|---|---|---|---|---|
| `ButtonCambiaClick` | 0x0050523C | 27 | INFERIDO | Handler `ButtonCambiaClick`; chama APIs do Windows: KERNEL32.DLL!CloseHandle, KERNEL32.DLL!CreateFileW, KERNEL32.DLL!FileTimeToLocalFileTime, KERNEL32.DLL!FileTimeToSystemTime, KERNEL32.DLL!GetFileTime; toca campos/controles: OpenDialogFile |  | KERNEL32.DLL!CloseHandle, KERNEL32.DLL!CreateFileW, KERNEL32.DLL!FileTimeToLocalFileTime, KERNEL32.DLL!FileTimeToSystemTime, KERNEL32.DLL!GetFileTime |  |
