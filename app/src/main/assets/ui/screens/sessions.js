(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  const rules = () => root.OmegasUi.DisplayRules;
  const { finite, escapeHtml } = root.OmegasUi.DisplayRules;

  /** Índice de equivalência no início e no fim da sessão, se o dado existir (senão null: a tela não inventa). */
  function indexRange(item) {
    const summary = item && typeof item.semanticSummary === 'object' && item.semanticSummary ? item.semanticSummary : {};
    const range = item?.index && typeof item.index === 'object' ? item.index : (summary.index && typeof summary.index === 'object' ? summary.index : {});
    const start = finite(range.start ?? item?.indexStart ?? summary.indexStart);
    const end = finite(range.end ?? item?.indexEnd ?? summary.indexEnd);
    return start === null && end === null ? null : { start, end };
  }
  function blackoutCount(item) {
    const summary = item && typeof item.semanticSummary === 'object' && item.semanticSummary ? item.semanticSummary : {};
    return finite(item?.blackouts ?? summary.blackouts ?? summary.stalls);
  }
  /** O índice é fração 0..1 (percentual = ×100); sem dado: "—", nunca 0%. */
  function percentText(value) {
    return value === null ? '—' : `${Math.round(Math.max(0, Math.min(1, value)) * 100)}%`;
  }

  /** Modelo puro de uma linha da lista (testável sem DOM). */
  function sessionRow(item) {
    const cng = finite(item?.cngTicks) || 0;
    const petrol = finite(item?.petrolTicks) || 0;
    const total = cng + petrol;
    const gnv = total > 0 ? Math.round(cng / total * 100) : 0;
    return {
      id: String(item?.id || ''),
      title: String(item?.reason || 'Sessão'),
      active: item?.active === true,
      durationMs: finite(item?.durationMs),
      bytes: finite(item?.bytes),
      blackouts: blackoutCount(item),
      index: indexRange(item),
      gnvPercent: total > 0 ? gnv : null,
      gasPercent: total > 0 ? 100 - gnv : null,
      raw: item,
    };
  }

  const MAX_ROWS = 20;

  class SessionsScreen {
    constructor(store, api) {
      this.store = store;
      this.api = api;
      this.host = document.getElementById('sessionsHost');
      this.signature = '';
      this.host?.addEventListener('click', event => {
        const button = event.target.closest('[data-export-session]');
        if (button) this.api.exportSession(button.dataset.exportSession || '');
      });
    }

    render(state) {
      if (!this.host) return;
      const status = state.sessionStatus || {};
      const listError = !Array.isArray(state.sessions) && state.sessionsError ? String(state.sessionsError) : '';
      const loading = !Array.isArray(state.sessions) && !listError;
      const all = Array.isArray(state.sessions) ? state.sessions : [];
      const rows = all.slice(0, MAX_ROWS).map(sessionRow);
      const recording = status.recording === true;
      // Enquanto grava, a duração nunca é "—": usa a da ponte; sem ela, a da sessão em andamento; sem ela, o tempo desde que a tela viu a gravação começar.
      if (!recording) this.recordingSince = 0;
      else if (!this.recordingSince) this.recordingSince = Date.now();
      const activeRow = rows.find(row => row.active);
      const recordingMs = !recording ? null : (finite(status.durationMs) ?? (activeRow ? activeRow.durationMs : null) ?? Math.max(0, Date.now() - this.recordingSince));
      const signature = JSON.stringify([recording, recordingMs !== null && Math.round(recordingMs / 10000), finite(status.events), loading, listError, all.length, rows.map(r => [r.id, r.bytes, r.active, r.blackouts, r.index])]);
      if (signature === this.signature && this.host.childElementCount) return;
      this.signature = signature;
      const R = rules();
      const more = all.length - rows.length;
      const durationShown = recording ? R.durationLabel(recordingMs) : R.durationLabel(status.durationMs);
      const list = rows.length ? rows.map(row => {
        const date = R.sessionDate(row.raw);
        const indexText = row.index ? `Índice ${percentText(row.index.start)} → ${percentText(row.index.end)}` : '';
        const facts = [
          row.durationMs === null ? '' : R.durationLabel(row.durationMs),
          row.blackouts === null ? '' : `${row.blackouts} ${row.blackouts === 1 ? 'apagão' : 'apagões'}`,
          indexText,
          row.gnvPercent === null ? '' : `GNV ${row.gnvPercent}% · gasolina ${row.gasPercent}%`,
          row.active ? 'em andamento' : '',
        ].filter(Boolean).join(' · ');
        return `<article class="recorded-session-item" data-session-id="${escapeHtml(row.id)}">
          <div class="recorded-session-header"><b>${escapeHtml(row.title)}</b><span class="session-datetime">${escapeHtml(date)}</span></div>
          <div class="recorded-session-meta"><span>${escapeHtml(facts || '—')}</span></div>
          ${row.gnvPercent === null ? '' : `<div class="session-fuel-bar"><div class="fuel-segment cng" style="width:${row.gnvPercent}%"></div><div class="fuel-segment petrol" style="width:${row.gasPercent}%"></div></div>`}
          <button type="button" class="secondary" data-export-session="${escapeHtml(row.id)}">Exportar ZIP</button>
        </article>`;
      }).join('') : (listError
        ? `<p class="empty-copy" data-sessions-error>Não consegui ler as sessões salvas (${escapeHtml(listError)}). O app tenta de novo sozinho; se continuar, feche e abra o app.</p>`
        : loading
        ? '<p class="empty-copy">Lendo as sessões salvas…</p>'
        : '<p class="empty-copy">Nenhuma sessão gravada ainda. Ela começa sozinha ao conectar a ECU.</p>');
      this.host.innerHTML = `
        <section class="diagnostic-recorder-card" data-recording="${recording ? 'true' : 'false'}">
          <header><div><small>AGORA</small><h3>${recording ? 'Gravando esta sessão' : 'Começa sozinha ao conectar a ECU'}</h3></div><span>${recording ? 'GRAVANDO' : 'AUTOMÁTICA'}</span></header>
          <div class="recorder-metrics"><span><b>${durationShown}</b> duração</span><span><b>${R.megabytesLabel(finite(status.megabytes))}</b> usados</span><span><b>${R.fmt(status.events, 0)}</b> eventos</span></div>
        </section>
        <section class="recorded-session-list" aria-label="Sessões gravadas">${list}</section>
        ${more > 0 ? `<p class="empty-copy" data-sessions-truncated>Mostrando as ${R.fmt(rows.length, 0)} mais recentes de ${R.fmt(all.length, 0)}. As mais antigas continuam salvas em Download/Omegas.</p>` : ''}`;
    }
  }

  ns.SessionsScreen = SessionsScreen;
  ns.SessionsModel = { sessionRow, indexRange, blackoutCount, percentText };
})(typeof window !== 'undefined' ? window : globalThis);
