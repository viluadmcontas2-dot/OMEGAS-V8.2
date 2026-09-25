from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAP_WRITER = (ROOT / "app/src/main/java/com/omegas/prohub/calibration/KWriteManager.kt").read_text()
CURVE_WRITER = (ROOT / "app/src/main/java/com/omegas/prohub/calibration/KFactorManager.kt").read_text()


def test_map_writer_uses_fast_ack_then_final_row_readback():
    assert "escrita ACK MAP_K" in MAP_WRITER
    assert "ECU_WRITE_ACK_PENDING_FINAL_READBACK" in MAP_WRITER
    assert "BATCH_VERIFYING_ROWS" in MAP_WRITER
    assert "confirmação final K[$row]" in MAP_WRITER
    assert "escrita + readback MAP_K" not in MAP_WRITER
    assert 'readRow(unit, row, "readback K[$row,$column]")' not in MAP_WRITER
    assert "waitTimeoutMs = 1_200L" in MAP_WRITER


def test_curve_writer_uses_fast_ack_then_single_final_curve_readback():
    assert "escrita ACK K factor" in CURVE_WRITER
    assert "VERIFYING_FINAL" in CURVE_WRITER
    assert "confirmação final K factor" in CURVE_WRITER
    assert "escrita + readback K factor" not in CURVE_WRITER
    assert 'readRawPoints(unit, KFactorProtocol.readFactors(), "readback K factor[$index]")' not in CURVE_WRITER
    assert "waitTimeoutMs = 1_200L" in CURVE_WRITER


def test_writers_do_not_run_full_readback_inside_each_write_loop():
    map_loop = re.search(r'cells\.length\(\).*?BATCH_VERIFYING_ROWS', MAP_WRITER, re.S).group(0)
    curve_loop = re.search(r'repeat\(points\.length\(\)\).*?VERIFYING_FINAL', CURVE_WRITER, re.S).group(0)
    assert "readback K[$row,$column]" not in map_loop
    assert "readback K factor[$index]" not in curve_loop
