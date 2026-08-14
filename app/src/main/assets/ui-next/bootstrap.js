import { store, UI_STATE } from './core/store.js';
import { router, MAIN_ROUTES } from './core/router.js';
import { scheduler } from './core/scheduler.js';
import { nextAdapter } from './adapters/index.js';
import { CAPABILITY } from './adapters/next-contract.js';
import { agoraRoute } from './routes/agora.js';
import { aprenderRoute } from './routes/aprender.js';
import { mapaKRoute } from './routes/mapa-k.js';
import { predictorRoute } from './routes/predictor.js';
import { curvaKRoute } from './routes/curva-k.js';
import { obdRoute } from './routes/obd.js';
import { escapeText, humanFuel } from './routes/common.js';

const workspace = document.getElementById('workspace');
const nav = document.getElementById('main-nav');
const sheet = document.getElementById('context-sheet');
const settingsButton = document.getElementById('settings-button');
const adapterIdentity = nextAdapter.identity();
const adapterCapabilities = nextAdapter.capabilities();
const routes = new Map([
  ['agora', agoraRoute],
  ['aprender', aprenderRoute],
  ['predictor', predictorRoute],
  ['mapa-k', mapaKRoute],
  ['curva-k', curvaKRoute],
  ['obd', obdRoute],
]);
let mountedRoute = null;
let fastPollBusy = false;
let learningPollBusy = false;
let obdPollBusy = false;
let suggestionPollBusy = false;

