# TabTemp — Temperatura

Nome na tela: ita "Temperatura" / eng "None" / por "None" `[OBS-COD]`. Aba 6 de 16 do grupo D3a. Rotina de abertura (OnShow): `TabTempShow` em `0x4B92C8` `[OBS-COD]`. TabVisible de projeto: False.

Legenda das marcas: `OBS-COD` = lido do DFM/codigo do ProgBase.exe (ou executado no emulador Unicorn); `OBS-FIO` = visto no Portmon da ECU do dono; `DERIVADO` = calculado a partir dessas observacoes; `INFERIDO` = deducao; `DESCONHECIDO` = sem prova.

**Resumo**: 23 controles no DFM (TButton=1, TCheckBox=4, TDrawGrid=1, TKeyComboBox=1, TLabel=12, TNumericEdit=4); 11 interativos; 0 com codigo de dado e formula; 4 com caixa/bit; 5 com valor lido no LOG. Fonte do .exe sha256 `8a2d297c8c21...`.

## Como o ProgBase trata estes controles (valido para a aba inteira)

- **Campo numerico (`TNumericEdit`)** `[OBS-COD]`: ao entrar no campo, `EditGiriCambioEnter` (0x4A98D8) mostra no rotulo `LabelLimite` o texto `(minimo;maximo)` do proprio campo; ao sair, `EditGiriCambioExit` (0x4A9CC0) esconde esse rotulo. Nada e enviado a ECU nesses dois momentos. Ao apertar **Enter**, o `TNumericEdit` limita o numero a [minimo;maximo] (so se minimo!=0 ou maximo!=0; 0x96C170) e o handler generico `EditRifLambdaKeyPress` (0x4A6A00) chama `TStreamDati.Set*` com o *codigo de dado* do campo (campo `+0x2EC`, indice `+0x2F0`; amarrados em 0x48A228): o valor e convertido pela formula do caso, gravado no cache do programa e enviado como SetNumber (escalar) ou SetNumber+indice (vetor). Depois o handler le o valor de volta do cache (`Get*`) e redesenha o campo arredondado ao passo (`Precision`, 0x96BB88). Nao ha releitura da ECU.
- **Caixa de marcar (`TCheckBox`)** `[OBS-COD]`: apertar Enter simula um clique do mouse (`CheckTpsBlocKeyPress` 0x4A8928 envia WM_LBUTTONDOWN/UP); o clique chama o handler `Check...Click`, que liga/desliga **um bit** do vetor FLAG_CONF1/2/3 (o programa regrava o vetor inteiro, ver quadros abaixo) e atualiza o que depende da caixa.
- **Lista (`TKeyComboBox`)** `[OBS-COD]`: Enter no combo chama o `...KeyPress` correspondente, que grava o indice escolhido (por codigo de dado `+0x330` ou por bit de FLAG_CONF).
- **Resposta da ECU a toda gravacao** `[OBS-FIO]`: a porta devolve o eco do quadro e a ECU responde `53 00 53` (LOG linhas 48568-48600 e 31284-31300). Em seguida o programa limpa os buffers e retoma a telemetria (`48 01 49`).
- **Conexao**: os valores 'atuais' vem de leituras `09`/`0A`/`29` feitas ao conectar (LOG de 12/08/2026); a tela do programa mostra o cache dessas leituras.

## Mapa de todos os controles (ordem do DFM)

