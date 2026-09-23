(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  class Scheduler {
    constructor(options) {
      const opts = options || {};
      this.intervalMs = Math.max(50, Number(opts.intervalMs) || 200);
      this.statusIntervalMs = Math.max(this.intervalMs, Number(opts.statusIntervalMs) || 1_000);
      this.contextIntervalMs = Math.max(this.intervalMs, Number(opts.contextIntervalMs) || 2_000);
      this.statusElapsedMs = 0;
      this.contextElapsedMs = 0;
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
    armTimer() {
      if (!this.running || this.timer) return;
      this.timer = root.setInterval(() => this.run(), this.intervalMs);
    }
    setCadenceMs(intervalMs) {
      const next = Math.max(50, Number(intervalMs) || 200);
      if (next === this.intervalMs) return this.intervalMs;
      const wasRunning = !!this.timer;
      if (wasRunning) root.clearInterval(this.timer);
      this.timer = null;
      this.intervalMs = next;
      this.statusElapsedMs = 0;
      this.contextElapsedMs = 0;
      if (wasRunning) this.armTimer();
      return this.intervalMs;
    }
    start() {
      if (this.timer) return;
      this.running = true;
      this.statusElapsedMs = 0;
      this.contextElapsedMs = 0;
      this.run();
      this.armTimer();
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
      this.statusElapsedMs += this.intervalMs;
      this.contextElapsedMs += this.intervalMs;
      if (this.tick === 1 || this.statusElapsedMs >= this.statusIntervalMs) {
        this.statusElapsedMs = 0;
        try { if (typeof this.onStatus === 'function') this.onStatus(this.tick); } catch (error) { console.error('[OMEGAS scheduler status]', error); }
        this.emitHooks('status');
      }
      if (this.tick === 1 || this.contextElapsedMs >= this.contextIntervalMs) {
        this.contextElapsedMs = 0;
        try { if (typeof this.onContext === 'function') this.onContext(this.tick); } catch (error) { console.error('[OMEGAS scheduler context]', error); }
        this.emitHooks('context');
      }
    }
  }

  ns.Scheduler = Scheduler;
})(typeof window !== 'undefined' ? window : globalThis);
