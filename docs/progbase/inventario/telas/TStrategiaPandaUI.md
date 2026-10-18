# TStrategiaPandaUI (recurso `TSTRATEGIAPANDAUI`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

**Papel:** frame (DERIVADO). Caixas de habilitação das estratégias Panda (mínimo, reconhecimento do clima, emulação do sensor de pressão) e soglias.

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
| `chk_SP_EN_STRATEGIA_EMU_SENS_PRESS_CLIMAClick` | 0x00522E18 | 6 | INFERIDO | Ao clicar em chk_SP_EN_STRATEGIA_EMU_SENS_PRESS_CLIMA «SP_EN_STRATEGIA_EMU_SENS_PRESS_CLIMA»: corpo de 6 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `chk_SP_EN_STRATEGIA_MINIMOClick` | 0x00522E08 | 6 | INFERIDO | Ao clicar em chk_SP_EN_STRATEGIA_MINIMO «SP_EN_STRATEGIA_MINIMO»: corpo de 6 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `chk_SP_EN_STRATEGIA_RIC_CLIMAClick` | 0x00522E10 | 6 | INFERIDO | Ao clicar em chk_SP_EN_STRATEGIA_RIC_CLIMA «SP_EN_STRATEGIA_RIC_CLIMA»: corpo de 6 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `chk_SP_EN_STRATEGIA_EMU_SENS_PRESS_CLIMAClick` (0x00522E18):
  - sub_522E20 (1772 B): chama TValueEditEx::SetVisible
  - (+0 subfunções sem fatos diretos ou fora da lista; telas.json)
- `chk_SP_EN_STRATEGIA_MINIMOClick` (0x00522E08):
  - sub_522E20 (1772 B): chama TValueEditEx::SetVisible
  - (+0 subfunções sem fatos diretos ou fora da lista; telas.json)
- `chk_SP_EN_STRATEGIA_RIC_CLIMAClick` (0x00522E10):
  - sub_522E20 (1772 B): chama TValueEditEx::SetVisible
  - (+0 subfunções sem fatos diretos ou fora da lista; telas.json)
