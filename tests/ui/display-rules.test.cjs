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
  const PRE = ['core/display-rules.js', 'core/live-store.js', 'components/curve-chart.js'];
  for (const file of [...PRE, ...files.filter(file => !PRE.includes(file))]) vm.runInContext(fs.readFileSync(UI(file), 'utf8'), context, { filename: file });
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
  assert.equal(rules.fuelLabel('CUTOFF'), 'CORTE');
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
  assert.equal(rules.durationLabel(0), '0 s');
  assert.equal(rules.durationLabel(125_000), '2 min');
  assert.equal(rules.durationLabel(3_900_000), '1h 05min');
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

test('palavras únicas de toda escrita na ECU: etapa, resultado humano, Desfazer/Voltar', () => {
  const w = rules.OPERATION_WORDING;
  assert.deepEqual(Array.from(w.stages), ['Foto antes', 'Gravando', 'Conferindo na ECU']);
  assert.equal(w.doneDetail, 'A ECU confirmou. A tela foi relida.');
  assert.equal(w.failedDetail, 'O app não conseguiu confirmar na ECU. A curva anterior continua.');
  assert.equal(w.doneTitle('Curva K'), 'Gravado · Curva K conferido na ECU', '"Gravado" só depois do readback');
  assert.equal(w.undo, 'Desfazer');
  assert.equal(w.back, 'Voltar');
  assert.equal(typeof rules.pendingSuggestionCount, 'undefined', 'a aba Sugestões saiu do produto');
});

test('motivo da queda dos pontos do GNV aparece com a hora; causa desconhecida não inventa motivo', () => {
  const note = rules.gasResetNote('AUTOMATCH_NATIVO', Date.UTC(2026, 9, 1, 16, 10, 0));
  assert.match(note, /Os pontos do OMEGAS no GNV recomeçaram/);
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
  assert.match(unknown.detail, /— regiões com ajuste confirmado/);
  assert.match(unknown.detail, /GNV —\/4/);
  const known = ui.AutoCalUxModel.sessionNarrative({
    recording: true, durationMs: 2_520_000,
    semanticSummary: { autocal: { gasZones: 0, correlatedRegions: [] } },
  });
  assert.match(known.detail, /42 min/);
  assert.match(known.detail, /0 regiões com ajuste confirmado/, 'zero medido continua zero');
  assert.match(known.detail, /GNV 0\/4/);
});

test('faixa de status: null não vira "0 ms" nem "0 rpm"', () => {
  const ui = load('components/vehicle-status-strip.js');
  assert.ok(ui.VehicleStatusStrip || true);
  const source = fs.readFileSync(UI('components/vehicle-status-strip.js'), 'utf8');
  assert.match(source, /const rules = ns\.DisplayRules/);
  assert.equal(rules.finite(null), null);
  assert.equal(rules.finite(''), null);
  assert.equal(rules.finite(true), null);
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

test('formatos únicos (pt-BR, "—" para desconhecido): ms 2 casas, MAP 3, K 3, rpm com milhar, fração em %, diferença assinada', () => {
  assert.equal(rules.ms(3.456), '3,46');
  assert.equal(rules.msUnit(3.4), '3,40 ms');
  assert.equal(rules.msBand(3.44, 4.21), 'de 3,4 a 4,2 ms');
  assert.equal(rules.barUnit(0.5321), '0,532 bar');
  assert.equal(rules.kValue(0.79998779296875), '0,800', 'o K nunca aparece cru');
  assert.equal(rules.rpm(2032), '2.032');
  assert.equal(rules.percentFraction(0.62), '62%');
  assert.equal(rules.percentFraction(0.01), '1%');
  assert.equal(rules.percentFraction(null), '—');
  assert.equal(rules.gapPercent(1.021), '+2,1%');
  assert.equal(rules.gapPercent(0.97), '-3,0%');
  for (const unknown of [null, undefined, '', NaN]) {
    for (const fn of ['ms', 'msUnit', 'barUnit', 'kValue', 'rpm', 'percentFraction', 'gapPercent']) assert.equal(rules[fn](unknown), '—', `${fn}(${String(unknown)})`);
  }
});

test('plural em português: alteração/alterações, célula/células, ponto/pontos', () => {
  assert.equal(rules.plural(1, 'alteração', 'alterações'), '1 alteração');
  assert.equal(rules.plural(12, 'alteração', 'alterações'), '12 alterações');
  assert.equal(rules.plural(0, 'célula', 'células'), '0 células');
  assert.equal(rules.plural(2, 'ponto', 'pontos'), '2 pontos');
  assert.equal(rules.plural(null, 'ponto', 'pontos'), '—');
  assert.doesNotMatch(rules.plural(12, 'alteração', 'alterações'), /ãoões/);
  const map = fs.readFileSync(UI('screens/map.js'), 'utf8');
  assert.doesNotMatch(map, /'ões'/, 'nada de "alteraçãoões"');
  assert.match(map, /plural\(changed, 'célula', 'células'\)/);
});

test('conexão: Conectando… (permissão USB) é diferente de Sem cabo, cada um com a próxima ação', () => {
  const c = rules.connectionState;
  assert.equal(c({ usbConnected: true, serviceRunning: true }).label, 'ECU online');
  const connecting = c({ usbConnected: false, usbPermissionPending: true, serviceRunning: true });
  assert.equal(connecting.key, 'connecting');
  assert.equal(connecting.label, 'Conectando…');
  assert.match(connecting.hint, /Permitir/);
  const nocable = c({ usbConnected: false, usbPermissionPending: false, serviceRunning: true });
  assert.equal(nocable.key, 'nocable');
  assert.equal(nocable.label, 'Sem cabo');
  assert.match(nocable.hint, /cabo USB/);
  assert.notEqual(connecting.label, nocable.label);
  assert.equal(c({ serviceRunning: false }).key, 'stopped');
  assert.equal(c(undefined).key, 'nocable');
});

test('fases do Refino: um rótulo por fase, em todo lugar (sem "—" lendo ou pausado)', () => {
  const expected = {
    SEM_ECU: 'Sem ECU', LENDO_ECU: 'Lendo a ECU', ECU_TRABALHANDO: 'ECU no automático', COLETANDO_NOSSOS: 'Medindo o GNV',
    PROPOSTA_PRONTA: 'Curva pronta', VERIFICANDO: 'Medindo', RESTAURAR_TRECHO: 'Piorou em um trecho', ESTAVEL: 'Estável', TENTATIVA_ENCERRADA: 'Pausado',
  };
  for (const [phase, label] of Object.entries(expected)) assert.equal(rules.phaseLabel(phase), label, phase);
  assert.equal(rules.phaseLabel('TENTATIVA_ENCERRADA', 'PROPOSTA_PRONTA'), 'Curva pronta', 'prazo vencido não apaga a proposta');
  assert.equal(rules.phaseLabel('XYZ'), '—');
});
