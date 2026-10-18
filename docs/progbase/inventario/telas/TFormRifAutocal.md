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
| `ButtonOkClick` | 0x0051B910 | 1067 | DERIVADO | OK: grava os limites K mín/máx e as referências do AutoCal nos vetores (TAebVector/TAebNumber Get/SetDouble; nomes "InjT_*"). Escreve parâmetros na ECU. OBS-COD. |  |  |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `ButtonOkClick` (0x0051B910):
  - (+9 subfunções sem fatos diretos ou fora da lista; telas.json)
