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
    return String(value ?? '').replace(/[&<>\"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '\"': '&quot;' }[char]));
  }
  function comparisonTargetMs(item) {
    return finite(item?.observed_pair?.petrol_target_ms ?? item?.petrol_target_ms ?? item?.petrolTargetMs);
  }
  function comparisonObservedMs(item) {
    return finite(item?.observed_pair?.petrol_on_cng_ms ?? item?.petrol_on_cng_ms ?? item?.petrolOnCngMs);
  }
  function comparisonError(item) {
    return finite(item?.observed_pair?.error_percent ?? item?.error_percent ?? item?.errorPercent ?? item?.relativeErrorPercent);
  }
  function comparisonQuality(item) {
    const raw = finite(item?.observed_pair?.quality ?? item?.quality ?? item?.confidence);
    if (raw === null) return 0;
    return raw > 1 ? Math.min(1, raw / 100) : Math.max(0, Math.min(1, raw));
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
      this.bind();
    }

    bind() {
      document.getElementById('curveReadButton')?.addEventListener('click', () => this.startRead());
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
      if (view !== 'learning' && view !== 'editor') return false;
      this.view = view;
      document.querySelectorAll('[data-curve-view]').forEach(button => button.classList.toggle('active', button.dataset.curveView === this.view));
      document.querySelectorAll('[data-curve-panel]').forEach(panel => panel.classList.toggle('active', panel.dataset.curvePanel === this.view));
      if (this.view === 'learning') this.renderLearning(this.store.get());
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

    directComparisons(state) {
      const maps = state.learning || {};
      return (Array.isArray(maps.comparisons) ? maps.comparisons : [])
        .map(item => ({
          raw: item,
          targetMs: comparisonTargetMs(item),
          observedMs: comparisonObservedMs(item),
          error: comparisonError(item),
          quality: comparisonQuality(item),
          rpm: finite(item?.observed_pair?.rpm ?? item?.rpm),
          mapBar: finite(item?.observed_pair?.map_bar ?? item?.map_bar ?? item?.mapBar),
        }))
        .filter(item => item.targetMs !== null && item.observedMs !== null && item.error !== null)
        .sort((a, b) => a.targetMs - b.targetMs);
    }

    renderLearning(state) {
      const host = document.getElementById('curveLearningChart');
      const summaryHost = document.getElementById('curveLearningSummary');
      if (!host || !summaryHost) return;
      const comparisons = this.directComparisons(state);
      const calibrationState = state.calibrationState || {};
      const latestComparison = calibrationState.latestComparison && typeof calibrationState.latestComparison === 'object'
        ? calibrationState.latestComparison
        : null;
      const proposal = calibrationState.proposal && typeof calibrationState.proposal === 'object'
        ? calibrationState.proposal
        : {};
      const currentPoints = this.points();
      const signature = JSON.stringify({
        comparisons: comparisons.map(item => [item.targetMs, item.observedMs, item.error, item.quality]),
        latestComparison,
        proposal,
        current: currentPoints.map(item => [item.index, item.petrolMs, item.factor]),
      });
      if (signature === this.learningSignature) return;
      this.learningSignature = signature;

      const heading = this.root?.querySelector('.global-learning-surface .surface-heading h3');
      if (heading) heading.textContent = 'Desvio medido × Curva K';
      const legend = this.root?.querySelector('.global-learning-surface .global-legend');
      if (legend) legend.innerHTML = '<span>pares físicos medidos</span><span>Curva K atual da ECU</span>';

      const width = 920; const height = 180; const px = 42; const py = 22;
      const targetValues = comparisons.map(item => item.targetMs).concat(currentPoints.map(item => finite(item.petrolMs)).filter(value => value !== null));
      const minMs = targetValues.length ? Math.min(...targetValues) : 0;
      const maxMs = targetValues.length ? Math.max(...targetValues) : 1;
      const xForMs = value => px + ((Number(value) - minMs) / Math.max(0.01, maxMs - minMs)) * (width - px * 2);
      const errors = comparisons.map(item => item.error);
      const maxAbs = Math.max(3, ...errors.map(Math.abs));
      const errorY = value => height / 2 - (Number(value || 0) / maxAbs) * (height / 2 - py);

      const factorValues = currentPoints.map(item => finite(item.factor)).filter(value => value !== null);
      const minFactor = factorValues.length ? Math.min(...factorValues) - 0.05 : 0.8;
      const maxFactor = factorValues.length ? Math.max(...factorValues) + 0.05 : 1.2;
      const factorY = value => height - py - ((Number(value) - minFactor) / Math.max(0.01, maxFactor - minFactor)) * (height - py * 2);
      const actualPath = currentPoints.filter(item => finite(item.petrolMs) !== null && finite(item.factor) !== null)
        .map((item, pos) => `${pos ? 'L' : 'M'} ${xForMs(item.petrolMs).toFixed(1)} ${factorY(item.factor).toFixed(1)}`)
        .join(' ');

      host.innerHTML = `<div class="global-learning-stack">
        <section class="global-error-chart"><small class="global-chart-label">DESVIO MEDIDO · alvo 0%</small><svg viewBox="0 0 ${width} ${height}" role="img" aria-label="Pares físicos gasolina e GNV por Petrol Inj."><line x1="${px}" y1="${height / 2}" x2="${width - px}" y2="${height / 2}" class="learn-grid-line"></line>${comparisons.map(item => `<circle class="${Math.abs(item.error) <= 1.5 ? 'learned-petrol-point' : 'learned-cng-point'}" cx="${xForMs(item.targetMs).toFixed(1)}" cy="${errorY(item.error).toFixed(1)}" r="${Math.max(3, 3 + item.quality * 3).toFixed(1)}"><title>${fmt(item.targetMs, 2)} → ${fmt(item.observedMs, 2)} ms · ${item.error > 0 ? '+' : ''}${fmt(item.error, 2)}%</title></circle>`).join('')}</svg></section>
        <section class="global-k-chart"><small class="global-chart-label">CURVA K · readback atual</small><svg viewBox="0 0 ${width} ${height}" role="img" aria-label="Curva K atual confirmada pela ECU">${actualPath ? `<path class="curve-line actual" d="${actualPath}"></path>` : ''}${currentPoints.filter(item => finite(item.petrolMs) !== null && finite(item.factor) !== null).map(item => `<circle class="curve-point ${Number(item.index) === this.activeIndex ? 'active' : ''}" cx="${xForMs(item.petrolMs).toFixed(1)}" cy="${factorY(item.factor).toFixed(1)}" r="${Number(item.index) === this.activeIndex ? 7 : 4}"></circle>`).join('')}</svg></section>
      </div>`;

      const gain = finite(proposal.actuatorGain);
      const multiplier = finite(proposal.correctionMultiplier);
      const proposalState = String(proposal.state || (proposal.available === false ? 'WAITING_FOR_EQUIVALENT_FUEL_EVIDENCE' : 'MEASURE_ACTUATOR_GAIN'));
      const proposalText = multiplier !== null
        ? `multiplicador ${fmt(multiplier, 4)} disponível para revisão manual`
        : gain !== null
          ? `ganho ${fmt(gain, 4)} observado; sem multiplicador emitido`
          : 'ganho causal ainda não medido; nenhum alvo K inventado';
      summaryHost.innerHTML = `<div class="editor-heading"><div><small>AUTORIDADE ÚNICA</small><h3>BlueCausalEngine</h3></div></div><div class="global-summary-grid"><div><small>PARES MEDIDOS</small><b>${comparisons.length}</b></div><div><small>GASOLINA</small><b>${Number(calibrationState.petrolEvidence || 0)}</b></div><div><small>GNV ATUAL</small><b>${Number(calibrationState.activeCngEvidence || 0)}</b></div><div><small>ATIVOS BLUE</small><b>${Number(calibrationState.activeComparisons || 0)}</b></div></div><div id="curveLearningPointContext" class="global-summary-list"></div><p class="empty-copy">${escapeHtml(proposalState)} · ${escapeHtml(proposalText)}. A medição não é convertida em correção pela WebView.</p>`;
      this.renderLearningPointContext(state, this.activeIndex ?? 0);
    }

    renderLearningPointContext(state, index) {
      const host = document.getElementById('curveLearningPointContext');
      if (!host) return;
      const current = this.points().find(item => Number(item.index) === Number(index)) || {};
      const targetMs = finite(current.petrolMs);
      const comparisons = this.directComparisons(state);
      const nearest = targetMs === null || !comparisons.length
        ? null
        : comparisons.slice().sort((a, b) => Math.abs(a.targetMs - targetMs) - Math.abs(b.targetMs - targetMs))[0];
      const manual = this.proposals.get(Number(index));
      const calibrationState = state.calibrationState || {};
      const latestComparison = calibrationState.latestComparison && typeof calibrationState.latestComparison === 'object'
        ? calibrationState.latestComparison
        : null;
      const proposal = calibrationState.proposal && typeof calibrationState.proposal === 'object'
        ? calibrationState.proposal
        : {};
      const multiplier = finite(proposal.correctionMultiplier);
      const nearestCopy = nearest
        ? `medido mais próximo ${fmt(nearest.targetMs, 2)} → ${fmt(nearest.observedMs, 2)} ms · ${nearest.error > 0 ? '+' : ''}${fmt(nearest.error, 2)}%`
        : 'nenhum par físico medido próximo';
      const blueCopy = latestComparison
        ? `último Blue ${fmt(latestComparison.petrolReferenceMs, 2)} → ${fmt(latestComparison.petrolOnCngMs, 2)} ms · ${fmt(latestComparison.errorPercent, 2)}%`
        : 'Blue ainda sem comparação reconciliada';
      host.innerHTML = `<div><span>Ponto ${Number(index) + 1} · ${fmt(current.petrolMs, 2)} ms</span><b>${escapeHtml(nearestCopy)}</b><small>${escapeHtml(blueCopy)}</small></div><div><span>K atual</span><b>${fmt(current.factor, 4)}</b><small>${manual ? `prévia manual ${fmt(manual.targetFactor, 4)}` : multiplier !== null ? `multiplicador Blue ${fmt(multiplier, 4)} · sem alvo de ponto automático` : 'sem alvo automático'}</small></div>`;
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
      const comparisons = this.directComparisons(state);
      const calibrationState = state.calibrationState || {};
      const latestComparison = calibrationState.latestComparison && typeof calibrationState.latestComparison === 'object'
        ? calibrationState.latestComparison
        : null;
      const proposal = calibrationState.proposal && typeof calibrationState.proposal === 'object'
        ? calibrationState.proposal
        : {};
      const multiplier = finite(proposal.correctionMultiplier);
      host.innerHTML = `<div class="curve-evidence-summary"><div class="evidence-stat"><b>${comparisons.length}</b><span>pares físicos medidos</span></div><div class="evidence-stat"><b>${Number(calibrationState.petrolEvidence || 0)}</b><span>evidências gasolina</span></div><div class="evidence-stat"><b>${Number(calibrationState.activeCngEvidence || 0)}</b><span>evidências GNV atuais</span></div><div class="evidence-stat"><b>${Number(calibrationState.activeComparisons || 0)}</b><span>comparações Blue ativas</span></div></div><div class="curve-native-explanation"><header><div><small>EVIDÊNCIA FÍSICA</small><h3>Gasolina × GNV medidos</h3></div><span>BlueCausalEngine</span></header><div class="global-summary-list">${comparisons.slice(-12).reverse().map(item => `<div><span>${fmt(item.rpm, 0)} RPM · MAP ${fmt(item.mapBar, 3)} bar</span><b>${fmt(item.targetMs, 2)} → ${fmt(item.observedMs, 2)} ms</b><small>${item.error > 0 ? '+' : ''}${fmt(item.error, 2)}% · qualidade ${Math.round(item.quality * 100)}%</small></div>`).join('') || '<p class="empty-copy">Ainda sem par físico gasolina × GNV.</p>'}</div><p>${latestComparison ? `Última comparação reconciliada: ${fmt(latestComparison.errorPercent, 2)}%. ` : ''}${multiplier !== null ? `O núcleo emitiu multiplicador ${fmt(multiplier, 4)} para revisão manual.` : `Estado ${escapeHtml(proposal.state || 'MEASURE_ACTUATOR_GAIN')}: nenhuma correção exata é inventada pela interface.`}</p></div>`;
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
