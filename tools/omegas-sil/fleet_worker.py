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



def frame_df_from_transactions(session_name: str, transactions) -> pd.DataFrame:
    rows = []
    for tx in transactions:
        row = decode_payload(tx.payload)
        row.update({
            "session": session_name,
            "sequence": tx.sequence,
            "recorded_at_ms": tx.recorded_at_ms,
            "fingerprint": "",
        })
        rows.append(row)
    df = pd.DataFrame(rows)
    if df.empty:
        return df
    df.sort_values(["recorded_at_ms", "sequence"], inplace=True)
    df["dt_ms"] = df["recorded_at_ms"].diff()
    df["dmap"] = df["map_bar"].diff()
    df["drpm"] = df["rpm"].diff()
    df["dpetrol_ms"] = df["petrol_ms"].diff()
    df["same_petrol_prev"] = df["petrol_raw"].diff().eq(0)
    df["stale_conflict"] = df["same_petrol_prev"] & df["dmap"].abs().ge(0.01)
    return df


def source_profile(root: Path, source: str, output: Path, cache_output: Path | None = None):
    path = root / source
    reader = read_portmon_zip if path.name.lower().startswith("portmon") else read_session_zip
    result = reader(path)
    df = frame_df_from_transactions(source, result.telemetry)
    if df.empty:
        out = {"session": source, "frames": 0, "unsupported": result.unsupported_reason, "invalid": result.invalid_replies}
        output.write_text(json.dumps(out, indent=2), encoding="utf-8")
        print(json.dumps(out))
        return
    if cache_output is not None:
        cache_output.parent.mkdir(parents=True, exist_ok=True)
        df.to_csv(cache_output, index=False, compression="gzip")
        temp = cache_output
        delete_temp = False
    else:
        temp = output.with_suffix(".tmp.csv.gz")
        df.to_csv(temp, index=False, compression="gzip")
        delete_temp = True
    try:
        session_profile(temp, source, output)
        payload = json.loads(output.read_text(encoding="utf-8"))
        payload["invalid_replies"] = result.invalid_replies
        payload["transactions"] = len(result.transactions)
        payload["unsupported"] = result.unsupported_reason
        output.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    finally:
        if delete_temp:
            temp.unlink(missing_ok=True)


def build_cache_selected(root: Path, sources: list[str], cache: Path, manifest_path: Path):
    sessions = {}
    source_meta = []
    for name in sources:
        path = root / name
        reader = read_portmon_zip if path.name.lower().startswith("portmon") else read_session_zip
        try:
            result = reader(path)
            tx = list(result.telemetry)
            source_meta.append({"file": name, "telemetry": len(tx), "invalid": result.invalid_replies, "unsupported": result.unsupported_reason})
            if tx:
                sessions[name] = tx
        except Exception as exc:
            source_meta.append({"file": name, "telemetry": 0, "error": repr(exc)})
    corpus = canonicalize_sessions(sessions)
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
        "root": str(root), "cache": str(cache), "rows": int(len(df)),
        "canonical_sessions": int(df["session"].nunique()),
        "fuel_counts": {str(k): int(v) for k, v in df["fuel"].value_counts().items()},
        "sessions": [{"session": s.canonical_id, "aliases": list(s.aliases), "frames": len(s.telemetry), "fingerprint": s.fingerprint} for s in corpus.sessions],
        "sources": source_meta,
    }
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps({k: manifest[k] for k in ["rows", "canonical_sessions", "fuel_counts"]}))


def add_stability_features(df: pd.DataFrame, window: int) -> pd.DataFrame:
    x = df.sort_values(["session", "recorded_at_ms", "sequence"]).copy()
    grouped = x.groupby("session", sort=False)
    x[f"map_range_w{window}"] = grouped["map_bar"].transform(
        lambda s: s.rolling(window, min_periods=window).max() - s.rolling(window, min_periods=window).min()
    )
    x[f"rpm_range_w{window}"] = grouped["rpm"].transform(
        lambda s: s.rolling(window, min_periods=window).max() - s.rolling(window, min_periods=window).min()
    )
    x["fresh_petrol"] = ~x["same_petrol_prev"].fillna(False)
    return x


def stable_filter(
    df: pd.DataFrame,
    window: int,
    map_range: float,
    rpm_range: float,
    require_fresh: bool,
    max_dt_ms: float | None,
) -> pd.DataFrame:
    x = add_stability_features(df, window)
    mask = (
        x[f"map_range_w{window}"].le(map_range) &
        x[f"rpm_range_w{window}"].le(rpm_range) &
        (~x["stale_conflict"])
    )
    if require_fresh:
        mask &= x["fresh_petrol"]
    if max_dt_ms is not None:
        mask &= x["dt_ms"].fillna(max_dt_ms).le(max_dt_ms)
    return x[mask].copy()


