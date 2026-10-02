# Prompt de execução — OMEGAS WU-006, versão 2 (consolidada)

Esta versão **substitui** a v1 e todos os complementos mandados por chat. Se algo neste arquivo conflitar com outra mensagem, vale este arquivo.

---

Você é o executor do OMEGAS V8.2. É um app Android para a ECU MP48 / Omega Platinum, carros a GNV, que roda na multimídia 1280×720 do carro.

- Repositório: `viluadmcontas2-dot/OMEGAS-V8.2`.
- Branch única: `claude/brave-darwin-wuliyo`.
- Parta do HEAD atual. Ele já inclui o seu commit `0da6454a` (estados humanos + `docs/product/UX-BLUEPRINT-CHECKLIST.md`). Preserve esse trabalho e construa em cima dele.
- Não crie branch, não abra PR e não apague branch remota.

## 0. Antes de tocar em código

1. Leia, nesta ordem:
   - `AGENTS.md`
   - `PROJECT.md`
   - `STATUS.md`
   - `docs/workunits/OMEGAS-WU-006.md`
   - `docs/evidence/WU-006-refined-replay.md`
   - `docs/reference/progbase/README.md`
   - `docs/reference/progbase/FORMULAS.md`
   - `docs/reference/progbase/ACHADOS-DIRIGIDOS-20261002.md`
   - `docs/reference/progbase/LACUNAS.md`
   - `docs/reference/progbase/parametros.json`
2. No Notion, leia por inteiro:
   - "Blueprint Premium UI/UX — Método CUSTOMROM reutilizável";
   - "Método aplicado — Omega Dev 4.0 Premium UI/UX";
   - "18 — Blueprint Car Info Next — Método OMEGAS + CUSTOMROM".

   O Car Info Next é o contrato 1280×720. Confira o seu `UX-BLUEPRINT-CHECKLIST.md` contra os três e complete o que faltar.
3. Leia o código que vai tocar **antes** de propor mudança:
   - `app/src/main/assets/ui/core/router.js`, `app.js`, `index.html`, `core/autocal-api.js`;
   - `screens/autocal-cockpit.js`, `screens/autocal-refine.js`, `screens/dashboard.js`;
   - `autocal/AutoCalJavascriptBridge.kt`, `autocal/EquivalenceLedger.kt`, `autocal/RefinementJournal.kt`, `autocal/RefinementAutopilot.kt`;
   - `service/TelemetryForegroundService.kt`, `service/NotificationController.kt`.

## 1. Regras invioláveis

Se você não conseguir cumprir uma delas, **pare e reporte**. Não contorne.

1. **ECU:** nunca gravar automaticamente. A escrita é sempre preparar → revisar → confirmar → ACK → readback. Falha ou divergência nunca vira sucesso na UI.
2. **Matemática congelada.** Não altere constantes nem fórmulas de:
   - `AutoMatchRefinedEngine`, `EquivalenceLedger`, `RefinementJournal`, `RefinementAutopilot`;
   - `tools/autocal_refine/*`.

   Os valores vieram de validação cruzada e teste cego em sessões reais (`LAMBDA=0.3`, `E_MAX=0.35`, `TELEMETRY_WEIGHT=0.4`, `MIN_BAND_SAMPLES=8`, `NOISE_LOG=0.02`, tolerância ±3%). Exceção: bug provado por teste que falha antes e passa depois. Funções **novas** de apresentação ou consulta podem ser criadas, puras e com teste.
3. **Nada inventado.** Sem pontos simulados, porcentagem de progresso fictícia, litros sem calibração ou "último valor" mostrado como atual. Sem dado, a tela diz **por que** não há dado e o que fazer.
4. **Sem alegação física.** Mantenha `PHYSICAL_VALIDATION_CLAIMED=false`.
5. **Custo zero.** CI só no fechamento: um disparo de `omegas-preapk-build.yml`, mais os disparos de correção que forem necessários. Commit só de documentação não dispara CI.
6. **Sem binários proprietários** do ProgBase no repositório.
7. **Testes nunca são pulados**, desabilitados ou colocados em quarentena para ficar verde.

## 2. O que existe e o que aprendemos (base da inteligência)

