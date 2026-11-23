"""Ômegas: investigação incremental somente leitura, sem inferir combustível sem rótulo."""
from collections import deque
from statistics import median
import math

DEFAULT_RULES={
    "stability_ms":6000, "max_obd_age_ms":2000, "max_mp48_age_ms":1400,
    "rpm_tolerance":250, "map_tolerance":12, "ect_tolerance":6,
    "tps_tolerance":8, "max_samples_per_mode":2400,
}
VALID_MODES={0x80:"liquido",0xA0:"liquido",0x90:"GNV",0xB0:"GNV"}
FEATURES=("rpm","mapKpa","ect","tps","speed","ltft","stft")
EXTRA=("ethanolPercent","ecuVoltage","fuelTypeCode")
CONDITIONS=("rpm","mapKpa","ect","tps")

class FuelEngine:
    def __init__(self,rules=None):
        self.rules=dict(DEFAULT_RULES)
        if rules:self.set_rules(rules)
        self.obd={}
        self.fuel_raw=None;self.fuel_time=-10**30;self.fuel_since=-10**30
        self.mode=None
        self.samples={"liquido":deque(maxlen=self.rules["max_samples_per_mode"]),
                      "GNV":deque(maxlen=self.rules["max_samples_per_mode"])}
        self.pairs=deque(maxlen=2000)
        self.paired_ids=set()
        self.next_id=0
        self.events=deque(maxlen=90)
        self.last_stft=None
        self.latest_at=0
        self.source_counts={"obd":0,"mp48":0}
        self.last_status_at=0
    def set_rules(self,raw):
        if not isinstance(raw,dict):raise ValueError("rules object required")
        for k,v in raw.items():
            if k not in DEFAULT_RULES or not isinstance(v,(int,float)) or not math.isfinite(v):
                raise ValueError("unsupported rule "+str(k))
            if v<1 or v>100000:raise ValueError("rule outside safe range: "+k)
            self.rules[k]=int(v)
    def ingest(self,item):
        if not isinstance(item,dict) or item.get("source") not in ("mp48","obd"):return
        source=item["source"];stamp=item.get("monoNs")
        if not isinstance(stamp,int) or stamp<0:return
        vals=item.get("values")
        if not isinstance(vals,dict):return
        self.latest_at=max(self.latest_at,stamp)
        self.source_counts[source]+=1
        if source=="mp48":
            flag=vals.get("fuelByte")
            if not isinstance(flag,int):return
            mode=VALID_MODES.get(flag)
            if flag != self.fuel_raw:
                if self.fuel_raw is not None:
                    self.events.append({"kind":"ALTERACAO_ESTADO_MP48",
                      "atMonoNs":stamp,"fromHex":hex(self.fuel_raw),"toHex":hex(flag),
                      "note":"Mudança de byte comprovada; sem prova de combustível por OBD."})
                self.fuel_since=stamp
                self.fuel_raw=flag
            self.fuel_time=stamp
            self.mode=mode
            return
        for k,v in vals.items():
            if k in FEATURES+EXTRA and isinstance(v,(float,int)) and math.isfinite(v):
                self.obd[k]=(float(v),stamp)
        if "stft" not in vals:return
        # A single Mode 01 PID arrives per response; never pretend all fields are simultaneous.
        frame={}
        for f in FEATURES:
            current=self.obd.get(f)
            if current is None or stamp-current[1] < 0 or stamp-current[1] > self.rules["max_obd_age_ms"]*1_000_000:
                return
            frame[f]=current[0]
        frame["atNs"]=stamp
        self.next_id+=1;frame["id"]=self.next_id
        if self.last_stft is not None:
            before=self.last_stft
            if 0<stamp-before["atNs"]<4_000_000_000 and abs(frame["stft"]-before["stft"])>=14:
                steady=abs(frame["rpm"]-before["rpm"])<=250 and abs(frame["mapKpa"]-before["mapKpa"])<=12
                self.events.append({"kind":"STFT_ABRUPTO","atMonoNs":stamp,
                    "fromPct":round(before["stft"],2),"toPct":round(frame["stft"],2),
                    "contexto":"similar" if steady else "carga_rotacao_variavel",
                    "note":"Mudança observada no OBD; não identifica combustível isoladamente."})
        self.last_stft=frame
        if self.mode is None or stamp-self.fuel_time < 0 or stamp-self.fuel_time > self.rules["max_mp48_age_ms"]*1_000_000:
            return
        if stamp-self.fuel_since < self.rules["stability_ms"]*1_000_000:return
        label=self.mode
        frame["label"]=label
        self.samples[label].append(frame)
        opposite="liquido" if label=="GNV" else "GNV"
        candidates=[]
        for other in reversed(self.samples[opposite]):
            if other["id"] in self.paired_ids:continue
            delta=(abs(frame["rpm"]-other["rpm"])/self.rules["rpm_tolerance"]
                + abs(frame["mapKpa"]-other["mapKpa"])/self.rules["map_tolerance"]
                + abs(frame["ect"]-other["ect"])/self.rules["ect_tolerance"]
                + abs(frame["tps"]-other["tps"])/self.rules["tps_tolerance"])
            ok=all(abs(frame[k]-other[k]) <= self.rules[rule] for k,rule in (
                ("rpm","rpm_tolerance"),("mapKpa","map_tolerance"),
                ("ect","ect_tolerance"),("tps","tps_tolerance")))
            if ok:candidates.append((delta,other))
        if candidates:
            other=min(candidates,key=lambda p:p[0])[1]
            self.paired_ids.update((frame["id"],other["id"]))
            liquid=frame if label=="liquido" else other
            gas=frame if label=="GNV" else other
            self.pairs.append({"liquid":liquid,"gnv":gas})
    def _classification(self):
        if len(self.pairs)<24:
            return {"decision":"SEM_MODELO_VALIDADO","reason":"Menos de 24 pares combustível líquido/GNV sob carga e temperatura comparáveis"}
        pairs=list(self.pairs)
        middle=max(1,int(len(pairs)*0.7))
        train=pairs[:middle];hold=pairs[middle:]
        if len(hold)<7:return {"decision":"SEM_MODELO_VALIDADO","reason":"Validação independente insuficiente"}
        avgL=median(p["liquid"]["stft"] for p in train)
        avgG=median(p["gnv"]["stft"] for p in train)
        if abs(avgL-avgG)<5:
            return {"decision":"SEM_MODELO_VALIDADO","reason":"STFT não separa combustíveis sob comparação controlada"}
        correct=0;count=0
        for p in hold:
            for label in ("liquid","gnv"):
                s=p[label]["stft"]
                predicted="liquid" if abs(s-avgL)<abs(s-avgG) else "gnv"
                correct+=predicted==label;count+=1
        accuracy=correct/count if count else 0.0
        if accuracy<0.8:
            return {"decision":"SEM_MODELO_VALIDADO","reason":"Falha na validação com observações retidas",
                "balancedAccuracyObserved":round(accuracy,3)}
        return {"decision":"CANDIDATO_NAO_VALIDADO",
                "reason":"Separação observada em dados retidos; exige sessões independentes e validação em OBD sem MP48",
                "balancedAccuracyObserved":round(accuracy,3),
                "liquidStftBaselinePct":round(avgL,2),
                "gnvStftBaselinePct":round(avgG,2)}
    def report(self):
        pairs=list(self.pairs)
        deltaStft=[p["gnv"]["stft"]-p["liquid"]["stft"] for p in pairs]
        deltaLtft=[p["gnv"]["ltft"]-p["liquid"]["ltft"] for p in pairs]
        return {"schema":"omegas.fuel-intelligence.v1",
                "nowMonoNs":self.latest_at,
                "sourceCounts":dict(self.source_counts),
                "mp48":{"rawFuelHex":hex(self.fuel_raw) if self.fuel_raw is not None else None,
                         "confirmedMode":self.mode,
                         "stableMs":max(0,(self.latest_at-self.fuel_since)//1_000_000) if self.fuel_raw is not None else None},
                "labelCounts":{k:len(v) for k,v in self.samples.items()},
                "controlled":{"matchedPairs":len(pairs),
                    "medianDeltaStftGnvMinusLiquid":round(median(deltaStft),3) if pairs else None,
                    "medianDeltaLtftGnvMinusLiquid":round(median(deltaLtft),3) if pairs else None},
                "obdOnly":self._classification(),
                "obdDirect":{k:round(self.obd[k][0],3) for k in EXTRA
                    if k in self.obd and 0<=self.latest_at-self.obd[k][1]<4_000_000_000},
                "events":list(self.events)[-12:],
                "rules":dict(self.rules),
                "notice":"O estado líquido da MP48 não distingue gasolina de etanol; o OBD pode informar teor via PID 0152 quando disponível. O modo GNV exige validação independente."}
