# Regras para agentes das unidades de trabalho (Omega Aura)

Vale para qualquer agente (Claude Sonnet em modo baixo, Codex, Antigravity). Plano: `PLANO.md` · fluxo: `CONTRATO-DE-FLUXO.md` · auditorias: `auditoria/A1..A4`.

## Antes de começar
1. Leia `AGENTS.md` (80 linhas), `docs/aura/PLANO.md`, `docs/aura/CONTRATO-DE-FLUXO.md` e SÓ a seção da auditoria que a sua tarefa cita.
2. `tools/sync-agentes.sh --so-ver`. Crie a sua branch `claude/aura-<ut>` a partir de `claude/omega-aura` (`git switch -c ...`).

## Como trabalhar
1. **TDD** (skill `superpowers:test-driven-development`): teste primeiro; rode e MOSTRE falhando (RED) pelo motivo certo; implemente o mínimo; rode e MOSTRE passando (GREEN). Nunca enfraqueça teste, critério ou lista para passar. Se um teste antigo trava texto ou comportamento que a tarefa manda mudar, troque o teste NA MESMA mudança (RED do novo primeiro) e diga qual trocou e por quê.
2. **Um dono por arquivo:** escreva só na sua lista. Precisa de outro arquivo? Não edite: peça no relatório. Nunca edite `AGENTS.md` (limite de 80 linhas), `docs/handoff/PENDENTE.md`, `docs/aura/PLANO.md`, `ui/index.html` nem `ui/app.js` (o Claude principal integra; informe as linhas `<script>`/`<link>` e a ordem).
3. **Protegidas:** `UsbSerialManager`, `ResponseDrivenEcuEngine`, `AutoCalProtocol`, `KFactorManager`, `KWriteManager`, `AutoCalNativeActionManager`. Não altere bytes nem fluxo; use só a API pública. Sem API paga, sem segredo em arquivo, log ou commit. Sem `git stash`, sem force push.
4. **Regras do dono que valem sempre:** sem relógio para decidir (use contagem de leituras ou evidência) · desconhecido é "—", nunca 0 · "gravado" só depois da leitura de volta igual · ação simples = 1 toque, sem janela de confirmação · texto da interface em português do Brasil, curto, verbo + objeto, sem jargão · a mesma palavra em todo lugar.
5. **Verificação:** enquanto edita, só checagem leve; ao fechar o bloco rode UMA vez o teste do que mudou e dos dependentes. Antes do envio: `node --test tests/ui/*.test.cjs` e `python3 -B -m unittest discover -s tests -p "test_*.py"`. Se criou, renomeou ou apagou arquivos em `tests/`, `docs/` ou `app/src/main/assets/ui/`, rode também `python3 -B tools/grafo_producao.py --check`, `python3 -B tools/mapa_de_fontes.py --check` e `python3 -B tools/auditoria.py --check`; se acusarem diferença, rode o mesmo script sem `--check` e inclua o resultado no commit (se só `docs/handoff/AUDITORIA.json` mudou por efeito colateral, `git checkout -- docs/handoff/AUDITORIA.json`).
6. **Commits** pequenos, mensagem em português dizendo o porquê, terminando com:
   `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`
   `Claude-Session: https://claude.ai/code/session_01GEY5uxfwAVFTW4f8ByCtRs`
7. **Enviar:** `tools/sync-agentes.sh` (sem force; lê o remoto de volta). Só diga "publicado" com saída 0.
8. **Kotlin não compila aqui** (sem Android SDK): a prova é a CI do envio. Carregue as ferramentas com `ToolSearch` (`select:mcp__github__actions_list,mcp__github__get_job_logs`), olhe o workflow "OMEGAS CI" da sua branch; para esperar use `sleep 270` com `run_in_background=true`, não consulte em laço. Vermelho = leia o log do job, corrija à frente, reenvie (no máximo 3 rodadas; depois pare e relate). Unidades só de JS/docs não esperam a CI: o Claude principal confere.
9. Não deixe processo em segundo plano nem arquivo temporário no repositório ao terminar.

## Relatório final (até 25 linhas, português simples)
FEITO · COMPROVADO (saída RED e GREEN em 1–2 linhas cada; CI com o SHA, se aplicável) · PENDENTE/BLOQUEADO · arquivos alterados · linhas `<script>`/`<link>` para o `index.html` · decisões para o dono ou para o Claude principal. Não afirme o que não rodou.
