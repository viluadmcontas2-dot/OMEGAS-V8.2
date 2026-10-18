OMEGAS — Guia do app

  Ω
  
# OMEGAS — Guia do app para o dono do carro

  
Como usar cada tela e cada botão, em linguagem simples, sem precisar saber o que é “central”, “injeção” ou “rotação”.

  
Inclui o passo a passo para o problema **“o carro apaga ao parar, ao pisar na embreagem e ao sair do lugar”**.

  
Versão do app: a que está sendo gerada em 18/10/2026. Baseado no que existe nos arquivos do app, nas gravações do seu carro e no manual oficial do fabricante (Landi Renzo, software OMEGAS 2.16.4 C, 2012).

  
Cada afirmação traz uma marca: [FABRICANTE] = está escrito no manual oficial · [CÓDIGO] = visto no app ou no programa original · [ESTIMATIVA] = conta ou dedução nossa, ainda não provada no carro.

## Sumário

  - Antes de começar: as 6 palavras que você precisa conhecer
  - O que está sempre na tela: barra do topo e menu lateral
  - Tela AutoCal (a central aprendendo sozinha)
  - Tela Refino (o app medindo e sugerindo)
  - Tela Ajuste local (Mapa K)
  - Tela Ajuste global (Curva K)
  - Tela Sessões (gravações da viagem)
  - Ferramentas, página 1: Motor e combustível
    Anti-apagão (interruptor)- Valores do anti-apagão (os 5 números)- Saída do corte · Marcha lenta · Troca de gasolina para gás · Injetores · Volta para gasolina
  - Ferramentas, página 2: App e arquivos
  - Passo a passo: o carro apaga ao parar e ao sair do lugar
  - O que ficou sem explicação (lista honesta)

**Regra de ouro do app:** nada é gravado na central sozinho. Todo botão que muda algo mostra “antes → depois”, pede um segundo toque, grava, lê de volta para conferir e oferece **Desfazer**. Se a conferência não bater, o app diz “não foi gravado”. [CÓDIGO]

## 1. Antes de começar: as 6 palavras que você precisa conhecer

| Central (ECU do GNV) | É o “cérebro” do kit de gás, uma caixinha com um computador dentro. Ela decide quanto gás abrir a cada instante. O app conversa com ela pelo cabo USB. Pense nela como um cozinheiro que segue uma receita: o app lê a receita e, só quando você manda, troca um ingrediente. |
|---|---|
| Injeção (ms) | Cada vez que o motor precisa de combustível, um bico abre por alguns milésimos de segundo (ms). Em marcha lenta é ~2 ms; pisando fundo passa de 10 ms. É como o tempo que você segura o gatilho de uma pistola de água: mais tempo, mais combustível. |
| Rotação (rpm) | Quantas voltas o motor dá por minuto. Parado, com o motor ligado, fica em torno de 850–950. Abaixo de ~400 o motor apaga. É o ponteiro do conta-giros. |
| MAP (bar) | Quanto o motor está “puxando ar”. Pé fora do acelerador ≈ 0,2–0,4 bar; pé no fundo ≈ 0,9–1,0 bar. É um medidor de esforço do motor. |
| Corte | Quando você solta o acelerador com o carro andando engatado, a central da gasolina para de injetar (“corte”). Ao voltar a injetar (na parada ou ao acelerar), o motor pode engasgar. É o momento mais perigoso para o apagão. [CÓDIGO] |
| Curva K e Mapa K | São os dois “ajustes de tempero” do gás. A Curva K é um ajuste geral por tempo de injeção (30 pontos). O Mapa K é um ajuste local por tempo de injeção × rotação (12 × 12 células). O AutoCal (aprendizado) mexe na Curva K; você pode mexer nos dois. |

### Como ler as marcas deste guia

- [FABRICANTE] O manual oficial Landi Renzo diz isso, com a página citada. Vale como “recomendação do fabricante”. Limite: o manual é de 2012, para uma versão anterior do programa; o que ele não cita, não quer dizer que não exista.
- [CÓDIGO] Vimos no código do app ou no programa original do fabricante (ProgBase). Ex.: o valor de fábrica de cada número e a faixa aceita.
- [ESTIMATIVA] Conta ou dedução nossa (ex.: “1.500 ms = 1,5 segundo em qualquer rotação” é conta; “este número é a espera antes de voltar ao gás” é dedução pela ordem dos campos na tela original). Trate como hipótese a testar no carro, uma por vez.

### O que sabemos do seu carro (gravações até 16/10/2026)

- Em 89 gravações (177 mil leituras), o app contou **31 apagões ou quase-apagões**. 14 deles foram “rotação caindo com o motor sob carga”, ou seja, **soltando a embreagem para sair do lugar**, todos no GNV. Outros 13 foram “marcha lenta que morreu” (o motor parado estável e de repente zera), 8 deles no GNV. [CÓDIGO] (script _apagoes_parada_saida.py_)
- Risco por situação, no GNV: soltar a embreagem sob carga apagou em **7,2%** das vezes (11 de 153) e quase apagou em 95; voltar à marcha lenta apagou em 2,3% (6 de 256); sair do lugar com o motor já estável, 1,3%. Na gasolina, nas mesmas situações: 0%, 0% e 1,7%. **O problema é do GNV, e o pior momento é a embreagem saindo.**
- Em 1.011 retomadas depois do corte: quando o motor voltou a injetar abaixo de 1.550 rpm, quase apagou em 10% das vezes; acima de 1.700 rpm, em 0,6%. **Retomar mais alto é 16 vezes mais seguro.** [CÓDIGO]
- Na leitura da sua central de 12/08/2026, o **anti-apagão estava DESLIGADO** e os 5 números dele estavam de fábrica. O app relê isso toda vez que conecta. [CÓDIGO]

## 2. O que está sempre na tela

### Barra do topo (leituras ao vivo)

Fica em todas as abas. Mostra o que a central está vendo agora. Quando aparece “—” é porque a leitura não chegou ou está velha (mais de 2,5 s): o app nunca mostra zero no lugar de “não sei”. [CÓDIGO]

