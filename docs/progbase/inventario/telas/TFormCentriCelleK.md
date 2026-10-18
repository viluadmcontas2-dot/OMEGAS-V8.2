# TFormCentriCelleK (recurso `TFORMCENTRICELLEK`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

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
| `ButtonAnnullaExit` | 0x00413E3C | 26 | INFERIDO | sem fatos diretos extraíveis (26 bytes; chama 1 funções sem nome) |  |  |  |
| `ButtonOkClick` | 0x00413BFC | 545 | INFERIDO | toca EditRifGiri0, NumEditRifMap0 |  |  |  |
| `ButtonOkExit` | 0x00413E20 | 26 | INFERIDO | sem fatos diretos extraíveis (26 bytes; chama 1 funções sem nome) |  |  |  |
| `FormShow` | 0x00413E58 | 113 | INFERIDO | toca EditRifGiri0 |  |  |  |
