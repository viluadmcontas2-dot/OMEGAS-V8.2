
# TAREFA P: refazer a tela 15 "Padrões do OMEGAS" (arquivo `ref/15-Padroes.dc.html` é o modelo ANTIGO → escreva `out/project/15-Padroes.dc.html`)

É uma folha de documentação ESTÁTICA (sem script) de 1600 px de largura; a altura é a que o conteúdo pedir (informe em `out/project/PROGRESSO.md` e use a MESMA altura na raiz e em `$preview`). Copie o estilo e o esqueleto de `ref/15-Padroes.dc.html` (raiz com variáveis, título, subtítulo, grade de 3 colunas de cartões `--surface` com título "NN · TÍTULO" em 14 px, a faixa da navegação inferior no fim). Mantenha os cartões abaixo e ATUALIZE/ACRESCENTE os indicados. Cartão com `min-height` e padding 18 para o conteúdo CABER (nada passa da borda). Reaproveite o desenho dos cartões que ficam iguais (copie o HTML do ref).

Ficam IGUAIS (copie): 01 Cores de estado · 02 Tipografia · 04 Interruptor · 05 Passo e digitação · 06 Chips de estado · 08 Vazio e sem central · 09 Ajuda em um toque · 10 Legenda do gráfico.

MUDAM:
- 03 BOTÕES: mantenha "Gravar 2 mudanças" (azul) + "Desfazer", "Guardar cópia" + "?", e o vermelho "Limpar 5 pontos"; TROQUE o botão cinza com cadeado "Gravar (carro em movimento)" por um botão cinza desabilitado "Escolha um trecho e ajuste" (sem cadeado; o motivo está dentro do botão). Legenda: "58 px de altura · verbo + objeto · o botão cinza diz o que falta".
- 07 AVISOS FIXOS → "07 · ATIVIDADE DA CENTRAL": os 4 selos que ficam no canto direito da barra do topo (40 px de altura, raio 10, 16 px bold), um por linha: azul (`background:var(--accent-soft);border:1px solid #2a4a7e`) com ícone de setas "Gravando Curva K · 5 pontos"; azul "Conferindo na central · 3 de 5"; verde (`var(--ok-soft)`, borda `#1d5a46`, texto `var(--ok)`) com visto "Gravado e conferido"; vermelho (`var(--danger-soft)`, borda `#7a3036`) com aviso "Não gravou". Legenda: "Gravar não trava a tela. O selo fica até a próxima ação; Desfazer fica no painel."
- 11 ZONA × COMBUSTÍVEL → "11 · TOQUE PARA APAGAR": desenhe a matriz como na tela 03: linhas Zona 4, Zona 3, Zona 2, Zona 1 (com a legenda pequena "a fundo", "firme", "normal", "leve · agora" sob o nome), duas colunas de células (Gasolina e GNV) com estados "Pronta ✓" (verde), "60 %" (amarelo), "Falta" (cinza) e um pequeno ícone de borracha nas células que não estão vazias; ENTRE os nomes e as células o texto VERTICAL "TOQUE PARA APAGAR" em coral (`color:#ff7a7a`, 14 px, 700, `writing-mode:vertical-rl;transform:rotate(180deg)`, letter-spacing .12em). Legenda: "Tocar na célula apaga aquela zona daquele combustível, na hora, sem janela. A central reaprende sozinha."
- 12 GRADE DE TELA: mantenha as 3 miniaturas (1280×720, 1024×600, 800×480) mas redesenhe-as com o layout novo: barra do topo, cabeçalho, painel esquerdo + cartão grande à direita ocupando até a navegação, SEM faixa de ações. Legenda: "1280: margem 32 · painel 370 · gráfico 830 · de 128 a 624 · alvo 58. Ações no fundo do painel esquerdo."
- 13 NAVEGAÇÃO INFERIOR: mantenha como está.

ACRESCENTE (novos, mesma grade, antes da navegação inferior; renumere 13 e 14 como navegação e barra do topo):
- 13 · BARRA DO TOPO: desenhe a barra (56 px de altura, largura 1216) com as 6 células NA ORDEM de prioridade do dono: [● No gás] · [Nível ●●○○○] · [Inj. gasolina 3,44 ms] · [MAP 0,39 bar] · [RPM 1.174] · [Tensão 13,8 V]; abaixo a regra: "Combustível atual primeiro, tensão por último. Rótulos curtos MAP e RPM só aqui; no texto corrido, 'pressão do gás' e 'giros'."
- 14 · LIMPEZA SEM TRABALHO: mostre o padrão "um toque apaga em lote": três linhas, cada uma com um botão e a consequência em texto — "Zona 3 · GNV (toque na célula)" → "apaga os pontos daquela zona"; "Limpar 4 pontos fora da curva" → "apaga só os pontos com anel tracejado"; "Abaixo de 1.200 giros" (interruptor Ligado) → "a central ignora e apaga sozinha os pontos de marcha lenta". Legenda: "Apagar vários pontos é sempre um toque; a central reaprende o que faltar."

A navegação inferior fica no fim, ocupando a largura toda.

Vocabulário: "central", "giros"; sem roxo; texto de corpo ≥ 16 px, legenda ≥ 14 px.
