(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  const FLAG = 'omegas-v9-primeiro-uso';
  const doc = () => root.document;
  const FOCUSAVEL = 'button:not([disabled]), [href], [tabindex]:not([tabindex="-1"])';
  let sheet = null, scrim = null, tour = null, tourIndex = 0, lastFocus = null, unsub = null, openedRoute = null;

  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])); }
  function currentRoute() {
    try { return root.OmegasApp.store.get().route || 'regular'; } catch (_) { return 'regular'; }
  }
  function helpBtn() { return doc().getElementById('ajudaBtn'); }
  function railTop() {
    const rail = doc().querySelector('.side-rail');
    return rail ? Math.round(rail.getBoundingClientRect().top) : root.innerHeight;
  }
  function trap(e, box) {
    if (e.key !== 'Tab') return;
    const items = Array.from(box.querySelectorAll(FOCUSAVEL)).filter(x => x.offsetParent !== null);
    if (!items.length) return;
    const first = items[0], last = items[items.length - 1];
    if (e.shiftKey && (doc().activeElement === first || !box.contains(doc().activeElement))) { e.preventDefault(); last.focus(); }
    else if (!e.shiftKey && (doc().activeElement === last || !box.contains(doc().activeElement))) { e.preventDefault(); first.focus(); }
  }
  function setExpanded(v) { const b = helpBtn(); if (b) b.setAttribute('aria-expanded', v ? 'true' : 'false'); }

  function block(rotulo, texto) { return `<section class="ajuda-bloco"><h3>${esc(rotulo)}</h3><p>${esc(texto)}</p></section>`; }

  function open(rota) {
    if (tour) closeTour(false);
    const route = rota || currentRoute();
    const t = (ns.AjudaTextos && ns.AjudaTextos.telas[route]) || ns.AjudaTextos.telas.regular;
    close(false);
    lastFocus = helpBtn() || doc().activeElement;
    openedRoute = route;
    const top = railTop();
    scrim = doc().createElement('div');
    scrim.className = 'ajuda-scrim';
    scrim.style.bottom = (root.innerHeight - top) + 'px';
    scrim.addEventListener('click', () => close(true));
    sheet = doc().createElement('div');
    sheet.className = 'ajuda-sheet';
    sheet.setAttribute('role', 'dialog');
    sheet.setAttribute('aria-modal', 'true');
    sheet.setAttribute('aria-labelledby', 'ajudaTitulo');
    sheet.style.bottom = (root.innerHeight - top) + 'px';
    sheet.dataset.rota = route;
    sheet.innerHTML = `<h2 id="ajudaTitulo" class="ajuda-titulo">${esc(t.titulo)}: ajuda</h2>
      <div class="ajuda-blocos">${block('O que é', t.eoque)}${block('O que fazer', t.fazer)}${block('O que acontece', t.acontece)}${t.quando ? block('Quando usar', t.quando) : ''}</div>
      <div class="ajuda-acoes"><button type="button" class="nx-btn primary" data-ajuda="fechar">Entendi</button><button type="button" class="nx-btn" data-ajuda="tour">Rever o passo a passo</button></div>`;
    sheet.addEventListener('click', e => {
      const b = e.target.closest('[data-ajuda]');
      if (!b) return;
      if (b.dataset.ajuda === 'fechar') close(true); else { close(false); openTour(); }
    });
    sheet.addEventListener('keydown', e => { if (e.key === 'Escape') { e.stopPropagation(); close(true); } else trap(e, sheet); });
    const host = doc().getElementById('app') || doc().body;
    host.appendChild(scrim); host.appendChild(sheet);
    setExpanded(true);
    sheet.querySelector('[data-ajuda="fechar"]').focus();
    try { unsub = root.OmegasApp.store.subscribe(s => { if (s.route !== openedRoute) close(false); }); } catch (_) { unsub = null; }
  }
  function close(restoreFocus) {
    if (typeof unsub === 'function') { try { unsub(); } catch (_) {} }
    unsub = null;
    if (!sheet) return;
    sheet.remove(); scrim.remove(); sheet = scrim = null;
    setExpanded(false);
    if (restoreFocus !== false && lastFocus && lastFocus.focus) lastFocus.focus();
  }

  function markSeen() { try { root.localStorage.setItem(FLAG, '1'); } catch (_) {} }
  function seen() { try { return root.localStorage.getItem(FLAG) === '1'; } catch (_) { return true; } }

  function renderTour() {
    const cards = ns.AjudaTextos.primeiroUso;
    const c = cards[tourIndex], last = tourIndex === cards.length - 1;
    const dots = cards.map((_, i) => `<i class="ajuda-ponto" ${i === tourIndex ? 'data-on="1"' : ''}></i>`).join('');
    tour.innerHTML = `<div class="ajuda-tour-caixa">
      <div class="ajuda-tour-topo"><span class="nx-eyebrow">Passo ${tourIndex + 1} de ${cards.length}</span><span class="ajuda-pontos" aria-hidden="true">${dots}</span></div>
      <h2 id="ajudaTourTitulo" class="ajuda-tour-titulo">${esc(c.titulo)}</h2>
      <p class="ajuda-tour-texto">${esc(c.texto)}</p>
      <div class="ajuda-acoes">${last ? '' : '<button type="button" class="nx-btn" data-tour="pular">Pular</button>'}<button type="button" class="nx-btn primary" data-tour="${last ? 'fim' : 'prox'}">${last ? 'Começar' : 'Próximo'}</button></div></div>`;
    const alvo = tour.querySelector('[data-tour="prox"], [data-tour="fim"]');
    if (alvo) alvo.focus();
  }
  function openTour() {
    close(false);
    if (tour) tour.remove();
    lastFocus = helpBtn() || doc().activeElement;
    tourIndex = 0;
    tour = doc().createElement('div');
    tour.className = 'ajuda-tour';
    tour.setAttribute('role', 'dialog');
    tour.setAttribute('aria-modal', 'true');
    tour.setAttribute('aria-labelledby', 'ajudaTourTitulo');
    tour.addEventListener('click', e => {
      const b = e.target.closest('[data-tour]');
      if (!b) return;
      if (b.dataset.tour === 'prox') { tourIndex += 1; renderTour(); } else closeTour(true);
    });
    tour.addEventListener('keydown', e => { if (e.key === 'Escape') { e.stopPropagation(); closeTour(true); } else trap(e, tour); });
    (doc().getElementById('app') || doc().body).appendChild(tour);
    renderTour();
    markSeen();
  }
  function closeTour(restoreFocus) {
    if (!tour) return;
    tour.remove(); tour = null;
    if (restoreFocus !== false && lastFocus && lastFocus.focus) lastFocus.focus();
  }

  ns.Ajuda = { open, close: () => close(true), openTour, closeTour: () => closeTour(true), isOpen: () => !!sheet, isTourOpen: () => !!tour };

  /** Teste e2e e automação não podem ser atrapalhados: nesses casos o primeiro uso não abre sozinho. */
  function automacao() {
    return !!((root.navigator && root.navigator.webdriver) || root.__bench || root.OmegasE2eBench || root.OmegasBench);
  }
  function boot() {
    const b = helpBtn();
    if (b && !b.dataset.ajudaLigado) {
      b.dataset.ajudaLigado = '1';
      b.addEventListener('click', () => { if (sheet) close(true); else open(); });
    }
    if (!seen() && !automacao()) openTour();
  }
  function start() { if (root.OmegasApp) boot(); else root.addEventListener('omegas-app-ready', boot, { once: true }); }
  if (doc()) start();
})(typeof window !== 'undefined' ? window : globalThis);
