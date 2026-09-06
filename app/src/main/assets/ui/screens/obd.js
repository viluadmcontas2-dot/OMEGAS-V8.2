(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  function finite(value) { return Number.isFinite(Number(value)) ? Number(value) : null; }
  function fmt(value, digits) {
    const n = finite(value);
    return n === null ? '—' : n.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits });
  }
  function text(id, value) {
    const node = document.getElementById(id);
    if (!node) return;
    const next = value == null ? '—' : String(value);
    if (node.textContent !== next) node.textContent = next;
  }
  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[char]));
  }
  function first(source, names) {
    for (const name of names) {
      if (source && source[name] !== undefined && source[name] !== null) return source[name];
    }
    return null;
  }
  function trimLabel(value) {
    const n = finite(value);
    return n === null ? '—' : `${n > 0 ? '+' : ''}${fmt(n, 1)}%`;
  }
  function ensureStyles() {
    if (document.querySelector('link[data-witness-multimedia]')) return;
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = 'styles-witness-multimedia.css';
    link.dataset.witnessMultimedia = 'true';
    document.head.appendChild(link);
  }
  function witnessState(raw) {
    const value = String(raw || 'UNAVAILABLE').toUpperCase();
    if (value === 'SUPPORTS') return { label: 'CONFIRMA BLUE', tone: 'supports', detail: 'Direção física concorda com a comparação Blue.' };
    if (value === 'CONFLICTS') return { label: 'CONFLITA', tone: 'conflicts', detail: 'A evidência física discorda; a confiança não é acelerada.' };
    if (value === 'INSUFFICIENT') return { label: 'AINDA INSUFICIENTE', tone: 'insufficient', detail: 'Faltam pares compatíveis para concluir.' };
    return { label: 'INDISPONÍVEL', tone: 'unavailable', detail: 'Sem evidência física suficiente neste momento.' };
  }

  class ObdScreen {
    constructor(store, api) {
      this.store = store;
      this.api = api;
      this.root = document.querySelector('[data-screen="obd"]');
      this.view = 'observe';
      this.connectionSignature = '';
      this.sensorSignature = '';
      this.powerSignature = '';
      this.lastWitness = {};
      this.lastWitnessAt = 0;
      ensureStyles();
      this.installLayout();
      this.bind();
    }

    installLayout() {
      if (!this.root) return;
      this.root.classList.add('multimedia-obd-screen');
      this.root.innerHTML = `
        <div class="witness-page-intro">
          <div><small>OBD WITNESS</small><h2>STFT como prova física</h2><p>Gasolina é a referência física compatível; GNV é comparado contra ela.</p></div>
          <div class="witness-head-actions">
            <span id="obdStatusPill" class="status-pill" data-online="false">OBD offline</span>
            <button id="obdRefreshButton" type="button" class="secondary">Atualizar</button>
            <button id="obdDisconnectButton" type="button" class="quiet-button">Desconectar</button>
          </div>
        </div>

        <div class="witness-view-tabs" role="tablist" aria-label="Visão OBD">
          <button type="button" data-obd-view="observe" class="active">Witness</button>
          <button type="button" data-obd-view="setup">Conexão</button>
        </div>

        <div class="witness-panel active" data-obd-panel="observe">
          <section class="witness-live-card">
            <div class="witness-live-main">
              <small>STFT BANK 1 · PID 0106</small>
              <div><strong id="obdLiveStft">—</strong><em>%</em></div>
              <p id="obdLiveStatus">Aguardando ELM327</p>
            </div>
            <div class="witness-authority-note">
              <b>Somente evidência</b>
              <span>OBD observa STFT e não escreve K.</span>
              <span>RPM, MAP, Petrol Inj. e combustível vêm da MP48.</span>
            </div>
          </section>

          <section class="witness-result-card" id="obdWitnessCard" data-state="unavailable">
            <div class="witness-result-heading">
              <div><small>WITNESS</small><b id="obdWitnessState">INDISPONÍVEL</b><span id="obdWitnessDetail">Sem evidência física suficiente neste momento.</span></div>
              <div class="witness-quality"><small>QUALIDADE</small><b id="obdWitnessQuality">—</b></div>
            </div>
            <div class="witness-comparison-grid">
              <article><small>REFERÊNCIA GASOLINA</small><b id="obdGasolineReference">—</b><span>STFT físico compatível</span></article>
              <article><small>STFT NO GNV</small><b id="obdGnvStft">—</b><span>mesma condição física</span></article>
              <article class="witness-residual"><small>RESIDUAL GNV − GASOLINA</small><b id="obdResidual">—</b><span>pontos percentuais</span></article>
              <article><small>AMOSTRAS</small><b id="obdWitnessSamples">—</b><span>gasolina · GNV</span></article>
            </div>
          </section>

          <section class="witness-pair-card">
            <header><div><small>PAREAMENTO MP48</small><h3>Condição física da observação</h3></div><span id="obdPairSkew">—</span></header>
            <div class="witness-pair-grid">
              <article><small>RPM MP48</small><b id="obdPairedRpm">—</b></article>
              <article><small>MAP MP48</small><b id="obdPairedMap">—</b></article>
              <article><small>PETROL INJ. MP48</small><b id="obdPairedPetrol">—</b></article>
              <article><small>COMBUSTÍVEL MP48</small><b id="obdPairedFuel">—</b></article>
            </div>
          </section>
        </div>

        <div class="witness-panel" data-obd-panel="setup">
          <div class="witness-setup-grid">
            <section id="obdConnectionCenter" class="witness-setup-card"></section>
            <section id="obdSensorList" class="witness-setup-card"></section>
            <section id="obdPowerCard" class="witness-setup-card"></section>
          </div>
          <button id="obdPermissionButton" type="button" class="secondary witness-permission-button">Autorizar Bluetooth</button>
        </div>`;
    }

    bind() {
      this.root?.querySelectorAll('[data-obd-view]').forEach(button => button.addEventListener('click', () => this.setView(button.dataset.obdView || 'observe')));
      document.getElementById('obdPermissionButton')?.addEventListener('click', () => this.api.requestBluetoothPermission());
      document.getElementById('obdDisconnectButton')?.addEventListener('click', () => {
        const result = this.api.disconnectObd();
        this.connectionSignature = '';
        if (result?.ok === false) this.alert(result.error || 'Não foi possível desconectar o OBD.');
      });
      document.getElementById('obdRefreshButton')?.addEventListener('click', () => {
        this.connectionSignature = '';
        this.sensorSignature = '';
        this.lastWitnessAt = 0;
        this.store.patch({ obd: this.api.obd() || {}, obdDevices: this.api.obdDevices() || {} });
        this.render(this.store.get());
      });
      document.getElementById('obdConnectionCenter')?.addEventListener('click', event => {
        const mode = event.target.closest('[data-obd-mode]');
        if (mode) {
          const result = this.api.setObdMode(mode.dataset.obdMode || 'off');
          if (result?.ok === false) this.alert(result.error || 'Não foi possível alterar a fonte OBD.');
          this.connectionSignature = '';
          return;
        }
        const connect = event.target.closest('[data-obd-connect]');
        if (connect) {
          const result = this.api.connectObd(connect.dataset.obdConnect || '');
          if (result?.ok === false) this.alert(result.error || 'Não foi possível iniciar a conexão OBD.');
          this.connectionSignature = '';
        }
      });
      document.getElementById('obdPowerCard')?.addEventListener('click', event => {
        if (event.target.closest('[data-obd-battery-request]')) this.api.requestBatteryOptimizationExemption();
        if (event.target.closest('[data-obd-overlay-request]')) this.api.requestOverlayPermissionAndEnable();
        if (event.target.closest('[data-obd-overlay-enable]')) this.api.setTelemetryOverlayEnabled(true);
        if (event.target.closest('[data-obd-overlay-disable]')) this.api.setTelemetryOverlayEnabled(false);
        this.powerSignature = '';
        this.renderPower();
      });
    }

    setView(view) {
      this.view = ['observe', 'setup'].includes(view) ? view : 'observe';
      this.root?.querySelectorAll('[data-obd-view]').forEach(button => button.classList.toggle('active', button.dataset.obdView === this.view));
      this.root?.querySelectorAll('[data-obd-panel]').forEach(panel => panel.classList.toggle('active', panel.dataset.obdPanel === this.view));
      if (this.view === 'setup') {
        const state = this.store.get();
        this.renderConnection(state);
        this.renderSensors(state.obd || {});
        this.renderPower();
      }
    }

    readWitness() {
      const now = Date.now();
      if (now - this.lastWitnessAt < 700) return this.lastWitness;
      this.lastWitnessAt = now;
      const snapshot = this.api.fullSnapshot() || {};
      const witness = snapshot.obd_witness || snapshot.obdWitness || {};
      if (witness && typeof witness === 'object') this.lastWitness = witness;
      if (this.api.isDemo?.() && !Object.keys(this.lastWitness || {}).length) {
        this.lastWitness = {
          state: 'SUPPORTS', stftPct: 8.4, gasolineReferencePct: 1.7, gnvStftPct: 8.4,
          residualPp: 6.7, quality: 0.86, gasolineSamples: 9, gnvSamples: 7,
          rpm: 1840, map_bar: 0.56, petrol_ms: 4.42, fuel: 'GNV', skew_ms: 34,
        };
      }
      return this.lastWitness || {};
    }

    render(state) {
      if (!this.root) return;
      const obd = state.obd || {};
      const stage = String(obd.connectionStage || obd.state || obd.status || '').toUpperCase();
      const connected = obd.connected === true || ['LIVE', 'CONNECTED', 'CONECTADO', 'REMOTO AO VIVO'].includes(stage);
      const connecting = ['RFCOMM', 'ELM_INIT', 'PROTOCOL', 'STFT_READY', 'CONECTANDO'].includes(stage);
      const liveStft = connected ? finite(first(obd, ['stft', 'shortTermFuelTrim', 'short_term_fuel_trim'])) : null;
      const witness = this.readWitness();
      const stateView = witnessState(witness.state);
      const gasolineReference = finite(witness.gasolineReferencePct);
      const gnvStft = finite(witness.gnvStftPct);
      const residual = finite(witness.residualPp);
      const quality = finite(witness.quality);
      const gasolineSamples = Math.max(0, Number(witness.gasolineSamples || 0));
      const gnvSamples = Math.max(0, Number(witness.gnvSamples || 0));
      const pairedRpm = finite(witness.rpm);
      const pairedMap = finite(witness.map_bar ?? witness.mapBar);
      const pairedPetrol = finite(witness.petrol_ms ?? witness.petrolMs);
      const pairedFuel = String(witness.fuel || '—').replace('PETROL', 'GASOLINA').replace('CNG', 'GNV');
      const skew = finite(witness.skew_ms ?? witness.skewMs);

      text('obdLiveStft', liveStft === null ? '—' : `${liveStft > 0 ? '+' : ''}${fmt(liveStft, 1)}`);
      text('obdLiveStatus', connected ? (liveStft === null ? 'ELM online · aguardando resposta 0106' : 'Leitura 0106 em tempo real') : connecting ? `Conectando · ${stage}` : 'Aguardando ELM327');
      text('obdWitnessState', stateView.label);
      text('obdWitnessDetail', stateView.detail);
      text('obdWitnessQuality', quality === null ? '—' : `${Math.round(Math.max(0, Math.min(1, quality)) * 100)}%`);
      text('obdGasolineReference', trimLabel(gasolineReference));
      text('obdGnvStft', trimLabel(gnvStft));
      text('obdResidual', residual === null ? '—' : `${residual > 0 ? '+' : ''}${fmt(residual, 1)} pp`);
      text('obdWitnessSamples', `${gasolineSamples} · ${gnvSamples}`);
      text('obdPairedRpm', pairedRpm === null ? '—' : Math.round(pairedRpm).toLocaleString('pt-BR'));
      text('obdPairedMap', pairedMap === null ? '—' : `${fmt(pairedMap, 2)} bar`);
      text('obdPairedPetrol', pairedPetrol === null ? '—' : `${fmt(pairedPetrol, 2)} ms`);
      text('obdPairedFuel', pairedFuel);
      text('obdPairSkew', skew === null ? 'sem pareamento' : `Δt ${Math.round(skew)} ms`);

      const card = document.getElementById('obdWitnessCard');
      if (card) card.dataset.state = stateView.tone;
      const status = document.getElementById('obdStatusPill');
      if (status) {
        status.dataset.online = connected ? 'true' : 'false';
        text('obdStatusPill', connected ? 'OBD online' : connecting ? 'OBD conectando' : 'OBD offline');
      }

      if (this.view === 'setup') {
        this.renderConnection(state);
        this.renderSensors(obd);
        this.renderPower();
      }
    }

    renderConnection(state) {
      const host = document.getElementById('obdConnectionCenter');
      if (!host) return;
      const obd = state.obd || {};
      const devicesState = state.obdDevices || {};
      const mode = String(obd.mode || 'off').toLowerCase();
      const rawDevices = Array.isArray(devicesState.devices) ? devicesState.devices : [];
      const permissionRequired = obd.permissionRequired === true || devicesState.permissionRequired === true;
      const bluetoothEnabled = devicesState.bluetoothEnabled !== false && devicesState.enabled !== false && obd.bluetoothEnabled !== false;
      const stage = String(obd.connectionStage || obd.state || '').toUpperCase();
      const connected = obd.connected === true || ['LIVE', 'CONNECTED', 'CONECTADO'].includes(stage);
      const connecting = ['RFCOMM', 'ELM_INIT', 'PROTOCOL', 'STFT_READY', 'CONECTANDO'].includes(stage);
      const selected = String(obd.deviceAddress || obd.lastDeviceAddress || devicesState.lastDeviceAddress || '');
      const diagnostic = obd.diagnostic || {};
      const protocol = String(diagnostic.protocolMode || obd.protocol || '');
      const supported = Array.isArray(diagnostic.supportedStandardPids) ? diagnostic.supportedStandardPids : [];
      const devices = rawDevices.slice().sort((a, b) => {
        const score = item => (item.connected ? 3 : 0) + (String(item.address || '') === selected ? 2 : 0);
        return score(b) - score(a);
      });
      const signature = JSON.stringify({ mode, devices, permissionRequired, bluetoothEnabled, connected, connecting, selected, protocol, supported: supported.length, stage });
      if (signature === this.connectionSignature) return;
      this.connectionSignature = signature;

      const deviceRows = devices.length ? devices.map(device => `
        <div class="witness-device-row">
          <div><small>${device.connected ? 'CONECTADO' : String(device.address || '') === selected ? 'USADO POR ÚLTIMO' : 'PAREADO'}</small><b>${escapeHtml(device.name || 'ELM327')}</b><span>${escapeHtml(device.address || '')}</span></div>
          <button type="button" class="${String(device.address || '') === selected ? 'primary' : 'secondary'}" data-obd-connect="${escapeHtml(device.address || '')}" ${device.connected ? 'disabled' : ''}>${device.connected ? 'Em uso' : String(device.address || '') === selected ? 'Reconectar' : 'Conectar'}</button>
        </div>`).join('') : '<p class="empty-copy">Nenhum ELM327 pareado no Android.</p>';

      host.innerHTML = `
        <div class="witness-setup-heading"><div><small>CONEXÃO</small><h3>${connected ? 'ELM pronto para STFT' : connecting ? 'Conectando ao carro' : 'Escolha a fonte OBD'}</h3></div><span>${escapeHtml(stage || 'IDLE')}</span></div>
        <div class="witness-connection-progress"><span data-state="${permissionRequired ? 'waiting' : 'done'}">Bluetooth</span><span data-state="${connected ? 'done' : connecting ? 'active' : 'waiting'}">ELM327</span><span data-state="${protocol ? 'done' : connected ? 'active' : 'waiting'}">Protocolo</span><span data-state="${connected ? 'done' : 'waiting'}">STFT 0106</span></div>
        <div class="witness-mode-buttons"><button type="button" data-obd-mode="local" class="${mode === 'local' ? 'active' : ''}">ELM Bluetooth</button><button type="button" data-obd-mode="remote" class="${mode === 'remote' ? 'active' : ''}">Omegas Link</button><button type="button" data-obd-mode="off" class="${mode === 'off' ? 'active' : ''}">Desativado</button></div>
        ${permissionRequired ? '<p class="witness-note">Autorize o Bluetooth para acessar os dispositivos pareados.</p>' : !bluetoothEnabled ? '<p class="witness-note">Ligue o Bluetooth do Android para continuar.</p>' : deviceRows}
        <p class="witness-note">MP48 permanece autoridade de condição física e combustível; OBD fornece apenas STFT.</p>`;
    }

    renderSensors(obd) {
      const host = document.getElementById('obdSensorList');
      if (!host) return;
      const diagnostic = obd.diagnostic || {};
      const supported = Array.isArray(diagnostic.supportedStandardPids) ? diagnostic.supportedStandardPids : [];
      const pidRows = Array.isArray(diagnostic.pids) ? diagnostic.pids : [];
      const stftPid = pidRows.find(item => String(item.command || '').replace(/\s/g, '').toUpperCase() === '0106') || null;
      const hasStftSupport = supported.some(item => Number(item) === 6 || String(item).toUpperCase() === '0106');
      const signature = JSON.stringify({ protocol: diagnostic.protocolMode, cycle: diagnostic.lastCycleMs, rate: diagnostic.pollRateHz, stftPid, hasStftSupport, stage: obd.connectionStage });
      if (signature === this.sensorSignature) return;
      this.sensorSignature = signature;
      host.innerHTML = `
        <div class="witness-setup-heading"><div><small>SINAL CIENTÍFICO</small><h3>STFT Bank 1</h3></div><span>Mode 01</span></div>
        <div class="witness-sensor-main"><strong>0106</strong><div><b>${stftPid?.responded ? 'respondendo' : hasStftSupport ? 'suportado' : 'aguardando suporte'}</b><span>${stftPid?.responded ? `${fmt(stftPid.latencyMs, 0)} ms na última resposta` : 'único PID usado como evidência'}</span></div></div>
        <div class="witness-diagnostic-facts"><span>Protocolo <b>${escapeHtml(diagnostic.protocolMode || 'aguardando')}</b></span><span>Ciclo <b>${finite(diagnostic.lastCycleMs) === null ? '—' : `${fmt(diagnostic.lastCycleMs, 0)} ms`}</b></span><span>Taxa <b>${finite(diagnostic.pollRateHz) === null ? '—' : `${fmt(diagnostic.pollRateHz, 1)} Hz`}</b></span></div>`;
    }

    renderPower() {
      const host = document.getElementById('obdPowerCard');
      if (!host) return;
      const battery = this.api.batteryOptimizationStatus?.() || {};
      const overlay = this.api.overlayStatus?.() || {};
      const signature = JSON.stringify({ battery, overlay });
      if (signature === this.powerSignature) return;
      this.powerSignature = signature;
      host.innerHTML = `
        <div class="witness-setup-heading"><div><small>MULTIMÍDIA</small><h3>Sessão contínua</h3></div></div>
        <div class="witness-runtime-row"><div><small>BATERIA</small><b>${battery.ignoringOptimizations === true ? 'Sem restrição do Android' : 'Android pode limitar a sessão'}</b></div>${battery.supported !== false && battery.ignoringOptimizations !== true ? '<button type="button" class="secondary" data-obd-battery-request>Permitir</button>' : ''}</div>
        <div class="witness-runtime-row"><div><small>TELEMETRIA FLUTUANTE</small><b>${overlay.visible === true ? 'Ativa' : 'Desativada'}</b></div>${overlay.visible === true ? '<button type="button" class="quiet-button" data-obd-overlay-disable>Desativar</button>' : overlay.permissionGranted === true ? '<button type="button" class="secondary" data-obd-overlay-enable>Ativar</button>' : '<button type="button" class="secondary" data-obd-overlay-request>Autorizar</button>'}</div>`;
    }

    alert(message) {
      this.store.patch({ alert: { level: 'warning', message: String(message || 'Operação OBD indisponível') } });
    }
  }

  ns.ObdScreen = ObdScreen;
})(typeof window !== 'undefined' ? window : globalThis);
