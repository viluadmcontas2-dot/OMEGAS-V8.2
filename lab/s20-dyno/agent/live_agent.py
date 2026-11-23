"""Ômegas investigator: local continuous stream + optional Gemini on aggregated evidence.

Never writes to vehicle interfaces. Never sends raw frames to Gemini.
Hot-reloads JSON rules and Python modules without APK recompilation.
"""
from pathlib import Path
from threading import Thread, Lock
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from concurrent.futures import ThreadPoolExecutor
import json, os, time, glob, urllib.request, urllib.error, re
from fuel_engine import FuelEngine

ROOT=Path.home()/"omegas-intelligence"
DATA=Path("/sdcard/Download/Omegas/Inteligencia")
CONF=Path.home()/".config/omegas"
RULES=ROOT/"rules.json"
KEY=CONF/"gemini.key"
LATEST=DATA/"latest-analysis.json"
MODEL="gemini-3.8-flash"
LOCK=Lock()
ENGINE=FuelEngine()
STATE={"status":"AGUARDANDO_TELEMETRIA","gemini":{"status":"SEM_CHAVE"},"uptimeSince":int(time.time()),"stream":None}
SEEN={}
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
        r["gemini"]=STATE["gemini"]
        r["stream"]=STATE["stream"]
        r["generatedAtEpochMs"]=int(time.time()*1000)
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
            ENGINE.ingest({"v":1,"source":x["source"],"monoNs":int(x["mono_ns"]),
                           "t":x.get("wall_ms"),"values":x.get("data",{})})
        except Exception:continue
    STATE["historicalEvidence"]="3904 registros de 21/11/2026 reprocessados; rotulos 0x88 nao sao gasolina"

def maybe_gemini(report):
    global LAST_GEMINI,DAILY_COUNT,DAY
    if not KEY.exists() or not KEY.stat().st_size:
        STATE["gemini"]={"status":"SEM_CHAVE"}
        return
    day=time.localtime().tm_yday
    if day!=DAY: DAY=day;DAILY_COUNT=0
    events=report.get("events",[])
    if not events or DAILY_COUNT>=40 or time.monotonic()-LAST_GEMINI<120:
        return
    stamp=events[-1].get("atMonoNs")
    if stamp==STATE.get("lastGeminiEvent"):return
    if report.get("sourceCounts",{}).get("obd",0)<40:return
    LAST_GEMINI=time.monotonic()
    DAY=day;DAILY_COUNT+=1
    STATE["lastGeminiEvent"]=stamp
    safe={"labelCounts":report.get("labelCounts"),
          "controlled":report.get("controlled"),
          "obdOnly":report.get("obdOnly"),
          "events":events[-4:],
          "mp48State":report.get("mp48")}
    def call():
        try:
            key=KEY.read_text(encoding="utf-8").strip()
            if not key:return
            prompt=("Atue como auditor de ECU/MP48. Somente leitura. "
                "Diferencie evidência observada, hipótese e lacuna; não invente causa, "
                "não classifique gasolina/etanol/GNV sem prova; não prescreva gravações. "
                "Até 180 palavras. Baseie-se apenas no JSON agregado.\n"
                +json.dumps(safe,ensure_ascii=False))
            payload={"contents":[{"role":"user","parts":[{"text":prompt}]}],
                     "generationConfig":{"temperature":0.15,"maxOutputTokens":500}}
            url="https://generativelanguage.googleapis.com/v1beta/models/"+MODEL+":generateContent"
            request=urllib.request.Request(url,method="POST",headers={
                "Content-Type":"application/json","x-goog-api-key":key},
                data=json.dumps(payload).encode())
            with urllib.request.urlopen(request,timeout=22) as response:
                body=json.loads(response.read(100000))
            answer=" ".join(p.get("text","") for c in body.get("candidates",[])
                            for p in c.get("content",{}).get("parts",[]) if "text" in p).strip()
            with LOCK:
                STATE["gemini"]={"status":"SUGESTAO_NAO_E_PROVA",
                    "model":MODEL,"text":answer[:2500],"atEpochMs":int(time.time()*1000)}
        except urllib.error.HTTPError as e:
            with LOCK:STATE["gemini"]={"status":"ERRO_API","httpStatus":e.code}
        except Exception as e:
            with LOCK:STATE["gemini"]={"status":"FALHA_CONEXAO","type":type(e).__name__}
    POOL.submit(call)

def main_loop():
    global STOP
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
                        with LOCK:ENGINE.ingest(event)
                    SEEN[str(path)]=f.tell()
                STATE["stream"]=path.name
                STATE["status"]="OBSERVANDO"
            except (OSError,UnicodeError):continue
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
            with LOCK:STATE["gemini"]={"status":"CHAVE_CONFIGURADA"}
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
            payload={"ok":True,"status":STATE["status"],"geminiConfigured":KEY.exists()}
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
    if KEY.exists():os.chmod(KEY,0o600)
    replay_previous_capture()
    server=ThreadingHTTPServer(("127.0.0.1",18765),Handler)
    Thread(target=server.serve_forever,daemon=True).start()
    print("OMEGAS_AGENT_READY http://127.0.0.1:18765/health",flush=True)
    try:main_loop()
    finally:server.shutdown()

if __name__=="__main__":
    main()
