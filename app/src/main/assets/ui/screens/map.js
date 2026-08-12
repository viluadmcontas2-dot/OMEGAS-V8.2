(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  function finite(value) { return Number.isFinite(Number(value)) ? Number(value) : null; }
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
      this.pendingContext = null;
      this.liveContext = null;
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
      document.getElementById('mapReadButton')?.addEventListener('click', () => this.startRead());
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
      document.getElementById('mapReviewButton')?.addEventListener('click', () => this.openReview());
      document.getElementById('mapReviewBack')?.addEventListener('click', () => this.closeReview());
      document.getElementById('mapWriteButton')?.addEventListener('click', () => this.writeReview());
      document.getElementById('mapDismissResult')?.addEventListener('click', () => this.closeReview());
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

    startRead(automatic) {
      if (this.reading) return;
      const result = this.api.startMapRead();
      if (!result?.ok || !result?.started) {
        this.alert(result?.error || 'Não foi possível iniciar a leitura do Mapa K.');
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
            this.editor.reset();
            this.alert(result?.error || 'Falha ao ler o Mapa K.');
            text('mapSourceStatus', 'Mapa não confirmado');
          } else {
            try {
              this.editor.load(result);
              this.buildGrid();
              this.renderGrid();
              this.renderEditor();
              text('mapSourceStatus', `ECU confirmada · ${result.writableCells || 144} células graváveis`);
              this.store.patch({ map: { ...this.store.get().map, state: 'ready', data: result, selection: 0, review: null } });
              this.applyContext(this.pendingContext || this.store.get().routeContext);
            } catch (error) {
              this.alert(error.message);
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
      corner.innerHTML = '<small>Petrol Inj.</small><b>ms \\ RPM</b>';
      table.appendChild(corner);

      for (let column = 0; column < 12; column += 1) {
        const header = document.createElement('button');
        header.type = 'button';
        header.className = 'map-axis-header map-rpm-header';
        header.dataset.selectColumn = String(column);
        header.innerHTML = `<small>RPM</small><b>${Math.round(snapshot.axes.rpmBins[column] || 0).toLocaleString('pt-BR')}</b>`;
        header.title = 'Selecionar ou desmarcar toda esta faixa de RPM';
        this.columnHeaders.push(header);
        table.appendChild(header);
      }

      for (let row = 0; row < 12; row += 1) {
        const rowHeader = document.createElement('button');
        rowHeader.type = 'button';
        rowHeader.className = 'map-axis-header map-ms-header';
        rowHeader.dataset.selectRow = String(row);
        rowHeader.innerHTML = `<small>Petrol Inj.</small><b>${fmt(snapshot.axes.petrolBins[row], 1)} ms</b>`;
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
      technical.innerHTML = '<b>Linha técnica 0C protegida</b><span>Visível ao protocolo, fora de qualquer seleção em massa e fora da escrita manual.</span>';
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
      text('mapSelectionCount', `${count} selecionada${count === 1 ? '' : 's'}`);
      const button = document.getElementById('mapReviewButton');
      if (button) {
        button.disabled = count === 0;
        button.textContent = count ? `Revisar ${count} alteração${count === 1 ? '' : 'ões'}` : 'Selecione células';
      }
      if (Number.isInteger(activeRow) && Number.isInteger(activeColumn) && this.editor.hasMap()) {
        const snapshot = this.editor.snapshot();
        text('mapActiveCell', `${fmt(snapshot.axes.petrolBins[activeRow], 1)} ms · ${snapshot.axes.rpmBins[activeColumn]} RPM`);
      }
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

    openReview() {
      try {
        if (this.editor.targetOverrides?.size !== this.editor.selectionCount()) this.applyAdjustment();
        this.review = this.editor.buildReview();
      } catch (error) {
        this.alert(error.message);
        return;
      }
      const reviewHost = document.getElementById('mapReviewList');
      if (reviewHost) {
        const first = this.review.items.slice(0, 16);
        reviewHost.innerHTML = first.map(item => `<div><span>${fmt(item.petrolMs, 1)} ms · ${Math.round(item.rpm).toLocaleString('pt-BR')} RPM</span><b>${item.current} → ${item.target}</b></div>`).join('')
          + (this.review.items.length > first.length ? `<p>+ ${this.review.items.length - first.length} alterações na mesma intenção humana</p>` : '');
      }
      text('mapReviewCount', `${this.review.count} alteração${this.review.count === 1 ? '' : 'ões'}`);
      const write = document.getElementById('mapWriteButton');
      if (write) write.textContent = `Gravar ${this.review.count} alteração${this.review.count === 1 ? '' : 'ões'} na ECU`;
      this.root?.classList.add('is-reviewing');
      this.store.patch({ map: { ...this.store.get().map, review: this.review } });
    }

    closeReview() {
      this.root?.classList.remove('is-reviewing', 'is-writing', 'has-result');
      this.review = null;
      this.renderEditor();
    }

    writeReview() {
      if (!this.review?.items?.length) return;
      const result = this.api.writeMap(this.review.items, 3, 150, 'Ajuste manual confirmado na UI clean-slate');
      if (!result?.ok || !result?.started) {
        this.alert(result?.error || 'A escrita não iniciou.');
        return;
      }
      this.root?.classList.remove('is-reviewing');
      this.root?.classList.add('is-writing');
      this.lastOperationState = '';
      text('mapOperationTitle', 'Escrita manual em andamento');
      text('mapOperationMessage', `0 de ${this.review.count} células confirmadas`);
      this.store.patch({ map: { ...this.store.get().map, state: 'writing', operation: result } });
    }

    pollWrite() {
      const operation = this.api.mapWriteOperation();
      if (!operation || operation.state === 'IDLE' || operation.state === 'UNAVAILABLE') return;
      if (operation.state === this.lastOperationState && !operation.busy) return;
      this.lastOperationState = operation.state;
      const progress = Math.max(0, Math.min(100, finite(operation.progress) || 0));
      const bar = document.getElementById('mapOperationProgress');
      if (bar) bar.style.width = `${progress}%`;
      text('mapOperationMessage', `${operation.confirmedCells || 0} de ${operation.totalCells || this.review?.count || 0} células confirmadas`);

      if (operation.busy) {
        text('mapOperationTitle', operation.writerMessage || 'Checkpoint · escrita · ACK · readback');
        return;
      }
      if (operation.state === 'BATCH_CONFIRMED' && operation.readbackValid === true) {
        this.root?.classList.remove('is-writing');
        this.root?.classList.add('has-result');
        const result = document.getElementById('mapOperationResult');
        if (result) {
          result.dataset.level = 'ok';
          result.querySelector('b').textContent = `${operation.confirmedCells || operation.totalCells} alterações confirmadas pela ECU`;
          result.querySelector('span').textContent = 'ACK e readback concluídos. O mapa será relido para atualizar a tela.';
        }
        this.editor.reset();
        this.startRead(true);
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
        this.editor.reset();
      }
    }

    applyContext(context) {
      if (!context || !this.editor.hasMap()) return;
      const suggestion = context.suggestion;
      const changes = Array.isArray(suggestion?.mapChanges) ? suggestion.mapChanges : [];
      try {
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