| # | Componente | Tipo | Rotulo (por / eng / ita) | Unidade | Vetor (SC) | Bit | Visivel no DFM |
|---|---|---|---|---|---|---|---|
| 1 | `LabelParametriTemp` | rotulo | Parâmetros para o sensor de temperatura do motor / Engine temperature sensor parameters / Parametri per il sensore di temperatura motore della vettura | - | - | - | False |
| 2 | `LabelRPullUp` | rotulo | - / - / R pull-up | - | - | - | False |
| 3 | `LabelDeltaT` | rotulo | - / - / Delta T | - | - | - | False |
| 4 | `LabRPullUp` | rotulo | - / - / ohm | - | - | - | False |
| 5 | `LabDeltaT` | rotulo | - / - / °C | - | - | - | False |
| 6 | `LabelTMin` | rotulo | - / - / T min | - | - | - | False |
| 7 | `LabelTMax` | rotulo | - / - / T max | - | - | - | False |
| 8 | `LabTMin` | rotulo | - / - / °C | - | - | - | False |
| 9 | `LabTMax` | rotulo | - / - / °C | - | - | - | False |
| 10 | `LabelCaratteristicaTemp` | rotulo | Tabela do sensor de temperatura do motor / Engine temperature sensor characterisics / Caratteristica sensore di temperatura motore della vettura | - | - | - | False |
| 11 | `LabelInstr` | rotulo | VOLTAR: introduza valor ESPAÇO: aquisição do valor / RETURN: insert value SPACE: acquire value / INVIO: inserimento valore   SPAZIO: apprendimento valore  | - | - | - | False |
| 12 | `LabelTempGas` | rotulo | Tipo de sensor da temp. do gás / Gas temperature sensor type / Tipo di sensore temperatura gas | - | - | - | sim |
| 13 | `CheckTempMotore` | caixa de marcar | - / - / Utilizza sensore di temperatura motore della vettura | - | - | FLAG_CONF1[1]&0x0004 | False |
| 14 | `CheckTempAcqua` | caixa de marcar | - / - / Abilita cambio con temperatura acqua | - | - | FLAG_CONF1[1]&0x0001 | False |
| 15 | `CheckTempGas` | caixa de marcar | - / - / Abilita cambio con temperatura gas | - | - | FLAG_CONF1[1]&0x0002 | sim |
| 16 | `EditDeltaT` | edit numerico | - / - / Delta T | °C | - | - | False |
| 17 | `EditRPullUp` | edit numerico | - / - / R pull-up | ohm | - | - | False |
| 18 | `EditTMax` | edit numerico | - / - / T max | °C | - | - | False |
| 19 | `EditTMin` | edit numerico | - / - / T min | °C | - | - | False |
| 20 | `ComboTempRiduttore` | combo | Tipo de sensor da temp. do gás / Gas temperature sensor type / Tipo di sensore temperatura gas | - | - | - | sim |
| 21 | `ButtonNuoviParametri` | botao | &Modificar parâmetros do sensor da temperatura / &Modify temperature sensor parameters / &Modifica parametri sensore temperatura motore | - | - | - | False |
| 22 | `DrawGridTemp` | grade | - / - / - | - | - | - | False |
| 23 | `CheckFlashLubeSuTempAcqua` | caixa de marcar | Diagnosis on the water temperature sensor for VP system / Diagnosis on the water temperature sensor for VP system / Diagnosis on the water temperature sensor for VP system | - | - | FLAG_CONF3[0]&0x0080 | False |

## Controles interativos, um a um

### `CheckTempMotore` — Utilizza sensore di temperatura motore della vettura

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 4764 `[OBS-COD]`. Pai: `TabTemp`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Utilizza sensore di temperatura motore della vettura"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=38 Top=157 Largura=750 Altura=34; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTCHECKTEMPMOTORE`: "Use water temperature sensor of the car".

**O que isso faz (linguagem simples)**

- Usa o sensor de temperatura original do motor do carro em vez de um sensor extra; os parametros do sinal sao ajustados com um scanner OBD. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.23).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 1, mascara 0x0004, caso do setter 10, papel "caixa (Checked)", condicao "sempre"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 1 = 0x1051 -> bit 0x0004: **desligado** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckTempMotoreClick` (0x4B8E88) `[OBS-COD]`
  - SetFlag1 caso 10 -> FLAG_CONF1 palavra 1 mascara 0x0004 em `0x4B8EB0`
  - muda Visible de `LabelParametriTemp` em `0x4B8ECB`
  - muda Visible de `LabelRPullUp` em `0x4B8EDF`
  - muda Visible de `EditRPullUp` em `0x4B8EF3`
  - muda Visible de `LabRPullUp` em `0x4B8F07`
  - muda Visible de `LabelDeltaT` em `0x4B8F1B`
  - muda Visible de `EditDeltaT` em `0x4B8F2F`
  - muda Visible de `LabDeltaT` em `0x4B8F43`
  - muda Visible de `LabelTMin` em `0x4B8F57`
  - muda Visible de `EditTMin` em `0x4B8F6B`
  - muda Visible de `LabTMin` em `0x4B8F7F`
  - muda Visible de `LabelTMax` em `0x4B8F93`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 1 mascara 0x0004): 2486 1051 -> 2486 1055, quadro `35 03 00 86 24 55 10 47`.
  - desligar bit (FLAG_CONF1 palavra 1 mascara 0x0004): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetFlag1(caso 10)` em `0x4B932D` (TabTempShow) — incondicional
