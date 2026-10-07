# Workflows do GitHub Actions

Levantamento de 2026-10-07 sobre `.github/workflows/*.yml`. Branch canônica: `OmegasDiamante`. "Dispara hoje" responde: um push ou PR normal na `OmegasDiamante` executa este workflow?

| Workflow | Gatilho | O que faz | Dispara hoje? |
|---|---|---|---|
| `ci.yml` | push e PR em `OmegasDiamante` e `OmegasPlatina`; manual | Prova a identidade do SHA, roda `tools/run_checks.py`, `./gradlew testDebugUnitTest` e `lintDebug`. | Sim. É o gate real (`build_and_test`). |
| `verde-apk-now.yml` | manual (`workflow_dispatch`) e `workflow_call` | Gate de APK: só compila se `build_apk=true`; verifica, compila e publica o APK com SHA-256. Nunca roda em push. | Só manual. É o caminho do APK único no fim. |
| `experience-apk.yml` | PR em `OmegasDiamante` | Chama `verde-apk-now`, render multimídia e navegador elástico. | Não: todos os jobs exigem head `work/platina-diamante-ui` ou `work/platina-autocal-coerencia`; qualquer outra branch fica "skipped". |
| `diamante-elastic.yml` | PR em `OmegasDiamante` | Regressão da UI elástica no navegador. | Não: mesma condição de head; "skipped". |
| `autocal-mission-evidence.yml` | PR em `OmegasPlatina` | Chama `verde-apk-now` e `verde-android-render-evidence` para branches `work/platina-*`. | Não: só PR que mira `OmegasPlatina`, e o fluxo atual mira `OmegasDiamante`. |
| `autocal-blueprint-layout.yml` | push em `OmegasPlatina` (paths do cockpit AutoCal); manual | Regressão de layout do cockpit AutoCal. | Não em push na Diamante; só manual. |
| `verde-android-render-evidence.yml` | push em `OmegasVerde`/`OmegasPlatina` (paths de app/UI); manual; `workflow_call` | Renderiza o APK no emulador Android 1280x720 e coleta evidência. | Não em push na Diamante; manual ou chamado por outro workflow. |
| `verde-fast-contracts.yml` | push em `OmegasPlatina` (paths); manual | Contratos rápidos Python/UI no mesmo SHA. | Não em push na Diamante (o `ci.yml` já cobre `run_checks.py`). |
| `verde-autocal-host-parity.yml` | push em `OmegasVerde` (paths); manual | Paridade do host AutoCal com o ProgBase; pode abrir Issue. | Não: branch `OmegasVerde` é antiga; só manual. |
| `verde-forensic-fanout.yml` | push em `OmegasVerde` (paths); manual | Fanout forense do AutoCal (plano, lanes, agregação). | Não: só manual. |
| `verde-global-reality-fanout.yml` | push em `OmegasVerde`/`OmegasPlatina` (paths); manual | Fanout global de realidade (JVM, Node, Python) por lanes. | Não em push na Diamante; só manual. |

## Recomendação

Nada foi apagado nem alterado. Para a Diamante deixar de depender de nomes de branch antigos:

1. Trocar `OmegasPlatina`/`OmegasVerde` por `OmegasDiamante` nos `branches:` dos workflows `verde-*`, `autocal-*` que ainda interessam, ou apagá-los depois que o dono decidir quais gates quer.
2. Trocar as condições de head (`work/platina-diamante-ui`, `work/platina-autocal-coerencia`) de `experience-apk.yml` e `diamante-elastic.yml` por um padrão `work/diamante-*`, ou remover o `if` e usar `paths:` para a UI.
3. Renomear `verde-*` e o nome "OMEGAS PLATINA" dos workflows (cosmético; `verde-apk-now.yml` é citado em `AGENTS.md`/`STATUS.md` e precisa de atualização junto).

Não migrado agora porque cada troca muda quando o CI custoso dispara; é decisão do dono.
