# Checkpoint — Obsidian enxuto (2026-10-11 04:30 UTC)

Registro canônico para retomar sem reconstruir a história. Branch `work/platina-obsidian-enxuto`.

## Objetivo e restrições (dono)

- Base exata do APK "Obsidian" do dono: commit `2c456bca` (APK 8.0.0-test, run 37077178649). Nada da Platina posterior entra sem porte explícito.
- Objetivo único do app: aprender o comportamento da gasolina e deixar o GNV equivalente. O AutoCal nativo da ECU é a base; o OMEGAS observa sozinho, propõe e só grava quando o dono toca (foto antes, readback, Desfazer).
- App para leigo: simples, didático, sem complexidade visual; sem "Detalhes", sem "Entendi", sem botão "Abas"; navegação inferior fixa com 8 abas; nenhuma tela rola nem tem sub-rolagem; gráficos usam o máximo de espaço; botões embaixo, alinhados à esquerda.
- Margem do dono: alvo ±4%, até 5% aceito; nada de ação por 4,1–4,2%.
- Bytes de comando da ECU não mudam (UsbSerialManager, ResponseDrivenEcuEngine, AutoCalProtocol, KFactorManager, KWriteManager, AutoCalNativeActionManager — exceção: trava reset-nunca-pausa, decisão do dono 2026-10-08). Erro de transporte ≠ erro da ECU. Desconhecido = "—", nunca 0.
- Um APK só, no fim (`verde-apk-now.yml`, `build_apk=true`), com SHA-256. Validação física só com o dono no carro.

## Estado verificado (remoto = HEAD `cd805290`)

| Commit | Fatia |
|---|---|
| `a346e200` | navegação inferior; Agora sem cabeçalho |
| `1a9e68eb` | AutoCal sem leitura anterior; Readquirir em um toque |
| `216f1957` | Curva K relê a ECU ao entrar; rodapé único; Backups (N) |
| `b8614f38` | telemetria: tela só repinta o que mudou (AutoCal 942→147 mutações/s) |
| `e2c69330` | AutoCal: gráfico grande, rodapé à esquerda |
| `029de5e6` | trava reset-nunca-pausa-aprendizado (porte Diamante) |
| `d3643657` | refino: verificação com prazo, lenta fora, gasolina da ECU |
| `20a44307` | selo da Curva K só escreve quando muda |
| `5f1df892` | CI: trava "nenhuma aba rola" (`tools/ci/sem_rolagem_8_abas.cjs`) |
| `019cbf57` | sem "Detalhes" no AutoCal/Refino; Curva K com a curva à esquerda |
| `ab8cecf4` | gráficos do AutoCal/Refino na largura toda |
| `cd805290` | cartão AutoMatch X/Y fixo no topo do AutoCal |

Provas locais no último commit: node UI 152/152, `run_checks` PASS, blueprint AutoCal PASS, JVM 580/580 + lint (no `d3643657`). CI remota ainda não rodada nesses SHAs (workflows disparam só em `OmegasPlatina` ou `workflow_dispatch`).

## Em andamento (worktrees locais em scratchpad, nada publicado)

1. Refino rodada 2 — corrigir a revisão adversarial (`scratchpad/audit/revisao-refino.md`): CRÍTICO 1 referência trocando entre antes/depois (falso "piorou"); ALTO 2 diário legado; ALTO 3 alinhamento da curva no serviço; ALTO 4 restaurar contra a ECU; ALTO 5 SEM_BASE re-propõe; MÉDIO 6–19; margem 5%/4%. Worktree `wt-refino2`.
2. Aprender e Ferramentas sem rolagem (a trava de CI falha só nessas duas). Worktree `wt-learn`.
3. F4a ponte leve `getPresentSnapshotIfChanged` + cache de interpolação (como na cinza). Worktree `wt-f4`.
4. Auditorias só leitura: fluxo de dados (`scratchpad/audit/fluxo-dados.md`), jornada do leigo (`jornada-leigo.md`), refino global × resíduo local para Mapa K (`refino-global-local.md`).

## Próximos passos, em ordem

1. Integrar cada worktree: validar isolada (node UI, run_checks, JVM+lint quando houver Kotlin, trava sem rolagem, blueprint), commitar por fatia, push, conferir `git ls-remote`.
2. Decidir com os relatórios: um cérebro só (Refino) para o global; resíduo local (ex.: lenta pede ~20% a mais) proposto no Mapa K; o "Aprender"/AssistedCalibrationAdvisor já tem as duas camadas — reaproveitar em vez de criar.
3. Enxugar: remover 9 arquivos mortos (floating-telemetry, predictor-current-cell, styles-expansion*, predictor-model, screens/predictor + CSS) e os testes que só os testam; manter portmon-* (testam o editor de mapa vivo).
4. F4b: JSON da telemetria fora da thread da ECU (TelemetryForegroundService) — depois do refino rodada 2.
5. CI remota no SHA final (`ci.yml` e `autocal-blueprint-layout.yml` via `workflow_dispatch` no branch).
6. Um APK (`verde-apk-now.yml`, `build_apk=true`) com SHA-256; teste no carro com o dono (classe 5).

## Primeiro item ainda não provado

Refino rodada 2 integrada e testada; depois CI remota verde no SHA.
