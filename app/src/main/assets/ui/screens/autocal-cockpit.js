(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  // Mesma régua do Agora: cinza com 1,5 s sem quadro novo, some com 3 s (antes vivia 7,5 s com quadro velho).
  const AUTO_CAL_LIVE_GREY_MS = ns.LiveStore.GREY_MS;
  const AUTO_CAL_LIVE_STALE_MS = ns.LiveStore.STALE_MS;
  // Suavização do cursor: ~150 ms para chegar ao alvo (sem extrapolar o que o motor fará).
  // Texto da narrativa: no máximo 2 Hz, ou na hora quando muda região/combustível/estado.
  const AUTO_CAL_NARRATIVE_MS = ns.LiveStore.NARRATIVE_MS;
  const AUTO_CAL_OPERATIONAL_MAP_MAX_BAR = 1.15;
  const AUTO_CAL_X_AXIS_LABEL = 'Injeção (ms)';

  const D = ns.DisplayRules;
  const finite = D.finite;
  const escapeHtml = D.escapeHtml;
  function field(snapshot, key) {
    const fields = Array.isArray(snapshot?.fields) ? snapshot.fields : [];
    return fields.find(item => String(item?.key || '') === key && String(item?.status || '') === 'VALID') || null;
  }
  function vector(snapshot, key) {
    const item = field(snapshot, key);
    return Array.isArray(item?.rawValues) ? item.rawValues.map(value => finite(value) ?? 0) : [];
  }
  function actionLabel(action) {
    return ({
      ENABLE_AUTO_CAL: 'Iniciar a leitura da ECU',
      DISABLE_AUTO_CAL: 'Pausar a leitura da ECU',
      FINISH_AUTOCAL: 'Encerrar cota AutoMatch (técnico)',
      FINISH_AUTOMATCH: 'Encerrar AutoMatch (debug)',
      RESET_PETROL: 'Reler gasolina',
      RESET_GAS: 'Reler GNV',
      RESET_K_FACTOR: 'Resetar Curva K para 1,000',
      RESET_ALL: 'Nova leitura completa',
    })[action] || action;
  }

  function physicalVector(snapshot, key) {
    const item = field(snapshot, key);
    return Array.isArray(item?.physicalValues) ? item.physicalValues.map(value => finite(value)) : [];
  }

  function zoneForBand(index) {
    if (index <= 5) return 0;
    if (index <= 9) return 1;
    if (index <= 13) return 2;
    return 3;
  }

  function toggleActionKnown(enabled) {
    const value = finite(enabled);
    return value === 1 || value === 0 ? value : null;
  }
  function nativeZoneFlags(snapshot, key) {
    const values = vector(snapshot, key).slice(0, 4);
    return Array.from({ length: 4 }, (_, index) => (finite(values[index]) ?? 0) > 0);
  }

  function nativeZoneCount(snapshot, key) {
    return nativeZoneFlags(snapshot, key).filter(Boolean).length;
  }

  function projectedZoneFlags(projection, fuel) {
    const zones = projection?.ok === true && projection?.acquisitionZones && typeof projection.acquisitionZones === 'object'
      ? projection.acquisitionZones[fuel]
      : null;
    if (!Array.isArray(zones)) return null;
    return Array.from({ length: 4 }, (_, index) => zones[index] === true);
  }

  function scalarValue(snapshot, key) {
    const item = field(snapshot, key);
    const values = Array.isArray(item?.rawValues) ? item.rawValues : [];
    return values.length === 1 ? finite(values[0]) : null;
  }

  const AutoCalUxModel = {
    humanState(snapshot = {}, state = {}, projection = {}) {
      const nativeSnapshot = state.latestSnapshot?.fields ? state.latestSnapshot : {};
      const evidenceSnapshot = nativeSnapshot.fields ? nativeSnapshot : snapshot;
      const enabled = finite(state.autoCalEnabled ?? nativeSnapshot.autoCalEnabled ?? scalarValue(nativeSnapshot, 'AUTO_CAL_ENABLE'));
      const projectedPetrolZones = projectedZoneFlags(projection, 'petrol');
      const projectedGasZones = projectedZoneFlags(projection, 'gas');
      const petrolFieldAvailable = field(evidenceSnapshot, 'ACQUIRED_ZONES_PETROL') !== null;
      const gasFieldAvailable = field(evidenceSnapshot, 'ACQUIRED_ZONES_GAS') !== null;
      const petrolZoneFlags = projectedPetrolZones
        ?? (petrolFieldAvailable ? nativeZoneFlags(evidenceSnapshot, 'ACQUIRED_ZONES_PETROL') : null);
      const gasZoneFlags = projectedGasZones
        ?? (gasFieldAvailable ? nativeZoneFlags(evidenceSnapshot, 'ACQUIRED_ZONES_GAS') : null);
      const petrolZones = Array.isArray(petrolZoneFlags) ? petrolZoneFlags.filter(Boolean).length : null;
      const gasZones = Array.isArray(gasZoneFlags) ? gasZoneFlags.filter(Boolean).length : null;
      const petrolMissingZones = Array.isArray(petrolZoneFlags)
        ? petrolZoneFlags.map((acquired, index) => acquired ? null : index + 1).filter(Number.isInteger)
        : [];
      const gasMissingZones = Array.isArray(gasZoneFlags)
        ? gasZoneFlags.map((acquired, index) => acquired ? null : index + 1).filter(Number.isInteger)
        : [];
      const nativeStatus = nativeSnapshot.nativeStatus || {};
      const autoMatchCount = finite(state.autoMatchCount ?? nativeStatus.autoMatchCount ?? scalarValue(nativeSnapshot, 'NUM_AUTOMATCH_EXECUTED'));
      const maxAutoMatch = finite(state.maxAutomatch ?? nativeSnapshot.maxAutomatch ?? scalarValue(nativeSnapshot, 'MAX_AUTOMATCH'));
      // RELEASE INVARIANT · 3/3 is quota only; normal UX has no Finish CTA and acquisition remains separately enabled/paused.
      const autoMatchQuotaReached = autoMatchCount !== null && maxAutoMatch !== null && maxAutoMatch > 0 && autoMatchCount >= maxAutoMatch;
      const acquisitionState = String(state.state || '').toUpperCase();
      const title = acquisitionState === 'UNAVAILABLE'
        ? 'AutoCal sem estado confiável'
        : acquisitionState === 'PROBE_FAILED' || acquisitionState === 'FAILED'
          ? 'AutoCal com erro de leitura'
          : acquisitionState === 'WAITING_TELEMETRY_SETTLE'
            ? 'Conectando à leitura da ECU'
            : enabled === 1 && autoMatchQuotaReached
              ? 'AutoCal ativo · AutoMatch ' + Math.round(autoMatchCount) + '/' + Math.round(maxAutoMatch)
              : enabled === 1 ? 'AutoCal adquirindo'
              : enabled === 0 ? 'AutoCal pausado'
              : snapshot.available ? 'AutoCal aguardando estado' : 'Aguardando AutoCal';
      let progress = 'Gasolina ' + (petrolZones === null ? '—' : petrolZones) + '/4 zonas · GNV ' + (gasZones === null ? '—' : gasZones) + '/4 zonas';
      if (gasMissingZones.length) progress += ' · Faltam GNV: ' + gasMissingZones.map(zone => 'Z' + zone).join(', ');
      if (petrolMissingZones.length) progress += ' · Faltam gasolina: ' + petrolMissingZones.map(zone => 'Z' + zone).join(', ');
      const evidence = nativeSnapshot.nativeAutoMatchEvidence || snapshot.nativeAutoMatchEvidence || null;
      const evidenceState = String(evidence?.state || '');
      const evidenceDeltas = Array.isArray(evidence?.pointDeltas) ? evidence.pointDeltas : [];
      const evidenceBefore = finite(evidence?.beforeCount);
      const evidenceAfter = finite(evidence?.afterCount);
      const changedRaw = finite(evidence?.changedPointCount) ?? (evidenceDeltas.length ? evidenceDeltas.length : null);
      const changedPoints = changedRaw === null ? null : Math.max(0, Math.round(changedRaw));
      const largestDelta = evidenceDeltas
        .slice()
        .sort((a, b) => Math.abs(finite(b?.deltaFactor) ?? 0) - Math.abs(finite(a?.deltaFactor) ?? 0))[0] || null;
      const largestIndex = finite(largestDelta?.index);
      const largestBefore = finite(largestDelta?.beforeFactor);
      const largestAfter = finite(largestDelta?.afterFactor);
      const evidenceRange = evidenceBefore === null || evidenceAfter === null
        ? ''
        : Math.round(evidenceBefore) + '→' + Math.round(evidenceAfter);
      let evidenceTitle = 'Aguardando evento AutoMatch observável';
      let evidenceDetail = 'Quando o AutoMatch da ECU rodar, o OMEGAS compara a Curva K estável de antes e de depois dele.';
      if (evidenceState === 'FACTOR_CHANGE_CONFIRMED') {
        evidenceTitle = 'ECU AutoMatch ' + evidenceRange + ' · K mudou ' + (changedPoints === null ? '—' : changedPoints) + '/30';
        evidenceDetail = largestIndex === null || largestBefore === null || largestAfter === null
          ? 'Mudança da Curva K confirmada na ECU, comparando antes e depois do mesmo AutoMatch.'
          : 'Maior mudança observada: ponto ' + (Math.round(largestIndex) + 1) + ' · ' +
            D.kValue(largestBefore) + ' → ' + D.kValue(largestAfter) + '.';
      } else if (evidenceState === 'NO_FACTOR_CHANGE_OBSERVED') {
        evidenceTitle = 'ECU AutoMatch ' + evidenceRange + ' · K sem mudança';
        evidenceDetail = 'O contador avançou, mas a Curva K não mudou.';
      } else if (evidenceState === 'INCONCLUSIVE') {
        evidenceTitle = 'ECU AutoMatch ' + evidenceRange + ' · sem par antes/depois';
        evidenceDetail = 'O contador avançou, mas não existe um par antes/depois do mesmo AutoMatch confiável: ' +
          String(evidence?.reason || 'evidência insuficiente') + '.';
      }
      const autoMatch = autoMatchCount === null
        ? 'AutoMatch ainda sem contador válido'
        : maxAutoMatch !== null && maxAutoMatch > 0
          ? autoMatchQuotaReached
            ? 'AutoMatch automático ' + Math.round(autoMatchCount) + '/' + Math.round(maxAutoMatch) + ' · limite atingido'
            : 'AutoMatch automático ' + Math.round(autoMatchCount) + '/' + Math.round(maxAutoMatch) + ' · ECU decide quando executar'
          : Math.round(autoMatchCount) + ' AutoMatch ' + (Math.round(autoMatchCount) === 1 ? 'automático observado' : 'automáticos observados');
      let nextAction = 'A Leitura da ECU é automática; aguardando o próximo estado confirmado.';
      if (acquisitionState === 'UNAVAILABLE') {
        nextAction = String(state.message || state.error || 'O AutoCal da ECU está indisponível.') + ' · Nenhuma referência será escolhida pela interface.';
      } else if (acquisitionState === 'PROBE_FAILED' || acquisitionState === 'FAILED') {
        nextAction = String(state.message || state.error || 'Não foi possível ler o estado do AutoCal da ECU.') + ' · Verifique a conexão; o monitor tentará novamente automaticamente.';
      } else if (enabled === 0) nextAction = 'Inicie a leitura quando quiser continuar.';
      else if (enabled === 1 && autoMatchQuotaReached) nextAction = 'A cota automática de AutoMatch foi atingida. A leitura continua ativa e pode preencher novas zonas; pause só se quiser interromper.';
      else if (enabled === 1 && gasMissingZones.length) nextAction = 'Leitura ativa. Faltam no GNV: ' + gasMissingZones.map(zone => 'Z' + zone).join(', ') + '. Use a faixa AGORA para buscar essas zonas sem resetar dados.';
      else if (enabled === 1 && gasZones === 4) nextAction = 'As 4 zonas GNV já foram marcadas pela ECU. Continue acompanhando: o próximo AutoMatch é decisão da ECU.';
      else if (enabled === 1) nextAction = 'Leitura ativa; aguardando a ECU publicar as quatro zonas.';
      return {
        title, progress, autoMatch, nextAction,
        autoMatchEvidenceState: evidenceState || 'WAITING',
        autoMatchEvidenceTitle: evidenceTitle,
        autoMatchEvidenceDetail: evidenceDetail,
        petrolZones, gasZones, petrolMissingZones, gasMissingZones,
        petrolZoneFlags, gasZoneFlags, enabled, autoMatchCount, maxAutoMatch, autoMatchQuotaReached,
      };
    },

    readNarrative(readerState = {}) {
      const state = String(readerState.state || 'IDLE').toUpperCase();
      const busy = readerState.busy === true || state === 'QUEUED' || state === 'READING' || state === 'CANCEL_REQUESTED';
      const progress = finite(readerState.progress);
      const suffix = progress !== null && busy ? ' · ' + Math.round(progress) + '%' : '';
      if (state === 'QUEUED') return { state, busy, level: 'working', title: 'Solicitação recebida', detail: 'Preparando leitura da ECU' + suffix, next: 'Aguarde a leitura iniciar.' };
      if (state === 'READING') return { state, busy, level: 'working', title: 'Lendo ECU', detail: String(readerState.message || 'Recebendo campos AutoCal') + suffix, next: 'Aguarde ou cancele a leitura.' };
      if (state === 'CANCEL_REQUESTED') return { state, busy: true, cancelling: true, level: 'working', title: 'Cancelando leitura', detail: String(readerState.message || 'Cancelamento solicitado') + suffix, next: 'Aguarde a leitura encerrar com segurança.' };
      if (state === 'READY') return { state, busy: false, level: 'ok', title: 'Leitura concluída', detail: String(readerState.message || 'Todos os campos esperados foram processados.'), next: 'Dados prontos para inspeção.' };
      if (state === 'READY_PARTIAL') return { state, busy: false, level: 'warning', title: 'Leitura parcial', detail: String(readerState.message || 'Alguns campos não foram confirmados pela ECU.'), next: 'Veja os detalhes técnicos ou tente consultar novamente.' };
      if (state === 'CANCELLED') return { state, busy: false, level: 'neutral', title: 'Leitura cancelada', detail: String(readerState.message || 'A leitura foi interrompida sem alterar a ECU.'), next: 'A próxima leitura será retomada automaticamente quando a sessão estiver pronta.' };
      if (state === 'DISCONNECTED') return { state, busy: false, level: 'error', title: 'ECU desconectada', detail: String(readerState.message || 'A conexão foi perdida.'), next: 'Reconecte a ECU e tente novamente.' };
      if (state === 'STALE_SESSION') return { state, busy: false, level: 'error', title: 'Sessão mudou', detail: String(readerState.message || 'A sessão USB mudou durante a leitura.'), next: 'A leitura automática será reiniciada na sessão atual.' };
      if (state === 'CALIBRATION_CONFLICT') return { state, busy: false, level: 'warning', title: 'Outra calibração está em uso', detail: String(readerState.message || 'A porta serial está ocupada por outra operação.'), next: 'Finalize a outra operação e tente novamente.' };
      if (state === 'FAILED' || state === 'TIMEOUT' || state === 'UNAVAILABLE') return { state, busy: false, level: 'error', title: state === 'TIMEOUT' ? 'Tempo de leitura esgotado' : 'Leitura falhou', detail: String(readerState.error || readerState.message || 'A ECU não concluiu a leitura.'), next: 'Verifique a conexão; o monitor tentará novamente automaticamente.' };
      return { state, busy: false, level: 'neutral', title: 'Leitura pronta para iniciar', detail: 'Nenhuma consulta manual em andamento.', next: 'A Leitura da ECU se atualiza sozinha.' };
    },

    sessionNarrative(status = {}) {
      const summary = status?.semanticSummary && typeof status.semanticSummary === 'object' ? status.semanticSummary : {};
      const autocal = summary?.autocal && typeof summary.autocal === 'object' ? summary.autocal : {};
      const recording = status.recording === true;
      const durationRaw = finite(status.durationMs ?? summary.durationMs);
      const durationMs = Math.max(0, durationRaw ?? 0);
      const minutes = Math.floor(durationMs / 60000);
      const regionsKnown = Array.isArray(autocal.correlatedRegions);
      const regions = regionsKnown ? autocal.correlatedRegions.length : 0;
      const gasZonesRaw = finite(autocal.gasZones);
      const gasZones = Math.max(0, Math.min(4, Math.round(gasZonesRaw ?? 0)));
      const dropped = Math.max(0, Math.round(finite(status.droppedEvents) ?? 0));
      const lastError = String(status.lastError || '');
      const documentsMirror = status?.documentsMirror && typeof status.documentsMirror === 'object' ? status.documentsMirror : {};
      const mirrorFailed = documentsMirror.available === false || documentsMirror.lastSyncOk === false;
      const warning = dropped > 0 || lastError.length > 0 || mirrorFailed;
      const title = recording ? 'Sessão atual' : summary?.sessionId ? 'Última sessão' : 'Sessões prontas';
      const detail = (recording ? (durationRaw === null ? '—' : minutes) + ' min' : 'histórico preservado') +
        ' · ' + (regionsKnown ? regions : '—') + ' ' + (regions === 1 && regionsKnown ? 'região com ajuste confirmado' : 'regiões com ajuste confirmado') +
        ' · GNV ' + (gasZonesRaw === null ? '—' : gasZones) + '/4';
      const next = dropped > 0 || lastError.length > 0
        ? 'Há uma lacuna na gravação interna da evidência. Veja os detalhes antes de usar esta sessão.'
        : mirrorFailed
          ? 'A sessão segue protegida na memória interna, mas Downloads/Omegas precisa de atenção.'
          : recording
            ? 'Salvando em Downloads/Omegas automaticamente enquanto a sessão acontece.'
            : 'Salvo em Downloads/Omegas. Abra Sessões apenas para revisar ou exportar.';
      return { title, detail, next, level: warning ? 'warning' : 'ok', recording, minutes, regions, gasZones, dropped, documentsMirror, mirrorFailed };
    },

    // Leitura viva única (core/live-store.js): cinza em 1,5 s, some em 3 s.
    livePoint(telemetry = {}) { return ns.LiveStore.point(telemetry); },

    /**
     * Onde o MAP vivo cai nos 18 limiares da ECU. Cada faixa i é (THD[i], THD[i+1]] (confirmado em 740 de 740
     * leituras reais); abaixo de THD[0] é lenta/desaceleração e acima de THD[17] é a faixa 18. Nunca devolve
     * "indisponível" quando a ECU deu os limiares.
     */
    liveRegion(snapshot = {}, live = {}) {
      const band = this.currentBand(snapshot, live);
      if (band) return { kind: 'band', index: band.index, zone: zoneForBand(band.index) + 1 };
      const thresholds = physicalVector(snapshot, 'MNFLD_PRESS_THD');
      const mapBar = finite(live?.mapBar ?? live?.load_bar ?? live?.map_bar);
      const values = thresholds.map(Number);
      const valid = mapBar !== null && values.length >= 2 && thresholds.every(value => finite(value) !== null) &&
        values.every((value, index) => index === 0 || value > values[index - 1]);
      if (!valid) return { kind: 'unknown', index: null, zone: null };
      if (mapBar <= values[0]) return { kind: 'idle', index: null, zone: null, edge: values[0] };
      const last = values.length - 1;
      return { kind: 'above', index: last, zone: zoneForBand(last) + 1, edge: values[last] };
    },

    /** Texto honesto sobre a idade e a completude da leitura da ECU (nada de "—" mudo). */
    readingNote(snapshot = {}, nowMs = Date.now()) {
      const parts = [];
      const at = finite(snapshot?.capturedAtMs);
      if (at !== null && at > 0) {
        const seconds = Math.round((nowMs - at) / 1000);
        if (seconds >= 6) parts.push('leitura atrasada há ' + (seconds < 90 ? seconds + ' s' : Math.round(seconds / 60) + ' min'));
      }
      const fields = Array.isArray(snapshot?.fields) ? snapshot.fields : [];
      const missing = fields.filter(item => item && String(item.status || '') !== 'VALID').length;
      if (missing > 0) parts.push(missing + (missing === 1 ? ' campo sem leitura (aparece como —)' : ' campos sem leitura (aparecem como —)'));
      else if (snapshot?.partial === true && fields.length > 0) parts.push('leitura parcial');
      return parts.join(' · ');
    },

    liveFuelState(value) {
      const raw = String(value || '').trim().toUpperCase();
      if (!raw || raw === '—' || raw === '--') return { kind: 'unknown', label: '—', active: false };
      if (raw.includes('TRANS') || raw.includes('COMUT')) return { kind: 'transition', label: 'Transição', active: false };
      if (raw.includes('CUTOFF')) return { kind: 'cutoff', label: 'Corte', active: false };
      if (raw.includes('DESLIG') || raw.includes('SEM_MOTOR') || raw.includes('ENGINE_OFF')) {
        return { kind: 'off', label: 'Desligado', active: false };
      }
      if (raw.includes('GNV') || raw.includes('CNG') || raw === 'GAS' || raw.includes('GAS_ATIVO')) {
        return { kind: 'gas', label: 'GNV', active: true };
      }
      if (raw.includes('GASOLINA') || raw.includes('PETROL') || raw.includes('ETANOL')) {
        return { kind: 'petrol', label: 'Gasolina', active: true };
      }
      return { kind: 'unknown', label: '—', active: false };
    },

    currentBand(snapshot = {}, live = {}) {
      const thresholds = physicalVector(snapshot, 'MNFLD_PRESS_THD');
      const mapBar = finite(live?.mapBar ?? live?.load_bar ?? live?.map_bar);
      if (mapBar === null || thresholds.length < 2) return null;
      if (thresholds.some(value => finite(value) === null)) return null;

      const values = thresholds.map(Number);
      for (let index = 0; index < values.length - 1; index += 1) {
        if (!(values[index + 1] > values[index])) return null;
      }
      if (!(mapBar > values[0] && mapBar < values[values.length - 1])) return null;

      for (let index = 0; index < values.length - 1; index += 1) {
        if (mapBar > values[index] && mapBar <= values[index + 1]) {
          return { index, lower: values[index], upper: values[index + 1] };
        }
      }
      return null;
    },

    currentZone(snapshot = {}, live = {}) {
      return this.liveRegion(snapshot, live).zone;
    },

    zoneSurface(snapshot = {}, human = {}) {
      const thresholds = physicalVector(snapshot, 'MNFLD_PRESS_THD');
      if (thresholds.length !== 18 || thresholds.some(value => finite(value) === null)) return [];
      if (thresholds.some((value, index) => index > 0 && !(value > thresholds[index - 1]))) return [];
      // São 18 limiares físicos, portanto 17 intervalos; não inventar threshold[18].
      const edges = [0, 6, 10, 14, 17];
      const state = (flags, index) => !Array.isArray(flags) || flags.length !== 4
        ? 'unknown' : flags[index] === true ? 'acquired' : 'missing';
      return Array.from({ length: 4 }, (_, index) => ({
        zone: index + 1,
        lower: thresholds[edges[index]],
        upper: thresholds[edges[index + 1]],
        petrolState: state(human.petrolZoneFlags, index),
        gasState: state(human.gasZoneFlags, index),
      }));
    },

    referencePoints(snapshot = {}, analysis = {}) {
      const petrolMs = physicalVector(snapshot, 'PETR_INJ_TBP');
      const petrolMap = physicalVector(snapshot, 'PETR_MNFLD_PRESS_RV');
      const gasMap = physicalVector(snapshot, 'GAS_MNFLD_PRESS_RV');
      const analysisPoints = Array.isArray(analysis?.points) ? analysis.points : [];
      const byIndex = new Map(analysisPoints.map(point => [Number(point?.index), point]));
      const count = Math.min(petrolMs.length, petrolMap.length, gasMap.length);
      const points = [];
      for (let index = 0; index < count; index += 1) {
        const x = finite(petrolMs[index]);
        const petrol = finite(petrolMap[index]);
        const gas = finite(gasMap[index]);
        const nativeAnalysis = byIndex.get(index) || {};
        const gasEquivalentMs = finite(nativeAnalysis.gasEquivalentTimeMs);
        if (x !== null && petrol !== null && gas !== null) {
          points.push({ index, petrolMs: x, petrolMapBar: petrol, gasMapBar: gas, gasEquivalentMs });
        }
      }
      return points;
    },

    acquiredPoints(snapshot = {}, fuel = 'gas') {
      const normalized = String(fuel || '').toLowerCase();
      const gas = normalized === 'gas' || normalized === 'gnv' || normalized === 'cng';
      const xValues = physicalVector(snapshot, gas ? 'PETR_INJ_TBUF_GAS' : 'PETR_INJ_TBUF');
      const yValues = physicalVector(snapshot, gas ? 'MNFLD_PRESS_BUF_GAS' : 'MNFLD_PRESS_BUF');
      const counters = vector(snapshot, gas ? 'NUM_BUF_UPD_GAS' : 'NUM_BUF_UPD_PETR');
      const calibration = vector(snapshot, 'CALIBRATION_VAL_1');
      const petrolLow = finite(vector(snapshot, 'VECT_AUTOCAL_U8_1')[0]);
      const petrolNormal = finite(calibration[2]);
      const gasLow = finite(calibration[5]);
      const gasNormal = finite(calibration[8]);
      const count = Math.min(18, xValues.length, yValues.length, counters.length);
      const points = [];
      for (let index = 0; index < count; index += 1) {
        const counter = finite(counters[index]) ?? 0;
        const petrolMs = finite(xValues[index]);
        const mapBar = finite(yValues[index]);
        const threshold = gas
          ? (index <= 5 ? gasLow : gasNormal)
          : (index <= 5 ? petrolLow : petrolNormal);
        if (counter > 0 && petrolMs !== null && petrolMs > 0 && mapBar !== null) {
          const acquired = threshold !== null && threshold > 0 && counter >= threshold;
          const progress = threshold !== null && threshold > 0
            ? Math.max(0, Math.min(1, counter / threshold))
            : null;
          points.push({
            fuel: gas ? 'GAS' : 'PETROL',
            fuelLabel: gas ? 'GNV' : 'Gasolina',
            index,
            point: index + 1,
            zone: zoneForBand(index) + 1,
            counter,
            threshold,
            progress,
            acquisitionState: acquired ? 'ACQUIRED' : 'COLLECTING',
            petrolMs,
            mapBar,
          });
        }
      }
      return points;
    },

    referenceDomain(points = [], history = [], zoneSurface = [], acquiredPoints = []) {
      const physicalFloor = Array.isArray(zoneSurface) && zoneSurface.length
        ? Math.max(0, finite(zoneSurface[0]?.lower) ?? 0)
        : 0;
      const yMin = Math.min(physicalFloor, AUTO_CAL_OPERATIONAL_MAP_MAX_BAR - 0.05);
      const yMax = AUTO_CAL_OPERATIONAL_MAP_MAX_BAR;
      const all = [...points, ...history, ...acquiredPoints].filter(point =>
        [finite(point?.petrolMapBar), finite(point?.gasMapBar), finite(point?.mapBar)]
          .some(value => value !== null && value >= yMin && value <= yMax));
      if (!all.length) return null;
      const xValues = all.flatMap(point => {
        const values = [finite(point.petrolMs)];
        const equivalent = finite(point.gasEquivalentMs);
        if (equivalent !== null) values.push(equivalent);
        return values.filter(value => value !== null);
      });
      if (!xValues.length) return null;
      // Lista já filtrada por finite() e não vazia; o reduce evita ±Infinity/NaN mesmo se isso mudar.
      let xMin = xValues.reduce((best, value) => (value < best ? value : best), xValues[0]);
      let xMax = xValues.reduce((best, value) => (value > best ? value : best), xValues[0]);
      if (!Number.isFinite(xMin) || !Number.isFinite(xMax)) return null;
      if (xMax - xMin < 0.01) {
        const pad = Math.max(0.25, Math.abs(xMin) * 0.08);
        xMin -= pad; xMax += pad;
      } else {
        const pad = Math.max(0.08, (xMax - xMin) * 0.05);
        xMin -= pad; xMax += pad;
      }
      xMin = Math.max(0, xMin);
      return { xMin, xMax, yMin, yMax };
    },

    projectLive(live, scale) {
      if (!live || !scale) return null;
      const outsideX = live.petrolMs < scale.xMin || live.petrolMs > scale.xMax;
      const outsideY = live.mapBar < scale.yMin || live.mapBar > scale.yMax;
      const clampedX = Math.max(scale.xMin, Math.min(scale.xMax, live.petrolMs));
      const clampedY = Math.max(scale.yMin, Math.min(scale.yMax, live.mapBar));
      return {
        x: scale.xFor(clampedX),
        y: scale.yFor(clampedY),
        outOfRange: outsideX || outsideY,
      };
    },

    liveLabelAnchor(projected, scale) {
      if (!projected || !scale) return { x: 0, y: 0, textAnchor: 'start' };
      const left = scale.xFor(scale.xMin);
      const right = scale.xFor(scale.xMax);
      const top = scale.yFor(scale.yMax);
      const bottom = scale.yFor(scale.yMin);
      const labelOnLeft = projected.x > right - 184;
      const belowPoint = projected.y < top + 36;
      const rawX = labelOnLeft ? projected.x - 12 : projected.x + 12;
      const rawY = projected.y + (belowPoint ? 24 : -12);
      return {
        x: Math.max(left + 10, Math.min(right - 10, rawX)),
        y: Math.max(top + 26, Math.min(bottom - 16, rawY)),
        textAnchor: labelOnLeft ? 'end' : 'start',
      };
    },

    referenceComparison(previousPoints = [], currentPoints = []) {
      const previousByIndex = new Map((Array.isArray(previousPoints) ? previousPoints : [])
        .map(point => [Number(point?.index), point]));
      const rows = (Array.isArray(currentPoints) ? currentPoints : [])
        .map(point => {
          const previous = previousByIndex.get(Number(point?.index));
          if (!previous) return null;
          const mapDelta = finite(point.petrolMapBar) !== null && finite(previous.petrolMapBar) !== null
            ? Number((point.petrolMapBar - previous.petrolMapBar).toFixed(3))
            : null;
          const gasDelta = finite(point.gasMapBar) !== null && finite(previous.gasMapBar) !== null
            ? Number((point.gasMapBar - previous.gasMapBar).toFixed(3))
            : null;
          if (mapDelta === null && gasDelta === null) return null;
          return { index: point.index, petrolMs: point.petrolMs, mapDelta, gasDelta };
        })
        .filter(Boolean);
      if (!rows.length) {
        return { available: false, count: 0, mapDeltaAvgBar: null, gasDeltaAvgBar: null, changedPoints: [] };
      }
      const avg = key => {
        const values = rows.map(row => finite(row[key])).filter(value => value !== null);
        if (!values.length) return null;
        return Number((values.reduce((sum, value) => sum + value, 0) / values.length).toFixed(3));
      };
      return {
        available: true,
        count: rows.length,
        mapDeltaAvgBar: avg('mapDelta'),
        gasDeltaAvgBar: avg('gasDelta'),
        changedPoints: rows
          .slice()
          .sort((a, b) => Math.abs(finite(b.gasDelta) ?? 0) - Math.abs(finite(a.gasDelta) ?? 0))
          .slice(0, 3),
      };
    },

    referenceFingerprint(snapshot = {}, analysis = {}) {
      const points = this.referencePoints(snapshot, analysis);
      if (!points.length) return '';
      return points.map(point => [
        point.index,
        finite(point.petrolMs) === null ? 'x' : Number(point.petrolMs).toFixed(4),
        finite(point.petrolMapBar) === null ? 'x' : Number(point.petrolMapBar).toFixed(4),
        finite(point.gasMapBar) === null ? 'x' : Number(point.gasMapBar).toFixed(4),
        finite(point.gasEquivalentMs) === null ? 'x' : Number(point.gasEquivalentMs).toFixed(4),
      ].join(':')).join('|');
    },

    referenceTransition(previousProjection = {}, nextProjection = {}, previousSnapshot = {}, nextSnapshot = {}, previousAnalysis = {}) {
      const previousSession = String(previousProjection?.sessionId ?? '');
      const nextSession = String(nextProjection?.sessionId ?? '');
      const sessionChanged = previousSession !== nextSession;
      const previousUsable = previousProjection?.referenceUsable === true;
      const nextUsable = nextProjection?.referenceUsable === true;
      const referenceLost = previousUsable && !nextUsable;
      const referenceRegained = !previousUsable && nextUsable;
      const previousHash = this.referenceFingerprint(previousSnapshot, previousAnalysis);
      const nextHash = this.referenceFingerprint(nextSnapshot, nextProjection?.analysis || {});
      const referenceChanged = previousUsable && nextUsable &&
        !!(previousHash && nextHash && previousHash !== nextHash);
      return {
        sessionChanged,
        referenceChanged,
        referenceLost,
        referenceRegained,
        resetSelection: sessionChanged || referenceChanged || referenceLost || referenceRegained,
        clearHistory: sessionChanged,
        previousPoints: !sessionChanged && referenceChanged
          ? this.referencePoints(previousSnapshot, previousAnalysis)
          : [],
      };
    },

    bandStrip(snapshot = {}, projection = {}) {
      const counters = vector(snapshot, 'NUM_BUF_UPD_GAS');
      const rawZones = vector(snapshot, 'ACQUIRED_ZONES_GAS');
      const projectedZones = projectedZoneFlags(projection, 'gas');
      const events = Array.isArray(projection?.correlation)
        ? projection.correlation
        : Array.isArray(snapshot.nativeMaturityEvents) ? snapshot.nativeMaturityEvents : [];
      const byBand = new Map(events.map(event => [Number(event?.bandIndex), event]));
      const persistent = projection?.correlationState && typeof projection.correlationState === 'object'
        ? projection.correlationState
        : {};
      const correlatedBands = new Set(Array.isArray(persistent.correlatedBands)
        ? persistent.correlatedBands.map(Number).filter(Number.isInteger)
        : []);
      const retryableBands = new Set(Array.isArray(persistent.retryableBands)
        ? persistent.retryableBands.map(Number).filter(Number.isInteger)
        : []);
      return Array.from({ length: 18 }, (_, index) => {
        const counter = finite(counters[index]) ?? 0;
        const zone = zoneForBand(index);
        const event = byBand.get(index) || null;
        const correlated = correlatedBands.has(index) || String(event?.correlationState || '') === 'CORRELATED';
        const retryable = retryableBands.has(index);
        const stateName = correlated ? 'anchored' : (event || retryable) ? 'mature' : counter > 0 ? 'activity' : 'empty';
        return {
          index,
          zone,
          counter,
          zoneAcquired: projectedZones ? projectedZones[zone] === true : (finite(rawZones[zone]) ?? 0) > 0,
          state: stateName,
          event,
        };
      });
    },

    referenceSourceLabel(projection = {}) {
      const source = String(projection?.source || '').toUpperCase();
      const freshness = String(projection?.freshness || '').toUpperCase();
      if (source === 'NATIVE_MONITOR') {
        return freshness === 'CURRENT_SESSION' ? 'Monitor nativo · sessão atual' : 'Monitor nativo';
      }
      if (source === 'MANUAL_READER') {
        return freshness === 'CURRENT_SESSION' ? 'Leitura manual · sessão atual' : 'Leitura manual';
      }
      if (freshness === 'STALE_SESSION') return 'Sem fonte atual · sessão anterior rejeitada';
      if (source === 'NONE') return 'Sem fonte confiável';
      return source || '—';
    },

    bandNarrative(band = {}) {
      const eventState = String(band?.event?.correlationState || '');
      if (band.state === 'anchored') {
        return eventState === 'CORRELATED'
          ? 'Nesta leitura, a região amadureceu e encontrou correlação física confiável.'
          : 'Correlação física confiável confirmada nesta sessão.';
      }
      if (band.state === 'mature') {
        return band.event
          ? 'Nesta leitura, a região amadureceu; a correlação física ainda não foi confirmada com confiança.'
          : 'Região madura nesta sessão; aguardando nova janela de telemetria para tentar a correlação física novamente.';
      }
      return band.counter > 0
        ? 'A ECU registrou atividade nesta região.'
        : 'Ainda não há atividade nesta região.';
    },

    toggleAction(enabled) {
      const value = finite(enabled);
      if (value === 1) return 'DISABLE_AUTO_CAL';
      if (value === 0) return 'ENABLE_AUTO_CAL';
      return null;
    },

    pointSelectionTransition(pendingKeys = [], actionState = {}, referenceTransition = {}) {
      const pending = Array.isArray(pendingKeys)
        ? pendingKeys.map(String).filter(Boolean)
        : [];
      const state = String(actionState?.state || '').toUpperCase();

      if (referenceTransition?.sessionChanged === true) {
        return { clear: true, preserve: false, restore: [], pending: [], reason: 'SESSION_CHANGED' };
      }
      if (!pending.length) {
        return {
          clear: referenceTransition?.resetSelection === true,
          preserve: false,
          restore: [],
          pending: [],
          reason: referenceTransition?.resetSelection === true ? 'REFERENCE_CHANGED' : 'IDLE',
        };
      }
      if (state === 'CONFIRMED') {
        return { clear: true, preserve: false, restore: [], pending: [], reason: 'CONFIRMED' };
      }
      if (state === 'FAILED') {
        return {
          clear: false,
          preserve: true,
          restore: pending,
          pending: [],
          reason: 'FAILED_RETAIN_INTENT',
        };
      }
      return {
        clear: false,
        preserve: true,
        restore: pending,
        pending,
        reason: 'IN_FLIGHT',
      };
    },


  };

  class AutoCalCockpit {
    constructor(app) {
      this.app = app;
      this.store = app.store;
      this.scheduler = app.scheduler;
      this.api = ns.AutoCalApi;
      this.active = false;
      this.prepared = null;
      this.state = {};
      this.snapshot = {};
      this.projection = {};
      this.analysis = {};
      this.referenceUsable = false;
      this.operationalPending = false;
      this.readerState = {};
      this.readerSnapshot = {};
      this.acquisitionState = {};
      this.acquisitionSnapshot = {};
      this.actionState = {};
      this.sessionState = {};
      this.sessions = [];
      this.sessionDrawerOpen = false;
      this.chartScale = null;
      this.cursor = new ns.LiveStore.EaseCursor(() => this.panel?.querySelector('.autocal-live-layer'));
      this.previousReferencePoints = [];
      this.comparisonPinned = false;
      this.currentReferencePoints = [];
      this.currentAcquiredPoints = [];
      this.chartHistoryVisible = false;
      this.selectedReferenceIndex = null;
      this.selectedAcquiredPoint = null;
      this.selectedAcquiredPoints = new Set();
      this.pendingPointReacquisitionKeys = new Set();
      this.selectedBandIndex = null;
      this.inject();
      this.bind();
      // Releitura por revisão: evidência, tabelas e sessão só quando andaram (ou o vigia vence);
      // com operação ou releitura de ponto em curso, relê sempre (o progresso não tem revisão própria).
      const revisions = ns.Revisions;
      this.dataGate = revisions ? revisions.gate(revisions.SLOW_KINDS, revisions.WATCHDOG_MS) : { due: () => true, mark() {} };
      this.dataDirty = false;
      this.unsubscribeRevisions = revisions ? revisions.subscribe(kind => { if (kind !== 'live') this.dataDirty = true; }) : () => {};
      this.unsubscribeStatus = this.scheduler.addHook('status', () => {
        if (this.store.get().route !== 'autocal') return;
        if (this.dataGate.due(this.refreshBusy())) { this.refresh(); this.dataGate.mark(); }
        // Confirmação do botão e "estado não chegou" dependem do relógio, não de dado novo: repinta só o botão (render é barato e idempotente).
        else if (this.toggleWaiting || (this.stateUnknownSince && Date.now() - this.stateUnknownSince > 6000)) this.render();
      });
      this.unsubscribeFast = this.scheduler.addHook('fast', () => {
        if (this.store.get().route !== 'autocal') {
          // Fora da aba não há quadro de animação rodando.
          if (this.unsubscribeFrame) { this.unsubscribeFrame(); this.unsubscribeFrame = null; this.cursorFrameAt = null; }
          return;
        }
        if (!this.unsubscribeFrame && typeof this.scheduler.addFrameHook === 'function') {
          this.unsubscribeFrame = this.scheduler.addFrameHook(timestamp => this.animateCursor(timestamp));
        }
        if (this.firstRefreshPending) { this.firstRefreshPending = false; this.dataDirty = false; this.refresh(); this.dataGate.mark(); }
        else if (this.dataDirty) { this.dataDirty = false; this.refresh(); this.dataGate.mark(); }
        this.renderLiveCursor();
      });
      if (this.store.get().route === 'autocal') {
        this.active = true;
        this.firstRefreshPending = true;
      }
    }

    inject() {
      if (!document.querySelector('link[data-autocal-cockpit-style]')) {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = 'styles-autocal-cockpit.css';
        link.dataset.autocalCockpitStyle = 'true';
        document.head.appendChild(link);
      }
      const stack = document.getElementById('autocalScreenHost');
      if (stack && !stack.querySelector('.autocal-cockpit')) {
        const panel = document.createElement('div');
        panel.className = 'autocal-route-panel';
        panel.innerHTML = `
          <section class="autocal-cockpit ar-shell ar-autocal" aria-label="AutoCal da ECU">
            <header class="ar-status" aria-label="AutoCal · Gasolina e GNV" aria-live="polite">
              <h2 class="instrument-title">AutoCal</h2><p id="autocalHumanAction" class="ar-sentence" data-level="neutral">Lendo o estado da ECU…</p><span id="autocalLiveFuel" class="ar-fuel autocal-fuel-chip" data-fuel-state="unknown">—</span>
              <div class="ar-tile"><small>MAP</small><b><span id="autocalLiveMap">—</span><em>bar</em></b></div>
              <div class="ar-tile"><small>Injeção</small><b><span id="autocalLivePetrol">—</span><em>ms</em></b></div>
              <div class="ar-tile"><small>RPM</small><b id="autocalLiveRpm">—</b></div>
              <div class="ar-tile ar-zone"><small>Zona</small><b id="autocalLiveZone">—</b></div>
              <div class="ar-tile ar-automatch" id="autocalAutoMatchTile" data-state="unknown"><small>AutoMatch</small><b id="autocalAutoMatchCount">—</b></div>
              <div class="autocal-reading-controls"><button type="button" class="ar-ghost" data-autocal-history hidden aria-label="Mostrar leitura anterior">Leitura anterior</button><button type="button" data-autocal-toggle class="btn-primary btn-compact" data-loading="true" disabled>Lendo estado…</button></div>
              <span id="autocalLiveTitle" hidden>Aguardando telemetria</span>
              <p id="autocalLiveNarrative" class="ar-sr" hidden></p>
              <span id="autocalNativeState" hidden>Leitura da ECU: aguardando</span>
            </header>

            <section class="ar-chart-card" aria-label="Leitura da ECU · Gasolina × GNV">
              <div class="ar-legend-row">
                <div class="ar-legend" id="autocalLegend" aria-label="Legenda do gráfico"></div>
                <span id="autocalReferenceCount" class="ar-sr" hidden>—</span>
              </div>
              <div id="autocalReferenceChart" class="ar-chart-host"><div class="chart-empty">Aguardando as curvas da ECU.</div></div>
              <div class="ar-readout" id="autocalChartInspector" data-empty="true"><span>Toque num ponto da curva.</span></div>
            </section>

            <div class="ar-act">
              <div class="ar-buttons">
                <details class="instrument-details"><summary>Zonas e histórico</summary><div class="ar-secondary autocal-secondary-stack" role="region" aria-label="Mais sobre o AutoCal">
              <section class="ar-card autocal-zone-card" aria-label="Cobertura das zonas">
                <h4>Zonas aprendidas pela ECU</h4>
                <div id="autocalZoneMeter" class="autocal-zone-meter" aria-label="Zonas AutoCal aguardando leitura">
                  <div class="petrol"><span class="autocal-zone-fuel">Gasolina</span><div class="autocal-zone-cells" role="list"><span class="autocal-zone-cell" data-autocal-zone-petrol="0" data-state="unknown" data-current="false" role="listitem"><b>Z1</b><small>—</small></span><span class="autocal-zone-cell" data-autocal-zone-petrol="1" data-state="unknown" data-current="false" role="listitem"><b>Z2</b><small>—</small></span><span class="autocal-zone-cell" data-autocal-zone-petrol="2" data-state="unknown" data-current="false" role="listitem"><b>Z3</b><small>—</small></span><span class="autocal-zone-cell" data-autocal-zone-petrol="3" data-state="unknown" data-current="false" role="listitem"><b>Z4</b><small>—</small></span></div></div>
                  <div class="gas"><span class="autocal-zone-fuel">GNV</span><div class="autocal-zone-cells" role="list"><span class="autocal-zone-cell" data-autocal-zone-gas="0" data-state="unknown" data-current="false" role="listitem"><b>Z1</b><small>—</small></span><span class="autocal-zone-cell" data-autocal-zone-gas="1" data-state="unknown" data-current="false" role="listitem"><b>Z2</b><small>—</small></span><span class="autocal-zone-cell" data-autocal-zone-gas="2" data-state="unknown" data-current="false" role="listitem"><b>Z3</b><small>—</small></span><span class="autocal-zone-cell" data-autocal-zone-gas="3" data-state="unknown" data-current="false" role="listitem"><b>Z4</b><small>—</small></span></div></div>
                </div>
                <span id="autocalZoneSummary" class="ar-sr">—</span>
              </section>

              <section id="autocalAutoMatchEvidence" class="ar-card autocal-automatch-evidence" data-state="WAITING" aria-live="polite">
                <h4>O que o AutoMatch mudou</h4>
                <b id="autocalAutoMatchEvidenceTitle">Aguardando o primeiro AutoMatch</b>
                <span id="autocalAutoMatchEvidenceDetail"></span>
              </section>

              <div id="autocalResetComparison" class="ar-card autocal-reset-comparison" hidden aria-live="polite"></div>

              <section class="ar-card autocal-session-strip" data-session-level="ok" aria-live="polite">
                <div class="autocal-session-copy">
                  <h4>Sessão</h4>
                  <b id="autocalSessionSummary">Sessões prontas</b>
                  <span id="autocalSessionDetail">Histórico ainda sem dados desta conexão.</span>
                  <span id="autocalSessionState">Salvo automaticamente</span>
                </div>
                <button type="button" data-autocal-sessions class="btn-secondary btn-compact">Ver sessões</button>
              </section>
              <section id="autocalSessionDrawer" class="ar-card autocal-session-drawer" hidden aria-label="Sessões recentes">
                <h4>Sessões recentes</h4><p id="autocalSessionNext"></p>
                <div id="autocalSessionList" class="autocal-session-list"></div>
              </section>
            </div>

            </details>
                <button type="button" class="autocal-reacquire-action" data-autocal-action="RESET_GAS">Reler GNV</button>
                <button type="button" class="autocal-reacquire-action" data-autocal-action="RESET_PETROL">Reler gasolina</button>
                <details class="autocal-reset-menu ar-more">
                  <summary>Mais opções</summary>
                  <div class="autocal-reset-popover" aria-label="Mais opções do AutoCal">
                    ${ns.CurveChart.viewControls()}
                    <section class="autocal-reset-group" data-reset-scope="advanced">
                      <button type="button" data-autocal-action="RESET_K_FACTOR">Resetar Curva K para 1,000</button>
                      <p>O AutoMatch é automático e decidido pela ECU. Resetar volta a Curva K inteira para 1,000: dá para desfazer em um toque. Pausar a leitura é a única ação desta tela que muda o AutoCal da ECU.</p>
                    </section>
                  </div>
                </details>
              </div>
              <small id="autocalRelearnNote" class="autocal-relearn-note" hidden>A ECU reaprendeu desde a última referência.</small>
            </div>

<div id="autocalReview" class="autocal-review" hidden></div>
          </section>`;
        stack.appendChild(panel);
        this.panel = panel;
      } else {
        this.panel = stack?.querySelector('.autocal-route-panel') || null;
      }
    }

    bind() {
      ns.CurveChart.bindView(this.panel, this, () => this.renderReferenceChart(this.snapshot || {}));
      this.panel?.querySelector('[data-autocal-toggle]')?.addEventListener('click', event => {
        const action = event.currentTarget?.dataset?.action;
        if (action) this.runOperational(action);
      });
      this.panel?.querySelectorAll('[data-autocal-action]').forEach(button => {
        button.addEventListener('click', () => {
          button.closest('.autocal-reset-menu')?.removeAttribute('open');
          this.prepare(button.dataset.autocalAction);
        });
      });
      this.panel?.querySelector('[data-autocal-history]')?.addEventListener('click', () => {
        if (!this.previousReferencePoints.length) return;
        this.chartHistoryVisible = !this.chartHistoryVisible;
        this.renderHistoryControl();
        this.renderReferenceChart(this.snapshot);
      });
      this.panel?.querySelector('[data-autocal-sessions]')?.addEventListener('click', event => {
        this.sessionDrawerOpen = !this.sessionDrawerOpen;
        const drawer = document.getElementById('autocalSessionDrawer');
        if (drawer) drawer.hidden = !this.sessionDrawerOpen;
        event.currentTarget.textContent = this.sessionDrawerOpen ? 'Ocultar sessões' : 'Ver sessões';
        if (this.sessionDrawerOpen) this.loadSessions();
      });
      this.panel?.addEventListener('click', event => {
        if (event.target.closest('[data-autocal-cancel]')) this.cancelPrepared();
        if (event.target.closest('[data-autocal-confirm]')) this.confirmPrepared();
        const band = event.target.closest('[data-autocal-band-index]');
        if (band) this.inspectBand(Number(band.dataset.autocalBandIndex));
        const acquiredPoint = event.target.closest('[data-autocal-acquired-index]');
        if (acquiredPoint) {
          this.inspectAcquiredPoint(
            acquiredPoint.dataset.autocalAcquiredFuel,
            Number(acquiredPoint.dataset.autocalAcquiredIndex),
          );
        }
        const point = event.target.closest('[data-autocal-ref-index]');
        if (point) this.inspectReferencePoint(Number(point.dataset.autocalRefIndex));
        const reacquire = event.target.closest('[data-autocal-reacquire-point]');
        if (reacquire) {
          this.requestPointReacquisition(
            reacquire.dataset.autocalReacquireFuel,
            Number(reacquire.dataset.autocalReacquireIndex),
          );
        }
        const togglePoint = event.target.closest('[data-autocal-toggle-point-selection]');
        if (togglePoint) {
          this.toggleAcquiredPointSelection(
            togglePoint.dataset.autocalReacquireFuel,
            Number(togglePoint.dataset.autocalReacquireIndex),
          );
        }
        if (event.target.closest('[data-autocal-reacquire-selected]')) this.requestSelectedPointReacquisition();
        if (event.target.closest('[data-autocal-clear-point-selection]')) this.clearAcquiredPointSelection();
        const exportButton = event.target.closest('[data-autocal-export-session]');
        if (exportButton?.dataset?.sessionId) this.api?.exportSession?.(exportButton.dataset.sessionId);
      });
    }

    enter() {
      this.active = true;
      this.refresh();
      this.dataGate.mark();
    }

    /** Há operação na ECU ou releitura de ponto em andamento: o progresso não tem revisão, então relê sempre. */
    refreshBusy() {
      return this.operationalPending === true || Boolean(this.toggleWaiting) || this.actionState?.busy === true ||
        (this.pendingPointReacquisitionKeys?.size || 0) > 0;
    }

    refresh() {
      if (!this.api?.available?.()) {
        this.renderUnavailable();
        return;
      }
      const projection = this.api.projection?.() || {};
      const authoritative = projection?.ok === true;
      const previousProjection = this.projection || {};
      this.projection = projection;

      if (!authoritative) {
        const message = String(projection?.error || 'O AutoCal da ECU não respondeu com estado confiável.');
        this.readerState = {
          state: 'UNAVAILABLE',
          error: message,
          reasonCode: 'AUTOCAL_PROJECTION_UNAVAILABLE',
        };
        this.readerSnapshot = { available: false, fields: [] };
        this.acquisitionSnapshot = { available: false, fields: [] };
        this.acquisitionState = {
          state: 'UNAVAILABLE',
          message,
          error: message,
          reasonCode: 'AUTOCAL_PROJECTION_UNAVAILABLE',
        };
        this.state = this.acquisitionState;
        this.snapshot = { available: false, fields: [] };
        this.analysis = {};
        this.referenceUsable = false;
        this.selectedAcquiredPoint = null;
        this.selectedAcquiredPoints?.clear?.();
        this.actionState = this.api.actionStatus() || {};
        this.operationalPending = this.actionState?.busy === true ||
          [
          'QUEUED', 'READING_BEFORE', 'SENDING_ACTION', 'READING_AFTER',
          'READING_FINISH_SOURCE', 'SENDING_FINISH_COMMIT', 'VERIFYING_FINISH',
          'RESETTING_K', 'VERIFYING_K_RESET',
        ].includes(String(this.actionState?.state || ''));
        this.sessionState = this.api.sessionStatus?.() || {};
        this.render();
        return;
      }

      this.readerState = projection.manualStatus || {};
      this.readerSnapshot = projection.manualSnapshot || {};
      const acquisitionStatus = projection.nativeStatus || {};
      this.acquisitionSnapshot = projection.nativeSnapshot || {};
      this.acquisitionState = {
        ...acquisitionStatus,
        latestSnapshot: this.acquisitionSnapshot?.available
          ? this.acquisitionSnapshot
          : acquisitionStatus.latestSnapshot,
      };
      this.state = this.acquisitionState;

      const nextSnapshot = projection.snapshot || {};
      const nextAnalysis = projection.analysis || {};
      this.referenceUsable = projection.referenceUsable === true;

      const referenceTransition = AutoCalUxModel.referenceTransition(
        previousProjection,
        projection,
        this.snapshot,
        nextSnapshot,
        this.analysis,
      );
      const nextActionState = this.api.actionStatus() || {};
      const selectionTransition = AutoCalUxModel.pointSelectionTransition(
        Array.from(this.pendingPointReacquisitionKeys || []),
        nextActionState,
        referenceTransition,
      );
      if (referenceTransition.clearHistory) {
        this.previousReferencePoints = [];
        this.comparisonPinned = false;
        this.chartHistoryVisible = false;
      } else if (referenceTransition.referenceChanged && !this.comparisonPinned) {
        this.previousReferencePoints = referenceTransition.previousPoints;
      }
      if (referenceTransition.resetSelection || selectionTransition.clear) {
        this.selectedReferenceIndex = null;
        this.selectedAcquiredPoint = null;
      }
      if (selectionTransition.clear) this.selectedAcquiredPoints?.clear?.();
      if (selectionTransition.restore.length) {
        this.selectedAcquiredPoints = new Set(selectionTransition.restore);
      }
      this.pendingPointReacquisitionKeys = new Set(selectionTransition.pending);
      this.snapshot = nextSnapshot || {};
      this.analysis = nextAnalysis;
      this.actionState = nextActionState;
      this.operationalPending = this.actionState?.busy === true ||
        [
          'QUEUED', 'READING_BEFORE', 'SENDING_ACTION', 'READING_AFTER',
          'READING_FINISH_SOURCE', 'SENDING_FINISH_COMMIT', 'VERIFYING_FINISH',
          'RESETTING_K', 'VERIFYING_K_RESET',
        ].includes(String(this.actionState?.state || ''));
      this.sessionState = this.api.sessionStatus?.() || {};
      this.render();
    }

    loadSessions() {
      if (!this.api?.available?.()) return;
      const next = this.api.sessions?.();
      this.sessions = Array.isArray(next) ? next : [];
      this.renderSessionState();
    }

    runOperational(action) {
      if (action === 'REREAD_STATE') { this.stateUnknownSince = Date.now(); this.refresh(); return; }
      if (!['ENABLE_AUTO_CAL', 'DISABLE_AUTO_CAL'].includes(action) || !this.api?.available?.()) return;
      const enable = action === 'ENABLE_AUTO_CAL';
      this.operationalPending = true;
      this.render();
      const result = this.api.setAcquisitionEnabled?.(enable) || { ok: false, error: 'Ação operacional indisponível.' };
      if (result?.ok !== true) {
        this.operationalPending = false;
        this.store.patch({ alert: { level: 'warning', message: result?.error || 'Não foi possível alterar a leitura do AutoCal.' } });
      } else {
        // O botão só volta quando a ECU confirmar o novo estado (ou em 10 s): uma resposta "ok" do envio não é a conferência.
        this.toggleWaiting = { target: enable ? 1 : 0, since: Date.now() };
        this.store.patch({ alert: { level: 'ok', message: enable
          ? 'Início enviado. Conferindo na ECU…'
          : 'Pausa enviada. Conferindo na ECU…' } });
      }
      this.refresh();
    }

    /** Um toque: abre a Curva K, que tira a foto e só então zera. Sem a tela Curva K cai no fluxo antigo. */
    resetViaCurve() {
      const router = this.app?.router || root.OmegasApp?.router;
      if (!router || typeof router.open !== 'function') return false;
      return router.open('curve', 'editor', { resetNow: true }) === true;
    }

    prepare(action) {
      if (!action || !this.api?.available?.()) return;
      // Resetar a Curva K tem UM caminho: o da aba Curva K (foto antes, zera, conferência, Desfazer).
      if (action === 'RESET_K_FACTOR' && this.resetViaCurve()) return;
      const result = this.api.prepare(action);
      if (!result?.ok || !result?.prepared) {
        this.store.patch({ alert: { level: 'warning', message: result?.error || 'A ação AutoCal não pôde ser preparada.' } });
        return;
      }
      this.prepared = result;
      this.renderReview();
    }

    cancelPrepared() {
      this.api?.cancelPreparation?.();
      this.prepared = null;
      const review = document.getElementById('autocalReview');
      if (review) { review.hidden = true; review.innerHTML = ''; }
      this.refresh();
    }

    confirmPrepared() {
      const prepared = this.prepared;
      if (!prepared?.preparationId) return;
      const result = this.api.execute(prepared.preparationId);
      if (result?.ok !== true) {
        this.store.patch({ alert: { level: 'warning', message: result?.error || 'A ação AutoCal não pôde ser executada.' } });
        return;
      }
      if (this.referenceUsable && String(prepared.action || '').startsWith('RESET_')) {
        this.previousReferencePoints = AutoCalUxModel.referencePoints(this.snapshot, this.analysis);
        this.comparisonPinned = this.previousReferencePoints.length > 0;
        this.chartHistoryVisible = this.comparisonPinned;
      }
      this.prepared = null;
      const review = document.getElementById('autocalReview');
      if (review) { review.hidden = true; review.innerHTML = ''; }
      this.store.patch({ alert: { level: 'working', message: 'Comando enviado para a ECU. Aguarde a conferência.' } });
      this.refresh();
    }


    render() {
      const snapshot = this.snapshot || {};
      const state = this.state || {};
      const events = Array.isArray(this.projection?.correlation)
        ? this.projection.correlation
        : Array.isArray(snapshot.nativeMaturityEvents) ? snapshot.nativeMaturityEvents : [];
      const human = AutoCalUxModel.humanState(snapshot, state, this.projection);
      const acquisitionName = String(state.state || '').toUpperCase();
      const acquisitionLabel = acquisitionName === 'UNAVAILABLE' ? 'indisponível'
        : acquisitionName === 'WAITING_TELEMETRY_SETTLE' ? 'conectando'
        : acquisitionName === 'PROBE_FAILED' || acquisitionName === 'FAILED' ? 'erro'
        : human.enabled === 1 ? 'adquirindo'
        : human.enabled === 0 ? 'pausado'
        : acquisitionName === 'DISCONNECTED' ? 'desconectado'
        : snapshot.available ? 'pronto' : 'aguardando';

      this.renderSentence(human, acquisitionName);
      this.renderAutoMatchTile(human);
      this.renderRelearn();
      this.text('autocalAutoMatchEvidenceTitle', human.autoMatchEvidenceTitle);
      this.text('autocalAutoMatchEvidenceDetail', human.autoMatchEvidenceDetail);
      const autoMatchEvidence = document.getElementById('autocalAutoMatchEvidence');
      if (autoMatchEvidence) autoMatchEvidence.dataset.state = human.autoMatchEvidenceState;
      this.text('autocalNativeState', 'Leitura da ECU: ' + acquisitionLabel);
      this.text('autocalZoneSummary', human.gasZones === null ? 'Zonas GNV sem leitura' : human.gasZones + '/4 zonas GNV');
      this.text('autocalStateRaw', state.state || '—');
      this.text('autocalEnableRaw', human.enabled === 1 ? 'ATIVA' : human.enabled === 0 ? 'PAUSADA' : '—');
      this.text('autocalSnapshotHash', snapshot.snapshotHash ? String(snapshot.snapshotHash).slice(0, 10) : '—');
      const readingNote = AutoCalUxModel.readingNote(snapshot, Date.now());
      this.text('autocalReferenceSource', AutoCalUxModel.referenceSourceLabel(this.projection) + (readingNote ? ' · ' + readingNote : ''));
      this.text('autocalMaturityRaw', events.length);
      this.renderZoneMeter(human);
      this.renderSessionState();
      this.renderLiveNarrative();

      if (this.toggleWaiting && (human.enabled === this.toggleWaiting.target || Date.now() - this.toggleWaiting.since > 10000)) this.toggleWaiting = null;
      const waiting = this.operationalPending || Boolean(this.toggleWaiting);
      // "Lendo estado…" não pode ficar eterno: se o estado da ECU não chega em 6 s, o botão diz isso e deixa reler com um toque.
      if (human.enabled === null || human.enabled === undefined || toggleActionKnown(human.enabled) === null) {
        if (!this.stateUnknownSince) this.stateUnknownSince = Date.now();
      } else this.stateUnknownSince = 0;
      const stateStuck = Boolean(this.stateUnknownSince) && Date.now() - this.stateUnknownSince > 6000;
      const toggle = this.panel?.querySelector('[data-autocal-toggle]');
      if (toggle) {
        const action = AutoCalUxModel.toggleAction(human.enabled) || (stateStuck ? 'REREAD_STATE' : null);
        toggle.dataset.action = action || '';
        toggle.disabled = !action || waiting;
        toggle.textContent = waiting
          ? 'Confirmando ECU…'
          : action === 'DISABLE_AUTO_CAL'
            ? 'Pausar leitura'
            : action === 'ENABLE_AUTO_CAL' ? 'Iniciar leitura' : action === 'REREAD_STATE' ? 'Estado não chegou · reler' : 'Lendo estado…';
        toggle.dataset.loading = action || waiting ? 'false' : 'true';
      }

      this.panel?.querySelectorAll('[data-autocal-action]').forEach(button => {
        button.disabled = waiting;
      });

      this.renderHistoryControl();

      this.renderReferenceChart(snapshot);
      this.renderBands(snapshot);
      this.renderEvents(events);
      this.renderActionState();
    }

    /** UMA frase humana de estado (nada de jargão): o que a ECU está fazendo e o que falta. */
    /** [fuelKind] = combustível de AGORA pela telemetria ('petrol', 'gas', …): a frase nunca manda "dirigir no GNV" com o carro na gasolina. */
    sentenceFor(human, acquisitionName, fuelKind) {
      const zones = list => list.map(zone => 'Z' + zone).join(', ');
      if (acquisitionName === 'UNAVAILABLE' || acquisitionName === 'PROBE_FAILED' || acquisitionName === 'FAILED') {
        return { level: 'error', text: 'Sem leitura da ECU. Confira o cabo: o app tenta de novo sozinho.' };
      }
      if (acquisitionName === 'WAITING_TELEMETRY_SETTLE') return { level: 'neutral', text: 'Conectando à leitura da ECU…' };
      if (human.enabled === 0) return { level: 'warn', text: 'Leitura pausada. Toque em Iniciar leitura para continuar aprendendo.' };
      if (human.enabled === 1) {
        if (human.gasMissingZones.length) {
          return fuelKind === 'petrol'
            ? { level: 'neutral', text: 'Aprendendo. Quando o carro passar para o GNV, falta ' + zones(human.gasMissingZones) + '.' }
            : { level: 'neutral', text: 'Aprendendo: dirija normal no GNV. Falta ' + zones(human.gasMissingZones) + '.' };
        }
        if (human.petrolMissingZones.length) return { level: 'neutral', text: 'GNV completo. Falta a gasolina em ' + zones(human.petrolMissingZones) + '.' };
        if (human.gasZones === 4 && human.petrolZones === 4) return { level: 'ok', text: 'Gasolina e GNV aprendidos.' };
        return { level: 'neutral', text: 'Leitura ativa. Aguardando a ECU publicar as zonas.' };
      }
      return { level: 'neutral', text: 'Lendo o estado da ECU…' };
    }

    renderSentence(human, acquisitionName) {
      const node = document.getElementById('autocalHumanAction');
      if (!node) return;
      const live = ns.LiveStore && typeof ns.LiveStore.read === 'function' && this.store ? ns.LiveStore.read(this.store.get(), { fallback: true }) : null;
      const sentence = this.sentenceFor(human, acquisitionName, live ? AutoCalUxModel.liveFuelState(live.fuel).kind : 'unknown');
      if (node.textContent !== sentence.text) node.textContent = sentence.text;
      if (node.dataset.level !== sentence.level) node.dataset.level = sentence.level;
    }

    /** Contador do AutoMatch no topo, ao lado de MAP, Injeção, RPM e Zona. Desconhecido = "—", nunca 0. */
    renderAutoMatchTile(human) {
      const count = human.autoMatchCount;
      const max = human.maxAutoMatch;
      const text = count === null ? '—' : Math.round(count) + (max !== null && max > 0 ? '/' + Math.round(max) : '');
      this.text('autocalAutoMatchCount', text);
      const tile = document.getElementById('autocalAutoMatchTile');
      if (tile) D.setDataIfChanged(tile, 'state', count === null ? 'unknown' : human.autoMatchQuotaReached ? 'full' : 'running');
    }

    renderSessionState() {
      const narrative = AutoCalUxModel.sessionNarrative(this.sessionState || {});
      this.text('autocalSessionSummary', narrative.title);
      this.text('autocalSessionDetail', narrative.detail);
      this.text(
        'autocalSessionState',
        narrative.mirrorFailed
          ? 'Protegido na memória interna'
          : narrative.recording ? 'Salvando em Downloads/Omegas' : 'Salvo em Downloads/Omegas',
      );
      this.text('autocalSessionNext', narrative.next);
      const strip = this.panel?.querySelector('.autocal-session-strip');
      if (strip) strip.dataset.sessionLevel = narrative.level;

      const host = document.getElementById('autocalSessionList');
      if (!host || !this.sessionDrawerOpen) return;
      const sessions = Array.isArray(this.sessions) ? this.sessions.slice(0, 8) : [];
      if (!sessions.length) {
        host.innerHTML = '<p class="empty-copy">Nenhuma sessão gravada ainda.</p>';
        return;
      }
      host.innerHTML = sessions.map((item, index) => {
        const summary = item?.semanticSummary && typeof item.semanticSummary === 'object' ? item.semanticSummary : {};
        const autocal = summary?.autocal && typeof summary.autocal === 'object' ? summary.autocal : {};
        const when = finite(item.createdAt);
        const date = when === null ? 'Data indisponível' : new Date(when).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
        const durationRaw = finite(item.durationMs ?? summary.durationMs);
        const minutes = durationRaw === null ? '—' : Math.max(0, Math.floor(durationRaw / 60000));
        const regionsKnown = Array.isArray(autocal.correlatedRegions);
        const regions = regionsKnown ? autocal.correlatedRegions.length : '—';
        const gasZonesRaw = finite(autocal.gasZones);
        const gasZones = gasZonesRaw === null ? '—' : Math.max(0, Math.min(4, Math.round(gasZonesRaw)));
        const active = item.active === true;
        const id = escapeHtml(item.id || '');
        return '<article class="autocal-session-item" data-active="' + (active ? 'true' : 'false') + '">' +
          '<div><small>' + (active ? 'AGORA' : date) + '</small><b>' + minutes + ' min · ' + regions + ' ' + (regions === 1 ? 'região' : 'regiões') + '</b><span>GNV ' + gasZones + '/4 · ' + escapeHtml(item.reason || 'Sessão MP48') + '</span></div>' +
          '<button type="button" class="secondary" data-autocal-export-session data-session-id="' + id + '">Exportar</button>' +
        '</article>';
      }).join('');
    }

    /** Uma linha discreta, só quando o cérebro diz que a ECU reaprendeu (ecuDrift/relearnSuggested); senão nada. */
    renderRelearn() {
      const eq = ns.CurveChart?.evidence?.eq || null;
      const reference = eq?.reference || {};
      const drift = finite(reference.ecuDrift);
      const cells = [].concat(eq?.ownPetrol?.cells || [], eq?.ownGas?.cells || []);
      // 0,08 = EquivalenceTolerances.DIVERGENCE_ALARM (Kotlin); a flag por célula vale sozinha.
      const relearned = cells.some(cell => cell && cell.relearnSuggested === true) || (drift !== null && Math.abs(drift) > 0.08);
      const note = document.getElementById('autocalRelearnNote');
      if (note) note.hidden = !relearned;
      this.text('autocalDriftRaw', drift === null ? '—' : (drift * 100).toFixed(1).replace('.', ',') + ' %');
    }

    renderLiveNarrative() {
      const telemetry = this.store.get().telemetry || {};
      const live = AutoCalUxModel.livePoint(telemetry, this.projection);
      if (!live) {
        const ageMs = finite(telemetry.telemetryAgeMs ?? telemetry.ageMs);
        const stale = telemetry.valid === true && ageMs !== null && ageMs > AUTO_CAL_LIVE_STALE_MS;
        this.text('autocalLiveTitle', stale ? 'Telemetria com atraso · há ' + Math.round(ageMs / 1000) + ' s' : 'Aguardando telemetria válida');
        this.text('autocalLiveFuel', '—');
        const fuelChip = document.getElementById('autocalLiveFuel');
        if (fuelChip) fuelChip.dataset.fuelState = 'unknown';
        this.text('autocalLiveRpm', '—');
        this.text('autocalLivePetrol', '—');
        this.text('autocalLiveMap', '—');
        this.text('autocalLiveZone', '—');
        this.text('autocalLiveNarrative', stale
          ? 'Leitura atrasada há ' + Math.round(ageMs / 1000) + ' s: a última leitura passou da janela curta. AGORA foi ocultado até chegar uma leitura nova; a referência da ECU não foi alterada.'
          : 'O cursor AGORA aparece quando RPM, Injeção e MAP chegam válidos. Ele nunca vira ponto lido pela ECU.');
        return;
      }
      const rpmLabel = live.rpm === null ? 'RPM —' : Math.round(live.rpm).toLocaleString('pt-BR') + ' RPM';
      this.text('autocalLiveTitle', 'Motor nesta região agora');
      const fuelState = AutoCalUxModel.liveFuelState(live.fuel);
      this.text('autocalLiveFuel', fuelState.label);
      const fuelChip = document.getElementById('autocalLiveFuel');
      if (fuelChip) fuelChip.dataset.fuelState = fuelState.kind;
      this.text('autocalLiveRpm', live.rpm === null ? '—' : Math.round(live.rpm).toLocaleString('pt-BR'));
      this.text('autocalLivePetrol', D.ms(live.petrolMs));
      this.text('autocalLiveMap', D.bar(live.mapBar));
      const region = AutoCalUxModel.liveRegion(this.snapshot || {}, live);
      this.text('autocalLiveZone', region.kind === 'idle' ? 'Lenta' : region.zone === null ? '—' : 'Z' + region.zone);
      const enabled = AutoCalUxModel.humanState(this.snapshot || {}, this.acquisitionState || {}, this.projection).enabled;
      const acquisitionCopy = enabled === 1
        ? 'Leitura da ECU ativa. Se a condição estabilizar, a ECU pode fortalecer esta região.'
        : enabled === 0 ? 'Leitura da ECU pausada. O ponto AGORA é só leitura ao vivo.' : 'Estado da leitura da ECU ainda não confirmado.';
      const delayCopy = live.grey ? ' Leitura atrasada há ' + Math.max(1, Math.round(live.ageMs / 1000)) + ' s: o cursor está em cinza.' : '';
      this.text('autocalLiveNarrative', rpmLabel + ' · ' + D.msUnit(live.petrolMs) + ' · ' + D.barUnit(live.mapBar) + '. ' + acquisitionCopy + delayCopy);
    }

    renderLiveCursor() {
      // Telemetria nova chega a 4–12 Hz e o tick roda a 5 Hz: sem quadro novo (e sem gráfico, snapshot
      // ou estado novos) não há nada a repintar.
      const telemetry = this.store.get().telemetry || {};
      const point = AutoCalUxModel.livePoint(telemetry, this.projection);
      const ageNow = finite(telemetry.telemetryAgeMs ?? telemetry.ageMs);
      const key = point
        ? [point.sequence, point.petrolMs, point.mapBar, point.rpm, point.fuel, point.grey, point.grey ? Math.round(point.ageMs / 1000) : 0].join('|')
        : ['none', telemetry.valid === true, ageNow > AUTO_CAL_LIVE_STALE_MS, ageNow > AUTO_CAL_LIVE_STALE_MS ? Math.round(ageNow / 1000) : 0].join('|');
      const seen = this.cursorSeen;
      if (seen && seen.key === key && seen.snapshot === this.snapshot && seen.state === this.state &&
          seen.projection === this.projection && seen.scale === this.chartScale) return;
      this.cursorSeen = { key, snapshot: this.snapshot, state: this.state, projection: this.projection, scale: this.chartScale };
      const live = AutoCalUxModel.livePoint(this.store.get().telemetry || {});
      // Texto da narrativa: ≤ 2 Hz, ou na hora quando muda região, combustível ou estado (válido/cinza/atrasado).
      const region = live ? AutoCalUxModel.liveRegion(this.snapshot || {}, live) : null;
      const narrativeKey = live ? ['live', region.kind, region.index, AutoCalUxModel.liveFuelState(live.fuel).kind, live.grey, live.grey ? Math.round(live.ageMs / 1000) : 0].join('|') : key;
      const nowMs = Date.now();
      if (narrativeKey !== this.narrativeKey || nowMs - (this.narrativeAt || 0) >= AUTO_CAL_NARRATIVE_MS || seen?.snapshot !== this.snapshot || seen?.state !== this.state) {
        this.narrativeKey = narrativeKey;
        this.narrativeAt = nowMs;
        this.renderLiveNarrative();
      }
      this.renderZoneCursor(live);
      const scale = this.chartScale;
      const layer = this.panel?.querySelector('.autocal-live-layer');
      const bandLayer = this.panel?.querySelector('[data-autocal-current-band]');
      if (!live) {
        D.setAttrIfChanged(layer, 'display', 'none');
        D.setAttrIfChanged(bandLayer, 'display', 'none');
        this.cursor.clear();
        return;
      }
      if (scale && bandLayer) {
        const band = AutoCalUxModel.currentBand(this.snapshot || {}, live);
        const lower = band ? Math.max(band.lower, scale.yMin) : null;
        const upper = band ? Math.min(band.upper, scale.yMax) : null;
        if (band && lower !== null && upper !== null && upper > lower) {
          const x1 = scale.xFor(scale.xMin);
          const x2 = scale.xFor(scale.xMax);
          const y1 = scale.yFor(lower);
          const y2 = scale.yFor(upper);
          D.removeAttrIfPresent(bandLayer, 'display');
          D.setAttrIfChanged(bandLayer, 'data-band-index', String(band.index));
          D.setAttrIfChanged(bandLayer, 'x', Math.min(x1, x2).toFixed(1));
          D.setAttrIfChanged(bandLayer, 'width', Math.abs(x2 - x1).toFixed(1));
          D.setAttrIfChanged(bandLayer, 'y', Math.min(y1, y2).toFixed(1));
          D.setAttrIfChanged(bandLayer, 'height', Math.max(1, Math.abs(y2 - y1)).toFixed(1));
        } else {
          D.setAttrIfChanged(bandLayer, 'display', 'none');
          D.removeAttrIfPresent(bandLayer, 'data-band-index');
        }
      }
      if (!scale || !layer) return;
      const projected = AutoCalUxModel.projectLive(live, scale);
      if (!projected) return;
      D.removeAttrIfPresent(layer, 'display');
      D.setAttrIfChanged(layer, 'data-out-of-range', projected.outOfRange ? 'true' : 'false');
      D.setAttrIfChanged(layer, 'data-stale', live.grey ? 'true' : 'false');
      // O alvo muda a cada quadro novo; quem move o ponto é o quadro de animação (CSS transform, ease ~150 ms).
      this.cursor.setTarget(projected.x, projected.y, scale, projected.outOfRange);
      if (typeof this.scheduler?.addFrameHook !== 'function' || seen?.scale !== this.chartScale) this.cursor.paint();
      const label = this.panel?.querySelector('[data-autocal-live-label]');
      if (label) {
        const text = projected.outOfRange ? 'AGORA · fora da escala' : 'AGORA';
        const shown = live.grey ? text + ' · atrasado' : text;
        if (label.textContent !== shown) label.textContent = shown;
      }
    }

    /** Quadro de animação (rAF do scheduler): o cursor compartilhado só move a camada com CSS transform. */
    animateCursor(timestamp) { this.cursor.frame(timestamp); }

    renderZoneMeter(human) {
      const meter = document.getElementById('autocalZoneMeter');
      if (!meter) return;
      const petrolFlags = Array.isArray(human?.petrolZoneFlags) ? human.petrolZoneFlags.slice(0, 4) : null;
      const gasFlags = Array.isArray(human?.gasZoneFlags) ? human.gasZoneFlags.slice(0, 4) : null;
      const paint = (selector, flags, fuelLabel) => {
        this.panel?.querySelectorAll(selector).forEach(node => {
          const rawIndex = node.dataset.autocalZonePetrol ?? node.dataset.autocalZoneGas;
          const index = Number(rawIndex);
          const known = Array.isArray(flags) && index >= 0 && index < flags.length;
          const acquired = known && flags[index] === true;
          const state = !known ? 'unknown' : acquired ? 'acquired' : 'missing';
          node.dataset.state = state;
          node.dataset.active = acquired ? 'true' : 'false';
          const status = node.querySelector('small');
          // Só o que falta aparece escrito: zona já lida fica só colorida (nada de "OK" repetido 8 vezes).
          if (status) status.textContent = state === 'acquired' ? '' : state === 'missing' ? 'FALTA' : '—';
          node.setAttribute('aria-label', fuelLabel + ' Z' + (index + 1) + ': ' +
            (state === 'acquired' ? 'adquirida' : state === 'missing' ? 'falta adquirir' : 'estado não lido'));
        });
      };
      paint('[data-autocal-zone-petrol]', petrolFlags, 'Gasolina');
      paint('[data-autocal-zone-gas]', gasFlags, 'GNV');
      const petrolMissing = Array.isArray(human?.petrolMissingZones) ? human.petrolMissingZones : [];
      const gasMissing = Array.isArray(human?.gasMissingZones) ? human.gasMissingZones : [];
      const parts = [
        'Gasolina ' + (human?.petrolZones === null ? 'sem leitura' : human?.petrolZones + ' de 4'),
        'GNV ' + (human?.gasZones === null ? 'sem leitura' : human?.gasZones + ' de 4'),
      ];
      if (gasMissing.length) parts.push('faltam GNV ' + gasMissing.map(zone => 'Z' + zone).join(', '));
      if (petrolMissing.length) parts.push('faltam gasolina ' + petrolMissing.map(zone => 'Z' + zone).join(', '));
      meter.setAttribute('aria-label', parts.join('; '));
    }

    renderZoneCursor(live) {
      const currentZone = live ? AutoCalUxModel.currentZone(this.snapshot || {}, live) : null;
      this.panel?.querySelectorAll('.autocal-zone-cell').forEach(node => {
        const rawIndex = node.dataset.autocalZonePetrol ?? node.dataset.autocalZoneGas;
        const zone = Number(rawIndex) + 1;
        D.setDataIfChanged(node, 'current', currentZone !== null && zone === currentZone ? 'true' : 'false');
      });
      this.panel?.querySelectorAll('[data-autocal-zone-surface]').forEach(node => {
        const current = currentZone !== null && Number(node.dataset.autocalZoneSurface) === currentZone;
        D.setDataIfChanged(node, 'current', current ? 'true' : 'false');
        const label = node.querySelector('[data-autocal-zone-label]');
        if (label) D.setTextIfChanged(label, label.dataset.baseLabel);
      });
    }

    renderAcquisitionEpochChart(acquiredPetrol, acquiredGas, epoch, host) {
      ns.CurveChart?.release(host);
      this.chartSignature = null;
      // ProgBase DUMP/TAutoCalUI separates PetrolCurve (native RV), PetrolPoint,
      // GasPoint, GasPointPrev and KLine. Do not collapse them into one reference.
      this.chartScale = null;
      this.chartRenderKey = null;
      const snapshot = this.snapshot || {};
      const petrolCount = acquiredPetrol.length;
      const gasCount = acquiredGas.length;
      const automatch = finite(epoch.nativeAutoMatchCount);
      const quota = finite(this.state?.maxAutomatch ?? snapshot.maxAutomatch);
      const step = automatch === null ? '—' : String(automatch) + (quota !== null ? '/' + quota : '');
      const restartBoth = epoch.petrolPending === true && epoch.gasPending === true;
      const petrolRestart = epoch.petrolPending === true;
      const gasRestart = epoch.gasPending === true;
      const stage = restartBoth
        ? 'Aguardando nova leitura de gasolina e GNV'
        : petrolRestart
          ? 'Gasolina reiniciada: a referência anterior não é a atual'
          : gasRestart
            ? 'GNV reiniciado: gasolina preservada; aguardando leitura nova da ECU'
            : epoch.referencePending
              ? 'GNV atual sendo adquirido: aguardando novo grupo de curvas da ECU'
              : 'Coleta em andamento; ainda sem suporte para comparação';

      // A referência gasolina sobrevive ao RESET_GAS e ao AutoMatch, mas jamais
      // ao RESET_PETROL/RESET_ALL. Verifique também coerência temporal do PAR
      // PETR_INJ_TBP + PETR_MNFLD_PRESS_RV sem exigir a curva GNV reiniciada.
      const petrolCurve = [];
      if (epoch.petrolPending !== true && epoch.petrolReferencePending === false) {
        const axisField = field(snapshot, 'PETR_INJ_TBP');
        const rvField = field(snapshot, 'PETR_MNFLD_PRESS_RV');
        const axisAt = finite(axisField?.capturedAtMs);
        const rvAt = finite(rvField?.capturedAtMs);
        const skewLimit = finite(this.projection?.referenceTimingLimitMs);
        const coherent = String(snapshot.source || '') !== 'ECU_READ' ||
          (axisAt !== null && rvAt !== null && skewLimit !== null &&
            Math.abs(axisAt - rvAt) <= skewLimit);
        const xs = physicalVector(snapshot, 'PETR_INJ_TBP');
        const ys = physicalVector(snapshot, 'PETR_MNFLD_PRESS_RV');
        if (coherent && xs.length === 30 && ys.length === 30) {
          for (let i = 0; i < 30; i += 1) {
            const x = finite(xs[i]), y = finite(ys[i]);
            if (x !== null && x >= 0 && y !== null) {
              petrolCurve.push({ petrolMs: x, petrolMapBar: y });
            }
          }
        }
      }

      // GAS_PREV has its own native buffer pair but NO native counter. Present
      // only as a subdued historical layer; never count it as current GNV.
      const previousGas = [];
      if (finite(epoch.gasGeneration) > 0 && !restartBoth) {
        const xs = physicalVector(snapshot, 'PETR_INJ_TBUF_GAS_PREV');
        const ys = physicalVector(snapshot, 'MNFLD_PRESS_BUF_GAS_PREV');
        for (let i = 0; i < Math.min(18, xs.length, ys.length); i += 1) {
          const x = finite(xs[i]), y = finite(ys[i]);
          if (x !== null && x > 0 && y !== null && y > 0) {
            previousGas.push({ petrolMs: x, mapBar: y });
          }
        }
      }

      // Durante o reset de um combustível o 0 é medida (recomeçou); sem reset e sem resposta da ECU é desconhecido.
      const petrolKnown = epoch.petrolPending === true || vector(snapshot, 'NUM_BUF_UPD_PETR').length > 0;
      const gasKnown = epoch.gasPending === true || vector(snapshot, 'NUM_BUF_UPD_GAS').length > 0;
      this.text('autocalReferenceCount',
        'Gasolina ' + (petrolKnown ? petrolCount : '—') + '/18 · GNV ' + (gasKnown ? gasCount : '—') + '/18 faixas com amostra');
      const kValues = physicalVector(snapshot, 'MUL_ACT');
      const kNarrative = kValues.length === 30
        ? 'Curva K: 30 pontos lidos; só leitura, a ECU não é alterada.'
        : 'Curva K: aguardando a Leitura da ECU.';
      const sourceNarrative = petrolCurve.length
        ? 'Linha contínua = referência gasolina da ECU preservada. '
        : 'Sem curva gasolina temporalmente válida nesta etapa. ';
      const previousNarrative = previousGas.length
        ? 'Círculos esmaecidos = GNV anterior sem contador atual. ' : '';
      const quotaNarrative = automatch !== null && quota !== null && automatch >= quota
        ? ' Cota de AutoMatch atingida; a leitura NÃO terminou.' : '';
      this.readout(stage + '.' + (automatch !== null && quota !== null && automatch >= quota ? ' AutoMatch ' + step + ': a leitura continua.' : ''));

      const current = [...acquiredPetrol, ...acquiredGas, ...petrolCurve, ...previousGas];
      const domain = AutoCalUxModel.referenceDomain([], [], [], current);
      let chart = '';
      if (domain) {
        const width = 1000, height = 400, left = 64, right = 28, top = 22, bottom = 48;
        const x = value => left + (value - domain.xMin) / (domain.xMax - domain.xMin) * (width - left - right);
        const y = value => height - bottom -
          (value - domain.yMin) / (domain.yMax - domain.yMin) * (height - top - bottom);
        const within = point => point.petrolMs >= domain.xMin && point.petrolMs <= domain.xMax &&
          (point.mapBar ?? point.petrolMapBar) >= domain.yMin &&
          (point.mapBar ?? point.petrolMapBar) <= domain.yMax;
        const ordered = list => list.filter(within).slice().sort((a, b) => a.petrolMs - b.petrolMs);
        const lineFor = (list, valueKey) => ordered(list).map((point, index) =>
          (index ? 'L' : 'M') + ' ' + x(point.petrolMs).toFixed(1) + ' ' +
          y(point[valueKey]).toFixed(1)).join(' ');
        const acquisition = (list, fuel) => {
          const items = ordered(list);
          const line = items.length >= 2
            ? '<path class="autocal-epoch-acquisition-line ' + fuel + '" d="' +
              lineFor(items, 'mapBar') + '"></path>' : '';
          const dots = items.map(point =>
            '<circle class="autocal-acquired-point ' + fuel + ' ' +
            (point.acquisitionState === 'ACQUIRED' ? 'acquired' : 'collecting') +
            '" cx="' + x(point.petrolMs).toFixed(1) +
            '" cy="' + y(point.mapBar).toFixed(1) + '" r="5.5"></circle>').join('');
          return line + dots;
        };
        const reference = petrolCurve.length >= 2
          ? '<path class="autocal-reference-line petrol epoch-anchor" d="' +
            lineFor(petrolCurve, 'petrolMapBar') + '"></path>' : '';
        const oldGas = previousGas.filter(within).map(point =>
          '<circle class="autocal-previous-gas-point" cx="' + x(point.petrolMs).toFixed(1) +
          '" cy="' + y(point.mapBar).toFixed(1) + '" r="4.5"></circle>').join('');

        const live = AutoCalUxModel.livePoint(this.store.get().telemetry || {});
        const inDomain = live && live.petrolMs >= domain.xMin && live.petrolMs <= domain.xMax &&
          live.mapBar >= domain.yMin && live.mapBar <= domain.yMax;
        const liveMarkup = inDomain
          ? '<circle class="autocal-live-point" cx="' + x(live.petrolMs).toFixed(1) +
            '" cy="' + y(live.mapBar).toFixed(1) + '" r="6"></circle>' +
            '<text class="autocal-live-label" x="' + (x(live.petrolMs) + 10).toFixed(1) +
            '" y="' + (y(live.mapBar) - 8).toFixed(1) + '">AGORA</text>' : '';

        chart = '<svg class="autocal-reference-svg" viewBox="0 0 1000 400" role="img" ' +
          'aria-label="Aquisição atual da ECU; referência gasolina independente; sem equivalência durante reset">' +
          '<title>AQUISIÇÃO EM TEMPO REAL · gasolina e GNV por época nativa</title>' +
          '<path d="M64 22 V352 H972" fill="none" stroke="currentColor" opacity=".2"></path>' +
          reference + oldGas +
          acquisition(acquiredPetrol, 'petrol') + acquisition(acquiredGas, 'gas') + liveMarkup +
          '<text class="autocal-axis-title x" x="518" y="395" text-anchor="middle">' +
          AUTO_CAL_X_AXIS_LABEL + '</text>' +
          '<text class="autocal-axis-title y" x="14" y="200" text-anchor="middle" ' +
          'transform="rotate(-90 14 200)">MAP (bar)</text></svg>';
      }
      host.innerHTML = chart || '<div class="chart-empty"><b>AQUISIÇÃO EM TEMPO REAL</b><span>' +
        escapeHtml(stage) + '. Aguardando pontos da ECU. Nenhuma equivalência é calculada agora.</span></div>';
      this.renderLiveNarrative();
    }

    renderReferenceChart(snapshot) {
      const host = document.getElementById('autocalReferenceChart');
      if (!host) return;
      const points = AutoCalUxModel.referencePoints(snapshot, this.analysis || {});
      const acquiredPetrol = AutoCalUxModel.acquiredPoints(snapshot, 'petrol');
      const acquiredGas = AutoCalUxModel.acquiredPoints(snapshot, 'gas');
      const acquiredPoints = [...acquiredPetrol, ...acquiredGas];
      const live = AutoCalUxModel.livePoint(this.store.get().telemetry || {});
      this.currentReferencePoints = points;
      this.currentAcquiredPoints = acquiredPoints;
      const timingKnown = this.projection?.referenceTimingKnown === true;
      const timingCoherent = this.projection?.referenceTimingCoherent === true;
      const timingSpanMs = finite(this.projection?.referenceTimingSpanMs);
      const timingLimitMs = finite(this.projection?.referenceTimingLimitMs);
      const timingProblem = timingKnown && !timingCoherent;

      if (!points.length || this.referenceUsable === false) {
        const liveEpoch = this.projection?.liveAcquisitionEpoch || {};
        if (liveEpoch.comparisonAllowed === false) {
          this.renderAcquisitionEpochChart(acquiredPetrol, acquiredGas, liveEpoch, host);
          return;
        }
        this.chartRenderKey = null;
        this.chartSignature = null;
        ns.CurveChart?.release(host);
        this.renderResetComparison([]);
        this.chartScale = null;
        if (timingProblem) {
          const spanLabel = timingSpanMs === null ? 'intervalo desconhecido' : Math.round(timingSpanMs) + ' ms';
          const limitLabel = timingLimitMs === null ? 'limite nativo' : 'limite ' + Math.round(timingLimitMs) + ' ms';
          this.text('autocalReferenceCount', D.plural(points.length, 'ponto', 'pontos') + ' · fora da janela');
          host.innerHTML = '<div class="chart-empty"><b>REFERÊNCIA FORA DA JANELA</b><span>Os vetores físicos foram lidos com ' + spanLabel + ' de diferença; ' + limitLabel + '. Aguarde a próxima atualização automática da ECU. O AGORA continua vivo sem virar referência.</span></div>';
          this.readout( 'Referência física temporalmente incoerente. Aguarde a próxima atualização automática da ECU; o cursor AGORA continua somente como telemetria.');
        } else {
          this.text('autocalReferenceCount', '0 pontos utilizáveis');
          host.innerHTML = '<div class="chart-empty"><b>SEM REFERÊNCIA</b><span>A ECU ainda não publicou uma referência física utilizável. O AGORA continua nos valores ao lado, sem inventar escala.</span></div>';
          this.readout( live
            ? 'AGORA: ' + D.msUnit(live.petrolMs) + ' · ' + D.barUnit(live.mapBar) + '. Referência da ECU indisponível.'
            : 'Aguardando Injeção e MAP nativos.');
        }
        this.renderLiveNarrative();
        return;
      }

      this.text('autocalReferenceCount', D.plural(points.length, 'ponto lido', 'pontos lidos'));

      const chart = ns.CurveChart;
      const history = this.chartHistoryVisible ? this.previousReferencePoints : [];
      // Armazém único da evidência: busca eq/análise só quando a tabela da ECU muda ou no vigia de 5 s.
      chart.updateEvidence(this.api, this.projection, Date.now(), false);
      const width = Math.round(host.clientWidth) || 1000;
      const height = Math.round(host.clientHeight) || 400;
      const store = chart.evidence;
      const signature = chart.evidenceSignature({
        snapshot, eq: store.eq, analysis: store.analysis, sessionId: this.projection?.sessionId, history,
        extra: `ecu18|${Math.round(width / 16)}x${Math.round(height / 16)}|${chart.viewKey(this.chartView)}`,
      });
      const alreadyShown = this.renderedChartHost === host && this.chartSignature === signature && host.contains?.(chart.shared.node) !== false;
      if (alreadyShown) {
        this.renderLiveCursor();
        return;
      }
      const input = { snapshot, projection: this.projection, eq: store.eq, analysis: store.analysis, history, mode: 'ecu18', view: this.chartView };
      chart.mount(host, signature, () => {
        const model = chart.buildModel(input);
        if (!model || !model.domain) {
          return { html: '<div class="chart-empty"><b>SEM REFERÊNCIA</b><span>Os vetores recebidos não formam um domínio físico válido.</span></div>', scale: null, model: null };
        }
        return { ...chart.buildSvg(model, { width, height }), model };
      }, 'ecu18');
      const shown = chart.shared;
      const model = shown.model;
      this.chartScale = shown.scale;
      if (!model || !shown.scale) {
        this.renderedChartHost = null;
        this.chartSignature = null;
        return;
      }
      this.currentReferencePoints = model.reference;
      this.currentAcquiredPoints = model.ecu;
      const legend = document.getElementById('autocalLegend');
      const legendKey = `ecu18|${history.length > 0}`;
      if (legend && this.legendKey !== legendKey) {
        this.legendKey = legendKey;
        legend.innerHTML = chart.legendHtml({ mode: 'ecu18' }) + (history.length ? '<span class="previous" data-legend="previous">Leitura anterior</span>' : '');
      }
      chart.applySelection({ ref: this.selectedReferenceIndex, ecu: this.selectedAcquiredPoint, batch: this.selectedAcquiredPoints });
      if (this.selectedAcquiredPoint) {
        const [fuel, rawIndex] = this.selectedAcquiredPoint.split(':');
        this.inspectAcquiredPoint(fuel, Number(rawIndex));
      } else if (Number.isInteger(this.selectedReferenceIndex)) {
        this.inspectReferencePoint(this.selectedReferenceIndex);
      } else this.readout('');
      this.renderResetComparison(model.reference);
      this.renderLiveCursor();
      this.renderedChartHost = host;
      this.chartSignature = signature;
    }

    renderHistoryControl() {
      const history = this.panel?.querySelector('[data-autocal-history]');
      if (!history) return;
      history.disabled = this.previousReferencePoints.length === 0 || !this.referenceUsable;
      history.hidden = history.disabled;
      history.textContent = this.chartHistoryVisible ? 'Ocultar anterior' : 'Leitura anterior';
      history.setAttribute('aria-pressed', String(this.chartHistoryVisible));
      history.setAttribute('aria-label', this.chartHistoryVisible ? 'Ocultar leitura anterior' : 'Mostrar leitura anterior');
    }

    renderResetComparison(points) {
      const host = document.getElementById('autocalResetComparison');
      if (!host) return;
      const comparison = AutoCalUxModel.referenceComparison(this.previousReferencePoints, points);
      if (!comparison.available) {
        host.hidden = true;
        host.innerHTML = '';
        return;
      }
      const formatDelta = value => value === null ? '—' : (value > 0 ? '+' : '') + D.barUnit(value);
      host.hidden = false;
      host.innerHTML = '<div><small>ANTES × DEPOIS</small><b>' + D.plural(comparison.count, 'ponto', 'pontos') + '</b><span>Comparando a referência anterior com a nova leitura.</span></div>' +
        '<div><small>GASOLINA</small><b>' + formatDelta(comparison.mapDeltaAvgBar) + '</b><span>variação média de MAP</span></div>' +
        '<div><small>GNV</small><b>' + formatDelta(comparison.gasDeltaAvgBar) + '</b><span>variação média de MAP</span></div>';
    }

    /** Uma linha curta e humana ao lado do gráfico. Sem card que rola, sem código; só com dados coerentes. */
    readout(text, actionHtml) {
      const host = document.getElementById('autocalChartInspector');
      if (!host) return;
      const line = String(text || '').trim();
      const html = line
        ? '<span>' + escapeHtml(line) + '</span>' + (actionHtml || '')
        : '<span>Toque num ponto da curva.</span>';
      const key = line + '|' + (actionHtml || '');
      const data = host.dataset || (host.dataset = {});
      if (data.key === key) return;
      data.key = key;
      data.empty = line ? 'false' : 'true';
      host.innerHTML = html;
    }

    inspectAcquiredPoint(fuel, index) {
      const point = this.currentAcquiredPoints.find(item =>
        String(item.fuel) === String(fuel) && Number(item.index) === Number(index));
      const coherent = point && finite(point.petrolMs) !== null && finite(point.mapBar) !== null && finite(point.counter) !== null;
      if (!coherent) {
        this.selectedAcquiredPoint = null;
        this.readout('');
        return;
      }
      this.selectedAcquiredPoint = point.fuel + ':' + point.index;
      this.selectedReferenceIndex = null;
      const amostras = Math.round(point.counter);
      const line = [point.fuelLabel, 'ponto ' + point.point, D.msUnit(point.petrolMs), D.barUnit(point.mapBar), 'Z' + point.zone,
        amostras + (amostras === 1 ? ' amostra' : ' amostras'), point.acquisitionState === 'ACQUIRED' ? '' : 'ainda lendo'].filter(Boolean).join(' · ');
      this.readout(line, '<button type="button" class="btn-secondary btn-compact" data-autocal-reacquire-point data-autocal-reacquire-fuel="' + point.fuel +
        '" data-autocal-reacquire-index="' + point.index + '">Ler de novo</button>');
      document.querySelectorAll('#autocalReferenceChart [data-autocal-acquired-index]').forEach(node => {
        const nodeKey = String(node.dataset.autocalAcquiredFuel) + ':' + String(node.dataset.autocalAcquiredIndex);
        node.classList.toggle('selected', nodeKey === this.selectedAcquiredPoint);
        node.classList.toggle('batch-selected', this.selectedAcquiredPoints.has(nodeKey));
      });
      document.querySelectorAll('#autocalReferenceChart [data-autocal-ref-index]').forEach(node => node.classList.remove('selected'));
    }

    requestPointReacquisition(fuel, index) {
      if (!this.api?.available?.() || !Number.isInteger(index)) return;
      const prepared = this.api.preparePointDelete?.(fuel, index) || { ok: false, error: 'Readquisição pontual indisponível.' };
      if (!prepared?.ok || !prepared?.prepared) {
        this.store.patch({ alert: { level: 'warning', message: prepared?.error || 'Não foi possível preparar este ponto.' } });
        return;
      }
      const result = this.api.execute(prepared.preparationId);
      if (result?.ok !== true) {
        this.api?.cancelPreparation?.();
        this.store.patch({ alert: { level: 'warning', message: result?.error || 'Não foi possível abrir a confirmação do ponto.' } });
        return;
      }
      this.store.patch({ alert: { level: 'working', message: 'Leitura nova enviada para a ECU. Aguarde a conferência deste ponto.' } });
      this.refresh();
    }
 
    toggleAcquiredPointSelection(fuel, index) {
      if (!Number.isInteger(index)) return;
      const key = String(fuel) + ':' + String(index);
      if (this.selectedAcquiredPoints.has(key)) this.selectedAcquiredPoints.delete(key);
      else this.selectedAcquiredPoints.add(key);
      this.inspectAcquiredPoint(fuel, index);
      this.renderReferenceChart(this.snapshot);
    }

    clearAcquiredPointSelection() {
      this.selectedAcquiredPoints.clear();
      const current = this.selectedAcquiredPoint?.split(':');
      if (current?.length === 2) this.inspectAcquiredPoint(current[0], Number(current[1]));
      this.renderReferenceChart(this.snapshot);
    }

    requestSelectedPointReacquisition() {
      if (!this.api?.available?.() || this.selectedAcquiredPoints.size === 0) return;
      const targets = Array.from(this.selectedAcquiredPoints).map(key => {
        const [fuel, rawIndex] = key.split(':');
        return { fuel, index: Number(rawIndex) };
      }).filter(item => Number.isInteger(item.index));
      const prepared = this.api.preparePointDeleteBatch?.(targets) || { ok: false, error: 'Readquisição múltipla indisponível.' };
      if (!prepared?.ok || !prepared?.prepared) {
        this.store.patch({ alert: { level: 'warning', message: prepared?.error || 'Não foi possível preparar os pontos selecionados.' } });
        return;
      }
      const result = this.api.execute(prepared.preparationId);
      if (result?.ok !== true) {
        this.api?.cancelPreparation?.();
        this.store.patch({ alert: { level: 'warning', message: result?.error || 'Não foi possível iniciar a readquisição selecionada.' } });
        return;
      }
      const count = targets.length;
      this.pendingPointReacquisitionKeys = new Set(targets.map(item => item.fuel + ':' + item.index));
      this.store.patch({
        alert: {
          level: 'working',
          message: D.plural(count, 'ponto', 'pontos') +
            ' sendo lidos de novo. A seleção só é limpa depois que a ECU confirmar.',
        },
      });
      this.refresh();
    }

    inspectReferencePoint(index) {
      const point = this.currentReferencePoints.find(item => Number(item.index) === Number(index));
      const coherent = point && finite(point.petrolMs) !== null && finite(point.petrolMapBar) !== null && finite(point.gasMapBar) !== null;
      if (!coherent) { this.selectedReferenceIndex = null; this.readout(''); return; }
      this.selectedReferenceIndex = point.index;
      this.selectedAcquiredPoint = null;
      this.readout('Curva · ponto ' + (point.index + 1) + ' · ' + D.msUnit(point.petrolMs) + ' · gasolina ' + D.bar(point.petrolMapBar) + ' · GNV ' + D.barUnit(point.gasMapBar));
      ns.CurveChart?.applySelection({ ref: point.index, ecu: null, batch: this.selectedAcquiredPoints });
      document.querySelectorAll('#autocalReferenceChart [data-autocal-acquired-index]').forEach(node => node.classList.remove('selected'));
    }

    renderBands(snapshot) {
      const host = document.getElementById('autocalBands');
      if (!host) return;
      // Evidência: só redesenha quando a tabela da ECU mudou (D2), não a cada leitura do relógio.
      const bandsKey = (ns.CurveChart ? ns.CurveChart.tableSignature(snapshot) : '') + '|' + this.selectedBandIndex + '|' + host.childElementCount;
      if (this.bandsKey === bandsKey) return;
      this.bandsKey = bandsKey;
      const bands = AutoCalUxModel.bandStrip(snapshot, this.projection);
      host.innerHTML = bands.map(band => {
        const stateLabel = band.state === 'anchored' ? 'correlacionada'
          : band.state === 'mature' ? 'evento'
          : band.state === 'activity' ? 'atividade' : 'vazia';
        return '<button type="button" class="autocal-band-segment" data-autocal-band-index="' + band.index +
          '" data-state="' + band.state + '" data-zone-acquired="' + (band.zoneAcquired ? 'true' : 'false') +
          '" role="listitem" aria-pressed="false" aria-label="Região ' + (band.index + 1) + ' de 18, ' + stateLabel +
          '"><span>' + (band.index + 1) + '</span><i></i><small>' + (band.zoneAcquired ? 'zona ok' : stateLabel) + '</small></button>';
      }).join('');
      const preferred = Number.isInteger(this.selectedBandIndex)
        ? this.selectedBandIndex
        : (bands.find(item => item.state !== 'empty')?.index ?? 0);
      this.inspectBand(preferred);
    }

    inspectBand(index) {
      const band = AutoCalUxModel.bandStrip(this.snapshot || {}, this.projection).find(item => item.index === index);
      const host = document.getElementById('autocalBandInspector');
      if (!band || !host) return;
      this.selectedBandIndex = index;
      document.querySelectorAll('[data-autocal-band-index]').forEach(node => {
        const selected = Number(node.dataset.autocalBandIndex) === index;
        node.classList.toggle('selected', selected);
        node.setAttribute('aria-pressed', selected ? 'true' : 'false');
      });

      const message = AutoCalUxModel.bandNarrative(band);

      const zoneText = band.zoneAcquired
        ? 'Zona ' + (band.zone + 1) + ' confirmada pela ECU'
        : 'Zona ' + (band.zone + 1) + ' ainda não confirmada pela ECU';
      host.innerHTML = '<b>Região ' + (index + 1) + ' de 18 · ' + zoneText + '</b><span>' + message + '</span>';
    }

    renderEvents(events) {
      const host = document.getElementById('autocalEvents');
      if (!host) return;
      const last = events.length ? events[events.length - 1] : {};
      const eventsKey = [events.length, last.counter, last.bandIndex, last.correlationState, last.rpm].join('|');
      if (this.eventsKey === eventsKey) return;
      this.eventsKey = eventsKey;
      if (!events.length) {
        host.innerHTML = '<p class="empty-copy">Nenhum evento de maturidade foi gerado nesta leitura. Isso não apaga o que a ECU já acumulou.</p>';
        return;
      }
      host.innerHTML = events.slice(-6).reverse().map(event => {
        const correlated = String(event.correlationState || '') === 'CORRELATED';
        const rpm = finite(event.rpm);
        const confidenceRaw = finite(event.correlationConfidence);
        const confidence = confidenceRaw === null ? '—' : Math.round(confidenceRaw * 100);
        const bandIndex = finite(event.bandIndex);
        return `<article data-state="${correlated ? 'correlated' : 'raw'}"><div><b>${bandIndex === null ? 'B—' : 'B' + (bandIndex + 1)}</b><span>${escapeHtml(event.zone || 'zona')}</span></div><p>${correlated ? `${rpm === null ? 'RPM —' : `${Math.round(rpm).toLocaleString('pt-BR')} RPM`} · confiança ${confidence}${confidence === '—' ? '' : '%'}` : escapeHtml(event.correlationReason || 'NO_RELIABLE_CORRELATION')}</p><small>contador ${finite(event.counter) ?? '—'} · limiar ${finite(event.threshold) ?? '—'}</small></article>`;
      }).join('');
    }

    renderActionState() {
      const host = document.getElementById('autocalActionStatus');
      if (!host) return;
      const state = this.actionState || {};
      const name = String(state.state || 'IDLE').toUpperCase();
      const message = String(state.message || 'Nenhuma ação preparada.');
      const working = [
        'PREPARED', 'QUEUED', 'READING_BEFORE', 'SENDING_ACTION', 'READING_AFTER',
        'READING_FINISH_SOURCE', 'SENDING_FINISH_COMMIT', 'VERIFYING_FINISH',
        'RESETTING_K', 'VERIFYING_K_RESET',
      ].includes(name);
      const failed = name === 'FAILED';
      const recovery = state?.recovery && typeof state.recovery === 'object' ? state.recovery : null;
      const recoveryNext = String(recovery?.nextAction || '').trim();
      const recoveryCode = String(recovery?.reasonCode || state?.reasonCode || '');
      const mutationMayHaveStarted = state?.mutationMayHaveStarted === true;
      const uncertainty = failed && mutationMayHaveStarted
        ? ' · Estado incerto: a ECU pode ter mudado. Releia antes de repetir.'
        : '';
      host.dataset.level = failed ? 'error' : name === 'CONFIRMED' ? 'ok' : working ? 'working' : 'neutral';
      host.dataset.reasonCode = recoveryCode;
      host.dataset.mutationUncertain = mutationMayHaveStarted ? 'true' : 'false';
      host.textContent = name === 'IDLE' ? message
        : name === 'CONFIRMED' ? 'Concluído · ' + message
        : failed ? 'Não concluído · ' + message + uncertainty + (recoveryNext ? ' · Próximo: ' + recoveryNext : '')
        : 'Executando · ' + message;
    }

    renderReview() {
      const review = document.getElementById('autocalReview');
      const prepared = this.prepared;
      if (!review || !prepared) return;
      review.hidden = false;
      review.innerHTML = `<div class="autocal-review-card"><header><div><small>REVISÃO ANTES DA ECU</small><h3>${escapeHtml(prepared.label || actionLabel(prepared.action))}</h3></div><button type="button" data-autocal-cancel class="icon-close" aria-label="Fechar revisão">×</button></header><p>${escapeHtml(prepared.description || '')}</p><div class="write-contract"><b>Nada foi enviado à ECU.</b><span>Confirmar executa agora pelo OMEGAS e confere na ECU. Salvar uma foto antes é opcional: só se você quiser.</span></div><details class="autocal-review-tech"><summary>Detalhes técnicos</summary><dl><div><dt>Ação</dt><dd>${escapeHtml(actionLabel(prepared.action))}</dd></div><div><dt>Comando</dt><dd>${escapeHtml(prepared.commandHex || '—')}</dd></div><div><dt>Sessão</dt><dd>${escapeHtml(prepared.sessionId || '—')}</dd></div><div><dt>Verificação pós-ação</dt><dd>ACK + leitura posterior da ECU</dd></div></dl></details><div class="operation-actions"><button type="button" data-autocal-cancel class="secondary">Cancelar</button><button type="button" data-autocal-confirm class="danger-primary">Executar agora</button></div></div>`;
    }

    renderUnavailable() {
      this.chartRenderKey = null;
      this.text('autocalNativeState', 'AUTOCAL INDISPONÍVEL');
      this.text('autocalHumanTitle', 'AutoCal indisponível');
      this.text('autocalHumanProgress', 'A tela não recebeu o AutoCal da ECU.');
      this.text('autocalHumanAction', 'AutoCal indisponível. Reconecte a ECU e tente de novo.');
      const sentence = document.getElementById('autocalHumanAction');
      if (sentence) sentence.dataset.level = 'error';
      this.text('autocalZoneSummary', '—');
      this.text('autocalReferenceCount', '—');
      this.text('autocalMaturityRaw', '—');
      const host = document.getElementById('autocalReferenceChart');
      if (host) host.innerHTML = '<div class="chart-empty"><b>Sem ligação com a ECU</b><span>Nenhum dado foi inventado para preencher o gráfico.</span></div>';
    }

    text(id, value) {
      const node = document.getElementById(id);
      if (node && node.textContent !== String(value ?? '—')) node.textContent = String(value ?? '—');
    }
  }

  function boot() {
    const app = root.OmegasApp;
    if (!app?.store || !app?.scheduler || !ns.AutoCalApi) {
      if (typeof root.addEventListener === 'function') root.addEventListener('omegas-app-ready', boot, { once: true });
      return;
    }
    if (app.autoCalCockpit) return;
    app.autoCalCockpit = new AutoCalCockpit(app);
  }

  ns.AutoCalUxModel = AutoCalUxModel;
  ns.AutoCalCockpit = AutoCalCockpit;
  boot();
})(typeof window !== 'undefined' ? window : globalThis);