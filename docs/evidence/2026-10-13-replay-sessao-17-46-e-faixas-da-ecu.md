# Replay da sessão das 17:46 (APK de teste anterior) e as faixas prontas da ECU

Ferramenta nova: `python3 -I tools/sessoes/replay_sessao.py <pasta com events_*.jsonl> [--min 10]` (aceita sessão em ZIP único ou em partes).

## Sessão 2026-10-13 17:46 (90 min, 13,7 mil quadros de telemetria)
```
Sessão: 90.2 min · 13769 quadros de telemetria · 11 gravações de K · 1 apagão(ões) · 165 afundamentos de RPM

== Linha do tempo (janelas de 10 min) ==
janela  | GNV% gasol% corte% | rpm med | afund. | apagões | grav.K | fase final do Refino
  0- 10 |   94     0      5 |    1142 |      9 |       0 |      0 | —
 10- 20 |   88     0     12 |    1922 |     23 |       0 |      0 | —
 20- 30 |   92     0      8 |    1727 |     25 |       0 |      0 | —
 30- 40 |   97     0      2 |     872 |     24 |       0 |      0 | —
 40- 50 |   91     3      6 |    1110 |     29 |       0 |      0 | PROPOSTA_PRONTA
 50- 60 |   81    14      4 |    1425 |     24 |       0 |      0 | —
 60- 70 |  100     0      0 |     865 |      0 |       0 |      0 | —
 70- 80 |   90     7      2 |     874 |     15 |       0 |      6 | RESTAURAR_TRECHO
 80- 90 |   64    26      5 |    1815 |     16 |       1 |      5 | PROPOSTA_PRONTA
 90-100 |  100     0      0 |     875 |      0 |       0 |      0 | —

== Gravações de K e o que o app mediu depois ==
   73.9 min · 11 pontos (+3.0%..+9.7%) · depois: melhorou 0, piorou 2, medindo 9
   75.5 min ·  3 pontos (+2.3%..+7.7%) · depois: melhorou 8, piorou 2, medindo 0
   76.0 min · 20 pontos (-8.8%..+8.7%) · depois: melhorou 5, piorou 0, medindo 5
   78.0 min · 17 pontos (-6.0%..+3.0%) · depois: melhorou 1, piorou 0, medindo 10
   78.8 min ·  2 pontos (+2.8%..+10.7%) · depois: melhorou 5, piorou 5, medindo 0
   79.7 min ·  3 pontos (+4.1%..+25.6%) · depois: melhorou 5, piorou 5, medindo 0
   82.4 min · 15 pontos (-2.3%..+1.6%) · depois: melhorou 7, piorou 0, medindo 5
   83.9 min ·  6 pontos (-0.6%..+1.0%) · depois: melhorou 5, piorou 0, medindo 0
   87.9 min ·  1 pontos (+14.7%..+14.7%) · depois: melhorou 0, piorou 0, medindo 1
   88.4 min ·  5 pontos (+9.5%..+19.1%) · depois: melhorou 1, piorou 4, medindo 0
   89.5 min ·  5 pontos (+4.7%..+14.9%) · depois: melhorou 0, piorou 0, medindo 5
   80.5 min · ação da ECU: Readquirir GNV (CONFIRMED)

== Cobertura da superfície (células RPM×MAP com leitura estável de 3 quadros), acumulada ==
  até  15 min: GNV   1 células · gasolina   0 células
  até  30 min: GNV  62 células · gasolina   0 células
  até  45 min: GNV  82 células · gasolina   0 células
  até  60 min: GNV  90 células · gasolina   0 células
  até  75 min: GNV  95 células · gasolina  10 células
  até  90 min: GNV  96 células · gasolina  10 células
  até 105 min: GNV 110 células · gasolina  37 células
  total: GNV 110 células · gasolina 37 células
```

## Leitura (descritiva; nada virou regra)
- **Gasolina foi só ~10% do tempo** (jan. 50–60 e 80–90): a superfície de gasolina chegou a 37 células contra 110 de GNV. É por isso que o Refino tinha pouca gasolina própria e a Curva da ECU serviu de referência.
- **11 gravações de K em 16 minutos** (73,9 → 89,5 min); em 3 delas (78,8; 79,7; 88,4) o app disse que **tantos pontos pioraram quanto melhoraram** (5/5 e 1/4). O app avalia a gravação em 0–4 min, com poucas amostras por faixa; a leitura "piorou" muda de uma janela para outra. Não dá para afirmar convergência nem divergência com esses dados.
- **Afundamentos de RPM (<1100 com queda ≥20%):** 165 no total, 9–29 por janela de 10 min nos primeiros 60 min e 15–16 nas janelas 70–90, com 11 gravações no meio. NÃO normalizei pela quantidade de condução (janelas de marcha lenta pura têm 0): não é prova de melhora.
- A ECU não fez nenhum AutoMatch na sessão (0 de 3); a única ação nativa foi o seu "Readquirir GNV" aos 80,5 min.

## As faixas prontas da ECU persistem entre sessões? (para usar os quadradinhos ao conectar)
Em 82 pares de sessões consecutivas (< 48 h entre uma e outra), **79 já começam com faixas de GNV preenchidas** (tipicamente 11–16 das 18). A primeira leitura vem poucos segundos depois de conectar, tempo insuficiente para o app ter adquirido isso: os dados vêm da memória da ECU. Em 14 pares os contadores são idênticos aos do fim da sessão anterior. Conclusão: ler as faixas maduras (tempo e MAP médios por combustível) ao conectar daria pontos imediatos. Falta provar a fórmula da média e o limiar de "pronta" na prática.
