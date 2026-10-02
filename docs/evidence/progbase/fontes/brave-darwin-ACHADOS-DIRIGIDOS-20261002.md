# Escavação dirigida ProgBase × OMEGAS: novos mecanismos e pontos cegos

Data: **2026-10-02**. Investigação exclusivamente read-only, sem inventário geral, execução em ECU ou alteração no app. **CONFIRMADO-DFM** = componente/SC/dimensão/transformação no recurso; **CONFIRMADO-APP** = código remoto da branch; **INFERIDO** = oportunidade testável; **ABERTO** = sem captura/rotina causal comprovada.

Fontes primárias: Drive DUMP `1SMZqx2Sd1HKooJ5GVCSuYDPvkCn6R0kH`, `RT_RCDATA(10)__TSTREAMDATI__0.bin` (texto convertido `RCDATA_TSTREAMDATI_0.dfm.txt`, ID `1LL-yIuTgghHbtl-3GW5lFf2LOUYJXuf8`), `RT_RCDATA(10)__TAUTOCALDM__0.bin` (texto `RCDATA_TAUTOCALDM_0.dfm.txt`, ID `1OgwRkmKbjxmNDLfbsvHwh_ghYH8CKNQb`) e `TFORMCONFIG` (texto ID `13r-o5iKrDZtzSaOFPU_tZevDTYo-N0P8`). Offsets abaixo são **posições de componente no recurso binário**, rastreáveis em `parametros.json`, não VA do .text. Conversões `ttFormula` confrontadas com a extração estática `README_RELATORIO.md` §7.2 do mesmo executável (Drive ID `1XKjCR1MP9vlHjBS0Z2DE1csTiSzqTGj9`) e `FORMULAS.md`. O relatório é fonte derivada, não prova de firmware.

Autoridade de comparação: GitHub remoto `viluadmcontas2-dot/OMEGAS-V8.2`, `claude/brave-darwin-wuliyo`, SHA de partida `ac7c30cc8f0eeca1e244ce9356689f82784e3528`; arquivos Kotlin citados nas seções.

## 1. Superfície 4 × 5 de características do injetor além dos offsets usuais

**CONFIRMADO-DFM**, `TSTREAMDATI`:

| SC | Componente e offset | Shape | Raw → valor de exibição |
|---:|---|---|---|
| 313, índice 1 | `INJR_GAS_FLOW` @`0x1981B` | U16 escalar dentro de vetor | `raw/4096` |
| 314 | `NORM_TEMP` @`0x19550` | S16, default UI **293** | identidade; Kelvin é hipótese, não unidade comprovada |
| 315 | `NORM_PRESS` @`0x19699` | S16, default UI **1,95** | `raw/1024` |
| 316 | `INJR_TOFS_PTR_H` @`0x19970` | S16 × **4** | `raw/1024` |
| 317 | `INJR_TOFS_PTR_V` @`0x19B30` | S16 × **5** | `raw/1024` |
| 318 | `INJR_TOFS_TBL` @`0x19CFB` | S16, **4 linhas × 5 colunas** | `raw/4096` |

Ainda existem `TEMPO_MORTO_INIETTORI_BENZINA` **SC 125** U16 escalar @`0x7A53`, `TEMPO_MORTO_INIETTORI_GAS` **SC 126** U16 escalar @`0x7B2E`, separados dos vetores assinados de dez elementos `ADV_OFFSET_INJ_PETROL` **SC 242** e `ADV_OFFSET_INJ_GAS` **SC 243** (`TSTRATEGIATEMPIMORTIDM`; ver `FORMULAS.md`).

**CONFIRMADO-APP:** `CalibrationPhysicsFoundation.kt`, `DeadtimeEvidence`/`GasPulsePhysics.activePulse`, só subtrai deadtime quando conhecido, atual e com proveniência; na ausência mantém `UNKNOWN`. **INFERIDO:** estudar se a grade 316–318 é uma superfície de offset contextual que ajuda a separar injetor, ganho da curva e condições físicas. **ABERTO:** identidade física dos eixos H/V, unidade da tabela e aderência à ECU conectada. Não afirmar que a tabela está em ms nem que o intercepto +0,99 ms do modelo de 609 leituras é este tempo morto.

**Teste discriminante:** estimar o resíduo do pulso de gás em diferentes `ΔP` e `T_gás`, com calibração fixa, e comparar contra leituras reais 125/126/242/243/316–318 apenas depois de provar suas unidades. Pare se shape/versão não corresponderem.

## 2. Três famílias de pressão e uma família térmica

**CONFIRMADO-DFM**, `TSTREAMDATI`:

| Mecanismo nomeado | Referência | Coeficientes | Observação |
|---|---|---|---|
| Coletor | `RIF_PRESS_COLL` **SC 95**, U16 ×15 @`0x5879` | `COEFF_PRESS_COLL` **SC 96**, S16 ×15 @`0x5A0B` | Conversão visual identidade; fórmula de atuação não demonstrada |
| Diferencial | `RIF_PRESS_DIFF` **SC 123**, U16 ×15 @`0x7723` | `COEFF_PRESS_DIFF` **SC 124**, S16 ×15 @`0x78B6` | Não é automaticamente igual à curva de coletor |
| Absoluta | `RIF_PRESS_ASS` **SC 223**, máscara U8, ×15 @`0xFC2C` | `COEFF_PRESS_ASS` **SC 224**, S16 ×15 @`0xFDB0` | Eixo com encoding diferente da família diferencial |
| Temperatura de gás | `RIF_TEMP_GAS` **SC 92**, máscara U8, ×10 @`0x55EE` | `COEFF_TEMP_GAS` **SC 93**, máscara U8, ×9 @`0x5738` | **Não** emparelhar 10:9 elemento por elemento sem saber o formato de segmentos |

**CONFIRMADO-APP:** `Mp48Protocol.kt` deriva `pressureDiffBar = gasPressureAbsBar - mapBar`; `PetrolReferenceSelector.kt` transporta contexto ambiental. `CalibrationPhysicsFoundation.kt` declara `K2_PRESSURE=raw/8192` e `K4_GAS_TEMP=raw/32768` de `FREST 0x0A` como **STATIC_ORACLE_CANDIDATE**, não fatores live validados. **INFERIDO:** ajustar separadamente a dispersão do erro por `RPM × MAP × ΔP × T_gás` e distinguir deriva de ambiente de erro da Curva K; não inferir causalidade de uma correlação nem aplicar simultaneamente as três famílias sem identificar a versão/estratégia.

## 3. Limiares de aquisição nativos AutoCal que o caminho observado não lê

**CONFIRMADO-DFM**, `TAUTOCALDM`; valores a seguir são **defaults UI**, nunca readback da ECU:

| SC | Nome / offset | Default de tela | Escala raw → tela |
|---:|---|---:|---|
| 387 | `DIFF_ENG_SPD_THD` @`0x3347` | 400 | identidade |
| 388 | `DELTA_ENG_SPD_THD` @`0x3726` | 200 | identidade |
| 389 | `DIFF_MNFLD_PRESS_THD` @`0x347A` | 0,5 | `raw/1024` |
| 390 | `DELTA_MNFLD_PRESS_THD` @`0x30A2` | 0,05 | `raw/1024` |
| 391 | `DIFF_PETR_TINJ_T_THD` @`0x35D0` | 4,0 | `raw/512` |
| 392 | `DELTA_PETR_INJ_T_THD` @`0x3200` | 1,0 | `raw/512` |

Também existem `LIMIT_PRESSURE_MIN` **SC 361** (default 0,05, S16 `raw/1024`, @`0x1C95`) e `LIMIT_PRESSURE_MAX` **SC 362** (default 0,95, mesma representação, @`0x212D`); `MAX_RPM_FOR_AUTOCAL` **SC 378** (U16, default 3000, @`0x2A3D`).

**CONFIRMADO-APP:** a coleção explícita `AutoCalProtocol.READ_ONLY_FIELDS` não inclui 361/362 ou 387–392; `CompositeCalibrationReader.readAtSessionStart` lê contagem, eixos, Curva K e Mapa K, não esses limiares. É uma lacuna **nas duas rotas verificadas**, não declaração global de ausência no APK. **INFERIDO:** observá-los em snapshot opcional para confrontar gates nativos de aquisição e critérios de coerência do refinador. Os nomes DIFF/DELTA não provam duração, operação relacional nem semântica da janela. Não substituir gates do OMEGAS pelos defaults de fábrica.

## 4. O indicador de nível do app não é uma curva de sensor comprovada

**CONFIRMADO-DFM:** `TIPO_SENSORE` **SC 36** @`0x25B8`; `RIF_SENSORE` **SC 37**, quatro referências @`0x2672`; filtros FAST/SLOW **SC 276**, `raw/32768`, defaults UI **1,0 e 0,015**, @`0x16F1B`/`0x1709A`; vetor **SC 300**, defaults histerese e LEDs **[3,12,37,62,87]**, @`0x17224`–`0x17772`. `TFORMCONFIG.ComboSensore` enumera A.E.B., 0–90 ohm, Landi Renzo, Sensata HD, Cartesio e customizações; `CheckRiconoscimentoPieno` existe (`Visible=False` no DFM), sem predicado da ECU reconstruído.

**CONFIRMADO-APP:** `Mp48Protocol.kt` lê `levelRaw` U8 no offset 13 da telemetria; `Mp48TelemetryScale.levelPercentage` calcula `floor((255-raw)*100/255)`, independentemente do perfil e referências SC 36/37. **INFERIDO:** tratá-lo como proxy, e não como volume comprovado em todos os perfis; parear raw, tipo, referências, limiares e exibição original com abastecimentos medidos. `TANK_VOL` **SC 313**, índice 0 @`0x18FD7`, tem default UI **40** e escala `1000×raw/32768`, mas capacidade nominal NÃO equivale a gás consumível nem prova economia. O índice 1 do mesmo SC corresponde a `INJR_GAS_FLOW`; não decodificar SC 313 inteiro como escalar de tanque.

