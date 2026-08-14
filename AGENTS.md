# Contrato operacional estável

## Gate obrigatório do agente

Antes de qualquer operação técnica, carregar **Operational Governance** (skill `$operational-governance`). Projeto técnico novo ou incompleto exige `$governed-project-bootstrap`, que cria/vincula Central, repositório e `AGENTS.md` remotamente antes do código, resolvendo a conta conectada ao vivo. Se o plugin não estiver disponível, usar `$project-governance-guard`; se nenhuma skill estiver acessível, abrir o [Project Governance Guard — Master Checkpoint](https://www.notion.so/3ba8ee52ac5481bfb69bc53a483aad53) e permanecer fail-closed.

A sequência de engenharia é obrigatória: `@Codex Engineering Guardrails` → skill oficial direta `code-work` ou `code-verification` → [fallback integral no Notion](https://www.notion.so/3ba8ee52ac548106ad70da67a2621ea5). Antes de repetir operação conhecida, consultar o [Runbook Técnico](https://www.notion.so/f5c5e3d2a12e42feb36d25ebf8b0b7f8).

Contrato estável: `WORK_SURFACE=REMOTE`, `TEST_SURFACE=LOCAL`, `LOCAL_SOURCE_MUTATION=DENIED`, `SYNC_STEP=NONE`. Falha de escrita remota ativa recuperação remota; nunca autoriza acumular diff local.

Este arquivo deve permanecer **curto e quase imutável**. Governança viva não pertence ao GitHub.

## Fonte de governança

- **Notion é o cérebro e a governança primária.** Antes de operar, o agente deve ler a governança atual do projeto no Notion e a regra global de economia/execução.
- Estado, prioridade, plano, decisão, autorização, branch ativa, exceção, aprendizado, roadmap e próximo passo ficam no **Notion**, não duplicados neste repositório.
- Não criar commits apenas para espelhar mudanças cotidianas de governança, memória ou planejamento do Notion.
- **Checkpoint ativo não pode se contradizer.** Título, propriedades, corpo, estado e próximo passo do handoff atual devem apontar para a mesma realidade.

## Fonte do código e execução remota

- **GitHub remoto é a verdade do estado atual do código e a bancada principal de execução.**
- O padrão é: ler remoto → editar remoto diretamente pelo mecanismo mais simples e seguro disponível → validar proporcionalmente ao risco → reler remoto → registrar checkpoint.
- Não criar clone, ZIP, worktree nem usar Git Database de baixo nível (`blob/tree/ref`) por ritual. Esses caminhos só entram quando uma necessidade técnica real impedir a edição remota simples ou exigir atomicidade que ela não preserve.
- Para mudanças comuns de arquivo, preferir `fetch_file` + `update_file`/equivalente remoto e seguir.
- Ambiente local é auxiliar para testes, build ou ferramentas que realmente precisem de runtime local; não é pedágio obrigatório nem fonte paralela de verdade.
- Antes de uma escrita relevante e antes de concluir, revalidar branch/HEAD remoto. Se houver mudança concorrente, reconciliar sem sobrescrever trabalho alheio.
- **Tempo do proprietário é recurso crítico.** Entre rotas com segurança equivalente, escolher a que termina com menos passos, menos espera e menos revalidação redundante.

## Testes e Actions

- Validar com a prova mínima suficiente para o risco da mudança; teste local entra quando realmente necessário para provar comportamento que leitura/contrato remoto não prova.
- GitHub Actions só devem ser usadas quando houver dependência real de ambiente remoto, segredo protegido, publicação/deploy, assinatura ou outra prova que não possa ser obtida de forma mais simples com confiança suficiente.
- **Edição/push remoto comum deve consumir zero Actions pesadas por padrão.** Se uma alteração comum acordar build/workflow caro sem necessidade, tratar o gatilho como defeito de automação e corrigi-lo em escopo próprio.
- Não repetir auditoria, snapshot, hash, teste ou reconciliação já válidos sem evidência nova que os invalide.

## Comunicação

- O proprietário opera em linguagem humana. O agente traduz a intenção para a execução técnica adequada sem exigir nomes de comandos, workflows ou sintaxe.
- Em execução técnica longa ou multi-etapa, manter **heartbeats visíveis no chat por evento material** e registrar no Notion as descobertas, bloqueios, mudanças de direção e fechamentos relevantes no mesmo bloco.
- Heartbeat deve ser curto e imediato após mudança material de estado; não substituir execução por spam de progresso.

## Regra de alteração deste arquivo

Só alterar este arquivo quando uma **invariante durável** mudar. Se a informação puder mudar com frequência, ela pertence ao Notion.
