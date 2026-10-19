from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAP_WRITER = (ROOT / "app/src/main/java/com/omegas/prohub/calibration/KWriteManager.kt").read_text()
CURVE_WRITER = (ROOT / "app/src/main/java/com/omegas/prohub/calibration/KFactorManager.kt").read_text()


def test_map_writer_uses_fast_ack_then_final_row_readback():
    # Só estrutura (rótulos de log mudam): verificação final por linha, fora do laço de escrita.
    # Gravar é instantâneo: "Gravado" no ACK; a leitura de volta (1 por linha) roda em segundo plano, fora do laço de escrita.
    assert "BATCH_CONFIRMED" in MAP_WRITER
    assert "conferência em segundo plano Mapa K" in MAP_WRITER
    assert 'readRow(unit, row, "conferência K[$row]")' in MAP_WRITER
    assert "escrita + readback MAP_K" not in MAP_WRITER
    assert 'readRow(unit, row, "readback K[$row,$column]")' not in MAP_WRITER
    assert "waitTimeoutMs = 8_000L" in MAP_WRITER  # espera a vez na porta (conferência anterior em segundo plano); nunca falha por porta ocupada


def test_curve_writer_uses_fast_ack_then_single_final_curve_readback():
    assert "BATCH_CONFIRMED" in CURVE_WRITER  # "Gravado" no ACK
    assert "conferência em segundo plano Curva K" in CURVE_WRITER  # leitura do vetor inteiro, 1 vez, em segundo plano
    assert "escrita + readback K factor" not in CURVE_WRITER
    assert 'readRawPoints(unit, KFactorProtocol.readFactors(), "readback K factor[$index]")' not in CURVE_WRITER
    assert "waitTimeoutMs = 8_000L" in CURVE_WRITER  # idem na Curva K


def test_writers_do_not_run_full_readback_inside_each_write_loop():
    map_loop = re.search(r'cells\.length\(\).*?BATCH_CONFIRMED', MAP_WRITER, re.S).group(0)
    curve_loop = re.search(r'repeat\(points\.length\(\)\).*?BATCH_CONFIRMED', CURVE_WRITER, re.S).group(0)
    assert "readback K[$row,$column]" not in map_loop
    assert "readback K factor[$index]" not in curve_loop


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
    print("WRITER_FAST_ACK_CONTRACT=PASS")
