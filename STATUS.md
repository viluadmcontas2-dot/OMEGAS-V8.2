# OMEGAS Diamante — Status

Branch canônica: `OmegasDiamante` (criada de `OmegasPlatina` @ `a125436`). Programa ativo: **Norte Único** (épico #131). Execução enxuta (R12): fatias F1–F8 em lotes; APK único no fim.
Histórico completo até 2026-10-03: `docs/archive/STATUS-ate-2026-10-03.md`.

## Último APK gerado (2026-10-06)

- Workflow `verde-apk-now.yml` na `OmegasDiamante`, run `37395169323` (SUCCESS, `workflow_dispatch`), commit `bf744566fdd9e254c2875a6cf5fa186e45c0fd4f`.
- Artifact `omegas-platina-final-bf744566fdd9e254c2875a6cf5fa186e45c0fd4f` (id `11382273257`), 4.705.532 bytes, digest do ZIP `sha256:733b0731840971d80017db20979b72fe127c836a28b800314a94b0f7a9465b54`.
- Esse APK é anterior ao apagamento automático fora da curva e ao refino por regime (PRs #161/#162): o código atual da `OmegasDiamante` ainda não tem APK.

## Último CI da OmegasDiamante

- `ci.yml`, run `37618882882`, commit `1632a38793cb74a895c992b953e0f876eddea910` (2026-10-07), SUCCESS.

## Prova

- Classe de prova do APK acima: 1 a 4 (contratos, sintético, replay do corpus real, APK renderizado no emulador).
- Não provado: classe 5. USB/ECU reais, equivalência física, balão sobre outros apps e sensação no carro dependem do dono no carro (`docs/V82_PHYSICAL_VALIDATION_PROTOCOL.md`).

PHYSICAL_VALIDATION_CLAIMED=false