- `Checked/ItemIndex := calculado por GetFlag1(caso 10)` em `0x49064C` (FormShow; metodo de OUTRA aba/tela) — incondicional

### `CheckTempAcqua` — Abilita cambio con temperatura acqua

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 4785 `[OBS-COD]`. Pai: `TabTemp`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Abilita cambio con temperatura acqua"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=38 Top=85 Largura=750 Altura=34; visivel de projeto: False; habilitado de projeto: False `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTCHECKTEMPACQUA`: "Enable change-over with water temperature".

**O que isso faz (linguagem simples)**

- Liga a troca condicionada a temperatura da agua (so disponivel se o fio laranja estiver ligado ao sensor); nao se marca na mao. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.22).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 1, mascara 0x0001, caso do setter 8, papel "caixa (Checked)", condicao "sempre"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 1 = 0x1051 -> bit 0x0001: **LIGADO** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckTempAcquaClick` (0x4B8B30) `[OBS-COD]`
  - SetFlag1 caso 8 -> FLAG_CONF1 palavra 1 mascara 0x0001 em `0x4B8B60`
  - chama o metodo do TStreamDati `0x434548` em `0x4B8B6A` (le: -; escreve: -)
  - GetFlag1 caso 8 -> FLAG_CONF1 palavra 1 mascara 0x0001 em `0x4B8B8C`
  - muda Visible de um controle nao identificado pela analise estatica em `0x4B8BBF`
  - muda Visible de um controle nao identificado pela analise estatica em `0x4B8BD3`
  - muda Visible de um controle nao identificado pela analise estatica em `0x4B8BF7`
  - muda Visible de um controle nao identificado pela analise estatica em `0x4B8C0B`
  - muda Visible de um controle nao identificado pela analise estatica em `0x4B8C1F`
  - GetFlag1 caso 8 -> FLAG_CONF1 palavra 1 mascara 0x0001 em `0x4B8C3A`
  - chama o metodo do TStreamDati `0x431878` em `0x4B8C50` (le: STATO_DIAGNOSI, STATO_DIAGNOSI_LR; escreve: -)
  - chama o metodo do TStreamDati `0x4318F8` em `0x4B8C64` (le: ABIL_DIAGNOSI, ABIL_DIAGNOSI_LR, STATO_DIAGNOSI, STATO_DIAGNOSI_LR; escreve: -)
  - chama o metodo do TStreamDati `0x431878` em `0x4B8C78` (le: STATO_DIAGNOSI, STATO_DIAGNOSI_LR; escreve: -)
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 1 mascara 0x0001): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - desligar bit (FLAG_CONF1 palavra 1 mascara 0x0001): 2486 1051 -> 2486 1050, quadro `35 03 00 86 24 50 10 42`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetFlag1(caso 8)` em `0x4B92F1` (TabTempShow) — incondicional
- `Checked/ItemIndex := calculado por GetFlag1(caso 8)` em `0x490610` (FormShow; metodo de OUTRA aba/tela) — incondicional

