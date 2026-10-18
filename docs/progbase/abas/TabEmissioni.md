# TabEmissioni — Emissões

Nome na tela: ita "Emissioni" / eng "Emissions" / por "Emissões" `[OBS-COD]`. Aba 3 de 16 do grupo D3a. Rotina de abertura (OnShow): `TabEmissioniShow` em `0x4A8964` `[OBS-COD]`. TabVisible de projeto: False.

Legenda das marcas: `OBS-COD` = lido do DFM/codigo do ProgBase.exe (ou executado no emulador Unicorn); `OBS-FIO` = visto no Portmon da ECU do dono; `DERIVADO` = calculado a partir dessas observacoes; `INFERIDO` = deducao; `DESCONHECIDO` = sem prova.

**Resumo**: 34 controles no DFM (TCheckBox=1, TDrawGrid=3, TGroupBox=2, TLabel=19, TNumericEdit=9); 13 interativos; 7 com codigo de dado e formula; 1 com caixa/bit; 8 com valor lido no LOG. Fonte do .exe sha256 `8a2d297c8c21...`.

## Como o ProgBase trata estes controles (valido para a aba inteira)

- **Campo numerico (`TNumericEdit`)** `[OBS-COD]`: ao entrar no campo, `EditGiriCambioEnter` (0x4A98D8) mostra no rotulo `LabelLimite` o texto `(minimo;maximo)` do proprio campo; ao sair, `EditGiriCambioExit` (0x4A9CC0) esconde esse rotulo. Nada e enviado a ECU nesses dois momentos. Ao apertar **Enter**, o `TNumericEdit` limita o numero a [minimo;maximo] (so se minimo!=0 ou maximo!=0; 0x96C170) e o handler generico `EditRifLambdaKeyPress` (0x4A6A00) chama `TStreamDati.Set*` com o *codigo de dado* do campo (campo `+0x2EC`, indice `+0x2F0`; amarrados em 0x48A228): o valor e convertido pela formula do caso, gravado no cache do programa e enviado como SetNumber (escalar) ou SetNumber+indice (vetor). Depois o handler le o valor de volta do cache (`Get*`) e redesenha o campo arredondado ao passo (`Precision`, 0x96BB88). Nao ha releitura da ECU.
- **Caixa de marcar (`TCheckBox`)** `[OBS-COD]`: apertar Enter simula um clique do mouse (`CheckTpsBlocKeyPress` 0x4A8928 envia WM_LBUTTONDOWN/UP); o clique chama o handler `Check...Click`, que liga/desliga **um bit** do vetor FLAG_CONF1/2/3 (o programa regrava o vetor inteiro, ver quadros abaixo) e atualiza o que depende da caixa.
- **Lista (`TKeyComboBox`)** `[OBS-COD]`: Enter no combo chama o `...KeyPress` correspondente, que grava o indice escolhido (por codigo de dado `+0x330` ou por bit de FLAG_CONF).
- **Resposta da ECU a toda gravacao** `[OBS-FIO]`: a porta devolve o eco do quadro e a ECU responde `53 00 53` (LOG linhas 48568-48600 e 31284-31300). Em seguida o programa limpa os buffers e retoma a telemetria (`48 01 49`).
- **Conexao**: os valores 'atuais' vem de leituras `09`/`0A`/`29` feitas ao conectar (LOG de 12/08/2026); a tela do programa mostra o cache dessas leituras.

## Mapa de todos os controles (ordem do DFM)

