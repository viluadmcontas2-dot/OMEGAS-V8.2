(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  /**
   * Regular (início): a jornada em 4 passos e UMA próxima ação (spec 4.1).
   * Só LÊ: store (telemetria e estado da central), um gancho 'status' (1 s, só na rota regular) para a análise do AutoCal
   * (equivalence() uma vez por gancho) e Car Info. Nunca grava na central e nunca chama writeMap/writeCurve/prepare/execute:
   * o botão principal só navega ou abre a prévia do Refino; a gravação continua sendo o toque do dono no cartão de revisão
   * (leitura de volta e Desfazer incluídos).
   * Desconhecido = "—", nunca 0.
   */
  const DASH = '—';
  const STEP_NAMES = ['Preparar', 'Coletar', 'Ajustar', 'Conferir'];
  const STEP_WHY = [
    'Ligue o carro e conecte o cabo. O app confere a central, a bateria, o gás e a água.',
    'Dirija normalmente. O app aprende como o seu carro responde no gás e na gasolina.',
    'Quando o app achar ajustes, você revisa e confirma. Nada muda antes do seu toque.',
    'Depois de aplicar, o app compara o antes e o depois e diz se melhorou.',
  ];
  const ZONE_NAMES = ['Leve', 'Média', 'Forte', 'Máxima'];
  const LEVEL_WORDS = { full: 'Cheio', three: '¾', half: '½', quarter: '¼', reserve: 'Reserva', unknown: DASH };
  const ONE = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  const INT = new Intl.NumberFormat('pt-BR');

  const finite = value => (value === null || value === undefined || value === '' || !Number.isFinite(Number(value)) ? null : Number(value));
  const esc = value => String(value ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const times = n => (n === 1 ? '1 vez' : `${INT.format(n)} vezes`);
  const points = n => (n === 1 ? '1 ponto' : `${INT.format(n)} pontos`);

  /** "Onde o carro sofre": faixas com paradas (peso 2) e engasgos (peso 1), da maior para a menor. */
  function stallRows(stalls) {
    if (!stalls || typeof stalls !== 'object') return { known: false, rows: [], dead: null, near: null };
    const dead = finite(stalls.count);
    const near = finite(stalls.nearCount);
    const bands = new Map();
    const add = (list, weight, kind) => (Array.isArray(list) ? list : []).forEach(r => {
      const from = finite(r?.fromMs);
      const count = finite(r?.count);
      if (from === null || !count) return;
      const key = from.toFixed(2);
      const cur = bands.get(key) || { from, to: finite(r.toMs) ?? from + 0.5, weight: 0, dead: 0, near: 0, rpmLow: Infinity, rpmHigh: -Infinity };
      cur.weight += weight * count;
      cur[kind] += count;
      const lo = finite(r.rpmLow) ?? finite(r.rpmBefore);
      const hi = finite(r.rpmHigh) ?? finite(r.rpmBefore);
      if (lo !== null) cur.rpmLow = Math.min(cur.rpmLow, lo);
      if (hi !== null) cur.rpmHigh = Math.max(cur.rpmHigh, hi);
      bands.set(key, cur);
    });
    add(stalls.regions, 2, 'dead');
    add(stalls.nearRegions, 1, 'near');
    const rows = [...bands.values()].sort((a, b) => b.weight - a.weight).map(b => {
      const hasRpm = Number.isFinite(b.rpmLow) && Number.isFinite(b.rpmHigh);
      const round = v => INT.format(Math.round(v / 50) * 50);
      const where = hasRpm
        ? (Math.round(b.rpmLow / 50) === Math.round(b.rpmHigh / 50) ? `Perto de ${round(b.rpmLow)} giros` : `Entre ${round(b.rpmLow)} e ${round(b.rpmHigh)} giros`)
        : 'Ao soltar e voltar ao acelerador';
      const what = [b.dead ? `Parou ${times(b.dead)}` : '', b.near ? `Engasgou ${times(b.near)}` : ''].filter(Boolean).join(' · ');
      return { where, what, impact: b.weight >= 4 ? 'Alto' : b.weight >= 2 ? 'Médio' : 'Baixo', weight: b.weight };
    });
    return { known: dead !== null || near !== null || rows.length > 0, rows, dead, near };
  }

  /** Coleta do GNV: cada zona é pronta / coletando / falta / desconhecida (do AutoCal; sem leitura = desconhecida). */
  function coverage(snapshot) {
    const O = ns.AutoCalOriginal;
    const hasSnapshot = !!snapshot && Array.isArray(snapshot.fields) && snapshot.fields.length > 0;
    const rowFor = (fuel, label) => ({
      fuel, label,
      zones: hasSnapshot && O?.zoneStates ? O.zoneStates(snapshot, fuel).map(z => z.state) : [DASH, DASH, DASH, DASH].map(() => 'unknown'),
    });
    return [rowFor('gas', 'GNV'), rowFor('petrol', 'Gasolina')];
  }
  function coverageFraction(rows) {
    const gas = rows.find(r => r.fuel === 'gas');
    if (!gas || gas.zones.every(z => z === 'unknown')) return null;
    const score = { done: 1, collecting: 0.5, missing: 0, unknown: 0 };
    return gas.zones.reduce((sum, z) => sum + score[z], 0) / 4;
  }

  /** Antes e depois em palavras (o Kotlin já deu o veredito de cada ponto). */
  function checkSummary(result) {
    const list = Array.isArray(result?.points) ? result.points : [];
    if (!result || !list.length) return { known: false, improved: 0, worse: 0, measuring: 0, verdict: '' };
    const count = name => list.filter(p => p && p.outcome === name).length;
    const improved = count('MELHOROU');
    const worse = count('PIOROU');
    const measuring = count('MEDINDO');
    const verdict = worse > 0 ? 'Piorou' : measuring > 0 && improved === 0 ? 'Medindo' : improved > 0 ? 'Melhorou' : 'Medindo';
    return { known: true, improved, worse, measuring, verdict };
  }

  /**
   * Modelo puro (testável sem tela). Entrada:
   *  central  = CentralEstado.describe(state)
   *  live     = LiveBarModel.read(telemetry)
   *  coolant  = texto da água ('—' = não medido)
   *  eq       = AutoCalApi.equivalence() (ou null), snapshot = projection().snapshot (ou null)
   */
  function build({ central, live, coolant, eq, snapshot }) {
    const online = central.online === true && central.tone === 'ok';
    const pilot = eq && typeof eq === 'object' && eq.autopilot && typeof eq.autopilot === 'object' ? eq.autopilot : null;
    const phase = pilot && pilot.phase ? String(pilot.phase) : '';
    const voltageAlert = live.voltageState === 'alert';
    const prepared = online && !voltageAlert;
    const cov = coverage(snapshot);
    const frac = coverageFraction(cov);
    const stalls = stallRows(eq?.stalls);
    const check = checkSummary(eq?.refinement?.result);
    const proposalCount = finite(pilot?.proposal?.count);
    const action = pilot?.action && typeof pilot.action === 'object' ? pilot.action : null;
    const reviewReady = !!action && action.kind === 'review' && action.enabled !== false;

    let current;
    if (!prepared) current = 0;
    else if (phase === 'PROPOSTA_PRONTA' || phase === 'RESTAURAR_TRECHO') current = 2;
    else if (phase === 'VERIFICANDO') current = 3;
    else if (phase === 'ESTAVEL') current = 4;
    else current = 1;

    let progress = null;
    if (central.online) {
      if (!prepared) progress = 5;
      else if (current === 1) progress = 10 + Math.round(50 * (frac === null ? 0.1 : frac));
      else progress = { 2: 70, 3: 85, 4: 100 }[current];
    }

    // Manchete (UMA frase) e dica
    let headline; let next;
    if (!central.online || central.tone !== 'ok') { headline = central.title; next = central.next; }
    else if (voltageAlert) { headline = 'A tensão da bateria está alta.'; next = 'Acima de 14,1 V o GNV desliga. Confira o carro antes de seguir.'; }
    else if (current === 1) {
      headline = 'Dirija normalmente: o app está aprendendo o seu carro.';
      const gas = cov[0].zones;
      next = frac === null ? 'Varie: parado, cidade e estrada.' : `Já coletei ${gas.filter(z => z === 'done').length} de 4 faixas de pressão no gás.`;
    } else if (phase === 'RESTAURAR_TRECHO') { headline = 'Um ajuste piorou o carro. Dá para voltar atrás.'; next = 'Toque em Revisar: nada muda antes da sua confirmação.'; }
    else if (current === 2) { headline = proposalCount !== null && proposalCount > 0 ? `Achei ${points(proposalCount)} para o carro andar melhor.` : 'Achei ajustes para o carro andar melhor.'; next = 'Toque em Revisar: nada muda antes da sua confirmação.'; }
    else if (current === 3) { headline = 'Medindo se o ajuste melhorou o carro.'; next = 'Continue dirigindo normalmente.'; }
    else { headline = 'Regulagem concluída: o carro está estável.'; next = 'Se o carro engasgar de novo, volte aqui.'; }

    // Botão principal: só navega ou abre a prévia do Refino (nunca grava).
    let primary;
    if (!central.online || central.tone !== 'ok') primary = { kind: 'go', route: 'carro', label: 'Conferir a conexão' };
    else if (voltageAlert) primary = { kind: 'go', route: 'carro', label: 'Ver o carro' };
    else if (current === 1) primary = { kind: 'go', route: 'autocal', label: 'Acompanhar a coleta' };
    else if (current === 2) primary = reviewReady ? { kind: 'review', route: 'refino', label: 'Revisar a sugestão' } : { kind: 'go', route: 'refino', label: 'Abrir o Refino' };
    else if (current === 3) primary = { kind: 'go', route: 'refino', label: 'Ver a medição' };
    else primary = { kind: 'go', route: 'carro', label: 'Ver o carro' };

    // Resumos de uma linha por passo
    const gasZones = cov[0].zones;
    const gasDone = gasZones.filter(z => z === 'done').length;
    const summaries = [
      prepared ? `${central.title}${live.voltage !== DASH ? ` · ${live.voltage} V` : ''}` : (online ? `Tensão alta: ${live.voltage} V` : central.title),
      frac === null ? 'Dirija normalmente' : `GNV: ${gasDone} de 4 faixas`,
      proposalCount !== null && proposalCount > 0 && current >= 2 ? `${points(proposalCount)} para ajustar` : (stalls.rows.length ? `${stalls.rows.length} ${stalls.rows.length === 1 ? 'ponto' : 'pontos'} de sofrimento` : 'Ainda sem sugestão'),
      check.known ? check.verdict : 'Depois de aplicar',
    ];
    const steps = STEP_NAMES.map((name, i) => ({
      name, summary: summaries[i], why: STEP_WHY[i],
      state: i < current ? 'done' : i === current ? 'now' : 'later',
    }));

    const checks = [
      { label: 'Central', value: central.title, word: central.tone === 'ok' ? 'Pronta' : 'Atenção', tone: central.tone },
      { label: 'Tensão', value: live.voltage === DASH ? DASH : `${live.voltage} V`, word: live.voltageState === 'alert' ? 'Alta' : live.voltageState === 'measured' ? 'Normal' : 'Sem leitura', tone: live.voltageState === 'alert' ? 'warn' : live.voltageState === 'measured' ? 'ok' : 'unknown' },
      { label: 'Nível do gás', value: LEVEL_WORDS[live.levelState] || DASH, word: live.levelState === 'unknown' ? 'Sem leitura' : live.levelState === 'reserve' ? 'Baixo' : 'Medido', tone: live.levelState === 'reserve' ? 'warn' : live.levelState === 'unknown' ? 'unknown' : 'ok' },
      { label: 'Água quente', value: coolant && coolant !== DASH ? coolant : DASH, word: coolant && coolant !== DASH ? 'Medida' : 'Não medida', tone: coolant && coolant !== DASH ? 'ok' : 'unknown' },
    ];

    return { online, prepared, current, progress, headline, next, primary, steps, checks, coverage: cov, stalls, check, proposalCount, phase };
  }

  // ---------- Desenho ----------
  const RING_R = 52;
  const RING_LEN = 2 * Math.PI * RING_R;
  const STATE_WORD = { done: 'Feito', now: 'Agora', later: 'Depois' };
  const ZONE_WORD = { done: 'Pronta', collecting: 'Coletando', missing: 'Falta', unknown: DASH };

  function checksHtml(model) {
    return `<div class="rg-checks">${model.checks.map(c => `<div class="rg-check" data-tone="${c.tone}"><small>${esc(c.label)}</small><b>${esc(c.value)}</b><span><i class="nx-dot"></i>${esc(c.word)}</span></div>`).join('')}</div>`;
  }
  function coverageHtml(model) {
    const head = ZONE_NAMES.map(n => `<span class="rg-zone-head">${n}</span>`).join('');
    const rows = model.coverage.map(r => `<span class="rg-fuel">${esc(r.label)}</span>${r.zones.map(z => `<span class="rg-zone" data-state="${z}">${ZONE_WORD[z]}</span>`).join('')}`).join('');
    return `<div class="rg-cover" role="table" aria-label="Cobertura da coleta"><span class="rg-corner">Pressão no motor</span>${head}${rows}</div><p class="rg-note rg-note-cover">Cada faixa fica pronta depois de rodar nela por um tempo.</p>`;
  }
  function stallsHtml(model) {
    const s = model.stalls;
    if (!s.known) return '<p class="rg-empty">Ainda sem leitura de engasgos. Dirija e o app registra onde o carro sofre.</p>';
    if (!s.rows.length) {
      const total = (s.dead || 0) + (s.near || 0);
      if (total > 0) return `<p class="rg-empty">O carro engasgou ${times(total)} no GNV, sem uma faixa definida ainda.</p>`;
      return '<p class="rg-empty">Nenhum engasgo registrado. Se o carro apagar ou falhar, aparece aqui.</p>';
    }
    return `<p class="rg-list-title">Onde o carro sofre</p><ol class="rg-stalls">${s.rows.slice(0, 3).map(r => `<li data-impact="${r.impact}"><div><b>${esc(r.where)}</b><span>${esc(r.what)}</span></div><em>${r.impact}</em></li>`).join('')}</ol>`;
  }
  function ajustarHtml(model) {
    const how = model.current === 2
      ? '<ol class="rg-how"><li><b>Revisar</b>Veja o que o app quer mudar. Nada é enviado ainda.</li><li><b>Confirmar</b>Só com o seu toque o app grava na central e lê de volta para conferir.</li><li><b>Desfazer</b>Se o carro piorar, um toque volta ao que era antes.</li></ol>' : '';
    return how + stallsHtml(model);
  }
  function conferirHtml(model) {
    const c = model.check;
    if (!c.known) return '<p class="rg-empty">Ainda não há uma gravação para comparar. Depois de aplicar uma sugestão, aqui aparece se o carro melhorou ou piorou.</p>';
    return `<div class="rg-verdict" data-verdict="${c.verdict}"><b>${c.verdict}</b><span>Antes e depois da última gravação</span></div>
      <div class="rg-counts"><span data-k="ok"><b>${INT.format(c.improved)}</b> melhoraram</span><span data-k="bad"><b>${INT.format(c.worse)}</b> pioraram</span><span data-k="mid"><b>${INT.format(c.measuring)}</b> medindo</span></div>`;
  }
  function bodyHtml(model, i) {
    const step = model.steps[i];
    const body = [checksHtml, coverageHtml, ajustarHtml, conferirHtml][i](model);
    return step.state === 'later' ? `<p class="rg-why">${esc(step.why)}</p>${body}` : body;
  }

  class RegularScreen {
    constructor(app) {
      this.app = app;
      this.store = app.store;
      this.host = document.getElementById('regularHost');
      this.eq = null;
      this.snapshot = null;
      this.coolant = DASH;
      this.open = null; // passo aberto por toque (null = o passo "agora")
      this.shown = {};
      this.model = null;
      this.built = false;
      this.build();
      // Dados pesados: só no gancho de status (1 s), só na rota regular, uma análise por gancho.
      this.offStatus = app.scheduler.addHook('status', () => { if (this.store.get().route === 'regular') this.refreshData(); });
      this.offStore = this.store.subscribe(state => { if (state.route === 'regular') this.update(); });
    }

    enter() { this.refreshData(); }

    build() {
      if (!this.host || this.built) return;
      this.built = true;
      const R = RING_R;
      this.host.innerHTML = `
        <div class="rg">
          <section class="rg-hero nx-card" aria-label="Situação da regulagem">
            <div class="rg-ring" role="img" aria-label="Regulagem">
              <svg viewBox="0 0 120 120" aria-hidden="true"><circle class="rg-ring-bg" cx="60" cy="60" r="${R}"/><circle class="rg-ring-fg" cx="60" cy="60" r="${R}" stroke-dasharray="${RING_LEN.toFixed(1)}" stroke-dashoffset="${RING_LEN.toFixed(1)}" transform="rotate(-90 60 60)"/></svg>
              <div class="rg-ring-text"><small>Regulagem</small><b data-rg="pct">${DASH}</b></div>
            </div>
            <h2 class="rg-headline" data-rg="headline"></h2>
            <p class="rg-next" data-rg="next"></p>
            <button type="button" class="nx-btn primary rg-primary" data-rg-primary></button>
          </section>
          <ol class="rg-steps" aria-label="Os 4 passos da regulagem">
            ${STEP_NAMES.map((name, i) => `<li class="rg-step" data-step="${i}">
              <button type="button" class="rg-step-head" data-rg-step="${i}" aria-expanded="false">
                <span class="rg-badge">${i + 1}</span>
                <span class="rg-step-title"><b>${name}</b><span data-rg="sum${i}"></span></span>
                <span class="rg-state" data-rg="state${i}"></span>
              </button>
              <div class="rg-body" data-rg="body${i}"></div>
            </li>`).join('')}
          </ol>
        </div>`;
      this.host.addEventListener('click', event => this.onClick(event));
    }

    /** Lê o que é caro: uma análise do AutoCal e o Car Info (1 s, só na rota regular). */
    refreshData() {
      const api = ns.AutoCalApi;
      if (api?.available?.()) {
        this.eq = api.equivalence?.() || null;
        const projection = api.projection?.() || {};
        this.snapshot = projection.ok === true ? (projection.snapshot || null) : null;
      } else { this.eq = null; this.snapshot = null; }
      this.coolant = DASH;
      try {
        const view = ns.CarInfoView?.view?.(this.app.api?.carInfo?.(), Date.now());
        const item = view?.values?.find(v => v.key === 'coolantTemp');
        if (item && item.text !== DASH) this.coolant = `${item.text} °C`;
      } catch (_) { this.coolant = DASH; }
      this.update();
    }

    render() { this.update(); }

    set(name, text) {
      if (this.shown[name] === text) return;
      this.shown[name] = text;
      const node = this.host?.querySelector(`[data-rg="${name}"]`);
      if (node) node.textContent = text;
    }
    setHtml(name, html) {
      if (this.shown[name] === html) return;
      this.shown[name] = html;
      const node = this.host?.querySelector(`[data-rg="${name}"]`);
      if (node) node.innerHTML = html;
    }

    update() {
      if (!this.host || !this.built) return;
      const state = this.store.get();
      const central = ns.CentralEstado?.describe ? ns.CentralEstado.describe(state) : { online: false, tone: 'bad', title: 'Sem central', next: 'Conecte o cabo USB e ligue a ignição.' };
      const live = ns.LiveBarModel?.read ? ns.LiveBarModel.read(state.telemetry) : { voltage: DASH, voltageState: 'unknown', levelState: 'unknown' };
      const model = this.model = build({ central, live, coolant: this.coolant, eq: this.eq, snapshot: this.snapshot });
      const ring = this.host.querySelector('.rg-ring-fg');
      const pct = model.progress;
      this.set('pct', pct === null ? DASH : `${pct}%`);
      const offset = (RING_LEN * (1 - (pct === null ? 0 : pct / 100))).toFixed(1);
      if (ring && this.shown.offset !== offset) { this.shown.offset = offset; ring.setAttribute('stroke-dashoffset', offset); }
      const ringBox = this.host.querySelector('.rg-ring');
      if (ringBox) {
        const tone = pct === null ? 'unknown' : pct >= 100 ? 'ok' : model.prepared ? 'go' : 'warn';
        if (ringBox.dataset.tone !== tone) ringBox.dataset.tone = tone;
        const label = pct === null ? 'Regulagem: sem leitura' : `Regulagem ${pct}%`;
        if (ringBox.getAttribute('aria-label') !== label) ringBox.setAttribute('aria-label', label);
      }
      this.set('headline', model.headline);
      this.set('next', model.next);
      const button = this.host.querySelector('[data-rg-primary]');
      if (button) {
        if (button.textContent !== model.primary.label) button.textContent = model.primary.label;
        button.dataset.kind = model.primary.kind;
      }
      const expanded = this.open === null ? model.current : this.open;
      model.steps.forEach((step, i) => {
        this.set(`sum${i}`, step.summary);
        this.set(`state${i}`, STATE_WORD[step.state]);
        const li = this.host.querySelector(`.rg-step[data-step="${i}"]`);
        if (!li) return;
        if (li.dataset.state !== step.state) li.dataset.state = step.state;
        const isOpen = i === expanded;
        const flag = isOpen ? 'open' : 'closed';
        if (li.dataset.open !== flag) {
          li.dataset.open = flag;
          li.querySelector('.rg-step-head')?.setAttribute('aria-expanded', String(isOpen));
        }
        this.setHtml(`body${i}`, isOpen ? bodyHtml(model, i) : '');
      });
    }

    onClick(event) {
      const stepButton = event.target.closest('[data-rg-step]');
      if (stepButton) {
        const i = Number(stepButton.dataset.rgStep);
        const current = this.model ? this.model.current : 0;
        const shownNow = this.open === null ? current : this.open;
        // Tocar no passo aberto volta ao passo "agora"; senão abre o tocado.
        this.open = i === shownNow ? null : i;
        this.update();
        return;
      }
      if (event.target.closest('[data-rg-primary]')) this.runPrimary();
    }

    /** O botão principal só navega ou abre a prévia do Refino; a gravação é o toque do dono dentro do cartão de revisão. */
    runPrimary() {
      const primary = this.model?.primary;
      if (!primary) return;
      this.app.router.navigate(primary.route);
      if (primary.kind !== 'review') return;
      const refino = this.app.refino;
      if (refino && typeof refino.refresh === 'function' && typeof refino.openPreview === 'function') {
        refino.refresh(true);
        refino.openPreview();
      }
    }
  }

  ns.RegularScreen = RegularScreen;
  ns.RegularModel = { build, stallRows, coverage, checkSummary };
  function boot() {
    const app = root.OmegasApp;
    if (!app) return;
    app.regular = new ns.RegularScreen(app);
    if (app.store.get().route === "regular") app.regular.enter();
  }
  if (root.OmegasApp) boot(); else root.addEventListener("omegas-app-ready", boot, { once: true });
})(typeof window !== 'undefined' ? window : globalThis);
