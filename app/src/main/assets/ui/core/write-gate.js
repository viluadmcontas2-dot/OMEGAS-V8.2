(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.OmegasWriteGate = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  /**
   * Porta de gravação do app (dono, 2026-10-13): cada AutoMatch da ECU calcula a correção com a curva e os buffers que ela
   * conhecia quando COMEÇOU. Se o app grava Curva K ou Mapa K enquanto a ECU ainda está no automático, a ECU aplica a correção
   * dela em cima da nossa (e com buffers adquiridos sob o produto antigo). Por isso toda gravação que o app PROPÕE (reforço do
   * engasgo, ajuste local, exportação para o Mapa K) só abre quando o piloto diz que a ECU terminou (`ecuDone`: AutoCal
   * desligado, máximo de AutoMatch atingido, ou aquisição completa e quieta). Edição manual na aba Curva K não passa por aqui.
   * Estado desconhecido (sem piloto, ponte fora) não bloqueia: a porta só fecha quando o Kotlin diz explicitamente que não acabou.
   */
  function check(autopilot) {
    if (!autopilot || autopilot.ecuDone !== false) return { ok: true, message: '' };
    const n = Number.isFinite(Number(autopilot.autoMatchCount)) ? Number(autopilot.autoMatchCount) : null;
    const m = Number.isFinite(Number(autopilot.maxAutomatch)) && Number(autopilot.maxAutomatch) > 0 ? Number(autopilot.maxAutomatch) : null;
    const where = n !== null && m !== null ? ` (${n} de ${m})` : '';
    return {
      ok: false,
      message: `A ECU ainda está no AutoMatch${where}. Espere ela terminar: se eu gravar agora, ela aplica a correção dela em cima da minha.`,
    };
  }

  return { check };
});
