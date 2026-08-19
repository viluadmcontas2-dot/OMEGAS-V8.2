# Contrato operacional estável

## Gate obrigatório do agente

Antes de qualquer operação técnica, carregar `@governar-projeto` (referência interna: `$governar-projeto`). Projeto técnico novo ou incompleto exige `@preparar-projeto`; falha de escrita remota chama `@destravar-github`. Resolver skills pelo nome instalado, nunca por caminho `/root/...` ou diretório `skill-*`. Se a skill não estiver exposta, abrir o [GOVERNANCE ENTRYPOINT — CANONICAL](https://app.notion.com/p/3c18ee52ac548182a850f533dfbc8e84) e permanecer fail-closed. O EntryPoint carrega o **GOVERNANCE KERNEL**, resolve `PROJECT_ID`, registries e authority; `Project Governance Guard` e `Operational Governance` antigos estão SUPERSEDED e nunca são bootstrap.

A sequência de engenharia é obrigatória: `@Codex Engineering Guardrails` → skill oficial direta `code-work` ou `code-verification` → [fallback integral no Notion](https://www.notion.so/3ba8ee52ac548106ad70da67a2621ea5). Antes de repetir operação conhecida, consultar o [Runbook Técnico](https://www.notion.so/f5c5e3d2a12e42feb36d25ebf8b0b7f8).

Contrato estável: `WORK_SURFACE=REMOTE`, `SOURCE_MUTATION_TARGET=GITHUB_REMOTE_API`, `TEST_SURFACE=EPHEMERAL_RUNTIME|REMOTE_CI|NOT_AVAILABLE`, `LOCAL_SOURCE_MUTATION=DENIED`, `SYNC_STEP=NONE`. Falha, urgência, conveniência ou autorização durante a tarefa nunca liberam edição local. Sem escrita remota segura, bloquear.

Este arquivo deve permanecer **curto e quase imutável**. Governança viva não pertence ao GitHub.

## Fonte de governança, execução e código

- **Notion é o cérebro durável e a governança primária.** Todo boot passa pelo `GOVERNANCE ENTRYPOINT`, que carrega o `GOVERNANCE KERNEL`, resolve `PROJECT_ID`, Project Registry, Contract Registry, Capability Registry e `GLOBAL-LEDGER-001`.
- **A autoridade operacional mutável é resolvida pelo Project Registry.** Quando o Registry declarar `EXECUTION_AUTHORITY=LINEAR`, estado operacional, prioridade, fila, owner, blockers e próximo passo ficam no **Linear**; o Notion permanece conhecimento/governança/requisitos/arquitetura/contratos/decisões/evidência e não vira dual writer. Se o Registry declarar outra autoridade, seguir exatamente o binding vigente.
- **GitHub remoto é a autoridade do source.** Branch ativa e SHA para qualquer claim técnico devem ser reabertos no remoto; nunca inferir `main` ou branch por nome.
- Aprendizados, decisões duráveis, contratos e evidência consolidada permanecem no Notion; projeções operacionais ficam na autoridade de execução resolvida, sem duplicar authority.
- Não criar commits apenas para espelhar mudanças cotidianas de governança, memória ou planejamento.
- **Checkpoint ativo não pode se contradizer.** Registry, roteador humano, execution writer, branch/SHA, Ledger e próximo gate devem apontar para a mesma realidade; conflito material = fail-closed até reconciliação.

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

- Antes de qualquer verdict que possa liberar dependente material, executor **e auditor independente** devem bootar pelo Governance EntryPoint, resolver o `CONTRACT REGISTRY`, carregar `GLOBAL-LEDGER-001`, `docs/contracts/transversal-pass-fail-gate.json` e `docs/contracts/TRANSVERSAL_PASS_FAIL_GATE.md`.
- O receipt deve conter `COVERAGE_MANIFEST` e classificar **todo contrato ACTIVE/retroativo aplicável** como `APPLIES` ou `NOT_APPLICABLE_WITH_REASON`. A lista mínima OMEGAS inclui Programa Mestre, MASTER TRACE MAP, GS-001, OME-EVIDENCE-PROVENANCE/Evidence Lab+Execution Ledger, AL-001, AL-002, AL-003, **AL-003A**, AL-004, OME-ADP-001 e HW-001. Lista mínima nunca limita contratos adicionais do Registry.
- Se o owner tocar superfície humana/visível/interativa, deve resolver e classificar explicitamente `OME-STATE-HUMAN-UI`, `UIUX-CUSTOMROM` e `UIUX-OMEGADEV` ou registrar `NOT_APPLICABLE_WITH_REASON`.
- Falta de leitura, child/subpage/slice material não enumerado, aplicabilidade não classificada, requirement aplicável sem evidência, helper sem consumidor real, teste apenas string/grep para comportamento executável, benchmark host apresentado como RK3326, Prediction usada como Observation, harness quebrado usado como prova ou **implementador concedendo PASS ao próprio owner** tornam o gate automaticamente não-PASS.
- Estados adicionais obrigatórios: `STALE_BY_GOVERNANCE` e `STALE_BY_EVIDENCE`. Qualquer PASS histórico sem receipt transversal + auditor independente vira `STALE_BY_GOVERNANCE` até reauditoria no SHA remoto exato.
- Implementação/migração fecha no máximo em `IMPLEMENTED_AWAITING_AUDIT`; verdict final requer auditoria independente e `META_AUDIT=PASS` sobre a própria cobertura/auditoria. Só `PASS` fresco do **auditor independente**, após o gate transversal completo, libera owner/extensão/gate dependente. Nenhuma urgência, sequência numérica ou CI verde substitui essa regra.
- Em source/texto com bytes acessíveis, auditoria material lê integralmente a superfície relevante. Em Notion/Linear/conectores estruturados sem bytes crus, usar `block-complete/object-complete` + enumeração de children/subpages/databases e nunca alegar literal byte-a-byte sem os bytes.

## Comunicação

- O proprietário opera em linguagem humana. O agente traduz a intenção para a execução técnica adequada sem exigir nomes de comandos, workflows ou sintaxe.
- Em execução técnica longa ou multi-etapa, manter **heartbeats visíveis no chat por evento material** e registrar no Notion as descobertas, bloqueios, mudanças de direção e fechamentos relevantes no mesmo bloco.
- Heartbeat deve ser curto e imediato após mudança material de estado; não substituir execução por spam de progresso.

## Regra de alteração deste arquivo

Só alterar este arquivo quando uma **invariante durável** mudar. Se a informação puder mudar com frequência, ela pertence ao Notion/à autoridade operacional resolvida, não a este arquivo.