| Campo | O que é | Valores normais | Sinal de problema |
|---|---|---|---|
| Tensão (V) | Voltagem elétrica que chega na central. Só aparece no GNV. | 13,5 a 14,0 V com o motor ligado | Acima de 14,1 V o campo fica em alerta: o GNV pode desligar e voltar para gasolina (relato de campo seu). A escala ainda é provisória. [ESTIMATIVA] |
| MAP (bar) | Esforço do motor (ver cap. 1). | 0,2–0,4 parado; até 1,0 acelerando | — |
| Inj. gasolina (ms) | Tempo de injeção da gasolina que a central do carro pediria. É a “régua” que o kit de gás usa. | ~1,5–2,5 parado; 10+ pisando fundo | Zero com o carro andando = corte (normal ao soltar o pé). |
| Rotação | Voltas do motor por minuto. | 850–950 parado | Abaixo de ~600 parado = quase apagando. |

### Menu lateral (as 6 abas)

| Aba | Para que serve | Grava na central? |
|---|---|---|
| AutoCal | Acompanhar a central aprendendo sozinha e mandar ela reaprender. | Só com botão: reaprender, pausar/retomar, AutoMatch manual. |
| Refino | O app mede por conta própria e propõe correções na Curva K e no Mapa K. | Só depois de “Ver alterações” → “Gravar”. |
| Ajuste local | Editar o Mapa K célula por célula (tempo × rotação). | Só com “Gravar na ECU”. |
| Ajuste global | Editar a Curva K ponto por ponto; backups; suavizar. | Só com “Gravar na ECU” / “Restaurar backup”. |
| Sessões | Gravações da viagem (os dados que provam se melhorou). | Nunca. |
| Ferramentas | Página 1: funções do motor (anti-apagão etc.). Página 2: cópias, aprendizado, bateria, balão, registro. | Só o anti-apagão (interruptor e 5 números), sempre com confirmação. |

Embaixo do menu aparece **“Sem central”** ou o nome da central e o combustível em uso (gasolina / GNV).

## 3. Tela AutoCal (a central aprendendo sozinha)

**O que é.** A central do GNV tem um modo de aprendizado: ela compara como o motor se comporta na gasolina e no GNV e ajusta a Curva K sozinha. É como um aluno fazendo a lição: esta tela mostra o caderno dele em tempo real. O desenho é o mesmo do programa original do fabricante. [CÓDIGO]

**O que o fabricante recomenda para o aprendizado** (manual p. 44–45): motor quente (80–90 °C); segurar 3.000 rpm em ponto morto com o pé firme; depois soltar o pé e deixar em marcha lenta **sem ar-condicionado, sem faróis e sem mexer no volante**. O programa troca de combustível sozinho; a chave do comutador não importa. [FABRICANTE]

### O que você vê

| Elemento | Significado |
|---|---|
| Bolinhas “Gasolina” e “GNV” (4 cada) | As 4 zonas de esforço do motor (do pé fora ao pé no fundo). Verde = zona completa; azul = coletando; laranja = falta. Cinza = sem leitura. |
| Bolinhas “Ajustes” | Quantos ajustes automáticos (AutoMatch) a central já fez dos que ela pretende fazer (normalmente 3). Cheia = feito. |
| Chip ao lado do título | Combustível em uso agora. |
| Gráfico grande | Eixo de baixo = tempo de injeção da gasolina (ms); eixo de cima = MAP. Bolinhas = pontos já medidos; linhas = a curva que a central montou (gasolina e GNV); ◆ AGORA = onde o motor está neste instante; faixa colorida = faixa de MAP atual; fundo verde/vermelho = zona adquirida ou não; “#3/5” = 3 de 5 pontos da zona. Linhas finas = limites de MAP que a central usa. |
| Gráfico pequeno (Curva K) | Os 30 pontos da Curva K que a central está usando. Linha em 1,8 = teto de gravação do app. Ponto acima do teto fica marcado. |
| “Toque numa bolinha” | Tocar num ponto medido mostra seus números e deixa pedir “Reaprender só este ponto”. |
| “Contexto da aquisição” | Texto do estado (“Gasolina 2/4 zonas · GNV 1/4”), sessão atual, evidência do último ajuste da central e, em “Detalhes técnicos”, as 18 regiões. É só leitura. |

### Os botões (rodapé)

| Botão | O que acontece ao tocar | Quando usar / não usar |
|---|---|---|
| **Pausar aprendizado** / **Retomar aprendizado** | Liga ou desliga o modo de aprendizado da central. O app confere na central e mostra “Conferindo na ECU…”. [CÓDIGO] | Pausar só se você quer testar um ajuste seu sem a central mexer por cima. Com o aprendizado pausado a central para de corrigir: lembre de retomar. Qualquer “reaprender” religa o aprendizado sozinho. [CÓDIGO] |
| **Reaprender GNV (apaga o atual)** | A central joga fora o que aprendeu do GNV e começa de novo. Faz backup antes e lê de volta. | Depois de uma mudança grande (Curva K, Mapa K, anti-apagão) para a central medir de novo com a configuração nova. Apaga mesmo: o GNV volta a ser “cru” até reaprender. |
| **Reaprender gasolina (apaga o atual)** | Idem, para a referência da gasolina. | Raro. Só se a referência da gasolina ficou errada (ex.: mexeram no motor). |
| **AutoMatch manual (aplica as médias)** | Só aparece depois que a central fez os ajustes automáticos dela. Pede à central aplicar de novo as médias medidas sobre a Curva K. Um toque, conferido. | Quando os quadradinhos (“faixas prontas”) encheram mais depois dos ajustes automáticos. Não usar com o motor frio ou com poucas faixas prontas. |
| Ações avançadas › **Curva K toda em 1,0** | Põe os 30 pontos da curva em neutro (1,0). Guarda uma cópia antes. | Perigoso se a curva estava fazendo uma correção grande (ex.: 1,5): o motor muda de uma vez, a tensão sobe e acima de 14,1 V o GNV pode desligar. Sua solução de campo: passar a correção grande para o Mapa K (~220) e deixar a curva em 1,0. O app avisa só quando a curva está longe de 1,0. [CÓDIGO] |
| Ações avançadas › **Recomeçar tudo** | Apaga TUDO que a central aprendeu (gasolina, GNV e Curva K = 1,0). | Último recurso. Depois precisa refazer o aprendizado inteiro, como o fabricante descreve (p. 44–45). |
| **Ver sessões** | Abre a lista de gravações desta tela (exportar). | — |

