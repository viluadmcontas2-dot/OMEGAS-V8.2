import { store, UI_STATE } from './core/store.js';
import { router, MAIN_ROUTES } from './core/router.js';
import { scheduler } from './core/scheduler.js';
import { simulatedAdapter } from './adapters/simulated.js';
import { simulatedMapKAdapter } from './adapters/simulated-map.js';
import { simulatedPredictorAdapter } from './adapters/simulated-predictor.js';
import { agoraRoute } from './routes/agora.js';
import { aprenderRoute } from './routes/aprender.js';
import { mapaKRoute } from './routes/mapa-k.js';
import { predictorRoute } from './routes/predictor.js';
import { escapeText, humanFuel } from './routes/common.js';

const workspace = document.getElementById('workspace');
const nav = document.getElementById('main-nav');
const sheet = document.getElementById('context-sheet');
const settingsButton = document.getElementById('settings-button');
const adapters = Object.freeze({ live: simulatedAdapter, mapK: simulatedMapKAdapter, predictor: simulatedPredictorAdapter });
const routes = new Map([
  ['agora', agoraRoute],
  ['aprender', aprenderRoute],
  ['predictor', predictorRoute],
  ['mapa-k', mapaKRoute],
]);
let mountedRoute = null;
let fastPollBusy = false;
let learningPollBusy = false;

const ctx = Object.freeze({
  workspace, sheet, store, router, scheduler,
  loadCellContext,
  loadPredictor,
  readMapK,
  toggleLearningMapEditor,
  mapEditorState,
  mapEditorActions,
});

function renderNav(route) {
  nav.replaceChildren(...MAIN_ROUTES.map((item) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'nav-button';
    button.textContent = item.label;
    button.dataset.route = item.id;
    if (item.id === route) button.setAttribute('aria-current', 'page');
    button.addEventListener('click', () => router.navigate(item.id));
    return button;
  }));
}

function renderState(state) {
  updateShell(state);
  const route = routes.get(state.route);
  if (mountedRoute !== state.route) {
    mountedRoute = state.route;
    if (route) route.mount(ctx, state); else mountPlanned(state.route);
  } else if (route) {
    route.update(ctx, state);
  }
}

function mountPlanned(route) {
  const info = MAIN_ROUTES.find((item) => item.id === route);
  const descriptions = {
    'curva-k': 'Ajuste global por Petrol Inj., com AutoCal e comparação na mesma autoridade.',
    obd: 'Segunda testemunha observacional. Nunca escreve K nem substitui a referência de gasolina.',
  };
  workspace.innerHTML = `<section class="route-page" data-route="${escapeText(route)}">
    <div class="route-heading"><div><h1>${escapeText(info?.label || route)}</h1><p>${escapeText(descriptions[route] || '')}</p></div></div>
    <div class="empty-state"><div><strong>Superfície NEXT em construção</strong>Será ligada ao mesmo Store, Router e Scheduler; nenhum subsistema próprio será criado.</div></div></section>`;
}

function updateShell(state) {
  const t = state.telemetry || {};
  const online = t.valid === true;
  const stale = online && Number(t.ageMs) > 1500;
  const dot = document.getElementById('ecu-dot');
  dot.dataset.status = stale ? 'stale' : online ? 'online' : 'offline';
  document.getElementById('ecu-status').textContent = stale ? 'ECU com dado antigo' : online ? 'ECU online' : 'ECU offline';
  document.getElementById('fuel-status').textContent = online ? humanFuel(t.fuel) : '—';
  document.getElementById('freshness-status').textContent = freshnessLabel(t);
}

function freshnessLabel(t) {
  if (!t?.valid) return 'sem telemetria';
  const age = Number(t.ageMs);
  if (!Number.isFinite(age) || age < 0) return 'frescor desconhecido';
  if (age <= 500) return 'ao vivo';
  if (age <= 1500) return `${Math.round(age)} ms`;
  return `desatualizado • ${Math.round(age / 100) / 10}s`;
}

async function pollFastTelemetry() {
  if (fastPollBusy) return;
  fastPollBusy = true;
  try {
    store.dispatch({ type: 'TELEMETRY_UPDATED', payload: await adapters.live.fastTelemetry() });
  } catch (error) {
    store.dispatch({ type: 'TELEMETRY_INVALIDATED', reason: error?.message || 'Falha ao obter telemetria' });
  } finally { fastPollBusy = false; }
}

async function pollLearning() {
  if (learningPollBusy) return;
  learningPollBusy = true;
  try {
    store.dispatch({ type: 'LEARNING_UPDATED', payload: await adapters.live.learningStatus() });
  } catch (error) {
    store.dispatch({ type: 'LEARNING_UPDATED', payload: { state: UI_STATE.UNAVAILABLE, reason: error?.message || 'Learning indisponível' } });
  } finally { learningPollBusy = false; }
}

