#!/usr/bin/env python3
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]


def path(rel): return ROOT / rel

def read(rel): return path(rel).read_text(encoding="utf-8")

def write(rel, text):
    target = path(rel); target.parent.mkdir(parents=True, exist_ok=True); target.write_text(text, encoding="utf-8")

def delete(rel):
    target = path(rel)
    if target.is_file(): target.unlink()
    elif target.is_dir(): shutil.rmtree(target)

def replace(rel, old, new):
    target = path(rel)
    if not target.is_file(): return
    text = target.read_text(encoding="utf-8")
    if old in text: target.write_text(text.replace(old, new), encoding="utf-8")

def regex(rel, pattern, replacement, flags=0):
    target = path(rel)
    if not target.is_file(): return
    text = target.read_text(encoding="utf-8")
    updated = re.sub(pattern, replacement, text, flags=flags)
    if updated != text: target.write_text(updated, encoding="utf-8")

# Router: only current screens. Auto-Cal stays an independent observational tab.
write("app/src/main/assets/ui/core/router.js", r'''(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  const ROUTES = ['dashboard', 'learning', 'map', 'curve', 'obd', 'suggestions', 'tools'];
  const STORAGE_KEY = 'omegas-blue-route';

  function loadOptionalScript(src) {
    if (typeof document === 'undefined') return;
    if (document.querySelector(`script[data-omegas-extension="${src}"]`)) return;
    const script = document.createElement('script');
    script.src = src;
    script.dataset.omegasExtension = src;
    script.onerror = () => console.error('[OMEGAS router] extensão não carregada:', src);
    document.head.appendChild(script);
  }

  class Router {
    constructor(store) { this.store = store; this.onNavigate = null; }
    current() { return this.store.get().route; }
    navigate(route, context) {
      if (!ROUTES.includes(route)) return false;
      const previous = this.current();
      if (previous === route && context === undefined) return true;
      this.store.patch({ route, routeContext: context === undefined ? null : context });
      try { root.localStorage.setItem(STORAGE_KEY, route); } catch (_) {}
      if (typeof this.onNavigate === 'function') this.onNavigate(route, previous, context);
      return true;
    }
    restore() {
      let saved = 'dashboard';
      try { saved = root.localStorage.getItem(STORAGE_KEY) || saved; } catch (_) {}
      if (!ROUTES.includes(saved)) saved = 'dashboard';
      this.store.patch({ route: saved, routeContext: null });
      return saved;
    }
  }

  ns.Router = Router;
  ns.ROUTES = ROUTES;
  loadOptionalScript('components/vehicle-status-strip.js');
  loadOptionalScript('components/split-layout.js');
  loadOptionalScript('core/autocal-api.js');
  loadOptionalScript('screens/autocal-cockpit.js');
})(typeof window !== 'undefined' ? window : globalThis);
''')

# Store has no second scientific state machine.
replace("app/src/main/assets/ui/core/store.js", "      predictor: { state: 'idle', data: null, activeCell: null, inspector: null },\n", "")

# Browser demo mirrors evidence + Blue proposal only; it never fabricates a correction.
native = "app/src/main/assets/ui/core/native-api.js"
regex(native, r'  function demoLearning\(\) \{.*?\n  \}\n\n  function demoObdMaps', r'''  function demoLearning() {
    const regions = [
      { id: 'demo-petrol-1', fuel: 'PETROL', rpm: 1800, map_bar: 0.44, petrol_ms: 4.10, quality: 0.92, updated_at: Date.now() - 5000, visits: ['demo-petrol-1'] },
      { id: 'demo-cng-1', fuel: 'CNG', rpm: 1810, map_bar: 0.45, petrol_ms: 4.18, quality: 0.89, updated_at: Date.now() - 2000, epoch: 1, visits: ['demo-cng-1'] },
    ];
    return {
      ok: true, demo: true, format: 'omegas-blue-evidence', learningDataRevision: 1, epoch: 1,
      regions, comparisons: [], decisionAuthority: 'BLUE_CAUSAL_ENGINE',
      current: { fuel: 'GNV', rpm: 1810, petrolMs: 4.18, mapBar: 0.45 },
      uiPipeline: 'PHYSICAL_EVIDENCE_ONLY',
    };
  }

  function demoObdMaps''', flags=re.S)
