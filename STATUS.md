# Status do OMEGAS V8.0 RED Performance

- WorkUnit ativa: `OMEGAS-RED-WU-001`
- Issue: `#9`
- Branch: `hotfix/v8.0-red-performance`
- Estado: `IMPLEMENTING`
- Base remota inicial: `eb9791b9341aac85dfe19fccc50e42957ee4b16f`
- Autoridade: `REPO_FIRST_ENGINEERING=TRUE`
- Notion/Linear: `HISTORICAL_MEMORY_ONLY=TRUE`
- Escrita automática ECU: `FALSE`
- Limite Mapa K: `100..180`

## Baseline

`python -B tools/run_checks.py` passou todos os gates anteriores a `test_native_autocal_contract.py`; esse teste encerrou por `FileNotFoundError: kotlinc`, limitação do runtime e não falha de asserção. A prova Kotlin usará Gradle.

## Gates

- G1 Repo-first/Issue/Spec Kit: `IMPLEMENTING`
- G2 Limites K e autonomia por RPM: `PENDING`
- G3 Procedência auditável: `PENDING`
- G4 Superfície contínua global + local: `PENDING`
- G5 Sugestões e UI: `PENDING`
- G6 Auto-Cal/regressões: `PENDING`
- G7 Verificação/publish: `PENDING`

Nenhuma mudança funcional desta WorkUnit foi ainda declarada provada. Não há validação física no veículo nesta execução.
