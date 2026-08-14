import { UI_STATE } from '../core/store.js';

export function escapeText(value) {
  return String(value ?? '—')
    .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;').replaceAll("'", '&#039;');
}

export function format(value, digits = 1) {
  const number = Number(value);
  return Number.isFinite(number) ? number.toFixed(digits) : '—';
}

export function humanFuel(raw) {
  const fuel = String(raw || '').toUpperCase();
  if (fuel === 'CNG' || fuel === 'GNV') return 'GNV';
  if (fuel === 'PETROL' || fuel === 'GASOLINA') return 'Gasolina';
  return '—';
}

export function humanDirection(direction) {
  switch (direction) {
    case 'INCREASE_CNG_DELIVERY': return 'GNV entregando menos que a referência';
    case 'DECREASE_CNG_DELIVERY': return 'GNV entregando mais que a referência';
    case 'EQUIVALENT': return 'Gasolina e GNV equivalentes nesta condição';
    default: return 'Sem conclusão ainda';
  }
}

export function humanLearningState(state) {
  switch (state) {
    case 'CONSOLIDATED': return 'Consolidado';
    case 'REVALIDATING': return 'Revalidando';
    case 'OBSERVED': return 'Formando evidência';
    case UI_STATE.STALE: return 'Dados antigos';
    default: return 'Aguardando condição';
  }
}

export function semanticItem(kicker, item, className = '') {
  const value = item?.value == null ? '—' : `${format(item.value, 2)} ${escapeText(item.unit || '')}`;
  return `<article class="semantic-item ${className}">
    <span class="semantic-role">${escapeText(kicker)} • ${escapeText(item?.state || 'INDISPONÍVEL')}</span>
    <strong>${value}</strong>
    <p><b>${escapeText(item?.label || 'Sem dado')}</b><br>${escapeText(item?.explanation || '')}</p>
  </article>`;
}

export function setText(id, value) {
  const node = document.getElementById(id);
  if (node) node.textContent = value;
}
