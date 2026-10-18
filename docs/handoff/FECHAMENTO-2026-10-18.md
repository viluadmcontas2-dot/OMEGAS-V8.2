# Fechamento 2026-10-18 — regras e tarefas (dono: "termine tudo, por evento, sem gravar sozinho")

## Regras do dono (valem para TODA tarefa)
1. **Tudo por evento/gatilho. Nada por tempo.** Nenhuma decisão do app depende de minutos/segundos (nem "a cada N min", nem "12 s depois"). Gatilhos válidos: sessão USB nova + primeiro quadro válido, mudança de contagem de AutoMatch, mudança de curva/mapa pela ECU, escrita confirmada (ACK+readback), toque do operador, chegada de dado. Os laços de leitura contínua da serial (telemetria) são o fluxo de dados, não decisão: ficam, mas cada um tem que estar listado e justificado em `docs/handoff/EVENTOS-SEM-TEMPO.md`. Proibido criar `setInterval`, `scheduleAtFixedRate`, `scheduleWithFixedDelay`, `Thread.sleep`, `postDelayed`, `delay()` novos como base de decisão. Esperar a ECU = esperar a RESPOSTA (leitura de volta), contando leituras, não relógio.
2. **O app NUNCA grava na ECU sozinho.** Só sugere (grava a sugestão na sessão e mostra na tela). Gravar = ação manual do operador com ACK + leitura de volta. AutoMatch manual continua só por toque.
3. Não mudar bytes/comandos de `UsbSerialManager`, `ResponseDrivenEcuEngine`, `AutoCalProtocol`, `KFactorManager`, `KWriteManager`, `AutoCalNativeActionManager`.
4. TDD: teste que falha primeiro, correção mínima, teste verde. Idempotente: rodar duas vezes dá o mesmo resultado.
5. Texto de tela em português simples. Desconhecido é "—", nunca 0.
6. Verificações locais: `node --test tests/ui/*.test.cjs`, `python3 -B tools/run_checks.py`, `node tools/ci/sem_rolagem_abas.cjs`; Kotlin só compila na CI (escreva os testes JUnit mesmo assim; `kotlinc` local só para arquivos puros).
7. NÃO commitar. Atualizar `PROGRESSO.md` na raiz do seu diretório de trabalho a cada passo.
8. Só mexer nos arquivos do seu escopo. Se precisar de outro arquivo, anote em PROGRESSO.md em vez de editar.

## Evidência de base (já medida)
`docs/evidence/2026-10-18-quadro-gasolina-gnv-k.md` (será criado pela tarefa W5). Gasolina real × equivalente da ECU em GNV concordam em 0–4 % na maior parte das faixas MAP×RPM; MAP < 0,5 chega a 8–12 %.
