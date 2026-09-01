(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  function finite(value) { return Number.isFinite(Number(value)) ? Number(value) : null; }
  function fmt(value, digits) {
    const n = finite(value);
    return n === null ? '—' : n.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits });
  }
  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[char]));
  }
  function cellPosition(item) {
    const cell = item && item.cell;
    const row = finite(item?.row ?? item?.cell_row ?? item?.cng_cell_row ?? item?.cngCellRow ?? cell?.row);
    const column = finite(item?.column ?? item?.cell_column ?? item?.cng_cell_column ?? item?.cngCellColumn ?? cell?.column);
    return row === null || column === null ? null : { row: Math.trunc(row), column: Math.trunc(column) };
  }
  function key(row, column) { return `${row}:${column}`; }
  function indexByCell(items) {
    const map = new Map();
    (Array.isArray(items) ? items : []).forEach(item => {
      const pos = cellPosition(item);
      if (!pos) return;
      const itemKey = key(pos.row, pos.column);
      const previous = map.get(itemKey);
      const score = finite(item.confidence) ?? finite(item.samples) ?? finite(item.weight) ?? 0;
      const previousScore = previous ? (finite(previous.confidence) ?? finite(previous.samples) ?? finite(previous.weight) ?? 0) : -1;
      if (!previous || score >= previousScore) map.set(itemKey, item);
    });
    return map;
  }
  function persistentMapSuggestions(state) {
    const map = new Map();
    const items = Array.isArray(state?.calibrationState?.suggestionItems) ? state.calibrationState.suggestionItems : [];
    items.forEach(item => {
      if (item?.target !== 'MAP_K' || !['PENDING', 'OBSERVING'].includes(String(item.lifecycle || ''))) return;
      const change = Array.isArray(item.mapChanges) ? item.mapChanges[0] : null;
      const row = finite(change?.row);
      const column = finite(change?.column);
      if (row === null || column === null) return;
      map.set(key(Math.trunc(row), Math.trunc(column)), item);
    });
    return map;
  }
  function mapSuggestionDelta(item) {
    const change = Array.isArray(item?.mapChanges) ? item.mapChanges[0] : null;
    const before = finite(change?.before);
    const after = finite(change?.after);
    if (before === null || after === null || before === 0) return null;
    return (after / before - 1) * 100;
  }
  function stabilityLabel(value) {
    const state = String(value || '').toUpperCase();
    if (state === 'CONSOLIDATED') return 'Consolidado';
    if (state === 'REVALIDATING') return 'Revalidando';
    if (state === 'LEARNING') return 'Aprendendo';
    return 'Sem evidência';
  }
  function comparisonError(item) {
    return finite(item?.observed_pair?.error_percent ?? item?.predictedErrorPercent ?? item?.errorPercent ?? item?.error_pct ?? item?.error_percent ?? item?.relativeErrorPercent ?? item?.deltaPercent ?? item?.differencePercent ?? item?.error);
  }
  function comparisonTargetMs(item) { return finite(item?.observed_pair?.petrol_target_ms ?? item?.petrol_target_ms ?? item?.petrolTargetMs); }
  function comparisonObservedMs(item) { return finite(item?.observed_pair?.petrol_on_cng_ms ?? item?.petrol_on_cng_ms ?? item?.petrolOnCngMs); }
  function confidence(item) {
    const raw = finite(item?.confidence);
    if (raw !== null) return raw > 1 ? Math.min(1, raw / 100) : Math.max(0, Math.min(1, raw));
    const samples = finite(item?.samples);
    return samples === null ? 0 : Math.min(1, samples / 50);
  }
  function evidenceIndex(model) { return new Map((model?.cells || []).map(cell => [cell.key, cell])); }
  function fuelLabel(value) {
    const fuel = String(value || '').toUpperCase();
    if (fuel.includes('PETROL') || fuel.includes('GASOLINA')) return 'Gasolina';
    if (fuel.includes('CNG') || fuel.includes('GNV') || fuel === 'GAS') return 'GNV';
    return fuel || '—';
  }
  function stateLabel(decision) {
    const state = String(decision?.state || 'OBSERVING_ENGINE').toUpperCase();
    if (decision?.learning_eligible === true || state === 'SAMPLE_ACCEPTED') return 'Evidência aceita';
    if (state === 'FORMING_SAMPLE') return 'Formando evidência';
    if (state === 'FUEL_VERIFYING') return 'Confirmando combustível';
    if (state === 'FUEL_STABLE') return 'Combustível confirmado';
    if (state === 'ENGINE_WARMING') return 'Aguardando temperatura';
    if (state === 'SAMPLE_REJECTED') return 'Janela recusada';
    if (state === 'WINDOW_TIMEOUT') return 'Janela reiniciada';
    if (state === 'TELEMETRY_GAP') return 'Telemetria interrompida';
    if (state === 'CUTOFF') return 'Aprendizado pausado';
    return state.replaceAll('_', ' ').toLowerCase().replace(/^./, char => char.toUpperCase());
  }

  class LearningScreen {
    constructor(store, router, api) {
      this.store = store;
      this.router = router;
      this.api = api;
      this.root = document.querySelector('[data-screen="learning"]');
      this.host = document.getElementById('learningGrid');
      this.detail = document.getElementById('learningCellDetail');
      this.selectedCell = null;
      this.inspectorPane = 'collection';
      this.decisionHistory = [];
      this.lastDecisionHistorySignature = '';
      this.grid = this.host && ns.PhysicalGrid ? new ns.PhysicalGrid(this.host, {
        onCell: (row, column) => {
          this.selectedCell = { row, column };
          this.setInspectorPane('cell');
          this.renderDetail(this.store.get(), row, column);
        },
      }) : null;
      this.ensureInspector();
      this.bind();
    }

    ensureInspector() {
      if (!this.detail) return;
      this.detail.innerHTML = `
        <div class="learning-inspector-tabs" role="tablist" aria-label="Aprendizado">
          <button type="button" data-learning-inspector="cell">Célula</button>
          <button type="button" data-learning-inspector="collection" class="active">Agora</button>
        </div>
        <div id="learningCellPane" class="learning-inspector-pane" data-pane="cell">
          <div class="detail-empty"><b>Toque em uma célula</b><span>Veja gasolina, GNV, diferença aprendida e o próximo passo.</span></div>
        </div>
        <div id="learningCollectionPane" class="learning-inspector-pane active" data-pane="collection"></div>
      `;
      this.cellPane = document.getElementById('learningCellPane');
      this.collectionPane = document.getElementById('learningCollectionPane');
      this.tolerancePane = null;
      this.detail.querySelectorAll('[data-learning-inspector]').forEach(button => {
        button.addEventListener('click', () => this.setInspectorPane(button.dataset.learningInspector));
      });
    }

    setInspectorPane(pane) {
      this.inspectorPane = pane || 'collection';
      this.detail?.querySelectorAll('[data-learning-inspector]').forEach(button => {
        const active = button.dataset.learningInspector === this.inspectorPane;
        button.classList.toggle('active', active);
        button.setAttribute('aria-selected', active ? 'true' : 'false');
      });
      this.detail?.querySelectorAll('.learning-inspector-pane').forEach(node => {
        node.classList.toggle('active', node.dataset.pane === this.inspectorPane);
      });
    }

    bind() {
      this.root?.querySelectorAll('[data-learning-layer]').forEach(button => {
        button.addEventListener('click', () => this.store.patch({ learningLayer: button.dataset.learningLayer }));
      });
    }

    buildEvidenceModel(maps) { return ns.LearningModel?.buildModel ? ns.LearningModel.buildModel(maps || {}) : null; }

    observeDecision(decision) {
      const state = String(decision?.state || '').toUpperCase();
      const code = String(decision?.reason_code || state || '').toUpperCase();
      const meaningful = decision?.learning_eligible === true || [
        'SAMPLE_ACCEPTED', 'SAMPLE_REJECTED', 'WINDOW_TIMEOUT', 'TELEMETRY_GAP',
        'FUEL_STABLE', 'ENGINE_WARMING', 'INVALID', 'PLAUSIBILITY_REJECTED',
      ].includes(state) || ['SAMPLE_ACCEPTED', 'SAMPLE_ACCEPTED_EARLY', 'PLAUSIBILITY_REJECTED'].includes(code);
      if (!meaningful) return;
      const row = finite(decision?.cell_row);
      const column = finite(decision?.cell_column);
      const cell = row !== null && column !== null && row >= 0 && column >= 0 ? `${row + 1}×${column + 1}` : '—';
      const signature = [state, code, decision?.learning_eligible === true, decision?.reason || '', decision?.fuel_confirmed || '', cell].join('|');
      if (!signature || signature === this.lastDecisionHistorySignature) return;
      this.lastDecisionHistorySignature = signature;
      const level = decision?.learning_eligible === true || state === 'SAMPLE_ACCEPTED'
        ? 'accepted'
        : ['SAMPLE_REJECTED', 'WINDOW_TIMEOUT', 'TELEMETRY_GAP', 'INVALID'].includes(state) || code === 'PLAUSIBILITY_REJECTED'
          ? 'rejected'
          : 'info';
      this.decisionHistory.unshift({
        time: new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
        level,
        label: stateLabel(decision),
        reason: decision?.reason || code || 'Decisão observada',
        code,
        fuel: fuelLabel(decision?.fuel_confirmed),
        cell,
      });
      if (this.decisionHistory.length > 6) this.decisionHistory.length = 6;
    }

    render(state) {
      if (!this.root || !this.grid) return;
      this.observeDecision(state.learningDecision || {});
      const maps = state.learning || {};
      const axes = maps.grid || {};
      this.grid.setAxes?.(axes.rpmBins || [], axes.petrolBins || []);
      const model = this.buildEvidenceModel(maps);
      const evidence = evidenceIndex(model);
      const layer = state.learningLayer || 'comparison';
      this.root.querySelectorAll('[data-learning-layer]').forEach(button => button.classList.toggle('active', button.dataset.learningLayer === layer));

      const comparisons = indexByCell(maps.comparisons);
      const advisor = maps.assistedCalibration || maps.assisted_calibration || {};
      const predictions = indexByCell(advisor.mapResidualPredictions);
      const stability = indexByCell(state.calibrationState?.learningStability?.map || []);
      const mapSuggestions = persistentMapSuggestions(state);
      const persistentItems = Array.isArray(state.calibrationState?.suggestionItems) ? state.calibrationState.suggestionItems : [];

      this.grid.cells.forEach((cell, cellKey) => {
        const learned = evidence.get(cellKey);
        const stable = stability.get(cellKey);
        let source = null;
        let cellText = '·';
        let subtext = '';
        let heat = 0;
        let tone = 'neutral';
        if (layer === 'petrol') {
          source = learned?.petrol || null;
          if (source) {
            const meanMs = finite(source.petrolMs);
            cellText = meanMs === null ? '•' : fmt(meanMs, 2);
            subtext = meanMs === null ? `${Math.round(source.samples || 0)} am.` : 'ms';
            heat = confidence(source);
            tone = 'petrol';
          }
        } else if (layer === 'cng') {
          source = learned?.cng || null;
          if (source) {
            const meanMs = finite(source.petrolMs);
            cellText = meanMs === null ? '•' : fmt(meanMs, 2);
            subtext = meanMs === null ? `${Math.round(source.samples || 0)} am.` : 'ms';
            heat = confidence(source);
            tone = 'cng';
          }
        } else if (layer === 'comparison') {
          const prediction = ns.LearningModel?.localComparisonPrediction
            ? ns.LearningModel.localComparisonPrediction(predictions.get(cellKey))
            : null;
          source = comparisons.get(cellKey) || stable || prediction || null;
          const consolidated = finite(stable?.consolidatedErrorPercent);
          const rawError = comparisonError(comparisons.get(cellKey));
          const predictedError = comparisonError(prediction);
          const error = consolidated ?? rawError ?? predictedError;
          const stableState = String(stable?.state || '').toUpperCase();
          if (error !== null) {
            cellText = `${error > 0 ? '+' : ''}${fmt(error, 1)}%`;
            subtext = stableState === 'REVALIDATING'
              ? 'revalidando'
              : stableState === 'CONSOLIDATED'
                ? 'consolidado'
                : stableState === 'LEARNING'
                  ? 'aprendendo'
                  : rawError !== null
                    ? 'par direto'
                    : String(prediction?.supportType || '') === 'GLOBAL_ONLY'
                      ? 'tendência global'
                      : 'previsão local';
            heat = Math.min(1, Math.abs(error) / 8);
            tone = Math.abs(error) <= 1.5 ? 'good' : error > 0 ? 'high' : 'low';
          } else if (learned?.state === ns.LearningModel?.STATES?.COMPARABLE) {
            cellText = '…';
            subtext = stableState === 'LEARNING' ? 'aprendendo' : 'comparando';
            heat = 0.2;
          }
        } else if (layer === 'suggestion') {
          source = mapSuggestions.get(cellKey);
          const delta = mapSuggestionDelta(source);
          if (source && delta !== null) {
            cellText = `${delta > 0 ? '+' : ''}${fmt(delta, 1)}%`;
            subtext = source.actionable === true ? 'revisar' : String(source.stabilityState || '').toUpperCase() === 'REVALIDATING' ? 'revalidando' : 'observando';
            heat = confidence(source);
            tone = source.actionable === true ? 'suggestion' : 'neutral';
          }
        }
        this.grid.updateCell(Number(cell.dataset.row), Number(cell.dataset.column), {
          text: cellText, subtext, heat, tone, hasData: !!source || !!learned || !!stable,
          state: stable?.state || learned?.state || '',
          selected: this.selectedCell?.row === Number(cell.dataset.row) && this.selectedCell?.column === Number(cell.dataset.column),
        });
      });

      const coverage = document.getElementById('learningCoverageSummary');
      if (coverage && model) {
        const petrolCount = model.counts.petrol + model.counts.comparable;
        const cngCount = model.counts.cng + model.counts.comparable;
        coverage.textContent = `${petrolCount} gasolina · ${cngCount} GNV atual · ${model.counts.ready} comparáveis`;
      }
      const suggestionSummary = document.getElementById('learningSuggestionSummary');
      if (suggestionSummary) {
        const globalCount = persistentItems.filter(item => item.lifecycle === 'PENDING' && item.target === 'CURVE_K' && item.actionable === true).length;
        const localCount = persistentItems.filter(item => item.lifecycle === 'PENDING' && item.target === 'MAP_K' && item.actionable === true).length;
        suggestionSummary.textContent = `${globalCount} global · ${localCount} local`;
      }

      this.renderCollection(state);
      if (this.selectedCell) this.renderDetail(state, this.selectedCell.row, this.selectedCell.column);
    }

    renderCollection(state) {
      if (!this.collectionPane) return;
      const decision = state.learningDecision || {};
      const learningStatus = state.learningStatus || {};
      const restoring = learningStatus.restoring === true || String(learningStatus.state || '').toUpperCase() === 'LEARNING_RESTORING';
      const live = state.telemetry?.live || {};
      const interpolation = state.telemetry?.interpolation || {};
      const cell = interpolation.cell || {};
      const eligible = decision.learning_eligible === true;
      const row = finite(decision.cell_row ?? cell.row);
      const column = finite(decision.cell_column ?? cell.column);
      const rpm = finite(interpolation.rpm ?? live.rpm);
      const petrolMs = finite(interpolation.petrolMs ?? live.petrol_ms ?? live.petrolMs);
      const mapBar = finite(interpolation.mapBar ?? live.load_bar ?? live.map_bar);
      const reason = restoring
        ? (learningStatus.reason || 'Restaurando a memória salva.')
        : (decision.reason || live.sample_reason || 'Observando o motor.');
      const status = restoring ? 'Restaurando' : eligible ? 'Leitura aprendida' : stateLabel(decision);
      const level = restoring ? 'waiting' : eligible ? 'accepted' : 'observing';
      const historyRows = this.decisionHistory.length
        ? this.decisionHistory.slice(0, 4).map(item => `<div data-level="${escapeHtml(item.level)}"><time>${escapeHtml(item.time)}</time><b>${escapeHtml(item.label)}</b><span>${escapeHtml(item.reason)}</span></div>`).join('')
        : '<p class="empty-copy">Nenhuma decisão recente nesta tela.</p>';

      this.collectionPane.innerHTML = `
        <section class="learning-now-card" data-level="${level}">
          <header><div><small>COLETA AUTOMÁTICA</small><h3>${escapeHtml(status)}</h3></div>${row !== null && column !== null && row >= 0 && column >= 0 ? `<span>Célula ${row + 1}×${column + 1}</span>` : ''}</header>
          <p>${escapeHtml(reason)}</p>
          <div class="learning-now-values">
            <div><small>RPM</small><b>${rpm === null ? '—' : Math.round(rpm).toLocaleString('pt-BR')}</b></div>
            <div><small>MAP</small><b>${fmt(mapBar, 3)} bar</b></div>
            <div><small>PETROL INJ.</small><b>${fmt(petrolMs, 2)} ms</b></div>
          </div>
          <p class="learning-plain-note">O app observa continuamente. Esta situação informa se a leitura atual já entrou na memória.</p>
        </section>
        <details class="learning-technical-details">
          <summary>Detalhes técnicos</summary>
          <div class="decision-history-list">${historyRows}</div>
          <p class="learning-light-note">RPM × MAP define a condição física. A posição ao vivo é apenas informativa. A interpolação bilinear continua no Kotlin.</p>
        </details>
      `;
    }

    renderDetail(state, row, column) {
      if (!this.cellPane) return;
      const maps = state.learning || {};
      const model = this.buildEvidenceModel(maps);
      const learned = evidenceIndex(model).get(key(row, column));
      const comparison = indexByCell(maps.comparisons).get(key(row, column));
      const advisor = maps.assistedCalibration || maps.assisted_calibration || {};
      const prediction = ns.LearningModel?.localComparisonPrediction
        ? ns.LearningModel.localComparisonPrediction(indexByCell(advisor.mapResidualPredictions).get(key(row, column)))
        : null;
      const stability = indexByCell(state.calibrationState?.learningStability?.map || []).get(key(row, column));
      const suggestion = persistentMapSuggestions(state).get(key(row, column));
      const rawError = comparisonError(comparison);
      const consolidatedError = finite(stability?.consolidatedErrorPercent);
      const recentError = finite(stability?.recentErrorPercent);
      const displayError = consolidatedError ?? rawError;
      const targetMs = comparisonTargetMs(comparison);
      const observedMs = comparisonObservedMs(comparison);
      const observedPair = comparison?.observed_pair || comparison || null;
      const referenceSupport = comparison?.reference_support || null;
      const delta = mapSuggestionDelta(suggestion);
      const petrolSamples = finite(learned?.petrol?.samples) ?? 0;
      const cngSamples = finite(learned?.cng?.samples) ?? 0;
      const petrolVisits = finite(learned?.petrol?.visits) ?? 0;
      const cngVisits = finite(learned?.cng?.visits) ?? 0;
      const petrolSessions = finite(learned?.petrol?.sessions) ?? 0;
      const cngSessions = finite(learned?.cng?.sessions) ?? 0;
      const petrolMeanMs = finite(learned?.petrol?.petrolMs);
      const cngMeanMs = finite(learned?.cng?.petrolMs);
      const petrolRpm = finite(learned?.petrol?.rpm);
      const cngRpm = finite(learned?.cng?.rpm);
      const petrolMap = finite(learned?.petrol?.mapBar);
      const cngMap = finite(learned?.cng?.mapBar);
      const historicalEpochs = [...new Set((learned?.previousCng || []).map(item => item.epoch))].sort((a, b) => b - a);
      const axes = maps.grid || {};
      const axisRpm = finite(axes.rpmBins?.[column]);
      const axisPetrol = finite(axes.petrolBins?.[row]);
      const rpmLabel = finite(learned?.rpm) ?? axisRpm;
      const petrolLabel = finite(learned?.petrolMs) ?? axisPetrol;
      const stabilityState = String(stability?.state || 'NO_EVIDENCE').toUpperCase();
      const comparisonText = displayError !== null && targetMs !== null && observedMs !== null
        ? `${fmt(targetMs, 2)} → ${fmt(observedMs, 2)} ms · ${displayError > 0 ? '+' : ''}${fmt(displayError, 1)}%${consolidatedError !== null ? ' consolidado' : ''}`
        : displayError !== null ? `${displayError > 0 ? '+' : ''}${fmt(displayError, 1)}%` : 'ainda não existe par equivalente válido';
      const pairCondition = observedPair
        ? `${finite(observedPair.rpm) === null ? 'RPM —' : `${Math.round(finite(observedPair.rpm)).toLocaleString('pt-BR')} RPM`} · MAP ${fmt(observedPair.map_bar, 3)} bar · qualidade ${Math.round((finite(observedPair.quality) || 0) * 100)}%`
        : '—';
      const supportType = String(referenceSupport?.support_type || 'UNKNOWN').toUpperCase();
      const supportLabel = supportType === 'DIRECT' ? 'direto' : supportType === 'NEAR' ? 'vizinho interpolado' : 'legado/não informado';
      const supportDistance = finite(referenceSupport?.nearest_distance);
      const supportText = referenceSupport
        ? `${supportLabel} · ${Math.round(finite(referenceSupport.selected_candidates) || 0)} referência(s) · dispersão ${fmt(referenceSupport.spread_ms, 3)} ms${supportDistance === null ? '' : ` · distância ${fmt(supportDistance, 2)}`}`
        : 'procedência não disponível em evidência legada';
      const predictionType = String(prediction?.supportType || 'UNKNOWN').toUpperCase();
      const predictionText = prediction
        ? `${predictionType === 'DIRECT' ? 'suporte direto' : predictionType === 'NEAR' ? 'vizinho interpolado' : predictionType === 'GLOBAL_ONLY' ? 'somente tendência global' : 'sem suporte'} · total ${fmt(prediction.predictedErrorPercent, 1)}% = global ${fmt(prediction.globalErrorPercent, 1)}% + local ${fmt(prediction.localResidualPercent, 1)}% · incerteza ${fmt(prediction.uncertaintyPercent, 1)}%`
        : 'ainda não calculada';
      const recentText = stabilityState === 'REVALIDATING' && recentError !== null
        ? `${recentError > 0 ? '+' : ''}${fmt(recentError, 1)}% · ${Math.round(finite(stability?.recentUniqueVisits) || 0)} visita(s) nova(s)`
        : stabilityState === 'CONSOLIDATED'
          ? 'sem divergência recente relevante'
          : recentError !== null ? `${recentError > 0 ? '+' : ''}${fmt(recentError, 1)}%` : '—';
      const whereText = observedPair
        ? `${pairCondition}. RPM × MAP define a condição física; temperatura apenas contextualiza quando existe dos dois lados.`
        : `${rpmLabel === null ? 'RPM —' : `${Math.round(rpmLabel).toLocaleString('pt-BR')} RPM`} · MAP ${fmt(cngMap ?? petrolMap, 3)} bar. RPM × MAP define a condição física.`;
      const gasolineExpectedText = targetMs !== null
        ? `${fmt(targetMs, 2)} ms no par equivalente usado no cálculo`
        : learned?.petrol ? `${fmt(petrolMeanMs, 2)} ms no resumo agregado da região` : 'ainda sem referência equivalente';
      const cngObservedText = observedMs !== null
        ? `${fmt(observedMs, 2)} ms no par observado`
        : learned?.cng ? `${fmt(cngMeanMs, 2)} ms no resumo agregado da região` : 'ainda sem observação GNV';
      const meaningText = delta === null
        ? 'A região continua em observação; nenhuma mudança foi registrada.'
        : `${delta > 0 ? '+' : ''}${fmt(delta, 1)}% no Mapa K · ${suggestion?.actionable === true ? 'sugestão pronta para revisão humana' : 'evidência ainda em observação'}`;
      this.cellPane.innerHTML = `
        <div class="detail-eyebrow">CÉLULA ${row + 1} × ${column + 1}</div>
        <h3>${rpmLabel === null ? 'RPM —' : `${Math.round(rpmLabel).toLocaleString('pt-BR')} RPM`} · ${fmt(petrolLabel, 1)} ms</h3>
        <div class="learning-primary-facts">
          <div><small>Gasolina esperada</small><b>${gasolineExpectedText}</b></div>
          <div><small>GNV observado</small><b>${cngObservedText}</b></div>
          <div><small>Diferença aprendida</small><b>${comparisonText}</b></div>
          <div><small>Situação</small><b>${escapeHtml(stabilityLabel(stabilityState))}</b></div>
        </div>
        <p class="learning-reason">${escapeHtml(meaningText)}</p>
        <button class="primary wide" type="button" data-edit-learning-cell>${suggestion?.actionable ? 'Revisar sugestão nesta célula' : 'Abrir esta célula no Mapa K'}</button>
        <small class="manual-edit-contract">Abrir o editor não escreve na ECU. Revisão, confirmação, ACK e readback continuam obrigatórios.</small>
        <details class="learning-technical-details">
          <summary>Detalhes técnicos</summary>
          <dl class="detail-list">
            <div><dt>Onde</dt><dd>${whereText}</dd></div>
            <div><dt>Por que confiar</dt><dd>${supportText}. Precisão local: gasolina ${Math.round(confidence(learned?.petrol) * 100)}%, GNV ${Math.round(confidence(learned?.cng) * 100)}%.</dd></div>
            <div><dt>Resumo projetado da célula</dt><dd>Não é o par usado no cálculo; agrega evidências que influenciam esta célula.</dd></div>
            <div><dt>Memória consolidada</dt><dd>${consolidatedError === null ? 'ainda não consolidada' : `${consolidatedError > 0 ? '+' : ''}${fmt(consolidatedError, 1)}% · confiança ${Math.round((finite(stability?.confidence) || 0) * 100)}%`}</dd></div>
            <div><dt>Evidência recente</dt><dd>${recentText}</dd></div>
            <div><dt>Massa local</dt><dd>Gasolina: ${Math.round(petrolSamples)} amostras, ${petrolVisits} visitas, ${petrolSessions} sessões. GNV: ${Math.round(cngSamples)} amostras, ${cngVisits} visitas, ${cngSessions} sessões.</dd></div>
            <div><dt>Predição RPM × MAP</dt><dd>${predictionText}</dd></div>
            <div><dt>Histórico GNV</dt><dd>${historicalEpochs.length ? `épocas ${historicalEpochs.join(', ')} · somente consulta` : 'nenhum'}</dd></div>
          </dl>
        </details>
      `;
      this.cellPane.querySelector('[data-edit-learning-cell]')?.addEventListener('click', () => {
        this.router.navigate('map', {
          origin: 'learning',
          cell: { row, column },
          physical: { rpm: rpmLabel, petrolMs: petrolLabel },
          suggestion: suggestion?.actionable ? suggestion : null,
        });
      });
    }
  }

  ns.LearningScreen = LearningScreen;
})(typeof window !== 'undefined' ? window : globalThis);
