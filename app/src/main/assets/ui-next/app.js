import { store, UI_STATE } from './core/store.js';
import { router, MAIN_ROUTES } from './core/router.js';
import { scheduler } from './core/scheduler.js';
import { simulatedAdapter } from './adapters/simulated.js';
import { simulatedMapKAdapter } from './adapters/simulated-map.js';
import { renderMapKEditor } from './components/map-k-editor.js';

const adapter = simulatedAdapter;
const mapAdapter = simulatedMapKAdapter;
const workspace = document.getElementById('workspace');
const nav = document.getElementById('main-nav');
const sheet = document.getElementById('context-sheet');
const settingsButton = document.getElementById('settings-button');
let renderedRoute = '';
let fastPollBusy = false;
let learningPollBusy = false;

function escapeText(value) {
  return String(value ?? '—')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function format(value, digits = 1) {
  const n = Number(value);
  return Number.isFinite(n) ? n.toFixed(digits) : '—';
}

function freshnessLabel(telemetry) {
  if (!telemetry?.valid) return 'sem telemetria';
  const age = Number(telemetry.ageMs);
  if (!Number.isFinite(age) || age < 0) return 'frescor desconhecido';
  if (age <= 500) return 'ao vivo';
  if (age <= 1500) return `${Math.round(age)} ms`;
  return `desatualizado • ${Math.round(age / 100) / 10}s`;
}

function humanFuel(raw) {
  const fuel = String(raw || '').toUpperCase();
  if (fuel === 'CNG' || fuel === 'GNV') return 'GNV';
  if (fuel === 'PETROL' || fuel === 'GASOLINA') return 'Gasolina';
  return 'Combustível —';
}

function renderNav(route) {
  nav.replaceChildren(...MAIN_ROUTES.map((item) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'nav-button';
    button.dataset.route = item.id;
    button.textContent = item.label;
    if (item.id === route) button.setAttribute('aria-current', 'page');
    button.addEventListener('click', () => router.navigate(item.id));
    return button;
  }));
}

function updateShell(state) {
  const telemetry = state.telemetry || {};
  const online = telemetry.valid === true;
  const stale = online && Number(telemetry.ageMs) > 1500;
  const dot = document.getElementById('ecu-dot');
  dot.dataset.status = stale ? 'stale' : online ? 'online' : 'offline';
  document.getElementById('ecu-status').textContent = stale ? 'ECU com dado antigo' : online ? 'ECU online' : 'ECU offline';
  document.getElementById('fuel-status').textContent = online ? humanFuel(telemetry.fuel) : '—';
  document.getElementById('freshness-status').textContent = freshnessLabel(telemetry);
}

function renderRoute(state) {
  if (renderedRoute === state.route) return;
  renderedRoute = state.route;
  switch (state.route) {
    case 'agora': renderAgora(); break;
    case 'aprender': renderAprender(); break;
    case 'mapa-k': renderMapaK(); break;
    default: renderPlannedRoute(state.route); break;
  }
}

function renderAgora() {
  workspace.innerHTML = `
    <section class="route-page now-page" data-route="agora">
      <div class="route-heading"><div><h1>Agora</h1><p>O que o motor está fazendo neste instante — sem misturar o presente com o que foi aprendido.</p></div></div>
      <div class="now-layout">
        <div class="now-primary">
          <section class="live-cockpit" aria-label="Telemetria atual">
            <div class="metric primary"><span class="metric-label">RPM</span><strong class="metric-value" id="now-rpm">—</strong><span class="metric-origin">MP48 • agora</span></div>
            <div class="metric"><span class="metric-label">Petrol Inj.</span><strong class="metric-value"><span id="now-petrol">—</span><span class="metric-unit">ms</span></strong><span class="metric-origin">comando gasolina • agora</span></div>
            <div class="metric"><span class="metric-label">MAP</span><strong class="metric-value"><span id="now-map">—</span><span class="metric-unit">bar</span></strong><span class="metric-origin">carga do motor</span></div>
            <div class="metric"><span class="metric-label">Gas Inj.</span><strong class="metric-value"><span id="now-gas">—</span><span class="metric-unit">ms</span></strong><span class="metric-origin">diagnóstico • não é referência</span></div>
          </section>
          <div class="context-row">
            <button class="cell-context-button" id="current-cell-button" type="button">
              <span><strong>Região atual</strong><small id="current-cell-copy">Calculando posição física…</small></span>
              <span class="cell-badge" id="current-cell-badge">—</span>
            </button>
          </div>
        </div>
        <aside class="learning-now" aria-label="Aprendendo agora">
          <span class="section-kicker">Aprendendo agora</span>
          <strong class="learning-state" id="learning-state">Aguardando dados</strong>
          <p class="learning-reason" id="learning-reason">O Learning não interfere na telemetria enquanto prepara o contexto.</p>
          <div class="learning-meta"><span class="state-chip" id="learning-source">Learning</span><span class="state-chip" id="learning-fuel">—</span></div>
        </aside>
      </div>
    </section>`;
  document.getElementById('current-cell-button').addEventListener('click', async () => {
    await loadCellContext();
    router.navigate('aprender');
  });
  updateAgora(store.get());
}

