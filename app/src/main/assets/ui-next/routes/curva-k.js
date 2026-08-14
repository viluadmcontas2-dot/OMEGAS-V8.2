import { escapeText, format, humanDirection } from './common.js';

const PERSPECTIVES = Object.freeze([
  ['adjust', 'Ajustar Curva K'],
  ['autocal', 'AutoCal'],
  ['compare', 'Comparar'],
]);
let lastCurveState = null;
let lastAutoCalState = null;

export const curvaKRoute = {
  mount(ctx, state) {
    lastCurveState = null;
    lastAutoCalState = null;
    ctx.workspace.innerHTML = `<section class="route-page" data-route="curva-k">
      <div class="route-heading"><div><h1>Curva K</h1><p>Tendência global por Petrol Inj. — separada do Mapa K local.</p></div>
      <button class="secondary-action" id="curve-reread" type="button">Reler ECU</button></div>
      <div class="perspective-tabs" id="curve-tabs"></div><div id="curve-root"></div></section>`;
    document.getElementById('curve-reread')?.addEventListener('click', ctx.readCurve);
    if (state.curveK.state !== 'READY') ctx.readCurve();
    this.update(ctx, state);
  },

  update(ctx, state) {
    if (lastCurveState === state.curveK && lastAutoCalState === state.autocal) return;
    lastCurveState = state.curveK;
    lastAutoCalState = state.autocal;
    const curve = state.curveK || {};
    const perspective = curve.perspective || 'adjust';
    const tabs = document.getElementById('curve-tabs');
    if (tabs) {
      tabs.innerHTML = PERSPECTIVES.map(([id, label]) => `<button type="button" class="secondary-action perspective-tab" data-perspective="${id}" aria-pressed="${perspective === id}">${label}</button>`).join('');
      tabs.querySelectorAll('[data-perspective]').forEach((button) => button.addEventListener('click', () => ctx.setCurvePerspective(button.dataset.perspective)));
    }
    if (perspective === 'autocal') renderAutoCal(state);
    else if (perspective === 'compare') renderComparison(state);
    else renderCurve(ctx, state);
  },
};

function renderCurve(ctx, state) {
  const root = document.getElementById('curve-root');
  const curve = state.curveK || {};
  if (!root) return;
  const hasCurve = Array.isArray(curve.points) && curve.points.length === 30;
  if (!hasCurve) {
    root.className = 'empty-state';
    root.innerHTML = `<div><strong>${curve.state === 'BUSY' ? 'Lendo Curva K' : curve.state === 'FAILURE' ? 'Não foi possível confirmar a Curva K' : 'Curva não confirmada'}</strong>${escapeText(curve.error || 'São necessários exatamente 30 pontos reais antes da edição.')}</div>`;
    return;
  }

  const prepared = curve.prepared || [];
  const draftBlocked = curve.draftBlocked === true || curve.state === 'STALE' || curve.state === 'FAILURE';
  const editable = curve.state === 'READY' && !draftBlocked;
  const blockedReason = curve.confirmationBlockedReason || 'O estado real da ECU precisa ser relido antes de confirmar.';
  root.className = 'curve-layout';
  root.innerHTML = `<section class="curve-points" aria-label="30 pontos da Curva K">
    ${(curve.points || []).map((point) => {
      const prep = prepared.find((item) => item.index === point.index);
      return `<button class="curve-point${prep ? ' prepared' : ''}" type="button" data-index="${point.index}" ${editable ? '' : 'title="Rascunho visível; releia a ECU antes de editar"'}><small>${format(point.petrolMs,2)} ms</small><b>${prep ? format(prep.after,3) : format(point.factor,3)}</b><span>${prep ? 'preparado' : 'ECU'}</span></button>`;
    }).join('')}
    </section>
    <section class="learning-now" id="curve-detail">
      <span class="section-kicker">Curva K global</span>
      <strong class="learning-state">${draftBlocked ? 'Rascunho preservado • confirmação bloqueada' : 'Toque num ponto'}</strong>
      <p class="learning-reason">${draftBlocked ? escapeText(blockedReason) : 'O eixo é Petrol Inj.; nenhum ponto desta curva é uma célula do Mapa K.'}</p>
      <div class="learning-meta"><span class="state-chip">30 pontos reais</span><span class="state-chip">${prepared.length} preparados</span>${draftBlocked ? '<span class="state-chip" data-tone="warning">STALE — releitura obrigatória</span>' : ''}</div>
      ${draftBlocked && prepared.length ? '<button class="primary-action" id="curve-review" type="button">Revisar rascunho preservado</button>' : ''}
    </section>`;
  root.querySelectorAll('.curve-point').forEach((button) => button.addEventListener('click', () => renderPointDetail(ctx, curve, Number(button.dataset.index))));
  document.getElementById('curve-review')?.addEventListener('click', ctx.reviewCurve);
}

