# Canvas de telas v3 — repaginação ponta a ponta (2026-10-26/27)

Fonte de verdade das telas desenhadas no artefato **Design** do dono: <https://claude.ai/artifact/4J4VM2Bkc6zW4d1sNXe8w4> (privado; o dono e a equipe editam por lá). Este diretório guarda o mesmo conteúdo em arquivos, para que Claude, Codex e AGY trabalhem sobre o GitHub e nada se perca.

**Regra 10 (REGRAS-DO-DONO): isto é mockup. Nenhuma tela do app muda antes do OK do dono sobre o conjunto de todas as telas e o mapa de navegação.**

## Estrutura
- `project/` — as telas (`NN-Nome.dc.html`) e o `canvas.json` (posição, tamanho, quais são interativas). Formato "Design Component": cada tela é HTML com buracos `{{ caminho }}`, `<sc-for>`, `<sc-if>` e uma classe `Component extends DCLogic`.
- `gen/` — geradores e checagens. Quem gera o quê:
  - `autocal_v3.py` → 02 e 03 (e o script do AutoCal, reaproveitado) · `autocal_800.py` → 16 · `retopbar800.py` → barra do topo da 17
  - `refino_v3.py` → 05, 05b, 05c, 05d · `ajuste_local_v3.py` → 06 · `curva_mapa_v3.py` → 08
  - `funcoes_v3.py` → dados da 18 · `main_v3.py` → Main (parte de `parts/Main.base.html`) · `canvas_v3.py` → `canvas.json`
  - `retopbar.py` → reordena a barra do topo de todas as telas 1280×720 · `lint_dc.py` → formato e vocabulário
  - `steps/todas-as-telas.json` → os cliques usados nas capturas.
  - 07, 09–15, 19, 20 foram escritas à mão ou pelo Codex e ajustadas depois (não têm gerador).
  - Legado (v1/v2), mantido só como histórico: `autocal_base.py`, `autocal_svg.py`, `curva_svg.py`, `refino_svg.py`, `refino_v2.py`, `c2m.py`, `05-casca.html`.
- `codex/` — os briefs entregues ao Codex (`BRIEF.md` comum, `LAYOUT-V3.md`, `TAREFA-*.md`). O Codex escreve só em pasta própria; quem integra é o Claude.
- Ferramentas em `tools/design/`: `render_dc.cjs` (renderiza cada tela com Chromium, tira captura e confere texto cortado, texto vazando do cartão, texto em cima de texto, alvo de toque pequeno, tela fora do quadro e erro de script) e `dc-runtime.js` (runtime local mínimo). Teste da ferramenta: `tests/design/render_dc.test.cjs`.

## Como ver e conferir (sem abrir o artefato)
```sh
node tools/design/render_dc.cjs docs/produto/mockups/v3/project --out /tmp/render \
     --steps docs/produto/mockups/v3/gen/steps/todas-as-telas.json                            # todas as telas, com cliques
python3 -I docs/produto/mockups/v3/gen/lint_dc.py docs/produto/mockups/v3/project/*.dc.html   # formato e vocabulário
node --test tests/design/render_dc.test.cjs                                                    # teste da ferramenta
```
Último resultado (2026-10-27): 23 telas, 78 capturas, 0 erro de script / texto cortado / texto vazando / fora do quadro; lint OK. Únicos alvos de toque < 44 px: as células e cabeçalhos da grade 12×12 do Mapa K (06 e 17), que são mapa de leitura.

## Telas
| Nº | Tela | Observação |
|---|---|---|
| Main | Jornada e navegação | resumo, 5 abas, regras, o que o dono já decidiu e o que ainda precisa de OK |
| 01 | Conectar | primeira vez |
| 02, 03 | AutoCal esperando / aprendendo | o app mede sozinho; o dono só liga ou desliga. Na 03: tocar na célula Zona × Combustível apaga; arrastar escolhe pontos; botões de apagar sob o gráfico |
| 05, 05b, 05c, 05d | Refino: achei um problema · tudo certo · piorou · central ainda aprende | um botão (Gravar correção); "Refinar por minha conta" na 05d |
| 06, 07, 08 | Ajuste local (Mapa K), Ajuste global (Curva K), Curva → Mapa K | |
| 09 | Ferramentas | "Para você agora" + 8 grupos; só leitura marcado |
| 10, 11, 12, 13 | Anti-apagão, Carro apagando, Avisos da central, Viagens | |
| 19, 20 | Balão sobre outros apps (3 estilos + bolinhas do gás), Nível do gás (calibrar em 2 pontos) | |
| 14, 15 | Estados e avisos, Padrões (componentes) | |
| 16, 17 | AutoCal e Ajuste local em 800×480 | |
| 18 | Nada some | toda função e onde foi parar |

