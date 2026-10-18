# TAutoCalDM (recurso `TAUTOCALDM`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

**Papel:** módulo de dados (OBS-COD). Vetores/escalares do AutoCal ligados a controles (AUTO_CAL_ENABLE, VECT_AUTOCAL_*, NUM_AUTOMATCH_*).

- Instância DFM: `AutoCalDM`; unidade: `@:AutoCal_DM`; ancestral: `TDataModule`; tamanho de instância: 0x10C; componentes no DFM: 36.
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` (0x004111F8)

## Controles com evento/ação (4)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| AUTO_CAL_ENABLE | TAebNumber |  |  | OnGetData→`AUTO_CAL_ENABLEGetData` (0x00512B8C) |
| VECT_AUTOCAL_U8_2 | TAebNumber |  |  | OnGetData→`VECT_AUTOCAL_U8_2GetData` (0x00512B90) |
| VECT_AUTOCAL_U8_0 | TAebNumber |  |  | OnGetData→`AUTO_CAL_ENABLEGetData` (0x00512B8C) |
| NUM_ATUOMATCH_EXECUTED | TAebNumber |  |  | OnGetData→`AUTO_CAL_ENABLEGetData` (0x00512B8C) |

## Handlers (2): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `AUTO_CAL_ENABLEGetData` | 0x00512B8C | 1 | OBS-COD | Ao ler dado de AUTO_CAL_ENABLE (+2 componentes). Corpo é só RET: nada acontece neste evento (OBS-COD). |  |  |  |
| `VECT_AUTOCAL_U8_2GetData` | 0x00512B90 | 1 | OBS-COD | Ao ler dado de VECT_AUTOCAL_U8_2. Corpo é só RET: nada acontece neste evento (OBS-COD). |  |  |  |
