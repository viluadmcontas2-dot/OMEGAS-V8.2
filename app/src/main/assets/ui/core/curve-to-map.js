(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.OmegasCurveToMap = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  /**
   * "Curva K ⇄ Mapa K" do OMEGAS (decisão do dono, 2026-10-13): o ExportToK do original nunca foi testado e não é usado.
   * Aqui nada grava: o módulo só calcula a PROPOSTA (mapa novo + nós novos da curva); a gravação segue o fluxo manual
   * (prévia, um toque, ACK e leitura de volta). A fórmula de interpolação da curva vem da desmontagem do ExportToK do
   * ProgBase (PROTOCOLO-LANDI doc 26); o restante (refatorar o produto, janela de folga) é do OMEGAS.
   */
  const finite = value => value === null || value === undefined || value === '' || !Number.isFinite(Number(value)) ? null : Number(value);

  // ------------------------------------------------------------------------------------------------------------
  // Rebalanceamento curva K ⇄ Mapa K ("refatoração"): a ECU aplica Mapa K × curva K, então o que importa é o PRODUTO.
  // Em vez de multiplicar a curva no mapa e deixar o mapa estourar (220 × 1,48 = 326 não existe), refatoramos o produto:
  // o mapa leva o máximo que cabe com folga, a curva fica só com o que sobra (neutra quando tudo cabe). Nenhum valor
  // fixo: o mapa e a curva vêm da leitura em tempo real e o resultado se adapta a eles (mapa alto, baixo ou misto).
  // ------------------------------------------------------------------------------------------------------------
  const MAP_MIN = 100;           // piso e teto do byte do Mapa K (fatos do protocolo, os mesmos do editor)
  const MAP_MAX = 255;
  const LOCAL_HEADROOM = 0.15;   // folga para o ajuste local (o mesmo ±15% por gravação do LocalMapPlanner)
  const Q14 = 16384;
  const K_WRITE_CEILING = 1.8;   // teto de escrita da curva (regra do dono; mesmo valor do KFactorProtocol)

  function bracket(xs, x) {
    for (let i = 0; i < xs.length - 1; i += 1) {
      if (xs[i] <= x && x <= xs[i + 1] && xs[i + 1] !== xs[i]) {
        const w = (x - xs[i]) / (xs[i + 1] - xs[i]);
        return { i, wHi: w, wLo: 1 - w };
      }
    }
    return null;
  }

  function interp(xs, ys, x) {
    const b = bracket(xs, x);
    return b ? ys[b.i] * b.wLo + ys[b.i + 1] * b.wHi : null;
  }

  const K_WRITE_FLOOR = 0.6;     // piso de escrita da curva (o mesmo MIN_SAFE_FACTOR do Kotlin)

  /** Mapa K entre linhas: interpolação linear em Tinj (INFERIDO: é como a ECU lê entre linhas; fora delas vale a linha mais próxima). */
  function mapAt(rowsX, valuesByRow, x) {
    if (x <= rowsX[0]) return valuesByRow[0];
    const last = rowsX.length - 1;
    if (x >= rowsX[last]) return valuesByRow[last];
    for (let r = 0; r < last; r += 1) {
      if (rowsX[r] <= x && x <= rowsX[r + 1]) {
        const w = rowsX[r + 1] === rowsX[r] ? 0 : (x - rowsX[r]) / (rowsX[r + 1] - rowsX[r]);
        return valuesByRow[r] * (1 - w) + valuesByRow[r + 1] * w;
      }
    }
    return valuesByRow[last];
  }

  /**
   * Refatora o produto Mapa K × curva K. A ECU aplica o produto; ele precisa continuar o mesmo em cada linha E entre as
   * linhas (nos nós da curva). Multiplicar a curva em cada linha e zerar a curva perde a forma da curva entre as linhas
   * (erro de até ~12% na captura real); aqui os nós da curva ficam com o que as linhas não conseguem representar
   * (poucos %, ou o excedente quando o mapa não tem folga) e as células ficam dentro de uma janela com folga para o
   * ajuste local. Nada é fixo: mapa, curva e eixo vêm da leitura em tempo real.
   *
   * @param {{tbp:number[], k:number[], rowMs:number[], cells:number[][]}} input
   *   tbp/k = curva K lida (30 nós); rowMs = Tinj de cada linha; cells = Mapa K lido (linha × coluna).
   */
  function balance({ tbp = [], k = [], rowMs = [], cells = [], mapMin = MAP_MIN, mapMax = MAP_MAX, headroom = LOCAL_HEADROOM } = {}) {
    const nodes = [];
    for (let i = 0; i < Math.min(tbp.length, k.length); i += 1) nodes.push({ i, x: finite(tbp[i]), y: finite(k[i]) });
    const valid = nodes.filter(n => n.x !== null && n.x > 0 && n.y !== null && n.y > 0);
    const xs = valid.map(n => n.x);
    const ys = valid.map(n => n.y);
    const winLo = mapMin / (1 - headroom);
    const winHi = mapMax / (1 + headroom);

    const rows = rowMs.map((value, row) => {
      const ms = finite(value);
      const line = Array.isArray(cells[row]) ? cells[row].map(finite) : [];
      const base = { row, ms, status: 'ok', kOld: null, kNew: null, cells: [] };
      if (ms === null || !line.length || line.some(v => v === null)) return { ...base, status: 'sem-dados' };
      if (valid.length < 2) return { ...base, status: 'sem-curva' };
      if (ms < xs[0]) return { ...base, status: 'abaixo' };
      if (ms > xs[xs.length - 1]) return { ...base, status: 'acima' };
      const kOld = interp(xs, ys, ms);
      if (kOld === null) return { ...base, status: 'degenerada' };
      return { ...base, kOld };
    });
    const live = rows.filter(r => r.status === 'ok');
    const columns = live.length ? cells[live[0].row].length : 0;
    const rx = live.map(r => r.ms);

    const nextK = ys.slice();
    let inside = [];
    let target = [];            // alvo float por linha viva × coluna
    if (live.length) {
      const first = rx[0];
      const last = rx[rx.length - 1];
      // Tolerância de 0,01 ms: o eixo das linhas vem de inteiros × 0,00256 e cai um fio antes do nó (17,99936 × 18,0).
      inside = valid.map((n, idx) => (n.x >= first - 0.01 && n.x <= last + 0.01 ? idx : -1)).filter(idx => idx >= 0);
      // Produto real que a ECU aplica hoje (por coluna): mapa entre linhas × curva atual.
      const totalAtRow = live.map(r => cells[r.row].map(v => v * r.kOld));
      const totalAtNode = inside.map(idx => Array.from({ length: columns }, (_, c) => mapAt(rx, live.map(r => cells[r.row][c]), xs[idx]) * ys[idx]));
      // Janela de cada célula: a com folga, ampliada só até onde a célula já estava (nunca empurra para mais longe).
      const allowed = live.map(r => cells[r.row].map(v => [Math.max(mapMin, Math.min(winLo, v)), Math.min(mapMax, Math.max(winHi, v))]));
      inside.forEach(idx => { nextK[idx] = 1; });
      target = live.map(() => Array(columns).fill(0));
      for (let pass = 0; pass < 12; pass += 1) {
        live.forEach((r, ri) => {
          const kr = interp(xs, nextK, r.ms);
          for (let c = 0; c < columns; c += 1) target[ri][c] = Math.min(allowed[ri][c][1], Math.max(allowed[ri][c][0], totalAtRow[ri][c] / kr));
        });
        let moved = 0;
        inside.forEach((idx, ni) => {
          let logSum = 0;
          for (let c = 0; c < columns; c += 1) logSum += Math.log(totalAtNode[ni][c] / mapAt(rx, target.map(row => row[c]), xs[idx]));
          const value = Math.exp(logSum / columns);
          moved = Math.max(moved, Math.abs(value - nextK[idx]));
          nextK[idx] = value;
        });
        if (moved < 1e-7) break;
      }
    }
    const ceilingRaw = Math.round(K_WRITE_CEILING * Q14);
    const floorRaw = Math.round(K_WRITE_FLOOR * Q14);
    const realizedRaw = nextK.map((v, idx) => (inside.includes(idx) ? Math.min(ceilingRaw, Math.max(floorRaw, Math.round(v * Q14))) : Math.round(ys[idx] * Q14)));
    const realized = realizedRaw.map(v => v / Q14);

    // Células finais com a curva que de fato vai para a ECU; erros medidos no produto (linhas e nós).
    let saturated = 0;
    let tight = 0;
    let changedCells = 0;
    let maxRowError = 0;
    const finalByRow = [];
    const outRows = rows.map(r => {
      if (r.status !== 'ok') return r;
      const ri = live.indexOf(r);
      const kNew = interp(xs, realized, r.ms);
      const out = cells[r.row].map((current, column) => {
        const f = current * r.kOld;
        const raw = f / kNew;
        // A janela com folga só guia onde a curva absorve; o valor final só é limitado pelo byte do mapa (100–255):
        // precisão do produto vale mais que folga. Célula perto da borda é contada em `tightCells` para a tela avisar.
        const targetValue = Math.min(mapMax, Math.max(mapMin, Math.round(raw)));
        const sat = raw > mapMax + 0.5 || raw < mapMin - 0.5;
        if (sat) saturated += 1;
        if (targetValue > mapMax / (1 + headroom / 2) || targetValue < mapMin / (1 - headroom / 2)) tight += 1;
        if (targetValue !== current) changedCells += 1;
        const error = (targetValue * kNew) / f - 1;
        maxRowError = Math.max(maxRowError, Math.abs(error));
        return { column, current, target: targetValue, errorPct: error * 100, saturated: sat };
      });
      finalByRow[ri] = out.map(c => c.target);
      return { ...r, kNew, cells: out };
    });
    let maxNodeError = 0;
    inside.forEach(idx => {
      for (let c = 0; c < columns; c += 1) {
        const before = mapAt(rx, live.map(r => cells[r.row][c]), xs[idx]) * ys[idx];
        const after = mapAt(rx, finalByRow.map(row => row[c]), xs[idx]) * realized[idx];
        maxNodeError = Math.max(maxNodeError, Math.abs(after / before - 1));
      }
    });

    const curveNodes = valid.map((n, idx) => ({
      index: n.i, ms: n.x, current: n.y, target: realized[idx],
      currentRaw: Math.round(n.y * Q14), targetRaw: realizedRaw[idx],
      changed: Math.round(n.y * Q14) !== realizedRaw[idx],
    }));
    const share = inside.length ? Math.max(...inside.map(idx => Math.abs(realized[idx] - 1))) : 0;
    return {
      rows: outRows,
      nodes: curveNodes,
      summary: {
        rowsUsed: live.length,
        rowsSkipped: rows.length - live.length,
        cellsChanged: changedCells,
        nodesChanged: curveNodes.filter(n => n.changed).length,
        saturatedCells: saturated,
        tightCells: tight,
        maxRowErrorPct: maxRowError * 100,
        maxNodeErrorPct: maxNodeError * 100,
        curveShare: share,
      },
    };
  }

  /** O que multiplicar célula × K da linha e zerar a curva faria (o ExportToK do original, só para comparar): erro nos nós. */
  function naiveError({ tbp, k, rowMs, cells }) {
    const xs = tbp.slice();
    const live = rowMs.map((ms, row) => ({ ms, row, kOld: interp(xs, k, ms) })).filter(r => r.kOld !== null);
    const rx = live.map(r => r.ms);
    let worst = 0;
    const columns = cells[live[0].row].length;
    xs.forEach((x, idx) => {
      if (x < rx[0] || x > rx[rx.length - 1]) return;
      for (let c = 0; c < columns; c += 1) {
        const before = mapAt(rx, live.map(r => cells[r.row][c]), x) * k[idx];
        const after = mapAt(rx, live.map(r => cells[r.row][c] * r.kOld), x);
        worst = Math.max(worst, Math.abs(after / before - 1));
      }
    });
    return worst * 100;
  }

  return { balance, naiveError, interp, bracket, mapAt, MAP_MIN, MAP_MAX, LOCAL_HEADROOM, K_WRITE_CEILING, K_WRITE_FLOOR };
});
