# TAREFA A: duas telas de Ferramentas (arquivos `out/project/10-Anti-apagao.dc.html` e `out/project/12-Avisos-da-central.dc.html`)

Ambas: 1280×720, INTERATIVAS, aba ativa da navegação = Ferramentas, barra do topo conectada (Tensão 13,8 V · Pressão da admissão 0,39 bar · Inj. gasolina 3,44 ms · Rotação 860 · Nível com a 2ª bolinha acesa · lâmpada "No gás" verde; copie do modelo 06).

## 1) 10-Anti-apagao.dc.html — tela "Anti-apagão"
Cabeçalho: título "Anti-apagão" + chip de estado ("Ligado" verde quando ligado; "Desligado" cinza quando desligado) + pílula com UMA frase: "Ao pisar na embreagem, segura o motor para não apagar."

PAINEL ESQUERDO (370×396):
1. Interruptor grande, ≥ 58 px de altura, com a palavra "Ligado" ou "Desligado" SEMPRE escrita ao lado (nunca só cor). Um toque troca e grava na hora; depois a pílula do cabeçalho mostra, fixa: "Ligado e conferido pela central." (ou "Desligado e conferido pela central.") e o botão "Desfazer" aparece na barra de baixo.
2. Campo "Rotação máxima para agir": valor grande "1.600 giros" (32 px) e, ao lado, botões "−50" e "+50" (58 px; cada toque grava na hora, como hoje) e um botão com lápis (`aria-label="Digitar o valor"`). Linha de apoio (15–16 px): "De 0 a 6.000. Valor de fábrica: 1.600."
3. Texto do efeito (16–17 px, 3 linhas no máximo): "Subir: ele age em mais situações e pode gastar mais gasolina (não comprovado). Descer: age só em giros mais baixos."

CARTÃO DIREITO (830×396), título pequeno "SUAS VIAGENS" (14 px, espaçado): gráfico de barras feito com `div`s (sem SVG), 4 colunas verticais, nesta ordem: 1.400 · 1.600 · 1.800 · 2.000 (rótulo do valor embaixo, 20 px). A altura de cada barra = quantos apagões em movimento o anti-apagão teria segurado, de um total de 20: 15, 19, 20, 20 (escreva "15 de 20", "19 de 20", "20 de 20", "20 de 20" em cima de cada barra, 18 px). Cores: a barra do valor de HOJE (1.600 no início) em `--accent`, com a etiqueta "Hoje"; a barra de SUGESTÃO (1.800) em `--ok`, com a etiqueta "Sugestão do app"; as outras em `--surface3`. Cada barra é um `<button>` de largura ≥ 120 px; tocar numa barra a seleciona (borda branca de 3 px) e mostra, acima do gráfico e à esquerda, o botão "Usar 1.800 giros" (verbo + objeto, 58 px, texto com o número da barra selecionada). Um toque nele grava na hora: o campo do painel passa para o valor novo, o "Hoje" muda de barra, a pílula fica "Gravado e conferido pela central: 1.800 giros." e aparece "Desfazer".
Legenda do gráfico (15–16 px, `--muted`): "Base: 20 apagões em movimento nas suas viagens gravadas. Acima de 1.800 não segura mais nenhum; só gasta mais gasolina (inferido)."
Rodapé do cartão, uma linha: ícone de viagem + "Sua última viagem: 0 apagões em movimento · 2 engasgos parado".

BARRA DE AÇÕES (esquerda, nesta ordem): "‹ Voltar" (link para `09-Ferramentas.dc.html`; sempre o 1º botão), "Restaurar fábrica" (volta a 1.600, grava na hora), "Desfazer" (só aparece depois de gravar; volta ao valor anterior), "Como funciona" (só visual), "Outros valores (em teste)" (só visual, discreto, sem destaque).
ESTADO: ligado (bool), giros (número, padrão 1600), barra selecionada, valor anterior (para Desfazer), mensagem. O botão "−50"/"+50" respeita o intervalo 0 a 6.000.

## 2) 12-Avisos-da-central.dc.html — tela "Avisos da central"
Cabeçalho: título "Avisos da central" + chip de estado (vermelho "1 falha"; verde "Tudo certo" depois de apagar) + pílula: "A central avisou 1 problema. Enquanto isso, ela usa a gasolina."

PAINEL ESQUERDO (370×396): contagem grande "13 OK · 1 falha" (OK em --ok, falha em --danger; 32 px) e abaixo "14 verificações feitas agora, sem gravar nada." (17 px, --muted). Botões (58 px, empilhados, alinhados à esquerda): "Apagar avisos de falha" (secundário; 1 toque; depois a pílula fica fixa: "Avisos apagados. A central confere de novo sozinha." e a lista passa a mostrar as 14 linhas OK; chip "Tudo certo") e "Ver detalhes" (alterna uma 2ª linha de 14 px em cada item, com "o que a central faz": "Volta para a gasolina" ou "Só avisa").

CARTÃO DIREITO (830×396): lista em 2 colunas de 7 linhas (cada linha ~50 px de altura; `display:grid` com `repeat(2,minmax(0,1fr))`). Cada linha: ícone de estado (✓ verde = OK, ✕ vermelho = Falha, ! âmbar = Atenção; sempre ícone + palavra, nunca só cor), nome (18 px, 600) e a palavra do estado à direita da linha ("OK" / "Falha"). As 14 verificações, em ordem (nome → o que a central faz):
 1 Injetores de gás → Volta para a gasolina
 2 Válvula do redutor → Volta para a gasolina
 3 Sensor de pressão do gás → Volta para a gasolina
 4 Sensor de pressão da admissão → Volta para a gasolina
 5 Termômetro do gás → Volta para a gasolina  ← ESTA é a falha do exemplo
 6 Comutador presente → Só avisa
 7 Sensor de alta pressão → Só avisa
 8 Gás frio demais (abaixo de −15 °C) → Só avisa
 9 Isolamento da baixa pressão → Só avisa
 10 Isolamento da alta pressão → Só avisa
 11 Pressão baixa → Só avisa
 12 Pressão alta demais → Só avisa
 13 Leitura da alta pressão faz sentido → Só avisa
 14 Pouco gás (pressão do gás baixa) → Só avisa
A linha 5 (enquanto houver falha) fica destacada: fundo `--danger-soft`, borda `1px solid #7a3036`, e uma 2ª linha com "O que fazer: mostre este aviso ao instalador." (15 px). Use `sc-for` com um array calculado em `renderVals()` (cada item com `nome`, `acao`, `estado`, flags `ok`/`falha` e cores prontas).
BARRA DE AÇÕES: "‹ Voltar" (link para `09-Ferramentas.dc.html`) como 1º botão; depois os botões do painel não se repetem aqui.
ESTADO: `apagado` (bool) e `detalhes` (bool).
