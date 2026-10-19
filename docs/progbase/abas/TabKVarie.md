# TabKVarie

OBS-COD — MAN-ABAS-B-TabKVarie

Inventário dos controles e vínculos de eventos no original. Fórmulas e efeitos ainda DESCONHECIDOS.

| Controle | Classe | Eventos/endereço |
|---|---|---|
| TabKVarie | TTabSheet | OnShow: TabKVarieShow @ 0x4B94C8 |
| LabelSogliaFS2 | TLabel | OnMouseDown: ControlMouseDown @ 0x4C6020 |
| LabelMsFS2 | TLabel |  |
| LabelKFiltro2 | TLabel |  |
| LabelGiriBassi | TLabel |  |
| LabelGiriAlti | TLabel |  |
| LabelGiriKFiltro | TLabel | OnMouseDown: ControlMouseDown @ 0x4C6020 |
| EditFiltro | TNumericEdit | OnEnter: EditFiltroEnter @ 0x4BDFD4; OnExit: EditFiltroExit @ 0x4BE208; OnKeyPress: EditFiltroKeyPress @ 0x4B9644 |
| EditSogliaFS2 | TNumericEdit | OnEnter: EditGiriKFiltroEnter @ 0x4BE90C; OnExit: EditGiriKFiltroExit @ 0x4BEABC; OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| EditKFiltro | TNumericEdit | OnEnter: EditGiriKFiltroEnter @ 0x4BE90C; OnExit: EditGiriKFiltroExit @ 0x4BEABC; OnKeyPress: EditKFiltroKeyPress @ 0x4B95C0 |
| EditGiriKFiltro | TNumericEdit | OnEnter: EditGiriKFiltroEnter @ 0x4BE90C; OnExit: EditGiriKFiltroExit @ 0x4BEABC; OnKeyPress: EditGiriKFiltroKeyPress @ 0x4BEB24 |
| EditKFiltroGiriAlti | TNumericEdit | OnEnter: EditGiriKFiltroEnter @ 0x4BE90C; OnExit: EditGiriKFiltroExit @ 0x4BEABC; OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| EditGiriAltiKFiltro | TNumericEdit | OnEnter: EditGiriKFiltroEnter @ 0x4BE90C; OnExit: EditGiriKFiltroExit @ 0x4BEABC; OnKeyPress: EditGiriKFiltroKeyPress @ 0x4BEB24 |

## Limites

- Propriedades e eventos não provam a fórmula nem o efeito do clique.
- Combos/grades: itens dinâmicos e conteúdo da ECU não inferidos do DFM.
- Não é captura de tela nem execução do aplicativo Windows.