| # | Componente | Tipo | Rotulo (por / eng / ita) | Unidade | Vetor (SC) | Bit | Visivel no DFM |
|---|---|---|---|---|---|---|---|
| 1 | `LabelCorrettoreIntegrale` | rotulo | Opções / Options / Opzioni | - | - | - | False |
| 2 | `LabelLivEmulAlto` | rotulo | Nível alto de emulação / High emulation level / Livello alto di emulazione | - | - | - | False |
| 3 | `LabelLivEmulBasso` | rotulo | Nível baixo de emulação / Low emulation level / Livello basso di emulazione | - | - | - | False |
| 4 | `LabelV10` | rotulo | - / - / V | - | - | - | False |
| 5 | `LabelV11` | rotulo | - / - / V | - | - | - | False |
| 6 | `LabelRitardoGiriEmulazione` | rotulo | Atraso inicial de emulação / Start emulation delay / Ritardo per attivazione emulazione | - | - | - | False |
| 7 | `LabelS16` | rotulo | - / - / s | - | - | - | False |
| 8 | `LabelSmagrRientroCutoff` | rotulo | Abaixamento saída da cutoff / Exit from cutoff leaning / Smagrimento rientro da cutoff | - | - | - | sim |
| 9 | `LabelNumeroIniettateSmagr` | rotulo | Abaixamento número de injeções / Leaning number of injections / Numero iniettate in smagrimento | - | - | - | sim |
| 10 | `LabelCorrIntInf` | rotulo | - / - / 1 | - | - | - | False |
| 11 | `LabelCorrIntSup` | rotulo | - / - / 2 | - | - | - | False |
| 12 | `Label1` | rotulo | - / - / % | - | - | - | sim |
| 13 | `LabelNotaEmiss` | rotulo | - / - / "Nota: l'emulazione è disattivata con ritardo per attivazione emulazione uguale a zero" | - | - | - | False |
| 14 | `LabelV12` | rotulo | - / - / V | - | - | - | False |
| 15 | `LabelV13` | rotulo | - / - / V | - | - | - | False |
| 16 | `DrawGridEmissioniMultipoint` | grade | - / - / - | - | - | - | False |
| 17 | `EditLivEmulAlto` | edit numerico | Nível alto de emulação / High emulation level / Livello alto di emulazione | V | LIVELLO_EMUL_ALTO (71) | - | False |
| 18 | `EditLivEmulBasso` | edit numerico | Nível baixo de emulação / Low emulation level / Livello basso di emulazione | V | LIVELLO_EMUL_BASSO (72) | - | False |
| 19 | `EditRitardoGiriEmulazione` | edit numerico | Atraso inicial de emulação / Start emulation delay / Ritardo per attivazione emulazione | s | RITARDO_GIRI_EMULAZIONE (135) | - | False |
| 20 | `CheckInibisciEmulazione` | caixa de marcar | Desativa a emulação de rotações baixas / Disable emulation at low rpm / Disattiva emulazione per giri bassi | - | - | FLAG_CONF1[0]&0x8000 | False |
| 21 | `EditSmagrRientroCutoff` | edit numerico | Abaixamento saída da cutoff / Exit from cutoff leaning / Smagrimento rientro da cutoff | % | SMAGRIMENTO_RIENTRO_CUTOFF (136) | - | sim |
| 22 | `EditNumeroIniettateSmagr` | edit numerico | Abaixamento número de injeções / Leaning number of injections / Numero iniettate in smagrimento | - | NUMERO_INIETTATE_SMAGRIMENTO (137) | - | sim |
| 23 | `EditCorrIntInf` | edit numerico | - / - / 1 | 2 | - | - | False |
| 24 | `EditCorrIntSup` | edit numerico | - / - / 2 | - | - | - | False |
| 25 | `DrawGridEmissioni` | grade | - / - / - | - | - | - | False |
| 26 | `GroupBancataL0` | grupo | - / - / Bancata 1 | - | - | - | False |
| 27 | `LabOxigen0` | rotulo | - / - / 0V | - | - | - | False |
| 28 | `LabelOxigen0` | rotulo | - / - / Lambda post. | - | - | - | sim |
| 29 | `GroupBancataL1` | grupo | - / - / Bancata 2 | - | - | - | False |
| 30 | `LabOxigen1` | rotulo | - / - / 0V | - | - | - | False |
| 31 | `LabelOxigen1` | rotulo | - / - / Lambda post. | - | - | - | sim |
| 32 | `DrawGridAnticipoMultipoint` | grade | - / - / - | - | - | - | False |
| 33 | `EditLivEmulStabilizzato` | edit numerico | - / - / V | V | EMULAZIONE_POSTERIORE (170) | - | False |
| 34 | `EditLivEmulCutoff` | edit numerico | - / - / V | V | EMULAZIONE_POSTERIORE (170) | - | False |

