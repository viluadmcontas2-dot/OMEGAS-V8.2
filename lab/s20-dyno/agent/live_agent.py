"""Ômegas investigator: local continuous stream + optional Gemini on aggregated evidence.

Never writes to vehicle interfaces. Never sends raw frames to Gemini.
Hot-reloads JSON rules and Python modules without APK recompilation.
"""
from pathlib import Path
from threading import Thread, Lock
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from concurrent.futures import ThreadPoolExecutor
import json, os, time, glob, urllib.request, urllib.error, re, queue
from fuel_engine import FuelEngine
from discovery_engine import DiscoveryEngine
from research_journal import ResearchJournal, build_research_brief
from gemini_gateway import (preferred_models, normalized_gateway_status,
                            status_label, generation_settings, complete_gemini_response)

ROOT=Path.home()/"omegas-intelligence"
DATA=Path("/sdcard/Download/Omegas/Inteligencia")
CONF=Path.home()/".config/omegas"
RULES=ROOT/"rules.json"
KEY=CONF/"gemini.key"
LATEST=DATA/"latest-analysis.json"
MODEL="gemini-3.8-flash"
AVAILABLE_MODELS=[]
LOCK=Lock()
ENGINE=FuelEngine()
DISCOVERY=DiscoveryEngine()
JOURNAL=ResearchJournal(ROOT/"research.sqlite3")
AI_RESULTS=queue.Queue()
RESEARCH_CACHE={"discovery":{},"findings":[],"reviews":[],"stats":{"updates":0,"geminiReviews":0}}
LAST_RESEARCH_CYCLE=0.0
LAST_MODEL_EVIDENCE_REVISION=-1

STATE={"status":"AGUARDANDO_TELEMETRIA","gemini":{"status":"SEM_CHAVE"},"uptimeSince":int(time.time()),"stream":None}
SEEN={}
LIVE_SOURCE_COUNTS={"obd":0,"mp48":0}
LAST_LIVE_EVENT_AT=0.0
LAST_GEMINI=0.
DAILY_COUNT=0
DAY=time.localtime().tm_yday
POOL=ThreadPoolExecutor(max_workers=1)
STOP=False