function renderPointDetail(ctx, curve, index) {
  const root = document.getElementById('curve-detail');
  const point = curve.points?.find((item) => item.index === index);
  if (!root || !point) return;
  const editable = curve.state === 'READY' && curve.draftBlocked !== true;
  root.innerHTML = `<span class="section-kicker">Petrol Inj. ${format(point.petrolMs,2)} ms</span><strong class="learning-state">Fator K ${format(point.factor,3)}</strong><p class="learning-reason">${editable ? 'Prepare uma proposta global. Nada é gravado ao tocar ou ajustar.' : escapeText(curve.confirmationBlockedReason || 'Reler ECU antes de editar.')}</p>
    <div class="nudge-group"><button class="secondary-action" data-delta="-0.02" ${editable ? '' : 'disabled title="Reler ECU antes de ajustar"'}>−0,02</button><button class="secondary-action" data-delta="-0.005" ${editable ? '' : 'disabled title="Reler ECU antes de ajustar"'}>−0,005</button><button class="secondary-action" data-delta="0.005" ${editable ? '' : 'disabled title="Reler ECU antes de ajustar"'}>+0,005</button><button class="secondary-action" data-delta="0.02" ${editable ? '' : 'disabled title="Reler ECU antes de ajustar"'}>+0,02</button></div>
    <button class="primary-action" id="curve-review" type="button" ${curve.prepared?.length ? '' : 'disabled title="Nenhum ponto preparado"'}>Revisar pontos preparados</button>`;
  root.querySelectorAll('[data-delta]').forEach((button) => button.addEventListener('click', () => ctx.prepareCurvePoint(index, Number(button.dataset.delta))));
  document.getElementById('curve-review')?.addEventListener('click', ctx.reviewCurve);
}

function renderAutoCal(state) {
  const root = document.getElementById('curve-root');
  const ac = state.autocal || {};
  if (!root) return;
  root.className = 'autocal-layout';
  root.innerHTML = `<section class="learning-now"><span class="section-kicker">Auto Calibration nativa</span><strong class="learning-state">${ac.enabled ? 'Ativa' : 'Desabilitada'}</strong><p class="learning-reason">A própria ECU coleta zonas e executa AutoMatch conforme seus critérios. O app observa e só envia ação nativa após revisão humana.</p><div class="learning-meta"><span class="state-chip">${ac.acquiredZones ?? 0}/${ac.maxZones ?? 30} zonas</span><span class="state-chip">${ac.matureZones ?? 0} maduras</span><span class="state-chip">contador ${ac.counter ?? '—'}</span></div></section>
    <section class="learning-now"><span class="section-kicker">Efeito global</span><strong class="learning-state">${escapeText(ac.mulActSummary || 'Sem leitura')}</strong><p class="learning-reason">${escapeText(ac.disableWarning || 'Enable/Disable podem mudar a participação efetiva da correção K.')}</p><button class="secondary-action danger-action" type="button" disabled title="Ação real ainda não liberada nesta superfície">${ac.enabled ? 'Desabilitar AutoCal — revisão crítica' : 'Habilitar AutoCal — revisão crítica'}</button><small class="learning-reason">Ação real permanece bloqueada no simulador; Finish não é inventado.</small></section>`;
}

function renderComparison(state) {
  const root = document.getElementById('curve-root');
  const comparison = state.curveK?.comparison || {};
  if (!root) return;
  root.className = 'compare-layout';
  root.innerHTML = `<section class="learning-now"><span class="section-kicker">Gasolina × GNV global</span><strong class="learning-state">${comparison.state === 'READY' ? humanDirection(comparison.direction) : 'Aguardando cobertura'}</strong><p class="learning-reason">Erro global ${comparison.globalErrorPct == null ? '—' : format(comparison.globalErrorPct,1)+'%'} • confiança ${comparison.confidence == null ? '—' : Math.round(comparison.confidence*100)+'%'}</p><div class="learning-meta"><span class="state-chip">gasolina ${Math.round((comparison.gasolineCoverage||0)*100)}%</span><span class="state-chip">GNV ${Math.round((comparison.cngCoverage||0)*100)}%</span></div></section>
    <section class="learning-now"><span class="section-kicker">Limite de responsabilidade</span><strong class="learning-state">Global aqui; local no Mapa K</strong><p class="learning-reason">${escapeText(comparison.localResidualNote || 'Resíduos localizados não viram pontos globais da Curva K.')}</p></section>`;
}