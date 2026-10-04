'use strict';
// Fix UI · Sessões e Ferramentas (achado 18). Teste de USO no DOM real.
const test = require('node:test');
const L = require('./wiring/lib.cjs');
const { assert } = L;
const W = require('./wiring/world.cjs');

const FRAMES = W.realFrames('ref_', f => f.fuel === 'GNV' && f.petrol_ms > 2);
const session = (n, extra) => ({ id: `session_2026-10-${String(n).padStart(2, '0')}_10-00-00`, reason: 'USB', durationMs: 60000 * n, bytes: 1000 * n, cngTicks: 5, petrolTicks: 5, ...(extra || {}) });

function boot(route, mutate) {
  const w = new W.World();
  w.setFrame(FRAMES[10]);
  if (mutate) mutate(w);
  const app = L.boot({ world: w });
  app.go(route);
  app.advance(3000);
  return app;
}
const screen = (app, name) => app.$(`[data-screen="${name}"]`).textContent.replace(/\s+/g, ' ');

test('18. duração nunca "—" enquanto está GRAVANDO (mesmo sem durationMs da ponte) e anda com o tempo', () => {
  const app = boot('sessions', w => { w.sessionStatus = { ...w.sessionStatus, recording: true, events: 4210, megabytes: 3.2 }; });
  const duration = () => app.$('.recorder-metrics b').textContent;
  assert.notEqual(duration(), '—');
  assert.match(duration(), /^\d+ (s|min)$/);
  app.advance(120000);
  assert.match(duration(), /^[1-9]\d* min$/, `anda: ${duration()}`);
  app.destroy();
});

test('18. milhares com ponto pt-BR ("4.210 eventos") e sem gravação a duração desconhecida continua "—"', () => {
  const app = boot('sessions', w => { w.sessionStatus = { ...w.sessionStatus, recording: true, events: 4210, durationMs: 90000 }; });
  assert.match(screen(app, 'sessions'), /4\.210 eventos/);
  app.destroy();
  const idle = boot('sessions', w => { w.sessionStatus = { ...w.sessionStatus, recording: false, events: 4210 }; delete w.sessionStatus.durationMs; });
  assert.equal(idle.$('.recorder-metrics b').textContent, '—');
  idle.destroy();
});

test('18. lista com mais de 20 sessões avisa que mostra as mais recentes (nada some em silêncio)', () => {
  const app = boot('sessions', w => { w.sessions = Array.from({ length: 27 }, (_, i) => session(i + 1)); });
  assert.equal(app.$$('.recorded-session-item').length, 20);
  assert.match(app.$('[data-sessions-truncated]').textContent, /Mostrando as 20 mais recentes de 27/);
  app.destroy();
  const few = boot('sessions', w => { w.sessions = [session(1)]; });
  assert.equal(few.$('[data-sessions-truncated]'), null);
  few.destroy();
});

test('18. Exportar ZIP continua um toque e não ocupa a largura toda do cartão', () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const css = fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/ui/styles-lote-f.css'), 'utf8');
  assert.match(css, /button\[data-export-session\] \{[^}]*justify-self: end; width: auto/);
  const app = boot('sessions', w => { w.sessions = [session(1)]; });
  const mark = app.world.mark();
  app.$('[data-export-session]').click(); app.flush();
  assert.equal(app.world.since(mark).filter(c => c.method === 'exportSession').length, 1);
  app.destroy();
});

test('18. Ferramentas: selo de saúde com palavra, "Último dado" em palavras do glossário, select com a opção do valor atual', () => {
  const app = boot('tools', w => { w.sessionStatus = { ...w.sessionStatus, settings: { ...w.sessionStatus.settings, telemetryEveryMs: 1500 } }; });
  const text = screen(app, 'tools');
  assert.match(text, /Tudo certo/);
  assert.doesNotMatch(text, /Telemetria sem telemetria/);
  assert.doesNotMatch(text, /\bOK\b/);
  const healthText = app.$('.background-health-grid').textContent;
  assert.match(healthText, /Último dado/);
  assert.doesNotMatch(healthText, /\d+ ms/, 'idade em palavras (agora / há N s), não em ms');
  const select = app.$('[data-session-telemetry]');
  assert.equal(select.value, '1500', 'o valor atual tem opção correspondente');
  assert.ok(select.options.some(o => o.attrs.get('value') === '1500'));
  assert.ok(select.options.some(o => o.attrs.get('value') === '1000'));
  app.destroy();
});

test('18. Ferramentas sem ECU: "Último dado" diz "sem dados" (nunca repete "telemetria")', () => {
  const app = boot('tools', w => { Object.assign(w.status, { usbConnected: false }); w.telemetry = { sequence: 1, updatedAt: 0, valid: false, live: {} }; });
  assert.match(app.$('.background-health-grid').textContent, /Último dado sem dados/);
  app.destroy();
});
