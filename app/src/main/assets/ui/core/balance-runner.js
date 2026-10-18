(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.OmegasBalanceRunner = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  /**
   * Segunda etapa do "Curva K → Mapa K" (core/curve-to-map.js): depois que o Mapa K novo foi gravado e conferido,
   * a curva recebe os nós refatorados (neutros dentro das linhas). Este módulo não decide nada de conta: só relê a curva,
   * confere que ela ainda é a que foi planejada (senão não grava), grava pela ponte e espera ACK + readback.
   * Se falhar, devolve o motivo e o plano continua pendente para o dono tentar de novo (o mapa já novo + curva antiga
   * dobraria a correção; por isso a tela avisa e oferece "Concluir curva").
   */
  const defaultNextEvent = () => globalThis.OmegasApp.scheduler.nextEvent();

  async function waitFor(read, nextEvent) {
    for (;;) {
      await nextEvent();
      const status = read() || {};
      if (!(status.busy === true || /QUEUED|READING|WRITING/.test(String(status.state || '')))) return status;
    }
  }

  /**
   * @param api   {startCurveRead, curveOperation, writeCurve}
   * @param nodes [{index, currentRaw, targetRaw}] só os nós que mudam
   */
  async function writeCurveStage(api, nodes, { nextEvent = defaultNextEvent, reason = 'Curva K → Mapa K: curva refatorada' } = {}) {
    const changing = (nodes || []).filter(n => n.targetRaw !== n.currentRaw);
    if (!changing.length) return { ok: true, written: 0, message: 'A curva já está como o plano pede.' };
    const started = api.startCurveRead();
    if (!started?.ok || !started?.started) return { ok: false, error: started?.error || 'A leitura da Curva K não iniciou.' };
    const read = await waitFor(() => api.curveOperation(), nextEvent);
    const list = Array.isArray(read.points) ? read.points : [];
    if (read.ok === false || read.state !== 'COMPLETED' || list.length !== 30) return { ok: false, error: read.error || 'A ECU não devolveu os 30 pontos da Curva K.' };
    const raw = new Map(list.map((p, i) => [Number(p.index ?? i), Number(p.factorRaw)]));
    // Curva já no alvo (tentativa anterior concluiu e só a tela perdeu a resposta): nada a fazer, sem gravar de novo.
    if (changing.every(n => raw.get(n.index) === n.targetRaw)) return { ok: true, written: 0, message: 'A curva já está como o plano pede.' };
    const stale = changing.find(n => raw.get(n.index) !== n.currentRaw);
    if (stale) return { ok: false, stale: true, error: 'A Curva K da ECU mudou desde a leitura do plano. Nada foi gravado na curva.' };
    const write = api.writeCurve(changing.map(({ index, currentRaw, targetRaw }) => ({ index, currentRaw, targetRaw })), reason);
    if (!write?.ok || !write?.started) return { ok: false, error: write?.error || 'A gravação da curva não iniciou.' };
    const done = await waitFor(() => api.curveOperation(), nextEvent);
    if (done.state === 'BATCH_CONFIRMED' && done.readbackValid === true) return { ok: true, written: changing.length };
    return { ok: false, error: done.error || 'A ECU não confirmou a curva (leitura de volta diferente).' };
  }

  return { writeCurveStage };
});
