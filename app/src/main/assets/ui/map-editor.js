(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.OmegasMapEditor = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  const ROWS = 12;
  const COLUMNS = 12;
  const MAX_SELECTION = ROWS * COLUMNS;
  const MIN_K = 100;
  const MAX_K = 180;
  const PROTOCOL_MAX_K = 255;

  const finite = value => Number.isFinite(Number(value)) ? Number(value) : null;
  const clamp = (value, minimum, maximum) => Math.max(minimum, Math.min(maximum, value));

  function exactTarget(value, label) {
    const numeric = finite(value);
    if (numeric === null || !Number.isInteger(numeric) || numeric < MIN_K || numeric > MAX_K) {
      throw new Error(`${label || 'Alvo'} fora do intervalo K confirmado [${MIN_K}–${MAX_K}].`);
    }
    return numeric;
  }

  class MapEditor {
    constructor() {
      this.rows = [];
      this.axes = { petrolBins: [], rpmBins: [] };
      this.extraRow = [];
      this.selected = new Map();
      this.targetOverrides = new Map();
      this.mode = 'percent';
      this.adjustment = 0;
      this.hash = '';
    }

    reset() {
      this.rows = [];
      this.axes = { petrolBins: [], rpmBins: [] };
      this.extraRow = [];
      this.selected.clear();
      this.targetOverrides.clear();
      this.hash = '';
      return this.snapshot();
    }

    load(payload) {
      const rows = Array.isArray(payload && payload.rows) ? payload.rows : [];
      if (rows.length !== ROWS || rows.some(row => !Array.isArray(row) || row.length !== COLUMNS)) {
        throw new Error('Mapa K precisa conter exatamente 12 × 12 células editáveis.');
      }
      // A leitura deve permanecer fiel ao U8 da ECU; somente novos alvos usam 100..180.
      this.rows = rows.map(row => row.map(value => clamp(Math.round(Number(value)), 0, PROTOCOL_MAX_K)));
      this.extraRow = Array.isArray(payload.extraRow) ? payload.extraRow.slice(0, COLUMNS) : [];
      const axes = payload.axes || {};
      this.axes = {
        petrolBins: Array.isArray(axes.petrolBins) ? axes.petrolBins.slice(0, ROWS) : [],
        rpmBins: Array.isArray(axes.rpmBins) ? axes.rpmBins.slice(0, COLUMNS) : [],
      };
      this.hash = String(payload.hash || '');
      this.selected.clear();
      this.targetOverrides.clear();
      return this.snapshot();
    }

    snapshot() {
      return {
        rows: this.rows.map(row => row.slice()),
        axes: { petrolBins: this.axes.petrolBins.slice(), rpmBins: this.axes.rpmBins.slice() },
        extraRow: this.extraRow.slice(),
        hash: this.hash,
        selectionCount: this.selected.size,
      };
    }

    hasMap() { return this.rows.length === ROWS; }
    key(row, column) { return `${row}:${column}`; }

    toggle(row, column) {
      this.assertCell(row, column);
      const key = this.key(row, column);
      if (this.selected.has(key)) {
        this.selected.delete(key);
        this.targetOverrides.delete(key);
        return false;
      }
      if (this.selected.size >= MAX_SELECTION) throw new Error(`A grade possui no máximo ${MAX_SELECTION} células graváveis.`);
      this.selected.set(key, { row, column });
      this.targetOverrides.delete(key);
      return true;
    }

    selectOnly(row, column) {
      this.selected.clear();
      this.targetOverrides.clear();
      this.assertCell(row, column);
      this.selected.set(this.key(row, column), { row, column });
    }

    selectRange(startRow, startColumn, endRow, endColumn, additive = true) {
      this.assertCell(startRow, startColumn);
      this.assertCell(endRow, endColumn);
      if (!additive) {
        this.selected.clear();
        this.targetOverrides.clear();
      }
      const rowStart = Math.min(startRow, endRow);
      const rowEnd = Math.max(startRow, endRow);
      const columnStart = Math.min(startColumn, endColumn);
      const columnEnd = Math.max(startColumn, endColumn);
      for (let row = rowStart; row <= rowEnd; row += 1) {
        for (let column = columnStart; column <= columnEnd; column += 1) {
          const cellKey = this.key(row, column);
          this.selected.set(cellKey, { row, column });
          this.targetOverrides.delete(cellKey);
        }
      }
      return this.selectionCount();
    }

    selectRow(row, additive = true) {
      if (!Number.isInteger(row) || row < 0 || row >= ROWS) throw new Error(`Linha inválida [${row}].`);
      if (!this.hasMap()) throw new Error('Leia o Mapa K antes de selecionar células.');
      if (!additive) {
        this.selected.clear();
        this.targetOverrides.clear();
      }
      for (let column = 0; column < COLUMNS; column += 1) {
        const cellKey = this.key(row, column);
        this.selected.set(cellKey, { row, column });
        this.targetOverrides.delete(cellKey);
      }
      return this.selectionCount();
    }

    selectColumn(column, additive = true) {
      if (!Number.isInteger(column) || column < 0 || column >= COLUMNS) throw new Error(`Coluna inválida [${column}].`);
      if (!this.hasMap()) throw new Error('Leia o Mapa K antes de selecionar células.');
      if (!additive) {
        this.selected.clear();
        this.targetOverrides.clear();
      }
      for (let row = 0; row < ROWS; row += 1) {
        const cellKey = this.key(row, column);
        this.selected.set(cellKey, { row, column });
        this.targetOverrides.delete(cellKey);
      }
      return this.selectionCount();
    }

    toggleRow(row) {
      if (!Number.isInteger(row) || row < 0 || row >= ROWS) throw new Error(`Linha inválida [${row}].`);
      if (!this.hasMap()) throw new Error('Leia o Mapa K antes de selecionar células.');
      const keys = Array.from({ length: COLUMNS }, (_, column) => this.key(row, column));
      const allSelected = keys.every(cellKey => this.selected.has(cellKey));
      keys.forEach((cellKey, column) => {
        if (allSelected) {
          this.selected.delete(cellKey);
          this.targetOverrides.delete(cellKey);
        } else {
          this.selected.set(cellKey, { row, column });
          this.targetOverrides.delete(cellKey);
        }
      });
      return this.selectionCount();
    }

    toggleColumn(column) {
      if (!Number.isInteger(column) || column < 0 || column >= COLUMNS) throw new Error(`Coluna inválida [${column}].`);
      if (!this.hasMap()) throw new Error('Leia o Mapa K antes de selecionar células.');
      const keys = Array.from({ length: ROWS }, (_, row) => this.key(row, column));
      const allSelected = keys.every(cellKey => this.selected.has(cellKey));
      keys.forEach((cellKey, row) => {
        if (allSelected) {
          this.selected.delete(cellKey);
          this.targetOverrides.delete(cellKey);
        } else {
          this.selected.set(cellKey, { row, column });
          this.targetOverrides.delete(cellKey);
        }
      });
      return this.selectionCount();
    }

    selectAll() {
      if (!this.hasMap()) throw new Error('Leia o Mapa K antes de selecionar células.');
      this.selected.clear();
      this.targetOverrides.clear();
      for (let row = 0; row < ROWS; row += 1) {
        for (let column = 0; column < COLUMNS; column += 1) {
          this.selected.set(this.key(row, column), { row, column });
        }
      }
      return this.selectionCount();
    }

    clearSelection() {
      this.selected.clear();
      this.targetOverrides.clear();
    }
    isSelected(row, column) { return this.selected.has(this.key(row, column)); }
    selectionCount() { return this.selected.size; }

    selectedCells() {
      if (!this.hasMap()) throw new Error('Leia o Mapa K antes de preparar alterações.');
      return [...this.selected.values()].map(({ row, column }) => ({
        row,
        column,
        current: this.rows[row][column],
        petrolMs: this.axes.petrolBins[row] ?? null,
        rpm: this.axes.rpmBins[column] ?? null,
      }));
    }

    setAdjustment(mode, value) {
      if (!['percent', 'delta', 'target'].includes(mode)) throw new Error('Modo de alteração inválido.');
      const numeric = finite(value);
      if (numeric === null) throw new Error('Informe um valor numérico.');
      this.mode = mode;
      this.adjustment = numeric;
      this.targetOverrides.clear();
    }

    setTargetOverride(row, column, target) {
      this.assertCell(row, column);
      const confirmedTarget = exactTarget(target, 'Alvo da sugestão');
      const cellKey = this.key(row, column);
      this.selected.set(cellKey, { row, column });
      this.targetOverrides.set(cellKey, confirmedTarget);
    }

    applyNativePreview(items) {
      const previewItems = Array.isArray(items) ? items : [];
      const selectedKeys = new Set(this.selected.keys());
      const next = new Map();
      previewItems.forEach(item => {
        const row = Number(item.row);
        const column = Number(item.column);
        const cellKey = this.key(row, column);
        if (!selectedKeys.has(cellKey)) return;
        this.assertCell(row, column);
        next.set(cellKey, exactTarget(item.target, 'Prévia nativa'));
      });
      if (next.size !== this.selected.size) throw new Error('Prévia nativa incompleta para a seleção atual.');
      this.targetOverrides = next;
      return this.buildReview();
    }

    setTargetOverrides(changes) {
      this.selected.clear();
      this.targetOverrides.clear();
      (Array.isArray(changes) ? changes : []).forEach(item => {
        this.setTargetOverride(Number(item.row), Number(item.column), Number(item.after ?? item.target));
      });
      return this.selectionCount();
    }

    buildReview() {
      if (!this.hasMap()) throw new Error('Leia o Mapa K antes de preparar alterações.');
      if (!this.selected.size) throw new Error('Selecione ao menos uma célula.');
      if (this.targetOverrides.size !== this.selected.size) throw new Error('Gere a prévia nativa antes de revisar.');
      const items = [];
      for (const { row, column } of this.selected.values()) {
        const current = this.rows[row][column];
        const target = this.targetOverrides.get(this.key(row, column));
        if (target === undefined) throw new Error('Prévia nativa incompleta.');
        if (target === current) continue;
        items.push({
          row,
          column,
          current,
          target,
          petrolMs: this.axes.petrolBins[row] ?? null,
          rpm: this.axes.rpmBins[column] ?? null,
        });
      }
      if (!items.length) throw new Error('A alteração escolhida não muda nenhuma célula.');
      return { mode: this.mode, adjustment: this.adjustment, count: items.length, items };
    }

    applyReadback(payload) {
      if (payload && Array.isArray(payload.rows)) this.load({ ...payload, axes: payload.axes || this.axes });
    }

    assertCell(row, column) {
      if (!Number.isInteger(row) || !Number.isInteger(column) || row < 0 || row >= ROWS || column < 0 || column >= COLUMNS) {
        throw new Error(`Célula inválida [${row},${column}].`);
      }
      if (!this.hasMap()) throw new Error('Leia o Mapa K antes de selecionar células.');
    }
  }

  return { MapEditor, ROWS, COLUMNS, MAX_SELECTION, MIN_K, MAX_K };
});