def fit_predict_baseline(train: pd.DataFrame, test: pd.DataFrame, model_name: str):
    from sklearn.linear_model import Ridge
    from sklearn.pipeline import make_pipeline
    from sklearn.preprocessing import PolynomialFeatures, SplineTransformer, StandardScaler

    if model_name.startswith("spline"):
        _, k, d = model_name.split("_")
        knots = int(k[1:])
        degree = int(d[1:])
        model = make_pipeline(
            SplineTransformer(n_knots=knots, degree=degree, include_bias=False),
            Ridge(alpha=1e-4),
        )
        features = ["map_bar"]
    elif model_name == "map_rpm_poly2":
        model = make_pipeline(
            PolynomialFeatures(2, include_bias=False),
            StandardScaler(),
            Ridge(alpha=0.1),
        )
        features = ["map_bar", "rpm"]
    elif model_name == "map_rpm_poly3":
        model = make_pipeline(
            PolynomialFeatures(3, include_bias=False),
            StandardScaler(),
            Ridge(alpha=0.3),
        )
        features = ["map_bar", "rpm"]
    else:
        raise ValueError(model_name)

    train2 = train.dropna(subset=features + ["petrol_ms"])
    test2 = test.dropna(subset=features + ["petrol_ms"])
    if len(train2) < 100 or len(test2) < 20:
        return None, test2
    model.fit(train2[features], train2["petrol_ms"])
    return model.predict(test2[features]), test2


def stable_sweep(cache: Path, window: int, output: Path):
    raw = petrol_rows(load_cache(cache), clean=False)
    model_names = [
        "spline_k4_d2", "spline_k6_d2", "spline_k8_d2", "spline_k10_d2",
        "spline_k12_d2", "spline_k16_d2", "spline_k20_d2",
        "spline_k6_d3", "spline_k10_d3", "spline_k16_d3",
        "map_rpm_poly2", "map_rpm_poly3",
    ]
    map_ranges = [0.004, 0.006, 0.008, 0.010, 0.015, 0.020, 0.030, 0.050]
    rpm_ranges = [20, 35, 50, 75, 100, 150, 250]
    max_dts = [None, 400.0, 600.0, 900.0]
    fresh_options = [True, False]
    results = []
    for map_thr in map_ranges:
        for rpm_thr in rpm_ranges:
            for require_fresh in fresh_options:
                for max_dt in max_dts:
                    filtered = stable_filter(raw, window, map_thr, rpm_thr, require_fresh, max_dt)
                    session_counts = filtered.groupby("session").size()
                    usable_sessions = session_counts[session_counts >= 40].index.tolist()
                    filtered = filtered[filtered["session"].isin(usable_sessions)]
                    if len(usable_sessions) < 3 or len(filtered) < 300:
                        continue
                    for model_name in model_names:
                        folds = []
                        for held in usable_sessions:
                            train = filtered[filtered["session"] != held]
                            test = filtered[filtered["session"] == held]
                            pred, test2 = fit_predict_baseline(train, test, model_name)
                            if pred is None:
                                continue
                            folds.append({"session": held, **metrics(test2["petrol_ms"], pred)})
                        if len(folds) < 3:
                            continue
                        summary = macro_summary(folds)
                        # Practical score drives toward zero while penalizing low coverage.
                        coverage = float(len(filtered) / max(1, len(raw)))
                        score = (
                            summary.get("macro_mean_abs_correction_pct", 999.0)
                            + 0.20 * summary.get("macro_p90_abs_correction_pct", 999.0)
                            + 2.0 * max(0.0, 0.25 - coverage)
                        )
                        results.append({
                            "window": window,
                            "map_range": map_thr,
                            "rpm_range": rpm_thr,
                            "require_fresh": require_fresh,
                            "max_dt_ms": max_dt,
                            "model": model_name,
                            "rows": int(len(filtered)),
                            "sessions": len(usable_sessions),
                            "coverage": coverage,
                            "score": score,
                            "summary": summary,
                            "folds": folds,
                        })
    results.sort(key=lambda r: (r["score"], r["summary"].get("macro_mean_abs_correction_pct", 999)))
    payload = {
        "window": window,
        "raw_petrol_rows": int(len(raw)),
        "candidate_count": len(results),
        "top": results[:50],
        "best": results[0] if results else None,
    }
    output.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    best = payload["best"]
    print(json.dumps({
        "window": window,
        "candidate_count": len(results),
        "best": None if best is None else {
            "filter": {k: best[k] for k in ["map_range","rpm_range","require_fresh","max_dt_ms","model","rows","sessions","coverage"]},
            "summary": best["summary"],
        },
    }))


