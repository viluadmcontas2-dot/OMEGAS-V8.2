(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Palavras únicas de toda escrita na ECU (core/display-rules.js).
  function wording() { return root.OmegasUi.DisplayRules.OPERATION_WORDING; }
  function failureText(operation, fallback) { return root.OmegasUi.DisplayRules.failureText(operation, fallback); }
  const { finite, fmt } = root.OmegasUi.DisplayRules;
  const D = () => root.OmegasUi.DisplayRules;
  const signed = value => (value > 0 ? '+' : value < 0 ? '−' : '') + Math.abs(value);
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
      // Desfazer do Mapa K: id da foto (= id da escrita) e etapa ('' | 'preparing' | 'writing').
      this.undoId = '';
      this.restorePhase = '';
      this.releasing = false;
      this.releaseTicks = 0;
      this.pendingContext = null;
      this.liveContext = null;
      this.bind();
    }

    key(row, column) { return `${row}:${column}`; }

    bind() {
      document.getElementById('mapReadButton')?.addEventListener('click', () => this.startRead());
      document.getElementById('mapSelectAll')?.addEventListener('click', () => {
        try { this.editor.selectAll(); this.review = null; this.renderEditor(); this.refreshSelectionPreview(); }
        catch (error) { this.alert(error.message); }
      });
      document.getElementById('mapClearSelection')?.addEventListener('click', () => {
        this.editor.clearSelection(); this.review = null; this.renderEditor(); this.renderGrid();
      });
      document.getElementById('mapAdjustmentMode')?.addEventListener('change', () => { this.syncModeSwitch(); this.applyAdjustment(); });
      // Seletor segmentado (Somar · Definir · %): um toque troca o modo; o <select> escondido segue sendo a fonte única do modo.
      document.querySelectorAll('[data-map-mode]').forEach(button => button.addEventListener('click', () => {
        const select = document.getElementById('mapAdjustmentMode');
        if (!select || select.value === button.dataset.mapMode) return;
        select.value = button.dataset.mapMode;
        this.syncModeSwitch();
        this.applyAdjustment();
      }));
      this.syncModeSwitch();
      document.getElementById('mapAdjustmentValue')?.addEventListener('input', () => this.applyAdjustment());
      document.querySelectorAll('[data-map-nudge]').forEach(button => button.addEventListener('click', () => {
        const input = document.getElementById('mapAdjustmentValue');
        if (!input) return;
        input.value = String((finite(input.value) || 0) + Number(button.dataset.mapNudge || 0));
        this.applyAdjustment();
      }));
      document.getElementById('mapReviewButton')?.addEventListener('click', () => this.writePrepared());
      document.getElementById('mapDismissResult')?.addEventListener('click', () => this.dismissResult());
      document.getElementById('mapUndoButton')?.addEventListener('click', () => this.undoLast());
      document.getElementById('mapRereadButton')?.addEventListener('click', () => { this.dismissResult(); this.startRead(); });
    }

    /** Destaca o modo escolhido e diz a unidade do número digitado ao lado (K, não "valor"). */
    syncModeSwitch() {
      const mode = document.getElementById('mapAdjustmentMode')?.value || 'percent';
      document.querySelectorAll('[data-map-mode]').forEach(button => {
        const on = button.dataset.mapMode === mode;
        button.classList.toggle('active', on);
        button.setAttribute('aria-checked', on ? 'true' : 'false');
      });
      const unit = { percent: '% sobre o K', delta: 'K a somar', target: 'K final' }[mode] || 'K';
      text('mapAdjustmentUnit', unit);
    }

    /** O cabo/ECU voltou: lê de novo sozinho (ler é automático; só o toque do dono grava). */
    onReconnect() {
      if (this.reading || this.restorePhase || this.store.get().map?.state === 'writing') return;
      if (this.store.get().route !== 'map') { this.rereadOnEnter = true; return; }
      this.startRead(true);
      text('mapSourceStatus', 'ECU voltou · relendo o Mapa K');
    }

    onEnter(context) {
      this.pendingContext = context || null;
      if (this.rereadOnEnter) {
        this.rereadOnEnter = false;
        if (!this.reading && this.store.get().map?.state !== 'writing') { this.startRead(true); return; }
      }
      // O slot "última operação" é compartilhado com a Curva K/Refino: nunca herdar o estado de outra tela.
      if (this.store.get().map?.state !== 'writing') this.lastOperationState = '';
      if (!this.editor.hasMap() && !this.reading) {
        this.startRead(true);
        return;
      }
      if (this.editor.hasMap()) this.applyContext(this.pendingContext);
    }

    settleReadFailure(message, result) {
      this.reading = false;
      this.editor.reset();
      this.cells.clear();
      this.rowHeaders = [];
      this.columnHeaders = [];
      if (this.host) {
        // Trava de segurança (saída do modo de gravação não confirmada): um toque, a ECU confirma a saída.
        this.host.innerHTML = result && result.safetyLocked === true
          ? '<div class="map-empty-state"><b>Mapa K bloqueado por segurança</b><span>A saída do modo de gravação não foi confirmada. Toque para a ECU confirmar a saída.</span><button id="mapReleaseButton" type="button" class="primary" style="min-height:76px;min-width:300px;font-size:24px">Liberar Mapa K</button></div>'
          : '<div class="map-empty-state"><b>Mapa indisponível</b><span>Leitura da ECU não confirmada. Verifique a conexão e tente novamente.</span></div>';
        document.getElementById('mapReleaseButton')?.addEventListener('click', () => this.releaseInsertion());
      }
      text('mapSourceStatus', 'Mapa não confirmado');
      this.store.patch({ map: { ...this.store.get().map, state: 'failed', data: null, selection: 0, review: null } });
      if (message) this.alert(message);
    }

    /**
     * Um toque do dono em "Liberar Mapa K": o Kotlin manda a saída do modo de gravação pela fila normal e só
     * `recovered === true` (ACK da ECU) vira "Mapa K liberado". Falha de cabo ≠ recusa da ECU (failureKind).
     * O acompanhamento roda no poll do scheduler (nenhum timer de tela).
     */
    releaseInsertion() {
      if (this.releasing) return;
      const started = this.api.releaseMapInsertion();
      if (!started?.ok || !started?.started) {
        this.alert(failureText(started, 'Não foi possível liberar o Mapa K.'));
        return;
      }
      this.releasing = true;
      this.releaseTicks = 0;
      const button = document.getElementById('mapReleaseButton');
      if (button) { button.disabled = true; button.textContent = 'Liberando…'; }
    }

    pollRelease() {
      if (!this.releasing) return;
      const operation = this.api.mapWriteOperation();
      if (operation && operation.busy && this.releaseTicks < 600) { this.releaseTicks += 1; return; }
      this.releasing = false;
      if (operation && operation.ok === true && operation.recovered === true) {
        if (this.host) this.host.innerHTML = '<div class="map-empty-state"><b>Mapa K liberado</b><span>A ECU confirmou a saída. Toque em Reler ECU para ler o mapa desta sessão.</span></div>';
        text('mapSourceStatus', 'Mapa K liberado · releia a ECU');
      } else {
        this.alert(failureText(operation, 'A ECU não confirmou a saída. O Mapa K continua bloqueado.'));
        const button = document.getElementById('mapReleaseButton');
        if (button) { button.disabled = false; button.textContent = 'Liberar Mapa K'; }
      }
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

    poll() {
      if (this.reading) {
        const result = this.api.mapReadResult();
        if (!result?.busy && result?.state !== 'READING') {
          this.reading = false;
          if (!result?.ok || result?.state === 'FAILED') {
            this.settleReadFailure(failureText(result, 'Falha ao ler o Mapa K.'), result);
          } else {
            try {
              this.editor.load(result);
              this.buildGrid();
              this.renderGrid();
              this.renderEditor();
              text('mapSourceStatus', `ECU confirmada · ${result.writableCells || 144} células`);
              this.store.patch({ map: { ...this.store.get().map, state: 'ready', data: result, selection: 0, review: null } });
              this.applyContext(this.pendingContext || this.store.get().routeContext);
            } catch (error) {
              this.settleReadFailure(error.message);
            }
          }
        }
      }
      this.pollWrite();
      this.pollRelease();
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
      corner.title = 'Linha técnica 0C protegida: visível ao protocolo, fora da seleção em massa e da escrita manual.';
      corner.innerHTML = '<small>ms ↓</small><b>RPM →</b>';
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
        rowHeader.title = 'Selecionar ou desmarcar toda esta faixa de injeção';
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
        if (this.editor.selectionCount() > 0) {
          const preview = this.api.previewMapAdjustment(this.editor.selectedCells(), mode, value);
          if (!preview?.ok || !Array.isArray(preview.items)) throw new Error(preview?.error || 'Prévia Kotlin do Mapa K indisponível.');
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
        if (deltaNode) deltaNode.textContent = preview ? signed(preview.target - preview.current) : '';
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
      text('mapSelectionCount', D().plural(count, 'selecionada', 'selecionadas'));
      const button = document.getElementById('mapReviewButton');
      if (button) {
        // O botão conta as células que MUDAM de verdade (a prévia do Kotlin), não só as selecionadas: nunca "Gravar 144 células"
        // junto com "a alteração não muda nenhuma célula".
        let changed = 0;
        if (count) { try { changed = this.editor.buildReview().count; } catch (_) { changed = 0; } }
        button.disabled = changed === 0;
        button.textContent = !count ? 'Selecione células'
          : changed === 0 ? 'Digite o ajuste para mudar o K'
            : `Gravar ${D().plural(changed, 'célula', 'células')}`;
      }
      if (Number.isInteger(activeRow) && Number.isInteger(activeColumn) && this.editor.hasMap()) {
        const snapshot = this.editor.snapshot();
        text('mapActiveCell', `${fmt(snapshot.axes.petrolBins[activeRow], 1)} ms · ${snapshot.axes.rpmBins[activeColumn]} RPM`);
      }
      this.store.patch({ map: { ...this.store.get().map, selection: count, review: this.review } });
    }

    /** Só a célula em que o motor está AGORA: RPM e injeção já estão na faixa de status do cabeçalho (sem duplicar). */
    renderLiveContext(context) {
      this.liveContext = context || null;
      const known = context && Number.isInteger(context.row) && Number.isInteger(context.column);
      text('mapLiveLabel', known ? `${context.row + 1}×${context.column + 1}` : '—');
      text('mapLiveCell', known ? `célula ${context.row + 1}×${context.column + 1}` : 'célula —');
      const node = document.getElementById('mapLiveLabel');
      if (node) node.dataset.state = known ? (context.level === 'late' ? 'late' : 'fresh') : 'none';
    }

    writePrepared() {
      // Toque duplo com a ECU ocupada: a segunda chamada não envia a escrita de novo.
      if (this.store.get().map?.state === 'writing' || this.root?.classList.contains('is-writing')) return;
      try {
        if (this.editor.targetOverrides?.size !== this.editor.selectionCount()) this.applyAdjustment();
        this.review = this.editor.buildReview();
      } catch (error) {
        this.alert(error.message);
        return;
      }
      if (!this.review?.items?.length) return;
      const result = this.api.writeMap(this.review.items, 0, 0, 'Ajuste manual confirmado na UI clean-slate');
      if (!result?.ok || !result?.started) {
        this.alert(failureText(result, 'A escrita não iniciou.'));
        this.review = null;
        this.renderEditor();
        return;
      }
      this.root?.classList.add('is-writing');
      this.setStep(0);
      this.lastOperationState = '';
      this.undoId = '';
      this.restorePhase = '';
      this.showResultButtons(false, false);
      text('mapOperationTitle', wording().writing);
      text('mapOperationMessage', `0 de ${this.review.count} células confirmadas`);
      this.store.patch({ map: { ...this.store.get().map, state: 'writing', operation: result, review: this.review } });
    }

    dismissResult() {
      this.root?.classList.remove('is-writing', 'has-result');
      this.review = null;
      this.renderEditor();
    }

    /** Botões do cartão de resultado: Desfazer (foto desta escrita) e Reler ECU (depois de falha parcial). */
    showResultButtons(undo, reread) {
      const undoButton = document.getElementById('mapUndoButton');
      if (undoButton) undoButton.hidden = !(undo && this.undoId);
      const rereadButton = document.getElementById('mapRereadButton');
      if (rereadButton) rereadButton.hidden = !reread;
    }

    /**
     * Desfazer do Mapa K (um toque): relê o mapa (somente leitura), calcula o que volta ao valor da
     * foto desta escrita e grava de volta pelo mesmo escritor em lote, com readback.
     */
    undoLast() {
      if (!this.undoId || this.reading || this.store.get().map?.state === 'writing') return;
      const prepared = this.api.prepareMapRestore(this.undoId);
      if (!prepared?.ok || !prepared?.started) {
        this.alert(failureText(prepared, 'Não foi possível preparar o Desfazer do Mapa K.'));
        return;
      }
      this.restorePhase = 'preparing';
      this.lastOperationState = '';
      this.root?.classList.remove('has-result');
      this.root?.classList.add('is-writing');
      text('mapOperationTitle', 'Relendo o Mapa K da ECU…');
      text('mapOperationMessage', 'Somente leitura: nada foi enviado à ECU.');
      this.store.patch({ map: { ...this.store.get().map, state: 'writing' } });
    }

    finishRestoreStep(operation) {
      this.restorePhase = '';
      if (!operation.ok || operation.state !== 'COMPLETED') {
        this.settleWriteFailure({ ...operation, ok: false }, 'Não foi possível preparar o Desfazer.');
        return;
      }
      const cells = Array.isArray(operation.cells) ? operation.cells : [];
      if (!cells.length) {
        this.root?.classList.remove('is-writing');
        this.root?.classList.add('has-result');
        const result = document.getElementById('mapOperationResult');
        if (result) {
          result.dataset.level = 'ok';
          result.querySelector('b').textContent = 'Nada a desfazer';
          result.querySelector('span').textContent = 'A ECU já está igual à foto de antes da gravação.';
        }
        this.showResultButtons(false, false);
        this.store.patch({ map: { ...this.store.get().map, state: 'ready' } });
        return;
      }
      const started = this.api.restoreMap(cells, this.undoId);
      if (!started?.ok || !started?.started) {
        this.settleWriteFailure({ ...started, ok: false }, 'A restauração do Mapa K não iniciou.');
        return;
      }
      this.restorePhase = 'writing';
      this.lastOperationState = '';
      text('mapOperationTitle', wording().writing);
      text('mapOperationMessage', `0 de ${D().plural(cells.length, 'célula', 'células')} confirmadas`);
    }

    setStep(index) {
      document.querySelectorAll('#mapOperationSteps span').forEach((node, i) => { node.dataset.state = i < index ? 'done' : i === index ? 'active' : 'pending'; });
    }

    settleWriteFailure(operation, fallback) {
      this.root?.classList.remove('is-writing');
      this.root?.classList.add('has-result');
      // Falha no meio do lote: células já foram gravadas, então a ECU pode estar parcialmente alterada.
      const partial = operation.ecuPartiallyChanged === true || operation.partial === true;
      const done = Number(operation.confirmedCells) || 0;
      const result = document.getElementById('mapOperationResult');
      if (result) {
        result.dataset.level = 'critical';
        result.querySelector('b').textContent = partial ? 'ECU parcialmente alterada' : wording().failedTitle;
        const why = failureText(operation, fallback || 'Leia a ECU de novo.').trim();
        const whyEnded = /[.!?…]$/.test(why) ? why : `${why}.`;
        result.querySelector('span').textContent = partial
          ? `${D().plural(done, 'célula', 'células')} já ${done === 1 ? 'recebeu' : 'receberam'} o novo valor antes da falha. ${whyEnded} Leia a ECU de novo para ver o estado real.`
          : whyEnded;
      }
      const lastId = Array.isArray(operation.adjustmentIds) ? String(operation.adjustmentIds[operation.adjustmentIds.length - 1] || '') : String(operation.backupId || '');
      if (partial && lastId) this.undoId = lastId;
      this.showResultButtons(partial, true);
      this.editor.reset();
      this.store.patch({ map: { ...this.store.get().map, state: 'failed' } });
    }

    pollWrite() {
      // Só quem está gravando lê o slot compartilhado: um BATCH_CONFIRMED velho da Curva K/Refino
      // nunca vira "Gravado" no Mapa.
      if (this.store.get().map?.state !== 'writing') return;
      const operation = this.api.mapWriteOperation();
      if (!operation || operation.state === 'IDLE' || operation.state === 'UNAVAILABLE') return;
      if (operation.state === this.lastOperationState && !operation.busy) return;
      this.lastOperationState = operation.state;
      const progress = Math.max(0, Math.min(100, finite(operation.progress) || 0));
      const bar = document.getElementById('mapOperationProgress');
      if (bar) bar.style.width = `${progress}%`;
      text('mapOperationMessage', `${operation.confirmedCells || 0} de ${D().plural(operation.totalCells || this.review?.count || 0, 'célula', 'células')} confirmadas`);

      if (operation.busy) {
        // Etapa visível (Foto antes → Gravando → Conferindo na ECU); o texto é nosso, não o do escritor.
        this.setStep(progress >= 90 ? 2 : progress > 0 ? 1 : 0);
        text('mapOperationTitle', progress >= 90 ? wording().stages[2] + '…' : progress > 0 ? `${wording().writing.replace('…', '')} ${Math.round(progress)}%` : 'Foto antes · guardando o Mapa K atual');
        return;
      }
      if (this.restorePhase === 'preparing') {
        // A prévia do Desfazer não é uma gravação: só leva ao passo seguinte.
        this.finishRestoreStep(operation);
        return;
      }
      if (operation.state === 'BATCH_CONFIRMED' && operation.readbackValid === true) {
        this.root?.classList.remove('is-writing');
        this.root?.classList.add('has-result');
        const restored = this.restorePhase === 'writing';
        this.restorePhase = '';
        const result = document.getElementById('mapOperationResult');
        if (result) {
          result.dataset.level = 'ok';
          const confirmed = finite(operation.confirmedCells) ?? finite(operation.totalCells);
          result.querySelector('b').textContent = wording().doneTitle(confirmed === null ? 'células' : D().plural(confirmed, 'célula', 'células'), { fem: true, many: confirmed !== 1 });
          result.querySelector('span').textContent = wording().doneDetail;
        }
        // O Desfazer desta escrita é a foto que o Kotlin guardou antes dela (o id da escrita).
        const ids = Array.isArray(operation.adjustmentIds) ? operation.adjustmentIds : [];
        this.undoId = restored ? '' : String(ids[ids.length - 1] || '');
        this.showResultButtons(!restored, false);
        this.editor.reset();
        this.startRead(true);
      } else if (operation.state === 'BATCH_PARTIAL_FAILED' || operation.ok === false) {
        this.restorePhase = '';
        this.settleWriteFailure(operation, 'Releitura obrigatória.');
      }
    }

    applyContext(context) {
      if (!context || !this.editor.hasMap()) return;
      try {
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