## Controles interativos, um a um

### `DrawGridEmissioniMultipoint` — DrawGridEmissioniMultipoint

Tipo: **grade** (`TDrawGrid`), DFM linha 2949 `[OBS-COD]`. Pai: `TabEmissioni`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `None` (associacao None)): ita = "-"; eng = "-"; por = "-" `[DESCONHECIDO]`.
- Posicao no DFM: Left=9 Top=18 Largura=729 Altura=135; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Tabela de pontos da emulacao da sonda lambda (varios pontos). [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `DrawGridEmissioniMultipointKeyPress` (0x4C33D0) `[OBS-COD]`
- `OnDblClick` -> `DrawGridEmissioniMultipointDblClick` (0x4C33E0) `[OBS-COD]`
- `OnKeyDown` -> `DrawGridKKeyDown` (0x4B2514) `[OBS-COD]`
- `OnDrawCell` -> `DrawGridEmissioniMultipointDrawCell` (0x4C2E3C) `[OBS-COD]`: desenho das celulas da grade (exibicao); nao envia nada a ECU

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Grade: cada celula editada chama um setter indexado (SetIdx) do handler `KeyPress`/`DblClick`; quadros por celula: `14 SC lo hi idx valor ck` (SetNumber+indice). Quadros exatos por celula: **DESCONHECIDO** (nao executados aqui).

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditLivEmulAlto` — Nível alto de emulação

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 2978 `[OBS-COD]`. Pai: `TabEmissioni`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelLivEmulAlto` (associacao INFERIDO)): ita = "Livello alto di emulazione"; eng = "High emulation level"; por = "Nível alto de emulação" `[OBS-COD]`.
- Unidade ao lado: **V** `[OBS-COD]` (rotulo `LabelV10`).
- Posicao no DFM: Left=548 Top=195 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 4.9, passo (Precision) 0.02. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Nivel de tensao alto da emulacao da sonda lambda (o que a ECU da gasolina "ve"). [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 30 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **LIVELLO_EMUL_ALTO** (SerialCode 71, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x436B8D): `valor_bruto = trunc((((v * 255) * 0.2) + 0.5))` com `v` = valor digitado.
    - ECU -> tela (getter 0x435326): `valor_na_tela = ((SD[0x2B9C] * 5) * 0.003921568627)`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- LIVELLO_EMUL_ALTO (SC 71): bruto 41; na tela: **0.803922** `[OBS-FIO, tela DERIVADO]` (LOG linha 1106346).

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
  - valor atual da ECU do dono: tela = 0.803922 -> quadro `12 47 00 29 82`.
  - maximo da faixa do DFM: tela = 4.9 -> quadro `12 47 00 FA 53`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4A89D2` (TabEmissioniShow) — incondicional

### `EditLivEmulBasso` — Nível baixo de emulação

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 2998 `[OBS-COD]`. Pai: `TabEmissioni`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelLivEmulBasso` (associacao INFERIDO)): ita = "Livello basso di emulazione"; eng = "Low emulation level"; por = "Nível baixo de emulação" `[OBS-COD]`.
- Unidade ao lado: **V** `[OBS-COD]` (rotulo `LabelV11`).
- Posicao no DFM: Left=548 Top=233 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 4.9, passo (Precision) 0.02. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Nivel de tensao baixo da emulacao da sonda lambda. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 31 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **LIVELLO_EMUL_BASSO** (SerialCode 72, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x436BBC): `valor_bruto = trunc((((v * 255) * 0.2) + 0.5))` com `v` = valor digitado.
    - ECU -> tela (getter 0x435353): `valor_na_tela = ((SD[0x2BA0] * 5) * 0.003921568627)`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- LIVELLO_EMUL_BASSO (SC 72): bruto 5; na tela: **0.098039** `[OBS-FIO, tela DERIVADO]` (LOG linha 1106368).

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
  - valor atual da ECU do dono: tela = 0.098039 -> quadro `12 48 00 05 5F`.
  - maximo da faixa do DFM: tela = 4.9 -> quadro `12 48 00 FA 54`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4A8A4D` (TabEmissioniShow) — incondicional

### `EditRitardoGiriEmulazione` — Atraso inicial de emulação

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 3017 `[OBS-COD]`. Pai: `TabEmissioni`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelRitardoGiriEmulazione` (associacao INFERIDO)): ita = "Ritardo per attivazione emulazione"; eng = "Start emulation delay"; por = "Atraso inicial de emulação" `[OBS-COD]`.
- Unidade ao lado: **s** `[OBS-COD]` (rotulo `LabelS16`).
- Posicao no DFM: Left=548 Top=271 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 255.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Atraso para a emulacao comecar depois de atingir a rotacao. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 58 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **RITARDO_GIRI_EMULAZIONE** (SerialCode 135, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x436E40): `valor_bruto = trunc(v)` com `v` = valor digitado.
    - ECU -> tela (getter 0x435688): `valor_na_tela = SD[0x2B00]`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- RITARDO_GIRI_EMULAZIONE (SC 135): bruto 0; na tela: **0.0** `[OBS-FIO, tela DERIVADO]` (LOG linha 1108314).

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
  - valor atual da ECU do dono: tela = 0.0 -> quadro `12 87 00 00 99`.
  - maximo da faixa do DFM: tela = 255.0 -> quadro `12 87 00 FF 98`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `CheckInibisciEmulazione` — Desativa a emulação de rotações baixas

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 3037 `[OBS-COD]`. Pai: `TabEmissioni`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Disattiva emulazione per giri bassi"; eng = "Disable emulation at low rpm"; por = "Desativa a emulação de rotações baixas" `[OBS-COD]`.
- Posicao no DFM: Left=38 Top=329 Largura=760 Altura=31; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Desliga a emulacao da lambda em rotacao baixa. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 0, mascara 0x8000, caso do setter 28, papel "caixa (Checked)", condicao "obj+0x80D!=0"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 0 = 0x2486 -> bit 0x8000: **desligado** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckInibisciEmulazioneClick` (0x4B9280) `[OBS-COD]`
  - SetFlag1 caso 28 -> FLAG_CONF1 palavra 0 mascara 0x8000 em `0x4B929F`
  - GetFlag1 caso 28 -> FLAG_CONF1 palavra 0 mascara 0x8000 em `0x4B92B1`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 0 mascara 0x8000): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - desligar bit (FLAG_CONF1 palavra 0 mascara 0x8000): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetFlag1(caso 28)` em `0x4B92C0` (CheckInibisciEmulazioneClick) — incondicional
