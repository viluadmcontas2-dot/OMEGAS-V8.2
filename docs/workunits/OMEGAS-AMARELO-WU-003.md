# OMEGAS-AMARELO-WU-003 — Replay real e GitHub Actions

Issue: #75
Estado: BLOCKED_BY_WU_001

## Resultado observável
CI reexecuta contratos AutoCAL/MP48 a partir de fixtures compactos derivados dos dois logs crus.

## Requisitos
- source SHA + recipe + range;
- parser/runtime real;
- timing/cadência;
- artifacts de diagnóstico;
- mudanças docs-only não disparam build pesado;
- falha determinística em regressão.

## Harvest inicial
Reavaliar fixture/test adicionados no Verde em `6049a6f4...`.

## Não escopo
Release APK.