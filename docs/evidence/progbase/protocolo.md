# Tema 1 — Transporte e protocolo (ProgBase 4.2.0.6 ↔ MP48)

Selos: `PROVADO` = visto na fiação ou no DUMP sem ambiguidade; `INFERIDO` = dedução com raciocínio em uma linha; `DESCONHECIDO` = lacuna, com o que provaria. Fontes completas em `fontes/INDICE-FONTES.md`. Comandos AutoCal específicos estão em `autocal.md`; Mapa K/Curva K em `curvas-mapas.md`; nível em `level.md`.

Notação de fonte: `LN seq N / idx M` = transação N (parser `scripts/omegas/portmon_parser.py`) e índice IRP M do `PortmonLOGNOVO.LOG` de 63.424.275 B (SHA-256 `341542e8790594e0ee640d2e80d131214f63c98199f16e36b061760fecdba1ce`, extraído de `PortmonLOGNOVO.zip` Drive `1idvIhV4eFGXv2VVNsU0CT6ewdtTBqNFp`, 20.288 transações). É uma captura **diferente e menor** que o Lognovo de 149,9 MB citado pelas fixtures; os fatos abaixo valem para ela, com índices próprios.

## 1.1 Transporte

| Item | Valor | Selo | Fonte |
|---|---|---|---|
| Porta | serial USB Silicon Labs CP210x (`Silabser0` no Portmon) | PROVADO | LN idx 19/58/180 (`IRP_MJ_CREATE Silabser0`); `1.LOG` linha 1 |
| Velocidade e formato | **9600 bps, 8 bits, 1 stop, sem paridade**; sem handshake de hardware (`HANDFLOW Shake:0`), RTS e DTR baixados ao abrir, filas 4096/4096 | PROVADO | LN idx 63, 185, 217434, 551365 (`SET_BAUD_RATE 9600`), `SET_LINE_CONTROL StopBits:1 Parity:NONE WordLength:8`, `CLR_RTS`, `CLR_DTR` em toda abertura |
| Sondagem inicial | antes da sessão o ProgBase abre a porta a **38400**, envia um único byte `00`, recebe `80`, fecha e reabre a 9600 (também sonda `Serial1` com `00` e `00 02 02`, que dão TIMEOUT) | PROVADO | LN seq 1 / idx 34 (`00` → `80` a 38400); idx 15/53 em `Serial1` |
| Purge | `IOCTL_SERIAL_PURGE TXCLEAR RXCLEAR` antes de **toda** escrita (20.288 purges para 20.288 escritas) | PROVADO | LN (contagem); `1.LOG` eventos 0, 44, 88… |
| Timeouts seriais (`COMMTIMEOUTS`) | esperando o status: `RI:100 RM:50 RC:1200 WM:50 WC:50`; depois: `RI:100 RM:50 RC:200`. Uma única vez `RC:40` dentro do primeiro `00 02 02` | PROVADO | LN idx 77 (RC:40), 20.286× RC:1200, 20.294× RC:200; `1.LOG` eventos 4–7 |
| Padrão de I/O por transação | PURGE → WRITE req → READ 1 (eco byte 0) → READ n−1 (resto do eco) → GET/SET_TIMEOUTS RC:1200 → READ 1 (status) → SET_TIMEOUTS RC:200 → READ 1 (len) → READ 1 × len → READ 1 (checksum) | PROVADO | LN idx 7294–7345; `1.LOG` eventos 1–43 |
| Duração de um quadro `48 01` | mediana 43,1 ms de IRP (LN); ~45,8 ms entre quadros no `PortmonAUTOCAL` | PROVADO | LN (9.408 quadros); `progbase-autocal-consumer-map-v1.json#compact_fixture_median_gap_ms` |

## 1.2 Formato do quadro

**Requisição** = `cmd arg… cs`, `cs = soma(bytes anteriores) mod 256`. `PROVADO`: 20.287/20.287 requisições multi-byte do LN fecham a soma; o mesmo em `full-corpus-manifest.json` (PortmonAUTOCAL).

**Resposta** = `eco literal da requisição` + `status` + `len` + `payload[len]` + `cs`, `cs = (status + len + soma(payload)) mod 256`. `PROVADO`: 20.287/20.287 no LN; 36.457/36.463 no PortmonAUTOCAL (6 exceções nas seq 16110–16115, não abertas). Exemplo: `29 1D 00 46 | 53 05 00 00 00 00 00 | 58`.

