# Obsidian — plano de replay do residual local por RPM

> Execução guiada por evidência. O refino atual é o incumbente e sua matemática permanece congelada durante a fase de pesquisa.

**Branch:** `work/platina-obsidian-enxuto`  
**Base inicial:** `f7627f640a16c918d0ff4bbf4012c28109b7dfb7`  
**Escopo:** ferramentas offline, testes e evidência; nenhuma alteração de writer, protocolo, UI ou APK.

## Objetivo

Medir o residual que permanece depois da curva nativa `MAP × ms` e do refino atual da Obsidian, determinar se esse residual é localmente dependente de RPM e comparar o classificador atual com desafiantes simples e River em shadow.

O objetivo não é reconstruir a curva-base. A curva da ECU e `AutoMatchRefinedEngine` são o baseline. O ganho novo só pode vir do termo local que essa curva bidimensional não explica.

## Estado atual que deve ser preservado

- `AutoMatchRefinedEngine` monta alvos de equivalência das curvas nativas, aplica ajuste robusto e Whittaker em `ln K`, limita passo e elasticidade e mede roughness.
- `EquivalenceLedger` forma observações estáveis de três frames, congela gasolina por região `RPM × MAP`, invalida GNV quando a calibração muda e casa gasolina/GNV.
- `EquivalenceAttribution` separa grupos por coluna física de RPM do Mapa K e faixa de Tpet; exige pares, visitas independentes, comparador em outras colunas, estabilidade e histerese.
- `LocalMapPlanner` apenas produz candidato manual e nunca escreve na ECU.
- O histórico declara essa matemática congelada. Esta missão começa por replay e falsificação, sem alterá-la.

## Invariantes

1. A referência continua sendo a curva geométrica `MAP × ms` adquirida pela ECU.
2. RPM é explicador do residual, não novo eixo da curva-base.
3. Mudança de Curva K, Mapa K, reset ou AutoMatch abre/invalida a época aplicável.
4. Partes do mesmo arquivo/sessão física nunca são separadas entre treino e teste como sessões independentes.
5. River nunca emite ajuste para a ECU.
6. Nenhuma melhoria de consumo ou dirigibilidade será declarada por replay.
7. Sem evidência independente resulta em `UNKNOWN`.

## Task 1 — Congelar corpus e proveniência

**Arquivos:**

- criar `tools/autocal_refine/rpm_residual_replay.py`;
- criar `tests/test_rpm_residual_replay.py`;
- gerar `docs/research/obsidian/rpm-residual/corpus-manifest.json`;
- gerar `docs/research/obsidian/rpm-residual/manifest.sha256`.

### RED

Testar que o inventário:

- agrupa partes pelo `sessionId` físico;
- detecta conteúdo duplicado por SHA-256;
- registra arquivos malformados, eventos descartados e quebras;
- não aceita sessão sem identidade;
- preserva origem, tamanho, hash e classificação sem copiar o corpus bruto para o GitHub.

### GREEN mínimo

Implementar leitura streaming dos JSONL autorizados, normalização de envelopes e manifesto determinístico ordenado.

### Verificação

Executar o teste focado e regenerar o manifesto duas vezes; hashes precisam coincidir.

## Task 2 — Reconstruir épocas e observações causais

**Arquivos:** os mesmos script e teste.

### RED

Fixtures pequenas devem provar que:

- snapshot de curva só vale a partir de seu timestamp;
- write/readback/reset/AutoMatch quebra a época correta;
- três frames estáveis viram uma visita observacional;
- troca de combustível e transiente limpam a janela;
- frames futuros nunca formam a referência de um ponto passado;
- dados parciais continuam utilizáveis somente nos campos/intervalos válidos.

### GREEN mínimo

Reproduzir as regras observacionais de `EquivalenceLedger` sem importar Android/Kotlin. Emitir uma tabela de visitas com sessão, época, combustível, timestamp, RPM, MAP, Tpet, curva causal e flags de qualidade.

## Task 3 — Calcular o residual pós-base

### RED

Provar em fixture que:

```text
r = ln(Tpet_GNV / Tpet_gasolina_comparável)
```

