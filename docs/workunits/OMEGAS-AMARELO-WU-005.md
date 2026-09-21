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


## Correção de fronteira de domínio — LEVELS — 2026-09-21

`LEVELS / level_raw` **não pertence ao AutoCAL**.

Semântica de produto:
- LEVELS representa exclusivamente o sinal/quantidade de GNV no cilindro;
- sua superfície é `Dashboard / Agora`;
- AutoCAL não publica, não renderiza e não usa LEVELS para aquisição, zonas, Curve K, estado ou progresso;
- nenhuma relação encontrada por proximidade em ProgBase pode promover LEVELS ao domínio AutoCAL.

Implementação:
- removido `levelsRaw` de `AutoCalUiProjection`;
- removido `LEVELS RAW` do cockpit AutoCAL;
- Dashboard/Agora consome `telemetry.live.level_raw` como `NÍVEL GNV`;
- valor permanece RAW/sinal ECU até existir conversão física comprovada; não inventar %, litros ou m³;
- fast-contract CI agora executa teste de fronteira garantindo presença no Agora e ausência no AutoCAL.


## Contrato UX original — checkbox Auto Calibration — 2026-09-21

Observação operacional do owner, alinhada ao binding já provado de `AUTO_CAL_ENABLE`:
- o ProgBase original usa um único checkbox Auto Calibration;
- marcado: ativa Auto Calibration e o AutoMatch automático da ECU;
- desmarcado: desativa ambos e mostra aviso antes de efetivar;
- não modelar botões separados de Start/Stop AutoCAL;
- não modelar um segundo enable de AutoMatch sem evidência nativa;
- desativar não é reset: buffers/pontos não devem ser apagados por inferência.

O cockpit Amarelo foi ajustado para checkbox com aviso na desativação.
