(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Ferramentas, página "Motor e combustível" (dono, 2026-10-18): um cartão por assunto, em palavras simples.
  // Cada cartão: título, uma linha, o estado escrito (Ligado/Desligado) e UM controle. Interruptor = liga/desliga; campo com lápis = número que
  // o dono pode digitar; botão com verbo = ação. Nada grava no 1º toque: o app lê a central, mostra "antes → depois" e só grava no 2º toque,
  // confere lendo de volta e oferece Desfazer. Sem relógio: a tela relê o que a ponte já tem a cada ciclo de dados.
  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  }
  const WHY = {
    nobridge: 'Só funciona no app, com a central.',
    nousb: 'Conecte a central para mudar.',
    unread: 'Esperando a leitura da central.',
  };
  const PENCIL = '<svg class="pencil" viewBox="0 0 24 24" width="22" height="22" aria-hidden="true" focusable="false"><path d="M3 17.25V21h3.75L17.8 9.94l-3.75-3.75L3 17.25zm17.7-10.2a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z" fill="currentColor"/></svg>';

  class AdvancedPanel {
    constructor(store, api) {
      this.store = store;
      this.api = api;
      this.A = ns.AdvancedFlags;
      this.V = ns.AntiApagaoValores;
      this.M = ns.CartoesMotor;
      this.screen = document.getElementById('toolsDrawer');
      this.host = document.getElementById('toolAdvanced');
      this.layer = document.getElementById('toolsLayer');
      this.open = true;          // a página 1 de Ferramentas existe sempre; só desenha quando a aba está à vista
      this.view = 'home';        // 'home' | 'valores' | id de um cartão de leitura
      this.data = { read: false, params: {} };
      this.drafts = {};          // índice -> texto digitado
      this.signature = '';
      this.layerSignature = '';
      if (!this.host || !this.A || !this.V || !this.M) return;
      const bridge = new Proxy({}, { get: (_, name) => (root.OmegasAutoCal ? root.OmegasAutoCal[name] : undefined) }); // ponte lida na hora do uso
      const addHook = (name, fn) => root.OmegasApp?.scheduler?.addHook?.(name, fn) || (() => {});
      this.flag = this.A.createWriteFlow({ bridge, addHook, onChange: () => { this.loadData(); this.render(); } });
      this.vals = this.V.createValuesFlow({ bridge, addHook, onChange: () => { this.loadData(); this.render(); } });
      this.host.addEventListener('click', event => this.onClick(event));
      this.host.addEventListener('input', event => this.onInput(event));
      this.layer?.addEventListener('click', event => this.onClick(event));
      // Um toque fora da janela de confirmação desarma a prévia (evento, nunca prazo). Toque DENTRO da janela (até no cartão, fora dos
      // botões) não cancela: o 2º toque do toque duplo pode cair no cartão sem estragar a confirmação.
      document.addEventListener('click', event => {
        if (this.layer && this.layer.contains(event.target)) return;
        this.flag.otherTouch();
        this.vals.otherTouch();
      }, true);
      this.flag.adopt();
      this.vals.adopt();
    }

    /** Chamado pelo ciclo de dados (app.js). */
    refresh() {
      if (!this.host || !this.flag) return;
      this.loadData();
      const state = this.store.get();
      const usb = state.status?.usbConnected === true ? true : state.status?.usbConnected === false ? false : undefined;
      this.flag.observe({ route: state.route, usbConnected: usb });
      this.vals.observe({ route: state.route, usbConnected: usb });
      if (state.route !== 'tools') { if (this.view !== 'home') { this.view = 'home'; this.signature = ''; this.setView(); } return; }
      this.render();
    }

    /** Lê da ponte o que a central já respondeu (sem pedir nada). */
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
    hasBridge() { const b = root.OmegasAutoCal; return !!b && typeof b.prepareAntiStalloFlag === 'function' && typeof b.prepareAntiStalloValues === 'function'; }

    reason(kind) {
      if (!this.hasBridge()) return WHY.nobridge;
      if (!this.usb()) return WHY.nousb;
      if (kind === 'flag' && !this.A.wordsOf(this.param('FLAG_CONF3'))) return WHY.unread;
      if (kind === 'values' && !this.V.valuesOf(this.param('ANTI_STALLO'))) return WHY.unread;
      return '';
    }

    // ---------- eventos ----------
    onClick(event) {
      const t = event.target.closest('button');
      if (!t) return;
      if (t.matches('[data-adv-switch]')) {
        const entry = this.param('FLAG_CONF3');
        const words = this.A.wordsOf(entry);
        if (!words || this.reason('flag')) return;
        this.vals.cancel();
        this.flag.tapSwitch(entry, this.usb());
      } else if (t.matches('[data-adv-confirm]')) {
        const kind = t.dataset.advConfirm;
        if (kind === 'values') this.vals.confirm();
        else if (this.flag.view().confirmOn === 'undo') this.flag.tapUndo();
        else this.flag.tapSwitch(this.param('FLAG_CONF3'), this.usb());
      } else if (t.matches('[data-adv-cancel]')) { this.flag.cancel(); this.vals.cancel(); this.dismissLayers(); }
      else if (t.matches('[data-adv-close]')) { this.flag.dismiss(); this.vals.dismiss(); }
      else if (t.matches('[data-adv-undo-flag]')) { this.flag.dismiss(); this.flag.tapUndo(); }
      else if (t.matches('[data-adv-undo-values]')) {
        const undo = this.vals.view().undo;
        if (undo.available) { this.vals.dismiss(); this.vals.ask('undo', this.diff(undo.values), this.usb()); }
      } else if (t.matches('[data-adv-save]')) this.askSave();
      else if (t.matches('[data-adv-factory]')) {
        const current = this.V.valuesOf(this.param('ANTI_STALLO'));
        const changes = this.V.factoryChanges(current);
        this.flag.cancel();
        if (Object.keys(changes).length) this.vals.ask('factory', changes, this.usb());
      } else if (t.matches('[data-adv-open]')) { this.view = t.dataset.advOpen; if (this.view === 'valores') this.resetDrafts(); this.setView(); }
      else if (t.matches('[data-adv-back]')) { this.view = 'home'; this.setView(); }
      this.signature = '';
      this.render();
    }

    dismissLayers() { this.flag.dismiss(); this.vals.dismiss(); }

    onInput(event) {
      const input = event.target.closest('input[data-adv-field]');
      if (!input) return;
      this.drafts[input.dataset.advField] = input.value;
      this.paintFields();
    }

    resetDrafts() {
      const values = this.V.valuesOf(this.param('ANTI_STALLO'));
      this.drafts = {};
      this.V.FIELDS.forEach(f => { this.drafts[f.index] = values ? this.V.num(values[f.index]) : ''; });
    }

    /** O que o dono digitou que difere do valor lido (só campos válidos). */
    pendingChanges() {
      const values = this.V.valuesOf(this.param('ANTI_STALLO'));
      const out = { changes: {}, invalid: false, errors: {} };
      this.V.FIELDS.forEach(f => {
        const text = this.drafts[f.index];
        if (text === undefined) return;
        const parsed = this.V.parseInput(text);
        if (!parsed.ok) { out.invalid = true; out.errors[f.index] = parsed.error; return; }
        const check = this.V.validate({ [f.index]: parsed.value });
        if (!check.ok) { out.invalid = true; out.errors[f.index] = `Use um valor ${this.V.range(f).toLowerCase()}.`; return; }
        if (values && parsed.value !== values[f.index]) out.changes[f.index] = parsed.value;
      });
      return out;
    }

    diff(target) {
      const values = this.V.valuesOf(this.param('ANTI_STALLO'));
      const out = {};
      this.V.FIELDS.forEach(f => { if (!values || target[f.index] !== values[f.index]) out[f.index] = target[f.index]; });
      return out;
    }

    askSave() {
      const p = this.pendingChanges();
      if (p.invalid || !Object.keys(p.changes).length || this.reason('values')) return;
      this.flag.cancel();
      this.vals.ask('edit', p.changes, this.usb());
    }

    /** Atualiza erro, aviso e botão sem refazer o HTML (o foco do campo digitado não pode se perder). */
    paintFields() {
      const p = this.pendingChanges();
      this.V.FIELDS.forEach(f => {
        const box = this.host.querySelector(`[data-field="${f.index}"]`);
        if (!box) return;
        const err = p.errors[f.index];
        box.dataset.invalid = err ? 'true' : 'false';
        box.dataset.changed = f.index in p.changes ? 'true' : 'false';
        const input = box.querySelector('input');
        if (input) input.setAttribute('aria-invalid', err ? 'true' : 'false');
        const msg = box.querySelector('.num-error');
        if (msg) msg.textContent = err || '';
        const sec = box.querySelector('.num-seconds');
        if (sec) { const parsed = this.V.parseInput(this.drafts[f.index]); sec.textContent = parsed.ok ? `= ${this.V.secondsText(parsed.value)}` : ''; }
      });
      const save = this.host.querySelector('[data-adv-save]');
      if (save) save.disabled = p.invalid || !Object.keys(p.changes).length || !!this.reason('values');
    }

    setView() {
      if (this.host) this.host.dataset.mode = this.view === 'home' ? 'home' : 'view';
      if (this.screen) this.screen.dataset.view = this.screen.querySelector('[data-mode="view"]') ? 'view' : 'home';
    }

    // ---------- desenho ----------
    render() {
      if (!this.host || !this.flag) return;
      const state = this.store.get();
      if (state.route !== 'tools') return;
      this.setView();
      this.renderLayer();
      const active = document.activeElement;
      if (this.view === 'valores' && active && this.host.contains(active) && active.tagName === 'INPUT') { this.paintFields(); return; }
      const fv = this.flag.view();
      const vv = this.vals.view();
      const signature = JSON.stringify([this.view, this.data, this.usb(), this.hasBridge(), fv.phase, vv.phase, vv.undo, this.view === 'valores' ? this.drafts : null]);
      if (signature === this.signature && this.host.childElementCount) return;
      this.signature = signature;
      this.host.dataset.mode = this.view === 'home' ? 'home' : 'view';
      const cards = this.M.build(this.data.params);
      this.host.innerHTML = this.view === 'home' ? this.renderHome(cards) : this.view === 'valores' ? this.renderValues(cards) : this.renderReadOnly(cards.find(c => c.id === this.view));
      if (this.view === 'valores') this.paintFields();
    }

    renderHome(cards) {
      const busy = ['preparing', 'preview', 'writing'].includes(this.flag.view().phase);
      return cards.map(card => {
        if (card.kind === 'switch') {
          const why = this.reason('flag');
          const state = card.on === null ? 'unknown' : card.on ? 'on' : 'off';
          return `
        <article class="tool-tile" data-card="${card.id}" data-state="${state}">
          <h3>${escapeHtml(card.title)}</h3>
          <p class="tile-line${why ? ' reason' : ''}">${escapeHtml(why || card.line)}</p>
          <button type="button" class="tool-switch" role="switch" aria-checked="${card.on === true ? 'true' : 'false'}" aria-label="${escapeHtml(card.title)}: ${escapeHtml(card.stateText)}" data-adv-switch ${why || busy ? 'disabled' : ''}>
            <span class="sw-track" aria-hidden="true"><i></i></span><b>${escapeHtml(card.stateText)}</b>
          </button>
        </article>`;
        }
        const go = card.kind === 'values' ? `Mudar valores ${PENCIL}` : 'Ver ›';
        return `
        <button type="button" class="tool-tile tool-open" data-card="${card.id}" data-adv-open="${card.id}" data-state="${card.on === null ? (card.stateText === '—' ? 'unknown' : 'neutral') : card.on ? 'on' : 'off'}">
          <h3>${escapeHtml(card.title)}</h3>
          <p class="tile-line">${escapeHtml(card.line)}</p>
          <span class="tile-state">${escapeHtml(card.stateText)}</span>
          <span class="tile-go">${go}</span>
        </button>`;
      }).join('');
    }

    renderValues(cards) {
      const values = this.V.valuesOf(this.param('ANTI_STALLO'));
      const why = this.reason('values');
      const undo = this.vals.view().undo;
      const fields = this.V.FIELDS.map(f => `
        <div class="num-field" data-field="${f.index}" data-invalid="false" data-changed="false">
          <label for="advField${f.index}">${escapeHtml(f.label)}</label>
          <div class="num-box">
            <input id="advField${f.index}" type="text" inputmode="numeric" pattern="[0-9.]*" autocomplete="off" data-adv-field="${f.index}" value="${escapeHtml(this.drafts[f.index] ?? (values ? this.V.num(values[f.index]) : ''))}" ${values ? '' : 'disabled'} aria-describedby="advHelp${f.index}">
            ${PENCIL}
            <span class="num-unit">${escapeHtml(f.unit)}</span>
          </div>
          <p class="num-help" id="advHelp${f.index}">${escapeHtml(f.help)} <span class="num-seconds"></span></p>
          <p class="num-meta">${escapeHtml(this.V.range(f))} · Valor de fábrica: ${escapeHtml(this.V.num(f.factory))}</p>
          <p class="num-error" role="alert"></p>
        </div>`).join('');
      return `
        <section class="tool-view values-view" data-card="valores">
          <header><button type="button" class="secondary tool-back" data-adv-back><span aria-hidden="true">‹</span> Voltar</button><h3>Valores do anti-apagão</h3><p>${escapeHtml(why || 'Toque num número, digite o novo valor e toque em Gravar.')}</p></header>
          <div class="num-grid">${fields}</div>
          <div class="tool-actions">
            <button type="button" class="primary" data-adv-save disabled>Gravar valores</button>
            <button type="button" class="secondary" data-adv-factory ${why ? 'disabled' : ''}>Restaurar fábrica</button>
            ${undo.available ? '<button type="button" class="secondary" data-adv-undo-values>Desfazer a última gravação</button>' : ''}
          </div>
          <details class="tool-tech"><summary>Ver detalhes técnicos</summary><ul>${(cards.find(c => c.id === 'valores')?.tech || []).map(t => `<li>${escapeHtml(t)}</li>`).join('')}<li>Faixa e valor de fábrica: tela original do programa do fabricante; a ordem dos campos é inferida.</li></ul></details>
        </section>`;
    }

    renderReadOnly(card) {
      if (!card) return '';
      const tech = card.tech.map(t => `<li>${escapeHtml(t)}</li>`).join('');
      return `
        <section class="tool-view read-view" data-card="${card.id}">
          <header><button type="button" class="secondary tool-back" data-adv-back><span aria-hidden="true">‹</span> Voltar</button><h3>${escapeHtml(card.viewTitle || card.title)}</h3><p>Só mostra como está. O app não muda isto.</p></header>
          <ul class="read-list">${card.rows.map(r => `<li data-state="${r.on === null ? 'neutral' : r.on ? 'on' : 'off'}"><span>${escapeHtml(r.label)}</span><b>${escapeHtml(r.text)}</b></li>`).join('')}</ul>
          <details class="tool-tech"><summary>Ver detalhes técnicos</summary><ul>${tech}</ul></details>
        </section>`;
    }

    /** Janela de confirmação e de resultado (cobre a tela; só os botões dela respondem). */
    renderLayer() {
      if (!this.layer) return;
      const fv = this.flag.view();
      const vv = this.vals.view();
      const words = this.A.wordsOf(this.param('FLAG_CONF3'));
      const on = words ? (words[0] & this.A.ANTI_MASK) !== 0 : null;
      const sig = JSON.stringify([fv.phase, fv.message, fv.confirmOn, fv.undo, vv.phase, vv.message, vv.kind, vv.plan, vv.undo, on]);
      if (sig === this.layerSignature) return;
      this.layerSignature = sig;
      let html = '';
      let flagBusy = fv.phase !== 'idle' || fv.message;
      let valsBusy = vv.phase !== 'idle' || vv.message;
      if (valsBusy) html = this.layerValues(vv); else if (flagBusy) html = this.layerFlag(fv, on);
      this.layer.hidden = !html;
      this.layer.innerHTML = html;
      if (html) { const first = this.layer.querySelector('button:not([disabled])'); if (first && document.activeElement && !this.layer.contains(document.activeElement)) first.focus({ preventScroll: true }); }
    }

    layerCard(title, body, buttons, level) {
      return `<div class="tool-layer-card" role="dialog" aria-modal="true" aria-label="${escapeHtml(title)}" data-level="${level || ''}"><h3>${escapeHtml(title)}</h3>${body}<div class="tool-actions">${buttons}</div></div>`;
    }

    layerFlag(fv, on) {
      const undoing = fv.confirmOn === 'undo';
      const target = undoing ? (on ? 'Desligado' : 'Ligado') : (on ? 'Desligado' : 'Ligado');
      const verb = target === 'Ligado' ? 'Ligar anti-apagão' : 'Desligar anti-apagão';
      if (fv.phase === 'preparing') return this.layerCard('Lendo a central…', '<p>Só leitura. Nada foi gravado.</p>', '<button type="button" class="secondary" data-adv-cancel>Agora não</button>');
      if (fv.phase === 'preview') {
        return this.layerCard(`${verb}?`, `<p class="layer-change"><span>${on ? 'Ligado' : 'Desligado'}</span><i aria-hidden="true">→</i><b>${target}</b></p><p>O resto continua igual.</p>`,
          `<button type="button" class="primary" data-adv-confirm="flag">${verb}</button><button type="button" class="secondary" data-adv-cancel>Agora não</button>`);
      }
      if (fv.phase === 'writing') return this.layerCard('Gravando e conferindo…', '<p>Não desconecte o cabo.</p>', '<button type="button" class="secondary" disabled>Aguarde</button>');
      if (fv.phase === 'done') {
        const undo = fv.undo.available ? `<button type="button" class="secondary" data-adv-undo-flag>Desfazer: voltar a ${fv.undo.enable ? 'Ligado' : 'Desligado'}</button>` : '';
        return this.layerCard('Pronto', `<p class="adv-result" data-level="ok">${escapeHtml(fv.message)}</p>`, `<button type="button" class="primary" data-adv-close>Fechar</button>${undo}`, 'ok');
      }
      return this.layerCard(fv.phase === 'error' ? 'Não foi gravado' : 'Aviso', `<p class="adv-result" data-level="${fv.phase === 'error' ? 'warning' : 'info'}">${escapeHtml(fv.message)}</p>`, '<button type="button" class="primary" data-adv-close>Fechar</button>', fv.phase === 'error' ? 'warning' : '');
    }

    layerValues(vv) {
      const V = this.V;
      const title = vv.kind === 'factory' ? 'Restaurar valores de fábrica?' : vv.kind === 'undo' ? 'Desfazer a última gravação?' : 'Gravar estes valores?';
      const verb = vv.kind === 'factory' ? 'Restaurar fábrica' : vv.kind === 'undo' ? 'Desfazer' : 'Gravar valores';
      if (vv.phase === 'preparing') return this.layerCard('Lendo a central…', '<p>Só leitura. Nada foi gravado.</p>', '<button type="button" class="secondary" data-adv-cancel>Agora não</button>');
      if (vv.phase === 'preview' && vv.plan) {
        const lines = vv.plan.changed.map(i => {
          const f = V.FIELDS[i];
          const far = V.farFromFactory(i, vv.plan.after[i]) ? '<em>Longe do valor de fábrica. Confira antes de gravar.</em>' : '';
          return `<li><span>${escapeHtml(f.label)}</span><b>${escapeHtml(V.num(vv.plan.before[i]))} <i aria-hidden="true">→</i> ${escapeHtml(V.num(vv.plan.after[i]))} ${escapeHtml(f.unit)}</b>${far}</li>`;
        }).join('');
        return this.layerCard(title, `<ul class="layer-list">${lines}</ul>`,
          `<button type="button" class="primary" data-adv-confirm="values">${verb}</button><button type="button" class="secondary" data-adv-cancel>Agora não</button>`);
      }
      if (vv.phase === 'writing') return this.layerCard('Gravando e conferindo…', '<p>Não desconecte o cabo.</p>', '<button type="button" class="secondary" disabled>Aguarde</button>');
      if (vv.phase === 'done') {
        return this.layerCard('Pronto', `<p class="adv-result" data-level="ok">${escapeHtml(vv.message)}</p>`, '<button type="button" class="primary" data-adv-close>Fechar</button>', 'ok');
      }
      return this.layerCard(vv.phase === 'error' ? 'Não foi gravado' : 'Aviso', `<p class="adv-result" data-level="${vv.phase === 'error' ? 'warning' : 'info'}">${escapeHtml(vv.message)}</p>`, '<button type="button" class="primary" data-adv-close>Fechar</button>', vv.phase === 'error' ? 'warning' : '');
    }
  }

  ns.AdvancedPanel = AdvancedPanel;
})(typeof window !== 'undefined' ? window : globalThis);
