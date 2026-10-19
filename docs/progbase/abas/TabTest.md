# TabTest

OBS-COD — MAN-ABAS-B-TabTest

Inventário dos controles e vínculos de eventos no original. Fórmulas e efeitos ainda DESCONHECIDOS.

| Controle | Classe | Eventos/endereço |
|---|---|---|
| TabTest | TTabSheet | OnShow: TabTestShow @ 0x4BA440 |
| LabelTestWord | TLabel |  |
| LabelTestByte | TLabel |  |
| LabelTestArray | TLabel |  |
| LabelTestTempo | TLabel |  |
| LabTestTempo | TLabel |  |
| EditTestWord | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| EditTestByte | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| EditTestTempo | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| DrawGridTest | TDrawGrid | OnDblClick: DrawGridTestDblClick @ 0x4B75EC; OnDrawCell: DrawGridTestDrawCell @ 0x4B71B8; OnKeyDown: DrawGridKKeyDown @ 0x4B2514; OnKeyPress: DrawGridTestKeyPress @ 0x4B7650 |

## Limites

- Propriedades e eventos não provam a fórmula nem o efeito do clique.
- Combos/grades: itens dinâmicos e conteúdo da ECU não inferidos do DFM.
- Não é captura de tela nem execução do aplicativo Windows.
