(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.OmegasStallRegion = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  /**
   * Tropeços e apagões do GNV → células do Mapa K (dono, 2026-10-13). O detector (StallWatch.kt) só LÊ o que a ECU manda e
   * registra ONDE o motor engasgou (Petrol Inj., MAP, RPM). Aqui só se decide QUAIS células do Mapa K propor e QUANTO:
   *  - lugar: a faixa de Petrol Inj. com mais ocorrências (apagão pesa 2, tropeço pesa 1), exigindo MIN_EVENTS;
   *  - células: as linhas do Mapa K que cobrem a faixa × as colunas de RPM que cobrem o RPM de antes (no máximo 3 colunas);
   *  - quanto: um passo pequeno (+5%; +10% com muita evidência), nunca acima do passo máximo do ajuste local (15%).
   * Só propõe: gravar é o fluxo manual do Mapa K (prévia, confirmação, ACK e leitura de volta). Direção = mais GNV, porque o
   * engasgo vem logo após o corte/desaceleração, onde o GNV mede 12–30% mais magro (docs/evidence/2026-10-13-*).
   */
  const MIN_EVENTS = 3;
  const STEP_PERCENT = 5;
  const STRONG_PERCENT = 10;
  const STRONG_EVENTS = 6;
  const MAX_PERCENT = 15;
  const MAX_COLUMNS = 3;
  const finite = value => value === null || value === undefined || value === '' || !Number.isFinite(Number(value)) ? null : Number(value);

  /** Linhas/colunas que cobrem [lo, hi] (a de baixo até a de cima), como LocalMapPlanner.rowsCovering no Kotlin. */
  function covering(bins, lo, hi) {
    if (!Array.isArray(bins) || !bins.length) return [];
    const a = Math.min(lo, hi);
    const b = Math.max(lo, hi);
    let first = 0;
    bins.forEach((v, i) => { if (v <= a) first = i; });
    let last = bins.length - 1;
    for (let i = 0; i < bins.length; i += 1) if (bins[i] >= b) { last = i; break; }
    const out = [];
    for (let i = first; i <= Math.max(first, last); i += 1) out.push(i);
    return out;
  }

  /** Junta apagões (peso 2) e tropeços (peso 1) por faixa de Petrol Inj. e devolve a mais forte. */
  function topRegion(stalls) {
    const bands = new Map();
    const add = (list, weight, kind) => (Array.isArray(list) ? list : []).forEach(r => {
      const from = finite(r.fromMs);
      const count = finite(r.count);
      if (from === null || !count) return;
      const key = from.toFixed(2);
      const cur = bands.get(key) || { fromMs: from, toMs: finite(r.toMs) ?? from + 0.5, weight: 0, stalls: 0, near: 0, afterCutoff: 0, mapBars: [], rpmLow: Infinity, rpmHigh: -Infinity };
      cur.weight += weight * count;
      cur[kind] += count;
      if (kind === 'near') cur.afterCutoff += finite(r.afterCutoff) || 0;
      if (finite(r.mapBar) !== null) cur.mapBars.push(finite(r.mapBar));
      const lo = finite(r.rpmLow) ?? finite(r.rpmBefore);
      const hi = finite(r.rpmHigh) ?? finite(r.rpmBefore);
      if (lo !== null) cur.rpmLow = Math.min(cur.rpmLow, lo);
      if (hi !== null) cur.rpmHigh = Math.max(cur.rpmHigh, hi);
      bands.set(key, cur);
    });
    add(stalls?.regions, 2, 'stalls');
    add(stalls?.nearRegions, 1, 'near');
    return [...bands.values()].sort((x, y) => y.weight - x.weight)[0] || null;
  }

  /**
   * @param stalls  eq.stalls do Kotlin ({regions, nearRegions, ...})
   * @param axes    {petrolBins, rpmBins} do Mapa K lido AGORA da ECU (nada fixo)
   */
  function suggest(stalls, axes = {}, { minEvents = MIN_EVENTS } = {}) {
    const region = topRegion(stalls);
    if (!region) return { ok: false, reason: 'Sem tropeços nem apagões registrados.' };
    const events = region.stalls + region.near;
    if (events < minEvents) return { ok: false, reason: `Só ${events} ocorrência${events === 1 ? '' : 's'} nesta faixa: preciso de ${minEvents} para propor.`, region };
    const rows = covering(axes.petrolBins, region.fromMs, region.toMs);
    let columns = Number.isFinite(region.rpmLow) && Number.isFinite(region.rpmHigh) ? covering(axes.rpmBins, region.rpmLow, region.rpmHigh) : [];
    if (!rows.length || !columns.length) return { ok: false, reason: 'O Mapa K ainda não foi lido: não sei quais células.', region };
    if (columns.length > MAX_COLUMNS) {
      // Muito espalhado em RPM: fica com as colunas mais perto do meio do que aconteceu.
      const mid = (region.rpmLow + region.rpmHigh) / 2;
      columns = columns.slice().sort((p, q) => Math.abs(axes.rpmBins[p] - mid) - Math.abs(axes.rpmBins[q] - mid)).slice(0, MAX_COLUMNS).sort((p, q) => p - q);
    }
    const percent = Math.min(MAX_PERCENT, region.weight >= STRONG_EVENTS ? STRONG_PERCENT : STEP_PERCENT);
    const cells = [];
    rows.forEach(row => columns.forEach(column => cells.push({ row, column, deltaPercent: percent })));
    return {
      ok: true, percent, cells, events, region: { ...region, mapBar: region.mapBars.length ? region.mapBars.reduce((a, b) => a + b, 0) / region.mapBars.length : null },
      reason: `${events} ocorrência${events === 1 ? '' : 's'} entre ${region.fromMs.toFixed(1).replace('.', ',')} e ${region.toMs.toFixed(1).replace('.', ',')} ms`,
    };
  }

  return { suggest, topRegion, covering, MIN_EVENTS, STEP_PERCENT, STRONG_PERCENT, MAX_PERCENT, MAX_COLUMNS };
});
