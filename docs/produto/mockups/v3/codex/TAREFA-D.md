
# TAREFA D: a tela FERRAMENTAS (arquivo `out/project/09-Ferramentas.dc.html`)

É a tela que o dono mais quer ver. 1280×720, INTERATIVA, aba ativa da navegação = Ferramentas, barra do topo conectada (Tensão 13,8 V · Pressão da admissão 0,39 bar · Inj. gasolina 3,44 ms · Rotação 860 · Nível com a 2ª bolinha acesa · lâmpada "No gás"; copie do modelo 06).
Referências de conteúdo já prontas (leia, para não contradizer): `ref/10-Anti-apagao.dc.html`, `ref/12-Avisos-da-central.dc.html`, `ref/13-Viagens.dc.html` e o inventário `ref/18-Funcoes.dc.html` (linhas de "Ferramentas").

## Ideia (leia antes de desenhar)
Ferramentas hoje exige "Ver ›" em tudo e abrir página dentro de página. A nova tela:
- tem uma 1ª página INTELIGENTE, "Para você agora", onde o app já cruzou as viagens e a central e diz, em 1 a 3 cartões, o que merece o seu toque, com o botão que resolve na hora;
- põe o controle simples DENTRO do cartão (interruptor, − / +, botão), sem abrir página; só abre outra tela quando o assunto é grande (10, 11, 12, 13, 19);
- o cartão só de leitura NÃO tem botão: tem a etiqueta fixa "Só leitura" (ícone de cadeado + palavra), para o usuário nunca procurar o que não existe.
Sem barras decorativas, sem gráfico enfeite. Mostre só o que informa. Quanto mais óbvio, melhor.

## Layout (diferente do modelo 06 de propósito; é o layout já aprovado das Ferramentas, evoluído)
Não há cabeçalho de tela nem barra de ações separados. Tudo cabe entre a barra do topo (56 px) e a navegação (top:636px).
- LISTA DE GRUPOS à esquerda: `left:32px;top:64px;width:300px;height:560px`. Sete botões de 72 px de altura, vão de 8 px (7×72+6×8 = 552). Cada botão: bolinha de estado (10 px) + nome (20 px, 700) + linha de estado (16 px, `--muted`; colorida quando há alerta; sempre com palavra). O ativo tem `border:1px solid #2a4a7e;background:var(--accent-soft)` e uma faixa azul de 4 px na borda esquerda; os outros `background:var(--surface);border:1px solid var(--line)`. Os sete, nesta ordem, com a linha de estado inicial:
  1. Para você agora · "3 coisas" (azul; o número muda conforme o usuário resolve) 
  2. Evitar apagão · "Anti-apagão ligado · 1.600 giros" (verde)
  3. Gasolina ⇄ gás · "No gás agora" (verde)
  4. Peças do gás · "Gás pouco mais de ¼" (verde)
  5. Diagnóstico · "1 falha" (vermelho)
  6. Viagens e arquivos · "Gravando agora" (verde)
  7. Ajustes do app · "Tudo certo" (verde)
- PAINEL à direita: `left:348px;top:64px;width:900px;height:560px`. De cima para baixo: linha de título (`height:48px`: título do grupo 28 px/700 + à direita do título, na mesma linha, uma pílula de UMA frase de 17 px com o que o grupo faz); cartões a partir de `top:60px` dentro do painel; TIRA DE ESTADO fixa no fim (`bottom:0;height:56px`): à esquerda o botão "Desfazer" (58 px; só aparece depois de gravar algo) e ao lado o aviso FIXO (nunca some sozinho). Sem gravação recente o aviso é: "Um toque e pronto: o app grava e confere na central." (--muted).
- CARTÃO padrão: `display:flex;align-items:center;gap:16px;box-sizing:border-box;padding:0 16px;background:var(--surface);border:1px solid var(--line);border-radius:14px`, altura 92 px (1 controle) ou 126 px (2 controles empilhados, vão 8 px). À ESQUERDA a coluna do controle (largura 220 px); à direita o texto: título 22 px/700; linha de valor 18 px/600 (colorida: ok / warn / danger / branca); linha de efeito 16 px `--muted`, 1 linha. Vão entre cartões 10 px. Cartão "Só leitura": no lugar do controle, a etiqueta (cadeado + "Só leitura", 16 px, `--muted`, borda tracejada `1px dashed var(--line)`, altura 44 px) e o cartão é um pouco mais baixo: 84 px.
- Interruptor: caixa de 58 px de altura com a PALAVRA "Ligado"/"Desligado" sempre escrita ao lado (nunca só cor); ligado = fundo `--ok-soft`, borda `#1d5a46`, texto `--gas`; desligado = fundo `--surface2`, borda `--line`, texto `--muted`.
- Passo − / +: três peças lado a lado de 58 px de altura: botão "−" (58 px), valor (texto 22 px/700), botão "+" (58 px).

