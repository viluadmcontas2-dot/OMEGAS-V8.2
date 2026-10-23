"""Analisador offline dos resumos de sessão do app (formato omegas-session-summary-v1; ver docs/EXPORT-SESSOES.md).

Lê pastas e zips (inclusive zip dentro de zip), deduplica por sessão, recalcula os tanques (ciclos entre abastecimentos),
agrupa por mapa/curva, compara mapas SÓ quando a cobertura é comparável e gera CSV + JSON pronto para o Graphify
(nós: sessão, tanque, mapa, dia; arestas: pertence_a, usa_mapa, abasteceu_depois_de).

Uso: python3 -I tools/sessoes/analisar_resumos.py <pasta-ou-zip>... --saida <pasta>
Desconhecido é vazio/None, nunca 0. Sem relógio no resultado: a mesma entrada dá sempre a mesma saída.
"""
import argparse, csv, hashlib, io, json, os, sys, zipfile
from datetime import datetime, timezone

SCHEMA = 'omegas-session-summary-v1'
MAX_JSON = 2 * 1024 * 1024
MAX_SERIE = 8 * 1024 * 1024
MAX_MEMBROS = 20000
REFUEL_DROP = 40            # igual ao app (queda do nível bruto = abastecimento)
MIN_NIVEL = 20              # menos que isso de nível consumido não sustenta razão
MIN_KM_GNV = 5.0            # cobertura mínima para comparar mapas
MIN_BANDA_S = 60            # segundos mínimos por faixa de rpm
TOL_VEL, TOL_RPM = 0.30, 0.20
BANDAS = (('baixa', 0, 1500), ('media', 1500, 2500), ('alta', 2500, 10 ** 9))
FONTES_KM = ('MM', 'GPS')
FONTES = ('MM', 'GPS', 'RPM_PROXY')


# ----------------------------------------------------------------------------------------------------------- leitura
def _ler_membros_zip(dados, origem, nivel=0):
    """Gera (caminho, bytes) dos .json/.csv de um zip; zip dentro de zip só um nível; limites contra zip bomba."""
    try:
        z = zipfile.ZipFile(io.BytesIO(dados))
    except zipfile.BadZipFile:
        return
    with z:
        infos = z.infolist()[:MAX_MEMBROS]
        for info in infos:
            nome = info.filename
            baixo = nome.lower()
            if info.is_dir():
                continue
            if baixo.endswith('.zip') and nivel == 0 and info.file_size <= 64 * 1024 * 1024:
                yield from _ler_membros_zip(z.read(info), origem + '!' + nome, 1)
            elif baixo.endswith('.json') and info.file_size <= MAX_JSON:
                yield origem + '!' + nome, z.read(info)
            elif baixo.endswith('.csv') and info.file_size <= MAX_SERIE:
                yield origem + '!' + nome, z.read(info)


def _varrer(caminhos):
    """Gera (caminho, bytes) de todo .json/.csv/.zip pequeno encontrado."""
    for base in caminhos:
        if os.path.isdir(base):
            for raiz, pastas, arquivos in os.walk(base):
                pastas.sort()
                for nome in sorted(arquivos):
                    caminho = os.path.join(raiz, nome)
                    baixo = nome.lower()
                    try:
                        tam = os.path.getsize(caminho)
                        if baixo.endswith('.zip'):
                            with open(caminho, 'rb') as fh:
                                yield from _ler_membros_zip(fh.read(), caminho)
                        elif (baixo.endswith('.json') and tam <= MAX_JSON) or (baixo.endswith('.csv') and tam <= MAX_SERIE):
                            with open(caminho, 'rb') as fh:
                                yield caminho, fh.read()
                    except OSError:
                        continue
        elif os.path.isfile(base):
            with open(base, 'rb') as fh:
                dados = fh.read()
            if base.lower().endswith('.zip'):
                yield from _ler_membros_zip(dados, base)
            else:
                yield base, dados


def _pasta_de(caminho):
    if '!' in caminho:
        zip_part, _, membro = caminho.rpartition('!')
        return zip_part + '!' + os.path.dirname(membro)
    return os.path.dirname(caminho)


