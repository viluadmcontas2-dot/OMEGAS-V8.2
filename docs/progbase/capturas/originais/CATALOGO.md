# Catálogo das capturas do ProgBase original ("XP - GNV SOFTWARE")

Fonte: pasta do Drive do dono OMEGAS > CAPTURAS ORIGINAIS (id `1dDigTO1-NST-ehGrTwTd3Bborr_v8yCo`), prints de 2026-10-19 (11:45 a 12:02), 23 imagens de 1718 x 878 px, baixadas e conferidas visualmente uma a uma. O nome do arquivo aqui é só o horário (`HH-MM-SS.png`); no Drive é `Copy of  XP - GNV SOFTWARE-2026-10-19-HH-MM-SS.png`.
Programa: **Landi Renzo Omegas** (ProgBase), carro de teste "Config: 208 REFINO AGOSTO", gás **CNG**, motor 3 cilindros 1000 cc, ECU conectada (menos nas 2 últimas). Janela de fundo do Windows XP ("Portmon on \\SILVDOWN...") = o dono estava gravando a serial junto.

Marcas: **OBS-FIO** = lido direto na imagem (ou valor/relacionamento conferido em várias imagens); **INFERIDO** = leitura minha, não demonstrada. Nada aqui prova o que a central faz com o campo; só o que o original MOSTRA.

Duplicatas já existentes no repo (mesmo conteúdo, md5 igual): `F4-nivel-gas-volta-automatica.png` = `11-46-05.png`; `F7-centros-das-celulas-k-bins.png` = `11-50-09.png`; `F9-injetores-anti-stallo-ligado.png` = `11-52-02.png`.

## Barra de status (rodapé), igual em todas as janelas "Vehicle configuration"

OBS-FIO: indicador **GAS** (verde), **Revs** (rpm), **Gas Tinj.** e **Petrol Tinj.** (ms), **Level** (número cru, 125 em todas), **Press.diff.** (bar), **T.water** e **T.gas** (°C), **Press.** (bar), **MAP** (bar), **Lambda** ("n.a." = sonda não conectada). Na F9 aparece **ΔRPM** (1 na F9, 3 na 11-58-22) no lugar de Revs, mostrando a variação de rotação ao vivo.
OBS-FIO (conta nas imagens): **Press.diff. = Press. − MAP** (2,45−0,50=1,95; 2,44−0,44=2,00; 2,42−0,45=1,97; 2,50−0,60=1,90). Gás em marcha lenta: Revs ~860 a 880, Gas Tinj ~9,6 a 11,4 ms, Petrol Tinj ~3,5 a 4,1 ms, T.água 63-65 °C, T.gás 52-53 °C.

## Janela "Vehicle configuration" (abas F1 a F11 à esquerda)

Abas visíveis, em ordem: F1 Gas change-over, F2 Lambda, F3 Emissions, F4 Gas level, F5 Pressure, F6 Temperature, F7 K insertion, F8 Advanced, F9 Injectors, F10 Gas/petrol, F11 Autocalibration, F12 - Esc Exit. **Não há captura de F5 Pressure nem de F10 Gas/petrol.**
INFERIDO: texto em **vermelho** (valores "Weak", "3 cylinders", "Single coil", "0 - 5 Volt", "Not connected", "Non standard", "disabled", rótulo "Use vehicle's engine temperature sensor") parece marcar campo que foge do padrão de fábrica; valores em azul/preto são o padrão.

### 11-45-41 · F1 Gas change-over
OBS-FIO: Fuel type **CNG** (lista); Engine **Aspirated**; Injector type (vazio); Displacement (cc) **1000**; Inj. **Sequential**; "Type of rev signal" marcada, **Weak**; "RPM reading" camshaft e flywheel desmarcados; No. of cylinders **3**; Ignition type **Single coil**; **Type of change-over: In acceleration**; **Revs. threshold for change-over 1200**; **Water temperature for change-over 40 °C**; "Start on gas with hot engine" desmarcada. Há rolagem lateral (mais campos abaixo).
Ensina (INFERIDO): a troca gasolina→gás "em aceleração" só vale acima de 1200 rpm e com água acima de 40 °C. Casa com as caixas F6 (água marcada) e com os bits de troca do app (FLAG_CONF1/3).

### 11-45-51 · F2 Lambda
OBS-FIO: Number of Lambda probes **1**; Type of Lambda probe **0 - 5 Volt**; Oxygen Sensor 1 (purple wire) **Not connected**; "Enable lubricant level diagnosis" desmarcada. Rodapé: Lambda n.a. (coerente com "Not connected").

