export class NextScheduler {
  #hooks = new Map();
  #running = false;
  #rafId = 0;
  #lastAt = 0;
  #previousFrameAt = 0;
  #pressureScore = 0;
  #pressure = 'NORMAL';

  addHook(id, callback, minIntervalMs = 0) {
    if (!id || typeof callback !== 'function') throw new TypeError('hook NEXT inválido');
    if (this.#hooks.has(id)) throw new Error(`Hook duplicado: ${id}`);
    this.#hooks.set(id, { callback, minIntervalMs: Math.max(0, minIntervalMs), lastRunAt: 0 });
    return () => this.#hooks.delete(id);
  }

  start() {
    if (this.#running) return;
    this.#running = true;
    this.#previousFrameAt = 0;
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

  pressure() {
    return this.#pressure;
  }

  debugSnapshot() {
    return Object.freeze({
      running: this.#running,
      hookCount: this.#hooks.size,
      lastAt: this.#lastAt,
      pressure: this.#pressure,
      pressureScore: this.#pressureScore,
      hooks: [...this.#hooks.keys()],
    });
  }

  #tick(time) {
    if (!this.#running) return;
    if (this.#previousFrameAt > 0) {
      const frameGap = time - this.#previousFrameAt;
      if (frameGap > 80) this.#pressureScore = Math.min(10, this.#pressureScore + 2);
      else if (frameGap > 48) this.#pressureScore = Math.min(10, this.#pressureScore + 1);
      else if (frameGap < 36) this.#pressureScore = Math.max(0, this.#pressureScore - 1);
      this.#pressure = this.#pressureScore >= 3 ? 'REDUCED' : 'NORMAL';
    }
    this.#previousFrameAt = time;
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