### `CheckTempGas` — Abilita cambio con temperatura gas

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 4802 `[OBS-COD]`. Pai: `TabTemp`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Abilita cambio con temperatura gas"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=38 Top=51 Largura=750 Altura=34; visivel de projeto: sim; habilitado de projeto: False `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Liga a troca condicionada a temperatura do gas lida no sensor da linha de baixa pressao; depende da ligacao do fio, nao se marca na mao. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.22).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 1, mascara 0x0002, caso do setter 9, papel "caixa (Checked)", condicao "sempre"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 1 = 0x1051 -> bit 0x0002: **desligado** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckTempRiduttoreClick` (0x4B8E10) `[OBS-COD]`
  - SetFlag1 caso 9 -> FLAG_CONF1 palavra 1 mascara 0x0002 em `0x4B8E38`
  - GetFlag1 caso 9 -> FLAG_CONF1 palavra 1 mascara 0x0002 em `0x4B8E4A`
  - muda Visible de `LabelTempCambio` em `0x4B8E57`
  - muda Visible de `EditTempCambio` em `0x4B8E6B`
  - muda Visible de `LabelT1` em `0x4B8E7F`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 1 mascara 0x0002): 2486 1051 -> 2486 1053, quadro `35 03 00 86 24 53 10 45`.
  - desligar bit (FLAG_CONF1 palavra 1 mascara 0x0002): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetFlag1(caso 9)` em `0x4B930F` (TabTempShow) — incondicional
- `Checked/ItemIndex := calculado por GetFlag1(caso 9)` em `0x49062E` (FormShow; metodo de OUTRA aba/tela) — incondicional

### `EditDeltaT` — Delta T

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 4816 `[OBS-COD]`. Pai: `TabTemp`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelDeltaT` (associacao INFERIDO)): ita = "Delta T"; eng = "-"; por = "-" `[OBS-COD]`.
- Unidade ao lado: **°C** `[OBS-COD]` (rotulo `LabDeltaT`).
- Posicao no DFM: Left=151 Top=265 Largura=87 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 1.0, maximo 100.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTLABELDELTAT`: "Delta T".

**O que isso faz (linguagem simples)**

- Velocidade com que o programa adquire a leitura de tensao do sensor do carro. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.23).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnEnter` -> `EditRPullUpEnter` (0x4B86C4) `[OBS-COD]`
  - muda Caption de `LabelLimite` em `0x4B8815`
  - muda Visible de `LabelLimite` em `0x4B88AA`
- `OnExit` -> `EditRPullUpExit` (0x4B88C0) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x4303B8` em `0x4B8917` (le: PARAMETRI_TEMP; escreve: -)
  - chama o metodo do TStreamDati `0x4303B8` em `0x4B8942` (le: PARAMETRI_TEMP; escreve: -)
  - chama o metodo do TStreamDati `0x4303B8` em `0x4B8962` (le: PARAMETRI_TEMP; escreve: -)
  - chama o metodo do TStreamDati `0x4303B8` em `0x4B8982` (le: PARAMETRI_TEMP; escreve: -)
  - chama o metodo do TStreamDati `0x4303B8` em `0x4B8A1F` (le: PARAMETRI_TEMP; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8A2A` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8A43` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8A64` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8A7F` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8A9A` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8AD2` (le: -; escreve: -)
  - muda Visible de `LabelLimite` em `0x4B8B25`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4B8F2F` (CheckTempMotoreClick) — incondicional

### `EditRPullUp` — R pull-up

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 4838 `[OBS-COD]`. Pai: `TabTemp`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelRPullUp` (associacao INFERIDO)): ita = "R pull-up"; eng = "-"; por = "-" `[OBS-COD]`.
- Unidade ao lado: **ohm** `[OBS-COD]` (rotulo `LabRPullUp`).
- Posicao no DFM: Left=151 Top=226 Largura=87 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 1.0, maximo 5000.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTLABELRPULLUP`: "Pull-up resistor".

**O que isso faz (linguagem simples)**

- Valor do resistor pull-up da central de gasolina (se nao souber, deixe o padrao). `[OBS-COD]` (fonte: manual SW 2.16.4 C p.23).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnEnter` -> `EditRPullUpEnter` (0x4B86C4) `[OBS-COD]`
  - muda Caption de `LabelLimite` em `0x4B8815`
  - muda Visible de `LabelLimite` em `0x4B88AA`
- `OnExit` -> `EditRPullUpExit` (0x4B88C0) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x4303B8` em `0x4B8917` (le: PARAMETRI_TEMP; escreve: -)
  - chama o metodo do TStreamDati `0x4303B8` em `0x4B8942` (le: PARAMETRI_TEMP; escreve: -)
  - chama o metodo do TStreamDati `0x4303B8` em `0x4B8962` (le: PARAMETRI_TEMP; escreve: -)
  - chama o metodo do TStreamDati `0x4303B8` em `0x4B8982` (le: PARAMETRI_TEMP; escreve: -)
  - chama o metodo do TStreamDati `0x4303B8` em `0x4B8A1F` (le: PARAMETRI_TEMP; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8A2A` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8A43` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8A64` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8A7F` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8A9A` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8AD2` (le: -; escreve: -)
  - muda Visible de `LabelLimite` em `0x4B8B25`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4B8EF3` (CheckTempMotoreClick) — incondicional

### `EditTMax` — T max

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 4859 `[OBS-COD]`. Pai: `TabTemp`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTMax` (associacao INFERIDO)): ita = "T max"; eng = "-"; por = "-" `[OBS-COD]`.
- Unidade ao lado: **°C** `[OBS-COD]` (rotulo `LabTMax`).
- Posicao no DFM: Left=529 Top=265 Largura=87 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 180.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTLABELTMAX`: "Maximum temperature".

**O que isso faz (linguagem simples)**

- Temperatura maxima da parametrizacao do sensor do carro (sugerido 100 graus C). `[OBS-COD]` (fonte: manual SW 2.16.4 C p.23).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnEnter` -> `EditRPullUpEnter` (0x4B86C4) `[OBS-COD]`
  - muda Caption de `LabelLimite` em `0x4B8815`
  - muda Visible de `LabelLimite` em `0x4B88AA`
