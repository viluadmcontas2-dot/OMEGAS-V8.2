# Status do OMEGAS V8.2

- WorkUnit ativa: `OMEGAS-WU-006`
- Issue: #7
- Branch ativa: `work/wu-006-calibration-science-hardening`
- Estado da WorkUnit: `IMPLEMENTING`
- Gate fechado: `G1_CORPUS_CONTRACT=PROVEN`
- Próximo item não provado: `G2_INDEPENDENT_REPLAY` / `G3_TEMPORAL_INDEPENDENCE`
- UI/UX: congelada
- Governança: `REPO_FIRST_ENGINEERING=TRUE`
- Política de custo: `ZERO_MONETARY_SPEND=ABSOLUTE`
- Rota pelo PC do proprietário: proibida
- Predictor: fail-closed/ABSTAIN enquanto risco/P(improve) não forem empiricamente calibrados
- ECU: escrita manual somente, com ACK/readback

## Contrato científico ativo

`(RPM, MAP)` identifica a condição operacional. A referência é o tempo de injeção de gasolina aprendido nessa região; quando o motor opera em GNV, o tempo de injeção de gasolina comandado pela ECU é observado na região RPM×MAP correspondente. A equivalência/resíduo compara esses tempos de injeção sob suporte físico compatível.

Temperatura, ΔP e outros canais ambientais não são eixos do Mapa K nem dimensões obrigatórias do matching nesta WorkUnit. Permanecem opcionais para diagnóstico/ablação offline e não podem atrasar o aprendizado por padrão.

## G1 Corpus Contract — PROVEN

O corpus bruto permanece fora do repositório; apenas derivados compactos e determinísticos foram canonizados.

Evidência:
- `44` ocorrências de pacotes descobertas recursivamente;
- `33` sessões lógicas após deduplicação;
- representante determinístico por sessão lógica e verificação fail-closed de bytes/SHA declarados;
- lane científica limitada a `8.0.0-test-debug` + `mp48-progbase-v2`, sem misturar gerações apenas por compartilharem nome de schema;
- `1708` episódios derivados na lane V8: `266` GASOLINA e `1442` GNV;
- fixture compactado determinístico: `34846` bytes, SHA-256 `9fd4a4fda3d907af67c9c29c01b17b54cb607f13c3351b66aff553e962980d94`;
- conteúdo JSONL reconstruído: `347449` bytes, SHA-256 `ae050e6770143bd042cc0416fc66cbd91d5694d7ca7917e2d9cfdf078f34a8fd`;
- fixture dividido em 8 partes Base64, indexado por hash e reconstruível por `tools/science/reconstruct_fixture.py`;
- teste de adulteração falha fechado antes de aceitar uma parte divergente;
- as 8 partes remotas foram reconciliadas com a regeneração independente; uma divergência real na `part04` foi detectada e corrigida antes da promoção do gate.

O baseline gasolina walk-forward derivado nesta etapa é régua independente de validação — não performance declarada do algoritmo de produção. Resultado atual: `247` episódios futuros testados, `213` suportados, cobertura `0.8623481781376519`, mediana do erro relativo absoluto `0.01252769477538073`, P90 `0.05406177103407854`, P95 `0.0801364710411562`.

## Gate atual — G2/G3

Objetivo: provar replay independente e separar precisão local de verdadeira independência temporal/entre trajetórias/sessões. O runtime não pode transformar frames adjacentes ou janelas sobrepostas em confiança equivalente a experimentos independentes.

Antes de mudar produção é obrigatório:
- mapear o fluxo real `EquivalenceRuntime → EquivalenceSurface → confiança/incerteza`;
- localizar quais identidades de sessão, trajetória e epoch já existem no runtime;
- escrever testes RED demonstrando a inflação de confiança atual;
- preservar Kish ESS/novelty apenas onde representam precisão local, não generalização entre sessões;
- introduzir suporte independente e/ou piso de incerteza entre sessões sem fabricar evidência ausente;
- manter predictor fail-closed e nenhuma escrita automática na ECU.

## Próximos gates

`G1 Corpus(PROVEN) → G2 Independent Replay → G3 Temporal Independence → G4 Blind Walk-Forward → G5 RPM×MAP→Tinj Tuning → G6 Causal MAP_K Replay → G7 Sensitivity → G8 Risk Coverage → G9 P(improve) → G10 Shadow/Falsification → G11 Production Proof → G12 APK Candidate`.

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
