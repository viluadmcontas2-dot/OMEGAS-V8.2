(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  function fmt(value, digits) {
    const number = Number(value);
    if (!Number.isFinite(number)) return '—';
    return number.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits });
  }

  class PhysicalGrid {
    constructor(host, options) {
      this.host = host;
      this.options = options || {};
      this.rows = Number(this.options.rows) || 12;
      this.columns = Number(this.options.columns) || 12;
      this.cells = new Map();
      this.traceKeys = new Set();
      this.activeTraceKey = null;
      this.traceTrail = new Map();
      this.traceTrailMs = 1400;
      this.traceTrailMax = 16;
      this.columnAxes = [];
      this.rowAxes = [];
      this.build();
    }

    key(row, column) { return `${row}:${column}`; }

    build() {
      if (!this.host) return;
      this.host.innerHTML = '';
      this.host.classList.add('physical-grid', 'physical-grid-with-axes');
      this.host.style.setProperty('--grid-columns', String(this.columns));
      const fragment = document.createDocumentFragment();
      const corner = document.createElement('div');
      corner.className = 'physical-axis-corner';
      corner.textContent = 'ms \\ RPM';
      fragment.appendChild(corner);

      for (let column = 0; column < this.columns; column += 1) {
        const axis = document.createElement('div');
        axis.className = 'physical-axis physical-axis-rpm';
        axis.dataset.axisColumn = String(column);
        axis.textContent = '—';
        this.columnAxes.push(axis);
        fragment.appendChild(axis);
      }

      for (let row = 0; row < this.rows; row += 1) {
        const rowAxis = document.createElement('div');
        rowAxis.className = 'physical-axis physical-axis-ms';
        rowAxis.dataset.axisRow = String(row);
        rowAxis.textContent = '—';
        this.rowAxes.push(rowAxis);
        fragment.appendChild(rowAxis);
        for (let column = 0; column < this.columns; column += 1) {
          const button = document.createElement('button');
          button.type = 'button';
          button.className = 'physical-cell';
          button.dataset.row = String(row);
          button.dataset.column = String(column);
          button.dataset.key = this.key(row, column);
          button.innerHTML = '<span class="cell-value">·</span><small class="cell-subvalue"></small>';
          button._valueNode = button.querySelector('.cell-value');
          button._subvalueNode = button.querySelector('.cell-subvalue');
          button._visualSignature = '';
          this.cells.set(this.key(row, column), button);
          fragment.appendChild(button);
        }
      }
      this.host.appendChild(fragment);
      this.host.addEventListener('click', event => {
        const cell = event.target.closest('.physical-cell');
        if (!cell || !this.host.contains(cell)) return;
        if (typeof this.options.onCell === 'function') {
          this.options.onCell(Number(cell.dataset.row), Number(cell.dataset.column), cell);
        }
      });
    }

    setAxes(rpmBins, petrolBins) {
      const rpm = Array.isArray(rpmBins) ? rpmBins : [];
      const petrol = Array.isArray(petrolBins) ? petrolBins : [];
      this.columnAxes.forEach((node, index) => {
        const next = Number.isFinite(Number(rpm[index])) ? Math.round(Number(rpm[index])).toLocaleString('pt-BR') : '—';
        if (node.textContent !== next) node.textContent = next;
      });
      this.rowAxes.forEach((node, index) => {
        const next = Number.isFinite(Number(petrol[index])) ? fmt(petrol[index], 1) : '—';
        if (node.textContent !== next) node.textContent = next;
      });
    }

    cell(row, column) { return this.cells.get(this.key(row, column)) || null; }

    resetVisual() {
      this.cells.forEach(cell => {
        cell.className = 'physical-cell';
        cell.style.removeProperty('--heat');
        if (cell._valueNode) cell._valueNode.textContent = '·';
        if (cell._subvalueNode) cell._subvalueNode.textContent = '';
        cell._visualSignature = '';
        delete cell.dataset.tone;
        delete cell.dataset.state;
      });
    }

    updateCell(row, column, visual) {
      const cell = this.cell(row, column);
      if (!cell) return;
      const value = visual || {};
      const text = value.text === undefined ? undefined : String(value.text);
      const subtext = value.subtext === undefined ? undefined : String(value.subtext || '');
      const heat = value.heat === undefined ? undefined : Math.max(0, Math.min(1, Number(value.heat) || 0));
      const tone = value.tone || '';
      const state = value.state || '';
      const hasData = value.hasData !== false && value.text !== undefined && value.text !== '·';
      const selected = value.selected === true;
      const preview = value.preview === true;
      const signature = [text, subtext, heat === undefined ? '-' : heat.toFixed(3), tone, state, hasData ? 1 : 0, selected ? 1 : 0, preview ? 1 : 0].join('|');
      if (signature === cell._visualSignature) return;
      cell._visualSignature = signature;

      if (text !== undefined && cell._valueNode && cell._valueNode.textContent !== text) cell._valueNode.textContent = text;
      if (value.subtext !== undefined && cell._subvalueNode && cell._subvalueNode.textContent !== subtext) cell._subvalueNode.textContent = subtext;
      if (heat !== undefined) cell.style.setProperty('--heat', String(heat));
      if (tone) cell.dataset.tone = tone; else delete cell.dataset.tone;
      if (state) cell.dataset.state = state; else delete cell.dataset.state;
      cell.classList.toggle('has-data', hasData);
      cell.classList.toggle('selected', selected);
      cell.classList.toggle('preview', preview);
    }

    setTrace(points, nearest) {
      const now = Date.now();
      const contributors = Array.isArray(points) ? points : [];
      const nextKeys = new Set();
      const weights = new Map();

      contributors.forEach(point => {
        const row = Number(point && point.row);
        const column = Number(point && point.column);
        if (!Number.isFinite(row) || !Number.isFinite(column)) return;
        const cellKey = this.key(row, column);
        nextKeys.add(cellKey);
        weights.set(cellKey, Math.max(0, Math.min(1, Number(point.weight) || 0)));
      });

      this.traceKeys.forEach(cellKey => {
        if (!nextKeys.has(cellKey)) this.traceTrail.set(cellKey, now);
      });
      if (this.activeTraceKey && !nextKeys.has(this.activeTraceKey)) {
        this.traceTrail.set(this.activeTraceKey, now);
      }

      const nearestRow = Number(nearest && nearest.row);
      const nearestColumn = Number(nearest && nearest.column);
      const nearestKey = Number.isFinite(nearestRow) && Number.isFinite(nearestColumn)
        ? this.key(nearestRow, nearestColumn)
        : null;
      this.activeTraceKey = nearestKey || nextKeys.values().next().value || null;
      this.traceKeys = nextKeys;

      const trailMs = Math.max(0, Number(this.traceTrailMs) || 0);
      this.traceTrail.forEach((timestamp, cellKey) => {
        if (nextKeys.has(cellKey) || (trailMs > 0 && now - timestamp > trailMs)) {
          this.traceTrail.delete(cellKey);
        }
      });
      const maxTrail = Math.max(0, Number(this.traceTrailMax) || 0);
      if (maxTrail && this.traceTrail.size > maxTrail) {
        const newest = [...this.traceTrail.entries()].sort((left, right) => right[1] - left[1]).slice(0, maxTrail);
        this.traceTrail = new Map(newest);
      }

      this.cells.forEach((cell, cellKey) => {
        const contributing = nextKeys.has(cellKey);
        const trail = this.traceTrail.has(cellKey) && !contributing;
        cell.classList.toggle('live-contributor', contributing);
        cell.classList.toggle('live-nearest', nearestKey === cellKey);
        cell.classList.toggle('live-trail', trail);
        if (contributing) cell.style.setProperty('--trace-weight', String(weights.get(cellKey) || 0));
        else cell.style.removeProperty('--trace-weight');
      });
    }
    setSelected(keys) {
      const selected = keys instanceof Set ? keys : new Set(keys || []);
      this.cells.forEach((cell, cellKey) => {
        const active = selected.has(cellKey);
        if (cell.classList.contains('selected') !== active) {
          cell.classList.toggle('selected', active);
          cell._visualSignature = '';
        }
      });
    }
  }

  ns.PhysicalGrid = PhysicalGrid;
})(typeof window !== 'undefined' ? window : globalThis);
