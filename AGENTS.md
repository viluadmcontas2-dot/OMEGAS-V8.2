# Governança operacional obrigatória

## Regra principal

O proprietário opera este projeto exclusivamente em linguagem humana e não precisa conhecer nomes técnicos de GitHub Actions, CI, workflows, YAML, Gradle, runners, jobs ou comandos equivalentes.

Todo agente que trabalhar neste repositório deve traduzir a intenção do proprietário para a execução técnica mínima, suficiente, segura e econômica.

## Economia de GitHub Actions é prioridade

GitHub Actions é orçamento finito. Não desperdiçar minutos de execução.

Regras obrigatórias:

- Não executar build completo, empacotamento, APK, bundle, publicação ou suíte pesada automaticamente a cada commit.
- Alterações de documentação, governança, textos, imagens, planejamento ou metadados não devem disparar tarefas pesadas.
- Microcommits sucessivos não justificam repetir a mesma validação pesada.
- Quando houver várias alterações em sequência, validar de forma barata durante o trabalho e reservar a validação pesada para o checkpoint que realmente precise dela.
- Execuções antigas e superadas devem ser canceladas quando possível.
- Evitar workflows duplicados validando o mesmo estado por gatilhos diferentes.
- Evitar qualquer automação que faça commit automático e provoque novas automações em cascata.
- Todo workflow deve ter limite de tempo coerente e filtro de escopo.
- Preferir testes específicos e baratos antes de testes amplos.
- Publicação, build, teste e alteração são operações distintas. Uma não deve implicar automaticamente as outras.
- Se o proprietário disser em linguagem humana algo como “me entregue o APK”, “publique”, “teste”, “execute” ou “continue”, o agente decide internamente a forma técnica adequada. Nunca exigir que o proprietário peça um nome de workflow ou comando técnico.

## Fronteira de segurança

Economia não autoriza pular uma validação material necessária para segurança, integridade, compatibilidade ou entrega correta. A obrigação é escolher a prova mais barata que ainda seja suficiente.

## Antes de criar ou alterar automações

O agente deve responder internamente:

1. O resultado pedido realmente exige ligar uma automação remota?
2. Existe teste local, estático ou mais específico que prove a mesma coisa com menor custo?
3. Esta automação será disparada por mudanças irrelevantes?
4. Pode haver duplicação com outro workflow?
5. Há risco de loop entre automação, commit e nova automação?
6. Existe cancelamento de execução obsoleta e limite de tempo?
7. O artefato gerado é realmente necessário agora?

Se qualquer resposta indicar desperdício evitável, corrigir a arquitetura antes de ativar a automação.

## Regra para agentes futuros

Nenhum agente deve pedir ao proprietário para aprender ou escrever sintaxe técnica para economizar Actions. Essa responsabilidade é do agente.