### 11-45-59 · F3 Emissions
OBS-FIO: **Exit from cutoff leaning 0 %** (faixa mostrada **(-100;100)**, fundo azul = dica de faixa); **Leaning number of injections 0**. Tela quase vazia: só 2 campos.

### 11-46-05 · F4 Gas level (sensor AEB)
OBS-FIO: Type of GAS level sensor **AEB**; "Recognition of full" desmarcada; **Automatic change back to petrol marcada**; **Low pressure time for change back 0 s**; **Low pressure value for change back 1.25 bar**; "Gas electrovalves opened in advance" desmarcada e cinza (desabilitada). Barra de rolagem lateral.
Ensina: a "volta automática à gasolina" é por PRESSÃO baixa (limiar 1,25 bar, atraso 0 s), função diferente da "gasolina em rotação alta". Pressão normal do gás nesta tela: Press. 2,42 / Press.diff. 1,96 (INFERIDO: o limiar de 1,25 bar é comparado à Press.diff., pois está na mesma faixa de unidades; falta provar).

### 11-46-34 · F4 com sensor "Non standard"
OBS-FIO: ao escolher **Non standard** aparece a caixa "Non-standard level references": **Reserve 39, 1/4 93, 2/4 143, 3/4 219** (números crus), botões **Accept**, **Set min level** (valor 0), **Set max level** (valor 0), **Find levels**. Level ao vivo = 125 (entre 1/4 e 2/4 pelos limiares, INFERIDO). O resto igual à 11-46-05.

### 11-46-41 e 11-46-52 · F4, lista de sensores aberta
OBS-FIO: opções da lista "Type of GAS level sensor": **AEB, 0 - 90 ohm, Landi Renzo, Sensata HD, AEB807A, Non standard, Non standard inverted**. (11-46-41 só difere em T.água 65 e 11-46-52 em 64 °C.)

### 11-47-12 · F6 Temperature
OBS-FIO: Gas temperature sensor type **4k7** (lista); "Enable change-over with gas temperature" desmarcada; **"Enable change-over with water temperature" marcada**; "Use vehicle's engine temperature sensor" desmarcada (rótulo vermelho).

### 11-47-54 · F7 K insertion (grade do Mapa K, com os 12 x 12 valores)
OBS-FIO: cabeçalho **inj/rpm**; colunas rpm **850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500**; linhas de tempo (ms) **2,00 2,50 3,00 3,50 4,50 6,00 8,00 10,00 12,00 14,00 16,00 18,00**. Valores (números inteiros crus, iguais em TODAS as 12 linhas, só variam com o rpm): **850=253, 1350=250, 1850=245, 2500=240, 3000=236, 3500 em diante=226**. Botões **Modify map refs.** e **Start calibration**; abas **K factor** (ativa) e **Times**; campo **K factor = 1.5** (desabilitado, cinza). À direita barra vertical **30% / 0% / -30%** com leitura **0.0%**. Um **ponto azul** marca a posição atual na grade.
OBS-FIO (conferido em 3 imagens): o ponto fica na coluna do rpm atual e, na vertical, na posição do **Petrol Tinj.** na escala de ms: com 3,78 ms o ponto está na linha 3,50 (11-47-54); com 5,15 ms está entre 4,50 e 6,00, a 43% do caminho (11-49-59). INFERIDO: o ponto é a célula (interpolada) que a central está usando agora; a barra de 30%/-30% é a correção ao vivo em % (na 11-57-05 mostra **23,1 %** com a barra azul subindo acima do 0).

### 11-49-59 · F7, foco em "Modify map refs."
OBS-FIO: mesma grade; botão Modify map refs. com foco; Petrol Tinj. 5,15 ms, Gas Tinj. 14,25 ms, MAP 0,60 bar (ponto azul na linha 4,50, ver cálculo acima).

### 11-50-09 e 11-57-05 · janela "Map cell centres of the k coefficients"
OBS-FIO: janela aberta por Modify map refs.; colunas "REVS. columns (rpm)": **850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500**; "Time lines (ms)": **2, 2.5, 3, 3.5, 4.5, 6, 8, 10, 12, 14, 16, 18**; todos editáveis; botões **OK** e **Cancel**. As 2 imagens são iguais (a 11-57-05 mostra ΔRPM 3 e +23,1 % ao fundo). Já descrito no LEIA-ME; o "0 ms na 1ª linha" continua NÃO visto (a 1ª linha é 2).

