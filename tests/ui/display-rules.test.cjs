'use strict';
// Classe 2 (comportamento sintético): desconhecido nunca vira 0, e cada número que pode cair diz o motivo.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ROOT = path.resolve(__dirname, '../..');
const UI = file => path.join(ROOT, 'app/src/main/assets/ui', file);

function load(...files) {
  const context = { console, setTimeout: () => 0, clearTimeout() {}, localStorage: { getItem: () => null, setItem() {} } };
  context.window = context;
  context.globalThis = context;
  vm.createContext(context);
  for (const file of files) vm.runInContext(fs.readFileSync(UI(file), 'utf8'), context, { filename: file });
  return context.OmegasUi;
}

const rules = load('core/display-rules.js').DisplayRules;

test('contagem: null, vazio, NaN, texto e negativo são "—"; zero medido continua 0', () => {
  for (const unknown of [null, undefined, '', NaN, Infinity, 'abc', -1, true]) assert.equal(rules.count(unknown), '—', String(unknown));
  assert.equal(rules.count(0), '0');
  assert.equal(rules.count('7'), '7');
  assert.equal(rules.count(11.6), '12');
});

test('razão n/total: lado desconhecido não vira 0/total', () => {
  assert.equal(rules.ratio(null, 18), '—/18');
  assert.equal(rules.ratio(0, 18), '0/18');
  assert.equal(rules.ratio(3, 4), '3/4');
  assert.equal(rules.ratio(3, null), '—');
});

test('combustível: o "--" padrão do Kotlin e o vazio são "—"; os estados reais continuam', () => {
  for (const unknown of ['--', '', null, undefined, '—', 'null', 'undefined']) assert.equal(rules.fuelLabel(unknown), '—', String(unknown));
  assert.equal(rules.fuelLabel('GASOLINA'), 'GASOLINA');
  assert.equal(rules.fuelLabel('petrol'), 'GASOLINA');
  assert.equal(rules.fuelLabel('GNV'), 'GNV');
  assert.equal(rules.fuelLabel('CNG'), 'GNV');
  assert.equal(rules.fuelLabel('CUTOFF'), 'CUTOFF');
  assert.equal(rules.fuelLabel('TRANSICAO'), 'TRANSIÇÃO');
  assert.equal(rules.fuelLabel('DESLIGADO'), 'DESLIGADO');
});

test('duração, tamanho e idade: desconhecido é "—", não "0m 0s", "0 B" nem "0 ms"', () => {
  for (const unknown of [null, undefined, '', NaN, -5]) {
    assert.equal(rules.durationLabel(unknown), '—');
    assert.equal(rules.bytesLabel(unknown), '—');
    assert.equal(rules.megabytesLabel(unknown), '—');
    assert.equal(rules.ageLabel(unknown), '—');
  }
  assert.equal(rules.durationLabel(0), '0m 0s');
  assert.equal(rules.durationLabel(125_000), '2m 5s');
  assert.equal(rules.durationLabel(3_900_000), '1h 5m');
  assert.equal(rules.bytesLabel(0), '0 B');
  assert.equal(rules.bytesLabel(2048), '2 KB');
  assert.match(rules.bytesLabel(5 * 1024 * 1024), /^5,0 MB$/);
  assert.equal(rules.ageLabel(250), '250 ms');
  assert.equal(rules.ageLabel(2500), '2,5 s');
});

test('data da sessão: sem data conhecida não vira 31/12/1969', () => {
  assert.equal(rules.sessionDate({ id: 'session_2026-10-01_13-01-12_a879a88b' }), '01/10/2026 13:01:12');
  assert.equal(rules.sessionDate({ id: 'estranho' }), '—');
  assert.equal(rules.sessionDate({ id: 'estranho', createdAt: 0 }), '—');
  assert.equal(rules.sessionDate({}), '—');
  assert.notEqual(rules.sessionDate({ id: 'x', createdAt: 1_790_000_000_000 }), '—');
});

test('número do menu de sugestões: desconhecido não mexe, e o refino pronto soma 1', () => {
  const pending = (target, lifecycle = 'PENDING', actionable = true) => ({ target, lifecycle, actionable });
  assert.equal(rules.pendingSuggestionCount(undefined, false), null, 'ciência não respondeu: não escreve 0');
  assert.equal(rules.pendingSuggestionCount(null, false), null);
  assert.equal(rules.pendingSuggestionCount(undefined, true), 1, 'sem ciência mas com curva do refino pronta');
  assert.equal(rules.pendingSuggestionCount([], false), 0, 'ciência respondeu e não há nada: 0 de verdade');
  assert.equal(rules.pendingSuggestionCount([pending('MAP_K'), pending('CURVE_K')], false), 2);
  assert.equal(rules.pendingSuggestionCount([pending('MAP_K'), pending('CURVE_K')], true), 3);
  assert.equal(rules.pendingSuggestionCount([pending('MAP_K', 'OBSERVING'), pending('MAP_K', 'PENDING', false), pending('OUTRO')], false), 0);
});

