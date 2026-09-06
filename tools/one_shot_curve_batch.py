#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match, found {count}\n--- needle ---\n{old[:400]}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


curve = ROOT / "app/src/main/assets/ui/screens/curve.js"
html = ROOT / "app/src/main/assets/ui/index.html"
css = ROOT / "app/src/main/assets/ui/styles-witness-multimedia.css"

replace_once(
    curve,
    """      this.data = null;\n      this.activeIndex = null;\n      this.proposals = new Map();""",
    """      this.data = null;\n      this.activeIndex = null;\n      this.selectedIndices = new Set();\n      this.dragSelecting = false;\n      this.dragMoved = false;\n      this.dragStartIndex = null;\n      this.dragStartWasSelected = false;\n      this.proposals = new Map();""",
)

replace_once(
    curve,
    """      document.getElementById('curvePreparePoint')?.addEventListener('click', () => this.prepareActivePoint());\n      document.querySelectorAll('[data-curve-view]').forEach(button => button.addEventListener('click', () => this.setView(button.dataset.curveView || 'editor')));\n      document.querySelectorAll('[data-curve-nudge]').forEach(button => button.addEventListener('click', () => this.nudgeActive(Number(button.dataset.curveNudge) || 0)));""",
    """      document.getElementById('curvePreparePoint')?.addEventListener('click', () => this.prepareSelectionTarget());\n      document.getElementById('curveClearSelection')?.addEventListener('click', () => this.clearSelection());\n      document.querySelectorAll('[data-curve-view]').forEach(button => button.addEventListener('click', () => this.setView(button.dataset.curveView || 'editor')));\n      document.querySelectorAll('[data-curve-nudge]').forEach(button => button.addEventListener('click', () => this.nudgeSelection(Number(button.dataset.curveNudge) || 0)));""",
)

replace_once(
    curve,
    """      this.data = null;\n      this.proposals.clear();\n      text('curveSourceStatus', 'Lendo 30 pontos diretamente da ECU');""",
    """      this.data = null;\n      this.proposals.clear();\n      this.selectedIndices.clear();\n      this.activeIndex = null;\n      text('curveSourceStatus', 'Lendo 30 pontos diretamente da ECU');""",
)

old_selection = """    selectPoint(index) {\n      const point = this.points().find(item => Number(item.index) === Number(index));\n      if (!point) return;\n      this.activeIndex = Number(point.index);\n      text('curveActivePoint', `Ponto ${this.activeIndex + 1} · ${fmt(point.petrolMs, 2)} ms`);\n      text('curveCurrentFactor', fmt(point.factor, 4));\n      const input = document.getElementById('curveTargetFactor');\n      if (input) input.value = String(finite(this.proposals.get(this.activeIndex)?.targetFactor ?? point.factor) ?? '');\n      this.renderChart();\n      this.renderLearningPointContext(this.store.get(), this.activeIndex);\n    }\n\n    nudgeActive(delta) {\n      if (this.activeIndex === null || !delta) return;\n      const input = document.getElementById('curveTargetFactor');\n      const current = finite(input?.value) ?? finite(this.points().find(item => Number(item.index) === this.activeIndex)?.factor);\n      if (current === null) return;\n      if (input) input.value = String(Math.max(0.6, Math.min(4, current + delta)).toFixed(4));\n      this.prepareActivePoint();\n    }\n"""

