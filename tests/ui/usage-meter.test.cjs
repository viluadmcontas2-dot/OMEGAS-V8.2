'use strict';

// Medidor de uso (dono, 2026-10-13): conta abas e toques no aparelho, sem rede e sem valores digitados; vai para o log da sessão.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { Meter, controlKey, slug } = require('../../app/src/main/assets/ui/core/usage-meter.js');

const el = (tag, attrs = {}, text = '', extra = {}) => {
  const node = {
    tagName: tag.toUpperCase(), id: attrs.id || '', dataset: {}, textContent: text, ...extra,
    getAttributeNames: () => Object.keys(attrs).filter(k => k.startsWith('data-')),
    matches: sel => sel.split(',').some(s => (s.trim().startsWith('.') && String(attrs.class || '').split(' ').includes(s.trim().slice(1))) || (s.trim().startsWith('[') && attrs[s.trim().slice(1, -1)] !== undefined)),
    closest(sel) {
      return sel.split(',').map(s => s.trim()).some(s => {
        if (s === 'button' || s === 'summary' || s === 'label' || s === 'select') return tag === s;
        if (s.startsWith('[data-') && !s.includes('=')) return attrs[s.slice(1, -1)] !== undefined;
        if (s.startsWith('.')) return String(attrs.class || '').split(' ').includes(s.slice(1));
        if (s === 'svg') return tag === 'svg' || !!extra.inSvg;
        return false;
      }) ? node : null;
    },
  };
  for (const [k, v] of Object.entries(attrs)) if (k.startsWith('data-route')) node.dataset.route = v;
  return node;
};

test('chave do controle: aba, id, atributo data-*, rótulo; nada sem identidade', () => {
  assert.equal(controlKey(el('button', { 'data-route': 'refino' }, 'Refino')), 'aba:refino');
  assert.equal(controlKey(el('button', { id: 'mapFromCurve' }, 'Curva K → Mapa K')), 'id:mapFromCurve');
  assert.equal(controlKey(el('button', { 'data-refino-preview': '' }, 'Ver alterações')), 'data-refino-preview');
  assert.equal(controlKey(el('button', {}, 'Reaprender GNV (apaga o atual)')), 'rotulo:reaprender-gnv-apaga-o-atual');
  assert.equal(controlKey(el('div', {})), null);
  assert.equal(controlKey(null), null);
  assert.equal(slug('Gravar no Mapa K!'), 'gravar-no-mapa-k');
});

test('conta toques e visitas/segundos por aba; delta é o que mudou desde o último envio', () => {
  let t = 1000;
  const meter = new Meter(() => t);
  meter.route('autocal', t);
  meter.tap('aba:refino'); meter.tap('aba:refino'); meter.tap('data-refino-preview');
  t += 30_000; meter.route('refino', t);
  t += 12_000;
  const d = meter.delta(t);
  assert.deepEqual(d.taps, { 'aba:refino': 2, 'data-refino-preview': 1 });
  assert.deepEqual(d.routes.autocal, { visits: 1, seconds: 30 });
  assert.deepEqual(d.routes.refino, { visits: 1, seconds: 12 });
  assert.equal(d.windowSeconds, 42);
});

test('envia só se houve uso, zera depois do envio aceito e mantém a aba aberta contando', () => {
  let t = 0;
  const meter = new Meter(() => t);
  const sent = [];
  assert.equal(meter.flush(d => sent.push(d), t), false, 'sem uso, nada é enviado');
  meter.route('map', t); meter.tap('mapa:celula');
  t += 5000;
  assert.equal(meter.flush(d => { sent.push(d); return true; }, t), true);
  assert.equal(sent.length, 1);
  assert.deepEqual(sent[0].routes.map, { visits: 1, seconds: 5 });
  t += 4000;
  const next = meter.delta(t);
  assert.deepEqual(next.taps, {});
  assert.deepEqual(next.routes.map, { visits: 0, seconds: 4 }, 'a aba continua aberta: só o tempo novo');
  // ponte que recusa (retorna false): nada se perde
  meter.tap('x');
  assert.equal(meter.flush(() => false, t), false);
  assert.equal(meter.taps.x, 1);
});

test('limite de chaves; envio por evento', () => {
  const meter = new Meter(() => 0);
  for (let i = 0; i < 300; i += 1) meter.tap('k' + i);
  assert.equal(Object.keys(meter.taps).length, 200);
  assert.equal(meter.due, undefined, 'envio é por evento (rota, toque, aba oculta), não por janela de tempo');
});

test('só contagens: sem rede, sem valor digitado, sem texto de entrada; a ponte grava no log da sessão', () => {
  const source = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/assets/ui/core/usage-meter.js'), 'utf8');
  assert.doesNotMatch(source, /fetch\(|XMLHttpRequest|sendBeacon|WebSocket|\.value\b|innerText|setInterval/);
  const api = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/assets/ui/core/native-api.js'), 'utf8');
  assert.match(api, /recordUiUsage\(delta\)[\s\S]*'recordUiUsage'/);
  const kotlin = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt'), 'utf8');
  assert.match(kotlin, /fun recordUiUsage\(json: String\): String/);
  assert.match(kotlin, /sessionRecorder\.record\("ui_usage", "webview"/);
});
