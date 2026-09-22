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
assert.match(source, /Consulte a ECU novamente/);
assert.match(source, /'limite ' \+ Math\.round\(timingLimitMs\) \+ ' ms'/);
assert.equal(/\.concat\(live \? \[live\.mapBar\]/.test(source), false, 'AGORA não pode participar do domínio');

const points = [
  { index: 0, petrolMs: 2, petrolMapBar: 0.30, gasMapBar: 0.34, gasEquivalentMs: 2.2 },
  { index: 1, petrolMs: 10, petrolMapBar: 1.00, gasMapBar: 1.10, gasEquivalentMs: 10.4 },
];
const domain = model.referenceDomain(points, []);
assert.ok(domain.xMin < 2 && domain.xMax > 10.4);
assert.ok(domain.xMax < 12, 'domínio deve vir apenas da referência nativa');

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
  ageMs: 2500,
  live: { petrol_ms: 4.50, load_bar: 0.45, rpm: 900, fuel: 'GNV' },
};
assert.equal(
  model.livePoint({ ...freshTelemetry, ageMs: 2501 }),
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


const typedInstrument = {
  liveNow: { petrolMs: 4.84, mapBar: 0.44, rpm: 2500, ageMs: 75, fuel: 'GNV' },
  reference: {
    petrol: [
      { index: 0, petrolMs: 2.0, mapBar: 0.20 },
      { index: 1, petrolMs: 4.0, mapBar: 0.40 },
    ],
    gas: [
      { index: 0, petrolMs: 2.0, mapBar: 0.18 },
      { index: 1, petrolMs: 4.0, mapBar: 0.37 },
    ],
  },
  acquisition: {
    petrolCurrent: [{ index: 0, petrolMs: 4.0, mapBar: 0.40 }],
    gasCurrent: [{ index: 0, petrolMs: 4.0, mapBar: 0.39 }],
    gasPrevious: [{ index: 0, petrolMs: 4.0, mapBar: 0.46 }],
  },
};
const typedReference = model.instrumentReferencePoints(typedInstrument);
assert.equal(typedReference.length, 2);
assert.equal(typedReference[1].petrolMapBar, 0.40);
assert.equal(typedReference[1].gasMapBar, 0.37);

const typedLive = model.instrumentLive(typedInstrument);
assert.equal(typedLive.petrolMs, 4.84);
assert.equal(typedLive.mapBar, 0.44);
assert.equal(model.instrumentLive({ liveNow: { petrolMs: 4, mapBar: 0.4, ageMs: 2501 } }), null);

const currentGas = model.instrumentAcquisitionPoints(typedInstrument, 'gasCurrent');
const previousGas = model.instrumentAcquisitionPoints(typedInstrument, 'gasPrevious');
assert.equal(currentGas[0].mapBar, 0.39);
assert.equal(previousGas[0].mapBar, 0.46);
assert.notEqual(currentGas[0].mapBar, previousGas[0].mapBar, 'GNV anterior e atual precisam permanecer camadas distintas');

assert.match(source, /this\.projection\?\.instrument/);
assert.match(source, /gasPreviousMarkup/);
assert.match(source, /gasCurrentMarkup/);
assert.match(source, /acquisitionMarkup\(gasPreviousAcquisition, 'gas-previous'/);
assert.match(source, /acquisitionMarkup\(gasCurrentAcquisition, 'gas-current'/);


const typedK = model.instrumentKPoints({
  kCurve: {
    points: [
      { index: 0, petrolMs: 2.0, factor: 1.0 },
      { index: 1, petrolMs: 4.5, factor: 1.025 },
      { index: 2, petrolMs: 6.0, factor: 0.99 },
    ],
  },
});
assert.equal(typedK.length, 3);
assert.equal(typedK[1].petrolMs, 4.5);
assert.equal(typedK[1].factor, 1.025);
assert.match(source, /instrumentKPoints/);
assert.match(source, /renderKCurve\(\)/);
assert.match(source, /PETR_INJ_TBP × MUL_ACT/);
assert.match(source, /sem alvo calculado pelo OMEGAS/);
assert.equal(source.includes('targetK'), false, 'Curve K nativa não pode ganhar alvo calculado no JS');


assert.equal(source.includes('LEVELS RAW'), false, 'LEVELS não pertence ao AutoCAL');
assert.equal(source.includes('autocalLiveLevel'), false, 'AutoCAL não pode renderizar nível do cilindro');
assert.equal(source.includes('levelsRaw'), false, 'projeção AutoCAL não pode carregar LEVELS');
assert.equal(source.includes('level_raw'), false, 'AutoCAL não pode consumir sinal de nível do cilindro');


const typedRegions = model.instrumentZoneRegions({
  zoneRegions: [
    { index: 0, lowMapBar: 0.000, highMapBar: 0.461, petrolAcquired: true, gasAcquired: false, label: 'Região 1' },
    { index: 1, lowMapBar: 0.461, highMapBar: 0.666, petrolAcquired: true, gasAcquired: true, label: 'Região 2' },
    { index: 2, lowMapBar: 0.666, highMapBar: 0.870, petrolAcquired: false, gasAcquired: true, label: 'Região 3' },
    { index: 3, lowMapBar: 0.870, highMapBar: 1.126, petrolAcquired: false, gasAcquired: false, label: 'Região 4' },
  ],
});
assert.equal(typedRegions.length, 4);
assert.equal(typedRegions[0].highMapBar, 0.461);
assert.equal(typedRegions[3].lowMapBar, 0.870);
assert.equal(typedRegions[3].highMapBar, 1.126);
assert.match(source, /instrumentZoneRegions/);
assert.match(source, /autocal-zone-region/);
assert.match(source, /R' \+ \(region\.index \+ 1\)/);
assert.equal(source.includes('/4 zonas GNV'), false);
assert.equal(source.includes(' de 4 zonas'), false);
assert.equal(source.includes('zona ok'), false);


const spatialBands = model.bandStrip(
  {
    fields: [
      {
        key: 'NUM_BUF_UPD_GAS',
        status: 'VALID',
        rawValues: Array.from({ length: 18 }, (_, index) => index === 17 ? 3 : 0),
      },
    ],
  },
  {
    instrument: {
      zoneRegions: [
        { index: 0, lowMapBar: 0.000, highMapBar: 0.461, petrolAcquired: false, gasAcquired: true, label: 'Região 1' },
        { index: 1, lowMapBar: 0.461, highMapBar: 0.666, petrolAcquired: false, gasAcquired: false, label: 'Região 2' },
        { index: 2, lowMapBar: 0.666, highMapBar: 0.870, petrolAcquired: false, gasAcquired: false, label: 'Região 3' },
        { index: 3, lowMapBar: 0.870, highMapBar: 1.126, petrolAcquired: false, gasAcquired: false, label: 'Região 4' },
      ],
      acquisition: {
        gasCurrent: [{ index: 17, petrolMs: 7.2, mapBar: 0.400 }],
      },
    },
  },
);
assert.equal(spatialBands[17].zone, 0,
  'posição 18 com MAP 0.400 deve pertencer espacialmente à R1, não à região inferida pelo índice');
assert.equal(spatialBands[17].zoneAcquired, true);
assert.equal(spatialBands[17].mapBar, 0.400);
assert.equal(source.includes('zoneForBand('), false,
  'consumer não pode manter agrupamento 18→4 hardcoded por índice');


assert.match(source, /Regiões registradas neste ciclo/);
assert.match(source, /registrada pela ECU neste ciclo/);
assert.equal(source.includes('flag ativa'), false);
assert.equal(source.includes('flag inativa'), false);


assert.match(source, /GNV · época anterior/);
assert.match(source, /Comparar referência anterior/);
assert.match(source, /GNV · época anterior/);
assert.equal(source.includes('Mostrar leitura anterior'), false);
assert.equal(source.includes('Buffer GNV anterior'), false);


const typedEpoch = model.instrumentEpoch({
  epoch: {
    autoMatchExecuted: 2,
    maxAutoMatch: 3,
    gasCurrentRole: 'CURRENT_NATIVE_AUTOMATCH_EPOCH',
    gasPreviousRole: 'PREVIOUS_NATIVE_AUTOMATCH_EPOCH',
    authority: 'ECU_READ',
  },
});
assert.equal(typedEpoch.autoMatchExecuted, 2);
assert.equal(typedEpoch.maxAutoMatch, 3);
assert.equal(typedEpoch.gasPreviousRole, 'PREVIOUS_NATIVE_AUTOMATCH_EPOCH');
assert.equal(typedEpoch.authority, 'ECU_READ');

const humanFromTypedEpoch = model.humanState(
  {},
  {
    latestSnapshot: {
      fields: [
        { key: 'NUM_AUTOMATCH_EXECUTED', rawValues: [1] },
        { key: 'MAX_AUTOMATCH', rawValues: [9] },
      ],
      autoCalEnabled: 1,
    },
  },
  {
    instrument: {
      epoch: {
        autoMatchExecuted: 2,
        maxAutoMatch: 3,
        authority: 'ECU_READ',
      },
    },
  },
);
assert.equal(humanFromTypedEpoch.autoMatchCount, 2,
  'typed instrument epoch must outrank raw snapshot fallback');
assert.equal(humanFromTypedEpoch.maxAutoMatch, 3,
  'typed max AutoMatch must outrank raw snapshot fallback');


const epochProjection = (sessionId, autoMatchExecuted, factors) => ({
  ok: true,
  sessionId,
  instrument: {
    epoch: { autoMatchExecuted, maxAutoMatch: 3, authority: 'ECU_READ' },
    kCurve: {
      points: factors.map((factor, index) => ({
        index,
        petrolMs: [2.0, 4.5, 6.0][index],
        factor,
      })),
    },
  },
});

const epochBaseline = model.instrumentKPoints(epochProjection(101, 2, [1.0, 1.01, 0.99]).instrument);
const nativeEpochChange = model.epochTransition(
  epochProjection(101, 2, [1.0, 1.01, 0.99]),
  epochProjection(101, 3, [1.01, 1.02, 1.0]),
  epochBaseline,
);
assert.equal(nativeEpochChange.counterChanged, true);
assert.equal(nativeEpochChange.changed, true);
assert.equal(nativeEpochChange.rollover, false);
assert.equal(nativeEpochChange.reason, 'COUNTER_AND_K_CHANGED');
assert.equal(nativeEpochChange.previousKPoints.length, 3);
assert.equal(nativeEpochChange.currentKPoints.length, 3);

const nativeEpochRollover = model.epochTransition(
  epochProjection(101, 3, [1.01, 1.02, 1.0]),
  epochProjection(101, 0, [0.99, 1.0, 1.01]),
  model.instrumentKPoints(epochProjection(101, 3, [1.01, 1.02, 1.0]).instrument),
);
assert.equal(nativeEpochRollover.changed, true);
assert.equal(nativeEpochRollover.rollover, true, '3→0 must remain a native epoch transition');

const counterOnlyChange = model.epochTransition(
  epochProjection(101, 2, [1.0, 1.01, 0.99]),
  epochProjection(101, 3, [1.0, 1.01, 0.99]),
  epochBaseline,
);
assert.equal(counterOnlyChange.counterChanged, true);
assert.equal(counterOnlyChange.changed, false,
  'counter change without Curve K delta must not be presented as native K adjustment');
assert.equal(counterOnlyChange.reason, 'COUNTER_CHANGED_WITHOUT_K_DELTA');

const crossSessionEpoch = model.epochTransition(
  epochProjection(101, 2, [1.0, 1.01, 0.99]),
  epochProjection(202, 3, [1.01, 1.02, 1.0]),
  epochBaseline,
);
assert.equal(crossSessionEpoch.changed, false);
assert.equal(crossSessionEpoch.reason, 'SESSION_CHANGED');

assert.match(source, /previousKPoints/);
assert.match(source, /autocal-k-line previous/);
assert.match(source, /Novo epoch nativo/);
assert.match(source, /COUNTER_CHANGED_WITHOUT_K_DELTA/);
