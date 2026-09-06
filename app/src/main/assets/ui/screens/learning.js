(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  function finite(value) { return Number.isFinite(Number(value)) ? Number(value) : null; }
  function fmt(value, digits) {
    const n = finite(value);
    return n === null ? '—' : n.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits });
  }
  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>\"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '\"': '&quot;' }[char]));
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
      const score = finite(item.confidence) ?? finite(item.quality) ?? finite(item.samples) ?? finite(item.weight) ?? 0;
      const previousScore = previous ? (finite(previous.confidence) ?? finite(previous.quality) ?? finite(previous.samples) ?? finite(previous.weight) ?? 0) : -1;
      if (!previous || score >= previousScore) map.set(itemKey, item);
    });
    return map;
  }
  function comparisonError(item) {
    return finite(item?.observed_pair?.error_percent ?? item?.errorPercent ?? item?.error_pct ?? item?.error_percent ?? item?.relativeErrorPercent ?? item?.differencePercent ?? item?.error);
  }
  function comparisonTargetMs(item) {
    return finite(item?.petrolReferenceMs ?? item?.observed_pair?.petrol_target_ms ?? item?.petrol_target_ms ?? item?.petrolTargetMs);
  }
  function comparisonObservedMs(item) {
    return finite(item?.observed_pair?.petrol_on_cng_ms ?? item?.petrol_on_cng_ms ?? item?.petrolOnCngMs);
  }
  function confidence(item) {
    const raw = finite(item?.confidence ?? item?.quality);
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
  function blueProposalSummary(calibrationState) {
    const blue = calibrationState || {};
    const proposal = blue.proposal && typeof blue.proposal === 'object' ? blue.proposal : {};
    const multiplier = finite(proposal.correctionMultiplier);
    if (proposal.available === true && multiplier !== null) {
      return { available: true, label: `Blue: multiplicador ${fmt(multiplier, 4)}`, detail: 'proposta separada da medição' };
    }
    return {
      available: false,
      label: 'Blue: aguardando ganho causal',
      detail: proposal.state || blue.reason || 'sem alvo K inventado',
    };
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
        <div class="learning-inspector-tabs" role="tablist" aria-label="Detalhes do aprendizado">
          <button type="button" data-learning-inspector="cell">Célula</button>
          <button type="button" data-learning-inspector="collection" class="active">Coleta</button>
        </div>
        <div id="learningCellPane" class="learning-inspector-pane" data-pane="cell">
          <div class="detail-empty"><b>Toque em uma célula</b><span>Veja gasolina, GNV e o desvio realmente medido. A proposta Blue aparece separada.</span></div>
        </div>
        <div id="learningCollectionPane" class="learning-inspector-pane active" data-pane="collection"></div>
      `;
      this.cellPane = document.getElementById('learningCellPane');
      this.collectionPane = document.getElementById('learningCollectionPane');
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
      const layer = ['petrol', 'cng', 'comparison'].includes(state.learningLayer) ? state.learningLayer : 'comparison';
      this.root.querySelectorAll('[data-learning-layer]').forEach(button => button.classList.toggle('active', button.dataset.learningLayer === layer));

      const comparisons = indexByCell(maps.comparisons);
      this.grid.cells.forEach((cell, cellKey) => {
        const learned = evidence.get(cellKey);
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
            subtext = meanMs === null ? `${Math.round(source.samples || 0)} am.` : 'ms gasolina';
            heat = confidence(source);
            tone = 'petrol';
          }
        } else if (layer === 'cng') {
          source = learned?.cng || null;
          if (source) {
            const meanMs = finite(source.petrolMs);
            cellText = meanMs === null ? '•' : fmt(meanMs, 2);
            subtext = meanMs === null ? `${Math.round(source.samples || 0)} am.` : 'ms no GNV';
            heat = confidence(source);
            tone = 'cng';
          }
        } else if (layer === 'comparison') {
          source = comparisons.get(cellKey) || null;
          const error = comparisonError(source);
          if (error !== null) {
            cellText = `${error > 0 ? '+' : ''}${fmt(error, 1)}%`;
            subtext = 'par medido';
            heat = Math.min(1, Math.abs(error) / 8);
            tone = Math.abs(error) <= 1.5 ? 'good' : error > 0 ? 'high' : 'low';
          } else if (learned?.state === ns.LearningModel?.STATES?.COMPARABLE) {
            cellText = '…';
            subtext = 'sem par medido';
            heat = 0.15;
          }
        }
        this.grid.updateCell(Number(cell.dataset.row), Number(cell.dataset.column), {
          text: cellText,
          subtext,
          heat,
          tone,
          hasData: !!source || !!learned,
          state: learned?.state || '',
          selected: this.selectedCell?.row === Number(cell.dataset.row) && this.selectedCell?.column === Number(cell.dataset.column),
        });
      });

      const coverage = document.getElementById('learningCoverageSummary');
      if (coverage && model) {
        const petrolCount = model.counts.petrol + model.counts.comparable;
        const cngCount = model.counts.cng + model.counts.comparable;
        coverage.textContent = `${petrolCount} gasolina · ${cngCount} GNV atual · ${comparisons.size} pares medidos`;
      }
      const proposalSummary = blueProposalSummary(state.calibrationState);
      const summary = document.getElementById('learningSuggestionSummary');
      if (summary) summary.textContent = proposalSummary.label;

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
      const count = Math.max(0, finite(decision.frame_count) || 0);
      const desired = Math.max(0, finite(decision.desired_frames) || 0);
      const minimum = Math.max(0, finite(decision.minimum_frames) || 0);
      const progress = desired > 0 ? Math.min(100, count / desired * 100) : 0;
      const eligible = decision.learning_eligible === true;
      const fuel = fuelLabel(decision.fuel_confirmed || live.fuel);
      const reason = restoring
        ? (learningStatus.reason || 'Restaurando conhecimento persistido em segundo plano.')
        : (decision.reason || live.sample_reason || 'Aguardando decisão do núcleo.');
      const quality = finite(decision.quality);
      const row = finite(decision.cell_row ?? cell.row);
      const column = finite(decision.cell_column ?? cell.column);
      const rpm = finite(interpolation.rpm ?? live.rpm);
      const petrolMs = finite(interpolation.petrolMs ?? live.petrol_ms ?? live.petrolMs);
      const mapBar = finite(interpolation.mapBar ?? live.load_bar ?? live.map_bar);
      const pressure = finite(live.pressure_diff_bar ?? live.gas_pressure_abs_bar);
      const water = finite(live.water_c ?? live.waterC);
      const timeout = finite(decision.window_budget_ms);
      const age = finite(decision.window_age_ms);
      const historyRows = this.decisionHistory.length
        ? this.decisionHistory.map(item => `<div data-level="${escapeHtml(item.level)}"><time>${escapeHtml(item.time)}</time><b>${escapeHtml(item.label)}</b><span>${escapeHtml(item.reason)}</span><small>${escapeHtml(item.fuel)} · célula ${escapeHtml(item.cell)} · ${escapeHtml(item.code)}</small></div>`).join('')
        : '<p class="empty-copy">Nenhum aceite, descarte ou transição relevante observado desde que esta tela foi aberta.</p>';

      this.collectionPane.innerHTML = `
        <section class="learning-decision-card" data-eligible="${eligible ? 'true' : 'false'}">
          <div class="decision-top"><div><small>DECISÃO DO NÚCLEO</small><h3>${restoring ? 'Learning restaurando' : escapeHtml(stateLabel(decision))}</h3></div><span>${restoring ? 'EM SEGUNDO PLANO' : (eligible ? 'CONTA' : 'NÃO CONTA AINDA')}</span></div>
          <p>${escapeHtml(reason)}</p>
          <div class="collection-progress"><i style="width:${progress.toFixed(1)}%"></i></div>
          <div class="collection-facts"><span><b>${count}/${desired || '—'}</b> leituras</span><span>mínimo ${minimum || '—'}</span><span>${fuel}</span><span>${quality === null ? 'qualidade —' : `qualidade ${Math.round(quality * 100)}%`}</span></div>
          <small class="reason-code">${restoring ? 'LEARNING_RESTORE_PENDING' : escapeHtml(decision.reason_code || decision.state || 'OBSERVING_ENGINE')}</small>
        </section>
        <section class="learning-current-condition">
          <header><div><small>CONDIÇÃO AGORA</small><h3>O que o núcleo está observando</h3></div>${row !== null && column !== null && row >= 0 && column >= 0 ? `<span>Célula ${row + 1}×${column + 1}</span>` : ''}</header>
          <div class="condition-grid">
            <div><small>RPM</small><b>${rpm === null ? '—' : Math.round(rpm).toLocaleString('pt-BR')}</b></div>
            <div><small>Petrol Inj.</small><b>${fmt(petrolMs, 2)} ms</b></div>
            <div><small>MAP</small><b>${fmt(mapBar, 3)} bar</b></div>
            <div><small>Pressão GNV</small><b>${fmt(pressure, 3)} bar</b></div>
            <div><small>Água</small><b>${water === null ? '—' : `${fmt(water, 0)} °C`}</b></div>
            <div><small>Janela</small><b>${age === null ? '—' : `${fmt(age / 1000, 1)} s`}${timeout ? ` / ${fmt(timeout / 1000, 1)} s` : ''}</b></div>
          </div>
          <p class="learning-light-note">A posição ao vivo é somente contexto. Gasolina é a referência; GNV só é comparado quando existe par físico equivalente.</p>
        </section>
        <section class="learning-decision-history">
          <header><div><small>ÚLTIMAS DECISÕES OBSERVADAS</small><h3>O que acabou de acontecer com a coleta</h3></div><span>${this.decisionHistory.length}/6</span></header>
          <div class="decision-history-list">${historyRows}</div>
          <p>O registro persistente completo continua no SessionRecorder em Ferramentas.</p>
        </section>
        <section class="learning-policy-summary">
          <header><small>ESTABILIDADE DA EVIDÊNCIA</small><span>AUTOMÁTICA</span></header>
          <div class="policy-grid"><span>RPM <b>interno</b></span><span>MAP <b>interno</b></span><span>Petrol Inj. <b>interno</b></span><span>Continuidade <b>protegida</b></span></div>
          <p>O núcleo decide automaticamente se RPM, MAP e Petrol Inj. representam a mesma condição física. Não existe perfil do usuário para afrouxar ou apertar a ciência.</p>
        </section>
      `;
    }

    renderDetail(state, row, column) {
      if (!this.cellPane) return;
      const maps = state.learning || {};
      const model = this.buildEvidenceModel(maps);
      const learned = evidenceIndex(model).get(key(row, column));
      const comparison = indexByCell(maps.comparisons).get(key(row, column));
      const measuredError = comparisonError(comparison);
      const targetMs = comparisonTargetMs(comparison);
      const observedMs = comparisonObservedMs(comparison);
      const observedPair = comparison?.observed_pair || comparison || null;
      const referenceSupport = comparison?.reference_support || null;
      const petrolSamples = finite(learned?.petrol?.samples) ?? 0;
      const cngSamples = finite(learned?.cng?.samples) ?? 0;
      const petrolVisits = finite(learned?.petrol?.visits) ?? 0;
      const cngVisits = finite(learned?.cng?.visits) ?? 0;
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
      const blue = state.calibrationState || {};
      const latestComparison = blue.latestComparison && typeof blue.latestComparison === 'object' ? blue.latestComparison : null;
      const proposal = blue.proposal && typeof blue.proposal === 'object' ? blue.proposal : {};
      const multiplier = finite(proposal.correctionMultiplier);
      const comparisonText = measuredError !== null && targetMs !== null && observedMs !== null
        ? `${fmt(targetMs, 2)} → ${fmt(observedMs, 2)} ms · ${measuredError > 0 ? '+' : ''}${fmt(measuredError, 1)}%`
        : 'ainda não existe par equivalente válido';
      const pairCondition = observedPair
        ? `${finite(observedPair.rpm) === null ? 'RPM —' : `${Math.round(finite(observedPair.rpm)).toLocaleString('pt-BR')} RPM`} · MAP ${fmt(observedPair.map_bar ?? observedPair.mapBar, 3)} bar · qualidade ${Math.round((finite(observedPair.quality) || 0) * 100)}%`
        : '—';
      const supportType = String(referenceSupport?.support_type || 'UNKNOWN').toUpperCase();
      const supportLabel = supportType === 'DIRECT' ? 'direto' : supportType === 'NEAR' ? 'vizinho físico' : 'não informado';
      const supportText = referenceSupport
        ? `${supportLabel} · ${Math.round(finite(referenceSupport.selected_candidates) || 0)} referência(s) · dispersão ${fmt(referenceSupport.spread_ms, 3)} ms`
        : 'procedência não disponível';
      const blueText = proposal.available === true && multiplier !== null
        ? `BlueCausalEngine propõe multiplicador ${fmt(multiplier, 4)}; medir e corrigir continuam etapas separadas.`
        : `BlueCausalEngine: ${escapeHtml(proposal.state || 'sem ganho causal suficiente')}. Nenhum alvo K é inventado.`;
      const latestText = latestComparison
        ? `último erro Blue ${fmt(latestComparison.errorPercent, 2)}% · qualidade ${Math.round((finite(latestComparison.quality) || 0) * 100)}%`
        : 'nenhuma comparação Blue reconciliada ainda';

      this.cellPane.innerHTML = `
        <div class="detail-eyebrow">CÉLULA ${row + 1} × ${column + 1}</div>
        <h3>${rpmLabel === null ? 'RPM —' : `${Math.round(rpmLabel).toLocaleString('pt-BR')} RPM`} · ${fmt(petrolLabel, 1)} ms</h3>
        <p class="learning-reason"><b>Gasolina é a referência.</b> Esta tela mostra evidência física e o desvio medido; proposta de correção é saída separada do Blue.</p>
        <dl class="detail-list enhanced-detail-list">
          <div><dt>Gasolina — referência agregada</dt><dd>${learned?.petrol ? `${fmt(petrolMeanMs, 2)} ms · ${petrolRpm === null ? 'RPM —' : `${Math.round(petrolRpm).toLocaleString('pt-BR')} RPM`} · MAP ${fmt(petrolMap, 3)} bar` : 'sem evidência gasolina nesta célula'}</dd></div>
          <div><dt>Qualidade da referência</dt><dd>${learned?.petrol ? `${Math.round(petrolSamples)} amostras · ${petrolVisits} visita(s) · qualidade ${Math.round(confidence(learned.petrol) * 100)}%` : '—'}</dd></div>
          <div><dt>GNV atual — Petrol Inj.</dt><dd>${learned?.cng ? `${fmt(cngMeanMs, 2)} ms · ${cngRpm === null ? 'RPM —' : `${Math.round(cngRpm).toLocaleString('pt-BR')} RPM`} · MAP ${fmt(cngMap, 3)} bar` : 'sem evidência GNV atual nesta célula'}</dd></div>
          <div><dt>Qualidade do GNV</dt><dd>${learned?.cng ? `${Math.round(cngSamples)} amostras · ${cngVisits} visita(s) · qualidade ${Math.round(confidence(learned.cng) * 100)}% · época ${model?.epoch ?? '—'}` : '—'}</dd></div>
          <div><dt>Desvio medido</dt><dd>${comparisonText}<br>${pairCondition}</dd></div>
          <div><dt>Suporte da referência</dt><dd>${supportText}</dd></div>
          <div><dt>Histórico GNV</dt><dd>${historicalEpochs.length ? `épocas ${historicalEpochs.join(', ')} · somente consulta` : 'nenhum'}</dd></div>
          <div><dt>Correção Blue — separada da medição</dt><dd>${blueText}<br>${latestText}</dd></div>
        </dl>
        <button class="primary wide" type="button" data-edit-learning-cell>Editar esta célula manualmente</button>
        <small class="manual-edit-contract">Abrir o editor não escreve na ECU. Revisão, confirmação, ACK e readback continuam obrigatórios.</small>
      `;
      this.cellPane.querySelector('[data-edit-learning-cell]')?.addEventListener('click', () => {
        this.router.navigate('map', {
          origin: 'learning',
          cell: { row, column },
          physical: { rpm: rpmLabel, petrolMs: petrolLabel },
        });
      });
    }
  }

  ns.LearningScreen = LearningScreen;
})(typeof window !== 'undefined' ? window : globalThis);