Status observados: **só `53` (ACK) e `CA`** (ver 1.5). Nenhum outro byte de status existe nas duas capturas.

Tamanhos: telemetria 40 bytes (3 eco + `53` + `22` + 34 + cs); vetor 18×U16 → `len 24`; 30×U16 → `len 3C`; linha do Mapa K → `len 0C`; escalar U8 → `len 01`; U16 → `len 02`.

## 1.3 Gramática dos comandos

Todo parâmetro tem um `SerialCode` (SC) de 16 bits, enviado **little-endian** (`lo hi`). Opcode de escrita = `0x11 + nº de bytes de dado`; `SetVector` curto = `0x31 + nº de bytes`.

| Operação | Corpo (sem checksum) | Payload da resposta | Selo | Fonte |
|---|---|---|---|---|
| Telemetria ao vivo | `48 01` | 34 bytes (`telemetria.md`) | PROVADO | 9.408× no LN; todas as capturas |
| Status compacto AutoCal | `48 0B` | 14 bytes: byte 12 = `AUTO_CAL_ENABLE`, byte 13 = contador AutoMatch | PROVADO | LN 23× (`53 0E 00×12 01 03` com AutoCal ligado e 3 AutoMatch; `00 00` desligado); epochs fixture |
| Status secundário | `48 08` | **sempre `CA 01 10`** nesta ECU (3.334×) | PROVADO | LN seq 444 em diante; o ProgBase insiste nele intercalado com `48 01` |
| Ler escalar (`GetNumber`) | `09 lo hi` | 1 ou 2 bytes (`DataLength`) | PROVADO | `09 79 00 82 → 53 02 00 0A` (LN seq 28); `09 21 00 2A → 53 01 00` |
| Ler elemento de vetor | `0A lo hi idx` | 1 elemento | PROVADO | `0A 73 01 00 7E → 53 01 04` (LN seq 19, `MODULE_VERSION`=4); `0A 14 01 01 20 → 53 02 33 13` |
| Ler vetor (`GetVector`) | `29 lo hi` | `n × DataLength`, LE | PROVADO | `29 4B 01 75 → 53 3C 00 01 00 02 …` (LN seq ~ e fixture reference) |
| Ler linha de matriz | `2A lo hi row` | uma linha | PROVADO | `2A 54 00 00 7E → 53 0C A2×12` (LN seq 36); 13 linhas `00..0C` |
| Escrever escalar U8 | `12 lo hi v` | vazio (`53 00`) | PROVADO | `12 4A 01 00 5D` (LN seq 12615), `12 4A 01 01 5E` (seq 12691) |
| Escrever 2 bytes (U16 ou U8 indexado) | `13 lo hi b0 b1` | `53 00` | INFERIDO | regra do opcode; não há `13` escrito no LN |
| Escrever 3 bytes: elemento U16 indexado ou célula de matriz | `14 lo hi idx lo hi` / `14 lo hi row col v` | `53 00` | PROVADO | `14 3D 00 00 52 03 A6` (eixo RPM, LN seq 15226); `14 54 00 00 00 64 CC` (Mapa K, seq 1054); Reset K `14 61 01 00 00 40 B6` (desmontagem) |
| `SetVector` curto (≤5 bytes) | `(31+n) lo hi dado[n]` | `53 00` | PROVADO (n=4) | `35 03 00 86 2C 51 10 4B` (LN seq 719); regra geral INFERIDA |
| `SetVector` longo | `37 lo (n+2) hi dado[n]` | `53 00` | INFERIDO | desmontagem (`progbase-autocal-dump-contract-v1.json#setVectorExtended`); nenhum `37` no LN |
| Ação AutoCal | `02 24 04 modo` | `53 00` | PROVADO | `02 24 04 04 2E → 53 00` (LN seq 1487 / idx 34971); modos em `autocal.md` |
| Commit de apagar pontos | `01 24 05` | `53 00` | INFERIDO | desmontagem (`AutoCalPointDeleteProtocol.commit`); não ocorre no LN |
| Calibração clássica (`TFormCalibra`) | `00 13` (início) e `00 14` (poll) | `00 14 → 53 02 sel 00`, `sel` 1…9, 0x0C | PROVADO | LN seq 1903 (`00 13 13 → 53 00`), 4.693× `00 14 14`; `progbase-classic-calibration-v1.json` |
| Polls de diagnóstico da tela | `29 1D 00` (`STATO_DIAGNOSI`, 5×U8), `09 21 00` (`DIAGNOSI_INJ_BENZ`) | sempre zeros nas capturas | PROVADO | 974× / 194× no LN; 5.285× / 1.057× no PortmonAUTOCAL. Ignorados de propósito |

