# ProgBase: fórmula de gás, nível e leitura passiva (investigação dirigida)

**Data:** 2026-10-02. **Escopo:** somente as três perguntas do proprietário; não é uma revisão de `parametros.json` nem confirmação de uma estratégia de escrita. **Estado global:** interpretação de metadados de tela e do protocolo, com fórmula interna da ECU **não identificada**.

**Fontes verificadas:** Drive/DUMP (`1SMZqx2Sd1HKooJ5GVCSuYDPvkCn6R0kH`), `ProgBase.exe.Dump.bin` SHA-256 `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`, `RT_RCDATA(10)__TSTREAMDATI__0.bin` SHA-256 `d55e310ec32e2154297c84ecc986f085647cd491c9d3ff2293c0de5b595b2250`, `RT_RCDATA(10)__TSTRATEGIATEMPIMORTIDM__0.bin` SHA-256 `7d95a63dfe34469e7bd3e371176b2c22f8934e097681fb89feb27c129a790fbc`, `RT_RCDATA(10)__TFORMCONFIG__0.bin`. **Offsets de DFM são posições zero-based dentro do recurso; endereços de desmontagem abaixo são VA (ImageBase 0x00400000), não offsets de arquivo.** O executável é PE32 x86, `.text` RVA 0x1000, VMA 0x00401000, raw offset 0x600.

## 1. Tempo de injeção do gás

### 1.1 Hipótese estatística do proprietário: INFERIDO, não fórmula identificada

Para 609 observações em regime GNV, foi comunicado o ajuste empírico

```text
gas_ms_observado ≈ 2,29 × petrol_ms × K_interpolado(petrol_ms) + 0,99 ms
R² informado = 0,98
```

Trata-se de **evidência observacional fornecida pelo proprietário**; o corpus das 609 linhas não integrou este DUMP, portanto o ajuste e seu R² **não foram recalculados nesta etapa**. O intercepto de 0,99 ms **não pode ser atribuído diretamente** a tempo morto de gás, e 2,29 **não pode ser identificado isoladamente** como ganho do Mapa K, de injetor, de pressão ou de temperatura. A igualdade desejada no projeto refere-se à resposta de injeção de gasolina em regimes equivalentes, não à igualdade obrigatória entre os dois pulsos físicos.

### 1.2 Componentes relevantes: CONFIRMADO como metadados, não como equação da ECU

| Componente | Evidência DFM | Tipo/forma declarada e conversão visível | O que NÃO prova |
|---|---|---|---|
| `ADV_OFFSET_INJ_PETROL` (SC 242 / 0x00F2) | `TSTRATEGIATEMPIMORTIDM`, nome @0x61, `DataMask` @0x76, `SerialCode` @0x82, `Signed` @0x90, `ArrayDimension` @0xC4, `Coeffs` @0x11E | 10 entradas, máscara 0xFF, Signed=true, DataLength ausente (S8 **inferido** pelo default de um byte); `Coeffs=[1,0,0,1]` (identidade), padrão dez zeros | Unidade física (ms, ticks etc.), eixo e posição do offset na fórmula |
| `ADV_OFFSET_INJ_GAS` (SC 243 / 0x00F3) | `TSTRATEGIATEMPIMORTIDM`, nome @0x1F4, `SerialCode` @0x212, `Signed` @0x220, `ArrayDimension` @0x254, `Coeffs` @0x2AF | Mesmos 10 elementos, máscara 0xFF, Signed=true, identidade, dez zeros | Não identifica o intercepto 0,99 ms da regressão |
| `MAP_K` (SC 84 / 0x0054) | `TSTREAMDATI`, nome @0x4AEB, `SerialCode` @0x4AFC, `ColCount` @0x4B09, `RowCount` @0x4B14, `Coeffs` @0x4B57 | matriz de 13 linhas × 12 colunas; DataMask=255, DataLength não informado; valores U8 **inferidos**; transformação de exibição identidade | Não prova fator multiplicativo nem que 100 = neutro. Os defaults em zero no DFM tampouco são um mapa de ECU medido |
| `RIF_PRESS_COLL` (SC 95 / 0x005F) | `TSTREAMDATI`, nome @0x5885, `DataLength` @0x5893, SC @0x58AE, dimensão @0x58BB, `Coeffs` @0x590A | 15 × U16, DataMask=65535, conversão identidade | Não estabelece se o eixo está em bar ou contagens internas |
| `COEFF_PRESS_COLL` (SC 96 / 0x0060) | `TSTREAMDATI`, nome @0x5A17, `DataLength` @0x5A27, SC @0x5A42, `Signed` @0x5A4F, dimensão @0x5A57, `Coeffs` @0x5AA7 | 15 × S16, DataMask=65535, conversão identidade | Não estabelece normalização, interpolação nem se representa fator, delta ou percentual |

