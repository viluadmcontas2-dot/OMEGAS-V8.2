# Estratégia de testes — OMEGAS Platina (Norte Único)

## Onde roda

Todo teste roda no GitHub Actions; nada é compilado ou testado na sessão de execução.

- PR para `OmegasPlatina`: `ci.yml` (`build_and_test`: `tools/run_checks.py`, `testDebugUnitTest`, `lintDebug`) + `issue-gate.yml` (`Issue gate`).
- Durante uma Task: `tools/ci/remote-test.sh <kind> <alvo>` dispara `task-check.yml` na branch e espera o run do SHA exato.
  - `gradle <Classe>` → `./gradlew testDebugUnitTest --tests "<Classe>"`
  - `node <arquivo>` → `node --test <arquivo>`
  - `python <arquivo>` → `python3 -B <arquivo>`
  - `checks` → `tools/run_checks.py` + `testDebugUnitTest lintDebug`
  - `android [cenário]` → `verde-android-render-evidence.yml` (render 1280×720 no emulador)
- APK com SHA-256: `verde-apk-now.yml` ao fim das fatias 3, 5 e 8.
- `ci.yml` e `tools/run_checks.py` descobrem testes por glob (`tests/test_*.py`, `tests/ui/*.test.cjs`).

## Método

Evidência → teste RED válido → correção mínima → GREEN focado → revisão do diff → portão completo → CI no SHA do PR. Teste de produto não é PASS se não exercitou o que o dono vê.

## Classes de prova

1 contrato de texto · 2 sintético · 3 replay de sessão real · 4 APK renderizado no emulador · 5 físico (só o dono, no carro, por `docs/V82_PHYSICAL_VALIDATION_PROTOCOL.md`). Nenhum PR chama algo de "validado" sem classe 5.

## Gates da estrutura nova (spec §4.6)

- índice sobe nas sessões reais depois de ajuste (replay);
- paridade Kotlin ↔ Python do índice e das fases;
- fila: uma mutação por vez; cabo caído → `✗` sem crash; Desfazer = foto byte a byte;
- ponte: nenhum método sem chamador; nenhum intent sem handler;
- render 1280×720 de cada superfície em cada estado obrigatório;
- DOM: alvo ≥ 76 px (Mapa K ≥ 44 px, R7), texto crítico ≥ 24 px;
- APK novo sobre dados antigos abre sem crash e limpa os órfãos;
- SIGKILL no meio da sessão → um ZIP, sem perda.

## Testes que morrem ou mudam

A lista exata, por fatia, está na spec §4.6 e em cada plano. Teste que morre sai no mesmo PR do código que ele cobria; nunca é pulado ou desligado para ficar verde.
