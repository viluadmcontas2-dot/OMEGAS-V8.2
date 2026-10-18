# TabLambda — Sonda Lambda

Nome na tela: ita "Lambda" / eng "Lambda" / por "Sonda Lambda" `[OBS-COD]`. Aba 2 de 16 do grupo D3a. Rotina de abertura (OnShow): `TabLambdaShow` em `0x4A6770` `[OBS-COD]`. TabVisible de projeto: sim.

Legenda das marcas: `OBS-COD` = lido do DFM/codigo do ProgBase.exe (ou executado no emulador Unicorn); `OBS-FIO` = visto no Portmon da ECU do dono; `DERIVADO` = calculado a partir dessas observacoes; `INFERIDO` = deducao; `DESCONHECIDO` = sem prova.

**Resumo**: 45 controles no DFM (TCheckBox=1, TKeyComboBox=4, TLabel=27, TNumericEdit=12, TPanel=1); 17 interativos; 8 com codigo de dado e formula; 1 com caixa/bit; 9 com valor lido no LOG. Fonte do .exe sha256 `8a2d297c8c21...`.

## Como o ProgBase trata estes controles (valido para a aba inteira)

- **Campo numerico (`TNumericEdit`)** `[OBS-COD]`: ao entrar no campo, `EditGiriCambioEnter` (0x4A98D8) mostra no rotulo `LabelLimite` o texto `(minimo;maximo)` do proprio campo; ao sair, `EditGiriCambioExit` (0x4A9CC0) esconde esse rotulo. Nada e enviado a ECU nesses dois momentos. Ao apertar **Enter**, o `TNumericEdit` limita o numero a [minimo;maximo] (so se minimo!=0 ou maximo!=0; 0x96C170) e o handler generico `EditRifLambdaKeyPress` (0x4A6A00) chama `TStreamDati.Set*` com o *codigo de dado* do campo (campo `+0x2EC`, indice `+0x2F0`; amarrados em 0x48A228): o valor e convertido pela formula do caso, gravado no cache do programa e enviado como SetNumber (escalar) ou SetNumber+indice (vetor). Depois o handler le o valor de volta do cache (`Get*`) e redesenha o campo arredondado ao passo (`Precision`, 0x96BB88). Nao ha releitura da ECU.
- **Caixa de marcar (`TCheckBox`)** `[OBS-COD]`: apertar Enter simula um clique do mouse (`CheckTpsBlocKeyPress` 0x4A8928 envia WM_LBUTTONDOWN/UP); o clique chama o handler `Check...Click`, que liga/desliga **um bit** do vetor FLAG_CONF1/2/3 (o programa regrava o vetor inteiro, ver quadros abaixo) e atualiza o que depende da caixa.
- **Lista (`TKeyComboBox`)** `[OBS-COD]`: Enter no combo chama o `...KeyPress` correspondente, que grava o indice escolhido (por codigo de dado `+0x330` ou por bit de FLAG_CONF).
- **Resposta da ECU a toda gravacao** `[OBS-FIO]`: a porta devolve o eco do quadro e a ECU responde `53 00 53` (LOG linhas 48568-48600 e 31284-31300). Em seguida o programa limpa os buffers e retoma a telemetria (`48 01 49`).
- **Conexao**: os valores 'atuais' vem de leituras `09`/`0A`/`29` feitas ao conectar (LOG de 12/08/2026); a tela do programa mostra o cache dessas leituras.

## Mapa de todos os controles (ordem do DFM)