## 5. Telemetria do trilho não é a pressão de tanque MGLEV

**CONFIRMADO-DFM**, `TSTREAMDATI`: `MGLEV_DIAG_ERR` **SC 325** @`0x18D2E`; `MGLEV_TANK_PRESS` **SC 326**, S16 escalar @`0x18DEC`; `MGLEV_DATA` **SC 327**, S16 ×3 @`0x18EC3`; `MGLEV_ERR_ST` **SC 328**, três posições (largura exata pendente) @`0x1875E`.

**CONFIRMADO-APP:** `Mp48Protocol.decodeTelemetry` usa `gasPressureRaw` do offset 14 como pressão absoluta do trilho segundo `Mp48TelemetryScale`, e `levelRaw` do offset 13. **INFERIDO:** não atribuir a pressão do trilho à pressão do cilindro nem deduzir vazamento/consumo a partir dela. Investigar captura somente leitura dos SC 325–328 por família de firmware, sem supor que existam em todas as ECUs.

## 6. Neutralidade do Mapa K: código do OMEGAS versus exibição ProgBase

**CONFIRMADO-APP:** `CalibrationPhysicsFoundation.kt`, `PhysicsEvidenceMatrix` e `PhysicsFactors.k1FromMapRaw` declaram o modelo `K1=raw/128` e `128` neutro; `CalibrationPhysicsFoundationTest.kt` verifica `k1FromMapRaw(128)==1,0`. **CONFIRMADO-DFM:** `MAP_K` **SC 84** @`0x4ADF`, matriz 13×12, máscara 255 e transformação de exibição identidade. Não há contradição obrigatória entre exibição bruta e semântica física, mas **teste de implementação não demonstra fórmula universal de firmware**. Rastrear a captura/readback por firmware que fundamenta o E4 declarado no app; não trocar 128 por 100 baseado na aparência de uma tela nem presumir multiplicador a partir do DFM sozinho.

## 7. O modelo de 609 pontos não comprova economia nem identifica mecanismos separadamente

Regressão **informada pelo proprietário**, não recalculada aqui: `tgas ≈ 2,29 × tpetrol × K_interpolado + 0,99 ms`, 609 leituras GNV, R² 0,98. **INFERIDO:** ganho e intercepto agregam possíveis efeitos de mapa, deadtime, condições e injetor; não identificam causalmente nenhum deles. O objetivo primário documentado em `AGENTS.md` é comparar a **resposta de gasolina em RPM × MAP comparáveis** nos dois combustíveis, e não impor igualdade literal entre os tempos físicos das válvulas.

Teste offline proposto: (1) particionar por sessão/geração de calibração/combustível e retirar cutoff/transições; (2) estabelecer base `tgas=a·tpetrol·K+b`; (3) comparar resíduo condicionado ao Mapa K físico, pressão diferencial e temperatura, sem assumir seus coeficientes; (4) validação leave-one-session-out, não split aleatório de pontos temporalmente correlacionados; (5) investigar primeiro a mudança de intercepto em pulsos curtos; (6) medir separadamente a economia por observações de consumo/abastecimento comparáveis e sua incerteza.

## Trabalho que realmente agrega valor, com gates de evidência

| Possibilidade | Comprovação mínima antes de construir | Condição de parada |
|---|---|---|
| Snapshot SOMENTE LEITURA de 125/126, 314–318, 361/362, 387–392, 325–328 | Captura individual de request/ACK/payload, tamanho, ordem de bytes e versão; guardar `raw`, `usbSessionId`, `timestamp`, perfil e `UNKNOWN` quando faltante | NACK ou comprimento diferente: não insistir nem adivinhar formato |
| Refino de resíduos por contexto e grade de injetor | Explicar ganho fora da sessão usada para ajustar e descartar covariáveis confundidas | Sem escala/unidade da grade: só diagnóstico, sem fator ativo |
| Comparação de gates nativos e critérios do OMEGAS | Entender a semântica temporal de DIFF/DELTA por código/captura e correlacionar aceitações por zona | Não substituir regra validada por default do DFM |
| Nível por sensor e reconhecimento de cheio | SC 36/37/276/300 + LEDs do ProgBase e abastecimentos | Sem curva/geom. calibrada: apresentar proxy, não litros precisos |
| MGLEV diagnóstico separado | Mensagens e famílias SC 325–328 reproduzidas passivamente | Não derivar tanque de pressão do trilho |
| Origem física do `K1=raw/128` | Captura E4 identificável e associada à família do firmware | Não promover unidade de teste a prova nativa universal |

**Limites desta escavação:** leitura de DFM/relatório estático e código remoto; nenhum novo Portmon, teste com ECU, validação das 609 linhas, cálculo de economia ou disassembly causal adicional do firmware. Não instalar leitor automático destes SC até validar contrato e impacto no scheduler MP48.
