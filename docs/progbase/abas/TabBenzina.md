# TabBenzina

OBS-COD — MAN-ABAS-B-TabBenzina

Inventário dos controles e vínculos de eventos no original. Fórmulas e efeitos ainda DESCONHECIDOS.

| Controle | Classe | Eventos/endereço |
|---|---|---|
| TabBenzina | TTabSheet | OnShow: TabBenzinaShow @ 0x4BF004 |
| ScrollBox1 | TScrollBox |  |
| LabelCoeffSplitFuel | TLabel |  |
| LabelRifSplitFuel | TLabel |  |
| LabRpmForSplitFuel | TLabel |  |
| LabelRpmForSplitFuel | TLabel | OnMouseDown: ControlMouseDown @ 0x4C6020 |
| GroupMinimoBenzina | TGroupBox |  |
| LabelGiriMinimoPerBenzina | TLabel | OnMouseDown: ControlMouseDown @ 0x4C6020 |
| LabGiriMinimoPerBenzina | TLabel |  |
| LabelIniettate | TLabel | OnMouseDown: ControlMouseDown @ 0x4C6020 |
| LabelUscita | TLabel | OnMouseDown: ControlMouseDown @ 0x4C6020 |
| LabelIniettateCutoff | TLabel | OnMouseDown: ControlMouseDown @ 0x4C6020 |
| RadioGas | TRadioButton | OnClick: RadioGasClick @ 0x4BFAD8; OnMouseDown: ControlMouseDown @ 0x4C6020 |
| EditGiriMinimoPerBenzina | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditGiriMinimoPerBenzinaKeyPress @ 0x4BFE6C |
| RadioBenzina | TRadioButton | OnClick: RadioGasClick @ 0x4BFAD8; OnMouseDown: ControlMouseDown @ 0x4C6020 |
| RadioRientro | TRadioButton | OnClick: RadioGasClick @ 0x4BFAD8; OnMouseDown: ControlMouseDown @ 0x4C6020 |
| EditIniettate | TNumericEdit | OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| EditUscita | TNumericEdit | OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| EditIniettateCutoff | TNumericEdit | OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| CheckParzializza | TCheckBox | OnClick: CheckParzializzaClick @ 0x4BFAA0; OnKeyPress: CheckTpsBlocKeyPress @ 0x4A8928; OnMouseDown: ControlMouseDown @ 0x4C6020 |
| GroupAltiGiriBenzina | TGroupBox |  |
| LabelGiriPerBenzina | TLabel | OnMouseDown: ControlMouseDown @ 0x4C6020 |
| LabelTempiBenzina | TLabel | OnMouseDown: ControlMouseDown @ 0x4C6020 |
| LabGiriPerBenzina | TLabel |  |
| LabTempiBenzina | TLabel |  |
| LabTempoBenzina1 | TLabel |  |
| LabelTempoBenzina1 | TLabel | OnMouseDown: ControlMouseDown @ 0x4C6020 |
| ShapeBenzina | TShape |  |
| LabelRpmRangeStop | TLabel |  |
| LabelRpmRangeStart | TLabel |  |
| RadioGasAltiGiri | TRadioButton | OnClick: RadioGasAltiGiriClick @ 0x4BFEF4; OnMouseDown: ControlMouseDown @ 0x4C6020 |
| RadioBenzinaAltiGiri | TRadioButton | OnClick: RadioGasAltiGiriClick @ 0x4BFEF4; OnMouseDown: ControlMouseDown @ 0x4C6020 |
| EditTempoBenzina3 | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditTempoBenzinaKeyPress @ 0x4C0570 |
| EditGiriPerBenzina | TNumericEdit | OnKeyPress: EditGiriPerBenzinaKeyPress @ 0x4C04E8 |
| EditTempoBenzina1 | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditTempoBenzinaKeyPress @ 0x4C0570 |
| RadioContributoAltiGiri | TRadioButton | OnClick: RadioGasAltiGiriClick @ 0x4BFEF4; OnMouseDown: ControlMouseDown @ 0x4C6020 |
| EditGiriSupBenzina | TNumericEdit | OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| EditRpmForSplitFuel | TNumericEdit | OnKeyPress: EditRifLambdaKeyPress @ 0x4A6A00 |
| DrawGridSplitFuel | TDrawGrid | OnDblClick: DrawGridSplitFuelDblClick @ 0x4C2788; OnDrawCell: DrawGridSplitFuelDrawCell @ 0x4C2420; OnKeyDown: DrawGridKKeyDown @ 0x4B2514; OnKeyPress: DrawGridSplitFuelKeyPress @ 0x4C27EC |
| CheckIstantaneo | TCheckBox | OnClick: CheckIstantaneoClick @ 0x4BFA68; OnKeyPress: CheckTpsBlocKeyPress @ 0x4A8928; OnMouseDown: ControlMouseDown @ 0x4C6020 |
| CheckSplitFuel | TCheckBox | OnClick: CheckSplitFuelClick @ 0x4C1C64; OnKeyPress: CheckTpsBlocKeyPress @ 0x4A8928; OnMouseDown: ControlMouseDown @ 0x4C6020 |
| StrategiaPandaUI1 | TStrategiaPandaUI |  |
| PanelBackground | TGroupBox |  |
| edit_SP_SogliaInnescoGiri | TValueEditEx |  |
| edit_SP_SogliaInnescoTInj | TValueEditEx |  |
| edit_SP_SogliaInnescoMAP | TValueEditEx |  |
| edit_SP_SogliaDisinnescoGiri | TValueEditEx |  |
| edit_SP_SogliaDisinnescoTInj | TValueEditEx |  |
| edit_SP_SogliaDisinnescoMAP | TValueEditEx |  |
| edit_SP_TempoInterventoEmulazione | TValueEditEx |  |
| edit_SP_ValoreDiEmulazioneSensPress | TValueEditEx |  |
| edit_SP_SogliaMAPperEmulazioneContinuativa | TValueEditEx |  |
| edit_SP_SogliaAriaCondizionataON | TValueEditEx |  |
| edit_SP_SogliaInnescoGiri_CO | TValueEditEx |  |
| edit_SP_SogliaInnescoTInj_CO | TValueEditEx |  |
| edit_SP_SogliaInnescoMAP_CO | TValueEditEx |  |
| edit_SP_SogliaDisinnescoGiri_CO | TValueEditEx |  |
| edit_SP_SogliaDisinnescoTInj_CO | TValueEditEx |  |
| edit_SP_SogliaDisinnescoMAP_CO | TValueEditEx |  |
| edit_SP_TempoInterventoEmulazione_CO | TValueEditEx |  |
| edit_SP_SogliaMAPperEmulazioneContinuativa_CO | TValueEditEx |  |

## Limites

- Propriedades e eventos não provam a fórmula nem o efeito do clique.
- Combos/grades: itens dinâmicos e conteúdo da ECU não inferidos do DFM.
- Não é captura de tela nem execução do aplicativo Windows.
