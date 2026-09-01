"""Fail-closed governed real-corpus replay for WU-006 G2.

Consumes only the privacy-safe hash-bound episode fixture. Offline evidence only;
not Android runtime, ECU or vehicle authority.
"""
from __future__ import annotations
import base64, gzip, hashlib, json, math
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Sequence
from lab.red_blend.local_science import BootstrapInterval, DistributionSummary, MultimodalityDecision, MultimodalityPolicy, bootstrap_mean_interval, detect_multimodality, summarize_distribution

_FIXTURE_SCHEMA="omegas-science-episode-fixture-index-v1"
_ALLOWED_FIELDS={"session_key","order","fuel","start_ms","end_ms","rpm","map_bar","petrol_ms","window_count","rpm_bin","map_bin"}
_FUELS={"GASOLINA","GNV"}

def _sha(data:bytes)->str:return hashlib.sha256(data).hexdigest()

def _validate_episode(e:Any)->dict[str,Any]:
    if not isinstance(e,dict) or set(e)!=_ALLOWED_FIELDS:raise ValueError("episode fixture privacy/schema shape mismatch")
    sk=e["session_key"]
    if not isinstance(sk,str) or len(sk)!=16 or any(c not in "0123456789abcdef" for c in sk):raise ValueError("episode session_key is not privacy-safe 16-hex")
    if e["fuel"] not in _FUELS:raise ValueError("episode fuel outside governed lane")
    for k in ("rpm","map_bar","petrol_ms"):
        try:v=float(e[k])
        except (TypeError,ValueError) as exc:raise ValueError(f"episode {k} is not numeric") from exc
        if not math.isfinite(v):raise ValueError(f"episode {k} is not finite")
    for k in ("order","start_ms","end_ms","window_count","rpm_bin","map_bin"):
        if isinstance(e[k],bool) or not isinstance(e[k],int):raise ValueError(f"episode {k} must be integer")
    if e["end_ms"]<e["start_ms"] or e["window_count"]<1:raise ValueError("episode temporal/window contract invalid")
    return e

def load_governed_fixture(parts_dir:Path,index_path:Path)->list[dict[str,Any]]:
    index=json.loads(index_path.read_text(encoding="utf-8"))
    if index.get("schema")!=_FIXTURE_SCHEMA:raise ValueError("unsupported fixture index schema")
    expected=index.get("parts") or [];actual=sorted(parts_dir.glob(index["part_glob"]))
    if len(actual)!=index.get("part_count") or len(expected)!=index.get("part_count"):raise ValueError("fixture part count mismatch")
    chunks=[]
    for path,spec in zip(actual,expected):
        if path.name!=spec.get("name"):raise ValueError("fixture part order/name mismatch")
        raw=path.read_bytes()
        if len(raw)!=index.get("part_chars"):raise ValueError(f"fixture part length mismatch: {path.name}")
        if _sha(raw)!=spec.get("sha256"):raise ValueError(f"fixture part sha256 mismatch: {path.name}")
        chunks.append(raw)
    try:compressed=base64.b64decode(b"".join(chunks),validate=True)
    except Exception as exc:raise ValueError("fixture base64 reconstruction failed") from exc
    if len(compressed)!=index.get("compressed_bytes") or _sha(compressed)!=index.get("compressed_sha256"):raise ValueError("reconstructed compressed fixture mismatch")
    try:raw=gzip.decompress(compressed)
    except (OSError,EOFError) as exc:raise ValueError("fixture gzip reconstruction failed") from exc
    if len(raw)!=index.get("uncompressed_bytes") or _sha(raw)!=index.get("uncompressed_sha256"):raise ValueError("reconstructed uncompressed fixture mismatch")
    lines=[line for line in raw.splitlines() if line]
    if len(lines)!=index.get("episode_lines"):raise ValueError("fixture episode line count mismatch")
    return [_validate_episode(json.loads(line)) for line in lines]

@dataclass(frozen=True)
class RealRegionAudit:
    rpm_bin:int;map_bin:int;count:int;unique_sessions:int;mean_rpm:float;mean_map_bar:float;summary:DistributionSummary;bootstrap:BootstrapInterval;multimodality:MultimodalityDecision;classification:str
@dataclass(frozen=True)
class RealCorpusReport:
    fuel:str;total_fuel_episodes:int;analyzed_regions:int;analyzed_episodes:int;sparse_episodes:int;unimodal_regions:int;multimodal_regions:int;ambiguous_regions:int;regions:tuple[RealRegionAudit,...];claim_scope:str="REAL_CORPUS_LOCAL_ONLY_NOT_TRANSFER";policy_label:str="LAB_HEURISTIC"

def _region_seed(seed,rpm_bin,map_bin):return (int(seed)*1_000_003+int(rpm_bin)*9_176+int(map_bin)*6_113)&0x7fffffff

def analyze_real_regions(episodes:Sequence[dict[str,Any]],*,fuel:str,min_samples:int=4,bootstrap_draws:int=1000,seed:int=0,policy:MultimodalityPolicy=MultimodalityPolicy())->RealCorpusReport:
    if fuel not in _FUELS:raise ValueError("fuel must be GASOLINA or GNV")
    if min_samples<2:raise ValueError("min_samples must be >= 2")
    selected=[_validate_episode(dict(e)) for e in episodes if e.get("fuel")==fuel];groups=defaultdict(list)
    for e in selected:groups[(e["rpm_bin"],e["map_bin"])].append(e)
    regions=[]
    for (rb,mb),g in sorted(groups.items()):
        if len(g)<min_samples:continue
        samples=[float(e["petrol_ms"]) for e in g];summary=summarize_distribution(samples);boot=bootstrap_mean_interval(samples,draws=bootstrap_draws,seed=_region_seed(seed,rb,mb),alpha=.05);decision=detect_multimodality(samples,policy)
        classification="MULTIMODAL" if decision.is_multimodal else ("UNIMODAL_SUPPORTED" if decision.bic_gain<=0 else "AMBIGUOUS_MIXTURE_SIGNAL")
        regions.append(RealRegionAudit(rb,mb,len(g),len({e["session_key"] for e in g}),sum(float(e["rpm"]) for e in g)/len(g),sum(float(e["map_bar"]) for e in g)/len(g),summary,boot,decision,classification))
    regions.sort(key=lambda r:(-r.count,r.rpm_bin,r.map_bin));classes=Counter(r.classification for r in regions);analyzed=sum(r.count for r in regions)
    return RealCorpusReport(fuel,len(selected),len(regions),analyzed,len(selected)-analyzed,classes["UNIMODAL_SUPPORTED"],classes["MULTIMODAL"],classes["AMBIGUOUS_MIXTURE_SIGNAL"],tuple(regions))
