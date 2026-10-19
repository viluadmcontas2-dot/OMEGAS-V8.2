# Modo autônomo do Codex (leia e siga; só pare quando o limite de 5 h acabar)

Ordem de leitura: AGENTS.md → docs/handoff/CONTEXTO-DO-DONO.md → PENDENTE.md → CONTRATO-CODEX.md (§1–§9) → PROMPT-CORRECAO-TELA-FERRAMENTAS.md → docs/progbase/RETOMAR.md. Branch: claude/carregar-sessao-r7j85r.

## Prioridade 0 (primeiro, antes de qualquer outra coisa)
Corrigir a aba "Ferramentas" VAZIA no WebView (PROMPT-CORRECAO-TELA-FERRAMENTAS.md), CI, gerar APK e avisar no issue de progresso. Depois a lista de PENDENTE.md em ordem.

## Cadência (obrigatória)
- COMMIT + PUSH a cada ação concluída (arquivo/teste/captura), no máximo a cada ~10 min, mesmo parcial ("WIP: <o quê>"). Nunca acumule trabalho local: se o limite travar em 100%, tudo já está no remoto.
- A cada FEATURE concluída: 1 comentário no issue "Progresso do Codex" (o dono recebe e-mail do GitHub): o que mudou em linguagem simples, evidência (SHA, teste verde, print), próximo passo.
- Auto-auditoria a cada ação: antes de commitar, 1 linha no docs/handoff/AUDITORIA.md (data, ação, teste RED→GREEN, evidence id, risco). Nunca "feito" sem teste verde.

## Método (igual ao do Claude)
- RED→GREEN, mutação, fluxo, esperado×inesperado. Testes locais mínimos (1 arquivo); suíte inteira na CI (ci.yml dividida em jobs paralelos).
- SEMPRE emular a central (tools/emulacao/ecu_emulator.py; ligar ao original via porta serial virtual — CONTRATO §1) e capturar cada tela/ação (PNG + quadros) em docs/progbase/capturas/ e docs/evidence/img/, como já existe no repositório.
- Engenharia reversa: skill reverse-engineer-anything + REA (investigate_feature, audit_residual_unknowns, verify_reconstruction, compare_functions); se o MCP estourar tempo, usar `npx -y rea-agents@6.4.0` em segundo plano. Evidence ids (REA e MAN-) no INDEX.md; costurar tudo no Graphify (graphify update . + tools/grafo_conhecimento.py + tools/progbase/grafo_progbase.py) a cada marco.
- SEMÂNTICA: muitos nomes parecidos têm funções diferentes (TFormAutoCal ≠ TFormCalibra ≠ TFormCorrCalibra; AutoCal de aprendizado ≠ calibração rápida; FLAG_CONF1/2/3...). Para cada nome: 1 linha "o que é / o que NÃO é" no glossário interno docs/progbase/semantica.md, e textos da interface em linguagem simples e autoexplicativa. Para cada função: causa→efeito didático (como altera, melhora, refina ou ajuda o usuário).
- Regras permanentes: app NUNCA grava sozinho (2 toques + leitura de volta + Desfazer); nada por tempo; não burlar licença; não versionar .exe/DLL/License.dat; linguagem leiga; alvo multimídia paisagem.
