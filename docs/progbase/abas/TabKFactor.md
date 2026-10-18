# TabKFactor — K factor

Nome na tela: ita "K factor" / eng "None" / por "None" `[OBS-COD]`. Aba 10 de 16 do grupo D3a. Rotina de abertura (OnShow): `TabKFactorShow` em `0x4C49FC` `[OBS-COD]`. TabVisible de projeto: False.

Legenda das marcas: `OBS-COD` = lido do DFM/codigo do ProgBase.exe (ou executado no emulador Unicorn); `OBS-FIO` = visto no Portmon da ECU do dono; `DERIVADO` = calculado a partir dessas observacoes; `INFERIDO` = deducao; `DESCONHECIDO` = sem prova.

**Resumo**: 12 controles no DFM (TKeyComboBox=1, TLabel=6, TNumericEdit=5); 6 interativos; 4 com codigo de dado e formula; 0 com caixa/bit; 4 com valor lido no LOG. Fonte do .exe sha256 `8a2d297c8c21...`.

## Como o ProgBase trata estes controles (valido para a aba inteira)

- **Campo numerico (`TNumericEdit`)** `[OBS-COD]`: ao entrar no campo, `EditGiriCambioEnter` (0x4A98D8) mostra no rotulo `LabelLimite` o texto `(minimo;maximo)` do proprio campo; ao sair, `EditGiriCambioExit` (0x4A9CC0) esconde esse rotulo. Nada e enviado a ECU nesses dois momentos. Ao apertar **Enter**, o `TNumericEdit` limita o numero a [minimo;maximo] (so se minimo!=0 ou maximo!=0; 0x96C170) e o handler generico `EditRifLambdaKeyPress` (0x4A6A00) chama `TStreamDati.Set*` com o *codigo de dado* do campo (campo `+0x2EC`, indice `+0x2F0`; amarrados em 0x48A228): o valor e convertido pela formula do caso, gravado no cache do programa e enviado como SetNumber (escalar) ou SetNumber+indice (vetor). Depois o handler le o valor de volta do cache (`Get*`) e redesenha o campo arredondado ao passo (`Precision`, 0x96BB88). Nao ha releitura da ECU.
- **Caixa de marcar (`TCheckBox`)** `[OBS-COD]`: apertar Enter simula um clique do mouse (`CheckTpsBlocKeyPress` 0x4A8928 envia WM_LBUTTONDOWN/UP); o clique chama o handler `Check...Click`, que liga/desliga **um bit** do vetor FLAG_CONF1/2/3 (o programa regrava o vetor inteiro, ver quadros abaixo) e atualiza o que depende da caixa.
- **Lista (`TKeyComboBox`)** `[OBS-COD]`: Enter no combo chama o `...KeyPress` correspondente, que grava o indice escolhido (por codigo de dado `+0x330` ou por bit de FLAG_CONF).
- **Resposta da ECU a toda gravacao** `[OBS-FIO]`: a porta devolve o eco do quadro e a ECU responde `53 00 53` (LOG linhas 48568-48600 e 31284-31300). Em seguida o programa limpa os buffers e retoma a telemetria (`48 01 49`).
- **Conexao**: os valores 'atuais' vem de leituras `09`/`0A`/`29` feitas ao conectar (LOG de 12/08/2026); a tela do programa mostra o cache dessas leituras.

## Mapa de todos os controles (ordem do DFM)

| # | Componente | Tipo | Rotulo (por / eng / ita) | Unidade | Vetor (SC) | Bit | Visivel no DFM |
|---|---|---|---|---|---|---|---|
| 1 | `LabelKFactor` | rotulo | - / - / K factor | - | - | - | sim |
| 2 | `LabelP1` | rotulo | - / - / P1 | - | - | - | False |
| 3 | `LabelTInjK` | rotulo | - / - / T.inj. | - | - | - | False |
| 4 | `LabelK` | rotulo | - / - / K | - | - | - | False |
| 5 | `LabelP2` | rotulo | - / - / P2 | - | - | - | False |
| 6 | `LabelInjK` | rotulo | - / - / ms | - | - | - | False |
| 7 | `ComboK` | combo | - / - / K | - | - | - | False |
| 8 | `EditKMappaNeutro` | edit numerico | - / - / - | - | - | - | False |
| 9 | `EditTInj1` | edit numerico | - / - / T.inj. | - | K_FACTOR_PARAM (198) | - | False |
| 10 | `EditK1` | edit numerico | - / - / K | - | K_FACTOR_PARAM (198) | - | False |
| 11 | `EditTInj2` | edit numerico | - / - / T.inj. | ms | K_FACTOR_PARAM (198) | - | False |
| 12 | `EditK2` | edit numerico | - / - / K | - | K_FACTOR_PARAM (198) | - | False |

