# OMEGAS Platina — Norte Único: GNV equivalente à gasolina

**Data:** 2026-10-03 · **Base:** branch `OmegasPlatina` · **Status:** spec aprovada em conversa, aguardando revisão escrita do dono.

**Norte deste documento (e de tudo que vier depois):**
- [Blueprint Premium UI/UX — Método CUSTOMROM reutilizável](https://app.notion.com/p/3b68ee52ac5481839046f36b482aab44)
- [Método aplicado — Omega Dev 4.0 Premium UI/UX](https://app.notion.com/p/3b78ee52ac548170b5c1fb69606ced21)

Citações como `CR §19` apontam para o Blueprint CUSTOMROM; `OD §12` para o Método Omega Dev.

Este documento substitui `AGENTS.md`, `PROJECT.md`, `LEARNING_RULES.md`, `docs/ARCHITECTURE.md` e os `docs/BLOCK_*.md` como fonte de direção. Eles são reescritos na Fatia 0 (ver §7).

---

## 0. A meta do app, em uma frase

> **O motor, no GNV, se comporta como na gasolina.** O app sabe quão perto disso está, onde falta, e qual é a única próxima ação. Ele observa sozinho; só muda algo quando o dono toca.

Tudo abaixo serve a essa frase. O que não serve, sai.

### 0.1 O que o app mede (telemetria da ECU MP48, sem sonda, sem OBD)

RPM · MAP (bar) · tempo de injeção de gasolina (`petrol_ms`) · tempo de injeção de GNV (`gas_ms`) · pressão do gás (absoluta e diferencial) · combustível ativo · nível · velocidade GPS quando ligado.

**A sonda indireta.** A central original do carro continua calculando `petrol_ms` no GNV, corrigido pela sonda dela. Se no GNV ela pede mais ms de gasolina do que pediria na gasolina no mesmo ponto da curva, o GNV está pobre; se pede menos, rico. A **Curva K** (30 pontos, MAP × tempo de injeção, `MUL_ACT` Q14) é a régua. O **Mapa K** (RPM × MAP) é ajuste fino secundário.

### 0.2 Regras invariantes (valem para todo o código e toda a UI)

1. **Observar é automático; mudar é sempre o dono.** O app lê, mede, compara, grava sessão e propõe. Nunca grava K, zera, restaura ou aplica nada sozinho.
2. **Todo botão é um toque.** Sem diálogo de confirmação, sem segurar, sem repetir. A proteção é a **foto antes** (snapshot da curva/aquisição) e o **Desfazer depois** (`CR §19`).
3. **O fim de toda ação é a releitura da ECU.** "Gravado" só aparece depois do readback bater. Antes disso, o botão mostra a etapa (`OD §12`).
4. **Uma autoridade de estado.** Uma ponte, um `snapshot()` com revisão, uma fila de operações (`OD §11`).
5. **Nenhuma falha derruba o app.** Toda exceção vira estado `✗` legível com próxima ação (`OD §18`).
6. **Dois níveis sempre:** frase humana primeiro; comando, bytes e readback em "Ver detalhe" (`OD §8`, `CR §22`).
7. **Erro de transporte ≠ erro da ECU** (`CR §46`).
8. **Os comandos de leitura e escrita da ECU não mudam.** `UsbSerialManager`, `ResponseDrivenEcuEngine`, `AutoCalProtocol`, `KFactorManager`, `KWriteManager`, `AutoCalNativeActionManager` são provados e ficam intactos; só passam a ser chamados pela fila.
9. **Todo teste roda no GitHub Actions.** A sessão de execução edita, empurra e lê o resultado. Nada é compilado ou testado localmente.
10. **Uma direção visual só:** tokens CUSTOMROM, cor com semântica, cards só com unidade semântica, normalidade compacta (`CR §23–§35`).

---

## 1. O cérebro único: Ponto de Equivalência

### 1.1 Unidade básica

Cada um dos **30 pontos da Curva K** vira um objeto `EquivalencePoint`. O eixo de tudo é o **tempo de injeção de gasolina (ms)**, que é o eixo da curva. Cada ponto sabe:

| Dimensão | O que responde | Como calcula | Fonte |
|---|---|---|---|
| **Mistura** | GNV pobre ou rico aqui? | `K_alvo / K_atual`, com `K_alvo(T_p) = K(T_g)·T_g/T_p` (já existe no `AutoMatchRefinedEngine`) + **intervalo de confiança** pela dispersão das amostras | `T_p` da **Curva Própria de gasolina** e `T_g` da **Curva Própria de GNV** (§1.6b), ambas aprendidas da telemetria com a Referência congelada (§1.6) como prior |
| **Forma** | A curva é lisa aqui? | inclinação local `|Δ ln K / Δ ln t|` entre vizinhos, contra o teto `E_MAX` | curva atual e proposta |
| **Experiência** | Anda igual à gasolina aqui? | no GNV: desvio de RPM com MAP estável (após remover tendência) e quase-apagões/hora; **sempre comparado à gasolina no mesmo MAP equivalente**, não no ms cru (no GNV pobre o ms sobe e "escorrega" de faixa) | telemetria + `StallWatch` |
| **Uso** | Quanto tempo o dono passa aqui? | fração do tempo de condução (RPM ≥ 1000) por ponto, janela móvel das últimas 10 sessões | sessões |

### 1.2 Estado de cada ponto (linguagem humana, é o que a UI mostra)

```
SEM_DADOS → APRENDENDO → MEDIDO → { EQUIVALENTE | POBRE n% | RICO n% } → EM_PROVA → CONFIRMADO | CONTESTADO | INCONCLUSIVO
```

Tolerâncias (herdadas do Refino v2, um lugar só, `EquivalenceTolerances`): `EQUIVALENTE` = |desvio| ≤ `tol`, com `tol = max(4%, 2 × dispersão da célula)` (§1.6b); `POBRE/RICO` leve = até 8%; acima disso, desvio grande (vermelho). "Piorou" exige piora > 4% **e** erro > 5%, como hoje.

- `MEDIDO` exige amostra mínima e intervalo de confiança abaixo do limiar (valores herdados de `BAND_MATURE_COUNT`, `TELEMETRY_MIN_MS`, tolerâncias do Refino v2).
- `EM_PROVA` começa quando o dono grava um ajuste que toca esse ponto; termina quando há amostra nova suficiente **ou** o timebox do Refino v2 expira (vira `INCONCLUSIVO`, visível).
- `CONFIRMADO` = Mistura dentro da tolerância **e** Experiência não piorou. `CONTESTADO` = Mistura melhorou mas Experiência piorou (tremor ou quase-apagão subiu). Contestado aparece em âmbar com a frase "melhorou a mistura, piorou a suavidade".
- Nada disso age. Só mede e mostra.

### 1.3 O índice: "% da sua condução equivalente à gasolina"

`índice = Σ(uso_i · equivalente_i) / Σ(uso_i)` sobre pontos com estado ≥ `MEDIDO`. Pontos sem confiança não entram nem no numerador nem no denominador; a cobertura (quantos pontos entram) é mostrada ao lado. Guardado por sessão para desenhar a evolução.

### 1.4 Próxima ação (uma só, a mais valiosa)

Ordem de prioridade, primeira que casar:

1. **Operação em andamento** → mostra o estado dela.
1b. **Sem Referência congelada e aquisição de gasolina madura** → "Congelar esta curva como referência". (Sem Referência o app ainda propõe quando a Curva Própria estiver madura, marcado "sem referência da ECU"; a âncora só encurta o caminho.)
2. **Pontos `CONTESTADO`** → "Ajuste em 6–8 ms piorou a suavidade · Desfazer".
3. **Pontos `POBRE/RICO` com confiança, ponderados por Uso** → "3 pontos pobres entre 6 e 8 ms (4–6%) · Aplicar ajuste" (abre a curva proposta).
4. **Pontos `EM_PROVA`** → "Rodando para provar o ajuste · faltam ~N min de condução nessa faixa".
5. **Buraco de dados onde há Uso** → "Rode em subida leve (~0,6 bar) para eu medir entre 5 e 7 ms".
6. **Nada** → "Equivalente. Nada a fazer."

### 1.5 Quem propõe K

**Só a Mistura**, pelo `AutoMatchRefinedEngine` (isotônico → K_alvo → Whittaker em ln K → trava de passo e inclinação). Forma e Experiência **nunca** entram no cálculo do K; elas confirmam, contestam e localizam. Isso mantém cada proposta explicável: "pobre 5% aqui, com 42 amostras, por isso +5%".

### 1.6 A Referência: curva de gasolina do AutoCal nativo, congelada

**A base mais sólida é a curva de gasolina que a própria ECU gerou no AutoCal nativo.** Ela é a média da ECU por banda, sobre muito mais amostras do que qualquer leitura do app, e a validação real já existente concorda: contra a gasolina medida na mesma sessão, mediana 1,000 e 80% dos pontos dentro de 2,3%. **Toda a inteligência do app gira ao redor dela.**

**Congelar.** A curva de gasolina da ECU não é usada "ao vivo": o dono **congela** uma aquisição como **Referência** (intent `REFERENCE_FREEZE`, um toque, na aba AutoCal e na dica de primeiro uso). O congelado é um objeto versionado, como o backup da Curva K:

```
Reference { id, frozenAt, ecuAcquisitionFingerprint, points[MAP→petrol_ms], maturity[], source=AUTOCAL_NATIVO }
```

- **Existe uma Referência congelada por vez.** Congelar de novo (depois de readquirir, por exemplo) substitui a anterior; a anterior vira a foto do Desfazer e some ao fim da sessão. Nada de lista para o dono gerenciar.
- **Sem Referência congelada** (app novo, ECU sem aquisição madura): o app trabalha em modo `PROVISÓRIO` com a curva da ECU ao vivo, marcado na UI, e a próxima ação é "Congelar esta curva como referência" assim que a aquisição de gasolina estiver madura (≥ `ECU_REF_MIN_POINTS`, faixa ≥ `ECU_REF_MIN_SPAN_BAR`).
- **A ECU continua readquirindo por conta própria** (é dela). Isso não muda a Referência: o app mostra "a ECU reaprendeu gasolina desde o congelamento (diferença máx. n%)" e oferece "Congelar de novo". Decisão do dono, um toque.

### 1.6b A Curva Própria: o app aprende sozinho, mais denso que a ECU

A Referência é a âncora. **Mas o app tem inteligência própria:** ele aprende, a partir da telemetria, a sua **Curva Própria de gasolina** e a sua **Curva Própria de GNV**, e elas podem ficar **melhores que a da ECU**, inclusive onde a ECU já marcou ponto.

**Por que podem ficar melhores.** A ECU resume tudo em 18 bandas, cada uma com 1–2 amostras, sem suavização (é a causa dos dentes de serra, `OMEGAS-WU-006`). O app vê cada quadro de telemetria: milhares de leituras estáveis por sessão, com RPM, MAP, tempo e contexto. Com isso ele resolve a curva em **células de 0,02 bar** (~40 células na faixa útil, contra 18 bandas), com dispersão e contagem por célula.

**Como aprende (é o motor que já existe, com a Referência como ponto de partida):**

```
Curva Própria T(MAP) = ajuste isotônico robusto das leituras estáveis
                       + Referência como prior, com peso que cai conforme a célula enche
                       + suavização Whittaker (λ já validado por validação cruzada nas sessões reais)
```

- **Célula vazia** → vale a Referência (prior puro). A linha é tracejada ali.
- **Célula enchendo** → mistura; a tela mostra a transição.
- **Célula madura** (contagem ≥ `BAND_MATURE_COUNT`, dispersão abaixo do limiar) → a Curva Própria domina, mesmo que discorde da Referência. A discordância é mostrada, não escondida: "aqui a sua curva está 2,4% abaixo da ECU, com 312 amostras e dispersão 1,8%".
- **Discordância grande** (> 8%) de uma célula madura contra a Referência é sinal, não erro: aparece em âmbar com "a ECU pode ter aprendido esse trecho mal · Reaprender ponto na ECU" (um toque, `AUTOCAL_RELEARN`). É o app corrigindo a ECU, com evidência.

**O que isso muda nas outras seções:**

- **Mistura (§1.1)** usa `T_p` da Curva Própria de gasolina e `T_g` da Curva Própria de GNV, avaliadas no MAP de cada ponto da Curva K. A Referência entra só como prior.
- **A Curva Própria de gasolina persiste sempre.** Gasolina não depende da Curva K nem da Referência: é conhecimento do app. Mudar a Curva K descarta só a Curva Própria de GNV (como o `EquivalenceLedger` já faz). **Trocar a Referência não apaga nada:** só troca o prior, a curva é reajustada, e os pontos `EM_PROVA` recomeçam a prova.
- **Sem Referência congelada**, o app aprende do zero com prior fraco. Ele **pode** propor ajuste de K quando a Curva Própria estiver madura na faixa, marcado como "sem referência da ECU"; a próxima ação continua sugerindo congelar, porque a âncora encurta o caminho.
- **Tolerância deixa de ser número fixo:** `tol = max(4%, 2 × dispersão da célula)`. Célula bem medida é cobrada com rigor; célula rala ganha folga, e a folga aparece na UI como barra de confiança.
- **Na tela Curva**, duas linhas de gasolina: Referência (tracejada) e Própria (sólida), com sombreado onde divergem. O dono vê o app ficando mais esperto que a ECU, sessão a sessão.

**Gate (CI):** nas sessões reais, a Curva Própria madura prevê a gasolina de uma faixa escondida (validação cruzada) com erro menor que a Referência sozinha. Se não prevê, o aprendizado próprio não está agregando e o PR não passa.

### 1.7 Evidência

- Cada ponto guarda `samples`, `sources` (`AUTOCAL|TELEMETRIA|ECU_REF`), `confidence`, `lastSeenAt`. Tudo visível em "Ver detalhe".
- **Gate de verdade (CI):** replay das sessões reais já versionadas (`fixtures/`, `evidence/`): o índice das sessões posteriores a um ajuste gravado tem que ser maior que o das anteriores. Se não for, a métrica está errada e o PR não passa.
- Paridade Kotlin ↔ oráculo Python (`tools/autocal_refine/`) mantida e estendida ao índice.

### 1.8 O que morre com isso

Cérebro 2 (`MotorLearningMemory`/Advisor e a referência adaptativa), cérebro 3 (`com.omegas.v7`, coordenador e fila de sugestões), Predictor. Detalhe em §4.

---

## 2. Uma autoridade: `snapshot()` e `request()`

### 2.1 Uma ponte JS ↔ Kotlin

`OmegasNative`, `OmegasV7`, `OmegasAutoCal` e `OmegasPower` viram **uma** interface, `Omegas`, com dois verbos e nada mais:

```
Omegas.snapshot(sinceRevision) → { revision, connection, live, now, reference, points[30], index, nextAction, operation, session, autocal, settings }
Omegas.request(intent)         → { receiptId }        // enfileira e volta na hora
```

Mais um canal de eventos (`onSnapshot(revision)`) para a tela saber que há revisão nova sem fazer polling por timer.

- A tela só redesenha o pedaço cuja revisão mudou (`CR §52`). Telemetria pode chegar a 80 ms; a tela não.
- **Teste de contrato:** todo método da ponte tem ao menos um chamador no JS, e todo intent do JS existe no Kotlin. Lista cruzada por teste.

### 2.2 Intents (lista fechada)

| Intent | Risco | Foto antes | Desfazer |
|---|---|---|---|
| `CURVE_WRITE(points)` | amarelo | curva | sim |
| `CURVE_RESET` | amarelo | curva | sim |
| `CURVE_RESTORE(backupId)` | amarelo | curva | sim |
| `CURVE_BACKUP_SAVE(label)` | verde (lê e salva arquivo; não muta a ECU) | — | — |
| `MAP_WRITE(cells)` | amarelo | mapa | sim |
| `REFERENCE_FREEZE` | verde (não toca a ECU) | Referência anterior | sim: volta à anterior, até o fim da sessão |
| `AUTOCAL_RELEARN(points)` | amarelo | — | **não**: não há comando provado que devolva pontos apagados à ECU; a linha diz "a ECU vai reaprender estes pontos" |
| `AUTOCAL_PAUSE` / `AUTOCAL_RESUME` | verde | — | — |
| `AUTOCAL_RESET_GAS` / `AUTOCAL_RESET_PETROL` | amarelo | aquisição + curva | sim: devolve a curva K da foto; o que a ECU reaprende depois é novo, e a linha diz isso |
| `SESSION_EXPORT` | verde | — | — |
| `OVERLAY_TOGGLE` | verde | — | — |
| `SETTINGS_SET(k,v)` | verde | — | — |

Saem: `MANUAL_AUTOMATCH`, `RESET_ALL` como ação distinta (zerar gás e gasolina em sequência cobre o caso, cada um com Desfazer), todos os `*Link*`, `registerRefuel`, `restartEngine`, `setGnvSettings` e os demais ~30 métodos sem botão.

### 2.3 Fila de operações

- **Uma mutação na ECU por vez.** A fila serializa; a porta serial já tem árbitro.
- Ciclo de vida de **toda** operação, visível no botão que a pediu e na barra global:

```
RECEBIDO → PREPARANDO (foto) → EXECUTANDO → CONFERINDO (readback)
        → ✓ CONCLUÍDO · Desfazer
        → ✗ FALHOU: <motivo humano> · Ver detalhe · <próxima ação>
```

- Feedback em < 100 ms em qualquer etapa. Sem porcentagem inventada.
- Falha de transporte (cabo, USB, timeout) e recusa da ECU (readback não bate, NACK) são estados distintos com próximas ações distintas.
- Toda operação deixa **recibo na sessão**: intent, foto, bytes enviados, readback, duração, resultado. O recibo é o que inicia `EM_PROVA` nos pontos tocados.
- `Desfazer` é um intent como qualquer outro (`CURVE_RESTORE(fotoId)`), com o mesmo ciclo. Expira junto com a sessão.

### 2.4 Estado interno

Um `AppState` imutável em Kotlin, com `revision` monotônica, publicado por um único `StateStore`. Quem escreve nele: o motor de telemetria, o `EquivalenceEngine`, a fila, o monitor do AutoCal, a sessão. Quem lê: só a ponte. `RuntimeSnapshotBus`, `BackgroundMemo`s do AutoCal, arquivos JSON do ledger/journal continuam como **cache interno** dos motores, mas não são mais fonte para a UI.

---

## 3. Experiência: multimídia 1280×720, uma direção só

### 3.1 Navegação: a estrutura da Platina, com subpáginas onde a lógica pede

**Ponto de partida é o que já existe e funciona** (`CR §1`, `CR §5`): trilho vertical de 7 itens numerados à esquerda (`index.html:13-34`), cabeçalho com eyebrow e título, `vehicle-status-strip` global, e três padrões de "um nível abaixo" que a Platina já usa e o dono aprovou no aparelho:

| Padrão existente | Onde está hoje | Quando usar |
|---|---|---|
| **Subpágina por tablist** (troca o painel, a aba fica visível, volta num toque) | Ajuste global: `Aprendizado global \| Editar Curva K` (`curve.js:61-69`) | quando uma aba tem duas ou três **visões** da mesma coisa |
| **Overlay de operação** (cobre a tela: gravando → resultado → "Voltar") | Mapa e Curva (`index.html:93-94, :154-155`); revisão do AutoCal e do Refino | toda mutação na ECU: é o ciclo de vida de §2.3 desenhado |
| **`<details>` no lugar** | "Detalhes técnicos", "Contexto da aquisição", "Evidência técnica" | o nível técnico (`OD §8`); nome único passa a ser **"Detalhes técnicos"** em todas |

**O que não muda:** o trilho, os 7 lugares, os três padrões, o cabeçalho, a faixa de status. A interface é a mesma parado e em movimento; não há modo.

**O que muda:** o conteúdo das abas, porque hoje três delas (Ajuste global, Refino, Sugestões) disputam a mesma tarefa e uma (Sugestões) não é alimentada por nada. As 7 abas passam a ser:

| # | Aba (rótulo no trilho) | Responde a | Subpáginas (tablist) | Vem de |
|---|---|---|---|---|
| 01 | **Agora** | "Como estou e o que faço agora?" — índice grande, próxima ação com **um** botão (abre a aba certa, já posicionada), curva mini com ▲ AGORA, combustível, conexão | — | Agora (o tile REFINO vira a próxima ação) |
| 02 | **Mapa K** | ajuste fino por célula RPM × MAP | — (overlay de gravação/resultado como hoje) | Ajuste local, igual |
| 03 | **Curva K** | "Onde o GNV difere da gasolina e o que gravo?" | `Equivalência` (Referência tracejada, Própria sólida, 30 pontos por estado, ▲ AGORA, **Aplicar ajuste** com a proposta em fantasma) · `Editar` (inspector ponto a ponto, Preparar, Gravar, como hoje) · `Backups` (salvar, listar, restaurar, resetar) | Ajuste global (`Aprendizado global` vira `Equivalência`) + o gráfico do Refino |
| 04 | **AutoCal** | "O que a ECU já aprendeu?" | `Aquisição` (matriz 18 × gasolina/GNV, Pausar/Retomar, Reaprender, Zerar gás/gasolina, inspector de ponto) · `Referência` (card da curva congelada, **Congelar** / **Congelar de novo**, diferença contra o que a ECU tem agora) · `Épocas` (o "Ver sessões" de hoje) | AutoCal, sem AutoMatch manual e sem "Nova aquisição completa" |
| 05 | **Refino** | "Em que fase estou e o que falta?" | `Fases` (a lista de 5 fases de hoje, com os estados de §1.2, e o botão único por fase) · `Pontos` (30 linhas: ms · estado · % · amostras · uso; tocar expande) | Refino + a parte útil de Sugestões |
| 06 | **Sessões** | "O que aconteceu, e estou chegando lá?" | `Evolução` (índice sessão a sessão, apagões/hora) · `Lista` (duração, apagões, índice início → fim; tocar expande: fases, recibos; Exportar ZIP) | a lista de sessões que hoje vive em Ferramentas |
| 07 | **Ferramentas** | sistema | — (`<details>` como hoje: retenção, log, autoteste; balão e sua autorização; GPS) | Ferramentas, sem exportar/importar aprendizado |

**Sai do trilho:** **Sugestões**. Motivo de produto, não de gosto: a fila que a alimenta nunca é chamada (ver §4), então a aba mostra restos de sessões antigas; e o que ela faz de útil (dizer qual ajuste está pronto e levar à tela certa) é exatamente a "próxima ação" do Agora e a subpágina `Pontos` do Refino.

**Regras de coerência entre abas (é o que torna tudo "a mesma lógica"):**
- toda subpágina é uma **tablist na mesma posição** (abaixo do cabeçalho), com no máximo 3 itens, e a última visitada é lembrada por aba (`CR §38`);
- toda mutação na ECU abre o **mesmo overlay**: etapa → resultado humano → `Desfazer` / `Voltar`;
- o nível técnico se chama **"Detalhes técnicos"** em toda tela e é sempre o último bloco;
- cada aba abre com **uma linha didática** dizendo o que responde (a pergunta da coluna 3);
- "Agora" nunca executa nada: só mostra e leva, já posicionado (ex.: abre `Curva K › Equivalência` com a proposta em fantasma e o botão **Aplicar** pronto).

**Bugs de navegação da Platina que saem junto:** "Voltar ao aprendizado" chama a rota `learning`, que não existe (`map.js:57`); `routeMeta.predictor` sem rota; `#suggestionsButton`/`#toolsButton`/`renderSuggestions` em `drawers.js` apontam para elementos inexistentes; o cabeçalho do Agora deveria sumir por `data-omegas-route`, que ninguém seta.
### 3.2 Regras de composição

- **Entender em 2 s** (`OD §5`): ação primária > estado global > contexto > secundário > técnico.
- **Alvo de toque ≥ 76 px** (Android Automotive; única exceção: as 144 células da grade do Mapa K, ≥ 44 px), **texto crítico ≥ 24 px**, legível a ~70 cm. Verificado por teste de DOM no render do CI.
- **Cards só com unidade semântica** (`CR §23`): ponto, operação, sessão, faixa do AutoCal. Nunca por número ou label.
- **Cor é estado** (`CR §25`): verde equivalente, âmbar pobre/rico leve e `CONTESTADO`, vermelho desvio grande ou falha, oco sem dado, accent só para ação primária e AGORA.
- **Tokens** (`CR §24`): `--bg 8,12,18 · --surface 16,23,33 · --surface-2 22,31,44 · --stroke 41,55,73 · --text 245,248,252 · --text-2 150,166,187 · --accent 116,92,255 · --ok 56,211,159 · --warn 247,185,85 · --danger 255,107,107`. Um arquivo `tokens.css`; nenhum CSS define cor fora dele.
- **Normalidade compacta, problema ganha espaço** (`CR §34`).
- **Contexto preservado** ao trocar de aba (`CR §38`): seleção, scroll, linha expandida, operação em andamento.
- **Estados obrigatórios por superfície** (`CR §35`): sem cabo · conectando · ECU sem aquisição · **sem Referência (provisório)** · **ECU reaprendeu desde o congelamento** · aprendendo · normal · executando · falha transporte · falha ECU · vazio · dados longos. Cada um renderizado no CI.
- **Toda ação técnica tem nome humano** (`OD §9`): `MUL_ACT write` → "Aplicar ajuste"; `Reset gas 0x02` → "Zerar gás"; readback → "Conferindo na ECU".

### 3.3 Performance e leveza

- Render só por revisão; diff por seção do snapshot; nenhum `setInterval` de UI.
- Curva desenhada em `<canvas>` com 30 pontos + AGORA; redesenho parcial do AGORA a cada quadro, curva inteira só em revisão de pontos.
- Zero I/O na thread principal; todo motor em executor próprio (já é assim; mantido).
- Sem framework novo (`CR §42`). WebView + JS vanilla + Kotlin, como hoje.
- Orçamento: primeira pintura < 300 ms após `snapshot()`; resposta a toque < 100 ms; sem jank em sessão de 4 h (teste de SIGKILL + retomada existente mantido).

---

## 4. Poda: quem consome quem, e a ordem segura

Mapa feito sobre a `OmegasPlatina` antes de qualquer remoção. Regra: **extrair primeiro, apagar depois; cada passo compila no CI.**

### 4.1 Fica (núcleo provado, intocado)

`UsbSerialManager` · `ResponseDrivenEcuEngine` · `AutoCalProtocol` · `KFactorManager` · `KWriteManager` · `AutoCalNativeActionManager` · `NativeAutoCalMonitor` e trackers (maturidade, época, contador) · `EquivalenceLedger` · `EcuPetrolReference` · `AutoMatchRefinedEngine` · `AutoMatchV5Engine`/`AutoMatchSnapshotAnalysis` · `StallWatch` · `RefinementJournal` · `SessionRecorder` + ZIP único + `RESUMO.md` · `TelemetryOverlayController` · `Mp48SerialScheduler`.

Ficam também, por dependência do motor da ECU: `LearningToleranceSettings`, `LearningControlModel`, `AdaptiveSampleWindow`, `LearningTemperatureSettings`, `NativeAnchorTelemetryWindow`, `NativeAutoCalAnchorCorrelator`, `NativeLearningAnchor`, `LearningEvidenceModels` (mudam de pacote para `ecu/`).

### 4.2 Extrações obrigatórias antes de apagar

| O que | De onde | Para onde | Por quê |
|---|---|---|---|
| Escrita e operações da Curva K e do Mapa K (`startCurveRead/Backup/ListBackups/RestorePrepare/Reset/BatchWrite`, `startMapBatchWrite`, `previewMapAdjustment`, `getLastOperation`) | `V7JavascriptBridge` | fila única (§2) | `curve.js`, `map.js` e `refino.js` gravam por `OmegasV7`; `OmegasNative` não tem backup/restore/poller |
| Célula ao vivo (`liveInterpolationJson`, `cellFor`) | `LearningGridProjection` | `calibration/` sobre `KMapPhysicalAxes` | roda no caminho quente de cada quadro; alimenta AGORA e balão |
| Eixos `rpmBins`/`petrolBins` | `LearningGridProjection` | `KMapPhysicalAxes` | `ContinuousLearningMath` depende |
| `MotorSampleAnalyzer` (só confirmação de combustível + validade de amostra, `SampleDecision`) | `learning/` | `ecu/` | está na assinatura `onTelemetry` e o `SessionRecorder` grava `sample_state`/`sample{}` em todo evento; formato de sessão não muda |
| `RefinementAutopilot` → renomeado `EquivalencePhases` | `autocal/` | `autocal/` | é a máquina de fases (Refino, selo, notificação, RESUMO); não grava nada; o nome mente |
| Leitura por reflexão de `v7Bridge`/`lastOperation` | `androidTest/DashboardLevelsRenderTest.kt` | ler pela ponte única | quebraria só no emulador |

### 4.3 Sai (apagado, não escondido)

| Grupo | Arquivos | Consumidores a desligar antes |
|---|---|---|
| **Predictor** | `PredictorInterpolator/Surface/SpatialConfidence.kt`, `screens/predictor.js`, `core/predictor-model.js`, `components/predictor-current-cell.js`, `styles-predictor*.css` | `V7CalibrationAccess` (:42–92), `app.js` (:296–298) |
| **AutoMatch manual** | enum `MANUAL_AUTOMATCH` em `AutoCalNativeActionManager`, allow-lists em `AutoCalJavascriptBridge`, `AutoCalProtocol:25`, botão em `autocal-cockpit.js`, fixtures `platinum-autocal-action-parity-v1.json` e `progbase-autocal-action-map-v1.json` | 4 testes Python de paridade passam a listar só ações expostas; `tools/omegas/extract_lognovo_autocal_epochs.py` mantém a string para logs antigos |
| **Cérebro 3 (V7)** | `com/omegas/v7/**`, `V7JavascriptBridge`, `V7CalibrationAccess`, `V7CalibrationCoordinator`, `AdvisorSuggestionAdapterV7`, `ExistingCalibrationWriterV7`, `CalibrationWriterReadBackV7` | `MainActivity` (:254–311), `HubJavascriptBridge` (:18, :122–128, :153), leitores de `calibrationState.suggestionItems` em `app.js`, `curve.js`, `curve-prediction-state.js`, `drawers.js` |
| **Cérebro 2 (learning)** | `MotorLearningMemory`, `SignalLearningStore`, `LiveOnlyLearningStore`, `DeferredLiveOnlyLearningStore`, `AdaptivePetrolReference`, `PetrolReferenceSelector`, `AssistedCalibrationAdvisor`, `LearningGridProjection`, `LearningSnapshotReconciler`, `LearningUiSnapshotAssembler`, `LearningArchiveManager`, `AdvisorRevisionGate`, `CoalescedSnapshotWriter`, `ContinuousWindowNovelty`, `LearningEvidenceBudget`, `LearningMemoryBudget`, `SciencePublicationGate`, `InternalLearningNamespace`, `LearningEvidenceDimensions` | `NativeRuntimeManager` (:38, :99–116, :188–299, :380), `TelemetryForegroundService` (:200–264, :342–369, :439–560), `HubJavascriptBridge` (`getLearningMaps` e :91/:101/:354/:364), `MainActivity` (:95–110, :359), `AutoCalJavascriptBridge:384`, `OmegasLinkManager` (sync de aprendizado :34–35, :348–420) |
| **Órfãos Kotlin** | `AebProtocolFramesV7`, `ArchitectureContracts`, `AutoMatchDraftReviewValidator`, `AutoMatchKFactorDraft`, `AutoMatchResidualPlanner`, `CurveKComparison`, `NativeAutoCalEvidenceSummary`, `OmegasMigrationService`, `OrderedBackgroundPipeline`, `StartupLifecyclePolicy`, `TelemetryVisualLifecyclePolicy`, `WeightedStat`, `CalibrationCausalTransitionV7` (classe; tipos migram) | nenhum |
| **Órfãos JS/CSS** | nunca carregados: `components/physical-grid.js`, `components/floating-telemetry.js`, `portmon-*.js`, `styles-expansion*.css`, `styles-floating-telemetry.css`. Carregados pelo `index.html` mas sem consumidor vivo (confirmar por grep de símbolos no passo): `core/learning-model.js`, `suggestion-model.js`, `map-editor.js`, `styles-calibration-obd.css` (OBD já saiu do produto) | o passo faz grep de cada símbolo exportado antes de apagar |
| **Telas reescritas** | `screens/dashboard.js`, `screens/refino.js`, `screens/curve.js`, `screens/map.js`, `screens/autocal-cockpit.js` e seus CSS | mantêm rota e padrões; conteúdo refeito conforme §3.1 |

### 4.4 Dados em aparelhos instalados

- **`applicationId` continua `com.omegas.v7.test`.** Mudar apaga todo dado instalado.
- Arquivos órfãos sem leitor (`native_learning_state_*`, `learning_*`, `v7_sessions/`, `runtimeBackups/learning_checkpoints/`): limpeza única na primeira abertura, com linha no log da sessão. `refinement_autopilot.json` **fica**: é o arquivo de `EquivalencePhases`. `SharedPreferences` `omegas_learning_native` e `omegas_learning_v5` **ficam** (usados pelas tolerâncias).
- Recibos antigos com `MANUAL_AUTOMATCH` são lidos como string; `Action.valueOf` só roda em entrada nova. Sem crash.
- Interop LAN com APK antigo (`OmegasLinkManager` sync de aprendizado) deixa de existir; versão de protocolo sobe e o antigo é recusado com mensagem.

### 4.5 Ordem de remoção (cada passo = 1 PR verde)

0. Extrações (§4.2).
1. Predictor.
2. AutoMatch manual + fixtures.
3. V7 inteiro + leitores de `suggestionItems`.
4. Learning + sync LAN + `LearningArchiveManager`.
5. Órfãos Kotlin/JS/CSS + tela Sugestões (junto com a UI nova, §7 fatia 6).

### 4.6 Testes

**Morrem com o código:** `app/src/test/.../v7/**`, `learning/*` (exceto `ContinuousLearningMathTest`), `Predictor*Test`, `E2ELearningFlowTest`, `LearningLatencyContractTest`, `RefinementAutopilotTest` (vira `EquivalencePhasesTest`), `AdvisorSuggestionAdapterV7*`, `CalibrationWriterReadBackV7Test`; UI `predictor-*.test.cjs`; Python que importam fonte apagado: `test_block1_session_contract`, `test_final_pre_apk_product_contract`, `test_final_storage_and_curve_reset_contract`, `test_map_kotlin_math_authority_contract`, `test_v7_map_batch_contract`, `test_platinum_autocal_action_parity` (reescrito), `test_platina_final_mission`, `test_causal_step_wiring`, `test_multimedia_telemetry_backpressure`, `test_native_autocal_contract` (reescrito), `test_checkpoint_hot_path`, `test_verde_scientific_runtime`, `test_startup_learning_restore`, `test_learning_*_budget`, `test_advisor_revision_budget`, `test_predictor_map_residual`, `test_red_*`, `test_native_anchor_science`, `test_learning_consolidation`, `test_block3_suggestion_ui`, `test_suggestion_readback_lifecycle`, `test_curve_kotlin_math_authority`, `test_v82_integral_regression`.

**Editados:** `EcuReferenceCycleTest`, `EcuReferenceScenarioTest`, `RefinementCycleScenarioTest`, `RefinementRealSessionTest`, `EquivalenceViewPerformanceTest`, `AutoCalNativeActionManagerTest`, `AutoCalProtocolTest`, `test_mp48_serial_scheduler_behavior`, UI `app-shell-runtime`, `didactic-expansion`, `refino-screen`, `live-tracing-contract`, `split-layout`, `autocal-cockpit`, `autocal-reacquisition-ux`, `RefinoRenderTest`, `DashboardLevelsRenderTest`, `test_governance_contract` (reescrito do zero: passa a exigir a ponte única e os invariantes de §0.2).

**Novos (gates da estrutura nova):**
- índice sobe nas sessões reais depois de ajuste (replay);
- paridade Kotlin ↔ Python do índice e das fases;
- fila: uma mutação por vez; cabo caído → `✗` sem crash; Desfazer = foto byte a byte;
- ponte: nenhum método sem chamador; nenhum intent sem handler;
- render 1280×720 de cada superfície em cada estado obrigatório;
- DOM: alvo ≥ 76 px, texto crítico ≥ 24 px;
- APK novo sobre dados antigos abre sem crash e limpa os órfãos;
- SIGKILL no meio da sessão → um ZIP, sem perda (existente, mantido).

**Workflows:** `verde-autocal-host-parity.yml` e `verde-android-render-evidence.yml` nomeiam testes; atualizados na fatia correspondente. `ci.yml` e `tools/run_checks.py` descobrem por glob.

---

## 5. Documentos: reescritos do zero, curtos

Na Fatia 0:

- **`AGENTS.md`** (uma tela): a meta (§0), as 10 regras invariantes (§0.2), as 7 abas, onde está a prova, link para esta spec e para os dois métodos do Notion. Nada de "autoridade GitHub remoto", "mutação local negada", workflows legados Verde, tabela de readiness.
- **`PROJECT.md`**: 10 linhas: repo, branch, plataforma, meta, link.
- **`STATUS.md`**: só o último APK (SHA, run, classe de prova, não provado). Histórico vai para `docs/archive/STATUS-ate-2026-10-03.md`.
- **`docs/ARCHITECTURE.md`**: §2 e §4 desta spec em forma de diagrama + tabela "quem escreve no `AppState`".
- **Arquivados em `docs/archive/`** (um commit, sem edição): `LEARNING_RULES.md`, `docs/BLOCK_*.md`, `docs/decisions/`, `docs/spec-kits/`, `docs/workunits/`, `docs/handoff/`, `docs/incidents/`, specs anteriores de `docs/superpowers/specs/`, `docs/MODELO_EQUIVALENCIA_CAUSAL.md`, `docs/OBD_*.md`, `docs/PENTE_FINO_VERDE.md`, `docs/MIGRATION.md`.
- **Ficam vivos:** `docs/TEST_STRATEGY.md` (reescrito para §4.6), `docs/V82_PHYSICAL_VALIDATION_PROTOCOL.md` (é o único jeito de provar classe 5), `docs/autocal/`, `docs/reference/`, `docs/evidence/`, `docs/research/`.

---

## 6. O que nenhum CI prova

Sensação no carro · balão sobre outros apps · cabo USB real · comportamento da ECU física. Só o dono, no carro. Nenhum PR, STATUS ou mensagem chama algo de "validado" sem isso. O protocolo é `docs/V82_PHYSICAL_VALIDATION_PROTOCOL.md`, atualizado para as 3 telas.

---

## 7. Entrega em fatias (o que o Claude Code executa)

**Regras de execução:**
- base `OmegasPlatina`; uma branch `work/platina-f<N>-<nome>` por fatia; um PR por fatia; merge só com CI verde no SHA exato.
- nenhum teste local; o executor edita, empurra e lê o resultado do GitHub Actions. Logs de falha são lidos pelo `gh run view --log-failed`, nunca reproduzidos localmente.
- cada PR diz: o que mudou · classe de prova (1 contrato de texto · 2 sintético · 3 replay real · 4 APK no emulador · 5 físico) · o que ficou **não provado**.
- toda fatia nasce com os testes que a provam escritos antes do código (TDD).
- APK com SHA-256 gerado pelo `verde-apk-now.yml` ao fim das fatias 3, 5 e 8.

| # | Fatia | Entrega | Prova no CI |
|---|---|---|---|
| 0 | **Docs do zero** | §5 | `test_governance_contract` reescrito; lint |
| 1 | **Extrações** | §4.2 | tudo que existe continua verde; render do Refino inalterado |
| 2 | **Poda 1** | Predictor, AutoMatch manual, V7, leitores de `suggestionItems` | compila; fixtures atualizados; render verde |
| 3 | **Poda 2** | learning, sync LAN, `LearningArchiveManager`, órfãos Kotlin | compila; "nenhum método sem botão"; APK sobre dados antigos abre; **APK** |
| 4 | **Cérebro único** | `Reference` congelada (§1.6), Curvas Próprias por célula de 0,02 bar (§1.6b), `EquivalencePoint`, `EquivalenceEngine`, índice, próxima ação, `EquivalencePhases` com estados de §1.2 | índice sobe nas sessões reais; Curva Própria madura prevê faixa escondida melhor que a Referência (validação cruzada); trocar Referência não perde dados (replay); paridade Python |
| 5 | **Autoridade única** | `AppState`, `StateStore`, ponte `Omegas`, fila, foto, Desfazer, recibos | fila serializa; `✗` sem crash; Desfazer byte a byte; ponte sem órfão; **APK** |
| 6 | **UI Agora + Curva K + Refino** | abas 01, 03, 05 de §3.1 com subpáginas; Sugestões removida | render de cada estado; DOM ≥ 76/24 px |
| 7 | **UI AutoCal + Mapa K + Sessões + Ferramentas** | abas 02, 04, 06, 07 de §3.1; faixa de status e estados sem cabo/conectando | idem |
| 8 | **Acabamento** | `tokens.css` único, balão, limpeza de CSS/JS, protocolo físico atualizado | render; **APK final** |

Cada fatia ganha um plano próprio em `docs/superpowers/plans/`, com tarefas de 1–3 arquivos, e cada tarefa com o teste antes.

---

## 8. Critério de "fechado"

A estrutura está fechada quando, no SHA final:

1. existe **uma** Referência congelada, **um** cérebro, **uma** ponte, **uma** fila, **um** arquivo de tokens;
2. toda ação da UI percorre `RECEBIDO → … → ✓/✗` com readback, e tem Desfazer quando muda a ECU;
3. nenhum método, arquivo JS, CSS ou classe sem consumidor (teste);
4. o índice "% equivalente" é calculado, mostrado, guardado por sessão e sobe nas sessões reais pós-ajuste;
5. as 7 abas, suas subpáginas, a faixa de status e os estados sem cabo/conectando renderizam todos os estados obrigatórios em 1280×720;
6. o `AGENTS.md` cabe numa tela e não contradiz nada do que o código faz;
7. o dono instalou o APK no carro e o `STATUS.md` diz o que ele viu, inclusive o que não gostou.
