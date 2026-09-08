(() => {
  var __defProp = Object.defineProperty;
  var __defProps = Object.defineProperties;
  var __getOwnPropDescs = Object.getOwnPropertyDescriptors;
  var __getOwnPropSymbols = Object.getOwnPropertySymbols;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __propIsEnum = Object.prototype.propertyIsEnumerable;
  var __defNormalProp = (obj, key, value) => key in obj ? __defProp(obj, key, { enumerable: true, configurable: true, writable: true, value }) : obj[key] = value;
  var __spreadValues = (a, b) => {
    for (var prop in b || (b = {}))
      if (__hasOwnProp.call(b, prop))
        __defNormalProp(a, prop, b[prop]);
    if (__getOwnPropSymbols)
      for (var prop of __getOwnPropSymbols(b)) {
        if (__propIsEnum.call(b, prop))
          __defNormalProp(a, prop, b[prop]);
      }
    return a;
  };
  var __spreadProps = (a, b) => __defProps(a, __getOwnPropDescs(b));
  (function(root) {
    "use strict";
    const ns = root.OmegasUi = root.OmegasUi || {};
    const PETROL_BINS = [2, 2.5, 3, 3.5, 4.5, 6, 8, 10, 12, 14, 16, 18];
    const RPM_BINS = [850, 1350, 1850, 2500, 3e3, 3500, 4e3, 4500, 5e3, 5500, 6e3, 6500];
    function parse(value, fallback) {
      if (value === null || value === void 0 || value === "") return fallback;
      if (typeof value !== "string") return value;
      try {
        return JSON.parse(value);
      } catch (_) {
        return fallback;
      }
    }
    function invoke(target, name, args, fallback) {
      const fn = target && target[name];
      if (typeof fn !== "function") return fallback;
      try {
        return parse(fn.apply(target, args || []), fallback);
      } catch (error) {
        return { ok: false, error: error && error.message ? error.message : String(error) };
      }
    }
    function demoTelemetry() {
      const phase = Date.now() / 1e3 % 12;
      const rpm = Math.round(1700 + Math.sin(phase) * 620);
      const petrolMs = 4.15 + Math.sin(phase * 0.6) * 0.34;
      const gasMs = petrolMs * (1 + Math.sin(phase * 0.35) * 0.012);
      const mapBar = 0.42 + Math.max(0, Math.sin(phase * 0.5)) * 0.31;
      return {
        ok: true,
        valid: true,
        demo: true,
        ageMs: 42,
        telemetryAgeMs: 42,
        updatedAt: Date.now(),
        live: {
          rpm,
          petrol_ms: petrolMs,
          gas_ms_diagnostic: gasMs,
          load_bar: mapBar,
          fuel: "GNV",
          sample_state: "FORMING_SAMPLE",
          sample_reason: "Formando amostra 7/10 leituras",
          sample_frame_count: 7,
          sample_minimum_frames: 6,
          sample_desired_frames: 10,
          sample: {
            state: "FORMING_SAMPLE",
            reason: "Formando amostra 7/10 leituras",
            reason_code: "FORMING_SAMPLE",
            frame_count: 7,
            minimum_frames: 6,
            desired_frames: 10,
            duration_ms: 1780,
            learning_eligible: false,
            fuel_confirmed: "GNV",
            window_age_ms: 1780,
            window_budget_ms: 3e3,
            cell_row: 4,
            cell_column: 3,
            cell_key: "4:3",
            quality: 0.82
          }
        },
        interpolation: {
          valid: true,
          educationalOnly: true,
          affectsLearning: false,
          affectsCalibration: false,
          method: "BILINEAR_RPM_X_PETROL_MS",
          rpm,
          petrolMs,
          mapBar,
          cell: {
            row: 4,
            column: 3,
            continuousWeights: [
              { row: 4, column: 3, weight: 0.42 },
              { row: 4, column: 4, weight: 0.28 },
              { row: 5, column: 3, weight: 0.18 },
              { row: 5, column: 4, weight: 0.12 }
            ]
          }
        }
      };
    }
    function demoMap() {
      const rows = Array.from({ length: 12 }, (_, row) => Array.from({ length: 12 }, (_2, column) => 116 + row * 2 + Math.round(column * 0.7)));
      return {
        ok: true,
        state: "COMPLETED",
        demo: true,
        rows,
        extraRow: Array(12).fill(0),
        axes: { petrolBins: PETROL_BINS.slice(), rpmBins: RPM_BINS.slice() },
        hash: "browser-demo",
        writableCells: 144,
        sessionConfirmed: true
      };
    }
    function demoCurve() {
      const points = Array.from({ length: 30 }, (_, index) => ({
        index,
        petrolMs: 1.5 + index * 0.35,
        factor: 1.02 + Math.sin(index / 6) * 0.08,
        factorRaw: Math.round((1.02 + Math.sin(index / 6) * 0.08) * 16384)
      }));
      return { ok: true, demo: true, points, pointCount: 30, minimumFactor: 0.6, maximumFactor: 3.99 };
    }
    function demoLearning() {
      const cells = [];
      for (let row = 0; row < 12; row += 1) {
        for (let column = 0; column < 12; column += 1) {
          if ((row + column) % 3 !== 0) continue;
          cells.push({
            row,
            column,
            key: "".concat(row, ":").concat(column),
            samples: 12 + (row * 7 + column * 5) % 55,
            visits: 2 + (row + column) % 4,
            confidence: 0.55 + (row + column) % 5 * 0.09,
            stage: "ACCEPTED"
          });
        }
      }
      const petrol = cells.map((item) => __spreadProps(__spreadValues({}, item), { fuel: "PETROL", petrolMs: 3.8 + item.row * 0.17 }));
      const cng = cells.map((item) => __spreadProps(__spreadValues({}, item), { fuel: "CNG", petrolMs: 3.84 + item.row * 0.17 }));
      const comparisons = cells.map((item, index) => __spreadProps(__spreadValues({}, item), {
        errorPercent: (index % 9 - 4) * 0.35,
        quality: item.confidence
      }));
      return {
        ok: true,
        demo: true,
        decisionAuthority: "BLUE_CAUSAL_ENGINE",
        uiPipeline: "PHYSICAL_EVIDENCE_ONLY",
        grid: { rows: 12, columns: 12, petrolBins: PETROL_BINS, rpmBins: RPM_BINS },
        cells,
        petrol,
        cng,
        comparisons,
        current: { fuel: "GNV", rpm: 2100, petrolMs: 4.2, mapBar: 0.56, cell: { row: 4, column: 3 } }
      };
    }
    function demoToleranceSettings() {
      const levels = ["Muito rigoroso", "Rigoroso", "Equilibrado", "Flex\xEDvel", "Muito flex\xEDvel"];
      const controls = [
        ["rpm", "Estabilidade da rota\xE7\xE3o", "Quanto a rota\xE7\xE3o pode variar durante uma medi\xE7\xE3o."],
        ["map", "Estabilidade da carga", "Quanto o MAP pode variar durante uma medi\xE7\xE3o."],
        ["petrol", "Estabilidade do Petrol Inj.", "Quanto o tempo comandado pela ECU pode oscilar."],
        ["pressure", "Estabilidade da press\xE3o GNV", "Quanto a press\xE3o diferencial pode variar."],
        ["collection", "Ritmo da coleta", "Quanto tempo o aplicativo observa antes de formar uma evid\xEAncia."]
      ].map(([id, title, description]) => ({ id, title, description, selected: 2, selectedLabel: levels[2], actualValues: {} }));
      return {
        ok: true,
        policy: {
          requiredFrames: 10,
          rpmOscillationMinimum: 40,
          rpmOscillationPercent: 1.5,
          mapOscillationBar: 0.035,
          petrolOscillationPercent: 10,
          pressureOscillationBar: 0.04,
          minimumWaterC: 60
        },
        controlModel: { ok: true, minimumWaterC: 60, levels, controls }
      };
    }
    function demoMapAdjustment(cells, mode, adjustment) {
      const minimumK = 100;
      const maximumK = 180;
      const value = Number(adjustment);
      const items = (Array.isArray(cells) ? cells : []).map((cell) => {
        const current = Number(cell.current);
        const raw = mode === "percent" ? current * (1 + value / 100) : mode === "delta" ? current + value : value;
        const target = Math.max(minimumK, Math.min(maximumK, Math.round(raw)));
        return { row: Number(cell.row), column: Number(cell.column), current, target, changed: target !== current };
      });
      return { ok: true, demo: true, simulationOnly: true, mode, adjustment: value, minimumK, maximumK, automatic: false, requiresReview: true, items };
    }
    class NativeApi {
      constructor() {
        this.native = root.OmegasNative || null;
        this.blue = root.OmegasBlue || null;
        this.power = root.OmegasPower || null;
        this.demo = !this.native;
        this.demoMapState = demoMap();
        this.demoCurveState = demoCurve();
        this.demoScienceRevision = 0;
      }
      isDemo() {
        return this.demo;
      }
      releaseIdentity() {
        return invoke(this.native, "getReleaseIdentity", [], { product: "OMEGAS", generation: "BLUE", versionName: "demo" });
      }
      status() {
        if (this.demo) return {
          serviceRunning: true,
          engineRunning: true,
          engineReady: true,
          engineStuck: false,
          usbConnected: true,
          usbPermissionPending: false,
          fuelState: "GNV",
          rpm: demoTelemetry().live.rpm,
          petrolMs: demoTelemetry().live.petrol_ms,
          gasMs: demoTelemetry().live.gas_ms_diagnostic,
          mapBar: demoTelemetry().live.load_bar,
          directTelemetryAgeMs: 42,
          wakeLockHeld: true,
          demo: true
        };
        return invoke(this.native, "getStatus", [], {});
      }
      presentSnapshot() {
        if (this.demo) return { ok: true, revision: Date.now(), data: demoTelemetry(), demo: true };
        return invoke(this.native, "getPresentSnapshot", [], { ok: false, revision: 0, data: {} });
      }
      scienceSnapshotSince(revision) {
        if (this.demo) {
          this.demoScienceRevision += 1;
          const learning = demoLearning();
          return {
            ok: true,
            changed: true,
            revision: this.demoScienceRevision,
            refreshing: false,
            data: {
              learning,
              calibrationState: { ready: true, suggestionItems: [] }
            },
            demo: true
          };
        }
        return invoke(this.native, "getScienceSnapshotSince", [Number(revision) || 0], { ok: false, changed: false, revision: Number(revision) || 0 });
      }
      telemetry() {
        return this.demo ? demoTelemetry() : invoke(this.native, "getLiveTelemetry", [], {});
      }
      fullSnapshot() {
        return this.demo ? demoTelemetry() : invoke(this.native, "getFullEngineSnapshot", [], {});
      }
      learning() {
        return this.demo ? demoLearning() : invoke(this.native, "getLearningMaps", [], {});
      }
      learningStatus() {
        return this.demo ? { live: { state: "DEMO", reason: "Dados simulados para validar interface." } } : invoke(this.native, "getLearningSyncStatus", [], {});
      }
      learningDecision() {
        var _a, _b, _c, _d, _e, _f, _g, _h, _i, _j, _k, _l, _m, _n, _o, _p, _q, _r, _s, _t;
        const snapshot = this.fullSnapshot() || {};
        const live = snapshot.live || snapshot.data || {};
        const sample = live.sample && typeof live.sample === "object" ? live.sample : {};
        return {
          ok: snapshot.ok !== false,
          state: sample.state || live.sample_state || "OBSERVING_ENGINE",
          reason: sample.reason || live.sample_reason || "Observando o motor",
          reason_code: sample.reason_code || sample.reasonCode || live.sample_state || "OBSERVING_ENGINE",
          frame_count: Number((_b = (_a = sample.frame_count) != null ? _a : live.sample_frame_count) != null ? _b : 0),
          minimum_frames: Number((_d = (_c = sample.minimum_frames) != null ? _c : live.sample_minimum_frames) != null ? _d : 0),
          desired_frames: Number((_f = (_e = sample.desired_frames) != null ? _e : live.sample_desired_frames) != null ? _f : 0),
          duration_ms: Number((_h = (_g = sample.duration_ms) != null ? _g : live.sample_duration_ms) != null ? _h : 0),
          median_interval_ms: Number((_i = sample.median_interval_ms) != null ? _i : 0),
          gap_ms: Number((_j = sample.gap_ms) != null ? _j : 0),
          learning_eligible: sample.learning_eligible === true,
          fuel_confirmed: (_l = (_k = sample.fuel_confirmed) != null ? _k : live.fuel) != null ? _l : null,
          window_age_ms: Number((_n = (_m = sample.window_age_ms) != null ? _m : sample.duration_ms) != null ? _n : 0),
          window_budget_ms: Number((_o = sample.window_budget_ms) != null ? _o : 0),
          frames_evicted: Number((_p = sample.frames_evicted) != null ? _p : 0),
          cell_key: sample.cell_key || "",
          cell_row: Number((_q = sample.cell_row) != null ? _q : -1),
          cell_column: Number((_r = sample.cell_column) != null ? _r : -1),
          quality: Number((_t = (_s = sample.quality) != null ? _s : live.learning_quality) != null ? _t : 0),
          plausibility_reasons: Array.isArray(sample.plausibility_reasons) ? sample.plausibility_reasons : [],
          live
        };
      }
      learningToleranceSettings() {
        return this.demo ? demoToleranceSettings() : invoke(this.native, "getLearningToleranceSettings", [], {});
      }
      setLearningToleranceControls(semanticControls) {
        if (this.demo) return __spreadProps(__spreadValues({}, demoToleranceSettings()), { applied: semanticControls || {}, demo: true });
        return invoke(this.native, "setLearningToleranceSettings", [JSON.stringify({ semanticControls: semanticControls || {} })], { ok: false });
      }
      resetLearningToleranceSettings() {
        return this.demo ? demoToleranceSettings() : invoke(this.native, "resetLearningToleranceSettings", [], { ok: false });
      }
      obd() {
        if (this.demo) return {
          ok: true,
          connected: true,
          mode: "local",
          state: "CONECTADO",
          stft: -1.6,
          ltft: 0.8,
          rpm: 2080,
          coolant: 88,
          load: 34,
          throttle: 18,
          speed: 52,
          mapKpa: 49,
          intakeAirC: 31,
          mafGps: 10.8,
          fuelLevelPct: 63,
          fuelLevelSupported: true,
          moduleVoltageV: 13.92,
          updatedAt: Date.now(),
          reason: "Condi\xE7\xE3o qualificada",
          learningState: "QUALIFICADO",
          conditionState: "FORMANDO 5/6",
          independentEvidence: { accepted: true, reason: "LEGACY_CONTEXT_ONLY", cellKey: "5:5", fuel: "GNV", fuelSource: "MP48_LABEL", axes: "OBD_RPM_X_LOAD" },
          diagnostic: {
            protocolMode: "ELM autom\xE1tico (ATSP0)",
            lastCycleMs: 210,
            pollRateHz: 3.2,
            supportedStandardPids: ["0103", "0104", "0105", "0106", "0107", "010B", "010C", "010D", "010F", "0110", "0111", "012F", "0142"],
            pids: [
              { command: "0106", responded: true, latencyMs: 42 },
              { command: "010C", responded: true, latencyMs: 39 },
              { command: "012F", responded: true, latencyMs: 54 }
            ]
          },
          demo: true
        };
        return invoke(this.native, "getObdStatus", [], {});
      }
      obdDevices() {
        if (this.demo) return { permissionRequired: false, enabled: true, devices: [{ name: "ELM327 DEMO", address: "00:11:22:33:44:55", bonded: true, selected: true, connected: true }] };
        return invoke(this.native, "listObdDevices", [], { permissionRequired: false, enabled: false, devices: [] });
      }
      requestBluetoothPermission() {
        return this.demo ? true : invoke(this.native, "requestBluetoothPermission", [], false);
      }
      connectObd(address) {
        return this.demo ? { ok: true, state: "CONECTANDO", address } : invoke(this.native, "connectObd", [address || ""], { ok: false });
      }
      disconnectObd() {
        return this.demo ? { ok: true } : invoke(this.native, "disconnectObd", [], { ok: false });
      }
      setObdMode(mode) {
        return this.demo ? { ok: true, mode } : invoke(this.native, "setObdMode", [mode || "off"], { ok: false });
      }
      batteryOptimizationStatus() {
        return this.demo ? { supported: true, ignoringOptimizations: true, promptedAutomatically: true, demo: true } : invoke(this.power, "getBatteryOptimizationStatus", [], { supported: false, ignoringOptimizations: false });
      }
      requestBatteryOptimizationExemption() {
        return this.demo ? { ok: true, supported: true, alreadyAllowed: true, demo: true } : invoke(this.power, "requestBatteryOptimizationExemption", [], { ok: false, error: "Controle de bateria indispon\xEDvel" });
      }
      overlayStatus() {
        return this.demo ? { ok: true, supported: true, permissionGranted: true, requestedEnabled: false, visible: false, observationalOnly: true, demo: true } : invoke(this.power, "getOverlayStatus", [], { ok: false, supported: false, permissionGranted: false, requestedEnabled: false, visible: false });
      }
      requestOverlayPermissionAndEnable() {
        return this.demo ? { ok: true, supported: true, permissionGranted: true, requestedEnabled: true, visible: true, observationalOnly: true, demo: true } : invoke(this.power, "requestOverlayPermissionAndEnable", [], { ok: false, error: "Controle do flutuante indispon\xEDvel" });
      }
      setTelemetryOverlayEnabled(enabled) {
        return this.demo ? { ok: true, supported: true, permissionGranted: true, requestedEnabled: enabled === true, visible: enabled === true, observationalOnly: true, demo: true } : invoke(this.power, "setOverlayEnabled", [enabled === true], { ok: false, error: "Controle do flutuante indispon\xEDvel" });
      }
      connectUsb() {
        return this.demo ? true : invoke(this.native, "connectUsb", [""], false);
      }
      disconnectUsb() {
        return this.demo ? true : invoke(this.native, "disconnectUsb", [], false);
      }
      startMapRead() {
        if (this.demo) return { ok: true, started: true, state: "READING" };
        return invoke(this.native, "startKMapRead", [], { ok: false, error: "Ponte nativa indispon\xEDvel" });
      }
      mapReadResult() {
        return this.demo ? this.demoMapState : invoke(this.native, "getKMapReadResult", [], { ok: false, state: "FAILED", error: "Leitura indispon\xEDvel" });
      }
      previewMapAdjustment(cells, mode, adjustment) {
        if (this.demo) return demoMapAdjustment(cells, mode, adjustment);
        return invoke(this.blue, "previewMapAdjustment", [JSON.stringify(cells || []), mode || "percent", Number(adjustment)], { ok: false, error: "Pr\xE9via Kotlin do Mapa K indispon\xEDvel" });
      }
      writeMap(cells, maxStep, pauseMs, reason) {
        if (this.demo) return { ok: false, simulationOnly: true, error: "Simula\xE7\xE3o: nenhuma escrita \xE9 enviada \xE0 ECU." };
        return invoke(this.blue, "startMapBatchWrite", [JSON.stringify(cells || []), maxStep || 3, pauseMs || 150, reason || "Ajuste manual"], { ok: false, error: "Ponte de calibra\xE7\xE3o indispon\xEDvel" });
      }
      mapWriteOperation() {
        return this.demo ? { ok: true, state: "IDLE", busy: false, progress: 0 } : invoke(this.blue, "getLastOperation", [], { ok: false, state: "UNAVAILABLE", busy: false });
      }
      startCurveRead() {
        if (this.demo) return { ok: true, started: true, state: "CURVE_READING" };
        return invoke(this.blue, "startCurveRead", [], { ok: false, error: "Ponte de calibra\xE7\xE3o indispon\xEDvel" });
      }
      curveOperation() {
        if (this.demo) return __spreadProps(__spreadValues({}, this.demoCurveState), { state: "COMPLETED", busy: false });
        return invoke(this.blue, "getLastOperation", [], { ok: false, state: "UNAVAILABLE", busy: false });
      }
      previewCurvePoint(index, targetFactor) {
        if (this.demo) {
          const point = this.demoCurveState.points.find((item) => Number(item.index) === Number(index));
          if (!point) return { ok: false, error: "Ponto inv\xE1lido" };
          const targetRaw = Math.round(Number(targetFactor) * 16384);
          return {
            ok: true,
            index: Number(index),
            petrolMs: point.petrolMs,
            currentFactor: point.factor,
            targetFactor: Number(targetFactor),
            currentRaw: point.factorRaw,
            targetRaw,
            deltaPercent: (Number(targetFactor) / point.factor - 1) * 100,
            changed: targetRaw !== point.factorRaw
          };
        }
        return invoke(this.native, "previewKFactorPoint", [index, targetFactor], { ok: false });
      }
      writeCurve(points, reason) {
        if (this.demo) return { ok: false, simulationOnly: true, error: "Simula\xE7\xE3o: nenhuma escrita \xE9 enviada \xE0 ECU." };
        return invoke(this.blue, "startCurveBatchWrite", [JSON.stringify(points || []), reason || "Ajuste manual Curva K"], { ok: false, error: "Ponte de calibra\xE7\xE3o indispon\xEDvel" });
      }
      sessionStatus() {
        return this.demo ? { recording: false, events: 0, megabytes: 0, settings: { autoStartOnUsb: true, telemetryEveryMs: 500, captureRawUsb: false, maxSessionMb: 64, keepSessions: 30 } } : invoke(this.native, "getSessionRecorderStatus", [], {});
      }
      sessions() {
        return this.demo ? [] : invoke(this.native, "listRecordedSessions", [], []);
      }
      setSessionSettings(settings) {
        const s = settings || {};
        const normalized = {
          telemetryEveryMs: Number(s.telemetryEveryMs) || 500,
          maxSessionMb: Number(s.maxSessionMb) || 64,
          keepSessions: Math.max(20, Math.min(100, Number(s.keepSessions) || 30)),
          autoStartOnUsb: s.autoStartOnUsb !== false,
          captureRawUsb: s.captureRawUsb === true
        };
        if (this.demo) return { ok: true, settings: normalized, demo: true };
        return invoke(this.native, "setSessionRecorderSettings", [normalized.telemetryEveryMs, normalized.maxSessionMb, normalized.keepSessions, normalized.autoStartOnUsb, normalized.captureRawUsb], { ok: false });
      }
      startSession(reason) {
        return this.demo ? { ok: true, recording: true, demo: true } : invoke(this.native, "startSessionRecording", [reason || "manual"], { ok: false });
      }
      stopSession(reason) {
        return this.demo ? { ok: true, recording: false, demo: true } : invoke(this.native, "stopSessionRecording", [reason || "manual"], { ok: false });
      }
      exportSession(sessionId) {
        return this.demo ? false : invoke(this.native, "exportSession", [sessionId || ""], false);
      }
      logs() {
        return this.demo ? [] : invoke(this.native, "getLogs", [], []);
      }
      exportLearning() {
        return this.demo ? false : invoke(this.native, "exportLearningArchive", [], false);
      }
      importLearning() {
        return this.demo ? false : invoke(this.native, "importLearningArchive", [], false);
      }
      exportLogs() {
        return this.demo ? false : invoke(this.native, "exportLogs", [], false);
      }
      selfTest() {
        return this.demo ? { ok: true, demo: true } : invoke(this.native, "runEngineSelfTests", [], {});
      }
    }
    ns.NativeApi = NativeApi;
    ns.nativeParse = parse;
  })(typeof window !== "undefined" ? window : globalThis);
})();
