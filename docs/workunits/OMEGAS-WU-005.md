# OMEGAS-WU-005 — Finalização funcional e cutover repo-first

- Objetivo humano: APK corrigido, utilizável e testado sem redesenhar a UI.
- Issue: #5
- Branch: `work/v8.2-functional-final-20260828`
- Estado: `VERIFYING`
- Risco: médio
- Custo: zero monetário
- Não escopo: redesign, escrita física na ECU, alegação de validação veicular.

## Plano

1. alinhar testes obsoletos ao runtime atual;
2. provar gate rápido;
3. simular Predictor, aprendizado, sugestões, confiança e abstention;
4. executar `testDebugUnitTest`, `lintDebug` e `assembleDebug`;
5. publicar APK + evidência + SHA-256;
6. consolidar árvore completa e governança na `main`.

## Aceitação

- todos os gates acima verdes no mesmo SHA;
- estados desconhecido/abstention não viram medição ou sugestão;
- sugestões não autorizam escrita;
- fluxo ECU permanece manual e com readback;
- repositório contém contexto suficiente para retomar sem chat, Notion ou Linear;
- PR único reintroduz a árvore completa na branch canônica.

## Evidência

Manifesto: `docs/evidence/OMEGAS-WU-005.json`.

## Próximo item não provado

Gate rápido no SHA remoto após correções de contrato.
