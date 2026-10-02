#!/usr/bin/env python3
"""Simulador determinístico do laço fechado da ECU rodando em GNV.

Modelo mínimo, explícito e falsificável:
  * a ECU calcula o tempo de gasolina T e injeta gás proporcional a T·K(T);
  * K(T) é interpolado linearmente nos 30 pontos do eixo (MUL_ACT);
  * a correção de mistura (lambda) é multiplicativa e integral:
        T[k+1] = T[k] · (1 + α · (Q / (T[k−d]·K(T[k−d])) − 1))
    onde Q é a vazão de gás exigida pelo ponto de operação e d o atraso em frames.

Linearizando no ponto fixo, o ganho do laço vale α·(1+ε), com
ε = d ln K / d ln T (elasticidade local da Curva K). Se esse produto passa de
~2 (d=0) ou ~1 (d=1) a correção ultrapassa o alvo e entra em ciclo-limite —
o "tranco" observado em GNV. A Equivalência Refinada limita |ε| ≤ E_MAX.

Isto é evidência de simulação, não validação física.
"""
import math

from refined_oracle import interp


def k_of(t, axis_ms, factors):
    return interp(t, axis_ms, factors)


def simulate(axis_ms, factors, fixed_point_ms, alpha, delay=0, steps=240, kick=1.06):
    q = fixed_point_ms * k_of(fixed_point_ms, axis_ms, factors)
    history = [fixed_point_ms * kick] * (delay + 1)
    for _ in range(steps):
        lagged = history[-1 - delay]
        current = history[-1]
        flow = lagged * k_of(lagged, axis_ms, factors)
        nxt = current * (1.0 + alpha * (q / flow - 1.0))
        history.append(min(max(nxt, axis_ms[0]), axis_ms[-1]))
    tail = history[-60:]
    return max(tail) - min(tail), tail


def oscillation_profile(axis_ms, factors, alpha, delay, lo=1.5, hi=12.0, step=0.05):
    out = []
    t = lo
    while t <= hi + 1e-9:
        amplitude, _ = simulate(axis_ms, factors, t, alpha, delay)
        out.append((round(t, 3), amplitude))
        t += step
    return out


def calibrate_alpha(axis_ms, factors, delay, observed_band=(8.0, 8.5), observed_amplitude=0.6):
    """Menor α que reproduz o ciclo-limite observado na faixa indicada."""
    alpha = 0.05
    while alpha <= 2.0:
        hits = [a for t, a in oscillation_profile(axis_ms, factors, alpha, delay, *observed_band, 0.05)
                if a >= observed_amplitude]
        if hits:
            return round(alpha, 2)
        alpha += 0.01
    return None


def usage_weighted_oscillation(axis_ms, factors, alpha, delay, usage, threshold=0.1):
    """Fração do uso real de GNV (histograma por ms) em ponto oscilante."""
    total = sum(usage.values()) or 1
    bad = 0
    for t, count in usage.items():
        amplitude, _ = simulate(axis_ms, factors, t, alpha, delay)
        if amplitude > threshold:
            bad += count
    return bad / total
