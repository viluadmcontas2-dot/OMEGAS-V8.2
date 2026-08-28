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
    for (const name of names) if (source && source[name] !== undefined && source[name] !== null) return source[name];
    return null;
  }
  function statMean(cell, name) { return finite(cell && cell[name] && cell[name].mean); }
  function trimLabel(value) {
    const n = finite(value);
    return n === null ? '—' : `${n > 0 ? '+' : ''}${fmt(n, 1)}%`;
  }
  function nearest(value, bins) {
    if (!Array.isArray(bins) || !bins.length || finite(value) === null) return -1;
    let best = 0; let distance = Infinity;
    bins.forEach((item, index) => { const d = Math.abs(Number(item) - Number(value)); if (d < distance) { best = index; distance = d; } });
    return best;
  }

  class ObdScreen {
    constructor(store, api) {
      this.store = store;
      this.api = api;
      this.root = document.querySelector('[data-screen="obd"]');
      this.view = 'observe';
      this.mapLayer = 'comparison';
      this.selectedCellKey = null;
      this.lastMaps = {};
      this.lastMapsAt = 0;
      this.mapsIntervalMs = 1500;
      this.mapSignature = '';
      this.connectionSignature = '';
      this.sensorSignature = '';
      this.powerSignature = '';
      this.bind();
    }

    bind() {
      document.querySelectorAll('[data-obd-view]').forEach(button => button.addEventListener('click', () => this.setView(button.dataset.obdView || 'observe')));
      document.querySelectorAll('[data-obd-go]').forEach(button => button.addEventListener('click', () => this.setView(button.dataset.obdGo || 'observe')));
      document.getElementById('obdPermissionButton')?.addEventListener('click', () => this.api.requestBluetoothPermission());
      document.getElementById('obdDisconnectButton')?.addEventListener('click', () => {
        const result = this.api.disconnectObd();
        this.connectionSignature = '';
        if (result?.ok === false) this.alert(result.error || 'Não foi possível desconectar o OBD.');
      });
      document.getElementById('obdRefreshButton')?.addEventListener('click', () => {
        this.lastMapsAt = 0; this.mapSignature = ''; this.connectionSignature = ''; this.sensorSignature = '';
        this.store.patch({ obd: this.api.obd() || {}, obdDevices: this.api.obdDevices() || {} });
        this.render(this.store.get());
      });
      document.getElementById('obdIndependentMap')?.addEventListener('click', event => {
        const cell = event.target.closest('[data-obd-cell-key]');
        if (!cell) return;
        this.selectedCellKey = cell.dataset.obdCellKey || null;
        this.mapSignature = '';
        this.renderMap(this.readMaps(false), this.store.get());
      });
      document.querySelector('.obd-map-tabs')?.addEventListener('click', event => {
        const button = event.target.closest('[data-obd-map-layer]');
        if (!button) return;
        this.mapLayer = button.dataset.obdMapLayer || 'comparison';
        document.querySelectorAll('[data-obd-map-layer]').forEach(item => item.classList.toggle('active', item === button));
        this.mapSignature = '';
        this.renderMap(this.readMaps(false), this.store.get());
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
      document.getElementById('obdConnectionCenter')?.addEventListener('change', event => {
        if (!event.target.matches('[data-obd-manual-fuel]')) return;
        const result = this.api.setObdManualFuel(event.target.value || '');
        if (result?.ok === false) this.alert(result.error || 'Não foi possível alterar o rótulo de combustível.');
        this.connectionSignature = '';
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
      this.view = ['observe', 'map', 'setup'].includes(view) ? view : 'observe';
      document.querySelectorAll('[data-obd-view]').forEach(button => button.classList.toggle('active', button.dataset.obdView === this.view));
      document.querySelectorAll('[data-obd-panel]').forEach(panel => panel.classList.toggle('active', panel.dataset.obdPanel === this.view));
      if (this.view === 'map') this.renderMap(this.readMaps(true), this.store.get());
      if (this.view === 'setup') {
        this.renderConnection(this.store.get());
        this.renderPower();
        this.renderSensors(this.store.get().obd || {});
      }
    }

    readMaps(force) {
      const now = Date.now();
      if (!force && now - this.lastMapsAt < this.mapsIntervalMs) return this.lastMaps;
      this.lastMapsAt = now;
      this.lastMaps = this.api.obdMaps() || {};
      return this.lastMaps;
    }

    render(state) {
      if (!this.root) return;
      const obd = state.obd || {};
      const hub = state.status || {};
      const connected = obd.connected === true || ['CONNECTED', 'CONECTADO', 'REMOTO AO VIVO'].includes(String(obd.state || obd.status || '').toUpperCase());
      const connecting = String(obd.state || '').toUpperCase() === 'CONECTANDO';
      const stft = finite(first(obd, ['stft', 'shortTermFuelTrim', 'short_term_fuel_trim']));
      const ltft = finite(first(obd, ['ltft', 'longTermFuelTrim', 'long_term_fuel_trim']));
      const obdRpm = finite(first(obd, ['rpm', 'engineRpm', 'engine_rpm']));
      const map = finite(first(obd, ['mapKpa', 'map', 'mapBar', 'map_bar', 'manifoldPressure']));
      const load = finite(first(obd, ['load', 'engineLoad', 'calculatedLoad', 'calculated_load']));
      const pedal = finite(first(obd, ['pedal', 'acceleratorPedal', 'accelerator_pedal', 'throttle']));
      const maf = finite(first(obd, ['mafGps', 'maf', 'massAirFlow']));
      const coolant = finite(first(obd, ['coolant', 'coolantC', 'waterC']));
      const voltage = finite(first(obd, ['moduleVoltageV', 'controlModuleVoltage', 'voltage']));
      const petrolMs = finite(hub.petrolMs);
      const rpm = finite(hub.rpm) ?? obdRpm;
      const updatedAt = finite(first(obd, ['updatedAt', 'lastUpdatedAt']));
      const explicitAge = finite(first(obd, ['ageMs', 'telemetryAgeMs', 'lastAgeMs']));
      const age = explicitAge !== null ? explicitAge : updatedAt !== null ? Math.max(0, Date.now() - updatedAt) : null;

      text('obdStft', trimLabel(stft)); text('obdLtft', trimLabel(ltft));
      text('obdRpm', rpm === null ? '—' : Math.round(rpm).toLocaleString('pt-BR'));
      text('obdPetrol', petrolMs === null || petrolMs <= 0 ? '—' : `${fmt(petrolMs, 2)} ms`);
      text('obdMap', map === null ? '—' : `${fmt(map, 0)} kPa`); text('obdLoad', load === null ? '—' : `${fmt(load, 0)}%`);
      text('obdPedal', pedal === null ? '—' : `${fmt(pedal, 0)}%`); text('obdMaf', maf === null ? '—' : `${fmt(maf, 1)} g/s`);
      text('obdCoolant', coolant === null ? '—' : `${fmt(coolant, 0)} °C`); text('obdVoltage', voltage === null ? '—' : `${fmt(voltage, 2)} V`);
      text('obdConnection', connected ? (obd.mode === 'remote' ? 'Omegas Link conectado' : 'Bluetooth conectado') : connecting ? 'Conectando ao ELM327…' : 'OBD desconectado');
      text('obdFreshness', age === null ? '—' : age < 1000 ? `${Math.round(age)} ms` : `${fmt(age / 1000, 1)} s`);

      const status = document.getElementById('obdStatusPill');
      if (status) {
        status.dataset.online = connected ? 'true' : 'false';
        text('obdStatusPill', connected ? 'OBD online' : connecting ? 'OBD conectando' : 'OBD offline');
      }

      const maps = this.readMaps(false);
      const key = this.currentKey(maps, rpm, petrolMs);
      text('obdLiveCell', key || '—');
      text('obdLiveDecision', key ? (connected ? 'STFT sendo associado à mesma faixa RPM × Petrol Inj.' : 'Faixa MP48 disponível; aguardando OBD.') : 'Aguardando OBD + MP48 com Petrol Inj. válido.');
      text('obdLiveFuel', String(hub.fuelState || obd.fuel || '—'));
      const validation = key ? maps.validation?.[key] : null;
      text('obdLiveDelta', validation && finite(validation.gnv) !== null ? trimLabel(validation.gnv) : '—');

      if (this.view === 'map') this.renderMap(maps, state);
      if (this.view === 'setup') {
        this.renderConnection(state);
        this.renderPower();
        this.renderSensors(obd);
      }
    }

    currentKey(maps, rpm, petrolMs) {
      const rpmBins = Array.isArray(maps?.rpmBins) ? maps.rpmBins : [];
      const petrolBins = Array.isArray(maps?.petrolMsBins) ? maps.petrolMsBins : [];
      if (finite(rpm) === null || finite(petrolMs) === null || Number(petrolMs) <= 0 || rpmBins.length !== 12 || petrolBins.length !== 12) return null;
      const column = nearest(rpm, rpmBins);
      const row = nearest(petrolMs, petrolBins);
      return column < 0 || row < 0 ? null : `${column}:${row}`;
    }

    renderMap(maps, state) {
      const host = document.getElementById('obdIndependentMap');
      if (!host) return;
      const rpmBins = Array.isArray(maps?.rpmBins) ? maps.rpmBins : [];
      const petrolBins = Array.isArray(maps?.petrolMsBins) ? maps.petrolMsBins : [];
      if (rpmBins.length !== 12 || petrolBins.length !== 12) {
        host.innerHTML = '<div class="obd-map-corner">—</div><div class="obd-map-axis">Aguardando mapa OBD RPM × Petrol Inj.</div>';
        text('obdMapSummary', 'Sem eixos físicos ainda');
        return;
      }
      const gasoline = maps.gasoline || {};
      const gnv = maps.gnv || {};
      const validation = maps.validation || {};
      const hub = state.status || {};
      const currentKey = this.currentKey(maps, finite(hub.rpm), finite(hub.petrolMs));
      const signature = `${this.mapLayer}|${this.selectedCellKey || ''}|${currentKey || ''}|${maps.updatedAt || 0}`;
      if (signature === this.mapSignature) return;
      this.mapSignature = signature;

      let ready = 0; let populated = 0;
      const parts = ['<div class="obd-map-corner">ms\\rpm</div>'];
      rpmBins.forEach(value => parts.push(`<div class="obd-map-axis">${Math.round(Number(value)).toLocaleString('pt-BR')}</div>`));
      petrolBins.forEach((petrolValue, row) => {
        parts.push(`<div class="obd-map-axis">${fmt(petrolValue, 1)}</div>`);
        rpmBins.forEach((_, column) => {
          const key = `${column}:${row}`;
          const petrolCell = gasoline[key] || null;
          const gnvCell = gnv[key] || null;
          const compare = validation[key] || null;
          let value = null; let samples = 0; let readyCell = false; let status = '';
          if (this.mapLayer === 'gasoline') {
            value = statMean(petrolCell, 'stft'); samples = Number(petrolCell?.stft?.physicalSamples ?? petrolCell?.qualified ?? 0); readyCell = samples > 0;
          } else if (this.mapLayer === 'gnv') {
            value = statMean(gnvCell, 'stft'); samples = Number(gnvCell?.stft?.physicalSamples ?? gnvCell?.qualified ?? 0); readyCell = samples > 0;
          } else {
            value = finite(compare?.gnv); samples = Number(compare?.gnvSamples || 0); readyCell = samples > 0; status = String(compare?.status || '');
          }
          if (value !== null || samples > 0) populated += 1;
          if (readyCell) ready += 1;
          const visual = value === null ? 'empty' : Math.abs(value) <= 2 ? 'neutral' : value > 0 ? 'positive' : 'negative';
          const classes = ['obd-map-cell']; if (key === currentKey) classes.push('current'); if (key === this.selectedCellKey) classes.push('selected');
          const title = `RPM ${rpmBins[column]} · Petrol Inj. ${fmt(petrolValue, 1)} ms · ${value === null ? 'sem STFT' : `STFT ${trimLabel(value)}`}${status ? ` · ${status}` : ''}`;
          parts.push(`<button type="button" class="${classes.join(' ')}" data-obd-cell-key="${key}" data-state="${readyCell ? 'ready' : visual}" title="${escapeHtml(title)}"><b>${value === null ? '·' : trimLabel(value)}</b><small>${samples || ''}</small></button>`);
        });
      });
      host.innerHTML = parts.join('');
      const layerLabel = this.mapLayer === 'gasoline' ? 'Gasolina' : this.mapLayer === 'gnv' ? 'GNV' : 'GNV direto · alvo STFT 0%';
      text('obdMapSummary', `${layerLabel} · ${populated} células · ${ready} com evidência`);
      this.renderCellDetail(maps, currentKey);
    }

    renderCellDetail(maps, currentKey) {
      const host = document.getElementById('obdCellDetail');
      if (!host) return;
      const key = this.selectedCellKey || currentKey;
      if (!key) {
        host.innerHTML = '<b>Toque em uma célula</b><span>STFT, LTFT e contexto daquela faixa aparecem aqui.</span>';
        return;
      }
      const [column, row] = key.split(':').map(Number);
      const rpmBins = maps.rpmBins || []; const petrolBins = maps.petrolMsBins || [];
      const petrol = maps.gasoline?.[key] || {}; const gnv = maps.gnv?.[key] || {}; const comparison = maps.validation?.[key] || {};
      const html = `<header><div><small>CÉLULA ${escapeHtml(key)}</small><b>${Math.round(Number(rpmBins[column] || 0)).toLocaleString('pt-BR')} rpm · ${fmt(petrolBins[row], 2)} ms</b></div><span>${key === currentKey ? 'AGORA' : 'MEMÓRIA'}</span></header><div class="obd-cell-facts"><div><small>GASOLINA STFT</small><b>${trimLabel(statMean(petrol, 'stft'))}</b><span>${Number(comparison.gasolineSamples || petrol.qualified || 0)} amostras</span></div><div><small>GNV STFT</small><b>${trimLabel(statMean(gnv, 'stft'))}</b><span>${Number(comparison.gnvSamples || gnv.qualified || 0)} amostras</span></div><div><small>SINAL GNV</small><b>${escapeHtml(comparison.status || '—')}</b><span>alvo direto: STFT próximo de 0%</span></div><div><small>LTFT GNV</small><b>${trimLabel(statMean(gnv, 'ltft'))}</b><span>contexto</span></div><div><small>ÁGUA GNV</small><b>${statMean(gnv, 'coolant') === null ? '—' : `${fmt(statMean(gnv, 'coolant'), 0)} °C`}</b><span>contexto</span></div><div><small>VELOCIDADE</small><b>${statMean(gnv, 'speed') === null ? '—' : `${fmt(statMean(gnv, 'speed'), 0)} km/h`}</b><span>contexto</span></div></div>`;
      if (host.innerHTML !== html) host.innerHTML = html;
    }

    renderConnection(state) {
      const host = document.getElementById('obdConnectionCenter');
      if (!host) return;
      const obd = state.obd || {};
      const devicesState = state.obdDevices || {};
      const mode = String(obd.mode || 'off').toLowerCase();
      const rawDevices = Array.isArray(devicesState.devices) ? devicesState.devices : [];
      const permissionRequired = obd.permissionRequired === true || devicesState.permissionRequired === true;
      const bluetoothEnabled = devicesState.bluetoothEnabled !== false && obd.bluetoothEnabled !== false;
      const connected = obd.connected === true || String(obd.state || '').toUpperCase() === 'CONECTADO';
      const connecting = String(obd.state || '').toUpperCase() === 'CONECTANDO';
      const selected = String(obd.deviceAddress || obd.lastDeviceAddress || devicesState.lastDeviceAddress || '');
      const diagnostic = obd.diagnostic || {};
      const protocol = String(diagnostic.protocolMode || obd.protocol || '');
      const supported = Array.isArray(diagnostic.supportedStandardPids) ? diagnostic.supportedStandardPids : [];
      const mp48Available = state.status?.usbConnected === true && state.status?.engineReady === true;
      const devices = rawDevices.slice().sort((a, b) => {
        const score = item => (item.connected ? 3 : 0) + (String(item.address || '') === selected ? 2 : 0);
        return score(b) - score(a);
      });
      const signature = JSON.stringify({ mode, devices, permissionRequired, bluetoothEnabled, connected, connecting, selected, protocol, supported: supported.length, mp48Available, manualFuel: obd.manualFuel || '' });
      if (signature === this.connectionSignature) return;
      this.connectionSignature = signature;

      const bluetoothState = permissionRequired || !bluetoothEnabled ? 'waiting' : 'done';
      const elmState = connected ? 'done' : connecting ? 'active' : 'waiting';
      const protocolState = connected && protocol ? 'done' : connected ? 'active' : 'waiting';
      const sensorState = connected && supported.length ? 'done' : connected && protocol ? 'active' : 'waiting';
      const last = devices.find(device => String(device.address || '') === selected) || devices.find(device => device.connected) || devices[0] || null;
      const deviceRows = devices.length ? devices.map(device => `<div class="obd-device-card"><div><small>${device.connected ? 'CONECTADO' : String(device.address || '') === selected ? 'USADO POR ÚLTIMO' : 'PAREADO'}</small><b>${escapeHtml(device.name || 'ELM327')}</b><span>${escapeHtml(device.address || '')}</span></div><button type="button" class="${String(device.address || '') === selected ? 'primary' : 'secondary'}" data-obd-connect="${escapeHtml(device.address || '')}" ${device.connected ? 'disabled' : ''}>${device.connected ? 'Em uso' : String(device.address || '') === selected ? 'Reconectar' : 'Conectar'}</button></div>`).join('') : '<p class="empty-copy">Nenhum ELM327 pareado no Android.</p>';
      const compactConnected = connected && last ? `<div class="obd-device-card"><div><small>CONECTADO</small><b>${escapeHtml(last.name || 'ELM327')}</b><span>${escapeHtml(protocol || 'protocolo em negociação')} · ${supported.length} PIDs</span></div><button type="button" class="quiet-button" data-obd-connect="${escapeHtml(last.address || '')}" disabled>Em uso</button></div>` : '';

      host.innerHTML = `<div class="editor-heading"><div><small>FONTE OBD</small><h3>${connected ? 'Conexão pronta' : connecting ? 'Conectando ao carro' : 'Conectar ao OBD'}</h3></div></div>
        <div class="obd-connection-progress"><span data-state="${bluetoothState === 'done' ? 'done' : 'active'}">1 · Bluetooth</span><span data-state="${elmState}">2 · ELM327</span><span data-state="${protocolState}">3 · Protocolo</span><span data-state="${sensorState}">4 · Sensores</span></div>
        <div class="obd-mode-buttons"><button type="button" data-obd-mode="local" class="${mode === 'local' ? 'active' : ''}">ELM Bluetooth</button><button type="button" data-obd-mode="remote" class="${mode === 'remote' ? 'active' : ''}">Omegas Link</button><button type="button" data-obd-mode="off" class="${mode === 'off' ? 'active' : ''}">Desativado</button></div>
        ${permissionRequired ? '<p class="obd-note">Autorize o Bluetooth para o app acessar os dispositivos pareados.</p>' : !bluetoothEnabled ? '<p class="obd-note">Ligue o Bluetooth do Android para continuar.</p>' : connected ? compactConnected : deviceRows}
        <label class="field-label"><span>Combustível quando MP48 não estiver disponível</span><select data-obd-manual-fuel ${mp48Available ? 'disabled' : ''}><option value="">Não informado</option><option value="GASOLINA" ${String(obd.manualFuel || '').toUpperCase() === 'GASOLINA' ? 'selected' : ''}>Gasolina</option><option value="GNV" ${String(obd.manualFuel || '').toUpperCase() === 'GNV' ? 'selected' : ''}>GNV</option></select></label>
        <p class="empty-copy">${mp48Available ? 'MP48 é a autoridade do combustível e fornece Petrol Inj. para os eixos.' : 'Sem MP48 não existe Petrol Inj. confiável; o mapa RPM × Petrol Inj. não inventa célula.'}</p>`;
    }

    renderPower() {
      const host = document.getElementById('obdPowerCard');
      if (!host) return;
      const battery = this.api.batteryOptimizationStatus?.() || {};
      const overlay = this.api.overlayStatus?.() || {};
      const signature = JSON.stringify({ battery, overlay });
      if (signature === this.powerSignature) return;
      this.powerSignature = signature;
      host.innerHTML = `<div class="editor-heading"><div><small>MULTIMÍDIA</small><h3>Execução em segundo plano</h3></div></div><div class="obd-runtime-controls"><div class="obd-runtime-row"><div class="obd-power-copy"><small>BATERIA</small><b>${battery.ignoringOptimizations === true ? 'Sem restrição do Android' : 'Android pode limitar a sessão'}</b><span>Use somente para sessões longas.</span></div>${battery.supported !== false && battery.ignoringOptimizations !== true ? '<button type="button" class="secondary" data-obd-battery-request>Permitir</button>' : ''}</div><div class="obd-runtime-row"><div class="obd-power-copy"><small>FLUTUANTE</small><b>${overlay.visible === true ? 'Ativo' : 'Desativado'}</b><span>Preservado; não altera coleta.</span></div>${overlay.visible === true ? '<button type="button" class="quiet-button" data-obd-overlay-disable>Desativar</button>' : overlay.permissionGranted === true ? '<button type="button" class="secondary" data-obd-overlay-enable>Ativar</button>' : '<button type="button" class="secondary" data-obd-overlay-request>Autorizar</button>'}</div></div>`;
    }

    renderSensors(obd) {
      const host = document.getElementById('obdSensorList');
      if (!host) return;
      const diagnostic = obd.diagnostic || {};
      const supported = Array.isArray(diagnostic.supportedStandardPids) ? diagnostic.supportedStandardPids : [];
      const rows = Array.isArray(diagnostic.pids) ? diagnostic.pids.slice(0, 28).map(item => `<div class="sensor-row"><span>${escapeHtml(item.command || 'PID')}</span><b>${item.responded ? 'respondendo' : 'sem resposta'}</b><small>${item.responded ? `${fmt(item.latencyMs, 0)} ms` : escapeHtml(item.error || '')}</small></div>`).join('') : '';
      const html = `<div class="obd-diagnostic-summary"><span>Protocolo <b>${escapeHtml(diagnostic.protocolMode || 'aguardando')}</b></span><span>Ciclo <b>${finite(diagnostic.lastCycleMs) === null ? '—' : `${fmt(diagnostic.lastCycleMs, 0)} ms`}</b></span><span>Taxa <b>${finite(diagnostic.pollRateHz) === null ? '—' : `${fmt(diagnostic.pollRateHz, 1)} Hz`}</b></span><span>PIDs <b>${supported.length}</b></span></div><div class="obd-pid-rows">${rows || '<p class="empty-copy">Aguardando diagnóstico do adaptador.</p>'}</div>`;
      if (html === this.sensorSignature) return;
      this.sensorSignature = html;
      host.innerHTML = html;
    }

    alert(message) { this.store.patch({ alert: { level: 'warning', message: String(message || 'Operação OBD indisponível') } }); }
  }

  ns.ObdScreen = ObdScreen;
})(typeof window !== 'undefined' ? window : globalThis);
