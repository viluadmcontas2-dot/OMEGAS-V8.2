# TFormCalibra (recurso `TFORMCALIBRA`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormCalibra`; unidade: `@:Calibra`; ancestral: `TForm`; tamanho de instância: 0x654; componentes no DFM: 58.
- Título: "Calibrazione automatica centralina" — eng: "Automatic ECU calibration" / por: "Calibração automática da central de comando"
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `TFormMain.OpenFormAutotaratura` (0x0050093C)

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

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `ButtonEsciClick` | 0x00486F84 | 6 | INFERIDO | sem fatos diretos extraíveis (6 bytes; chama 1 funções sem nome) |  |  |  |
| `ButtonProseguiClick` | 0x00486F64 | 31 | INFERIDO | toca PanelMessaggio, PanelMonoplug |  |  |  |
| `FormClose` | 0x00484678 | 296 | INFERIDO | chama TAebProtocol::SendCommand |  |  | GetTickCount, Sleep |
| `FormKeyPress` | 0x0048693C | 724 | INFERIDO | chama TAebProtocol::SendCommand; toca LabelMessaggio, LabelOperazione, PanelMessaggio, ProgressBarCalibra |  |  |  |
| `TimerBlinkTimer` | 0x004847A0 | 4239 | INFERIDO | chama TAebProtocol::SendCommand, TStreamDati.TimerIOTimer; toca LabLambda, LabLambda2, LabPressione, LabTempMotore, LabTempRiduttore, LabTempoIniezioneBenzina, LabTempoIniezioneGas, LabTensioneIniettori |  |  |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `FormClose` (0x00484678):
  - sub_438204 (205 B): APIs GetTickCount,Sleep; chama TAebProtocol::Connect,TAebProtocol::DisableErrorHandling,TAebProtocol::Disconnect,TAebProtocol::EnableErrorHandling
  -   sub_4382D4 (119 B): APIs GetTickCount,Sleep; chama TAebNumber::GetEcuData
  - (+2 subfunções sem fatos diretos ou fora da lista; telas.json)
- `FormKeyPress` (0x0048693C):
  -   sub_42ECD8 (181 B): chama TAebMatrix::SetEcuData
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  - (+6 subfunções sem fatos diretos ou fora da lista; telas.json)
- `TimerBlinkTimer` (0x004847A0):
  - (+13 subfunções sem fatos diretos ou fora da lista; telas.json)
