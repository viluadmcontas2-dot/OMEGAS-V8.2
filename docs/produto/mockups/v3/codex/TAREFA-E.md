
# TAREFA E: padrões e telas pequenas (3 arquivos em `out/project/`)

## 1) `15-Padroes.dc.html` — folha de padrões (ESTÁTICA, tamanho 1600×1500; sem rolagem interna)
Muda o tamanho: raiz com `width:1600px;height:1500px`; no script use `data-props='{"$preview":{"width":1600,"height":1500}}'`. É a "folha de consulta" do design: tudo o que as telas repetem, desenhado UMA vez, com o nome e a regra ao lado. Fundo `--bg`, título da folha 30 px "Padrões do OMEGAS" + frase "Os mesmos blocos, nas mesmas posições, com as mesmas palavras, em todas as telas." Sem emoji. Texto de explicação ≥ 16 px; legenda ≥ 14 px.
Organize em grade de cartões `--surface` (borda `--line`, raio 14), 3 colunas, cada cartão com título (14 px, espaçado, `--dim`), o exemplo real e UMA linha de regra. Conteúdo, nesta ordem:
 1. Cores de estado (amostras grandes com nome e palavra de uso): Pronto/ok `--ok` · Atenção `--warn` · Falha `--danger` · Informação `--accent` · Neutro `--muted`. Regra: "Cor sempre vem com palavra e ícone."
 2. Tipografia: 30 título de tela · 22 título de cartão · 20 botão · 17 texto · 15–16 apoio · 14 legenda mínima; números grandes 24–32 tabulares. Regra: "Texto crítico nunca abaixo de 17 px no carro."
 3. Botões (todos 58 px de altura, verbo + objeto): principal (`--accent-btn`), secundário, perigo (vermelho, só para apagar), desligado (cinza, com cadeado e a razão), só ícone (com rótulo acessível). Mostre "Gravar 2 mudanças", "Guardar cópia", "Limpar 5 pontos", "Desfazer", "Gravar (carro em movimento)" desabilitado.
 4. Interruptor "Ligado/Desligado" com a palavra escrita, nos dois estados.
 5. Passo − / valor / + com lápis ✎ (digitar valor) e a linha "o que sobe / o que desce".
 6. Chips de estado: Pronta, 60 %, Falta, Aprendendo, Sem central, 1 falha, Tudo certo (ícone + palavra).
 7. Aviso FIXO de gravação: "Gravado e conferido pela central: 1.800 giros." + botão Desfazer (verde). E o aviso de erro: vermelho, diz o que fazer ("Não gravou. Pare o carro e toque em Gravar de novo.").
 8. Estado vazio (convida a agir: ícone, uma frase, um botão) e estado "Sem central" (o que fazer: "Ligue a ignição e conecte o cabo USB").
 9. Ajuda "?" em 1 toque: o cartão de 3 linhas: "O que é · O que fazer · O que acontece" (exemplo: tela Refino). Regra: "O balão de ajuda abre ao lado, nunca por cima do que você está mexendo, e fecha com o mesmo toque."
 10. Legenda do gráfico: gasolina = círculo vermelho-claro `#ff7a7a`; gás = quadrado verde `#5fe38a`; Curva K = linha azul; faixa tracejada = área segura. Regra: "Forma + cor, nunca só cor."
 11. Matriz Zona × Combustível (Pronta / 60 % / Falta) em miniatura.
 12. Grade de tela: 3 miniaturas desenhadas com `div` (1280×720, 1024×600, 800×480) mostrando topo, cabeçalho, painel à ESQUERDA, gráfico/cartão à DIREITA, barra de ações à esquerda e navegação de 5 abas; escreva as medidas (margem 32; painel 370; gráfico 830; alvo 58).
 13. Navegação inferior: 5 abas (AutoCal · Refino · Ajuste local · Ajuste global · Ferramentas), ativa/inativa/selo "12".
Se algum bloco não couber, simplifique o bloco, não a fonte.

## 2) `16-AutoCal-800x480.dc.html` e 3) `17-Ajuste-local-800x480.dc.html` (ESTÁTICAS, 800×480)
Raiz `width:800px;height:480px`; `data-props='{"$preview":{"width":800,"height":480}}'`. Mostram como as duas telas se adaptam à multimídia pequena. Use `ref/03-AutoCal.dc.html` (para 16) e `ref/06-Ajuste-local.dc.html` (para 17) como modelo de conteúdo e tokens; reorganize a geometria (não encolha tudo por `scale`!).
Escala 800×480: barra do topo 40 px (números 16–18 px bold, rótulos 12 px); cabeçalho `top:44px;height:38px` (título 22 px, UMA frase curta de ≤ 40 caracteres); conteúdo `top:88px;height:272px`: painel ESQUERDO `left:12px;width:250px`, gráfico/cartão DIREITO `left:274px;width:514px`; barra de ações `top:366px;height:52px` (botões de 48 px, à esquerda); navegação `top:424px;height:56px`, 5 abas só com ícone 22 px + rótulo 15 px (sem selo se faltar espaço; o selo "12" fica sobre o ícone). Texto de corpo ≥ 15 px, legenda ≥ 12 px, alvo de toque ≥ 48 px.
 - 16 (AutoCal aprendendo): chip "Aprendendo ● ● ○" + frase "Falta o GNV nas zonas 3 e 4." Painel esquerdo: bloco verde "Tudo certo: água 86 °C · gás ¼ · sem falhas" (2 linhas) e a matriz Zona 4..1 × Gasolina/GNV (Pronta / 60 % / Falta; Zona 1 marcada "agora"). Direita: gráfico estático (SVG) com as 4 zonas, uns 14 pontos (círculos gasolina, quadrados gás), a Curva K azul e a coluna dos quadradinhos da Aquisição À DIREITA do gráfico. Barra de ações: interruptor "Aprendizado automático Ligado", "Limpar pontos", "Ações avançadas ▴".
 - 17 (Ajuste local): a grade do Mapa K 12×12 com cores de calor, células 30×18 px, cabeçalhos de linha e de coluna de 40 px para tocar (selecionam a linha/coluna), uma região selecionada com borda branca, a célula "agora" com anel tracejado. Painel esquerdo: "Região do engasgo +4 %" (sugestão), segmentos Percentual · Somar · Definir (48 px), valor com − / + / ✎. Barra de ações: "Gravar 6 células" (principal), "Desfazer".
Em ambas, o texto nunca é cortado e nada rola. Ícones: SVG de traço inline.
