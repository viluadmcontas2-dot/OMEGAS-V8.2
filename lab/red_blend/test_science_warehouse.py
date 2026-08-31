import json
import sqlite3
import zipfile
from pathlib import Path

import pytest

from warehouse_cache import (
    PARSER_VERSION,
    create_cache,
    event_digest,
    ingest_session_zip,
    session_key,
    source_sha256,
)


def write_session_zip(path: Path, session_id: str, events, app_version='8.0.0-test-debug'):
    manifest = {
        'format': 'omegas-session-log-v1',
        'schemaVersion': 2,
        'sessionId': session_id,
        'createdAtMs': 1000,
        'createdAtUtc': '2026-08-30T00:00:01.000Z',
        'metadata': {'appVersion': app_version},
    }
    with zipfile.ZipFile(path, 'w', compression=zipfile.ZIP_DEFLATED) as z:
        z.writestr('manifest.json', json.dumps(manifest))
        payload = ''.join(json.dumps(e, separators=(',', ':')) + '\n' for e in events)
        z.writestr('events_0001.jsonl', payload)


def telemetry(seq, rpm=1000, map_bar=0.5, petrol_ms=5.5, fuel='GNV'):
    return {
        'format': 'omegas-session-log-v1',
        'sequence': seq,
        'recordedAtMs': 1000 + seq,
        'recordedAtUtc': f'2026-08-30T00:00:{seq:02d}.000Z',
        'type': 'telemetry',
        'source': 'mp48',
        'data': {
            'rpm': rpm,
            'load_bar': map_bar,
            'petrol_ms': petrol_ms,
            'gas_ms_diagnostic': petrol_ms * 1.2,
            'fuel': fuel,
            'water_c': 80,
            'gas_c': 30,
            'gas_pressure_abs_bar': 1.8,
            'pressure_diff_bar': 1.3,
            'plausible': True,
        },
    }


def test_stable_identities_and_schema(tmp_path):
    db = tmp_path / 'cache.sqlite'
    conn = create_cache(db, PARSER_VERSION)
    tables = {r[0] for r in conn.execute("select name from sqlite_master where type='table'")}
    assert {'source_blob', 'logical_session', 'telemetry', 'map_k_batch', 'k_factor_batch', 'autocal_snapshot', 'rpm_map_summary'} <= tables
    assert session_key('raw-session') == session_key('raw-session')
    assert 'raw-session' not in session_key('raw-session')
    event = telemetry(1)
    assert event_digest(event) == event_digest(json.loads(json.dumps(event)))


def test_session_ingestion_is_idempotent_across_duplicate_exports(tmp_path):
    a = tmp_path / 'a.zip'
    b = tmp_path / 'b.zip'
    events = [telemetry(1), telemetry(2)]
    write_session_zip(a, 'session-x', events)
    write_session_zip(b, 'session-x', events)
    conn = create_cache(tmp_path / 'cache.sqlite', PARSER_VERSION)
    s1 = ingest_session_zip(conn, a)
    s2 = ingest_session_zip(conn, b)
    assert s1.telemetry_inserted == 2
    assert s2.telemetry_inserted == 0
    assert conn.execute('select count(*) from telemetry').fetchone()[0] == 2
    assert conn.execute('select count(*) from logical_session').fetchone()[0] == 1
    assert conn.execute('select count(*) from session_source').fetchone()[0] == 2


def test_same_sequence_different_content_is_recorded_as_conflict(tmp_path):
    a = tmp_path / 'a.zip'
    b = tmp_path / 'b.zip'
    write_session_zip(a, 'session-x', [telemetry(1, petrol_ms=5.0)])
    write_session_zip(b, 'session-x', [telemetry(1, petrol_ms=7.0)])
    conn = create_cache(tmp_path / 'cache.sqlite', PARSER_VERSION)
    ingest_session_zip(conn, a)
    ingest_session_zip(conn, b)
    assert conn.execute('select count(*) from event_conflict').fetchone()[0] == 1
    assert conn.execute('select count(*) from telemetry').fetchone()[0] == 2


def test_malformed_line_is_preserved_without_guessing(tmp_path):
    zpath = tmp_path / 'bad.zip'
    manifest = {'sessionId':'session-bad','createdAtMs':1,'createdAtUtc':'2026-08-30T00:00:00Z','metadata':{'appVersion':'x'}}
    with zipfile.ZipFile(zpath, 'w') as z:
        z.writestr('manifest.json', json.dumps(manifest))
        z.writestr('events_0001.jsonl', json.dumps(telemetry(1))+'\n'+ '{"broken":')
    conn = create_cache(tmp_path / 'cache.sqlite', PARSER_VERSION)
    stats = ingest_session_zip(conn, zpath)
    assert stats.malformed_records == 1
    row = conn.execute('select line_number, line_sha256, error_class from malformed_record').fetchone()
    assert row[0] == 2
    assert len(row[1]) == 64
    assert row[2] == 'JSONDecodeError'
    assert conn.execute('select count(*) from telemetry').fetchone()[0] == 1