### 2.1 Evidências reais

São de 75 sessões do dono e da sessão de referência 2026-10-01 17:19.

**AutoMatch nativo da ECU:**
- É uma razão horizontal de ganho total, limitada a [0,75; 1,20]. A réplica erra ≤10 LSB.
- Ele cria **dente de serra**, picos e topo chapado. Também reage a bandas outlier: a banda 9 com razão 0,824, contra vizinhas de ~1,05.

**Tranco no GNV:**
- O tranco é **ciclo-limite**. Onde a Curva K é íngreme (|d lnK/d ln t| > ~0,35), a ECU oscila 8↔9 ms a cada quadro.
- O fator observado é 1,18, igual ao degrau da curva.

**Leitura estável (`EquivalenceLedger`):**
- O zigue-zague 8↔9 ms existe até na gasolina.
- Por isso a leitura estável é a média de 3 quadros: ≤1,2 s, RPM ±150, MAP ±0,03 bar.

**Regimes e tempo de gás:**
- **Marcha lenta (<1000 rpm) é outro regime.** Fica fora do índice de condução.
- O tempo de gás segue `gás ≈ gasolina × K × MapaK/100 + 0,99 ms`. O 0,99 ms é tempo morto, e o Mapa K é um multiplicador percentual.
- O erro de equivalência depende da **carga, não do RPM**. Por isso o refino é na **Curva K**. Mapa K automático só entra se a medição provar dependência de RPM (fora do escopo agora).

**Pontos e eixo de comparação:**
- Pares RPM×MAP próprios do app (gasolina × GNV no mesmo ponto) melhoram o erro cego: 3,29% → 2,91% e 5,68% → 5,45%.
- 36 bandas só por MAP não ganham nada. **RPM×MAP é o eixo certo.**

**Refino e consumo:**
- Teste cego da curva refinada contra o que a condução em gasolina pede: 7,7% → 5,7% e 8,4% → 3,3%.
- "Gás por ar" = Σ(gás − 0,99)·rpm / Σ MAP·rpm. É a métrica de consumo que independe do trânsito.

### 2.2 O ciclo fechado já implementado

Está em Kotlin, com 690 testes JVM verdes, paridade Kotlin↔Python e o gate rápido PASS.

1. **ECU no automático.** O `RefinementAutopilot` só observa. O `EquivalenceLedger` junta os nossos pontos.
2. **A ECU parou.** Uma destas condições ativa o `ecuDoneReason`:
   - `MAX_AUTOMATCH` atingido;
   - AutoCal desligado;
   - 18/18 + 10 min sem automático novo;
   - ≥12/18 + 25 min.

   A partir daí vale uma destas fases:
   - `COLETANDO_NOSSOS`;
   - `PROPOSTA_PRONTA`: alguma faixa ≥8 amostras fora de ±3%;
   - `VERIFICANDO`;
   - `RESTAURAR_TRECHO`;
   - `ESTAVEL`: ≥3 faixas medidas, todas dentro de ±3%.
3. **Verificação.** O `RefinementJournal` dá o veredito por faixa: CONFIRMADA, PASSOU, CURTA ou PIOROU.
   - O ganho aprendido por faixa é ×0,7 / ×0,5 / ×1,15 / volta a 1. Ele alimenta a próxima proposta.
   - Há duas saídas: `restorePoints` (só o trecho que piorou) e "desfazer" a partir de `latest.beforeRaw` / `latest.afterRaw`.
4. **Notificação:** uma por fase, para `PROPOSTA_PRONTA`, `RESTAURAR_TRECHO` e `ESTAVEL`.
5. **Sessões gravadas:** a fase aparece no evento `refinement_phase`.

**Os tempos 10/25 min do piloto são HIPÓTESE.** Ainda não foram confirmados no carro. Não os trate como fato na UI nem nos docs.

### 2.3 Contratos de dados

**Use exatamente estes nomes.** Erro de nome de campo é a falha mais provável.

`AutoCalApi.equivalence()` → `getEquivalence()` retorna:

