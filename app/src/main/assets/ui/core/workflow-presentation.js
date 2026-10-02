(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  const COPY = {
    offline: ['Sem ECU', 'Conecte o cabo para iniciar a leitura.', 'muted'],
    reading: ['Lendo a ECU', 'Aguarde a leitura; nada está sendo gravado.', 'ok'],
    collecting: ['Coletando', 'Dirija normalmente; o app acompanha os pontos.', 'ok'],
    insufficient: ['Dados insuficientes', 'Dirija em gasolina e GNV para completar a comparação.', 'warn'],
    ready: ['Pronto para revisar', 'Revise a proposta antes de confirmar qualquer gravação.', 'warn'],
    writing: ['Gravando na ECU', 'Aguarde a confirmação e a releitura. Não desconecte o cabo.', 'danger'],
    verified: ['Verificado', 'A releitura ou a comparação confirmou o resultado.', 'ok'],
    divergent: ['Resultado divergente', 'O resultado não foi confirmado. Confira a conexão e revise antes de tentar novamente.', 'danger'],
  };
  function state(s, eq) {
    let key = 'insufficient';
    if (s.status?.usbConnected !== true) key = 'offline';
    else {
      const op = s.route === 'curve' ? s.telemetry?.k_factor : s.route === 'map' ? s.telemetry?.k_write : null;
      const name = String(op?.state || '').toUpperCase();
      if (/FAIL|ERROR|DIVERG|REJECT|ABORT/.test(name)) key = 'divergent';
      else if (name === 'BATCH_CONFIRMED') key = op.readbackValid === true || op.details?.readbackValid === true ? 'verified' : 'divergent';
      else if (op?.busy === true) key = /WRITE|WRIT|VERIFY/.test(name) ? 'writing' : 'reading';
      else if (s.route === 'curve' && s.curve?.proposal || s.route === 'map' && s.map?.review || s.route === 'suggestions' && Number(s.calibrationState?.suggestionPending) > 0) key = 'ready';
      else if (s.route === 'dashboard' || s.route === 'autocal') {
        key = { ECU_TRABALHANDO: 'collecting', COLETANDO_NOSSOS: 'insufficient', PROPOSTA_PRONTA: 'ready', VERIFICANDO: 'collecting', ESTAVEL: 'verified', RESTAURAR_TRECHO: 'divergent' }[eq?.autopilot?.phase] || 'insufficient';
      } else if (s.route === 'learning') key = 'collecting';
      else if (s.route === 'tools' && s.telemetry?.levelSensor?.state === 'READING') key = 'reading';
    }
    const [title, next, tone] = COPY[key];
    return { key, title, next, tone };
  }
  function render(s, eq) {
    const screen = root.document?.querySelector(`[data-screen="${s.route}"]`);
    if (!screen) return;
    let node = screen.querySelector('.workflow-state');
    if (!node) {
      node = root.document.createElement('div');
      node.className = 'workflow-state';
      node.setAttribute('role', 'status');
      node.setAttribute('aria-live', 'polite');
      screen.prepend(node);
    }
    const value = state(s, eq);
    node.dataset.state = value.key;
    node.dataset.tone = value.tone;
    node.textContent = `${value.title} · ${value.next}`;
  }
  ns.WorkflowPresentation = { state, render };
})(typeof window !== 'undefined' ? window : globalThis);
