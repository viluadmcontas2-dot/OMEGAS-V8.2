# Comandos do protocolo AEB/MP48 (gerado — não editar à mão)

Gerado por `tools/progbase/protocolo_comandos.py`. Executável analisado: sha256 `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4` · captura Portmon sha256 `341542e8790594e0ee640d2e80d131214f63c98199f16e36b061760fecdba1ce`.

## Quadro e checksum (OBS-FIO + OBS-COD)

- TX: b0 b1 [carga] ck — ck = soma mod 256 de TODOS os bytes anteriores do quadro (inclui b0). b0 = classe | n, n (3 bits) = nº de bytes entre o 2º byte e o ck; n==7 => estendido (byte seguinte = n+1).
- RX: eco do quadro + ACK + n + dados + ck; ck = (ACK + n + soma(dados)) mod 256 — o eco não entra.
- Tamanho do quadro TX: (b0 & 7) + 3 para b0 & 7 < 7 (confere com 100 % dos 20 290 pedidos da captura)
- Bytes: little-endian em endereços e valores; exceção: SetEcuBaudRate/SetNewEcuBaudRate enviam ALTO primeiro

## Timeouts e contagens (OBS-COD (construtor RE:0x00920128) + OBS-FIO (SET_TIMEOUTS RC))

- 1º byte da resposta: **1000 ms** (Portmon: RC=1200); bytes seguintes: 0 ms (RC=200).
- Retries por comando: **5** (6 tentativas no total); pausa após `96`: 400 ms; Cancflash: 8000 ms; linha de flash: 200 ms; Progflash/Endflash: 1000 ms; espera de boot: 2000 ms.
- Tabela de baud (índice -> baud): 0=110, 1=300, 2=1200, 3=2400, 4=4800, 5=9600, 6=19200, 7=38400, 8=57600, 9=115200 (padrão = índice 5 = 9600).

## Respostas e como o original as trata

### ECO

- O que é: O adaptador USB-serial devolve cada byte enviado (meia-duplex com eco). O original manda o quadro inteiro num único IRP_MJ_WRITE e depois lê N bytes de eco (N = tamanho do quadro) comparando byte a byte.
- Marca: OBS-FIO (quadro inteiro num WRITE; eco no início da resposta) + OBS-COD (RE:0x00923608, laço de comparação em 0x009237EB..0x00923830)
- Tratamento: divergência -> ComFail |= 0x08, zera o buffer, falha do comando -> Retry (RE:0x009226E4)

### ACK_53

- O que é: `53 n d1..dn ck`, ck = (0x53 + n + soma(d)) & 0xFF. O eco NÃO entra no checksum.
- Marca: OBS-FIO (16 790 respostas, 100 % com checksum certo) + OBS-COD (ReadAck RE:0x00923860, VerifyCk RE:0x00923B30)
- Tratamento: ReadAck só consome o byte 0x53; quem chama lê n, os dados e chama VerifyCk. Falha de checksum -> ComFail |= 0x10 -> Retry.

### NAK_CA

- O que é: `CA s1 s2 ck`, ck = (0xCA + s1 + s2) & 0xFF. Na captura toda NAK é `CA 01 10 DB` (3 499 casos).
- Marca: OBS-FIO (3 499 casos, todos `CA 01 10 DB`) + OBS-COD (RE:0x009239A1..0x00923A25)
- Tratamento: s2 & 0x10 -> ComFail |= 0x04 (parâmetro não suportado, SEM retry; o objeto TAebNumber/Vector marca sucesso=1 e válido=0). Sem o bit 0x10 -> ComFail |= 0x02 (retry até 5). Checksum do NAK errado -> 0x22.

### RETRY_96

- O que é: `96 b ck` (ECU ocupada).
- Marca: OBS-COD (RE:0x0092392F..0x00923962); 0 ocorrências na captura -> comportamento não observado no fio
- Tratamento: lê 1 byte + checksum; se ck ok: Sleep(+0x25C = 400 ms), ComFail |= 0x102 (retry); senão 0x22.

### OUTRO_BYTE

- O que é: qualquer outro valor no lugar do ACK
- Marca: OBS-COD (RE:0x00923A27..0x00923B1D); 0 ocorrências na captura
- Tratamento: com reconexão automática ligada (+0x2BC) e conectado: Sleep(500), tenta Connect por até 2000 ms, Sleep(500); em qualquer caso ComFail |= 0x22 e falha. Sem reconexão automática: só ComFail |= 0x22.

### TIMEOUT

- O que é: nenhum byte em 1000 ms (RC=1200 no SET_TIMEOUTS do Portmon: 1000 + 200 de folga da porta)
- Marca: OBS-FIO (RC 1200 antes de toda leitura do ACK, RC 200 nos bytes seguintes; 2 timeouts reais, ambos em Serial1/COM sem ECU) + OBS-COD (+0x258 = 1000; a porta grava ComFail |= 1 se a leitura veio curta)
- Tratamento: ComFail 0x01 -> Retry: até 5 reenvios do mesmo quadro (+0x260); depois, se +0x2BC: pergunta ao usuário (retorno 6 = Sim) e tenta Connect; senão marca desconectado e fecha a porta.

### Retry (RE:0x009226E4) — pseudocódigo