```
{ ok, ratio|null, samples, petrolObservations, gasObservations, gasEpochReason, gasEpochAt,
  drivingMinRpm, gasPerAir|null,
  bands: [{fromMs, toMs, samples, ratio|null}] (5 faixas: 3–4,5 · 4,5–6 · 6–7,5 · 7,5–9 · 9–12 ms),
  refinement: { bandScale[5], latest|null: { id, status, appliedAt, source, beforeRaw[30], afterRaw[30],
                indexBefore, indexAfter, bands:[{fromMs,toMs,samplesBefore,samplesAfter,ratioBefore,ratioAfter,verdict}] },
                history:[…], count },
  restorePoints: [{index, currentRaw, targetRaw}],
  autopilot: { phase, headline, next, canDisconnect, ecuOnline, autoMatchCount|null, maxAutomatch|null,
               autoCalEnabled|null, petrolValid, gasValid, quietMinutes, ecuDone, ecuDoneReason|null,
               ourPoints, bandsMeasured, bandsOff[], bandsMissing[], journalStatus? } }
```

`AutoCalApi.refinedAnalysis()` retorna os campos que `autocal-refine.js` já consome:
- `available`, `refinementMode` (`EQUIVALENCE` | `POLISH`), `changedCount`;
- `matureCommonPoints`, `minimumMatureCommonPoints`, `telemetryTargets`;
- `joltRiskBefore` / `joltRiskAfter` (`LOW` | `ATTENTION` | `HIGH`);
- `evidenceErrorBefore` / `evidenceErrorAfter`;
- `metricsBefore` / `metricsAfter`, `needsAnotherPass`, `guards`, `elasticityLimit`;
- `targets[]`, `rejectedBands[]`;
- `points[30]{index, referenceTimeMs, currentRaw, calculatedRaw, currentFactor, calculatedFactor, origin, deltaPercent}`.

## 3. Navegação

**Mantenha a navegação atual e acrescente um único destino.**

```
Agora · AutoCal · Refino (NOVO) · Aprender · Ajuste global · Ajuste local · Sugestões · Ferramentas
```

- "Refino" é o nosso refino. O "AutoCal" é o da ECU. São **separados**.
- O AutoCal **não está congelado**: ele melhora com as evidências do §5. O que se mantém é a estrutura de navegação.

**Onde você pode falhar ao criar o destino:**
- **Rota em todos os lugares.** A rota nova precisa estar em `ROUTES` (`core/router.js`), no item da side-nav em `index.html`, em `activateRoute` (`app.js`) e no carregamento opcional do script.
- **Rota salva antiga.** Uma rota salva que não existe mais deve cair em `dashboard`. Isso já é tratado: confira.
- **Painel sem desenhar.** Hoje o painel de refino é instanciado dentro de `autocal-cockpit.js` (`refineHost`). Mova-o para o host da tela Refino. Já houve o bug de o painel não desenhar ao entrar na rota, corrigido com `subscribeSelected(..., true)`, que dispara na hora. Replique isso.
- **Polling.** Nenhum timer por tela. Use o Store/Scheduler central. `getRefinedAnalysis` é memoizado, mas pesado: chame ao entrar na tela e quando o snapshot ou a evidência mudarem, nunca por quadro de telemetria. `getEquivalence` no máximo a cada 3 s, e só com a tela visível.
- **Contexto.** Trocar de destino não pode perder revisão aberta nem operação de escrita em andamento.

## 4. Tela "Refino" (o nosso)

Uma tela, **sem rolagem** em 1280×644. Siga os tokens, a tipografia (valor principal grande, nada essencial abaixo de 12sp) e os targets (56–68dp) do Car Info Next.

**Hierarquia:**
1. **Linha do tempo do piloto:** ECU no automático → Nossos pontos → Refino → Verificação → Estável.
   - A fase `RESTAURAR_TRECHO` aparece sobre "Verificação", em vermelho.
   - `SEM_ECU` é um estado vazio que explica o que fazer.
