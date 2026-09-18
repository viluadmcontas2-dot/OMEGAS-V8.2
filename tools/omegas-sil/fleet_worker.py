#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import math
import os
import sys
from pathlib import Path

import numpy as np
import pandas as pd

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from corpus import canonicalize_sessions, read_portmon_zip, read_session_zip

INJECTION_SCALE = 0.00256
FUEL_NAMES = {0x00: "ENGINE_OFF", 0x80: "PETROL", 0x88: "TRANSITION", 0x90: "CNG"}
DEFAULT_CACHE = Path(os.environ.get(
    "OMEGAS_SIL_CACHE",
    r"C:\Users\hugov\AppData\Local\AgentRed\cache\omegas-sil-v2\canonical_frames.csv.gz",
))


def u16le(data: bytes, offset: int) -> int:
    return data[offset] | (data[offset + 1] << 8)


def decode_payload(payload: bytes) -> dict:
    p = payload[:34]
    rpm = u16le(p, 0)
    gas_raw = u16le(p, 6)
    petrol_raw = u16le(p, 8)
    fuel_byte = p[11]
    water_raw = p[12]
    level_raw = p[13]
    gas_pressure_raw = u16le(p, 14)
    gas_temp_raw = p[16]
    map_raw = u16le(p, 17)
    raw19 = p[19]
    gas2_raw = u16le(p, 24)
    petrol2_raw = u16le(p, 28)
    map_bar = map_raw / 1000.0
    petrol_ms = petrol_raw * INJECTION_SCALE
    gas_ms = gas_raw * INJECTION_SCALE
    physical_cutoff = rpm >= 1200 and petrol_ms < 0.70 and gas_raw == 0 and map_bar < 0.35
    fuel = "CUTOFF" if physical_cutoff else FUEL_NAMES.get(fuel_byte, "UNKNOWN")
    return {
        "rpm": rpm,
        "gas_raw": gas_raw,
        "gas_ms": gas_ms,
        "petrol_raw": petrol_raw,
        "petrol_ms": petrol_ms,
        "fuel_byte": fuel_byte,
        "fuel": fuel,
        "water_raw": water_raw,
        "level_raw": level_raw,
        "gas_pressure_raw": gas_pressure_raw,
        "gas_temp_raw": gas_temp_raw,
        "map_raw": map_raw,
        "map_bar": map_bar,
        "raw19": raw19,
        "gas2_raw": gas2_raw,
        "petrol2_raw": petrol2_raw,
    }


def read_all_canonical(root: Path):
    sessions = {}
    source_meta = []
    for path in sorted(root.glob("*.zip")):
        try:
            reader = read_portmon_zip if path.name.lower().startswith("portmon") else read_session_zip
            result = reader(path)
            tx = list(result.telemetry)
            source_meta.append({
                "file": path.name,
                "telemetry": len(tx),
                "invalid": result.invalid_replies,
                "unsupported": result.unsupported_reason,
            })
            if tx:
                sessions[path.name] = tx
        except Exception as exc:
            source_meta.append({"file": path.name, "telemetry": 0, "error": repr(exc)})
    return canonicalize_sessions(sessions), source_meta


def build_cache(root: Path, cache: Path, manifest_path: Path):
    corpus, source_meta = read_all_canonical(root)
    rows = []
    for session in corpus.sessions:
        for tx in session.telemetry:
            row = decode_payload(tx.payload)
            row.update({
                "session": session.canonical_id,
                "sequence": tx.sequence,
                "recorded_at_ms": tx.recorded_at_ms,
                "fingerprint": session.fingerprint,
            })
            rows.append(row)
    df = pd.DataFrame(rows)
    df.sort_values(["session", "recorded_at_ms", "sequence"], inplace=True)
    g = df.groupby("session", sort=False)
    df["dt_ms"] = g["recorded_at_ms"].diff()
    df["dmap"] = g["map_bar"].diff()
    df["drpm"] = g["rpm"].diff()
    df["dpetrol_ms"] = g["petrol_ms"].diff()
    df["same_petrol_prev"] = g["petrol_raw"].diff().eq(0)
    df["stale_conflict"] = df["same_petrol_prev"] & df["dmap"].abs().ge(0.01)
    cache.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(cache, index=False, compression="gzip")
    manifest = {
        "root": str(root),
        "cache": str(cache),
        "rows": int(len(df)),
        "canonical_sessions": int(df["session"].nunique()),
        "fuel_counts": {str(k): int(v) for k, v in df["fuel"].value_counts().items()},
        "sessions": [
            {
                "session": s.canonical_id,
                "aliases": list(s.aliases),
                "frames": len(s.telemetry),
                "fingerprint": s.fingerprint,
            }
            for s in corpus.sessions
        ],
        "sources": source_meta,
    }
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps({k: manifest[k] for k in ["rows", "canonical_sessions", "fuel_counts"]}))
    return manifest