function updateAgora(state) {
  if (state.route !== 'agora') return;
  const t = state.telemetry || {};
  setText('now-rpm', t.valid ? Math.round(Number(t.rpm) || 0) : '—');
  setText('now-petrol', t.valid ? format(t.petrolMs, 2) : '—');
  setText('now-map', t.valid ? format(t.mapBar, 2) : '—');
  setText('now-gas', t.valid && t.gasMsDiagnostic != null ? format(t.gasMsDiagnostic, 2) : '—');
  setText('learning-fuel', t.valid ? humanFuel(t.fuel) : '—');
  const learning = state.learning || {};
  setText('learning-state', learning.label || humanLearningState(learning.state));
  setText('learning-reason', learning.reason || 'Ainda não há conclusão científica para esta condição.');
  setText('learning-source', learning.source === 'OBD' ? 'OBD' : 'Comparação gasolina↔GNV');
  const cell = state.cellContext?.cell;
  setText('current-cell-badge', cell ? `${cell.row + 1}:${cell.column + 1}` : '—');
  setText('current-cell-copy', cell
    ? 'Contexto físico RPM × Petrol Inj. • tocar abre Aprender; não seleciona escrita'
    : 'Toque para abrir Aprender quando houver contexto válido');
}

function renderAprender() {
  workspace.innerHTML = `
    <section class="route-page" data-route="aprender">
      <div class="route-heading">
        <div><h1>Aprender</h1><p>O que foi medido, o que é referência equivalente, qual é a diferença e o que vale apenas revisar.</p></div>
        <button class="secondary-action" id="refresh-cell-context" type="button">Atualizar contexto</button>
      </div>
      <div id="learning-context-root" class="empty-state"><div><strong>Preparando contexto</strong>Buscando a explicação nativa da região atual.</div></div>
      <div id="learning-map-editor"></div>
    </section>`;
  document.getElementById('refresh-cell-context').addEventListener('click', loadCellContext);
  if (!store.get().cellContext) loadCellContext();
  updateAprender(store.get());
}

function updateAprender(state) {
  if (state.route !== 'aprender') return;
  const root = document.getElementById('learning-context-root');
  if (!root) return;
  const context = state.cellContext;
  if (!context) {
    root.className = 'empty-state';
    root.innerHTML = '<div><strong>Sem região comparável ainda</strong>Continue a coleta normalmente. A tela não inventa referência, rico/pobre ou sugestão.</div>';
    renderLearningEditor(state);
    return;
  }
  const current = context.currentCondition?.petrolInjection || {};
  const reference = context.gasolineEquivalentReference || {};
  const cng = context.cngObservation || {};
  const comparison = context.comparison || {};
  const calibration = context.calibration || {};
  const suggestionLabel = comparison.comparable ? 'Ajuste local — Mapa K' : 'Sem sugestão local ainda';
  root.className = 'route-page';
  root.innerHTML = `
    <section class="semantic-grid" aria-label="Origem dos valores desta região">
      ${semanticItem('AGORA', current, '')}
      ${semanticItem('REFERÊNCIA', reference, 'reference')}
      ${semanticItem('NO GNV', cng, 'observed')}
    </section>
    <section class="learning-now">
      <span class="section-kicker">Diferença • comparação gasolina↔GNV</span>
      <strong class="learning-state">${comparison.comparable ? humanDirection(comparison.direction) : 'Ainda não comparável'}</strong>
      <p class="learning-reason">${escapeText(comparison.reason || 'A referência equivalente ainda não está disponível com confiança suficiente.')}</p>
      <div class="learning-meta">
        <span class="state-chip">Diferença ${comparison.comparable ? `${format(comparison.differenceMs, 2)} ms` : '—'}</span>
        <span class="state-chip">${comparison.comparable ? `${format(comparison.differencePct, 1)}%` : 'sem %'}</span>
        <span class="state-chip">qualidade ${comparison.quality == null ? '—' : `${Math.round(comparison.quality * 100)}%`}</span>
      </div>
    </section>
    <section class="proposal-banner">
      <div><strong>${escapeText(suggestionLabel)}</strong><span>K atual ${calibration.currentK ?? '—'} • K alvo ${calibration.targetK ?? '—'} • abrir não escreve</span></div>
      <button class="primary-action" id="open-learning-map-editor" type="button" ${comparison.comparable ? '' : 'disabled'}>${state.contextualEditor?.open ? 'Fechar editor' : 'Editar Mapa K aqui'}</button>
    </section>`;
  document.getElementById('open-learning-map-editor')?.addEventListener('click', () => toggleLearningMapEditor());
  renderLearningEditor(state);
}

