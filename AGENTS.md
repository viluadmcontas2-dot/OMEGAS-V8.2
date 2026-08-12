<!-- OMEGAS_REOPS_INTEGRATION_VERSION: 2026-08-09.1 -->
# OMEGAS V8 — regras obrigatórias globais

## Fonte oficial e alcance
Estas regras valem para todo arquivo, toda branch existente após sincronização, toda branch futura, todo PR e toda automação do repositório `viluadmcontas-alt/OMEGAS-V8`.

**OMEGAS V8 é o repositório funcional e de trabalho oficial atual.** O repositório `felipetbestkkj-ship-it/OMEGAS-V7` é apenas origem histórica da migração e pode ser consultado para proveniência quando necessário; não substitui o estado vivo do V8.

OMEGAS V8 é o aplicativo Android para leitura, aprendizado, diagnóstico e ajuste manual assistido de centrais GNV OMEGAS/MP48.

## Arquitetura de governança e skills
Este repositório usa três camadas complementares:
1. governança local OMEGAS V8;
2. Codex Engineering Guardrails como camada técnica obrigatória de governo (`code-verification` ou `code-work`);
3. REOPS fixado por `.reops-lock.yaml` e demais skills auxiliares.

A governança local não é substituída pelo REOPS. Skill, plugin ou agente não pode ampliar autoridade, enfraquecer invariantes, autorizar escrita automática na ECU ou autorizar publicação.

### Precedência obrigatória do Codex Engineering Guardrails
Para toda ação técnica envolvendo código, programação, testes, documentação técnica ou GitHub, a ordem abaixo é obrigatória:
1. tentar usar diretamente o plugin nominal **Codex Engineering Guardrails**;
2. se o plugin não estiver disponível ou invocável, localizar, carregar e usar **a skill direta do próprio Codex Engineering Guardrails** como fallback prioritário;
3. somente depois de o Guardrails estar resolvido, selecionar o modo aplicável (`code-verification` para inspeção/diagnóstico sem alteração; `code-work` para mudança autorizada) e carregar as demais skills auxiliares necessárias;
4. REOPS, `evidence-verification`, `guarded-skill-resolver`, `adaptive-execution-orchestrator` ou qualquer outra skill auxiliar **não substituem** a skill direta do Codex Engineering Guardrails quando ela puder ser resolvida;
5. se nem o plugin nem a skill direta do Codex Engineering Guardrails puderem ser resolvidos, registrar a limitação real antes de recorrer às skills auxiliares disponíveis; nunca declarar que uma skill auxiliar é equivalente ao Guardrails.

É proibido começar pelas skills auxiliares e só depois procurar o Codex Engineering Guardrails. A precedência é sempre:
`plugin Codex Engineering Guardrails → skill direta Codex Engineering Guardrails → skills auxiliares/REOPS`.

Antes de qualquer ação técnica:
1. leia `AGENTS.md` e `.reops-lock.yaml`;
2. resolva o Codex Engineering Guardrails seguindo obrigatoriamente a precedência `plugin → skill direta`;
3. selecione o modo aplicável do Guardrails: `code-verification` ou `code-work`;
4. resolva o REOPS no commit exato do lock;
5. carregue `evidence-verification` e `guarded-skill-resolver`;
6. selecione somente as demais skills necessárias;
7. leia `START_HERE.md`, `SKILLS.md`, `TESTING_RULES.md`, `docs/TEST_STRATEGY.md`, `LEARNING_RULES.md` quando aplicável, `PROJECT_STATE.md`, `DECISIONS.md` e `CAPABILITY_MATRIX.md`.

Se o plugin nominal do Guardrails não estiver disponível, isso **não autoriza** pular diretamente para REOPS ou skills auxiliares: a próxima tentativa obrigatória é a skill direta do próprio Codex Engineering Guardrails. Somente após indisponibilidade comprovada de ambos pode-se recorrer às skills auxiliares disponíveis, registrando explicitamente a limitação. Nunca substitua evidência por memória.

