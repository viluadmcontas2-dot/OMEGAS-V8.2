(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Equivalência Refinada: fluxo guiado Coletar → Revisar → Gravar e verificar.
  // A matemática é do Kotlin (AutoMatchRefinedEngine); esta camada só apresenta,
  // conduz a revisão humana e usa o caminho de escrita existente (leitura → conferência
  // → gravação → readback). Nada é gravado sem o toque de confirmação.

  const ORIGIN = {
    MEASURED: { label: 'Medido', tone: 'ok' },
    BLENDED: { label: 'Transição', tone: 'accent' },
    SMOOTHED: { label: 'Suavizado', tone: 'warn' },
    HELD: { label: 'Mantido', tone: 'muted' },
  };
  const POLL_MS = 300;
  const OPERATION_TIMEOUT_MS = 90000;

  function finite(value) {
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  }
  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[char]));
  }
  function fmt(value, digits) {
    const number = finite(value);
    return number === null ? '—' : number.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits });
  }
  function pct(value, digits = 1) {
    const number = finite(value);
    return number === null ? '—' : `${number > 0 ? '+' : ''}${fmt(number, digits)}%`;
  }

  /** Estado humano do fluxo a partir da análise Kotlin (puro, testável). */
  function deriveFlow(analysis, operation) {
    const a = analysis || {};
    const op = operation || {};
    if (op.phase === 'writing' || op.phase === 'reading') return { step: 4, state: 'busy' };
    if (op.phase === 'done') return { step: 4, state: 'done' };
    if (op.phase === 'failed') return { step: 4, state: 'problem' };
    if (!a.available) return { step: 1, state: 'waiting' };
    if (a.refinementMode !== 'EQUIVALENCE') {
      return { step: 2, state: Number(a.changedCount) > 0 ? 'polish' : 'collecting' };
    }
    return { step: 3, state: Number(a.changedCount) > 0 ? 'ready' : 'aligned' };
  }

  /** Mensagem principal: normalidade compacta, problema ganha espaço. */
  function headline(analysis, flow) {
    const a = analysis || {};
    if (flow.state === 'busy') return { tone: 'accent', title: 'Gravando e conferindo na ECU', text: 'Não desligue o motor nem desconecte o cabo.' };
    if (flow.state === 'done') return { tone: 'ok', title: 'Curva gravada e conferida', text: 'Agora recolete o GNV rodando normalmente para medir o novo resultado.' };
    if (flow.state === 'problem') return { tone: 'danger', title: 'A gravação não foi confirmada', text: 'A ECU manteve a curva anterior ou o readback divergiu. Nada foi dado como sucesso.' };
    if (!a.available) return { tone: 'muted', title: 'Aguardando leitura da ECU', text: a.message || 'Conecte a ECU; o snapshot AutoCal chega sozinho em alguns segundos.' };
    const mature = finite(a.matureCommonPoints) ?? 0;
    const need = finite(a.minimumMatureCommonPoints) ?? 4;
    if (a.refinementMode !== 'EQUIVALENCE') {
      return Number(a.changedCount) > 0
        ? { tone: 'warn', title: 'Curva atual tem degraus que causam trancos', text: `Dá para suavizar já. Para a equivalência completa faltam ${Math.max(0, need - mature)} faixa(s) de carga com gasolina e GNV.` }
        : { tone: 'muted', title: 'Coletando gasolina e GNV', text: `Faixas comparáveis: ${mature} de ${need} necessárias. Rode variando a carga com o motor quente.` };
    }
    if (Number(a.changedCount) === 0) return { tone: 'ok', title: 'GNV equivalente à gasolina', text: 'A curva atual já está dentro da tolerância medida.' };
    return { tone: 'accent', title: 'Curva refinada pronta para revisão', text: `${a.changedCount} ponto(s) ajustados com evidência de ${mature} faixa(s) comparáveis.` };
  }

  function chartSvg(points) {
    const items = (points || []).filter(p => finite(p.referenceTimeMs) !== null);
    if (!items.length) return '';
    const width = 640;
    const height = 190;
    const pad = { l: 38, r: 10, t: 12, b: 24 };
    const xs = items.map(p => Math.log(p.referenceTimeMs));
    const values = items.flatMap(p => [finite(p.currentFactor), finite(p.calculatedFactor)]).filter(v => v !== null);
    const min = Math.min(...values) * 0.98;
    const max = Math.max(...values) * 1.02;
    const x = v => pad.l + (v - xs[0]) / (xs[xs.length - 1] - xs[0]) * (width - pad.l - pad.r);
    const y = v => pad.t + (1 - (v - min) / (max - min || 1)) * (height - pad.t - pad.b);
    const line = key => items.map((p, i) => `${i ? 'L' : 'M'}${x(xs[i]).toFixed(1)},${y(p[key]).toFixed(1)}`).join('');
    const ticks = [0.5, 1, 2, 4, 8, 16].filter(t => Math.log(t) >= xs[0] && Math.log(t) <= xs[xs.length - 1]);
    const grid = ticks.map(t => `<line x1="${x(Math.log(t))}" x2="${x(Math.log(t))}" y1="${pad.t}" y2="${height - pad.b}" class="grid"/><text x="${x(Math.log(t))}" y="${height - 6}" class="tick">${t} ms</text>`).join('');
    const yTicks = [min, (min + max) / 2, max].map(v => `<text x="4" y="${y(v) + 3}" class="tick">${fmt(v, 2)}</text>`).join('');
    const dots = items.map((p, i) => `<circle cx="${x(xs[i]).toFixed(1)}" cy="${y(p.calculatedFactor).toFixed(1)}" r="3" data-origin="${escapeHtml(p.origin)}"><title>${fmt(p.referenceTimeMs, 2)} ms · ${fmt(p.currentFactor, 3)} → ${fmt(p.calculatedFactor, 3)} (${escapeHtml(ORIGIN[p.origin]?.label || p.origin)})</title></circle>`).join('');
    return `<svg viewBox="0 0 ${width} ${height}" class="refine-chart" role="img" aria-label="Curva K atual e refinada por tempo de injeção">${grid}${yTicks}<path d="${line('currentFactor')}" class="current"/><path d="${line('calculatedFactor')}" class="refined"/>${dots}</svg>`;
  }

  class AutoCalRefinePanel {
    constructor(host, app, api) {
      this.host = host;
      this.app = app;
      this.store = app.store;
      this.api = api;
      this.native = app.api;
      this.analysis = null;
      this.operation = { phase: 'idle' };
      this.lastApplied = null;
      this.reviewOpen = false;
      this.host.addEventListener('click', event => this.onClick(event));
    }

    refresh() {
      if (this.operation.phase === 'reading' || this.operation.phase === 'writing') return;
      this.analysis = this.api?.refinedAnalysis?.() || null;
      this.render();
    }

    onClick(event) {
      if (event.target.closest('[data-refine-review]')) this.openReview();
      if (event.target.closest('[data-refine-cancel]')) this.closeReview();
      if (event.target.closest('[data-refine-apply]')) this.apply();
      if (event.target.closest('[data-refine-restore]')) this.restore();
      if (event.target.closest('[data-refine-dismiss]')) { this.operation = { phase: 'idle' }; this.refresh(); }
    }

    changedPoints() {
      return (this.analysis?.points || []).filter(p => Number(p.calculatedRaw) !== Number(p.currentRaw) && p.origin !== 'HELD');
    }

    openReview() {
      const draft = this.api.createRefinedDraft();
      if (!draft?.ok) { this.alert(draft?.error || 'Não foi possível preparar a revisão.'); return; }
      const review = this.api.draftReview();
      if (!review?.ok || !Array.isArray(review.points) || !review.points.length) {
        this.alert(review?.error || 'Nenhum ponto para gravar.');
        return;
      }
      this.review = review;
      this.reviewOpen = true;
      this.render();
      this.host.querySelector?.('.refine-review')?.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' });
    }

    closeReview() {
      this.reviewOpen = false;
      this.review = null;
      this.api?.clearDraft?.();
      this.render();
    }

    apply() {
      const points = (this.review?.points || []).map(p => ({ index: Number(p.index), currentRaw: Number(p.currentRaw), targetRaw: Number(p.targetRaw) }));
      if (!points.length) return;
      this.reviewOpen = false;
      this.runWrite(points, 'Equivalência refinada OMEGAS confirmada no AutoCal', true);
    }

    restore() {
      if (!this.lastApplied?.length) return;
      const points = this.lastApplied.map(p => ({ index: p.index, currentRaw: p.targetRaw, targetRaw: p.currentRaw }));
      this.runWrite(points, 'Restaurar curva anterior (antes da equivalência refinada)', false);
    }

    /** Lê a curva nesta conexão, confere com o snapshot e só então grava (ACK + readback no Kotlin). */
    runWrite(points, reason, remember) {
      const read = this.native.startCurveRead();
      if (!read?.ok || !read?.started) { this.fail(read?.error || 'A leitura da Curva K não iniciou.'); return; }
      this.operation = { phase: 'reading', startedAt: Date.now() };
      this.render();
      this.poll(result => {
        const factors = Array.isArray(result.factorsRaw) ? result.factorsRaw.map(Number) : null;
        if (!factors || factors.length !== 30) { this.fail('A ECU não devolveu os 30 pontos da Curva K.'); return; }
        const stale = points.find(p => factors[p.index] !== p.currentRaw);
        if (stale) { this.fail('A curva da ECU mudou desde o snapshot AutoCal. Aguarde um novo snapshot e revise de novo.'); return; }
        const write = this.native.writeCurve(points, reason);
        if (!write?.ok || !write?.started) { this.fail(write?.error || 'A gravação não iniciou.'); return; }
        this.operation = { phase: 'writing', startedAt: Date.now(), total: points.length };
        this.render();
        this.poll(done => {
          if (done.state === 'BATCH_CONFIRMED' && done.readbackValid === true) {
            this.lastApplied = remember ? points : null;
            this.operation = { phase: 'done', restored: !remember };
            this.api?.clearDraft?.();
            this.render();
          } else {
            this.fail(done.error || 'Readback não confirmou a gravação.');
          }
        });
      });
    }

    poll(onFinish) {
      const started = Date.now();
      const tick = () => {
        const status = this.native.curveOperation() || {};
        if (status.busy === true || /QUEUED|READING|WRITING/.test(String(status.state || ''))) {
          if (Date.now() - started > OPERATION_TIMEOUT_MS) { this.fail('Tempo limite aguardando a ECU.'); return; }
          if (this.operation.phase === 'writing') this.operation.progress = finite(status.progress);
          this.render();
          root.setTimeout(tick, POLL_MS);
          return;
        }
        if (status.ok === false || /FAILED|TIMEOUT/.test(String(status.state || ''))) { this.fail(status.error || 'A ECU recusou a operação.'); return; }
        onFinish(status);
      };
      root.setTimeout(tick, POLL_MS);
    }

    fail(message) {
      this.operation = { phase: 'failed', message: String(message || 'Falha') };
      this.render();
    }

    alert(message) { this.store.patch({ alert: { level: 'warning', message: String(message) } }); }

    render() {
      const a = this.analysis || {};
      const flow = deriveFlow(a, this.operation);
      const head = headline(a, flow);
      const before = a.metricsBefore || {};
      const after = a.metricsAfter || {};
      const changed = this.changedPoints();
      const roughCut = finite(before.roughness) && finite(after.roughness) !== null && before.roughness > 0
        ? Math.round((1 - after.roughness / before.roughness) * 100) : null;
      const riskBefore = finite(before.maxElasticity);
      const riskAfter = finite(after.maxElasticity);
      const risk = v => v === null ? 'muted' : v > 1 ? 'danger' : v > 0.5 ? 'warn' : 'ok';
      const steps = [
        ['Gasolina', 'faixas coletadas'],
        ['GNV', 'mesmas faixas no GNV'],
        ['Revisar', 'curva refinada'],
        ['Gravar', 'conferir na ECU'],
      ].map(([title, sub], i) => {
        const n = i + 1;
        const state = n < flow.step ? 'done' : n === flow.step ? 'active' : 'pending';
        return `<li data-state="${state}"><i>${state === 'done' ? '✓' : n}</i><div><b>${title}</b><span>${sub}</span></div></li>`;
      }).join('');
      const op = this.operation;
      const progress = op.phase === 'writing' && finite(op.progress) !== null ? `<i class="refine-progress" style="--progress:${Math.max(0, Math.min(100, op.progress))}%"></i>` : '';
      const primary = (() => {
        if (op.phase === 'reading' || op.phase === 'writing') return `<button type="button" disabled>${op.phase === 'reading' ? 'Conferindo a curva da ECU…' : 'Gravando…'}</button>${progress}`;
        if (op.phase === 'done') {
          return op.restored
            ? '<button type="button" data-refine-dismiss class="secondary">Ok</button>'
            : '<span class="refine-done">✓ Gravada e conferida</span><button type="button" data-refine-restore class="secondary">Restaurar curva anterior</button><button type="button" data-refine-dismiss class="secondary">Ok</button>';
        }
        if (op.phase === 'failed') return `<span class="refine-error">${escapeHtml(op.message)}</span><button type="button" data-refine-dismiss class="secondary">Entendi</button>`;
        if (!a.available || !changed.length || this.reviewOpen) return '';
        return `<button type="button" data-refine-review class="primary">Revisar e aplicar ${changed.length} ponto${changed.length === 1 ? '' : 's'}</button>`;
      })();
      const legend = Object.entries(ORIGIN).map(([key, o]) => `<span data-origin="${key}">${o.label}</span>`).join('');
      const review = this.reviewOpen && this.review ? `<div class="refine-review" role="dialog" aria-label="Revisão da curva refinada">
          <header><div><small>REVISÃO · GRAVAÇÃO NA ECU</small><h4>${this.review.points.length} ponto(s) da Curva K</h4></div><button type="button" data-refine-cancel class="icon-close" aria-label="Cancelar">×</button></header>
          <div class="refine-review-list">${this.review.points.map(p => {
            const point = (a.points || [])[p.index] || {};
            const delta = finite(p.currentFactor) ? (p.targetFactor / p.currentFactor - 1) * 100 : null;
            return `<div><span>${fmt(point.referenceTimeMs, 1)} ms</span><b>${fmt(p.currentFactor, 3)} → ${fmt(p.targetFactor, 3)}</b><small data-origin="${escapeHtml(point.origin)}">${pct(delta)} · ${escapeHtml(ORIGIN[point.origin]?.label || '')}</small></div>`;
          }).join('')}</div>
          <p class="refine-contract"><b>Ainda nada foi enviado.</b> O app relê a curva, confere que nada mudou, grava ponto a ponto e confirma por readback. A curva anterior fica guardada para restaurar.</p>
          <div class="operation-actions"><button type="button" data-refine-cancel class="secondary">Cancelar</button><button type="button" data-refine-apply class="danger-primary">Gravar na ECU</button></div>
        </div>` : '';
      const targets = Array.isArray(a.targets) ? a.targets : [];
      const rejected = Array.isArray(a.rejectedBands) ? a.rejectedBands : [];
      this.host.innerHTML = `
        <section class="refine-card" data-tone="${head.tone}">
          <header class="refine-head">
            <div><small>EQUIVALÊNCIA GNV = GASOLINA</small><h3>${escapeHtml(head.title)}</h3><p>${escapeHtml(head.text)}</p></div>
            <ol class="refine-steps">${steps}</ol>
          </header>
          ${a.available ? `<div class="refine-body">
            <div class="refine-chart-wrap">${chartSvg(a.points)}<div class="refine-legend"><span class="current">Atual</span><span class="refined">Refinada</span>${legend}</div></div>
            <dl class="refine-metrics">
              <div><dt>Risco de tranco</dt><dd><b data-tone="${risk(riskBefore)}">${fmt(riskBefore, 2)}</b> → <b data-tone="${risk(riskAfter)}">${fmt(riskAfter, 2)}</b></dd><span>inclinação máx. da curva (seguro ≤ ${fmt(a.guards?.maximumElasticity, 2)})</span></div>
              <div><dt>Serrilhado</dt><dd>${roughCut === null ? '—' : `−${roughCut}%`}</dd><span>rugosidade removida</span></div>
              <div><dt>Maior degrau</dt><dd>${fmt((before.maxNeighborStep || 0) * 100, 1)}% → ${fmt((after.maxNeighborStep || 0) * 100, 1)}%</dd><span>entre pontos vizinhos</span></div>
              <div><dt>Evidência</dt><dd>${fmt(a.matureCommonPoints, 0)} faixas</dd><span>gasolina × GNV comparáveis</span></div>
            </dl>
          </div>` : ''}
          ${a.needsAnotherPass ? '<p class="refine-note">A curva atual está muito serrilhada: esta passada respeita o limite de ±15% e uma segunda passada termina o ajuste.</p>' : ''}
          <div class="refine-actions">${primary}</div>
          ${review}
          ${a.available ? `<details class="refine-details"><summary>Detalhes técnicos</summary>
            <p>${escapeHtml(a.algorithm || '')} · modo ${escapeHtml(a.refinementMode || '')} · buffers ${a.buffersCoherent ? 'coerentes' : 'incoerentes'} · trava ±${fmt(a.guards?.maximumStepPercent, 0)}%/execução, |d ln K / d ln t| ≤ ${fmt(a.elasticityLimit, 2)}</p>
            ${targets.length ? `<table><thead><tr><th>MAP</th><th>T gasolina</th><th>T no GNV</th><th>Razão</th><th>Peso</th><th>K alvo</th></tr></thead><tbody>${targets.map(t => `<tr${finite(t.robustWeight) !== null && t.robustWeight < 0.3 ? ' class="discounted"' : ''}><td>${fmt(t.mapBar, 3)}</td><td>${fmt(t.petrolMs, 2)}</td><td>${fmt(t.gasMs, 2)}</td><td>${fmt(t.ratio, 3)}</td><td>${fmt(t.weight, 2)}</td><td>${fmt(t.targetFactor, 3)}</td></tr>`).join('')}</tbody></table>` : ''}
            ${rejected.length ? `<p>Bandas descartadas por fugirem da curva física: ${rejected.map(r => `${escapeHtml(r.fuel)} B${Number(r.band) + 1} (${fmt(r.mapBar, 2)} bar, ${fmt(r.timeMs, 2)} ms)`).join(' · ')}</p>` : ''}
          </details>` : ''}
        </section>`;
    }
  }

  ns.AutoCalRefinePanel = AutoCalRefinePanel;
  ns.AutoCalRefineModel = { deriveFlow, headline };
})(typeof window !== 'undefined' ? window : globalThis);