def load_cache(cache: Path) -> pd.DataFrame:
    if not cache.exists():
        raise SystemExit(f"CACHE_NOT_FOUND {cache}")
    df = pd.read_csv(cache)
    for c in ["same_petrol_prev", "stale_conflict"]:
        if c in df:
            df[c] = df[c].astype(bool)
    return df


def correction_pct(y_true, y_pred):
    y_true = np.asarray(y_true, dtype=float)
    y_pred = np.asarray(y_pred, dtype=float)
    return (y_pred - y_true) / y_true * 100.0


def metrics(y_true, y_pred) -> dict:
    y_true = np.asarray(y_true, dtype=float)
    y_pred = np.asarray(y_pred, dtype=float)
    good = np.isfinite(y_true) & np.isfinite(y_pred) & (y_true > 0)
    if not good.any():
        return {"n": 0}
    y = y_true[good]
    p = y_pred[good]
    corr = correction_pct(y, p)
    ae = np.abs(p - y)
    ac = np.abs(corr)
    return {
        "n": int(len(y)),
        "mean_signed_correction_pct": float(np.mean(corr)),
        "mean_abs_correction_pct": float(np.mean(ac)),
        "median_abs_correction_pct": float(np.median(ac)),
        "p90_abs_correction_pct": float(np.quantile(ac, 0.90)),
        "p99_abs_correction_pct": float(np.quantile(ac, 0.99)),
        "within_5pct_rate": float(np.mean(ac <= 5.0)),
        "mae_ms": float(np.mean(ae)),
        "p90_abs_error_ms": float(np.quantile(ae, 0.90)),
    }


def petrol_rows(df: pd.DataFrame, clean: bool = True) -> pd.DataFrame:
    x = df[(df["fuel"] == "PETROL") & (df["petrol_ms"] > 0.7) & (df["petrol_ms"] < 30.0)].copy()
    if clean:
        x = x[
            (~x["stale_conflict"]) &
            x["map_bar"].between(0.12, 1.05) &
            x["rpm"].between(500, 6500)
        ].copy()
    return x