- `Checked/ItemIndex := calculado por GetFlag1(caso 28)` em `0x4A8C53` (TabEmissioniShow) — incondicional

### `EditSmagrRientroCutoff` — Abaixamento saída da cutoff

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 3049 `[OBS-COD]`. Pai: `TabEmissioni`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelSmagrRientroCutoff` (associacao INFERIDO)): ita = "Smagrimento rientro da cutoff"; eng = "Exit from cutoff leaning"; por = "Abaixamento saída da cutoff" `[OBS-COD]`.
- Unidade ao lado: **%** `[OBS-COD]` (rotulo `Label1`).
- Posicao no DFM: Left=548 Top=363 Largura=72 Altura=37; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo -100.0, maximo 100.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTSMAGRRIENTROCUTOFF`: "The purpose of the strategy is to enrich or to lean the mixture leaving the cut-off phase. \ Activation of this option is functional for providing the car with greater quickness of response \ after releasing the accelerator pedal.".

**O que isso faz (linguagem simples)**

- Empobrece ou enriquece a mistura (em %, de -100 a 100) logo depois do corte de desaceleracao; util se o motor apaga ou quase apaga na volta a marcha lenta. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.17).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 59 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **SMAGRIMENTO_RIENTRO_CUTOFF** (SerialCode 136, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x436D1D): `valor_bruto = trunc((v + 100))` com `v` = valor digitado.
    - ECU -> tela (getter 0x435556): `valor_na_tela = (SD[0x2D68] + -100)`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- SMAGRIMENTO_RIENTRO_CUTOFF (SC 136): bruto 100; na tela: **0.0** `[OBS-FIO, tela DERIVADO]` (LOG linha 1108336).

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
  - valor atual da ECU do dono: tela = 0.0 -> quadro `12 88 00 64 FE`.
  - maximo da faixa do DFM: tela = 100.0 -> quadro `12 88 00 C8 62`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditNumeroIniettateSmagr` — Abaixamento número de injeções

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 3069 `[OBS-COD]`. Pai: `TabEmissioni`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelNumeroIniettateSmagr` (associacao INFERIDO)): ita = "Numero iniettate in smagrimento"; eng = "Leaning number of injections"; por = "Abaixamento número de injeções" `[OBS-COD]`.
- Posicao no DFM: Left=548 Top=401 Largura=72 Altura=37; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 254.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTNUMEROINIETTATESMAGR`: "Number of gas injections for which the cut off exit strategy is activated. \ This is the number of enrich/lean injections.".

**O que isso faz (linguagem simples)**

- Por quantas injecoes (0 a 255) vale o ajuste de empobrecimento na saida do corte. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.17).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 60 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **NUMERO_INIETTATE_SMAGRIMENTO** (SerialCode 137, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x436D8F): `valor_bruto = trunc(v)` com `v` = valor digitado.
    - ECU -> tela (getter 0x43559D): `valor_na_tela = SD[0x2D6C]`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- NUMERO_INIETTATE_SMAGRIMENTO (SC 137): bruto 0; na tela: **0.0** `[OBS-FIO, tela DERIVADO]` (LOG linha 1108358).

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
  - valor atual da ECU do dono: tela = 0.0 -> quadro `12 89 00 00 9B`.
  - maximo da faixa do DFM: tela = 254.0 -> quadro `12 89 00 FE 99`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditCorrIntInf` — 1

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 3088 `[OBS-COD]`. Pai: `TabEmissioni`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelCorrIntInf` (associacao INFERIDO)): ita = "1"; eng = "-"; por = "-" `[OBS-COD]`.
- Unidade ao lado: **2** `[OBS-COD]` (rotulo `LabelCorrIntSup`).
- Posicao no DFM: Left=548 Top=439 Largura=102 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 65535.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Limite inferior da correcao integral. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditCorrIntInfKeyPress` (0x4B91BC) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x42FCD0` em `0x4B91F0` (le: CORRETTORE_INTEGRALE; escreve: -)
  - chama o metodo do TStreamDati `0x42FCC0` em `0x4B91FF` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x42FCD0` em `0x4B9242` (le: CORRETTORE_INTEGRALE; escreve: -)
  - chama o metodo do TStreamDati `0x42FCC0` em `0x4B9254` (le: -; escreve: -)
