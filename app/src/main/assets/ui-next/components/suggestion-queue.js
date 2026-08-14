import { escapeText } from '../routes/common.js';

function lifecycleTone(value) {
  if (value === 'PENDENTE') return 'ready';
  if (value === 'OBSERVANDO') return 'watching';
  if (value === 'APLICADA') return 'applied';
  return 'superseded';
}

export function renderSuggestionQueue(container, state, actions = {}) {
  if (!container) return;
  const items = state?.items || [];
  const active = items.filter((item) => item.lifecycle === 'PENDENTE' || item.lifecycle === 'OBSERVANDO');
  const ready = items.filter((item) => item.actionable);
  container.innerHTML = `<section class="suggestion-queue">
    <div class="editor-header">
      <div><span class="section-kicker">Sugestões</span><h2>${active.length} ativa${active.length === 1 ? '' : 's'} • ${ready.length} pronta${ready.length === 1 ? '' : 's'}</h2><p>Fila persistente para revisão humana. Nada aqui escreve sozinho.</p></div>
      <button class="secondary-action" data-action="select-ready" type="button" ${ready.length ? '' : 'disabled'}>Selecionar prontas</button>
    </div>
    <div class="suggestion-list">
      ${items.map((item) => suggestionItem(item)).join('') || '<div class="empty-state"><div><strong>Sem sugestões</strong>Continue a coleta normal.</div></div>'}
    </div>
  </section>`;
  container.querySelector('[data-action="select-ready"]')?.addEventListener('click', () => actions.onSelectReady?.(ready));
  container.querySelectorAll('[data-suggestion-id]').forEach((button) => {
    button.addEventListener('click', () => actions.onOpen?.(items.find((item) => item.id === button.dataset.suggestionId)));
  });
}

function suggestionItem(item) {
  const confidence = Number.isFinite(Number(item.confidence)) ? `${Math.round(Number(item.confidence) * 100)}%` : '—';
  const actionLabel = item.target === 'CURVE_K' ? 'Abrir na Curva K' : 'Abrir no Mapa K';
  return `<article class="suggestion-item ${lifecycleTone(item.lifecycle)}">
    <div class="suggestion-copy">
      <div class="suggestion-meta"><span class="state-chip">${escapeText(item.lifecycle)}</span><span>${escapeText(item.targetLabel)}</span><span>confiança ${confidence}</span></div>
      <strong>${escapeText(item.reason || 'Sem justificativa')}</strong>
      <p>${escapeText(item.whatIsMissing || '')}</p>
      <small>ID ${escapeText(item.id)}</small>
    </div>
    <button class="secondary-action" type="button" data-suggestion-id="${escapeText(item.id)}" ${item.actionable ? '' : 'disabled'}>${actionLabel}</button>
  </article>`;
}