def ler_copias(caminhos):
    """Cada cópia = {resumo, sha, origem, serie}; a série é o `series_1hz*.csv` da mesma pasta (ou do mesmo zip)."""
    arquivos = list(_varrer(caminhos))
    series = {}
    for caminho, dados in arquivos:
        if os.path.basename(caminho.rpartition('!')[2]).lower().startswith('series_1hz') and caminho.lower().endswith('.csv'):
            series.setdefault(_pasta_de(caminho), dados.decode('utf-8', 'replace'))
    copias = []
    for caminho, dados in arquivos:
        if not caminho.lower().endswith('.json'):
            continue
        try:
            resumo = json.loads(dados.decode('utf-8'))
        except (ValueError, UnicodeDecodeError):
            continue
        if not isinstance(resumo, dict) or resumo.get('schema') != SCHEMA:
            continue
        copias.append({'resumo': resumo, 'sha': hashlib.sha256(dados).hexdigest(), 'origem': caminho,
                       'serie': series.get(_pasta_de(caminho))})
    return copias


# -------------------------------------------------------------------------------------------------------- utilidades
def _g(d, caminho, padrao=None):
    for k in caminho.split('.'):
        if not isinstance(d, dict) or d.get(k) is None:
            return padrao
        d = d[k]
    return d


def _chave(resumo):
    return _g(resumo, 'identity.sessionUuid') or _g(resumo, 'identity.sessionId')


def _num(v):
    return v if isinstance(v, (int, float)) and not isinstance(v, bool) else None


def _soma(valores):
    vs = [v for v in valores if v is not None]
    return round(sum(vs), 6) if vs else None


def _dia(ms):
    return datetime.fromtimestamp(ms / 1000.0, tz=timezone.utc).strftime('%Y-%m-%d') if ms is not None else None


def para_csv(linhas):
    if not linhas:
        return ''
    campos = []
    for l in linhas:
        for k in l:
            if k not in campos:
                campos.append(k)
    out = io.StringIO()
    w = csv.writer(out, lineterminator='\n')
    w.writerow(campos)
    for l in linhas:
        w.writerow(['' if l.get(k) is None else (json.dumps(l[k], ensure_ascii=False, sort_keys=True) if isinstance(l[k], (dict, list)) else l[k]) for k in campos])
    return out.getvalue()


# ---------------------------------------------------------------------------------------------------------- dedupe
def deduplicar(copias):
    """Remove cópias idênticas (mesma sessão + mesmo SHA) e escolhe uma versão por sessão (final > mais eventos > mais longa)."""
    vistas, identicas = set(), 0
    por_sessao = {}
    for c in copias:
        chave = _chave(c['resumo'])
        if not chave:
            continue
        if (chave, c['sha']) in vistas:
            identicas += 1
            continue
        vistas.add((chave, c['sha']))
        por_sessao.setdefault(chave, []).append(c)
    unicas, descartadas = [], 0
    for chave in sorted(por_sessao):
        versoes = sorted(por_sessao[chave], key=lambda c: (
            bool(c['resumo'].get('final')), _num(_g(c['resumo'], 'identity.events')) or 0,
            _num(_g(c['resumo'], 'identity.elapsedMs')) or 0, c['serie'] is not None, c['sha']), reverse=True)
        unicas.append(versoes[0])
        descartadas += len(versoes) - 1
    return unicas, identicas, descartadas