## 1.4 Sessão: conexão, dump inicial, desconexão

Sequência real, `PROVADO` (LN seq 2–30, repetida em cada reconexão nas seq 7549–7561 e 19890–19902):

```
00 02 02      -> 53 04 FE 4F 45 0B     (4 bytes; identidade/versão da ECU, semântica DESCONHECIDA)
01 00 3A 3B   -> 53 00
00 25 25      -> 53 01 02              (1 byte = 2)
00 02 02      -> 53 04 FE 4F 45 0B     (repetido)
09 33 00 3C   -> 53 02 00 00           (SC 51 SUB_CLIENT_CODE)
09 40 00 49   -> 53 01 FE              (SC 64 CODICE_CLIENTE)
09 4C 00 55   -> 53 02 01 04           (SC 76 MINIMA_VERSIONE_INTERFACCIA = 0x0401)
09 5B 00 64   -> 53 02 01 04           (SC 91 WARNING_VERSIONE_INTERFACCIA)
00 01 01      -> 53 00                 (fecha; IRP_MJ_CLOSE; reabre a 9600)
01 00 3A 3B / 00 25 25 / 09 02 00 0B -> 53 01 AC (SC 2 REGISTRO_EE) / 00 02 02
09 33 00 / 29 03 00 2C -> 53 04 86 24 51 10 (FLAG_CONF1) / 09 01 00 (REGISTRO_INIT=0)
29 94 00 / 0A 73 01 00 -> 04 (MODULE_VERSION) / 09 7B 01 ×3 / 29 00 00 -> 20 bytes ASCII (identificador)
29 C0 00 (FLAG_CONF2) / 29 03 00 / 29 FA 00 (FLAG_CONF3) / 09 79 00 -> 53 02 00 0A (BASE_TEMPI_GLOBALE = 2560)
09 86 00 / 29 8A 00 / 29 8B 00 / 01 04 54 -> CA 01 10 / 29 37 00 (TEMPI_PER_K) …
```

Depois disso o ProgBase lê **todos** os parâmetros do modelo (seq 31–410: 365 requisições distintas, 236 SC diferentes, inclusive as 13 linhas do Mapa K) e só então começa a telemetria `48 01` (seq 411 / idx 7295). O mesmo dump completo se repete a cada reconexão.

Desconexão (`PROVADO`): `01 12 00 13 → 53 00`, `00 01 01 → 53 00`, `IRP_MJ_CLEANUP/CLOSE` (LN seq 7549–7550 e 19890–19891). Na primeira sessão (sem escrita) só `00 01 01`. `INFERIDO`: `01 12 00` é "encerrar programação/gravar" e só é enviado quando houve escrita de parâmetro na sessão (as duas ocorrências vêm logo após blocos `14 3D 00`/`14 37 00`). A captura termina no meio da telemetria, sem desconexão final.

O OMEGAS usa exatamente `00 02 02`, `01 00 3A 3B`, `00 25 25`, `00 01 01` (`Mp48Protocol.CMD_INIT_1/2, CMD_IDENTIFY, CMD_DISCONNECT`): compatível com o original. Não envia `01 12 00` nem a sondagem a 38400.

## 1.5 Status `CA`: o que significa de verdade

`PROVADO` (LN): todas as 3.499 respostas não-ACK são **exatamente `CA 01 10 DB`**. Elas vêm de 56 requisições distintas e são **estáveis e repetíveis** por SC: `48 08` (sempre), `01 04 54`, SC 313/314/315/316/317/318 (`TANK_VOL`, `NORM_*`, `INJR_*`), SC 356 (`VECT_AUTOCAL_EE`), SC 181 (`TIPO_CARBURANTE`), SC 400 (`0A 90 01`), SC 44 (`2A 2C 00`), SC 291–299 indexados, etc. O ProgBase continua a sessão normalmente depois de cada `CA`.

Conclusão: `INFERIDO` com força: `CA 01 10` = "objeto/comando não suportado por esta ECU ou sem dados" (NACK leve), **não** um erro de sessão. `CA 01 08` não aparece em nenhuma das duas capturas (`DESCONHECIDO`). A regra do OMEGAS "CA 01 10 = não-retryable, exige nova sessão USB" (`UsbProtocolReply.kt`) é mais severa que o comportamento do original e vale ser contrastada.

