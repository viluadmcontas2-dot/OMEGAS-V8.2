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
- Independência: leituras estáveis consecutivas se sobrepõem, então quadro não é evidência. Episódio = **visita à faixa**: pares da mesma faixa separados por ≥ 60 s. Peso por episódio (um episódio de uma faixa vale ≤ 4 pares) e teto de peso da telemetria por faixa.
- Um ponto só é julgado (EQUIVALENTE/POBRE/RICO) com ≥ 3 leituras em ≥ 3 visitas, dispersão conhecida e meia-largura `1,96·disp/√visitas ≤ 4%`. Senão é APRENDENDO (nunca "tolerância larga").
- Tolerância `clamp(2·dispersão, 4%, 5%)`; a frase do ESTAVEL diz ±4% (±5% onde a gasolina é a curva da ECU).
- O GNV medido não é puxado para a gasolina (a Referência é prior só da gasolina).
- `index` só é número quando os pontos julgados cobrem ≥ 50% do uso da condução (`judgedUsage`); senão é `null` ("—").
- A telemetria não move ponto que a evidência nativa madura já cobre; nó cujo erro de evidência cabe em ±4% (e cujo K é coerente) não se move; proposta que piora o critério do próprio motor nunca é emitida.
- Faixa grossa só vale com leituras espalhadas por dentro dela (≥ 2 de 3 terços, ≥ 2 pares cada).
- Prova por ponto: só julga se o ponto foi julgado com episódios independentes; fechada sem convergir vira INCONCLUSIVO com motivo (`NAO_CONVERGIU`, depois de 2 tentativas `TENTATIVAS_ESGOTADAS`), nunca some em silêncio nem propõe em laço.
- Diário: exige episódios e cobertura interna; o ganho aprendido decai a cada experimento; "curta" que não melhorou não firma ganho; Desfazer/Restaurar/Reset não contam como passada de ganho nem ensinam.
- Alinhamento à Curva K da ECU roda no tique do serviço (não depende da tela); impressão digital desconhecida com GNV guardado = falha fechada; snapshot com `temporalCoherent == false` não é curva do cérebro.

**Contrato de forma do resultado (`EquivalenceJson.result`, teste `BrainContractTest`):** `index` é número escalar 0..1 **ou `null`** (mostrar "—"; nunca objeto, nunca 0 no lugar de desconhecido). `provisional` (boolean), `coverage` (inteiro) e `judgedUsage` (0..1) são **irmãos planos** de `index` no mesmo nível, não filhos dele. As mesmas chaves existem quando `available` é false (`index: null`, `coverage: 0`). `reference.ageMs` (nulo = "—") e `reference.stale` (deriva da ECU > 8%) registram idade e deriva da Referência; a decisão de congelar de novo é do dono.

