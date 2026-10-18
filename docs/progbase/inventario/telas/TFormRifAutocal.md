# TFormRifAutocal (recurso `TFORMRIFAUTOCAL`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormRifAutocal`; unidade: `@:CentriCelleAuto`; ancestral: `TForm`; tamanho de instância: 0x514; componentes no DFM: 83.
- Título: "Riferimenti per l'autocalibrazione" — eng: "References for autocalibration" / por: "None"
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` (0x004111F8), `TAutoCalUI.ActionAutoCalRifExecute` (0x005187A0)

## Controles com evento/ação (2)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| ButtonOk | TButton | &OK |  | OnClick→`ButtonOkClick` (0x0051B910) |
| ButtonAnnulla | TButton | &Annulla / &Cancel / &Cancelar |  |  |

## Handlers (1): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `ButtonOkClick` | 0x0051B910 | 1067 | INFERIDO | chama TAebNumber::GetDouble, TAebNumber::SetDouble, TAebVector::GetDouble, TAebVector::SetDouble; toca EditKMax, EditKMin, EditRifMap0, TInjBox |  |  |  |

### Cadeia de subfunções sem nome alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `ButtonOkClick` (0x0051B910):
  - sub_40487C (45 B)
  - sub_96BA2C (343 B)
  -   sub_96BD74 (19 B)
  -   sub_981F2C (447 B)
  -     sub_9820F0 (31 B)
  -     sub_98211C (33 B)
  -     sub_982150 (25 B)
  -     sub_982174 (25 B)
  -     sub_982198 (25 B)
  -     sub_9821B8 (25 B)