**Não fazer:** não tirar o cabo nem desligar o carro com “Conferindo na ECU…” na tela; não apertar “Recomeçar tudo” para resolver apagão (ele não resolve: só apaga o aprendizado). Nenhum destes botões pausa o aprendizado por si só.

## 4. Tela Refino (o app medindo e sugerindo)

**O que é.** O “nosso AutoCal”: o app mede, por conta própria, quanto gás está sendo usado em relação à gasolina em cada faixa, marca onde o carro engasgou e propõe correções. Mesmo gráfico do AutoCal, com nossas marcas por cima: quadradinhos = medido por nós; ✕ = apagou; ◆ = quase apagou. [CÓDIGO]

**Frase de estado** (chip ao lado do título): “ECU no automático” (a central ainda está aprendendo: o Refino espera), “Nossos pontos” (coletando), “Curva pronta” (tem proposta), “Verificando” (gravou e está medindo se melhorou), “Estável”, “Pontos pioraram”. Embaixo, uma linha diz o próximo passo e, se houver, **“O carro engasgou N vezes no GNV. Em X o motor apagou. Quase sempre ao soltar e voltar do acelerador, perto de 1,8 ms.”** Essa linha é o seu termômetro do problema.

| Botão | O que acontece | Observações |
|---|---|---|
| **Ver alterações** | Abre o cartão “O que vai mudar · Nada foi enviado à ECU ainda”. Pode ter duas partes: Curva K (“Corrige 6 pontos entre 1,5 e 3,0 ms; ajuste médio +4,2%”) e Mapa K (“Reforço do GNV em 4 células (+6%), perto de 1,5–2,2 ms”). Cada parte tem o seu **Gravar**. | Só aparece quando há evidência. Se a central ainda está no automático, a porta de gravação do Mapa K fica fechada (a central aplicaria a correção por cima). Antes de gravar, o app relê a curva: se mudou, a lista é refeita e avisa. |
| **Refinar por minha conta** / **Esperar a ECU terminar** | Você assume: as gravações liberam mesmo com a central no automático. Depois de gravar, mande a central reler o GNV (AutoCal › Reaprender GNV). | Use quando a central demora a terminar e você quer testar. Nunca grava sozinho. |
| **Desfazer última gravação** | Volta os pontos gravados pelo Refino ao valor anterior (também conferido na central). | Só enquanto a última gravação ainda está sendo medida. |
| **Entendi** / **Pronto** / **Tentar de novo** | Fecha o aviso de resultado. | — |

**Reforço do engasgo** (o que o cartão propõe para o Mapa K): quando o app registrou engasgos, ele localiza a região do mapa onde eles acontecem (tempo de injeção baixo × rotação baixa) e propõe mais gás ali, em poucas células. É a correção mais direta para “apaga na parada”. [ESTIMATIVA] (a proposta é do app; o efeito no carro é o que você vai medir nas sessões).

**Refino automático** (interruptor em Ferramentas › página 2): “Ligado” = depois de cada ajuste automático da central o app registra uma sugestão na sessão; “Desligado” = não sugere. Em nenhum dos dois ele grava. [CÓDIGO]

**Não fazer:** não gravar Curva K e Mapa K para a mesma região no mesmo dia (não dá para saber qual ajudou). Não desligar o carro com “Gravando e conferindo…” na tela.

## 5. Tela Ajuste local (Mapa K)

**O que é.** Uma tabela de 12 linhas (tempo de injeção da gasolina, ms) por 12 colunas (rotação). Cada célula é um número de **100 a 255** que diz “quanto gás a mais” naquela situação: 100 é o mínimo (sem reforço); 255 é o máximo. É como um mapa de tempero: você pode pôr mais sal só num prato, sem mudar os outros. A linha técnica do mapa não é editável. [CÓDIGO]

Em cima aparece **AGORA**: a célula em que o motor está neste instante. Assim você vê, parado em marcha lenta, exatamente qual célula é a da parada.

| Controle | O que faz | Dicas e limites |
|---|---|---|
| Tocar numa célula | Seleciona/desmarca. Tocar no título da linha ou da coluna seleciona a linha/coluna toda. | “N selecionadas” mostra a contagem. |
| **Selecionar tudo** / **Limpar** | Marca ou desmarca as 144 células. | Evite “tudo”: correção geral é papel da Curva K. |
| Como ajustar: Percentual | Soma X% ao valor atual das células marcadas (ex.: +5% em 200 → 210). | Para apagão na parada, comece com +3 a +6% só nas células da lenta. [ESTIMATIVA] |
| Como ajustar: Somar valor | Soma X unidades (ex.: +10 em 200 → 210). | — |
| Como ajustar: Definir valor | Põe exatamente X em todas as marcadas. | Só entre 100 e 255. |
| Valor e botões **−5** **−1** **+1** **+5** | O número do ajuste; os botões mudam de 1 em 1 ou 5 em 5. | A célula mostra “atual→novo” enquanto você ajusta. |
| **Curva K → Mapa K** | Lê a Curva K e propõe passar a correção dela para o mapa, linha por linha, deixando a curva neutra (1,0) nessas linhas. Grava em 2 etapas (mapa, depois curva), cada uma conferida. | É a sua solução de campo “correção grande vai para o mapa”. Se a 2ª etapa falhar, aparece **Concluir curva** para terminar; até lá a correção pode estar em dobro. |
| **Equivalência % por RPM** e **Preparar sugestão** | Mostra uma tabela só de leitura (diferença GNV × gasolina por faixa e rotação) e, quando o app tem dados, prepara uma sugestão local no mapa. | Se diz “Ainda faltam dados confirmados”, rode mais com a gravação ligada. |
| **Gravar na ECU** | Grava as células marcadas, espera a resposta da central e lê de volta. Mostra “N alterações confirmadas pela ECU” ou “A ECU não confirmou toda a operação”. | Um toque. Se não confirmou, o mapa é relido: nada é dado como gravado. |
| **Voltar ao aprendizado** | Volta para a tela AutoCal. | — |