def test_map_curve_and_autocal_are_separate_dimensions(tmp_path):
    map_event = {
        'sequence': 1, 'recordedAtMs': 10, 'recordedAtUtc': '2026-08-30T00:00:00Z',
        'type': 'k_batch_confirmed', 'source':'map_k',
        'data': {'adjustmentId':'ADJ-1','oldHash':'a','newHash':'b','humanConfirmed':True,'readbackValid':True,
                 'confirmedEvents':[{'row':4,'column':0,'rpm':850,'petrolMs':4.5,'before':144,'after':154,'readback':154,'confirmed':True}]}
    }
    curve_event = {
        'sequence': 2, 'recordedAtMs': 20, 'recordedAtUtc': '2026-08-30T00:00:01Z',
        'type':'k_factor_batch_confirmed','source':'k_factor',
        'data': {'adjustmentId':'KF-1','oldHash':'c','newHash':'d','humanConfirmed':True,'readbackValid':True,
                 'confirmedEvents':[{'index':8,'petrolMs':4.5,'beforeRaw':16000,'afterRaw':17000,'beforeFactor':0.9765625,'afterFactor':1.03759765625,'confirmed':True}]}
    }
    auto_event = {
        'sequence':3,'recordedAtMs':30,'recordedAtUtc':'2026-08-30T00:00:02Z','type':'autocal_native_snapshot','source':'autocal',
        'data': {'snapshotHash':'f'*64,'moduleVersion':100,'partial':False,'temporalCoherent':True,
                 'fields':[{'key':'MNFLD_PRESS_THD','elementCount':18,'status':'VALID','physicalValues':[i/10 for i in range(18)],'rawPayloadHex':'00'},
                           {'key':'MUL_ACT','elementCount':30,'status':'VALID','physicalValues':[1.0]*30,'rawPayloadHex':'00'}]}
    }
    zpath=tmp_path/'cal.zip'; write_session_zip(zpath,'session-cal',[map_event,curve_event,auto_event])
    conn=create_cache(tmp_path/'cache.sqlite',PARSER_VERSION)
    ingest_session_zip(conn,zpath)
    assert conn.execute('select count(*) from map_k_batch').fetchone()[0] == 1
    assert conn.execute('select count(*) from map_k_cell_change').fetchone()[0] == 1
    assert conn.execute('select count(*) from k_factor_batch').fetchone()[0] == 1
    assert conn.execute('select count(*) from k_factor_point_change').fetchone()[0] == 1
    fields=dict(conn.execute('select field_key, element_count from autocal_field'))
    assert fields['MNFLD_PRESS_THD'] == 18
    assert fields['MUL_ACT'] == 30


def test_source_sha256_is_content_based(tmp_path):
    a=tmp_path/'a.bin'; b=tmp_path/'b.bin'; a.write_bytes(b'abc'); b.write_bytes(b'abc')
    assert source_sha256(a) == source_sha256(b)


def test_usb_raw_is_summarized_not_materialized(tmp_path):
    zpath = tmp_path / 'usb.zip'
    events = [
        {'sequence': i, 'recordedAtMs': i, 'type': 'usb_raw', 'source': 'usb', 'data': {'hex': 'AA'*64}}
        for i in range(1, 1001)
    ]
    write_session_zip(zpath, 'session-usb', events)
    conn = create_cache(tmp_path / 'cache.sqlite', PARSER_VERSION)
    ingest_session_zip(conn, zpath)
    assert conn.execute("select count(*) from event_seen").fetchone()[0] == 0
    row = conn.execute("select raw_count, min_sequence, max_sequence from source_event_summary where event_type='usb_raw'").fetchone()
    assert tuple(row) == (1000, 1, 1000)


def test_non_usb_non_telemetry_event_is_kept_as_canonical_fact(tmp_path):
    zpath = tmp_path / 'facts.zip'
    event = {'sequence': 9, 'recordedAtMs': 9, 'type':'full_snapshot', 'source':'runtime', 'data': {'x': 1, 'nested': {'b':2}}}
    write_session_zip(zpath, 'session-fact', [event])
    conn = create_cache(tmp_path / 'cache.sqlite', PARSER_VERSION)
    ingest_session_zip(conn, zpath)
    row = conn.execute("select event_type, event_json from event_fact").fetchone()
    assert row[0] == 'full_snapshot'
    assert json.loads(row[1]) == event


def test_exact_blob_reuses_parsed_source_but_preserves_occurrence(tmp_path):
    a = tmp_path / 'a.zip'; b = tmp_path / 'b.zip'
    events = [telemetry(1), telemetry(2)]
    write_session_zip(a, 'session-cache-hit', events)
    b.write_bytes(a.read_bytes())
    conn = create_cache(tmp_path / 'cache.sqlite', PARSER_VERSION)
    first = ingest_session_zip(conn, a)
    second = ingest_session_zip(conn, b)
    assert first.cache_hit is False
    assert second.cache_hit is True
    assert second.telemetry_inserted == 0
    assert conn.execute('select count(*) from source_blob').fetchone()[0] == 1
    assert conn.execute('select count(*) from source_occurrence').fetchone()[0] == 2
    assert conn.execute('select count(*) from session_source').fetchone()[0] == 2


def test_telemetry_keeps_full_semantic_payload_while_indexing_common_fields(tmp_path):
    zpath = tmp_path / 'telemetry-rich.zip'
    event = telemetry(1)
    event['data'].update({
        'petrol_2_raw': 4181,
        'petrol_2_ms_diagnostic': 10.70336,
        'gas_2_raw': 0,
        'unknown_raw_19': 7,
        'telemetry_scale_schema': 'mp48-progbase-v2',
        'captured_elapsed_ms': 123456,
        'k_interpolated': 154.5,
        'sample': {'cell_key':'7:1','quality':0.8},
    })
    write_session_zip(zpath, 'session-rich', [event])
    conn = create_cache(tmp_path / 'cache.sqlite', PARSER_VERSION)
    ingest_session_zip(conn, zpath)
    row = conn.execute('select data_json from telemetry').fetchone()
    payload = json.loads(row[0])
    assert payload['petrol_2_raw'] == 4181
    assert payload['unknown_raw_19'] == 7
    assert payload['k_interpolated'] == 154.5
    assert payload['sample']['cell_key'] == '7:1'
