# Contrato operacional estável

## Autoridade e bootstrap

```plain text
REMOTE_FIRST=ALWAYS
REPO_FIRST=TRUE
WORK_SURFACE=GITHUB_REMOTE
SOURCE_MUTATION_TARGET=GITHUB_REMOTE_API
LOCAL_SOURCE_MUTATION=DENIED
LOCAL_SOURCE_AUTHORITY=DENIED
EXTERNAL_EXECUTION_TRACKER=RETIRED
CHAT_MEMORY_IS_AUTHORITY=FALSE
```

Para qualquer trabalho source-bearing:

1. resolver a branch autorizada e o HEAD **remoto** atual;
2. ler este `AGENTS.md`;
3. ler o STATUS canônico aplicável e o WorkUnit ativo no repositório;
4. resolver a GitHub Issue/PR lineage correspondente;
5. só então executar diagnóstico, mudança, teste e verificação.

O repositório remoto é a autoridade de engenharia. WorkUnit + GitHub Issue/PR formam a linhagem executável. Nenhum tracker externo escolhe current, branch, SHA, gate ou próximo passo.

Notion é referência opcional para estratégia, business, produto, UX e decisões humanas quando isso puder mudar materialmente a execução. Não é pré-requisito universal para source mutation.

Brainbase/BRASKO é bootstrap read-only de governança global; não executa trabalho e não substitui o repositório.

## Engenharia

A sequência metodológica é:

`Codex Engineering Guardrails → skill aplicável → implementação/verificação proporcional`.

- source mutation: usar `code-work`;
- auditoria, teste, diagnóstico ou review read-only: usar `code-verification`;
- Superpowers entra somente com a skill de processo realmente aplicável;
- não criar arquitetura paralela, plano paralelo ou estado executivo fora do repo.

Antes de construir, procurar primeiro o artefato original, protocolo, código, fixture, teste, binário ou dado que possa revelar diretamente a verdade.

## Escrita remota

- Código nasce no GitHub remoto.
- Não criar clone/worktree/local checkout como fonte de autoridade.
- Runtime local/efêmero, quando disponível, serve apenas para testar o SHA remoto exato.
- Não publicar alteração originada localmente.
- Para mudança comum, preferir leitura remota → edição pela GitHub API → releitura do novo SHA.
- Antes de write material e antes de concluir, revalidar HEAD remoto e reconciliar concorrência.

## Evidência e CI

Implementação não é prova.

A cada slice:

`diagnóstico → evidência → mudança mínima → teste → commit remoto → releitura do SHA → gate proporcional`.

GitHub Actions é usada quando fornece evidência necessária que não pode ser obtida com confiança equivalente por uma prova mais barata. Não repetir CI do mesmo SHA sem nova hipótese/invalidation.

Se CI ficar vermelha, parar no primeiro gate quebrado, corrigir causa e não empilhar mudança nova.

## Continuidade OMEGAS

Para a linha Amarelo, a retomada começa em:

`docs/omegas-amarelo/STATUS.md → docs/workunits/OMEGAS-AMARELO-WU-*.md → GitHub issue correspondente`.

Autoridade científica do AutoCAL permanece:

1. ProgBase original + Portmons crus;
2. código/fixtures/testes canônicos do branch Amarelo;
3. CI/evidência no SHA remoto exato;
4. Verde apenas HARVEST/REVALIDATE.

Não gerar APK sem autorização explícita.

## Comunicação

O proprietário opera em linguagem humana. Em execução longa, reportar somente checkpoints materiais, bloqueios reais e decisões que mudem a rota.

Este arquivo deve permanecer curto e conter apenas invariantes duráveis. Estado mutável pertence aos STATUS/WorkUnits/issues do repositório.