### Valor → efeito (uma célula de marcha lenta)

| Valor | O que acontece na prática | Marca |
|---|---|---|
| 100 | Sem reforço naquela célula: o gás segue só a Curva K. | [CÓDIGO] |
| +5% a +10% sobre o atual | Um pouco mais de gás naquela situação: tende a segurar o motor na parada se ele estava pobre. É o tamanho de passo que o Refino propõe. | [ESTIMATIVA] |
| ~220 em várias células | Correção grande: foi o que você usou no lugar de uma Curva K em 1,5. | [CÓDIGO] (relato seu) |
| 255 | Máximo: se precisa disso, o problema é outro (bico pequeno, pressão, vazamento). O fabricante só manda “multiplicar o mapa” quando as medianas passam de 230–240 e a mistura segue pobre, e aí manda refazer o aprendizado (p. 25). | [FABRICANTE] |

**Não fazer:** não mexer em dezenas de células de uma vez; não gravar durante um “Reaprender GNV”; não corrigir no mapa o que já corrigiu na curva (dobra).

## 6. Tela Ajuste global (Curva K)

**O que é.** Uma linha com 30 pontos, um para cada tempo de injeção. Cada ponto é um **fator**: 1,00 = neutro; 1,10 = 10% mais gás; 0,90 = 10% menos. É o volume geral do rádio: mexer aqui muda todas as rotações daquele tempo de injeção de uma vez. O app só grava entre **0,6 e 1,8**. [CÓDIGO]

| Controle | O que faz | Dicas e limites |
|---|---|---|
| Tocar num dos 30 pontos / arrastar um ponto | Mostra “Atual” e deixa digitar o Fator desejado (ex.: 1,02). Arrastar só prepara a prévia. | Nada muda na central até “Gravar”. |
| **−0,05** **−0,01** **+0,01** **+0,05** | Ajuste fino do fator. | Passos de 0,01 a 0,03 são o normal. 0,05 já é bastante. |
| **Preparar este ponto** | Põe o ponto na lista “Pontos preparados” (a proposta). | Pode preparar vários antes de gravar. **Limpar** apaga a lista. |
| **Gravar na ECU** | Grava os pontos preparados, confere na central e mostra “Curva K confirmada pela ECU”. | Uma confirmação só, sem “tem certeza?”. |
| **Reler ECU** | Lê de novo os 30 pontos. | Use antes de editar, se a tela diz “ECU sem resposta”. |
| **Suavizar** | Prepara uma curva sem “dentes” (pontos vizinhos muito diferentes). Não grava. | Confira os pontos preparados; grave só se concordar. |
| **Guardar backup** / lista de backups / **Restaurar backup** | Salva uma cópia da curva atual; restaurar mostra o antes→depois e grava conferido. | Guarde backup antes de qualquer mudança. Se “Já está igual”, nada a fazer. |
| **Curva K toda em 1,0** | Faz cópia e põe tudo em neutro. | Mesmo aviso da tela AutoCal: perigoso se a curva estava longe de 1,0. |

### Valor → efeito (um ponto baixo, ~2 ms, região da parada)

| Fator | Efeito | Marca |
|---|---|---|
| 0,90 | 10% menos gás em todas as rotações com esse tempo de injeção: motor mais pobre na parada, mais risco de apagar. | [ESTIMATIVA] |
| 1,00 | Neutro: a central usa só o que aprendeu + o Mapa K. | [CÓDIGO] |
| 1,05–1,15 | Um pouco mais de gás. Nas suas gravações, a faixa de 1–3 ms fica ~10% “pobre” em todas as rotações: por isso os pontos baixos costumam ficar acima de 1,0. | [CÓDIGO] (medição nas sessões) |
| 1,30 ou mais | Correção grande. Na sessão de 16/10 o carro apagou com os pontos baixos em 1,30, em 1,03 e em 1,20: o valor da curva, sozinho, não resolveu o apagão. Correção grande é melhor no Mapa K. | [CÓDIGO] |
| 1,80 | Teto do app. Acima disso o app não grava. | [CÓDIGO] |

**Não fazer:** não editar a curva enquanto a central está no automático e você espera o aprendizado dela (ela vai mexer por cima); não pular o backup.

## 7. Tela Sessões (gravações da viagem)

**O que é.** A caixa-preta do carro: o app grava rotação, MAP, injeção, combustível e tudo que foi gravado na central. É com essas gravações que se prova se um ajuste melhorou. Começa sozinha quando a central conecta. [CÓDIGO]

| Cartão / controle | O que faz | Valores |
|---|---|---|
| Gravação · **Começar a gravar** / **Parar de gravar** | Liga ou para a gravação manualmente. Mostra “Gravando · 12m 30s · 3,2 MB”. | Deixe ligada sempre que for testar um ajuste. |
| Gravações salvas · **Salvar arquivo** | Lista (3 por página) com data, duração e tamanho. “Salvar arquivo” exporta para compartilhar (ex.: mandar para quem analisa). | “‹ Mais novas” / “Mais antigas ›” para paginar. |
| Tamanho das gravações › Mudar | Abre as opções abaixo. **Aplicar** salva. |  |
| Salvar a cada | De quanto em quanto tempo uma leitura entra no arquivo. | 0,25 s (padrão, melhor para ver apagão) a 5 s (arquivo pequeno, mas perde o momento do engasgo). |
| Tamanho máximo (MB) | Quando a gravação chega nesse tamanho, ela fecha e outra começa. | 64 a 1.024; padrão 256. |
| Quantas guardar | Quantas gravações ficam no celular antes de apagar as mais antigas. | 20 a 100; padrão 20. |
| Gravar ao conectar a central | Liga a gravação sozinha ao conectar o cabo. | Deixe marcado. |
| Gravar tudo do cabo (arquivo grande) | Guarda também cada byte que passou no cabo. | Só se alguém pedir para investigar o protocolo. |

### Como ver, numa sessão, se o apagão melhorou

