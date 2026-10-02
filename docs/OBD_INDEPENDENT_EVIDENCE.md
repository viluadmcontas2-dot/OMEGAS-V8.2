# OBD — segunda prova independente

Data: 2026-08-08
Estado: implementado na branch `feature/ux-didactic-expansion`; CI Android e validação física pendentes.

## Objetivo

Transformar a área OBD em uma segunda fonte de evidência do comportamento do motor, separada do aprendizado MP48 e incapaz de escrever calibração.

## Duas superfícies OBD

1. **Correlação OBD × MP48 existente**
   - eixos: RPM e tempo de injeção gasolina da MP48;
   - conteúdo: STFT/LTFT e contexto OBD;
   - finalidade: correlacionar a ECU original com a célula física usada pelo núcleo.

2. **Mapa OBD independente**
   - eixo X: RPM OBD;
   - eixo Y: carga calculada OBD, PID `0104`;
   - conteúdo: STFT, LTFT, velocidade, temperatura, MAP, MAF e borboleta vindos do OBD;
   - camadas: Gasolina, GNV e Comparação;
   - comparação: diferença de STFT GNV − gasolina calculada no Kotlin apenas quando há amostras suficientes na mesma célula OBD.

O segundo mapa não usa tempo de injeção como eixo e não participa do aprendizado ou da calibração.

## PIDs

Críticos em todo ciclo:
- `0103` — estado do sistema de combustível / closed loop;
- `0106` — STFT banco 1;
- `010C` — RPM.

Contexto:
- `0104` — carga calculada;
- `0105` — temperatura do líquido de arrefecimento;
- `0107` — LTFT banco 1;
- `010B` — pressão absoluta do coletor (MAP);
- `010D` — velocidade;
- `010F` — temperatura do ar de admissão;
- `0110` — MAF;
- `0111` — posição da borboleta;
- `012F` — nível do tanque em percentual;
- `0142` — tensão do módulo de controle.

A descoberta Mode 01 continua sendo a autoridade de disponibilidade. Um PID não anunciado pela ECU é exibido como não suportado e não deve ser inventado. O PID `012F` fornece percentual; esta implementação não converte para litros sem conhecer a capacidade real do tanque.

## Gate da coleta independente

Uma amostra entra no mapa OBD independente somente quando:
- há rótulo observacional de combustível;
- a ECU original está em closed loop;
- RPM OBD é válido;
- carga OBD está entre 0 e 100%;
- STFT é válido;
- quando a temperatura OBD está disponível, o motor não está abaixo do mínimo configurado.

O rótulo de combustível pode vir da MP48 ou, se ela não estiver disponível, do operador. Rótulo manual não habilita aprendizado MP48 e não concede autoridade de escrita.

## Persistência e épocas

O mapa independente é persistido dentro de `obd_assist_v1.json` em um componente próprio. Após um ajuste manual confirmado por readback, a fotografia anterior é preservada no histórico da época e uma nova coleta ativa começa, evitando misturar evidência antes/depois.

## Invariantes

- OBD é somente observação;
- nenhuma chamada a `KWriteManager`, `KFactorManager`, `startKWrite`, `startKBatchWrite` ou `startKFactorWrite` existe na superfície OBD;
- nenhuma sugestão OBD inicia escrita;
- nenhuma matemática de Mapa K ou Curva K foi alterada;
- nenhuma gravação automática na ECU foi adicionada;
- a comparação do mapa independente é informativa e não vira correção automática.

## Evidência automatizada adicionada

- `ObdIndependentEvidenceMapTest.kt`;
- `tests/test_obd_independent_evidence_contract.py`;
- `tests/ui/obd-independent-map.test.cjs`;
- os contratos rápidos foram adicionados a `tools/run_checks.py`.

## Validação física ainda necessária

No veículo/aparelho real confirmar:
- quais PIDs o carro anuncia;
- se `012F` existe e representa corretamente o nível do tanque;
- estabilidade/latência do ELM327 com o grupo ampliado;
- preenchimento das células RPM × carga durante condução;
- separação Gasolina/GNV;
- comportamento via Omegas Link;
- layout e toque na multimídia 1024×600.
