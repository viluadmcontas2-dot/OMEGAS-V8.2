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
