export class NextScheduler {
  #hooks = new Map();
  #running = false;
  #rafId = 0;
  #lastAt = 0;

  addHook(id, callback, minIntervalMs = 0) {
    if (!id || typeof callback !== 'function') throw new TypeError('hook NEXT inválido');
    if (this.#hooks.has(id)) throw new Error(`Hook duplicado: ${id}`);
    this.#hooks.set(id, { callback, minIntervalMs: Math.max(0, minIntervalMs), lastRunAt: 0 });
    return () => this.#hooks.delete(id);
  }

  start() {
    if (this.#running) return;
    this.#running = true;
    this.#rafId = requestAnimationFrame((time) => this.#tick(time));
  }

  stop() {
    if (!this.#running) return;
    this.#running = false;
    cancelAnimationFrame(this.#rafId);
    this.#rafId = 0;
  }

  hookCount() {
    return this.#hooks.size;
  }

  debugSnapshot() {
    return Object.freeze({
      running: this.#running,
      hookCount: this.#hooks.size,
      lastAt: this.#lastAt,
      hooks: [...this.#hooks.keys()],
    });
  }

  #tick(time) {
    if (!this.#running) return;
    this.#lastAt = time;
    for (const hook of this.#hooks.values()) {
      if (time - hook.lastRunAt < hook.minIntervalMs) continue;
      hook.lastRunAt = time;
      try {
        hook.callback(time);
      } catch (error) {
        console.error('NEXT scheduler hook falhou', error);
      }
    }
    this.#rafId = requestAnimationFrame((nextTime) => this.#tick(nextTime));
  }
}

export const scheduler = new NextScheduler();
