# TFormRiferimenti (recurso `TFORMRIFERIMENTI`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormRiferimenti`; unidade: `@:RiferimentiMappa`; ancestral: `TForm`; tamanho de instância: 0x3D4; componentes no DFM: 5.
- Título: "Modifica riferimenti mappa"
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `TAebGrid::EditRiferimentiMappa` (0x0093E36C)

## Controles com evento/ação (3)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| FormRiferimenti | TFormRiferimenti | Modifica riferimenti mappa |  | OnCloseQuery→`FormCloseQuery` (0x009744E0) |
| ButtonOk | TButton | &OK |  |  |
| ButtonAnnulla | TButton | &Annulla / &Cancel / &Cancelar |  |  |

## Handlers (1): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `FormCloseQuery` | 0x009744E0 | 1007 | INFERIDO | sem fatos diretos extraíveis (1007 bytes; chama 11 funções sem nome) |  |  |  |

### Cadeia de subfunções sem nome alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `FormCloseQuery` (0x009744E0):
  - sub_40487C (45 B)
  - sub_910F38 (25 B)
  - sub_96BA2C (343 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_9820F0 (31 B)
  -     sub_98211C (33 B)
  -     sub_982150 (25 B)
  -     sub_982174 (25 B)
  -     sub_982198 (25 B)
  -     sub_9821B8 (25 B)
