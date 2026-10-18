# TStrategiaTempiMortiUI (recurso `TSTRATEGIATEMPIMORTIUI`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

**Papel:** frame (DERIVADO). Edição dos tempos mortos de injetor gás/gasolina A..I com setas.

- Instância DFM: `StrategiaTempiMortiUI`; unidade: `@:StrategiaTempiMorti_UI`; ancestral: `TFrame`; tamanho de instância: 0x4CC; componentes no DFM: 105.
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

## Controles com evento/ação (20)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| UpDownPetrolA | TUpDown |  |  | OnClick→`UpDownPetrolAClick` (0x00526114) |
| UpDownGasA | TUpDown |  |  | OnClick→`UpDownGasAClick` (0x00526154) |
| UpDownGasB | TUpDown |  |  | OnClick→`UpDownGasAClick` (0x00526154) |
| UpDownPetrolB | TUpDown |  |  | OnClick→`UpDownPetrolAClick` (0x00526114) |
| UpDownPetrolC | TUpDown |  |  | OnClick→`UpDownPetrolAClick` (0x00526114) |
| UpDownGasC | TUpDown |  |  | OnClick→`UpDownGasAClick` (0x00526154) |
| UpDownPetrolD | TUpDown |  |  | OnClick→`UpDownPetrolAClick` (0x00526114) |
| UpDownGasD | TUpDown |  |  | OnClick→`UpDownGasAClick` (0x00526154) |
| UpDownPetrolI | TUpDown |  |  | OnClick→`UpDownPetrolAClick` (0x00526114) |
| UpDownGasI | TUpDown |  |  | OnClick→`UpDownGasAClick` (0x00526154) |
| UpDownPetrolE | TUpDown |  |  | OnClick→`UpDownPetrolAClick` (0x00526114) |
| UpDownPetrolF | TUpDown |  |  | OnClick→`UpDownPetrolAClick` (0x00526114) |
| UpDownPetrolG | TUpDown |  |  | OnClick→`UpDownPetrolAClick` (0x00526114) |
| UpDownPetrolH | TUpDown |  |  | OnClick→`UpDownPetrolAClick` (0x00526114) |
| UpDownPetrolL | TUpDown |  |  | OnClick→`UpDownPetrolAClick` (0x00526114) |
| UpDownGasE | TUpDown |  |  | OnClick→`UpDownGasAClick` (0x00526154) |
| UpDownGasF | TUpDown |  |  | OnClick→`UpDownGasAClick` (0x00526154) |
| UpDownGasG | TUpDown |  |  | OnClick→`UpDownGasAClick` (0x00526154) |
| UpDownGasH | TUpDown |  |  | OnClick→`UpDownGasAClick` (0x00526154) |
| UpDownGasL | TUpDown |  |  | OnClick→`UpDownGasAClick` (0x00526154) |

## Handlers (2): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `UpDownGasAClick` | 0x00526154 | 64 | INFERIDO | Ao clicar em UpDownGasA (+9 componentes): corpo de 64 bytes sem fatos diretos (chama 4 funções sem nome). |  |  |  |
| `UpDownPetrolAClick` | 0x00526114 | 64 | INFERIDO | Ao clicar em UpDownPetrolA (+9 componentes): corpo de 64 bytes sem fatos diretos (chama 4 funções sem nome). |  |  |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `UpDownGasAClick` (0x00526154):
  - sub_523898 (28 B): chama TAebVector::GetInt
  - sub_5238B4 (39 B): chama TAebVector::SetEcuData
  - (+0 subfunções sem fatos diretos ou fora da lista; telas.json)
- `UpDownPetrolAClick` (0x00526114):
  - sub_523854 (28 B): chama TAebVector::GetInt
  - sub_523870 (39 B): chama TAebVector::SetEcuData
  - (+0 subfunções sem fatos diretos ou fora da lista; telas.json)
