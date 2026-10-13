(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  const PROPOSAL_ROWS = 4;

  // Desconhecido (null/vazio) fica desconhecido: Number(null) seria 0 e viraria "0,0% · equivalente".
  function finite(value) { return value === null || value === undefined || value === '' || !Number.isFinite(Number(value)) ? null : Number(value); }
  function fmt(value, digits) {
    const n = finite(value);
    return n === null ? '—' : n.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits });
  }
  function text(id, value) {
    const node = document.getElementById(id);
    if (!node) return;
    const next = value == null ? '—' : String(value);
    if (node.textContent !== next) node.textContent = next;
  }
  // Campo "Fator desejado" com vírgula decimal (dono, 2026-10-11: "1,02", como o resto do app); aceita vírgula ou ponto.
  // Mesmos dígitos de antes (sem arredondar o alvo normalizado pela ECU), só a vírgula muda.
  function factorText(value) {
    const n = finite(value);
    return n === null ? '' : String(n).replace('.', ',');
  }
  function parseFactor(value) {
    return finite(typeof value === 'string' ? value.trim().replace(',', '.') : value);
  }
  // Arrasto de nó (dono, 2026-10-13): mesmos limites do botão de ajuste fino (0,6 a 1,8); passo de 0,001 (precisão da ECU em Q14 é maior).
  // Teto de gravação da curva K (dono, 2026-10-13): nenhum nó sobe acima de 1,8 por erro. Mesmo valor do KFactorProtocol.MAX_WRITE_FACTOR.
  const DRAG_FACTOR_MIN = 0.6;
  const DRAG_FACTOR_MAX = 1.8;
  function dragFactor(yView, scale) {
    const raw = scale.min + ((scale.height - scale.padY - yView) / (scale.height - scale.padY * 2)) * (scale.max - scale.min);
    return Math.round(Math.min(DRAG_FACTOR_MAX, Math.max(DRAG_FACTOR_MIN, raw)) * 1000) / 1000;
  }
  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[char]));
  }

  class CurveScreen {
    constructor(store, api) {
      this.store = store;
      this.api = api;
      this.root = document.querySelector('[data-screen="curve"]');
      this.data = null;
      this.activeIndex = null;
      this.proposals = new Map();
      this.pendingSuggestion = null;
      this.reading = false;
      this.writing = false;
      this.backupTask = null;
      this.restoreContext = null;
      this.bind();
    }

    bind() {
      // A leitura é automática (reconexão + 15 s), mas o botão fica para o dono forçar a releitura quando quiser (2026-10-13).
      document.getElementById('curveReadButton')?.addEventListener('click', () => this.startRead());
      document.getElementById('curveBackupSave')?.addEventListener('click', () => this.saveBackup());
      document.getElementById('curveResetButton')?.addEventListener('click', () => this.resetCurve());
      document.getElementById('curveBackupRestore')?.addEventListener('click', () => this.writeRestore());
      document.getElementById('curveBackupSelect')?.addEventListener('change', event => {
        const fileName = String(event.target?.value || '');
        this.cancelRestorePreview('');
        if (fileName) this.prepareRestore(fileName);
      });
      document.getElementById('curvePreparePoint')?.addEventListener('click', () => this.prepareActivePoint());
      document.querySelectorAll('[data-curve-nudge]').forEach(button => button.addEventListener('click', () => this.nudgeActive(Number(button.dataset.curveNudge) || 0)));
      document.getElementById('curveClearProposals')?.addEventListener('click', () => {
        this.cancelRestorePreview('Restauração descartada · nenhuma escrita enviada');
        this.proposals.clear(); this.renderChart(); this.renderProposalList();
      });
      document.getElementById('curveReviewButton')?.addEventListener('click', () => this.writePrepared());
      document.getElementById('curveDismissResult')?.addEventListener('click', () => this.dismissResult());
      this.bindDrag();
    }

    /**
     * Arrastar um nó da Curva K (como no original, que grava ao soltar): aqui soltar só PREPARA a prévia do ponto;
     * a escrita continua exigindo a revisão e a leitura de volta (invariante do dono). Durante o arrasto só o nó,
     * a linha tracejada e o valor mudam (um quadro por requestAnimationFrame); nada é relido nem redesenhado por inteiro.
     */
    bindDrag() {
      const host = document.getElementById('curveChart');
      if (!host || typeof host.addEventListener !== 'function') return;
      const raf = typeof requestAnimationFrame === 'function' ? requestAnimationFrame : callback => setTimeout(callback, 16);
      host.addEventListener('pointerdown', event => {
        // O nó visível (data-curve-dot) fica por cima da área de toque (data-curve-index): os dois iniciam o arrasto.
        const hit = event.target?.closest?.('[data-curve-index], [data-curve-dot]');
        if (!hit || !this.chartScale || event.button > 0 || this.writing) return;
        const index = Number(hit.dataset.curveIndex ?? hit.dataset.curveDot);
        const point = this.points().find(item => Number(item.index) === index);
        if (!point) return;
        event.preventDefault();
        this.drag = null;
        this.selectPoint(index);
        const start = this.proposals.get(index)?.targetFactor ?? point.factor;
        this.drag = { index, pointerId: event.pointerId, start: Number(start), factor: Number(start), moved: false, frame: 0 };
        try { host.setPointerCapture?.(event.pointerId); } catch (_) { /* sem captura: o arrasto segue pelo host */ }
        host.classList?.add('dragging');
      });
      host.addEventListener('pointermove', event => {
        const drag = this.drag;
        if (!drag || event.pointerId !== drag.pointerId || !this.chartScale) return;
        const svg = host.querySelector('svg');
        const box = svg?.getBoundingClientRect?.();
        if (!box || !(box.height > 0)) return;
        const yView = ((event.clientY - box.top) / box.height) * this.chartScale.height;
        drag.factor = dragFactor(yView, this.chartScale);
        drag.moved = drag.moved || Math.abs(drag.factor - drag.start) >= 0.001;
        if (!drag.frame) drag.frame = raf(() => { drag.frame = 0; this.paintDrag(); });
      });
      const finish = event => {
        const drag = this.drag;
        if (!drag || (event && event.pointerId !== drag.pointerId)) return;
        this.drag = null;
        host.classList?.remove('dragging');
        try { host.releasePointerCapture?.(drag.pointerId); } catch (_) { /* já solto */ }
        if (!drag.moved) { this.renderChart(); return; }
        const input = document.getElementById('curveTargetFactor');
        if (input) input.value = factorText(drag.factor);
        this.prepareActivePoint();
      };
      host.addEventListener('pointerup', finish);
      host.addEventListener('pointercancel', event => {
        const drag = this.drag;
        if (!drag || event.pointerId !== drag.pointerId) return;
        this.drag = null;
        host.classList?.remove('dragging');
        this.renderChart();
      });
    }

    /** Move só o que muda durante o arrasto: nó, linha tracejada e o valor mostrado. */
    paintDrag() {
      const drag = this.drag;
      const scale = this.chartScale;
      const host = document.getElementById('curveChart');
      if (!drag || !scale || !host) return;
      const y = scale.yFor(drag.factor).toFixed(1);
      host.querySelectorAll(`[data-curve-index="${drag.index}"], [data-curve-dot="${drag.index}"]`).forEach(node => node.setAttribute('cy', y));
      const path = host.querySelector('.curve-line.proposal');
      if (path) {
        path.setAttribute('d', this.points().map((point, position) => {
          const value = Number(point.index) === drag.index ? drag.factor : (this.proposals.get(Number(point.index))?.targetFactor ?? point.factor);
          return `${position ? 'L' : 'M'} ${scale.xFor(position).toFixed(1)} ${scale.yFor(Number(value)).toFixed(1)}`;
        }).join(' '));
      }
      const input = document.getElementById('curveTargetFactor');
      if (input) input.value = factorText(drag.factor);
      text('curveTargetNormalized', `${fmt(drag.start, 4)} → ${fmt(drag.factor, 4)}`);
    }

    onEnter(context) {
      const suggestion = context && context.suggestion;
      if (suggestion) {
        this.pendingSuggestion = suggestion;
        this.renderSuggestionFocus(suggestion);
      }
      // Toda entrada na aba relê a Curva K da ECU (dono, 2026-10-11); o botão Reler ECU continua.
      if (!this.reading && !this.writing && !this.backupTask) this.startRead(true);
      if (this.data && suggestion) {
        this.focusSuggestion(suggestion);
        this.prepareSuggestion(suggestion, true);
      }
      if (!this.backupTask && !this.restoreContext) text('curveBackupStatus', '');
      this.refreshBackups();
    }

    refreshBackups() {
      const select = document.getElementById('curveBackupSelect');
      const restore = document.getElementById('curveBackupRestore');
      if (!select) return;
      const backups = this.api.curveBackups();
      const rows = Array.isArray(backups) ? backups : [];
      // "Backups (N)" compacto no rodapé (dono, 2026-10-11); a lista completa abre ao tocar.
      select.innerHTML = `<option value="">Backups (${rows.length})</option>` + rows.map(item => {
        const when = Number(item.createdAt) > 0 ? new Date(Number(item.createdAt)).toLocaleString('pt-BR') : 'data desconhecida';
        const kind = item.type === 'MANUAL_SNAPSHOT' ? 'salvo' : 'automático';
        return `<option value="${escapeHtml(item.fileName)}">${escapeHtml(item.label || 'Curva K')} · ${escapeHtml(when)} · ${kind}</option>`;
      }).join('');
      select.disabled = rows.length === 0;
      if (restore) {
        restore.disabled = true;
        restore.textContent = 'Restaurar backup';
      }
    }

    saveBackup() {
      if (this.reading || this.writing || this.backupTask) return;
      const result = this.api.startCurveBackup('Curva salva manualmente');
      if (!result?.ok || !result?.started) {
        this.alert(result?.error || 'Não foi possível salvar a Curva K.');
        return;
      }
      this.backupTask = 'save';
      text('curveBackupStatus', 'Salvando curva atual…');
    }

    /**
     * "Curva K toda em 1,0" em um toque (dono, 2026-10-11: sem dupla confirmação; proteção = cópia antes).
     * 1) guarda uma cópia da curva atual pelo mesmo caminho do botão "Salvar curva atual";
     * 2) só com a cópia confirmada (poll → startResetAfterBackup) o reset começa; cópia que falha não reseta.
     */
    resetCurve() {
      if (this.reading || this.writing || this.backupTask) return;
      const backup = this.api.startCurveBackup('Cópia antes de pôr a Curva K em 1,0');
      if (!backup?.ok || !backup?.started) {
        this.alert(`Não foi possível guardar a cópia da curva. A Curva K não foi mexida. ${backup?.error || ''}`.trim());
        return;
      }
      this.backupTask = 'reset-backup';
      this.resetBackupStartedAt = backup.startedAt ?? null;
      text('curveBackupStatus', 'Guardando cópia antes de pôr em 1,0…');
    }

    /** Segundo passo do reset: só roda depois da cópia confirmada (arquivo + hash) da curva atual. */
    startResetAfterBackup() {
      this.cancelRestorePreview('');
      this.proposals.clear();
      this.renderChart();
      this.renderProposalList();
      const result = this.api.resetCurve();
      if (!result?.ok || !result?.started) {
        this.alert(result?.error || 'Não foi possível pôr a Curva K em 1,0. A cópia ficou guardada.');
        return;
      }
      this.writing = true;
      this.root?.classList.remove('has-result');
      this.root?.classList.add('is-writing');
      text('curveOperationTitle', 'Pondo a Curva K toda em 1,0');
      text('curveOperationMessage', 'Gravando e conferindo…');
      const bar = document.getElementById('curveOperationProgress');
      if (bar) bar.style.width = '0%';
    }

    prepareRestore(fileName = String(document.getElementById('curveBackupSelect')?.value || '')) {
      if (this.reading || this.writing || this.backupTask) return;
      if (!fileName) return;
      const restore = document.getElementById('curveBackupRestore');
      if (restore) {
        restore.disabled = true;
        restore.textContent = 'Validando backup…';
      }
      this.restoreContext = null;
      this.proposals.clear();
      this.renderChart();
      this.renderProposalList();
      const result = this.api.prepareCurveRestore(fileName);
      if (!result?.ok || !result?.started) {
        const select = document.getElementById('curveBackupSelect');
        if (select) select.value = '';
        if (restore) {
          restore.disabled = true;
          restore.textContent = 'Restaurar backup';
        }
        this.alert(result?.error || 'Não foi possível preparar a restauração.');
        return;
      }
      this.backupTask = 'restore-preview';
      text('curveBackupStatus', 'Conferindo backup e curva atual…');
    }

    writeRestore() {
      if (this.reading || this.writing || this.backupTask) return;
      if (!this.restoreContext || !this.proposals.size) {
        this.alert('Escolha um backup e aguarde a prévia antes de restaurar.');
        return;
      }
      this.writePrepared();
    }

    cancelRestorePreview(message) {
      if (!this.restoreContext) return;
      this.restoreContext = null;
      const select = document.getElementById('curveBackupSelect');
      const restore = document.getElementById('curveBackupRestore');
      if (select) select.value = '';
      if (restore) {
        restore.disabled = true;
        restore.textContent = 'Restaurar backup';
      }
      if (message) text('curveBackupStatus', message);
    }

    settleReadFailure(message) {
      this.reading = false;
      this.data = null;
      // Sem leitura confirmada não há base para propostas: tudo volta a "—".
      this.proposals.clear();
      this.cancelRestorePreview('');
      this.renderChart();
      this.renderProposalList();
      this.root?.classList.remove('is-reading');
      text('curveSourceStatus', 'Curva não confirmada');
      this.store.patch({ curve: { ...this.store.get().curve, state: 'failed', data: null, status: {} } });
      if (message) this.alert(message);
    }

    /**
     * Atualização automática (dono, 2026-10-13: "não tenho que clicar em Reler"): o app.js chama isto ao reconectar a ECU e
     * a cada 15 s na aba. Leitura silenciosa: sem faixa "Lendo…", só troca o gráfico se a ECU mudou. Não mexe enquanto o
     * dono edita (proposta preparada) ou grava.
     */
    autoSync(justConnected) {
      if (this.reading || this.writing || this.backupTask) return;
      if (this.data && this.proposals.size > 0 && !justConnected) return;
      this.startRead(true, !!this.data);
    }

    startRead(onEnter = false, silent = false) {
      if (this.reading || this.writing) return;
      const result = this.api.startCurveRead();
      if (!result?.ok || !result?.started) {
        // Releitura de entrada sem ECU não vira aviso a cada toque na aba; a curva mostrada é dita como a última lida.
        if (onEnter && this.data) { text('curveSourceStatus', 'Última leitura · ECU sem resposta'); return; }
        // Sem curva na tela, o gráfico diz o que fazer (dono, 2026-10-11) em vez de "a leitura começa ao abrir".
        if (!this.data) { text('curveSourceStatus', 'Curva não lida'); this.renderChart(); }
        this.alert(result?.error || 'Não foi possível iniciar a leitura da Curva K.');
        return;
      }
      // A curva anterior fica na tela durante a leitura; ao chegar, só sobrevivem propostas feitas sobre o mesmo valor da ECU.
      this.reading = true;
      if (!this.data) {
        const host = document.getElementById('curveChart');
        if (host) host.innerHTML = '<div class="chart-empty">Lendo a Curva K da ECU…</div>';
      }
      if (!silent) {
        text('curveSourceStatus', 'Lendo 30 pontos diretamente da ECU');
        this.root?.classList.add('is-reading');
      }
      if (this.pendingSuggestion) this.renderSuggestionFocus(this.pendingSuggestion);
    }

    poll() {
      if (!this.reading && !this.writing && !this.backupTask) return;
      const operation = this.api.curveOperation();
      if (!operation) return;
      if (this.backupTask && !operation.busy) {
        const task = this.backupTask;
        this.backupTask = null;
        if (task === 'reset-backup') {
          // A cópia precisa ser esta (mesmo início), estar completa e ter arquivo + hash; senão a curva não é mexida.
          const startedAt = this.resetBackupStartedAt;
          this.resetBackupStartedAt = null;
          const sameOperation = startedAt == null || operation.startedAt == null || operation.startedAt === startedAt;
          const saved = operation.state === 'COMPLETED' && operation.ok === true && sameOperation &&
            Boolean(operation.fileName) && Boolean(operation.hash);
          this.refreshBackups();
          if (!saved) {
            text('curveBackupStatus', 'Cópia não guardada · Curva K não foi mexida');
            this.alert(`Não foi possível guardar a cópia da curva. A Curva K não foi mexida. ${operation.error || ''}`.trim());
            return;
          }
          text('curveBackupStatus', 'Cópia guardada · pondo a Curva K em 1,0');
          this.startResetAfterBackup();
          return;
        }
        if (operation.state !== 'COMPLETED' || !operation.ok) {
          this.restoreContext = null;
          const select = document.getElementById('curveBackupSelect');
          const restore = document.getElementById('curveBackupRestore');
          if (select) select.value = '';
          if (restore) {
            restore.disabled = true;
            restore.textContent = 'Restaurar backup';
          }
          text('curveBackupStatus', 'Backup indisponível');
          this.alert(operation.error || 'Operação de backup da Curva K falhou.');
          return;
        }
        if (task === 'save') {
          if (operation.curve && Array.isArray(operation.curve.points) && operation.curve.points.length === 30) {
            this.data = operation.curve;
            this.renderChart();
          }
          text('curveBackupStatus', (operation.publicPath || 'Download/Omegas') + ' · ' + String(operation.hash || '').slice(0, 8));
          this.refreshBackups();
          return;
        }
        if (task === 'restore-preview') {
          const points = Array.isArray(operation.points) ? operation.points : [];
          if (operation.currentCurve && Array.isArray(operation.currentCurve.points)) {
            this.data = operation.currentCurve;
            this.renderChart();
          }
          if (!points.length) {
            this.restoreContext = null;
            const restore = document.getElementById('curveBackupRestore');
            if (restore) {
              restore.disabled = true;
              restore.textContent = 'Já está igual';
            }
            text('curveBackupStatus', 'A ECU já está igual ao backup');
            this.alert('A Curva K atual já é idêntica ao backup escolhido.');
            return;
          }
          this.restoreContext = {
            fileName: operation.fileName,
            hash: operation.hash,
            createdAt: operation.createdAt,
            label: operation.label || 'Backup Curva K',
          };
          this.proposals.clear();
          points.forEach(item => this.proposals.set(Number(item.index), {
            index: Number(item.index),
            petrolMs: Number(item.petrolMs),
            currentRaw: Number(item.currentRaw),
            targetRaw: Number(item.targetRaw),
            currentFactor: Number(item.currentFactor),
            targetFactor: Number(item.targetFactor),
            deltaPercent: Number(item.deltaPercent),
          }));
          this.renderProposalList();
          const restore = document.getElementById('curveBackupRestore');
          if (restore) {
            restore.disabled = false;
            restore.textContent = `Restaurar ${points.length} ponto${points.length === 1 ? '' : 's'} na ECU`;
          }
          text('curveBackupStatus', 'Restauração pronta · confira o antes→depois e use o botão Restaurar');
          return;
        }
      }

      if (this.reading && !operation.busy) {
        if (operation.state !== 'COMPLETED' && !operation.demo) {
          this.settleReadFailure(operation.error || 'A leitura da Curva K não foi confirmada pela ECU.');
          return;
        }
        this.reading = false;
        this.root?.classList.remove('is-reading');
        if (!operation.ok || !Array.isArray(operation.points) || operation.points.length !== 30) {
          this.settleReadFailure(operation.error || 'A Curva K não retornou os 30 pontos válidos.');
          return;
        }
        this.data = operation;
        this.dropStaleProposals(operation.points);
        text('curveSourceStatus', 'ECU confirmada · 30 pontos');
        this.renderChart();
        this.renderProposalList();
        if (this.pendingSuggestion) {
          this.renderSuggestionFocus(this.pendingSuggestion);
          this.focusSuggestion(this.pendingSuggestion);
          this.prepareSuggestion(this.pendingSuggestion, true);
        } else {
          this.selectPoint(this.activeIndex ?? 0);
        }
          this.store.patch({ curve: { ...this.store.get().curve, state: 'ready', data: operation, status: {} } });
        return;
      }

      if (this.writing) {
        const progress = Math.max(0, Math.min(100, finite(operation.progress) || finite(operation.writerProgress) || 0));
        const bar = document.getElementById('curveOperationProgress');
        if (bar) bar.style.width = `${progress}%`;
        text('curveOperationTitle', operation.message || operation.writerMessage || 'Gravando e conferindo…');
        if (!operation.busy) {
          this.writing = false;
          // "Gravado" só depois do ACK e da leitura de volta da ECU (BATCH_CONFIRMED + readbackValid); o texto é para leigo.
          if (operation.state === 'BATCH_CONFIRMED' && operation.readbackValid === true) {
            this.root?.classList.remove('is-writing');
            this.root?.classList.add('has-result');
            const result = document.getElementById('curveOperationResult');
            if (result) {
              result.dataset.level = 'ok';
              result.querySelector('b').textContent = 'Curva K confirmada pela ECU';
              result.querySelector('span').textContent = 'Gravado e conferido na ECU.';
            }
            this.data = null;
            this.proposals.clear();
            this.pendingSuggestion = null;
            if (this.restoreContext) text('curveBackupStatus', 'Backup restaurado e confirmado pela ECU');
            this.restoreContext = null;
            this.refreshBackups();
            this.startRead();
          } else {
            this.root?.classList.remove('is-writing');
            this.root?.classList.add('has-result');
            const result = document.getElementById('curveOperationResult');
            if (result) {
              result.dataset.level = 'critical';
              result.querySelector('b').textContent = 'A Curva K não foi confirmada';
              result.querySelector('span').textContent = operation.error || operation.message || 'A ECU não confirmou toda a operação. Releitura obrigatória.';
            }
            this.data = null;
            if (this.restoreContext) text('curveBackupStatus', 'Restauração não confirmada · releitura obrigatória');
            this.restoreContext = null;
            this.proposals.clear();
            this.renderProposalList();
          }
        }
      }
    }

    /** Proposta preparada sobre um valor que a ECU já não tem perde a validade (a escrita também recusaria). */
    dropStaleProposals(points) {
      const fresh = new Map((Array.isArray(points) ? points : []).map(point => [Number(point.index), Number(point.factorRaw)]));
      const before = this.proposals.size;
      [...this.proposals.keys()].forEach(index => {
        if (fresh.get(index) !== Number(this.proposals.get(index)?.currentRaw)) this.proposals.delete(index);
      });
      if (this.restoreContext && this.proposals.size !== before) {
        this.proposals.clear();
        this.cancelRestorePreview('A Curva K mudou na ECU · escolha o backup de novo');
      }
    }

    points() { return Array.isArray(this.data?.points) ? this.data.points : []; }

    selectPoint(index) {
      const point = this.points().find(item => Number(item.index) === Number(index));
      if (!point) return;
      this.activeIndex = Number(point.index);
      text('curveActivePoint', `Ponto ${this.activeIndex + 1} · ${fmt(point.petrolMs, 2)} ms`);
      text('curveCurrentFactor', fmt(point.factor, 4));
      const input = document.getElementById('curveTargetFactor');
      if (input) input.value = factorText(this.proposals.get(this.activeIndex)?.targetFactor ?? point.factor);
      this.renderChart();
    }

    nudgeActive(delta) {
      if (this.activeIndex === null || !delta) return;
      const input = document.getElementById('curveTargetFactor');
      const current = parseFactor(input?.value) ?? finite(this.points().find(item => Number(item.index) === this.activeIndex)?.factor);
      if (current === null) return;
      if (input) input.value = factorText(Number(Math.max(DRAG_FACTOR_MIN, Math.min(DRAG_FACTOR_MAX, current + delta)).toFixed(4)));
      this.prepareActivePoint();
    }

    focusSuggestion(suggestion) {
      const changes = Array.isArray(suggestion?.curveChanges) ? suggestion.curveChanges : [];
      if (changes.length) {
        this.selectPoint(Number(changes[0].index));
        return this.points().find(item => Number(item.index) === Number(changes[0].index)) || null;
      }
      const point = this.resolveSuggestionPoint(suggestion);
      if (point) this.selectPoint(Number(point.index));
      return point;
    }

    resolveSuggestionPoint(suggestion) {
      if (!suggestion) return null;
      const explicitIndex = finite(suggestion.index);
      if (explicitIndex !== null) {
        const exact = this.points().find(item => Number(item.index) === Number(explicitIndex));
        if (exact) return exact;
      }
      const targetMs = finite(suggestion.petrolMs);
      if (targetMs === null || !this.points().length) return null;
      return this.points().slice().sort((a, b) =>
        Math.abs(Number(a.petrolMs) - targetMs) - Math.abs(Number(b.petrolMs) - targetMs)
      )[0] || null;
    }

    prepareActivePoint() {
      if (this.activeIndex === null) return;
      if (this.restoreContext) {
        this.cancelRestorePreview('Prévia de restauração descartada por edição manual');
        this.proposals.clear();
      }
      const requested = parseFactor(document.getElementById('curveTargetFactor')?.value);
      if (requested === null) { this.alert('Informe o fator K desejado.'); return; }
      const preview = this.api.previewCurvePoint(this.activeIndex, requested);
      if (!preview?.ok) { this.alert(preview?.error || 'Prévia da Curva K inválida.'); return; }
      this.acceptPreview(preview);
    }

    prepareSuggestion(suggestion = this.pendingSuggestion, silent = false) {
      if (this.restoreContext) {
        this.cancelRestorePreview('Prévia de restauração descartada por nova sugestão');
        this.proposals.clear();
      }
      if (!suggestion || !this.data) {
        if (!silent) this.alert('Aguarde a leitura da Curva K antes de preparar a sugestão.');
        return false;
      }
      const persistentChanges = Array.isArray(suggestion.curveChanges) ? suggestion.curveChanges : [];
      if (persistentChanges.length) {
        let changed = false;
        persistentChanges.forEach(change => {
          const index = Number(change.index);
          const requested = finite(change.after);
          if (!Number.isInteger(index) || requested === null) return;
          const preview = this.api.previewCurvePoint(index, requested);
          if (!preview?.ok) return;
          preview.preparedFromSuggestion = true;
          preview.suggestionId = suggestion.id || '';
          this.acceptPreview(preview, true);
          changed = changed || preview.changed === true;
        });
        this.renderChart();
        this.renderProposalList();
        this.renderSuggestionFocus(suggestion, { changed });
        return changed;
      }

      if (!silent) this.alert('Esta sugestão ainda não tem um valor exato para gravar.');
      return false;
    }

    acceptPreview(preview, deferRender = false) {
      const index = Number(preview.index);
      if (!Number.isInteger(index)) {
        this.alert('Prévia da Curva K sem índice válido.');
        return;
      }
      if (!preview.changed) this.proposals.delete(index);
      else this.proposals.set(index, preview);
      const input = document.getElementById('curveTargetFactor');
      if (input && index === this.activeIndex && finite(preview.targetFactor) !== null) input.value = factorText(preview.targetFactor);
      if (index === this.activeIndex) text('curveTargetNormalized', preview.changed ? `${fmt(preview.currentFactor, 4)} → ${fmt(preview.targetFactor, 4)}` : 'Sem alteração');
      if (!deferRender) {
        this.renderChart();
        this.renderProposalList();
      }
    }

    renderChart() {
      const host = document.getElementById('curveChart');
      // Durante o arrasto ninguém redesenha o gráfico por baixo do dedo (a leitura periódica também passa aqui).
      if (this.drag) return;
      const points = this.points();
      if (!host) return;
      if (!points.length) {
        host.innerHTML = '<div class="chart-empty">Curva não lida. Ligue a ECU: ela aparece sozinha.</div>';
        return;
      }
      const width = 920; const height = 350; const padX = 42; const padY = 34;
      const factors = points.map(item => finite(this.proposals.get(Number(item.index))?.targetFactor ?? item.factor) || 0);
      const min = Math.max(0.55, Math.min(...factors, ...points.map(item => finite(item.factor) || 0)) - 0.08);
      const max = Math.max(min + 0.2, Math.max(...factors, ...points.map(item => finite(item.factor) || 0)) + 0.08);
      const xFor = index => padX + (index / Math.max(1, points.length - 1)) * (width - padX * 2);
      const yFor = factor => height - padY - ((factor - min) / (max - min)) * (height - padY * 2);
      this.chartScale = { min, max, width, height, padX, padY, xFor, yFor };
      // Alvo de toque: 20 no celular (dedo), 15 com mouse; os nós ficam a ~28 px um do outro.
      const hitRadius = typeof matchMedia === 'function' && matchMedia('(pointer: coarse)').matches ? 20 : 15;
      const actualPath = points.map((point, index) => `${index ? 'L' : 'M'} ${xFor(index).toFixed(1)} ${yFor(Number(point.factor)).toFixed(1)}`).join(' ');
      const proposalPath = points.map((point, index) => {
        const proposal = this.proposals.get(Number(point.index));
        const value = proposal ? proposal.targetFactor : point.factor;
        return `${index ? 'L' : 'M'} ${xFor(index).toFixed(1)} ${yFor(Number(value)).toFixed(1)}`;
      }).join(' ');
      host.innerHTML = `<svg class="curve-svg" viewBox="0 0 ${width} ${height}" role="img" aria-label="Curva K com 30 pontos editáveis"><path class="curve-line actual" d="${actualPath}"></path><path class="curve-line proposal" d="${proposalPath}"></path>${points.map((point, index) => {
        const selected = Number(point.index) === this.activeIndex;
        const proposed = this.proposals.has(Number(point.index));
        const y = yFor(proposed ? this.proposals.get(Number(point.index)).targetFactor : point.factor).toFixed(1);
        const x = xFor(index).toFixed(1);
        const label = index % 5 === 0 || index === points.length - 1 ? `<text class="curve-point-label" x="${x}" y="${height - 8}" text-anchor="middle">${fmt(point.petrolMs, 1)}</text>` : '';
        return `<circle class="curve-point-hit" data-curve-index="${point.index}" cx="${x}" cy="${y}" r="${hitRadius}" tabindex="0" role="button" aria-label="Ponto ${Number(point.index) + 1}, ${fmt(point.petrolMs, 2)} ms"></circle><circle class="curve-point ${selected ? 'active' : ''} ${proposed ? 'proposed' : ''}" data-curve-dot="${point.index}" cx="${x}" cy="${y}" r="${selected ? 9 : 7}"></circle>${label}`;
      }).join('')}</svg>`;
      host.querySelectorAll('[data-curve-index]').forEach(point => {
        const select = () => this.selectPoint(Number(point.dataset.curveIndex));
        point.addEventListener('click', select);
        point.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); select(); } });
      });
    }

    renderProposalList() {
      const host = document.getElementById('curveProposalList');
      if (!host) return;
      const items = [...this.proposals.values()].sort((a, b) => Number(a.index) - Number(b.index));
      // Sem sub-rolagem (dono, 2026-10-11): as primeiras linhas aqui; todos os pontos aparecem no gráfico.
      const shown = items.slice(0, PROPOSAL_ROWS);
      const more = items.length - shown.length;
      host.innerHTML = items.length
        ? shown.map(item => `<div><span>${fmt(item.petrolMs, 2)} ms</span><b>${fmt(item.currentFactor, 4)} → ${fmt(item.targetFactor, 4)}</b><small>${item.deltaPercent > 0 ? '+' : ''}${fmt(item.deltaPercent, 1)}%</small></div>`).join('')
          + (more > 0 ? `<p class="proposal-more">+${more} ponto${more === 1 ? '' : 's'} · veja no gráfico</p>` : '')
        : '<p>Toque num ponto do gráfico e em “Preparar este ponto”.</p>';
      const review = document.getElementById('curveReviewButton');
      if (review) {
        if (this.restoreContext) {
          review.disabled = true;
          review.textContent = 'Toque em Restaurar, no rodapé';
        } else {
          review.disabled = items.length === 0;
          review.textContent = items.length
            ? `Gravar ${items.length} ponto${items.length === 1 ? '' : 's'} na ECU`
            : 'Nenhum ponto preparado';
        }
      }
    }

    renderSuggestionFocus(suggestion, preparedPreview = null) {
      const host = document.getElementById('curveSuggestionFocus');
      if (!host || !suggestion) return;
      this.pendingSuggestion = suggestion;
      host.hidden = false;
      const persistentChanges = Array.isArray(suggestion.curveChanges) ? suggestion.curveChanges : [];
      const prepared = preparedPreview?.changed === true || persistentChanges.some(change => this.proposals.has(Number(change.index)));
      const label = persistentChanges.length
        ? `${persistentChanges.length} ponto${persistentChanges.length === 1 ? '' : 's'} da Curva K`
        : 'ainda sem valor exato para gravar';
      host.innerHTML = `<b>${prepared ? 'Sugestão preparada para revisão' : 'Sugestão global em revisão'}</b><span>${escapeHtml(label)}</span><small>${escapeHtml(suggestion.rationale || suggestion.reason || suggestion.explanation || '')}</small><button type="button" data-curve-prepare-suggestion ${this.data && persistentChanges.length ? '' : 'disabled'}>${prepared ? 'Preparar de novo' : 'Preparar sugestão'}</button><small>${prepared ? 'Confira o antes → depois; nada foi gravado.' : 'Preparar não grava na ECU.'}</small>`;
      host.querySelector('[data-curve-prepare-suggestion]')?.addEventListener('click', () => this.prepareSuggestion(suggestion));
    }

    writePrepared() {
      if (this.reading || this.writing) return;
      const points = [...this.proposals.values()].map(item => ({
        index: Number(item.index),
        currentRaw: Number(item.currentRaw),
        targetRaw: Number(item.targetRaw),
      }));
      if (!points.length) return;
      const restoring = this.restoreContext !== null;
      const reason = restoring
        ? `Restaurar backup Curva K ${this.restoreContext.fileName}`
        : 'Ajuste manual confirmado na UI clean-slate';
      const result = this.api.writeCurve(points, reason);
      if (!result?.ok || !result?.started) {
        if (restoring) {
          this.restoreContext = null;
          this.proposals.clear();
          this.renderProposalList();
          text('curveBackupStatus', 'Restauração não iniciada');
        }
        this.alert(result?.error || 'A escrita da Curva K não iniciou.');
        return;
      }
      this.writing = true;
      this.root?.classList.add('is-writing');
      text('curveOperationTitle', restoring ? 'Restaurando backup da Curva K' : 'Escrita manual da Curva K');
      const bar = document.getElementById('curveOperationProgress');
      if (bar) bar.style.width = '0%';
    }

    dismissResult() {
      this.root?.classList.remove('is-writing', 'has-result');
    }

    alert(message) { this.store.patch({ alert: { level: 'warning', message: String(message || 'Operação indisponível') } }); }
  }

  ns.CurveScreen = CurveScreen;
  ns.CurveDrag = { dragFactor, FACTOR_MIN: DRAG_FACTOR_MIN, FACTOR_MAX: DRAG_FACTOR_MAX };
})(typeof window !== 'undefined' ? window : globalThis);