## Controles interativos, um a um

### `ComboK` — K

Tipo: **combo** (`TKeyComboBox`), DFM linha 5503 `[OBS-COD]`. Pai: `TabKFactor`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelK` (associacao INFERIDO)): ita = "K"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=102 Top=45 Largura=72 Altura=32; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Opcoes da lista `[OBS-COD]`: 0 = "1.00"; 1 = "1.50"; 2 = "2.00".

**O que isso faz (linguagem simples)**

- Fator K: multiplica os valores do mapa K por 1,5 ou 2; so para quando a media do mapa passa de 230-240 apos a autocalibracao e a mistura ainda esta pobre; depois e obrigatorio refazer a autocalibracao. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.25).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `ComboKKeyPress` (0x4C4880) `[OBS-COD]`
  - SetEsc codigo 55 -> vetor K_MAPPA_NEUTRO_LR (SC 122) em `0x4C48E3`
  - SetEsc codigo 55 -> vetor K_MAPPA_NEUTRO_LR (SC 122) em `0x4C48FE`
  - SetEsc codigo 55 -> vetor K_MAPPA_NEUTRO_LR (SC 122) em `0x4C491A`
  - GetEsc codigo 55 -> vetor K_MAPPA_NEUTRO_LR (SC 122) em `0x4C492D`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado` em `0x4C4955` (ComboKKeyPress) — SE eax == 0xd E sub_754CCC == 0 E SD.familia_LR[+0x80D] != 0 E sub eax, 0x40 !=  E sub eax, 0x15 !=  E sub eax, 0x2b == 
- `Checked/ItemIndex := calculado` em `0x4C496A` (ComboKKeyPress) — SE eax == 0xd E sub_754CCC == 0 E SD.familia_LR[+0x80D] != 0 E sub eax, 0x40 !=  E sub eax, 0x2b == 
- `Checked/ItemIndex := calculado` em `0x4C497F` (ComboKKeyPress) — SE eax == 0xd E sub_754CCC == 0 E SD.familia_LR[+0x80D] != 0 E sub eax, 0x2b == 
- `Checked/ItemIndex := calculado` em `0x4C4992` (ComboKKeyPress) — SE eax == 0xd E sub_754CCC == 0 E SD.familia_LR[+0x80D] != 0
- `Checked/ItemIndex := calculado` em `0x4C4A54` (TabKFactorShow) — SE SD.familia_LR[+0x80D] != 0 E sub eax, 0x40 !=  E sub eax, 0x15 !=  E sub eax, 0x2b == 
- `Checked/ItemIndex := calculado` em `0x4C4A69` (TabKFactorShow) — SE SD.familia_LR[+0x80D] != 0 E sub eax, 0x40 !=  E sub eax, 0x2b ==  E sub eax, 0x15 == 
- `Checked/ItemIndex := calculado` em `0x4C4A7E` (TabKFactorShow) — SE SD.familia_LR[+0x80D] != 0 E sub eax, 0x2b ==  E sub eax, 0x15 ==  E sub eax, 0x40 == 
- `Checked/ItemIndex := calculado` em `0x4C4A91` (TabKFactorShow) — SE SD.familia_LR[+0x80D] != 0 E sub eax, 0x15 ==  E sub eax, 0x40 ==  E sub eax, 0x2b == 

