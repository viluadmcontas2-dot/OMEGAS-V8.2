# Contrato OBD × MP48 — Fase 2

Esta pasta congela a fronteira científica antes da integração Android. É um
contrato puro e executável: não abre conexão, não grava na ECU e não altera o
aprendizado principal.

## Entidades congeladas

- `Sample`: frame bruto associado; não é evidência independente.
- `Condition`: janela estável de frames qualificados, com origem e épocas.
- `Comparison`: os dois STFTs preservados em paralelo, sem campo de subtração.
- `Epoch`: fronteira antes/depois com hashes de readback.

## Regras congeladas

- Um frame só pode entrar em uma janela estável após MP48/OBD presentes,
  sincronização temporal, RPM compatível, combustível identificado, tempo de
  gasolina válido, motor aquecido, malha fechada, ausência de transição, STFT
  válido e célula física identificada.
- Frames brutos não são condições independentes. Uma condição possui chave
  estável `originDeviceId + conditionId + mapEpochId + curveEpochId` e jamais é
  contada duas vezes.
- O mapa OBD posiciona a amostra por `tempo gasolina MP48 × RPM MP48`.
- O alvo operacional do GNV é `STFT GNV ≈ 0%`. STFT positivo orienta aumento
  gradual do combustível GNV; negativo orienta redução gradual.
- A gasolina gera somente alerta diagnóstico. Ela nunca é subtraída, nem muda
  o valor, a cor ou a direção do STFT GNV.
- Sem MP48, o OBD pode receber uma declaração manual de combustível pelo botão
  `Combustível ativo`. A interface marca a origem como `MANUAL_OPERATOR`; isso
  serve para leitura ao vivo e nunca qualifica uma condição nem posiciona uma
  célula no Mapa K.
- LTFT é contextual. Nenhum percentual de STFT é copiado diretamente para K.
- A alteração de K permanece manual. Uma nova época só nasce depois de
  confirmação da escrita e readback válido; a sensibilidade vem da resposta
  observada antes/depois.
- Evidência acima de 3.000 RPM é aceita para o OBD, mas recebe a etiqueta
  `ABOVE_PROGBASE_3000_RPM`; não pode ser apresentada como cobertura AutoCal
  equivalente ao limite legado.

## Executar

```bash
python3 -m unittest lab.contracts.test_obd_mp48_contract \
  lab.preview-v1.test_preview_contract
```

Os fixtures em `fixtures/obd_mp48_replay.json` são o replay mínimo que a Fase 3
deve continuar aprovando ao conectar o motor real.
