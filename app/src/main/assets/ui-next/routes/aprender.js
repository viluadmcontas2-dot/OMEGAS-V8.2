import { renderMapKEditor } from '../components/map-k-editor.js';
import { renderSuggestionQueue } from '../components/suggestion-queue.js';
import { escapeText, format, humanDirection, semanticItem } from './common.js';

export const aprenderRoute = {
  mount(ctx, state) {
    ctx.workspace.innerHTML = `<section class="route-page" data-route="aprender">
      <div class="route-heading"><div><h1>Aprender</h1><p>Medido, referência equivalente, diferença e revisão — sem misturar as origens.</p></div>
      <button class="secondary-action" id="refresh-cell-context" type="button">Atualizar contexto</button></div>
      <div id="learning-context-root"></div>
      <div id="learning-map-editor"></div>
      <div id="suggestion-queue-root"></div>
    </section>`;
    document.getElementById('refresh-cell-context')?.addEventListener('click', ctx.loadCellContext);
    if (!state.cellContext) ctx.loadCellContext();
    if (state.suggestions.state !== 'READY') ctx.loadSuggestions();
    this.update(ctx, state);
  },

  update(ctx, state) {
    const root = document.getElementById('learning-context-root');
    if (!root) return;
    const c = state.cellContext;
    if (!c) {
      root.className = 'empty-state';
      root.innerHTML = '<div><strong>Sem região comparável agora</strong>Continue coletando normalmente. A tela não inventa referência, rico/pobre ou sugestão.</div>';
      renderEditor(ctx, state);
      renderSuggestions(ctx, state);
      return;
    }
    const comparison = c.comparison || {};
    const calibration = c.calibration || {};
    root.className = 'route-page';
    root.innerHTML = `<section class="semantic-grid">
      ${semanticItem('AGORA', c.currentCondition?.petrolInjection || {})}
      ${semanticItem('REFERÊNCIA', c.gasolineEquivalentReference || {}, 'reference')}
      ${semanticItem('NO GNV', c.cngObservation || {}, 'observed')}
    </section>
    <section class="learning-now"><span class="section-kicker">Diferença • gasolina↔GNV</span>
      <strong class="learning-state">${comparison.comparable ? humanDirection(comparison.direction) : 'Ainda não comparável'}</strong>
      <p class="learning-reason">${escapeText(comparison.reason || 'Referência equivalente ainda insuficiente.')}</p>
      <div class="learning-meta"><span class="state-chip">${comparison.comparable ? `${format(comparison.differenceMs, 2)} ms` : '—'}</span><span class="state-chip">${comparison.comparable ? `${format(comparison.differencePct, 1)}%` : 'sem %'}</span><span class="state-chip">qualidade ${comparison.quality == null ? '—' : Math.round(comparison.quality * 100) + '%'}</span></div>
    </section>
    <section class="proposal-banner"><div><strong>${comparison.comparable ? 'Ajuste local — Mapa K' : 'Sem sugestão local ainda'}</strong><span>K atual ${calibration.currentK ?? '—'} • K alvo ${calibration.targetK ?? '—'} • abrir não escreve</span></div>
      <button class="primary-action" id="open-learning-map-editor" type="button" ${comparison.comparable ? '' : 'disabled'}>${state.contextualEditor?.open ? 'Fechar editor' : 'Editar Mapa K aqui'}</button></section>`;
    document.getElementById('open-learning-map-editor')?.addEventListener('click', ctx.toggleLearningMapEditor);
    renderEditor(ctx, state);
    renderSuggestions(ctx, state);
  },
};

function renderEditor(ctx, state) {
  const target = document.getElementById('learning-map-editor');
  if (!target) return;
  const open = state.contextualEditor?.open && state.contextualEditor?.kind === 'MAP_K';
  target.hidden = !open;
  if (!open) { target.innerHTML = ''; return; }
  renderMapKEditor(target, ctx.mapEditorState(state), ctx.mapEditorActions());
}

function renderSuggestions(ctx, state) {
  const target = document.getElementById('suggestion-queue-root');
  if (!target) return;
  renderSuggestionQueue(target, state.suggestions, {
    onSelectReady: ctx.selectReadySuggestions,
    onOpen: ctx.openSuggestion,
  });
}