def atomic_json(path,content):
    path.parent.mkdir(parents=True,exist_ok=True)
    temp=path.with_suffix(path.suffix+".part")
    temp.write_text(json.dumps(content,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    os.replace(temp,path)

def summary():
    with LOCK:
        r=ENGINE.report()
        r["runtimeStatus"]=STATE["status"]
        gemini=dict(STATE["gemini"])
        gemini["configured"]=bool(KEY.exists() and KEY.stat().st_size>15)
        gemini["message"]=status_label(gemini)
        r["gemini"]=gemini
        r["stream"]=STATE["stream"]
        r["generatedAtEpochMs"]=int(time.time()*1000)
        r["liveSourceCounts"]=dict(LIVE_SOURCE_COUNTS)
        r["lastLiveReadAgeSec"]=round(time.monotonic()-LAST_LIVE_EVENT_AT,1) if LAST_LIVE_EVENT_AT else None
        r["discovery"]=RESEARCH_CACHE["discovery"]
        r["learning"]={
            "state":"OBSERVANDO" if STATE["status"]=="OBSERVANDO" else "PAUSADO_SEM_DADOS_RECENTES",
            "findings":RESEARCH_CACHE["findings"][:12],
            "reviews":RESEARCH_CACHE["reviews"][:6],
            "stats":RESEARCH_CACHE["stats"],
            "lastCycleEpochMs":RESEARCH_CACHE.get("lastCycleEpochMs"),
            "currentFocus":RESEARCH_CACHE.get("currentFocus","TPS, sincronização e estados MP48")}
    return r

def reload_rules_if_changed():
    if not RULES.exists():return
    try:
        data=json.loads(RULES.read_text())
        with LOCK: ENGINE.set_rules(data)
    except Exception as e:
        STATE["ruleWarning"]=type(e).__name__  # No externally supplied code is ever evaluated.

def replay_previous_capture():
    p=Path("/sdcard/Download/Omegas/dyno-evidencias/dinamometro-omegas-1795307992224.zip")
    if not p.exists():return
    import zipfile
    with zipfile.ZipFile(p) as z:
        if z.testzip() is not None:return
        obs=json.loads(z.read("dyno-vault.json")).get("observations",[])
    for x in sorted(obs,key=lambda r:r.get("mono_ns",0)):
        if x.get("source") not in ("obd","mp48"):continue
        try:
            item={"v":1,"source":x["source"],"monoNs":int(x["mono_ns"]),
                  "t":x.get("wall_ms"),"values":x.get("data",{})}
            ENGINE.ingest(item)
            DISCOVERY.ingest(item)
        except Exception:continue
    STATE["historicalEvidence"]="3904 registros de 21/11/2026 reprocessados; rotulos 0x88 nao sao gasolina"

def verify_gemini_credentials():
    """Verify key with Google model metadata, not a paid generation request."""
    global AVAILABLE_MODELS
    if not KEY.exists() or KEY.stat().st_size<20:
        with LOCK:STATE["gemini"]=normalized_gateway_status(False,False,"SEM_CHAVE")
        return
    with LOCK:STATE["gemini"]=normalized_gateway_status(True,False,"VALIDANDO_CHAVE")
    try:
        secret=KEY.read_text(encoding="utf-8").strip()
        req=urllib.request.Request(
            "https://generativelanguage.googleapis.com/v1beta/models?pageSize=100",
            headers={"x-goog-api-key":secret})
        with urllib.request.urlopen(req,timeout=16) as response:
            body=json.load(response)
        compatible=[m.get("name","") for m in body.get("models",[])
                    if "generateContent" in m.get("supportedGenerationMethods",[])]
        AVAILABLE_MODELS=preferred_models(compatible,limit=3)
        with LOCK:
            STATE["gemini"]=normalized_gateway_status(True,True,"CREDENCIAL_VALIDADA",
                models=AVAILABLE_MODELS)
    except urllib.error.HTTPError as e:
        code=e.code
        state="CREDENCIAL_INVALIDA" if code in (400,401,403) else "FALHA_REDE"
        with LOCK:STATE["gemini"]=normalized_gateway_status(True,False,state,http_status=code)
    except Exception as e:
        with LOCK:STATE["gemini"]=normalized_gateway_status(
            True,False,"FALHA_REDE",errorType=type(e).__name__)


def maybe_gemini(report):
    global LAST_GEMINI,DAILY_COUNT,DAY
    if not KEY.exists() or not KEY.stat().st_size:
        with LOCK:STATE["gemini"]=normalized_gateway_status(False,False,"SEM_CHAVE")
        return
    # No historical-only generation: require actual current live stream.
    if STATE["status"]!="OBSERVANDO" or not STATE["stream"]:return
    if not STATE["gemini"].get("authenticated"):return
    today=time.localtime().tm_yday
    if today!=DAY:DAY=today;DAILY_COUNT=0
    if DAILY_COUNT>=40 or time.monotonic()-LAST_GEMINI<120:return
    live=report.get("liveSourceCounts",{})
    if live.get("obd",0)<30 or live.get("mp48",0)<30:return
    revisions=RESEARCH_CACHE.get("stats",{}).get("updates",0)
    if not revisions:return
    # The agent reviews new evidence, not only "dramatic" STFT events.
    # A periodic 10 minute re-evaluation prevents stale assumptions.
    if (revisions==STATE.get("lastGeminiRevision")
            and time.monotonic()-LAST_GEMINI<600):return
    LAST_GEMINI=time.monotonic()
    STATE["lastGeminiRevision"]=revisions
    selected=("tps-rpm-response","mp48-state-94","fuel-pattern",
              "rpm-cross-check","map-cross-check")[((DAILY_COUNT//3)%5)]
    safe=build_research_brief(report)
    safe["focusFindingId"]=selected
    safe["priorModelReviews"]=[
        {"findingId":x.get("findingId"),"text":x.get("text","")[:350]}
        for x in (RESEARCH_CACHE.get("reviews") or [])[:3]]
    STATE["currentReviewFocus"]=selected
    with LOCK:
        STATE["gemini"]=normalized_gateway_status(True,True,"ANALISE_EM_ANDAMENTO")

    def call():
        global DAILY_COUNT
        prompt=("Você é o INVESTIGADOR AUTÔNOMO ÔMEGAS, ciclo contínuo de engenharia reversa, SOMENTE LEITURA. "
                "Sua tarefa: encontrar relações novas e verificáveis entre TPS, RPM, MAP, STFT/LTFT, "
                "estados MP48 e ProgBase. Priorize a investigação 'focusFindingId'. "
                "Os campos de evidência foram calculados por código, não por você. "
                "Responda em português e em quatro seções curtas: "
                "1. MEDIDO (citando ID exato de evidência e números do JSON), "
                "2. RELAÇÃO EM ESTUDO (deixe explícito que não prova causa), "
                "3. TECNOLOGIA POSSÍVEL (recurso concreto a construir), "
                "4. PRÓXIMA INVESTIGAÇÃO PASSIVA (sem dirigir testes, alterar mapas, resetar ECU ou gravar central). "
                "Não repita sugestões de ciclos anteriores; procure contradições, lacunas e explicações alternativas. "
                "Diferença de timestamps entre sensores NÃO é tempo de resposta física; "
                "o TPS OBD tem resolução ~1s. 0x94 é estado observado e semântica deve ser auditada. "
                "Não distinga etanol de gasolina sem PID dedicado ou evidência. "
                "O objetivo é conhecimento demonstrável, não ajuste de carro. Máximo 220 palavras.\n"
                +json.dumps(safe,ensure_ascii=False))
        payload={"contents":[{"role":"user","parts":[{"text":prompt}]}]}
        key=KEY.read_text(encoding="utf-8").strip()
        options=AVAILABLE_MODELS or [MODEL]
        last_code=None
        for model in options[:3]:
            if DAILY_COUNT>=40:break
            DAILY_COUNT+=1
            payload["generationConfig"]=generation_settings(model)
            req=urllib.request.Request(
                "https://generativelanguage.googleapis.com/v1beta/models/"
                +model+":generateContent",method="POST",
                headers={"Content-Type":"application/json","x-goog-api-key":key},
                data=json.dumps(payload).encode())
            try:
                with urllib.request.urlopen(req,timeout=28) as response:
                    body=json.load(response)
                if not complete_gemini_response(body):
                    last_code="INCOMPLETE"
                    continue
                answer=" ".join(part.get("text","")
                    for candidate in body.get("candidates",[])
                    for part in candidate.get("content",{}).get("parts",[])
                    if isinstance(part.get("text"),str)).strip()
                if not answer:
                    with LOCK:STATE["gemini"]=normalized_gateway_status(
                        True,True,"FALHA_REDE",model=model,reason="RESPOSTA_VAZIA")
                    return
                at=int(time.time()*1000)
                with LOCK:
                    STATE["gemini"]=normalized_gateway_status(
                        True,True,"SUGESTAO_NAO_E_PROVA",model=model,
                        findingId=selected,text=answer[:2500],atEpochMs=at)
                AI_RESULTS.put({"findingId":selected,"text":answer[:2500],
                                "model":model,"atEpochMs":at})
                return
            except urllib.error.HTTPError as e:
                last_code=e.code
                if e.code in (429,503,404):continue
                state="CREDENCIAL_INVALIDA" if e.code in (401,403) else "ERRO_API"
                with LOCK:STATE["gemini"]=normalized_gateway_status(
                    True,e.code not in (401,403),state,http_status=e.code,model=model)
                return
            except Exception as e:
                with LOCK:STATE["gemini"]=normalized_gateway_status(
                    True,True,"FALHA_REDE",errorType=type(e).__name__,model=model)
                return
        final=("RESPOSTA_INCOMPLETA" if last_code=="INCOMPLETE" else
            "MODELO_INDISPONIVEL" if last_code in (503,404) else
            "LIMITE_API" if last_code==429 else "SEM_EVIDENCIAS")
        with LOCK:STATE["gemini"]=normalized_gateway_status(
            True,True,final,http_status=last_code)
    POOL.submit(call)

def main_loop():
    global STOP,LAST_LIVE_EVENT_AT,LAST_RESEARCH_CYCLE
    DATA.mkdir(parents=True,exist_ok=True)
    while not STOP:
        paths=sorted(DATA.glob("stream-*.ndjson"),key=lambda p:p.stat().st_mtime)
        for path in paths:
            if str(path) not in SEEN:SEEN[str(path)]=0
            try:
                size=path.stat().st_size
                if size<SEEN[str(path)]:SEEN[str(path)]=0
                if size==SEEN[str(path)]:continue
                with path.open("r",encoding="utf-8",errors="replace") as f:
                    f.seek(SEEN[str(path)])
                    while True:
                        offset=f.tell()
                        line=f.readline()
                        if not line:break
                        if not line.endswith("\n"):
                            f.seek(offset)
                            break
                        try: event=json.loads(line)
                        except json.JSONDecodeError:continue
                        if event.get("source") not in ("obd","mp48"):continue
                        with LOCK:
                            ENGINE.ingest(event)
                            DISCOVERY.ingest(event)
                            LIVE_SOURCE_COUNTS[event["source"]]+=1
                            LAST_LIVE_EVENT_AT=time.monotonic()
                    SEEN[str(path)]=f.tell()
                STATE["stream"]=path.name
                STATE["status"]="OBSERVANDO"
            except (OSError,UnicodeError):continue
        if LAST_LIVE_EVENT_AT and time.monotonic()-LAST_LIVE_EVENT_AT>12:
            STATE["status"]="SEM_DADOS_RECENTES"
        if time.monotonic()-LAST_RESEARCH_CYCLE>12:
            try:
                with LOCK:
                    discoveries=DISCOVERY.report()
                    fuel=ENGINE.report()
                findings=list(discoveries["findings"])
                controlled=fuel.get("controlled",{})
                cnt=controlled.get("matchedPairs",0)
                counts=fuel.get("labelCounts",{})
                verdict="EM_INVESTIGACAO" if cnt>=24 else "LACUNA"
                findings.append({"id":"fuel-pattern",
                    "title":"Reconhecimento GNV versus combustível líquido usando somente OBD",
                    "state":verdict,
                    "evidence":f"{cnt} pares sob condições comparáveis; "
                        f"referências líquido={counts.get('liquido',0)}, GNV={counts.get('GNV',0)}; "
                        f"diferença mediana STFT={controlled.get('medianDeltaStftGnvMinusLiquid')} pontos percentuais. "
                        f"Classificador: {fuel.get('obdOnly',{}).get('decision')}.",
                    "nextStep":"Validar entre sessões independentes e em trechos com rótulo MP48 confiável, evitando confundir gasolina com etanol.",
                    "category":"combustivel"})
                JOURNAL.observe(findings,now_ms=int(time.time()*1000))
                with LOCK:
                    RESEARCH_CACHE.update({
                        "discovery":discoveries,
                        "findings":JOURNAL.get_findings(),
                        "reviews":JOURNAL.get_reviews(limit=6),
                        "stats":JOURNAL.stats(),
                        "lastCycleEpochMs":int(time.time()*1000),
                        "currentFocus":"Associação TPS→RPM/MAP, divergências entre sensores e semântica 0x94"})
                LAST_RESEARCH_CYCLE=time.monotonic()
            except Exception as error:
                STATE["researchError"]=type(error).__name__
        while not AI_RESULTS.empty():
            try:
                review=AI_RESULTS.get_nowait()
                JOURNAL.record_gemini(review["findingId"],review["text"],
                    review["model"],review.get("atEpochMs"))
                with LOCK:
                    RESEARCH_CACHE["reviews"]=JOURNAL.get_reviews(limit=6)
                    RESEARCH_CACHE["stats"]=JOURNAL.stats()
            except Exception as error:
                STATE["researchError"]=type(error).__name__
        report=summary()
        try:atomic_json(LATEST,report)
        except OSError:pass
        maybe_gemini(report)
        reload_rules_if_changed()
        time.sleep(0.7)

class Handler(BaseHTTPRequestHandler):
    def log_message(self,format,*args):pass
    def do_POST(self):
        # Local-only UI key onboarding: no API key is ever returned in GET or logs.
        if self.path!="/configure-gemini" or self.client_address[0]!="127.0.0.1":
            self.send_error(404);return
        try:
            length=int(self.headers.get("Content-Length","0"))
            if not 0<length<=512:raise ValueError("invalid length")
            data=json.loads(self.rfile.read(length))
            key=data.get("apiKey","")
            if not isinstance(key,str) or not re.fullmatch(r"[A-Za-z0-9_\-.]{20,256}",key):
                raise ValueError("invalid Gemini key shape")
            CONF.mkdir(parents=True,exist_ok=True)
            tmp=KEY.with_suffix(".tmp")
            fd=os.open(tmp,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
            with os.fdopen(fd,"w") as out:out.write(key+"\n")
            os.replace(tmp,KEY)
            os.chmod(KEY,0o600)
            with LOCK:STATE["gemini"]=normalized_gateway_status(True,False,"VALIDANDO_CHAVE")
            POOL.submit(verify_gemini_credentials)
            body=b'{"ok":true,"gemini":"CHAVE_CONFIGURADA"}'
            self.send_response(200)
        except Exception:
            body=b'{"ok":false,"reason":"invalid configuration"}'
            self.send_response(400)
        self.send_header("Content-Type","application/json")
        self.send_header("Content-Length",str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path=="/health":
            payload={"ok":True,"status":STATE["status"],
                "geminiConfigured":KEY.exists(),
                "geminiAuthenticated":bool(STATE["gemini"].get("authenticated"))}
        elif self.path=="/status":
            payload=summary()
        else:
            self.send_error(404);return
        data=json.dumps(payload,ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type","application/json; charset=utf-8")
        self.send_header("Cache-Control","no-store")
        self.send_header("Content-Length",str(len(data)))
        self.end_headers()
        self.wfile.write(data)

def main():
    CONF.mkdir(parents=True,exist_ok=True)
    if KEY.exists():
        os.chmod(KEY,0o600)
        POOL.submit(verify_gemini_credentials)
    replay_previous_capture()
    server=ThreadingHTTPServer(("127.0.0.1",18765),Handler)
    Thread(target=server.serve_forever,daemon=True).start()
    print("OMEGAS_AGENT_READY http://127.0.0.1:18765/health",flush=True)
    try:main_loop()
    finally:server.shutdown()

if __name__=="__main__":
    main()
