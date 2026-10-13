# AutoCal: original × OMEGAS atual — 2026-10-13 (v2, com código do original)

Referência: `PROTOCOLO-LANDI` @ `bd1c65d`, docs 17–22 (capturas `PortmonAUTOCAL (1).LOG` sha256 `4a70f5ae…` e
`PortmonLOGNOVO.LOG` sha256 `43a63272…`; formulários e código do `ProgBase.exe` sha256 `8a2d297c…`).
OMEGAS conferido em `88ff8c07` (+ este commit só de docs). Ordem: impacto na equivalência GNV × gasolina
(curva K = base global; Mapa K = residual por RPM × MAP).
"Código" = provado no `ProgBase.exe` (decompilação + desmontagem). "Tela" = visto rodando a `index.html` real do OMEGAS no
Chromium com os dados da captura (`tools/render_omegas.cjs` do PROTOCOLO-LANDI). "Relatado" = achado de agente, não reconferido.

Imagem lado a lado (original emulado × OMEGAS, mesmo instante, mesmos dados): `docs/evidence/img/2026-10-13-autocal-original-x-omegas.png`.

## Defeitos

| # | Divergência | Evidência no OMEGAS | Original (prova) | Impacto |
|---|---|---|---|---|
| 1 | **Zonas deslocadas uma banda**: fronteiras em 0,50 / 0,70 / 0,90 bar | `assets/ui/screens/autocal-cockpit.js:353` (`edges = [0, 6, 10, 14, 17]`), `:120` (`zoneForBand`: ≤5, ≤9, ≤13). Código + tela | Zonas por banda `0–4, 5–8, 9–12, 13–17`; fronteiras `THD[5], THD[9], THD[13]` = 0,45 / 0,65 / 0,85 (tabela `[5,9,13]` em `0x51A7B8`, `0x517254`, `0x51A85C`; linhas pontilhadas da S2). Código | Bandas 5, 9 e 13 contadas na zona errada; "zona completa" diverge da ECU |
| 2 | **`0x94` (GNV em corte) vira `UNKNOWN`** | `java/.../ecu/Mp48Protocol.kt:101-107` aceita só `80/A0/88/A8/90/B0` | `0x94` = 5 % dos quadros da sessão AutoCal (MAP médio 0,19 bar, rpm ≥ 1337) | Telemetria "desconhecida" em desaceleração no GNV; risco de descartar/atribuir amostra errada |
| 3 | **`Pausar aprendizado` = Enable 0 sem aviso**, um toque | `autocal-cockpit.js:1050-1066` (`runOperational`), rótulo `:104,1144`; mensagem `:1252` "Aquisição pausada… AGORA é somente telemetria". Código | O original pede **Sim/Não** com o aviso `AUTOCALWARNINGDISABLE`: *desligar faz a curva K (linha azul) deixar de atuar na injeção a gás* (`CheckAutoCalEnableBeforeSetData`, `0x51A474`). Captura: Enable 0 por 120 s congelou buffers, contadores, zonas **e K** | O texto do OMEGAS só fala em "pausar aprendizado"; não diz que a correção K **para de ser aplicada** no carro. Regra do dono pede um toque: decisão sua sobre o aviso |
| 4 | `MUL_ACT` (curva K) e `0x018D/018E` lidos a cada 4 s | `NativeAutoCalMonitor.kt:1097-1103`, `NativeAutoCalRefreshPlanner.kt:57` | `0x0161` em todo ciclo (bloco D); `0x018D/018E` em chamadas alternadas (`contador & 1`) | Base global pode ficar até 4 s atrasada depois de um automatch |
| 5 | Aquisição não é lida com Enable desligado | `NativeAutoCalMonitor.kt:223,230` | O original continua lendo (nada muda na ECU) | Tela congela; mostra "pausado" sem mostrar o estado |
| 6 | Lê as duas zonas sempre | `NativeAutoCalMonitor.kt:538-539` | Só as do combustível em uso (`FUN_005171EC`: `+0x4A0 ∈ {0,2}` → gás, `1` → gasolina) | ~25 ms por bloco; dado correto |
| 7 | Mudança de `48 0B[12/13]` dispara snapshot completo (~34 campos) | `NativeAutoCalMonitor.kt:216-219` | Só atualiza o contador `0x0174` e redesenha | Bloqueia a aquisição durante o snapshot |
| 8 | **Eixos**: X auto-escalado (ticks 1,2 / 4,7 / 8,2 / 11,8 / 15,3 / 18,8), Y com 5 rótulos e teto 1,15 | `autocal-cockpit.js:12,433-434,446-456`. Tela | X fixo `1…último nó de PETR_INJ_TBP` (22), rótulo a cada 1 ms; Y 0,15–1,10 de 0,05 em 0,05 (`FUN_00516D8C`) | Pontos não comparáveis de uma sessão para outra |
| 9 | **Nós da curva com MAP ≈ 0 desenhados** como barras verticais grossas à esquerda e inspetor "Ponto 1 · 0,50 ms · MAP gasolina 0,010 bar" | Tela (barras azul/verde em x≈1,2) | Esses nós (MAP 0,01–0,13 < eixo mínimo) ficam fora do gráfico; o original recorta | Ruído visual e leitura falsa no inspetor |
| 10 | **Faltam** gráfico K, painel Acquisition, limites MIN/MAX tracejados, faixa magenta da banda atual, rótulos `#n/3`, cursor vertical (há ponto "AGORA") | `autocal-cockpit.js:1455-1458`, `:1693-1702`. Tela | Doc 21 §4–§5 (regras provadas no código) | Experiência diferente da referência |
| 11 | **Curva K não é editável** por arrasto | — | `ChartKLineMouseDown/DragOver/EndDrag` (`0x517DE8/0x5178CC/0x517980`): arrasta o nó, aceita `0,1 < K < 10,0`, grava `MUL_ACT[i]` ao soltar | Sem ajuste fino manual dos nós (o invariante do dono exige intenção + ACK + readback; a semântica do original é gravar ao soltar) |
| 12 | Não há "Apagar pontos selecionados" | — | `ActionDeleteSelectedPointsExecute` (`0x518E6C`): grava `PETROL_POINT_2DELETE 0x016D` / `GAS_POINT_2DELETE 0x016E` (18 × u8) | Não dá para tirar ponto ruim sem Reset |

