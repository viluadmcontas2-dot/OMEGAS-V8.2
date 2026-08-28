(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  function finite(value) { return Number.isFinite(Number(value)) ? Number(value) : null; }
  function fmt(value, digits) {
    const n = finite(value);
    return n === null ? '—' : n.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits });
  }
  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[char]));
  }

  class PredictorScreen {
    constructor(app) {
      this.app = app;
      this.store = app.store;
      this.router = app.router;
      this.data = null;
      this.activeKey = '';
      this.lastRoute = '';
      this.injectShell();
      this.bind();
      this.unsubscribePredictor = this.store.subscribeSelected(
        state => state.predictor,
        predictor => this.onPredictorState(predictor),
        true,
      );
      this.unsubscribeCalibration = this.store.subscribeSelected(
        state => state.calibrationState?.predictor || null,
        predictor => this.onCalibrationPredictorState(predictor),
        true,
      );
      this.unsubscribeStore = this.store.subscribeSelected(
        state => state.route,
        route => this.onRoute(route),
        true,
      );
    }

    injectShell() {
      if (!document.querySelector('link[data-predictor-style]')) {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = 'styles-predictor.css';
        link.dataset.predictorStyle = 'true';
        document.head?.appendChild?.(link);
      }
      const nav = document.querySelector('.side-nav');
      if (nav && !nav.querySelector('[data-route="predictor"]')) {
        const button = document.createElement('button');
        button.type = 'button';
        button.dataset.route = 'predictor';
        button.innerHTML = '<i>03</i><span>Predictor</span>';
        const learning = nav.querySelector('[data-route="learning"]');
        if (learning?.nextSibling) nav.insertBefore(button, learning.nextSibling); else nav.appendChild(button);
        const order = ['dashboard', 'learning', 'predictor', 'map', 'curve', 'obd', 'suggestions', 'tools'];
        order.forEach((route, index) => {
          const node = nav.querySelector(`[data-route="${route}"] i`);
          if (node) node.textContent = String(index + 1).padStart(2, '0');
        });
      }
      const host = document.querySelector('.screen-host');
      if (host && !host.querySelector('[data-screen="predictor"]')) {
        const section = document.createElement('section');
        section.className = 'screen predictor-screen';
        section.dataset.screen = 'predictor';
        section.setAttribute('aria-labelledby', 'predictorTitle');
        section.innerHTML = `
          <div class="page-intro predictor-intro">
            <div><small>PREDICTOR</small><h2 id="predictorTitle">Mapa de decisão K</h2><p>Observado, validado e previsto sem esconder incerteza.</p></div>
            <div class="predictor-state-summary"><span id="predictorRevision">Aguardando ciência</span><b id="predictorCoverage">0 com suporte</b></div>
          </div>
          <div class="predictor-workspace">
            <section class="predictor-surface">
              <div class="predictor-axis-top">RPM →</div>
              <div id="predictorGrid" class="predictor-grid" aria-label="Superfície Predictor"></div>
              <div class="predictor-axis-side">PETROL INJ. ↓</div>
              <div class="predictor-legend"><span data-state="VALIDADO">Validado</span><span data-state="OBSERVADO">Observado</span><span data-state="PREVISTO">Previsto</span><span data-state="DESCONHECIDO">Sem previsão</span></div>
            </section>
            <aside id="predictorInspector" class="inspector-panel predictor-inspector">
              <div class="detail-empty"><b>Escolha uma célula</b><span>O Predictor explica origem, autoridade, risco e suporte antes de qualquer revisão manual.</span></div>
            </aside>
          </div>`;
        const map = host.querySelector('[data-screen="map"]');
        if (map) host.insertBefore(section, map); else host.appendChild(section);
      }
      this.navButton = document.querySelector('[data-route="predictor"]');
      this.root = document.querySelector('[data-screen="predictor"]');
      this.grid = document.getElementById('predictorGrid');
      this.inspector = document.getElementById('predictorInspector');
    }

    bind() {
      this.navButton?.addEventListener('click', () => this.router.navigate('predictor'));
      this.grid?.addEventListener('click', event => {
        const cell = event.target.closest('[data-predictor-cell]');
        if (!cell) return;
        this.activeKey = cell.dataset.predictorCell || '';
        this.render();
      });
      this.inspector?.addEventListener('click', event => {
        const button = event.target.closest('[data-predictor-review]');
        if (!button) return;
        const cell = this.cells().find(item => String(item.key) === this.activeKey);
        if (!cell || !ns.PredictorModel?.openMapReview(this.router, cell)) {
          this.store.patch({ alert: { level: 'warning', message: 'Esta célula ainda não possui proposta revisável.' } });
        }
      });
    }

    onPredictorState(predictor) {
      const next = predictor?.data || null;
      if (next === this.data) return;
      this.data = next;
      if (this.store.get().route === 'predictor') this.render();
    }

    onCalibrationPredictorState(predictor) {
      if (!predictor || this.store.get().predictor?.data) return;
      if (predictor === this.data) return;
      this.data = predictor;
      if (this.store.get().route === 'predictor') this.render();
    }

    onRoute(route) {
      const active = route === 'predictor';
      this.navButton?.classList.toggle('active', active);
      this.navButton?.setAttribute('aria-current', active ? 'page' : 'false');
      this.root?.classList.toggle('active', active);
      this.root?.setAttribute('aria-hidden', active ? 'false' : 'true');
      if (active) {
        const eyebrow = document.getElementById('routeEyebrow');
        const title = document.getElementById('routeTitle');
        if (eyebrow) eyebrow.textContent = 'DECIDIR';
        if (title) title.textContent = 'Predictor';
        this.render();
      }
      this.lastRoute = route;
    }

    cells() { return Array.isArray(this.data?.cells) ? this.data.cells : []; }

    render() {
      if (!this.grid || !this.inspector) return;
      const cells = this.cells();
      if (!cells.length) {
        this.grid.innerHTML = '<div class="predictor-empty"><b>Sem superfície disponível</b><span>A tela aguarda o estado científico já publicado na Store.</span></div>';
        this.inspector.innerHTML = '<div class="detail-empty"><b>Predictor aguardando</b><span>A rota não inicia aquisição, polling ou recomputação científica.</span></div>';
        return;
      }
      const rpmBins = [...new Set(cells.map(item => Number(item.rpm)).filter(Number.isFinite))].sort((a, b) => a - b);
      const petrolBins = [...new Set(cells.map(item => Number(item.petrolMs)).filter(Number.isFinite))].sort((a, b) => a - b);
      const cellByKey = new Map(cells.map(item => [`${Number(item.row)}:${Number(item.column)}`, item]));
      const explained = cells.map(item => ns.PredictorModel?.explainCell(item) || {});
      const validated = explained.filter(item => item.visualState === 'VALIDADO').length;
      const observed = explained.filter(item => item.visualState === 'OBSERVADO').length;
      const predicted = explained.filter(item => item.visualState === 'PREVISTO').length;
      const supported = validated + observed + predicted;
      const revision = document.getElementById('predictorRevision');
      const coverage = document.getElementById('predictorCoverage');
      if (revision) revision.textContent = this.data.revisionToken ? `rev ${String(this.data.revisionToken).slice(0, 8)}` : 'revisão científica';
      if (coverage) coverage.textContent = `${supported}/144 com estado humano tipado`;

      let html = '<div class="predictor-corner">ms \\ RPM</div>';
      rpmBins.forEach(rpm => { html += `<div class="predictor-axis-cell">${Math.round(rpm).toLocaleString('pt-BR')}</div>`; });
      petrolBins.forEach((petrolMs, row) => {
        html += `<div class="predictor-axis-cell predictor-ms">${fmt(petrolMs, 1)}</div>`;
        rpmBins.forEach((_, column) => {
          const cell = cellByKey.get(`${row}:${column}`) || {};
          const explanation = ns.PredictorModel?.explainCell(cell) || {};
          const state = String(explanation.visualState || 'DESCONHECIDO');
          const target = finite(explanation.targetK);
          const current = finite(explanation.currentK);
          const confidence = Math.round(Math.max(0, Math.min(1, finite(explanation.confidence) || 0)) * 100);
          const active = String(cell.key || `${row}:${column}`) === this.activeKey;
          const primary = target !== null ? target : current !== null ? current : '—';
          const label = target !== null && current !== null
            ? `${escapeHtml(explanation.targetLabel || 'ESTIMATIVA')}: ${current}→${target}`
            : escapeHtml(explanation.stateLabel || 'Sem previsão');
          html += `<button type="button" data-predictor-cell="${escapeHtml(cell.key || `${row}:${column}`)}" class="predictor-cell ${state.toLowerCase()} ${active ? 'active' : ''}" data-state="${escapeHtml(state)}" aria-label="${fmt(petrolMs, 1)} ms, ${rpmBins[column]} RPM, ${escapeHtml(explanation.stateLabel || state)}"><b>${primary}</b><span>${label}</span><i style="--confidence:${confidence}%"></i></button>`;
        });
      });
      this.grid.innerHTML = html;

      const activeCell = cells.find(item => String(item.key) === this.activeKey)
        || cells.find(item => item.humanState?.visualState === 'VALIDADO')
        || cells.find(item => item.humanState?.visualState === 'OBSERVADO')
        || cells.find(item => item.humanState?.visualState === 'PREVISTO')
        || cells[0];
      if (!this.activeKey && activeCell) this.activeKey = String(activeCell.key || '');
      this.renderInspector(activeCell);
    }

    renderInspector(cell) {
      const explanation = ns.PredictorModel?.explainCell(cell) || {};
      const confidence = Math.round((finite(explanation.confidence) || 0) * 100);
      const provenance = Array.isArray(explanation.provenance) ? explanation.provenance : [];
      const sourceLabels = provenance.slice(0, 6).map(item => String(item.source || '')).filter(Boolean);
      const interval = finite(explanation.intervalLowerK) !== null && finite(explanation.intervalUpperK) !== null
        ? `${fmt(explanation.intervalLowerK, 1)}–${fmt(explanation.intervalUpperK, 1)}`
        : '—';
      const disclosure = explanation.disclosure
        ? `<p class="predictor-reason">${escapeHtml(explanation.disclosure)}</p>`
        : '';
      this.inspector.innerHTML = `
        <div class="editor-heading"><div><small>${escapeHtml(explanation.stateLabel || 'Sem previsão')}</small><h3>${fmt(explanation.petrolMs, 1)} ms · ${Math.round(finite(explanation.rpm) || 0).toLocaleString('pt-BR')} RPM</h3></div><span class="predictor-confidence">${confidence}%</span></div>
        <div class="predictor-k-pair"><div><small>K ATUAL</small><b>${explanation.currentK ?? '—'}</b></div><div><small>${escapeHtml(explanation.targetLabel || 'ESTIMATIVA')}</small><b>${explanation.targetK ?? '—'}</b></div></div>
        <div class="predictor-facts"><span>intervalo ${interval}</span><span>${escapeHtml(explanation.authority || 'UNKNOWN')}</span><span>${escapeHtml(explanation.actionState || 'ABSTAIN')}</span></div>
        <p class="predictor-reason">${escapeHtml(explanation.reason || 'Estado científico humano indisponível.')}</p>
        ${disclosure}
        <div class="predictor-facts"><span>${Number(explanation.distinctTrajectories || 0)} trajetórias independentes</span><span>${Number(explanation.nativeAnchorCount || 0)} âncoras AutoCal</span><span>${explanation.predicted ? 'predição tipada' : explanation.directObservation ? 'observação direta' : 'sem previsão'}</span></div>
        <details class="predictor-provenance"><summary>Ver proveniência</summary><p>${sourceLabels.length ? sourceLabels.map(escapeHtml).join(' · ') : 'Nenhuma fonte científica suficiente.'}</p></details>
        <button type="button" data-predictor-review class="primary wide" ${explanation.requiresHumanReview ? '' : 'disabled'}>${explanation.requiresHumanReview ? 'Revisar no Mapa K' : 'Sem proposta revisável'}</button>
        <div class="safety-copy"><b>Somente decisão</b><span>Predictor não grava na ECU. O Mapa K oficial continua exigindo revisão e confirmação.</span></div>`;
    }
  }

  function boot() {
    const app = root.OmegasApp;
    if (!app?.store || !app?.router || !ns.PredictorModel) {
      root.setTimeout(boot, 25);
      return;
    }
    if (app.screens.predictor) return;
    app.screens.predictor = new PredictorScreen(app);
  }

  ns.PredictorScreen = PredictorScreen;
  boot();
})(typeof window !== 'undefined' ? window : globalThis);