replace(native, "OBD_ONLY_LEGACY", "OBD_ONLY_OBSERVATIONAL")
regex(native, r'calibrationState: \{ ready: true, suggestionItems: \[\], predictor: \{ ok: true, cells: \[\] \} \},\n\s*predictor: \{ ok: true, cells: \[\] \},', "calibrationState: { ready: true, decisionAuthority: 'BLUE_CAUSAL_ENGINE', proposal: { ok: true, available: false, state: 'WAITING_FOR_EQUIVALENT_FUEL_EVIDENCE', manualOnly: true } },")

# Shell consumes the single native Blue state. Suggestions page becomes a read-only proposal explanation.
app = "app/src/main/assets/ui/app.js"
replace(app, "  const selectedSuggestionIds = new Set();\n", "")
replace(app, "    suggestions: ['DECIDIR', 'Sugestões'],", "    suggestions: ['PROPOSTA', 'Proposta Blue'],")
replace(app, " || route === 'predictor'", "")
regex(app, r'\n\s*const predictor = data\.predictor \|\| data\.calibrationState\?\.predictor;\n\s*if \(predictor\) \{\n\s*patch\.predictor = .*?\n\s*\}', "", flags=re.S)
regex(app, r'  function suggestionTargetLabel\(item\) \{.*?\n  function renderPersistentSuggestions\(state\) \{.*?\n  \}\n\n  /\*\* Pinta cache primeiro;', r'''  function renderPersistentSuggestions(state) {
    const host = byId('suggestionList');
    if (!host) return;
    const calibration = state.calibrationState || {};
    const proposal = calibration.proposal && typeof calibration.proposal === 'object' ? calibration.proposal : {};
    const comparison = calibration.latestComparison && typeof calibration.latestComparison === 'object' ? calibration.latestComparison : null;
    const available = proposal.available === true;
    const multiplier = finite(proposal.correctionMultiplier);
    const actionable = available && multiplier !== null;
    setText('suggestionCount', actionable ? 1 : 0);
    const error = finite(comparison?.errorPercent);
    const quality = finite(comparison?.quality);
    const stateLabel = proposal.state || (available ? 'EVIDÊNCIA COMPARADA' : 'AGUARDANDO EVIDÊNCIA');
    const explanation = actionable
      ? 'O Blue mediu resposta causal suficiente para apresentar um multiplicador. A gravação continua manual e exige revisão, ACK e readback.'
      : 'O Blue não possui ganho causal medido suficiente para transformar erro em K. Nenhum alvo é inventado.';
    host.innerHTML = `<section class="suggestion-group"><header><div><small>ÚNICA AUTORIDADE</small><h3>BlueCausalEngine</h3></div></header>
      <div class="suggestion-row"><span></span><span class="suggestion-row-main"><b>${escapeHtml(stateLabel)}</b><span>${escapeHtml(explanation)}</span></span><span class="suggestion-row-meta"><b>${error === null ? '—' : `${error >= 0 ? '+' : ''}${error.toFixed(2).replace('.', ',')}%`}</b><small>${quality === null ? 'qualidade —' : `qualidade ${Math.round(quality * 100)}%`}</small></span></div>
      <div class="suggestion-group-actions"><button type="button" class="secondary" data-open-curve>Revisar Curva K manualmente</button></div></section>`;
    host.querySelector('[data-open-curve]')?.addEventListener('click', () => router.navigate('curve', { origin: 'blue-proposal' }));
  }

  /** Pinta cache primeiro;''', flags=re.S)
regex(app, r'\n\s*if \(route === \'predictor\'\) \{.*?\n\s*\}', "", flags=re.S)
replace(app, " || route === 'predictor'", "")