- `OnEnter` -> `EditGiriKFiltroEnter` (0x4BE90C) `[OBS-COD]`
- `OnExit` -> `EditGiriKFiltroExit` (0x4BEABC) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditCorrIntSup` — 2

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 3107 `[OBS-COD]`. Pai: `TabEmissioni`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelCorrIntSup` (associacao INFERIDO)): ita = "2"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=696 Top=439 Largura=102 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 65535.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Limite superior da correcao integral. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditCorrIntInfKeyPress` (0x4B91BC) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x42FCD0` em `0x4B91F0` (le: CORRETTORE_INTEGRALE; escreve: -)
  - chama o metodo do TStreamDati `0x42FCC0` em `0x4B91FF` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x42FCD0` em `0x4B9242` (le: CORRETTORE_INTEGRALE; escreve: -)
  - chama o metodo do TStreamDati `0x42FCC0` em `0x4B9254` (le: -; escreve: -)
- `OnEnter` -> `EditGiriKFiltroEnter` (0x4BE90C) `[OBS-COD]`
- `OnExit` -> `EditGiriKFiltroExit` (0x4BEABC) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `DrawGridEmissioni` — DrawGridEmissioni

Tipo: **grade** (`TDrawGrid`), DFM linha 3127 `[OBS-COD]`. Pai: `TabEmissioni`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `None` (associacao None)): ita = "-"; eng = "-"; por = "-" `[DESCONHECIDO]`.
- Posicao no DFM: Left=7 Top=66 Largura=733 Altura=82; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Tabela da emulacao de emissoes (valores por ponto). [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `DrawGridEmissioniKeyPress` (0x4B4520) `[OBS-COD]`
- `OnDblClick` -> `DrawGridEmissioniDblClick` (0x4B4530) `[OBS-COD]`
- `OnKeyDown` -> `DrawGridKKeyDown` (0x4B2514) `[OBS-COD]`
- `OnDrawCell` -> `DrawGridEmissioniDrawCell` (0x4B3F68) `[OBS-COD]`: desenho das celulas da grade (exibicao); nao envia nada a ECU

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Grade: cada celula editada chama um setter indexado (SetIdx) do handler `KeyPress`/`DblClick`; quadros por celula: `14 SC lo hi idx valor ck` (SetNumber+indice). Quadros exatos por celula: **DESCONHECIDO** (nao executados aqui).

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `DrawGridAnticipoMultipoint` — DrawGridAnticipoMultipoint

Tipo: **grade** (`TDrawGrid`), DFM linha 3229 `[OBS-COD]`. Pai: `TabEmissioni`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `None` (associacao None)): ita = "-"; eng = "-"; por = "-" `[DESCONHECIDO]`.
- Posicao no DFM: Left=9 Top=153 Largura=729 Altura=32; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Tabela de antecipacao (varios pontos) da emulacao. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `DrawGridAnticipoMultipointKeyPress` (0x4C3C28) `[OBS-COD]`
- `OnDblClick` -> `DrawGridAnticipoMultipointDblClick` (0x4C3C38) `[OBS-COD]`
- `OnKeyDown` -> `DrawGridKKeyDown` (0x4B2514) `[OBS-COD]`
- `OnDrawCell` -> `DrawGridAnticipoMultipointDrawCell` (0x4C3944) `[OBS-COD]`: desenho das celulas da grade (exibicao); nao envia nada a ECU

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Grade: cada celula editada chama um setter indexado (SetIdx) do handler `KeyPress`/`DblClick`; quadros por celula: `14 SC lo hi idx valor ck` (SetNumber+indice). Quadros exatos por celula: **DESCONHECIDO** (nao executados aqui).

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditLivEmulStabilizzato` — V

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 3259 `[OBS-COD]`. Pai: `TabEmissioni`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelV10` (associacao INFERIDO)): ita = "V"; eng = "-"; por = "-" `[OBS-COD]`.
- Unidade ao lado: **V** `[OBS-COD]` (rotulo `LabelV12`).
- Posicao no DFM: Left=669 Top=195 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 4.9, passo (Precision) 0.02. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Nivel de tensao da emulacao quando a mistura esta estabilizada. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 80, indice 1 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **EMULAZIONE_POSTERIORE** (SerialCode 170, TAebVector, indexado) `[OBS-COD]`.
    - tela -> ECU (setter 0x4376A9): `valor_bruto = trunc((((v * 255) * 0.2) + 0.5))` com `v` = valor digitado.
    - ECU -> tela (getter 0x435F7E): `valor_na_tela = ((SD[0x283C + i*4] * 5) * 0.003921568627)`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- EMULAZIONE_POSTERIORE (SC 170): bruto [5, 36] (elemento 1 = 36); na tela: **nao calculado** `[OBS-FIO, tela DERIVADO]` (LOG linha 1109104).

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
  - maximo da faixa do DFM: tela = 4.9 -> quadro `13 AA 00 01 FA B8`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := verdadeiro` em `0x4A8A9B` (TabEmissioniShow) — incondicional

