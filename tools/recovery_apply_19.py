from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


def write(path, text):
    (ROOT / path).write_text(text, encoding="utf-8")


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 occurrence, found {count}")
    return text.replace(old, new, 1)


def regex_once(text, pattern, replacement, label):
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 regex match, found {count}")
    return updated

# 1) Native science boundary: causal comparisons from BlueCalibrationCoordinator
# become the comparison payload consumed by Learning. JS remains presentation-only.
path = "app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt"
text = read(path)
old = '''                    val calibration = try { JSONObject(service.blueCalibrationStateJson()) } catch (error: Exception) {
                        JSONObject().put("ok", false).put("error", error.message ?: "Estado Blue indisponível")
                    }
                    val science = JSONObject()
'''
new = '''                    val calibration = try { JSONObject(service.blueCalibrationStateJson()) } catch (error: Exception) {
                        JSONObject().put("ok", false).put("error", error.message ?: "Estado Blue indisponível")
                    }
                    calibration.optJSONArray("comparisons")?.let { causalComparisons ->
                        learning.put("comparisons", JSONArray(causalComparisons.toString()))
                            .put("comparisonCount", causalComparisons.length())
                            .put("comparison_count", causalComparisons.length())
                    }
                    val science = JSONObject()
'''
text = replace_once(text, old, new, "Hub science causal comparisons")
write(path, text)

# 2) Normal UI stops polling an owner-configurable tolerance model.
path = "app/src/main/assets/ui/app.js"
text = read(path)
text = replace_once(
    text,
    "      patch.learningTolerance = api.learningToleranceSettings() || {};\n",
    "",
    "app tolerance polling",
)
write(path, text)

# 3) Learning cockpit: consume native Blue comparisons, map petrolReferenceMs,
# and remove manual scientific tolerance controls from the normal surface.
path = "app/src/main/assets/ui/screens/learning.js"
text = read(path)
text = replace_once(
    text,
    "    return finite(item?.observed_pair?.petrol_target_ms ?? item?.petrol_target_ms ?? item?.petrolTargetMs);",
    "    return finite(item?.petrolReferenceMs ?? item?.observed_pair?.petrol_target_ms ?? item?.petrol_target_ms ?? item?.petrolTargetMs);",
    "Learning Blue petrol reference alias",
)
text = replace_once(text, "      this.toleranceSignature = '';\n", "", "Learning tolerance signature")
text = replace_once(
    text,
    '          <button type="button" data-learning-inspector="tolerances">Tolerâncias</button>\n',
    "",
    "Learning tolerance tab",
)
text = replace_once(
    text,
    '        <div id="learningTolerancePane" class="learning-inspector-pane" data-pane="tolerances"></div>\n',
    "",
    "Learning tolerance pane",
)
text = replace_once(
    text,
    "      this.tolerancePane = document.getElementById('learningTolerancePane');\n",
    "",
    "Learning tolerance pane binding",
)
text = replace_once(text, "      this.renderTolerances(state);\n", "", "Learning tolerance render call")
text = replace_once(
    text,
    "      const tolerance = state.learningTolerance || {};\n      const policy = tolerance.policy || tolerance.applied || {};\n",
    "",
    "Learning tolerance state",
)
old_policy = '''        <section class="learning-policy-summary">
          <header><small>LIMITES CONFIGURADOS</small><button type="button" data-open-tolerances>ajustar</button></header>
          <div class="policy-grid"><span>RPM <b>${fmt(policy.rpmOscillationMinimum, 0)} rpm / ${fmt(policy.rpmOscillationPercent, 1)}%</b></span><span>MAP <b>${fmt(policy.mapOscillationBar, 3)} bar</b></span><span>Petrol Inj. <b>${fmt(policy.petrolOscillationPercent, 1)}%</b></span><span>Pressão <b>${fmt(policy.pressureOscillationBar, 3)} bar</b></span></div>
          <p>Esses números vêm da política Kotlin. A interface não decide se a amostra é válida.</p>
        </section>
'''
new_policy = '''        <section class="learning-policy-summary">
          <header><small>ESTABILIDADE DA EVIDÊNCIA</small><span>AUTOMÁTICA</span></header>
          <div class="policy-grid"><span>RPM <b>interno</b></span><span>MAP <b>interno</b></span><span>Petrol Inj. <b>interno</b></span><span>Continuidade <b>protegida</b></span></div>
          <p>O núcleo decide automaticamente se RPM, MAP e Petrol Inj. representam a mesma condição física. Não existe perfil do usuário para afrouxar ou apertar a ciência.</p>
        </section>
'''
text = replace_once(text, old_policy, new_policy, "Learning policy summary")
text = replace_once(
    text,
    "      this.collectionPane.querySelector('[data-open-tolerances]')?.addEventListener('click', () => this.setInspectorPane('tolerances'));\n",
    "",
    "Learning tolerance open handler",
)
text = regex_once(
    text,
    r"\n    renderTolerances\(state\) \{.*?\n    renderDetail\(state, row, column\) \{",
    "\n    renderDetail(state, row, column) {",
    "Learning manual tolerance methods",
)
write(path, text)