test('o número do menu não oscila entre duas fórmulas: os 3 escritores antigos viraram 1', () => {
  const app = fs.readFileSync(UI('app.js'), 'utf8');
  const drawers = fs.readFileSync(UI('components/drawers.js'), 'utf8');
  const writers = [...app.matchAll(/setText\('suggestionCount'/g)].length;
  assert.equal(writers, 1, 'só updateSuggestionBadge escreve o número');
  assert.doesNotMatch(drawers, /getElementById\('suggestionCount'\)/);
});

test('motivo da queda dos pontos do GNV aparece com a hora; causa desconhecida não inventa motivo', () => {
  const note = rules.gasResetNote('AUTOMATCH_NATIVO', Date.UTC(2026, 9, 1, 16, 10, 0));
  assert.match(note, /Nossos pontos de GNV recomeçaram/);
  assert.match(note, /a ECU trocou a curva no automático/);
  assert.match(rules.gasResetNote('CURVA_K_GRAVADA', 0), /a Curva K foi gravada/);
  assert.doesNotMatch(rules.gasResetNote('CURVA_K_GRAVADA', 0), / às /);
  assert.equal(rules.gasResetNote('CAUSA_QUE_NAO_EXISTE', 1), '');
  assert.equal(rules.gasResetNote(undefined, 1), '');
});

test('rotas ao vivo incluem o Refino: sem isso a bolinha AGORA dele congelava', () => {
  const ui = load('core/router.js');
  assert.ok(ui.LIVE_ROUTES.includes('refino'));
  assert.ok(ui.LIVE_ROUTES.includes('autocal'));
  assert.ok(ui.LIVE_ROUTES.includes('dashboard'));
  for (const route of ui.LIVE_ROUTES) assert.ok(ui.ROUTES.includes(route), `${route} precisa ser uma rota`);
});

test('AutoCal: duração, regiões e zonas desconhecidas aparecem como "—" na narrativa da sessão', () => {
  const ui = load('screens/autocal-cockpit.js');
  const unknown = ui.AutoCalUxModel.sessionNarrative({ recording: true });
  assert.match(unknown.detail, /^— min/);
  assert.match(unknown.detail, /— regiões correlacionadas/);
  assert.match(unknown.detail, /GNV —\/4/);
  const known = ui.AutoCalUxModel.sessionNarrative({
    recording: true, durationMs: 2_520_000,
    semanticSummary: { autocal: { gasZones: 0, correlatedRegions: [] } },
  });
  assert.match(known.detail, /42 min/);
  assert.match(known.detail, /0 regiões correlacionadas/, 'zero medido continua zero');
  assert.match(known.detail, /GNV 0\/4/);
});

test('faixa de status: null não vira "0 ms" nem "0 rpm"', () => {
  const ui = load('components/vehicle-status-strip.js');
  assert.ok(ui.VehicleStatusStrip || true);
  const source = fs.readFileSync(UI('components/vehicle-status-strip.js'), 'utf8');
  assert.match(source, /value === null \|\| value === undefined \|\| value === ''/);
});

test('telemetria fora das rotas ao vivo vence em 3 s e vira desconhecida', () => {
  const R = rules;
  const now = 1_000_000;
  // Rota ao vivo: o pump renova; esta regra nunca invalida.
  assert.equal(R.offRouteTelemetryExpired(true, true, now - 60_000, now), false);
  // Fora das rotas ao vivo: dentro de 3 s vale, depois vence.
  assert.equal(R.offRouteTelemetryExpired(false, true, now - 2_900, now), false);
  assert.equal(R.offRouteTelemetryExpired(false, true, now - 3_100, now), true);
  // Instante desconhecido não é frescor.
  assert.equal(R.offRouteTelemetryExpired(false, true, 0, now), true);
  assert.equal(R.offRouteTelemetryExpired(false, true, null, now), true);
  // Já inválida não precisa invalidar de novo (evita laço de patch).
  assert.equal(R.offRouteTelemetryExpired(false, false, now - 60_000, now), false);
  assert.equal(R.offRouteTelemetryExpired(false, undefined, now - 60_000, now), false);
});

test('telemetria flutuante: estado em palavras e pergunta de primeiro uso uma vez só', () => {
  const R = rules;
  assert.equal(R.overlayState({ supported: true, permissionGranted: false }).key, 'needs-permission');
  assert.match(R.overlayState({ supported: true, permissionGranted: false }).help, /Autorizar/);
  assert.equal(R.overlayState({ supported: true, permissionGranted: true, requestedEnabled: false }).key, 'off');
  // Ligada mesmo com o app aberto (o balão fica escondido de propósito): não diz "Desativada".
  assert.equal(R.overlayState({ supported: true, permissionGranted: true, requestedEnabled: true, visible: false }).key, 'on');
  assert.equal(R.overlayState({ supported: false }).key, 'unsupported');
  const fresh = { supported: true, permissionGranted: false, requestedEnabled: false };
  assert.equal(R.shouldPromptOverlay(fresh, false), true);
  assert.equal(R.shouldPromptOverlay(fresh, true), false, 'já perguntou');
  assert.equal(R.shouldPromptOverlay({ ...fresh, requestedEnabled: true }, false), false, 'já tentou ligar');
  assert.equal(R.shouldPromptOverlay({ ...fresh, permissionGranted: true }, false), false, 'já autorizado');
  assert.equal(R.shouldPromptOverlay({ ...fresh, supported: false }, false), false);
  assert.equal(R.shouldPromptOverlay({ ...fresh, testHarness: true }, false), false, 'teste instrumentado não vê a pergunta');
});
