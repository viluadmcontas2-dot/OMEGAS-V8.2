# TabDhlpAgaslev

OBS-COD — MAN-ABAS-B-TabDhlpAgaslev

Inventário dos controles e vínculos de eventos no original. Fórmulas e efeitos ainda DESCONHECIDOS.

| Controle | Classe | Eventos/endereço |
|---|---|---|
| TabDhlpAgaslev | TTabSheet | OnShow: TabDhlpAgaslevShow @ 0x4C8EC4 |
| LabelDhlp | TLabel |  |
| LabelAgaslev | TLabel |  |
| LabelDeltaTInterruzioneWarmup | TLabel | OnMouseDown: ControlMouseDown @ 0x4C6020 |
| LabDeltaTInterruzioneWarmup | TLabel |  |
| EditPressInsulDiagAftCrkDly | TValueEditEx | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0 |
| EditLoPressInsulDiagFallThd | TValueEditEx | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0 |
| EditPressInsulDiagMinTankLvlThd | TValueEditEx | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0 |
| EditPressInsulDiagPressZnt | TValueEditEx | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0 |
| EditPressInsulDiagPressZntOut | TValueEditEx | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0 |
| EditPressInsulDiagWatTempHiThd | TValueEditEx | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0 |
| EditPressInsulDiagWatTempLoThd | TValueEditEx | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0 |
| EditLoPassFiltConFast | TValueEditEx | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0 |
| EditLoPassFiltConSlow | TValueEditEx | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0 |
| EditIsteresiRiaccensione | TValueEditEx | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0 |
| EditSogliaLed1 | TValueEditEx |  |
| EditSogliaLed2 | TValueEditEx |  |
| EditSogliaLed3 | TValueEditEx |  |
| EditSogliaLed4 | TValueEditEx |  |
| EditAnticipoInterruzioneWarmup | TValueEditEx | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0 |
| EditDeltaTInterruzioneWarmup | TNumericEdit | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0; OnKeyPress: EditDeltaTInterruzioneWarmupKeyPress @ 0x4C8ECC |
| ButtonDebug | TButton | OnClick: ButtonDebugClick @ 0x4C8EC8 |
| EditDeltaTRailPerBloccoDHLP | TValueEditEx | OnEnter: EditGiriCambioEnter @ 0x4A98D8; OnExit: EditGiriCambioExit @ 0x4A9CC0 |

## Limites

- Propriedades e eventos não provam a fórmula nem o efeito do clique.
- Combos/grades: itens dinâmicos e conteúdo da ECU não inferidos do DFM.
- Não é captura de tela nem execução do aplicativo Windows.
