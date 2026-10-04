'use strict';
// Fix UI · telemetria parada, conexão, USB bloqueado, Agora e faixa (achados 2, 6, 8, 9, 12, 17). Teste de USO no DOM real.
const test = require('node:test');
const L = require('./wiring/lib.cjs');
const { assert } = L;
const W = require('./wiring/world.cjs');

const FRAMES = W.realFrames('ref_', f => f.fuel === 'GNV' && f.petrol_ms > 2);
const dash = app => app.$('[data-screen="dashboard"]').textContent.replace(/\s+/g, ' ');
const tiles = app => ['dashHeroPetrol', 'dashRpm', 'dashMap', 'dashFuel'].map(id => app.byId(id).textContent);

function boot(route = 'dashboard', mutate) {
  const w = new W.World();
  w.setFrame(FRAMES[10]);
  if (mutate) mutate(w);
  const app = L.boot({ world: w });
  app.go(route);
  return app;
}
/** A ECU manda quadros por `seconds` s (um por 250 ms). */
function feed(app, seconds, i = 20) {
  for (let t = 0; t < seconds * 4; t += 1) { app.world.setFrame(FRAMES[(i + t) % FRAMES.length]); app.advance(250); }
}

test('2. valid=true com a idade crescendo: normal → atrasado (cinza) → "—" + "Sem dados há N s" → volta quando o quadro chega', () => {
  const app = boot();
  feed(app, 2);
  assert.ok(tiles(app).every(t => t !== '—'), `com dado fresco todos os 4 aparecem: ${tiles(app)}`);
  assert.match(dash(app), /Leitura em tempo real/);
  assert.equal(app.byId('vehicleStatusStrip').dataset.stale, 'false');
  // a ECU para de mandar (valid continua true; a idade cresce)
  app.advance(2000);
  assert.equal(app.world.telemetry.valid, true);
  assert.equal(app.byId('vehicleStatusStrip').dataset.stale, 'true', 'de 1,5 a 3 s: cinza');
  assert.ok(tiles(app).every(t => t !== '—'), 'atrasado ainda mostra o último valor, em cinza');
  assert.match(dash(app), /Dados atrasados/);
  app.advance(2500);
  assert.deepEqual(tiles(app), ['—', '—', '—', '—'], 'acima de 3 s o valor velho vira "—" (nunca finge ser de agora)');
  assert.match(dash(app), /Sem dados há \d+ s · confira o cabo/);
  assert.doesNotMatch(dash(app), /expirada|Ajustes permanecem bloqueados/);
  assert.equal(app.byId('globalEcu').dataset.online, 'false', 'chip global não diz "ECU online" sem dado');
  assert.match(app.byId('globalEcu').textContent, /Sem dados da ECU/);
  assert.equal(app.byId('globalFuel').textContent, '—');
  app.advance(8000);
  assert.match(dash(app), /Sem dados há (1\d|\d{2,}) s/, 'o contador continua andando depois de 10 s');
  // volta
  feed(app, 1);
  assert.ok(tiles(app).every(t => t !== '—'));
  assert.equal(app.byId('globalEcu').dataset.online, 'true');
  assert.match(app.byId('globalEcu').textContent, /ECU online/);
  L.assertClean(app, 'telemetria parada');
  app.destroy();
});

test('2. Mapa K: a célula AGORA some ("—") com a leitura velha e volta com a leitura nova; nunca fica um valor velho', () => {
  const app = boot('map');
  app.settle(2);
  feed(app, 1);
  assert.match(app.byId('mapLiveLabel').textContent, /^\d+×\d+$/);
  app.advance(4000);
  assert.equal(app.byId('mapLiveLabel').textContent, '—');
  feed(app, 1);
  assert.match(app.byId('mapLiveLabel').textContent, /^\d+×\d+$/);
  app.destroy();
});

test('6. faixa do cabeçalho: sem "FRESCOR"/"SERVIÇO", idade em palavras (agora / há N s), nunca "ms"', () => {
  const app = boot('curve');
  feed(app, 3);
  const strip = app.byId('vehicleStatusStrip');
  assert.ok(strip);
  const text = strip.textContent;
  assert.doesNotMatch(text, /FRESCOR|SERVIÇO/);
  assert.match(strip.querySelector('[data-vehicle-fact="age"] b').textContent, /^(agora|há \d+ s)$/);
  assert.notEqual(strip.querySelector('[data-vehicle-fact="rpm"] b').textContent, '—', 'Curva K com ECU enviando: a faixa não fica toda "—"');
  assert.notEqual(strip.querySelector('[data-vehicle-fact="fuel"] b').textContent, '—');
  assert.match(strip.querySelector('[data-vehicle-fact="petrol"] b').textContent, /ms$/);
  app.destroy();
});

test('6. faixa em Ferramentas também tem dados, e "atrasado" / "sem dados" aparecem na faixa', () => {
  const app = boot('tools');
  feed(app, 4);
  const get = k => app.byId('vehicleStatusStrip').querySelector(`[data-vehicle-fact="${k}"] b`).textContent;
  assert.notEqual(get('rpm'), '—');
  app.advance(2200);
  assert.match(get('age'), /atrasado/);
  app.advance(2500);
  assert.equal(get('rpm'), '—');
  assert.match(get('age'), /sem dados/);
  app.destroy();
});