```
Retry(self, &tentativa):                       # RE:0x009226E4
  se self.modo_sem_reconexao(+0x2BE):
      se ComFail & 0x100 e tentativa < +0x260: tentativa += 1; devolve VERDADEIRO
      devolve FALSO
  se ComFail == 0: devolve FALSO                # nada a refazer
  se ComFail & 0x44: devolve FALSO              # NAK "não suportado" ou reconexão falhou: sem retry
  se tentativa < +0x260 (=5): tentativa += 1; devolve VERDADEIRO
  se self.conectando(+0x2B1): devolve FALSO
  # esgotou: o enlace é dado como perdido
  conectado(+0x2B0) = FALSO; fecha a porta
  se self.reconexao_automatica(+0x2BC):          # padrão 0 (desligada)
      mostra diálogo Sim/Não (retorno 6 = Sim)
      se Sim: se Connect() ok: tentativa = 0; devolve VERDADEIRO
              senão ComFail |= 0x40
      conectado = FALSO; fecha a porta
  dispara OnConnect(conectado, ComFail)
  devolve FALSO
```

## ComFail (máscara de erros em +0x44)

| bit | significado | marca | onde / efeito |
|---|---|---|---|
| `0x0001` | leitura incompleta/timeout na porta | OBS-COD | gravado pela porta (RE:0x0091F29D); bloqueia novos envios (máscara 0x29) |
| `0x0002` | NAK (CA) sem o bit 0x10 do 2º byte de status | OBS-COD | RE:0x00923A04; retry até 5 |
| `0x0004` | NAK (CA) com bit 0x10 = parâmetro/comando não suportado; ou endereço >= 0xFF com versão 0 | OBS-COD | sem retry (máscara 0x44); o objeto fica "inválido" mas a leitura não é erro de transporte |
| `0x0008` | eco divergente | OBS-COD | RE:0x00922FA5/0x00923393; bloqueia envios (0x29); retry |
| `0x0010` | checksum da resposta divergente | OBS-COD | RE:0x00923B7D (VerifyCk); conta falhas consecutivas em +0x264 |
| `0x0020` | endereço >= 0x1FF com versão < 2; ou resposta inválida (0x22 = 0x20|0x02) | OBS-COD | bloqueia envios (0x29) |
| `0x0040` | reconexão falhou | OBS-COD | sem retry (máscara 0x44) |
| `0x0100` | pedido de retry (0x96 recebido com checksum certo, 0x102 = 0x100|0x02) | OBS-COD | única condição de retry no modo +0x2BE |
| `0x0400` | falha ao abrir/configurar a porta | OBS-COD | gravado pela porta |
| `0x8000` | tamanho da resposta diferente do esperado (0x8020 = 0x8000|0x20) | OBS-COD | GetNumber/GetVector/GetPacket; retorna falso SEM retry |

## Comandos

