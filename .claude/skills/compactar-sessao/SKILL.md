---
name: compactar-sessao
description: Use quando a conversa passar de ~150k tokens de contexto, antes de tarefas longas, ou quando o dono falar em economizar tokens. Mantém a janela de contexto pequena (cada mensagem relê a sessão inteira) gravando o estado em arquivo e delegando a subagentes.
---
# Compactar sessão (economia de tokens)

Cada mensagem reenvia a sessão inteira: contexto grande = mais tokens por mensagem.
1. **Estado em arquivo, não na conversa**: docs/handoff/PENDENTE.md (ledger), CONTEXTO-DO-DONO.md (quem é/o que pediu), docs/progbase/**/PROGRESSO*.md. Atualize antes de compactar; depois da compactação releia só esses.
2. **Delegue a subagentes** tudo que gera saída grande (logs, testes, decompilação, pesquisa); exija relatório ≤10 linhas; nunca cole saída bruta de teste/CI — use `tail -5`, `grep -c`, ou leia só o job que falhou.
3. **Não releia arquivos grandes**: use `graphify query`/`graphify path` (graphify-out) e grep com contexto curto.
4. **Compacte cedo** (/compact) ao fechar um marco (APK entregue, frente concluída), com instrução: "manter só objetivo, estado do PENDENTE, SHA/CI atuais, regras permanentes do dono".
5. Sessão nova para frente nova: comece por AGENTS.md → CONTEXTO-DO-DONO.md → PENDENTE.md.