test('6. USB com permissão negada: "USB bloqueado" com botão que pede de novo (connectUsb), diferente de "Sem cabo"', () => {
  const app = boot('dashboard', w => {
    Object.assign(w.status, { usbConnected: false, usbPermissionDenied: true, engineRunning: false, fuelState: '--' });
    w.telemetry = { sequence: 1, updatedAt: 0, valid: false, live: {} };
  });
  app.settle(2);
  assert.match(app.byId('globalEcu').textContent, /USB bloqueado/);
  assert.equal(app.byId('globalEcu').hasAttribute('hidden'), true, 'a palavra vira o botão (sem texto repetido)');
  const allow = app.byId('globalUsbAllow');
  assert.equal(allow.hasAttribute('hidden'), false);
  assert.match(allow.textContent, /USB bloqueado/);
  assert.match(allow.textContent, /toque para permitir/);
  const mark = app.world.mark();
  allow.click(); app.flush();
  assert.equal(app.world.since(mark).filter(c => c.method === 'connectUsb').length, 1, 'o toque chama connectUsb');
  assert.match(dash(app), /USB bloqueado/);
  assert.equal(app.$('[data-screen="dashboard"] [data-usb-allow]').hasAttribute('hidden'), false);
  // sem negação: "Sem cabo", sem botão
  const plain = boot('dashboard', w => {
    Object.assign(w.status, { usbConnected: false, engineRunning: false, fuelState: '--' });
    w.telemetry = { sequence: 1, updatedAt: 0, valid: false, live: {} };
  });
  plain.settle(2);
  assert.match(plain.byId('globalEcu').textContent, /Sem cabo/);
  assert.equal(plain.byId('globalUsbAllow').hasAttribute('hidden'), true);
  app.destroy(); plain.destroy();
});

test('12. trilho: texto da ECU/combustível ≥ 24 px e selo "—" sem caixa quando não há leitura', () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const css = fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/ui/styles-lote-f.css'), 'utf8');
  const block = css.slice(css.indexOf('Fix UI · aviso, trilho, faixa'));
  assert.match(block, /rail-status span \{[^}]*font-size: var\(--text-critical\)/);
  assert.match(block, /rail-status b \{[^}]*font-size: var\(--text-critical\)/);
  assert.match(block, /rail-status b\[data-fuel="—"\] \{[^}]*background: transparent/);
  const app = boot();
  app.advance(8000);
  assert.equal(app.byId('globalFuel').dataset.fuel, '—');
  app.destroy();
});

test('17. Agora apresenta intenção e cobertura; telemetria global sem duplicar mostradores', () => {
  const app = boot();
  assert.equal(app.$$('.now-tile').length, 0);
  for (const id of ['dashHeroPetrol','dashRpm','dashMap','dashFuel']) assert.ok(app.byId(id).closest('#vehicleStatusStrip'),id+' único no topo');
  assert.doesNotMatch(dash(app), /CÉLULA/);
  assert.equal(app.byId('dashCell'), null);
  const fs = require('node:fs');
  const path = require('node:path');
  const css = fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/ui/styles-dashboard-now.css'), 'utf8');
  assert.doesNotMatch(css, /\[data-empty="true"\]\s*\{[^}]*opacity/);
  app.destroy();
});

test('8. cabo some e volta: Mapa K e Curva K releem sozinhos (ler é automático); nunca gravam', () => {
  const app = boot('curve');
  feed(app, 2);
  app.settle(2);
  const reads = () => app.world.callsOf('startCurveRead').length;
  const writes = () => app.world.calls.filter(c => ['startCurveBatchWrite', 'startCurveRestoreWrite', 'startCurveReset', 'startMapBatchWrite'].includes(c.method)).length;
  const before = reads();
  Object.assign(app.world.status, { usbConnected: false });
  app.world.telemetry = { ...app.world.telemetry, valid: false, updatedAt: 0 };
  app.advance(2500);
  assert.equal(app.byId('globalEcu').dataset.online, 'false');
  Object.assign(app.world.status, { usbConnected: true });
  feed(app, 2);
  app.settle(2);
  assert.equal(reads(), before + 1, 'a Curva K releu uma vez ao voltar');
  assert.equal(writes(), 0);
  assert.match(app.byId('curveSourceStatus').textContent, /ECU confirmada/);
  app.destroy();
});

test('8. Mapa K relê ao voltar o cabo; fora da aba, relê ao entrar', () => {
  const app = boot('map');
  feed(app, 2);
  app.settle(3);
  const reads = () => app.world.callsOf('startKMapRead').length;
  const before = reads();
  app.go('tools');
  Object.assign(app.world.status, { usbConnected: false });
  app.world.telemetry = { ...app.world.telemetry, valid: false, updatedAt: 0 };
  app.advance(2500);
  Object.assign(app.world.status, { usbConnected: true });
  feed(app, 2);
  assert.equal(reads(), before, 'fora da aba não lê');
  app.go('map');
  app.settle(3);
  assert.equal(reads(), before + 1, 'ao entrar na aba relê (o cabo tinha caído)');
  app.destroy();
});
