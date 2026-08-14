import { store } from './core/store.js';
import { nextAdapter } from './adapters/index.js';
import { CAPABILITY } from './adapters/next-contract.js';

let refreshBusy = false;
let lastSequence = -1;

async function refreshFastSnapshot(event) {
  if (refreshBusy) return;
  if (nextAdapter.capabilities()?.[CAPABILITY.FAST_TELEMETRY]?.available !== true) return;
  if (event?.sequence != null && Number(event.sequence) === lastSequence) return;
  lastSequence = Number(event?.sequence ?? lastSequence);
  refreshBusy = true;
  try {
    store.dispatch({ type: 'TELEMETRY_UPDATED', payload: await nextAdapter.fastTelemetry() });
  } catch (error) {
    store.dispatch({
      type: 'TELEMETRY_INVALIDATED',
      reason: String(error?.message || 'Refresh nativo falhou; aguardando próximo evento/fallback.'),
    });
  } finally {
    refreshBusy = false;
  }
}

const unsubscribe = nextAdapter.subscribeRevisions((event) => {
  refreshFastSnapshot(event);
});

addEventListener('pagehide', () => {
  try { unsubscribe?.(); } catch (_) {}
}, { once: true });
