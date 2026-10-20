# Mockups v2 — as 4 abas com a navegação de 5 botões

Regras 6, 8, 9 e 10 de `docs/REGRAS-DO-DONO.md`. **Nada do app foi alterado.** Os PNG são o app REAL (Chromium + ponte falsa dos testes) com CSS/DOM injetado só na captura (`fonte/v2-injetar.js`). Ferramentas e as bolinhas do nível do gás são de outro agente: aqui só há um quadro tracejado reservando o lugar à direita da barra do topo. Dados na tela são da bancada (curva de exemplo, proposta de exemplo do Refino).

**Fluxo:** AutoCal ⇄ Refino ⇄ Ajuste local ⇄ Ajuste global ⇄ Ferramentas (Sessões e Diagnóstico moram dentro de Ferramentas). Estados por aba: sem central, lendo, com dados, ação, prévia, gravado. Nome: `NN-aba-estado-LARGURAxALTURA.png` (1280x720 e 800x480). 01–04 AutoCal · 05–09 Refino · 10–15 Ajuste local · 16–21 Ajuste global.

## O que muda em todas (só nav + alinhamento)
- Navegação inferior: 5 botões (AutoCal, Refino, Ajuste local, Ajuste global, Ferramentas), 58 px, 8 px entre eles, junto à marca, à esquerda. Hoje são 6 esticados (`index.html` linhas 21–26; `styles.css` `.side-nav button{flex:1 1 0}`).
- Barra do topo: Tensão, MAP (hoje o rótulo é "Pressão da admissão", `index.html` `data-live="map"`), Inj. gasolina, Rotação; encostada à esquerda, com espaço à direita para as bolinhas.

## Por aba
- **AutoCal** (01–04): gráfico e rodapé aprovados, sem mudança. Só altura 56→58. "Ações avançadas" abre o menu à esquerda (hoje abre em x=568).
- **Refino** (05–09): igual ao aprovado; barra `.refino-action-bar` já à esquerda, 56→58. "Gravado" mostra "Gravado e conferido pela central" + Desfazer.
- **Ajuste local** (10–15): painel de edição passa para a ESQUERDA (300 px); a barra de 5 botões do cartão desce para o rodapé único, junto com "Gravar na central"; −5/−1/+1/+5 ficam no painel, tamanho natural.
- **Ajuste global** (16–21): painel do ponto passa para a ESQUERDA (340 px); "Gravar na central" vai para o início do rodapé; −0,05…+0,05 e "Preparar este ponto" ficam à esquerda em tamanho natural.

## Medido no app hoje (`medicao-botoes-antes.txt`, getBoundingClientRect, 1280x720)
Fora do padrão (à direita / altura ≠ 58 / buraco), arquivo responsável:
- Ajuste local: lista suspensa, −5 −1 +1 +5 e **Gravar na central** em x=1006–1248 (coluna direita). `index.html` (`.map-editor-panel`), `styles-refine.css:35` (coluna 270 px à direita), `styles.css:45/52` (`.nudge-row`, `.wide`). Barra do cartão (5 botões, x=29–658) com 44 px: `styles.css` `.surface-toolbar`.
- Ajuste global: −0,05…+0,05, **Preparar este ponto**, **Limpar** e **Gravar na central** em x=938–1250 (direita). `styles-calibration-obd.css:23-39` (área `side` à direita), `.curve-nudge-row`. Rodapé: "Curva K toda em 1,0" termina em x=989 (centro >60%); altura 56 (`--foot-h`, linha 23 e 57–60).
- AutoCal: 4 botões 56 px e popover "Ações avançadas" em x=568 (`styles-autocal-cockpit.css` ~1459, 2016–2050; `--act-h`).
- Refino: 2 botões 56 px, já à esquerda (`styles-autocal-cockpit.css` 2017–2034; `screens/refino.js:296`).
- Nenhum buraco entre botões vizinhos foi medido; o problema é a coluna direita e a altura.

## Achados a decidir (não foram corrigidos)
1. **800x480 já quebra no app real:** grade do Mapa K colapsa, gráfico da Curva K cobre o rodapé (toque em "Suavizar" bloqueado), popover do AutoCal e "Curva K toda em 1,0" saem da tela. Viewport canônico é 1280x720; o 800x480 precisa de passada própria.
2. Sem central, AutoCal e Refino mostram gráfico de demonstração, não "vazio".
3. "Fator desejado" da Curva K exibe número longo (1,01000976562…).
4. A bancada não devolve `readbackValid` da Curva K no Refino; só na captura foi forçado para mostrar "gravado".
5. Decisão do dono: mover os painéis de edição para a esquerda muda a estrutura aprovada das telas; alternativa é manter à direita só com campos (sem botões).

Reproduzir (raiz do repo): `NODE_PATH=/opt/node22/lib/node_modules node docs/produto/mockups/v2/abas/fonte/capturar.cjs` e `.../fonte/medir.cjs 1280x720`.
