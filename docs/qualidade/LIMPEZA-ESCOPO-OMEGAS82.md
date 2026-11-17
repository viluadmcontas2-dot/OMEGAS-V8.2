# OMEGAS 8.2 — limpeza segura e inventário de legado (PR #195)

> Auditoria OBS-COD do Git tree completo no SHA `17b2a79a9e10c49f680e98dedc1fbe50789be0e0`. Limpeza é parte da qualidade, mas **não** autoriza apagar fontes/oráculos ou outras branches. Checkpoint sujeito a nova verificação depois de cada commit.

| Categoria | Resultado verificado | Decisão |
|---|---|---|
| Arquivos rastreados | 1692 (árvore não truncada) | Verificar dependências antes de deletar |
| Lixo óbvio `.bak/.old/.tmp/~`, `__pycache__`, `.pyc`, `node_modules`, `.DS_Store` | **0** | Nada seguro para excluir |
| Binários `.apk/.aab/.exe/.dll/.bin` rastreados | **0** | Nenhuma limpeza desta categoria |
| Pasta `docs/archive/` | 1 arquivo `HANDOFF-2026-10.md` | **KEEP**: registro histórico de decisões |
| Arquivos sob diretórios `evidence/evidencias/progbase` | 633 | **KEEP**: potencial oráculo de reversão; não deletar sem cruzar index/grafo |
| Branches remotas existentes | 26 (inclui OMEGASCANONICO, arquivos e linhas paralelas) | **FORA DO ESCOPO de exclusão**; única branch mutável neste programa = `feat/premium-adaptive-ui-v8-2` |

**Regra de saneamento:** candidato CLEANUP_SAFE exige referência/uso comprovado, teste RED/GREEN onde cabível, diff e CI verde do SHA. Se não houver prova, estado KEEP/INVESTIGAR; nada removido arbitrariamente. Métodos internos não anotados (por exemplo `readKFactorCurve`) não viram automaticamente lixo: o bug corrigido estava no inventário do grafo, não no código Kotlin. Nenhum dado da pesquisa ou comportamento físico foi alterado.

**Próximos achados de limpeza não destrutiva:** consolidar inconsistências entre docs de inventário, verificar referências antes de apagar, conservar versões antigas como histórico. Não force-push, não merge e não exclua branches de outros projetos.

## Reconciliação do ledger — KWriteManager (2026-11-17)

Confronto de `docs/handoff/PENDENTE.md` com código Kotlin e `KWriteManagerSafetyTest.kt` (CI #1983, SHA `8bd66da576b6dc280760ba5248497d9ff5bc3d85`):
- **DONE / OBS-COD e teste:** flag de K insertion marcada antes do envio; timeout da ativação faz saída segura uma vez e saída sem ACK mantém trava. Testes: `activationWithoutReplyStillSendsSafeExitOnce`, `activationAndExitWithoutReplyTryExitOnceThenLock`.
- **DONE / teste:** a liberação de `busy` por token no KWriteManager não libera outra operação (`finishingOperationDoesNotFreeAnotherOperationsBusy`). Não estender o veredito automaticamente ao KFactorManager.
- **BLOCKED / decisão:** "Gravado" após ACK/saída segura, com readback/correção subsequente, continua conforme decisão de 2026-10-19; plano novo conflita. Nenhuma mudança na escrita manual ou no writer.
- A pendência histórica já corrigida saiu do ledger; o item do KFactorManager e o conflito do ACK permaneceram. Não se remove evidência nem funcionalidade de reversão.

**Reconciliado também KFactorManager:** `KFactorManagerBusyTest.kt::finishingOperationDoesNotFreeAnotherOperationsBusy` e `OwnedBusyLock` com token demonstram que encerramento da conferência da operação A não libera o ocupado da operação B. Kotlin CI #1985 passou. A antiga pendência de dupla liberação como *risco não resolvido* foi retirada; código nativo não foi alterado. A semântica "Gravado" de ambos os escritores ainda depende de decisão expressa.