### `EditKMappaNeutro` — EditKMappaNeutro

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 5523 `[OBS-COD]`. Pai: `TabKFactor`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `None` (associacao None)): ita = "-"; eng = "-"; por = "-" `[DESCONHECIDO]`.
- Posicao no DFM: Left=102 Top=45 Largura=72 Altura=32; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 1.0, maximo 2.5, passo (Precision) 0.1. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Valor K do mapa neutro (usado como referencia do mapa). [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditKMappaNeutroKeyPress` (0x4C499C) `[OBS-COD]`
  - SetEsc codigo 97 -> vetor K_MAPPA_NEUTRO (SC 188) em `0x4C49CD`
  - GetEsc codigo 97 -> vetor K_MAPPA_NEUTRO (SC 188) em `0x4C49E0`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := falso` em `0x4C4ABE` (TabKFactorShow) — SE SD.familia_LR[+0x80D] != 0 E chamada virtual dword ptr [edx + 0x104] >= 0
- `Visible := calculado por sub_9691D4` em `0x4C4AEC` (TabKFactorShow) — SE SD.familia_LR[+0x80D] != 0 E chamada virtual dword ptr [edx + 0x104] < 0
- `Visible := calculado por sub_96BB88` em `0x4C4E5D` (TabKFactorShow) — SE SD.familia_LR[+0x80D] == 0

### `EditTInj1` — T.inj.

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 5547 `[OBS-COD]`. Pai: `TabKFactor`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTInjK` (associacao INFERIDO)): ita = "T.inj."; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=348 Top=30 Largura=72 Altura=32; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 1.0, maximo 5.0, passo (Precision) 0.02. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Primeiro ponto de tempo de injecao da curva do K de referencia. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: {"LR ([SD+0x80D]!=0)": -1, "demais ([SD+0x80D]==0)": 101}, indice 0 (campo +0x2EC (DataCode do TNumericEdit)).
  - familia LR: sem codigo nesta familia (-1)
  - Familia demais: vetor **K_FACTOR_PARAM** (SerialCode 198, TAebVector, indexado) `[OBS-COD]`.
    - tela -> ECU (setter 0x437808): `valor_bruto = trunc(((v / ((SD[0x2980] * 0.00025) * 0.004)) + 0.5))` com `v` = valor digitado.
    - ECU -> tela (getter 0x436085): `valor_na_tela = (((SD[0x2980] * 0.00025) * 0.004) * SD[0x2A70 + i*4])`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- K_FACTOR_PARAM (SC 198): bruto [586, 58, 1172, 85] (elemento 0 = 586); na tela: **nao calculado** `[OBS-FIO, tela DERIVADO]` (LOG linha 1109564).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditRifLambdaKeyPress` (0x4A6A00) `[OBS-COD]`
  - SetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A45` (handler generico)
  - GetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A64` (handler generico)
  - SetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A99` (handler generico)
  - GetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6AB1` (handler generico)
- `OnEnter` -> `EditGiriCambioEnter` (0x4A98D8) `[OBS-COD]`
- `OnExit` -> `EditGiriCambioExit` (0x4A9CC0) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Gravar valor (executado no Unicorn com o codigo original; `[OBS-COD]`):
  - maximo da faixa do DFM: tela = 5.0 -> quadro `14 C6 00 00 A1 07 82`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := falso` em `0x4C4BE9` (TabKFactorShow) — SE SD.familia_LR[+0x80D] != 0
- `Visible := calculado` em `0x4C4D7C` (TabKFactorShow) — SE SD.familia_LR[+0x80D] == 0

### `EditK1` — K

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 5570 `[OBS-COD]`. Pai: `TabKFactor`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelK` (associacao INFERIDO)): ita = "K"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=348 Top=64 Largura=72 Altura=32; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 1.0, maximo 2.5, passo (Precision) 0.1. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Valor K no primeiro ponto da curva de referencia. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: {"LR ([SD+0x80D]!=0)": -1, "demais ([SD+0x80D]==0)": 101}, indice 1 (campo +0x2EC (DataCode do TNumericEdit)).
  - familia LR: sem codigo nesta familia (-1)
  - Familia demais: vetor **K_FACTOR_PARAM** (SerialCode 198, TAebVector, indexado) `[OBS-COD]`.
    - tela -> ECU (setter 0x437808): `valor_bruto = trunc(((v / ((SD[0x2980] * 0.00025) * 0.004)) + 0.5))` com `v` = valor digitado.
    - ECU -> tela (getter 0x436085): `valor_na_tela = (((SD[0x2980] * 0.00025) * 0.004) * SD[0x2A70 + i*4])`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- K_FACTOR_PARAM (SC 198): bruto [586, 58, 1172, 85] (elemento 1 = 58); na tela: **nao calculado** `[OBS-FIO, tela DERIVADO]` (LOG linha 1109564).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditRifLambdaKeyPress` (0x4A6A00) `[OBS-COD]`
  - SetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A45` (handler generico)
  - GetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A64` (handler generico)
  - SetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A99` (handler generico)
  - GetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6AB1` (handler generico)
- `OnEnter` -> `EditGiriCambioEnter` (0x4A98D8) `[OBS-COD]`
- `OnExit` -> `EditGiriCambioExit` (0x4A9CC0) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Gravar valor (executado no Unicorn com o codigo original; `[OBS-COD]`):
  - maximo da faixa do DFM: tela = 2.5 -> quadro `14 C6 00 01 33 00 0E`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := falso` em `0x4C4BF6` (TabKFactorShow) — SE SD.familia_LR[+0x80D] != 0
