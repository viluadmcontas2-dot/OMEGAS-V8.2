/* Mockup v2 das abas: CSS/DOM injetado SÓ na página de captura (o app não muda).
 * window.__v2.on(rota) aplica; window.__v2.off() devolve tudo ao lugar (a interação segue no layout real).
 * Regras do dono: no máximo 5 botões de navegação (Sessões e Diagnóstico moram em Ferramentas); barra do topo persistente
 * com espaço à direita para as bolinhas do nível do gás; botões sempre à esquerda, juntos, 58 px, 8 px entre eles. */
(function () {
  const CSS = `
html.v2 .side-nav button[data-route="sessions"] { display: none !important; }
html.v2 .side-nav { flex: 0 0 auto !important; gap: 8px !important; margin-left: 14px; }
html.v2 .side-nav button { flex: 0 0 auto !important; height: 58px !important; min-height: 58px !important; padding: 0 22px !important; }
html.v2 .side-nav button span { white-space: nowrap !important; font-size: 17px !important; }
html.v2 .rail-status { margin-left: auto; }
html.v2 .live-bar { justify-content: flex-start !important; gap: 8px; padding-left: 14px !important; }
html.v2 .live-bar .v2-gas-slot { margin-left: auto; margin-right: 14px; min-width: 250px; height: 34px; border: 1px dashed #4b5a70; border-radius: 12px; color: #6f7f96; font-size: 12px; display: flex; align-items: center; justify-content: center; letter-spacing: .04em; }
html.v2 .btn-bar { display: flex; flex: 0 0 auto; flex-wrap: nowrap; justify-content: flex-start; align-items: stretch; gap: 8px; min-width: 0; padding: 0 12px 0 12px; margin: 0; }
html.v2 .btn-bar > * { flex: 0 0 auto; width: auto !important; height: 58px !important; min-height: 58px !important; padding: 0 14px; font-size: 15px; font-weight: 800; }
html.v2 .btn-bar > select { width: 150px !important; }
html.v2 .btn-bar > .v2-nudge { min-width: 58px; padding: 0 14px; background: var(--surface-2); color: var(--text); border: 1px solid var(--line); border-radius: 9px; }
@media (max-width: 900px) {
  html.v2 .side-nav { margin-left: 0; gap: 6px !important; }
  html.v2 .side-nav button { padding: 0 11px !important; }
  html.v2 .side-nav button span { font-size: 15px !important; }
  html.v2 .live-bar { gap: 6px; padding-left: 8px !important; }
  html.v2 .live-bar .live-cell { padding-inline: 8px !important; }
  html.v2 .live-bar .live-cell small { font-size: 11px !important; letter-spacing: 0 !important; }
  html.v2 .live-bar .live-cell b { font-size: 16px !important; }
  html.v2 .live-bar .v2-gas-slot { min-width: 120px; margin-right: 8px; font-size: 10px; }
}
html.v2 .v2-hide { display: none !important; }
/* AutoCal e Refino: só a altura dos botões (58) */
html.v2 .autocal-focus-actions > button, html.v2 .autocal-focus-actions > .autocal-reset-menu, html.v2 .autocal-focus-actions .autocal-reset-menu > summary, html.v2 .refino-action-bar > button { height: 58px !important; min-height: 58px !important; }
html.v2 .autocal-reset-popover { position: fixed !important; left: 12px !important; right: auto !important; top: auto !important; }
/* Ajuste local: painel de edição passa para a esquerda; botões dele com tamanho natural */
html.v2 .map-screen .map-workspace { grid-template-columns: 300px minmax(0, 1fr) !important; }
html.v2 .map-screen .map-editor-panel { order: -1; }
html.v2 .map-screen .map-workspace { flex-direction: row; }
html.v2 .nudge-row, html.v2 .curve-nudge-row { display: flex !important; justify-content: flex-start; gap: 8px; }
html.v2 .nudge-row button, html.v2 .curve-nudge-row button { flex: 0 0 auto; width: auto !important; min-width: 58px; height: 58px; padding: 0 14px; }
html.v2 .inspector-panel .wide { width: auto !important; justify-self: start; align-self: flex-start; height: 58px; padding: 0 18px; }
html.v2 .inspector-panel .quiet-button { height: 58px; }
/* Ajuste global: painel à esquerda; rodapé único com 58 px */
html.v2 .curve-screen { --foot-h: 58px; }
html.v2 .screen.curve-screen.active { grid-template-columns: 340px minmax(0, 1fr) !important; grid-template-areas: "title title" "side main" "foot foot" !important; }
html.v2 .curve-footbar { padding: 0; }
html.v2 .curve-footbar > button, html.v2 .curve-footbar > select { min-width: 0 !important; padding: 0 16px !important; }
`;
  let moved = []; let saved = [];
  function el(sel) { return document.querySelector(sel); }
  function move(node, bar) { if (!node || node.hidden) return; const ph = document.createComment('v2'); node.parentNode.insertBefore(ph, node); moved.push({ node, ph }); node.classList.add('v2-moved'); bar.appendChild(node); }
  function setText(node, text) { if (!node) return; saved.push([node, node.textContent]); node.textContent = text; }
  function overflow(bar) { return bar.scrollWidth > bar.clientWidth + 1 || bar.getBoundingClientRect().right > innerWidth - 8; }
  function on(route) {
    off();
    if (!document.getElementById('v2css')) { const s = document.createElement('style'); s.id = 'v2css'; s.textContent = CSS; document.head.appendChild(s); }
    document.documentElement.classList.add('v2');
    // barra do topo: MAP no lugar de "Pressão da admissão" + espaço reservado às bolinhas do nível do gás
    setText(el('.live-cell[data-live="map"] small'), 'MAP');
    const bar = el('#liveBar'); const slot = document.createElement('div'); slot.className = 'v2-gas-slot'; slot.textContent = 'espaço: nível do gás (bolinhas)'; bar.appendChild(slot);
    saved.push([slot, null]);
    const info = { route, nivel: 'A' };
    if (route === 'map') {
      const scr = el('.map-screen'); const foot = document.createElement('div'); foot.className = 'btn-bar v2-foot'; scr.appendChild(foot); saved.push([foot, null]);
      const full = ['#mapSelectAll', '#mapClearSelection', '#mapFromCurve', '#mapFinishCurve', '#mapShowEquivalence', '#mapPrepareSuggestion'].map(el);
      full.forEach(n => move(n, foot));
      move(el('#mapReviewButton'), foot);
      if (!overflow(foot)) el('.map-screen .surface-toolbar').classList.add('v2-hide'); // barra do cartão ficou vazia: o espaço volta para a grade
      if (overflow(foot)) { // não cabe em uma linha: só o Gravar desce; o resto fica no cartão, já à esquerda
        const back = moved.splice(0).reverse(); back.forEach(m => { m.ph.replaceWith(m.node); });
        move(el('#mapReviewButton'), foot); info.nivel = 'B';
      }
    }
    if (route === 'curve') {
      const foot = el('.curve-footbar'); foot.classList.add('btn-bar');
      move(el('#curveReviewButton'), foot); foot.insertBefore(el('#curveReviewButton'), foot.firstChild);
      if (overflow(foot)) { const b = moved.pop(); b.ph.replaceWith(b.node); info.nivel = 'B'; }
    }
    if (route === 'autocal') {
      const pop = el('.autocal-reset-popover'); const bar2 = el('.autocal-focus-actions');
      if (pop && bar2) pop.style.bottom = (innerHeight - bar2.getBoundingClientRect().top + 6) + 'px';
    }
    return info;
  }
  function off() {
    moved.splice(0).reverse().forEach(m => { m.ph.replaceWith(m.node); m.node.classList.remove('v2-moved'); m.node.classList.remove('v2-nudge'); });
    saved.splice(0).forEach(([n, t]) => { if (t === null) n.remove(); else n.textContent = t; });
    document.querySelectorAll('.v2-hide').forEach(n => n.classList.remove('v2-hide'));
    document.querySelectorAll('.curve-footbar.btn-bar').forEach(n => n.classList.remove('btn-bar'));
    const pop = el('.autocal-reset-popover'); if (pop) pop.style.bottom = '';
    document.documentElement.classList.remove('v2');
  }
  window.__v2 = { on, off };
})();
