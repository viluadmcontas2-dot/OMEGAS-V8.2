(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Desconhecido (null/vazio) fica desconhecido: Number(null) seria 0 e viraria "0,0% · equivalente".
  function finite(value) { return value === null || value === undefined || value === '' || !Number.isFinite(Number(value)) ? null : Number(value); }
  function fmt(value, digits) {
    const n = finite(value);
    return n === null ? '—' : n.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits });
  }
  function text(id, value) { const node = document.getElementById(id); if (node) node.textContent = value == null ? '—' : String(value); }

  class MapScreen {
    constructor(store, api, router) {
      this.store = store;
      this.api = api;
      this.router = router || null;
      this.root = document.querySelector('[data-screen="map"]');
      this.host = document.getElementById('mapGrid');
      this.editor = new root.OmegasMapEditor.MapEditor();
      this.cells = new Map();
      this.rowHeaders = [];
      this.columnHeaders = [];
      this.reading = false;
      this.readRequested = false;
      this.dragStart = null;
      this.review = null;
      this.lastOperationState = '';
      this.writeStartedAt = null;
      this.pendingContext = null;
      this.liveContext = null;
      this.curveImport = null;
      this.ensureContextChrome();
      this.bind();
    }

    key(row, column) { return `${row}:${column}`; }

    ensureContextChrome() {
      const intro = this.root?.querySelector('.page-intro.action-intro');
      const actions = intro?.querySelector('.inline-actions');
      if (intro && !document.getElementById('mapLiveLabel')) {
        const live = document.createElement('div');
        live.className = 'map-live-condition';
        live.innerHTML = '<small>AGORA</small><b id="mapLiveLabel">Aguardando condição válida</b><span id="mapLiveCell">célula —</span>';
        intro.insertBefore(live, actions || null);
      }
      if (actions && !document.getElementById('mapBackToLearning')) {
        const back = document.createElement('button');
        back.id = 'mapBackToLearning';
        back.type = 'button';
        back.className = 'quiet-button';
        back.textContent = 'Voltar ao aprendizado';
        actions.prepend(back);
      }
    }

    bind() {
      document.getElementById('mapBackToLearning')?.addEventListener('click', () => this.router?.navigate('learning'));
      document.getElementById('mapSelectAll')?.addEventListener('click', () => {
        try { this.editor.selectAll(); this.review = null; this.renderEditor(); this.refreshSelectionPreview(); }
        catch (error) { this.alert(error.message); }
      });
      document.getElementById('mapClearSelection')?.addEventListener('click', () => {
        this.editor.clearSelection(); this.review = null; this.renderEditor(); this.renderGrid();
      });
      document.getElementById('mapAdjustmentMode')?.addEventListener('change', () => this.applyAdjustment());
      document.getElementById('mapAdjustmentValue')?.addEventListener('input', () => this.applyAdjustment());
      document.querySelectorAll('[data-map-nudge]').forEach(button => button.addEventListener('click', () => {
        const input = document.getElementById('mapAdjustmentValue');
        if (!input) return;
        input.value = String((finite(input.value) || 0) + Number(button.dataset.mapNudge || 0));
        this.applyAdjustment();
      }));
      document.getElementById('mapFromCurve')?.addEventListener('click', () => this.startCurveToMap());
      document.getElementById('mapFinishCurve')?.addEventListener('click', () => this.runCurveStage());
      document.getElementById('mapReviewButton')?.addEventListener('click', () => this.writePrepared());
      document.getElementById('mapDismissResult')?.addEventListener('click', () => this.dismissResult());
    }

    onEnter(context) {
      this.pendingContext = context || null;
      this.root?.classList.toggle('from-learning', context?.origin === 'learning');
      if (!this.editor.hasMap() && !this.reading) {
        this.startRead(true);
        return;
      }
      if (this.editor.hasMap()) this.applyContext(this.pendingContext);
    }

    /** Atualização automática ao reconectar a ECU (sem botão Reler). Nunca apaga edição, revisão ou gravação em curso. */
    autoSync(justConnected) {
      if (this.reading || this.curveImport || this.review || this.root?.classList.contains('is-writing')) return;
      if (!this.editor.hasMap() || justConnected) this.startRead(true);
    }

    settleReadFailure(message) {
      this.reading = false;
      if (this.curveImport) { this.curveImport = null; const b = document.getElementById('mapFromCurve'); if (b) b.disabled = false; }
      this.editor.reset();
      this.cells.clear();
      this.rowHeaders = [];
      this.columnHeaders = [];
      if (this.host) {
        this.host.innerHTML = '<div class="map-empty-state"><b>Mapa indisponível</b><span>Leitura da ECU não confirmada. Verifique a conexão e tente novamente.</span></div>';
      }
      text('mapSourceStatus', 'Mapa não confirmado');
      this.store.patch({ map: { ...this.store.get().map, state: 'failed', data: null, selection: 0, review: null } });
      if (message) this.alert(message);
    }

    startRead(automatic) {
      if (this.reading) return;
      const result = this.api.startMapRead();
      if (!result?.ok || !result?.started) {
        this.settleReadFailure(result?.error || 'Não foi possível iniciar a leitura do Mapa K.');
        return;
      }
      this.reading = true;
      this.readRequested = true;
      this.review = null;
      this.editor.reset();
      this.cells.clear();
      this.rowHeaders = [];
      this.columnHeaders = [];
      if (this.host) this.host.innerHTML = '<div class="map-empty-state"><div class="spinner"></div><b>Lendo Mapa K da ECU</b><span>13 linhas físicas · somente leitura</span></div>';
      text('mapSourceStatus', automatic ? 'Leitura automática em andamento' : 'Relendo diretamente da ECU');
      this.store.patch({ map: { ...this.store.get().map, state: 'reading', data: null, selection: 0, review: null } });
    }

    /**
     * Curva K → Mapa K (rotina própria; o ExportToK do original não é usado). Relê o Mapa K e a Curva K da ECU AGORA (nada
     * fixo: o mapa pode ter qualquer valor), refatora o produto Mapa × Curva (core/curve-to-map.js) e deixa a PROPOSTA na
     * grade. Gravar = mapa (fluxo manual, ACK + leitura de volta) e, em seguida, a curva neutra (core/balance-runner.js).
     */
    startCurveToMap() {
      const gate = root.OmegasWriteGate?.check((root.OmegasUi || {}).AutoCalApi?.equivalence?.()?.autopilot);
      if (gate && !gate.ok) { this.alert(gate.message); return; }
      if (!this.editor.hasMap() || this.reading || this.curveImport || this.root?.classList.contains('is-writing')) return;
      this.curveImport = { phase: 'map', startedAt: Date.now() };
      this.curvePlan = null;
      this.renderCurveStage();
      const button = document.getElementById('mapFromCurve');
      if (button) button.disabled = true;
      this.startRead(false);
      if (!this.reading) this.finishCurveImport('Não foi possível reler o Mapa K. Nada foi proposto.');
    }

    finishCurveImport(message) {
      this.curveImport = null;
      const button = document.getElementById('mapFromCurve');
      if (button) button.disabled = false;
      if (message) this.alert(message);
    }

    /** Chamado quando o Mapa K acabou de ser relido: agora lê a curva. */
    continueCurveToMap() {
      const pending = this.curveImport;
      if (!pending || pending.phase !== 'map') return;
      const started = this.api.startCurveRead();
      if (!started?.ok || !started?.started) {
        this.finishCurveImport(started?.error || 'Não foi possível ler a Curva K.');
        return;
      }
      this.curveImport = { phase: 'curve', startedAt: Date.now() };
      text('mapSourceStatus', 'Lendo a Curva K para propor o Mapa K');
    }

    pollCurveToMap() {
      const pending = this.curveImport;
      if (!pending || pending.phase !== 'curve') return;
      const operation = this.api.curveOperation();
      if (!operation || operation.busy) {
        if (Date.now() - pending.startedAt > 20000) this.finishCurveImport('A leitura da Curva K demorou demais. Nada foi proposto.');
        return;
      }
      if (!operation.ok || !Array.isArray(operation.points) || operation.points.length !== 30 ||
          (operation.state !== 'COMPLETED' && !operation.demo)) {
        this.finishCurveImport(operation.error || 'A Curva K não retornou os 30 pontos válidos. Nada foi proposto.');
        return;
      }
      const message = this.applyCurveToMap(operation.points);
      text('mapSourceStatus', 'Mapa lido da ECU');
      this.finishCurveImport(message);
    }

    /** Devolve a mensagem para o dono; a proposta fica nas células (alvos) e "Gravar" fica disponível. */
    applyCurveToMap(points) {
      const planner = root.OmegasCurveToMap;
      if (!planner) return 'Rotina Curva K → Mapa K indisponível.';
      const snapshot = this.editor.snapshot();
      this.curvePlan = null;
      // Refino do piloto (Kotlin): vale em QUALQUER fase, também com a ECU ainda no automático (dono, 2026-10-13: não esperar o
      // 3º AutoMatch). O motor calcula a proposta o tempo todo com os nossos pontos; só pontos cujo valor na ECU ainda é o da
      // proposta entram (refinementTargets descarta os que a ECU já mexeu).
      const pilot = (root.OmegasUi || {}).AutoCalApi?.equivalence?.()?.autopilot;
      const proposal = pilot?.proposal?.known && Number(pilot?.proposal?.count) > 0 ? pilot.proposal.points : null;
      const refined = proposal && planner.refinementTargets ? planner.refinementTargets(points, proposal) : null;
      const result = planner.balance({
        tbp: points.map(point => point.petrolMs),
        k: points.map(point => point.factor),
        rowMs: snapshot.axes.petrolBins,
        cells: snapshot.rows,
        refine: refined && refined.applied ? refined.refine : null,
        // Exportação PARCIAL (dono, 2026-10-16: "não pode ser 100%"): metade do ln(K) vai para o mapa e o mapa nunca sobe acima de 200.
        share: planner.EXPORT_SHARE, softMax: planner.EXPORT_SOFT_MAX,
      });
      const summary = result.summary;
      if (!summary.rowsUsed) return 'Nenhuma linha do Mapa K cai dentro da faixa da Curva K: nada a passar para o Mapa K.';
      if (!summary.cellsChanged) return summary.nodesChanged ? 'Só a curva mudaria (o mapa já absorve tudo): use a aba Ajuste global se quiser mexer nela.' : 'Mapa K e Curva K já estão equilibrados (curva neutra nas linhas): nada a fazer.';
      const changes = [];
      result.rows.forEach(row => row.cells.forEach(cell => {
        if (cell.target !== cell.current) changes.push({ row: row.row, column: cell.column, target: cell.target });
      }));
      const nodes = result.nodes.filter(node => node.changed).map(({ index, currentRaw, targetRaw }) => ({ index, currentRaw, targetRaw }));
      this.editor.clearSelection();
      try {
        if (changes.length) this.editor.setTargetOverrides(changes);
      } catch (error) {
        this.editor.clearSelection();
        this.review = null;
        this.renderGrid();
        this.renderEditor();
        return error.message;
      }
      this.curvePlan = { changes, nodes, armed: false };
      this.review = null;
      this.renderGrid();
      this.renderEditor();
      const share = Math.round(summary.curveShare * 100);
      const extra = [
        share >= 1 ? `curva com até ${share}%` : '',
        summary.tightCells ? `${summary.tightCells} células sem folga` : '',
        summary.saturatedCells ? `${summary.saturatedCells} células não cabem em 100–255` : '',
      ].filter(Boolean).join(' · ');
      // A curva da ECU sai serrilhada do AutoMatch; ela é suavizada ANTES de virar mapa (senão o serrilhado vai junto).
      const smoothed = summary.smoothed && summary.roughnessBefore - summary.roughnessAfter >= 1
        ? ` · curva suavizada (serrilhado ${summary.roughnessBefore.toFixed(0)}% → ${summary.roughnessAfter.toFixed(0)}%)` : '';
      const refinedText = summary.refinedNodes ? ` · inclui o refino (${summary.refinedNodes} ${summary.refinedNodes === 1 ? 'ponto' : 'pontos'})` : '';
      return `Proposta: ${changes.length} células + ${nodes.length} pontos da curva${refinedText}${smoothed} · erro ${summary.maxRowErrorPct.toFixed(1)}%${extra ? ` · ${extra}` : ''}. Grave: mapa, depois curva.`;
    }

    /**
     * Segunda etapa: a curva refatorada, só depois do mapa confirmado pela ECU. Também é o que "Concluir curva" repete:
     * se a etapa falha (tempo limite, ECU recusou, curva mudou), o plano fica armado com `failed` — um plano novo partiria
     * do produto já dobrado (mapa novo × curva velha) e fixaria a dobra. A releitura trata curva já no alvo como concluída.
     */
    async runCurveStage() {
      const plan = this.curvePlan;
      const runner = root.OmegasBalanceRunner;
      if (!plan?.armed || plan.running || !runner) return;
      if (!plan.nodes.length) { this.curvePlan = null; this.renderCurveStage(); return; }
      plan.running = true;
      this.renderCurveStage();
      text('mapSourceStatus', 'Gravando a Curva K (etapa 2 de 2)');
      let outcome;
      try { outcome = await runner.writeCurveStage(this.api, plan.nodes, { reason: 'Curva K → Mapa K: curva refatorada' }); }
      catch (error) { outcome = { ok: false, error: error?.message || String(error) }; }
      plan.running = false;
      if (outcome.ok) {
        this.curvePlan = null;
        text('mapSourceStatus', 'Mapa lido da ECU');
        this.alert('Mapa e Curva K gravados e conferidos pela ECU. A curva ficou neutra nas linhas do mapa; o AutoCal segue aprendendo o resto.');
      } else {
        plan.failed = outcome.error;
        text('mapSourceStatus', 'Curva K pendente (etapa 2 de 2)');
        this.alert(outcome.stale
          ? 'O mapa foi gravado, mas a Curva K mudou desde o plano e a curva planejada NÃO foi gravada. Até concluir, a correção pode estar em dobro nas linhas do mapa. Confira a Curva K em Ajuste global; “Concluir curva” tenta de novo.'
          : `O mapa foi gravado, mas a curva NÃO: ${outcome.error} Até concluir, a correção está em dobro nas linhas do mapa. Toque em “Concluir curva” para gravar só a curva planejada.`);
      }
      this.renderCurveStage();
    }

    /** "Concluir curva" só existe com a etapa 2 pendente (mapa já gravado, curva planejada ainda não confirmada); `hidden` não ocupa lugar. */
    renderCurveStage() {
      const button = document.getElementById('mapFinishCurve');
      if (button) button.hidden = !(this.curvePlan?.armed && this.curvePlan.failed && !this.curvePlan.running);
    }

    poll() {
      this.pollCurveToMap();
      if (this.reading) {
        const result = this.api.mapReadResult();
        if (!result?.busy && result?.state !== 'READING') {
          this.reading = false;
          if (!result?.ok || result?.state === 'FAILED') {
            this.settleReadFailure(result?.error || 'Falha ao ler o Mapa K.');
          } else {
            try {
              this.editor.load(result);
              this.buildGrid();
              this.renderGrid();
              this.renderEditor();
              text('mapSourceStatus', 'Mapa lido da ECU');
              this.store.patch({ map: { ...this.store.get().map, state: 'ready', data: result, selection: 0, review: null } });
              this.applyContext(this.pendingContext || this.store.get().routeContext);
              this.continueCurveToMap();
            } catch (error) {
              this.settleReadFailure(error.message);
            }
          }
        }
      }
      this.pollWrite();
    }

    buildGrid() {
      if (!this.host || !this.editor.hasMap()) return;
      this.host.innerHTML = '';
      this.cells.clear();
      this.rowHeaders = [];
      this.columnHeaders = [];
      const snapshot = this.editor.snapshot();
      const table = document.createElement('div');
      table.className = 'map-k-grid map-k-grid-with-axes';
      table.style.setProperty('--map-columns', '12');

      const corner = document.createElement('div');
      corner.className = 'map-axis-corner';
      corner.innerHTML = '<small>INJEÇÃO</small><b>ms ↓ · RPM →</b>';
      table.appendChild(corner);

      for (let column = 0; column < 12; column += 1) {
        const header = document.createElement('button');
        header.type = 'button';
        header.className = 'map-axis-header map-rpm-header';
        header.dataset.selectColumn = String(column);
        header.innerHTML = `<b>${Math.round(snapshot.axes.rpmBins[column] || 0).toLocaleString('pt-BR')}</b>`;
        header.title = 'Selecionar ou desmarcar toda esta faixa de RPM';
        this.columnHeaders.push(header);
        table.appendChild(header);
      }

      for (let row = 0; row < 12; row += 1) {
        const rowHeader = document.createElement('button');
        rowHeader.type = 'button';
        rowHeader.className = 'map-axis-header map-ms-header';
        rowHeader.dataset.selectRow = String(row);
        rowHeader.innerHTML = `<b>${fmt(snapshot.axes.petrolBins[row], 1)} ms</b>`;
        rowHeader.title = 'Selecionar ou desmarcar toda esta faixa de Petrol Inj.';
        this.rowHeaders.push(rowHeader);
        table.appendChild(rowHeader);
        for (let column = 0; column < 12; column += 1) {
          const cell = document.createElement('button');
          cell.type = 'button';
          cell.className = 'map-k-cell';
          cell.dataset.row = String(row);
          cell.dataset.column = String(column);
          cell.dataset.key = this.key(row, column);
          cell.setAttribute('aria-label', `${fmt(snapshot.axes.petrolBins[row], 1)} ms, ${Math.round(snapshot.axes.rpmBins[column] || 0)} RPM, K ${snapshot.rows[row][column]}`);
          cell.innerHTML = `<b>${snapshot.rows[row][column]}</b><span></span>`;
          this.cells.set(this.key(row, column), cell);
          table.appendChild(cell);
        }
      }
      this.host.appendChild(table);
      const technical = document.createElement('div');
      technical.className = 'technical-row-note';
      // Uma vez só, em palavras do dono (2026-10-11); a 13ª linha (0C) fica fora da grade e da escrita manual.
      technical.innerHTML = '<b>Última linha: só leitura</b><span>13ª linha da ECU, fora desta grade</span>';
      this.host.appendChild(technical);

      table.addEventListener('click', event => {
        const columnHeader = event.target.closest('[data-select-column]');
        const rowHeader = event.target.closest('[data-select-row]');
        if (columnHeader) {
          try {
            this.editor.toggleColumn(Number(columnHeader.dataset.selectColumn));
            this.review = null;
            this.renderEditor();
            this.refreshSelectionPreview();
          } catch (error) { this.alert(error.message); }
          return;
        }
        if (rowHeader) {
          try {
            this.editor.toggleRow(Number(rowHeader.dataset.selectRow));
            this.review = null;
            this.renderEditor();
            this.refreshSelectionPreview();
          } catch (error) { this.alert(error.message); }
        }
      });
      table.addEventListener('pointerdown', event => {
        const cell = event.target.closest('.map-k-cell');
        if (!cell) return;
        this.dragStart = { row: Number(cell.dataset.row), column: Number(cell.dataset.column) };
        try { cell.setPointerCapture?.(event.pointerId); } catch (_) {}
      });
      table.addEventListener('pointerup', event => {
        const cell = event.target.closest('.map-k-cell');
        if (!cell || !this.dragStart) return;
        const end = { row: Number(cell.dataset.row), column: Number(cell.dataset.column) };
        try {
          if (end.row === this.dragStart.row && end.column === this.dragStart.column) this.editor.toggle(end.row, end.column);
          else this.editor.selectRange(this.dragStart.row, this.dragStart.column, end.row, end.column, true);
          this.review = null;
          this.renderEditor(end.row, end.column);
          this.refreshSelectionPreview();
        } catch (error) { this.alert(error.message); }
        this.dragStart = null;
      });
    }

    refreshSelectionPreview() {
      if (!this.editor.selectionCount()) {
        this.renderGrid();
        return;
      }
      const value = finite(document.getElementById('mapAdjustmentValue')?.value);
      if (value === null) {
        this.renderGrid();
        return;
      }
      this.applyAdjustment();
    }

    applyAdjustment() {
      if (!this.editor.hasMap()) return;
      const mode = document.getElementById('mapAdjustmentMode')?.value || 'percent';
      const value = finite(document.getElementById('mapAdjustmentValue')?.value);
      if (value === null) return;
      try {
        this.editor.setAdjustment(mode, value);
        // Somar/percentual 0 não muda nada (dono, 2026-10-11): sem prévia, sem aviso, "Gravar" fica desabilitado.
        const noChange = value === 0 && mode !== 'target';
        if (!noChange && this.editor.selectionCount() > 0) {
          const preview = this.api.previewMapAdjustment(this.editor.selectedCells(), mode, value);
          if (!preview?.ok || !Array.isArray(preview.items)) throw new Error(preview?.error || 'Prévia do Mapa K indisponível.');
          this.editor.applyNativePreview(preview.items);
        }
        this.review = null;
        this.renderGrid();
        this.renderEditor();
      } catch (error) { this.alert(error.message); }
    }

    renderGrid() {
      if (!this.editor.hasMap()) return;
      const snapshot = this.editor.snapshot();
      let previewItems = new Map();
      if (this.editor.selectionCount() > 0) {
        try { previewItems = new Map(this.editor.buildReview().items.map(item => [this.key(item.row, item.column), item])); }
        catch (_) {}
      }
      this.cells.forEach((cell, cellKey) => {
        const row = Number(cell.dataset.row);
        const column = Number(cell.dataset.column);
        const selected = this.editor.isSelected(row, column);
        const preview = previewItems.get(cellKey);
        cell.classList.toggle('selected', selected);
        cell.classList.toggle('preview', !!preview);
        const valueNode = cell.querySelector('b');
        const deltaNode = cell.querySelector('span');
        if (valueNode) valueNode.textContent = preview ? String(preview.target) : String(snapshot.rows[row][column]);
        if (deltaNode) deltaNode.textContent = preview ? `${preview.current}→${preview.target}` : '';
      });
      this.columnHeaders.forEach((header, column) => {
        const all = Array.from({ length: 12 }, (_, row) => this.editor.isSelected(row, column)).every(Boolean);
        header.classList.toggle('selected', all);
      });
      this.rowHeaders.forEach((header, row) => {
        const all = Array.from({ length: 12 }, (_, column) => this.editor.isSelected(row, column)).every(Boolean);
        header.classList.toggle('selected', all);
      });
    }

    renderEditor(activeRow, activeColumn) {
      const count = this.editor.selectionCount();
      // "Gravar" só com algo a gravar (dono, 2026-10-11): selecionado sem mudança diz "Escolha quanto mudar".
      const changes = count ? this.editor.changeCount() : 0;
      text('mapSelectionCount', `${count} selecionada${count === 1 ? '' : 's'}`);
      const button = document.getElementById('mapReviewButton');
      if (button) {
        button.disabled = count === 0 || changes === 0;
        button.textContent = !count ? 'Gravar na ECU'
          : !changes ? 'Gravar na ECU'
            : `Gravar ${changes} ${changes === 1 ? 'alteração' : 'alterações'} na ECU`;
      }
      if (Number.isInteger(activeRow) && Number.isInteger(activeColumn) && this.editor.hasMap()) {
        const snapshot = this.editor.snapshot();
        text('mapActiveCell', `${fmt(snapshot.axes.petrolBins[activeRow], 1)} ms · ${fmt(snapshot.axes.rpmBins[activeColumn], 0)} RPM`);
      }
      this.renderCurveStage();
      this.store.patch({ map: { ...this.store.get().map, selection: count, review: this.review } });
    }

    renderLiveContext(context) {
      this.liveContext = context || null;
      text('mapLiveLabel', context?.label || 'Aguardando condição válida');
      const cell = context && Number.isInteger(context.row) && Number.isInteger(context.column)
        ? `célula ${context.row + 1}×${context.column + 1}`
        : 'célula —';
      text('mapLiveCell', cell);
    }

    writePrepared() {
      try {
        if (this.editor.targetOverrides?.size !== this.editor.selectionCount()) this.applyAdjustment();
        this.review = this.editor.buildReview();
      } catch (error) {
        this.alert(error.message);
        return;
      }
      if (!this.review?.items?.length) return;
      // Proposta do Curva K → Mapa K gravada sem edição: é ela que arma a etapa 2 (curva) e passa pela porta de gravação NO
      // TOQUE (dono, 2026-10-16: a proposta pode ter sido montada antes de a ECU voltar ao automático, ex.: Reaprender GNV).
      // Edição manual de célula não passa pela porta. Uma etapa 2 já pendente (falhou antes) não morre por uma edição manual.
      const plan = this.curvePlan;
      let proposal = false;
      if (plan && !plan.armed) {
        const sent = new Map(this.review.items.map(item => [`${item.row}:${item.column}`, item.target]));
        proposal = sent.size === plan.changes.length && plan.changes.every(c => sent.get(`${c.row}:${c.column}`) === c.target);
        if (!proposal) this.curvePlan = null;
      }
      if (proposal) {
        const gate = root.OmegasWriteGate?.check((root.OmegasUi || {}).AutoCalApi?.equivalence?.()?.autopilot);
        if (gate && !gate.ok) { this.alert(gate.message); this.review = null; this.renderEditor(); return; }
      }
      const result = this.api.writeMap(this.review.items, 'Ajuste manual confirmado na UI clean-slate');
      if (!result?.ok || !result?.started) {
        this.alert(result?.error || 'A escrita não iniciou.');
        this.review = null;
        this.renderEditor();
        return;
      }
      if (proposal) plan.armed = true;
      this.root?.classList.add('is-writing');
      this.lastOperationState = '';
      // A ponte guarda uma "última operação" só para Curva K, Mapa K, backup e reset: esta tela só segue a gravação que iniciou.
      this.writeStartedAt = result.startedAt ?? null;
      text('mapOperationTitle', 'Escrita manual em andamento');
      text('mapOperationMessage', `0 de ${this.review.count} células confirmadas`);
      this.store.patch({ map: { ...this.store.get().map, state: 'writing', operation: result, review: this.review } });
    }

    dismissResult() {
      this.root?.classList.remove('is-writing', 'has-result');
      this.review = null;
      this.renderEditor();
    }

    pollWrite() {
      const operation = this.api.mapWriteOperation();
      if (!operation || operation.state === 'IDLE' || operation.state === 'UNAVAILABLE') return;
      if (this.writeStartedAt == null || operation.startedAt !== this.writeStartedAt) return; // operação da Curva K ou antiga
      if (operation.state === this.lastOperationState && !operation.busy) return;
      this.lastOperationState = operation.state;
      const progress = Math.max(0, Math.min(100, finite(operation.progress) || 0));
      const bar = document.getElementById('mapOperationProgress');
      if (bar) bar.style.width = `${progress}%`;
      text('mapOperationMessage', `${operation.confirmedCells || 0} de ${operation.totalCells || this.review?.count || 0} células confirmadas`);

      if (operation.busy) {
        text('mapOperationTitle', operation.writerMessage || 'Gravando e conferindo…');
        return;
      }
      if (operation.state === 'BATCH_CONFIRMED' && operation.readbackValid === true) {
        this.root?.classList.remove('is-writing');
        this.root?.classList.add('has-result');
        const result = document.getElementById('mapOperationResult');
        if (result) {
          result.dataset.level = 'ok';
          result.querySelector('b').textContent = `${operation.confirmedCells || operation.totalCells} alterações confirmadas pela ECU`;
          // "Gravado" só aqui: BATCH_CONFIRMED + readbackValid (ACK e leitura de volta da ECU); texto para leigo (dono, 2026-10-11).
          result.querySelector('span').textContent = 'Gravado e conferido na ECU. O mapa será relido.';
        }
        this.writeStartedAt = null;
        this.editor.reset();
        const reread = () => this.startRead(true);
        // Só a gravação da própria proposta puxa a etapa 2; uma etapa 2 pendente (falhou antes) espera o toque em "Concluir curva".
        if (this.curvePlan?.armed && !this.curvePlan.failed) this.runCurveStage().then(reread, reread); else reread();
      } else if (operation.state === 'BATCH_PARTIAL_FAILED' || operation.ok === false) {
        this.root?.classList.remove('is-writing');
        this.root?.classList.add('has-result');
        const failure = operation.failure || {};
        const result = document.getElementById('mapOperationResult');
        if (result) {
          result.dataset.level = 'critical';
          result.querySelector('b').textContent = 'A ECU não confirmou toda a operação';
          result.querySelector('span').textContent = `${operation.confirmedCells || 0} células foram confirmadas antes da falha. ${failure.error || operation.error || 'Releitura obrigatória.'}`;
        }
        this.writeStartedAt = null;
        if (!this.curvePlan?.failed) this.curvePlan = null; // a etapa 2 pendente de antes não morre por uma gravação manual falhar
        this.editor.reset();
      }
    }

    applyContext(context) {
      if (!context || !this.editor.hasMap()) return;
      const suggestion = context.suggestion;
      const changes = Array.isArray(suggestion?.mapChanges) ? suggestion.mapChanges : [];
      try {
        // Ajuste local do Refino (dono, 2026-10-11: a curva aprende tudo e sabe onde ajustar o quê): só o percentual por
        // célula. O alvo sai da prévia Kotlin (MapKManualPlanner), o mesmo caminho do ajuste percentual manual, sobre o
        // valor lido da ECU — a conta do Mapa K não é feita no JS.
        const percentOnly = changes.length > 0 && changes.every(c => c && c.after == null && c.target == null && finite(c.deltaPercent) !== null);
        if (percentOnly) {
          const groups = new Map();
          changes.forEach(c => {
            const delta = finite(c.deltaPercent);
            if (!groups.has(delta)) groups.set(delta, []);
            groups.get(delta).push({ row: Number(c.row), column: Number(c.column) });
          });
          const targets = [];
          for (const [delta, cells] of groups) {
            this.editor.clearSelection();
            cells.forEach(cell => this.editor.toggle(cell.row, cell.column));
            const preview = this.api.previewMapAdjustment(this.editor.selectedCells(), 'percent', delta);
            if (!preview?.ok || !Array.isArray(preview.items)) throw new Error(preview?.error || 'Prévia do Mapa K indisponível.');
            preview.items.forEach(item => targets.push({ row: Number(item.row), column: Number(item.column), after: Number(item.target) }));
          }
          this.editor.setTargetOverrides(targets);
          const first = targets[0] || changes[0];
          this.renderGrid();
          this.renderEditor(Number(first.row), Number(first.column));
          return;
        }
        if (changes.length) {
          this.editor.setTargetOverrides(changes);
          const first = changes[0];
          this.renderGrid();
          this.renderEditor(Number(first.row), Number(first.column));
          return;
        }
        if (suggestion) {
          const row = Number(suggestion.row);
          const column = Number(suggestion.column);
          if (Number.isInteger(row) && Number.isInteger(column)) {
            this.editor.selectOnly(row, column);
            document.getElementById('mapAdjustmentMode').value = 'percent';
            document.getElementById('mapAdjustmentValue').value = String(Number(suggestion.deltaPercent || 0));
            this.applyAdjustment();
            this.renderEditor(row, column);
            return;
          }
        }
        const row = Number(context.cell?.row ?? context.row);
        const column = Number(context.cell?.column ?? context.column);
        if (Number.isInteger(row) && Number.isInteger(column)) {
          this.editor.selectOnly(row, column);
          this.renderGrid();
          this.renderEditor(row, column);
          document.getElementById('mapAdjustmentValue')?.focus?.();
        }
      } catch (error) { this.alert(error.message); }
    }

    alert(message) {
      this.store.patch({ alert: { level: 'warning', message: String(message || 'Operação indisponível') } });
    }
  }

  ns.MapScreen = MapScreen;
})(typeof window !== 'undefined' ? window : globalThis);