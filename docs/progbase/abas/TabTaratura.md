# TabTaratura

OBS-COD — MAN-ABAS-B-TabTaratura

Inventário dos controles e vínculos de eventos no original. Fórmulas e efeitos ainda DESCONHECIDOS.

| Controle | Classe | Eventos/endereço |
|---|---|---|
| TabTaratura | TTabSheet | OnShow: TabTaraturaShow @ 0x4A9CD0 |
| LabelTempTaratura | TLabel |  |
| LabelT3 | TLabel |  |
| LabelScartoMinFuoriMin | TLabel |  |
| LabelU0 | TLabel |  |
| LabelScartoMinAlMin | TLabel |  |
| LabelU1 | TLabel |  |
| LabelTInjDefault | TLabel |  |
| LabelGiriAutotaratura | TLabel |  |
| LabGiriAutotaratura | TLabel |  |
| LabelDeltaK | TLabel |  |
| LabelNumControlli | TLabel |  |
| LabelNumControlliOk | TLabel |  |
| EditTempTaratura | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| EditScartoMinFuoriMin | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditScartoMinFuoriMinKeyPress @ 0x4BA1E8 |
| EditScartoMinAlMin | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| EditGiriAutotaratura | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| EditDeltaK | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| EditNumControlli | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| EditNumControlliOk | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| DrawGridTInjDefault | TDrawGrid | OnDblClick: DrawGridTInjDefaultDblClick @ 0x4B6868; OnDrawCell: DrawGridTInjDefaultDrawCell @ 0x4B64A0; OnKeyDown: DrawGridKKeyDown @ 0x4B2514; OnKeyPress: DrawGridTInjDefaultKeyPress @ 0x4B68CC |

## Limites

- Propriedades e eventos não provam a fórmula nem o efeito do clique.
- Combos/grades: itens dinâmicos e conteúdo da ECU não inferidos do DFM.
- Não é captura de tela nem execução do aplicativo Windows.
