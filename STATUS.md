# OMEGAS Platina — Status

Programa ativo: **Norte Único** (épico #131). Fatia em execução: NORTE-WU-00 (#132, PR #130).
Histórico completo até 2026-10-03: `docs/archive/STATUS-ate-2026-10-03.md`.

## Último APK conhecido — Refino #122 reconciliada na #127 (2026-10-03)

- Branch: `OmegasPlatina`. Integração PR #127 mesclada em `021e536727dddaa597623f6512530b92236576b2`; fonte de produto `e895751fd158cafd8221cd9569bc237799001b38`.
- APK: artifact `11277955529`, 4.822.137 bytes, `APK_SHA256=e52b157b82ec27cda11d66b4dae8161ad9778690fb7cd4a2a66f8abbd33ef64e`; digest do ZIP `7baabbb025f1d2e6cfb6d7187faeca272f4fb6180451fc6bc388bbd4b79576e7`.
- CI principal: run `37133472095`. Evidência exata (APK + 31 cenários Android 1280×720): run `37133472236`. Ambos SUCCESS.
- Classe de prova: 1 a 4 (contratos, sintético, replay do corpus real, APK renderizado no emulador).
- Não provado: classe 5. USB/ECU reais, equivalência física, balão sobre outros apps e sensação no carro dependem do dono no carro (`docs/V82_PHYSICAL_VALIDATION_PROTOCOL.md`).

PHYSICAL_VALIDATION_CLAIMED=false
