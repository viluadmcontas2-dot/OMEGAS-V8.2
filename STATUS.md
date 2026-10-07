# OMEGAS Diamante — Status

Branch canônica: `OmegasDiamante` (criada de `OmegasPlatina` @ `a125436`). Programa ativo: **Norte Único** (épico #131). Execução enxuta (R12): fatias F1–F8 em lotes; APK único no fim.
Histórico completo até 2026-10-03: `docs/archive/STATUS-ate-2026-10-03.md`.

## Último APK gerado (2026-10-07)

- Workflow `verde-apk-now.yml` na `OmegasDiamante`, run `37662103276` (SUCCESS, `workflow_dispatch`), commit `740283d1b68e7801da2d40a601d1008394a53bd3` (PR #171, UI rodada 2).
- Artifact `omegas-platina-final-740283d1b68e7801da2d40a601d1008394a53bd3` (id `11500763708`), 4.776.913 bytes, digest do ZIP `sha256:f7e38df5fddcb746aab0ccdcbecab243293f1783ff69dbf86549c149050f5b6e`. Link: https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/37662103276/artifacts/11500763708
- **SHA-256 do APK** (`app/build/outputs/apk/debug/app-debug.apk`, 4.771.881 bytes): `b8f0d8620b32f8a1cc4c12da013d16757ee3434dbde62324f2127afc5d9ad2f4` (confere com `omegas-platina-apk-evidence.txt`: `OMEGAS_BUILD_COMMIT=740283d1b68e`, `APK_ZIP_INTEGRITY=PASS`).
- Contém o apagamento automático fora da curva, o refino por regime e a UI rodada 2 (rodapé único, 4 bugs de layout corrigidos).

## Último CI da OmegasDiamante

- `ci.yml`, run `37661953811`, commit `740283d1b68e7801da2d40a601d1008394a53bd3` (2026-10-07), SUCCESS.

## Prova

- Classe de prova do APK acima: 1 a 3 (contratos, sintético, replay do corpus real) e 4 em render real no Chromium (ponte falsa). APK NÃO rodado em emulador nesta rodada.
- Não provado: classe 5. USB/ECU reais, equivalência física, balão sobre outros apps e sensação no carro dependem do dono no carro (`docs/V82_PHYSICAL_VALIDATION_PROTOCOL.md`).

PHYSICAL_VALIDATION_CLAIMED=false
