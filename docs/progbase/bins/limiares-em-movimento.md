# Apagões em movimento: qual Rotação máxima o seu carro pede

Gerado por `python3 tools/sessoes/limiares_em_movimento.py --escrever`. Evidência DERIVADA dos logs; nada é gravado na central.

Lidas 89 sessões (12.3 h). Só contam quedas que partem de 1200 rpm ou mais (em movimento); motor morrendo em marcha lenta ficou de fora.
Achados: **20 apagões** (motor caiu abaixo de 350 rpm e ficou), 35 quase-apagões e 3646 quedas comuns (troca de marcha, desaceleração).

| Rotação máxima | Apagões que a função veria | Quase-apagões vistos | Quedas comuns que a acionariam (por hora) |
|---|---|---|---|
| 1400 | 15 de 20 | 35 de 35 | 152.2 |
| 1600 | 19 de 20 | 35 de 35 | 187.4 |
| 1800 | 20 de 20 | 35 de 35 | 221.9 |
| 2000 | 20 de 20 | 35 de 35 | 249.4 |
| 2200 | 20 de 20 | 35 de 35 | 267.5 |

**Número sugerido para Rotação máxima: 1800 rpm** — o menor valor que vê todos os apagões em movimento. Acima disso só aumenta o acionamento à toa.

Tempo que o motor ficou abaixo de 700 rpm nos quase-apagões: metade até 0.5 s, 90% até 2.0 s, o pior 5.0 s. É a medida que sustenta olhar a Espera para sair do gás (hoje 1500 ms); sem prova de efeito, **não** é sugestão de mudança.

## Limites

- Não há velocidade, embreagem nem chave no log: "em movimento" = a rotação vinha de 1.200 rpm ou mais; o dono confirmou que houve apagões em movimento.
- Isto não simula a função da central: mostra o que passa pelo limiar, não que a função teria salvado o motor.
- A velocidade de queda (rpm por leitura de 0,25 s) não vira o número do cartão "Queda de rotação": a unidade da central é desconhecida. Nenhuma sugestão para ela.
- Os quatro ajustes extras continuam sem dado para sugerir mudança.