| id | comando | quadro | resposta esperada | log | OMEGAS |
|---|---|---|---|---|---|
| `wake` | Sonda de descoberta (00 a 38400 baud) | `00` (1 byte; sem checksum e SEM eco) | `80` (1 byte, sem eco) = ECU presente<br>timeout (RC 200 ms) = nada na porta | 2 (linha 32) | nao |
| `ident` | GetIdentification | `00 02 02` (b0=0 bytes de carga, b1=0x02, ck) | `53 04 b0 b1 b2 b3 ck` (n=4) — observado `53 04 FE 4F 45 0B F4`<br>`53 06 ... ck` (n=6: dois bytes extras lidos)<br>NAK/timeout -> Retry | 4 (linha 108) | sim |
| `connect` | Connect | `01 00 3A ck` (3B) — b0=1 byte de carga, b1=0x00, carga 0x3A | `53 00 53`<br>NAK `CA 01 08` visto só na reconexão de outra captura (doc 17 protocolo-landi) | 4 (linha 176) | sim |
| `versao` | GetProtocolVersion (ProtocolCommand 0x25) | `00 25 25` (byte do comando = +0x2C9, padrão 0x25) | `53 01 vv ck` — observado `53 01 02 56` (versão 2) | 4 (linha 196) | sim |
| `disconnect` | Disconnect | `00 01 01` | `53 00 53` | 3 (linha 340) | sim |
| `getnumber` | GetNumber (escalar) | v2: `09 lo hi ck`; v0/v1: `08 end ck` (v1: end>=0xFF vira `FF (end+1)&FF`) | `53 n valor ck` com n == tamanho do elemento (1, 2 ou 4); valor little-endian, com sinal se Signed<br>NAK `CA 01 10 DB` = SerialCode inexistente nesta ECU | 502 (linha 246) | sim |
| `getnumber_idx` | GetNumber + índice (elemento de vetor) | v2: `0A lo hi idx ck`; v0/v1: `09 end idx ck` | igual ao GetNumber | 299 (linha 582) | sim |
| `getnumber_2` | GetNumber + linha + coluna (elemento de matriz) | v2: `0B lo hi linha coluna ck`; v0/v1: `0A end linha coluna ck` (1º byte extra = linha, 2º = coluna) | igual ao GetNumber | 0 | parcial |
| `getvector` | GetVector (vetor inteiro) | v2: `29 lo hi ck`; v0/v1: `28 end ck` | `53 n d... ck`, n = dimensão x tamanho (ex. 60 = 30 x u16); NAK se SerialCode inexistente | 1556 (linha 508) | sim |
| `getvector_row` | GetVector + linha (uma linha de matriz) | v2: `2A lo hi linha ck`; v0/v1: `29 end linha ck` | `53 n d... ck`, n = colunas x tamanho (ex. MAP_K: 12) | 207 (linha 1218) | sim |
| `getpacket` | GetPacket (canal de dados em tempo real) | `48 canal ck` | `53 n d... ck` — canal 01: 34 bytes (telemetria); canal 0B: 14 bytes (status AutoCal)<br>NAK `CA 01 10 DB` no canal 08 (3 334x) = canal não implementado nesta ECU | 12765 (linha 14544) | sim |
| `getbyaddr` | GetByAddress (memória por endereço) | `6A flags a_lo a_hi ck` (endereço 16 bits) ou `6C flags a0 a1 a2 a3 ck` (32 bits). flags: bit7=valor 16 bits, bit5=endereço 32 bits | `53 n valor ck` (n lido e IGNORADO; valor de 1 ou 2 bytes conforme flags, devolvido com extensão de sinal) | 0 | nao |
| `elemdim` | GetElementDimension | v2: número `09 lo hi`, vetor `0A lo hi 00`, matriz `0B lo hi 00 00` (v0/v1: 08/09/0A) | igual ao GetNumber: o tamanho n da resposta é a "dimensão" | — | nao |
| `setnumber` | SetNumber (escalar / elemento de vetor / elemento de matriz) | op = 0x10 | carga (carga < 7), carga = tamanho do valor (1,2,4) + 1 (v2: byte alto do endereço) + 1 (índice) ou +2 (linha+coluna). v2 sem índice, 1 B: `12 lo hi val ck`; 2 B: `13`; 4 B: `15`. Com índice: `13 lo hi idx val`; com linha+coluna: `14 lo hi linha coluna val`. carga >= 7: op `17`, depois `carga+1` (RE:0x00925BF5..0x00925C59) | `53 00 53` (n=0, SEM byte de resultado — corrige o doc 07 do protocolo-landi)<br>NAK -> Retry/erro | 220 (linha 48568) | sim |
| `setvector` | SetVector (vetor inteiro ou linha) | op = 0x30 | carga (carga < 7), carga = dim x tamanho + 1 (v2) (+1 com linha); `35 lo hi d1 d2 d3 d4 ck` p.ex. FLAG_CONF1. carga >= 7: op `37`, `carga+1`, endereço, [linha], dados | `53 00 53` | 19 (linha 31284) | sim |
| `setbyaddr` | SetByAddress | `73 flags a_lo a_hi val` (8 bits/end16), `74 ... v_lo v_hi`, `75 flags a0..a3 val`, `76 flags a0..a3 v_lo v_hi` | `53 00 53` | 0 | nao |
| `sendcommand` | SendCommand genérico (b0 = nº de bytes de carga, b1 = código) | `n cmd c1..cn ck` com n<7; n>=7: `07 cmd (n+1) c1..cn ck` | `53 m d1..dm ck` (m bytes devolvidos ao chamador) | — | parcial |
| `cmd_12` | Comando 0x12 (carga 00) | `01 12 00 13` | `53 00 53` | 2 (linha 434818) | nao |
| `cmd_13` | Comando 0x13 (sem carga) | `00 13 13` | `53 00 53` | 6 (linha 103458) | nao |
| `cmd_14` | Comando 0x14 (sem carga) — polling da calibração | `00 14 14` | `53 02 02 00 57` (2 bytes de dados `02 00`, valor constante em 4 693/4 693) | 4693 (linha 103566) | nao |
| `cmd_04` | Comando 0x04 (carga 54) | `01 04 54 59` | NAK `CA 01 10 DB` (3/3) | 3 (linha 992) | nao |
| `setduty` | SetDuty (teste de saída por PWM) | `02 05 id valor ck` (valor >= 0) ou `01 05 id ck` (valor < 0) | `53 00 53` | 0 | nao |
| `setbaud` | SetEcuBaudRate (hardware antigo) | `02 03 hi lo ck`, valor = 8 000 000 / (baud x 16) em 16 bits, ALTO primeiro | `53 00 53` | 0 | nao |
| `setnewbaud` | SetNewEcuBaudRate (hardware novo) | `02 19 hi lo ck`, valor = baud / 2 em 16 bits, ALTO primeiro; baud em [1200, 115200] | `53 00 53` | 0 | nao |
| `autocal_acao` | Ação AutoCal (02 24 04 modo) | `02 24 04 modo ck` — modo 1=gasolina, 2=GNV, 4=Reset all, 8=automatch manual | `53 00 53` (a ECU executa na hora; o efeito aparece nos vetores AutoCal lidos em seguida) | 1 (linha 69944) | sim |
| `autocal_05` | Ação AutoCal 01 24 05 | `01 24 05 2A` | `53 00 53` (esperado) | 0 | nao |
| `cancflash` | Cancflash (apaga flash) | `00 09 09` | `53 m st ck` (st = status com sinal) | 0 | nao |
| `exitboot_nf` | ExitBootNotForced | `00 0A 0A` | `53 m st ck` | 0 | nao |
| `exitboot` | ExitBoot (forçado) | `00 0B 0B` | `53 m st ck` | 0 | nao |
| `progflash` | Progflash (entra em programação) | `93 93` (1 byte + ck) | `A7 A7` (byte A7 e o mesmo valor como checksum) | 0 | nao |
| `endflash` | Endflash | `85 85` | `53 53` | 0 | nao |
| `flash_linha` | Linha de firmware (S-record em texto -> binário) | `53 31 cnt addr_hi addr_lo dados.. srec_ck` (a linha ASCII do arquivo vira bytes; 1ª letra e tipo vão como ASCII, o resto em pares hexa). EOF: `53 39 03 FF FF FE`. Sem checksum adicional do protocolo. | `A7 A7` (linha aceita) ou `53 53` | — | nao |
| `v01_getnumber` | GetNumber v0/v1 (endereço de 1 byte) | `08 end ck` / v1 estendido `08 FF (end+1) ck` | igual ao GetNumber v2 | 0 | nao |
| `v01_getvector` | GetVector v0/v1 | `28 end ck` | igual ao GetVector v2 | 0 | nao |

