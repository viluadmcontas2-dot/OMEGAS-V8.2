(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.OmegasMapFix = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  /**
   * Correção no Mapa K sem sair da tela (dono, 2026-10-13: "interações claras, um botão para gravar no mapa").
   * Fluxo único para QUALQUER proposta de células (reforço do engasgo ou diferença por RPM do Refino):
   *   1) lê o Mapa K da ECU agora; 2) a proposta (planFn) escolhe as células com os eixos reais; 3) o Kotlin calcula os alvos
   *   (prévia do ajuste percentual, o mesmo caminho do Ajuste local); 4) mostra o resumo; 5) só o toque em "Gravar" grava
   *   (ACK + leitura de volta pela ponte); 6) "Desfazer" grava os valores de antes, também conferido.
   * Nenhuma conta de Mapa K é feita aqui; nada é gravado sem o toque. Estados: idle · reading · ready · writing · done · undone · failed.
   */
  const finite = value => value === null || value === undefined || value === '' || !Number.isFinite(Number(value)) ? null : Number(value);

  class MapFix {
    /**
     * @param native  ponte (startMapRead, mapReadResult, previewMapAdjustment, writeMap, mapWriteOperation)
     * @param editor  OmegasMapEditor.MapEditor (reset/load/snapshot/clearSelection/toggle/selectedCells/setTargetOverrides/buildReview)
     * @param env     {subscribe} para teste
     */
    constructor({ native, editor, onChange, onWritten, env }) {
      this.native = native;
      this.editor = editor;
      this.onChange = typeof onChange === 'function' ? onChange : () => {};
      // Avisa a tela quando uma gravação (ou desfazer) foi conferida: ela registra em OmegasFluxo ("Última gravação").
      this.onWritten = typeof onWritten === 'function' ? onWritten : () => {};
      this.subscribe = env?.subscribe || (fn => globalThis.OmegasApp.scheduler.addHook('fast', fn));
      // Evento que encerra a espera sem resposta final: USB desconectado.
      this.offline = env?.offline || (() => globalThis.OmegasApp?.store?.get?.()?.status?.usbConnected === false);
      this.state = { phase: 'idle' };
      // Última gravação conferida: fica FORA de state.phase, então sobrevive a dismiss(), a uma nova leitura e a trocar de tela
      // (dono, 2026-10-27: gravar a próxima não exige desfazer). Uma nova gravação substitui; espelho em OmegasFluxo.pending
      // (localStorage, provisório até o Diário) para voltar depois de recarregar.
      this.last = this.loadLast();
      this.undoWaiters = [];
    }

    loadLast() {
      try {
        const saved = globalThis.OmegasFluxo?.pending?.get?.('refino-mapfix-last');
        return saved && Array.isArray(saved.undoItems) && saved.undoItems.length && !saved.undone ? saved : null;
      } catch (_) { return null; }
    }

    saveLast() {
      try {
        const pending = globalThis.OmegasFluxo?.pending;
        if (!pending) return;
        if (this.last) pending.save('refino-mapfix-last', this.last); else pending.clear('refino-mapfix-last');
      } catch (_) { /* espelho opcional */ }
    }

    get canUndo() { return !!(this.last && this.last.undoItems?.length && !this.last.undone) && !this.busy; }

    set(state) { this.state = state; this.onChange(state); }
    get busy() { return this.state.phase === 'reading' || this.state.phase === 'writing'; }

    /** @param planFn ({axes}) => {ok, cells:[{row,column,deltaPercent}], summary} | {ok:false, reason} */
    start(planFn) {
      if (this.busy) return;
      const started = this.native.startMapRead();
      if (!started?.ok || !started?.started) { this.fail(started?.error || 'Não consegui ler o Mapa K da ECU.'); return; }
      this.set({ phase: 'reading', message: 'Lendo o Mapa K da ECU…' });
      this.poll(() => this.native.mapReadResult(), result => {
        if (result.ok === false || result.state === 'FAILED') { this.fail(result.error || 'Falha ao ler o Mapa K.'); return; }
        try { this.prepare(result, planFn); } catch (error) { this.fail(error.message); }
      });
    }

    prepare(mapResult, planFn) {
      this.editor.reset();
      this.editor.load(mapResult);
      const plan = planFn({ axes: this.editor.snapshot().axes });
      if (!plan?.ok) { this.set({ phase: 'idle', note: plan?.reason || 'Nada a corrigir agora.' }); return; }
      // Um pedido de prévia por percentual (o alvo de cada célula é o que o Kotlin devolve).
      const groups = new Map();
      for (const cell of plan.cells) {
        const delta = finite(cell.deltaPercent);
        if (delta === null) continue;
        if (!groups.has(delta)) groups.set(delta, []);
        groups.get(delta).push(cell);
      }
      const targets = [];
      for (const [delta, cells] of groups) {
        this.editor.clearSelection();
        cells.forEach(cell => this.editor.toggle(Number(cell.row), Number(cell.column)));
        const preview = this.native.previewMapAdjustment(this.editor.selectedCells(), 'percent', delta);
        if (!preview?.ok || !Array.isArray(preview.items)) throw new Error(preview?.error || 'Prévia do Mapa K indisponível.');
        preview.items.forEach(item => targets.push({ row: Number(item.row), column: Number(item.column), after: Number(item.target) }));
      }
      this.editor.setTargetOverrides(targets);
      let review;
      try { review = this.editor.buildReview(); } catch (_) { review = null; }
      if (!review?.items?.length) { this.set({ phase: 'idle', note: 'As células desse lugar já estão no limite do Mapa K: nada a reforçar.' }); return; }
      this.set({
        phase: 'ready',
        items: review.items,
        undoItems: review.items.map(item => ({ ...item, current: item.target, target: item.current })),
        summary: typeof plan.summary === 'function' ? plan.summary(review.items.length) : String(plan.summary || ''),
      });
    }

    /** Grava a lista mostrada (ACK + leitura de volta); só diz "gravado" com BATCH_CONFIRMED + readbackValid. */
    write(kind) {
      const items = kind === 'undo' ? (this.last && !this.last.undone ? this.last.undoItems : this.state.undoItems) : this.state.items;
      if (!Array.isArray(items) || !items.length || this.busy) return;
      const reason = kind === 'undo' ? 'Refino: desfazer correção no Mapa K' : 'Refino: correção no Mapa K';
      const result = this.native.writeMap(items, reason);
      if (!result?.ok || !result?.started) { this.fail(result?.error || 'A gravação não iniciou.'); return; }
      const undoItems = this.state.undoItems;
      this.set({ phase: 'writing', message: 'Gravando e conferindo na ECU…', undoItems });
      const startedAt = result.startedAt ?? null;
      this.poll(() => this.native.mapWriteOperation(), operation => {
        if (operation.state === 'BATCH_CONFIRMED' && operation.readbackValid === true) {
          if (kind === 'undo') {
            if (this.last) this.last = { ...this.last, undone: true };
            this.saveLast();
            this.set({ phase: 'undone' });
            this.onWritten({ kind: 'undo', last: this.last });
            this.settle(true);
          } else {
            this.last = { count: items.length, items, undoItems, undone: false };
            this.saveLast();
            this.set({ phase: 'done', undoItems });
            this.onWritten({ kind: 'apply', last: this.last });
          }
        } else {
          this.fail(operation.failure?.error || operation.error || 'A ECU não confirmou a gravação. Confira o Mapa K antes de dirigir.');
        }
      }, operation => startedAt == null || operation.startedAt === startedAt);
    }

    /** Desfaz a última gravação conferida (mesmo depois de dispensar). Devolve uma promessa: true só com leitura de volta igual. */
    undo() {
      return new Promise(resolve => {
        if (!this.canUndo) { resolve(false); return; }
        this.undoWaiters.push(resolve);
        this.write('undo');
        if (this.state.phase === 'failed') this.settle(false);
      });
    }

    settle(ok) { const waiting = this.undoWaiters.splice(0); waiting.forEach(resolve => resolve(ok)); }
    dismiss() { if (!this.busy) this.set({ phase: 'idle' }); }

    poll(read, onFinish, accept = () => true) {
      let off = () => {};
      const tick = () => {
        // Status primeiro: resposta final (ex.: gravação confirmada) vale mesmo se o USB caiu logo depois.
        const status = read() || {};
        const waiting = !accept(status) || status.busy === true || /QUEUED|READING|WRITING/.test(String(status.state || ''));
        if (waiting) {
          if (this.offline()) {
            off();
            this.fail('O cabo soltou: a operação foi interrompida. Releia o Mapa K ao reconectar.');
          }
          return;
        }
        off();
        onFinish(status);
      };
      off = this.subscribe(tick);
    }

    fail(message) { this.set({ phase: 'failed', message: String(message || 'Falha') }); this.settle(false); }
  }

  return { MapFix };
});
