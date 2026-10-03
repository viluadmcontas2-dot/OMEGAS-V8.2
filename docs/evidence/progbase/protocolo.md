# Tema 1 — Transporte e protocolo (ProgBase 4.2.0.6 ↔ MP48)

Selos: `PROVADO` = visto na fiação (LN) ou no DUMP sem ambiguidade; `INFERIDO` = dedução com raciocínio em uma linha; `DESCONHECIDO` = lacuna, com o que provaria. Fontes e notação em `fontes/INDICE-FONTES.md`. Comandos AutoCal em `autocal.md`; Mapa K/Curva K em `curvas-mapas.md`; nível em `level.md`.

Notação: `LN seq N / idx M` = transação N (ordem das escritas `IRP_MJ_WRITE` com resposta) e índice IRP M do `PortmonLOGNOVO.LOG` contido em `PortmonLOGNOVO (1).zip`. O LOG completo foi parseado: 149.911.521 B, 2.631.711 linhas e 39.517 transações. Seq 1 é sonda sem quadro; seq 39517 é resposta incompleta no EOF. As contagens históricas abaixo, quando não marcadas como completas, referem-se ao prefixo seq 1–20288. Resultado integral em 1.8. `DUMP/<arquivo>` = arquivo da pasta DUMP do Drive; `@0x…` = offset zero-based dentro do recurso; `VA` = endereço virtual em `DUMP/ProgBase.exe.Dump.bin` (ImageBase `0x00400000`).

## 1.1 Transporte

| Item | Valor | Selo | Fonte |
|---|---|---|---|
| Porta | serial USB Silicon Labs CP210x (`Silabser0` no Portmon) | PROVADO | LN idx 19/58/180 (`IRP_MJ_CREATE Silabser0`) |
| Velocidade e formato | **9600 bps, 8 bits, 1 stop, sem paridade**; sem handshake de hardware (`HANDFLOW Shake:0`), RTS e DTR baixados ao abrir, filas 4096/4096 | PROVADO | LN idx 63, 185, 217434, 551365 (`SET_BAUD_RATE 9600`), `SET_LINE_CONTROL StopBits:1 Parity:NONE WordLength:8`, `CLR_RTS`, `CLR_DTR` em toda abertura |
| Sondagem inicial | antes da sessão o ProgBase abre a porta a **38400**, envia um único byte `00`, recebe `80`, fecha e reabre a 9600 (também sonda `Serial1` com `00` e `00 02 02`, que dão TIMEOUT) | PROVADO | LN seq 1 / idx 34 (`00` → `80` a 38400); idx 15/53 em `Serial1` |
| Purge | `IOCTL_SERIAL_PURGE TXCLEAR RXCLEAR` antes de **toda** escrita (20.288 purges para 20.288 escritas no prefixo) | PROVADO | LN (contagem) |
| Timeouts seriais (`COMMTIMEOUTS`) | esperando o status: `RI:100 RM:50 RC:1200 WM:50 WC:50`; depois: `RI:100 RM:50 RC:200`. Uma única vez `RC:40` dentro do primeiro `00 02 02` | PROVADO | LN idx 77 (RC:40), 20.286× RC:1200, 20.294× RC:200 |
| Padrão de I/O por transação | PURGE → WRITE req → READ 1 (eco byte 0) → READ n−1 (resto do eco) → GET/SET_TIMEOUTS RC:1200 → READ 1 (status) → SET_TIMEOUTS RC:200 → READ 1 (len) → READ 1 × len → READ 1 (checksum) | PROVADO | LN idx 7294–7345 |
| Duração de um quadro `48 01` | mediana 43,1 ms de IRP (9.408 quadros no prefixo) | PROVADO | LN |

## 1.2 Formato do quadro

**Requisição** = `cmd arg… cs`, `cs = soma(bytes anteriores) mod 256`. `PROVADO`: 39.516/39.516 requisições multi-byte do LN completo fecham a soma.

