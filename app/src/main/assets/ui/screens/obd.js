(() => {
  (function(root) {
    "use strict";
    const ns = root.OmegasUi = root.OmegasUi || {};
    function finite(value) {
      return Number.isFinite(Number(value)) ? Number(value) : null;
    }
    function fmt(value, digits) {
      const n = finite(value);
      return n === null ? "\u2014" : n.toLocaleString("pt-BR", { minimumFractionDigits: digits, maximumFractionDigits: digits });
    }
    function text(id, value) {
      const node = document.getElementById(id);
      if (!node) return;
      const next = value == null ? "\u2014" : String(value);
      if (node.textContent !== next) node.textContent = next;
    }
    function escapeHtml(value) {
      return String(value != null ? value : "").replace(/[&<>"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[char]);
    }
    function first(source, names) {
      for (const name of names) {
        if (source && source[name] !== void 0 && source[name] !== null) return source[name];
      }
      return null;
    }
    function trimLabel(value) {
      const n = finite(value);
      return n === null ? "\u2014" : "".concat(n > 0 ? "+" : "").concat(fmt(n, 1), "%");
    }
    function ensureStyles() {
      if (document.querySelector("link[data-witness-multimedia]")) return;
      const link = document.createElement("link");
      link.rel = "stylesheet";
      link.href = "styles-witness-multimedia.css";
      link.dataset.witnessMultimedia = "true";
      document.head.appendChild(link);
    }
    function witnessState(raw, gnvStft) {
      const value = String(raw || "UNAVAILABLE").toUpperCase();
      if (value === "SUPPORTS") return { label: "CONFIRMA BLUE", tone: "supports", detail: "STFT GNV concorda com o erro MP48 da mesma regi\xE3o." };
      if (value === "CONFLICTS") return { label: "CONFLITA", tone: "conflicts", detail: "STFT GNV discorda do erro MP48; a confian\xE7a n\xE3o \xE9 acelerada." };
      if (finite(gnvStft) !== null) return { label: "STFT GNV PRONTO", tone: "insufficient", detail: "Witness coletado; o Blue decide suporte/conflito junto ao erro MP48." };
      if (value === "INSUFFICIENT") return { label: "COLETANDO GNV", tone: "insufficient", detail: "Faltam amostras STFT pareadas ao frame MP48 atual." };
      return { label: "INDISPON\xCDVEL", tone: "unavailable", detail: "Sem STFT GNV pareado neste momento." };
    }
    class ObdScreen {
      constructor(store, api) {
        this.store = store;
        this.api = api;
        this.root = document.querySelector('[data-screen="obd"]');
        this.view = "observe";
        this.connectionSignature = "";
        this.sensorSignature = "";
        this.powerSignature = "";
        this.lastWitness = {};
        this.lastWitnessAt = 0;
        ensureStyles();
        this.installLayout();
        this.bind();
      }
      installLayout() {
        if (!this.root) return;
        this.root.classList.add("multimedia-obd-screen");
        this.root.innerHTML = '\n        <div class="witness-page-intro">\n          <div><small>OBD INDEPENDENTE</small><h2>STFT do GNV</h2><p>OBD aprende diretamente pelo STFT do GNV, sem depender do MP48.</p></div>\n          <div class="witness-head-actions">\n            <span id="obdStatusPill" class="status-pill" data-online="false">OBD offline</span>\n            <button id="obdRefreshButton" type="button" class="secondary">Atualizar</button>\n            <button id="obdDisconnectButton" type="button" class="quiet-button">Desconectar</button>\n          </div>\n        </div>\n\n        <div class="witness-view-tabs" role="tablist" aria-label="Vis\xE3o OBD">\n          <button type="button" data-obd-view="observe" class="active">Witness</button>\n          <button type="button" data-obd-view="setup">Conex\xE3o</button>\n        </div>\n\n        <div class="witness-panel active" data-obd-panel="observe">\n          <section class="witness-live-card">\n            <div class="witness-live-main">\n              <small>STFT BANK 1 \xB7 PID 0106</small>\n              <div><strong id="obdLiveStft">\u2014</strong><em>%</em></div>\n              <p id="obdLiveStatus">Aguardando ELM327</p>\n            </div>\n            <div class="witness-authority-note">\n              <b>Somente evid\xEAncia</b>\n              <span>OBD observa STFT e n\xE3o escreve K.</span>\n              <span>RPM e MAP v\xEAm diretamente do OBD.</span>\n            </div>\n          </section>\n\n          <section class="witness-result-card" id="obdWitnessCard" data-state="unavailable">\n            <div class="witness-result-heading">\n              <div><small>WITNESS</small><b id="obdWitnessState">INDISPON\xCDVEL</b><span id="obdWitnessDetail">Sem evid\xEAncia f\xEDsica suficiente neste momento.</span></div>\n              <div class="witness-quality"><small>QUALIDADE</small><b id="obdWitnessQuality">\u2014</b></div>\n            </div>\n            <div class="witness-comparison-grid">\n              <article><small>STFT NO GNV</small><b id="obdGnvStft">\u2014</b><span>testemunha r\xE1pida da lambda</span></article>\n              <article><small>LEITURA</small><b id="obdStftMeaning">\u2014</b><span>rico / pobre / neutro</span></article>\n              <article class="witness-residual"><small>INTEGRA\xC7\xC3O</small><b id="obdWitnessMode">OBD independente</b><span>correção direta no GNV</span></article>\n              <article><small>AMOSTRAS GNV</small><b id="obdWitnessSamples">\u2014</b><span>coletadas pelo OBD</span></article>\n            </div>\n          </section>\n\n          <section class="witness-pair-card">\n            <header><div><small>REGIÃO OBD</small><h3>RPM e MAP do mesmo ciclo OBD</h3></div><span id="obdPairSkew">\u2014</span></header>\n            <div class="witness-pair-grid">\n              <article><small>RPM OBD</small><b id="obdPairedRpm">\u2014</b></article>\n              <article><small>MAP OBD</small><b id="obdPairedMap">\u2014</b></article>\n              <article><small>CORREÇÃO</small><b id="obdPairedPetrol">\u2014</b></article>\n              <article><small>MODO</small><b id="obdPairedFuel">\u2014</b></article>\n            </div>\n          </section>\n        </div>\n\n        <div class="witness-panel" data-obd-panel="setup">\n          <div class="witness-setup-grid">\n            <section id="obdConnectionCenter" class="witness-setup-card"></section>\n            <section id="obdSensorList" class="witness-setup-card"></section>\n            <section id="obdPowerCard" class="witness-setup-card"></section>\n          </div>\n          <button id="obdPermissionButton" type="button" class="secondary witness-permission-button">Autorizar Bluetooth</button>\n        </div>';
      }
      bind() {
        var _a, _b, _c, _d, _e, _f;
        (_a = this.root) == null ? void 0 : _a.querySelectorAll("[data-obd-view]").forEach((button) => button.addEventListener("click", () => this.setView(button.dataset.obdView || "observe")));
        (_b = document.getElementById("obdPermissionButton")) == null ? void 0 : _b.addEventListener("click", () => this.api.requestBluetoothPermission());
        (_c = document.getElementById("obdDisconnectButton")) == null ? void 0 : _c.addEventListener("click", () => {
          const result = this.api.disconnectObd();
          this.connectionSignature = "";
          if ((result == null ? void 0 : result.ok) === false) this.alert(result.error || "N\xE3o foi poss\xEDvel desconectar o OBD.");
        });
        (_d = document.getElementById("obdRefreshButton")) == null ? void 0 : _d.addEventListener("click", () => {
          this.connectionSignature = "";
          this.sensorSignature = "";
          this.lastWitnessAt = 0;
          this.store.patch({ obd: this.api.obd() || {}, obdDevices: this.api.obdDevices() || {} });
          this.render(this.store.get());
        });
        (_e = document.getElementById("obdConnectionCenter")) == null ? void 0 : _e.addEventListener("click", (event) => {
          const gnv = event.target.closest("[data-obd-gnv]");
          if (gnv) {
            this.api.setObdGnvLearningEnabled(gnv.dataset.obdGnv === "true");
            this.connectionSignature = "";
            return;
          }
          const mode = event.target.closest("[data-obd-mode]");
          if (mode) {
            const result = this.api.setObdMode(mode.dataset.obdMode || "off");
            if ((result == null ? void 0 : result.ok) === false) this.alert(result.error || "N\xE3o foi poss\xEDvel alterar a fonte OBD.");
            this.connectionSignature = "";
            return;
          }
          const retry = event.target.closest("[data-obd-retry]");
          if (retry) {
            const address = retry.dataset.obdRetry || "";
            if (!address) {
              this.alert("Selecione novamente o ELM327 para repetir a conexão.");
              return;
            }
            const result = this.api.connectObd(address);
            if ((result == null ? void 0 : result.ok) === false) this.alert(result.error || "Não foi possível repetir a conexão OBD.");
            this.connectionSignature = "";
            return;
          }
          const connect = event.target.closest("[data-obd-connect]");
          if (connect) {
            const result = this.api.connectObd(connect.dataset.obdConnect || "");
            if ((result == null ? void 0 : result.ok) === false) this.alert(result.error || "N\xE3o foi poss\xEDvel iniciar a conex\xE3o OBD.");
            this.connectionSignature = "";
          }
        });
        (_f = document.getElementById("obdPowerCard")) == null ? void 0 : _f.addEventListener("click", (event) => {
          if (event.target.closest("[data-obd-battery-request]")) this.api.requestBatteryOptimizationExemption();
          if (event.target.closest("[data-obd-overlay-request]")) this.api.requestOverlayPermissionAndEnable();
          if (event.target.closest("[data-obd-overlay-enable]")) this.api.setTelemetryOverlayEnabled(true);
          if (event.target.closest("[data-obd-overlay-disable]")) this.api.setTelemetryOverlayEnabled(false);
          this.powerSignature = "";
          this.renderPower();
        });
      }
      setView(view) {
        var _a, _b;
        this.view = ["observe", "setup"].includes(view) ? view : "observe";
        (_a = this.root) == null ? void 0 : _a.querySelectorAll("[data-obd-view]").forEach((button) => button.classList.toggle("active", button.dataset.obdView === this.view));
        (_b = this.root) == null ? void 0 : _b.querySelectorAll("[data-obd-panel]").forEach((panel) => panel.classList.toggle("active", panel.dataset.obdPanel === this.view));
        if (this.view === "setup") {
          const state = this.store.get();
          this.renderConnection(state);
          this.renderSensors(state.obd || {});
          this.renderPower();
        }
      }
      readWitness() {
        var _a, _b;
        const now = Date.now();
        if (now - this.lastWitnessAt < 700) return this.lastWitness;
        this.lastWitnessAt = now;
        const snapshot = this.api.fullSnapshot() || {};
        const witness = snapshot.obd_witness || snapshot.obdWitness || {};
        if (witness && typeof witness === "object") this.lastWitness = witness;
        if (((_b = (_a = this.api).isDemo) == null ? void 0 : _b.call(_a)) && !Object.keys(this.lastWitness || {}).length) {
          this.lastWitness = {
            state: "INSUFFICIENT",
            stftPct: 8.4,
            gnvStftPct: 8.4,
            quality: 0.86,
            gasolineSamples: 0,
            gnvSamples: 7,
            rpm: 1840,
            map_bar: 0.56,
            petrol_ms: 4.42,
            fuel: "GNV",
            skew_ms: 34
          };
        }
        return this.lastWitness || {};
      }
      render(state) {
        var _a, _b, _c;
        if (!this.root) return;
        const obd = state.obd || {};
        const stage = String(obd.connectionStage || obd.state || obd.status || "").toUpperCase();
        const connected = obd.connected === true || ["LIVE", "CONNECTED", "CONECTADO", "REMOTO AO VIVO"].includes(stage);
        const connecting = ["RFCOMM", "ELM_INIT", "PROTOCOL", "STFT_READY", "CONECTANDO"].includes(stage);
        const liveStft = connected ? finite(first(obd, ["stft", "shortTermFuelTrim", "short_term_fuel_trim"])) : null;
        const witness = this.readWitness();
        const gnvStft = finite(witness.gnvStftPct);
        const stateView = witnessState(witness.state, gnvStft);
        const quality = finite(witness.quality);
        const gnvSamples = Math.max(0, Number(witness.gnvSamples || 0));
        const pairedRpm = finite(witness.rpm);
        const pairedMap = finite((_a = witness.map_bar) != null ? _a : witness.mapBar);
        const pairedPetrol = finite(witness.correctionPercent);
        const pairedFuel = witness.gnvModeDeclared === true ? "GNV" : "\u2014";
        const skew = finite((_c = witness.skew_ms) != null ? _c : witness.skewMs);
        text("obdLiveStft", liveStft === null ? "\u2014" : "".concat(liveStft > 0 ? "+" : "").concat(fmt(liveStft, 1)));
        text("obdLiveStatus", connected ? liveStft === null ? "ELM online \xB7 aguardando resposta 0106" : "Leitura 0106 em tempo real" : connecting ? "Conectando \xB7 ".concat(stage) : "Aguardando ELM327");
        text("obdWitnessState", stateView.label);
        text("obdWitnessDetail", stateView.detail);
        text("obdWitnessQuality", quality === null ? "\u2014" : "".concat(Math.round(Math.max(0, Math.min(1, quality)) * 100), "%"));
        text("obdGnvStft", trimLabel(gnvStft));
        text("obdStftMeaning", gnvStft === null ? "?" : Math.abs(gnvStft) <= 0.5 ? "NEUTRO" : gnvStft > 0 ? "POBRE" : "RICO");
        text("obdWitnessMode", "OBD independente");
        text("obdWitnessSamples", "".concat(gnvSamples));
        text("obdPairedRpm", pairedRpm === null ? "\u2014" : Math.round(pairedRpm).toLocaleString("pt-BR"));
        text("obdPairedMap", pairedMap === null ? "\u2014" : "".concat(fmt(pairedMap, 2), " bar"));
        text("obdPairedPetrol", pairedPetrol === null ? "\u2014" : "".concat(pairedPetrol > 0 ? "+" : "").concat(fmt(pairedPetrol, 1), "%"));
        text("obdPairedFuel", pairedFuel);
        text("obdPairSkew", skew === null ? "sem pareamento" : "\u0394t ".concat(Math.round(skew), " ms"));
        const card = document.getElementById("obdWitnessCard");
        if (card) card.dataset.state = stateView.tone;
        const status = document.getElementById("obdStatusPill");
        if (status) {
          status.dataset.online = connected ? "true" : "false";
          text("obdStatusPill", connected ? "OBD online" : connecting ? "OBD conectando" : "OBD offline");
        }
        if (this.view === "setup") {
          this.renderConnection(state);
          this.renderSensors(obd);
          this.renderPower();
        }
      }
      renderConnection(state) {
        const host = document.getElementById("obdConnectionCenter");
        if (!host) return;
        const obd = state.obd || {};
        const devicesState = state.obdDevices || {};
        const mode = String(obd.mode || "off").toLowerCase();
        const gnvModeDeclared = obd.gnvModeDeclared === true;
        const rawDevices = Array.isArray(devicesState.devices) ? devicesState.devices : [];
        const permissionRequired = obd.permissionRequired === true || devicesState.permissionRequired === true;
        const bluetoothEnabled = devicesState.bluetoothEnabled !== false && devicesState.enabled !== false && obd.bluetoothEnabled !== false;
        const stage = String(obd.connectionStage || obd.state || "").toUpperCase();
        const connected = obd.connected === true || ["LIVE", "CONNECTED", "CONECTADO"].includes(stage);
        const connecting = ["RFCOMM", "ELM_INIT", "PROTOCOL", "STFT_READY", "CONECTANDO"].includes(stage);
        const selected = String(obd.deviceAddress || obd.lastDeviceAddress || devicesState.lastDeviceAddress || "");
        const diagnostic = obd.diagnostic || {};
        const knownStages = ["PERMISSION", "RFCOMM", "ELM_INIT", "PROTOCOL", "STFT_READY", "LIVE", "ERROR"];
        const errorCode = String(obd.errorCode || diagnostic.errorCode || "");
        const diagnosticDetail = String(obd.detail || diagnostic.detail || obd.lastError || "");
        const retryable = obd.retryable === true || diagnostic.retryable === true;
        const protocol = String(diagnostic.protocolMode || obd.protocol || "");
        const supported = Array.isArray(diagnostic.supportedStandardPids) ? diagnostic.supportedStandardPids : [];
        const devices = rawDevices.slice().sort((a, b) => {
          const score = (item) => (item.connected ? 3 : 0) + (String(item.address || "") === selected ? 2 : 0);
          return score(b) - score(a);
        });
        const signature = JSON.stringify({ mode, gnvModeDeclared, devices, permissionRequired, bluetoothEnabled, connected, connecting, selected, protocol, supported: supported.length, stage, errorCode, diagnosticDetail, retryable, knownStages });
        if (signature === this.connectionSignature) return;
        this.connectionSignature = signature;
        const deviceRows = devices.length ? devices.map((device) => '\n        <div class="witness-device-row">\n          <div><small>'.concat(device.connected ? "CONECTADO" : String(device.address || "") === selected ? "USADO POR \xDALTIMO" : "PAREADO", "</small><b>").concat(escapeHtml(device.name || "ELM327"), "</b><span>").concat(escapeHtml(device.address || ""), '</span></div>\n          <button type="button" class="').concat(String(device.address || "") === selected ? "primary" : "secondary", '" data-obd-connect="').concat(escapeHtml(device.address || ""), '" ').concat(device.connected ? "disabled" : "", ">").concat(device.connected ? "Em uso" : String(device.address || "") === selected ? "Reconectar" : "Conectar", "</button>\n        </div>")).join("") : '<p class="empty-copy">Nenhum ELM327 pareado no Android.</p>';
        host.innerHTML = '\n        <div class="witness-setup-heading"><div><small>CONEX\xC3O</small><h3>'.concat(connected ? "ELM pronto para STFT" : connecting ? "Conectando ao carro" : "Escolha a fonte OBD", "</h3></div><span>").concat(escapeHtml(stage || "IDLE"), '</span></div>\n        <div class="witness-connection-progress"><span data-state="').concat(permissionRequired ? "waiting" : "done", '">Bluetooth</span><span data-state="').concat(connected ? "done" : connecting ? "active" : "waiting", '">ELM327</span><span data-state="').concat(protocol ? "done" : connected ? "active" : "waiting", '">Protocolo</span><span data-state="').concat(connected ? "done" : "waiting", '">STFT 0106</span></div>\n        <div class="witness-mode-buttons"><button type="button" data-obd-gnv="true" class="'.concat(gnvModeDeclared ? "active" : "", '">Aprender GNV</button><button type="button" data-obd-gnv="false">Pausar aprendizado</button></div><div class="witness-mode-buttons"><button type="button" data-obd-mode="local" class="').concat(mode === "local" ? "active" : "", '">ELM Bluetooth</button><button type="button" data-obd-mode="remote" class="').concat(mode === "remote" ? "active" : "", '">Omegas Link</button><button type="button" data-obd-mode="off" class="').concat(mode === "off" ? "active" : "", '">Desativado</button></div>\n        ').concat(permissionRequired ? '<p class="witness-note">Autorize o Bluetooth para acessar os dispositivos pareados.</p>' : !bluetoothEnabled ? '<p class="witness-note">Ligue o Bluetooth do Android para continuar.</p>' : deviceRows, '\n        <p class="witness-note">OBD usa seu próprio RPM, MAP e STFT. Confirme GNV antes de aprender.</p>');
        if (errorCode || diagnosticDetail) {
          const diagnosticBox = document.createElement("div");
          diagnosticBox.className = "witness-note";
          diagnosticBox.dataset.obdDiagnostic = "true";
          diagnosticBox.innerHTML = "<b>Falha ".concat(escapeHtml(errorCode || stage || "OBD"), "</b><span>Etapa ").concat(escapeHtml(stage || "desconhecida"), " · ").concat(escapeHtml(diagnosticDetail || "Sem detalhe nativo"), "</span>").concat(retryable && selected ? '<button type="button" class="secondary" data-obd-retry="'.concat(escapeHtml(selected), '">Tentar novamente</button>') : "");
          host.appendChild(diagnosticBox);
        }
      }
      renderSensors(obd) {
        const host = document.getElementById("obdSensorList");
        if (!host) return;
        const diagnostic = obd.diagnostic || {};
        const supported = Array.isArray(diagnostic.supportedStandardPids) ? diagnostic.supportedStandardPids : [];
        const pidRows = Array.isArray(diagnostic.pids) ? diagnostic.pids : [];
        const stftPid = pidRows.find((item) => String(item.command || "").replace(/\s/g, "").toUpperCase() === "0106") || null;
        const hasStftSupport = supported.some((item) => Number(item) === 6 || String(item).toUpperCase() === "0106");
        const signature = JSON.stringify({ protocol: diagnostic.protocolMode, cycle: diagnostic.lastCycleMs, rate: diagnostic.pollRateHz, stftPid, hasStftSupport, stage: obd.connectionStage });
        if (signature === this.sensorSignature) return;
        this.sensorSignature = signature;
        host.innerHTML = '\n        <div class="witness-setup-heading"><div><small>SINAL CIENT\xCDFICO</small><h3>STFT Bank 1</h3></div><span>Mode 01</span></div>\n        <div class="witness-sensor-main"><strong>0106</strong><div><b>'.concat((stftPid == null ? void 0 : stftPid.responded) ? "respondendo" : hasStftSupport ? "suportado" : "aguardando suporte", "</b><span>").concat((stftPid == null ? void 0 : stftPid.responded) ? "".concat(fmt(stftPid.latencyMs, 0), " ms na \xFAltima resposta") : "\xFAnico PID usado como evid\xEAncia", '</span></div></div>\n        <div class="witness-diagnostic-facts"><span>Protocolo <b>').concat(escapeHtml(diagnostic.protocolMode || "aguardando"), "</b></span><span>Ciclo <b>").concat(finite(diagnostic.lastCycleMs) === null ? "\u2014" : "".concat(fmt(diagnostic.lastCycleMs, 0), " ms"), "</b></span><span>Taxa <b>").concat(finite(diagnostic.pollRateHz) === null ? "\u2014" : "".concat(fmt(diagnostic.pollRateHz, 1), " Hz"), "</b></span></div>");
      }
      renderPower() {
        var _a, _b, _c, _d;
        const host = document.getElementById("obdPowerCard");
        if (!host) return;
        const battery = ((_b = (_a = this.api).batteryOptimizationStatus) == null ? void 0 : _b.call(_a)) || {};
        const overlay = ((_d = (_c = this.api).overlayStatus) == null ? void 0 : _d.call(_c)) || {};
        const signature = JSON.stringify({ battery, overlay });
        if (signature === this.powerSignature) return;
        this.powerSignature = signature;
        host.innerHTML = '\n        <div class="witness-setup-heading"><div><small>MULTIM\xCDDIA</small><h3>Sess\xE3o cont\xEDnua</h3></div></div>\n        <div class="witness-runtime-row"><div><small>BATERIA</small><b>'.concat(battery.ignoringOptimizations === true ? "Sem restri\xE7\xE3o do Android" : "Android pode limitar a sess\xE3o", "</b></div>").concat(battery.supported !== false && battery.ignoringOptimizations !== true ? '<button type="button" class="secondary" data-obd-battery-request>Permitir</button>' : "", '</div>\n        <div class="witness-runtime-row"><div><small>TELEMETRIA FLUTUANTE</small><b>').concat(overlay.visible === true ? "Ativa" : "Desativada", "</b></div>").concat(overlay.visible === true ? '<button type="button" class="quiet-button" data-obd-overlay-disable>Desativar</button>' : overlay.permissionGranted === true ? '<button type="button" class="secondary" data-obd-overlay-enable>Ativar</button>' : '<button type="button" class="secondary" data-obd-overlay-request>Autorizar</button>', "</div>");
      }
      alert(message) {
        this.store.patch({ alert: { level: "warning", message: String(message || "Opera\xE7\xE3o OBD indispon\xEDvel") } });
      }
    }
    ns.ObdScreen = ObdScreen;
  })(typeof window !== "undefined" ? window : globalThis);
})();
