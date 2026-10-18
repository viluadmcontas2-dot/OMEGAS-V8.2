# TFormScarica (recurso `TFORMSCARICA`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

**Papel:** diálogo (DERIVADO). Janela de programação da ECU: mostra arquivo escolhido (data) e botões Programação/Mudar arquivo.

- Instância DFM: `FormScarica`; unidade: `@:ScaricaFile`; ancestral: `TForm`; tamanho de instância: 0x3D4; componentes no DFM: 9.
- Título: "Programmazione centralina" — eng: "Ecu programming" / por: "Programação da central de comando"
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` (0x004111F8)

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

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `ButtonCambiaClick` | 0x0050523C | 27 | DERIVADO | Botão "Mudar arquivo": abre o OpenDialogFile para escolher outro arquivo de firmware/configuração e mostra data/hora do arquivo (CreateFileW, GetFileTime, FileTimeToSystemTime). OBS-COD (APIs). |  |  | CloseHandle, CreateFileW, FileTimeToLocalFileTime, FileTimeToSystemTime |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `ButtonCambiaClick` (0x0050523C):
  - sub_505AA8 (1907 B): APIs CloseHandle,CreateFileW,FileTimeToLocalFileTime,FileTimeToSystemTime
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
