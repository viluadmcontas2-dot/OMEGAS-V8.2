#!/usr/bin/env python3
"""Cobertura da engenharia reversa do ProgBase: quanto falta, por área, com evidência.

Lê só arquivos versionados em docs/progbase (e docs/grafo/producao.json), sem relógio e sem rede:
a mesma entrada dá a mesma saída (idempotente). Escreve:
  docs/progbase/cobertura.json   (dados + grafo área -> evidência/aba/vetor/função/ponte/teste)
  docs/progbase/COBERTURA.md     (resumo curto, <= 60 linhas)

Uso:
  python3 -B tools/progbase/cobertura_re.py            # regenera
  python3 -B tools/progbase/cobertura_re.py --check    # falha (1) se JSON/MD estiverem velhos (verifyreconstruction)

Marcas (mesmas do INDEX de evidências): OBS-FIO, OBS-COD, DERIVADO, INFERIDO, DESCONHECIDO.
"% provado" = (OBS-FIO + OBS-COD + DERIVADO) / total de evidências da área.
O agrupamento por área (abas, vetores, funções, perguntas) é heurística de nome: marca INFERIDO.
"""
import glob
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PB = ROOT / 'docs/progbase'
OUT_JSON = PB / 'cobertura.json'
OUT_MD = PB / 'COBERTURA.md'
MARCAS = ['OBS-FIO', 'OBS-COD', 'DERIVADO', 'INFERIDO', 'DESCONHECIDO']
PROVADAS = {'OBS-FIO', 'OBS-COD', 'DERIVADO'}
GLOBAL = 'Geral (decompilação)'