`MUL_ACT` (SC 353 / 0x0161): **CONFIRMADO no código atual do app**, `KFactorProtocol.kt`, como curva Q14 (`K=raw/16384`) e eixo `PETR_INJ_TBP` (`ms=raw/512`). A cardinalidade observada pelo aplicativo é 30, enquanto o recurso DFM estático registra configuração diferente, conforme `LACUNAS.md`. Não transpor o shape do formulário diretamente para o firmware conectado.

### 1.3 O que a desmontagem efetivamente demonstrou

- **CONFIRMADO:** `TLinearTransformation.Transform`, VA **0x009818F0**, instruções `fmul/fadd` em 0x00981911–0x00981920 e 0x00981937–0x00981949, executa transformação genérica de exibição `y=(a*x+b)/(c*x+d)` quando habilitada, com tratamento de denominador nulo. Essa **não** é a fórmula do pulso de gás.
- **CONFIRMADO:** `TAebProtocol.GetNumber` VA **0x00924994** e `TAebProtocol.GetVector` VA **0x00925258** existem e manipulam transporte/elementos (por exemplo, ramificações por largura/sinal em 0x00925435–0x0092546B). Não se encontrou nelas cálculo de tempo de injetor.
- **CONFIRMADO:** exportação de inicialização de `Strategiatempimorti_dm`, VA **0x00523B4C**; não calcula gás. O DFM desse módulo contém dois vetores de offset, não uma implementação do tempo de injeção.
- **NÃO DETERMINADO:** rotina do firmware MP48 que compõe `petrol_ms`, `MUL_ACT`, `MAP_K`, offsets, pressão, temperatura e demais compensações. O ProgBase é cliente de configuração da ECU; não assumir que o executável do PC contenha o algoritmo do firmware. Nenhum endereço de suposta fórmula ECU é fornecido sem vínculo demonstrado.

**Conclusão da pergunta 1:** a única fórmula numérica disponível para `gas_ms` continua sendo o **modelo estatístico INFERIDO** do proprietário, com os limites acima. Escalas físicas internas dos offsets e da correção de pressão/temperatura **não foram confirmadas**; não é legítimo completar a equação com fatores fabricados.

## 2. Nível do cilindro e LEDs

### 2.1 Ligações e padrões que realmente aparecem nos recursos

