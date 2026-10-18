# Matriz comando x resposta (gerado — não editar à mão)

Gerado por `tools/progbase/protocolo_matriz.py`. Cada captura é lida do zero (nada é copiado de documentos anteriores). Linhas citadas = número da linha do arquivo `.LOG`.

## Captura LN

Fonte: captura Portmon (sha256 `43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64`), 39524 transações (um IRP_MJ_WRITE do ProgBase + as leituras seguintes), 7 aberturas de porta. Marca: **OBS-FIO**.

Classes de resposta: `ACK_53` = `53 n dados ck` válido; `NAK_CA` = `CA s1 s2 ck`; `SEM_RESPOSTA` = timeout; `WAKE_80` = `80` sem eco. Totais: ACK_53=36016, ECO_DIVERGENTE=1, NAK_CA=3499, SEM_RESPOSTA=7, WAKE_80=1.

ECU emulada (`tools/emulacao/ecu_emulator.py`): 21506 pares distintos regenerados, 0 divergentes.

### Aberturas de porta

| # | dispositivo | baud | escritas | linha de abertura | RC de leitura vistos |
|---|---|---|---|---|---|
| 1 | Serial1 | 38400 | 1 | 2 | 200 ms x1 |
| 2 | Silabser0 | 38400 | 1 | 40 | 200 ms x1 |
| 3 | Serial1 | 9600 | 1 | 78 | 200 ms x1 |
| 4 | Silabser0 | 9600 | 9 | 118 | 1200 ms x8, 200 ms x10, 40 ms x1 |
| 5 | Silabser0 | 9600 | 7540 | 362 | 1200 ms x7540, 200 ms x7541 |
| 6 | Silabser0 | 9600 | 12341 | 434860 | 1200 ms x12341, 200 ms x12342 |
| 7 | Silabser0 | 9600 | 19631 | 1102722 | 1200 ms x19630, 200 ms x19631 |

### Famílias de comando

| família | total | ACK | NAK | sem resp. | outras | pedidos distintos | pares (pedido,resposta) distintos regenerados | 1ª linha ACK | 1ª linha NAK |
|---|---|---|---|---|---|---|---|---|---|
| `00 (1 byte)` | 2 | 0 | 0 | 1 | 1 | 1 | 1/1 | — | — |
| `00 01 /3` | 3 | 3 | 0 | 0 | 0 | 1 | 1/1 | 340 | — |
| `00 02 /3` | 4 | 3 | 0 | 1 | 0 | 1 | 1/1 | 148 | — |
| `00 13 /3` | 6 | 6 | 0 | 0 | 0 | 1 | 1/1 | 103458 | — |
| `00 14 /3` | 4693 | 4693 | 0 | 0 | 0 | 1 | 14/14 | 103566 | — |
| `00 25 /3` | 4 | 4 | 0 | 0 | 0 | 1 | 1/1 | 196 | — |
| `01 00 /4` | 4 | 4 | 0 | 0 | 0 | 1 | 1/1 | 176 | — |
| `01 04 /4` | 3 | 0 | 3 | 0 | 0 | 1 | 1/1 | — | 992 |
| `01 12 /4` | 2 | 2 | 0 | 0 | 0 | 1 | 1/1 | 434818 | — |
| `02 24 /5` | 1 | 1 | 0 | 0 | 0 | 1 | 1/1 | 69944 | — |
| `09 /4` | 1047 | 1005 | 42 | 0 | 0 | 100 | 106/106 | 246 | 3544 |
| `0A /5` | 299 | 251 | 48 | 0 | 0 | 98 | 98/98 | 582 | 9030 |
| `12 /5` | 8 | 8 | 0 | 0 | 0 | 2 | 2/2 | 697674 | — |
| `14 /7` | 216 | 216 | 0 | 0 | 0 | 171 | 171/171 | 48568 | — |
| `29 /4` | 8782 | 8728 | 54 | 0 | 0 | 110 | 806/806 | 508 | 3736 |
| `2A /5` | 207 | 189 | 18 | 0 | 0 | 69 | 95/95 | 1218 | 3858 |
| `35 /8` | 19 | 19 | 0 | 0 | 0 | 4 | 4/4 | 31284 | — |
| `48 /3` | 24224 | 20884 | 3334 | 5 | 1 | 3 | 20201/20201 | 14544 | 17140 |