### `EditLivEmulCutoff` — V

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 3279 `[OBS-COD]`. Pai: `TabEmissioni`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelV11` (associacao INFERIDO)): ita = "V"; eng = "-"; por = "-" `[OBS-COD]`.
- Unidade ao lado: **V** `[OBS-COD]` (rotulo `LabelV13`).
- Posicao no DFM: Left=669 Top=233 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 4.9, passo (Precision) 0.02. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Nivel de tensao da emulacao durante o corte de desaceleracao. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 80, indice 0 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **EMULAZIONE_POSTERIORE** (SerialCode 170, TAebVector, indexado) `[OBS-COD]`.
    - tela -> ECU (setter 0x4376A9): `valor_bruto = trunc((((v * 255) * 0.2) + 0.5))` com `v` = valor digitado.
    - ECU -> tela (getter 0x435F7E): `valor_na_tela = ((SD[0x283C + i*4] * 5) * 0.003921568627)`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- EMULAZIONE_POSTERIORE (SC 170): bruto [5, 36] (elemento 0 = 5); na tela: **nao calculado** `[OBS-FIO, tela DERIVADO]` (LOG linha 1109104).

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
  - maximo da faixa do DFM: tela = 4.9 -> quadro `13 AA 00 00 FA B7`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := verdadeiro` em `0x4A8AE9` (TabEmissioniShow) — incondicional

