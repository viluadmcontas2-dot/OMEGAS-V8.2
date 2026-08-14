import { humanFuel, humanLearningState, format, setText } from './common.js';

export const agoraRoute = {
  mount(ctx, state) {
    ctx.workspace.innerHTML = `
      <section class="route-page now-page" data-route="agora">
        <div class="route-heading"><div><h1>Agora</h1><p>O que o motor está fazendo neste instante — sem misturar o presente com o que foi aprendido.</p></div></div>
        <div class="now-layout">
          <div class="now-primary">
            <section class="live-cockpit" aria-label="Telemetria atual">
              <div class="metric primary"><span class="metric-label">RPM</span><strong class="metric-value" id="now-rpm">—</strong><span class="metric-origin">MP48 • agora</span></div>
              <div class="metric"><span class="metric-label">Petrol Inj.</span><strong class="metric-value"><span id="now-petrol">—</span><span class="metric-unit">ms</span></strong><span class="metric-origin">comando gasolina • agora</span></div>
              <div class="metric"><span class="metric-label">MAP</span><strong class="metric-value"><span id="now-map">—</span><span class="metric-unit">bar</span></strong><span class="metric-origin">carga do motor</span></div>
              <div class="metric"><span class="metric-label">Gas Inj.</span><strong class="metric-value"><span id="now-gas">—</span><span class="metric-unit">ms</span></strong><span class="metric-origin">diagnóstico • não é referência</span></div>
            </section>
            <button class="cell-context-button" id="current-cell-button" type="button">
              <span><strong>Região atual</strong><small id="current-cell-copy">Calculando posição física…</small></span>
              <span class="cell-badge" id="current-cell-badge">—</span>
            </button>
          </div>
          <aside class="learning-now" aria-label="Aprendendo agora">
            <span class="section-kicker">Aprendendo agora</span>
            <strong class="learning-state" id="learning-state">Aguardando dados</strong>
            <p class="learning-reason" id="learning-reason">O Learning não interfere na telemetria enquanto prepara o contexto.</p>
            <div class="learning-meta"><span class="state-chip" id="learning-source">Learning</span><span class="state-chip" id="learning-fuel">—</span></div>
          </aside>
        </div>
      </section>`;
    document.getElementById('current-cell-button')?.addEventListener('click', async () => {
      await ctx.loadCellContext();
      ctx.router.navigate('aprender');
    });
    this.update(ctx, state);
  },

  update(_ctx, state) {
    const telemetry = state.telemetry || {};
    setText('now-rpm', telemetry.valid ? Math.round(Number(telemetry.rpm) || 0) : '—');
    setText('now-petrol', telemetry.valid ? format(telemetry.petrolMs, 2) : '—');
    setText('now-map', telemetry.valid ? format(telemetry.mapBar, 2) : '—');
    setText('now-gas', telemetry.valid && telemetry.gasMsDiagnostic != null ? format(telemetry.gasMsDiagnostic, 2) : '—');
    setText('learning-fuel', telemetry.valid ? humanFuel(telemetry.fuel) : '—');
    const learning = state.learning || {};
    setText('learning-state', learning.label || humanLearningState(learning.state));
    setText('learning-reason', learning.reason || 'Ainda não há conclusão científica para esta condição.');
    setText('learning-source', learning.source === 'OBD' ? 'OBD' : 'Comparação gasolina↔GNV');
    const cell = state.cellContext?.cell;
    setText('current-cell-badge', cell ? `${cell.row + 1}:${cell.column + 1}` : '—');
    setText('current-cell-copy', cell
      ? 'Contexto físico RPM × Petrol Inj. • tocar abre Aprender; não seleciona escrita'
      : 'Toque para abrir Aprender quando houver contexto válido');
  },
};
