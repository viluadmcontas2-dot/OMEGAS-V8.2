# Canvas de telas v3 — repaginação ponta a ponta (2026-10-26/27)

Fonte de verdade das telas desenhadas no artefato **Design** do dono: <https://claude.ai/artifact/4J4VM2Bkc6zW4d1sNXe8w4> (privado; o dono e a equipe editam por lá). Este diretório guarda o mesmo conteúdo em arquivos, para que Claude, Codex e AGY trabalhem sobre o GitHub e nada se perca.

**Regra 10 (REGRAS-DO-DONO): isto é mockup. Nenhuma tela do app muda antes do OK do dono sobre o conjunto de todas as telas e o mapa de navegação.**

## Estrutura
- `project/` — as telas (`NN-Nome.dc.html`) e o `canvas.json` (posição, tamanho, quais são interativas). Formato "Design Component": cada tela é HTML com buracos `{{ caminho }}`, `<sc-for>`, `<sc-if>` e uma classe `Component extends DCLogic`.
- `gen/` — geradores e checagens (`lint_dc.py`, `refino_v2.py` gera a tela 05; os `*_svg.py` geram as curvas), e `steps/` com os cliques usados nas capturas.
- `codex/` — os briefs entregues ao Codex (`BRIEF.md` comum + `TAREFA-*.md`). O Codex escreve só em pasta própria; quem integra é o Claude.
- Ferramentas em `tools/design/`: `render_dc.cjs` (renderiza cada tela com Chromium, tira captura e confere texto cortado, texto em cima de texto, alvo de toque pequeno, tela fora do quadro e erro de script) e `dc-runtime.js` (runtime local mínimo). Teste da ferramenta: `tests/design/render_dc.test.cjs`.

## Como ver e conferir (sem abrir o artefato)
```sh
node tools/design/render_dc.cjs docs/produto/mockups/v3/project --out /tmp/render            # todas as telas
node tools/design/render_dc.cjs docs/produto/mockups/v3/project --out /tmp/render --only 05 \
     --steps docs/produto/mockups/v3/gen/steps/todas-as-telas.json                           # com cliques
python3 -I docs/produto/mockups/v3/gen/lint_dc.py docs/produto/mockups/v3/project/*.dc.html  # formato e vocabulário
```

## Telas
| Nº | Tela | Observação |
|---|---|---|
| Main | Jornada e navegação | resumo, 5 abas, regras, o que precisa de OK |
| 01 | Conectar | primeira vez |
| 02, 03 | AutoCal esperando / aprendendo | o app mede sozinho; o dono só liga ou desliga o aprendizado |
| 03b, 04 | AutoCal em movimento / Limpar pontos | ver "Decisões": ambas saem na próxima rodada |
| 05 | Refino | refeito na rodada seguinte (um botão, causa e efeito) |
| 06, 07, 08 | Ajuste local (Mapa K), Ajuste global (Curva K), Curva → Mapa K | |
| 09 | Ferramentas | "Para você agora" + 6 grupos; só leitura marcado |
| 10, 11, 12, 13 | Anti-apagão, Carro apagando, Avisos da central, Viagens | |
| 14, 15 | Estados (vazio, erro, sem central…), Padrões (componentes) | |
| 16, 17 | AutoCal e Ajuste local em 800×480 | |
| 18 | Nada some | toda função e onde foi parar |
| 19 | Balão sobre outros apps | |

## Decisões do dono neste ciclo (2026-10-26/27)
- Pode mexer nas 4 abas que a regra 12 travava, **desde que seja mexida boa**; pode mostrar água e as 14 verificações, **bem orientadas**.
- **Não** travar gravação com o carro andando. O app **pode pedir a permissão do GPS** (velocidade e viagens).
- "Viagens" no lugar de "Sessões".
- Barra do topo por prioridade: **No gás · Nível · Inj. gasolina · MAP · RPM · Tensão**. Rótulos "MAP" e "RPM" na barra.
- AutoCal: tocar na zona (pronta ou parcial) **apaga na hora**, com a frase "toque para apagar" na vertical; sem passar por tela de limpar.
- Refino: **um botão (Gravar)**, sem trilha de passos nem chips de estado; pensar causa e efeito, dúvidas e tentativas do usuário.
- Aproveitar o espaço: gráficos esticados na vertical até perto da navegação, sem barra de ações sobrando; gráfico pode ocupar o cartão inteiro.
- Gravar **não trava a tela**: aviso discreto de andamento ("gravando 5 pontos na Curva K…") e um sinal quando termina.
- Balão: gostou; falta **bolinhas do gás** e mais de um estilo (mais discreto). Garantir leitura **precisa** do nível do gás.
- Melhorar o algoritmo que apaga sozinho pontos com RPM baixo (1.000 / 1.200 ou o valor configurado).

## Coordenação entre agentes (Claude, Codex, AGY)
- O GitHub é o barramento. Antes de escrever: `tools/sync-agentes.sh --so-ver`. Depois de cada marco: commit pequeno e `tools/sync-agentes.sh` (busca, mescla sem resolver conflito sozinho, envia sem force, lê o remoto de volta).
- Uma superfície de escrita por agente: telas em `docs/produto/mockups/v3/`; código do app em branch própria. O ledger único é `docs/handoff/PENDENTE.md`.
- **Frente paralela:** a branch `nova-experiencia` implementa 4 abas (Regular, Carro, Ajuste fino, Ferramentas). Este canvas mantém as 5 abas (regra 8). A escolha é do dono; até lá, não mesclar as duas às cegas. Aproveitáveis nas duas: ajuda "?" por tela (`ajuda-textos.js`), primeiro uso em 4 cartões, componente único de estado da central.
