# Checkpoint — Obsidian enxuto (2026-10-11 17:40 UTC)

Registro canônico para retomar sem reconstruir a história. Branch `work/platina-obsidian-enxuto`.

## Objetivo e restrições (dono)

- Base exata do APK "Obsidian" do dono: commit `2c456bca` (APK 8.0.0-test, run 37077178649). Nada da Platina posterior entra sem porte explícito.
- Objetivo único do app: aprender o comportamento da gasolina e deixar o GNV equivalente. O AutoCal nativo da ECU é a base; o OMEGAS observa sozinho, propõe e só grava quando o dono toca (foto antes, readback, Desfazer).
- App para leigo: simples, didático, sem complexidade visual; sem "Detalhes", sem "Entendi", sem botão "Abas"; navegação inferior fixa com 8 abas; nenhuma tela rola nem tem sub-rolagem; gráficos usam o máximo de espaço; botões embaixo, alinhados à esquerda.
- Margem do dono: alvo ±4%, até 5% aceito; nada de ação por 4,1–4,2%.
- Bytes de comando da ECU não mudam (UsbSerialManager, ResponseDrivenEcuEngine, AutoCalProtocol, KFactorManager, KWriteManager, AutoCalNativeActionManager — exceção: trava reset-nunca-pausa, decisão do dono 2026-10-08). Erro de transporte ≠ erro da ECU. Desconhecido = "—", nunca 0.
- Um APK só, no fim (`verde-apk-now.yml`, `build_apk=true`), com SHA-256. Validação física só com o dono no carro.

## Estado verificado (remoto = HEAD `3953de6e`)

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
| `ef347b19` | refino rodada 2: sem falso "piorou", sem laço Desfazer/Restaurar, margem 4%/5%, curva só ≥ 1350 rpm |
| `a6703aa8` | dados: resultado do AutoCal visível; desconhecido nunca vira 0; Mapa K sem resultado da Curva K |
| `63f58a81` | UX: fila de sugestões congelada fora da tela; Tolerâncias honestas (só água mínima); textos do Aprender/Sugestões/Ferramentas |
| `5b798567` | perf: AutoCal sem leitura repetida no pacote de 1 s; Ferramentas não relê o disco a cada 2 s |
| `3953de6e` | ponte: gravadores diretos e leituras síncronas sem tela fora do JS; faixa oculta não redesenha |

Provas: CI remota verde em `ef347b19` (`OMEGAS PLATINA CI` run 38155536387; `AutoCal Platina layout regression` run 38155537889). Locais em `3953de6e`: JVM 610/610 + lint 0 erros, node UI 174/174, unittest Python OK, `run_checks` PASS, `sem_rolagem_8_abas` PASS, blueprint AutoCal PASS.

## Em andamento (worktree local em scratchpad, nada publicado)

1. Textos para leigo e ajustes pequenos (auditoria da jornada, `scratchpad/audit/jornada-leigo.md`) em Agora, AutoCal, Mapa K, Curva K e Refino; Reset da Curva K em um toque com cópia antes. Worktree `wt-textos` (base `a6703aa8`).

Decisões tomadas e registradas: telemetria fora da thread da ECU (F4b) descartada por medição (~43 µs/quadro na JVM; entrega já é latest-only fora da thread). Fila V7 de sugestões não exibida (sem alimentador desde a base; o Refino propõe a curva).

## Próximos passos, em ordem

1. Integrar `wt-textos`: validar isolado (node UI, unittest, run_checks, sem_rolagem com o estado novo do ponto do AutoCal, blueprint), commitar, push.
2. CI remota no SHA final (`ci.yml` e `autocal-blueprint-layout.yml` via `workflow_dispatch` no branch).
3. Um APK (`verde-apk-now.yml`, `build_apk=true`) com SHA-256; teste no carro com o dono (classe 5).
4. Decisões a levar ao dono: limiar de 1350 rpm para a curva aprender; gasolina própria primeiro × referência da ECU; religar ou não a fila de sugestões do Mapa K no futuro; botões do AutoCal/Mapa K desabilitados sem ECU.

## Primeiro item ainda não provado

Textos da jornada integrados e testados; depois CI remota verde no SHA final.