- Antes de mudar qualquer coisa, dirija um trajeto com pelo menos **20 paradas e saídas** (semáforos) no GNV, com a gravação ligada. Anote o que a tela Refino diz: “O carro engasgou N vezes no GNV. Em X o motor apagou.”
- Faça **um** ajuste (cap. 10). Repita o mesmo trajeto, ou outro com ~20 paradas.
- Compare a frase do Refino (engasgos e apagões por 20 paradas) e, se tiver quem analise, o arquivo exportado: a conta de “apagou / quase apagou por situação” é feita pelo script _tools/sessoes/apagoes_parada_saida.py_, que dá a taxa para “retorno à lenta”, “saída do lugar” e “embreagem sob carga”. [CÓDIGO]
- Melhorou = menos engasgos nas mesmas situações E rotação mínima nas paradas subiu (de ~450 para 700+). Piorou ou igual = desfaça e tente o próximo item.

## 8. Ferramentas, página 1: Motor e combustível

Um cartão por assunto. Interruptor = liga/desliga; campo com lápis = número que você digita; “Ver ›” = só mostra, o app não muda. Todos os cartões mostram “—” até a central responder (“Esperando a leitura da central”) e dizem “Conecte a central para mudar” sem cabo. [CÓDIGO]

### 8.1 Anti-apagão (interruptor)

**O que é.** Uma função da própria central (no programa original chama-se “Abilitazione anti-stallo” / “Enable un-stalling strategy”). Quando o motor está em rotação baixa e caindo rápido, a central tira o motor do GNV por um instante (usa gasolina) para ele não apagar, e volta ao gás depois de uma espera. É como um instrutor que segura o volante só quando o carro está quase saindo da pista. [CÓDIGO] (rótulos da tela original) · [ESTIMATIVA] (o efeito físico ainda não foi medido no seu carro; o manual público de 2012 não cita esta função).

**O que acontece ao tocar.** 1º toque: “Lendo a central…”, depois a janela “Ligar anti-apagão?” com “Desligado → Ligado · O resto continua igual”. 2º toque em **Ligar anti-apagão**: “Gravando e conferindo…” e, se a leitura de volta bater, “Pronto. Anti-apagão ligado e conferido na central”, com o botão **Desfazer: voltar a Desligado**. **Agora não** ou um toque fora da janela cancela. Trocar de aba ou tirar o cabo também cancela. [CÓDIGO]

**Na sua central** (12/08/2026): **Desligado**. Das 8 vezes que o carro apagou no GNV nas gravações, em 5 a regra “deveria ter agido” se estivesse ligada; dos 14 apagões logo depois de sair do GNV, em 13. Isso é compatível com “estava desligada”, mas **não prova que ligar resolve**: só o teste no carro prova. [CÓDIGO]

**Avisos:** “A central não aceitou” = nada mudou, tente de novo. “Não sei como ficou na central” = o cabo soltou ou a leitura de volta não veio: reconecte e olhe o estado do cartão antes de tentar de novo. O app grava a “família” inteira de interruptores onde esse bit mora, lendo antes e mudando só este; os outros continuam iguais.

### 8.2 Valores do anti-apagão (os 5 números)

Cartão “Quando ele age e quando sai”. Estado: “De fábrica” ou “Mudados”. Toque em **Mudar valores**: digite, depois **Gravar valores** (mesmo fluxo: antes → depois, 2º toque, conferência, Desfazer). **Restaurar fábrica** volta os 5 de uma vez. Valores fora da faixa são recusados antes de gravar; valores a mais de 50% do de fábrica mostram “Longe do valor de fábrica. Confira antes de gravar.” [CÓDIGO]

**Honestidade sobre a ordem:** os 5 números chegam da central como uma lista. Qual número é qual foi deduzido pela numeração dos campos na tela do programa original (campo 0 = primeiro, …, campo 4 = último). É a dedução mais forte possível sem um teste no carro, mas ainda é dedução. A própria tela avisa “a ordem dos campos é inferida”. [ESTIMATIVA]

#### Número 1 — Rotação máxima para agir (rpm) · fábrica 1.600 · faixa 0 a 6.000

Só abaixo desta rotação o anti-apagão pode entrar. É a “altura da cerca”: acima dela a central não se mete. Original: “Max giri attivazione anti-stallo”. [CÓDIGO]

| Valor | Efeito esperado |
|---|---|
| 800–1.000 (baixo) | Só age quando o motor já está quase na marcha lenta: pode chegar tarde, porque na embreagem saindo a rotação cai de 1.000 para 700 em poucos décimos. |
| 1.600 (fábrica) | Cobre a volta à lenta e a saída do lugar. Suas gravações: retomadas abaixo de 1.550 rpm é onde o risco é 16× maior; 1.600 pega essa zona. |
| 1.800–2.200 (alto) | Age mais cedo, também em desacelerações normais na estrada: mais trocas para gasolina, mais gasolina gasta, possíveis trancos. Primeiro passo razoável se 1.600 não bastar: 1.800. [ESTIMATIVA] |
| Acima de 3.000 | Sem sentido: a central ficaria trocando combustível em rodagem normal. |

#### Número 2 — Queda de rotação para agir (rpm por ciclo) · fábrica 100 · faixa 0 a 1.000

Quanto a rotação precisa cair, de uma leitura da central para a seguinte (“por ciclo”), para a regra disparar. É a sensibilidade do gatilho. Original: “Delta giri attivazione”. [CÓDIGO]

| Valor | Efeito esperado |
|---|---|
| 20–50 (baixo = sensível) | Dispara com queda pequena: age em quase toda desaceleração abaixo de 1.600. Mais proteção, mais gasolina e mais trocas. |
| 100 (fábrica) | Dispara em quedas rápidas. Nas suas gravações de apagão, as quedas entre leituras foram de 200 a 800 rpm: 100 pegaria todas as rápidas; não pegaria as “lenta que morreu devagar” (quedas de 10–35). |
| 300+ (alto = pouco sensível) | Só dispara em quedas violentas; pode não agir a tempo. |

“Ciclo” = o intervalo entre duas leituras internas da central; não é documentado. [ESTIMATIVA]

#### Número 3 — Tempo antes de voltar ao gás (ms) · fábrica 1.500 · faixa 0 a 10.000

