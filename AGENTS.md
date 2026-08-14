# Contrato operacional estável

## Gate obrigatório do agente

Antes de qualquer operação técnica, carregar `@governar-projeto` (referência interna: `$governar-projeto`). Projeto técnico novo ou incompleto exige `@preparar-projeto`; falha de escrita remota chama `@destravar-github`. Resolver skills pelo nome instalado, nunca por caminho `/root/...` ou diretório `skill-*`. Se a skill não estiver exposta, abrir o [Project Governance Guard — Master Checkpoint](https://www.notion.so/3ba8ee52ac5481bfb69bc53a483aad53) e permanecer fail-closed.

A sequência de engenharia é obrigatória: `@Codex Engineering Guardrails` → skill oficial direta `code-work` ou `code-verification` → [fallback integral no Notion](https://www.notion.so/3ba8ee52ac548106ad70da67a2621ea5). Antes de repetir operação conhecida, consultar o [Runbook Técnico](https://www.notion.so/f5c5e3d2a12e42feb36d25ebf8b0b7f8).

Contrato estável: `WORK_SURFACE=REMOTE`, `SOURCE_MUTATION_TARGET=GITHUB_REMOTE_API`, `TEST_SURFACE=EPHEMERAL_RUNTIME|REMOTE_CI|NOT_AVAILABLE`, `LOCAL_SOURCE_MUTATION=DENIED`, `SYNC_STEP=NONE`. Falha, urgência, conveniência ou autorização durante a tarefa nunca liberam edição local. Sem escrita remota segura, bloquear.

Este arquivo deve permanecer **curto e quase imutável**. Governança viva não pertence ao GitHub.

## Fonte de governança

- **Notion é o cérebro e a governança primária.** Antes de operar, o agente deve ler a governança atual do projeto no Notion e a regra global de economia/execução.
- Estado, prioridade, plano, decisão, autorização, branch ativa, exceção, aprendizado, roadmap e próximo passo ficam no **Notion**, não duplicados neste repositório.
- Não criar commits apenas para espelhar mudanças cotidianas de governança, memória ou planejamento do Notion.

## Fonte do código e execução remota

- **GitHub remoto é a verdade do estado atual e a única superfície de mutação do código.**
- A regra é: ler remoto → editar pelo GitHub Connector/API → reler o novo SHA → validar proporcionalmente ao risco → registrar checkpoint.
- Não criar clone, ZIP, worktree nem usar Git Database de baixo nível (`blob/tree/ref`) por ritual. Esses caminhos só entram quando uma necessidade técnica real impedir a edição remota simples ou exigir atomicidade especial.
- Para mudanças comuns de arquivo, preferir `fetch_file` + `update_file`/equivalente remoto e seguir.
- Runtime efêmero serve somente para testar/buildar o SHA remoto e deve terminar sem alteração de fonte, configuração ou lockfiles. Sem runtime, usar CI remoto apenas quando necessário e autorizado; caso contrário registrar `TEST_NOT_AVAILABLE`.
- **Tempo do proprietário é recurso crítico.** Entre rotas com segurança equivalente, escolher a que termina com menos passos, menos espera e menos revalidação redundante.

## Testes e Actions

- Validar com a prova mínima suficiente para o risco da mudança.
- GitHub Actions só devem ser usadas quando houver dependência real de ambiente remoto, segredo protegido, publicação/deploy, assinatura ou outra prova que não possa ser obtida de forma mais simples com confiança suficiente.
- **Edição/push remoto comum deve consumir zero Actions pesadas por padrão.** Se uma alteração comum acordar build/workflow caro sem necessidade, tratar o gatilho como defeito de automação e corrigi-lo em escopo próprio.
- Não repetir auditoria, snapshot, hash, teste ou reconciliação já válidos sem evidência nova que os invalide.

## Comunicação

- O proprietário opera em linguagem humana. O agente traduz a intenção para a execução técnica adequada sem exigir nomes de comandos, workflows ou sintaxe.
- Em execução longa ou multi-etapa, manter heartbeats curtos no chat por evento material e checkpoints correspondentes no Notion.

## Regra de alteração deste arquivo

Só alterar este arquivo quando uma **invariante durável** mudar. Se a informação puder mudar com frequência, ela pertence ao Notion.
