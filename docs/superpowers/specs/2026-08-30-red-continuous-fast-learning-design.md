# RED Continuous Fast Learning Design

## Problema comprovado

A tela combina agregado de gasolina, agregado GNV e uma comparação selecionada separadamente. Pode mostrar GNV 6,96 ms enquanto a equivalência usa 5,69 ms, ou “sem evidência gasolina” enquanto usa referência 5,21 ms. A confiança do agregado aparece como se fosse confiança da equivalência.

## Modelo

`error_ratio = (petrol_on_cng_ms - petrol_target_ms) / petrol_target_ms`

`error_ratio = global_curve(petrol_target_ms) + local_residual(rpm, map_bar) + noise`

- `global_curve` interpola os pontos físicos da Curva K.
- `local_residual` usa regressão local robusta e limitada em RPM×MAP.
- a projeção física estima MAP por âncoras `(RPM, Tinj_ref)→MAP` e consulta o residual;
- suporte distante contribui ao global, mas não copia residual local;
- saída sem suporte é `GLOBAL_ONLY` ou `UNKNOWN`, nunca observação inventada.

## Procedência

Cada comparação publica origem/timestamp, RPM/MAP observado, par realmente usado, regiões da referência, tipo e distância do suporte, spread, qualidade, época e hash de calibração. A UI chama agregados de “resumo projetado da célula”.

## Confiança

Separar `precision`, `independentSupport`, `supportType`, `uncertaintyPercent` e `nearestDistance`. Uma evidência forte gera previsão provisória imediata; repetição correlacionada não fabrica visitas.

## Sugestões

- Curva K recebe tendência global.
- Mapa K recebe somente residual local com suporte direto/próximo e margem maior que deadband+incerteza.
- Passo depende da incerteza, nunca ultrapassa o alvo e mantém K em 100–180.
- Nenhuma sugestão chama writer.

## Segurança, desempenho e compatibilidade

- RPM não bloqueia fluxo manual; serviço/USB/engine/telemetria fresca permanecem requisitos.
- confirmação, ACK e readback permanecem.
- Advisor roda somente em revisão semântica, fora do hot path.
- no máximo 600 comparações × 144 projeções, com vizinhança limitada.
- campos legados continuam legíveis e a UI funciona sem previsão.