| # | Componente | Tipo | Rotulo (por / eng / ita) | Unidade | Vetor (SC) | Bit | Visivel no DFM |
|---|---|---|---|---|---|---|---|
| 1 | `LabelRitardoLambda` | rotulo | Reading delay / Reading delay / Ritardo lettura | - | - | - | False |
| 2 | `LabelS3` | rotulo | - / - / s | - | - | - | False |
| 3 | `LabelTempoSondaFredda` | rotulo | Time to retain cool probe / Time to retain cool probe / Tempo per ritenere sonda fredda | - | - | - | False |
| 4 | `LabelS4` | rotulo | - / - / s | - | - | - | False |
| 5 | `LabelRifSupFredda` | rotulo | High. ref. cooling / High. ref. cooling / Rif. sup. raffreddarsi | - | - | - | False |
| 6 | `LabelV2` | rotulo | - / - / V | - | - | - | False |
| 7 | `LabelRifInfFredda` | rotulo | Low. ref. cooling / Low. ref. cooling / Rif. inf. raffreddarsi | - | - | - | False |
| 8 | `LabelV3` | rotulo | - / - / V | - | - | - | False |
| 9 | `LabelRifSupCalda` | rotulo | High. ref. warm-up / High. ref. warm-up / Rif. sup. scaldarsi | - | - | - | False |
| 10 | `LabelV4` | rotulo | - / - / V | - | - | - | False |
| 11 | `LabelRifInfCalda` | rotulo | Low. ref. warm-up / Low. ref. warm-up / Rif. inf. scaldarsi | - | - | - | False |
| 12 | `LabelV5` | rotulo | - / - / V | - | - | - | False |
| 13 | `LabelTipoLambda` | rotulo | Tipo de sonda Lambda / Type of Lambda probe / Tipo di sonda Lambda | - | - | - | sim |
| 14 | `LabelNumeroSonde` | rotulo | Número de sondas Lambda / Number of Lambda probes / Numero sonde | - | - | - | False |
| 15 | `LabelV7` | rotulo | - / - / V | - | - | - | False |
| 16 | `LabelRifLambda` | rotulo | Reference / Reference / Riferimento | - | - | - | False |
| 17 | `LabelCorrBancata2` | rotulo | - / - / Correttore 2° bancata | - | - | - | False |
| 18 | `LabelTipoSonda1` | rotulo | Sonda Lambda 1 (fio roxo) / Oxygen Sensor 1 (purple wire) / Sonda Lambda 1 (filo viola) | - | - | - | sim |
| 19 | `LabelTipoSonda2` | rotulo | Sonda Lambda 2 (fio roxo/preto) / Oxygen Sensor 2 (purple/black wire) / Sonda Lambda 2 (filo viola/nero) | - | - | - | sim |
| 20 | `EditRifLambda` | edit numerico | Reference / Reference / Riferimento | V | RIF_LAMBDA (5) | - | False |
| 21 | `EditRitardoLbd` | edit numerico | Reading delay / Reading delay / Ritardo lettura | s | RITARDO_SONDA (6) | - | False |
| 22 | `EditSondaFredda` | edit numerico | Time to retain cool probe / Time to retain cool probe / Tempo per ritenere sonda fredda | s | TEMPO_LBD_FREDDA (7) | - | False |
| 23 | `EditRifSupFredda` | edit numerico | High. ref. cooling / High. ref. cooling / Rif. sup. raffreddarsi | V | RIF_SUP_LAMBDA_FREDDA (8) | - | False |
| 24 | `EditRifInfFredda` | edit numerico | Low. ref. cooling / Low. ref. cooling / Rif. inf. raffreddarsi | V | RIF_INF_LAMBDA_FREDDA (9) | - | False |
| 25 | `EditRifSupCalda` | edit numerico | High. ref. warm-up / High. ref. warm-up / Rif. sup. scaldarsi | V | RIF_SUP_LAMBDA_CALDA (10) | - | False |
| 26 | `EditRifInfCalda` | edit numerico | Low. ref. warm-up / Low. ref. warm-up / Rif. inf. scaldarsi | V | RIF_INF_LAMBDA_CALDA (11) | - | False |
| 27 | `ComboTipoLambda` | combo | Tipo de sonda Lambda / Type of Lambda probe / Tipo di sonda Lambda | - | TIPO_LAMBDA (4) | - | sim |
| 28 | `ComboNumeroSonde` | combo | Número de sondas Lambda / Number of Lambda probes / Numero sonde | - | - | - | False |
| 29 | `EditCorrBancata2` | edit numerico | - / - / Correttore 2° bancata | - | - | - | False |
| 30 | `ComboSonda1` | combo | Sonda Lambda 1 (fio roxo) / Oxygen Sensor 1 (purple wire) / Sonda Lambda 1 (filo viola) | - | - | - | sim |
| 31 | `ComboSonda2` | combo | Sonda Lambda 2 (fio roxo/preto) / Oxygen Sensor 2 (purple/black wire) / Sonda Lambda 2 (filo viola/nero) | - | - | - | sim |
| 32 | `PanelFlashLube` | painel | - / - / - | - | - | - | False |
| 33 | `LabelFlashLube0` | rotulo | - / - / Soglia livello di riserva | - | - | - | False |
| 34 | `LabFlashLube0` | rotulo | - / - / V | - | - | - | False |
| 35 | `LabelFlashLube1` | rotulo | - / - / Tempo di filtro | - | - | - | False |
| 36 | `LabFlashLube1` | rotulo | - / - / s | - | - | - | False |
| 37 | `LabelFlashLube2` | rotulo | - / - / Tempo basso livello per retropassaggio | - | - | - | False |
| 38 | `LabFlashLube2` | rotulo | - / - / h | - | - | - | False |
| 39 | `LabelFlashLube3` | rotulo | - / - / Tempo alto livello per reset diagnosi  | - | - | - | False |
| 40 | `LabFlashLube3` | rotulo | - / - / h | - | - | - | False |
| 41 | `CheckFlashLube` | caixa de marcar | - / - / Abilita diagnosi livello lubrificante  | - | - | FLAG_CONF2[1]&0x4000 | sim |
| 42 | `EditFlashLube0` | edit numerico | - / - / Soglia livello di riserva | V | - | - | False |
| 43 | `EditFlashLube1` | edit numerico | - / - / Tempo di filtro | s | - | - | False |
| 44 | `EditFlashLube2` | edit numerico | - / - / Tempo basso livello per retropassaggio | h | - | - | False |
| 45 | `EditFlashLube3` | edit numerico | - / - / Tempo alto livello per reset diagnosi  | h | - | - | False |

