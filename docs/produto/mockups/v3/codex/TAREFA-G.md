
# TAREFA G: refazer o BALÃO (arquivo `out/project/19-Balao.dc.html`)

1280×720, INTERATIVA, aba ativa da navegação = Ferramentas. Barra do topo conectada (Tensão 13,8 V · Pressão da admissão 0,39 bar · Inj. gasolina 3,44 ms · Rotação 1.174 · Nível com a 2ª bolinha acesa · lâmpada "No gás"; copie do modelo 06).

## O que é o balão (fatos do app; não invente nada fora disto)
É uma janelinha flutuante do Android que aparece POR CIMA de outros apps (o GPS, por exemplo) quando o OMEGAS não está na tela, com as leituras ao vivo. Só mostra: nunca grava nada na central. Hoje: um botão "Ω" que, ao tocar, abre 4 linhas pequenas em jargão (CÉLULA, STFT, PETROL, RPM, texto de 13 px); sem estado do gás, sem aviso, posição fixa que o usuário só arrasta. Some sozinho quando o OMEGAS volta à tela.
PROBLEMAS que a nova versão resolve: (1) fechado não mostra NADA útil; (2) jargão; (3) texto minúsculo para olhar de relance; (4) não avisa de problema (tensão acima de 14,1 V faz o gás desligar; gás acabando; falha da central; sem central) justo quando o usuário está no GPS; (5) tocar não leva ao app; (6) não escolhe lado nem tamanho.

## O balão novo (desenhe com `div` e SVG de traço; sem imagem)
- FECHADO (padrão): pílula de fundo `rgba(11,17,28,.94)`, borda de 2 px na cor do estado, raio 18, sombra suave. Médio = 190×76 px; Grande = 244×96 px. Conteúdo: lâmpada (círculo 16–20 px) + a PALAVRA do estado + o número que mais importa. Estados (ícone + palavra, nunca só cor):
   · "No gás" (verde `--gas`, borda `#1d5a46`) + "1.174 giros"
   · "Em gasolina" (âmbar `--warn`) + "1.174 giros"
   · "Pouco gás" (âmbar, ícone de triângulo) + "¼ do tanque"
   · "Tensão alta" (vermelho `--danger`, ícone de triângulo) + "14,3 V"
   · "Sem central" (cinza `--muted`, ícone de cabo desligado) + "—"
  Texto da palavra 22 px (Grande 26), número 20 px (Grande 24), sempre legível de relance.
- ABERTO (tocar no balão abre; tocar de novo fecha): painel de 270×300 (Médio) ou 330×360 (Grande), mesma borda de estado. De cima para baixo: linha do estado (lâmpada + palavra, 22 px); se houver aviso, uma faixa colorida com UMA frase ("Tensão acima de 14,1 V: o gás pode desligar." / "Gás no fim: a central volta para a gasolina." / "Sem central: ligue a ignição e confira o cabo USB."); depois as 4 leituras, rótulo 15 px `--muted` e valor 22 px em branco, tabulares: "Giros 1.174" · "Injeção de gasolina 3,44 ms" · "Correção da gasolina +2,1 %" · "Célula do Mapa K: 1.850 giros · 3,12 ms". Por fim o botão "Abrir o OMEGAS" (58 px, `--accent-btn`; aqui só visual) e, embaixo, em 14 px `--muted`, "Só mostra. Nunca grava na central."
- Pílula e painel têm alvo de toque ≥ 58 px de altura onde for tocável.

## Layout da tela
Cabeçalho (`left:32px;top:64px;width:1216px;height:56px`): título "Balão sobre outros apps" (28 px) + chip "Ligado" (verde)/"Desligado" (cinza) + UMA frase: "As leituras ficam por cima do mapa, sem você sair do GPS."
PAINEL ESQUERDO (`left:32px;top:128px;width:370px;height:396px`), título pequeno "AJUSTES DO BALÃO" (14 px espaçado), de cima para baixo:
 1. Interruptor 58 px "Balão sobre outros apps" com a palavra "Ligado"/"Desligado" escrita. Desligado: a prévia some e mostra o texto "O balão está desligado." com o botão "Ligar o balão" no centro da prévia.
 2. "Tamanho": rótulo 17 px + segmentos [Médio | Grande] (52 px).
 3. "Lado da tela": rótulo + segmentos [Esquerda | Direita] (52 px).
 4. Interruptor 58 px "Só quando houver aviso" (modo discreto). Ligado: o balão fica escondido enquanto está tudo bem e aparece sozinho quando algo pede atenção.
 5. Linha de permissão (17 px): ícone de visto verde + "Permissão para aparecer sobre outros apps: concedida." (o Android pede uma vez).
 Entre os blocos vão de 12 px; tudo cabe em 396 px.
CARTÃO DIREITO (`left:418px;top:128px;width:830px;height:396px`), rótulo "PRÉVIA SOBRE O MAPA" (14 px espaçado) no canto: um mapa de navegação GENÉRICO desenhado em SVG (sem marca de nenhum app): fundo azul-petróleo escuro, ruas em tons mais claros (linhas de 6–14 px), um quarteirão ou dois, a ROTA como linha grossa azul-clara com seta, o carro como seta branca, uma faixa de instrução no topo ("Em 300 m, vire à direita · Av. Central", fonte 20 px) e uma faixa de chegada embaixo ("12 min · 6,4 km"). O balão aparece sobre o mapa, encostado na borda do lado escolhido (a 16 px da borda, a 90 px do topo) com o tamanho escolhido, no estado escolhido. Tocar no balão da prévia abre/fecha.
 - Modo discreto ligado + estado "No gás" ou "Em gasolina": o balão NÃO aparece; no lugar, uma etiqueta tracejada na borda: "Balão escondido: nada pede atenção." Nos estados de aviso ele aparece.
BARRA DE AÇÕES (`left:32px;top:538px;width:1216px;height:70px;display:flex;gap:12px`): "‹ Voltar" (link `09-Ferramentas.dc.html`, 1º botão) e, ao lado, o seletor da prévia "Ver como fica:" (texto 16 px `--muted`) com 5 botões de 58 px na ordem No gás · Em gasolina · Pouco gás · Tensão alta · Sem central (o selecionado com `border:1px solid #2a4a7e;background:var(--accent-soft)`; cada um com a lâmpada na cor do estado). Largura total ≤ 1216 px.
Estado: `ligado` (true), `tamanho` ('medio'), `lado` ('direita'), `discreto` (false), `situacao` ('gas'|'gasolina'|'pouco'|'tensao'|'sem'), `aberto` (false). Tudo por clique, sem timers. A lista de situações e os valores de cada uma saem de um array em `renderVals()` (rótulo, palavra, número, cor, borda, aviso, ícone).
