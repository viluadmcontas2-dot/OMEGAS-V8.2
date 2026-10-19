# TabAdvanced

OBS-COD — MAN-ABAS-B-TabAdvanced

Inventário dos controles e vínculos de eventos no original. Fórmulas e efeitos ainda DESCONHECIDOS.

| Controle | Classe | Eventos/endereço |
|---|---|---|
| TabAdvanced | TTabSheet | OnShow: TabAdvancedShow @ 0x4C9BBC |
| LabelHelp | TLabel |  |
| CheckContributi | TCheckBox | OnClick: CheckContributiClick @ 0x4C9C1C; OnKeyPress: CheckTpsBlocKeyPress @ 0x4A8928 |
| DrawGridContributi | TDrawGrid | OnDblClick: DrawGridContributiDblClick @ 0x4CA288; OnDrawCell: DrawGridContributiDrawCell @ 0x4C9DF0; OnKeyDown: DrawGridKKeyDown @ 0x4B2514; OnKeyPress: DrawGridContributiKeyPress @ 0x4CA278 |

## Limites

- Propriedades e eventos não provam a fórmula nem o efeito do clique.
- Combos/grades: itens dinâmicos e conteúdo da ECU não inferidos do DFM.
- Não é captura de tela nem execução do aplicativo Windows.
