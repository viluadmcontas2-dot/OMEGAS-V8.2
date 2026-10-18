# TStreamDati (recurso `TSTREAMDATI`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `StreamDati`; unidade: `@:iodata`; ancestral: `TDataModule`; tamanho de instância: 0x3EBC; componentes no DFM: 321.
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

## Temporizadores (1)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) | Intervalo ms |
|---|---|---|---|---|---|
| TimerIO | TTimer |  |  | OnTimer→`TimerIOTimer` (0x00437C6C) | 3000 |

## Handlers (1): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos (chaves i18n) | APIs | Chama |
|---|---|---:|---|---|---|---|---|
| `TimerIOTimer` | 0x00437C6C | 1415 | INFERIDO | Handler `TimerIOTimer`; chama APIs do Windows: KERNEL32.DLL!GetTickCount, KERNEL32.DLL!Sleep, USER32.DLL!PostMessageW; chama: TAebNumber::GetEcuData, TAebProtocol::Connect, TAebProtocol::DisableErrorHandling, TAebProtocol::Disconnect, TAebProtocol::EnableErrorHandling, TAebProtocol::GetComFail; toca campos/controles: AebConnection, MASK_INIETTORI_BENZINA, MASK_INIETTORI_BENZINA_LR, SERIAL_NUMBER |  | KERNEL32.DLL!GetTickCount, KERNEL32.DLL!Sleep, USER32.DLL!PostMessageW | TAebNumber::GetEcuData, TAebProtocol::Connect, TAebProtocol::DisableErrorHandling, TAebProtocol::Disconnect, TAebProtocol::EnableErrorHandling |
