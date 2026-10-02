# Oportunidades para o OMEGAS

Ordenadas por valor potencial de informação, não por ganhos de consumo já demonstrados. Todas **INFERIDAS** como aplicações; seus parâmetros de base são rastreados em parametros.json. A métrica principal é erro de tempo de gasolina comparado em células/regimes equivalentes, não igualdade literal de duração dos pulsos físicos gasosos.

## 1. Nível calibrado por referência

- Dados a ler: TIPO_SENSORE (SC 36), RIF_SENSORE (SC 37).
- Hipótese: Comparar nível bruto contra faixas/reserva para mensurar volume utilizável; ler, nunca presumir linearidade.
- Risco: Risco: geometrias de cilindros e sensores invertidos.
- Fonte: DUMP/RT_RCDATA(10)__TSTREAMDATI__0.bin: offset 0x25B8; componente TIPO_SENSORE; propriedade SerialCode ; DUMP/RT_RCDATA(10)__TSTREAMDATI__0.bin: offset 0x2672; componente RIF_SENSORE; propriedade SerialCode.

## 2. Tanque e autonomia

- Dados a ler: TANK_VOL (SC 313), TANK_VOL (SC 313).
- Hipótese: Combinar litros configurados com abastecimentos reais e variabilidade de pressão/temperatura.
- Risco: Risco: volume nominal não é gás disponível.
- Fonte: DUMP/RT_RCDATA(10)__TSTREAMDATI__0.bin: offset 0x18FD7; componente TANK_VOL; propriedade SerialCode ; DUMP/RT_RCDATA(10)__TSTREAMDATI__0.bin: offset 0x18FD7; componente TANK_VOL; propriedade SerialCode.

## 3. Limiar visual e histerese

- Dados a ler: SOGLIA_LED_1 (SC 300), SOGLIA_LED_4 (SC 300).
- Hipótese: Usar LEDs e histerese para detectar instabilidade de reserva sem oscilação.
- Risco: Risco: extrapolação entre LEDs.
- Fonte: DUMP/RT_RCDATA(10)__TSTREAMDATI__0.bin: offset 0x17376; componente SOGLIA_LED_1; propriedade SerialCode ; DUMP/RT_RCDATA(10)__TSTREAMDATI__0.bin: offset 0x17772; componente SOGLIA_LED_4; propriedade SerialCode.

## 4. Filtragem de nível

- Dados a ler: LO_PASS_FILT_CON_FAST (SC 276), LO_PASS_FILT_CON_SLOW (SC 276).
- Hipótese: Observar resposta a abastecimento/consumo sem confundir filtro com queda real.
- Risco: Risco: atraso introduzido pelo filtro.
- Fonte: DUMP/RT_RCDATA(10)__TSTREAMDATI__0.bin: offset 0x16F1B; componente LO_PASS_FILT_CON_FAST; propriedade SerialCode ; DUMP/RT_RCDATA(10)__TSTREAMDATI__0.bin: offset 0x1709A; componente LO_PASS_FILT_CON_SLOW; propriedade SerialCode.

## 5. Curva K e referência gasolina

- Dados a ler: MUL_ACT (SC 353), PETR_INJ_TBP (SC 331).
- Hipótese: Comparar aquisição GNV e gasolina por ponto e manter histórico com dispersão.
- Risco: Risco: Q14, interpolação e cobertura esparsa.
- Fonte: DUMP/RT_RCDATA(10)__TAUTOCALDM__0.bin: offset 0x1855; componente MUL_ACT; propriedade SerialCode ; DUMP/RT_RCDATA(10)__TAUTOCALDM__0.bin: offset 0x275; componente PETR_INJ_TBP; propriedade SerialCode.

## 6. Mapa K bidimensional

- Dados a ler: MAP_K (SC 84), GIRI_PER_K (SC 61).
- Hipótese: Analisar erro condicionado por RPM e tempo de gasolina, com validação fora da amostra.
- Risco: Risco: eixos, clipping e mistura de regimes.
- Fonte: DUMP/RT_RCDATA(10)__TSTREAMDATI__0.bin: offset 0x4ADF; componente MAP_K; propriedade SerialCode ; DUMP/RT_RCDATA(10)__TSTREAMDATI__0.bin: offset 0x3C67; componente GIRI_PER_K; propriedade SerialCode.

## 7. Tempo morto e injetores

- Dados a ler: ADV_OFFSET_INJ_GAS (SC 243), ADV_OFFSET_INJ_PETROL (SC 242).
- Hipótese: Distinguir atraso físico de correção percentual antes de otimizar pulsos curtos.
- Risco: Risco: saturação elétrica e transientes.
- Fonte: DUMP/RT_RCDATA(10)__TSTRATEGIATEMPIMORTIDM__0.bin: offset 0x1E8; componente ADV_OFFSET_INJ_GAS; propriedade SerialCode ; DUMP/RT_RCDATA(10)__TSTRATEGIATEMPIMORTIDM__0.bin: offset 0x55; componente ADV_OFFSET_INJ_PETROL; propriedade SerialCode.

## 8. Pressão e temperatura

- Dados a ler: RIF_PRESS_COLL (SC 95), COEFF_PRESS_COLL (SC 96).
- Hipótese: Separar deriva térmica/pressão da curva K usando séries sincronizadas.
- Risco: Risco: conversão física ainda não extraída do Delphi.
- Fonte: DUMP/RT_RCDATA(10)__TSTREAMDATI__0.bin: offset 0x5879; componente RIF_PRESS_COLL; propriedade SerialCode ; DUMP/RT_RCDATA(10)__TSTREAMDATI__0.bin: offset 0x5A0B; componente COEFF_PRESS_COLL; propriedade SerialCode.

## 9. Comutação e retorno seguro

- Dados a ler: RITARDO_CAMBIO (SC 17), TEMPO_RITORNO_BENZINA (SC 62).
- Hipótese: Excluir janelas de troca do cálculo de economia e reconhecer falhas de alimentação.
- Risco: Risco: intervenção indevida nos limiares de segurança.
- Fonte: DUMP/RT_RCDATA(10)__TSTREAMDATI__0.bin: offset 0x12A1; componente RITARDO_CAMBIO; propriedade SerialCode ; DUMP/RT_RCDATA(10)__TSTREAMDATI__0.bin: offset 0x3DDD; componente TEMPO_RITORNO_BENZINA; propriedade SerialCode.

## 10. Cutoff e injeção contínua

- Dados a ler: GIRI_TEMPO_CUTOFF (SC 50), TEMPO_INIEZIONE_CONTINUA (SC 54).
- Hipótese: Remover cutoff da métrica de equivalência; identificar regimes de difícil calibração.
- Risco: Risco: falsos positivos por pacote incompleto.
- Fonte: DUMP/RT_RCDATA(10)__TSTREAMDATI__0.bin: offset 0x3319; componente GIRI_TEMPO_CUTOFF; propriedade SerialCode ; DUMP/RT_RCDATA(10)__TSTREAMDATI__0.bin: offset 0x37D3; componente TEMPO_INIEZIONE_CONTINUA; propriedade SerialCode.

