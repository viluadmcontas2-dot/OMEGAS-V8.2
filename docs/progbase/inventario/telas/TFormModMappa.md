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
| `EditValoreKeyPress` | 0x00969E80 | 751 | INFERIDO | sem fatos diretos extraíveis (751 bytes; chama 8 funções sem nome) |  |  |  |
| `FormClose` | 0x00969D68 | 277 | INFERIDO | sem fatos diretos extraíveis (277 bytes; chama 6 funções sem nome) |  |  |  |
| `GetDouble` (sem evento no DFM) | 0x0096A238 | 135 | INFERIDO | sem fatos diretos extraíveis (135 bytes; chama 5 funções sem nome) |  |  |  |
| `GetInt` (sem evento no DFM) | 0x0096A170 | 101 | INFERIDO | sem fatos diretos extraíveis (101 bytes; chama 5 funções sem nome) |  |  |  |
| `SetDouble` (sem evento no DFM) | 0x0096A2C8 | 95 | INFERIDO | sem fatos diretos extraíveis (95 bytes; chama 4 funções sem nome) |  |  |  |
| `SetInt` (sem evento no DFM) | 0x0096A1D8 | 93 | INFERIDO | sem fatos diretos extraíveis (93 bytes; chama 4 funções sem nome) |  |  |  |
