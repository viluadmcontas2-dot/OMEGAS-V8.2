'use strict';
// Analise por PIXEL do grafico (screenshot do elemento): proporcao de pixels fora do fundo, faixas de eixo e grade 32x16 de luminancia
// para comparar com a imagem-base. Roda num about:blank do mesmo browser (canvas), sem dependencia nova.
async function analyzer(page) {
  return {
    page,
    async analyze(buf) {
      return page.evaluate(async b64 => {
        const img = new Image(); img.src = 'data:image/png;base64,' + b64; await img.decode();
        const w = img.width, h = img.height, c = document.createElement('canvas'); c.width = w; c.height = h;
        const x = c.getContext('2d'); x.drawImage(img, 0, 0); const d = x.getImageData(0, 0, w, h).data;
        const q = (r, g, b) => ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
        const hist = new Map(); for (let i = 0; i < d.length; i += 16) { const k = q(d[i], d[i + 1], d[i + 2]); hist.set(k, (hist.get(k) || 0) + 1); }
        let bg = 0, best = -1; hist.forEach((v, k) => { if (v > best) { best = v; bg = k; } });
        const far = i => { const k = q(d[i], d[i + 1], d[i + 2]); const dr = Math.abs(((k >> 8) & 15) - ((bg >> 8) & 15)), dg = Math.abs(((k >> 4) & 15) - ((bg >> 4) & 15)), db = Math.abs((k & 15) - (bg & 15)); return dr + dg + db > 2; };
        const ratio = (x0, y0, x1, y1) => { let n = 0, t = 0; for (let yy = y0; yy < y1; yy++) for (let xx = x0; xx < x1; xx++) { t++; if (far((yy * w + xx) * 4)) n++; } return t ? n / t : 0; };
        const GX = 32, GY = 16, grid = [];
        for (let gy = 0; gy < GY; gy++) for (let gx = 0; gx < GX; gx++) {
          let s = 0, n = 0; for (let yy = Math.floor(gy * h / GY); yy < Math.floor((gy + 1) * h / GY); yy += 2) for (let xx = Math.floor(gx * w / GX); xx < Math.floor((gx + 1) * w / GX); xx += 2) { const i = (yy * w + xx) * 4; s += 0.3 * d[i] + 0.59 * d[i + 1] + 0.11 * d[i + 2]; n++; }
          grid.push(n ? s / n : 0);
        }
        return { w, h, nonBg: ratio(0, 0, w, h), leftStrip: ratio(0, 0, Math.min(88, w), h - 48), bottomStrip: ratio(88, h - 48, w, h), grid };
      }, buf.toString('base64'));
    },
  };
}
// diferenca media absoluta (0..1) entre duas grades de luminancia
function gridDiff(a, b) { if (!a || !b || a.length !== b.length) return 1; let s = 0; for (let i = 0; i < a.length; i++) s += Math.abs(a[i] - b[i]); return s / a.length / 255; }
module.exports = { analyzer, gridDiff };
