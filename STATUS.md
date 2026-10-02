# Status do OMEGAS V8.2

- WorkUnit ativa: `OMEGAS-WU-006` (AutoCal — Equivalência Refinada GNV = gasolina)
- Branch: `claude/brave-darwin-wuliyo`
- Estado WU-006: `ANDROID_PROVEN` no SHA `b2df77bd2c84be63e72d2595101395eeabf5efe4` (gate rápido, testes Android/JVM, lint e APK verdes); `PHYSICAL_VALIDATION_CLAIMED=false`
- Evidência WU-006: `docs/evidence/WU-006-refined-replay.md`

## APK histórico provado (WU-005)

- Issue: #5
- Branch de fechamento: `work/v8.2-functional-final-20260828`
- Estado funcional: `RELEASE_PROVEN`
- Governança: `REPO_FIRST_ENGINEERING=TRUE`
- Política de custo: `ZERO_MONETARY_SPEND=ABSOLUTE`
- Rota pelo PC do proprietário: proibida
- Fonte funcional do APK: `da8191416d4fbd3d9b7253b10bdbe438323e8822`
- Árvore da fonte: `d052b6930ce3a20ab396dfbc4455ab25c8260f60`

## Construído e provado

- equivalência científica primária `RPM × MAP(bar) → Petrol Inj. (ms)`;
- aprendizado persistente e reconciliação;
- sugestões passivas e revisão humana obrigatória;
- separação entre Mapa K e Curva K;
- fluxo manual `Preparar → Revisar → Confirmar → ACK → Readback`;
- simulador de ECU com sucesso, rejeição, timeout, falha de ACK e readback divergente;
- proteção contra escrita automática na ECU.

## Provas históricas da WU-005 (não representam a suíte atual)

- gate rápido: `QUALITY_GATE_FAST=PASS`;
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

## Higiene atual — 2026-10-02

- Fonte inicial: `463a08d95680efa757059664e964f2d1d1df2d9d`.
- Produto: Agora · AutoCal (equivalência refinada GNV = gasolina) · Aprender · Ajuste global (Curva K) · Ajuste local (Mapa K) · Sugestões · Ferramentas.
- Gate rápido: `QUALITY_GATE_FAST=PASS`; suíte JVM integral sem SDK: `778 → 674`, todos OK; 104 casos removidos exclusivamente com código morto.
- Relatório e reprodução: `docs/evidence/HYGIENE-20261002.md`.
- Inventário remoto: `docs/governance/BRANCHES.md`; nenhuma branch remota apagada.
- Estado da higiene: `PROVEN` — os três commits temáticos estão no SHA Android/APK provado abaixo.
- Workflow final: `omegas-preapk-build.yml`, evento `workflow_dispatch`, run `37041514785`, job `110952524699`, conclusão `success`.
- Fonte do APK: `b2df77bd2c84be63e72d2595101395eeabf5efe4`; árvore `80a2de2dc850335455b565f62455a211ae483aa3`.
- Android: `./gradlew clean testDebugUnitTest lintDebug assembleDebug -PomegasAbis=armeabi-v7a --no-daemon --stacktrace` → `BUILD SUCCESSFUL in 4m 17s`.
- Artifact: `11242364183` / `omegas-v82-rc-b2df77bd2c84be63e72d2595101395eeabf5efe4`, 4.812.072 bytes.
- Digest do artifact: `sha256:5d31616999385d9585a11b200a7a1527377e5cb21dbb80d410eacbf6b0363334`; ZIP baixado pelo conector e hash recalculado, correspondente.
- APK: `app-debug.apk`, 4.811.249 bytes; SHA-256 `9f06d6de3366f491696934da7832ebbe0f2c1b31405343ec0d5da6b4d18e59b8`, recalculado e correspondente ao recibo do build.
- Evidência: `docs/evidence/HYGIENE-CI-20261002.json`; run: https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/37041514785.
- A prova do artifact pertence exclusivamente ao SHA acima. Em `68da5eec`, app, testes e workflow são idênticos ao SHA provado; documentos e `tools/autocal_refine/experiments.py` posteriores foram preservados e não são atribuídos a esse artifact.
- `PHYSICAL_VALIDATION_CLAIMED=false`.
