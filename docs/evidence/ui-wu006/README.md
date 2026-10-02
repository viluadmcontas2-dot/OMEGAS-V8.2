# Evidência preliminar WU-006

Checkpoint de passagem, não aceite final. Source atual: 59f6fc57443e3e3eb3298f5777ee322e702c5f98.

Leia `../../handoff/WU006-CONTINUATION-2026-10-02.md` para origem/limites de cada teste. `draft/` contém Chromium 1280×720 com bridge falso e fixture, sem ECU física. O script cria componentes reais; não valida todo o startup Android/UI. AutoCal normal/problem precisam ajustar fixture (LOW normal, span from/to problem); 644/900 e checklist item a item pendentes.

709 JVM foram executados no candidato 22c6a8b7238dfce5d66d80d7201dcaf6c7d3004f; depois do ajuste de continuidade no source 59f6fc57 houve 5 afetados PASS e gate exato PASS. Nenhum desses logs comprova Gradle Android, lint ou APK atual.

Capturas e logs são evidência de desenvolvimento. Não representam consumo/volume medido, desaparecimento de tranco ou validação no veículo.

PHYSICAL_VALIDATION_CLAIMED=false
