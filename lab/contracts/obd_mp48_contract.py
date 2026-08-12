"""Contrato executável da Fase 2 para evidência OBD × MP48.

Este módulo é deliberadamente puro: não importa Android, Bluetooth, USB nem
qualquer escritor da ECU. Ele define a fronteira que o motor real deverá
respeitar na Fase 3.
"""

from dataclasses import dataclass
from enum import Enum
from typing import Optional


class Rejection(str, Enum):
    NO_MP48 = "REJECTED_NO_MP48"
    NO_OBD = "REJECTED_NO_OBD"
    TIME_SKEW = "REJECTED_TIME_SKEW"
    RPM_MISMATCH = "REJECTED_RPM_MISMATCH"
    INVALID_FUEL = "REJECTED_INVALID_FUEL"
    INVALID_INJECTION_TIME = "REJECTED_INVALID_INJECTION_TIME"
    COLD_ENGINE = "REJECTED_COLD_ENGINE"
    OPEN_LOOP = "REJECTED_OPEN_LOOP"
    FUEL_TRANSITION = "REJECTED_FUEL_TRANSITION"
    INVALID_STFT = "REJECTED_INVALID_STFT"
    NO_PHYSICAL_CELL = "REJECTED_NO_PHYSICAL_CELL"
    DUPLICATE = "REJECTED_DUPLICATE"


class Source(str, Enum):
    LOCAL = "LOCAL"
    REMOTE = "REMOTE"


@dataclass(frozen=True)
class ObdFuelState:
    """Combustível exibido ao OBD e a origem explícita dessa informação."""
    fuel: Optional[str]
    source: str
    can_qualify_map: bool


@dataclass(frozen=True)
class Sample:
    """Frame bruto já associado, ainda sem valor de condição independente."""
    source: Source
    observed_at_ms: int
    fuel: str
    mp48_rpm: int
    obd_rpm: int
    gasoline_injection_ms: float
    stft: float


@dataclass(frozen=True)
class Condition:
    """Janela estável única, formada por vários frames qualificados."""
    origin_device_id: str
    condition_id: str
    map_epoch_id: str
    curve_epoch_id: str
    source: Source
    sample_count: int


@dataclass(frozen=True)
class Epoch:
    """Fronteira histórica aberta exclusivamente após escrita manual validada."""
    epoch_id: str
    map_readback_hash: str
    curve_readback_hash: str
    started_at_ms: int


@dataclass(frozen=True)
class Comparison:
    """Leitura paralela, sem diferença matemática entre GNV e gasolina."""
    gnv_stft: float
    gasoline_stft: Optional[float]
    gasoline_advisory: Optional[str]


@dataclass(frozen=True)
class Qualification:
    accepted: bool
    rejection: Optional[Rejection] = None
    legacy_coverage: str = "WITHIN_PROGBASE_3000_RPM"


def fuel_state_for_obd(*, mp48_present: bool, mp48_fuel: Optional[str], manual_fuel: Optional[str]) -> ObdFuelState:
    """Resolve o combustível sem confundir declaração manual com confirmação.

    A MP48, quando disponível, sempre prevalece. Sem ela, o operador pode
    informar o combustível para tornar o painel OBD legível; essa informação
    nunca autoriza a entrada no mapa porque ainda não há célula física MP48.
    """
    if mp48_present and mp48_fuel in {"GNV", "GASOLINA"}:
        return ObdFuelState(mp48_fuel, "MP48_CONFIRMED", True)
    if not mp48_present and manual_fuel in {"GNV", "GASOLINA"}:
        return ObdFuelState(manual_fuel, "MANUAL_OPERATOR", False)
    return ObdFuelState(None, "UNKNOWN", False)


def qualify(sample: dict, *, max_time_skew_ms: int = 250, max_rpm_delta: int = 150) -> Qualification:
    """Decide se um frame pode entrar em uma janela estável.

    A ordem é fixa para que Diagnóstico informe o primeiro motivo real de
    rejeição, sem mascarar o dado instantâneo mostrado em "Agora".
    """
    if not sample.get("mp48_present"):
        return Qualification(False, Rejection.NO_MP48)
    if not sample.get("obd_present"):
        return Qualification(False, Rejection.NO_OBD)
    if abs(sample["mp48_at_ms"] - sample["obd_at_ms"]) > max_time_skew_ms:
        return Qualification(False, Rejection.TIME_SKEW)
    if abs(sample["mp48_rpm"] - sample["obd_rpm"]) > max_rpm_delta:
        return Qualification(False, Rejection.RPM_MISMATCH)
    if sample.get("fuel") not in {"GNV", "GASOLINA"}:
        return Qualification(False, Rejection.INVALID_FUEL)
    if sample.get("gasoline_injection_ms", 0) <= 0:
        return Qualification(False, Rejection.INVALID_INJECTION_TIME)
    if sample.get("coolant_c", 0) < 70:
        return Qualification(False, Rejection.COLD_ENGINE)
    if not sample.get("closed_loop"):
        return Qualification(False, Rejection.OPEN_LOOP)
    if sample.get("fuel_transition"):
        return Qualification(False, Rejection.FUEL_TRANSITION)
    if not -100 <= sample.get("stft", -101) <= 99.2:
        return Qualification(False, Rejection.INVALID_STFT)
    if not sample.get("physical_cell"):
        return Qualification(False, Rejection.NO_PHYSICAL_CELL)
    coverage = "ABOVE_PROGBASE_3000_RPM" if sample["mp48_rpm"] > 3000 else "WITHIN_PROGBASE_3000_RPM"
    return Qualification(True, legacy_coverage=coverage)


def condition_key(condition: dict) -> tuple:
    """Uma condição é contada uma vez, mesmo que chegue local e remotamente."""
    return (
        condition["origin_device_id"],
        condition["condition_id"],
        condition["map_epoch_id"],
        condition["curve_epoch_id"],
    )


def is_duplicate(condition: dict, seen: set[tuple]) -> bool:
    key = condition_key(condition)
    if key in seen:
        return True
    seen.add(key)
    return False


def direct_gnv_signal(stft_gnv: float) -> dict:
    """O alvo é sempre o STFT do GNV: gasolina não entra nesta função."""
    if stft_gnv > 3:
        direction, label = "INCREASE_GNV_FUEL", "TENDENCY_POOR"
    elif stft_gnv < -3:
        direction, label = "DECREASE_GNV_FUEL", "TENDENCY_RICH"
    else:
        direction, label = "HOLD_AND_CONFIRM", "NEAR_TARGET"
    return {"display_stft": stft_gnv, "direction": direction, "label": label}


def gasoline_advisory(stft_gasoline: float) -> Optional[str]:
    """Alerta independente; nunca recalcula, reduz ou neutraliza o GNV."""
    return "GASOLINE_BASE_OUT_OF_NEUTRAL" if abs(stft_gasoline) > 3 else None


def parallel_comparison(gnv_stft: float, gasoline_stft: Optional[float]) -> Comparison:
    """Preserva os dois sinais; não existe campo de diferença GNV−gasolina."""
    advisory = gasoline_advisory(gasoline_stft) if gasoline_stft is not None else None
    return Comparison(gnv_stft, gasoline_stft, advisory)


def may_open_epoch(write_confirmed: bool, readback_valid: bool) -> bool:
    return write_confirmed and readback_valid


def sensitivity_observation(before_stft: float, after_stft: float, k_delta: float) -> Optional[float]:
    """Registra resposta observada; não inventa ganho quando não houve ajuste."""
    if k_delta == 0:
        return None
    return (after_stft - before_stft) / k_delta
