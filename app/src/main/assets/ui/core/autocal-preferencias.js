(function (root, factory) {
  'use strict';
  const api = factory(root);
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) { const ns = root.OmegasUi = root.OmegasUi || {}; ns.AutoCalPrefs = api; }
})(typeof window !== 'undefined' ? window : globalThis, function (root) {
  'use strict';
  // Preferências do AutoCal do original (docs/progbase/protocolo/autocal-preferencias.md, EV-AUTOCAL-001..008).
  // O catálogo e os quadros espelham AutoCalPreferenceCore.kt (o Kotlin é quem manda na central; aqui só pedimos {id, raw}).
  // NADA aqui inventa valor de fábrica: o efeito e a faixa de fábrica de cada item seguem "não comprovados".
  const KEY = 'omegas.autocal.prefs.v1';
  const DEFAULTS = Object.freeze({ refreshMs: 300, showZones: true, showPoints: true, showMessages: true, showKLine: true, colorPetrol: '', colorGas: '', calibrationValue: '' });

  // scale: valor mostrado = bruto / scale (só onde o documento dá a unidade); step em BRUTO; digits = casas decimais mostradas.
  const P = (id, sc, index, width, min, max, o) => Object.freeze(Object.assign({ id, sc, index, width, min, max, signed: false, scale: 1, digits: 0, unit: '', step: 1, kind: 'stepper' }, o));
  const refs = (prefix, sc, scale, step, max, label) => Array.from({ length: 18 }, (_, i) =>
    P(prefix + i, sc, i, 2, 0, max, { group: 'faixas', section: label, scale, digits: 2, step, label: 'Ponto ' + (i + 1), tech: prefix + i, order: i }));

  const CAL_NAMES = [ // 12 CalibrationVal na ordem do documento: [id, SC:índice, nome técnico, nome simples]
    ['VECT_AUTOCAL_U8_0', '357:0', 'AutoCalIdleMinBufPetrThd', 'Marcha lenta, gasolina: mínimo no buffer'],
    ['VECT_AUTOCAL_U8_1', '357:1', 'AutoCalIdleMinBufUpdPetrThd', 'Marcha lenta, gasolina: mínimo para atualizar'],
    ['CALIBRATION_VAL_1:0', '370:0', 'AutoCalIdleSumBufPetrThd', 'Marcha lenta, gasolina: soma no buffer'],
    ['CALIBRATION_VAL_1:4', '370:4', 'AutoCalIdleMinBufGasThd', 'Marcha lenta, GNV: mínimo no buffer'],
    ['CALIBRATION_VAL_1:5', '370:5', 'AutoCalIdleMinBufUpdGasThd', 'Marcha lenta, GNV: mínimo para atualizar'],
    ['CALIBRATION_VAL_1:6', '370:6', 'AutoCalIdleSumBufGasThd', 'Marcha lenta, GNV: soma no buffer'],
    ['CALIBRATION_VAL_1:1', '370:1', 'AutoCalMinBufPetrThd', 'Gasolina: mínimo no buffer'],
    ['CALIBRATION_VAL_1:2', '370:2', 'AutoCalMinBufUpdPetrThd', 'Gasolina: mínimo para atualizar'],
    ['CALIBRATION_VAL_1:3', '370:3', 'AutoCalSumBufPetrThd', 'Gasolina: soma no buffer'],
    ['CALIBRATION_VAL_1:7', '370:7', 'AutoCalMinBufGasThd', 'GNV: mínimo no buffer'],
    ['CALIBRATION_VAL_1:8', '370:8', 'AutoCalMinBufUpdGasThd', 'GNV: mínimo para atualizar'],
    ['CALIBRATION_VAL_1:9', '370:9', 'AutoCalSumBufGasThd', 'GNV: soma no buffer'],
  ];
  const calSpec = ([id, loc, tech, label]) => {
    const [sc, index] = loc.split(':').map(Number);
    return P(id, sc, index, 1, 0, 255, { group: 'qualidade', section: 'Quanto a central junta antes de aprender', label, tech, step: 1 });
  };

  const CATALOG = Object.freeze([
    P('AUTO_CAL_ENABLE', 330, -1, 1, 0, 1, { group: 'aprendizado', kind: 'enable', label: 'Aprendizado ligado', tech: 'AUTO_CAL_ENABLE', help: 'Liga o aprendizado automático da central. Desligar pausa a curva, por isso o app só deixa ligar.' }),
    P('VECT_AUTOCAL_U8_2', 357, 2, 1, 1, 99, { group: 'aprendizado', label: 'Quantos AutoMatch', tech: 'VECT_AUTOCAL_U8_2', unit: 'vezes', step: 1, help: 'Quantas vezes a central aplica o AutoMatch sozinha.' }),
    P('MAX_RPM_FOR_AUTOCAL', 378, -1, 2, 400, 8000, { group: 'aprendizado', label: 'Giro máximo para aprender', tech: 'MAX_RPM_FOR_AUTOCAL', unit: 'rpm', step: 100, help: 'Acima desse giro a central não aprende.' }),
    P('DISABLE_ACQ_BAND', 395, -1, 1, 0, 1, { group: 'aprendizado', kind: 'switch', label: 'Ignorar a faixa mais alta', tech: 'DISABLE_ACQ_BAND', help: 'Quando ligado, a central deixa de coletar na faixa mais alta de pressão.' }),
    P('LIMIT_PRESSURE_MIN', 361, -1, 2, 0, 4096, { group: 'faixas', section: 'Limites de pressão (MAP)', signed: true, scale: 1024, digits: 2, unit: 'bar', step: 51, label: 'Pressão mínima', tech: 'LIMIT_PRESSURE_MIN', help: 'Abaixo disso a central não coleta.' }),
    P('LIMIT_PRESSURE_MAX', 362, -1, 2, 0, 4096, { group: 'faixas', section: 'Limites de pressão (MAP)', signed: true, scale: 1024, digits: 2, unit: 'bar', step: 51, label: 'Pressão máxima', tech: 'LIMIT_PRESSURE_MAX', help: 'Acima disso a central não coleta.' }),
    ...refs('MNFLD_PRESS_THD:', 332, 1024, 10, 4096, 'Pressões de referência (18 pontos, sempre crescentes)').map(s => Object.freeze(Object.assign({}, s, { unit: 'bar' }))),
    ...refs('PETR_INJ_TBP:', 331, 512, 51, 20480, 'Tempos de injeção de referência (18 pontos, sempre crescentes)').map(s => Object.freeze(Object.assign({}, s, { unit: 'ms' }))),
    ...CAL_NAMES.map(calSpec),
    P('EN_CDN_T_THD', 359, 1, 2, 0, 65535, { group: 'qualidade', section: 'Limites de estabilidade', label: 'Tempo de estabilidade', tech: 'EN_CDN_T_THD', scale: 1024, digits: 2, step: 102 }),
    P('DIFF_ENG_SPD_THD', 387, -1, 2, 0, 65535, { group: 'qualidade', section: 'Limites de estabilidade', label: 'Giro: diferença aceita', tech: 'DIFF_ENG_SPD_THD', unit: 'rpm', step: 50 }),
    P('DELTA_ENG_SPD_THD', 388, -1, 2, 0, 65535, { group: 'qualidade', section: 'Limites de estabilidade', label: 'Giro: variação aceita', tech: 'DELTA_ENG_SPD_THD', unit: 'rpm', step: 50 }),
    P('DIFF_MNFLD_PRESS_THD', 389, -1, 2, 0, 65535, { group: 'qualidade', section: 'Limites de estabilidade', label: 'Pressão: diferença aceita', tech: 'DIFF_MNFLD_PRESS_THD', step: 10 }),
    P('DELTA_MNFLD_PRESS_THD', 390, -1, 2, 0, 65535, { group: 'qualidade', section: 'Limites de estabilidade', label: 'Pressão: variação aceita', tech: 'DELTA_MNFLD_PRESS_THD', step: 10 }),
    P('DIFF_PETR_TINJ_T_THD', 391, -1, 2, 0, 65535, { group: 'qualidade', section: 'Limites de estabilidade', label: 'Injeção: diferença aceita', tech: 'DIFF_PETR_TINJ_T_THD', step: 51 }),
    P('DELTA_PETR_INJ_T_THD', 392, -1, 2, 0, 65535, { group: 'qualidade', section: 'Limites de estabilidade', label: 'Injeção: variação aceita', tech: 'DELTA_PETR_INJ_T_THD', step: 51 }),
  ]);
  const BY_ID = Object.freeze(Object.fromEntries(CATALOG.map(s => [s.id, s])));
  const GROUPS = Object.freeze([
    { id: 'aprendizado', title: 'Aprendizado', hint: 'Liga o aprendizado e define até onde ele vai.' },
    { id: 'faixas', title: 'Faixas de pressão e injeção', hint: 'Onde a central coleta. Os pontos têm que ser sempre crescentes.' },
    { id: 'qualidade', title: 'Qualidade da coleta', hint: 'Quão estável e cheia tem que estar a coleta para a central aprender.' },
    { id: 'exibicao', title: 'Exibição neste aparelho', hint: 'Só muda a tela deste aparelho. Não mexe na central.' },
  ]);

  const sum = bytes => bytes.reduce((a, b) => (a + b) & 0xFF, 0);
  const hex = bytes => bytes.map(b => b.toString(16).toUpperCase().padStart(2, '0')).join(' ');
  const framed = body => hex(body.concat(sum(body)));
  const validRaw = (s, raw) => Number.isInteger(raw) && raw >= s.min && raw <= s.max;
  // Mesmo quadro do Kotlin: 0x11 + largura + (vetor ? 1 : 0), SC em 2 bytes, [índice], valor little-endian, soma módulo 256.
  function writeFrame(s, raw) {
    if (!s || !validRaw(s, raw)) return null;
    const body = [0x11 + s.width + (s.index >= 0 ? 1 : 0), s.sc & 0xFF, (s.sc >> 8) & 0xFF];
    if (s.index >= 0) body.push(s.index);
    body.push(raw & 0xFF); if (s.width === 2) body.push((raw >> 8) & 0xFF);
    return framed(body);
  }
  // Leitura: número = 09; elemento de vetor = vetor inteiro 29 (o 0A não tem resposta provada).
  function readFrame(s) { return framed([s.index >= 0 ? 0x29 : 0x09, s.sc & 0xFF, (s.sc >> 8) & 0xFF]); }

  // ---- valores mostrados ----
  const toDisplay = (s, raw) => (raw == null ? '—' : (raw / s.scale).toFixed(s.digits).replace('.', ','));
  function fromDisplay(s, text) {
    const n = Number(String(text).trim().replace(',', '.'));
    return Number.isFinite(n) && String(text).trim() !== '' ? Math.round(n * s.scale) : null;
  }
  // Recusa antes de enviar. `known(id)` devolve o valor atual de outro item ou null. Vizinho desconhecido não bloqueia aqui (o Kotlin relê e decide).
  function validate(s, raw, known) {
    if (!validRaw(s, raw)) return 'Valor fora da faixa (' + toDisplay(s, s.min) + ' a ' + toDisplay(s, s.max) + ').';
    if (s.id === 'AUTO_CAL_ENABLE' && raw === 0) return 'Desligar o aprendizado pausa a curva. Use só Ligar.';
    const get = known || (() => null);
    if (s.id === 'LIMIT_PRESSURE_MIN') { const o = get('LIMIT_PRESSURE_MAX'); if (o != null && raw >= o) return 'A pressão mínima tem que ficar abaixo da máxima.'; }
    if (s.id === 'LIMIT_PRESSURE_MAX') { const o = get('LIMIT_PRESSURE_MIN'); if (o != null && raw <= o) return 'A pressão máxima tem que ficar acima da mínima.'; }
    if (s.order != null) {
      const base = s.id.slice(0, s.id.indexOf(':') + 1);
      const prev = s.order > 0 ? get(base + (s.order - 1)) : null; const next = s.order < 17 ? get(base + (s.order + 1)) : null;
      if (prev != null && raw <= prev) return 'Cada ponto tem que ser maior que o anterior.';
      if (next != null && raw >= next) return 'Cada ponto tem que ser menor que o seguinte.';
    }
    return null;
  }

  // ---- preferências locais (neste aparelho) ----
  function storage() { try { return root.localStorage || null; } catch (_) { return null; } }
  function load() {
    const out = Object.assign({}, DEFAULTS);
    try {
      const s = JSON.parse(storage()?.getItem(KEY) || '{}');
      if (Number.isFinite(s.refreshMs)) out.refreshMs = Math.min(2000, Math.max(200, s.refreshMs));
      for (const k of ['showZones', 'showPoints', 'showMessages', 'showKLine']) if (typeof s[k] === 'boolean') out[k] = s[k];
      for (const k of ['colorPetrol', 'colorGas']) if (/^#[0-9a-f]{6}$/i.test(s[k] || '')) out[k] = s[k];
      if (typeof s.calibrationValue === 'string') out.calibrationValue = s.calibrationValue.slice(0, 16);
    } catch (_) { /* sem armazenamento: usa o padrão */ }
    return out;
  }
  function save(prefs) { try { storage()?.setItem(KEY, JSON.stringify(prefs)); return true; } catch (_) { return false; } }
  function applyToDom(prefs, doc) {
    const el = (doc || root.document)?.documentElement; if (!el) return;
    el.dataset.acHideZones = prefs.showZones ? '0' : '1';
    el.dataset.acHidePoints = prefs.showPoints ? '0' : '1';
    el.dataset.acHideMessages = prefs.showMessages ? '0' : '1';
    el.dataset.acHideKline = prefs.showKLine === false ? '1' : '0';
    if (prefs.colorPetrol) el.style.setProperty('--ac-pref-petrol', prefs.colorPetrol); else el.style.removeProperty('--ac-pref-petrol');
    if (prefs.colorGas) el.style.setProperty('--ac-pref-gas', prefs.colorGas); else el.style.removeProperty('--ac-pref-gas');
  }

  // ---- ponte com a central ----
  function parse(v) { try { return typeof v === 'string' ? JSON.parse(v) : v; } catch (_) { return null; } }
  function supported() { const b = root.OmegasAutoCal; return !!b && typeof b.readAutoCalPreference === 'function' && typeof b.applyAutoCalPreference === 'function'; }
  // Desconhecido = null (a tela mostra "—"). Nunca 0.
  function read(ids) {
    const out = Object.fromEntries(ids.map(id => [id, null]));
    if (!supported()) return out;
    try {
      const r = parse(root.OmegasAutoCal.readAutoCalPreference(JSON.stringify({ ids })));
      if (r && r.ok && r.values) for (const id of ids) if (Number.isInteger(r.values[id])) out[id] = r.values[id];
    } catch (_) { /* fica "—" */ }
    return out;
  }
  // Um toque: o Kotlin lê, grava, exige ACK e relê. "Gravado" só com outcome CONFIRMED e leitura de volta igual ao pedido.
  function write(id, raw, known) {
    const s = BY_ID[id];
    if (!s) return { state: 'invalid', message: 'Item desconhecido.' };
    const why = validate(s, raw, known);
    if (why) return { state: 'invalid', message: why };
    if (!supported()) return { state: 'unavailable', message: 'Gravação indisponível nesta versão do app.' };
    let r = null;
    try { r = parse(root.OmegasAutoCal.applyAutoCalPreference(JSON.stringify({ id, raw }))); } catch (_) { r = null; }
    if (!r) return { state: 'failed', message: 'A central não respondeu. Nada foi dado como gravado.' };
    const before = Number.isInteger(r.before) ? r.before : null;
    if (r.outcome === 'NOTHING_TO_DO') return { state: 'same', raw, before, message: 'Já estava com esse valor.' };
    if (r.ok === true && r.outcome === 'CONFIRMED' && r.after === raw) return { state: 'saved', raw, before, message: 'Gravado e conferido.' };
    if (r.outcome === 'READBACK_DIVERGED' && Number.isInteger(r.after)) return { state: 'mismatch', raw: r.after, before, message: 'A central devolveu outro valor. Não foi dado como gravado.' };
    return { state: 'failed', before, message: r.message || r.error || 'A central não confirmou. Não foi dado como gravado.' };
  }

  // ---- tela ----
  const esc = t => String(t ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const SEAL = '<span class="pref-seal" title="O efeito deste item e o valor de fábrica ainda não foram comprovados">não comprovado</span>';

  function cardHtml(s, current, draft, note) {
    const shown = draft != null ? draft : current;
    const head = '<label for="pref-' + esc(s.id) + '">' + esc(s.label) + '</label>' + SEAL;
    const tail = (s.tech ? '<p class="pref-tech">' + esc(s.tech) + ' · bruto ' + (current == null ? '—' : current) + '</p>' : '')
      + '<p class="pref-note" role="status" data-kind="' + esc(note?.kind || '') + '">' + esc(note?.text || '') + '</p>';
    const help = s.help ? '<p class="pref-help">' + esc(s.help) + '</p>' : '';
    let body;
    if (s.kind === 'enable' || s.kind === 'switch') {
      const on = current === 1;
      const label = s.kind === 'enable' ? (on ? 'Ligado' : 'Ligar') : (on ? 'Desligar' : 'Ligar');
      const dis = current == null || (s.kind === 'enable' && on);
      body = '<div class="pref-switch"><span class="pref-state">' + (current == null ? '—' : on ? 'Ligado' : 'Desligado') + '</span>'
        + '<button type="button" class="step-btn" data-pref-set="' + esc(s.id) + ':' + (on ? 0 : 1) + '"' + (dis ? ' disabled' : '') + '>' + label + '</button></div>';
    } else {
      const dis = current == null ? ' disabled' : '';
      const dirty = draft != null && draft !== current;
      body = '<div class="num-stepper"><button type="button" class="step-btn" data-pref-step="' + esc(s.id) + ':-1" aria-label="Diminuir ' + esc(s.label) + '"' + dis + '>−</button>'
        + '<div class="num-box"><input id="pref-' + esc(s.id) + '" type="text" inputmode="decimal" autocomplete="off" data-pref-field="' + esc(s.id) + '" value="' + esc(shown == null ? '' : toDisplay(s, shown)) + '" placeholder="—"' + dis + '>'
        + (s.unit ? '<span class="num-unit">' + esc(s.unit) + '</span>' : '') + '</div>'
        + '<button type="button" class="step-btn" data-pref-step="' + esc(s.id) + ':1" aria-label="Aumentar ' + esc(s.label) + '"' + dis + '>+</button></div>'
        + '<div class="pref-actions"><button type="button" class="step-btn pref-save" data-pref-save="' + esc(s.id) + '"' + (dirty ? '' : ' hidden') + '>Gravar na central</button></div>';
    }
    const undo = note?.undo != null ? '<button type="button" class="pref-undo" data-pref-undo="' + esc(s.id) + '">Desfazer</button>' : '';
    return '<div class="num-field vv-card pref-card" data-pref-card="' + esc(s.id) + '">' + head + help + body + undo + tail + '</div>';
  }

  function localHtml(prefs) {
    const sw = (k, label) => '<label class="pref-local-row"><span>' + label + '</span><input type="checkbox" data-pref="' + k + '"' + (prefs[k] ? ' checked' : '') + '></label>';
    return '<div class="vv-card pref-card"><label for="pref-refresh">Atualizar a cada</label>'
      + '<div class="num-stepper"><button type="button" class="step-btn" data-pref-local-step="-50" aria-label="Diminuir">−</button>'
      + '<div class="num-box"><input id="pref-refresh" type="text" inputmode="numeric" data-pref="refreshMs" value="' + esc(prefs.refreshMs) + '"><span class="num-unit">ms</span></div>'
      + '<button type="button" class="step-btn" data-pref-local-step="50" aria-label="Aumentar">+</button></div><p class="pref-help">De 200 a 2000 ms.</p></div>'
      + '<div class="vv-card pref-card">' + sw('showMessages', 'Mostrar mensagens') + sw('showZones', 'Mostrar zonas') + sw('showPoints', 'Mostrar pontos') + sw('showKLine', 'Mostrar linha da curva K') + '</div>'
      + '<div class="vv-card pref-card"><label class="pref-local-row"><span>Cor da gasolina</span><input type="color" data-pref="colorPetrol" value="' + esc(prefs.colorPetrol || '#ff5a5a') + '"></label>'
      + '<label class="pref-local-row"><span>Cor do GNV</span><input type="color" data-pref="colorGas" value="' + esc(prefs.colorGas || '#4cd964') + '"></label></div>';
  }

  function groupHtml(g, state) {
    if (g.id === 'exibicao') return '<details class="pref-group" data-pref-group="exibicao"><summary>' + esc(g.title) + '<small>' + esc(g.hint) + '</small></summary><div class="vv-grid pref-grid">' + localHtml(state.prefs) + '</div></details>';
    let lastSection = null; let cards = '';
    for (const s of CATALOG.filter(x => x.group === g.id)) {
      if (s.section && s.section !== lastSection) { cards += '<h4 class="pref-section">' + esc(s.section) + '</h4>'; lastSection = s.section; }
      cards += cardHtml(s, state.values[s.id] ?? null, state.drafts[s.id], state.notes[s.id]);
    }
    return '<details class="pref-group" data-pref-group="' + g.id + '"><summary>' + esc(g.title) + '<small>' + esc(g.hint) + '</small></summary><div class="vv-grid pref-grid">' + cards + '</div></details>';
  }

  function panelHtml(state) {
    return '<p class="pref-intro">Ajustes da central do AutoCal. Cada item diz "não comprovado" enquanto o efeito dele não foi visto numa central real. Toque em Gravar: o app confere o que a central devolve.</p>'
      + '<p class="pref-status" id="autocalPrefState" role="status">' + (supported() ? '—' : 'Sem ligação com a central neste aparelho.') + '</p>'
      + GROUPS.map(g => groupHtml(g, state)).join('');
  }

  // Liga o painel ao menu <details id="autocalPrefMenu">: monta, lê um grupo quando ele abre, grava com um toque.
  function mount(menu, hooks) {
    const doc = menu.ownerDocument || root.document;
    const body = menu.querySelector('[data-pref-body]') || menu;
    const state = { prefs: load(), values: {}, drafts: {}, notes: {}, loaded: {} };
    applyToDom(state.prefs, doc);
    const notify = () => { try { doc.dispatchEvent(new root.CustomEvent('omegas:autocal-prefs', { detail: Object.assign({}, state.prefs) })); } catch (_) { /* sem evento */ } };
    const known = id => (Number.isInteger(state.values[id]) ? state.values[id] : null);
    const statusEl = () => body.querySelector('#autocalPrefState');
    const repaint = id => {
      const old = body.querySelector('[data-pref-card="' + id + '"]'); const s = BY_ID[id]; if (!old || !s) return;
      const tmp = doc.createElement('div'); tmp.innerHTML = cardHtml(s, known(id), state.drafts[id], state.notes[id]);
      old.replaceWith(tmp.firstElementChild);
    };
    const openGroup = g => {
      if (state.loaded[g]) return; state.loaded[g] = true;
      const ids = CATALOG.filter(s => s.group === g).map(s => s.id);
      Object.assign(state.values, read(ids));
      ids.forEach(repaint);
      const unknown = ids.filter(id => state.values[id] == null).length;
      if (statusEl()) statusEl().textContent = unknown === ids.length ? 'Não consegui ler a central. Os valores aparecem como "—".' : unknown ? unknown + ' item(ns) sem leitura ("—").' : 'Valores lidos da central.';
    };
    const step = (id, dir) => {
      const s = BY_ID[id]; const base = state.drafts[id] ?? known(id); if (base == null) return;
      state.drafts[id] = Math.min(s.max, Math.max(s.min, base + dir * s.step)); state.notes[id] = null; repaint(id);
    };
    const commit = (id, raw) => {
      const r = write(id, raw, known);
      const prev = known(id);
      if (r.state === 'saved') { state.values[id] = raw; delete state.drafts[id]; state.notes[id] = { kind: 'ok', text: r.message, undo: r.before }; }
      else if (r.state === 'same') { delete state.drafts[id]; state.notes[id] = { kind: 'ok', text: r.message }; }
      else if (r.state === 'mismatch') { state.values[id] = r.raw; delete state.drafts[id]; state.notes[id] = { kind: 'error', text: r.message }; }
      else state.notes[id] = { kind: 'error', text: r.message };
      if (r.state === 'saved' && r.before == null && prev != null) state.notes[id].undo = prev;
      repaint(id);
      if (hooks?.onWrite) hooks.onWrite(id, r);
    };
    body.innerHTML = panelHtml(state);
    menu.addEventListener('toggle', () => { if (menu.open) { body.querySelectorAll('details.pref-group[open]').forEach(d => openGroup(d.dataset.prefGroup)); } });
    body.addEventListener('toggle', ev => { const d = ev.target; if (d?.dataset?.prefGroup && d.open && d.dataset.prefGroup !== 'exibicao') openGroup(d.dataset.prefGroup); }, true);
    body.addEventListener('click', ev => {
      const t = ev.target.closest?.('button'); if (!t) return;
      if (t.dataset.prefStep) { const [id, d] = t.dataset.prefStep.split(':'); step(id, Number(d)); return; }
      if (t.dataset.prefSave) { const id = t.dataset.prefSave; if (state.drafts[id] != null) commit(id, state.drafts[id]); return; }
      if (t.dataset.prefSet) { const [id, v] = t.dataset.prefSet.split(':'); commit(id, Number(v)); return; }
      if (t.dataset.prefUndo) { const id = t.dataset.prefUndo; const back = state.notes[id]?.undo; if (Number.isInteger(back)) { const keep = back; commit(id, keep); if (state.notes[id]) { state.notes[id].undo = null; state.notes[id].text = 'Desfeito.'; repaint(id); } } return; }
      if (t.dataset.prefLocalStep) {
        state.prefs.refreshMs = Math.min(2000, Math.max(200, state.prefs.refreshMs + Number(t.dataset.prefLocalStep)));
        const input = body.querySelector('[data-pref="refreshMs"]'); if (input) input.value = state.prefs.refreshMs;
        save(state.prefs); applyToDom(state.prefs, doc); notify();
      }
    });
    body.addEventListener('change', ev => {
      const el = ev.target;
      if (el.dataset?.prefField) {
        const s = BY_ID[el.dataset.prefField]; const raw = fromDisplay(s, el.value);
        if (raw == null) { state.notes[s.id] = { kind: 'error', text: 'Digite um número.' }; } else { state.drafts[s.id] = raw; state.notes[s.id] = null; }
        repaint(s.id); return;
      }
      const k = el.dataset?.pref; if (!k) return;
      state.prefs[k] = el.type === 'checkbox' ? el.checked : k === 'refreshMs' ? Math.min(2000, Math.max(200, Number(el.value) || DEFAULTS.refreshMs)) : el.value;
      if (k === 'refreshMs') el.value = state.prefs.refreshMs;
      save(state.prefs); applyToDom(state.prefs, doc); notify();
    });
    return { state, openGroup, step, commit };
  }

  return { KEY, DEFAULTS, CATALOG, BY_ID, GROUPS, CAL_NAMES, writeFrame, readFrame, validate, toDisplay, fromDisplay, validRaw, load, save, applyToDom, supported, read, write, cardHtml, panelHtml, mount };
});
