# OMEGAS V8.2 — contrato operacional

## Autoridade

- Este repositório é a fonte canônica de engenharia: código, requisitos ativos, decisões, status, testes e evidências.
- A leitura inicial é: `AGENTS.md` → `PROJECT.md` → `STATUS.md` → WorkUnit ativa em `docs/workunits/`.
- GitHub Issue → uma branch → um PR → checks/evidências → merge. Não criar branches de auditoria ou genealogias paralelas.
- Notion e Linear podem guardar estratégia ou histórico, mas não são dependências de boot nem autoridades sobre estado técnico mutável.
- Chat, Brainbase, MCP USE e executores são superfícies de operação, nunca fonte do projeto.

## Execução

- Mutação de source ocorre pela API remota do GitHub. Runtime efêmero pode testar/buildar o SHA remoto exato.
- UI/UX evolui somente por decisão explícita do owner. Decisão de 2026-10-02 (WU-006): navegação por intenção (Agora · AutoCal · Aprender · Ajuste global · Ajuste local · Sugestões · Ferramentas), seguindo os blueprints CUSTOMROM/Omega Dev; Predictor e OBD fora da navegação, com o código preservado.
- Escrita na ECU é sempre manual: preparar → revisar → confirmar → ACK → readback. Falha ou divergência nunca é sucesso.
- O AutoCal é o centro do produto: a ECU coleta; o OMEGAS refina a equivalência GNV = gasolina (`AutoMatchRefinedEngine`), com ganho proporcional à evidência, trava de coerência (passo ≤ ±15%/execução, |Δ ln K/Δ ln t| ≤ 0,35) e falha fechada sem evidência.
- Predictor (fora da navegação) continua diagnóstico e deve falhar fechado/abster quando suporte ou confiança forem insuficientes.
- A equivalência científica primária é `RPM × MAP(bar) → Petrol Inj. (ms)`; `RPM × Petrol Inj.` localiza downstream a célula física do Mapa K.
- Mapa K e Curva K permanecem separados. Nenhum aprendizado ou sugestão grava automaticamente na ECU.

## Verificação e custo

- Ordem: gate rápido → testes afetados/simulações → suíte Android → lint → APK.
- GitHub Actions é último recurso para prova Android/APK quando o executor não possui SDK; usar somente fluxo seletivo, cancelável e sem gasto monetário.
- Mudanças apenas documentais não podem disparar build pesado.
- `PROVEN` exige SHA, comandos, resultados e artifact/hash registrados em `docs/evidence/` e `STATUS.md`.
- Sem validação física, declarar explicitamente o limite; nunca alegar ECU/veículo testados.

### Build/deploy discipline

- `HOSTING_PROVIDER_IS_NOT_TDD_RUNNER = TRUE`.
- RED→GREEN loops rodam na superfície válida mais barata; não exigem GitHub Actions, hosting, APK remoto ou publicação por commit.
- Commits WIP/intermediários não devem disparar build/deploy externo pesado. Consolidar prova externa somente em gate material de integração, artifact/release ou pedido explícito do owner.
- Docs/governança/status-only não justificam build externo.
- Quota de CI/hosting nunca autoriza upgrade pago ou fallback pago.
- Repo-first, TDD e evidência continuam obrigatórios; reduzir frequência de builds externos não reduz rigor.