def calibration_sweep(cache: Path, output: Path):
    from sklearn.linear_model import LinearRegression, Ridge
    from sklearn.pipeline import make_pipeline
    from sklearn.preprocessing import SplineTransformer

    raw = petrol_rows(load_cache(cache), clean=True).sort_values(["session", "recorded_at_ms", "sequence"]).copy()
    sessions = sorted(raw["session"].unique())
    baseline_specs = [(6,2),(8,2),(12,2),(20,2),(8,3),(12,3)]
    calib_sizes = [10, 20, 50, 100, 200, 500, 1000]
    modes = ["offset", "gain", "affine"]
    results = []

    for knots, degree in baseline_specs:
        for held in sessions:
            train = raw[raw["session"] != held].copy()
            test = raw[raw["session"] == held].copy()
            if len(train) < 500 or len(test) < 100:
                continue
            base = make_pipeline(SplineTransformer(n_knots=knots, degree=degree, include_bias=False), Ridge(alpha=1e-4))
            base.fit(train[["map_bar"]], train["petrol_ms"])
            base_pred = base.predict(test[["map_bar"]])
            zero_shot = metrics(test["petrol_ms"], base_pred)
            results.append({
                "baseline": f"spline_k{knots}_d{degree}",
                "held": held,
                "calib_n": 0,
                "mode": "zero_shot",
                "calib_fraction": 0.0,
                **zero_shot,
            })

            for n in calib_sizes:
                if len(test) <= n + 30:
                    continue
                calib = test.iloc[:n]
                eval_df = test.iloc[n:]
                calib_base = base.predict(calib[["map_bar"]])
                eval_base = base.predict(eval_df[["map_bar"]])
                y_cal = calib["petrol_ms"].to_numpy()

                for mode in modes:
                    if mode == "offset":
                        b = float(np.median(y_cal - calib_base))
                        pred = eval_base + b
                        params = {"offset_ms": b}
                    elif mode == "gain":
                        ratios = y_cal / np.maximum(calib_base, 1e-6)
                        a = float(np.median(ratios))
                        pred = eval_base * a
                        params = {"gain": a}
                    else:
                        reg = LinearRegression().fit(calib_base.reshape(-1,1), y_cal)
                        a = float(reg.coef_[0]); b = float(reg.intercept_)
                        # keep calibration physically conservative
                        a = float(np.clip(a, 0.75, 1.25))
                        b = float(np.clip(b, -1.5, 1.5))
                        pred = eval_base * a + b
                        params = {"gain": a, "offset_ms": b}
                    results.append({
                        "baseline": f"spline_k{knots}_d{degree}",
                        "held": held,
                        "calib_n": n,
                        "mode": mode,
                        "calib_fraction": float(n / len(test)),
                        "params": params,
                        **metrics(eval_df["petrol_ms"], pred),
                    })

    # Aggregate by baseline/calib/mode over held-out sessions.
    groups = {}
    for row in results:
        key = (row["baseline"], row["calib_n"], row["mode"])
        groups.setdefault(key, []).append(row)
    summaries = []
    for (baseline, n, mode), folds in groups.items():
        if len(folds) < 3:
            continue
        summary = macro_summary(folds)
        summaries.append({
            "baseline": baseline,
            "calib_n": n,
            "mode": mode,
            "folds": len(folds),
            "summary": summary,
            "per_session": folds,
        })
    summaries.sort(key=lambda r: (
        r["summary"].get("macro_mean_abs_correction_pct", 999),
        r["calib_n"],
    ))
    payload = {"rows": int(len(raw)), "sessions": len(sessions), "summaries": summaries, "best": summaries[0] if summaries else None}
    output.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps({"rows": len(raw), "sessions": len(sessions), "best": payload["best"]}))


