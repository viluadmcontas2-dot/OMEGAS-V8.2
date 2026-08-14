import { escapeText, format } from './common.js';

function tone(state) {
  if (state === 'VALIDADO') return 'validated';
  if (state === 'OBSERVADO') return 'observed';
  if (state === 'PREVISTO') return 'predicted';
  return 'unknown';
}

export const predictorRoute = {
  mount(ctx, state) {
    ctx.workspace.innerHTML = `<section class="route-page" data-route="predictor">
      <div class="route-heading"><div><h1>Predictor</h1><p>K plausível a partir de evidência direta — com confiança, suporte e limites visíveis.</p></div>
      <button class="secondary-action" id="refresh-predictor" type="button">Atualizar ciência</button></div>
      <div id="predictor-root"></div></section>`;
    document.getElementById('refresh-predictor')?.addEventListener('click', ctx.loadPredictor);
    if (state.predictor.state !== 'READY') ctx.loadPredictor();
    this.update(ctx, state);
  },

  update(ctx, state) {
    const root = document.getElementById('predictor-root');
    if (!root) return;
    const predictor = state.predictor || {};
    if (predictor.state !== 'READY') {
      root.className = 'empty-state';
      root.innerHTML = `<div><strong>${predictor.state === 'BUSY' ? 'Atualizando Predictor' : 'Predictor ainda indisponível'}</strong>${escapeText(predictor.error || 'A previsão só aparece quando o núcleo possui suporte científico suficiente.')}</div>`;
      return;
    }
    const cells = predictor.cells || [];
    root.className = 'predictor-layout';
    root.innerHTML = `<section class="predictor-summary">
        <div><span class="section-kicker">Revisão científica</span><strong>${escapeText(predictor.revision || '—')}</strong></div>
        <div class="learning-meta"><span class="state-chip">não extrapola</span><span class="state-chip">previsão não vira evidência</span><span class="state-chip">zero escrita automática</span></div>
      </section>
      <section class="predictor-grid" role="grid" aria-label="Predictor 12 por 12">
        ${cells.map((cell) => `<button class="predictor-cell ${tone(cell.state)}" type="button" data-key="${escapeText(cell.key)}" aria-label="${escapeText(cell.state)} K ${cell.targetK ?? 'indisponível'}">
          <span class="predictor-state-dot"></span><b>${cell.targetK ?? '—'}</b><small>${cell.state === 'PREVISTO' ? Math.round((cell.predictionConfidence || 0) * 100) + '%' : cell.state}</small>
        </button>`).join('')}
      </section>
      <div id="predictor-detail" class="learning-now"><span class="section-kicker">Toque numa região</span><strong class="learning-state">Entenda antes de ajustar</strong><p class="learning-reason">A cor informa a origem: validado/observado é evidência direta; previsto é inferência; desconhecido permanece sem alvo.</p></div>`;
    root.querySelectorAll('.predictor-cell').forEach((button) => button.addEventListener('click', () => {
      const cell = cells.find((item) => item.key === button.dataset.key);
      renderDetail(ctx, cell);
    }));
  },
};

function renderDetail(ctx, cell) {
  const root = document.getElementById('predictor-detail');
  if (!root || !cell) return;
  const confidence = cell.predicted ? cell.predictionConfidence : cell.confidence;
  const canReview = cell.targetK != null && cell.state !== 'DESCONHECIDO';
  root.innerHTML = `<span class="section-kicker">${escapeText(cell.state)} • região ${escapeText(cell.key)}</span>
    <strong class="learning-state">K atual ${cell.currentK ?? '—'} → K alvo ${cell.targetK ?? '—'}</strong>
    <p class="learning-reason">${cell.predicted ? escapeText(cell.predictionReason || 'Interpolação conservativa de suporte direto.') : 'Evidência direta; não é uma previsão autoalimentada.'}</p>
    <div class="learning-meta"><span class="state-chip">confiança ${confidence == null ? '—' : Math.round(confidence * 100) + '%'}</span><span class="state-chip">${cell.supportCount || 0} suportes</span><span class="state-chip">${cell.distinctTrajectories || 0} trajetórias</span></div>
    <button class="primary-action" id="predictor-edit-map" type="button" ${canReview ? '' : 'disabled'}>Revisar no Mapa K</button>`;
  document.getElementById('predictor-edit-map')?.addEventListener('click', () => {
    ctx.store.dispatch({ type: 'MAP_K_STATE', payload: { selection: [{ row: cell.row, column: cell.column }], proposal: null } });
    ctx.router.navigate('mapa-k');
  });
}