def session_profile(cache: Path, session: str, output: Path):
    from sklearn.linear_model import LinearRegression
    from sklearn.preprocessing import PolynomialFeatures
    from sklearn.pipeline import make_pipeline

    df = load_cache(cache)
    raw = df[df["session"] == session].copy()
    x = petrol_rows(raw, clean=False)
    clean = petrol_rows(raw, clean=True)
    out = {
        "session": session,
        "frames": int(len(raw)),
        "petrol_frames": int(len(x)),
        "clean_petrol_frames": int(len(clean)),
        "map_range": [float(x["map_bar"].min()), float(x["map_bar"].max())] if len(x) else [],
        "rpm_range": [float(x["rpm"].min()), float(x["rpm"].max())] if len(x) else [],
        "same_petrol_prev_rate": float(x["same_petrol_prev"].mean()) if len(x) else math.nan,
        "stale_conflict_rate": float(x["stale_conflict"].mean()) if len(x) else math.nan,
        "models": [],
    }
    if len(clean) >= 100:
        cut = max(20, int(len(clean) * 0.8))
        train, test = clean.iloc[:cut], clean.iloc[cut:]
        for degree in range(1, 6):
            model = make_pipeline(PolynomialFeatures(degree, include_bias=False), LinearRegression())
            model.fit(train[["map_bar"]], train["petrol_ms"])
            pred = model.predict(test[["map_bar"]])
            out["models"].append({"name": f"map_poly_{degree}", **metrics(test["petrol_ms"], pred)})
        model = make_pipeline(PolynomialFeatures(2, include_bias=False), LinearRegression())
        model.fit(train[["map_bar", "rpm"]], train["petrol_ms"])
        pred = model.predict(test[["map_bar", "rpm"]])
        out["models"].append({"name": "map_rpm_poly2", **metrics(test["petrol_ms"], pred)})
    output.write_text(json.dumps(out, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps({"session": session, "petrol": len(x), "clean": len(clean), "stale": out["stale_conflict_rate"], "best": min(out["models"], key=lambda m:m.get("mean_abs_correction_pct",999)) if out["models"] else None}))


def loso_splits(df: pd.DataFrame):
    sessions = sorted(df["session"].unique())
    for held in sessions:
        yield held, df[df["session"] != held], df[df["session"] == held]


def macro_summary(folds: list[dict]) -> dict:
    valid = [f for f in folds if f.get("n", 0) > 0 and math.isfinite(f.get("mean_abs_correction_pct", math.nan))]
    if not valid:
        return {"folds": 0}
    keys = ["mean_abs_correction_pct", "median_abs_correction_pct", "p90_abs_correction_pct", "p99_abs_correction_pct", "within_5pct_rate", "mae_ms", "mean_signed_correction_pct"]
    out = {"folds": len(valid)}
    for key in keys:
        vals = [float(f[key]) for f in valid if key in f and math.isfinite(float(f[key]))]
        if vals:
            out["macro_" + key] = float(np.mean(vals))
            out["worst_" + key] = float(max(vals)) if "within" not in key else float(min(vals))
    return out


def global_family(cache: Path, family: str, output: Path):
    os.environ.setdefault("OMP_NUM_THREADS", "1")
    os.environ.setdefault("MKL_NUM_THREADS", "1")
    os.environ.setdefault("OPENBLAS_NUM_THREADS", "1")
    from sklearn.compose import ColumnTransformer
    from sklearn.ensemble import ExtraTreesRegressor, HistGradientBoostingRegressor, RandomForestRegressor
    from sklearn.linear_model import HuberRegressor, LinearRegression, Ridge
    from sklearn.pipeline import make_pipeline
    from sklearn.preprocessing import PolynomialFeatures, SplineTransformer, StandardScaler

    df = petrol_rows(load_cache(cache), clean=True)
    df = df.sort_values(["session", "recorded_at_ms"]).copy()
    # Dynamics use only features available independently of target petrol injection.
    df["dmap_abs"] = df["dmap"].abs().fillna(0.0)
    df["drpm_abs"] = df["drpm"].abs().fillna(0.0)
    candidates = []

    if family == "map":
        for degree in range(1, 8):
            candidates.append((f"poly{degree}", ["map_bar"], make_pipeline(PolynomialFeatures(degree, include_bias=False), Ridge(alpha=1e-6))))
        for knots in [4, 6, 8, 10, 12, 16, 20]:
            for degree in [1, 2, 3]:
                candidates.append((f"spline_k{knots}_d{degree}", ["map_bar"], make_pipeline(SplineTransformer(n_knots=knots, degree=degree, include_bias=False), Ridge(alpha=1e-4))))
    elif family == "rpm":
        for degree in [1, 2, 3]:
            candidates.append((f"map_rpm_poly{degree}", ["map_bar", "rpm"], make_pipeline(PolynomialFeatures(degree, include_bias=False), StandardScaler(), Ridge(alpha=0.1))))
        for alpha in [0.01, 0.1, 1.0, 10.0]:
            candidates.append((f"map_spline_rpm_a{alpha}", ["map_bar", "rpm"], make_pipeline(PolynomialFeatures(2, include_bias=False), StandardScaler(), Ridge(alpha=alpha))))
    elif family == "dynamic":
        features = ["map_bar", "rpm", "dmap", "drpm", "dt_ms", "dmap_abs", "drpm_abs", "water_raw", "gas_pressure_raw", "gas_temp_raw", "raw19"]
        for degree in [1, 2]:
            candidates.append((f"dynamic_poly{degree}", features, make_pipeline(PolynomialFeatures(degree, include_bias=False), StandardScaler(), Ridge(alpha=1.0))))
        for alpha in [0.1, 1.0, 10.0, 100.0]:
            candidates.append((f"dynamic_ridge_{alpha}", features, make_pipeline(StandardScaler(), Ridge(alpha=alpha))))
    elif family == "trees":
        features = ["map_bar", "rpm", "dmap", "drpm", "dt_ms", "water_raw", "gas_pressure_raw", "gas_temp_raw", "raw19"]
        for depth in [6, 10, 16, None]:
            candidates.append((f"extra_d{depth}", features, ExtraTreesRegressor(n_estimators=180, max_depth=depth, min_samples_leaf=3, n_jobs=1, random_state=42)))
        for depth in [6, 10, 16]:
            candidates.append((f"rf_d{depth}", features, RandomForestRegressor(n_estimators=120, max_depth=depth, min_samples_leaf=3, n_jobs=1, random_state=42)))
        for leaves in [15, 31, 63]:
            candidates.append((f"hgb_l{leaves}", features, HistGradientBoostingRegressor(max_iter=220, max_leaf_nodes=leaves, learning_rate=0.07, l2_regularization=0.2, random_state=42)))
    elif family == "robust":
        features = ["map_bar", "rpm"]
        for eps in [1.1, 1.2, 1.35, 1.5, 1.75, 2.0]:
            candidates.append((f"huber_e{eps}", features, make_pipeline(PolynomialFeatures(2, include_bias=False), StandardScaler(), HuberRegressor(epsilon=eps, alpha=0.01, max_iter=300))))
    else:
        raise SystemExit(f"UNKNOWN_FAMILY {family}")

    results = []
    for name, features, estimator in candidates:
        folds = []
        for held, train, test in loso_splits(df):
            train = train.dropna(subset=features + ["petrol_ms"])
            test = test.dropna(subset=features + ["petrol_ms"])
            if len(train) < 200 or len(test) < 20:
                continue
            try:
                estimator.fit(train[features], train["petrol_ms"])
                pred = estimator.predict(test[features])
                fold = {"session": held, **metrics(test["petrol_ms"], pred)}
                folds.append(fold)
            except Exception as exc:
                folds.append({"session": held, "n": 0, "error": repr(exc)})
        summary = macro_summary(folds)
        results.append({"name": name, "features": features, "summary": summary, "folds": folds})
    results.sort(key=lambda r: r["summary"].get("macro_mean_abs_correction_pct", 1e9))
    payload = {"family": family, "rows": len(df), "sessions": int(df["session"].nunique()), "results": results, "best": results[0] if results else None}
    output.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps({"family": family, "rows": len(df), "sessions": int(df["session"].nunique()), "best": results[0]["name"] if results else None, "summary": results[0]["summary"] if results else {}}))


