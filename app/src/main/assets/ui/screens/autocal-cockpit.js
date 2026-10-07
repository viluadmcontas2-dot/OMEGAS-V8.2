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
    // The bridge uses [] for unknown, not for four missing zones.
    if (!Array.isArray(zones) || zones.length !== 4 || !zones.every(value => typeof value === 'boolean')) return null;
    return zones.slice();
  }

  function scalarValue(snapshot, key) {
    const item = field(snapshot, key);
    const values = Array.isArray(item?.rawValues) ? item.rawValues : [];
    return values.length === 1 ? finite(values[0]) : null;
  }

  const AutoCalUxModel = {
    epochNarrative(epoch = {}) {
      const petrol = epoch.petrolPending === true || epoch.petrolReferencePending === true;
      const gas = epoch.gasPending === true || epoch.gasReferencePending === true;
      if (petrol && gas) return 'Aguardando curvas atuais de gasolina e GNV da ECU';
      if (petrol) return 'Referência de gasolina pendente: aguardando a curva atual da ECU';
      if (gas) return 'Referência de GNV pendente: aguardando a curva atual da ECU';
      if (epoch.referencePending) return 'Aguardando curvas atuais da ECU para comparar';
      return 'Aguardando dados atuais da ECU para comparar';
    },
    humanState(snapshot = {}, state = {}, projection = {}) {
      const nativeSnapshot = state.latestSnapshot?.fields ? state.latestSnapshot : {};
      const evidenceSnapshot = nativeSnapshot.fields ? nativeSnapshot : snapshot;
      // Pausar/Iniciar só com o flag que a ECU entregou: em falha de leitura ou sem cabo o estado é desconhecido, nunca o cache.
      const stateName = String(state.state || '').toUpperCase();
      const unreadable = ['PROBE_FAILED', 'FAILED', 'UNAVAILABLE', 'DISCONNECTED', 'IDLE'].includes(stateName);
      const enabled = finite(state.autoCalEnabled ?? nativeSnapshot.autoCalEnabled ?? scalarValue(nativeSnapshot, 'AUTO_CAL_ENABLE'));
      const enabledLive = unreadable ? null : enabled;
      const projectedPetrolZones = projectedZoneFlags(projection, 'petrol');
      const projectedGasZones = projectedZoneFlags(projection, 'gas');
      const petrolFieldAvailable = field(evidenceSnapshot, 'ACQUIRED_ZONES_PETROL') !== null;
      const gasFieldAvailable = field(evidenceSnapshot, 'ACQUIRED_ZONES_GAS') !== null;
      const hasProjectedZones = projection.ok === true && projection.acquisitionZones;
      const petrolZoneFlags = hasProjectedZones ? projectedPetrolZones
        : (petrolFieldAvailable ? nativeZoneFlags(evidenceSnapshot, 'ACQUIRED_ZONES_PETROL') : null);
      const gasZoneFlags = hasProjectedZones ? projectedGasZones
        : (gasFieldAvailable ? nativeZoneFlags(evidenceSnapshot, 'ACQUIRED_ZONES_GAS') : null);
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
            : enabledLive === 1 && autoMatchQuotaReached
              ? 'AutoCal ativo · AutoMatch ' + Math.round(autoMatchCount) + '/' + Math.round(maxAutoMatch)
              : enabledLive === 1 ? 'AutoCal adquirindo'
              : enabledLive === 0 ? 'AutoCal pausado'
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
      } else if (enabledLive === 0) nextAction = 'Inicie a leitura quando quiser continuar.';
      else if (enabledLive === 1 && autoMatchQuotaReached) nextAction = 'A cota automática de AutoMatch foi atingida. A leitura continua ativa e pode preencher novas zonas; pause só se quiser interromper.';
      else if (enabledLive === 1 && gasMissingZones.length) nextAction = 'Leitura ativa. Faltam no GNV: ' + gasMissingZones.map(zone => 'Z' + zone).join(', ') + '. Use a faixa AGORA para buscar essas zonas sem resetar dados.';
      else if (enabledLive === 1 && gasZones === 4) nextAction = 'As 4 zonas GNV já foram marcadas pela ECU. Continue acompanhando: o próximo AutoMatch é decisão da ECU.';
      else if (enabledLive === 1) nextAction = 'Leitura ativa; aguardando a ECU publicar as quatro zonas.';
      return {
        title, progress, autoMatch, nextAction,
        autoMatchEvidenceState: evidenceState || 'WAITING',
        autoMatchEvidenceTitle: evidenceTitle,
        autoMatchEvidenceDetail: evidenceDetail,
        petrolZones, gasZones, petrolMissingZones, gasMissingZones,
        petrolZoneFlags, gasZoneFlags, enabled: enabledLive, autoMatchCount, maxAutoMatch, autoMatchQuotaReached,
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
      if (actionState?.automatic === true) {
        // A limpeza automática só começa com o escritor livre: a ação do dono já terminou e o estado dela foi
        // sobrescrito. Sem saber o desfecho, a seleção sai sem marcar nada como apagado (o nativo recusa ponto vazio).
        return { clear: true, preserve: false, restore: [], pending: [], reason: 'SUPERSEDED_BY_AUTOMATIC' };
      }
      if (state === 'CONFIRMED') {
        return { clear: true, preserve: false, restore: [], pending: [], reason: 'CONFIRMED' };
      }
      if (state === 'FAILED' && actionState?.mutationMayHaveStarted === true) {
        // A ECU pode ter apagado parte dos pontos: a seleção antiga não vale mais (evita apagar ponto já zerado).
        return { clear: true, preserve: false, restore: [], pending: [], reason: 'FAILED_UNCERTAIN' };
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

    /** "o ponto 5" · "os pontos 3 e 5" (índices da ECU, 0..17 → pontos 1..18). */
    pointsPhrase(indexes = []) {
      const points = [...new Set((Array.isArray(indexes) ? indexes : []).map(Number).filter(Number.isInteger))]
        .sort((a, b) => a - b).map(index => index + 1);
      if (!points.length) return null;
      if (points.length === 1) return 'o ponto ' + points[0];
      return 'os pontos ' + points.slice(0, -1).join(', ') + ' e ' + points[points.length - 1];
    },

    /** Aviso do apagamento automático: o app (não o dono) pediu, e por quê, em palavras de leigo. */
    autoDeleteSentence(indexes = [], fuel = 'GAS') {
      const phrase = AutoCalUxModel.pointsPhrase(indexes);
      const of = AutoCalUxModel.autoFuelOf(fuel);
      if (!phrase) return 'O app pediu para a ECU reaprender um ponto ' + of + ' — ele estava fora da curva.';
      const many = phrase.startsWith('os ');
      return 'O app pediu para a ECU reaprender ' + phrase + ' ' + of + ' — ' +
        (many ? 'eles estavam' : 'ele estava') + ' fora da curva.';
    },

    /** "do GNV" / "da gasolina" para o combustível do apagamento automático (GAS ou PETROL). */
    autoFuelOf(fuel) {
      return String(fuel || '').toUpperCase() === 'PETROL' ? 'da gasolina' : 'do GNV';
    },

    /** Combustível (GAS/PETROL) de um estado da ação automática; GNV quando o estado não diz. */
    autoActionFuel(actionState = {}) {
      const details = actionState?.details && typeof actionState.details === 'object' ? actionState.details : {};
      const firstFuel = list => Array.isArray(list) && list.length ? list[0]?.fuel : null;
      const found = [
        details.fuel,
        details.details?.fuel,
        firstFuel(details.targets),
        firstFuel(details.details?.targets),
        details.pointDelete?.fuel,
        firstFuel(details.pointDelete?.targets),
      ].map(value => String(value || '').toUpperCase()).find(value => value === 'GAS' || value === 'PETROL');
      return found || 'GAS';
    },

    /** Índices de um estado da ação automática (evidência antes do envio, alvos durante, recibo no fim). */
    autoActionIndexes(actionState = {}) {
      const details = actionState?.details && typeof actionState.details === 'object' ? actionState.details : {};
      const fromTargets = list => Array.isArray(list) ? list.map(t => Number(t?.index)).filter(Number.isInteger) : [];
      const candidates = [
        fromTargets(details.targets),
        fromTargets(details.details?.targets),
        Array.isArray(details.bands) ? details.bands.map(b => Number(b?.band)).filter(Number.isInteger) : [],
        fromTargets(details.pointDelete?.targets),
        Number.isInteger(details.pointDelete?.index) ? [details.pointDelete.index] : [],
        Number.isInteger(details.index) ? [details.index] : [],
      ];
      return candidates.find(list => list.length) || [];
    },

    /** Recibo do apagamento automático no estado CONFIRMED da ação: só bandas cujo readback provou o apagamento. */
    automaticDeleteReceipt(actionState = {}) {
      if (actionState?.automatic !== true || String(actionState?.state || '').toUpperCase() !== 'CONFIRMED') return null;
      if (String(actionState?.action || '') !== 'DELETE_POINT') return null;
      const receipt = actionState.details && typeof actionState.details === 'object' ? actionState.details : {};
      const id = String(receipt.id || '');
      if (!id) return null;
      const effect = Array.isArray(receipt.details?.effect) ? receipt.details.effect : [];
      const ambiguous = new Set(effect.filter(row => row?.result === 'AMBIGUOUS').map(row => Number(row.index)));
      const indexes = AutoCalUxModel.autoActionIndexes(actionState).filter(index => !ambiguous.has(index));
      return { receiptId: id, fuel: AutoCalUxModel.autoActionFuel(actionState), indexes, atMs: finite(receipt.finishedAtMs) };
    },

    /** Motivo da pausa da limpeza automática em palavras simples (o código vem do app). */
    autoCleanupPauseReason(code) {
      switch (String(code || '')) {
        case 'OTHER_FUEL_GUARD':
        case 'PETROL_GUARD': return 'o outro combustível mudou de um jeito estranho durante a limpeza';
        case 'REPEATED_FAILURES': return 'a ECU não respondeu bem várias vezes seguidas';
        case 'READBACK_INEFFECTIVE': return 'a ECU não apagou o ponto quando o app pediu';
        default: return 'o app encontrou algo inesperado';
      }
    },

    /** Linha discreta: "Limpeza automática: ligada · N pontos reaprendidos nesta sessão" ou pausada com o que fazer. */
    autoCleanupLine(status = {}) {
      if (status?.ok !== true || status.active !== true) return { hidden: true, text: '', level: 'neutral' };
      if (status.enabled !== true) {
        return {
          hidden: false,
          level: 'warn',
          text: 'Limpeza automática pausada nesta conexão: ' + AutoCalUxModel.autoCleanupPauseReason(status.pauseCode) +
            '. Reconecte o cabo para tentar de novo.',
        };
      }
      const count = Math.max(0, Math.round(finite(status.relearnedThisSession) ?? 0));
      const tally = count === 0 ? 'nenhum ponto reaprendido'
        : count === 1 ? '1 ponto reaprendido' : count + ' pontos reaprendidos';
      return { hidden: false, level: 'neutral', text: 'Limpeza automática: ligada · ' + tally + ' nesta sessão' };
    },

  };

  /** Apagamento automático mais velho que isto (a tela estava fechada) já foi coberto por leituras novas. */
  const AUTO_DELETE_FRESH_MS = 60000;

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
      this.chartScale = null;
      this.cursor = new ns.LiveStore.EaseCursor(() => this.chartPart('.autocal-live-layer'));
      this.previousReferencePoints = [];
      this.comparisonPinned = false;
      this.currentReferencePoints = [];
      this.currentAcquiredPoints = [];
      this.chartHistoryVisible = false;
      this.selectedReferenceIndex = null;
      this.selectedAcquiredPoint = null;
      this.selectedAcquiredPoints = new Set();
      this.pendingPointReacquisitionKeys = new Set();
      // Pontos que a ECU confirmou ter apagado: chave fuel:index → hora da confirmação. Ficam cinza e intocáveis
      // até chegar uma leitura da ECU mais nova que a confirmação (a leitura antiga ainda os mostra).
      this.recentlyDeleted = new Map();
      // Limpeza automática do GNV: último estado e os recibos já absorvidos (cada um acinzenta e avisa uma vez).
      this.autoCleanup = {};
      this.seenAutoDeletes = new Set();
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
              <div class="autocal-title-row"><h2 class="instrument-title">Aprendizado da ECU</h2><small id="autocalAutoCleanLine" class="autocal-autoclean-line" data-level="neutral" hidden></small><small id="autocalRelearnNote" class="autocal-relearn-note" hidden>A ECU reaprendeu desde a última referência.</small></div><p id="autocalHumanAction" class="ar-sentence" data-level="neutral">Lendo o estado da ECU…</p><p id="autocalActionStatus" class="ar-reason" data-level="neutral" hidden></p><span id="autocalLiveFuel" class="ar-fuel autocal-fuel-chip" data-fuel-state="unknown">—</span>
              <div class="ar-tile ar-load"><small>Carga do motor</small><b id="autocalLiveLoad">—</b></div>
              <div class="ar-tile"><small>RPM</small><b id="autocalLiveRpm">—</b></div>
              <div class="ar-tile ar-zone" title="Zona = quanto o motor está carregado: zona 1 = lenta … zona 4 = acelerando forte"><small>Zona de carga</small><b id="autocalLiveZone">—</b></div>
              <div class="ar-tile ar-automatch" id="autocalAutoMatchTile" data-state="unknown"><small>Ajustes automáticos da ECU</small><b id="autocalAutoMatchCount">—</b></div>
              <div class="autocal-reading-controls"><button type="button" class="ar-ghost" data-autocal-history hidden aria-label="Mostrar leitura anterior">Leitura anterior</button><button type="button" data-autocal-toggle class="btn-primary btn-compact" data-loading="true" disabled>Lendo estado…</button></div>
              <span id="autocalLiveTitle" hidden>Aguardando telemetria</span>
              <p id="autocalLiveNarrative" class="ar-sr" hidden></p>
              <span id="autocalNativeState" hidden>Leitura da ECU: aguardando</span>
            </header>

              <section class="autocal-zone-card autocal-zone-strip" aria-label="Cobertura das zonas">
                <div id="autocalZoneMeter" class="autocal-zone-meter" aria-label="Zonas AutoCal aguardando leitura">
                  <div class="petrol"><span class="autocal-zone-fuel">Gasolina</span><div class="autocal-zone-cells" role="list"><span class="autocal-zone-cell" data-autocal-zone-petrol="0" data-state="unknown" data-current="false" role="listitem"><b>Z1</b><small>—</small></span><span class="autocal-zone-cell" data-autocal-zone-petrol="1" data-state="unknown" data-current="false" role="listitem"><b>Z2</b><small>—</small></span><span class="autocal-zone-cell" data-autocal-zone-petrol="2" data-state="unknown" data-current="false" role="listitem"><b>Z3</b><small>—</small></span><span class="autocal-zone-cell" data-autocal-zone-petrol="3" data-state="unknown" data-current="false" role="listitem"><b>Z4</b><small>—</small></span></div></div>
                  <div class="gas"><span class="autocal-zone-fuel">GNV</span><div class="autocal-zone-cells" role="list"><span class="autocal-zone-cell" data-autocal-zone-gas="0" data-state="unknown" data-current="false" role="listitem"><b>Z1</b><small>—</small></span><span class="autocal-zone-cell" data-autocal-zone-gas="1" data-state="unknown" data-current="false" role="listitem"><b>Z2</b><small>—</small></span><span class="autocal-zone-cell" data-autocal-zone-gas="2" data-state="unknown" data-current="false" role="listitem"><b>Z3</b><small>—</small></span><span class="autocal-zone-cell" data-autocal-zone-gas="3" data-state="unknown" data-current="false" role="listitem"><b>Z4</b><small>—</small></span></div></div>
                </div>
                <small class="autocal-zone-legend">Zona 1 = lenta · 2 = rodando leve · 3 = acelerando · 4 = acelerando forte</small>
                <span id="autocalZoneSummary" class="ar-sr">—</span>
              </section>

            <section class="ar-chart-card" aria-label="Leitura da ECU · Gasolina × GNV">
              <div class="ar-legend-row">
                <div class="ar-legend" id="autocalLegend" aria-label="Legenda do gráfico"></div>
                <span id="autocalNoiseSummary" class="autocal-noise-summary" aria-live="polite"></span>
                <span id="autocalReferenceCount" class="ar-sr" hidden>—</span>
              </div>
              <div id="autocalReferenceChart" class="ar-chart-host"><div class="chart-empty">Aguardando as curvas da ECU.</div></div>
              <div class="ar-readout" id="autocalChartInspector" data-empty="true"><span>Toque num ponto da curva.</span></div>
            </section>

            <div class="ar-act">
              <div class="ar-buttons autocal-main-actions">
                <details class="instrument-details"><summary>Histórico e detalhes</summary><div class="ar-secondary autocal-secondary-stack" role="region" aria-label="Mais sobre o AutoCal">


              <section class="ar-card autocal-live-tech">
                <h4>Detalhes técnicos</h4>
                <span>MAP <span id="autocalLiveMap">—</span> bar · Injeção <span id="autocalLivePetrol">—</span> ms</span>
                <span id="autocalReferenceSource">—</span>
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
              </section>
            </div>

            </details>
                <details class="autocal-reset-menu ar-more">
                  <summary>Mais opções</summary>
                  <div class="autocal-reset-popover" id="autocalOptionsPanel" aria-label="Mais opções do AutoCal">
                    <button type="button" class="autocal-options-close" data-autocal-close-options>Fechar opções ×</button>
                    ${ns.CurveChart.viewControls()}
                    <section class="autocal-reset-group" data-reset-scope="relearn">
                      <p class="autocal-reacquire-note">Só se a ECU aprendeu errado: ela esquece o que aprendeu daquele combustível e aprende de novo enquanto você dirige.</p>
                      <button type="button" class="autocal-reacquire-action" data-autocal-action="RESET_GAS">Zerar aprendizado da ECU (GNV)</button>
                      <button type="button" class="autocal-reacquire-action" data-autocal-action="RESET_PETROL">Zerar aprendizado da ECU (gasolina)</button>
                      <div class="autocal-reset-confirm" id="autocalResetConfirm" role="alertdialog" aria-label="Confirmar zerar o aprendizado da ECU" hidden>
                        <p id="autocalResetConfirmText">Zerar o que a ECU aprendeu? Não dá para desfazer.</p>
                        <button type="button" class="btn-danger" data-autocal-reset-confirm>Zerar</button>
                        <button type="button" class="btn-ghost" data-autocal-reset-cancel>Cancelar</button>
                      </div>
                      <p>Para zerar a Curva K, use a aba Curva K.</p>
                    </section>
                  </div>
                </details>
              </div>
              <div class="ar-buttons autocal-point-actions" hidden role="group" aria-label="Pontos marcados para a ECU medir de novo">
                <button type="button" class="btn-primary" data-autocal-reacquire-selected>Reaprender 1 ponto</button>
                <button type="button" data-autocal-clear-point-selection>Cancelar</button>
              </div>
            </div>

          </section>`;
        stack.appendChild(panel);
        this.panel = panel;
      } else {
        this.panel = stack?.querySelector('.autocal-route-panel') || null;
      }
    }

    bind() {
      ns.CurveChart.bindView(this.panel, this, () => this.renderReferenceChart(this.snapshot || {}));
      const options = this.panel?.querySelector('.ar-more');
      const syncOptions = () => {
        const open = options?.hasAttribute('open') === true;
        const summary = options?.querySelector('summary');
        if (summary) {
          summary.setAttribute('aria-expanded', String(open));
          summary.setAttribute('aria-controls', 'autocalOptionsPanel');
          summary.textContent = open ? 'Fechar opções ▴' : 'Mais opções ▾';
        }
      };
      options?.addEventListener('toggle', syncOptions);
      this.panel?.querySelector('[data-autocal-close-options]')?.addEventListener('click', () => {
        options?.removeAttribute('open');
        syncOptions();
      });
      syncOptions();
      this.panel?.querySelector('[data-autocal-toggle]')?.addEventListener('click', event => {
        const action = event.currentTarget?.dataset?.action;
        if (action) this.runOperational(action);
      });
      this.panel?.querySelectorAll('[data-autocal-action]').forEach(button => {
        button.addEventListener('click', () => {
          // Zerar o aprendizado apaga na ECU: pede confirmação antes (Revisto (W2)).
          if (String(button.dataset.autocalAction).startsWith('RESET_')) { this.askResetConfirm(button.dataset.autocalAction); return; }
          button.closest('.autocal-reset-menu')?.removeAttribute('open');
          syncOptions();
          this.prepare(button.dataset.autocalAction);
        });
      });
      this.panel?.querySelector('[data-autocal-reset-confirm]')?.addEventListener('click', () => {
        const action = this.pendingResetAction;
        this.askResetConfirm(null);
        options?.removeAttribute('open');
        syncOptions();
        if (action) this.prepare(action);
      });
      this.panel?.querySelector('[data-autocal-reset-cancel]')?.addEventListener('click', () => this.askResetConfirm(null));
      this.panel?.querySelector('[data-autocal-history]')?.addEventListener('click', () => {
        if (!this.previousReferencePoints.length) return;
        this.chartHistoryVisible = !this.chartHistoryVisible;
        this.renderHistoryControl();
        this.renderReferenceChart(this.snapshot);
      });
      this.panel?.addEventListener('click', event => {
        const acquiredPoint = event.target.closest('[data-autocal-acquired-index]');
        if (acquiredPoint) {
          this.tapAcquiredPoint(
            acquiredPoint.dataset.autocalAcquiredFuel,
            Number(acquiredPoint.dataset.autocalAcquiredIndex),
          );
        }
        const point = event.target.closest('[data-autocal-ref-index]');
        if (point) this.inspectReferencePoint(Number(point.dataset.autocalRefIndex));
        if (event.target.closest('[data-autocal-reacquire-selected]')) this.requestSelectedPointReacquisition();
        if (event.target.closest('[data-autocal-clear-point-selection]')) this.clearAcquiredPointSelection();
      });
    }

    /** Entrar na aba é barato: nada de ponte aqui (o gráfico deste modo continua montado). A leitura vem em refreshNow(). */
    enter() {
      this.active = true;
      this.firstRefreshPending = false;
    }

    /** UMA releitura ao entrar (o app chama depois do primeiro quadro pintado). */
    refreshNow() {
      this.firstRefreshPending = false;
      this.dataDirty = false;
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
        this.autoCleanup = {};
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
      if (selectionTransition.reason === 'CONFIRMED') {
        // Marca = depois da confirmação E depois da leitura que estava na mão nesse instante (relógios podem diferir).
        const confirmedAt = Date.now();
        this.pendingPointReacquisitionKeys.forEach(key => {
          const seen = this.countersCapturedAt(nextSnapshot, key.split(':')[0]);
          this.deletedPoints().set(key, Math.max(confirmedAt, seen ?? -Infinity));
        });
      }
      if (selectionTransition.clear) this.selectedAcquiredPoints?.clear?.();
      if (selectionTransition.restore.length) {
        this.selectedAcquiredPoints = new Set(selectionTransition.restore);
      }
      this.pendingPointReacquisitionKeys = new Set(selectionTransition.pending);
      this.snapshot = nextSnapshot || {};
      this.autoCleanup = this.api.autoCleanup?.() || {};
      this.absorbAutomaticDeletes(this.autoCleanup, this.snapshot, nextActionState);
      this.expireRecentlyDeleted(this.snapshot);
      this.pruneSelection(this.snapshot);
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

    /** Mapa dos pontos recém-apagados (criado sob demanda). */
    deletedPoints() {
      if (!(this.recentlyDeleted instanceof Map)) this.recentlyDeleted = new Map();
      return this.recentlyDeleted;
    }

    /**
     * O app apagou sozinho pontos fora da curva (GNV ou gasolina): eles entram no MESMO cinza do apagamento do dono
     * (intocáveis até chegar leitura da ECU mais nova) e o aviso curto sai uma vez por recibo. Recibo velho
     * (tela fechada na hora) só é registrado: as leituras seguintes já mostram a ECU como ela está.
     */
    absorbAutomaticDeletes(status, snapshot, actionState = {}) {
      const items = status?.ok === true && Array.isArray(status.recentDeletes) ? status.recentDeletes.slice() : [];
      // O recibo também chega no estado da ação (CONFIRMED) e pode vir antes do resumo: as duas vias, um aviso só.
      const receipt = AutoCalUxModel.automaticDeleteReceipt(actionState);
      if (receipt) items.push(receipt);
      if (!(this.seenAutoDeletes instanceof Set)) this.seenAutoDeletes = new Set();
      const now = Date.now();
      const fresh = { GAS: [], PETROL: [] };
      items.forEach(item => {
        const id = String(item?.receiptId || '');
        if (!id || this.seenAutoDeletes.has(id)) return;
        this.seenAutoDeletes.add(id);
        const at = finite(item.atMs);
        const fuel = String(item?.fuel || 'GAS').toUpperCase() === 'PETROL' ? 'PETROL' : 'GAS';
        const indexes = Array.isArray(item.indexes) ? item.indexes.map(Number).filter(Number.isInteger) : [];
        if (at === null || now - at > AUTO_DELETE_FRESH_MS || !indexes.length) return;
        const seen = this.countersCapturedAt(snapshot, fuel);
        indexes.forEach(index => this.deletedPoints().set(fuel + ':' + index, Math.max(at, seen ?? -Infinity)));
        fresh[fuel].push(...indexes);
      });
      const fuels = Object.keys(fresh).filter(fuel => fresh[fuel].length);
      if (!fuels.length) return;
      if (this.selectedAcquiredPoints instanceof Set) {
        fuels.forEach(fuel => fresh[fuel].forEach(index => this.selectedAcquiredPoints.delete(fuel + ':' + index)));
      }
      const message = fuels.map(fuel => AutoCalUxModel.autoDeleteSentence(fresh[fuel], fuel)).join(' ');
      this.store.patch({ alert: { level: 'ok', message } });
    }

    /** Hora da leitura dos contadores da ECU (campo NUM_BUF_UPD_*; senão a do snapshot). */
    countersCapturedAt(snapshot, fuel) {
      const key = fuel === 'PETROL' ? 'NUM_BUF_UPD_PETR' : 'NUM_BUF_UPD_GAS';
      return finite(field(snapshot, key)?.capturedAtMs) ?? finite(snapshot?.capturedAtMs);
    }

    /** Um ponto apagado sai da lista quando chega leitura mais nova que a marca da confirmação (ou em 60 s sem hora conhecida). */
    expireRecentlyDeleted(snapshot) {
      const now = Date.now();
      this.deletedPoints().forEach((mark, key) => {
        const capturedAt = this.countersCapturedAt(snapshot, key.split(':')[0]);
        if ((capturedAt !== null && capturedAt > mark) || (capturedAt === null && now - mark > 60000)) this.deletedPoints().delete(key);
      });
    }

    /** Chaves dos pontos que existem AGORA na ECU (contador > 0) e não acabaram de ser apagados. */
    livePointKeys(snapshot) {
      const keys = new Set();
      for (const fuel of ['petrol', 'gas']) {
        AutoCalUxModel.acquiredPoints(snapshot || {}, fuel).forEach(p => keys.add(p.fuel + ':' + p.index));
      }
      this.deletedPoints().forEach((_, key) => keys.delete(key));
      return keys;
    }

    /** A seleção só guarda pontos que existem agora: nada de "seleção fantasma" de ponto já zerado. */
    pruneSelection(snapshot) {
      if (this.pendingPointReacquisitionKeys?.size) return;
      if (!(this.selectedAcquiredPoints instanceof Set)) return;
      const live = this.livePointKeys(snapshot);
      [...this.selectedAcquiredPoints].forEach(key => { if (!live.has(key)) this.selectedAcquiredPoints.delete(key); });
      if (this.selectedAcquiredPoint && !live.has(this.selectedAcquiredPoint)) this.selectedAcquiredPoint = null;
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
        this.store.patch({ alert: { level: 'warning', message: result?.error || 'Não foi possível mudar o aprendizado da ECU.' } });
      } else {
        // O botão só volta quando a ECU confirmar o novo estado (ou em 10 s): uma resposta "ok" do envio não é a conferência.
        this.toggleWaiting = { target: enable ? 1 : 0, since: Date.now() };
        this.store.patch({ alert: { level: 'ok', message: enable
          ? 'Pedido para retomar o aprendizado enviado. Conferindo na ECU…'
          : 'Pedido de pausa enviado. Conferindo na ECU…' } });
      }
      this.refresh();
    }

    /** Mostra (action) ou esconde (null) a confirmação de zerar o aprendizado da ECU. */
    askResetConfirm(action) {
      this.pendingResetAction = action || null;
      const box = this.panel?.querySelector('#autocalResetConfirm');
      if (!box) return;
      box.hidden = !action;
      const text = this.panel.querySelector('#autocalResetConfirmText');
      if (text && action) text.textContent = 'Zerar o que a ECU aprendeu ' + (action === 'RESET_PETROL' ? 'da gasolina' : 'do GNV') + '? Ela volta a aprender enquanto você dirige. Não dá para desfazer.';
    }

    prepare(action) {
      if (!action || !this.api?.available?.()) return;
      // Zerar a Curva K tem UM caminho: o botão da aba Curva K (foto antes, zera, conferência, Desfazer).
      if (action === 'RESET_K_FACTOR') return;
      const result = this.api.prepare(action);
      if (!result?.ok || !result?.prepared) {
        this.store.patch({ alert: { level: 'warning', message: result?.error || 'A ação AutoCal não pôde ser preparada.' } });
        return;
      }
      this.prepared = result;
      // Um toque (decisão do dono, 2026-10-05): sem cartão de revisão em nenhum botão. A proteção é o ACK + readback no
      // escritor e, onde existe, a foto antes com Desfazer.
      this.confirmPrepared();
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
      this.store.patch({ alert: { level: 'working', message: 'Comando enviado para a ECU. Aguarde a conferência.' } });
      this.refresh();
    }


    render() {
      const snapshot = this.snapshot || {};
      this.pruneSelection(snapshot);
      const state = this.state || {};
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
      this.renderAutoCleanLine();
      this.text('autocalAutoMatchEvidenceTitle', human.autoMatchEvidenceTitle);
      this.text('autocalAutoMatchEvidenceDetail', human.autoMatchEvidenceDetail);
      const autoMatchEvidence = document.getElementById('autocalAutoMatchEvidence');
      if (autoMatchEvidence) autoMatchEvidence.dataset.state = human.autoMatchEvidenceState;
      this.text('autocalNativeState', 'Leitura da ECU: ' + acquisitionLabel);
      this.text('autocalZoneSummary', human.gasZones === null ? 'Zonas GNV sem leitura' : human.gasZones + '/4 zonas GNV');
      const readingNote = AutoCalUxModel.readingNote(snapshot, Date.now());
      this.text('autocalReferenceSource', AutoCalUxModel.referenceSourceLabel(this.projection) + (readingNote ? ' · ' + readingNote : ''));
      this.renderZoneMeter(human);
      this.renderSessionState();
      this.renderLiveNarrative();

      if (this.toggleWaiting && String(this.actionState?.state || '').toUpperCase() === 'FAILED') {
        // A ECU não confirmou (readback diferente, sem ACK): o botão volta e o dono fica sabendo; nada mudou na ECU.
        this.toggleWaiting = null;
        this.store.patch({ alert: { level: 'warning', message: 'A ECU não confirmou a mudança do aprendizado: ' + String(this.actionState.message || 'tente de novo') + '.' } });
      }
      if (this.toggleWaiting && (human.enabled === this.toggleWaiting.target || Date.now() - this.toggleWaiting.since > 10000)) this.toggleWaiting = null;
      const waiting = this.operationalPending || Boolean(this.toggleWaiting);
      // Limpeza automática em curso: os botões esperam, mas o texto não finge que o dono pediu algo.
      const automaticOnly = waiting && !this.toggleWaiting && this.actionState?.automatic === true;
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
        toggle.textContent = waiting && !automaticOnly
          ? 'Confirmando ECU…'
          : action === 'DISABLE_AUTO_CAL'
            ? 'Pausar aprendizado da ECU'
            : action === 'ENABLE_AUTO_CAL' ? 'Retomar aprendizado' : action === 'REREAD_STATE' ? 'Estado não chegou · ler de novo' : 'Lendo estado…';
        toggle.dataset.loading = action || waiting ? 'false' : 'true';
      }

      this.panel?.querySelectorAll('[data-autocal-action]').forEach(button => {
        button.disabled = waiting;
      });

      this.renderHistoryControl();

      this.renderReferenceChart(snapshot);
      this.renderActionState();
      this.renderPointActions();
      const rejected = this.referenceUsable === true && Array.isArray(this.analysis?.rejectedBands) ? this.analysis.rejectedBands.length : 0;
      this.text('autocalNoiseSummary', rejected ? D.plural(rejected, 'ponto incoerente ignorado', 'pontos incoerentes ignorados') + ' no cálculo do Refino' : '');
    }

    /** UMA frase humana de estado (nada de jargão): o que a ECU está fazendo e o que falta. */
    /** [fuelKind] = combustível de AGORA pela telemetria ('petrol', 'gas', …): a frase nunca manda "dirigir no GNV" com o carro na gasolina. */
    sentenceFor(human, acquisitionName, fuelKind) {
      // "zona 2" · "zonas 1 e 3" · "zonas 1, 2 e 4"
      const zones = list => list.length === 1 ? 'a zona ' + list[0]
        : 'as zonas ' + list.slice(0, -1).join(', ') + ' e ' + list[list.length - 1];
      const missing = list => (list.length === 1 ? 'falta ' : 'faltam ') + zones(list);
      if (['UNAVAILABLE', 'PROBE_FAILED', 'FAILED', 'DISCONNECTED', 'STALE_SESSION'].includes(acquisitionName)) {
        return { level: 'error', text: 'Sem leitura da ECU. Confira o cabo: o app tenta de novo sozinho.' };
      }
      const action = this.actionState || {};
      if (action.busy === true && ['RESET_PETROL', 'RESET_GAS'].includes(action.action)) {
        const fuel = action.action === 'RESET_PETROL' ? 'da gasolina' : 'do GNV';
        return { level: 'neutral', text: 'Recomeçando o aprendizado ' + fuel + ': conferindo na ECU…' };
      }
      if (acquisitionName === 'WAITING_TELEMETRY_SETTLE') return { level: 'neutral', text: 'Conectando à ECU…' };
      if (human.enabled === 0) return { level: 'warn', text: 'Aprendizado pausado. Toque em Retomar aprendizado para continuar.' };
      if (human.enabled === 1) {
        const petrol = human.petrolMissingZones;
        const gas = human.gasMissingZones;
        if (fuelKind === 'petrol' && petrol.length) return { level: 'neutral', text: 'A ECU está aprendendo a gasolina · ' + missing(petrol) + '.' };
        if (fuelKind === 'gas' && gas.length) return { level: 'neutral', text: 'A ECU está aprendendo o GNV · ' + missing(gas) + '.' };
        if (petrol.length) return { level: 'neutral', text: 'Falta aprender a gasolina n' + zones(petrol) + '. Dirija um pouco na gasolina.' };
        if (gas.length) return { level: 'neutral', text: 'Falta aprender o GNV n' + zones(gas) + '. Dirija um pouco no GNV.' };
        if (human.petrolZones === null || human.gasZones === null) return { level: 'neutral', text: 'Aguardando a ECU confirmar as zonas de gasolina e GNV.' };
        if (human.gasZones === 4 && human.petrolZones === 4) return { level: 'ok', text: 'Gasolina e GNV aprendidos.' };
        return { level: 'neutral', text: 'Aprendizado ativo. Aguardando a ECU confirmar as zonas.' };
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
      const text = count === null ? '—' : Math.round(count) + (max !== null && max > 0 ? ' de ' + Math.round(max) : '');
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
      const strip = this.panel?.querySelector('.autocal-session-strip');
      if (strip) strip.dataset.sessionLevel = narrative.level;


    }

    /** Linha discreta da limpeza automática do GNV (ligada · N reaprendidos, ou pausada com o que fazer). */
    renderAutoCleanLine() {
      const node = document.getElementById('autocalAutoCleanLine');
      if (!node) return;
      const line = AutoCalUxModel.autoCleanupLine(this.autoCleanup || {});
      if (node.hidden !== line.hidden) node.hidden = line.hidden;
      if (node.textContent !== line.text) node.textContent = line.text;
      if (node.dataset.level !== line.level) node.dataset.level = line.level;
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
        this.text('autocalLiveLoad', '—');
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
      this.text('autocalLiveZone', region.kind === 'idle' ? 'Marcha lenta' : region.zone === null ? '—' : 'Zona ' + region.zone + ' de 4');
      // Carga em palavras (a pressão em bar fica nos Detalhes técnicos).
      this.text('autocalLiveLoad', live.mapBar < 0.45 ? 'leve' : live.mapBar <= 0.75 ? 'média' : 'forte');
      const enabled = AutoCalUxModel.humanState(this.snapshot || {}, this.acquisitionState || {}, this.projection).enabled;
      const acquisitionCopy = enabled === 1
        ? 'Aprendizado da ECU ativo. Se a condição estabilizar, a ECU pode aprender mais nesta região.'
        : enabled === 0 ? 'Aprendizado da ECU pausado. O ponto AGORA só mostra onde o motor está.' : 'Estado do aprendizado da ECU ainda não confirmado.';
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
      const layer = this.chartPart('.autocal-live-layer');
      const bandLayer = this.chartPart('[data-autocal-current-band]');
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
      // O laço de quadros dorme quando o cursor chega; alvo novo o acorda.
      this.scheduler?.wake?.();
      if (typeof this.scheduler?.addFrameHook !== 'function' || seen?.scale !== this.chartScale) this.cursor.paint();
      const label = this.chartPart('[data-autocal-live-label]');
      if (label) {
        const text = projected.outOfRange ? 'AGORA · fora da escala' : 'AGORA';
        const shown = live.grey ? text + ' · atrasado' : text;
        if (label.textContent !== shown) label.textContent = shown;
      }
    }

    /** Peça do gráfico do AutoCal: só dentro do próprio quadro (o Refino tem a sua camada AGORA). */
    chartPart(selector) {
      const host = document.getElementById('autocalReferenceChart');
      return host && typeof host.querySelector === 'function' ? host.querySelector(selector) : null;
    }

    /** Quadro de animação (rAF do scheduler): o cursor compartilhado só move a camada com CSS transform. */
    animateCursor(timestamp) { return this.cursor.frame(timestamp); }

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
          // Estado visível sem depender só da cor: adquirida ✓, falta FALTA, desconhecida —.
          if (status) status.textContent = state === 'acquired' ? '✓' : state === 'missing' ? 'FALTA' : '—';
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
      if (this.chartScale) {
        const { xMin, xMax, yMin, yMax } = this.chartScale;
        this.epochDomain = { xMin, xMax, yMin, yMax };
      }
      const snapshot = this.snapshot || {};
      const axis = field(snapshot, 'PETR_INJ_TBP');
      const rv = field(snapshot, 'PETR_MNFLD_PRESS_RV');
      const axisAt = finite(axis?.capturedAtMs), rvAt = finite(rv?.capturedAtMs);
      const limit = finite(this.projection?.referenceTimingLimitMs);
      // A revisão da ponte e a telemetria não são geometria. Preserve o SVG e
      // o cursor quando só eles mudam; invalide por dados ou máscara da época.
      // Pontos entram só pela geometria: o contador (progresso) é atualizado por atributo, sem refazer o SVG.
      const geometry = list => list.map(p => [p.index, p.petrolMs, p.mapBar]);
      const key = JSON.stringify([geometry(acquiredPetrol), geometry(acquiredGas), epoch,
        snapshot.source, this.state?.maxAutomatch ?? snapshot.maxAutomatch,
        axisAt !== null && rvAt !== null && limit !== null && Math.abs(axisAt - rvAt) <= limit,
        ['PETR_INJ_TBP','PETR_MNFLD_PRESS_RV','PETR_INJ_TBUF_GAS_PREV','MNFLD_PRESS_BUF_GAS_PREV','MNFLD_PRESS_THD','ACQUIRED_ZONES_PETROL','ACQUIRED_ZONES_GAS','MUL_ACT']
          .map(name => [field(snapshot, name)?.status, physicalVector(snapshot, name)]),
        Math.round((host.clientWidth || 1000) / 16), Math.round((host.clientHeight || 400) / 16),
        ns.CurveChart?.viewKey(this.chartView)]);
      if (this.epochChartHost === host && this.epochChartKey === key &&
          this.epochChartNode && host.firstElementChild === this.epochChartNode) {
        ns.CurveChart?.updatePoints(host, [...acquiredPetrol, ...acquiredGas]);
        this.renderLiveCursor();
        return;
      }
      ns.CurveChart?.release(host);
      this.chartSignature = null;
      // ProgBase DUMP/TAutoCalUI separates PetrolCurve (native RV), PetrolPoint,
      // GasPoint, GasPointPrev and KLine. Do not collapse them into one reference.
      this.chartScale = null;
      this.chartRenderKey = null;
      const petrolCount = acquiredPetrol.length;
      const gasCount = acquiredGas.length;
      const automatch = finite(epoch.nativeAutoMatchCount);
      const quota = finite(this.state?.maxAutomatch ?? snapshot.maxAutomatch);
      const step = automatch === null ? '—' : String(automatch) + (quota !== null ? '/' + quota : '');
      const restartBoth = epoch.petrolPending === true && epoch.gasPending === true;
      const stage = AutoCalUxModel.epochNarrative(epoch);

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

      const visible = this.chartView || {};
      const petrol = visible.petrol === false ? [] : acquiredPetrol;
      const gas = visible.gas === false ? [] : acquiredGas;
      const reference = visible.petrol === false ? [] : petrolCurve;
      const previous = visible.gas === false ? [] : previousGas;
      const ecu = [...petrol.map(p => ({ ...p, fuel: 'PETROL' })), ...gas.map(p => ({ ...p, fuel: 'GAS' }))];
      const previousDomain = this.epochDomain;
      const domain = ns.CurveChart.focusDomain(reference, ecu, previous, { fullRange: visible.fullRange === true }) || previousDomain;
      let chart = '';
      if (domain) {
        this.epochDomain = { ...domain };
        const width = Math.round(host.clientWidth) || 1000;
        const height = Math.round(host.clientHeight) || 400;
        const human = AutoCalUxModel.humanState(snapshot, this.state || {}, this.projection);
        const model = { domain, reference, ecu, zones: AutoCalUxModel.zoneSurface(snapshot, human), history: [] };
        const built = ns.CurveChart.buildSvg(model, { width, height });
        this.chartScale = built.scale;
        const { xFor: x, yFor: y } = built.scale;
        const within = p => p.petrolMs >= domain.xMin && p.petrolMs <= domain.xMax &&
          p.mapBar >= domain.yMin && p.mapBar <= domain.yMax;
        const line = (list, fuel) => {
          const ordered = list.filter(within).slice().sort((a, b) => a.petrolMs - b.petrolMs);
          if (ordered.length < 2) return '';
          const d = ordered.map((p, i) => (i ? 'L' : 'M') + ' ' + x(p.petrolMs).toFixed(1) + ' ' + y(p.mapBar).toFixed(1)).join(' ');
          return '<path class="autocal-epoch-acquisition-line ' + fuel + '" d="' + d + '"></path>';
        };
        const historical = previous.filter(within).map(p => '<circle class="autocal-previous-gas-point" cx="' +
          x(p.petrolMs).toFixed(1) + '" cy="' + y(p.mapBar).toFixed(1) + '" r="4.5"></circle>').join('');
        chart = built.svg
          .replace('class="autocal-reference-line petrol"', 'class="autocal-reference-line petrol epoch-anchor"')
          .replace('<g class="autocal-live-layer"', line(petrol, 'petrol') + line(gas, 'gas') + historical + '<g class="autocal-live-layer"')
          .replace('</svg>', '<title>CURVAS DA ECU · aquisição atual, sem equivalência durante reinício</title></svg>');
      }
      host.innerHTML = chart ? '<div class="curve-chart-shared" data-mode="ecu18">' + chart + '</div>' : '<div class="chart-empty"><b>CURVAS DA ECU</b><span>' +
        escapeHtml(stage) + '. Aguardando pontos da ECU. Nenhuma equivalência é calculada agora.</span></div>';
      this.epochChartHost = host;
      this.epochChartKey = key;
      this.epochChartNode = host.firstElementChild;
      this.renderLiveNarrative();
      this.renderLiveCursor();
      if (this.selectedAcquiredPoint) {
        const [fuel,index] = this.selectedAcquiredPoint.split(':');
        this.inspectAcquiredPoint(fuel,Number(index));
      }
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
          host.innerHTML = '<div class="chart-empty"><b>Aguarde alguns segundos</b><span>A ECU mandou dados fora de sincronia. Aguarde alguns segundos; o app tenta de novo sozinho.</span>' +
            '<details class="instrument-details"><summary>Detalhes técnicos</summary><span>Vetores lidos com ' + spanLabel + ' de diferença; ' + limitLabel + '.</span></details></div>';
          this.readout('A ECU mandou dados fora de sincronia. Aguarde alguns segundos; o app tenta de novo sozinho.');
        } else {
          this.text('autocalReferenceCount', '0 pontos utilizáveis');
          host.innerHTML = '<div class="chart-empty"><b>Curva da gasolina ainda não chegou</b><span>Dirija um pouco na gasolina: a ECU precisa disso para desenhar a curva.</span></div>';
          this.readout('Dirija um pouco na gasolina: a ECU precisa disso para desenhar a curva.');
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
      const alreadyShown = this.renderedChartHost === host && this.chartSignature === signature && host.contains?.(chart.nodeFor('ecu18')) !== false;
      if (alreadyShown) {
        chart.updatePoints(chart.nodeFor('ecu18'), acquiredPoints);
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
      // Contadores de agora (o modelo pode vir do cache deste modo), só dos pontos visíveis no desenho.
      this.currentAcquiredPoints = acquiredPoints.filter(p => model.ecu.some(m => m.fuel === p.fuel && m.index === p.index));
      // O desenho pode ter vindo do cache deste modo: o progresso de agora entra por atributo.
      chart.updatePoints(chart.nodeFor('ecu18'), acquiredPoints);
      const legend = document.getElementById('autocalLegend');
      const legendKey = `ecu18|${history.length > 0}`;
      if (legend && this.legendKey !== legendKey) {
        this.legendKey = legendKey;
        legend.innerHTML = chart.legendHtml({ mode: 'ecu18' }) + (history.length ? '<span class="previous" data-legend="previous">Leitura anterior</span>' : '');
      }
      chart.applySelection({ ref: this.selectedReferenceIndex, ecu: this.selectedAcquiredPoint, batch: this.selectedAcquiredPoints }, 'ecu18');
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

    /** Detalhe do ponto em português simples; os números técnicos ficam num "Detalhes técnicos". */
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
      const passes = Math.round(point.counter);
      const fuelName = point.fuel === 'GAS' ? 'do GNV' : 'da gasolina';
      const line = 'Ponto ' + point.point + ' ' + fuelName + ' (zona ' + point.zone + '). A ECU já passou aqui ' +
        (passes === 1 ? '1 vez' : passes + ' vezes') + '. Reaprender faz a ECU medir este ponto de novo.';
      const technical = [D.msUnit(point.petrolMs), D.barUnit(point.mapBar),
        'contador ' + passes + (finite(point.threshold) === null ? '' : ' de ' + Math.round(point.threshold)),
        point.acquisitionState === 'ACQUIRED' ? 'aprendido' : 'ainda aprendendo'].join(' · ');
      this.readout(line, '<details class="autocal-point-tech"><summary>Detalhes técnicos</summary><span>' + escapeHtml(technical) + '</span></details>');
      this.renderPointActions();
    }

    /** Tocar num ponto marca ou desmarca; ponto recém-apagado é intocável até a ECU mandar leitura nova. */
    tapAcquiredPoint(fuel, index) {
      if (!Number.isInteger(index)) return;
      const key = String(fuel) + ':' + String(index);
      if (this.deletedPoints().has(key)) {
        this.readout('Este ponto acabou de ser apagado. Ele some do gráfico quando a ECU mandar a próxima leitura.');
        return;
      }
      if (this.refreshBusy()) return;
      if (this.selectedAcquiredPoints.has(key)) {
        this.selectedAcquiredPoints.delete(key);
        if (this.selectedAcquiredPoint === key) this.selectedAcquiredPoint = null;
        if (this.selectedAcquiredPoints.size) {
          const [lastFuel, lastIndex] = [...this.selectedAcquiredPoints].pop().split(':');
          this.inspectAcquiredPoint(lastFuel, Number(lastIndex));
        } else this.readout('');
      } else {
        if (!this.livePointKeys(this.snapshot).has(key)) return;
        this.selectedAcquiredPoints.add(key);
        this.inspectAcquiredPoint(fuel, index);
      }
      this.renderPointActions();
    }

    /** Classes da seleção e dos recém-apagados direto no gráfico do AutoCal (sem redesenhar). */
    paintSelection() {
      const host = document.getElementById('autocalReferenceChart');
      if (!host || typeof host.querySelectorAll !== 'function') return;
      host.querySelectorAll('[data-autocal-acquired-index]').forEach(node => {
        const key = node.dataset.autocalAcquiredFuel + ':' + node.dataset.autocalAcquiredIndex;
        const deleted = this.deletedPoints().has(key);
        node.classList.toggle('selected', !deleted && key === this.selectedAcquiredPoint);
        node.classList.toggle('batch-selected', !deleted && this.selectedAcquiredPoints.has(key));
        node.classList.toggle('recently-deleted', deleted);
        if (deleted) node.setAttribute('aria-disabled', 'true'); else node.removeAttribute('aria-disabled');
      });
      host.querySelectorAll('[data-autocal-point-key]').forEach(node => {
        node.classList.toggle('recently-deleted', this.deletedPoints().has(node.getAttribute('data-autocal-point-key')));
      });
    }

    /** Só dois botões: "Reaprender N pontos" e "Cancelar". Aparecem com pontos marcados (ou enquanto a ECU confere). */
    renderPointActions() {
      const count = this.selectedAcquiredPoints.size;
      const busy = this.refreshBusy();
      const pending = this.pendingPointReacquisitionKeys.size > 0;
      const editing = count > 0 || pending;
      const bar = this.panel?.querySelector('.autocal-point-actions');
      const main = this.panel?.querySelector('.autocal-main-actions');
      if (!bar || !main) return;
      bar.hidden = !editing; main.hidden = editing;
      const batch = bar.querySelector('[data-autocal-reacquire-selected]');
      batch.disabled = busy || !count;
      batch.textContent = busy && pending ? 'Conferindo na ECU…' : 'Reaprender ' + D.plural(Math.max(count, 1), 'ponto', 'pontos');
      bar.querySelector('[data-autocal-clear-point-selection]').disabled = busy;
      this.paintSelection();
    }

    /** "Cancelar": desmarca tudo e volta aos botões normais. */
    clearAcquiredPointSelection() {
      this.selectedAcquiredPoints.clear();
      this.selectedAcquiredPoint = null;
      this.readout('');
      this.renderPointActions();
    }

    requestSelectedPointReacquisition() {
      // Antes de apagar: só pontos que existem agora (contador > 0) e não acabaram de ser apagados.
      this.pruneSelection(this.snapshot);
      if (!this.api?.available?.() || this.selectedAcquiredPoints.size === 0 || this.refreshBusy()) { this.renderPointActions(); return; }
      const targets = Array.from(this.selectedAcquiredPoints).map(key => {
        const [fuel, rawIndex] = key.split(':');
        return { fuel, index: Number(rawIndex) };
      }).filter(item => Number.isInteger(item.index));
      const prepared = this.api.preparePointDeleteBatch?.(targets) || { ok: false, error: 'Reaprender pontos indisponível.' };
      if (!prepared?.ok || !prepared?.prepared) {
        this.store.patch({ alert: { level: 'warning', message: prepared?.error || 'Não foi possível preparar os pontos marcados.' } });
        return;
      }
      const result = this.api.execute(prepared.preparationId);
      if (result?.ok !== true) {
        this.api?.cancelPreparation?.();
        this.store.patch({ alert: { level: 'warning', message: result?.error || 'Não foi possível começar a reaprender os pontos.' } });
        return;
      }
      const count = targets.length;
      this.pendingPointReacquisitionKeys = new Set(targets.map(item => item.fuel + ':' + item.index));
      this.store.patch({
        alert: {
          level: 'working',
          message: D.plural(count, 'ponto vai', 'pontos vão') + ' ser medidos de novo. A seleção só é limpa depois que a ECU confirmar.',
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
      this.renderPointActions();
      this.readout('Curva · ponto ' + (point.index + 1) + ' · ' + D.msUnit(point.petrolMs) + ' · gasolina ' + D.bar(point.petrolMapBar) + ' · GNV ' + D.barUnit(point.gasMapBar));
      ns.CurveChart?.applySelection({ ref: point.index, ecu: null, batch: this.selectedAcquiredPoints }, 'ecu18');
      this.paintSelection();
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
      if (state.automatic === true && name !== 'IDLE') {
        // Limpeza automática: o app agiu sozinho; nunca "Pronto"/"você confirmou", nunca jargão do protocolo.
        const indexes = AutoCalUxModel.autoActionIndexes(state);
        const fuel = AutoCalUxModel.autoActionFuel(state);
        host.hidden = false;
        host.dataset.level = name === 'CONFIRMED' ? 'ok' : working ? 'working' : 'neutral';
        host.dataset.reasonCode = '';
        host.dataset.mutationUncertain = 'false';
        host.textContent = name === 'CONFIRMED'
          ? AutoCalUxModel.autoDeleteSentence(indexes, fuel)
          : failed ? 'A limpeza automática não conseguiu agora; o app tenta de novo sozinho.'
          : 'O app está pedindo para a ECU reaprender ' + (AutoCalUxModel.pointsPhrase(indexes) || 'um ponto') +
            ' ' + AutoCalUxModel.autoFuelOf(fuel) + ', que estava fora da curva.';
        return;
      }
      const recovery = state?.recovery && typeof state.recovery === 'object' ? state.recovery : null;
      const recoveryNext = String(recovery?.nextAction || '').trim();
      const recoveryCode = String(recovery?.reasonCode || state?.reasonCode || '');
      const mutationMayHaveStarted = state?.mutationMayHaveStarted === true;
      const uncertainty = failed && mutationMayHaveStarted
        ? ' · A ECU pode ter mudado em parte: a seleção foi limpa; espere a próxima leitura antes de tentar de novo.'
        : '';
      host.hidden = name === 'IDLE';
      host.dataset.level = failed ? 'error' : name === 'CONFIRMED' ? 'ok' : working ? 'working' : 'neutral';
      host.dataset.reasonCode = recoveryCode;
      host.dataset.mutationUncertain = mutationMayHaveStarted ? 'true' : 'false';
      host.textContent = name === 'IDLE' ? message
        : name === 'CONFIRMED' ? 'Pronto · ' + message
        : failed ? 'Não deu certo · ' + message + uncertainty + (recoveryNext ? ' · Próximo passo: ' + recoveryNext : '')
        : 'Fazendo agora · ' + message;
    }

    renderUnavailable() {
      this.chartRenderKey = null;
      this.text('autocalNativeState', 'AUTOCAL INDISPONÍVEL');
      this.text('autocalHumanAction', 'AutoCal indisponível. Reconecte a ECU e tente de novo.');
      const sentence = document.getElementById('autocalHumanAction');
      if (sentence) sentence.dataset.level = 'error';
      this.text('autocalZoneSummary', '—');
      this.text('autocalReferenceCount', '—');
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
