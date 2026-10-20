(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Volta automática para a gasolina por PRESSÃO BAIXA do gás (ProgBase F4: "Automatic change back to petrol"). Fonte:
  // docs/progbase/protocolo/volta-automatica-gasolina.md. Lido da central: TEMPO_RITORNO_BENZINA (código 62; 255 = desligada,
  // 0..3 = espera em s) e a tabela RIF_PRESS_DIFF (código 123, mbar; o ponto 0 é o limite que a tela mostra) e
  // COEFF_PRESS_DIFF (código 124). Mudar a pressão recalcula os 15 pontos no nativo (VoltaGasolinaCore), igual ao original.
  const DASH = '—';
  const OFF = 255;
  const TEMPO_MAX = 3;
  const STEP = 50;          // − / + : 0,05 bar
  const MIN = 200;          // DFM EditBassaPressioneMp: 0,2..2,0 bar, passo 0,005
  const MAX = 2000;

  const okValues = (e, n) => (e && e.state === 'OK' && Array.isArray(e.values) && e.values.length === n && e.values.every(Number.isInteger) ? e.values : null);

  function read(params) {
    const p = params || {};
    const t = okValues(p.TEMPO_RITORNO_BENZINA, 1);
    const tempo = t ? t[0] : null;
    const rif = okValues(p.RIF_PRESS_DIFF, 15);
    const coeff = okValues(p.COEFF_PRESS_DIFF, 15);
    return { tempo, on: tempo === null ? null : tempo !== OFF, rif, coeff, limitMbar: rif ? rif[0] : null };
  }

  /** O original trunca (1,2 bar vira 1199 mbar); a tela arredonda ao passo dele (0,005 bar). */
  const snap = mbar => Math.round(mbar / 5) * 5;
  function barText(mbar) {
    if (!Number.isFinite(mbar)) return DASH;
    return (snap(mbar) / 1000).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 3 });
  }
  const waitText = s => (s === null || s === undefined || s === OFF ? DASH : s === 0 ? 'Na hora' : `${s} s`);
  const step = (mbar, dir) => Math.max(MIN, Math.min(MAX, snap(mbar) + dir * STEP));
  const nextWait = s => ((Number.isInteger(s) && s >= 0 && s < TEMPO_MAX) ? s + 1 : 0);

  function sentence(m, mbar) {
    const limit = Number.isFinite(mbar) ? mbar : m.limitMbar;
    if (m.on === false) return 'Desligado: se a pressão do gás cair, o carro não volta sozinho para a gasolina.';
    if (m.on === true && Number.isFinite(limit)) return `Se a pressão do gás cair abaixo de ${barText(limit)} bar, o carro volta sozinho para a gasolina.`;
    return 'Se a pressão do gás cair demais, o carro volta sozinho para a gasolina.';
  }

  function stateOf(on) { return on === null ? DASH : on ? 'Ligado' : 'Desligado'; }

  /** O cartão (mesma forma dos outros de CartoesMotor). */
  function card(params) {
    const m = read(params);
    const tech = [
      `TEMPO_RITORNO_BENZINA · código 62 (0x3E) · ${m.tempo === null ? 'não lido' : m.tempo} (255 = desligada; 0 a 3 = espera em s) · gravar: 12 3E 00 vv soma`,
      `RIF_PRESS_DIFF · código 123 (0x7B) · mbar: ${m.rif ? m.rif.join(' ') : 'não lido'} (ponto 0 = limite; ponto 10 = referência)`,
      `COEFF_PRESS_DIFF · código 124 (0x7C) · ${m.coeff ? m.coeff.join(' ') : 'não lido'}`,
      'Mudar a pressão recalcula os 15 pontos como o ProgBase e grava 30 quadros 14 7B|7C 00 i lo hi soma, um a um, com leitura de volta.',
      'A central compara a pressão do gás menos a do coletor (Press.diff.) com o limite: inferido das telas do original.',
    ];
    return {
      id: 'volta', kind: 'volta', title: 'Volta para gasolina', viewTitle: 'Volta para gasolina',
      line: sentence(m),
      shortLine: m.on === false ? 'Não volta sozinho à gasolina.' : m.on === true && m.limitMbar !== null ? `Gás abaixo de ${barText(m.limitMbar)} bar: volta à gasolina.` : 'Pressão baixa: volta à gasolina.',
      on: m.on, stateText: stateOf(m.on), stateFor: 'Volta sozinha',
      note: 'É uma proteção: se o gás acabar ou a pressão cair, o motor passa para a gasolina em vez de falhar.',
      rows: [
        { label: 'Volta sozinha para a gasolina', text: stateOf(m.on), on: m.on },
        { label: 'Pressão do gás que faz voltar', text: m.limitMbar === null ? DASH : `${barText(m.limitMbar)} bar`, on: null },
        { label: 'Espera antes de voltar', text: m.on === false ? DASH : waitText(m.tempo), on: null },
      ],
      tech,
      model: m,
    };
  }

  /** O que "Salvar" manda: só o que mudou em relação à central. `drafts` = { mbar?, segundos? }. */
  function saveRequest(m, drafts) {
    const d = drafts || {};
    const out = { acao: 'salvar' };
    if (Number.isInteger(d.segundos) && d.segundos !== m.tempo) out.segundos = d.segundos;
    if (Number.isInteger(d.mbar) && (m.limitMbar === null || snap(m.limitMbar) !== d.mbar)) out.mbar = d.mbar;
    return Object.keys(out).length > 1 ? out : null;
  }

  /** Status do nativo → fase e frase de uma linha. "Gravado" só com CONFIRMED (releitura igual). */
  function phaseOf(status) {
    const s = status || {};
    if (s.state === 'WRITING' || s.busy === true) return { phase: 'writing', text: 'Gravando e conferindo…' };
    if (s.state === 'DONE' && s.outcome === 'CONFIRMED') return { phase: 'done', text: 'Salvo e conferido na central ✓' };
    if (s.state === 'DONE' && s.outcome === 'NOTHING_TO_DO') return { phase: 'info', text: s.message || 'Já está assim na central.' };
    if (s.state === 'DONE' || s.state === 'ERROR') return { phase: 'error', text: 'Não gravou. ' + (s.message || 'Confira o cabo e tente de novo.') };
    return { phase: 'idle', text: '' };
  }

  ns.VoltaGasolina = { read, card, barText, waitText, step, nextWait, sentence, saveRequest, phaseOf, OFF, TEMPO_MAX, MIN, MAX, STEP };
})(typeof window !== 'undefined' ? window : globalThis);
