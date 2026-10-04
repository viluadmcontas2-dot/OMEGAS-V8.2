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
      this.calibration = root.OmegasCalibration || null;
      this.power = root.OmegasPower || null;
      this.demo = !this.native;
      this.demoMapState = demoMap();
      this.demoCurveState = demoCurve();
    }

    isDemo() { return this.demo; }
    releaseIdentity() { return invoke(this.native, 'getReleaseIdentity', [], { product: 'OMEGAS', generation: 'V8', versionName: 'demo' }); }
    status() {
      if (this.demo) return {
        serviceRunning: true, engineRunning: true, engineReady: true, engineStuck: false,
        usbConnected: true, usbPermissionPending: false, fuelState: 'GNV', rpm: demoTelemetry().live.rpm,
        petrolMs: demoTelemetry().live.petrol_ms, gasMs: demoTelemetry().live.gas_ms_diagnostic,
        mapBar: demoTelemetry().live.load_bar, directTelemetryAgeMs: 42, wakeLockHeld: true, demo: true,
      };
      return invoke(this.native, 'getStatus', [], {});
    }
    /**
     * `lastSequence` = sequência do último quadro que a tela já pintou. Se nada mudou, o Kotlin
     * responde `{changed:false}` sem montar nem serializar o quadro (a tela só atualiza a idade).
     */
    presentSnapshot(lastSequence) {
      if (this.demo) return { ok: true, revision: Date.now(), data: demoTelemetry(), demo: true };
      const seen = Number(lastSequence);
      if (Number.isFinite(seen) && seen >= 0 && this.native && typeof this.native.getPresentSnapshotIfChanged === 'function') {
        return invoke(this.native, 'getPresentSnapshotIfChanged', [seen], { ok: false, revision: 0, data: {} });
      }
      return invoke(this.native, 'getPresentSnapshot', [], { ok: false, revision: 0, data: {} });
    }
    telemetry() { return this.demo ? demoTelemetry() : invoke(this.native, 'getLiveTelemetry', [], {}); }
    fullSnapshot() { return this.demo ? demoTelemetry() : invoke(this.native, 'getFullEngineSnapshot', [], {}); }
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
    setOverlayScale(scale) {
      return this.demo
        ? { ok: true, scale: Number(scale) || 1.25, demo: true }
        : invoke(this.power, 'setOverlayScale', [Number(scale) || 1.25], { ok: false, error: 'Controle do flutuante indisponível' });
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
      return invoke(this.calibration, 'previewMapAdjustment', [JSON.stringify(cells || []), mode || 'percent', Number(adjustment)], { ok: false, error: 'Prévia Kotlin do Mapa K indisponível' });
    }
    writeMap(cells, maxStep, pauseMs, reason) {
      if (this.demo) return { ok: false, simulationOnly: true, error: 'Simulação: nenhuma escrita é enviada à ECU.' };
      return invoke(this.calibration, 'startMapBatchWrite', [JSON.stringify(cells || []), 0, 0, reason || 'Ajuste manual'], { ok: false, error: 'Ponte V7 indisponível' });
    }
    mapWriteOperation() { return this.demo ? { ok: true, state: 'IDLE', busy: false, progress: 0 } : invoke(this.calibration, 'getLastOperation', [], { ok: false, state: 'UNAVAILABLE', busy: false }); }

    startCurveRead() {
      if (this.demo) return { ok: true, started: true, state: 'CURVE_READING' };
      return invoke(this.calibration, 'startCurveRead', [], { ok: false, error: 'Ponte V7 indisponível' });
    }
    startCurveBackup(label) {
      if (this.demo) return { ok: false, simulationOnly: true, error: 'Backup real exige ECU conectada.' };
      return invoke(this.calibration, 'startCurveBackup', [label || 'Curva salva manualmente'], { ok: false, error: 'Backup da Curva K indisponível' });
    }
    curveBackups() {
      if (this.demo) return [];
      return invoke(this.calibration, 'listCurveBackups', [], []);
    }
    prepareCurveRestore(fileName) {
      if (this.demo) return { ok: false, simulationOnly: true, error: 'Restauração real exige ECU conectada.' };
      return invoke(this.calibration, 'startCurveRestorePrepare', [fileName || ''], { ok: false, error: 'Restauração da Curva K indisponível' });
    }
    resetCurve() {
      if (this.demo) return { ok: false, simulationOnly: true, error: 'Reset real exige ECU conectada.' };
      return invoke(this.calibration, 'startCurveReset', [], { ok: false, error: 'Reset da Curva K indisponível' });
    }
    /** Desfazer da Curva K: grava SOMENTE os valores da foto `fileName` (o Kotlin confere cada alvo). */
    restoreCurve(points, fileName) {
      if (this.demo) return { ok: false, simulationOnly: true, error: 'Simulação: nenhuma escrita é enviada à ECU.' };
      return invoke(this.calibration, 'startCurveRestoreWrite', [JSON.stringify(points || []), String(fileName || '')], { ok: false, error: 'Restauração da Curva K indisponível' });
    }
    mapBackups() {
      if (this.demo) return [];
      return invoke(this.calibration, 'listMapBackups', [], []);
    }
    /** Desfazer do Mapa K, passo 1: relê o mapa (somente leitura) e lista o que voltaria. */
    prepareMapRestore(adjustmentId) {
      if (this.demo) return { ok: false, simulationOnly: true, error: 'Restauração real exige ECU conectada.' };
      return invoke(this.calibration, 'startMapRestorePrepare', [String(adjustmentId || '')], { ok: false, error: 'Restauração do Mapa K indisponível' });
    }
    /** Desfazer do Mapa K, passo 2 (toque do dono): mesmo escritor em lote, com foto e readback. */
    restoreMap(cells, adjustmentId) {
      if (this.demo) return { ok: false, simulationOnly: true, error: 'Simulação: nenhuma escrita é enviada à ECU.' };
      return invoke(this.calibration, 'startMapRestoreWrite', [JSON.stringify(cells || []), String(adjustmentId || '')], { ok: false, error: 'Restauração do Mapa K indisponível' });
    }
    curveOperation() {
      if (this.demo) return { ...this.demoCurveState, state: 'COMPLETED', busy: false };
      return invoke(this.calibration, 'getLastOperation', [], { ok: false, state: 'UNAVAILABLE', busy: false });
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
      return invoke(this.calibration, 'startCurveBatchWrite', [JSON.stringify(points || []), reason || 'Ajuste manual Curva K'], { ok: false, error: 'Ponte V7 indisponível' });
    }

    sessionStatus() { return this.demo ? { recording: false, events: 0, megabytes: 0, settings: { autoStartOnUsb: true, telemetryEveryMs: 250, captureRawUsb: false, maxSessionMb: 256, keepSessions: 20 } } : invoke(this.native, 'getSessionRecorderStatus', [], {}); }
    sessions() { return this.demo ? [] : invoke(this.native, 'listRecordedSessions', [], []); }
    setSessionSettings(settings) {
      const s = settings || {};
      if (this.demo) return { ok: true, settings: s, demo: true };
      return invoke(this.native, 'setSessionRecorderSettings', [Number(s.telemetryEveryMs) || 250, Number(s.maxSessionMb) || 256, Math.max(20, Number(s.keepSessions) || 20), s.autoStartOnUsb !== false, s.captureRawUsb === true], { ok: false });
    }
    startSession(reason) { return this.demo ? { ok: true, recording: true, demo: true } : invoke(this.native, 'startSessionRecording', [reason || 'manual'], { ok: false }); }
    stopSession(reason) { return this.demo ? { ok: true, recording: false, demo: true } : invoke(this.native, 'stopSessionRecording', [reason || 'manual'], { ok: false }); }
    exportSession(sessionId) { return this.demo ? false : invoke(this.native, 'exportSession', [sessionId || ''], false); }
    logs() { return this.demo ? [] : invoke(this.native, 'getLogs', [], []); }

    exportData() { return this.demo ? false : invoke(this.native, 'exportData', [], false); }
    exportLogs() { return this.demo ? false : invoke(this.native, 'exportLogs', [], false); }
    selfTest() { return this.demo ? { ok: true, demo: true } : invoke(this.native, 'runEngineSelfTests', [], {}); }
  }

  ns.NativeApi = NativeApi;
  ns.nativeParse = parse;
})(typeof window !== 'undefined' ? window : globalThis);