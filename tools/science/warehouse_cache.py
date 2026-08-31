from __future__ import annotations

import hashlib
import json
import sqlite3
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any

PARSER_VERSION = "omegas-science-warehouse-v1"


def _canonical_json(value: Any) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")


def event_digest(event: dict) -> str:
    return hashlib.sha256(_canonical_json(event)).hexdigest()


def session_key(raw_session_id: str) -> str:
    digest = hashlib.sha256(raw_session_id.encode("utf-8")).hexdigest()[:20].upper()
    return f"SES-{digest}"


def source_sha256(path: Path) -> str:
    h = hashlib.sha256()
    with Path(path).open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


SCHEMA = """
PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS source_blob(
    source_sha256 TEXT PRIMARY KEY,
    bytes INTEGER NOT NULL,
    parser_version TEXT NOT NULL,
    source_class TEXT NOT NULL DEFAULT 'UNKNOWN',
    parsed INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS source_session(
    source_sha256 TEXT PRIMARY KEY REFERENCES source_blob(source_sha256),
    session_key TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS source_relation(
    parent_sha256 TEXT NOT NULL REFERENCES source_blob(source_sha256),
    child_sha256 TEXT NOT NULL REFERENCES source_blob(source_sha256),
    relation TEXT NOT NULL,
    PRIMARY KEY(parent_sha256, child_sha256, relation)
);
CREATE TABLE IF NOT EXISTS json_artifact(
    source_sha256 TEXT PRIMARY KEY REFERENCES source_blob(source_sha256),
    source_class TEXT NOT NULL,
    format TEXT,
    document_json TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS source_occurrence(
    occurrence_key TEXT PRIMARY KEY,
    source_sha256 TEXT NOT NULL REFERENCES source_blob(source_sha256),
    source_alias TEXT
);
CREATE TABLE IF NOT EXISTS logical_session(
    session_key TEXT PRIMARY KEY,
    created_at_ms INTEGER,
    created_at_utc TEXT,
    app_version TEXT
);
CREATE TABLE IF NOT EXISTS session_source(
    session_key TEXT NOT NULL REFERENCES logical_session(session_key),
    occurrence_key TEXT NOT NULL REFERENCES source_occurrence(occurrence_key),
    PRIMARY KEY(session_key, occurrence_key)
);
CREATE TABLE IF NOT EXISTS event_seen(
    session_key TEXT NOT NULL,
    sequence INTEGER NOT NULL,
    event_sha256 TEXT NOT NULL,
    event_type TEXT,
    PRIMARY KEY(session_key, sequence, event_sha256)
);
CREATE TABLE IF NOT EXISTS event_conflict(
    session_key TEXT NOT NULL,
    sequence INTEGER NOT NULL,
    first_sha256 TEXT NOT NULL,
    conflicting_sha256 TEXT NOT NULL,
    PRIMARY KEY(session_key, sequence, first_sha256, conflicting_sha256)
);
CREATE TABLE IF NOT EXISTS malformed_record(
    occurrence_key TEXT NOT NULL,
    segment TEXT NOT NULL,
    line_number INTEGER NOT NULL,
    line_sha256 TEXT NOT NULL,
    error_class TEXT NOT NULL,
    PRIMARY KEY(occurrence_key, segment, line_number, line_sha256)
);
CREATE TABLE IF NOT EXISTS opaque_event(
    session_key TEXT NOT NULL,
    sequence INTEGER NOT NULL,
    event_sha256 TEXT NOT NULL,
    event_type TEXT,
    PRIMARY KEY(session_key, sequence, event_sha256)
);
CREATE TABLE IF NOT EXISTS event_fact(
    session_key TEXT NOT NULL,
    sequence INTEGER NOT NULL,
    event_sha256 TEXT NOT NULL,
    event_type TEXT,
    recorded_at_ms INTEGER,
    recorded_at_utc TEXT,
    event_json TEXT NOT NULL,
    PRIMARY KEY(session_key, sequence, event_sha256)
);
CREATE TABLE IF NOT EXISTS source_event_summary(
    source_sha256 TEXT NOT NULL REFERENCES source_blob(source_sha256),
    session_key TEXT NOT NULL,
    event_type TEXT NOT NULL,
    raw_count INTEGER NOT NULL,
    min_sequence INTEGER,
    max_sequence INTEGER,
    PRIMARY KEY(source_sha256, session_key, event_type)
);
CREATE TABLE IF NOT EXISTS telemetry(
    session_key TEXT NOT NULL,
    sequence INTEGER NOT NULL,
    event_sha256 TEXT NOT NULL,
    recorded_at_ms INTEGER,
    recorded_at_utc TEXT,
    fuel TEXT,
    rpm REAL,
    map_bar REAL,
    petrol_ms REAL,
    gas_ms REAL,
    water_c REAL,
    gas_c REAL,
    gas_pressure_abs_bar REAL,
    pressure_diff_bar REAL,
    plausible INTEGER,
    data_json TEXT NOT NULL,
    PRIMARY KEY(session_key, sequence, event_sha256)
);
CREATE TABLE IF NOT EXISTS map_k_batch(
    adjustment_key TEXT PRIMARY KEY,
    raw_adjustment_sha256 TEXT NOT NULL,
    session_key TEXT NOT NULL,
    recorded_at_ms INTEGER,
    old_hash TEXT,
    new_hash TEXT,
    confirmed INTEGER NOT NULL,
    readback_valid INTEGER
);
CREATE TABLE IF NOT EXISTS map_k_cell_change(
    adjustment_key TEXT NOT NULL REFERENCES map_k_batch(adjustment_key),
    row_index INTEGER NOT NULL,
    column_index INTEGER NOT NULL,
    rpm_axis REAL,
    petrol_ms_axis REAL,
    before_k INTEGER,
    after_k INTEGER,
    readback_k INTEGER,
    confirmed INTEGER NOT NULL,
    PRIMARY KEY(adjustment_key, row_index, column_index)
);
CREATE TABLE IF NOT EXISTS k_factor_batch(
    adjustment_key TEXT PRIMARY KEY,
    raw_adjustment_sha256 TEXT NOT NULL,
    session_key TEXT NOT NULL,
    recorded_at_ms INTEGER,
    old_hash TEXT,
    new_hash TEXT,
    confirmed INTEGER NOT NULL,
    readback_valid INTEGER
);
CREATE TABLE IF NOT EXISTS k_factor_point_change(
    adjustment_key TEXT NOT NULL REFERENCES k_factor_batch(adjustment_key),
    point_index INTEGER NOT NULL,
    petrol_ms REAL,
    before_raw INTEGER,
    after_raw INTEGER,
    before_factor REAL,
    after_factor REAL,
    confirmed INTEGER NOT NULL,
    PRIMARY KEY(adjustment_key, point_index)
);
CREATE TABLE IF NOT EXISTS autocal_snapshot(
    snapshot_key TEXT PRIMARY KEY,
    session_key TEXT NOT NULL,
    recorded_at_ms INTEGER,
    module_version INTEGER,
    partial INTEGER,
    temporal_coherent INTEGER
);
CREATE TABLE IF NOT EXISTS autocal_field(
    snapshot_key TEXT NOT NULL REFERENCES autocal_snapshot(snapshot_key),
    field_key TEXT NOT NULL,
    element_count INTEGER,
    valid INTEGER,
    failure_reason TEXT,
    physical_values_json TEXT,
    raw_sha256 TEXT,
    PRIMARY KEY(snapshot_key, field_key)
);
CREATE TABLE IF NOT EXISTS portmon_capture_summary(
    capture_sha256 TEXT PRIMARY KEY,
    transaction_count INTEGER,
    write_attempt_count INTEGER,
    metadata_json TEXT
);
CREATE TABLE IF NOT EXISTS rpm_map_summary(
    session_key TEXT NOT NULL,
    fuel TEXT NOT NULL,
    rpm_bin INTEGER NOT NULL,
    map_bin_mbar INTEGER NOT NULL,
    sample_count INTEGER NOT NULL,
    mean_petrol_ms REAL,
    mean_map_bar REAL,
    mean_rpm REAL,
    PRIMARY KEY(session_key, fuel, rpm_bin, map_bin_mbar)
);
CREATE TABLE IF NOT EXISTS analysis_result(
    result_key TEXT PRIMARY KEY,
    analysis_type TEXT NOT NULL,
    metrics_json TEXT NOT NULL
);
"""