### `wake` — Sonda de descoberta (00 a 38400 baud)

- Grupo: handshake · Rotina: DESCONHECIDA
- Marca: OBS-FIO (envio/resposta); DESCONHECIDO (rotina)
- Tratamento no original: DESCONHECIDO: a rotina que envia este byte não foi localizada no executável (não está em TAebProtocol). Visto 2x: Serial1/COM sem ECU -> timeout (linha 32); Silabser0 -> `80` (linha 70). Depois o ProgBase fecha a porta e reabre a 9600.
- Captura: 2 ocorrências {'SEM_RESPOSTA': 1, 'WAKE_80': 1}; primeira na linha 32: `00` -> ``
- OMEGAS: nao — o app abre direto a 9600 e começa por `00 02 02`

### `ident` — GetIdentification

- Grupo: identificacao · Rotina: RE:0x00923DA4 (GetIdentification)
- Marca: OBS-FIO (4 ocorrências, 3 ACK + 1 timeout em COM); OBS-COD (rotina)
- Tratamento no original: RE:0x00923DA4: ReadAck; n=ReadByte; com buffer: lê 4 bytes (6 se n==6) em b0..b3(+b4,b5); sem buffer: lê e descarta n bytes; VerifyCk; falha -> Retry. O Connect de alto nível (RE:0x00927D80) interpreta b1==3 como "ECU no bootloader", b2=família, b3&0x7F=versão de hardware, b3&0x80=flash duplo — só em modo boot; em modo aplicação a ECU capturada devolveu FE 4F 45 0B.
- Captura: 4 ocorrências {'ACK_53': 3, 'SEM_RESPOSTA': 1}; primeira na linha 108: `00 02 02` -> ``
- OMEGAS: sim — Mp48Protocol.kt CMD_INIT_1

### `connect` — Connect

- Grupo: handshake · Rotina: RE:0x00923FDC (Connect)
- Marca: OBS-FIO (4x, todas ACK); OBS-COD
- Tratamento no original: RE:0x00923FDC: zera versão (+0x2C4=0); marca conectando(+0x2B1)=1; laço de Retry: envia Connect, ReadAck, ReadByte(n), VerifyCk. Sem ComFail -> conectado(+0x2B0)=1 e segue para GetProtocolVersion. Falha -> conectado=0 e, se +0x2BF, fecha a porta. Dispara OnConnect.
- Captura: 4 ocorrências {'ACK_53': 4}; primeira na linha 176: `01 00 3A 3B` -> `01 00 3A 3B 53 00 53`
- OMEGAS: sim — Mp48Protocol.kt CMD_INIT_2

### `versao` — GetProtocolVersion (ProtocolCommand 0x25)

- Grupo: handshake · Rotina: RE:0x00923FDC (trecho GetProtocolVersion)
- Marca: OBS-FIO (4x, versão 2); OBS-COD
- Tratamento no original: dentro do Connect (RE:0x00924112..0x009241A6): lê n, aloca n dwords, lê n bytes, VerifyCk; se ok ProtocolVersion(+0x2C4) = 1º byte. Define TODO o endereçamento seguinte.
- Captura: 4 ocorrências {'ACK_53': 4}; primeira na linha 196: `00 25 25` -> `00 25 25 53 01 02 56`
- OMEGAS: sim — Mp48Protocol.kt CMD_IDENTIFY (nome enganoso: é a versão do protocolo)

### `disconnect` — Disconnect

- Grupo: handshake · Rotina: RE:0x00924324 (Disconnect)
- Marca: OBS-FIO (3x); OBS-COD
- Tratamento no original: RE:0x00924324: UMA tentativa, sem Retry. Depois conectado=0, versão=0, fecha a porta se +0x2BF, dispara OnConnect(0).
- Captura: 3 ocorrências {'ACK_53': 3}; primeira na linha 340: `00 01 01` -> `00 01 01 53 00 53`
- OMEGAS: sim — Mp48Protocol.kt CMD_DISCONNECT

### `getnumber` — GetNumber (escalar)