# nome, peso para o usuário (1-10), prefixos de id, ids avulsos, abas, regex de vetor, classes/regex de função,
# aba do grafo de produção, regex da pergunta aberta, próximo passo padrão
AREAS = [
    dict(nome='Anti-apagão', peso=10, prefixos=['ANTIAPAGAO'], ids=['EV-CAPTURA-013', 'EV-CAPTURA-014'], abas=[],
         vetor=r'STALL', funcao=r'AntiStall|Stallo', prod='Ajuste GNV', lacuna=r'[Aa]nti-apag',
         passo='Provar no carro a gravação dos 5 números e a escala da queda de rotação.'),
    dict(nome='Volta à gasolina', peso=9, prefixos=['VOLTAGASOLINA'], ids=['EV-CAPTURA-001', 'EV-CAPTURA-015'],
         abas=['TabSwitchToPetrol', 'TabBenzina'], vetor=r'RITORNO_BENZINA|RETROPASSAGGIO|SUP_BENZINA|SWITCH',
         funcao=r'^TFormSwitch', prod='Ajuste GNV', lacuna=r'Volta autom',
         passo='Capturar o ACK dos quadros 14 7B|7C no carro e o efeito real.'),
    dict(nome='AutoCal/preferências', peso=8, prefixos=[], ids=['EV-CAPTURA-009'],
         abas=['TabAutoCalNew', 'TabAdatta', 'TabCalibration'], vetor=r'AUTOCAL|ADATTA',
         funcao=r'AutoCal|Autocal|CorrCalibra|TFormCalibra', prod='AutoCal', lacuna=r'AutoCal',
         passo='Registrar no fio cada pergunta/ação do AutoCal e as preferências salvas.'),
    dict(nome='Mapa K/bins', peso=8, prefixos=['BINS'], ids=['EV-CAPTURA-007', 'EV-CAPTURA-008'],
         abas=['TabKCoefficienti', 'TabKVarie'], vetor=r'MAP_K|MAPPA_K|K_MAPPA|MAP_ESTERNO|GIRI_PER_K|TEMPI_PER_K|TEMPI_SECONDI',
         funcao=r'CentriCelle|ModMappa|Aeb3DMap|AebMatrix|GridWorkingPoint|GridReference', prod='Mapa K',
         lacuna=r'Mapa K / bins', passo='Descobrir qual vetor guarda as 12 linhas de ms dos bins.'),
    dict(nome='Curva K', peso=8, prefixos=[], ids=[], abas=['TabKFactor', 'TabKOpenLoop', 'TabCorrettori'],
         vetor=r'K_FILTRO|K_FACTOR|COEFF_K|CURVA|ADATTA_PARAMETRI_K|K_OPEN', funcao=r'KFactor|KOpenLoop|Correttori',
         prod='Curva K', lacuna=r'Curva K', passo='Provar a gravação da Curva K em 1 quadro 0x37 com a central real.'),
    dict(nome='Marcha lenta', peso=6, prefixos=['LENTA'], ids=[], abas=[], vetor=r'MINIMO|IDLE|LENTA',
         funcao=r'^$', prod='Ajuste GNV', lacuna=r'[Mm]archa lenta', passo='Fechar as evidências de marcha lenta em aberto.'),
    dict(nome='Injetores/F9', peso=7, prefixos=[], ids=['EV-CAPTURA-004', 'EV-CAPTURA-005'],
         abas=['TabIniezione', 'TabInjSettings', 'TabInjSettingsSt'],
         vetor=r'INIETT|_INJ|INJ_|TEMPO_(APERTURA|CHIUSURA|MORTO)|IMPEDENZA|CORRENTE|HOLDING|CICCHETTO',
         funcao=r'StrategiaTempiMorti|StrategiaPanda', prod='Ajuste GNV', lacuna=r'Injetores|Tempos mortos',
         passo='Ligar os números da F9 (2,5 ms, extra 0,1 ms) aos vetores no fio.'),
    dict(nome='Nível do gás', peso=7, prefixos=[], ids=['EV-CAPTURA-003'], abas=['TabSensore', 'TabDhlpAgaslev'],
         vetor=r'SENSORE|LIVELLO|LITRI', funcao=r'Sensore|Livello|Agaslev', prod='Ajuste GNV', lacuna=r'N[ií]vel do g',
         passo='Provar os 7 tipos de sensor e as referências Reserva/1/4/2/4/3/4 no fio.'),
    dict(nome='Diagnóstico', peso=6, prefixos=[], ids=['EV-CAPTURA-010'], abas=['TabTest'], vetor=r'DIAGNOSI',
         funcao=r'Diagnosi', prod='Diagnóstico', lacuna=r'Diagn[óo]stico',
         passo='Mapear as 14 verificações (código, caixa, estado, ação) ao vetor de diagnóstico.'),
    dict(nome='Display', peso=5, prefixos=[], ids=['EV-CAPTURA-011'], abas=['TabConsumometro', 'TabEsci', 'TabFrest', 'TabTaratura'],
         vetor=r'DISPLAY|CONSUM', funcao=r'FormMain|GraficoScope|AEBGauge|Visualizza', prod='Agora', lacuna=r'Display',
         passo='Confirmar a origem de cada grandeza da barra de status na resposta 48.'),
    dict(nome='Transporte', peso=6, prefixos=['TRANSPORTE'], ids=[], abas=[],
         vetor=r'SERIAL|CONNESSIONE|VERSIONE_INTERFACCIA', funcao=r'AebCom|AebFtdi|AebTcp|SerialData|PacketData|LandiConnect',
         prod='Ferramentas', lacuna=r'^$', passo='Entender a opção +0x2BD do Connect e a perda de porta.'),
    dict(nome='Cadastro/F1', peso=7, prefixos=[], ids=['EV-CAPTURA-002', 'EV-CAPTURA-012'],
         abas=['TabCambio', 'TabLambda', 'TabEmissioni', 'TabPressione', 'TabVariatore'],
         vetor=r'CODICE|CLIENT|IDENTIFICATIVO|CILINDRATA|MODELLO|REGISTRO|FLAG_CONF|NOTE_CONFIG|SEQUENZA|TIPO_ACCENS|CAMBIO|LAMBDA',
         funcao=r'FormService|Cambio|Lambda|Emission', prod='Ajuste GNV', lacuna=r'Cadastro|Lambda|Quando o g',
         passo='Provar o tipo de troca, rpm mínimo e temperatura de troca no fio.'),
    dict(nome='F6/F7/F8', peso=5, prefixos=[], ids=['EV-CAPTURA-006'],
         abas=['TabTemp', 'TabAdvanced', 'TabKTempRid', 'TabKTempMot', 'TabKPressDiff', 'TabPressAss', 'TabKTempi', 'TabKTempiEx'],
         vetor=r'TEMP_|RIF_TEMP|COEFF_TEMP|PRESS|ANTICIPO|DELAY|BENZINA_LR|FILTRO', funcao=r'^$', prod='Ajuste GNV',
         lacuna=r'F6 Salvar|Mapa de contribui', passo='Decodificar o mapa de contribuição de gasolina 12 x 12 (F8).'),
]
CLASSE_PARA_AREA = None  # preenchido a partir de AREAS[...]['funcao']

