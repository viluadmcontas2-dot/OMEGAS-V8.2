#!/usr/bin/env python3
"""Mutantes mecânicos da UI e das pontes (Lote W, parte 4): prova que a suíte de USO fica VERMELHA quando o app erra.

Estilo de tools/ci/run_refinement_mutants.py, mas sem Gradle: cada mutante é uma troca de texto aplicada numa CÓPIA
das fontes (a árvore do repositório nunca é tocada) e a suíte relevante roda contra a cópia:
  * kind=node  -> `node --test <testes>` com UI_ROOT apontando para a cópia da UI;
  * kind=graph -> `tests/test_wiring_graph.py` com WIRING_ROOT apontando para a cópia (Kotlin + UI).
Um mutante é MORTO quando a suíte falha; SOBREVIVENTE quando continua verde (= falta teste).

A base dos mutantes é o app atual com os defeitos conhecidos que CEGAM a cobertura consertados na cópia (hoje: o
ReferenceError do Refino, DEFECT-9), senão todo mutante do Refino sobreviveria por trás de um teste `todo`.

Uso:
  python3 -B tools/wiring/run_ui_mutants.py            # todos (modo completo: roda TODA a suíte de uso por mutante)
  python3 -B tools/wiring/run_ui_mutants.py --fast     # todos, só os testes alvo de cada mutante
  python3 -B tools/wiring/run_ui_mutants.py --ci       # subconjunto pequeno e rápido (usado em tests/test_wiring_mutants_ci.py)
  python3 -B tools/wiring/run_ui_mutants.py --only curve-reset-frozen,fuel-label
"""
import argparse
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import time

ROOT = pathlib.Path(__file__).resolve().parents[2]
UI = "app/src/main/assets/ui"
KT = "app/src/main/java/com/omegas/prohub"
ALL_NODE_TESTS = sorted(str(p.relative_to(ROOT)) for p in (ROOT / "tests/ui").glob("wiring-*.test.cjs")) + ["tests/ui/autocal-sem-leitura-anterior.test.cjs"]

M1, M2, M3, M4, M5, M6, M7, M8 = (f"tests/ui/wiring-{n}.test.cjs" for n in (
    "m1-connection", "m2-fuel", "m3-autocal", "m4-refino", "m5-curve", "m6-map", "m7-sessions", "m8-tools"))
FLOWS, CROSS = "tests/ui/wiring-flows.test.cjs", "tests/ui/wiring-cross.test.cjs"
FIX_REFINO, FIX_AUTOCAL, FIX_CURVE = "tests/ui/fix-refino-usage.test.cjs", "tests/ui/fix-autocal-usage.test.cjs", "tests/ui/fix-curve-usage.test.cjs"
FIX_MAP, FIX_TELEMETRY, FIX_SESSIONS = "tests/ui/fix-map-usage.test.cjs", "tests/ui/fix-telemetry-usage.test.cjs", "tests/ui/fix-sessions-tools-usage.test.cjs"


def mutant(mid, klass, path, old, new, tests, kind="node", ci=False, note=""):
    return dict(id=mid, klass=klass, path=path, old=old, new=new, tests=tests, kind=kind, ci=ci, note=note)


