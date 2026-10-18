# Handoff para o Codex (continuar sem o Claude)

## Como começar (cole no Codex)
> Leia, nesta ordem: AGENTS.md, docs/handoff/PENDENTE.md, docs/handoff/CONTRATO-CODEX.md, docs/progbase/RETOMAR.md, .claude/skills/entender-dono/SKILL.md, .claude/skills/emular-por-observacao/SKILL.md, docs/mapa-de-fontes/MAPA.md. Rode `python3 -B tools/run_checks.py` e `node --test tests/ui/*.test.cjs` para ver o que está verde. Continue do primeiro item não marcado em PENDENTE.md, usando os PROGRESSO*.md de docs/progbase. Antes de criar algo, consulte `graphify query "<pergunta>"` (graphify-out/) e docs/progbase/grafo. Trabalhe com subagentes, TDD RED→GREEN, idempotente, checkpoint em arquivo a cada marco, commit+push a cada marco. Nunca grave na ECU sem 2 toques; nada por tempo; não burle licença; não versione .exe/DLL/License.dat.

## Como pensar (o que o dono exige)
1. Síntese da mensagem do dono em 1 frase; corrigir termos; questionar; buscar o melhor caminho (reaproveitar o que existe).
2. A cada descoberta no original: (a) ligar ao grafo e ao INDEX.md com evidence id; (b) perguntar "como isso resolve o problema do dono (carro apaga com embreagem pisada)?"; (c) perguntar "como a telemetria do OMEGAS torna isso automático/inteligente?"; (d) propor só o que for útil, didático e leve.
3. Interface: didática, premium, leve. Modelo = tela AutoCal atual. Botão diz o que faz + estado escrito; campo editável com lápis; interruptor Ligado/Desligado; 1 linha de apoio; zero jargão (teste tests/ui/linguagem-leiga.test.cjs reprova). Alvo: multimídia paisagem 1280x720/1024x600/800x480.
4. Evidência em tudo (OBS-COD/OBS-FIO/DERIVADO/INFERIDO/DESCONHECIDO). Nunca "feito" sem teste verde. CI: `ci.yml` (workflow_dispatch) e `verde-apk-now.yml` (build_apk=true) — só esses dois workflows.
5. Gestão de agentes: brief fechado com prazo; checkpoint em arquivo; ping aos 15 min; relançar aos 20.

## Estado em 2026-10-18 (ver STATUS.md e PENDENTE.md para o atual)
- Anti-apagão: botão manual pronto (quadro provado por execução do código original). Marcha lenta: só leitura + sugestão. Leveza: -52% JS no arranque. e2e de toque: 47 testes. Decompilação: inventário, protocolo, abas 1-16, Ghidra completo, grafo, manual por página, original rodando com a licença do dono (sem ECU). Guia PDF v1 (longo; reescrever curto).
- Próximo grande passo: ECU simulada ligada ao original (CONTRATO-CODEX §1) e artefato explorável (§2).
