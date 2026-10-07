# AutoCal — gráfico informativo e interativo

**Primary user:** dono do carro / instalador, sem conhecimento de GNV | **Platform:** Android (WebView) em central multimídia de carro, toque, sem hover | **Depth:** Full | **Status:** Draft

## Purpose

O dono precisa saber, olhando o gráfico e sem abrir nada, se o GNV está igual à gasolina em cada faixa e o que a ECU está aprendendo agora. Sem isso ele depende de botões de "Detalhes" e "Histórico", que ninguém abre no carro, e erra o diagnóstico: hoje o gráfico mostra curvas e pontos, mas não diz o que cada ponto é, se está bom, nem o tamanho da diferença.

**Out of scope:** a codificação visual do gráfico (tipo de gráfico, escalas, cores, marcas): fica para a orientação de visualização de dados. Este spec define quais informações o gráfico deve entregar, onde ficam e como respondem ao toque. Também fora: Mapa K, Curva K e Ajuste GNV (outras abas).

## Users and Goals

| Role | Can do here | Cannot do here |
| --- | --- | --- |
| Dono / instalador | Ver a equivalência por faixa, tocar em ponto ou faixa para ler o valor, mandar a ECU reaprender pontos, pausar ou retomar o aprendizado, reler GNV ou gasolina | Editar valores da curva (isso é na aba Curva K / Mapa K) |

Goals:

- Saber em um olhar se o GNV está igual à gasolina e em quais faixas não está.
- Entender qualquer ponto tocando nele, sem sair do gráfico.
- Ver que o app limpou um ponto sozinho e por quê.

## Assumptions and Open Questions

| # | Assumption or question | Impact if wrong | Status |
| --- | --- | --- | --- |
| A1 | O gráfico continua com MAP no eixo vertical e injeção de gasolina (ms) no horizontal, como hoje. | Todo o posicionamento de legendas e do cursor muda. | Assumed |
| A2 | A diferença GNV × gasolina por faixa sai da razão entre os buffers nativos no mesmo MAP (mediana 1,01 nas sessões reais). | Se a ECU mudar o significado do buffer, o texto de diferença fica errado. | Assumed |
| A3 | "Fora da curva" usa o mesmo critério do apagamento automático (ajuste robusto do Refino). | Marca e apagamento divergiriam. | Assumed |
| A4 | O regime em que cada ponto foi aprendido (parado ou rodando) já existe no registro do apagamento automático, mas ainda não chega à tela. | Exige um campo novo na ponte para a callout dizer "aprendido parado". | Assumed |
| A5 | Resolução de referência 1024×600 em paisagem; sem mouse, então nada depende de hover nem de segurar. | Interações por hover não funcionam. | Assumed |
| Q1 | Mostrar a folga entre as duas curvas como área sombreada ou só como número? | É codificação visual; decide a orientação de visualização de dados. | Open |
| Q2 | Mostrar também o histórico (pontos apagados/reaprendidos) como camada opcional? | O dono pediu que histórico não exista; assumido que não. | Assumed: não |

## Entry and Exit Points

| Direction | Screen | Trigger | State carried |
| --- | --- | --- | --- |
| In | Barra de abas | Toque em AutoCal | Seleção de pontos limpa; última leitura mantida |
| In | Agora | Toque no botão de próxima ação quando for aprendizado | Nenhum |
| Out | Qualquer aba | Toque na barra de abas | Seleção de pontos descartada |

## Data Requirements

| Data | Read / write | Volume | Freshness | Notes |
| --- | --- | --- | --- | --- |
| Pontos nativos de gasolina e GNV (MAP, ms, contador de amostras por banda, 16 bandas) | Read | 32 pontos | Leitura a cada poucos segundos | Contador diz se o ponto está maduro |
| Curva do GNV hoje | Read | 1 curva | Idem | |
| Posição ao vivo (MAP, injeção de gasolina, combustível, rpm) | Read | 1 amostra | Cerca de 3 por segundo | Cursor "Agora" |
| Zonas aprendidas (Z1 a Z4, gasolina e GNV) | Read | 8 flags | Idem leitura dos pontos | |
| Diferença GNV × gasolina por faixa | Read | até 16 | Idem | Derivada dos pontos; ver A2 |
| Marca de ponto fora da curva | Read | até 32 | Idem | Ver A3 |
| Regime de aprendizado do ponto (parado ou rodando) | Read | até 32 | Por aquisição | Campo novo (A4) |
| Resumo da limpeza automática (ligada, pausada e motivo, reaprendidos na sessão, últimos apagamentos) | Read | Poucos itens | Por evento | Já existe na ponte |
| Pausar/retomar aprendizado, reler GNV, reler gasolina, reaprender pontos | Write | 1 ação | Sob demanda | Ações já existentes |