async function loadCellContext() {
  try {
    store.dispatch({ type: 'CELL_CONTEXT_UPDATED', payload: await adapters.live.cellContext() });
  } catch (_) {
    store.dispatch({ type: 'CELL_CONTEXT_UPDATED', payload: null });
  }
}

async function loadPredictor() {
  store.dispatch({ type: 'PREDICTOR_STATE', payload: { state: UI_STATE.BUSY } });
  try {
    const payload = await adapters.predictor.snapshot();
    store.dispatch({ type: 'PREDICTOR_STATE', payload: { ...payload, state: UI_STATE.READY } });
  } catch (error) {
    store.dispatch({ type: 'PREDICTOR_STATE', payload: { state: UI_STATE.FAILURE, error: error?.message || 'Predictor indisponível' } });
  }
}

async function readMapK() {
  store.dispatch({ type: 'MAP_K_STATE', payload: { state: UI_STATE.BUSY, selection: [], proposal: null } });
  try {
    store.dispatch({ type: 'MAP_K_STATE', payload: await adapters.mapK.readMap() });
  } catch (error) {
    store.dispatch({ type: 'MAP_K_STATE', payload: { state: UI_STATE.FAILURE, error: error?.message || 'Falha ao ler Mapa K', selection: [], proposal: null } });
  }
}

async function toggleLearningMapEditor() {
  const current = store.get().contextualEditor;
  const open = !(current?.open && current?.kind === 'MAP_K');
  store.dispatch({ type: 'CONTEXT_EDITOR_CHANGED', payload: { kind: open ? 'MAP_K' : null, open, originRoute: open ? 'aprender' : null } });
  if (open && store.get().mapK.state !== UI_STATE.READY) await readMapK();
}

function mapEditorState(state) {
  return { ...state.mapK, currentCell: state.cellContext?.cell || null };
}

function mapEditorActions() {
  return Object.freeze({
    onRead: readMapK,
    onToggleCell: toggleMapCell,
    onClearSelection: () => store.dispatch({ type: 'MAP_K_STATE', payload: { selection: [], proposal: null } }),
    onNudge: previewMapDelta,
    onReview: reviewMapProposal,
  });
}

function toggleMapCell(cell) {
  const mapK = store.get().mapK;
  if (mapK.state !== UI_STATE.READY) return;
  const key = `${cell.row}:${cell.column}`;
  const current = mapK.selection || [];
  const exists = current.some((item) => `${item.row}:${item.column}` === key);
  const next = exists ? current.filter((item) => `${item.row}:${item.column}` !== key) : [...current, cell];
  if (next.length > 144) return;
  store.dispatch({ type: 'MAP_K_STATE', payload: { selection: next, proposal: null } });
}

async function previewMapDelta(delta) {
  const selection = store.get().mapK.selection || [];
  if (!selection.length) return;
  const proposal = await adapters.mapK.preview(selection, delta);
  store.dispatch({ type: 'MAP_K_STATE', payload: { proposal } });
}

function reviewMapProposal() {
  const proposal = store.get().mapK.proposal;
  if (!proposal) return;
  sheet.hidden = false;
  const rows = (proposal.changes || []).slice(0, 24).map((c) => `<tr><td>${c.row + 1}:${c.column + 1}</td><td>${c.before}</td><td>→</td><td>${c.after}</td></tr>`).join('');
  sheet.innerHTML = `<div class="route-heading"><div><h1>Revisar Mapa K</h1><p>Nenhuma escrita foi enviada.</p></div><button class="icon-action" id="close-sheet" type="button">×</button></div>
    <table class="review-table"><tbody>${rows}</tbody></table><p class="learning-reason">Simulador: confirmação física permanece indisponível até a fachada nativa comprovar checkpoint, ACK e readback.</p>
    <button class="primary-action" type="button" disabled>Confirmar escrita — indisponível no simulador</button>`;
  document.getElementById('close-sheet')?.addEventListener('click', closeSheet);
}

function openSettings() {
  sheet.hidden = false;
  sheet.innerHTML = `<div class="route-heading"><div><h1>Mais</h1><p>Preferências visuais e detalhes técnicos sob demanda.</p></div><button class="icon-action" id="close-sheet" type="button">×</button></div>
    <p class="learning-reason">Acompanhamento visual não altera Learning, Predictor ou ECU. Logs avançados ficam aqui sem virar um sétimo destino.</p>`;
  document.getElementById('close-sheet')?.addEventListener('click', closeSheet);
}

function closeSheet() { sheet.hidden = true; sheet.innerHTML = ''; }

settingsButton.addEventListener('click', openSettings);
router.subscribe((snapshot) => renderNav(snapshot.route));
store.subscribe(renderState);
scheduler.addHook('fast-telemetry', pollFastTelemetry, 100);
scheduler.addHook('learning-status', pollLearning, 650);
scheduler.start();
loadCellContext();
