# TFormCentriCelle (recurso `TFORMCENTRICELLE`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormCentriCelle`; unidade: `@:CentriCelle`; ancestral: `TForm`; tamanho de instância: 0x44C; componentes no DFM: 37.
- Título: "Centri celle mappa di carburazione" — eng: "Centres of carburation map cells" / por: "Centros das células no mapa de carburação"
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

## Controles com evento/ação (3)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| FormCentriCelle | TFormCentriCelle | Centri celle mappa di carburazione / Centres of carburation map cells / Centros das células no mapa de carburação |  | OnShow→`FormShow` (0x00413730) |
| ButtonOk | TButton | &OK |  | OnClick→`ButtonOkClick` (0x00413524) |
| ButtonAnnulla | TButton | &Annulla / &Cancel / &Cancelar |  | OnExit→`ButtonAnnullaExit` (0x00413724) |

## Handlers (3): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos (chaves i18n) | APIs | Chama |
|---|---|---:|---|---|---|---|---|
| `ButtonAnnullaExit` | 0x00413724 | 12 | INFERIDO | Handler `ButtonAnnullaExit`; sem fatos extraíveis automaticamente (12 bytes) |  |  |  |
| `ButtonOkClick` | 0x00413524 | 509 | INFERIDO | Handler `ButtonOkClick`; toca campos/controles: EditRifGiri0, NumEditRifMap0 |  |  |  |
| `FormShow` | 0x00413730 | 100 | INFERIDO | Handler `FormShow`; sem fatos extraíveis automaticamente (100 bytes) |  |  |  |
