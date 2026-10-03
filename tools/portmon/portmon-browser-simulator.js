'use strict';

(function installPortmonBrowserSimulator(root) {
  if (!root || root.OmegasNative || !root.OmegasPortmonReplay) return;

  const corpus = {
    description: 'Amostra passiva do PortmonAUTOCAL.LOG real.',
    originalSha256: '4a70f5ae79b1d688c05bd169f3e6a588b52105580d24b8a72a5cff398a384c0b',
    compressedSha256: '202ff799ca3bba653986ce000ed69d4b3049fdf0aef6d614e605a3ca4d959deb',
    transactions: [
      { sequence: 1, request: '48 01 49', response: '48 01 49 53 22 65 03 32 1F 00 00 00 00 EC 06 00 80 2C B1 FA 09 39 93 01 00 00 00 00 00 00 00 00 00 EA 06 00 00 00 00 3D' },
      { sequence: 2, request: '48 01 49', response: '48 01 49 53 22 65 03 32 1F 00 00 00 00 E9 06 00 80 2C B1 F2 09 39 93 01 00 00 00 00 00 00 00 00 00 EA 06 00 00 00 00 32' },
      { sequence: 3, request: '48 01 49', response: '48 01 49 53 22 64 03 32 1F 00 00 00 00 E9 06 00 80 2C B1 F2 09 39 93 01 00 00 00 00 00 00 00 00 00 EA 06 00 00 00 00 31' },
      { sequence: 10, request: '29 1D 00 46', response: '29 1D 00 46 53 05 00 00 00 00 00 58' },
      { sequence: 15, request: '09 21 00 2A', response: '09 21 00 2A 53 01 00 54' },
    ],
  };

  const replay = new root.OmegasPortmonReplay.PortmonReplayAdapter(corpus);
  const rows = Array.from({ length: 12 }, (_, row) => Array.from({ length: 12 }, (_, column) => 110 + row + column));
  const extraRow = Array(12).fill(77);
  const axes = {
    petrolBins: [2, 2.5, 3, 3.5, 4.5, 6, 8, 10, 12, 14, 16, 18],
    rpmBins: [850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500],
  };
  const rawFrames = [];
  let connected = true;
  let mode = 'NORMAL';
  let mapReadResult = { ok: true, state: 'IDLE', busy: false };
  let writeStatus = { ok: true, state: 'IDLE', busy: false };

  const clone = value => JSON.parse(JSON.stringify(value));
  const hex = bytes => bytes.map(value => (value & 0xFF).toString(16).padStart(2, '0').toUpperCase()).join(' ');
  const checksum = bytes => bytes.reduce((sum, value) => (sum + value) & 0xFF, 0);
  const withChecksum = bytes => [...bytes, checksum(bytes)];
  const readRowFrame = row => withChecksum([0x2A, 0x54, 0x00, row]);
  const writeCellFrame = (row, column, value) => withChecksum([0x14, 0x54, 0x00, row, column, value]);
  const ackFrame = request => [...request, 0x53, 0x01, 0x00, checksum(request)];
  const hash = () => rows.flat().join('-');
  const json = value => JSON.stringify(value);
  const record = (kind, request, response, details = {}) => rawFrames.push({
    index: rawFrames.length + 1, kind, request: hex(request), response: response ? hex(response) : null, ...details,
  });

  function readMap() {
    if (!connected) return { ok: false, state: 'FAILED', busy: false, error: 'ECU simulada desconectada' };
    for (let row = 0; row < 13; row += 1) {
      const request = readRowFrame(row);
      const values = row < 12 ? rows[row] : extraRow;
      const response = withChecksum([...request, 0x53, ...values]);
      record('READ_MAP_ROW', request, response, { row });
    }
    return {
      ok: true, state: 'COMPLETED', busy: false, simulated: true,
      rows: clone(rows), extraRow: clone(extraRow), axes: clone(axes), hash: hash(), writableCells: 144,
    };
  }

  function writeBatch(payload) {
    const before = clone(rows);
    const frames = [];
    for (const item of payload) {
      if (!Number.isInteger(item.row) || item.row < 0 || item.row >= 12 ||
          !Number.isInteger(item.column) || item.column < 0 || item.column >= 12) {
        throw new Error('Célula fora da área editável');
      }
      if (before[item.row][item.column] !== item.current) throw new Error('Valor atual divergente do readback anterior');
      if (!Number.isInteger(item.target) || item.target < 0 || item.target > 255) throw new Error('Valor alvo fora de U8');
      const request = writeCellFrame(item.row, item.column, item.target);
      const response = mode === 'ACK_TIMEOUT' ? null : ackFrame(request);
      record('WRITE_MAP_CELL', request, response, { row: item.row, column: item.column, target: item.target });
      frames.push({ request: hex(request), response: response ? hex(response) : null });
    }
    if (mode === 'ACK_TIMEOUT') return { ok: false, code: 'ACK_TIMEOUT', frames };
    if (mode === 'ACK_REJECTED') return { ok: false, code: 'ACK_REJECTED', frames };
    payload.forEach(item => { rows[item.row][item.column] = item.target; });
    if (mode === 'READBACK_DIVERGENT' && payload.length) {
      const first = payload[0];
      rows[first.row][first.column] = Math.max(0, first.target - 1);
      return { ok: false, code: 'READBACK_DIVERGENT', frames };
    }
    return { ok: true, frames };
  }

  root.OmegasNative = {
    __portmonSimulator: true,
    getReleaseIdentity: () => json({ product: 'OMEGAS V7', versionName: 'Portmon LAB', channel: 'SIMULATOR', engine: 'MP48', debug: true }),
    getStatus: () => json({
      serviceRunning: true, engineRunning: connected, engineReady: connected, engineStuck: false,
      usbConnected: connected, usbDevice: 'ECU MP48 simulada', usbPermissionPending: false,
      ecuState: connected ? 'SIMULATED' : 'OFFLINE', fuelState: 'SIMULADO', rpm: 0,
      petrolMs: 0, gasMs: 0, mapBar: 0, directTelemetryAgeMs: connected ? 0 : -1,
      simulated: true, lastError: writeStatus.ok === false ? writeStatus.message : '',
    }),
    getFullEngineSnapshot() {
      const frame = connected ? replay.exchange('48 01 49') : null;
      return json({
        ok: connected, simulated: true, sequence: frame?.sequence || 0, updatedAt: Date.now(), ageMs: 0,
        live: { fuel: 'SIMULADO', rpm: 0, petrol_ms: 0, gas_ms_diagnostic: 0, load_bar: 0 },
        portmon: frame, rawFrames: rawFrames.slice(-20), source: replay.source,
      });
    },
    getLearningSyncStatus: () => json({ ok: true, state: 'SIMULATOR', reason: 'Bancada simulada ativa.' }),
    getLearningMaps: () => json({ ok: true, petrol: [], cng: [], comparisons: [], cells: [] }),
    getObdStatus: () => json({ connected: false, message: 'OBD fora desta bancada.' }),
    connectUsb() { connected = true; return true; },
    disconnectUsb() { connected = false; return true; },
    listUsbDevices: () => json([{ name: 'ECU MP48 simulada', simulated: true }]),
    startKMapRead() {
      mapReadResult = { ok: true, state: 'READING', busy: true };
      mapReadResult = readMap();
      return json({ ok: mapReadResult.ok, started: mapReadResult.ok, state: mapReadResult.state, error: mapReadResult.error });
    },
    getKMapReadResult: () => json(mapReadResult),
    readKMap: () => json(readMap()),
    startKBatchWrite(payloadJson, maxStep, pauseMs, reason) {
      if (!connected) return json({ ok: false, started: false, error: 'ECU simulada desconectada' });
      let payload;
      try { payload = JSON.parse(payloadJson); } catch (_) { return json({ ok: false, started: false, error: 'Lote inválido' }); }
      if (!Array.isArray(payload) || payload.length < 1 || payload.length > 16) return json({ ok: false, started: false, error: 'Lote deve conter de 1 a 16 células' });
      writeStatus = { ok: true, state: 'WRITING', busy: true, progress: 25, message: reason || 'Escrita simulada' };
      try {
        const result = writeBatch(payload);
        if (!result.ok) {
          writeStatus = { ok: false, state: 'FAILED', busy: false, progress: 100, ack: result.code !== 'ACK_TIMEOUT', readback: false, message: result.code, details: { frames: result.frames } };
        } else {
          const readback = readMap();
          writeStatus = { ok: true, state: 'BATCH_CONFIRMED', busy: false, progress: 100, ack: true, readback: true,
            message: 'Escrita simulada confirmada byte a byte por ACK e readback',
            details: { rows: readback.rows, extraRow: readback.extraRow, axes: readback.axes, newHash: readback.hash, cells: payload.length, frames: result.frames },
          };
        }
      } catch (error) {
        writeStatus = { ok: false, state: 'FAILED', busy: false, progress: 100, ack: false, readback: false, message: error.message || String(error) };
      }
      return json({ ok: true, started: true, simulated: true, cells: payload.length, maxStep, pauseMs });
    },
    getKWriteStatus: () => json(writeStatus),
    setPortmonSimulatorMode(nextMode) {
      const allowed = ['NORMAL', 'ACK_TIMEOUT', 'ACK_REJECTED', 'READBACK_DIVERGENT'];
      if (!allowed.includes(nextMode)) return json({ ok: false, error: 'Modo inválido' });
      mode = nextMode;
      return json({ ok: true, mode });
    },
    getPortmonSimulatorTrace: () => json({ ok: true, mode, frames: clone(rawFrames), source: replay.source, mapHash: hash() }),
    runEngineSelfTests() {
      replay.reset();
      return json({ ok: true, simulated: true, portmonFrames: [replay.exchange('48 01 49'), replay.exchange('48 01 49')], protocolTrace: clone(rawFrames), mapHash: hash() });
    },
    requestBluetoothPermission() {}, exportLearningArchive() {}, importLearningArchive() {}, exportLogs() {},
  };
}(typeof window !== 'undefined' ? window : globalThis));
