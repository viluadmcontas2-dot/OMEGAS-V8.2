(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  const STORAGE_KEY = 'omegas-live-tracing-enabled';

  class PredictorCurrentCell {
    constructor(app) {
      this.app = app;
      this.store = app.store;
      this.api = app.api;
      this.scheduler = app.scheduler;
      this.currentKey = '';
      this.traceKeys = new Set();
      this.lastSequence = -1;
      this.injectStyle();
      this.injectToggle();
      const enabled = this.restoreEnabled();
      this.store.patch({ liveTracingEnabled: enabled });
      this.unsubscribeStore = this.store.subscribe(state => this.render(state), true);
      this.unsubscribeFast = this.scheduler.addHook('fast', () => this.refreshFast());
    }

    injectStyle() {
      if (document.querySelector('link[data-predictor-live-style]')) return;
      const link = document.createElement('link');
      link.rel = 'stylesheet';
      link.href = 'styles-predictor-live.css';
      link.dataset.predictorLiveStyle = 'true';
      document.head.appendChild(link);
    }

    injectToggle() {
      const summary = document.querySelector('[data-screen="predictor"] .predictor-state-summary');
      if (!summary || document.getElementById('predictorLiveTraceToggle')) return;
      const button = document.createElement('button');
      button.id = 'predictorLiveTraceToggle';
      button.type = 'button';
      button.className = 'predictor-live-toggle';
      button.addEventListener('click', () => {
        const enabled = this.store.get().liveTracingEnabled !== false;
        const next = !enabled;
        this.persistEnabled(next);
        this.store.patch({ liveTracingEnabled: next });
      });
      summary.appendChild(button);
      this.toggle = button;
    }

    refreshFast() {
      const state = this.store.get();
      if (state.route !== 'predictor' || state.liveTracingEnabled === false) return;
      if (!this.api || typeof this.api.telemetry !== 'function') return;
      const latest = this.api.telemetry() || {};
      const interpolation = latest.interpolation || {};
      const sequence = Number(interpolation.sequence ?? latest.sequence ?? latest.updatedAt ?? -1);
      if (Number.isFinite(sequence) && sequence === this.lastSequence) return;
      if (Number.isFinite(sequence)) this.lastSequence = sequence;
      this.store.patch({ telemetry: latest });
    }

    render(state) {
      const enabled = state.liveTracingEnabled !== false;
      this.renderToggle(enabled);
      if (state.route !== 'predictor' || !enabled) {
        this.clearTrace();
        return;
      }
      const interpolation = state.telemetry?.interpolation || {};
      if (interpolation.valid !== true || interpolation.affectsLearning !== false || interpolation.affectsCalibration !== false) {
        this.clearTrace();
        return;
      }
      const cell = interpolation.cell || {};
      const row = Number(cell.row);
      const column = Number(cell.column);
      const weights = Array.isArray(cell.continuousWeights) ? cell.continuousWeights : [];
      if (!Number.isInteger(row) || !Number.isInteger(column) || row < 0 || column < 0 || !weights.length) {
        this.clearTrace();
        return;
      }
      this.setTrace(`${row}:${column}`, weights);
    }

    setTrace(currentKey, weights) {
      this.traceKeys.forEach(key => {
        const node = document.querySelector(`[data-predictor-cell="${key}"]`);
        node?.classList.remove('trace-weight');
        node?.style.removeProperty('--trace-weight');
      });
      this.traceKeys.clear();
      if (this.currentKey && this.currentKey !== currentKey) {
        document.querySelector(`[data-predictor-cell="${this.currentKey}"]`)?.classList.remove('current');
      }

      weights.slice(0, 4).forEach(item => {
        const row = Number(item?.row);
        const column = Number(item?.column);
        const weight = Number(item?.weight);
        if (!Number.isInteger(row) || !Number.isInteger(column) || !Number.isFinite(weight) || weight <= 0) return;
        const key = `${row}:${column}`;
        const node = document.querySelector(`[data-predictor-cell="${key}"]`);
        if (!node) return;
        node.classList.add('trace-weight');
        node.style.setProperty('--trace-weight', String(Math.max(0, Math.min(1, weight))));
        this.traceKeys.add(key);
      });

      this.currentKey = currentKey;
      document.querySelector(`[data-predictor-cell="${currentKey}"]`)?.classList.add('current');
    }

    clearTrace() {
      if (this.currentKey) {
        document.querySelector(`[data-predictor-cell="${this.currentKey}"]`)?.classList.remove('current');
      }
      this.currentKey = '';
      this.traceKeys.forEach(key => {
        const node = document.querySelector(`[data-predictor-cell="${key}"]`);
        node?.classList.remove('trace-weight');
        node?.style.removeProperty('--trace-weight');
      });
      this.traceKeys.clear();
    }

    renderToggle(enabled) {
      if (!this.toggle) this.toggle = document.getElementById('predictorLiveTraceToggle');
      if (!this.toggle) return;
      this.toggle.dataset.enabled = enabled ? 'true' : 'false';
      this.toggle.textContent = enabled ? 'Tracing ao vivo · ON' : 'Tracing ao vivo · OFF';
      this.toggle.setAttribute('aria-pressed', enabled ? 'true' : 'false');
      this.toggle.title = enabled
        ? 'Desliga somente o destaque visual. O Learning continua coletando normalmente.'
        : 'Liga somente o destaque visual. O Learning não depende deste controle.';
    }

    restoreEnabled() {
      try { return root.localStorage.getItem(STORAGE_KEY) !== 'false'; }
      catch (_) { return true; }
    }

    persistEnabled(enabled) {
      try { root.localStorage.setItem(STORAGE_KEY, enabled ? 'true' : 'false'); }
      catch (_) {}
    }
  }

  function boot() {
    const app = root.OmegasApp;
    if (!app?.store || !app?.scheduler || !document.querySelector('[data-screen="predictor"]')) {
      root.setTimeout(boot, 25);
      return;
    }
    if (app.predictorCurrentCell) return;
    app.predictorCurrentCell = new PredictorCurrentCell(app);
  }

  ns.PredictorCurrentCell = PredictorCurrentCell;
  boot();
})(typeof window !== 'undefined' ? window : globalThis);