new_selection = """    pointByIndex(index) {\n      return this.points().find(item => Number(item.index) === Number(index)) || null;\n    }\n\n    selectedPointIndices() {\n      if (this.selectedIndices.size) return [...this.selectedIndices].sort((a, b) => a - b);\n      return this.activeIndex === null ? [] : [this.activeIndex];\n    }\n\n    refreshActiveEditor() {\n      const point = this.activeIndex === null ? null : this.pointByIndex(this.activeIndex);\n      if (!point) {\n        text('curveActivePoint', 'Selecione um ou vários pontos');\n        text('curveCurrentFactor', '—');\n        text('curveTargetNormalized', 'Prévia calculada pelo Kotlin');\n        const input = document.getElementById('curveTargetFactor');\n        if (input) input.value = '';\n        return;\n      }\n      const count = this.selectedIndices.size || 1;\n      const suffix = count > 1 ? ` · ${count} selecionados` : '';\n      text('curveActivePoint', `Ponto ${this.activeIndex + 1} · ${fmt(point.petrolMs, 2)} ms${suffix}`);\n      text('curveCurrentFactor', fmt(point.factor, 4));\n      const input = document.getElementById('curveTargetFactor');\n      if (input) input.value = String(finite(this.proposals.get(this.activeIndex)?.targetFactor ?? point.factor) ?? '');\n      this.renderLearningPointContext(this.store.get(), this.activeIndex);\n    }\n\n    selectOnly(index) {\n      const point = this.pointByIndex(index);\n      if (!point) return;\n      this.selectedIndices.clear();\n      this.selectedIndices.add(Number(point.index));\n      this.activeIndex = Number(point.index);\n      this.refreshActiveEditor();\n      this.renderChart();\n    }\n\n    selectPoint(index) { this.selectOnly(index); }\n\n    toggleSelection(index) {\n      const point = this.pointByIndex(index);\n      if (!point) return;\n      const key = Number(point.index);\n      if (this.selectedIndices.has(key)) this.selectedIndices.delete(key);\n      else this.selectedIndices.add(key);\n      if (this.selectedIndices.has(key)) this.activeIndex = key;\n      else if (this.activeIndex === key) this.activeIndex = [...this.selectedIndices].at(-1) ?? null;\n      this.refreshActiveEditor();\n      this.renderChart();\n    }\n\n    clearSelection() {\n      this.selectedIndices.clear();\n      this.activeIndex = null;\n      this.refreshActiveEditor();\n      this.renderChart();\n    }\n\n    nudgeActive(delta) { this.nudgeSelection(delta); }\n\n    nudgeSelection(delta) {\n      const indices = this.selectedPointIndices();\n      if (!indices.length || !delta) return;\n      for (const index of indices) {\n        const point = this.pointByIndex(index);\n        const current = finite(this.proposals.get(index)?.targetFactor ?? point?.factor);\n        if (current === null) continue;\n        const requested = Math.max(0.6, Math.min(4, current + delta));\n        const preview = this.api.previewCurvePoint(index, requested);\n        if (!preview?.ok) continue;\n        this.acceptPreview(preview, true);\n      }\n      this.refreshActiveEditor();\n      this.renderChart();\n      this.renderProposalList();\n    }\n"""
replace_once(curve, old_selection, new_selection)

old_prepare = """    prepareActivePoint() {\n      if (this.activeIndex === null) return;\n      const requested = finite(document.getElementById('curveTargetFactor')?.value);\n      if (requested === null) { this.alert('Informe o fator K desejado.'); return; }\n      const preview = this.api.previewCurvePoint(this.activeIndex, requested);\n      if (!preview?.ok) { this.alert(preview?.error || 'Prévia da Curva K inválida.'); return; }\n      this.acceptPreview(preview);\n    }\n"""
new_prepare = """    prepareActivePoint() { return this.prepareSelectionTarget(); }\n\n    prepareSelectionTarget() {\n      const indices = this.selectedPointIndices();\n      if (!indices.length) { this.alert('Selecione pelo menos um ponto da Curva K.'); return false; }\n      const requested = finite(document.getElementById('curveTargetFactor')?.value);\n      if (requested === null) { this.alert('Informe o fator K desejado.'); return false; }\n      let prepared = 0;\n      for (const index of indices) {\n        const preview = this.api.previewCurvePoint(index, requested);\n        if (!preview?.ok) continue;\n        this.acceptPreview(preview, true);\n        prepared += 1;\n      }\n      this.refreshActiveEditor();\n      this.renderChart();\n      this.renderProposalList();\n      if (!prepared) this.alert('Nenhum ponto selecionado produziu uma prévia válida.');\n      return prepared > 0;\n    }\n"""
replace_once(curve, old_prepare, new_prepare)

replace_once(
    curve,
    """        const selected = Number(point.index) === this.activeIndex;\n        const proposed = this.proposals.has(Number(point.index));""",
    """        const active = Number(point.index) === this.activeIndex;\n        const selected = this.selectedIndices.has(Number(point.index));\n        const proposed = this.proposals.has(Number(point.index));""",
)

replace_once(
    curve,
    """        return `<circle class=\"curve-point-hit\" data-curve-index=\"${point.index}\" cx=\"${x}\" cy=\"${y}\" r=\"15\" tabindex=\"0\" role=\"button\" aria-label=\"Ponto ${Number(point.index) + 1}, ${fmt(point.petrolMs, 2)} ms\"></circle><circle class=\"curve-point ${selected ? 'active' : ''} ${proposed ? 'proposed' : ''}\" cx=\"${x}\" cy=\"${y}\" r=\"${selected ? 9 : 7}\"></circle>${label}`;""",
    """        return `<circle class=\"curve-point-hit\" data-curve-index=\"${point.index}\" cx=\"${x}\" cy=\"${y}\" r=\"18\" tabindex=\"0\" role=\"button\" aria-pressed=\"${selected}\" aria-label=\"Ponto ${Number(point.index) + 1}, ${fmt(point.petrolMs, 2)} ms\"></circle><circle class=\"curve-point ${active ? 'active' : ''} ${selected ? 'selected' : ''} ${proposed ? 'proposed' : ''}\" data-curve-point=\"${point.index}\" cx=\"${x}\" cy=\"${y}\" r=\"${selected ? 9 : 7}\"></circle>${label}`;""",
)

