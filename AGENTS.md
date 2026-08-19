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
- **Checkpoint ativo não pode se contradizer.** Título, propriedades, corpo, estado e próximo passo do handoff atual devem apontar para a mesma realidade.

## Fonte do código e execução remota

- **GitHub remoto é a verdade do estado atual e a única superfície de mutação do código.**
- A regra é: ler remoto → editar pelo GitHub Connector/API → reler o novo SHA → validar proporcionalmente ao risco → registrar checkpoint.
- Não criar clone, ZIP, worktree nem usar Git Database de baixo nível (`blob/tree/ref`) por ritual. Esses caminhos só entram quando uma necessidade técnica real impedir a edição remota simples ou exigir atomicidade que ela não preserve.
- Para mudanças comuns de arquivo, preferir `fetch_file` + `update_file`/equivalente remoto e seguir.
- Runtime efêmero serve somente para testar/buildar o SHA remoto e deve terminar sem alteração de fonte, configuração ou lockfiles. Sem runtime, usar CI remoto apenas quando necessário e autorizado; caso contrário registrar `TEST_NOT_AVAILABLE`.
- Antes de uma escrita relevante e antes de concluir, revalidar branch/HEAD remoto. Se houver mudança concorrente, reconciliar sem sobrescrever trabalho alheio.
- **Tempo do proprietário é recurso crítico.** Entre rotas com segurança equivalente, escolher a que termina com menos passos, menos espera e menos revalidação redundante.

## Testes e Actions

- Validar com a prova mínima suficiente para o risco e a relevância da mudança. Começar pelo comportamento alterado e expandir somente para consumidores, dependências, invariantes e superfícies com interferência plausível.
- Classificar a validação por impacto: mudança documental/diagnóstica → inspeção e contrato; lógica isolada → teste focado + consumidores diretos; estado/concurrency/persistência → regressão + componente + falhas; USB/ECU/writer/protocolo/segurança/migração → contratos amplos, integração e validação física quando aplicável; gate de fase/PREAPK/APK → auditoria ampla definida pelo Programa.
- **Todo teste que concede avanço deve ter PASS CONTRACT explícito antes de ser tratado como gate.** O contrato deve registrar: alvo/SHA, risco coberto, critérios observáveis, testes necessários, resultado esperado, dependências cruzadas, limites do que não foi provado e eventos que invalidam a evidência.
- Estados permitidos do gate: `PASS`, `PARTIAL`, `FAIL` ou `INCONCLUSIVE`. `PASS` exige evidência fresca suficiente para todos os critérios obrigatórios do contrato e ausência de achado material aberto. `PARTIAL`, `FAIL`, `INCONCLUSIVE` e `TEST_NOT_AVAILABLE` nunca liberam dependente material.
- Resultado verde isolado não é PASS por si só. Teste estrutural/string/grep não substitui prova comportamental quando o comportamento é executável; teste antigo não prova SHA novo; CI verde prova somente o que efetivamente executou.
- Evidência continua válida enquanto não houver mudança no código, contrato, dependência ou ambiente que possa afetar o risco coberto. Não repetir auditoria/teste ainda válido sem evidência nova que o invalide.
- Teste local entra quando realmente necessário para provar comportamento que leitura/contrato remoto não prova. Runtime efêmero é read-only para fonte e deve testar o SHA remoto exato.
- GitHub Actions só devem ser usadas quando houver dependência real de ambiente remoto, segredo protegido, publicação/deploy, assinatura ou outra prova que não possa ser obtida de forma mais simples com confiança suficiente.
- **Edição/push remoto comum deve consumir zero Actions pesadas por padrão.** Se uma alteração comum acordar build/workflow caro sem necessidade, tratar o gatilho como defeito de automação e corrigi-lo em escopo próprio.

## TRANSVERSAL PASS/FAIL obrigatório

- Antes de qualquer verdict que possa liberar dependente material, executor **e auditor independente** devem carregar `docs/contracts/transversal-pass-fail-gate.json` e `docs/contracts/TRANSVERSAL_PASS_FAIL_GATE.md`.
- O receipt deve classificar, uma a uma, as fontes obrigatórias `Programa Mestre`, `MASTER TRACE MAP`, `AL-001`, `AL-002`, `AL-003`, `AL-004` e `HW-001` como `APPLIES` ou `NOT_APPLICABLE_WITH_REASON`, e anexar evidência para toda obrigação aplicável.
- Falta de leitura, aplicabilidade não classificada, requirement aplicável sem evidência, helper sem consumidor real, teste apenas string/grep para comportamento executável, benchmark host apresentado como RK3326, Prediction usada como Observation, harness quebrado usado como prova ou **implementador concedendo PASS ao próprio owner** tornam o gate automaticamente não-PASS.
- Estados adicionais obrigatórios: `STALE_BY_GOVERNANCE` e `STALE_BY_EVIDENCE`. Qualquer PASS histórico sem receipt transversal + auditor independente vira `STALE_BY_GOVERNANCE` até reauditoria no SHA remoto exato.
- Só `PASS` fresco do **auditor independente**, após o gate transversal completo, libera owner/extensão/gate dependente. Nenhuma urgência, sequência numérica ou CI verde substitui essa regra.

## Comunicação

- O proprietário opera em linguagem humana. O agente traduz a intenção para a execução técnica adequada sem exigir nomes de comandos, workflows ou sintaxe.
- Em execução técnica longa ou multi-etapa, manter **heartbeats visíveis no chat por evento material** e registrar no Notion as descobertas, bloqueios, mudanças de direção e fechamentos relevantes no mesmo bloco.
- Heartbeat deve ser curto e imediato após mudança material de estado; não substituir execução por spam de progresso.

## Regra de alteração deste arquivo

Só alterar este arquivo quando uma **invariante durável** mudar. Se a informação puder mudar com frequência, ela pertence ao Notion.
