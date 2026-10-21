(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Multimídia (Car Info), SÓ LEITURA (dono, 2026-10-21): ver no carro se a central Android entrega velocidade, odômetro etc.
  // Entrada = CarInfoSource.json() (enabled, connected, state, lastError, failures, samples, lastSampleAtMs, nextRetryAtMs, sample).
  // Desconhecido = "—", nunca 0. O único controle liga/desliga a LEITURA (setCarInfoEnabled), num toque. Nada vai para a CAN/ECU.
  const DASH = '—';
  const NO_SPEED_HINT = 'A central não mandou velocidade. Os dados brutos foram gravados na sessão.';
  const NO_SPEED_AFTER_MS = 60_000;
  const HINT = 'Conectou, mas o carro não mandou velocidade/odômetro: o Peugeot pode usar outro canal.';
  // [chave do DashBoardInfo, rótulo, unidade, casas]. Consumo sem unidade certa (docs/CAR-INFO.md): mostra só o número.
  const FIELDS = [
    ['speed', 'Velocidade', 'km/h', 0], ['odo', 'Odômetro', 'km', 0], ['rotationRate', 'Rotação', 'rpm', 0],
    ['gearShiftPosition', 'Marcha', '', 0], ['instFuelCont', 'Consumo agora', '', 1], ['avgFuelCont', 'Consumo médio', '', 1],
    ['cruisingRange', 'Autonomia', 'km', 0], ['fuelPercentage', 'Combustível', '%', 0], ['coolantTemp', 'Temperatura da água', '°C', 0],
  ];

  const finite = value => (value === null || value === undefined || value === '' || !Number.isFinite(Number(value)) ? null : Number(value));
  // Defesa extra (campo, 2026-10-21: "Consumo agora" -1,0): valor negativo onde não existe vira "—". Temperatura e marcha podem ser negativas.
  const MAY_BE_NEGATIVE = new Set(['coolantTemp', 'gearShiftPosition']);
  const fmt = (v, digits) => { const n = finite(v); return n === null ? DASH : n.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits }); };
  const esc = v => String(v ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const short = text => { const t = String(text || '').trim() || 'motivo desconhecido'; return t.length > 60 ? t.slice(0, 59) + '…' : t; };
  const ago = ms => (ms < 120_000 ? `${Math.max(0, Math.round(ms / 1000))} s` : `${Math.round(ms / 60_000)} min`);

  /** Estado em palavras + valores. `info` nulo ou ok:false = ponte sem a leitura (navegador, app antigo). */
  function view(info, nowMs, ecuRpm) {
    const now = finite(nowMs) ?? Date.now();
    const ok = !!info && typeof info === 'object' && info.ok !== false && typeof info.enabled === 'boolean';
    const enabled = ok ? info.enabled : null;
    const tries = (finite(ok && info.failures) || 0) + 1;
    let kind; let sentence; let meta = '';
    if (!ok) { kind = 'nobridge'; sentence = 'Indisponível: só funciona no app'; }
    else if (!enabled) { kind = 'off'; sentence = 'Desligada'; }
    else if (info.connected === true) { kind = 'on'; sentence = 'Conectada e lendo'; }
    else if (info.state === 'conectando') { kind = 'searching'; sentence = `Procurando a multimídia… (tentativa ${tries})`; }
    else {
      const wait = finite(info.nextRetryAtMs) === null ? null : finite(info.nextRetryAtMs) - now;
      if (wait !== null && wait > 0) {
        kind = 'searching';
        sentence = `Procurando a multimídia… (tentativa ${tries}, próxima em ${Math.ceil(wait / 1000)} s)`;
        meta = info.lastError ? `Indisponível: ${short(info.lastError)}` : '';
      } else { kind = 'unavailable'; sentence = `Indisponível: ${short(info.lastError)}`; }
    }
    if (ok && !meta) {
      const count = finite(info.samples) || 0;
      const at = finite(info.lastSampleAtMs);
      const plural = `${count.toLocaleString('pt-BR')} ${count === 1 ? 'leitura' : 'leituras'}`;
      meta = at === null ? `Nenhuma leitura ainda · ${plural}` : `Última leitura há ${ago(now - at)} · ${plural}`;
    }
    const sample = ok && enabled && info.sample && typeof info.sample === 'object' ? info.sample : {};
    const values = FIELDS.map(([key, label, unit, digits]) => ({ key, label, unit, text: !MAY_BE_NEGATIVE.has(key) && finite(sample[key]) < 0 ? DASH : fmt(sample[key], digits) }));
    const known = key => finite(sample[key]) !== null && !(!MAY_BE_NEGATIVE.has(key) && finite(sample[key]) < 0);
    const first = finite(ok && info.firstSampleAtMs);
    const rpm = known('rotationRate') ? finite(sample.rotationRate) : finite(ecuRpm);
    // Carro andando (rotação > 1000) há mais de 60 s de leitura e a central não mandou velocidade nem odômetro.
    const noSpeed = kind === 'on' && first !== null && now - first > NO_SPEED_AFTER_MS && rpm !== null && rpm > 1000 && !known('speed') && !known('odo');
    const hint = noSpeed ? NO_SPEED_HINT : (kind === 'on' && values.every(x => x.text === DASH) ? HINT : '');
    const ch = ok && info.channels && typeof info.channels === 'object' ? info.channels : null;
    const diag = kind === 'on' && ch && finite(ch.channelsActive) !== null ? `Canais ativos: ${finite(ch.channelsActive)} de ${finite(ch.channelsTotal) || 17}` : '';
    // Detalhes (complexidade escondida): canal de cada valor conhecido e chamadas por transação.
    const src = sample.sources && typeof sample.sources === 'object' ? sample.sources : {};
    const sources = FIELDS.filter(([key]) => known(key)).map(([key, label]) => ({ key, label, source: String(src[key] || 'tx6') }));
    const calls = ch && ch.tx && typeof ch.tx === 'object' ? Object.keys(ch.tx).sort((a, b) => a - b).map(k => ({ tx: k, n: finite(ch.tx[k] && ch.tx[k].n) || 0 })) : [];
    const details = { sources, calls };
    const stateText = { on: 'Lendo', off: 'Desligada', searching: 'Procurando', unavailable: 'Indisponível', nobridge: 'Indisponível' }[kind];
    return { kind, enabled, sentence, meta, values, hint, diag, details, stateText };
  }

  /** "Detalhes": fechado por padrão; só texto curto (canal de cada valor, chamadas por canal). */
  function detailsHtml(v, opts) {
    const d = v.details;
    if (!d || (!d.sources.length && !d.calls.length)) return '';
    const open = opts && opts.detailsOpen === true ? ' open' : '';
    const line1 = d.sources.map(x => `${esc(x.label)}: ${esc(x.source)}`).join(' · ');
    const line2 = d.calls.map(x => `${esc(x.tx)}: ${x.n.toLocaleString('pt-BR')}`).join(' · ');
    return `<details class="carinfo-details"${open}><summary>Detalhes</summary>${line1 ? `<p>Canal de cada valor — ${line1}</p>` : ''}${line2 ? `<p>Chamadas por canal — ${line2}</p>` : ''}</details>`;
  }

  /** Área aberta: Voltar, título, chave; estado e idade; grade 3×3; dica (só quando conectada e tudo "—"). */
  function html(v, opts) {
    const on = v.enabled === true;
    const sw = v.enabled === null
      ? '<button type="button" class="tool-switch" aria-label="Ler da multimídia" data-carinfo-toggle disabled><span class="sw-track" aria-hidden="true"><i></i></span><span class="sw-caption"><b>—</b><small>Ler da multimídia</small></span></button>'
      : `<button type="button" class="tool-switch" role="switch" aria-checked="${on}" aria-label="Ler da multimídia" data-carinfo-toggle><span class="sw-track" aria-hidden="true"><i></i></span><span class="sw-caption"><b>${on ? 'Ligado' : 'Desligado'}</b><small>Ler da multimídia</small></span></button>`;
    return `
      <section class="tool-view carinfo-view" data-card="carinfo" data-state="${esc(v.kind)}">
        <header><button type="button" class="secondary tool-back" data-tool-back><span aria-hidden="true">‹</span> Voltar</button><h3>Multimídia (Car Info)</h3>${sw}</header>
        <div class="carinfo-status" role="status"><b>${esc(v.sentence)}</b><span>${esc(v.meta)}</span>${v.diag ? `<span class="carinfo-diag">${esc(v.diag)}</span>` : ''}</div>
        <div class="carinfo-grid">${v.values.map(x => `<div class="carinfo-cell" data-field="${esc(x.key)}" data-known="${x.text !== DASH}"><span>${esc(x.label)}</span><b>${esc(x.text)}${x.unit && x.text !== DASH ? ` <small>${esc(x.unit)}</small>` : ''}</b></div>`).join('')}</div>
        ${v.hint ? `<p class="carinfo-hint">${esc(v.hint)}</p>` : ''}
        ${detailsHtml(v, opts)}
      </section>`;
  }

  /** Um toque = a ordem: liga/desliga a leitura. Sem ponte não faz nada. */
  function toggle(api, v) {
    if (!api || typeof api.setCarInfoEnabled !== 'function' || v.enabled === null) return null;
    return api.setCarInfoEnabled(!v.enabled);
  }

  ns.CarInfoView = { FIELDS, HINT, NO_SPEED_HINT, view, html, toggle };
})(typeof window !== 'undefined' ? window : globalThis);