NAO_INTERATIVAS = {'TLabel', 'TPanel', 'TTabSheet', 'TBevel', 'TImage', 'TShape', 'TChildLabel', 'TPanelImage', 'TGroupBox'}
BARRA = 20


def jl(p):
    with open(p, encoding='utf-8') as f:
        return json.load(f)


def pct(a, b):
    return round(100.0 * a / b, 1) if b else 0.0


def barra(p):
    n = int(round(p / 100.0 * BARRA))
    return '█' * n + '░' * (BARRA - n)


def area_da_evidencia(e):
    for a in AREAS:
        if e['id'] in a['ids']:
            return a['nome']
    m = re.match(r'EV-([A-Z]+)-', e['id'])
    pre = m.group(1) if m else ''
    for a in AREAS:
        if pre in a['prefixos']:
            return a['nome']
    return GLOBAL


def carregar_evidencias():
    out = []
    for e in jl(PB / 'evidencias/INDEX.json'):
        out.append(dict(id=e['id'], marca=e['marca'], afirmacao=e['afirmacao'], fonte=e.get('fonte', ''),
                        usado_por=sorted(e.get('usado_por') or []), area=area_da_evidencia(e)))
    out.sort(key=lambda e: e['id'])
    return out


def carregar_abas():
    """Por aba: total de controles e quantos têm vetor/fórmula conhecidos. Dois esquemas de JSON."""
    abas = {}
    for f in sorted(glob.glob(str(PB / 'abas/Tab*.json'))):
        t = jl(f)
        nome = Path(f).stem
        if 'meta' in t:
            m = t['meta']
            abas[nome] = dict(controles=m['n_controles'], interativos=m['n_interativos'], cobertos=m['n_ligados_ao_vetor'],
                              esquema='A (controles ligados ao vetor)')
        else:
            ctl = t.get('controles', [])
            inter = [c for c in ctl if c.get('classe') not in NAO_INTERATIVAS]
            cob = [c for c in inter if c.get('formula', 'DESCONHECIDO') != 'DESCONHECIDO'
                   or c.get('efeito_runtime', 'DESCONHECIDO') != 'DESCONHECIDO']
            abas[nome] = dict(controles=len(ctl), interativos=len(inter), cobertos=len(cob),
                              esquema='B (inventário DFM; fórmula/efeito ainda DESCONHECIDO)')
    return abas


def carregar_vetores():
    v = jl(PB / 'protocolo/vetores.json')
    return [dict(nome=i['nome'], modulo=i['modulo'], presenca=i.get('presenca_na_ecu_capturada'),
                 unidade_marca=i.get('unidade_marca', 'DESCONHECIDO')) for i in v['itens']]


def presenca_ok(p):
    return isinstance(p, str) and p.startswith('confirmada') or p is True


def carregar_funcoes():
    nomes = []
    for linha in (PB / 'decomp/funcoes-app.md').read_text(encoding='utf-8').splitlines():
        if linha.startswith('| 0x'):
            nomes.append(linha.split('|')[2].strip())
    cls = jl(PB / 'decomp/classificacao-funcoes.json')
    return nomes, dict(programa=cls['alvo_por_categoria'].get('programa', 0),
                       desconhecida=cls['alvo_por_categoria'].get('desconhecida', 0),
                       biblioteca=cls['alvo_por_categoria'].get('biblioteca', 0),
                       total_alvo=cls['alvo_n'])


def area_da_funcao(nome):
    for a in AREAS:
        if a['funcao'] != r'^$' and re.search(a['funcao'], nome):
            return a['nome']
    return None


def catalogo_capturas():
    p = PB / 'capturas/originais/CATALOGO.md'
    txt = p.read_text(encoding='utf-8')
    return len(re.findall(r'^### ', txt, flags=re.M))


def data_dos_dados(evs):
    datas = []
    for e in evs:
        datas += re.findall(r'20\d\d-\d\d-\d\d', e['fonte'])
    return max(datas) if datas else None


