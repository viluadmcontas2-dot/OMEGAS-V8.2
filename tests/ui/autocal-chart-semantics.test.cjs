'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
const context = { console, setTimeout: () => 0, clearTimeout: () => {} };
context.globalThis = context;
vm.createContext(context);
vm.runInContext(source, context, { filename: 'autocal-cockpit.js' });
const model = context.OmegasUi.AutoCalUxModel;

assert.match(source, /Petrol Inj\. \(ms\)/);
assert.match(source, /MAP \(bar\)/);
assert.match(source, /autocal-axis-tick-x/);
assert.match(source, /autocal-axis-tick-y/);
assert.match(source, />SEM REFERÊNCIA</);
assert.match(source, /REFERÊNCIA FORA DA JANELA/);
assert.match(source, /referenceTimingSpanMs/);
assert.match(source, /referenceTimingLimitMs/);
assert.doesNotMatch(source, /Consulte a ECU novamente/i, 'AutoCal não deve instruir refresh manual da ECU');
assert.match(source, /Aguarde a próxima atualização automática da ECU/i);
assert.match(source, /'limite ' \+ Math\.round\(timingLimitMs\) \+ ' ms'/);
assert.equal(/\.concat\(live \? \[live\.mapBar\]/.test(source), false, 'AGORA não pode participar do domínio');

const points = [
  { index: 0, petrolMs: 2, petrolMapBar: 0.30, gasMapBar: 0.34, gasEquivalentMs: 2.2 },
  { index: 1, petrolMs: 10, petrolMapBar: 1.00, gasMapBar: 1.10, gasEquivalentMs: 10.4 },
];
const domain = model.referenceDomain(points, []);
assert.ok(domain.xMin < 2 && domain.xMax > 10.4);
assert.ok(domain.xMax < 12, 'domínio deve vir apenas da referência nativa dentro da janela operacional');
assert.equal(domain.yMin, 0, 'sem thresholds, fallback operacional começa em zero');
assert.equal(domain.yMax, 1.15, 'a escala MAP do AutoCal deve terminar em 1,15 bar');

const nearZeroDomain = model.referenceDomain([
  { index: 0, petrolMs: 0.05, petrolMapBar: 0.01, gasMapBar: 0.015, gasEquivalentMs: 0.06 },
  { index: 1, petrolMs: 0.40, petrolMapBar: 0.08, gasMapBar: 0.09, gasEquivalentMs: 0.45 },
], []);
assert.ok(nearZeroDomain.xMin >= 0,
  'padding visual não pode fabricar tempo de injeção negativo');
assert.ok(nearZeroDomain.yMin >= 0,
  'padding visual não pode fabricar MAP negativo');
assert.equal(nearZeroDomain.yMax, 1.15,
  'não deve existir expansão automática do eixo MAP acima de 1,15 bar');


const scale = {
  ...domain,
  xFor: value => value,
  yFor: value => value,
};
const live = model.projectLive({ petrolMs: 50, mapBar: 5 }, scale);
assert.equal(live.outOfRange, true);
assert.equal(live.x, domain.xMax);
assert.equal(live.y, domain.yMax);

const snapshot = {
  fields: [
    { key: 'PETR_INJ_TBP', status: 'VALID', physicalValues: [2, 10] },
    { key: 'PETR_MNFLD_PRESS_RV', status: 'VALID', physicalValues: [0.3, 1.0] },
    { key: 'GAS_MNFLD_PRESS_RV', status: 'VALID', physicalValues: [0.34, 1.1] },
  ],
};
const ref = model.referencePoints(snapshot, { points: [
  { index: 0, gasEquivalentTimeMs: 2.25 },
  { index: 1, gasEquivalentTimeMs: 10.5 },
]});
assert.equal(ref[0].gasEquivalentMs, 2.25);
assert.equal(ref[1].gasEquivalentMs, 10.5);


const freshTelemetry = {
  valid: true,
  ageMs: 7500,
  live: { petrol_ms: 4.50, load_bar: 0.45, rpm: 900, level_raw: 173, fuel: 'GNV' },
};
const freshAutoCalPoint = model.livePoint(freshTelemetry);
assert.equal(freshAutoCalPoint.petrolMs, 4.50);
assert.equal(freshAutoCalPoint.mapBar, 0.45);
assert.equal(freshAutoCalPoint.rpm, 900);
assert.equal(
  Object.prototype.hasOwnProperty.call(freshAutoCalPoint, 'levelRaw'),
  false,
  'LEVELS pertence ao Dashboard/AGORA global e deve ser ignorado pelo modelo AutoCal',
);
assert.equal(
  model.livePoint({ ...freshTelemetry, ageMs: 7501 }),
  null,
  'AGORA não pode continuar visível depois da janela de frescor adotada pela HMI',
);
assert.equal(
  model.livePoint({ ...freshTelemetry, valid: false, ageMs: 50 }),
  null,
  'telemetria explicitamente inválida não pode materializar AGORA',
);
assert.equal(
  model.livePoint({ ...freshTelemetry, ageMs: -1 }),
  null,
  'frescor desconhecido falha fechado para o cursor AGORA',
);
assert.match(source, /Telemetria com atraso/);
assert.match(source, /AGORA foi ocultado/);


const previousProjection = { sessionId: 101, referenceUsable: true };
const sameSessionProjection = { sessionId: 101, referenceUsable: true };
const nextSessionProjection = { sessionId: 202, referenceUsable: true };
const oldReferenceSnapshot = {
  snapshotHash: 'old-reference',
  fields: snapshot.fields,
};
const newReferenceSnapshot = {
  snapshotHash: 'new-reference',
  fields: snapshot.fields,
};

const sameSessionTransition = model.referenceTransition(
  previousProjection,
  sameSessionProjection,
  oldReferenceSnapshot,
  newReferenceSnapshot,
  { points: [] },
);
assert.equal(sameSessionTransition.sessionChanged, false);
assert.equal(sameSessionTransition.referenceChanged, true);
assert.equal(sameSessionTransition.resetSelection, true, 'nova referência deve invalidar a seleção antiga');
assert.equal(sameSessionTransition.previousPoints.length, 2, 'mesma sessão pode preservar a leitura imediatamente anterior');

const reconnectTransition = model.referenceTransition(
  previousProjection,
  nextSessionProjection,
  oldReferenceSnapshot,
  newReferenceSnapshot,
  { points: [] },
);
assert.equal(reconnectTransition.sessionChanged, true);
assert.equal(reconnectTransition.resetSelection, true, 'reconnect deve invalidar seleção da sessão anterior');
assert.equal(reconnectTransition.clearHistory, true, 'reconnect deve fechar Leitura anterior');
assert.equal(reconnectTransition.previousPoints.length, 0, 'Leitura anterior não pode atravessar sessão USB');

const referenceLostTransition = model.referenceTransition(
  { sessionId: 101, referenceUsable: true },
  { sessionId: 101, referenceUsable: false },
  oldReferenceSnapshot,
  { fields: [] },
  { points: [] },
);
assert.equal(referenceLostTransition.resetSelection, true,
  'perda da referência precisa invalidar seleção antiga mesmo sem novo hash');
assert.equal(referenceLostTransition.clearHistory, true,
  'histórico gráfico não pode sobreviver a um intervalo sem referência utilizável');

const referenceRegainedTransition = model.referenceTransition(
  { sessionId: 101, referenceUsable: false },
  { sessionId: 101, referenceUsable: true },
  { fields: [] },
  newReferenceSnapshot,
  { points: [] },
);
assert.equal(referenceRegainedTransition.resetSelection, true,
  'referência recuperada precisa começar sem seleção herdada do estado indisponível');
assert.equal(referenceRegainedTransition.previousPoints.length, 0,
  'referência recuperada após gap não pode inventar Leitura anterior');

console.log('AUTOCAL_CHART_SEMANTICS=PASS');


const zoneWindow = [
  { zone: 1, lower: 0.20, upper: 0.50 },
  { zone: 2, lower: 0.50, upper: 0.74 },
  { zone: 3, lower: 0.74, upper: 0.98 },
  { zone: 4, lower: 0.98, upper: 1.12 },
];
const focused = model.referenceDomain([
  { petrolMs: 3.0, petrolMapBar: 0.30, gasMapBar: 0.32, gasEquivalentMs: 3.1 },
  { petrolMs: 8.0, petrolMapBar: 1.10, gasMapBar: 1.12, gasEquivalentMs: 8.2 },
  { petrolMs: 20.0, petrolMapBar: 1.60, gasMapBar: 1.70, gasEquivalentMs: 21.0 },
], [], zoneWindow);
assert.equal(focused.yMin, 0.20, 'o gráfico deve começar no primeiro limiar físico quando conhecido');
assert.equal(focused.yMax, 1.15, 'o gráfico deve terminar em 1,15 bar sem opção de escala completa');
assert.ok(focused.xMax < 10, 'pontos acima de 1,15 bar não podem esticar também o eixo de injeção');
assert.equal(/expandir escala|escala completa|ver escala/i.test(source), false,
  'a HMI não deve oferecer modo de escala expandida');