- Grupo: leitura · Rotina: RE:0x00924994
- Marca: OBS-FIO (502 pedidos: 460 ACK, 42 NAK); OBS-COD
- Tratamento no original: RE:0x00924994. Se n != tamanho: lê e descarta n bytes, VerifyCk, ComFail |= 0x8020 e devolve FALSO SEM retry. NAK com bit 0x10 -> ComFail 4 (sem retry; TAebNumber marca válido=0). Demais falhas -> Retry (5x).
- Captura: 502 ocorrências {'ACK_53': 460, 'NAK_CA': 42}; primeira na linha 246: `09 33 00 3C` -> `09 33 00 3C 53 02 00 00 55`
- OMEGAS: sim — AutoCalProtocol.kt READ_SCALAR

### `getnumber_idx` — GetNumber + índice (elemento de vetor)

- Grupo: leitura · Rotina: RE:0x00924C74
- Marca: OBS-FIO (299: 251 ACK, 48 NAK); OBS-COD
- Tratamento no original: RE:0x00924C74 (mesma lógica de tamanho/retry). Atenção: mesmo quadro do GetElementDimension de vetor.
- Captura: 299 ocorrências {'ACK_53': 251, 'NAK_CA': 48}; primeira na linha 582: `0A 73 01 00 7E` -> `0A 73 01 00 7E 53 01 04 58`
- OMEGAS: sim — AutoCalProtocol.kt READ_INDEXED

### `getnumber_2` — GetNumber + linha + coluna (elemento de matriz)

- Grupo: leitura · Rotina: RE:0x00924F60
- Marca: OBS-COD; 0 ocorrências na captura (nenhum objeto lido assim)
- Tratamento no original: RE:0x00924F60. NOTA: o doc 06 do protocolo-landi chamava os extras de "sub/idx"; no código o 1º byte extra é RowIndex (+0x50) e o 2º ColIndex (+0x4C).
- OMEGAS: parcial — AebProtocolFramesV7.kt getNumber(address,row,column), sem uso comprovado em runtime

### `getvector` — GetVector (vetor inteiro)

- Grupo: leitura · Rotina: RE:0x00925258
- Marca: OBS-FIO (1 556: 1 502 ACK, 54 NAK); OBS-COD
- Tratamento no original: RE:0x00925258: lê n; se n == 4*cont... (elemento 4/2/1 B) copia; n incompatível -> ComFail 0x8020 sem retry. A dimensão esperada vem do objeto (ArrayDimension); a ECU capturada devolve 30 elementos nos vetores AutoCal cujo DFM diz 18 (vetores.json: tamanho_confere_com_dfm=false).
- Captura: 1556 ocorrências {'ACK_53': 1502, 'NAK_CA': 54}; primeira na linha 508: `29 03 00 2C` -> `29 03 00 2C 53 04 86 24 51 10 62`
- OMEGAS: sim — AutoCalProtocol.kt READ_VECTOR; Mp48Protocol.kt

### `getvector_row` — GetVector + linha (uma linha de matriz)

- Grupo: leitura · Rotina: RE:0x00925570
- Marca: OBS-FIO (207: 189 ACK, 18 NAK); OBS-COD
- Tratamento no original: RE:0x00925570 (igual ao GetVector).
- Captura: 207 ocorrências {'ACK_53': 189, 'NAK_CA': 18}; primeira na linha 1218: `2A 54 00 00 7E` -> `2A 54 00 00 7E 53 0C A2 A2 A2 A2 A2 A2 A2 A2 A2 A2 A2 A2 F7`
- OMEGAS: sim — Mp48Protocol.kt readKRow

### `getpacket` — GetPacket (canal de dados em tempo real)

- Grupo: leitura · Rotina: RE:0x00925894
- Marca: OBS-FIO (12 765: canal 01 x9 408, 08 x3 334 NAK, 0B x23); OBS-COD
- Tratamento no original: RE:0x00925894: n é lido com MOVSX (byte com sinal) -> pacotes > 127 B não seriam suportados; copia min(n, buffer); n > buffer -> ComFail 0x8020 sem retry. NAK (0x10) sem retry: o 48 08 repete 3 334x porque o relógio da tela o pede sempre.
- Captura: 12765 ocorrências {'ACK_53': 9431, 'NAK_CA': 3334}; primeira na linha 14544: `48 0B 53` -> `48 0B 53 53 0E 00 00 00 00 00 00 00 00 00 00 00 00 01 03 65`
- OMEGAS: sim — Mp48Protocol.kt CMD_TELEMETRY/CMD_SECONDARY_STATUS; AutoCalProtocol.kt CMD_NATIVE_STATUS

### `getbyaddr` — GetByAddress (memória por endereço)

- Grupo: leitura · Rotina: RE:0x009259C4
- Marca: OBS-COD; 0 ocorrências na captura
- Tratamento no original: RE:0x009259C4. Não confere n com o tamanho pedido.
- OMEGAS: nao

### `elemdim` — GetElementDimension

- Grupo: leitura · Rotina: RE:0x00926730
- Marca: OBS-COD; chamado por RE:0x0045C16D/0x0045C61A/0x0047F4D1 (tela TFormConfig); não separável no fio
- Tratamento no original: RE:0x00926730: lê n e descarta; devolve -1 (sem porta), -3 (endereço fora da faixa), -2 (falha). No fio é INDISTINGUÍVEL de GetNumber+idx(0).
- OMEGAS: nao

### `setnumber` — SetNumber (escalar / elemento de vetor / elemento de matriz)

