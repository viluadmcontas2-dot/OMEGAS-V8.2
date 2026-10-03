# Prompt — OMEGAS HUB: app novo, do zero, com o conhecimento dos motores da Platina

> Cole este prompt inteiro no Claude Code aberto no repositório **OMEGAS Hub** (`viluadmcontas2-dot/-megas-Hub-`; recomendo renomear para `omegas-hub` em Settings → General antes de começar). Adicione também `viluadmcontas2-dot/OMEGAS-V8.2` à sessão **somente para leitura**: é a fonte de conhecimento e de evidência, nunca de código.

---

Você vai criar o **OMEGAS HUB** do zero. A base mãe é o **OMEGAS Platina** (`OMEGAS-V8.2`, branch `OmegasPlatina` @ `a125436`): funciona no carro do dono, mas é pesado e cheio de bugs. O Hub nasce **puro**: código novo, nomes novos, arquitetura enxuta. **Nenhum byte de código da Platina é reutilizado.** O que atravessa é o **conhecimento** (como a ECU fala, o que cada tela faz, o que a ciência calcula) e a **evidência** (gravações reais do carro e do ProgBase), que servem para provar que o Hub faz o mesmo que a Platina na ECU — e melhor na tela.

## 0. Meta e regras (não negociáveis)

**Meta:** o motor, no GNV, se comporta como na gasolina. O app sabe quão perto está, onde falta e qual é a única próxima ação. Observa sozinho; só muda algo quando o dono toca.

1. Observar é automático; **mudar a ECU é sempre o dono**. Nada grava K, zera, restaura ou aplica sozinho.
2. Todo botão é **um toque**: sem diálogo de confirmação, sem segurar. Proteção = **foto antes + Desfazer** (restaurar a leitura anterior; nenhum comando novo na ECU).
3. O fim de toda gravação é o **readback** da ECU. "Gravado" só depois dele.
4. Nenhuma falha derruba o app: toda exceção vira estado `✗` legível com próxima ação. Erro de transporte (cabo/USB) ≠ erro da ECU (NACK/readback).
5. **Clean-room.** Proibido copiar arquivo, função, trecho ou CSS da Platina. Permitido ler a Platina para escrever **especificações** (docs e fixtures), e usar as gravações reais como oráculo. Um teste no CI falha se qualquer arquivo do Hub compartilhar **12 ou mais linhas consecutivas iguais** (normalizadas) com qualquer arquivo da Platina no SHA mãe. Nomes `v7`, `prohub`, `Verde`, `Platina`, `learning`, `Predictor`, `MANUAL_AUTOMATCH`: zero ocorrências em código.
6. **Os bytes para a ECU são sagrados.** Toda leitura/escrita que o Hub emite tem que ser **idêntica** à que a Platina emite para a mesma operação, provada por replay das gravações (portmon do ProgBase + sessões reais). O Hub só fala com a ECU real depois que essa prova passa.
7. Dois níveis em toda tela: frase humana primeiro; comando, bytes e readback em **"Detalhes técnicos"**.
8. Multimídia fraca, 1280×720, Android 8+ (`minSdk 26`): WebView + JS vanilla, sem framework, sem bundler, sem web font, sem `backdrop-filter`; **zero timers de UI** (redesenho por revisão).
9. Texto de UI em português. Código curto e didático; nada "por via das dúvidas".
10. Validação física (classe 5) só com o dono no carro. Nunca escreva "validado" sem isso.

## 1. Decisões de engenharia (já tomadas — execute)

| Tema | Decisão |
|---|---|
| `applicationId` | **`com.omegas.hub`**. Instalação limpa; o estado real vive na ECU e as sessões no Drive. Convive com a Platina até o dono desinstalar. |
| Pacotes Kotlin | `com.omegas.hub.{usb, ecu, autocal, calibration, equivalence, session, state, service, bridge}`; assets em `ui/`. Nomes dizem o que a coisa é (`EcuLink`, `Mp48Frames`, `CurveKWriter`, `MapKWriter`, `AutoCalMonitor`, `EquivalenceEngine`, `SessionRecorder`). |
| Ponte JS↔Kotlin | **Uma só: `Omegas`**, `snapshot(sinceRevision)` e `request(intentJson)`, evento `window.OmegasOnRevision(revision)`. Estado em um `StateStore` imutável com `revision` monotônica e seções `connection, live, now, reference, points, index, nextAction, operation, session, autocal, settings`. Fila: **uma mutação na ECU por vez**, ciclo `RECEBIDO → PREPARANDO (foto) → EXECUTANDO → CONFERINDO (readback) → CONCLUÍDO | FALHOU`, recibo na sessão. |
| Intents (lista fechada) | `CURVE_WRITE, CURVE_RESET, CURVE_RESTORE, CURVE_BACKUP_SAVE, MAP_WRITE, REFERENCE_FREEZE, AUTOCAL_RELEARN, AUTOCAL_PAUSE, AUTOCAL_RESUME, AUTOCAL_RESET_GAS, AUTOCAL_RESET_PETROL, SESSION_EXPORT, OVERLAY_TOGGLE, SETTINGS_SET, UNDO`. Nada além disso tem botão. |
| Formato de sessão | **Compatível** com `omegas-session-log-v1` (JSON Lines: `sequence, recordedAtMs, recordedAtUtc, type, source, data`; `manifest.json`, `session_summary.json`). O Drive e as ferramentas do dono dependem dele. |
| Execução | Lotes grandes; teste só quando decide algo (Python/JS local; Kotlin no CI do PR, não há SDK na sessão); uma branch de trabalho por vez; canônica = `main`; PR entra com CI verde no SHA. Emulador **uma vez**, no fechamento de UI/UX. APK **um só**, no fim, com SHA-256. |

