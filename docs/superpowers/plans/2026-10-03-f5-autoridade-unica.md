# Fatia 5 — Autoridade única Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trocar as quatro pontes (`OmegasNative`, `OmegasCalibration`, `OmegasAutoCal`, `OmegasPower`) por uma só, `Omegas`, com `snapshot(sinceRevision)` + `request(intent)`, servida por um `StateStore` com revisão, uma `OperationQueue` serial com foto antes e `Desfazer`, e recibo de toda operação na sessão.

**Architecture:** Pacote novo `com.omegas.prohub.state`: `StateStore` (11 seções JSON, `revision` monotônica) ← escrito por `StatePublisher` (telemetria, cérebro F4, AutoCal, sessão, ajustes) e pela `OperationQueue` (seção `operation`); a fila roda um `IntentHandler` por `Intent` sobre portas finas (`CurvePort`, `MapPort`, `AutoCalActionPort`) que só chamam os managers provados (`KFactorManager`, `KWriteManager`, `AutoCalNativeActionManager`) — nenhum comando de ECU muda. A página lê só por `OmegasBridge` (JS `Omegas`) e é avisada por `window.OmegasOnRevision(revision)` (≤ 10 Hz); `native-api.js` e `autocal-api.js` viram adaptadores finos sobre `core/omegas.js`, de modo que as telas atuais continuam funcionando até a F6/F7.

**Tech Stack:** Kotlin (Android, JVM 17, JUnit 4, `org.json`), WebView + JS vanilla (`node --test` em `vm`), contratos Python, GitHub Actions (`tools/ci/remote-test.sh`), `gh`.

**Spec:** docs/superpowers/specs/2026-10-03-omegas-platina-norte-unico-design.md  ·  **Índice/contrato:** docs/superpowers/plans/2026-10-03-00-norte-unico-index.md

Branch: `work/platina-f5-autoridade-unica` (de `OmegasPlatina`, com F1–F4 mesclados). Toda mensagem de commit desta fatia termina com as duas linhas:

```
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01YN94YqPpeRPSaeNkh2QkF7
```

> **Linhas citadas** em "Modify:" são da base atual (pré-F1). F1–F4 deslocam linhas: antes de editar, reconfirme cada trecho com o `grep` indicado no passo. Os símbolos citados são estáveis (F1 copia os 9 endpoints de curva/mapa para `CalibrationOperationsBridge` com as mesmas assinaturas).


> **Antes de começar:** leia a seção **Reconciliação entre planos** do índice (`2026-10-03-00-norte-unico-index.md`). Onde este plano divergir dela (intents, payloads, formato do snapshot, comando do emulador), vale o índice.
## Global Constraints

Todas as do índice, mais:

- Pacote único `com.omegas.prohub.state`; nomes `Intent`, `OpStage`, `FailureKind`, `StateStore`, `OperationQueue`, `Snapshotter`, `OmegasBridge` exatamente como no índice. Os handlers ficam em `com.omegas.prohub.state.handlers`.
- Nenhuma linha de `UsbSerialManager`, `ResponseDrivenEcuEngine`, `AutoCalProtocol`, `KFactorManager`, `KWriteManager`, `AutoCalNativeActionManager`, `AutoCalPointDeleteProtocol` muda; as portas só chamam métodos públicos que já existem.
- Só a `OperationQueue` (thread única `omegas-operation-queue`) chama métodos mutantes desses managers. Leituras automáticas de curva/mapa passam pela mesma thread (`OperationQueue.observe`), nunca em paralelo a uma mutação.
- **Sessão do Desfazer = vida do `TelemetryForegroundService`**, não da gravação de sessão nem do cabo: queda de USB encerra o `SessionRecorder` (`TelemetryForegroundService.kt:745-749`), mas a foto precisa sobreviver para o Review Focus 2. `Snapshotter.endSession()` roda só em `onDestroy` do serviço.
- Fotos ficam só em memória (`Snapshotter`); o recibo na sessão guarda os bytes da foto para perícia.
- Toda espera por um manager assíncrono tem prazo: `OPERATION_TIMEOUT_MS = 120_000L`; estourou → `FALHOU/TRANSPORTE` ("Tempo esgotado esperando a ECU").
- `Throwable` (inclusive `Error`) dentro de handler vira `FALHOU`; a fila nunca morre e aceita o próximo pedido.
- `OmegasBridge` responde em < 100 ms: `request` só valida e enfileira; `snapshot` só serializa.
- Payload de cada intent é o definido em Task 5.2 (`IntentPayloads`); campo desconhecido é ignorado, campo obrigatório ausente → `{"ok":false,"error":...}` síncrono, sem recibo.
- `window.Omegas` é o objeto nativo injetado; o módulo JS é `window.OmegasUi.Omegas` (não sobrescreve o nativo).
- Sem `setInterval` novo em JS nesta fatia (o `Scheduler` atual fica; some na F6/F7).

## Review Focus

1. **Cabo USB cai no meio de `CURVE_WRITE`** (`KFactorManager` termina em `BATCH_FAILED` "USB desconectado" depois de 2 de 5 pontos): recibo `FALHOU`/`TRANSPORTE`, próxima ação fala em cabo, foto mantida; com o cabo de volta, `UNDO{photoId}` relê, regrava só os pontos divergentes e o readback é igual à foto byte a byte. Teste: Task 5.4 `cableDropMidWriteKeepsPhotoAndUndoRestoresAfterReconnect`.
2. **Dois toques seguidos** (`CURVE_WRITE` e logo `MAP_WRITE`): o segundo fica em `RECEBIDO` até o primeiro chegar a `CONCLUIDO`/`FALHOU`; nunca há dois handlers ao mesmo tempo. Teste: Task 5.2 `secondSubmitWaitsForFirst`.
3. **Exceção inesperada no handler** (`IllegalStateException`, `JSONException`, `NotImplementedError`): recibo `FALHOU`/`APP` com frase humana, app vivo, o pedido seguinte executa. Teste: Task 5.2 `anyThrowableBecomesFailedAndQueueSurvives`.
4. **Telemetria a 80 ms inunda revisões**: a página recebe `OmegasOnRevision` no máximo 10×/s e a última revisão sempre chega (nada de estado final perdido). Teste: Task 5.1 `throttleCapsAtTenHzAndDeliversLast`.
5. **Tela atual chamando ponte removida** (`OmegasNative.getStatus`, `OmegasAutoCal.getEquivalence`, `OmegasCalibration.startCurveBatchWrite`, `OmegasPower.setOverlayEnabled`): nenhum arquivo de `assets/ui` cita essas pontes e os adaptadores devolvem o mesmo formato legado, então gravar curva/mapa/AutoCal pelas telas antigas termina em "confirmado". Testes: Task 5.12/5.13 (UI) e Task 5.15 `test_omegas_bridge_contract.py`.

---

### Task 5.1: Contratos, `StateStore` e `RevisionThrottle`

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/state/Intent.kt`
- Create: `app/src/main/java/com/omegas/prohub/state/StateStore.kt`
- Create: `app/src/main/java/com/omegas/prohub/state/RevisionThrottle.kt`
- Test: `app/src/test/java/com/omegas/prohub/state/StateStoreTest.kt`, `app/src/test/java/com/omegas/prohub/state/RevisionThrottleTest.kt`

**Interfaces:**
- Produces (índice, literal):
```kotlin
enum class Intent { CURVE_WRITE, CURVE_RESET, CURVE_RESTORE, MAP_WRITE, REFERENCE_FREEZE, AUTOCAL_RELEARN,
  AUTOCAL_PAUSE, AUTOCAL_RESUME, AUTOCAL_RESET_GAS, AUTOCAL_RESET_PETROL, SESSION_EXPORT, OVERLAY_TOGGLE, SETTINGS_SET, UNDO }
enum class OpStage { RECEBIDO, PREPARANDO, EXECUTANDO, CONFERINDO, CONCLUIDO, FALHOU }
enum class FailureKind { TRANSPORTE, ECU, APP }
```
- Produces (novo):
```kotlin
class StateStore {
  companion object { val SECTIONS: List<String> = listOf("connection","live","now","reference","points","index","nextAction","operation","session","autocal","settings") }
  fun put(section: String, value: JSONObject)      // seção fora de SECTIONS → IllegalArgumentException; igual (toString) ao atual → no-op, sem revisão
  fun put(section: String, value: JSONArray)       // só para "points" (spec §2.1: points[30]); outra seção → IllegalArgumentException
  fun revision(): Long
  fun snapshot(sinceRevision: Long): JSONObject    // {"revision": R} + só seções com revisão > sinceRevision; seção nunca escrita é omitida
  fun addListener(listener: (Long) -> Unit): () -> Unit   // chamado fora do lock com a revisão nova; devolve o "remover"
}
class RevisionThrottle(
  private val minIntervalMs: Long = 100L,
  private val now: () -> Long,
  private val schedule: (delayMs: Long, task: () -> Unit) -> Unit,   // produção: webView.postDelayed
  private val emit: (Long) -> Unit,
) { fun onRevision(revision: Long) }   // thread-safe; guarda o máximo pendente; emite sempre via schedule (delay 0 se livre), no máx. 1 por minIntervalMs, borda final garantida
```

- [ ] **Step 1: Write the failing tests**

Primeiro criar a branch e o PR rascunho: `git switch -c work/platina-f5-autoridade-unica origin/OmegasPlatina`.

```kotlin
// StateStoreTest
@Test fun revisionStartsAtZeroAndIsMonotonic() {
  val s = StateStore(); assertEquals(0L, s.revision())
  s.put("live", JSONObject().put("rpm", 800)); assertEquals(1L, s.revision())
  s.put("live", JSONObject().put("rpm", 800)); assertEquals(1L, s.revision())   // igual = sem revisão
  s.put("session", JSONObject().put("recording", true)); assertEquals(2L, s.revision())
}
@Test fun partialSnapshotHasOnlyChangedSections() {
  val s = StateStore(); s.put("live", JSONObject().put("rpm", 800)); s.put("session", JSONObject().put("recording", true))
  val part = s.snapshot(1L)
  assertEquals(setOf("revision", "session"), part.keySet())
  assertEquals(2L, part.getLong("revision"))
  assertEquals(setOf("revision", "live", "session"), s.snapshot(0L).keySet())
  assertEquals(setOf("revision"), s.snapshot(2L).keySet())
}
@Test fun pointsIsAnArray() {
  val s = StateStore(); s.put("points", JSONArray(List(30) { JSONObject().put("index", it) }))
  assertEquals(30, s.snapshot(0L).getJSONArray("points").length())
}
@Test(expected = IllegalArgumentException::class) fun unknownSectionRejected() { StateStore().put("map", JSONObject()) }
@Test(expected = IllegalArgumentException::class) fun arrayOnlyForPoints() { StateStore().put("live", JSONArray()) }
@Test fun concurrentWritersGetDistinctRevisions() {
  val s = StateStore(); val seen = java.util.concurrent.ConcurrentLinkedQueue<Long>(); s.addListener { seen += it }
  val pool = java.util.concurrent.Executors.newFixedThreadPool(8)
  repeat(8) { t -> pool.execute { repeat(1000) { i -> s.put(StateStore.SECTIONS[t], JSONObject().put("i", i)) } } }
  pool.shutdown(); assertTrue(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS))
  assertEquals(8000L, s.revision()); assertEquals(8000, seen.toSet().size)
  assertEquals(1, s.snapshot(7999L).keySet().size - 1)
}
// RevisionThrottleTest — relógio e agenda manuais
private class FakeClock { var t = 0L }
private class ManualScheduler(val clock: FakeClock) { val tasks = mutableListOf<Pair<Long, () -> Unit>>()
  fun schedule(d: Long, task: () -> Unit) { tasks += (clock.t + d) to task }
  fun runDue() { val due = tasks.filter { it.first <= clock.t }; tasks.removeAll(due); due.forEach { it.second() } } }
@Test fun throttleCapsAtTenHzAndDeliversLast() {
  val clock = FakeClock(); val sch = ManualScheduler(clock); val out = mutableListOf<Long>()
  val th = RevisionThrottle(100L, { clock.t }, sch::schedule) { out += it }
  for (r in 1L..1000L) { clock.t = r - 1; th.onRevision(r); sch.runDue() }
  clock.t = 1200; sch.runDue()
  assertTrue("emissões=${out.size}", out.size <= 11)
  assertEquals(1000L, out.last())
  assertEquals(out.sorted(), out); assertEquals(out.distinct(), out)
}
@Test fun firstRevisionGoesOutImmediately() {
  val clock = FakeClock(); val sch = ManualScheduler(clock); val out = mutableListOf<Long>()
  RevisionThrottle(100L, { clock.t }, sch::schedule) { out += it }.onRevision(7L); sch.runDue()
  assertEquals(listOf(7L), out)
}
```

- [ ] **Step 2: Run to see it fail**

Run: `git push -u origin work/platina-f5-autoridade-unica && gh pr create --draft --base OmegasPlatina --title "F5: autoridade única (Omegas)" --body "WIP" ; tools/ci/remote-test.sh gradle "com.omegas.prohub.state.*"`
Expected: `REMOTE_TEST=FAIL` com `Unresolved reference: StateStore`.

- [ ] **Step 3: Implement**

Assinaturas acima. `StateStore`: `synchronized(lock)`; por seção guarda `(String serializado, revisão)`; `snapshot` monta o `JSONObject` a partir das strings guardadas (`JSONObject(raw)` / `JSONArray(raw)`); listeners numa `CopyOnWriteArrayList`, chamados depois de sair do lock. `RevisionThrottle`: `@Synchronized onRevision`: `pending = max(pending, r)`; se nada agendado → agenda com `delay = max(0, lastEmitAt + minIntervalMs - now())`; a tarefa agendada zera o agendamento, emite `pending` se `> lastEmitted`, grava `lastEmitAt = now()`.

- [ ] **Step 4: Run to verify**

Run: `tools/ci/remote-test.sh gradle "com.omegas.prohub.state.*"`
Expected: `REMOTE_TEST=PASS`.

- [ ] **Step 5: Commit** `feat(state): contratos Intent/OpStage/FailureKind, StateStore com revisão e RevisionThrottle ≤10 Hz`

---

### Task 5.2: `OperationQueue` e `FailureClassifier`

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/state/OperationQueue.kt`
- Create: `app/src/main/java/com/omegas/prohub/state/FailureClassifier.kt`
- Test: `app/src/test/java/com/omegas/prohub/state/OperationQueueTest.kt`, `app/src/test/java/com/omegas/prohub/state/FailureClassifierTest.kt`