old_bind_points = """      host.querySelectorAll('[data-curve-index]').forEach(point => {\n        const select = () => this.selectPoint(Number(point.dataset.curveIndex));\n        point.addEventListener('click', select);\n        point.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); select(); } });\n      });\n"""
new_bind_points = """      const syncSelectionClasses = () => {\n        host.querySelectorAll('[data-curve-index]').forEach(hit => {\n          const index = Number(hit.dataset.curveIndex);\n          const selected = this.selectedIndices.has(index);\n          hit.setAttribute('aria-pressed', String(selected));\n          const dot = host.querySelector(`[data-curve-point=\"${index}\"]`);\n          dot?.classList.toggle('selected', selected);\n          dot?.classList.toggle('active', index === this.activeIndex);\n          dot?.setAttribute('r', selected ? '9' : '7');\n        });\n      };\n      const finishDrag = () => {\n        if (!this.dragSelecting) return;\n        if (!this.dragMoved && this.dragStartWasSelected && this.dragStartIndex !== null) {\n          this.selectedIndices.delete(Number(this.dragStartIndex));\n          if (this.activeIndex === Number(this.dragStartIndex)) this.activeIndex = [...this.selectedIndices].at(-1) ?? null;\n          this.refreshActiveEditor();\n        }\n        this.dragSelecting = false;\n        this.dragMoved = false;\n        this.dragStartIndex = null;\n        this.dragStartWasSelected = false;\n        syncSelectionClasses();\n      };\n      host.querySelectorAll('[data-curve-index]').forEach(point => {\n        const index = () => Number(point.dataset.curveIndex);\n        point.addEventListener('pointerdown', event => {\n          if (typeof event.button === 'number' && event.button !== 0) return;\n          event.preventDefault();\n          const key = index();\n          this.dragSelecting = true;\n          this.dragMoved = false;\n          this.dragStartIndex = key;\n          this.dragStartWasSelected = this.selectedIndices.has(key);\n          this.selectedIndices.add(key);\n          this.activeIndex = key;\n          this.refreshActiveEditor();\n          syncSelectionClasses();\n        });\n        point.addEventListener('pointerenter', () => {\n          if (!this.dragSelecting) return;\n          const key = index();\n          if (key !== this.dragStartIndex) this.dragMoved = true;\n          this.selectedIndices.add(key);\n          this.activeIndex = key;\n          this.refreshActiveEditor();\n          syncSelectionClasses();\n        });\n        point.addEventListener('pointerup', finishDrag);\n        point.addEventListener('pointercancel', finishDrag);\n        point.addEventListener('keydown', event => {\n          if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); this.toggleSelection(index()); }\n        });\n      });\n      host.onpointerup = finishDrag;\n      host.onpointercancel = finishDrag;\n"""
replace_once(curve, old_bind_points, new_bind_points)

replace_once(
    html,
    "<div><small>AJUSTE GLOBAL</small><h2 id=\"curveTitle\">Curva K</h2><p>Aprenda a tendência global e edite cada ponto individualmente.</p></div>",
    "<div><small>AJUSTE GLOBAL</small><h2 id=\"curveTitle\">Curva K</h2><p>Aprenda a tendência global e ajuste um ou vários pontos com a mesma ação.</p></div>",
)
replace_once(
    html,
    "<div class=\"curve-legend\"><span><i class=\"line actual\"></i>Atual da ECU</span><span><i class=\"line proposal\"></i>Proposta</span><span class=\"curve-point-hint\">toque em qualquer um dos 30 pontos</span></div>",
    "<div class=\"curve-legend\"><span><i class=\"line actual\"></i>Atual da ECU</span><span><i class=\"line proposal\"></i>Proposta</span><span class=\"curve-point-hint\">toque para selecionar · arraste para selecionar vários</span></div>",
)
replace_once(
    html,
    "<div class=\"editor-heading\"><div><small>PONTO ATIVO</small><h3 id=\"curveActivePoint\">Selecione um ponto</h3></div></div>",
    "<div class=\"editor-heading\"><div><small>SELEÇÃO</small><h3 id=\"curveActivePoint\">Selecione um ou vários pontos</h3></div><button id=\"curveClearSelection\" type=\"button\" class=\"quiet-button\">Limpar seleção</button></div>",
)
replace_once(
    html,
    "<button id=\"curvePreparePoint\" type=\"button\" class=\"secondary wide\">Preparar este ponto</button>",
    "<button id=\"curvePreparePoint\" type=\"button\" class=\"secondary wide\">Aplicar valor à seleção</button>",
)

css_text = css.read_text(encoding="utf-8")
marker = "/* Curve batch selection — cockpit touch hardening. */"
if marker not in css_text:
    css_text += """\n\n/* Curve batch selection — cockpit touch hardening. */\n.curve-point-hit { touch-action: none; cursor: pointer; }\n.curve-point.selected {\n  stroke: var(--accent);\n  stroke-width: 4px;\n}\n.curve-point.active.selected { stroke-width: 5px; }\n#curveClearSelection { min-height: 40px; white-space: nowrap; }\n"""
    css.write_text(css_text, encoding="utf-8")

print("CURVE_BATCH_APPLICATOR=READY")