function renderLearningEditor(state) {
  const target = document.getElementById('learning-map-editor');
  if (!target) return;
  const open = state.contextualEditor?.open && state.contextualEditor?.kind === 'MAP_K';
  target.hidden = !open;
  if (!open) {
    target.innerHTML = '';
    return;
  }
  renderMapKEditor(target, mapEditorState(state), mapEditorActions());
}

async function toggleLearningMapEditor() {
  const current = store.get().contextualEditor;
  const willOpen = !(current?.open && current?.kind === 'MAP_K');
  store.dispatch({ type: 'CONTEXT_EDITOR_CHANGED', payload: { kind: willOpen ? 'MAP_K' : null, open: willOpen, originRoute: willOpen ? 'aprender' : null } });
  if (willOpen && store.get().mapK.state !== UI_STATE.READY) await readMapK();
}

function renderMapaK() {
  workspace.innerHTML = `
    <section class="route-page" data-route="mapa-k">
      <div class="route-heading"><div><h1>Mapa K</h1><p>Ajuste local consciente: ler primeiro, selecionar, preparar, revisar e só então confirmar uma escrita nativa.</p></div></div>
      <div id="map-k-root"></div>
    </section>`;
  if (store.get().mapK.state !== UI_STATE.READY) readMapK();
  updateMapaK(store.get());
}

function updateMapaK(state) {
  if (state.route !== 'mapa-k') return;
  renderMapKEditor(document.getElementById('map-k-root'), mapEditorState(state), mapEditorActions());
}

function mapEditorState(state) {
  return {
    ...state.mapK,
    currentCell: state.cellContext?.cell || null,
  };
}

function mapEditorActions() {
  return {
    onRead: readMapK,
    onToggleCell: toggleMapCell,
    onClearSelection: clearMapSelection,
    onNudge: previewMapDelta,
    onReview: reviewMapProposal,
  };
}