## Controles interativos, um a um

### `EditRifLambda` — Reference

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 2398 `[OBS-COD]`. Pai: `TabLambda`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelRifLambda` (associacao INFERIDO)): ita = "Riferimento"; eng = "Reference"; por = "Reference" `[OBS-COD]`.
- Unidade ao lado: **V** `[OBS-COD]` (rotulo `LabelV7`).
- Posicao no DFM: Left=655 Top=402 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 5.0, passo (Precision) 0.02. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Parametro da leitura da sonda lambda (referencia/atraso/tempo de sonda fria); o campo fica escondido nesta versao do programa [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 2 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **RIF_LAMBDA** (SerialCode 5, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x43668B): `valor_bruto = trunc((((v * 255) * 0.2) + 0.5))` com `v` = valor digitado.
    - ECU -> tela (getter 0x434EFE): `valor_na_tela = ((SD[0x29A4] * 5) * 0.003921568627)`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- RIF_LAMBDA (SC 5): bruto 28; na tela: **0.54902** `[OBS-FIO, tela DERIVADO]` (LOG linha 1105096).

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
  - valor atual da ECU do dono: tela = 0.54902 -> quadro `12 05 00 1C 33`.
  - maximo da faixa do DFM: tela = 5.0 -> quadro `12 05 00 FF 16`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditRitardoLbd` — Reading delay

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 2417 `[OBS-COD]`. Pai: `TabLambda`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelRitardoLambda` (associacao INFERIDO)): ita = "Ritardo lettura"; eng = "Reading delay"; por = "Reading delay" `[OBS-COD]`.
- Unidade ao lado: **s** `[OBS-COD]` (rotulo `LabelS3`).
- Posicao no DFM: Left=296 Top=402 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 255.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Parametro da leitura da sonda lambda (referencia/atraso/tempo de sonda fria); o campo fica escondido nesta versao do programa [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 3 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **RITARDO_SONDA** (SerialCode 6, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x4366BA): `valor_bruto = trunc(v)` com `v` = valor digitado.
    - ECU -> tela (getter 0x434F22): `valor_na_tela = SD[0x29A8]`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- RITARDO_SONDA (SC 6): bruto 0; na tela: **0.0** `[OBS-FIO, tela DERIVADO]` (LOG linha 1105118).

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
  - valor atual da ECU do dono: tela = 0.0 -> quadro `12 06 00 00 18`.
  - maximo da faixa do DFM: tela = 255.0 -> quadro `12 06 00 FF 17`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditSondaFredda` — Time to retain cool probe

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 2436 `[OBS-COD]`. Pai: `TabLambda`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTempoSondaFredda` (associacao INFERIDO)): ita = "Tempo per ritenere sonda fredda"; eng = "Time to retain cool probe"; por = "Time to retain cool probe" `[OBS-COD]`.
- Unidade ao lado: **s** `[OBS-COD]` (rotulo `LabelS4`).
- Posicao no DFM: Left=548 Top=519 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 255.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Parametro da leitura da sonda lambda (referencia/atraso/tempo de sonda fria); o campo fica escondido nesta versao do programa [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 4 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **TEMPO_LBD_FREDDA** (SerialCode 7, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x4366DB): `valor_bruto = trunc(v)` com `v` = valor digitado.
    - ECU -> tela (getter 0x434F3E): `valor_na_tela = SD[0x29AC]`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- TEMPO_LBD_FREDDA (SC 7): bruto 5; na tela: **5.0** `[OBS-FIO, tela DERIVADO]` (LOG linha 1105140).

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
  - valor atual da ECU do dono: tela = 5.0 -> quadro `12 07 00 05 1E`.
  - maximo da faixa do DFM: tela = 255.0 -> quadro `12 07 00 FF 18`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditRifSupFredda` — High. ref. cooling

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 2455 `[OBS-COD]`. Pai: `TabLambda`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelRifSupFredda` (associacao INFERIDO)): ita = "Rif. sup. raffreddarsi"; eng = "High. ref. cooling"; por = "High. ref. cooling" `[OBS-COD]`.
- Unidade ao lado: **V** `[OBS-COD]` (rotulo `LabelV2`).
- Posicao no DFM: Left=296 Top=479 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 5.0, passo (Precision) 0.02. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Parametro da leitura da sonda lambda (referencia/atraso/tempo de sonda fria); o campo fica escondido nesta versao do programa [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 5 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **RIF_SUP_LAMBDA_FREDDA** (SerialCode 8, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x4366FC): `valor_bruto = trunc((((v * 255) * 0.2) + 0.5))` com `v` = valor digitado.
    - ECU -> tela (getter 0x434F5A): `valor_na_tela = ((SD[0x29B0] * 5) * 0.003921568627)`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- RIF_SUP_LAMBDA_FREDDA (SC 8): bruto 31; na tela: **0.607843** `[OBS-FIO, tela DERIVADO]` (LOG linha 1105162).

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
  - valor atual da ECU do dono: tela = 0.607843 -> quadro `12 08 00 1F 39`.
  - maximo da faixa do DFM: tela = 5.0 -> quadro `12 08 00 FF 19`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditRifInfFredda` — Low. ref. cooling

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 2474 `[OBS-COD]`. Pai: `TabLambda`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelRifInfFredda` (associacao INFERIDO)): ita = "Rif. inf. raffreddarsi"; eng = "Low. ref. cooling"; por = "Low. ref. cooling" `[OBS-COD]`.
- Unidade ao lado: **V** `[OBS-COD]` (rotulo `LabelV3`).
- Posicao no DFM: Left=655 Top=478 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 5.0, passo (Precision) 0.02. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Parametro da leitura da sonda lambda (referencia/atraso/tempo de sonda fria); o campo fica escondido nesta versao do programa [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 6 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **RIF_INF_LAMBDA_FREDDA** (SerialCode 9, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x43672B): `valor_bruto = trunc((((v * 255) * 0.2) + 0.5))` com `v` = valor digitado.
    - ECU -> tela (getter 0x434F7E): `valor_na_tela = ((SD[0x29B4] * 5) * 0.003921568627)`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- RIF_INF_LAMBDA_FREDDA (SC 9): bruto 15; na tela: **0.294118** `[OBS-FIO, tela DERIVADO]` (LOG linha 1105184).

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
  - valor atual da ECU do dono: tela = 0.294118 -> quadro `12 09 00 0F 2A`.
  - maximo da faixa do DFM: tela = 5.0 -> quadro `12 09 00 FF 1A`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditRifSupCalda` — High. ref. warm-up

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 2493 `[OBS-COD]`. Pai: `TabLambda`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelRifSupCalda` (associacao INFERIDO)): ita = "Rif. sup. scaldarsi"; eng = "High. ref. warm-up"; por = "High. ref. warm-up" `[OBS-COD]`.
- Unidade ao lado: **V** `[OBS-COD]` (rotulo `LabelV4`).
- Posicao no DFM: Left=296 Top=440 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 5.0, passo (Precision) 0.02. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Parametro da leitura da sonda lambda (referencia/atraso/tempo de sonda fria); o campo fica escondido nesta versao do programa [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 7 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **RIF_SUP_LAMBDA_CALDA** (SerialCode 10, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x43675A): `valor_bruto = trunc((((v * 255) * 0.2) + 0.5))` com `v` = valor digitado.
    - ECU -> tela (getter 0x434FA2): `valor_na_tela = ((SD[0x29B8] * 5) * 0.003921568627)`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- RIF_SUP_LAMBDA_CALDA (SC 10): bruto 31; na tela: **0.607843** `[OBS-FIO, tela DERIVADO]` (LOG linha 1105206).

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
  - valor atual da ECU do dono: tela = 0.607843 -> quadro `12 0A 00 1F 3B`.
  - maximo da faixa do DFM: tela = 5.0 -> quadro `12 0A 00 FF 1B`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditRifInfCalda` — Low. ref. warm-up

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 2512 `[OBS-COD]`. Pai: `TabLambda`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelRifInfCalda` (associacao INFERIDO)): ita = "Rif. inf. scaldarsi"; eng = "Low. ref. warm-up"; por = "Low. ref. warm-up" `[OBS-COD]`.
- Unidade ao lado: **V** `[OBS-COD]` (rotulo `LabelV5`).
- Posicao no DFM: Left=655 Top=440 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 5.0, passo (Precision) 0.02. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Parametro da leitura da sonda lambda (referencia/atraso/tempo de sonda fria); o campo fica escondido nesta versao do programa [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 8 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **RIF_INF_LAMBDA_CALDA** (SerialCode 11, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x436789): `valor_bruto = trunc((((v * 255) * 0.2) + 0.5))` com `v` = valor digitado.
    - ECU -> tela (getter 0x434FC6): `valor_na_tela = ((SD[0x29BC] * 5) * 0.003921568627)`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- RIF_INF_LAMBDA_CALDA (SC 11): bruto 15; na tela: **0.294118** `[OBS-FIO, tela DERIVADO]` (LOG linha 1105228).

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
  - valor atual da ECU do dono: tela = 0.294118 -> quadro `12 0B 00 0F 2C`.
  - maximo da faixa do DFM: tela = 5.0 -> quadro `12 0B 00 FF 1C`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `ComboTipoLambda` — Tipo de sonda Lambda

Tipo: **combo** (`TKeyComboBox`), DFM linha 2531 `[OBS-COD]`. Pai: `TabLambda`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTipoLambda` (associacao INFERIDO)): ita = "Tipo di sonda Lambda"; eng = "Type of Lambda probe"; por = "Tipo de sonda Lambda" `[OBS-COD]`.
- Posicao no DFM: Left=548 Top=89 Largura=227 Altura=37; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Opcoes da lista `[OBS-COD]`: 0 = "0 - 1 Volt"; 1 = "0 - 5 Volt"; 2 = "5 - 0 Volt"; 3 = "0.8 - 1.6 Volt"; 4 = "UEGO".
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTTIPOLAMBDA`: "Type of Lambda probe".

**O que isso faz (linguagem simples)**

- Tipo da sonda lambda do carro (0-1 V, 0-5 V, 5-0 V ou 0,8-1,6 V), para a ECU poder conferir o sinal. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.16).
- Manual SW 2.16.4 C, p.16 (linha 764 de sw.txt): "C The type of lambda probe fitted to a vehicle can be set so as to have (if connected) signal verification. The following values can be set: • 0 - 1 Volt;" `[OBS-COD]`.

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 1 (campo +0x330 (codigo do TKeyComboBox)).
  - Familia unico: vetor **TIPO_LAMBDA** (SerialCode 4, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x43666B): `valor_bruto = TAB[0xA5CFF4 + trunc(v)*4]` com `v` = valor digitado.
    - ECU -> tela (getter 0x434EDC): `valor_na_tela = f_433FCC(SD, 10866676, 6, SD[0x29A0])`.
    - Usa funcoes auxiliares do .exe: 0x433FCC (tabelas de conversao; ver docs/progbase/abas/_funcoes_auxiliares.json) `[OBS-COD]`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- TIPO_LAMBDA (SC 4): bruto 2; na tela: **0.0** `[OBS-FIO, tela DERIVADO]` (LOG linha 1105074).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `ComboTipoLambdaKeyPress` (0x4A6674) `[OBS-COD]`
  - SetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A66C9` (handler generico)
  - chama o metodo do TStreamDati `0x43405C` em `0x4A6765` (le: cache de TIPO_LAMBDA; escreve: -)

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Gravar valor (executado no Unicorn com o codigo original; `[OBS-COD]`):
  - valor atual da ECU do dono: tela = 0.0 -> quadro `12 04 00 02 18`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Cada opcao da lista chama o metodo `0x43405C` `[INFERIDO]` (convencao assumida):
  - opcao 0 "0 - 1 Volt" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x434032.
  - opcao 1 "0 - 5 Volt" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x434032.
  - opcao 2 "5 - 0 Volt" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x434032.
  - opcao 3 "0.8 - 1.6 Volt" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x434032.
  - opcao 4 "UEGO" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x434032.
  - Estrutura: igual a acima. Fonte: metodo 0x43405C executado no Unicorn com edx = indice da opcao (convencao assumida; o handler ComboTipoLambdaKeyPress o chama em 0x4A6765)
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetEsc(codigo 1)` em `0x4A6797` (TabLambdaShow) — incondicional

### `ComboNumeroSonde` — Número de sondas Lambda

Tipo: **combo** (`TKeyComboBox`), DFM linha 2553 `[OBS-COD]`. Pai: `TabLambda`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelNumeroSonde` (associacao INFERIDO)): ita = "Numero sonde"; eng = "Number of Lambda probes"; por = "Número de sondas Lambda" `[OBS-COD]`.
- Posicao no DFM: Left=548 Top=13 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Opcoes da lista `[OBS-COD]`: 0 = "1"; 1 = "2".
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTNUMEROSONDE`: "Number of Lambda probes".

**O que isso faz (linguagem simples)**

- Quantas sondas lambda (antes do catalisador) o carro tem; com duas, os tempos e sinais sao duplicados. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.15).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `ComboNumeroSondeKeyPress` (0x4A9664) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x433938` em `0x4A9692` (le: FLAG_CONF1; escreve: -)

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Cada opcao da lista chama o metodo `0x433938` `[INFERIDO]` (convencao assumida):
  - opcao 0 "1" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x433942.
  - opcao 1 "2" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x433942.
  - Estrutura: igual a acima. Fonte: metodo 0x433938 executado no Unicorn com edx = indice da opcao (convencao assumida; o handler ComboNumeroSondeKeyPress o chama em 0x4A9692)
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por sub_433918` em `0x4A67B3` (TabLambdaShow) — incondicional

### `EditCorrBancata2` — Correttore 2° bancata

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 2570 `[OBS-COD]`. Pai: `TabLambda`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelCorrBancata2` (associacao INFERIDO)): ita = "Correttore 2° bancata"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=548 Top=50 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo -20.0, maximo 20.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTCORRBANCATA2`: "2° bank corrector".

**O que isso faz (linguagem simples)**

- Correcao (-20 a +20) da mistura do segundo banco quando os dois bancos estao levemente desequilibrados; nao tem efeito em motor de 4 cilindros. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.15).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditCorrBancata2KeyPress` (0x4C2CCC) `[OBS-COD]`
  - SetEsc codigo 82 -> vetor CORRETTORE_BANCATA2_LR (SC 187) em `0x4C2D0C`
  - GetEsc codigo 82 -> vetor CORRETTORE_BANCATA2_LR (SC 187) em `0x4C2D1B`
  - SetIdx codigo 74 -> vetor CORRETTORE_BANCATA2 (SC 158) em `0x4C2D5A`
  - GetIdx codigo 74 -> vetor CORRETTORE_BANCATA2 (SC 158) em `0x4C2D71`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado por sub_433918` em `0x4A987E` (ComboNumeroSondeKeyPress, ComboNumeroSondeKeyPress via sub_4A96A4) — SE eax == 0xd E sub_754CCC == 0
- `Visible := calculado por sub_433918` em `0x4A987E` (ComboSonda1KeyPress, ComboSonda1KeyPress via sub_4A96A4) — SE eax == 0xd
- `Visible := calculado por sub_433918` em `0x4A987E` (FormShow, FormShow via sub_4A96A4; metodo de OUTRA aba/tela) — incondicional

### `ComboSonda1` — Sonda Lambda 1 (fio roxo)

Tipo: **combo** (`TKeyComboBox`), DFM linha 2589 `[OBS-COD]`. Pai: `TabLambda`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTipoSonda1` (associacao INFERIDO)): ita = "Sonda Lambda 1 (filo viola)"; eng = "Oxygen Sensor 1 (purple wire)"; por = "Sonda Lambda 1 (fio roxo)" `[OBS-COD]`.
- Posicao no DFM: Left=548 Top=129 Largura=227 Altura=37; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Opcoes da lista `[OBS-COD]`: 0 = "Non connessa"; 1 = "Anteriore"; 2 = "Posteriore".
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTTIPOSONDA1`: "Oxygen Sensor 1 (purple wire)".

**O que isso faz (linguagem simples)**

- Lembrete de onde esta ligado o fio violeta da sonda 1: nao conectado, antes (Pre) ou depois (Post) do catalisador. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.16).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `ComboSonda1KeyPress` (0x4C2D94) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x433A4C` em `0x4C2DC8` (le: TIPI_SONDA_LAMBDA, TIPI_SONDA_LAMBDA_LR, cache de TIPI_SONDA_LAMBDA_LR; escreve: cache de TIPI_SONDA_LAMBDA_LR)
  - chama o metodo do TStreamDati `0x433A00` em `0x4C2DDA` (le: cache de TIPI_SONDA_LAMBDA_LR; escreve: -)
  - chama o metodo do TStreamDati `0x433A4C` em `0x4C2E0C` (le: TIPI_SONDA_LAMBDA, TIPI_SONDA_LAMBDA_LR, cache de TIPI_SONDA_LAMBDA_LR; escreve: cache de TIPI_SONDA_LAMBDA_LR)
  - chama o metodo do TStreamDati `0x433A00` em `0x4C2E1E` (le: cache de TIPI_SONDA_LAMBDA_LR; escreve: -)

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por sub_433A00` em `0x4A6965` (TabLambdaShow) — incondicional

### `ComboSonda2` — Sonda Lambda 2 (fio roxo/preto)

Tipo: **combo** (`TKeyComboBox`), DFM linha 2610 `[OBS-COD]`. Pai: `TabLambda`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTipoSonda2` (associacao INFERIDO)): ita = "Sonda Lambda 2 (filo viola/nero)"; eng = "Oxygen Sensor 2 (purple/black wire)"; por = "Sonda Lambda 2 (fio roxo/preto)" `[OBS-COD]`.
- Posicao no DFM: Left=548 Top=168 Largura=227 Altura=37; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Opcoes da lista `[OBS-COD]`: 0 = "Non connessa"; 1 = "Anteriore"; 2 = "Posteriore".
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTTIPOSONDA2`: "Oxygen Sensor 2 (purple/black wire)".

**O que isso faz (linguagem simples)**

- Lembrete de onde esta ligado o fio violeta/preto da sonda 2: nao conectado, antes ou depois do catalisador. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.16).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `ComboSonda1KeyPress` (0x4C2D94) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x433A4C` em `0x4C2DC8` (le: TIPI_SONDA_LAMBDA, TIPI_SONDA_LAMBDA_LR, cache de TIPI_SONDA_LAMBDA_LR; escreve: cache de TIPI_SONDA_LAMBDA_LR)
  - chama o metodo do TStreamDati `0x433A00` em `0x4C2DDA` (le: cache de TIPI_SONDA_LAMBDA_LR; escreve: -)
  - chama o metodo do TStreamDati `0x433A4C` em `0x4C2E0C` (le: TIPI_SONDA_LAMBDA, TIPI_SONDA_LAMBDA_LR, cache de TIPI_SONDA_LAMBDA_LR; escreve: cache de TIPI_SONDA_LAMBDA_LR)
  - chama o metodo do TStreamDati `0x433A00` em `0x4C2E1E` (le: cache de TIPI_SONDA_LAMBDA_LR; escreve: -)

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por sub_433A00` em `0x4A6983` (TabLambdaShow) — incondicional

### `CheckFlashLube` — Abilita diagnosi livello lubrificante 

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 2714 `[OBS-COD]`. Pai: `PanelFlashLube`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Abilita diagnosi livello lubrificante "; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=38 Top=0 Largura=731 Altura=31; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Liga a diagnostico do nivel de oleo/lubrificante (Flash Lube) no sistema. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF2** (SerialCode 192), palavra 1, mascara 0x4000, caso do setter 20, papel "caixa (Checked)", condicao "sempre"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF2 palavra 1 = 0x0000 -> bit 0x4000: **desligado** `[OBS-FIO]` (LOG linha 1102816).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckFlashLubeClick` (0x4C88AC) `[OBS-COD]`
  - SetFlag2 caso 20 -> FLAG_CONF2 palavra 1 mascara 0x4000 em `0x4C88D8`
  - GetEsc codigo 1 -> vetor TIPO_LAMBDA (SC 4) em `0x4C88FA`
  - SetEsc codigo 1 -> vetor TIPO_LAMBDA (SC 4) em `0x4C8925`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF2 palavra 1 mascara 0x4000): 0001 0000 -> 0001 4000, quadro `35 C0 00 01 00 00 40 36`.
  - desligar bit (FLAG_CONF2 palavra 1 mascara 0x4000): 0001 0000 -> 0001 0000, quadro `35 C0 00 01 00 00 00 F6`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetFlag2(caso 20)` em `0x4A69A8` (TabLambdaShow) — incondicional

### `EditFlashLube0` — Soglia livello di riserva

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 2729 `[OBS-COD]`. Pai: `PanelFlashLube`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelFlashLube0` (associacao INFERIDO)): ita = "Soglia livello di riserva"; eng = "-"; por = "-" `[OBS-COD]`.
- Unidade ao lado: **V** `[OBS-COD]` (rotulo `LabFlashLube0`).
- Posicao no DFM: Left=548 Top=38 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 5.0, passo (Precision) 0.01. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Tensao-limite do nivel de reserva do lubrificante. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditFlashLubeKeyPress` (0x4C8948) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x432D20` em `0x4C89AB` (le: FLASH_LUBE_PARAMETER; escreve: -)
  - chama o metodo do TStreamDati `0x432D10` em `0x4C89BA` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x432D20` em `0x4C8A09` (le: FLASH_LUBE_PARAMETER; escreve: -)
  - chama o metodo do TStreamDati `0x432D10` em `0x4C8A1B` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x432D20` em `0x4C8A58` (le: FLASH_LUBE_PARAMETER; escreve: -)
  - chama o metodo do TStreamDati `0x432D10` em `0x4C8A6A` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x432D20` em `0x4C8AAD` (le: FLASH_LUBE_PARAMETER; escreve: -)
  - chama o metodo do TStreamDati `0x432D10` em `0x4C8ABF` (le: -; escreve: -)
- `OnEnter` -> `EditGiriCambioEnter` (0x4A98D8) `[OBS-COD]`
- `OnExit` -> `EditGiriCambioExit` (0x4A9CC0) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditFlashLube1` — Tempo di filtro

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 2746 `[OBS-COD]`. Pai: `PanelFlashLube`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelFlashLube1` (associacao INFERIDO)): ita = "Tempo di filtro"; eng = "-"; por = "-" `[OBS-COD]`.
- Unidade ao lado: **s** `[OBS-COD]` (rotulo `LabFlashLube1`).
- Posicao no DFM: Left=548 Top=76 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 300.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Tempo de filtro (segundos) antes de aceitar a leitura do nivel de lubrificante. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditFlashLubeKeyPress` (0x4C8948) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x432D20` em `0x4C89AB` (le: FLASH_LUBE_PARAMETER; escreve: -)
  - chama o metodo do TStreamDati `0x432D10` em `0x4C89BA` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x432D20` em `0x4C8A09` (le: FLASH_LUBE_PARAMETER; escreve: -)
  - chama o metodo do TStreamDati `0x432D10` em `0x4C8A1B` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x432D20` em `0x4C8A58` (le: FLASH_LUBE_PARAMETER; escreve: -)
  - chama o metodo do TStreamDati `0x432D10` em `0x4C8A6A` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x432D20` em `0x4C8AAD` (le: FLASH_LUBE_PARAMETER; escreve: -)
  - chama o metodo do TStreamDati `0x432D10` em `0x4C8ABF` (le: -; escreve: -)
