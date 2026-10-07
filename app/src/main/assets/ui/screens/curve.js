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
  /** K digitado em pt-BR: aceita "1,05" e "1.05". Vazio/ruim = null (nunca 0). */
  function parseK(raw) {
    const clean = String(raw == null ? '' : raw).trim().replace(/\s/g, '').replace(',', '.');
    return clean === '' ? null : finite(clean);
  }
  /** K mostrado em pt-BR com 3 casas ("0,800"). */
  const kText = value => (finite(value) === null ? '' : D.kValue(value));
  const DISABLED_REASON = 'Escolha um ponto e ajuste o K';
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
      this.pendingRestoreFile = '';
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
      document.getElementById('curveBackupRestore')?.addEventListener('click', () => this.undoCurve());
      document.getElementById('curveBackupSelect')?.addEventListener('change', event => {
        const fileName = String(event.target?.value || '');
        if (this.reading || this.writing || this.backupTask) {
          event.target.value = this.pendingRestoreFile || this.restoreContext?.fileName || '';
          return;
        }
        this.cancelRestorePreview('');
        if (fileName) this.prepareRestore(fileName);
      });
      // Digitou o K e saiu do campo (ou Enter): o ponto já fica preparado. Não existe um segundo botão "Preparar".
      const target = document.getElementById('curveTargetFactor');
      target?.addEventListener('change', () => this.prepareActivePoint());
      // Enquanto digita, o botão principal já se oferece para gravar o K digitado (um toque prepara e grava).
      target?.addEventListener('input', () => this.renderProposalList());
      target?.addEventListener('keydown', event => { if (event.key === 'Enter') { event.preventDefault(); this.prepareActivePoint(); } });
      document.querySelectorAll('[data-curve-view]').forEach(button => button.addEventListener('click', () => this.setView(button.dataset.curveView || 'editor')));
      document.querySelectorAll('[data-curve-nudge]').forEach(button => button.addEventListener('click', () => this.nudgeActive(Number(button.dataset.curveNudge) || 0)));
      document.getElementById('curveClearProposals')?.addEventListener('click', () => {
        this.cancelRestorePreview('Desfazer descartado · nada foi enviado à ECU');
        this.proposals.clear();
        // O campo K volta ao valor atual do ponto: "Limpar" nunca deixa um K digitado esperando para ser gravado.
        const active = this.points().find(item => Number(item.index) === this.activeIndex);
        const input = document.getElementById('curveTargetFactor');
        if (input && active) input.value = kText(active.factor);
        text('curveTargetNormalized', 'Prévia calculada pelo app');
        this.renderChart(); this.renderProposalList();
      });
      document.getElementById('curveReviewButton')?.addEventListener('click', () => this.writePrepared());
      // Aviso breve (Revisto (W2)): o texto de status aparece sobre o gráfico e some sozinho depois de alguns segundos.
      const statusNode = document.getElementById('curveBackupStatus');
      if (statusNode && typeof root.MutationObserver === 'function') {
        let hideTimer = null;
        new root.MutationObserver(() => {
          statusNode.dataset.fresh = 'true';
          if (hideTimer) clearTimeout(hideTimer);
          hideTimer = setTimeout(() => { statusNode.dataset.fresh = 'false'; }, 5000);
        }).observe(statusNode, { childList: true, characterData: true, subtree: true });
      }
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
        this.root?.classList.remove('is-writing');
      }
      if (context && context.subpage) this.setView(context.subpage);
      // Vindo do AutoCal: o toque lá já foi o do dono. O reset roda aqui (foto antes, depois zera) assim que a
      // curva estiver lida; só uma vez. Qualquer outra entrada na aba apaga o pedido: nunca zera sem toque novo.
      this.pendingReset = Boolean(context && context.resetNow === true);
      if (this.rereadOnEnter) {
        this.rereadOnEnter = false;
        if (!this.reading && !this.writing && !this.backupTask) this.startRead(true);
      }
      if (!this.data && !this.reading) this.startRead(true);
      if (this.pendingReset && this.data && !this.reading) {
        this.pendingReset = false;
        this.resetCurve();
      }
      this.refreshBackups();
      this.updateControls();
      if (this.view === 'overview') this.renderOverview(this.store.get());
    }

    /** Voltou do segundo plano: não é uma entrada nova. Mantém foto escolhida, prévia e Desfazer; só retoma o acompanhamento. */
    onResume() {
      this.refreshBackups();
      this.updateControls();
      this.poll();
    }

    /** O cabo/ECU voltou: lê de novo sozinho (ler é automático; só o toque do dono grava). */
    onReconnect() {
      if (this.reading || this.writing || this.backupTask) return;
      if (this.store.get().route !== 'curve') { this.rereadOnEnter = true; return; }
      this.cancelRestorePreview('');
      this.startRead(true);
      if (this.reading) text('curveSourceStatus', 'ECU voltou · relendo a Curva K');
    }

    /** Sem curva lida não há o que ajustar: ±K e o botão principal ficam desativados e dizem o que fazer. */
    updateControls() {
      const ready = this.points().length > 0;
      const busy = this.reading || this.writing || Boolean(this.backupTask);
      ['curveBackupSave','curveBackupSelect','curveResetButton'].forEach(id => {
        const control = document.getElementById(id);
        if (control) { control.disabled = busy; control.title = busy ? 'Aguarde a operação atual da ECU' : ''; }
      });
      document.querySelectorAll('[data-curve-nudge]').forEach(button => {
        button.disabled = !ready || this.activeIndex === null || this.reading || this.writing;
        button.title = button.disabled ? DISABLED_REASON : '';
      });
      const input = document.getElementById('curveTargetFactor');
      if (input) input.disabled = !ready || this.activeIndex === null;
      const clear = document.getElementById('curveClearProposals');
      if (clear) clear.hidden = this.proposals.size === 0;
    }

    refreshBackups() {
      const select = document.getElementById('curveBackupSelect');
      const restore = document.getElementById('curveBackupRestore');
      if (!select) return;
      const keep = this.restoreContext ? String(this.restoreContext.fileName || '') : String(select.value || '');
      const backups = this.api.curveBackups();
      const rows = Array.isArray(backups) ? backups : [];
      const now = Date.now();
      select.innerHTML = rows.length
        ? '<option value="">Escolha uma foto…</option>' + rows.map(item => {
            const at = finite(item.createdAt);
            const when = at !== null && at > 0 ? D.ageText(at, now) : 'data desconhecida';
            const kind = item.type === 'MANUAL_SNAPSHOT' ? 'salva' : 'automática';
            return `<option value="${escapeHtml(item.fileName)}">${escapeHtml(item.label || 'Curva K')} · ${escapeHtml(when)} · ${kind}</option>`;
          }).join('')
        : '<option value="">Nenhuma foto salva</option>';
      // A foto escolhida e a prévia do Desfazer sobrevivem a uma releitura da lista (ex.: voltar do segundo plano).
      if (keep && rows.some(item => item.fileName === keep)) select.value = keep;
      this.syncRestoreButton();
      if (this.restoreContext) text('curveBackupStatus', 'Pronto: toque em Desfazer para voltar a esta foto');
      else if (rows.length) text('curveBackupStatus', `${D.plural(rows.length, 'foto salva', 'fotos salvas')} · escolha uma para ver o que volta`);
      else text('curveBackupStatus', 'Nenhuma foto salva');
    }

    /** Desfazer só aparece quando há o que desfazer: uma foto escolhida e conferida com diferenças. */
    syncRestoreButton() {
      const restore = document.getElementById('curveBackupRestore');
      if (!restore) return;
      const select = document.getElementById('curveBackupSelect');
      const hasPhoto = Boolean(select && (select.value || /value="[^"]+"/.test(String(select.innerHTML || ''))));
      const ready = Boolean(this.restoreContext && this.proposals.size);
      const busy = this.backupTask === 'restore-preview';
      restore.hidden = !(hasPhoto || ready || busy);
      restore.disabled = busy;
      restore.textContent = busy ? 'Conferindo…' : ready ? `Desfazer · ${D.plural(this.proposals.size, 'ponto', 'pontos')}` : 'Desfazer';
      if (ready && this.autoRestore) { this.autoRestore = false; this.writeRestore(); }
    }

    /** Um toque: usa por baixo a foto mais recente (guardada em silêncio) e grava de volta; o fim é o readback da ECU. */
    undoCurve() {
      if (this.reading || this.writing || this.backupTask) return;
      if (this.restoreContext && this.proposals.size) { this.writeRestore(); return; }
      const select = document.getElementById('curveBackupSelect');
      const rows = (this.api.curveBackups() || []).filter(item => item && item.fileName);
      if (!rows.length) { this.alert('Ainda não há o que desfazer.'); return; }
      rows.sort((a, b) => (finite(b.createdAt) || 0) - (finite(a.createdAt) || 0));
      if (select) select.value = rows[0].fileName;
      this.autoRestore = true;
      this.prepareRestore(rows[0].fileName);
    }

    saveBackup() {
      if (this.reading || this.writing || this.backupTask) return;
      const result = this.api.startCurveBackup('Curva salva manualmente');
      if (!result?.ok || !result?.started) {
        this.alert(result?.error || 'Não foi possível salvar a foto da Curva K.');
        return;
      }
      this.backupTask = 'save';
      this.updateControls();
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
      this.updateControls();
      text('curveBackupStatus', 'Salvando a foto da curva antes de zerar…');
      // A foto antes aparece como etapa do cartão de operação, não como linha de status miúda.
      this.root?.classList.remove('has-result');
      this.root?.classList.add('is-writing');
      text('curveOperationTitle', 'Foto antes · salvando a Curva K atual');
      this.setStep(0);
      const photoBar = document.getElementById('curveOperationProgress');
      if (photoBar) photoBar.style.width = '8%';
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
      text('curveOperationTitle', 'Gravando na ECU… Curva K em 1,000');
      this.setStep(1);
      text('curveOperationMessage', RESET_NOTE);
      const bar = document.getElementById('curveOperationProgress');
      if (bar) bar.style.width = '0%';
    }

    /** Etapa visível da operação (Foto antes → Gravando → Conferindo na ECU): a foto antes é um passo de verdade. */
    setStep(index) {
      document.querySelectorAll('#curveOperationSteps span').forEach((node, i) => { node.dataset.state = i < index ? 'done' : i === index ? 'active' : 'pending'; });
    }

    prepareRestore(fileName = String(document.getElementById('curveBackupSelect')?.value || '')) {
      if (this.reading || this.writing || this.backupTask) return;
      if (!fileName) return;
      this.pendingRestoreFile = fileName;
      const chosen = document.getElementById('curveBackupSelect');
      if (chosen) chosen.value = fileName;
      this.restoreContext = null;
      this.backupTask = 'restore-preview';
      this.syncRestoreButton();
      this.backupTask = null;
      this.proposals.clear();
      this.renderChart();
      this.renderProposalList();
      const result = this.api.prepareCurveRestore(fileName);
      if (!result?.ok || !result?.started) {
        const select = document.getElementById('curveBackupSelect');
        if (select) select.value = '';
        this.syncRestoreButton();
        this.alert(result?.error || 'Não foi possível preparar o Desfazer.');
        return;
      }
      this.backupTask = 'restore-preview';
      this.updateControls();
      this.syncRestoreButton();
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
      this.syncRestoreButton();
      if (message) text('curveBackupStatus', message);
    }

    settleReadFailure(message) {
      this.reading = false;
      this.data = null;
      this.pendingReset = false;
      this.proposals.clear();
      this.root?.classList.remove('is-reading');
      text('curveSourceStatus', 'ECU não confirmada');
      this.renderChart();
      this.renderProposalList();
      this.updateControls();
      this.store.patch({ curve: { ...this.store.get().curve, state: 'failed', data: null, status: {} } });
      if (message) this.alert(message);
    }

    startRead() {
      // Leitura que não inicia nunca deixa um pedido de reset esperando por outra leitura.
      if (this.backupTask || this.reading || this.writing) { this.pendingReset = false; return; }
      const result = this.api.startCurveRead();
      if (!result?.ok || !result?.started) {
        // A leitura não começou (ex.: outra operação ocupa a ECU): o que já está na tela continua valendo; só avisa.
        this.pendingReset = false;
        if (!this.data) text('curveSourceStatus', 'ECU não confirmada');
        this.alert(result?.error || 'Não foi possível iniciar a leitura da Curva K.');
        return;
      }
      this.reading = true;
      this.data = null;
      this.proposals.clear();
      this.activeIndex = null;
      text('curveSourceStatus', 'Lendo 30 pontos diretamente da ECU');
      this.root?.classList.add('is-reading');
      this.renderChart();
      this.renderProposalList();
      this.updateControls();
    }

    poll() {
      if (!this.reading && !this.writing && !this.backupTask) return;
      const operation = this.api.curveOperation();
      if (!operation) return;
      if (this.backupTask && !operation.busy) {
        const task = this.backupTask;
        this.backupTask = null;
        this.updateControls();
        if (operation.state !== 'COMPLETED' || !operation.ok) {
          this.restoreContext = null;
          const select = document.getElementById('curveBackupSelect');
          const restore = document.getElementById('curveBackupRestore');
          if (select) select.value = '';
          this.syncRestoreButton();
          text('curveBackupStatus', 'Foto indisponível');
          this.root?.classList.remove('is-writing');
          this.alert(failureText(operation, 'O app não conseguiu salvar ou ler a foto da Curva K.'));
          return;
        }
        if (task === 'reset-photo') {
          // Só a operação de foto devolve hash e caminho; uma leitura qualquer não autoriza o reset.
          if (!operation.hash || !operation.publicPath || !operation.fileName) {
            // A regra "foto antes" não se enfraquece: sem a foto salva em Download/Omegas, nada é zerado.
            text('curveBackupStatus', 'Nada foi zerado: a foto não foi salva. Libere espaço no celular e toque em Resetar Curva K de novo.');
            this.root?.classList.remove('is-writing');
            this.alert('Nada foi zerado: a foto de antes não foi salva. Libere espaço no celular e toque em Resetar Curva K de novo.');
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
          // A lista é relida primeiro (ela escreve "N fotos salvas"); o caminho e o hash do arquivo novo ficam por último.
          this.refreshBackups();
          text('curveBackupStatus', (operation.publicPath || 'Download/Omegas') + ' · ' + String(operation.hash || '').slice(0, 8));
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
            this.syncRestoreButton();
            text('curveBackupStatus', 'A ECU já está igual à foto: nada a desfazer');
            this.alert('A Curva K atual já é igual à foto escolhida.');
            return;
          }
          const chosen = document.getElementById('curveBackupSelect');
          if (chosen) chosen.value = operation.fileName;
          this.pendingRestoreFile = '';
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
          this.syncRestoreButton();
          text('curveBackupStatus', 'Pronto: toque em Desfazer para voltar a esta foto');
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
        if (this.pendingReset) {
          this.pendingReset = false;
          this.resetCurve();
        }
        this.selectPoint(0);
        this.updateControls();
        if (this.view === 'overview') this.renderOverview(this.store.get());
        this.store.patch({ curve: { ...this.store.get().curve, state: 'ready', data: operation, status: {} } });
        return;
      }

      if (this.writing) {
        const progress = Math.max(0, Math.min(100, finite(operation.progress) || finite(operation.writerProgress) || 0));
        const bar = document.getElementById('curveOperationProgress');
        if (bar) bar.style.width = `${progress}%`;
        // Texto da etapa é nosso (a mensagem do escritor pode ter jargão): Gravando → Conferindo na ECU.
        this.setStep(progress >= 90 ? 2 : 1);
        text('curveOperationTitle', progress >= 90 ? wording().stages[2] + '…' : `${wording().writing.replace('…', '')} ${Math.round(progress)}%`);
        if (!operation.busy) {
          this.writing = false;
          if (operation.state === 'BATCH_CONFIRMED' && operation.readbackValid === true) {
            this.root?.classList.remove('is-writing');
            this.root?.classList.add('has-result');
            // Desfazer = a foto desta operação. Reset: a foto tirada antes dele; escrita/restauração: a que o
            // Kotlin guardou antes do primeiro ACK (`photoFile`). Sem foto (ou sem nada alterado) não há Desfazer.
            const unchanged = operation.nothingToChange === true || (operation.details && Number(operation.details.changedPoints) === 0);
            this.undoFile = unchanged ? '' : String((this.writeKind === 'reset' && this.resetPhotoFile) || operation.photoFile || '');
            const result = document.getElementById('curveOperationResult');
            if (result) {
              result.dataset.level = 'ok';
              const nothing = operation.nothingToChange === true;
              result.querySelector('b').textContent = nothing ? 'Nada a gravar' : wording().doneTitle('Curva K', { fem: true });
              result.querySelector('span').textContent = nothing ? 'A Curva K já estava em 1,000. Nada foi enviado à ECU.' : wording().doneDetail;
              this.showUndo(!nothing);
            }
            this.data = null;
            this.proposals.clear();
            if (this.restoreContext) text('curveBackupStatus', 'Foto restaurada e confirmada pela ECU');
            this.restoreContext = null;
            this.renderChart();
            this.renderProposalList();
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
            // A curva da tela pode já não ser a da ECU: some o desenho antigo e os botões de ajuste até reler.
            this.data = null;
            if (this.restoreContext) text('curveBackupStatus', 'O app não conseguiu confirmar na ECU · leia a ECU de novo');
            this.restoreContext = null;
            this.proposals.clear();
            text('curveSourceStatus', 'ECU não confirmada');
            this.renderChart();
            this.renderProposalList();
            this.updateControls();
            this.refreshBackups();
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
      if (input) input.value = kText(this.proposals.get(this.activeIndex)?.targetFactor ?? point.factor);
      this.updateControls();
      this.renderChart();
      this.renderOverviewPointContext(this.store.get(), this.activeIndex);
    }

    nudgeActive(delta) {
      if (this.activeIndex === null || !delta) return;
      const input = document.getElementById('curveTargetFactor');
      const current = parseK(input?.value) ?? finite(this.points().find(item => Number(item.index) === this.activeIndex)?.factor);
      if (current === null) return;
      if (input) input.value = kText(Math.max(0.6, Math.min(4, current + delta)));
      this.prepareActivePoint();
    }

    prepareActivePoint() {
      if (this.activeIndex === null) return;
      if (this.restoreContext) {
        this.cancelRestorePreview('Prévia do Desfazer descartada por edição manual');
        this.proposals.clear();
      }
      const requested = parseK(document.getElementById('curveTargetFactor')?.value);
      if (requested === null) { this.alert('Digite o K desejado (ex.: 1,050) ou use os botões − e +.'); return; }
      const preview = this.api.previewCurvePoint(this.activeIndex, requested);
      if (!preview?.ok) { this.alert('Não deu para calcular este ponto. Toque no ponto e tente de novo.'); return; }
      this.acceptPreview(preview);
    }

    acceptPreview(preview, deferRender = false) {
      const index = Number(preview.index);
      if (!Number.isInteger(index)) {
        this.alert('Não deu para calcular este ponto. Toque no ponto e tente de novo.');
        return;
      }
      if (!preview.changed) this.proposals.delete(index);
      else this.proposals.set(index, preview);
      const input = document.getElementById('curveTargetFactor');
      if (input && index === this.activeIndex && finite(preview.targetFactor) !== null) input.value = kText(preview.targetFactor);
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
      // Um só ouvinte no quadro (delegação): o desenho é refeito sem empilhar ouvinte nos 30 pontos a cada vez.
      if (this.pointHost !== host) {
        this.pointHost = host;
        host.addEventListener('click', event => {
          const direct = event.target.closest && event.target.closest('[data-curve-index]');
          const hits = Array.from(host.querySelectorAll('.curve-point-hit'));
          if (!hits.length) return;
          const measurable = Number.isFinite(event.clientX) && Number.isFinite(event.clientY) && hits.some(node => node.getBoundingClientRect().width > 0);
          if (!measurable) { if (direct) this.selectPoint(Number(direct.dataset.curveIndex)); return; }
          // O ponto mais perto do toque (em x e y) ganha: com 30 pontos os círculos de 48 px se encostam.
          let best = null; let bestD = Infinity;
          hits.forEach(node => {
            const box = node.getBoundingClientRect();
            const d = Math.hypot(event.clientX - (box.left + box.width / 2), (event.clientY - (box.top + box.height / 2)) * 0.5);
            if (d < bestD) { bestD = d; best = node; }
          });
          if (best && bestD <= 40) this.selectPoint(Number(best.dataset.curveIndex));
        });
        host.addEventListener('keydown', event => {
          const point = event.target.closest('[data-curve-index]');
          if (point && (event.key === 'Enter' || event.key === ' ')) { event.preventDefault(); this.selectPoint(Number(point.dataset.curveIndex)); }
        });
      }
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
      summaryHost.innerHTML = `<div class="editor-heading"><div><small>30 PONTOS FÍSICOS</small><h3>Curva K atual × proposta</h3></div></div><div class="curve-overview-grid"><div><small>PONTOS LIDOS</small><b>${points.filter(item => item.factor !== null).length}/30</b></div><div><small>PROPOSTOS</small><b>${proposed}</b></div></div><div id="curveOverviewPointContext" class="curve-overview-list"></div>`;
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

    /** K digitado, ainda não preparado, diferente do que a tela já mostra para o ponto ativo. */
    typedPending() {
      if (this.restoreContext || this.activeIndex === null || this.reading || this.writing) return false;
      const typed = parseK(document.getElementById('curveTargetFactor')?.value);
      const shown = finite(this.proposals.get(this.activeIndex)?.targetFactor ?? this.points().find(item => Number(item.index) === this.activeIndex)?.factor);
      return typed !== null && shown !== null && Math.abs(typed - shown) >= 0.0005;
    }

    renderProposalList() {
      const host = document.getElementById('curveProposalList');
      if (!host) return;
      const items = [...this.proposals.values()].sort((a, b) => Number(a.index) - Number(b.index));
      host.innerHTML = items.length
        ? items.map(item => {
          const delta = finite(item.deltaPercent);
          return `<div class="proposal-row"><span>${D.msUnit(item.petrolMs)}</span><b>${D.kValue(item.currentFactor)} → ${D.kValue(item.targetFactor)}</b><small>${delta === null ? '—' : `${delta > 0 ? '+' : ''}${fmt(delta, 1)}%`}</small></div>`;
        }).join('')
        : '<p>Nenhum ponto preparado.</p>';
      const review = document.getElementById('curveReviewButton');
      if (review) {
        // UM só botão primário: os botões − e + (e o campo K) já preparam o ponto; aqui só se grava.
        let label;
        let disabled;
        const count = items.length + (this.typedPending() && !this.proposals.has(this.activeIndex) ? 1 : 0);
        if (this.restoreContext) { disabled = true; label = 'Desfazer pronto no botão acima'; }
        else if (count) { disabled = false; label = `Gravar ${D.plural(count, 'ponto', 'pontos')} na ECU`; }
        else { disabled = true; label = DISABLED_REASON; }
        review.disabled = disabled;
        if (review.textContent !== label) review.textContent = label;
      }
      this.updateControls();
    }

    writePrepared() {
      // Toque duplo com a ECU ocupada: a segunda chamada não envia a escrita de novo.
      if (this.writing || this.reading || this.backupTask) return;
      // K digitado e ainda não preparado (o dono não saiu do campo): um toque só prepara e grava.
      if (this.typedPending()) this.prepareActivePoint();
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
      this.setStep(1);
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