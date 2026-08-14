import { store } from './core/store.js';
import { scheduler } from './core/scheduler.js';
import { updateMapKLiveTrace } from './components/map-k-editor.js';

let lastPaintAt = 0;

function paintLiveTracing(time) {
  const state = store.get();
  const enabled = state.visual?.liveTracing !== false && document.visibilityState === 'visible';
  const reduced = scheduler.pressure() === 'REDUCED' || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true;
  const minInterval = reduced ? 250 : 100;
  if (enabled && time - lastPaintAt < minInterval) return;
  lastPaintAt = time;

  const trace = state.telemetry?.liveTrace || null;
  const containers = [
    document.getElementById('map-k-root'),
    document.getElementById('learning-map-editor'),
  ].filter(Boolean);
  for (const container of containers) updateMapKLiveTrace(container, trace, enabled);
}

scheduler.addHook('live-tracing', paintLiveTracing, 50);

/* Extensão do painel Mais: o toggle altera somente Store.visual. */
document.addEventListener('click', (event) => {
  if (event.target?.id !== 'settings-button') return;
  const sheet = document.getElementById('context-sheet');
  if (!sheet || sheet.querySelector('[data-live-tracing-control]')) return;
  const state = store.get();
  const section = document.createElement('section');
  section.className = 'learning-now';
  section.dataset.liveTracingControl = 'true';
  section.innerHTML = `<span class="section-kicker">Live tracing</span>
    <strong class="learning-state">${state.visual?.liveTracing !== false ? 'Ligado' : 'Desligado'}</strong>
    <p class="learning-reason">Mostra somente a posição/pesos atuais calculados pelo Kotlin. Não altera Learning, Predictor ou ECU.</p>
    <button class="secondary-action" type="button" data-toggle-live-tracing>${state.visual?.liveTracing !== false ? 'Desligar tracing' : 'Ligar tracing'}</button>`;
  sheet.appendChild(section);
  section.querySelector('[data-toggle-live-tracing]')?.addEventListener('click', () => {
    const next = store.get().visual?.liveTracing === false;
    store.dispatch({ type: 'VISUAL_PREFERENCE_CHANGED', payload: { liveTracing: next } });
    section.querySelector('.learning-state').textContent = next ? 'Ligado' : 'Desligado';
    section.querySelector('[data-toggle-live-tracing]').textContent = next ? 'Desligar tracing' : 'Ligar tracing';
  });
});
