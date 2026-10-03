'use strict';
// Defeitos reais conhecidos + probes independentes. Importado por todos os módulos.
const { defect, todo, active } = require('./defects.cjs');
const { boot } = require('./harness.cjs');
const { World } = require('./world.cjs');
const S = require('./scenarios.cjs');

// DEFECT-9: o Refino lança ReferenceError em toda renderização (const `agreed` usada antes de ser declarada).
defect('DEFECT-9', 'Refino lança ReferenceError ao renderizar (refino.js: `agreed` usada antes do const)', () => {
  const app = boot();
  app.go('refino');
  const hit = app.errors.some(e => /agreed/.test(e));
  app.destroy();
  return hit;
});

// DEFECT-1: usbPermissionPending/usbDevice/ecuState chegam do Kotlin e a tela nunca usa: "aguardando permissão USB" é igual a "sem cabo".
defect('DEFECT-1', 'permissão USB pendente (usbPermissionPending) não aparece: a tela é igual a "sem cabo"', () => {
  const text = pending => {
    const w = new World();
    Object.assign(w.status, { usbConnected: false, usbPermissionPending: pending, engineRunning: false, fuelState: '--' });
    w.telemetry = { sequence: 1, updatedAt: 0, valid: false, live: {} };
    const app = boot({ world: w });
    app.settle(2);
    const out = app.doc.body.textContent.replace(/\s+/g, ' ');
    app.destroy();
    return out;
  };
  return text(false) === text(true);
});

// DEFECT-10: conectado mas sem nenhum quadro (ECU muda, idade desconhecida) o Agora diz "operação estável / atualizadas".
defect('DEFECT-10', 'ECU conectada e muda (nenhum quadro) aparece como "Leitura em tempo real · operação estável"', () => {
  const w = new World();
  Object.assign(w.status, { usbConnected: true, engineRunning: true, fuelState: '--' });
  w.telemetry = { sequence: 1, updatedAt: 0, valid: false, live: {} };
  const app = boot({ world: w });
  app.settle(2);
  const txt = app.doc.querySelector('[data-screen="dashboard"]').textContent;
  app.destroy();
  return /operação estável|leitura em tempo real/i.test(txt);
});

const probeApp = make => { const app = make(); return app; };