- `Visible := calculado` em `0x4C4D90` (TabKFactorShow) — SE SD.familia_LR[+0x80D] == 0

### `EditTInj2` — T.inj.

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 5593 `[OBS-COD]`. Pai: `TabKFactor`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTInjK` (associacao INFERIDO)): ita = "T.inj."; eng = "-"; por = "-" `[OBS-COD]`.
- Unidade ao lado: **ms** `[OBS-COD]` (rotulo `LabelInjK`).
- Posicao no DFM: Left=427 Top=30 Largura=72 Altura=32; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 1.0, maximo 5.0, passo (Precision) 0.02. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Segundo ponto de tempo de injecao da curva do K de referencia. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: {"LR ([SD+0x80D]!=0)": -1, "demais ([SD+0x80D]==0)": 101}, indice 2 (campo +0x2EC (DataCode do TNumericEdit)).
  - familia LR: sem codigo nesta familia (-1)
  - Familia demais: vetor **K_FACTOR_PARAM** (SerialCode 198, TAebVector, indexado) `[OBS-COD]`.
    - tela -> ECU (setter 0x437808): `valor_bruto = trunc(((v / ((SD[0x2980] * 0.00025) * 0.004)) + 0.5))` com `v` = valor digitado.
    - ECU -> tela (getter 0x436085): `valor_na_tela = (((SD[0x2980] * 0.00025) * 0.004) * SD[0x2A70 + i*4])`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- K_FACTOR_PARAM (SC 198): bruto [586, 58, 1172, 85] (elemento 2 = 1172); na tela: **nao calculado** `[OBS-FIO, tela DERIVADO]` (LOG linha 1109564).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditRifLambdaKeyPress` (0x4A6A00) `[OBS-COD]`
  - SetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A45` (handler generico)
  - GetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A64` (handler generico)
  - SetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A99` (handler generico)
  - GetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6AB1` (handler generico)