- `OnExit` -> `EditRPullUpExit` (0x4B88C0) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x4303B8` em `0x4B8917` (le: PARAMETRI_TEMP; escreve: -)
  - chama o metodo do TStreamDati `0x4303B8` em `0x4B8942` (le: PARAMETRI_TEMP; escreve: -)
  - chama o metodo do TStreamDati `0x4303B8` em `0x4B8962` (le: PARAMETRI_TEMP; escreve: -)
  - chama o metodo do TStreamDati `0x4303B8` em `0x4B8982` (le: PARAMETRI_TEMP; escreve: -)
  - chama o metodo do TStreamDati `0x4303B8` em `0x4B8A1F` (le: PARAMETRI_TEMP; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8A2A` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8A43` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8A64` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8A7F` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8A9A` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8AD2` (le: -; escreve: -)
  - muda Visible de `LabelLimite` em `0x4B8B25`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4B8FA7` (CheckTempMotoreClick) — incondicional

### `EditTMin` — T min

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 4880 `[OBS-COD]`. Pai: `TabTemp`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTMin` (associacao INFERIDO)): ita = "T min"; eng = "-"; por = "-" `[OBS-COD]`.
- Unidade ao lado: **°C** `[OBS-COD]` (rotulo `LabTMin`).
- Posicao no DFM: Left=529 Top=226 Largura=87 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo -40.0, maximo 120.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTLABELTMIN`: "Minimum temperature".

**O que isso faz (linguagem simples)**

- Temperatura minima da parametrizacao do sensor do carro (sugerido: a menor lida com o motor frio no scanner OBD). `[OBS-COD]` (fonte: manual SW 2.16.4 C p.23).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnEnter` -> `EditRPullUpEnter` (0x4B86C4) `[OBS-COD]`
  - muda Caption de `LabelLimite` em `0x4B8815`
  - muda Visible de `LabelLimite` em `0x4B88AA`
- `OnExit` -> `EditRPullUpExit` (0x4B88C0) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x4303B8` em `0x4B8917` (le: PARAMETRI_TEMP; escreve: -)
  - chama o metodo do TStreamDati `0x4303B8` em `0x4B8942` (le: PARAMETRI_TEMP; escreve: -)
  - chama o metodo do TStreamDati `0x4303B8` em `0x4B8962` (le: PARAMETRI_TEMP; escreve: -)
  - chama o metodo do TStreamDati `0x4303B8` em `0x4B8982` (le: PARAMETRI_TEMP; escreve: -)
  - chama o metodo do TStreamDati `0x4303B8` em `0x4B8A1F` (le: PARAMETRI_TEMP; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8A2A` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8A43` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8A64` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8A7F` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8A9A` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x4303A8` em `0x4B8AD2` (le: -; escreve: -)
  - muda Visible de `LabelLimite` em `0x4B8B25`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4B8F6B` (CheckTempMotoreClick) — incondicional

### `ComboTempRiduttore` — Tipo de sensor da temp. do gás

Tipo: **combo** (`TKeyComboBox`), DFM linha 4902 `[OBS-COD]`. Pai: `TabTemp`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTempGas` (associacao INFERIDO)): ita = "Tipo di sensore temperatura gas"; eng = "Gas temperature sensor type"; por = "Tipo de sensor da temp. do gás" `[OBS-COD]`.
- Posicao no DFM: Left=529 Top=10 Largura=178 Altura=37; visivel de projeto: sim; habilitado de projeto: False `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Opcoes da lista `[OBS-COD]`: 0 = "4k7"; 1 = "1k"; 2 = "2k2"; 3 = "SENSATA".
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTLABELTEMPGAS`: "Temperature gas sensor type".