### 11-50-40 · F7 aba "Times"
OBS-FIO: "**Petrol injector dead time 0.6 ms**"; "**Gas injector dead time**" (sem valor visível na coluna da esquerda); coluna "**extra-injections**" com **0 ms** (linha petrol) e **0 ms** (linha gas). Mesma grade do Mapa K acima.

### 11-50-49 · F8 Advanced
OBS-FIO: caixa **"Enable map of petrol contribution"** DESMARCADA; grade 12 x 12 (mesmos eixos rpm x ms) toda **0**, em cinza (desabilitada); nota em itálico: **"NOTE: the values are percentage of petrol injection time"**.

### 11-52-02 · F9 Injectors (= `F9-injetores-anti-stallo-ligado.png`)
OBS-FIO, na ordem da tela: "Increase max current in gas change-over" desmarcada; "Heating injectors" desmarcada; **Gas injector minimum opening time 2.5 ms**; **Gas extra-injection minimum opening time 0.1 ms**; "Extra-injections cutting" marcada; **"Enable un-stalling strategy" marcada**; **"Max RPM to enable un-stalling" 1600 rpm** (único número do anti-apagão visível); **Gas injectors timing advance: disabled** (lista, vermelho); **RPMs for gas injectors driving in cutoff 0 rpm**; **Gas injection time during cutoff 2.5 ms**; **Pressure for inj gas activation in cutoff 0 bar**; **T. petrol max for press. discharge in cutoff 0.8 ms**; **Enrichment in acceleration 50** (sem unidade na tela); **Weakening on Mazda™ − 5** (há um sinal "−" antes da caixa; se é rótulo de sinal ou botão não está claro). Rodapé com **ΔRPM 1**.
Conclusão para o anti-apagão: **confirmado, o original mostra UM só número** (1600 rpm). Os outros 4 do vetor 307 (queda para agir, espera para sair do gás, folga, queda para parar) NÃO aparecem em nenhuma tela capturada; continuam DESCONHECIDOS pelo original. O rodapé "ΔRPM" ao vivo (valores 1 e 3 na marcha lenta de ~865 rpm) mostra que o original calcula uma variação de rotação em unidade de "rpm" inteira; INFERIDO: os campos "Delta RPM" do vetor usam essa mesma grandeza (valor de fábrica 100 do DFM seria uma queda de 100 por leitura, bem acima do ruído de 1 a 3 da marcha lenta).

### 11-54-23 · "Automatic ECU calibration" (início do AutoCal)
OBS-FIO: janela própria; relógio **rpm x 1000** com **0870**; painel: Water temperature **63 °C**, Gas temperature **52 °C**, **Voltage for gas injectors 13.74 V**, Gas injection time **11.18 ms**, Petrol injection time **3.87 ms**, Pressure **2.32 bar**; barra **Lambda 0 a 5** = n.a.; pergunta: **"Has engine water temperature reached the value of about 80°C - 90°C?"** com **Continue / Cancel**; botão **Exit**.
Ensina: o AutoCal original só começa depois de o dono confirmar que a água está em ~80-90 °C; e o nome do dado de tensão é "tensão dos injetores de gás" (13,7 V em marcha lenta).

### 11-58-22 · "ECU diagnosis"
OBS-FIO: tabela "Diagnosis checks / State / Action". Linhas (código entre parênteses, caixa, estado, ação): (20x) Gas injectors ☑ OK Switch to petrol (lista aberta); (231) Reducer valve ☐ OK Switch to petrol; (190) Gas pressure sensor ☑ OK Switch to petrol; (105) Map sensor ☑ OK Switch to petrol; (180) Gas temperature sensor ☐ OK Switch to petrol; (600) Changeover switch present ☑ OK None; (460) HP sensor ☑ OK None; (181) Gas temperature too low ☐ (campo **-15**) OK None; Low pressure insulation ☐ None; High pressure insulation ☑ None; Low pressure ☐ None; Over pressure ☑ None; High pressure plausibility ☐ None; Low gas pressure ☐ None (a lista continua abaixo). Abas inferiores: Operating times | Gas injectors cut out | Petrol injectors connection | Switch/s... (setas); caixa de texto vazia; botão **Reset**; caixa marcada com texto **"#ERROR!"** (erro do próprio programa); botões **Reset errors** e **Exit**. Todos os estados em **OK**.
Ensina: cada defeito tem uma caixa (monitorar) e uma ação (trocar para gasolina ou nada). INFERIDO: o número é o código do erro da central.

