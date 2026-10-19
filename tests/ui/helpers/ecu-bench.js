'use strict';
// Bancada de toque: ECU em JS (mesmas regras de tools/emulacao/ecu_emulator.py, estado = fixture da captura LOGNOVO) + a
// "ponte nativa" que o Kotlin teria (OmegasNative / OmegasV7 / OmegasAutoCal) falando com ela por QUADROS de verdade.
// Roda no Node (testes de lógica) e dentro da página (Playwright, via addInitScript). Sem relógio: "dado chega" = deliver().
// A bancada REGISTRA todo quadro enviado à ECU (ecu.log); classe WRITE = escrita (0x10..0x17, 0x30..0x37), ACTION = 02 24 ...
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.OmegasE2eBench = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  const ACK = 0x53, NAK = 0xCA;
  const H = bytes => Array.from(bytes, x => (x & 255).toString(16).toUpperCase().padStart(2, '0')).join(' ');
  const B = text => String(text).trim().split(/\s+/).filter(Boolean).map(x => parseInt(x, 16));
  const sum = bytes => bytes.reduce((a, x) => (a + x) & 255, 0);
  const pedido = (op, ...params) => { const c = [op, ...params]; return [...c, sum(c)]; };
  const quadro = (ack, data) => { const c = [ack, data.length, ...data]; return [...c, sum(c)]; };
  const eWrite = f => (f[0] >= 0x10 && f[0] <= 0x17) || (f[0] >= 0x30 && f[0] <= 0x37);
  const u16 = (d, i) => d[i] | (d[i + 1] << 8);
  const s16 = (d, i) => { const v = u16(d, i); return v > 0x7FFF ? v - 0x10000 : v; };
  const words = d => { const out = []; for (let i = 0; i + 1 < d.length; i += 2) out.push(u16(d, i)); if (d.length % 2) out.push(d[d.length - 1]); return out; };
  const clone = v => JSON.parse(JSON.stringify(v));

  function tamanhoPedido(buf) {
    if (!buf.length) return null;
    const low = buf[0] & 7;
    if (low === 7) return buf.length < 3 ? null : buf[2] + 3; // estendido: 37, end. baixo, n+1, end. alto (decompilação do original)
    return low + 3;
  }
  function classify(frame) {
    if (eWrite(frame)) return 'WRITE';
    if (frame[0] === 0x02 && frame[1] === 0x24) return 'ACTION';
    return 'READ';
  }

  // ---------------------------------------------------------------- ECU
  class EcuJs {
    constructor(fixture) {
      this.estado = new Map(Object.entries(fixture.leituras).map(([k, v]) => [k.toUpperCase(), B(v)]));
      this.telemetria = (fixture.telemetria_48_01 || []).map(B);
      this.telIdx = 0;
      this.log = [];           // {n, hex, kind}
      this.acoes = [];         // modos 02 24 04 <modo> recebidos
      this.faults = { noReply: false, noReadReply: false, nak: false, ignoreWrite: false, corruptWrite: false };
      this.n = 0;
    }
    /** Remove falhas e zera o registro (o estado da ECU fica). */
    clearLog() { this.log.length = 0; }
    sent(kind) { return this.log.filter(f => f.kind === kind); }
    get(key) { return this.estado.get(key.toUpperCase()); }
    put(key, data) { this.estado.set(key.toUpperCase(), data.slice()); }

    /** Um quadro de pedido -> bytes que a ECU devolve (eco + ACK/NAK + n + dados + soma). [] = silêncio. */
    transact(frame) {
      frame = Array.from(frame);
      const kind = classify(frame);
      this.log.push({ n: ++this.n, hex: H(frame), kind });
      if (sum(frame.slice(0, -1)) !== frame[frame.length - 1] || tamanhoPedido(frame) !== frame.length) return frame;
      if (kind === 'WRITE') {
        if (this.faults.noReply) return [];
        if (this.faults.nak) return [...frame, ...quadro(NAK, [])];
        if (!this.faults.ignoreWrite) this.applyWrite(frame);
        return [...frame, ...quadro(ACK, [])];
      }
      if (kind === 'ACTION') { this.acoes.push(frame[3]); return this.faults.noReply ? [] : [...frame, ...quadro(ACK, [])]; }
      if (frame[0] === 0x48 && frame[1] === 0x01) {
        if (!this.telemetria.length) return frame;
        const data = this.telemetria[this.telIdx++ % this.telemetria.length];
        return [...frame, ...quadro(ACK, data)];
      }
      // Falha de leitura independente da falha de escrita: não transformar
      // testes de ACK/NAK de gravação em falhas de preparação da tela.
      if (this.faults.noReadReply) return [];
      const data = this.get(H(frame));
      return data ? [...frame, ...quadro(ACK, data)] : frame;
    }

    applyWrite(p) {
      const op = p[0];
      let aLo, aHi, corpo;
      if ((op & 7) === 7) { aLo = p[1]; aHi = p[3]; corpo = p.slice(4, -1); } else { aLo = p[1]; aHi = p[2]; corpo = p.slice(3, -1); }
      if (this.faults.corruptWrite) corpo = corpo.map((b, i) => (i === corpo.length - 1 ? b ^ 0x01 : b));
      const kVet = H(pedido(0x29, aLo, aHi));
      if (op >= 0x30 && op <= 0x37) {
        this.put(kVet, corpo);
        for (let idx = 0; idx < 256; idx += 1) {
          const k = H(pedido(0x0A, aLo, aHi, idx));
          const d = this.get(k);
          if (d && (idx + 1) * d.length <= corpo.length) this.put(k, corpo.slice(idx * d.length, (idx + 1) * d.length));
        }
        return;
      }
      const kEsc = H(pedido(0x09, aLo, aHi));
      const dEsc = this.get(kEsc);
      if (dEsc && dEsc.length === corpo.length) { this.put(kEsc, corpo); return; }
      if (corpo.length >= 3) {                       // matriz: linha, coluna, valor
        const kLin = H(pedido(0x2A, aLo, aHi, corpo[0]));
        const d = this.get(kLin);
        const s = corpo.length - 2;
        if (d && (corpo[1] + 1) * s <= d.length) {
          const novo = d.slice();
          corpo.slice(2).forEach((b, i) => { novo[corpo[1] * s + i] = b; });
          this.put(kLin, novo);
          return;
        }
      }
      if (corpo.length >= 2) {                       // vetor: índice, valor
        const d = this.get(kVet);
        const s = corpo.length - 1;
        if (d && (corpo[0] + 1) * s <= d.length) {
          const novo = d.slice();
          corpo.slice(1).forEach((b, i) => { novo[corpo[0] * s + i] = b; });
          this.put(kVet, novo);
          const kIdx = H(pedido(0x0A, aLo, aHi, corpo[0]));
          if (this.get(kIdx)) this.put(kIdx, corpo.slice(1));
          return;
        }
      }
      if (!dEsc && [1, 2, 4].includes(corpo.length)) this.put(kEsc, corpo);
    }
  }

  // ---------------------------------------------------------------- ponte nativa (o que o Kotlin faria)
  const PETROL_BINS = [2, 2.5, 3, 3.5, 4.5, 6, 8, 10, 12, 14, 16, 18];
  const RPM_BINS = [850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500];
  const ANTI_ADDR = 0xFA;
  const ADV = [
    ['FLAG_CONF1', pedido(0x29, 0x03, 0x00)], ['FLAG_CONF2', pedido(0x29, 0xC0, 0x00)], ['FLAG_CONF3', pedido(0x29, ANTI_ADDR, 0x00)],
    ['ANTI_STALLO', pedido(0x29, 0x33, 0x01)], ['GIRI_PER_BENZINA', pedido(0x29, 0xA1, 0x00)], ['NUMERO_INJ_BENZINA_CUTOFF', pedido(0x09, 0x6A, 0x00)],
    ['PARAM_PROGRESSIONI_L0', pedido(0x2A, 0x34, 0x01, 0)], ['PARAM_PROGRESSIONI_L1', pedido(0x2A, 0x34, 0x01, 1)],
    ['PARAM_PROGRESSIONI_L2', pedido(0x2A, 0x34, 0x01, 2)], ['PARAM_PROGRESSIONI_L3', pedido(0x2A, 0x34, 0x01, 3)],
  ];

  function createBench(fixture, options = {}) {
    const ecu = new EcuJs(fixture);
    const bench = {
      ecu, usb: false, session: 0, seq: 0, live: null, advRead: false, params: {}, scalars: {},
      hold: options.hold === true, held: null, antiPending: null, antiStatus: { state: 'IDLE' }, antiUndo: null, antiUndoSession: 0,
      actionPrepared: null, actionStatus: {}, prepN: 0, mapOp: { ok: true, state: 'IDLE', busy: false }, curveOp: { ok: true, state: 'IDLE', busy: false },
      mapRead: { ok: true, state: 'IDLE', busy: false }, opN: 0,
    };
    const send = frame => ecu.transact(frame);
    const decodeReply = (frame, reply) => {
      if (reply.length < frame.length + 3 || H(reply.slice(0, frame.length)) !== H(frame)) return { ok: false, status: -1, payload: [] };
      const body = reply.slice(frame.length);
      const n = body[1];
      if (body.length !== n + 3 || sum(body.slice(0, -1)) !== body[body.length - 1]) return { ok: false, status: body[0], payload: [] };
      return { ok: body[0] === ACK, status: body[0], payload: body.slice(2, 2 + n) };
    };
    const read = frame => decodeReply(frame, send(frame));

    // ---- USB e dado (eventos)
    bench.plugUsb = () => { bench.usb = true; bench.session += 1; bench.advRead = false; bench.params = {}; bench.live = null; bench.seq = 0; };
    bench.unplugUsb = () => { bench.usb = false; };
    /** Chegou um quadro de telemetria (o laço contínuo da serial). Na 1ª leitura válida da sessão: lê os parâmetros (por evento). */
    bench.deliver = () => {
      if (!bench.usb) return false;
      const frame = pedido(0x48, 0x01);
      const reply = read(frame);
      if (!reply.ok || reply.payload.length < 20) return false;
      const d = reply.payload;
      const fuelByte = d[11];
      bench.seq += 1;
      bench.live = {
        rpm: u16(d, 0), petrol_ms: u16(d, 8) * 0.00256, gas_ms_diagnostic: u16(d, 6) ? u16(d, 6) * 0.00256 : null, load_bar: s16(d, 17) / 1000,
        fuel: [0x90, 0xB0].includes(fuelByte) ? 'CNG' : [0x80, 0xA0].includes(fuelByte) ? 'PETROL' : 'UNKNOWN', unknown_raw_19: d[19],
      };
      if (!bench.advRead) bench.readAdvanced();
      return true;
    };
    bench.readAdvanced = () => {
      ADV.forEach(([name, frame]) => {
        const r = read(frame);
        bench.params[name] = r.ok ? { state: 'OK', values: words(r.payload) } : { state: 'UNREAD' };
      });
      bench.advRead = true;
    };
    bench.setFault = (name, on = true) => { ecu.faults[name] = on; };
    /** Pré-condição de cenário (não é quadro): põe um valor direto no estado da ECU. */
    bench.seed = (requestHex, data) => ecu.put(requestHex, data);
    bench.seedCurve = factors => {
      const raw = factors.flatMap(f => { const r = Math.round(f * 16384); return [r & 255, r >> 8]; });
      ecu.put(H(pedido(0x29, 0x61, 0x01)), raw);
    };

    // ---- anti-apagão (AntiStalloFlagWriter + AntiStalloFlagCore)
    const antiFrame = w => pedido(0x35, ANTI_ADDR, 0x00, w[0] & 255, w[0] >> 8, w[1] & 255, w[1] >> 8);
    const readFlag3 = () => { const r = read(pedido(0x29, ANTI_ADDR, 0x00)); const w = words(r.payload); return r.ok && w.length === 2 ? w : null; };
    const setFlag3Param = w => { bench.params.FLAG_CONF3 = { state: 'OK', values: w.slice() }; };
    const antiOk = (extra = {}) => JSON.stringify({ ok: true, started: true, ...extra });
    const antiFail = error => JSON.stringify({ ok: false, error });

    function antiPrepare(enable) {
      if (!bench.usb) return antiFail('USB desconectado');
      const words0 = readFlag3();
      if (!words0) { bench.antiStatus = { state: 'ERROR', message: 'Não foi possível ler a ECU: sem resposta' }; return antiOk(); }
      setFlag3Param(words0);
      const after = [enable ? (words0[0] | 0x20) : (words0[0] & ~0x20 & 0xFFFF), words0[1]];
      const token = 'tok' + (++bench.opN);
      bench.antiPending = { token, enable, before: words0, after, session: bench.session, frame: antiFrame(after), noop: words0[0] === after[0] };
      bench.antiStatus = { state: 'READY', token, enable, before: words0, after, frameHex: H(antiFrame(after)), noop: bench.antiPending.noop };
      return antiOk();
    }
    function antiConfirm(token) {
      const p = bench.antiPending;
      if (!p || p.token !== token) return antiFail('Não há prévia válida; refaça o toque.');
      if (p.noop) return antiFail('O anti-apagão já está no estado pedido.');
      if (!bench.usb || p.session !== bench.session) return antiFail('USB mudou desde a prévia; refaça o toque.');
      bench.antiPending = null;
      bench.antiStatus = { state: 'WRITING' };
      const current = readFlag3();
      if (!current) return finishAnti(p, { outcome: 'ERROR', message: 'Não foi possível reler a ECU antes de gravar: sem resposta' });
      if (current[0] !== p.before[0] || current[1] !== p.before[1]) return finishAnti(p, { outcome: 'CHANGED_MEANWHILE', message: 'A ECU mudou desde a prévia; nada foi gravado. Refaça a prévia.' });
      const reply = send(p.frame);
      if (bench.hold) { bench.held = { p, reply }; return antiOk(); }
      return resolveWrite(p, reply);
    }
    function resolveWrite(p, reply) {
      bench.held = null;
      const dec = decodeReply(p.frame, reply);
      if (!dec.ok) return finishAnti(p, { outcome: 'NOT_ACKED', message: `A ECU não confirmou a gravação (${reply.length ? 'status 0x' + (dec.status & 255).toString(16).toUpperCase() : 'sem resposta'}).` });
      const back = readFlag3();
      if (!back) return finishAnti(p, { outcome: 'READBACK_FAILED', message: 'A ECU deu ACK, mas a leitura de volta falhou.' });
      setFlag3Param(back);
      const same = back[0] === p.after[0] && back[1] === p.after[1];
      return finishAnti(p, same ? { outcome: 'CONFIRMED', message: 'ECU confirmou e a leitura de volta bate.', readback: back }
        : { outcome: 'READBACK_DIVERGED', message: 'A ECU deu ACK, mas a leitura de volta é diferente do esperado.', readback: back });
    }
    function finishAnti(p, result) {
      bench.antiStatus = { state: 'DONE', ...result, before: p.before, after: p.after, readback: result.readback || null, frameHex: H(p.frame) };
      if (result.outcome === 'CONFIRMED') { bench.antiUndo = (p.before[0] & 0x20) !== 0; bench.antiUndoSession = p.session; }
      return antiOk();
    }
    // ---- anti-apagão, os 5 números (AntiStalloParamsWriter + AntiStalloParamsCore)
    const ANTI_V_MAX = [6000, 1000, 10000, 1000, 1000];
    const antiVFrame = v => { const payload = v.flatMap(x => [x & 255, x >> 8]); const body = [0x37, 0x33, payload.length + 2, 0x01, ...payload]; return [...body, sum(body)]; };
    const readAntiV = () => { const r = read(pedido(0x29, 0x33, 0x01)); const w = words(r.payload); return r.ok && w.length === 5 ? w : null; };
    const setAntiVParam = v => { bench.params.ANTI_STALLO = { state: 'OK', values: v.slice() }; };
    bench.antiVPending = null; bench.antiVStatus = { state: 'IDLE' }; bench.antiVUndo = null; bench.antiVUndoSession = 0; bench.heldV = null;
    function antiVPrepare(changesJson) {
      let ch; try { ch = JSON.parse(changesJson); } catch (_) { return antiFail('Pedido inválido.'); }
      const keys = Object.keys(ch || {});
      if (!keys.length) return antiFail('Nenhum número foi informado.');
      for (const k of keys) {
        const i = Number(k);
        if (!/^\d+$/.test(k) || i > 4) return antiFail('Esse campo não existe.');
        if (!Number.isInteger(ch[k])) return antiFail('O valor tem que ser um número inteiro.');
        if (ch[k] < 0 || ch[k] > ANTI_V_MAX[i]) return antiFail(`O valor tem que ficar entre 0 e ${ANTI_V_MAX[i]}.`);
      }
      if (!bench.usb) return antiFail('USB desconectado');
      const live = readAntiV();
      if (!live) { bench.antiVStatus = { state: 'ERROR', message: 'Não consegui ler a central.' }; return antiOk(); }
      setAntiVParam(live);
      const after = live.slice(); keys.forEach(k => { after[Number(k)] = ch[k]; });
      const changed = after.map((v, i) => (v !== live[i] ? i : -1)).filter(i => i >= 0);
      const token = 'tv' + (++bench.opN);
      bench.antiVPending = { token, before: live, after, changed, session: bench.session, frame: antiVFrame(after), noop: !changed.length };
      bench.antiVStatus = { state: 'READY', token, before: live, after, changed, frameHex: H(antiVFrame(after)), noop: !changed.length };
      return antiOk();
    }
    function antiVConfirm(token) {
      const p = bench.antiVPending;
      if (!p || p.token !== token) return antiFail('Não há conferência válida; refaça o toque.');
      if (p.noop) return antiFail('Esses números já estão assim na central.');
      if (!bench.usb || p.session !== bench.session) return antiFail('O cabo USB mudou desde a conferência; refaça o toque.');
      bench.antiVPending = null; bench.antiVStatus = { state: 'WRITING' };
      const cur = readAntiV();
      if (!cur) return finishAntiV(p, { outcome: 'ERROR', message: 'Não consegui ler a central antes de gravar.' });
      if (cur.some((v, i) => v !== p.before[i])) return finishAntiV(p, { outcome: 'CHANGED_MEANWHILE', message: 'Os números da central mudaram.' });
      const reply = send(p.frame);
      if (bench.hold) { bench.heldV = { p, reply }; return antiOk(); }
      return resolveV(p, reply);
    }
    function resolveV(p, reply) {
      bench.heldV = null;
      const dec = decodeReply(p.frame, reply);
      if (!dec.ok) { const back0 = readAntiV(); return finishAntiV(p, { outcome: 'NOT_ACKED', message: 'A central não confirmou.', valueChanged: !!back0 && back0.some((v, i) => v !== p.before[i]) }); }
      const back = readAntiV();
      if (!back) return finishAntiV(p, { outcome: 'READBACK_FAILED', message: 'A leitura depois de gravar falhou.' });
      setAntiVParam(back);
      const same = back.every((v, i) => v === p.after[i]);
      return finishAntiV(p, same ? { outcome: 'CONFIRMED', message: 'Gravado e conferido na central.', readback: back } : { outcome: 'READBACK_DIVERGED', message: 'Os números lidos são outros.', readback: back });
    }
    function finishAntiV(p, result) {
      bench.antiVStatus = { state: 'DONE', ...result, before: p.before, after: p.after, changed: p.changed, readback: result.readback || null, frameHex: H(p.frame) };
      if (result.outcome === 'CONFIRMED') { bench.antiVUndo = p.before.slice(); bench.antiVUndoSession = p.session; }
      return antiOk();
    }
    bench.releaseValuesWrite = () => { if (bench.heldV) resolveV(bench.heldV.p, bench.heldV.reply); };
    /** Termina uma gravação segurada (hold): a resposta da ECU chega agora. */
    bench.releaseWrite = () => { if (bench.held) resolveWrite(bench.held.p, bench.held.reply); };

    // ---- Mapa K / Curva K
    const mapRows = () => { const rows = []; for (let r = 0; r < 13; r += 1) { const x = read(pedido(0x2A, 0x54, 0x00, r)); if (!x.ok) return null; rows.push(x.payload); } return rows; };
    const startedOp = () => ({ startedAt: ++bench.opN });
    function readMap() {
      if (!bench.usb) return { ok: false, state: 'FAILED', busy: false, error: 'USB desconectado' };
      const all = mapRows();
      if (!all) return { ok: false, state: 'FAILED', busy: false, error: 'A ECU não respondeu à leitura do Mapa K' };
      return { ok: true, state: 'COMPLETED', busy: false, rows: all.slice(0, 12), extraRow: all[12], axes: { petrolBins: PETROL_BINS, rpmBins: RPM_BINS },
        hash: all.flat().join('-'), writableCells: 144, sessionConfirmed: true };
    }
    function readCurve() {
      if (!bench.usb) return { ok: false, state: 'FAILED', busy: false, error: 'USB desconectado' };
      const axis = read(pedido(0x29, 0x4B, 0x01)); const k = read(pedido(0x29, 0x61, 0x01));
      if (!axis.ok || !k.ok || axis.payload.length !== 60 || k.payload.length !== 60) return { ok: false, state: 'FAILED', busy: false, error: 'A ECU não respondeu à leitura da Curva K' };
      const ax = words(axis.payload), ks = words(k.payload);
      const points = ks.map((raw, index) => ({ index, petrolMs: ax[index] / 512, factor: raw / 16384, factorRaw: raw }));
      return { ok: true, state: 'COMPLETED', busy: false, points, pointCount: 30, minimumFactor: 0.6, maximumFactor: 3.99, ...startedOp() };
    }
    function writeMapBatch(items) {
      const op = { ok: true, state: 'BATCH_CONFIRMED', busy: false, progress: 100, totalCells: items.length, confirmedCells: 0, readbackValid: false, ...startedOp() };
      const before = mapRows();
      for (const item of items) {
        if (!before || before[item.row][item.column] !== item.current) return { ...op, ok: false, state: 'BATCH_PARTIAL_FAILED', error: 'Valor atual divergente da ECU', failure: { error: 'Valor atual divergente da ECU' } };
        if (!(item.target >= 100 && item.target <= 255)) return { ...op, ok: false, state: 'BATCH_PARTIAL_FAILED', error: 'Alvo fora de 100–255', failure: { error: 'Alvo fora de 100–255' } };
        const frame = pedido(0x14, 0x54, 0x00, item.row, item.column, item.target);
        if (!decodeReply(frame, send(frame)).ok) return { ...op, ok: false, state: 'BATCH_PARTIAL_FAILED', error: 'A ECU não deu ACK', failure: { error: 'A ECU não deu ACK' } };
        op.confirmedCells += 1;
      }
      const after = mapRows();
      op.readbackValid = !!after && items.every(i => after[i.row][i.column] === i.target);
      if (!op.readbackValid) { op.ok = false; op.state = 'BATCH_PARTIAL_FAILED'; op.error = 'A leitura de volta é diferente'; op.failure = { error: op.error }; }
      return op;
    }
    function writeCurveBatch(points) {
      const op = { ok: true, state: 'BATCH_CONFIRMED', busy: false, progress: 100, totalCells: points.length, confirmedCells: 0, readbackValid: false, ...startedOp() };
      const cur = readCurve();
      for (const pt of points) {
        if (!cur.ok || cur.points[pt.index].factorRaw !== pt.currentRaw) return { ...op, ok: false, state: 'BATCH_PARTIAL_FAILED', error: 'A Curva K mudou na ECU' };
        if (!(pt.targetRaw >= Math.round(0.6 * 16384) && pt.targetRaw <= 29491)) return { ...op, ok: false, state: 'BATCH_PARTIAL_FAILED', error: 'Alvo acima do teto 1,8' };
        const frame = pedido(0x14, 0x61, 0x01, pt.index, pt.targetRaw & 255, pt.targetRaw >> 8);
        if (!decodeReply(frame, send(frame)).ok) return { ...op, ok: false, state: 'BATCH_PARTIAL_FAILED', error: 'A ECU não deu ACK' };
        op.confirmedCells += 1;
      }
      const after = readCurve();
      op.readbackValid = after.ok && points.every(p => after.points[p.index].factorRaw === p.targetRaw);
      if (!op.readbackValid) { op.ok = false; op.state = 'BATCH_PARTIAL_FAILED'; op.error = 'A leitura de volta é diferente'; }
      return op;
    }

    // ---- ações AutoCal (02 24 04 modo): preparar NUNCA envia; executar envia um quadro
    const ACTION_MODE = { RESET_PETROL: 1, RESET_GAS: 2, MANUAL_AUTOMATCH: 8 };
    const json = JSON.stringify;

    const native = {
      getReleaseIdentity: () => json({ product: 'OMEGAS', versionName: 'bancada-e2e', engine: 'MP48', generation: 'V8' }),
      getStatus: () => json({
        serviceRunning: true, engineRunning: bench.usb, engineReady: bench.usb && bench.seq > 0, engineStuck: false, usbConnected: bench.usb,
        usbPermissionPending: false, fuelState: bench.live ? bench.live.fuel : null, rpm: bench.live ? bench.live.rpm : null,
        petrolMs: bench.live ? bench.live.petrol_ms : null, mapBar: bench.live ? bench.live.load_bar : null,
        directTelemetryAgeMs: bench.usb && bench.seq > 0 ? 40 : -1,
      }),
      getPresentSnapshot: () => json(presentEnvelope()),
      getPresentSnapshotIfChanged: since => { const env = presentEnvelope(); return json(Number(since) === env.data.sequence ? { ok: true, changed: false, telemetryAgeMs: env.data.telemetryAgeMs } : { ...env, changed: true }); },
      getScienceSnapshotSince: rev => json({ ok: true, changed: false, revision: Number(rev) || 0 }),
      getSessionRecorderStatus: () => json({ recording: false }),
      listRecordedSessions: () => json([]),
      getLogs: () => json([]),
      startKMapRead: () => { bench.mapRead = readMap(); return json({ ok: bench.mapRead.ok, started: bench.mapRead.ok, state: bench.mapRead.state, error: bench.mapRead.error }); },
      getKMapReadResult: () => json(bench.mapRead),
      previewKFactorPoint: (index, factor) => {
        const cur = readCurve();
        const point = cur.ok && cur.points.find(p => p.index === Number(index));
        if (!point) return json({ ok: false, error: 'Ponto inválido' });
        if (!(Number(factor) >= 0.6 && Number(factor) <= 1.8)) return json({ ok: false, error: 'Informe um fator entre 0,60 e 1,80' });
        const targetRaw = Math.floor(Number(factor) * 16384);
        return json({ ok: true, index: point.index, petrolMs: point.petrolMs, currentFactor: point.factor, targetFactor: targetRaw / 16384, currentRaw: point.factorRaw, targetRaw,
          deltaPercent: (targetRaw / point.factorRaw - 1) * 100, changed: targetRaw !== point.factorRaw });
      },
    };
    function presentEnvelope() {
      const ok = bench.usb && !!bench.live;
      const data = ok ? { valid: true, sequence: bench.seq, ageMs: 40, telemetryAgeMs: 40, live: { ...bench.live } } : { valid: false, sequence: -1, telemetryAgeMs: -1, live: {} };
      return { ok: true, revision: bench.seq, data };
    }
    const v7 = {
      previewMapAdjustment: (cellsJson, mode, adjustment) => {
        const cells = JSON.parse(cellsJson); const v = Number(adjustment);
        return json({ ok: true, mode, adjustment: v, minimumK: 100, maximumK: 255, automatic: false, requiresReview: true,
          items: cells.map(c => { const cur = Number(c.current); const raw = mode === 'percent' ? cur * (1 + v / 100) : mode === 'delta' ? cur + v : v; const t = Math.max(100, Math.min(255, Math.round(raw))); return { row: c.row, column: c.column, current: cur, target: t, changed: t !== cur }; }) });
      },
      startMapBatchWrite: (itemsJson) => {
        if (!bench.usb) return json({ ok: false, error: 'USB desconectado' });
        bench.mapOp = writeMapBatch(JSON.parse(itemsJson));
        return json({ ok: true, started: true, startedAt: bench.mapOp.startedAt });
      },
      getLastOperation: () => json(bench.lastOp || bench.mapOp),
      startCurveRead: () => {
        const cur = readCurve(); bench.curveOp = cur; bench.lastOp = cur;
        return json({ ok: cur.ok, started: cur.ok, state: cur.state, error: cur.error });
      },
      startCurveBatchWrite: (pointsJson) => {
        if (!bench.usb) return json({ ok: false, error: 'USB desconectado' });
        bench.lastOp = writeCurveBatch(JSON.parse(pointsJson));
        return json({ ok: true, started: true, startedAt: bench.lastOp.startedAt });
      },
      listCurveBackups: () => json([]),
    };
    // getLastOperation do Mapa K usa a mesma "última operação" da ponte real: a escrita do mapa a define.
    const startMapWrite = v7.startMapBatchWrite;
    v7.startMapBatchWrite = items => { const r = startMapWrite(items); bench.lastOp = bench.mapOp; return r; };

    const autocalExtra = {
      getAdvancedParams: () => json({ ok: true, read: bench.advRead, trigger: 'AUTO_START', params: clone(bench.params) }),
      prepareAntiStalloFlag: enable => antiPrepare(enable === true || enable === 'true'),
      confirmAntiStalloFlag: token => antiConfirm(String(token)),
      cancelAntiStalloFlag: () => { if (bench.antiStatus.state === 'READY') bench.antiStatus = { state: 'IDLE' }; bench.antiPending = null; return json({ ok: true }); },
      getAntiStalloFlagStatus: () => json({ ...bench.antiStatus, busy: false,
        undo: bench.antiUndo !== null && bench.antiUndoSession === bench.session && bench.usb ? { available: true, enable: bench.antiUndo } : { available: false } }),
      prepareAntiStalloValues: json => antiVPrepare(String(json)),
      confirmAntiStalloValues: token => antiVConfirm(String(token)),
      cancelAntiStalloValues: () => { if (bench.antiVStatus.state === 'READY') bench.antiVStatus = { state: 'IDLE' }; bench.antiVPending = null; return json({ ok: true }); },
      getAntiStalloValuesStatus: () => json({ ...bench.antiVStatus, busy: false,
        undo: bench.antiVUndo && bench.antiVUndoSession === bench.session && bench.usb ? { available: true, values: bench.antiVUndo } : { available: false } }),
      prepareNativeAction: action => {
        if (!(action in ACTION_MODE)) return json({ ok: false, error: 'Ação não exposta nesta bancada: ' + action });
        bench.actionPrepared = { id: 'prep' + (++bench.prepN), action };
        return json({ ok: true, prepared: true, preparationId: bench.actionPrepared.id, action });
      },
      executeNativeAction: id => {
        const p = bench.actionPrepared;
        if (!p || p.id !== id) return json({ ok: false, error: 'Preparação inválida' });
        if (!bench.usb) return json({ ok: false, error: 'USB desconectado' });
        bench.actionPrepared = null;
        const frame = pedido(0x02, 0x24, 0x04, ACTION_MODE[p.action]);
        const dec = decodeReply(frame, send(frame));
        bench.actionStatus = { state: dec.ok ? 'CONFIRMED' : 'FAILED', action: p.action, ok: dec.ok };
        return json({ ok: dec.ok, started: dec.ok, error: dec.ok ? undefined : 'A ECU não deu ACK' });
      },
      clearNativeActionPreparation: () => { bench.actionPrepared = null; return json({ ok: true }); },
      getNativeActionStatus: () => json(bench.actionStatus),
      getMapSuggestion: () => json({ ok: true, verdict: 'SUGERIR', reason: 'DIFERENCA_LOCAL', cells: bench.suggestionCells || [], equivalence: { cells: [] } }),
    };
    bench.suggestionCells = [];
    bench.native = native; bench.v7 = v7; bench.autocalExtra = autocalExtra;
    return bench;
  }

  // ---------------------------------------------------------------- classificação do que saiu pelo fio
  function summarize(ecu) {
    const by = { WRITE: [], ACTION: [], READ: [] };
    ecu.log.forEach(f => by[f.kind].push(f.hex));
    return by;
  }

  return { EcuJs, createBench, summarize, H, B, pedido, tamanhoPedido, classify };
});
