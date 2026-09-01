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
      this.lastScienceRevision = -1;
      this.injectShell();
      this.bind();
      this.unsubscribeStore = this.store.subscribe(state => this.onState(state), true);
    }

    injectShell() {
      if (!document.querySelector('link[data-predictor-style]')) {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = 'styles-predictor.css';
        link.dataset.predictorStyle = 'true';
        document.head.appendChild(link);
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
              <div class="detail-empty"><b>Escolha uma célula</b><span>O Predictor explica origem, confiança e suporte antes de qualquer revisão manual.</span></div>
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
          this.store.patch({ alert: { level: 'warning', message: 'Esta célula ainda não possui alvo K revisável.' } });
        }
      });
    }

    onState(state) {
      const route = state.route;
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
      }

      const revision = Number(state.scienceRevision ?? -1);
      const predictor = state.calibrationState?.predictor || state.predictor?.data || null;
      if (predictor && (revision !== this.lastScienceRevision || predictor !== this.data)) {
        this.data = predictor;
        this.lastScienceRevision = revision;
        if (active) this.render();
      } else if (active && this.lastRoute !== route) {
        this.render();
      }
      this.lastRoute = route;
    }

    cells() { return Array.isArray(this.data?.cells) ? this.data.cells : []; }

    render() {
      if (!this.grid || !this.inspector) return;
      const cells = this.cells();
      if (!cells.length) {
        this.grid.innerHTML = '<div class="predictor-empty"><b>Sem superfície disponível</b><span>Leia o Mapa K e acumule evidência gasolina × GNV.</span></div>';
        this.inspector.innerHTML = '<div class="detail-empty"><b>Predictor aguardando</b><span>Nenhuma célula foi inventada sem suporte.</span></div>';
        return;
      }
      const rpmBins = [...new Set(cells.map(item => Number(item.rpm)).filter(Number.isFinite))].sort((a, b) => a - b);
      const petrolBins = [...new Set(cells.map(item => Number(item.petrolMs)).filter(Number.isFinite))].sort((a, b) => a - b);
      const cellByKey = new Map(cells.map(item => [`${Number(item.row)}:${Number(item.column)}`, item]));
      const validated = cells.filter(item => item.state === 'VALIDADO').length;
      const observed = cells.filter(item => item.state === 'OBSERVADO').length;
      const predicted = cells.filter(item => item.state === 'PREVISTO').length;
      const supported = validated + observed + predicted;
      const revision = document.getElementById('predictorRevision');
      const coverage = document.getElementById('predictorCoverage');
      if (revision) revision.textContent = this.data.revisionToken ? `rev ${String(this.data.revisionToken).slice(0, 8)}` : 'revisão científica';
      if (coverage) coverage.textContent = `${supported}/144 com suporte`;

      let html = '<div class="predictor-corner">ms \\ RPM</div>';
      rpmBins.forEach(rpm => { html += `<div class="predictor-axis-cell">${Math.round(rpm).toLocaleString('pt-BR')}</div>`; });
      petrolBins.forEach((petrolMs, row) => {
        html += `<div class="predictor-axis-cell predictor-ms">${fmt(petrolMs, 1)}</div>`;
        rpmBins.forEach((_, column) => {
          const cell = cellByKey.get(`${row}:${column}`) || {};
          const state = String(cell.state || 'DESCONHECIDO');
          const target = finite(cell.targetK);
          const current = finite(cell.currentK);
          const confidence = Math.round(Math.max(0, Math.min(1, finite(cell.predictionConfidence ?? cell.confidence) || 0)) * 100);
          const active = String(cell.key || `${row}:${column}`) === this.activeKey;
          const primary = target !== null ? target : current !== null ? current : '—';
          html += `<button type="button" data-predictor-cell="${escapeHtml(cell.key || `${row}:${column}`)}" class="predictor-cell ${state.toLowerCase()} ${active ? 'active' : ''}" data-state="${escapeHtml(state)}" aria-label="${fmt(petrolMs, 1)} ms, ${rpmBins[column]} RPM, ${escapeHtml(state)}"><b>${primary}</b><span>${target !== null && current !== null ? `${current}→${target}` : escapeHtml(state === 'DESCONHECIDO' ? 'sem previsão' : state.toLowerCase())}</span><i style="--confidence:${confidence}%"></i></button>`;
        });
      });
      this.grid.innerHTML = html;

      const activeCell = cells.find(item => String(item.key) === this.activeKey) || cells.find(item => item.state === 'VALIDADO') || cells.find(item => item.state === 'OBSERVADO') || cells.find(item => item.state === 'PREVISTO') || cells[0];
      if (!this.activeKey && activeCell) this.activeKey = String(activeCell.key || '');
      this.renderInspector(activeCell);
    }

    renderInspector(cell) {
      const explanation = ns.PredictorModel?.explainCell(cell) || {};
      const confidence = Math.round((finite(explanation.confidence) || 0) * 100);
      const provenance = Array.isArray(explanation.provenance) ? explanation.provenance : [];
      const sourceLabels = provenance.slice(0, 6).map(item => String(item.source || '')).filter(Boolean);
      this.inspector.innerHTML = `
        <div class="editor-heading"><div><small>${escapeHtml(explanation.stateLabel || 'Sem previsão')}</small><h3>${fmt(explanation.petrolMs, 1)} ms · ${Math.round(finite(explanation.rpm) || 0).toLocaleString('pt-BR')} RPM</h3></div><span class="predictor-confidence">${confidence}%</span></div>
        <div class="predictor-k-pair"><div><small>K ATUAL</small><b>${explanation.currentK ?? '—'}</b></div><div><small>K ALVO</small><b>${explanation.targetK ?? '—'}</b></div></div>
        <div class="predictor-support"><small>DE ONDE VEIO</small><b>${escapeHtml(explanation.supportLabel || 'Sem previsão local')}</b></div>
        <p class="predictor-reason">${escapeHtml(explanation.reason || 'Sem suporte científico suficiente.')}</p>
        <div class="predictor-facts"><span>${escapeHtml(explanation.evidenceSummary || '0 passagens distintas · 0 leituras nativas confirmadas')}</span><span>${explanation.predicted ? 'interpolação local conservadora' : explanation.directObservation ? 'observação local direta' : 'sem previsão local'}</span></div>
        <div class="predictor-next-step"><small>PRÓXIMO PASSO</small><b>${escapeHtml(explanation.nextStep || 'Continue coletando evidência local.')}</b></div>
        <details class="predictor-provenance"><summary>Ver fontes técnicas</summary><p>${sourceLabels.length ? sourceLabels.map(escapeHtml).join(' · ') : 'Nenhuma fonte científica suficiente.'}</p></details>
        <button type="button" data-predictor-review class="primary wide" ${explanation.requiresHumanReview ? '' : 'disabled'}>${explanation.requiresHumanReview ? 'Revisar no Mapa K' : 'Ainda sem ajuste local'}</button>
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