2. **Frase principal grande** (`autopilot.headline`) + **próximo passo** (`autopilot.next`).
3. **Uma ação primária**, conforme a fase:

   | Fase | Ação |
   |---|---|
   | `PROPOSTA_PRONTA` | "Revisar e gravar" (fluxo `openReview` → `apply` existente) |
   | `RESTAURAR_TRECHO` | "Restaurar trecho que piorou" (`restorePoints`) |
   | `ESTAVEL` | "✓ Estável · pode desconectar", sem botão |
   | Demais fases | Nenhum botão primário |

   Se a análise refinada tiver mudança mas o piloto ainda estiver em `ECU_TRABALHANDO`: permita revisar, com o aviso "a ECU ainda está no automático e pode sobrescrever". **Não bloqueie.**
4. **Gráfico "Nossa curva"**, ocupando a maior área livre. É **uma cópia do gráfico original do AutoCal da ECU, com mais pontos**:
   - **eixos iguais aos do original:** X = tempo de injeção gasolina, Tpet (ms); Y = MAP (bar);
   - **duas famílias:** Gasolina e GNV, cada uma com a sua linha;
   - **os 18 pontos da ECU por família,** como no original: válido cheio, coletando vazado, sem coordenada não plotado;
   - **as nossas bandas densas** entre eles, em tamanho menor (ver §4.1).

   A distância horizontal entre a linha GNV e a linha gasolina no mesmo MAP é o que o refino corrige. Mostre isso com uma faixa sombreada ou uma legenda curta: "afastamento GNV × gasolina".

   Ao tocar num ponto, mostre os valores dele sem mover o layout: MAP, ms, amostras, RPM típico, ECU ou nosso.

   O gráfico da **Curva K** (atual × refinada, com as origens Medido / Transição / Anti-tranco / Mantido) não some. Ele passa para o **painel de revisão antes de gravar**, que é onde se decide a mudança.
5. **Métricas, no máximo 3:**
   - Puxada no GNV: `joltRiskBefore` → `joltRiskAfter`, e onde fica o degrau;
   - Mudança: N pontos, até ±X%;
   - Evidência: "N faixas ECU + M nossos".
6. **Painéis fechados:**
   - "Resultado da última gravação": vereditos por faixa + Desfazer;
   - "Detalhes técnicos": o que já existe hoje.

A revisão antes de gravar é um painel por cima, com botões grandes. Mantenha **intacto** o `runWrite`: reler a curva, comparar com o snapshot (se algo mudou, aborta), gravar e confirmar por readback.

### 4.1 Dados da nossa curva: bandas densas (criar)

A ECU tem 18 bandas de MAP por combustível. Nós criamos as nossas, mais densas, com as leituras estáveis que o `EquivalenceLedger` já guarda.

- **Função:** em `EquivalenceLedger`, crie `denseBandsJson(binBar: Double = 0.025, minSamples: Int = 5): JSONObject`. Só leitura.
- **Agrupamento:** por combustível (lane gasolina e lane GNV; o GNV só da época vigente da curva), agrupe as observações em faixas de MAP de 0,025 bar, o que dá ~40–50 bandas na faixa útil.
- **Saída por faixa:** `{mapBar (centro), tpetMs (mediana), samples, rpmMedian, idleShare}`. Só entram faixas com ≥ `minSamples`.
- **Bridge:** exponha em `getEquivalence` como `denseBands: {petrol:[…], gas:[…], binBar, minSamples}`. Teste JVM com dados sintéticos: mediana correta, faixa com poucas amostras omitida, GNV zerado após `resetGas`.

**Honestidade sobre o que essas bandas são:**
- **Na tela:** elas são para **ver** com mais detalhe onde GNV e gasolina se afastam.
- **No cálculo:** o motor de refino **continua** usando os pares RPM×MAP (`telemetryTargets`), que no teste cego ganharam das bandas só por MAP (2,94%/5,41% contra 3,31%/5,55%).
- **Não troque** a entrada do motor pelas bandas densas.
- **Tooltip** do gráfico: "nossas bandas: mediana das leituras estáveis a cada 0,025 bar".

**Sem dados:** sem leituras, mostre "Ainda sem pontos próprios — rode na gasolina e no GNV". **Nunca simule pontos.**

## 5. AutoCal (o da ECU): melhorias com evidência

Mantenha a função da tela: acompanhar o AutoCal nativo. Melhore com o que aprendemos:

