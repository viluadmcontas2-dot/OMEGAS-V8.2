# TStreamDati (recurso `TSTREAMDATI`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `StreamDati`; unidade: `@:iodata`; ancestral: `TDataModule`; tamanho de instância: 0x3EBC; componentes no DFM: 321.
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `WinMain_CriaFormularios (Application.CreateForm de TFormMain, TStreamDati, TFormConfig, módulos de dados e telas)` (0x004111F8)

## Temporizadores (1)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) | Intervalo ms |
|---|---|---|---|---|---|
| TimerIO | TTimer |  |  | OnTimer→`TimerIOTimer` (0x00437C6C) | 3000 |

## Handlers (1): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `TimerIOTimer` | 0x00437C6C | 1415 | INFERIDO | chama APIs do Windows GetTickCount, Sleep, PostMessageW; chama TAebNumber::GetEcuData, TAebProtocol::Connect, TAebProtocol::DisableErrorHandling, TAebProtocol::Disconnect, TAebProtocol::EnableErrorHandling, TAebProtocol::GetComFail; toca AebConnection, MASK_INIETTORI_BENZINA, MASK_INIETTORI_BENZINA_LR, SERIAL_NUMBER |  | GetTickCount, Sleep, PostMessageW |  |
