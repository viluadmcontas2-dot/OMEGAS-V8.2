# TabKTempi — Tempi

Nome na tela: ita "Tempi" / eng "None" / por "None" `[OBS-COD]`. Aba 15 de 16 do grupo D3a. Rotina de abertura (OnShow): `TabKTempiShow` em `0x4B1254` `[OBS-COD]`. TabVisible de projeto: sim.

Legenda das marcas: `OBS-COD` = lido do DFM/codigo do ProgBase.exe (ou executado no emulador Unicorn); `OBS-FIO` = visto no Portmon da ECU do dono; `DERIVADO` = calculado a partir dessas observacoes; `INFERIDO` = deducao; `DESCONHECIDO` = sem prova.

**Resumo**: 11 controles no DFM (TLabel=7, TNumericEdit=4); 4 interativos; 2 com codigo de dado e formula; 0 com caixa/bit; 2 com valor lido no LOG. Fonte do .exe sha256 `8a2d297c8c21...`.

## Como o ProgBase trata estes controles (valido para a aba inteira)

- **Campo numerico (`TNumericEdit`)** `[OBS-COD]`: ao entrar no campo, `EditGiriCambioEnter` (0x4A98D8) mostra no rotulo `LabelLimite` o texto `(minimo;maximo)` do proprio campo; ao sair, `EditGiriCambioExit` (0x4A9CC0) esconde esse rotulo. Nada e enviado a ECU nesses dois momentos. Ao apertar **Enter**, o `TNumericEdit` limita o numero a [minimo;maximo] (so se minimo!=0 ou maximo!=0; 0x96C170) e o handler generico `EditRifLambdaKeyPress` (0x4A6A00) chama `TStreamDati.Set*` com o *codigo de dado* do campo (campo `+0x2EC`, indice `+0x2F0`; amarrados em 0x48A228): o valor e convertido pela formula do caso, gravado no cache do programa e enviado como SetNumber (escalar) ou SetNumber+indice (vetor). Depois o handler le o valor de volta do cache (`Get*`) e redesenha o campo arredondado ao passo (`Precision`, 0x96BB88). Nao ha releitura da ECU.
- **Caixa de marcar (`TCheckBox`)** `[OBS-COD]`: apertar Enter simula um clique do mouse (`CheckTpsBlocKeyPress` 0x4A8928 envia WM_LBUTTONDOWN/UP); o clique chama o handler `Check...Click`, que liga/desliga **um bit** do vetor FLAG_CONF1/2/3 (o programa regrava o vetor inteiro, ver quadros abaixo) e atualiza o que depende da caixa.
- **Lista (`TKeyComboBox`)** `[OBS-COD]`: Enter no combo chama o `...KeyPress` correspondente, que grava o indice escolhido (por codigo de dado `+0x330` ou por bit de FLAG_CONF).
- **Resposta da ECU a toda gravacao** `[OBS-FIO]`: a porta devolve o eco do quadro e a ECU responde `53 00 53` (LOG linhas 48568-48600 e 31284-31300). Em seguida o programa limpa os buffers e retoma a telemetria (`48 01 49`).
- **Conexao**: os valores 'atuais' vem de leituras `09`/`0A`/`29` feitas ao conectar (LOG de 12/08/2026); a tela do programa mostra o cache dessas leituras.

## Mapa de todos os controles (ordem do DFM)

| # | Componente | Tipo | Rotulo (por / eng / ita) | Unidade | Vetor (SC) | Bit | Visivel no DFM |
|---|---|---|---|---|---|---|---|
| 1 | `LabelTempoMortoBenz` | rotulo | Tempo morto do injetor de gasolina / Petrol injector dead time / T morto iniettore benzina | - | - | - | sim |
| 2 | `LabelTempoMortoGas` | rotulo | Tempo morto do injetor de gás / Gas injector dead time / T morto iniettore gas | - | - | - | sim |
| 3 | `LabelMsBenz` | rotulo | - / - / ms | - | - | - | sim |
| 4 | `LabelMsGas` | rotulo | - / - / ms | - | - | - | sim |
| 5 | `LabelMsBenzExtra` | rotulo | - / - / ms | - | - | - | sim |
| 6 | `LabelMsGasExtra` | rotulo | - / - / ms | - | - | - | sim |
| 7 | `LabelExtraIniettate` | rotulo | extra-injeção / extra-injections / extrainiettate | - | - | - | sim |
| 8 | `EditTempoMortoBenz` | edit numerico | Tempo morto do injetor de gasolina / Petrol injector dead time / T morto iniettore benzina | ms | - | - | sim |
| 9 | `EditTempoMortoGas` | edit numerico | Tempo morto do injetor de gás / Gas injector dead time / T morto iniettore gas | ms | - | - | sim |
| 10 | `EditTempoBenzExtra` | edit numerico | - / - / ms | ms | TEMPI_EXTRAINIETTATE (155) | - | sim |
| 11 | `EditTempoGasExtra` | edit numerico | - / - / ms | ms | TEMPI_EXTRAINIETTATE (155) | - | sim |

