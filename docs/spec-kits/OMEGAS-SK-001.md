# OMEGAS-SK-001 — ProgBase AutoCal parity + global reality gate

## Identidade

- Estado: ACTIVE
- Epic: #81
- WorkUnit: `OMEGAS-WU-006`
- Branch: `OmegasVerde`
- Spec: `docs/superpowers/specs/2026-09-21-omegas-verde-progbase-autocal-parity-design.md`
- Plan: `docs/superpowers/plans/2026-09-21-omegas-verde-progbase-autocal-parity.md`
- Evidence: `docs/evidence/OMEGAS-WU-006.json`

## Objetivo final

Fechar AutoCal e o gate de realidade do OMEGAS sem tentativa-e-erro: entender o original, comparar cada consumer, reproduzir divergências com dados reais, corrigir apenas o que estiver provado e promover somente com render/runtime + CI.

## Workstreams

1. #82 — ProgBase byte/consumer map
2. #83 — OMEGAS parity matrix
3. #84 — global real-log rendered E2E
4. #85 — Dashboard LEVELS RAW
5. #86 — canonical session unification

## Dependências

`#82 -> #83 -> REDs de implementação`

`#68 -> #84 -> gates render/runtime`

`#83 + #84 -> #85/#86 quando a divergência estiver reproduzida`

## Limites

- nada de SIL/CIU;
- nada de ciência paralela em JS;
- nada de volume/percentual inventado para LEVELS;
- nada de “CI verde = veículo provado”;
- nada de remoção DEAD sem prova de consumidores.