**Interfaces:**
- Consumes: `StateStore`, `Intent`, `OpStage`, `FailureKind` (5.1); `Snapshotter`/`Photo` (5.3 — declarar aqui só a interface mínima `fun interface PhotoBook { fun has(id: String): Boolean }`, que o `Snapshotter` implementa na 5.3).
- Produces:
```kotlin
class OperationFailure(val kind: FailureKind, message: String, val details: JSONObject = JSONObject()) : Exception(message)
fun interface IntentHandler { fun run(ctx: OperationContext): JSONObject }   // devolve "result"; lança para falhar
class OperationContext(val receiptId: String, val intent: Intent, val payload: JSONObject) {
  fun stage(stage: OpStage, message: String)          // só PREPARANDO/EXECUTANDO/CONFERINDO; publica na hora
  fun photo(photoId: String, bytes: JSONObject)        // registra a foto no recibo (bytes para perícia)
  fun sent(details: JSONObject); fun readback(details: JSONObject); fun managerStatus(status: JSONObject); fun legacy(view: JSONObject)
}
object IntentPayloads { fun validate(intent: Intent, payload: JSONObject): String? }  // null = válido; senão mensagem humana
class OperationQueue(
  private val store: StateStore,
  private val handlers: Map<Intent, IntentHandler>,
  private val photos: PhotoBook,
  private val receiptSink: (JSONObject) -> Unit,                 // produção: sessionRecorder.record("operation_receipt","operation",it,force=true)
  private val clock: () -> Long = System::currentTimeMillis,
) {
  fun submit(intent: Intent, payload: JSONObject): String        // receiptId "OP-<epochMs>-<seq>"; publica RECEBIDO em < 1 ms; nunca lança
  fun observe(name: String, block: () -> Unit)                    // mesma thread, sem recibo; exceção só vai para o log do store
  fun receipt(receiptId: String): JSONObject?
  fun close()
}
object FailureClassifier {
  fun of(error: Throwable): FailureKind                           // OperationFailure→kind; IOException/TimeoutException→TRANSPORTE; resto→APP
  fun ofMessage(message: String?): FailureKind
  fun ofAutoCalReason(reasonCode: String): FailureKind
  fun ofSafetyCode(code: String): FailureKind
  fun nextAction(kind: FailureKind, hasPhoto: Boolean): String
}
```
- Seção `operation` (publicada a cada mudança): `{"current": Receipt|null, "last": Receipt|null, "pending": [receiptId…], "history": [≤20 Receipts, mais novo primeiro], "legacy": {"curve": {…}, "map": {…}, "autocal": {…}}}`.
- `Receipt` (JSON): `receiptId, intent, payload, stage, message, failureKind|null, nextAction|null, photoId|null, undoable, undoOf|null, sent, readback, managerStatus, result, receivedAt, startedAt, finishedAt, durationMs`.
- Payloads (validados por `IntentPayloads`): `CURVE_WRITE {points:[{index:0..29,targetRaw:Int}], reason?}` · `CURVE_RESET {}` · `CURVE_RESTORE {backupId}` · `MAP_WRITE {cells:[{row,column,target}], reason?}` · `REFERENCE_FREEZE {}` · `AUTOCAL_RELEARN {points:[{fuel:"GAS"|"PETROL",index:0..17}]}` · `AUTOCAL_PAUSE|RESUME|RESET_GAS|RESET_PETROL {}` · `SESSION_EXPORT {kind:"session"|"logs"|"data", sessionId?}` · `OVERLAY_TOGGLE {enabled}` · `SETTINGS_SET {key, value}` · `UNDO {photoId}`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// OperationQueueTest
private fun queue(handlers: Map<Intent, IntentHandler>, sink: MutableList<JSONObject> = mutableListOf(), store: StateStore = StateStore()) =
  OperationQueue(store, handlers, { false }, { sink += it })
private fun awaitStage(q: OperationQueue, id: String, vararg st: OpStage) { val end = System.currentTimeMillis() + 5_000
  while (System.currentTimeMillis() < end) { if (q.receipt(id)?.optString("stage") in st.map { it.name }) return; Thread.sleep(5) }
  fail("recibo $id não chegou a ${st.toList()}: ${q.receipt(id)}") }

@Test fun secondSubmitWaitsForFirst() {
  val gate = java.util.concurrent.CountDownLatch(1); val running = java.util.concurrent.atomic.AtomicInteger(); val maxRunning = java.util.concurrent.atomic.AtomicInteger()
  val h = IntentHandler { maxRunning.accumulateAndGet(running.incrementAndGet(), ::maxOf); if (it.intent == Intent.CURVE_WRITE) gate.await(); running.decrementAndGet(); JSONObject() }
  val q = queue(mapOf(Intent.CURVE_WRITE to h, Intent.MAP_WRITE to h))
  val a = q.submit(Intent.CURVE_WRITE, JSONObject("""{"points":[{"index":0,"targetRaw":16500}]}"""))
  val b = q.submit(Intent.MAP_WRITE, JSONObject("""{"cells":[{"row":0,"column":0,"target":130}]}"""))
  Thread.sleep(200)
  assertEquals("RECEBIDO", q.receipt(b)!!.getString("stage"))
  gate.countDown(); awaitStage(q, b, OpStage.CONCLUIDO)
  assertEquals("CONCLUIDO", q.receipt(a)!!.getString("stage")); assertEquals(1, maxRunning.get())
}
@Test fun anyThrowableBecomesFailedAndQueueSurvives() {
  val sink = mutableListOf<JSONObject>()
  val q = queue(mapOf(
    Intent.CURVE_RESET to IntentHandler { throw NotImplementedError("x") },
    Intent.MAP_WRITE to IntentHandler { throw org.json.JSONException("bad") },
    Intent.OVERLAY_TOGGLE to IntentHandler { JSONObject().put("ok", true) }), sink)
  val a = q.submit(Intent.CURVE_RESET, JSONObject()); val b = q.submit(Intent.MAP_WRITE, JSONObject("""{"cells":[{"row":0,"column":0,"target":130}]}"""))
  val c = q.submit(Intent.OVERLAY_TOGGLE, JSONObject().put("enabled", true))
  awaitStage(q, c, OpStage.CONCLUIDO)
  for (id in listOf(a, b)) { val r = q.receipt(id)!!; assertEquals("FALHOU", r.getString("stage")); assertEquals("APP", r.getString("failureKind")) }
  assertEquals(3, sink.size)   // um recibo por operação, na sessão
}
@Test fun ioExceptionIsTransportAndStagesArePublishedInOrder() {
  val store = StateStore(); val stages = mutableListOf<String>()
  store.addListener { store.snapshot(0).optJSONObject("operation")?.optJSONObject("current")?.optString("stage")?.let { s -> if (stages.lastOrNull() != s) stages += s } }
  val q = queue(mapOf(Intent.CURVE_RESET to IntentHandler { it.stage(OpStage.PREPARANDO, "foto"); it.stage(OpStage.EXECUTANDO, "gravando"); throw java.io.IOException("Broken pipe") }), store = store)
  val id = q.submit(Intent.CURVE_RESET, JSONObject()); awaitStage(q, id, OpStage.FALHOU)
  assertEquals("TRANSPORTE", q.receipt(id)!!.getString("failureKind"))
  assertEquals(listOf("RECEBIDO", "PREPARANDO", "EXECUTANDO"), stages.take(3))
}
@Test fun invalidPayloadIsRejectedByValidator() {
  assertNotNull(IntentPayloads.validate(Intent.CURVE_WRITE, JSONObject("""{"points":[{"index":30,"targetRaw":1}]}""")))
  assertNotNull(IntentPayloads.validate(Intent.UNDO, JSONObject()))
  assertNull(IntentPayloads.validate(Intent.AUTOCAL_RELEARN, JSONObject("""{"points":[{"fuel":"GAS","index":17}]}""")))
}
@Test fun receiptHasSpecFields() { /* handler: ctx.photo("OP-1", …); ctx.sent(…); ctx.readback(…) → recibo tem
  intent, photoId, sent, readback, durationMs ≥ 0, result, stage=CONCLUIDO, undoable=true */ }

// FailureClassifierTest — mensagens reais dos managers/serial
@Test fun messagesFromRealSourcesClassify() {
  val t = FailureKind.TRANSPORTE; val e = FailureKind.ECU; val a = FailureKind.APP
  mapOf(
    "USB desconectado" to t,                                            // UsbSerialManager.kt:368, KFactorManager.kt:563
    "USB em recuperação transitória" to t,                              // UsbSerialManager.kt:358
    "Sessão USB mudou durante escrita K factor[3]" to t,                // UsbSerialManager.kt:403
    "Timeout aguardando status" to t, "Eco divergente ou incompleto" to t,
    "Resposta incompleta: 3/9 bytes" to t, "Checksum RX inválido: esperado 1A, recebido 1B" to t,
    "Tempo esgotado esperando a ECU" to t,
    "A confirmação final da curva divergiu" to e,                       // KFactorManager.kt:464
    "Ponto 3: esperado 16384, encontrado 16000" to e,                   // KFactorManager.kt:423
    "ECU retornou status 0xCA" to e, "ACK inválido em escrita do ponto 2" to e,
    "A curva da ECU mudou. Leia novamente antes de aplicar" to e,
    "Outra operação de calibração está em andamento" to a,
    "Leia a curva K factor nesta conexão antes de aplicar" to a, null to a,
  ).forEach { (msg, kind) -> assertEquals(msg, kind, FailureClassifier.ofMessage(msg)) }
}
@Test fun autoCalReasonsAndSafetyCodes() {
  assertEquals(FailureKind.TRANSPORTE, FailureClassifier.ofAutoCalReason("USB_DISCONNECTED"))
  assertEquals(FailureKind.TRANSPORTE, FailureClassifier.ofAutoCalReason("USB_SESSION_CHANGED"))
  assertEquals(FailureKind.ECU, FailureClassifier.ofAutoCalReason("READBACK_MISMATCH"))
  assertEquals(FailureKind.ECU, FailureClassifier.ofAutoCalReason("ECU_ACK_MISSING"))
  assertEquals(FailureKind.APP, FailureClassifier.ofAutoCalReason("CALIBRATION_BUSY"))
  for (c in listOf("USB_DISCONNECTED", "USB_PERMISSION_PENDING", "ENGINE_UNSAFE", "TELEMETRY_STALE")) assertEquals(FailureKind.TRANSPORTE, FailureClassifier.ofSafetyCode(c))
  assertEquals(FailureKind.APP, FailureClassifier.ofSafetyCode("SERVICE_UNAVAILABLE"))
  assertEquals("Verifique o cabo. Quando a ECU voltar, toque em Desfazer.", FailureClassifier.nextAction(FailureKind.TRANSPORTE, true))
  assertEquals("Verifique o cabo e tente de novo.", FailureClassifier.nextAction(FailureKind.TRANSPORTE, false))
  assertEquals("A ECU não confirmou. Ela será relida antes de qualquer nova tentativa.", FailureClassifier.nextAction(FailureKind.ECU, false))
  assertEquals("Erro interno do app. Veja os detalhes técnicos.", FailureClassifier.nextAction(FailureKind.APP, false))
}
```

- [ ] **Step 2: Run to see it fail** — Run: `tools/ci/remote-test.sh gradle "com.omegas.prohub.state.*"` · Expected: `REMOTE_TEST=FAIL` com `Unresolved reference: OperationQueue`.

- [ ] **Step 3: Implement**

`OperationQueue`: `Executors.newSingleThreadExecutor` (thread daemon `omegas-operation-queue`); `submit` cria o recibo (`RECEBIDO`), põe em `pending`, publica `operation`, enfileira. No worker: `startedAt`; handler ausente → `FALHOU/APP` "Intent sem handler"; `try { result = handler.run(ctx); CONCLUIDO } catch (t: Throwable) { FALHOU; failureKind = FailureClassifier.of(t); message = t.message ?: "Falha inesperada"; nextAction = nextAction(kind, photoId != null && photos.has(photoId)) }`; `undoable = stage == CONCLUIDO && photoId != null` (para `FALHOU` com foto: `undoable = true` também — Review Focus 2); `receiptSink(recibo)` dentro de `try/catch`. `ofMessage`: minúsculas; testa primeiro a lista de transporte (`usb desconectado`, `recuperação transitória`, `sessão usb mudou`, `timeout`, `tempo esgotado`, `eco divergente`, `resposta incompleta`, `resposta sem campo`, `checksum rx`, `tamanho de resposta`, `falha serial`), depois ECU (`divergiu`, `readback`, `ecu retornou status`, `ack inválido`, `não confirmou`, `curva da ecu mudou`, `encontrado`, `não persistiu`, `resposta inesperada`), senão `APP`.

- [ ] **Step 4: Run to verify** — `tools/ci/remote-test.sh gradle "com.omegas.prohub.state.*"` · Expected: `REMOTE_TEST=PASS`.

- [ ] **Step 5: Commit** `feat(state): OperationQueue serial com recibo e FailureClassifier transporte/ECU/app`

---

### Task 5.3: `Snapshotter` (fotos para `UNDO`)

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/state/Snapshotter.kt`
- Modify: `app/src/main/java/com/omegas/prohub/state/OperationQueue.kt` (`PhotoBook` passa a ser implementado por `Snapshotter`)
- Test: `app/src/test/java/com/omegas/prohub/state/SnapshotterTest.kt`

