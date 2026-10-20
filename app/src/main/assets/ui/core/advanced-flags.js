(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  const DATA = ns.AdvancedFlagsData || {};

  // ---- Ferramentas > Avançado (dono, 2026-10-18) ----
  // Leitura em português simples + escrita manual SÓ do bit 0x20 da palavra 0 de FLAG_CONF3 (anti-apagão).
  // Sem relógio: esperar a ECU = esperar a resposta (gancho de dados da bridge). Desconhecido é "—", nunca 0.
  const ANTI_ADDRESS = 250;          // FLAG_CONF3 (SerialCode 0x00FA), 2 × u16
  const ANTI_MASK = 0x0020;          // palavra 0, caixa 'Abilitazione anti-stallo'
  const FLAGS = ['FLAG_CONF1', 'FLAG_CONF2', 'FLAG_CONF3'];
  const DASH = '—';

  function isWord(v) { return typeof v === 'number' && Number.isInteger(v) && v >= 0 && v <= 0xFFFF; }
  function wordsOf(entry) {
    return entry && entry.state === 'OK' && Array.isArray(entry.values) && entry.values.length === 2 && entry.values.every(isWord)
      ? entry.values.slice() : null;
  }
  function valuesOf(entry, count) {
    return entry && entry.state === 'OK' && Array.isArray(entry.values) && entry.values.length === count &&
      entry.values.every(v => typeof v === 'number' && Number.isInteger(v)) ? entry.values.slice() : null;
  }
  function num(v) { return v === null || v === undefined ? DASH : String(v).replace(/\B(?=(\d{3})+(?!\d))/g, '.'); }
  function hex4(v) { return v.toString(16).toUpperCase().padStart(4, '0'); }
  function pair(words) { return `${hex4(words[0])} ${hex4(words[1])}`; }
  function hexBytes(bytes) { return bytes.map(b => b.toString(16).toUpperCase().padStart(2, '0')).join(' '); }

  // ---------- leitura (os cartões usam wordsOf/simpleParams; a tabela de bits fica em `table` para a paridade com o original) ----------
  const SIMPLE = [
    { name: 'GIRI_PER_BENZINA', count: 2, label: 'Rotação para rodar a gasolina', help: 'dois valores; o papel de cada um é desconhecido', unit: 'rpm (inferido pelo nome)' },
    { name: 'NUMERO_INJ_BENZINA_CUTOFF', count: 1, label: 'Injeções a gasolina ao voltar do corte', help: 'quantas injeções antes de voltar ao gás (pelo nome)', unit: 'injeções (inferido)' },
  ];
  function simpleParams(params) {
    return SIMPLE.map(s => {
      const v = valuesOf((params || {})[s.name], s.count);
      return { ...s, cells: v ? v.map(num) : Array(s.count).fill(DASH) };
    });
  }

  // ---------- quadro SetVector ----------
  function frameForVector(address, words) {
    if (!Array.isArray(words) || !words.length || words.length > 2 || !words.every(isWord) || !(address >= 0 && address <= 0xFFFF)) {
      throw new Error('quadro inválido');
    }
    const payload = [];
    for (const w of words) payload.push(w & 0xFF, (w >>> 8) & 0xFF);
    const body = [0x31 + payload.length, address & 0xFF, (address >>> 8) & 0xFF, ...payload];
    return [...body, body.reduce((a, b) => a + b, 0) & 0xFF];
  }
  function wordsFromBytes(b) { return [b[0] | (b[1] << 8), b[2] | (b[3] << 8)]; }

  function planAntiStallo(words, enable) {
    if (!Array.isArray(words) || words.length !== 2 || !words.every(isWord)) {
      return { ok: false, error: 'Valor atual desconhecido: espere a leitura da central.' };
    }
    const after = [enable ? (words[0] | ANTI_MASK) : (words[0] & ~ANTI_MASK & 0xFFFF), words[1]];
    const frame = frameForVector(ANTI_ADDRESS, after);
    return {
      ok: true, enable, before: words.slice(), after, frame, frameHex: hexBytes(frame),
      changedMask: words[0] ^ after[0], noop: words[0] === after[0],
    };
  }
  function verifyReadback(plan, words) {
    if (!plan || !plan.ok) return { ok: false, message: 'Sem plano.' };
    if (!Array.isArray(words) || words.length !== 2 || !words.every(isWord)) return { ok: false, message: 'A leitura de volta não veio completa.' };
    const ok = words[0] === plan.after[0] && words[1] === plan.after[1];
    return { ok, message: ok ? 'ok' : `esperado ${pair(plan.after)}, veio ${pair(words)}` };
  }

  // ---------- fluxo de toque (prévia -> toque duplo -> gravar -> leitura de volta) ----------
  function parse(text) { try { return typeof text === 'string' ? JSON.parse(text) : (text || {}); } catch (_) { return {}; } }

  function createWriteFlow({ bridge, addHook, onChange }) {
    const has = n => typeof bridge[n] === 'function';
    const haveBridge = () => !!bridge && has('getAntiStalloFlagStatus') &&
      (has('applyAntiStalloFlag') || (has('prepareAntiStalloFlag') && has('confirmAntiStalloFlag') && has('cancelAntiStalloFlag')));
    // A ponte é conferida na hora do uso (pode ser injetada depois da tela).
    const s = { phase: 'idle', plan: null, token: null, message: '', undo: null, fromUndo: false, release: null, uncertain: false };
    const changed = () => { if (typeof onChange === 'function') onChange(); };
    const unhook = () => { if (s.release) { s.release(); s.release = null; } };
    const hook = () => { if (!s.release && typeof addHook === 'function') s.release = addHook('fast', poll); };
    const set = (phase, message) => { s.phase = phase; if (message !== undefined) s.message = message; changed(); };
    const fail = (message, uncertain) => { unhook(); s.plan = null; s.token = null; s.uncertain = uncertain === true; set('error', message); };
    // Texto de tela em linguagem simples (o que houve + o que fazer). O texto técnico do nativo (ACK, status 0xCA...) nunca vai à tela.
    const HUMAN = {
      unsure: 'Não sei como ficou na central. Espere a leitura voltar e confira o estado antes de tentar de novo.',
      refused: 'A central não aceitou. Tente de novo.',
      changed: 'O estado mudou na central. Nada foi gravado. Toque de novo para conferir.',
      noLink: 'Não consegui falar com a central. Nada foi gravado. Confira o cabo e tente de novo.',
      usbDrop: 'O cabo soltou durante a gravação. Não sei como ficou. Reconecte e confira o estado.',
      mismatch: 'A conferência do app não bate com a da central. Nada foi gravado.',
    };

    function prepare(enable) {
      // Caminho normal: UMA chamada nativa (ler -> gravar -> reler numa só etapa); o resultado chega no status.
      if (typeof bridge.applyAntiStalloFlag === 'function') {
        const sent = parse(bridge.applyAntiStalloFlag(enable));
        if (sent.ok === false) { fail(sent.error || HUMAN.noLink); return; }
        s.enable = enable; s.message = ''; s.uncertain = false; s.plan = null; s.token = null;
        set('writing', '');
        hook();
        return;
      }
      const reply = parse(bridge.prepareAntiStalloFlag(enable));
      if (reply.ok === false) { fail(reply.error || HUMAN.noLink); return; }
      s.enable = enable;
      s.message = '';
      s.uncertain = false;
      set('preparing');
      hook();
    }
    function confirm() {
      const reply = parse(bridge.confirmAntiStalloFlag(s.token));
      if (reply.ok === false) { fail(reply.error || HUMAN.noLink); return; }
      set('writing', '');
      hook();
    }
    function onReady(status) {
      const plan = planAntiStallo(status.before, s.enable);
      if (status.enable !== s.enable || !plan.ok || plan.frameHex !== status.frameHex || !status.after ||
          plan.after[0] !== status.after[0] || plan.after[1] !== status.after[1]) {
        bridge.cancelAntiStalloFlag();
        fail(HUMAN.mismatch);
        return;
      }
      unhook();
      if (plan.noop) {
        bridge.cancelAntiStalloFlag();
        s.plan = null;
        set('idle', `O anti-apagão já está ${s.enable ? 'ligado' : 'desligado'}. Nada a gravar.`);
        return;
      }
      // O toque no interruptor JÁ é a ordem (dono: sem pergunta de confirmação). A conferência do plano acima continua valendo.
      s.plan = plan;
      s.token = status.token;
      confirm();
    }
    function onDone(status) {
      unhook();
      // Na chamada única não houve prévia no JS: o plano vem do que a central leu e do quadro que o nativo montou.
      const plan = s.plan || planAntiStallo(status.before, s.enable);
      if (status.outcome === 'NOTHING_TO_DO') {
        s.plan = null; s.token = null;
        set('idle', `O anti-apagão já está ${s.enable ? 'ligado' : 'desligado'}. Nada a gravar.`);
        return;
      }

      if (status.outcome === 'CONFIRMED' || status.outcome === 'READBACK_DIVERGED') {
        const check = verifyReadback(plan, status.readback);
        if (status.outcome === 'CONFIRMED' && check.ok) {
          s.undo = status.undo && status.undo.available ? { available: true, enable: status.undo.enable } : null;
          s.plan = null; s.token = null;
          set('done', `Pronto. Anti-apagão ${plan.enable ? 'ligado' : 'desligado'} e conferido na central.`);
          return;
        }
        fail(HUMAN.unsure, true);
        return;
      }
      // Sem ACK com o valor intacto = recusa limpa; leitura de volta falha ou valor mudado = o app não sabe o que a ECU tem.
      if (status.outcome === 'READBACK_FAILED' || status.valueChanged === true) {
        fail(HUMAN.unsure, true);
        return;
      }
      fail(status.outcome === 'CHANGED_MEANWHILE' ? HUMAN.changed : status.outcome === 'NOT_ACKED' ? HUMAN.refused : HUMAN.noLink);
    }
    function poll() {
      let status;
      try { status = parse(bridge.getAntiStalloFlagStatus()); }
      catch (_) { if (s.phase === 'writing') fail(HUMAN.unsure, true); else if (s.phase === 'preparing') fail(HUMAN.noLink); return; }
      if (s.phase === 'preparing') {
        if (status.state === 'READY') onReady(status);
        else if (status.state === 'ERROR' || (status.state === 'DONE' && status.outcome)) fail(HUMAN.noLink);
      } else if (s.phase === 'writing') {
        if (status.state === 'DONE') onDone(status);
        // O nativo deu erro, perdeu o pedido ou a ponte caiu: o quadro pode já ter saído, então é estado incerto, nunca fica preso em "gravando".
        else if (status.state === 'ERROR' || status.state === 'IDLE' || status.ok === false) fail(HUMAN.unsure, true);
      }
    }
    function cancel() {
      if (s.phase !== 'preparing' && s.phase !== 'preview') return;
      unhook();
      bridge.cancelAntiStalloFlag();
      s.plan = null; s.token = null;
      set('idle', '');
    }
    const bitOn = entry => { const w = wordsOf(entry); return w ? (w[0] & ANTI_MASK) !== 0 : null; };

    return {
      view() {
        const preview = s.plan ? { beforeHex: pair(s.plan.before), afterHex: pair(s.plan.after),
          text: `Antes ${pair(s.plan.before)} → depois ${pair(s.plan.after)}. Só o anti-apagão muda; o resto fica igual.` } : null;
        return { phase: s.phase, message: s.message, uncertain: s.uncertain, preview, undo: s.undo ? { available: true, enable: s.undo.enable } : { available: false },
          buttonLabel: s.phase === 'preview' ? 'Toque de novo para gravar' : s.phase === 'preparing' ? 'Lendo a central…' : s.phase === 'writing' ? 'Gravando e conferindo…' : null,
          confirmOn: s.phase === 'preview' ? (s.fromUndo ? 'undo' : 'switch') : null };
      },
      buttonLabel(entry) {
        const v = this.view();
        return v.buttonLabel || (bitOn(entry) === true ? 'Desligar anti-apagão' : 'Ligar anti-apagão');
      },
      canTap(entry, usbConnected) {
        if (!haveBridge() || usbConnected !== true || s.phase === 'preparing' || s.phase === 'writing') return false;
        return s.phase === 'preview' ? !s.fromUndo : true;
      },
      tapSwitch(entry, usbConnected) {
        if (!haveBridge() || usbConnected !== true) return;
        if (s.phase === 'preview') { if (s.fromUndo) cancel(); else confirm(); return; }
        if (s.phase === 'preparing' || s.phase === 'writing') return;
        const on = bitOn(entry);
        s.fromUndo = false;
        // Desconhecido não equivale a "desligado": o Kotlin confere o estado real
        // na ECU e, se já estiver ligado, não emite escrita alguma.
        prepare(on === null ? true : !on);
      },
      tapUndo() {
        if (!haveBridge()) return;
        if (s.phase === 'preview' && s.fromUndo) { confirm(); return; }
        if (s.phase === 'preparing' || s.phase === 'writing' || !s.undo) return;
        s.fromUndo = true;
        prepare(s.undo.enable);
      },
      cancel,
      /** Fecha o aviso de resultado (Pronto / erro / já está assim). O "desfazer" guardado continua. */
      dismiss() { if (s.phase === 'done' || s.phase === 'error') s.phase = 'idle'; if (s.phase === 'idle') { s.message = ''; s.uncertain = false; } changed(); },
      otherTouch() { cancel(); },
      observe(state) {
        const away = state.route !== 'tools';
        const noUsb = state.usbConnected === false;
        if (s.phase === 'writing' && noUsb) { fail(HUMAN.usbDrop, true); return; }
        if (away || noUsb) cancel();
      },
      /** Pega o "desfazer" já guardado no nativo (a tela pode ter sido recriada). */
      adopt() {
        if (!haveBridge() || s.undo) return;
        const st = parse(bridge.getAntiStalloFlagStatus());
        if (st.undo && st.undo.available) { s.undo = { available: true, enable: st.undo.enable }; changed(); }
      },
    };
  }

  ns.AdvancedFlags = {
    table: DATA, FLAGS, ANTI_ADDRESS, ANTI_MASK, simpleParams,
    frameForVector, wordsFromBytes, planAntiStallo, verifyReadback, createWriteFlow, hexBytes, pair, wordsOf,
  };
})(typeof window !== 'undefined' ? window : globalThis);
