# OMEGAS V8.0 RED → BLUE — contrato operacional

## Autoridade

- Este repositório é a fonte canônica de código, requisitos ativos, decisões, status, testes e evidências da RED/BLUE.
- A retomada começa por `AGENTS.md` → `.specify/memory/constitution.md` → `specs/001-blue-runtime-convergence/` → `STATUS.md`.
- Fluxo: uma Issue → a branch BLUE → Spec Kit (`spec.md` → `plan.md` → `tasks.md`) → TDD/testes/evidências → checkpoint. Não criar genealogias paralelas.
- Notion, Drive, chats e sessões históricas são memória/evidência; não governam estado técnico mutável da branch.
- A branch `hotfix/v8.0-red-performance` é o ancestral estável RED. A branch `work/omegas-blue-causal-engine` é a linha BLUE derivada diretamente dela; não transportar `main`/V8.2/science branches como um todo.

## Engenharia

- TDD obrigatório: teste falha pelo motivo esperado, implementação mínima, teste verde e regressão ampla.
- Investigar causa antes de corrigir sintomas.
- Cada comparação deve carregar procedência suficiente para ser auditável.
- `BlueCausalEngine` é a única autoridade runtime de equivalência/correção. Nenhum Predictor, Advisor, AutoMatch, V7 equivalence ou Auto-Cal pode possuir matemática decisória concorrente.
- A equivalência científica primária é `RPM × MAP(bar) → Petrol Inj. (ms)`; referência nasce de microburst estável, não de contagem artificial de visitas.
- `RPM × Petrol Inj.` é a projeção downstream para localizar a célula física do Mapa K.
- Curva K representa tendência global por tempo de injeção; Mapa K representa residual local após remover a tendência global.
- Estado de calibração muda quando Curva K ou Mapa K muda com escrita/readback confirmado; telemetria de estados diferentes não é misturada como se fosse uma calibração.

## Segurança de calibração

- Observar, aprender, prever, abrir editor e preparar proposta não escreve na ECU.
- Escrita é sempre manual: preparar → revisar → confirmar → ACK → readback.
- Falha ou divergência nunca é sucesso.
- Mapa K editável fica limitado a `100..180` enquanto essa política operacional estiver vigente.
- **RPM nunca bloqueia abertura, edição, revisão ou início manual de transação confirmada de Curva K, Mapa K ou proposta Auto-Cal. Não existe gate “abaixo de 1200 RPM” nem exigência de marcha lenta.**
- RPM continua obrigatório para localização/semântica do Mapa K e pode participar da qualidade da evidência; não é autorização de escrita.
- Serviço ativo, USB, ECU pronta, telemetria fresca, confirmação humana, ACK e readback continuam requisitos técnicos de escrita.
- Nenhum componente grava automaticamente.

## Sessões

- Uma sessão lógica de condução pode conter vários segmentos USB. Queda/reconexão transitória não cria, sozinha, uma nova sessão relevante.
- Sessões fechadas são classificadas `PROBE`, `VALID` ou `PROTECTED` por evidência. Probe minúsculo nunca expulsa sessão útil.
- Padrão: reter 30 sessões `VALID/PROTECTED`; configuração nunca abaixo de 20.
- Sessão com escrita/readback confirmado ou proteção explícita é `PROTECTED` e não pode ser removida por pruning automático.
- Gravação quente usa spool privado rápido; promoção para vault público/SAF é posterior e fail-safe. Falha no vault nunca apaga o spool.

## UX de aprendizado

- Medição e ação são conceitos diferentes. Superfícies primárias: Gasolina, GNV e Desvio medido.
- Correção é uma `BlueCorrectionProposal` separada; sem ganho causal suficiente, a UI explica por que não há alvo.
- Nenhuma camada de “Desvio” pode apresentar previsão/fallback como se fosse par medido.

## Verificação e CI

- GitHub Actions remoto é a execução primária aprovada enquanto o repositório permanecer público e usar runners padrão sem custo adicional.
- Gate barato de Spec Kit/legacy/drift/RPM/sessões roda antes do Android pesado.
- Pipeline: `FAST → FULL JVM/unit → lint → APK/evidence` no SHA exato; concurrency cancela SHA supersedido.
- `PROVEN` exige SHA, comandos, resultados e limites em `STATUS.md`, evidência e Issue vinculada.
- CI não substitui validação física: sem teste no veículo, nunca alegar economia, estabilidade física ou comportamento real comprovados.
