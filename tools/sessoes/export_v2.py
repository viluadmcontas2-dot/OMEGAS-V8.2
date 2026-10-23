"""Export v2 das sessões do OMEGAS (docs/EXPORT-SESSOES.md): ingestão idempotente, conversor v1 -> v2, derivação e grafo.

Camadas: BRUTA (o que o app grava: raw/*.tsv colunar, events_*.jsonl) é a fonte da verdade; DERIVADA (derived/) é recalculada
aqui, versionada (DERIVE_VERSION) e nunca é fonte; ÍNDICE (index/) guarda o que já entrou (ingest_log.csv, conflitos).
Regras do dono: desconhecido é vazio/None, nunca 0; só observa (nada escreve na ECU); a mesma entrada dá a mesma saída.

Uso (sempre com python3 -I):
  export_v2.py ingerir  <pasta|zip>... --loja DIR          # partes v2 e pacotes de resumo, idempotente por (sessão, arquivo, parte, sha256)
  export_v2.py converter <pasta|zip v1> --loja DIR          # sessão v1 (events_*.jsonl) -> pasta v2 com bruta parcial
  export_v2.py derivar  <pasta de sessão v2>                # derived/: séries 1 e 5 Hz, segmentos, degraus, tanques, estol, células
  export_v2.py grafo    <loja> --saida DIR                  # nós Session/TankCycle/MapVersion/CurveVersion/EcuProfile/WriteAction/Stall
"""
import argparse, bisect, collections, csv, glob, hashlib, io, json, os, statistics, sys, zipfile
from datetime import datetime, timezone

DERIVE_VERSION = 'derive-v1'
INJ_MS_PER_COUNT = 0.00256            # Mp48TelemetryScale
WATER_OFFSET_C = 109
GAS_OFFSET_C = 20
CYLINDERS = 4
GAP_MS = 2000                          # lacuna de telemetria; fora das somas
MOVING_KMH = 2.0
MOVING_RPM = 1100
LEVEL_MIN_VALID = 26
LEVEL_WINDOW, LEVEL_SPREAD, LEVEL_STEP, REFUEL_DROP = 15, 3, 20, 40
MIN_LEVEL_UNITS = 20
MIN_CELL_S = 10
RPM_BIN, MAP_BIN = 500, 0.1
MM_GAP_MS, GPS_GAP_MS = 2000, 5000
MAX_MEMBRO = 64 * 1024 * 1024


# ============================================================================================================ utilidades
def sha256(b):
    return hashlib.sha256(b).hexdigest()


def _csv(linhas, campos=None):
    if not linhas:
        return ''
    campos = campos or list(linhas[0].keys())
    out = io.StringIO()
    w = csv.writer(out, lineterminator='\n')
    w.writerow(campos)
    for l in linhas:
        w.writerow(['' if l.get(c) is None else l[c] for c in campos])
    return out.getvalue()


def _grava(caminho, texto):
    os.makedirs(os.path.dirname(caminho), exist_ok=True)
    with open(caminho, 'w', encoding='utf-8', newline='') as fh:
        fh.write(texto)


def _round(v, n=4):
    return None if v is None else round(v, n)


def u16(b, i):
    return b[i] | (b[i + 1] << 8)


def s16(b, i):
    v = u16(b, i)
    return v - 65536 if v >= 32768 else v


def decodificar_quadro(hexstr):
    """34 bytes do `48 01` -> valores crus e físicos (escalas de Mp48TelemetryScale). Tamanho errado = None."""
    try:
        b = bytes.fromhex(hexstr)
    except ValueError:
        return None
    if len(b) < 34:
        return None
    gas_raw, petrol_raw = u16(b, 6), u16(b, 8)
    return {
        'rpm': u16(b, 0), 'gas_raw': gas_raw, 'petrol_raw': petrol_raw, 'fuel_byte': b[11], 'water_raw': b[12],
        'level': b[13], 'gas_press_raw': u16(b, 14), 'gas_temp_raw': b[16], 'map_raw': s16(b, 17), 'volt_raw': b[19],
        'gas2_raw': u16(b, 24), 'petrol2_raw': u16(b, 28),
        'gas_ms': gas_raw * INJ_MS_PER_COUNT if gas_raw else None, 'petrol_ms': petrol_raw * INJ_MS_PER_COUNT,
        'map_bar': s16(b, 17) / 1000.0, 'water_c': WATER_OFFSET_C - b[12], 'gas_c': b[16] - GAS_OFFSET_C,
        'volts': b[19] / 16.0 if b[19] else None,
        'fuel': classe_combustivel(b[11], u16(b, 0)),
    }


def classe_combustivel(fuel_byte, rpm):
    if fuel_byte == 0 or rpm <= 0:
        return 'MOTOR_OFF'
    if fuel_byte in (0x80, 0xA0):
        return 'GASOLINA'
    if fuel_byte in (0x88, 0xA8):
        return 'TRANSICAO'
    if fuel_byte in (0x90, 0xB0):
        return 'GNV'
    return 'DESCONHECIDO'


def ler_tsv(caminho):
    """(cabeçalho dict, linhas de dados) de um .tsv bruto; linha cortada pela queda de energia (sem \\n final) é ignorada."""
    with open(caminho, 'rb') as fh:
        dados = fh.read()
    fim = dados.rfind(b'\n')
    texto = dados[:fim + 1].decode('utf-8', 'replace') if fim >= 0 else ''
    cab, linhas = {}, []
    for ln in texto.split('\n'):
        if not ln:
            continue
        if ln.startswith('#'):
            for tok in ln.split()[1:]:
                if '=' in tok:
                    k, _, v = tok.partition('=')
                    cab[k] = v
            continue
        linhas.append(ln.split('\t'))
    return cab, linhas


