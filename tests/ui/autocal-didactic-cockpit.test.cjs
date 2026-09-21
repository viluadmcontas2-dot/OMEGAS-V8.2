const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
const css = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/styles-autocal-cockpit.css'), 'utf8');

const context = { console, setTimeout: () => 0, clearTimeout: () => {} };
context.globalThis = context;
vm.createContext(context);
vm.runInContext(source, context, { filename: 'autocal-cockpit.js' });

const model = context.OmegasUi?.AutoCalUxModel;
assert.ok(model, 'AutoCalUxModel deve ser exportado para teste do estado humano sem DOM');

const snapshot = {
  available: true,
  autoCalEnabled: 1,
  maxAutomatch: 3,
  nativeStatus: { autoMatchCount: 3 },
  nativeMaturityEvents: [
    { bandIndex: 4, correlationState: 'CORRELATED', counter: 10, threshold: 10, rpm: 1450, correlationConfidence: 0.92 },
  ],
  fields: [
    { key: 'ACQUIRED_ZONES_PETROL', status: 'VALID', rawValues: [1, 1, 1, 1], physicalValues: [1, 1, 1, 1] },
    { key: 'ACQUIRED_ZONES_GAS', status: 'VALID', rawValues: [1, 1, 1, 0], physicalValues: [1, 1, 1, 0] },
    { key: 'NUM_BUF_UPD_GAS', status: 'VALID', rawValues: Array.from({ length: 18 }, (_, i) => i < 16 ? 10 : 0), physicalValues: Array.from({ length: 18 }, (_, i) => i < 16 ? 10 : 0) },
    { key: 'PETR_INJ_TBP', status: 'VALID', rawValues: Array.from({ length: 30 }, (_, i) => 1000 + i), physicalValues: Array.from({ length: 30 }, (_, i) => 2 + i * 0.2) },
    { key: 'PETR_MNFLD_PRESS_RV', status: 'VALID', rawValues: Array.from({ length: 30 }, (_, i) => 300 + i), physicalValues: Array.from({ length: 30 }, (_, i) => 0.30 + i * 0.01) },
    { key: 'GAS_MNFLD_PRESS_RV', status: 'VALID', rawValues: Array.from({ length: 30 }, (_, i) => 310 + i), physicalValues: Array.from({ length: 30 }, (_, i) => 0.31 + i * 0.01) },
  ],
};

const human = model.humanState(snapshot, { state: 'READY', autoCalEnabled: 1, latestSnapshot: snapshot });
assert.equal(human.title, 'AutoCal adquirindo');
assert.equal(human.petrolZones, 4);
assert.equal(human.gasZones, 3);
assert.equal(human.petrolZoneFlags.join(','), 'true,true,true,true');
assert.equal(human.gasZoneFlags.join(','), 'true,true,true,false');
assert.match(human.progress, /Gasolina 4\/4 zonas/);
assert.match(human.progress, /GNV 3\/4 zonas/);
assert.match(human.autoMatch, /3 AutoMatch executados/);
assert.match(human.nextAction, /Aquisição habilitada/);

const pausedNativeSnapshot = { ...snapshot, autoCalEnabled: 0 };
const paused = model.humanState(snapshot, { state: 'PAUSED', autoCalEnabled: 0, latestSnapshot: pausedNativeSnapshot });
assert.equal(paused.title, 'AutoCal pausado');
assert.match(paused.nextAction, /Inicie a aquisição/);

const refs = model.referencePoints(snapshot);
assert.equal(refs.length, 30);
assert.deepEqual(
  { petrolMs: refs[0].petrolMs, petrolMapBar: refs[0].petrolMapBar, gasMapBar: refs[0].gasMapBar },
  { petrolMs: 2, petrolMapBar: 0.3, gasMapBar: 0.31 },
);

const sparseZoneSnapshot = {
  ...snapshot,
  fields: snapshot.fields.map(field => field.key === 'ACQUIRED_ZONES_GAS'
    ? { ...field, rawValues: [1, 0, 1, 0], physicalValues: [1, 0, 1, 0] }
    : field),
};
const sparseHuman = model.humanState(sparseZoneSnapshot, { state: 'READY', autoCalEnabled: 1, latestSnapshot: sparseZoneSnapshot });
assert.equal(sparseHuman.gasZones, 2);
assert.equal(sparseHuman.gasZoneFlags.join(','), 'true,false,true,false',
  'zonas esparsas devem preservar posição física, não compactar para as duas primeiras');


