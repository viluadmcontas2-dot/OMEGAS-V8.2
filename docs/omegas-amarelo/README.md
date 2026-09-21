# OMEGAS Amarelo

Produto sucessor separado do OmegasVerde.

## Boot obrigatório
1. Este README.
2. `STATUS.md`.
3. `spec-kit/README.md`.
4. WorkUnit ativa em `docs/workunits/OMEGAS-AMARELO-WU-*.md`.
5. Issue correspondente no GitHub.
6. Evidências/relatórios ligados na WorkUnit.

## Autoridade
- Épico: #72.
- Branch de fundação: `work/omegas-amarelo-foundation`.
- Baseline de criação: `OmegasVerde@6049a6f4d9b56aa6d380500a475567ec6f1ad4bc`.
- SIL histórica: `work/omegas-verde-sil@2d091faf946c29fc4d36c6db72c42c7cac936bca`.
- Radar do Verde: #80.

## Regra de separação
O Amarelo não será uma integração incremental no Verde. O Verde é fonte viva de ativos candidatos. Nada é importado wholesale; cada item é classificado HARVEST, REVALIDATE, REFERENCE ONLY ou REJECT.

## Sequência
WU-001 ground truth serial -> WU-002 state machine/consumer graph -> WU-003 replay/CI -> WU-004 ciência de aprendizado -> WU-005 AutoCAL UX -> WU-006 runtime leve -> WU-007 integração/E2E.

Sem APK nesta fase.