### 11-59-27, 11-59-40 e 11-59-49 · "Graph - gnv 870 rpm 4"
OBS-FIO: gráfico em tempo real "Graph data in real time", eixo X **Time (s)** 0 a ~29, eixo Y 0 a 11; legenda: **Revs x 1000, Lambda, Gas inj. T, Petrol inj. T, T inj. benz. filter, Pressure, T.gas x 10, T.water x 10, MAP**; menu "Graph". Na 11-59-49 abre "**Add/remove traces**": marcadas Revs x 1000, Lambda, Gas inj. T, Petrol inj. T, T inj. benz. filter, Pressure, T.gas x 10, T.water x 10, MAP; desmarcadas as versões "**2**" (Gas inj. T 2, Petrol inj. T 2, T inj. benz. filter 2, Lambda 2 = segundo banco). Botões OK/Cancel.
OBS-FIO (leitura das curvas): gás ~10,5; gasolina ~4,8; MAP ~0,47; rpm ~0,87 (x1000); a curva "Pressure" fica em ~1,85 a 1,9, valor da **Press.diff.**, não da Press. de 2,4 (INFERIDO: o gráfico mostra a diferença).

### 12-00-04 · "Vehicle parameter display" (F2 Display)
OBS-FIO: menu "Acquisitions"; relógio rpm x 1000 **0860**; 4 lâmpadas: **Key-on** (vermelha, acesa), **Cutoff** (verde escuro, apagada), **Gas** (vermelha, acesa), **Petrol** (verde escuro, apagada); **Pressure 2.39 bar**; **Gas injection time 10.67 ms**; **Petrol injection time 3.82 ms**; **Level 125**; **MAP 0.46 bar**; **Water temperature 65 °C**; **Gas temperature 52 °C**; **Voltage for gas injectors 13.68 V**; barra Lambda 0 a 5 = n.a.; botão Exit.

### 12-01-44 · Menu principal "Landi Renzo Omegas" (ECU não conectada)
OBS-FIO: **F1 Vehicle configuration, F2 Display, F3 Diagnosis, F4 Automatic calibration, F5 Modify calibration, F6 Save current configuration, F7 Load new configuration, F8 Ecu programming, F9 Update schemes and maps, F10 Exit**; menu File / Connection / Help; barra "ECU not connected" (amarela) | "Config: 208 REFINO AGOSTO" | "CNG".

### 12-01-58 · Janela "Sobre" (Landi Renzo Omegas)
OBS-FIO: logo LANDIRENZO; abas **Product name** e **User code**; na aba User code há um código hexadecimal de usuário em 3 linhas (~65 caracteres). **A imagem NÃO foi versionada (repositório público): contém o código de usuário da licença. Ela continua só na pasta do Drive.** Botão Exit.

## Confirmação e correção do LEIA-ME.md

- Anti-apagão = **1 número visível (Max RPM 1600)**: CONFIRMADO (11-52-02). Os outros 4 não existem em tela alguma capturada.
- F4 "volta automática": 0 s e 1,25 bar CONFIRMADO; adicionado: é o par tempo+pressão (não o bit "gasolina em rotação alta").
- F7 bins: CONFIRMADO; acrescentado que o valor cru das células é uniforme por linha (só muda com o rpm) neste carro, e que existe a aba "Times" com tempos mortos.
- "Weakening on Mazda 5" no LEIA-ME: na tela há um "−" antes da caixa; sinal ainda não esclarecido.

## O que as capturas ensinam de COMO FUNCIONA (resumo)

1. **Troca para gás tem 3 condições visíveis**: tipo ("In acceleration"), rpm mínimo (1200 rpm) e temperatura da água (40 °C) quando "change-over with water temperature" está marcada (OBS-FIO). Gás e água usam sensores separados (F6).
2. **Volta à gasolina por pressão baixa** é um par: tempo (0 s = imediata) e valor (1,25 bar). Com Press.diff. normal ~1,95, a folga é ~0,7 bar (INFERIDO a comparação com a diferença).
3. **Press.diff. = Press. − MAP** (OBS-FIO) e é o que o gráfico traça como "Pressure".
4. **Mapa K**: eixos fixos 12 rpm x 12 tempos de gasolina em ms; o ponto ao vivo cai na linha do Petrol Tinj. (OBS-FIO); valores crus 226 a 253.
5. **Unidades**: tempos em ms, pressão em bar, temperatura em °C, rotação em rpm, tensão em V; nível do gás em número cru (sem unidade); "Enrichment in acceleration 50" sem unidade.
6. **Diagnóstico** liga cada falha a uma ação (trocar para gasolina ou nada).
7. **AutoCal original pede confirmação de água em 80-90 °C** antes de começar.

## LACUNAS DO APP vs ORIGINAL (ordenadas por impacto para o leigo)