## Ferramentas
- `code-verification`: auditoria, diagnóstico, investigação e testes sem alteração.
- `code-work`: qualquer mudança autorizada.
- GitHub: fonte primária para repositório, branches, commits, PRs, CI, artifacts, APKs e releases.
- Notion: governança operacional, checkpoints e decisões; não substitui GitHub como prova do código atual.

## Trabalho remoto primeiro
- Todo trabalho de código, documentação, teste e governança deve acontecer diretamente na branch remota do GitHub sempre que tecnicamente suportado.
- Arquivo local, ZIP, pasta temporária ou memória são apenas apoio, não estado oficial.
- Uma mudança só pode ser apresentada como aplicada quando estiver confirmada na branch remota com commit identificável.
- Antes de começar, confirme repositório, branch e commit; depois confirme novamente estado remoto e CI aplicável.
- Esta regra não autoriza `main`, merge, PR, tag, release, APK, deploy, Netlify ou publicação.

## Autorizações sem repetição
- Uma autorização explícita para aplicar um bloco cobre edições, testes, documentação e commits necessários nessa branch de trabalho.
- Não pedir nova autorização para etapas internas já contidas no objetivo aprovado.
- Perguntar novamente somente quando surgir mudança real de produto, aumento material de risco ou fronteira externa ainda não autorizada.
- Evitar microautorizações.

## Fontes de verdade
1. ordem explícita mais recente do proprietário;
2. `AGENTS.md`, `.reops-lock.yaml` e regras locais;
3. Guardrails/REOPS fixados;
4. GitHub e evidência viva;
5. `PROJECT_STATE.md`;
6. `DECISIONS.md`, `CAPABILITY_MATRIX.md` e incidentes;
7. Notion como registro operacional/documental;
8. memória apenas como contexto.

Fato mutável sem confirmação atual deve ser descrito como **não confirmado**.

## Invariantes do produto
- nenhuma escrita automática na ECU;
- sugestão, timer, conexão, leitura ou OBD nunca inicia escrita;
- toda escrita é manual, revisada e confirmada;
- checkpoint, ACK e readback são obrigatórios;
- ACK ausente ou readback divergente é falha;
- OBD é observacional;
- Curva K e Mapa K permanecem separados;
- referência de gasolina é preservada;
- UI não recalcula matemática ou escalas críticas do núcleo;
- protocolo e decisões críticas ficam no Kotlin;
- linha técnica do Mapa K não é editável;
- atualização não apaga ou reinterpreta aprendizado sem migração explícita e rollback;
- segredos, senhas, chaves e keystores não entram no repositório.

## Autoridade única de escrita
`CalibrationWriteSafetyPolicy` é a política comum para decidir se uma mutação manual da ECU pode **começar**. Toda superfície mutável conhecida deve consultar a mesma decisão de serviço, USB, permissão, engine, telemetria fresca e RPM.

Os writers continuam responsáveis por checkpoint/backup, ACK, readback, recuperação e resultado. Uma bridge nunca pode criar um atalho que contorne a política comum.

`applySuggestion` e equivalentes legados são somente preparação/revisão. Sugestão nunca é writer.

## Mapa K — autonomia do proprietário
O operador pode selecionar de **1 a 144 células graváveis** do Mapa K em uma única intenção humana. Isto é decisão explícita do proprietário e não deve ser reduzido a 16 por interpretação de segurança.

O writer pode dividir internamente a intenção em blocos de até 16 células. Essa fragmentação é detalhe nativo de segurança/protocolo; a UI não coordena transações internas.

Fluxo obrigatório:
`Ler mapa → selecionar 1–144 células → editor visível → ajustar → revisar → confirmar → escrever em blocos nativos → validar ACK/readback → refletir readback real`

Falha em qualquer bloco torna a intenção global parcial/falha; nunca sucesso silencioso.

## Curva K e sugestões
A tendência global pertence à Curva K; o residual local pertence ao Mapa K.

Fluxo de sugestão global:
`evidência Kotlin → sugestão → Preparar sugestão → prévia Kotlin → revisão antes/depois → confirmação humana → writer → ACK → readback`

