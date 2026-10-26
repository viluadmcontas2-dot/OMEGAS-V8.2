(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  /**
   * Carro: o que o carro está fazendo agora (spec 4.2). SÓ LEITURA: nenhuma gravação, nenhum polling próprio.
   * Usa store.subscribe (quadro rápido) e o gancho 'status' do agendador (1x por segundo, só na rota carro) para as paradas do motor.
   * Valores velhos (> 2,5 s), ausentes ou sem central aparecem como "—", nunca 0 nem o último valor.
   */
  const ROUTE = 'carro';
  // Faixas escritas em palavras (a cor só reforça). Min/max na unidade do medidor.
  const GAUGES = [
    { key: 'rpm', label: 'Giros do motor', unit: 'giros por minuto', min: null, max: null, scale: [0, 7000], text: 'Sem faixa de referência comprovada', low: 'Baixo: o motor pode apagar', high: 'Alto: giros demais' },
    { key: 'map', label: 'Pressão da admissão', unit: 'bar', min: null, max: null, scale: [0, 1.4], text: 'Sem faixa de referência comprovada', low: 'Baixa: confira a leitura', high: 'Alta: acima do normal' },
    { key: 'petrol', label: 'Injeção de gasolina', unit: 'milissegundos', min: null, max: null, scale: [0, 18], text: 'Sem faixa de referência comprovada', low: 'Baixa: injeção quase parada', high: 'Alta: muita gasolina' },
    { key: 'voltage', label: 'Tensão', unit: 'volts', min: null, max: 14.1, scale: [10, 16], text: 'O GNV desliga acima de 14,1 V (escala estimada)', low: 'Baixa: bateria fraca', high: 'Alta: o GNV desliga' },
  ];
  const LEVELS = { reserve: 'Reserva', quarter: '¼', half: '½', three: '¾', full: 'Cheio', unknown: '—' };
  const LEVEL_ORDER = ['reserve', 'quarter', 'half', 'three', 'full'];
  const PT = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 });

  const num = text => {
    const n = parseFloat(String(text).replace(/\./g, '').replace(',', '.'));
    return Number.isFinite(n) ? n : null;
  };
  const fmt1 = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

  /** Zona de um medidor: 'none' (sem leitura), 'ok', 'low', 'high'. */
  function zone(g, value) {
    if (value === null) return 'none';
    // Só a tensão tem limite comprovado (GNV desliga acima de 14,1 V). Os demais medidores mostram o valor ao vivo, sem julgar.
    if (g.min === null && g.max === null) return 'ref';
    if (g.min !== null && value < g.min) return 'low';
    if (g.max !== null && value > g.max) return 'high';
    return 'ok';
  }

  /** Situação do combustível em palavras; só com quadro novo. */
  function situation(state, fresh) {
    if (!fresh) return { text: '—', tone: 'none' };
    const status = state.status || {};
    const t = state.telemetry || {};
    const live = t.live || t.data || {};
    const raw = String(live.fuel || live.state || status.fuelState || '').toUpperCase();
    if (raw.includes('TRANSIC')) return { text: 'Em transição', tone: 'warn' };
    if (raw.includes('PETROL') || raw.includes('GASOLINA')) return { text: 'Em gasolina', tone: 'ok' };
    if (raw.includes('CNG') || raw.includes('GNV') || raw.includes('GAS')) return { text: 'Rodando a gás', tone: 'ok' };
    if (raw.includes('CUTOFF')) return { text: 'Gás cortado', tone: 'warn' };
    return { text: 'Situação ainda desconhecida', tone: 'none' };
  }

  /** Diagnóstico simples: lista de { tone, title, todo }, do mais grave ao menos grave. */
  function diagnose(model) {
    const out = [];
    const { central, view, stallCount, values, zones } = model;
    if (!central.online) {
      out.push({ tone: central.tone, title: central.title, todo: central.next });
      return out;
    }
    if (central.tone !== 'ok') out.push({ tone: central.tone, title: central.title, todo: central.next });
    else if (model.stale) out.push({ tone: 'warn', title: 'Dados atrasados', todo: 'Os medidores ficam em — até chegar uma leitura nova. Ligue a ignição.' });
    if (zones.voltage === 'high') out.push({ tone: 'bad', title: `Tensão em ${view.voltage} V: acima de 14,1 V o GNV desliga`, todo: 'Desligue o som, o ar e os faróis. Se continuar, mande conferir a bateria e o alternador.' });
    if (stallCount > 0) out.push({ tone: 'warn', title: `O motor apagou ${stallCount === 1 ? '1 vez' : stallCount + ' vezes'}`, todo: 'Abra Regular: o app usa isso para sugerir o ajuste.' });
    if (view.levelState === 'reserve') out.push({ tone: 'warn', title: 'Gás na reserva', todo: 'Abasteça logo: com pouco gás o carro volta sozinho para a gasolina.' });
    if (!out.length) out.push({ tone: 'ok', title: values.any ? 'Nada estranho agora' : 'Aguardando leituras', todo: values.any ? 'Pode continuar dirigindo normalmente.' : 'Ligue a ignição e aguarde alguns segundos.' });
    return out;
  }

  function buildModel(state, stallCount) {
    const central = (ns.CentralEstado && ns.CentralEstado.describe(state)) || { online: false, tone: 'bad', title: 'Sem central', next: 'Conecte o cabo USB e ligue a ignição.' };
    const t = state.telemetry || {};
    const age = Number(t.telemetryAgeMs ?? t.ageMs);
    const fresh = central.online && t.valid === true && !(Number.isFinite(age) && age > 2500);
    const view = ns.LiveBarModel.read(fresh ? t : null);
    const values = { rpm: num(view.rpm), map: num(view.map), petrol: num(view.petrol), voltage: num(view.voltage) };
    values.any = Object.values(values).some(v => v !== null);
    const zones = {};
    GAUGES.forEach(g => { zones[g.key] = zone(g, values[g.key]); });
    if (view.voltageState === 'alert') zones.voltage = 'high'; // mesma regra da barra do topo (valor sem arredondar)
    else if (zones.voltage === 'high') zones.voltage = 'ok';
    return { central, view, values, zones, fresh, stale: central.online && !fresh, stallCount, sit: situation(state, fresh) };
  }

  class CarroScreen {
    constructor(app) {
      this.app = app;
      this.host = document.getElementById('carroHost');
      this.stallCount = null;
      this.sig = '';
      this.built = false;
      this.offs = [];
      this.offs.push(app.store.subscribe(state => { if (state.route === ROUTE) this.update(state); }));
      const sch = app.scheduler;
      if (sch && typeof sch.addHook === 'function') {
        this.offs.push(sch.addHook('status', () => this.pollStalls()));
      }
    }
    enter() {
      this.render();
      this.pollStalls();
    }
    /** Paradas do motor: no máximo 1x por ciclo 'status' e só com a aba aberta. */
    pollStalls() {
      if (this.app.store.get().route !== ROUTE) return;
      const api = ns.AutoCalApi;
      if (!api || typeof api.stalls !== 'function') return;
      const res = api.stalls();
      const count = res && res.ok !== false && res.stalls && Number.isFinite(Number(res.stalls.count)) ? Number(res.stalls.count) : null;
      if (count !== this.stallCount) { this.stallCount = count; this.update(this.app.store.get()); }
    }
    render() {
      if (!this.host) return;
      const gauges = GAUGES.map(g => `
        <section class="carro-gauge" data-g="${g.key}" data-zone="none" aria-label="${g.label}">
          <h3 class="carro-g-label">${g.label}</h3>
          <div class="carro-g-value"><b data-v>—</b><span class="carro-g-unit">${g.unit}</span></div>
          <div class="carro-g-bar" aria-hidden="true"><i class="carro-g-good"></i><i class="carro-g-mark"></i></div>
          <div class="carro-g-state" data-s>Sem leitura</div>
          <div class="carro-g-range">${g.text}</div>
        </section>`).join('');
      this.host.innerHTML = `
        <div class="carro-top nx-card">
          <div class="carro-sit" data-tone="none"><i class="nx-dot"></i><b data-k="sit">—</b></div>
          <div class="carro-meta">
            <div class="carro-level" data-state="unknown" role="img" aria-label="Nível do gás"><span class="carro-lvl-k">Nível do gás</span><span class="carro-pips"><i></i><i></i><i></i><i></i><i></i></span><b data-k="level">—</b></div>
            <div class="carro-src"><span class="carro-lvl-k">Dados de</span><b data-k="source">Sem central</b></div>
          </div>
        </div>
        <div class="carro-gauges">${gauges}</div>
        <div class="carro-diag nx-card"><h3 class="nx-eyebrow">O que está estranho e o que fazer</h3><div class="carro-diag-list" data-k="diag"></div></div>`;
      this.built = true;
      this.sig = '';
      this.update(this.app.store.get());
    }
    update(state) {
      if (!this.host || !this.built) return;
      const m = buildModel(state, this.stallCount);
      const sig = [m.central.title, m.view.rpm, m.view.map, m.view.petrol, m.view.voltage, m.view.levelState, m.view.sourceText, m.sit.text, m.stallCount, m.stale].join('|');
      if (sig === this.sig) return;
      this.sig = sig;
      const q = sel => this.host.querySelector(sel);
      const setText = (node, text) => { if (node && node.textContent !== text) node.textContent = text; };
      const setData = (node, key, v) => { if (node && node.dataset[key] !== v) node.dataset[key] = v; };

      const sitNode = q('.carro-sit');
      let sitText = m.sit.text; let sitTone = m.sit.tone;
      if (!m.central.online) { sitText = m.central.title; sitTone = m.central.tone; }
      else if (m.stale) { sitText = m.central.tone === 'ok' ? 'Dados atrasados' : m.central.title; sitTone = 'warn'; }
      if (m.stallCount > 0 && m.fresh) sitText += ` · Motor apagou ${m.stallCount === 1 ? '1 vez' : m.stallCount + ' vezes'}`;
      setData(sitNode, 'tone', sitTone);
      setText(q('[data-k="sit"]'), sitText);

      setData(q('.carro-level'), 'state', m.view.levelState);
      const idx = LEVEL_ORDER.indexOf(m.view.levelState);
      this.host.querySelectorAll('.carro-pips i').forEach((pip, i) => setData(pip, 'on', idx >= 0 && i <= idx ? '1' : '0'));
      setText(q('[data-k="level"]'), LEVELS[m.view.levelState] || '—');
      setText(q('[data-k="source"]'), m.central.online && !m.stale ? 'Central do carro' : (m.central.online ? 'Central (atrasada)' : '—'));

      GAUGES.forEach(g => {
        const node = q(`[data-g="${g.key}"]`);
        const z = m.zones[g.key];
        setData(node, 'zone', z);
        setText(node.querySelector('[data-v]'), m.view[g.key]);
        setText(node.querySelector('[data-s]'), z === 'none' ? 'Sem leitura' : z === 'ref' ? 'Ao vivo' : z === 'ok' ? 'Normal' : `Atenção · ${z === 'low' ? g.low : g.high}`);
        const span = g.scale[1] - g.scale[0];
        const pct = x => Math.max(0, Math.min(100, ((x - g.scale[0]) / span) * 100));
        const good = node.querySelector('.carro-g-good');
        good.hidden = g.min === null;
        if (g.min !== null) { good.style.left = pct(g.min) + '%'; good.style.width = (pct(g.max) - pct(g.min)) + '%'; }
        const mark = node.querySelector('.carro-g-mark');
        const v = m.values[g.key];
        mark.hidden = v === null;
        if (v !== null) mark.style.left = pct(v) + '%';
      });

      const items = diagnose(m).slice(0, 3);
      const list = q('[data-k="diag"]');
      list.replaceChildren(...items.map(it => {
        const el = document.createElement('div');
        el.className = 'carro-d-item'; el.dataset.tone = it.tone;
        const dot = document.createElement('i'); dot.className = 'nx-dot';
        const body = document.createElement('div');
        const b = document.createElement('b'); b.textContent = it.title;
        const p = document.createElement('p'); p.textContent = it.todo;
        body.append(b, p); el.append(dot, body);
        return el;
      }));
    }
  }
  ns.CarroScreen = CarroScreen;
  ns.CarroModel = { buildModel, diagnose, zone, GAUGES };
  function boot() {
    const app = root.OmegasApp;
    if (!app) return;
    app.carro = new ns.CarroScreen(app);
    if (app.store.get().route === "carro") app.carro.enter();
  }
  if (root.OmegasApp) boot(); else root.addEventListener("omegas-app-ready", boot, { once: true });
})(typeof window !== "undefined" ? window : globalThis);