# ---------------------------------------------------------------------------------------------------------- sessões
def _linha_sessao(c):
    r = c['resumo']
    inicio = _num(_g(r, 'identity.startMs'))
    suspeito = bool(_g(r, 'clock.clockSuspect', False))
    ref = _num(_g(r, 'clock.gpsReferenceOffsetMs'))
    if not suspeito:
        corrigido = inicio
    else:
        corrigido = inicio + ref if (inicio is not None and ref is not None) else None
    primario = _g(r, 'movement.primary.source')
    seg = r.get('tankSegments') or []
    mapa, curva = _g(r, 'mapCurve.map.hashLast'), _g(r, 'mapCurve.curve.hashLast')
    fonte_km = next((f for f in FONTES_KM if _num(_g(r, f'movement.bySource.{f}.kmOnGnv')) is not None), None)
    return {
        'sessionId': _g(r, 'identity.sessionId'), 'sessionUuid': _g(r, 'identity.sessionUuid'),
        'startMs': inicio, 'startMsCorrigido': corrigido, 'relogioSuspeito': suspeito,
        'motivosRelogio': ','.join(_g(r, 'clock.suspectReasons', []) or []) or None,
        'dia': _dia(corrigido if corrigido is not None else inicio), 'duracaoMs': _num(_g(r, 'identity.elapsedMs')),
        'fonteMovimento': primario, 'movimentoProxy': _g(r, 'movement.primary.proxy'),
        'movendoMs': _g(r, 'movement.primary.movingMs'), 'paradoMs': _g(r, 'movement.primary.stoppedMs'),
        'km_MM': _g(r, 'movement.bySource.MM.km'), 'km_GPS': _g(r, 'movement.bySource.GPS.km'),
        'km_odometro': _g(r, 'movement.odometer.km'),
        'fonteKmGnv': fonte_km, 'kmGnv': _g(r, f'movement.bySource.{fonte_km}.kmOnGnv') if fonte_km else None,
        'rpmMedio': _g(r, 'engine.rpmMean'), 'rpmMax': _g(r, 'engine.rpmMax'),
        'minInjetorGnv': _g(r, 'engine.injectorOpenMinutesGnv'), 'gnvMs': _g(r, 'durations.gnvMs'),
        'coberturaVelPct': _cobertura_vel(r, primario),
        'nivelInicio': _g(r, 'gnvLevel.startRaw'), 'nivelFim': _g(r, 'gnvLevel.endRaw'),
        'abasteceuEntre': _g(r, 'gnvLevel.refuel.betweenSessions'),
        'abasteceuDentro': len(_g(r, 'gnvLevel.refuel.intraSession', []) or []),
        'segmentos': len(seg), 'mapaHash': mapa, 'curvaHash': curva, 'mapaId': mapa_id(mapa, curva),
        'final': bool(r.get('final')), 'fechouLimpo': r.get('closedCleanly'),
        'plausivelPct': _g(r, 'quality.plausiblePct'), 'lacunas': _g(r, 'gaps.count'),
        'appVersion': _g(r, 'build.appVersion'), 'commit': _g(r, 'build.commit'),
    }


def _cobertura_vel(r, fonte):
    cob = _num(_g(r, f'movement.bySource.{fonte}.coverageMs')) if fonte else None
    tel = _num(_g(r, 'durations.telemetryCoveredMs'))
    return round(cob * 100.0 / tel, 2) if cob is not None and tel else None


def mapa_id(mapa, curva):
    if not mapa and not curva:
        return 'desconhecido'
    return f"{(mapa or '—')[:12]}|{(curva or '—')[:12]}"


