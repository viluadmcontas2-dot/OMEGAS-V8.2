import { store, UI_STATE } from './core/store.js';
import { router, MAIN_ROUTES } from './core/router.js';
import { scheduler } from './core/scheduler.js';
import { simulatedAdapter } from './adapters/simulated.js';

const adapter = simulatedAdapter;
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

function humanFuel(raw) {
  const fuel = String(raw || '').toUpperCase();
  if (fuel === 'CNG' || fuel === 'GNV') return 'GNV';
  if (fuel === 'PETROL' || fuel === 'GASOLINA') return 'Gasolina';
  return 'Combustível —';
}

function renderRoute(state) {
  if (renderedRoute === state.route) return;
  renderedRoute = state.route;
  switch (state.route) {
    case 'agora': renderAgora(); break;
    case 'aprender': renderAprender(); break;
    default: renderPlannedRoute(state.route); break;
  }
}

function renderAgora() {
  workspace.innerHTML = `
    <section class="route-page now-page" data-route="agora">
      <div class="route-heading">
        <div>
          <h1>Agora</h1>
          <p>O que o motor está fazendo neste instante — sem misturar o presente com o que foi aprendido.</p>
        </div>
      </div>
      <div class="now-layout">
        <div class="now-primary">
          <section class="live-cockpit" aria-label="Telemetria atual">
            <div class="metric primary">
              <span class="metric-label">RPM</span>
              <strong class="metric-value" id="now-rpm">—</strong>
              <span class="metric-origin">MP48 • agora</span>
            </div>
            <div class="metric">
              <span class="metric-label">Petrol Inj.</span>
              <strong class="metric-value"><span id="now-petrol">—</span><span class="metric-unit">ms</span></strong>
              <span class="metric-origin">comando gasolina • agora</span>
            </div>
            <div class="metric">
              <span class="metric-label">MAP</span>
              <strong class="metric-value"><span id="now-map">—</span><span class="metric-unit">bar</span></strong>
              <span class="metric-origin">carga do motor</span>
            </div>
            <div class="metric">
              <span class="metric-label">Gas Inj.</span>
              <strong class="metric-value"><span id="now-gas">—</span><span class="metric-unit">ms</span></strong>
              <span class="metric-origin">diagnóstico • não é referência</span>
            </div>
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
          <div class="learning-meta">
            <span class="state-chip" id="learning-source">Learning</span>
            <span class="state-chip" id="learning-fuel">—</span>
          </div>
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
        <div>
          <h1>Aprender</h1>
          <p>Separar o que foi medido, o que é referência equivalente e o que ainda é apenas previsão.</p>
        </div>
        <button class="secondary-action" id="refresh-cell-context" type="button">Atualizar contexto</button>
      </div>
      <div id="learning-context-root" class="empty-state"><div><strong>Preparando contexto</strong>Buscando a explicação nativa da região atual.</div></div>
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
    root.innerHTML = '<div><strong>Sem região comparável ainda</strong>Continue a coleta normalmente. A tela não vai inventar referência ou diagnóstico.</div>';
    return;
  }
  const current = context.currentCondition?.petrolInjection || {};
  const reference = context.gasolineEquivalentReference || {};
  const cng = context.cngObservation || {};
  const comparison = context.comparison || {};
  root.className = 'route-page';
  root.innerHTML = `
    <section class="semantic-grid" aria-label="Origem dos valores desta região">
      ${semanticItem('AGORA', current, '')}
      ${semanticItem('REFERÊNCIA', reference, 'reference')}
      ${semanticItem('NO GNV', cng, 'observed')}
    </section>
    <section class="learning-now">
      <span class="section-kicker">Por que estes números são comparáveis?</span>
      <strong class="learning-state">${comparison.comparable ? humanDirection(comparison.direction) : 'Ainda não comparável'}</strong>
      <p class="learning-reason">${escapeText(comparison.reason || 'A referência equivalente ainda não está disponível com confiança suficiente.')}</p>
      <div class="learning-meta">
        <span class="state-chip">Diferença ${comparison.comparable ? `${format(comparison.differenceMs, 2)} ms` : '—'}</span>
        <span class="state-chip">${comparison.comparable ? `${format(comparison.differencePct, 1)}%` : 'sem %'}</span>
        <span class="state-chip">qualidade ${comparison.quality == null ? '—' : `${Math.round(comparison.quality * 100)}%`}</span>
      </div>
    </section>`;
}

function semanticItem(kicker, item, className) {
  const value = item?.value == null ? '—' : `${format(item.value, 2)} ${escapeText(item.unit || '')}`;
  return `<article class="semantic-item ${className}">
    <span class="semantic-role">${escapeText(kicker)} • ${escapeText(item?.state || 'INDISPONÍVEL')}</span>
    <strong>${value}</strong>
    <p><b>${escapeText(item?.label || 'Sem dado')}</b><br>${escapeText(item?.explanation || '')}</p>
  </article>`;
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
    'mapa-k': 'Ajuste local consciente: ler, selecionar, revisar, confirmar e validar readback.',
    'curva-k': 'Ajuste global por Petrol Inj., com AutoCal e comparação na mesma autoridade.',
    obd: 'Segunda testemunha observacional. Nunca escreve K nem substitui a referência de gasolina.',
  };
  workspace.innerHTML = `<section class="route-page" data-route="${escapeText(route)}">
    <div class="route-heading"><div><h1>${escapeText(info?.label || route)}</h1><p>${escapeText(descriptions[route] || '')}</p></div></div>
    <div class="empty-state"><div><strong>Superfície NEXT em construção</strong>A fundação desta função será conectada ao mesmo Store, Router e Scheduler — sem reaproveitar a tela antiga.</div></div>
  </section>`;
}

function updateCurrentRoute(state) {
  renderRoute(state);
  if (state.route === 'agora') updateAgora(state);
  if (state.route === 'aprender') updateAprender(state);
}

async function pollFastTelemetry() {
  if (fastPollBusy) return;
  fastPollBusy = true;
  try {
    const payload = await adapter.fastTelemetry();
    store.dispatch({ type: 'TELEMETRY_UPDATED', payload });
  } catch (error) {
    store.dispatch({ type: 'TELEMETRY_INVALIDATED', reason: error?.message || 'Falha ao obter telemetria' });
  } finally {
    fastPollBusy = false;
  }
}

async function pollLearning() {
  if (learningPollBusy) return;
  learningPollBusy = true;
  try {
    const payload = await adapter.learningStatus();
    store.dispatch({ type: 'LEARNING_UPDATED', payload });
  } catch (error) {
    store.dispatch({ type: 'LEARNING_UPDATED', payload: { state: UI_STATE.UNAVAILABLE, reason: error?.message || 'Learning indisponível' } });
  } finally {
    learningPollBusy = false;
  }
}

async function loadCellContext() {
  try {
    const payload = await adapter.cellContext();
    store.dispatch({ type: 'CELL_CONTEXT_UPDATED', payload });
  } catch (error) {
    store.dispatch({ type: 'CELL_CONTEXT_UPDATED', payload: null });
  }
}

function setText(id, value) {
  const node = document.getElementById(id);
  if (node) node.textContent = value;
}

function openSettings() {
  sheet.hidden = false;
  sheet.innerHTML = `
    <div class="route-heading"><div><h1>Mais</h1><p>Preferências visuais e detalhes técnicos sob demanda.</p></div><button class="icon-action" id="close-sheet" type="button" aria-label="Fechar">×</button></div>
    <p class="learning-reason">Acompanhamento visual não altera Learning, Predictor ou ECU. Logs e diagnóstico avançado entrarão aqui sem virar um sétimo destino principal.</p>`;
  document.getElementById('close-sheet').addEventListener('click', () => { sheet.hidden = true; });
}

settingsButton.addEventListener('click', openSettings);
router.subscribe((snapshot) => renderNav(snapshot.route));
store.subscribe((state) => {
  updateShell(state);
  updateCurrentRoute(state);
});

scheduler.addHook('fast-telemetry', pollFastTelemetry, 100);
scheduler.addHook('learning-status', pollLearning, 650);
scheduler.start();
loadCellContext();
