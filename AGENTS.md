# Contrato operacional estável

Este arquivo deve permanecer **curto e quase imutável**. Governança viva não pertence ao GitHub.

## Fonte de governança

- **Notion é o cérebro e a governança primária.** Antes de operar, o agente deve ler a governança atual do projeto no Notion e a regra global de economia/execução.
- Estado, prioridade, plano, decisão, autorização, branch ativa, exceção, aprendizado, roadmap e próximo passo ficam no **Notion**, não duplicados neste repositório.
- Não criar commits apenas para espelhar mudanças cotidianas de governança, memória ou planejamento do Notion.

## Fonte do código e execução remota

- **GitHub remoto é a verdade do estado atual do código e a bancada principal de execução.**
- O padrão é: ler remoto → editar remoto diretamente pelo mecanismo mais simples e seguro disponível → validar proporcionalmente ao risco → reler remoto → registrar checkpoint.
- Não criar clone, ZIP, worktree nem usar Git Database de baixo nível (`blob/tree/ref`) por ritual. Esses caminhos só entram quando uma necessidade técnica real impedir a edição remota simples ou exigir atomicidade especial.
- Para mudanças comuns de arquivo, preferir `fetch_file` + `update_file`/equivalente remoto e seguir.
- Ambiente local é auxiliar para testes, build ou ferramentas que realmente precisem de runtime local; não é pedágio obrigatório nem fonte paralela de verdade.
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
