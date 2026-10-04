(function (root) {
  'use strict';
  // 08 Diagnóstico: engasgos e fluidez do GNV, e tudo que saiu do Refino (último resultado, Desfazer, técnico).
  // Só desenha: a matemática é do Kotlin. Contratos novos (podem faltar; a tela diz "sem dados" em vez de inventar):
  //   equivalence().stalls.regions[] {mapBar, rpm, ms, count, firstAt, lastAt, curvePoints[], proposal?}
  //   equivalence().fluidity {gasolina|petrol, gnv|cng} cada um {index 0..1, jerks, samples}
  const ns = root.OmegasUi = root.OmegasUi || {};
  const D = ns.DisplayRules;
  const { finite, escapeHtml, fmt } = D;

  const IDLE = { from: 870, to: 1350 };
  const W = 880, H = 270, PAD = { l: 66, r: 34, t: 14, b: 60 };
  const VERDICT = {
    CONFIRMADA: 'GNV igual à gasolina', PASSOU: 'Passou do ponto; a próxima fica mais suave', CURTA: 'Faltou; a próxima fica mais firme',
    PIOROU: 'Piorou', COLETANDO: 'Medindo…', SEM_ANTES: 'Sem medição anterior', SEM_DADOS: 'Poucas leituras para julgar',
  };
  const STATUS = {
    VERIFICADO: 'GNV igual à gasolina', PIOROU_EM_PARTE: 'Piorou em parte', FALHA_PARCIAL: 'Gravação incompleta; a ECU pode ter mudado em parte',
    VERIFICANDO: 'Medindo…', SEM_BASE: 'Sem medição anterior', INCONCLUSIVO: 'Inconclusivo', INTERROMPIDO: 'A ECU mudou a curva no meio',
  };
  const CLOSING = {
    SEM_BASE: 'As faixas alteradas não tinham medição de antes; a de agora virou a base.',
    INCONCLUSIVO: 'Poucas leituras nas faixas alteradas. O Refino segue medindo do zero.',
    INTERROMPIDO: 'A ECU mudou a curva por fora durante a medição; o resultado perdeu a validade.',
    FALHA_PARCIAL: 'A gravação falhou no meio e a ECU pode ter sido alterada em parte. Desfazer no Refino volta à foto de antes.',
  };

  const plural = (n, one, many) => `${n} ${n === 1 ? one : many}`;
  const inIdle = r => r.rpm !== null && r.rpm >= IDLE.from && r.rpm <= IDLE.to;
  const ago = at => {
    const t = finite(at); if (t === null || t <= 0) return '';
    const s = Math.max(0, (Date.now() - t) / 1000);
    return s < 60 ? 'agora há pouco' : s < 3600 ? `há ${Math.round(s / 60)} min` : `há ${Math.round(s / 3600)} h`;
  };

  function regionsOf(eq) {
    const st = eq && typeof eq.stalls === 'object' && eq.stalls ? eq.stalls : null;
    if (!st) return null;
    const list = Array.isArray(st.regions) ? st.regions : [];
    return list.map((r, i) => ({
      id: i,
      mapBar: finite(r.mapBar), rpm: finite(r.rpm), ms: finite(r.ms ?? (finite(r.fromMs) !== null && finite(r.toMs) !== null ? (r.fromMs + r.toMs) / 2 : null)),
      count: Math.max(1, Math.round(finite(r.count) ?? 1)), firstAt: finite(r.firstAt), lastAt: finite(r.lastAt),
      curvePoints: Array.isArray(r.curvePoints) ? r.curvePoints : [], proposal: r.proposal || null,
    })).filter(r => r.mapBar !== null && (r.rpm !== null || r.ms !== null));
  }

  function fluidityOf(eq) {
    const f = eq && typeof eq.fluidity === 'object' && eq.fluidity ? eq.fluidity : null;
    if (!f) return null;
    const one = x => {
      if (!x || typeof x !== 'object') return null;
      const index = finite(x.index);
      return index === null ? null : { index: Math.max(0, Math.min(1, index)), jerks: finite(x.jerks), samples: finite(x.samples) };
    };
    const petrol = one(f.gasolina || f.petrol), gnv = one(f.gnv || f.cng);
    return petrol || gnv ? { petrol, gnv } : null;
  }

  /** Frase de estado + ação, a partir só dos engasgos. */
  function stateModel(eq, regions) {
    if (!eq || eq.ok === false || !eq.stalls || regions === null) return { kind: 'nodata', text: 'Ainda sem dados de engasgo. Ligue a ECU e dirija no GNV; o app observa sozinho.' };
    if (!regions.length) return { kind: 'ok', text: 'Nenhum engasgo registrado: o GNV está firme até aqui.' };
    const total = regions.reduce((a, r) => a + r.count, 0);
    const idle = regions.filter(inIdle);
    const idleCount = idle.reduce((a, r) => a + r.count, 0);
    const proposal = regions.some(r => r.proposal);
    let text = idleCount
      ? `${plural(idleCount, 'engasgo', 'engasgos')} em marcha lenta (${fmt(IDLE.from, 0)}–${fmt(IDLE.to, 0)} rpm)`
      : `${plural(total, 'engasgo', 'engasgos')} fora da marcha lenta`;
    if (idleCount && total > idleCount) text += `, mais ${total - idleCount} em outras faixas`;
    text += proposal ? ': há uma proposta de ajuste local no Refino.' : '. O Refino ainda não tem proposta; continue dirigindo.';
    return { kind: idleCount ? 'bad' : 'warn', text, proposal };
  }

  function regionLine(r) {
    const where = inIdle(r) ? 'Marcha lenta' : (r.rpm !== null && r.rpm < IDLE.from ? 'Abaixo da lenta' : 'Em carga');
    const bits = [r.rpm !== null ? `${fmt(r.rpm, 0)} rpm` : null, `${D.bar ? D.bar(r.mapBar) : fmt(r.mapBar, 3)} bar`, r.ms !== null ? D.ms(r.ms) + ' ms' : null].filter(Boolean);
    const when = ago(r.lastAt);
    return `${where}: ${plural(r.count, 'engasgo', 'engasgos')} perto de ${bits.join(' · ')}${when ? `, o último ${when}` : ''}.${r.proposal ? ' Há proposta no Refino.' : ''}`;
  }

  function chartSvg(regions, selected, noData) {
    const useRpm = !regions.length || regions.every(r => r.rpm !== null);
    const xs = regions.map(r => useRpm ? r.rpm : r.ms);
    const xMin = useRpm ? Math.min(600, ...xs.map(v => Math.floor(v / 100) * 100 - 100)) : 0;
    const xMax = useRpm ? Math.max(2000, ...xs.map(v => Math.ceil(v / 500) * 500)) : Math.max(8, ...xs.map(v => Math.ceil(v) + 1));
    const ys = regions.map(r => r.mapBar);
    const yMin = Math.max(0, Math.min(0.2, ...ys.map(v => Math.floor(v * 10) / 10 - 0.1)));
    const yMax = Math.min(1.2, Math.max(0.8, ...ys.map(v => Math.ceil(v * 10) / 10 + 0.1)));
    const X = v => PAD.l + (v - xMin) / (xMax - xMin) * (W - PAD.l - PAD.r);
    const Y = v => H - PAD.b - (v - yMin) / (yMax - yMin) * (H - PAD.t - PAD.b);
    const xStep = useRpm ? (xMax - xMin > 2500 ? 500 : 250) : 1;
    let grid = '';
    for (let v = Math.ceil(xMin / xStep) * xStep; v <= xMax; v += xStep) grid += `<line class="dg-grid" x1="${X(v)}" x2="${X(v)}" y1="${PAD.t}" y2="${H - PAD.b}"/><text class="dg-tick" x="${X(v)}" y="${H - PAD.b + 22}" text-anchor="middle">${useRpm ? fmt(v, 0) : v}</text>`;
    for (let v = Math.ceil(yMin * 5) / 5; v <= yMax + 1e-9; v += 0.2) grid += `<line class="dg-grid" x1="${PAD.l}" x2="${W - PAD.r}" y1="${Y(v)}" y2="${Y(v)}"/><text class="dg-tick" x="${PAD.l - 8}" y="${Y(v) + 6}" text-anchor="end">${fmt(v, 1)}</text>`;
    const band = useRpm ? `<rect class="dg-idle" x="${X(IDLE.from)}" y="${PAD.t}" width="${X(IDLE.to) - X(IDLE.from)}" height="${H - PAD.t - PAD.b}"/><text class="dg-idle-label" x="${(X(IDLE.from) + X(IDLE.to)) / 2}" y="${PAD.t + 22}" text-anchor="middle">Marcha lenta</text>` : '';
    const maxC = Math.max(1, ...regions.map(r => r.count));
    const marks = regions.map(r => {
      const x = X(useRpm ? r.rpm : r.ms), y = Y(r.mapBar), rad = 16 + 14 * (r.count / maxC);
      const tone = inIdle(r) ? 'bad' : 'warn';
      return `<g class="dg-region" data-region="${r.id}" data-tone="${tone}" data-selected="${selected === r.id}" tabindex="0" role="button" aria-label="${escapeHtml(regionLine(r))}">
        <circle class="dg-hit" cx="${x}" cy="${y}" r="${Math.max(30, rad + 6)}"/><circle class="dg-dot" cx="${x}" cy="${y}" r="${rad}"/><text class="dg-count" x="${x}" y="${y + 7}" text-anchor="middle">${r.count}</text></g>`;
    }).join('');
    const empty = regions.length ? '' : `<text class="dg-empty" x="${(W + PAD.l) / 2}" y="${H / 2 - 10}" text-anchor="middle">${noData ? 'Sem dados ainda' : 'Tudo firme: nada engasgou'}</text>`;
    return `<svg viewBox="0 0 ${W} ${H}" class="dg-svg" role="img" aria-label="Regiões de engasgo: pressão do coletor contra rotação">
      ${band}${grid}<line class="dg-axis" x1="${PAD.l}" x2="${W - PAD.r}" y1="${H - PAD.b}" y2="${H - PAD.b}"/><line class="dg-axis" x1="${PAD.l}" x2="${PAD.l}" y1="${PAD.t}" y2="${H - PAD.b}"/>
      <text class="dg-axis-title" x="${(W + PAD.l) / 2}" y="${H - 6}" text-anchor="middle">${useRpm ? 'Rotação (rpm)' : 'Injeção na gasolina (ms)'}</text>
      <text class="dg-axis-title" x="14" y="${H / 2}" text-anchor="middle" transform="rotate(-90 14 ${H / 2})">MAP (bar)</text>
      ${empty}${marks}</svg>`;
  }

  function fluidityCard(fl) {
    const row = (label, tone, x, hint) => {
      const pct = x ? Math.round(x.index * 100) : null;
      return `<div class="dg-fl-row" data-tone="${tone}" data-fuel="${label === 'GNV' ? 'gnv' : 'gasolina'}"><span class="dg-fl-name">${label}</span>
        <div class="dg-fl-bar" role="img" aria-label="${label}: ${pct === null ? 'sem medida' : pct + '% linear'}"><i style="width:${pct === null ? 0 : pct}%"></i></div>
        <b>${pct === null ? '—' : pct + '%'}</b><span class="dg-fl-hint">${hint}</span></div>`;
    };
    const hintP = fl && fl.petrol ? 'Acelera em linha reta.' : 'Dirija na gasolina para medir.';
    const hintG = !fl || !fl.gnv ? 'Dirija no GNV para medir.' : fl.gnv.jerks ? `${plural(fl.gnv.jerks, 'solavanco', 'solavancos')} nas acelerações.` : 'Sem solavancos.';
    let verdict = 'A fluidez compara a resposta do motor nas acelerações: 100% é linear, como a gasolina.';
    if (fl && fl.petrol && fl.gnv) {
      const gap = Math.round((fl.petrol.index - fl.gnv.index) * 100);
      verdict = gap <= 3 ? 'O GNV acelera tão linear quanto a gasolina.' : `O GNV está ${gap} pontos menos linear que a gasolina nas acelerações.`;
    }
    return `<section class="dg-card dg-fluidity" aria-label="Fluidez nas acelerações"><header class="dg-head"><div><small>FLUIDEZ NAS ACELERAÇÕES</small><h3>Gasolina é linear; o GNV pode engasgar</h3></div></header>
      ${row('Gasolina', 'ok', fl && fl.petrol, hintP)}${row('GNV', fl && fl.gnv && fl.petrol && fl.petrol.index - fl.gnv.index > 0.03 ? 'warn' : 'ok', fl && fl.gnv, hintG)}
      <p class="dg-verdict">${escapeHtml(verdict)}</p></section>`;
  }

  class DiagnosticoScreen {
    constructor(app) {
      this.app = app;
      this.host = document.getElementById('diagnosticoHost');
      this.selected = null;
      this.eq = null;
      this.sig = '';
      this.host?.addEventListener('click', e => this.onClick(e));
      this.host?.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') this.onClick(e); });
    }
    onClick(e) {
      const go = e.target.closest('[data-diag-go-refino]');
      if (go) { this.app.router.navigate('refino'); return; }
      const g = e.target.closest('[data-region]');
      if (g) { const id = Number(g.dataset.region); this.selected = this.selected === id ? null : id; this.sig = ''; this.render(); }
    }
    refresh(force) {
      const now = Date.now();
      if (!force && this.last && now - this.last < 2000) return;
      this.last = now;
      const api = ns.AutoCalApi;
      this.eq = (api && api.equivalence ? api.equivalence() : null) || null;
      this.render();
    }
    render() {
      if (!this.host) return;
      const eq = this.eq;
      const sig = JSON.stringify([eq && eq.stalls, eq && eq.fluidity, eq && eq.refinement, eq && eq.autopilot, this.selected, !!eq]);
      if (sig === this.sig && this.host.childElementCount) return;
      this.sig = sig;
      const open = [...this.host.querySelectorAll('details')].map(d => d.open);
      const regions = regionsOf(eq);
      const list = regions || [];
      const st = stateModel(eq, regions);
      const sel = list.find(r => r.id === this.selected);
      const sub = !eq || eq.ok === false || !eq.stalls ? 'Sem dados ainda' : list.length ? `${plural(list.reduce((a, r) => a + r.count, 0), 'engasgo', 'engasgos')} em ${plural(list.length, 'região', 'regiões')}` : 'Nenhum engasgo';
      const touchLine = sel ? regionLine(sel) : list.length ? 'Toque numa região do gráfico para ver o que aconteceu ali.' : '';
      const act = st.proposal ? '<button type="button" class="btn btn-primary" data-diag-go-refino>Ver a proposta no Refino</button>' : '';
      {
        this.host.innerHTML = `
        <section class="dg-card dg-chart" aria-label="Onde o motor engasga">
          <header class="dg-head"><div><small>ONDE O GNV ENGASGA</small><h3>${escapeHtml(sub)}</h3></div><span class="dg-legend" ${list.length ? '' : 'hidden'}><i class="dg-lg bad"></i>Marcha lenta <i class="dg-lg warn"></i>Outras faixas</span></header>
          <div class="dg-plot">${chartSvg(list, this.selected, st.kind === 'nodata')}</div>
          ${touchLine ? `<p class="dg-region-line" data-has="${sel ? 'true' : 'false'}">${escapeHtml(touchLine)}</p>` : ''}
        </section>
        <section class="dg-state" data-kind="${st.kind}"><p>${escapeHtml(st.text)}</p>${act}</section>
        ${fluidityCard(fluidityOf(eq))}
        ${this.journal(eq)}
        ${this.undo(eq)}
        ${this.tech(eq, list)}`;
      }
      [...this.host.querySelectorAll('details')].forEach((d, i) => { if (open[i]) d.open = true; });
    }
    journal(eq) {
      const latest = eq && eq.refinement && eq.refinement.latest;
      let body;
      if (!latest || !latest.status) body = '<p class="dg-note">O Refino ainda não gravou nenhum ajuste.</p>';
      else {
        const bands = (Array.isArray(latest.bands) ? latest.bands : []).filter(b => VERDICT[b.verdict]);
        const rows = bands.map(b => `<li data-verdict="${escapeHtml(b.verdict)}"><span>${fmt(b.fromMs, 1)}–${fmt(b.toMs, 1)} ms</span><b>${D.gapPercent(b.ratioBefore)}${finite(b.ratioAfter) === null ? '' : ' → ' + D.gapPercent(b.ratioAfter)}</b><em>${escapeHtml(VERDICT[b.verdict])}</em></li>`).join('');
        body = `<p class="dg-note">${escapeHtml((STATUS[latest.status] || 'Resultado desconhecido').replace(/([^….])$/, '$1.'))}</p>${rows ? `<ul class="dg-bands">${rows}</ul>` : ''}${CLOSING[latest.status] ? `<p class="dg-note">${escapeHtml(CLOSING[latest.status])}</p>` : ''}`;
      }
      return `<section class="dg-card" aria-label="Último resultado do Refino"><header class="dg-head"><div><small>ÚLTIMO RESULTADO DO REFINO</small><h3>O que o último ajuste fez</h3></div></header>${body}</section>`;
    }
    undo(eq) {
      const hist = (eq && eq.refinement && Array.isArray(eq.refinement.history) ? eq.refinement.history : []).slice().reverse();
      const items = hist.map(h => {
        const when = finite(h.appliedAt) ? new Date(h.appliedAt).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—';
        const ratio = finite(h.ratioBefore) === null ? '' : ` · ${D.gapPercent(h.ratioBefore)}${finite(h.ratioAfter) === null ? '' : ' → ' + D.gapPercent(h.ratioAfter)}`;
        return `<li data-status="${escapeHtml(h.status)}"><b>${escapeHtml(when)}</b><span>${escapeHtml(STATUS[h.status] || 'Resultado desconhecido')}${escapeHtml(ratio)}</span></li>`;
      }).join('');
      return `<section class="dg-card" aria-label="Histórico de gravações e Desfazer"><header class="dg-head"><div><small>GRAVAÇÕES E DESFAZER</small><h3>${hist.length ? plural(hist.length, 'gravação feita pelo Refino', 'gravações feitas pelo Refino') : 'Nenhuma gravação para desfazer'}</h3></div>${hist.length ? '<button type="button" class="btn btn-secondary btn-compact" data-diag-go-refino>Desfazer no Refino</button>' : ''}</header>${items ? `<ol class="dg-history">${items}</ol>` : '<p class="dg-note">Quando o Refino gravar, cada gravação aparece aqui com a foto de antes guardada.</p>'}</section>`;
    }
    tech(eq, regions) {
      const pilot = (eq && eq.autopilot) || {};
      const rows = [
        ['Pode desconectar (Refino)', pilot.canDisconnect === true ? 'sim' : pilot.canDisconnect === false ? 'ainda não' : '—'],
        ['Prazo da tentativa', (pilot.watchdogExpired === true ? 'vencido' : pilot.watchdogExpired === false ? 'dentro do prazo' : '—') + (pilot.timeoutReason ? ' · ' + pilot.timeoutReason : '')],
        ['AutoMatch da ECU', `${fmt(pilot.autoMatchCount, 0)} de ${fmt(pilot.maxAutomatch, 0)}${pilot.ecuDoneReason ? ' · ' + pilot.ecuDoneReason : ''}`],
        ['Motor apagou / quase / religou', eq && eq.stalls ? `${fmt(eq.stalls.count, 0)} / ${fmt(eq.stalls.nearCount, 0)} / ${fmt(eq.stalls.restartedCount, 0)}` : '—'],
        ['Pontos da curva por região', regions.length ? regions.map((r, i) => `#${i + 1}: ${r.curvePoints.length ? r.curvePoints.join(', ') : '—'}`).join(' · ') : '—'],
        ['Proposta de ajuste local', regions.some(r => r.proposal) ? regions.filter(r => r.proposal).map(r => JSON.stringify(r.proposal)).join(' · ') : 'nenhuma'],
      ];
      return `<details class="dg-card dg-tech"><summary><span><b>Detalhes técnicos: tudo que saiu do Refino</b></span><em>Abrir</em></summary><dl class="dg-kv">${rows.map(([k, v]) => `<div><dt>${escapeHtml(k)}</dt><dd>${escapeHtml(String(v))}</dd></div>`).join('')}</dl></details>`;
    }
  }

  ns.DiagnosticoScreen = DiagnosticoScreen;
  ns.DiagnosticoModel = { regionsOf, fluidityOf, stateModel, regionLine, IDLE };
})(typeof window !== 'undefined' ? window : globalThis);
