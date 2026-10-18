# TAutoCalDM (recurso `TAUTOCALDM`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `AutoCalDM`; unidade: `@:AutoCal_DM`; ancestral: `TDataModule`; tamanho de instância: 0x10C; componentes no DFM: 36.
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

## Controles com evento/ação (4)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| AUTO_CAL_ENABLE | TAebNumber |  |  | OnGetData→`AUTO_CAL_ENABLEGetData` (0x00512B8C) |
| VECT_AUTOCAL_U8_2 | TAebNumber |  |  | OnGetData→`VECT_AUTOCAL_U8_2GetData` (0x00512B90) |
| VECT_AUTOCAL_U8_0 | TAebNumber |  |  | OnGetData→`AUTO_CAL_ENABLEGetData` (0x00512B8C) |
| NUM_ATUOMATCH_EXECUTED | TAebNumber |  |  | OnGetData→`AUTO_CAL_ENABLEGetData` (0x00512B8C) |

## Handlers (2): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos (chaves i18n) | APIs | Chama |
|---|---|---:|---|---|---|---|---|
| `AUTO_CAL_ENABLEGetData` | 0x00512B8C | 1 | INFERIDO | Handler `AUTO_CAL_ENABLEGetData`; sem fatos extraíveis automaticamente (1 bytes) |  |  |  |
| `VECT_AUTOCAL_U8_2GetData` | 0x00512B90 | 1 | INFERIDO | Handler `VECT_AUTOCAL_U8_2GetData`; sem fatos extraíveis automaticamente (1 bytes) |  |  |  |
