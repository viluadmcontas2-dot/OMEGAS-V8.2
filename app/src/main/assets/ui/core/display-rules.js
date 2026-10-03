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
   * Quantas decisões pendentes mostrar no menu. null = ainda não dá para saber (a ciência não
   * respondeu e não há aviso do refino): a tela não mexe no número, não escreve 0.
   * Pendente = ajuste acionável de Mapa K/Curva K + 1 se o refino tem curva pronta ou trecho a restaurar.
   */
  function pendingSuggestionCount(items, refinementReady) {
    const refinement = refinementReady ? 1 : 0;
    if (!Array.isArray(items)) return refinement > 0 ? refinement : null;
    const actionable = items.filter(item => item && item.lifecycle === 'PENDING' && item.actionable === true &&
      (item.target === 'MAP_K' || item.target === 'CURVE_K')).length;
    return actionable + refinement;
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
    ageLabel, sessionDate, pendingSuggestionCount, gasResetNote, GAS_RESET_REASON,
  };
})(typeof window !== 'undefined' ? window : globalThis);
