import { escapeText, format } from './common.js';

function valueOrDash(value, digits = 1, suffix = '') {
  return value == null || Number.isNaN(Number(value)) ? '—' : `${format(value, digits)}${suffix}`;
}

export const obdRoute = {
  mount(ctx, state) {
    ctx.workspace.innerHTML = `<section class="route-page" data-route="obd">
      <div class="route-heading"><div><h1>OBD</h1><p>Segunda testemunha: observa trims e contexto. Nunca escreve K, ECU ou Learning.</p></div>
      <button class="secondary-action" id="obd-refresh" type="button">Atualizar estado</button></div>
      <div id="obd-root"></div></section>`;
    document.getElementById('obd-refresh')?.addEventListener('click', ctx.loadObd);
    if (state.obd.state === 'UNAVAILABLE') ctx.loadObd();
    this.update(ctx, state);
  },

  update(_ctx, state) {
    const root = document.getElementById('obd-root');
    if (!root) return;
    const obd = state.obd || {};
    if (obd.state === 'UNAVAILABLE' || obd.state === 'BUSY') {
      root.className = 'empty-state';
      root.innerHTML = `<div><strong>${obd.state === 'BUSY' ? 'Atualizando OBD' : 'OBD indisponível'}</strong>MP48, Learning, Mapa K e Curva K continuam independentes.</div>`;
      return;
    }
    const layers = obd.layers || {};
    root.className = 'obd-layout';
    root.innerHTML = `
      <section class="obd-live learning-now">
        <span class="section-kicker">Estado OBD • ${escapeText(obd.state)}</span>
        <strong class="learning-state">STFT ${valueOrDash(obd.stftPct,1,'%')}</strong>
        <p class="learning-reason">LTFT ${valueOrDash(obd.ltftPct,1,'%')} • RPM ${valueOrDash(obd.rpm,0)} • MAP ${valueOrDash(obd.mapKpa,0,' kPa')} • carga ${valueOrDash(obd.loadPct,0,'%')}</p>
        <div class="learning-meta"><span class="state-chip">${obd.closedLoop ? 'closed loop' : 'open loop'}</span><span class="state-chip">idade ${obd.ageMs == null ? '—' : Math.round(obd.ageMs)+' ms'}</span><span class="state-chip">somente observação</span></div>
      </section>
      <section class="obd-layers">
        ${layerCard('Gasolina', layers.gasoline)}
        ${layerCard('GNV', layers.cng)}
        ${comparisonCard(layers.comparison)}
      </section>
      <section class="learning-now obd-rule"><span class="section-kicker">Regra de autoridade</span><strong class="learning-state">OBD informa; não decide</strong><p class="learning-reason">Se OBD e Learning divergirem, a tela mostra as duas fontes separadas. Não calcula média escondida e não cria comando de calibração.</p></section>`;
  },
};

function layerCard(label, layer = {}) {
  return `<article class="semantic-item"><span class="semantic-role">${escapeText(label)} • ${escapeText(layer.state || 'SEM DADO')}</span><strong>STFT ${valueOrDash(layer.stftMeanPct,1,'%')}</strong><p>LTFT ${valueOrDash(layer.ltftMeanPct,1,'%')} • ${layer.samples ?? 0} amostras qualificadas</p></article>`;
}

function comparisonCard(comparison = {}) {
  const state = comparison.state || 'INDISPONÍVEL';
  return `<article class="semantic-item ${state === 'CONCORDA' ? 'observed' : state === 'DIVERGE' ? 'reference' : ''}"><span class="semantic-role">Comparação • ${escapeText(state)}</span><strong>${state === 'CONCORDA' ? 'Fontes concordam' : state === 'DIVERGE' ? 'Fontes divergem' : 'Sem comparação'}</strong><p>${escapeText(comparison.explanation || 'Aguardando dados suficientes nas duas fontes.')}</p></article>`;
}