const ctx = Object.freeze({
  workspace,
  sheet,
  store,
  router,
  scheduler,
  adapterIdentity,
  adapterCapabilities,
  loadCellContext,
  loadPredictor,
  loadObd,
  loadSuggestions,
  selectReadySuggestions,
  openSuggestion,
  readMapK,
  toggleLearningMapEditor,
  mapEditorState,
  mapEditorActions,
  readCurve,
  setCurvePerspective,
  prepareCurvePoint,
  reviewCurve,
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
  workspace.innerHTML = `<section class="route-page" data-route="${escapeText(route)}">
    <div class="route-heading"><div><h1>${escapeText(info?.label || route)}</h1><p>Ligada ao mesmo Store, Router, Scheduler e NextAdapter.</p></div></div>
    <div class="empty-state"><div><strong>Superfície NEXT em construção</strong>Função ausente fica explícita; nenhuma ponte paralela é criada.</div></div>
  </section>`;
}

function updateShell(state) {
  const telemetry = state.telemetry || {};
  const online = telemetry.valid === true;
  const stale = online && Number(telemetry.ageMs) > 1500;
  const dot = document.getElementById('ecu-dot');
  dot.dataset.status = stale ? 'stale' : online ? 'online' : 'offline';
  document.getElementById('ecu-status').textContent = adapterIdentity.dataFictional
    ? 'SIMULADO • sem ECU real'
    : stale ? 'ECU com dado antigo' : online ? 'ECU online' : 'ECU offline';
  document.getElementById('fuel-status').textContent = online ? humanFuel(telemetry.fuel) : '—';
  document.getElementById('freshness-status').textContent = adapterIdentity.dataFictional
    ? 'dados fictícios'
    : freshnessLabel(telemetry);
}

function freshnessLabel(telemetry) {
  if (!telemetry?.valid) return 'sem telemetria';
  const age = Number(telemetry.ageMs);
  if (!Number.isFinite(age) || age < 0) return 'frescor desconhecido';
  if (age <= 500) return 'ao vivo';
  if (age <= 1500) return `${Math.round(age)} ms`;
  return `desatualizado • ${Math.round(age / 100) / 10}s`;
}

async function pollFastTelemetry() {
  if (fastPollBusy || !available(CAPABILITY.FAST_TELEMETRY)) return;
  fastPollBusy = true;
  try {
    store.dispatch({ type: 'TELEMETRY_UPDATED', payload: await nextAdapter.fastTelemetry() });
  } catch (error) {
    store.dispatch({ type: 'TELEMETRY_INVALIDATED', reason: humanError(error, 'Falha ao obter telemetria') });
  } finally {
    fastPollBusy = false;
  }
}

async function pollLearning() {
  if (learningPollBusy || !available(CAPABILITY.LEARNING_STATUS)) return;
  learningPollBusy = true;
  try {
    store.dispatch({ type: 'LEARNING_UPDATED', payload: await nextAdapter.learningStatus() });
  } catch (error) {
    store.dispatch({ type: 'LEARNING_UPDATED', payload: { state: UI_STATE.UNAVAILABLE, reason: humanError(error, 'Learning indisponível') } });
  } finally {
    learningPollBusy = false;
  }
}

async function loadObd() {
  if (obdPollBusy) return;
  if (!available(CAPABILITY.OBD_WITNESS)) {
    store.dispatch({ type: 'OBD_STATE', payload: unavailableState(CAPABILITY.OBD_WITNESS) });
    return;
  }
  obdPollBusy = true;
  try {
    store.dispatch({ type: 'OBD_STATE', payload: await nextAdapter.obdSnapshot() });
  } catch (error) {
    store.dispatch({ type: 'OBD_STATE', payload: { state: 'ERRO', observationalOnly: true, error: humanError(error, 'OBD indisponível') } });
  } finally {
    obdPollBusy = false;
  }
}

async function loadSuggestions() {
  if (suggestionPollBusy) return;
  if (!available(CAPABILITY.SUGGESTIONS)) {
    store.dispatch({ type: 'SUGGESTIONS_STATE', payload: unavailableState(CAPABILITY.SUGGESTIONS) });
    return;
  }
  suggestionPollBusy = true;
  try {
    const payload = await nextAdapter.suggestionsSnapshot();
    store.dispatch({ type: 'SUGGESTIONS_STATE', payload: { ...payload, state: UI_STATE.READY } });
  } catch (error) {
    store.dispatch({ type: 'SUGGESTIONS_STATE', payload: { state: UI_STATE.FAILURE, error: humanError(error, 'Sugestões indisponíveis'), items: [] } });
  } finally {
    suggestionPollBusy = false;
  }
}

async function loadCellContext() {
  if (!available(CAPABILITY.CELL_SEMANTICS)) {
    store.dispatch({ type: 'CELL_CONTEXT_UPDATED', payload: null });
    return;
  }
  try {
    store.dispatch({ type: 'CELL_CONTEXT_UPDATED', payload: await nextAdapter.cellContext() });
  } catch (_) {
    store.dispatch({ type: 'CELL_CONTEXT_UPDATED', payload: null });
  }
}

async function loadPredictor() {
  if (!available(CAPABILITY.PREDICTOR)) {
    store.dispatch({ type: 'PREDICTOR_STATE', payload: unavailableState(CAPABILITY.PREDICTOR) });
    return;
  }
  store.dispatch({ type: 'PREDICTOR_STATE', payload: { state: UI_STATE.BUSY } });
  try {
    const payload = await nextAdapter.predictorSnapshot();
    store.dispatch({ type: 'PREDICTOR_STATE', payload: { ...payload, state: UI_STATE.READY } });
  } catch (error) {
    store.dispatch({ type: 'PREDICTOR_STATE', payload: { state: UI_STATE.FAILURE, error: humanError(error, 'Predictor indisponível') } });
  }
}

async function readMapK() {
  if (!available(CAPABILITY.MAP_READ)) {
    store.dispatch({ type: 'MAP_K_STATE', payload: unavailableState(CAPABILITY.MAP_READ) });
    return;
  }
  store.dispatch({ type: 'MAP_K_STATE', payload: { state: UI_STATE.BUSY } });
  try {
    store.dispatch({ type: 'MAP_K_STATE', payload: await nextAdapter.readMapK() });
  } catch (error) {
    store.dispatch({ type: 'MAP_K_STATE', payload: { state: UI_STATE.FAILURE, error: humanError(error, 'Falha ao ler Mapa K') } });
  }
}

async function toggleLearningMapEditor() {
  const current = store.get().contextualEditor;
  const open = !(current?.open && current?.kind === 'MAP_K');
  store.dispatch({ type: 'CONTEXT_EDITOR_CHANGED', payload: { kind: open ? 'MAP_K' : null, open, originRoute: open ? 'aprender' : null } });
  if (open && store.get().mapK.state !== UI_STATE.READY) await readMapK();
}

function mapEditorState(state) {
  return {
    ...state.mapK,
    currentCell: state.cellContext?.cell || null,
    liveTrace: state.telemetry?.liveTrace || null,
    liveTracingEnabled: state.visual?.liveTracing !== false,
  };
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
  if (mapK.state !== UI_STATE.READY || mapK.draftBlocked) return;
  const key = `${cell.row}:${cell.column}`;
  const current = mapK.selection || [];
  const exists = current.some((item) => `${item.row}:${item.column}` === key);
  const next = exists ? current.filter((item) => `${item.row}:${item.column}` !== key) : [...current, cell];
  if (next.length > 144) return;
  store.dispatch({ type: 'MAP_K_STATE', payload: { selection: next, proposal: null } });
}

async function previewMapDelta(delta) {
  const selection = store.get().mapK.selection || [];
  if (!selection.length || !available(CAPABILITY.MAP_PREVIEW)) return;
  try {
    const proposal = await nextAdapter.previewMapK(selection, delta);
    store.dispatch({ type: 'MAP_K_STATE', payload: { proposal } });
  } catch (error) {
    store.dispatch({ type: 'GLOBAL_ERROR', payload: { message: humanError(error, 'Não foi possível preparar o Mapa K.'), recoverable: true } });
  }
}

function reviewMapProposal() {
  const proposal = store.get().mapK.proposal;
  if (!proposal) return;
  sheet.hidden = false;
  const rows = (proposal.changes || []).slice(0, 24).map((change) =>
    `<tr><td>${change.row + 1}:${change.column + 1}</td><td>${change.before}</td><td>→</td><td>${change.after}</td></tr>`,
  ).join('');
  const writeStatus = adapterCapabilities[CAPABILITY.MAP_WRITE];
  sheet.innerHTML = `<div class="route-heading"><div><h1>Revisar Mapa K</h1><p>Antes/depois da mesma intenção humana. Revisar não grava.</p></div><button class="icon-action" id="close-sheet" type="button">×</button></div>
    <table class="review-table"><tbody>${rows}</tbody></table>
    <p class="learning-reason">${escapeText(writeStatus?.available ? 'A escrita real exige confirmação crítica separada.' : writeStatus?.reason || 'Writer indisponível.')}</p>
    <button class="primary-action" type="button" disabled title="${escapeText(writeStatus?.reason || 'Gate de writer não liberado')}">Gravar na ECU — bloqueado neste gate</button>`;
  document.getElementById('close-sheet')?.addEventListener('click', closeSheet);
}

async function readCurve() {
  const perspective = store.get().curveK.perspective || 'adjust';
  if (!available(CAPABILITY.CURVE_READ)) {
    store.dispatch({ type: 'CURVE_K_STATE', payload: { ...unavailableState(CAPABILITY.CURVE_READ), perspective } });
    return;
  }
  store.dispatch({ type: 'CURVE_K_STATE', payload: { state: UI_STATE.BUSY, perspective } });
  const curveResult = await settled(() => nextAdapter.readCurveK());
  if (!curveResult.ok) {
    store.dispatch({ type: 'CURVE_K_STATE', payload: { state: UI_STATE.FAILURE, perspective, error: humanError(curveResult.error, 'Curva K indisponível') } });
    return;
  }
  const [autocalResult, comparisonResult] = await Promise.all([
    settled(() => available(CAPABILITY.AUTOCAL_STATUS) ? nextAdapter.autoCalStatus() : Promise.reject(unavailableState(CAPABILITY.AUTOCAL_STATUS))),
    settled(() => typeof nextAdapter.curveComparison === 'function' ? nextAdapter.curveComparison() : Promise.resolve({ state: UI_STATE.UNAVAILABLE })),
  ]);
  store.dispatch({ type: 'CURVE_K_STATE', payload: {
    ...curveResult.value,
    perspective,
    comparison: comparisonResult.ok ? comparisonResult.value : { state: UI_STATE.UNAVAILABLE, error: humanError(comparisonResult.error, 'Comparação indisponível') },
  } });
  store.dispatch({ type: 'AUTOCAL_STATE', payload: autocalResult.ok
    ? autocalResult.value
    : { state: UI_STATE.UNAVAILABLE, error: humanError(autocalResult.error, capabilityReason(CAPABILITY.AUTOCAL_STATUS)) } });
}

function setCurvePerspective(perspective) {
  if (!['adjust', 'autocal', 'compare'].includes(perspective)) return;
  store.dispatch({ type: 'CURVE_K_STATE', payload: { perspective } });
}

async function prepareCurvePoint(index, delta) {
  const curve = store.get().curveK;
  if (curve.state !== UI_STATE.READY || curve.draftBlocked || !available(CAPABILITY.CURVE_PREVIEW)) return;
  try {
    const proposal = await nextAdapter.previewCurveK(index, delta);
    const prepared = (curve.prepared || []).filter((item) => item.index !== index);
    store.dispatch({ type: 'CURVE_K_STATE', payload: { prepared: [...prepared, proposal] } });
  } catch (error) {
    store.dispatch({ type: 'GLOBAL_ERROR', payload: { message: humanError(error, 'Não foi possível preparar a Curva K.'), recoverable: true } });
  }
}

function reviewCurve() {
  const prepared = store.get().curveK.prepared || [];
  if (!prepared.length) return;
  sheet.hidden = false;
  const rows = prepared.map((point) =>
    `<tr><td>${point.index + 1}</td><td>${Number(point.petrolMs).toFixed(2)} ms</td><td>${Number(point.before).toFixed(3)}</td><td>→</td><td>${Number(point.after).toFixed(3)}</td></tr>`,
  ).join('');
  const writeStatus = adapterCapabilities[CAPABILITY.CURVE_WRITE];
  sheet.innerHTML = `<div class="route-heading"><div><h1>Revisar Curva K</h1><p>${prepared.length} ponto(s) preparados. Revisar não grava.</p></div><button class="icon-action" id="close-sheet" type="button">×</button></div>
    <table class="review-table"><thead><tr><th>Ponto</th><th>Petrol Inj.</th><th>Atual</th><th></th><th>Proposta</th></tr></thead><tbody>${rows}</tbody></table>
    <p class="learning-reason">${escapeText(writeStatus?.reason || 'Writer real ainda não liberado nesta fachada.')}</p>
    <button class="primary-action" type="button" disabled title="${escapeText(writeStatus?.reason || 'Gate de writer não liberado')}">Gravar na ECU — bloqueado neste gate</button>`;
  document.getElementById('close-sheet')?.addEventListener('click', closeSheet);
}

async function selectReadySuggestions(items) {
  const ready = (items || []).filter((item) => item?.actionable);
  const local = ready.filter((item) => item.target === 'MAP_K').flatMap((item) => item.mapChanges || []);
  const global = ready.filter((item) => item.target === 'CURVE_K').flatMap((item) => item.curveChanges || []);

  if (local.length) {
    if (store.get().mapK.state !== UI_STATE.READY) await readMapK();
    const unique = [...new Map(local.map((change) => [`${change.row}:${change.column}`, change])).values()];
    store.dispatch({ type: 'MAP_K_STATE', payload: {
      selection: unique.map((change) => ({ row: change.row, column: change.column })),
      proposal: {
        summary: `${unique.length} célula(s) selecionada(s) da fila • nenhuma escrita`,
        changes: unique,
        source: 'SUGGESTION_QUEUE',
        automaticWrite: false,
        humanConfirmationRequired: true,
      },
    } });
  }

  if (global.length) {
    if (store.get().curveK.state !== UI_STATE.READY) await readCurve();
    store.dispatch({ type: 'CURVE_K_STATE', payload: { perspective: 'adjust', prepared: curvePreparedFromChanges(global) } });
  }
}

async function openSuggestion(item) {
  if (!item?.actionable) return;
  if (item.target === 'MAP_K') {
    if (store.get().mapK.state !== UI_STATE.READY) await readMapK();
    const changes = item.mapChanges || [];
    store.dispatch({ type: 'MAP_K_STATE', payload: {
      selection: changes.map((change) => ({ row: change.row, column: change.column })),
      proposal: {
        summary: `Sugestão ${item.id} aberta para revisão • nenhuma escrita`,
        changes,
        source: item.id,
        automaticWrite: false,
        humanConfirmationRequired: true,
      },
    } });
    router.navigate('mapa-k');
    return;
  }
  if (item.target === 'CURVE_K') {
    if (store.get().curveK.state !== UI_STATE.READY) await readCurve();
    store.dispatch({ type: 'CURVE_K_STATE', payload: { perspective: 'adjust', prepared: curvePreparedFromChanges(item.curveChanges || []) } });
    router.navigate('curva-k');
  }
}

function curvePreparedFromChanges(changes) {
  const points = store.get().curveK.points || [];
  return (changes || []).map((change) => ({
    index: change.index,
    petrolMs: points.find((point) => point.index === change.index)?.petrolMs ?? 0,
    before: change.before,
    after: change.after,
    source: 'SUGGESTION_QUEUE',
    automaticWrite: false,
    humanConfirmationRequired: true,
  }));
}

function openSettings() {
  sheet.hidden = false;
  const unavailable = Object.entries(adapterCapabilities)
    .filter(([, value]) => value.available !== true)
    .map(([key, value]) => `<li><strong>${escapeText(key)}</strong> — ${escapeText(value.reason)}</li>`)
    .join('');
  sheet.innerHTML = `<div class="route-heading"><div><h1>Mais</h1><p>Preferências visuais e diagnóstico da superfície atual.</p></div><button class="icon-action" id="close-sheet" type="button">×</button></div>
    <p class="learning-reason"><strong>Ambiente:</strong> ${escapeText(adapterIdentity.mode)} • fonte ${escapeText(adapterIdentity.source)}${adapterIdentity.dataFictional ? ' • DADOS FICTÍCIOS' : ''}</p>
    ${unavailable ? `<details class="help-details"><summary>Funções ainda indisponíveis</summary><ul>${unavailable}</ul></details>` : ''}
    <p class="learning-reason">Flutuante, live tracing e detalhes avançados vivem aqui sem virar novos destinos principais e sem alterar a ciência.</p>`;
  document.getElementById('close-sheet')?.addEventListener('click', closeSheet);
}

function closeSheet() {
  sheet.hidden = true;
  sheet.innerHTML = '';
}

function available(capability) {
  return adapterCapabilities?.[capability]?.available === true;
}

function capabilityReason(capability) {
  return adapterCapabilities?.[capability]?.reason || 'Função indisponível neste ambiente.';
}

function unavailableState(capability) {
  return Object.freeze({ state: UI_STATE.UNAVAILABLE, error: capabilityReason(capability), reason: capabilityReason(capability), capability });
}

function humanError(error, fallback) {
  return String(error?.message || error?.error || error?.reason || fallback || 'Não foi possível concluir a operação.');
}

async function settled(factory) {
  try {
    return { ok: true, value: await factory() };
  } catch (error) {
    return { ok: false, error };
  }
}

settingsButton.addEventListener('click', openSettings);
router.subscribe((snapshot) => renderNav(snapshot.route));
store.subscribe(renderState);
scheduler.addHook('fast-telemetry', pollFastTelemetry, 100);
scheduler.addHook('learning-status', pollLearning, 650);
scheduler.addHook('obd-witness', loadObd, 1000);
scheduler.addHook('suggestions', loadSuggestions, 2000);
scheduler.start();
loadCellContext();
loadObd();
loadSuggestions();