**Interfaces:**
- Produces:
```kotlin
enum class PhotoKind { CURVE, MAP, ACQUISITION, ACQUISITION_AND_CURVE, REFERENCE }
data class Photo(val id: String, val intent: Intent, val kind: PhotoKind, val takenAt: Long,
  val curveAxisRaw: IntArray? = null, val curveFactorsRaw: IntArray? = null,   // MUL_ACT Q14, 30
  val mapRows: List<IntArray>? = null,                                        // 12 × 12
  val acquisition: JSONObject? = null, val reference: JSONObject? = null) { fun toJson(): JSONObject }
class Snapshotter : PhotoBook {
  companion object { fun kindFor(intent: Intent): PhotoKind? }   // tabela spec §2.2
  fun put(photo: Photo); fun get(id: String): Photo?; override fun has(id: String): Boolean
  fun endSession()                                                // apaga tudo; chamado só no onDestroy do serviço
}
```
Tabela de `kindFor`: `CURVE_WRITE|CURVE_RESET|CURVE_RESTORE → CURVE` · `MAP_WRITE → MAP` · `AUTOCAL_RELEARN → ACQUISITION` · `AUTOCAL_RESET_GAS|AUTOCAL_RESET_PETROL → ACQUISITION_AND_CURVE` · `REFERENCE_FREEZE → REFERENCE` · os demais (`AUTOCAL_PAUSE`, `AUTOCAL_RESUME`, `SESSION_EXPORT`, `OVERLAY_TOGGLE`, `SETTINGS_SET`, `UNDO`) → `null`.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun photoPolicyMatchesSpecTable() {
  val expected = mapOf(Intent.CURVE_WRITE to PhotoKind.CURVE, Intent.CURVE_RESET to PhotoKind.CURVE, Intent.CURVE_RESTORE to PhotoKind.CURVE,
    Intent.MAP_WRITE to PhotoKind.MAP, Intent.AUTOCAL_RELEARN to PhotoKind.ACQUISITION,
    Intent.AUTOCAL_RESET_GAS to PhotoKind.ACQUISITION_AND_CURVE, Intent.AUTOCAL_RESET_PETROL to PhotoKind.ACQUISITION_AND_CURVE,
    Intent.REFERENCE_FREEZE to PhotoKind.REFERENCE)
  Intent.values().forEach { assertEquals(it.name, expected[it], Snapshotter.kindFor(it)) }
}
@Test fun photoIsKeptByteForByteUntilSessionEnd() {
  val s = Snapshotter(); val raw = IntArray(30) { 16384 + it * 7 }
  s.put(Photo("OP-1", Intent.CURVE_WRITE, PhotoKind.CURVE, 1L, IntArray(30) { 512 + it }, raw.copyOf()))
  raw[0] = 0   // a foto não pode ser alias do array de quem chamou
  assertArrayEquals(IntArray(30) { 16384 + it * 7 }, s.get("OP-1")!!.curveFactorsRaw)
  assertTrue(s.has("OP-1")); s.endSession(); assertFalse(s.has("OP-1")); assertNull(s.get("OP-1"))
}
@Test fun photoJsonCarriesRawBytes() {
  val j = Photo("OP-2", Intent.MAP_WRITE, PhotoKind.MAP, 5L, mapRows = List(12) { r -> IntArray(12) { c -> 100 + r + c } }).toJson()
  assertEquals("MAP", j.getString("kind")); assertEquals(12, j.getJSONArray("mapRows").length()); assertEquals(122, j.getJSONArray("mapRows").getJSONArray(11).getInt(11))
}
@Test fun queueMarksUndoableOnlyWhenPhotoExists() { /* OperationQueue com Snapshotter real: handler que faz snapshotter.put + ctx.photo → undoable=true;
  handler sem foto → undoable=false; depois de endSession(), FALHOU com aquele photoId → nextAction = nextAction(kind,false) */ }
```

- [ ] **Step 2: Run to see it fail** — `tools/ci/remote-test.sh gradle "com.omegas.prohub.state.SnapshotterTest"` · Expected: `REMOTE_TEST=FAIL` (`Unresolved reference: Snapshotter`).

- [ ] **Step 3: Implement** — `ConcurrentHashMap<String, Photo>`; `put` copia os arrays (`copyOf`) e as listas; `toJson` serializa todos os campos não nulos.

- [ ] **Step 4: Run to verify** — mesmo comando → `REMOTE_TEST=PASS`.

- [ ] **Step 5: Commit** `feat(state): Snapshotter com fotos por intent (spec §2.2) e expiração no fim da sessão`

---

### Task 5.4: Curva K pela fila — `CURVE_WRITE`, `CURVE_RESET`, `CURVE_RESTORE`, `UNDO` de curva

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/state/handlers/CurvePort.kt` (`CurvePort`, `KFactorCurvePort`, `StatusWaiter`)
- Create: `app/src/main/java/com/omegas/prohub/state/handlers/CurveHandlers.kt`
- Create: `app/src/main/java/com/omegas/prohub/state/handlers/LegacyOperationView.kt`
- Test: `app/src/test/java/com/omegas/prohub/state/handlers/CurveHandlersTest.kt`, `app/src/test/java/com/omegas/prohub/state/handlers/FakeKFactor.kt` (fake que imita o contrato de status de `KFactorManager`)

**Interfaces:**
- Consumes: `KFactorManager.readCurve(): JSONObject` (`KFactorManager.kt:167-199`, devolve `axisRaw`/`factorsRaw` e preenche o cache da conexão), `startBatchWrite(points: JSONArray, reason: String): JSONObject` (`:333-381`, assíncrono), `statusJson(): String` (`:60`; estados `BATCH_QUEUED`, `VERIFYING_CURRENT`, `WRITING_POINT`, `VERIFYING_FINAL`, `BATCH_CONFIRMED` com `details.curve`, `BATCH_FAILED`), `prepareRestore(fileName): JSONObject` (`:273-330`); `CalibrationWriteSafetyPolicy.evaluate(status).code/reason` (`CalibrationWriteSafetyPolicy.kt:23-36`).
- Produces:
```kotlin
data class CurveRead(val axisRaw: IntArray, val factorsRaw: IntArray, val json: JSONObject)
data class CurveChange(val index: Int, val currentRaw: Int, val targetRaw: Int)
interface CurvePort {
  fun read(): CurveRead                                                     // falha → OperationFailure(FailureClassifier.ofMessage(erro))
  fun write(changes: List<CurveChange>, reason: String, ctx: OperationContext): CurveRead   // espera BATCH_CONFIRMED; devolve details.curve
  fun restorePlan(backupId: String): Pair<CurveRead, IntArray>              // (curva atual, fatores do backup) via prepareRestore
}
class KFactorCurvePort(
  private val readCurve: () -> JSONObject, private val startBatchWrite: (JSONArray, String) -> JSONObject,
  private val status: () -> JSONObject, private val prepareRestore: (String) -> JSONObject,
  private val waiter: StatusWaiter = StatusWaiter(),
) : CurvePort
class StatusWaiter(private val timeoutMs: Long = 120_000L, private val pollMs: Long = 80L,
  private val clock: () -> Long = System::currentTimeMillis, private val sleep: (Long) -> Unit = Thread::sleep) {
  fun await(status: () -> JSONObject, terminal: Set<String>, onProgress: (JSONObject) -> Unit): JSONObject  // estoura → OperationFailure(TRANSPORTE,"Tempo esgotado esperando a ECU")
}
class CurveHandlers(private val port: CurvePort, private val photos: Snapshotter, private val safety: () -> CalibrationWriteSafetyPolicy.Decision, private val clock: () -> Long = System::currentTimeMillis) {
  val write: IntentHandler; val reset: IntentHandler; val restore: IntentHandler
  fun undo(ctx: OperationContext, photo: Photo): JSONObject                 // chamado pelo despachante de UNDO (Task 5.6)
}
object LegacyOperationView {   // formato que curve.js/refino.js/map.js já leem (ex-V7JavascriptBridge.kt:274-283 e :296-323)
  fun curveProgress(managerStatus: JSONObject, startedAt: Long, total: Int): JSONObject   // {ok, state:"CURVE_WRITING", writerState, busy:true, progress,…}
  fun curveFinal(managerStatus: JSONObject?, error: String?, startedAt: Long, finishedAt: Long): JSONObject  // confirmado → state BATCH_CONFIRMED, readbackValid true, busy false; senão CURVE_WRITE_FAILED
}
```
Mapeamento de estágio: `readCurve`/foto → `PREPARANDO`; `BATCH_QUEUED|VERIFYING_CURRENT|WRITING_POINT` → `EXECUTANDO`; `VERIFYING_FINAL` → `CONFERINDO`. Depois de `BATCH_CONFIRMED` o handler confere `readback.factorsRaw` contra o alvo esperado (30 posições); diferente → `OperationFailure(ECU, "Readback não bate com o pedido")`.

- [ ] **Step 1: Write the failing tests**

`FakeKFactor` guarda `ecu: IntArray(30)`, `axis: IntArray(30)`, `connected`, `dropAfterPoints: Int?`, `corruptReadback: Boolean`; `startBatchWrite` valida `currentRaw == ecu[index]` como `KFactorManager.kt:420-424` e escreve num thread, publicando a mesma sequência de estados; com `dropAfterPoints = n` escreve n pontos e termina em `BATCH_FAILED` com mensagem `"USB desconectado"` (o que `UsbSerialManager.protocolTransaction` devolve quando a porta some, `UsbSerialManager.kt:368-369`); `corruptReadback` termina em `BATCH_FAILED` "A confirmação final da curva divergiu".

```kotlin
private val base = IntArray(30) { 16384 + it * 11 }
private fun ctx(intent: Intent, payload: String) = OperationContext("OP-1", intent, JSONObject(payload))
@Test fun curveWritePhotographsThenWritesAndConfirms() {
  val k = FakeKFactor(base.copyOf()); val snaps = Snapshotter(); val h = CurveHandlers(k.port(), snaps, { CalibrationWriteSafetyPolicy.Decision(true) })
  h.write.run(ctx(Intent.CURVE_WRITE, """{"points":[{"index":4,"targetRaw":17000},{"index":5,"targetRaw":17100}]}"""))
  assertArrayEquals(base, snaps.get("OP-1")!!.curveFactorsRaw)
  assertEquals(17000, k.ecu[4]); assertEquals(17100, k.ecu[5]); assertEquals(base[6], k.ecu[6])
}
@Test fun readbackMismatchIsEcuFailure() {
  val k = FakeKFactor(base.copyOf()).apply { corruptReadback = true }
  val e = assertThrows(OperationFailure::class.java) { CurveHandlers(k.port(), Snapshotter(), { CalibrationWriteSafetyPolicy.Decision(true) })
    .write.run(ctx(Intent.CURVE_WRITE, """{"points":[{"index":0,"targetRaw":17000}]}""")) }
  assertEquals(FailureKind.ECU, e.kind)
}
@Test fun cableDropMidWriteKeepsPhotoAndUndoRestoresAfterReconnect() {
  val k = FakeKFactor(base.copyOf()).apply { dropAfterPoints = 2 }; val snaps = Snapshotter()
  val q = OperationQueue(StateStore(), mapOf(Intent.CURVE_WRITE to CurveHandlers(k.port(), snaps, { CalibrationWriteSafetyPolicy.Decision(true) }).write,
    Intent.UNDO to UndoProbe(snaps, k)), snaps, {})
  val id = q.submit(Intent.CURVE_WRITE, JSONObject("""{"points":[${(0 until 5).joinToString(",") { """{"index":$it,"targetRaw":${17000 + it}}""" }}]}"""))
  awaitStage(q, id, OpStage.FALHOU)
  val r = q.receipt(id)!!
  assertEquals("TRANSPORTE", r.getString("failureKind")); assertTrue(r.getBoolean("undoable"))
  assertEquals("Verifique o cabo. Quando a ECU voltar, toque em Desfazer.", r.getString("nextAction"))
  assertEquals(17000, k.ecu[0]); assertEquals(17001, k.ecu[1]); assertEquals(base[2], k.ecu[2])   // parcial, como na ECU real
  k.dropAfterPoints = null   // cabo voltou
  val u = q.submit(Intent.UNDO, JSONObject().put("photoId", id)); awaitStage(q, u, OpStage.CONCLUIDO)
  assertArrayEquals(base, k.ecu)
  assertEquals(2, k.lastBatch.size)   // só os pontos divergentes foram regravados
}
@Test fun undoRestoresPhotoByteForByte() {
  val k = FakeKFactor(base.copyOf()); val snaps = Snapshotter(); val h = CurveHandlers(k.port(), snaps, { CalibrationWriteSafetyPolicy.Decision(true) })
  h.write.run(ctx(Intent.CURVE_WRITE, """{"points":[${(0 until 30).joinToString(",") { """{"index":$it,"targetRaw":${20000 + it}}""" }}]}"""))
  h.undo(OperationContext("OP-2", Intent.UNDO, JSONObject().put("photoId", "OP-1")), snaps.get("OP-1")!!)
  assertArrayEquals(base, k.readMulAct())   // IntArray cru de MUL_ACT, 30 pontos
}
@Test fun resetTargetsQ14OneOnlyWhereDifferent() { /* base com ecu[3]=16384 → lastBatch não contém índice 3; todos ficam 16384 */ }
@Test fun restoreWritesBackupFactors() { /* FakeKFactor.backups["MANUAL-1.json"] = IntArray(30){18000}; CURVE_RESTORE {backupId} → ecu == 18000×30; foto == base */ }
@Test fun safetyBlockIsClassifiedBeforeTouchingEcu() {
  val k = FakeKFactor(base.copyOf())
  val e = assertThrows(OperationFailure::class.java) { CurveHandlers(k.port(), Snapshotter(), { CalibrationWriteSafetyPolicy.Decision(false, "Conecte a ECU antes de gravar", "USB_DISCONNECTED") })
    .write.run(ctx(Intent.CURVE_WRITE, """{"points":[{"index":0,"targetRaw":17000}]}""")) }
  assertEquals(FailureKind.TRANSPORTE, e.kind); assertEquals(0, k.reads)
}
@Test fun statusWaiterTimesOutAsTransport() { /* clock fake avança 121_000 ms; status sempre WRITING_POINT → OperationFailure(TRANSPORTE,"Tempo esgotado esperando a ECU") */ }
@Test fun legacyViewMatchesOldBridgeShape() {
  val ok = LegacyOperationView.curveFinal(JSONObject().put("state", "BATCH_CONFIRMED").put("details", JSONObject().put("readbackValid", true)), null, 1L, 2L)
  assertEquals("BATCH_CONFIRMED", ok.getString("state")); assertTrue(ok.getBoolean("readbackValid")); assertFalse(ok.getBoolean("busy")); assertTrue(ok.getBoolean("ok"))
  val bad = LegacyOperationView.curveFinal(null, "USB desconectado", 1L, 2L)
  assertEquals("CURVE_WRITE_FAILED", bad.getString("state")); assertEquals("USB desconectado", bad.getString("error")); assertFalse(bad.getBoolean("ok"))
}
```
(`UndoProbe` é um `IntentHandler` de teste que faz `h.undo(ctx, snaps.get(photoId)!!)`; o despachante real nasce na 5.6.)

