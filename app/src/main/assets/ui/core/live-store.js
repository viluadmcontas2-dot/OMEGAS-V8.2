(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // LiveStore: a ÚNICA leitura viva do motor para Agora, AutoCal e Refino. O app.js tem um só bombeador do quadro
  // (presentSnapshot) e guarda em store.telemetry; aqui ficam as regras de idade (cinza em 1,5 s, some em 3 s) e o
  // cursor leve que anda no quadro de animação só com CSS transform (sem layout, sem texto por quadro).
  const GREY_MS = 1500;
  const STALE_MS = 3000;
  const EASE_MS = 50;          // constante de tempo: ~150 ms até chegar ao alvo
  const NARRATIVE_MS = 500;    // texto do cursor: no máximo 2 Hz, ou na hora quando muda região/combustível

  function finite(value) {
    if (value === null || value === undefined || value === '' || typeof value === 'boolean') return null;
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  }

  /** Ponto vivo (Petrol Inj. × MAP) ou null quando o quadro está vencido/ausente. grey = atrasado (> 1,5 s). */
  function point(telemetry) {
    const source = telemetry || {};
    const ageMs = finite(source.telemetryAgeMs ?? source.ageMs);
    if (source.valid !== true || ageMs === null || ageMs < 0 || ageMs > STALE_MS) return null;
    const live = source.live || source.data || source;
    const petrolMs = finite(live.petrol_ms ?? live.petrolMs);
    const mapBar = finite(live.load_bar ?? live.map_bar ?? live.mapBar);
    const rpm = finite(live.rpm);
    if (petrolMs === null || mapBar === null) return null;
    return { petrolMs, mapBar, rpm, fuel: String(live.fuel || live.state || '—'), sequence: finite(source.sequence), ageMs, grey: ageMs > GREY_MS };
  }

  /** Leitura para os números grandes: valores + se estão atrasados. Desconhecido é null (a tela mostra "—"). */
  function read(state) {
    const telemetry = (state && state.telemetry) || {};
    const live = telemetry.live || telemetry.data || telemetry;
    const ageMs = finite(telemetry.telemetryAgeMs ?? telemetry.ageMs);
    const valid = telemetry.valid === true;
    return {
      valid, ageMs,
      rpm: valid ? finite(live.rpm) : null,
      petrolMs: valid ? finite(live.petrol_ms ?? live.petrolMs) : null,
      mapBar: valid ? finite(live.load_bar ?? live.map_bar ?? live.mapBar) : null,
      fuel: valid ? String(live.fuel || live.state || '') : '',
      grey: valid && ageMs !== null && ageMs > GREY_MS,
      stale: valid && ageMs !== null && ageMs > STALE_MS,
    };
  }

  /**
   * Cursor suave: recebe alvos (px do gráfico) e, a cada quadro, aproxima a posição e move a camada com
   * `style.transform`. Só toca o DOM quando algo mudou; rótulo/texto são decididos por quem chama (≤ 2 Hz).
   */
  class EaseCursor {
    constructor(getLayer) {
      this.getLayer = getLayer;
      this.pos = null;
      this.target = null;
      this.frameAt = null;
      this.painted = '';
      this.paintedLayer = null;
      this.flip = '';
    }
    setTarget(x, y, scale, outOfRange) {
      this.target = { x, y, scale, outOfRange: outOfRange === true };
      if (!this.pos) this.pos = { x, y };
    }
    clear() { this.target = null; this.pos = null; this.frameAt = null; this.painted = ''; this.paintedLayer = null; }
    /** Âncora do rótulo: vira para a esquerda/abaixo perto das bordas (só mexe no DOM quando essa escolha muda). */
    placeLabel(layer, pos, scale) {
      const label = layer.querySelector('[data-autocal-live-label]');
      if (!label || !scale) return;
      const left = pos.x > scale.xFor(scale.xMax) - 184;
      const below = pos.y < scale.yFor(scale.yMax) + 36;
      const key = `${left}|${below}`;
      if (key === this.flip) return;
      this.flip = key;
      label.setAttribute('x', left ? '-12' : '12');
      label.setAttribute('y', below ? '24' : '-12');
      label.setAttribute('text-anchor', left ? 'end' : 'start');
    }
    paint() {
      const layer = this.getLayer();
      const pos = this.pos;
      if (!layer || !pos || !this.target) return;
      const value = `translate(${pos.x.toFixed(1)}px, ${pos.y.toFixed(1)}px)`;
      if (value === this.painted && layer === this.paintedLayer) return;
      this.painted = value;
      this.paintedLayer = layer;
      if (layer !== this.flipLayer) { this.flipLayer = layer; this.flip = ''; }
      layer.style.transform = value;
      this.placeLabel(layer, pos, this.target.scale);
    }
    /** Quadro de animação: devolve true se ainda está andando. */
    frame(timestamp) {
      const target = this.target;
      const pos = this.pos;
      if (!target || !pos) { this.frameAt = null; return false; }
      const dt = this.frameAt === null ? 16 : Math.max(0, Math.min(100, timestamp - this.frameAt));
      this.frameAt = timestamp;
      const dx = target.x - pos.x;
      const dy = target.y - pos.y;
      if (Math.abs(dx) < 0.05 && Math.abs(dy) < 0.05) {
        pos.x = target.x; pos.y = target.y;
        this.paint();
        return false;
      }
      const k = 1 - Math.exp(-dt / EASE_MS);
      pos.x += dx * k;
      pos.y += dy * k;
      this.paint();
      return true;
    }
  }

  ns.LiveStore = { GREY_MS, STALE_MS, EASE_MS, NARRATIVE_MS, point, read, EaseCursor };
})(typeof window !== 'undefined' ? window : globalThis);