- Grupo: escrita · Rotina: RE:0x00925AFC, 0x00925D18, 0x00925F44
- Marca: OBS-FIO (op 12 x4, 14 x216; todos `53 00 53`); OBS-COD
- Tratamento no original: RE:0x00925AFC/0x00925D18/0x00925F44: ReadAck; ReadByte (n, ignorado); VerifyCk; falha -> Retry. Porta fechada ou sem Connect: devolve VERDADEIRO sem enviar nada. O quadro sozinho é AMBÍGUO: `14 lo hi a b c` = (linha,coluna,valor 1 B) OU (idx, valor 2 B); só o objeto desambigua.
- Captura: 220 ocorrências {'ACK_53': 220}; primeira na linha 48568: `14 54 00 00 00 64 CC` -> `14 54 00 00 00 64 CC 53 00 53`
- OMEGAS: sim — AutoCalProtocol.kt frameWriteU8/U16, Mp48Protocol.kt writeKCell

### `setvector` — SetVector (vetor inteiro ou linha)

- Grupo: escrita · Rotina: RE:0x0092617C, 0x009263B0
- Marca: OBS-FIO (op 35 x19, todas `53 00 53`); OBS-COD; op 37 sem ocorrência na captura
- Tratamento no original: RE:0x0092617C (inteiro) / RE:0x009263B0 (+linha): mesmo fluxo do SetNumber.
- Captura: 19 ocorrências {'ACK_53': 19}; primeira na linha 31284: `35 03 00 86 2C 51 10 4B` -> `35 03 00 86 2C 51 10 4B 53 00 53`
- OMEGAS: sim — AutoCalProtocol.kt WRITE_VECTOR_EXTENDED; Mp48Protocol.kt kInsertionMode

### `setbyaddr` — SetByAddress

- Grupo: escrita · Rotina: RE:0x009265F8
- Marca: OBS-COD; 0 ocorrências
- Tratamento no original: RE:0x009265F8 (mesmo fluxo).
- OMEGAS: nao

### `sendcommand` — SendCommand genérico (b0 = nº de bytes de carga, b1 = código)

- Grupo: sistema · Rotina: RE:0x009244C8
- Marca: OBS-COD
- Tratamento no original: RE:0x009244C8: ReadAck; m=ReadByte; copia min(m, buffer); *tamanho=m; VerifyCk; falha -> Retry. Usado pela interface (TFormCalibra, TFormConfig, TFormDiagnosi, TFormLandiConnect, TFormSwitch) com os códigos 04,0E,10,11,12,13,14,15,21,24,26,27,28 e a variável de TFormSwitch.
- OMEGAS: parcial

### `cmd_12` — Comando 0x12 (carga 00)

- Grupo: sistema · Rotina: RE:0x009244C8
- Marca: OBS-FIO (2x); significado INFERIDO
- Tratamento no original: SendCommand(0x12). Chamadores: RE:0x0042B8A3, 0x0048031D (TFormConfig/TFormMain), 0x0048640B (TFormCalibra.TimerBlinkTimer), 0x004B0998 (TFormConfig.ButtonRicercaParametriClick), 0x004FBC64. Na captura aparece sempre imediatamente antes do Disconnect `00 01 01` (linhas 434818 e 1102680). Significado: INFERIDO "encerrar modo de edição/liberar a ECU"; DESCONHECIDO o efeito na ECU.
- Captura: 2 ocorrências {'ACK_53': 2}; primeira na linha 434818: `01 12 00 13` -> `01 12 00 13 53 00 53`
- OMEGAS: nao

### `cmd_13` — Comando 0x13 (sem carga)

- Grupo: sistema · Rotina: RE:0x009244C8
- Marca: OBS-FIO (6x); OBS-COD (chamador)
- Tratamento no original: SendCommand(0x13). Chamador no código: RE:0x00486B6A (TFormCalibra.FormKeyPress = tecla na tela de calibração). Seis ocorrências na captura, em sequência com o polling da tela. Efeito DESCONHECIDO.
- Captura: 6 ocorrências {'ACK_53': 6}; primeira na linha 103458: `00 13 13` -> `00 13 13 53 00 53`
- OMEGAS: nao

### `cmd_14` — Comando 0x14 (sem carga) — polling da calibração

- Grupo: sistema · Rotina: RE:0x009244C8
- Marca: OBS-FIO (4 693x, resposta única); OBS-COD (chamador)
- Tratamento no original: SendCommand(0x14). Chamador: RE:0x004857ED (TFormCalibra.TimerBlinkTimer = relógio da tela de calibração). É o comando mais frequente depois do 48 01. Significado DESCONHECIDO (resposta constante).
- Captura: 4693 ocorrências {'ACK_53': 4693}; primeira na linha 103566: `00 14 14` -> `00 14 14 53 02 02 00 57`
- OMEGAS: nao

### `cmd_04` — Comando 0x04 (carga 54)

- Grupo: sistema · Rotina: RE:0x009244C8
- Marca: OBS-FIO (3x NAK); finalidade DESCONHECIDA
- Tratamento no original: SendCommand(0x04) (RE:0x0043C761). A ECU capturada NÃO implementa o comando: NAK com bit 0x10 -> ComFail 4, sem retry. Enviado uma vez no início de cada sessão longa (linhas 992, 435194, 1103056).
- Captura: 3 ocorrências {'NAK_CA': 3}; primeira na linha 992: `01 04 54 59` -> `01 04 54 59 CA 01 10 DB`
- OMEGAS: nao