- [ ] **Step 2: Run to see it fail** — `tools/ci/remote-test.sh gradle "com.omegas.prohub.state.handlers.CurveHandlersTest"` · Expected: `REMOTE_TEST=FAIL` (`Unresolved reference: CurveHandlers`).

- [ ] **Step 3: Implement**

Cada handler: (1) `safety()` bloqueado → `OperationFailure(FailureClassifier.ofSafetyCode(code), reason)`; (2) `PREPARANDO`: `port.read()` (para `RESTORE`: `port.restorePlan(backupId)`), `photos.put(Photo(ctx.receiptId, intent, CURVE, …))`, `ctx.photo(id, photo.toJson())`; (3) monta `CurveChange` a partir da foto (`WRITE`: pontos do payload com `currentRaw = foto[index]`, ignora iguais; `RESET`: alvo `16384` onde diferente; `RESTORE`: fatores do backup onde diferente); lista vazia → `CONCLUIDO` com `result.message = "A ECU já está igual"`; (4) `port.write` → `ctx.sent`, `ctx.readback`, `ctx.managerStatus`, `ctx.legacy(LegacyOperationView…)`; (5) confere readback. `undo`: `port.read()`; eixo diferente da foto → `OperationFailure(ECU, "O eixo da curva mudou; Desfazer bloqueado")`; muda só onde `atual != foto`; confere `readback == foto` (byte a byte). `KFactorCurvePort.write` chama `startBatchWrite`; `ok=false` na partida → `OperationFailure(ofMessage(error), error)`; espera `BATCH_CONFIRMED|BATCH_FAILED`; `BATCH_FAILED` → `OperationFailure(ofMessage(message), message, details)`. `LegacyOperationView`: copiar sem mudança a montagem de `lastOperation` de `CalibrationOperationsBridge.startCurveBatchWrite` (ex-`V7JavascriptBridge.kt:274-283` progresso, `:296-323` final), sem `suggestionReconciliation`.

- [ ] **Step 4: Run to verify** — `tools/ci/remote-test.sh gradle "com.omegas.prohub.state.handlers.CurveHandlersTest"` · Expected: `REMOTE_TEST=PASS`.

- [ ] **Step 5: Commit** `feat(state): Curva K pela fila com foto, readback e Desfazer byte a byte`

---

### Task 5.5: Mapa K pela fila — `MAP_WRITE` e `UNDO` de mapa

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/state/handlers/MapHandlers.kt` (`MapPort`, `KWriteMapPort`, `MapHandlers`)
- Modify: `app/src/main/java/com/omegas/prohub/state/handlers/LegacyOperationView.kt` (+ `mapProgress`, `mapFinal`)
- Test: `app/src/test/java/com/omegas/prohub/state/handlers/MapHandlersTest.kt`

**Interfaces:**
- Consumes: `KWriteManager.readFullMap(): JSONObject` (`KWriteManager.kt:170-235`, `rows` 12×12), `startBatchWrite(cells, maxStep, pauseMs, reason)` (`:276-319`; células `{row,column,current,target}`, `MIN_SAFE_K = 100`), `statusJson()` (estados `BATCH_QUEUED`, `BATCH_PREPARING`, `BATCH_CHECKING_ROWS`, `BATCH_VERIFYING_ROWS`, `BATCH_CONFIRMED` com `details.rows`, `BATCH_PARTIAL_FAILED`, `FAILED`).
- Produces:
```kotlin
data class MapRead(val rows: List<IntArray>, val json: JSONObject)
data class MapChange(val row: Int, val column: Int, val current: Int, val target: Int)
interface MapPort { fun read(): MapRead; fun write(changes: List<MapChange>, reason: String, ctx: OperationContext): MapRead }
class KWriteMapPort(private val readFullMap: () -> JSONObject, private val startBatchWrite: (JSONArray, Int, Int, String) -> JSONObject,
  private val status: () -> JSONObject, private val waiter: StatusWaiter = StatusWaiter()) : MapPort
class MapHandlers(private val port: MapPort, private val photos: Snapshotter, private val safety: () -> CalibrationWriteSafetyPolicy.Decision) {
  val write: IntentHandler; fun undo(ctx: OperationContext, photo: Photo): JSONObject }
```
Estágios: foto → `PREPARANDO`; `BATCH_QUEUED|BATCH_PREPARING|BATCH_CHECKING_ROWS` e escrita → `EXECUTANDO`; `BATCH_VERIFYING_ROWS` → `CONFERINDO`; terminal `BATCH_CONFIRMED` / `*FAILED`.

- [ ] **Step 1: Write the failing test** (fake `FakeKWrite` com `ecu: Array<IntArray>(12){IntArray(12){120}}`, mesma ideia do `FakeKFactor`)

```kotlin
@Test fun mapWritePhotographsAndConfirms() { /* MAP_WRITE {cells:[{row:2,column:3,target:131}]} → foto rows[2][3]==120; ecu[2][3]==131; lastBatch[0] == {row:2,column:3,current:120,target:131} */ }
@Test fun mapUndoRestoresAllCellsByteForByte() { /* 6 células mudadas → undo → ecu == foto em todas as 144 posições (contentEquals por linha) */ }
@Test fun mapTargetBelowSafeMinimumFailsAsAppWithoutWriting() { /* target 99 → OperationFailure(APP) com mensagem "Valor K alvo deve estar entre 100 e 255"; FakeKWrite.writes == 0 */ }
@Test fun partialFailureFromCableIsTransport() { /* BATCH_PARTIAL_FAILED "USB desconectado" → OperationFailure(TRANSPORTE); foto mantida */ }
@Test fun legacyMapFinalKeepsOldStates() {
  assertEquals("BATCH_CONFIRMED", LegacyOperationView.mapFinal(JSONObject().put("state","BATCH_CONFIRMED").put("details", JSONObject().put("readbackValid", true)), null, 1, 2).getString("state"))
  assertEquals("FAILED", LegacyOperationView.mapFinal(null, "USB desconectado", 1, 2).getString("state"))
}
```

- [ ] **Step 2: Run to see it fail** — `tools/ci/remote-test.sh gradle "com.omegas.prohub.state.handlers.MapHandlersTest"` · Expected: `REMOTE_TEST=FAIL`.

- [ ] **Step 3: Implement** — como 5.4; `current` vem da foto; `KWriteMapPort.write` passa `maxStep = 0`, `pauseMs = 0` (como `native-api.js:writeMap` hoje); `mapProgress/mapFinal`: copiar a montagem de `lastOperation` de `CalibrationOperationsBridge.startMapBatchWrite` (ex-`V7JavascriptBridge.kt:344-538`), mantendo `confirmedCells`/`totalCells`.

- [ ] **Step 4: Run to verify** — mesmo comando → `REMOTE_TEST=PASS`.

- [ ] **Step 5: Commit** `feat(state): Mapa K pela fila com foto e Desfazer`

---

### Task 5.6: AutoCal, Referência e o despachante de `UNDO`

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/state/handlers/AutoCalHandlers.kt` (`AutoCalActionPort`, `NativeActionPort`, `AutoCalHandlers`)
- Create: `app/src/main/java/com/omegas/prohub/state/handlers/ReferenceHandlers.kt`
- Create: `app/src/main/java/com/omegas/prohub/state/handlers/UndoHandler.kt`
- Test: `app/src/test/java/com/omegas/prohub/state/handlers/AutoCalHandlersTest.kt`, `.../ReferenceHandlersTest.kt`, `.../UndoHandlerTest.kt`

**Interfaces:**
- Consumes: `AutoCalNativeActionManager.prepare(actionName): JSONObject` (`AutoCalNativeActionManager.kt:139`), `preparePointDeletes(targets)` (`:157`), `execute(preparationId)` (`:241-271`, assíncrono), `statusJson()` (`:135`; estados `QUEUED`, `READING_BEFORE`, `SENDING_ACTION`, `READING_AFTER`, `VERIFYING_*`, `CONFIRMED`, `FAILED` com `reasonCode`), `AutoCalRecoveryPolicy` (`reasonCode`); `AutoCalPointDeleteProtocol.Target/Fuel.parse` (`AutoCalPointDeleteProtocol.kt:20-45`); F4: o corpo de `AutoCalJavascriptBridge.freezeReference()` (temporário) e a `ReferenceStore` que ele usa; `CurvePort`/`CurveHandlers.undo` (5.4), `MapHandlers.undo` (5.5).
- Produces:
```kotlin
interface AutoCalActionPort { fun prepare(action: String): JSONObject; fun prepareRelearn(targets: List<AutoCalPointDeleteProtocol.Target>): JSONObject
  fun execute(preparationId: String): JSONObject; fun status(): JSONObject; fun acquisitionSnapshot(): JSONObject }
class NativeActionPort(private val manager: AutoCalNativeActionManager, private val snapshot: () -> JSONObject) : AutoCalActionPort
class AutoCalHandlers(private val port: AutoCalActionPort, private val curve: CurvePort, private val photos: Snapshotter,
  private val safety: () -> CalibrationWriteSafetyPolicy.Decision, private val waiter: StatusWaiter = StatusWaiter()) {
  val relearn: IntentHandler; val pause: IntentHandler; val resume: IntentHandler; val resetGas: IntentHandler; val resetPetrol: IntentHandler }
class ReferenceHandlers(private val freeze: () -> JSONObject, private val current: () -> JSONObject?, private val restore: (JSONObject?) -> Unit, private val photos: Snapshotter) {
  val freezeHandler: IntentHandler; fun undo(ctx: OperationContext, photo: Photo): JSONObject }
class UndoHandler(private val photos: Snapshotter, private val curve: CurveHandlers, private val map: MapHandlers, private val reference: ReferenceHandlers) : IntentHandler
```
Ações: `PAUSE` → `prepare("DISABLE_AUTO_CAL")`+`execute`; `RESUME` → `ENABLE_AUTO_CAL`; `RESET_GAS` → `RESET_GAS`; `RESET_PETROL` → `RESET_PETROL`; `RELEARN` → `prepareRelearn(targets)` (= `DELETE_POINT`). Estágios: foto → `PREPARANDO`; `QUEUED|READING_BEFORE|SENDING_ACTION` → `EXECUTANDO`; `READING_AFTER|VERIFYING_*|READING_FINISH_SOURCE` → `CONFERINDO`; terminal `CONFIRMED`/`FAILED`; falha → `OperationFailure(FailureClassifier.ofAutoCalReason(status.reasonCode), status.message)`. Fotos: `RELEARN` → `ACQUISITION` (`acquisitionSnapshot()`); `RESET_*` → `ACQUISITION_AND_CURVE` (`acquisitionSnapshot()` + `curve.read()`); `REFERENCE_FREEZE` → `REFERENCE` (`current()` antes do congelamento; `null` vira `JSONObject.NULL`).
`UndoHandler`: foto ausente → `OperationFailure(APP, "Este Desfazer expirou com a sessão")`; `CURVE`/`ACQUISITION_AND_CURVE` → `curve.undo` (a resposta diz "A curva K voltou. O que a ECU reaprender depois é novo."); `MAP` → `map.undo`; `REFERENCE` → `reference.undo`; `ACQUISITION` → `OperationFailure(ECU, "A ECU não aceita devolver pontos apagados; ela vai reaprendê-los")` (ver "Cobertura da spec").

- [ ] **Step 1: Write the failing tests**

```kotlin
// AutoCalHandlersTest — FakeAutoCalPort imita AutoCalNativeActionManager: prepare devolve {ok,prepared,preparationId}; execute publica QUEUED→SENDING_ACTION→READING_AFTER→CONFIRMED
@Test fun pauseAndResumeUseOperationalToggle() { /* pause → port.prepared == ["DISABLE_AUTO_CAL"]; resume → ["ENABLE_AUTO_CAL"]; nenhuma foto */ }
@Test fun resetGasPhotographsAcquisitionAndCurve() { /* foto kind ACQUISITION_AND_CURVE com curveFactorsRaw.size==30 e acquisition != null; port.prepared == ["RESET_GAS"] */ }
@Test fun relearnSendsTargets() { /* {points:[{fuel:"GAS",index:3},{fuel:"PETROL",index:17}]} → port.relearnTargets == [Target(GAS,3), Target(PETROL,17)] */ }
@Test fun failedActionMapsRecoveryReason() {
  /* status final FAILED reasonCode USB_DISCONNECTED → TRANSPORTE; READBACK_MISMATCH → ECU; prepare {ok:false,error:"Outra ação AutoCal está em andamento"} → APP */
}
// ReferenceHandlersTest
@Test fun freezeKeepsPreviousReferenceAsPhotoAndUndoRestoresIt() {
  var cur: JSONObject? = JSONObject().put("id", "REF-A"); val restored = mutableListOf<JSONObject?>(); val snaps = Snapshotter()
  val h = ReferenceHandlers({ cur = JSONObject().put("id", "REF-B"); cur!! }, { cur }, { restored += it; cur = it }, snaps)
  h.freezeHandler.run(OperationContext("OP-9", Intent.REFERENCE_FREEZE, JSONObject()))
  assertEquals("REF-A", snaps.get("OP-9")!!.reference!!.getString("id"))
  h.undo(OperationContext("OP-10", Intent.UNDO, JSONObject().put("photoId", "OP-9")), snaps.get("OP-9")!!)
  assertEquals("REF-A", cur!!.getString("id"))
}
// UndoHandlerTest
@Test fun expiredPhotoFailsAsApp() { /* snaps.endSession(); UNDO {photoId:"OP-1"} → OperationFailure(APP,"Este Desfazer expirou com a sessão") */ }
@Test fun relearnUndoIsHonestlyRefused() { /* foto ACQUISITION → OperationFailure(ECU, "A ECU não aceita devolver pontos apagados; ela vai reaprendê-los") */ }
@Test fun resetUndoRestoresCurveFromPhoto() { /* foto ACQUISITION_AND_CURVE com base; FakeKFactor.ecu alterado → UNDO → ecu == base */ }
```

