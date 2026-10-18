# TFormCentriCelleK (recurso `TFORMCENTRICELLEK`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

**Papel:** diálogo (DERIVADO). Edita os centros das células do mapa dos coeficientes K.

- Instância DFM: `FormCentriCelleK`; unidade: `@:CentriCelleK`; ancestral: `TForm`; tamanho de instância: 0x48C; componentes no DFM: 53.
- Título: "Centri celle mappa dei coefficienti k" — eng: "Map cell centres of the k coefficients" / por: "Centros das células no mapa dos coeficientes k"
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `TFormConfig.ButtonModificaRifTarClick` (0x004B10C8)

## Controles com evento/ação (3)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| FormCentriCelleK | TFormCentriCelleK | Centri celle mappa dei coefficienti k / Map cell centres of the k coefficients / Centros das células no mapa dos coeficientes k |  | OnShow→`FormShow` (0x00413E58) |
| ButtonOk | TButton | &OK |  | OnClick→`ButtonOkClick` (0x00413BFC); OnExit→`ButtonOkExit` (0x00413E20) |
| ButtonAnnulla | TButton | &Annulla / &Cancel / &Cancelar |  | OnExit→`ButtonAnnullaExit` (0x00413E3C) |

## Handlers (4): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `ButtonAnnullaExit` | 0x00413E3C | 26 | INFERIDO | Ao sair do campo ButtonAnnulla «&Cancelar»: corpo de 26 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `ButtonOkClick` | 0x00413BFC | 545 | DERIVADO | OK: valida e grava os centros das células do mapa dos coeficientes K. OBS-COD (campos). |  |  |  |
| `ButtonOkExit` | 0x00413E20 | 26 | INFERIDO | Ao sair do campo ButtonOk «&OK !»: corpo de 26 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `FormShow` | 0x00413E58 | 113 | INFERIDO | Ao exibir o formulário FormCentriCelleK «Centros das células no mapa dos coeficientes k»: mexe em EditRifGiri0. |  |  |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `ButtonOkClick` (0x00413BFC):
  - sub_42EDB4 (94 B): chama TAebVector::SetEcuData
  - sub_42EE94 (199 B): chama TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+14 subfunções sem fatos diretos ou fora da lista; telas.json)
