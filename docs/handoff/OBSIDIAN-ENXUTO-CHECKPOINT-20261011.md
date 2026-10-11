# Checkpoint — Obsidian enxuto (2026-10-11 16:20 UTC)

Registro canônico para retomar sem reconstruir a história. Branch `work/platina-obsidian-enxuto`.

## Objetivo e restrições (dono)

- Base exata do APK "Obsidian" do dono: commit `2c456bca` (APK 8.0.0-test, run 37077178649). Nada da Platina posterior entra sem porte explícito.
- Objetivo único do app: aprender o comportamento da gasolina e deixar o GNV equivalente. O AutoCal nativo da ECU é a base; o OMEGAS observa sozinho, propõe e só grava quando o dono toca (foto antes, readback, Desfazer).
- App para leigo: simples, didático, sem complexidade visual; sem "Detalhes", sem "Entendi", sem botão "Abas"; navegação inferior fixa com 8 abas; nenhuma tela rola nem tem sub-rolagem; gráficos usam o máximo de espaço; botões embaixo, alinhados à esquerda.
- Margem do dono: alvo ±4%, até 5% aceito; nada de ação por 4,1–4,2%.
- Bytes de comando da ECU não mudam (UsbSerialManager, ResponseDrivenEcuEngine, AutoCalProtocol, KFactorManager, KWriteManager, AutoCalNativeActionManager — exceção: trava reset-nunca-pausa, decisão do dono 2026-10-08). Erro de transporte ≠ erro da ECU. Desconhecido = "—", nunca 0.
- Um APK só, no fim (`verde-apk-now.yml`, `build_apk=true`), com SHA-256. Validação física só com o dono no carro.

## Estado verificado (remoto = HEAD `b714edb0`)

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
| `748aa9ba` | este checkpoint |
| `310ac875` | ponte leve `getPresentSnapshotIfChanged` (como na cinza) |
| `27f8d770` | Aprender e Ferramentas sem rolagem, sem "Detalhes" e sem código cru |
| `b714edb0` | enxugar: 9 arquivos de interface mortos apagados + contrato que impede a volta |

Provas: CI remota verde em `27f8d770` (`OMEGAS PLATINA CI` run 38154080587; `AutoCal Platina layout regression` run 38154082559, inclui a trava "nenhuma aba rola"). Locais em `b714edb0`: node UI 161/161, unittest Python 171 OK, `run_checks` PASS, `sem_rolagem_8_abas` PASS, blueprint AutoCal PASS. JVM 585/585 em `310ac875`.

## Em andamento (worktrees locais em scratchpad, nada publicado)

1. Refino rodada 2 — corrigir a revisão adversarial (`scratchpad/audit/revisao-refino.md`): CRÍTICO 1 referência trocando entre antes/depois (falso "piorou"); ALTO 2 diário legado; ALTO 3 alinhamento da curva no serviço; ALTO 4 restaurar contra a ECU; ALTO 5 SEM_BASE re-propõe; MÉDIO 6–19; margem 5%/4%; curva aprende só ≥ 1350 rpm. Worktree `wt-refino2` (base `748aa9ba`).
2. Auditorias só leitura relançadas em `b714edb0`: fluxo de dados (`scratchpad/audit/fluxo-dados.md`) e jornada do leigo (`scratchpad/audit/jornada-leigo.md`). Já pronta: refino global × resíduo local (`refino-global-local.md`) — conclusão: o corpus não mostra resíduo local separável na lenta; o Refino basta para o global; sem planejador local de Mapa K agora.

## Próximos passos, em ordem

1. Integrar o refino rodada 2: validar isolado (JVM+lint, node UI, run_checks, sondas da revisão), commitar, push, `git ls-remote`.
2. Corrigir em fatias pequenas os achados das duas auditorias que valem agora.
3. F4b: JSON da telemetria fora da thread da ECU (`TelemetryForegroundService`) — depois do refino rodada 2.
4. CI remota no SHA final (`ci.yml` e `autocal-blueprint-layout.yml` via `workflow_dispatch` no branch).
5. Um APK (`verde-apk-now.yml`, `build_apk=true`) com SHA-256; teste no carro com o dono (classe 5).
6. Decisões a levar ao dono: limiar de 1350 rpm para a curva aprender; gasolina própria primeiro × referência da ECU.

## Primeiro item ainda não provado

Refino rodada 2 integrada e testada; depois CI remota verde no SHA.
