# WU-006 — auditoria de continuação e fronteira de prova (2026-10-02)

**Ramo único:** `claude/brave-darwin-wuliyo`. **Baseline remoto:** `cdd8039535dba1ba6c72f64e6a9d44411f2025a0`, idêntico ao HEAD no bootstrap, sem commits posteriores do proprietário naquele instante. Último source dos blocos 1–6: `59f6fc57443e3e3eb3298f5777ee322e702c5f98`. O roteiro do bloco 7 foi publicado em `ded8cdea088a907e08082695e93a43e97e778629`. A fixture de captura foi corrigida em `267dbafab3c7b24d2f628b6b29898fdae23331bc`.

## Método e resultado da revisão

- Brainbase: bootstrap de organizações **read-only**, sem tarefa paga. Codex Engineering Guardrails e skills Superpowers lidos; esta revisão é **self-review**, NÃO revisão por agente independente.
- Autoridade: `AGENTS.md` → `PROJECT.md` → `STATUS.md` → WorkUnit → prompt consolidado integral → handoff integral. Referências ProgBase consultadas. O transporte Git direto deste executor não resolveu `github.com`; por isso o `git pull --ff-only` solicitado **não executou**. Comparação GitHub API confirmou o baseline remoto antes da primeira mutação. Não afirmar equivalência entre isso e um pull local.
- API remota: único mecanismo usado para criar/alterar arquivos nesta continuação. Sem branch extra, PR, merge, disparo externo automático ou operação em ECU.
- Revisão estática de `router.js`, `index.html`, `refinement-screen.js`, `autocal-refine.js`, `autocal-evidence.js`, `NotificationRoute.kt`, `TelemetryForegroundService.kt`, `LevelEstimator.kt`, `LevelSensorSnapshot.kt`, `CalibrationScoreboard.kt` e testes associados.
- Ordem científica preservada: **MAP × Tpet nativo (18 bandas por combustível) → MAP × Tpet denso (somente gráfico) → RPM × MAP complementar → RefinementJournal**. Hipótese de ciclo-limite descartada; degrau prejudica linearidade, sem garantia física de eliminar tranco.

### Achado material R-01: geração USB da leitura de nível

**Importante; revisão estática, reprodução concorrente ainda pendente.** Em `LevelSensorSnapshot.read()`, `resetForSession(sessionId)` e a publicação `READING` não são uma única operação condicionada à sessão corrente. Após a última validação externa, um `resetForSession(newSession)` pode ocorrer; a publicação final de `snapshot` é incondicional dentro do lock e pode reintroduzir o `sessionId` antigo sobre o novo. O teste existente `session change rejects capture and discards prior reading` muda o callback e só **depois** chama `resetForSession(8)`: não fixa esse interleaving. Teste RED proposto: transação da sessão 7 bloqueada por latch; thread 2 executa `resetForSession(8)` e inicia sessão/leitura 8; liberar 7; exigir que `json().sessionId` nunca volte para 7, nem estado READY/READING antigo. Aplicar correção mínima somente com RED→GREEN e gate executável. **Não houve mudança especulativa no código de produção.** Manter R-01 aberto antes de qualificar o APK como release pronto.

### Sensor, consumo e placar

- `LevelSensorSnapshot.FIELDS`: leitura passiva SC36, SC37, SC276, SC300 e SC313 **somente index=0**. SC36/37 raw opacos, SC276/300 metadados de forma, SC313 parâmetro nominal, não litros.
- `LevelEstimator()` real não recebe cinco âncoras nem `filterPolicyVerified/ledPolicyVerified`; portanto `Mp48TelemetryScale.levelPercentage` continua proxy. Os testes sintéticos da variante calibrada não provam firmware MP48. `observeCalibration()` só envia `position` ao placar se `!proxy`; queda/km permanece indisponível no perfil atual. `ConsumptionTracker` legado ainda recebe `level_raw`, sem nova alegação de volume calibrado.
- `CalibrationScoreboard.confirm()` exige confirmação humana, readback válido e ID não duplicado; GPS só conta continuidade no GNV; comparação de gás/ar exige 5 km por época. Lacuna: stress de token inesperado e concorrência de callbacks ainda sem prova nova.