- `OnEnter` -> `EditGiriCambioEnter` (0x4A98D8) `[OBS-COD]`
- `OnExit` -> `EditGiriCambioExit` (0x4A9CC0) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditFlashLube2` — Tempo basso livello per retropassaggio

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 2764 `[OBS-COD]`. Pai: `PanelFlashLube`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelFlashLube2` (associacao INFERIDO)): ita = "Tempo basso livello per retropassaggio"; eng = "-"; por = "-" `[OBS-COD]`.
- Unidade ao lado: **h** `[OBS-COD]` (rotulo `LabFlashLube2`).
- Posicao no DFM: Left=548 Top=113 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 1.0, maximo 15.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Horas de nivel baixo antes de voltar para gasolina (retropassagem). [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditFlashLubeKeyPress` (0x4C8948) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x432D20` em `0x4C89AB` (le: FLASH_LUBE_PARAMETER; escreve: -)
  - chama o metodo do TStreamDati `0x432D10` em `0x4C89BA` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x432D20` em `0x4C8A09` (le: FLASH_LUBE_PARAMETER; escreve: -)
  - chama o metodo do TStreamDati `0x432D10` em `0x4C8A1B` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x432D20` em `0x4C8A58` (le: FLASH_LUBE_PARAMETER; escreve: -)
  - chama o metodo do TStreamDati `0x432D10` em `0x4C8A6A` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x432D20` em `0x4C8AAD` (le: FLASH_LUBE_PARAMETER; escreve: -)
  - chama o metodo do TStreamDati `0x432D10` em `0x4C8ABF` (le: -; escreve: -)
- `OnEnter` -> `EditGiriCambioEnter` (0x4A98D8) `[OBS-COD]`
- `OnExit` -> `EditGiriCambioExit` (0x4A9CC0) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditFlashLube3` — Tempo alto livello per reset diagnosi 

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 2783 `[OBS-COD]`. Pai: `PanelFlashLube`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelFlashLube3` (associacao INFERIDO)): ita = "Tempo alto livello per reset diagnosi "; eng = "-"; por = "-" `[OBS-COD]`.
- Unidade ao lado: **h** `[OBS-COD]` (rotulo `LabFlashLube3`).
- Posicao no DFM: Left=548 Top=151 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 1.0, maximo 15.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Horas de nivel alto para zerar o diagnostico. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditFlashLubeKeyPress` (0x4C8948) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x432D20` em `0x4C89AB` (le: FLASH_LUBE_PARAMETER; escreve: -)
  - chama o metodo do TStreamDati `0x432D10` em `0x4C89BA` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x432D20` em `0x4C8A09` (le: FLASH_LUBE_PARAMETER; escreve: -)
  - chama o metodo do TStreamDati `0x432D10` em `0x4C8A1B` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x432D20` em `0x4C8A58` (le: FLASH_LUBE_PARAMETER; escreve: -)
  - chama o metodo do TStreamDati `0x432D10` em `0x4C8A6A` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x432D20` em `0x4C8AAD` (le: FLASH_LUBE_PARAMETER; escreve: -)
  - chama o metodo do TStreamDati `0x432D10` em `0x4C8ABF` (le: -; escreve: -)
- `OnEnter` -> `EditGiriCambioEnter` (0x4A98D8) `[OBS-COD]`
- `OnExit` -> `EditGiriCambioExit` (0x4A9CC0) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

## Rotulos com regra de mostrar/esconder ou texto dinamico

### `LabelCorrBancata2` — "Correttore 2° bancata"

Texto: ita "Correttore 2° bancata" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado por sub_433918` em `0x4A986A` (ComboNumeroSondeKeyPress, ComboNumeroSondeKeyPress via sub_4A96A4) — SE eax == 0xd E sub_754CCC == 0
- `Visible := calculado por sub_433918` em `0x4A986A` (ComboSonda1KeyPress, ComboSonda1KeyPress via sub_4A96A4) — SE eax == 0xd
- `Visible := calculado por sub_433918` em `0x4A986A` (FormShow, FormShow via sub_4A96A4; metodo de OUTRA aba/tela) — incondicional

### `LabelTipoSonda1` — "Sonda Lambda 1 (fio roxo)"

Texto: ita "Sonda Lambda 1 (filo viola)" / eng "Oxygen Sensor 1 (purple wire)" / por "Sonda Lambda 1 (fio roxo)" `[OBS-COD]`. Estado de projeto: visivel=sim.
- `Visible := calculado por GetEsc(codigo 4)` em `0x4A68AB` (TabLambdaShow) — incondicional

### `LabelTipoSonda2` — "Sonda Lambda 2 (fio roxo/preto)"

Texto: ita "Sonda Lambda 2 (filo viola/nero)" / eng "Oxygen Sensor 2 (purple/black wire)" / por "Sonda Lambda 2 (fio roxo/preto)" `[OBS-COD]`. Estado de projeto: visivel=sim.
- `Visible := verdadeiro` em `0x4A6934` (TabLambdaShow) — incondicional

## Lacunas desta aba

- Edits sem codigo de dado na rotina 0x48A228 (o valor passa por metodo proprio do TStreamDati; formula nao decodificada aqui, `DESCONHECIDO`): `EditCorrBancata2`, `EditFlashLube0`, `EditFlashLube1`, `EditFlashLube2`, `EditFlashLube3`.
- Combos cujo valor e gravado por metodo proprio (sem bit nem codigo generico): `ComboNumeroSonde`, `ComboSonda1`, `ComboSonda2`. Os vetores tocados aparecem na lista de acessos do handler; o significado de cada opcao no fio e `DESCONHECIDO` quando nao ha formula.
- Visibilidade/habilitacao: as regras listadas sao as que o codigo escreve; a combinacao final depende de dados da ECU (modelo, familia LR, MASK_FUNCTION) que o LOG so mostra em parte. O dono usa familia nao-LR (INFERIDO: o original leu SC 192, nunca 193).
- A associacao rotulo<->campo e por nome/geometria (`INFERIDO`), e a chave da dica (HINT...) segue o nome do componente (`INFERIDO`).