Base: `app/src/main/assets/ui` (abas AutoCal, Ajuste local, Ajuste global, Sessões, Ferramentas; página 1 de Ferramentas = cartões de `core/cartoes-motor.js` e `core/anti-apagao-valores.js`). Sem alterar o app. "Falta" = não achei no código; confirmar ao construir.

| # | Função do original | No OMEGAS | Veredito |
|---|---|---|---|
| 1 | **Quando o gás entra (F1)**: tipo de troca, rpm mínimo 1200, água 40 °C | Cartão "Troca de gasolina para gás" mostra só os bits (um cilindro por vez, sobreposição, troca automática) | **FALTA** os dois números (rpm, °C) e o tipo. É a pergunta nº 1 do leigo ("por que ainda está na gasolina?"). |
| 2 | **Volta automática à gasolina por pressão baixa (F4)**: tempo 0 s, valor 1,25 bar | Cartão "Volta para gasolina" fala de rotação alta e bomba; não cita pressão nem 1,25 bar | **DIFERENTE/FALTA**: o cartão pode induzir a achar que "volta" = rotação. Explicar o par tempo+pressão. |
| 3 | **Diagnóstico (F3)**: 14 verificações, estado OK/erro, ação e caixa por linha, Reset errors | Só o bit "Apagar erros OBD" e a aba Ferramentas; sem tela de falhas por item | **FALTA** (no código atual de `assets/ui` não há tela de diagnóstico; AGENTS lista a aba Diagnóstico, conferir). |
| 4 | **Display vivo (F2) e Gráfico**: pressão do gás, Press.diff., T.gás, T.água, nível, tensão, lâmpadas Key-on/Cutoff/Gas/Petrol; gráfico com escolha de traços | Barra do topo só tem Tensão, MAP, Petrol Inj. e RPM | **FALTA** a maior parte; **RÓTULO**: original diz "Voltage for gas injectors" (13,7 V), app diz "Tensão da ECU" (escala provisória byte 19 ÷ 16). |
| 5 | **Injetores (F9) com números**: abertura mínima 2,5 ms, extra 0,1 ms, avanço, corte (rpm/ms/bar), enriquecimento 50, Mazda | Cartão "Injetores" só consulta 3 caixas; "Saída do corte" mostra um número de injeções | **FALTA** (campos, unidades e fábrica). |
| 6 | **Nível do gás (F4)**: 7 tipos de sensor, referências Reserva/1/4/2/4/3/4 (39/93/143/219), Set min/max, Find levels, "recognition of full" | Nenhuma tela; só a caixa "Reconhecimento de tanque cheio" nos flags | **FALTA**. |
| 7 | **AutoCal pergunta "água em 80-90 °C?"** antes de iniciar (11-54-23) | Não achei essa conferência no AutoCal | **FALTA** (didático e protege a coleta). |
| 8 | **Tempos mortos (F7 Times)**: gasolina 0,6 ms; extra 0 ms | Não achei | **FALTA**. |
| 9 | **Mapa de contribuição de gasolina (F8)**: 12 x 12 em % do tempo de gasolina, desligado | Só o bit "Mapa de adição de gasolina" (CheckContributi) em `advanced-flags-data.js` | **FALTA a grade**; unidade confirmada (%). |
| 10 | **Cadastro do carro (F1/F2/F6)**: combustível, cilindrada, nº de cilindros, ignição, sinal de rotação, sondas lambda, sensor de temperatura 4k7 | Sem cadastro; só bits soltos | **FALTA** (baixo risco, útil para o leigo ver "o que está configurado"). |
| 11 | **Mapa K / bins (F7)**: valor cru 226-253, ponto ao vivo, barra ±30 %, Modify map refs. | Mapa K existe com os mesmos eixos 12 x 12; edição das referências não achei | **IGUAL nos eixos; falta** editar referências (a regra do dono tranca a tela, não mexer sem pedido). |
| 12 | **Anti-apagão**: original mostra 1 número (1600 rpm) | Cartão "Valores do anti-apagão" mostra 5 (4 extras sem prova de efeito) | **DIFERENTE**: app expõe mais que o original; manter o aviso "experimental" nos 4 extras. |
| 13 | F6 Salvar / F7 Carregar configuração, F8 Ecu programming, F9 Update schemes and maps, Sobre/User code | Só backup da curva K e registro de sessão | **FALTA** (fora do escopo do leigo; F8/F9 ficam como não portar). |
| 14 | Lambda (F2) e lubrificante | Bit "Diagnóstico do nível de lubrificante" existe; sonda lambda sem tela | Parcial. |
