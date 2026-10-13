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

  // ------------------------------------------------------------------------------------------------------------
  // Suavização da curva ANTES de passar para o mapa (dono, 2026-10-13): a curva nativa do AutoMatch sai serrilhada e com
  // pontos extremos (nas 57 curvas reais das sessões do dono: pulo máximo entre vizinhos mediana 17,7%, p90 37%; ex.:
  // 1,50 → 0,89 → 1,50 em nós vizinhos). Exportar assim levaria o serrilhado para o mapa. Alisador de Whittaker em ln(K)
  // sobre Petrol Inj. (penalidade na 2ª derivada, passo não uniforme) com pesos robustos (pontos extremos pesam pouco).
  // Medido nas 57 curvas: serrilhado mediano 17,7% → ≤1,3%, pior caso 3,4%; desvio RMS mediano 3,1% (concentrado nos extremos).
  // ------------------------------------------------------------------------------------------------------------
  const SMOOTH_LAMBDA = 1;
  const SMOOTH_ROBUST = 0.04;     // resíduo (em ln) a partir do qual um ponto passa a pesar pouco
  const SMOOTH_DEADBAND = 0.005;  // mudança menor que 0,5% não vale a pena: mantém o valor da ECU

  function solveDense(A, b) {
    const n = b.length;
    for (let i = 0; i < n; i += 1) {
      let p = i;
      for (let r = i + 1; r < n; r += 1) if (Math.abs(A[r][i]) > Math.abs(A[p][i])) p = r;
      [A[i], A[p]] = [A[p], A[i]]; [b[i], b[p]] = [b[p], b[i]];
      for (let r = i + 1; r < n; r += 1) {
        const f = A[r][i] / A[i][i];
        for (let c = i; c < n; c += 1) A[r][c] -= f * A[i][c];
        b[r] -= f * b[i];
      }
    }
    const x = Array(n).fill(0);
    for (let i = n - 1; i >= 0; i -= 1) {
      let sum = b[i];
      for (let c = i + 1; c < n; c += 1) sum -= A[i][c] * x[c];
      x[i] = sum / A[i][i];
    }
    return x;
  }

  /** Devolve a curva suavizada (mesmo tamanho). xs crescente (ms), ys > 0. Nunca devolve fora de [piso, teto] de escrita. */
  function smoothCurve(xs, ys, { lambda = SMOOTH_LAMBDA, robust = SMOOTH_ROBUST, deadband = SMOOTH_DEADBAND } = {}) {
    const n = ys.length;
    if (n < 4 || xs.some((x, i) => i > 0 && !(x > xs[i - 1]))) return ys.slice();
    const y = ys.map(Math.log);
    let w = Array(n).fill(1);
    let z = y.slice();
    for (let iteration = 0; iteration < 8; iteration += 1) {
      const A = Array.from({ length: n }, () => Array(n).fill(0));
      const b = Array(n).fill(0);
      for (let i = 0; i < n; i += 1) { A[i][i] += w[i]; b[i] += w[i] * y[i]; }
      for (let i = 1; i < n - 1; i += 1) {
        const h0 = xs[i] - xs[i - 1];
        const h1 = xs[i + 1] - xs[i];
        const co = [2 / (h0 * (h0 + h1)), -2 / (h0 * h1), 2 / (h1 * (h0 + h1))];
        const scale = ((h0 + h1) / 2) ** 2;
        const idx = [i - 1, i, i + 1];
        for (let a = 0; a < 3; a += 1) for (let q = 0; q < 3; q += 1) A[idx[a]][idx[q]] += lambda * co[a] * co[q] * scale;
      }
      z = solveDense(A, b);
      w = z.map((value, i) => 1 / (1 + (Math.abs(y[i] - value) / robust) ** 2));
    }
    return z.map((value, i) => {
      if (Math.abs(value - y[i]) < deadband) return ys[i];
      return Math.min(K_WRITE_CEILING, Math.max(K_WRITE_FLOOR, Math.exp(value)));
    });
  }

  /** Maior pulo (em %) da 2ª diferença de ln(K) entre vizinhos: mede serrilhado. */
  function roughness(ys) {
    const y = ys.map(Math.log);
    let max = 0;
    for (let i = 1; i < y.length - 1; i += 1) max = Math.max(max, Math.abs(y[i - 1] - 2 * y[i] + y[i + 1]));
    return max * 100;
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
  function balance({ tbp = [], k = [], rowMs = [], cells = [], mapMin = MAP_MIN, mapMax = MAP_MAX, headroom = LOCAL_HEADROOM, smooth = true, refine = null } = {}) {
    const nodes = [];
    for (let i = 0; i < Math.min(tbp.length, k.length); i += 1) nodes.push({ i, x: finite(tbp[i]), y: finite(k[i]) });
    const valid = nodes.filter(n => n.x !== null && n.x > 0 && n.y !== null && n.y > 0);
    const xs = valid.map(n => n.x);
    const ys = valid.map(n => n.y);
    // Refino (dono, 2026-10-13: "a exportação já sai refinada, sem esperar adquirir tudo de novo"): onde o Refino já tem um alvo
    // validado para o nó (`refine[índice]`, fator K), a curva-base é a refinada; o resto segue a da ECU.
    const yr = Array.isArray(refine) ? valid.map(n => (finite(refine[n.i]) !== null && refine[n.i] > 0 ? refine[n.i] : n.y)) : ys;
    // Curva ALVO: a (refinada e) suavizada — o produto que o mapa e a curva novos têm que reproduzir. Sem suavizar, é a base.
    const ysT = smooth ? smoothCurve(xs, yr) : yr.slice();
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
      const kOld = interp(xs, ysT, ms);
      if (kOld === null) return { ...base, status: 'degenerada' };
      return { ...base, kOld, kEcu: interp(xs, ys, ms) };
    });
    const live = rows.filter(r => r.status === 'ok');
    const columns = live.length ? cells[live[0].row].length : 0;
    const rx = live.map(r => r.ms);

    const nextK = ysT.slice();
    let inside = [];
    let target = [];            // alvo float por linha viva × coluna
    if (live.length) {
      // TODOS os nós absorvem, também os fora do alcance das linhas: o Mapa K vale a linha da ponta ali (INFERIDO), então
      // quando a célula da ponta muda o nó de fora tem que compensar, senão o produto muda (erro de ~20% medido em curva
      // com K ≠ 1 nas pontas) e a curva gravada fica com degrau entre o nó de dentro e o de fora.
      inside = valid.map((_, idx) => idx);
      // Produto real que a ECU aplica hoje (por coluna): mapa entre linhas × curva atual.
      const totalAtRow = live.map(r => cells[r.row].map(v => v * r.kOld));
      const totalAtNode = inside.map(idx => Array.from({ length: columns }, (_, c) => mapAt(rx, live.map(r => cells[r.row][c]), xs[idx]) * ysT[idx]));
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
    const realizedRaw = nextK.map((v, idx) => (inside.includes(idx) ? Math.min(ceilingRaw, Math.max(floorRaw, Math.round(v * Q14))) : Math.round(ysT[idx] * Q14)));
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
        const before = mapAt(rx, live.map(r => cells[r.row][c]), xs[idx]) * ysT[idx];
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
    const smoothDev = yr.map((v, i) => Math.abs(Math.log(ysT[i] / v)) * 100);
    const refinedNodes = yr.filter((v, i) => v !== ys[i]).length;
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
        smoothed: smooth,
        roughnessBefore: roughness(ys),
        roughnessAfter: roughness(ysT),
        refinedNodes,
        smoothedNodes: smoothDev.filter(d => d > 0.01).length,
        smoothMaxChangePct: smoothDev.length ? Math.max(...smoothDev) : 0,
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

  /**
   * Alvos do Refino por nó (fator K) a partir da proposta do piloto (Kotlin). Só vale para o nó cujo valor atual na ECU ainda é
   * EXATAMENTE o que o piloto usou (`currentRaw`); proposta velha (a curva mudou) é ignorada: nada de misturar gerações.
   * @param points curva lida agora [{index, factorRaw}], @param proposal [{index, currentRaw, targetRaw}]
   */
  function refinementTargets(points, proposal) {
    const refine = Array(30).fill(null);
    let applied = 0;
    let stale = 0;
    const raw = new Map((points || []).map((p, i) => [Number(p.index ?? i), finite(p.factorRaw)]));
    for (const item of Array.isArray(proposal) ? proposal : []) {
      const index = Number(item?.index);
      const target = finite(item?.targetRaw);
      if (!Number.isInteger(index) || index < 0 || index > 29 || target === null || target <= 0) continue;
      if (raw.get(index) !== finite(item.currentRaw)) { stale += 1; continue; }
      refine[index] = target / Q14;
      applied += 1;
    }
    return { refine, applied, stale };
  }

  return { balance, naiveError, refinementTargets, smoothCurve, roughness, interp, bracket, mapAt, MAP_MIN, MAP_MAX, LOCAL_HEADROOM, K_WRITE_CEILING, K_WRITE_FLOOR };
});
