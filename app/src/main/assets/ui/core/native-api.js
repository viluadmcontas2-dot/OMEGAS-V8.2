(function (root) {
  'use strict';

  const ns = root.OmegasUi = root.OmegasUi || {};
  const PETROL_BINS = [2, 2.5, 3, 3.5, 4.5, 6, 8, 10, 12, 14, 16, 18];
  const RPM_BINS = [850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500];

  function parse(value, fallback) {
    if (value === null || value === undefined || value === '') return fallback;
    if (typeof value !== 'string') return value;
    try { return JSON.parse(value); } catch (_) { return fallback; }
  }

  function invoke(target, name, args, fallback) {
    const fn = target && target[name];
    if (typeof fn !== 'function') return fallback;
    try { return parse(fn.apply(target, args || []), fallback); }
    catch (error) { return { ok: false, error: error && error.message ? error.message : String(error) }; }
  }

  function demoTelemetry() {
    const phase = (Date.now() / 1000) % 12;
    const rpm = Math.round(1700 + Math.sin(phase) * 620);
    const petrolMs = 4.15 + Math.sin(phase * 0.6) * 0.34;
    const gasMs = petrolMs * (1 + Math.sin(phase * 0.35) * 0.012);
    const mapBar = 0.42 + Math.max(0, Math.sin(phase * 0.5)) * 0.31;
    return {
      ok: true, valid: true, demo: true, ageMs: 42, telemetryAgeMs: 42, updatedAt: Date.now(),
      live: {
        rpm, petrol_ms: petrolMs, gas_ms_diagnostic: gasMs, load_bar: mapBar, fuel: 'GNV',
        sample_state: 'FORMING_SAMPLE', sample_reason: 'Formando amostra 7/10 leituras',
        sample_frame_count: 7, sample_minimum_frames: 6, sample_desired_frames: 10,
        sample: {
          state: 'FORMING_SAMPLE', reason: 'Formando amostra 7/10 leituras', reason_code: 'FORMING_SAMPLE',
          frame_count: 7, minimum_frames: 6, desired_frames: 10, duration_ms: 1780,
          learning_eligible: false, fuel_confirmed: 'GNV', window_age_ms: 1780,
          window_budget_ms: 3000, cell_row: 4, cell_column: 3, cell_key: '4:3', quality: 0.82,
        },
      },
      interpolation: {
        valid: true, educationalOnly: true, affectsLearning: false, affectsCalibration: false,
        method: 'BILINEAR_RPM_X_PETROL_MS', rpm, petrolMs, mapBar,
        cell: {
          row: 4, column: 3,
          continuousWeights: [
            { row: 4, column: 3, weight: 0.42 }, { row: 4, column: 4, weight: 0.28 },
            { row: 5, column: 3, weight: 0.18 }, { row: 5, column: 4, weight: 0.12 },
          ],
        },
      },
    };
  }

  function demoMap() {
    const rows = Array.from({ length: 12 }, (_, row) =>
      Array.from({ length: 12 }, (_, column) => 116 + row * 2 + Math.round(column * 0.7)));
    return {
      ok: true, state: 'COMPLETED', demo: true, rows, extraRow: Array(12).fill(0),
      axes: { petrolBins: PETROL_BINS.slice(), rpmBins: RPM_BINS.slice() },
      hash: 'browser-demo', writableCells: 144, sessionConfirmed: true,
    };
  }

  function demoCurve() {
    const points = Array.from({ length: 30 }, (_, index) => ({
      index,
      petrolMs: 1.5 + index * 0.35,
      factor: 1.02 + Math.sin(index / 6) * 0.08,
      factorRaw: Math.round((1.02 + Math.sin(index / 6) * 0.08) * 16384),
    }));
    return { ok: true, demo: true, points, pointCount: 30, minimumFactor: 0.60, maximumFactor: 3.99 };
  }

  function demoLearning() {
    const cells = [];
    for (let row = 0; row < 12; row += 1) {
      for (let column = 0; column < 12; column += 1) {
        if ((row + column) % 3 === 0) {
          cells.push({
            row, column, key: `${row}:${column}`, samples: 12 + ((row * 7 + column * 5) % 55),
            visits: 2 + ((row + column) % 4), sessions: 1 + ((row + column) % 2),
            confidence: 0.55 + (((row + column) % 5) * 0.09), stage: 'ACCEPTED',
          });
        }
      }
    }
    const petrolCurve = Array.from({ length: 18 }, (_, index) => ({
      mapBar: 0.20 + index * 0.05,
      petrolMs: 2.0 + index * 0.24,
      confidence: 0.72,
      confidenceStage: 'ACCEPTED',
      uniqueVisits: 4,
      effectiveSamples: 18,
      series: 'PETROL',
    }));
    const cngCurve = petrolCurve.map((item, index) => ({
      ...item,
      petrolMs: item.petrolMs * (1 + 0.035 * Math.sin(index / 4)),
      confidence: 0.68,
      series: 'CNG',
    }));
    const kFactorSuggestions = Array.from({ length: 30 }, (_, index) => ({
      index,
      petrolMs: 1.5 + index * 0.35,
      actionable: index % 4 === 0,
      confidence: 0.70,
      confidenceStage: 'ACCEPTED',
      suggestedDeltaPercent: index % 8 === 0 ? 1.2 : -0.8,
      decisionReason: 'Demonstração de tendência global',
    }));
    return {
      ok: true, demo: true,
      grid: { rows: 12, columns: 12, petrolBins: PETROL_BINS, rpmBins: RPM_BINS },
      cells,
      petrol: cells.map(item => ({ ...item, fuel: 'PETROL' })),
      cng: cells.map(item => ({ ...item, fuel: 'CNG', epoch: 1 })),
      comparisons: cells.map((item, index) => ({ ...item, errorPercent: ((index % 9) - 4) * 0.9 })),
      assistedCalibration: {
        comparisonCount: cells.length,
        uniqueVisitCount: 18,
        petrolCurve,
        cngCurve,
        kFactorSuggestions,
        reconciliation: { pending_cng_visits: 0 },
      },
      current: { fuel: 'GNV', rpm: 2100, petrolMs: 4.2, mapBar: 0.56, cell: { row: 4, column: 3 } },
    };
  }

  function demoObdMaps() {
    const gasoline = {};
    const gnv = {};
    const validation = {};
    for (let column = 1; column < 8; column += 1) {
      for (let row = 1; row < 8; row += 1) {
        if ((column + row) % 2 !== 0) continue;
        const key = `${column}:${row}`;
        const petrol = ((column + row) % 5 - 2) * 0.7;
        const gas = ((column * 3 + row) % 7 - 3) * 0.8;
        const petrolSamples = 8 + ((column + row) % 7);
        const gnvSamples = 9 + ((column * 2 + row) % 8);
        gasoline[key] = { stft: { mean: petrol, physicalSamples: petrolSamples }, ltft: { mean: 0.5 }, speed: { mean: 48 }, coolant: { mean: 88 }, qualified: petrolSamples };
        gnv[key] = { stft: { mean: gas, physicalSamples: gnvSamples }, ltft: { mean: 0.8 }, speed: { mean: 52 }, coolant: { mean: 89 }, qualified: gnvSamples };
        validation[key] = {
          gasoline: petrol, gnv: gas, gasolineSamples: petrolSamples, gnvSamples,
          sameCell: true, sameEpoch: true, comparisonReady: true,
          comparisonReason: 'MESMA_CELULA_E_EPOCA',
          status: Math.abs(gas) <= 2 ? 'EQUIVALENTE' : gas > 0 ? 'AUMENTAR_GNV' : 'DIMINUIR_GNV',
        };
      }
    }
    const independentRpmBins = [750, 1000, 1250, 1500, 1750, 2000, 2500, 3000, 3500, 4000, 5000, 6500];
    const loadBins = [0, 5, 10, 15, 20, 30, 40, 50, 60, 70, 85, 100];
    return {
      rpmBins: RPM_BINS.slice(), petrolMsBins: PETROL_BINS.slice(), gasoline, gnv, validation,
      updatedAt: Date.now(),
      independent: {
        source: 'OBD_ONLY_LEGACY', observationalOnly: true, affectsLearning: false, affectsCalibration: false,
        minimumSamplesPerCell: 6,
        axes: { x: 'rpm', y: 'calculatedLoadPct', rpmBins: independentRpmBins, loadBins },
        gasoline: {}, gnv: {}, validation: {}, updatedAt: Date.now(),
      },
      demo: true,
    };
  }

  function demoToleranceSettings() {
    const levels = ['Muito rigoroso', 'Rigoroso', 'Equilibrado', 'Flexível', 'Muito flexível'];
    const controls = [
      ['rpm', 'Estabilidade da rotação', 'Quanto a rotação pode variar durante uma medição.'],
      ['map', 'Estabilidade da carga', 'Quanto o MAP pode variar durante uma medição.'],
      ['petrol', 'Estabilidade do Petrol Inj.', 'Quanto o tempo comandado pela ECU pode oscilar.'],
      ['pressure', 'Estabilidade da pressão GNV', 'Quanto a pressão diferencial pode variar.'],
      ['collection', 'Ritmo da coleta', 'Quanto tempo o aplicativo observa antes de formar uma evidência.'],
    ].map(([id, title, description]) => ({ id, title, description, selected: 2, selectedLabel: levels[2], actualValues: {} }));
    return {
      ok: true,
      policy: {
        requiredFrames: 10, rpmOscillationMinimum: 40, rpmOscillationPercent: 1.5,
        mapOscillationBar: 0.035, petrolOscillationPercent: 10,
        pressureOscillationBar: 0.04, minimumWaterC: 60,
      },
      controlModel: { ok: true, minimumWaterC: 60, levels, controls },
    };
  }

  function demoMapAdjustment(cells, mode, adjustment) {
    const minimumK = 100;
    const maximumK = 255;
    const value = Number(adjustment);
    const items = (Array.isArray(cells) ? cells : []).map(cell => {
      const current = Number(cell.current);
      const raw = mode === 'percent' ? current * (1 + value / 100) : mode === 'delta' ? current + value : value;
      const target = Math.max(minimumK, Math.min(maximumK, Math.round(raw)));
      return { row: Number(cell.row), column: Number(cell.column), current, target, changed: target !== current };
    });
    return { ok: true, demo: true, simulationOnly: true, mode, adjustment: value, minimumK, maximumK, automatic: false, requiresReview: true, items };
  }

  class NativeApi {
    constructor() {
      this.native = root.OmegasNative || null;
      this.v7 = root.OmegasV7 || null;
      this.power = root.OmegasPower || null;
      this.demo = !this.native;
      this.demoMapState = demoMap();
      this.demoCurveState = demoCurve();
    }

    isDemo() { return this.demo; }
    releaseIdentity() { return invoke(this.native, 'getReleaseIdentity', [], { product: 'OMEGAS', generation: 'V7', versionName: 'demo' }); }
    status() {
      if (this.demo) return {
        serviceRunning: true, engineRunning: true, engineReady: true, engineStuck: false,
        usbConnected: true, usbPermissionPending: false, fuelState: 'GNV', rpm: demoTelemetry().live.rpm,
        petrolMs: demoTelemetry().live.petrol_ms, gasMs: demoTelemetry().live.gas_ms_diagnostic,
        mapBar: demoTelemetry().live.load_bar, directTelemetryAgeMs: 42, wakeLockHeld: true, demo: true,
      };
      return invoke(this.native, 'getStatus', [], {});
    }
    telemetry() { return this.demo ? demoTelemetry() : invoke(this.native, 'getLiveTelemetry', [], {}); }
    fullSnapshot() { return this.demo ? demoTelemetry() : invoke(this.native, 'getFullEngineSnapshot', [], {}); }
    learning() { return this.demo ? demoLearning() : invoke(this.native, 'getLearningMaps', [], {}); }
    learningStatus() { return this.demo ? { live: { state: 'DEMO', reason: 'Dados simulados para validar interface.' } } : invoke(this.native, 'getLearningSyncStatus', [], {}); }
    learningDecision() {
      const snapshot = this.fullSnapshot() || {};
      const live = snapshot.live || snapshot.data || {};
      const sample = live.sample && typeof live.sample === 'object' ? live.sample : {};
      return {
        ok: snapshot.ok !== false,
        state: sample.state || live.sample_state || 'OBSERVING_ENGINE',
        reason: sample.reason || live.sample_reason || 'Observando o motor',
        reason_code: sample.reason_code || sample.reasonCode || live.sample_state || 'OBSERVING_ENGINE',
        frame_count: Number(sample.frame_count ?? live.sample_frame_count ?? 0),
        minimum_frames: Number(sample.minimum_frames ?? live.sample_minimum_frames ?? 0),
        desired_frames: Number(sample.desired_frames ?? live.sample_desired_frames ?? 0),
        duration_ms: Number(sample.duration_ms ?? live.sample_duration_ms ?? 0),
        median_interval_ms: Number(sample.median_interval_ms ?? 0),
        gap_ms: Number(sample.gap_ms ?? 0),
        learning_eligible: sample.learning_eligible === true,
        fuel_confirmed: sample.fuel_confirmed ?? live.fuel ?? null,
        window_age_ms: Number(sample.window_age_ms ?? sample.duration_ms ?? 0),
        window_budget_ms: Number(sample.window_budget_ms ?? 0),
        frames_evicted: Number(sample.frames_evicted ?? 0),
        cell_key: sample.cell_key || '',
        cell_row: Number(sample.cell_row ?? -1),
        cell_column: Number(sample.cell_column ?? -1),
        quality: Number(sample.quality ?? live.learning_quality ?? 0),
        plausibility_reasons: Array.isArray(sample.plausibility_reasons) ? sample.plausibility_reasons : [],
        live,
      };
    }
    learningToleranceSettings() { return this.demo ? demoToleranceSettings() : invoke(this.native, 'getLearningToleranceSettings', [], {}); }
    setLearningToleranceControls(semanticControls) {
      if (this.demo) return { ...demoToleranceSettings(), applied: semanticControls || {}, demo: true };
      return invoke(this.native, 'setLearningToleranceSettings', [JSON.stringify({ semanticControls: semanticControls || {} })], { ok: false });
    }
    resetLearningToleranceSettings() { return this.demo ? demoToleranceSettings() : invoke(this.native, 'resetLearningToleranceSettings', [], { ok: false }); }

    obd() {
      if (this.demo) return {
        ok: true, connected: true, mode: 'local', state: 'CONECTADO', stft: -1.6, ltft: 0.8,
        rpm: 2080, coolant: 88, load: 34, throttle: 18, speed: 52, mapKpa: 49,
        intakeAirC: 31, mafGps: 10.8, fuelLevelPct: 63, fuelLevelSupported: true, moduleVoltageV: 13.92,
        updatedAt: Date.now(), reason: 'Condição qualificada', learningState: 'QUALIFICADO', conditionState: 'FORMANDO 5/6',
        independentEvidence: { accepted: true, reason: 'LEGACY_CONTEXT_ONLY', cellKey: '5:5', fuel: 'GNV', fuelSource: 'MP48_LABEL', axes: 'OBD_RPM_X_LOAD' },
        diagnostic: {
          protocolMode: 'ELM automático (ATSP0)', lastCycleMs: 210, pollRateHz: 3.2,
          supportedStandardPids: ['0103', '0104', '0105', '0106', '0107', '010B', '010C', '010D', '010F', '0110', '0111', '012F', '0142'],
          pids: [
            { command: '0106', responded: true, latencyMs: 42 },
            { command: '010C', responded: true, latencyMs: 39 },
            { command: '012F', responded: true, latencyMs: 54 },
          ],
        }, demo: true,
      };
      return invoke(this.native, 'getObdStatus', [], {});
    }
    obdMaps() { return this.demo ? demoObdMaps() : invoke(this.native, 'getObdMaps', [], {}); }
    obdDevices() {
      if (this.demo) return { permissionRequired: false, enabled: true, devices: [{ name: 'ELM327 DEMO', address: '00:11:22:33:44:55', bonded: true, selected: true, connected: true }] };
      return invoke(this.native, 'listObdDevices', [], { permissionRequired: false, enabled: false, devices: [] });
    }
    requestBluetoothPermission() { return this.demo ? true : invoke(this.native, 'requestBluetoothPermission', [], false); }
    connectObd(address) { return this.demo ? { ok: true, state: 'CONECTANDO', address } : invoke(this.native, 'connectObd', [address || ''], { ok: false }); }
    disconnectObd() { return this.demo ? { ok: true } : invoke(this.native, 'disconnectObd', [], { ok: false }); }
    setObdMode(mode) { return this.demo ? { ok: true, mode } : invoke(this.native, 'setObdMode', [mode || 'off'], { ok: false }); }
    setObdManualFuel(fuel) { return this.demo ? { ok: true, manualFuel: fuel } : invoke(this.native, 'setObdManualFuel', [fuel || ''], { ok: false }); }

    batteryOptimizationStatus() {
      return this.demo
        ? { supported: true, ignoringOptimizations: true, promptedAutomatically: true, demo: true }
        : invoke(this.power, 'getBatteryOptimizationStatus', [], { supported: false, ignoringOptimizations: false });
    }
    requestBatteryOptimizationExemption() {
      return this.demo
        ? { ok: true, supported: true, alreadyAllowed: true, demo: true }
        : invoke(this.power, 'requestBatteryOptimizationExemption', [], { ok: false, error: 'Controle de bateria indisponível' });
    }
    overlayStatus() {
      return this.demo
        ? { ok: true, supported: true, permissionGranted: true, requestedEnabled: false, visible: false, observationalOnly: true, demo: true }
        : invoke(this.power, 'getOverlayStatus', [], { ok: false, supported: false, permissionGranted: false, requestedEnabled: false, visible: false });
    }
    requestOverlayPermissionAndEnable() {
      return this.demo
        ? { ok: true, supported: true, permissionGranted: true, requestedEnabled: true, visible: true, observationalOnly: true, demo: true }
        : invoke(this.power, 'requestOverlayPermissionAndEnable', [], { ok: false, error: 'Controle do flutuante indisponível' });
    }
    setTelemetryOverlayEnabled(enabled) {
      return this.demo
        ? { ok: true, supported: true, permissionGranted: true, requestedEnabled: enabled === true, visible: enabled === true, observationalOnly: true, demo: true }
        : invoke(this.power, 'setOverlayEnabled', [enabled === true], { ok: false, error: 'Controle do flutuante indisponível' });
    }

    connectUsb() { return this.demo ? true : invoke(this.native, 'connectUsb', [''], false); }
    disconnectUsb() { return this.demo ? true : invoke(this.native, 'disconnectUsb', [], false); }

    startMapRead() {
      if (this.demo) return { ok: true, started: true, state: 'READING' };
      return invoke(this.native, 'startKMapRead', [], { ok: false, error: 'Ponte nativa indisponível' });
    }
    mapReadResult() { return this.demo ? this.demoMapState : invoke(this.native, 'getKMapReadResult', [], { ok: false, state: 'FAILED', error: 'Leitura indisponível' }); }
    previewMapAdjustment(cells, mode, adjustment) {
      if (this.demo) return demoMapAdjustment(cells, mode, adjustment);
      return invoke(this.v7, 'previewMapAdjustment', [JSON.stringify(cells || []), mode || 'percent', Number(adjustment)], { ok: false, error: 'Prévia Kotlin do Mapa K indisponível' });
    }
    writeMap(cells, maxStep, pauseMs, reason) {
      if (this.demo) return { ok: false, simulationOnly: true, error: 'Simulação: nenhuma escrita é enviada à ECU.' };
      return invoke(this.v7, 'startMapBatchWrite', [JSON.stringify(cells || []), maxStep || 3, pauseMs || 150, reason || 'Ajuste manual'], { ok: false, error: 'Ponte V7 indisponível' });
    }
    mapWriteOperation() { return this.demo ? { ok: true, state: 'IDLE', busy: false, progress: 0 } : invoke(this.v7, 'getLastOperation', [], { ok: false, state: 'UNAVAILABLE', busy: false }); }

    startCurveRead() {
      if (this.demo) return { ok: true, started: true, state: 'CURVE_READING' };
      return invoke(this.v7, 'startCurveRead', [], { ok: false, error: 'Ponte V7 indisponível' });
    }
    curveOperation() {
      if (this.demo) return { ...this.demoCurveState, state: 'COMPLETED', busy: false };
      return invoke(this.v7, 'getLastOperation', [], { ok: false, state: 'UNAVAILABLE', busy: false });
    }
    previewCurvePoint(index, targetFactor) {
      if (this.demo) {
        const point = this.demoCurveState.points.find(item => Number(item.index) === Number(index));
        if (!point) return { ok: false, error: 'Ponto inválido' };
        const targetRaw = Math.round(Number(targetFactor) * 16384);
        return {
          ok: true, index: Number(index), petrolMs: point.petrolMs,
          currentFactor: point.factor, targetFactor: Number(targetFactor),
          currentRaw: point.factorRaw, targetRaw,
          deltaPercent: (Number(targetFactor) / point.factor - 1) * 100,
          changed: targetRaw !== point.factorRaw,
        };
      }
      return invoke(this.native, 'previewKFactorPoint', [index, targetFactor], { ok: false });
    }
    writeCurve(points, reason) {
      if (this.demo) return { ok: false, simulationOnly: true, error: 'Simulação: nenhuma escrita é enviada à ECU.' };
      return invoke(this.v7, 'startCurveBatchWrite', [JSON.stringify(points || []), reason || 'Ajuste manual Curva K'], { ok: false, error: 'Ponte V7 indisponível' });
    }

    sessionStatus() { return this.demo ? { recording: false, events: 0, megabytes: 0, settings: { autoStartOnUsb: true, telemetryEveryMs: 500, captureRawUsb: false, maxSessionMb: 64, keepSessions: 10 } } : invoke(this.native, 'getSessionRecorderStatus', [], {}); }
    sessions() { return this.demo ? [] : invoke(this.native, 'listRecordedSessions', [], []); }
    setSessionSettings(settings) {
      const s = settings || {};
      if (this.demo) return { ok: true, settings: s, demo: true };
      return invoke(this.native, 'setSessionRecorderSettings', [Number(s.telemetryEveryMs) || 500, Number(s.maxSessionMb) || 64, Number(s.keepSessions) || 10, s.autoStartOnUsb !== false, s.captureRawUsb === true], { ok: false });
    }
    startSession(reason) { return this.demo ? { ok: true, recording: true, demo: true } : invoke(this.native, 'startSessionRecording', [reason || 'manual'], { ok: false }); }
    stopSession(reason) { return this.demo ? { ok: true, recording: false, demo: true } : invoke(this.native, 'stopSessionRecording', [reason || 'manual'], { ok: false }); }
    exportSession(sessionId) { return this.demo ? false : invoke(this.native, 'exportSession', [sessionId || ''], false); }
    logs() { return this.demo ? [] : invoke(this.native, 'getLogs', [], []); }

    exportLearning() { return this.demo ? false : invoke(this.native, 'exportLearningArchive', [], false); }
    importLearning() { return this.demo ? false : invoke(this.native, 'importLearningArchive', [], false); }
    exportLogs() { return this.demo ? false : invoke(this.native, 'exportLogs', [], false); }
    selfTest() { return this.demo ? { ok: true, demo: true } : invoke(this.native, 'runEngineSelfTests', [], {}); }
  }

  ns.NativeApi = NativeApi;
  ns.nativeParse = parse;
})(typeof window !== 'undefined' ? window : globalThis);