## Controles interativos, um a um

### `EditTempoMortoBenz` — Tempo morto do injetor de gasolina

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 5946 `[OBS-COD]`. Pai: `TabKTempi`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTempoMortoBenz` (associacao INFERIDO)): ita = "T morto iniettore benzina"; eng = "Petrol injector dead time"; por = "Tempo morto do injetor de gasolina" `[OBS-COD]`.
- Unidade ao lado: **ms** `[OBS-COD]` (rotulo `LabelMsBenz`).
- Posicao no DFM: Left=461 Top=33 Largura=72 Altura=32; visivel de projeto: sim; habilitado de projeto: False `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 5.0, passo (Precision) 0.02. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTTEMPOMORTOBENZ`: "Petrol injector dead time".

**O que isso faz (linguagem simples)**

- Tempo morto do injetor de gasolina: compensa o atraso de abrir/fechar; o manual recomenda nao mexer. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.25).
- Manual SW 2.16.4 C, p.25 (linha 1303 de sw.txt): "Petrol injector dead time; Gas injector dead time : These are two characteristic parameters regarding injectors and serve to compensate for any delays the opening and closing of injectors." `[OBS-COD]`.

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditTempoMortoBenzKeyPress` (0x4B11B0) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x4312A8` em `0x4B11E3` (le: TEMPO_APERTURA_INIETTORE, TEMPO_CHIUSURA_INIETTORE, TEMPO_MORTO_INIETTORI_BENZINA, TEMPO_MORTO_INIETTORI_GAS; escreve: -)
  - chama o metodo do TStreamDati `0x4312A8` em `0x4B1204` (le: TEMPO_APERTURA_INIETTORE, TEMPO_CHIUSURA_INIETTORE, TEMPO_MORTO_INIETTORI_BENZINA, TEMPO_MORTO_INIETTORI_GAS; escreve: -)
  - chama o metodo do TStreamDati `0x4337D8` em `0x4B1243` (le: TEMPO_MORTO_INIETTORI_LR; escreve: -)
- `OnEnter` -> `EditGiriKFiltroEnter` (0x4BE90C) `[OBS-COD]`
- `OnExit` -> `EditGiriKFiltroExit` (0x4BEABC) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Metodo proprio `0x4312A8` (caso 3) executado no Unicorn `[DERIVADO]`:
  - tela = 1.0 -> quadro(s) `13 7D 00 87 01 18`.
  - tela = 2.0 -> quadro(s) `13 7D 00 0D 03 A0`.
  - Estrutura: igual a acima. Fonte: metodo original 0x4312A8 do TStreamDati executado no Unicorn (convencao assumida: eax=Self, edx=caso, double na pilha; o handler EditTempoMortoBenzKeyPress o chama em 0x4B11E3)
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditTempoMortoGas` — Tempo morto do injetor de gás

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 5966 `[OBS-COD]`. Pai: `TabKTempi`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTempoMortoGas` (associacao INFERIDO)): ita = "T morto iniettore gas"; eng = "Gas injector dead time"; por = "Tempo morto do injetor de gás" `[OBS-COD]`.
- Unidade ao lado: **ms** `[OBS-COD]` (rotulo `LabelMsGas`).
- Posicao no DFM: Left=461 Top=73 Largura=72 Altura=32; visivel de projeto: sim; habilitado de projeto: False `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 5.0, passo (Precision) 0.02. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTTEMPOMORTOGAS`: "Gas injector dead time".

**O que isso faz (linguagem simples)**

- Tempo morto do injetor de gas: compensa o atraso de abrir/fechar; o manual recomenda nao mexer. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.25).
- Manual SW 2.16.4 C, p.25 (linha 1303 de sw.txt): "Petrol injector dead time; Gas injector dead time : These are two characteristic parameters regarding injectors and serve to compensate for any delays the opening and closing of injectors." `[OBS-COD]`.

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditTempoMortoBenzKeyPress` (0x4B11B0) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x4312A8` em `0x4B11E3` (le: TEMPO_APERTURA_INIETTORE, TEMPO_CHIUSURA_INIETTORE, TEMPO_MORTO_INIETTORI_BENZINA, TEMPO_MORTO_INIETTORI_GAS; escreve: -)
  - chama o metodo do TStreamDati `0x4312A8` em `0x4B1204` (le: TEMPO_APERTURA_INIETTORE, TEMPO_CHIUSURA_INIETTORE, TEMPO_MORTO_INIETTORI_BENZINA, TEMPO_MORTO_INIETTORI_GAS; escreve: -)
  - chama o metodo do TStreamDati `0x4337D8` em `0x4B1243` (le: TEMPO_MORTO_INIETTORI_LR; escreve: -)
- `OnEnter` -> `EditGiriKFiltroEnter` (0x4BE90C) `[OBS-COD]`
- `OnExit` -> `EditGiriKFiltroExit` (0x4BEABC) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Metodo proprio `0x4312A8` (caso 4) executado no Unicorn `[DERIVADO]`:
  - tela = 1.0 -> quadro(s) `13 7E 00 87 01 19`.
  - tela = 2.0 -> quadro(s) `13 7E 00 0D 03 A1`.
  - Estrutura: igual a acima. Fonte: metodo original 0x4312A8 do TStreamDati executado no Unicorn (convencao assumida: eax=Self, edx=caso, double na pilha; o handler EditTempoMortoBenzKeyPress o chama em 0x4B1204)
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditTempoBenzExtra` — ms

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 5986 `[OBS-COD]`. Pai: `TabKTempi`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelMsBenz` (associacao INFERIDO)): ita = "ms"; eng = "-"; por = "-" `[OBS-COD]`.
- Unidade ao lado: **ms** `[OBS-COD]` (rotulo `LabelMsBenzExtra`).
- Posicao no DFM: Left=623 Top=31 Largura=72 Altura=32; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 5.0, passo (Precision) 0.02. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Tempo morto da gasolina nas extra-injecoes (0 a 5 ms); em ECU 184-188 coloque 1,4 para cortar as extra-injecoes. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.26).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 71, indice 0 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **TEMPI_EXTRAINIETTATE** (SerialCode 155, TAebVector, indexado) `[OBS-COD]`.
    - tela -> ECU (setter 0x437581): `valor_bruto = trunc(((v / ((SD[0x2980] * 0.00025) * 0.004)) + 0.5))` com `v` = valor digitado.
    - ECU -> tela (getter 0x435E50): `valor_na_tela = (((SD[0x2980] * 0.00025) * 0.004) * SD[0x2C64 + i*4])`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- TEMPI_EXTRAINIETTATE (SC 155): bruto [0, 0, 39] (elemento 0 = 0); na tela: **nao calculado** `[OBS-FIO, tela DERIVADO]` (LOG linha 1108916).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditRifLambdaKeyPress` (0x4A6A00) `[OBS-COD]`
  - SetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A45` (handler generico)
  - GetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A64` (handler generico)
  - SetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A99` (handler generico)
  - GetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6AB1` (handler generico)
