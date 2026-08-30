# OMEGAS V8.2 — contrato operacional

## Autoridade

- Este repositório é a fonte canônica de engenharia: código, requisitos ativos, decisões, status, testes e evidências.
- A leitura inicial é: `AGENTS.md` → `PROJECT.md` → `STATUS.md` → WorkUnit ativa em `docs/workunits/`.
- GitHub Issue → uma branch → um PR → checks/evidências → merge. Não criar branches de auditoria ou genealogias paralelas.
- Notion e Linear podem guardar estratégia ou histórico, mas não são dependências de boot nem autoridades sobre estado técnico mutável.
- Chat, Brainbase, MCP USE e executores são superfícies de operação, nunca fonte do projeto.

## Execução

- Mutação de source ocorre pela API remota do GitHub. Runtime efêmero pode testar/buildar o SHA remoto exato.
- UI/UX atual está congelada nesta WorkUnit. Não reinterpretar nem redesenhar sem nova decisão explícita do owner.
- Escrita na ECU é sempre manual: preparar → revisar → confirmar → ACK → readback. Falha ou divergência nunca é sucesso.
- Predictor é diagnóstico e deve falhar fechado/abster quando suporte ou confiança forem insuficientes.
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