## Rotulos com regra de mostrar/esconder ou texto dinamico

### `LabelLivEmulAlto` — "Nível alto de emulação"

Texto: ita "Livello alto di emulazione" / eng "High emulation level" / por "Nível alto de emulação" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := verdadeiro` em `0x4A89BE` (TabEmissioniShow) — incondicional

### `LabelLivEmulBasso` — "Nível baixo de emulação"

Texto: ita "Livello basso di emulazione" / eng "Low emulation level" / por "Nível baixo de emulação" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := verdadeiro` em `0x4A8A39` (TabEmissioniShow) — incondicional

### `LabelV10` — "V"

Texto: ita "V" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4A89E6` (TabEmissioniShow) — incondicional

### `LabelV11` — "V"

Texto: ita "V" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4A8A61` (TabEmissioniShow) — incondicional

### `LabelV12` — "V"

Texto: ita "V" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4A8AAF` (TabEmissioniShow) — incondicional

### `LabelV13` — "V"

Texto: ita "V" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4A8AFD` (TabEmissioniShow) — incondicional

### `LabOxigen0` — "0V"

Texto: ita "0V" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Caption := calculado` em `0x4A5126` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration)
- `Visible := calculado por sub_A4EDD0` em `0x4A517C` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration)

### `LabOxigen1` — "0V"

Texto: ita "0V" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Caption := calculado` em `0x4A558A` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration)
- `Visible := calculado por sub_A4EDD0` em `0x4A55E1` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration)

## Lacunas desta aba

- Edits sem codigo de dado na rotina 0x48A228 (o valor passa por metodo proprio do TStreamDati; formula nao decodificada aqui, `DESCONHECIDO`): `EditCorrIntInf`, `EditCorrIntSup`.
- Grades (`TDrawGrid`): o conteudo e desenhado em `OnDrawCell`; o conteudo e os quadros por celula nao foram decodificados celula a celula: `DrawGridEmissioniMultipoint`, `DrawGridEmissioni`, `DrawGridAnticipoMultipoint`.
- Visibilidade/habilitacao: as regras listadas sao as que o codigo escreve; a combinacao final depende de dados da ECU (modelo, familia LR, MASK_FUNCTION) que o LOG so mostra em parte. O dono usa familia nao-LR (INFERIDO: o original leu SC 192, nunca 193).
- A associacao rotulo<->campo e por nome/geometria (`INFERIDO`), e a chave da dica (HINT...) segue o nome do componente (`INFERIDO`).

