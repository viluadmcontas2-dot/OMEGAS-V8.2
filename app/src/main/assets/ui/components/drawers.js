(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Desconhecido (null/vazio) fica desconhecido: Number(null) seria 0 e viraria "0,0% · equivalente".
  function finite(value) { return value === null || value === undefined || value === '' || !Number.isFinite(Number(value)) ? null : Number(value); }
  function fmt(value, digits) {
    const n = finite(value);
    return n === null ? '—' : n.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits });
  }
  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[char]));
  }
  function bytesLabel(bytes) {
    const n = finite(bytes) || 0;
    if (n >= 1024 * 1024) return `${fmt(n / 1024 / 1024, 1)} MB`;
    if (n >= 1024) return `${fmt(n / 1024, 0)} KB`;
    return `${Math.round(n)} B`;
  }
  function durationLabel(ms) {
    if (finite(ms) === null) return '—'; // desconhecido não vira "0m 0s"
    const value = Math.max(0, finite(ms));
    const minutes = Math.floor(value / 60000);
    const seconds = Math.floor((value % 60000) / 1000);
    if (minutes >= 60) return `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
    return `${minutes}m ${seconds}s`;
  }
  /** Dados da ECU em palavras (dono, 2026-10-11: sem "Telemetria 42 ms"). */
  function ageLabel(ms) {
    const value = finite(ms);
    if (value === null || value < 0) return 'sem dados';
    if (value <= 2500) return 'chegando';
    return `sem dados há ${Math.round(value / 1000)} s`;
  }

  const LEVELS = { ALL: 'Tudo', ERROR: 'Só erros', WARN: 'Só avisos', INFO: 'Só informações' };
  const SESSIONS_PER_PAGE = 3;
  const LOG_PAGE_SIZE = 5;

  // Ferramentas, página "App e arquivos" + tela Sessões (dono, 2026-10-18): um cartão por função, com o estado escrito
  // (Ligado/Desligado) e UM controle com verbo. Interruptor = liga/desliga. Nada de sigla na frente; sem rolagem.
  class Drawers {
    constructor(store, router, api) {
      this.store = store;
      this.router = router;
      this.api = api;
      this.tools = document.getElementById('toolsDrawer');
      this.logLevel = 'ALL';
      this.logCategory = 'ALL';
      // Sem rolagem: o registro mostra uma página por vez em vez de uma lista que rola.
      this.logPage = 0;
      this.sessionPage = 0;
      this.view = 'home';          // 'home' | 'log'
      this.page = 1;               // página de Ferramentas: 1 = motor e combustível, 2 = app e arquivos
      this.probeNote = '';
      this.sessionSettingsFeedback = '';
      this.ensureToolsExpansion();
      this.bind();
    }

    ensureToolsExpansion() {
      if (!this.tools || document.getElementById('toolDiagnosticsWorkspace')) return;
      const host = document.createElement('div');
      host.id = 'toolDiagnosticsWorkspace';
      host.className = 'tool-grid';
      this.tools.appendChild(host);
    }

    bind() {
      document.querySelectorAll('[data-close-drawer]').forEach(button => button.addEventListener('click', () => {
        this.store.patch({ toolsOpen: false });
      }));
      document.querySelectorAll('[data-tools-page]').forEach(button => button.addEventListener('click', () => this.setPage(Number(button.dataset.toolsPage))));
      this.syncAutoRefine();
      for (const id of ['toolDiagnosticsWorkspace', 'sessionsHost']) {
        document.getElementById(id)?.addEventListener('click', event => this.handleToolClick(event));
        document.getElementById(id)?.addEventListener('change', event => this.handleToolChange(event));
      }
    }

    setPage(page) {
      this.page = page === 2 ? 2 : 1;
      this.view = 'home';
      if (this.tools) this.tools.dataset.page = String(this.page);
      this.toolsSignature = '';
      this.renderTools(this.store.get());
      this.syncView();
    }

    /** A tela inteira está "em visão" quando qualquer página abriu um cartão (some o rodapé de páginas). */
    syncView() {
      if (this.tools) this.tools.dataset.view = this.tools.querySelector('[data-mode="view"]') ? 'view' : 'home';
    }

    handleToolClick(event) {
      const target = event.target.closest('button');
      if (!target) return;
      if (target.id === 'toolExportData') this.api.exportData();
      else if (target.id === 'toolExportLearning') this.api.exportLearning();
      else if (target.id === 'toolImportLearning') this.api.importLearning();
      else if (target.id === 'toolBufferProbe') this.runBufferProbe();
      else if (target.id === 'toolAutoRefine') this.toggleAutoRefine();
      else if (target.matches('[data-session-start]')) {
        const result = this.api.startSession('registro manual pela interface');
        this.notifyResult(result, 'Gravação iniciada.');
        this.refreshSessionStatus(result);
      } else if (target.matches('[data-session-stop]')) {
        const result = this.api.stopSession('parada manual pela interface');
        this.notifyResult(result, 'Gravação encerrada.');
        this.refreshSessionStatus(result);
      } else if (target.matches('[data-session-settings]')) {
        this.applySessionSettings();
      } else if (target.matches('[data-export-session]')) {
        this.api.exportSession(target.dataset.exportSession || '');
      } else if (target.matches('[data-session-page]')) {
        this.sessionPage = Math.max(0, (this.sessionPage || 0) + (target.dataset.sessionPage === 'older' ? 1 : -1));
        this.toolsSignature = '';
        this.renderSessions(this.store.get());
      } else if (target.matches('[data-tool-battery-request]')) {
        this.api.requestBatteryOptimizationExemption?.();
        this.toolsSignature = '';
      } else if (target.matches('[data-tool-overlay-request]')) {
        this.api.requestOverlayPermissionAndEnable?.();
        this.toolsSignature = '';
      } else if (target.matches('[data-tool-overlay-enable]')) {
        this.api.setTelemetryOverlayEnabled?.(true);
        this.toolsSignature = '';
        this.renderTools(this.store.get());
      } else if (target.matches('[data-tool-overlay-disable]')) {
        this.api.setTelemetryOverlayEnabled?.(false);
        this.toolsSignature = '';
        this.renderTools(this.store.get());
      } else if (target.matches('[data-tool-open-log]')) {
        this.view = 'log';
        this.logPage = 0;
        this.toolsSignature = '';
        this.renderTools(this.store.get());
        this.syncView();
      } else if (target.matches('[data-tool-back]')) {
        this.view = 'home';
        this.toolsSignature = '';
        this.renderTools(this.store.get());
        this.syncView();
      } else if (target.matches('[data-tool-export-logs]')) {
        this.api.exportLogs();
      } else if (target.matches('[data-tool-selftest]')) {
        const result = this.api.selfTest();
        this.store.patch({ alert: { level: result?.ok ? 'ok' : 'warning', message: result?.ok ? 'Teste do app concluído.' : (result?.error || 'O teste do app não terminou. Tente de novo.') } });
      } else if (target.matches('[data-log-page]')) {
        this.logPage = Math.max(0, (this.logPage || 0) + (target.dataset.logPage === 'next' ? 1 : -1));
        this.toolsSignature = '';
        this.renderTools(this.store.get());
      }
    }

    handleToolChange(event) {
      if (event.target.matches('[data-log-level]')) {
        this.logLevel = event.target.value || 'ALL';
        this.logPage = 0;
        this.toolsSignature = '';
        this.renderTools(this.store.get());
      }
      if (event.target.matches('[data-log-category]')) {
        this.logCategory = event.target.value || 'ALL';
        this.logPage = 0;
        this.toolsSignature = '';
        this.renderTools(this.store.get());
      }
    }

    // Teste de gravação nos pontos (dono, 2026-10-17): toque duplo (arma e confirma), sem diálogo do sistema.
    setProbeNote(text) {
      this.probeNote = text;
      const label = document.getElementById('toolBufferProbe')?.querySelector('span');
      if (label) label.textContent = text;
    }

    runBufferProbe() {
      const button = document.getElementById('toolBufferProbe');
      const bridge = window.OmegasAutoCal;
      if (!button || !bridge?.startBufferWriteProbe) {
        this.store.patch({ alert: { level: 'warning', message: 'O teste só funciona no app, com a central.' } });
        return;
      }
      if (this.bufferProbeRunning) return;
      if (!this.bufferProbeArmed) {
        this.bufferProbeArmed = true;
        this.setProbeNote('Toque de novo para testar');
        const disarmNow = () => {
          this.bufferProbeArmed = false;
          this.setProbeNote('');
          this.bufferProbeDisarm?.();
        };
        // Desarma na troca de aba, no USB desconectado e em qualquer outro toque (evento, nunca prazo).
        const disarm = state => {
          if (state.route === 'tools' && state.status?.usbConnected !== false) return;
          disarmNow();
        };
        const otherClick = event => { if (!button.contains?.(event?.target)) disarmNow(); };
        this.bufferProbeDisarm?.();
        const unsubscribe = this.store.subscribe(disarm);
        document.addEventListener?.('click', otherClick, true);
        this.bufferProbeDisarm = () => {
          this.bufferProbeDisarm = null;
          unsubscribe?.();
          document.removeEventListener?.('click', otherClick, true);
        };
        return;
      }
      this.bufferProbeDisarm?.();
      this.bufferProbeArmed = false;
      let started;
      try { started = JSON.parse(bridge.startBufferWriteProbe()); } catch (_) { started = { ok: false, error: 'Resposta inválida do teste.' }; }
      if (started?.ok === false) {
        this.setProbeNote(started.error || 'Não começou.');
        this.notifyResult(started, '');
        return;
      }
      this.bufferProbeRunning = true;
      this.setProbeNote('Testando…');
      const poll = () => {
        let status = {};
        try { status = JSON.parse(bridge.getBufferWriteProbeStatus()); } catch (_) { status = {}; }
        if (status.state !== 'DONE') {
          // Sem resposta final e o USB caiu: a espera termina; o resultado (se vier) fica na sessão.
          if (this.store.get?.()?.status?.usbConnected === false) {
            this.bufferProbePoll?.();
            this.bufferProbeRunning = false;
            this.setProbeNote('O cabo soltou. O resultado fica na gravação da viagem.');
          }
          return;
        }
        this.bufferProbePoll?.();
        this.bufferProbeRunning = false;
        const text = Drawers.describeBufferProbe(status);
        this.setProbeNote(text);
        this.store.patch({ alert: { level: status.outcome === 'ACCEPTED_AND_HELD' || status.outcome === 'REFUSED' ? 'ok' : 'warning', message: text } });
      };
      this.bufferProbePoll = window.OmegasApp.scheduler.addHook('fast', poll);
    }

    // Refino automático depois de cada AutoMatch da central: Desligado · Ligado (só sugere). O app nunca grava sozinho (dono, 2026-10-18).
    static describeAutoRefineMode(mode) {
      if (mode === 'OFF') return 'Desligado: não sugere nada depois do AutoMatch';
      return 'Ligado: só sugere; gravar é sempre manual';
    }

    syncAutoRefine() {
      const label = document.getElementById('toolAutoRefine')?.querySelector('span');
      const bridge = window.OmegasAutoCal;
      if (!bridge?.getAutoRefineMode) return;
      let mode = 'SHADOW';
      try { mode = String(bridge.getAutoRefineMode()) || 'SHADOW'; } catch (_) { mode = 'SHADOW'; }
      this.autoRefineMode = mode;
      if (label) label.textContent = mode === 'OFF' ? 'Desligado' : 'Ligado';
      const button = document.getElementById('toolAutoRefine');
      if (button?.setAttribute) button.setAttribute('aria-checked', mode === 'OFF' ? 'false' : 'true');
    }

    toggleAutoRefine() {
      const bridge = window.OmegasAutoCal;
      if (!bridge?.setAutoRefineMode) return;
      const next = (this.autoRefineMode || 'SHADOW') === 'OFF' ? 'SHADOW' : 'OFF';
      let result;
      try { result = JSON.parse(bridge.setAutoRefineMode(next)); } catch (_) { result = { ok: false, error: 'Resposta inválida' }; }
      if (result?.ok === false) { this.notifyResult(result, ''); this.syncAutoRefine(); return; }
      this.autoRefineMode = next;
      this.toolsSignature = '';
      this.syncAutoRefine();
      this.renderTools(this.store.get());
    }

    static describeBufferProbe(status) {
      const back = status.restoreNeeded ? (status.restored ? ' Original restaurado.' : ' ATENÇÃO: o original NÃO voltou. Use Reaprender GNV no AutoCal.') : '';
      switch (status.outcome) {
        case 'REFUSED': return 'A central recusou escrever nos pontos (só leitura). Nada mudou.';
        case 'ACCEPTED_AND_HELD': return 'A central aceitou e o valor ficou (faixa ' + status.band + ').' + back;
        case 'ACCEPTED_THEN_OVERWRITTEN': return 'A central aceitou, mas desfez sozinha o valor.' + back;
        case 'ACCEPTED_BUT_DIFFERENT': return 'A central aceitou, mas guardou outro valor.' + back;
        case 'NO_STABLE_BAND': return 'Nenhuma faixa do GNV está cheia ainda; nada foi escrito.';
        default: return 'Sonda sem resultado: ' + (status.message || 'erro') + back;
      }
    }

    notifyResult(result, successMessage) {
      if (result?.ok === false) {
        this.store.patch({ alert: { level: 'warning', message: result.error || 'Não deu certo. Tente de novo.' } });
      } else {
        this.store.patch({ alert: { level: 'ok', message: successMessage } });
      }
    }

    refreshSessionStatus(result) {
      if (!result || typeof result !== 'object' || result.ok === false) return;
      this.store.patch({ sessionStatus: result });
      this.toolsSignature = '';
      this.renderSessions(this.store.get());
    }

    applySessionSettings() {
      const host = document.getElementById('sessionsHost');
      if (!host) return;
      const settings = {
        telemetryEveryMs: Number(host.querySelector('[data-session-telemetry]')?.value || 250),
        maxSessionMb: Number(host.querySelector('[data-session-maxmb]')?.value || 256),
        keepSessions: Math.max(20, Number(host.querySelector('[data-session-keep]')?.value || 20)),
        autoStartOnUsb: host.querySelector('[data-session-autostart]')?.checked === true,
        captureRawUsb: host.querySelector('[data-session-rawusb]')?.checked === true,
      };
      const result = this.api.setSessionSettings(settings);
      const applied = result?.settings || settings;
      if (result?.ok === false) {
        this.sessionSettingsFeedback = result.error || 'Não deu para aplicar. Tente de novo.';
      } else {
        this.sessionSettingsFeedback = `Pronto: ${applied.maxSessionMb || settings.maxSessionMb} MB por gravação · guarda ${applied.keepSessions || settings.keepSessions} gravações`;
      }
      this.notifyResult(result, 'Tamanho das gravações atualizado.');
      this.refreshSessionStatus(result);
    }

    render(state) {
      const route = state.route;
      if (this.tools) this.tools.classList.toggle('open', route === 'tools');
      document.body.classList.toggle('drawer-open', route === 'tools');
      if (route !== 'tools' && this.view !== 'home') { this.view = 'home'; this.toolsSignature = ''; }
      if (route === 'tools') this.renderTools(state);
      if (route === 'sessions') this.renderSessions(state);
    }

    preserveSessionSettingsInteraction(host) {
      const panel = host?.querySelector('.diagnostic-settings');
      if (!panel) return false;
      const active = document.activeElement;
      return !!(active && panel.contains(active) && ['INPUT', 'SELECT'].includes(active.tagName));
    }

    // ---------- Ferramentas, página 2 ----------
    renderTools(state) {
      const host = document.getElementById('toolDiagnosticsWorkspace');
      if (!host) return;
      const logs = Array.isArray(state.logs) ? state.logs : [];
      const appStatus = state.status || {};
      const categories = [...new Set(logs.map(item => String(item.category || 'OUTROS').toUpperCase()))].sort();
      const filteredLogs = logs.filter(item => {
        const level = String(item.level || '').toUpperCase();
        const category = String(item.category || 'OUTROS').toUpperCase();
        return (this.logLevel === 'ALL' || level === this.logLevel) && (this.logCategory === 'ALL' || category === this.logCategory);
      }).slice(-24).reverse();
      const logPageCount = Math.max(1, Math.ceil(filteredLogs.length / LOG_PAGE_SIZE));
      this.logPage = Math.min(Math.max(0, this.logPage || 0), logPageCount - 1);
      const pageLogs = filteredLogs.slice(this.logPage * LOG_PAGE_SIZE, (this.logPage + 1) * LOG_PAGE_SIZE);
      const serviceHealthy = appStatus.serviceRunning === true && appStatus.engineStuck !== true;
      const battery = this.api.batteryOptimizationStatus?.() || {};
      const overlay = this.api.overlayStatus?.() || {};
      let mode = this.autoRefineMode;
      if (!mode) { try { mode = String(window.OmegasAutoCal?.getAutoRefineMode?.() || 'SHADOW'); } catch (_) { mode = 'SHADOW'; } this.autoRefineMode = mode; }
      // Só redesenha quando algo visível mudou: redesenho a cada ciclo engolia toques.
      const signature = JSON.stringify([
        this.view, this.page, serviceHealthy, appStatus.usbConnected, battery, overlay, mode, this.probeNote, !!window.OmegasAutoCal?.startBufferWriteProbe,
        filteredLogs.map(item => [item.time, item.message]), this.logLevel, this.logCategory, this.logPage,
      ]);
      if (signature === this.toolsSignature && host.childElementCount) return;
      this.toolsSignature = signature;
      host.dataset.mode = this.view === 'home' ? 'home' : 'view';

      if (this.view === 'log') {
        host.innerHTML = `
        <section class="tool-view log-view" data-card="registro">
          <header><button type="button" class="secondary tool-back" data-tool-back><span aria-hidden="true">‹</span> Voltar</button><h3>Registro do app</h3><p>${serviceHealthy ? 'O app está funcionando.' : 'O app precisa de atenção.'}</p></header>
          <div class="log-tools">
            <select data-log-level aria-label="Mostrar">${Object.entries(LEVELS).map(([value, text]) => `<option value="${value}" ${this.logLevel === value ? 'selected' : ''}>${text}</option>`).join('')}</select>
            <select data-log-category aria-label="Assunto"><option value="ALL">Todos os assuntos</option>${categories.map(value => `<option value="${escapeHtml(value)}" ${this.logCategory === value ? 'selected' : ''}>${escapeHtml(value)}</option>`).join('')}</select>
            <button type="button" class="secondary" data-tool-export-logs>Salvar registro</button>
            <button type="button" class="secondary" data-tool-selftest>Testar o app</button>
          </div>
          <div class="log-lines">
            ${pageLogs.length ? pageLogs.map(item => `<div data-level="${escapeHtml(String(item.level || 'INFO').toLowerCase())}"><time>${escapeHtml(item.time || '')}</time><b>${escapeHtml(item.category || 'LOG')}</b><span>${escapeHtml(item.message || '')}</span></div>`).join('') : '<p class="empty-copy">Nenhum aviso por enquanto.</p>'}
          </div>
          ${logPageCount > 1 ? `<div class="tool-log-pager"><button type="button" class="secondary" data-log-page="prev" ${this.logPage === 0 ? 'disabled' : ''}>‹ Mais novos</button><span>${this.logPage + 1} de ${logPageCount}</span><button type="button" class="secondary" data-log-page="next" ${this.logPage >= logPageCount - 1 ? 'disabled' : ''}>Mais antigos ›</button></div>` : ''}
        </section>`;
        return;
      }

      const refineOn = mode !== 'OFF';
      const batteryOk = battery.ignoringOptimizations === true || battery.supported === false;
      const overlayState = overlay.visible === true ? 'on' : 'off';
      const overlayAttr = overlay.visible === true ? 'data-tool-overlay-disable'
        : overlay.permissionGranted === true ? 'data-tool-overlay-enable' : 'data-tool-overlay-request';
      host.innerHTML = `
        <article class="tool-tile" data-card="copia" data-state="neutral">
          <h3>Cópia de segurança</h3>
          <p class="tile-line">Guarda tudo do app num arquivo.</p>
          <button type="button" id="toolExportData" class="tile-action"><b>Salvar cópia</b></button>
        </article>
        <article class="tool-tile" data-card="aprendizado-salvar" data-state="neutral">
          <h3>Aprendizado</h3>
          <p class="tile-line">Guarda o que o app aprendeu.</p>
          <button type="button" id="toolExportLearning" class="tile-action"><b>Salvar aprendizado</b></button>
        </article>
        <article class="tool-tile" data-card="aprendizado-carregar" data-state="neutral">
          <h3>Aprendizado de outro arquivo</h3>
          <p class="tile-line">Confere antes. Não grava na central.</p>
          <button type="button" id="toolImportLearning" class="tile-action"><b>Carregar aprendizado</b></button>
        </article>
        <article class="tool-tile" data-card="refino-automatico" data-state="${refineOn ? 'on' : 'off'}">
          <h3>Refino automático</h3>
          <p class="tile-line">Só sugere. Nunca grava sozinho.</p>
          <button type="button" id="toolAutoRefine" class="tool-switch" role="switch" aria-checked="${refineOn ? 'true' : 'false'}" aria-label="Refino automático"><i class="sw-track" aria-hidden="true"><u></u></i><span class="sw-label">${refineOn ? 'Ligado' : 'Desligado'}</span></button>
        </article>
        <article class="tool-tile" data-card="teste-gravacao" data-state="neutral">
          <h3>Teste de gravação</h3>
          <p class="tile-line">Só com o carro parado.</p>
          <button type="button" id="toolBufferProbe" class="tile-action"><b>Testar</b><span>${escapeHtml(this.probeNote)}</span></button>
        </article>
        <article class="tool-tile" data-card="bateria" data-state="${batteryOk ? 'on' : 'off'}">
          <h3>Bateria</h3>
          <p class="tile-line">${batteryOk ? 'O Android não pausa o app.' : 'O Android pode pausar o app.'}</p>
          ${batteryOk ? '<span class="tile-state">Sem limite</span>' : '<button type="button" class="tile-action" data-tool-battery-request><b>Permitir</b></button>'}
        </article>
        <article class="tool-tile" data-card="balao" data-state="${overlayState}">
          <h3>Balão sobre outros apps</h3>
          <p class="tile-line">Mostra as leituras por cima do mapa.</p>
          <button type="button" class="tool-switch" role="switch" aria-checked="${overlay.visible === true ? 'true' : 'false'}" aria-label="Balão sobre outros apps" ${overlayAttr}><i class="sw-track" aria-hidden="true"><u></u></i><span class="sw-label">${overlay.visible === true ? 'Ligado' : overlay.permissionGranted === true ? 'Desligado' : 'Pedir permissão'}</span></button>
        </article>
        <button type="button" class="tool-tile tool-open" data-card="registro" data-tool-open-log data-state="${serviceHealthy ? 'on' : 'off'}">
          <h3>Registro do app</h3>
          <p class="tile-line">Avisos e teste do app.</p>
          <span class="tile-state">${serviceHealthy ? 'Tudo certo' : 'Atenção'}</span>
          <span class="tile-go">Ver ›</span>
        </button>`;
    }

    // ---------- Sessões ----------
    renderSessions(state) {
      const host = document.getElementById('sessionsHost');
      if (!host) return;
      if (this.preserveSessionSettingsInteraction(host)) return;
      const status = state.sessionStatus || {};
      const settings = status.settings || {};
      const sessions = Array.isArray(state.sessions) ? state.sessions : [];
      const recording = status.recording === true;
      const mb = finite(status.megabytes) || 0;
      const settingsOpenBeforeRender = host.querySelector('.diagnostic-settings')?.open === true;
      const pages = Math.max(1, Math.ceil(sessions.length / SESSIONS_PER_PAGE));
      this.sessionPage = Math.min(Math.max(0, this.sessionPage || 0), pages - 1);
      const shown = sessions.slice(this.sessionPage * SESSIONS_PER_PAGE, (this.sessionPage + 1) * SESSIONS_PER_PAGE);
      const signature = JSON.stringify([
        recording, status.events, Math.round(mb * 10), Math.round((finite(status.durationMs) || 0) / 10000), settings,
        sessions.map(item => [item.id, item.bytes, item.active]), this.sessionPage, this.sessionSettingsFeedback, state.status?.usbConnected,
      ]);
      if (signature === this.sessionsSignature && host.childElementCount) return;
      this.sessionsSignature = signature;
      host.innerHTML = `
        <section class="sess-card sess-rec" data-recording="${recording ? 'true' : 'false'}">
          <h3>Gravação</h3>
          <span class="tile-state" data-on="${recording ? 'true' : 'false'}">${recording ? 'Gravando' : 'Parada'}</span>
          <p class="tile-line">${recording ? `${durationLabel(status.durationMs)} · ${finite(status.megabytes) === null ? '—' : `${fmt(mb, 1)} MB`}` : (settings.autoStartOnUsb !== false ? 'Começa sozinha quando a central conecta.' : 'Toque para começar.')}</p>
          <button type="button" class="${recording ? 'secondary' : 'primary'} sess-main" ${recording ? 'data-session-stop' : 'data-session-start'}><b>${recording ? 'Parar de gravar' : 'Começar a gravar'}</b></button>
        </section>
        <section class="sess-card sess-list">
          <h3>Gravações salvas <span class="count">(${sessions.length})</span></h3>
          ${shown.length ? `<div class="recorded-session-list">${shown.map(item => {
            const match = String(item.id || '').match(/session_(\d{4}-\d{2}-\d{2})_(\d{2}-\d{2}-\d{2})/);
            const readableDate = match ? match[1].split('-').reverse().join('/') + ' ' + match[2].replace(/-/g, ':') : (new Date(item.createdAt || 0).toLocaleString('pt-BR'));
            return `
            <article class="recorded-session-item">
              <div><b>${readableDate}</b><span>${durationLabel(item.durationMs)} · ${bytesLabel(item.bytes)}${item.active ? ' · gravando agora' : ''}</span></div>
              <button type="button" class="secondary" data-export-session="${escapeHtml(item.id)}">Salvar arquivo</button>
            </article>`;
          }).join('')}</div>` : '<p class="empty-copy">Nenhuma gravação ainda. Ela começa sozinha ao conectar a central.</p>'}
          ${pages > 1 ? `<div class="tool-log-pager"><button type="button" class="secondary" data-session-page="newer" ${this.sessionPage === 0 ? 'disabled' : ''}>‹ Mais novas</button><span>${this.sessionPage + 1} de ${pages}</span><button type="button" class="secondary" data-session-page="older" ${this.sessionPage >= pages - 1 ? 'disabled' : ''}>Mais antigas ›</button></div>` : ''}
        </section>
        <section class="sess-card sess-size">
          <details class="diagnostic-settings" ${settingsOpenBeforeRender ? 'open' : ''}>
            <summary><span class="sum-title">Tamanho das gravações</span><span class="sum-state">${finite(settings.maxSessionMb ?? status.limitMb) === null ? '' : `${Math.round(finite(settings.maxSessionMb ?? status.limitMb))} MB cada`}</span><em class="tool-disclosure-back">Voltar</em></summary>
            <div class="tool-disclosure-body">
              <div class="diagnostic-settings-grid">
                <label><span>Salvar a cada</span><select data-session-telemetry>
                  ${[250, 500, 1000, 2000, 5000].map(value => `<option value="${value}" ${Number(settings.telemetryEveryMs) === value ? 'selected' : ''}>${value < 1000 ? `${value / 1000}`.replace('.', ',') + ' s' : `${value / 1000} s`}</option>`).join('')}
                </select></label>
                <label><span>Tamanho máximo (MB)</span><input data-session-maxmb type="number" min="64" max="1024" step="64" value="${Number(settings.maxSessionMb || status.limitMb || 256)}"></label>
                <label><span>Quantas guardar</span><input data-session-keep type="number" min="20" max="100" step="1" value="${Math.max(20, Number(settings.keepSessions || 20))}"></label>
                <label class="check-setting"><input data-session-autostart type="checkbox" ${settings.autoStartOnUsb !== false ? 'checked' : ''}><span>Gravar ao conectar a central</span></label>
                <label class="check-setting"><input data-session-rawusb type="checkbox" ${settings.captureRawUsb === true ? 'checked' : ''}><span>Gravar tudo do cabo (arquivo grande)</span></label>
              </div>
              <button type="button" class="primary" data-session-settings>Aplicar</button>
              ${this.sessionSettingsFeedback ? `<small class="settings-feedback">${escapeHtml(this.sessionSettingsFeedback)}</small>` : ''}
            </div>
          </details>
        </section>`;
    }
  }

  ns.Drawers = Drawers;
})(typeof window !== 'undefined' ? window : globalThis);
