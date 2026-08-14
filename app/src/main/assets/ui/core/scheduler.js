(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  class Scheduler {
    constructor(options) {
      const opts = options || {};
      this.intervalMs = Math.max(100, Number(opts.intervalMs) || 200);
      this.onFast = opts.onFast || null;
      this.onStatus = opts.onStatus || null;
      this.onContext = opts.onContext || null;
      this.hooks = { fast: new Set(), status: new Set(), context: new Set() };
      this.timer = null;
      this.tick = 0;
      this.running = false;
    }
    addHook(cadence, listener) {
      const set = this.hooks[cadence];
      if (!set || typeof listener !== 'function') return () => {};
      set.add(listener);
      return () => set.delete(listener);
    }
    start() {
      if (this.timer) return;
      this.running = true;
      this.run();
      this.timer = root.setInterval(() => this.run(), this.intervalMs);
    }
    stop() {
      if (this.timer) root.clearInterval(this.timer);
      this.timer = null;
      this.running = false;
    }
    emitHooks(cadence) {
      const set = this.hooks[cadence];
      if (!set) return;
      set.forEach(listener => {
        try { listener(this.tick); } catch (error) { console.error(`[OMEGAS scheduler ${cadence} hook]`, error); }
      });
    }
    run() {
      this.tick += 1;
      try { if (typeof this.onFast === 'function') this.onFast(this.tick); } catch (error) { console.error('[OMEGAS scheduler fast]', error); }
      this.emitHooks('fast');
      if (this.tick === 1 || this.tick % 5 === 0) {
        try { if (typeof this.onStatus === 'function') this.onStatus(this.tick); } catch (error) { console.error('[OMEGAS scheduler status]', error); }
        this.emitHooks('status');
      }
      if (this.tick === 1 || this.tick % 10 === 0) {
        try { if (typeof this.onContext === 'function') this.onContext(this.tick); } catch (error) { console.error('[OMEGAS scheduler context]', error); }
        this.emitHooks('context');
      }
    }
  }

  ns.Scheduler = Scheduler;
})(typeof window !== 'undefined' ? window : globalThis);
