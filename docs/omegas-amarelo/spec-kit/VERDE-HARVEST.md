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

## Watermark atual — 2026-09-21
`eeefaaa4d8371e3c4c6cf1260f1ee4b65e836618`

Delta desde o watermark anterior registrado `2e3eaffedeb6a20972275bfee7d065e5d2a88c86`: 6 commits.

### Classificação do delta
- **HARVEST** — padrão de fan-out/receipts do forensic workflow e plano determinístico v2.
- **REVALIDATE** — `PortmonSameEcuParityTest.kt` e evidência de render 1280×720; úteis, mas precisam passar pelos gates Amarelo e pelo corpus dual-Portmon.
- **REFERENCE ONLY** — mudanças de build/render específicas do Verde.
- **REJECT AS AUTHORITY** — qualquer conclusão que trate a arquitetura/runtime Verde como semântica nativa do ProgBase/ECU.

Nenhum merge wholesale é autorizado.


## Watermark delta — 2026-09-21 — `b65b90bbad72c40890a383448a879b040ee05342`
Delta desde `eeefaaa4d8371e3c4c6cf1260f1ee4b65e836618`: 1 commit.

### Classificação
- **REVALIDATE** — refresh operacional de inputs AutoCAL em `NativeAutoCalMonitor.kt` e teste associado.
- **REFERENCE ONLY** — exposição visual de LEVELS RAW e ajustes de dashboard.
- **REJECT AS AUTHORITY** — usar essas mudanças do runtime Verde para definir semântica nativa ProgBase/ECU.

Nenhum merge/cherry-pick wholesale. Próximo watermark parte de `b65b90bbad72c40890a383448a879b040ee05342`.


## Watermark delta — 2026-09-21 — `361904c5ad28fc6a7451c37d10fb76057bf5eee8`
Delta desde `b65b90bbad72c40890a383448a879b040ee05342`: 4 commits.

### Classificação
- **REVALIDATE** — portabilidade/correção do runner de evidência Android; relevante apenas como padrão de CI.
- **REFERENCE ONLY** — alterações de dashboard e seus testes de apresentação.
- **REJECT AS AUTHORITY** — qualquer inferência de semântica nativa AutoCAL a partir dessas mudanças de UI/runner.

Nenhum ativo deste delta altera o contrato WU-001. Próximo watermark parte de `361904c5ad28fc6a7451c37d10fb76057bf5eee8`.


## Watermark delta — 2026-09-21 — `d231f230e5ed09e7003fe62538efa0090214ca9b`
Delta desde `361904c5ad28fc6a7451c37d10fb76057bf5eee8`: 11 commits.

### Classificação
- **HARVEST** — padrão de fan-out com agregação a partir da verdade de cada job em `.github/workflows/verde-global-reality-fanout.yml`; útil como desenho de compute/evidence, não como ciência nativa.
- **REVALIDATE** — `PortmonSameEcuParityTest.kt`, contrato de sessão canônica e endurecimento do render Android; só podem ser promovidos após gates Amarelo e corpus dual-Portmon.
- **REFERENCE ONLY** — `STATUS.md`, WU-006 Verde, dashboard/render e mudanças do runtime `Mp48Protocol.kt`/`Mp48TelemetryScale.kt`.
- **REJECT AS AUTHORITY** — qualquer semântica de protocolo, AutoCAL, escala ou estado inferida do runtime Verde em vez de ProgBase 4.2.0.6 + Portmons crus.

Nenhum merge/cherry-pick wholesale. Este delta não substitui a autoridade nativa do WU-001/WU-002. Próximo watermark parte de `d231f230e5ed09e7003fe62538efa0090214ca9b`.