1. **Diagnóstico da curva que a ECU gerou.** Depois de cada AutoMatch nativo, mostre em linguagem humana se a curva da ECU tem **degrau que causa tranco** e onde ("degrau em 8–9 ms"). Use `joltRiskBefore` e o trecho mais íngreme, que já existem na análise refinada. Inclua um link "Ver correção no Refino".
2. **Bandas suspeitas.** Bandas em `rejectedBands` aparecem marcadas como "fora da curva física — ignorada no refino", com MAP e ms.
3. **Progresso real do automático:** `autoMatchCount` / `maxAutomatch` e bandas válidas, gasolina X/18 e GNV Y/18. Sem porcentagem inventada.
4. **O que falta coletar, em linguagem de volante.** Para cada faixa de injeção em `autopilot.bandsMissing`, diga em que RPM×MAP o motorista costuma estar nela. Calcule com as observações de gasolina já guardadas no ledger, pela mediana de rpm e MAP das leituras naquela faixa de ms. Crie para isso uma função pura em Kotlin, com teste. Ex.: "falta 6–7,5 ms: costuma ser ~2.300 rpm, 0,7 bar". Sem dados, diga "ainda sem referência".
5. A normalidade fica compacta. Quando há problema (banda incoerente, leitura falhou, AutoCal desligado), ele ganha espaço com o impacto e o próximo passo.

## 6. Agora, notificação e Sugestões

- **Agora:** o cartão "Calibração", que já existe, mostra a fase em uma linha + o próximo passo + GNV÷gasolina. Ao tocar, abre **Refino**.
- **Notificação:**
  - o toque na notificação do piloto (`NotificationController.buildRefinementAlert`) abre o app **direto em Refino**, via extra no Intent lido por `MainActivity` e repassado ao router;
  - a notificação contínua mantém a frase da fase;
  - teste.
- **Sugestões:** quando a fase for `PROPOSTA_PRONTA`, a fila mostra "Curva refinada pronta". Ao tocar, abre Refino com a revisão. Não duplique a lógica de revisão.

## 7. Levels e consumo

### 7.1 Leitura dos parâmetros

O número SC do ProgBase **é** o endereço de leitura do protocolo. Exemplo: `MUL_ACT` = SC 353 = `0x0161` em `AutoCalProtocol`.

Leia, **somente leitura**, pelo caminho do snapshot existente:
- `TIPO_SENSORE` SC 36;
- `RIF_SENSORE` SC 37;
- filtros SC 276;
- LEDs SC 300;
- `TANK_VOL` SC 313, **só o índice 0**.

**Onde você pode falhar:**
- **Formato.** Encoding e tamanho vêm de `parametros.json` e `FORMULAS.md`. Se não estiverem provados, leia os bytes crus, registre-os e mostre "não decodificado". **Não chute a escala.**
- **Leitura.** Um parâmetro que a ECU não responder vira "não lido", nunca 0.
- **SC 313.** Não decodifique o SC 313 inteiro: o índice 1 é `INJR_GAS_FLOW`.

### 7.2 `LevelEstimator` (Kotlin puro, com testes)

1. **Filtro:** aplique sobre `level_raw` o filtro da ECU, com as constantes do SC 276, rápido e lento.
2. **Mapeamento:** converta raw → posição usando as referências `RIF_SENSORE` **do próprio carro**, interpolando reserva → 1/4 → 2/4 → 3/4 → cheio, no sentido correto do sensor (`TIPO_SENSORE`; hoje `(255-raw)` assume sensor invertido).
3. **Saída:**
   - degrau igual aos LEDs da ECU (SC 300);
   - barra contínua filtrada que anda de 1 em 1, sem saltos de 50.
4. **Sem referências lidas:** mostre "proxy" explícito, sem litros. O código atual (`Mp48TelemetryScale.levelPercentage`) continua existindo como fallback rotulado.

### 7.3 Placar por calibração

- **Época:** cada gravação confirmada de Curva K ou Mapa K abre uma época.
- **Dados por época:** `gasPerAir`, km pelo GPS, queda de nível filtrada e km por degrau.
- **Tela:** "Esta calibração × anterior", em Ferramentas e numa linha no Agora.
- **Precisão:** com menos de N km (defina N e justifique), o placar diz "dados insuficientes" em vez de comparar.

