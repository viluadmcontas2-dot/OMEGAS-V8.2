(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Refino OMEGAS: o "nosso AutoCal". Mesma linguagem visual do AutoCal da Platina
  // (cabeçalho, gráfico Petrol Inj. × MAP, revisão), com os nossos pontos por cima.
  // A matemática é do Kotlin (AutoMatchRefinedEngine, EquivalenceLedger, diário e piloto).
  // Nada grava sozinho: a gravação usa o fluxo da Curva K da Platina (leitura → conferência
  // → escrita → ACK → readback), sempre depois do toque de confirmação.

  const PHASES = [
    ['ECU_TRABALHANDO', 'ECU no automático'],
    ['COLETANDO_NOSSOS', 'Nossos pontos'],
    ['PROPOSTA_PRONTA', 'Curva pronta'],
    ['VERIFICANDO', 'Verificando'],
    ['ESTAVEL', 'Estável'],
  ];
  const PHASE_TONE = {
    SEM_ECU: 'unknown', ECU_TRABALHANDO: 'unknown', COLETANDO_NOSSOS: 'collecting',
    PROPOSTA_PRONTA: 'ready', VERIFICANDO: 'collecting', RESTAURAR_TRECHO: 'problem', ESTAVEL: 'ok',
  };
  const VERDICT = {
    CONFIRMADA: 'chegou na gasolina',
    PASSOU: 'passou do ponto · próxima mais suave',
    CURTA: 'faltou · próxima mais firme',
    PIOROU: 'piorou',
    COLETANDO: 'medindo…',
    SEM_ANTES: 'sem medição anterior',
  };
  const ORIGIN = { MEASURED: 'Medido', BLENDED: 'Transição', SMOOTHED: 'Anti-tranco', HELD: 'Mantido' };
  const POLL_MS = 300;
  const OPERATION_TIMEOUT_MS = 90000;
  const DATA_EVERY_TICKS = 2;

  function finite(value) {
    if (value === null || value === undefined || value === '') return null;
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
  function pct(ratio) {
    const r = finite(ratio);
    if (r === null) return '—';
    const p = (r - 1) * 100;
    return `${p > 0 ? '+' : ''}${fmt(p, 1)}%`;
  }

  /** Pontos que a revisão propõe gravar (puro, testável). */
  function proposedPoints(analysis) {
    const points = Array.isArray(analysis?.points) ? analysis.points : [];
    return points
      .filter(p => p && p.origin !== 'HELD' && finite(p.calculatedRaw) !== null && Number(p.calculatedRaw) !== Number(p.currentRaw))
      .map(p => ({ index: Number(p.index), currentRaw: Number(p.currentRaw), targetRaw: Number(p.calculatedRaw) }));
  }

  /** Desfazer a última gravação registrada no diário (antes ← depois). */
  function undoPoints(latest) {
    const before = latest?.beforeRaw;
    const after = latest?.afterRaw;
    if (!Array.isArray(before) || !Array.isArray(after) || before.length !== 30 || after.length !== 30) return [];
    const out = [];
    for (let i = 0; i < 30; i += 1) if (Number(before[i]) !== Number(after[i])) out.push({ index: i, currentRaw: Number(after[i]), targetRaw: Number(before[i]) });
    return out;
  }

  /** Ação principal conforme a fase do piloto (uma só por vez). */
  function primaryAction(eq, analysis) {
    const phase = eq?.autopilot?.phase || 'SEM_ECU';
    const restore = Array.isArray(eq?.restorePoints) ? eq.restorePoints.length : 0;
    const proposal = proposedPoints(analysis).length;
    if (phase === 'RESTAURAR_TRECHO' && restore) return { kind: 'restore', label: `Restaurar trecho que piorou (${restore} ponto${restore === 1 ? '' : 's'})` };
    if (phase === 'ESTAVEL') return { kind: 'stable', label: '✓ Estável · pode desconectar' };
    if (proposal && analysis?.available) {
      return { kind: 'review', label: `Revisar e gravar ${proposal} ponto${proposal === 1 ? '' : 's'}`, early: phase === 'ECU_TRABALHANDO' };
    }
    return { kind: 'none', label: '' };
  }

  class RefinoScreen {
    constructor(app) {
      this.app = app;
      this.store = app.store;
      this.scheduler = app.scheduler;
      this.native = app.api;
      this.api = ns.AutoCalApi;
      this.eq = null;
      this.analysis = null;
      this.snapshot = null;
      this.ticks = 0;
      this.operation = { phase: 'idle' };
      this.reviewPoints = null;
      this.lastRenderKey = '';
      this.inject();
      this.unsubscribeStatus = this.scheduler.addHook('status', () => {
        if (this.store.get().route !== 'refino') return;
        this.ticks += 1;
        if (this.ticks % DATA_EVERY_TICKS === 0) this.refresh();
      });
      // Ao entrar na aba, desenha na hora (sem esperar o próximo tick).
      let lastRoute = null;
      this.store.subscribe(state => {
        if (state.route === lastRoute) return;
        lastRoute = state.route;
        if (state.route === 'refino') root.setTimeout(() => this.refresh(true), 0);
      }, true);
    }

    inject() {
      const host = document.getElementById('refinoScreenHost');
      if (!host || host.querySelector('.autocal-cockpit')) return;
      host.innerHTML = `
        <section class="autocal-cockpit refino-cockpit" aria-label="Refino OMEGAS">
          <header class="autocal-focus-toolbar">
            <div class="autocal-focus-title">
              <div class="autocal-title-line"><h3>Refino · GNV = Gasolina</h3><span id="refinoPhaseChip" class="autocal-fuel-chip" data-fuel-state="unknown">—</span></div>
              <p id="refinoHeadline">Aguardando a ECU</p>
            </div>
            <div class="autocal-focus-metrics" aria-live="polite">
              <div class="autocal-focus-metric"><small>GNV ÷ Gasolina</small><b><span id="refinoRatio">—</span></b></div>
              <div class="autocal-focus-metric"><small>Pontos ECU</small><b id="refinoEcuPoints">—</b></div>
              <div class="autocal-focus-metric"><small>Nossos pontos</small><b id="refinoOurPoints">—</b></div>
            </div>
            <div class="autocal-focus-actions"><button type="button" class="autocal-primary-action" data-refino-primary hidden></button></div>
          </header>
          <ol class="refino-steps" id="refinoSteps" aria-label="Fases do refino"></ol>
          <p class="refino-next" id="refinoNext"></p>
          <section class="autocal-reference-card" aria-label="NOSSA CURVA · Gasolina × GNV">
            <span class="autocal-plot-title">NOSSA CURVA · Gasolina × GNV</span>
            <div class="autocal-chart-workspace">
              <div id="refinoChart" class="autocal-chart-host"><div class="chart-empty">Aguardando a leitura da ECU.</div></div>
              <div class="autocal-chart-legend">
                <span class="petrol">Curva gasolina (ECU)</span>
                <span class="gas">Curva GNV (ECU)</span>
                <span class="acquired">Pontos da ECU</span>
                <span class="refino-ours-petrol">Nossos · gasolina</span>
                <span class="refino-ours-gas">Nossos · GNV</span>
              </div>
              <aside id="refinoInspector" class="autocal-chart-inspector"><b>Toque em um ponto</b><span>Pontos grandes são da ECU; os pequenos são nossos, a cada 0,025 bar.</span></aside>
            </div>
          </section>
          <details class="autocal-secondary-details" id="refinoResultDetails">
            <summary>Resultado da última gravação</summary>
            <div id="refinoJournal" class="refino-journal"></div>
          </details>
          <details class="autocal-secondary-details">
            <summary>Detalhes técnicos</summary>
            <div id="refinoTech" class="refino-tech"></div>
          </details>
          <div id="refinoReview" class="autocal-review" hidden></div>
        </section>`;
      host.addEventListener('click', event => this.onClick(event));
    }

    refresh(force) {
      if (this.operation.phase === 'reading' || this.operation.phase === 'writing') return;
      if (!this.api?.available?.()) { this.renderUnavailable(); return; }
      this.eq = this.api.equivalence?.() || null;
      this.analysis = this.api.refinedAnalysis?.() || null;
      const projection = this.api.projection?.() || {};
      this.snapshot = projection.ok === true ? (projection.snapshot || {}) : {};
      this.render(force === true);
    }

    onClick(event) {
      if (event.target.closest('[data-refino-dismiss]')) { this.operation = { phase: 'idle' }; this.refresh(true); return; }
      if (event.target.closest('[data-refino-primary]')) this.primary();
      if (event.target.closest('[data-refino-cancel]')) this.closeReview();
      if (event.target.closest('[data-refino-confirm]')) this.confirm();
      if (event.target.closest('[data-refino-undo]')) this.openReview('undo');
      const dot = event.target.closest('[data-refino-dot]');
      if (dot) this.inspect(dot.dataset.refinoDot);
    }

    primary() {
      const action = primaryAction(this.eq, this.analysis);
      if (action.kind === 'review') this.openReview('apply');
      if (action.kind === 'restore') this.openReview('restore');
    }

    openReview(kind) {
      let points = [];
      let title = '';
      let reason = '';
      if (kind === 'apply') { points = proposedPoints(this.analysis); title = 'Gravar curva refinada'; reason = 'Refino OMEGAS: curva refinada confirmada'; }
      if (kind === 'restore') { points = (this.eq?.restorePoints || []).map(p => ({ index: Number(p.index), currentRaw: Number(p.currentRaw), targetRaw: Number(p.targetRaw) })); title = 'Restaurar trecho que piorou'; reason = 'Refino OMEGAS: restaurar trecho que piorou'; }
      if (kind === 'undo') { points = undoPoints(this.eq?.refinement?.latest); title = 'Desfazer última gravação'; reason = 'Refino OMEGAS: desfazer última gravação'; }
      if (!points.length) return;
      this.reviewPoints = { points, reason };
      const byIndex = new Map((this.analysis?.points || []).map(p => [Number(p.index), p]));
      const rows = points.map(p => {
        const ref = byIndex.get(p.index) || {};
        const before = p.currentRaw / 16384;
        const after = p.targetRaw / 16384;
        return `<div><dt>${fmt(ref.referenceTimeMs, 1)} ms</dt><dd><b>${fmt(before, 3)} → ${fmt(after, 3)}</b> · ${pct(after / before)}${ref.origin ? ' · ' + escapeHtml(ORIGIN[ref.origin] || ref.origin) : ''}</dd></div>`;
      }).join('');
      const early = kind === 'apply' && this.eq?.autopilot?.phase === 'ECU_TRABALHANDO'
        ? '<p class="refino-warning">A ECU ainda está no automático e pode sobrescrever esta curva. O ideal é gravar quando ela terminar — o app avisa.</p>' : '';
      const review = document.getElementById('refinoReview');
      if (!review) return;
      review.hidden = false;
      review.innerHTML = `<div class="autocal-review-card"><header><div><small>REVISÃO ANTES DA ECU</small><h3>${escapeHtml(title)} · ${points.length} ponto${points.length === 1 ? '' : 's'} da Curva K</h3></div><button type="button" data-refino-cancel class="icon-close" aria-label="Fechar revisão">×</button></header>${early}<dl class="refino-review-list">${rows}</dl><div class="write-contract"><b>Nada foi enviado à ECU.</b><span>Ao confirmar, o app relê a curva, confere que nada mudou, grava e confirma por readback. A curva anterior fica no diário para desfazer.</span></div><div class="operation-actions"><button type="button" data-refino-cancel class="secondary">Cancelar</button><button type="button" data-refino-confirm class="danger-primary">Gravar na ECU</button></div></div>`;
    }

    closeReview() {
      this.reviewPoints = null;
      const review = document.getElementById('refinoReview');
      if (review) { review.hidden = true; review.innerHTML = ''; }
    }

    confirm() {
      const pending = this.reviewPoints;
      this.closeReview();
      if (!pending?.points?.length) return;
      this.runWrite(pending.points, pending.reason);
    }

    /** Lê a Curva K nesta conexão, confere com o snapshot e só então grava (ACK + readback no Kotlin). */
    runWrite(points, reason) {
      const read = this.native?.startCurveRead?.();
      if (!read?.ok || !read?.started) { this.fail(read?.error || 'A leitura da Curva K não iniciou.'); return; }
      this.operation = { phase: 'reading' };
      this.render(true);
      this.poll(result => {
        const list = Array.isArray(result.points) ? result.points : [];
        if (result.state !== 'COMPLETED' || list.length !== 30) { this.fail(result.error || 'A ECU não devolveu os 30 pontos da Curva K.'); return; }
        const factors = new Map(list.map((p, i) => [Number(p.index ?? i), Number(p.factorRaw)]));
        const stale = points.find(p => factors.get(p.index) !== p.currentRaw);
        if (stale) { this.fail('A Curva K da ECU mudou desde a última leitura. Aguarde a próxima atualização e revise de novo.'); return; }
        const write = this.native.writeCurve(points, reason);
        if (!write?.ok || !write?.started) { this.fail(write?.error || 'A gravação não iniciou.'); return; }
        this.operation = { phase: 'writing', total: points.length };
        this.render(true);
        this.poll(done => {
          if (done.state === 'BATCH_CONFIRMED' && done.readbackValid === true) {
            this.operation = { phase: 'done' };
            this.render(true);
          } else {
            this.fail(done.error || 'O readback não confirmou a gravação. A ECU manteve a curva anterior.');
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
          if (this.operation.phase === 'writing') { this.operation.progress = finite(status.progress); this.renderOperation(); }
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
      this.render(true);
    }

    renderUnavailable() {
      const headline = document.getElementById('refinoHeadline');
      if (headline) headline.textContent = 'Refino indisponível nesta tela (ponte AutoCal ausente).';
    }

    renderOperation() {
      const button = document.querySelector('[data-refino-primary]');
      const op = this.operation;
      if (!button) return;
      if (op.phase === 'reading' || op.phase === 'writing') {
        button.hidden = false;
        button.disabled = true;
        const progress = finite(op.progress);
        button.textContent = op.phase === 'reading' ? 'Conferindo a curva da ECU…' : `Gravando…${progress === null ? '' : ' ' + Math.round(progress) + '%'}`;
      }
    }

    render(force) {
      const eq = this.eq || {};
      const pilot = eq.autopilot || {};
      const phase = pilot.phase || 'SEM_ECU';
      const op = this.operation;
      const key = JSON.stringify([phase, pilot.headline, pilot.next, eq.ratio, eq.samples, op, eq.denseBands, (this.analysis?.points || []).map(p => p.calculatedRaw), this.snapshot?.snapshotHash, eq.refinement?.latest?.status]);
      if (!force && key === this.lastRenderKey) return;
      this.lastRenderKey = key;

      const chip = document.getElementById('refinoPhaseChip');
      if (chip) { chip.dataset.fuelState = PHASE_TONE[phase] || 'unknown'; chip.textContent = (PHASES.find(([k]) => k === phase) || [null, phase === 'RESTAURAR_TRECHO' ? 'Trecho piorou' : 'Sem ECU'])[1]; }
      const headline = op.phase === 'done' ? 'Curva gravada e conferida pela ECU.'
        : op.phase === 'failed' ? op.message
          : pilot.headline || 'Conecte a ECU para acompanhar a calibração.';
      const next = op.phase === 'done' ? 'Dirija normalmente: o app mede se o GNV chegou na gasolina, faixa por faixa.'
        : op.phase === 'failed' ? 'Nada foi dado como gravado. Toque em "Entendi" e tente de novo quando a ECU estabilizar.'
          : pilot.next || '';
      setText('refinoHeadline', headline);
      setText('refinoNext', next);
      setText('refinoRatio', pct(eq.ratio));
      const ecuPoints = (finite(pilot.petrolValid) ?? 0) + (finite(pilot.gasValid) ?? 0);
      setText('refinoEcuPoints', pilot.petrolValid === undefined ? '—' : `${ecuPoints}/36`);
      const dense = eq.denseBands || {};
      const ours = (Array.isArray(dense.petrol) ? dense.petrol.length : 0) + (Array.isArray(dense.gas) ? dense.gas.length : 0);
      setText('refinoOurPoints', `${ours} faixas`);

      const steps = document.getElementById('refinoSteps');
      if (steps) {
        const at = PHASES.findIndex(([k]) => k === (phase === 'RESTAURAR_TRECHO' ? 'VERIFICANDO' : phase));
        steps.innerHTML = PHASES.map(([k, label], i) => `<li data-state="${at < 0 ? 'pending' : i < at ? 'done' : i === at ? 'active' : 'pending'}"${phase === 'RESTAURAR_TRECHO' && k === 'VERIFICANDO' ? ' data-problem="true"' : ''}>${escapeHtml(label)}</li>`).join('');
      }

      const button = document.querySelector('[data-refino-primary]');
      if (button) {
        if (op.phase === 'reading' || op.phase === 'writing') this.renderOperation();
        else if (op.phase === 'done' || op.phase === 'failed') { button.hidden = false; button.disabled = false; button.textContent = 'Entendi'; button.dataset.refinoDismiss = ''; }
        else {
          delete button.dataset.refinoDismiss;
          const action = primaryAction(eq, this.analysis);
          button.hidden = action.kind === 'none';
          button.disabled = action.kind === 'stable';
          button.textContent = action.label;
          button.dataset.kind = action.kind;
        }
      }
      this.renderChart();
      this.renderJournal();
      this.renderTech();
    }

    renderChart() {
      const host = document.getElementById('refinoChart');
      const model = ns.AutoCalUxModel;
      if (!host || !model) return;
      const snapshot = this.snapshot || {};
      // Curva RV zerada (ECU ainda sem aquisição daquele combustível) não vira linha no zero.
      const reference = model.referencePoints(snapshot, {});
      const hasPetrolRv = reference.some(p => finite(p.petrolMapBar) > 0);
      const hasGasRv = reference.some(p => finite(p.gasMapBar) > 0);
      const acquired = [...model.acquiredPoints(snapshot, 'petrol'), ...model.acquiredPoints(snapshot, 'gas')];
      const dense = this.eq?.denseBands || {};
      const ours = [
        ...(Array.isArray(dense.petrol) ? dense.petrol : []).map(p => ({ ...p, fuel: 'PETROL' })),
        ...(Array.isArray(dense.gas) ? dense.gas : []).map(p => ({ ...p, fuel: 'GAS' })),
      ].filter(p => finite(p.tpetMs) !== null && finite(p.mapBar) !== null);
      this.ours = ours;
      this.acquired = acquired;
      if (!reference.length && !acquired.length && !ours.length) {
        host.innerHTML = '<div class="chart-empty"><b>SEM PONTOS AINDA</b><span>Rode na gasolina e no GNV com a ECU conectada. Os pontos da ECU e os nossos aparecem aqui.</span></div>';
        return;
      }
      // Escala focada onde há pontos medidos (ECU + nossos); a RV só completa o contexto.
      const measuredXs = [...acquired.map(p => p.petrolMs), ...ours.map(p => p.tpetMs)].filter(v => v > 0);
      const xs = measuredXs.length ? measuredXs : reference.map(p => p.petrolMs).filter(v => v > 0);
      const xMin = 0;
      const xMax = Math.max(4, Math.ceil(Math.max(...xs) * 1.25));
      const ys = [
        ...reference.filter(p => p.petrolMs <= xMax).flatMap(p => [hasPetrolRv ? p.petrolMapBar : null, hasGasRv ? p.gasMapBar : null]),
        ...acquired.map(p => p.mapBar), ...ours.map(p => p.mapBar),
      ].filter(v => Number.isFinite(v) && v > 0);
      const yMin = Math.max(0, Math.floor(Math.min(...ys) * 20) / 20 - 0.05);
      const yMax = Math.ceil(Math.max(...ys) * 20) / 20 + 0.05;
      const width = 1000; const height = 400; const padLeft = 64; const padRight = 28; const padTop = 22; const padBottom = 48;
      const xFor = v => padLeft + ((v - xMin) / (xMax - xMin)) * (width - padLeft - padRight);
      const yFor = v => height - padBottom - ((v - yMin) / (yMax - yMin)) * (height - padTop - padBottom);
      const xTicks = Array.from({ length: 6 }, (_, i) => xMin + i * (xMax - xMin) / 5);
      const yTicks = Array.from({ length: 5 }, (_, i) => yMin + i * (yMax - yMin) / 4);
      const grid = yTicks.map(v => `<line class="autocal-grid-line" x1="${padLeft}" y1="${yFor(v).toFixed(1)}" x2="${width - padRight}" y2="${yFor(v).toFixed(1)}"></line><text class="autocal-axis-tick-y" x="${padLeft - 8}" y="${(yFor(v) + 4).toFixed(1)}" text-anchor="end">${v.toFixed(2)}</text>`).join('') +
        xTicks.map(v => `<line class="autocal-grid-line vertical" x1="${xFor(v).toFixed(1)}" y1="${padTop}" x2="${xFor(v).toFixed(1)}" y2="${height - padBottom}"></line><text class="autocal-axis-tick-x" x="${xFor(v).toFixed(1)}" y="${height - 23}" text-anchor="middle">${v.toFixed(1)}</text>`).join('');
      const path = key => reference.filter(p => finite(p[key]) !== null && p[key] > 0 && p[key] >= yMin && p[key] <= yMax && p.petrolMs <= xMax)
        .map((p, i) => `${i ? 'L' : 'M'} ${xFor(p.petrolMs).toFixed(1)} ${yFor(p[key]).toFixed(1)}`).join(' ');
      const acquiredMarkup = acquired.map((p, i) => `<circle class="autocal-acquired-point ${p.fuel === 'GAS' ? 'gas' : 'petrol'} ${p.acquisitionState === 'ACQUIRED' ? 'acquired' : 'collecting'}" data-refino-dot="ecu:${i}" cx="${xFor(p.petrolMs).toFixed(1)}" cy="${yFor(p.mapBar).toFixed(1)}" r="${p.acquisitionState === 'ACQUIRED' ? '6.0' : '4.6'}"></circle>`).join('');
      const oursMarkup = ours.map((p, i) => `<circle class="refino-our-point ${p.fuel === 'GAS' ? 'gas' : 'petrol'}" data-refino-dot="our:${i}" cx="${xFor(p.tpetMs).toFixed(1)}" cy="${yFor(p.mapBar).toFixed(1)}" r="3.2"></circle>`).join('');
      host.innerHTML = `<svg class="autocal-reference-svg" viewBox="0 0 ${width} ${height}" role="img" aria-label="Nossa curva: Petrol Inj. por MAP, gasolina e GNV, pontos da ECU e nossos">${grid}` +
        `<text class="autocal-axis-title x" x="${((padLeft + width - padRight) / 2).toFixed(1)}" y="${height - 5}" text-anchor="middle">Petrol Inj. (ms)</text>` +
        `<text class="autocal-axis-title y" x="14" y="${height / 2}" text-anchor="middle" transform="rotate(-90 14 ${height / 2})">MAP (bar)</text>` +
        `<g>${hasPetrolRv ? `<path class="autocal-reference-line petrol" d="${path('petrolMapBar')}"></path>` : ''}${hasGasRv ? `<path class="autocal-reference-line gas" d="${path('gasMapBar')}"></path>` : ''}${oursMarkup}${acquiredMarkup}</g></svg>`;
    }

    inspect(token) {
      const [kind, raw] = String(token || '').split(':');
      const index = Number(raw);
      const inspector = document.getElementById('refinoInspector');
      if (!inspector) return;
      if (kind === 'our') {
        const p = (this.ours || [])[index];
        if (!p) return;
        inspector.innerHTML = `<b>Nosso ponto · ${p.fuel === 'GAS' ? 'GNV' : 'Gasolina'}</b><span>MAP ${fmt(p.mapBar, 3)} bar · ${fmt(p.tpetMs, 2)} ms · ${fmt(p.samples, 0)} leituras estáveis · RPM típico ${fmt(p.rpmMedian, 0)}</span>`;
      } else {
        const p = (this.acquired || [])[index];
        if (!p) return;
        inspector.innerHTML = `<b>Ponto da ECU · ${escapeHtml(p.fuelLabel)} B${p.point}</b><span>MAP ${fmt(p.mapBar, 3)} bar · ${fmt(p.petrolMs, 2)} ms · ${p.acquisitionState === 'ACQUIRED' ? 'adquirido' : 'coletando'} (${fmt(p.counter, 0)}/${fmt(p.threshold, 0)})</span>`;
      }
    }

    renderJournal() {
      const host = document.getElementById('refinoJournal');
      if (!host) return;
      const latest = this.eq?.refinement?.latest;
      if (!latest || !latest.status) { host.innerHTML = '<p>Nenhuma gravação feita pelo refino ainda.</p>'; return; }
      const bands = (Array.isArray(latest.bands) ? latest.bands : []).filter(b => VERDICT[b.verdict]);
      const rows = bands.map(b => `<div data-verdict="${escapeHtml(b.verdict)}"><span>${fmt(b.fromMs, 1)}–${fmt(b.toMs, 1)} ms</span><b>${pct(b.ratioBefore)}${finite(b.ratioAfter) === null ? '' : ' → ' + pct(b.ratioAfter)}</b><small>${escapeHtml(VERDICT[b.verdict])}</small></div>`).join('');
      const undo = undoPoints(latest).length ? '<button type="button" class="secondary" data-refino-undo>Desfazer última gravação</button>' : '';
      host.innerHTML = `${rows ? `<div class="refino-verdicts">${rows}</div>` : '<p>Dirija no GNV: o app compara cada faixa com a gasolina no mesmo RPM e MAP.</p>'}<p class="refino-note">Cada resultado ajusta a força da próxima correção naquela faixa.</p>${undo}`;
    }

    renderTech() {
      const host = document.getElementById('refinoTech');
      if (!host) return;
      const a = this.analysis || {};
      const pilot = this.eq?.autopilot || {};
      host.innerHTML = `<dl>
        <div><dt>Modo</dt><dd>${escapeHtml(a.refinementMode || '—')} · ${a.available ? 'disponível' : escapeHtml(a.message || 'aguardando evidência')}</dd></div>
        <div><dt>Bandas comuns maduras</dt><dd>${fmt(a.matureCommonPoints, 0)} de ${fmt(a.minimumMatureCommonPoints, 0)} necessárias · ${fmt(a.telemetryTargets, 0)} alvos dos nossos pontos</dd></div>
        <div><dt>Trava</dt><dd>±${fmt(a.guards?.maximumStepPercent, 0)}% por gravação · |Δ ln K/Δ ln t| ≤ ${fmt(a.elasticityLimit, 2)}</dd></div>
        <div><dt>Automático da ECU</dt><dd>${fmt(pilot.autoMatchCount, 0)} de ${fmt(pilot.maxAutomatch, 0)} · ${escapeHtml(pilot.ecuDoneReason || 'ainda trabalhando')}</dd></div>
      </dl>`;
    }
  }

  function setText(id, value) {
    const node = document.getElementById(id);
    if (node && node.textContent !== String(value ?? '')) node.textContent = String(value ?? '');
  }

  function boot() {
    const app = root.OmegasApp;
    if (!app?.store || !app?.scheduler || !app?.api || !ns.AutoCalApi || !ns.AutoCalUxModel) {
      root.setTimeout(boot, 50);
      return;
    }
    if (app.refino) return;
    app.refino = new RefinoScreen(app);
  }

  ns.RefinoModel = { proposedPoints, undoPoints, primaryAction };
  ns.RefinoScreen = RefinoScreen;
  if (typeof document !== 'undefined') boot();
})(typeof window !== 'undefined' ? window : globalThis);