# 4) TRANSITION (0x88) remains gasoline evidence until CNG is actually confirmed.
path = "app/src/main/java/com/omegas/prohub/learning/MotorSampleAnalyzer.kt"
text = read(path)
old = '''    fun add(
        frame: Mp48Telemetry,
        plannedGap: Boolean = false,
        toleratedGap: Boolean = false,
    ): SampleDecision = addInternal(frame, plannedGap, toleratedGap).withCell(frame)
'''
new = '''    fun add(
        frame: Mp48Telemetry,
        plannedGap: Boolean = false,
        toleratedGap: Boolean = false,
    ): SampleDecision {
        val scientificFrame = if (frame.fuel == Mp48Fuel.TRANSITION) {
            frame.copy(fuel = Mp48Fuel.PETROL, state = "GASOLINA_TRANSICAO")
        } else {
            frame
        }
        return addInternal(scientificFrame, plannedGap, toleratedGap).withCell(frame)
    }
'''
text = replace_once(text, old, new, "Motor TRANSITION normalization")
transition_block = '''            Mp48Fuel.TRANSITION -> {
                resetSamples(requireFullWindow = true)
                return SampleDecision.transition(
                    state = "FUEL_TRANSITION",
                    reason = "Troca de combustível indicada — observando o motor, sem aprender",
                    fuelConfirmed = stableFuel?.wireName,
                    transitionTarget = transitionTarget?.wireName,
                )
            }
'''
text = replace_once(text, transition_block, "", "Motor legacy TRANSITION rejection")
text = replace_once(
    text,
    "            Mp48Fuel.PETROL, Mp48Fuel.CNG -> Unit\n",
    "            Mp48Fuel.PETROL, Mp48Fuel.TRANSITION, Mp48Fuel.CNG -> Unit\n",
    "Motor exhaustive fuel branch",
)
write(path, text)

# 5) Serial recovery gets its own transport policy instead of reading learning knobs.
path = "app/src/main/java/com/omegas/prohub/ecu/ResponseDrivenEcuEngine.kt"
text = read(path)
text = text.replace("import com.omegas.prohub.learning.LearningToleranceSettings\n", "")
text = replace_once(text, "        val tolerances = LearningToleranceSettings.current\n", "", "Engine status learning tolerance local")
text = replace_once(
    text,
    '                consecutiveFailures > tolerances.toleratedSerialFailures -> "SOFT"\n',
    '                consecutiveFailures > Mp48SerialRecoveryPolicy.toleratedFailures -> "SOFT"\n',
    "Engine recovery mode threshold",
)
text = replace_once(
    text,
    '            .put("learningTolerances", tolerances.toJson())\n',
    '            .put("serialRecoveryPolicy", Mp48SerialRecoveryPolicy.toJson())\n',
    "Engine recovery diagnostics",
)
text = replace_once(
    text,
    "        val toleratedGap = consecutiveFailures in 1..LearningToleranceSettings.current.toleratedSerialFailures\n",
    "        val toleratedGap = consecutiveFailures in 1..Mp48SerialRecoveryPolicy.toleratedFailures\n",
    "Engine tolerated serial gap",
)
old = '''        val tolerances = LearningToleranceSettings.current
        val softRecoveryAfterFailures = tolerances.toleratedSerialFailures + 1
        val hardRecoveryAfterFailures = tolerances.hardRecoveryFailures
        val hardRecoverySilenceMs = tolerances.hardRecoverySilenceMs
'''
new = '''        val softRecoveryAfterFailures = Mp48SerialRecoveryPolicy.toleratedFailures + 1
        val hardRecoveryAfterFailures = Mp48SerialRecoveryPolicy.hardRecoveryFailures
        val hardRecoverySilenceMs = Mp48SerialRecoveryPolicy.hardRecoverySilenceMs
'''
text = replace_once(text, old, new, "Engine recovery policy source")
write(path, text)

