# TFormAggKey (recurso `TFORMAGGKEY`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormAggKey`; unidade: `@:AggKey`; ancestral: `TForm`; tamanho de instância: 0x3CC; componentes no DFM: 7.
- Título: "Upgrade hardware key"
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `0x00414B8C` (0x00414B8C), `0x0041A680` (0x0041A680)

## Controles com evento/ação (3)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| FormAggKey | TFormAggKey | Upgrade hardware key |  | OnShow→`FormShow` (0x0041199C) |
| ButtonUpgrade | TButton | Ricarica ora / Reload now / Recarregar agora |  |  |
| ButtonLater | TButton | Continua a lavorare / Continue to work / Continua a trabalhar |  |  |

## Handlers (1): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `FormShow` | 0x0041199C | 718 | INFERIDO | usa textos MESSBUTTLATER, MESSBUTTOK, MESSCHARGEKEY, MESSCONTACT, MESSEXPIRED, MESSUPDATECODE; chama TTranslatorExt::GetString; toca ButtonLater, ButtonUpgrade, LabelCodice, LabelValidita | MESSBUTTLATER, MESSBUTTOK, MESSCHARGEKEY, MESSCONTACT, MESSEXPIRED, MESSUPDATECODE |  |  |

### Cadeia de subfunções sem nome alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `FormShow` (0x0041199C):
  - sub_40487C (45 B)
