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

  const { finite } = ns.DisplayRules;

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

  /**
   * UMA regra de frescor para todo número ao vivo (Agora, faixa do cabeçalho, combustível do trilho, Mapa K, Refino):
   *   fresh  até 1,5 s            · número normal
   *   late   de 1,5 s a 3 s       · número cinza + "atrasado"
   *   lost   acima de 3 s         · número vira "—" (valor velho nunca finge ser de agora) + "Sem dados há N s"
   *   none   sem leitura válida   · "—"
   * `valid=true` com a idade crescendo (ECU parou de enviar) cai em late e depois lost, nunca fica "ao vivo".
   * Idade desconhecida com valid=true: o número aparece (a ponte disse que vale), sem selo de atraso.
   * `fallback`: rota sem bombeador de quadros (Curva K, Sessões, Ferramentas) lê o status de 1 Hz (rpm, injeção, combustível e idade).
   */
  function read(state, options) {
    const telemetry = (state && state.telemetry) || {};
    const status = (state && state.status) || {};
    let live = telemetry.live || telemetry.data || telemetry;
    let valid = telemetry.valid === true;
    let ageMs = finite(telemetry.telemetryAgeMs ?? telemetry.ageMs);
    if (!valid && options && options.fallback === true && status.usbConnected === true) {
      const statusAge = finite(status.directTelemetryAgeMs);
      if (statusAge !== null && statusAge >= 0 && finite(status.rpm) !== null) {
        live = { rpm: status.rpm, petrol_ms: status.petrolMs, load_bar: status.mapBar, fuel: status.fuelState };
        valid = true;
        ageMs = statusAge;
      }
    }
    // Idade negativa = a ponte diz que nunca chegou quadro: nenhuma leitura vale, mesmo com valid=true.
    if (valid && ageMs !== null && ageMs < 0) valid = false;
    if (valid && ageMs === null) {
      const statusAge = finite(status.directTelemetryAgeMs);
      if (statusAge !== null && statusAge >= 0) ageMs = statusAge;
    }
    const known = ageMs !== null && ageMs >= 0;
    let level = 'none';
    if (valid) level = !known ? 'fresh' : ageMs > STALE_MS ? 'lost' : ageMs > GREY_MS ? 'late' : 'fresh';
    const show = level === 'fresh' || level === 'late';
    return {
      valid, ageMs: known ? ageMs : null, level,
      ageUnknown: valid && !known,
      rpm: show ? finite(live.rpm) : null,
      petrolMs: show ? finite(live.petrol_ms ?? live.petrolMs) : null,
      mapBar: show ? finite(live.load_bar ?? live.map_bar ?? live.mapBar) : null,
      fuel: show ? String(live.fuel || live.state || '') : '',
      levelRaw: show ? finite(live.level_raw ?? live.levelRaw) : null,
      grey: level === 'late' || level === 'lost',
      stale: level === 'lost',
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
