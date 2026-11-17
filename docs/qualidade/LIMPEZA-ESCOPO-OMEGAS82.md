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
