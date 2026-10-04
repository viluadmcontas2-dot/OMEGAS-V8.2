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
      autoMatch: finite(item?.autoMatchCount ?? item?.automatchCount ?? item?.semanticSummary?.autoMatchCount ?? item?.semanticSummary?.autoMatch),
      path: String(item?.path || item?.folder || ''),
      raw: item,
    };
  }

  const FOLDER = 'Download/Omegas';
  const plural = (n, one, many) => `${n} ${n === 1 ? one : many}`;
  /** Uma frase humana por sessão; só afirma o que o dado traz. */
  function sessionSummary(row) {
    const parts = [];
    if (row.gnvPercent !== null) parts.push(row.gnvPercent === 0 ? 'Rodou só na gasolina' : row.gasPercent === 0 ? 'Rodou só no GNV' : `Rodou ${row.gnvPercent}% no GNV`);
    let text = parts.join(', ');
    const extra = [];
    if (row.blackouts !== null) extra.push(row.blackouts === 0 ? 'sem apagões' : plural(row.blackouts, 'apagão', 'apagões'));
    if (extra.length) text += `${text ? '; ' : ''}${extra.join(', ')}`;
    if (row.index && row.index.start !== null && row.index.end !== null) text += `${text ? '. ' : ''}GNV igual à gasolina: ${percentText(row.index.start)} → ${percentText(row.index.end)}`;
    return text ? text + '.' : '';
  }

  const MAX_ROWS = 20;

  class SessionsScreen {
    constructor(store, api) {
      this.store = store;
      this.api = api;
      this.host = document.getElementById('sessionsHost');
      this.signature = '';
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
      const signature = JSON.stringify([recording, recordingMs !== null && Math.round(recordingMs / 10000), finite(status.events), loading, listError, all.length, rows.map(r => [r.id, r.bytes, r.active, r.blackouts, r.index, r.autoMatch])]);
      if (signature === this.signature && this.host.childElementCount) return;
      this.signature = signature;
      const R = rules();
      const more = all.length - rows.length;
      const durationShown = recording ? R.durationLabel(recordingMs) : R.durationLabel(status.durationMs);
      const list = rows.length ? rows.map(row => {
        const date = R.sessionDate(row.raw);
        const [day, time] = date === R.DASH ? [R.DASH, ''] : date.split(' ');
        const shortTime = time ? time.slice(0, 5) : '';
        const summary = sessionSummary(row);
        const facts = [
          ['Duração', row.durationMs === null ? R.DASH : R.durationLabel(row.durationMs)],
          ['Tamanho', R.bytesLabel(row.bytes)],
          ['AutoMatch', row.autoMatch === null ? R.DASH : String(row.autoMatch)],
        ].map(([k, v]) => `<div><dt>${k}</dt><dd>${escapeHtml(v)}</dd></div>`).join('');
        return `<article class="ss-item" data-session-id="${escapeHtml(row.id)}" data-state="${row.active ? 'recording' : 'closed'}">
          <div class="ss-when"><b>${escapeHtml(day)}</b><span>${escapeHtml(shortTime)}</span><em class="ss-state" data-state="${row.active ? 'recording' : 'closed'}">${row.active ? 'Gravando' : 'Fechada'}</em></div>
          <div class="ss-body">
            <p class="ss-summary">${escapeHtml(summary || (row.active ? 'Gravando agora; o resumo aparece quando ela fechar.' : 'Sem resumo para esta sessão.'))}</p>
            ${row.gnvPercent === null ? '' : `<div class="session-fuel-bar" role="img" aria-label="GNV ${row.gnvPercent}%, gasolina ${row.gasPercent}%"><div class="fuel-segment cng" style="width:${row.gnvPercent}%"></div><div class="fuel-segment petrol" style="width:${row.gasPercent}%"></div></div>`}
            <small class="ss-path">${escapeHtml(row.path || FOLDER)}${row.title && row.title !== 'Sessão' ? ' · ' + escapeHtml(row.title) : ''}</small>
          </div>
          <dl class="ss-facts">${facts}</dl>
        </article>`;
      }).join('') : (listError
        ? `<p class="empty-copy" data-sessions-error>Não consegui ler as sessões salvas (${escapeHtml(listError)}). O app tenta de novo sozinho; se continuar, feche e abra o app.</p>`
        : loading
        ? '<p class="empty-copy">Lendo as sessões salvas…</p>'
        : '<p class="empty-copy">Nenhuma sessão gravada ainda. A primeira começa sozinha quando você ligar a ECU.</p>');
      this.host.innerHTML = `
        <section class="ss-now" data-recording="${recording ? 'true' : 'false'}">
          <div class="ss-now-main"><small>${recording ? 'GRAVANDO AGORA' : 'GRAVAÇÃO'}</small><h3>${recording ? 'Esta condução está sendo gravada' : 'Nenhuma gravação em andamento'}</h3><p>${recording ? 'Fecha sozinha ao desligar a ECU.' : 'A próxima começa sozinha ao conectar a ECU.'}</p></div>
          <dl class="ss-facts"><div><dt>Duração</dt><dd>${recording ? durationShown : R.DASH}</dd></div><div><dt>Usado</dt><dd>${R.megabytesLabel(finite(status.megabytes))}</dd></div><div><dt>Eventos</dt><dd>${R.fmt(status.events, 0)}</dd></div></dl>
        </section>
        <p class="ss-where">Cada sessão é salva sozinha em <b>${FOLDER}</b> quando termina, pronta para compartilhar.</p>
        <section class="ss-list" aria-label="Sessões gravadas">${list}</section>
        ${more > 0 ? `<p class="empty-copy" data-sessions-truncated>Mostrando as ${R.fmt(rows.length, 0)} mais recentes de ${R.fmt(all.length, 0)}. As mais antigas continuam em ${FOLDER}.</p>` : ''}`;
    }
  }

  ns.SessionsScreen = SessionsScreen;
  ns.SessionsModel = { sessionRow, sessionSummary, indexRange, blackoutCount, percentText };
})(typeof window !== 'undefined' ? window : globalThis);
