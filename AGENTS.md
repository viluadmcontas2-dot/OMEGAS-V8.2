# OMEGAS — contrato operacional estável

## Autoridade

- **GitHub remoto é a autoridade técnica do OMEGAS:** código, Issues, specs, planos, WorkUnits, STATUS e evidências versionadas.
- Boot obrigatório: `AGENTS.md` → `PROJECT.md` → `STATUS.md` → Spec Kit ativo → WorkUnit ativa → Issues ligadas.
- Notion pode ser consultado **somente como referência read-only** de UX/produto quando o owner pedir (ex.: CUSTOMROM / OMEGA DEV). Notion/Linear não controlam esta execução OMEGAS.
- Chat, Brainbase, AgentRed e MMMACHINE são superfícies de operação; nunca substituem o estado remoto versionado.

## Superfície de trabalho

`WORK_SURFACE=REMOTE`
`SOURCE_MUTATION_TARGET=GITHUB_REMOTE_API`
`LOCAL_SOURCE_MUTATION=DENIED`
`TEST_SURFACE=EPHEMERAL_RUNTIME|REMOTE_CI|DEVICE_WHEN_AUTHORIZED`

Antes de escrita relevante e antes de concluir:
1. resolver HEAD remoto de `OmegasVerde`;
2. reconciliar movimento concorrente;
3. nunca sobrescrever trabalho remoto alheio.

Runtime local/MMMACHINE pode testar ou inspecionar o SHA remoto exato, mas não é fonte de autoridade.

## Método obrigatório

Engenharia: `@Codex Engineering Guardrails` + Superpowers aplicável.

Mudança comportamental:
`evidência -> RED válido -> correção mínima -> GREEN focado -> revisão do diff -> verificação ampla proporcional -> CI remota`.

Não chamar teste de produto de PASS quando ele não exercitou o comportamento observado pelo operador.

## Gate global de realidade

Para qualquer superfície dependente de ECU/MP48/telemetria, contratos estáticos/unitários são apoio, não prova final.

A cadeia alvo é:
`corpus real -> replay determinístico -> runtime/bridge real aplicável -> WebView/app renderizado -> evidência visual/estado`.

Viewport automotivo canônico: `1280x720`.

## Invariantes OMEGAS Verde atuais

- nenhuma escrita automática de Map K/Curve K;
- escrita manual exige intenção explícita + ACK + readback;
- `RESET_ALL` não é ação operacional exposta; somente Reset gas point 0x04, observado com efeito amplo, pode ser solicitado manualmente como reinício de aquisição com aviso explícito, confirmação Android e backup completo pré-mutação;
- ciência/protocolo críticos permanecem Kotlin/native;
- AGORA deve permanecer no mesmo contexto da referência AutoCal;
- LEVELS permanece RAW até existir calibração física separada;
- validação física só pode ser alegada com dispositivo/ECU real.

## Fronteira SIL/CIU

**SIL/CIU é independente. Não portar, copiar, cherry-pickar, mesclar ou usar código SIL/CIU como implementação do Verde sem autorização explícita do owner.**

O programa ativo compara o OMEGAS Verde com o **ProgBase original e seus logs reais**.

## Execução paralela

AgentRed pode usar até 20 slots quando as tarefas forem realmente independentes.
- um owner por superfície de escrita;
- scouts/falsificadores podem rodar em paralelo;
- integração e promoção ficam serializadas;
- relato de worker não é prova: verificar o estado integrado diretamente.