def falsification(cache: Path, output: Path):
    from sklearn.linear_model import Ridge
    from sklearn.pipeline import make_pipeline
    from sklearn.preprocessing import SplineTransformer
    rng = np.random.default_rng(20260918)
    df = petrol_rows(load_cache(cache), clean=True).sort_values(["session", "recorded_at_ms"]).copy()
    folds = []
    for held, train, test in loso_splits(df):
        if len(train) < 200 or len(test) < 20:
            continue
        model = make_pipeline(SplineTransformer(n_knots=8, degree=3, include_bias=False), Ridge(alpha=1e-4))
        model.fit(train[["map_bar"]], train["petrol_ms"])
        pred = model.predict(test[["map_bar"]])
        real = metrics(test["petrol_ms"], pred)
        shuffled = test["petrol_ms"].to_numpy().copy()
        rng.shuffle(shuffled)
        null = metrics(shuffled, pred)
        shifted = metrics(test["petrol_ms"].iloc[1:].to_numpy(), pred[:-1])
        folds.append({"session": held, "real": real, "shuffle_null": null, "lag_misaligned": shifted})
    out = {"folds": folds}
    output.write_text(json.dumps(out, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps({"folds": len(folds), "real_macro_abs_corr": float(np.mean([x["real"]["mean_abs_correction_pct"] for x in folds])), "shuffle_macro_abs_corr": float(np.mean([x["shuffle_null"]["mean_abs_correction_pct"] for x in folds]))}))


def main():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("build-cache")
    p.add_argument("--root", type=Path, required=True)
    p.add_argument("--cache", type=Path, default=DEFAULT_CACHE)
    p.add_argument("--manifest", type=Path, required=True)

    p = sub.add_parser("session-profile")
    p.add_argument("--cache", type=Path, default=DEFAULT_CACHE)
    p.add_argument("--session", required=True)
    p.add_argument("--output", type=Path, required=True)

    p = sub.add_parser("global-family")
    p.add_argument("--cache", type=Path, default=DEFAULT_CACHE)
    p.add_argument("--family", choices=["map", "rpm", "dynamic", "trees", "robust"], required=True)
    p.add_argument("--output", type=Path, required=True)

    p = sub.add_parser("falsification")
    p.add_argument("--cache", type=Path, default=DEFAULT_CACHE)
    p.add_argument("--output", type=Path, required=True)

    args = parser.parse_args()
    if args.cmd == "build-cache":
        build_cache(args.root, args.cache, args.manifest)
    elif args.cmd == "session-profile":
        session_profile(args.cache, args.session, args.output)
    elif args.cmd == "global-family":
        global_family(args.cache, args.family, args.output)
    elif args.cmd == "falsification":
        falsification(args.cache, args.output)


if __name__ == "__main__":
    main()
