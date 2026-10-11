# Checkpoint — Obsidian enxuto (2026-10-11 17:45 UTC)

Registro canônico para retomar sem reconstruir a história. Branch `work/platina-obsidian-enxuto`.

## Objetivo e restrições (dono)

- Base exata do APK "Obsidian" do dono: commit `2c456bca` (APK 8.0.0-test, run 37077178649). Nada da Platina posterior entra sem porte explícito.
- Objetivo único do app: aprender o comportamento da gasolina e deixar o GNV equivalente. O AutoCal nativo da ECU é a base; o OMEGAS observa sozinho, propõe e só grava quando o dono toca (foto antes, readback, Desfazer).
- App para leigo: simples, didático, sem complexidade visual; sem "Detalhes", sem "Entendi", sem botão "Abas"; navegação inferior fixa com 8 abas; nenhuma tela rola nem tem sub-rolagem; gráficos usam o máximo de espaço; botões embaixo, alinhados à esquerda.
- Margem do dono: alvo ±4%, até 5% aceito; nada de ação por 4,1–4,2%.
- Bytes de comando da ECU não mudam (UsbSerialManager, ResponseDrivenEcuEngine, AutoCalProtocol, KFactorManager, KWriteManager, AutoCalNativeActionManager — exceção: trava reset-nunca-pausa, decisão do dono 2026-10-08). Erro de transporte ≠ erro da ECU. Desconhecido = "—", nunca 0.
- Um APK só, no fim (`verde-apk-now.yml`, `build_apk=true`), com SHA-256. Validação física só com o dono no carro.

## Estado verificado (remoto = HEAD `131a7153`)

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
| `36a3aa6c` | textos para leigo (Agora, AutoCal, Mapa K, Curva K, Refino); Curva K em 1,0 em um toque com cópia antes |
| `7aae7e77` | aviso à direita acima da barra (não cobre os botões da esquerda) |
| `f4eee9a8` | refino: a curva aprende tudo (lenta incluída) e sabe onde ajustar o quê (local separável → Mapa K) |
| `131a7153` | ajuste local do Mapa K pela prévia Kotlin (sem conta no JS) |

Provas locais em `131a7153`: JVM 619/619 + lint 0 erros, node UI 194/194, unittest Python OK, `run_checks` PASS, `sem_rolagem_8_abas` PASS (22 estados), blueprint AutoCal PASS. Replay real: REF E0 — 51 pares de lenta entram, 0 ações locais, teste cego 4,01% (atual 6,69%); AUTOMATCH E2 — lenta +20% não separável vai para a curva (3,5–4,5 ms sobem).

## Decisões do dono registradas (2026-10-11)

- "A curva deve aprender tudo e saber onde ajustar o quê": sem piso de RPM; diferença só vira local (Mapa K) quando separável (≥ 8 pares, ≥ 3 visitas, outras colunas no mesmo t) e > 5% (sai em 4%).
- Botões do AutoCal e do Mapa K sem ECU: não desabilitar.
- Telemetria fora da thread da ECU (F4b): descartada por medição (~43 µs/quadro na JVM).
- Fila V7 de sugestões não exibida (sem alimentador desde a base); o Refino propõe curva e ajuste local.

## Próximos passos, em ordem

1. CI remota verde em `131a7153` (`ci.yml` e `autocal-blueprint-layout.yml`).
2. Um APK (`verde-apk-now.yml`, `build_apk=true`) desse SHA, com SHA-256.
3. Teste no carro com o dono (classe 5): lenta e cruzeiro no GNV, Refino propõe curva/ajuste local, gravação com readback, Desfazer.
4. Ainda em aberto para decidir com dado do carro: gasolina própria primeiro × referência da ECU; semântica do Mapa K (multiplicativo? neutro 100 ou 128? interpola?).

## Primeiro item ainda não provado

CI remota verde em `131a7153` e APK com SHA-256; depois validação física no carro.
