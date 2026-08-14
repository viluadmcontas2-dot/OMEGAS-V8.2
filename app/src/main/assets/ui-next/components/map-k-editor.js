function cellKey(row, column) {
  return `${row}:${column}`;
}

function selectedSet(selection = []) {
  return new Set(selection.map((item) => typeof item === 'string' ? item : cellKey(item.row, item.column)));
}

function formatK(value) {
  const number = Number(value);
  return Number.isFinite(number) ? Math.round(number) : '—';
}

/**
 * Editor visual único do Mapa K NEXT.
 * Não toca bridge, writer, USB ou protocolo.
 */
export function renderMapKEditor(container, state, actions = {}) {
  if (!container) return;
  const map = state?.map || [];
  const selection = selectedSet(state?.selection);
  const ready = state?.state === 'READY' && map.length === 12;

  if (!ready) {
    container.innerHTML = `
      <section class="map-editor-shell" data-map-state="${state?.state || 'UNAVAILABLE'}">
        <div class="editor-header">
          <div><span class="section-kicker">Mapa K local</span><h2>Mapa ainda não confirmado</h2><p>Leia a ECU antes de selecionar ou preparar qualquer alteração.</p></div>
          <button class="primary-action" data-action="read-map" type="button">Ler Mapa K</button>
        </div>
      </section>`;
    container.querySelector('[data-action="read-map"]')?.addEventListener('click', () => actions.onRead?.());
    return;
  }

  const cells = [];
  for (let row = 0; row < 12; row += 1) {
    for (let column = 0; column < 12; column += 1) {
      const key = cellKey(row, column);
      const value = map[row]?.[column];
      const isSelected = selection.has(key);
      const isCurrent = state.currentCell?.row === row && state.currentCell?.column === column;
      cells.push(`<button class="k-cell${isSelected ? ' selected' : ''}${isCurrent ? ' current' : ''}" type="button" data-row="${row}" data-column="${column}" aria-pressed="${isSelected}"><span>${formatK(value)}</span></button>`);
    }
  }

  const selectedCount = selection.size;
  const proposal = state?.proposal;
  container.innerHTML = `
    <section class="map-editor-shell" data-map-state="READY">
      <div class="editor-header">
        <div>
          <span class="section-kicker">Mapa K local</span>
          <h2>${selectedCount ? `${selectedCount} célula${selectedCount === 1 ? '' : 's'} selecionada${selectedCount === 1 ? '' : 's'}` : 'Toque numa célula para editar'}</h2>
          <p>Célula atual tem halo próprio. Seleção de edição é preenchida; live tracing é apenas observacional.</p>
        </div>
        <div class="editor-header-actions">
          <button class="secondary-action" data-action="read-map" type="button">Reler ECU</button>
          <button class="secondary-action" data-action="clear-selection" type="button" ${selectedCount ? '' : 'disabled'}>Limpar</button>
        </div>
      </div>

      <div class="map-editor-grid-wrap">
        <div class="map-axis-caption">RPM →</div>
        <div class="k-grid-stage">
          <div class="k-grid" role="grid" aria-label="Mapa K 12 por 12">${cells.join('')}</div>
          <div class="live-trace-layer" data-live-trace-layer aria-hidden="true">
            ${[0, 1, 2, 3].map((index) => `<span class="live-trace-weight" data-trace-index="${index}" hidden></span>`).join('')}
          </div>
        </div>
        <div class="map-axis-caption vertical">Petrol Inj. ↑</div>
      </div>

      <div class="map-editor-controls">
        <div class="selection-summary">
          <span class="section-kicker">Edição preparada</span>
          <strong>${selectedCount ? `${selectedCount}/144` : 'Nenhuma célula'}</strong>
          <small>A linha técnica não pertence a esta grade gravável.</small>
        </div>
        <div class="nudge-group" aria-label="Ajustar proposta">
          <button class="secondary-action nudge" data-delta="-5" type="button" ${selectedCount ? '' : 'disabled'}>−5</button>
          <button class="secondary-action nudge" data-delta="-1" type="button" ${selectedCount ? '' : 'disabled'}>−1</button>
          <button class="secondary-action nudge" data-delta="1" type="button" ${selectedCount ? '' : 'disabled'}>+1</button>
          <button class="secondary-action nudge" data-delta="5" type="button" ${selectedCount ? '' : 'disabled'}>+5</button>
        </div>
        <button class="primary-action" data-action="review" type="button" ${selectedCount && proposal ? '' : 'disabled'}>Revisar alterações</button>
      </div>

      ${proposal ? `<div class="proposal-banner"><strong>Proposta pronta para revisão</strong><span>${proposal.summary || 'Antes/depois calculado pelo núcleo.'}</span></div>` : ''}
    </section>`;

  container.querySelectorAll('.k-cell').forEach((button) => {
    button.addEventListener('click', () => actions.onToggleCell?.({ row: Number(button.dataset.row), column: Number(button.dataset.column) }));
  });
  container.querySelectorAll('[data-delta]').forEach((button) => {
    button.addEventListener('click', () => actions.onNudge?.(Number(button.dataset.delta)));
  });
  container.querySelector('[data-action="read-map"]')?.addEventListener('click', () => actions.onRead?.());
  container.querySelector('[data-action="clear-selection"]')?.addEventListener('click', () => actions.onClearSelection?.());
  container.querySelector('[data-action="review"]')?.addEventListener('click', () => actions.onReview?.());
  updateMapKLiveTrace(container, state?.liveTrace, state?.liveTracingEnabled !== false);
}

/** Atualiza somente quatro marcadores pequenos; nunca reconstrói a grade. */
export function updateMapKLiveTrace(container, trace, enabled = true) {
  const layer = container?.querySelector?.('[data-live-trace-layer]');
  if (!layer) return;
  const markers = [...layer.querySelectorAll('[data-trace-index]')];
  const usable = enabled && trace?.valid === true ? (trace.weights || []).slice(0, 4) : [];
  markers.forEach((marker, index) => {
    const item = usable[index];
    if (!item || item.row < 0 || item.row > 11 || item.column < 0 || item.column > 11) {
      marker.hidden = true;
      marker.textContent = '';
      return;
    }
    const weight = Math.max(0, Math.min(1, Number(item.weight) || 0));
    marker.hidden = false;
    marker.style.left = `${((item.column + 0.5) / 12) * 100}%`;
    marker.style.top = `${((item.row + 0.5) / 12) * 100}%`;
    marker.style.opacity = String(0.28 + weight * 0.72);
    marker.style.setProperty('--trace-scale', String(0.72 + weight * 0.65));
    marker.textContent = weight >= 0.12 ? `${Math.round(weight * 100)}%` : '';
  });
  layer.dataset.traceSequence = String(trace?.sequence ?? '');
  layer.dataset.traceMode = enabled ? 'on' : 'off';
}