## 2. Fase de conhecimento (P0) — o único momento em que se lê a Platina

Saída: `docs/spec/` do Hub, escrito por você a partir da Platina **e** da evidência. Nada de código.

| Spec | Fonte na Platina (ler) | Conteúdo exigido |
|---|---|---|
| `ecu-link.md` | `usb/UsbSerialManager`, `UsbRecoveryPolicy`, `OmegasUsbIdentity`, `ecu/Mp48SerialScheduler`, `Mp48BackpressureScheduler` | VID/PID, driver, baud, framing, cadência (~2 s operacional, ~4 s referência), árbitro serial único, política de reconexão e de backpressure, o que é erro de transporte |
| `mp48-frames.md` | `ecu/Mp48Protocol`, `Mp48TelemetryScale`, `ResponseDrivenEcuEngine`, fixtures `tests/fixtures/portmon-*`, `evidence/portmon/` | **tabela byte a byte** de cada comando de telemetria: request, resposta, offsets, escalas (RPM, MAP S16LE com fail-closed no bit alto, `petrol_ms`, `gas_ms`, pressões, combustível ativo, LEVELS RAW), checksums, timeouts |
| `autocal.md` | `ecu/AutoCalProtocol`, `AutoCalPointDeleteProtocol`, `AutoCalScale`, `autocal/AutoCalNativeActionManager`, `NativeAutoCal*`, fixture `tests/fixtures/progbase-autocal-action-map-v1.json`, `docs/autocal/` | comando `0x24`, ações `0x01` reset gasolina / `0x02` reset gás / `0x04` reset geral (não exposto) / `0x08` AutoMatch (não exposto), frames exatos, campos do snapshot (`PETR_INJ_TBP, MNFLD_PRESS_THD, MUL_ACT, *_BUF*, NUM_BUF_UPD_*, ACQUIRED_ZONES_*, NUM_AUTOMATCH_EXECUTED` …), 18 bandas, maturidade, épocas, contador 3/3, apagar ponto |
| `curve-k.md` e `map-k.md` | `calibration/KFactorProtocol`, `KFactorManager`, `KWriteManager`, `KMapPhysicalAxes`, `MapBatchPlan`, `CalibrationWriteSafetyPolicy`, `KFactorBackupRetention` | Curva K: 30 pontos, `MUL_ACT` Q14, leitura, backup, restore, reset, escrita em lote, **readback obrigatório e critério de "bateu"**. Mapa K: 12×12 RPM×MAP, 1–16 células por lote, uma chamada de lote, ACK, readback, eixos físicos |
| `equivalence.md` | spec `docs/superpowers/specs/2026-10-03-omegas-platina-norte-unico-design.md` §1, plano `plans/2026-10-03-f4-cerebro-unico.md`, `autocal/EcuPetrolReference`, `EquivalenceLedger`, `EquivalencePhases`, `AutoMatchRefinedEngine`, `StallWatch`, `tools/autocal_refine/` | Referência congelada, Curva Própria por célula de 0,02 bar, `EquivalencePoint` (estados `SEM_DADOS … CONTESTADO`), tolerâncias, índice "% equivalente", próxima ação, fases, apagão/quase-apagão. **Oráculo Python** dos cálculos (escrito do zero) com paridade exigida do Kotlin |
| `session.md` | `diagnostics/SessionRecorder`, `SessionPartPlanner`, `SessionResumo`, `README_PARA_IA.txt` das sessões no Drive | formato `omegas-session-log-v1` campo a campo, ZIP único por sessão, `RESUMO.md`, SIGKILL sem perda |
| `ux.md` | `assets/ui/index.html`, `screens/*.js`, `core/display-rules.js`, `components/*`, Notion [CUSTOMROM](https://app.notion.com/p/3b68ee52ac5481839046f36b482aab44) e [Omega Dev 4.0](https://app.notion.com/p/3b78ee52ac548170b5c1fb69606ced21) | 7 abas `01 Agora · 02 Mapa K · 03 Curva K · 04 AutoCal · 05 Refino · 06 Sessões · 07 Ferramentas`; para cada aba: pergunta que responde, elementos, estados (sem cabo / conectando / conectado / gravando / falhou), subpáginas (Curva K: Equivalência · Editar · Backups; AutoCal: Aquisição · Referência · Épocas; Refino: Fases · Pontos; Sessões: Evolução · Lista), fluxos de um toque, overlay de operação, regras de exibição (número desconhecido nunca vira 0; barra não congela). Sai: Sugestões, AutoMatch manual, "Nova aquisição completa", exportar/importar aprendizado, LAN. **Screenshots de referência** da Platina (run do emulador existente) em `docs/spec/ux/`. |

**Evidência que vem junto (dados, não código):** `fixtures/autocal/real/*.json.gz` (sessões reais do dono), `tests/fixtures/portmon-*.json`, `tests/fixtures/progbase-*.json`, `evidence/portmon/`. Guarde em `fixtures/` do Hub com um `ORIGEM.md` (SHA e caminho de origem).

## 3. Entrega em 5 PRs (cada um entra em `main` com CI verde)

| PR | Entrega | Prova |
|---|---|---|
| **P0 · Conhecimento + esqueleto** | `docs/spec/*` (§2), fixtures com origem, `AGENTS.md` (uma tela), Gradle (`com.omegas.hub`), `ci.yml` (`tools/run_checks.py` Python + `node --test` → `testDebugUnitTest lintDebug`), **teste clean-room** (12 linhas) e de nomes proibidos | CI verde |
| **P1 · Motor da ECU** | `usb`, `ecu` (frames, telemetria, árbitro, cadência), `autocal` (monitor, ações expostas), `calibration` (Curva K e Mapa K com backup/restore/reset/lote/readback) — tudo escrito do zero pelas specs | **conformidade byte a byte**: para cada comando e ação, os bytes emitidos = fixtures do portmon/ProgBase; telemetria decodificada = valores das sessões reais (replay); teste de ACK falho ≠ sucesso e readback divergente ≠ sucesso |
| **P2 · Cérebro + estado + ponte** | `equivalence` (motor + oráculo Python), `StateStore`, fila, `Omegas`, `session` | replay das sessões reais: índice sobe após ajuste, Curva Própria prevê melhor que a Referência (validação cruzada), paridade Python↔Kotlin; fila serializa; cabo caído → `FALHOU` sem crash; Desfazer = foto byte a byte; nenhum intent sem handler, nenhum método sem chamador |
| **P3 · UI/UX** | `ui/` do zero seguindo `ux.md`: `tokens.css` único (fundo, superfície, texto, acento + 5 cores de estado), um CSS por tela, gráficos em `<canvas>` com antialias (Referência tracejada, Própria sólida, 30 pontos por estado, ▲ AGORA com redesenho parcial), overlay de operação único, "Detalhes técnicos" sempre último, toque ≥ 76 px (Mapa K ≥ 44 px), texto crítico ≥ 24 px, zero timers | `node --test` por tela e estado; **emulador 1280×720 uma vez** (7 abas × estados), comparado lado a lado com os screenshots da Platina |
| **P4 · APK** | `STATUS.md` (SHA, run, classe de prova, não provado), protocolo de teste físico das 7 abas, `apk.yml` manual | **APK final** com SHA-256 |

**Em cada PR:** o que mudou · classe de prova (1 contrato · 2 sintético · 3 replay real · 4 emulador · 5 físico) · o que ficou **não provado**. Spec ambígua ou Platina contradizendo a evidência: a **evidência** (portmon, sessões) vale mais que o código da Platina; registre a decisão em uma linha na Issue do PR e siga. Se a decisão muda o que o dono vê ou o que a ECU recebe, pare e pergunte.

## 4. Orçamentos (metas; só clean-room, nomes proibidos, conformidade de bytes e ponte sem órfão falham o CI)

Kotlin **≤ 10k linhas** (Platina: 20,7k). UI **≤ 5k** (Platina: 8,8k). Métodos de ponte: **2** (Platina: 57). Timers de UI: **0** (Platina: 13). Primeira pintura < 300 ms, toque < 100 ms no emulador (registrar no STATUS, classe 4).

## 5. Relato ao dono

Português simples, ao fim de cada PR: o que mudou na tela, número do PR, link do run que provou, o que ficou não provado. Sem jargão.

Comece pelo P0.
