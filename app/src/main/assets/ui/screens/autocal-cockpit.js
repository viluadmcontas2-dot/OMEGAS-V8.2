(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  function finite(value) {
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  }
  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[char]));
  }
  function actionLabel(action) {
    return ({
      ENABLE_AUTO_CAL: 'Habilitar Auto Calibration',
      DISABLE_AUTO_CAL: 'Pausar Auto Calibration',
      RESET_PETROL: 'Resetar aquisição gasolina',
      RESET_GAS: 'Resetar aquisição GNV',
      RESET_ALL: 'Começar nova aquisição AutoCal',
    })[action] || action;
  }
  function fuelLabel(fuel) {
    return ({ PETROL: 'Gasolina', GAS: 'GNV atual', GAS_PREVIOUS: 'GNV anterior' })[String(fuel || '')] || String(fuel || '—');
  }

  class AutoCalCockpit {
    constructor(app) {
      this.app = app;
      this.store = app.store;
      this.scheduler = app.scheduler;
      this.api = ns.AutoCalApi;
      this.active = false;
      this.prepared = null;
      this.state = {};
      this.snapshot = {};
      this.actionState = {};
      this.inject();
      this.bind();
      this.unsubscribeContext = this.scheduler.addHook('context', () => {
        if (this.store.get().route === 'curve' && this.active) this.refresh();
      });
    }

    inject() {
      if (!document.querySelector('link[data-autocal-cockpit-style]')) {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = 'styles-autocal-cockpit.css';
        link.dataset.autocalCockpitStyle = 'true';
        document.head.appendChild(link);
      }
      const switcher = document.getElementById('curveViewSwitch');
      if (switcher && !switcher.querySelector('[data-curve-view="autocal"]')) {
        const button = document.createElement('button');
        button.type = 'button';
        button.dataset.curveView = 'autocal';
        button.textContent = 'AutoCal';
        switcher.appendChild(button);
        this.button = button;
      } else {
        this.button = switcher?.querySelector('[data-curve-view="autocal"]') || null;
      }

      const stack = document.querySelector('[data-screen="curve"] .curve-view-stack');
      if (stack && !stack.querySelector('[data-curve-panel="autocal"]')) {
        const panel = document.createElement('div');
        panel.className = 'curve-view autocal-cockpit-view';
        panel.dataset.curvePanel = 'autocal';
        panel.innerHTML = `
          <section class="autocal-cockpit" aria-label="Cockpit Auto Calibration nativa">
            <header class="autocal-head">
              <div><small>AUTO CALIBRATION NATIVA</small><h3>O que a ECU está aprendendo agora</h3><p>Observação e controle manual da aquisição nativa. AutoMatch continua dentro da ECU.</p></div>
              <div class="autocal-head-actions"><span id="autocalNativeState" class="source-status">Aguardando ECU</span><button type="button" data-autocal-read class="secondary">Solicitar snapshot</button></div>
            </header>
            <div class="autocal-live-strip">
              <div><small>ESTADO</small><b id="autocalState">—</b></div>
              <div><small>AQUISIÇÃO</small><b id="autocalEnable">—</b></div>
              <div><small>AUTOMATCH ECU</small><b id="autocalMatchCount">—</b></div>
              <div><small>MÁX. AUTOMATCH</small><b id="autocalMaxMatch">—</b></div>
              <div><small>EVENTOS MADUROS</small><b id="autocalMatureCount">0</b></div>
            </div>
            <div class="autocal-layout">
              <section class="autocal-bands-card">
                <div class="autocal-section-head"><div><small>MAP × TPET · 18 BANDAS POR FAMÍLIA</small><h4>Gasolina · GNV atual · GNV anterior</h4></div><span>Kotlin é a autoridade</span></div>
                <div id="autocalBands" class="autocal-bands"></div>
                <p class="autocal-note">X = tempo de injeção gasolina (Tpet); Y = MAP. Ponto sem coordenada física permanece sem posição. Os 30 pontos de Curva K/referência não viram bolinhas de aquisição.</p>
              </section>
              <aside class="autocal-side">
                <section class="autocal-events-card"><div class="autocal-section-head"><div><small>MATURIDADE</small><h4>Últimos eventos</h4></div></div><div id="autocalEvents" class="autocal-events"></div></section>
                <section class="autocal-actions-card">
                  <div class="autocal-section-head"><div><small>CONTROLE MANUAL</small><h4>Ações da ECU</h4></div><span>dupla confirmação</span></div>
                  <div class="autocal-actions">
                    <button type="button" data-autocal-action="ENABLE_AUTO_CAL">Habilitar coleta</button>
                    <button type="button" data-autocal-action="DISABLE_AUTO_CAL">Pausar coleta</button>
                    <button type="button" data-autocal-action="RESET_PETROL">Reset gasolina</button>
                    <button type="button" data-autocal-action="RESET_GAS">Reset GNV</button>
                    <button type="button" data-autocal-action="RESET_ALL" class="critical">Nova aquisição</button>
                  </div>
                  <div id="autocalActionStatus" class="autocal-action-status">Nenhuma ação preparada.</div>
                </section>
              </aside>
            </div>
            <div id="autocalReview" class="autocal-review" hidden></div>
          </section>`;
        stack.appendChild(panel);
        this.panel = panel;
      } else {
        this.panel = stack?.querySelector('[data-curve-panel="autocal"]') || null;
      }
    }

    bind() {
      this.button?.addEventListener('click', () => this.open());
      document.querySelectorAll('#curveViewSwitch [data-curve-view="learning"], #curveViewSwitch [data-curve-view="editor"]').forEach(button => {
        button.addEventListener('click', () => { this.active = false; });
      });
      this.panel?.querySelector('[data-autocal-read]')?.addEventListener('click', () => this.requestRead());
      this.panel?.querySelectorAll('[data-autocal-action]').forEach(button => {
        button.addEventListener('click', () => this.prepare(button.dataset.autocalAction));
      });
      this.panel?.addEventListener('click', event => {
        if (event.target.closest('[data-autocal-cancel]')) this.cancelPrepared();
        if (event.target.closest('[data-autocal-confirm]')) this.confirmPrepared();
      });
    }

    open() {
      this.active = true;
      document.querySelectorAll('#curveViewSwitch [data-curve-view]').forEach(button => button.classList.toggle('active', button === this.button));
      document.querySelectorAll('[data-screen="curve"] [data-curve-panel]').forEach(panel => panel.classList.toggle('active', panel === this.panel));
      this.refresh();
    }

    refresh() {
      if (!this.api?.available?.()) {
        this.renderUnavailable();
        return;
      }
      this.state = this.api.status() || {};
      this.snapshot = this.api.snapshot() || {};
      this.actionState = this.api.actionStatus() || {};
      this.render();
    }

    requestRead() {
      if (!this.api?.available?.()) return;
      const result = this.api.startRead();
      if (result?.ok === false) {
        this.store.patch({ alert: { level: 'warning', message: result.error || 'Leitura AutoCal indisponível.' } });
      } else {
        this.store.patch({ alert: { level: 'ok', message: 'Snapshot AutoCal solicitado. A telemetria continua sob a mesma engine MP48.' } });
      }
      this.refresh();
    }

    prepare(action) {
      if (!action || !this.api?.available?.()) return;
      const result = this.api.prepare(action);
      if (!result?.ok || !result?.prepared) {
        this.store.patch({ alert: { level: 'warning', message: result?.error || 'A ação AutoCal não pôde ser preparada.' } });
        return;
      }
      this.prepared = result;
      this.renderReview();
    }

    cancelPrepared() {
      this.api?.cancelPreparation?.();
      this.prepared = null;
      const review = document.getElementById('autocalReview');
      if (review) { review.hidden = true; review.innerHTML = ''; }
      this.refresh();
    }

    confirmPrepared() {
      const prepared = this.prepared;
      if (!prepared?.preparationId) return;
      const result = this.api.execute(prepared.preparationId);
      if (result?.ok === false) {
        this.store.patch({ alert: { level: 'warning', message: result.error || 'A confirmação Android não pôde ser aberta.' } });
        return;
      }
      this.prepared = null;
      const review = document.getElementById('autocalReview');
      if (review) { review.hidden = true; review.innerHTML = ''; }
      this.store.patch({ alert: { level: 'warning', message: 'Confirmação Android aberta. O comando ainda não foi enviado até você confirmar lá.' } });
      this.refresh();
    }

    render() {
      const state = this.state || {};
      const snapshot = this.snapshot || {};
      const projection = snapshot.humanProjection || {};
      const nativeStatus = snapshot.nativeStatus || state.latestSnapshot?.nativeStatus || {};
      const enabled = finite(snapshot.autoCalEnabled ?? state.autoCalEnabled);
      const autoMatchCount = finite(nativeStatus.autoMatchCount ?? state.autoMatchCount);
      const maxAutoMatch = finite(snapshot.maxAutomatch ?? state.maxAutomatch);
      const events = Array.isArray(snapshot.nativeMaturityEvents) ? snapshot.nativeMaturityEvents : [];
      this.text('autocalNativeState', projection.message || state.state || (snapshot.available ? 'READY' : 'AGUARDANDO'));
      this.text('autocalState', projection.message || projection.state || state.state || '—');
      this.text('autocalEnable', enabled === 1 ? 'ATIVA' : enabled === 0 ? 'PAUSADA' : '—');
      this.text('autocalMatchCount', autoMatchCount ?? '—');
      this.text('autocalMaxMatch', maxAutoMatch ?? '—');
      this.text('autocalMatureCount', events.length);
      this.renderBands(projection);
      this.renderEvents(events);
      this.renderActionState();
    }

    renderBands(projection) {
      const host = document.getElementById('autocalBands');
      if (!host) return;
      const points = Array.isArray(projection?.acquisitionPoints) ? projection.acquisitionPoints : [];
      if (!points.length) {
        host.innerHTML = '<div class="detail-empty"><b>Estado nativo insuficiente</b><span>A projeção Kotlin ainda não possui as 18 bandas físicas de aquisição.</span></div>';
        return;
      }
      const families = ['PETROL', 'GAS', 'GAS_PREVIOUS'];
      host.innerHTML = families.map(fuel => {
        const family = points.filter(point => String(point.fuel || '') === fuel);
        if (!family.length) return '';
        const cards = family.map(point => {
          const positioned = point.positioned === true;
          const tpet = finite(point.tPetrolMs);
          const map = finite(point.mapBar);
          const anchor = point.correlatedAnchor === true;
          const state = anchor ? 'anchored' : String(point.maturity || 'UNKNOWN').toLowerCase();
          const coordinate = positioned && tpet !== null && map !== null
            ? `${tpet.toFixed(2)} ms · MAP ${map.toFixed(3)} bar`
            : 'sem coordenada física';
          return `<div class="autocal-band" data-state="${escapeHtml(state)}"><header><span>B${String(Number(point.bandIndex) + 1).padStart(2, '0')}</span><b>Z${Number(point.zone) + 1}</b></header><small>${escapeHtml(coordinate)}</small><small>${escapeHtml(point.maturity || 'UNKNOWN')}${anchor ? ' · âncora correlacionada' : ''}</small></div>`;
        }).join('');
        return `<div class="autocal-family" data-fuel="${fuel}"><div class="autocal-family-title"><b>${escapeHtml(fuelLabel(fuel))}</b><span>18 bandas · ${escapeHtml(projection.xAxis || 'TPET_MS')} × ${escapeHtml(projection.yAxis || 'MAP_BAR')}</span></div>${cards}</div>`;
      }).join('');
    }

    renderEvents(events) {
      const host = document.getElementById('autocalEvents');
      if (!host) return;
      if (!events.length) {
        host.innerHTML = '<p class="empty-copy">Nenhuma banda recém-amadurecida neste snapshot.</p>';
        return;
      }
      host.innerHTML = events.slice(-6).reverse().map(event => {
        const correlated = String(event.correlationState || '') === 'CORRELATED';
        const rpm = finite(event.rpm);
        const confidence = Math.round((finite(event.correlationConfidence) || 0) * 100);
        return `<article data-state="${correlated ? 'correlated' : 'raw'}"><div><b>B${Number(event.bandIndex) + 1}</b><span>${escapeHtml(event.zone || 'zona')}</span></div><p>${correlated ? `${rpm === null ? 'RPM —' : `${Math.round(rpm).toLocaleString('pt-BR')} RPM`} · confiança ${confidence}%` : escapeHtml(event.correlationReason || 'NO_RELIABLE_CORRELATION')}</p><small>contador ${finite(event.counter) ?? '—'} · limiar ${finite(event.threshold) ?? '—'}</small></article>`;
      }).join('');
    }

    renderActionState() {
      const host = document.getElementById('autocalActionStatus');
      if (!host) return;
      const state = this.actionState || {};
      const name = String(state.state || 'IDLE');
      const progress = finite(state.progress);
      host.innerHTML = `<b>${escapeHtml(name)}</b><span>${escapeHtml(state.message || 'Nenhuma ação preparada.')}</span>${progress === null ? '' : `<i style="--progress:${Math.max(0, Math.min(100, progress))}%"></i>`}`;
    }

    renderReview() {
      const review = document.getElementById('autocalReview');
      const prepared = this.prepared;
      if (!review || !prepared) return;
      review.hidden = false;
      review.innerHTML = `<div class="autocal-review-card"><header><div><small>REVISÃO WEBVIEW</small><h3>${escapeHtml(prepared.label || actionLabel(prepared.action))}</h3></div><button type="button" data-autocal-cancel class="icon-close">×</button></header><p>${escapeHtml(prepared.description || '')}</p><dl><div><dt>Ação</dt><dd>${escapeHtml(prepared.action)}</dd></div><div><dt>Comando</dt><dd>${escapeHtml(prepared.commandHex || '—')}</dd></div><div><dt>Sessão</dt><dd>${escapeHtml(prepared.sessionId || '—')}</dd></div><div><dt>ECU pode alterar MUL_ACT</dt><dd>${prepared.mayChangeMulAct ? 'sim' : 'não'}</dd></div></dl><div class="write-contract"><b>Ainda não foi enviado.</b><span>Continuar abre uma segunda confirmação Android. Só o botão positivo desse diálogo envia o comando.</span></div><div class="operation-actions"><button type="button" data-autocal-cancel class="secondary">Cancelar</button><button type="button" data-autocal-confirm class="danger-primary">Continuar para confirmação Android</button></div></div>`;
    }

    renderUnavailable() {
      this.text('autocalNativeState', 'BRIDGE INDISPONÍVEL');
      const host = document.getElementById('autocalBands');
      if (host) host.innerHTML = '<div class="detail-empty"><b>AutoCal indisponível</b><span>O bridge nativo ainda não foi anexado à WebView.</span></div>';
    }

    text(id, value) {
      const node = document.getElementById(id);
      if (node && node.textContent !== String(value ?? '—')) node.textContent = String(value ?? '—');
    }
  }

  function boot() {
    const app = root.OmegasApp;
    if (!app?.store || !app?.scheduler || !ns.AutoCalApi) {
      root.setTimeout(boot, 25);
      return;
    }
    if (app.autoCalCockpit) return;
    app.autoCalCockpit = new AutoCalCockpit(app);
  }

  ns.AutoCalCockpit = AutoCalCockpit;
  boot();
})(typeof window !== 'undefined' ? window : globalThis);
