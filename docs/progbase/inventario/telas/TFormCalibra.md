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

### Cadeia de subfunções sem nome alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `FormClose` (0x00484678):
  - sub_42B244 (15 B)
  - sub_4381F4 (7 B)
  - sub_438204 (205 B): APIs GetTickCount,Sleep; chama TAebProtocol::Connect,TAebProtocol::DisableErrorHandling,TAebProtocol::Disconnect,TAebProtocol::EnableErrorHandling
  -   sub_4382D4 (119 B): APIs GetTickCount,Sleep; chama TAebNumber::GetEcuData
- `FormKeyPress` (0x0048693C):
  - sub_40487C (45 B)
  - sub_486CEC (625 B)
  -   sub_42EC94 (65 B)
  -   sub_42ECD8 (181 B): chama TAebMatrix::SetEcuData
  -     sub_42F338 (7 B)
  -     sub_42F340 (7 B)
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
- `TimerBlinkTimer` (0x004847A0):
  - sub_40487C (45 B)
  - sub_404C40 (81 B)
  - sub_404C94 (59 B)
  - sub_405754 (48 B)
  - sub_4057C0 (139 B)
  - sub_40584C (57 B)
  - sub_42A52C (358 B)
  - sub_42A788 (358 B)
  - sub_4339B8 (72 B)
  - sub_433A00 (75 B)
  - sub_433FFC (14 B)
  - sub_434530 (21 B)
  - … +7 (ver telas.json)