## Conflitos com invariantes do dono (decisão sua; não alterei)

| Fato | Onde |
|---|---|
| `RESET_ALL` está exposto ("Recomeçar tudo") e liberado na ponte | `autocal-cockpit.js:764`, `AutoCalJavascriptBridge.kt:99`. Código |
| AGENTS.md chama de `0x04` o "reset de ponto de gás", mas 0x04 = Reset all; GNV = `0x02` | `AutoCalProtocol.kt:22-24`; original: `push 1/2/4/8` em `0x5189C0/CC/D8/B4` |
| `RESET_PETROL` também em um toque; AGENTS.md permite só o de GNV | `autocal-cockpit.js:756` |
| Cartão "EVIDÊNCIA CAUSAL" compara a curva K antes × depois | `autocal-cockpit.js:207-216`. Relatado |
| O original **mostra** a série "GasPointPrev" (pontos do último automatch, círculos pálidos) | doc 21 §4.1; o invariante `autocal-sem-leitura-anterior` proíbe mostrá-la no OMEGAS (mantido) |

## O que já bate

Frames `12 4A 01 vv`, `02 24 04 <modo>` e as escalas /512, /1024, Q14; `48 0B` (byte 12 concluído, 13 contador); formas de `0x014B`/`0x014C`;
`Finish` = copiar `MaxAutomatch` para `NumAutomatchExecuted` (igual ao `AutoCalNativeActionManager`); Reset K = regravar `MUL_ACT` em laço.
