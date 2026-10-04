# Arquitetura — OMEGAS Platina (Norte Único)

Fonte: spec §2 e §4 (`docs/superpowers/specs/2026-10-03-omegas-platina-norte-unico-design.md`). Este arquivo descreve o **alvo**; as fatias F1–F8 levam o código até ele.

## Fluxo

```
 ECU MP48 ──USB serial──> UsbSerialManager ─> Mp48SerialScheduler ─> ResponseDrivenEcuEngine
                                                     │                       │ telemetria (rpm, MAP, petrol_ms, gas_ms…)
                                                     │                       v
             fila única (OperationQueue)             │       NativeAutoCalMonitor · SessionRecorder · StallWatch
   ┌──────────────────────────────────────┐          │                       │
   │ RECEBIDO → PREPARANDO (foto)          │  comandos│                       v
   │ → EXECUTANDO → CONFERINDO (readback)  │──────────┘        EquivalenceEngine (Referência congelada,
   │ → CONCLUÍDO · Desfazer | FALHOU       │                    Curva Própria, 30 pontos, índice,
   └──────────────────────────────────────┘                    próxima ação, EquivalencePhases)
           ^ request(intent)                                             │
           │                                                             v
   ┌───────┴─────────────────────────────────────────────────────────────────────┐
   │ StateStore: AppState imutável, revision monotônica                          │
   │ seções: connection, live, now, reference, points, index, nextAction,        │
   │         operation, session, autocal, settings                                │
   └───────┬─────────────────────────────────────────────────────────────────────┘
           │ snapshot(sinceRevision) · OmegasOnRevision(revision)
           v
   Ponte única `Omegas` (JS) ─> assets/ui: 01 Agora · 02 Mapa K · 03 Curva K · 04 AutoCal
                                          05 Refino · 06 Sessões · 07 Ferramentas
```

A UI não calcula protocolo, não confirma escrita e não guarda cópia paralela da ECU. Ela lê `snapshot()` e pede `request(intent)`; nada mais.

## Quem escreve no `StateStore`

| Escritor | Seções | Quando |
|---|---|---|
| Motor de telemetria (`ResponseDrivenEcuEngine` via serviço) | `connection`, `live`, `now` | a cada quadro válido; a tela redesenha por revisão, não por quadro |
| `EquivalenceEngine` | `reference`, `points`, `index`, `nextAction` | quando a telemetria muda o estado de um ponto ou a Referência muda |
| `OperationQueue` | `operation` | a cada etapa de um intent (recibo) |
| `NativeAutoCalMonitor` | `autocal` | a cada leitura da aquisição nativa |
| `SessionRecorder` | `session` | abertura, eventos, fechamento, ZIP |
| Serviço / configurações | `settings` | intent `SETTINGS_SET` ou `OVERLAY_TOGGLE` |

Leitor: só a ponte `Omegas`. `RuntimeSnapshotBus`, memos do AutoCal e os JSON de ledger/journal são cache interno dos motores, nunca fonte da UI.

## Núcleo intocado (spec §4.1)

`UsbSerialManager` · `ResponseDrivenEcuEngine` · `AutoCalProtocol` · `KFactorManager` · `KWriteManager` · `AutoCalNativeActionManager` · `NativeAutoCalMonitor` · `EquivalenceLedger` · `EcuPetrolReference` · `AutoMatchRefinedEngine` · `StallWatch` · `RefinementJournal` · `SessionRecorder` · `TelemetryOverlayController` · `Mp48SerialScheduler`. Passam a ser chamados pela fila; os comandos não mudam.

## Intents (lista fechada, spec §2.2 + R1 do índice)

`CURVE_WRITE` · `CURVE_RESET` · `CURVE_RESTORE` · `CURVE_BACKUP_SAVE` · `MAP_WRITE` · `REFERENCE_FREEZE` · `AUTOCAL_RELEARN` (sem Desfazer) · `AUTOCAL_PAUSE` · `AUTOCAL_RESUME` · `AUTOCAL_RESET_GAS` · `AUTOCAL_RESET_PETROL` · `SESSION_EXPORT` · `OVERLAY_TOGGLE` · `SETTINGS_SET` · `UNDO`.

## Evidência do cérebro e contrato do resultado (revisão adversarial)

**Regras (o "% equivalente" só sobe com evidência):**

