
# TAREFA J: balão refeito (v2) e nova tela do nível do gás (arquivos `out/project/19-Balao.dc.html` e `out/project/20-Nivel-do-gas.dc.html`)

Leia a LAYOUT-V3 (acima). Os dois arquivos são INTERATIVOS. A barra do topo e a navegação (aba ativa = Ferramentas) já estão certas no modelo `ref/19-Balao.dc.html`: copie-as sem mexer. Para o 20, copie a barra do topo e a navegação do próprio 19.

## 1) `19-Balao.dc.html` — editar o arquivo em `ref/` (mesmo nome)
O dono GOSTOU do balão. Pediu: (a) as BOLINHAS DO GÁS (nível, 5 bolinhas como na barra do topo) dentro do balão; (b) mais de um ESTILO, inclusive um bem discreto; (c) aproveitar o espaço (LAYOUT-V3); (d) rótulos curtos "RPM" e "MAP".
- Layout V3: Voltar no cabeçalho (link `09-Ferramentas.dc.html`), sem barra embaixo, painel e cartão com `height:496px`. O seletor "Ver como fica:" (No gás · Em gasolina · Pouco gás · Tensão alta · Sem central) passa para DENTRO do cartão da prévia, embaixo, centralizado (botões de 48 px); o rótulo "PRÉVIA SOBRE O MAPA" fica no canto de cima do cartão. O mapa genérico ocupa o cartão todo (redesenhe o SVG para 830×496).
- Bolinhas do gás: na pílula e no painel aberto, uma fileira de 5 círculos de 14–16 px (acesos conforme o nível; "Pouco gás" = 1 acesa; "Sem central" = todas apagadas e "—"), igual à barra do topo; no painel aberto, uma linha "Nível do gás" com as bolinhas e a palavra ("Pouco mais de ¼").
- ESTILOS (segmentos no painel esquerdo, rótulo "Estilo do balão"): 
   · Compacto (padrão): pílula como hoje + bolinhas (190×92 médio / 244×112 grande): linha 1 lâmpada + palavra do estado; linha 2 bolinhas + número ("1.174 RPM").
   · Mínimo: pílula bem pequena (140×48 médio / 176×60 grande): lâmpada + 5 bolinhas, sem número. Em aviso troca as bolinhas pelo ícone de triângulo + palavra curta ("Tensão alta").
   · Completo: painel sempre aberto (sem fechar), com estado, aviso, bolinhas e as 4 leituras (RPM, Inj. gasolina, Correção gasolina, Mapa K · célula) e o botão "Abrir o OMEGAS".
  O "Só quando houver aviso" continua como interruptor.
- Painel esquerdo (370×496): interruptor "Balão sobre outros apps"; "Estilo do balão" (3 segmentos de 52 px); "Tamanho" (Médio | Grande); "Lado da tela" (Esquerda | Direita); interruptor "Só quando houver aviso"; linha de permissão concedida; "O balão só mostra. Nunca grava na central." (15 px).
- Rotule as leituras do painel aberto: "RPM", "Inj. gasolina", "Correção gasolina", "Mapa K · célula". Mantenha o painel aberto compacto (≈ 306 px de largura no Médio) e NUNCA tampando o centro do mapa.

## 2) `20-Nivel-do-gas.dc.html` — tela nova, sub-tela de Ferramentas › Peças do gás
Dono: "garantir que a leitura do nível do gás vai ficar realmente precisa". O nível aparece em 5 bolinhas (como o comutador do carro). O sensor oscila (o gás balança, o carro vibra) e precisa ser CALIBRADO em dois pontos: tanque cheio e reserva. O app já tem "registrar abastecimento" e pode perceber o abastecimento sozinho (a leitura sobe de repente).
Cabeçalho: Voltar (→ `09-Ferramentas.dc.html`) + "Nível do gás" + chip ("Leitura estável" verde | "Oscilando" âmbar) + pílula de UMA frase: "As bolinhas mostram quanto gás resta. Acerte uma vez e fica preciso."
PAINEL ESQUERDO (370×496):
  1. SÓ quando a situação for "Abasteceu": cartão de sugestão (borda `#1d5a46`, fundo `--ok-soft`): "Parece que você abasteceu" / "O nível subiu de ¼ para cheio." + botão primário "Marcar como cheio" (1 toque).
  2. Rótulo "ACERTAR O NÍVEL" e dois cartões de 96 px, botão à esquerda: "Tanque cheio" · valor "3,82 V · marcado hoje 13:40" (ou "não marcado") · botão "Marcar cheio" (usa a leitura de agora); "Reserva" · valor "0,95 V · não marcado" · botão "Marcar reserva". Cada toque grava na hora e o valor passa a "marcado agora"; aviso fixo "Gravado: tanque cheio em 3,82 V." acima dos botões do fundo, e o botão "Desfazer" no fundo (só depois de marcar).
  3. Ajuda (15 px muted): "Com o tanque cheio, marque Cheio. Quando a luz de reserva acender, marque Reserva."
  4. No pé, em 14 px âmbar: "PRECISA OK: calibrar em dois pontos e usar a média das leituras (proposta)."
CARTÃO DIREITO (830×496):
  - Topo: as 5 bolinhas GRANDES (círculos de 56 px, vão 18 px) acesas conforme o nível, com a palavra grande ao lado (ex.: "Pouco mais de ¼", 28 px); abaixo "Leitura da central: 2,31 V" (28 px, números tabulares).
  - Meio: a RÉGUA de calibração: barra horizontal de ~700 px com 5 faixas (uma por bolinha) entre "Reserva 0,95 V" e "Cheio 3,82 V", marcadores verticais nos limites e um ponteiro "agora" na leitura; legendas ≥ 14 px. Se Cheio ou Reserva não estiver marcado, o extremo fica tracejado com "falta marcar".
  - Embaixo: mini-gráfico de linha (SVG, ~700×110) com as últimas 40 leituras (faixa mínimo–máximo em cinza) e a frase de estabilidade (estável: "Estável nas últimas 20 leituras: o nível é confiável."; oscilando: "Oscilando (±0,4 V): o app usa a média das últimas leituras. Pare em local plano para conferir.").
  - Seletor da prévia dentro do cartão, canto inferior direito: rótulo "Ver como fica:" + 3 botões de 44 px: Estável · Oscilando · Abasteceu.
Estado: `situacao` ('estavel'|'oscilando'|'abasteceu'), `cheio` (número|null), `reserva` (número|null), `ultimo` (para Desfazer), `aviso`. As bolinhas acesas vêm do cálculo (leitura entre reserva e cheio dividida em 5 faixas); marcar cheio/reserva recalcula as bolinhas e a régua. Selo ATIVIDADE DA CENTRAL no topo ao marcar.
