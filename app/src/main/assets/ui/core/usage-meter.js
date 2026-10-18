(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.OmegasUsageMeter = api;
  if (typeof document !== 'undefined' && root.OmegasUi) root.OmegasUi.UsageMeter = api;
  if (typeof document !== 'undefined') api.boot(root);
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  /**
   * Medidor de uso (dono, 2026-10-13: "benchmarking de áreas e botões usados, com minúcia"). Conta, no próprio aparelho:
   *   · por aba: quantas visitas e quantos segundos ficou aberta;
   *   · por controle: quantos toques (botão, aba, ponto do gráfico, célula do mapa), identificado pelo id/atributo/rótulo.
   * Nada de rede, nada de valor digitado, nada de dado da ECU: só contagens. Vai para o log da sessão (evento `ui_usage`,
   * deltas desde o último envio), que já viaja com a sessão para o Drive. Serve para decidir o que podar e, no futuro,
   * para análise do produto. Não cria timer: o envio pega carona nos ganchos do agendador e nos eventos de visibilidade.
   */
  const MAX_KEYS = 200;

  const slug = text => String(text || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 32);

  /** Chave estável de um controle: data-route, id, 1º atributo data-*, ou o rótulo. Sem texto de entrada do usuário. */
  function controlKey(element) {
    if (!element) return null;
    const route = element.closest?.('[data-route]');
    if (route) return 'aba:' + route.dataset.route;
    const target = element.closest?.('button, summary, [data-curve-dot], [data-band], .map-k-cell, .ao-point, .ao-hit, [role="button"], label, select');
    if (!target) return element.closest?.('svg') ? 'grafico' : null;
    if (target.id) return 'id:' + target.id;
    if (target.matches?.('.map-k-cell')) return 'mapa:celula';
    if (target.matches?.('[data-curve-dot]')) return 'curva:ponto';
    if (target.matches?.('.ao-point, .ao-hit')) return 'grafico:ponto';
    for (const attr of target.getAttributeNames?.() || []) if (attr.startsWith('data-') && attr !== 'data-state' && attr !== 'data-kind') return attr;
    const label = slug(target.textContent);
    return label ? 'rotulo:' + label : null;
  }

  class Meter {
    constructor(now = () => Date.now()) {
      this.now = now;
      this.reset();
      this.startedAt = now();
    }
    reset() {
      this.routes = {};
      this.taps = {};
      this.lastFlushAt = this.now();
    }
    tap(key) {
      if (!key) return;
      if (!(key in this.taps) && Object.keys(this.taps).length >= MAX_KEYS) return;
      this.taps[key] = (this.taps[key] || 0) + 1;
    }
    /** Troca de aba: fecha o tempo da anterior e abre a próxima. */
    route(name, at = this.now()) {
      if (this.current) {
        const row = this.routes[this.current.name] || (this.routes[this.current.name] = { visits: 0, ms: 0 });
        row.ms += Math.max(0, at - this.current.at);
      }
      if (name) {
        const row = this.routes[name] || (this.routes[name] = { visits: 0, ms: 0 });
        row.visits += 1;
      }
      this.current = name ? { name, at } : null;
    }
    /** Deltas desde o último envio (inclui o tempo corrido da aba aberta agora). */
    delta(at = this.now()) {
      const routes = {};
      for (const [name, row] of Object.entries(this.routes)) routes[name] = { visits: row.visits, seconds: Math.round(row.ms / 1000) };
      if (this.current) {
        const row = routes[this.current.name] || (routes[this.current.name] = { visits: 0, seconds: 0 });
        row.seconds += Math.round(Math.max(0, at - this.current.at) / 1000);
      }
      return { routes, taps: { ...this.taps }, windowSeconds: Math.round((at - this.lastFlushAt) / 1000) };
    }
    empty(delta) { return !Object.keys(delta.taps).length && !Object.values(delta.routes).some(r => r.visits || r.seconds); }
    /** Envia só se houve uso; zera depois. `send` devolve true quando a ponte aceitou. */
    flush(send, at = this.now()) {
      const delta = this.delta(at);
      if (this.empty(delta)) { this.lastFlushAt = at; return false; }
      const ok = send(delta) !== false;
      if (ok) {
        this.routes = {};
        this.taps = {};
        if (this.current) this.current.at = at;
        this.lastFlushAt = at;
      }
      return ok;
    }
  }

  function boot(root) {
    if (!root.document || root.OmegasUsageMeterBooted) return;
    const attach = () => {
      const app = root.OmegasApp;
      if (!app?.store || !app?.scheduler) { root.addEventListener?.('omegas-app-ready', attach, { once: true }); return; }
      root.OmegasUsageMeterBooted = true;
      const meter = new Meter();
      app.usage = meter;
      const send = delta => {
        const result = app.api?.recordUiUsage?.(delta);
        return result === undefined ? true : result?.ok !== false;
      };
      let last = null;
      app.store.subscribe(state => {
        if (state.route === last) return;
        last = state.route;
        meter.route(state.route);
        meter.flush(send);
      }, true);
      root.document.addEventListener('click', event => { meter.tap(controlKey(event.target)); meter.flush(send); }, true);
      root.document.addEventListener('visibilitychange', () => { if (root.document.hidden) meter.flush(send); });
    };
    attach();
  }

  return { Meter, controlKey, slug, boot };
});