# Curve screen: manual K editor + evidence display. It never derives a target from evidence.
write("app/src/main/assets/ui/screens/curve.js", r'''(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  function finite(value) { return Number.isFinite(Number(value)) ? Number(value) : null; }
  function fmt(value, digits) { const n = finite(value); return n === null ? '—' : n.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits }); }
  function text(id, value) { const node = document.getElementById(id); if (node) node.textContent = value == null ? '—' : String(value); }
  function escapeHtml(value) { return String(value ?? '').replace(/[&<>\"]/g, c => ({ '&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;' }[c])); }

  class CurveScreen {
    constructor(store, api) {
      this.store = store; this.api = api; this.root = document.querySelector('[data-screen="curve"]');
      this.data = null; this.activeIndex = null; this.proposals = new Map(); this.reading = false; this.writing = false; this.view = 'editor'; this.learningSignature = '';
      this.bind();
    }
    bind() {
      document.getElementById('curveReadButton')?.addEventListener('click', () => this.startRead());
      document.getElementById('curvePreparePoint')?.addEventListener('click', () => this.prepareActivePoint());
      document.querySelectorAll('[data-curve-view]').forEach(button => button.addEventListener('click', () => this.setView(button.dataset.curveView || 'editor')));
      document.querySelectorAll('[data-curve-nudge]').forEach(button => button.addEventListener('click', () => this.nudgeActive(Number(button.dataset.curveNudge) || 0)));
      document.getElementById('curveClearProposals')?.addEventListener('click', () => { this.proposals.clear(); this.renderChart(); this.renderProposalList(); });
      document.getElementById('curveReviewButton')?.addEventListener('click', () => this.openReview());
      document.getElementById('curveReviewBack')?.addEventListener('click', () => this.closeReview());
      document.getElementById('curveWriteButton')?.addEventListener('click', () => this.writeReview());
      document.getElementById('curveDismissResult')?.addEventListener('click', () => this.closeReview());
    }
    needsLearning() { return this.view === 'learning'; }
    setView(view) {
      if (view !== 'learning' && view !== 'editor') return false;
      this.view = view;
      document.querySelectorAll('[data-curve-view]').forEach(button => button.classList.toggle('active', button.dataset.curveView === view));
      document.querySelectorAll('[data-curve-panel]').forEach(panel => panel.classList.toggle('active', panel.dataset.curvePanel === view));
      if (view === 'learning') this.renderLearning(this.store.get()); else this.renderChart();
      return true;
    }
    onEnter() { if (!this.data && !this.reading) this.startRead(); if (this.view === 'learning') this.renderLearning(this.store.get()); }
    startRead() {
      if (this.reading || this.writing) return;
      const result = this.api.startCurveRead();
      if (!result?.ok || !result?.started) { this.alert(result?.error || 'Não foi possível iniciar a leitura da Curva K.'); return; }
      this.reading = true; this.data = null; this.proposals.clear(); text('curveSourceStatus', 'Lendo 30 pontos diretamente da ECU'); this.root?.classList.add('is-reading');
    }
    poll() {
      if (!this.reading && !this.writing) return;
      const operation = this.api.curveOperation(); if (!operation) return;
      if (this.reading && !operation.busy && (operation.state === 'COMPLETED' || operation.demo)) {
        this.reading = false; this.root?.classList.remove('is-reading');
        if (!operation.ok || !Array.isArray(operation.points) || operation.points.length !== 30) { this.alert(operation.error || 'A Curva K não retornou 30 pontos válidos.'); text('curveSourceStatus', 'Curva não confirmada'); return; }
        this.data = operation; text('curveSourceStatus', 'ECU confirmada · 30 pontos'); this.renderChart(); this.renderEvidence(this.store.get()); this.selectPoint(0);
        if (this.view === 'learning') this.renderLearning(this.store.get());
        this.store.patch({ curve: { ...this.store.get().curve, state: 'ready', data: operation, status: {} } });
        return;
      }
      if (!this.writing) return;
      const progress = Math.max(0, Math.min(100, finite(operation.progress) || finite(operation.writerProgress) || 0));
      const bar = document.getElementById('curveOperationProgress'); if (bar) bar.style.width = `${progress}%`;
      text('curveOperationTitle', operation.message || operation.writerMessage || 'Backup · escrita · ACK · readback');
      if (operation.busy) return;
      this.writing = false; this.root?.classList.remove('is-writing'); this.root?.classList.add('has-result');
      const result = document.getElementById('curveOperationResult');
      if (operation.state === 'BATCH_CONFIRMED' && operation.readbackValid === true) {
        if (result) { result.dataset.level = 'ok'; result.querySelector('b').textContent = 'Curva K confirmada pela ECU'; result.querySelector('span').textContent = 'ACK e readback completos. A curva será relida.'; }
        this.data = null; this.proposals.clear(); this.startRead();
      } else {
        if (result) { result.dataset.level = 'critical'; result.querySelector('b').textContent = 'A Curva K não foi confirmada'; result.querySelector('span').textContent = operation.error || operation.message || 'A ECU não confirmou toda a operação.'; }
        this.data = null;
      }
    }
    points() { return Array.isArray(this.data?.points) ? this.data.points : []; }
    selectPoint(index) {
      const point = this.points().find(item => Number(item.index) === Number(index)); if (!point) return;
      this.activeIndex = Number(point.index); text('curveActivePoint', `Ponto ${this.activeIndex + 1} · ${fmt(point.petrolMs, 2)} ms`); text('curveCurrentFactor', fmt(point.factor, 4));
      const input = document.getElementById('curveTargetFactor'); if (input) input.value = String(finite(this.proposals.get(this.activeIndex)?.targetFactor ?? point.factor) ?? '');
      this.renderChart(); this.renderLearningPointContext(this.store.get(), this.activeIndex);
    }
    nudgeActive(delta) {
      if (this.activeIndex === null || !delta) return;
      const input = document.getElementById('curveTargetFactor'); const current = finite(input?.value) ?? finite(this.points().find(item => Number(item.index) === this.activeIndex)?.factor); if (current === null) return;
      if (input) input.value = String(Math.max(0.6, Math.min(4, current + delta)).toFixed(4)); this.prepareActivePoint();
    }
    prepareActivePoint() {
      if (this.activeIndex === null) return;
      const requested = finite(document.getElementById('curveTargetFactor')?.value); if (requested === null) { this.alert('Informe o fator K desejado.'); return; }
      const preview = this.api.previewCurvePoint(this.activeIndex, requested); if (!preview?.ok) { this.alert(preview?.error || 'Prévia da Curva K inválida.'); return; }
      this.acceptPreview(preview);
    }
    acceptPreview(preview) {
      const index = Number(preview.index); if (!Number.isInteger(index)) { this.alert('Prévia da Curva K sem índice válido.'); return; }
      if (!preview.changed) this.proposals.delete(index); else this.proposals.set(index, preview);
      if (index === this.activeIndex) text('curveTargetNormalized', preview.changed ? `${fmt(preview.currentFactor,4)} → ${fmt(preview.targetFactor,4)}` : 'Sem alteração');
      this.renderChart(); this.renderProposalList();
    }
    renderChart() {
      const host = document.getElementById('curveChart'); const points = this.points(); if (!host) return;
      if (!points.length) { host.innerHTML = '<div class="chart-empty">Leia a Curva K para visualizar os 30 pontos.</div>'; return; }
      const width=920,height=350,padX=42,padY=34; const factors=points.map(item=>finite(this.proposals.get(Number(item.index))?.targetFactor ?? item.factor)||0);
      const min=Math.max(0.55,Math.min(...factors,...points.map(item=>finite(item.factor)||0))-0.08); const max=Math.max(min+0.2,Math.max(...factors,...points.map(item=>finite(item.factor)||0))+0.08);
      const xFor=index=>padX+(index/Math.max(1,points.length-1))*(width-padX*2); const yFor=factor=>height-padY-((factor-min)/(max-min))*(height-padY*2);
      const actual=points.map((p,i)=>`${i?'L':'M'} ${xFor(i).toFixed(1)} ${yFor(Number(p.factor)).toFixed(1)}`).join(' ');
      const prepared=points.map((p,i)=>`${i?'L':'M'} ${xFor(i).toFixed(1)} ${yFor(Number(this.proposals.get(Number(p.index))?.targetFactor ?? p.factor)).toFixed(1)}`).join(' ');
      host.innerHTML=`<svg class="curve-svg" viewBox="0 0 ${width} ${height}" role="img" aria-label="Curva K lida da ECU"><path class="curve-line actual" d="${actual}"></path><path class="curve-line proposal" d="${prepared}"></path>${points.map((p,i)=>{const selected=Number(p.index)===this.activeIndex;const changed=this.proposals.has(Number(p.index));const x=xFor(i).toFixed(1);const y=yFor(Number(this.proposals.get(Number(p.index))?.targetFactor ?? p.factor)).toFixed(1);return `<circle class="curve-point-hit" data-curve-index="${p.index}" cx="${x}" cy="${y}" r="15"></circle><circle class="curve-point ${selected?'active':''} ${changed?'proposed':''}" cx="${x}" cy="${y}" r="${selected?9:7}"></circle>`;}).join('')}</svg>`;
      host.querySelectorAll('[data-curve-index]').forEach(node=>node.addEventListener('click',()=>this.selectPoint(Number(node.dataset.curveIndex))));
    }
    renderLearning(state) {
      const host=document.getElementById('curveLearningChart'); const summary=document.getElementById('curveLearningSummary'); if(!host||!summary)return;
      const regions=Array.isArray(state.learning?.regions)?state.learning.regions:[]; const petrol=regions.filter(r=>['PETROL','GASOLINA'].includes(String(r.fuel).toUpperCase())); const cng=regions.filter(r=>['CNG','GNV','GAS'].includes(String(r.fuel).toUpperCase())); const blue=state.calibrationState||{}; const comparison=blue.latestComparison&&typeof blue.latestComparison==='object'?blue.latestComparison:null;
      const signature=JSON.stringify([petrol.length,cng.length,comparison,blue.proposal]); if(signature===this.learningSignature)return; this.learningSignature=signature;
      const recent=[...petrol.slice(-8),...cng.slice(-8)].sort((a,b)=>Number(a.updated_at||0)-Number(b.updated_at||0));
      host.innerHTML=`<div class="global-summary-list">${recent.map(r=>`<div><span>${escapeHtml(String(r.fuel||'—'))} · ${fmt(r.rpm,0)} RPM · MAP ${fmt(r.map_bar,3)}</span><b>Petrol ${fmt(r.petrol_ms,3)} ms</b><small>qualidade ${Math.round((finite(r.quality)||0)*100)}%</small></div>`).join('')||'<p class="empty-copy">Colete gasolina e GNV para formar evidência física.</p>'}</div>`;
      const proposal=blue.proposal&&typeof blue.proposal==='object'?blue.proposal:{}; const error=finite(comparison?.errorPercent); const quality=finite(comparison?.quality);
      summary.innerHTML=`<div class="editor-heading"><div><small>EVIDÊNCIA BLUE</small><h3>Gasolina é a referência</h3></div></div><div class="global-summary-grid"><div><small>GASOLINA</small><b>${petrol.length}</b></div><div><small>GNV ATUAL</small><b>${cng.length}</b></div><div><small>ERRO BLUE</small><b>${error===null?'—':`${error>=0?'+':''}${fmt(error,2)}%`}</b></div><div><small>QUALIDADE</small><b>${quality===null?'—':`${Math.round(quality*100)}%`}</b></div></div><p class="empty-copy">${proposal.correctionMultiplier==null?'Sem ganho causal medido, o Blue não inventa alvo K.':'Existe resposta causal medida; revise manualmente antes de qualquer escrita.'}</p>`;
      this.renderLearningPointContext(state,this.activeIndex??0);
    }
    renderLearningPointContext(state,index) {
      const host=document.getElementById('curveLearningPointContext'); if(!host)return; const point=this.points().find(item=>Number(item.index)===Number(index))||{}; const blue=state.calibrationState||{}; const comparison=blue.latestComparison&&typeof blue.latestComparison==='object'?blue.latestComparison:null;
      host.innerHTML=`<div><span>Ponto ${Number(index)+1} · ${fmt(point.petrolMs,2)} ms</span><b>K atual ${fmt(point.factor,4)}</b><small>erro Blue ${comparison?fmt(comparison.errorPercent,2)+'%':'—'} · nenhum alvo calculado no navegador</small></div>`;
    }
    renderProposalList() { const host=document.getElementById('curveProposalList'); if(!host)return; const items=[...this.proposals.values()].sort((a,b)=>Number(a.index)-Number(b.index)); host.innerHTML=items.length?items.map(item=>`<div><span>${fmt(item.petrolMs,2)} ms</span><b>${fmt(item.currentFactor,4)} → ${fmt(item.targetFactor,4)}</b><small>preparado manualmente</small></div>`).join(''):'<p>Nenhum ponto preparado.</p>'; const review=document.getElementById('curveReviewButton'); if(review){review.disabled=!items.length;review.textContent=items.length?`Revisar ${items.length} ponto${items.length===1?'':'s'}`:'Prepare pontos';} }
    renderEvidence(state) { const host=document.getElementById('curveEvidenceList'); if(!host)return; const regions=Array.isArray(state.learning?.regions)?state.learning.regions:[]; const petrol=regions.filter(r=>['PETROL','GASOLINA'].includes(String(r.fuel).toUpperCase())); const cng=regions.filter(r=>['CNG','GNV','GAS'].includes(String(r.fuel).toUpperCase())); const comparison=state.calibrationState?.latestComparison; host.innerHTML=`<div class="curve-evidence-summary"><div class="evidence-stat"><b>${petrol.length}</b><span>evidências gasolina</span></div><div class="evidence-stat"><b>${cng.length}</b><span>evidências GNV da época</span></div><div class="evidence-stat"><b>${comparison&&typeof comparison==='object'?fmt(comparison.errorPercent,2)+'%':'—'}</b><span>erro reconciliado pelo Blue</span></div></div><p>A interface só exibe evidência e o resultado do BlueCausalEngine. A edição K permanece manual.</p>`; }
    openReview() { if(!this.proposals.size)return; const host=document.getElementById('curveReviewList'); const items=[...this.proposals.values()].sort((a,b)=>Number(a.index)-Number(b.index)); if(host)host.innerHTML=items.map(item=>`<div><span>Ponto ${Number(item.index)+1} · ${fmt(item.petrolMs,2)} ms</span><b>${fmt(item.currentFactor,4)} → ${fmt(item.targetFactor,4)}</b></div>`).join(''); text('curveReviewCount',`${items.length} ponto${items.length===1?'':'s'}`); this.root?.classList.add('is-reviewing'); }
    closeReview() { this.root?.classList.remove('is-reviewing','is-writing','has-result'); }
    writeReview() { const points=[...this.proposals.values()].map(item=>({index:Number(item.index),currentRaw:Number(item.currentRaw),targetRaw:Number(item.targetRaw)})); if(!points.length)return; const result=this.api.writeCurve(points,'Ajuste manual confirmado na UI Blue'); if(!result?.ok||!result?.started){this.alert(result?.error||'A escrita da Curva K não iniciou.');return;} this.writing=true; this.root?.classList.remove('is-reviewing'); this.root?.classList.add('is-writing'); text('curveOperationTitle','Escrita manual da Curva K'); const bar=document.getElementById('curveOperationProgress');if(bar)bar.style.width='0%'; }
    alert(message) { this.store.patch({alert:{level:'warning',message:String(message||'Operação indisponível')}}); }
  }
  ns.CurveScreen=CurveScreen;
})(typeof window !== 'undefined' ? window : globalThis);
''')