def calcular():
    evs = carregar_evidencias()
    abas = carregar_abas()
    vets = carregar_vetores()
    nomes_fn, fn_global = carregar_funcoes()
    prod = jl(ROOT / 'docs/grafo/producao.json')
    prod_abas = {a['aba']: a for a in prod['abas']}
    lacunas = [l for l in prod['lacunas'] if l.get('falta')]

    areas = []
    desconhecidos = []
    for a in AREAS + [dict(nome=GLOBAL, peso=4, abas=[], vetor=r'^$', funcao=r'^$', prod=None, lacuna=r'^$',
                           passo='Nomear as funções sem dono e os bytes de .text fora de função.')]:
        mine = [e for e in evs if e['area'] == a['nome']]
        cont = {m: sum(1 for e in mine if e['marca'] == m) for m in MARCAS}
        prov = sum(cont[m] for m in PROVADAS)
        ab = {n: abas[n] for n in a['abas'] if n in abas}
        a_int = sum(x['interativos'] for x in ab.values())
        a_cob = sum(x['cobertos'] for x in ab.values())
        vs = [v for v in vets if a['vetor'] != r'^$' and re.search(a['vetor'], v['nome'])]
        v_ok = sum(1 for v in vs if presenca_ok(v['presenca']) and v['unidade_marca'] != 'DESCONHECIDO')
        v_fio = sum(1 for v in vs if presenca_ok(v['presenca']))
        fns = [n for n in nomes_fn if area_da_funcao(n) == a['nome']]
        pa = prod_abas.get(a.get('prod')) if a.get('prod') else None
        ev_ids = [e['id'] for e in mine]
        no_grafo = [i for i in ev_ids if pa and i in pa['evidencias']]
        usa = [e['id'] for e in mine if e['usado_por']]
        lac = [l for l in lacunas if a['lacuna'] != r'^$' and re.search(a['lacuna'], l['funcao'])]
        desc = [e for e in mine if e['marca'] == 'DESCONHECIDO']
        for e in desc:
            desconhecidos.append(dict(tipo='evidencia', id=e['id'], area=a['nome'], texto=e['afirmacao'],
                                      impacto=a['peso'] * 8 + (10 if e['usado_por'] else 0)))
        for l in lac:
            desconhecidos.append(dict(tipo='pergunta', id='PROD-%02d' % l['n'], area=a['nome'], texto=l['funcao'],
                                      impacto=100 - 3 * l['n']))
        passo = a['passo']
        comp = [x for x in ((pct(prov, len(mine)), len(mine)), (pct(a_cob, a_int), a_int), (pct(v_ok, len(vs)), len(vs))) if x[1]]
        areas.append(dict(
            area=a['nome'], peso=a['peso'], evidencias=len(mine), por_marca=cont,
            pct_provado=pct(prov, len(mine)),
            pct_cobertura=round(sum(c[0] for c in comp) / len(comp), 1) if comp else 0.0,
            abas=dict(nomes=sorted(ab), controles_interativos=a_int, com_vetor_ou_formula=a_cob,
                      pct=pct(a_cob, a_int)),
            vetores=dict(total=len(vs), no_fio=v_fio, no_fio_com_unidade=v_ok, pct=pct(v_ok, len(vs))),
            funcoes=dict(com_dono=len(fns)),
            app=dict(evidencias_usadas_no_codigo=len(usa), pct=pct(len(usa), len(mine)),
                     aba_producao=a.get('prod'), pontes=len(pa['pontes']) if pa else 0,
                     testes=len(pa['testes']) if pa else 0, evidencias_no_grafo_de_producao=len(no_grafo)),
            perguntas_abertas=[l['n'] for l in lac],
            desconhecidos=[e['id'] for e in desc],
            proximo_passo=passo,
            grafo=dict(evidencias=ev_ids, abas=sorted(ab), ponte_producao=a.get('prod')),
        ))
    desconhecidos.sort(key=lambda d: (-d['impacto'], d['id']))
    total = len(evs)
    cont_t = {m: sum(1 for e in evs if e['marca'] == m) for m in MARCAS}
    sem_dono = fn_global['desconhecida']
    return dict(
        titulo='Cobertura da Engenharia Reversa',
        gerador='tools/progbase/cobertura_re.py',
        dados_ate=data_dos_dados(evs),
        criterio='% provado = (OBS-FIO + OBS-COD + DERIVADO) / evidências da área; % cobertura = média de provado, abas com vetor e vetores no fio com unidade (só o que tem dado); agrupamento por área é heurística de nome (INFERIDO).',
        total=dict(evidencias=total, por_marca=cont_t,
                   pct_provado=pct(sum(cont_t[m] for m in PROVADAS), total),
                   abas_inventariadas=len(abas), abas_interativos=sum(x['interativos'] for x in abas.values()),
                   abas_com_vetor_ou_formula=sum(x['cobertos'] for x in abas.values()),
                   vetores=len(vets), vetores_no_fio=sum(1 for v in vets if presenca_ok(v['presenca'])),
                   funcoes_do_programa_nomeadas=len(nomes_fn) - nomes_fn.count('(sem nome)'),
                   funcoes_do_programa_sem_nome=nomes_fn.count('(sem nome)'),
                   decomp=dict(fn_global, pct_sem_dono=pct(sem_dono, fn_global['total_alvo'])),
                   capturas_catalogadas=catalogo_capturas(),
                   perguntas_abertas_producao=len(lacunas)),
        areas=areas,
        desconhecidos_top=desconhecidos[:10],
        desconhecidos_total=len(desconhecidos),
    )


def renderizar_json(d):
    return json.dumps(d, ensure_ascii=False, indent=1, sort_keys=True) + '\n'


def renderizar_md(d):
    L = ['# Cobertura da engenharia reversa (ProgBase)', '',
         'Gerado por `tools/progbase/cobertura_re.py` (dados até %s). Detalhe: `cobertura.json`. '
         'Cobertura = média de: %% provado (OBS-FIO+OBS-COD+DERIVADO sobre as evidências), abas com vetor e vetores no fio com unidade.' % d['dados_ate'], '',
         'Total: %d evidências, **%s%% provado**; %d abas, %d vetores (%d no fio); %d funções do programa sem dono.'
         % (d['total']['evidencias'], d['total']['pct_provado'], d['total']['abas_inventariadas'],
            d['total']['vetores'], d['total']['vetores_no_fio'], d['total']['decomp']['desconhecida']), '',
         '| Área | Cobertura | Provado | Ev. | Abas c/ vetor | Vetores | App usa |', '|---|---|---|---|---|---|---|']
    for a in d['areas']:
        L.append('| %s | `%s` %s%% | %s%% | %d | %d/%d | %d/%d | %d/%d |' % (
            a['area'], barra(a['pct_cobertura']), a['pct_cobertura'], a['pct_provado'], a['evidencias'],
            a['abas']['com_vetor_ou_formula'], a['abas']['controles_interativos'],
            a['vetores']['no_fio_com_unidade'], a['vetores']['total'],
            a['app']['evidencias_usadas_no_codigo'], a['evidencias']))
    L += ['', '## 10 maiores DESCONHECIDOS (impacto para o usuário)']
    for i, x in enumerate(d['desconhecidos_top'], 1):
        L.append('%d. [%s] %s: %s' % (i, x['area'], x['id'], x['texto'][:105].rstrip()))
    L += ['', '## Próximo passo por área']
    for a in d['areas']:
        L.append('- %s: %s' % (a['area'], a['proximo_passo']))
    return '\n'.join(L) + '\n'


def main(argv):
    d = calcular()
    j, m = renderizar_json(d), renderizar_md(d)
    if '--check' in argv:
        ruim = [p.name for p, t in ((OUT_JSON, j), (OUT_MD, m)) if not p.exists() or p.read_text(encoding='utf-8') != t]
        if ruim:
            print('cobertura desatualizada: %s. Rode: python3 -B tools/progbase/cobertura_re.py' % ', '.join(ruim))
            return 1
        print('cobertura em dia (%d evidências, %s%% provado)' % (d['total']['evidencias'], d['total']['pct_provado']))
        return 0
    OUT_JSON.write_text(j, encoding='utf-8')
    OUT_MD.write_text(m, encoding='utf-8')
    print('escrito %s e %s' % (OUT_JSON.relative_to(ROOT), OUT_MD.relative_to(ROOT)))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
