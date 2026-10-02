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
      this.sessionSettingsFeedback = '';
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
      document.getElementById('toolExportData')?.addEventListener('click', () => this.api.exportData());
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
        this.refreshSessionStatus(result);
      } else if (target.matches('[data-session-stop]')) {
        const result = this.api.stopSession('parada manual pela interface');
        this.notifyResult(result, 'Gravação de diagnóstico encerrada.');
        this.refreshSessionStatus(result);
      } else if (target.matches('[data-session-settings]')) {
        this.applySessionSettings();
      } else if (target.matches('[data-export-session]')) {
        this.api.exportSession(target.dataset.exportSession || '');
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
      } else if (target.matches('[data-tool-export-logs]')) {
        this.api.exportLogs();
      } else if (target.matches('[data-tool-selftest]')) {
        const result = this.api.selfTest();
        this.store.patch({ alert: { level: result?.ok ? 'ok' : 'warning', message: result?.ok ? 'Autoteste concluído.' : (result?.error || 'Autoteste não concluído.') } });
      }
    }

    handleToolChange(event) {
      if (event.target.matches('[data-log-level]')) {
        this.logLevel = event.target.value || 'ALL';
        this.toolsSignature = '';
        this.renderTools(this.store.get());
      }
      if (event.target.matches('[data-log-category]')) {
        this.logCategory = event.target.value || 'ALL';
        this.toolsSignature = '';
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

    refreshSessionStatus(result) {
      if (!result || typeof result !== 'object' || result.ok === false) return;
      this.store.patch({ sessionStatus: result });
      this.toolsSignature = '';
      this.renderTools(this.store.get());
    }

    applySessionSettings() {
      const host = document.getElementById('toolDiagnosticsWorkspace');
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
        this.sessionSettingsFeedback = result.error || 'Política não aplicada.';
      } else {
        this.sessionSettingsFeedback = `Aplicado: ${applied.telemetryEveryMs || settings.telemetryEveryMs} ms · ${applied.maxSessionMb || settings.maxSessionMb} MB · ${applied.keepSessions || settings.keepSessions} sessões`;
      }
      this.notifyResult(result, 'Política de logs atualizada.');
      this.refreshSessionStatus(result);
    }

    render(state) {
      if (this.suggestions) this.suggestions.classList.toggle('open', state.suggestionsOpen === true);
      if (this.tools) this.tools.classList.toggle('open', state.toolsOpen === true);
      document.body.classList.toggle('drawer-open', state.suggestionsOpen === true || state.toolsOpen === true);
      // #suggestionList pertence à fila persistente (app.js). Dois donos reescrevendo a
      // mesma lista a cada 2 s faziam o toque sumir e a aba Sugestões parecer travada.
      if (state.toolsOpen) this.renderTools(state);
      const demo = document.getElementById('toolEnvironment');
      if (demo) demo.textContent = state.demo ? 'Simulação de interface · nenhuma escrita real' : 'Backup, sessões e saúde do app';
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

    preserveSessionSettingsInteraction(host) {
      const panel = host?.querySelector('.diagnostic-settings');
      if (!panel) return false;
      const active = document.activeElement;
      return !!(active && panel.contains(active) && ['INPUT', 'SELECT'].includes(active.tagName));
    }

    renderTools(state) {
      const host = document.getElementById('toolDiagnosticsWorkspace');
      if (!host) return;
      if (this.preserveSessionSettingsInteraction(host)) return;
      const settingsOpenBeforeRender = host.querySelector('.diagnostic-settings')?.open === true;
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

      const battery = this.api.batteryOptimizationStatus?.() || {};
      const overlay = this.api.overlayStatus?.() || {};
      // Só redesenha quando algo visível mudou: redesenho a cada tick fechava seletores,
      // resetava a rolagem e engolia toques (a aba parecia travada).
      const signature = JSON.stringify([
        appStatus.serviceRunning, appStatus.engineRunning, appStatus.engineStuck, appStatus.usbConnected,
        Math.round((finite(appStatus.directTelemetryAgeMs) ?? -1) / 1000), battery, overlay,
        status.recording, status.events, Math.round(mb * 10), status.droppedEvents, Math.round((finite(status.durationMs) || 0) / 10000),
        settings, sessions.map(item => [item.id, item.bytes, item.active]), petrolCount, cngCount, comparisonCount,
        filteredLogs.map(item => [item.time, item.message]), this.logLevel, this.logCategory, this.sessionSettingsFeedback,
      ]);
      if (signature === this.toolsSignature && host.childElementCount) return;
      this.toolsSignature = signature;
      const logsOpenBeforeRender = host.querySelector('.tool-logs')?.open === true;
      const batteryAction = battery.supported !== false && battery.ignoringOptimizations !== true
        ? '<button type="button" class="secondary" data-tool-battery-request>Permitir</button>' : '';
      const overlayAction = overlay.visible === true
        ? '<button type="button" class="quiet-button" data-tool-overlay-disable>Desativar</button>'
        : overlay.permissionGranted === true
          ? '<button type="button" class="secondary" data-tool-overlay-enable>Ativar</button>'
          : '<button type="button" class="secondary" data-tool-overlay-request>Autorizar</button>';

      host.innerHTML = `
        <section class="background-health-card" data-healthy="${serviceHealthy ? 'true' : 'false'}">
          <header><div><small>SAÚDE DO APP</small><h3>${serviceHealthy ? 'Funcionando em segundo plano' : appStatus.serviceRunning ? 'Comunicação com a ECU exige atenção' : 'O serviço do OMEGAS não está ativo'}</h3></div><span>${serviceHealthy ? 'OK' : 'ATENÇÃO'}</span></header>
          <div class="background-health-grid">
            <span>ECU <b>${appStatus.usbConnected ? 'conectada' : 'desconectada'}</b></span>
            <span>Leitura <b>${appStatus.engineRunning ? 'ativa' : 'parada'}</b></span>
            <span>Telemetria <b>${ageLabel(appStatus.directTelemetryAgeMs)}</b></span>
          </div>
          <div class="tool-power-rows">
            <div class="tool-power-row"><div><small>BATERIA</small><b>${battery.ignoringOptimizations === true ? 'Sem restrição do Android' : 'O Android pode pausar o app'}</b><span>Permita para sessões longas com a tela apagada.</span></div>${batteryAction}</div>
            <div class="tool-power-row"><div><small>TELEMETRIA FLUTUANTE</small><b>${overlay.visible === true ? 'Ativa' : 'Desativada'}</b><span>Balão por cima de outros apps (mapa, música). Não aparece por cima do OMEGAS.</span></div>${overlayAction}</div>
          </div>
        </section>

        <section class="diagnostic-recorder-card" data-recording="${recording ? 'true' : 'false'}">
          <header><div><small>SESSÕES</small><h3>${recording ? 'Gravando esta sessão' : 'Gravação parada'}</h3></div><span>${recording ? 'GRAVANDO' : 'PARADA'}</span></header>
          <div class="recorder-metrics">
            <span><b>${durationLabel(status.durationMs)}</b> duração</span>
            <span><b>${fmt(mb, 1)} MB</b> usados</span>
            <span><b>${status.events || 0}</b> eventos</span>
          </div>
          <div class="recorder-space"><i style="width:${fullness.toFixed(1)}%"></i></div>
          <div class="recorder-actions">
            <button type="button" class="${recording ? 'quiet-button' : 'primary'}" data-session-start ${recording ? 'disabled' : ''}>Iniciar sessão</button>
            <button type="button" class="${recording ? 'secondary' : 'quiet-button'}" data-session-stop ${recording ? '' : 'disabled'}>Encerrar</button>
          </div>
          <div class="recorded-session-list">
            ${sessions.length ? sessions.slice(0, 8).map(item => {
              const match = String(item.id || '').match(/session_(\d{4}-\d{2}-\d{2})_(\d{2}-\d{2}-\d{2})/);
              const readableDate = match ? match[1].split('-').reverse().join('/') + ' ' + match[2].replace(/-/g, ':') : (new Date(item.createdAt || 0).toLocaleString('pt-BR'));
              const pctCng = (finite(item.cngTicks) || 0);
              const pctPet = (finite(item.petrolTicks) || 0);
              const totalTicks = pctCng + pctPet;
              const gnvPercent = totalTicks > 0 ? Math.round((pctCng / totalTicks) * 100) : 0;
              const gasPercent = totalTicks > 0 ? 100 - gnvPercent : 0;
              return `
              <article class="recorded-session-item">
                <div class="recorded-session-header">
                  <b>${escapeHtml(item.reason || 'Sessão')}</b>
                  <span class="session-datetime">${readableDate}</span>
                </div>
                <div class="recorded-session-meta">
                  <span>${durationLabel(item.durationMs)} · ${bytesLabel(item.bytes)}${item.active ? ' · em andamento' : ''}${totalTicks > 0 ? ` · GNV ${gnvPercent}% · gasolina ${gasPercent}%` : ''}</span>
                </div>
                ${totalTicks > 0 ? `
                <div class="session-fuel-bar" title="GNV: ${gnvPercent}% | Gasolina: ${gasPercent}%">
                  <div class="fuel-segment cng" style="width: ${gnvPercent}%;"></div>
                  <div class="fuel-segment petrol" style="width: ${gasPercent}%;"></div>
                </div>
                ` : ''}
                <button type="button" class="quiet-button" data-export-session="${escapeHtml(item.id)}">Exportar ZIP</button>
              </article>`;
            }).join('') : '<p class="empty-copy">Nenhuma sessão gravada ainda. Ela começa sozinha ao conectar a ECU.</p>'}
          </div>
        </section>

        <section class="learning-portability-card">
          <header><div><small>APRENDIZADO</small><h3>O que vai no arquivo .omegas</h3></div></header>
          <div class="learning-portability-grid">
            <span><b>${petrolCount}</b> regiões gasolina</span>
            <span><b>${cngCount}</b> regiões GNV</span>
            <span><b>${comparisonCount}</b> comparações</span>
          </div>
          <p>Use <b>Exportar aprendizado</b> e <b>Importar aprendizado</b> acima. Importar confere o arquivo antes de aceitar e nunca grava na ECU. O GNV de uma calibração antiga não volta para a calibração atual.</p>
        </section>

        <details class="diagnostic-settings" ${settingsOpenBeforeRender ? 'open' : ''}>
          <summary>Tamanho e retenção das sessões</summary>
          <div class="diagnostic-settings-grid">
            <label><span>Telemetria salva</span><select data-session-telemetry>
              ${[250, 500, 1000, 2000, 5000].map(value => `<option value="${value}" ${Number(settings.telemetryEveryMs) === value ? 'selected' : ''}>${value < 1000 ? `${value} ms` : `${value / 1000} s`}</option>`).join('')}
            </select></label>
            <label><span>Limite por sessão</span><input data-session-maxmb type="number" min="64" max="1024" step="64" value="${Number(settings.maxSessionMb || status.limitMb || 256)}"><small>MB</small></label>
            <label><span>Manter sessões</span><input data-session-keep type="number" min="20" max="100" step="1" value="${Math.max(20, Number(settings.keepSessions || 20))}"></label>
            <label class="check-setting"><input data-session-autostart type="checkbox" ${settings.autoStartOnUsb !== false ? 'checked' : ''}><span>Iniciar ao conectar a ECU</span></label>
            <label class="check-setting"><input data-session-rawusb type="checkbox" ${settings.captureRawUsb === true ? 'checked' : ''}><span>Capturar USB bruto</span></label>
          </div>
          <p>USB bruto aumenta bastante o tamanho. Use só para investigar falha de comunicação.</p>
          <button type="button" class="secondary wide" data-session-settings>Aplicar</button>
          ${this.sessionSettingsFeedback ? `<small class="settings-feedback">${escapeHtml(this.sessionSettingsFeedback)}</small>` : ''}
        </details>

        <details class="tool-logs live-log-console" ${logsOpenBeforeRender ? 'open' : ''}>
          <summary>Detalhes técnicos · log do sistema (${logs.length})</summary>
          <div class="recorder-actions">
            <button type="button" class="secondary" data-tool-export-logs>Exportar logs</button>
            <button type="button" class="secondary" data-tool-selftest>Executar autoteste</button>
          </div>
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
        </details>
      `;
    }
  }

  ns.Drawers = Drawers;
})(typeof window !== 'undefined' ? window : globalThis);