def create_cache(path: Path, parser_version: str) -> sqlite3.Connection:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(path)
    conn.executescript(SCHEMA)
    conn.execute("INSERT OR REPLACE INTO meta(key,value) VALUES('parser_version',?)", (parser_version,))
    conn.commit()
    return conn


def _adjustment_key(raw: str, prefix: str) -> tuple[str, str]:
    digest = hashlib.sha256(raw.encode("utf-8")).hexdigest()
    return f"{prefix}-{digest[:20].upper()}", digest


def _boolint(v: Any) -> int | None:
    if v is None:
        return None
    return 1 if bool(v) else 0


@dataclass
class IngestStats:
    cache_hit: bool = False
    telemetry_inserted: int = 0
    malformed_records: int = 0
    opaque_events: int = 0
    map_k_batches: int = 0
    k_factor_batches: int = 0
    autocal_snapshots: int = 0


def _insert_event_identity(conn: sqlite3.Connection, skey: str, seq: int, ev_sha: str, ev_type: str | None) -> bool:
    existing = [r[0] for r in conn.execute(
        "SELECT event_sha256 FROM event_seen WHERE session_key=? AND sequence=?",
        (skey, seq),
    )]
    if ev_sha in existing:
        return False
    for old in existing:
        a, b = sorted((old, ev_sha))
        conn.execute(
            "INSERT OR IGNORE INTO event_conflict(session_key,sequence,first_sha256,conflicting_sha256) VALUES(?,?,?,?)",
            (skey, seq, a, b),
        )
    conn.execute(
        "INSERT INTO event_seen(session_key,sequence,event_sha256,event_type) VALUES(?,?,?,?)",
        (skey, seq, ev_sha, ev_type),
    )
    return True


