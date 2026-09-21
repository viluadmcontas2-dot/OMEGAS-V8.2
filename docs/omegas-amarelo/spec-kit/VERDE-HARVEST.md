# Verde Harvest Contract

O OmegasVerde continua ativo em paralelo e deve ser consultado no início e no fechamento de cada WorkUnit do Amarelo.

## Processo
1. Resolver HEAD remoto atual de `OmegasVerde`.
2. Comparar com o watermark anterior.
3. Identificar somente mudanças relevantes à WorkUnit.
4. Classificar cada ativo:
   - HARVEST — útil e comprovado.
   - REVALIDATE — promissor, precisa passar gates Amarelo.
   - REFERENCE ONLY — contexto/evidência.
   - REJECT — legado ou contrato incompatível.
5. Registrar decisão na issue #80 e na WorkUnit.

## Watermark inicial
`6049a6f4d9b56aa6d380500a475567ec6f1ad4bc`

## Candidatos iniciais
HARVEST/REVALIDATE:
- `tests/fixtures/portmon-autocal-cycle-v1.json`
- `tests/test_real_mp48_replay_fixture_contract.py`
- issues #67, #68, #69, #71 como contratos de replay/live/session que precisam ser reavaliados no contexto Amarelo.

## Proibição
Nunca mergear Verde wholesale para “atualizar” o Amarelo. Harvest é por contrato e evidência.