async function readMapK() {
  store.dispatch({ type: 'MAP_K_STATE', payload: { state: UI_STATE.BUSY, selection: [], proposal: null } });
  try {
    const payload = await mapAdapter.readMap();
    store.dispatch({ type: 'MAP_K_STATE', payload });
  } catch (error) {
    store.dispatch({ type: 'MAP_K_STATE', payload: { state: UI_STATE.FAILURE, error: error?.message || 'Falha ao ler Mapa K', selection: [], proposal: null } });
  }
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

function clearMapSelection() {
  store.dispatch({ type: 'MAP_K_STATE', payload: { selection: [], proposal: null } });
}

async function previewMapDelta(delta) {
  const selection = store.get().mapK.selection || [];
  if (!selection.length) return;
  const proposal = await mapAdapter.preview(selection, delta);
  store.dispatch({ type: 'MAP_K_STATE', payload: { proposal } });
}

function reviewMapProposal() {
  const proposal = store.get().mapK.proposal;
  if (!proposal) return;
  sheet.hidden = false;
  const rows = (proposal.changes || []).slice(0, 24).map((change) => `<tr><td>${change.row + 1}:${change.column + 1}</td><td>${change.before}</td><td>→</td><td>${change.after}</td></tr>`).join('');
  sheet.innerHTML = `
    <div class="route-heading"><div><h1>Revisar Mapa K</h1><p>Antes/depois da mesma intenção humana. Nenhuma escrita foi enviada.</p></div><button class="icon-action" id="close-sheet" type="button" aria-label="Fechar">×</button></div>
    <table class="review-table"><thead><tr><th>Célula</th><th>Atual</th><th></th><th>Proposta</th></tr></thead><tbody>${rows}</tbody></table>
    <p class="learning-reason">Ambiente NEXT ainda está no adaptador simulado. A confirmação física só será habilitada quando a fachada nativa usar checkpoint, ACK e readback.</p>
    <button class="primary-action" type="button" disabled>Confirmar escrita — indisponível no simulador</button>`;
  document.getElementById('close-sheet').addEventListener('click', () => { sheet.hidden = true; });
}

function semanticItem(kicker, item, className) {
  const value = item?.value == null ? '—' : `${format(item.value, 2)} ${escapeText(item.unit || '')}`;
  return `<article class="semantic-item ${className}"><span class="semantic-role">${escapeText(kicker)} • ${escapeText(item?.state || 'INDISPONÍVEL')}</span><strong>${value}</strong><p><b>${escapeText(item?.label || 'Sem dado')}</b><br>${escapeText(item?.explanation || '')}</p></article>`;
}

function humanDirection(direction) {
  switch (direction) {
    case 'INCREASE_CNG_DELIVERY': return 'GNV entregando menos que a referência';
    case 'DECREASE_CNG_DELIVERY': return 'GNV entregando mais que a referência';
    case 'EQUIVALENT': return 'Gasolina e GNV equivalentes nesta condição';
    default: return 'Sem conclusão ainda';
  }
}

function humanLearningState(state) {
  switch (state) {
    case 'CONSOLIDATED': return 'Consolidado';
    case 'REVALIDATING': return 'Revalidando';
    case 'OBSERVED': return 'Formando evidência';
    case UI_STATE.STALE: return 'Dados antigos';
    default: return 'Aguardando condição';
  }
}

function renderPlannedRoute(route) {
  const info = MAIN_ROUTES.find((item) => item.id === route);
  const descriptions = {
    predictor: 'O que o aprendizado permite prever, com qual confiança e por quê.',
    'curva-k': 'Ajuste global por Petrol Inj., com AutoCal e comparação na mesma autoridade.',
    obd: 'Segunda testemunha observacional. Nunca escreve K nem substitui a referência de gasolina.',
  };
  workspace.innerHTML = `<section class="route-page" data-route="${escapeText(route)}"><div class="route-heading"><div><h1>${escapeText(info?.label || route)}</h1><p>${escapeText(descriptions[route] || '')}</p></div></div><div class="empty-state"><div><strong>Superfície NEXT em construção</strong>Será conectada ao mesmo Store, Router e Scheduler — sem reaproveitar a tela antiga.</div></div></section>`;
}

function updateCurrentRoute(state) {
  renderRoute(state);
  if (state.route === 'agora') updateAgora(state);
  if (state.route === 'aprender') updateAprender(state);
  if (state.route === 'mapa-k') updateMapaK(state);
}

async function pollFastTelemetry() {
  if (fastPollBusy) return;
  fastPollBusy = true;
  try {
    store.dispatch({ type: 'TELEMETRY_UPDATED', payload: await adapter.fastTelemetry() });
  } catch (error) {
    store.dispatch({ type: 'TELEMETRY_INVALIDATED', reason: error?.message || 'Falha ao obter telemetria' });
  } finally { fastPollBusy = false; }
}

async function pollLearning() {
  if (learningPollBusy) return;
  learningPollBusy = true;
  try {
    store.dispatch({ type: 'LEARNING_UPDATED', payload: await adapter.learningStatus() });
  } catch (error) {
    store.dispatch({ type: 'LEARNING_UPDATED', payload: { state: UI_STATE.UNAVAILABLE, reason: error?.message || 'Learning indisponível' } });
  } finally { learningPollBusy = false; }
}

async function loadCellContext() {
  try {
    const payload = await adapter.cellContext();
    store.dispatch({ type: 'CELL_CONTEXT_UPDATED', payload });
    store.dispatch({ type: 'MAP_K_STATE', payload: { currentCell: payload?.cell || null } });
  } catch (_) {
    store.dispatch({ type: 'CELL_CONTEXT_UPDATED', payload: null });
  }
}

function setText(id, value) {
  const node = document.getElementById(id);
  if (node) node.textContent = value;
}

function openSettings() {
  sheet.hidden = false;
  sheet.innerHTML = `<div class="route-heading"><div><h1>Mais</h1><p>Preferências visuais e detalhes técnicos sob demanda.</p></div><button class="icon-action" id="close-sheet" type="button" aria-label="Fechar">×</button></div><p class="learning-reason">Acompanhamento visual não altera Learning, Predictor ou ECU. Logs e diagnóstico avançado entram aqui sem virar um sétimo destino principal.</p>`;
  document.getElementById('close-sheet').addEventListener('click', () => { sheet.hidden = true; });
}

settingsButton.addEventListener('click', openSettings);
router.subscribe((snapshot) => renderNav(snapshot.route));
store.subscribe((state) => { updateShell(state); updateCurrentRoute(state); });
scheduler.addHook('fast-telemetry', pollFastTelemetry, 100);
scheduler.addHook('learning-status', pollLearning, 650);
scheduler.start();
loadCellContext();