Depois que a regra agiu, quanto tempo a central espera antes de devolver o motor ao GNV. O app mostra o valor também em segundos (1.500 ms = 1,5 s). Original: “Attesa uscita gas”, unidade ms na tela. [CÓDIGO]

```
Conta: 1 segundo = 1.000 ms. 1.500 ms ÷ 1.000 = 1,5 s. 3.000 ms = 3 s. Não depende da rotação.
```

| Valor | Efeito esperado |
|---|---|
| 0–500 (curto) | Volta ao gás quase na hora: o motor pode engasgar de novo logo em seguida (a rotação ainda não se firmou). |
| 1.500 (fábrica = 1,5 s) | Tempo de a marcha lenta se estabilizar. Nas suas gravações, do início da queda até o motor morrer passam cerca de 1 a 2 s. [ESTIMATIVA] |
| 2.500–4.000 (longo) | Mais segurança na parada; gasta mais gasolina em cada parada e, se o anti-apagão disparar muito, o carro passa boa parte da cidade na gasolina. 2.500 é um segundo passo razoável. |
| Perto de 10.000 | 10 s na gasolina a cada disparo: só para teste. |

#### Número 4 — Folga para não ficar trocando (rpm por ciclo) · fábrica 2 · faixa 0 a 1.000

Uma “zona morta” em volta do gatilho (número 2) para a regra não ligar e desligar a cada pequena oscilação. Original: “Isteresi su soglia attivazione”. É como o termostato da geladeira, que não liga e desliga a cada 0,1 grau. [CÓDIGO]

| Valor | Efeito esperado |
|---|---|
| 0 | Sem folga: pode oscilar (liga/desliga seguidos). |
| 2 (fábrica) | Folga mínima. Dificilmente precisa mexer. |
| 50+ | Folga grande demais: a regra pode demorar a soltar ou a entrar. Não recomendado sem motivo. [ESTIMATIVA] |

#### Número 5 — Queda para parar de agir (rpm por ciclo) · fábrica 20 · faixa 0 a 1.000

Quando a queda de rotação por ciclo fica menor que isso (o motor “se firmou”), a regra entende que o perigo passou e começa a contar o tempo para voltar ao gás. Original: “Delta giri disattivazione”. [CÓDIGO]

| Valor | Efeito esperado |
|---|---|
| 0–5 (muito exigente) | Só solta quando a rotação está totalmente parada: fica mais tempo na gasolina. |
| 20 (fábrica) | Solta assim que a queda vira pequena. Equilíbrio normal. |
| 100+ (pouco exigente) | Solta cedo, ainda com a rotação caindo: pode voltar ao gás e apagar. Evite subir. |

**Faixa segura (resumo):** fábrica 1.600 · 100 · 1.500 · 2 · 20. Mexa um número por vez, de preferência nesta ordem: Rotação máxima (1.600 → 1.800), depois Tempo (1.500 → 2.500), depois Queda para agir (100 → 60). Volte ao de fábrica se não melhorou. Sinais de que está errado: carro passando a maior parte do tempo na gasolina na cidade (número 1 ou 3 altos; número 2 baixo), trancos ao desacelerar (número 2 muito baixo), apagando igual (a regra não pegou: número 1 baixo ou número 2 alto, ou o problema não é este). [ESTIMATIVA]

## 8 (cont.). Os cartões só de leitura (“Ver ›”)

Estes cartões mostram como a central está configurada. O app **não muda** nada neles (seria preciso o programa original do instalador). Servem para você e o instalador saberem o que está ligado. [CÓDIGO]

### 8.3 Saída do corte (quando solta o acelerador)

| Linha | O que é | Na sua central (12/08) |
|---|---|---|
| Saída suave do corte | “Progressões”: ao voltar do corte, a central passa de gasolina para gás aos poucos, em vez de de uma vez. Original: “Abilita progressioni”. | Desligado |
| Gasolina ao sair do corte (injeções) | Quantas injeções de gasolina a central dá ao voltar do corte antes de entregar ao gás. É o “empurrão” de gasolina na retomada. O nome vem do parâmetro; o papel é deduzido. [ESTIMATIVA] | 0 (nenhuma) |
| Corte das injeções extras | Ignora as injeções extras curtas da gasolina ao copiá-las para o gás. O manual diz: só para centrais 194–198, usada quando há injeções extras frequentes (p. 28). | Ligado [FABRICANTE] |

**Por que importa:** “Saída suave” e “Gasolina ao sair do corte” são as duas funções da central feitas exatamente para a retomada depois do corte, que é o seu caso. As duas estão desligadas/zero. Pedir ao instalador para ligar uma delas (uma por vez) é um passo legítimo. [CÓDIGO]

#### O que significa “N injeções” em segundos (a conta)

```
Motor parado a ~900 rpm = 15 voltas por segundo.
Cada cilindro injeta 1 vez a cada 2 voltas = 7,5 injeções por segundo por cilindro.
Motor de 4 cilindros = 30 injeções por segundo no total.

Se a central conta no total:     20 injeções ≈ 20 ÷ 30 ≈ 0,7 s
Se a central conta por cilindro: 20 injeções ≈ 20 ÷ 7,5 ≈ 2,7 s
Nossa referência: "20 injeções ≈ 1 segundo, mais ou menos".
Em 6 cilindros, 45 injeções/s no total: 20 injeções ≈ 0,45 s.
Acelerando a 2.000 rpm tudo é 2,2× mais rápido.
```

| Valor | Efeito esperado |
|---|---|
| 0 (como está) | Nenhuma gasolina na retomada: o motor volta direto no gás. |
| 5 | Quase nada: segura menos de meio segundo. |
| 20 | Padrão do fabricante para a função parecida “Volta à gasolina” na marcha lenta (p. 31): ~1 s de gasolina e volta ao gás sozinho. [FABRICANTE] |
| 40–50 (máximo 50 na tela original) | ~2 s ou mais: gasta mais gasolina e pode demorar a voltar ao gás; com muitas paradas, o carro roda bastante na gasolina. |

O fabricante não diz se conta por cilindro ou no total, nem o número de cilindros do seu motor está nos arquivos: a conta acima supõe 4 cilindros. [ESTIMATIVA]

### 8.4 Marcha lenta (como o motor roda parado)