### QA visual, escopo exato

As 11 imagens antigas em `docs/evidence/ui-wu006/draft/` foram inspecionadas como **capturas 1280×720 de componentes com bridge falso**. Elas não equivalem a E2E do startup Android nem dados reais do veículo.

| Prova visual já inspecionada | Observação |
|---|---|
| `refino-SEM_ECU-720.png` | instrução conectar cabo, sem curva antiga como atual |
| `refino-PROPOSTA_PRONTA-720.png` | timeline, MAP vertical, Tpet horizontal, nativos e densos distintos, três métricas e botão visíveis; sem concluir toque/área segura de 644 |
| `refino-RESTAURAR_TRECHO-720.png` | ação manual com destaque e fase sobre Verificação |
| `agora-720.png` | cartão de calibração e fase compactos |
| `tools-level-720.png` | proxy sem litros/LEDs e placar com 2 km = dados insuficientes |
| `autocal-normal-720.png` e `autocal-problem-720.png` | **rascunhos inválidos como par normal/problema**: o normal herdava HIGH; o problema trazia “não informado ms” por `tPetrolMs` em vez de `timeMs` |

O gerador `docs/evidence/ui-wu006/capture-wu006.cjs` foi alterado somente nas fixtures: LOW sem rejeições no normal; HIGH com par 8–9 ms e rejeição com `timeMs` no problema; nomes de arquivos agora contêm `largura x altura` para evitar sobrescrever o 720 ao capturar 644, 900 ou 1024. **As imagens antigas não foram substituídas; as novas capturas ainda não foram executadas ou inspecionadas neste executor.** A transformação passou verificações estruturais de ocorrência/nomes/campos antes da mutação remota; não substitui Playwright.

### Matriz de evidência, sem reatribuir SHA

| Camada | Estado |
|---|---|
| Gate rápido completo + paridade no HEAD desta continuação | **NÃO EXECUTADOS**. Ambiente sem checkout Git acessível e sem harness Android; não reutilizar resultados antigos como atuais |
| Histórico local do handoff | 709 JVM no candidato anterior a `59f6fc57`; no source final do bloco 6 houve gate + 5 testes de placar afetados. São recibos históricos, não prova Android |
| Testes Node novos, JVM integral, QA visual 1280×644/900/1024×600, foco e erro/readback | **PENDENTES** de executor apto, inclusive verificação da fixture corrigida |
| Revisão independente Superpowers | **PENDENTE**. Só self-review remota, R-01 sinalizado |
| `omegas-preapk-build.yml` via `workflow_dispatch` | **NÃO DISPARADO**. Conector GitHub instalado dispõe de leitura dos runs/artifacts mas não expõe `workflow_dispatch`; não criar push trigger nem PR como atalho |
| Gradle `testDebugUnitTest lintDebug assembleDebug`, APK SHA-bound, ZIP/APK SHA-256 | **SEM PROVA desta árvore e sem APK atual disponível**. O APK histórico `b2df77bd` é de outra árvore |
| ECU real, rodagem, consumo, supressão de tranco, sensor nativo | **NÃO VALIDADOS** |

**Stop condition para o fechamento:** executar RED→GREEN do R-01; concluir imagens/inspeção e revisão independente; gate/JVM/paridade efetivos no source candidate; então pedir ao proprietário o disparo manual seletivo no GitHub Actions (ou uso autorizado de navegador em superfície apta) na branch indicada. Capturar `GITHUB_SHA`, tree, Gradle/lint, run/job/artifact, ZIP e APK hashes/package/assinatura/ABI; atualizar `STATUS.md` sem atribuir um APK a commit documental posterior. Se falhar a mesma causa duas vezes, parar e reportar.

`SIMULATED_ECU_ONLY=true`; `NO_INSTALL_PERFORMED=true`; `PHYSICAL_VALIDATION_CLAIMED=false`.
