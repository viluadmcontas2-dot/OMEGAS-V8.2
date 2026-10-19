# TabInjSettings

OBS-COD — MAN-ABAS-B-TabInjSettings

Inventário dos controles e vínculos de eventos no original. Fórmulas e efeitos ainda DESCONHECIDOS.

| Controle | Classe | Eventos/endereço |
|---|---|---|
| TabInjSettings | TTabSheet |  |
| LabelTempoMaxCorr | TLabel |  |
| LabelMs1 | TLabel |  |
| LabelCorrenteCutoff | TLabel |  |
| LabelMs7 | TLabel |  |
| LabelTempoInterSpegnimento | TLabel |  |
| LabelMs4 | TLabel |  |
| LabelValPercHoldingCurrent | TLabel |  |
| LabelPerc | TLabel |  |
| LabelCorrente | TLabel |  |
| LabelA | TLabel |  |
| LabelImpedenzaIniettori | TLabel |  |
| LabelHz | TLabel |  |
| LabelMaxCorrenteCambio | TLabel | OnMouseDown: ControlMouseDown @ 0x4C6020 |
| LabMaxCorrenteCambio | TLabel |  |
| EditTempoMassimaCorrente | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| EditCorrenteCutoff | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| EditParamInj | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| EditTempoInterSpegnimento | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| EditValPercHoldingCurrent | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| EditCorrente | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| EditImpedenzaIniettori | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| CheckTempoMaxCorrente | TCheckBox | OnClick: CheckTempoMaxCorrenteClick @ 0x4B0E9C; OnKeyPress: CheckTpsBlocKeyPress @ 0x4A8928 |
| EditMaxCorrenteCambio | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditAdvParamInjGasKeyPress @ 0x4C7150 |

## Limites

- Propriedades e eventos não provam a fórmula nem o efeito do clique.
- Combos/grades: itens dinâmicos e conteúdo da ECU não inferidos do DFM.
- Não é captura de tela nem execução do aplicativo Windows.