**Resposta** = `eco literal da requisição` + `status` + `len` + `payload[len]` + `cs`, `cs = (status + len + soma(payload)) mod 256`. `PROVADO`: 39.515/39.515 respostas enquadradas no LN completo (sonda e EOF excluídos). Exemplo: `29 1D 00 46 | 53 05 00 00 00 00 00 | 58`.

Status observados: **só `53` (ACK) e `CA`** (ver 1.5). Nenhum outro byte de status existe no LN.

Tamanhos: telemetria 40 bytes (3 eco + `53` + `22` + 34 + cs); vetor 18×U16 → `len 24`; 30×U16 → `len 3C`; linha do Mapa K → `len 0C`; escalar U8 → `len 01`; U16 → `len 02`.

## 1.3 Gramática dos comandos

Todo parâmetro tem um `SerialCode` (SC) de 16 bits, enviado **little-endian** (`lo hi`). Opcode de escrita = `0x11 + nº de bytes de dado`; `SetVector` curto = `0x31 + nº de bytes`.

| Operação | Corpo (sem checksum) | Payload da resposta | Selo | Fonte |
|---|---|---|---|---|
| Telemetria ao vivo | `48 01` | 34 bytes (`telemetria.md`) | PROVADO | 9.408× no LN |
| Status compacto AutoCal | `48 0B` | 14 bytes: byte 12 de semântica **DESCONHECIDA** (L-13); byte 13 acompanha contador AutoMatch | PROVADO | LN seq 410 (`53 0E 00×12 01 03`: AutoCal ligado, 3 AutoMatch); `00 00` após Reset All (seq 1487–1502); isso não prova enable desligado |
| Status secundário | `48 08` | **sempre `CA 01 10`** nesta ECU (3.334×) | PROVADO | LN seq 444 em diante; o ProgBase insiste nele intercalado com `48 01` |
| Ler escalar (`GetNumber`) | `09 lo hi` | 1 ou 2 bytes (`DataLength`) | PROVADO | `09 79 00 82 → 53 02 00 0A` (LN seq 28); `09 21 00 2A → 53 01 00` |
| Ler elemento de vetor | `0A lo hi idx` | 1 elemento | PROVADO | `0A 73 01 00 7E → 53 01 04` (LN seq 19, `MODULE_VERSION`=4); `0A 2C 01 00..04` (seq 298–302) |
| Ler vetor (`GetVector`) | `29 lo hi` | `n × DataLength`, LE | PROVADO | `29 4B 01 75 → 53 3C 00 01 00 02 …` (LN seq 342 área; idx 10142 primeira ocorrência) |
| Ler linha de matriz | `2A lo hi row` | uma linha | PROVADO | `2A 54 00 00 7E → 53 0C A2×12` (LN seq 36); 13 linhas `00..0C` |
| Escrever escalar U8 | `12 lo hi v` | vazio (`53 00`) | PROVADO | `12 4A 01 00 5D` (LN seq 12615), `12 4A 01 01 5E` (seq 12691) |
| Escrever 2 bytes (U16 ou U8 indexado) | `13 lo hi b0 b1` | `53 00` | INFERIDO | regra do opcode; não há `13` escrito no LN |
| Escrever 3 bytes: elemento U16 indexado ou célula de matriz | `14 lo hi idx lo hi` / `14 lo hi row col v` | `53 00` | PROVADO | `14 3D 00 00 52 03 A6` (eixo RPM, LN seq 15226); `14 54 00 00 00 64 CC` (Mapa K, seq 1054) |
| `SetVector` curto (≤5 bytes) | `(31+n) lo hi dado[n]` | `53 00` | PROVADO (n=4) | `35 03 00 86 2C 51 10 4B` (LN seq 719); regra geral INFERIDA pelo opcode |
| `SetVector` longo | `37 …` | `53 00` | DESCONHECIDO | nenhum `37` no LN; o método `TAebProtocol.SetVector` existe (`DUMP/ProgBase.exe.ExportFunctions.txt`) mas o formato longo não foi visto na fiação |
| Ação AutoCal | `02 24 04 modo` | `53 00` | PROVADO | `02 24 04 04 2E → 53 00` (LN seq 1487 / idx 34971); modos em `autocal.md` |
| Commit de apagar pontos | `01 24 05` | — | INFERIDO | desmontagem de `ActionDeleteSelectedPointsExecute` (`autocal.md` 4.3); não ocorre no LN |
| Calibração clássica (`TFormCalibra`) | `00 13` (início) e `00 14` (poll) | `00 14 → 53 02 sel 00`, `sel` 1…9, 0x0C | PROVADO | LN seq 1903 (`00 13 13 → 53 00`), 4.693× `00 14 14` |
| Polls de diagnóstico da tela | `29 1D 00` (`STATO_DIAGNOSI`, 5×U8), `09 21 00` (`DIAGNOSI_INJ_BENZ`) | sempre zeros | PROVADO | 974× / 194× no LN. Ignorados de propósito |

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
29 94 00 -> 53 02 1B 25 / 0A 73 01 00 -> 04 (MODULE_VERSION) / 09 7B 01 ×3
29 00 00 -> 53 14 + 20 bytes ASCII "4Z03xAcgHb7Fu0rYejAs" (identificador; seq 24)
29 C0 00 (FLAG_CONF2) / 29 03 00 / 29 FA 00 (FLAG_CONF3) / 09 79 00 -> 53 02 00 0A (BASE_TEMPI_GLOBALE = 2560)
09 86 00 / 29 8A 00 / 29 8B 00 / 01 04 54 -> CA 01 10 / 29 37 00 (TEMPI_PER_K) …
```

Depois disso o ProgBase lê **todos** os parâmetros do modelo (seq 31–410: 365 requisições distintas, 236 SC diferentes, inclusive as 13 linhas do Mapa K) e só então começa a telemetria `48 01` (seq 411 / idx 7295). O mesmo dump completo se repete a cada reconexão.

Desconexão (`PROVADO`): `01 12 00 13 → 53 00`, `00 01 01 → 53 00`, `IRP_MJ_CLEANUP/CLOSE` (LN seq 7549–7550 e 19890–19891). Na primeira sessão (sem escrita) só `00 01 01`. `INFERIDO`: `01 12 00` é "encerrar programação/gravar" e só é enviado quando houve escrita de parâmetro na sessão (as duas ocorrências vêm logo após blocos `14 3D 00`/`14 37 00`). O arquivo completo termina no meio da última resposta de telemetria (seq 39517).

## 1.5 Status `CA`: o que significa de verdade

`PROVADO` (LN): todas as 3.499 respostas não-ACK são **exatamente `CA 01 10 DB`**. Elas vêm de 56 requisições distintas e são **estáveis e repetíveis** por SC: `48 08` (sempre), `01 04 54`, SC 313/314/315/316/317/318 (`TANK_VOL`, `NORM_*`, `INJR_*`), SC 356 (`VECT_AUTOCAL_EE`), SC 181 (`TIPO_CARBURANTE`), SC 400 (`0A 90 01`), SC 44 (`2A 2C 00`), SC 291–299 indexados, etc. O ProgBase continua a sessão normalmente depois de cada `CA`: não repete o pedido, não reconecta, segue para a próxima requisição.

Conclusão: `INFERIDO` com força: `CA 01 10` = "objeto/comando não suportado por esta ECU ou sem dados" (NACK leve), **não** um erro de sessão. Nenhum outro status `CA xx yy` aparece no LN (`DESCONHECIDO` se existe).

## 1.6 `FLAG_CONF1` (SC 3) e o "modo de inserção K"

- `PROVADO`: `29 03 00 2C → 53 04 86 24 51 10` (LN seq 16, 26, 7555, 19896): `FLAG_CONF1 = [0x2486, 0x1051]` (`TAebVector` de 2×U16 no DFM `DUMP/RT_RCDATA(10)__TSTREAMDATI__0.bin`).
- `PROVADO`: o ProgBase escreve esse vetor com `35 03 00 86 xx 51 10`, mudando só o byte alto da primeira palavra: `2C` (bit 0x0800 ligado, 8×), `24` (bits desligados, 4×), `3C` (bits 0x0800+0x1000, 6×), `34` (só 0x1000, 1×). Ex.: seq 719 (`2C`), 1332 (`24`), 8548 (`3C`), 14825 (`34`).
- `PROVADO`: o bloco de 144 escritas do Mapa K (seq 1054–1197) ocorre **entre** `2C` (seq 719) e `24` (seq 1332), com telemetria normal no meio dos dois flags (mas não dentro do bloco).
- `PROVADO`: o valor escrito é sempre o valor lido no dump (`86 24 51 10`) com um ou dois bits alterados; o ProgBase nunca escreve outro conteúdo em SC 3.
- `INFERIDO`: bit 0x0800 = "modo de edição/inserção do mapa" (liga antes do bloco K, desliga depois); bit 0x1000 = segundo modo ligado em outras telas (semântica `DESCONHECIDO`; aparece com `3C`/`34`).

## 1.7 Tempos, cadências, retries

| Item | Valor | Selo | Fonte |
|---|---|---|---|
| Timer de apresentação | `Timer1.Interval = 75` ms em `TFormVisualizza`; `TimerDati` em `TFormConfig` | PROVADO (DFM) | `DUMP/RT_RCDATA(10)__TFORMVISUALIZZA__0.bin`, `__TFORMCONFIG__0.bin` (offsets em `telemetria.md` 2.1) |
| Laço serial em regime | `48 01` alternado 1:1 com `48 08` (ou com `00 14` quando a calibração clássica está aberta); leituras secundárias intercaladas | PROVADO | LN seq 715–723, 1905–1909 |
| Família de aquisição AutoCal (`0x015B…0x0163`, `0x016F/0x0170`) | estimativa histórica ~2 s por soma de durações de IRP; **tempo de parede não provado** | INFERIDO | request_timestamp é 0 em todo o LN; confirmar no scheduler DUMP |
| Família de referência (`0x018D/0x018E`) | estimativa histórica ~4 s por durações de IRP; **tempo de parede não provado** | INFERIDO | confirmar scheduler DUMP |
| Escrita em bloco | 144 escritas `14 54 00` consecutivas sem nenhuma telemetria entre elas; 24 escritas de eixo idem | PROVADO | LN seq 1054–1197, 7525–7548, 15226–15249 |
| Retries | nenhuma requisição repetida após falta de resposta; só 3 TIMEOUT em todo o prefixo e todos na sondagem `Serial1` | PROVADO (ausência) | LN |
| Readback após escrita | não há releitura imediata depois do bloco K nem depois de `12 4A 01`; o ProgBase volta à telemetria e relê os objetos no ciclo normal | PROVADO | LN seq 1198, 12616 |

## 1.8 Conferência integral do LN

- 39.517 grupos de escrita; 39.516 requisições multi-byte com checksum válido.
- 39.515 respostas com eco literal, len exato e checksum válido: **36.016 ACK 53** e **3.499 CA**, todos CA com payload `10`.
- Excluídas do formato: sonda seq 1 / idx 34 (`00 → 80`) e EOF seq 39517 / idx 1315820 (`48 01 49`, apenas `48` recebido). EOF não prova timeout/retry nem defeito da ECU.
- Telemetria: 20.451 requisições, 20.450 respostas completas. Status compacto: 434 leituras; K: 439; contador GNV: 440.
- Nenhum status adicional entre respostas completas. Isso fecha a busca no **corpus**, não o espaço de status possíveis do protocolo.
- `request_timestamp` do parser é 0.0 para todas as escritas deste arquivo. Durações de IRP não incluem necessariamente pausas do host: não calcular cadência de parede a partir delas.
- Métodos e estatísticas reproduzíveis: `python docs/evidence/progbase/fontes/validar-ln.py PortmonLOGNOVO.LOG --parser scripts/omegas/portmon_parser.py`; SHA do parser de retomada `17252b0a06e6091ae77d0d44843a8902c32d2172` (Git blob).

Resultado e trechos mínimos: `fontes/ln-validacao-completa.json`; limites e exclusões fazem parte da evidência.
