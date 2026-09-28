# Platina — correção de maturidade AutoCal baseada no DUMP

Data: 2026-09-28

## Problema encontrado

O código herdado promovia posições específicas de `CALIBRATION_VAL_1` a limiares de maturidade:

- índice 2 → `petrolNormal`;
- índice 5 → `gasLow`;
- índice 8 → `gasNormal`.

Isso não é sustentado pela evidência canônica.

## Evidência

O DUMP hash-bound do ProgBase prova simultaneamente:

- `CALIBRATION_VAL_1` no `TAUTOCALDM` tem `ArrayDimension = 10` no módulo observado;
- `TAUTOCALSETTINGS` expõe um editor conceitual de **12** linhas;
- os 12 conceitos são Idle/Normal × Petrol/Gas × MinBuf/MinBufUpd/SumBuf;
- ainda não existe prova da projeção exata desses 12 conceitos sobre os 10 bytes retornados pela ECU observada.

A própria documentação forense remota registra explicitamente que **não é válido atribuir os dez bytes às primeiras dez linhas sem provar o mapeamento de módulo/handler**.

## Correção Platina

A Platina agora falha fechado:

- não promove `CALIBRATION_VAL_1[2/5/8]` a thresholds;
- mantém o vetor bruto disponível em diagnóstico;
- mantém `VECT_AUTOCAL_U8_1` apenas com seu significado diretamente provado: `AUTOCAL_IDLE_MIN_BUF_UPD_PETR_THD`;
- mostra atividade real das 18 bandas por buffers/contadores;
- mostra aquisição de zona somente pelos vetores nativos `ACQUIRED_ZONES_PETROL/GAS`;
- não chama uma bolinha de madura/valida por uma regra não provada;
- continua atualizando as 18 bandas em tempo real mesmo com maturidade semântica desconhecida.

## Consequência

Isto remove uma falsa certeza sem perder informação operacional.

A UI pode continuar mostrando:
- ponto observado;
- Tinj;
- MAP;
- contador;
- combustível;
- zona;
- flag nativa de zona adquirida;
- histórico GAS_PREV.

O que ela não pode afirmar ainda é:
> “esta banda ficou madura porque count >= X”

quando `X` depende do mapeamento 10↔12 não resolvido.

## Stop condition

Maturidade per-band baseada em threshold só pode voltar a ser promovida quando o DUMP/Portmon/firmware fechar o binding exato dos thresholds para o módulo observado. Até lá, zona nativa e atividade bruta são a autoridade.
