# Apagões e quedas nas sessões reais

Gerado por `python3 tools/sessoes/sugerir_antiapagao.py --corpus`. Evidência DERIVADA dos registros; nenhuma escrita na central.

Foram lidas todas as 93 sessões catalogadas: 89 têm telemetria, 4 estão vazias. Total: 177457 leituras.

**Movimento não é comprovável neste corpus:** faltam velocidade, embreagem e chave. Rotação alta não prova carro andando. Motor parando e recuperando também pode ser partida após desligamento manual. Cada caso permanece candidato, inclusive os fora do gás.

Triagem: motor observado em pelo menos 500 rpm e depois abaixo de 350; abaixo de 100 marca motor parado. Agrupa até recuperar 500 ou perder a leitura. Não reordena relógios. Tempos são diferenças do registro, não tempo validado de partida. O JSON guarda cada leitura antes/durante/depois.

| Sessão / leitura | Evento | GNV antes | Menor rotação | Recuperação (leituras) | Tempo registrado (ms) | Desfecho |
|---|---|---|---|---|---|---|
| 005 / 313 | MOTOR_PAROU | True | 0.0 | 7 | 2031.0 | RECUPEROU |
| 014 / 609 | MOTOR_PAROU | True | 0.0 | 9 | 2425.0 | RECUPEROU |
| 018 / 1407 | MOTOR_PAROU | True | 0.0 | 8 | 2307.0 | RECUPEROU |
| 027 / 725 | MOTOR_PAROU | True | 0.0 | 7 | 1996.0 | RECUPEROU |
| 030 / 561 | MOTOR_PAROU | True | 0.0 | 4 | 1149.0 | RECUPEROU |
| 030 / 1457 | MOTOR_PAROU | True | 0.0 | 7 | 1511.0 | RECUPEROU |
| 030 / 1754 | MOTOR_PAROU | True | 0.0 | 6 | 1749.0 | RECUPEROU |
| 035 / 2084 | MOTOR_PAROU | True | 0.0 | 5 | 1642.0 | RECUPEROU |
| 043 / 1228 | QUEDA_FORTE | True | 308.0 | 1 | 314.0 | RECUPEROU |
| 043 / 2424 | MOTOR_PAROU | True | 0.0 | — | — | SEM_RECUPERACAO_REGISTRADA |
| 038 / 1881 | MOTOR_PAROU | True | 0.0 | 3 | 1202.0 | RECUPEROU |
| 042 / 1706 | MOTOR_PAROU | True | 0.0 | — | — | SEM_RECUPERACAO_REGISTRADA |
| 045 / 1631 | QUEDA_FORTE | True | 291.0 | 1 | 300.0 | RECUPEROU |
| 045 / 1637 | MOTOR_PAROU | True | 0.0 | 7 | 1951.0 | RECUPEROU |
| 048 / 891 | MOTOR_PAROU | True | 0.0 | 8 | 2260.0 | RECUPEROU |
| 056 / 2258 | MOTOR_PAROU | False | 0.0 | — | — | SEM_RECUPERACAO_REGISTRADA |
| 060 / 1016 | MOTOR_PAROU | False | 0.0 | — | — | SEM_RECUPERACAO_REGISTRADA |
| 053 / 1082 | MOTOR_PAROU | True | 0.0 | — | — | SEM_RECUPERACAO_REGISTRADA |
| 055 / 1168 | MOTOR_PAROU | True | 0.0 | 8 | 2310.0 | RECUPEROU |
| 055 / 1581 | MOTOR_PAROU | True | 0.0 | — | — | SEM_RECUPERACAO_REGISTRADA |
| 057 / 711 | MOTOR_PAROU | True | 0.0 | — | — | SEM_RECUPERACAO_REGISTRADA |
| 061 / 665 | MOTOR_PAROU | True | 0.0 | — | — | SEM_RECUPERACAO_REGISTRADA |
| 068 / 179 | MOTOR_PAROU | False | 0.0 | — | — | SEM_RECUPERACAO_REGISTRADA |
| 075 / 226 | MOTOR_PAROU | False | 0.0 | 3 | 1095.0 | RECUPEROU |
| 084 / 198 | MOTOR_PAROU | True | 0.0 | — | — | SEM_RECUPERACAO_REGISTRADA |
| 085 / 709 | MOTOR_PAROU | False | 0.0 | — | — | SEM_RECUPERACAO_REGISTRADA |
| 092 / 119 | MOTOR_PAROU | True | 0.0 | 17 | 6107.0 | RECUPEROU |
| 092 / 138 | MOTOR_PAROU | False | 0.0 | 8 | 2458.0 | RECUPEROU |
| 092 / 236 | QUEDA_FORTE | True | 325.0 | 1 | 433.0 | RECUPEROU |
| 092 / 242 | MOTOR_PAROU | True | 0.0 | — | — | SEM_RECUPERACAO_REGISTRADA |
| 122 / 84 | MOTOR_PAROU | False | 0.0 | — | — | SEM_RECUPERACAO_REGISTRADA |

## Comparação de limiares (não é simulação da proteção)

Cobertura conta eventos com GNV antes e leitura anterior entre 350 e o limiar. Cruzamentos sem queda nas 24 leituras seguintes são oportunidades potencialmente desnecessárias, **não** gasolina desperdiçada comprovada. Não se conhece o ciclo interno da central, a atuação da proteção em cada sessão nem o consumo. Não converter diferença entre leituras em rpm por ciclo.

| Rotação máxima | Eventos com leitura anterior abaixo | Cruzamentos em GNV | Sem queda depois | Observação incompleta |
|---|---|---|---|---|
| 1200 | 24 | 1392 | 1370 | 5 |
| 1400 | 24 | 1288 | 1267 | 10 |
| 1600 | 24 | 1303 | 1281 | 16 |
| 1800 | 24 | 1314 | 1297 | 11 |
| 2000 | 24 | 1252 | 1245 | 4 |
| 2200 | 24 | 1100 | 1088 | 8 |

## Conclusões e limites

Os dados não sustentam um número ótimo que evite todos os apagões sem gasolina extra. Não há comparação controlada das configurações nas mesmas condições. Os quatro extras permanecem **sem dado para sugerir**; os rótulos do original não provam o algoritmo dentro da central.

Próximo passo: registrar por evento os valores lidos da proteção, queda, combustível e recuperação no app; recalcular sugestões ao encerrar a sessão, sem gravar sozinho. Não reduzir ajuste associado a falha registrada. Confirmar movimento exige fonte adicional, não adivinhação por rotação.