def _ingest_telemetry(conn: sqlite3.Connection, skey: str, event: dict, ev_sha: str) -> bool:
    data = event.get("data") or {}
    cur = conn.execute(
        """INSERT OR IGNORE INTO telemetry(
           session_key,sequence,event_sha256,recorded_at_ms,recorded_at_utc,fuel,rpm,map_bar,petrol_ms,gas_ms,
           water_c,gas_c,gas_pressure_abs_bar,pressure_diff_bar,plausible,data_json)
           VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
        (
            skey, int(event.get("sequence", -1)), ev_sha, event.get("recordedAtMs"), event.get("recordedAtUtc"),
            data.get("fuel"), data.get("rpm"), data.get("load_bar"), data.get("petrol_ms"),
            data.get("gas_ms_diagnostic"), data.get("water_c"), data.get("gas_c"),
            data.get("gas_pressure_abs_bar"), data.get("pressure_diff_bar"), _boolint(data.get("plausible")),
            _canonical_json(data).decode("utf-8"),
        ),
    )
    return cur.rowcount > 0


def _ingest_map_batch(conn: sqlite3.Connection, skey: str, event: dict) -> bool:
    data = event.get("data") or {}
    raw_id = str(data.get("adjustmentId") or "")
    if not raw_id:
        raw_id = event_digest(event)
    akey, raw_digest = _adjustment_key(raw_id, "MAP")
    cur = conn.execute(
        """INSERT OR IGNORE INTO map_k_batch(adjustment_key,raw_adjustment_sha256,session_key,recorded_at_ms,old_hash,new_hash,confirmed,readback_valid)
           VALUES(?,?,?,?,?,?,?,?)""",
        (akey, raw_digest, skey, event.get("recordedAtMs"), data.get("oldHash"), data.get("newHash"),
         _boolint(data.get("humanConfirmed", True)), _boolint(data.get("readbackValid"))),
    )
    for cell in data.get("confirmedEvents") or []:
        conn.execute(
            """INSERT OR IGNORE INTO map_k_cell_change(adjustment_key,row_index,column_index,rpm_axis,petrol_ms_axis,before_k,after_k,readback_k,confirmed)
               VALUES(?,?,?,?,?,?,?,?,?)""",
            (akey, cell.get("row"), cell.get("column"), cell.get("rpm"), cell.get("petrolMs"),
             cell.get("before"), cell.get("after"), cell.get("readback"), _boolint(cell.get("confirmed", False))),
        )
    return cur.rowcount > 0


def _ingest_curve_batch(conn: sqlite3.Connection, skey: str, event: dict) -> bool:
    data = event.get("data") or {}
    raw_id = str(data.get("adjustmentId") or "")
    if not raw_id:
        raw_id = event_digest(event)
    akey, raw_digest = _adjustment_key(raw_id, "CURVE")
    cur = conn.execute(
        """INSERT OR IGNORE INTO k_factor_batch(adjustment_key,raw_adjustment_sha256,session_key,recorded_at_ms,old_hash,new_hash,confirmed,readback_valid)
           VALUES(?,?,?,?,?,?,?,?)""",
        (akey, raw_digest, skey, event.get("recordedAtMs"), data.get("oldHash"), data.get("newHash"),
         _boolint(data.get("humanConfirmed", True)), _boolint(data.get("readbackValid"))),
    )
    for point in data.get("confirmedEvents") or []:
        conn.execute(
            """INSERT OR IGNORE INTO k_factor_point_change(adjustment_key,point_index,petrol_ms,before_raw,after_raw,before_factor,after_factor,confirmed)
               VALUES(?,?,?,?,?,?,?,?)""",
            (akey, point.get("index"), point.get("petrolMs"), point.get("beforeRaw"), point.get("afterRaw"),
             point.get("beforeFactor"), point.get("afterFactor"), _boolint(point.get("confirmed", False))),
        )
    return cur.rowcount > 0


def _ingest_autocal(conn: sqlite3.Connection, skey: str, event: dict) -> bool:
    data = event.get("data") or {}
    raw_hash = str(data.get("snapshotHash") or event_digest(event))
    snapshot = f"AUTO-{hashlib.sha256(raw_hash.encode('utf-8')).hexdigest()[:20].upper()}"
    cur = conn.execute(
        """INSERT OR IGNORE INTO autocal_snapshot(snapshot_key,session_key,recorded_at_ms,module_version,partial,temporal_coherent)
           VALUES(?,?,?,?,?,?)""",
        (snapshot, skey, event.get("recordedAtMs"), data.get("moduleVersion"), _boolint(data.get("partial")),
         _boolint(data.get("temporalCoherent"))),
    )
    for field in data.get("fields") or []:
        raw = str(field.get("rawPayloadHex") or "")
        raw_sha = hashlib.sha256(raw.encode("utf-8")).hexdigest() if raw else None
        status = field.get("status")
        conn.execute(
            """INSERT OR IGNORE INTO autocal_field(snapshot_key,field_key,element_count,valid,failure_reason,physical_values_json,raw_sha256)
               VALUES(?,?,?,?,?,?,?)""",
            (snapshot, field.get("key") or field.get("identity") or "UNKNOWN", field.get("elementCount"),
             None if status is None else (1 if status == "VALID" else 0), field.get("error"),
             json.dumps(field.get("physicalValues"), separators=(",", ":")) if field.get("physicalValues") is not None else None,
             raw_sha),
        )
    return cur.rowcount > 0


def rebuild_rpm_map_summary(
    conn: sqlite3.Connection,
    rpm_bin_width: int = 50,
    map_bin_mbar_width: int = 10,
) -> int:
    if rpm_bin_width <= 0 or map_bin_mbar_width <= 0:
        raise ValueError("bin widths must be positive")
    conn.execute("DELETE FROM rpm_map_summary")
    conn.execute(
        """INSERT INTO rpm_map_summary(
           session_key,fuel,rpm_bin,map_bin_mbar,sample_count,mean_petrol_ms,mean_map_bar,mean_rpm)
           SELECT session_key, fuel,
                  CAST(ROUND(rpm / ?) AS INTEGER) * ?,
                  CAST(ROUND((map_bar * 1000.0) / ?) AS INTEGER) * ?,
                  COUNT(*), AVG(petrol_ms), AVG(map_bar), AVG(rpm)
             FROM telemetry
            WHERE rpm IS NOT NULL AND rpm > 0
              AND map_bar IS NOT NULL AND map_bar > 0
              AND petrol_ms IS NOT NULL AND petrol_ms > 0
              AND fuel IS NOT NULL
              AND (plausible IS NULL OR plausible = 1)
            GROUP BY session_key, fuel,
                     CAST(ROUND(rpm / ?) AS INTEGER) * ?,
                     CAST(ROUND((map_bar * 1000.0) / ?) AS INTEGER) * ?
        """,
        (
            rpm_bin_width, rpm_bin_width, map_bin_mbar_width, map_bin_mbar_width,
            rpm_bin_width, rpm_bin_width, map_bin_mbar_width, map_bin_mbar_width,
        ),
    )
    conn.execute("INSERT OR REPLACE INTO meta(key,value) VALUES('rpm_bin_width',?)", (str(rpm_bin_width),))
    conn.execute("INSERT OR REPLACE INTO meta(key,value) VALUES('map_bin_mbar_width',?)", (str(map_bin_mbar_width),))
    count = conn.execute("SELECT COUNT(*) FROM rpm_map_summary").fetchone()[0]
    conn.commit()
    return int(count)


def ingest_json_artifact(conn: sqlite3.Connection, path: Path, source_class: str) -> bool:
    path = Path(path)
    sha = source_sha256(path)
    if conn.execute("SELECT 1 FROM json_artifact WHERE source_sha256=?", (sha,)).fetchone():
        return False
    payload = json.loads(path.read_text(encoding="utf-8"))
    conn.execute(
        "INSERT OR IGNORE INTO source_blob(source_sha256,bytes,parser_version,source_class,parsed) VALUES(?,?,?,?,1)",
        (sha, path.stat().st_size, PARSER_VERSION, source_class),
    )
    conn.execute(
        "INSERT INTO json_artifact(source_sha256,source_class,format,document_json) VALUES(?,?,?,?)",
        (sha, source_class, payload.get("format") if isinstance(payload, dict) else None, _canonical_json(payload).decode("utf-8")),
    )
    conn.commit()
    return True


def ingest_session_zip(conn: sqlite3.Connection, zip_path: Path, source_alias: str | None = None) -> IngestStats:
    zip_path = Path(zip_path)
    sha = source_sha256(zip_path)
    size = zip_path.stat().st_size
    occurrence_seed = f"{zip_path.resolve()}|{sha}"
    occurrence = "OCC-" + hashlib.sha256(occurrence_seed.encode("utf-8")).hexdigest()[:20].upper()
    stats = IngestStats()

    existing = conn.execute("SELECT parsed FROM source_blob WHERE source_sha256=?", (sha,)).fetchone()
    conn.execute(
        "INSERT OR IGNORE INTO source_blob(source_sha256,bytes,parser_version,source_class,parsed) VALUES(?,?,?,'SESSION_ZIP',0)",
        (sha, size, PARSER_VERSION),
    )
    conn.execute(
        "INSERT OR IGNORE INTO source_occurrence(occurrence_key,source_sha256,source_alias) VALUES(?,?,?)",
        (occurrence, sha, source_alias),
    )

    if existing and existing[0] == 1:
        mapped = conn.execute("SELECT session_key FROM source_session WHERE source_sha256=?", (sha,)).fetchone()
        if mapped:
            conn.execute(
                "INSERT OR IGNORE INTO session_source(session_key,occurrence_key) VALUES(?,?)",
                (mapped[0], occurrence),
            )
        conn.commit()
        stats.cache_hit = True
        return stats

    source_summaries: dict[str, list[int | None]] = {}
    with zipfile.ZipFile(zip_path, "r") as z:
        manifest = json.loads(z.read("manifest.json"))
        raw_session_id = str(manifest.get("sessionId") or sha)
        skey = session_key(raw_session_id)
        meta = manifest.get("metadata") or {}
        conn.execute(
            """INSERT OR IGNORE INTO logical_session(session_key,created_at_ms,created_at_utc,app_version) VALUES(?,?,?,?)""",
            (skey, manifest.get("createdAtMs"), manifest.get("createdAtUtc"), meta.get("appVersion")),
        )
        conn.execute("INSERT OR IGNORE INTO session_source(session_key,occurrence_key) VALUES(?,?)", (skey, occurrence))
        conn.execute("INSERT OR REPLACE INTO source_session(source_sha256,session_key) VALUES(?,?)", (sha, skey))

        segments = sorted(n for n in z.namelist() if n.startswith("events_") and n.endswith(".jsonl"))
        for segment in segments:
            with z.open(segment, "r") as stream:
                for line_number, raw_line in enumerate(stream, 1):
                    raw_line = raw_line.rstrip(b"\r\n")
                    if not raw_line:
                        continue
                    try:
                        event = json.loads(raw_line)
                    except json.JSONDecodeError as exc:
                        lsha = hashlib.sha256(raw_line).hexdigest()
                        conn.execute(
                            """INSERT OR IGNORE INTO malformed_record(occurrence_key,segment,line_number,line_sha256,error_class) VALUES(?,?,?,?,?)""",
                            (occurrence, segment, line_number, lsha, exc.__class__.__name__),
                        )
                        stats.malformed_records += 1
                        continue

                    seq = int(event.get("sequence", -1))
                    ev_type = str(event.get("type") or "UNKNOWN")
                    if ev_type == "usb_raw":
                        summary = source_summaries.setdefault(ev_type, [0, None, None])
                        summary[0] = int(summary[0] or 0) + 1
                        summary[1] = seq if summary[1] is None else min(int(summary[1]), seq)
                        summary[2] = seq if summary[2] is None else max(int(summary[2]), seq)
                        continue

                    ev_sha = event_digest(event)
                    if not _insert_event_identity(conn, skey, seq, ev_sha, ev_type):
                        continue
                    if ev_type != "telemetry":
                        conn.execute(
                            """INSERT OR IGNORE INTO event_fact(session_key,sequence,event_sha256,event_type,recorded_at_ms,recorded_at_utc,event_json)
                               VALUES(?,?,?,?,?,?,?)""",
                            (
                                skey, seq, ev_sha, ev_type, event.get("recordedAtMs"), event.get("recordedAtUtc"),
                                _canonical_json(event).decode("utf-8"),
                            ),
                        )
                    if ev_type == "telemetry":
                        if _ingest_telemetry(conn, skey, event, ev_sha):
                            stats.telemetry_inserted += 1
                    elif ev_type == "k_batch_confirmed":
                        if _ingest_map_batch(conn, skey, event):
                            stats.map_k_batches += 1
                    elif ev_type == "k_factor_batch_confirmed":
                        if _ingest_curve_batch(conn, skey, event):
                            stats.k_factor_batches += 1
                    elif ev_type == "autocal_native_snapshot":
                        if _ingest_autocal(conn, skey, event):
                            stats.autocal_snapshots += 1
                    else:
                        conn.execute(
                            "INSERT OR IGNORE INTO opaque_event(session_key,sequence,event_sha256,event_type) VALUES(?,?,?,?)",
                            (skey, seq, ev_sha, ev_type),
                        )
                        stats.opaque_events += 1

        for ev_type, (count, min_seq, max_seq) in source_summaries.items():
            conn.execute(
                """INSERT OR REPLACE INTO source_event_summary(source_sha256,session_key,event_type,raw_count,min_sequence,max_sequence)
                   VALUES(?,?,?,?,?,?)""",
                (sha, skey, ev_type, count, min_seq, max_seq),
            )

    conn.execute("UPDATE source_blob SET parsed=1 WHERE source_sha256=?", (sha,))
    conn.commit()
    return stats
