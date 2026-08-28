(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  function finite(value) { return Number.isFinite(Number(value)) ? Number(value) : null; }
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
    const value = Math.max(0, finite(ms) || 0);
    const minutes = Math.floor(value / 60000);
    const seconds = Math.floor((value % 60000) / 1000);
    if (minutes >= 60) return `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
    return `${minutes}m ${seconds}s`;
  }
  function ageLabel(ms) {
    const value = finite(ms);
    if (value === null || value < 0) return 'sem telemetria';
    if (value < 1000) return `${Math.round(value)} ms`;
    return `${fmt(value / 1000, 1)} s`;
  }

  class Drawers {
    constructor(store, router, api) {
      this.store = store;
      this.router = router;
      this.api = api;
      this.suggestions = document.getElementById('suggestionDrawer');
      this.tools = document.getElementById('toolsDrawer');
      this.logLevel = 'ALL';
      this.logCategory = 'ALL';
      this.ensureToolsExpansion();
      this.bind();
    }

    ensureToolsExpansion() {
      if (!this.tools || document.getElementById('toolDiagnosticsWorkspace')) return;
      const host = document.createElement('div');
      host.id = 'toolDiagnosticsWorkspace';
      host.className = 'tool-diagnostics-workspace';
      this.tools.appendChild(host);
    }

    bind() {
      document.getElementById('suggestionsButton')?.addEventListener('click', () => {
        this.store.patch({ suggestionsOpen: !this.store.get().suggestionsOpen, toolsOpen: false });
      });
      document.getElementById('toolsButton')?.addEventListener('click', () => {
        this.store.patch({ toolsOpen: !this.store.get().toolsOpen, suggestionsOpen: false });
      });
      document.querySelectorAll('[data-close-drawer]').forEach(button => button.addEventListener('click', () => {
        this.store.patch({ suggestionsOpen: false, toolsOpen: false });
      }));
      document.getElementById('toolExportLearning')?.addEventListener('click', () => this.api.exportLearning());
      document.getElementById('toolImportLearning')?.addEventListener('click', () => this.api.importLearning());
      document.getElementById('toolExportLogs')?.addEventListener('click', () => this.api.exportLogs());
      document.getElementById('toolSelfTest')?.addEventListener('click', () => {
        const result = this.api.selfTest();
        this.store.patch({ alert: { level: result?.ok ? 'ok' : 'warning', message: result?.ok ? 'Autoteste concluído.' : (result?.error || 'Autoteste não concluído.') } });
      });
      document.getElementById('toolDiagnosticsWorkspace')?.addEventListener('click', event => this.handleToolClick(event));
      document.getElementById('toolDiagnosticsWorkspace')?.addEventListener('change', event => this.handleToolChange(event));
    }

    handleToolClick(event) {
      const target = event.target.closest('button');
      if (!target) return;
      if (target.matches('[data-session-start]')) {
        const result = this.api.startSession('registro manual pela interface');
        this.notifyResult(result, 'Gravação de diagnóstico iniciada.');
      } else if (target.matches('[data-session-stop]')) {
        const result = this.api.stopSession('parada manual pela interface');
        this.notifyResult(result, 'Gravação de diagnóstico encerrada.');
      } else if (target.matches('[data-session-settings]')) {
        this.applySessionSettings();
      } else if (target.matches('[data-export-session]')) {
        this.api.exportSession(target.dataset.exportSession || '');
      }
    }

    handleToolChange(event) {
      if (event.target.matches('[data-log-level]')) {
        this.logLevel = event.target.value || 'ALL';
        this.renderTools(this.store.get());
      }
      if (event.target.matches('[data-log-category]')) {
        this.logCategory = event.target.value || 'ALL';
        this.renderTools(this.store.get());
      }
    }

    notifyResult(result, successMessage) {
      if (result?.ok === false) {
        this.store.patch({ alert: { level: 'warning', message: result.error || 'Operação não concluída.' } });
      } else {
        this.store.patch({ alert: { level: 'ok', message: successMessage } });
      }
    }

    applySessionSettings() {
      const host = document.getElementById('toolDiagnosticsWorkspace');
      if (!host) return;
      const settings = {
        telemetryEveryMs: Number(host.querySelector('[data-session-telemetry]')?.value || 500),
        maxSessionMb: Number(host.querySelector('[data-session-maxmb]')?.value || 64),
        keepSessions: Number(host.querySelector('[data-session-keep]')?.value || 10),
        autoStartOnUsb: host.querySelector('[data-session-autostart]')?.checked === true,
        captureRawUsb: host.querySelector('[data-session-rawusb]')?.checked === true,
      };
      const result = this.api.setSessionSettings(settings);
      this.notifyResult(result, 'Política de logs atualizada.');
    }

    render(state) {
      if (this.suggestions) this.suggestions.classList.toggle('open', state.suggestionsOpen === true);
      if (this.tools) this.tools.classList.toggle('open', state.toolsOpen === true);
      document.body.classList.toggle('drawer-open', state.suggestionsOpen === true || state.toolsOpen === true);
      this.renderSuggestions(state);
      if (state.toolsOpen) this.renderTools(state);
      const demo = document.getElementById('toolEnvironment');
      if (demo) demo.textContent = state.demo ? 'Simulação de interface · nenhuma escrita real' : 'APK/WebView · ponte nativa ativa';
    }

    renderSuggestions(state) {
      const host = document.getElementById('suggestionList');
      if (!host) return;
      const maps = state.learning || {};
      const model = root.OmegasSuggestionModel;
      const split = model?.split ? model.split(maps.assistedCalibration || maps.assisted_calibration || {}) : { actionable: [], insufficient: [] };
      const items = split.actionable || [];
      const count = document.getElementById('suggestionCount');
      if (count) count.textContent = String(items.length);
      const button = document.getElementById('suggestionsButton');
      if (button) button.classList.toggle('has-items', items.length > 0);
      host.innerHTML = items.length ? items.map((item, index) => `
        <article class="suggestion-item" data-suggestion-index="${index}">
          <div class="suggestion-scope">${item.scope === 'global' ? 'GLOBAL · CURVA K' : 'LOCAL · MAPA K'}</div>
          <div class="suggestion-main"><b>${item.deltaPercent > 0 ? '+' : ''}${fmt(item.deltaPercent, 1)}%</b><span>confiança ${escapeHtml(item.confidenceLabel)}</span></div>
          <p>${escapeHtml(item.reason)}</p>
          <button type="button" class="secondary compact">Revisar em ${escapeHtml(item.destination)}</button>
        </article>`).join('') : '<div class="drawer-empty"><b>Nenhuma sugestão pronta</b><span>O aprendizado continua coletando evidência.</span></div>';
      host.querySelectorAll('[data-suggestion-index]').forEach(card => {
        card.querySelector('button')?.addEventListener('click', () => {
          const item = items[Number(card.dataset.suggestionIndex)];
          const action = model?.reviewAction ? model.reviewAction(item) : { allowed: false };
          if (!action.allowed || action.writesEcu === true) return;
          this.store.patch({ suggestionsOpen: false });
          this.router.navigate(item.type === 'curve' ? 'curve' : 'map', { suggestion: item });
        });
      });
    }

    renderTools(state) {
      const host = document.getElementById('toolDiagnosticsWorkspace');
      if (!host) return;
      const status = state.sessionStatus || {};
      const settings = status.settings || {};
      const sessions = Array.isArray(state.sessions) ? state.sessions : [];
      const logs = Array.isArray(state.logs) ? state.logs : [];
      const appStatus = state.status || {};
      const learning = state.learning || {};
      const petrolCount = Array.isArray(learning.petrol) ? learning.petrol.length : 0;
      const cngCount = Array.isArray(learning.cng) ? learning.cng.length : 0;
      const comparisonCount = finite(learning.comparisonCount) ?? (Array.isArray(learning.comparisons) ? learning.comparisons.length : 0);
      const categories = [...new Set(logs.map(item => String(item.category || 'OUTROS').toUpperCase()))].sort();
      const filteredLogs = logs.filter(item => {
        const level = String(item.level || '').toUpperCase();
        const category = String(item.category || 'OUTROS').toUpperCase();
        return (this.logLevel === 'ALL' || level === this.logLevel) && (this.logCategory === 'ALL' || category === this.logCategory);
      }).slice(-24).reverse();
      const recording = status.recording === true;
      const mb = finite(status.megabytes) || 0;
      const limitMb = finite(status.limitMb ?? settings.maxSessionMb) || 0;
      const fullness = limitMb > 0 ? Math.min(100, mb / limitMb * 100) : 0;
      const serviceHealthy = appStatus.serviceRunning === true && appStatus.engineStuck !== true;

      host.innerHTML = `
        <section class="background-health-card" data-healthy="${serviceHealthy ? 'true' : 'false'}">
          <header><div><small>SEGUNDO PLANO</small><h3>${appStatus.serviceRunning ? 'Serviço Android ativo' : 'Serviço Android não está ativo'}</h3></div><span>${serviceHealthy ? 'MONITORANDO' : 'ATENÇÃO'}</span></header>
          <div class="background-health-grid">
            <span>Engine <b>${appStatus.engineRunning ? 'ativa' : 'parada'}</b></span>
            <span>USB <b>${appStatus.usbConnected ? 'conectado' : 'desconectado'}</b></span>
            <span>Telemetria <b>${ageLabel(appStatus.directTelemetryAgeMs)}</b></span>
            <span>Foreground <b>connectedDevice</b></span>
          </div>
          <p>Ao apagar a tela, a WebView para de redesenhar, mas o ForegroundService continua responsável por USB, OBD e aprendizado. O aplicativo não pede exclusão da otimização de bateria automaticamente.</p>
          <small class="background-validation-note">Validação real ainda exige teste com tela apagada e política de bateria do aparelho.</small>
        </section>

        <section class="learning-portability-card">
          <header><div><small>APRENDIZADO .OMEGAS</small><h3>O que será levado no arquivo</h3></div><span>época ${Number(learning.epoch || 1)}</span></header>
          <div class="learning-portability-grid">
            <span><b>${petrolCount}</b> regiões gasolina</span>
            <span><b>${cngCount}</b> regiões GNV atuais</span>
            <span><b>${comparisonCount}</b> comparações</span>
            <span><b>${escapeHtml(learning.telemetryScaleSchema || 'MP48')}</b> escala</span>
          </div>
          <p><b>Exportar</b> leva aprendizado, contexto OBD e histórico K confirmado. <b>Importar</b> valida formato/escala antes de aceitar qualquer componente.</p>
          <div class="portability-policy"><b>Política live-only</b><span>Gasolina é a referência preservável. Evidência GNV retroativa não volta para a memória ativa da calibração atual.</span></div>
          <small>Os botões Exportar/Importar acima apenas abrem o seletor de arquivo; nenhuma importação escreve na ECU.</small>
        </section>

        <section class="diagnostic-recorder-card" data-recording="${recording ? 'true' : 'false'}">
          <header><div><small>DIAGNÓSTICO ESTRUTURADO</small><h3>${recording ? 'Gravando sessão' : 'Gravador parado'}</h3></div><span>${recording ? 'ATIVO' : 'INATIVO'}</span></header>
          <div class="recorder-metrics">
            <span><b>${status.events || 0}</b> eventos</span>
            <span><b>${fmt(mb, 1)} MB</b> usados</span>
            <span><b>${status.droppedEvents || 0}</b> descartados</span>
            <span><b>${durationLabel(status.durationMs)}</b> duração</span>
          </div>
          <div class="recorder-space"><i style="width:${fullness.toFixed(1)}%"></i></div>
          <div class="recorder-actions">
            <button type="button" class="${recording ? 'quiet-button' : 'primary'}" data-session-start ${recording ? 'disabled' : ''}>Iniciar sessão</button>
            <button type="button" class="${recording ? 'secondary' : 'quiet-button'}" data-session-stop ${recording ? '' : 'disabled'}>Encerrar</button>
          </div>
        </section>

        <details class="diagnostic-settings">
          <summary>Retenção e tamanho dos logs</summary>
          <div class="diagnostic-settings-grid">
            <label><span>Telemetria salva</span><select data-session-telemetry>
              ${[200, 500, 1000, 2000, 5000].map(value => `<option value="${value}" ${Number(settings.telemetryEveryMs) === value ? 'selected' : ''}>${value < 1000 ? `${value} ms` : `${value / 1000} s`}</option>`).join('')}
            </select></label>
            <label><span>Limite por sessão</span><input data-session-maxmb type="number" min="4" max="1024" step="4" value="${Number(settings.maxSessionMb || status.limitMb || 64)}"><small>MB</small></label>
            <label><span>Manter sessões</span><input data-session-keep type="number" min="1" max="100" step="1" value="${Number(settings.keepSessions || 10)}"></label>
            <label class="check-setting"><input data-session-autostart type="checkbox" ${settings.autoStartOnUsb !== false ? 'checked' : ''}><span>Iniciar ao conectar MP48</span></label>
            <label class="check-setting"><input data-session-rawusb type="checkbox" ${settings.captureRawUsb === true ? 'checked' : ''}><span>Capturar USB bruto</span></label>
          </div>
          <p>USB bruto aumenta bastante o tamanho. Use quando estiver investigando protocolo ou falha de comunicação.</p>
          <button type="button" class="secondary wide" data-session-settings>Aplicar política de logs</button>
        </details>

        <section class="recorded-sessions">
          <header><div><small>SESSÕES</small><h3>${sessions.length} armazenada${sessions.length === 1 ? '' : 's'}</h3></div></header>
          <div class="recorded-session-list">
            ${sessions.length ? sessions.slice(0, 8).map(item => `
              <article>
                <div><b>${escapeHtml(item.reason || 'Sessão')}</b><span>${durationLabel(item.durationMs)} · ${bytesLabel(item.bytes)}${item.active ? ' · ativa' : ''}</span></div>
                <button type="button" class="quiet-button" data-export-session="${escapeHtml(item.id)}">Exportar ZIP</button>
              </article>`).join('') : '<p class="empty-copy">Nenhuma sessão gravada.</p>'}
          </div>
        </section>

        <section class="live-log-console">
          <header><div><small>LOG DE SISTEMA</small><h3>Últimos eventos</h3></div><span>${logs.length}</span></header>
          <div class="log-filters">
            <select data-log-level>
              ${['ALL', 'ERROR', 'WARN', 'INFO'].map(value => `<option value="${value}" ${this.logLevel === value ? 'selected' : ''}>${value === 'ALL' ? 'Todos níveis' : value}</option>`).join('')}
            </select>
            <select data-log-category>
              <option value="ALL">Todas categorias</option>
              ${categories.map(value => `<option value="${escapeHtml(value)}" ${this.logCategory === value ? 'selected' : ''}>${escapeHtml(value)}</option>`).join('')}
            </select>
          </div>
          <div class="log-lines">
            ${filteredLogs.length ? filteredLogs.map(item => `<div data-level="${escapeHtml(String(item.level || 'INFO').toLowerCase())}"><time>${escapeHtml(item.time || '')}</time><b>${escapeHtml(item.category || 'LOG')}</b><span>${escapeHtml(item.message || '')}</span></div>`).join('') : '<p class="empty-copy">Nenhum evento neste filtro.</p>'}
          </div>
        </section>
      `;
    }
  }

  ns.Drawers = Drawers;
})(typeof window !== 'undefined' ? window : globalThis);