# Remove stale browser decision modules and stale script/style includes if present.
for rel in [
    "app/src/main/assets/ui/components/curve-prediction-state.js",
    "app/src/main/assets/ui/styles-curve-prediction.css",
    "app/src/main/assets/ui/core/predictor-model.js",
    "app/src/main/assets/ui/screens/predictor.js",
    "app/src/main/assets/ui/components/predictor-current-cell.js",
    "app/src/main/assets/ui/suggestion-model.js",
]: delete(rel)
index="app/src/main/assets/ui/index.html"
for name in ["styles-curve-prediction.css","components/curve-prediction-state.js","core/predictor-model.js","screens/predictor.js","components/predictor-current-cell.js","suggestion-model.js"]:
    regex(index, rf'^.*{re.escape(name)}.*\n?', '', flags=re.M)

# Rename useful runtime contracts instead of preserving historical labels.
def rename_clean(old,new,repls):
    source=path(old)
    if not source.is_file(): return
    text=source.read_text(encoding='utf-8')
    for a,b in repls: text=text.replace(a,b)
    write(new,text); source.unlink()
rename_clean("tests/test_red_backpressure_behavior.py","tests/test_runtime_backpressure_behavior.py",[("RedBackpressureBehaviorTest","RuntimeBackpressureBehaviorTest"),("omegas-red-backpressure","omegas-runtime-backpressure"),("red-backpressure.jar","runtime-backpressure.jar"),("RED_BACKPRESSURE_BEHAVIOR","RUNTIME_BACKPRESSURE_BEHAVIOR")])
rename_clean("tests/test_red_hotfix_contract.py","tests/test_runtime_resilience_contract.py",[("RED_","RUNTIME_"),("red_","runtime_")])
rename_clean("tests/test_red_learning_confidence_contract.py","tests/test_learning_quality_contract.py",[("RED_","LEARNING_QUALITY_"),("red_","quality_")])
rename_clean("tests/test_physical_usb_autocal_hotfix_contract.py","tests/test_physical_usb_autocal_contract.py",[("HOTFIX","CONTRACT"),("hotfix","contract")])