- `OnEnter` -> `EditGiriCambioEnter` (0x4A98D8) `[OBS-COD]`
- `OnExit` -> `EditGiriCambioExit` (0x4A9CC0) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Gravar valor (executado no Unicorn com o codigo original; `[OBS-COD]`):
  - maximo da faixa do DFM: tela = 5.0 -> quadro `14 C6 00 02 A1 07 84`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := falso` em `0x4C4C03` (TabKFactorShow) — SE SD.familia_LR[+0x80D] != 0
- `Visible := calculado` em `0x4C4DA4` (TabKFactorShow) — SE SD.familia_LR[+0x80D] == 0

### `EditK2` — K

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 5616 `[OBS-COD]`. Pai: `TabKFactor`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelK` (associacao INFERIDO)): ita = "K"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=427 Top=64 Largura=72 Altura=32; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 1.0, maximo 2.5, passo (Precision) 0.1. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Valor K no segundo ponto da curva de referencia. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: {"LR ([SD+0x80D]!=0)": -1, "demais ([SD+0x80D]==0)": 101}, indice 3 (campo +0x2EC (DataCode do TNumericEdit)).
  - familia LR: sem codigo nesta familia (-1)
  - Familia demais: vetor **K_FACTOR_PARAM** (SerialCode 198, TAebVector, indexado) `[OBS-COD]`.
    - tela -> ECU (setter 0x437808): `valor_bruto = trunc(((v / ((SD[0x2980] * 0.00025) * 0.004)) + 0.5))` com `v` = valor digitado.
    - ECU -> tela (getter 0x436085): `valor_na_tela = (((SD[0x2980] * 0.00025) * 0.004) * SD[0x2A70 + i*4])`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- K_FACTOR_PARAM (SC 198): bruto [586, 58, 1172, 85] (elemento 3 = 85); na tela: **nao calculado** `[OBS-FIO, tela DERIVADO]` (LOG linha 1109564).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditRifLambdaKeyPress` (0x4A6A00) `[OBS-COD]`
  - SetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A45` (handler generico)
  - GetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A64` (handler generico)
  - SetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A99` (handler generico)
  - GetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6AB1` (handler generico)
- `OnEnter` -> `EditGiriCambioEnter` (0x4A98D8) `[OBS-COD]`
- `OnExit` -> `EditGiriCambioExit` (0x4A9CC0) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Gravar valor (executado no Unicorn com o codigo original; `[OBS-COD]`):
  - maximo da faixa do DFM: tela = 2.5 -> quadro `14 C6 00 03 33 00 10`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := falso` em `0x4C4C10` (TabKFactorShow) — SE SD.familia_LR[+0x80D] != 0
- `Visible := calculado` em `0x4C4DB8` (TabKFactorShow) — SE SD.familia_LR[+0x80D] == 0

## Rotulos com regra de mostrar/esconder ou texto dinamico

### `LabelP1` — "P1"

Texto: ita "P1" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := falso` em `0x4C4B6C` (TabKFactorShow) — SE SD.familia_LR[+0x80D] != 0
- `Visible := calculado por sub_489034` em `0x4C4CBE` (TabKFactorShow) — SE SD.familia_LR[+0x80D] == 0

### `LabelTInjK` — "T.inj."

Texto: ita "T.inj." / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := falso` em `0x4C4B86` (TabKFactorShow) — SE SD.familia_LR[+0x80D] != 0
- `Visible := calculado` em `0x4C4D40` (TabKFactorShow) — SE SD.familia_LR[+0x80D] == 0

### `LabelK` — "K"

Texto: ita "K" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := falso` em `0x4C4BA0` (TabKFactorShow) — SE SD.familia_LR[+0x80D] != 0
- `Visible := calculado` em `0x4C4D68` (TabKFactorShow) — SE SD.familia_LR[+0x80D] == 0

### `LabelP2` — "P2"

Texto: ita "P2" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := falso` em `0x4C4B79` (TabKFactorShow) — SE SD.familia_LR[+0x80D] != 0
- `Visible := calculado` em `0x4C4D2C` (TabKFactorShow) — SE SD.familia_LR[+0x80D] == 0

### `LabelInjK` — "ms"

Texto: ita "ms" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := falso` em `0x4C4B93` (TabKFactorShow) — SE SD.familia_LR[+0x80D] != 0
- `Visible := calculado` em `0x4C4D54` (TabKFactorShow) — SE SD.familia_LR[+0x80D] == 0

## Lacunas desta aba

- Edits sem codigo de dado na rotina 0x48A228 (o valor passa por metodo proprio do TStreamDati; formula nao decodificada aqui, `DESCONHECIDO`): `EditKMappaNeutro`.
- Combos cujo valor e gravado por metodo proprio (sem bit nem codigo generico): `ComboK`. Os vetores tocados aparecem na lista de acessos do handler; o significado de cada opcao no fio e `DESCONHECIDO` quando nao ha formula.
- Visibilidade/habilitacao: as regras listadas sao as que o codigo escreve; a combinacao final depende de dados da ECU (modelo, familia LR, MASK_FUNCTION) que o LOG so mostra em parte. O dono usa familia nao-LR (INFERIDO: o original leu SC 192, nunca 193).
- A associacao rotulo<->campo e por nome/geometria (`INFERIDO`), e a chave da dica (HINT...) segue o nome do componente (`INFERIDO`).

