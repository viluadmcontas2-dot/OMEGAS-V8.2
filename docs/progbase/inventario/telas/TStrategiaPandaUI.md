# TStrategiaPandaUI (recurso `TSTRATEGIAPANDAUI`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `StrategiaPandaUI`; unidade: `@:StrategiaPanda_UI`; ancestral: `TFrame`; tamanho de instância: 0x3BC; componentes no DFM: 46.
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

## Controles com evento/ação (3)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| PanelBackground/chk_SP_EN_STRATEGIA_MINIMO | TAebCheckBox | SP_EN_STRATEGIA_MINIMO |  | OnClick→`chk_SP_EN_STRATEGIA_MINIMOClick` (0x00522E08) |
| PanelBackground/chk_SP_EN_STRATEGIA_RIC_CLIMA | TAebCheckBox | SP_EN_STRATEGIA_RIC_CLIMA |  | OnClick→`chk_SP_EN_STRATEGIA_RIC_CLIMAClick` (0x00522E10) |
| PanelBackground/chk_SP_EN_STRATEGIA_EMU_SENS_PRESS_CLIMA | TAebCheckBox | SP_EN_STRATEGIA_EMU_SENS_PRESS_CLIMA |  | OnClick→`chk_SP_EN_STRATEGIA_EMU_SENS_PRESS_CLIMAClick` (0x00522E18) |

## Handlers (3): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `chk_SP_EN_STRATEGIA_EMU_SENS_PRESS_CLIMAClick` | 0x00522E18 | 6 | INFERIDO | sem fatos diretos extraíveis (6 bytes; chama 1 funções sem nome) |  |  |  |
| `chk_SP_EN_STRATEGIA_MINIMOClick` | 0x00522E08 | 6 | INFERIDO | sem fatos diretos extraíveis (6 bytes; chama 1 funções sem nome) |  |  |  |
| `chk_SP_EN_STRATEGIA_RIC_CLIMAClick` | 0x00522E10 | 6 | INFERIDO | sem fatos diretos extraíveis (6 bytes; chama 1 funções sem nome) |  |  |  |

### Cadeia de subfunções sem nome alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `chk_SP_EN_STRATEGIA_EMU_SENS_PRESS_CLIMAClick` (0x00522E18):
  - sub_522E20 (1772 B): chama TValueEditEx::SetVisible; toca chk_SP_EN_STRATEGIA_EMU_SENS_PRESS_CLIMA,chk_SP_EN_STRATEGIA_MINIMO,chk_SP_EN_STRATEGIA_RIC_CLIMA,edit_SP_SogliaAriaCondizionataON
- `chk_SP_EN_STRATEGIA_MINIMOClick` (0x00522E08):
  - sub_522E20 (1772 B): chama TValueEditEx::SetVisible; toca chk_SP_EN_STRATEGIA_EMU_SENS_PRESS_CLIMA,chk_SP_EN_STRATEGIA_MINIMO,chk_SP_EN_STRATEGIA_RIC_CLIMA,edit_SP_SogliaAriaCondizionataON
- `chk_SP_EN_STRATEGIA_RIC_CLIMAClick` (0x00522E10):
  - sub_522E20 (1772 B): chama TValueEditEx::SetVisible; toca chk_SP_EN_STRATEGIA_EMU_SENS_PRESS_CLIMA,chk_SP_EN_STRATEGIA_MINIMO,chk_SP_EN_STRATEGIA_RIC_CLIMA,edit_SP_SogliaAriaCondizionataON