| Elemento | Fonte direta | Informação CONFIRMADA |
|---|---|---|
| `TIPO_SENSORE`, SC 36 | `TSTREAMDATI`: nome @0x25C4, SC @0x25DC, máscara @0x25D0 | um valor, máscara 255; coeficientes identidade @0x2627 |
| `RIF_SENSORE`, SC 37 | `TSTREAMDATI`: nome @0x267E, SC @0x2695, dimensão @0x26A2, default @0x273A | quatro referências em ordem de formulário **reserva, 1/4, 2/4, 3/4**, vinculadas à tela; DFM traz vetor default `[0,0,0,0]`, **não** curvas calibradas dos tipos de sensor |
| Seletor dos tipos | `TFORMCONFIG` binário, `ComboSensore` @0x11661, `Items.Strings` @0x1176C (aprox.) | opções na ordem de UI: `A.E.B.`, `0 - 90 ohm`, `Landi Renzo`, `Sensata HD`, `Cartesio`, `Non standard`, `Non standard invertito`. A correspondência exata índice UI → valor gravado em SC 36 exige seguir `ComboSensoreKeyPress` |
| Referências visuais | `TFORMCONFIG`, `LabelSensore1..4` @0x11917–0x11A85 | captions `Riserva`, `1/4`, `2/4`, `3/4` |
| Reconhecimento de cheio | `TFORMCONFIG`, `CheckRiconoscimentoPieno` @0x13B0E | checkbox e evento `CheckRiconoscimentoPienoClick`; predicado, temporização e efeitos não identificados |
| Filtro FAST, SC 276, índice implícito 0 | `TSTREAMDATI`, `LO_PASS_FILT_CON_FAST` @0x16F27; `RowIndex` não definido; `Coeffs` @0x1704F | U16, `ttFormula`, coeficientes [1,0,0,32768], exibição **raw/32768**, default exibido **1,0**, precisão 0,0001 |
| Filtro SLOW, SC 276, índice 1 | `LO_PASS_FILT_CON_SLOW` @0x170A6; `RowIndex=1` @0x17183; `Coeffs` @0x171D9 | U16, raw/32768, default exibido **0,015**, precisão 0,0001 |
| Histerese + LEDs, SC 300 | `TSTREAMDATI`: `ISTERESI_RIACCENSIONE` @0x17230; `SOGLIA_LED_1..4` @0x17382, 0x174D6, 0x1762A, 0x1777E | cinco subcampos U8 de conversão identidade. `RowIndex` implícito 0 para histerese, explícito 1..4 para LEDs. Valores **padrão do DFM**: `[3,12,37,62,87]`. Isso não prova a comparação ou a direção dos limiares |

### 2.2 Tabelas padrão por tipo: estado da prova

| Opção da UI | Presença confirmada | Quatro referências padrão próprias (raw 0–255) |
|---|---|---|
| A.E.B. | Sim, `ComboSensore.Items.Strings` | **NÃO DETERMINADAS** |
| 0–90 ohm | Sim | **NÃO DETERMINADAS** |
| Landi Renzo | Sim | **NÃO DETERMINADAS** |
| Sensata HD | Sim | **NÃO DETERMINADAS** |
| Cartesio | Sim | **NÃO DETERMINADAS** |
| Non standard / invertido | Sim | Valores personalizados/inversão **não reconstruídos** |

Não confundir `RIF_SENSORE.DefaultValue=[0,0,0,0]` (default estrutural no formulário) com a tabela física fornecida dinamicamente quando o tipo é selecionado. Também não confundir os cinco padrões do SC 300 com curvas padrão por sensor.

### 2.3 Sequência operacional: CONFIRMADO versus INFERIDO

1. **CONFIRMADO como interfaces:** existem tipo SC 36, quatro referências SC 37, filtros SC 276 e cinco limiares/histerese SC 300; a telemetria MP48 atual oferece `levelRaw` (byte de offset 13 do payload de 34 bytes em `Mp48Protocol.kt`).
2. **INFERIDO, sequência não desmontada:** a escolha do tipo pode selecionar uma curva/tabela de calibração ou uma orientação (normal/invertida). Falta associação entre enum real SC 36 e padrões numéricos.
3. **INFERIDO, operação exata ausente:** o valor é filtrado em algum estágio usando FAST/SLOW; os coeficientes 1,0 e 0,015 estão demonstrados, mas não a equação temporal, critério de troca do filtro ou unidade do sinal filtrado.
4. **INFERIDO, interpolação não comprovada:** após orientação/calibração, referências de reserva/1⁄4/2⁄4/3⁄4 podem definir regiões ou interpolação para exibir nível. Sem referência de cheio, curva por tipo e código do evento, **não há percentual ProgBase reproduzível ponto a ponto**.
5. **CONFIRMADO valores; INFERIDO lógica:** `[3,12,37,62,87]` são os defaults DFM para histerese + LEDs. A regra exata de acender/apagar, inclusive qual limiar recebe a histerese e o tratamento de cheio, não foi estabelecida.
6. **Cuidado com o aplicativo existente:** `Mp48TelemetryScale.levelPercentage` implementa `floor((255 - raw)*100/255)`. **CONFIRMADO como regra Kotlin atual; NÃO confirmado como algoritmo do ProgBase para todos os tipos de sensor.** Comparar telemetria, LEDs oficiais, tipo e referências antes de adotá-la como verdade física.

