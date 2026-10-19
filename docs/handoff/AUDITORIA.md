# Auditoria de ações

2026-10-19 | CI: contrato AutoCal renomeado e executor de quatro testes ativado; falha injetada antes ignorada (exit 0), agora detectada (exit 1); execução integrada AUTOCAL_HOST_CONTRACT=PASS tests=4; evidência local e CI remota pendente no novo SHA; risco: teste estático não prova ECU.

- 2026-10-19 — Corpus completo: triagem por ordem gravada, linha do tempo até recuperação e comparação observacional de limiares; `test_apagoes_movimento.py` RED (analisador ausente) → GREEN 7/7. Fonte SHA-256 registrada em `docs/progbase/bins/apagoes-em-movimento.json`. Risco/limite: sem velocidade/chave não confirma movimento nem causa; sem algoritmo interno não recomenda extras ou promete consumo. Worktree wip-codex; push adiado para preservar APK em andamento, conforme ordem atual do dono.
