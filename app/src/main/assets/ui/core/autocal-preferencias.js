(function (root, factory) {
  'use strict';
  const api = factory(root);
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) { const ns = root.OmegasUi = root.OmegasUi || {}; ns.AutoCalPrefs = api; }
})(typeof window !== 'undefined' ? window : globalThis, function (root) {
  'use strict';
  // Preferências do AutoCal (docs/progbase/protocolo/autocal-preferencias.md, EV-AUTOCAL-001..006).
  // Só dois parâmetros da central têm quadro provado; o resto fica "não comprovado" e somente leitura.
  const KEY = 'omegas.autocal.prefs.v1';
  const DEFAULTS = Object.freeze({ refreshMs: 300, showZones: true, showPoints: true, showMessages: true, colorPetrol: '', colorGas: '', calibrationValue: '' });
  const PARAMS = Object.freeze({
    automatchLimit: { id: 'automatchLimit', label: 'Limite de AutoMatch', sc: 357, index: 2, width: 1, min: 1, max: 99 },
    rpmLimit: { id: 'rpmLimit', label: 'Limite de RPM', sc: 378, index: -1, width: 2, min: 400, max: 8000 },
  });
  const UNPROVEN = Object.freeze([
    'Habilitar autocalibração', 'Desativar faixa de aquisição mais alta', 'Limite mínimo de MAP', 'Limite máximo de MAP',
    'EN_CDN_T_THD', 'DIFF_ENG_SPD_THD', 'DELTA_ENG_SPD_THD', 'DIFF_MNFLD_PRESS_THD', 'DELTA_MNFLD_PRESS_THD',
    'DIFF_PETR_TINJ_T_THD', 'DELTA_PETR_INJ_T_THD', 'Grade avançada de calibração (12 itens)', 'Referências de MAP e injeção',
  ]);
  const sum = bytes => bytes.reduce((a, b) => (a + b) & 0xFF, 0);
  const hex = bytes => bytes.map(b => b.toString(16).toUpperCase().padStart(2, '0')).join(' ');

  function validRaw(p, raw) { return Number.isInteger(raw) && raw >= p.min && raw <= p.max; }
  function writeFrame(p, raw) {
    if (!validRaw(p, raw)) return null;
    const head = p.index >= 0 ? [0x13, p.sc & 0xFF, 0x01, p.index] : [p.width === 2 ? 0x13 : 0x12, p.sc & 0xFF, 0x01];
    const value = p.width === 2 ? [raw & 0xFF, (raw >> 8) & 0xFF] : [raw & 0xFF];
    const body = head.concat(value);
    return hex(body.concat(sum(body)));
  }
  function readFrame(p) {
    const body = p.index >= 0 ? [0x0A, p.sc & 0xFF, 0x01, p.index] : [0x09, p.sc & 0xFF, 0x01];
    return hex(body.concat(sum(body)));
  }

  function storage() { try { return root.localStorage || null; } catch (_) { return null; } }
  function load() {
    const out = Object.assign({}, DEFAULTS);
    try {
      const s = JSON.parse(storage()?.getItem(KEY) || '{}');
      if (Number.isFinite(s.refreshMs)) out.refreshMs = Math.min(2000, Math.max(200, s.refreshMs));
      for (const k of ['showZones', 'showPoints', 'showMessages']) if (typeof s[k] === 'boolean') out[k] = s[k];
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
    if (prefs.colorPetrol) el.style.setProperty('--ac-pref-petrol', prefs.colorPetrol); else el.style.removeProperty('--ac-pref-petrol');
    if (prefs.colorGas) el.style.setProperty('--ac-pref-gas', prefs.colorGas); else el.style.removeProperty('--ac-pref-gas');
  }

  function parse(v) { try { return typeof v === 'string' ? JSON.parse(v) : v; } catch (_) { return null; } }
  function supported() { const b = root.OmegasAutoCal; return !!b && typeof b.readAutoCalPreference === 'function' && typeof b.applyAutoCalPreference === 'function'; }
  // Desconhecido = null (a tela mostra "—"). Nunca 0.
  function read(p) {
    if (!supported()) return null;
    try {
      const r = parse(root.OmegasAutoCal.readAutoCalPreference(JSON.stringify({ id: p.id, frame: readFrame(p) })));
      return r && r.ok && Number.isInteger(r.raw) ? r.raw : null;
    } catch (_) { return null; }
  }
  // Um toque: grava, exige ACK e relê. "Gravado" só se o readback for igual ao pedido.
  function write(p, raw) {
    const frame = writeFrame(p, raw);
    if (!frame) return { state: 'invalid', message: 'Valor fora da faixa ' + p.min + '–' + p.max + '.' };
    if (!supported()) return { state: 'unavailable', message: 'Gravação indisponível nesta versão do app.' };
    let r = null;
    try { r = parse(root.OmegasAutoCal.applyAutoCalPreference(JSON.stringify({ id: p.id, raw, frame, readFrame: readFrame(p) }))); } catch (_) { r = null; }
    if (!r || r.ok !== true || r.acked !== true) return { state: 'failed', message: 'A central não confirmou. Tente de novo.' };
    if (!Number.isInteger(r.readback)) return { state: 'unverified', message: 'Sem leitura de volta; não conferido.' };
    return r.readback === raw ? { state: 'saved', raw, message: 'Gravado' } : { state: 'mismatch', raw: r.readback, message: 'A central devolveu outro valor.' };
  }

  return { KEY, DEFAULTS, PARAMS, UNPROVEN, writeFrame, readFrame, load, save, applyToDom, supported, read, write, validRaw };
});
