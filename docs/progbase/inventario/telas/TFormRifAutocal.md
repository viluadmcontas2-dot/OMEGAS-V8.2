# TFormRifAutocal (recurso `TFORMRIFAUTOCAL`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormRifAutocal`; unidade: `@:CentriCelleAuto`; ancestral: `TForm`; tamanho de instância: 0x514; componentes no DFM: 83.
- Título: "Riferimenti per l'autocalibrazione" — eng: "References for autocalibration" / por: "None"
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

## Controles com evento/ação (2)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| ButtonOk | TButton | &OK |  | OnClick→`ButtonOkClick` (0x0051B910) |
| ButtonAnnulla | TButton | &Annulla / &Cancel / &Cancelar |  |  |

## Handlers (1): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos (chaves i18n) | APIs | Chama |
|---|---|---:|---|---|---|---|---|
| `ButtonOkClick` | 0x0051B910 | 1067 | INFERIDO | Handler `ButtonOkClick`; chama: TAebNumber::GetDouble, TAebNumber::SetDouble, TAebVector::GetDouble, TAebVector::SetDouble; toca campos/controles: EditKMax, EditKMin, EditRifMap0, TInjBox |  |  | TAebNumber::GetDouble, TAebNumber::SetDouble, TAebVector::GetDouble, TAebVector::SetDouble |