## Layout Structure

```emmet
screen[col, gap=xs]
  > chartArea[col, fill, gap=xs]
    > legendRow[row, justify=start, align=center, gap=md, hug]
      > curveLegend*
    + plot[fill]
      > gasolineCurve
      + gnvCurve
      + gasolinePoint*
      + gnvPoint*
      + zoneLabel*
      + liveCursor
      + equivalenceStrip
      + autoCleanChip[hug]
      + errorBanner?[hug]
      + crosshairReadout?[overlay, hug]
      + pointCallout?[overlay, col, gap=xs, hug]
        > pointHeadline
        + pointFacts
        + reacquireInline?
  + actionBar[row, gap=sm, align=center, hug]
    > pauseResumeButton
    + rereadGnvButton
    + rereadGasolineButton
    + reacquireSelectedButton?
    + cancelSelectionButton?
```

## Layout Breakdown

### chartArea

Purpose: dar o máximo de área ao gráfico; nenhum bloco de texto fixo acima dele (título, quadros e legenda de zonas saem).

Contains:

- legendRow: uma linha fina que nomeia as duas curvas e os dois tipos de ponto (ponto da ECU, ponto ainda em aprendizado).
- plot: o gráfico em si, ocupando todo o espaço restante.

### plot

Purpose: responder "o GNV está igual à gasolina?" e "o que a ECU está aprendendo agora?".

Contains:

- gasolineCurve e gnvCurve: as duas curvas.
- gasolinePoint* e gnvPoint*: um ponto por banda aprendida, de cada combustível.
- zoneLabel*: rótulos Z1 a Z4 junto ao eixo, nunca no mesmo lugar do cursor.
- liveCursor: a posição atual do carro.
- equivalenceStrip: uma faixa estreita ao longo do eixo horizontal com a diferença GNV × gasolina por faixa, em texto curto ao toque e com marca por faixa.
- autoCleanChip: "Limpeza automática: ligada · N pontos reaprendidos".
- errorBanner: faixa fina só quando o AutoCal não responde.
- crosshairReadout e pointCallout: sobreposições que aparecem ao toque.

Shown when (conditionals):

- errorBanner: só quando a ECU não entrega o estado do AutoCal.
- crosshairReadout: após toque em área vazia do gráfico; some ao tocar de novo ou em outro ponto.
- pointCallout: após toque em um ponto; some ao tocar fora.
- reacquireInline: dentro do pointCallout, só quando o ponto estiver fora da curva ou maduro e apagável.

### actionBar

Purpose: todas as ações em uma única linha, um toque cada, visíveis sem rolar.

Shown when:

- reacquireSelectedButton e cancelSelectionButton: só enquanto houver pontos marcados.

## Component Details

### plot

Type: Gráfico interativo.

Why it exists: único lugar onde a diferença entre GNV e gasolina e o aprendizado da ECU aparecem juntos.

Data: pontos nativos, curva do GNV, posição ao vivo, zonas, diferença por faixa, marca de fora da curva.

Fields:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| Extensão horizontal | Faixa de ms | Yes | Deve incluir a injeção atual: o cursor nunca fica "fora da escala" |
| Extensão vertical | Faixa de MAP | Yes | Fixa, para zonas estáveis |

Behavior:

- A extensão horizontal cresce para conter o cursor; se a injeção atual passar do limite habitual, o eixo se ajusta em vez de escrever "fora da escala".
- Ponto imaturo (poucas amostras) tem aparência distinta de ponto maduro, além da cor, para quem não distingue cor.
- Ponto fora da curva tem marca própria (forma diferente), nunca só mudança de cor.
- Ponto recém-apagado fica esmaecido e intocável até a ECU reler.
- As barras laterais sem legenda do gráfico atual saem; o progresso por zona aparece dentro de cada zoneLabel (por exemplo "Z2 · 5 de 6 pontos").

States:

| State | What the user sees | What the user can do |
| --- | --- | --- |
| Empty | Gráfico com os eixos e a frase "Aguardando a ECU" no centro | Nada além das abas |
| Loading | Última leitura mantida, esmaecida, com "Atualizando" no chip | Tocar nos pontos da leitura anterior |
| Populated | Curvas, pontos, cursor, faixa de equivalência | Tocar pontos, faixas e área vazia; usar a barra de ações |
| Error | errorBanner fino "AutoCal indisponível. Reconecte a ECU e tente de novo." sobre o gráfico, que mostra a última leitura | Reconectar o cabo; as demais ações ficam desabilitadas com o motivo |

### liveCursor

Type: Marcador ao vivo.

Why it exists: mostrar onde o carro está agora na curva e se ali o GNV acompanha a gasolina.

