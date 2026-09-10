# OMEGAS Blue — motores MP48 e OBD independentes

Rastreio canônico: #29.

## Relação assimétrica

Os motores não dependem um do outro para medir nem sugerir. A integração admitida é de mão única e não bloqueante: quando houver evidência OBD compatível, ela pode aumentar a confiança de uma conclusão MP48. Ausência ou conflito OBD nunca apagam, recalculam ou bloqueiam uma medição MP48 válida.

### Motor MP48

1. Coleta normalmente em gasolina e GNV.
2. Calcula o erro original pela equivalência de Petrol Inj. gasolina × Petrol Inj. durante GNV.
3. Mantém pareamento temporal, ganho causal, qualidade e endereçamento físico do Mapa K.
4. Funciona integralmente sem OBD.
5. OBD concordante pode somente bonificar confiança; não entra no erro nem no alvo.

### Motor OBD

1. É dedicado a STFT Bank 1 no GNV; não aprende gasolina e não usa LTFT.
2. Adquire RPM (010C), MAP (010B) e STFT (0106) no mesmo ciclo delimitado.
3. Não lê nem exige combustível, Petrol Inj., telemetria, calibração ou disponibilidade do MP48.
4. O operador declara explicitamente o modo GNV antes de coletar.
5. Agrupa e persiste evidência por RPM × MAP e época OBD.
6. Usa mediana, MAD, suporte e qualidade temporal.
7. Calcula sua própria recomendação: multiplicador = 1 + STFT/100, limitado a 0,80..1,20.
8. Com readback confirmado do Mapa K, endereça no máximo uma célula coerente com RPM × MAP e prepara sugestão manual própria.

## Segurança comum

- Resultados e estados aparecem separados na UI.
- Persistência, épocas, prontidão e falhas são isoladas.
- Não somar nem fundir correções.
- OBD conflitante não bloqueia MP48.
- Sem escrita automática.
- Aplicação: preparar → revisar → confirmar → ACK → readback.
- Uma escrita confirmada inicia nova época do motor cuja calibração foi alterada.

## Qualidade OBD

- Rejeitar ciclo incompleto, não finito ou maior que 750 ms.
- Rejeitar |STFT| > 50%, RPM fora de 400..8000 e MAP fora de 0,10..1,60 bar.
- Mínimo de 5 amostras por vizinhança e qualidade mínima 0,55.
- Deadband: |mediana STFT| ≤ 1%.

## Aceite

- MP48 gasolina × GNV permanece verde com OBD ausente.
- OBD gera +10% → 1,10 e -10% → 0,90 com MP48 ausente.
- Outlier, região distante e época anterior não contaminam.
- Reinício restaura a época OBD atual.
- OBD não declara dados prontos antes de 010C, 010B e 0106 válidos.
- Modo GNV OBD é explícito.
- OBD prepara no máximo uma célula manual do Mapa K a partir de readback confirmado.
- OBD concordante pode aumentar confiança MP48; ausência/conflito nunca bloqueiam ou alteram sua matemática.
- A UI identifica a origem e não sugere dependência.
