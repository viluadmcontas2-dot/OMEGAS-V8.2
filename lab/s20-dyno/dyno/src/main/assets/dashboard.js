(() => {
  'use strict';

  const missing = '—';
  const byId = id => document.getElementById(id);
  const number = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 3 });
  const isObject = value => value !== null && typeof value === 'object' && !Array.isArray(value);
  const isText = value => typeof value === 'string' && value.trim() !== '';
  let screen = 'live';
  let investigatorLastPoll = 0;

  let history = [];
  let vault = [];
  let vaultError = '';
  let stories = [];
  let storyKey = '';
  const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
  const waitingComparison = 'Aguardando leitura comparável';
  const comparisonNeeds = 'Faltam trechos com os dois combustíveis confirmados, em faixas semelhantes de rotação, esforço e temperatura do motor.';
  const states = new Map([
    ['capturing', 'Em andamento'], ['capturing_passive', 'Em andamento · Escuta passiva'],
    ['complete', 'Encerrada'], ['interrupted', 'Interrompida']
  ]);
  const sources = { obd: 'Leitor do motor (OBD)', mp48: 'Central do gás (MP48)' };

  function display(value, unit = '') {
    if (Number.isFinite(value)) return number.format(value) + unit;
    return isText(value) ? value : missing;
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
    if (isObject(result) && ((isText(result.error) && result.error !== missing) || result.ok === false || result.success === false)) {
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
    stft: ['stft', '%'], ltft: ['ltft', '%'], ect: ['ect', ' °C'], speed: ['speed', ' km/h'],
    ethanol: ['ethanolPercent', '%'], voltage: ['ecuVoltage', ' V'], fueltype: ['fuelTypeCode', '']
  };

  function renderSource(id, source, status, fields) {
    const connected = source?.connected;
    const online = connected === true || status === true || status === 'conectado';
    const reading = Object.values(fields).some(([field]) => display(source?.[field]) !== missing);
    const offline = connected === false || status === false || status === 'desconectado';
    show(id + '-status', online ? (reading ? 'Recebendo leituras' : 'Conectado, sem leitura recente') :
      isText(status) && !['desconectado', missing].includes(status) ? status :
      offline ? 'Desconectado, sem leitura ao vivo' : 'Sem informação da conexão');
    show(id + '-age', 'Idade da leitura · ' + display(source?.ageMs, ' ms'));
    for (const [key, [field, unit]] of Object.entries(fields)) {
      show(id + '-' + key, display(connected === false ? undefined : source?.[field], unit));
    }
  }

  function refreshInvestigator() {
    const now = Date.now();
    if (now - investigatorLastPoll < 3500) return;
    investigatorLastPoll = now;
    try {
      const d = request('getIntelligence');
      const counts = d?.labelCounts || {};
      const pairs = d?.controlled || {};
      const mode = d?.obdOnly || {};
      const gemini = d?.gemini || {};
      show('investigator-status', d.runtimeStatus === 'OBSERVANDO' ?
        'Recebendo e cruzando dados reais' : 'Aguardando transmissão do S20');
      show('investigator-counts', 'Gasolina: ' + (counts.gasolina || 0) +
        ' · GNV: ' + (counts.GNV || 0));
      show('investigator-pairs', Number.isInteger(pairs.matchedPairs) ?
        number.format(pairs.matchedPairs) : '—');
      show('investigator-stft', Number.isFinite(pairs.medianDeltaStftGnvMinusPetrol) ?
        display(pairs.medianDeltaStftGnvMinusPetrol, '% (GNV − gasolina)') :
        'Aguardando comparações controladas');
      show('investigator-fuel', mode.decision === 'CANDIDATO_NAO_VALIDADO' ?
        'Padrão candidato. Ainda não comprova combustível.' :
        'Ainda não é possível identificar só pelo OBD');
      show('investigator-events', Array.isArray(d.events) && d.events.length ?
        d.events.slice(-3).map(e => e.kind === 'STFT_ABRUPTO' ?
          'STFT mudou de ' + display(e.fromPct, '%') + ' para ' +
          display(e.toPct, '%') + ' · ' + (e.contexto || 'contexto desconhecido') :
          'Mudança MP48: ' + (e.fromHex || '?') + ' → ' + (e.toHex || '?'))
          .join(' | ') : 'Aguardando eventos mensuráveis.');
      show('investigator-gemini', gemini.status === 'SUGESTAO_NAO_E_PROVA' ?
        'Gemini · sugestão (não é prova): ' + String(gemini.text || '').slice(0,650) :
        gemini.status === 'CHAVE_CONFIGURADA' ?
        'Gemini configurado. Só recebe resumos de evidências.' :
        gemini.status === 'SEM_CHAVE' ? 'Gemini não configurado (análise local ativa).' :
        'Gemini · ' + (gemini.status || 'aguardando configuração'));
    } catch {
      show('investigator-status', 'Motor de análise local indisponível');
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
    refreshInvestigator();
    try {
      const power = request('batteryStatus');
      byId('battery-setup').hidden = power?.unrestricted === true;
    } catch { byId('battery-setup').hidden = true; }
    const connectionError = status?.error;
    const connectionHelp = !isText(connectionError) || connectionError === missing ? '' :
      connectionError.includes('Adaptador USB MP48 não encontrado') ?
        'MP48 não encontrada. Conecte a interface USB da MP48 e tente novamente.' :
      connectionError.includes('Acesso ao Bluetooth não autorizado') ?
        'Bluetooth não autorizado. Permita o acesso ao Bluetooth nas configurações do aplicativo.' :
      connectionError.includes('read failed') || connectionError.includes('socket might closed') ?
        'OBD Bluetooth não respondeu. Reconexão automática em andamento.' :
        'Conexão ou leitura: ' + connectionError;
    notice('snapshot-status', error ? 'Leituras indisponíveis (snapshot).' : connectionHelp);
    renderSource('mp48', mp48, status?.mp48, mp48Fields);
    renderSource('obd', obd, status?.obd, obdFields);

    const capturing = status?.capturing;
    const hasFreshRpm = [obd, mp48].some(source =>
      source?.connected === true && Number.isFinite(source?.rpm) &&
      Number.isFinite(source?.ageMs) && source.ageMs >= 0 && source.ageMs <= 1500);
    show('capture-status', capturing === true ? 'Captura em andamento' :
      error ? 'Leituras indisponíveis' :
      hasFreshRpm ? 'Pronto para registrar: RPM real recebido' :
      'Aguardando RPM real do OBD ou MP48');
    byId('start').hidden = capturing === true || !hasFreshRpm;
    byId('start').disabled = Boolean(error) || capturing === true || !hasFreshRpm;
    byId('stop').hidden = capturing !== true;
    for (const id of ['obd', 'mp48']) {
      const current = status?.[id];
      const isConnected = data[id]?.connected === true;
      const pending = current === 'conectando' || current === 'aguardando permissão' ||
        (typeof current === 'string' && current.startsWith('reconectando'));
      const button = byId('connect-' + id);
      button.disabled = isConnected || pending;
      button.textContent = isConnected ? (id === 'obd' ? 'OBD conectado' : 'MP48 conectada') :
        pending ? (id === 'obd' && typeof current === 'string' &&
          current.startsWith('reconectando') ? 'Reconexão automática ativa' : 'Conectando…') :
        id === 'obd' ? 'Conectar OBD Bluetooth' : 'Conectar MP48 USB';
    }
    const mp48Rpm = byId('mp48-rpm').textContent;
    const obdRpm = byId('obd-rpm').textContent;
    show('live-rpm', mp48Rpm !== missing ? mp48Rpm : obdRpm);
    const fuel = byId('mp48-fuel').textContent;
    if (fuel === 'GASOLINA') show('mp48-fuel', 'Gasolina');
    notice('rotation-source', mp48Rpm !== missing ? sources.mp48 : obdRpm !== missing ? sources.obd : '');

    const matched = Number.isInteger(fusion?.matchedEvents) && fusion.matchedEvents > 0;
    const hasAdvice = matched && isText(fusion?.advice) && fusion.advice !== missing;
    show('comparison-title', matched ? 'Resposta da aceleração' : waitingComparison);
    show('comparison', matched ?
      [sampleCount(fusion.matchedEvents, 'par comparável', 'pares comparáveis'),
        isText(fusion.comparison) && fusion.comparison !== missing ? fusion.comparison : ''].filter(Boolean).join(' · ') :
      comparisonNeeds);
    show('quality', 'Qualidade da leitura · ' + display(fusion?.quality));
    show('lag', display(matched ? fusion?.lagMs : undefined, ' ms'));
    show('roughness', display(matched ? fusion?.roughness : undefined));
    show('confidence', display(matched ? fusion?.confidence : undefined));
    show('advice', hasAdvice ? fusion.advice : 'Ainda não podemos recomendar um ajuste. ' +
      (matched ? 'Os trechos comparados não sustentam uma sugestão de Curva K.' :
        'Precisamos comparar a resposta do carro com gasolina e GNV em condições semelhantes.'));

    history = Array.isArray(data.history) ? data.history : [];
    updateStories(data, matched, Boolean(error));
    const hasHistory = history.some(validSample);
    byId('chart').hidden = !hasHistory;
    byId('rotation-history').hidden = !hasHistory;
    if (!hasHistory) show('chart-legend', 'Sem leituras de rotação');
    if (hasHistory && screen === 'live') drawChart();
  }

  function validSample(sample) {
    return Number.isFinite(sample?.t) && Number.isFinite(sample.rpm) && sample.rpm >= 0;
  }

  function drawChart() {
    const canvas = byId('chart');
    if (canvas.hidden) return;
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
      if (!validSample(sample)) continue;
      const source = sources[sample.source?.toLowerCase?.()] || 'Fonte não identificada';
      if (!series.has(source)) series.set(source, []);
      series.get(source).push(sample);
      first = Math.min(first, sample.t);
      last = Math.max(last, sample.t);
      peak = Math.max(peak, sample.rpm);
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

  function connect(method, ...args) {
    try {
      const result = request(method, ...args);
      if (!isObject(result)) throw new Error('Resposta inválida de ' + method);
      // "Solicitação recebida" não significa "conexão estabelecida".
      // O estado confirmado vem do snapshot da fonte, após resposta do Android.
      notice('action-status', '');
      refresh();
      return result;
    } catch (error) {
      notice('action-status', error.message);
    }
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

  function normalize(text) {
    return text.toLocaleLowerCase('pt-BR').normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  }

  function element(tag, label, className) {
    const node = document.createElement(tag);
    if (label) node.textContent = label;
    if (className) node.className = className;
    return node;
  }

  function sampleCount(count, singular, plural) {
    return Number.isInteger(count) && count >= 0 ?
      number.format(count) + ' ' + (count === 1 ? singular : plural) : missing;
  }

  function dateLabel(timestamp) {
    if (!Number.isFinite(timestamp)) return missing;
    const date = new Date(timestamp);
    return Number.isFinite(date.getTime()) ? dateTime.format(date) : missing;
  }

  function captureCard(session) {
    // Older bridge responses included named events instead of sessions.
    if (!('state' in session) && !('observation_count' in session) && isText(session.type)) {
      return { title: session.type, evidence: 'Evento registrado na captura.',
        pending: 'Faltam leituras dos dois combustíveis para comparar a resposta do carro.' };
    }
    const count = session.observation_count;
    const samples = sampleCount(count, 'amostra', 'amostras');
    const start = dateLabel(session.started_at_ms);
    const end = dateLabel(session.ended_at_ms);
    const state = states.get(session.state) || 'Estado desconhecido';
    return {
      title: count > 0 ? 'Esta viagem foi observada' :
        count === 0 ? 'Captura sem amostras' : 'Captura registrada',
      meta: [start, samples, state].join(' · '),
      evidence: count > 0 ? 'A captura guardou ' + samples + '. O total pode incluir dados brutos.' :
        count === 0 ? 'Ainda não há amostras nesta captura.' : 'Total de amostras indisponível.',
      pending: 'O resumo da captura não confirma se há trechos equivalentes em gasolina e GNV.',
      end,
      id: session.id
    };
  }

  function renderEntry(entry) {
    const row = element('li');
    const details = element('details', '', 'entry');
    const summary = element('summary');
    summary.append(element('strong', entry.title));
    if (entry.meta) summary.append(element('span', entry.meta, 'muted'));
    const body = element('div', '', 'entry-body');
    body.append(element('p', entry.evidence), element('p', entry.pending, 'muted'));
    if (entry.end && entry.end !== missing) body.append(element('p', 'Encerrada em ' + entry.end));
    if (Number.isFinite(entry.id) || isText(entry.id)) {
      const advanced = element('details');
      advanced.append(element('summary', 'Detalhes avançados'),
        element('p', 'Identificador da captura · ' + entry.id));
      body.append(advanced);
    }
    details.append(summary, body);
    row.append(details);
    return row;
  }

  function updateStories(data, matched, failed) {
    const source = data.mp48;
    const mp48Seen = (source?.connected !== false && Object.values(mp48Fields).some(
      ([field]) => display(source?.[field]) !== missing)) ||
      history.some(sample => sample?.source?.toLowerCase?.() === 'mp48' && Number.isFinite(sample.rpm));
    const mp48Known = isObject(source) || (isText(data.status?.mp48) && data.status.mp48 !== missing);
    const key = [failed, mp48Seen, mp48Known, matched].join();
    if (key === storyKey) return;
    storyKey = key;
    stories = [];
    if (!failed && !mp48Seen && mp48Known) {
      stories.push({
        title: 'A central do gás ainda não transmitiu',
        evidence: 'O painel está sem leituras da central do gás nesta observação.',
        pending: 'Faltam leituras da central para confirmar o combustível em uso e os tempos de injeção.'
      });
    }
    if (!matched) stories.push({
      title: waitingComparison,
      evidence: failed ? 'O painel está sem acesso às leituras do motor.' :
        'O painel ainda não recebeu trechos comparáveis.',
      pending: comparisonNeeds
    });
    if (screen === 'vault') filterVault(true);
  }

  function filterVault(storiesOnly = false) {
    const words = normalize(byId('search').value.trim()).split(/\s+/);
    const matches = entries => entries.filter(({ title, meta, evidence, pending }) => {
      const searchable = normalize([title, meta, evidence, pending].join(' '));
      return words.every(word => searchable.includes(word));
    });
    const captures = matches(vault);
    const visibleStories = matches(stories);
    if (!storiesOnly) byId('events').replaceChildren(...captures.slice(0, 12).map(renderEntry));
    byId('stories').replaceChildren(...visibleStories.map(renderEntry));
    show('vault-status', vaultError || (!vault.length && !words[0] ?
      'Nenhuma captura salva. As capturas aparecem aqui depois de registradas.' :
      !captures.length && !visibleStories.length ? 'Nenhuma captura ou história encontrada.' :
      sampleCount(captures.length, 'captura', 'capturas')));
  }

  const conceptGraph = {
    summary: 'Siga as ligações para ver como cada leitura ajuda a comparar os combustíveis.',
    nodes: [
      { id: 'petrol', title: 'Gasolina', description: 'Serve de referência para a aceleração em condições semelhantes.' },
      { id: 'mp48', title: sources.mp48, description: 'Informa qual combustível está em uso e os tempos de injeção.' },
      { id: 'obd', title: sources.obd, description: 'Mostra a rotação e as condições do motor durante a aceleração.' },
      { id: 'response', title: 'Resposta da aceleração', description: 'Compara a reação do motor à aceleração com gasolina e GNV.' },
      { id: 'curve', title: 'Curva K', description: 'Ajusta a proporção de combustível. Uma sugestão depende de evidência comparável.' }
    ],
    edges: [
      { from: 'petrol', to: 'response', label: 'Define a referência' },
      { from: 'mp48', to: 'response', label: 'Confirma gasolina ou GNV' },
      { from: 'obd', to: 'response', label: 'Mostra a reação do motor' },
      { from: 'response', to: 'curve', label: 'Ajuda a avaliar um ajuste' }
    ]
  };

  function readGraph() {
    if (typeof window.DynoNative?.getKnowledgeGraph !== 'function') return conceptGraph;
    try {
      const graph = request('getKnowledgeGraph');
      if (Array.isArray(graph?.nodes) && Array.isArray(graph.edges) &&
          graph.nodes.some(node => isText(node?.id) && isText(node.title))) return graph;
    } catch {
      // The conceptual view also works when the optional bridge is unavailable.
    }
    return conceptGraph;
  }

  function renderGraph() {
    const graph = readGraph();
    const nodes = new Map(graph.nodes.filter(node => isText(node?.id) && isText(node.title))
      .map(node => [node.id, node]));
    const controls = [];
    function control(label, detail, className) {
      const button = element('button', label, className);
      button.type = 'button';
      button.setAttribute('aria-pressed', 'false');
      button.setAttribute('aria-controls', 'graph-detail');
      button.addEventListener('click', () => {
        for (const item of controls) item.setAttribute('aria-pressed', String(item === button));
        show('graph-detail', detail);
      });
      controls.push(button);
      return button;
    }
    show('graph-title', graph === conceptGraph ? 'Como analisaremos, não é medição' : 'Como as evidências se relacionam');
    show('graph-summary', isText(graph.summary) ? graph.summary : '');
    show('graph-detail', 'Escolha um assunto ou uma ligação para ver o que ele explica.');
    byId('graph-nodes').replaceChildren(...Array.from(nodes.values(), node => {
      const evidence = sampleCount(node.evidenceCount, 'evidência', 'evidências');
      const status = node.observed === true ? 'Observado' : 'Conceito';
      return control(node.title, [status, node.title, node.observed === true && evidence !== missing ? evidence : '',
        isText(node.description) ? node.description : ''].filter(Boolean).join(' · '));
    }));
    const links = [];
    for (const edge of graph.edges) {
      const from = nodes.get(edge?.from);
      const to = nodes.get(edge?.to);
      if (!from || !to) continue;
      const label = isText(edge.label) ? edge.label : 'Relação de análise';
      const link = control('', 'Conceito · ' + from.title + ' → ' + to.title + ' · ' + label, 'graph-link');
      link.append(element('span', from.title), element('span', '→', 'arrow'), element('span', to.title),
        element('span', label, 'link-label'));
      const row = element('li');
      row.append(link);
      links.push(row);
    }
    byId('graph-edges').replaceChildren(...links);
  }

  function loadVault() {
    vault = [];
    vaultError = '';
    try {
      const sessions = request('getVault');
      if (!Array.isArray(sessions)) throw new Error('Lista de capturas inválida');
      vault = sessions.filter(isObject).map(captureCard);
    } catch {
      vaultError = 'Não foi possível carregar as capturas.';
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
      if (id === 'vault') {
        loadVault();
        renderGraph();
      }
      if (id === 'live') drawChart();
    });
  }
  byId('start').addEventListener('click', () => {
    if (byId('start').disabled) {
      notice('action-status', 'Captura bloqueada: conecte o OBD ou MP48 e aguarde uma leitura real de RPM.');
      return;
    }
    action('startCapture', 'Solicitação de captura enviada');
    refresh();
    if (screen === 'vault') loadVault();
  });
  byId('stop').addEventListener('click', () => {
    action('stopCapture', 'Solicitação de encerramento enviada');
    refresh();
    if (screen === 'vault') loadVault();
  });
  byId('connect-obd').addEventListener('click', () => {
    try {
      const devices = request('listObdDevices');
      if (!Array.isArray(devices) || !devices.length) {
        const obdState = request('getSnapshot')?.status?.obd;
        notice('action-status', obdState === 'aguardando permissão' ?
          'Aguardando autorização Bluetooth. Depois, toque em Conectar OBD novamente.' :
          'Nenhum adaptador OBD pareado disponível. Pareie o adaptador nas configurações Bluetooth e toque novamente.');
        return;
      }
      if (devices.length === 1) {
        connect('connectObd', devices[0].id);
        return;
      }
      const selector = byId('obd-device');
      const placeholder = element('option', 'Escolha o adaptador OBD');
      placeholder.value = '';
      selector.replaceChildren(placeholder, ...devices.map(device => {
        const option = element('option', device.name || device.id);
        option.value = device.id;
        return option;
      }));
      selector.value = '';
      selector.hidden = false;
      notice('action-status', 'Escolha o adaptador OBD na lista.');
    } catch (error) {
      notice('action-status', error.message);
    }
  });
  byId('obd-device').addEventListener('change', () => {
    const id = byId('obd-device').value;
    if (id) connect('connectObd', id);
  });
  byId('battery-authorize').addEventListener('click',
    () => action('requestBatteryOptimization', 'Abrindo autorização de bateria'));
  byId('gemini-configure').addEventListener('click',
    () => action('configureGemini', 'Configuração Gemini solicitada'));
  byId('connect-mp48').addEventListener('click', () =>
    connect('connectMp48'));
  byId('search').addEventListener('input', () => filterVault());
  byId('export').addEventListener('click', () => action('exportVault', 'Solicitação de exportação enviada'));
  window.addEventListener('resize', () => {
    if (screen === 'live') drawChart();
  });

  refresh();
  setInterval(refresh, 500);
})();
