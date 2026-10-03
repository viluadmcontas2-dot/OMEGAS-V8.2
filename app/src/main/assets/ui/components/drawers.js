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
  // Regras únicas de exibição (core/display-rules.js): desconhecido mostra "—", nunca 0.
  const rules = () => root.OmegasUi.DisplayRules;
  function bytesLabel(bytes) { return rules().bytesLabel(bytes); }
  function durationLabel(ms) { return rules().durationLabel(ms); }
  function ageLabel(ms) {
    const label = rules().ageLabel(ms);
    return label === rules().DASH ? 'sem telemetria' : label;
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
      if (target.matches('[data-session-settings]')) {
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
      } else if (target.matches('[data-tool-overlay-scale]')) {
        this.api.setOverlayScale?.(Number(target.dataset.toolOverlayScale));
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
        autoStartOnUsb: true,
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
      const sessionsLoading = !Array.isArray(state.sessions);
      const sessions = Array.isArray(state.sessions) ? state.sessions : [];
      const logs = Array.isArray(state.logs) ? state.logs : [];
      const appStatus = state.status || {};
      const learning = state.learning || {};
      // Aprendizado que ainda não respondeu é desconhecido ("—"), não "0 regiões".
      const petrolCount = Array.isArray(learning.petrol) ? learning.petrol.length : null;
      const cngCount = Array.isArray(learning.cng) ? learning.cng.length : null;
      const comparisonCount = finite(learning.comparisonCount) ?? (Array.isArray(learning.comparisons) ? learning.comparisons.length : null);
      const categories = [...new Set(logs.map(item => String(item.category || 'OUTROS').toUpperCase()))].sort();
      const filteredLogs = logs.filter(item => {
        const level = String(item.level || '').toUpperCase();
        const category = String(item.category || 'OUTROS').toUpperCase();
        return (this.logLevel === 'ALL' || level === this.logLevel) && (this.logCategory === 'ALL' || category === this.logCategory);
      }).slice(-24).reverse();
      const recording = status.recording === true;
      const mb = finite(status.megabytes);
      const limitMb = finite(status.limitMb ?? settings.maxSessionMb) || 0;
      const fullness = limitMb > 0 && mb !== null ? Math.min(100, mb / limitMb * 100) : 0;
      const serviceHealthy = appStatus.serviceRunning === true && appStatus.engineStuck !== true;

      const battery = this.api.batteryOptimizationStatus?.() || {};
      const overlay = this.api.overlayStatus?.() || {};
      // Só redesenha quando algo visível mudou: redesenho a cada tick fechava seletores,
      // resetava a rolagem e engolia toques (a aba parecia travada).
      const signature = JSON.stringify([
        appStatus.serviceRunning, appStatus.engineRunning, appStatus.engineStuck, appStatus.usbConnected,
        Math.round((finite(appStatus.directTelemetryAgeMs) ?? -1) / 1000), battery, overlay,
        status.recording, status.events, mb === null ? null : Math.round(mb * 10), status.droppedEvents, Math.round((finite(status.durationMs) || 0) / 10000),
        settings, sessionsLoading, sessions.map(item => [item.id, item.bytes, item.active]), petrolCount, cngCount, comparisonCount,
        filteredLogs.map(item => [item.time, item.message]), this.logLevel, this.logCategory, this.sessionSettingsFeedback,
      ]);
      if (signature === this.toolsSignature && host.childElementCount) return;
      this.toolsSignature = signature;
      const logsOpenBeforeRender = host.querySelector('.tool-logs')?.open === true;
      const batteryAction = battery.supported !== false && battery.ignoringOptimizations !== true
        ? '<button type="button" class="secondary" data-tool-battery-request>Permitir</button>' : '';
      const overlayInfo = rules().overlayState(overlay);
      const overlayAction = overlayInfo.key === 'on'
        ? '<button type="button" class="quiet-button" data-tool-overlay-disable>Desativar</button>'
        : overlayInfo.key === 'off'
          ? '<button type="button" class="secondary" data-tool-overlay-enable>Ativar</button>'
          : overlayInfo.key === 'needs-permission'
            ? '<button type="button" class="primary" data-tool-overlay-request>Autorizar</button>' : '';
      const overlaySizes = overlayInfo.key === 'on'
        ? `<div class="overlay-size" role="group" aria-label="Tamanho do balão">${[['1', 'Pequeno'], ['1.25', 'Médio'], ['1.6', 'Grande']].map(([value, label]) =>
          `<button type="button" class="${Math.abs((finite(overlay.scale) ?? 1.25) - Number(value)) < 0.05 ? 'secondary' : 'quiet-button'}" data-tool-overlay-scale="${value}">${label}</button>`).join('')}</div>` : '';

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
            <div class="tool-power-row tool-overlay-row" data-overlay-state="${overlayInfo.key}"><div><small>TELEMETRIA FLUTUANTE</small><b>${overlayInfo.title}</b><span>${overlayInfo.help}</span>${overlaySizes}</div>${overlayAction}</div>
          </div>
        </section>

        <section class="diagnostic-recorder-card" data-recording="${recording ? 'true' : 'false'}">
          <header><div><small>SESSÕES</small><h3>${recording ? 'Gravando esta sessão' : 'Começa sozinha ao conectar a ECU'}</h3></div><span>${recording ? 'GRAVANDO' : 'AUTOMÁTICA'}</span></header>
          <div class="recorder-metrics">
            <span><b>${durationLabel(status.durationMs)}</b> duração</span>
            <span><b>${rules().megabytesLabel(mb)}</b> usados</span>
            <span><b>${rules().count(status.events)}</b> eventos</span>
          </div>
          <div class="recorder-space"><i style="width:${fullness.toFixed(1)}%"></i></div>
          <div class="recorded-session-list">
            ${sessions.length ? sessions.slice(0, 8).map(item => {
              const match = String(item.id || '').match(/session_(\d{4}-\d{2}-\d{2})_(\d{2}-\d{2}-\d{2})/);
              const readableDate = rules().sessionDate(item);
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
            }).join('') : sessionsLoading ? '<p class="empty-copy">Lendo as sessões salvas…</p>' : '<p class="empty-copy">Nenhuma sessão gravada ainda. Ela começa sozinha ao conectar a ECU.</p>'}
          </div>
        </section>

        <section class="learning-portability-card">
          <header><div><small>APRENDIZADO</small><h3>O que vai no arquivo .omegas</h3></div></header>
          <div class="learning-portability-grid">
            <span><b>${rules().count(petrolCount)}</b> regiões gasolina</span>
            <span><b>${rules().count(cngCount)}</b> regiões GNV</span>
            <span><b>${rules().count(comparisonCount)}</b> comparações</span>
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
            <label class="check-setting"><input data-session-rawusb type="checkbox" ${settings.captureRawUsb === true ? 'checked' : ''}><span>Capturar USB bruto</span></label>
          </div>
          <p>Cada sessão vira <b>um só arquivo ZIP</b> em <b>Download/Omegas</b>, pronto quando ela termina (ou na próxima abertura do app, se ele fechar no meio). O app guarda as ${Math.max(20, Number(settings.keepSessions || 20))} sessões mais recentes e nunca apaga uma que ainda não foi copiada para essa pasta.</p>
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
