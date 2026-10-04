(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  /**
   * Regras únicas de exibição de número. Cada regra diz: o que é inválido, o que a tela mostra
   * nesse caso (sempre "—", nunca 0) e, quando o número pode cair, o motivo que aparece.
   *
   * DESCONHECIDO ≠ ZERO. null, undefined, '', NaN e Infinity nunca viram 0.
   * Um 0 só aparece quando a fonte mediu zero.
   */
  const DASH = '—';

  function finite(value) {
    if (value === null || value === undefined || value === '' || typeof value === 'boolean') return null;
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  }

  function number(value, digits) {
    const n = finite(value);
    return n === null ? DASH : n.toLocaleString('pt-BR', { minimumFractionDigits: digits || 0, maximumFractionDigits: digits || 0 });
  }

  function escapeHtml(value) {
    return String(value === null || value === undefined ? '' : value).replace(/[&<>"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[char]));
  }
  function clamp(value, minimum, maximum) { return Math.max(minimum, Math.min(maximum, value)); }
  /** Número pt-BR com casas fixas; desconhecido → "—" (nunca 0). */
  const fmt = number;

  /** Formatos únicos por grandeza (pt-BR, "—" para desconhecido). */
  const ms = value => number(value, 2);
  const msUnit = value => finite(value) === null ? DASH : `${number(value, 2)} ms`;
  const msBand = (from, to) => finite(from) === null || finite(to) === null ? DASH : `de ${number(from, 1)} a ${number(to, 1)} ms`;
  const bar = value => number(value, 3);
  const barUnit = value => finite(value) === null ? DASH : `${number(value, 3)} bar`;
  const kValue = value => number(value, 3);
  const rpm = value => finite(value) === null ? DASH : Math.round(finite(value)).toLocaleString('pt-BR');
  /** Fração 0..1 → "62%" (0 casas). Nunca 0% por falta de dado. */
  const percentFraction = value => finite(value) === null ? DASH : `${Math.round(clamp(finite(value), 0, 1) * 100)}%`;
  /** Razão GNV÷gasolina → diferença assinada "+2,1%" (nunca 1,021). */
  function gapPercent(ratioValue) {
    const r = finite(ratioValue);
    if (r === null) return DASH;
    const p = (r - 1) * 100;
    return `${p > 0 ? '+' : ''}${number(p, 1)}%`;
  }
  /** Plural em português: plural(1,'alteração','alterações') → "1 alteração". */
  function plural(n, one, many) {
    const v = finite(n);
    if (v === null) return DASH;
    return `${Math.round(v).toLocaleString('pt-BR')} ${Math.round(v) === 1 ? one : (many || one + 's')}`;
  }
  /** "agora", "há 12 s", "há 3 min", "há 2 h". Sem data conhecida: "—". */
  function ageText(atMs, nowMs) {
    const at = finite(atMs);
    const now = finite(nowMs);
    if (at === null || at <= 0 || now === null) return DASH;
    const s = Math.max(0, Math.round((now - at) / 1000));
    if (s < 5) return 'agora';
    if (s < 90) return `há ${s} s`;
    if (s < 5400) return `há ${Math.round(s / 60)} min`;
    return `há ${Math.round(s / 3600)} h`;
  }

  /** Idade em ms (frescor da leitura) na palavra do glossário: "agora" até 1,5 s, depois "há N s" / "há N min" / "há N h". Desconhecida: "—". */
  function ageSinceMs(ms) {
    const value = finite(ms);
    if (value === null || value < 0) return DASH;
    if (value < 1500) return 'agora';
    const s = Math.round(value / 1000);
    if (s < 90) return `há ${s} s`;
    if (s < 5400) return `há ${Math.round(s / 60)} min`;
    return `há ${Math.round(s / 3600)} h`;
  }

  /** Contagem inteira ≥ 0. Desconhecido → "—". */
  function count(value) {
    const n = finite(value);
    return n === null || n < 0 ? DASH : String(Math.round(n));
  }

  /** "n/total" (zonas, faixas). Qualquer lado desconhecido → "—/total" ou "—". */
  function ratio(value, total) {
    const t = finite(total);
    if (t === null) return DASH;
    return `${count(value)}/${Math.round(t)}`;
  }

  /** Combustível da MP48. O padrão do Kotlin ("--") e vazio são desconhecidos. */
  function fuelLabel(raw) {
    const value = String(raw === null || raw === undefined ? '' : raw).trim().toUpperCase();
    if (!value || value === '--' || value === '—' || value === 'NULL' || value === 'UNDEFINED') return DASH;
    if (value.includes('PETROL') || value.includes('GASOLINA')) return 'GASOLINA';
    if (value.includes('CNG') || value.includes('GNV') || value.includes('GAS')) return 'GNV';
    if (value.includes('CUTOFF')) return 'CORTE';
    if (value.includes('TRANS')) return 'TRANSIÇÃO';
    if (value.includes('OFF') || value.includes('DESLIG')) return 'DESLIGADO';
    // Nome que a ECU não explica (DESCONHECIDO, código novo) nunca vira rótulo cru: é desconhecido.
    return DASH;
  }

  function durationLabel(ms) {
    const value = finite(ms);
    if (value === null || value < 0) return DASH;
    const minutes = Math.floor(value / 60000);
    const seconds = Math.floor((value % 60000) / 1000);
    if (minutes >= 60) return `${Math.floor(minutes / 60)}h ${String(minutes % 60).padStart(2, '0')}min`;
    return minutes ? `${minutes} min` : `${seconds} s`;
  }

  function bytesLabel(bytes) {
    const n = finite(bytes);
    if (n === null || n < 0) return DASH;
    if (n >= 1024 * 1024) return `${number(n / 1024 / 1024, 1)} MB`;
    if (n >= 1024) return `${number(n / 1024, 0)} KB`;
    return `${Math.round(n)} B`;
  }

  function megabytesLabel(mb) {
    const n = finite(mb);
    return n === null || n < 0 ? DASH : `${number(n, 1)} MB`;
  }

  function ageLabel(ms) {
    const value = finite(ms);
    if (value === null || value < 0) return DASH;
    if (value < 1000) return `${Math.round(value)} ms`;
    return `${number(value / 1000, 1)} s`;
  }

  /** Data de uma sessão: sem data conhecida não vira 31/12/1969. */
  function sessionDate(item) {
    const match = String(item?.id || '').match(/session_(\d{4}-\d{2}-\d{2})_(\d{2}-\d{2}-\d{2})/);
    if (match) return match[1].split('-').reverse().join('/') + ' ' + match[2].replace(/-/g, ':');
    const when = finite(item?.createdAt);
    if (when === null || when <= 0) return DASH;
    return new Date(when).toLocaleString('pt-BR');
  }

  /**
   * Palavras únicas de toda escrita na ECU (spec §3.1): etapa → resultado humano → Desfazer/Voltar.
   * "Gravado" só depois do readback (regra 3).
   */
  const OPERATION_WORDING = {
    stages: ['Foto antes', 'Gravando', 'Conferindo na ECU'],
    writing: 'Gravando na ECU…',
    // `opts.fem` = substantivo feminino (Curva K, células); `opts.many` = plural: "conferida", "conferidas".
    doneTitle: (what, opts) => `Gravado · ${what} ${opts && opts.fem ? (opts.many ? 'conferidas' : 'conferida') : 'conferido'} na ECU`,
    doneDetail: 'A ECU confirmou. A tela foi relida.',
    failedTitle: 'Não foi gravado',
    failedDetail: 'O app não conseguiu confirmar na ECU. A curva anterior continua.',
    back: 'Voltar',
    undo: 'Desfazer',
  };

  /**
   * Regra 7: erro de transporte (cabo/USB) não é erro da ECU. O Kotlin classifica a falha em
   * `failureKind` (TRANSPORTE / ECU / APP) onde lê `reply.error`; a tela mostra textos distintos.
   */
  function failureKind(operation) {
    const raw = operation && (operation.failureKind || (operation.failure && operation.failure.failureKind));
    const kind = String(raw || '').toUpperCase();
    return kind === 'TRANSPORTE' || kind === 'ECU' ? kind : 'APP';
  }
  /** Motivo da política de segurança (Kotlin: CalibrationWriteSafetyPolicy) em palavras simples + próxima ação. */
  function safetyReason(raw) {
    const text = String(raw || '');
    if (/Telemetria não está atual/i.test(text)) return 'a telemetria está velha. Aguarde alguns segundos e toque de novo.';
    if (/Conecte a ECU/i.test(text)) return 'a ECU não está conectada. Conecte o cabo e toque de novo.';
    if (/Permissão USB/i.test(text)) return 'falta autorizar o USB no Android. Toque em Permitir.';
    if (/Comunicação com a ECU/i.test(text)) return 'a comunicação com a ECU não está estável. Aguarde e toque de novo.';
    if (/Serviço Android/i.test(text)) return 'o serviço do app não está rodando. Reabra o app.';
    return text || 'a segurança não liberou a gravação agora.';
  }
  function failureText(operation, fallback) {
    const op = operation || {};
    const failure = op.failure || {};
    if (op.safetyBlocked === true || failure.safetyBlocked === true || String(op.writerState || '').startsWith('SAFETY_LOCKED')) {
      return `Gravação bloqueada: ${safetyReason(op.error || failure.error || op.message)}`;
    }
    const message = String(op.error || failure.error || failure.message || op.message || op.writerMessage || fallback || '').trim();
    const kind = failureKind(op);
    if (kind === 'TRANSPORTE') return `Cabo/USB: ${message || 'a comunicação com a ECU falhou'}`;
    if (kind === 'ECU') return `A ECU recusou: ${message || 'o comando não foi aceito'}`;
    return message;
  }

  /**
   * Telemetria só é atualizada nas rotas ao vivo. Numa rota sem pump (Ajuste global, Sugestões,
   * Ferramentas) o último valor ficava na barra de status como se fosse de agora. Depois de maxMs
   * sem renovar, vale "desconhecido" (—), nunca o último RPM com cara de ao vivo.
   * Instante da última renovação desconhecido também conta como vencido.
   */
  const OFF_ROUTE_TELEMETRY_MAX_MS = 3000;
  function offRouteTelemetryExpired(isLiveRoute, telemetryValid, patchedAtMs, nowMs, maxMs) {
    if (isLiveRoute === true || telemetryValid !== true) return false;
    const at = finite(patchedAtMs);
    const now = finite(nowMs);
    if (at === null || at <= 0 || now === null) return true;
    return now - at > (finite(maxMs) ?? OFF_ROUTE_TELEMETRY_MAX_MS);
  }

  /**
   * Telemetria flutuante: o balão só aparece fora do OMEGAS, então "visível" é quase sempre falso com o
   * app aberto. O estado que importa é: sem autorização, desligada ou ligada (aparece ao sair do app).
   */
  function overlayState(status) {
    const s = status || {};
    if (s.supported === false) return { key: 'unsupported', title: 'Indisponível neste Android', help: 'Este Android não permite balão sobre outros apps.' };
    if (s.permissionGranted !== true) return { key: 'needs-permission', title: 'Precisa de autorização', help: 'O balão mostra combustível, RPM, Injeção, MAP e gás por cima de outros apps (mapa, música). Toque em Autorizar: o Android abre a tela certa, marque o OMEGAS e volte.' };
    if (s.requestedEnabled === true) return { key: 'on', title: 'Ligada', help: 'Aparece quando você sai do OMEGAS e nunca cobre o app. Arraste para mover e toque no Ω para abrir ou fechar.' };
    return { key: 'off', title: 'Desligada', help: 'Autorizada. Toque em Ativar para mostrar o balão quando você sair do OMEGAS.' };
  }

  /** Pergunta uma única vez, no primeiro uso, se quer ligar o balão; quem já decidiu não é incomodado. */
  function shouldPromptOverlay(status, alreadyPrompted) {
    const s = status || {};
    return alreadyPrompted !== true && s.testHarness !== true && s.supported === true && s.permissionGranted !== true && s.requestedEnabled !== true;
  }

  /**
   * Conexão com a ECU em palavras que o motorista entende, cada uma com a próxima ação:
   * online · Sem dados da ECU (cabo ligado, nenhuma leitura fresca) · Conectando… (permissão USB pendente) ·
   * USB bloqueado (o dono negou a permissão: toque para permitir) · Sem cabo · Serviço parado.
   * "ECU online" só quando chega dado: `reading` (LiveStore.read) com leitura válida e fresca. Sem `reading` o chamador
   * só sabe do cabo (usbConnected) e a palavra "online" vale só para isso.
   */
  function connectionState(status, reading) {
    const s = status || {};
    if (s.usbConnected === true) {
      if (s.engineStuck === true) return { key: 'attention', label: 'ECU sem resposta', hint: 'Aguarde ou reconecte o cabo USB', online: true };
      if (reading && (reading.level === 'none' || reading.level === 'lost')) {
        return { key: 'nodata', label: 'Sem dados da ECU', hint: 'Confira o cabo e a chave; volta sozinho quando chegar dado', online: false, connected: true };
      }
      return { key: 'online', label: 'ECU online', hint: '', online: true };
    }
    if (s.usbPermissionPending === true) return { key: 'connecting', label: 'Conectando…', hint: 'Toque em Permitir no aviso de USB do Android', online: false };
    if (s.usbPermissionDenied === true) return { key: 'denied', label: 'USB bloqueado', hint: 'Toque para permitir o USB', online: false, action: 'connectUsb' };
    if (s.serviceRunning === false) return { key: 'stopped', label: 'Serviço parado', hint: 'Abra o OMEGAS de novo para iniciar o serviço', online: false };
    return { key: 'nocable', label: 'Sem cabo', hint: 'Conecte o cabo USB na ECU', online: false };
  }

  /** Rótulos únicos das fases do Refino (Refino, Agora e qualquer outro lugar que fale da fase). */
  const PHASE_LABELS = {
    SEM_ECU: 'Sem ECU', LENDO_ECU: 'Lendo a ECU', ECU_TRABALHANDO: 'ECU no automático', COLETANDO_NOSSOS: 'Medindo',
    PROPOSTA_PRONTA: 'Curva pronta', VERIFICANDO: 'Medindo', RESTAURAR_TRECHO: 'Piorou em um trecho', ESTAVEL: 'Estável', TENTATIVA_ENCERRADA: 'Pausado',
  };
  /** Prazo vencido com proposta pronta: a proposta continua válida, então a fase segue "Curva pronta". */
  function phaseLabel(phase, expiredFrom) {
    if (phase === 'TENTATIVA_ENCERRADA' && expiredFrom === 'PROPOSTA_PRONTA') return PHASE_LABELS.PROPOSTA_PRONTA;
    return PHASE_LABELS[phase] || DASH;
  }

  /** Explica por que os pontos do GNV recomeçaram (a causa vem do Kotlin, em código). */
  const GAS_RESET_REASON = {
    AUTOMATCH_NATIVO: 'a ECU trocou a curva no automático',
    CURVA_K_GRAVADA: 'a Curva K foi gravada',
    MAPA_K_GRAVADO: 'o Mapa K foi gravado',
    CURVA_K_MUDOU_FORA_DO_APP: 'a curva mudou fora do app',
    INICIO: 'o app acabou de abrir',
    CARREGADO: 'o app reabriu e leu o arquivo salvo',
  };
  function gasResetNote(reason, atMs) {
    const text = GAS_RESET_REASON[String(reason || '')];
    if (!text) return '';
    const at = finite(atMs);
    const clock = at && at > 0 ? new Date(at).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }) : '';
    return `Os pontos do OMEGAS no GNV recomeçaram${clock ? ' às ' + clock : ''} porque ${text} (o GNV medido com a curva antiga não vale para a nova).`;
  }

  /** Escritas no DOM só quando o valor muda (mesmo valor = zero mutação, zero recálculo de estilo). */
  function setAttrIfChanged(node, name, value) {
    if (!node) return false;
    const next = String(value);
    if (node.getAttribute(name) === next) return false;
    node.setAttribute(name, next);
    return true;
  }
  function removeAttrIfPresent(node, name) {
    if (!node || !node.hasAttribute(name)) return false;
    node.removeAttribute(name);
    return true;
  }
  function setDataIfChanged(node, key, value) {
    if (!node) return false;
    const next = String(value);
    if (node.dataset[key] === next) return false;
    node.dataset[key] = next;
    return true;
  }
  function setTextIfChanged(node, value) {
    if (!node) return false;
    const next = String(value);
    if (node.textContent === next) return false;
    node.textContent = next;
    return true;
  }

  ns.DisplayRules = {
    setAttrIfChanged, removeAttrIfPresent, setDataIfChanged, setTextIfChanged,
    DASH, finite, number, fmt, escapeHtml, clamp, ms, msUnit, msBand, bar, barUnit, kValue, rpm, percentFraction, gapPercent, plural, ageText, ageSinceMs, phaseLabel, PHASE_LABELS, connectionState, count, ratio, fuelLabel, durationLabel, bytesLabel, megabytesLabel,
    ageLabel, sessionDate, OPERATION_WORDING, failureKind, failureText, gasResetNote, GAS_RESET_REASON,
    offRouteTelemetryExpired, OFF_ROUTE_TELEMETRY_MAX_MS, overlayState, shouldPromptOverlay,
  };
})(typeof window !== 'undefined' ? window : globalThis);