### `setduty` — SetDuty (teste de saída por PWM)

- Grupo: sistema · Rotina: RE:0x00924708
- Marca: OBS-COD; 0 ocorrências na captura
- Tratamento no original: RE:0x00924708 (id em dl, valor em ecx). Nome herdado do doc 12; o significado exato é INFERIDO.
- OMEGAS: nao

### `setbaud` — SetEcuBaudRate (hardware antigo)

- Grupo: sistema · Rotina: RE:0x009247F0
- Marca: OBS-COD; 0 ocorrências
- Tratamento no original: RE:0x009247F0; usado no flash (RE:0x00928E78) e em RE:0x00927D80.
- OMEGAS: nao

### `setnewbaud` — SetNewEcuBaudRate (hardware novo)

- Grupo: sistema · Rotina: RE:0x009248B0
- Marca: OBS-COD; 0 ocorrências
- Tratamento no original: RE:0x009248B0 (recusa baud fora da faixa sem enviar).
- OMEGAS: nao

### `autocal_acao` — Ação AutoCal (02 24 04 modo)

- Grupo: acao · Rotina: RE:0x00512280
- Marca: OBS-FIO (modo 04 x1); OBS-COD (envio e portão); modos 01/02/08 INFERIDO
- Tratamento no original: RE:0x00512280 (portão IsConnect RE:0x00512264; devolve 0 ok, 1 falha de envio, 2 sem conexão). Chamado por CommonAction RE:0x00517568, que antes pede confirmação ao usuário e depois espera 1 s (Sleep 1000) e recarrega a tela. Só o modo 04 foi visto no fio (linha 69944); 01/02/08 vêm do código/doc 17.
- Captura: 1 ocorrências {'ACK_53': 1}; primeira na linha 69944: `02 24 04 04 2E` -> `02 24 04 04 2E 53 00 53`
- OMEGAS: sim — AutoCalProtocol.kt manualAction

### `autocal_05` — Ação AutoCal 01 24 05

- Grupo: acao · Rotina: RE:0x00512300
- Marca: OBS-COD; 0 ocorrências; efeito DESCONHECIDO
- Tratamento no original: RE:0x00512300, chamado por RE:0x00512858 depois de gravar dois vetores de flags por ponto (TAutoCalSettings). Nunca visto no fio.
- OMEGAS: nao

### `cancflash` — Cancflash (apaga flash)

- Grupo: flash · Rotina: RE:0x009282B0
- Marca: OBS-COD; 0 ocorrências
- Tratamento no original: RE:0x009282B0: troca o timeout de leitura para +0x2B8 (8000 ms), envia, lê m e st, VerifyCk, restaura; falha de todas as tentativas -> -2. Chamado só pelo download de firmware (RE:0x00929315, 0x0092A00E).
- OMEGAS: nao

### `exitboot_nf` — ExitBootNotForced

- Grupo: flash · Rotina: RE:0x00928200
- Marca: OBS-COD; 0 ocorrências
- Tratamento no original: RE:0x00928200.
- OMEGAS: nao

### `exitboot` — ExitBoot (forçado)

- Grupo: flash · Rotina: RE:0x00928150
- Marca: OBS-COD; 0 ocorrências
- Tratamento no original: RE:0x00928150; também via SendCommand(0x0B) em RE:0x0092929A (limpeza após falha no download).
- OMEGAS: nao

### `progflash` — Progflash (entra em programação)

- Grupo: flash · Rotina: RE:0x0092839C
- Marca: OBS-COD; 0 ocorrências
- Tratamento no original: RE:0x0092839C: 4 tentativas; lê 1 byte em 1000 ms, depois outro em 0 ms; exige igual e == 0xA7.
- OMEGAS: nao

### `endflash` — Endflash

- Grupo: flash · Rotina: RE:0x0092845C
- Marca: OBS-COD; 0 ocorrências
- Tratamento no original: RE:0x0092845C: 4 tentativas; ok se o byte lido == 0x53; fecha a porta e marca desconectado.
- OMEGAS: nao

### `flash_linha` — Linha de firmware (S-record em texto -> binário)

- Grupo: flash · Rotina: RE:0x009285A8, 0x00928968, 0x00928E78
- Marca: OBS-COD; 0 ocorrências (a captura não tem flash)
- Tratamento no original: RE:0x009285A8 (linhas) e RE:0x00928968 (fim): até 8 tentativas por linha, resposta em 200 ms; falha -> restaura o baud, Disconnect, devolve -1000. Fluxo completo em RE:0x00928E78 (ScaricaFile).
- OMEGAS: nao

### `v01_getnumber` — GetNumber v0/v1 (endereço de 1 byte)

- Grupo: leitura · Rotina: RE:0x00924994
- Marca: OBS-COD; 0 ocorrências (a ECU capturada é versão 2)
- Tratamento no original: mesmas rotinas; só o formato do endereço muda (RE:0x00924A58 base 8; +1 se versão >= 2). Limites: end >= 0xFF com versão 0 -> ComFail 4; end >= 0x1FF com versão < 2 -> ComFail 0x20. Corrige o doc 04: o limiar é 0xFF (não 0xFE) e não há colisão FE/1FE.
- OMEGAS: nao

