# Backlog: o que o original envia e o OMEGAS não usa (gerado — não editar à mão)

Critério de "usa": app_menciona = o nome ou o SerialCode (>= 0x100) aparece em app/src/main (*.kt, *.js, *.json, *.html). Recomendações são INFERIDO (decisão de projeto), os fatos vêm de `comandos.json`/`vetores.json`.

Classes de comando: **A** leitura/diagnóstico sem risco · **B** escrita de calibração · **C** atuadores/bancada · **D** firmware/boot/baud (nunca no app) · **E** legado/sem valor. Contagem: A=4, B=4, C=2, D=9, E=3.

## 1. Comandos

| id | comando | quadro | no log | classe | prioridade | risco | recomendação |
|---|---|---|---|---|---|---|---|
| `wake` | Sonda de descoberta (00 a 38400 baud) | `00` (1 byte; sem checksum e SEM eco) | 2 | A | baixa | nenhum | Só serviria para descobrir qual adaptador tem ECU. O app já abre o USB pelo VID/PID; não precisa. |
| `getnumber_2` | GetNumber + linha + coluna (elemento de matriz) | v2: `0B lo hi linha coluna ck`; v0/v1: `0A end linha coluna ck` (1º byte extra = linha, 2º = coluna) | 0 | A | baixa | nenhum | Ler 1 célula de matriz sem baixar a linha. Otimização; o app lê a linha `2A`. |
| `getbyaddr` | GetByAddress (memória por endereço) | `6A flags a_lo a_hi ck` (endereço 16 bits) ou `6C flags a0 a1 a2 a3 ck` (32 bits). flags: bit7=valor 16 bits, bit5=endereço 32 bits | 0 | C | baixa | medio | Leitura de memória por endereço físico. Útil só para engenharia; não expor ao usuário. |
| `elemdim` | GetElementDimension | v2: número `09 lo hi`, vetor `0A lo hi 00`, matriz `0B lo hi 00 00` (v0/v1: 08/09/0A) | — | A | baixa | nenhum | Descobrir o tamanho de um parâmetro. No fio é um GetNumber+idx(0); o app já conhece os tamanhos. |
| `setbyaddr` | SetByAddress | `73 flags a_lo a_hi val` (8 bits/end16), `74 ... v_lo v_hi`, `75 flags a0..a3 val`, `76 flags a0..a3 v_lo v_hi` | 0 | D | nenhuma | alto | Escrita em endereço físico arbitrário. NUNCA expor. |
| `sendcommand` | SendCommand genérico (b0 = nº de bytes de carga, b1 = código) | `n cmd c1..cn ck` com n<7; n>=7: `07 cmd (n+1) c1..cn ck` | — | B | media | variavel | Os códigos 0E,10,11,13,14,15,21,26,27,28 só aparecem nas telas Calibração/Diagnóstico/Config/LandiConnect. (pelo NOME do evento, INFERIDO) 0x21 = teste de saída ligar/desligar (TFormDiagnosi.PanelTestOn/OffClick); 0x27 = aquisição de injetor/sequência e tacômetro (TFormConfig.CheckInjAcquisitionClick, ButtonSequenceAcquisitionClick, EditRpmTachimetroKeyPress); 0x28 = botão Reset (TFormLandiConnect.ButtonResetClick). Nada disso está no log; capturar cada botão antes de implementar. |
| `cmd_12` | Comando 0x12 (carga 00) | `01 12 00 13` | 2 | B | media | baixo | O original manda `01 12 00` antes de todo Disconnect. Efeito desconhecido; capturar uma sessão só com esse comando (e comparar `48 01`/`48 0B` antes e depois) antes de decidir. |
| `cmd_13` | Comando 0x13 (sem carga) | `00 13 13` | 6 | B | baixa | desconhecido | Tecla na tela de Calibração (Taratura). Só se o app ganhar a tela de Taratura. |
| `cmd_14` | Comando 0x14 (sem carga) — polling da calibração | `00 14 14` | 4693 | A | media | nenhum | Polling da tela de Calibração (resposta constante `02 00` em 4 693/4 693). Pode ser o "estado do modo taratura"; capturar com o modo ligado/desligado para ver se o valor muda. |
| `cmd_04` | Comando 0x04 (carga 54) | `01 04 54 59` | 3 | E | nenhuma | nenhum | A ECU capturada responde NAK (não suportado). Ignorar. |
| `setduty` | SetDuty (teste de saída por PWM) | `02 05 id valor ck` (valor >= 0) ou `01 05 id ck` (valor < 0) | 0 | C | baixa | medio | Teste de saída por PWM (aciona atuador). Só com tela de diagnóstico dedicada e confirmação. |
| `setbaud` | SetEcuBaudRate (hardware antigo) | `02 03 hi lo ck`, valor = 8 000 000 / (baud x 16) em 16 bits, ALTO primeiro | 0 | D | nenhuma | alto | Troca de baud para flash. Nunca no app. |
| `setnewbaud` | SetNewEcuBaudRate (hardware novo) | `02 19 hi lo ck`, valor = baud / 2 em 16 bits, ALTO primeiro; baud em [1200, 115200] | 0 | D | nenhuma | alto | Troca de baud para flash. Nunca no app. |
| `autocal_05` | Ação AutoCal 01 24 05 | `01 24 05 2A` | 0 | B | baixa | desconhecido | Enviado após gravar dois vetores de flags por ponto; nunca visto. Capturar antes de usar. |
| `cancflash` | Cancflash (apaga flash) | `00 09 09` | 0 | D | nenhuma | alto | Apaga a flash. Nunca no app. |
| `exitboot_nf` | ExitBootNotForced | `00 0A 0A` | 0 | D | nenhuma | alto | Sai do bootloader. Nunca no app. |
| `exitboot` | ExitBoot (forçado) | `00 0B 0B` | 0 | D | nenhuma | alto | Sai do bootloader (forçado). Nunca no app. |
| `progflash` | Progflash (entra em programação) | `93 93` (1 byte + ck) | 0 | D | nenhuma | alto | Entra em programação de flash. Nunca no app. |
| `endflash` | Endflash | `85 85` | 0 | D | nenhuma | alto | Encerra programação de flash. Nunca no app. |
| `flash_linha` | Linha de firmware (S-record em texto -> binário) | `53 31 cnt addr_hi addr_lo dados.. srec_ck` (a linha ASCII do arquivo vira bytes; 1ª letra e tipo vão como ASCII, o resto em pares hexa). EOF: `53 39 03 FF FF FE`. Sem checksum adicional do protocolo. | — | D | nenhuma | alto | Grava firmware. Nunca no app. |
| `v01_getnumber` | GetNumber v0/v1 (endereço de 1 byte) | `08 end ck` / v1 estendido `08 FF (end+1) ck` | 0 | E | nenhuma | nenhum | ECUs de protocolo 0/1; a capturada é v2. |
| `v01_getvector` | GetVector v0/v1 | `28 end ck` | 0 | E | nenhuma | nenhum | ECUs de protocolo 0/1; a capturada é v2. |

