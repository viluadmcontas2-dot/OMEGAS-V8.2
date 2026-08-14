# Contrato operacional estável

Este arquivo deve permanecer **curto e quase imutável**. Governança viva não pertence ao GitHub.

## Fonte de governança

- **Notion é o cérebro e a governança primária.** Antes de operar, o agente deve ler a governança atual do projeto no Notion e a regra global de economia/execução.
- Estado, prioridade, plano, decisão, autorização, branch ativa, exceção, aprendizado, roadmap e próximo passo ficam no **Notion**, não duplicados neste repositório.
- Não criar commits apenas para espelhar mudanças de governança, memória ou planejamento do Notion.

## Fonte do código e escrita remota

- **GitHub remoto é a verdade do estado atual do código.**
- A cópia local é bancada temporária e descartável: deve nascer do branch/commit remoto recém-validado e nunca ser tratada como fonte independente.
- Antes de começar, antes de materializar qualquer mudança e antes de concluir, conferir branch e HEAD remotos; se o HEAD mudou, abortar a escrita e reconciliar antes de continuar.
- Alterações autoritativas devem ser materializadas na branch autorizada pelo **GitHub remoto**. `git push` da bancada local não é o mecanismo normal de publicação.
- Para uma mudança pequena de arquivo único, a Contents API remota é aceitável. Para um pacote coerente de vários arquivos, preferir uma única transação lógica pela Git Database API: criar/reutilizar blobs → montar tree/subtree → criar um commit com o HEAD validado como parent → revalidar o HEAD → mover a ref uma única vez, sem `force` → reler commit/tree e provar os hashes.
- Nunca mover a ref antes de a árvore candidata estar pronta. Falha local, de ZIP, ferramenta ou teste deve deixar a branch remota no último estado válido; objetos Git órfãos não contam como publicação.
- Trabalho local serve para editar, comparar, calcular hashes e testar. O recibo final vem sempre do estado remoto relido após a escrita.

## Testes e Actions

- **Testar localmente é a primeira opção obrigatória.** Se puder ser provado localmente com confiança suficiente, não usar GitHub Actions.
- Actions só devem ser usadas quando houver dependência real de ambiente remoto, segredo protegido, publicação/deploy, assinatura ou outra prova que não possa ser obtida localmente com confiança suficiente.
- Mesmo quando Actions forem necessárias, pré-validar localmente tudo que for possível.
- Não criar automações pesadas por padrão, não disparar builds por documentação/governança e não permitir loops de automação.

## Comunicação

O proprietário opera em linguagem humana. O agente traduz a intenção para a execução técnica adequada sem exigir nomes de comandos, workflows ou sintaxe.

## Regra de alteração deste arquivo

Só alterar este arquivo quando uma **invariante durável** mudar. Se a informação puder mudar com frequência, ela pertence ao Notion.