def fluxo(sessao, nome):
    """Todas as linhas de um fluxo bruto (segmentos em ordem), com o cabeçalho do primeiro."""
    cab, linhas = {}, []
    for f in sorted(glob.glob(os.path.join(sessao, 'raw', nome + '.*.tsv'))):
        c, ls = ler_tsv(f)
        cab = cab or c
        linhas += ls
    return cab, linhas


def eventos(sessao):
    out = []
    for f in sorted(glob.glob(os.path.join(sessao, 'events_*.jsonl'))):
        with open(f, 'rb') as fh:
            for ln in fh.read().split(b'\n'):
                try:
                    out.append(json.loads(ln))
                except ValueError:
                    pass
    return out


def _int(v):
    try:
        return int(v)
    except (TypeError, ValueError):
        return None


# ================================================================================================================ ingestão
def _membros(entradas):
    """(caminho_logico, bytes) de arquivos soltos, pastas e zips (zip dentro de zip, um nível)."""
    def zip_membros(dados, origem, nivel):
        try:
            z = zipfile.ZipFile(io.BytesIO(dados))
        except zipfile.BadZipFile:
            return
        with z:
            for info in z.infolist():
                if info.is_dir() or info.file_size > MAX_MEMBRO:
                    continue
                dados2 = z.read(info)
                if info.filename.lower().endswith('.zip') and nivel == 0:
                    yield from zip_membros(dados2, origem + '!' + info.filename, 1)
                else:
                    yield origem + '!' + info.filename, dados2

    for base in entradas:
        if os.path.isdir(base):
            for raiz, pastas, arqs in os.walk(base):
                pastas.sort()
                for a in sorted(arqs):
                    p = os.path.join(raiz, a)
                    with open(p, 'rb') as fh:
                        dados = fh.read()
                    if a.lower().endswith('.zip'):
                        yield from zip_membros(dados, p, 0)
                    else:
                        yield p, dados
        elif os.path.isfile(base):
            with open(base, 'rb') as fh:
                dados = fh.read()
            if base.lower().endswith('.zip'):
                yield from zip_membros(dados, base, 0)
            else:
                yield base, dados


def _dir_base(caminho):
    """(pasta lógica, nome) de um membro: `zip!interno/a/b.txt` -> (`zip!interno/a`, `b.txt`)."""
    if '!' in caminho:
        origem, _, inner = caminho.rpartition('!')
        pasta, _, nome = inner.rpartition('/')
        return origem + '!' + pasta, nome
    return os.path.dirname(caminho), os.path.basename(caminho)


def ingerir(entradas, loja):
    """Monta `loja/<chave>/...` a partir das partes v2 (parte.json + fatias) e pacotes de resumo. Idempotente: a chave
    natural é (sessão, arquivo, byte inicial, sha256); a mesma chave com outro sha vira CONFLITO e nunca sobrescreve."""
    idx = os.path.join(loja, 'index')
    log_path = os.path.join(idx, 'ingest_log.csv')
    conflitos_path = os.path.join(idx, 'conflicts.csv')
    ja = {}
    linhas_antigas = []
    if os.path.isfile(log_path):
        linhas_antigas = list(csv.DictReader(open(log_path, encoding='utf-8')))
        for r in linhas_antigas:
            ja[(r['session'], r['file'], r['from_byte'])] = r['sha256']
    novos, conflitos, repetidos, invalidos = [], [], 0, 0
    membros = dict(_membros(entradas))
    pastas = collections.defaultdict(dict)
    for caminho, dados in membros.items():
        pasta, nome = _dir_base(caminho)
        pastas[pasta][nome] = dados
    partes_json = sorted(c for c in membros if _dir_base(c)[1] == 'parte.json')
    em_parte = set()
    for caminho in partes_json:
        pasta, _ = _dir_base(caminho)
        try:
            parte = json.loads(membros[caminho])
        except ValueError:
            invalidos += 1
            continue
        sessao = parte.get('sessionKey') or parte.get('sessionUuid') or parte.get('sessionId')
        if not sessao:
            invalidos += 1
            continue
        for item in parte.get('entries', []):
            nome = item.get('entry')
            completo = (pasta + '/' + nome) if not pasta.endswith('!') else pasta + nome
            dados = membros.get(completo)
            em_parte.add(completo)
            if dados is None or (item.get('sha256') and sha256(dados) != item['sha256']):
                invalidos += 1
                conflitos.append({'session': sessao, 'file': item.get('source'), 'from_byte': item.get('fromByte', 0), 'motivo': 'membro ausente ou sha256 nao confere'})
                continue
            origem = item.get('source')
            whole = bool(item.get('whole'))
            de = 0 if whole else item.get('fromByte', 0)
            destino_rel = origem if (whole or not origem.endswith('.tsv')) else 'raw/' + origem
            chave = (sessao, destino_rel, str(de))
            sha = sha256(dados)
            if chave in ja:
                if ja[chave] == sha:
                    repetidos += 1
                else:
                    conflitos.append({'session': sessao, 'file': destino_rel, 'from_byte': de, 'motivo': 'mesmo trecho com outro sha256'})
                continue
            if whole:
                _grava_bytes(os.path.join(loja, sessao, destino_rel), dados)
            else:
                _escrever_fatia(os.path.join(loja, sessao, destino_rel), de, dados)
            ja[chave] = sha
            novos.append({'session': sessao, 'file': destino_rel, 'from_byte': de, 'part': parte.get('partIndex', parte.get('part')), 'sha256': sha, 'bytes': len(dados)})
    # pacotes leves (`*_resumo.zip` ou pastas resumo/): manifest + session_summary + series
    for pasta, arqs in sorted(pastas.items()):
        if 'parte.json' in arqs or not ({'session_summary.json', 'series_1hz.csv'} & set(arqs)):
            continue
        sessao = _sessao_de_resumo(arqs)
        if not sessao:
            continue
        for nome in ('session_summary.json', 'series_1hz.csv', 'manifest.json'):
            if nome not in arqs or (pasta + '/' + nome) in em_parte:
                continue
            dados, sha = arqs[nome], sha256(arqs[nome])
            chave = (sessao, 'resumo/' + nome, '0')
            if chave in ja and ja[chave] == sha:
                repetidos += 1
                continue
            if chave in ja:
                conflitos.append({'session': sessao, 'file': 'resumo/' + nome, 'from_byte': 0, 'motivo': 'versao diferente do resumo (a mais nova vence)'})
            _grava_bytes(os.path.join(loja, sessao, 'resumo', nome), dados)
            ja[chave] = sha
            novos.append({'session': sessao, 'file': 'resumo/' + nome, 'from_byte': 0, 'part': None, 'sha256': sha, 'bytes': len(dados)})
    campos = ['session', 'file', 'from_byte', 'part', 'sha256', 'bytes']
    substituidas = {(n['session'], n['file'], str(n['from_byte'])) for n in novos}
    linhas = [r for r in linhas_antigas if (r['session'], r['file'], r['from_byte']) not in substituidas] + novos
    linhas.sort(key=lambda r: (r['session'], r['file'], int(r['from_byte'] or 0)))
    _grava(log_path, _csv(linhas, campos))
    if conflitos:
        antigos = list(csv.DictReader(open(conflitos_path, encoding='utf-8'))) if os.path.isfile(conflitos_path) else []
        _grava(conflitos_path, _csv(antigos + conflitos, ['session', 'file', 'from_byte', 'motivo']))
    return {'novos': len(novos), 'repetidos': repetidos, 'conflitos': len(conflitos), 'invalidos': invalidos}