## 2. Comportamentos de enlace: original x OMEGAS

| item | original | OMEGAS | referência | situação |
|---|---|---|---|---|
| Retries | até 5 reenvios do mesmo quadro (6 tentativas), depois diálogo de reconexão (se ligado) | sem retry cego em NAK não-retentável; reconecta pelo ciclo de vida USB (ResponseDrivenEcuEngine.kt, UsbRecoveryPolicy.kt) | ResponseDrivenEcuEngine.kt:25-26 | diferente-de-proposito |
| NAK `CA 01 10` / `CA 01 08` | testa o bit 0x10 do 2º byte de status (qualquer outro valor com bit 0x10 = não suportado; sem o bit = retry) | compara igualdade com 0x10 e 0x08; outros códigos viram EXTENDED_UNKNOWN | UsbProtocolReply.kt:13-23 | lacuna-pequena |
| Purge antes de cada comando | IOCTL_SERIAL_PURGE (TX+RX) antes de TODO WRITE (20 290/20 290) | purge antes de cada transação (purgeBefore=true por padrão) | UsbSerialManager.kt:353,372 | igual |
| Eco | compara o eco byte a byte; divergência -> ComFail 8 -> retry | readExact(request.size) e compara o eco inteiro | UsbSerialManager.kt:375-377 | igual |
| Timeout do 1º byte da resposta | 1000 ms (+200 do driver) | timeoutMs padrão 1800 ms | UsbSerialManager.kt:352 | diferente-de-proposito |
| Tamanho máximo de resposta | GetPacket lê o tamanho como byte COM SINAL (<= 127 B); Get* confere n com o tamanho do objeto | rejeita n > 192 | UsbSerialManager.kt:385 | lacuna-pequena |
| Resposta `96` (ECU ocupada) | espera 400 ms e reenvia (até 5x) | sem tratamento específico | UsbProtocolReply.kt | lacuna-nao-observada-no-fio |
| Sequência de encerramento | `01 12 00` e depois `00 01 01` (Disconnect, uma tentativa, sem retry) | só Mp48Protocol.CMD_DISCONNECT | Mp48Protocol.kt:18 | lacuna-pequena |
| Escrita com porta fechada | SetNumber/SetVector devolvem VERDADEIRO sem enviar nada | escrita falha explicitamente; gravado só após ACK + readback (AGENTS.md) | AGENTS.md invariantes | diferente-de-proposito |
| Ordem do handshake | ident `00 02` -> Connect `01 00 3A` -> versão `00 25` -> (REGISTRO_EE `09 02 00`) -> ident de novo | CMD_INIT_1 (`00 02 02`), CMD_INIT_2 (`01 00 3A`), depois leitura de versão | ResponseDrivenEcuEngine.kt:341-369 | igual |

