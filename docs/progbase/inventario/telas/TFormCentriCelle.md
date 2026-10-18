# TFormCentriCelle (recurso `TFORMCENTRICELLE`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormCentriCelle`; unidade: `@:CentriCelle`; ancestral: `TForm`; tamanho de instância: 0x44C; componentes no DFM: 37.
- Título: "Centri celle mappa di carburazione" — eng: "Centres of carburation map cells" / por: "Centros das células no mapa de carburação"
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `0x004B4594` (0x004B4594), `TFormConfig.DrawGridEmissioniMultipointDblClick` (0x004C33E0)

## Controles com evento/ação (3)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| FormCentriCelle | TFormCentriCelle | Centri celle mappa di carburazione / Centres of carburation map cells / Centros das células no mapa de carburação |  | OnShow→`FormShow` (0x00413730) |
| ButtonOk | TButton | &OK |  | OnClick→`ButtonOkClick` (0x00413524) |
| ButtonAnnulla | TButton | &Annulla / &Cancel / &Cancelar |  | OnExit→`ButtonAnnullaExit` (0x00413724) |

## Handlers (3): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `ButtonAnnullaExit` | 0x00413724 | 12 | INFERIDO | Ao sair do campo ButtonAnnulla «&Cancelar»: corpo de 12 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `ButtonOkClick` | 0x00413524 | 509 | DERIVADO | OK: valida e grava os centros das células do mapa de carburação (EditRifGiri*/NumEditRifMap*). OBS-COD (campos). |  |  |  |
| `FormShow` | 0x00413730 | 100 | INFERIDO | Ao exibir o formulário FormCentriCelle «Centros das células no mapa de carburação»: corpo de 100 bytes sem fatos diretos (chama 3 funções sem nome). |  |  |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `ButtonOkClick` (0x00413524):
  - sub_42F768 (92 B): chama TAebVector::SetEcuData
  - sub_42FBAC (142 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+12 subfunções sem fatos diretos ou fora da lista; telas.json)