## 3. Como ler sem escrever: contrato dos objetos e hipótese de quadros

As formas abaixo vêm diretamente do DFM; quando `DataLength` está ausente, a classificação `U8/S8` decorre de máscara 255 e `Signed` (ou ausência dele) e está assinalada como **INFERIDO** até verificar o default da classe no código/uma captura. Escala = transformação raw→exibido do **formulário**, não necessariamente unidade física. `DataLength=2` e máscara 65535 são **CONFIRMADOS** onde explícitos.

| SC decimal / hex | Objeto / conteúdo lógico | Leitura (tipo × elementos) | Raw → exibição | Fonte DFM / estado |
|---|---|---|---|---|
| 36 / 0x0024 | `TIPO_SENSORE` | U8 × 1 (largura INFERIDA) | identidade, enum não fechado | `TSTREAMDATI` @0x25C4/0x25DC |
| 37 / 0x0025 | `RIF_SENSORE` | U8 × 4 (largura INFERIDA) | identidade; referências de sensor em raw, unidade física não provada | @0x267E/0x2695/0x26A2 |
| 84 / 0x0054 | `MAP_K` | U8 × (13 linhas × 12 colunas), largura INFERIDA | identidade; não declarar 100% neutro | @0x4AEB/0x4B09/0x4B14 |
| 95 / 0x005F | `RIF_PRESS_COLL` | **U16_LE × 15** | identidade `x`, unidade desconhecida | @0x5885/0x5893/0x590A |
| 96 / 0x0060 | `COEFF_PRESS_COLL` | **S16_LE × 15** | identidade `x`, função matemática desconhecida | @0x5A17/0x5A27/0x5A4F/0x5AA7 |
| 242 / 0x00F2 | `ADV_OFFSET_INJ_PETROL` | S8 × 10 (largura INFERIDA, Signed CONFIRMADO) | identidade `x`; **não converter em ms** | `TSTRATEGIATEMPIMORTIDM` @0x61/0x82/0x90/0x11E |
| 243 / 0x00F3 | `ADV_OFFSET_INJ_GAS` | S8 × 10 (idem) | identidade `x`; **não converter em ms** | `TSTRATEGIATEMPIMORTIDM` @0x1F4/0x212/0x220/0x2AF |
| 276 / 0x0114 | vetor `VectAgaslevKFilter`: FAST índice 0, SLOW índice 1 | **U16_LE × 2** (dois elementos indicados por subcomponentes) | ambos `raw/32768`; defaults exibidos `[1,0;0,015]` | `TSTREAMDATI` @0x16F27–0x17223; `RowIndex=1` @0x17183 |
| 300 / 0x012C | vetor `VectRiaccLed`: histerese índice 0; LED1..4 índices 1..4 | U8 × 5 (largura INFERIDA) | identidade `x`; defaults DFM `[3,12,37,62,87]` | @0x17230–0x178C5; `RowIndex` 1..4 |
| 313 / 0x0139 | vetor `LandiConnect`: `TANK_VOL` índice 0; `INJR_GAS_FLOW` índice 1 | **U16_LE × 2** (dois subcampos encontrados; dimensão total do protocolo não comprovada) | `TankVol = 1000×raw/32768` (padrão exibido 40); `InjrGasFlow=raw/4096` | @0x18FE3, `Coeffs` @0x190F8; @0x19827, `RowIndex=1` @0x198E6, `Coeffs` @0x19925 |

