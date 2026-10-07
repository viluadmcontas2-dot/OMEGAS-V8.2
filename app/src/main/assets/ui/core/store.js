(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  /**
   * Erros de tela: cada falha vai para o console COM o nome de onde veio; 3 falhas seguidas na mesma tela mostram
   * um aviso tocável ("Algo falhou nesta tela; toque para recarregar"). Um ciclo do scheduler sem erro zera a conta.
   */
  const ScreenErrors = {
    LIMIT: 3,
    count: 0,
    route: null,
    note(where, error) {
      console.error(`[OMEGAS ${where}]`, error);
      const route = typeof document !== 'undefined' && document.body && document.body.dataset ? document.body.dataset.omegasRoute || '' : '';
      if (route !== this.route) { this.route = route; this.count = 0; }
      this.count += 1;
      if (this.count >= this.LIMIT) this.show();
    },
    clear() { this.count = 0; },
    show() {
      if (typeof document === 'undefined' || !document.body || typeof document.createElement !== 'function') return;
      if (document.getElementById('screenErrorBanner')) return;
      const banner = document.createElement('button');
      banner.type = 'button';
      banner.id = 'screenErrorBanner';
      banner.className = 'screen-error-banner';
      banner.setAttribute('role', 'alert');
      banner.textContent = 'Algo falhou nesta tela; toque para recarregar';
      banner.addEventListener('click', () => { try { root.location.reload(); } catch (_) { banner.remove(); } });
      document.body.appendChild(banner);
    },
  };
  ns.ScreenErrors = ScreenErrors;

  class Store {
    constructor(initial) {
      this.state = Object.freeze({ ...(initial || {}) });
      this.listeners = new Set();
    }
    get() { return this.state; }
    set(next) {
      const value = typeof next === 'function' ? next(this.state) : next;
      this.state = Object.freeze({ ...(value || {}) });
      this.emit();
      return this.state;
    }
    patch(partial) {
      const value = typeof partial === 'function' ? partial(this.state) : partial;
      if (!value || typeof value !== 'object') return this.state;
      this.state = Object.freeze({ ...this.state, ...value });
      this.emit();
      return this.state;
    }
    update(key, value) { return this.patch({ [key]: value }); }
    subscribe(listener, immediate) {
      if (typeof listener !== 'function') return () => {};
      this.listeners.add(listener);
      if (immediate) listener(this.state);
      return () => this.listeners.delete(listener);
    }
    emit() {
      this.listeners.forEach(listener => {
        try { listener(this.state); } catch (error) { ScreenErrors.note('store', error); }
      });
    }
  }

  ns.Store = Store;
  ns.createInitialState = function () {
    return {
      route: 'dashboard',
      visible: true,
      status: {},
      telemetry: {},
      obd: {},
      obdDevices: {},
      map: { state: 'idle', data: null, selection: 0, activeCell: null, review: null, operation: null },
      curve: { state: 'idle', data: null, activePoint: null, proposal: null, status: {} },
      sessionStatus: {},
      sessions: [],
      logs: [],
      equivalence: null,
      alert: null,
      identity: {},
      demo: false,
    };
  };
})(typeof window !== 'undefined' ? window : globalThis);