## Layout v3 (vale para toda tela nova)
Barra do topo 56 px; cabeçalho em `y=64–120`; painel esquerdo e cartão direito em `y=128–624` (496 px); **sem barra de ações**: os botões ficam no fundo do painel esquerdo (ou, no AutoCal, logo abaixo do gráfico); navegação de baixo em `y=636`. Em 800×480: barra de 40 px, cabeçalho em `y=42`, painéis em `y=92–416`, navegação em `y=424`. Detalhes: `codex/LAYOUT-V3.md`.

**Aviso de gravação** (dono, 2026-10-27): um selo pequeno e discreto no canto direito da barra do topo (onde sobra espaço): "Gravando…", "Conferindo…", "Gravado e conferido", "Não gravou". Nunca trava a tela; fica até a próxima ação; o "Desfazer" fica no painel. Em 800×480 a barra está cheia, então o selo ocupa a pílula de frase do cabeçalho.

## Decisões do dono neste ciclo (2026-10-26/27)
- Pode mexer nas 4 abas que a regra 12 travava, **desde que seja mexida boa**; pode mostrar água e as 14 verificações, **bem orientadas**.
- **Não** travar gravação com o carro andando. O app **pode pedir a permissão do GPS** (velocidade e viagens). "Viagens" no lugar de "Sessões".
- Barra do topo por prioridade: **No gás · Nível · Inj. gasolina · MAP · RPM · Tensão**. Rótulos "MAP" e "RPM" na barra.
- AutoCal: tocar na zona (pronta ou parcial) **apaga na hora**, com "toque para apagar" na vertical; sem tela de limpar. A faixa "Curva K" sai do AutoCal (inútil ali) e os botões de apagar/reaprender ficam no lugar dela, sob o gráfico.
- Refino: **um botão (Gravar)**, sem trilha de passos; causa e efeito, dúvidas e tentativas do usuário.
- Aproveitar o espaço: gráficos esticados até perto da navegação, sem barra de ações sobrando.
- Gravar **não trava a tela**: aviso discreto no canto direito da barra do topo.
- Balão: gostou; mais de um estilo (mais discreto) e **bolinhas do gás**. Garantir leitura **precisa** do nível do gás.
- Melhorar o algoritmo que apaga sozinho pontos com RPM baixo (1.000 / 1.200 ou o valor configurado) — ver `docs/handoff/PENDENTE.md`.

## Coordenação entre agentes (Claude, Codex, AGY)
- O GitHub é o barramento. Antes de escrever: `tools/sync-agentes.sh --so-ver`. Depois de cada marco: commit pequeno e `tools/sync-agentes.sh` (busca, mescla sem resolver conflito sozinho, envia sem force, lê o remoto de volta).
- Uma superfície de escrita por agente: telas em `docs/produto/mockups/v3/`; código do app em branch própria. O ledger único é `docs/handoff/PENDENTE.md`.
- **Codex:** em 2026-10-27 a franquia do ChatGPT Plus acabou no meio das tarefas M, O e P (16, 14 e 15); as saídas parciais foram revisadas e terminadas pelo Claude (16 foi refeita por gerador). Nada foi cobrado por API. A franquia volta no horário que o próprio Codex informa; até lá o Claude segue sozinho.
- **Frente paralela:** a branch `nova-experiencia` implementa 4 abas (Regular, Carro, Ajuste fino, Ferramentas). Este canvas mantém as 5 abas (regra 8). A escolha é do dono; até lá, não mesclar as duas às cegas. Aproveitáveis nas duas: ajuda "?" por tela (`ajuda-textos.js`), primeiro uso em 4 cartões, componente único de estado da central.