### Exemplos reais (primeira ocorrência de cada classe)

#### `00 (1 byte)`

- SEM_RESPOSTA — linha 32 (abertura 1): `00` -> ``
- WAKE_80 — linha 70 (abertura 2): `00` -> `80`

#### `00 01 /3`

- ACK_53 — linha 340 (abertura 4): `00 01 01` -> `00 01 01 53 00 53`

#### `00 02 /3`

- ACK_53 — linha 148 (abertura 4): `00 02 02` -> `00 02 02 53 04 FE 4F 45 0B F4`
- SEM_RESPOSTA — linha 108 (abertura 3): `00 02 02` -> ``

#### `00 13 /3`

- ACK_53 — linha 103458 (abertura 5): `00 13 13` -> `00 13 13 53 00 53`

#### `00 14 /3`

- ACK_53 — linha 103566 (abertura 5): `00 14 14` -> `00 14 14 53 02 02 00 57`

#### `00 25 /3`

- ACK_53 — linha 196 (abertura 4): `00 25 25` -> `00 25 25 53 01 02 56`

#### `01 00 /4`

- ACK_53 — linha 176 (abertura 4): `01 00 3A 3B` -> `01 00 3A 3B 53 00 53`

#### `01 04 /4`

- NAK_CA — linha 992 (abertura 5): `01 04 54 59` -> `01 04 54 59 CA 01 10 DB`
- NAK observados: `CA 01 10 DB` x3

#### `01 12 /4`

- ACK_53 — linha 434818 (abertura 5): `01 12 00 13` -> `01 12 00 13 53 00 53`

#### `02 24 /5`

- ACK_53 — linha 69944 (abertura 5): `02 24 04 04 2E` -> `02 24 04 04 2E 53 00 53`

#### `09 /4`

- ACK_53 — linha 246 (abertura 4): `09 33 00 3C` -> `09 33 00 3C 53 02 00 00 55`
- NAK_CA — linha 3544 (abertura 5): `09 90 01 9A` -> `09 90 01 9A CA 01 10 DB`
- NAK observados: `CA 01 10 DB` x42

#### `0A /5`

- ACK_53 — linha 582 (abertura 5): `0A 73 01 00 7E` -> `0A 73 01 00 7E 53 01 04 58`
- NAK_CA — linha 9030 (abertura 5): `0A 23 01 01 2F` -> `0A 23 01 01 2F CA 01 10 DB`
- NAK observados: `CA 01 10 DB` x48

#### `12 /5`

- ACK_53 — linha 697674 (abertura 6): `12 4A 01 00 5D` -> `12 4A 01 00 5D 53 00 53`

#### `14 /7`

- ACK_53 — linha 48568 (abertura 5): `14 54 00 00 00 64 CC` -> `14 54 00 00 00 64 CC 53 00 53`

#### `29 /4`

- ACK_53 — linha 508 (abertura 5): `29 03 00 2C` -> `29 03 00 2C 53 04 86 24 51 10 62`
- NAK_CA — linha 3736 (abertura 5): `29 26 00 4F` -> `29 26 00 4F CA 01 10 DB`
- NAK observados: `CA 01 10 DB` x54

#### `2A /5`

- ACK_53 — linha 1218 (abertura 5): `2A 54 00 00 7E` -> `2A 54 00 00 7E 53 0C A2 A2 A2 A2 A2 A2 A2 A2 A2 A2 A2 A2 F7`
- NAK_CA — linha 3858 (abertura 5): `2A 2C 00 00 56` -> `2A 2C 00 00 56 CA 01 10 DB`
- NAK observados: `CA 01 10 DB` x18

#### `35 /8`

- ACK_53 — linha 31284 (abertura 5): `35 03 00 86 2C 51 10 4B` -> `35 03 00 86 2C 51 10 4B 53 00 53`

#### `48 /3`