MUTANTS = [
    # ---- botão congelado: o handler de clique some
    mutant("curve-reset-frozen", "botão congelado", f"{UI}/screens/curve.js",
           "      document.getElementById('curveResetButton')?.addEventListener('click', () => this.resetCurve());\n", "", [M5]),
    mutant("map-undo-frozen", "botão congelado", f"{UI}/screens/map.js",
           "      document.getElementById('mapUndoButton')?.addEventListener('click', () => this.undoLast());\n", "", [M6]),
    # (sessions-export-frozen removido: o botão Exportar ZIP saiu; as sessões se exportam sozinhas)
    mutant("tools-battery-frozen", "botão congelado", f"{UI}/components/drawers.js",
           "        this.api.requestBatteryOptimizationExemption?.();\n", "", [M8], ci=True),
    mutant("agora-next-frozen", "botão congelado", f"{UI}/screens/dashboard.js",
           "if (app && app.router) app.router.navigate('refino');", "", [FLOWS, M1]),
    mutant("refino-confirm-frozen", "botão congelado", f"{UI}/screens/refino.js",
           "      if (event.target.closest('[data-refino-primary]')) this.primary();\n", "", [FLOWS, M4]),
    # ---- desconhecido vira 0
    mutant("rules-finite-zero", "desconhecido vira 0", f"{UI}/core/display-rules.js",
           "if (value === null || value === undefined || value === '' || typeof value === 'boolean') return null;",
           "if (value === null || value === undefined || value === '' || typeof value === 'boolean') return 0;", [M7, M4]),
    mutant("livestore-finite-zero", "desconhecido vira 0", f"{UI}/core/live-store.js",
           'const { finite } = ns.DisplayRules;',
           'const finite = value => Number(value);', [M2, M1], ci=True),
    # ---- guarda de ocupado
    mutant("save-backup-no-guard", "guarda de ocupado removida", f"{UI}/screens/curve.js",
           "    saveBackup() {\n      if (this.reading || this.writing || this.backupTask) return;\n", "    saveBackup() {\n", [M5]),
    mutant("reset-no-guard", "guarda de ocupado removida", f"{UI}/screens/curve.js",
           "    resetCurve() {\n      if (this.reading || this.writing || this.backupTask) return;\n", "    resetCurve() {\n", [M5]),
    mutant("map-undo-no-guard", "guarda de ocupado removida", f"{UI}/screens/map.js",
           "if (!this.undoId || this.reading || this.store.get().map?.state === 'writing') return;", "if (!this.undoId) return;", [M6]),
    # ---- protocolo de escrita
    mutant("reset-skips-photo", "pula a foto antes de gravar", f"{UI}/screens/curve.js",
           "      const photo = this.api.startCurveBackup('Antes do reset');\n      if (!photo?.ok || !photo?.started) {\n        this.alert(photo?.error || 'Não foi possível salvar a foto da Curva K; nada foi zerado.');\n        return;\n      }\n      this.backupTask = 'reset-photo';\n      text('curveBackupStatus', 'Salvando a foto da curva antes de zerar…');\n",
           "      this.startResetWrite();\n", [M5, M3]),
    mutant("curve-no-reread-after-write", "pula a conferência", f"{UI}/screens/curve.js",
           "            this.refreshBackups();\n            this.startRead(true);\n", "            this.refreshBackups();\n", [M5]),
    mutant("map-no-reread-after-write", "pula a conferência", f"{UI}/screens/map.js",
           "        this.showResultButtons(!restored, false);\n        this.editor.reset();\n        this.startRead(true);\n",
           "        this.showResultButtons(!restored, false);\n        this.editor.reset();\n", [M6]),
    mutant("refino-no-stale-check", "pula a conferência", f"{UI}/screens/refino.js",
           "const stale = points.find(p => factors.get(p.index) !== p.currentRaw);", "const stale = null;", [FLOWS]),
    mutant("map-writes-without-targets", "payload de escrita errado", f"{UI}/screens/map.js",
           "this.api.writeMap(this.review.items, 0, 0,", "this.api.writeMap(this.editor.selectedCells(), 0, 0,", [M6]),
    mutant("map-undo-id-lost", "Desfazer perdido", f"{UI}/screens/map.js",
           "this.undoId = restored ? '' : String(ids[ids.length - 1] || '');", "this.undoId = '';", [M6]),
    mutant("native-api-wrong-reset-method", "método de ponte errado", f"{UI}/core/native-api.js",
           "'startCurveReset', [], {", "'startCurveResett', [], {", [M5, M3]),
    # ---- rótulos e estados
    mutant("stage-labels-swapped", "rótulos trocados", f"{UI}/index.html",
           "<span>Foto antes</span><span>Gravando</span>", "<span>Gravando</span><span>Foto antes</span>", [M5], ci=True),
    mutant("fuel-label-swapped", "rótulos trocados", f"{UI}/core/display-rules.js",
           "if (value.includes('PETROL') || value.includes('GASOLINA')) return 'GASOLINA';",
           "if (value.includes('PETROL') || value.includes('GASOLINA')) return 'GNV';", [M2]),
    mutant("failure-kind-swapped", "rótulos trocados", f"{UI}/core/display-rules.js",
           "if (kind === 'TRANSPORTE') return `Cabo/USB: ", "if (kind === 'TRANSPORTE') return `A ECU recusou: ", [M5, M6]),
    mutant("stale-telemetry-never-flagged", "estado velho vira fresco", f"{UI}/core/live-store.js",
           "if (valid) level = !known ? 'fresh' : ageMs > STALE_MS ? 'lost' : ageMs > GREY_MS ? 'late' : 'fresh';", "if (valid) level = 'fresh';", [M1]),
    mutant("refino-allows-write-during-ecu-auto", "regra de fase", f"{UI}/screens/refino.js",
           "if (phase === 'SEM_ECU' || phase === 'LENDO_ECU' || phase === 'ECU_TRABALHANDO') {", "if (phase === 'SEM_ECU' || phase === 'LENDO_ECU') {", [M4]),
    mutant("refino-busy-button-enabled", "disabled removido em ocupado", f"{UI}/screens/refino.js",
           "        button.disabled = true;\n        const progress = finite(op.progress);", "        button.disabled = false;\n        const progress = finite(op.progress);", [FLOWS]),
    # ---- renderização
    mutant("curve-renders-before-data", "renderiza antes do dado", f"{UI}/screens/curve.js",
           "      if (this.reading && !operation.busy) {", "      if (this.reading) {", [M5]),
    mutant("curve-listener-registered-twice", "listener duplicado", f"{UI}/screens/curve.js",
           "      document.getElementById('curveReviewButton')?.addEventListener('click', () => this.writePrepared());\n",
           "      document.getElementById('curveReviewButton')?.addEventListener('click', () => this.writePrepared());\n      document.getElementById('curveReviewButton')?.addEventListener('click', () => this.writePrepared());\n", [M5],
           note="EQUIVALENTE desde a guarda de ocupado de writePrepared (DEFECT-14): o ouvinte duplicado chama duas vezes e a segunda é ignorada"),
    mutant("sessions-no-escape-html", "escapeHtml esquecido", f"{UI}/screens/sessions.js",
           " · ${escapeHtml(rows[0].title)}</span>", " · ${rows[0].title}</span>", [M7], ci=True),
    mutant("curve-learning-chart-empty-array", "gráfico com lista vazia", f"{UI}/screens/curve.js",
           "const minFactor = factorValues.length ? Math.min(...factorValues) - 0.05 : 0.8;", "const minFactor = true ? Math.min(...factorValues) - 0.05 : 0.8;", [M5],
           note="EQUIVALENTE: sem fatores nenhum caminho/ponto é desenhado, então o mínimo infinito não aparece na tela"),
    mutant("toast-never-shown", "aviso invisível", f"{UI}/app.js",
           "    toast.classList.add('show');\n", "", [M5]),
    mutant("scheduler-leaks-timer", "timer vazando", f"{UI}/core/scheduler.js",
           "      if (wasRunning) root.clearInterval(this.timer);\n", "", [CROSS]),
    # ---- Fix UI (2026-10-04): cada conserto tem um mutante que o teste de uso precisa matar
    mutant("scheduler-start-no-rearm", "cursor congela após segundo plano", f"{UI}/core/scheduler.js",
           "      this.armFrame();\n      if (this.timer) return;", "      if (this.timer) return;", [FIX_REFINO], ci=True),
    mutant("refino-index-read-as-object", "contrato do Kotlin lido errado", f"{UI}/screens/refino.js",
           "    const value = typeof raw === 'number' ? finite(raw) : null;", "    const value = finite(eq?.index?.value);", [FIX_REFINO], ci=True),
    mutant("refino-undo-after-ecu-changed", "Desfazer sem o que desfazer", f"{UI}/screens/refino.js",
           "const available = has && (partial || (!changedByEcu && !wasUndo));", "const available = has;", [FIX_REFINO]),
    mutant("autocal-toggle-reenabled-early", "botão reabilita antes da resposta", f"{UI}/screens/autocal-cockpit.js",
           "      const waiting = this.operationalPending || Boolean(this.toggleWaiting);", "      const waiting = this.operationalPending;", [FIX_AUTOCAL], ci=True),
    mutant("autocal-seconds-frozen", "texto 'há N s' congelado", f"{UI}/screens/autocal-cockpit.js",
           "point.fuel, point.grey, point.grey ? Math.round(point.ageMs / 1000) : 0]", "point.fuel, point.grey]", [FIX_AUTOCAL]),
    mutant("curve-pending-reset-survives", "reset sem toque novo", f"{UI}/screens/curve.js",
           "      this.pendingReset = Boolean(context && context.resetNow === true);", "      if (context && context.resetNow === true) this.pendingReset = true;", [FIX_CURVE], ci=True),
    mutant("curve-resume-reenters", "Desfazer morto depois do segundo plano", f"{UI}/screens/curve.js",
           "      this.refreshBackups();\n      this.updateControls();\n      this.poll();", "      this.restoreContext = null;\n      this.refreshBackups();\n      this.updateControls();\n      this.poll();", [FIX_CURVE]),
    mutant("curve-failure-keeps-old-chart", "curva antiga depois da falha", f"{UI}/screens/curve.js",
           "            text('curveSourceStatus', 'ECU não confirmada');\n            this.renderChart();\n            this.renderProposalList();\n            this.updateControls();\n            this.refreshBackups();", "            this.refreshBackups();", [FIX_CURVE]),
    mutant("map-title-singular-plural", "plural errado", f"{UI}/screens/map.js",
           "D().plural(confirmed, 'célula', 'células'), { fem: true, many: confirmed !== 1 }", "`${confirmed} célula(s)`, { fem: true, many: confirmed !== 1 }", [FIX_MAP], ci=True),
    mutant("strip-feeds-no-fallback", "faixa toda — fora do Agora", f"{UI}/components/vehicle-status-strip.js",
           "ns.LiveStore.read(state, { fallback: true })", "ns.LiveStore.read(state)", [FIX_TELEMETRY]),
    mutant("rail-online-without-data", "ECU online sem dado", f"{UI}/core/display-rules.js",
           "if (reading && (reading.level === 'none' || reading.level === 'lost')) {", "if (false) {", [FIX_TELEMETRY], ci=True),
    mutant("sessions-duration-dash-while-recording", "duração — gravando", f"{UI}/screens/sessions.js",
           "const durationShown = recording ? R.durationLabel(recordingMs) : R.durationLabel(status.durationMs);", "const durationShown = R.durationLabel(status.durationMs);", [FIX_SESSIONS]),
    # ---- grafo produtor/consumidor (estático)
    mutant("kotlin-key-renamed", "chave JSON renomeada no Kotlin", f"{KT}/web/HubJavascriptBridge.kt",
           '.put("usbConnected", status.usbConnected)', '.put("usbConnectd", status.usbConnected)', ["tests/test_wiring_graph.py"], kind="graph", ci=True),
    mutant("js-key-renamed", "chave JSON renomeada no JS", f"{UI}/core/display-rules.js",
           "if (s.usbConnected === true) {", "if (s.usbConnectd === true) {", ["tests/test_wiring_graph.py"], kind="graph", ci=True),
    mutant("kotlin-method-renamed", "método de ponte renomeado", f"{KT}/web/HubJavascriptBridge.kt",
           "fun getLiveTelemetry()", "fun getLiveTelemetri()", ["tests/test_wiring_graph.py"], kind="graph"),
    mutant("kotlin-producer-removed", "produtor sem consumidor", f"{KT}/web/HubJavascriptBridge.kt",
           'telemetryValid = root.optBoolean("valid", false),\n        )\n        root.put("ok", true)\n            .put("telemetryAgeMs", root.optLong("ageMs", -1L))', 'telemetryValid = root.optBoolean("valid", false),\n        )\n        root.put("ok", true)\n            .put("telemetryAgeMsX", root.optLong("ageMs", -1L))', ["tests/test_wiring_graph.py"], kind="graph"),
    # ---- TRAVA reset-nunca-pausa-aprendizado (regra 14): sem o religar, o contrato tem de ficar VERMELHO
    mutant("reset-sem-religar", "reset deixa o aprendizado pausado", f"{KT}/autocal/AutoCalNativeActionManager.kt",
           "            keepLearningEnabled(prepared)\n        }\n        ensureSession(prepared)\n        update(\"READING_AFTER\"",
           "        }\n        ensureSession(prepared)\n        update(\"READING_AFTER\"",
           ["tests/test_reset_nunca_pausa_aprendizado.py"], kind="contract", ci=True),
    mutant("reset-sem-religar-na-falha", "reset deixa o aprendizado pausado", f"{KT}/autocal/AutoCalNativeActionManager.kt",
           "throw learningRestoreAfterFailure(prepared, error)", "throw error",
           ["tests/test_reset_nunca_pausa_aprendizado.py"], kind="contract", ci=True),
    # ---- TRAVA autocal-sem-leitura-anterior (regra 16): reintroduzir a legenda "Leitura anterior" tem de deixar o teste VERMELHO
    mutant("autocal-leitura-anterior-volta", "leitura anterior reaparece", f"{UI}/screens/autocal-cockpit.js",
           "legend.innerHTML = chart.legendHtml({ mode: 'ecu18' });",
           "legend.innerHTML = chart.legendHtml({ mode: 'ecu18' }) + '<span class=\"previous\" data-legend=\"previous\">Leitura anterior</span>';",
           ["tests/ui/autocal-sem-leitura-anterior.test.cjs"], ci=True),
]


