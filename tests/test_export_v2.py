"""Export v2: bruta -> ingestão idempotente, conversor v1, derivação (séries, segmentos, degraus, tanques, células) e grafo."""
import copy, csv, hashlib, io, json, os, shutil, subprocess, sys, tempfile, unittest, zipfile

ROOT = os.path.join(os.path.dirname(__file__), '..')
sys.path.insert(0, os.path.join(ROOT, 'tools', 'sessoes'))
import export_v2 as ev2  # noqa: E402

FIX_RAW = os.path.join(ROOT, 'tests', 'fixtures', 'export-v2', 'raw')
BASE_E = 1_000_000
WALL0 = 1_790_000_000_000


def quadro(rpm=900, gas=1950, petrol=1200, fuel=0x90, water=69, level=126, mapraw=410, volt=224):
    b = bytearray(34)
    b[0:2] = rpm.to_bytes(2, 'little'); b[6:8] = gas.to_bytes(2, 'little'); b[8:10] = petrol.to_bytes(2, 'little')
    b[11] = fuel; b[12] = water; b[13] = level; b[14:16] = (2016).to_bytes(2, 'little'); b[16] = 60
    b[17:19] = mapraw.to_bytes(2, 'little'); b[19] = volt
    return bytes(b).hex()


def cab(nome, cols, chave='S_t_b_1'):
    return f"#omegas-raw-v2 stream={nome} cols={cols} session={chave} boot=b segment=1 base_e={BASE_E}\n"


def mk_sessao(pasta, frames, mm=(), eventos=(), heartbeat=(), manifest=None):
    """frames: [(t_ms, kwargs de quadro)]; mm: [(t_ms, km/h)]; eventos: [(t_ms, tipo, data)]."""
    os.makedirs(os.path.join(pasta, 'raw'), exist_ok=True)
    with open(os.path.join(pasta, 'raw', 'frames_48_01.0001.tsv'), 'w') as fh:
        fh.write(cab('frames_48_01', 'e_ms,hex'))
        for t, kw in frames:
            fh.write(f"{t}\t{quadro(**kw)}\n")
    if mm:
        with open(os.path.join(pasta, 'raw', 'mm.0001.tsv'), 'w') as fh:
            fh.write(cab('mm', 'e_ms,wall_ms,kind,json'))
            for t, v in mm:
                fh.write(f"{t}\t{WALL0 + t}\tcar_info\t{json.dumps({'speed': v, 'complete': True})}\n")
    if heartbeat:
        with open(os.path.join(pasta, 'raw', 'heartbeat.0001.tsv'), 'w') as fh:
            fh.write(cab('heartbeat', 'e_ms,wall_ms,engine_frames,engine_failures,recorded_frames,dropped_lines'))
            for t, wall in heartbeat:
                fh.write(f"{t}\t{wall}\t0\t0\t0\t0\n")
    if eventos:
        with open(os.path.join(pasta, 'events_0001.jsonl'), 'w') as fh:
            for i, (t, tipo, data) in enumerate(eventos):
                fh.write(json.dumps({'sequence': i + 1, 'recordedAtMs': WALL0 + t, 'elapsedMs': BASE_E + t, 'type': tipo, 'data': data}) + '\n')
    if manifest is not None:
        with open(os.path.join(pasta, 'manifest.json'), 'w') as fh:
            json.dump(manifest, fh)


def seg(n_seg, inicio, **kw):
    """n_seg segundos de quadros a ~15 Hz (66 ms) a partir de `inicio` ms."""
    return [(inicio + i * 66, kw) for i in range(int(n_seg * 1000 / 66))]