Mostra o “Modo na marcha lenta” e a “Rotação que vale como marcha lenta”. Os 3 modos existem no programa original e o fabricante os descreve assim (p. 31) [FABRICANTE]:

| Modo | O que faz | Para quem |
|---|---|---|
| Só gás (“Gas”) | Parado, o motor roda só a gás. | Padrão. É o que está na sua central (rotação de lenta = “não usada”). [CÓDIGO] |
| Segurar com gasolina na parada (“Return to petrol”) | Ao cair para a marcha lenta, a central dá 20 injeções de gasolina e depois volta ao gás sozinha. | “Usada quando o motor tende a apagar ao voltar à marcha lenta.” O fabricante diz, na página do gás no corte (p. 28–29): para apagão ao voltar à lenta, prefira esta família de ajuste (Gás–Gasolina). |
| Sempre gasolina na parada (“Petrol”) | Abaixo da rotação de lenta, roda sempre a gasolina, com atraso embutido para não ficar trocando. | Mesmo caso, mais forte. Gasta gasolina em toda parada. |

“Rotação que vale como marcha lenta”: abaixo dela um dos dois modos acima entra; faixa 0–2.000 rpm (p. 31); o programa original grava 1.100 ao escolher um desses modos. [CÓDIGO] O app só mostra e sugere (“Sugestão: Segurar com gasolina na parada”); **não grava** este modo: é mudança para o instalador fazer no programa original.

### 8.5 Troca de gasolina para gás

| Linha | O que é | Na sua central |
|---|---|---|
| Um cilindro por vez | Troca sequencial: passa os cilindros para gás um a um, a cada 5 injeções, para a troca ser macia (p. 31). [FABRICANTE] | Ligado |
| Troca automática | Interruptor da central para trocar sozinha (depende de outras condições). | Desligado |
| Troca pela temperatura da água / do gás | Só passa para gás quando o motor (ou o gás) esquenta até a temperatura configurada. | Água: Ligado · Gás: Desligado |

Fora do app, a sua central troca para gás a partir de 1.200 rpm (o fabricante sugere 1.600 como padrão, p. 12). Trocar numa rotação baixa pode cair na zona de risco; é assunto para o instalador. [CÓDIGO] [FABRICANTE]

### 8.6 Injetores

| Linha | O que é | Na sua central |
|---|---|---|
| Controle dos injetores | A central vigia os bicos de gás (diagnóstico). | Ligado |
| Aquecimento dos injetores | Pré-aquece os bicos no frio. | Desligado |
| Corrente maior na troca para gás | Abre o bico com mais força na troca e na saída do corte. | Desligado |

Não mexer sem o instalador: errar corrente de bico pode queimá-lo. [CÓDIGO]

### 8.7 Volta para gasolina

| Linha | O que é | Na sua central |
|---|---|---|
| Gasolina entra sozinha em rotação alta | Adiciona gasolina em rotação alta (falta de vazão de gás). Não é para a parada. | Desligado |
| Corte da bomba de gasolina | Desliga a bomba de gasolina enquanto roda a gás. | Desligado |
| Reativar a bomba na reserva | Religa a bomba quando o gás acaba. | Desligado |

Dica de diagnóstico do fabricante (p. 19): um “apagão” no GNV pode ser a central voltando para gasolina por pressão baixa (cilindro quase vazio): aviso sonoro rápido e as duas luzes acesas no comutador. [FABRICANTE]

Em qualquer cartão, “Ver detalhes técnicos” mostra os códigos internos. Não precisa deles para usar o app.

## 9. Ferramentas, página 2: App e arquivos

Toque em **Mais ferramentas ›** no rodapé. Nenhum cartão desta página grava na central (exceto o teste de gravação, que escreve e desfaz). [CÓDIGO]

| Cartão | O que faz ao tocar | Quando usar |
|---|---|---|
| Cópia de segurança · **Salvar cópia** | Guarda tudo do app (ajustes, aprendizado, gravações) num arquivo. | Antes de trocar de celular ou de mexer muito. |
| Aprendizado · **Salvar aprendizado** | Guarda só o que o app aprendeu (medições do Refino). | Para levar para outro celular. |
| Aprendizado de outro arquivo · **Carregar aprendizado** | Lê um arquivo de aprendizado e confere antes. Não grava na central. | Idem. |
| Refino automático (interruptor) | Ligado: depois de cada ajuste automático da central, o app anota uma sugestão. Desligado: não anota. Nunca grava sozinho. | Pode deixar ligado. |
| Teste de gravação · **Testar** (2 toques) | Escreve um valor de teste numa faixa do GNV já cheia, confere se a central aceitou e segurou, e restaura o original. Resultado em palavras: “A central aceitou e o valor ficou” / “recusou (só leitura)” / “aceitou, mas desfez sozinha”. | Só com o carro parado. Serve para saber se a central aceita escrita naqueles pontos. Se avisar “o original NÃO voltou”, use AutoCal › Reaprender GNV. |
| Bateria · **Permitir** | Pede ao Android para não pausar o app em segundo plano. | Se a gravação para sozinha quando a tela apaga. |
| Balão sobre outros apps · **Permitir balão** / interruptor | Mostra as leituras (rotação, MAP…) por cima do mapa/GPS. | Para ver a rotação enquanto dirige com o Waze aberto. |
| Registro do app · Ver › | Lista de avisos (filtros “Só erros / Só avisos / Só informações” e por assunto), **Salvar registro** (exporta) e **Testar o app** (autoteste). | Quando algo não funciona e alguém pedir o registro. |

## 10. Passo a passo: o carro apaga ao parar e ao sair do lugar

**Diagnóstico pelas suas gravações:** é um problema do GNV (na gasolina não apaga), concentrado em dois momentos: (a) **soltar a embreagem para sair** (7% de apagão, 62% de quase-apagão) e (b) **a volta à marcha lenta / lenta que morre** (2%). Em quase todos, a rotação estava abaixo de 1.600 e caindo 200–800 rpm entre uma leitura e outra: exatamente a situação para a qual o anti-apagão foi feito, e ele estava desligado. [CÓDIGO]