def fix_known_defects(ui):
    """Conserta na CÓPIA o que cega a cobertura (DEFECT-9: `agreed` usada antes do const em refino.js)."""
    path = ui / "screens/refino.js"
    lines = path.read_text("utf-8").split("\n")
    head = next((i for i, l in enumerate(lines) if "const headline = op.phase" in l), None)
    decl = next((i for i, l in enumerate(lines) if "const agreed = op.phase" in l), None)
    if head is not None and decl is not None and decl > head:
        line = lines.pop(decl)
        lines.insert(head, line)
        path.write_text("\n".join(lines), "utf-8")


def make_tree(tmp, need_kotlin):
    base = pathlib.Path(tmp)
    ui_dst = base / UI
    shutil.copytree(ROOT / UI, ui_dst)
    fix_known_defects(ui_dst)
    if need_kotlin:
        shutil.copytree(ROOT / "app/src/main/java", base / "app/src/main/java")
    return base


def run_suite(m, tree, full):
    env = dict(os.environ)
    if m["kind"] == "contract":
        env["RESET_ROOT"] = str(tree)
        cmd = [sys.executable, "-B"] + m["tests"]
    elif m["kind"] == "node":
        env["UI_ROOT"] = str(tree / UI)
        tests = ALL_NODE_TESTS if full else m["tests"]
        cmd = ["node", "--test"] + tests
    else:
        env["WIRING_ROOT"] = str(tree)
        cmd = [sys.executable, "-B"] + m["tests"]
    try:
        run = subprocess.run(cmd, cwd=ROOT, env=env, capture_output=True, text=True, timeout=240)
    except subprocess.TimeoutExpired:
        return 1, "tempo esgotado"
    return run.returncode, (run.stdout + run.stderr)