- [ ] **Step 2: Run to see it fail** — `tools/ci/remote-test.sh gradle "com.omegas.prohub.state.handlers.*"` · Expected: `REMOTE_TEST=FAIL` (`Unresolved reference: AutoCalHandlers`).

- [ ] **Step 3: Implement**

Antes: `grep -n "fun freezeReference" -A30 app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt` — o lambda `freeze` em produção (Task 5.9) é exatamente esse corpo, e `current`/`restore` usam a `ReferenceStore` da F4. Se a `ReferenceStore` não tiver um jeito de voltar à Referência anterior, adicionar nela `fun restore(previous: Reference?)` (substitui a atual pela anterior, ou nenhuma) com teste em `ReferenceStoreTest` (`restoreBringsBackPreviousReference`: congela A, congela B, `restore(A)` → `current().id == A.id`; `restore(null)` → `current() == null`). `NativeActionPort.execute` devolve a resposta do manager; `prepare` com `ok=false` → `OperationFailure(ofMessage(error))`; `prepared=false` também.

- [ ] **Step 4: Run to verify** — `tools/ci/remote-test.sh gradle "com.omegas.prohub.state.handlers.*"` e, se mexeu na `ReferenceStore`, `tools/ci/remote-test.sh gradle "com.omegas.prohub.equivalence.ReferenceStoreTest"` · Expected: `REMOTE_TEST=PASS`.

- [ ] **Step 5: Commit** `feat(state): AutoCal e Referência pela fila; Desfazer único por foto`

---

### Task 5.7: Intents verdes do app — `SESSION_EXPORT`, `OVERLAY_TOGGLE`, `SETTINGS_SET`

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/state/handlers/AppHandlers.kt` (`HostActions`, `AppHandlers`)
- Test: `app/src/test/java/com/omegas/prohub/state/handlers/AppHandlersTest.kt`

**Interfaces:**
- Consumes (produção, ligados na 5.9/5.10): `MainActivity.exportSession(sessionId)` (`MainActivity.kt:343`), `exportLogs()` (`:339`), `exportData()` (`:335`), `setGpsEnabled(enabled)` (`:374-394`), `requestBatteryOptimizationExemption(manual = true)` (`:428`); `TelemetryForegroundService.setTelemetryOverlayEnabled(enabled)` (`TelemetryForegroundService.kt:639-644`), `setTelemetryOverlayScale(scale)` (`:646-651`), `setLanEnabled(enabled)` (`:624-636`), `updateSessionRecorderSettings(...)` (`:587-600`); o fluxo de permissão do balão hoje em `PowerJavascriptBridge.requestOverlayPermissionAndEnable` (`PowerJavascriptBridge.kt:37-73`).
- Produces:
```kotlin
interface HostActions {                         // implementado pela MainActivity; null quando não há tela
  fun exportSession(sessionId: String); fun exportLogs(); fun exportData()
  fun setGpsEnabled(enabled: Boolean); fun requestBatteryExemption(); fun openOverlayPermission(): Boolean   // true se abriu a tela do Android
  fun overlayPermissionGranted(): Boolean
}
class AppHandlers(private val host: () -> HostActions?, private val overlayEnable: (Boolean) -> JSONObject, private val overlayScale: (Double) -> JSONObject,
  private val lanEnable: (Boolean) -> JSONObject, private val recorder: (JSONObject) -> JSONObject) {
  val export: IntentHandler; val overlay: IntentHandler; val settings: IntentHandler
  companion object { val SETTINGS_KEYS = setOf("recorder", "overlay.scale", "gps.enabled", "lan.enabled", "power.batteryExemption") }
}
```

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun exportKindsGoToHost() { /* kind session+sessionId "s1" → host.calls == ["exportSession:s1"]; logs → "exportLogs"; data → "exportData"; kind "x" → OperationFailure(APP) */ }
@Test fun exportWithoutScreenFails() { /* host() == null → OperationFailure(APP, "Abra o app para escolher onde salvar") */ }
@Test fun overlayToggleAsksPermissionWhenMissing() { /* overlayPermissionGranted=false, enabled=true → overlayEnable(true) chamado e openOverlayPermission chamado; result.permissionRequired == true */ }
@Test fun settingsKeysAreClosed() {
  /* "lan.enabled" true → lanEnable(true); "overlay.scale" 1.5 → overlayScale(1.5); "recorder" {...} → recorder(obj);
     "gps.enabled" false → host.setGpsEnabled(false); "power.batteryExemption" → host.requestBatteryExemption();
     "foo" → OperationFailure(APP, "Ajuste desconhecido: foo") */
  assertEquals(setOf("recorder", "overlay.scale", "gps.enabled", "lan.enabled", "power.batteryExemption"), AppHandlers.SETTINGS_KEYS)
}
```

- [ ] **Step 2: Run to see it fail** — `tools/ci/remote-test.sh gradle "com.omegas.prohub.state.handlers.AppHandlersTest"` · Expected: `REMOTE_TEST=FAIL`.

- [ ] **Step 3: Implement** — despacho por `kind`/`key`; `SESSION_EXPORT` conclui quando o seletor do Android foi aberto (`result.message = "Escolha onde salvar"`); o salvamento em si continua no `registerForActivityResult` da `MainActivity` (toast atual).

- [ ] **Step 4: Run to verify** — mesmo comando → `REMOTE_TEST=PASS`.

- [ ] **Step 5: Commit** `feat(state): exportar, balão e ajustes como intents verdes`

---

### Task 5.8: `StateSections` — as 11 seções em JSON

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/state/StateSections.kt`
- Test: `app/src/test/java/com/omegas/prohub/state/StateSectionsTest.kt`

**Interfaces:**
- Consumes: `HubStatus` (`model/HubStatus.kt:3-…`), `TelemetryStateStore.liveJson()` (formato atual), `LiveCellProjection.liveInterpolationJson(...)` (F1, mesmas assinaturas de `LearningGridProjection`), `EquivalenceResult`/`EquivalencePoint`/`NextAction`/`Reference` (F4, índice), `AutoCalUiProjection.project(nativeStatus, nativeSnapshot, manualStatus, manualSnapshot)` (`AutoCalJavascriptBridge.kt:60-73`).
- Produces (formatos que F6/F7 consomem):
```kotlin
object StateSections {
  fun connection(status: HubStatus, release: JSONObject, ecu: JSONObject, selfTest: JSONObject): JSONObject
  // {usbConnected, usbDevice, usbPermissionPending, engineRunning, engineReady, engineStuck, ecuState, fuelState, lastError,
  //  safety:{allowed, code, reason}, release, selfTest, ecu:{curve: <readCurve json>|null, map: <readFullMap json>|null, curveBackups:[…]}}
  fun live(liveRoot: JSONObject): JSONObject          // liveJson + ok:true + telemetryAgeMs (= ageMs)
  fun now(liveRoot: JSONObject): JSONObject           // LiveCellProjection.liveInterpolationJson(rpm, petrol_ms, load_bar, sequence, updatedAt, valid)
  fun reference(reference: Reference?, provisional: Boolean): JSONObject   // {present, id, frozenAt, ecuAcquisitionFingerprint, points:[{mapBar,petrolMs,maturity}], provisional}
  fun points(result: EquivalenceResult): JSONArray     // 30 objetos com exatamente os campos de EquivalencePoint (nomes do índice)
  fun index(result: EquivalenceResult): JSONObject     // {value|null, coverage, provisional}
  fun nextAction(action: NextAction): JSONObject       // {kind, text, route|null, subpage|null, pointIndexes}
  fun session(recorderStatus: JSONObject, list: JSONArray, logs: JSONArray): JSONObject   // {status, list, logs(≤ 200 últimos)}
  fun autocal(projection: JSONObject, action: JSONObject, phases: JSONObject, equivalenceView: JSONObject?): JSONObject
  fun settings(overlay: JSONObject, power: JSONObject, gps: Boolean, lan: JSONObject, recorder: JSONObject): JSONObject
}
```
Nota: `points` nasce `[]` (seção vazia) enquanto a F4 ainda não tem resultado.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun pointsHaveContractFieldsOnly() {
  val p = EquivalencePoint(0, 2.0, 1.0, 1.05, 0.05, 0.04, null, null, 0.01, 0.12, 42, setOf("TELEMETRIA"), PointState.POBRE)
  val arr = StateSections.points(syntheticResult(List(30) { p.copy(index = it) }))
  assertEquals(30, arr.length())
  assertEquals(setOf("index","axisMs","kCurrent","kTarget","mixture","tolerance","roughnessRatio","nearStallRatio","slope","usage","samples","sources","state"), arr.getJSONObject(0).keySet())
  assertEquals("POBRE", arr.getJSONObject(0).getString("state")); assertTrue(arr.getJSONObject(0).isNull("roughnessRatio"))
}
@Test fun indexNullStaysNull() { assertTrue(StateSections.index(syntheticResult(emptyList(), index = null)).isNull("value")) }
@Test fun connectionCarriesSafetyAndEcuTables() {
  val c = StateSections.connection(HubStatus(serviceRunning = true, usbConnected = false), JSONObject(), JSONObject().put("curve", JSONObject.NULL), JSONObject())
  assertEquals("USB_DISCONNECTED", c.getJSONObject("safety").getString("code")); assertFalse(c.getJSONObject("safety").getBoolean("allowed"))
}
@Test fun liveAndNowFromTelemetryRoot() {
  val root = JSONObject().put("valid", true).put("ageMs", 40).put("sequence", 9).put("updatedAt", 1000)
    .put("live", JSONObject().put("rpm", 2100).put("petrol_ms", 4.2).put("load_bar", 0.56))
  assertEquals(40, StateSections.live(root).getInt("telemetryAgeMs"))
  assertEquals(LiveCellProjection.liveInterpolationJson(2100.0, 4.2, 0.56, 9, 1000, true).toString(), StateSections.now(root).toString())
}
```
(`syntheticResult` monta um `EquivalenceResult` com `NextAction(NextActionKind.NOTHING, "Equivalente. Nada a fazer.", null, null, emptyList())`, curvas vazias e `proposal = null`.)

- [ ] **Step 2: Run to see it fail** — `tools/ci/remote-test.sh gradle "com.omegas.prohub.state.StateSectionsTest"` · Expected: `REMOTE_TEST=FAIL`.

- [ ] **Step 3: Implement** — antes, `grep -n "fun toJson\|fun json" app/src/main/java/com/omegas/prohub/equivalence/*.kt`: se a F4 já serializa algum desses tipos, delegar a ela (mesmos nomes de campo) em vez de duplicar. `safety` vem de `CalibrationWriteSafetyPolicy.evaluate(status)`.

- [ ] **Step 4: Run to verify** — mesmo comando → `REMOTE_TEST=PASS`.

- [ ] **Step 5: Commit** `feat(state): StateSections monta as 11 seções do snapshot`

---

### Task 5.9: `OmegasRuntime` no serviço — dono do store, da fila e do AutoCal

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/state/OmegasRuntime.kt` (`OmegasRuntime`, `StatePublisher`)
- Modify: `app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt` (`onCreate` `:147-295`, `onDestroy` `:314-338`, `handleUsbTransition` `:694-755`, `stateChanged` `:1013-1017`, `consumeEngineEvent` `:923-…`)
- Test: `app/src/test/java/com/omegas/prohub/state/OmegasRuntimeTest.kt`

**Interfaces:**
- Consumes: tudo de 5.1–5.8; `AutoCalNativeActionManager(...)` com os mesmos argumentos de `AutoCalJavascriptBridge.currentNativeManager` (`AutoCalJavascriptBridge.kt:384-424`); `service.nativeAutoCalStatusJson()`/`nativeAutoCalSnapshotJson()` (`:653-657`); `service.listKFactorBackups()` (`:461`); `runtime.selfTestJson()` (`NativeRuntimeManager.kt:213`); EquivalenceEngine (F4): o ponto onde o serviço recalcula o `EquivalenceResult` (achar com `grep -n "EquivalenceResult\|equivalenceEngine" app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt`).
- Produces:
```kotlin
class OmegasRuntime(
  val store: StateStore, val snapshotter: Snapshotter, val queue: OperationQueue,
  val publisher: StatePublisher, val autoCalActions: AutoCalNativeActionManager?,   // null em teste
) {
  companion object { fun handlers(curve: CurveHandlers, map: MapHandlers, autocal: AutoCalHandlers, reference: ReferenceHandlers, app: AppHandlers, undo: UndoHandler): Map<Intent, IntentHandler> }
  @Volatile var host: HostActions?            // MainActivity se registra / desregistra
  fun onUsbSessionReady()                      // queue.observe("curve") { readCurve → publisher.ecuCurve } ; queue.observe("map") { readFullMap → publisher.ecuMap }
  fun close()                                  // queue.close(); snapshotter.endSession()
}
class StatePublisher(private val store: StateStore, /* fontes como lambdas */) {
  fun onTelemetry(); fun onStatus(); fun onEquivalence(result: EquivalenceResult); fun onReference()
  fun onAutoCal(); fun onSession(); fun onSettings(); fun ecuCurve(json: JSONObject?); fun ecuMap(json: JSONObject?); fun ecuBackups()
}
```
Ligações no serviço: `stateChanged()` → `omegas.publisher.onStatus()`; `consumeEngineEvent` → `onTelemetry()` (live + now); recálculo da F4 → `onEquivalence(result)` + `onReference()`; `NativeAutoCalMonitor.onStateChanged`/`onFreshSnapshot` e `onStateChanged` do `AutoCalNativeActionManager` → `onAutoCal()` (projeção + status da ação + `EquivalencePhases.json()` + `EquivalenceView` se ainda existir, calculados no executor do publisher a cada 2 s no máximo — o mesmo papel dos três `BackgroundMemo` de `AutoCalJavascriptBridge.kt:33-46`); `sessionRecorder.start/stop` e `log.setListener` (logs ≤ 1/s) → `onSession()`; mudanças de GPS/LAN/balão → `onSettings()`; `handleUsbTransition` conectado → `omegas.onUsbSessionReady()` depois de `kFactor.beginUsbSession` (`:725`); `onDestroy` → `omegas.close()` antes de `kFactor.close()`. `receiptSink = { sessionRecorder.record("operation_receipt", "operation", it, force = true) }`. O `onConfirmedBatch` do `KFactorManager` (`:211-217`) continua sendo quem inicia `EM_PROVA` via diário; a fila não duplica isso. `AutoCalSnapshotManager` (leitura manual) não é recriado: a projeção recebe `manualStatus = {}` e `manualSnapshot = {"available":false}`.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun handlerMapCoversEveryIntent() {
  val map = OmegasRuntime.handlers(fakeCurve(), fakeMap(), fakeAutoCal(), fakeRef(), fakeApp(), fakeUndo())
  assertEquals(Intent.values().toSet(), map.keys)
}
@Test fun publisherWritesOnlyContractSections() {
  val store = StateStore(); val p = testPublisher(store)
  p.onStatus(); p.onTelemetry(); p.onSession(); p.onSettings(); p.ecuCurve(JSONObject().put("factorsRaw", JSONArray(List(30) { 16384 })))
  assertTrue(StateStore.SECTIONS.containsAll(store.snapshot(0).keySet() - "revision"))
  assertEquals(30, store.snapshot(0).getJSONObject("connection").getJSONObject("ecu").getJSONObject("curve").getJSONArray("factorsRaw").length())
}
@Test fun usbSessionReadyObservesCurveThenMapOnQueueThread() { /* fakes registram Thread.currentThread().name == "omegas-operation-queue" e ordem ["curve","map"] */ }
```

