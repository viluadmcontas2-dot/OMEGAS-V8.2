# OMEGAS-AMARELO-WU-006 — Runtime e telemetria leve

Issue: #78
Estado: BLOCKED_BY_WU_001_WU_003

## Resultado observável
Telemetria fluida em hardware fraco sem bloquear aquisição científica.

## Contrato
- scheduler por classe de dado;
- live rápido, vetores lentos;
- parsing/ciência fora do hot path visual;
- snapshots coerentes;
- projection diff/incremental;
- backpressure mensurável;
- reconnect sem voto científico novo.

## Gate
Replay de cadência real + 1280×720 + budgets de CPU/latência/frescor registrados.