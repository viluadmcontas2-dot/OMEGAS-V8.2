# TFormModMappa (recurso `TFORMMODMAPPA`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormModMappa`; unidade: `@:ModificaMappa`; ancestral: `TForm`; tamanho de instância: 0x3CC; componentes no DFM: 5.
- Título: "Modifica valore"
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

## Controles com evento/ação (4)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| FormModMappa | TFormModMappa | Modifica valore |  | OnClose→`FormClose` (0x00969D68) |
| EditValore | TEdit |  |  | OnKeyPress→`EditValoreKeyPress` (0x00969E80) |
| ButtonOK | TButton | &OK |  |  |
| ButtonAnnulla | TButton | &Annulla / &Cancel / &Cancelar |  |  |

## Handlers (6): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos (chaves i18n) | APIs | Chama |
|---|---|---:|---|---|---|---|---|
| `EditValoreKeyPress` | 0x00969E80 | 751 | INFERIDO | Handler `EditValoreKeyPress`; sem fatos extraíveis automaticamente (751 bytes) |  |  |  |
| `FormClose` | 0x00969D68 | 277 | INFERIDO | Handler `FormClose`; sem fatos extraíveis automaticamente (277 bytes) |  |  |  |
| `GetDouble` (sem evento no DFM) | 0x0096A238 | 135 | INFERIDO | Handler `GetDouble`; sem fatos extraíveis automaticamente (135 bytes) |  |  |  |
| `GetInt` (sem evento no DFM) | 0x0096A170 | 101 | INFERIDO | Handler `GetInt`; sem fatos extraíveis automaticamente (101 bytes) |  |  |  |
| `SetDouble` (sem evento no DFM) | 0x0096A2C8 | 95 | INFERIDO | Handler `SetDouble`; sem fatos extraíveis automaticamente (95 bytes) |  |  |  |
| `SetInt` (sem evento no DFM) | 0x0096A1D8 | 93 | INFERIDO | Handler `SetInt`; sem fatos extraíveis automaticamente (93 bytes) |  |  |  |