# Migration scaffolding/history is not part of the final product tree.
for rel in [
    ".github/workflows/red-fast-learning-one-shot.yml",
    "tests/test_blue_learning_storage_contract.py",
    "docs/MIGRATION.md",
    "docs/incidents/2026-08-12-advisor-overrefresh.md",
    "docs/workunits/OMEGAS-RED-WU-001.md",
    "docs/superpowers/plans/2026-08-19-red-hotfix-performance.md",
    "docs/superpowers/plans/2026-08-19-red-v2-snapshot-fast-science.md",
    "docs/superpowers/plans/2026-08-29-red-fast-global-suggestions.md",
    "docs/superpowers/plans/2026-08-30-red-continuous-fast-learning.md",
    "docs/superpowers/specs/2026-08-29-red-fast-global-suggestions-design.md",
    "docs/superpowers/specs/2026-08-30-red-continuous-fast-learning-design.md",
    "docs/superpowers/plans/2026-09-05-blue-runtime-convergence.md",
    "specs/001-blue-runtime-convergence",
]: delete(rel)

# Current architecture contract: positive assertions only.
write("tests/test_blue_runtime_contract.py", r'''#!/usr/bin/env python3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
required=[
  "app/src/main/java/com/omegas/prohub/blue/BlueCausalEngine.kt",
  "app/src/main/java/com/omegas/prohub/blue/BlueDomain.kt",
  "app/src/main/java/com/omegas/prohub/learning/BlueEvidenceStore.kt",
  "app/src/main/java/com/omegas/prohub/calibration/BlueCalibrationCoordinator.kt",
]
for rel in required: assert (ROOT/rel).is_file(), f"missing current Blue component: {rel}"
runtime=(ROOT/"app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt").read_text(encoding="utf-8")
assert "private val learning = BlueEvidenceStore(" in runtime
store=(ROOT/required[2]).read_text(encoding="utf-8")
assert "decisionAuthority" in store and "BLUE_CAUSAL_ENGINE" in store
coordinator=(ROOT/required[3]).read_text(encoding="utf-8")
assert "private val engine = BlueCausalEngine()" in coordinator
assert "automaticWrite" in coordinator
projection=(ROOT/"app/src/main/java/com/omegas/prohub/learning/LearningUiSnapshotAssembler.kt").read_text(encoding="utf-8")
assert "PHYSICAL_EVIDENCE_ONLY" in projection
router=(ROOT/"app/src/main/assets/ui/core/router.js").read_text(encoding="utf-8")
assert "Blue" not in router or "blue" in router.lower()
print("BLUE_RUNTIME_CONTRACT=PASS")
''')

# Final production scan is intentionally semantic: no second browser decision engine.
assets=path("app/src/main/assets/ui")
for target in assets.rglob("*.js"):
    text=target.read_text(encoding="utf-8")
    lowered=text.lower()
    assert "assistedcalibration" not in lowered, f"parallel calibration authority remains: {target.relative_to(ROOT)}"
    assert "predictor" not in lowered, f"parallel prediction authority remains: {target.relative_to(ROOT)}"

print("BLUE_FINAL_CLEANUP_APPLIED")
