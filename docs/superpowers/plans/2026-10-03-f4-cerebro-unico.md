# Fatia 4 — Cérebro único Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Um cérebro só (`com.omegas.prohub.equivalence`) que, a partir da Referência congelada e das Curvas Próprias aprendidas da telemetria, diz o estado de cada um dos 30 pontos da Curva K, o índice "% da condução equivalente à gasolina" e a única próxima ação — sem nunca tocar a ECU.

**Architecture:** Funções puras (`OwnCurveFitter`, `EquivalenceEngine`) sobre três acumuladores persistidos (`ReferenceStore`, `ExperienceMeter`, `UsageMeter`) e os que já existem (`EquivalenceLedger`, `StallWatch`). `EquivalenceRuntime` junta tudo e é a única coisa que o serviço chama; `EquivalencePhases` (ex-`RefinementAutopilot`, F1) ganha o ciclo de prova por ponto (`EM_PROVA → CONFIRMADO|CONTESTADO|INCONCLUSIVO`). A proposta de K continua saindo só do `AutoMatchRefinedEngine`, alimentado por pares derivados das Curvas Próprias.

**Tech Stack:** Kotlin (JVM 17, JUnit 4, `org.json`), oráculo Python (`tools/autocal_refine/refined_oracle.py`), replay real `fixtures/autocal/real/*.json.gz`, laço remoto `tools/ci/remote-test.sh`.

**Spec:** docs/superpowers/specs/2026-10-03-omegas-platina-norte-unico-design.md  ·  **Índice/contrato:** docs/superpowers/plans/2026-10-03-00-norte-unico-index.md

Branch: `work/platina-f4-cerebro-unico` a partir de `OmegasPlatina` (com F1–F3 mesclados). Todo commit termina com as duas linhas:

```
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01YN94YqPpeRPSaeNkh2QkF7
```

**Linhas citadas:** medidas na base pré-F1. F1–F3 deslocam linhas (F1 renomeia `RefinementAutopilot.kt` → `EquivalencePhases.kt`; F3 apaga blocos de learning do serviço). Cada "Modify" traz também uma âncora de texto; o executor confirma com `grep -n "<âncora>"` antes de editar.


> **Antes de começar:** leia a seção **Reconciliação entre planos** do índice (`2026-10-03-00-norte-unico-index.md`). Onde este plano divergir dela (intents, payloads, formato do snapshot, comando do emulador), vale o índice.
## Global Constraints

Todas as do índice, mais:

- O pacote `com.omegas.prohub.equivalence` não importa `android.*` nem referencia `UsbSerialManager`, `KWriteManager`, `KFactorManager`, `AutoCalNativeActionManager`, `ResponseDrivenEcuEngine` (só observa; teste de contrato na Task 4.9).
- `AutoMatchRefinedEngine`: corpo intocado. Única mudança permitida: `private` → `internal` em `Observation` e `whittaker` (decisão: **expor como `internal`**, não extrair `RobustCurveMath` — mantém o núcleo provado byte a byte e a paridade existente).
- Mediana = `sorted[size / 2]` (mesma regra de `EquivalenceLedger.index()` e `StallWatch`), em Kotlin e no oráculo Python.
- Todo arquivo novo tem `FORMAT`, escrita atômica (`.tmp` + `renameTo`) e leitura tolerante: formato errado ou JSON corrompido → estado vazio, sem exceção.
- `EquivalenceRuntime.evaluate` roda no executor do serviço (`healthTick`, 3 s), nunca na thread principal; a ponte só lê o último resultado pronto.
- Nada no F4 grava K, aquisição ou Referência sem toque do dono; `freezeReference()` é o único verbo novo e não toca a ECU.

## Review Focus

1. **ECU reaprende gasolina depois do congelamento** (REFERENCE seq 95 congelado → seq 962 zera gasolina → seq 2183 readquire): `current()` não muda (mesmo `id` e pontos), `ecuDrift(962) == null`, `ecuDrift(2183) > 0.10`. Teste na Task 4.2.
2. **App recém-instalado, ECU madura, zero telemetria** (REFERENCE seq 95, ledger vazio, sem Referência congelada): `provisional == true`, Curva Própria = prior puro (todas as células `REFERENCE`), pontos com `axisMs ≥ 3,0` e MAP equivalente conhecido em `APRENDENDO`, demais `SEM_DADOS`, próxima ação `FREEZE_REFERENCE`. Teste na Task 4.6.
3. **Trocar a Referência com o livro cheio** (congela 95, depois 2183): contagens do ledger e amostras por célula idênticas, só o prior muda; provas abertas voltam a `onlineMs = 0` e seguem `EM_PROVA`. Testes nas Tasks 4.3 e 4.8.
4. **GNV pobre "escorrega" de faixa**: no mesmo MAP o GNV pede +15% de ms; tremor, quase-apagão e uso caem no ponto do **MAP equivalente**, não no ms cru. Teste na Task 4.4.
5. **Arquivo de fases antigo sem `proofs`** e **arquivo de Referência corrompido**: abrem sem crash; fases sem provas, `current() == null`. Testes nas Tasks 4.7 e 4.2.

---