class Base(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = self.tmp.name

    def tearDown(self):
        self.tmp.cleanup()

    def sessao(self, nome='S_t_b_1', **kw):
        pasta = os.path.join(self.dir, 'loja', nome)
        mk_sessao(pasta, **kw)
        return pasta

    def tabela(self, pasta, arq):
        return list(csv.DictReader(open(os.path.join(pasta, 'derived', arq), encoding='utf-8')))


class Decodificacao(unittest.TestCase):
    def test_primeiro_quadro_do_fixture_do_kotlin(self):
        _, linhas = ev2.ler_tsv(os.path.join(FIX_RAW, 'frames_48_01.0001.tsv'))
        d = ev2.decodificar_quadro(linhas[0][1])
        self.assertEqual((d['rpm'], d['level'], d['fuel'], d['water_c']), (900, 126, 'GNV', 40))
        self.assertAlmostEqual(d['gas_ms'], 1950 * 0.00256, places=4)
        self.assertAlmostEqual(d['map_bar'], 0.41, places=3)
        self.assertAlmostEqual(d['volts'], 14.0, places=3)

    def test_quadro_curto_ou_lixo_e_desconhecido(self):
        self.assertIsNone(ev2.decodificar_quadro('abcd'))
        self.assertIsNone(ev2.decodificar_quadro('zz' * 34))

    def test_gas_zero_e_none_nao_zero(self):
        d = ev2.decodificar_quadro(quadro(gas=0, volt=0))
        self.assertIsNone(d['gas_ms'])
        self.assertIsNone(d['volts'])

    def test_linha_cortada_pela_queda_de_energia_e_ignorada(self):
        with tempfile.TemporaryDirectory() as t:
            p = os.path.join(t, 'f.tsv')
            open(p, 'w').write(cab('f', 'e_ms,hex') + f"0\t{quadro()}\n66\t{quadro()[:20]}")
            self.assertEqual(len(ev2.ler_tsv(p)[1]), 1)


class Derivacao(Base):
    def test_series_1hz_e_5hz_do_fixture_do_kotlin(self):
        pasta = os.path.join(self.dir, 'S_abcd1234_9f8e7d6c_7000')
        shutil.copytree(FIX_RAW, os.path.join(pasta, 'raw'))
        r = ev2.derivar(pasta)
        um = self.tabela(pasta, 'series_1hz.csv')
        cinco = self.tabela(pasta, 'series_5hz.csv')
        self.assertEqual(len(um), 10)
        self.assertGreaterEqual(len(cinco), 45)
        self.assertEqual(um[0]['combustivel'], 'GNV')
        self.assertIn(um[0]['nivel'], ('126', '127'))
        self.assertEqual(r['qualidade']['quadros'], 150)
        self.assertTrue(r['qualidade']['raw_completo'])
        self.assertEqual(r['qualidade']['lacunas'], 0)
        self.assertEqual(r['qualidade']['salto_relogio_ms'], 0)
        # um único car_info (sem 2 leituras) não vira km: None, nunca 0
        self.assertIsNone(r['kpis']['fontes_velocidade']['MM'])
        self.assertIsNone(r['kpis']['fontes_velocidade']['GPS'])
        self.assertIsNone(r['kpis']['fontes_velocidade']['RPM_PROXY']['km'])
        self.assertGreater(r['kpis']['fontes_velocidade']['RPM_PROXY']['andando_ms'], 0)
        self.assertGreater(r['kpis']['fontes_velocidade']['RPM_PROXY']['parado_ms'], 0)

    def test_km_por_fonte_e_parado_x_andando_sem_misturar(self):
        pasta = self.sessao(frames=seg(60, 0, rpm=2200, level=126), mm=[(t * 1000, 36 if t >= 10 else 0) for t in range(0, 61)])
        r = ev2.derivar(pasta)
        mm = r['kpis']['fontes_velocidade']['MM']
        self.assertAlmostEqual(mm['km'], 0.5, delta=0.01)             # 50 s a 36 km/h (+ a rampa de 1 s do trapézio)
        self.assertAlmostEqual(mm['parado_ms'], 10000, delta=1100)
        self.assertIsNone(r['kpis']['fontes_velocidade']['GPS'])
        fontes = {s['fonte'] for s in self.tabela(pasta, 'segments.csv')}
        self.assertEqual(fontes, {'MM', 'RPM_PROXY'})

    def test_abastecimento_no_meio_da_sessao_vira_dois_tanques(self):
        frames = seg(40, 0, level=220, rpm=1500) + seg(40, 40_000, level=60, rpm=1500)
        pasta = self.sessao(frames=frames, mm=[(t * 1000, 36) for t in range(0, 81)])
        r = ev2.derivar(pasta)
        self.assertEqual(len(r['abastecimentos']), 1)
        self.assertEqual((r['abastecimentos'][0]['de'], r['abastecimentos'][0]['queda']), (220, 160))
        ciclos = self.tabela(pasta, 'tank_cycles.csv')
        self.assertEqual([c['iniciado_por'] for c in ciclos], ['INICIO_DA_SESSAO', 'ABASTECIMENTO'])
        self.assertEqual(ciclos[1]['nivel_ini'], '60')
        km = sum(float(c['km_MM']) for c in ciclos)
        self.assertAlmostEqual(km, 0.8, delta=0.05)
        passos = self.tabela(pasta, 'level_steps.csv')
        self.assertEqual([p['tipo'] for p in passos], ['INITIAL', 'STEP'])

    def test_nivel_sem_leitura_19_a_22_nunca_vira_degrau(self):
        pasta = self.sessao(frames=seg(30, 0, level=21) + seg(30, 30_000, level=180))
        r = ev2.derivar(pasta)
        self.assertEqual(len(r['abastecimentos']), 0)
        self.assertGreater(r['qualidade']['nivel_invalido_quadros'], 100)

    def test_sessao_sem_stop_e_marcada(self):
        pasta = self.sessao(frames=seg(5, 0), eventos=[(0, 'session_started', {})])
        self.assertIs(ev2.derivar(pasta)['qualidade']['fechou_limpo'], False)
        pasta2 = self.sessao('S_t_b_2', frames=seg(5, 0), eventos=[(0, 'session_started', {}), (5000, 'session_stopped', {})])
        self.assertIs(ev2.derivar(pasta2)['qualidade']['fechou_limpo'], True)

    def test_relogio_de_parede_com_salto_e_detectado_pelo_batimento_e_a_serie_nao_muda(self):
        hb = [(0, WALL0), (5000, WALL0 + 5000), (10000, WALL0 + 10000 + 3_600_000)]
        pasta = self.sessao(frames=seg(12, 0), heartbeat=hb)
        r = ev2.derivar(pasta)
        self.assertEqual(r['qualidade']['salto_relogio_ms'], 3_600_000)
        self.assertTrue(r['qualidade']['relogio_suspeito'])
        self.assertEqual(len(self.tabela(pasta, 'series_1hz.csv')), 12)   # a série usa só o monotônico

    def test_lacuna_maior_que_2s_e_contada_e_nao_entra_nas_somas(self):
        pasta = self.sessao(frames=seg(5, 0) + seg(5, 20_000))
        q = ev2.derivar(pasta)['qualidade']
        self.assertEqual(q['lacunas'], 1)
        self.assertLess(q['cobertura_ms'], 10_500)


class Celulas(Base):
    def test_so_compara_celula_com_cobertura_nas_duas_versoes(self):
        # versão A (mapa-A) 60 s; versão B 60 s na mesma célula (2200 rpm, 0,62 bar) e 5 s numa célula só da B
        frames = seg(60, 0, rpm=2200, mapraw=620, gas=3000) + seg(60, 60_000, rpm=2200, mapraw=620, gas=2400) + seg(5, 120_000, rpm=3200, mapraw=900, gas=2000)
        ev = [(0, 'k_read_map', {'hash': 'mapa-A' + '0' * 6}), (60_000, 'k_read_map', {'hash': 'mapa-B' + '0' * 6})]
        pasta = self.sessao(frames=frames, eventos=ev)
        ev2.derivar(pasta)
        comp = self.tabela(pasta, 'cell_compare.csv')
        comum = [c for c in comp if c['rpm_bin'] == '2000']
        self.assertEqual(len(comum), 1)
        self.assertAlmostEqual(float(comum[0]['delta_pct']), -20.0, delta=1.0)
        # célula que só a versão B cobriu não gera comparação
        self.assertFalse([c for c in comp if c['rpm_bin'] == '3000'])

    def test_celula_com_poucos_segundos_fica_sem_delta(self):
        frames = seg(5, 0, rpm=2200, mapraw=620, gas=3000) + seg(60, 5_000, rpm=2200, mapraw=620, gas=2400)
        ev = [(0, 'k_read_map', {'hash': 'mapa-A' + '0' * 6}), (5_000, 'k_read_map', {'hash': 'mapa-B' + '0' * 6})]
        pasta = self.sessao(frames=frames, eventos=ev)
        ev2.derivar(pasta)
        comp = self.tabela(pasta, 'cell_compare.csv')
        self.assertEqual(len(comp), 1)
        self.assertEqual(comp[0]['delta_pct'], '')


def parte_zip(caminho, chave, prefixo, itens, indice, ultima):
    """Imita SessionPartPlanner.writeZip: <prefixo>/<entry> + parte.json com sha256 por membro."""
    listing = []
    with zipfile.ZipFile(caminho, 'w', zipfile.ZIP_DEFLATED) as z:
        for entry, source, de, dados, whole in itens:
            z.writestr(f"{prefixo}/{entry}", dados)
            item = {'entry': entry, 'source': source, 'sha256': hashlib.sha256(dados).hexdigest()}
            if whole:
                item['whole'] = True
            else:
                item.update(fromByte=de, toByte=de + len(dados))
            listing.append(item)
        z.writestr(f"{prefixo}/parte.json", json.dumps({'format': 'omegas-session-part-v1', 'sessionKey': chave, 'partIndex': indice,
                                                          'isLast': ultima, 'entries': listing}))


class Ingestao(Base):
    def setUp(self):
        super().setUp()
        self.raw = open(os.path.join(FIX_RAW, 'frames_48_01.0001.tsv'), 'rb').read()
        corte = self.raw.index(b'\n', len(self.raw) // 2) + 1
        self.a, self.b = self.raw[:corte], self.raw[corte:]
        self.corte = corte
        self.p1 = os.path.join(self.dir, 'in', 'S_x_parte_0001.zip')
        self.p2 = os.path.join(self.dir, 'in', 'S_x_parte_0002.zip')
        os.makedirs(os.path.dirname(self.p1))
        parte_zip(self.p1, 'S_x_b_1', 'S_x', [('raw/frames_48_01.0001.from_0.tsv', 'frames_48_01.0001.tsv', 0, self.a, False)], 1, False)
        parte_zip(self.p2, 'S_x_b_1', 'S_x', [('raw/frames_48_01.0001.from_%d.tsv' % self.corte, 'frames_48_01.0001.tsv', self.corte, self.b, False)], 2, True)
        self.loja = os.path.join(self.dir, 'loja2')

    def montado(self):
        return open(os.path.join(self.loja, 'S_x_b_1', 'raw', 'frames_48_01.0001.tsv'), 'rb').read()

    def test_duas_partes_remontam_o_arquivo_byte_a_byte_em_qualquer_ordem(self):
        r = ev2.ingerir([self.p2, self.p1], self.loja)
        self.assertEqual(r['novos'], 2)
        self.assertEqual(self.montado(), self.raw)

    def test_copia_duplicada_zip_renomeado_e_mesma_parte_em_outro_zip_nao_duplicam(self):
        ev2.ingerir([self.p1, self.p2], self.loja)
        copia = os.path.join(self.dir, 'in2', 'S_x_parte_0001 (12).zip')
        os.makedirs(os.path.dirname(copia))
        shutil.copy(self.p1, copia)
        r = ev2.ingerir([self.dir + '/in', copia, self.dir + '/in2'], self.loja)
        self.assertEqual((r['novos'], r['conflitos']), (0, 0))
        self.assertGreaterEqual(r['repetidos'], 2)
        self.assertEqual(self.montado(), self.raw)
        log = list(csv.DictReader(open(os.path.join(self.loja, 'index', 'ingest_log.csv'))))
        self.assertEqual(len(log), 2)

    def test_membro_corrompido_nao_entra_e_vira_invalido(self):
        ruim = os.path.join(self.dir, 'in3', 'ruim.zip')
        os.makedirs(os.path.dirname(ruim))
        with zipfile.ZipFile(ruim, 'w') as z:
            z.writestr('S_x/raw/frames_48_01.0001.from_0.tsv', b'adulterado\n')
            z.writestr('S_x/parte.json', json.dumps({'sessionKey': 'S_x_b_1', 'partIndex': 1, 'entries': [
                {'entry': 'raw/frames_48_01.0001.from_0.tsv', 'source': 'frames_48_01.0001.tsv', 'fromByte': 0, 'sha256': 'f' * 64}]}))
        r = ev2.ingerir([ruim], self.loja)
        self.assertEqual((r['novos'], r['invalidos']), (0, 1))
        self.assertFalse(os.path.exists(os.path.join(self.loja, 'S_x_b_1', 'raw')))

    def test_mesmo_trecho_com_outro_conteudo_e_conflito_e_nao_sobrescreve(self):
        ev2.ingerir([self.p1], self.loja)
        outro = os.path.join(self.dir, 'in4', 'p.zip')
        os.makedirs(os.path.dirname(outro))
        parte_zip(outro, 'S_x_b_1', 'S_x', [('raw/frames_48_01.0001.from_0.tsv', 'frames_48_01.0001.tsv', 0, b'#outro\n', False)], 1, False)
        r = ev2.ingerir([outro], self.loja)
        self.assertEqual(r['conflitos'], 1)
        self.assertEqual(open(os.path.join(self.loja, 'S_x_b_1', 'raw', 'frames_48_01.0001.tsv'), 'rb').read(), self.a)

    def test_pacote_leve_do_resumo_entra_uma_vez(self):
        resumo = {'schema': 'omegas-session-summary-v1', 'identity': {'sessionKey': 'S_x_b_1', 'sessionId': 's'}}
        pacote = os.path.join(self.dir, 'in5', 'S_x_resumo.zip')
        os.makedirs(os.path.dirname(pacote))
        with zipfile.ZipFile(pacote, 'w') as z:
            z.writestr('s/session_summary.json', json.dumps(resumo))
            z.writestr('s/series_1hz.csv', 't_s,rpm\n0,900\n')
            z.writestr('tank_ledger.json', '{}')
        self.assertEqual(ev2.ingerir([pacote], self.loja)['novos'], 2)
        self.assertEqual(ev2.ingerir([pacote], self.loja)['novos'], 0)
        self.assertTrue(os.path.isfile(os.path.join(self.loja, 'S_x_b_1', 'resumo', 'session_summary.json')))

    def test_arquivos_de_mesmo_nome_na_raiz_e_em_resumo_entram_separados(self):
        z = os.path.join(self.dir, 'in6', 'p.zip')
        os.makedirs(os.path.dirname(z))
        parte_zip(z, 'S_y_b_1', 'S_y', [('manifest.json', 'manifest.json', 0, b'{"raiz":1}', True),
                                       ('resumo/manifest.json', 'resumo/manifest.json', 0, b'{"resumo":1}', True)], 1, True)
        ev2.ingerir([z], self.loja)
        self.assertEqual(open(os.path.join(self.loja, 'S_y_b_1', 'manifest.json')).read(), '{"raiz":1}')
        self.assertEqual(open(os.path.join(self.loja, 'S_y_b_1', 'resumo', 'manifest.json')).read(), '{"resumo":1}')


def v1_evento(seq, t, rpm, level, fuel_byte=0x90, gas=3000):
    return {'format': 'omegas-session-log-v1', 'sequence': seq, 'recordedAtMs': WALL0 + t, 'type': 'telemetry', 'source': 'mp48',
            'data': {'captured_elapsed_ms': BASE_E + t, 'rpm': rpm, 'level_raw': level, 'fuel_byte': fuel_byte, 'gas_raw': gas,
                     'petrol_raw': 1200, 'water_raw': 69, 'map_raw': 500, 'gas_pressure_raw': 2000, 'gas_temperature_raw': 60,
                     'unknown_raw_19': 224}}


class ConversorV1(Base):
    def v1(self, nome, n=400, stop=False, wall_shift=0):
        pasta = os.path.join(self.dir, nome)
        os.makedirs(pasta)
        evs = [v1_evento(i + 1, i * 250, 1800 if i > 40 else 900, 150) for i in range(n)]
        if stop:
            evs.append({'sequence': n + 1, 'recordedAtMs': WALL0 + n * 250, 'type': 'session_stopped', 'data': {}})
        with open(os.path.join(pasta, 'events_0001.jsonl'), 'w') as fh:
            for e in evs:
                e = copy.deepcopy(e)
                e['recordedAtMs'] += wall_shift
                fh.write(json.dumps(e) + '\n')
        json.dump({'sessionId': 'session_2026-10-13_10-00-00_dev12345', 'createdAtMs': WALL0 + wall_shift, 'deviceId': 'dev12345abc',
                   'metadata': {'appVersion': '8.0.0-test-debug'}}, open(os.path.join(pasta, 'manifest.json'), 'w'))
        return pasta

    def test_sessao_v1_vira_v2_com_chave_estavel_e_bruta_parcial_marcada(self):
        a = self.v1('a')
        loja = os.path.join(self.dir, 'loja3')
        k = ev2.converter(a, loja)
        self.assertTrue(k.startswith('S_dev12345_L'))
        r = ev2.derivar(os.path.join(loja, k))
        self.assertEqual(r['qualidade']['quadros'], 400)
        self.assertFalse(r['qualidade']['raw_completo'])
        self.assertIs(r['qualidade']['fechou_limpo'], False)     # sem session_stopped
        self.assertEqual(self.tabela(os.path.join(loja, k), 'series_1hz.csv')[0]['nivel'], '150')

    def test_mesma_sessao_em_pasta_e_em_zip_da_a_mesma_chave_mesmo_com_relogio_errado(self):
        a = self.v1('a')
        b = self.v1('b')
        zb = os.path.join(self.dir, 'b.zip')
        with zipfile.ZipFile(zb, 'w') as z:
            for f in os.listdir(b):
                z.write(os.path.join(b, f), 'sessao/' + f)
        loja = os.path.join(self.dir, 'loja4')
        k1 = ev2.converter(a, loja)
        k2 = ev2.converter(zb, loja)
        self.assertEqual(k1, k2)
        # o relógio de parede errado (1 ano antes) muda a parede, mas offset e chave seguem o monotônico: outra cópia com a parede
        # deslocada de um ano que ainda tem o MESMO monotônico relativo se reconhece pelo (aparelho, início monotônico) ao menos
        c = self.v1('c', wall_shift=-365 * 86_400_000)
        k3 = ev2.converter(c, loja)
        self.assertEqual(k3.split('_')[1], k1.split('_')[1])

    def test_v1_com_stop_e_limpo(self):
        a = self.v1('a', stop=True)
        k = ev2.converter(a, os.path.join(self.dir, 'loja5'))
        self.assertTrue(json.load(open(os.path.join(self.dir, 'loja5', k, 'manifest.json')))['clean'])

    def test_pasta_sem_eventos_nao_converte(self):
        vazio = os.path.join(self.dir, 'vazio')
        os.makedirs(vazio)
        self.assertIsNone(ev2.converter(vazio, os.path.join(self.dir, 'loja6')))


class Grafo(Base):
    def test_nos_e_arestas_do_dono(self):
        loja = os.path.join(self.dir, 'loja')
        ev1 = [(0, 'autocal_native_snapshot', {'fields': [{'key': 'MODULE_VERSION', 'status': 'VALID', 'rawValues': [100]}]}),
               (10, 'level_calibration', {'typeRaw': 129, 'refs': [39, 93, 143, 219]}),
               (100, 'k_read_map', {'hash': 'mapaaaaaaaaaaaaaa'}), (200, 'k_read_curve', {'hash': 'curvaaaaaaaaaaaaa'}),
               (20_000, 'k_batch_confirmed', {'adjustmentId': 'adj1', 'newHash': 'mapabbbbbbbbbbbbbb', 'readbackValid': True}),
               (20_500, 'k_read_map', {'hash': 'mapabbbbbbbbbbbbbb'}),
               (30_000, 'engine_stall', {'rpmBefore': 900, 'petrolMs': 2.0, 'mapBar': 0.3})]
        mk_sessao(os.path.join(loja, 'S_t_b_1'), seg(40, 0, level=220, rpm=1500), eventos=ev1)
        mk_sessao(os.path.join(loja, 'S_t_b_2'), seg(40, 0, level=60, rpm=1500), eventos=[(100, 'k_read_map', {'hash': 'mapabbbbbbbbbbbbbb'})])
        g = ev2.grafo(loja)
        tipos = {n['tipo'] for n in g['nodes']}
        self.assertEqual(tipos, {'Session', 'TankCycle', 'MapVersion', 'CurveVersion', 'EcuProfile', 'WriteAction', 'Stall'})
        rel = {(e['source'], e['relation'], e['target']) for e in g['links']}
        self.assertIn(('sessao:S_t_b_1', 'usa_mapa', 'mapa:mapaaaaaaaaa'), rel)
        self.assertIn(('tanque:S_t_b_2:0', 'abasteceu_depois_de', 'tanque:S_t_b_1:0'), rel)
        self.assertIn(('escrita:S_t_b_1:5', 'escreveu_antes_de', 'estol:S_t_b_1:7'), rel)
        self.assertIn(('escrita:S_t_b_1:5', 'produziu', 'mapa:mapabbbbbbbb'), rel)
        escreveu = [e for e in g['links'] if e['relation'] == 'escreveu_antes_de'][0]
        self.assertEqual(escreveu['confidence'], 'INFERRED')

    def test_sessao_sem_eventos_nao_inventa_mapa_nem_escrita(self):
        loja = os.path.join(self.dir, 'loja')
        mk_sessao(os.path.join(loja, 'S_t_b_1'), seg(10, 0))
        g = ev2.grafo(loja)
        self.assertEqual({n['tipo'] for n in g['nodes']}, {'Session', 'TankCycle'})


class Cli(Base):
    def test_cli_isolado_ingere_deriva_e_gera_grafo_de_forma_idempotente(self):
        entrada = os.path.join(self.dir, 'in')
        os.makedirs(entrada)
        raw = open(os.path.join(FIX_RAW, 'frames_48_01.0001.tsv'), 'rb').read()
        parte_zip(os.path.join(entrada, 'p.zip'), 'S_abcd1234_9f8e7d6c_7000', 'S', [('raw/frames_48_01.0001.from_0.tsv', 'frames_48_01.0001.tsv', 0, raw, False)], 1, True)
        loja = os.path.join(self.dir, 'loja')
        saida = os.path.join(self.dir, 'saida')
        exe = [sys.executable, '-I', os.path.join(ROOT, 'tools', 'sessoes', 'export_v2.py')]
        for cmd in (['ingerir', entrada, '--loja', loja], ['derivar', os.path.join(loja, 'S_abcd1234_9f8e7d6c_7000')], ['grafo', loja, '--saida', saida]):
            r = subprocess.run(exe + cmd, capture_output=True, text=True)
            self.assertEqual(r.returncode, 0, r.stderr)
        antes = open(os.path.join(saida, 'grafo_graphify.json'), 'rb').read()
        r = subprocess.run(exe + ['ingerir', entrada, '--loja', loja], capture_output=True, text=True)
        self.assertIn('0 trechos novos', r.stdout)
        subprocess.run(exe + ['grafo', loja, '--saida', saida], capture_output=True, text=True)
        self.assertEqual(antes, open(os.path.join(saida, 'grafo_graphify.json'), 'rb').read())


if __name__ == '__main__':
    unittest.main()
