'use strict';
// Apoio aos testes de fluxo das telas (Ajuste local, Ajuste global, Curva -> Mapa K): carrega o núcleo OmegasFluxo
// num contexto vm, entrega hospedeiros de DOM mínimo e tira "fotos" dos botões fixos para compararEstados.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { documentMinimo } = require('./dom-minimo.cjs');
const { compararEstados } = require('../../../tools/ci/_contrato_fluxo.cjs');
const ui = path.resolve(__dirname, '../../../app/src/main/assets/ui');

function instalarFluxo(ctx) {
  for (const file of ['core/write-gate.js', 'components/acao-principal.js', 'core/fluxo.js']) vm.runInNewContext(fs.readFileSync(path.join(ui, file), 'utf8'), ctx);
  return ctx.OmegasFluxo;
}
function hospedeiro() { return documentMinimo().createElement('section'); }
/** Foto dos controles fixos do hospedeiro: o que não pode sumir, esconder nem mudar de lugar entre os estados. */
function foto(host, rotulo) {
  return ['principal', 'ultima', 'desfazer'].map(kind => {
    const el = host.querySelector(`[data-fluxo="${kind}"]`);
    if (!el) return null;
    const r = el.getBoundingClientRect();
    let hidden = !!el.hidden;
    for (let n = el.parentElement; n; n = n.parentElement) hidden = hidden || !!n.hidden;
    return { key: `${rotulo}:${kind}`, hidden, x: r.x, y: r.y, width: r.width, height: r.height };
  }).filter(Boolean);
}
const principalDe = host => host.querySelector('[data-fluxo="principal"]');
const desfazerDe = host => host.querySelector('[data-fluxo="desfazer"]');
const ultimaDe = host => host.querySelector('[data-fluxo="ultima"]');
const tick = () => new Promise(resolve => setImmediate(resolve));
module.exports = { instalarFluxo, hospedeiro, foto, principalDe, desfazerDe, ultimaDe, compararEstados, tick, ui };
