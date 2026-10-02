(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Equivalência Refinada: fluxo guiado Coletar → Revisar → Gravar e verificar.
  // A matemática é do Kotlin (AutoMatchRefinedEngine); esta camada só apresenta,
  // conduz a revisão humana e usa o caminho de escrita existente (leitura → conferência
  // → gravação → readback). Nada é gravado sem o toque de confirmação.

  const ORIGIN = {
    MEASURED: { label: 'Medido', tone: 'ok' },
    SUPPORTED: { label: 'Medido', tone: 'ok' },
    BLENDED: { label: 'Transição', tone: 'accent' },
    SMOOTHED: { label: 'Anti-tranco', tone: 'warn' },
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

  // Piloto do refino (Kotlin RefinementAutopilot): ECU no automático → nossos pontos → refino → verificação → estável.
  const PILOT = [
    ['ECU_TRABALHANDO', 'ECU no automático'],
    ['COLETANDO_NOSSOS', 'Nossos pontos'],
    ['PROPOSTA_PRONTA', 'Refino'],
    ['VERIFICANDO', 'Verificação'],
    ['ESTAVEL', 'Estável'],
  ];
  const PILOT_TONE = { SEM_ECU: 'muted', ECU_TRABALHANDO: 'muted', COLETANDO_NOSSOS: 'accent', PROPOSTA_PRONTA: 'accent', VERIFICANDO: 'accent', RESTAURAR_TRECHO: 'danger', ESTAVEL: 'ok' };
  const VERDICT = {
    CONFIRMADA: ['chegou na gasolina', 'ok'],
    PASSOU: ['passou do ponto · próxima mais suave', 'warn'],
    CURTA: ['faltou · próxima mais firme', 'accent'],
    PIOROU: ['piorou', 'danger'],
    COLETANDO: ['medindo…', 'muted'],
    SEM_ANTES: ['sem medição anterior', 'muted'],
  };
  const JOURNAL_STATUS = { VERIFICANDO: 'Verificando a curva gravada', VERIFICADO: 'Curva gravada verificada', PIOROU_EM_PARTE: 'Um trecho piorou', INTERROMPIDO: 'Verificação interrompida (outra alteração na ECU)' };

  /** Pontos para desfazer a última gravação inteira (antes ← depois), a partir do diário. */
  function undoPoints(latest) {
    const before = latest?.beforeRaw;
    const after = latest?.afterRaw;
    if (!Array.isArray(before) || !Array.isArray(after) || before.length !== 30 || after.length !== 30) return [];
    const out = [];
    for (let i = 0; i < 30; i += 1) if (Number(before[i]) !== Number(after[i])) out.push({ index: i, currentRaw: Number(after[i]), targetRaw: Number(before[i]) });
    return out;
  }

  function pilotHtml(eq) {
    const pilot = eq?.autopilot;
    if (!pilot?.phase) return '';
    const phase = pilot.phase === 'RESTAURAR_TRECHO' ? 'VERIFICANDO' : pilot.phase;
    const at = PILOT.findIndex(([key]) => key === phase);
    const steps = PILOT.map(([key, label], i) => {
      const state = at < 0 ? 'pending' : i < at ? 'done' : i === at ? 'active' : 'pending';
      return `<li data-state="${state}" data-phase="${key}">${escapeHtml(label)}</li>`;
    }).join('');
    const count = finite(pilot.autoMatchCount);
    const max = finite(pilot.maxAutomatch);
    const facts = [
      count !== null ? `automático ECU ${count}${max !== null ? `/${max}` : ''}` : null,
      `bandas ECU gasolina ${fmt(pilot.petrolValid, 0)}/18 · GNV ${fmt(pilot.gasValid, 0)}/18`,
      `${fmt(pilot.ourPoints, 0)} pontos nossos (RPM×MAP)`,
    ].filter(Boolean).join(' · ');
    return `<div class="refine-pilot" data-tone="${PILOT_TONE[pilot.phase] || 'muted'}" data-phase="${escapeHtml(pilot.phase)}">
        <ol class="refine-pilot-steps">${steps}</ol>
        <p><b>${escapeHtml(pilot.headline || '')}</b> ${escapeHtml(pilot.next || '')}</p>
        <details class="pilot-details"><summary>Detalhes técnicos</summary><small>${escapeHtml(facts)}</small></details>${pilot.canDisconnect ? '<b class="disconnect-ready">Pode desconectar</b>' : ''}
      </div>`;
  }

  function journalHtml(eq) {
    const latest = eq?.refinement?.latest;
    if (!latest || !latest.status) return '';
    const bands = (Array.isArray(latest.bands) ? latest.bands : []).filter(b => VERDICT[b.verdict]);
    const rows = bands.map(b => {
      const [label, tone] = VERDICT[b.verdict];
      const before = finite(b.ratioBefore);
      const after = finite(b.ratioAfter);
      const ratio = before === null ? '' : `${pct((before - 1) * 100)}${after === null ? '' : ` → ${pct((after - 1) * 100)}`}`;
      return `<div><span>${fmt(b.fromMs, 1)}–${fmt(b.toMs, 1)} ms</span><b>${ratio}</b><small data-tone="${tone}">${label}</small></div>`;
    }).join('');
    const restore = Array.isArray(eq.restorePoints) && eq.restorePoints.length
      ? `<button type="button" data-refine-restore-band class="danger-primary">Restaurar trecho que piorou (${eq.restorePoints.length} ponto${eq.restorePoints.length === 1 ? '' : 's'})</button>` : '';
    const undo = undoPoints(latest).length ? '<button type="button" data-refine-undo class="secondary">Desfazer última gravação</button>' : '';
    return `<div class="refine-journal" data-status="${escapeHtml(latest.status)}">
        <header><small>DEPOIS DA GRAVAÇÃO · GNV ÷ GASOLINA POR FAIXA</small><b>${escapeHtml(JOURNAL_STATUS[latest.status] || latest.status)}</b></header>
        ${rows ? `<div class="refine-journal-bands">${rows}</div>` : '<p>Dirija normalmente no GNV; o app compara cada faixa com a gasolina no mesmo RPM e MAP.</p>'}
        <p class="refine-contract">O resultado de cada faixa ajusta o ganho da próxima proposta: passou do ponto → mais suave; faltou → mais firme.</p>
        ${restore || undo ? `<div class="operation-actions">${undo}${restore}</div>` : ''}
      </div>`;
  }

  const RISK = { LOW: ['Linear', 'ok'], ATTENTION: ['Pouco linear', 'warn'], HIGH: ['Com trancos', 'danger'], UNKNOWN: ['—', 'muted'] };

  /** Trecho (em ms) onde a curva é mais íngreme — é onde a ECU oscila e dá o tranco. */
  function steepestSpan(points, key) {
    let best = null;
    for (let i = 2; i < Math.min(points.length - 1, 22); i += 1) {
      const a = points[i];
      const b = points[i + 1];
      const ka = finite(a?.[key]);
      const kb = finite(b?.[key]);
      if (!ka || !kb || !finite(a.referenceTimeMs) || !finite(b.referenceTimeMs)) continue;
      const e = Math.abs(Math.log(kb / ka) / Math.log(b.referenceTimeMs / a.referenceTimeMs));
      if (!best || e > best.e) best = { e, from: a.referenceTimeMs, to: b.referenceTimeMs };
    }
    return best;
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
        ? { tone: 'warn', title: 'A curva atual tem degraus que tiram a linearidade do GNV', text: `Dá para corrigir os degraus agora sem mudar o nível da curva. Para igualar o GNV à gasolina faltam ${Math.max(0, need - mature)} faixa(s) de carga coletadas nos dois combustíveis.` }
        : { tone: 'muted', title: 'Coletando gasolina e GNV', text: `Faixas comparáveis: ${mature} de ${need} necessárias. Rode variando a carga com o motor quente.` };
    }
    if (Number(a.changedCount) === 0) return { tone: 'ok', title: 'GNV equivalente à gasolina', text: 'A curva atual já está dentro da tolerância medida.' };
    return { tone: 'accent', title: 'Curva refinada pronta para revisão', text: `O GNV passa a seguir o que a gasolina pede em ${mature} faixas medidas. O formato medido é mantido; só os degraus que tiram a linearidade da puxada são limitados.` };
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
      const eq = this.api?.equivalence?.() || null;
      const snapshot = this.api?.snapshot?.() || null;
      const signature = JSON.stringify([snapshot,eq?.gasEpochAt,eq?.samples,eq?.ratio,eq?.bands,eq?.refinement?.bandScale,eq?.denseBands]);
      if (this.analysisSignature !== signature || !this.analysis) {
        this.analysis = this.api?.refinedAnalysis?.() || null;
        this.analysisSignature = signature;
      }
      this.snapshot = snapshot;
      this.equivalence = eq;
      this.render();
    }

    onClick(event) {
      if (event.target.closest('[data-refine-review]')) this.openReview();
      if (event.target.closest('[data-refine-cancel]')) this.closeReview();
      if (event.target.closest('[data-refine-apply]')) this.apply();
      if (event.target.closest('[data-refine-restore]')) this.restore();
      if (event.target.closest('[data-refine-restore-band]')) this.restoreBand();
      if (event.target.closest('[data-refine-undo]')) this.undoLast();
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
      // Revisão é um overlay; o layout principal não se move.
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

    /** Restaura só os pontos das faixas que pioraram (diário do refino). */
    restoreBand() {
      const points = (this.equivalence?.restorePoints || []).map(p => ({ index: Number(p.index), currentRaw: Number(p.currentRaw), targetRaw: Number(p.targetRaw) }));
      if (!points.length) return;
      this.runWrite(points, 'Restaurar trecho que piorou (verificação do refino OMEGAS)', false);
    }

    /** Desfaz a última gravação de Curva K registrada no diário (sobrevive a reinício do app). */
    undoLast() {
      const points = undoPoints(this.equivalence?.refinement?.latest);
      if (!points.length) return;
      this.runWrite(points, 'Desfazer última gravação da Curva K (diário do refino OMEGAS)', false);
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
      const pilot = this.equivalence?.autopilot;
      if (pilot?.phase) { head.title = pilot.headline || head.title; head.text = pilot.next || head.text; }
      if (pilot?.phase === 'SEM_ECU') { head.title = 'Sem ECU'; head.text = 'Conecte o cabo para observar a calibração.'; }
      const before = a.metricsBefore || {};
      const after = a.metricsAfter || {};
      const changed = this.changedPoints();
      const riskBefore = RISK[a.joltRiskBefore] || RISK.UNKNOWN;
      const riskAfter = RISK[a.joltRiskAfter] || RISK.UNKNOWN;
      const span = steepestSpan(a.points || [], 'currentFactor');
      const where = a.joltRiskBefore && a.joltRiskBefore !== 'LOW' && span ? `degrau hoje em ${fmt(span.from, 1)}–${fmt(span.to, 1)} ms` : 'gás acompanha a gasolina';
      const errBefore = finite(a.evidenceErrorBefore);
      const errAfter = finite(a.evidenceErrorAfter);
      const maxChange = changed.reduce((m, p) => Math.max(m, Math.abs(finite(p.deltaPercent) || 0)), 0);
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
        if (this.reviewOpen) return '';
        if (pilot?.phase === 'ESTAVEL') return '<span class="refine-done">✓ Estável · pode desconectar</span>';
        if (pilot?.phase === 'RESTAURAR_TRECHO' && this.equivalence?.restorePoints?.length) return '<button type="button" data-refine-restore-band class="danger-primary">Restaurar trecho que piorou</button>';
        if (pilot?.phase && !['PROPOSTA_PRONTA','ECU_TRABALHANDO'].includes(pilot.phase)) return '';
        if (!a.available || !changed.length) return '';
        return `<button type="button" data-refine-review class="primary">${pilot?.phase ? 'Revisar e gravar' : `Revisar e aplicar ${changed.length} ponto${changed.length === 1 ? '' : 's'}`}</button>`;
      })();
      const legend = Object.entries(ORIGIN).map(([key, o]) => `<span data-origin="${key}">${o.label}</span>`).join('');
      const review = this.reviewOpen && this.review ? `<div class="refine-review" role="dialog" aria-label="Revisão da curva refinada">
          <header><div><small>REVISÃO · GRAVAÇÃO NA ECU</small><h4>${this.review.points.length} ponto(s) da Curva K</h4></div><button type="button" data-refine-cancel class="icon-close" aria-label="Cancelar">×</button></header>
          <div class="refine-review-plot">${chartSvg(a.points)}<div class="refine-legend"><span class="current">Atual</span><span class="refined">Refinada</span>${legend}</div></div>
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
          ${pilotHtml(this.equivalence)}
          <header class="refine-head">
            <div><small>EQUIVALÊNCIA GNV = GASOLINA</small><h3>${escapeHtml(head.title)}</h3><p>${escapeHtml(head.text)}</p></div>
            ${this.equivalence?.autopilot?.phase ? '' : `<ol class="refine-steps">${steps}</ol>`}
          </header>
          ${a.available ? `<div class="refine-body">
            <div class="refine-chart-wrap"><h4>Nossa curva</h4>${ns.OurCurvePlot?.html(this.snapshot,this.equivalence?.denseBands) || '<p class="our-curve-empty">Ainda sem pontos próprios — rode na gasolina e no GNV</p>'}<div class="our-curve-detail" aria-live="polite">Toque num ponto para conferir MAP, ms e amostras.</div></div>
            <dl class="refine-metrics">
              <div><dt>Puxada no GNV</dt><dd><b data-tone="${riskBefore[1]}">${riskBefore[0]}</b> → <b data-tone="${riskAfter[1]}">${riskAfter[0]}</b></dd><span>${escapeHtml(where)}</span></div>
              <div><dt>Mudança</dt><dd>${changed.length} ponto${changed.length === 1 ? '' : 's'}</dd><span>${changed.length ? `até ±${fmt(maxChange, 1)}% (limite ±${fmt(a.guards?.maximumStepPercent, 0)}%)` : 'curva mantida'}</span></div>
              <div><dt>Evidência</dt><dd>${fmt(a.matureCommonPoints, 0)} faixas ECU${finite(a.telemetryTargets) ? ` + ${fmt(a.telemetryTargets, 0)} nossos` : ''}</dd><span>pontos da ECU + pontos próprios GNV × gasolina no mesmo RPM e MAP</span></div>
            </dl>
          </div>` : ''}
          ${this.equivalence?.autopilot?.phase === 'ECU_TRABALHANDO' && changed.length ? '<p class="refine-note">A ECU ainda está no automático e pode sobrescrever a curva. O melhor momento para gravar é quando ela terminar — o app avisa.</p>' : ''}
          ${a.needsAnotherPass ? '<p class="refine-note">A curva atual tem degraus fortes demais para corrigir com segurança de uma vez (limite ±15%). Grave, rode alguns minutos e o app propõe a segunda passada.</p>' : ''}
          <div class="refine-actions">${primary}</div>
          ${review}
          ${op.phase === 'idle' && this.equivalence?.refinement?.latest ? `<details class="refine-result"><summary>Resultado da última gravação</summary>${journalHtml(this.equivalence)}</details>` : ''}
          ${a.available ? `<details class="refine-details"><summary>Detalhes técnicos</summary>
            <p>Inclinação máx. |d ln K / d ln t|: ${fmt(before.maxElasticity, 2)} → ${fmt(after.maxElasticity, 2)} (limite ${fmt(a.elasticityLimit, 2)}; acima disso o gás deixa de seguir linearmente o pedido da gasolina — escolhido pelo teste cego com telemetria em gasolina).</p>
            <p>${escapeHtml(a.algorithm || '')} · modo ${escapeHtml(a.refinementMode || '')} · buffers ${a.buffersCoherent ? 'coerentes' : 'incoerentes'} · trava ±${fmt(a.guards?.maximumStepPercent, 0)}%/execução, |d ln K / d ln t| ≤ ${fmt(a.elasticityLimit, 2)}</p>
            ${targets.length ? `<table><thead><tr><th>MAP</th><th>T gasolina</th><th>T no GNV</th><th>Razão</th><th>Peso</th><th>K alvo</th></tr></thead><tbody>${targets.map(t => `<tr${finite(t.robustWeight) !== null && t.robustWeight < 0.3 ? ' class="discounted"' : ''}><td>${fmt(t.mapBar, 3)}</td><td>${fmt(t.petrolMs, 2)}</td><td>${fmt(t.gasMs, 2)}</td><td>${fmt(t.ratio, 3)}</td><td>${fmt(t.weight, 2)}</td><td>${fmt(t.targetFactor, 3)}</td></tr>`).join('')}</tbody></table>` : ''}
            ${rejected.length ? `<p>Bandas descartadas por fugirem da curva física: ${rejected.map(r => `${escapeHtml(r.fuel)} B${Number(r.band) + 1} (${fmt(r.mapBar, 2)} bar, ${fmt(r.timeMs, 2)} ms)`).join(' · ')}</p>` : ''}
          </details>` : ''}
        </section>`;
    }
  }

  ns.AutoCalRefinePanel = AutoCalRefinePanel;
  ns.AutoCalRefineModel = { deriveFlow, headline, undoPoints, pilotHtml, journalHtml };
})(typeof window !== 'undefined' ? window : globalThis);