é calculado somente quando existe referência de gasolina anterior e comparável na mesma vizinhança `RPM × MAP` e na época compatível. Sem referência, resultado é `UNKNOWN`, nunca zero.

Separar explicitamente:

- residual global mediano da época;
- residual local do grupo menos outras RPMs em Tpet comparável;
- ausência de separabilidade.

### GREEN mínimo

Produzir `visits.csv` e `residual-groups.csv` sem dados sintéticos apresentados como evidência.

## Task 4 — Reexecutar o incumbente exatamente

### RED

Portar vetores de paridade de `EquivalenceAttributionTest` e do oráculo existente. O Python deve reproduzir chaves, motivos, histerese, contagem de visitas e residual do Kotlin.

### GREEN mínimo

Usar as mesmas constantes e regras do incumbente:

- colunas físicas `KMapPhysicalAxes.rpmBins()`;
- faixas de Tpet existentes;
- mínimo de pares/visitas;
- limiares de entrada/saída;
- exclusão de grupos locais da Curva K global.

Registrar divergência como falha; não “corrigir” o replay até entender a causa.

## Task 5 — Comparar desafiantes

**Desafiantes:**

- `ZERO_LOCAL`: nenhum refino local;
- `INCUMBENT`: classificador atual;
- `CONTINUOUS_RPM`: regressão robusta contínua do residual por RPM dentro da vizinhança de Tpet;
- `ADAPTIVE_REGIME`: mudança de regime inferida dos dados, sem RPM fixa universal;
- `RIVER_ARF_SHADOW`: predição online normalizada, com limites numéricos.

### Validação

- predição antes de aprendizado;
- holdout por sessão física completa para alegações gerais;
- replay cronológico dentro da sessão para adaptação online;
- métricas por sessão e mediana entre sessões, nunca somente por frame;
- bootstrap por sessão e Wilcoxon pareado quando houver amostra suficiente;
- ablação de MAP, RPM, Tpet e época;
- relatório separado para lenta, cruzeiro e transientes, sem impor que esses rótulos sejam universais.

### Critério de promoção

Um desafiante só pode seguir para protótipo Kotlin se reduzir o erro residual fora da amostra, vencer em sessões independentes, permanecer numericamente estável e não classificar incerteza como ajuste.

## Task 6 — Gates de não regressão do refino atual

Congelar para cada curva candidata:

- `maxNeighborStep`;
- `maxElasticity`;
- `roughness`;
- mudanças de sinal da inclinação;
- erro de evidência antes/depois;
- regiões HELD preservadas.

O novo termo local não pode alterar a Curva K durante esta missão. O relatório deve simular separadamente seu efeito esperado e marcar a semântica do Mapa K como não comprovada quando faltar identificação.

Consumo, trancos e dirigibilidade entram como critérios de teste físico futuro. O replay pode medir proxies de linearidade, nunca declarar melhora física.

## Task 7 — Evidência versionada

**Saídas:**

- `docs/research/obsidian/rpm-residual/REPORT.md`;
- `model-comparison.csv`;
- `session-results.csv`;
- `residual-groups.csv`;
- gráficos PNG/SVG reproduzíveis;
- `run-receipt.json` com versões, comando, hashes e HEAD;
- logs de testes.

O relatório deve responder:

1. quais aparentes segundas curvas são apenas estrutura da referência;
2. em quais regiões sobra residual por RPM depois do refino;
3. se o classificador atual separa corretamente esses casos;
4. qual desafiante, se algum, supera o incumbente;
5. quais grupos continuam `UNKNOWN`;
6. qual o menor teste no carro capaz de validar o efeito físico.

## Task 8 — Decisão sobre Kotlin

Somente se Task 5 superar os gates:

1. escrever teste Kotlin RED para uma função pura shadow;
2. implementar o menor algoritmo determinístico destilado;
3. não conectá-lo a writer nem mudar constantes congeladas do incumbente;
4. executar testes focados, paridade, suíte proporcional e CI remota no mesmo SHA;
5. manter `PHYSICAL_VALIDATION_CLAIMED=false`.

Se nenhum desafiante vencer, a conclusão correta é preservar o algoritmo atual e versionar o que foi falsificado.