## 3. Parâmetros (TStreamDati, 314)

| grupo | quantidade | leitura |
|---|---|---|
| lido pelo original e mencionado no app | 27 | o app já conhece |
| **lido pelo original e NÃO mencionado no app** | 142 | candidatos de backlog (lista abaixo) |
| só NAK na captura | 53 | não existe nesta ECU/firmware |
| nunca pedido na captura | 92 | telas/ações que o dono não abriu; DESCONHECIDO se existem na ECU |

### 3.1 Lidos pelo original e não mencionados no app

| SC | nome | forma | tipo | dim | leituras | escritas do original | só leitura |
|---|---|---|---|---|---|---|---|
| 0x0000 | `SERIAL_NUMBER` | vetor | u8 | 20 | 9 | 0 | não |
| 0x0001 | `REGISTRO_INIT` | numero | u8 | 1 | 1 | 0 | não |
| 0x0002 | `REGISTRO_EE` | numero | u8 | 1 | 3 | 0 | não |
| 0x0004 | `TIPO_LAMBDA` | numero | u8 | 1 | 3 | 0 | não |
| 0x0005 | `RIF_LAMBDA` | numero | u8 | 1 | 3 | 0 | não |
| 0x0006 | `RITARDO_SONDA` | numero | u8 | 1 | 3 | 0 | não |
| 0x0007 | `TEMPO_LBD_FREDDA` | numero | u8 | 1 | 3 | 0 | não |
| 0x0008 | `RIF_SUP_LAMBDA_FREDDA` | numero | u8 | 1 | 3 | 0 | não |
| 0x0009 | `RIF_INF_LAMBDA_FREDDA` | numero | u8 | 1 | 3 | 0 | não |
| 0x000A | `RIF_SUP_LAMBDA_CALDA` | numero | u8 | 1 | 3 | 0 | não |
| 0x000B | `RIF_INF_LAMBDA_CALDA` | numero | u8 | 1 | 3 | 0 | não |
| 0x000C | `RIF_GIRI` | vetor | u16 | 12 | 3 | 0 | não |
| 0x000D | `SEQUENZA_INIEZIONE` | matriz | u8 | 2x8 | 6 | 0 | não |
| 0x000F | `TEMP_GAS_CAMBIO_LR` | numero | u8 | 1 | 3 | 0 | não |
| 0x000F | `TEMP_RID_CAMBIO` | numero | u8 | 1 | 3 | 0 | não |
| 0x0012 | `TEST_WORD` | numero | u16 | 1 | 3 | 0 | não |
| 0x0014 | `TIPO_ACCENS` | numero | u8 | 1 | 3 | 0 | não |
| 0x0015 | `MODELLO_HARDWARE` | numero | u8 | 1 | 3 | 0 | não |
| 0x0016 | `TAGLIA_INIETTORE_LR` | numero | s8 | 1 | 3 | 0 | não |
| 0x001C | `ABIL_DIAGNOSI` | vetor | u8 | 5 | 3 | 0 | não |
| 0x001D | `STATO_DIAGNOSI` | vetor | u8 | 5 | 3668 | 0 | não |
| 0x001F | `ACTION_DIAGNOSI` | matriz | u8 | 3x5 | 9 | 0 | não |
| 0x0021 | `DIAGNOSI_INJ_BENZ` | numero | u8 | 1 | 732 | 0 | não |
| 0x0024 | `TIPO_SENSORE` | numero | u8 | 1 | 3 | 0 | não |
| 0x0025 | `RIF_SENSORE` | vetor | u8 | 4 | 3 | 0 | não |
| 0x002A | `RIF_TEMP_GAS_LR` | vetor | u8 | 10 | 3 | 0 | não |
| 0x002A | `RIF_TEMP_RID` | vetor | u8 | 10 | 3 | 0 | não |
| 0x002B | `COEFF_TEMP_GAS_LR` | vetor | u8 | 9 | 3 | 0 | não |
| 0x002B | `COEFF_TEMP_RID` | vetor | u8 | 9 | 3 | 0 | não |
| 0x002E | `MAPPA_DELAY_GAS_TEMP` | matriz | u8 | 2x9 | 6 | 0 | não |
| 0x002F | `RIF_DELAY_GAS_TEMP` | vetor | u8 | 9 | 3 | 0 | não |
| 0x0033 | `SUB_CLIENT_CODE` | numero | u16 | 1 | 2 | 0 | não |
| 0x0033 | `TEMPO_CUTOFF_LR` | numero | u16 | 1 | 2 | 0 | não |
| 0x0034 | `TIPO_INIEZIONE` | numero | u8 | 1 | 3 | 0 | não |
| 0x0035 | `IDENTIFICATIVO` | vetor | u8 | 30 | 3 | 0 | não |
| 0x0036 | `TEMPO_INIEZIONE_CONTINUA` | numero | u8 | 1 | 3 | 0 | não |
| 0x0037 | `TEMPI_PER_K` | vetor | s16 | 12 | 3 | 36 | não |
| 0x0038 | `CORRENTE_MANTENIMENTO` | numero | u8 | 1 | 3 | 0 | não |
| 0x003B | `TEMPO_GAS` | numero | u16 | 1 | 3 | 0 | não |
| 0x003C | `TEMPO_BENZINA` | numero | u16 | 1 | 3 | 0 | não |
| 0x003D | `GIRI_PER_K` | vetor | s16 | 12 | 3 | 36 | não |
| 0x0040 | `CODICE_CLIENTE` | numero | u8 | 1 | 1 | 0 | não |
| 0x0046 | `SOGLIA_RICCO_FORZATO` | numero | u8 | 1 | 3 | 0 | não |
| 0x0047 | `LIVELLO_EMUL_ALTO` | numero | u8 | 1 | 3 | 0 | não |
| 0x0048 | `LIVELLO_EMUL_BASSO` | numero | u8 | 1 | 3 | 0 | não |
| 0x004A | `TEMPO_MAX_CORRENTE` | vetor | u16 | 2 | 3 | 0 | não |
| 0x004B | `CILINDRATA` | numero | u16 | 1 | 3 | 0 | não |
| 0x004C | `MINIMA_VERSIONE_INTERFACCIA` | numero | u16 | 1 | 1 | 0 | não |
| 0x004E | `AVVIAMENTI_EMERGENZA` | numero | u8 | 1 | 3 | 0 | não |
| 0x004F | `PARAM_INJ` | numero | u16 | 1 | 3 | 0 | não |
| 0x0051 | `TEMP_DIAGNOSI` | vetor | u8 | 4 | 3 | 0 | não |
| 0x0052 | `TEMPO_CHIUSURA_INIETTORE` | numero | u16 | 1 | 3 | 0 | não |
| 0x0053 | `TEMPO_APERTURA_INIETTORE` | numero | u16 | 1 | 3 | 0 | não |
| 0x0055 | `TEMPO_GAS_PARZIALE` | numero | u16 | 1 | 3 | 0 | não |
| 0x0058 | `TEMPO_TAGLIANDI` | numero | u16 | 1 | 3 | 0 | não |
| 0x005B | `WARNING_VERSIONE_INTERFACCIA` | numero | u16 | 1 | 1 | 0 | não |
| 0x005C | `RIF_TEMP_GAS` | vetor | u8 | 10 | 3 | 0 | não |
| 0x005C | `RIF_TEMP_RID_LR` | vetor | u8 | 10 | 3 | 0 | não |
| 0x005D | `COEFF_TEMP_GAS` | vetor | u8 | 9 | 3 | 0 | não |
| 0x005D | `COEFF_TEMP_RID_LR` | vetor | u8 | 9 | 3 | 0 | não |
| 0x005F | `RIF_PRESS_COLL` | vetor | u16 | 15 | 3 | 0 | não |
| 0x0060 | `COEFF_PRESS_COLL` | vetor | s16 | 15 | 3 | 0 | não |
| 0x006A | `SOGLIA_FLUSSO_SUBSONICO_LR` | numero | u8 | 1 | 3 | 0 | não |
| 0x006B | `TAGLIA_INIETTORI_LR` | numero | u8 | 1 | 3 | 0 | não |
| 0x006D | `TEMPO_SOVRAPPOSIZIONE` | numero | u8 | 1 | 3 | 0 | não |
| 0x006E | `TEMPO_CICCHETTO` | numero | u8 | 1 | 3 | 0 | não |
| 0x006F | `RIF_MAP_SONDA_LAMBDA` | vetor | u16 | 4 | 3 | 0 | não |
| 0x006F | `RITARDO_PASSAGGIO_GAS_LR` | vetor | u8 | 8 | 3 | 0 | não |
| 0x0070 | `MAP_RIF_SONDA_LAMBDA` | matriz | u8 | 3x12 | 9 | 0 | não |
| 0x0071 | `MAPPA_FILTRO_BENZINA` | matriz | u8 | 12x12 | 36 | 0 | não |
| 0x0072 | `MAP_FILTRO_BENZINA` | vetor | u16 | 12 | 3 | 0 | não |
| 0x0074 | `TIPO_CONNESSIONE_OBD_LR` | numero | u8 | 1 | 3 | 0 | não |
| 0x0075 | `TEMP_GAS_CAMBIO` | numero | u8 | 1 | 3 | 0 | não |
| 0x0075 | `TEMP_RID_CAMBIO_LR` | numero | u8 | 1 | 3 | 0 | não |
| 0x0076 | `IMPEDENZA_INIETTORI` | numero | u8 | 1 | 3 | 0 | não |
| 0x0077 | `VAL_PERC_HOLDING_CURRENT` | numero | u8 | 1 | 3 | 0 | não |
| 0x0078 | `MAP_ESTERNO` | vetor | s16 | 6 | 3 | 0 | não |
| 0x0079 | `BASE_TEMPI_GLOBALE` | numero | u16 | 1 | 3 | 0 | não |
| 0x007B | `RIF_PRESS_DIFF` | vetor | u16 | 15 | 3 | 0 | não |
| 0x007C | `COEFF_PRESS_DIFF` | vetor | s16 | 15 | 3 | 0 | não |
| 0x007D | `TEMPO_MORTO_INIETTORI_BENZINA` | numero | u16 | 1 | 3 | 0 | não |
| 0x007E | `TEMPO_MORTO_INIETTORI_GAS` | numero | u16 | 1 | 3 | 0 | não |
| 0x007F | `TEMPI_K_OPENLOOP` | vetor | u16 | 12 | 3 | 0 | não |
| 0x0082 | `RIF_SONDA_LAMBDA` | vetor | u8 | 12 | 3 | 0 | não |
| 0x0086 | `TIPO_SENSORE_TEMPERATURA` | numero | u8 | 1 | 3 | 0 | não |
| 0x008A | `PARAMETRI_TEMP` | vetor | s8 | 5 | 3 | 0 | não |
| 0x008B | `ECU_TEMP` | vetor | u8 | 30 | 3 | 0 | não |
| 0x008F | `TINJ_3000RPM` | matriz | u16 | 8x2 | 24 | 0 | não |
| 0x0090 | `TEMP_AUTOTARATURA` | numero | u8 | 1 | 3 | 0 | não |
| 0x0091 | `SCARTO_MINIMO_TARATURA` | vetor | u16 | 2 | 3 | 0 | não |
| 0x0092 | `T_INJ_BENZ_MAX_CAMBIO` | numero | u16 | 1 | 3 | 0 | não |
| 0x0093 | `SPOSTAMENTO_TARATURA` | numero | u16 | 1 | 3 | 0 | não |
| 0x0094 | `TIPO_INIETTORE` | vetor | u8 | 2 | 4 | 0 | não |
| 0x0095 | `MAPPA_CORR_TARATURA` | matriz | s8 | 4x4 | 12 | 0 | não |
| 0x0096 | `GIRI_AUTOTARATURA` | numero | u16 | 1 | 3 | 0 | não |
| 0x0097 | `PARAM_AUTOTARATURA` | vetor | u8 | 3 | 3 | 0 | não |
| 0x0098 | `TINJ_FILTRO` | vetor | u16 | 8 | 3 | 0 | não |
| 0x009E | `CORRETTORE_BANCATA2` | vetor | s8 | 12 | 3 | 0 | não |
| 0x009E | `TEMPERATURA_ACQUA_AVVIO_LR` | vetor | u8 | 9 | 3 | 0 | não |
| 0x00A0 | `TEMPI_PER_BENZINA` | vetor | u16 | 4 | 3 | 0 | não |
| 0x00A1 | `SEQUENZA_INJ_BENZ_LR` | vetor | u8 | 8 | 3 | 0 | não |
| 0x00A3 | `INIETTATE_PER_BENZINA` | vetor | u8 | 2 | 3 | 0 | não |
| 0x00A3 | `RIF_MAP_ANTICIPO_LR` | vetor | u8 | 5 | 3 | 0 | não |
| 0x00AA | `EMULAZIONE_POSTERIORE` | vetor | u8 | 2 | 3 | 0 | não |
| 0x00AB | `TIPI_SONDA_LAMBDA` | numero | u8 | 1 | 3 | 0 | não |
| 0x00AC | `MASK_INIETTORI_BENZINA` | numero | u16 | 1 | 8 | 0 | não |
| 0x00BC | `K_MAPPA_NEUTRO` | numero | u8 | 1 | 3 | 0 | não |
| 0x00BD | `CONFIGURA_ADATTA_LR` | vetor | u8 | 2 | 3 | 0 | não |
| 0x00BD | `PRESS_RETROPASSAGGIO` | vetor | u16 | 12 | 3 | 0 | não |
| 0x00C2 | `MASK_FUNCTION` | vetor | u8 | 30 | 3 | 0 | não |
| 0x00C3 | `TEMP_GAS_CALDO` | numero | u8 | 1 | 3 | 0 | não |
| 0x00C4 | `RIF_PRESS_SPLIT_FUEL` | vetor | u8 | 10 | 3 | 0 | não |
| 0x00C5 | `COEFF_PRESS_SPLIT_FUEL` | vetor | u8 | 10 | 3 | 0 | não |
| 0x00C6 | `K_FACTOR_PARAM` | vetor | u16 | 4 | 3 | 0 | não |
| 0x00C9 | `OVER_PRESSURE_DIAGNOSYS` | numero | u16 | 1 | 3 | 0 | não |
| 0x00D0 | `MAPPA_CONTRIBUTI_BENZINA` | matriz | u8 | 12x12 | 36 | 0 | não |
| 0x00D5 | `NUMERO_PARTENZE_EMERGENZA` | numero | u8 | 1 | 3 | 0 | não |
| 0x00DB | `ADVANCED_PARAM_INJ` | vetor | u16 | 6 | 3 | 0 | não |
| 0x00DC | `TEMPERATURA_ACQUA_AVVIO` | vetor | u8 | 9 | 3 | 0 | não |
| 0x00DE | `SOGLIA_FLUSSO_SUBSONICO` | numero | u8 | 1 | 3 | 0 | não |
| 0x00DF | `RIF_PRESS_ASS` | vetor | u8 | 15 | 3 | 0 | não |
| 0x00E0 | `COEFF_PRESS_ASS` | vetor | s16 | 15 | 3 | 0 | não |
| 0x00E1 | `TAGLIA_INIETTORE` | numero | s8 | 1 | 3 | 0 | não |
| 0x00E2 | `CHANGE_OVER_CILYNDER_DELAY` | vetor | u16 | 4 | 3 | 0 | não |
| 0x00E3 | `FREST_PARAMETER_LR` | vetor | u16 | 6 | 3 | 0 | não |
| 0x00E3 | `SERVICE_DATA` | vetor | u8 | 25 | 3 | 0 | não |
| 0x00E7 | `ADVANCED_TEMP_RID` | vetor | u8 | 4 | 3 | 0 | não |
| 0x00E8 | `ADVANCED_PRESS_BACK` | vetor | s16 | 2 | 3 | 0 | não |
| 0x00ED | `PARAMETRI_TAGLIANDI` | vetor | u8 | 3 | 3 | 0 | não |
| 0x00EE | `TEMPI_ANTICIPI_EV` | vetor | u8 | 2 | 3 | 0 | não |
| 0x00F1 | `ADV_PARAM_INJ` | vetor | u16 | 12 | 3 | 0 | não |
| 0x00F8 | `FLASH_LUBE_PARAMETER` | vetor | u16 | 4 | 3 | 0 | não |
| 0x00F9 | `NUM_DENTI_ALBERO_CAMME` | numero | u8 | 1 | 3 | 0 | não |
| 0x0114 | `LO_PASS_FILT_CON_SLOW` | elemento_de_vetor | u16 | 1 | 3 | 0 | não |
| 0x012C | `SOGLIA_LED_1` | elemento_de_vetor | u8 | 1 | 3 | 0 | não |
| 0x012C | `SOGLIA_LED_2` | elemento_de_vetor | u8 | 1 | 3 | 0 | não |
| 0x012C | `SOGLIA_LED_3` | elemento_de_vetor | u8 | 1 | 3 | 0 | não |
| 0x012C | `SOGLIA_LED_4` | elemento_de_vetor | u8 | 1 | 3 | 0 | não |
| 0x012F | `ANTICIPO_INTERRUZIONE_WARMUP` | elemento_de_vetor | u8 | 1 | 3 | 0 | não |
| 0x012F | `DELTA_T_RAIL_BLOCCO_DHLP` | elemento_de_vetor | u8 | 1 | 3 | 0 | não |
| 0x0179 | `ABIL_FREEZEFRAME` | vetor | u8 | 5 | 3 | 0 | não |
| 0x017E | `SequenzaAutomaticaAcquisita` | numero | u8 | 1 | 3 | 0 | não |