// DEFECT-11: depois de uma gravação confirmada o botão "Gravar N ponto" e a lista "Pontos preparados" ficam OBSOLETOS:
// botão ativo que não faz nada.
defect('DEFECT-11', 'Curva K: após gravar OK o botão "Gravar 1 ponto" e a lista de pontos preparados ficam obsoletos (botão ativo que não faz nada)', () => {
  const a = S.curveApp(); S.editPoint(a, 9); S.tapReview(a); a.settle(4);
  const stale = !a.byId('curveReviewButton').hasAttribute('disabled') && /Gravar \d+ ponto/.test(a.byId('curveReviewButton').textContent);
  a.destroy();
  return stale;
});
// DEFECT-12: com a leitura falha/ausente os botões ±nudge e "Preparar este ponto" continuam ativos e não fazem nada.
defect('DEFECT-12', 'Curva K: ±nudge e "Preparar este ponto" ficam ativos e mortos quando não há curva lida', () => {
  const a = S.curveApp({ outcome: { curveRead: 'nack' } });
  const live = !a.$('[data-curve-nudge="0.05"]').hasAttribute('disabled') && !a.byId('curvePreparePoint').hasAttribute('disabled');
  a.destroy();
  return live;
});
// DEFECT-13: depois de uma gravação com falha o gráfico continua desenhando a curva ANTIGA (pontos mortos) até reler manualmente.
defect('DEFECT-13', 'Curva K: após falha de gravação o gráfico segue mostrando a curva antiga, com pontos que não respondem', () => {
  const a = S.curveApp({ outcome: { curveWrite: 'partial' } }); S.editPoint(a, 9); S.tapReview(a); a.settle(4);
  const dot = a.$('circle[data-curve-index="15"]');
  const dead = !!dot && require('./lib.cjs').tap(a, dot).effect === 'none';
  a.destroy();
  return dead;
});
// DEFECT-14: toque duplo em Gravar (ECU ocupada) chama a ponte duas vezes: writePrepared não tem guarda.
// DEFECT-16: o fator desconhecido de um ponto aparece como "0,0000" (curve.js finite(null) === 0).
defect('DEFECT-16', 'Curva K: fator desconhecido (null) aparece como "0,0000" em vez de —', () => {
  const W2 = require('./world.cjs');
  const w = new W2.World();
  w.mutateResponse = (b, m, obj) => { if (m === 'getLastOperation' && Array.isArray(obj.points)) obj.points[3].factor = null; return obj; };
  const a = boot({ world: w });
  a.go('curve'); a.settle(4);
  a.$('circle[data-curve-index="3"]').click(); a.flush();
  const bad = /^0([,.]0+)?$/.test(a.byId('curveCurrentFactor').textContent.trim());
  a.destroy();
  return bad;
});
// DEFECT-17: toque duplo em "Gravar" do Mapa K (ECU ocupada) chama startMapBatchWrite duas vezes.
defect('DEFECT-17', 'Mapa K: toque duplo em "Gravar" envia a escrita duas vezes (writePrepared sem guarda de ocupado)', () => {
  const a = S.mapApp({ opPolls: 8 }); a.settle(12);
  const cell = (r, c) => a.$(`.map-k-cell[data-row="${r}"][data-column="${c}"]`);
  [[2, 3], [2, 4]].forEach(([r, c]) => { const e = cell(r, c); e.dispatchEvent(new a.win.Event('pointerdown', { bubbles: true, pointerId: 1 })); e.dispatchEvent(new a.win.Event('pointerup', { bubbles: true, pointerId: 1 })); e.click(); });
  const mode = a.byId('mapAdjustmentMode'); mode.value = 'target'; mode.dispatchEvent(new a.win.Event('change', { bubbles: true }));
  const input = a.byId('mapAdjustmentValue'); input.value = '150'; input.dispatchEvent(new a.win.Event('input', { bubbles: true }));
  const mark = a.world.mark();
  a.byId('mapReviewButton').click(); a.byId('mapReviewButton').click(); a.flush();
  const n = a.world.since(mark).filter(c => c.method === 'startMapBatchWrite').length;
  a.destroy();
  return n > 1;
});
// DEFECT-18: eixo/valor desconhecido do Mapa K vira "0,0 ms" (map.js finite(null) === 0).
defect('DEFECT-18', 'Mapa K: bin de Petrol Inj. desconhecido (null) aparece como "0,0 ms" em vez de —', () => {
  const w = new World();
  w.mutateResponse = (b, m, obj) => { if (m === 'getKMapReadResult' && obj.axes) obj.axes.petrolBins[2] = null; return obj; };
  const a = boot({ world: w });
  a.go('map'); a.settle(4);
  const bad = /^\s*0([.,]0+)?\s*ms/.test(a.$('[data-select-row="2"]').textContent);
  a.destroy();
  return bad;
});
// DEFECT-19: se listRecordedSessions falha, a aba Sessões fica em "Lendo as sessões salvas…" para sempre.
defect('DEFECT-19', 'Sessões: falha ao listar vira "Lendo as sessões salvas…" eterno (sem erro legível nem próxima ação)', () => {
  const w = new World();
  w.throwOn.add('listRecordedSessions');
  const a = boot({ world: w });
  a.go('sessions'); a.advance(10000); a.settle(4);
  const stuck = /Lendo as sess/i.test(a.doc.querySelector('[data-screen="sessions"]').textContent);
  a.destroy();
  return stuck;
});
// DEFECT-20: plural errado: "1 apagõo" (sessions.js monta 'apagõ' + 'o').
defect('DEFECT-20', 'Sessões: singular escrito "1 apagõo" em vez de "1 apagão"', () => {
  const w = new World();
  w.sessions = [{ id: 'session_2026-10-02_10-00-00', reason: 'USB', durationMs: 60000, bytes: 10, cngTicks: 1, petrolTicks: 1, semanticSummary: { blackouts: 1 } }];
  const a = boot({ world: w });
  a.go('sessions'); a.settle(6);
  const bad = /apagõo/.test(a.doc.querySelector('[data-screen="sessions"]').textContent);
  a.destroy();
  return bad;
});
// DEFECT-21: configuração de sessão não numérica vira "NaN"/"Infinity" no campo e no texto de Ferramentas.
defect('DEFECT-21', 'Ferramentas: retenção de sessões não numérica (keepSessions/maxSessionMb) aparece como NaN/Infinity', () => {
  const w = new World();
  w.sessionStatus = { ...w.sessionStatus, settings: { telemetryEveryMs: 250, maxSessionMb: 256, keepSessions: 'abc' } };
  const a = boot({ world: w });
  a.go('tools'); a.settle(6);
  const bad = /NaN|Infinity/.test(a.doc.querySelector('[data-screen="tools"]').textContent);
  a.destroy();
  return bad;
});
// DEFECT-22: getRefinedAnalysis.points que não é lista derruba a renderização do Refino (TypeError em refino.js:482).
defect('DEFECT-22', 'Refino: análise com points não-lista lança TypeError ao renderizar (refino.js:482)', () => {
  const w = new World();
  w.refined = { ok: true, available: true, points: 'abc' };
  const a = boot({ world: w });
  a.go('refino'); a.settle(4);
  const hit = a.errors.some(e => /map is not a function/.test(e));
  a.destroy();
  return hit;
});
defect('DEFECT-14', 'Curva K: toque duplo em "Gravar" envia a escrita duas vezes (writePrepared sem guarda de ocupado)', () => {
  const a = S.curveApp({ opPolls: 8 }); a.settle(12); S.editPoint(a, 9);
  const mark = a.world.mark();
  a.byId('curveReviewButton').click(); a.byId('curveReviewButton').click(); a.flush();
  const n = a.world.since(mark).filter(c => c.method === 'startCurveBatchWrite').length;
  a.destroy();
  return n > 1;
});
// DEFECT-15: "Salvar curva atual": o caminho/hash do arquivo é escrito e logo apagado por refreshBackups().
defect('DEFECT-15', 'Curva K: depois de salvar a foto o caminho/hash do arquivo nunca aparece (refreshBackups sobrescreve)', () => {
  const a = S.curveApp(); a.byId('curveBackupSave').click(); a.settle(3);
  const shown = /Omegas|abcdef12/.test(a.byId('curveBackupStatus').textContent);
  a.destroy();
  return !shown;
});

module.exports = { todo, active };
