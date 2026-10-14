(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  /**
   * Modelo e desenho do AutoCal como o programa original (PROTOCOLO-LANDI docs 20, 21 e 25), em tempo real.
   * Lê só os vetores do snapshot nativo e nunca depende de "referência utilizável": o que a ECU mandou é o que se desenha.
   * Por decisão do dono (2026-10-13) NÃO existe a série GasPointPrev (leitura anterior) nem "Manual automatch".
   */
  const AXIS_Y = { min: 0.15, max: 1.10, step: 0.05 };
  // O original mostra 0,4–1,6; aqui vai até 2,0 para que um nó acima do teto de gravação (1,8) apareça em vez de sumir.
  const K_AXIS_Y = { min: 0.4, max: 2.0, step: 0.1 };
  const K_WRITE_CEILING = 1.8;
  // Zonas por banda (tabela [5, 9, 13] do código do original): 0–4, 5–8, 9–12, 13–17.
  const ZONE_EDGES = [0, 5, 9, 13, 17];
  const BANDS = 18;
  const X_AXIS_FLOOR_MS = 6;

  function finite(value) {
    if (value === null || value === undefined || value === '') return null;
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  }
  function field(snapshot, key) {
    const fields = Array.isArray(snapshot?.fields) ? snapshot.fields : [];
    return fields.find(item => String(item?.key || '') === key && String(item?.status || '') === 'VALID') || null;
  }
  function physical(snapshot, key) {
    const item = field(snapshot, key);
    return Array.isArray(item?.physicalValues) ? item.physicalValues.map(finite) : [];
  }
  function raw(snapshot, key) {
    const item = field(snapshot, key);
    return Array.isArray(item?.rawValues) ? item.rawValues.map(finite) : [];
  }
  function esc(text) {
    return String(text).replace(/[&<>"]/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[ch]);
  }

  function zoneOfBand(index) {
    if (index <= 4) return 0;
    if (index <= 8) return 1;
    if (index <= 12) return 2;
    return 3;
  }

  /** Nós com Tinj ou MAP = 0 são omitidos (slot vazio); nós abaixo do eixo MAP ficam fora do desenho. */
  function pairs(xs, ys, axisY) {
    const count = Math.min(xs.length, ys.length);
    const out = [];
    for (let index = 0; index < count; index += 1) {
      const x = xs[index];
      const y = ys[index];
      if (x === null || y === null || x === 0 || y === 0) continue;
      out.push({ index, x, y, visible: y >= axisY.min && y <= axisY.max });
    }
    return out;
  }

  function currentBand(thresholds, mapBar) {
    if (mapBar === null || thresholds.length < 2 || thresholds.some(value => value === null)) return null;
    for (let index = 0; index < thresholds.length - 1; index += 1) {
      if (!(thresholds[index + 1] > thresholds[index])) return null;
    }
    if (!(mapBar > thresholds[0] && mapBar < thresholds[thresholds.length - 1])) return null;
    for (let index = 0; index < thresholds.length - 1; index += 1) {
      if (mapBar > thresholds[index] && mapBar <= thresholds[index + 1]) {
        return { index, lower: thresholds[index], upper: thresholds[index + 1] };
      }
    }
    return null;
  }

  /**
   * Bolinhas por zona (UX do dono, 2026-10-13): verde = zona completa (flag da ECU), azul = coletando (já há amostra na zona),
   * laranja = falta. Zona sem leitura = 'unknown' (cinza). Vale para cada combustível, independente do que está em uso.
   */
  function zoneStates(snapshot, fuel) {
    const flags = raw(snapshot, fuel === 'petrol' ? 'ACQUIRED_ZONES_PETROL' : 'ACQUIRED_ZONES_GAS');
    const buffer = physical(snapshot, fuel === 'petrol' ? 'MNFLD_PRESS_BUF' : 'MNFLD_PRESS_BUF_GAS');
    return Array.from({ length: 4 }, (_, zone) => {
      if (flags.length !== 4 || flags[zone] === null) return { zone, state: 'unknown' };
      if (flags[zone] !== 0) return { zone, state: 'done' };
      let samples = 0;
      for (let band = ZONE_EDGES[zone]; band < ZONE_EDGES[zone + 1]; band += 1) if ((buffer[band] ?? 0) !== 0) samples += 1;
      return { zone, state: samples > 0 ? 'collecting' : 'missing' };
    });
  }

  /** Uma bolinha por AutoMatch da cota: cheia = já feito. Sem contador ou sem cota = lista vazia (nada inventado). */
  function automatchDots(count, max) {
    const done = finite(count);
    const total = finite(max);
    if (done === null || total === null || total < 1 || total > 12) return [];
    return Array.from({ length: total }, (_, index) => ({ index, done: index < done }));
  }

  /**
   * @param {object} snapshot  snapshot nativo (fields[].key/status/rawValues/physicalValues)
   * @param {{fuel?: 'gas'|'petrol', live?: object}} options  fuel = combustível em uso (zonas e rótulos seguem ele)
   */
  function build(snapshot, options = {}) {
    const fuel = options.fuel === 'petrol' ? 'petrol' : 'gas';
    const live = options.live || null;
    const tbp = physical(snapshot, 'PETR_INJ_TBP');
    const thresholds = physical(snapshot, 'MNFLD_PRESS_THD');
    const lastNode = [...tbp].reverse().find(value => value !== null && value > 0) ?? null;
    if (lastNode === null || thresholds.length !== BANDS || thresholds.some(value => value === null)) {
      return { ok: false, reason: 'Aguardando eixo e limiares da ECU.' };
    }
    const fullAxisX = { min: 1, max: Math.max(lastNode, 2) };
    const axisY = { ...AXIS_Y };

    const petrolX = physical(snapshot, 'PETR_INJ_TBUF');
    const petrolY = physical(snapshot, 'MNFLD_PRESS_BUF');
    const gasX = physical(snapshot, 'PETR_INJ_TBUF_GAS');
    const gasY = physical(snapshot, 'MNFLD_PRESS_BUF_GAS');
    const counters = { petrol: raw(snapshot, 'NUM_BUF_UPD_PETR'), gas: raw(snapshot, 'NUM_BUF_UPD_GAS') };
    const calibration = raw(snapshot, 'CALIBRATION_VAL_1');
    const u8 = index => raw(snapshot, 'VECT_AUTOCAL_U8_' + index)[0] ?? null;
    const calibrationAt = index => (calibration.length > index ? calibration[index] : null);

    // Limiar de amostras por banda (FUN_00516f64): o 5 é constante do código do original.
    const threshold = (which, band) => which === 'gas'
      ? (band <= 5 ? calibrationAt(5) : calibrationAt(8))
      : (band <= 5 ? u8(1) : calibrationAt(2));
    const squares = which => Array.from({ length: BANDS }, (_, band) => {
      const count = counters[which][band] ?? null;
      const limit = threshold(which, band);
      const y = (which === 'gas' ? gasY : petrolY)[band] ?? null;
      const shown = count !== null && limit !== null && limit > 0 && count >= limit && y !== null && y !== 0;
      return { band, count, limit, y, shown };
    });

    // Rótulo "#n/d" (ChartDataAfterDraw): n = entradas ≠ 0 do buffer MAP do combustível em uso dentro da zona.
    const bufferInUse = fuel === 'gas' ? gasY : petrolY;
    const denominators = fuel === 'gas'
      ? [calibrationAt(4), calibrationAt(7), calibrationAt(7), calibrationAt(7)]
      : [u8(0), calibrationAt(1), calibrationAt(1), calibrationAt(1)];
    const zoneFlags = raw(snapshot, fuel === 'gas' ? 'ACQUIRED_ZONES_GAS' : 'ACQUIRED_ZONES_PETROL');
    const zones = Array.from({ length: 4 }, (_, zone) => {
      let n = 0;
      for (let band = ZONE_EDGES[zone]; band < ZONE_EDGES[zone + 1]; band += 1) {
        if ((bufferInUse[band] ?? 0) !== 0) n += 1;
      }
      const d = denominators[zone];
      return {
        zone,
        lower: thresholds[ZONE_EDGES[zone]],
        upper: thresholds[Math.min(ZONE_EDGES[zone + 1], BANDS - 1)],
        acquired: zoneFlags.length === 4 ? zoneFlags[zone] !== 0 && zoneFlags[zone] !== null : null,
        n,
        d,
        label: d === null ? '#' + n + '/—' : '#' + n + '/' + d,
      };
    });

    const kValues = physical(snapshot, 'MUL_ACT');
    const kNodes = [];
    for (let index = 0; index < Math.min(tbp.length, kValues.length); index += 1) {
      if (tbp[index] !== null && tbp[index] > 0 && kValues[index] !== null) {
        kNodes.push({ index, x: tbp[index], y: kValues[index] });
      }
    }

    const liveMap = finite(live?.mapBar ?? live?.load_bar);
    const livePetrol = finite(live?.petrolMs ?? live?.petrol_ms);
    const petrolPoints = pairs(petrolX, petrolY, axisY);
    const gasPoints = pairs(gasX, gasY, axisY);
    // Eixo X flexível (dono, 2026-10-13): só até onde já houve aquisição (ou onde o motor está agora), com 1 ms de folga
    // e passo de 2 ms para o eixo não ficar pulando; nunca além do último nó da ECU nem abaixo de 6 ms.
    // Extras do Refino (nossos pontos medidos e marcas de engasgo) entram no MESMO gráfico; o AutoCal não passa nada.
    const extras = options.extras || null;
    const ours = extras ? {
      petrol: ourNodes(extras.petrol, axisY, fullAxisX),
      gas: ourNodes(extras.gas, axisY, fullAxisX),
      stalls: markNodes(extras.stalls, axisY, fullAxisX),
      near: markNodes(extras.near, axisY, fullAxisX),
    } : null;
    const oursX = ours ? [...ours.petrol, ...ours.gas, ...ours.stalls, ...ours.near].map(n => n.x) : [];
    const reached = Math.max(0, ...petrolPoints.map(n => n.x), ...gasPoints.map(n => n.x), ...oursX, livePetrol ?? 0);
    const axisX = { min: 1, max: Math.min(fullAxisX.max, Math.max(X_AXIS_FLOOR_MS, Math.ceil((reached + 1) / 2) * 2)) };
    return {
      ok: true,
      fuel,
      axisX,
      fullAxisX,
      axisY,
      kAxisY: { ...K_AXIS_Y },
      thresholds,
      zones,
      petrolSquares: squares('petrol'),
      gasSquares: squares('gas'),
      petrolPoints,
      gasPoints,
      petrolCurve: pairs(tbp, physical(snapshot, 'PETR_MNFLD_PRESS_RV'), axisY),
      gasCurve: pairs(tbp, physical(snapshot, 'GAS_MNFLD_PRESS_RV'), axisY),
      limitMin: physical(snapshot, 'LIMIT_PRESSURE_MIN')[0] ?? null,
      limitMax: physical(snapshot, 'LIMIT_PRESSURE_MAX')[0] ?? null,
      band: currentBand(thresholds, liveMap),
      run: liveMap !== null && livePetrol !== null ? { x: livePetrol, y: liveMap } : null,
      mapNow: liveMap,
      kNodes,
      ours,
    };
  }

  const OUR_MAX_NODES = 10;

  /**
   * Nossos pontos medidos (faixas densas do Refino) → nós do gráfico. O app pode medir quantos pontos quiser; a tela mostra no
   * máximo OUR_MAX_NODES por combustível (dono, 2026-10-13: "não preciso ver os mil pontos"): vizinhos no eixo X viram um
   * ponto só, ponderado pelas amostras. Só a tela é resumida; o motor continua usando tudo.
   */
  function ourNodes(list, axisY, axisX) {
    const nodes = (Array.isArray(list) ? list : [])
      .map(p => ({ x: finite(p?.tpetMs), y: finite(p?.mapBar), samples: Math.max(1, finite(p?.samples) || 1) }))
      .filter(n => n.x !== null && n.y !== null && n.x > 0 && n.x <= axisX.max && n.y >= axisY.min && n.y <= axisY.max)
      .sort((a, b) => a.x - b.x);
    if (nodes.length <= OUR_MAX_NODES) return nodes;
    const width = axisX.max / OUR_MAX_NODES;
    const bins = new Map();
    for (const n of nodes) {
      const key = Math.min(OUR_MAX_NODES - 1, Math.floor(n.x / width));
      const bin = bins.get(key) || { x: 0, y: 0, samples: 0 };
      bin.x += n.x * n.samples; bin.y += n.y * n.samples; bin.samples += n.samples;
      bins.set(key, bin);
    }
    return [...bins.entries()].sort((a, b) => a[0] - b[0])
      .map(([, b]) => ({ x: b.x / b.samples, y: b.y / b.samples, samples: b.samples }));
  }
  function markNodes(list, axisY, axisX) {
    return (Array.isArray(list) ? list : [])
      .map(e => ({ x: finite(e?.petrolMs), y: finite(e?.mapBar) }))
      .filter(n => n.x !== null && n.y !== null && n.x > 0 && n.x <= axisX.max && n.y >= axisY.min && n.y <= axisY.max);
  }

  // ---------------------------------------------------------------------------------------------- desenho (SVG)
  function plot(width, height, axisX, axisY, margin) {
    const m = { left: 46, right: 10, top: 10, bottom: 28, ...margin };
    const w = width - m.left - m.right;
    const h = height - m.top - m.bottom;
    return {
      m, w, h,
      X: value => m.left + ((value - axisX.min) / (axisX.max - axisX.min)) * w,
      Y: value => m.top + h - ((value - axisY.min) / (axisY.max - axisY.min)) * h,
    };
  }
  const fixed = value => Number(value).toFixed(1);
  function yTicks(axisY, every) {
    const out = [];
    for (let i = 0; axisY.min + i * axisY.step <= axisY.max + 1e-9; i += every) out.push(axisY.min + i * axisY.step);
    return out;
  }
  function smooth(points) {
    if (points.length < 3) return 'M' + points.map(p => fixed(p[0]) + ',' + fixed(p[1])).join(' L');
    let d = 'M' + fixed(points[0][0]) + ',' + fixed(points[0][1]);
    for (let i = 0; i < points.length - 1; i += 1) {
      const p0 = points[Math.max(i - 1, 0)], p1 = points[i], p2 = points[i + 1], p3 = points[Math.min(i + 2, points.length - 1)];
      d += ' C' + fixed(p1[0] + (p2[0] - p0[0]) / 6) + ',' + fixed(p1[1] + (p2[1] - p0[1]) / 6) + ' ' +
        fixed(p2[0] - (p3[0] - p1[0]) / 6) + ',' + fixed(p2[1] - (p3[1] - p1[1]) / 6) + ' ' + fixed(p2[0]) + ',' + fixed(p2[1]);
    }
    return d;
  }

  /** Gráfico principal (ChartData): zonas, bandas, curvas, pontos, limites e o ponto de operação. */
  function renderMain(model, width, height) {
    if (!model?.ok) return '';
    const p = plot(width, height, model.axisX, model.axisY, { bottom: 30 });
    const out = [];
    out.push('<svg class="ao-svg" data-ao="main" viewBox="0 0 ' + width + ' ' + height + '" role="img" aria-label="AutoCal · Tinj gasolina × MAP">');
    out.push('<defs><clipPath id="aoMainClip"><rect x="' + p.m.left + '" y="' + p.m.top + '" width="' + p.w + '" height="' + p.h + '"/></clipPath></defs>');
    for (const zone of model.zones) {
      const top = p.Y(Math.min(zone.upper, model.axisY.max));
      const bottom = p.Y(Math.max(zone.lower, model.axisY.min));
      out.push('<rect class="ao-zone" data-zone="' + zone.zone + '" data-acquired="' + (zone.acquired === null ? 'unknown' : zone.acquired) +
        '" x="' + p.m.left + '" y="' + fixed(top) + '" width="' + p.w + '" height="' + fixed(Math.max(bottom - top, 0)) + '"/>');
    }
    for (const value of yTicks(model.axisY, 1)) {
      out.push('<line class="ao-grid" x1="' + p.m.left + '" x2="' + (p.m.left + p.w) + '" y1="' + fixed(p.Y(value)) + '" y2="' + fixed(p.Y(value)) + '"/>');
      // Grade de 0,05 em 0,05; número de 0,10 em 0,10 e em 12 px (multimídia: letra legível, sem número em cima de número).
      if (Math.round((value - model.axisY.min) / model.axisY.step) % 2 === 0) out.push('<text class="ao-tick" x="' + (p.m.left - 6) + '" y="' + fixed(p.Y(value) + 4) + '" text-anchor="end">' + value.toFixed(2) + '</text>');
    }
    const xTicks = Math.floor(model.axisX.max);
    for (let value = Math.ceil(model.axisX.min); value <= xTicks; value += 1) {
      out.push('<text class="ao-tick" x="' + fixed(p.X(value)) + '" y="' + (height - 12) + '" text-anchor="middle">' + value + '</text>');
    }
    out.push('<text class="ao-axis-title" x="' + fixed(p.m.left + p.w / 2) + '" y="' + (height - 1) + '" text-anchor="middle">Tempo de injeção na gasolina (ms)</text>');
    out.push('<g clip-path="url(#aoMainClip)">');
    for (const zone of model.zones) {
      // Fronteira de cada zona acima da primeira + rótulo #n/d encostado à direita, sobre a fronteira.
      const y = p.Y(Math.min(zone.upper, model.axisY.max));
      out.push('<line class="ao-zone-edge" x1="' + p.m.left + '" x2="' + (p.m.left + p.w) + '" y1="' + fixed(y) + '" y2="' + fixed(y) + '"/>');
      out.push('<text class="ao-zone-label" data-zone-label="' + zone.zone + '" x="' + (p.m.left + p.w - 6) + '" y="' + fixed(y + 18) + '" text-anchor="end">' + esc(zone.label) + '</text>');
    }
    out.push('<g data-ao-live="band">' + liveBand(model, p) + '</g>');
    for (const [value, cls, name] of [[model.limitMin, 'ao-limit-min', 'min'], [model.limitMax, 'ao-limit-max', 'max']]) {
      if (value !== null) out.push('<line class="' + cls + '" data-limit="' + name + '" x1="' + p.m.left + '" x2="' + (p.m.left + p.w) + '" y1="' + fixed(p.Y(value)) + '" y2="' + fixed(p.Y(value)) + '"/>');
    }
    for (const [curve, cls] of [[model.petrolCurve, 'petrol'], [model.gasCurve, 'gas']]) {
      const visible = curve.filter(node => node.visible).map(node => [p.X(node.x), p.Y(node.y)]);
      if (visible.length > 1) out.push('<path class="ao-curve ' + cls + '" data-series="' + cls + '-curve" d="' + smooth(visible) + '"/>');
      for (const [x, y] of visible) out.push('<path class="ao-marker ' + cls + '" d="' + (cls === 'petrol'
        ? 'M' + fixed(x - 3) + ',' + fixed(y) + ' h6 M' + fixed(x) + ',' + fixed(y - 3) + ' v6'
        : 'M' + fixed(x - 3) + ',' + fixed(y - 3) + ' l6,6 M' + fixed(x + 3) + ',' + fixed(y - 3) + ' l-6,6') + '"/>');
    }
    for (const [points, cls] of [[model.petrolPoints, 'petrol'], [model.gasPoints, 'gas']]) {
      for (const node of points) {
        if (!node.visible) continue;
        // Área de toque de 32 px (boa prática de celular: alvo ≥ 44 px no total com o ponto) + o ponto visível por cima.
        out.push('<circle class="ao-hit" data-series="' + cls + '-point" data-band="' + node.index + '" cx="' + fixed(p.X(node.x)) + '" cy="' + fixed(p.Y(node.y)) + '" r="16"/>');
        out.push('<circle class="ao-point ' + cls + '" data-series="' + cls + '-point" data-band="' + node.index + '" cx="' + fixed(p.X(node.x)) + '" cy="' + fixed(p.Y(node.y)) + '" r="5"/>');
      }
    }
    if (model.ours) out.push(renderOurs(model.ours, p));
    out.push('<g data-ao-live="run">' + liveRun(model, p) + '</g>');
    out.push('</g></svg>');
    return out.join('');
  }

  /** Só no Refino: nossa curva (linha tracejada pelos nossos pontos), nossos pontos (quadrados) e as marcas de engasgo. */
  function renderOurs(ours, p) {
    const out = ['<g class="ao-ours" data-ao-ours="1">'];
    for (const [nodes, cls] of [[ours.petrol, 'petrol'], [ours.gas, 'gas']]) {
      if (nodes.length > 1) out.push('<path class="ao-our-curve ' + cls + '" data-series="our-' + cls + '-curve" d="' + smooth(nodes.map(n => [p.X(n.x), p.Y(n.y)])) + '"/>');
      for (const n of nodes) {
        out.push('<rect class="ao-our-point ' + cls + '" data-series="our-' + cls + '-point" x="' + fixed(p.X(n.x) - 4) + '" y="' + fixed(p.Y(n.y) - 4) + '" width="8" height="8" rx="1.5"/>');
      }
    }
    for (const n of ours.near) {
      const x = p.X(n.x), y = p.Y(n.y);
      out.push('<path class="ao-near-mark" data-series="near" d="M' + fixed(x) + ',' + fixed(y - 7) + ' l7,7 l-7,7 l-7,-7 z"/>');
    }
    for (const n of ours.stalls) {
      const x = p.X(n.x), y = p.Y(n.y);
      out.push('<path class="ao-stall-mark" data-series="stall" d="M' + fixed(x - 6) + ',' + fixed(y - 6) + ' l12,12 M' + fixed(x + 6) + ',' + fixed(y - 6) + ' l-12,12"/>');
    }
    out.push('</g>');
    return out.join('');
  }

  /** Painel Acquisition (ChartPoint): uma coluna de quadrados por combustível, no valor do MAP do buffer. */
  function renderAcquisition(model, width, height) {
    if (!model?.ok) return '';
    const p = plot(width, height, { min: -0.9, max: 0.9 }, model.axisY, { left: 40, right: 6, bottom: 20 });
    const out = [];
    out.push('<svg class="ao-svg" data-ao="acquisition" viewBox="0 0 ' + width + ' ' + height + '" role="img" aria-label="Acquisition">');
    for (const value of yTicks(model.axisY, 1)) {
      out.push('<line class="ao-grid" x1="' + p.m.left + '" x2="' + (p.m.left + p.w) + '" y1="' + fixed(p.Y(value)) + '" y2="' + fixed(p.Y(value)) + '"/>');
      if (Math.round((value - model.axisY.min) / model.axisY.step) % 2 === 0) out.push('<text class="ao-tick" x="' + (p.m.left - 5) + '" y="' + fixed(p.Y(value) + 4) + '" text-anchor="end">' + value.toFixed(2) + '</text>');
    }
    out.push('<text class="ao-axis-title" x="' + fixed(p.m.left + p.w / 2) + '" y="' + (height - 4) + '" text-anchor="middle">Aquisição</text>');
    for (const [squares, cls, x] of [[model.petrolSquares, 'petrol', -0.3], [model.gasSquares, 'gas', 0.3]]) {
      for (const square of squares) {
        if (!square.shown || square.y < model.axisY.min || square.y > model.axisY.max) continue;
        out.push('<rect class="ao-square ' + cls + '" data-band="' + square.band + '" data-count="' + square.count + '" x="' + fixed(p.X(x) - 5) + '" y="' + fixed(p.Y(square.y) - 5) + '" width="10" height="10"/>');
      }
    }
    out.push('<g data-ao-live="acq">' + liveAcq(model, p) + '</g>');
    out.push('</svg>');
    return out.join('');
  }

  /** Curva K (ChartKLine): MUL_ACT por nó, suavizada, só leitura (a ECU é quem a ajusta). */
  function renderK(model, width, height) {
    if (!model?.ok) return '';
    const p = plot(width, height, model.axisX, model.kAxisY, { bottom: 8 });
    const out = [];
    out.push('<svg class="ao-svg" data-ao="k" viewBox="0 0 ' + width + ' ' + height + '" role="img" aria-label="Curva K">');
    for (let value = model.kAxisY.min; value <= model.kAxisY.max + 1e-9; value += model.kAxisY.step) {
      out.push('<line class="ao-grid" x1="' + p.m.left + '" x2="' + (p.m.left + p.w) + '" y1="' + fixed(p.Y(value)) + '" y2="' + fixed(p.Y(value)) + '"/>');
      // Grade de 0,1 em 0,1; rótulo de 0,4 em 0,4 (12 px) para não se sobrepor na altura pequena do painel.
      if (Math.round((value - model.kAxisY.min) / model.kAxisY.step) % 4 === 0) {
        out.push('<text class="ao-tick" x="' + (p.m.left - 6) + '" y="' + fixed(p.Y(value) + 3.5) + '" text-anchor="end">' + value.toFixed(2) + '</text>');
      }
    }
    const nodes = model.kNodes.filter(node => node.y >= model.kAxisY.min && node.y <= model.kAxisY.max && node.x <= model.axisX.max);
    const pts = nodes.map(node => [p.X(node.x), p.Y(node.y)]);
    out.push('<line class="ao-k-ceiling" data-k-ceiling="1.8" x1="' + p.m.left + '" x2="' + (p.m.left + p.w) + '" y1="' + fixed(p.Y(K_WRITE_CEILING)) + '" y2="' + fixed(p.Y(K_WRITE_CEILING)) + '"/>');
    if (pts.length > 1) out.push('<path class="ao-k-line" data-series="k-line" d="' + smooth(pts) + '"/>');
    for (const [index, node] of nodes.entries()) {
      out.push('<circle class="ao-k-node' + (node.y > K_WRITE_CEILING ? ' over' : '') + '" data-node="' + node.index + '" data-value="' + node.y.toFixed(3) + '" cx="' + fixed(pts[index][0]) + '" cy="' + fixed(pts[index][1]) + '" r="3"><title>' + node.y.toFixed(3) + '</title></circle>');
    }
    out.push('<g data-ao-live="k">' + liveK(model, p) + '</g>');
    out.push('</svg>');
    return out.join('');
  }


  // ------------------------------------------------------------------------------------- camada AO VIVO (leve)
  // Dono, 2026-10-13: o cursor e a faixa de MAP atual têm que andar em tempo real. Só estes poucos elementos dependem da
  // telemetria; o resto do gráfico (zonas, curvas, pontos, quadrados) só muda quando a ECU manda dado novo. Por isso o
  // desenho é dividido: o gráfico inteiro é feito uma vez e esta camada é trocada a cada amostra (alguns elementos).
  function liveBand(model, p) {
    if (!model.band) return '';
    const top = p.Y(Math.min(model.band.upper, model.axisY.max));
    const bottom = p.Y(Math.max(model.band.lower, model.axisY.min));
    return '<rect class="ao-band" data-current-band="' + model.band.index + '" x="' + p.m.left + '" y="' + fixed(top) + '" width="' + p.w + '" height="' + fixed(bottom - top) + '"/>';
  }

  function liveRun(model, p) {
    if (!model.run || !(model.run.y >= model.axisY.min && model.run.y <= model.axisY.max)) return '';
    const x = p.X(Math.min(Math.max(model.run.x, model.axisX.min), model.axisX.max));
    const y = p.Y(model.run.y);
    return '<line class="ao-run-axis" x1="' + fixed(x) + '" x2="' + fixed(x) + '" y1="' + p.m.top + '" y2="' + (p.m.top + p.h) + '"/>' +
      '<line class="ao-run-axis" x1="' + p.m.left + '" x2="' + (p.m.left + p.w) + '" y1="' + fixed(y) + '" y2="' + fixed(y) + '"/>' +
      '<path class="ao-run" data-series="run" d="M' + fixed(x) + ',' + fixed(y - 7) + ' l7,7 l-7,7 l-7,-7 z"/>';
  }

  function liveAcq(model, p) {
    if (model.mapNow === null || !(model.mapNow >= model.axisY.min && model.mapNow <= model.axisY.max)) return '';
    return '<line class="ao-run-axis" x1="' + p.m.left + '" x2="' + (p.m.left + p.w) + '" y1="' + fixed(p.Y(model.mapNow)) + '" y2="' + fixed(p.Y(model.mapNow)) + '"/>';
  }

  function liveK(model, p) {
    if (!model.run) return '';
    const x = p.X(Math.min(Math.max(model.run.x, model.axisX.min), model.axisX.max));
    return '<line class="ao-run-axis" x1="' + fixed(x) + '" x2="' + fixed(x) + '" y1="' + p.m.top + '" y2="' + (p.m.top + p.h) + '"/>';
  }

  /** Só o que depende do AGORA (faixa de MAP atual, ponto de operação, MAP agora) para a amostra `live`. */
  function liveState(model, live) {
    const liveMap = finite(live?.mapBar ?? live?.load_bar);
    const livePetrol = finite(live?.petrolMs ?? live?.petrol_ms);
    return {
      band: currentBand(model.thresholds, liveMap),
      run: liveMap !== null && livePetrol !== null ? { x: livePetrol, y: liveMap } : null,
      mapNow: liveMap,
    };
  }

  /** O modelo sem nada do AGORA: serve de chave para saber se o gráfico inteiro precisa ser refeito. */
  function staticModel(model) {
    if (!model) return model;
    return { ...model, band: null, run: null, mapNow: null };
  }

  /** Conteúdo das camadas ao vivo ({band, run, acq, k}) para os mesmos tamanhos do gráfico inteiro. */
  function renderLive(model, sizes) {
    if (!model?.ok) return null;
    const main = plot(sizes.mainWidth, sizes.mainHeight, model.axisX, model.axisY, { bottom: 30 });
    const acq = plot(sizes.sideWidth, sizes.mainHeight, { min: -0.9, max: 0.9 }, model.axisY, { left: 40, right: 6, bottom: 20 });
    const k = plot(sizes.mainWidth, sizes.kHeight, model.axisX, model.kAxisY, { bottom: 8 });
    return { band: liveBand(model, main), run: liveRun(model, main), acq: liveAcq(model, acq), k: liveK(model, k) };
  }

  ns.AutoCalOriginal = { AXIS_Y, K_AXIS_Y, K_WRITE_CEILING, ZONE_EDGES, BANDS, zoneOfBand, build, zoneStates, automatchDots, renderMain, renderAcquisition, renderK, liveState, staticModel, renderLive };
})(typeof window !== 'undefined' ? window : globalThis);