- ACK_53 — linha 14544 (abertura 5): `48 0B 53` -> `48 0B 53 53 0E 00 00 00 00 00 00 00 00 00 00 00 00 01 03 65`
- ECO_DIVERGENTE — linha 2631642 (abertura 7): `48 01 49` -> `48`
- NAK_CA — linha 17140 (abertura 5): `48 08 50` -> `48 08 50 CA 01 10 DB`
- SEM_RESPOSTA — linha 2631650 (abertura 7): `48 01 49` -> ``
- NAK observados: `CA 01 10 DB` x3334

## Captura AC

Fonte: captura Portmon (sha256 `4a70f5ae79b1d688c05bd169f3e6a588b52105580d24b8a72a5cff398a384c0b`), 36463 transações (um IRP_MJ_WRITE do ProgBase + as leituras seguintes), 2 aberturas de porta. Marca: **OBS-FIO**.

Classes de resposta: `ACK_53` = `53 n dados ck` válido; `NAK_CA` = `CA s1 s2 ck`; `SEM_RESPOSTA` = timeout; `WAKE_80` = `80` sem eco. Totais: ACK_53=36456, ECO_DIVERGENTE=6, NAK_CA=1.

ECU emulada (`tools/emulacao/ecu_emulator.py`): 22315 pares distintos regenerados, 0 divergentes.

### Aberturas de porta

| # | dispositivo | baud | escritas | linha de abertura | RC de leitura vistos |
|---|---|---|---|---|---|
| 1 | Silabser0 | 9600 | 1 | 1262478 | 1200 ms x1, 200 ms x2 |
| 2 | Silabser0 | 9600 | 20347 | 1262532 | 1200 ms x20347, 200 ms x20348 |

### Famílias de comando

| família | total | ACK | NAK | sem resp. | outras | pedidos distintos | pares (pedido,resposta) distintos regenerados | 1ª linha ACK | 1ª linha NAK |
|---|---|---|---|---|---|---|---|---|---|
| `00 25 /3` | 1 | 1 | 0 | 0 | 0 | 1 | 1/1 | 1262582 | — |
| `01 00 /4` | 2 | 1 | 1 | 0 | 0 | 1 | 2/2 | 1262562 | 1262508 |
| `02 24 /5` | 1 | 1 | 0 | 0 | 0 | 1 | 1/1 | 52478 | — |
| `09 /4` | 1060 | 1060 | 0 | 0 | 0 | 2 | 4/4 | 980 | — |
| `29 /4` | 13487 | 13487 | 0 | 0 | 0 | 14 | 1160/1160 | 830 | — |
| `48 /3` | 21912 | 21906 | 0 | 0 | 6 | 2 | 21147/21147 | 38 | — |

### Exemplos reais (primeira ocorrência de cada classe)

#### `00 25 /3`

- ACK_53 — linha 1262582 (abertura 2): `00 25 25` -> `00 25 25 53 01 02 56`

#### `01 00 /4`

- ACK_53 — linha 1262562 (abertura 2): `01 00 3A 3B` -> `01 00 3A 3B 53 00 53`
- NAK_CA — linha 1262508 (abertura 1): `01 00 3A 3B` -> `01 00 3A 3B CA 01 08 D3`
- NAK observados: `CA 01 08 D3` x1

#### `02 24 /5`

- ACK_53 — linha 52478 (abertura 0): `02 24 04 04 2E` -> `02 24 04 04 2E 53 00 53`

#### `09 /4`

- ACK_53 — linha 980 (abertura 0): `09 21 00 2A` -> `09 21 00 2A 53 01 00 54`

#### `29 /4`

- ACK_53 — linha 830 (abertura 0): `29 1D 00 46` -> `29 1D 00 46 53 05 00 00 00 00 00 58`

#### `48 /3`

- ACK_53 — linha 38 (abertura 0): `48 01 49` -> `48 01 49 53 22 65 03 32 1F 00 00 00 00 EC 06 00 80 2C B1 FA 09 39 93 01 00 00 00 00 00 00 00 00 00 EA 06 00 00 00 00 3D`
- ECO_DIVERGENTE — linha 1262428 (abertura 0): `48 01 49` -> `48 01`