Data: MAP e injeção de gasolina atuais, combustível.

Behavior:

- Rótulo curto "Agora" ao lado, nunca sobreposto a rótulo de zona; se estiver na borda, o rótulo vai para o lado de dentro com seta.
- Ao tocar nele, abre o crosshairReadout fixo na posição ao vivo.

States: Empty (sem telemetria): some. Loading: congela na última posição com indicação de atraso. Populated: segue o carro. Error: some e o errorBanner explica.

### equivalenceStrip

Type: Faixa de diferença por faixa.

Why it exists: responde "está igual?" sem número técnico: cada faixa indica se o GNV está igual, acima ou abaixo da gasolina.

Data: diferença percentual por faixa (A2).

Behavior:

- Cada faixa tem rótulo de texto curto e uma marca não dependente de cor ("igual", "GNV +6%", "GNV −6%"). Tolerância de "igual" é a mesma do Refino.
- Ao tocar numa faixa, destaca os pontos dela e abre o crosshairReadout com a frase completa.

States: Empty (sem pontos nas duas curvas): "Faltam pontos" nessa faixa. Loading: mantém o último valor. Populated: marca por faixa. Error: omitida.

### crosshairReadout

Type: Leitura sobreposta.

Why it exists: dar valores exatos de qualquer posição sem botão de detalhes.

Data: MAP e injeção na posição tocada, valor das duas curvas, diferença.

Behavior:

- Frase única em português simples, por exemplo "Com carga moderada: gasolina 3,4 ms, GNV 3,6 ms — GNV 6% acima".
- Acompanha o dedo apenas durante o arraste; ao soltar, fica fixa até novo toque.

States: Empty (toque fora das curvas): "Sem dados aqui". Loading/Populated/Error: usa a última leitura; no erro mostra "Sem leitura".

### pointCallout

Type: Balão ancorado ao ponto.

Why it exists: dizer o que o ponto é e se deve ser reaprendido, sem sair do gráfico.

Data: combustível, banda, ms, MAP, contador de amostras, diferença para a outra curva, marca de fora da curva, regime de aprendizado (A4), zona.

Behavior:

- Título curto: "Ponto 4 do GNV · zona 2".
- Fatos em até três linhas, por exemplo "A ECU já passou aqui 12 vezes · 3,2 ms · 6% acima da gasolina" e, quando houver, "Aprendido com o carro parado".
- Se o ponto estiver fora da curva: linha "Fora da curva — o app reaprende sozinho quando você andar" e botão reacquireInline "Reaprender agora".
- Tocar em outro ponto troca o balão; tocar fora o fecha.

States: Empty: N/A (nasce de um ponto existente). Loading: botão desabilitado com "Aguarde". Populated: como acima. Error: botão desabilitado com o motivo ("sem conexão").

### autoCleanChip

Type: Chip de estado.

Why it exists: o dono precisa saber que o app está cuidando dos pontos, e quando parou.

Data: resumo da limpeza automática.

Behavior:

- Ligada: "Limpeza automática: ligada · N pontos reaprendidos".
- Pausada: "Limpeza automática pausada: <motivo simples>. Reconecte o cabo."
- Ao apagar um ponto sozinha, uma frase aparece por poucos segundos ao lado do chip: "Reaprendendo o ponto X do GNV — estava fora da curva."

States: Empty (sem conexão): oculto. Loading: mantém o texto atual. Populated: como acima. Error: mostra pausada com motivo.

### actionBar

Type: Barra de ações, uma linha.

Why it exists: todas as ações a um toque, lado a lado, sem menus.

Data: estado do aprendizado, seleção de pontos.

Behavior:

- pauseResumeButton alterna "Pausar aprendizado da ECU" e "Retomar aprendizado".
- rereadGnvButton "Reler GNV" e rereadGasolineButton "Reler gasolina": um toque, sem confirmação.
- reacquireSelectedButton "Reaprender N pontos" e cancelSelectionButton "Cancelar": só com seleção.
- Ações de gravação desabilitam com motivo curto quando não houver conexão ou o aparelho não tiver o controle da ECU.

States: Empty: botões desabilitados com motivo. Loading: o botão em uso mostra "Aguarde…". Populated: habilitados. Error: o motivo aparece no próprio botão desabilitado.

## User Flow

Primary path:

1. O dono abre AutoCal e vê as duas curvas, os pontos, o cursor e a faixa de equivalência.
2. Lê "igual" na maioria das faixas e "GNV +6%" em uma.
3. Toca a faixa: o gráfico destaca os pontos dela e mostra a frase completa.
4. Toca um ponto fora da curva: o balão explica e oferece "Reaprender agora".
5. Toca "Reaprender agora": o ponto esmaece e o chip mostra "Reaprendendo".
6. A ECU aprende de novo andando; o ponto volta, a faixa passa a "igual".

