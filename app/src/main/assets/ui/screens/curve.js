(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Palavras únicas de toda escrita na ECU (core/display-rules.js).
  function wording() { return root.OmegasUi.DisplayRules.OPERATION_WORDING; }
  const RESET_NOTE = 'Resetar a Curva K para 1.0 · A foto da curva atual foi salva antes. Use Desfazer para voltar.';
  function finite(value) { return Number.isFinite(Number(value)) ? Number(value) : null; }
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
      this.view = 'editor';
      this.learningSignature = '';
      this.bind();
    }

    bind() {
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
      document.querySelectorAll('[data-curve-view]').forEach(button => button.addEventListener('click', () => this.setView(button.dataset.curveView || 'editor')));
      document.querySelectorAll('[data-curve-nudge]').forEach(button => button.addEventListener('click', () => this.nudgeActive(Number(button.dataset.curveNudge) || 0)));
      document.getElementById('curveClearProposals')?.addEventListener('click', () => {
        this.cancelRestorePreview('Restauração descartada · nenhuma escrita enviada');
        this.proposals.clear(); this.renderChart(); this.renderProposalList();
      });
      document.getElementById('curveReviewButton')?.addEventListener('click', () => this.writePrepared());
      document.getElementById('curveDismissResult')?.addEventListener('click', () => this.dismissResult());
      document.getElementById('curveUndoButton')?.addEventListener('click', () => this.undoLast());
    }

    needsLearning() { return this.view === 'learning'; }

    setView(view) {
      if (view !== 'learning' && view !== 'editor') return false;
      this.view = view;
      document.querySelectorAll('[data-curve-view]').forEach(button => button.classList.toggle('active', button.dataset.curveView === this.view));
      document.querySelectorAll('[data-curve-panel]').forEach(panel => panel.classList.toggle('active', panel.dataset.curvePanel === this.view));
      if (this.view === 'learning') this.renderLearning(this.store.get());
      else this.renderChart();
      return true;
    }

    onEnter(context) {
      if (this.backupTask === 'reset-photo') {
        // Voltou à aba com um reset pendente: o toque já passou, não zera. O dono toca de novo se ainda quiser.
        this.backupTask = null;
        text('curveBackupStatus', 'Reset cancelado: a foto não foi confirmada. Toque em Resetar de novo.');
      }
      if (context && context.subpage) this.setView(context.subpage);
      const suggestion = context && context.suggestion;
      if (suggestion) {
        this.pendingSuggestion = suggestion;
        this.setView('editor');
        this.renderSuggestionFocus(suggestion);
      }
      if (!this.data && !this.reading) this.startRead(true);
      if (this.data && suggestion) {
        this.focusSuggestion(suggestion);
        this.prepareSuggestion(suggestion, true);
      }
      this.refreshBackups();
      if (this.view === 'learning') this.renderLearning(this.store.get());
    }

    refreshBackups() {
      const select = document.getElementById('curveBackupSelect');
      const restore = document.getElementById('curveBackupRestore');
      if (!select) return;
      const backups = this.api.curveBackups();
      const rows = Array.isArray(backups) ? backups : [];
      select.innerHTML = rows.length
        ? '<option value="">Escolha um backup…</option>' + rows.map(item => {
            const when = Number(item.createdAt) > 0 ? new Date(Number(item.createdAt)).toLocaleString('pt-BR') : 'data desconhecida';
            const kind = item.type === 'MANUAL_SNAPSHOT' ? 'salvo' : 'automático';
            return `<option value="${escapeHtml(item.fileName)}">${escapeHtml(item.label || 'Curva K')} · ${escapeHtml(when)} · ${kind}</option>`;
          }).join('')
        : '<option value="">Nenhum backup salvo</option>';
      if (restore) {
        restore.disabled = true;
        restore.textContent = 'Restaurar backup';
      }
      if (rows.length) text('curveBackupStatus', `${rows.length} backup${rows.length === 1 ? '' : 's'} disponível${rows.length === 1 ? '' : 'is'} · escolha para pré-visualizar`);
      else text('curveBackupStatus', 'Nenhum backup salvo');
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

    /** Um toque, sem diálogo. Foto antes: salva a curva atual (só leitura) e só então zera; se a foto falhar, nada é zerado. */
    resetCurve() {
      if (this.reading || this.writing || this.backupTask) return;
      const photo = this.api.startCurveBackup('Antes do reset');
      if (!photo?.ok || !photo?.started) {
        this.alert(photo?.error || 'Não foi possível salvar a foto da Curva K; nada foi zerado.');
        return;
      }
      this.backupTask = 'reset-photo';
      text('curveBackupStatus', 'Salvando a foto da curva antes de zerar…');
    }

    /** Segunda etapa do reset: só roda depois que a foto foi gravada em disco. */
    startResetWrite() {
      this.cancelRestorePreview('');
      this.proposals.clear();
      this.renderChart();
      this.renderProposalList();
      const result = this.api.resetCurve();
      if (!result?.ok || !result?.started) {
        this.alert(result?.error || 'Não foi possível iniciar o reset da Curva K.');
        return;
      }
      this.writing = true;
      this.root?.classList.remove('has-result');
      this.root?.classList.add('is-writing');
      text('curveOperationTitle', 'Gravando na ECU… Curva K em 1.0');
      text('curveOperationMessage', RESET_NOTE);
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
      this.root?.classList.remove('is-reading');
      text('curveSourceStatus', 'Curva não confirmada');
      this.store.patch({ curve: { ...this.store.get().curve, state: 'failed', data: null, status: {} } });
      if (message) this.alert(message);
    }

    startRead() {
      if (this.backupTask) return;
      if (this.reading || this.writing) return;
      const result = this.api.startCurveRead();
      if (!result?.ok || !result?.started) {
        this.alert(result?.error || 'Não foi possível iniciar a leitura da Curva K.');
        return;
      }
      this.reading = true;
      this.data = null;
      this.proposals.clear();
      text('curveSourceStatus', 'Lendo 30 pontos diretamente da ECU');
      this.root?.classList.add('is-reading');
      if (this.pendingSuggestion) this.renderSuggestionFocus(this.pendingSuggestion);
    }

    poll() {
      if (!this.reading && !this.writing && !this.backupTask) return;
      const operation = this.api.curveOperation();
      if (!operation) return;
      if (this.backupTask && !operation.busy) {
        const task = this.backupTask;
        this.backupTask = null;
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
        if (task === 'reset-photo') {
          // Só a operação de foto devolve hash e caminho; uma leitura qualquer não autoriza o reset.
          if (!operation.hash || !operation.publicPath) {
            text('curveBackupStatus', 'Reset cancelado: a foto da curva não foi confirmada.');
            this.alert('A foto da Curva K não foi confirmada; nada foi zerado.');
            return;
          }
          this.refreshBackups();
          this.startResetWrite();
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
        text('curveSourceStatus', 'ECU confirmada · 30 pontos');
        this.renderChart();
        this.renderEvidence(this.store.get());
        if (this.pendingSuggestion) {
          this.renderSuggestionFocus(this.pendingSuggestion);
          this.focusSuggestion(this.pendingSuggestion);
          this.prepareSuggestion(this.pendingSuggestion, true);
        } else {
          this.selectPoint(0);
        }
        if (this.view === 'learning') this.renderLearning(this.store.get());
        this.store.patch({ curve: { ...this.store.get().curve, state: 'ready', data: operation, status: {} } });
        return;
      }

      if (this.writing) {
        const progress = Math.max(0, Math.min(100, finite(operation.progress) || finite(operation.writerProgress) || 0));
        const bar = document.getElementById('curveOperationProgress');
        if (bar) bar.style.width = `${progress}%`;
        text('curveOperationTitle', operation.message || operation.writerMessage || wording().stages.join(' · '));
        if (!operation.busy) {
          this.writing = false;
          if (operation.state === 'BATCH_CONFIRMED' && operation.readbackValid === true) {
            this.root?.classList.remove('is-writing');
            this.root?.classList.add('has-result');
            const result = document.getElementById('curveOperationResult');
            if (result) {
              result.dataset.level = 'ok';
              result.querySelector('b').textContent = wording().doneTitle('Curva K');
              result.querySelector('span').textContent = wording().doneDetail;
              this.showUndo(true);
            }
            this.data = null;
            this.proposals.clear();
            this.pendingSuggestion = null;
            if (this.restoreContext) text('curveBackupStatus', 'Backup restaurado e confirmado pela ECU');
            this.restoreContext = null;
            this.refreshBackups();
            this.startRead(true);
          } else {
            this.root?.classList.remove('is-writing');
            this.root?.classList.add('has-result');
            const result = document.getElementById('curveOperationResult');
            if (result) {
              result.dataset.level = 'critical';
              result.querySelector('b').textContent = wording().failedTitle;
              this.showUndo(false);
              result.querySelector('span').textContent = operation.error || operation.message || wording().failedDetail;
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

    points() { return Array.isArray(this.data?.points) ? this.data.points : []; }

    selectPoint(index) {
      const point = this.points().find(item => Number(item.index) === Number(index));
      if (!point) return;
      this.activeIndex = Number(point.index);
      text('curveActivePoint', `Ponto ${this.activeIndex + 1} · ${fmt(point.petrolMs, 2)} ms`);
      text('curveCurrentFactor', fmt(point.factor, 4));
      const input = document.getElementById('curveTargetFactor');
      if (input) input.value = String(finite(this.proposals.get(this.activeIndex)?.targetFactor ?? point.factor) ?? '');
      this.renderChart();
      this.renderLearningPointContext(this.store.get(), this.activeIndex);
    }

    nudgeActive(delta) {
      if (this.activeIndex === null || !delta) return;
      const input = document.getElementById('curveTargetFactor');
      const current = finite(input?.value) ?? finite(this.points().find(item => Number(item.index) === this.activeIndex)?.factor);
      if (current === null) return;
      if (input) input.value = String(Math.max(0.6, Math.min(4, current + delta)).toFixed(4));
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
      const requested = finite(document.getElementById('curveTargetFactor')?.value);
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

      if (!silent) this.alert('A sugestão ainda não possui alvo K exato calculado pelo Kotlin.');
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
      if (input && index === this.activeIndex && finite(preview.targetFactor) !== null) input.value = String(preview.targetFactor);
      if (index === this.activeIndex) text('curveTargetNormalized', preview.changed ? `${fmt(preview.currentFactor, 4)} → ${fmt(preview.targetFactor, 4)}` : 'Sem alteração');
      if (!deferRender) {
        this.renderChart();
        this.renderProposalList();
      }
    }

    renderChart() {
      const host = document.getElementById('curveChart');
      const points = this.points();
      if (!host) return;
      if (!points.length) {
        host.innerHTML = '<div class="chart-empty">Leia a Curva K para visualizar os 30 pontos.</div>';
        return;
      }
      const width = 920; const height = 350; const padX = 42; const padY = 34;
      const factors = points.map(item => finite(this.proposals.get(Number(item.index))?.targetFactor ?? item.factor) || 0);
      const min = Math.max(0.55, Math.min(...factors, ...points.map(item => finite(item.factor) || 0)) - 0.08);
      const max = Math.max(min + 0.2, Math.max(...factors, ...points.map(item => finite(item.factor) || 0)) + 0.08);
      const xFor = index => padX + (index / Math.max(1, points.length - 1)) * (width - padX * 2);
      const yFor = factor => height - padY - ((factor - min) / (max - min)) * (height - padY * 2);
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
        return `<circle class="curve-point-hit" data-curve-index="${point.index}" cx="${x}" cy="${y}" r="15" tabindex="0" role="button" aria-label="Ponto ${Number(point.index) + 1}, ${fmt(point.petrolMs, 2)} ms"></circle><circle class="curve-point ${selected ? 'active' : ''} ${proposed ? 'proposed' : ''}" cx="${x}" cy="${y}" r="${selected ? 9 : 7}"></circle>${label}`;
      }).join('')}</svg>`;
      host.querySelectorAll('[data-curve-index]').forEach(point => {
        const select = () => this.selectPoint(Number(point.dataset.curveIndex));
        point.addEventListener('click', select);
        point.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); select(); } });
      });
    }

    renderLearning(state) {
      const host = document.getElementById('curveLearningChart');
      const summaryHost = document.getElementById('curveLearningSummary');
      if (!host || !summaryHost) return;
      const currentPoints = this.points();
      const points = Array.from({ length: 30 }, (_, index) => {
        const current = currentPoints.find(item => Number(item.index) === index) || {};
        const proposal = this.proposals.get(index);
        return {
          index,
          petrolMs: finite(current.petrolMs),
          factor: finite(current.factor),
          proposedFactor: finite(proposal?.targetFactor) ?? null,
        };
      });
      const signature = JSON.stringify({ points });
      if (signature === this.learningSignature) return;
      this.learningSignature = signature;

      const heading = this.root?.querySelector('.global-learning-surface .surface-heading h3');
      if (heading) heading.textContent = 'Curva K atual × proposta';
      const legend = this.root?.querySelector('.global-learning-surface .global-legend');
      if (legend) legend.innerHTML = '<span>atual × proposta</span>';

      const width = 920; const height = 180; const py = 22; const px = 42;
      const xFor = index => px + (index / 29) * (width - px * 2);
      const factorValues = points.flatMap(item => [item.factor, item.proposedFactor]).filter(value => value !== null);
      const minFactor = factorValues.length ? Math.min(...factorValues) - 0.05 : 0.8;
      const maxFactor = factorValues.length ? Math.max(...factorValues) + 0.05 : 1.2;
      const factorY = value => height - py - ((Number(value) - minFactor) / Math.max(0.01, maxFactor - minFactor)) * (height - py * 2);
      const actualPath = points.filter(item => item.factor !== null).map((item, pos) => `${pos ? 'L' : 'M'} ${xFor(item.index).toFixed(1)} ${factorY(item.factor).toFixed(1)}`).join(' ');
      const proposedPath = points.filter(item => item.proposedFactor !== null).map((item, pos) => `${pos ? 'L' : 'M'} ${xFor(item.index).toFixed(1)} ${factorY(item.proposedFactor).toFixed(1)}`).join(' ');

      host.innerHTML = `<div class="global-learning-stack">
        <section class="global-k-chart"><small class="global-chart-label">CURVA K · atual × proposta</small><svg viewBox="0 0 ${width} ${height}" role="img" aria-label="Curva K atual e proposta nos mesmos 30 pontos">${actualPath ? `<path class="curve-line actual" d="${actualPath}"></path>` : ''}${proposedPath ? `<path class="curve-line proposal" d="${proposedPath}"></path>` : ''}${points.map(item => item.factor === null ? '' : `<circle data-learning-curve-index="${item.index}" class="curve-point ${item.index === this.activeIndex ? 'active' : ''}" cx="${xFor(item.index).toFixed(1)}" cy="${factorY(item.proposedFactor ?? item.factor).toFixed(1)}" r="${item.index === this.activeIndex ? 7 : 4}"></circle>${item.index % 5 === 0 || item.index === 29 ? `<text class="curve-point-label" x="${xFor(item.index).toFixed(1)}" y="${height - 5}" text-anchor="middle">${fmt(item.petrolMs, 1)}</text>` : ''}`).join('')}</svg></section>
      </div>`;
      host.querySelectorAll('[data-learning-curve-index]').forEach(node => node.addEventListener('click', () => this.selectPoint(Number(node.dataset.learningCurveIndex))));

      const proposed = points.filter(item => item.proposedFactor !== null).length;
      summaryHost.innerHTML = `<div class="editor-heading"><div><small>30 PONTOS FÍSICOS</small><h3>Curva K atual × proposta</h3></div></div><div class="global-summary-grid"><div><small>PONTOS LIDOS</small><b>${points.filter(item => item.factor !== null).length}/30</b></div><div><small>PROPOSTOS</small><b>${proposed}</b></div></div><div id="curveLearningPointContext" class="global-summary-list"></div><p class="empty-copy">O eixo X é Petrol Inj. dos 30 pontos. O erro por ponto volta com a Equivalência. A UI só desenha alvos K exatos vindos do Kotlin.</p>`;
      this.renderLearningPointContext(state, this.activeIndex ?? 0);
    }

    renderLearningPointContext(state, index) {
      const host = document.getElementById('curveLearningPointContext');
      if (!host) return;
      const current = this.points().find(item => Number(item.index) === Number(index)) || {};
      const proposal = this.proposals.get(Number(index));
      const target = finite(proposal?.targetFactor);
      host.innerHTML = `<div><span>Ponto ${Number(index) + 1} · ${fmt(current.petrolMs, 2)} ms</span><b>K atual</b><small>${fmt(current.factor, 4)}</small></div><div><span>proposta</span><b>${fmt(target, 4)}</b></div>`;
    }

    renderProposalList() {
      const host = document.getElementById('curveProposalList');
      if (!host) return;
      const items = [...this.proposals.values()].sort((a, b) => Number(a.index) - Number(b.index));
      host.innerHTML = items.length ? items.map(item => `<div><span>${fmt(item.petrolMs, 2)} ms</span><b>${fmt(item.currentFactor, 4)} → ${fmt(item.targetFactor, 4)}</b><small>${item.deltaPercent > 0 ? '+' : ''}${fmt(item.deltaPercent, 1)}%</small></div>`).join('') : '<p>Nenhum ponto preparado.</p>';
      const review = document.getElementById('curveReviewButton');
      if (review) {
        if (this.restoreContext) {
          review.disabled = true;
          review.textContent = 'Restauração pronta no botão acima';
        } else {
          review.disabled = items.length === 0;
          review.textContent = items.length
            ? `Gravar ${items.length} ponto${items.length === 1 ? '' : 's'} na ECU`
            : 'Prepare pontos';
        }
      }
    }

    renderEvidence(state) {
      const host = document.getElementById('curveEvidenceList');
      if (!host) return;
      host.innerHTML = '<p class="empty-copy">A evidência gasolina × GNV volta com a Curva Própria, em Curva K › Equivalência.</p>';
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
        : 'aguardando alvo exato do Kotlin';
      host.innerHTML = `<b>${prepared ? 'Sugestão preparada para revisão' : 'Sugestão global em revisão'}</b><span>${escapeHtml(label)}</span><small>${escapeHtml(suggestion.rationale || suggestion.reason || suggestion.explanation || '')}</small><button type="button" data-curve-prepare-suggestion ${this.data && persistentChanges.length ? '' : 'disabled'}>${prepared ? 'Repreparar sugestão' : 'Preparar sugestão'}</button><small>${prepared ? 'Revise o antes/depois; nenhuma escrita foi iniciada.' : 'A prévia é normalizada pelo Kotlin. Não grava na ECU.'}</small>`;
      host.querySelector('[data-curve-prepare-suggestion]')?.addEventListener('click', () => this.prepareSuggestion(suggestion));
    }

    writePrepared() {
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
      text('curveOperationTitle', restoring ? 'Gravando na ECU… backup da Curva K' : wording().writing);
      const bar = document.getElementById('curveOperationProgress');
      if (bar) bar.style.width = '0%';
    }

    dismissResult() {
      this.root?.classList.remove('is-writing', 'has-result');
    }

    /** Desfazer = abrir a prévia de restauração da foto mais recente; gravar de volta é o toque seguinte. */
    showUndo(visible) {
      const button = document.getElementById('curveUndoButton');
      if (button) button.hidden = !visible || !this.latestBackupFile();
    }

    latestBackupFile() {
      const rows = this.api.curveBackups();
      const list = Array.isArray(rows) ? rows.filter(item => item && item.fileName) : [];
      list.sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0));
      return list.length ? String(list[0].fileName) : '';
    }

    undoLast() {
      const fileName = this.latestBackupFile();
      this.dismissResult();
      if (fileName) this.prepareRestore(fileName);
    }

    alert(message) { this.store.patch({ alert: { level: 'warning', message: String(message || 'Operação indisponível') } }); }
  }

  ns.CurveScreen = CurveScreen;
})(typeof window !== 'undefined' ? window : globalThis);