(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Ferramentas > Avançado (dono, 2026-10-18). Mesmo visual dos cartões de Ferramentas. Anti-apagão no topo; bits e códigos em "Ver detalhes".
  // Sem relógio: a tela relê o que a ponte já tem a cada ciclo de dados; a gravação só acontece no toque duplo do dono.
  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  }
  const DETAIL_TABS = [['FLAG_CONF1', 'Ajustes 1'], ['FLAG_CONF2', 'Ajustes 2'], ['FLAG_CONF3', 'Ajustes 3'], ['PARAMS', 'Números']];
  const WHY_DISABLED = {
    nobridge: 'Indisponível neste ambiente (simulação). Só funciona no app com a ECU.',
    nousb: 'Conecte a ECU para ligar ou desligar.',
    unread: 'Esperando a leitura da ECU (—). O botão libera quando o valor chegar.',
  };

  class AdvancedPanel {
    constructor(store, api) {
      this.store = store;
      this.api = api;
      this.A = ns.AdvancedFlags;
      this.screen = document.getElementById('toolsDrawer');
      this.host = document.getElementById('toolAdvanced');
      this.toggle = document.getElementById('toolAdvancedToggle');
      this.open = false;
      this.detail = null;      // null = tela principal; 'FLAG_CONF1'..'PARAMS' = Ver detalhes
      this.data = { read: false, params: {} };
      this.signature = '';
      if (!this.host || !this.toggle || !this.A) return;
      this.flow = this.A.createWriteFlow({
        bridge: new Proxy({}, { get: (_, name) => (root.OmegasAutoCal ? root.OmegasAutoCal[name] : undefined) }), // ponte lida na hora do uso
        addHook: (name, fn) => root.OmegasApp?.scheduler?.addHook?.(name, fn) || (() => {}),
        onChange: () => { this.loadData(); this.render(); },
      });
      this.toggle.addEventListener('click', () => this.setOpen(!this.open));
      this.host.addEventListener('click', event => this.onClick(event));
      // Qualquer toque fora dos botões de gravar/desfazer desarma a prévia (evento, nunca prazo).
      document.addEventListener('click', event => {
        const inside = this.open && this.host.contains(event.target);
        const second = inside && (event.target.closest('[data-adv-confirm]') || (this.flow.view().confirmOn === 'undo' && event.target.closest('[data-adv-undo]')));
        if (!second) this.flow.otherTouch();
      }, true);
    }

    setOpen(open) {
      this.open = open === true;
      if (!this.open) { this.flow.cancel(); this.detail = null; }
      this.screen?.classList.toggle('advanced-open', this.open);
      this.toggle.textContent = this.open ? 'Voltar às ferramentas' : 'Avançado';
      const title = document.getElementById('toolsTitle');
      if (title) title.textContent = this.open ? 'Anti-apagão e ajustes lidos da ECU' : 'Backup, sessões e saúde do app';
      if (this.open) { this.flow.adopt(); this.refresh(); }
      this.signature = '';
      this.render();
    }

    /** Chamado pelo ciclo de dados da aba Ferramentas (app.js). */
    refresh() {
      if (!this.open || !this.flow) return;
      this.loadData();
      const state = this.store.get();
      this.flow.observe({ route: state.route, usbConnected: state.status?.usbConnected === true ? true : state.status?.usbConnected === false ? false : undefined });
      this.render();
    }

    /** Lê da ponte o que a ECU já respondeu (sem pedir nada à ECU). */
    loadData() {
      const bridge = root.OmegasAutoCal;
      let data = { read: false, params: {} };
      if (bridge && typeof bridge.getAdvancedParams === 'function') {
        try { const parsed = JSON.parse(bridge.getAdvancedParams()); if (parsed && parsed.ok !== false) data = parsed; } catch (_) { /* fica "não lido" */ }
      }
      this.data = data;
    }

    usb() { return this.store.get().status?.usbConnected === true; }
    param(name) { return (this.data.params || {})[name]; }

    onClick(event) {
      const button = event.target.closest('button');
      if (!button) return;
      if (button.matches('[data-adv-confirm]')) this.flow.tapSwitch(this.param('FLAG_CONF3'), this.usb());
      else if (button.matches('[data-adv-undo]')) this.flow.tapUndo();
      else if (button.matches('[data-adv-cancel]')) this.flow.cancel();
      else if (button.matches('[data-adv-details]')) { this.detail = button.dataset.advDetails || 'FLAG_CONF3'; this.signature = ''; this.render(); }
      else if (button.matches('[data-adv-tab]')) { this.detail = button.dataset.advTab; this.signature = ''; this.render(); }
      else if (button.matches('[data-adv-back]')) { this.detail = null; this.signature = ''; this.render(); }
    }

    disabledReason(entry) {
      if (!this.flow.canTap(entry, this.usb())) {
        const bridge = root.OmegasAutoCal;
        if (!bridge || typeof bridge.prepareAntiStalloFlag !== 'function') return WHY_DISABLED.nobridge;
        if (!this.usb()) return WHY_DISABLED.nousb;
        if (!this.A.wordsOf(entry)) return WHY_DISABLED.unread;
      }
      return '';
    }

    render() {
      if (!this.open || !this.host) return;
      const view = this.flow.view();
      const entry = this.param('FLAG_CONF3');
      const signature = JSON.stringify([view, this.data, this.detail, this.usb(), this.flow.buttonLabel(entry)]);
      if (signature === this.signature && this.host.childElementCount) return;
      this.signature = signature;
      this.host.dataset.mode = this.detail ? 'detail' : 'main';
      this.host.innerHTML = this.detail ? this.renderDetail() : this.renderMain(view, entry);
    }

    renderMain(view, entry) {
      const A = this.A;
      const words = A.wordsOf(entry);
      const on = words ? (words[0] & A.ANTI_MASK) !== 0 : null;
      const stateText = on === null ? '—' : on ? 'Ligado' : 'Desligado';
      const busy = view.phase === 'preparing' || view.phase === 'writing';
      const reason = this.disabledReason(entry);
      const canTap = !reason && !busy;
      const confirming = view.confirmOn === 'switch';
      const undoConfirming = view.confirmOn === 'undo';
      const target = on === null ? '' : on ? 'Desligado' : 'Ligado';
      const preview = view.preview ? `
        <div class="adv-preview" data-testid="adv-preview">
          <div class="adv-change"><span>${escapeHtml(on === null ? '—' : stateText)}</span><i aria-hidden="true">→</i><b>${escapeHtml(undoConfirming ? (on ? 'Desligado' : 'Ligado') : target)}</b></div>
          <p>${escapeHtml(view.preview.text)}</p>
        </div>` : '';
      const level = view.phase === 'done' ? 'ok' : view.phase === 'error' ? 'warning' : '';
      const message = view.message ? `<p class="adv-result" data-level="${level || 'info'}">${escapeHtml(view.message)}</p>` : '';
      const hint = reason && !view.message ? `<p class="adv-hint">${escapeHtml(reason)}</p>` : '';
      const fields = A.antiStallo(this.param('ANTI_STALLO'));
      const simple = A.simpleParams(this.data.params);
      return `
        <section class="adv-card adv-main" data-state="${on === null ? 'unknown' : on ? 'on' : 'off'}">
          <header><div><small>ANTI-APAGÃO</small><h3>${escapeHtml(stateText)}</h3></div><span>${on === null ? 'NÃO LIDO' : 'LIDO DA ECU'}</span></header>
          <p class="adv-what">Evita o motor apagar quando você tira o pé.</p>
          ${preview}${message}${hint}
          <div class="adv-actions">
            <button type="button" class="primary" data-adv-confirm ${canTap || confirming ? '' : 'disabled'}>${escapeHtml(this.flow.buttonLabel(entry))}</button>
            ${confirming ? '<button type="button" class="quiet-button" data-adv-cancel>Cancelar</button>' : ''}
            <button type="button" class="secondary" data-adv-undo ${(view.undo.available && !busy && this.usb()) || undoConfirming ? '' : 'disabled'}>${undoConfirming ? 'Toque de novo para desfazer' : 'Desfazer'}</button>
            ${undoConfirming ? '<button type="button" class="quiet-button" data-adv-cancel>Cancelar</button>' : ''}
          </div>
        </section>
        <section class="adv-card adv-fields">
          <header><div><small>AJUSTES DO ANTI-APAGÃO</small><h3>Quando ele entra e sai</h3></div></header>
          <ul class="adv-list">${fields.fields.map(f => `
            <li><div><b>${escapeHtml(f.label)}</b><span>${escapeHtml(f.help)}</span></div><em>${escapeHtml(f.valueText)}${f.valueText === '—' ? '' : ` <small>${escapeHtml(f.unit)}</small>`}</em></li>`).join('')}
          </ul>
          <p class="adv-note">Nomes de cada campo: inferidos da tela do programa original; a ECU não os rotula.</p>
        </section>
        <section class="adv-card adv-others">
          <header><div><small>OUTROS AJUSTES LIDOS</small><h3>Só para consulta</h3></div></header>
          <ul class="adv-list">${simple.map(p => `
            <li><div><b>${escapeHtml(p.label)}</b><span>${escapeHtml(p.help)}</span></div><em>${p.cells.map(escapeHtml).join(' · ')}</em></li>`).join('')}
          </ul>
          <button type="button" class="secondary adv-more" data-adv-details="FLAG_CONF3">Ver detalhes<span>bits, códigos e números lidos</span></button>
        </section>`;
    }

    renderDetail() {
      const A = this.A;
      const tabs = DETAIL_TABS.map(([key, label]) => `<button type="button" class="${this.detail === key ? 'primary' : 'secondary'}" data-adv-tab="${key}">${label}</button>`).join('');
      let body;
      if (this.detail === 'PARAMS') {
        const prog = A.progressioni(this.data.params);
        const anti = A.antiStallo(this.param('ANTI_STALLO'));
        body = `
          <div class="adv-params">
            <div><b>Progressões</b><small>código 308 · 4 linhas de 3 números · o que cada coluna significa é desconhecido</small>
              <table><tbody>${prog.rows.map(r => `<tr><th>linha ${r.index + 1}</th>${r.cells.map(c => `<td>${escapeHtml(c)}</td>`).join('')}</tr>`).join('')}</tbody></table></div>
            <div><b>Anti-apagão (código 307)</b><small>5 números de 16 bits, na ordem lida</small>
              <p class="adv-raw">${anti.fields.map(f => escapeHtml(f.valueText)).join(' · ')}</p>
              <small>Rotação para gasolina (código 161) e injeções no corte (código 106) estão na tela anterior.</small></div>
          </div>`;
      } else {
        const v = A.decodeFlags(this.detail, this.param(this.detail));
        body = `
          <p class="adv-caption"><b>${escapeHtml(v.name)}</b> · código ${v.code} · palavra 0: ${escapeHtml(v.hex[0])} · palavra 1: ${escapeHtml(v.hex[1])}</p>
          <ul class="adv-bits">${v.rows.map(r => `
            <li data-state="${r.state}"><span>${escapeHtml(r.label)}<small>palavra ${r.word} · bit 0x${r.mask.toString(16).toUpperCase().padStart(4, '0')}</small></span><b>${escapeHtml(r.stateText)}</b></li>`).join('')}
          </ul>
          <p class="adv-note">${v.unknownBits} dos 32 bits não têm função conhecida: aparecem como desconhecido (—), nunca como desligado.</p>`;
      }
      return `
        <section class="adv-card adv-detail">
          <header><button type="button" class="secondary adv-back" data-adv-back>Voltar</button><div class="adv-tabs">${tabs}</div></header>
          ${body}
        </section>`;
    }
  }

  ns.AdvancedPanel = AdvancedPanel;
})(typeof window !== 'undefined' ? window : globalThis);