def blocked_session_sweep(cache: Path, output: Path):
    from sklearn.linear_model import Ridge
    from sklearn.pipeline import make_pipeline
    from sklearn.preprocessing import PolynomialFeatures, SplineTransformer, StandardScaler

    df = petrol_rows(load_cache(cache), clean=True).sort_values(["session","recorded_at_ms","sequence"]).copy()
    model_specs = [
        ("spline_k8_d2", ["map_bar"], make_pipeline(SplineTransformer(n_knots=8,degree=2,include_bias=False),Ridge(alpha=1e-4))),
        ("spline_k20_d2", ["map_bar"], make_pipeline(SplineTransformer(n_knots=20,degree=2,include_bias=False),Ridge(alpha=1e-4))),
        ("map_rpm_poly2", ["map_bar","rpm"], make_pipeline(PolynomialFeatures(2,include_bias=False),StandardScaler(),Ridge(alpha=0.1))),
    ]
    fractions = [0.2,0.3,0.4,0.5,0.6,0.7,0.8]
    results=[]
    for session,g in df.groupby("session",sort=False):
        g=g.sort_values(["recorded_at_ms","sequence"])
        if len(g)<150: continue
        for frac in fractions:
            cut=max(50,int(len(g)*frac))
            if len(g)-cut<30: continue
            train=g.iloc[:cut]; test=g.iloc[cut:]
            for name,features,model in model_specs:
                try:
                    model.fit(train[features],train["petrol_ms"])
                    pred=model.predict(test[features])
                    results.append({"session":session,"train_fraction":frac,"model":name,"train_n":len(train),"test_n":len(test),**metrics(test["petrol_ms"],pred)})
                except Exception as exc:
                    results.append({"session":session,"train_fraction":frac,"model":name,"error":repr(exc),"n":0})
    groups={}
    for row in results:
        if row.get("n",0)<=0: continue
        key=(row["train_fraction"],row["model"])
        groups.setdefault(key,[]).append(row)
    summaries=[]
    for (frac,model),folds in groups.items():
        summaries.append({"train_fraction":frac,"model":model,"summary":macro_summary(folds),"per_session":folds})
    summaries.sort(key=lambda r:r["summary"].get("macro_mean_abs_correction_pct",999))
    payload={"rows":len(df),"sessions":int(df.session.nunique()),"summaries":summaries,"best":summaries[0] if summaries else None}
    output.write_text(json.dumps(payload,indent=2,ensure_ascii=False),encoding="utf-8")
    print(json.dumps({"best":payload["best"]}))

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

    p = sub.add_parser("source-profile")
    p.add_argument("--root", type=Path, required=True)
    p.add_argument("--source", required=True)
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--cache-output", type=Path)

    p = sub.add_parser("build-cache-selected")
    p.add_argument("--root", type=Path, required=True)
    p.add_argument("--source", action="append", required=True)
    p.add_argument("--cache", type=Path, required=True)
    p.add_argument("--manifest", type=Path, required=True)

    p = sub.add_parser("global-family")
    p.add_argument("--cache", type=Path, default=DEFAULT_CACHE)
    p.add_argument("--family", choices=["map", "rpm", "dynamic", "trees", "robust"], required=True)
    p.add_argument("--output", type=Path, required=True)

    p = sub.add_parser("falsification")
    p.add_argument("--cache", type=Path, default=DEFAULT_CACHE)
    p.add_argument("--output", type=Path, required=True)

    p = sub.add_parser("stable-sweep")
    p.add_argument("--cache", type=Path, default=DEFAULT_CACHE)
    p.add_argument("--window", type=int, choices=[3,5,7,9], required=True)
    p.add_argument("--output", type=Path, required=True)

    p = sub.add_parser("calibration-sweep")
    p.add_argument("--cache", type=Path, default=DEFAULT_CACHE)
    p.add_argument("--output", type=Path, required=True)

    p = sub.add_parser("blocked-session-sweep")
    p.add_argument("--cache", type=Path, default=DEFAULT_CACHE)
    p.add_argument("--output", type=Path, required=True)

    args = parser.parse_args()
    if args.cmd == "build-cache":
        build_cache(args.root, args.cache, args.manifest)
    elif args.cmd == "session-profile":
        session_profile(args.cache, args.session, args.output)
    elif args.cmd == "source-profile":
        source_profile(args.root, args.source, args.output, args.cache_output)
    elif args.cmd == "build-cache-selected":
        build_cache_selected(args.root, args.source, args.cache, args.manifest)
    elif args.cmd == "global-family":
        global_family(args.cache, args.family, args.output)
    elif args.cmd == "falsification":
        falsification(args.cache, args.output)
    elif args.cmd == "stable-sweep":
        stable_sweep(args.cache, args.window, args.output)
    elif args.cmd == "calibration-sweep":
        calibration_sweep(args.cache, args.output)
    elif args.cmd == "blocked-session-sweep":
        blocked_session_sweep(args.cache, args.output)


if __name__ == "__main__":
    main()