# ---------------------------------------------------------------------------------------------------------- tanques
def calcular_tanques(copias, linhas):
    """Mesmo algoritmo do livro-caixa do app (TankLedger.kt), refeito aqui sobre as sessões deduplicadas."""
    pares = sorted(zip(copias, linhas), key=lambda p: (
        p[1]['startMsCorrigido'] if p[1]['startMsCorrigido'] is not None else (p[1]['startMs'] if p[1]['startMs'] is not None else 1 << 62),
        p[1]['sessionId'] or ''))
    ciclos, atual, prev_fim = [], None, None
    for c, linha in pares:
        segmentos = c['resumo'].get('tankSegments') or []
        sem_correcao = linha['relogioSuspeito'] and linha['startMsCorrigido'] is None
        vistos = set()
        for i, seg in enumerate(segmentos):
            ini = _num(seg.get('startRaw'))
            motivo = None
            if atual is None:
                motivo = 'FIRST_SESSION'
            elif i > 0:
                motivo = 'REFUEL_IN_SESSION'
            elif prev_fim is not None and ini is not None and prev_fim - ini >= REFUEL_DROP:
                motivo = 'REFUEL_BETWEEN_SESSIONS'
            if motivo:
                atual = {'indice': len(ciclos), 'iniciadoPor': motivo, 'primeiraSessao': linha['sessionId'], 'sessoes': set(),
                         'nivelInicio': None, 'nivelFim': None, 'km': {f: [] for f in FONTES_KM}, 'kmGnv': {f: [] for f in FONTES_KM},
                         'movendoMs': {f: [] for f in FONTES}, 'paradoMs': {f: [] for f in FONTES}, 'gnvMs': [], 'injetorMin': [],
                         'cob': {'sessoes': set(), 'MM': set(), 'GPS': set(), 'nivel': set()}, 'incerta': False}
                ciclos.append(atual)
            atual['ultimaSessao'] = linha['sessionId']
            atual['sessoes'].add(linha['sessionId'])
            atual['cob']['sessoes'].add(linha['sessionId'])
            if sem_correcao or (linha['relogioSuspeito'] and linha['startMsCorrigido'] is None):
                atual['incerta'] = True
            if atual['nivelInicio'] is None:
                atual['nivelInicio'] = ini
            fim = _num(seg.get('endRaw'))
            if fim is not None:
                atual['nivelFim'] = fim
                atual['cob']['nivel'].add(linha['sessionId'])
                prev_fim = fim
            for f in FONTES_KM:
                km = _num((seg.get('km') or {}).get(f))
                atual['km'][f].append(km)
                atual['kmGnv'][f].append(_num((seg.get('kmOnGnv') or {}).get(f)))
                if km is not None:
                    atual['cob'][f].add(linha['sessionId'])
            for f in FONTES:
                atual['movendoMs'][f].append(_num((seg.get('movingMs') or {}).get(f)))
                atual['paradoMs'][f].append(_num((seg.get('stoppedMs') or {}).get(f)))
            atual['gnvMs'].append(_num(seg.get('gnvMs')))
            atual['injetorMin'].append(_num(seg.get('injectorOpenMinutesGnv')))
            vistos.add(i)
    saida = []
    for ciclo in ciclos:
        ini, fim = ciclo['nivelInicio'], ciclo['nivelFim']
        consumido = fim - ini if ini is not None and fim is not None and fim >= ini else None
        suficiente = consumido is not None and consumido >= MIN_NIVEL
        km_gnv = {f: _soma(v) for f, v in ciclo['kmGnv'].items()}
        fonte = next((f for f in FONTES_KM if (km_gnv[f] or 0) > 0), None)
        inj = _soma(ciclo['injetorMin'])
        gnv = _soma(ciclo['gnvMs'])
        saida.append({
            'indice': ciclo['indice'], 'iniciadoPor': ciclo['iniciadoPor'], 'primeiraSessao': ciclo['primeiraSessao'],
            'ultimaSessao': ciclo['ultimaSessao'], 'sessoes': len(ciclo['sessoes']), 'nivelInicio': ini, 'nivelFim': fim,
            'nivelConsumido': consumido, 'dadosSuficientes': suficiente,
            'km': {f: _soma(v) for f, v in ciclo['km'].items()}, 'kmGnv': km_gnv,
            'movendoMs': {f: _soma(v) for f, v in ciclo['movendoMs'].items()},
            'paradoMs': {f: _soma(v) for f, v in ciclo['paradoMs'].items()},
            'gnvMs': gnv, 'injetorMin': inj,
            'kmPorUnidadeNivel': {'valor': round(km_gnv[fonte] / consumido, 5), 'fonte': fonte, 'rotulo': 'PROXY'}
            if suficiente and fonte else None,
            'injetorMinPor100Nivel': {'valor': round(inj / consumido * 100.0, 4), 'rotulo': 'PROXY'}
            if suficiente and inj is not None else None,
            'cobertura': {'sessoes': len(ciclo['cob']['sessoes']), 'comMM': len(ciclo['cob']['MM']),
                          'comGPS': len(ciclo['cob']['GPS']), 'comNivel': len(ciclo['cob']['nivel'])},
            'ordemIncerta': ciclo['incerta'], 'aberto': ciclo['indice'] == len(ciclos) - 1,
            '_sessoes': sorted(ciclo['sessoes']),
        })
    return saida


