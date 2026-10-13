(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.OmegasCurveToMap = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  /**
   * "Curva K → Mapa K" do OMEGAS (decisão do dono, 2026-10-13): o ExportToK do original nunca foi testado e não é usado;
   * esta rotina só PROPÕE alterações no Mapa K (a gravação segue o fluxo manual: prévia, confirmação, ACK e leitura de volta).
   *
   * Fórmula tirada do ExportToK do ProgBase (desmontagem de 0x512624/0x51280C/0x5127B0, PROTOCOLO-LANDI doc 26):
   *   por linha do Mapa K, x = Tinj da linha (ms); acha o par de nós da curva K com TBP[a] ≤ x ≤ TBP[b];
   *   K = y0 + (y1 − y0)·(x − x0)/(x1 − x0); toda célula da linha é multiplicada por K; linha fora de [TBP[0], TBP[último]] não muda.
   * Diferenças deliberadas: nó repetido (x1 = x0) pula a linha em vez de zerá-la (o original devolve K = 0); arredondamento e
   * limites [100, 255] ficam com o Mapa K nativo ("percent" = valor × (1 + p/100), arredondado e limitado).
   */
  const finite = value => value === null || value === undefined || value === '' || !Number.isFinite(Number(value)) ? null : Number(value);

  function lerp(x, x0, x1, y0, y1) {
    return x1 === x0 ? 0 : y0 + ((y1 - y0) * (x - x0)) / (x1 - x0);
  }

  function plan({ tbp = [], k = [], rowMs = [] } = {}) {
    const nodes = [];
    for (let index = 0; index < Math.min(tbp.length, k.length); index += 1) {
      const x = finite(tbp[index]);
      const y = finite(k[index]);
      if (x !== null && x > 0 && y !== null && y > 0) nodes.push({ x, y });
    }
    const rows = rowMs.map((value, row) => {
      const ms = finite(value);
      const base = { row, ms, k: null, percent: null };
      if (ms === null) return { ...base, status: 'sem-eixo' };
      if (nodes.length < 2) return { ...base, status: 'sem-curva' };
      if (ms < nodes[0].x) return { ...base, status: 'abaixo' };
      if (ms > nodes[nodes.length - 1].x) return { ...base, status: 'acima' };
      for (let i = 0; i < nodes.length - 1; i += 1) {
        const a = nodes[i];
        const b = nodes[i + 1];
        if (a.x <= ms && ms <= b.x && b.x !== a.x) {
          const factor = lerp(ms, a.x, b.x, a.y, b.y);
          return { ...base, status: 'ok', k: factor, percent: (factor - 1) * 100 };
        }
      }
      return { ...base, status: 'degenerada' };
    });
    const changing = rows.filter(item => item.status === 'ok' && Math.abs(item.percent) > 1e-9);
    return { rows, changing, nodes: nodes.length };
  }

  return { plan, lerp };
});
