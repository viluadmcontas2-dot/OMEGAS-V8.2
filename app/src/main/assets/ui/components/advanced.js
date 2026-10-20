(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  /** Ciclos de contexto do agendador do app (o que já atualiza a tela, ~2 s) entre dois pedidos de leitura dos 5 números enquanto eles não chegam. Sem relógio novo. */
  const READ_RETRY_CYCLES = 1;

  // Ferramentas, página "Motor e combustível" (dono, 2026-10-18): um cartão por assunto, em palavras simples.
  // Cada cartão: título, uma linha, o estado escrito (Ligado/Desligado) e UM controle. Interruptor = liga/desliga; campo com lápis = número que
  // o dono pode digitar; botão com verbo = ação. Um toque = a ordem (regra sem atrito): o nativo lê a central, grava, confere lendo
  // de volta e o cartão oferece Desfazer. Sem relógio: a tela relê o que a ponte já tem a cada ciclo de dados.
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
      this.VG = ns.VoltaGasolina;   // opcional: sem ele o cartão "Volta para gasolina" vira só leitura
      this.G = ns.FerramentasGrupos; // páginas de grupo (dono, 2026-10-20): tudo do assunto numa página só
      this.voltaDrafts = {};        // { mbar?, segundos? } ainda não salvos
      this.screen = document.getElementById('toolsDrawer');
      this.host = document.getElementById('toolAdvanced');
      this.open = true;          // a página 1 de Ferramentas existe sempre; só desenha quando a aba está à vista
      this.view = 'home';        // 'home' | 'valores' (grupo anti-apagão) | 'info' | 'volta' | 'troca' | 'lenta' | 'outras'
      this.data = { read: false, params: {} };
      this.drafts = {};          // índice -> texto digitado
      this.signature = '';
      this.extraIndex = 1;
      this.saveAgain = false;
      this.directReadRequested = false;
      if (!this.host || !this.A || !this.V || !this.M) return;
      // Ponte lida na hora do uso. A chamada sai SEMPRE como `OmegasAutoCal.metodo(...)`: no WebView do Android o método Java só
      // responde quando chamado no próprio objeto injetado (com outro `this` lança "Java bridge method can't be invoked on a
      // non-injected object"). Antes era `Proxy` devolvendo o método solto: no Chromium passava, no aparelho nada gravava.
      const bridge = new Proxy({}, { get: (_, name) => {
        const target = root.OmegasAutoCal;
        if (!target || typeof target[name] !== 'function') return target ? target[name] : undefined;
        return (...args) => target[name](...args);
      } });
      const addHook = (name, fn) => root.OmegasApp?.scheduler?.addHook?.(name, fn) || (() => {});
      this.flag = this.A.createWriteFlow({ bridge, addHook, onChange: () => { this.loadData(); this.render(); } });
      this.vals = this.V.createValuesFlow({ bridge, addHook, onChange: () => { this.loadData(); this.render(); } });
      this.host.addEventListener('click', event => this.onClick(event));
      this.host.addEventListener('input', event => this.onInput(event));
      // Digitou e saiu do campo (ou Enter): grava na hora, como os botões − / +.
      this.host.addEventListener('change', event => { if (event.target.closest('input[data-adv-field]')) this.autoSave(); });
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
      if (state.route !== 'tools' || usb === false) this.saveAgain = false;   // toque enfileirado não sobrevive a sair da aba nem a cabo solto
      if (state.route !== 'tools') {
        this.directReadRequested = false;
        if (this.view !== 'home') { this.view = 'home'; this.signature = ''; this.setView(); }
        return;
      }
      if (usb === false) this.directReadRequested = false;
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
      this.voltaReadIfMissing(bridge);
      const v = this.V.valuesOf(data.params?.ANTI_STALLO);
      if (v) { this.lastValues = v; return; }
      // Tela de valores sem os 5 números conhecidos: pede a leitura direta (não depende do Mapa K/Curva K).
      // Sem relógio: a cada ciclo de atualização da tela (o do agendador, sem relógio novo), enquanto os valores não
      // chegam. O nativo ainda responde `alreadyReading` se uma leitura estiver em andamento, então nunca empilha. (Antes era UMA vez por visita: se essa
      // falhasse, os botões − / + ficavam desligados para sempre e parecia que "não grava".)
      this.readTick = (this.readTick || 0) + 1;
      if (this.view === 'valores' && this.usb() && this.readTick >= (this.nextReadAt || 0) &&
          this.vals.view().phase === 'idle' && typeof bridge?.readAntiStalloValues === 'function') {
        this.directReadRequested = true;
        this.nextReadAt = this.readTick + READ_RETRY_CYCLES; // conta ciclos do agendador, não segundos
        try {
          const result = JSON.parse(bridge.readAntiStalloValues());
          if (result.ok === false) console.warn('[OMEGAS Ferramentas] Leitura direta recusada:', result.error);
        } catch (error) { console.warn('[OMEGAS Ferramentas] Leitura direta indisponível:', error); }
      }
    }

    /** Valores atuais; enquanto grava/relê, mantém o último conhecido (a leitura volta daqui a pouco): os botões não podem sumir no meio dos toques. */
    valuesNow() {
      const live = this.V.valuesOf(this.param('ANTI_STALLO'));
      if (live) { this.lastValues = live; return live; }
      const ph = this.vals.view().phase;
      return (ph === 'preparing' || ph === 'writing' || ph === 'done') && this.lastValues ? this.lastValues : null;
    }

    usb() { return this.store.get().status?.usbConnected === true; }
    param(name) { return (this.data.params || {})[name]; }
    hasBridge(kind = 'flag') {
      const b = root.OmegasAutoCal;
      const apply = kind === 'values' ? 'applyAntiStalloValues' : 'applyAntiStalloFlag';
      const prepare = kind === 'values' ? 'prepareAntiStalloValues' : 'prepareAntiStalloFlag';
      return !!b && (typeof b[apply] === 'function' || typeof b[prepare] === 'function');   // a chamada única basta; prepare/confirm é só a reserva antiga
    }

    reason(kind) {
      if (!this.hasBridge(kind)) return WHY.nobridge;
      if (!this.usb()) return WHY.nousb;
      // O primeiro toque lê FLAG_CONF3 ao vivo. O snapshot geral NÃO é pré-requisito.
      // Uma leitura geral incompleta não bloqueia edição digitada: o gravador
      // nativo relê ANTI_STALLO diretamente antes de qualquer escrita.
      return '';
    }

    // ---------- eventos ----------
    onClick(event) {
      // Tocar no lápis, na unidade ou na borda do campo = tocar no campo (o teclado abre aí).
      const box = event.target.closest('.num-box');
      if (box) { const input = box.querySelector('input'); if (input && !input.disabled && event.target !== input) input.focus(); return; }
      const stepBtn = event.target.closest('[data-adv-step]');
      if (stepBtn && !stepBtn.disabled) { this.stepField(stepBtn.dataset.advStep); return; }
      // A ação principal também responde ao toque no título e na descrição. Um toque = a ordem (sem prévia nem janela).
      const t = event.target.closest('button') ||
        (event.target.closest('#toolAdvanced [data-card="anti"]') ? this.host.querySelector('[data-adv-switch]') : null);
      if (!t || t.disabled) return;
      if (t.matches('[data-adv-switch]')) {
        const entry = this.param('FLAG_CONF3');
        if (this.reason('flag')) return;
        if (['preparing', 'writing'].includes(this.vals.view().phase)) return;   // os 5 números estão gravando: o toque espera a vez
        this.vals.cancel();
        this.flag.tapSwitch(entry, this.usb());
      } else if (t.matches('[data-adv-undo-flag]')) { this.flag.dismiss(); this.flag.tapUndo(); }
      else if (t.matches('[data-adv-undo-values]')) {
        const undo = this.vals.view().undo;
        if (undo.available) { this.vals.dismiss(); this.drafts = {}; this.vals.ask('undo', this.diff(undo.values), this.usb()); }
      } else if (t.matches('[data-adv-factory]')) {
        const current = this.valuesNow();
        const changes = this.V.factoryChanges(current);
        this.flag.cancel(); this.drafts = {};
        if (Object.keys(changes).length) this.vals.ask('factory', changes, this.usb());
      } else if (t.matches('[data-volta-step], [data-volta-toggle], [data-volta-undo]')) { this.voltaTap(t); }
      else if (t.matches('[data-adv-open-view]')) { this.view = t.dataset.advOpenView; this.setView(); }
      else if (t.matches('[data-adv-back-valores]')) { this.view = 'valores'; this.setView(); }
      else if (t.matches('[data-adv-use-suggestion]')) {
        const changes = this.V.suggestionChanges(this.valuesNow(), this.data.antiStallSuggestion);
        this.flag.cancel(); this.drafts = {};
        if (Object.keys(changes).length) this.vals.ask('edit', changes, this.usb());
      } else if (t.matches('[data-adv-open-info-tab]')) { this.infoTab = t.dataset.advOpenInfoTab; this.view = 'info'; this.setView(); }
      else if (t.matches('[data-adv-info-tab]')) { this.infoTab = t.dataset.advInfoTab; }
      else if (t.matches('[data-adv-open]')) {
        this.view = (this.G && this.G.groupOf(t.dataset.advOpen)) || t.dataset.advOpen;
        if (this.view === 'volta') { this.voltaDrafts = {}; this.voltaRefused = ''; this.voltaReadAsked = false; this.voltaAgain = false; this.voltaSent = false; this.loadData(); }
        if (this.view === 'valores') {
          this.resetDrafts();
          // A solicitação de leitura nasce do toque, não depende de um novo
          // ciclo de telemetria. A consulta não grava e é única por visita.
          this.loadData();
          // Se a resposta for imediata (emulador), atualiza o cache agora.
          // Na ECU real o retorno assíncrono vem pelo ciclo normal do app.
          if (this.directReadRequested && !this.valuesNow()) this.loadData();
          this.resetDrafts();
        }
        this.setView();
      }
      else if (t.matches('[data-adv-back]')) { this.view = 'home'; this.setView(); }
      this.signature = '';
      this.render();
    }

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
      const values = this.valuesNow();
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
    /** O interruptor está lendo/gravando (a porta serial é uma só: nunca duas gravações juntas). */
    flagBusy() { return ['preparing', 'writing'].includes(this.flag.view().phase); }

    autoSave() {
      const phase = this.vals.view().phase;
      if (phase === 'preparing' || phase === 'writing' || this.flagBusy()) { this.saveAgain = true; return; }
      this.saveAgain = false;
      const p = this.pendingChanges();
      if (p.invalid || !Object.keys(p.changes).length || this.reason('values')) return;
      this.vals.dismiss();
      this.flag.cancel();
      this.vals.ask('edit', p.changes, this.usb());
    }

    isValuesView() { return this.view === 'valores' || this.view === 'info'; }

    resetDrafts() {
      const values = this.valuesNow();
      this.drafts = {};
      if (values) this.V.FIELDS.forEach(f => { this.drafts[f.index] = this.V.num(values[f.index]); });
    }

    /** O que o dono digitou que difere do valor lido (só campos válidos). */
    pendingChanges() {
      const values = this.valuesNow();
      const out = { changes: {}, invalid: false, errors: {} };
      this.V.FIELDS.forEach(f => {
        const text = this.drafts[f.index];
        if (text === undefined) return;
        const parsed = this.V.parseInput(text);
        if (!parsed.ok) { out.invalid = true; out.errors[f.index] = parsed.error; return; }
        const check = this.V.validate({ [f.index]: parsed.value });
        if (!check.ok) { out.invalid = true; out.errors[f.index] = `Use um valor ${this.V.range(f).toLowerCase()}.`; return; }
        // Sem snapshot, aceita apenas o campo digitado pelo usuário;
        // o Kotlin relê os outros quatro e valida o plano antes de gravar.
        if (!values || parsed.value !== values[f.index]) out.changes[f.index] = parsed.value;
      });
      return out;
    }

    diff(target) {
      const values = this.valuesNow();
      const out = {};
      for (let i = 0; i < this.V.COUNT; i++) { if (!values || target[i] !== values[i]) out[i] = target[i]; }   // os 5 números, mesmo com 1 só à vista: o Desfazer da fábrica devolve todos
      return out;
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
      // Enquanto o teclado está aberto, o cartão não é reconstruído.
      // Ainda assim, a confirmação real da ECU precisa aparecer imediatamente.
      const status = this.host.querySelector('.vv-status');
      if (status) {
        const { level, text } = this.valuesStatus();
        if (status.dataset.level !== level) status.dataset.level = level;
        if (status.textContent !== text) status.textContent = text;
      }
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
      if (this.saveAgain) {
        const ph = this.vals.view().phase;
        // Só regrava o toque enfileirado se a gravação anterior TERMINOU BEM; falha ou erro nunca vira nova gravação sem toque.
        if (ph === 'error' || ph === 'partial') this.saveAgain = false;
        else if (!['preparing', 'writing'].includes(ph) && !this.flagBusy()) this.autoSave();
      }
      const active = document.activeElement;
      if (this.isValuesView() && active && this.host.contains(active) && active.tagName === 'INPUT') { this.paintFields(); return; }
      // Um fluxo que falha não pode derrubar a aba: a assinatura usa um estado neutro e só o cartão dele cai (barreira por cartão).
      const safe = flow => { try { return flow.view(); } catch (_) { return { phase: 'falhou', message: '', undo: null }; } };
      const fv = safe(this.flag);
      const vv = safe(this.vals);
      // Não substitui botões sob o dedo enquanto uma escrita está em curso.
      // Preserva toques rápidos e mantém status/erros atualizados sem recriar a tela.
      if (this.view === 'valores' && this.host.querySelector('.vv-grid') &&
          ['preparing', 'writing'].includes(vv.phase)) {
        this.paintFields();
        return;
      }
      // Volta: a releitura da central chega pela ponte junto com o fim da gravação; relê aqui para a tela nunca mostrar o valor velho.
      if (this.view === 'volta') { this.loadData(); this.voltaSettle(); }
      const signature = JSON.stringify([this.view, this.data, this.usb(), this.hasBridge(), fv.phase, fv.message, vv.phase, vv.message, vv.undo, this.isValuesView() ? [this.drafts, this.infoTab] : null,
        this.view === 'volta' ? [this.voltaStatus(), this.voltaDrafts, this.voltaRefused] : null]);
      if (signature === this.signature && this.host.childElementCount) return;
      this.signature = signature;
      this.cardFailed = false;
      try {
        let cards;
        let usingUnknown = false;
        try { cards = this.M.build(this.data.params); }
        catch (error) {
          console.warn('[OMEGAS Ferramentas] Leitura inválida, mostrando estado desconhecido', error);
          cards = this.M.build({}); // Degrada apenas a leitura, nunca escreve nem presume desligado.
          usingUnknown = true;
        }
        let next;
        try {
          next = this.view === 'home' ? this.renderHome(cards)
            : this.view === 'valores' ? this.renderValues(cards)
            : this.view === 'info' ? this.renderInfo()
            : this.view === 'volta' && this.VG ? this.renderVolta(cards.find(c => c.id === 'volta'))
            : this.renderReadOnly(this.view, cards);
        } catch (error) {
          if (this.view === 'home') throw error;
          // Uma tela interna que não abre não pode prender o dono nela: volta aos cartões (que têm barreira própria).
          console.error('[OMEGAS Ferramentas] Esta parte não abriu; voltando aos cartões', error);
          this.view = 'home';
          this.setView();
          next = this.renderHome(cards);
        }
        if (this.host.innerHTML !== next) this.host.innerHTML = next;
        if (this.isValuesView()) this.paintFields();
        // Leitura degradada ou cartão que falhou: o próximo ciclo de dados tenta de novo sozinho (sem toque, sem relógio novo).
        if (usingUnknown || this.cardFailed) this.signature = '';
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

    /** Interruptor do anti-apagão: o mesmo cartão na tela inicial e na página do grupo. Um toque = a ordem; tudo na linha do cartão. */
    switchCardHtml(card, extra = '') {
      const fv = this.flag.view();
      const busy = ['preparing', 'writing'].includes(fv.phase);
      const why = this.reason('flag');
      // Todo resultado (ok, "já está ligado", recusa, cabo solto, incerto) vai na linha do próprio cartão: sem janela, sem 2º toque.
      const failed = fv.phase === 'error' && fv.message ? fv.message : '';
      const note = busy ? 'Gravando e conferindo…' : (['idle', 'done'].includes(fv.phase) && fv.message ? fv.message : '');
      const line = failed || why || note || (card.on === null ? 'Ainda não lido. Toque para conferir e ligar.' : card.line);
      const shortLine = failed || why || note || (card.on === null ? 'Ainda não lido. Toque para conferir e ligar.' : (card.shortLine || card.line));
      const state = card.on === null ? 'unknown' : card.on ? 'on' : 'off';
      const action = card.on === null ? 'Conferir e ligar' : card.on ? 'Desligar anti-apagão' : 'Ligar anti-apagão';
      return `
        <article class="tool-tile${extra}" data-card="${card.id}" data-item="${card.id}" data-kind="switch" data-state="${state}" data-actionable="${!why && !busy}">
          <h3>${escapeHtml(card.title)}</h3>
          <p class="tile-line${why && !failed ? ' reason' : ''}"${failed ? ' data-level="warn" role="status"' : ''}><span class="tile-full">${escapeHtml(line)}</span><span class="tile-compact">${escapeHtml(shortLine)}</span></p>
          ${extra && 'effect' in card ? this.effectHtml(card.effect) : ''}
          <button type="button" class="tool-switch" role="${card.on === null ? 'button' : 'switch'}" ${card.on === null ? '' : 'aria-checked="' + (card.on ? 'true' : 'false') + '"'} aria-label="${escapeHtml(action)}. Estado atual: ${escapeHtml(card.stateText)}" data-adv-switch ${why || busy ? 'disabled' : ''}>
            <span class="sw-track" aria-hidden="true"><i></i></span><span class="sw-caption"><b>${escapeHtml(card.stateText)}</b><small>${escapeHtml(action)}</small></span>
          </button>
          ${fv.undo.available && ['done', 'idle'].includes(fv.phase) ? '<button type="button" class="tile-undo" data-adv-undo-flag>Desfazer</button>' : ''}
        </article>`;
    }

    renderHome(cards) {
      // Grupo sem nada provado para mostrar não aparece (dono, 2026-10-20).
      const empty = id => id === 'lenta' && this.G && !this.G.readCards('lenta', this.data.params).length;
      const tiles = cards.filter(card => !empty(card.id)).map(card => {
        try {
        if (card.kind === 'switch') return this.switchCardHtml(card);
        const go = card.kind === 'values' ? `Mudar valores ${PENCIL}` : 'Ver ›';
        // Falha, parcial ou incerto dos 5 números continua à vista no cartão, mesmo fora da tela de valores.
        const vv = card.kind === 'values' ? this.vals.view() : null;
        const said = vv && ['error', 'partial'].includes(vv.phase) && vv.message ? vv.message : '';
        return `
        <button type="button" class="tool-tile tool-open" data-card="${card.id}" data-adv-open="${card.id}" data-state="${card.on === null ? (card.stateText === '—' ? 'unknown' : 'neutral') : card.on ? 'on' : 'off'}">
          <h3>${escapeHtml(card.title)}</h3>
          <p class="tile-line"${said ? ' data-level="warn" role="status"' : ''}><span class="tile-full">${escapeHtml(said || card.line)}</span><span class="tile-compact">${escapeHtml(said || card.shortLine || card.line)}</span></p>
          <span class="tile-state">${escapeHtml(card.stateText)}</span>
          <span class="tile-go">${go}</span>
        </button>`;
        } catch (error) {
          console.error('[OMEGAS Ferramentas] Um cartão não abriu', error);
          this.cardFailed = true;
          return this.errorCard(card?.id || 'parte');
        }
      });
      // 8º cartão (o lugar vazio da grade): as configurações que o app lê e ainda não muda, todas numa página.
      if (this.G && this.G.readCards('outras', this.data.params).length) tiles.push(`
        <button type="button" class="tool-tile tool-open" data-card="outras" data-adv-open="outras" data-state="neutral">
          <h3>Outras configurações</h3>
          <p class="tile-line"><span class="tile-full">OBD, Start &amp; Stop, Landi Connect e mais: o que é cada um.</span><span class="tile-compact">OBD, Start &amp; Stop e mais.</span></p>
          <span class="tile-state">Só leitura</span>
          <span class="tile-go">Ver ›</span>
        </button>`);
      return tiles.join('');
    }

    /** Card de um ajuste: − / + gravam na hora. Todos os cinco ficam expostos, cada um no seu card. */
    cardHtml(f, values) {
      return `
        <div class="num-field vv-card grp-card" data-kind="edit" data-item="av-${f.index}" data-field="${f.index}" data-invalid="false" data-changed="false" data-main="${f.index === 0}">
          <label for="advField${f.index}">${escapeHtml(f.label)}</label>
          <div class="num-stepper">
            <button type="button" class="step-btn" data-adv-step="${f.index}:-1" aria-label="Diminuir ${f.step} ${escapeHtml(f.unit)}" ${values ? '' : 'disabled'}>− ${f.step}</button>
            <div class="num-box">
              <input id="advField${f.index}" type="text" inputmode="none" data-kbd="numeric" pattern="[0-9.]*" autocomplete="off" data-adv-field="${f.index}" value="${escapeHtml(this.drafts[f.index] ?? (values ? this.V.num(values[f.index]) : ''))}" ${this.reason('values') ? 'disabled' : ''} aria-describedby="advHelp${f.index}">
              ${PENCIL}
              <span class="num-unit">${escapeHtml(f.unit)}</span>
            </div>
            <button type="button" class="step-btn" data-adv-step="${f.index}:1" aria-label="Aumentar ${f.step} ${escapeHtml(f.unit)}" ${values ? '' : 'disabled'}>+ ${f.step}</button>
          </div>
          <p class="num-help" id="advHelp${f.index}">${escapeHtml(f.what || f.short)}${f.unit === 'ms' ? ' <span class="num-seconds"></span>' : ''}</p>
          ${this.effectHtml(f.effect)}
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
      // Parcial e incerto NUNCA viram "gravado" nem "não gravado": a frase do fluxo já diz o que houve e o que fazer.
      if (vv.phase === 'partial' || (vv.phase === 'error' && vv.uncertain)) return ['warn', vv.message || ''];
      if (vv.phase === 'error') {
        const msg = vv.message || '';
        return ['bad', /Nada foi gravado|continuam como estavam/.test(msg) ? msg : 'Não gravou. ' + msg];
      }
      if (vv.message) return ['info', vv.message];   // ex.: "já estão assim": linha de estado, sem janela
      return ['idle', 'Toque em − ou +: grava na hora.'];
    }

    valuesStatus() {
      const values = this.valuesNow();
      const why = this.reason('values');
      const [level, message] = this.saveStatus();
      const text = !values && !why && this.vals.view().phase === 'idle'
        ? 'Digite um número. A central será lida antes da gravação; os botões −/+ aguardam a leitura.'
        : (why || message);
      return { level, text };
    }

    /** Casca comum das páginas de grupo (modelo da tela "Valores do anti-apagão"): Voltar + título + linha de estado; grade de cartões
     *  (rola só na vertical); rodapé de botões juntos à esquerda. */
    groupPage({ group, cls = '', dataCard, title, status, cards, gridCls = '', actions = '' }) {
      const [level, text] = status || ['idle', ''];
      return `
        <section class="tool-view grp-view ${cls}" data-group="${group}" data-card="${dataCard || group}">
          <header class="grp-head"><button type="button" class="secondary tool-back" data-adv-back><span aria-hidden="true">‹</span> Voltar</button><h3>${escapeHtml(title)}</h3><p class="grp-status vv-status" data-level="${level}" role="status">${escapeHtml(text)}</p></header>
          <div class="grp-grid ${gridCls}">${cards}</div>
          ${actions.trim() ? `<div class="btn-bar grp-actions">${actions}</div>` : ''}
        </section>`;
    }

    /** Linha 2 de todo cartão: o EFEITO de mudar. Sem efeito provado o cartão nem existe (dono, 2026-10-20), então nunca sai vazio. */
    effectHtml(effect) { return effect ? `<p class="grp-effect">${escapeHtml(effect)}</p>` : ''; }

    /** Cartão SÓ LEITURA: nome, valor lido ("—" se não lido), dica numa linha; sem − / + e sem lápis. */
    readCardHtml(c) {
      return `
        <article class="grp-card grp-read" data-kind="read" data-item="${escapeHtml(c.id)}" data-state="${c.on === null || c.on === undefined ? 'neutral' : c.on ? 'on' : 'off'}">
          <h4 class="grp-label">${escapeHtml(c.label)}</h4>
          <p class="grp-line"><b class="grp-val${c.small ? ' grp-val-text' : ''}">${escapeHtml(c.value)}</b>${c.unit ? ` <span class="grp-unit">${escapeHtml(c.unit)}</span>` : ''}</p>
          <p class="grp-hint">${escapeHtml(c.hint || '')}</p>
          ${this.effectHtml(c.effect)}
          ${c.range ? `<p class="grp-range">${escapeHtml(c.range)}</p>` : ''}
          <span class="grp-ro">Só leitura</span>
        </article>`;
    }

    /** Grupo Anti-apagão: o interruptor + os 5 ajustes + a última viagem, tudo numa página. − / + gravam na hora. */
    renderValues(cards) {
      const values = this.valuesNow();
      const why = this.reason('values');
      const undo = this.vals.view().undo;
      const { level, text: displayStatus } = this.valuesStatus();
      const sug = this.data.antiStallSuggestion || {};
      const anti = (cards || []).find(c => c.id === 'anti');
      return this.groupPage({
        group: 'valores', cls: 'values-view', dataCard: 'valores', title: 'Anti-apagão', status: [level, displayStatus], gridCls: 'vv-grid',
        cards: `${anti ? this.switchCardHtml(anti, ' grp-card grp-switch') : ''}
            ${this.V.FIELDS.map(f => this.cardHtml(f, values)).join('')}
            <aside class="vv-info vv-sug grp-card" data-kind="info" data-item="viagem" data-state="neutral" aria-label="Análise da viagem">
              <p class="vv-sug-title"><b>${Number.isInteger(sug.quedas) ? `${sug.quedas} quedas fortes na viagem` : 'Sua última viagem'}</b></p>
              <p class="vv-sug-short">Ainda sem novo ajuste.</p>
              <p class="vv-sug-why">${escapeHtml(sug.motivo || 'Ao encerrar uma viagem, o app analisa as quedas do motor. Ainda não há dados para sugerir números.')}</p>
              <button type="button" class="secondary" data-adv-open-info-tab="carro"><span class="vv-t-long">Ver os números do seu carro</span><span class="vv-t-short">Ver os números</span></button>
            </aside>`,
        actions: `<button type="button" class="secondary" data-adv-factory ${why || !values || values.every((v, i) => v === this.V.FACTORY[i]) ? 'disabled' : ''}>Restaurar fábrica</button>
            ${undo.available ? '<button type="button" class="secondary" data-adv-undo-values>Desfazer</button>' : ''}
            <button type="button" class="secondary" data-adv-open-view="info">Como funciona</button>`,
      });
    }

    /** Os números do carro do dono, tirados dos logs reais (fonte: docs/progbase/bins/sugestao-antiapagao-carro-do-dono.json). */
    carroHtml() {
      const analysis = this.data.antiStallSuggestion || {};
      return `<p><b>${escapeHtml(analysis.motivo || 'Ainda não há uma viagem analisada neste aparelho.')}</b></p>
        <p>A análise é refeita ao encerrar cada viagem. O app guarda as quedas do motor e os ajustes que conseguiu ler.</p>
        <p>As sessões antigas não permitem confirmar se o carro estava andando nem medir gasolina economizada.</p>
        <p>Nenhum número é gravado sozinho.</p>`;
    }

    /** Explicação completa: leitura, em abas curtas (cabe sem rolar). */
    renderInfo() {
      const values = this.valuesNow();
      const lim = values ? this.V.num(values[0]) : '—';
      const tab = this.infoTab || 'como';
      const tabList = [['como', 'Como funciona'], ...this.V.FIELDS.map(f => ['f' + f.index, f.tab]), ['carro', 'Seu carro'], ['nao', 'NÃO é']];
      const tabs = tabList.map(([id, nome]) => `<button type="button" class="vv-tab" data-adv-info-tab="${id}" aria-pressed="${id === tab}">${escapeHtml(nome)}</button>`).join('');
      const field = /^f\d$/.test(tab) ? this.V.FIELDS[Number(tab[1])] : null;
      const body = field
        ? `<h4>${escapeHtml(field.label)}</h4><p>${escapeHtml(field.help)}</p><ul class="vv-hints"><li><b>Se precisar de mais:</b> ${escapeHtml(field.tipUp)}</li><li><b>Se atrapalhar:</b> ${escapeHtml(field.tipDown)}</li></ul><p class="vv-meta">${escapeHtml(this.V.range(field))} · Valor de fábrica: ${escapeHtml(this.V.num(field.factory))}</p>`
        : tab === 'carro'
          ? this.carroHtml()
        : tab === 'nao'
          ? `<p><b>Não é o corte de injeção</b> (soltar o acelerador em desaceleração): o corte tem ajustes próprios no cartão <b>Saída do corte</b>.</p><p>Os dois se encontram: no seu log, depois de cada corte, com o anti-apagão <b>desligado</b> a rotação afundou até 447 rpm (26 vezes abaixo de 600); <b>ligado</b>, nunca passou de 680 rpm.</p><p>O programa original mostra só este número.</p>`
          : `<p class="vv-how"><b>A história:</b> ao pisar na embreagem para parar, a rotação cai de repente. No gás, o motor pode apagar aí. Se isso acontece abaixo de ${escapeHtml(lim)} rpm, o anti-apagão percebe a queda e segura o motor até ela acalmar.</p><p><b>Rotação máxima para agir</b> (${escapeHtml(lim)} rpm): só age abaixo destes giros. Acelerando normal, não faz nada.</p><p><b>Já provado:</b> no seu log, ligado, os giros nunca caíram abaixo de 600 depois do corte; desligado, caíram até 447.</p>`;
      return `
        <section class="tool-view values-view" data-card="valores-info">
          <header><button type="button" class="secondary tool-back" data-adv-back-valores><span aria-hidden="true">‹</span> Voltar</button><h3>Como o anti-apagão funciona</h3></header>
          <nav class="vv-tabs" aria-label="Assunto">${tabs}</nav>
          <div class="vv-info-full">${body}</div>
        </section>`;
    }

    // ---------- Volta para gasolina (pressão baixa do gás) ----------
    voltaBridge(name) { const b = root.OmegasAutoCal; return !!b && typeof b[name] === 'function'; }

    /** Status do gravador nativo (nunca derruba a tela). */
    voltaStatus() {
      if (!this.voltaBridge('getVoltaGasolinaStatus')) return {};
      try { return JSON.parse(root.OmegasAutoCal.getVoltaGasolinaStatus()) || {}; } catch (_) { return {}; }
    }

    /** Tela aberta sem o tempo/tabela lidos: pede a leitura direta (só lê; repete a cada 3 ciclos do agendador, sem relógio novo). */
    voltaReadIfMissing(bridge) {
      if (this.view !== 'volta' || !this.VG || !this.usb() || !this.voltaBridge('readVoltaGasolina')) return;
      const m = this.VG.read(this.data.params);
      if (m.tempo !== null && m.rif) return;
      this.voltaReadTick = (this.voltaReadTick || 0) + 1;
      if (this.voltaReadAsked && this.voltaReadTick % 3) return;
      this.voltaReadAsked = true;
      try { JSON.parse(bridge.readVoltaGasolina()); } catch (error) { console.warn('[OMEGAS Ferramentas] Leitura da volta indisponível', error); }
    }

    voltaWhy() {
      if (!this.voltaBridge('applyVoltaGasolina')) return WHY.nobridge;
      if (!this.usb()) return WHY.nousb;
      return '';
    }

    voltaSend(request) {
      if (!request || this.voltaWhy()) return;
      if (this.voltaStatus().busy === true) return;   // um toque por vez: o anterior ainda está gravando
      try {
        const sent = JSON.parse(root.OmegasAutoCal.applyVoltaGasolina(JSON.stringify(request)));
        this.voltaRefused = sent && sent.ok === false ? (sent.error || 'Não foi possível. Tente de novo.') : '';
      } catch (_) { this.voltaRefused = 'Não foi possível falar com a central. Tente de novo.'; }
    }

    voltaTap(t) {
      this.voltaRefused = '';
      if (t.matches('[data-volta-step]')) { this.voltaStep(t.dataset.voltaStep); return; }
      this.voltaDrafts = {};
      this.voltaAgain = false;
      if (t.matches('[data-volta-toggle]')) {
        const m = this.VG.read(this.data.params);
        this.voltaSend({ acao: m.on ? 'desligar' : 'ligar' });
      } else if (t.matches('[data-volta-undo]')) this.voltaSend({ acao: 'desfazer' });
    }

    /** − / + da volta: um toque = a ordem (sem "Salvar"). Toques durante uma gravação somam e vão em seguida (por evento, sem relógio). */
    voltaStep(spec) {
      const [key, d] = String(spec).split(':');
      const dir = Number(d) < 0 ? -1 : 1;
      const VG = this.VG;
      const m = VG.read(this.data.params);
      const draft = this.voltaDrafts[key];
      let next;
      if (key === 'mbar') {
        const base = Number.isInteger(draft) ? draft : m.limitMbar;
        if (!Number.isFinite(base)) return;
        next = VG.step(base, dir);
      } else if (key === 'segundos') {
        const base = Number.isInteger(draft) ? draft : m.tempo;
        if (!Number.isInteger(base) || base === VG.OFF) return;
        next = Math.max(0, Math.min(VG.TEMPO_MAX, base + dir));
      } else return;
      this.voltaDrafts = { ...this.voltaDrafts, [key]: next };
      this.voltaFlush();
    }

    voltaFlush() {
      if (this.voltaStatus().busy === true) { this.voltaAgain = true; return; }
      this.voltaAgain = false;
      const request = this.VG.saveRequest(this.VG.read(this.data.params), this.voltaDrafts);
      if (!request) { this.voltaDrafts = {}; return; }
      this.voltaSend(request);
      this.voltaSent = true;
      if (this.voltaRefused) { this.voltaDrafts = {}; this.voltaSent = false; }
    }

    /** A cada ciclo da tela: terminou a gravação? manda o toque que esperava; senão volta a mostrar o que a central confirmou. */
    voltaSettle() {
      if (!this.VG || this.voltaStatus().busy === true) return;
      if (this.voltaAgain) { this.voltaFlush(); return; }
      if (this.voltaSent) { this.voltaSent = false; this.voltaDrafts = {}; }
    }

    stepperHtml({ item, label, value, unit, call, minus, plus, offMinus, offPlus, hint, what, effect }) {
      return `
        <div class="grp-card grp-edit" data-kind="edit" data-item="${item}">
          <h4 class="grp-label">${escapeHtml(label)}</h4>
          <div class="num-stepper">
            <button type="button" class="step-btn" data-volta-step="${call}:-1" aria-label="Diminuir ${escapeHtml(minus)}" ${offMinus ? 'disabled' : ''}>− ${escapeHtml(minus)}</button>
            <div class="num-box grp-box"><b class="grp-val">${escapeHtml(value)}</b>${unit ? `<span class="num-unit">${escapeHtml(unit)}</span>` : ''}</div>
            <button type="button" class="step-btn" data-volta-step="${call}:1" aria-label="Aumentar ${escapeHtml(plus)}" ${offPlus ? 'disabled' : ''}>+ ${escapeHtml(plus)}</button>
          </div>
          ${what ? `<p class="grp-hint">${escapeHtml(what)}</p>` : ''}
          ${this.effectHtml(effect)}
          <p class="grp-range">${escapeHtml(hint)}</p>
        </div>`;
    }

    /** Grupo Volta para gasolina: ligar/desligar, pressão e espera, cada um no seu cartão; − / + gravam na hora; Desfazer no rodapé. */
    renderVolta(card) {
      if (!card) return '';
      const VG = this.VG;
      const m = VG.read(this.data.params);
      const d = this.voltaDrafts;
      const st = this.voltaStatus();
      const ph = VG.phaseOf(st);
      const busy = ph.phase === 'writing';
      const why = this.voltaWhy();
      const mbar = Number.isInteger(d.mbar) ? d.mbar : m.limitMbar;
      const wait = Number.isInteger(d.segundos) ? d.segundos : m.tempo;
      const level = { writing: 'busy', done: 'ok', info: 'info', error: 'bad' };
      const status = this.voltaRefused ? ['bad', 'Não gravou. ' + this.voltaRefused] : why ? ['idle', why]
        : ph.text ? [level[ph.phase] || 'idle', ph.text] : ['idle', 'Toque em − ou +: grava na hora.'];
      const off = !!why;
      const action = m.on === false ? 'Ligar volta' : 'Desligar volta';
      const tech = card.tech.concat((st.notProven || []).map(x => 'Ainda sem prova: ' + x)).map(t => `<li>${escapeHtml(t)}</li>`).join('');
      const extra = this.G ? this.G.readCards('volta', this.data.params).map(c => this.readCardHtml(c)).join('') : '';
      const waitOff = off || m.on !== true || !Number.isInteger(wait);
      return this.groupPage({
        group: 'volta', cls: 'read-view volta-view', dataCard: 'volta', title: card.viewTitle || card.title, status,
        cards: `
          <article class="tool-tile grp-card grp-switch" data-item="volta-liga" data-kind="switch" data-state="${m.on === null ? 'unknown' : m.on ? 'on' : 'off'}">
            <h3>Volta sozinha para a gasolina</h3>
            <p class="grp-hint volta-say">${escapeHtml(m.on === null && !why ? 'Aguardando a central.' : VG.sentence(m, mbar))}</p>
            ${this.effectHtml(VG.TEXT.liga.effect)}
            <button type="button" class="tool-switch" role="${m.on === null ? 'button' : 'switch'}" ${m.on === null ? '' : `aria-checked="${m.on}"`} aria-label="${escapeHtml(action)}" data-volta-toggle ${off || busy || m.on === null ? 'disabled' : ''}>
              <span class="sw-track" aria-hidden="true"><i></i></span><span class="sw-caption"><b>${escapeHtml(card.stateText)}</b><small>${escapeHtml(action)}</small></span>
            </button>
          </article>
          ${this.stepperHtml({ item: 'volta-mbar', label: 'Pressão do gás que faz voltar', value: mbar === null ? '—' : VG.barText(mbar), unit: 'bar', call: 'mbar', minus: '0,05', plus: '0,05',
            offMinus: off || mbar === null || mbar <= VG.MIN, offPlus: off || mbar === null || mbar >= VG.MAX, hint: `De ${VG.barText(VG.MIN)} a ${VG.barText(VG.MAX)} bar.`,
            what: VG.TEXT.pressao.what, effect: VG.TEXT.pressao.effect })}
          ${this.stepperHtml({ item: 'volta-espera', label: 'Espera antes de voltar', value: m.on === false && !Number.isInteger(d.segundos) ? '—' : VG.waitText(wait), unit: '', call: 'segundos', minus: '1 s', plus: '1 s',
            offMinus: waitOff || wait <= 0, offPlus: waitOff || wait >= VG.TEMPO_MAX, hint: `De "na hora" a ${VG.TEMPO_MAX} s.`,
            what: VG.TEXT.espera.what, effect: VG.TEXT.espera.effect })}
          ${extra}
          <details class="tool-tech grp-tech"><summary>Ver detalhes técnicos</summary><ul>${tech}</ul></details>`,
        actions: st.undo && st.undo.available && !busy ? '<button type="button" class="secondary" data-volta-undo>Desfazer</button>' : '',
      });
    }

    /** Grupos só de leitura (troca, marcha lenta, outras): todos os cartões do assunto numa página; sem − / +. */
    renderReadOnly(group, cards) {
      if (!this.G || !this.G.TITLES[group]) return '';
      const items = this.G.readCards(group, this.data.params);
      const source = { troca: ['troca', 'corte', 'injetores'], lenta: ['lenta'] }[group] || [];
      const tech = source.flatMap(id => ((cards || []).find(c => c.id === id) || { tech: [] }).tech).map(t => `<li>${escapeHtml(t)}</li>`).join('');
      const note = { troca: 'Quando e como o carro passa da gasolina ao gás. Não quer dizer que o gás acabou.', lenta: 'Motor ligado sem acelerar: parado no sinal ou manobrando.', outras: 'Nomes do programa original, traduzidos. O app só lê; ainda não muda.' }[group];
      return this.groupPage({
        group, cls: 'read-view', title: this.G.TITLES[group], status: ['idle', note],
        cards: items.map(c => this.readCardHtml(c)).join('') + (tech ? `<details class="tool-tech grp-tech"><summary>Ver detalhes técnicos</summary><ul>${tech}</ul></details>` : ''),
      });
    }
  }

  ns.AdvancedPanel = AdvancedPanel;
})(typeof window !== 'undefined' ? window : globalThis);
