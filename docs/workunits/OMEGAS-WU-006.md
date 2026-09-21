# OMEGAS-WU-006 — ProgBase AutoCal parity + global reality gate

## Controle

- Estado: `ACTIVE`
- Epic: #81
- Spec Kit: `OMEGAS-SK-001`
- Branch: `OmegasVerde`
- Baseline inicial: `6049a6f4d9b56aa6d380500a475567ec6f1ad4bc`

## Resultado observável

Ao final, o operador deve abrir AutoCal e ver a informação equivalente à rotina original — curvas gasolina/GNV e estado atual quando a ECU fornece os dados — em uma HMI mais simples e segura, sem consultas manuais desnecessárias. O restante das superfícies dependentes de telemetria deve possuir gate E2E derivado de logs reais.

## Sub-objetivo A — original como evidência

### Início
Congelar EXE/hash, Portmon e corpus real.

### Meio
Mapear byte/endereço -> producer -> cadência -> estado -> consumer VCL/UI.

### Fim
Matriz ProgBase completa e versionada, com desconhecidos explícitos.

## Sub-objetivo B — OMEGAS parity

### Início
Traçar a cadeia real atual até a WebView.

### Meio
Comparar consumer por consumer com o original.

### Fim
Cada item classificado: `MATCH | INTENTIONAL_IMPROVEMENT | MISSING | WRONG | INCONCLUSIVE`.

## Sub-objetivo C — correções provadas

### Início
Criar RED que reproduza cada divergência confirmada.

### Meio
Aplicar a menor correção coerente, uma superfície de escrita por vez.

### Fim
GREEN focado + diff review + suites proporcionais + CI remota.

## Sub-objetivo D — realidade visual/runtime

### Início
Usar fixture/corpus real versionado.

### Meio
Replay determinístico pela fronteira real aplicável e render da WebView `1280x720`.

### Fim
Screenshots/receipts demonstram curva, AGORA, Dashboard, estados, Map/Learning e sessão conforme cenários cobertos.

## Sub-objetivo E — produto humano

### Início
Reproduzir ausência de LEVELS no Dashboard e divergência de sessão.

### Meio
Corrigir somente após causa provada.

### Fim
LEVELS RAW está na superfície principal sem semântica inventada; AutoCal pertence à sessão canônica.

## Fechamento

A WorkUnit fecha somente com:
- #82–#86 reconciliadas;
- spec/plan/status/evidence atualizados;
- regressões principais renderizadas;
- CI canônica verde no SHA final;
- classificação PASS/PARTIAL/FAIL/INCONCLUSIVE;
- limite físico declarado.

## NON-GOAL

Portar SIL/CIU.
