export const SIMULATED_FIXTURE = Object.freeze({
  ONLINE: 'online',
  STALE: 'stale',
  RESTORING: 'restoring',
  ERROR: 'error',
  READBACK_FAIL: 'readback-fail',
  NO_DATA: 'no-data',
});

export const SIMULATED_FIXTURES = Object.freeze({
  [SIMULATED_FIXTURE.ONLINE]: Object.freeze({
    label: 'Online',
    telemetry: { valid: true, ageMs: 0, engineState: 'ONLINE' },
    learning: { state: 'CONSOLIDATED', label: 'Referência pronta para comparar' },
    failures: {},
  }),
  [SIMULATED_FIXTURE.STALE]: Object.freeze({
    label: 'Stale',
    telemetry: { valid: true, ageMs: 4200, engineState: 'ONLINE' },
    learning: { state: 'STALE', label: 'Dados antigos — releitura necessária' },
    failures: {},
  }),
  [SIMULATED_FIXTURE.RESTORING]: Object.freeze({
    label: 'Restoring',
    telemetry: { valid: true, ageMs: 80, engineState: 'ONLINE' },
    learning: { state: 'RESTORING', label: 'Restaurando aprendizado', reason: 'Telemetria continua independente durante o restore.' },
    failures: {},
  }),
  [SIMULATED_FIXTURE.ERROR]: Object.freeze({
    label: 'Error',
    telemetry: { valid: false, ageMs: -1, engineState: 'ERROR' },
    learning: { state: 'UNAVAILABLE', label: 'Learning indisponível' },
    failures: { predictor: 'Falha simulada ao carregar Predictor.' },
  }),
  [SIMULATED_FIXTURE.READBACK_FAIL]: Object.freeze({
    label: 'Readback fail',
    telemetry: { valid: true, ageMs: 120, engineState: 'ONLINE' },
    learning: { state: 'CONSOLIDATED', label: 'Referência pronta para comparar' },
    failures: { write: 'ACK recebido, mas readback divergiu. Operação não é sucesso.' },
  }),
  [SIMULATED_FIXTURE.NO_DATA]: Object.freeze({
    label: 'Sem dado',
    telemetry: { valid: false, ageMs: -1, engineState: 'SEM_DADO' },
    learning: { state: 'UNAVAILABLE', label: 'Sem referência ainda', reason: 'Continue a coleta normal; nenhum valor será inventado.' },
    failures: {},
  }),
});

export function selectedFixtureName() {
  try {
    const query = new URLSearchParams(globalThis.location?.search || '');
    const requested = String(query.get('fixture') || '').trim().toLowerCase();
    return SIMULATED_FIXTURES[requested] ? requested : SIMULATED_FIXTURE.ONLINE;
  } catch (_) {
    return SIMULATED_FIXTURE.ONLINE;
  }
}
