# OMEGAS-AMARELO-WU-005 — AutoCAL UX

Issue: #77
Estado: IMPLEMENTATION_ACTIVE_CLOSURE_BLOCKED_BY_WU_001_WU_002_WU_003

## Resultado observável
Tela AutoCAL automotiva que preserva a semântica original e melhora legibilidade/explicação.

## Contrato
- gráfico Y MAP × X Petrol Inj.;
- referência + GNV + estados suportados;
- fade semântico;
- gráfico Curva K/MUL_ACT;
- ação e consequência claras;
- detalhe técnico sob demanda;
- 1280×720.

## Autoridade
UI projeta estado nativo; não recalcula a ciência como segunda autoridade.

## Gate
Replay real -> runtime/bridge -> WebView renderizado -> screenshot/DOM artifact.

## Implementação progressiva autorizada — 2026-09-21

A implementação visual pode avançar somente sobre relações já PROVEN, sem antecipar o fechamento científico dos WUs anteriores.

Pipeline adotado:
`ECU/native fields -> AutoCalInstrumentProjection -> AutoCalUiProjection.instrument -> autocal-cockpit.js`.

Já implementado:
- referência gasolina/GNV a partir de `PETR_INJ_TBP × *_MNFLD_PRESS_RV`;
- AGORA separado da cadência lenta e ocultado quando stale;
- `PetrolPoint`, `GasPoint` e `GasPointPrev` como camadas nativas distintas;
- pontos de aquisição visíveis independentemente da conclusão das flags de zona;
- quatro flags de zona como contexto, nunca como porcentagem;
- Curve K atual a partir de `PETR_INJ_TBP × MUL_ACT`, sem alvo/smoothing científico OMEGAS;
- JavaScript limitado a apresentação; autoridade semântica publicada pelo Kotlin tipado.

Evidência corretiva importante:
`GasPointPrev` é buffer nativo separado, mas **não** equivale sempre ao `GasPoint` do refresh imediatamente anterior. A UI o rotula como buffer anterior e não inventa animação causal old->new.

Gate de fechamento continua:
real replay -> runtime/bridge -> WebView renderizado -> screenshot/DOM artifact em 1280×720.
Nenhuma aprovação visual final é inferida apenas de testes unitários/DOM.
