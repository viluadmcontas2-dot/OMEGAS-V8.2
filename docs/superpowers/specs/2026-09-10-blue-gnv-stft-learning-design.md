# OMEGAS Blue — aprendizado GNV por STFT

## Decisão aprovada

O sinal científico primário passa a ser o STFT Bank 1 observado enquanto o veículo roda no GNV. Gasolina e comparação de Petrol Inj. deixam de ser pré-requisitos de medição. MP48 não vota no erro; quando disponível, fornece somente contexto opcional (combustível, Petrol Inj. e endereço físico) e permanece como canal manual de leitura/escrita da calibração.

## Fluxo

1. O ELM adquire RPM (010C), MAP (010B) e STFT (0106) no mesmo ciclo delimitado.
2. O runtime aceita somente ciclos completos, finitos, plausíveis e temporalmente compactos.
3. As amostras são agrupadas por região RPM × MAP e persistidas.
4. A estimativa usa mediana robusta, dispersão MAD, massa efetiva e qualidade temporal.
5. STFT positivo pede aumento de combustível; negativo pede redução. Multiplicador bruto = 1 + STFT/100, limitado a 0,80..1,20.
6. Medição e percentual ficam disponíveis sem gasolina e sem readback K.
7. Um alvo exato de Mapa K só é preparado quando existe readback confirmado e Petrol Inj. contextual para endereçar a célula.
8. Toda escrita continua manual: preparar → revisar → confirmar → ACK → readback.

## Segurança e qualidade

- Não usar LTFT na matemática.
- Não aprender em gasolina quando MP48 informar gasolina.
- Se o combustível estiver indisponível, o modo OBD é explicitamente GNV-only e a UI deve comunicar isso.
- Não usar amostra com |STFT| > 50%, RPM fora de 400..8000, MAP fora de 0,10..1,60 bar ou ciclo maior que 750 ms.
- Mínimo de 5 amostras por vizinhança; qualidade mínima 0,55 para sugestão.
- Deadband de ação: |mediana STFT| ≤ 1%.
- Mudança confirmada de calibração inicia nova época e impede mistura de amostras.
- Sem escrita automática.

## Aceite

- Cinco amostras GNV próximas, sem gasolina, produzem medição STFT.
- +10% gera multiplicador 1,10; -10% gera 0,90.
- Outlier isolado não domina a mediana.
- Região distante não contamina a estimativa.
- Reinício restaura amostras da época atual.
- Mudança de época isola evidência antiga.
- OBD não declara dados prontos antes de RPM, MAP e STFT válidos.
- O fluxo funciona antes de qualquer leitura de Mapa/Curva.
- Com readback e Petrol Inj. contextual, prepara no máximo uma célula manual.