# ------------------------------------------------------------------------------------------------------------ mapas
def _bandas(serie_texto):
    """Segundos e ms de injetor GNV por faixa de rpm, lidos da série de 1 Hz (ms = gas_ms × rpm/120 × 4)."""
    bandas = {n: {'s': 0, 'injetorMs': 0.0} for n, _, _ in BANDAS}
    if not serie_texto:
        return bandas
    for linha in csv.DictReader(io.StringIO(serie_texto)):
        try:
            rpm = float(linha['rpm'])
        except (KeyError, ValueError):
            continue
        for nome, lo, hi in BANDAS:
            if lo <= rpm < hi:
                bandas[nome]['s'] += 1
                try:
                    bandas[nome]['injetorMs'] += float(linha['gas_ms']) * rpm / 120.0 * 4
                except (KeyError, ValueError):
                    pass
                break
    return bandas


def agrupar_mapas(copias, linhas):
    grupos = {}
    for c, linha in zip(copias, linhas):
        g = grupos.setdefault(linha['mapaId'], {'mapaId': linha['mapaId'], 'mapaHash': linha['mapaHash'], 'curvaHash': linha['curvaHash'],
                                                'sessoes': 0, 'fontes': set(), 'km': [], 'inj': [], 'cobMs': [], 'telMs': [],
                                                'rpmPeso': [], 'bandas': {n: {'s': 0, 'injetorMs': 0.0} for n, _, _ in BANDAS}})
        g['sessoes'] += 1
        fonte = linha['fonteKmGnv']
        if fonte:
            g['fontes'].add(fonte)
            g['km'].append(linha['kmGnv'])
            cob = _num(_g(c['resumo'], f'movement.bySource.{fonte}.coverageMs'))
            g['cobMs'].append(cob)
        g['inj'].append(linha['minInjetorGnv'])
        tel = _num(_g(c['resumo'], 'durations.telemetryCoveredMs'))
        g['telMs'].append(tel)
        if linha['rpmMedio'] is not None and tel:
            g['rpmPeso'].append((linha['rpmMedio'], tel))
        for nome, b in _bandas(c['serie']).items():
            g['bandas'][nome]['s'] += b['s']
            g['bandas'][nome]['injetorMs'] += b['injetorMs']
    saida = []
    for id_ in sorted(grupos):
        g = grupos[id_]
        km, inj, cob, tel = _soma(g['km']), _soma(g['inj']), _soma(g['cobMs']), _soma(g['telMs'])
        peso = sum(p for _, p in g['rpmPeso'])
        saida.append({
            'mapaId': id_, 'mapaHash': g['mapaHash'], 'curvaHash': g['curvaHash'], 'sessoes': g['sessoes'],
            'fonteKm': (sorted(g['fontes'])[0] if len(g['fontes']) == 1 else ('MISTA' if g['fontes'] else None)),
            'kmGnv': km, 'minInjetorGnv': inj,
            'injetorMinPorKm': round(inj / km, 5) if km and inj is not None else None,
            'velMediaKmh': round(km / (cob / 3_600_000.0), 2) if km is not None and cob else None,
            'rpmMedio': round(sum(r * p for r, p in g['rpmPeso']) / peso, 1) if peso else None,
            'coberturaVelPct': round(cob * 100.0 / tel, 2) if cob is not None and tel else None,
            'bandas': {n: {'s': b['s'], 'injetorMsPorS': round(b['injetorMs'] / b['s'], 3) if b['s'] else None}
                       for n, b in g['bandas'].items()},
        })
    return saida


