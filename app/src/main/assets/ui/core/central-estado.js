(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  /**
   * Estado único da central, igual em todas as telas (spec 5.4). Puro: recebe o estado do store e devolve
   * { online, tone: 'ok'|'warn'|'bad', title, next }. Quem desenha usa estas mesmas palavras.
   */
  const FRESH_MS = 2500;
  function describe(state) {
    const s = (state && state.status) || {};
    const age = Number(s.directTelemetryAgeMs);
    if (s.usbPermissionPending === true) {
      return { online: false, tone: 'warn', title: 'Autorize o cabo USB', next: 'Toque em Permitir na janela que apareceu.' };
    }
    if (s.usbConnected !== true) {
      return { online: false, tone: 'bad', title: 'Sem central', next: 'Conecte o cabo USB e ligue a ignição.' };
    }
    if (!(Number.isFinite(age) && age >= 0 && age <= FRESH_MS)) {
      return { online: true, tone: 'warn', title: 'Central sem resposta', next: 'Ligue a ignição. O app continua tentando sozinho.' };
    }
    return { online: true, tone: 'ok', title: 'Central conectada', next: '' };
  }
  ns.CentralEstado = { describe, FRESH_MS };
})(typeof window !== 'undefined' ? window : globalThis);