def _escrever_fatia(destino, de, dados, whole=False):
    os.makedirs(os.path.dirname(destino), exist_ok=True)
    if whole:
        _grava_bytes(destino, dados)
        return
    modo = 'r+b' if os.path.exists(destino) else 'w+b'
    with open(destino, modo) as fh:
        fh.seek(0, 2)
        if fh.tell() < de:
            fh.write(b'\0' * (de - fh.tell()))
        fh.seek(de)
        fh.write(dados)


def _grava_bytes(caminho, dados):
    os.makedirs(os.path.dirname(caminho), exist_ok=True)
    with open(caminho, 'wb') as fh:
        fh.write(dados)


def _sessao_de_resumo(arqs):
    for nome in ('manifest.json', 'session_summary.json'):
        if nome in arqs:
            try:
                j = json.loads(arqs[nome])
            except ValueError:
                continue
            ident = j.get('identity') or j
            k = ident.get('sessionKey') or ident.get('sessionUuid') or ident.get('sessionId')
            if k:
                return k
    return None


# ============================================================================================================== conversor
def converter(entrada, loja):
    """Sessão v1 (events_*.jsonl + manifest) -> pasta v2 com a bruta PARCIAL: os campos crus que o v1 gravou (rpm, injeção,
    combustível, água, nível, pressão, MAP...) viram quadros de 34 bytes (o resto 00); `quality.raw_complete=false`."""
    arquivos = {c: d for c, d in _membros([entrada])}
    evs, manifesto = [], {}
    for caminho in sorted(arquivos):
        base = caminho.rpartition('/')[2]
        if base.startswith('events_') and '.jsonl' in base:
            for ln in arquivos[caminho].split(b'\n'):
                try:
                    evs.append(json.loads(ln))
                except ValueError:
                    pass
        elif base == 'manifest.json':
            try:
                manifesto = json.loads(arquivos[caminho])
            except ValueError:
                pass
    if not evs:
        return None
    sid = manifesto.get('sessionId') or 'sessao_v1'
    tel = [e for e in evs if e.get('type') == 'telemetry' and isinstance(e.get('data'), dict) and e['data'].get('rpm') is not None]
    inicio = manifesto.get('createdAtMs') or (evs[0].get('recordedAtMs') or 0)
    offsets = [e['recordedAtMs'] - e['data']['captured_elapsed_ms'] for e in tel if e['data'].get('captured_elapsed_ms') is not None and e.get('recordedAtMs')]
    offset = statistics.median(offsets) if offsets else None
    start_elapsed = (inicio - offset) if offset is not None else 0
    dev = (manifesto.get('deviceId') or sid.rpartition('_')[2] or 'legado')[:8]
    boot = 'L' + hashlib.sha256(str(int((offset or 0) // 60000)).encode()).hexdigest()[:7]
    chave = f"S_{dev}_{boot}_{int(start_elapsed // 1000)}"
    destino = os.path.join(loja, chave)
    quadros = []
    for e in tel:
        d = e['data']
        el = d.get('captured_elapsed_ms')
        if el is None:
            el = e['recordedAtMs'] - (offset or 0)
        b = bytearray(34)
        b[0:2] = int(d.get('rpm') or 0).to_bytes(2, 'little')
        b[6:8] = int(d.get('gas_raw') or 0).to_bytes(2, 'little')
        b[8:10] = int(d.get('petrol_raw', d.get('petrol_counts')) or 0).to_bytes(2, 'little')
        b[11] = int(d.get('fuel_byte') or 0) & 255
        b[12] = int(d.get('water_raw') or 0) & 255
        b[13] = int(d.get('level_raw') or 0) & 255
        b[14:16] = int(d.get('gas_pressure_raw') or 0).to_bytes(2, 'little')
        b[16] = int(d.get('gas_temperature_raw') or 0) & 255
        b[17:19] = (int(d.get('map_raw') or 0) & 0xFFFF).to_bytes(2, 'little')
        b[19] = int(d.get('unknown_raw_19', d.get('dynamic_correction')) or 0) & 255
        b[24:26] = int(d.get('gas_2_raw') or 0).to_bytes(2, 'little')
        b[28:30] = int(d.get('petrol_2_raw') or 0).to_bytes(2, 'little')
        quadros.append((el, bytes(b).hex(), e.get('recordedAtMs')))
    quadros.sort(key=lambda q: q[0])
    base_e = quadros[0][0] if quadros else start_elapsed
    cab = lambda nome, cols: f"#omegas-raw-v2 stream={nome} cols={cols} session={chave} boot={boot} segment=1 base_e={int(base_e)} legacy=v1\n"
    _grava(os.path.join(destino, 'raw', 'frames_48_01.0001.tsv'),
           cab('frames_48_01', 'e_ms,hex') + ''.join(f"{int(el - base_e)}\t{h}\n" for el, h, _ in quadros))
    mm = []
    for e in evs:
        if e.get('type') in ('car_info', 'car_info_raw'):
            w = e.get('recordedAtMs') or 0
            el = (w - offset) if offset is not None else w
            mm.append(f"{int(el - base_e)}\t{w}\t{e['type']}\t{json.dumps(e.get('data'), separators=(',', ':'))}\n")
    if mm:
        _grava(os.path.join(destino, 'raw', 'mm.0001.tsv'), cab('mm', 'e_ms,wall_ms,kind,json') + ''.join(mm))
    tx = []
    for e in evs:
        if e.get('type') == 'ecu_command' and isinstance(e.get('data'), dict):
            d = e['data']
            w = e.get('recordedAtMs') or 0
            el = (w - offset) if offset is not None else w
            tx.append('\t'.join([str(int(el - base_e)), str(d.get('workClass', '')), str(d.get('status', '')), '1' if d.get('ok') else '0',
                                 str(d.get('elapsedMs', '')), str(d.get('requestHex', '')), str(d.get('payloadHex', '')),
                                 str(d.get('reason', '')).replace('\t', ' '), str(d.get('error', '')).replace('\t', ' ')]) + '\n')
    if tx:
        _grava(os.path.join(destino, 'raw', 'tx.0001.tsv'), cab('tx', 'e_ms,class,status,ok,ms,req_hex,resp_hex,reason,error') + ''.join(tx))
    _grava(os.path.join(destino, 'events_0001.jsonl'), ''.join(json.dumps(e, separators=(',', ':')) + '\n' for e in evs))
    parou = any(e.get('type') == 'session_stopped' for e in evs)
    _grava(os.path.join(destino, 'manifest.json'), json.dumps({
        'schema': 'omegas-session-export-v2-legacy', 'sessionKey': chave, 'sessionIdV1': sid, 'bootId': boot,
        'deviceId': dev, 'startMs': inicio, 'clean': parou,
        'appVersion': (manifesto.get('metadata') or {}).get('appVersion'),
        'quality': {'raw_complete': False, 'frames': len(quadros), 'note': 'v1 gravava so ~19% dos quadros e so os campos decodificados'},
        'v1_compat': True,
    }, indent=1, sort_keys=True))
    return chave


# =============================================================================================================== derivação
def _carregar_quadros(sessao):
    cab, linhas = fluxo(sessao, 'frames_48_01')
    q = []
    for l in linhas:
        if len(l) < 2:
            continue
        e, d = _int(l[0]), decodificar_quadro(l[1])
        if e is not None and d is not None:
            d['e_ms'] = e
            q.append(d)
    q.sort(key=lambda d: d['e_ms'])
    return cab, q


def _carregar_velocidades(sessao, evs, base_e):
    """Fontes de velocidade por ordem de prioridade: MM (fluxo mm car_info e eventos), GPS (full_snapshot), com tempo relativo."""
    mm, gps = [], []
    _, linhas = fluxo(sessao, 'mm')
    for l in linhas:
        if len(l) >= 4 and l[2] == 'car_info':
            try:
                d = json.loads(l[3])
            except ValueError:
                continue
            v = d.get('speed')
            if isinstance(v, (int, float)) and 0 <= v <= 400:
                mm.append((_int(l[0]), float(v)))
    for e in evs:
        if e.get('type') == 'full_snapshot' and isinstance(e.get('data'), dict):
            g = e['data'].get('gps') or {}
            if g.get('enabled') and g.get('latitude') is not None and (g.get('timestamp') or 0) > 0 and isinstance(g.get('speedKmh'), (int, float)):
                el = e.get('elapsedMs')
                t = (el - base_e) if (el is not None and base_e is not None) else None
                if t is not None:
                    gps.append((int(t), float(g['speedKmh'])))
    return {'MM': mm, 'GPS': gps}


def _integrar(amostras, gap):
    """Trapézio entre leituras da mesma fonte; buraco > gap não integra. Devolve (km, parado_ms, andando_ms, cobertura_ms)."""
    if len(amostras) < 2:
        return None
    km = parado = andando = cob = 0.0
    for (t0, v0), (t1, v1) in zip(amostras, amostras[1:]):
        dt = t1 - t0
        if dt <= 0 or dt > gap:
            continue
        cob += dt
        km += (v0 + v1) / 2.0 * dt / 3_600_000.0
        if v0 > MOVING_KMH:
            andando += dt
        else:
            parado += dt
    return (km, parado, andando, cob) if cob > 0 else None


def _niveis(quadros):
    """Degraus e abastecimentos pelo nível firme (15 quadros com dispersão <= 3), igual ao LevelTracker do app."""
    janela, firme, passos, abastec, invalidos = [], None, [], [], 0
    for q in quadros:
        n = q['level']
        if n < LEVEL_MIN_VALID:
            invalidos += 1
            continue
        janela.append((q['e_ms'], n))
        janela = janela[-LEVEL_WINDOW:]
        if len(janela) < LEVEL_WINDOW:
            continue
        vs = [x[1] for x in janela]
        if max(vs) - min(vs) > LEVEL_SPREAD:
            continue
        nivel = sorted(vs)[len(vs) // 2]
        if firme is None:
            firme = nivel
            passos.append({'tipo': 'INITIAL', 'de': None, 'para': nivel, 't_ms': janela[0][0]})
        elif abs(nivel - firme) >= LEVEL_STEP:
            passos.append({'tipo': 'STEP', 'de': firme, 'para': nivel, 't_ms': janela[0][0]})
            if firme - nivel >= REFUEL_DROP:
                abastec.append({'t_ms': janela[0][0], 'de': firme, 'para': nivel, 'queda': firme - nivel})
            firme = nivel
    return passos, abastec, invalidos


def derivar(sessao):
    """Escreve `derived/` ao lado da bruta. Tudo recalculável: apague e rode de novo."""
    cab, q = _carregar_quadros(sessao)
    evs = eventos(sessao)
    man = {}
    try:
        man = json.load(open(os.path.join(sessao, 'manifest.json'), encoding='utf-8'))
    except (OSError, ValueError):
        pass
    saida = os.path.join(sessao, 'derived')
    base_e = _int(cab.get('base_e'))
    # lacunas e batimento (qualidade)
    lacunas, cobertos = [], 0.0
    for a, b in zip(q, q[1:]):
        dt = b['e_ms'] - a['e_ms']
        if dt > GAP_MS:
            lacunas.append({'ini_ms': a['e_ms'], 'fim_ms': b['e_ms'], 'ms': dt})
        elif dt > 0:
            cobertos += dt
    hb = [l for l in fluxo(sessao, 'heartbeat')[1] if len(l) >= 6]
    descartes = max([_int(l[5]) or 0 for l in hb], default=None)
    offsets = [(_int(l[1]) - base_e - _int(l[0])) for l in hb if base_e is not None and _int(l[1]) is not None and _int(l[0]) is not None]
    salto_relogio = (max(offsets) - min(offsets)) if offsets else None
    # séries 1 Hz e 5 Hz
    def serie(passo_ms):
        baldes = collections.OrderedDict()
        for x in q:
            baldes.setdefault(x['e_ms'] // passo_ms, []).append(x)
        linhas = []
        for k, xs in baldes.items():
            gas = [x['gas_ms'] for x in xs if x['gas_ms'] is not None]
            vol = [x['volts'] for x in xs if x['volts'] is not None]
            comb = collections.Counter(x['fuel'] for x in xs).most_common(1)[0][0]
            nv = [x['level'] for x in xs if x['level'] >= LEVEL_MIN_VALID]
            linhas.append({
                't_s': _round(k * passo_ms / 1000.0, 1), 'quadros': len(xs), 'rpm': round(sum(x['rpm'] for x in xs) / len(xs)),
                'gas_ms': _round(sum(gas) / len(gas), 3) if gas else None,
                'petrol_ms': _round(sum(x['petrol_ms'] for x in xs) / len(xs), 3),
                'map_bar': _round(sum(x['map_bar'] for x in xs) / len(xs), 3),
                'nivel': sorted(nv)[len(nv) // 2] if nv else None, 'agua_c': xs[-1]['water_c'],
                'volts': _round(sum(vol) / len(vol), 2) if vol else None, 'combustivel': comb})
        return linhas
    _grava(os.path.join(saida, 'series_1hz.csv'), _csv(serie(1000)))
    _grava(os.path.join(saida, 'series_5hz.csv'), _csv(serie(200)))
    # velocidade por fonte e segmentos parado x andando
    vels = _carregar_velocidades(sessao, evs, base_e)
    fontes, segmentos = {}, []
    for nome in ('MM', 'GPS'):
        r = _integrar(vels[nome], MM_GAP_MS if nome == 'MM' else GPS_GAP_MS)
        fontes[nome] = None if r is None else {'km': _round(r[0]), 'parado_ms': r[1], 'andando_ms': r[2], 'cobertura_ms': r[3]}
        am = vels[nome]
        atual = None
        for (t0, v0), (t1, _) in zip(am, am[1:]):
            gap = MM_GAP_MS if nome == 'MM' else GPS_GAP_MS
            if t1 - t0 > gap or t1 <= t0:
                atual = None
                continue
            est = 'andando' if v0 > MOVING_KMH else 'parado'
            if atual and atual['estado'] == est and atual['fonte'] == nome:
                atual['fim_ms'] = t1
            else:
                atual = {'fonte': nome, 'estado': est, 'ini_ms': t0, 'fim_ms': t1}
                segmentos.append(atual)
    prox = {'parado_ms': 0.0, 'andando_ms': 0.0}
    atual = None
    for a, b in zip(q, q[1:]):
        dt = b['e_ms'] - a['e_ms']
        if dt <= 0 or dt > GAP_MS:
            atual = None
            continue
        est = 'andando' if a['rpm'] > MOVING_RPM else 'parado'
        prox['andando_ms' if est == 'andando' else 'parado_ms'] += dt
        if atual and atual['estado'] == est:
            atual['fim_ms'] = b['e_ms']
        else:
            atual = {'fonte': 'RPM_PROXY', 'estado': est, 'ini_ms': a['e_ms'], 'fim_ms': b['e_ms']}
            segmentos.append(atual)
    fontes['RPM_PROXY'] = {'km': None, 'parado_ms': prox['parado_ms'], 'andando_ms': prox['andando_ms'], 'cobertura_ms': cobertos, 'proxy': True} if q else None
    _grava(os.path.join(saida, 'segments.csv'), _csv(segmentos, ['fonte', 'estado', 'ini_ms', 'fim_ms']))
    # degraus, abastecimentos e ciclos de tanque
    passos, abastec, invalidos = _niveis(q)
    _grava(os.path.join(saida, 'level_steps.csv'), _csv(passos, ['tipo', 'de', 'para', 't_ms']))
    cortes = [0] + [a['t_ms'] for a in abastec] + [None]
    ciclos = []
    t_ini = q[0]['e_ms'] if q else 0
    for i in range(len(cortes) - 1):
        a, b = cortes[i] if i else t_ini, cortes[i + 1]
        xs = [x for x in q if x['e_ms'] >= a and (b is None or x['e_ms'] < b)]
        inj = gnv = 0.0
        for p, n in zip(xs, xs[1:]):
            dt = n['e_ms'] - p['e_ms']
            if 0 < dt <= GAP_MS:
                if p['gas_ms'] is not None:
                    inj += p['gas_ms'] * p['rpm'] / 120.0 * CYLINDERS * (dt / 1000.0)
                if p['fuel'] == 'GNV':
                    gnv += dt
        niveis = [x['level'] for x in xs if x['level'] >= LEVEL_MIN_VALID]
        ini_n = sorted(niveis[:LEVEL_WINDOW])[len(niveis[:LEVEL_WINDOW]) // 2] if niveis else None
        fim_n = sorted(niveis[-LEVEL_WINDOW:])[len(niveis[-LEVEL_WINDOW:]) // 2] if niveis else None
        consumido = (fim_n - ini_n) if ini_n is not None and fim_n is not None and fim_n >= ini_n else None
        km_ciclo = {}
        for nome in ('MM', 'GPS'):
            am = [(t, v) for t, v in vels[nome] if t >= a and (b is None or t < b)]
            r = _integrar(am, MM_GAP_MS if nome == 'MM' else GPS_GAP_MS)
            km_ciclo[nome] = None if r is None else _round(r[0])
        ciclos.append({'ciclo': i, 'ini_ms': a, 'fim_ms': b, 'iniciado_por': 'INICIO_DA_SESSAO' if i == 0 else 'ABASTECIMENTO',
                       'nivel_ini': ini_n, 'nivel_fim': fim_n, 'nivel_consumido': consumido,
                       'injetor_min_gnv': _round(inj / 60000.0, 4), 'gnv_ms': gnv, 'km_MM': km_ciclo['MM'], 'km_GPS': km_ciclo['GPS'],
                       'km_por_unidade_nivel_PROXY': _round(km_ciclo['MM'] / consumido, 5) if consumido and consumido >= MIN_LEVEL_UNITS and km_ciclo['MM'] else None})
    _grava(os.path.join(saida, 'tank_cycles.csv'), _csv(ciclos))
    # estol, escritas e versões de mapa/curva a partir dos eventos de controle
    estols, escritas, versoes = [], [], []
    for e in evs:
        d = e.get('data') if isinstance(e.get('data'), dict) else {}
        el = e.get('elapsedMs')
        t = int(el - base_e) if (el is not None and base_e is not None) else None
        tipo = e.get('type')
        if tipo == 'engine_stall':
            estols.append({'t_ms': t, 'seq': e.get('sequence'), 'rpm_antes': d.get('rpmBefore'), 'petrol_ms': d.get('petrolMs'), 'map_bar': d.get('mapBar')})
        elif tipo in ('k_batch_confirmed', 'k_factor_batch_confirmed', 'anti_stallo_flag_write', 'anti_stallo_params_write', 'volta_gasolina_write') or \
                (tipo == 'ecu_command' and d.get('workClass') in ('MANUAL_WRITE', 'SAFETY')):
            escritas.append({'t_ms': t, 'seq': e.get('sequence'), 'tipo': tipo, 'id': d.get('adjustmentId'), 'hash_depois': d.get('newHash'), 'confirmada': d.get('readbackValid')})
        elif tipo in ('k_read_map', 'k_map_state', 'k_read_curve', 'k_curve_state') and d.get('hash'):
            versoes.append({'t_ms': t, 'tipo': 'mapa' if 'map' in tipo else 'curva', 'hash': d['hash']})
    _grava(os.path.join(saida, 'stalls.csv'), _csv(estols, ['t_ms', 'seq', 'rpm_antes', 'petrol_ms', 'map_bar']))
    _grava(os.path.join(saida, 'writes.csv'), _csv(escritas, ['t_ms', 'seq', 'tipo', 'id', 'hash_depois', 'confirmada']))
    # rendimento por célula (rpm x MAP) por versão de mapa/curva, só com cobertura (MIN_CELL_S segundos)
    celulas = _celulas(q, versoes)
    _grava(os.path.join(saida, 'cell_yield.csv'), _csv(celulas['linhas'], ['mapa', 'curva', 'rpm_bin', 'map_bin', 'segundos', 'injetor_ms_por_s']))
    _grava(os.path.join(saida, 'cell_compare.csv'), _csv(celulas['comparacao'], ['mapa_a', 'mapa_b', 'rpm_bin', 'map_bin', 'segundos_a', 'segundos_b', 'delta_pct']))
    qualidade = {
        'quadros': len(q), 'raw_completo': bool(q) and not (man.get('quality') or {}).get('raw_complete') is False,
        'lacunas': len(lacunas), 'lacunas_ms': sum(l['ms'] for l in lacunas), 'cobertura_ms': cobertos,
        'linhas_descartadas_max': descartes, 'salto_relogio_ms': salto_relogio,
        'relogio_suspeito': bool(salto_relogio and salto_relogio > 10_000),
        'fechou_limpo': man.get('clean') if 'clean' in man else (True if any(e.get('type') == 'session_stopped' for e in evs) else (False if evs or q else None)),
        'nivel_invalido_quadros': invalidos,
    }
    kpis = {'derive_version': DERIVE_VERSION, 'fontes_velocidade': fontes, 'abastecimentos': len(abastec), 'ciclos': len(ciclos),
            'estols': len(estols), 'escritas': len(escritas), 'versoes_mapa_curva': len(versoes)}
    _grava(os.path.join(saida, 'quality.json'), json.dumps(qualidade, indent=1, sort_keys=True))
    _grava(os.path.join(saida, 'kpis.json'), json.dumps(kpis, indent=1, sort_keys=True))
    _grava(os.path.join(saida, 'derive_version.txt'), DERIVE_VERSION + '\n')
    return {'qualidade': qualidade, 'kpis': kpis, 'ciclos': ciclos, 'abastecimentos': abastec, 'estols': estols, 'escritas': escritas, 'versoes': versoes}


def _celulas(quadros, versoes):
    """Célula = (versão de mapa/curva, faixa de rpm, faixa de MAP): segundos cobertos e ms de injetor GNV por segundo.
    Comparação entre versões SÓ em células com >= MIN_CELL_S segundos nas duas; o resto fica sem delta (não conclui)."""
    if not quadros:
        return {'linhas': [], 'comparacao': []}
    ts = [v['t_ms'] for v in versoes if v['t_ms'] is not None]
    def versao_em(t):
        mapa = curva = 'desconhecido'
        for v in versoes:
            if v['t_ms'] is not None and v['t_ms'] <= t:
                if v['tipo'] == 'mapa':
                    mapa = v['hash'][:12]
                else:
                    curva = v['hash'][:12]
        return mapa, curva
    acc = collections.defaultdict(lambda: [0.0, 0.0])
    for a, b in zip(quadros, quadros[1:]):
        dt = b['e_ms'] - a['e_ms']
        if dt <= 0 or dt > GAP_MS or a['fuel'] != 'GNV' or a['gas_ms'] is None:
            continue
        m, c = versao_em(a['e_ms'])
        k = (m, c, int(a['rpm'] // RPM_BIN) * RPM_BIN, round(int(max(a['map_bar'], 0) / MAP_BIN) * MAP_BIN, 1))
        acc[k][0] += dt / 1000.0
        acc[k][1] += a['gas_ms'] * a['rpm'] / 120.0 * CYLINDERS * (dt / 1000.0)
    linhas = [{'mapa': k[0], 'curva': k[1], 'rpm_bin': k[2], 'map_bin': k[3], 'segundos': _round(v[0], 2),
               'injetor_ms_por_s': _round(v[1] / v[0], 3) if v[0] else None} for k, v in sorted(acc.items())]
    comp = []
    por_mapa = collections.defaultdict(dict)
    for l in linhas:
        if l['mapa'] != 'desconhecido':
            por_mapa[l['mapa']][(l['rpm_bin'], l['map_bin'])] = l
    mapas = sorted(por_mapa)
    for i in range(len(mapas)):
        for j in range(i + 1, len(mapas)):
            for cel in sorted(set(por_mapa[mapas[i]]) & set(por_mapa[mapas[j]])):
                a, b = por_mapa[mapas[i]][cel], por_mapa[mapas[j]][cel]
                ok = a['segundos'] >= MIN_CELL_S and b['segundos'] >= MIN_CELL_S and a['injetor_ms_por_s']
                comp.append({'mapa_a': mapas[i], 'mapa_b': mapas[j], 'rpm_bin': cel[0], 'map_bin': cel[1],
                             'segundos_a': a['segundos'], 'segundos_b': b['segundos'],
                             'delta_pct': _round((b['injetor_ms_por_s'] / a['injetor_ms_por_s'] - 1) * 100, 2) if ok else None})
    return {'linhas': linhas, 'comparacao': comp}


# ================================================================================================================= grafo
def grafo(loja):
    nos, arestas = {}, []

    def no(id_, tipo, rotulo, **p):
        nos.setdefault(id_, {'id': id_, 'label': rotulo, 'tipo': tipo, 'file_type': 'data', **{k: v for k, v in p.items() if v is not None}})

    def aresta(a, b, rel, conf='EXTRACTED'):
        arestas.append({'source': a, 'target': b, 'relation': rel, 'confidence': conf, 'confidence_score': 1.0 if conf == 'EXTRACTED' else 0.7})

    sessoes = sorted(d for d in glob.glob(os.path.join(loja, 'S_*')) if os.path.isdir(d))
    anterior = None
    ciclo_anterior_global = None
    for pasta in sessoes:
        chave = os.path.basename(pasta)
        r = derivar(pasta)
        sid = f"sessao:{chave}"
        q = r['qualidade']
        no(sid, 'Session', chave, quadros=q['quadros'], fechouLimpo=q['fechou_limpo'], relogioSuspeito=q['relogio_suspeito'],
           semStop=(q['fechou_limpo'] is False))
        ciclos = r['ciclos']
        ids = []
        for c in ciclos:
            cid = f"tanque:{chave}:{c['ciclo']}"
            ids.append(cid)
            no(cid, 'TankCycle', f"Tanque {chave}#{c['ciclo']}", nivelIni=c['nivel_ini'], nivelFim=c['nivel_fim'],
               injetorMin=c['injetor_min_gnv'], kmMM=c['km_MM'], kmPorUnidadeNivelPROXY=c['km_por_unidade_nivel_PROXY'])
            aresta(cid, sid, 'pertence_a')
            if ciclo_anterior_global and c['iniciado_por'] == 'ABASTECIMENTO':
                aresta(cid, ciclo_anterior_global, 'abasteceu_depois_de')
            elif ciclo_anterior_global and c['ciclo'] == 0 and _abasteceu_entre(ciclo_anterior_global, c, nos):
                aresta(cid, ciclo_anterior_global, 'abasteceu_depois_de')
            ciclo_anterior_global = cid
        versoes = collections.OrderedDict()
        for v in r['versoes']:
            nid = f"{'mapa' if v['tipo'] == 'mapa' else 'curva'}:{v['hash'][:12]}"
            no(nid, 'MapVersion' if v['tipo'] == 'mapa' else 'CurveVersion', nid, hash=v['hash'])
            aresta(sid, nid, 'usa_mapa' if v['tipo'] == 'mapa' else 'usa_curva')
            versoes[nid] = v
        evs = eventos(pasta)
        perfil = _perfil_ecu(evs)
        if perfil:
            pid = f"ecu:{perfil['id']}"
            no(pid, 'EcuProfile', pid, **{k: v for k, v in perfil.items() if k != 'id'})
            aresta(sid, pid, 'tem_perfil_ecu')
        ult_escrita = None
        marcos = sorted([(w['t_ms'] if w['t_ms'] is not None else -1, 'w', w) for w in r['escritas']] +
                        [(s['t_ms'] if s['t_ms'] is not None else -1, 's', s) for s in r['estols']], key=lambda x: (x[0], x[1] == 's'))
        for t, tipo, item in marcos:
            if tipo == 'w':
                wid = f"escrita:{chave}:{item['seq']}"
                no(wid, 'WriteAction', item['tipo'], confirmada=item['confirmada'], t_ms=item['t_ms'])
                aresta(wid, sid, 'pertence_a')
                if item.get('hash_depois'):
                    alvo = next((n for n in nos if n.endswith(item['hash_depois'][:12]) and nos[n]['tipo'] in ('MapVersion', 'CurveVersion')), None)
                    if alvo:
                        aresta(wid, alvo, 'produziu')
                ult_escrita = wid
            else:
                stid = f"estol:{chave}:{item['seq']}"
                no(stid, 'Stall', 'Estol', t_ms=item['t_ms'], rpmAntes=item['rpm_antes'])
                aresta(stid, sid, 'pertence_a')
                if ult_escrita:
                    aresta(ult_escrita, stid, 'escreveu_antes_de', 'INFERRED')
        anterior = sid
    return {'directed': True, 'multigraph': False, 'graph': {'schema_version': 1, 'origem': 'omegas-export-v2', 'derive_version': DERIVE_VERSION},
            'nodes': [nos[k] for k in sorted(nos)], 'links': arestas}


def _abasteceu_entre(cid_anterior, ciclo, nos):
    ant = nos.get(cid_anterior, {})
    fim, ini = ant.get('nivelFim'), ciclo.get('nivel_ini')
    return fim is not None and ini is not None and fim - ini >= REFUEL_DROP


def _perfil_ecu(evs):
    versao = tipo = refs = None
    for e in evs:
        d = e.get('data') if isinstance(e.get('data'), dict) else {}
        if e.get('type') in ('autocal_native_snapshot', 'autocal_manual_snapshot'):
            for f in d.get('fields', []) or []:
                if f.get('key') == 'MODULE_VERSION' and f.get('status') == 'VALID' and f.get('rawValues'):
                    versao = f['rawValues'][0]
        if e.get('type') == 'level_calibration':
            tipo, refs = d.get('typeRaw'), d.get('refs')
    if versao is None and tipo is None:
        return None
    return {'id': f"v{versao}-t{tipo}-{'_'.join(map(str, refs or []))}", 'moduleVersion': versao, 'sensorTypeRaw': tipo, 'levelRefs': refs}


# ====================================================================================================================== CLI
def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = p.add_subparsers(dest='cmd', required=True)
    a = sub.add_parser('ingerir'); a.add_argument('entradas', nargs='+'); a.add_argument('--loja', required=True)
    c = sub.add_parser('converter'); c.add_argument('entrada'); c.add_argument('--loja', required=True)
    d = sub.add_parser('derivar'); d.add_argument('sessao')
    g = sub.add_parser('grafo'); g.add_argument('loja'); g.add_argument('--saida', required=True)
    args = p.parse_args(argv)
    if args.cmd == 'ingerir':
        r = ingerir(args.entradas, args.loja)
        print(f"{r['novos']} trechos novos, {r['repetidos']} repetidos (ignorados), {r['conflitos']} conflitos, {r['invalidos']} inválidos")
    elif args.cmd == 'converter':
        k = converter(args.entrada, args.loja)
        print('sessão convertida: ' + k if k else 'nada a converter')
        return 0 if k else 1
    elif args.cmd == 'derivar':
        r = derivar(args.sessao)
        print(f"{r['qualidade']['quadros']} quadros; {len(r['ciclos'])} ciclos de tanque; {len(r['abastecimentos'])} abastecimentos")
    elif args.cmd == 'grafo':
        gr = grafo(args.loja)
        _grava(os.path.join(args.saida, 'grafo_graphify.json'), json.dumps(gr, ensure_ascii=False, indent=1, sort_keys=True))
        print(f"{len(gr['nodes'])} nós, {len(gr['links'])} arestas")
    return 0


if __name__ == '__main__':
    sys.exit(main())
