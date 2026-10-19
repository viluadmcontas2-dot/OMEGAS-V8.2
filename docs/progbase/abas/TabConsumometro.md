# TabConsumometro

OBS-COD — MAN-ABAS-B-TabConsumometro

Inventário dos controles e vínculos de eventos no original. Fórmulas e efeitos ainda DESCONHECIDOS.

| Controle | Classe | Eventos/endereço |
|---|---|---|
| TabConsumometro | TTabSheet | OnShow: TabConsumometroShow @ 0x4C1044 |
| LabelLitriSerbatoio | TLabel |  |
| LabLitriSerbatoio | TLabel |  |
| LabelTagliaIniettori | TLabel |  |
| CheckConsumometro | TCheckBox | OnClick: CheckConsumometroClick @ 0x4C1108; OnKeyPress: CheckTpsBlocKeyPress @ 0x4A8928 |
| EditLitriSerbatoio | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| ComboTagliaIniettori | TKeyComboBox | OnKeyPress: ComboTagliaIniettoriKeyPress @ 0x4C1138 |

## Limites

- Propriedades e eventos não provam a fórmula nem o efeito do clique.
- Combos/grades: itens dinâmicos e conteúdo da ECU não inferidos do DFM.
- Não é captura de tela nem execução do aplicativo Windows.