- `OnEnter` -> `EditGiriKFiltroEnter` (0x4BE90C) `[OBS-COD]`
- `OnExit` -> `EditGiriKFiltroExit` (0x4BEABC) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Gravar valor (executado no Unicorn com o codigo original; `[OBS-COD]`):
  - maximo da faixa do DFM: tela = 5.0 -> quadro `14 9B 00 00 A1 07 57`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditTempoGasExtra` — ms

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 6006 `[OBS-COD]`. Pai: `TabKTempi`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelMsGas` (associacao INFERIDO)): ita = "ms"; eng = "-"; por = "-" `[OBS-COD]`.
- Unidade ao lado: **ms** `[OBS-COD]` (rotulo `LabelMsGasExtra`).
- Posicao no DFM: Left=623 Top=72 Largura=72 Altura=32; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 5.0, passo (Precision) 0.02. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Tempo morto do gas nas extra-injecoes (0 a 5 ms), onde o injetor de gas nao e linear. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.26).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 71, indice 1 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **TEMPI_EXTRAINIETTATE** (SerialCode 155, TAebVector, indexado) `[OBS-COD]`.
    - tela -> ECU (setter 0x437581): `valor_bruto = trunc(((v / ((SD[0x2980] * 0.00025) * 0.004)) + 0.5))` com `v` = valor digitado.
    - ECU -> tela (getter 0x435E50): `valor_na_tela = (((SD[0x2980] * 0.00025) * 0.004) * SD[0x2C64 + i*4])`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- TEMPI_EXTRAINIETTATE (SC 155): bruto [0, 0, 39] (elemento 1 = 0); na tela: **nao calculado** `[OBS-FIO, tela DERIVADO]` (LOG linha 1108916).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditRifLambdaKeyPress` (0x4A6A00) `[OBS-COD]`
  - SetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A45` (handler generico)
  - GetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A64` (handler generico)
  - SetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A99` (handler generico)
  - GetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6AB1` (handler generico)
- `OnEnter` -> `EditGiriKFiltroEnter` (0x4BE90C) `[OBS-COD]`
- `OnExit` -> `EditGiriKFiltroExit` (0x4BEABC) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Gravar valor (executado no Unicorn com o codigo original; `[OBS-COD]`):
  - maximo da faixa do DFM: tela = 5.0 -> quadro `14 9B 00 01 A1 07 58`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

## Lacunas desta aba

- Edits sem codigo de dado na rotina 0x48A228 (o valor passa por metodo proprio do TStreamDati; formula nao decodificada aqui, `DESCONHECIDO`): `EditTempoMortoBenz`, `EditTempoMortoGas`.
- Visibilidade/habilitacao: as regras listadas sao as que o codigo escreve; a combinacao final depende de dados da ECU (modelo, familia LR, MASK_FUNCTION) que o LOG so mostra em parte. O dono usa familia nao-LR (INFERIDO: o original leu SC 192, nunca 193).
- A associacao rotulo<->campo e por nome/geometria (`INFERIDO`), e a chave da dica (HINT...) segue o nome do componente (`INFERIDO`).