**Comandos de consulta para análise offline, não execução:** no `AutoCalProtocol.kt` desta branch, `READ_SCALAR=0x09` corresponde a `[09, loSC, hiSC]`, `READ_VECTOR=0x29` a `[29, loSC, hiSC]`, `READ_INDEXED=0x0A` a `[0A, loSC, hiSC, index]`; `Mp48Protocol.frame` acrescenta checksum de soma módulo 256. Para `MAP_K`, `Mp48Protocol.readKRow(row)` já utiliza `[2A,54,00,row]` e são 13 consultas, `row=0..12`, com 12 colunas esperadas por linha. **CONFIRMADO como comportamento do código atual do OMEGAS; aplicação a todos os demais SCs é INFERIDA por analogia, não confirmada por captura individual do ProgBase.**

| Objeto | Corpo de consulta proposto (sem checksum) | Shape de resposta que deve ser exigido/validado antes de decodificar |
|---|---|---|
| 36 | `09 24 00` | 1 byte |
| 37 | `29 25 00` | 4 bytes |
| 84 | `2A 54 00 rr`, rr=00..0C | 12 bytes por linha, treze linhas |
| 95 / 96 | `29 5F 00` / `29 60 00` | 30 bytes cada |
| 242 / 243 | `29 F2 00` / `29 F3 00` | 10 bytes cada |
| 276 | `29 14 01` | **hipótese: 4 bytes** para vetor de dois U16 |
| 300 | `29 2C 01` | **hipótese: 5 bytes** para vetor de cinco U8 |
| 313 | `29 39 01` | **hipótese: ao menos 4 bytes** para dois U16; verificar comprimento real |

**Alternativa indexada somente após validação da ECU:** `0A 14 01 ii` (`ii=00/01`), `0A 2C 01 ii` (`ii=00..04`), `0A 39 01 ii` (`ii=00/01`). Não confundir `RowIndex` do objeto DFM com garantia de que o comando `0x0A` aceite exatamente o mesmo índice em toda família/firmware. Validar ACK `0x53`, comprimento, endianness e versão; leitura é passiva, mas não habilitar polling desconhecido em ECU física sem prova de contrato.

### Tentativas efetivamente realizadas, e ponto de parada

1. Fetch autenticado do dump PE original no Drive, **12.643.840 bytes**, com SHA-256 verificado acima. A seção `.text` foi identificada por `objdump -h`; não houve falha de limite de tamanho nesse caminho. Os DFM `TSTREAMDATI` e `TSTRATEGIATEMPIMORTIDM` foram obtidos integralmente e suas propriedades `Coeffs`/defaults decodificadas dos valores Extended 80-bit.
2. `objdump -p`: correlação de exportações/endereços de `TLinearTransformation.Transform`, `TAebProtocol.GetNumber`, `GetVector` e módulos `Strategiatempimorti_*`. `objdump -d -Mintel --start-address=0x9818f0 --stop-address=0x981960 ProgBase.exe.Dump.bin` confirma a transformação genérica; `--start-address=0x925258 --stop-address=0x925570` inspeciona `GetVector`; `--start-address=0x523b4c --stop-address=0x523b5c` inspeciona init do módulo de tempo morto.
3. Inspeção do binário `TFORMCONFIG` no offset 0x1176C encontra as opções do sensor; o DFM de `RIF_SENSORE` não contém as curvas por perfil. A callback `ComboSensoreKeyPress` e `CheckRiconoscimentoPienoClick` não foram vinculadas a uma rotina de interpolação/decisão com prova suficiente.
4. **STOP CONDITION:** não foi localizada rotina demonstravelmente responsável pelo cálculo ECU de `gas_ms` nem rotina completa de nível por sensor. Não completar a fórmula nem inventar a tabela a partir de nomes ou de regressão. Próximas evidências necessárias: firmware/rotinas ECU ou Portmon de leituras e seleção de tipos, logs sincronizados de pressão/temperatura/t_gas, e XREF de callbacks do `TFORMCONFIG`. Nenhuma escrita na ECU foi feita.