const conflictingSnapshot = {
  ...snapshot,
  nativeMaturityEvents: [],
  fields: snapshot.fields.map(field => {
    if (field.key === 'ACQUIRED_ZONES_PETROL' || field.key === 'ACQUIRED_ZONES_GAS') {
      return { ...field, rawValues: [1, 1, 1, 1], physicalValues: [1, 1, 1, 1] };
    }
    if (field.key === 'NUM_BUF_UPD_GAS') {
      return {
        ...field,
        rawValues: Array.from({ length: 18 }, (_, i) => (i === 4 || i === 8 ? 12 : 0)),
        physicalValues: Array.from({ length: 18 }, (_, i) => (i === 4 || i === 8 ? 12 : 0)),
      };
    }
    return field;
  }),
};
const authoritativeProjection = {
  ok: true,
  acquisitionZones: {
    petrol: [true, false, false, false],
    gas: [true, false, true, false],
  },
  correlationState: {
    correlatedBands: [4],
    retryableBands: [8],
  },
  correlation: [],
};
const projectedHuman = model.humanState(
  conflictingSnapshot,
  { state: 'READY', autoCalEnabled: 1, latestSnapshot: conflictingSnapshot },
  authoritativeProjection,
);
assert.equal(projectedHuman.petrolZones, 1, 'projeção Kotlin deve vencer vetor bruto conflitante de gasolina');
assert.equal(projectedHuman.gasZones, 2, 'projeção Kotlin deve vencer vetor bruto conflitante de GNV');
assert.equal(projectedHuman.gasZoneFlags.join(','), 'true,false,true,false');

const projectedBands = model.bandStrip(conflictingSnapshot, authoritativeProjection);
assert.equal(projectedBands[4].state, 'anchored', 'correlação persistente da projeção deve sobreviver sem evento novo');
assert.equal(projectedBands[8].state, 'mature', 'retry persistente deve permanecer distinto de simples atividade');

assert.equal(typeof model.referenceSourceLabel, 'function',
  'fonte da referência deve ser traduzida pelo modelo e ficar testável sem DOM');
assert.equal(
  model.referenceSourceLabel({ source: 'NATIVE_MONITOR', freshness: 'CURRENT_SESSION' }),
  'Monitor nativo · sessão atual',
);
assert.equal(
  model.referenceSourceLabel({ source: 'MANUAL_READER', freshness: 'CURRENT_SESSION' }),
  'Leitura manual · sessão atual',
);
assert.equal(
  model.referenceSourceLabel({ source: 'NONE', freshness: 'STALE_SESSION' }),
  'Sem fonte atual · sessão anterior rejeitada',
);

assert.equal(typeof model.bandNarrative, 'function', 'mensagem da região deve ser testável sem DOM');
const persistedCorrelationNarrative = model.bandNarrative({ ...projectedBands[4], event: null });
assert.match(persistedCorrelationNarrative, /confirmada nesta sessão/i);
assert.doesNotMatch(persistedCorrelationNarrative, /Nesta leitura/i,
  'correlação persistente não pode ser apresentada como evento da leitura atual');

const currentCorrelationNarrative = model.bandNarrative({
  ...projectedBands[4],
  event: { bandIndex: 4, correlationState: 'CORRELATED' },
});
assert.match(currentCorrelationNarrative, /Nesta leitura/i,
  'evento correlacionado realmente atual pode manter contexto temporal da leitura');

const persistedRetryNarrative = model.bandNarrative({ ...projectedBands[8], event: null });
assert.match(persistedRetryNarrative, /aguardando.*janela|nova janela/i,
  'retry persistente deve explicar o próximo passo sem afirmar correlação inexistente');

assert.match(source, /AutoCalUxModel\.humanState\(snapshot,\s*state,\s*this\.projection\)/,
  'render deve consumir a projeção Kotlin para zonas');
assert.match(source, /AutoCalUxModel\.bandStrip\([^)]*this\.projection/,
  'render/inspector de regiões devem consumir a projeção Kotlin');
assert.match(source, /this\.projection\?\.correlation/,
  'eventos de correlação exibidos devem vir da projeção Kotlin');

const bands = model.bandStrip(snapshot);
assert.equal(bands.length, 18);
assert.equal(bands[4].state, 'anchored');
assert.equal(bands[16].state, 'empty');
assert.equal(bands[13].zoneAcquired, true);
assert.equal(bands[16].zoneAcquired, false);

assert.equal(model.toggleAction(1), 'DISABLE_AUTO_CAL');
assert.equal(model.toggleAction(0), 'ENABLE_AUTO_CAL');
assert.equal(model.toggleAction(null), null, 'estado nativo ausente não pode virar comando de retomar coleta');
assert.equal(model.toggleAction(undefined), null, 'estado nativo indefinido não pode virar comando de retomar coleta');
const disconnected = model.humanState({ available: false }, { state: 'DISCONNECTED', latestSnapshot: { available: false } });
assert.equal(disconnected.enabled, null, 'DISCONNECTED deve preservar estado AutoCal desconhecido');

assert.equal(typeof model.updateChartView, 'undefined', 'gráfico físico não pode ter transform visual desacoplado dos eixos');
assert.equal(source.includes('data-autocal-chart-action="zoom-in"'), false);
assert.equal(source.includes('data-autocal-chart-action="zoom-out"'), false);
assert.equal(source.includes('data-autocal-chart-action="fit"'), false);
assert.equal(source.includes('Petrol Inj. (ms)'), true, 'eixo X precisa manter unidade física');
assert.equal(source.includes('MAP (bar)'), true, 'eixo Y precisa manter unidade física');
assert.equal(source.includes('data-autocal-history'), true, 'comparação com leitura anterior deve permanecer disponível');
assert.equal(source.includes('data-autocal-toggle'), true);
assert.equal(source.includes('data-autocal-band-index'), true);
assert.equal(source.includes('autocalHumanTitle'), true);
assert.equal(source.includes('autocalTechnicalDetails'), true);
assert.equal(source.includes('id="autocalReferenceSource"'), true,
  'fonte da referência deve existir somente no painel técnico existente');
