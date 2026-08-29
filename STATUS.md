# Status do OMEGAS V8.2

- WorkUnit ativa: `OMEGAS-WU-006`
- Issue: #7
- Branch ativa: `work/wu-006-calibration-science-hardening`
- Estado da WorkUnit: `IMPLEMENTING`
- Próximo item não provado: `G1_CORPUS_CONTRACT`
- UI/UX: congelada
- Governança: `REPO_FIRST_ENGINEERING=TRUE`
- Política de custo: `ZERO_MONETARY_SPEND=ABSOLUTE`
- Rota pelo PC do proprietário: proibida
- Predictor: fail-closed/ABSTAIN enquanto risco/P(improve) não forem empiricamente calibrados
- ECU: escrita manual somente, com ACK/readback

## Contrato científico ativo

`(RPM, MAP)` identifica a condição operacional. A referência é o tempo de injeção de gasolina aprendido nessa região; quando o motor opera em GNV, o tempo de injeção de gasolina comandado pela ECU é observado na região RPM×MAP correspondente. A equivalência/resíduo compara esses tempos de injeção sob suporte físico compatível.

Temperatura, ΔP e outros canais ambientais não são eixos do Mapa K nem dimensões obrigatórias do matching nesta WorkUnit. Permanecem opcionais para diagnóstico/ablação offline e não podem atrasar o aprendizado por padrão.

## Gate atual — G1 Corpus Contract

Objetivo: versionar apenas derivados compactos, determinísticos e privacy-safe do corpus real, vinculados por hash às fontes, sem levar o ZIP bruto para o repositório.

Para fechar G1 é necessário provar:
- descoberta determinística das sessões;
- deduplicação por sessão lógica;
- representante determinístico por sessão;
- verificação fail-closed de bytes/SHA declarados;
- outputs reproduzíveis;
- duplicatas/prefixos incapazes de inflar a massa científica.

## Próximos gates

`G1 Corpus → G2 Independent Replay → G3 Temporal Independence → G4 Blind Walk-Forward → G5 RPM×MAP→Tinj Tuning → G6 Causal MAP_K Replay → G7 Sensitivity → G8 Risk Coverage → G9 P(improve) → G10 Shadow/Falsification → G11 Production Proof → G12 APK Candidate`.

## Release funcional anterior preservada — WU-005

A WU-005 permanece `RELEASE_PROVEN` e não é reatribuída a commits desta WorkUnit.

- fonte funcional do APK: `da8191416d4fbd3d9b7253b10bdbe438323e8822`
- tree: `d052b6930ce3a20ab396dfbc4455ab25c8260f60`
- Android/JVM: `939` testes no run de fechamento
- `testDebugUnitTest=PASS`
- `lintDebug=PASS`
- `assembleDebug=PASS`
- workflow run: `33191643201`
- job: `98918331139`
- artifact ID: `9694156687`
- APK SHA-256: `e020afacf94e21eef085f36552f7f9bada4a67ee35bd0c3f631d43615adba07b`
- tamanho: `5126045` bytes
- package: `com.omegas.v7.test`
- `SIMULATED_ECU_ONLY=true`
- `NO_INSTALL_PERFORMED=true`
- `PHYSICAL_VALIDATION_CLAIMED=false`

## Limite atual

WU-006 ainda não reivindica `REPLAY_PROVEN`, `MODEL_PROVEN`, `APK_READY_FOR_PHYSICAL_TEST` ou `VEHICLE_PROVEN`. Esses estados só podem avançar com evidência nova registrada na branch e na Issue #7.