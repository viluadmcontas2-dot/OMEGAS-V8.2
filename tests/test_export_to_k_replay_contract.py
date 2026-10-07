import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("export_to_k_replay", ROOT / "tools/omegas/export_to_k_replay.py")
replay = importlib.util.module_from_spec(spec)
spec.loader.exec_module(replay)

AXIS = [0.5 * k for k in range(1, 21)] + [11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 20.0, 22.0]
ROW_MS = [2.0, 2.5, 3.0, 3.5, 4.5, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0]


def flat_map(value):
    return [[value] * 12 for _ in range(13)]


def test_axis_fixture_matches_real_ecu_shape():
    assert len(AXIS) == 30
    assert all(b > a for a, b in zip(AXIS, AXIS[1:]))


def test_neutral_curve_leaves_the_map_unchanged():
    result = replay.export_to_k(flat_map(165), AXIS, [1.0] * 30, ROW_MS)
    assert result["changedCells"] == 0
    assert result["newMap"][:12] == flat_map(165)[:12]
    assert result["skippedRows"] == []


def test_factor_multiplies_all_twelve_columns_and_truncates():
    curve = [0.999] * 30
    result = replay.export_to_k(flat_map(165), AXIS, curve, ROW_MS)
    assert all(cell == 164 for row in result["newMap"][:12] for cell in row)  # trunc(164.835), nunca arredonda para cima


def test_cells_saturate_at_zero_and_255():
    high = replay.export_to_k(flat_map(200), AXIS, [2.0] * 30, ROW_MS)
    assert all(cell == 255 for row in high["newMap"][:12] for cell in row)
    assert high["saturatedHigh"] == 144
    low = replay.export_to_k(flat_map(1), AXIS, [0.4] * 30, ROW_MS)
    assert all(cell == 0 for row in low["newMap"][:12] for cell in row)
    assert low["saturatedLow"] == 144


def test_rows_outside_the_curve_axis_are_not_touched():
    row_ms = [0.2] + ROW_MS[1:11] + [30.0]
    result = replay.export_to_k(flat_map(100), AXIS, [1.5] * 30, row_ms)
    assert result["skippedRows"] == [0, 11]
    assert result["newMap"][0] == [100] * 12
    assert result["newMap"][11] == [100] * 12
    assert result["newMap"][5] == [150] * 12


def test_the_thirteenth_row_is_never_part_of_the_loop():
    kmap = flat_map(100)
    kmap[12] = [137] * 12
    result = replay.export_to_k(kmap, AXIS, [1.5] * 30, ROW_MS)
    assert result["newMap"][12] == [137] * 12


def test_linear_interpolation_between_axis_points():
    curve = [1.0] * 30
    curve[3], curve[4] = 1.0, 2.0  # eixo 2.0 e 2.5 ms
    result = replay.export_to_k(flat_map(100), AXIS, curve, [2.25] + ROW_MS[1:])
    assert abs(result["rows"][0]["factor"] - 1.5) < 1e-12
    assert result["newMap"][0] == [150] * 12


def test_progbase_returns_zero_for_a_repeated_axis_point_but_the_replay_refuses_by_default():
    assert replay.interpolate(1.0, 1.0, 1.0, 1.2, 1.4, strict=False) == 0.0
    try:
        replay.interpolate(1.0, 1.0, 1.0, 1.2, 1.4)
    except ValueError:
        pass
    else:
        raise AssertionError("eixo repetido precisa ser recusado")


def test_row_references_reproduce_the_real_tempi_per_k_bins():
    raw = [781, 977, 1172, 1367, 1758, 2344, 3125, 3906, 4687, 5469, 6250, 7031]
    ms = [replay.row_reference_ms(value, 2560) for value in raw]
    for got, want in zip(ms, ROW_MS):
        assert abs(got - want) < 0.002, (got, want)


def test_export_followed_by_the_final_reset_is_idempotent():
    first = replay.export_to_k(flat_map(165), AXIS, [1.2] * 30, ROW_MS)
    assert first["curveAfter"] == [1.0] * 30
    second = replay.export_to_k(first["newMap"], AXIS, first["curveAfter"], ROW_MS)
    assert second["changedCells"] == 0


if __name__ == "__main__":
    test_axis_fixture_matches_real_ecu_shape()
    test_neutral_curve_leaves_the_map_unchanged()
    test_factor_multiplies_all_twelve_columns_and_truncates()
    test_cells_saturate_at_zero_and_255()
    test_rows_outside_the_curve_axis_are_not_touched()
    test_the_thirteenth_row_is_never_part_of_the_loop()
    test_linear_interpolation_between_axis_points()
    test_progbase_returns_zero_for_a_repeated_axis_point_but_the_replay_refuses_by_default()
    test_row_references_reproduce_the_real_tempi_per_k_bins()
    test_export_followed_by_the_final_reset_is_idempotent()
    print("EXPORT_TO_K_REPLAY_CONTRACT=PASS")