- [ ] **Step 2: Run to see it fail** — `tools/ci/remote-test.sh gradle "com.omegas.prohub.state.OmegasRuntimeTest"` · Expected: `REMOTE_TEST=FAIL`.

- [ ] **Step 3: Implement** — `lateinit var omegas: OmegasRuntime` no serviço, criado no fim do `onCreate` (depois de `kWriter`, `kFactor`, `nativeAutoCal`); `safety = { CalibrationWriteSafetyPolicy.evaluate(status()) }`; portas com `kFactor::readCurve`, `kFactor::startBatchWrite`, `{ JSONObject(kFactor.statusJson()) }`, `kFactor::prepareRestore`, `kWriter::readFullMap`, `kWriter::startBatchWrite`, `{ JSONObject(kWriter.statusJson()) }`.

- [ ] **Step 4: Run to verify** — `tools/ci/remote-test.sh gradle "com.omegas.prohub.state.*"` e `tools/ci/remote-test.sh checks ""` (o serviço compila e o resto continua verde) · Expected: `REMOTE_TEST=PASS`.

- [ ] **Step 5: Commit** `feat(state): OmegasRuntime no serviço publica o snapshot e possui a fila`

---

### Task 5.10: `OmegasBridge` e a `MainActivity`

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/state/OmegasBridge.kt`
- Modify: `app/src/main/java/com/omegas/prohub/MainActivity.kt` (`configureWebView` `:288-333`, `onDestroy` `:247-264`, conexão do serviço, `requestBatteryOptimizationExemption` `:428`)
- Test: `app/src/test/java/com/omegas/prohub/state/OmegasBridgeTest.kt`

**Interfaces:**
- Produces (índice):
```kotlin
class OmegasBridge(private val runtime: () -> OmegasRuntime?) {
  companion object { const val JS_NAME = "Omegas" }
  @JavascriptInterface fun snapshot(sinceRevision: Long): String   // runtime nulo → {"revision":0,"connection":{"serviceRunning":false}}
  @JavascriptInterface fun request(intentJson: String): String     // {"ok":true,"receiptId":…} | {"ok":false,"error":…}
}
```
`request` aceita `{"intent":"CURVE_WRITE","payload":{…}}`; intent fora do enum → `"Pedido desconhecido: X"`; JSON inválido → `"Pedido ilegível"`; `IntentPayloads.validate` ≠ null → essa mensagem; nada disso cria recibo.
- `MainActivity` implementa `HostActions` (5.7) e registra `runtime.host = this` no bind (`null` no `onDestroy`); injeta `OmegasBridge` como `"Omegas"` **ao lado** das pontes antigas (elas saem na 5.15); `RevisionThrottle(100L, SystemClock::uptimeMillis, { d, t -> webView.postDelayed(t, d) }) { r -> webView.evaluateJavascript("window.OmegasOnRevision&&window.OmegasOnRevision($r)", null) }` ligado a `runtime.store.addListener` no bind e removido no `onDestroy`.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun requestEnqueuesAndReturnsReceipt() {
  val rt = testRuntime(); val r = JSONObject(OmegasBridge { rt }.request("""{"intent":"OVERLAY_TOGGLE","payload":{"enabled":true}}"""))
  assertTrue(r.getBoolean("ok")); assertTrue(r.getString("receiptId").startsWith("OP-"))
}
@Test fun badRequestsNeverCreateReceipts() {
  val rt = testRuntime(); val b = OmegasBridge { rt }
  assertEquals("Pedido ilegível", JSONObject(b.request("{")).getString("error"))
  assertEquals("Pedido desconhecido: RESET_ALL", JSONObject(b.request("""{"intent":"RESET_ALL"}""")).getString("error"))
  assertFalse(JSONObject(b.request("""{"intent":"UNDO","payload":{}}""")).getBoolean("ok"))
  assertTrue(rt.store.snapshot(0).optJSONObject("operation")?.optJSONArray("history")?.length() ?: 0 == 0)
}
@Test fun snapshotIsPartialAndFastWithoutService() {
  assertEquals(0L, JSONObject(OmegasBridge { null }.snapshot(0)).getLong("revision"))
  val rt = testRuntime(); rt.store.put("live", JSONObject().put("rpm", 900)); rt.store.put("session", JSONObject())
  assertEquals(setOf("revision", "session"), JSONObject(OmegasBridge { rt }.snapshot(1)).keySet())
}
@Test fun onlyTwoJavascriptMethods() {
  val names = OmegasBridge::class.java.declaredMethods.filter { it.isAnnotationPresent(android.webkit.JavascriptInterface::class.java) }.map { it.name }.toSet()
  assertEquals(setOf("snapshot", "request"), names)
}
```

- [ ] **Step 2: Run to see it fail** — `tools/ci/remote-test.sh gradle "com.omegas.prohub.state.OmegasBridgeTest"` · Expected: `REMOTE_TEST=FAIL`.

- [ ] **Step 3: Implement** — como acima; `MainActivity.openOverlayPermission` reaproveita o `startActivity(ACTION_MANAGE_OVERLAY_PERMISSION…)` de `PowerJavascriptBridge.kt:51-58`.

- [ ] **Step 4: Run to verify** — `tools/ci/remote-test.sh gradle "com.omegas.prohub.state.OmegasBridgeTest"` e `tools/ci/remote-test.sh checks ""` · Expected: `REMOTE_TEST=PASS`.

- [ ] **Step 5: Commit** `feat(state): ponte Omegas (snapshot/request) e aviso de revisão ≤10 Hz na WebView`

---

### Task 5.11: `core/omegas.js`

**Files:**
- Create: `app/src/main/assets/ui/core/omegas.js`
- Modify: `app/src/main/assets/ui/index.html` (`<script src="core/omegas.js" defer>` antes de `core/native-api.js`, `:187`)
- Test: `tests/ui/omegas-core.test.cjs`, helper `tests/ui/support/fake-omegas.cjs`

**Interfaces:**
- Produces (índice + forma):
```js
OmegasUi.Omegas = {
  available(),                        // true se window.Omegas tem snapshot/request nativos
  snapshot(sinceRevision?),           // chama window.Omegas.snapshot(since ?? últimaRevisão), funde as seções novas no estado e devolve o estado inteiro {revision, connection, live, …}
  request(intent, payload),           // JSON.stringify({intent, payload}) → objeto {ok, receiptId|error}; sem nativo → {ok:false, simulationOnly:true, error:'Simulação: nenhuma escrita é enviada à ECU.'}
  onRevision(cb),                     // cb(revision, changedSections[]); devolve o "cancelar"
  receipt(receiptId),                 // procura em operation.current/last/history
};
window.OmegasOnRevision = revision => { /* se revision > última: snapshot(); chama os cbs */ };
```
`tests/ui/support/fake-omegas.cjs`: `fakeOmegas(initialSections, onRequest)` → `{ snapshot(since) → string, request(json) → string, calls: [], set(section, value) }` com revisão por seção (mesma regra do `StateStore`).

- [ ] **Step 1: Write the failing test**

```js
test('snapshot funde só as seções novas e mantém o resto', () => {
  const { context, native } = boot({ live: { rpm: 800 }, session: { status: { recording: true } } });
  const O = context.OmegasUi.Omegas;
  assert.equal(O.snapshot().live.rpm, 800);
  native.set('live', { rpm: 900 });
  const s = O.snapshot();
  assert.equal(s.live.rpm, 900); assert.equal(s.session.status.recording, true);
  assert.equal(native.calls.at(-1).since, 2);
});
test('OmegasOnRevision chama os ouvintes uma vez por revisão nova', () => {
  const { context, native } = boot({ live: { rpm: 1 } }); const seen = [];
  context.OmegasUi.Omegas.onRevision((rev, changed) => seen.push([rev, changed]));
  native.set('autocal', { action: {} }); context.OmegasOnRevision(2); context.OmegasOnRevision(2);
  assert.deepEqual(seen, [[2, ['autocal']]]);
});
test('request serializa {intent, payload} e devolve o recibo', () => {
  const { context, native } = boot({});
  assert.deepEqual(context.OmegasUi.Omegas.request('CURVE_RESET', {}), { ok: true, receiptId: 'OP-test-1' });
  assert.deepEqual(JSON.parse(native.requests[0]), { intent: 'CURVE_RESET', payload: {} });
});
test('sem ponte nativa: estado vazio e request simulado', () => {
  const { context } = boot(null);
  assert.equal(context.OmegasUi.Omegas.available(), false);
  assert.equal(context.OmegasUi.Omegas.request('CURVE_RESET', {}).simulationOnly, true);
});
test('não sobrescreve window.Omegas nativo', () => { const { context, native } = boot({}); assert.equal(context.Omegas, native); });
```

- [ ] **Step 2: Run to see it fail** — `tools/ci/remote-test.sh node tests/ui/omegas-core.test.cjs` · Expected: `REMOTE_TEST=FAIL` (`ENOENT … core/omegas.js`).

- [ ] **Step 3: Implement** — IIFE no padrão de `core/native-api.js` (`(function (root) {…})(typeof window !== 'undefined' ? window : globalThis)`); parse tolerante (string ou objeto).

- [ ] **Step 4: Run to verify** — mesmo comando → `REMOTE_TEST=PASS`.

- [ ] **Step 5: Commit** `feat(ui): core/omegas.js — snapshot com revisão, request e onRevision`

---

### Task 5.12: `native-api.js` vira adaptador sobre `Omegas`

**Files:**
- Modify: `app/src/main/assets/ui/core/native-api.js` (classe `NativeApi`, `:191-358`; demos `:24-189` ficam)
- Modify: testes de UI que injetam `OmegasNative`/`OmegasCalibration`/`OmegasPower` (achar com `grep -lE "Omegas(Native|Calibration|V7|Power)" tests/ui/*.cjs`; hoje: `app-shell-runtime`, `curve-map-editor-coherence`, `didactic-expansion`, `floating-telemetry`, `global-vehicle-status`, `live-tracing-contract`, `portmon-browser-simulator-e2e`)
- Test: `tests/ui/native-api-adapter.test.cjs`

**Interfaces:**
- Consumes: `OmegasUi.Omegas` (5.11); seções de 5.8; `operation.legacy.curve|map` (5.4/5.5).
- Produces: a mesma API pública de `NativeApi` que as telas usam hoje, agora sem nenhuma ponte antiga:

| Método | Fonte nova |
|---|---|
| `isDemo()` | `!Omegas.available()` |
| `releaseIdentity()`, `selfTest()` | `connection.release`, `connection.selfTest` |
| `status()` | campos de `connection` + `rpm/petrolMs/gasMs/mapBar/directTelemetryAgeMs` de `live` (mesmas chaves de `getStatus`, `HubJavascriptBridge.kt:165-217`) |
| `presentSnapshot()` / `telemetry()` | `{ok:true, revision, data: {...live, interpolation: now}}` / `{...live, interpolation: now}` |
| `scienceSnapshotSince(r)` (se ainda existir após F4) | `{ok:true, changed:false, revision, data:{}}` |
| `logs()`, `sessionStatus()`, `sessions()` | `session.logs`, `session.status`, `session.list` |
| `overlayStatus()`, `batteryOptimizationStatus()` | `settings.overlay`, `settings.power` |
| `requestOverlayPermissionAndEnable()` / `setTelemetryOverlayEnabled(e)` | `request('OVERLAY_TOGGLE', {enabled:true|e})` |
| `setOverlayScale(s)`, `requestBatteryOptimizationExemption()`, `setSessionSettings(s)` | `request('SETTINGS_SET', {key:'overlay.scale'|'power.batteryExemption'|'recorder', value})` |
| `exportSession(id)`, `exportLogs()`, `exportData()` | `request('SESSION_EXPORT', {kind:'session', sessionId:id}|{kind:'logs'}|{kind:'data'})` |
| `startCurveRead()` / `curveOperation()` | marca leitura; devolve `{...connection.ecu.curve, ok:true, state:'COMPLETED', busy:false}` quando há curva, senão `{state:'CURVE_READING', busy:true}`; depois de um pedido de curva, `operation.legacy.curve` |
| `curveBackups()` | `connection.ecu.curveBackups` |
| `writeCurve(points, reason)` | `request('CURVE_WRITE', {points: points.map(p => ({index, targetRaw})), reason})` |
| `resetCurve()` / `prepareCurveRestore(file)` | `request('CURVE_RESET', {})` / `request('CURVE_RESTORE', {backupId:file})`; ao concluir, `curveOperation()` → `{ok:true, state:'COMPLETED', busy:false, points:[], currentCurve: readback}` (a tela mostra "A ECU já está igual ao backup") |
| `startCurveBackup(label)` | `{ok:false, state:'FAILED', busy:false, error:'Cada gravação já guarda uma foto da curva antes (Desfazer). Salvar manual saiu nesta versão.'}` |
| `startMapRead()` / `mapReadResult()` | `{ok:true, started:true, state:'READING'}` / `{...connection.ecu.map, ok:true, state:'COMPLETED', busy:false}` ou `{state:'READING', busy:true}` |
| `writeMap(cells, …, reason)` / `mapWriteOperation()` | `request('MAP_WRITE', {cells: cells.map(c => ({row, column, target})), reason})` / `operation.legacy.map` |
| `previewMapAdjustment(cells, mode, adj)` | JS puro, porte de `MapKManualPlanner.target` (`MapKManualPlanner.kt:17-27`): `percent|delta|target`, `Math.round`, clamp `100..255` |
| `previewCurvePoint(index, factor)` | JS puro, porte de `KFactorManualPlanner.preview` (`KFactorManualPlanner.kt:14-49`) sobre `connection.ecu.curve`; fator fora de `0,60..4,00` → `{ok:false, error:'Informe um fator entre 0,60 e 4,00'}` |
| `connectUsb()`, `disconnectUsb()`, `learning*`, `exportLearning()`, `importLearning()` (se ainda existirem) | removidos; sem chamador nas telas (grep do passo 1) |

- [ ] **Step 1: Write the failing test** (`tests/ui/native-api-adapter.test.cjs`, carrega `core/omegas.js` + `core/native-api.js` em `vm` com `fakeOmegas`)

```js
test('nenhuma ponte antiga é lida', () => {
  const src = fs.readFileSync('app/src/main/assets/ui/core/native-api.js', 'utf8');
  assert.doesNotMatch(src, /Omegas(Native|Calibration|V7|Power)\b/);
});
test('writeCurve vira CURVE_WRITE só com index/targetRaw', () => {
  const { api, native } = boot({ connection: { ecu: { curve: curve30() } } });
  assert.equal(api.writeCurve([{ index: 0, currentRaw: 12000, targetRaw: 12100 }], 'teste').ok, true);
  assert.deepEqual(JSON.parse(native.requests[0]), { intent: 'CURVE_WRITE', payload: { points: [{ index: 0, targetRaw: 12100 }], reason: 'teste' } });
});
test('writeMap vira MAP_WRITE e mapWriteOperation lê operation.legacy.map', () => {
  const { api, native } = boot({ operation: { legacy: { map: { state: 'BATCH_CONFIRMED', busy: false, ok: true } } } });
  api.writeMap([{ row: 0, column: 0, current: 120, target: 125 }], 0, 0, 'm');
  assert.equal(JSON.parse(native.requests[0]).intent, 'MAP_WRITE');
  assert.equal(api.mapWriteOperation().state, 'BATCH_CONFIRMED');
});
test('prévia do mapa igual ao Kotlin', () => {
  const { api } = boot({});
  const r = api.previewMapAdjustment([{ row: 0, column: 0, current: 120 }, { row: 0, column: 1, current: 120 }, { row: 0, column: 2, current: 250 }], 'percent', 5);
  assert.deepEqual(r.items.map(i => i.target), [126, 126, 255]);
  assert.equal(api.previewMapAdjustment([{ row: 0, column: 0, current: 120 }], 'delta', -30).items[0].target, 100);
  assert.equal(api.previewMapAdjustment([{ row: 0, column: 0, current: 120 }], 'target', 300).items[0].target, 255);
});
test('prévia da curva igual ao Kotlin', () => {
  const { api } = boot({ connection: { ecu: { curve: curve30() } } });
  assert.equal(api.previewCurvePoint(3, 1.05).targetRaw, Math.round(1.05 * 16384));
  assert.equal(api.previewCurvePoint(3, 0.5).ok, false);
});
test('status() mantém as chaves antigas', () => {
  const { api } = boot({ connection: { usbConnected: true, engineReady: true, fuelState: 'GNV' }, live: { live: { rpm: 2100 }, telemetryAgeMs: 40 } });
  const s = api.status(); assert.equal(s.usbConnected, true); assert.equal(s.fuelState, 'GNV'); assert.equal(s.rpm, 2100);
});
test('salvar backup manual responde com frase honesta', () => { assert.match(boot({}).api.startCurveBackup('x').error, /foto da curva antes/); });
```
Nos testes de UI listados acima, trocar `context.OmegasNative = {…}` / `context.OmegasV7|OmegasCalibration = {…}` / `context.OmegasPower = {…}` por `context.Omegas = fakeOmegas({...seções equivalentes})` e as asserções de chamada por asserções sobre `native.requests` (o teste `APK usa OmegasV7 para uma única intenção de mapa e curva`, `app-shell-runtime.test.cjs:127-152`, passa a se chamar `APK usa Omegas.request para uma única intenção de mapa e curva`, com as mesmas contagens: 2 pedidos, `MAP_WRITE` com 1 célula, `CURVE_WRITE` com 1 ponto). O simulador Portmon (em `tools/portmon/` desde a F3) passa a expor `root.Omegas = { snapshot, request }` com as mesmas respostas que hoje dá em `startKMapRead/startKBatchWrite/getKWriteStatus` (seções `connection.ecu.map` e `operation.legacy.map`).

- [ ] **Step 2: Run to see it fail** — `tools/ci/remote-test.sh node tests/ui/native-api-adapter.test.cjs` · Expected: `REMOTE_TEST=FAIL` (primeiro teste: `native-api.js` ainda cita `OmegasNative`).

- [ ] **Step 3: Implement** — `NativeApi` passa a ter `this.omegas = root.OmegasUi.Omegas`; `this.demo = !this.omegas.available()`; cada método da tabela; os ramos `demo` atuais ficam iguais.

- [ ] **Step 4: Run to verify** — `tools/ci/remote-test.sh node tests/ui/native-api-adapter.test.cjs` e depois `tools/ci/remote-test.sh checks ""` (todos os `tests/ui` migrados) · Expected: `REMOTE_TEST=PASS`.

- [ ] **Step 5: Commit** `refactor(ui): native-api.js vira adaptador fino sobre Omegas`

---

### Task 5.13: `autocal-api.js` vira adaptador sobre `Omegas`

**Files:**
- Modify: `app/src/main/assets/ui/core/autocal-api.js` (`:19-46`)
- Modify: `app/src/main/assets/ui/core/router.js` (`:54-55`: carregar `core/omegas.js` antes de `core/autocal-api.js` se ainda não carregado)
- Modify: testes de UI que injetam `OmegasAutoCal` (`grep -l "OmegasAutoCal" tests/ui/*.cjs`; hoje: `autocal-cockpit`, `autocal-consumer-graph`, `autocal-final-product`, `autocal-session-experience`, `autocal-test-sensitivity`)
- Test: `tests/ui/autocal-api-adapter.test.cjs`

**Interfaces:**

| Método de `AutoCalApi` | Fonte nova |
|---|---|
| `available()` | `Omegas.available()` |
| `projection()`, `actionStatus()` | `autocal.projection`, `operation.legacy.autocal` se houver pedido AutoCal em curso/último, senão `autocal.action` |
| `equivalence()`, `equivalenceFresh()`, `refinedAnalysis()`, `refinementPhase()` | `autocal.equivalenceView`, idem, `autocal.refined`, `{ok:true, autopilot: autocal.phases}` (só as que ainda existirem após F4) |
| `sessionStatus()`, `sessions()`, `exportSession(id)` | `session.status`, `session.list`, `request('SESSION_EXPORT', {kind:'session', sessionId:id})` |
| `setAcquisitionEnabled(e)` | `e ? request('AUTOCAL_RESUME', {}) : request('AUTOCAL_PAUSE', {})` |
| `prepare(action)` + `execute(id)` | `prepare` guarda a ação localmente e devolve `{ok:true, prepared:true, preparationId:'local-<n>', action}`; `execute(id)` envia `RESET_GAS→AUTOCAL_RESET_GAS`, `RESET_PETROL→AUTOCAL_RESET_PETROL`; outra ação → `{ok:false, error:'Ação não existe mais nesta versão'}` |
| `preparePointDelete(f,i)` / `preparePointDeleteBatch(t)` + `execute(id)` | guarda alvos; `execute` → `request('AUTOCAL_RELEARN', {points:[{fuel,index}…]})` |
| `cancelPreparation()` | limpa o local; `{ok:true, cleared:true, writesStarted:false}` |
| `identity`, `readerStatus`, `readerSnapshot`, `acquisitionStatus`, `acquisitionSnapshot`, `startRead`, `cancelRead`, `prepareKFactorReset` | removidos (sem chamador nas telas — grep do passo 1) |

`operation.legacy.autocal` = `managerStatus` final/corrente do `AutoCalNativeActionManager` (mesmos estados `CONFIRMED`, `FAILED`, `READING_AFTER`… que `autocal-cockpit.js` já lê); preencher em `AutoCalHandlers` (5.6) via `ctx.legacy(status)`.

- [ ] **Step 1: Write the failing test**

```js
test('autocal-api.js não cita OmegasAutoCal', () => assert.doesNotMatch(read('core/autocal-api.js'), /OmegasAutoCal/));
test('Zerar gás vira AUTOCAL_RESET_GAS em um pedido', () => {
  const { api, native } = boot({}); const p = api.prepare('RESET_GAS'); api.execute(p.preparationId);
  assert.deepEqual(native.requests.map(r => JSON.parse(r).intent), ['AUTOCAL_RESET_GAS']);
});
test('Readquirir pontos vira AUTOCAL_RELEARN com os alvos', () => {
  const { api, native } = boot({}); const p = api.preparePointDeleteBatch([{ fuel: 'GAS', index: 3 }]); api.execute(p.preparationId);
  assert.deepEqual(JSON.parse(native.requests[0]).payload, { points: [{ fuel: 'GAS', index: 3 }] });
});
test('Pausar/Retomar', () => { const { api, native } = boot({}); api.setAcquisitionEnabled(false); api.setAcquisitionEnabled(true);
  assert.deepEqual(native.requests.map(r => JSON.parse(r).intent), ['AUTOCAL_PAUSE', 'AUTOCAL_RESUME']); });
test('MANUAL_AUTOMATCH e RESET_ALL não existem', () => { const { api } = boot({});
  for (const a of ['MANUAL_AUTOMATCH', 'RESET_ALL', 'RESET_K_FACTOR']) assert.equal(api.execute(api.prepare(a).preparationId).ok, false); });
test('actionStatus devolve o status do manager que a tela já entende', () => {
  const { api } = boot({ operation: { legacy: { autocal: { state: 'CONFIRMED', busy: false } } } });
  assert.equal(api.actionStatus().state, 'CONFIRMED');
});
```

- [ ] **Step 2: Run to see it fail** — `tools/ci/remote-test.sh node tests/ui/autocal-api-adapter.test.cjs` · Expected: `REMOTE_TEST=FAIL`.

- [ ] **Step 3: Implement** — como a tabela; nos testes listados, `context.OmegasAutoCal = {…}` vira `context.Omegas = fakeOmegas({autocal: {...}, session: {...}})`.

- [ ] **Step 4: Run to verify** — `tools/ci/remote-test.sh node tests/ui/autocal-api-adapter.test.cjs` e `tools/ci/remote-test.sh checks ""` · Expected: `REMOTE_TEST=PASS`.

- [ ] **Step 5: Commit** `refactor(ui): autocal-api.js vira adaptador fino sobre Omegas`

---

### Task 5.14: Contratos Python e testes no emulador passam a ler a autoridade nova

**Files:**
- Modify/Delete: contratos Python que leem as pontes antigas (`grep -lE "HubJavascriptBridge|AutoCalJavascriptBridge|PowerJavascriptBridge|AutoCalBridgeProvider|CalibrationOperationsBridge|OmegasNative|OmegasAutoCal|OmegasPower|OmegasCalibration" tests/*.py tools/ci/*.py tests/fixtures/*.json`; hoje, entre os que sobrevivem à F2/F3: `test_autocal_bridge_surface_contract`, `test_autocal_canonical_session_contract`, `test_autocal_levels_scope_contract`, `test_autocal_operational_toggle_contract`, `test_autocal_point_delete_contract`, `test_autocal_reacquisition_scope_contract`, `test_autocal_reset_safety_gate`, `test_autocal_session_lifecycle_contract`, `test_background_power_overlay_contract`, `test_mp48_serial_scheduler_contract`, `test_native_autocal_contract`, `test_omegas_autocal_progbase_parity_matrix`, `test_telemetry_single_snapshot_authority_contract`, `test_usb_permission_denial_contract`, `tools/ci/autocal_forensic_lane.py`, `tests/fixtures/omegas-autocal-progbase-parity-v1.json`)
- Modify: `app/src/androidTest/java/com/omegas/prohub/RefinoRenderTest.kt` (`:374`, `:538-551`), `app/src/androidTest/java/com/omegas/prohub/DashboardLevelsRenderTest.kt` (`:437-470`, leitura por reflexão do campo da ponte de calibração)

