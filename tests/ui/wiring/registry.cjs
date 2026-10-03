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
