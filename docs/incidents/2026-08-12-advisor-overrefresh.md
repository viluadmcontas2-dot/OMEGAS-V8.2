# Incidente — Advisor recalculado com frequência maior que a revisão científica

## Sintoma e impacto
O `AssistedCalibrationAdvisor` é derivado das comparações gasolina/GNV, mas `SignalLearningStore` solicitava nova análise em toda amostra elegível e `export()` recalculava o Advisor sincronamente. Em Learning contínuo isso podia consumir CPU repetidamente mesmo quando a evidência científica relevante ainda representava a mesma visita/mesma tendência.

## Causa imediata
- `ingest()` chamava `scheduleAdvisorRefresh()` para qualquer amostra elegível.
- `export()` executava `AssistedCalibrationAdvisor.analyze(exported)` novamente, independentemente de existir revisão científica nova.
- A coalescência impedia fila infinita, mas não impedia recomputações consecutivas sob fluxo contínuo.

## Causa estrutural
A atualização do derivado científico estava acoplada ao ritmo de aquisição de amostras, e não ao ritmo de mudança do conhecimento.

## Correção preparada
- `AdvisorRevisionGate` cria revisão apenas quando um token científico muda; não existe timer novo.
- Comparações usam identidade estável + marcos logarítmicos de `observation_count` + direção + buckets de erro/qualidade.
- Referência de gasolina pode gerar revisão por nova visita/estágio ou mudança perceptível de média/confiança, permitindo reconciliação retroativa de GNV pendente.
- Merge e calibração confirmada forçam uma nova revisão.
- `export()` deixa de recalcular o Advisor sincronamente e entrega o derivado já publicado, junto com `advisorRevision`, `advisorPublishedRevision` e `advisorFresh`.
- O worker continua coalescido e latest-revision: se outra revisão surgir durante a análise, apenas a revisão mais nova precisa ser publicada em seguida.

## Por que os testes anteriores não detectaram
Os testes cobriam resultado e backpressure, mas não distinguiam “amostra nova” de “revisão científica nova”. Assim, uma implementação correta funcionalmente podia ainda recalcular o mesmo derivado dezenas/centenas de vezes.

## Testes de regressão
- `tests/test_advisor_revision_budget_contract.py` prova ausência de timer, remoção do refresh incondicional, ausência de análise síncrona em export e presença de revisão/freshness.
- O teste compila e executa `AdvisorRevisionGate.kt` real com `kotlinc`.
- Marcos de observação: 1, 2, 2, 4, 4... em vez de revisão a cada incremento, salvo mudança científica quantizada.
- `QUALITY_GATE_FAST=PASS` integral após a alteração.

## Evidência
O `AssistedCalibrationAdvisor` consome comparações reconciliadas. A nova política ainda atualiza quando uma comparação muda, quando uma referência de gasolina pode desbloquear reconciliação, ou quando merge/calibração altera o conjunto científico.

## Risco residual
- Buckets são uma política de desempenho e precisam ser medidos no uso real para confirmar que não deixam a sugestão perceptivelmente atrasada.
- `advisorFresh=false` pode existir por curto período enquanto a thread dedicada publica a revisão mais recente; o Advisor é derivado/observacional e nunca autoriza escrita automática.
- Compilação Android completa e teste físico seguem pendentes.