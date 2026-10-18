# TFormCalibra (recurso `TFORMCALIBRA`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormCalibra`; unidade: `@:Calibra`; ancestral: `TForm`; tamanho de instância: 0x654; componentes no DFM: 58.
- Título: "Calibrazione automatica centralina" — eng: "Automatic ECU calibration" / por: "Calibração automática da central de comando"
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

## Controles com evento/ação (4)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| FormCalibra | TFormCalibra | Calibrazione automatica centralina / Automatic ECU calibration / Calibração automática da central de comando |  | OnClose→`FormClose` (0x00484678); OnKeyPress→`FormKeyPress` (0x0048693C) |
| PanelMonoplug/ButtonProsegui | TButton | Prosegui / Continue / Continuar |  | OnClick→`ButtonProseguiClick` (0x00486F64) |
| PanelMonoplug/ButtonCancella | TButton | Annulla |  | OnClick→`ButtonEsciClick` (0x00486F84) |
| ButtonEsci | TButton | &Esci / &Exit / &Sair |  | OnClick→`ButtonEsciClick` (0x00486F84) |

## Temporizadores (1)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) | Intervalo ms |
|---|---|---|---|---|---|
| TimerBlink | TTimer |  |  | OnTimer→`TimerBlinkTimer` (0x004847A0) | 75 |

## Handlers (5): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos (chaves i18n) | APIs | Chama |
|---|---|---:|---|---|---|---|---|
| `ButtonEsciClick` | 0x00486F84 | 6 | INFERIDO | Handler `ButtonEsciClick`; sem fatos extraíveis automaticamente (6 bytes) |  |  |  |
| `ButtonProseguiClick` | 0x00486F64 | 31 | INFERIDO | Handler `ButtonProseguiClick`; toca campos/controles: PanelMessaggio, PanelMonoplug |  |  |  |
| `FormClose` | 0x00484678 | 296 | INFERIDO | Handler `FormClose`; chama APIs do Windows: KERNEL32.DLL!GetTickCount, KERNEL32.DLL!Sleep; chama: TAebProtocol::SendCommand |  | KERNEL32.DLL!GetTickCount, KERNEL32.DLL!Sleep | TAebProtocol::SendCommand |
| `FormKeyPress` | 0x0048693C | 724 | INFERIDO | Handler `FormKeyPress`; chama: TAebProtocol::SendCommand; toca campos/controles: LabelMessaggio, LabelOperazione, PanelMessaggio, ProgressBarCalibra |  |  | TAebProtocol::SendCommand |
| `TimerBlinkTimer` | 0x004847A0 | 4239 | INFERIDO | Handler `TimerBlinkTimer`; chama: TAebProtocol::SendCommand, TStreamDati.TimerIOTimer; toca campos/controles: LabLambda, LabLambda2, LabPressione, LabTempMotore, LabTempRiduttore, LabTempoIniezioneBenzina, LabTempoIniezioneGas, LabTensioneIniettori |  |  | TAebProtocol::SendCommand, TStreamDati.TimerIOTimer |