**Método:** um ajuste por vez · 20 paradas e saídas gravadas antes e depois · anote a frase do Refino (“engasgou N vezes”) · se não melhorou, desfaça antes do próximo. Nunca dois ajustes no mesmo dia.

| Passo | O que fazer no app | O que esperar | Marca |
|---|---|---|---|
| 0. Medir | Sessões: gravação ligada. Dirija 20 paradas/saídas no GNV. Anote o Refino: “engasgou N vezes, apagou X”. | Sua linha de base. | — |
| 1. Ligar o anti-apagão (com os 5 números de fábrica) | Ferramentas › Anti-apagão › toque, confira “Desligado → Ligado”, toque de novo. Confirme “Pronto… conferido na central”. | Nas paradas e saídas, um breve uso de gasolina (1,5 s) quando a rotação despenca. Menos apagões. Se a central “não aceitou”, ela pode não ter a função ou exigir condição (motor desligado): tente com a chave ligada e o motor desligado. | [ESTIMATIVA] |
| 2. Se melhorou pouco: Rotação máxima 1.600 → 1.800 | Valores do anti-apagão › número 1 › Gravar valores. | Age mais cedo na saída do lugar. Se o carro passar a ficar muito na gasolina, volte a 1.600. | [ESTIMATIVA] |
| 3. Se ainda apaga na parada: Tempo 1.500 → 2.500 ms | Número 3 (2,5 s). | Mais tempo de gasolina até a lenta firmar. | [ESTIMATIVA] |
| 4. Se ainda apaga na embreagem: Queda para agir 100 → 60 | Número 2. | Gatilho mais sensível. Se der tranco ao desacelerar, volte a 100. | [ESTIMATIVA] |
| 5. Reforço do GNV na lenta (Mapa K) | Refino › Ver alterações › se houver “Reforço do GNV em N células (+X%)” › Gravar no Mapa K. Ou Ajuste local: marque as células que AGORA aponta com o carro parado e com o pé saindo da embreagem, Percentual +5, Gravar. | Mais gás onde o motor engasga. Suas gravações mostram a faixa de 1–3 ms ~10% pobre. | [CÓDIGO] (medição) · [ESTIMATIVA] (efeito) |
| 6. Reaprender GNV | AutoCal › Reaprender GNV, depois rodar com o motor quente como o fabricante descreve. | A central mede de novo com a configuração nova. | [FABRICANTE] (p. 44–45) |
| 7. Com o instalador (fora do app) | Pedir, um por vez: Marcha lenta = “Segurar com gasolina na parada” (o que o fabricante indica para apagão ao voltar à lenta); “Gasolina ao sair do corte” = 20; “Saída suave do corte” ligada; rotação de troca 1.200 → 1.600. | O app mostra o estado de cada um no cartão correspondente, para conferir que foi feito. | [FABRICANTE] (p. 28–29, 31, 12) |

### Como saber se foi o anti-apagão que agiu

Na barra do topo, no momento da parada, o combustível muda de GNV para gasolina por ~1,5 s e volta. Nas gravações, aparece uma “saída do GNV” sem corte e com rotação baixa caindo: o script de análise classifica como “ANTI_APAGAO_CANDIDATO”. Antes de ligar, 1% das saídas do GNV tinham esse formato; se depois de ligar esse número subir bastante, a regra está agindo. [CÓDIGO]

**Não fazer:** ligar o anti-apagão e mexer na Curva K no mesmo dia; mudar dois dos 5 números de uma vez; gravar qualquer coisa com o cabo mal encaixado; “Recomeçar tudo” como tentativa.

## 11. O que ficou sem explicação (lista honesta)

- **Efeito real do anti-apagão no seu carro:** nunca foi ligado com gravação. A ordem exata dos 5 números é dedução pela tela original; só um teste “muda um número, mede” fecha a dúvida.
- **“Por ciclo”** (números 2, 4 e 5): o intervalo de leitura interno da central não está documentado.
- **Se a central aceita gravar o anti-apagão com o motor ligado:** o programa original tem condições internas (modelo de central); o app confere por leitura de volta e avisa se não ficou.
- **“Gasolina ao sair do corte”:** o papel do número é deduzido pelo nome; se conta por cilindro ou no total, não sabemos; o app só mostra (não grava).
- **“Saída suave do corte” (progressões):** existem 12 números internos (4 situações × 3 colunas) cujo significado é desconhecido; o app não os mostra.
- **Número de cilindros do seu motor:** não está nos arquivos; as contas de “injeções → segundos” supõem 4.
- **Tensão na barra do topo:** a escala (÷16) é provisória; o alerta de 14,1 V vem do seu relato.
- **Mapa K: o que “100” e “255” significam em porcentagem de gás** não está provado; sabe-se só que 100 é o piso, 255 o teto e que ~220 foi a sua correção grande.
- **Modo da marcha lenta:** o app só lê e sugere; a escrita ficou pendente de propósito.
- **AutoCal, detalhes técnicos:** “Estado raw”, “Snapshot”, “18 regiões”: são números internos para quem desenvolve; não têm efeito para o uso.
- **“Equivalência % por RPM” / “Preparar sugestão”** no Ajuste local: só aparecem com dados suficientes; o critério exato de “suficiente” é do código em Kotlin e não foi detalhado aqui.

Fontes usadas: arquivos de tela do app (_components/advanced.js, drawers.js, live-bar.js; screens/autocal-cockpit.js, autocal-original.js, refino.js, map.js, curve.js; core/idle-mode.js, advanced-flags.js, anti-apagao-valores.js, cartoes-motor.js_); evidências de 18/10/2026 (_aba-avancado, anti-stallo, anti-stallo-caixa-re, anti-stallo-ligado-na-ecu, anti-stallo-quadro-de-escrita, apagoes-x-anti-stallo, catalogo-funcoes-originais, manuais-publicos, tela-anti-stallo-original, flags-do-original_) e de 16/10 (_sessao-18-07-apagoes_); _tools/sessoes/apagoes_parada_saida.py_ (rodado em 18/10 sobre o corpus de 89 sessões); manual oficial “Software Manual Landi Renzo Omegas 2.16.4 C” (jul/2012), páginas 12, 17, 19, 25, 27, 28–29, 31, 44–45.
