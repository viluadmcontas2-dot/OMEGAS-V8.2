# Governança operacional obrigatória

## Regra principal

O proprietário opera este projeto exclusivamente em linguagem humana e não precisa conhecer nomes técnicos de GitHub Actions, CI, workflows, YAML, Gradle, runners, jobs ou comandos equivalentes.

Todo agente que trabalhar neste repositório deve traduzir a intenção do proprietário para a execução técnica mínima, suficiente, segura e econômica.

## Hierarquia de verdade

1. **Notion é o cérebro e a governança primária.** Missão, decisões, invariantes, prioridades, autorizações e regras operacionais vivem primeiro no Notion.
2. **GitHub remoto é a fonte oficial do estado atual do código.** Branch, commit, arquivos e histórico remotos atuais são a fotografia técnica oficial.
3. **A cópia local é apenas uma bancada temporária.** Nunca pode ser tratada como fonte de verdade independente nem como base confiável sem sincronização prévia com o remoto.
4. Este `AGENTS.md` é o **espelho operacional** da governança do Notion. Se houver divergência entre este arquivo e a governança atual do Notion, o agente deve reconciliar a regra antes de continuar.

## Sincronia com GitHub é prioridade máxima

Regras obrigatórias para qualquer agente:

- Antes de começar, consultar branch e commit remotos atuais.
- A cópia local deve nascer ou ser atualizada a partir dessa fotografia remota.
- Antes de editar, confirmar que o remoto não avançou desde a última leitura relevante.
- Não manter trabalho material preso apenas localmente por longos períodos. Publicar checkpoints coerentes na branch autorizada.
- Antes de qualquer push, conferir novamente se o remoto avançou e reconciliar conscientemente se necessário.
- Depois de cada push, confirmar qual branch e commit ficaram efetivamente no GitHub.
- Antes de concluir a tarefa, reler o estado remoto e comprovar que o GitHub contém exatamente o estado reportado ao proprietário.
- Em retomadas ou troca de agente, nunca confiar em pasta local antiga sem primeiro comparar e sincronizar com o GitHub.
- Se houver discrepância local/remoto, resolver a discrepância antes de continuar a implementação.

Manter o GitHub atualizado não significa fazer microcommits sem sentido. Significa não permitir deriva local prolongada: usar checkpoints pequenos o bastante para manter o remoto fresco, mas coerentes o bastante para preservar histórico, rollback e entendimento.

## Testes locais são a primeira opção obrigatória

**Todo teste que puder ser executado localmente com confiança suficiente deve ser executado localmente primeiro.**

Isso inclui, quando aplicável:

- análise estática;
- lint;
- testes unitários;
- testes de interface;
- validação de sintaxe;
- testes de contrato;
- build local;
- empacotamento local;
- reprodução de bugs;
- verificações de integração que não dependam de infraestrutura remota exclusiva.

GitHub Actions só deve ser usado quando o resultado realmente não puder ser obtido localmente com confiança suficiente, por exemplo quando depender de:

- ambiente remoto específico ou plataforma não disponível localmente;
- segredo ou credencial que não deve existir localmente;
- integração protegida exclusiva;
- assinatura, publicação ou deploy real;
- prova final remota materialmente necessária para a entrega.

Mesmo nesses casos, o agente deve pré-validar localmente tudo o que for possível antes de gastar Actions. **Conveniência, costume do projeto ou existência prévia de workflow não justificam usar Actions.**

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

## Sincronização sem desperdício

**GitHub remoto fresco + testes locais frequentes + Actions raros e justificáveis** é o estado desejado.

Se manter a branch atualizada estiver caro porque cada push dispara build ou suíte pesada, o agente deve corrigir a arquitetura da automação. Não deve deixar o GitHub desatualizado como forma de economizar.

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