## 1.6 `FLAG_CONF1` (SC 3) e o "modo de inserção K"

- `PROVADO`: `29 03 00 2C → 53 04 86 24 51 10` (LN seq 16, 26, 7555, 19896): `FLAG_CONF1 = [0x2486, 0x1051]`. O `.lec` do carro traz `Flag1=9350` (`0x2486`) e `Flag2=4177` (`0x1051`) (`fontes/lec-208-pegeot-pos-cali.md`). Mesmos valores.
- `PROVADO`: o ProgBase escreve esse vetor com `35 03 00 86 xx 51 10`, mudando só o byte alto da primeira palavra: `2C` (bit 0x0800 ligado, 8×), `24` (bits desligados, 4×), `3C` (bits 0x0800+0x1000, 6×), `34` (só 0x1000, 1×). Ex.: seq 719 (`2C`), 1332 (`24`), 8548 (`3C`), 14825 (`34`).
- `PROVADO`: o bloco de 144 escritas do Mapa K (seq 1054–1197) ocorre **entre** `2C` (seq 719) e `24` (seq 1332), com telemetria normal no meio dos dois flags (mas não dentro do bloco).
- `INFERIDO`: bit 0x0800 = "modo de edição/inserção do mapa" (o OMEGAS o chama de K insertion); bit 0x1000 = segundo modo ligado pelo ProgBase em outras telas (semântica DESCONHECIDA; aparece com `3C`/`34`).
- Ponto de contraste: `Mp48Protocol.kInsertionMode` envia `86 2C 51 10`/`86 24 51 10` **fixos**, isto é, o `FLAG_CONF1` deste carro. Em outra ECU reescreveria flags alheios. O original lê `29 03 00` no dump inicial e escreve o valor lido com o bit alterado.

## 1.7 Tempos, cadências, retries

| Item | Valor | Selo | Fonte |
|---|---|---|---|
| Timer de apresentação | `TimerDati.Interval = 75 ms` (`TFormConfig`); `Timer1.Interval = 75` (`TFormVisualizza`); `refresh_time_ms=300` da tela AutoCal (`autocalcfg.ini`) | PROVADO | DFM `RCDATA_TFORMVISUALIZZA_0.dfm.txt`; consumer-map `presentation`; `autocalcfg.ini` |
| Laço serial em regime | `48 01` alternado 1:1 com `48 08` (ou com `00 14` quando a calibração clássica está aberta); leituras secundárias intercaladas | PROVADO | LN seq 715–723, 1905–1909 |
| Família de aquisição AutoCal (`0x015B…0x0163`, `0x016F/0x0170`) | ~2,0 s (LN: `29 5B 01` 2,04 s; `29 61 01` 2,05 s) | PROVADO | LN; consumer-map |
| Família de referência (`0x018D/0x018E`) | ~4,0 s (LN 4,05 s) | PROVADO | LN; consumer-map |
| Escrita em bloco | 144 escritas `14 54 00` consecutivas sem nenhuma telemetria entre elas; 24 escritas de eixo idem | PROVADO | LN seq 1054–1197, 7525–7548, 15226–15249 |
| Retries | nenhuma requisição repetida após falta de resposta; só 3 TIMEOUT em todo o LN e todos na sondagem `Serial1` | PROVADO (ausência) | LN |
| Readback após escrita | não há releitura imediata depois do bloco K nem depois de `12 4A 01`; o ProgBase volta à telemetria e relê os objetos no ciclo normal | PROVADO | LN seq 1198, 12616 |

## 1.8 O que o OMEGAS faz diferente (anotação; contraste fica para depois)

- Timeouts: 1800 ms por transação, eco 900 ms, payload ≤192 (`UsbSerialManager.transaction`). Original: 1200 ms até o status, 200 ms depois.
- Purge: OMEGAS purga antes de cada transação, exceto dentro das escritas K; original purga antes de cada escrita, inclusive no bloco de 144.
- `CA 01 10`: OMEGAS trata como fatal de sessão; original continua.
- Flag K: OMEGAS grava valores fixos de `FLAG_CONF1`; original grava o valor lido com o bit alterado.
- Não envia `01 12 00` antes de desconectar; original envia quando escreveu.
