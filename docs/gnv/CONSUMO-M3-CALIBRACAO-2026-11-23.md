# GNV: consumo físico em m³ — experimento isolado (2026-11-23)

## Decisão

O byte MP48 level_raw descreve um sensor de nível em degraus e **não** é um medidor linear de m³.
Não usar capacidade nominal do cilindro × (1 − level_raw/255) como verdade física.
O código de produção já acumula injectorOpenMinutesGnv em SessionSummaryBuilder e TankLedger.
Aproveitar esta mesma medida; sem novo polling serial e sem CI/recompilação de APK por esta investigação.

## Grandezas

- GNV: m³ consumidos, km/m³, m³/100 km, R$/km. Litro por 100 km é métrica de combustível líquido e não significa que m³ e L sejam diretamente intercambiáveis.
- MP48: gas_ms_diagnostic, rpm, gas_pressure_abs_bar, pressure_diff_bar, gas_c e estado GNV confirmado. O tempo acumulado dos injetores NÃO mede volume sem conhecer/ajustar vazão por injetor, pressão, temperatura e composição.
- Distância: odômetro ou velocidade confirmada de fonte única (MM/GPS/OBD). RPM_PROXY não sustenta km.
- Referência independente: volume de GNV do dispenser e confirmação de **dois abastecimentos comparáveis** no início e no fim do ciclo, com cobertura de telemetria conhecida. O visor do posto não prova sozinho que o tanque estava no mesmo estado nas duas datas.

## Pipeline proposto

1. Continuar gravando quadro bruto, instante monotônico, origem, rpm, pulso GNV, pressão diferencial e temperatura. Não enviar escritas ao veículo.
2. Somar minutos de injetor aberto que já existem (4 cilindros, 4 tempos: rpm/120, apenas trechos GNV, gap <= 2 s).
3. Associar cada ciclo fechado ao comprovante do abastecimento **seguinte**, identificando o conjunto de sessões. Não calibrar usando somente o nível cru; requer cobertura >= 95% e indicação explícita de abastecimento comparável.
4. Reservar o terceiro ciclo como validação independente do fator de volume m³/minuto. Com erro fora do limite piloto de 5% ou divergência de fator de treinamento >10%, retornar estado DIVERGENCIA_MEDIDA e **não** exibir m³ estimados.
5. Comparar também faixa de pressão e temperatura entre os ciclos. Neste primeiro motor a variação ainda não é corrigida; sem esses dados, o estado de validação não equivale à precisão metrológica.
6. Só depois conectar o módulo ao serviço Android e à interface: mostrar progresso das referências, cobertura de leitura, erro do último ciclo, consumo acumulado e kg/m³ quando aplicável. Não mostrar zeros no lugar de desconhecido. Nenhum ajuste automático da MP48.

## Implementado nesta branch

- Motor puro CngVolumeCalibration.kt, isolado dos writers ECU/MP48.
- Verificação focal tools/jvm/gnv-volume-calibration-check.kt (cenários sintéticos, não prova em veículo).
- O módulo não está ligado ao runtime do aplicativo; **não foi liberado nem instalado no Oppo**.
- Executar verificação independente: kotlinc app/src/main/java/com/omegas/prohub/telemetry/CngVolumeCalibration.kt tools/jvm/gnv-volume-calibration-check.kt -include-runtime -d /tmp/gnv-check.jar && java -jar /tmp/gnv-check.jar.

## Limites científicos

A calibração baseada na duração de injeção é observacional, sujeita à curva de vazão e aos transientes dos injetores. Para precisão de bancada é necessário medidor de vazão/massa adequado e calibrado, sem intervenções não autorizadas na linha de GNV. Pressão estática do cilindro, nível elétrico e consumo estimado pela ECU de gasolina não são substitutos de medição volumétrica.

Fontes: INMETRO: https://www.gov.br/inmetro/pt-br/acesso-a-informacao/perguntas-frequentes/avaliacao-da-conformidade/servico-de-instalacao-de-gnv-em-veiculos/qual-a-capacidade-de-armazenamento-de-gnv-no-cilindro ; IPEM PR: https://www.ipem.pr.gov.br/Pagina/GNV-GAS-NATURAL-VEICULAR ; artigo: https://www.mdpi.com/2076-3417/16/18/9032 .
