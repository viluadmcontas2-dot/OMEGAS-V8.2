#!/usr/bin/env python3
"""Extrator estático do grafo produtor <-> consumidor (Kotlin -> ponte -> JS).

Saída (dict):
  producers      : chave JSON -> arquivos Kotlin que a emitem (`.put("k"`, `"k" to`)
  js_reads       : chave lida pelo JS -> arquivos JS que a leem
  js_defined     : chaves que o próprio JS cria (literais de objeto, atribuições, variáveis)
  kotlin_methods : {ponte -> métodos @JavascriptInterface}
  js_calls       : {ponte -> métodos que o JS chama}
Somente leitura de arquivos; sem dependências. `ROOT_OVERRIDE` permite apontar para uma cópia
(usado pelos mutantes).
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
KOTLIN_REL = "app/src/main/java/com/omegas/prohub"
UI_REL = "app/src/main/assets/ui"

# Arquivos que montam as respostas JSON entregues à WebView (superfície do contrato produtor).
BRIDGE_FACING = (
    "web/HubJavascriptBridge.kt", "autocal/AutoCalJavascriptBridge.kt", "web/CalibrationOperationsBridge.kt",
    "web/PowerJavascriptBridge.kt", "autocal/EquivalenceView.kt", "equivalence/EquivalenceJson.kt",
    "autocal/AutoCalUiProjection.kt", "autocal/EquivalencePhases.kt",
)
# Diretórios cujos produtores também chegam ao JS (snapshots, ledger, diário...).
BRIDGE_FACING_DIRS = ()

BRIDGE_CLASSES = {
    "OmegasNative": "web/HubJavascriptBridge.kt",
    "OmegasCalibration": "web/CalibrationOperationsBridge.kt",
    "OmegasPower": "web/PowerJavascriptBridge.kt",
    "OmegasAutoCal": "autocal/AutoCalJavascriptBridge.kt",
}

BUILTIN = set("""
length push pop shift unshift slice splice map filter find findIndex some every reduce forEach includes indexOf lastIndexOf join concat sort reverse flat flatMap fill keys values entries
toString toFixed toLocaleString toUpperCase toLowerCase trim padStart padEnd replace replaceAll split match test exec startsWith endsWith substring substr charAt charCodeAt repeat normalize at
min max abs round floor ceil sqrt pow log hypot sign trunc isFinite isNaN isInteger parseFloat parseInt now PI
assign freeze create from isArray stringify parse has get set add delete clear size then catch finally resolve reject all
call apply bind prototype constructor name message stack error warn info
document body head documentElement window globalThis console localStorage sessionStorage navigator
querySelector querySelectorAll closest matches getElementById getAttribute setAttribute removeAttribute hasAttribute
classList toggle contains dataset style textContent innerHTML innerText outerHTML value checked disabled hidden open selected id className title href src rel type placeholder tabIndex
appendChild append prepend insertBefore removeChild remove replaceChildren createElement createElementNS createTextNode cloneNode parentElement parentNode children childNodes firstChild lastChild
firstElementChild nextElementSibling previousElementSibling
addEventListener removeEventListener dispatchEvent preventDefault stopPropagation target currentTarget relatedTarget key code detail
focus blur click scrollIntoView scrollTop scrollLeft clientWidth clientHeight offsetWidth offsetHeight getBoundingClientRect width height left top right bottom
setProperty getPropertyValue removeProperty display position opacity transform visibility
setTimeout clearTimeout setInterval clearInterval requestAnimationFrame cancelAnimationFrame
activeElement visibilityState readyState hash
OmegasUi OmegasApp OmegasNative OmegasCalibration OmegasPower OmegasAutoCal
toLocaleDateString toLocaleTimeString getTime getFullYear getMonth getDate getHours getMinutes getSeconds toISOString
done next iterator innerWidth innerHeight pointerId setPointerCapture releasePointerCapture tagName childElementCount nodeType nodeName ownerDocument isConnected offsetTop offsetLeft scrollHeight scrollWidth clientX clientY pageX pageY button buttons ctrlKey shiftKey altKey metaKey deltaY deltaX timeStamp isTrusted EPSILON MAX_SAFE_INTEGER POSITIVE_INFINITY NEGATIVE_INFINITY
""".split())


def _root(root=None):
    return pathlib.Path(root) if root else ROOT


def kotlin_files(root=None):
    return sorted((_root(root) / KOTLIN_REL).rglob("*.kt"))


def js_files(root=None):
    ui = _root(root) / UI_REL
    files = [ui / "app.js", ui / "map-editor.js"]
    for sub in ("core", "screens", "components"):
        files += sorted((ui / sub).glob("*.js"))
    return [f for f in files if f.exists()]


def extract_producers(root=None):
    prod = {}
    pat = re.compile(r'\.(?:put|putOpt)\(\s*"([A-Za-z_][\w\-]*)"')
    pat2 = re.compile(r'"([A-Za-z_]\w*)"\s+to\s')
    base = _root(root) / KOTLIN_REL
    for path in kotlin_files(root):
        rel = path.relative_to(base).as_posix()
        text = path.read_text(encoding="utf-8")
        for m in list(pat.finditer(text)) + list(pat2.finditer(text)):
            prod.setdefault(m.group(1), set()).add(rel)
    return prod


def strip_js_noise(text):
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    return re.sub(r"(?m)(?<![:\\'\"`])//.*$", "", text)


READ_DOT = re.compile(r"(?:[A-Za-z_$][\w$]*|\)|\])\s*\??\.\s*([A-Za-z_$][\w$]*)\s*(\(?)")
READ_BRACKET = re.compile(r"\[\s*['\"]([A-Za-z_]\w*)['\"]\s*\]")
DESTRUCT = re.compile(r"(?:const|let|var)\s*\{([^}=]*)\}\s*=")
OBJ_KEY = re.compile(r"(?<![\w$.?])([A-Za-z_$][\w$]*)\s*:(?!:)")
ASSIGN = re.compile(r"\.\s*([A-Za-z_$][\w$]*)\s*(?:=(?!=)|\+=|-=|\|\|=|\?\?=)")
SHORTHAND = re.compile(r"[{,]\s*([A-Za-z_$][\w$]*)\s*(?=[,}])")
DECL = re.compile(r"(?:const|let|var|function|class)\s+([A-Za-z_$][\w$]*)")
METHOD = re.compile(r"(?m)^\s*(?:static\s+|async\s+|get\s+|set\s+)*([A-Za-z_$][\w$]*)\s*\([^)]*\)\s*\{")


def extract_js(root=None):
    reads, defined = {}, set()
    base = _root(root) / UI_REL
    for path in js_files(root):
        rel = path.relative_to(base).as_posix()
        text = strip_js_noise(path.read_text(encoding="utf-8"))
        no_str = re.sub(r"'(?:\\.|[^'\\\n])*'|\"(?:\\.|[^\"\\\n])*\"", "''", text)
        for m in READ_DOT.finditer(no_str):
            if m.group(2) == "(":
                continue
            reads.setdefault(m.group(1), set()).add(rel)
        for m in READ_BRACKET.finditer(text):
            reads.setdefault(m.group(1), set()).add(rel)
        for m in DESTRUCT.finditer(no_str):
            for part in m.group(1).split(","):
                name = part.split(":")[0].split("=")[0].strip()
                if re.fullmatch(r"[A-Za-z_$][\w$]*", name):
                    reads.setdefault(name, set()).add(rel)
        for rx in (OBJ_KEY, ASSIGN, SHORTHAND, DECL, METHOD):
            for m in rx.finditer(no_str):
                defined.add(m.group(1))
        for m in re.finditer(r"['\"]([A-Za-z_]\w*)['\"]\s*:", text):
            defined.add(m.group(1))
    return reads, defined


# Propriedades legítimas de número/texto/booleano: `.chave.length` não é objeto aninhado.
SCALAR_PROPS = set("length toFixed toString toLocaleString toUpperCase toLowerCase trim padStart padEnd replace replaceAll split match startsWith endsWith includes indexOf substring slice charAt normalize at".split())
_OBJECT_EXPR = re.compile(r"JSONObject\s*\(|JSONArray\s*\(|\b\w*[Jj]son\w*\s*\(|\.json\s*\(|\bmapOf\b|\blistOf\b|\.map\s*\{|\.toJson")
_PUT_VALUE = re.compile(r'\.(?:put|putOpt)\(\s*"([A-Za-z_][\w\-]*)"\s*,\s*([^\n]*)')
# Contrato do cérebro de equivalência (Kotlin -> eq.* no Refino/AutoCal). O nome `index` também existe em outros
# objetos (sessões, pontos); por isso a leitura só é conferida em variáveis que carregam o resultado do cérebro.
SHAPE_FILES = ("equivalence/EquivalenceJson.kt",)
SHAPE_RECEIVERS = ("eq", "equivalence", "view", "result", "analysis")


def extract_scalar_keys(root=None):
    """Chaves que as fontes do contrato (SHAPE_FILES) emitem SEMPRE como escalar (número/texto/booleano).

    `.put("index", num(x))` e `.put("coverage", result.coverage)` são escalares; `JSONObject()`, `JSONArray(...)`
    ou função de montagem não são. Uma chave com qualquer emissão não escalar fica de fora.
    """
    scalar, other = set(), set()
    base = _root(root) / KOTLIN_REL
    for rel in SHAPE_FILES:
        path = base / rel
        if not path.exists():
            continue
        for m in _PUT_VALUE.finditer(path.read_text(encoding="utf-8")):
            value = m.group(2).strip()
            if re.match(r"^JSONObject\.NULL\s*[,)]", value):
                continue  # null não decide a forma: o escalar vem das outras emissões da chave
            certain = value.startswith("num(") or re.match(r"^[a-z]\w*\.[a-z]\w*\s*[,)]", value) or re.match(r'^("[^"]*"|-?\d[\d.]*|true|false)\s*[,)]', value)
            # Só vale como escalar o que é claramente escalar; variável solta (`ref`, `points`) pode ser objeto.
            (scalar if certain and not _OBJECT_EXPR.search(value) else other).add(m.group(1))
    return scalar - other


def extract_shape_violations(root=None):
    """JS que lê `eq.chave.filho` onde o Kotlin emite `chave` escalar (ex.: `eq.index.value` com `index` = 0..1).

    O conjunto BUILTIN contém `value`, o que escondia exatamente este erro; aqui o filho de um escalar é sempre defeito.
    Devolve {"chave.filho": [arquivos JS]}.
    """
    scalar = extract_scalar_keys(root)
    if not scalar:
        return {}
    chain = re.compile(r"\b(?:%s)\s*\??\.\s*(%s)\s*\??\.\s*([A-Za-z_$][\w$]*)" % ("|".join(SHAPE_RECEIVERS), "|".join(sorted(scalar))))
    out = {}
    base = _root(root) / UI_REL
    for path in js_files(root):
        text = strip_js_noise(path.read_text(encoding="utf-8"))
        for m in chain.finditer(text):
            key, child = m.group(1), m.group(2)
            if child not in SCALAR_PROPS:
                out.setdefault(f"{key}.{child}", set()).add(path.relative_to(base).as_posix())
    return {k: sorted(v) for k, v in out.items()}


def extract_kotlin_methods(root=None):
    out = {}
    for js_name, rel in BRIDGE_CLASSES.items():
        text = (_root(root) / KOTLIN_REL / rel).read_text(encoding="utf-8")
        out[js_name] = set(re.findall(r"@JavascriptInterface\s+fun\s+(\w+)", text))
    return out


def extract_js_calls(root=None):
    calls = {k: set() for k in BRIDGE_CLASSES}
    target = {"native": "OmegasNative", "calibration": "OmegasCalibration", "power": "OmegasPower"}
    for path in js_files(root):
        text = path.read_text(encoding="utf-8")
        for m in re.finditer(
                r"invoke\(\s*(?:this\.|root\.)?(native|calibration|power|OmegasAutoCal|OmegasNative|OmegasCalibration|OmegasPower)\s*,\s*'(\w+)'",
                text):
            calls[target.get(m.group(1), m.group(1))].add(m.group(2))
        if path.name == "autocal-api.js":
            for m in re.finditer(r"invoke\('(\w+)'", text):
                calls["OmegasAutoCal"].add(m.group(1))
        for bridge in BRIDGE_CLASSES:
            for m in re.finditer(r"(?:root|window|globalThis)\.%s\s*\??\.\s*(\w+)" % bridge, text):
                calls[bridge].add(m.group(1))
        if path.name == "native-api.js":
            for m in re.finditer(r"this\.(native|calibration|power)\s*\??\.\s*(\w+)", text):
                calls[target[m.group(1)]].add(m.group(2))
            for m in re.finditer(r"typeof this\.(native|calibration|power)\.(\w+)", text):
                calls[target[m.group(1)]].add(m.group(2))
    return calls


def extract(root=None):
    prod = extract_producers(root)
    reads, defined = extract_js(root)
    return {
        "producers": {k: sorted(v) for k, v in prod.items()},
        "js_reads": {k: sorted(v) for k, v in reads.items()},
        "js_defined": sorted(defined),
        "kotlin_methods": {k: sorted(v) for k, v in extract_kotlin_methods(root).items()},
        "js_calls": {k: sorted(v) for k, v in extract_js_calls(root).items()},
        "builtin": sorted(BUILTIN),
        "shape_violations": extract_shape_violations(root),
    }


def is_bridge_facing(files):
    return any(f in BRIDGE_FACING or f.startswith(BRIDGE_FACING_DIRS) for f in files)


def analyse(data, allow):
    """Devolve (unfed, wind, missing_methods, unused_methods)."""
    prod = set(data["producers"])
    builtin = set(data["builtin"])
    defined = set(data["js_defined"])
    local = set(allow.get("consumer_local", {}))
    unfed = sorted(
        k for k in data["js_reads"]
        if k[0].islower() and k not in prod and k not in builtin and k not in defined and k not in local
    )
    read = set(data["js_reads"])
    internal = set(allow.get("producer_internal", {}))
    wind = sorted(
        k for k, files in data["producers"].items()
        if k not in read and k not in internal and is_bridge_facing(files)
    )
    missing, unused = [], []
    unused_ok = set(allow.get("methods_unused", {}))
    for bridge, calls in data["js_calls"].items():
        have = set(data["kotlin_methods"][bridge])
        for name in sorted(calls):
            if name not in have and name not in builtin:
                missing.append(f"{bridge}.{name}")
        for name in sorted(have - set(calls)):
            if f"{bridge}.{name}" not in unused_ok:
                unused.append(f"{bridge}.{name}")
    return unfed, wind, missing, unused


if __name__ == "__main__":
    data = extract()
    if len(sys.argv) > 1 and sys.argv[1] == "--json":
        print(json.dumps(data, ensure_ascii=False, indent=1))
    else:
        allow_path = ROOT / "tests/wiring/allowlist.json"
        allow = json.loads(allow_path.read_text(encoding="utf-8")) if allow_path.exists() else {}
        unfed, wind, missing, unused = analyse(data, allow)
        print("producers", len(data["producers"]), "reads", len(data["js_reads"]))
        print("UNFED", len(unfed), unfed)
        print("WIND", len(wind), wind)
        print("MISSING_METHODS", missing)
        print("UNUSED_METHODS", unused)
