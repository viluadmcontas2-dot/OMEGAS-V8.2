(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Palavras únicas de toda escrita na ECU (core/display-rules.js).
  function wording() { return root.OmegasUi.DisplayRules.OPERATION_WORDING; }
  function failureText(operation, fallback) { return root.OmegasUi.DisplayRules.failureText(operation, fallback); }
  const NEUTRAL_RAW = 16384; // 1.0 em Q14
  const RESET_NOTE = 'Resetar a Curva K para 1,000. A foto da curva atual foi salva antes; use Desfazer para voltar.';
  const D = root.OmegasUi.DisplayRules;
  const finite = D.finite;
  const fmt = D.fmt;
  const escapeHtml = D.escapeHtml;
  function text(id, value) {
    const node = document.getElementById(id);
    if (!node) return;
    const next = value == null ? '—' : String(value);
    if (node.textContent !== next) node.textContent = next;
  }
  class CurveScreen {
    constructor(store, api) {
      this.store = store;
      this.api = api;
      this.root = document.querySelector('[data-screen="curve"]');
      this.data = null;
      this.activeIndex = null;
      this.proposals = new Map();
      this.reading = false;
      this.writing = false;
      this.backupTask = null;
      this.restoreContext = null;
      // A foto que o Desfazer restaura: a que o Kotlin guardou ANTES desta escrita, nunca "a mais nova".
      this.undoFile = '';
      this.resetPhotoFile = '';
      this.writeKind = '';
      this.pendingReset = false;
      this.view = 'editor';
      this.overviewSignature = '';
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
        this.cancelRestorePreview('Desfazer descartado · nada foi enviado à ECU');
        this.proposals.clear(); this.renderChart(); this.renderProposalList();
      });
      document.getElementById('curveReviewButton')?.addEventListener('click', () => this.writePrepared());
      document.getElementById('curveDismissResult')?.addEventListener('click', () => this.dismissResult());
      document.getElementById('curveUndoButton')?.addEventListener('click', () => this.undoLast());
    }

    needsOverview() { return this.view === 'overview'; }

    setView(view) {
      if (view !== 'overview' && view !== 'editor') return false;
      this.view = view;
      document.querySelectorAll('[data-curve-view]').forEach(button => button.classList.toggle('active', button.dataset.curveView === this.view));
      document.querySelectorAll('[data-curve-panel]').forEach(panel => panel.classList.toggle('active', panel.dataset.curvePanel === this.view));
      if (this.view === 'overview') this.renderOverview(this.store.get());
      else this.renderChart();
      return true;
    }

    onEnter(context) {
      if (this.backupTask === 'reset-photo') {
        // Voltou à aba com um reset pendente: o toque já passou, não zera. O dono toca de novo se ainda quiser.
        this.backupTask = null;
        this.resetPhotoFile = '';
        text('curveBackupStatus', 'Reset cancelado: a foto não foi confirmada. Toque em Resetar de novo.');
      }
      if (context && context.subpage) this.setView(context.subpage);
      // Vindo do AutoCal: o toque lá já foi o do dono. O reset roda aqui (foto antes, depois zera)
      // assim que a curva estiver lida; só uma vez.
      if (context && context.resetNow === true) this.pendingReset = true;
      if (!this.data && !this.reading) this.startRead(true);
      if (this.pendingReset && this.data && !this.reading) {
        this.pendingReset = false;
        this.resetCurve();
      }
      this.refreshBackups();
      if (this.view === 'overview') this.renderOverview(this.store.get());
    }

    refreshBackups() {
      const select = document.getElementById('curveBackupSelect');
      const restore = document.getElementById('curveBackupRestore');
      if (!select) return;
      const backups = this.api.curveBackups();
      const rows = Array.isArray(backups) ? backups : [];
      select.innerHTML = rows.length
        ? '<option value="">Escolha uma foto…</option>' + rows.map(item => {
            const when = Number(item.createdAt) > 0 ? new Date(Number(item.createdAt)).toLocaleString('pt-BR') : 'data desconhecida';
            const kind = item.type === 'MANUAL_SNAPSHOT' ? 'salva' : 'automática';
            return `<option value="${escapeHtml(item.fileName)}">${escapeHtml(item.label || 'Curva K')} · ${escapeHtml(when)} · ${kind}</option>`;
          }).join('')
        : '<option value="">Nenhuma foto salva</option>';
      if (restore) {
        restore.disabled = true;
        restore.textContent = 'Desfazer (voltar à foto)';
      }
      if (rows.length) text('curveBackupStatus', `${D.plural(rows.length, 'foto salva', 'fotos salvas')} · escolha uma para ver o que volta`);
      else text('curveBackupStatus', 'Nenhuma foto salva');
    }

    saveBackup() {
      if (this.reading || this.writing || this.backupTask) return;
      const result = this.api.startCurveBackup('Curva salva manualmente');
      if (!result?.ok || !result?.started) {
        this.alert(result?.error || 'Não foi possível salvar a foto da Curva K.');
        return;
      }
      this.backupTask = 'save';
      text('curveBackupStatus', 'Salvando curva atual…');
    }

    /** Um toque, sem diálogo. Foto antes: salva a curva atual (só leitura) e só então zera; se a foto falhar, nada é zerado. */
    resetCurve() {
      if (this.reading || this.writing || this.backupTask) return;
      // Curva já neutra: não há o que zerar e uma foto dela sobrescreveria o ponto de Desfazer útil.
      if (this.curveIsNeutral()) {
        text('curveBackupStatus', 'A Curva K já está em 1,000 · nada a zerar.');
        return;
      }
      this.resetPhotoFile = '';
      const photo = this.api.startCurveBackup('Antes do reset');
      if (!photo?.ok || !photo?.started) {
        this.alert(photo?.error || 'Não foi possível salvar a foto da Curva K; nada foi zerado.');
        return;
      }
      this.backupTask = 'reset-photo';
      text('curveBackupStatus', 'Salvando a foto da curva antes de zerar…');
    }

    /** Os 30 pontos lidos já valem 1.0 (raw 16384): nada a zerar. */
    curveIsNeutral() {
      const points = this.points();
      return points.length === 30 && points.every(item => Number(item.factorRaw) === NEUTRAL_RAW);
    }

    /** Segunda etapa do reset: só roda depois que a foto foi gravada em disco. */
    startResetWrite() {
      this.cancelRestorePreview('');
      this.proposals.clear();
      this.renderChart();
      this.renderProposalList();
      const result = this.api.resetCurve();
      if (!result?.ok || !result?.started) {
        this.alert(failureText(result, 'Não foi possível iniciar o reset da Curva K.'));
        return;
      }
      this.writeKind = 'reset';
      this.undoFile = '';
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
        restore.textContent = 'Conferindo a foto…';
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
          restore.textContent = 'Desfazer (voltar à foto)';
        }
        this.alert(result?.error || 'Não foi possível preparar o Desfazer.');
        return;
      }
      this.backupTask = 'restore-preview';
      text('curveBackupStatus', 'Conferindo a foto e a curva atual…');
    }

    writeRestore() {
      if (this.reading || this.writing || this.backupTask) return;
      if (!this.restoreContext || !this.proposals.size) {
        this.alert('Escolha uma foto e aguarde a prévia antes de desfazer.');
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
        restore.textContent = 'Desfazer (voltar à foto)';
      }
      if (message) text('curveBackupStatus', message);
    }

    settleReadFailure(message) {
      this.reading = false;
      this.data = null;
      this.pendingReset = false;
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
            restore.textContent = 'Desfazer (voltar à foto)';
          }
          text('curveBackupStatus', 'Foto indisponível');
          this.alert(failureText(operation, 'O app não conseguiu salvar ou ler a foto da Curva K.'));
          return;
        }
        if (task === 'reset-photo') {
          // Só a operação de foto devolve hash e caminho; uma leitura qualquer não autoriza o reset.
          if (!operation.hash || !operation.publicPath || !operation.fileName) {
            text('curveBackupStatus', 'Reset cancelado: a foto da curva não foi confirmada.');
            this.alert('A foto da Curva K não foi confirmada; nada foi zerado.');
            return;
          }
          // O Desfazer deste reset restaura EXATAMENTE esta foto (um segundo reset não a substitui).
          this.resetPhotoFile = String(operation.fileName);
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
              restore.textContent = 'Já está igual à foto';
            }
            text('curveBackupStatus', 'A ECU já está igual à foto');
            this.alert('A Curva K atual já é igual à foto escolhida.');
            return;
          }
          this.restoreContext = {
            fileName: operation.fileName,
            hash: operation.hash,
            createdAt: operation.createdAt,
            label: operation.label || 'Foto da Curva K',
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
            restore.textContent = `Desfazer · ${D.plural(points.length, 'ponto', 'pontos')}`;
          }
          text('curveBackupStatus', 'Pronto: confira antes→depois e toque em Desfazer');
          return;
        }
      }

      if (this.reading && !operation.busy) {
        if (operation.state !== 'COMPLETED' && !operation.demo) {
          this.settleReadFailure(failureText(operation, 'A leitura da Curva K não foi confirmada pela ECU.'));
          return;
        }
        this.reading = false;
        this.root?.classList.remove('is-reading');
        if (!operation.ok || !Array.isArray(operation.points) || operation.points.length !== 30) {
          this.settleReadFailure(failureText(operation, 'A Curva K não retornou os 30 pontos válidos.'));
          return;
        }
        this.data = operation;
        text('curveSourceStatus', 'ECU confirmada · 30 pontos');
        this.renderChart();
        this.renderEvidence(this.store.get());
        if (this.pendingReset) {
          this.pendingReset = false;
          this.resetCurve();
        }
        this.selectPoint(0);
        if (this.view === 'overview') this.renderOverview(this.store.get());
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
            // Desfazer = a foto desta operação. Reset: a foto tirada antes dele; escrita/restauração: a que o
            // Kotlin guardou antes do primeiro ACK (`photoFile`). Sem foto (ou sem nada alterado) não há Desfazer.
            const unchanged = operation.details && Number(operation.details.changedPoints) === 0;
            this.undoFile = unchanged ? '' : String((this.writeKind === 'reset' && this.resetPhotoFile) || operation.photoFile || '');
            const result = document.getElementById('curveOperationResult');
            if (result) {
              result.dataset.level = 'ok';
              result.querySelector('b').textContent = wording().doneTitle('Curva K');
              result.querySelector('span').textContent = wording().doneDetail;
              this.showUndo(true);
            }
            this.data = null;
            this.proposals.clear();
            if (this.restoreContext) text('curveBackupStatus', 'Foto restaurada e confirmada pela ECU');
            this.restoreContext = null;
            this.refreshBackups();
            this.startRead(true);
          } else {
            this.root?.classList.remove('is-writing');
            this.root?.classList.add('has-result');
            // Falha com a ECU possivelmente alterada (parcial): o Desfazer volta à foto tirada antes.
            const mayHaveChanged = operation.partial === true || operation.mutationMayHaveStarted === true;
            this.undoFile = mayHaveChanged
              ? String((this.writeKind === 'reset' && this.resetPhotoFile) || operation.photoFile || '')
              : '';
            const result = document.getElementById('curveOperationResult');
            if (result) {
              result.dataset.level = 'critical';
              result.querySelector('b').textContent = mayHaveChanged ? 'ECU parcialmente alterada' : wording().failedTitle;
              this.showUndo(mayHaveChanged);
              result.querySelector('span').textContent = failureText(operation, wording().failedDetail);
            }
            this.data = null;
            if (this.restoreContext) text('curveBackupStatus', 'O app não conseguiu confirmar na ECU · releia a curva');
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
      text('curveCurrentFactor', D.kValue(point.factor));
      const input = document.getElementById('curveTargetFactor');
      if (input) { const shown = finite(this.proposals.get(this.activeIndex)?.targetFactor ?? point.factor); input.value = shown === null ? '' : String(Math.round(shown * 1000) / 1000); }
      this.renderChart();
      this.renderOverviewPointContext(this.store.get(), this.activeIndex);
    }

    nudgeActive(delta) {
      if (this.activeIndex === null || !delta) return;
      const input = document.getElementById('curveTargetFactor');
      const current = finite(input?.value) ?? finite(this.points().find(item => Number(item.index) === this.activeIndex)?.factor);
      if (current === null) return;
      if (input) input.value = String(Math.max(0.6, Math.min(4, current + delta)).toFixed(3));
      this.prepareActivePoint();
    }

    prepareActivePoint() {
      if (this.activeIndex === null) return;
      if (this.restoreContext) {
        this.cancelRestorePreview('Prévia do Desfazer descartada por edição manual');
        this.proposals.clear();
      }
      const requested = finite(document.getElementById('curveTargetFactor')?.value);
      if (requested === null) { this.alert('Informe o K desejado.'); return; }
      const preview = this.api.previewCurvePoint(this.activeIndex, requested);
      if (!preview?.ok) { this.alert(preview?.error || 'Prévia da Curva K inválida.'); return; }
      this.acceptPreview(preview);
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
      if (input && index === this.activeIndex && finite(preview.targetFactor) !== null) input.value = String(Math.round(preview.targetFactor * 1000) / 1000);
      if (index === this.activeIndex) text('curveTargetNormalized', preview.changed ? `${D.kValue(preview.currentFactor)} → ${D.kValue(preview.targetFactor)}` : 'Sem alteração');
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
        host.innerHTML = '<div class="chart-empty">Leia a Curva K para ver os 30 pontos.</div>';
        return;
      }
      // Desenho em pixels reais do quadro: alvo de toque de 48 px (círculo invisível por baixo do ponto) e texto legível.
      const width = Math.max(320, Math.round(host.clientWidth) || 600);
      const height = Math.max(160, Math.round(host.clientHeight) || 300);
      const padLeft = 54; const padRight = 22; const padTop = 20; const padBottom = 34;
      const factors = points.map(item => finite(this.proposals.get(Number(item.index))?.targetFactor ?? item.factor) || 0);
      const factorList = [...factors, ...points.map(item => finite(item.factor) || 0)].filter(Number.isFinite);
      const lowFactor = factorList.length ? Math.min(...factorList) : 1;
      const highFactor = factorList.length ? Math.max(...factorList) : 1;
      const min = Math.max(0.55, lowFactor - 0.08);
      const max = Math.max(min + 0.2, highFactor + 0.08);
      const xFor = index => padLeft + (index / Math.max(1, points.length - 1)) * (width - padLeft - padRight);
      const yFor = factor => height - padBottom - ((factor - min) / (max - min)) * (height - padTop - padBottom);
      const actualPath = points.map((point, index) => `${index ? 'L' : 'M'} ${xFor(index).toFixed(1)} ${yFor(Number(point.factor)).toFixed(1)}`).join(' ');
      const proposalPath = points.map((point, index) => {
        const proposal = this.proposals.get(Number(point.index));
        const value = proposal ? proposal.targetFactor : point.factor;
        return `${index ? 'L' : 'M'} ${xFor(index).toFixed(1)} ${yFor(Number(value)).toFixed(1)}`;
      }).join(' ');
      const yTicks = Array.from({ length: 4 }, (_, i) => min + (i * (max - min)) / 3);
      const grid = yTicks.map(v => `<line class="curve-grid-line" x1="${padLeft}" y1="${yFor(v).toFixed(1)}" x2="${width - padRight}" y2="${yFor(v).toFixed(1)}"></line><text class="curve-tick-label" x="${padLeft - 6}" y="${(yFor(v) + 5).toFixed(1)}" text-anchor="end">${D.kValue(v)}</text>`).join('');
      host.innerHTML = `<svg class="curve-svg" viewBox="0 0 ${width} ${height}" width="${width}" height="${height}" role="img" aria-label="Curva K com 30 pontos editáveis">${grid}<path class="curve-line actual" d="${actualPath}"></path><path class="curve-line proposal" d="${proposalPath}"></path>${points.map((point, index) => {
        const selected = Number(point.index) === this.activeIndex;
        const proposed = this.proposals.has(Number(point.index));
        const y = yFor(proposed ? this.proposals.get(Number(point.index)).targetFactor : point.factor).toFixed(1);
        const x = xFor(index).toFixed(1);
        const label = index % 5 === 0 || index === points.length - 1 ? `<text class="curve-point-label" x="${x}" y="${height - 8}" text-anchor="middle">${fmt(point.petrolMs, 1)}</text>` : '';
        return `<circle class="curve-point-hit" data-curve-index="${point.index}" cx="${x}" cy="${y}" r="24" tabindex="0" role="button" aria-label="Ponto ${Number(point.index) + 1}, ${D.msUnit(point.petrolMs)}"></circle><circle class="curve-point ${selected ? 'active' : ''} ${proposed ? 'proposed' : ''}" cx="${x}" cy="${y}" r="${selected ? 9 : 7}"></circle>${label}`;
      }).join('')}</svg>`;
      host.querySelectorAll('[data-curve-index]').forEach(point => {
        const select = () => this.selectPoint(Number(point.dataset.curveIndex));
        point.addEventListener('click', select);
        point.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); select(); } });
      });
    }

    renderOverview(state) {
      const host = document.getElementById('curveOverviewChart');
      const summaryHost = document.getElementById('curveOverviewSummary');
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
      // Evidência: assinatura barata (sem serializar os 30 pontos a cada leitura do relógio).
      const signature = points.map(item => `${item.petrolMs}/${item.factor}/${item.proposedFactor}`).join('|') + '#' + this.activeIndex;
      if (signature === this.overviewSignature) return;
      this.overviewSignature = signature;

      const width = 920; const height = 180; const py = 22; const px = 42;
      const xFor = index => px + (index / 29) * (width - px * 2);
      const factorValues = points.flatMap(item => [item.factor, item.proposedFactor]).filter(value => value !== null);
      const minFactor = factorValues.length ? Math.min(...factorValues) - 0.05 : 0.8;
      const maxFactor = factorValues.length ? Math.max(...factorValues) + 0.05 : 1.2;
      const factorY = value => height - py - ((Number(value) - minFactor) / Math.max(0.01, maxFactor - minFactor)) * (height - py * 2);
      const actualPath = points.filter(item => item.factor !== null).map((item, pos) => `${pos ? 'L' : 'M'} ${xFor(item.index).toFixed(1)} ${factorY(item.factor).toFixed(1)}`).join(' ');
      const proposedPath = points.filter(item => item.proposedFactor !== null).map((item, pos) => `${pos ? 'L' : 'M'} ${xFor(item.index).toFixed(1)} ${factorY(item.proposedFactor).toFixed(1)}`).join(' ');

      host.innerHTML = `<div class="curve-overview-stack">
        <section class="curve-overview-k"><small class="curve-overview-label">CURVA K · atual × proposta</small><svg viewBox="0 0 ${width} ${height}" role="img" aria-label="Curva K atual e proposta nos mesmos 30 pontos">${actualPath ? `<path class="curve-line actual" d="${actualPath}"></path>` : ''}${proposedPath ? `<path class="curve-line proposal" d="${proposedPath}"></path>` : ''}${points.map(item => item.factor === null ? '' : `<circle class="curve-point-hit" data-overview-index="${item.index}" cx="${xFor(item.index).toFixed(1)}" cy="${factorY(item.proposedFactor ?? item.factor).toFixed(1)}" r="24"></circle><circle class="curve-point ${item.index === this.activeIndex ? 'active' : ''}" cx="${xFor(item.index).toFixed(1)}" cy="${factorY(item.proposedFactor ?? item.factor).toFixed(1)}" r="${item.index === this.activeIndex ? 7 : 4}"></circle>${item.index % 5 === 0 || item.index === 29 ? `<text class="curve-point-label" x="${xFor(item.index).toFixed(1)}" y="${height - 5}" text-anchor="middle">${fmt(item.petrolMs, 1)}</text>` : ''}`).join('')}</svg></section>
      </div>`;
      host.querySelectorAll('[data-overview-index]').forEach(node => node.addEventListener('click', () => this.selectPoint(Number(node.dataset.overviewIndex))));

      const proposed = points.filter(item => item.proposedFactor !== null).length;
      summaryHost.innerHTML = `<div class="editor-heading"><div><small>30 PONTOS FÍSICOS</small><h3>Curva K atual × proposta</h3></div></div><div class="curve-overview-grid"><div><small>PONTOS LIDOS</small><b>${points.filter(item => item.factor !== null).length}/30</b></div><div><small>PROPOSTOS</small><b>${proposed}</b></div></div><div id="curveOverviewPointContext" class="curve-overview-list"></div><p class="empty-copy">O eixo X é Petrol Inj. dos 30 pontos. O erro por ponto volta com a Equivalência. A UI só desenha alvos K exatos vindos do Kotlin.</p>`;
      this.renderOverviewPointContext(state, this.activeIndex ?? 0);
    }

    renderOverviewPointContext(state, index) {
      const host = document.getElementById('curveOverviewPointContext');
      if (!host) return;
      const current = this.points().find(item => Number(item.index) === Number(index)) || {};
      const proposal = this.proposals.get(Number(index));
      const target = finite(proposal?.targetFactor);
      host.innerHTML = `<div><span>Ponto ${Number(index) + 1} · ${fmt(current.petrolMs, 2)} ms</span><b>K atual</b><small>${D.kValue(current.factor)}</small></div><div><span>proposta</span><b>${D.kValue(target)}</b></div>`;
    }

    renderProposalList() {
      const host = document.getElementById('curveProposalList');
      if (!host) return;
      const items = [...this.proposals.values()].sort((a, b) => Number(a.index) - Number(b.index));
      host.innerHTML = items.length ? items.map(item => `<div><span>${D.msUnit(item.petrolMs)}</span><b>${D.kValue(item.currentFactor)} → ${D.kValue(item.targetFactor)}</b><small>${item.deltaPercent > 0 ? '+' : ''}${fmt(item.deltaPercent, 1)}%</small></div>`).join('') : '<p>Nenhum ponto preparado.</p>';
      const review = document.getElementById('curveReviewButton');
      if (review) {
        if (this.restoreContext) {
          review.disabled = true;
          review.textContent = 'Desfazer pronto no botão acima';
        } else {
          review.disabled = items.length === 0;
          review.textContent = items.length
            ? `Gravar ${D.plural(items.length, 'ponto', 'pontos')} na ECU`
            : 'Prepare pontos';
        }
      }
    }

    renderEvidence(state) {
      const host = document.getElementById('curveEvidenceList');
      if (!host) return;
      host.innerHTML = '<p class="empty-copy">A evidência gasolina × GNV volta com a Curva Própria, em Curva K › Equivalência.</p>';
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
        ? `Desfazer: voltar à foto da Curva K ${this.restoreContext.fileName}`
        : 'Ajuste manual confirmado na UI clean-slate';
      const result = restoring
        ? this.api.restoreCurve(points, this.restoreContext.fileName)
        : this.api.writeCurve(points, reason);
      if (!result?.ok || !result?.started) {
        if (restoring) {
          this.restoreContext = null;
          this.proposals.clear();
          this.renderProposalList();
          text('curveBackupStatus', 'Desfazer não iniciado');
        }
        this.alert(failureText(result, 'O app não conseguiu iniciar a gravação da Curva K.'));
        return;
      }
      this.writeKind = restoring ? 'restore' : 'write';
      this.undoFile = '';
      this.writing = true;
      this.root?.classList.add('is-writing');
      text('curveOperationTitle', restoring ? 'Gravando na ECU… voltando à foto da Curva K' : wording().writing);
      const bar = document.getElementById('curveOperationProgress');
      if (bar) bar.style.width = '0%';
    }

    dismissResult() {
      this.root?.classList.remove('is-writing', 'has-result');
    }

    /** Desfazer = abrir a prévia de restauração da foto DESTA operação; gravar de volta é o toque seguinte. */
    showUndo(visible) {
      const button = document.getElementById('curveUndoButton');
      if (button) button.hidden = !visible || !this.undoFile;
    }

    undoLast() {
      const fileName = this.undoFile;
      this.dismissResult();
      if (fileName) this.prepareRestore(fileName);
    }

    alert(message) { this.store.patch({ alert: { level: 'warning', message: String(message || 'Operação indisponível') } }); }
  }

  ns.CurveScreen = CurveScreen;
})(typeof window !== 'undefined' ? window : globalThis);