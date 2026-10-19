(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.OmegasWriteVerify = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  /**
   * Conferência em segundo plano (dono, 2026-10-19): gravar é instantâneo (ACK = "Gravado"); a leitura de volta roda depois,
   * sem bloquear nada. Este módulo só guarda o estado {phase, label, detail} e avança a cada RESPOSTA da ECU (telemetria
   * ou ACK), nunca por relógio. A barra do topo desenha o estado: 'conferindo' discreto, âmbar se divergir/corrigir/falhar.
   * poll() devolve o estado nativo: {phase: idle|checking|fixing|ok|failed, detail?, label?}.
   */
  const ACTIVE = new Set(['checking', 'fixing']);
  const LABELS = { checking: 'conferindo', fixing: 'corrigindo', failed: 'não confere' };

  function view(raw) {
    const phase = raw && typeof raw.phase === 'string' ? raw.phase : 'idle';
    if (phase === 'idle' || phase === 'ok') return { phase: 'idle', label: '', detail: '' };
    return { phase, label: LABELS[phase] || '', detail: String(raw.detail || '') };
  }

  class Tracker {
    constructor(poll, onChange) {
      this.poll = poll; this.onChange = onChange || (() => {});
      this.state = { phase: 'idle', label: '', detail: '' };
      this.sticky = false; // falha fica até a próxima gravação: é o aviso
      this.armed = false;  // só consulta depois de uma gravação
    }
    /** Chamar após cada gravação aceita (ACK). */
    begin() {
      this.sticky = false; this.armed = true;
      this.state = { phase: 'checking', label: LABELS.checking, detail: '' }; this.onChange(this.state); // discreto, na hora; sem consultar ainda
      return this.state;
    }
    /** Chamar a cada resposta da ECU. Só consulta enquanto há conferência ativa. */
    tick() {
      if (this.sticky || !this.armed) return this.state;
      let raw = null;
      try { raw = this.poll(); } catch (_) { raw = null; }
      const next = view(raw);
      if (next.phase === 'failed') this.sticky = true;
      if (next.phase === 'idle') this.armed = false;
      if (next.phase !== this.state.phase || next.detail !== this.state.detail) { this.state = next; this.onChange(next); }
      return this.state;
    }
    active() { return ACTIVE.has(this.state.phase); }
  }

  return { Tracker, view, ACTIVE };
});
