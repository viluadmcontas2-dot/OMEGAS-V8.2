"""Análise offline de apagões × ANTI_STALLO: ordem de amostras, nunca decisões por relógio.

Só lê dados já gravados. Não fala com a ECU. t_ms aparece apenas no relatório.
Uso: python3 -I tools/sessoes/apagoes_x_antistallo.py [--extra events_0001.jsonl] [--out arquivo.md]
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# ANTI_STALLO de fábrica (ECU do dono). Ordem campo -> rótulo é INFERIDA.
RULE_MAX_RPM = 1600      # [0] "Max giri attivazione anti-stallo"
RULE_DELTA_RPM = 100     # [1] "Delta giri attivazione anti-stallo"
# [2]=1500 (espera, unidade DESCONHECIDA) [3]=2 histerese [4]=20 queda que desativa: não usados.

LOOKBACK = 8             # amostras anteriores (~2 s nominais a ~250 ms; só ordem decide)
STALL_FROM_RPM = 500     # rpm estava em marcha
STALL_TO_RPM = 100       # rpm colapsou
MAP_BAIXO_BAR = 0.35     # desaceleração: MAP baixo
INJECAO_ZERO_MS = 0.3    # mesmo limiar de "corte" das evidências anteriores
OUTCOME_SAMPLES = 4      # amostras seguintes olhadas na discriminação H1 x H2

_CUTOFF_BYTES = (148, 149, 180, 181)
_NOMES_ESTADO = {145: '0x91', 148: '0x94', 8: '0x08', 12: '0x0C', 128: 'GASOLINA_ATIVA'}


def family(row):
    b = row.get('fuel_byte')
    if b is not None:
        b = int(b)
        if b == 144:
            return 'GNV'
        if b in _CUTOFF_BYTES:
            return 'CUTOFF'
        if b == 128:
            return 'GASOLINA'
    fuel = row.get('fuel')
    if fuel in ('GNV', 'CUTOFF', 'GASOLINA'):
        return fuel
    if fuel == 'DESLIGADO':
        return 'OFF'
    return 'OUTRO'


def estado_nome(row):
    b = row.get('fuel_byte')
    if b is None:
        return str(row.get('state'))
    b = int(b)
    return _NOMES_ESTADO.get(b, '0x%02X' % b)


def _sessions(rows):
    cur, key = [], object()
    for r in rows:
        k = r.get('sessao')
        if cur and k != key:
            yield cur
            cur = []
        key = k
        cur.append(r)
    if cur:
        yield cur


def _rpm(r):
    v = r.get('rpm')
    return None if v is None else float(v)


def _small(x):
    return int(x) if float(x).is_integer() else round(float(x), 3)


def evaluate_rule(window, max_rpm=None, delta=None):
    """A regra [max_rpm, delta] deveria ter atuado? Só usa ordem das amostras."""
    max_rpm = RULE_MAX_RPM if max_rpm is None else max_rpm
    delta = RULE_DELTA_RPM if delta is None else delta
    valid = [r for r in window if _rpm(r) is not None and _rpm(r) >= STALL_TO_RPM]
    gap = any(w.get('sample_state') == 'TELEMETRY_GAP' or _rpm(w) is None for w in window)
    max_drop, hit = 0, None
    for prev, cur in zip(window, window[1:]):
        p, c = _rpm(prev), _rpm(cur)
        if p is None or c is None or c < STALL_TO_RPM:
            continue
        drop = p - c
        if drop > max_drop:
            max_drop = drop
        if hit is None and c <= max_rpm and drop >= delta:
            hit = (p, c)
    rpms = [_rpm(r) for r in valid]
    out = {'max_rpm': max_rpm, 'delta': delta, 'max_drop': _small(max_drop),
           'min_rpm': _small(min(rpms)) if rpms else None, 'samples': len(valid)}
    if hit:
        out.update(answer='SIM', reason='rpm %s -> %s: rpm<=%s e queda>=%s' % (_small(hit[0]), _small(hit[1]), max_rpm, delta))
    elif len(valid) < 2 or gap:
        out.update(answer='INDETERMINADO', reason='janela com menos de 2 amostras de rpm ou com lacuna de telemetria')
    else:
        out.update(answer='NAO', reason='nenhum par com rpm<=%s e queda>=%s (maior queda %s)' % (max_rpm, delta, _small(max_drop)))
    return out


def _window_info(rows, lo, hi):
    w = rows[lo:hi]
    rpms = [_rpm(r) for r in w]
    maps = [r.get('map_bar') for r in w if r.get('map_bar') is not None]
    ts = [r.get('t_ms') for r in w if r.get('t_ms') is not None]
    return {
        'rpm': [None if v is None else _small(v) for v in rpms],
        'map_min': min(maps) if maps else None, 'map_max': max(maps) if maps else None,
        'estados': [estado_nome(r) for r in w],
        't_ms_span': _small(ts[-1] - ts[0]) if len(ts) > 1 else None,   # só relatório
    }


def _is_cutoff_like(row):
    return family(row) == 'CUTOFF'


def _injecao_zero(row):
    gas = row.get('gas_ms')
    pet = row.get('petrol_ms')
    gas_zero = gas is None or float(gas) < INJECAO_ZERO_MS
    if _is_cutoff_like(row):
        return gas_zero
    return gas_zero and pet is not None and float(pet) < INJECAO_ZERO_MS


def classify_exit(rows, i, max_rpm, delta):
    cur = rows[i]
    m = cur.get('map_bar')
    if m is not None and float(m) <= MAP_BAIXO_BAR and _injecao_zero(cur):
        return 'CORTE_DESACELERACAO'
    c = _rpm(cur)
    if c is not None and c <= max_rpm:
        for k in range(max(1, i - 2), i + 1):
            p, q = _rpm(rows[k - 1]), _rpm(rows[k])
            if p is not None and q is not None and q <= max_rpm and p - q >= delta:
                return 'ANTI_APAGAO_CANDIDATO'
    return 'OUTRA_OU_INDETERMINADA'


def detect_events(rows, max_rpm=None, delta=None):
    max_rpm = RULE_MAX_RPM if max_rpm is None else max_rpm
    delta = RULE_DELTA_RPM if delta is None else delta
    events = []
    for sess in _sessions(rows):
        collapsed = False
        for i, cur in enumerate(sess):
            c = _rpm(cur)
            if c is None:
                continue
            if c >= STALL_TO_RPM:
                collapsed = False
            lo = max(0, i - LOOKBACK)
            # (a) apagão: rpm de >=500 para <100, chave não registrada como desligada
            if c < STALL_TO_RPM and not collapsed and i > 0:
                collapsed = True
                back = [(_rpm(r), r) for r in sess[lo:i]]
                peak = max((v for v, _ in back if v is not None), default=None)
                if peak is not None and peak >= STALL_FROM_RPM and cur.get('ignition_on') is not False:
                    fams = [family(r) for _, r in back]
                    last = next((f for f in reversed(fams) if f != 'CUTOFF'), None)
                    if last == 'GNV':
                        kind = 'APAGAO_GNV'
                    elif 'GNV' in fams:
                        kind = 'APAGAO_APOS_SAIDA_GNV'
                    else:
                        kind = 'APAGAO_FORA_DO_GNV'
                    events.append({
                        'kind': kind,
                        'sessao': cur.get('sessao'), 'index': i, 't_ms': cur.get('t_ms'),
                        'rpm_pico': _small(peak),
                        'chave': 'LIGADA' if cur.get('ignition_on') else 'SEM_REGISTRO',
                        'window': sess[lo:i], 'info': _window_info(sess, lo, i),
                        'rule': evaluate_rule(sess[lo:i], max_rpm, delta)})
                continue
            # (b) saída GNV -> outro estado com motor em marcha
            if i > 0 and family(sess[i - 1]) == 'GNV' and family(cur) not in ('GNV', 'OFF') \
                    and c >= STALL_FROM_RPM:
                events.append({
                    'kind': 'SAIDA_GNV', 'sessao': cur.get('sessao'), 'index': i, 't_ms': cur.get('t_ms'),
                    'estado_destino': estado_nome(cur), 'causa': classify_exit(sess, i, max_rpm, delta),
                    'window': sess[lo:i], 'info': _window_info(sess, lo, i),
                    'rule': evaluate_rule(sess[lo:i + 1], max_rpm, delta)})
    return events


def trigger_outcomes(rows, max_rpm=None, delta=None):
    """Dado de discriminação H1 x H2: o que acontece nas amostras seguintes ao gatilho da regra
    (GNV, rpm<=max, queda>=delta) comparado com GNV de mesma rpm sem queda (controle)."""
    max_rpm = RULE_MAX_RPM if max_rpm is None else max_rpm
    delta = RULE_DELTA_RPM if delta is None else delta
    zero = {'APAGOU': 0, 'SAIU_DO_GNV': 0, 'CORTE': 0, 'FICOU_NO_GNV': 0}
    res = {'gatilho': dict(zero), 'controle': dict(zero)}
    for sess in _sessions(rows):
        n = len(sess)
        for i in range(1, n - OUTCOME_SAMPLES):
            cur, prev = sess[i], sess[i - 1]
            c, p = _rpm(cur), _rpm(prev)
            if c is None or p is None or family(cur) != 'GNV' or family(prev) != 'GNV':
                continue
            if not (STALL_FROM_RPM <= c <= max_rpm):
                continue
            grp = 'gatilho' if p - c >= delta else ('controle' if p - c < delta // 2 else None)
            if grp is None:
                continue
            out = 'FICOU_NO_GNV'
            for nxt in sess[i + 1:i + 1 + OUTCOME_SAMPLES]:
                r, f = _rpm(nxt), family(nxt)
                if r is not None and r < STALL_TO_RPM:
                    out = 'APAGOU'
                elif f == 'CUTOFF':
                    out = 'CORTE'
                elif f in ('GASOLINA', 'OUTRO'):
                    out = 'SAIU_DO_GNV'
                else:
                    continue
                break
            res[grp][out] += 1
    return res


# ---------------------------------------------------------------- dados

def load_corpus_rows():
    import corpus
    return list(corpus.telemetria())


def load_extra_rows(path):
    rows, stalls = [], []
    for line in open(path, encoding='utf-8'):
        d = json.loads(line)
        if d['type'] == 'telemetry':
            t = d['data']
            rows.append({'sessao': 'extra:' + os.path.basename(os.path.dirname(os.path.abspath(path))),
                         't_ms': d['recordedAtMs'], 'rpm': t.get('rpm'), 'fuel': t.get('fuel'),
                         'state': t.get('state'), 'fuel_byte': t.get('fuel_byte'),
                         'gas_ms': t.get('gas_ms_diagnostic'), 'petrol_ms': t.get('petrol_ms'),
                         'map_bar': t.get('load_bar'), 'dyn': t.get('dynamic_correction'),
                         'sample_state': t.get('sample_state')})
        elif d['type'] == 'engine_stall':
            stalls.append(d['data'])
    return rows, stalls


# ---------------------------------------------------------------- relatório

def _pct(n, d):
    return '—' if not d else '%.1f%%' % (100.0 * n / d)


def _contagens(events):
    from collections import Counter
    return Counter(e['kind'] for e in events)


def build_report(extra_path=None):
    rows = load_corpus_rows()
    sources = [('corpus tests/fixtures/sessoes-uteis', rows, [])]
    if extra_path:
        er, stalls = load_extra_rows(extra_path)
        sources.append(('sessão 2026-10-16 18-38 (arquivo externo)', er, stalls))
    all_rows = [r for _, rs, _ in sources for r in rs]
    events = detect_events(all_rows)
    ap = [e for e in events if e['kind'].startswith('APAGAO')]
    ex = [e for e in events if e['kind'] == 'SAIDA_GNV']
    outc = trigger_outcomes(all_rows)
    L = []
    w = L.append
    w('# Apagões × ANTI_STALLO — o que os dados deixam concluir (2026-10-18)')
    w('')
    w('Gerado por `tools/sessoes/apagoes_x_antistallo.py` (determinístico: a mesma entrada gera o mesmo texto). '
      'Só leitura de gravações antigas; nada foi escrito na ECU. Nenhuma decisão usa relógio: os eventos e as janelas '
      'são por ordem de amostras; `t_ms` só aparece como informação.')
    w('')
    w('## Fontes')
    for nome, rs, st in sources:
        w('- %s: %d amostras, %d sessões, %d eventos `engine_stall` do app.' % (nome, len(rs), len(list(_sessions(rs))), len(st)))
    w('')
    w('## Hipóteses (escritas ANTES de olhar os eventos)')
    w('- **H1** a regra ANTI_STALLO está ligada e é fraca: atua, mas não evita o apagão.')
    w('- **H2** a regra está desligada (a caixa "Abilitazione anti-stallo" não está marcada).')
    w('- **H3** o apagão acontece fora do alcance da regra (rpm acima de 1600 ou sem queda de 100 rpm antes).')
    w('')
    w('## Definições usadas (testadas em `tests/test_apagoes_antistallo.py`)')
    w('- **Apagão em GNV**: em uma sessão, rpm cai de ≥%d (em alguma das %d amostras anteriores) para <%d, sem registro de chave desligada, '
      'e o último combustível fora de corte antes da queda é GNV (byte 0x90). A chave NÃO é gravada nas sessões: por isso um apagão '
      'e um desligar de chave com giro em queda ficam parecidos nos dados (INFERIDO como apagão).' % (STALL_FROM_RPM, LOOKBACK, STALL_TO_RPM))
    w('- **Apagão após saída do GNV**: igual ao apagão, mas houve GNV nas amostras anteriores e o último combustível fora de corte já não era GNV '
      '(ex.: estados 0x91, 0x08, 0x09, 0x0C, gasolina). É o caso em que o carro já tinha deixado o GNV e ainda assim apagou. '
      '**APAGAO_FORA_DO_GNV** = sem GNV na janela (não é assunto desta análise).')
    w('- **Saída GNV→outro**: amostra em GNV seguida de amostra fora do GNV com rpm ≥%d. Destino por byte de estado: 0x91, 0x94 (corte), 0x08, 0x0C, GASOLINA_ATIVA (0x80) ou outro.' % STALL_FROM_RPM)
    w('- **Causa da saída**: CORTE_DESACELERACAO = MAP ≤ %.2f bar e injeção ≈ 0 (gas_ms e, fora do estado de corte, também petrol_ms < %.1f ms); '
      'ANTI_APAGAO_CANDIDATO = rpm ≤ %d e queda ≥ %d entre amostras vizinhas nas 3 últimas amostras (é o que a regra pediria, mas NÃO prova que foi a regra); '
      'o resto é OUTRA_OU_INDETERMINADA.' % (MAP_BAIXO_BAR, INJECAO_ZERO_MS, RULE_MAX_RPM, RULE_DELTA_RPM))
    w('- **A regra deveria ter atuado?** SIM se, nas %d amostras antes do apagão, existe par vizinho com rpm ≤ %d e queda ≥ %d; '
      'NAO se há ≥2 amostras de rpm sem lacuna e nenhum par serve; INDETERMINADO caso contrário.' % (LOOKBACK, RULE_MAX_RPM, RULE_DELTA_RPM))
    w('')
    w('## Contagem de eventos')
    cnt = _contagens(events)
    w('')
    w('| Evento | Quantidade |')
    w('|---|---|')
    for k in ('APAGAO_GNV', 'APAGAO_APOS_SAIDA_GNV', 'APAGAO_FORA_DO_GNV', 'SAIDA_GNV'):
        w('| %s | %d |' % (k, cnt.get(k, 0)))
    w('')
    w('## Apagões, evento a evento')
    w('')
    if not ap:
        w('Nenhum apagão encontrado nas fontes carregadas.')
    else:
        w('| # | Sessão | Tipo | rpm pico | rpm das amostras anteriores | Maior queda | MAP (mín–máx) | Estados anteriores | A regra deveria ter atuado? |')
        w('|---|---|---|---|---|---|---|---|---|')
        for n, e in enumerate(ap, 1):
            inf, ru = e['info'], e['rule']
            w('| %d | %s | %s | %s | %s | %s | %s–%s | %s | **%s** — %s |' % (
                n, str(e['sessao'])[:12], e['kind'], e['rpm_pico'],
                ', '.join('—' if v is None else str(v) for v in inf['rpm']), ru['max_drop'],
                inf['map_min'], inf['map_max'], ' '.join(inf['estados']), ru['answer'], ru['reason']))
    w('')
    for kind in ('APAGAO_GNV', 'APAGAO_APOS_SAIDA_GNV', 'APAGAO_FORA_DO_GNV'):
        resp = {}
        for e in ap:
            if e['kind'] == kind:
                resp[e['rule']['answer']] = resp.get(e['rule']['answer'], 0) + 1
        w('- Resumo %s: %s.' % (kind, ', '.join('%s=%d' % kv for kv in sorted(resp.items())) or 'nenhum'))
    w('')
    w('Observação: várias janelas têm o mesmo rpm repetido (ex.: 541, 541, 541); é quadro repetido/parado antes do apagão, não queda lenta. '
      'Quando a queda é de 1 amostra só (ex.: de 1000 para <100) a regra tem uma única observação para agir.')
    w('')
    w('## Cruzamento com os `engine_stall` registrados pelo app')
    for nome, rs, st in sources:
        if not st and nome.startswith('corpus'):
            w('- %s: o corpus compacto não guarda eventos `engine_stall` (só K, snapshots e logs). Nada a cruzar.' % nome)
        else:
            for s in st:
                w('- %s: `%s` rpmBefore=%s, minRpm=%s, afterCutoff=%s. %s' % (
                    nome, s.get('kind'), s.get('rpmBefore'), s.get('minRpm'), s.get('afterCutoff'),
                    'rpm mínimo ≥ %d: NÃO é apagão pela definição acima (é quase-apagão).' % STALL_TO_RPM
                    if (s.get('minRpm') or 0) >= STALL_TO_RPM else 'rpm abaixo de %d.' % STALL_TO_RPM))
    w('')
    w('## Saídas do GNV por causa')
    w('')
    from collections import Counter
    por = Counter((e['estado_destino'], e['causa']) for e in ex)
    w('| Estado de destino | Causa | Quantidade |')
    w('|---|---|---|')
    for (est, causa), n in sorted(por.items(), key=lambda kv: (kv[0][0], kv[0][1])):
        w('| %s | %s | %d |' % (est, causa, n))
    w('')
    tot = len(ex)
    cc = Counter(e['causa'] for e in ex)
    w('Total de saídas: %d (%s). Observação: "candidato" é só o formato do gatilho; corte de desaceleração e '
      'troca por outro motivo (GIRI_MIN_CAMBIO etc.) também podem coincidir com rpm baixa e caindo.' % (
          tot, '; '.join('%s %s' % (k, _pct(v, tot)) for k, v in sorted(cc.items()))))
    w('')
    w('## O que decide H1 × H2 (e se o corpus decide)')
    w('')
    g, c = outc['gatilho'], outc['controle']
    sg, sc = sum(g.values()), sum(c.values())
    w('Gatilho da regra = GNV, %d ≤ rpm ≤ %d, queda ≥ %d entre amostras vizinhas. Controle = GNV na mesma faixa de rpm com queda < %d. '
      'Resultado nas %d amostras seguintes:' % (STALL_FROM_RPM, RULE_MAX_RPM, RULE_DELTA_RPM, RULE_DELTA_RPM // 2, OUTCOME_SAMPLES))
    w('')
    w('| Grupo | n | Saiu do GNV | Corte | Apagou | Ficou no GNV |')
    w('|---|---|---|---|---|---|')
    for nome, d, s in (('gatilho', g, sg), ('controle', c, sc)):
        w('| %s | %d | %d (%s) | %d (%s) | %d (%s) | %d (%s) |' % (
            nome, s, d['SAIU_DO_GNV'], _pct(d['SAIU_DO_GNV'], s), d['CORTE'], _pct(d['CORTE'], s),
            d['APAGOU'], _pct(d['APAGOU'], s), d['FICOU_NO_GNV'], _pct(d['FICOU_NO_GNV'], s)))
    w('')
    rg = (g['SAIU_DO_GNV'] / sg) if sg else None
    rc = (c['SAIU_DO_GNV'] / sc) if sc else None
    if rg is not None and rc:
        w('Número: a saída do GNV depois do gatilho é %.1f vezes a do controle (%s contra %s).' % (rg / rc, _pct(g['SAIU_DO_GNV'], sg), _pct(c['SAIU_DO_GNV'], sc)))
        w('')
    w('Leitura: se a regra estivesse ligada e atuando, esperaríamos MUITO mais "saiu do GNV" depois do gatilho do que no controle. '
      'Se for parecido, os dados são compatíveis com H2 (desligada) ou com uma regra que não troca de combustível. '
      'Mas a troca também acontece por outros motivos, o gatilho de queda de rpm também aparece em desacelerações comuns e a unidade da '
      'espera (campo [2]) é desconhecida; então esta comparação só ORIENTA, não decide.')
    w('')
    w('**O corpus NÃO permite decidir H1 × H2.** Dado que decide: (1) o valor da caixa "Abilitazione anti-stallo" lido da ECU '
      '(o endereço dela é DESCONHECIDO; precisa de captura do ProgBase original marcando/desmarcando só essa caixa, ou de leitura do endereço certo); '
      '(2) o `param_snapshot` da conexão (já gravado a cada conexão) mostrando ANTI_STALLO; (3) uma captura com os quadros `48 01` ao redor de um '
      'apagão ou quase-apagão para ver se houve troca de combustível dentro da janela da regra. Sem (1), H1 e H2 dão a mesma coisa nos dados de telemetria: o motor apagou depois de a rpm cair.')
    w('')
    w('## O que é PROVADO / INFERIDO / DESCONHECIDO')
    w('')
    w('| Afirmação | Grau |')
    w('|---|---|')
    w('| Os valores de fábrica lidos da ECU do dono são 1600·100·1500·2·20 | PROVADO (leitura Portmon) |')
    w('| Cada apagão listado acima caiu de ≥%d para <%d rpm nas gravações, pela definição acima | PROVADO (nos dados) |' % (STALL_FROM_RPM, STALL_TO_RPM))
    w('| Os números "a regra deveria ter atuado" da tabela | PROVADO como conta sobre a ordem das amostras; vale só se a ordem campo→rótulo for a certa |')
    w('| [0]=rpm máx., [1]=queda que ativa, [2]=espera, [3]=histerese, [4]=queda que desativa | INFERIDO (numeração dos rótulos da tela do original) |')
    w('| Que o evento seja apagão e não chave desligada | INFERIDO (a chave não é gravada) |')
    w('| Que a rotação baixa e caindo seja o gatilho real da regra | INFERIDO |')
    w('| Se a regra está ligada | DESCONHECIDO |')
    w('| Unidade do campo [2] (espera) e onde fica a caixa de habilitação | DESCONHECIDO |')
    w('| Se H1, H2 ou H3 vale | DESCONHECIDO (H3 só é compatível com os apagões cuja resposta é NAO; nos SIM, H1 e H2 continuam abertas) |')
    w('')
    w('Limite: poucos apagões, um carro. Não generalizar.')
    return '\n'.join(L) + '\n'


def main(argv):
    extra, out = None, None
    it = iter(argv)
    for a in it:
        if a == '--extra':
            extra = next(it)
        elif a == '--out':
            out = next(it)
        else:
            raise SystemExit('uso: apagoes_x_antistallo.py [--extra events.jsonl] [--out arquivo.md]')
    text = build_report(extra)
    if out:
        with open(out, 'w', encoding='utf-8', newline='\n') as fh:
            fh.write(text)
    else:
        sys.stdout.write(text)


if __name__ == '__main__':
    main(sys.argv[1:])
