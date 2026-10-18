#!/usr/bin/env bash
# Regenera TODA a decompilação do ProgBase.exe do zero (idempotente). Requer: JDK 21, Ghidra 12.1.4 (tools/rea/install.sh -> ~/.omegas-rea), pefile, o exe (tools/obter_progbase.py).
# Uso: PB_EXE=/caminho/ProgBase.exe [GHIDRA=~/.omegas-rea/ghidra_12.1.4_PUBLIC] [WORK=/tmp/pbwork] tools/progbase/decomp_tudo.sh
set -euo pipefail
: "${PB_EXE:?defina PB_EXE}"; G="${GHIDRA:-$HOME/.omegas-rea/ghidra_12.1.4_PUBLIC}"; W="${WORK:-/tmp/pbwork}"; R="$(cd "$(dirname "$0")/../.." && pwd)"; S="$R/tools/progbase"
mkdir -p "$W/out" "$W/proj"
python3 "$S/decomp_rtti.py" "$PB_EXE"
python3 - "$R" "$W" <<'PY'
import json,sys
d=json.load(open(sys.argv[1]+'/docs/progbase/decomp/rtti.json'))
open(sys.argv[2]+'/seeds.tsv','w').write(''.join(a+'\t'+l[0]['nome']+'\n' for a,l in sorted(d['nomes_por_endereco'].items())))
PY
GH="$G/support/analyzeHeadless $W/proj pb"
$G/support/analyzeHeadless "$W/proj" pb -import "$PB_EXE" -overwrite                      # 1) análise automática (~15 min)
$GH -process pb.exe -scriptPath "$S/ghidra" -preScript SeedNomes.java "$W/seeds.tsv" -postScript ExportFuncs.java "$W/out/funcs.jsonl"   # 2) nomes RTTI + export
python3 "$S/decomp_funcoes.py" "$W/out/funcs.jsonl"
python3 - "$R" "$W" <<'PY'
import gzip,json,sys
F=json.load(gzip.open(sys.argv[1]+'/docs/progbase/decomp/funcoes.json.gz'))['funcoes']
open(sys.argv[2]+'/out/todos.txt','w').write(''.join(f['a']+'\n' for f in F if not f['thunk'] and f['tam']<60000)+'0x00439E94\n')
PY
$GH -process pb.exe -noanalysis -scriptPath "$S/ghidra" -postScript DecompLista.java "$W/out/todos.txt" "$W/out/dec.jsonl" 40   # 3) pseudo-C (~25 min; retomável)
python3 "$S/decomp_empacotar.py" "$W/out/dec.jsonl"
$GH -process pb.exe -noanalysis -scriptPath "$S/ghidra" -postScript ExportLacunas.java "$W/out/lacunas.tsv" && gzip -9nc "$W/out/lacunas.tsv" > "$R/docs/progbase/decomp/lacunas.tsv.gz"
python3 "$S/decomp_lacunas.py" "$PB_EXE"; python3 "$S/decomp_cobertura.py"; python3 "$S/grafo_progbase.py"