**O que isso faz (linguagem simples)**

- Tipo do sensor de temperatura do gas instalado (o 4K7 e o padrao nos kits Landi e Keihin). `[OBS-COD]` (fonte: manual SW 2.16.4 C p.22).
- Manual SW 2.16.4 C, p.22 (linha 1094 de sw.txt): "A Gas temperature sensor type Identifies the type of gas sensor used. The 4K7 sensor is fitted as default with: the OMEGAS- EVO version with Matrix and Lovato “J-K” injec-" `[OBS-COD]`.

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `ComboTempRiduttoreKeyPress` (0x4B9494) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x430878` em `0x4B94BE` (le: TIPO_SENSORE_TEMPERATURA; escreve: -)

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Cada opcao da lista chama o metodo `0x430878` `[INFERIDO]` (convencao assumida):
  - opcao 0 "4k7" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x430887.
  - opcao 1 "1k" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x430887.
  - opcao 2 "2k2" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x430887.
  - opcao 3 "SENSATA" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x430887.
  - Estrutura: igual a acima. Fonte: metodo 0x430878 executado no Unicorn com edx = indice da opcao (convencao assumida; o handler ComboTempRiduttoreKeyPress o chama em 0x4B94BE)
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por sub_430864` em `0x4B93BA` (TabTempShow) — incondicional
- `Checked/ItemIndex := calculado por sub_430864` em `0x49094D` (FormShow; metodo de OUTRA aba/tela) — incondicional

### `ButtonNuoviParametri` — &Modificar parâmetros do sensor da temperatura

Tipo: **botao** (`TButton`), DFM linha 4925 `[OBS-COD]`. Pai: `TabTemp`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "&Modifica parametri sensore temperatura motore"; eng = "&Modify temperature sensor parameters"; por = "&Modificar parâmetros do sensor da temperatura" `[OBS-COD]`.
- Posicao no DFM: Left=40 Top=481 Largura=667 Altura=38; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTBUTTONNUOVIPARAMETRI`: "Modify parameters of engine temperature sensor".

**O que isso faz (linguagem simples)**

- Abre a tela para ajustar os parametros do sensor de temperatura do motor (pull-up, delta T, Tmin e Tmax) e montar a tabela temperatura x tensao. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.23).
- Manual SW 2.16.4 C, p.23 (linha 1135 de sw.txt): "D By pressing “Modify temperature sensor parameters” you can set: E R pull-up: the pull-up resistance value in the petrol ECU (if you don’t know the value, you can leave the default setting)." `[OBS-COD]`.

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 1, mascara 0x0004, caso do setter 10, papel "None", condicao "sempre"; origem: handler ButtonNuoviParametriClick.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 1 = 0x1051 -> bit 0x0004: **desligado** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `ButtonNuoviParametriClick` (0x4B9000) `[OBS-COD]`
  - muda Caption de `ButtonNuoviParametri` em `0x4B9063`
  - muda Caption de `ButtonNuoviParametri` em `0x4B9139`
  - SetFlag1 caso 10 -> FLAG_CONF1 palavra 1 mascara 0x0004 em `0x4B9192`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 1 mascara 0x0004): 2486 1051 -> 2486 1055, quadro `35 03 00 86 24 55 10 47`.
  - desligar bit (FLAG_CONF1 palavra 1 mascara 0x0004): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Caption := calculado` em `0x4B9063` (ButtonNuoviParametriClick) — SE sub_752BC4 != 0
- `Caption := calculado` em `0x4B9139` (ButtonNuoviParametriClick) — SE sub_752BC4 == 0
- `Visible := calculado` em `0x4B8FF7` (CheckTempMotoreClick) — incondicional

### `DrawGridTemp` — DrawGridTemp