## Os sete grupos (conteúdo exato; títulos em PT-BR leigo)
Todas as "gravações" abaixo são fictícias, de 1 toque, sem janela. Depois de cada uma: aviso fixo "Gravado e conferido pela central: …" na tira de estado e botão "Desfazer" (volta ao valor anterior e muda o aviso para "Desfeito e conferido pela central: …"). Só existe UM "Desfazer" por vez (o da última gravação).

### 1) Para você agora (grupo padrão aberto). Pílula: "O app já cruzou suas viagens e a central. Estes pedem o seu toque."
Três cartões de 110 px (controle à esquerda, 220 px):
 a. Controle: botão primário `--accent-btn` "Usar 1.800 giros". Título "O motor apagou 2 vezes em movimento". Valor (warn) "Hoje 1.600 giros seguram 19 de 20 apagões das suas viagens." Efeito "Com 1.800 giros seguraria 20 de 20; gasta um pouco mais de gasolina (inferido)." Ao tocar: grava; o controle vira a etiqueta verde "Feito" (ícone de visto + palavra), o valor vira (ok) "1.800 giros gravados e conferidos pela central."; o aviso fixo e o "Desfazer" aparecem; a linha de estado do grupo 2 passa a "Anti-apagão ligado · 1.800 giros"; o contador do grupo 1 cai de "3 coisas" para "2 coisas" (e "1 coisa" quando outro for resolvido; singular certo).
 b. Controle: link-botão secundário "Ver aviso" (`<a href="12-Avisos-da-central.dc.html">`). Título "A central avisou 1 problema". Valor (danger) "Termômetro do gás". Efeito "Enquanto isso ela usa a gasolina. Mostre este aviso ao instalador." Este cartão NÃO é resolvido aqui (só some quando a central deixar de avisar); o contador nunca passa de "1 coisa".
 c. Controle: botão secundário "Compartilhar resumo". Título "Sua última viagem está pronta". Valor "Hoje, 14:20 · 42 km · 43 % no gás". Efeito "Manda um resumo de uma página para o instalador." Ao tocar: vira "Feito" e o valor "Resumo salvo em Downloads/OMEGAS · 38 KB"; o aviso fixo diz o mesmo; sem "Desfazer" (não grava na central); o contador cai.
Se o usuário resolveu a e c, o contador mostra "1 coisa" e o cartão b continua. (Não desenhe estado vazio aqui.)

### 2) Evitar apagão. Pílula: "Segura o motor para ele não apagar."
 a. Cartão de 126 px, controle = interruptor "Ligado"/"Desligado" (em cima) + link-botão secundário "Ajustar giros ›" (embaixo, vai para `10-Anti-apagao.dc.html`). Título "Anti-apagão". Valor (ok) "Ligado: age abaixo de 1.600 giros." (o número acompanha o estado; desligado = (muted) "Desligado: o motor pode apagar ao pisar na embreagem."). Efeito "Ao pisar na embreagem, segura o motor para não apagar." O interruptor grava na hora.
 b. "Saída do corte" · Só leitura · valor "0 injeções de gasolina ao voltar a acelerar." · efeito "Quando você tira o pé e depois acelera de novo."
 c. "Marcha lenta" · Só leitura · valor "Só gás na marcha lenta." · efeito "Motor ligado sem acelerar, na parada ou manobra." e, abaixo do cartão, uma linha de 16 px `--muted`: "Sugestão do app: segurar com gasolina na parada. O app só sugere; gravar isso ainda não existe." (honesto: a escrita é da próxima versão).

### 3) Gasolina ⇄ gás. Pílula: "Como a central passa entre gasolina e gás."
 a. "Quando o gás entra" · Só leitura · valor "Ao acelerar, acima de 1.200 giros, água a 40 °C." · efeito "Agora: já está no gás." (linha ao vivo, em verde, que cruza giros e água reais com esses limites; se não estivesse, diria o que falta).
 b. "Troca de gasolina para gás" · Só leitura · valor "Um cilindro por vez." · efeito "Como a central passa da gasolina para o gás."
 c. Cartão de 126 px "Volta para gasolina": controle em cima = interruptor Ligado/Desligado; embaixo = passo − / + da pressão mínima do gás (valor "1,25 bar", passo 0,05, de 0,50 a 2,50; cada toque grava na hora). Valor (ok) "Volta se a pressão do gás cair abaixo de 1,25 bar." (acompanha o passo; desligado = (muted) "Desligada: o motor pode apagar quando o gás acaba."). Efeito "Espera 0 s. Protege o motor quando o gás acaba."

### 4) Peças do gás. Pílula: "O que o gás usa para funcionar."
 a. "Nível do gás" · controle: botão secundário "Regular o nível" (só visual) · valor "Pouco mais de ¼ do tanque." · efeito "Acerte as bolinhas do nível com o seu medidor."
 b. "Injetores" · Só leitura · valor "Controle ligado, abertura mínima 2,5 ms." · efeito "Os bicos que soltam o gás no motor."

