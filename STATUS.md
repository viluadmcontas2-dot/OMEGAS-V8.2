# Status do OMEGAS V8.2

- WorkUnit ativa: `OMEGAS-WU-005`
- Issue: #5
- Branch de fechamento: `work/v8.2-functional-final-20260828`
- Estado funcional: `RELEASE_PROVEN`
- UI/UX: congelada
- Governança: `REPO_FIRST_ENGINEERING=TRUE`
- Política de custo: `ZERO_MONETARY_SPEND=ABSOLUTE`
- Rota pelo PC do proprietário: proibida
- Fonte funcional do APK: `da8191416d4fbd3d9b7253b10bdbe438323e8822`
- Árvore da fonte: `d052b6930ce3a20ab396dfbc4455ab25c8260f60`

## Construído e provado

- equivalência científica primária `RPM × MAP(bar) → Petrol Inj. (ms)`;
- aprendizado persistente e reconciliação;
- Predictor tipado com confiança, incerteza e abstention/fail-closed;
- sugestões passivas e revisão humana obrigatória;
- separação entre Mapa K e Curva K;
- fluxo manual `Preparar → Revisar → Confirmar → ACK → Readback`;
- simulador de ECU com sucesso, rejeição, timeout, falha de ACK e readback divergente;
- proteção contra escrita automática na ECU.

## Provas da WorkUnit

- gate rápido: `QUALITY_GATE_FAST=PASS`;
- simulações/contratos do Predictor: `PASS`;
- Android/JVM: `testDebugUnitTest=PASS`;
- quantidade da suíte Android/JVM: `939` testes; a contagem vem do run anterior de 939 casos e o diff até o SHA verde altera somente fixtures/asserts dos três testes falhos, sem adicionar/remover testes;
- `lintDebug=PASS`;
- `assembleDebug=PASS`;
- workflow run: `33191643201`;
- job: `98918331139`;
- artifact: `9694156687` / `omegas-v82-rc-da8191416d4fbd3d9b7253b10bdbe438323e8822`;
- APK: `app-debug.apk`;
- SHA-256 do APK: `e020afacf94e21eef085f36552f7f9bada4a67ee35bd0c3f631d43615adba07b`;
- tamanho: `5126045` bytes;
- pacote: `com.omegas.v7.test`;
- assinatura debug: presente;
- filtro de build solicitado: `armeabi-v7a`;
- bibliotecas nativas empacotadas: nenhuma (`APK_NATIVE_ABIS` vazio / tarefas native libs `NO-SOURCE`).

O ZIP do artifact foi baixado e seu SHA-256 recalculado como `8d5339158307b78a8f4e415d510b0097455db20601e5a306d12752a86430ea5a`, igual ao digest publicado pelo GitHub. O SHA-256 do APK também foi recalculado fora do workflow e corresponde ao evidence emitido pelo build.

## Integração repo-first

A árvore completa aprovada permanece nesta mesma branch e deve entrar na `main` pela única linhagem da Issue #5 e por um único PR. O recibo de fechamento da Issue #5 registra o PR e o SHA final da `main`; este arquivo preserva o SHA exato que gerou o APK, portanto mudanças documentais ou de governança posteriores não reatribuem o artifact a outro commit.

Após o fechamento da Issue #5, novos agentes devem fazer boot técnico pela `main` e por `AGENTS.md`, `PROJECT.md`, `STATUS.md`, WorkUnit e manifesto de evidências.

## Limite físico

`SIMULATED_ECU_ONLY=true`, `NO_INSTALL_PERFORMED=true` e `PHYSICAL_VALIDATION_CLAIMED=false`.

Nenhuma escrita real em ECU, instalação no veículo ou validação física foi executada ou alegada nesta WorkUnit.
