(() => {
  'use strict';

  const missing = '—';
  const byId = id => document.getElementById(id);
  const number = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 3 });
  const isObject = value => value !== null && typeof value === 'object' && !Array.isArray(value);
  const isText = value => typeof value === 'string' && value.trim() !== '';
  let screen = 'live';
  let history = [];
  let vault = [];
  let vaultError = '';

  function display(value, unit = '') {
    if (Number.isFinite(value)) return number.format(value) + unit;
    return isText(value) ? value : missing;
  }

  function describe(value) {
    if (Array.isArray(value)) return value.map(describe).join(' · ') || missing;
    if (isObject(value)) {
      return Object.entries(value).map(([key, item]) => key + ' · ' + describe(item)).join('\n') || missing;
    }
    if (typeof value === 'boolean') return value ? 'Sim' : 'Não';
    return display(value);
  }

  function show(id, value) {
    const node = byId(id);
    if (node.textContent !== value) node.textContent = value;
  }

  function notice(id, value) {
    show(id, value);
    byId(id).hidden = !value;
  }

  function request(method, ...args) {
    const bridge = window.DynoNative;
    if (typeof bridge?.[method] !== 'function') throw new Error('Conexão com o app indisponível');
    let result;
    try {
      result = JSON.parse(bridge[method](...args));
    } catch {
      throw new Error('Falha na resposta de ' + method);
    }
    if (isObject(result) && (isText(result.error) || result.ok === false || result.success === false)) {
      throw new Error(result.error || result.message || 'A ação não foi concluída');
    }
    return result;
  }

  const mp48Fields = {
    rpm: ['rpm', ' rpm'], map: ['mapBar', ' bar'], pressure: ['gasPressureBar', ' bar'],
    petrol: ['petrolMs', ' ms'], gas: ['gasMs', ' ms'], fuel: ['fuel', '']
  };
  const obdFields = {
    rpm: ['rpm', ' rpm'], tps: ['tps', '%'], map: ['mapKpa', ' kPa'],
    stft: ['stft', '%'], ltft: ['ltft', '%'], ect: ['ect', ' °C'], speed: ['speed', ' km/h']
  };

  function renderSource(id, source, status, fields) {
    const connected = source?.connected;
    const fallback = connected === true ? 'Conectado' : connected === false ? 'Desconectado' : missing;
    show(id + '-status', typeof status === 'boolean' ? (status ? 'Conectado' : 'Desconectado') :
      isText(status) ? status : fallback);
    show(id + '-age', 'Idade da leitura · ' + display(source?.ageMs, ' ms'));
    for (const [key, [field, unit]] of Object.entries(fields)) {
      show(id + '-' + key, display(connected === false ? undefined : source?.[field], unit));
    }
  }

  function refresh() {
    let data = {};
    let error = '';
    try {
      data = request('getSnapshot');
      if (!isObject(data)) throw new Error('Snapshot inválido');
    } catch (failure) {
      data = {};
      error = failure.message;
    }
    const { mp48, obd, fusion, status } = data;
    notice('snapshot-status', error || (isText(status?.error) ? status.error : ''));
    renderSource('mp48', mp48, status?.mp48, mp48Fields);
    renderSource('obd', obd, status?.obd, obdFields);

    const capturing = status?.capturing;
    show('capture-status', 'Captura · ' + (capturing === true ? 'Em andamento' :
      capturing === false ? 'Parada' : missing));
    byId('start').hidden = capturing === true;
    byId('start').disabled = Boolean(error) || capturing === true;
    byId('stop').hidden = capturing !== true;
    show('quality', 'Qualidade · ' + display(fusion?.quality));
    show('comparison-title', display(fusion?.title));
    show('comparison', describe(fusion?.comparison));
    show('lag', display(fusion?.lagMs, ' ms'));
    show('roughness', display(fusion?.roughness));
    show('confidence', display(fusion?.confidence));
    show('advice', describe(fusion?.advice));

    history = Array.isArray(data.history) ? data.history : [];
    if (screen === 'live') drawChart();
  }

  function drawChart() {
    const canvas = byId('chart');
    const { width, height } = canvas.getBoundingClientRect();
    if (width < 60 || height < 40) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    const scale = Math.min(window.devicePixelRatio || 1, 2);
    const pixelWidth = Math.round(width * scale);
    const pixelHeight = Math.round(height * scale);
    if (canvas.width !== pixelWidth) canvas.width = pixelWidth;
    if (canvas.height !== pixelHeight) canvas.height = pixelHeight;
    ctx.setTransform(scale, 0, 0, scale, 0, 0);
    ctx.clearRect(0, 0, width, height);

    const series = new Map();
    let first = Infinity;
    let last = -Infinity;
    let peak = 0;
    for (const sample of history) {
      if (!Number.isFinite(sample?.t) || !Number.isFinite(sample.rpm) || sample.rpm < 0) continue;
      const source = display(sample.source);
      if (!series.has(source)) series.set(source, []);
      series.get(source).push(sample);
      first = Math.min(first, sample.t);
      last = Math.max(last, sample.t);
      peak = Math.max(peak, sample.rpm);
    }
    if (!series.size) {
      show('chart-legend', '— Sem leituras de rotação');
      return;
    }

    const left = 48;
    const top = 12;
    const bottom = height - 12;
    const ceiling = Math.max(1, Math.ceil(peak / 500)) * 500;
    ctx.font = '12px system-ui';
    ctx.fillStyle = '#a3b9d2';
    ctx.strokeStyle = '#30455e';
    ctx.lineWidth = 1;
    for (const fraction of [0, 0.5, 1]) {
      const y = bottom - fraction * (bottom - top);
      ctx.fillText(number.format(ceiling * fraction), 0, y + 4);
      ctx.beginPath();
      ctx.moveTo(left, y);
      ctx.lineTo(width - 8, y);
      ctx.stroke();
    }
    const colors = ['#69e5ff', '#849bff', '#fcc36e', '#95e2aa'];
    let index = 0;
    const legend = [];
    for (const [source, samples] of series) {
      const color = colors[index++ % colors.length];
      const key = document.createElement('span');
      key.style.color = color;
      key.textContent = '● ' + source + '  ';
      legend.push(key);
      samples.sort((a, b) => a.t - b.t);
      ctx.fillStyle = ctx.strokeStyle = color;
      ctx.lineWidth = 2;
      ctx.beginPath();
      samples.forEach((sample, i) => {
        const x = left + (last === first ? 0.5 : (sample.t - first) / (last - first)) * (width - left - 8);
        const y = bottom - sample.rpm / ceiling * (bottom - top);
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
        if (samples.length === 1) ctx.fillRect(x - 2, y - 2, 4, 4);
      });
      ctx.stroke();
    }
    byId('chart-legend').replaceChildren(...legend);
  }

  function action(method, success, ...args) {
    try {
      const result = request(method, ...args);
      if (!isObject(result)) throw new Error('Resposta inválida de ' + method);
      notice('action-status', isText(result.message) ? result.message : success);
      return result;
    } catch (error) {
      notice('action-status', error.message);
    }
  }

  function updateObdButton() {
    byId('connect-obd').disabled = !byId('devices').value;
  }

  function scanObd() {
    const select = byId('devices');
    const placeholder = document.createElement('option');
    placeholder.value = '';
    placeholder.textContent = 'Adaptador OBD';
    select.replaceChildren(placeholder);
    select.value = '';
    updateObdButton();
    try {
      const devices = request('listObdDevices');
      if (!Array.isArray(devices)) throw new Error('Lista OBD inválida');
      let count = 0;
      for (const device of devices) {
        if (!isText(device?.id)) continue;
        const option = document.createElement('option');
        option.value = device.id;
        option.textContent = isText(device.name) ? device.name : device.id;
        select.append(option);
        count++;
      }
      notice('action-status', count ? '' : 'Nenhum adaptador OBD encontrado');
    } catch (error) {
      notice('action-status', error.message);
    }
  }

  function normalize(text) {
    return text.toLocaleLowerCase('pt-BR').normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  }

  function filterVault() {
    const words = normalize(byId('search').value.trim()).split(/\s+/);
    const matches = vault.filter(event => words.every(word => normalize(event).includes(word)));
    const rows = matches.map(event => {
      const row = document.createElement('li');
      row.textContent = event;
      return row;
    });
    byId('events').replaceChildren(...rows);
    show('vault-status', vaultError || (vault.length ? matches.length + ' de ' + vault.length + ' eventos' :
      'Nenhum evento na captura'));
  }

  function loadVault() {
    vault = [];
    vaultError = '';
    try {
      const events = request('getVault');
      if (!Array.isArray(events)) throw new Error('Lista de eventos inválida');
      vault = events.map(describe);
    } catch (error) {
      vaultError = error.message;
    }
    filterVault();
  }

  for (const id of ['live', 'compare', 'vault']) {
    byId('tab-' + id).addEventListener('click', () => {
      screen = id;
      for (const panel of ['live', 'compare', 'vault']) {
        byId(panel).hidden = panel !== id;
        byId('tab-' + panel).setAttribute('aria-pressed', String(panel === id));
      }
      if (id === 'vault') loadVault();
      if (id === 'live') drawChart();
    });
  }
  byId('scan-obd').addEventListener('click', scanObd);
  byId('devices').addEventListener('change', updateObdButton);
  byId('connect-obd').addEventListener('click', () => {
    const id = byId('devices').value;
    if (id) action('connectObd', 'Solicitação de conexão OBD enviada', id);
    refresh();
  });
  byId('connect-mp48').addEventListener('click', () => {
    action('connectMp48', 'Solicitação de conexão MP48 enviada');
    refresh();
  });
  byId('start').addEventListener('click', () => {
    action('startCapture', 'Solicitação de captura enviada');
    refresh();
    if (screen === 'vault') loadVault();
  });
  byId('stop').addEventListener('click', () => {
    action('stopCapture', 'Solicitação de encerramento enviada');
    refresh();
    if (screen === 'vault') loadVault();
  });
  byId('search').addEventListener('input', filterVault);
  byId('export').addEventListener('click', () => action('exportVault', 'Solicitação de exportação enviada'));
  window.addEventListener('resize', () => {
    if (screen === 'live') drawChart();
  });

  refresh();
  setInterval(refresh, 500);
})();
