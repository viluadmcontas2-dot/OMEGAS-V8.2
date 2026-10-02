# Status do OMEGAS V8.2

## WU-006 atual: continuação de 2026-10-02 (fonte canônica desta entrada)

- Branch única: `claude/brave-darwin-wuliyo`; baseline do handoff `cdd8039535dba1ba6c72f64e6a9d44411f2025a0`, sem commits posteriores do proprietário na comparação remota inicial.
- Blocos 1–6: source de produto publicado no commit `59f6fc57443e3e3eb3298f5777ee322e702c5f98`, preservado. Bloco 7: roteiro de rodagem `docs/V82_REFINO_FIELD_TEST.md` publicado em `ded8cdea088a907e08082695e93a43e97e778629`. Gerador QA/fixtures corrigido em `267dbafab3c7b24d2f628b6b29898fdae23331bc`.
- Revisão/evidência: `docs/evidence/WU-006-FINAL-REVIEW-20261002.md`; checklist item a item em `docs/product/UX-BLUEPRINT-CHECKLIST.md`. As 11 imagens em `docs/evidence/ui-wu006/draft` continuam RASCUNHOS (1280×720/bridge falso), não aceite final. Há R-01 importante em `LevelSensorSnapshot.read()`: possível publicação obsoleta depois de troca de sessão USB; falta regressão concorrente RED→GREEN.
- Método científico vigente: **MAP × Tpet nativo da ECU** é a base; bandas densas **MAP × Tpet** são só visualização; RPM × MAP complementa (refino do refino); `RefinementJournal` verifica após gravação manual. Não há garantia de desaparecimento do tranco: degrau pode tirar a linearidade da puxada, hipótese de ciclo-limite descartada.
- Nível: somente leitura SC36/37/276/300/313 índice 0; informação insuficiente para filtro/LEDs nativos, cinco âncoras/sentido/cheio. UI em **proxy explícito** sem litros. Placar é observacional, mínimo de 5 km por época e nível calibrado indisponível.
- Prova desta continuação: verificação documental estrutural/inspeção estática remota e capturas antigas inspecionadas. **QUALITY_GATE_FAST atual, paridade atual, JVM integral atual, screenshots finais, review independente, Android Gradle, lint, APK e hashes do SHA final: PENDENTES/NÃO EXECUTADOS.** O executor não conseguiu resolver DNS de `github.com` para `git pull --ff-only`; a API autenticada mostrou o baseline canônico. O conector GitHub instalado não expõe `workflow_dispatch`: nenhuma rodada de CI foi criada aqui. Não usar o artifact histórico `b2df77bd` como APK da WU-006 atual.
- Gatilho de release: resolver R-01 com teste; QA e review independentes; gate/paridade/JVM no SHA remoto; uma execução seletiva `.github/workflows/omegas-preapk-build.yml` na branch correta por superfície autenticada apta; inspecionar run/job, `SOURCE_SHA` e árvore, Gradle `testDebugUnitTest lintDebug assembleDebug`, ZIP/APK SHA-256, package, assinatura e ABI. Atualizar a evidência apenas depois do resultado. Sem push trigger, PR, gasto ou escrita automática em ECU.
- `SIMULATED_ECU_ONLY=true`; `NO_INSTALL_PERFORMED=true`; `PHYSICAL_VALIDATION_CLAIMED=false`.

## Histórico preservado: evidência e estado anteriores a esta continuação

- WorkUnit ativa: `OMEGAS-WU-006` (AutoCal — Equivalência Refinada GNV = gasolina)
- Branch: `claude/brave-darwin-wuliyo`
- Prova Android **histórica**, anterior ao source atual dos blocos 1–6: `ANDROID_PROVEN` no SHA `b2df77bd2c84be63e72d2595101395eeabf5efe4` (gate rápido, testes Android/JVM, lint e APK verdes **nesse SHA antigo**, não nesta árvore); `PHYSICAL_VALIDATION_CLAIMED=false`
- Ciclo fechado do refino (ledger de pontos próprios + diário + piloto): `LOCAL_PROVEN`.
  - gate rápido PASS;
  - paridade Kotlin↔Python OK;
  - suíte JVM integral (690 testes) via kotlinc.
  - Prova Android/APK deste SHA pendente.
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
