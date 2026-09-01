(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  function finite(value) { return Number.isFinite(Number(value)) ? Number(value) : null; }
  function fmt(value, digits) {
    const n = finite(value);
    return n === null ? '—' : n.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits });
  }
  function text(id, value) {
    const node = document.getElementById(id);
    if (!node) return;
    const next = value == null ? '—' : String(value);
    if (node.textContent !== next) node.textContent = next;
  }
  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[char]));
  }

  class CurveScreen {
    constructor(store, api) {
      this.store = store;
      this.api = api;
      this.root = document.querySelector('[data-screen="curve"]');
      this.data = null;
      this.activeIndex = null;
      this.proposals = new Map();
      this.pendingSuggestion = null;
      this.reading = false;
      this.writing = false;
      this.view = 'editor';
      this.learningSignature = '';
      this.autoCalSignature = '';
      this.bind();
    }

    bind() {
      document.getElementById('curveReadButton')?.addEventListener('click', () => this.startRead());
      document.getElementById('autoCalRefresh')?.addEventListener('click', () => {
        const result = this.api.requestAutoCalSnapshot();
        if (result?.ok === false) this.alert(result.error || 'Não foi possível solicitar a leitura AutoCal.');
        this.autoCalSignature = '';
        this.renderAutoCal();
      });
      document.getElementById('curvePreparePoint')?.addEventListener('click', () => this.prepareActivePoint());
      document.querySelectorAll('[data-curve-view]').forEach(button => button.addEventListener('click', () => this.setView(button.dataset.curveView || 'editor')));
      document.querySelectorAll('[data-curve-nudge]').forEach(button => button.addEventListener('click', () => this.nudgeActive(Number(button.dataset.curveNudge) || 0)));
      document.getElementById('curveClearProposals')?.addEventListener('click', () => {
        this.proposals.clear(); this.renderChart(); this.renderProposalList();
      });
      document.getElementById('curveReviewButton')?.addEventListener('click', () => this.openReview());
      document.getElementById('curveReviewBack')?.addEventListener('click', () => this.closeReview());
      document.getElementById('curveWriteButton')?.addEventListener('click', () => this.writeReview());
      document.getElementById('curveDismissResult')?.addEventListener('click', () => this.closeReview());
    }

    needsLearning() { return this.view === 'learning'; }

    setView(view) {
      if (view !== 'learning' && view !== 'editor' && view !== 'autocal') return false;
      this.view = view;
      document.querySelectorAll('[data-curve-view]').forEach(button => button.classList.toggle('active', button.dataset.curveView === this.view));
      document.querySelectorAll('[data-curve-panel]').forEach(panel => panel.classList.toggle('active', panel.dataset.curvePanel === this.view));
      if (this.view === 'learning') this.renderLearning(this.store.get());
      else if (this.view === 'autocal') this.renderAutoCal();
      else this.renderChart();
      return true;
    }

    onEnter(context) {
      const suggestion = context && context.suggestion;
      if (suggestion) {
        this.pendingSuggestion = suggestion;
        this.setView('editor');
        this.renderSuggestionFocus(suggestion);
      }
      if (!this.data && !this.reading) this.startRead(true);
      if (this.data && suggestion) {
        this.focusSuggestion(suggestion);
        this.prepareSuggestion(suggestion, true);
      }
      if (this.view === 'learning') this.renderLearning(this.store.get());
      if (this.view === 'autocal') this.renderAutoCal();
    }

    startRead() {
      if (this.reading || this.writing) return;
      const result = this.api.startCurveRead();
      if (!result?.ok || !result?.started) {
        this.alert(result?.error || 'Não foi possível iniciar a leitura da Curva K.');
        return;
      }
      this.reading = true;
      this.data = null;
      this.proposals.clear();
      text('curveSourceStatus', 'Lendo 30 pontos diretamente da ECU');
      this.root?.classList.add('is-reading');
      if (this.pendingSuggestion) this.renderSuggestionFocus(this.pendingSuggestion);
    }

    poll() {
      if (this.view === 'autocal') this.renderAutoCal();
      if (!this.reading && !this.writing) return;
      const operation = this.api.curveOperation();
      if (!operation) return;
      if (this.reading && !operation.busy && (operation.state === 'COMPLETED' || operation.demo)) {
        this.reading = false;
        this.root?.classList.remove('is-reading');
        if (!operation.ok || !Array.isArray(operation.points) || operation.points.length !== 30) {
          this.alert(operation.error || 'A Curva K não retornou os 30 pontos válidos.');
          text('curveSourceStatus', 'Curva não confirmada');
          return;
        }
        this.data = operation;
        text('curveSourceStatus', 'ECU confirmada · 30 pontos');
        this.renderChart();
        this.renderEvidence(this.store.get());
        if (this.pendingSuggestion) {
          this.renderSuggestionFocus(this.pendingSuggestion);
          this.focusSuggestion(this.pendingSuggestion);
          this.prepareSuggestion(this.pendingSuggestion, true);
        } else {
          this.selectPoint(0);
        }
        if (this.view === 'learning') this.renderLearning(this.store.get());
        this.store.patch({ curve: { ...this.store.get().curve, state: 'ready', data: operation, status: {} } });
        return;
      }

      if (this.writing) {
        const progress = Math.max(0, Math.min(100, finite(operation.progress) || finite(operation.writerProgress) || 0));
        const bar = document.getElementById('curveOperationProgress');
        if (bar) bar.style.width = `${progress}%`;
        text('curveOperationTitle', operation.message || operation.writerMessage || 'Backup · escrita · ACK · readback');
        if (!operation.busy) {
          this.writing = false;
          if (operation.state === 'BATCH_CONFIRMED' && operation.readbackValid === true) {
            this.root?.classList.remove('is-writing');
            this.root?.classList.add('has-result');
            const result = document.getElementById('curveOperationResult');
            if (result) {
              result.dataset.level = 'ok';
              result.querySelector('b').textContent = 'Curva K confirmada pela ECU';
              result.querySelector('span').textContent = 'ACK e readback completos. A curva será relida.';
            }
            this.data = null;
            this.proposals.clear();
            this.pendingSuggestion = null;
            this.startRead(true);
          } else {
            this.root?.classList.remove('is-writing');
            this.root?.classList.add('has-result');
            const result = document.getElementById('curveOperationResult');
            if (result) {
              result.dataset.level = 'critical';
              result.querySelector('b').textContent = 'A Curva K não foi confirmada';
              result.querySelector('span').textContent = operation.error || operation.message || 'A ECU não confirmou toda a operação. Releitura obrigatória.';
            }
            this.data = null;
          }
        }
      }
    }

    renderAutoCal() {
      const status = this.api.autoCalStatus?.() || {};
      const snapshot = this.api.autoCalSnapshot?.() || {};
      const signature = JSON.stringify({ status, snapshot });
      if (signature === this.autoCalSignature) return;
      this.autoCalSignature = signature;

      const stateHost = document.getElementById('autoCalState');
      const summaryHost = document.getElementById('autoCalSummary');
      const technicalHost = document.getElementById('autoCalTechnical');
      const state = String(status.state || (status.connected ? 'READY' : 'WAITING')).toUpperCase();
      const connected = status.connected === true || snapshot.ok === true;
      const busy = status.busy === true || status.snapshotRequested === true;
      const coherent = snapshot.temporalCoherent === true;
      const partial = snapshot.partial === true;
      const fields = Array.isArray(snapshot.fields)
        ? snapshot.fields
        : Object.entries(snapshot.fields || {}).map(([name, value]) => ({ name, value }));
      const validFields = fields.filter(field => field?.valid !== false).length;
      const capturedAt = finite(snapshot.capturedAt ?? snapshot.timestamp ?? snapshot.updatedAt);
      const level = connected ? (partial || !coherent ? 'warning' : 'ok') : 'waiting';
      const title = busy ? 'Atualizando leitura da ECU' : connected ? (partial ? 'Snapshot parcial' : 'AutoCal lido') : 'Aguardando ECU';
      const detail = connected
        ? `${coherent ? 'Campos temporalmente coerentes' : 'Coerência temporal ainda não confirmada'} · ${validFields}/${fields.length || 0} campos válidos`
        : 'Conecte a MP48 para ler o estado AutoCal.';

      if (stateHost) {
        stateHost.dataset.level = level;
        stateHost.innerHTML = `<span class="state-indicator"></span><div><b>${escapeHtml(title)}</b><p>${escapeHtml(detail)}</p></div><small>${escapeHtml(state)}</small>`;
      }
      if (summaryHost) {
        summaryHost.innerHTML = `
          <div><small>ZONAS DE AQUISIÇÃO</small><b>18</b><span>família AutoCal</span></div>
          <div><small>CAMPOS LIDOS</small><b>${fields.length || '—'}</b><span>${partial ? 'snapshot parcial' : 'snapshot atual'}</span></div>
          <div><small>ÚLTIMA LEITURA</small><b>${capturedAt ? new Date(capturedAt).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }) : '—'}</b><span>somente leitura</span></div>
        `;
      }
      if (technicalHost) {
        technicalHost.innerHTML = fields.length
          ? fields.map(field => {
              const name = field.name || field.field || field.id || 'campo';
              const count = finite(field.rawElementCount ?? field.elementCount ?? field.count);
              const validity = field.valid === false ? (field.failureReason || 'inválido') : 'válido';
              return `<div><b>${escapeHtml(name)}</b><span>${count === null ? 'contagem não informada' : `${count} elementos`} · ${escapeHtml(validity)}</span></div>`;
            }).join('')
          : '<p>Nenhum campo materializado ainda. Use “Atualizar leitura” com a ECU conectada.</p>';
      }
    }

    points() { return Array.isArray(this.data?.points) ? this.data.points : []; }

    selectPoint(index) {
      const point = this.points().find(item => Number(item.index) === Number(index));
      if (!point) return;
      this.activeIndex = Number(point.index);
      text('curveActivePoint', `Ponto ${this.activeIndex + 1} · ${fmt(point.petrolMs, 2)} ms`);
      text('curveCurrentFactor', fmt(point.factor, 4));
      const input = document.getElementById('curveTargetFactor');
      if (input) input.value = String(finite(this.proposals.get(this.activeIndex)?.targetFactor ?? point.factor) ?? '');
      this.renderChart();
      this.renderLearningPointContext(this.store.get(), this.activeIndex);
    }

    nudgeActive(delta) {
      if (this.activeIndex === null || !delta) return;
      const input = document.getElementById('curveTargetFactor');
      const current = finite(input?.value) ?? finite(this.points().find(item => Number(item.index) === this.activeIndex)?.factor);
      if (current === null) return;
      if (input) input.value = String(Math.max(0.6, Math.min(4, current + delta)).toFixed(4));
      this.prepareActivePoint();
    }

    focusSuggestion(suggestion) {
      const changes = Array.isArray(suggestion?.curveChanges) ? suggestion.curveChanges : [];
      if (changes.length) {
        this.selectPoint(Number(changes[0].index));
        return this.points().find(item => Number(item.index) === Number(changes[0].index)) || null;
      }
      const point = this.resolveSuggestionPoint(suggestion);
      if (point) this.selectPoint(Number(point.index));
      return point;
    }

    resolveSuggestionPoint(suggestion) {
      if (!suggestion) return null;
      const explicitIndex = finite(suggestion.index);
      if (explicitIndex !== null) {
        const exact = this.points().find(item => Number(item.index) === Number(explicitIndex));
        if (exact) return exact;
      }
      const targetMs = finite(suggestion.petrolMs);
      if (targetMs === null || !this.points().length) return null;
      return this.points().slice().sort((a, b) =>
        Math.abs(Number(a.petrolMs) - targetMs) - Math.abs(Number(b.petrolMs) - targetMs)
      )[0] || null;
    }

    prepareActivePoint() {
      if (this.activeIndex === null) return;
      const requested = finite(document.getElementById('curveTargetFactor')?.value);
      if (requested === null) { this.alert('Informe o fator K desejado.'); return; }
      const preview = this.api.previewCurvePoint(this.activeIndex, requested);
      if (!preview?.ok) { this.alert(preview?.error || 'Prévia da Curva K inválida.'); return; }
      this.acceptPreview(preview);
    }

    prepareSuggestion(suggestion = this.pendingSuggestion, silent = false) {
      if (!suggestion || !this.data) {
        if (!silent) this.alert('Aguarde a leitura da Curva K antes de preparar a sugestão.');
        return false;
      }
      const persistentChanges = Array.isArray(suggestion.curveChanges) ? suggestion.curveChanges : [];
      if (persistentChanges.length) {
        let changed = false;
        persistentChanges.forEach(change => {
          const index = Number(change.index);
          const requested = finite(change.after);
          if (!Number.isInteger(index) || requested === null) return;
          const preview = this.api.previewCurvePoint(index, requested);
          if (!preview?.ok) return;
          preview.preparedFromSuggestion = true;
          preview.suggestionId = suggestion.id || '';
          this.acceptPreview(preview, true);
          changed = changed || preview.changed === true;
        });
        this.renderChart();
        this.renderProposalList();
        this.renderSuggestionFocus(suggestion, { changed });
        return changed;
      }

      if (!silent) this.alert('A sugestão ainda não possui alvo K exato calculado pelo Kotlin.');
      return false;
    }

    acceptPreview(preview, deferRender = false) {
      const index = Number(preview.index);
      if (!Number.isInteger(index)) {
        this.alert('Prévia da Curva K sem índice válido.');
        return;
      }
      if (!preview.changed) this.proposals.delete(index);
      else this.proposals.set(index, preview);
      const input = document.getElementById('curveTargetFactor');
      if (input && index === this.activeIndex && finite(preview.targetFactor) !== null) input.value = String(preview.targetFactor);
      if (index === this.activeIndex) text('curveTargetNormalized', preview.changed ? `${fmt(preview.currentFactor, 4)} → ${fmt(preview.targetFactor, 4)}` : 'Sem alteração');
      if (!deferRender) {
        this.renderChart();
        this.renderProposalList();
      }
    }

    renderChart() {
      const host = document.getElementById('curveChart');
      const points = this.points();
      if (!host) return;
      if (!points.length) {
        host.innerHTML = '<div class="chart-empty">Leia a Curva K para visualizar os 30 pontos.</div>';
        return;
      }
      const width = 920; const height = 350; const padX = 42; const padY = 34;
      const factors = points.map(item => finite(this.proposals.get(Number(item.index))?.targetFactor ?? item.factor) || 0);
      const min = Math.max(0.55, Math.min(...factors, ...points.map(item => finite(item.factor) || 0)) - 0.08);
      const max = Math.max(min + 0.2, Math.max(...factors, ...points.map(item => finite(item.factor) || 0)) + 0.08);
      const xFor = index => padX + (index / Math.max(1, points.length - 1)) * (width - padX * 2);
      const yFor = factor => height - padY - ((factor - min) / (max - min)) * (height - padY * 2);
      const actualPath = points.map((point, index) => `${index ? 'L' : 'M'} ${xFor(index).toFixed(1)} ${yFor(Number(point.factor)).toFixed(1)}`).join(' ');
      const proposalPath = points.map((point, index) => {
        const proposal = this.proposals.get(Number(point.index));
        const value = proposal ? proposal.targetFactor : point.factor;
        return `${index ? 'L' : 'M'} ${xFor(index).toFixed(1)} ${yFor(Number(value)).toFixed(1)}`;
      }).join(' ');
      host.innerHTML = `<svg class="curve-svg" viewBox="0 0 ${width} ${height}" role="img" aria-label="Curva K com 30 pontos editáveis"><path class="curve-line actual" d="${actualPath}"></path><path class="curve-line proposal" d="${proposalPath}"></path>${points.map((point, index) => {
        const selected = Number(point.index) === this.activeIndex;
        const proposed = this.proposals.has(Number(point.index));
        const y = yFor(proposed ? this.proposals.get(Number(point.index)).targetFactor : point.factor).toFixed(1);
        const x = xFor(index).toFixed(1);
        const label = index % 5 === 0 || index === points.length - 1 ? `<text class="curve-point-label" x="${x}" y="${height - 8}" text-anchor="middle">${fmt(point.petrolMs, 1)}</text>` : '';
        return `<circle class="curve-point-hit" data-curve-index="${point.index}" cx="${x}" cy="${y}" r="15" tabindex="0" role="button" aria-label="Ponto ${Number(point.index) + 1}, ${fmt(point.petrolMs, 2)} ms"></circle><circle class="curve-point ${selected ? 'active' : ''} ${proposed ? 'proposed' : ''}" cx="${x}" cy="${y}" r="${selected ? 9 : 7}"></circle>${label}`;
      }).join('')}</svg>`;
      host.querySelectorAll('[data-curve-index]').forEach(point => {
        const select = () => this.selectPoint(Number(point.dataset.curveIndex));
        point.addEventListener('click', select);
        point.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); select(); } });
      });
    }

    persistentCurveChanges(state) {
      const items = Array.isArray(state.calibrationState?.suggestionItems) ? state.calibrationState.suggestionItems : [];
      const changes = new Map();
      items.filter(item => item.target === 'CURVE_K' && item.lifecycle === 'PENDING' && item.actionable === true)
        .forEach(item => (Array.isArray(item.curveChanges) ? item.curveChanges : []).forEach(change => {
          const index = Number(change.index);
          if (Number.isInteger(index)) changes.set(index, change);
        }));
      return changes;
    }

    renderLearning(state) {
      const host = document.getElementById('curveLearningChart');
      const summaryHost = document.getElementById('curveLearningSummary');
      if (!host || !summaryHost) return;
      const maps = state.learning || {};
      const advisor = maps.assistedCalibration || maps.assisted_calibration || {};
      const suggestions = Array.isArray(advisor.kFactorSuggestions) ? advisor.kFactorSuggestions : [];
      const currentPoints = this.points();
      const exactChanges = this.persistentCurveChanges(state);
      const points = Array.from({ length: 30 }, (_, index) => {
        const advice = suggestions.find(item => Number(item.index) === index) || {};
        const current = currentPoints.find(item => Number(item.index) === index) || {};
        const petrolMs = finite(current.petrolMs ?? advice.petrolMs);
        const factor = finite(current.factor);
        const proposal = this.proposals.get(index);
        const exact = exactChanges.get(index);
        return {
          index,
          petrolMs,
          error: finite(advice.errorPercent ?? advice.error_percent ?? advice.relativeErrorPercent),
          confidence: finite(advice.confidence) ?? 0,
          uncertainty: finite(advice.uncertaintyPercent ?? advice.uncertainty_percent),
          actionable: advice.actionable === true,
          reason: advice.decisionReason || advice.readiness || '',
          factor,
          proposedFactor: finite(proposal?.targetFactor) ?? finite(exact?.after) ?? null,
        };
      });
      const signature = JSON.stringify({ points, comparisonCount: advisor.comparisonCount, uniqueVisitCount: advisor.uniqueVisitCount });
      if (signature === this.learningSignature) return;
      this.learningSignature = signature;

      const heading = this.root?.querySelector('.global-learning-surface .surface-heading h3');
      if (heading) heading.textContent = 'Erro global aprendido × Curva K';
      const legend = this.root?.querySelector('.global-learning-surface .global-legend');
      if (legend) legend.innerHTML = '<span>erro aprendido</span><span>atual × proposta</span>';

      const width = 920; const height = 180; const px = 42; const py = 22;
      const xFor = index => px + (index / 29) * (width - px * 2);
      const errors = points.map(item => item.error).filter(value => value !== null);
      const maxAbs = Math.max(3, ...errors.map(Math.abs));
      const errorY = value => height / 2 - (Number(value || 0) / maxAbs) * (height / 2 - py);
      const errorPath = points.filter(item => item.error !== null).map((item, pos) => `${pos ? 'L' : 'M'} ${xFor(item.index).toFixed(1)} ${errorY(item.error).toFixed(1)}`).join(' ');
      const factorValues = points.flatMap(item => [item.factor, item.proposedFactor]).filter(value => value !== null);
      const minFactor = factorValues.length ? Math.min(...factorValues) - 0.05 : 0.8;
      const maxFactor = factorValues.length ? Math.max(...factorValues) + 0.05 : 1.2;
      const factorY = value => height - py - ((Number(value) - minFactor) / Math.max(0.01, maxFactor - minFactor)) * (height - py * 2);
      const actualPath = points.filter(item => item.factor !== null).map((item, pos) => `${pos ? 'L' : 'M'} ${xFor(item.index).toFixed(1)} ${factorY(item.factor).toFixed(1)}`).join(' ');
      const proposedPath = points.filter(item => item.proposedFactor !== null).map((item, pos) => `${pos ? 'L' : 'M'} ${xFor(item.index).toFixed(1)} ${factorY(item.proposedFactor).toFixed(1)}`).join(' ');

      host.innerHTML = `<div class="global-learning-stack">
        <section class="global-error-chart"><small class="global-chart-label">ERRO GLOBAL · alvo 0%</small><svg viewBox="0 0 ${width} ${height}" role="img" aria-label="Erro global aprendido nos 30 pontos"><line x1="${px}" y1="${height / 2}" x2="${width - px}" y2="${height / 2}" class="learn-grid-line"></line>${errorPath ? `<path class="learned-cng-line" d="${errorPath}"></path>` : ''}${points.map(item => item.error === null ? '' : `<circle data-learning-curve-index="${item.index}" class="${item.actionable ? 'learned-cng-point' : 'learned-petrol-point'}" cx="${xFor(item.index).toFixed(1)}" cy="${errorY(item.error).toFixed(1)}" r="${item.index === this.activeIndex ? 7 : 4}"></circle>${item.uncertainty === null ? '' : `<line x1="${xFor(item.index).toFixed(1)}" x2="${xFor(item.index).toFixed(1)}" y1="${errorY(item.error + item.uncertainty).toFixed(1)}" y2="${errorY(item.error - item.uncertainty).toFixed(1)}" class="learn-grid-line"></line>`}`).join('')}</svg></section>
        <section class="global-k-chart"><small class="global-chart-label">CURVA K · atual × proposta</small><svg viewBox="0 0 ${width} ${height}" role="img" aria-label="Curva K atual e proposta nos mesmos 30 pontos">${actualPath ? `<path class="curve-line actual" d="${actualPath}"></path>` : ''}${proposedPath ? `<path class="curve-line proposal" d="${proposedPath}"></path>` : ''}${points.map(item => item.factor === null ? '' : `<circle data-learning-curve-index="${item.index}" class="curve-point ${item.index === this.activeIndex ? 'active' : ''}" cx="${xFor(item.index).toFixed(1)}" cy="${factorY(item.proposedFactor ?? item.factor).toFixed(1)}" r="${item.index === this.activeIndex ? 7 : 4}"></circle>${item.index % 5 === 0 || item.index === 29 ? `<text class="curve-point-label" x="${xFor(item.index).toFixed(1)}" y="${height - 5}" text-anchor="middle">${fmt(item.petrolMs, 1)}</text>` : ''}`).join('')}</svg></section>
      </div>`;
      host.querySelectorAll('[data-learning-curve-index]').forEach(node => node.addEventListener('click', () => this.selectPoint(Number(node.dataset.learningCurveIndex))));

      const actionable = points.filter(item => item.actionable).length;
      const observed = points.filter(item => item.error !== null).length;
      summaryHost.innerHTML = `<div class="editor-heading"><div><small>30 PONTOS FÍSICOS</small><h3>Aprendizado global</h3></div></div><div class="global-summary-grid"><div><small>COMPARAÇÕES</small><b>${Number(advisor.comparisonCount || 0)}</b></div><div><small>VISITAS</small><b>${Number(advisor.uniqueVisitCount || 0)}</b></div><div><small>FAIXAS OBSERVADAS</small><b>${observed}/30</b></div><div><small>PRONTAS</small><b>${actionable}</b></div></div><div id="curveLearningPointContext" class="global-summary-list"></div><p class="empty-copy">O eixo X é Petrol Inj. dos 30 pontos. A linha zero é o alvo do erro. A UI só desenha alvos K exatos vindos do Kotlin.</p>`;
      this.renderLearningPointContext(state, this.activeIndex ?? points.find(item => item.error !== null)?.index ?? 0);
    }

    renderLearningPointContext(state, index) {
      const host = document.getElementById('curveLearningPointContext');
      if (!host) return;
      const maps = state.learning || {};
      const advisor = maps.assistedCalibration || maps.assisted_calibration || {};
      const advice = (Array.isArray(advisor.kFactorSuggestions) ? advisor.kFactorSuggestions : []).find(item => Number(item.index) === Number(index)) || {};
      const current = this.points().find(item => Number(item.index) === Number(index)) || {};
      const proposal = this.proposals.get(Number(index));
      const exact = this.persistentCurveChanges(state).get(Number(index));
      const error = finite(advice.errorPercent ?? advice.error_percent ?? advice.relativeErrorPercent);
      const target = finite(proposal?.targetFactor) ?? finite(exact?.after);
      host.innerHTML = `<div><span>Ponto ${Number(index) + 1} · ${fmt(current.petrolMs ?? advice.petrolMs, 2)} ms</span><b>erro ${error === null ? '—' : `${error > 0 ? '+' : ''}${fmt(error, 1)}%`}</b><small>confiança ${Math.round((finite(advice.confidence) || 0) * 100)}% · incerteza ±${fmt(advice.uncertaintyPercent, 1)}%</small></div><div><span>K atual</span><b>${fmt(current.factor, 4)}</b><small>proposta ${fmt(target, 4)}</small></div>`;
    }

    renderProposalList() {
      const host = document.getElementById('curveProposalList');
      if (!host) return;
      const items = [...this.proposals.values()].sort((a, b) => Number(a.index) - Number(b.index));
      host.innerHTML = items.length ? items.map(item => `<div><span>${fmt(item.petrolMs, 2)} ms</span><b>${fmt(item.currentFactor, 4)} → ${fmt(item.targetFactor, 4)}</b><small>${item.deltaPercent > 0 ? '+' : ''}${fmt(item.deltaPercent, 1)}%</small></div>`).join('') : '<p>Nenhum ponto preparado.</p>';
      const review = document.getElementById('curveReviewButton');
      if (review) { review.disabled = items.length === 0; review.textContent = items.length ? `Revisar ${items.length} ponto${items.length === 1 ? '' : 's'}` : 'Prepare pontos'; }
    }

    renderEvidence(state) {
      const host = document.getElementById('curveEvidenceList');
      if (!host) return;
      const maps = state.learning || {};
      const advisor = maps.assistedCalibration || maps.assisted_calibration || {};
      const petrol = Array.isArray(advisor.petrolCurve) ? advisor.petrolCurve : [];
      const cng = Array.isArray(advisor.cngCurve) ? advisor.cngCurve : [];
      const rawGlobal = Array.isArray(advisor.kFactorSuggestions) ? advisor.kFactorSuggestions : [];
      const actionablePoints = rawGlobal.filter(item => item.actionable === true);
      const comparisonCount = finite(advisor.comparisonCount) ?? (Array.isArray(maps.comparisons) ? maps.comparisons.length : 0);
      const uniqueVisits = finite(advisor.uniqueVisitCount) ?? 0;
      host.innerHTML = `<div class="curve-evidence-summary"><div class="evidence-stat"><b>${comparisonCount}</b><span>comparações gasolina × GNV</span></div><div class="evidence-stat"><b>${uniqueVisits}</b><span>visitas físicas únicas</span></div><div class="evidence-stat"><b>${petrol.length}</b><span>pontos da referência gasolina</span></div><div class="evidence-stat"><b>${cng.length}</b><span>pontos observados no GNV</span></div></div><div class="curve-native-explanation"><header><div><small>EVIDÊNCIA FÍSICA</small><h3>Gasolina × GNV por MAP</h3></div><span>sob demanda</span></header><div class="global-summary-list">${petrol.slice(0, 12).map((item, i) => `<div><span>MAP ${fmt(item.mapBar, 2)} bar</span><b>Gas ${fmt(item.petrolMs, 2)} · GNV ${fmt(cng[i]?.petrolMs, 2)} ms</b><small>${escapeHtml(item.confidenceStage || cng[i]?.confidenceStage || '')}</small></div>`).join('') || '<p class="empty-copy">Ainda sem evidência física global.</p>'}</div><p>${actionablePoints.length} ponto(s) K estão atualmente prontos segundo o assessor Kotlin. Esta seção não calcula correção.</p></div>`;
    }

    renderSuggestionFocus(suggestion, preparedPreview = null) {
      const host = document.getElementById('curveSuggestionFocus');
      if (!host || !suggestion) return;
      this.pendingSuggestion = suggestion;
      host.hidden = false;
      const persistentChanges = Array.isArray(suggestion.curveChanges) ? suggestion.curveChanges : [];
      const prepared = preparedPreview?.changed === true || persistentChanges.some(change => this.proposals.has(Number(change.index)));
      const label = persistentChanges.length
        ? `${persistentChanges.length} ponto${persistentChanges.length === 1 ? '' : 's'} da Curva K`
        : 'aguardando alvo exato do Kotlin';
      host.innerHTML = `<b>${prepared ? 'Sugestão preparada para revisão' : 'Sugestão global em revisão'}</b><span>${escapeHtml(label)}</span><small>${escapeHtml(suggestion.rationale || suggestion.reason || suggestion.explanation || '')}</small><button type="button" data-curve-prepare-suggestion ${this.data && persistentChanges.length ? '' : 'disabled'}>${prepared ? 'Repreparar sugestão' : 'Preparar sugestão'}</button><small>${prepared ? 'Revise o antes/depois; nenhuma escrita foi iniciada.' : 'A prévia é normalizada pelo Kotlin. Não grava na ECU.'}</small>`;
      host.querySelector('[data-curve-prepare-suggestion]')?.addEventListener('click', () => this.prepareSuggestion(suggestion));
    }

    openReview() {
      if (!this.proposals.size) return;
      const host = document.getElementById('curveReviewList');
      const items = [...this.proposals.values()].sort((a, b) => Number(a.index) - Number(b.index));
      if (host) host.innerHTML = items.map(item => `<div><span>Ponto ${Number(item.index) + 1} · ${fmt(item.petrolMs, 2)} ms</span><b>${fmt(item.currentFactor, 4)} → ${fmt(item.targetFactor, 4)}</b></div>`).join('');
      text('curveReviewCount', `${items.length} ponto${items.length === 1 ? '' : 's'}`);
      const button = document.getElementById('curveWriteButton');
      if (button) button.textContent = `Gravar ${items.length} ponto${items.length === 1 ? '' : 's'} na ECU`;
      this.root?.classList.add('is-reviewing');
    }

    closeReview() { this.root?.classList.remove('is-reviewing', 'is-writing', 'has-result'); }

    writeReview() {
      const points = [...this.proposals.values()].map(item => ({ index: Number(item.index), currentRaw: Number(item.currentRaw), targetRaw: Number(item.targetRaw) }));
      if (!points.length) return;
      const result = this.api.writeCurve(points, 'Ajuste manual confirmado na UI clean-slate');
      if (!result?.ok || !result?.started) { this.alert(result?.error || 'A escrita da Curva K não iniciou.'); return; }
      this.writing = true;
      this.root?.classList.remove('is-reviewing');
      this.root?.classList.add('is-writing');
      text('curveOperationTitle', 'Escrita manual da Curva K');
      const bar = document.getElementById('curveOperationProgress');
      if (bar) bar.style.width = '0%';
    }

    alert(message) { this.store.patch({ alert: { level: 'warning', message: String(message || 'Operação indisponível') } }); }
  }

  ns.CurveScreen = CurveScreen;
})(typeof window !== 'undefined' ? window : globalThis);