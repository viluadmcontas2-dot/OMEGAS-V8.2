# Incident — owner 089 deixou matemática duplicada no reconciliador

## Sintoma e impacto
Após `089=PASS`, a reauditoria do owner 090 encontrou `LearningSnapshotReconciler.comparisonJson()` recalculando `difference/errorPct` fora de `FuelEquivalenceObjective`. O caminho ainda convertia denominador `<= 0.05 ms` em `0%`, o que poderia serializar um número aparentemente válido para uma comparação inválida.

## Causa imediata
O fechamento do 089 cobriu o caminho direto/consolidado de `MotorLearningMemory`, mas não incluiu o consumidor retroativo de `LearningSnapshotReconciler`.

## Causa estrutural
O inventário de consumidores da função objetivo não estava completo no teste de regressão. Havia duas autoridades matemáticas para equivalência.

## Por que os testes não detectaram
Os testes do 089 verificavam `MotorLearningMemory/FuelComparison`, porém não negavam explicitamente a fórmula duplicada no reconciliador de snapshots.

## Correção
`LearningSnapshotReconciler` agora chama `FuelEquivalenceObjective.evaluate()`. Resultado inválido não vira comparação nem publica erro numérico. O mesmo caminho passa a preservar IDs estruturados da referência, denominador, unidades, timestamps e contexto ambiental disponível.

## Teste de regressão
`tests/test_owner_089_reconciler_authority.py` impede a volta da fórmula ad-hoc e exige os campos de proveniência/units do contrato 089.

## Evidência
Correção iniciada no commit `62c2de5d410b8fb834b50a20a3da52438ccfbdef`; teste estrutural adicionado no commit `38a18edd4b755cc57940b3bab8bde0b19227a83a`.

## Risco residual
O build/JVM/lint amplo ainda precisa executar no mesmo SHA final da campanha. Qualquer outro consumidor de erro encontrado posteriormente deve usar a mesma autoridade antes do PREAPK.
