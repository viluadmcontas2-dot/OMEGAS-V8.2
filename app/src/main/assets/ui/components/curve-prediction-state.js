(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  function finite(value) {
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  }

  class CurvePredictionState {
    constructor(app) {
      this.app = app;
      this.store = app.store;
      this.activeIndex = 0;
      this.inject();
      this.bind();
      this.unsubscribe = this.store.subscribe(state => this.render(state), true);
    }

    inject() {
      const heading = document.querySelector('[data-screen="curve"] .curve-editor-panel .editor-heading');
      if (!heading || document.getElementById('curvePredictionState')) return;
      const badge = document.createElement('span');
      badge.id = 'curvePredictionState';
      badge.className = 'curve-prediction-state';
      badge.textContent = 'SEM PREVISÃO';
      heading.appendChild(badge);
      this.badge = badge;
      if (!document.querySelector('link[data-curve-prediction-style]')) {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = 'styles-curve-prediction.css';
        link.dataset.curvePredictionStyle = 'true';
        document.head.appendChild(link);
      }
    }

    bind() {
      const screen = document.querySelector('[data-screen="curve"]');
      screen?.addEventListener('click', event => {
        const point = event.target.closest('[data-curve-index], [data-learning-curve-index]');
        if (!point) return;
        const index = Number(point.dataset.curveIndex ?? point.dataset.learningCurveIndex);
        if (Number.isInteger(index) && index >= 0) {
          this.activeIndex = index;
          this.render(this.store.get());
        }
      });
    }

    render(state) {
      if (!this.badge) this.badge = document.getElementById('curvePredictionState');
      if (!this.badge) return;
      const advisor = state.learning?.assistedCalibration || state.learning?.assisted_calibration || {};
      const suggestions = Array.isArray(advisor.kFactorSuggestions) ? advisor.kFactorSuggestions : [];
      const advice = suggestions.find(item => Number(item.index) === this.activeIndex) || null;
      const calibrationItems = Array.isArray(state.calibrationState?.suggestionItems) ? state.calibrationState.suggestionItems : [];
      const exact = calibrationItems.find(item => item?.target === 'CURVE_K' && Array.isArray(item.curveChanges) && item.curveChanges.some(change => Number(change.index) === this.activeIndex));
      const exactChange = exact?.curveChanges?.find(change => Number(change.index) === this.activeIndex);
      const target = finite(exactChange?.after);
      const error = finite(advice?.errorPercent ?? advice?.error_percent ?? advice?.relativeErrorPercent);
      const actionable = advice?.actionable === true && target !== null;
      const stateName = actionable ? 'PREVISAO_OMEGAS' : error !== null ? 'OBSERVADO_SEM_PREVISAO' : 'SEM_PREVISAO';
      this.badge.dataset.state = stateName;
      this.badge.textContent = stateName === 'PREVISAO_OMEGAS'
        ? 'PREVISÃO OMEGAS'
        : stateName === 'OBSERVADO_SEM_PREVISAO'
          ? 'OBSERVADO · SEM PREVISÃO'
          : 'SEM PREVISÃO';
      this.badge.title = actionable
        ? 'Existe alvo K exato registrado; ainda exige revisão humana.'
        : error !== null
          ? 'Existe evidência global, mas ainda não há alvo K exato revisável.'
          : 'Ainda não existe suporte científico para prever ajuste neste ponto.';
    }
  }

  function boot() {
    const app = root.OmegasApp;
    if (!app?.store || !document.querySelector('[data-screen="curve"]')) {
      root.setTimeout(boot, 25);
      return;
    }
    if (app.curvePredictionState) return;
    app.curvePredictionState = new CurvePredictionState(app);
  }

  ns.CurvePredictionState = CurvePredictionState;
  boot();
})(typeof window !== 'undefined' ? window : globalThis);
