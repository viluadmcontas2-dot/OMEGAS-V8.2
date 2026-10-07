(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  const rules = ns.DisplayRules;
  const finite = rules.finite;
  const DASH = '—';

  function text(id, value) {
    const node = document.getElementById(id);
    if (!node) return;
    const next = value == null ? DASH : String(value);
    if (node.textContent !== next) node.textContent = next;
    const empty = next === DASH ? 'true' : 'false';
    if (node.dataset.empty !== empty) node.dataset.empty = empty;
  }

    // O serviço calcula em segundo plano; aqui só apresentamos o último resultado pronto.
    function summary(result) {
      const eq = result || {};
      const available = eq.ok === true && eq.available !== false;
      const index = available ? finite(eq.index) : null;
      const coverage = available ? finite(eq.coverage) : null;
      const fraction = value => value !== null && value >= 0 && value <= 1 ? Math.round(value * 100) : null;
      const action = available && eq.nextAction || {};
      const routes = ['dashboard', 'map', 'curve', 'autocal', 'refino', 'sessions', 'tools', 'diagnostico'];
      return {
        percent: fraction(index), coverage: fraction(coverage), provisional: eq.provisional === true,
        next: typeof action.text === 'string' && action.text.trim() ? action.text.trim() : 'Aguardando medição da ECU.',
        route: routes.includes(action.route) ? action.route : 'refino',
        points: available && Array.isArray(eq.points) ? eq.points.filter(p => p && Number.isInteger(p.index) && p.index >= 0 && p.index < 30) : [],
      };
    }
    ns.DashboardModel = { summary };
  function ensureStyles() {
    if (document.querySelector('link[data-dashboard-now]')) return;
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = 'styles-dashboard-now.css';
    link.dataset.dashboardNow = 'true';
    document.head.appendChild(link);
  }

  /** Agora apresenta a intenção do cérebro; os dados vivos ficam no cabeçalho global. */
  class DashboardScreen {
    constructor() {
      ensureStyles();
      this.root = document.querySelector('[data-screen="dashboard"]');
      this.lastHealthSignature = '';
      this.installLayout();
    }

    installLayout() {
      if (!this.root) return;
      this.root.classList.add('multimedia-now-screen');
      if (!this.refinoClickBound) {
        this.refinoClickBound = true;
        this.root.addEventListener('click', (event) => {
          if (event.target.closest && event.target.closest('[data-dash-refino]')) {
            const app = root.OmegasApp;
            if (app && app.router) app.router.navigate(this.nextRoute || 'refino');
          }
        });
      }
        this.root.innerHTML = `
          <div class="now-dashboard-shell">
            <section class="now-drive" aria-label="Direção agora">
              <div class="now-tile" data-tile="fuel"><small>Combustível</small><b id="dashDriveFuel" data-empty="true">—</b><span id="dashDriveFuelNote">sem leitura</span></div>
              <div class="now-tile" data-tile="ms"><small>Injeção</small><b id="dashDriveMs" data-empty="true">—</b><span id="dashDriveMsNote">ms</span></div>
              <div class="now-tile" data-tile="rpm"><small>RPM</small><b id="dashDriveRpm" data-empty="true">—</b><span id="dashDriveRpmNote">agora</span></div>
              <div class="now-tile" data-tile="map"><small>MAP</small><b id="dashDriveMap" data-empty="true">—</b><span id="dashDriveMapNote">bar</span></div>
            </section>
            <section class="now-intention" aria-label="Estado e próximo passo">
              <div><h3 id="dashState">Aguardando dados da ECU</h3><p id="dashNext">Aguardando medição da ECU.</p></div>
              <button type="button" class="primary" data-dash-refino>Abrir Refino</button>
            </section>
            <section class="now-coverage" aria-label="Equivalência com a gasolina">
              <div class="now-equivalence"><b id="dashEquivalence">—</b><p id="dashEquivalenceNote">Aguardando medição</p>
                <progress id="dashEquivalenceProgress" max="100" value="0" hidden aria-label="Condução equivalente à gasolina"></progress></div>
              <div class="now-coverage-bands"><header><h3>Faixas da sua condução</h3><span id="dashCoverage">—</span></header>
                <div id="dashBands" class="now-bands" aria-label="Estado das 30 faixas"></div>
                <p>● Igual à gasolina <em>● Fora</em> <span>● Medindo</span> <i>● Sem dado</i></p></div>
            </section>
            <footer class="now-quiet-row">
              <div id="dashHealth" class="now-session-card" data-level="offline"><span class="state-indicator"></span>
                <div class="now-session-copy"><b>Sem cabo</b><p data-health-detail>Conecte o cabo USB na ECU</p></div><button type="button" class="primary" data-usb-allow hidden>Permitir USB</button>
              </div>
              <details class="now-reading-details"><summary>Detalhes da leitura</summary><div><p>Nível da ECU <b id="dashLevelsRaw">—</b></p><p>Refino <b id="dashRefino">—</b></p></div></details>
            </footer>
          </div>`;
    }
      /** Uma visão pronta, no máximo a cada 3 s; inclui o ajuste local de engasgos do Refino. */
      renderRefino() {
        const now = Date.now();
        if (this.refinoAt && now - this.refinoAt < 3e3) return;
        // Sem cabo o Kotlin já diz "Sem ECU" / "Aguardar a ECU" (refinoState): a conexão em si fica no cartão de saúde.
        const api = root.OmegasUi && root.OmegasUi.AutoCalApi;
        // equivalence() é pesado (milhares de pontos serializados): o Agora reaproveita o resultado que o Refino/AutoCal
        // acabaram de ler e, fora isso, só relê quando a revisão da evidência andou (ou o vigia de 15 s vence).
        const revisions = ns.Revisions;
        if (!this.refinoGate && revisions) this.refinoGate = revisions.gate(['evidence'], 15000);
        const shared = ns.CurveChart && ns.CurveChart.evidence;
        let result = null;
        if (shared && shared.eq && now - (shared.fetchedAt || 0) < 5000) result = shared.eq;
        else if (!this.refinoGate || this.refinoGate.due(false, now)) {
          result = api && typeof api.equivalence === "function" ? api.equivalence() : null;
          if (this.refinoGate) this.refinoGate.mark(now);
        } else return;
        this.refinoAt = now;
        const pilot = result && result.autopilot || {};
        const rs = result && result.refinoState && typeof result.refinoState === "object" ? result.refinoState : {};
        // O rótulo curto do Kotlin sabe o combustível de agora ("Medindo a gasolina" × "Medindo o GNV"); a fase do piloto é o reserva.
        const label = typeof rs.label === "string" ? rs.label.trim() : "";
        text("dashRefino", label || rules.phaseLabel(pilot.phase, pilot.expiredFrom));
        const model = summary(result);
        text("dashEquivalence", model.percent === null ? "—" : model.percent + "%");
        text("dashEquivalenceNote", model.percent === null ? "Ainda sem base para comparar" : "da condução já equivale à gasolina" + (model.provisional ? " · provisório" : ""));
        text("dashCoverage", model.coverage === null ? "—" : model.coverage + "% de cobertura medida");
        // Próximo passo em palavras de dono: a frase do Kotlin (sabe o combustível, sem ms/bar/%); o texto do motor é o reserva.
        const said = typeof rs.nextAction === "string" && rs.nextAction.trim() ? rs.nextAction.trim() : "";
        text("dashNext", said || model.next);
        const what = typeof rs.phase === "string" && rs.phase.trim() ? rs.phase.trim() : "";
        text("dashState", what || "Aguardando dados da ECU");
        const progress = document.getElementById("dashEquivalenceProgress");
        if (progress) {
          progress.value = model.percent === null ? 0 : model.percent;
          progress.hidden = model.percent === null;
        }
        const button = this.root.querySelector("[data-dash-refino]");
        const names = { dashboard: 'Agora', map: 'Mapa K', curve: 'Curva K', autocal: 'AutoCal', refino: 'Refino', sessions: 'Sessões', tools: 'Ferramentas', diagnostico: 'Diagnóstico' };
        this.nextRoute = model.route === 'dashboard' ? 'refino' : model.route;
        if (button) button.textContent = 'Abrir ' + names[this.nextRoute];
        const bands = document.getElementById('dashBands');
        const key = model.points.map(p => p.index + ':' + p.state).join('|');
        if (bands && key !== this.bandsKey) {
          this.bandsKey = key;
          bands.innerHTML = Array.from({ length: 30 }, (_, index) => {
            const point = model.points.find(p => p.index === index) || {};
            const state = String(point.state || 'SEM_DADOS');
            const tone = ['EQUIVALENTE', 'CONFIRMADO'].includes(state) ? 'ok' : ['POBRE', 'RICO', 'CONTESTADO'].includes(state) ? 'attention' : state === 'SEM_DADOS' ? 'unknown' : 'measuring';
            return '<span data-tone="' + tone + '" title="Faixa ' + (index + 1) + '"></span>';
          }).join('');
        }
      }
    /** 4 blocos iguais de direção (combustível, injeção do combustível ativo, RPM, MAP): só a leitura única; "—" sem dado. */
    renderDrive(state, reading) {
      const shown = reading.level === 'fresh' || reading.level === 'late';
      const fuel = shown ? rules.fuelLabel(reading.fuel || (state.status || {}).fuelState) : DASH;
      const gas = fuel === 'GNV';
      const ms = fuel === 'CORTE' ? null : gas ? reading.gasMs : reading.petrolMs;
      text('dashDriveFuel', fuel);
      text('dashDriveFuelNote', !shown ? (reading.level === 'lost' ? 'sem dados' : 'sem leitura') : reading.level === 'late' ? 'atrasado' : fuel === 'GASOLINA' ? 'medindo a referência' : gas ? 'medindo o GNV' : 'agora');
      text('dashDriveMs', rules.ms(ms));
      text('dashDriveMsNote', ms === null ? 'ms' : gas ? 'ms · GNV' : 'ms · gasolina');
      text('dashDriveRpm', rules.rpm(reading.rpm));
      text('dashDriveRpmNote', reading.level === 'late' ? 'atrasado' : 'agora');
      text('dashDriveMap', rules.bar(reading.mapBar));
      text('dashDriveMapNote', reading.mapBar === null ? 'bar' : reading.mapBar < 0.45 ? 'bar · plano' : reading.mapBar <= 0.75 ? 'bar · subida leve' : 'bar · subida forte');
      const drive = this.root.querySelector('.now-drive');
      if (drive) rules.setDataIfChanged(drive, 'stale', reading.grey ? 'true' : 'false');
    }

    /** Mensagem do cartão de saúde: uma frase humana e o que fazer. */
    health(state, reading) {
      const status = state.status || {};
      const link = rules.connectionState(status, reading);
      const connected = status.usbConnected === true;
      const age = reading.ageMs;
      const seconds = age === null ? null : Math.round(age / 1000);
      if (!connected) {
        return { level: link.key === 'connecting' || link.key === 'denied' ? 'warning' : 'offline', message: link.label, detail: link.hint, allowUsb: link.key === 'denied' };
      }
      // Mesma frase do trilho para os estados que não são "sem dados": pausa, app travado, ECU recusou, USB recuperando.
      if (['paused', 'attention', 'refused', 'recovering', 'handshake'].includes(link.key)) {
        const stuck = status.engineStuck === true; // 'App travado' (connectionState): crítico, como a ECU que recusou
        const level = stuck || link.key === 'refused' ? 'critical' : 'warning';
        const detail = link.key === 'paused' || link.key === 'handshake' || link.key === 'recovering' ? link.hint : link.hint + '. Gravar fica bloqueado até a ECU responder.';
        return { level, message: link.label, detail };
      }
      // Leitura válida sem relógio continua sendo leitura (a faixa a mostra): só "sem dados" quando não há dado.
      if (reading.level === 'none') {
        return { level: 'warning', message: 'ECU sem dados', detail: 'Conectada, mas ainda não enviou leitura. Confira a chave e o motor.' };
      }
      if (reading.level === 'lost') {
        return { level: age > 8000 ? 'critical' : 'warning', message: `Sem dados há ${seconds} s · confira o cabo`, detail: 'Gravar fica bloqueado até os dados voltarem.' };
      }
      if (reading.level === 'late') {
        return { level: 'warning', message: 'Dados atrasados', detail: `Última leitura ${rules.ageSinceMs(age)}.` };
      }
      return { level: 'ok', message: 'Leitura em tempo real', detail: 'ECU e telemetria principal atualizadas' };
    }

    render(state) {
      if (!this.root) return;
      const status = state.status || {};
      const reading = ns.LiveStore.read(state);
      this.renderDrive(state, reading);
      text('dashLevelsRaw', reading.levelRaw === null ? DASH : Math.round(reading.levelRaw).toLocaleString('pt-BR'));
      this.renderRefino();
      const health = document.getElementById('dashHealth');
      if (health) {
        const next = this.health(state, reading);
        const signature = `${next.level}|${next.message}|${next.detail}|${next.allowUsb ? 1 : 0}`;
        if (signature !== this.lastHealthSignature) {
          this.lastHealthSignature = signature;
          health.dataset.level = next.level;
          const title = health.querySelector('.now-session-copy b');
          const copy = health.querySelector('[data-health-detail]');
          if (title) title.textContent = next.message;
          if (copy) copy.textContent = next.detail;
          const allow = health.querySelector('[data-usb-allow]');
          if (allow) allow.hidden = !next.allowUsb;
        }
      }
    }
  }
  ns.DashboardScreen = DashboardScreen;
})(typeof window !== 'undefined' ? window : globalThis);