def comparar_mapas(mapas):
    """Só compara quando a cobertura é comparável; senão devolve os motivos e NENHUM delta (não conclui sem cobertura)."""
    conhecidos = [m for m in mapas if m['mapaId'] != 'desconhecido']
    comparacoes = []
    for i in range(len(conhecidos)):
        for j in range(i + 1, len(conhecidos)):
            a, b = conhecidos[i], conhecidos[j]
            motivos = []
            if a['fonteKm'] is None or a['fonteKm'] != b['fonteKm'] or a['fonteKm'] == 'MISTA':
                motivos.append(f"fonte de km diferente ou ausente (A={a['fonteKm']}, B={b['fonteKm']})")
            for rot, m in (('A', a), ('B', b)):
                if (m['kmGnv'] or 0) < MIN_KM_GNV:
                    motivos.append(f"km no GNV abaixo de {MIN_KM_GNV:g} em {rot} ({m['kmGnv']})")
            if a['velMediaKmh'] and b['velMediaKmh'] and abs(a['velMediaKmh'] - b['velMediaKmh']) / a['velMediaKmh'] > TOL_VEL:
                motivos.append('velocidade media muito diferente (perfil de uso nao comparavel)')
            if a['rpmMedio'] and b['rpmMedio'] and abs(a['rpmMedio'] - b['rpmMedio']) / a['rpmMedio'] > TOL_RPM:
                motivos.append('rpm medio muito diferente (perfil de uso nao comparavel)')
            if a['injetorMinPorKm'] is None or b['injetorMinPorKm'] is None:
                motivos.append('injetor ou km desconhecido')
            ok = not motivos
            bandas = [n for n in a['bandas'] if (a['bandas'][n]['s'] >= MIN_BANDA_S and b['bandas'][n]['s'] >= MIN_BANDA_S)]
            deltas = None
            if ok:
                deltas = {'injetorMinPorKmPct': round((b['injetorMinPorKm'] / a['injetorMinPorKm'] - 1.0) * 100.0, 2),
                          'porBanda': {n: round((b['bandas'][n]['injetorMsPorS'] / a['bandas'][n]['injetorMsPorS'] - 1.0) * 100.0, 2)
                                       for n in bandas if a['bandas'][n]['injetorMsPorS'] and b['bandas'][n]['injetorMsPorS']}}
            comparacoes.append({'mapaA': a['mapaId'], 'mapaB': b['mapaId'], 'comparavel': ok, 'motivos': motivos,
                                'fonteKm': a['fonteKm'] if ok else None, 'bandasComparaveis': bandas if ok else [],
                                'deltas': deltas,
                                'cobertura': {'A': {'kmGnv': a['kmGnv'], 'sessoes': a['sessoes']}, 'B': {'kmGnv': b['kmGnv'], 'sessoes': b['sessoes']}}})
    return comparacoes


# ------------------------------------------------------------------------------------------------------------ grafo
def montar_grafo(linhas, tanques):
    nos, arestas = {}, []

    def no(id_, tipo, rotulo, **props):
        nos.setdefault(id_, {'id': id_, 'label': rotulo, 'tipo': tipo, 'file_type': 'data',
                             **{k: v for k, v in props.items() if v is not None}})

    def aresta(a, b, rel):
        arestas.append({'source': a, 'target': b, 'relation': rel, 'confidence': 'EXTRACTED', 'confidence_score': 1.0})

    for t in tanques:
        no(f"tanque:{t['indice']}", 'tanque', f"Tanque {t['indice']}", iniciadoPor=t['iniciadoPor'], nivelInicio=t['nivelInicio'],
           nivelFim=t['nivelFim'], kmGnvMM=t['kmGnv']['MM'], kmGnvGPS=t['kmGnv']['GPS'], injetorMin=t['injetorMin'],
           ordemIncerta=t['ordemIncerta'])
        if t['indice'] > 0:
            aresta(f"tanque:{t['indice']}", f"tanque:{t['indice'] - 1}", 'abasteceu_depois_de')
    tanque_de = {s: t['indice'] for t in tanques for s in t['_sessoes']}
    for l in linhas:
        sid = f"sessao:{l['sessionUuid'] or l['sessionId']}"
        no(sid, 'sessao', l['sessionId'], km_MM=l['km_MM'], km_GPS=l['km_GPS'], fonteMovimento=l['fonteMovimento'],
           nivelInicio=l['nivelInicio'], nivelFim=l['nivelFim'], relogioSuspeito=l['relogioSuspeito'])
        if l['sessionId'] in tanque_de:
            aresta(sid, f"tanque:{tanque_de[l['sessionId']]}", 'pertence_a')
        if l['dia']:
            no(f"dia:{l['dia']}", 'dia', l['dia'])
            aresta(sid, f"dia:{l['dia']}", 'pertence_a')
        if l['mapaId'] != 'desconhecido':
            no(f"mapa:{l['mapaId']}", 'mapa', l['mapaId'], mapaHash=l['mapaHash'], curvaHash=l['curvaHash'])
            aresta(sid, f"mapa:{l['mapaId']}", 'usa_mapa')
    return {'directed': True, 'multigraph': False,
            'graph': {'schema_version': 1, 'origem': 'omegas-analisar-resumos'},
            'nodes': [nos[k] for k in sorted(nos)], 'links': arestas}


