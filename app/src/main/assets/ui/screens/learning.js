(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Desconhecido (null/vazio) fica desconhecido: Number(null) seria 0 e viraria "0,0% · equivalente".
  function finite(value) { return value === null || value === undefined || value === '' || !Number.isFinite(Number(value)) ? null : Number(value); }
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
  function comparisonError(item) {
    return finite(item?.errorPercent ?? item?.error_pct ?? item?.error_percent ?? item?.relativeErrorPercent ?? item?.deltaPercent ?? item?.differencePercent ?? item?.error);
  }
  function stableComparisonError(stable, comparison) {
    const state = String(stable?.state || '').toUpperCase();
    const consolidated = finite(stable?.consolidatedErrorPercent);
    const recent = finite(stable?.recentErrorPercent);
    const raw = comparisonError(comparison);
    if (state === 'CONSOLIDATED' || state === 'REVALIDATING') return consolidated ?? recent ?? raw;
    if (state === 'LEARNING') return recent ?? raw;
    return consolidated ?? recent ?? raw;
  }
  function differenceText(error, deadband) {
    const value = finite(error);
    if (value === null) return 'sem comparação ainda';
    const sign = value > 0 ? '+' : '';
    if (Math.abs(value) <= deadband) return `${sign}${fmt(value, 1)}% · equivalente`;
    return `${sign}${fmt(value, 1)}% · precisa ${value > 0 ? 'mais' : 'menos'} GNV`;
  }
  function comparisonTargetMs(item) { return finite(item?.petrol_target_ms ?? item?.petrolTargetMs); }
  function comparisonObservedMs(item) { return finite(item?.petrol_on_cng_ms ?? item?.petrolOnCngMs); }
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
    if (state === 'SAMPLE_REJECTED') return 'Medição recusada';
    if (state === 'WINDOW_TIMEOUT') return 'Medição reiniciada';
    if (state === 'TELEMETRY_GAP') return 'ECU sem dados';
    if (state === 'CUTOFF') return 'Aprendizado pausado';
    if (state === 'ENGINE_OFF') return 'Motor desligado';
    if (state === 'FUEL_TRANSITION') return 'Trocando de combustível';
    // Sem código cru nem inglês na tela (dono, 2026-10-11): o resto, inclusive OBSERVING_ENGINE, é observar o motor.
    return 'Observando o motor';
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
      this.toleranceSignature = '';
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
      // Sem rolagem (dono, 2026-10-11): o histórico de decisões ganhou aba própria ("Decisões")
      // em vez de empilhar tudo em "Coleta" e obrigar a rolar o painel.
      this.detail.innerHTML = `
        <div class="learning-inspector-tabs" role="tablist" aria-label="Painel do aprendizado">
          <button type="button" data-learning-inspector="cell">Célula</button>
          <button type="button" data-learning-inspector="collection" class="active">Coleta</button>
          <button type="button" data-learning-inspector="history">Decisões</button>
          <button type="button" data-learning-inspector="tolerances">Tolerâncias</button>
        </div>
        <div id="learningCellPane" class="learning-inspector-pane" data-pane="cell">
          <div class="detail-empty"><b>Toque em uma célula</b><span>Veja a evidência e, se quiser, abra exatamente essa célula no editor oficial do Mapa K.</span></div>
        </div>
        <div id="learningCollectionPane" class="learning-inspector-pane active" data-pane="collection"></div>
        <div id="learningHistoryPane" class="learning-inspector-pane" data-pane="history"></div>
        <div id="learningTolerancePane" class="learning-inspector-pane" data-pane="tolerances"></div>
      `;
      this.cellPane = document.getElementById('learningCellPane');
      this.collectionPane = document.getElementById('learningCollectionPane');
      this.historyPane = document.getElementById('learningHistoryPane');
      this.tolerancePane = document.getElementById('learningTolerancePane');
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
      // A camada "Sugestão" saiu: a fila que a alimentava está congelada desde a base (ver app.js, withoutFrozenQueue).
      const layer = ['petrol', 'cng', 'comparison'].includes(state.learningLayer) ? state.learningLayer : 'comparison';
      this.root.querySelectorAll('[data-learning-layer]').forEach(button => button.classList.toggle('active', button.dataset.learningLayer === layer));

      const comparisons = indexByCell(maps.comparisons);
      const stability = indexByCell(state.calibrationState?.learningStability?.map || []);
      const mapSuggestions = persistentMapSuggestions(state);

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
          source = stable || comparisons.get(cellKey) || null;
          const error = stableComparisonError(stable, comparisons.get(cellKey));
          const stableState = String(stable?.state || '').toUpperCase();
          if (error !== null) {
            cellText = `${error > 0 ? '+' : ''}${fmt(error, 1)}%`;
            subtext = stableState === 'REVALIDATING'
              ? 'revalidando'
              : stableState === 'CONSOLIDATED'
                ? 'consolidado'
                : stableState === 'LEARNING'
                  ? 'aprendendo'
                  : 'comparando';
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

      this.renderCollection(state);
      this.renderTolerances(state);
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
        : (decision.reason || live.sample_reason || 'Aguardando a próxima medição.');
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
        ? this.decisionHistory.map(item => `<div data-level="${escapeHtml(item.level)}"><time>${escapeHtml(item.time)}</time><b>${escapeHtml(item.label)}</b><span>${escapeHtml(item.reason)}</span><small>${escapeHtml(item.fuel)} · célula ${escapeHtml(item.cell)}</small></div>`).join('')
        : '<p class="empty-copy">Nenhum aceite, descarte ou transição relevante observado desde que esta tela foi aberta.</p>';

      this.collectionPane.innerHTML = `
        <section class="learning-decision-card" data-eligible="${eligible ? 'true' : 'false'}">
          <div class="decision-top"><div><small>MEDIÇÃO AGORA</small><h3>${restoring ? 'Recuperando o aprendizado' : escapeHtml(stateLabel(decision))}</h3></div><span>${restoring ? 'EM SEGUNDO PLANO' : (eligible ? 'CONTA' : 'AINDA NÃO CONTA')}</span></div>
          <p>${escapeHtml(reason)}</p>
          <div class="collection-progress"><i style="width:${progress.toFixed(1)}%"></i></div>
          <div class="collection-facts"><span><b>${count}/${desired || '—'}</b> leituras</span><span>mínimo ${minimum || '—'}</span><span>${fuel}</span><span>${quality === null ? 'qualidade —' : `qualidade ${Math.round(quality * 100)}%`}</span></div>
        </section>
        <section class="learning-current-condition">
          <header><div><small>CONDIÇÃO AGORA</small><h3>O que o app está medindo</h3></div>${row !== null && column !== null && row >= 0 && column >= 0 ? `<span>Célula ${row + 1}×${column + 1}</span>` : ''}</header>
          <div class="condition-grid">
            <div><small>RPM</small><b>${rpm === null ? '—' : Math.round(rpm).toLocaleString('pt-BR')}</b></div>
            <div><small>Injeção</small><b>${petrolMs === null ? '—' : `${fmt(petrolMs, 2)} ms`}</b></div>
            <div><small>MAP</small><b>${mapBar === null ? '—' : `${fmt(mapBar, 3)} bar`}</b></div>
            <div><small>Pressão GNV</small><b>${pressure === null ? '—' : `${fmt(pressure, 3)} bar`}</b></div>
            <div><small>Água</small><b>${water === null ? '—' : `${fmt(water, 0)} °C`}</b></div>
            <div><small>Tempo da medição</small><b>${age === null ? '—' : `${fmt(age / 1000, 1)} s`}${timeout ? ` / ${fmt(timeout / 1000, 1)} s` : ''}</b></div>
          </div>
        </section>
      `;
      // Sem rolagem (dono, 2026-10-11): as seis últimas decisões ganharam a aba "Decisões", sem empilhar na Coleta.
      if (this.historyPane) {
        this.historyPane.innerHTML = `
          <section class="learning-decision-history">
            <header><div><small>ÚLTIMAS DECISÕES OBSERVADAS</small><h3>O que acabou de acontecer com a coleta</h3></div><span>${this.decisionHistory.length}/6</span></header>
            <div class="decision-history-list">${historyRows}</div>
            <p>O histórico completo fica em Ferramentas.</p>
          </section>
        `;
      }
    }

    renderTolerances(state) {
      if (!this.tolerancePane) return;
      const settings = state.learningTolerance || {};
      const model = settings.controlModel || {};
      const water = finite(model.minimumWaterC);
      const signature = JSON.stringify({ water, ok: settings.ok });
      if (signature === this.toleranceSignature) return;
      this.toleranceSignature = signature;
      if (settings.ok === false || water === null) {
        this.tolerancePane.innerHTML = '<div class="detail-empty"><b>Ajustes indisponíveis</b><span>Aparecem aqui quando o serviço estiver disponível.</span></div>';
        return;
      }
      // As regras de estabilidade são fixas no Kotlin (o "Aplicar" antigo dizia "atualizadas" sem mudar nada).
      // Só a água mínima muda de verdade (dono, 2026-10-11: só o que funciona, sem fingir).
      this.tolerancePane.innerHTML = `
        <div class="tolerance-heading"><div><small>COLETA</small><h3>Quando o aprendizado conta</h3></div></div>
        <div class="editor-rule"><b>Regras de estabilidade fixas.</b><span>Rotação, injeção e MAP precisam ficar estáveis para a medição contar; isso não muda aqui.</span></div>
        <div class="tolerance-controls">
          <label><span><b>Temperatura mínima da água</b><small>Abaixo disso o app espera o motor aquecer antes de aprender.</small></span><input id="learningMinimumWaterInput" type="number" min="20" max="100" step="1" value="${water}"></label>
        </div>
        <div class="tolerance-actions"><button type="button" class="primary" data-apply-water>Aplicar temperatura</button></div>
        <div class="editor-rule"><b>Nada aqui escreve na ECU.</b><span>Muda só quando o app começa a aprender.</span></div>
      `;
      this.tolerancePane.querySelector('[data-apply-water]')?.addEventListener('click', () => this.applyMinimumWater());
    }

    applyMinimumWater() {
      if (!this.api?.setLearningMinimumWaterC) return;
      const value = Number(document.getElementById('learningMinimumWaterInput')?.value);
      const result = this.api.setLearningMinimumWaterC(value);
      if (!result || result.ok === false) {
        this.store.patch({ alert: { level: 'warning', message: result?.error || 'Não foi possível aplicar a temperatura.' } });
        return;
      }
      const applied = finite(result.applied ?? result.minimumWaterC);
      this.toleranceSignature = '';
      const current = this.store.get().learningTolerance || {};
      this.store.patch({
        learningTolerance: { ...current, controlModel: { ...(current.controlModel || {}), minimumWaterC: applied } },
        alert: { level: 'ok', message: applied === null ? 'Temperatura aplicada.' : `Aprende a partir de ${applied} °C.` },
      });
    }

    renderDetail(state, row, column) {
      if (!this.cellPane) return;
      const maps = state.learning || {};
      const model = this.buildEvidenceModel(maps);
      const learned = evidenceIndex(model).get(key(row, column));
      const comparison = indexByCell(maps.comparisons).get(key(row, column));
      const stability = indexByCell(state.calibrationState?.learningStability?.map || []).get(key(row, column));
      const suggestion = persistentMapSuggestions(state).get(key(row, column));
      const rawError = comparisonError(comparison);
      const consolidatedError = finite(stability?.consolidatedErrorPercent);
      const recentError = finite(stability?.recentErrorPercent);
      const displayError = stableComparisonError(stability, comparison);
      const targetMs = comparisonTargetMs(comparison);
      const observedMs = comparisonObservedMs(comparison);
      const delta = mapSuggestionDelta(suggestion);
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
      const axes = maps.grid || {};
      const axisRpm = finite(axes.rpmBins?.[column]);
      const axisPetrol = finite(axes.petrolBins?.[row]);
      const rpmLabel = finite(learned?.rpm) ?? axisRpm;
      const petrolLabel = finite(learned?.petrolMs) ?? axisPetrol;
      const stabilityState = String(stability?.state || 'NO_EVIDENCE').toUpperCase();
      const live = state.telemetry?.live || {};
      let agora = String(live.fuel || '').toUpperCase();
      if (agora.includes('TRANSITION')) agora = 'Transição';
      else if (agora.includes('PETROL') || agora.includes('GASOLINA')) agora = 'Gasolina';
      else if (agora.includes('CNG') || agora.includes('GNV') || agora === 'GAS') agora = 'GNV';
      else if (agora === 'UNKNOWN' || agora === 'DESCONHECIDO' || !agora) agora = 'Sem dados da ECU';

      const mapVal = cngMap !== null ? cngMap : (petrolMap !== null ? petrolMap : null);
      const condicao = `${rpmLabel === null ? 'RPM —' : `${Math.round(rpmLabel).toLocaleString('pt-BR')} RPM`} · ${mapVal === null ? 'MAP —' : `MAP ${fmt(mapVal, 3)} bar`}`;

      const gasolinaEsperada = targetMs !== null ? `${fmt(targetMs, 2)} ms` : 'sem evidência';
      const noGnvAgora = observedMs !== null ? `${fmt(observedMs, 2)} ms` : 'sem evidência atual';

      const deadband = finite(maps.tolerancePolicy?.equivalenceDeadbandPercent) ?? 2.5;
      const differenceNow = differenceText(rawError, deadband);
      const differenceStable = consolidatedError === null
        ? 'ainda não consolidada'
        : differenceText(consolidatedError, deadband);
      const differenceTrend = recentError === null
        ? 'sem tendência recente'
        : differenceText(recentError, deadband) + (stabilityState === 'REVALIDATING' ? ' · revalidando' : '');

      const confValue = Math.max(finite(stability?.confidence) || 0, Math.max(confidence(learned?.petrol || {}), confidence(learned?.cng || {})));
      let confLevel = 'Baixa';
      if (confValue >= 0.8) confLevel = 'Alta';
      else if (confValue >= 0.4) confLevel = 'Média';
      const visits = Math.max(finite(stability?.consolidatedUniqueVisits) || finite(stability?.recentUniqueVisits) || 0, Math.max(petrolVisits, cngVisits));
      const confianca = `${confLevel} (${visits} visitas)`;

      const kChange = Array.isArray(suggestion?.mapChanges) ? suggestion.mapChanges[0] : null;
      const targetK = finite(kChange?.after) !== null ? kChange.after : 'aguardando convergência';
      const mapAddressRow = finite(kChange?.row);
      const mapAddressColumn = finite(kChange?.column);
      const mapAddressMs = mapAddressRow !== null && Number.isInteger(mapAddressRow)
        ? finite(axes.petrolBins?.[mapAddressRow])
        : null;
      const mapAddressRpm = mapAddressColumn !== null && Number.isInteger(mapAddressColumn)
        ? finite(axes.rpmBins?.[mapAddressColumn])
        : null;
      const mapAddress = mapAddressMs !== null && mapAddressRpm !== null
        ? `${fmt(mapAddressMs, 1)} ms · ${Math.round(mapAddressRpm).toLocaleString('pt-BR')} RPM · condição GNV observada`
        : 'aguardando endereço físico confiável';
      // Só o que a célula diz em português (dono, 2026-10-11: sem lista técnica; só usar e validar). Linhas da fila de
      // sugestões (estável, tendência, endereço, valor K) só aparecem quando há dado: hoje a fila não é alimentada.
      const row4 = (label, value) => `<div><dt>${label}</dt><dd>${escapeHtml(value)}</dd></div>`;
      const queueRows = [
        stability ? row4('Diferença estável', differenceStable) : '',
        stability ? row4('Tendência recente', differenceTrend) : '',
        suggestion ? row4('Endereço Mapa K', mapAddress) : '',
        suggestion ? row4('Novo valor K sugerido', targetK) : '',
      ].join('');
      this.cellPane.innerHTML = `
        <div class="detail-eyebrow">CÉLULA ${row + 1} × ${column + 1}</div>
        <dl class="detail-list enhanced-detail-list">
          <div><dt>Agora</dt><dd>${escapeHtml(agora)}</dd></div>
          <div><dt>Condição</dt><dd>${escapeHtml(condicao)}</dd></div>
          <div><dt>Gasolina esperada</dt><dd>${escapeHtml(gasolinaEsperada)}</dd></div>
          <div><dt>No GNV agora</dt><dd>${escapeHtml(noGnvAgora)}</dd></div>
          <div><dt>Diferença agora</dt><dd>${escapeHtml(differenceNow)}</dd></div>
          ${queueRows}
          <div><dt>Confiança</dt><dd>${escapeHtml(confianca)}</dd></div>
        </dl>
        <div class="learning-cell-actions">
          <button class="primary" type="button" data-edit-learning-cell>
            ${suggestion?.actionable ? 'Revisar no Mapa K' : 'Abrir esta célula no Mapa K'}
          </button>
        </div>
        <small class="manual-edit-contract">Abrir o Mapa K não grava nada.</small>
      `;
      this.cellPane.querySelector('[data-edit-learning-cell]')?.addEventListener('click', () => {
        this.router.navigate('map', {
          origin: 'learning',
          cell: { row, column },
          physical: {
            rpm: mapAddressRpm ?? rpmLabel,
            petrolMs: mapAddressMs ?? petrolLabel,
            basis: kChange ? 'CNG_OBSERVED_MAP_ADDRESS' : 'SELECTED_LEARNING_CELL',
          },
          suggestion: suggestion?.actionable ? suggestion : null,
        });
      });
    }
  }

  ns.LearningScreen = LearningScreen;
})(typeof window !== 'undefined' ? window : globalThis);
