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
    nobridge: 'Só funciona no app.',
    nousb: 'Conecte a central.',
    unread: 'Aguardando a central.',
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
      this.extraIndex = 1;
      this.saveAgain = false;
      this.layerSignature = '';
      if (!this.host || !this.A || !this.V || !this.M) return;
      const bridge = new Proxy({}, { get: (_, name) => (root.OmegasAutoCal ? root.OmegasAutoCal[name] : undefined) }); // ponte lida na hora do uso
      const addHook = (name, fn) => root.OmegasApp?.scheduler?.addHook?.(name, fn) || (() => {});
      this.flag = this.A.createWriteFlow({ bridge, addHook, onChange: () => { this.loadData(); this.render(); } });
      this.vals = this.V.createValuesFlow({ bridge, addHook, onChange: () => { this.loadData(); this.render(); } });
      this.host.addEventListener('click', event => this.onClick(event));
      this.host.addEventListener('input', event => this.onInput(event));
      // Digitou e saiu do campo (ou Enter): grava na hora, como os botões − / +.
      this.host.addEventListener('change', event => { if (event.target.closest('input[data-adv-field]')) this.autoSave(); });
      this.layer?.addEventListener('click', event => this.onClick(event));
      // Um toque fora da janela de confirmação desarma a prévia (evento, nunca prazo). Toque DENTRO da janela (até no cartão, fora dos
      // botões) não cancela: o 2º toque do toque duplo pode cair no cartão sem estragar a confirmação.
      document.addEventListener('click', event => {
        if (this.layer && this.layer.contains(event.target)) return;
        this.flag.otherTouch();
        this.vals.otherTouch();
      }, true);
      // As leituras auxiliares são opcionais. Uma exceção da ponte Android não
      // pode impedir a criação dos cartões nem duplicar listeners ao reabrir.
      try { this.flag.adopt(); }
      catch (error) { console.warn('[OMEGAS Ferramentas] Estado anterior do anti-apagão indisponível', error); }
      try { this.vals.adopt(); }
      catch (error) { console.warn('[OMEGAS Ferramentas] Estado anterior dos valores indisponível', error); }
    }

    /** Chamado pelo ciclo de dados (app.js). */
    refresh() {
      if (!this.host || !this.flag) return;
      const state = this.store.get();
      const usb = state.status?.usbConnected === true ? true : state.status?.usbConnected === false ? false : undefined;
      this.flag.observe({ route: state.route, usbConnected: usb });
      this.vals.observe({ route: state.route, usbConnected: usb });
      if (state.route !== 'tools') { if (this.view !== 'home') { this.view = 'home'; this.signature = ''; this.setView(); } return; }
      // Tal como as demais telas, só consulta seus dados enquanto está ativa.
      // Observe/cancel do fluxo de escrita permanece operando mesmo ao sair da aba.
      this.loadData();
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
    hasBridge(kind = 'flag') {
      const b = root.OmegasAutoCal;
      return !!b && typeof b[kind === 'values' ? 'prepareAntiStalloValues' : 'prepareAntiStalloFlag'] === 'function';
    }

    reason(kind) {
      if (!this.hasBridge(kind)) return WHY.nobridge;
      if (!this.usb()) return WHY.nousb;
      // O primeiro toque lê FLAG_CONF3 ao vivo. O snapshot geral NÃO é pré-requisito.
      if (kind === 'values' && !this.V.valuesOf(this.param('ANTI_STALLO'))) return WHY.unread;
      return '';
    }

    // ---------- eventos ----------
    onClick(event) {
      // Tocar no lápis, na unidade ou na borda do campo = tocar no campo (o teclado abre aí).
      const box = event.target.closest('.num-box');
      if (box) { const input = box.querySelector('input'); if (input && !input.disabled && event.target !== input) input.focus(); return; }
      const stepBtn = event.target.closest('[data-adv-step]');
      if (stepBtn && !stepBtn.disabled) { this.stepField(stepBtn.dataset.advStep); return; }
      // A ação principal também responde ao toque no título e na descrição.
      // O primeiro toque só prepara a prévia, nunca escreve diretamente.
      const t = event.target.closest('button') ||
        (event.target.closest('#toolAdvanced [data-card="anti"]') ? this.host.querySelector('[data-adv-switch]') : null);
      if (!t || t.disabled) return;
      if (t.matches('[data-adv-switch]')) {
        const entry = this.param('FLAG_CONF3');
        if (this.reason('flag')) return;
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
      } else if (t.matches('[data-adv-open-view]')) { this.view = t.dataset.advOpenView; this.setView(); }
      else if (t.matches('[data-adv-back-valores]')) { this.view = 'valores'; this.setView(); }
      else if (t.matches('[data-adv-extra]')) { this.extraIndex = Number(t.dataset.advExtra) || 1; }
      else if (t.matches('[data-adv-open]')) { this.view = t.dataset.advOpen; if (this.view === 'valores') this.resetDrafts(); this.setView(); }
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

    /** Botões − / +: mexem no número em passos pequenos (nunca fora da faixa) e só preparam; "Gravar valores" grava num toque. */
    stepField(spec) {
      const [idx, dir] = String(spec).split(':').map(Number);
      const f = this.V.FIELDS.find(x => x.index === idx);
      if (!f) return;
      const values = this.V.valuesOf(this.param('ANTI_STALLO'));
      const parsed = this.V.parseInput(this.drafts[idx]);
      const base = parsed.ok ? parsed.value : (values ? values[idx] : f.factory);
      const next = Math.max(f.min, Math.min(f.max, base + dir * f.step));
      this.drafts[idx] = this.V.num(next);
      const input = this.host.querySelector(`#advField${idx}`);
      if (input) input.value = this.drafts[idx];
      this.paintFields();
      this.autoSave();
    }

    /** Grava o que mudou, na hora. Toques durante uma gravação não se perdem: gravam em seguida (por evento, sem relógio). */
    autoSave() {
      const phase = this.vals.view().phase;
      if (phase === 'preparing' || phase === 'writing') { this.saveAgain = true; return; }
      this.saveAgain = false;
      const p = this.pendingChanges();
      if (p.invalid || !Object.keys(p.changes).length || this.reason('values')) return;
      this.vals.dismiss();
      this.flag.cancel();
      this.vals.ask('edit', p.changes, this.usb());
    }

    isValuesView() { return this.view === 'valores' || this.view === 'extras' || this.view === 'info'; }

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
        const warn = box.querySelector('.num-warn');
        if (warn) { const pv = this.V.parseInput(this.drafts[f.index]); warn.textContent = pv.ok && !err && this.V.farFromFactory(f.index, pv.value) ? 'Longe do valor de fábrica. Se o carro piorar, toque em Desfazer ou Restaurar fábrica.' : ''; }
        const sec = box.querySelector('.num-seconds');
        if (sec) { const parsed = this.V.parseInput(this.drafts[f.index]); sec.textContent = parsed.ok ? `= ${this.V.secondsText(parsed.value)}` : ''; }
      });
      const save = this.host.querySelector('[data-adv-save]');
      if (save) save.disabled = p.invalid || !Object.keys(p.changes).length || !!this.reason('values');
    }

    setView() {
      const mode = this.view === 'home' ? 'home' : 'view';
      if (this.host && this.host.dataset.mode !== mode) this.host.dataset.mode = mode;
      if (this.screen) {
        const screenView = this.screen.querySelector('[data-mode="view"]') ? 'view' : 'home';
        if (this.screen.dataset.view !== screenView) this.screen.dataset.view = screenView;
      }
    }

    // ---------- desenho ----------
    render() {
      if (!this.host || !this.flag) return;
      const state = this.store.get();
      if (state.route !== 'tools') return;
      this.setView();
      if (this.saveAgain && !['preparing', 'writing'].includes(this.vals.view().phase)) this.autoSave();
      try { this.renderLayer(); }
      catch (error) {
        console.error('[OMEGAS Ferramentas] Janela de confirmação indisponível', error);
        if (this.layer) { this.layer.hidden = true; this.layer.innerHTML = ''; }
        this.layerSignature = '';
      }
      const active = document.activeElement;
      if (this.isValuesView() && active && this.host.contains(active) && active.tagName === 'INPUT') { this.paintFields(); return; }
      const fv = this.flag.view();
      const vv = this.vals.view();
      const signature = JSON.stringify([this.view, this.data, this.usb(), this.hasBridge(), fv.phase, vv.phase, vv.message, vv.undo, this.isValuesView() ? [this.drafts, this.extraIndex] : null]);
      if (signature === this.signature && this.host.childElementCount) return;
      this.signature = signature;
      try {
        let cards;
        let usingUnknown = false;
        try { cards = this.M.build(this.data.params); }
        catch (error) {
          console.warn('[OMEGAS Ferramentas] Leitura inválida, mostrando estado desconhecido', error);
          cards = this.M.build({}); // Degrada apenas a leitura, nunca escreve nem presume desligado.
          usingUnknown = true;
        }
        const next = this.view === 'home' ? this.renderHome(cards)
          : this.view === 'valores' ? this.renderValues(cards)
          : this.view === 'extras' ? this.renderExtras(cards)
          : this.view === 'info' ? this.renderInfo()
          : this.renderReadOnly(cards.find(c => c.id === this.view));
        if (this.host.innerHTML !== next) this.host.innerHTML = next;
        if (this.isValuesView()) this.paintFields();
        if (usingUnknown) this.signature = ''; // Próximo quadro tenta recuperar sozinho, sem redesenho idêntico.
      } catch (error) {
        console.error('[OMEGAS Ferramentas] Cartões indisponíveis', error);
        const fallback = this.errorCard('ferramentas');
        if (this.host.innerHTML !== fallback) this.host.innerHTML = fallback;
        this.signature = '';
      }
    }

    errorCard(id) {
      return `<article class="tool-tile" data-card="${escapeHtml(id)}"><h3>Leitura indisponível</h3><p class="tile-line reason">Esta parte não pôde ser exibida. As outras continuam disponíveis.</p></article>`;
    }

    renderHome(cards) {
      const busy = ['preparing', 'preview', 'writing'].includes(this.flag.view().phase);
      return cards.map(card => {
        try {
        if (card.kind === 'switch') {
          const why = this.reason('flag');
          const line = why || (card.on === null ? 'Ainda não lido. Toque para conferir e ligar.' : card.line);
          const shortLine = why || (card.on === null ? 'Ainda não lido. Toque para conferir e ligar.' : (card.shortLine || card.line));
          const state = card.on === null ? 'unknown' : card.on ? 'on' : 'off';
          const action = card.on === null ? 'Conferir e ligar' : card.on ? 'Desligar anti-apagão' : 'Ligar anti-apagão';
          return `
        <article class="tool-tile" data-card="${card.id}" data-state="${state}" data-actionable="${!why && !busy}">
          <h3>${escapeHtml(card.title)}</h3>
          <p class="tile-line${why ? ' reason' : ''}"><span class="tile-full">${escapeHtml(line)}</span><span class="tile-compact">${escapeHtml(shortLine)}</span></p>
          <button type="button" class="tool-switch" role="${card.on === null ? 'button' : 'switch'}" ${card.on === null ? '' : 'aria-checked="' + (card.on ? 'true' : 'false') + '"'} aria-label="${escapeHtml(action)}. Estado atual: ${escapeHtml(card.stateText)}" data-adv-switch ${why || busy ? 'disabled' : ''}>
            <span class="sw-track" aria-hidden="true"><i></i></span><span class="sw-caption"><b>${escapeHtml(card.stateText)}</b><small>${escapeHtml(action)}</small></span>
          </button>
          ${this.flag.view().undo.available && ['done', 'idle'].includes(this.flag.view().phase) ? '<button type="button" class="tile-undo" data-adv-undo-flag>Desfazer</button>' : ''}
        </article>`;
        }
        const go = card.kind === 'values' ? `Mudar valores ${PENCIL}` : 'Ver ›';
        return `
        <button type="button" class="tool-tile tool-open" data-card="${card.id}" data-adv-open="${card.id}" data-state="${card.on === null ? (card.stateText === '—' ? 'unknown' : 'neutral') : card.on ? 'on' : 'off'}">
          <h3>${escapeHtml(card.title)}</h3>
          <p class="tile-line"><span class="tile-full">${escapeHtml(card.line)}</span><span class="tile-compact">${escapeHtml(card.shortLine || card.line)}</span></p>
          <span class="tile-state">${escapeHtml(card.stateText)}</span>
          <span class="tile-go">${go}</span>
        </button>`;
        } catch (error) {
          console.error('[OMEGAS Ferramentas] Um cartão não abriu', error);
          return this.errorCard(card?.id || 'parte');
        }
      }).join('');
    }

    /** Campo com − / + : cada toque já grava (sem botão "Gravar"). */
    fieldHtml(f, values) {
      return `
        <div class="num-field" data-field="${f.index}" data-invalid="false" data-changed="false">
          <label for="advField${f.index}">${escapeHtml(f.label)}</label>
          <div class="num-stepper">
            <button type="button" class="step-btn" data-adv-step="${f.index}:-1" aria-label="Diminuir ${f.step} ${escapeHtml(f.unit)}" ${values ? '' : 'disabled'}>− ${f.step}</button>
            <div class="num-box">
              <input id="advField${f.index}" type="text" inputmode="numeric" pattern="[0-9.]*" autocomplete="off" data-adv-field="${f.index}" value="${escapeHtml(this.drafts[f.index] ?? (values ? this.V.num(values[f.index]) : ''))}" ${values ? '' : 'disabled'} aria-describedby="advHelp${f.index}">
              ${PENCIL}
              <span class="num-unit">${escapeHtml(f.unit)}</span>
            </div>
            <button type="button" class="step-btn" data-adv-step="${f.index}:1" aria-label="Aumentar ${f.step} ${escapeHtml(f.unit)}" ${values ? '' : 'disabled'}>+ ${f.step}</button>
          </div>
          <p class="num-help" id="advHelp${f.index}">${escapeHtml(f.help)}${f.unit === 'ms' ? ' <span class="num-seconds"></span>' : ''}</p>
          <ul class="num-tips"><li><b>Se precisar de mais:</b> ${escapeHtml(f.tipUp)}</li><li><b>Se atrapalhar:</b> ${escapeHtml(f.tipDown)}</li></ul>
          <p class="num-meta">${escapeHtml(this.V.range(f))} · Valor de fábrica: ${escapeHtml(this.V.num(f.factory))}</p>
          <p class="num-warn"></p>
          <p class="num-error" role="alert"></p>
        </div>`;
    }

    /** Linha de estado da gravação, sempre visível: o dono vê o que está acontecendo. */
    saveStatus() {
      const vv = this.vals.view();
      if (vv.phase === 'preparing' || vv.phase === 'writing') return ['busy', 'Gravando…'];
      if (vv.phase === 'done') return ['ok', 'Gravado e conferido ✓'];
      if (vv.phase === 'error') return ['bad', 'Não gravou. ' + (vv.message || '')];
      return ['idle', 'Toque em − ou + : grava na hora.'];
    }

    footerHtml(cards, extra) {
      const values = this.V.valuesOf(this.param('ANTI_STALLO'));
      const why = this.reason('values');
      const undo = this.vals.view().undo;
      return `<div class="tool-actions vv-actions">
            <button type="button" class="secondary" data-adv-factory ${why || !values || values.every((v, i) => v === this.V.FACTORY[i]) ? 'disabled' : ''}>Restaurar fábrica</button>
            ${undo.available ? '<button type="button" class="secondary" data-adv-undo-values>Desfazer</button>' : ''}
            ${extra}
          </div>`;
    }

    /** Tela principal: o número que importa, ao lado do "como funciona". Sem rolagem. */
    renderValues(cards) {
      const values = this.V.valuesOf(this.param('ANTI_STALLO'));
      const why = this.reason('values');
      const f0 = this.V.FIELDS[0];
      const [level, statusText] = this.saveStatus();
      return `
        <section class="tool-view values-view" data-card="valores">
          <header><button type="button" class="secondary tool-back" data-adv-back><span aria-hidden="true">‹</span> Voltar</button><h3>Valores do anti-apagão</h3><p class="vv-status" data-level="${level}" role="status">${escapeHtml(why || statusText)}</p></header>
          <div class="vv-body">
            <div class="vv-main">${this.fieldHtml(f0, values)}</div>
            <aside class="vv-info" aria-label="Como funciona">
              <p class="vv-how"><b>Como funciona:</b> quando a rotação <b>cai de repente</b> abaixo de <b>${escapeHtml(this.V.num(values ? values[0] : f0.factory))} rpm</b> <span class="vv-long">(ex.: pisou na embreagem parando ou engatando ré), o anti-apagão entra e ajuda o motor a não apagar. Em aceleração normal ele não faz nada.</span><span class="vv-short">(ex.: pisou na embreagem), ele entra e ajuda o motor a não apagar.</span></p>
              <button type="button" class="secondary" data-adv-open-view="info">Ver explicação completa</button>
            </aside>
          </div>
          ${this.footerHtml(cards, '<button type="button" class="secondary" data-adv-open-view="extras">Ajustes extras (experimental) ›</button>')}
        </section>`;
    }

    /** Ajustes extras: um por vez (abas), cada um com a sua explicação completa. Sem rolagem. */
    renderExtras(cards) {
      const values = this.V.valuesOf(this.param('ANTI_STALLO'));
      const [level, statusText] = this.saveStatus();
      const extras = this.V.FIELDS.filter(f => f.index !== 0);
      const cur = extras.find(f => f.index === this.extraIndex) || extras[0];
      const tabs = extras.map(f => `<button type="button" class="vv-tab" data-adv-extra="${f.index}" aria-pressed="${f.index === cur.index}">${escapeHtml(f.tab)}</button>`).join('');
      return `
        <section class="tool-view values-view" data-card="valores-extras">
          <header><button type="button" class="secondary tool-back" data-adv-back-valores><span aria-hidden="true">‹</span> Voltar</button><h3>Ajustes extras <small>(experimental)</small></h3><p class="vv-status" data-level="${level}" role="status">${escapeHtml(statusText)}</p></header>
          <nav class="vv-tabs" aria-label="Qual ajuste">${tabs}</nav>
          <div class="vv-main vv-single">${this.fieldHtml(cur, values)}</div>
          <p class="vv-note">O programa original só mostra o primeiro número. O efeito exato destes quatro ainda não foi confirmado em carro.</p>
        </section>`;
    }

    /** Explicação completa (uma tela só de leitura, sem rolagem). */
    renderInfo() {
      const values = this.V.valuesOf(this.param('ANTI_STALLO'));
      const lim = this.V.num(values ? values[0] : 1600);
      return `
        <section class="tool-view values-view" data-card="valores-info">
          <header><button type="button" class="secondary tool-back" data-adv-back-valores><span aria-hidden="true">‹</span> Voltar</button><h3>Como o anti-apagão funciona</h3></header>
          <div class="vv-info-full">
            <ol>
              <li>A rotação cai mais rápido que <b>Queda para agir</b> <em>e</em> está abaixo de <b>${escapeHtml(lim)} rpm</b> → a proteção <b>entra</b>.</li>
              <li>A central sai do gás conforme a <b>Espera</b> (ms) e ajuda o motor a segurar.</li>
              <li>Quando a queda fica menor que <b>Queda para parar</b>, a proteção <b>solta</b>. A <b>Folga</b> evita ligar e desligar toda hora.</li>
            </ol>
            <p><b>Não é o corte de injeção</b> (soltar o acelerador em desaceleração): o corte tem ajustes próprios no cartão <b>Saída do corte</b>. Os dois se encontram: no seu log, depois de cada corte, com o anti-apagão <b>desligado</b> a rotação afundou até 447 rpm (26 vezes abaixo de 600); <b>ligado</b>, nunca passou de 680 rpm.</p>
          </div>
        </section>`;
    }

    renderReadOnly(card) {
      if (!card) return '';
      const tech = card.tech.map(t => `<li>${escapeHtml(t)}</li>`).join('');
      return `
        <section class="tool-view read-view" data-card="${card.id}">
          <header><button type="button" class="secondary tool-back" data-adv-back><span aria-hidden="true">‹</span> Voltar</button><h3>${escapeHtml(card.viewTitle || card.title)}</h3><p>Só mostra como está. O app não muda isto.</p></header>
          ${card.note ? `<p class="read-note">${escapeHtml(card.note)}</p>` : ''}
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
      // Sem janela de pergunta: ler, gravar e "pronto" acontecem no próprio cartão; só erro/aviso abrem a janela.
      if (['preparing', 'preview', 'writing', 'done'].includes(fv.phase)) return '';
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
      if (['preparing', 'preview', 'writing', 'done'].includes(vv.phase)) return '';   // sem janela: o resultado aparece na própria tela
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
