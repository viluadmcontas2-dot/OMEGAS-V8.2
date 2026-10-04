(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Gráfico único da "curva de aquisição" (Petrol Inj. × MAP) usado pelo AutoCal e pelo Refino.
  // Um só desenho, uma só legenda, um só modelo de dados e UM nó compartilhado: ele só é redesenhado quando a
  // assinatura da EVIDÊNCIA muda (D2). As telas apenas montam o nó no seu quadro. O cursor AGORA é uma camada
  // fina que cada tela move no próprio quadro de animação, sem redesenhar nada.

  // Utilitários únicos (core/display-rules.js, carregado antes).
  const { finite, escapeHtml: esc, number } = ns.DisplayRules;
  const tick = (v, digits) => number(v, digits);

  /** No máximo 4 séries semânticas (+ AGORA), todas com nome humano. "Motor apagou" só aparece quando houve. */
  const LEGEND = [
    { key: 'petrol', label: 'Curva da gasolina' },
    { key: 'gas', label: 'Curva do GNV hoje' },
    { key: 'proposal', label: 'Proposta' },
    { key: 'live', label: 'Agora' },
  ];
  const STALL_LEGEND = { key: 'stall', label: 'Motor apagou' };
  // Refino: as bolinhas da ECU e as nossas (entre elas) têm o mesmo tamanho e as cores de cada combustível.
  const BETWEEN_LEGEND = [
    { key: 'ecudot', label: 'Pontos da ECU' },
    { key: 'ourdot', label: 'Pontos do OMEGAS' },
    { key: 'ourmissing', label: 'Falta medir' },
  ];

  // ------------------------------------------------------------------ assinatura barata (sem serializar pontos)
  function mixNumber(hash, value) {
    const n = Number.isFinite(value) ? Math.round(value * 10000) | 0 : 0x7fffffff;
    return Math.imul(hash ^ n, 16777619) >>> 0;
  }
  function mixString(hash, text) {
    let h = hash;
    const s = String(text === null || text === undefined ? '' : text);
    for (let i = 0; i < s.length; i += 1) h = Math.imul(h ^ s.charCodeAt(i), 16777619) >>> 0;
    return h;
  }
  function mixArray(hash, list) {
    let h = mixNumber(hash, Array.isArray(list) ? list.length : -1);
    if (!Array.isArray(list)) return h;
    for (let i = 0; i < list.length; i += 1) h = mixNumber(h, Number(list[i]));
    return h;
  }
  /** Campos da tabela que definem o desenho: status + valores. Não usa snapshotHash (muda sem a geometria mudar). */
  const TABLE_KEYS = [
    'PETR_INJ_TBP', 'PETR_MNFLD_PRESS_RV', 'GAS_MNFLD_PRESS_RV', 'MNFLD_PRESS_THD',
    'PETR_INJ_TBUF', 'MNFLD_PRESS_BUF', 'NUM_BUF_UPD_PETR', 'PETR_INJ_TBUF_GAS', 'MNFLD_PRESS_BUF_GAS', 'NUM_BUF_UPD_GAS',
    'PETR_INJ_TBUF_GAS_PREV', 'MNFLD_PRESS_BUF_GAS_PREV', 'ACQUIRED_ZONES_PETROL', 'ACQUIRED_ZONES_GAS',
    'NUM_AUTOMATCH_EXECUTED', 'MUL_ACT', 'CALIBRATION_VAL_1', 'VECT_AUTOCAL_U8_1',
  ];
  function tableSignature(snapshot) {
    let h = 2166136261;
    const fields = Array.isArray(snapshot && snapshot.fields) ? snapshot.fields : [];
    for (const key of TABLE_KEYS) {
      const item = fields.find(f => f && f.key === key);
      h = mixString(h, key);
      if (!item) { h = mixNumber(h, -1); continue; }
      h = mixString(h, item.status);
      h = mixArray(h, item.rawValues || item.physicalValues);
    }
    return h.toString(36);
  }
  /** Cada lista do eq entra por tamanho + somas: O(n), sem serializar pontos. */
  function listSignature(h, list, keys) {
    let out = mixNumber(h, Array.isArray(list) ? list.length : -1);
    if (!Array.isArray(list)) return out;
    for (const key of keys) {
      let sum = 0;
      for (let i = 0; i < list.length; i += 1) { const v = Number(list[i] && list[i][key]); if (Number.isFinite(v)) sum += v; }
      out = mixNumber(out, sum);
    }
    return out;
  }
  function evidenceSignature(input) {
    const ctx = input || {};
    const eq = ctx.eq || {};
    const dense = eq.denseBands || {};
    let h = mixString(2166136261, tableSignature(ctx.snapshot));
    h = mixString(h, ctx.sessionId);
    h = mixString(h, ctx.size);
    h = mixNumber(h, ctx.history ? ctx.history.length : 0);
    h = mixNumber(h, ctx.usable === false ? 0 : 1);
    h = listSignature(h, dense.petrol, ['mapBar', 'tpetMs', 'samples']);
    h = listSignature(h, dense.gas, ['mapBar', 'tpetMs', 'samples']);
    h = mixNumber(h, Number(eq.stalls && eq.stalls.count));
    h = listSignature(h, eq.stalls && eq.stalls.events, ['petrolMs', 'mapBar']);
    h = mixNumber(h, Number(eq.gasEpochAt));
    h = listSignature(h, ctx.analysis && ctx.analysis.points, ['calculatedRaw', 'currentRaw']);
    h = listSignature(h, eq.points, ['axisMs']);
    if (Array.isArray(eq.betweenPoints)) {
      h = mixNumber(h, eq.betweenPoints.length);
      for (const b of eq.betweenPoints) {
        const g = (b && b.gas) || {}; const pt = (b && b.petrol) || {};
        for (const v of [b && b.index, b && b.centerMs, b && b.centerMapBar, b && b.n, b && b.visits, g.ms, g.mapBar, g.n, pt.ms, pt.mapBar, pt.n]) h = mixNumber(h, Number(v));
        h = mixString(h, b && b.state);
      }
    }
    h = mixString(h, ctx.extra);
    return h.toString(36);
  }

  // ------------------------------------------------------------------ agregação nas 18 faixas da ECU
  function median(values) {
    const sorted = values.slice().sort((a, b) => a - b);
    const mid = sorted.length >> 1;
    return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
  }
  function weightedMean(values, weights) {
    const total = weights.reduce((a, b) => a + b, 0) || 1;
    return values.reduce((acc, v, i) => acc + v * weights[i], 0) / total;
  }
  function weightedMedian(values, weights) {
    const pairs = values.map((v, i) => [v, weights[i]]).sort((a, b) => a[0] - b[0]);
    const half = pairs.reduce((acc, p) => acc + p[1], 0) / 2;
    let run = 0;
    for (const [v, w] of pairs) { run += w; if (run >= half) return v; }
    return pairs.length ? pairs[pairs.length - 1][0] : 0;
  }
  /** Valor da curva (interpolado) em x. Sem pontos: null. */
  function curveAt(points, key, x) {
    const list = (points || []).filter(p => finite(p.petrolMs) !== null && finite(p[key]) > 0).sort((a, b) => a.petrolMs - b.petrolMs);
    if (!list.length) return null;
    if (x <= list[0].petrolMs) return list[0][key];
    for (let i = 1; i < list.length; i += 1) {
      if (x <= list[i].petrolMs) {
        const a = list[i - 1]; const b = list[i];
        const t = b.petrolMs === a.petrolMs ? 0 : (x - a.petrolMs) / (b.petrolMs - a.petrolMs);
        return a[key] + (b[key] - a[key]) * t;
      }
    }
    return list[list.length - 1][key];
  }
  const ECU_BAND_COUNT = 18;
  /**
   * Faixas por MAP. kind 'ecu18' (AutoCal): faixa i = (THD[i], THD[i+1]]; a primeira recolhe a lenta e a última tudo acima de
   * THD[17]. kind 'between' (Refino): UM intervalo entre cada par de limiares consecutivos da ECU (17), mais as pontas abertas
   * (abaixo do primeiro e acima do último limiar), que só ganham marcador se tiverem evidência. Sem os 18 limiares válidos:
   * 18 fatias iguais (só desenho, nunca decisão).
   */
  function bandSlots(thresholds, yMin, yMax, kind) {
    const t = Array.isArray(thresholds) ? thresholds.map(Number) : [];
    const valid = t.length === ECU_BAND_COUNT && t.every((v, i) => Number.isFinite(v) && (i === 0 || v > t[i - 1]));
    if (kind === 'between' && valid) {
      const slots = [{ index: 0, kind: 'below', lowerBar: -Infinity, upperBar: t[0] }];
      for (let i = 0; i < ECU_BAND_COUNT - 1; i += 1) slots.push({ index: i + 1, kind: 'gap', lowerBar: t[i], upperBar: t[i + 1] });
      slots.push({ index: ECU_BAND_COUNT, kind: 'above', lowerBar: t[ECU_BAND_COUNT - 1], upperBar: Infinity });
      return slots;
    }
    const edges = valid ? t : Array.from({ length: ECU_BAND_COUNT }, (_, i) => yMin + (i * (yMax - yMin)) / ECU_BAND_COUNT);
    return Array.from({ length: ECU_BAND_COUNT }, (_, i) => ({
      index: i, kind: 'gap',
      lowerBar: i === 0 ? -Infinity : edges[i],
      upperBar: i === ECU_BAND_COUNT - 1 ? Infinity : edges[i + 1],
    }));
  }
  function slotOf(slots, mapBar) {
    for (const slot of slots) if (mapBar > slot.lowerBar && mapBar <= slot.upperBar) return slot.index;
    return mapBar <= slots[0].upperBar ? 0 : slots.length - 1;
  }
  /**
   * Leituras → UM marcador por faixa e por combustível (confiança = amostras/episódios; bigode só quando incerto).
   * items: [{ fuel, tpetMs, mapBar, samples, episodes?, rpmMedian, idleShare, lastAtMs, fineBins?: [{tpetMs,mapBar,samples}] }]
   * Os "fineBins" (resolução mais fina da ECU/Kotlin) entram na agregação quando existem e só aparecem no toque.
   * ctx: { thresholds, xMin, xMax, yMin, yMax, fullSamples }
   * Devolve { markers, bands, total, kept }. ctx.kind: 'ecu18' (AutoCal) ou 'between' (Refino).
   */
  function aggregateEvidence(items, ctx) {
    const c = ctx || {};
    const yMin = finite(c.yMin) ?? 0;
    const yMax = finite(c.yMax) ?? 1;
    const slots = bandSlots(c.thresholds, yMin, yMax, c.kind);
    const fine = [];
    for (const item of (items || [])) {
      if (Array.isArray(item.fineBins) && item.fineBins.length) {
        for (const f of item.fineBins) fine.push({ ...item, ...f, fuel: item.fuel, fineBins: undefined });
      } else fine.push(item);
    }
    const list = fine.filter(p => finite(p.tpetMs) !== null && finite(p.mapBar) !== null);
    const cells = new Map();
    for (const item of list) {
      const key = `${item.fuel}|${slotOf(slots, item.mapBar)}`;
      if (!cells.has(key)) cells.set(key, []);
      cells.get(key).push(item);
    }
    const ySpan = Math.max(0.01, yMax - yMin);
    const bins = [];
    for (const [key, members] of cells) {
      const slot = Number(key.split('|')[1]);
      const weights = members.map(m => Math.max(1, finite(m.samples) ?? 1));
      const n = weights.reduce((a, b) => a + b, 0);
      const ys = members.map(m => m.mapBar);
      const spread = Math.max(...ys) - Math.min(...ys);
      const bandHeight = Number.isFinite(slots[slot].upperBar - slots[slot].lowerBar) ? slots[slot].upperBar - slots[slot].lowerBar : ySpan / ECU_BAND_COUNT;
      bins.push({
        fuel: members[0].fuel, slot, kind: slots[slot].kind,
        // Entre limiares (Refino): centro ponderado das leituras da faixa; nas faixas da ECU (AutoCal): mediana.
        tpetMs: (c.kind === 'between' ? weightedMean : weightedMedian)(members.map(m => m.tpetMs), weights),
        mapBar: (c.kind === 'between' ? weightedMean : weightedMedian)(ys, weights),
        samples: n, episodes: members.reduce((a, m) => a + (finite(m.episodes) ?? 1), 0), count: members.length,
        rpmMedian: weightedMedian(members.map(m => finite(m.rpmMedian) ?? 0), weights),
        idleShare: members.reduce((a, m, i) => a + (finite(m.idleShare) ?? 0) * weights[i], 0) / n,
        lastAtMs: Math.max(...members.map(m => finite(m.lastAtMs) ?? 0)),
        lowerBar: slots[slot].lowerBar, upperBar: slots[slot].upperBar,
        // bigode só quando as leituras da faixa estão dispersas (incerta)
        whisker: members.length > 1 && spread > bandHeight * 0.5 ? Math.min(spread / 2, bandHeight) : 0,
        fineBins: members,
      });
    }
    const maxN = bins.reduce((a, b) => Math.max(a, b.samples), 1);
    const fullSamples = Math.max(1, finite(c.fullSamples) ?? maxN);
    for (const bin of bins) bin.confidence = Math.max(0.15, Math.min(1, bin.samples / fullSamples));
    const markers = bins.sort((a, b) => a.slot - b.slot || (a.fuel < b.fuel ? -1 : 1));
    // Modelo por faixa: bands[i] = { fromMs,toMs,ratio,samples,episodes,confidence,fineBins } (bands18 no AutoCal, betweenBands no Refino)
    const bands = slots.map(slot => {
      const gas = markers.find(m => m.slot === slot.index && m.fuel === 'GAS') || null;
      const petrol = markers.find(m => m.slot === slot.index && m.fuel === 'PETROL') || null;
      const both = [gas, petrol].filter(Boolean);
      const all = both.flatMap(m => m.fineBins);
      return {
        index: slot.index, kind: slot.kind, lowerBar: slot.lowerBar, upperBar: slot.upperBar,
        fromMs: all.length ? Math.min(...all.map(m => m.tpetMs)) : null,
        toMs: all.length ? Math.max(...all.map(m => m.tpetMs)) : null,
        ratio: gas && petrol && petrol.tpetMs > 0 ? gas.tpetMs / petrol.tpetMs : null,
        samples: both.reduce((a, m) => a + m.samples, 0),
        episodes: both.reduce((a, m) => a + m.episodes, 0),
        confidence: both.length ? both.reduce((a, m) => a + m.confidence, 0) / both.length : 0,
        fineBins: all,
      };
    });
    return { markers, bands, total: list.length, kept: markers.length };
  }


  // ------------------------------------------------------------------ pontos ENTRE as faixas da ECU (contrato betweenPoints[])
  /**
   * Um item por intervalo entre bandas adjacentes da ECU (≤ 36). Aceita nomes alternativos e AUSÊNCIA (null → lista vazia).
   * Saída: { index, state:'collected'|'missing', centerMs, centerMapBar, gas:{ms,mapBar,n}|null, petrol:{...}|null, n, visits }.
   */
  function normalizeBetween(list) {
    if (!Array.isArray(list)) return [];
    const pick = (o, keys) => { for (const k of keys) { const v = finite(o && o[k]); if (v !== null) return v; } return null; };
    const side = (b, name) => {
      const o = b[name] && typeof b[name] === 'object' ? b[name] : null;
      if (!o) return null;
      const ms = pick(o, ['ms']);
      const map = pick(o, ['mapBar']);
      if (ms === null || map === null || !(ms > 0)) return null;
      return { ms, mapBar: map, n: pick(o, ['n']) ?? 0 };
    };
    return list.filter(b => b && typeof b === 'object').slice(0, 36).map((b, i) => {
      const gas = side(b, 'gas');
      const petrol = side(b, 'petrol');
      const state = String(b.state || '').toLowerCase();
      const collected = state ? /colet|collected|ok/.test(state) : Boolean(gas || petrol);
      return {
        index: pick(b, ['index']) ?? i,
        kind: String(b.kind || 'gap'),
        state: collected ? 'collected' : 'missing',
        centerMs: pick(b, ['centerMs']),
        centerMapBar: pick(b, ['centerMapBar']),
        gas, petrol,
        n: pick(b, ['n']) ?? ((gas ? gas.n : 0) + (petrol ? petrol.n : 0)),
        visits: pick(b, ['visits']) ?? 0,
      };
    });
  }
  /** Sem betweenPoints do Kotlin: deriva dos marcadores agregados por intervalo (denseBands), só para desenhar. */
  function betweenFromMarkers(markers) {
    const bySlot = new Map();
    for (const m of markers || []) {
      if (!bySlot.has(m.slot)) bySlot.set(m.slot, { index: m.slot, state: 'collected', centerMs: m.tpetMs, centerMapBar: m.mapBar, gas: null, petrol: null, n: 0, visits: m.episodes || 0, derived: true });
      const b = bySlot.get(m.slot);
      b[m.fuel === 'GAS' ? 'gas' : 'petrol'] = { ms: m.tpetMs, mapBar: m.mapBar, n: m.samples };
      b.n += m.samples;
    }
    return [...bySlot.values()];
  }
  /** Ponto da ECU em frase curta, sem códigos. */
  function describeBetween(b) {
    const parts = [];
    const fuels = [b.gas ? ['GNV', b.gas] : null, b.petrol ? ['Gasolina', b.petrol] : null].filter(Boolean);
    const first = fuels[0];
    parts.push(first ? first[0] : 'Falta medir');
    parts.push(`entre os pontos ${Number(b.index) + 1} e ${Number(b.index) + 2} da ECU`);
    const ms = first ? first[1].ms : b.centerMs;
    if (finite(ms) !== null) parts.push(`${number(ms, 2)} ms`);
    parts.push(first ? 'já medido' : 'ainda sem medida');
    return parts.join(' · ');
  }

  // ------------------------------------------------------------------ escala
  function focusDomain(reference, ecu, ours, options) {
    const o = options || {};
    // Somente coordenadas que podem ser desenhadas. Eixos vazios da ECU não esticam a escala.
    const points = [
      ...(reference || []).flatMap(p => [
        { x: p.petrolMs, y: p.petrolMapBar },
        { x: p.petrolMs, y: p.gasMapBar },
        ...(o.equivalent ? [{ x: p.gasEquivalentMs, y: p.petrolMapBar }] : []),
      ]),
      ...(ecu || []).map(p => ({ x: p.petrolMs, y: p.mapBar })),
      ...(ours || []).map(p => ({ x: p.tpetMs ?? p.petrolMs, y: p.mapBar })),
    ].filter(p => Number.isFinite(p.x) && p.x > 0 && Number.isFinite(p.y) && p.y > 0);
    if (!points.length) return null;
    const maxX = Math.max(...points.map(p => p.x));
    const margin = Math.max(0.25, maxX * 0.04);
    const xMax = Math.max(o.fullRange ? 22 : 1, Math.ceil((maxX + margin) * 2) / 2);
    const ys = points.map(p => p.y);
    const yMargin = Math.max(0.025, (Math.max(...ys) - Math.min(...ys)) * 0.06);
    const yMin = o.fullRange ? 0 : Math.max(0, Math.floor((Math.min(...ys) - yMargin) * 20) / 20);
    const yMax = Math.max(yMin + 0.1, o.fullRange ? 1.15 : 0, Math.ceil((Math.max(...ys) + yMargin) * 20) / 20);
    return { xMin: 0, xMax, yMin, yMax };
  }

  function viewKey(view) {
    const v = view || {};
    return `${v.petrol !== false}|${v.gas !== false}|${v.fullRange === true}`;
  }
  function viewControls() {
    return '<fieldset class="chart-view-controls"><legend>Exibir no gráfico</legend>' +
      '<label><input type="checkbox" data-chart-view="petrol" checked>Gasolina</label>' +
      '<label><input type="checkbox" data-chart-view="gas" checked>GNV</label>' +
      '<label><input type="checkbox" data-chart-view="fullRange">Faixa inteira</label></fieldset>';
  }
  function bindView(host, owner, redraw) {
    if (!host) return;
    owner.chartView = owner.chartView || { petrol: true, gas: true, fullRange: false };
    host.addEventListener('change', event => {
      const input = event.target.closest('[data-chart-view]');
      if (!input) return;
      // Sempre fica pelo menos um combustível visível.
      const key = input.dataset.chartView;
      if ((key === 'petrol' || key === 'gas') && !input.checked &&
          owner.chartView[key === 'petrol' ? 'gas' : 'petrol'] === false) { input.checked = true; return; }
      owner.chartView[key] = input.checked;
      redraw();
    });
  }

  // ------------------------------------------------------------------ desenho
  /**
   * model: { reference, history, zones, ecu, ours(bins agregados), proposal:[index], stalls:[{petrolMs,mapBar}], live? }
   * opts:  { width, height, selected:{ref, ecu, batch:Set}, hit:{} }
   * Devolve { svg, scale } ou { empty: true }.
   */
  function buildSvg(model, opts) {
    const o = opts || {};
    const width = Math.max(320, Math.round(o.width || 1000));
    const height = Math.max(160, Math.round(o.height || 400));
    const between = o.mode === 'between';
    const padLeft = 88; const padRight = 32; const padTop = 12; const padBottom = 48;
    const reference = model.reference || [];
    const domain = model.domain;
    if (!domain) return { empty: true };
    const { xMin, xMax, yMin, yMax } = domain;
    const xFor = v => padLeft + ((v - xMin) / (xMax - xMin)) * (width - padLeft - padRight);
    const yFor = v => height - padBottom - ((v - yMin) / (yMax - yMin)) * (height - padTop - padBottom);
    const scale = { xMin, xMax, yMin, yMax, xFor, yFor, width, height };
    const inY = v => v >= yMin && v <= yMax;
    const pathFor = (items, yKey, xKey) => items
      .filter(p => finite(p[xKey || 'petrolMs']) !== null && finite(p[yKey]) !== null && p[yKey] > 0 && inY(p[yKey]) && p[xKey || 'petrolMs'] <= xMax)
      .map((p, i) => `${i ? 'L' : 'M'} ${xFor(p[xKey || 'petrolMs']).toFixed(1)} ${yFor(p[yKey]).toFixed(1)}`).join(' ');
    const sel = o.selected || {};
    const hasPetrol = reference.some(p => finite(p.petrolMapBar) > 0);
    const hasGas = reference.some(p => finite(p.gasMapBar) > 0);

    const step = xMax <= 6 ? 1 : xMax <= 14 ? 2 : xMax <= 24 ? 4 : Math.ceil(xMax / 6 / 2) * 2;
    const xTicks = Array.from({ length: Math.ceil(xMax / step) }, (_, i) => i * step);
    if (xMax - xTicks[xTicks.length - 1] < step / 2) xTicks.pop();
    xTicks.push(xMax);
    const yTicks = Array.from({ length: 5 }, (_, i) => yMin + i * (yMax - yMin) / 4);
    const grid = yTicks.map(v => `<line class="autocal-grid-line" x1="${padLeft}" y1="${yFor(v).toFixed(1)}" x2="${width - padRight}" y2="${yFor(v).toFixed(1)}"></line><text class="autocal-axis-tick-y" x="${padLeft - 8}" y="${(yFor(v) + 5).toFixed(1)}" text-anchor="end">${tick(v, 3)}</text>`).join('') +
      xTicks.map(v => `<line class="autocal-grid-line vertical" x1="${xFor(v).toFixed(1)}" y1="${padTop}" x2="${xFor(v).toFixed(1)}" y2="${height - padBottom}"></line><text class="autocal-axis-tick-x" x="${xFor(v).toFixed(1)}" y="${height - padBottom + 20}" text-anchor="middle">${tick(v, 1)}</text>`).join('');

    const zoneMarkup = between ? '' : (model.zones || []).map(zone => {
      const lower = Math.max(zone.lower, yMin);
      const upper = Math.min(zone.upper, yMax);
      if (upper <= lower) return '';
      const top = Math.min(yFor(lower), yFor(upper));
      const zoneHeight = Math.abs(yFor(lower) - yFor(upper));
      const label = state => state === 'acquired' ? 'OK' : state === 'missing' ? 'FALTA' : '—';
      const caption = `Z${zone.zone} · Gasolina ${label(zone.petrolState)} · GNV ${label(zone.gasState)}`;
      return `<g class="autocal-zone-surface" data-autocal-zone-surface="${zone.zone}" data-gas-state="${zone.gasState}" data-petrol-state="${zone.petrolState}" data-current="false" aria-label="${caption}">` +
        `<rect class="autocal-zone-background" x="${padLeft}" y="${top.toFixed(1)}" width="${width - padLeft - padRight}" height="${zoneHeight.toFixed(1)}"></rect>` +
        `<rect class="autocal-zone-petrol-edge" x="${padLeft + 2}" y="${top.toFixed(1)}" width="5" height="${zoneHeight.toFixed(1)}"></rect>` +
        `<rect class="autocal-zone-gas-edge" x="${padLeft + 9}" y="${top.toFixed(1)}" width="5" height="${zoneHeight.toFixed(1)}"></rect>` +
        (zoneHeight >= 24 ? `<text class="autocal-zone-label" data-autocal-zone-label data-base-label="Z${zone.zone}" x="${width - padRight - 10}" y="${(top + zoneHeight / 2 + 5).toFixed(1)}" text-anchor="end">Z${zone.zone}</text>` : '') + '</g>';
    }).join('');

    const history = between ? [] : (model.history || []);
    const previous = history.length
      ? `<path class="autocal-reference-line previous petrol" d="${pathFor(history, 'petrolMapBar')}"></path><path class="autocal-reference-line previous gas" d="${pathFor(history, 'gasMapBar')}"></path>` : '';
    const refMarkup = reference.map(p => {
      if (p.petrolMs > xMax) return '';
      const x = xFor(p.petrolMs).toFixed(1);
      const hit = (cy) => `<circle class="autocal-reference-hit" data-autocal-ref-index="${p.index}" cx="${x}" cy="${cy}" r="22"></circle>`;
      return (inY(p.petrolMapBar) && p.petrolMapBar > 0 ? `${hit(yFor(p.petrolMapBar).toFixed(1))}<circle class="autocal-reference-point petrol" data-ref-marker="${p.index}" cx="${x}" cy="${yFor(p.petrolMapBar).toFixed(1)}" r="5.5"></circle>` : '') +
        (inY(p.gasMapBar) && p.gasMapBar > 0 ? `${hit(yFor(p.gasMapBar).toFixed(1))}<circle class="autocal-reference-point gas" data-ref-marker="${p.index}" cx="${x}" cy="${yFor(p.gasMapBar).toFixed(1)}" r="4"></circle>` : '');
    }).join('');

    const ecuMarkup = (model.ecu || []).map((p, i) => {
      if (p.mapBar < yMin || p.mapBar > yMax || p.petrolMs < xMin || p.petrolMs > xMax) return '';
      const x = xFor(p.petrolMs).toFixed(1); const y = yFor(p.mapBar).toFixed(1);
      const key = `${p.fuel}:${p.index}`;
      const progress = finite(p.progress);
      const done = p.acquisitionState === 'ACQUIRED';
      return `<circle class="autocal-acquired-hit${sel.ecu === key ? ' selected' : ''}${sel.batch && sel.batch.has(key) ? ' batch-selected' : ''}" data-autocal-acquired-fuel="${p.fuel}" data-autocal-acquired-index="${p.index}" data-refino-dot="ecu:${i}" cx="${x}" cy="${y}" r="22"></circle>` +
        `<circle class="autocal-acquired-point ${p.fuel === 'GAS' ? 'gas' : 'petrol'} ${done ? 'acquired' : 'collecting'}" data-acquisition-state="${p.acquisitionState}" data-acquisition-progress="${(progress ?? 0).toFixed(3)}" cx="${x}" cy="${y}" r="6"${done || progress === null ? '' : ` fill-opacity="${(0.35 + 0.65 * progress).toFixed(2)}"`}></circle>`;
    }).join('');

    // Refino: pontos do OMEGAS são BOLINHAS do tamanho das da ECU, entre elas. AutoCal não mostra pontos do OMEGAS.
    const dot = (cls, x, y, extra) => `<circle class="chart-between ${cls}" cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="6"${extra || ''}></circle>`;
    const oursMarkup = between ? `<g class="layer-between">${(model.betweenPoints || []).map((b, i) => {
      const token = `data-chart-our="b:${i}" data-refino-dot="ourb:${i}"`;
      const shapes = [];
      const place = (side, cls) => {
        if (!side || side.mapBar < yMin || side.mapBar > yMax || side.ms > xMax) return;
        const x = xFor(side.ms); const y = yFor(side.mapBar);
        shapes.push(`<circle class="autocal-acquired-hit${o.selected && o.selected.our === `b:${i}` ? ' selected' : ''}" ${token} cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="22"></circle>` + dot(`${cls} collected`, x, y));
      };
      place(b.gas, 'gas'); place(b.petrol, 'petrol');
      if (!shapes.length && b.state === 'missing' && finite(b.centerMs) !== null && finite(b.centerMapBar) !== null && inY(b.centerMapBar) && b.centerMs <= xMax) {
        const x = xFor(b.centerMs); const y = yFor(b.centerMapBar);
        shapes.push(`<circle class="autocal-acquired-hit" ${token} cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="22"></circle>` + dot('missing', x, y));
      }
      return shapes.join('');
    }).join('')}</g>` : '';
    const tickMarkup = '';

    const proposalMarkup = true ? '' : (model.proposal || []).map(index => {
      const p = reference.find(r => r.index === index);
      if (!p || !inY(p.gasMapBar) || p.petrolMs > xMax) return '';
      return `<circle class="chart-proposal" cx="${xFor(p.petrolMs).toFixed(1)}" cy="${yFor(p.gasMapBar).toFixed(1)}" r="11"></circle>`;
    }).join('');

    const stallMarkup = !between ? '' : (model.stalls || []).filter(e => finite(e.petrolMs) !== null && finite(e.mapBar) !== null && e.petrolMs <= xMax && inY(e.mapBar))
      .map(e => { const x = xFor(e.petrolMs); const y = yFor(e.mapBar); return `<path class="refino-stall-mark" d="M${(x - 6).toFixed(1)} ${(y - 6).toFixed(1)} L${(x + 6).toFixed(1)} ${(y + 6).toFixed(1)} M${(x + 6).toFixed(1)} ${(y - 6).toFixed(1)} L${(x - 6).toFixed(1)} ${(y + 6).toFixed(1)}"></path>`; }).join('');

    const live = '<g class="autocal-live-layer" data-refino-live data-chart-live display="none" aria-label="Posição atual do motor"><circle class="autocal-live-halo" data-autocal-live-point r="13" cx="0" cy="0"></circle><circle class="autocal-live-point" data-autocal-live-point r="6" cx="0" cy="0"></circle><text class="autocal-live-label" data-autocal-live-label text-anchor="start" x="0" y="0">AGORA</text></g>';

    const equivalent = reference.filter(p => finite(p.gasEquivalentMs) !== null);
    const equivalencePath = between && equivalent.length > 1 ? `<path class="autocal-equivalence-line" d="${pathFor(equivalent, 'petrolMapBar', 'gasEquivalentMs')}"></path>` : '';

    const svg = `<svg class="autocal-reference-svg" viewBox="0 0 ${width} ${height}" width="${width}" height="${height}" role="img" aria-label="${between ? 'Refino: curva da gasolina e do GNV, pontos da ECU e pontos do OMEGAS entre eles' : 'AutoCal: curva da gasolina e do GNV, pontos lidos pela ECU e posição Agora'}">${grid}<g class="layer-zones">${zoneMarkup}</g>${tickMarkup}` +
      `<text class="autocal-axis-title x" x="${((padLeft + width - padRight) / 2).toFixed(1)}" y="${height - 6}" text-anchor="middle">Injeção de gasolina (ms)</text>` +
      `<text class="autocal-axis-title y" x="16" y="${(height - padBottom) / 2}" text-anchor="middle" transform="rotate(-90 16 ${(height - padBottom) / 2})">MAP (bar)</text>` +
      `<g><rect class="autocal-current-band-layer" data-autocal-current-band display="none" x="0" y="0" width="0" height="0"></rect>${previous}${equivalencePath}` +
      `${hasGas ? `<path class="autocal-reference-depth" aria-hidden="true" d="${pathFor(reference, 'gasMapBar')}"></path>` : ''}${hasPetrol ? `<path class="autocal-reference-line petrol" d="${pathFor(reference, 'petrolMapBar')}"></path>` : ''}${hasGas ? `<path class="autocal-reference-line gas" d="${pathFor(reference, 'gasMapBar')}"></path>` : ''}` +
      `${refMarkup}${oursMarkup}${ecuMarkup}${proposalMarkup}${stallMarkup}${live}</g></svg>`;
    return { svg, scale };
  }

  function legendHtml(flags) {
    const f = flags || {};
    const base = LEGEND.filter(item => item.key !== 'proposal' || f.proposal === true);
    const extra = f.mode === 'between' ? BETWEEN_LEGEND.filter(item => item.key !== 'ourmissing' || f.missing !== false) : [];
    const items = base.concat(extra, []);
    return items.map(item => `<span class="${item.key}" data-legend="${item.key}">${esc(item.label)}</span>`).join('');
  }

  // ------------------------------------------------------------------ nó compartilhado (uma instância, vários quadros)
  const shared = { mode: 'ecu18', signature: '', html: '', node: null, scale: null, renders: 0, mounts: 0, bins: [], model: null };
  /**
   * Garante que `host` mostre o gráfico da assinatura dada. Redesenha SÓ se a assinatura mudou; fora isso apenas
   * move o mesmo nó para o quadro pedido. `build()` devolve { svg, scale, bins } (ou { empty, html }).
   */
  function mount(host, signature, build, mode) {
    if (!host) return null;
    shared.mode = mode || 'ecu18';
    if (shared.signature !== signature || !shared.html) {
      const built = build() || {};
      shared.signature = signature;
      shared.renders += 1;
      shared.html = built.svg || built.html || '';
      shared.scale = built.scale || null;
      shared.bins = built.bins || [];
      shared.model = built.model || null;
      if (shared.node) shared.node.innerHTML = shared.html;
    }
    const canMove = typeof host.appendChild === 'function' && typeof document !== 'undefined' && typeof document.createElement === 'function';
    if (!canMove) {
      if (host.__chartSignature !== signature) { host.__chartSignature = signature; host.innerHTML = shared.html; }
      return shared;
    }
    if (!shared.node) { shared.node = document.createElement('div'); shared.node.className = 'curve-chart-shared'; shared.node.innerHTML = shared.html; }
    if (shared.node.parentNode !== host) {
      host.innerHTML = '';
      host.appendChild(shared.node);
      shared.mounts += 1;
    }
    shared.node.setAttribute('data-mode', shared.mode);
    return shared;
  }
  /** Outra coisa ocupou o quadro (vazio, época): a próxima montagem recoloca o nó. */
  function release(host) {
    if (host) host.__chartSignature = '';
  }
  function reset() { shared.signature = ''; shared.html = ''; shared.scale = null; shared.bins = []; shared.model = null; }

  /** Seleção é estado de tela, não evidência: só troca classes, nunca redesenha. */
  function applySelection(selected) {
    const node = shared.node;
    if (!node || typeof node.querySelectorAll !== 'function') return;
    const sel = selected || {};
    node.querySelectorAll('.autocal-reference-point[data-ref-marker]').forEach(marker => {
      marker.classList.toggle('selected', Number(marker.getAttribute('data-ref-marker')) === sel.ref);
    });
    node.querySelectorAll('.autocal-acquired-hit[data-chart-our]').forEach(hit => {
      hit.classList.toggle('selected', sel.our === hit.getAttribute('data-chart-our'));
    });
    node.querySelectorAll('.autocal-acquired-hit[data-autocal-acquired-fuel]').forEach(hit => {
      const key = `${hit.getAttribute('data-autocal-acquired-fuel')}:${hit.getAttribute('data-autocal-acquired-index')}`;
      hit.classList.toggle('selected', sel.ecu === key);
      hit.classList.toggle('batch-selected', !!(sel.batch && sel.batch.has(key)));
    });
  }


  // ------------------------------------------------------------------ modelo único e armazém de evidência
  /** Estado nativo do AutoCal derivado da projeção (igual para as duas telas). */
  function deriveState(projection) {
    const p = projection || {};
    const status = p.nativeStatus || {};
    const native = p.nativeSnapshot || {};
    return Object.assign({}, status, { latestSnapshot: native.available ? native : status.latestSnapshot });
  }
  function buildModel(ctx) {
    const UX = ns.AutoCalUxModel;
    if (!UX) return null;
    const c = ctx || {};
    const projection = c.projection || {};
    const snapshot = c.snapshot || projection.snapshot || {};
    const visible = c.view || {};
    const project = p => ({ ...p, petrolMapBar: visible.petrol === false ? null : p.petrolMapBar, gasMapBar: visible.gas === false ? null : p.gasMapBar, gasEquivalentMs: visible.gas === false ? null : p.gasEquivalentMs });
    const allReference = UX.referencePoints(snapshot, projection.analysis || {}).map(project);
    const history = (c.history || []).map(project);
    const ecu = [...(visible.petrol === false ? [] : UX.acquiredPoints(snapshot, 'petrol')), ...(visible.gas === false ? [] : UX.acquiredPoints(snapshot, 'gas'))];
    const eq = c.eq || {};
    const dense = eq.denseBands || {};
    const items = [
      ...(Array.isArray(dense.petrol) ? dense.petrol : []).map(p => ({ ...p, fuel: 'PETROL' })),
      ...(Array.isArray(dense.gas) ? dense.gas : []).map(p => ({ ...p, fuel: 'GAS' })),
    ].filter(p => finite(p.tpetMs) !== null && finite(p.mapBar) !== null && (p.fuel === 'GAS' ? visible.gas !== false : visible.petrol !== false));
    const human = UX.humanState(snapshot, deriveState(projection), projection);
    // AutoCal não desenha pontos nossos: a escala vem só da ECU e das curvas.
    const given = normalizeBetween(eq.betweenPoints || (c.analysis && c.analysis.betweenPoints)).filter(b => b.kind === 'gap' || (b.kind !== 'open-low' && b.kind !== 'open-high' && b.index >= 0 && b.index < 17)).map(b => ({
      ...b, gas: visible.gas === false ? null : b.gas, petrol: visible.petrol === false ? null : b.petrol,
    }));
    const intervalPoints = given.flatMap(b => [
      ...[b.gas, b.petrol].filter(Boolean).map(p => ({ tpetMs: p.ms, mapBar: p.mapBar })),
      ...(b.state === 'missing' ? [{ tpetMs: b.centerMs, mapBar: b.centerMapBar }] : []),
    ]);
    const stalls = Array.isArray(eq.stalls && eq.stalls.events) ? eq.stalls.events : [];
    const relevant = c.mode === 'between' ? [...items, ...intervalPoints, ...stalls.map(p => ({ tpetMs: p.petrolMs, mapBar: p.mapBar }))] : [];
    // A tabela completa é uma régua, não aquisição. Vista normal acompanha a faixa
    // adquirida; Faixa inteira mantém toda a régua. Nenhum ponto adquirido é descartado.
    const anchors = [...ecu.map(p => ({ x: p.petrolMs, y: p.mapBar })),
      ...relevant.map(p => ({ x: p.tpetMs ?? p.petrolMs, y: p.mapBar }))].filter(p => Number.isFinite(p.x) && p.x > 0 && Number.isFinite(p.y) && p.y > 0);
    const lastMeasured = anchors.length ? Math.max(...anchors.map(p => p.x)) : null;
    const limit = lastMeasured === null ? Infinity : lastMeasured + Math.max(.25, lastMeasured * .04);
    const reference = visible.fullRange || !anchors.length ? allReference : allReference.filter(p => p.petrolMs <= limit);
    const domain = focusDomain([...reference, ...history], ecu, relevant, { fullRange: visible.fullRange, equivalent: c.mode === 'between' && visible.gas !== false });
    const thresholds = (() => {
      const f = (Array.isArray(snapshot.fields) ? snapshot.fields : []).find(x => x && x.key === 'MNFLD_PRESS_THD' && x.status === 'VALID');
      return f && Array.isArray(f.physicalValues) ? f.physicalValues : null;
    })();
    const empty = { markers: [], bands: [], total: items.length, kept: 0 };
    const evidence = domain ? aggregateEvidence(items, { ...domain, thresholds, fullSamples: 60, kind: 'ecu18' }) : empty;
    // Refino: um item por intervalo ENTRE bandas da ECU. Se o Kotlin já entrega `betweenPoints`, ele manda;
    // sem ele, deriva dos marcadores agregados (só desenho).
    const between = domain ? aggregateEvidence(items, { ...domain, thresholds, fullSamples: 60, kind: 'between' }) : empty;
    const betweenPoints = given.length ? given : betweenFromMarkers(between.markers.filter(m => m.kind === 'gap'));
    const edges = thresholds ? thresholds.map(Number).filter(Number.isFinite) : [];
    const refined = c.analysis && Array.isArray(c.analysis.points) ? c.analysis.points : [];
    const proposal = refined.filter(p => p && p.origin !== 'HELD' && finite(p.calculatedRaw) !== null && Number(p.calculatedRaw) !== Number(p.currentRaw)).map(p => Number(p.index));
    return { reference, history, zones: UX.zoneSurface(snapshot, human), ecu, ours: evidence.markers, bands18: evidence.bands, between: between.markers, betweenPoints, betweenGiven: given.length > 0, betweenBands: between.bands, edges, evidence, domain, proposal, stalls, human, rawItems: items };
  }

  /**
   * Armazém ÚNICO da evidência do gráfico. Quem chama (AutoCal ou Refino) só pede `update`; ele busca eq/análise
   * no máximo a cada WATCHDOG_MS (rede de segurança contra evento perdido) ou na hora quando a tabela da ECU muda.
   */
  const WATCHDOG_MS = 5000;
  const evidence = { eq: null, analysis: null, tableSig: '', fetchedAt: 0, fetches: 0 };
  function updateEvidence(api, projection, nowMs, force) {
    const tableSig = tableSignature(projection && projection.snapshot);
    const due = force === true || !evidence.fetchedAt || tableSig !== evidence.tableSig || nowMs - evidence.fetchedAt >= WATCHDOG_MS;
    evidence.tableSig = tableSig;
    if (!due || !api) return false;
    evidence.fetchedAt = nowMs;
    evidence.fetches += 1;
    evidence.eq = (typeof api.equivalence === 'function' ? api.equivalence() : null) || evidence.eq;
    evidence.analysis = (typeof api.refinedAnalysis === 'function' ? api.refinedAnalysis() : null) || evidence.analysis;
    return true;
  }
  /** Depois de gravar/desfazer: o Refino lê o estado novo na hora. */
  function setEvidence(eq, analysis, nowMs) {
    if (eq) evidence.eq = eq;
    if (analysis) evidence.analysis = analysis;
    evidence.fetchedAt = nowMs || Date.now();
  }

  ns.CurveChart = {
    LEGEND, STALL_LEGEND, BETWEEN_LEGEND, normalizeBetween, betweenFromMarkers, describeBetween, ECU_BAND_COUNT, TABLE_KEYS, bandSlots,
    evidenceSignature, tableSignature, aggregateEvidence, curveAt, focusDomain, buildSvg, legendHtml,
    viewKey, viewControls, bindView, mount, release, reset, applySelection, shared, buildModel, deriveState, evidence, updateEvidence, setEvidence, WATCHDOG_MS,
  };
})(typeof window !== 'undefined' ? window : globalThis);
