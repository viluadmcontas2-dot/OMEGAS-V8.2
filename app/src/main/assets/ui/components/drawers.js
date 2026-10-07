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
      // Exportar logs e autoteste são os botões data-tool-export-logs / data-tool-selftest, tratados em handleToolClick.
      document.getElementById('toolDiagnosticsWorkspace')?.addEventListener('click', event => this.handleToolClick(event));
      document.getElementById('toolDiagnosticsWorkspace')?.addEventListener('change', event => this.handleToolChange(event));
    }

    handleToolClick(event) {
      const target = event.target.closest('button');
      if (!target) return;
      if (target.matches('[data-tool-export-data]')) {
        this.api.exportData();
      } else if (target.matches('[data-session-settings]')) {
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
      const healthOpenBeforeRender = host.querySelector('.ts-health-inspect')?.open === true;
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
      // Só redesenha quando algo visível mudou: redesenho a cada tick fechava seletores,
      // resetava a rolagem e engolia toques (a aba parecia travada).
      const signature = JSON.stringify([
        // A idade do último dado NÃO entra: mudava a cada 2 s, refazia a tela e a rolagem voltava ao topo.
        // Ela é atualizada no lugar (data-tool-usb-age) logo abaixo.
        appStatus.serviceRunning, appStatus.engineRunning, appStatus.engineStuck, appStatus.usbConnected,
        battery, overlay, this.overlayReply, state.identity,
        settings,
        filteredLogs.map(item => [item.time, item.message]), this.logLevel, this.logCategory, this.sessionSettingsFeedback,
      ]);
      const usbAge = appStatus.usbConnected === true ? `Último dado ${rules().ageSinceMs(appStatus.directTelemetryAgeMs)}` : 'Nenhum dado chegando.';
      if (signature === this.toolsSignature && host.childElementCount) {
        const ageNode = host.querySelector('[data-tool-usb-age]');
        if (ageNode && ageNode.textContent !== usbAge) ageNode.textContent = usbAge;
        return;
      }
      this.toolsSignature = signature;
      // Redesenho de verdade (algo mudou): a rolagem da tela fica onde o dono deixou.
      const scroller = this.tools;
      const scrollTop = scroller ? scroller.scrollTop : 0;
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
      const sizeButtons = [['1', 'Pequeno'], ['1.25', 'Médio'], ['1.6', 'Grande']].map(([value, label]) =>
        `<button type="button" class="${Math.abs((finite(overlay.scale) ?? 1.25) - Number(value)) < 0.05 ? 'is-on' : ''}" data-tool-overlay-scale="${value}">${label}</button>`).join('');
      const overlaySizes = overlayInfo.key === 'on'
        ? `<div class="ts-sizes segmented" role="group" aria-label="Tamanho do balão"><small>Tamanho</small>${sizeButtons}</div>` : '';

      const reply = this.overlayReply || {};
      const overlayReplyLine = !this.overlayReply ? '' : reply.permissionRequired === true ? 'Falta autorizar: marque o OMEGAS na tela do Android e volte.' : reply.launched === true ? 'A tela de autorização do Android foi aberta.' : reply.ok === false ? 'Não consegui abrir a autorização. Toque em Autorizar de novo.' : 'Pedido enviado.';
      const identity = state.identity || {};
      const chip = (tone, text) => `<span class="ts-chip" data-tone="${tone}">${text}</span>`;
      const usb = appStatus.usbConnected === true;
      const tiles = [
        ['ECU', usb ? (appStatus.engineStuck === true ? 'Sem resposta' : 'Conectada') : 'Desconectada', usb ? (appStatus.engineStuck === true ? 'warn' : 'ok') : 'bad', usb ? 'Falando com o módulo.' : 'Ligue o cabo USB na ECU.'],
        ['CABO USB', usb ? 'Ligado' : 'Sem cabo', usb ? 'ok' : 'bad', usbAge, 'data-tool-usb-age'],
        ['SERVIÇO', appStatus.serviceRunning === true ? 'Ativo' : 'Parado', appStatus.serviceRunning === true ? 'ok' : 'bad', appStatus.serviceRunning === true ? 'Roda com a tela apagada.' : 'Abra o OMEGAS de novo.'],
        ['LEITURA', appStatus.engineRunning === true ? 'Ativa' : 'Parada', appStatus.engineRunning === true ? 'ok' : 'warn', appStatus.engineRunning === true ? 'O app observa sozinho.' : 'Começa ao conectar a ECU.'],
      ];
      const batteryFree = battery.ignoringOptimizations === true;
      const keep = Math.max(20, settingNumber(settings.keepSessions, 20));
      const telemetryOptions = [...new Set([250, 500, 1000, 2000, 5000, ...(Number.isFinite(Number(settings.telemetryEveryMs)) && Number(settings.telemetryEveryMs) > 0 ? [Number(settings.telemetryEveryMs)] : [])])].sort((a, b) => a - b);
      const versionRows = [
        ['Produto', [identity.product || 'OMEGAS', identity.generation].filter(Boolean).join(' ')],
        ['Versão', identity.versionName || '—'],
      ];

      host.innerHTML = `
        <div class="ts-grid">
        <section class="ts-card" data-overlay-state="${overlayInfo.key}" aria-label="Telemetria flutuante">
          <header class="ts-head"><div><small>TELEMETRIA FLUTUANTE</small><h3>${overlayInfo.title}</h3></div>${chip(overlayInfo.key === 'on' ? 'ok' : overlayInfo.key === 'needs-permission' ? 'warn' : 'neutral', overlayInfo.key === 'on' ? 'Ligada' : overlayInfo.key === 'off' ? 'Desligada' : overlayInfo.key === 'unsupported' ? 'Indisponível' : 'Autorizar')}</header>
          <p class="ts-help">${overlayInfo.help}</p>
          ${overlayReplyLine ? `<p class="ts-help ts-reply">${overlayReplyLine}</p>` : ''}
          ${overlaySizes || ''}
          ${overlayAction ? `<div class="ts-actions">${overlayAction}</div>` : ''}
        </section>

        <section class="ts-card" aria-label="Backup">
          <header class="ts-head"><div><small>BACKUP</small><h3>Guardar tudo do app</h3></div></header>
          <p class="ts-help">Calibrações salvas, fotos da curva e sessões em um só arquivo. Exportar nunca altera a ECU.</p>
          <div class="ts-actions"><button id="toolExportData" type="button" class="primary" data-tool-export-data>Exportar backup completo</button></div>
        </section>

        <section class="ts-card ts-wide" data-healthy="${serviceHealthy ? 'true' : 'false'}" aria-label="Saúde do sistema">
          <header class="ts-head"><div><small>SAÚDE DO SISTEMA</small><h3>${serviceHealthy ? 'Tudo funcionando' : appStatus.serviceRunning ? 'A comunicação com a ECU pede atenção' : 'O serviço do OMEGAS não está ativo'}</h3></div>${chip(serviceHealthy ? 'ok' : 'warn', serviceHealthy ? 'Tudo certo' : 'Atenção')}</header>
          <details class="ts-health-inspect" ${healthOpenBeforeRender ? 'open' : ''}><summary>Ver conexões</summary><div class="ts-tiles">${tiles.map(([label, value, tone, hint, hook]) => `<div class="ts-tile" data-tone="${tone}"><small>${label}</small><b>${value}</b><span${hook ? ' ' + hook : ''}>${hint}</span></div>`).join('')}</div></details>
          <div class="ts-row" data-state="${batteryFree ? 'ok' : 'warn'}"><div><small>SEGUNDO PLANO</small><b>${batteryFree ? 'O Android não pausa o app' : 'O Android pode pausar o app'}</b><span>${batteryFree ? 'Sessões longas com a tela apagada seguem gravando.' : 'Permita para gravar sessões longas com a tela apagada.'}</span></div>${batteryAction}</div>
        </section>

        <details class="ts-card diagnostic-settings" ${settingsOpenBeforeRender ? 'open' : ''} aria-label="Retenção das sessões">
          <summary><span><small>SESSÕES</small><b>Retenção: guarda as ${keep} mais recentes</b></span><em>Ajustar</em></summary>
          <div class="ts-fields">
            <label><span>Gravar a cada</span><select data-session-telemetry>${telemetryOptions.map(value => `<option value="${value}" ${Number(settings.telemetryEveryMs) === value ? 'selected' : ''}>${value < 1000 ? `${value} ms` : `${value / 1000} s`}</option>`).join('')}</select></label>
            <details class="ts-advanced"><summary>Avançado</summary>
            <label><span>Limite por sessão (MB)</span><input data-session-maxmb type="number" min="64" max="1024" step="64" value="${settingNumber(settings.maxSessionMb || status.limitMb, 256)}"></label>
            <label><span>Manter sessões</span><input data-session-keep type="number" min="20" max="100" step="1" value="${keep}"></label>
            <label class="ts-check"><input data-session-rawusb type="checkbox" ${settings.captureRawUsb === true ? 'checked' : ''}><span>Capturar USB bruto <small>só para investigar falha de cabo; deixa o arquivo bem maior</small></span></label>
            </details>
          </div>
          <p class="ts-help">Cada sessão vira um ZIP em <b>Download/Omegas</b> quando termina. Nenhuma é apagada antes de ser copiada para lá.</p>
          <div class="ts-actions"><button type="button" class="secondary" data-session-settings>Aplicar</button>${this.sessionSettingsFeedback ? `<small class="settings-feedback">${escapeHtml(this.sessionSettingsFeedback)}</small>` : ''}</div>
        </details>

        <details class="ts-card ts-version" aria-label="Versão e identidade">
          <summary><span><small>VERSÃO</small><b>Este é o OMEGAS que você instalou</b></span><em>Abrir</em></summary>
          <dl class="ts-kv">${versionRows.map(([k, v]) => `<div><dt>${k}</dt><dd>${escapeHtml(String(v))}</dd></div>`).join('')}</dl>
        </details>

        <details class="ts-card tool-logs live-log-console ts-wide" ${logsOpenBeforeRender ? 'open' : ''}>
          <summary><span><b>Detalhes técnicos: registro do sistema (${logs.length} eventos)</b></span><em>Abrir</em></summary>
          <div class="ts-actions">
            <button type="button" class="secondary" data-tool-export-logs>Exportar registro</button>
            <button type="button" class="secondary" data-tool-selftest>Executar autoteste</button>
            <select data-log-level>${['ALL', 'ERROR', 'WARN', 'INFO'].map(value => `<option value="${value}" ${this.logLevel === value ? 'selected' : ''}>${value === 'ALL' ? 'Todos os níveis' : value}</option>`).join('')}</select>
            <select data-log-category><option value="ALL">Todas as categorias</option>${categories.map(value => `<option value="${escapeHtml(value)}" ${this.logCategory === value ? 'selected' : ''}>${escapeHtml(value)}</option>`).join('')}</select>
          </div>
          <div class="log-lines">${filteredLogs.length ? filteredLogs.map(item => `<div data-level="${escapeHtml(String(item.level || 'INFO').toLowerCase())}"><time>${escapeHtml(item.time || '')}</time><b>${escapeHtml(item.category || 'LOG')}</b><span>${escapeHtml(item.message || '')}</span></div>`).join('') : '<p class="empty-copy">Nenhum evento neste filtro.</p>'}</div>
        </details>
        </div>
      `;
      if (scroller && scroller.scrollTop !== scrollTop) scroller.scrollTop = scrollTop;
    }
  }

  ns.Drawers = Drawers;
})(typeof window !== 'undefined' ? window : globalThis);