def first_failure(output):
    for line in output.splitlines():
        if line.startswith("not ok") and "# TODO" not in line:
            return line[:150]
        if "FAIL:" in line or "AssertionError" in line:
            return line.strip()[:150]
    return ""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ci", action="store_true", help="subconjunto rápido")
    parser.add_argument("--fast", action="store_true", help="roda só os testes alvo de cada mutante")
    parser.add_argument("--only", default="", help="ids separados por vírgula")
    parser.add_argument("--min-kill", type=float, default=0.0, help="falha se a taxa de mortes ficar abaixo")
    args = parser.parse_args()
    chosen = [m for m in MUTANTS if (m["ci"] if args.ci else True)]
    if args.only:
        wanted = set(args.only.split(","))
        chosen = [m for m in MUTANTS if m["id"] in wanted]
    full = not (args.fast or args.ci)
    started = time.monotonic()
    results = []
    with tempfile.TemporaryDirectory(prefix="omegas-mutants-") as tmp:
        tmp_path = pathlib.Path(tmp)
        # base verde: os testes alvo passam na cópia sem mutação (senão "morto" não prova nada)
        base_tree = make_tree(tmp_path / "base", need_kotlin=any(m["kind"] in ("graph", "contract") for m in chosen))
        checked = set()
        for m in chosen:
            key = (m["kind"], tuple(m["tests"]), full)
            if key in checked:
                continue
            checked.add(key)
            code, out = run_suite(m, base_tree, full)
            if code != 0:
                print(f"BASE VERMELHA para {m['tests']}:\n{out[-1500:]}")
                return 2
        for m in chosen:
            tree = make_tree(tmp_path / m["id"], need_kotlin=m["kind"] in ("graph", "contract"))
            target = tree / m["path"]
            text = target.read_text("utf-8")
            if m["old"] not in text:
                print(f"ERRO: âncora do mutante {m['id']} não existe em {m['path']}")
                return 2
            target.write_text(text.replace(m["old"], m["new"], 1), "utf-8")
            t0 = time.monotonic()
            code, out = run_suite(m, tree, full)
            killed = code != 0
            results.append((m, killed, time.monotonic() - t0, first_failure(out)))
            print(f"{'MORTO     ' if killed else 'SOBREVIVEU'}  {m['id']:<40} {m['klass']:<34} {time.monotonic() - t0:5.1f}s  {first_failure(out) if killed else ''}", flush=True)
            shutil.rmtree(tree, ignore_errors=True)
    killed_n = sum(1 for _, k, _, _ in results if k)
    rate = killed_n / max(1, len(results))
    survivors = [m["id"] for m, k, _, _ in results if not k]
    print(f"\nMUTANTES={len(results)} MORTOS={killed_n} TAXA={rate:.0%} TEMPO={time.monotonic() - started:.0f}s")
    if survivors:
        print("SOBREVIVENTES (faltam testes): " + ", ".join(survivors))
    return 0 if rate >= args.min_kill and (not args.ci or not survivors) else 1


if __name__ == "__main__":
    sys.exit(main())