Alternate paths:

- O app apaga o ponto sozinho: o dono só vê o aviso curto e o ponto esmaecido.
- Várias bandas ruins: toca em cada uma (marca), usa "Reaprender N pontos".
- ECU sem resposta: errorBanner e ações desabilitadas; o gráfico mostra a última leitura.

## Interaction Rules

| Trigger | Result | Feedback | Reversible |
| --- | --- | --- | --- |
| Tocar um ponto | Abre pointCallout; marca o ponto como selecionado | Balão ancorado, ponto destacado | Sim: tocar fora |
| Tocar de novo no mesmo ponto | Desmarca | Balão fecha | Sim |
| Tocar área vazia do gráfico | Abre crosshairReadout na posição | Linha de leitura | Sim: tocar de novo |
| Tocar uma faixa da equivalenceStrip | Destaca pontos da faixa; abre readout | Frase da diferença | Sim |
| Tocar no cursor | Fixa o readout na posição ao vivo | Frase "Agora" | Sim |
| Tocar "Reaprender agora" (balão) | A ECU apaga o ponto e reaprende | Ponto esmaecido; chip "Reaprendendo" | Não (a ECU não devolve ponto apagado); sem confirmação, por escolha do dono |
| Tocar "Reler GNV" / "Reler gasolina" | A ECU zera o aprendizado daquele combustível | Botão em "Aguarde…"; gráfico esvazia aquele combustível | Não; sem confirmação, por escolha do dono |
| Tocar "Pausar aprendizado" | Aprendizado da ECU pausa | O botão vira "Retomar aprendizado" | Sim: tocar de novo |
| Evento de apagamento automático | O ponto esmaece e vira aviso por poucos segundos | Aviso ao lado do chip | N/A |

## Validation Rules

- Ponto sem amostras (contador zero) nunca é alvo de "Reaprender": o balão diz "Este ponto já está vazio" e não oferece o botão.
- Ponto apagado há pouco não pode ser selecionado até a ECU reler (evita a seleção fantasma).
- Duas frases de equivalência nunca se contradizem: a faixa e o readout usam a mesma diferença.

## Screen States

| State | When | What the user sees | Available actions |
| --- | --- | --- | --- |
| First use | ECU nunca aprendeu | Eixos com a frase "A ECU ainda não aprendeu. Dirija um pouco." | Pausar aprendizado, Reler |
| No results | Uma das curvas sem pontos | Só a curva existente; faixa de equivalência diz "Faltam pontos" | Pausar, Reler |
| Loading | Primeira leitura | Eixos e "Lendo a ECU…" | Navegar pelas abas |
| Error | ECU não responde | errorBanner fino sobre a última leitura | Reconectar o cabo |
| No permission | N/A: o AutoCal só lê; as ações desabilitam com motivo | N/A | N/A |

## Responsive Behavior

| Size | What changes | Why |
| --- | --- | --- |
| Wide (central multimídia, paisagem) | Como na árvore | Base do projeto |
| Medium | A barra de ações mantém uma linha; rótulos encurtam ("Pausar", "Reler GNV", "Reler gasolina") | Os três botões precisam caber lado a lado |
| Narrow (retrato/celular) | O pointCallout vira painel inferior; a legenda vira só ícones | Dedo cobre o ponto tocado; pouco espaço horizontal |

## Accessibility

- Keyboard: setas percorrem os pontos em ordem de MAP; Enter abre o balão; Esc fecha; todas as ações da barra são alcançáveis por Tab.
- Focus: ao abrir o balão o foco vai ao título; ao fechar volta ao ponto; após "Reaprender" o foco volta à barra de ações.
- Screen reader: cada ponto tem nome do tipo "Ponto 4 do GNV, 3,2 ms, 6% acima da gasolina, fora da curva"; mudanças (reaprendeu, pausou) são anunciadas sem recarregar a tela.
- Non-color cues: maduro × imaturo × fora da curva × esmaecido distinguem-se por forma e rótulo, não só por cor; a faixa de equivalência tem texto.
- Targets and motion: todos os pontos têm área de toque no tamanho mínimo da plataforma, maior que a marca desenhada; animações respeitam redução de movimento.

## Future Extensibility

- Camada opcional para comparar duas sessões: a árvore já isola cada curva em um nó próprio.
- Mostrar a folga entre as curvas como área (Q1) sem mudar a estrutura: a faixa de equivalência já recebe a mesma diferença.
- Nova ação por ponto no balão (por exemplo, marcar ponto como "forma real"): reacquireInline é um slot, não um botão fixo.