### Task 4.1: Tipos do contrato + acessos que o cérebro precisa

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/equivalence/EquivalenceTypes.kt`
- Modify: `app/src/main/java/com/omegas/prohub/autocal/AutoMatchRefinedEngine.kt:394` (âncora `private class Observation`) e `:409` (âncora `private fun whittaker(`) — só visibilidade
- Modify: `app/src/main/java/com/omegas/prohub/autocal/EquivalenceLedger.kt` após `:227` (âncora `fun pairs(): List<EvidencePair>`)
- Test: `app/src/test/java/com/omegas/prohub/equivalence/EquivalenceTypesTest.kt`, `app/src/test/java/com/omegas/prohub/autocal/EquivalenceLedgerTest.kt` (novo caso)

**Interfaces:**
- Produces (exatamente como no índice): `Fuel`, `RefPoint`, `Reference`, `CellSource`, `OwnCell`, `OwnCurve` (com `fun at(mapBar: Double): Double?` = interpolação linear entre centros de células com `petrolMs != null`; `null` fora do primeiro/último não-nulo), `PointState`, `EquivalencePoint`, `NextActionKind`, `NextAction`, `EquivalenceResult`, `EquivalenceTolerances` (`fun tolerance(dispersion: Double): Double = max(MIN, 2 * dispersion)`).
- Produces (novo, neste arquivo):
  ```kotlin
  data class ProofOutcome(val states: Map<Int, PointState>, val remainingMinutes: Int?) {
      companion object { val NONE = ProofOutcome(emptyMap(), null) } }
  ```
- Produces em `EquivalenceLedger`: `fun petrolObservations(): List<Obs>` e `fun gasObservations(): List<Obs>` (cópias sob `lock`, ordem de chegada).
- Produces em `AutoMatchRefinedEngine`: `internal class Observation(...)`, `internal fun whittaker(...)` (assinaturas inalteradas).

- [ ] **Step 1: Write the failing test**

```kotlin
class EquivalenceTypesTest {
    @Test fun `tolerancia e o maior entre 4 por cento e duas dispersoes`() {
        assertEquals(0.04, EquivalenceTolerances.tolerance(0.01), 1e-12)
        assertEquals(0.06, EquivalenceTolerances.tolerance(0.03), 1e-12)
        assertEquals(0.02, EquivalenceTolerances.CELL_BAR, 0.0); assertEquals(10, EquivalenceTolerances.USAGE_SESSIONS)
    }
    @Test fun `curva propria interpola so entre celulas conhecidas`() {
        val c = OwnCurve(Fuel.GASOLINA, listOf(
            OwnCell(0.31, null, 0, 0.0, CellSource.REFERENCE, null),
            OwnCell(0.33, 4.0, 5, 0.01, CellSource.OWN, 0.0),
            OwnCell(0.35, 5.0, 5, 0.01, CellSource.OWN, 0.0)))
        assertEquals(4.5, c.at(0.34)!!, 1e-12); assertNull(c.at(0.32)); assertNull(c.at(0.36))
    }
}
// EquivalenceLedgerTest (novo caso)
@Test fun `observacoes por combustivel saem na ordem de chegada e sobrevivem ao resetGas so na gasolina`() {
    // 3 quadros estáveis GASOLINA (rpm 2000, map 0.50, 5.0 ms) + 3 GNV (rpm 2000, map 0.50, 5.5 ms)
    assertEquals(1, ledger.petrolObservations().size); assertEquals(1, ledger.gasObservations().size)
    ledger.resetGas("TESTE")
    assertEquals(1, ledger.petrolObservations().size); assertEquals(0, ledger.gasObservations().size)
}
```

- [ ] **Step 2: Run to see it fail** — `git checkout -b work/platina-f4-cerebro-unico origin/OmegasPlatina`; commit do teste; `git push -u origin HEAD && gh pr create --draft --base OmegasPlatina --title "F4: cérebro único (Referência, Curvas Próprias, índice)" --body "WIP"`; `tools/ci/remote-test.sh gradle "com.omegas.prohub.equivalence.EquivalenceTypesTest"` → `REMOTE_TEST=FAIL` (`Unresolved reference: EquivalenceTolerances`).
- [ ] **Step 3: Implement** os tipos do índice literalmente; `ProofOutcome`; os dois acessos do ledger (`synchronized(lock) { petrol.toList() }`); as duas trocas de visibilidade.
- [ ] **Step 4: Run** `tools/ci/remote-test.sh gradle "com.omegas.prohub.equivalence.EquivalenceTypesTest"` e `tools/ci/remote-test.sh gradle "com.omegas.prohub.autocal.*"` → `REMOTE_TEST=PASS` (o segundo prova que `AutoMatchRefinedEngineTest` e os replays seguem verdes).
- [ ] **Step 5: Commit** `feat(equivalence): tipos do contrato F4 e acessos do ledger`

### Task 4.2: `ReferenceStore` — congelar, desfazer, deriva da ECU

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/equivalence/ReferenceStore.kt`
- Test: `app/src/test/java/com/omegas/prohub/equivalence/ReferenceStoreTest.kt`

**Interfaces:**
- Consumes: `AutoCalAcquisition.fromSnapshot` (JSON `points[]` com `fuel`, `state`, `timeRaw`, `mapRaw`, `timeMs`, `mapBar`, `counter`, `previous`), `EquivalenceLedger.ECU_REF_MIN_POINTS = 6`, `ECU_REF_MIN_SPAN_BAR = 0.20`, `ECU_REF_MARGIN_BAR = 0.03`.
- Produces:
  ```kotlin
  class ReferenceStore(private val file: File?, private val clock: () -> Long = System::currentTimeMillis) {
      companion object { const val FORMAT = "omegas-reference-v1"; const val PROVISIONAL_ID = "PROVISORIO"
          fun pointsFrom(acquisition: JSONObject?): List<RefPoint>   // gasolina, !previous, state ZONA_ADQUIRIDA; limpeza = setEcuPetrolReference; [] se imatura
          fun fingerprint(points: List<RefPoint>): String }           // JSONArray("map:ms:maturity"…).toString().hashCode().toString(16)
      fun current(): Reference?
      fun freeze(acquisition: JSONObject, now: Long): Reference       // id "REF-$now"; IllegalStateException("AQUISICAO_IMATURA") se pointsFrom vazio
      fun previous(): Reference?                                       // só memória (foto do Desfazer)
      fun restorePrevious(): Reference?                                // troca current↔previous; devolve o novo current
      fun endSession()                                                 // descarta previous
      fun ecuDrift(acquisition: JSONObject): Double?                   // max |live(m)/ref(m) − 1| nos MAP da Referência dentro da faixa viva; null sem ref, viva imatura ou sem sobreposição
      fun provisional(acquisition: JSONObject?): Reference?            // id PROVISIONAL_ID, frozenAt 0, não persiste; null se imatura
  }
  ```
  `RefPoint.maturity` = `counter` do ponto. `live(m)` = interpolação linear dos pontos de `pointsFrom(acquisition)`.

- [ ] **Step 1: Write the failing test**

```kotlin
class ReferenceStoreTest {
    private val ref = RealSessionReplaySupport.fixture(RealSessionReplaySupport.REFERENCE)
    private fun acq(seq: Int) = RealSessionReplaySupport.acquisition(RealSessionReplaySupport.snapshot(ref, seq))
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `congela a aquisicao madura de gasolina da ECU e relê do arquivo`() {
        val file = tmp.newFile("reference.json")
        val r = ReferenceStore(file).freeze(acq(95), 1_000L)
        assertEquals("REF-1000", r.id); assertEquals(1_000L, r.frozenAt); assertEquals(16, r.points.size)
        assertEquals(RefPoint(0.1865234375, 1.77734375, 10), r.points.first())
        assertEquals(r, ReferenceStore(file).current())
    }
    @Test fun `aquisicao imatura nao congela e nao apaga a atual`() {
        val s = ReferenceStore(null); val r = s.freeze(acq(95), 1L)
        val e = assertThrows(IllegalStateException::class.java) { s.freeze(acq(962), 2L) }
        assertEquals("AQUISICAO_IMATURA", e.message); assertEquals(r, s.current())
    }
    @Test fun `ECU reaprende gasolina depois do congelamento - referencia intacta e deriva reportada`() {  // Review Focus 1
        val file = tmp.newFile("reference.json"); val s = ReferenceStore(file); val r = s.freeze(acq(95), 1L)
        val bytes = file.readBytes()
        assertNull(s.ecuDrift(acq(962)))
        assertTrue(s.ecuDrift(acq(2183))!! > 0.10)
        assertEquals(r, s.current()); assertArrayEquals(bytes, file.readBytes())
    }
    @Test fun `congelar de novo guarda a anterior so ate o fim da sessao`() {
        val s = ReferenceStore(null); val a = s.freeze(acq(95), 1L); val b = s.freeze(acq(2183), 2L)
        assertEquals(a, s.previous()); assertEquals(a, s.restorePrevious()); assertEquals(b, s.previous())
        s.endSession(); assertNull(s.previous())
    }
    @Test fun `arquivo corrompido ou de outro formato abre sem referencia`() {  // Review Focus 5
        val f = tmp.newFile("reference.json"); f.writeText("{nao json"); assertNull(ReferenceStore(f).current())
        f.writeText("""{"format":"outro"}"""); assertNull(ReferenceStore(f).current())
    }
    @Test fun `provisoria vem da ECU ao vivo e nunca persiste`() {
        val f = File(tmp.root, "reference.json"); val p = ReferenceStore(f).provisional(acq(95))!!
        assertEquals(ReferenceStore.PROVISIONAL_ID, p.id); assertEquals(16, p.points.size); assertFalse(f.exists())
        assertNull(ReferenceStore(f).provisional(acq(962)))
    }
}
```

- [ ] **Step 2: Run** `tools/ci/remote-test.sh gradle "com.omegas.prohub.equivalence.ReferenceStoreTest"` → `REMOTE_TEST=FAIL` (`Unresolved reference: ReferenceStore`).
- [ ] **Step 3: Implement** conforme as assinaturas. Persistência: `{format, current:{id,frozenAt,ecuAcquisitionFingerprint,points:[[map,ms,maturity]…]}}`; `previous` nunca vai ao arquivo.
- [ ] **Step 4: Run** o mesmo comando → `REMOTE_TEST=PASS`.
- [ ] **Step 5: Commit** `feat(equivalence): ReferenceStore congela a gasolina da ECU e mede a deriva`

### Task 4.3: `OwnCurveFitter` — Curva Própria por célula de 0,02 bar (gate de validação cruzada)

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/equivalence/OwnCurveFitter.kt`
- Create (test helper): `app/src/test/java/com/omegas/prohub/equivalence/EquivalenceReplaySupport.kt`
- Test: `app/src/test/java/com/omegas/prohub/equivalence/OwnCurveFitterTest.kt`

**Interfaces:**
- Consumes: `AutoMatchRefinedEngine.pava`, `.whittaker`, `.Observation`, `.LAMBDA`, `.BAND_MATURE_COUNT`; `EquivalenceLedger.DRIVING_MIN_RPM`, `ECU_REF_MARGIN_BAR`.
- Produces:
  ```kotlin
  object OwnCurveFitter {
      const val GRID_MIN_BAR = 0.10; const val GRID_CELLS = 50          // [0,10; 1,10) bar
      const val PRIOR_N0 = 3.0                                           // = BAND_MATURE_COUNT, pseudo-contagem do prior
      fun cellOf(mapBar: Double): Int?; fun center(cell: Int): Double
      fun priorAt(reference: Reference, mapBar: Double): Double?         // ms; isotônico (pava em ln, pesos maturity) + interp linear; plano até ±ECU_REF_MARGIN_BAR; null fora
      fun fit(observations: List<EquivalenceLedger.Obs>, fuel: Fuel, prior: Reference?): OwnCurve
      fun mapFor(curve: OwnCurve, petrolMs: Double): Double?             // primeira travessia (espelho de inverse_first do oráculo)
  }
  ```
- Algoritmo (pinado; é o que os testes e a paridade não determinam sozinhos):
  1. Só `rpm ≥ DRIVING_MIN_RPM`. Por célula: `n`, `m = mediana ln(petrolMs)`, `dispersion = 1,4826·mediana|ln − m|` (0 com n < 2).
  2. `m' = pava(m, pesos n)` sobre as células com dados (isotônico entre células).
  3. Com prior: `p_j = ln priorAt(center_j)`; resíduo `y_j = m'_j − p_j`; `whittaker(u = centros, obs = (j, y_j, n/(n+PRIOR_N0)), prior = 0, priorWeights = PRIOR_N0/(n+PRIOR_N0) onde p_j existe senão 0, LAMBDA)`; `T_j = exp(p_j + r_j)`. Célula com dados e sem `p_j`: `T_j = exp(m'_j)`. Sem prior: Whittaker direto em `m'` só no vão [primeira, última célula com dados] (≥ 2 células; senão `exp(m')` nas próprias); fora → `null`.
  4. Final: `pava` em `ln T` nas células não nulas com pesos `n + PRIOR_N0`.
  5. `source`: n = 0 → `REFERENCE`; `n ≥ BAND_MATURE_COUNT` e `dispersion ≤ EquivalenceTolerances.MIN` → `OWN`; senão `BLENDED`. `divergence = T/priorAt − 1` se há prior e n > 0, senão `null`.
- Test helper `EquivalenceReplaySupport`: `fun ledger(name: String, keep: (RealSessionReplaySupport.Telemetry) -> Boolean = { true }): EquivalenceLedger`, `fun reference(name: String, seq: Int): Reference` (= `ReferenceStore(null).freeze(acquisition(snapshot), 0L)`), `fun curve(name: String, seq: Int): Pair<IntArray, IntArray>` (`PETR_INJ_TBP`, `MUL_ACT`), `fun writeAtMs(name: String): Long` (`kFactorWrites[0].recordedAtUtc`).

- [ ] **Step 1: Write the failing test**

```kotlin
class OwnCurveFitterTest {
    @Test fun `sem leitura a curva propria e o prior puro`() {
        val ref = EquivalenceReplaySupport.reference(REFERENCE, 95)
        val c = OwnCurveFitter.fit(emptyList(), Fuel.GASOLINA, ref)
        c.cells.filter { it.petrolMs != null }.forEach {
            assertEquals(CellSource.REFERENCE, it.source); assertEquals(0, it.samples)
            assertEquals(OwnCurveFitter.priorAt(ref, it.mapBar)!!, it.petrolMs!!, 1e-9) }
        assertEquals(OwnCurveFitter.GRID_CELLS, c.cells.size)
    }
    @Test fun `celula madura domina e a discordancia fica visivel`() {
        // 40 obs sintéticas em map 0.50 (célula 20) com petrolMs = 1.05 × priorAt(ref, 0.51), rpm 2000
        val cell = c.cells[20]
        assertEquals(CellSource.OWN, cell.source); assertEquals(40, cell.samples)
        assertEquals(0.05, cell.divergence!!, 0.01)
    }
    @Test fun `curva propria madura preve gasolina escondida melhor que a referencia sozinha`() {  // gate §1.6b
        // REFERENCE: ledger completo; obs de gasolina (rpm ≥ 1000) em blocos de 40 alternados (seq/40 % 2).
        // Ajusta com um bloco + prior(seq 95), julga no outro, e troca. Célula julgada = treino OWN e teste ≥ 3 obs.
        // erro = RMS de (ln previsto − mediana ln do teste); previsto = own.at(centro) × referência = priorAt(centro).
        assertTrue(judged >= 8)
        assertTrue("própria $rmsOwn ≥ referência $rmsRef", rmsOwn < rmsRef)
    }
    @Test fun `trocar a referencia so reajusta - amostras por celula identicas`() {  // Review Focus 3 (parte do fitter)
        val obs = EquivalenceReplaySupport.ledger(REFERENCE).petrolObservations()
        val a = OwnCurveFitter.fit(obs, Fuel.GASOLINA, EquivalenceReplaySupport.reference(REFERENCE, 95))
        val b = OwnCurveFitter.fit(obs, Fuel.GASOLINA, EquivalenceReplaySupport.reference(REFERENCE, 2183))
        assertEquals(a.cells.map { it.samples }, b.cells.map { it.samples })
        assertEquals(a.cells.map { it.dispersion }, b.cells.map { it.dispersion })
    }
    @Test fun `mapFor devolve o MAP da primeira travessia`() { /* curva 0.33→4.0, 0.35→5.0: mapFor(4.5) == 0.34 ± 1e-12; mapFor(6.0) == null */ }
}
```

> Nota de engenharia sobre o gate: a spec fala em "faixa escondida". Escondendo uma faixa de MAP inteira, a célula vazia **por definição** vale a Referência (§1.6b), e o teste viraria tautologia (erro igual). O gate compara então nas células onde a Curva Própria é **madura** contra leituras **não usadas no ajuste** (blocos alternados) — é a pergunta que a spec quer responder ("a curva própria madura agrega?"). Sondagem prévia nas leituras cruas da sessão REFERENCE: ~9–10 células julgáveis, RMS própria ≈ 8% contra Referência 10–16%.

- [ ] **Step 2: Run** `tools/ci/remote-test.sh gradle "com.omegas.prohub.equivalence.OwnCurveFitterTest"` → `REMOTE_TEST=FAIL` (`Unresolved reference: OwnCurveFitter`).
- [ ] **Step 3: Implement** o algoritmo acima e o helper de teste.
- [ ] **Step 4: Run** o mesmo comando → `REMOTE_TEST=PASS`. Se o gate de validação cruzada falhar, **não** afrouxar: ler `rmsOwn/rmsRef/judged` no log e reportar ao dono (a Curva Própria não estaria agregando, e a spec diz que o PR não passa).
- [ ] **Step 5: Commit** `feat(equivalence): Curva Própria por célula de 0,02 bar com Referência como prior`

### Task 4.4: `ExperienceMeter` — tremor e quase-apagão no MAP equivalente

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/equivalence/ExperienceMeter.kt`
- Test: `app/src/test/java/com/omegas/prohub/equivalence/ExperienceMeterTest.kt`

**Interfaces:**
- Consumes: `StallWatch` (instância sombra sem arquivo), `StallWatch.KIND_NEAR`, `KIND_STALL`, `OwnCurveFitter.cellOf`, `EquivalenceLedger.STABLE_MAP_SPREAD`, `DRIVING_MIN_RPM`.
- Produces:
  ```kotlin
  class ExperienceMeter(private val file: File?, private val clock: () -> Long = System::currentTimeMillis) {
      companion object { const val FORMAT = "omegas-experience-meter-v1"; const val WINDOW_FRAMES = 5
          const val WINDOW_MAX_MS = 2_000L; const val CELL_CAP = 60; const val MIN_WINDOWS = 6
          const val MIN_HOURS = 0.05; const val MAX_FRAME_DT_MS = 1_000L; const val POOL_CELLS = 1 }
      fun accept(t: Long, fuel: String, rpm: Double, map: Double, petrolMs: Double)
      fun onStall(event: JSONObject)        // evento do StallWatch principal (só GNV por construção)
      fun resetGas(reason: String)          // GNV medido com a curva antiga sai; gasolina fica
      fun revision(): Long
      fun flush()
      fun reading(): Reading
      class Reading internal constructor(/* cópias imutáveis por combustível × célula */) {
          fun roughnessRatio(mapBar: Double): Double?   // mediana GNV / mediana gasolina (piso 1 rpm), células j±POOL_CELLS, ≥ MIN_WINDOWS cada
          fun nearStallRatio(mapBar: Double): Double? } // ((nG+0,5)/hG) / ((nP+0,5)/hP), horas ≥ MIN_HOURS cada
  }
  ```
- Pinado: janela deslizante de `WINDOW_FRAMES` quadros do mesmo combustível (GASOLINA|GNV), todos `rpm ≥ 1000`, span ≤ `WINDOW_MAX_MS`, spread de MAP ≤ 0,03; tremor = RMS do resíduo da reta de mínimos quadrados rpm×t (tendência removida); janela aceita é limpa (sem sobreposição); célula = `cellOf(média do MAP)`, cada célula guarda os últimos `CELL_CAP`. Horas por célula = Σ `min(dt, MAX_FRAME_DT_MS)` com `rpm ≥ 1000`. Quase-apagões da gasolina: quadros de gasolina entram numa `StallWatch(null, clock)` sombra **rotulados `"GNV"`** (a `StallWatch` só detecta no GNV; a sombra aplica o mesmo critério à gasolina sem tocar o núcleo); `KIND_NEAR` e `KIND_STALL` contam, célula = `cellOf(event.mapBar)`.

- [ ] **Step 1: Write the failing test**

```kotlin
class ExperienceMeterTest {
    @Test fun `tendencia de RPM nao e tremor`() {
        // GNV, map 0.60, rpm = 2000 + 100·(t/1000), quadros a cada 300 ms por 60 s, sem ruído
        // + gasolina idem com ruído ±20 alternado → roughnessRatio(0.60) <= 0.05
    }
    @Test fun `GNV pobre escorrega de ms mas casa com a gasolina pelo MAP`() {  // Review Focus 4
        // gasolina: map 0.60, petrolMs 6.0, rpm 2000 ± 20 (alternado); GNV: map 0.60, petrolMs 6.9 (+15%), rpm 2000 ± 40
        assertEquals(2.0, m.reading().roughnessRatio(0.60)!!, 0.05)
        assertNull(m.reading().roughnessRatio(0.70))   // a célula do "ms cru" do GNV (onde a gasolina pede 6,9 ms) não recebe nada
    }
    @Test fun `quase-apagao por hora compara GNV com gasolina no mesmo MAP`() {
        // 0,1 h de condução em cada combustível em map 0.60; 2 eventos GNV via onStall(kind QUASE_APAGOU, mapBar 0.60); 0 na gasolina
        assertEquals(5.0, m.reading().nearStallRatio(0.60)!!, 1e-9)
    }
    @Test fun `resetGas apaga so o GNV e persiste no arquivo`() { /* após resetGas roughnessRatio == null; janelas de gasolina relidas de ExperienceMeter(file) */ }
    @Test fun `replay real da sessao REFERENCE mede tremor em ao menos um MAP`() {
        // todos os quadros de REFERENCE via accept; existe mapBar em 0.20..0.98 (passo 0.02) com roughnessRatio != null
    }
}
```

- [ ] **Step 2: Run** `tools/ci/remote-test.sh gradle "com.omegas.prohub.equivalence.ExperienceMeterTest"` → `REMOTE_TEST=FAIL`.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** → `REMOTE_TEST=PASS`.
- [ ] **Step 5: Commit** `feat(equivalence): ExperienceMeter mede tremor e quase-apagão no MAP equivalente`

### Task 4.5: `UsageMeter` — onde o dono passa o tempo (últimas 10 sessões)

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/equivalence/UsageMeter.kt`
- Test: `app/src/test/java/com/omegas/prohub/equivalence/UsageMeterTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  class UsageMeter(private val file: File?, private val clock: () -> Long = System::currentTimeMillis) {
      companion object { const val FORMAT = "omegas-usage-meter-v1"; const val MAX_FRAME_DT_MS = 1_000L }
      fun accept(t: Long, rpm: Double, map: Double, sessionId: Long)   // sessionId novo abre sessão; mantém USAGE_SESSIONS
      fun revision(): Long
      fun flush()
      fun reading(): Reading
      class Reading internal constructor(val cellMs: DoubleArray) {    // soma por célula da grade OwnCurveFitter
          fun byPoint(axisMs: List<Double>, ownPetrol: OwnCurve): List<Double> }
  }
  ```
- Pinado: conta qualquer combustível com `rpm ≥ DRIVING_MIN_RPM` e `map > 0`; `dt = min(t − t_anterior, MAX_FRAME_DT_MS)` (primeiro quadro da sessão: 0). `byPoint`: célula → `T = ownPetrol.at(center)` (pula se null) → ponto mais próximo em `ln t`; denominador = só o tempo atribuível; soma 1,0 (ou tudo 0).

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun `fracao do tempo de conducao por ponto pelo MAP equivalente`() {
    // sessão 1: 30 s em map 0.50 e 10 s em map 0.70 (quadros de 500 ms, rpm 2000) + 20 s de lenta (rpm 850)
    // ownPetrol sintética linear: T(map) = 10·map ms → 5,0 ms e 7,0 ms; eixo = eixo real REFERENCE seq 95
    val u = meter.reading().byPoint(axis, own)
    assertEquals(0.75, u[9], 1e-3); assertEquals(0.25, u[13], 1e-3); assertEquals(1.0, u.sum(), 1e-9)
}
@Test fun `janela movel guarda so as ultimas 10 sessoes`() { /* 11 sessões, só a 1ª em map 0.30 → célula 10 zera */ }
@Test fun `dt longo nao infla o uso`() { /* dois quadros a 60 s → conta 1 s */ }
@Test fun `persiste e relê igual`() { /* flush(); UsageMeter(file).reading().cellMs contentEquals */ }
```

- [ ] **Step 2: Run** `tools/ci/remote-test.sh gradle "com.omegas.prohub.equivalence.UsageMeterTest"` → `REMOTE_TEST=FAIL`.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** → `REMOTE_TEST=PASS`.
- [ ] **Step 5: Commit** `feat(equivalence): UsageMeter fração de condução por ponto`

### Task 4.6: `EquivalenceEngine` — estados, índice, próxima ação, proposta (gates do índice)

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/equivalence/EquivalenceEngine.kt`
- Test: `app/src/test/java/com/omegas/prohub/equivalence/EquivalenceEngineTest.kt`, `app/src/test/java/com/omegas/prohub/equivalence/EquivalenceRealSessionTest.kt`

**Interfaces:**
- Consumes: Tasks 4.1–4.5; `AutoMatchRefinedEngine.refine/Input/interp/TELEMETRY_MIN_MS/BAND_MATURE_COUNT/AXIS_COUNTS_PER_MS/Q14`.
- Produces:
  ```kotlin
  data class EquivalenceInput(
      val axisRaw: IntArray, val mulActRaw: IntArray,
      val reference: Reference?,            // ReferenceStore.current()
      val provisional: Reference?,          // ReferenceStore.provisional(acquisition), usado só se reference == null
      val petrolObs: List<EquivalenceLedger.Obs>, val gasObs: List<EquivalenceLedger.Obs>,
      val experience: ExperienceMeter.Reading, val usage: UsageMeter.Reading,
      val operation: String? = null,        // F5 preenche (texto da operação em andamento); F4 sempre null
      val pointGainScale: DoubleArray? = null)
  object EquivalenceEngine {
      const val CONFIDENCE_MAX = EquivalenceTolerances.MIN   // meia-largura 1,96·disp/√n
      const val COLLECT_MIN_USAGE = 0.02
      fun evaluate(input: EquivalenceInput, judge: (List<EquivalencePoint>) -> ProofOutcome = { ProofOutcome.NONE }): EquivalenceResult
      fun curveFromSnapshot(snapshot: JSONObject?): Pair<IntArray, IntArray>?   // PETR_INJ_TBP, MUL_ACT VALID com 30 valores
  }
  ```
- Regras pinadas (spec §1.1–§1.5):
  - Prior = `reference ?: provisional`; `provisional` no resultado = `reference == null`. `ownPetrol = fit(petrolObs, GASOLINA, prior)`, `ownGas = fit(gasObs, GNV, prior)` (hipótese "GNV = gasolina" como prior; o estado é que impede afirmar sem amostra).
  - Ponto i: `MAP_i = mapFor(ownPetrol, axisMs_i)`; `T_g = ownGas.at(MAP_i)`; `kTarget = interp(T_g, axisMs, K)·T_g/axisMs_i`; `mixture = kTarget/kCurrent − 1`; célula de evidência = `cellOf(MAP_i)`; `samples` = amostras GNV dessa célula; `dispersion = max(disp gasolina, disp GNV)` da célula; `tolerance = EquivalenceTolerances.tolerance(dispersion)`; `slope = max(|Δ ln K/Δ ln t|)` com os vizinhos na curva atual; `usage = usage.byPoint(...)[i]`; `roughnessRatio/nearStallRatio = experience.reading(MAP_i)`; `sources`: `TELEMETRIA` se samples > 0 ou célula de gasolina com n > 0, `ECU_REF` se a célula de gasolina não é `OWN` e há Referência congelada, `AUTOCAL` idem em modo provisório.
  - Estado base: `axisMs_i < TELEMETRY_MIN_MS` ou `MAP_i == null` → `SEM_DADOS`; `samples < BAND_MATURE_COUNT` ou `1,96·dispersion/√samples > CONFIDENCE_MAX` → `APRENDENDO`; `mixture == null` → `MEDIDO`; `|mixture| ≤ tolerance` → `EQUIVALENTE`; `mixture > tolerance` → `POBRE`; senão `RICO`. Depois `judge(base)` sobrepõe (`EM_PROVA/CONFIRMADO/CONTESTADO/INCONCLUSIVO`).
  - Índice: entram pontos com estado ∉ {`SEM_DADOS`,`APRENDENDO`,`MEDIDO`} e `mixture != null`; `equivalente = |mixture| ≤ tolerance`; `index = Σ usage·eq / Σ usage` (null se Σ usage = 0); `coverage` = quantos entram.
  - Proposta: `AutoMatchRefinedEngine.refine(Input(axisRaw, mulActRaw, null×6, telemetryPairs, pointGainScale))` com um par por obs GNV (`rpm ≥ 1000`): `(ownPetrol.at(map), ownGas.at(map))`, ambos não nulos e `tp ≥ TELEMETRY_MIN_MS`.
  - Próxima ação, primeira que casar (textos exatos, decimais com vírgula `Locale("pt","BR")`):
    1. `operation != null` → `OPERATION`, texto = `operation`.
    1b. `reference == null && provisional != null` → `FREEZE_REFERENCE`, "Congelar esta curva como referência", route `autocal`, subpage `referencia`.
    2. `CONTESTADO` → `CONTESTED`, "Ajuste em %.1f–%.1f ms piorou a suavidade · Desfazer", route `curve`, subpage `equivalencia`.
    3. `POBRE/RICO` e `proposal?.mode == EQUIVALENCE` com `refinedRaw != currentRaw` → `APPLY`, "N pontos pobres[ e M ricos] entre %.1f e %.1f ms (a–b%) · Aplicar ajuste" (+ " · sem referência da ECU" se `reference == null`), route `curve`, subpage `equivalencia`, `pointIndexes` ordenados por uso decrescente.
    4. `EM_PROVA` → `PROVING`, "Rodando para provar o ajuste · faltam ~N min de condução nessa faixa" (N = `remainingMinutes`), route `refino`, subpage `pontos`.
    5. `SEM_DADOS/APRENDENDO` com `usage ≥ COLLECT_MIN_USAGE` → `COLLECT`, "Rode %s (~%.1f bar) para eu medir entre %.1f e %.1f ms" com `em plano` (<0,45 bar), `em subida leve` (0,45–0,75), `em subida forte` (>0,75); se `coverage == 0` e nada com uso → `COLLECT`, "Rode no GNV para eu começar a medir", route `refino`, subpage `pontos`.
    6. `NOTHING`, "Equivalente. Nada a fazer.", route `null`.

- [ ] **Step 1: Write the failing tests**

```kotlin
class EquivalenceEngineTest {   // sintético: gasolina T = 10·map ms; GNV = gasolina × (1 + m) por MAP; 20 obs/célula, ruído ±0,5%
    @Test fun `GNV 6 por cento pobre em 6 a 7 ms vira POBRE e a proposta sobe K ali`() {
        // m = +0,06 em map 0.60–0.70, 0 no resto; K = 1,0; ref congelada = gasolina sintética
        assertEquals(PointState.POBRE, r.points[12].state); assertEquals(0.06, r.points[12].mixture!!, 0.01)
        assertEquals(NextActionKind.APPLY, r.nextAction.kind)
        assertTrue(r.proposal!!.refinedRaw[12] > r.proposal!!.currentRaw[12])
    }
    @Test fun `celula rala ganha folga - tolerancia 2x dispersao`() { /* disp 3% → tolerance 0.06; mixture 0.05 → EQUIVALENTE */ }
    @Test fun `indice pondera por uso e ignora pontos sem confianca`() {
        // 2 pontos medidos: uso 0,75 equivalente e 0,25 pobre; 1 ponto APRENDENDO com uso alto fica fora
        assertEquals(0.75, r.index!!, 1e-9); assertEquals(2, r.coverage)
    }
    @Test fun `prioridade da proxima acao segue a spec`() {
        // judge devolve CONTESTADO no ponto 12 → CONTESTED mesmo com POBRE em outros; texto "Ajuste em 6,0–6,0 ms piorou a suavidade · Desfazer"
        // judge devolve EM_PROVA e remainingMinutes 7, nada pobre → "Rodando para provar o ajuste · faltam ~7 min de condução nessa faixa"
        // operation = "Gravando Curva K" → OPERATION antes de tudo
    }
    @Test fun `sem referencia propoe com curva propria madura e avisa`() { /* reference=null, provisional=null → texto termina com " · sem referência da ECU" */ }
}

class EquivalenceRealSessionTest {
    @Test fun `app novo com ECU madura e sem telemetria pede para congelar`() {  // Review Focus 4 / gate (c)
        val (axis, k) = EquivalenceReplaySupport.curve(REFERENCE, 95)
        val prov = ReferenceStore(null).provisional(acquisition(snapshot(REFERENCE, 95)))
        val r = EquivalenceEngine.evaluate(EquivalenceInput(axis, k, null, prov, emptyList(), emptyList(), ExperienceMeter(null).reading(), UsageMeter(null).reading()))
        assertTrue(r.provisional); assertEquals(NextActionKind.FREEZE_REFERENCE, r.nextAction.kind)
        assertEquals("autocal", r.nextAction.route); assertEquals("referencia", r.nextAction.subpage)
        assertTrue(r.ownPetrol.cells.filter { it.petrolMs != null }.all { it.source == CellSource.REFERENCE })
        r.points.forEach { p ->
            val known = p.axisMs >= 3.0 && OwnCurveFitter.mapFor(r.ownPetrol, p.axisMs) != null
            assertEquals(if (known) PointState.APRENDENDO else PointState.SEM_DADOS, p.state) }
        assertTrue(r.points.count { it.state == PointState.APRENDENDO } >= 13); assertNull(r.index); assertEquals(0, r.coverage)
    }
    @Test fun `indice sobe depois da escrita real de K na sessao AUTOMATCH`() {  // gate §1.7 (replay real)
        // escrita 16:04:38.543Z; K antes = seq 634, K depois = seq 699; janela depois termina em seq 1401 (16:08:47.497Z),
        // antes do AutoMatch nativo trocar a curva (seq 1716). Gasolina = sessão inteira (independe de K). Ref congelada = seq 634.
        assertTrue(before.coverage >= 2 && after.coverage >= 2)
        assertTrue("antes ${before.index} depois ${after.index}", after.index!! > before.index!!)
    }
    @Test fun `na sessao REFERENCE o indice cai depois da escrita - o dono rotulou a curva anterior como a melhor`() {
        // escrita 20:31:57.487Z; K antes = seq 2183, depois = seq 2550; ref = seq 95. Rótulo da fixture: "melhor consumo GNV (K pré-reset seq 95)".
        assertTrue(after.index!! < before.index!!)
    }
    @Test fun `proposta aplicada sobe o indice sob o modelo linear de compensacao da ECU (simulado, nao e prova fisica)`() {  // gate (d) simulado
        // Suposição declarada: com K multiplicado por f no ms em que o GNV opera, a central original reduz petrol_ms por f
        // (malha fechada linear, sem tempo morto do injetor). REFERENCE pré-escrita (K seq 2183, ref 95):
        // r0 = evaluate; K1 = r0.proposal.refinedRaw; GNV obs com petrolMs' = petrolMs / (K1(petrolMs)/K0(petrolMs)); r1 = evaluate(K1).
        assertEquals(AutoMatchRefinedEngine.Mode.EQUIVALENCE, r0.proposal!!.mode)
        assertTrue(r1.index!! > r0.index!!); assertTrue(r1.coverage >= r0.coverage - 1)
    }
    @Test fun `avaliacao fria do corpus real cabe no tique do servico`() { /* corpus GNV_ONLY+AUTOMATCH+REFERENCE; mediana de 5 evaluate < 300 ms */ }
}
```

> Sondagem prévia (leituras cruas, tolerância fixa 4%): AUTOMATCH 0,053 (75 leituras) → 0,211 (161); REFERENCE 0,31 → 0,18. Se o gate do REFERENCE falhar com o motor completo, isso é **achado para o dono** (a métrica discorda do rótulo dele), não limiar para afrouxar.

- [ ] **Step 2: Run** `tools/ci/remote-test.sh gradle "com.omegas.prohub.equivalence.EquivalenceEngineTest"` → `REMOTE_TEST=FAIL` (`Unresolved reference: EquivalenceEngine`).
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `tools/ci/remote-test.sh gradle "com.omegas.prohub.equivalence.EquivalenceEngineTest"` e `tools/ci/remote-test.sh gradle "com.omegas.prohub.equivalence.EquivalenceRealSessionTest"` → `REMOTE_TEST=PASS`.
- [ ] **Step 5: Commit** `feat(equivalence): EquivalenceEngine estados, índice, próxima ação e proposta`

### Task 4.7: `EquivalencePhases` — prova por ponto

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/autocal/EquivalencePhases.kt` (ex-`RefinementAutopilot.kt`): companion (pré-F1 `:30-46`, âncora `const val FORMAT = "omegas-refinement-autopilot-v1"`), `json()` (`:195`), `save()`/`load()` (`:285-317`)
- Test: `app/src/test/java/com/omegas/prohub/autocal/EquivalencePhasesTest.kt` (novos casos)

**Interfaces:**
- Consumes: `EquivalencePoint`, `PointState`, `ProofOutcome`, `EquivalenceTolerances.WORSE_DELTA/WORSE_ERROR`, `RefinementJournal.MIN_BAND_SAMPLES` (8), `RefinementJournal.VERIFY_PARTIAL_ONLINE_MS` (15 min).
- Produces (acréscimos; `observe`, `takeAlert`, `json`, `FORMAT` e nome do arquivo inalterados):
  ```kotlin
  companion object { const val PROOF_MIN_SAMPLES = RefinementJournal.MIN_BAND_SAMPLES
                     const val PROOF_TIMEBOX_ONLINE_MS = RefinementJournal.VERIFY_PARTIAL_ONLINE_MS }
  fun beginProof(pointIndexes: List<Int>, baseline: List<EquivalencePoint>)   // substitui prova anterior desses pontos
  fun judgePoints(points: List<EquivalencePoint>, ecuOnline: Boolean): ProofOutcome
  fun restartProofs(reason: String)       // Referência trocada: provas abertas voltam a onlineMs = 0
  fun interruptProofs(reason: String)     // Mapa K / AutoMatch nativo: provas abertas somem (sem estado)
  ```
- Regras pinadas: `onlineMs` soma `dt` do relógio (teto 10 s, como `observe`) só com `ecuOnline`. Prova aberta: `samples < PROOF_MIN_SAMPLES` ou `mixture == null` → `EM_PROVA`; atingiu `PROOF_TIMEBOX_ONLINE_MS` sem isso → `INCONCLUSIVO`. Com amostra: `experiênciaPiorou` = algum de (`roughnessRatio`, `nearStallRatio`) com base e agora não nulos e `ln(agora) − ln(base) > WORSE_DELTA` e `ln(agora) > WORSE_ERROR` (base nula não contesta); `|m| ≤ tolerance` e não piorou → `CONFIRMADO`; `|m| < |m_base|` e piorou → `CONTESTADO`; qualquer outro → prova fecha **sem sobreposição** (o ponto volta ao estado base POBRE/RICO e a próxima ação propõe de novo; mistura que piorou continua coberta por `RESTAURAR_TRECHO` do diário). Veredito fechado persiste até nova `beginProof` do ponto. Persistência: chave opcional `proofs` no mesmo arquivo `omegas-refinement-autopilot-v1`; `json()` ganha `proofs: [{index, state, onlineMs, mixtureBefore, mixtureNow}]`.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun `ajuste gravado entra em prova e confirma com amostra nova dentro da tolerancia`() {
    phases.beginProof(listOf(12), listOf(point(12, mixture = 0.06, samples = 20, rough = 1.0)))
    assertEquals(PointState.EM_PROVA, phases.judgePoints(listOf(point(12, mixture = 0.01, samples = 3)), true).states[12])
    assertEquals(PointState.CONFIRMADO, phases.judgePoints(listOf(point(12, mixture = 0.01, samples = 8, rough = 1.02)), true).states[12])
}
@Test fun `mistura melhorou e tremor piorou vira CONTESTADO`() { /* base 0.06/rough 1.0 → agora 0.02/rough 1.10 */ }
@Test fun `piora de experiencia abaixo de 4 por cento nao contesta`() { /* rough 1.0 → 1.035: CONFIRMADO */ }
@Test fun `sem amostra ate o timebox de 15 min de conducao vira INCONCLUSIVO e o restante e informado`() {
    // relógio +10 s por judge com ecuOnline: aos 60 s remainingMinutes == 14; após 15 min → INCONCLUSIVO; offline não conta
}
@Test fun `trocar referencia recomeca a prova sem perder o ponto`() { /* restartProofs → proofs[0].onlineMs == 0 e ainda EM_PROVA */ }
@Test fun `arquivo antigo sem proofs abre sem crash e sem provas`() {  // Review Focus 5
    file.writeText("""{"format":"omegas-refinement-autopilot-v1","phase":"ESTAVEL","lastCount":3,"quietMs":0}""")
    val p = EquivalencePhases(file) { now }
    assertEquals(0, p.json().optJSONArray("proofs")?.length() ?: 0)
    assertTrue(p.judgePoints(emptyList(), true).states.isEmpty())
}
```

- [ ] **Step 2: Run** `tools/ci/remote-test.sh gradle "com.omegas.prohub.autocal.EquivalencePhasesTest"` → `REMOTE_TEST=FAIL` (`Unresolved reference: beginProof`).
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** o mesmo → `REMOTE_TEST=PASS` (casos antigos de fase continuam verdes).
- [ ] **Step 5: Commit** `feat(autocal): EquivalencePhases prova cada ponto ajustado (EM_PROVA → veredito)`

### Task 4.8: `EquivalenceRuntime` + `EquivalenceJson` — o que o serviço chama

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/equivalence/EquivalenceRuntime.kt`
- Create: `app/src/main/java/com/omegas/prohub/equivalence/EquivalenceJson.kt`
- Test: `app/src/test/java/com/omegas/prohub/equivalence/EquivalenceRuntimeTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  class EquivalenceRuntime(root: File?, private val clock: () -> Long = System::currentTimeMillis) {
      val references: ReferenceStore     // root/equivalence_reference.json
      val experience: ExperienceMeter    // root/experience_meter.json
      val usage: UsageMeter              // root/usage_meter.json
      fun onFrame(t: Long, fuel: String, rpm: Double, map: Double, petrolMs: Double, sessionId: Long)
      fun onStall(event: JSONObject)
      fun onGasReset(reason: String, phases: EquivalencePhases)        // experience.resetGas; interruptProofs exceto reason == "CURVA_K_GRAVADA"
      fun onCurveWritten(beforeRaw: IntArray, afterRaw: IntArray, phases: EquivalencePhases)  // beginProof(índices com before≠after, last()?.points ?: emptyList())
      fun freeze(acquisition: JSONObject?, phases: EquivalencePhases): Reference   // references.freeze(acq ?: error("AQUISICAO_IMATURA"), clock()); restartProofs("REFERENCIA_CONGELADA")
      fun restorePreviousReference(phases: EquivalencePhases): Reference?          // Desfazer do REFERENCE_FREEZE (F5 enfileira)
      fun evaluate(ledger: EquivalenceLedger, phases: EquivalencePhases, snapshot: JSONObject?, acquisition: JSONObject?,
                   ecuOnline: Boolean, gainScale: (List<Double>) -> DoubleArray?): EquivalenceResult?   // null sem Curva K lida
      fun last(): EquivalenceResult?
      fun json(acquisition: JSONObject?): JSONObject
      fun flush()
  }
  object EquivalenceJson {
      const val FORMAT = "omegas-equivalence-result-v1"
      fun result(result: EquivalenceResult?, reference: Reference?, ecuDrift: Double?, previous: Reference?): JSONObject
  }
  ```
- Esquema JSON pinado (F5 mapeia em `reference`, `points`, `index`, `nextAction`):
  `{ok, format, available, reason?, index|null, coverage, provisional, nextAction:{kind,text,route|null,subpage|null,pointIndexes[]}, points:[{index,axisMs,kCurrent,kTarget|null,mixture|null,tolerance,roughnessRatio|null,nearStallRatio|null,slope|null,usage,samples,sources[],state}], ownPetrol:{fuel,cells:[{mapBar,petrolMs|null,samples,dispersion,source,divergence|null,relearnSuggested}]}, ownGas:{…}, proposal:{mode,telemetryOnly,currentRaw[],refinedRaw[],origins[]}|null, reference:{id,frozenAt,ecuAcquisitionFingerprint,points:[{mapBar,petrolMs,maturity}],ecuDrift|null,previousId|null}|null, automatic:false}`. `relearnSuggested = source == OWN && |divergence| > DIVERGENCE_ALARM` (spec §1.6b). Sem resultado: `{ok:true, available:false, reason:"CURVA_K_NAO_LIDA", …reference}`.
- `evaluate` reavalia sempre que há prova aberta (o relógio da prova anda); sem prova aberta, devolve `last()` se a chave `ledger.revision()|reference.id|provisional fingerprint|fingerprint(MUL_ACT)|usage.revision()|experience.revision()` não mudou.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun `trocar a referencia nao perde dados do livro so reajusta e recomeca a prova`() {  // gate §7 / Review Focus 3
    // ledger = REFERENCE completo; rt.onFrame em todos os quadros; snapshot/acq seq 95 e 2183
    rt.freeze(acq95, phases); val r1 = rt.evaluate(ledger, phases, snap2183, acq2183, true) { null }!!
    rt.onCurveWritten(k(2183), k(2183).also { it[12] += 400 }, phases)
    val nP = ledger.petrolObservations().size; val nG = ledger.gasObservations().size
    rt.freeze(acq2183, phases); val r2 = rt.evaluate(ledger, phases, snap2183, acq2183, true) { null }!!
    assertEquals(nP, ledger.petrolObservations().size); assertEquals(nG, ledger.gasObservations().size)
    assertEquals(r1.ownPetrol.cells.map { it.samples }, r2.ownPetrol.cells.map { it.samples })
    assertEquals(PointState.EM_PROVA, r2.points[12].state)
    assertEquals(0L, phases.json().getJSONArray("proofs").getJSONObject(0).getLong("onlineMs"))
    assertNotEquals(r1.ownPetrol.cells.map { it.divergence }, r2.ownPetrol.cells.map { it.divergence })
}
@Test fun `json do resultado traz as chaves do contrato e a deriva da ECU`() {
    // freeze(acq95); evaluate; json(acq2183): reference.ecuDrift > 0.10, reference.id == "REF-…", points.length() == 30,
    // nextAction.kind in NextActionKind.values().map { it.name }, ownPetrol.cells.length() == 50, automatic == false
}
@Test fun `sem Curva K lida o resultado diz por que`() { /* evaluate(snapshot = null) == null; json(null).reason == "CURVA_K_NAO_LIDA" */ }
@Test fun `congelar sem aquisicao madura falha legivel e nao muda nada`() { /* freeze(acq962) lança "AQUISICAO_IMATURA"; references.current() inalterada */ }
```

- [ ] **Step 2: Run** `tools/ci/remote-test.sh gradle "com.omegas.prohub.equivalence.EquivalenceRuntimeTest"` → `REMOTE_TEST=FAIL`.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** → `REMOTE_TEST=PASS`.
- [ ] **Step 5: Commit** `feat(equivalence): EquivalenceRuntime e JSON do resultado`

### Task 4.9: Serviço alimenta o cérebro

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt`
  - campos `:108-117` (âncora `lateinit var stallWatch: StallWatch`): `lateinit var equivalenceRuntime: EquivalenceRuntime; private set`
  - `onCreate` `:157-160` (âncora `stallWatch = StallWatch(File(paths.runtimeRoot`): `equivalenceRuntime = EquivalenceRuntime(paths.runtimeRoot)`
  - Mapa K confirmado `:196-199` (âncora `equivalence.resetGas("MAPA_K_GRAVADO")`) e AutoMatch nativo `:233-236` (âncora `equivalence.resetGas("AUTOMATCH_NATIVO")`): `equivalenceRuntime.onGasReset(<mesmo motivo>, <EquivalencePhases>)`
  - `recordCurveExperiment` `:766-797`: `equivalenceRuntime.onCurveWritten(beforeRaw, afterRaw, <phases>)` logo após `refinementJournal.recordCurveWrite(`; no `finally`, junto de `equivalence.resetGas("CURVA_K_GRAVADA")`, `equivalenceRuntime.onGasReset("CURVA_K_GRAVADA", <phases>)`
  - `observeRefinement` `:833-866`: a linha `if (usb.connected) equivalence.setEcuPetrolReference(EcuPetrolReference.fromAcquisition(...))` passa a ser: com `equivalenceRuntime.references.current()` não nula → `equivalence.setEcuPetrolReference(ref.points.map { it.mapBar to it.petrolMs })` (a Referência congelada, não a ECU ao vivo, §1.6); senão o ramo atual (modo provisório). Depois do `observe(...)`: `equivalenceRuntime.evaluate(equivalence, <phases>, JSONObject(nativeAutoCalSnapshotJson()), acquisition, usb.connected && runtime.ready, refinementJournal::pointGainScale)`; grava `sessionRecorder.record("equivalence_result", "autocal", {index, coverage, provisional, nextAction}, force = true)` quando `(index arredondado a 0,001, coverage, nextAction.kind, provisional)` muda (índice guardado por sessão, §1.3).
  - `consumeEngineEvent` `:935-944` (âncora `equivalence.accept(`): `equivalenceRuntime.onFrame(t, fuel, rpm, map, petrolMs, if (usb.connected) usb.connectionSessionId else 0L)`; `:950-963` (âncora `stallWatch.accept(`) dentro do `?.let { event ->`: `equivalenceRuntime.onStall(event)`
  - `handleUsbTransition` (âncora `lastUsbSessionId = sessionId`, `:709`): ao mudar de sessão, `equivalenceRuntime.references.endSession()` e `equivalenceRuntime.flush()`
  - novos: `fun freezeReference(): String` (ok → `sessionRecorder.record("reference_frozen", …)`, `{ok:true, reference}`; `IllegalStateException` → `{ok:false, reason:"AQUISICAO_IMATURA", message:"A ECU ainda não tem curva de gasolina madura para congelar."}`) e `fun equivalenceResultJson(): String = equivalenceRuntime.json(acquisition atual).toString()`
- Test: `tests/test_f4_equivalence_wiring_contract.py`

**Interfaces:**
- Consumes: Task 4.8. Produces: `TelemetryForegroundService.freezeReference(): String`, `equivalenceResultJson(): String`, evento de sessão `equivalence_result` e `reference_frozen`.

- [ ] **Step 1: Write the failing test**

```python
import pathlib, re
root = pathlib.Path(__file__).resolve().parents[1]
svc = (root / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text(encoding="utf-8")
for must in ("equivalenceRuntime = EquivalenceRuntime(paths.runtimeRoot)", "equivalenceRuntime.onFrame(",
             "equivalenceRuntime.onStall(event)", "equivalenceRuntime.onCurveWritten(", "equivalenceRuntime.evaluate(",
             'equivalenceRuntime.onGasReset("MAPA_K_GRAVADO"', 'equivalenceRuntime.onGasReset("AUTOMATCH_NATIVO"',
             'record("equivalence_result"', 'record("reference_frozen"', "fun freezeReference(): String",
             "fun equivalenceResultJson(): String", "references.endSession()"):
    assert must in svc, must
live = svc.index("EcuPetrolReference.fromAcquisition")
assert svc.rfind("references.current()", 0, live) != -1, "a curva viva da ECU só vale sem Referência congelada"
pkg = root / "app/src/main/java/com/omegas/prohub/equivalence"
for f in pkg.glob("*.kt"):
    src = f.read_text(encoding="utf-8")
    assert "import android." not in src, f
    for forbidden in ("UsbSerialManager", "KWriteManager", "KFactorManager", "AutoCalNativeActionManager", "ResponseDrivenEcuEngine"):
        assert forbidden not in src, (f.name, forbidden)
print("F4_EQUIVALENCE_WIRING=PASS")
```

- [ ] **Step 2: Run** `tools/ci/remote-test.sh python tests/test_f4_equivalence_wiring_contract.py` → `REMOTE_TEST=FAIL` (`AssertionError: equivalenceRuntime = EquivalenceRuntime(paths.runtimeRoot)`).
- [ ] **Step 3: Implement** as edições acima (todas dentro de `try` já existentes ou novos `try/catch` com `log.add("WARN", "EQUIVALENCIA", …)`; nenhuma exceção sobe).
- [ ] **Step 4: Run** `tools/ci/remote-test.sh python tests/test_f4_equivalence_wiring_contract.py` → `REMOTE_TEST=PASS`; `tools/ci/remote-test.sh checks ""` → `REMOTE_TEST=PASS` (compila).
- [ ] **Step 5: Commit** `feat(service): telemetria, apagões e escritas alimentam o cérebro único`

### Task 4.10: Ponte provisória — resultado e `freezeReference`

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt` após `getEquivalenceFresh` (`:281-285`, âncora `fun getEquivalenceFresh(): String`) e `computeEquivalence` (`:290-295`, âncora `EquivalenceView.build(`)
- Modify: `app/src/main/java/com/omegas/prohub/autocal/EquivalenceView.kt:11-22`
- Modify: `app/src/main/assets/ui/core/autocal-api.js:40-46` (âncora `refinementPhase: () =>`)
- Modify: `tests/test_autocal_bridge_surface_contract.py:29-46` (lista `REQUIRED`)
- Test: `app/src/test/java/com/omegas/prohub/autocal/EquivalenceViewTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  @JavascriptInterface fun getEquivalenceResult(): String     // service.equivalenceResultJson(); pronto, sem cálculo na thread da WebView
  @JavascriptInterface fun freezeReference(): String          // temporário: F5 move para Omegas.request({intent:"REFERENCE_FREEZE"}); invalida memos
  // EquivalenceView
  fun build(ledger: EquivalenceLedger, journal: RefinementJournal, phases: EquivalencePhases, stalls: StallWatch,
            equivalence: JSONObject? = null): JSONObject    // + .put("equivalence", equivalence ?: JSONObject.NULL)
  ```
  JS: `equivalenceResult: () => invoke('getEquivalenceResult', [], { ok: false, available: false })`, `freezeReference: () => invoke('freezeReference', [], { ok: false })`.

- [ ] **Step 1: Write the failing test**

```kotlin
class EquivalenceViewTest {
    @Test fun `visao do Refino carrega o resultado do cerebro unico quando existe`() {
        val v = EquivalenceView.build(EquivalenceLedger(null), RefinementJournal(null), EquivalencePhases(null), StallWatch(null),
            JSONObject().put("format", EquivalenceJson.FORMAT).put("index", 0.5))
        assertEquals(0.5, v.getJSONObject("equivalence").getDouble("index"), 0.0)
        assertTrue(EquivalenceView.build(EquivalenceLedger(null), RefinementJournal(null), EquivalencePhases(null), StallWatch(null)).isNull("equivalence"))
    }
}
```
  e em `tests/test_autocal_bridge_surface_contract.py`: `"getEquivalenceResult", "freezeReference"` em `REQUIRED`, mais `assert "invoke('freezeReference'" in api and "invoke('getEquivalenceResult'" in api` (lendo `autocal-api.js`).

- [ ] **Step 2: Run** `tools/ci/remote-test.sh gradle "com.omegas.prohub.autocal.EquivalenceViewTest"` e `tools/ci/remote-test.sh python tests/test_autocal_bridge_surface_contract.py` → `REMOTE_TEST=FAIL`.
- [ ] **Step 3: Implement.** Se o teste da F3 "nenhum método sem botão" exigir chamada de tela (não só do `autocal-api.js`), adicionar a chamada no ponto onde a aba AutoCal já lê `equivalence()` — sem UI nova (F6/F7 desenham).
- [ ] **Step 4: Run** os dois → `REMOTE_TEST=PASS`; `tools/ci/remote-test.sh gradle "com.omegas.prohub.autocal.EquivalenceViewPerformanceTest"` → `REMOTE_TEST=PASS`.
- [ ] **Step 5: Commit** `feat(bridge): resultado do cérebro único e freezeReference (provisório até F5)`

### Task 4.11: Oráculo Python da Curva Própria e do índice + paridade Kotlin no CI

**Files:**
- Modify: `tools/autocal_refine/refined_oracle.py` — funções novas antes do bloco `if __name__ == "__main__":` (âncora; ~`:555`) e opção de linha de comando `--equivalence <fixture.json.gz> <refSeq> <curveSeq>` que imprime JSON
- Create: `tests/test_equivalence_oracle.py`
- Create: `app/src/test/java/com/omegas/prohub/equivalence/EquivalenceOracleParityTest.kt`

**Interfaces:**
- Produces (Python, espelho exato das Tasks 4.2/4.3/4.5/4.6, sem prova/experiência):
  `CELL_BAR = 0.02; GRID_MIN_BAR = 0.10; GRID_CELLS = 50; PRIOR_N0 = 3.0; DRIVING_MIN_RPM = 1000.0; MIN_TOL = 0.04; MAX_FRAME_DT_MS = 1000`;
  `reference_points(snapshot) -> list[(map, ms, maturity)]`, `prior_at(ref, m)`, `own_curve(obs, ref) -> list[cell]`, `map_for(cells, ms)`, `usage_cells(telemetry) -> list[float]`, `equivalence_points(axis_raw, k_raw, own_petrol, own_gas, usage) -> list[point]`, `equivalence_index(points) -> (index, coverage)`, `equivalence_replay(data, ref_seq, curve_seq) -> dict` (obs = `blind.cap_cells(blind.stable_frames(...))`, mesma regra do `EquivalenceLedger`).
  Saída JSON: `{"ownPetrol":[{mapBar,petrolMs,samples,dispersion,source}×50], "ownGas":[…], "points":[{index,mixture,tolerance,usage,samples,state}×30], "index", "coverage"}` (estados base apenas).
- Kotlin: o teste chama `ProcessBuilder("python3", "<raiz>/tools/autocal_refine/refined_oracle.py", "--equivalence", <fixture>, refSeq, curveSeq)` (raiz resolvida como em `RealSessionReplaySupport.fixture`: `..` ou `.`). Sem `python3`: falha se `System.getenv("CI") == "true"`, senão `Assume`. **Motivo:** a paridade Kotlin existente (`tests/test_refined_autocal_kotlin_parity.py`) pula no CI por falta de `kotlinc`/`ORG_JSON_JAR`; invertendo o sentido (JVM chama Python, que existe no runner) a paridade nova roda de verdade.

- [ ] **Step 1: Write the failing tests**

```python
# tests/test_equivalence_oracle.py
CASES = [("ref_2026-10-01_1719", 95, 2183), ("automatch_2026-10-01_1301", 634, 699), ("gnv_only_2026-09-30_0931", 642, 642)]
class EquivalenceOracle(unittest.TestCase):
    def test_own_curve_beats_reference_on_hidden_readings(self):  # mesmo gate da Task 4.3, no oráculo
        self.assertGreaterEqual(judged, 8); self.assertLess(rms_own, rms_ref)
    def test_index_rises_after_real_write_automatch(self):        # mesmo recorte da Task 4.6
        self.assertGreater(after["index"], before["index"])
    def test_replay_shape(self):
        for name, ref_seq, k_seq in CASES:
            out = oracle.equivalence_replay(load(name), ref_seq, k_seq)
            self.assertEqual(len(out["ownPetrol"]), 50); self.assertEqual(len(out["points"]), 30)
if __name__ == "__main__": unittest.main()
```

```kotlin
class EquivalenceOracleParityTest {
    @Test fun `Kotlin e oraculo Python concordam na curva propria nos pontos e no indice das tres sessoes reais`() {
        for ((name, refSeq, kSeq) in CASES) {
            val py = JSONObject(runOracle(name, refSeq, kSeq)); val kt = kotlinReplay(name, refSeq, kSeq)
            // células: samples e source iguais; petrolMs |rel| ≤ 1e-6; dispersion ≤ 1e-9
            // pontos: state igual; mixture/tolerance ≤ 1e-6; usage ≤ 1e-9; samples igual
            assertEquals(py.optDouble("index", Double.NaN), kt.index ?: Double.NaN, 1e-9); assertEquals(py.getInt("coverage"), kt.coverage)
        }
    }
}
```
  `kotlinReplay` = ledger da fixture inteira + `UsageMeter(null)` com todos os quadros (`sessionId = 1`) + `ExperienceMeter(null).reading()` vazio + `EquivalenceEngine.evaluate` com `reference = EquivalenceReplaySupport.reference(name, refSeq)`.

- [ ] **Step 2: Run** `tools/ci/remote-test.sh python tests/test_equivalence_oracle.py` e `tools/ci/remote-test.sh gradle "com.omegas.prohub.equivalence.EquivalenceOracleParityTest"` → `REMOTE_TEST=FAIL` (`AttributeError: module 'refined_oracle' has no attribute 'equivalence_replay'`).
- [ ] **Step 3: Implement** as funções Python reaproveitando `pava`, `whittaker`, `interp`, `inverse_first`, `refine` do próprio oráculo.
- [ ] **Step 4: Run** os dois → `REMOTE_TEST=PASS`; `tools/ci/remote-test.sh python tests/test_refined_autocal_oracle.py` → `REMOTE_TEST=PASS` (oráculo antigo intacto).
- [ ] **Step 5: Commit** `test(equivalence): oráculo Python da Curva Própria e do índice com paridade Kotlin no CI`

### Task 4.12: Gate completo e PR

- [ ] **Step 1:** `tools/ci/remote-test.sh checks ""` → `REMOTE_TEST=PASS`.
- [ ] **Step 2:** Descrição do PR: o que mudou (pacote `equivalence`, Referência congelada, Curvas Próprias, índice, próxima ação, prova por ponto, ponte provisória `freezeReference`/`getEquivalenceResult`) · classe de prova **3** (replay real: validação cruzada da Curva Própria, índice antes/depois de escrita real em AUTOMATCH e REFERENCE, troca de Referência, ECU readquirindo, paridade Python) e **2** (sintético: estados, prioridades, tremor, uso; e o gate simulado da proposta, com a suposição linear declarada) · **não provado:** sensação no carro, se o índice acompanha o que o dono sente, comportamento com a ECU física, UI (F6/F7).
- [ ] **Step 3:** `gh pr ready && gh pr checks --watch` → `OMEGAS PLATINA CI` verde no SHA do PR.
- [ ] **Step 4:** `gh pr merge --merge`.

---

## Cobertura da spec

| Requisito (spec) | Task |
|---|---|
| §1.1 `EquivalencePoint` por ponto da Curva K (Mistura com intervalo de confiança, Forma `slope`, Experiência, Uso) | 4.1, 4.6 |
| §1.1 Experiência casada por MAP equivalente, tremor sem tendência, quase-apagões/h vs gasolina | 4.4 |
| §1.1 Uso: condução RPM ≥ 1000, janela das últimas 10 sessões | 4.5 |
| §1.2 Estados `SEM_DADOS → … → RICO` e tolerâncias (`tol = max(4%, 2·disp)`, leve 8%) | 4.1, 4.6 |
| §1.2 `EM_PROVA`/`CONFIRMADO`/`CONTESTADO`/`INCONCLUSIVO`, timebox e "piorou" (> 4% e > 5%) do Refino v2 | 4.7 |
| §1.3 Índice ponderado por uso, cobertura, guardado por sessão | 4.6, 4.9 |
| §1.4 Próxima ação 1, 1b, 2–6 | 4.6 |
| §1.5 Só a Mistura propõe, via `AutoMatchRefinedEngine` com pares das Curvas Próprias | 4.6 |
| §1.6 Referência congelada, uma por vez, anterior como Desfazer até o fim da sessão, provisório sem Referência | 4.2, 4.8 |
| §1.6 ECU reaprende → Referência intacta, diferença máx. reportada (`ecuDrift`) | 4.2, 4.8 |
| §1.6b Curva Própria 0,02 bar, prior que cai com n, isotônico, Whittaker (λ validado), fontes REFERENCE/BLENDED/OWN, divergência e "Reaprender ponto" (> 8%) | 4.3, 4.8 |
| §1.6b Trocar Referência não apaga nada; `EM_PROVA` recomeça | 4.3, 4.7, 4.8 |
| §1.6b Gate: Curva Própria madura prevê melhor que a Referência (validação cruzada) | 4.3, 4.11 |
| §1.7 Evidência por ponto (`samples`, `sources`, confiança) | 4.6, 4.8 |
| §1.7 Gate: índice sobe depois de ajuste gravado (replay real) | 4.6, 4.11 |
| §1.7 Paridade Kotlin ↔ Python estendida ao índice | 4.11 |
| §2.2 `REFERENCE_FREEZE` (provisório como `freezeReference()` até a fila da F5) | 4.9, 4.10 |
| §7 linha 4: `EquivalencePhases` com estados de §1.2 | 4.7 |

**Fora desta fatia (por desenho):** desenho das telas (Referência tracejada × Própria sólida, âmbar do `CONTESTADO`, barra de confiança) → F6/F7; `REFERENCE_FREEZE` como intent com recibo e `UNDO` → F5 (consome `EquivalenceRuntime.freeze`/`restorePreviousReference`); `AUTOCAL_RELEARN` a partir de `relearnSuggested` → F5/F7.
