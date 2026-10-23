"""Analisador offline dos resumos de sessão (omegas-session-summary-v1): dedupe, relógio, tanques, mapas, grafo."""
import copy, csv, io, json, os, subprocess, sys, tempfile, unittest, zipfile

ROOT = os.path.join(os.path.dirname(__file__), '..')
sys.path.insert(0, os.path.join(ROOT, 'tools', 'sessoes'))
import analisar_resumos as ar  # noqa: E402

FIX = os.path.join(ROOT, 'tests', 'fixtures', 'resumos')
BASE = json.load(open(os.path.join(FIX, 'sessao_sintetica_final.json'), encoding='utf-8'))
SERIE = open(os.path.join(FIX, 'sessao_sintetica_series_1hz.csv'), encoding='utf-8').read()
T0 = BASE['identity']['startMs']


def variante(n, inicio_nivel, fim_nivel, km=1.0, inicio=None, mapa='mapa-A', curva='curva-A', suspeito=False,
             gps_ref=None, com_km=True, final=True, eventos=None):
    """Sessão sintética derivada do fixture real do Kotlin: muda identidade, nível, km, mapa/curva e relógio."""
    s = copy.deepcopy(BASE)
    s['identity']['sessionId'] = f'session_x_{n}'
    s['identity']['sessionUuid'] = f'uuid-{n}'
    s['identity']['startMs'] = inicio if inicio is not None else T0 + n * 3_600_000
    if eventos is not None:
        s['identity']['events'] = eventos
    s['final'] = final
    seg = s['tankSegments'][0]
    seg['startRaw'], seg['endRaw'] = inicio_nivel, fim_nivel
    s['gnvLevel']['startRaw'], s['gnvLevel']['endRaw'] = inicio_nivel, fim_nivel
    ms = s['movement']['bySource']['MM']
    for alvo in (seg['km'], seg['kmOnGnv']):
        alvo['MM'] = km if com_km else None
    ms['km'] = ms['kmOnGnv'] = km if com_km else None
    s['movement']['primary']['km'] = km if com_km else None
    if not com_km:
        s['movement']['bySource']['MM'].update(available=False, samples=0, coverageMs=None, movingMs=None, stoppedMs=None)
        s['movement']['primary'] = {'source': 'RPM_PROXY', 'proxy': True, 'movingMs': 1000.0, 'stoppedMs': 500.0, 'km': None}
        seg['km']['MM'] = seg['kmOnGnv']['MM'] = None
    s['mapCurve']['map']['hashLast'] = mapa
    s['mapCurve']['curve']['hashLast'] = curva
    s['mapCurve']['identityKnown'] = mapa is not None or curva is not None
    if suspeito:
        s['clock']['clockSuspect'] = True
        s['clock']['suspectReasons'] = ['WALL_BEFORE_2026']
        s['clock']['gpsReferenceOffsetMs'] = gps_ref
    return s