# ------------------------------------------------------------------------------------------------------------ geral
def analisar(caminhos):
    copias = ler_copias(caminhos)
    unicas, identicas, descartadas = deduplicar(copias)
    linhas = [_linha_sessao(c) for c in unicas]
    tanques = calcular_tanques(unicas, linhas)
    tanque_de = {s: t['indice'] for t in tanques for s in t['_sessoes']}
    for l in linhas:
        l['tanque'] = tanque_de.get(l['sessionId'])
    linhas = sorted(linhas, key=lambda l: (l['startMsCorrigido'] if l['startMsCorrigido'] is not None else (l['startMs'] or 0), l['sessionId'] or ''))
    mapas = agrupar_mapas(unicas, [_linha_sessao(c) for c in unicas])
    comparacoes = comparar_mapas(mapas)
    suspeitos = [l for l in linhas if l['relogioSuspeito']]
    relatorio = {
        'copias_lidas': len(copias), 'copias_identicas_removidas': identicas, 'versoes_descartadas': descartadas,
        'sessoes_unicas': len(unicas), 'relogio_suspeito': len(suspeitos),
        'sem_correcao_de_relogio': sum(1 for l in suspeitos if l['startMsCorrigido'] is None),
        'sessoes_sem_velocidade_real': sum(1 for l in linhas if l['fonteMovimento'] in (None, 'RPM_PROXY')),
        'sessoes_sem_identidade_de_mapa': sum(1 for l in linhas if l['mapaId'] == 'desconhecido'),
        'comparacoes_comparaveis': sum(1 for c in comparacoes if c['comparavel']), 'comparacoes_total': len(comparacoes),
        'aviso': 'Mapas so sao comparados com cobertura comparavel (mesma fonte de km, km minimo, perfil de uso parecido); '
                 'caso contrario veja os motivos e nao conclua.',
    }
    limpos = [{k: v for k, v in t.items() if k != '_sessoes'} for t in tanques]
    return {'sessoes': linhas, 'tanques': limpos, 'mapas': mapas, 'comparacoes': comparacoes, 'relatorio': relatorio,
            'grafo': montar_grafo(linhas, tanques)}


def escrever(resultado, saida):
    os.makedirs(saida, exist_ok=True)

    def grava(nome, texto):
        with open(os.path.join(saida, nome), 'w', encoding='utf-8', newline='') as fh:
            fh.write(texto)

    grava('sessoes.csv', para_csv(resultado['sessoes']))
    grava('tanques.csv', para_csv(resultado['tanques']))
    grava('mapas.csv', para_csv(resultado['mapas']))
    grava('grafo_graphify.json', json.dumps(resultado['grafo'], ensure_ascii=False, indent=1, sort_keys=True))
    grava('relatorio.json', json.dumps({'relatorio': resultado['relatorio'], 'tanques': resultado['tanques'],
                                        'mapas': resultado['mapas'], 'comparacoes': resultado['comparacoes']},
                                       ensure_ascii=False, indent=1, sort_keys=True))


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument('entradas', nargs='+', help='pastas ou zips com resumos (session_summary.json)')
    p.add_argument('--saida', required=True, help='pasta de saída (CSV, JSON, grafo)')
    args = p.parse_args(argv)
    r = analisar(args.entradas)
    escrever(r, args.saida)
    rel = r['relatorio']
    print(f"{rel['sessoes_unicas']} sessões únicas ({rel['copias_identicas_removidas']} cópias iguais e {rel['versoes_descartadas']} versões parciais descartadas); "
          f"{len(r['tanques'])} tanques; {rel['comparacoes_comparaveis']} de {rel['comparacoes_total']} comparações com cobertura comparável; "
          f"{rel['relogio_suspeito']} com relógio suspeito. Saída em {args.saida}")
    return 0


if __name__ == '__main__':
    sys.exit(main())
