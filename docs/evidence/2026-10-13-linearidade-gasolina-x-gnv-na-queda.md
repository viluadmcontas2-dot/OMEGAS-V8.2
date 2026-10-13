# Gasolina × GNV na desaceleração: o que a gasolina faz e o GNV não (2026-10-13)

Pedido do dono: comparar, no mesmo tipo de momento em que o GNV engasga, o que a gasolina faz (linearidade) e não apaga.
Método e código: `tools/sessoes/analise_linearidade_queda.py` (corpus real: 93 sessões, ~17 h; OBSERVAÇÃO, não regra).

## Observado
| Combustível | Quedas de rotação (<450 rpm depois de ≥650) | Taxa |
|---|---|---|
| GNV | 30 em 9,4 h | 3,2/h |
| GNV depois de corte (CUTOFF) | 7 em 0,67 h | 10,5/h |
| Gasolina | 2 em 2,2 h | 0,9/h |

Desacelerações (rpm de ≥1300 a ≤1000 em 1,5 s), afundamento = rpm da lenta final − rpm mínimo:
- Gasolina: mediana 15 rpm, 2 % com afundamento > 300 rpm (n=49).
- GNV: mediana 106 rpm, 13 % > 300 rpm (n=244). GNV depois de corte: mediana 308 rpm, 52 % > 300 rpm (n=27).
- **Controlando a carga** (petrol_ms no mínimo): em 3–6 ms gasolina e GNV afundam igual (10 rpm). A diferença aparece em 6–9 ms (109 × 166 rpm) e ≥ 9 ms (GNV 168), e é grande depois do corte (304–428 rpm). Ou seja: **o GNV não é pior no regime de baixa carga; é pior ao voltar de carga alta e de corte.**
- Linearidade da conta da ECU (gás ÷ gasolina em regime, mediana por faixa de petrol_ms): 1,94 (1,5 ms) → 2,40 (5 ms) → 2,20 (8–10 ms) → 1,92 (12 ms). Não é uma reta: a razão sobe até ~5 ms e cai depois.

## O que isso diz sobre "reforçar o Mapa K em +5 %"
Razão gás/gasolina (÷ regime no mesmo petrol_ms) em relação ao instante do mínimo de rpm:

| Instante | afundamento > 300 rpm | afundamento ≤ 150 rpm |
|---|---|---|
| −1500 ms | 0,993 | 1,025 |
| −1000 ms | 0,994 | 1,007 |
| −500 ms | 1,034 | 1,011 |
| −250 ms | **1,163** | 1,022 |
| 0 | 1,164 | 1,025 |

- **Antes** do afundamento a razão do GNV está igual ao regime (≈ 0,99; diferença ≤ 3 % para os casos rasos): **não há sinal de mistura pobre antes da queda.**
- Depois que a rotação já está caindo (−250 ms), a razão sobe ~16 %: é a ECU enriquecendo em reação (correção dinâmica), não uma causa.
- Conclusão (INFERIDA, não provada): o engasgo parece **transitório** (retomada depois do corte e volta de carga alta), não um erro estático de K em uma célula. Um +5 % fixo em células de petrol_ms baixo **não tem prova física** de que resolva, e a direção (mais gás) não é confirmada pelos dados.

## O que o app faz com isso
- O botão "Gravar no Mapa K — reforço do engasgo" continua **manual, com resumo e Desfazer** (nada grava sozinho), mas deve ser tratado como **experimento a medir**: comparar a taxa de engasgos por hora antes e depois (a taxa de base é 3,2/h no GNV).
- Próximo passo de ciência (não feito): registrar a taxa por hora e o tipo (depois de corte / carga alta) no StallWatch e mostrar "desde a correção: X por hora (antes: Y)".