Tipo: **grade** (`TDrawGrid`), DFM linha 4944 `[OBS-COD]`. Pai: `TabTemp`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `None` (associacao None)): ita = "-"; eng = "-"; por = "-" `[DESCONHECIDO]`.
- Posicao no DFM: Left=67 Top=342 Largura=501 Altura=132; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Tabela temperatura x tensao do sensor do carro; as tensoes devem sempre diminuir quando a temperatura sobe. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.23).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `DrawGridTempKeyPress` (0x4B80B0) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x43041C` em `0x4B80F3` (le: ECU_TEMP; escreve: -)
- `OnEnter` -> `DrawGridTempEnter` (0x4B868C) `[OBS-COD]`
  - muda Visible de um controle nao identificado pela analise estatica em `0x4B869D`
- `OnExit` -> `DrawGridTempExit` (0x4B86A4) `[OBS-COD]`
  - muda Visible de `LabelInstr` em `0x4B86BD`
- `OnDblClick` -> `DrawGridTempDblClick` (0x4B81F4) `[OBS-COD]`
- `OnKeyDown` -> `DrawGridKKeyDown` (0x4B2514) `[OBS-COD]`
- `OnDrawCell` -> `DrawGridTempDrawCell` (0x4B7874) `[OBS-COD]`: desenho das celulas da grade (exibicao); nao envia nada a ECU

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Grade: cada celula editada chama um setter indexado (SetIdx) do handler `KeyPress`/`DblClick`; quadros por celula: `14 SC lo hi idx valor ck` (SetNumber+indice). Quadros exatos por celula: **DESCONHECIDO** (nao executados aqui).

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4B8FE3` (CheckTempMotoreClick) — incondicional

### `CheckFlashLubeSuTempAcqua` — Diagnosis on the water temperature sensor for VP system

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 4973 `[OBS-COD]`. Pai: `TabTemp`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Diagnosis on the water temperature sensor for VP system"; eng = "Diagnosis on the water temperature sensor for VP system"; por = "Diagnosis on the water temperature sensor for VP system" `[OBS-COD]`.
- Posicao no DFM: Left=38 Top=123 Largura=750 Altura=34; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Diagnostico no sensor de temperatura da agua para o sistema VP. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF3** (SerialCode 250), palavra 0, mascara 0x0080, caso do setter 5, papel "caixa (Checked)", condicao "sempre"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF3 palavra 0 = 0x0000 -> bit 0x0080: **desligado** `[OBS-FIO]` (LOG linha 1102872).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckFlashLubeSuTempAcquaClick` (0x4B8C98) `[OBS-COD]`
  - SetFlag3 caso 5 -> FLAG_CONF3 palavra 0 mascara 0x0080 em `0x4B8CC8`
  - chama o metodo do TStreamDati `0x431878` em `0x4B8CF0` (le: STATO_DIAGNOSI, STATO_DIAGNOSI_LR; escreve: -)
  - chama o metodo do TStreamDati `0x4318F8` em `0x4B8D04` (le: ABIL_DIAGNOSI, ABIL_DIAGNOSI_LR, STATO_DIAGNOSI, STATO_DIAGNOSI_LR; escreve: -)
  - chama o metodo do TStreamDati `0x431A40` em `0x4B8D13` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x431A5C` em `0x4B8D24` (le: ACTION_DIAGNOSI, ACTION_DIAGNOSI_LR; escreve: -)
  - chama o metodo do TStreamDati `0x431A40` em `0x4B8D36` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x431A5C` em `0x4B8D4A` (le: ACTION_DIAGNOSI, ACTION_DIAGNOSI_LR; escreve: -)
  - chama o metodo do TStreamDati `0x431A40` em `0x4B8D5C` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x431A5C` em `0x4B8D70` (le: ACTION_DIAGNOSI, ACTION_DIAGNOSI_LR; escreve: -)
  - chama o metodo do TStreamDati `0x431878` em `0x4B8D87` (le: STATO_DIAGNOSI, STATO_DIAGNOSI_LR; escreve: -)
  - chama o metodo do TStreamDati `0x4318F8` em `0x4B8D9B` (le: ABIL_DIAGNOSI, ABIL_DIAGNOSI_LR, STATO_DIAGNOSI, STATO_DIAGNOSI_LR; escreve: -)
  - chama o metodo do TStreamDati `0x431A40` em `0x4B8DAA` (le: -; escreve: -)

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF3 palavra 0 mascara 0x0080): 0000 0000 -> 0080 0000, quadro `35 FA 00 80 00 00 00 AF`.
  - desligar bit (FLAG_CONF3 palavra 0 mascara 0x0080): 0000 0000 -> 0000 0000, quadro `35 FA 00 00 00 00 00 2F`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetFlag3(caso 5)` em `0x4B934B` (TabTempShow) — incondicional
- `Visible := verdadeiro` em `0x4B93A0` (TabTempShow) — incondicional
- `Checked/ItemIndex := calculado por GetFlag3(caso 5)` em `0x49066A` (FormShow; metodo de OUTRA aba/tela) — incondicional

## Rotulos com regra de mostrar/esconder ou texto dinamico

### `LabelParametriTemp` — "Parâmetros para o sensor de temperatura do motor"

Texto: ita "Parametri per il sensore di temperatura motore della vettura" / eng "Engine temperature sensor parameters" / por "Parâmetros para o sensor de temperatura do motor" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado por chamada virtual dword ptr [edx + 0x104]` em `0x4B8ECB` (CheckTempMotoreClick) — incondicional