- Fonte única: veredito e proposta de K saem dos mesmos pares (gasolina de referência × GNV), casados por RPM±150 / MAP±0,02 (ou pela curva de gasolina da ECU onde o app não mediu gasolina). `EvidencePairs` é usado pelo livro (`EquivalenceLedger`) e pelo cérebro (`EquivalenceEngine`).
- Independência por **estatística, não por relógio** (decisão do dono, 2026-10-04: removido o portão de "visitas separadas por ≥ 60 s"). Leituras estáveis consecutivas se sobrepõem (janela de 3 quadros ≈ 0,6 s), então o número de leituras não é o de amostras independentes: o **n efetivo** (`EvidencePairs.effectiveN`) é o menor entre a correção de autocorrelação lag-1 `n·(1−ρ)/(1+ρ)` (ρ∈[0;0,95]) e o nº de leituras separadas por ≥ 1 s (sem sobreposição). Bloco = pares da mesma faixa a < 3 s: só de-duplicação; o peso de um bloco vale ≤ 4 pares.
- Um ponto só é julgado (EQUIVALENTE/POBRE/RICO) quando `EquivalenceEngine.isJudgeable`: ≥ 3 pares, n efetivo ≥ 3, dispersão conhecida e intervalo de confiança do erro `t(n_ef)·disp/√n_ef ≤ 4%` (t de Student 95%). Poucos dados ⇒ intervalo largo ⇒ APRENDENDO ("ainda sem certeza"), sem pedir tempo. Muitos dados ⇒ julga, de quaisquer faixas, passando rápido ou devagar. Nenhum bloqueio por faixa individual; o estado é global (índice + uma ação).
- Texto ao dono nunca cita regra interna (minutos, visitas, intervalo de confiança, episódios): só a consequência humana ("Estou aprendendo seu motor"). Regras ficam em `technical`.
- Tolerância `clamp(2·dispersão, 4%, 5%)`; a frase do ESTAVEL diz ±4% (±5% onde a gasolina é a curva da ECU).
- O GNV medido não é puxado para a gasolina (a Referência é prior só da gasolina).
- `index` só é número quando os pontos julgados cobrem ≥ 50% do uso da condução (`judgedUsage`); senão é `null` ("—").
- A telemetria não move ponto que a evidência nativa madura já cobre; nó cujo erro de evidência cabe em ±4% (e cujo K é coerente) não se move; proposta que piora o critério do próprio motor nunca é emitida.
- Faixa grossa só vale com leituras espalhadas por dentro dela (≥ 2 de 3 terços, ≥ 2 pares cada).
- Prova por ponto: só julga se o ponto foi julgado com episódios independentes; fechada sem convergir vira INCONCLUSIVO com motivo (`NAO_CONVERGIU`, depois de 2 tentativas `TENTATIVAS_ESGOTADAS`), nunca some em silêncio nem propõe em laço.
- Diário: exige episódios e cobertura interna; o ganho aprendido decai a cada experimento; "curta" que não melhorou não firma ganho; Desfazer/Restaurar/Reset não contam como passada de ganho nem ensinam.
- Alinhamento à Curva K da ECU roda no tique do serviço (não depende da tela); impressão digital desconhecida com GNV guardado = falha fechada; snapshot com `temporalCoherent == false` não é curva do cérebro.

**Contrato de forma do resultado (`EquivalenceJson.result`, teste `BrainContractTest`):** `index` é número escalar 0..1 **ou `null`** (mostrar "—"; nunca objeto, nunca 0 no lugar de desconhecido). `provisional` (boolean), `coverage` (inteiro) e `judgedUsage` (0..1) são **irmãos planos** de `index` no mesmo nível, não filhos dele. As mesmas chaves existem quando `available` é false (`index: null`, `coverage: 0`). `reference.ageMs` (nulo = "—") e `reference.stale` (deriva da ECU > 8%) registram idade e deriva da Referência; a decisão de congelar de novo é do dono.


## Contratos novos da lógica (fix-logic, 2026-10-04) — para a UI

Todos só leitura (a ponte `getEquivalence` = `EquivalenceView.build`); nada grava; desconhecido é `null` ("—"), nunca 0.