**Regra por asserção:** invariante que continua valendo (toggle de um toque, gate de segurança antes de mutação, readback obrigatório, allow-list de ações sem `MANUAL_AUTOMATCH`/`RESET_ALL`, recibo na sessão) é reapontada para o dono novo (`state/handlers/*.kt`, `state/OmegasRuntime.kt`, `state/OmegasBridge.kt`); asserção sobre o formato interno de uma ponte que deixa de existir é apagada, e `test_autocal_bridge_surface_contract.py` é apagado inteiro (substituído pelo contrato da 5.15). No `RefinoRenderTest`, `OmegasAutoCal.getRefinedAnalysis()` vira `JSON.parse(Omegas.snapshot(0)).autocal.refined`, e o teste de latência mede `Omegas.snapshot(0)` (40 iterações, mesma mediana < 30 ms). No `DashboardLevelsRenderTest`, instalar a curva de fixture com `runtime.publisher.ecuCurve(fixture)` + `runtime.store.put("operation", …legacy.curve…)` em vez de `setPrivateField(bridge, "lastOperation", …)`.

- [ ] **Step 1: Write the failing test** — acrescentar a `tests/test_omegas_bridge_contract.py` (nasce aqui, completa na 5.15) a primeira verificação:

```python
import pathlib, re
root = pathlib.Path(__file__).resolve().parents[1]
OLD = re.compile(r"HubJavascriptBridge|AutoCalJavascriptBridge|PowerJavascriptBridge|AutoCalBridgeProvider|CalibrationOperationsBridge|\bOmegas(Native|AutoCal|Power|Calibration|V7)\b")
offenders = [str(p.relative_to(root)) for base in ("tests", "tools/ci", "app/src/androidTest") for p in (root / base).rglob("*")
             if p.is_file() and p.suffix in {".py", ".kt", ".cjs", ".json"} and p.name != "test_omegas_bridge_contract.py" and OLD.search(p.read_text(encoding="utf-8", errors="ignore"))]
assert not offenders, f"testes ainda leem pontes antigas: {offenders}"
print("BRIDGE_CONSUMERS_MIGRATED=PASS")
```

- [ ] **Step 2: Run to see it fail** — `tools/ci/remote-test.sh python tests/test_omegas_bridge_contract.py` · Expected: `REMOTE_TEST=FAIL` listando os arquivos acima.

- [ ] **Step 3: Migrar** cada arquivo pela regra.

- [ ] **Step 4: Run to verify** — `tools/ci/remote-test.sh python tests/test_omegas_bridge_contract.py`, `tools/ci/remote-test.sh checks ""` e o render no emulador: `gh workflow run verde-android-render-evidence.yml --ref work/platina-f5-autoridade-unica && gh run watch "$(gh run list --workflow verde-android-render-evidence.yml --branch work/platina-f5-autoridade-unica -L1 --json databaseId -q '.[0].databaseId')" --exit-status` · Expected: `REMOTE_TEST=PASS` nos dois primeiros e o workflow de render verde.

- [ ] **Step 5: Commit** `test: contratos e render no emulador leem a ponte Omegas`

---

### Task 5.15: Remover as pontes antigas e travar o contrato ponte ↔ JS

**Files:**
- Delete: `app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt`, `app/src/main/java/com/omegas/prohub/web/PowerJavascriptBridge.kt`, `app/src/main/java/com/omegas/prohub/web/CalibrationOperationsBridge.kt` (F1), `app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt`, `app/src/main/java/com/omegas/prohub/autocal/AutoCalBridgeProvider.kt`
- Modify: `app/src/main/AndroidManifest.xml` (remover o `<provider …AutoCalBridgeProvider>`, `:35-39`), `app/src/main/java/com/omegas/prohub/MainActivity.kt` (campos `:43-45`, criação `:307-312`, `onDestroy` `:252-261`: só `Omegas` fica)
- Test: `tests/test_omegas_bridge_contract.py` (completo)

**Interfaces:** some tudo de `OmegasNative`, `OmegasCalibration`, `OmegasAutoCal`, `OmegasPower`, inclusive `registerRefuel`, `setGnvSettings`, `restartEngine` (a ação da notificação `ACTION_RESTART_ENGINE` continua no serviço) e todos os `*Link*` (`getLinkStatus`, `configureOmegasLink`, `claimLinkMain`, `releaseLinkMain`, `syncLinkNow`). `TelemetryForegroundService` perde só os métodos que ficarem sem chamador (o `lint`/compilação do passo 4 aponta).

- [ ] **Step 1: Write the failing test** — completar `tests/test_omegas_bridge_contract.py`:

```python
main = root / "app/src/main"
for gone in ("java/com/omegas/prohub/web/HubJavascriptBridge.kt", "java/com/omegas/prohub/web/PowerJavascriptBridge.kt",
             "java/com/omegas/prohub/web/CalibrationOperationsBridge.kt", "java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt",
             "java/com/omegas/prohub/autocal/AutoCalBridgeProvider.kt"):
    assert not (main / gone).exists(), gone
assert "AutoCalBridgeProvider" not in (main / "AndroidManifest.xml").read_text(encoding="utf-8")

kotlin = {p: p.read_text(encoding="utf-8") for p in (main / "java").rglob("*.kt")}
injections = [m for t in kotlin.values() for m in re.findall(r'addJavascriptInterface\([^,]+,\s*("?[\w.]+"?)\)', t)]
assert injections == ["OmegasBridge.JS_NAME"] or injections == ['"Omegas"'], injections
bridge = (main / "java/com/omegas/prohub/state/OmegasBridge.kt").read_text(encoding="utf-8")
methods = re.findall(r"@JavascriptInterface\s+fun\s+(\w+)\(", bridge)
assert sorted(methods) == ["request", "snapshot"], methods
annotated = [p.name for p, t in kotlin.items() if "@JavascriptInterface" in t]
assert annotated == ["OmegasBridge.kt"], annotated

ui = {p: p.read_text(encoding="utf-8") for p in (main / "assets/ui").rglob("*.js")}
ui_text = "\n".join(ui.values())
for m in methods:   # todo método da ponte tem chamador no JS
    assert re.search(rf"\bnative\.{m}\(|Omegas\.{m}\(", (main / "assets/ui/core/omegas.js").read_text(encoding="utf-8")), m
enum_body = re.search(r"enum class Intent \{([^}]*)\}", (main / "java/com/omegas/prohub/state/Intent.kt").read_text(encoding="utf-8")).group(1)
intents = re.findall(r"[A-Z][A-Z_]+", enum_body)
assert len(intents) == 14, intents
used = set(re.findall(r"request\(\s*['\"]([A-Z_]+)['\"]", ui_text))
assert used, "nenhum intent encontrado no JS"
assert used <= set(intents), f"intent do JS sem enum Kotlin: {used - set(intents)}"
runtime = (main / "java/com/omegas/prohub/state/OmegasRuntime.kt").read_text(encoding="utf-8")
for i in intents:   # todo intent tem handler
    assert f"Intent.{i} to" in runtime, f"intent sem handler: {i}"
assert not re.search(r"\bOmegas(Native|AutoCal|Power|Calibration|V7)\b", ui_text)
for gone in ("registerRefuel", "restartEngine", "setGnvSettings", "getLinkStatus", "configureOmegasLink", "claimLinkMain", "releaseLinkMain", "syncLinkNow"):
    assert gone not in ui_text, gone
print("OMEGAS_BRIDGE_CONTRACT=PASS")
```
(Todo intent no JS é escrito como literal em `request('NOME', …)` — inclusive em `setAcquisitionEnabled` e no mapa de `execute` de `autocal-api.js` — para o grep enxergar; nada de nome montado por expressão.)

- [ ] **Step 2: Run to see it fail** — `tools/ci/remote-test.sh python tests/test_omegas_bridge_contract.py` · Expected: `REMOTE_TEST=FAIL` com `AssertionError: java/com/omegas/prohub/web/HubJavascriptBridge.kt`.

- [ ] **Step 3: Delete** os 5 arquivos, o `<provider>`, e o que a `MainActivity` referenciava; remover do serviço os métodos que só as pontes chamavam (ex.: `registerRefuel` via `consumptionTracker` continua existindo no tracker, mas sem endpoint).

- [ ] **Step 4: Run to verify** — `tools/ci/remote-test.sh python tests/test_omegas_bridge_contract.py` e `tools/ci/remote-test.sh checks ""` · Expected: `REMOTE_TEST=PASS`, log `OMEGAS_BRIDGE_CONTRACT=PASS`.

- [ ] **Step 5: Commit** `refactor!: remove OmegasNative, OmegasCalibration, OmegasAutoCal e OmegasPower; ponte única Omegas`

---

### Task 5.16: APK, `STATUS.md` e merge

**Files:**
- Modify: `STATUS.md` (bloco do último APK; continua ≤ 40 linhas e com `PHYSICAL_VALIDATION_CLAIMED=false`, exigido por `tests/test_governance_contract.py`)

- [ ] **Step 1: Gate completo** — Run: `tools/ci/remote-test.sh checks ""` · Expected: `REMOTE_TEST=PASS`.

- [ ] **Step 2: APK** — Run: `gh workflow run verde-apk-now.yml --ref work/platina-f5-autoridade-unica -f build_apk=true && sleep 10 && RUN=$(gh run list --workflow verde-apk-now.yml --branch work/platina-f5-autoridade-unica -L1 --json databaseId -q '.[0].databaseId') && gh run watch "$RUN" --exit-status && gh run view "$RUN" --log | grep -E "OMEGAS_PLATINA_APK=|SOURCE_SHA=|APK_SHA256="`
Expected: `OMEGAS_PLATINA_APK=PASS`, `SOURCE_SHA=<HEAD da branch>`, `APK_SHA256=<64 hex>`.

- [ ] **Step 3: `STATUS.md`** — substituir o bloco do último APK por: fatia `F5 — Autoridade única`, `SOURCE_SHA`, run id, `APK_SHA256`, classe de prova `2 sintético + 3 replay (render) + 4 APK no emulador`, não provado: "cabo real caindo no meio da gravação; Desfazer na ECU física; balão sobre outros apps", `PHYSICAL_VALIDATION_CLAIMED=false`. Run: `tools/ci/remote-test.sh python tests/test_governance_contract.py` · Expected: `REMOTE_TEST=PASS`.

- [ ] **Step 4: Commit** `docs(status): APK da F5 (ponte única) com SHA-256` e `git push`.

- [ ] **Step 5: PR pronto e merge** — corpo do PR: o que mudou (uma ponte, fila, foto, Desfazer, recibos; pontes antigas removidas) · classe de prova (1 contrato, 2 sintético, 3 replay no render, 4 APK) · não provado (físico, §6 da spec) · desvios conscientes (ver "Cobertura da spec"). Run: `gh pr ready && gh pr checks --watch && gh pr merge --merge` · Expected: `OMEGAS PLATINA CI` verde no SHA do PR e PR mesclado em `OmegasPlatina`.

---

## Cobertura da spec

| Requisito (spec) | Task |
|---|---|
| §2.1 uma ponte `Omegas`, dois verbos (`snapshot`, `request`) | 5.10, 5.15 |
| §2.1 canal de eventos sem polling (`OmegasOnRevision`) | 5.1 (throttle), 5.10 (ligação), 5.11 (JS) |
| §2.1 tela redesenha só a seção que mudou (snapshot parcial por revisão) | 5.1, 5.11 |
| §2.1 teste de contrato ponte ↔ JS, intent ↔ handler | 5.15 (+ 5.9 `handlerMapCoversEveryIntent`) |
| §2.2 lista fechada de intents + payloads | 5.1, 5.2 (`IntentPayloads`) |
| §2.2 foto antes por intent (tabela) | 5.3 |
| §2.2 Desfazer: curva, mapa, Referência, reset gás/gasolina (curva da foto) | 5.4, 5.5, 5.6 |
| §2.2 "Saem": `registerRefuel`, `restartEngine`, `setGnvSettings`, `*Link*`, métodos sem botão | 5.12, 5.13, 5.15 |
| §2.3 uma mutação na ECU por vez | 5.2, 5.9 (`observe` na mesma thread) |
| §2.3 ciclo RECEBIDO→PREPARANDO→EXECUTANDO→CONFERINDO→CONCLUÍDO/FALHOU | 5.2, 5.4–5.6 |
| §2.3 transporte ≠ ECU, próximas ações distintas | 5.2 (`FailureClassifier`), 5.4–5.6 |
| §2.3 recibo na sessão (intent, foto, bytes, readback, duração, resultado) | 5.2, 5.9 (`operation_receipt`) |
| §2.3 Desfazer é intent com o mesmo ciclo, expira com a sessão | 5.3, 5.6 |
| §2.4 `StateStore` com revisão monotônica; escritores = telemetria, cérebro, fila, AutoCal, sessão | 5.1, 5.8, 5.9 |
| §0.2-5 nenhuma exceção derruba o app | 5.2 |
| §0.2-8 comandos de ECU intactos, só chamados pela fila | 5.4–5.6, 5.9 |
| §4.2 leitura por reflexão no `DashboardLevelsRenderTest` → ponte única | 5.14 |
| §4.6 gates: fila serializa; cabo caído → ✗ sem crash; Desfazer byte a byte; ponte sem órfão | 5.2, 5.4, 5.15 |
| §7 linha 5: **APK** | 5.16 |

**Não colocado / desvio consciente (para o dono decidir na F6):**
- `AUTOCAL_RELEARN` → "Desfazer (restaura máscara)": não há comando provado que devolva pontos apagados à aquisição da ECU, e a regra 8 proíbe comando novo. O Desfazer desse intent responde `FALHOU/ECU` com a frase "A ECU não aceita devolver pontos apagados; ela vai reaprendê-los" (Task 5.6).
- "Salvar backup" manual da Curva K (spec §3.1, subpágina Backups) não está na lista fechada de §2.2. Nesta fatia o botão antigo responde com frase honesta; a foto automática antes de cada gravação cobre o Desfazer. Se o dono quiser o botão, precisa de um intent novo no índice.
- Mapa K e curva crua não têm seção própria no contrato; ficam em `connection.ecu.{curve,map,curveBackups}` (Task 5.8), lidos automaticamente uma vez por conexão USB pela fila (Task 5.9). Isso muda a regra antiga "leitura do mapa só manual" (`KWriteManager.kt:171-179`) em favor de §0.2-1 ("observar é automático").
