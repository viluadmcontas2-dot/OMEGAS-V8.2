(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  const rules = ns.DisplayRules;
  const DASH = '—';

  function text(id, value) {
    const node = document.getElementById(id);
    if (!node) return;
    const next = value == null ? DASH : String(value);
    if (node.textContent !== next) node.textContent = next;
    const empty = next === DASH ? 'true' : 'false';
    if (node.dataset.empty !== empty) node.dataset.empty = empty;
  }

  const fuelLabel = rules.fuelLabel;
  function ensureStyles() {
    if (document.querySelector('link[data-dashboard-now]')) return;
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = 'styles-dashboard-now.css';
    link.dataset.dashboardNow = 'true';
    document.head.appendChild(link);
  }

  /**
   * Agora é para dirigir: 4 valores de peso igual (injeção em ms, RPM, MAP e combustível) lidos de braço esticado.
   * Frescor numa regra só (LiveStore.read): até 1,5 s normal; de 1,5 a 3 s cinza + "atrasado"; acima de 3 s os números
   * viram "—" e o cartão de baixo diz "Sem dados há N s · confira o cabo". Valor velho nunca finge ser de agora.
   */
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
            if (app && app.router) app.router.navigate('refino');
          }
        });
      }
      this.root.innerHTML = `
        <div class="now-dashboard-shell">
          <section class="now-tile-grid" aria-label="Leitura principal">
            <article class="now-tile" data-tile="petrol"><small>INJEÇÃO</small><b><span id="dashHeroPetrol">—</span><em>ms</em></b></article>
            <article class="now-tile" data-tile="rpm"><small>RPM</small><b><span id="dashRpm">—</span><em>rpm</em></b></article>
            <article class="now-tile" data-tile="map"><small>MAP</small><b><span id="dashMap">—</span><em>bar</em></b></article>
            <article class="now-tile" data-tile="fuel"><small>COMBUSTÍVEL</small><b><span id="dashFuel">—</span></b><span class="now-tile-sub" id="dashFuelSub" hidden></span></article>
          </section>

          <section class="now-quiet-row" aria-label="Condição e apoio">
            <div id="dashHealth" class="now-session-card" data-level="offline">
              <span class="state-indicator"></span>
              <div class="now-session-copy"><b>Sem cabo</b><p data-health-detail>Conecte o cabo USB na ECU</p></div>
              <button type="button" class="primary" data-usb-allow hidden>Permitir USB</button>
            </div>
            <article class="now-quiet-tile" id="dashLevelsTile" hidden><small>NÍVEIS</small><b id="dashLevelsRaw">—</b></article>
            <article class="now-quiet-tile now-refino-card" id="dashRefinoTile" hidden role="button" data-dash-refino><small>REFINO</small><b id="dashRefino">—</b></article>
          </section>
        </div>`;
    }

    /** Fase do refino (o nosso AutoCal) em uma linha; consulta a cada 3 s, no máximo. */
    renderRefino() {
      const now = Date.now();
      if (this.refinoAt && now - this.refinoAt < 3000) return;
      this.refinoAt = now;
      const api = root.OmegasUi && root.OmegasUi.AutoCalApi;
      const eq = api && typeof api.refinementPhase === 'function' ? api.refinementPhase() : null;
      const pilot = (eq && eq.autopilot) || {};
      const refinoLabel = pilot.phase ? rules.phaseLabel(pilot.phase, pilot.expiredFrom) : DASH;
      text('dashRefino', refinoLabel);
      const refinoTile = document.getElementById('dashRefinoTile');
      if (refinoTile) refinoTile.hidden = !refinoLabel || refinoLabel === DASH;
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
      if (status.engineStuck === true) {
        return { level: 'critical', message: 'Comunicação travada', detail: 'Aguarde ou reconecte o cabo USB. Gravar fica bloqueado até a ECU responder.' };
      }
      if (reading.level === 'none' || reading.ageUnknown) {
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
      const fuel = reading.level === 'fresh' || reading.level === 'late' ? fuelLabel(reading.fuel || status.fuelState) : DASH;
      const cutoff = fuel === 'CORTE';
      text('dashHeroPetrol', cutoff ? DASH : rules.ms(reading.petrolMs));
      text('dashRpm', rules.rpm(reading.rpm));
      text('dashMap', rules.bar(reading.mapBar));
      text('dashFuel', fuel);
      const fuelSub = document.getElementById('dashFuelSub');
      if (fuelSub) { fuelSub.hidden = !cutoff; fuelSub.textContent = cutoff ? 'Desacelerando' : ''; }
      const levelsTile = document.getElementById('dashLevelsTile');
      if (levelsTile) levelsTile.hidden = reading.levelRaw === null;
      text('dashLevelsRaw', reading.levelRaw === null ? DASH : Math.round(reading.levelRaw).toLocaleString('pt-BR'));
      this.renderRefino();
      const tiles = this.root.querySelector('.now-tile-grid');
      if (tiles) {
        const connected = status.usbConnected === true;
        const staleTiles = connected && reading.grey ? 'true' : 'false';
        if (tiles.dataset.stale !== staleTiles) tiles.dataset.stale = staleTiles;
      }
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