**A. A ECU é a verdade da aquisição (`autopilot.ecuTruth`, `acquisitionZones`).** Banda *madura* = `ZONA_ADQUIRIDA` ou contador ≥ limiar da própria ECU (limiar > 0). Zona *coberta* = flag da ECU OU ≥ metade das bandas da zona maduras (a flag zera a cada AutoMatch; as bandas lidas continuam valendo). Só *falta* o que a ECU não tem, e nada falta depois que a ECU já entregou AutoMatch (contador ≥ 1) ou as 4 zonas dos dois combustíveis.
- `autopilot.ecuTruth`: `{read, autoMatchCount, maxAutomatch, autoCalEnabled, petrol|gas:{zoneFlags[4]|null, zonesFlagged, zoneCovered[4]|null, zonesCovered|null, zonesTotal:4, basis[4]("FLAG_DA_ECU"|"BANDAS_LIDAS"|"NAO_ADQUIRIDA"|"DESCONHECIDA"), bandsActive, bandsMature, bandsTotal:18, complete, missingZones[]}, allZonesCovered, delivered, missing:[{fuel, zones[], text}], summary}`.
- `autopilot.petrolZones/gasZones` = zonas **cobertas** (número) ou `null` sem leitura. `autopilot.next/headline` só pedem o que a ECU realmente não tem.
- `getAutoCalProjection().acquisitionZones`: `{petrol[4], gas[4]}` agora são as zonas **cobertas** (mesma forma antiga; vetor vazio = a ECU não entregou o campo), `raw` = flags como a ECU entregou, `basis`. O JS não muda: quem lê `acquisitionZones` já mostra a verdade da ECU. `acquisition.zoneFlags {petrol|gas: bool[4]|null}` é novo no snapshot de aquisição.

**B. Engasgo → ajuste local (`stalls`).** `stalls.regions[]` (ordenado por `count`): `{fromMs,toMs, mapBar, rpm, ms, count, stallCount, nearCount, firstAt, lastAt, ats[], curvePoints[] (índices 0..29 da Curva K que cobrem a região), recentCount (engasgos depois da última gravação), diagnosis:{direction:"POBRE"|"RICO"|"DESCONHECIDA", text, technicalReason}, proposal?:{direction, points:[{index, axisMs, kBefore, kAfter, kAfterRaw, deltaPct}], pointIndexes[], text, maxStepPct:8, kMin:0.75, kMax:1.2}}` (+ chaves antigas `rpmBefore`). `proposal` só com ≥ 2 engasgos recentes, direção determinada pela **evidência do cérebro** nos pontos da região (POBRE → K sobe; RICO → K desce), passo ≤ ±8% do K atual, K em 0,75..1,20, nunca empobrece abaixo de 3,5 ms. Sem direção: texto "região X com N engasgos; faltam dados para saber se é rico ou pobre" e nenhuma proposta. `stalls.localProposal` (ou `null`) tem a forma do `proposal` do cérebro (`mode:"STALL_LOCAL"`, `currentRaw[30]`, `refinedRaw[30]`, `pointIndexes`, `text`, `automatic:false`) para o mesmo toque de gravar (foto antes, Desfazer, readback). Quando o cérebro está em `COLLECT`/`NOTHING`, `equivalence.nextAction` e o `nextAction` plano viram `{kind:"APPLY", local:true, text, pointIndexes, currentRaw, refinedRaw}`. Engasgo nunca altera nada sozinho.
- `fluidity` (aba Diagnóstico): `{petrol|gas:{samples, bins, deviationPct|null, jerkPct|null, msPerBar|null, verdict:"LINEAR"|"SOLAVANCOS"|"SEM_DADOS", text}, summary, criteria}`. Desvio da reta ms×MAP e variação de ms por varredura de MAP (segunda diferença), em % do ms médio.

**C. `betweenPoints[]` (só no Refino).** Um por intervalo entre bandas vizinhas da ECU (17 para as 18; ≤ 19 contando as duas pontas abertas que só aparecem com leitura; teto 2× as bandas = 36): `{index (−1 ponta baixa, 0..16, 17 ponta alta), kind:"gap"|"open-low"|"open-high", fromMs, toMs, centerMs, mapBar|null, petrolMs|null, gnvMs|null, diffPct|null, samples, visits|null, state:"COLETADO"|"FALTA"}`. Grade fixa (54 bins finos → 18 faixas → 17 intervalos), cache por revisão do livro; independe de `FINE_BINS_ENABLED`. Bolinhas iguais, sem triângulo.

**D. `refinoState`.** `{phase, whatNow, nextAction, canAct, counts:{intervalsTotal, intervalsCollected, intervalsMissing, ecuAutoMatchCount, ecuAutoMatchMax, ecuZonesPetrol, ecuZonesGas, pointsToWrite}, whyNoProposal|null, ecuSummary|null, technical:{phase, reasonCode, failureDomain, nextActionKind, local, index, judgedUsage}}`. Todo texto fora de `technical` é humano (glossário) e nunca cita minutos, visitas, intervalo de confiança nem episódios.