### 5) Diagnóstico. Pílula: "A central se confere sozinha. Veja o que ela achou."
 a. "Avisos da central" · controle: link-botão "Ver avisos" (`12-Avisos-da-central.dc.html`) · valor (danger) "1 falha: Termômetro do gás." · efeito "14 verificações, cada uma com o que fazer."
 b. "O carro está apagando?" · controle: botão primário "Descobrir por quê" (`<a href="11-Carro-apagando.dc.html">`) · valor "O app olha os últimos apagões e diz o motivo mais provável." · efeito "Cruza giros, tensão, gás e viagens. Não grava nada."
 c. Cartão de 126 px "Registro do app": controle = botão secundário "Salvar registro" (em cima; 1 toque; valor passa a "Registro salvo em Downloads/OMEGAS") + botão secundário "Testar o app" (embaixo; 1 toque; valor passa a (ok) "O app respondeu em todas as telas.") · valor inicial (ok) "Tudo certo." · efeito "Avisos e teste do próprio app."
 d. "Teste de gravação" · controle: botão secundário "Testar" · valor "Só com o carro parado." · efeito "Confere se a central aceita gravar." Ao tocar: valor (ok) "A central aceitou gravar e conferiu." (exemplo).

### 6) Viagens e arquivos. Pílula: "O app grava cada viagem sozinho. Aqui você guarda e manda."
 a. "Viagens" · controle: link-botão "Ver viagens" (`13-Viagens.dc.html`) · valor (ok) "Gravando agora. 12 viagens salvas." · efeito "O app grava sozinho cada viagem."
 b. "Gravar sozinho ao conectar" · controle: interruptor (começa Ligado) · valor (ok) "Ligado: começa quando a central conecta." · efeito "Nada para ligar nem desligar na hora de dirigir." (desligado = (warn) "Desligado: nenhuma viagem será gravada.")
 c. "Cópia de segurança" · controle: botão primário "Guardar cópia" · valor "Última cópia: hoje, 13:40." · efeito "Guarda o aprendizado atual e as viagens." Ao tocar: valor (ok) "Cópia guardada agora." + aviso fixo "Cópia guardada em Downloads/OMEGAS · 4,8 MB."; sem "Desfazer".
 d. "Exportar e sobre" · controle: botão secundário "Compartilhar tudo" (só visual: aviso fixo "Pacote completo salvo em Downloads/OMEGAS.") · valor "Tudo em um arquivo só." · efeito "Mandar as viagens para o técnico. Versão do app: 14."

### 7) Ajustes do app. Pílula: "Como o app se comporta no aparelho."
 a. "Refino automático" · interruptor (começa Desligado) · valor Desligado: "Desligado: o Refino só sugere quando você abre a aba." Ligado: (ok) "Ligado: o app monta a correção sozinho e avisa. Só grava com o seu toque." · efeito "Nunca grava na central sozinho."
 b. "Bateria" · Só leitura · valor (ok) "Sem limite: o Android não pausa o app." · efeito "Se aparecer 'Permitir', toque para liberar."
 c. Cartão de 126 px "Balão sobre outros apps": controle = interruptor (começa Ligado) + link-botão secundário "Ver o balão ›" (`19-Balao.dc.html`) · valor Ligado (ok) "Ligado: leituras por cima do mapa." / Desligado (muted) "Desligado: só aparecem dentro do app." · efeito "Mostra as leituras com o GPS aberto."
 d. "Como funciona" · controle: botão secundário "Rever o passo a passo" (só visual; aviso fixo "O passo a passo abre em 4 cartões.") · valor "4 cartões: o que é, ligar, dirigir, aplicar." · efeito "Para quem usa o app pela primeira vez."

## Regras extras
- Todo cartão que muda de aparência faz isso por estado (`state` + `renderVals`), sem timers.
- Estado sugerido: `grupo` (texto: agora|apagao|troca|pecas|diag|viagens|ajustes), `anti` (bool, true), `giros` (1600), `volta` (bool, true), `pressao` (1.25), `s1`, `s3` (bool), `gravarSozinho` (bool, true), `refinoAuto` (bool, false), `balao` (bool, true), `registro`, `teste`, `copia` (textos), `ultimo` (objeto com o que desfazer, ou nulo), `aviso` (texto fixo).
- A lista de grupos e os cartões saem de arrays em `renderVals()` com `sc-for`; cada cartão traz flags (`somenteLeitura`, `temInterruptor`, `temPasso`, `temBotao`, `temLink`, …) e cores prontas. Um cartão = um item do array; use `sc-if` para as partes opcionais.
- Cada grupo mostra só os cartões dele (os arrays são por grupo; escolha pelo `grupo`).
- A lista de grupos é um conjunto de `<button type="button">` (cada um 72 px); tocar troca de grupo e NÃO apaga o aviso fixo.
