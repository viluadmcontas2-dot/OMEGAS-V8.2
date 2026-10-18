# TFormModMappa (recurso `TFORMMODMAPPA`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormModMappa`; unidade: `@:ModificaMappa`; ancestral: `TForm`; tamanho de instância: 0x3CC; componentes no DFM: 5.
- Título: "Modifica valore"
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `TAebGrid::EditValoriMappa` (0x0093DC84), `TAebVectorItems::EditValoriMappa` (0x009609F0)

## Controles com evento/ação (4)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| FormModMappa | TFormModMappa | Modifica valore |  | OnClose→`FormClose` (0x00969D68) |
| EditValore | TEdit |  |  | OnKeyPress→`EditValoreKeyPress` (0x00969E80) |
| ButtonOK | TButton | &OK |  |  |
| ButtonAnnulla | TButton | &Annulla / &Cancel / &Cancelar |  |  |

## Handlers (6): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `EditValoreKeyPress` | 0x00969E80 | 751 | INFERIDO | Ao digitar (filtro de teclas) em EditValore: corpo de 751 bytes sem fatos diretos (chama 8 funções sem nome). |  |  |  |
| `FormClose` | 0x00969D68 | 277 | INFERIDO | Ao fechar o formulário FormModMappa «Modifica valore»: corpo de 277 bytes sem fatos diretos (chama 6 funções sem nome). |  |  |  |
| `GetDouble` (sem evento no DFM) | 0x0096A238 | 135 | INFERIDO | Rotina publicada sem evento ligado no DFM (chamada pelo código): corpo de 135 bytes sem fatos diretos (chama 5 funções sem nome). |  |  |  |
| `GetInt` (sem evento no DFM) | 0x0096A170 | 101 | INFERIDO | Rotina publicada sem evento ligado no DFM (chamada pelo código): corpo de 101 bytes sem fatos diretos (chama 5 funções sem nome). |  |  |  |
| `SetDouble` (sem evento no DFM) | 0x0096A2C8 | 95 | INFERIDO | Rotina publicada sem evento ligado no DFM (chamada pelo código): corpo de 95 bytes sem fatos diretos (chama 4 funções sem nome). |  |  |  |
| `SetInt` (sem evento no DFM) | 0x0096A1D8 | 93 | INFERIDO | Rotina publicada sem evento ligado no DFM (chamada pelo código): corpo de 93 bytes sem fatos diretos (chama 4 funções sem nome). |  |  |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `EditValoreKeyPress` (0x00969E80):
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `SetInt` (0x0096A1D8):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
