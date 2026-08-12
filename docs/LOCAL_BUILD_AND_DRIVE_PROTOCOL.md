# OMEGAS V7 — Local Build + Drive Protocol

## Objetivo
Garantir continuidade de desenvolvimento e geração de APK quando o GitHub Actions estiver indisponível por limite de minutos, sem criar duas fontes de verdade e sem depender da memória de um chat.

## Autoridades
1. GitHub = fonte oficial do código, branches, commits, testes e governança.
2. Google Drive = distribuição móvel, snapshots fechados, APKs, checksums, logs e recibos de build.
3. Notion = estado operacional, decisões, checkpoints e links de evidência.

Drive nunca substitui GitHub como fonte de código editável. Um ZIP no Drive é sempre snapshot imutável de um commit GitHub identificado.

## Regra de sincronização
Antes de qualquer build local:
1. confirmar repositório, branch e commit remoto no GitHub;
2. confirmar que o checkout local corresponde exatamente ao commit remoto;
3. rodar o Local Quality Gate;
4. gerar APK + SHA-256;
5. criar BUILD_RECEIPT.md com branch, commit, testes, APK, hash e validações pendentes;
6. criar SOURCE_SNAPSHOT_<shortsha>.zip do mesmo commit;
7. publicar no Drive em pasta por commit;
8. atualizar CURRENT somente se o build passou o gate automatizado aplicável;
9. registrar no Notion o checkpoint com links e estado físico.

Se qualquer uma dessas identidades divergir, o resultado é INCONCLUSIVE e não deve substituir CURRENT.

## Local Quality Gate equivalente ao GitHub
Executar, nesta ordem:
- python -B tests/test_governance_contract.py
- python -B tools/run_checks.py
- checagem de sintaxe de todos os JS ativos com node --check
- validar gradle/wrapper/gradle-wrapper.jar pelo SHA-256 oficial do workflow
- validar distributionUrl do Gradle 8.9 e validateDistributionUrl=true
- ./gradlew --no-daemon --stacktrace testDebugUnitTest
- ./gradlew --no-daemon --stacktrace lintDebug
- ./gradlew --no-daemon --stacktrace assembleDebug
- calcular SHA-256 de todo APK gerado

O build local não pode enfraquecer, pular ou tornar opcional um teste só para obter verde.

## Estrutura oficial no Drive
OMEGAS V7 - Builds, APKs e Evidencias/
- CURRENT - Ultimo APK validado/
- Builds por commit/
- Branches e snapshots GitHub/
- Logs e validacoes/
- README - Como usar esta pasta OMEGAS V7

### Builds por commit
Cada build deve ficar em pasta nomeada:
<YYYY-MM-DD>_<shortsha>_<branch-curta>_<objetivo>

Conteúdo mínimo:
- OMEGAS-V7-<shortsha>-debug.apk
- APK_SHA256.txt
- BUILD_RECEIPT.md
- SOURCE_SNAPSHOT_<shortsha>.zip
- test-results/

### Snapshots por branch
A pasta Branches e snapshots GitHub representa fotografias, não branches editáveis.
Estrutura recomendada:
- feature__ux-didactic-expansion/
  - <shortsha>/
    - SOURCE_SNAPSHOT_<shortsha>.zip
    - SNAPSHOT_INFO.md

Nunca editar diretamente um snapshot e depois tratá-lo como fonte oficial. Qualquer mudança deve voltar ao GitHub em branch/commit identificável.

## CURRENT
CURRENT representa o último APK automatizadamente validado disponível para teste no celular.
Deve conter:
- APK
- APK_SHA256.txt
- BUILD_RECEIPT.md
- CURRENT_POINTER.md

CURRENT_POINTER.md deve declarar exatamente branch, commit, data, origem do build (GitHub Actions ou Local Quality Gate) e status de validação física.

## BUILD_RECEIPT obrigatório
Campos mínimos:
- repository
- branch
- source_commit
- source_commit_short
- build_origin: github-actions | local-quality-gate
- build_timestamp
- governance_contract
- run_checks
- javascript_syntax
- kotlin_tests
- android_lint
- apk_build
- apk_filename
- apk_sha256
- source_snapshot_filename
- phone_validation
- vehicle_validation
- notes

## Operação por celular
O proprietário não precisa usar terminal. Para teste no celular, o ponto de entrada é Drive > CURRENT - Ultimo APK validado.
O agente é responsável por manter o recibo e a rastreabilidade.

## Fallback quando GitHub Actions estiver sem cota
Não tornar o repositório público apenas para obter runner gratuito.
Usar Local Quality Gate em ambiente que possua checkout autenticado e Android SDK/JDK compatíveis.
Se o ambiente do agente não tiver checkout nem acesso Git de rede ao repositório privado, registrar BUILD BLOCKED e não fabricar um APK a partir de arquivos parciais.

## Atualização dos testes
Quando um teste, workflow ou regra de gate mudar no GitHub, este protocolo e o template do BUILD_RECEIPT devem ser revisados no mesmo bloco. O Drive não é atualizado manualmente como uma segunda implementação de testes; ele recebe a fotografia/recibo correspondente ao código oficial no GitHub.

## Validade
Nenhum APK é 'atual' apenas por estar no Drive. A validade é a combinação exata de branch + commit + gate + SHA-256 + status de validação física.
