# Contrato operacional estável

Este arquivo deve permanecer **curto e quase imutável**. Governança viva não pertence ao GitHub.

## Fonte de governança

- **Notion é o cérebro e a governança primária.** Antes de operar, o agente deve ler a governança atual do projeto no Notion e a regra global de economia/execução.
- Estado, prioridade, plano, decisão, autorização, branch ativa, exceção, aprendizado, roadmap e próximo passo ficam no **Notion**, não duplicados neste repositório.
- Não criar commits apenas para espelhar mudanças de governança, memória ou planejamento do Notion.

## Fonte do código

- **GitHub remoto é a verdade do estado atual do código.**
- A cópia local é temporária: deve partir do remoto atual e nunca ser tratada como fonte independente.
- Antes de começar, antes de publicar e antes de concluir, conferir branch e commit remotos e reconciliar qualquer discrepância.
- Não deixar trabalho material preso apenas localmente por longos períodos; manter a branch autorizada tecnicamente atualizada com checkpoints coerentes.

## Testes e Actions

- **Testar localmente é a primeira opção obrigatória.** Se puder ser provado localmente com confiança suficiente, não usar GitHub Actions.
- Actions só devem ser usadas quando houver dependência real de ambiente remoto, segredo protegido, publicação/deploy, assinatura ou outra prova que não possa ser obtida localmente com confiança suficiente.
- Mesmo quando Actions forem necessárias, pré-validar localmente tudo que for possível.
- Não criar automações pesadas por padrão, não disparar builds por documentação/governança e não permitir loops de automação.

## Comunicação

O proprietário opera em linguagem humana. O agente traduz a intenção para a execução técnica adequada sem exigir nomes de comandos, workflows ou sintaxe.

## Regra de alteração deste arquivo

Só alterar este arquivo quando uma **invariante durável** mudar. Se a informação puder mudar com frequência, ela pertence ao Notion.
