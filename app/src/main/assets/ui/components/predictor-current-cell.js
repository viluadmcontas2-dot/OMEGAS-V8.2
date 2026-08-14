(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  class PredictorCurrentCell {
    constructor(app) {
      this.app = app;
      this.currentKey = '';
      this.injectStyle();
      this.unsubscribe = app.store.subscribe(state => this.render(state), true);
    }

    injectStyle() {
      if (document.querySelector('link[data-predictor-live-style]')) return;
      const link = document.createElement('link');
      link.rel = 'stylesheet';
      link.href = 'styles-predictor-live.css';
      link.dataset.predictorLiveStyle = 'true';
      document.head.appendChild(link);
    }

    render(state) {
      if (state.route !== 'predictor') {
        this.setCurrent('');
        return;
      }
      const interpolation = state.telemetry?.interpolation || {};
      const cell = interpolation.cell || {};
      const decision = state.learningDecision || {};
      const row = Number(cell.row ?? decision.cell_row);
      const column = Number(cell.column ?? decision.cell_column);
      const valid = Number.isInteger(row) && Number.isInteger(column) && row >= 0 && column >= 0;
      this.setCurrent(valid ? `${row}:${column}` : '');
    }

    setCurrent(nextKey) {
      if (nextKey === this.currentKey) return;
      if (this.currentKey) {
        document.querySelector(`[data-predictor-cell="${this.currentKey}"]`)?.classList.remove('current');
      }
      this.currentKey = nextKey;
      if (this.currentKey) {
        document.querySelector(`[data-predictor-cell="${this.currentKey}"]`)?.classList.add('current');
      }
    }
  }

  function boot() {
    const app = root.OmegasApp;
    if (!app?.store || !document.querySelector('[data-screen="predictor"]')) {
      root.setTimeout(boot, 25);
      return;
    }
    if (app.predictorCurrentCell) return;
    app.predictorCurrentCell = new PredictorCurrentCell(app);
  }

  ns.PredictorCurrentCell = PredictorCurrentCell;
  boot();
})(typeof window !== 'undefined' ? window : globalThis);