assert.match(source, /autocalReferenceSource[^\n]*referenceSourceLabel|referenceSourceLabel\(this\.projection\)/,
  'render deve expor a fonte selecionada pela projeção Kotlin');
assert.equal(source.includes('AUTOMATCH ECU'), false);
assert.equal(source.includes('EVENTOS MADUROS'), false);
assert.equal(source.includes('RESET_ALL'), false);
assert.equal(source.includes('setInterval'), false);

assert.equal(css.includes('overflow-x: hidden'), true);
assert.match(css, /\.autocal-cockpit-view\s*\{[^}]*overflow-y:\s*auto/s, 'cockpit deve rolar verticalmente dentro da viewport em vez de cortar bandas e controles');
assert.equal(css.includes('container-type: inline-size'), true);
assert.equal(css.includes('min-height: 56px'), true);
assert.equal(css.includes('grid-template-columns: minmax(0, 1.45fr)'), false);
assert.equal(css.includes('min-height: 40px'), false);
assert.equal(source.includes('id="autocalZoneMeter"'), true, 'cockpit premium deve expor progresso visual das zonas');
assert.equal(source.includes('data-autocal-zone-petrol'), true);
assert.equal(source.includes('data-autocal-zone-gas'), true);
assert.equal(source.includes('autocal-band-legend'), true, 'estados das 18 faixas precisam de legenda visível');
assert.equal(source.includes('autocal-review-tech'), true, 'metadados técnicos da ação crítica devem ficar sob demanda');
assert.match(source, /data-autocal-ref-index=[^\n]+r="22"/, 'pontos do gráfico precisam de alvo de toque de pelo menos 44 px');
assert.match(css, /\.autocal-zone-meter\s*\{/, 'zone meter premium precisa de estilo dedicado');
assert.match(css, /\.autocal-inline-inspector\s*\{[^}]*font-size:\s*11px/s, 'inspector operacional não pode ficar microscópico');
assert.match(css, /\.autocal-human-copy strong\s*\{[^}]*font-size:\s*13px/s, 'próxima ação precisa ser legível a distância');


console.log('AUTOCAL_DIDACTIC_COCKPIT=PASS');

assert.equal(source.includes('>Ajustar</button>'), false, 'fit visual não pode parecer ajuste de ECU');
assert.equal(source.includes('>Ver tudo</button>'), false, 'fit/zoom não pode voltar sem transformar também os eixos físicos');
assert.equal(css.includes('touch-action: pan-y'), true, 'gráfico fixo deve devolver rolagem vertical à HMI');
assert.equal(source.includes('autocal-live-point'), true, 'cursor AGORA precisa de camada própria');

const staleManualWhilePaused = model.humanState(
  { ...snapshot, autoCalEnabled: 1 },
  { state: 'PAUSED', autoCalEnabled: 0, latestSnapshot: pausedNativeSnapshot },
);
assert.equal(staleManualWhilePaused.enabled, 0, 'snapshot manual antigo não pode reativar a aquisição');
assert.equal(staleManualWhilePaused.title, 'AutoCal pausado');
assert.equal(model.toggleAction(staleManualWhilePaused.enabled), 'ENABLE_AUTO_CAL');

const nativeActiveWhileManualStale = model.humanState(
  { ...snapshot, autoCalEnabled: 0 },
  { state: 'READY', autoCalEnabled: 1, latestSnapshot: snapshot },
);
assert.equal(nativeActiveWhileManualStale.enabled, 1, 'monitor nativo deve vencer snapshot manual antigo');
assert.equal(model.toggleAction(nativeActiveWhileManualStale.enabled), 'DISABLE_AUTO_CAL');

const cancelling = model.readNarrative({ state: 'CANCEL_REQUESTED', busy: true, message: 'Cancelamento solicitado', progress: 40 });
assert.equal(cancelling.busy, true);
assert.equal(cancelling.cancelling, true);
assert.equal(cancelling.title, 'Cancelando leitura');

const validAutoCalLive = model.livePoint(
  { valid: true, ageMs: 100, live: { rpm: 1300, petrol_ms: 4.8, load_bar: 0.44, level_raw: 999 } },
);
assert.equal(validAutoCalLive.rpm, 1300);
assert.equal(validAutoCalLive.petrolMs, 4.8);
assert.equal(validAutoCalLive.mapBar, 0.44);
assert.equal(Object.prototype.hasOwnProperty.call(validAutoCalLive, 'levelRaw'), false,
  'LEVELS pertence ao Dashboard/AGORA global e não pode vazar para o contexto AutoCal');
const invalidLive = model.livePoint({ valid: false, live: { rpm: 1300, petrol_ms: 4.8, load_bar: 0.44, level_raw: 173 } });
assert.equal(invalidLive, null, 'telemetria inválida não pode produzir cursor AGORA');