class Entrada(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = self.tmp.name

    def tearDown(self):
        self.tmp.cleanup()

    def grava(self, rel, resumo, serie=SERIE):
        pasta = os.path.join(self.dir, os.path.dirname(rel))
        os.makedirs(pasta, exist_ok=True)
        with open(os.path.join(self.dir, rel), 'w', encoding='utf-8') as fh:
            json.dump(resumo, fh)
        if serie is not None:
            with open(os.path.join(pasta, 'series_1hz.csv'), 'w', encoding='utf-8') as fh:
                fh.write(serie)

    def zip(self, nome, membros):
        with zipfile.ZipFile(os.path.join(self.dir, nome), 'w', zipfile.ZIP_DEFLATED) as z:
            for caminho, conteudo in membros.items():
                z.writestr(caminho, conteudo if isinstance(conteudo, str) else json.dumps(conteudo))

    def analisa(self):
        return ar.analisar([self.dir])


class Dedupe(Entrada):
    def test_mesma_sessao_em_varias_copias_conta_uma_vez(self):
        s = variante(1, 60, 100)
        self.grava('a/session_summary.json', s)
        self.grava('b/session_summary.json.json', s)          # extensão .json a mais
        self.grava('c/session_summary (12).json', s)          # (12) no nome
        self.zip('x.zip', {'sessao/resumo/session_summary.json': s, 'sessao/resumo/series_1hz.csv': SERIE})
        self.zip('y.zip', {'outro/session_summary.json': s})  # mesma sessão em outro zip
        r = self.analisa()
        self.assertEqual(len(r['sessoes']), 1)
        self.assertEqual(r['relatorio']['copias_identicas_removidas'], 4)
        self.assertEqual(r['relatorio']['sessoes_unicas'], 1)

    def test_versao_parcial_perde_para_a_final_da_mesma_sessao(self):
        parcial = variante(1, 60, 70, final=False, eventos=100)
        final = variante(1, 60, 100, final=True, eventos=900)
        self.grava('a/session_summary.json', parcial)
        self.grava('b/session_summary.json', final)
        r = self.analisa()
        self.assertEqual(len(r['sessoes']), 1)
        self.assertEqual(r['sessoes'][0]['final'], True)
        self.assertEqual(r['sessoes'][0]['nivelFim'], 100)
        self.assertEqual(r['relatorio']['versoes_descartadas'], 1)

    def test_arquivo_que_nao_e_resumo_e_ignorado_sem_quebrar(self):
        with open(os.path.join(self.dir, 'lixo.json'), 'w') as fh:
            fh.write('{"schema": "outro"}')
        with open(os.path.join(self.dir, 'quebrado.json'), 'w') as fh:
            fh.write('{nao e json')
        self.grava('a/session_summary.json', variante(1, 60, 100))
        self.assertEqual(len(self.analisa()['sessoes']), 1)


class Relogio(Entrada):
    def test_relogio_de_2025_e_corrigido_pelo_gps_ou_marcado_incerto(self):
        ano_errado = T0 - 365 * 86_400_000
        com_gps = variante(1, 60, 100, inicio=ano_errado, suspeito=True, gps_ref=365 * 86_400_000)
        sem_gps = variante(2, 100, 140, inicio=ano_errado + 3_600_000, suspeito=True)
        ok = variante(3, 140, 160)
        for s in (com_gps, sem_gps, ok):
            self.grava(f"{s['identity']['sessionId']}/session_summary.json", s)
        r = self.analisa()
        por_id = {x['sessionId']: x for x in r['sessoes']}
        self.assertEqual(por_id['session_x_1']['startMsCorrigido'], T0)
        self.assertEqual(por_id['session_x_1']['relogioSuspeito'], True)
        self.assertIsNone(por_id['session_x_2']['startMsCorrigido'])
        self.assertEqual(r['relatorio']['relogio_suspeito'], 2)
        self.assertEqual(r['relatorio']['sem_correcao_de_relogio'], 1)
        self.assertTrue(any(t['ordemIncerta'] for t in r['tanques']))


class Tanques(Entrada):
    def test_abastecimento_entre_sessoes_abre_tanque_e_aresta_do_grafo(self):
        for s in (variante(1, 60, 140, km=10), variante(2, 140, 200, km=8), variante(3, 50, 90, km=6)):
            self.grava(f"{s['identity']['sessionId']}/session_summary.json", s)
        r = self.analisa()
        self.assertEqual(len(r['tanques']), 2)
        t0, t1 = r['tanques']
        self.assertEqual(t0['sessoes'], 2)
        self.assertAlmostEqual(t0['km']['MM'], 18.0)
        self.assertEqual(t0['nivelConsumido'], 140)
        self.assertEqual(t1['iniciadoPor'], 'REFUEL_BETWEEN_SESSIONS')
        rel = {(e['source'], e['target'], e['relation']) for e in r['grafo']['links']}
        self.assertIn(('tanque:1', 'tanque:0', 'abasteceu_depois_de'), rel)
        self.assertIn(('sessao:uuid-3', 'tanque:1', 'pertence_a'), rel)

    def test_abastecimento_no_meio_da_sessao(self):
        s = variante(1, 150, 160)
        s['tankSegments'] = [
            dict(s['tankSegments'][0], index=0, startRaw=150, endRaw=160, endedByRefuel=True, endT=60000),
            dict(s['tankSegments'][0], index=1, startRaw=50, endRaw=70, endedByRefuel=False),
        ]
        self.grava('a/session_summary.json', s)
        r = self.analisa()
        self.assertEqual([t['iniciadoPor'] for t in r['tanques']], ['FIRST_SESSION', 'REFUEL_IN_SESSION'])
        self.assertEqual(r['tanques'][1]['nivelInicio'], 50)

    def test_km_desconhecido_fica_vazio_no_csv_e_razoes_so_com_dados(self):
        self.grava('a/session_summary.json', variante(1, 60, 70, com_km=False))
        r = self.analisa()
        self.assertIsNone(r['sessoes'][0]['km_MM'])
        self.assertIsNone(r['tanques'][0]['kmPorUnidadeNivel'])
        linhas = list(csv.DictReader(io.StringIO(ar.para_csv(r['sessoes']))))
        self.assertEqual(linhas[0]['km_MM'], '')
        self.assertEqual(linhas[0]['fonteMovimento'], 'RPM_PROXY')
        self.assertEqual(linhas[0]['movimentoProxy'], 'True')

    def test_km_por_unidade_de_nivel_e_proxy(self):
        self.grava('a/session_summary.json', variante(1, 60, 130, km=14))
        t = self.analisa()['tanques'][0]
        self.assertEqual(t['kmPorUnidadeNivel']['rotulo'], 'PROXY')
        self.assertAlmostEqual(t['kmPorUnidadeNivel']['valor'], 14 / 70, places=4)


class Mapas(Entrada):
    def test_sem_cobertura_comparavel_nao_conclui(self):
        pouco = variante(1, 60, 70, km=1.0, mapa='mapa-A', curva='curva-A')
        outro = variante(2, 70, 80, km=1.2, mapa='mapa-B', curva='curva-A')
        for s in (pouco, outro):
            self.grava(f"{s['identity']['sessionId']}/session_summary.json", s)
        c = self.analisa()['comparacoes'][0]
        self.assertFalse(c['comparavel'])
        self.assertIsNone(c['deltas'])
        self.assertTrue(any('km' in m for m in c['motivos']))

    def test_com_cobertura_comparavel_calcula_delta(self):
        for n, mapa, inj in ((1, 'mapa-A', 1.0), (2, 'mapa-B', 0.8)):
            s = variante(n, 60, 140, km=20, mapa=mapa)
            s['engine']['injectorOpenMinutesGnv'] = inj
            s['tankSegments'][0]['injectorOpenMinutesGnv'] = inj
            self.grava(f"{s['identity']['sessionId']}/session_summary.json", s)
        c = self.analisa()['comparacoes'][0]
        self.assertTrue(c['comparavel'], c['motivos'])
        self.assertAlmostEqual(c['deltas']['injetorMinPorKmPct'], -20.0, places=1)
        self.assertEqual(c['fonteKm'], 'MM')

    def test_identidade_desconhecida_nunca_entra_na_comparacao(self):
        a = variante(1, 60, 140, km=20, mapa=None, curva=None)
        b = variante(2, 60, 140, km=20, mapa=None, curva=None)
        for s in (a, b):
            self.grava(f"{s['identity']['sessionId']}/session_summary.json", s)
        r = self.analisa()
        self.assertEqual(r['comparacoes'], [])
        self.assertEqual(r['mapas'][0]['mapaId'], 'desconhecido')
        self.assertFalse(any(e['relation'] == 'usa_mapa' for e in r['grafo']['links']))


class Saidas(Entrada):
    def test_cli_isolado_gera_csv_json_grafo_e_e_idempotente(self):
        for s in (variante(1, 60, 140, km=10), variante(2, 50, 90, km=6)):
            self.grava(f"{s['identity']['sessionId']}/session_summary.json", s)
        saida = os.path.join(self.dir, 'saida')
        cmd = [sys.executable, '-I', os.path.join(ROOT, 'tools', 'sessoes', 'analisar_resumos.py'), self.dir, '--saida', saida]
        r1 = subprocess.run(cmd, capture_output=True, text=True)
        self.assertEqual(r1.returncode, 0, r1.stderr)
        arquivos = sorted(os.listdir(saida))
        self.assertEqual(arquivos, ['grafo_graphify.json', 'mapas.csv', 'relatorio.json', 'sessoes.csv', 'tanques.csv'])
        antes = {a: open(os.path.join(saida, a), 'rb').read() for a in arquivos}
        r2 = subprocess.run(cmd, capture_output=True, text=True)
        self.assertEqual(r2.returncode, 0, r2.stderr)
        self.assertEqual(antes, {a: open(os.path.join(saida, a), 'rb').read() for a in arquivos})
        grafo = json.load(open(os.path.join(saida, 'grafo_graphify.json')))
        tipos = {n['tipo'] for n in grafo['nodes']}
        self.assertEqual(tipos, {'sessao', 'tanque', 'mapa', 'dia'})
        self.assertEqual({e['relation'] for e in grafo['links']}, {'pertence_a', 'usa_mapa', 'abasteceu_depois_de'})

    def test_pasta_vazia_nao_quebra(self):
        r = self.analisa()
        self.assertEqual(r['sessoes'], [])
        self.assertEqual(r['tanques'], [])


if __name__ == '__main__':
    unittest.main()