### `v01_getvector` — GetVector v0/v1

- Grupo: leitura · Rotina: RE:0x00925258
- Marca: OBS-COD; 0 ocorrências
- Tratamento no original: RE:0x00925258 com versão < 2.
- OMEGAS: nao

## Chamadores de SendCommand (código -> dono)

| código | endereço | dono |
|---|---|---|
| 0x04 | RE:0x0043C761 | função grande RE:0x00439E94 (sem nome RTTI) |
| 0x0B | RE:0x0092929A | ScaricaFile RE:0x00928E78 |
| 0x0E | RE:0x00485DEC | TFormCalibra.TimerBlinkTimer |
| 0x0E | RE:0x00486A5D | TFormCalibra.FormKeyPress |
| 0x10 | RE:0x00486AAD | TFormCalibra.FormKeyPress |
| 0x11 | RE:0x0043A265 | função grande RE:0x00439E94 |
| 0x12 | RE:0x0042B8A3 | RE:0x0042B834 |
| 0x12 | RE:0x0048031D | RE:0x00439E94 |
| 0x12 | RE:0x0048640B | TFormCalibra.TimerBlinkTimer |
| 0x12 | RE:0x004B0998 | TFormConfig.ButtonRicercaParametriClick |
| 0x12 | RE:0x004FBC64 | RE:0x004FBA40 |
| 0x13 | RE:0x00486B6A | TFormCalibra.FormKeyPress |
| 0x14 | RE:0x004857ED | TFormCalibra.TimerBlinkTimer |
| 0x15 | RE:0x004846EA | RE:0x00482F34 |
| 0x15 | RE:0x004858AD | TFormCalibra.TimerBlinkTimer |
| 0x15 | RE:0x00485EAB | TFormCalibra.TimerBlinkTimer |
| 0x15 | RE:0x00486C65 | TFormCalibra.FormKeyPress |
| 0x21 | RE:0x004D7952 | TFormDiagnosi.PanelTestOnClick |
| 0x21 | RE:0x004D7A36 | TFormDiagnosi.PanelTestOnClick |
| 0x21 | RE:0x004D7CF0 | TFormDiagnosi.PanelTestOffClick |
| 0x24 | RE:0x005122D6 | AutoCal: ação (RE:0x00512280) |
| 0x24 | RE:0x00512350 | AutoCal: 01 24 05 (RE:0x00512300) |
| 0x26 | RE:0x00434610 | RE:0x0043457C |
| 0x27 | RE:0x004A5E75 | TFormConfig.TimerDatiTimer |
| 0x27 | RE:0x004A628C | TFormConfig.CheckInjAcquisitionClick |
| 0x27 | RE:0x004A6319 | TFormConfig.ButtonSequenceAcquisitionClick |
| 0x27 | RE:0x004A6548 | TFormConfig.EditRpmTachimetroKeyPress |
| 0x27 | RE:0x004C940E | TFormConfig.ButtonSwitchSettingsClick |
| 0x27 | RE:0x004C94D3 | TFormConfig.ButtonSwitchSettingsClick |
| 0x28 | RE:0x004EDA8E | TFormLandiConnect.ButtonResetClick |
| var | RE:0x00412916..0x00413094 | TFormSwitch (código vem da propriedade +0x3D8; ComboBuzzerKeyPress) |

## Campos da instância TAebProtocol (OBS-COD)

| offset | significado |
|---|---|
| 0x40 | ponteiro da porta (TAebBee/TAebTcp); 0 = sem porta |
| 0x44 | ComFail (máscara de erros; a camada de porta também grava nele) |
| 0x48 | acumulador do checksum (TX/RX) |
| 0x49 | buffer de transmissão (até ~260 B) |
| 0x14D | buffer de recepção |
| 0x254 | nº de bytes no buffer de TX |
| 0x258 | timeout 1º byte da resposta (ms) = 1000 |
| 0x25C | pausa após 0x96 (ms) = 400 |
| 0x260 | máximo de retries = 5 |
| 0x264 | falhas consecutivas de checksum |
| 0x26C | flag "baixando arquivo" |
| 0x28A | evento de log (OnLog); != 0 liga as mensagens de depuração |
| 0x294/0x298 | índice de baud (padrão 5 = 9600) normal/alternativo |
| 0x2B0 | conectado |
| 0x2B1 | conectando |
| 0x2B2 | hardware legado (família 0 e versão < 2) |
| 0x2B4 | permite trocar o baud do boot |
| 0x2B8 | timeout do Cancflash (ms) = 8000 |
| 0x2BC | reconexão automática (padrão 0) |
| 0x2BD | pula o handshake do Connect (padrão 0) |
| 0x2BE | modo sem reconexão (retry só se ComFail & 0x100) |
| 0x2BF | fecha a porta no Disconnect (padrão 1) |
| 0x2C0 | bit 7 da versão de hardware (flash duplo) |
| 0x2C1 | usa SetNewEcuBaudRate |
| 0x2C4 | ProtocolVersion (0, 1 ou 2) |
| 0x2C8 | ByteExtended = 0xFF |
| 0x2C9 | ProtocolCommand = 0x25 |
| 0x2DC | espera de boot (ms) = 2000 |
| 0x2E0 | usa transporte TCP (TAebTcp) em vez de serial |