Só observação. Nenhuma ação automática.

## 8. Roteiro de teste no carro

Crie `docs/V82_REFINO_FIELD_TEST.md`, curto, em português simples para o dono. Ele deve dizer:
- **Antes:** como instalar; como conferir que a ECU conecta.
- **Primeira rodagem:** deixar a ECU terminar o automático, rodar na gasolina e no GNV, e observar a fase em Refino.
- **Quando avisar "curva pronta":** como revisar e gravar, e o que observar depois (o tranco em 8–9 ms deve sumir).
- **Se avisar "trecho piorou":** como restaurar.
- **O que mandar de volta:** a pasta da sessão gravada, que já inclui `refinement_phase`.
- **O que vamos confirmar com essas sessões:** os tempos 10/25 min do piloto, a tolerância ±3% e o comportamento do level.

## 9. Testes e verificação

- **Gate rápido:** `python3 -B tools/run_checks.py` → `QUALITY_GATE_FAST=PASS`. Registre ali todo teste Node novo.
- **Testes Node em `tests/ui/`:**
  - rota Refino;
  - cada fase do piloto;
  - estado sem dados;
  - gráfico sem bandas e com bandas;
  - notificação → rota;
  - Sugestões → Refino;
  - AutoCal com bandas rejeitadas;
  - level com e sem referências.
- **Testes JVM:**
  - `denseBandsJson`;
  - a função "RPM×MAP típico por faixa";
  - `LevelEstimator`;
  - o placar.
- **Paridade:** `tests/test_refined_autocal_kotlin_parity.py` precisa continuar OK.
- **Screenshots** com Playwright (Chromium do executor), em 1280×720, com o bridge falso. Salve em `docs/evidence/ui-wu006/`:
  - Refino em cada fase;
  - AutoCal com e sem problema;
  - Agora;
  - Ferramentas → Sensor de nível.
- **Confira cada tela contra `UX-BLUEPRINT-CHECKLIST.md`**, item a item, e registre o resultado.

## 10. Commits e fechamento

1. **Um commit por bloco, nesta ordem:**
   1. navegação + Refino;
   2. dados da nossa curva (bandas densas);
   3. AutoCal;
   4. Agora / notificação / Sugestões;
   5. levels;
   6. placar;
   7. roteiro;
   8. docs.

   Cada bloco com os seus testes. Gate rápido verde antes de cada push.
2. **CI:** dispare `omegas-preapk-build.yml` na ref `claude/brave-darwin-wuliyo`. Se falhar, ache a causa raiz e corrija. Mesmo erro duas vezes → reporte ao dono em vez de insistir.
3. **Evidência:** registre SHA, run id, artifact e digest SHA-256 em `STATUS.md` e `docs/evidence/`, com `PHYSICAL_VALIDATION_CLAIMED=false`.
4. **WorkUnit:** atualize `docs/workunits/OMEGAS-WU-006.md`, separando o que foi feito, o que é hipótese e o que está pendente.
5. **Resposta ao dono** em português simples, no máximo 12 linhas:
   - o que mudou em cada tela;
   - como o level funciona agora;
   - link do APK;
   - o que só a rodagem no carro vai provar.

## 11. Pronto quando

- **Refino separado do AutoCal**, na side-nav. O motorista entende a fase em ~2 s e tem uma ação clara.
- **"Nossa curva"** é a cópia do gráfico MAP × Tpet da ECU: os 18 pontos dela, as nossas bandas densas por combustível e o afastamento GNV × gasolina. Nada simulado. A Curva K atual × refinada aparece na revisão.
- **AutoCal** explica o degrau da curva da ECU, as bandas suspeitas e o que falta coletar, em linguagem de volante.
- **Navegação:** a notificação e o cartão do Agora levam ao Refino.
- **Level:** usa o filtro e as referências da ECU e anda de 1 em 1. O placar compara calibrações.
- **Testes e prova:** gate, JVM, paridade e Node verdes; APK do CI com digest registrado; roteiro de campo escrito.
