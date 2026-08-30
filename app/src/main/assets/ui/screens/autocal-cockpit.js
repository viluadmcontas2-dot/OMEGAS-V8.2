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
  function field(snapshot, key) {
    const fields = Array.isArray(snapshot?.fields) ? snapshot.fields : [];
    return fields.find(item => String(item?.key || '') === key && String(item?.status || '') === 'VALID') || null;
  }
  function vector(snapshot, key) {
    const item = field(snapshot, key);
    return Array.isArray(item?.rawValues) ? item.rawValues.map(value => finite(value) ?? 0) : [];
  }
  function actionLabel(action) {
    return ({
      ENABLE_AUTO_CAL: 'Ativar coleta da ECU',
      DISABLE_AUTO_CAL: 'Pausar coleta da ECU',
      RESET_PETROL: 'Apagar aprendizado de gasolina',
      RESET_GAS: 'Apagar aprendizado de GNV',
      RESET_ALL: 'Apagar tudo e começar de novo',
    })[action] || action;
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
          <section class="autocal-cockpit" aria-label="Auto-Cal da ECU">
            <header class="autocal-head">
              <div><small>APRENDIZADO NATIVO</small><h3>Auto-Cal da ECU</h3><p>A ECU coleta condições do motor e tenta ajustar o próprio AutoMatch. O OMEGAS mostra o progresso e mantém qualquer comando sob sua confirmação.</p></div>
              <div class="autocal-head-actions"><span id="autocalNativeState" class="source-status">Aguardando ECU</span><button type="button" data-autocal-read class="secondary">Atualizar leitura</button></div>
            </header>
            <section class="autocal-next-step" aria-live="polite"><div><small>ORIENTAÇÃO</small><h4>O que fazer agora</h4></div><p id="autocalGuidance">Atualize a leitura para saber se a coleta da ECU está ativa.</p></section>
            <div class="autocal-live-strip">
              <div><small>Coleta da ECU</small><b id="autocalEnable">—</b></div>
              <div><small>Progresso do AutoMatch</small><b id="autocalMatchCount">—</b></div>
              <div><small>Limite configurado</small><b id="autocalMaxMatch">—</b></div>
              <div><small>Bandas prontas</small><b id="autocalMatureCount">0</b></div>
            </div>
            <div class="autocal-layout">
              <section class="autocal-bands-card">
                <div class="autocal-section-head"><div><small>18 FAIXAS DE APRENDIZADO</small><h4>Progresso por faixa</h4></div><span>somente leitura</span></div>
                <div id="autocalBands" class="autocal-bands"></div>
                <p class="autocal-note">Cada faixa representa uma condição interna da ECU. Ela só vira referência para o mapa quando existe uma posição física confiável.</p>
              </section>
              <aside class="autocal-side">
                <section class="autocal-events-card"><div class="autocal-section-head"><div><small>RESULTADOS RECENTES</small><h4>Onde a ECU conseguiu aprender</h4></div></div><div id="autocalEvents" class="autocal-events"></div></section>
                <section class="autocal-actions-card">
                  <div class="autocal-section-head"><div><small>CONTROLE DA COLETA</small><h4>Escolha uma ação</h4></div><span>sempre confirmada</span></div>
                  <p class="autocal-action-help">Ativar ou pausar não aplica um mapa sugerido pelo OMEGAS. Apenas controla a aquisição nativa da ECU.</p>
                  <div class="autocal-actions autocal-primary-actions"><button type="button" data-autocal-action="ENABLE_AUTO_CAL">Ativar coleta</button><button type="button" data-autocal-action="DISABLE_AUTO_CAL">Pausar coleta</button></div>
                  <details class="autocal-advanced"><summary>Reiniciar aprendizado (avançado)</summary><p>Estas ações apagam dados nativos já coletados. Use apenas quando você decidiu começar uma nova aquisição.</p><div class="autocal-actions"><button type="button" data-autocal-action="RESET_PETROL">Apagar gasolina</button><button type="button" data-autocal-action="RESET_GAS">Apagar GNV</button><button type="button" data-autocal-action="RESET_ALL" class="critical">Apagar tudo</button></div></details>
                  <div id="autocalActionStatus" class="autocal-action-status">Nenhuma ação preparada.</div>
                  <details class="autocal-advanced autocal-diagnostic"><summary>Diagnóstico técnico</summary><p>O estado bruto, os comandos e a sessão aparecem na revisão antes de qualquer envio.</p></details>
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
      const switcher = document.getElementById('curveViewSwitch');
      switcher?.addEventListener('click', event => {
        const target = event.target.closest?.('[data-curve-view="autocal"]');
        if (!target) return;
        this.button = target;
        this.open();
      });
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
      const nativeStatus = snapshot.nativeStatus || state.latestSnapshot?.nativeStatus || {};
      const enabled = finite(snapshot.autoCalEnabled ?? state.autoCalEnabled);
      const autoMatchCount = finite(nativeStatus.autoMatchCount ?? state.autoMatchCount);
      const maxAutoMatch = finite(snapshot.maxAutomatch ?? state.maxAutomatch);
      const events = Array.isArray(snapshot.nativeMaturityEvents) ? snapshot.nativeMaturityEvents : [];
      this.text('autocalNativeState', state.state || (snapshot.available ? 'READY' : 'AGUARDANDO'));
      this.text('autocalState', state.state || '—');
      this.text('autocalEnable', enabled === 1 ? 'ATIVA' : enabled === 0 ? 'PAUSADA' : '—');
      this.text('autocalMatchCount', autoMatchCount ?? '—');
      this.text('autocalMaxMatch', maxAutoMatch ?? '—');
      this.text('autocalMatureCount', events.length);
      this.renderGuidance(enabled, events, snapshot.available === true);
      this.renderBands(snapshot, events);
      this.renderEvents(events);
      this.renderActionState();
    }

    renderGuidance(enabled, events, available) {
      const message = !available
        ? 'Conecte a ECU e toque em Atualizar leitura.'
        : enabled === 0
          ? 'A coleta está pausada. Toque em Ativar coleta quando quiser continuar.'
          : enabled === 1 && events.length === 0
            ? 'A coleta está ativa. Dirija normalmente; o progresso aparece nas faixas abaixo.'
            : enabled === 1
              ? 'A coleta está ativa e já produziu resultados. Revise as faixas prontas abaixo.'
              : 'Toque em Atualizar leitura para confirmar o estado da coleta.';
      this.text('autocalGuidance', message);
    }

    renderBands(snapshot, events) {
      const host = document.getElementById('autocalBands');
      if (!host) return;
      const counters = vector(snapshot, 'NUM_BUF_UPD_GAS');
      const matured = new Map(events.map(event => [Number(event.bandIndex), event]));
      if (!counters.length) {
        host.innerHTML = '<div class="detail-empty"><b>Sem contador válido</b><span>A ECU ainda não publicou NUM_BUF_UPD_GAS neste snapshot.</span></div>';
        return;
      }
      host.innerHTML = counters.slice(0, 18).map((count, index) => {
        const event = matured.get(index);
        const threshold = finite(event?.threshold);
        const ratio = threshold && threshold > 0 ? Math.min(100, count / threshold * 100) : 0;
        const correlated = String(event?.correlationState || '') === 'CORRELATED';
        const state = event ? (correlated ? 'anchored' : 'mature') : count > 0 ? 'collecting' : 'empty';
        return `<div class="autocal-band" data-state="${state}"><header><span>B${String(index + 1).padStart(2, '0')}</span><b>${Math.round(count)}</b></header><i style="--progress:${ratio}%"></i><small>${event ? (correlated ? 'âncora correlacionada' : 'madura · sem posição confiável') : count > 0 ? 'coletando' : 'sem dados'}</small></div>`;
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
        return `<article data-state="${correlated ? 'correlated' : 'raw'}"><div><b>B${Number(event.bandIndex) + 1}</b><span>${escapeHtml(event.zone || 'zona')}</span></div><p>${correlated ? `${rpm === null ? 'RPM —' : `${Math.round(rpm).toLocaleString('pt-BR')} RPM`} · precisão da correlação ${confidence}%` : escapeHtml(event.correlationReason || 'NO_RELIABLE_CORRELATION')}</p><small>contador ${finite(event.counter) ?? '—'} · limiar ${finite(event.threshold) ?? '—'}</small></article>`;
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
      review.innerHTML = `<div class="autocal-review-card"><header><div><small>REVISE ANTES DE CONTINUAR</small><h3>${escapeHtml(prepared.label || actionLabel(prepared.action))}</h3></div><button type="button" data-autocal-cancel class="icon-close" aria-label="Fechar revisão">×</button></header><p>${escapeHtml(prepared.description || '')}</p><div class="write-contract"><b>Nada foi enviado.</b><span>O próximo botão abre a confirmação final do Android. O comando só sai depois da sua confirmação positiva.</span></div><details class="autocal-advanced"><summary>Diagnóstico técnico</summary><dl><div><dt>Ação</dt><dd>${escapeHtml(prepared.action)}</dd></div><div><dt>Comando</dt><dd>${escapeHtml(prepared.commandHex || '—')}</dd></div><div><dt>Sessão</dt><dd>${escapeHtml(prepared.sessionId || '—')}</dd></div><div><dt>ECU pode alterar MUL_ACT</dt><dd>${prepared.mayChangeMulAct ? 'sim' : 'não'}</dd></div></dl></details><div class="operation-actions"><button type="button" data-autocal-cancel class="secondary">Cancelar</button><button type="button" data-autocal-confirm class="danger-primary">Abrir confirmação final</button></div></div>`;
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