Preparar sugestão apenas preenche proposta; não escreve na ECU.

## Método CUSTOMROM obrigatório para novas melhorias de UI/UX
O projeto adota como referência de **método**, não de aparência, o blueprint Notion `Blueprint Premium UI/UX — Método CUSTOMROM reutilizável`.

Toda melhoria adicional deve seguir:
- intenção humana e efeito prático antes de arquitetura interna;
- preservar backend/Kotlin/USB/writers comprovados; não refatorar por estética;
- complexidade sob demanda: estado normal compacto, detalhes e diagnóstico quando necessários;
- feedback imediato após ação do operador;
- estado e próximo passo claros;
- segurança contextual: proteção aparece no momento da ação crítica, sem criar burocracia permanente no uso normal;
- ação crítica contextual, revisável e confirmável;
- linguagem de efeito primeiro, detalhe técnico depois;
- uma única autoridade para estado, navegação, eventos, seleção, revisão e confirmação;
- nenhuma segunda shell, timer concorrente ou lógica crítica duplicada na WebView;
- cada ação deve deixar explícito se **observa**, **prepara/revisa** ou **muta a ECU**.

Premium significa menor esforço cognitivo com maior confiança e segurança, não mais efeitos visuais.

## Autoridade única da interface
A UI ativa usa uma Store, um Router e um Scheduler. São proibidos shells empilhados, funções globais sobrescritas em sequência, timers competindo, `MutationObserver` reorganizando permanentemente a própria tela e editor ativo escondido por outro modo.

## Uso principal
- multimídia/tablet 9\", 16:9, horizontal: experiência principal;
- celular vertical: uso rápido e concentrado;
- mesma lógica e mesma autoridade em ambos.

## Protocolo e variantes
- `MODULE_VERSION 0x0173` deve orientar a validação 18/30 dos quatro vetores AutoCal dinâmicos; os contadores 0x015B/0x015C permanecem 18×U16.
- respostas `0xCA` conhecidas devem preservar frame bruto e semântica: retryable somente quando comprovado; classe non-retryable ou desconhecida nunca recebe retry cego.
- nomes de comandos não podem afirmar finalidade não comprovada.

## Testes
Ordem: reproduzir defeito → teste focado → componente → contratos → lint → build → integração → APK → validação física.

Toda mudança deve passar `python -B tools/run_checks.py`. Mudança Android deve passar `testDebugUnitTest`, `lintDebug` e `assembleDebug` no gate autorizado. CI verde prova somente o que os testes exercitaram.

Para Mapa K, proteger no mínimo: leitura antes da seleção, editor visível, eixos/valor corretos, 1–144 células, linha técnica protegida, revisão antes/depois, cancelar sem escrever, uma intenção humana, blocos internos de até 16, falha de ACK, readback divergente, falha parcial explícita e atualização pelo readback real.

## Incidentes e aprendizado
Defeito confirmado, quase falha ou descoberta reutilizável deve seguir `LEARNING_RULES.md` e, quando material, gerar incidente em `docs/incidents/` com sintoma, causa, por que testes não detectaram, correção, teste de regressão, evidência e risco residual.

## Build, APK e validação física
APK é fronteira separada. Nenhum build, artifact ou CI substitui teste em celular/multimídia e, para USB/ECU/OBD/escrita, validação física autorizada.

Não declarar `corrigido`, `funcionando`, `estável` ou `seguro` fisicamente antes da evidência correspondente.

## Formato final
- Modo Codex Engineering Guardrails executado:
- Skill Guardrails carregada:
- REOPS commit carregado:
- Skills REOPS carregadas:
- Skills REOPS adiadas:
- Objetivo aprovado:
- Repositório, branch e commit:
- Estado explicado:
- Arquivos alterados:
- Evidência e testes:
- Artifact/APK e SHA-256:
- Netlify e conteúdo servido:
- Ainda não validado no celular/veículo:
- main, publicação e sistemas externos foram alterados?:
- Próximo passo único:
