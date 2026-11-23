"""Evidence-first automotive signal investigator, no ECU commands or network.

Measurements use the same S20 monotonic clock, but different OBD PIDs are
sequentially polled. Apparent throttle-to-RPM delays have >= one TPS period
of timestamp uncertainty; they are NOT proven mechanical/ECU latency.
"""
from collections import defaultdict,deque,Counter
from statistics import median
from bisect import bisect_left
from math import isfinite

FIELDS=("rpm","mapKpa","tps","stft","ltft","ect","speed","ecuVoltage","ethanolPercent")
MPFIELDS=("rpm","mapBar","fuelByte","petrolMs","gasMs","gasPressureBar")
MAX_AGE=30*60*1_000_000_000

def med(x):
    return median(x) if x else None

def rounded(x,n=2):
    return round(x,n) if x is not None else None

def periods(rows):
    valid=[(rows[i][0]-rows[i-1][0])/1e6 for i in range(1,len(rows))
           if 0<rows[i][0]-rows[i-1][0]<=5e9]
    return med(valid)

def nearest(a, target):
    """Return nearest sample to target time for strictly increasing sequences."""
    if not a:return None
    stamps=[z[0] for z in a]
    pos=bisect_left(stamps,target)
    options=(a[j] for j in (pos-1,pos) if 0<=j<len(a))
    return min(options,key=lambda r:abs(r[0]-target),default=None)

class DiscoveryEngine:
    def __init__(self):
        self.samples=defaultdict(lambda:deque(maxlen=26000))
        self.states=Counter()
        self.transitions=deque(maxlen=80)
        self.last_fuel=None
        self.latest=0
        self.total=Counter()
        self.last_wall=None
        self.epoch=0

    def ingest(self,row):
        if not isinstance(row,dict) or row.get('source') not in ('obd','mp48'):return
        t=row.get('monoNs')
        if not isinstance(t,int) or t<0:return
        if self.latest and t < self.latest-60_000_000_000:
            self.samples.clear();self.states.clear();self.transitions.clear()
            self.last_fuel=None;self.epoch+=1
        self.latest=max(self.latest,t)
        v=row.get('values') or {}
        if not isinstance(v,dict):return
        source=row['source'];self.total[source]+=1
        self.last_wall=row.get('t',self.last_wall)
        if source=='mp48':
            f=v.get('fuelByte')
            if isinstance(f,int):
                key=f'0x{f:02x}'
                self.states[key]+=1
                if f!=self.last_fuel and self.last_fuel is not None:
                    self.transitions.append({'atMonoNs':t,'atEpochMs':row.get('t'),
                        'from':f'0x{self.last_fuel:02x}','to':key})
                self.last_fuel=f
        wanted=FIELDS if source=='obd' else MPFIELDS
        for key in wanted:
            value=v.get(key)
            if isinstance(value,(int,float)) and isfinite(value):
                self.samples[source,key].append((t,float(value)))
    def _pairs(self,obd_key,mp_key,scale=1):
        raw=list(self.samples['mp48',mp_key])
        obs=list(self.samples['obd',obd_key])
        if len(raw)<2 or len(obs)<2:return None
        ts=[x[0] for x in raw]
        diffs=[];gaps=[]
        for stamp,value in obs:
            p=bisect_left(ts,stamp)
            idxs=(k for k in (p-1,p) if 0<=k<len(raw))
            idx=min(idxs,key=lambda k:abs(raw[k][0]-stamp),default=None)
            if idx is None:continue
            delta=abs(raw[idx][0]-stamp)/1e6
            if delta>200:continue
            gaps.append(delta)
            diffs.append(value-raw[idx][1]*scale)
        if not diffs:return None
        return {'pairs':len(diffs),'medianAbsDiff':rounded(med([abs(d) for d in diffs])),
                'medianSignedDiff':rounded(med(diffs)),
                'medianTimestampGapMs':rounded(med(gaps)),
                'units':'RPM' if obd_key=='rpm' else 'kPa',
                'interpretation':'diferença entre sensores em amostras próximas, não atraso temporal'}
    def _throttle(self):
        tps=list(self.samples['obd','tps'])
        rpm=list(self.samples['mp48','rpm'])
        manifold=list(self.samples['mp48','mapBar'])
        fuel=list(self.samples['mp48','fuelByte'])
        tick=periods(tps)
        empty={'throttleEvents':0,'qualifiedResponses':0,'qualifiedMapResponses':0,
            'byFuel':{},'medianObservedRpmResponseMs':None,
            'medianObservedMapResponseMs':None,
            'resolutionUncertaintyMs':rounded(tick)}
        if tick is None or tick>2500 or len(tps)<4 or len(rpm)<8:
            return {**empty,'quality':'dados insuficientes: TPS ou RPM indisponíveis ou esparsos'}
        mPeriod=periods(rpm)
        if mPeriod is None or mPeriod>320:
            return {**empty,'quality':'dados insuficientes: amostragem MP48 lenta para estimar resposta'}
        rpm_times=[t for t,_ in rpm]
        map_times=[t for t,_ in manifold]
        fuel_times=[t for t,_ in fuel]
        events=[]
        responses=[]
        map_responses=[]
        by_fuel=defaultdict(lambda:{'throttleEvents':0,'qualifiedRpmResponses':0,
                                    'qualifiedMapResponses':0})
        last_event=-10**30
        for i in range(1,len(tps)):
            t,value=tps[i]
            prev_t,prev=tps[i-1]
            if (t-prev_t)/1e6>2500 or value-prev<12 or t-last_event<4e9:
                continue
            last_event=t
            events.append({'atMonoNs':t,'deltaTpsPct':rounded(value-prev)})
            f_start=bisect_left(fuel_times,t-500_000_000)
            f_end=bisect_left(fuel_times,t+1_500_000_000)
            flags=fuel[f_start:f_end]
            steady=(len(flags)>=5 and flags[0][0]<=t-300_000_000 and
                    flags[-1][0]>=t+1_200_000_000 and
                    all(v==flags[0][1] for _,v in flags))
            label={0x80:'liquido',0x90:'GNV'}.get(int(flags[0][1])) if steady else None
            if label:by_fuel[label]['throttleEvents']+=1

            # Change in MAP is separately measured. This is association, not
            # proof of causal TPS-to-MAP physical delay.
            m_begin=bisect_left(map_times,t-1_400_000_000)
            m_now=bisect_left(map_times,t)
            m_before=manifold[m_begin:m_now]
            if len(m_before)>=3:
                m_base=med([x[1] for x in m_before])
                rise=max(.075,abs(m_base)*.15)
                m_end=bisect_left(map_times,t+3_000_000_000)
                for mt,m_value in manifold[m_now:m_end]:
                    if m_value-m_base>=rise:
                        map_responses.append({'atMonoNs':t,
                            'firstMapIncreaseMs':rounded((mt-t)/1e6)})
                        if label:by_fuel[label]['qualifiedMapResponses']+=1
                        break

            r_begin=bisect_left(rpm_times,t-1_400_000_000)
            r_now=bisect_left(rpm_times,t)
            before=rpm[r_begin:r_now]
            if len(before)<3:continue
            rpm_base=med([v for _,v in before])
            rise=max(100,abs(rpm_base)*.07)
            r_end=bisect_left(rpm_times,t+3_000_000_000)
            for rt,r_value in rpm[r_now:r_end]:
                if r_value-rpm_base>=rise:
                    responses.append({'atMonoNs':t,'firstRpmIncreaseMs':rounded((rt-t)/1e6),
                        'rpmBaseline':rounded(rpm_base),'thresholdRpm':rounded(rise)})
                    if label:by_fuel[label]['qualifiedRpmResponses']+=1
                    break
        return {
            'throttleEvents':len(events),'qualifiedResponses':len(responses),
            'qualifiedMapResponses':len(map_responses),'byFuel':dict(by_fuel),
            'medianObservedMapResponseMs':rounded(med([x['firstMapIncreaseMs'] for x in map_responses])),
            'medianObservedRpmResponseMs':rounded(med([x['firstRpmIncreaseMs'] for x in responses])),
            'resolutionUncertaintyMs':rounded(tick),'mp48SamplePeriodMs':rounded(mPeriod),
            'quality':'INDICIO_COM_RESOLUCAO_LIMITADA' if responses or map_responses else
                'dados insuficientes para ligar variação TPS a RPM/MAP',
            'events':events[-12:],'responses':responses[-12:],
            'mapResponses':map_responses[-12:],
            'note':'Janela de associação temporal; NÃO é latência precisa da ECU. OBD TPS amostrado mais lentamente.'}

    def report(self):
        cadence={}
        for source,key in [('obd','tps'),('obd','rpm'),('obd','mapKpa'),
                           ('obd','stft'),('mp48','rpm'),('mp48','mapBar')]:
            items=list(self.samples[source,key])
            if items:
                cadence[source+'_'+key]={'count':len(items),
                    'medianPeriodMs':rounded(periods(items)),
                    'min':rounded(min(v for _,v in items)),
                    'max':rounded(max(v for _,v in items))}
        rpm=self._pairs('rpm','rpm')
        mp=self._pairs('mapKpa','mapBar',100)
        throttle=self._throttle()
        findings=[]
        def find(fid,title,state,evidence,next_step,category='telemetria'):
            findings.append({'id':fid,'title':title,'state':state,
                'evidence':evidence,'nextStep':next_step,'category':category,
                'evidenceType':'MEDICAO_DIRETA' if state=='OBSERVADO' else 'HIPOTESE_OU_LACUNA'})
        tps=cadence.get('obd_tps');mprpm=cadence.get('mp48_rpm')
        if tps and mprpm:
            find('sampling-cadence','Frequências diferentes de leitura','OBSERVADO',
                f'TPS OBD {tps["medianPeriodMs"]} ms/amostra; RPM MP48 {mprpm["medianPeriodMs"]} ms/amostra. Amostras TPS={tps["count"]}, MP48 RPM={mprpm["count"]}.',
                'Tratar cada PID com seu timestamp. Não anunciar atraso em milissegundos abaixo da resolução do TPS.')
        else:
            find('sampling-cadence','Aguardando amostras dos dois leitores','LACUNA',
                'Ainda não há taxas temporais comparáveis de TPS OBD e RPM MP48.',
                'Manter observação passiva e conferir o timestamp monotônico de ambos.')
        if rpm and rpm['pairs']>=10:
            find('rpm-cross-check','OBD e MP48: concordância da rotação','OBSERVADO',
                f'{rpm["pairs"]} pares em até 200 ms; diferença absoluta mediana {rpm["medianAbsDiff"]} RPM; deslocamento de timestamps mediano {rpm["medianTimestampGapMs"]} ms.',
                'Separar marcha lenta de aceleração antes de interpretar diferenças como erro de escala.')
        else:
            find('rpm-cross-check','Comparação RPM entre ECUs','LACUNA',
                'Ainda faltam dez pares RPM OBD/MP48 próximos no relógio.',
                'Aguardar ambos os leitores transmitirem simultaneamente.')
        if throttle['qualifiedResponses']:
            find('tps-rpm-response','Abertura de borboleta e resposta da rotação','EM_INVESTIGACAO',
                f'{throttle["throttleEvents"]} alterações TPS; {throttle["qualifiedResponses"]} seguidas por subida de RPM; {throttle["qualifiedMapResponses"]} seguidas por aumento de MAP. Por combustível MP48 estável: {throttle["byFuel"]}. Incerteza do TPS >= {throttle["resolutionUncertaintyMs"]} ms. Tempos aparentes não são latência da ECU.',
                'Comparar por combustível, rotação, MAP e velocidade, com amostras independentes. Não inferir causalidade ou atraso fino.')
        else:
            find('tps-rpm-response','Abertura de borboleta e resposta da rotação','LACUNA',
                f'{throttle["throttleEvents"]} mudanças TPS observadas; nenhuma sequência qualificada para estimar a resposta.',
                'Aguardar mudanças naturais com TPS OBD e RPM MP48 com timestamps íntegros.')
        if self.states.get('0x94',0)>0:
            find('mp48-state-94','Estado MP48 0x94 versus ProgBase','EM_INVESTIGACAO',
                f'{self.states["0x94"]} quadros 0x94 e {len(self.transitions)} mudanças de estado registradas. O byte é conhecido; sua semântica exige confrontar protocolo e pulsos.',
                'Contrastar pulsos GNV, RPM, MAP e código original. Não chamar qualquer 0x94 de apagão.')
        if mp and mp['pairs']>=10:
            find('map-cross-check','MAP OBD versus MP48','OBSERVADO',
                f'{mp["pairs"]} pares MAP em até 200 ms; diferença absoluta mediana {mp["medianAbsDiff"]} kPa.',
                'Conferir equivalência física e sensor em diferentes cargas antes de corrigir escala.')
        return {
            'schema':'omegas.discovery.v1','analyzedAtEpochMs':self.last_wall,
            'ingested':dict(self.total),'cadence':cadence,
            'synchronization':{'rpm':rpm or {'pairs':0},'map':mp or {'pairs':0}},
            'throttle':throttle,'states':{k:{'count':v} for k,v in sorted(self.states.items())},
            'transitions':list(self.transitions)[-12:],
            'findings':findings,'sessionEpoch':self.epoch,
            'safety':'SOMENTE_LEITURA; associações temporais não provam causalidade.'}
