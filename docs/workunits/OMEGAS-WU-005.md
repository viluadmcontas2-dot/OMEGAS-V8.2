# OMEGAS-WU-005 — Finalização funcional e cutover repo-first

- Objetivo humano: APK corrigido, utilizável e testado sem redesenhar a UI.
- Issue: #5
- Branch: `work/v8.2-functional-final-20260828`
- Estado funcional: `RELEASE_PROVEN`
- Risco residual: validação física fora desta WorkUnit
- Política de custo: zero monetário
- Não escopo: redesign, trabalho novo de OBD, escrita física na ECU, instalação ou alegação de validação veicular.

## Resultado funcional

1. contratos obsoletos foram reconciliados sem enfraquecer os invariantes científicos;
2. `QUALITY_GATE_FAST=PASS`;
3. Predictor, aprendizado, sugestões, confiança, incerteza e abstention/fail-closed estão verdes;
4. simulador de ECU prova ACK, rejeição, timeout, falha de ACK e readback divergente;
5. `testDebugUnitTest`, `lintDebug` e `assembleDebug` estão verdes;
6. a suíte Android/JVM mantém 939 testes;
7. APK debug foi publicado, baixado e verificado por SHA-256;
8. a UI/UX permaneceu congelada e nenhum writer automático foi introduzido.

## Fonte funcional comprovada

- SHA: `da8191416d4fbd3d9b7253b10bdbe438323e8822`
- tree: `d052b6930ce3a20ab396dfbc4455ab25c8260f60`
- workflow run: `33191643201`
- job: `98918331139`
- artifact ID: `9694156687`
- artifact: `omegas-v82-rc-da8191416d4fbd3d9b7253b10bdbe438323e8822`
- APK SHA-256: `e020afacf94e21eef085f36552f7f9bada4a67ee35bd0c3f631d43615adba07b`
- APK: `5126045` bytes
- package: `com.omegas.v7.test`
- filtro ABI solicitado ao build: `armeabi-v7a`
- libs nativas empacotadas: nenhuma; portanto `APK_NATIVE_ABIS` é vazio, não uma ABI inventada.

## Aceitação funcional fechada

- estados desconhecido/abstention não viram medição ou sugestão;
- previsão permanece distinta de observação;
- sugestões não autorizam escrita;
- fluxo ECU permanece manual e com ACK/readback;
- falha, recusa, timeout ou divergência nunca viram sucesso;
- Mapa K e Curva K permanecem separados;
- equivalência primária permanece `RPM × MAP(bar) → Petrol Inj. (ms)`.

## Evidência

Manifesto: `docs/evidence/OMEGAS-WU-005.json`.

O artifact contém `SIMULATED_ECU_ONLY=true`, `NO_INSTALL_PERFORMED=true` e `PHYSICAL_VALIDATION_CLAIMED=false`. O ZIP e o APK foram recalculados após download e os hashes correspondem às evidências publicadas.

## Fechamento repo-first

A única etapa restante desta WorkUnit é integrar a árvore completa na `main` pela mesma Issue #5, mesma branch e um único PR. O cutover deve preservar a árvore final desta branch e a história atual da `main`, sem force-push. O SHA final da `main` será registrado no recibo de fechamento da Issue #5.

Após o fechamento, a retomada técnica deve começar pela `main`, `AGENTS.md`, `PROJECT.md`, `STATUS.md`, esta WorkUnit e seu manifesto — nunca pela memória do chat.
