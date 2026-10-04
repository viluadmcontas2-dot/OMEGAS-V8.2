(function (root) {
  'use strict';
  // Configuração que chega da ponte pode vir vazia ou não numérica: nunca vira NaN/Infinity na tela.
  const settingNumber = (value, fallback) => { const n = Number(value); return value !== '' && value !== null && value !== undefined && Number.isFinite(n) ? n : fallback; };
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Regras únicas de exibição (core/display-rules.js): desconhecido mostra "—", nunca 0.
  const rules = () => root.OmegasUi.DisplayRules;
  const { finite, fmt, escapeHtml } = root.OmegasUi.DisplayRules;
  function ageLabel(ms) {
    const label = rules().ageLabel(ms);
    return label === rules().DASH ? 'sem telemetria' : label;
  }

  class Drawers {
    constructor(store, router, api) {
      this.store = store;
      this.router = router;
      this.api = api;
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
      document.getElementById('toolExportData')?.addEventListener('click', () => this.api.exportData());
      // Exportar logs e autoteste são os botões data-tool-export-logs / data-tool-selftest, tratados em handleToolClick.
      document.getElementById('toolDiagnosticsWorkspace')?.addEventListener('click', event => this.handleToolClick(event));
      document.getElementById('toolDiagnosticsWorkspace')?.addEventListener('change', event => this.handleToolChange(event));
    }

    handleToolClick(event) {
      const target = event.target.closest('button');
      if (!target) return;
      if (target.matches('[data-session-settings]')) {
        this.applySessionSettings();
      } else if (target.matches('[data-tool-battery-request]')) {
        this.api.requestBatteryOptimizationExemption?.();
        this.toolsSignature = '';
      } else if (target.matches('[data-tool-overlay-request]')) {
        this.overlayReply = this.api.requestOverlayPermissionAndEnable?.() || null;
        this.toolsSignature = '';
        this.renderTools(this.store.get());
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
        telemetryEveryMs: settingNumber(host.querySelector('[data-session-telemetry]')?.value, 250),
        maxSessionMb: settingNumber(host.querySelector('[data-session-maxmb]')?.value, 256),
        keepSessions: Math.max(20, settingNumber(host.querySelector('[data-session-keep]')?.value, 20)),
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
      this.renderTools(state);
      const demo = document.getElementById('toolEnvironment');
      if (demo) demo.textContent = state.demo ? 'Simulação de interface · nenhuma escrita real' : '';
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
      const logs = Array.isArray(state.logs) ? state.logs : [];
      const appStatus = state.status || {};
      const categories = [...new Set(logs.map(item => String(item.category || 'OUTROS').toUpperCase()))].sort();
      const filteredLogs = logs.filter(item => {
        const level = String(item.level || '').toUpperCase();
        const category = String(item.category || 'OUTROS').toUpperCase();
        return (this.logLevel === 'ALL' || level === this.logLevel) && (this.logCategory === 'ALL' || category === this.logCategory);
      }).slice(-24).reverse();
      const serviceHealthy = appStatus.serviceRunning === true && appStatus.engineStuck !== true;

      const battery = this.api.batteryOptimizationStatus?.() || {};
      const overlay = this.api.overlayStatus?.() || {};
      const pilotReply = ns.AutoCalApi?.refinementPhase?.() || {};
      const pilot = pilotReply.autopilot || {};
      const reply = this.overlayReply || {};
      // Só redesenha quando algo visível mudou: redesenho a cada tick fechava seletores,
      // resetava a rolagem e engolia toques (a aba parecia travada).
      const signature = JSON.stringify([
        appStatus.serviceRunning, appStatus.engineRunning, appStatus.engineStuck, appStatus.usbConnected,
        Math.round((finite(appStatus.directTelemetryAgeMs) ?? -1) / 1000), battery, overlay, pilot, this.overlayReply,
        settings,
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
          <header><div><small>SAÚDE DO APP</small><h3>${serviceHealthy ? 'Funcionando em segundo plano' : appStatus.serviceRunning ? 'Comunicação com a ECU exige atenção' : 'O serviço do OMEGAS não está ativo'}</h3></div><span class="health-badge" data-healthy="${serviceHealthy ? 'true' : 'false'}">${serviceHealthy ? 'Tudo certo' : 'Atenção'}</span></header>
          <div class="background-health-grid">
            <span>ECU <b>${appStatus.usbConnected ? 'conectada' : 'desconectada'}</b></span>
            <span>Leitura <b>${appStatus.engineRunning ? 'ativa' : 'parada'}</b></span>
            <span>Último dado <b>${appStatus.usbConnected === true ? rules().ageSinceMs(appStatus.directTelemetryAgeMs) : 'sem dados'}</b></span>
          </div>
          <div class="tool-power-rows">
            <div class="tool-power-row"><div><small>BATERIA</small><b>${battery.ignoringOptimizations === true ? 'Sem restrição do Android' : 'O Android pode pausar o app'}</b><span>Permita para sessões longas com a tela apagada.</span></div>${batteryAction}</div>
            <div class="tool-power-row tool-overlay-row" data-overlay-state="${overlayInfo.key}"><div><small>TELEMETRIA FLUTUANTE</small><b>${overlayInfo.title}</b><span>${overlayInfo.help}</span>${overlaySizes}</div>${overlayAction}</div>
          </div>
        </section>

        <details class="diagnostic-settings" ${settingsOpenBeforeRender ? 'open' : ''}>
          <summary>Retenção das sessões</summary>
          <div class="diagnostic-settings-grid">
            <label><span>Gravar a cada</span><select data-session-telemetry>
              ${[...new Set([250, 500, 1000, 2000, 5000, ...(Number.isFinite(Number(settings.telemetryEveryMs)) && Number(settings.telemetryEveryMs) > 0 ? [Number(settings.telemetryEveryMs)] : [])])].sort((a, b) => a - b).map(value => `<option value="${value}" ${Number(settings.telemetryEveryMs) === value ? 'selected' : ''}>${value < 1000 ? `${value} ms` : `${value / 1000} s`}</option>`).join('')}
            </select></label>
            <label><span>Limite por sessão</span><input data-session-maxmb type="number" min="64" max="1024" step="64" value="${settingNumber(settings.maxSessionMb || status.limitMb, 256)}"><small>MB</small></label>
            <label><span>Manter sessões</span><input data-session-keep type="number" min="20" max="100" step="1" value="${Math.max(20, settingNumber(settings.keepSessions, 20))}"></label>
            <label class="check-setting"><input data-session-rawusb type="checkbox" ${settings.captureRawUsb === true ? 'checked' : ''}><span>Capturar USB bruto</span></label>
          </div>
          <p>Cada sessão vira <b>um só arquivo ZIP</b> em <b>Download/Omegas</b>, pronto quando ela termina (ou na próxima abertura do app, se ele fechar no meio). O app guarda as ${Math.max(20, settingNumber(settings.keepSessions, 20))} sessões mais recentes e nunca apaga uma que ainda não foi copiada para essa pasta.</p>
          <p>USB bruto aumenta bastante o tamanho. Use só para investigar falha de comunicação.</p>
          <button type="button" class="secondary wide" data-session-settings>Aplicar</button>
          ${this.sessionSettingsFeedback ? `<small class="settings-feedback">${escapeHtml(this.sessionSettingsFeedback)}</small>` : ''}
        </details>

        <details class="tool-logs live-log-console" ${logsOpenBeforeRender ? 'open' : ''}>
          <summary>Detalhes técnicos (${logs.length} eventos do sistema)</summary>
          <dl class="tool-tech-rows" id="toolTechRows">
            <div><dt>Pode desconectar (Refino)</dt><dd>${pilot.canDisconnect === true ? 'sim' : pilot.canDisconnect === false ? 'ainda não' : '—'}</dd></div>
            <div><dt>Prazo da tentativa</dt><dd>${pilot.watchdogExpired === true ? 'vencido' : pilot.watchdogExpired === false ? 'dentro do prazo' : '—'}${pilot.timeoutReason ? ' · ' + escapeHtml(String(pilot.timeoutReason)) : ''}</dd></div>
            <div><dt>Último pedido do balão</dt><dd>${this.overlayReply ? (reply.permissionRequired === true ? 'precisa de autorização' : reply.launched === true ? 'tela de autorização aberta' : reply.ok === false ? 'não foi possível abrir a autorização' : 'enviado') : '—'}</dd></div>
          </dl>
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
