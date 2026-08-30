# OMEGAS V8.0 RED — contrato operacional

## Autoridade

- Este repositório é a fonte canônica de código, requisitos ativos, decisões, status, testes e evidências da RED.
- A retomada começa por `AGENTS.md` → `PROJECT.md` → `STATUS.md` → WorkUnit ativa em `docs/workunits/` → Issue vinculada.
- Fluxo: uma Issue → a branch RED → testes/evidências → checkpoint. Não criar genealogias paralelas.
- Notion e Linear são somente memória histórica e brainstorming; não governam estado técnico mutável desta branch.
- A branch `hotfix/v8.0-red-performance` é a linha de código da RED. Não transportar funcionalidades da V8.2 sem decisão explícita e teste de compatibilidade.

## Engenharia

- TDD obrigatório: teste falha pelo motivo esperado, implementação mínima, teste verde e regressão ampla.
- Investigar causa antes de corrigir sintomas.
- Cada comparação deve carregar procedência suficiente para ser auditável.
- A equivalência científica primária é `RPM × MAP(bar) → Petrol Inj. (ms)`.
- `RPM × Petrol Inj.` é somente a projeção downstream para localizar a célula física do Mapa K.
- Curva K representa tendência global por tempo de injeção; Mapa K representa residual local após remover a tendência global.
- Repetição correlacionada reduz ruído, mas não fabrica independência.

## Segurança e autonomia

- Observar, aprender, prever, abrir editor e preparar proposta não escreve na ECU.
- Escrita é sempre manual: preparar → revisar → confirmar → ACK → readback.
- Falha ou divergência nunca é sucesso.
- Mapa K editável fica limitado a `100..180`.
- RPM não bloqueia abertura, edição ou início manual de transação confirmada; serviço, USB, engine e telemetria fresca continuam requisitos técnicos.
- Nenhum Predictor, Advisor ou Auto-Cal grava automaticamente.

## Verificação e CI da RED pública

- `PUBLIC_REPO_STANDARD_ACTIONS=PRIMARY_REMOTE_EXECUTION` vale **somente** para o OMEGAS V8.0 RED neste repositório enquanto ele permanecer público e usar runners GitHub-hosted padrão sem custo adicional.
- Pushes com mudanças relevantes de código, testes, build ou workflow na branch RED devem disparar GitHub Actions automaticamente; documentação isolada não deve compilar Android.
- O pipeline remoto segue `FAST → FULL → APK/evidence`: contratos baratos falham cedo; o gate completo só começa após FAST verde; artifact só nasce após FULL verde.
- `concurrency` deve cancelar execução supersedida por SHA mais novo da mesma branch.
- A prova Kotlin/JVM, Android lint e geração de APK deve preferir GitHub Actions; execução local é apoio opcional e nunca autoridade exclusiva.
- Reutilizar cache seguro de dependências/build quando suportado; evitar recompilação deliberadamente redundante dentro do mesmo run.
- Larger runners, runners pagos ou qualquer modalidade com custo adicional exigem nova aprovação explícita do owner.
- Se o repositório deixar de ser público, se a política de cobrança mudar materialmente ou se o runner padrão deixar de ser a rota sem custo aprovada, esta exceção expira e a estratégia de CI deve ser reavaliada antes de novas execuções agressivas.
- `PROVEN` exige SHA, comandos, resultados e limites em `STATUS.md`, `docs/evidence/` e na Issue vinculada.
- CI não substitui validação física: sem teste no veículo, nunca alegar economia, estabilidade física ou comportamento real comprovados.
