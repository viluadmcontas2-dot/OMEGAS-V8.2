# TAREFA B: duas telas (arquivos `out/project/13-Viagens.dc.html` e `out/project/14-Estados.dc.html`)

## 1) 13-Viagens.dc.html (1280×720, INTERATIVA, aba ativa = Ferramentas)
É a antiga tela "Sessões" com a palavra nova "Viagens". A gravação começa sozinha quando a central conecta; o usuário não precisa ligar nada. Barra do topo conectada (copie do modelo 06).
Cabeçalho: título "Viagens" + chip "Gravando agora" (ponto vermelho `--danger` de 10 px + texto) + pílula: "A gravação começa sozinha quando a central conecta. Nada para ligar."

PAINEL ESQUERDO (370×396) = "Viagem escolhida" (a selecionada na lista; começa na 1ª):
 - Nome (22 px, 700), ex.: "Hoje, 14:20".
 - 3 linhas de resumo (17 px): "42 km · 18 km no gás" / "2 apagões em movimento · 4 engasgos" / "Aprendeu 12 pontos · 1 gravação sua (Curva K)". Os números mudam com a viagem escolhida.
 - Botões de 58 px, empilhados e alinhados à esquerda: "Compartilhar resumo" (primário --accent-btn; 1 toque; depois a pílula do cabeçalho fica fixa: "Resumo salvo em Downloads/OMEGAS · 38 KB"), "Pacote completo" (secundário; mesma ideia: "Pacote salvo em Downloads/OMEGAS · 2,1 MB") e "Apagar esta viagem" (contorno `#7a3036`, texto --danger; 1 toque, sem janela; a viagem some da lista, a seleção vai para a próxima, a pílula fica fixa "Viagem apagada." e aparece "Desfazer" na barra de baixo).

CARTÃO DIREITO (830×396): lista de 5 viagens (cada linha 64 px, é `<button>`; a selecionada tem borda `--accent` de 2 px e fundo `--accent-soft`). Dentro de cada linha, da esquerda para a direita: data e hora (19 px, 600; 130 px de largura), "NN km" (18 px), uma barra fina de "% no gás" (largura 110 px, altura 8 px, verde `--gas` sobre `--surface3`) com o número ("43 %"), uma mini linha do tempo (largura ~180 px, altura 24 px, feita de `div`s; marcadores: ✕ vermelho = apagão, ◇ âmbar = engasgo, ● azul = gravação sua; posições variadas) e o tamanho do arquivo (16 px, --muted). Dados das 5 viagens (data · km · % gás · apagões · engasgos · gravações suas · tamanho):
 1 "Hoje, 14:20" · 42 · 43 · 2 · 4 · 1 · "2,1 MB"
 2 "Hoje, 08:05" · 18 · 100 · 0 · 1 · 0 · "0,9 MB"
 3 "Ontem, 18:40" · 31 · 62 · 1 · 3 · 0 · "1,6 MB"
 4 "Ontem, 07:50" · 12 · 0 · 0 · 0 · 0 · "0,6 MB"
 5 "24/10, 17:10" · 67 · 71 · 0 · 2 · 2 · "3,4 MB"
Abaixo da lista, uma linha com: interruptor "Gravar sozinho ao conectar" com "Ligado" escrito ao lado (visual) e botão discreto "Ajustes da gravação ›" (visual). 
ESTADO VAZIO: quando a última viagem restante for apagada, o cartão mostra ícone + "Nenhuma viagem ainda" (24 px) + "Dirija com o app aberto. A gravação começa sozinha quando a central conecta." (18 px, --muted), e o painel esquerdo some os botões (fica só o texto "Sem viagem escolhida").
BARRA DE AÇÕES: "‹ Voltar" (link `09-Ferramentas.dc.html`) 1º; "Desfazer" aparece só depois de apagar (restaura a viagem apagada).
ESTADO: lista de viagens, índice selecionado, última apagada (para Desfazer), mensagem.

