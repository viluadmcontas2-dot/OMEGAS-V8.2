
# TAREFA F: duas telas (arquivos `out/project/11-Carro-apagando.dc.html` e `out/project/03b-AutoCal-em-movimento.dc.html`)

Ambas 1280×720, INTERATIVAS. Modelos: `ref/06-Ajuste-local.dc.html` (estrutura), `ref/03-AutoCal.dc.html` (gráfico e matriz; reaproveite o SVG do gráfico do 03 como está), `ref/10-Anti-apagao.dc.html` (aviso fixo + Desfazer).

## 1) 11-Carro-apagando.dc.html — "Carro apagando"
Aba ativa = Ferramentas. Barra do topo conectada e normal (Rotação 860, lâmpada "No gás"). Cabeçalho: título "Carro apagando" + chip vermelho "2 apagões em movimento" + pílula de UMA frase: "O app olhou o que aconteceu antes de cada apagão e achou o motivo mais provável."
IDEIA: o usuário não precisa descrever o problema. O app já viu os apagões na telemetria e diz o motivo. Tela 100 % de leitura + UM botão que resolve.
PAINEL ESQUERDO (370×396) "O QUE O APP VIU" (14 px espaçado): linha do tempo vertical do apagão ESCOLHIDO, sem barras decorativas, 5 linhas de 17–18 px com hora e fato (números reais da telemetria, em branco e tabulares):
   14:18:02 · Rodando a gás · 1.250 giros · 13,9 V
   14:18:05 · Giros caíram para 700 em 1 s
   14:18:06 · Motor apagou (0 giros)
   14:18:06 · Anti-apagão: ligado, age abaixo de 1.600 giros
   14:18:06 · Gás ¼ · pressão do gás 2,3 bar · tensão 13,9 V
 Acima da linha do tempo, um seletor com os 2 apagões ("Hoje 14:18" e "Ontem 18:40"), dois botões de 58 px lado a lado; trocar muda a linha do tempo e a lista da direita: o 2º apagão (ontem 18:40) tem os fatos "Rodando a gás · 1.100 giros · 14,3 V", "Tensão subiu acima de 14,1 V", "Motor apagou", "Anti-apagão: ligado, 1.600 giros", "Gás ½ · pressão do gás 2,4 bar".
CARTÃO DIREITO (830×396) "O QUE FAZER" (14 px espaçado): até 3 cartões de causa, do mais provável ao menos, cada um com: selo de confiança (Alta/Média/Baixa, ícone + palavra, nunca só cor), frase da causa (20 px/700), a evidência em números (17 px, `--muted`), e à ESQUERDA o botão que resolve (58 px). Para o apagão de HOJE:
   1. Alta · "O anti-apagão não segurou o motor." · "Os giros caíram de 1.250 para 700 em 1 s e ele age abaixo de 1.600. Com 1.800 giros ele teria segurado 20 de 20 apagões das suas viagens." · botão primário "Usar 1.800 giros" (1 toque; grava; o cartão vira verde: "Gravado e conferido pela central: 1.800 giros." + botão "Desfazer" na barra de baixo; a causa passa a "Resolvida")
   2. Baixa · "O gás estava acabando." · "Nível ¼ e pressão do gás 2,3 bar, acima do limite de 1,25 bar. Pouco provável." · botão secundário "Ver Volta para gasolina" (`09-Ferramentas.dc.html`)
   3. Baixa · "A tensão subiu demais." · "13,9 V, abaixo de 14,1 V. Não foi isso." · sem botão; chip "Descartada" (cinza)
 Para o apagão de ONTEM: 1. Média · "A tensão passou de 14,1 V e o GNV desligou." · "Subiu para 14,3 V. Acima de 14,1 V a central volta para a gasolina e o motor pode apagar." · botão secundário "Ver Ajuste global" (`07-Ajuste-global.dc.html`); 2. Baixa · "O anti-apagão não segurou." · "Com 1.600 giros 19 de 20 são seguros; este foi o que escapou." · botão "Usar 1.800 giros" (mesmo comportamento); 3. Descartada · "O gás acabando" · "Nível ½ e 2,4 bar."
 Rodapé do cartão (16 px, `--muted`): "Inferido das suas viagens gravadas; não é prova de causa."
BARRA DE AÇÕES (esquerda): "‹ Voltar" (link `09-Ferramentas.dc.html`), "Compartilhar com o instalador" (1 toque; vira aviso fixo "Resumo do apagão salvo em Downloads/OMEGAS · 12 KB"), "Desfazer" (só depois de gravar).
ESTADO: `apagao` (0|1), `usado` (bool), aviso.

## 2) 03b-AutoCal-em-movimento.dc.html — AutoCal com o carro andando
Copie `ref/03-AutoCal.dc.html` e mude SÓ o necessário. Ideia: com o carro em movimento o motorista só PODE OLHAR; o que escreve ou apaga fica travado, mas o aprendizado continua (ele é automático).
 - Chip de estado do cabeçalho vira âmbar "Em movimento" (ícone de carro) e a pílula diz: "Aprendendo. Pare o carro para mexer nos pontos."
 - Os números do painel esquerdo e a matriz ficam MAIORES (texto ≥ 22 px nas células; títulos 20 px); a matriz continua igual na informação.
 - "Limpar pontos" e "Ações avançadas ▴" ficam desabilitados (cinza, cadeado, e o motivo escrito no próprio botão: "Limpar pontos · parado"). O interruptor "Aprendizado automático" continua ativo (é seguro).
 - Sem sugestão de "Limpar N pontos" no painel; no lugar, uma linha de 17 px: "Quando parar, o app mostra os pontos fora da curva."
 - No cartão direito (gráfico) NENHUMA interação de toque nos pontos (sem tooltip, sem seleção).
 - Um botão "Simular: carro parado" (secundário, 58 px, na barra de ações, depois dos outros) alterna o estado e deixa tudo como no 03 (os botões voltam), só para demonstrar; escreva ao lado em 15 px `--muted`: "Só nesta prévia." 
 - Uma nota fixa de 15 px sob a barra de ações: "Como o app sabe que está andando: velocidade da multimídia (Car Info), se existir; senão giros e pressão variando. Sem sinal: tratado como parado." (é a regra proposta, marcada "PRECISA OK" em âmbar).
ESTADO: `parado` (bool, padrão false).
