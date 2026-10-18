# TFormModificaMappa (recurso `TFORMMODIFICAMAPPA`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormModificaMappa`; unidade: `@:ModMappa`; ancestral: `TForm`; tamanho de instância: 0x3CC; componentes no DFM: 5.
- Título: "Variazione cella"
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `TFormConfig.Create` (0x00489758), `0x004ECD9C` (0x004ECD9C)

## Controles com evento/ação (4)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| FormModificaMappa | TFormModificaMappa | Variazione cella |  | OnClose→`FormClose` (0x0050EDF8) |
| EditData | TEdit |  |  | OnKeyPress→`EditDataKeyPress` (0x0050EF08) |
| ButtonOK | TButton | &OK |  |  |
| ButtonAnnulla | TButton | &Annulla / &Cancel / &Cancelar |  |  |

## Handlers (2): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `EditDataKeyPress` | 0x0050EF08 | 677 | INFERIDO | Ao digitar (filtro de teclas) em EditData: corpo de 677 bytes sem fatos diretos (chama 8 funções sem nome). |  |  |  |
| `FormClose` | 0x0050EDF8 | 272 | INFERIDO | Ao fechar o formulário FormModificaMappa «Variazione cella»: corpo de 272 bytes sem fatos diretos (chama 6 funções sem nome). |  |  |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `EditDataKeyPress` (0x0050EF08):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