## 2) 14-Estados.dc.html (TAMANHO 1280×1240; `$preview` 1280×1240; ESTÁTICA, sem script de estado)
Título "Estados e avisos: o mesmo em todas as abas" (30 px) e uma frase (18 px, --muted): "Cada situação tem sempre o mesmo aviso, no mesmo lugar, com o mesmo caminho para resolver." Raiz com `height:1240px`. NÃO desenhe barra do topo nem navegação nesta tela. Grade de 2 colunas (`repeat(2,minmax(0,1fr))`, gap 20 px, padding 48 px) com 10 cartões (`--surface`, borda --line, raio 14, padding 18). Cada cartão tem um rótulo pequeno em caixa alta (14 px, espaçado, --dim) e, embaixo, a miniatura do componente (faixa de aviso, botões e textos reais, com botões de 58 px que são só visuais; botões "desabilitados" levam o atributo `disabled`). Os 10 cartões, em ordem:
 1 SEM CENTRAL (IGUAL EM TODA ABA): faixa cinza (`--surface2`, ícone de plug) "Sem central" (20 px, 700) + "Conecte o cabo USB da multimídia à central do gás e ligue a ignição. Se o Android pedir, permita o acesso; a conexão começa sozinha." (17 px). Embaixo, um botão desabilitado "Gravar na central" com o motivo "Sem central" ao lado.
 2 CENTRAL AINDA APRENDENDO (PORTA DE GRAVAÇÃO): faixa âmbar (`--warn-soft`, borda #6b5420) "A central ainda está aprendendo (2 de 3 ajustes)." + "Gravar fica bloqueado até ela terminar." + botão "Assumir o controle" (secundário, 58 px) + linha (15 px, --muted) "Depois será preciso reaprender o GNV no AutoCal."
 3 GRAVANDO EM ETAPAS: faixa azul (`--accent-soft`, borda #2a4a7e) "Etapa 1 de 2 · Mapa K · conferindo na central…" + barra de progresso de 2 segmentos (o 1º preenchido pela metade com --accent, o 2º vazio) + "Não desligue o carro nem tire o cabo." (16 px).
 4 GRAVADO: faixa verde (`--ok-soft`, borda #1d5a46, ícone de ✓) "Gravado e conferido pela central: Mapa K (6 células) e Curva K (12 pontos)." + botão "Desfazer" + nota (15 px) "Fica na tela até a próxima ação. Não some sozinho."
 5 NÃO CONFERE: faixa âmbar "Gravou, mas a central devolveu outro valor." + "O app regravou 1 vez e ainda não confere. Nada mais será gravado." + botão "Ver o que a central tem".
 6 CABO SOLTO: faixa vermelha (`--danger-soft`, borda #7a3036) "O cabo soltou durante a gravação." + "Não sei como ficou. Reconecte: o app confere sozinho."
 7 MAPA GRAVADO, CURVA PENDENTE: faixa âmbar fixa "Mapa K gravado. Curva K ainda não." + botão "Concluir curva" (primário).
 8 TENSÃO ALTA: uma célula da barra do topo em vermelho (borda e fundo `--danger-soft`/`#7a3036`): rótulo "Tensão" + "14,3" + "V" e abaixo o texto "Acima de 14,1 V o GNV desliga. Veja a bateria e o alternador do carro."
 9 TELA VAZIA QUE CONVIDA: ícone de gráfico + "Ainda sem pontos" (22 px, 700) + "Dirija um pouco com o app aberto. O gráfico aparece sozinho." (17 px).
 10 CARRO EM MOVIMENTO (NOVO): faixa âmbar "Pare o carro para gravar." + "Em movimento o app só mostra; gravar e limpar ficam travados." + botões "Gravar na central" e "Limpar pontos" desabilitados.
Cada faixa de aviso: raio 12, padding 14 px 16 px, ícone de 24 px à esquerda, texto principal 19–20 px/700 e apoio 16–17 px.
