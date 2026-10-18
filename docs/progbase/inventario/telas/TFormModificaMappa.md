# TFormModificaMappa (recurso `TFORMMODIFICAMAPPA`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormModificaMappa`; unidade: `@:ModMappa`; ancestral: `TForm`; tamanho de instância: 0x3CC; componentes no DFM: 5.
- Título: "Variazione cella"
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

## Controles com evento/ação (4)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| FormModificaMappa | TFormModificaMappa | Variazione cella |  | OnClose→`FormClose` (0x0050EDF8) |
| EditData | TEdit |  |  | OnKeyPress→`EditDataKeyPress` (0x0050EF08) |
| ButtonOK | TButton | &OK |  |  |
| ButtonAnnulla | TButton | &Annulla / &Cancel / &Cancelar |  |  |

## Handlers (2): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos (chaves i18n) | APIs | Chama |
|---|---|---:|---|---|---|---|---|
| `EditDataKeyPress` | 0x0050EF08 | 677 | INFERIDO | Handler `EditDataKeyPress`; sem fatos extraíveis automaticamente (677 bytes) |  |  |  |
| `FormClose` | 0x0050EDF8 | 272 | INFERIDO | Handler `FormClose`; sem fatos extraíveis automaticamente (272 bytes) |  |  |  |
