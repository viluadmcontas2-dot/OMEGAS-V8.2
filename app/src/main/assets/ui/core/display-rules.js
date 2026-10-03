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
    if (value.includes('CUTOFF')) return 'CUTOFF';
    if (value.includes('TRANS')) return 'TRANSIÇÃO';
    if (value.includes('OFF') || value.includes('DESLIG')) return 'DESLIGADO';
    return value;
  }

  function durationLabel(ms) {
    const value = finite(ms);
    if (value === null || value < 0) return DASH;
    const minutes = Math.floor(value / 60000);
    const seconds = Math.floor((value % 60000) / 1000);
    if (minutes >= 60) return `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
    return `${minutes}m ${seconds}s`;
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
    stages: ['Foto antes', 'Escrita', 'ACK', 'Conferindo na ECU'],
    writing: 'Gravando na ECU…',
    doneTitle: what => `Gravado · ${what} conferido na ECU`,
    doneDetail: 'A ECU confirmou a gravação (ACK e readback). A tela será relida.',
    failedTitle: 'Não foi gravado',
    failedDetail: 'A ECU não confirmou toda a operação. Releitura obrigatória.',
    back: 'Voltar',
    undo: 'Desfazer',
  };

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
    if (s.permissionGranted !== true) return { key: 'needs-permission', title: 'Precisa de autorização', help: 'O balão mostra combustível, RPM, Petrol Inj., MAP e gás por cima de outros apps (mapa, música). Toque em Autorizar: o Android abre a tela certa, marque o OMEGAS e volte.' };
    if (s.requestedEnabled === true) return { key: 'on', title: 'Ligada', help: 'Aparece quando você sai do OMEGAS e nunca cobre o app. Arraste para mover e toque no Ω para abrir ou fechar.' };
    return { key: 'off', title: 'Desligada', help: 'Autorizada. Toque em Ativar para mostrar o balão quando você sair do OMEGAS.' };
  }

  /** Pergunta uma única vez, no primeiro uso, se quer ligar o balão; quem já decidiu não é incomodado. */
  function shouldPromptOverlay(status, alreadyPrompted) {
    const s = status || {};
    return alreadyPrompted !== true && s.testHarness !== true && s.supported === true && s.permissionGranted !== true && s.requestedEnabled !== true;
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
    return `Nossos pontos de GNV recomeçaram${clock ? ' às ' + clock : ''} porque ${text} (o GNV medido com a curva antiga não vale para a nova).`;
  }

  ns.DisplayRules = {
    DASH, finite, number, count, ratio, fuelLabel, durationLabel, bytesLabel, megabytesLabel,
    ageLabel, sessionDate, OPERATION_WORDING, gasResetNote, GAS_RESET_REASON,
    offRouteTelemetryExpired, OFF_ROUTE_TELEMETRY_MAX_MS, overlayState, shouldPromptOverlay,
  };
})(typeof window !== 'undefined' ? window : globalThis);
