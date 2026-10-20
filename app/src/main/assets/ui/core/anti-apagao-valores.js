(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Os 5 números do anti-apagão (ANTI_STALLO, SerialCode 307 = 0x0133, 5 × u16), editáveis pelo dono (2026-10-18).
  // Espelha o Kotlin (AntiStalloParamsCore): mesma faixa, mesmo quadro. Marcas: OBS-COD = faixa MÁXIMA e valor de fábrica do DFM do
  // ProgBase (TFormConfig.EditAntiStallo0..4, TSTREAMDATI.ANTI_STALLO); DERIVADO = piso 0 (u16 sem sinal; o DFM não traz MinValue);
  // INFERIDO (forte) = qual edit é qual posição (DataIndex 0..4). Quadro: SetVector ESTENDIDO 0x37 (5 × u16 = 10 bytes > 5), não 0x3B.
  // Sem relógio: esperar a central = esperar a resposta (gancho de dados da ponte).
  const ADDRESS = 307;
  // Textos para quem nunca mexeu: o que o número faz, para que lado mexer em cada sintoma e o passo dos botões − / +.
  // O original só mostra o 1º número (Rotação máxima, 1600 rpm); o efeito dos outros 4 é INFERIDO (experimental).
  // Rótulos e unidades do ORIGINAL (i18n 963-970): "Max RPM to enable un-stalling" (rpm), "Delta RPM to enable" (rpm/ciclo),
  // "Delay to exit gas mode" (ms), "Hysteresis for enabling threshold" (rpm/ciclo), "Delta RPM to disenable" (rpm/ciclo).
  // Evidência de uso (OBS-FIO, log novo): com a função ligada a rotação pós-corte nunca caiu de 600 rpm (desligada: 26 vezes, mín. 447).
  const FIELDS = [
    { index: 0, tab: 'Rotação máx.', short: 'Só entra abaixo desta rotação.', hintUp: 'Ainda apaga? Aumente.', hintDown: 'Segura gasolina à toa? Diminua.', label: 'Rotação máxima para agir', unit: 'rpm', min: 0, max: 6000, factory: 1600, step: 50,
      help: 'O anti-apagão só entra com o motor ABAIXO desta rotação (parado, marcha lenta, manobra). Acima dela, em aceleração normal, ele não interfere.',
      tipUp: 'Ainda apaga ao pisar na embreagem? Aumente de 50 em 50 e teste.',
      tipDown: 'Segura a gasolina sem precisar? Diminua de 50 em 50.' },
    { index: 1, tab: 'Queda p/ agir', short: 'Velocidade da queda que ativa.', hintUp: 'Sem dado para sugerir.', hintDown: 'Efeito ainda não confirmado.', label: 'Queda de rotação para agir', unit: 'rpm', min: 0, max: 1000, factory: 100, step: 5,
      help: 'Mede a VELOCIDADE da queda: a rapidez com que o motor perde rotação. A escala interna ainda não foi confirmada. Se a rotação cair mais rápido que isso (embreagem, parada, depois de um corte), a proteção entra. Queda lenta não ativa.',
      tipUp: 'Ainda não há dados para recomendar aumento.',
      tipDown: 'Ainda não há dados para recomendar redução.' },
    { index: 2, tab: 'Espera', short: 'Espera para sair do gás.', hintUp: 'Sem dado para sugerir.', hintDown: 'Efeito ainda não confirmado.', label: 'Espera para sair do gás', unit: 'ms', min: 0, max: 10000, factory: 1500, step: 100,
      help: 'Tempo de espera para a central sair do modo gás quando a proteção age (original: "Delay to exit gas mode"). 1.000 ms = 1 segundo.',
      tipUp: 'Ainda não há dados para recomendar aumento.',
      tipDown: 'Ainda não há dados para recomendar redução.' },
    { index: 3, tab: 'Folga', short: 'Evita ligar e desligar à toa.', hintUp: 'Sem dado para sugerir.', hintDown: 'Efeito ainda não confirmado.', label: 'Folga para não ficar trocando', unit: 'rpm', min: 0, max: 1000, factory: 2, step: 5,
      help: 'Margem, medida na mesma escala da queda, para a proteção não ligar e desligar por oscilação pequena perto do limite.',
      tipUp: 'Ainda não há dados para recomendar aumento.',
      tipDown: 'Ainda não há dados para recomendar redução.' },
    { index: 4, tab: 'Queda p/ parar', short: 'Quando a queda acalma, solta.', hintUp: 'Sem dado para sugerir.', hintDown: 'Efeito ainda não confirmado.', label: 'Queda para parar de agir', unit: 'rpm', min: 0, max: 1000, factory: 20, step: 5,
      help: 'Ajuste experimental ligado ao fim da proteção. Ainda não há prova suficiente para orientar a mudança.',
      tipUp: 'Ainda não há dados para recomendar aumento.',
      tipDown: 'Ainda não há dados para recomendar redução.' },
  ];
  const COUNT = FIELDS.length;
  const FACTORY = FIELDS.map(f => f.factory);
  // <SUGESTAO:INICIO> gerado de docs/progbase/bins/sugestao-antiapagao-carro-do-dono.json (tools/sessoes/gerar_sugestao_js.py); um teste confere os dois.
  const SUGESTAO = {"valores": [1600, 100, 1500, 2, 20], "ligadoPior": 680, "desligadoPior": 447, "marcha": 870, "maiorInicioQueda": 1967, "faixaMax": 2000, "gasLigadoPct": 0.8, "gasDesligadoPct": 5.4, "ativMin": 165.1, "ativMax": 198.8, "quase1200": 25, "quase1600": 15, "quase2000": 6, "tabela": [{"m": 1200, "porHora": 170.2, "quase": 25}, {"m": 1400, "porHora": 198.8, "quase": 20}, {"m": 1600, "porHora": 192.3, "quase": 15}, {"m": 1800, "porHora": 179.4, "quase": 11}, {"m": 2000, "porHora": 165.1, "quase": 6}], "horas": 12.3, "gasLigado": {"leituras": 14258, "fora": 118}};
  // <SUGESTAO:FIM>
  const DASH = '—';

  function isU16(v) { return typeof v === 'number' && Number.isInteger(v) && v >= 0 && v <= 0xFFFF; }
  function valuesOf(entry) {
    return entry && entry.state === 'OK' && Array.isArray(entry.values) && entry.values.length === COUNT && entry.values.every(isU16)
      ? entry.values.slice() : null;
  }
  function num(v) { return v === null || v === undefined || Number.isNaN(v) ? DASH : String(v).replace(/\B(?=(\d{3})+(?!\d))/g, '.'); }
  function hexBytes(bytes) { return bytes.map(b => b.toString(16).toUpperCase().padStart(2, '0')).join(' '); }
  function range(field) { return `De ${num(field.min)} a ${num(field.max)}`; }

  /** Texto digitado → inteiro. Aceita "1600" e "1.600"; recusa vazio, decimal, letra e sinal. */
  function parseInput(text) {
    const raw = String(text === null || text === undefined ? '' : text).trim().replace(/\s+/g, '');
    if (raw === '') return { ok: false, error: 'Digite um número.' };
    if (/^\d{1,3}(\.\d{3})+$/.test(raw)) return { ok: true, value: Number(raw.replace(/\./g, '')) };
    if (!/^\d+$/.test(raw)) return { ok: false, error: 'Digite só números inteiros, sem vírgula.' };
    return { ok: true, value: Number(raw) };
  }

  /** `changes` = { índice: valor }. Nunca aceita fora da faixa. */
  function validate(changes) {
    const keys = Object.keys(changes || {});
    if (!keys.length) return { ok: false, error: 'Nenhum número foi informado.' };
    for (const key of keys) {
      const field = /^\d+$/.test(key) ? FIELDS[Number(key)] : null;
      if (!field) return { ok: false, error: 'Esse campo não existe.' };
      const value = changes[key];
      if (typeof value !== 'number' || !Number.isInteger(value)) return { ok: false, error: 'O valor tem que ser um número inteiro.' };
      if (value < field.min || value > field.max) return { ok: false, error: `${field.label}: use um valor ${range(field).toLowerCase()}.` };
    }
    return { ok: true };
  }

  function frameForVector(address, words) {
    if (!Array.isArray(words) || !words.length || !words.every(isU16) || !(address >= 0 && address <= 0xFFFF)) throw new Error('quadro inválido');
    const payload = [];
    for (const w of words) payload.push(w & 0xFF, (w >>> 8) & 0xFF);
    const head = payload.length <= 5
      ? [0x31 + payload.length, address & 0xFF, (address >>> 8) & 0xFF]
      : [0x37, address & 0xFF, payload.length + 2, (address >>> 8) & 0xFF]; // estendido: baixo, n+1, alto (decompilação do original)
    const body = [...head, ...payload];
    return [...body, body.reduce((a, b) => a + b, 0) & 0xFF];
  }

  /** Lê-modifica-escreve: só os campos de `changes` mudam; o resto é o valor lido. */
  function plan(before, changes) {
    if (!Array.isArray(before) || before.length !== COUNT || !before.every(isU16)) return { ok: false, error: 'Valores atuais desconhecidos: espere a leitura da central.' };
    const check = validate(changes);
    if (!check.ok) return check;
    const after = before.slice();
    for (const key of Object.keys(changes)) after[Number(key)] = changes[key];
    const frame = frameForVector(ADDRESS, after);
    const changed = after.map((v, i) => (v !== before[i] ? i : -1)).filter(i => i >= 0);
    return { ok: true, before: before.slice(), after, changed, noop: changed.length === 0, frame, frameHex: hexBytes(frame) };
  }
  function verifyReadback(p, values) {
    if (!p || !p.ok) return { ok: false, message: 'Sem plano.' };
    if (!Array.isArray(values) || values.length !== COUNT || !values.every(isU16)) return { ok: false, message: 'A leitura de volta não veio completa.' };
    const ok = values.every((v, i) => v === p.after[i]);
    return { ok, message: ok ? 'ok' : `esperado ${p.after.join(' ')}, veio ${values.join(' ')}` };
  }
  /** O que mudar para ficar igual à sugestão para o carro do dono (vazio = já está igual). */
  function suggestionChanges(current, analysis) {
    // Observação sem prova causal não autoriza voltar aos valores fixos do corpus.
    // O núcleo nativo ainda não recomenda números; desconhecido permanece sem ação.
    return {};
  }
  function factoryChanges(current) {
    const out = {};
    FIELDS.forEach(f => { if (!current || current[f.index] !== f.factory) out[f.index] = f.factory; });
    return out;
  }
  /** "1.500 ms" → "1,5 s" (só o campo de tempo). */
  function secondsText(ms) {
    if (!isU16(ms)) return DASH;
    return `${(ms / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 2 })} s`;
  }
  /** Fora de ±50% do valor de fábrica (INFERIDO, conservador): só avisa, a faixa dura é a do DFM. */
  function farFromFactory(index, value) {
    const f = FIELDS[index];
    if (!f || !isU16(value)) return false;
    return Math.abs(value - f.factory) > f.factory * 0.5;
  }

  const TEXT = {
    CONFIRMED: 'Pronto. A central aceitou e os números conferem.',
    NOT_ACKED: 'A central não aceitou. Os números continuam como estavam. Tente de novo.',
    UNSURE: 'Não sei como ficou na central. Espere a leitura voltar e confira os números antes de tentar de novo.',
    CHANGED: 'Os números mudaram na central. Nada foi gravado. Toque em Gravar para conferir de novo.',
    NO_LINK: 'Não consegui falar com a central. Nada foi gravado. Confira o cabo e tente de novo.',
    USB_DROP: 'O cabo soltou durante a gravação. Não sei como ficou. Reconecte e confira os números.',
    SAME: 'Esses números já estão assim na central. Nada a gravar.',
    PREVIEW_MISMATCH: 'A conferência do app não bate com a da central. Nada foi gravado.',
  };

  function parse(text) { try { return typeof text === 'string' ? JSON.parse(text) : (text || {}); } catch (_) { return {}; } }

  /**
   * Fluxo de toque dos 5 números: pedir (lê a central e mostra antes → depois) → 2º toque confirma → ACK → leitura de volta → Desfazer.
   * Gravar, Restaurar fábrica e Desfazer usam o MESMO caminho (`request`); nada grava sem o 2º toque.
   */
  function createValuesFlow({ bridge, addHook, onChange }) {
    const NAMES = ['prepareAntiStalloValues', 'confirmAntiStalloValues', 'cancelAntiStalloValues', 'getAntiStalloValuesStatus'];
    const has = n => typeof bridge[n] === 'function';
    const haveBridge = () => !!bridge && has('getAntiStalloValuesStatus') && (has('applyAntiStalloValues') || NAMES.every(has));
    const s = { phase: 'idle', kind: null, changes: null, plan: null, token: null, message: '', level: '', undo: null, release: null, uncertain: false };
    const changed = () => { if (typeof onChange === 'function') onChange(); };
    const unhook = () => { if (s.release) { s.release(); s.release = null; } };
    const hook = () => { if (!s.release && typeof addHook === 'function') s.release = addHook('fast', poll); };
    const set = (phase, message, level) => { s.phase = phase; if (message !== undefined) s.message = message; if (level !== undefined) s.level = level; changed(); };
    const fail = (message, uncertain) => { unhook(); s.plan = null; s.token = null; s.uncertain = uncertain === true; set('error', message, 'warning'); };

    function request(kind, changes) {
      const check = validate(changes);
      if (!check.ok) { set('error', check.error, 'warning'); return false; }
      // Caminho normal: UMA chamada nativa (ler -> gravar -> reler numa só etapa); o resultado chega no status.
      if (typeof bridge.applyAntiStalloValues === 'function') {
        const sent = parse(bridge.applyAntiStalloValues(JSON.stringify(changes)));
        if (sent.ok === false) { fail(sent.error || TEXT.NO_LINK); return false; }
        s.kind = kind; s.changes = changes; s.message = ''; s.uncertain = false; s.plan = null; s.token = null;
        set('writing', '', '');
        hook();
        return true;
      }
      const reply = parse(bridge.prepareAntiStalloValues(JSON.stringify(changes)));
      if (reply.ok === false) { fail(reply.error || TEXT.NO_LINK); return false; }
      s.kind = kind; s.changes = changes; s.message = ''; s.uncertain = false;
      set('preparing', '', '');
      hook();
      return true;
    }
    function confirm() {
      const reply = parse(bridge.confirmAntiStalloValues(s.token));
      if (reply.ok === false) { fail(reply.error || TEXT.NO_LINK); return; }
      set('writing', '', '');
      hook();
    }
    function onReady(status) {
      const mine = plan(status.before, s.changes);
      if (!mine.ok || mine.frameHex !== status.frameHex || !Array.isArray(status.after) || mine.after.some((v, i) => v !== status.after[i])) {
        bridge.cancelAntiStalloValues();
        fail(TEXT.PREVIEW_MISMATCH);
        return;
      }
      unhook();
      if (mine.noop) { bridge.cancelAntiStalloValues(); s.plan = null; set('idle', TEXT.SAME, 'info'); return; }
      // Sem atrito (dono): o toque em Gravar já é a ordem; o plano acima foi conferido contra o quadro calculado aqui.
      s.plan = mine; s.token = status.token;
      confirm();
    }
    function onDone(status) {
      unhook();
      // Na chamada única não houve prévia no JS: o plano vem do que a central leu (before) mais o pedido do toque.
      const mine = s.plan || plan(status.before, s.changes);
      if (status.outcome === 'NOTHING_TO_DO') { s.plan = null; s.token = null; set('idle', TEXT.SAME, 'info'); return; }
      if (status.outcome === 'CONFIRMED' || status.outcome === 'READBACK_DIVERGED') {
        const check = verifyReadback(mine, status.readback);
        if (status.outcome === 'CONFIRMED' && check.ok) {
          s.undo = status.undo && status.undo.available && Array.isArray(status.undo.values) ? { values: status.undo.values.slice() } : null;
          s.plan = null; s.token = null;
          set('done', TEXT.CONFIRMED, 'ok');
          return;
        }
        fail(TEXT.UNSURE, true);
        return;
      }
      if (status.outcome === 'READBACK_FAILED' || status.valueChanged === true) { fail(TEXT.UNSURE, true); return; }
      if (status.outcome === 'CHANGED_MEANWHILE') { fail(TEXT.CHANGED); return; }
      if (status.outcome === 'NOT_ACKED') { fail(TEXT.NOT_ACKED); return; }
      fail(TEXT.NO_LINK);
    }
    function poll() {
      let status;
      try { status = parse(bridge.getAntiStalloValuesStatus()); }
      catch (_) { if (s.phase === 'writing') fail(TEXT.UNSURE, true); else if (s.phase === 'preparing') fail(TEXT.NO_LINK); return; }
      if (s.phase === 'preparing') {
        if (status.state === 'READY') onReady(status);
        else if (status.state === 'ERROR' || (status.state === 'DONE' && status.outcome)) fail(TEXT.NO_LINK);
      } else if (s.phase === 'writing') {
        if (status.state === 'DONE') onDone(status);
        else if (status.state === 'ERROR' || status.state === 'IDLE' || status.ok === false) fail(TEXT.UNSURE, true);
      }
    }
    function cancel() {
      if (s.phase !== 'preparing' && s.phase !== 'preview') return;
      unhook();
      bridge.cancelAntiStalloValues();
      s.plan = null; s.token = null;
      set('idle', '', '');
    }

    return {
      view() {
        return { phase: s.phase, kind: s.kind, message: s.message, level: s.level, uncertain: s.uncertain, plan: s.plan,
          undo: s.undo ? { available: true, values: s.undo.values.slice() } : { available: false } };
      },
      canAsk(usbConnected) { return haveBridge() && usbConnected === true && s.phase !== 'preparing' && s.phase !== 'writing'; },
      /** 1º toque de Gravar / Restaurar fábrica / Desfazer. */
      ask(kind, changes, usbConnected) {
        if (!haveBridge() || usbConnected !== true || s.phase === 'preparing' || s.phase === 'writing') return false;
        if (s.phase === 'preview') cancel();
        return request(kind, changes);
      },
      /** 2º toque. */
      confirm() { if (s.phase === 'preview') confirm(); },
      cancel,
      dismiss() { if (s.phase === 'done' || s.phase === 'error') s.phase = 'idle'; if (s.phase === 'idle') { s.message = ''; s.uncertain = false; } changed(); },
      otherTouch() { cancel(); },
      observe(state) {
        const away = state.route !== 'tools';
        const noUsb = state.usbConnected === false;
        if (s.phase === 'writing' && noUsb) { fail(TEXT.USB_DROP, true); return; }
        if (away || noUsb) cancel();
      },
      /** Pega o "desfazer" já guardado no nativo (a tela pode ter sido recriada). */
      adopt() {
        if (!haveBridge() || s.undo) return;
        const st = parse(bridge.getAntiStalloValuesStatus());
        if (st.undo && st.undo.available && Array.isArray(st.undo.values)) { s.undo = { values: st.undo.values.slice() }; changed(); }
      },
    };
  }

  ns.AntiApagaoValores = {
    ADDRESS, FIELDS, FACTORY, SUGESTAO, suggestionChanges, COUNT, TEXT, valuesOf, parseInput, validate, plan, verifyReadback, factoryChanges, frameForVector,
    secondsText, farFromFactory, range, num, createValuesFlow,
  };
})(typeof window !== 'undefined' ? window : globalThis);