### `LabelRPullUp` — "R pull-up"

Texto: ita "R pull-up" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado por chamada virtual dword ptr [edx + 0x104]` em `0x4B8EDF` (CheckTempMotoreClick) — incondicional

### `LabelDeltaT` — "Delta T"

Texto: ita "Delta T" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4B8F1B` (CheckTempMotoreClick) — incondicional

### `LabRPullUp` — "ohm"

Texto: ita "ohm" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4B8F07` (CheckTempMotoreClick) — incondicional

### `LabDeltaT` — "°C"

Texto: ita "°C" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4B8F43` (CheckTempMotoreClick) — incondicional

### `LabelTMin` — "T min"

Texto: ita "T min" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4B8F57` (CheckTempMotoreClick) — incondicional

### `LabelTMax` — "T max"

Texto: ita "T max" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4B8F93` (CheckTempMotoreClick) — incondicional

### `LabTMin` — "°C"

Texto: ita "°C" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4B8F7F` (CheckTempMotoreClick) — incondicional

### `LabTMax` — "°C"

Texto: ita "°C" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4B8FBB` (CheckTempMotoreClick) — incondicional

### `LabelCaratteristicaTemp` — "Tabela do sensor de temperatura do motor"

Texto: ita "Caratteristica sensore di temperatura motore della vettura" / eng "Engine temperature sensor characterisics" / por "Tabela do sensor de temperatura do motor" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4B8FCF` (CheckTempMotoreClick) — incondicional

### `LabelInstr` — "VOLTAR: introduza valor ESPAÇO: aquisição do valor"

Texto: ita "INVIO: inserimento valore   SPAZIO: apprendimento valore " / eng "RETURN: insert value SPACE: acquire value" / por "VOLTAR: introduza valor ESPAÇO: aquisição do valor" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := falso` em `0x4B86BD` (DrawGridTempExit) — incondicional

## Lacunas desta aba

- Edits sem codigo de dado na rotina 0x48A228 (o valor passa por metodo proprio do TStreamDati; formula nao decodificada aqui, `DESCONHECIDO`): `EditDeltaT`, `EditRPullUp`, `EditTMax`, `EditTMin`.
- Combos cujo valor e gravado por metodo proprio (sem bit nem codigo generico): `ComboTempRiduttore`. Os vetores tocados aparecem na lista de acessos do handler; o significado de cada opcao no fio e `DESCONHECIDO` quando nao ha formula.
- Grades (`TDrawGrid`): o conteudo e desenhado em `OnDrawCell`; o conteudo e os quadros por celula nao foram decodificados celula a celula: `DrawGridTemp`.
- Visibilidade/habilitacao: as regras listadas sao as que o codigo escreve; a combinacao final depende de dados da ECU (modelo, familia LR, MASK_FUNCTION) que o LOG so mostra em parte. O dono usa familia nao-LR (INFERIDO: o original leu SC 192, nunca 193).
- A associacao rotulo<->campo e por nome/geometria (`INFERIDO`), e a chave da dica (HINT...) segue o nome do componente (`INFERIDO`).

