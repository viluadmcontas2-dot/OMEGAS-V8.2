# OMEGAS Blue — dois motores independentes de aprendizado GNV

## Decisão corrigida

MP48 e OBD são sistemas científicos independentes. Nenhum deles é pré-requisito, testemunha ou fonte matemática do outro.

### Motor MP48 — método original

1. Aprende amostras comparáveis por região usando o tempo de injeção da gasolina e o tempo de injeção do GNV fornecidos pelo MP48.
2. Preserva pareamento, ganho causal, qualidade e demais travas já existentes no motor original.
3. Pode preparar alvo exato de Mapa K porque o próprio MP48 fornece o contexto físico necessário.
4. Não usa STFT, MAP ou disponibilidade do OBD para calcular sua correção.

### Motor OBD — método independente

1. O ELM adquire RPM (010C), MAP (010B) e STFT Bank 1 (0106) no mesmo ciclo delimitado.
2. Aprende exclusivamente com STFT enquanto o modo OBD estiver declarado como GNV.
3. Agrupa e persiste evidência por região RPM × MAP.
4. Usa mediana robusta, dispersão MAD, massa efetiva e qualidade temporal.
5. STFT positivo pede aumento de combustível; negativo pede redução. Multiplicador bruto = 1 + STFT/100, limitado a 0,80..1,20.
6. Funciona sem gasolina, sem Petrol Inj. e sem leitura de Mapa/Curva.
7. Não usa dados MP48 na sua coleta, qualidade, erro ou correção.

## Convivência

- A interface apresenta resultados separados e identifica claramente a origem: MP48 ou OBD.
- Evidências, persistência, épocas, estados de prontidão e falhas são isolados.
- Uma falha ou ausência de um sistema não bloqueia o outro.
- Não somar, ponderar, fundir ou usar um motor para validar o outro.
- A aplicação na ECU continua manual: preparar → revisar → confirmar → ACK → readback.
- Se um resultado OBD não puder ser endereçado com segurança numa célula física, ele permanece como recomendação percentual; isso não cria dependência do MP48 para aprender.

## Segurança e qualidade OBD

- Não usar LTFT na matemática.
- Não aceitar amostra sem declaração explícita de operação em GNV.
- Rejeitar |STFT| > 50%, RPM fora de 400..8000, MAP fora de 0,10..1,60 bar ou ciclo maior que 750 ms.
- Mínimo de 5 amostras por vizinhança; qualidade mínima 0,55 para sugestão.
- Deadband de ação: |mediana STFT| ≤ 1%.
- Mudança confirmada de calibração inicia nova época somente no motor afetado.
- Sem escrita automática.

## Aceite

- O motor MP48 mantém o cálculo original gasolina × GNV e funciona com OBD ausente.
- O motor OBD produz medição STFT com cinco amostras GNV próximas e funciona com MP48 ausente.
- +10% STFT gera multiplicador 1,10; -10% gera 0,90.
- Outlier isolado não domina a mediana e região distante não contamina a estimativa.
- Reinício restaura amostras da época atual; mudança de época isola evidência antiga.
- OBD não declara dados prontos antes de RPM, MAP e STFT válidos.
- Nenhum caminho mistura amostras, qualidade ou matemática entre MP48 e OBD.
- A UI mostra duas autoridades separadas e nunca sugere que uma valida a outra.