policy_path = ROOT / "app/src/main/java/com/omegas/prohub/ecu/Mp48SerialRecoveryPolicy.kt"
if policy_path.exists():
    raise SystemExit("Mp48SerialRecoveryPolicy.kt unexpectedly already exists")
policy_path.write_text('''package com.omegas.prohub.ecu

import org.json.JSONObject

/**
 * Transport-only recovery policy for the MP48 serial session.
 * These thresholds are deliberately not part of the learning tolerance model.
 */
object Mp48SerialRecoveryPolicy {
    const val toleratedFailures: Int = 3
    const val hardRecoveryFailures: Int = 10
    const val hardRecoverySilenceMs: Long = 1_800L

    fun toJson(): JSONObject = JSONObject()
        .put("toleratedFailures", toleratedFailures)
        .put("hardRecoveryFailures", hardRecoveryFailures)
        .put("hardRecoverySilenceMs", hardRecoverySilenceMs)
        .put("ownerConfigurable", false)
        .put("scope", "MP48_TRANSPORT_RECOVERY")
}
''', encoding="utf-8")

# 6) Runtime unit coverage for TRANSITION semantics.
path = "app/src/test/java/com/omegas/prohub/learning/MotorSampleAnalyzerBoundaryTest.kt"
text = read(path)
marker = '''    @Test
    fun `engine off keeps the next window conservative`() {
'''
addition = '''    @Test
    fun `transition byte remains gasoline evidence until cng is confirmed`() {
        val analyzer = MotorSampleAnalyzer()
        var decision: SampleDecision? = null
        repeat(frames) { index ->
            decision = analyzer.add(
                frame(
                    at = index * 50L,
                    fuel = Mp48Fuel.TRANSITION,
                    petrolMs = 4.0,
                    gasRaw = 180,
                    mapBar = 0.60,
                ),
            )
        }
        assertTrue(decision!!.learningEligible)
        assertEquals(Mp48Fuel.PETROL, decision!!.sample!!.fuel)
        assertEquals("GASOLINA", decision!!.fuelConfirmed)
    }

'''
text = replace_once(text, marker, addition + marker, "Motor TRANSITION unit test insertion")
write(path, text)

# 7) Update the UI contract to verify the chosen native boundary instead of
# demanding that Learning reach into calibrationState directly.
path = "tests/ui/blue-learning-recovery-ui.test.cjs"
text = read(path)
text = replace_once(
    text,
    "const app = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/app.js'), 'utf8');\n",
    "const app = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/app.js'), 'utf8');\nconst hub = fs.readFileSync(path.join(ROOT, 'app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt'), 'utf8');\n",
    "UI recovery contract Hub source",
)
old = '''test('Learning usa comparações causais Blue e consegue localizá-las na grade física', () => {
  assert.match(learning, /calibrationState[^\\n]{0,120}comparisons|comparisons[^\\n]{0,120}calibrationState/s,
    'Desvio medido precisa consumir a lista de comparações do BlueCalibrationCoordinator');
  assert.match(learning, /petrolReferenceMs|petrolTargetMs/,
    'comparação Blue precisa usar o Petrol Inj. de referência para localizar a célula visual');
  assert.match(learning, /rpmBins/);
  assert.match(learning, /petrolBins/);
});
'''
new = '''test('Learning usa comparações causais Blue e consegue localizá-las na grade física', () => {
  assert.match(hub, /calibration\\.optJSONArray\\(\"comparisons\"\\)/,
    'boundary nativo precisa receber as comparações do BlueCalibrationCoordinator');
  assert.match(hub, /learning\\.put\\(\"comparisons\"/,
    'boundary nativo precisa publicar as comparações causais no payload de Learning');
  assert.match(learning, /maps\\.comparisons/,
    'Learning deve consumir somente o payload causal já reconciliado');
  assert.match(learning, /petrolReferenceMs|petrolTargetMs/,
    'comparação Blue precisa usar o Petrol Inj. de referência para localizar a célula visual');
  assert.match(learning, /rpmBins/);
  assert.match(learning, /petrolBins/);
});
'''
text = replace_once(text, old, new, "UI recovery comparison boundary contract")
write(path, text)

print("BLUE_RECOVERY_19_APPLY=OK")
