# SDE — ciclo-fechado-refino
Construído: `core/write-gate.js` (map.js export, refino.js mapProposal); `NativeKMovementTracker` ligado no `NativeAutoCalMonitor` (statusJson.kMovement e no evento de AutoMatch confirmado); `tools/jvm/AquisicaoEmulada.kt` (FakeEcu com anomalias + fuzz); `tools/audit/pontas_soltas.py`; poda: portmon-*.js → tools/portmon, 3 bindings mortos em drawers.js.
Desvios: nenhum nas classes de comando da ECU. Dívida: 33 funções de ponte sem uso (Kotlin, precisa CI).
