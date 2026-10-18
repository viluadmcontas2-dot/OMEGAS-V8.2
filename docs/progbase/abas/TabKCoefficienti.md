# TabKCoefficienti — Coefficienti k

Nome na tela: ita "Coefficienti k" / eng "None" / por "None" `[OBS-COD]`. Aba 7 de 16 do grupo D3a. Rotina de abertura (OnShow): `TabKCoefficientiShow` em `0x4B0EA0` `[OBS-COD]`. TabVisible de projeto: sim.

Legenda das marcas: `OBS-COD` = lido do DFM/codigo do ProgBase.exe (ou executado no emulador Unicorn); `OBS-FIO` = visto no Portmon da ECU do dono; `DERIVADO` = calculado a partir dessas observacoes; `INFERIDO` = deducao; `DESCONHECIDO` = sem prova.

**Resumo**: 32 controles no DFM (TBevel=8, TButton=3, TDrawGrid=1, TLabel=12, TPageControl=1, TPanel=2, TRadioButton=3, TShape=1, TStaticText=1); 7 interativos; 0 com codigo de dado e formula; 1 com caixa/bit; 3 com valor lido no LOG. Fonte do .exe sha256 `8a2d297c8c21...`.

## Como o ProgBase trata estes controles (valido para a aba inteira)

- **Campo numerico (`TNumericEdit`)** `[OBS-COD]`: ao entrar no campo, `EditGiriCambioEnter` (0x4A98D8) mostra no rotulo `LabelLimite` o texto `(minimo;maximo)` do proprio campo; ao sair, `EditGiriCambioExit` (0x4A9CC0) esconde esse rotulo. Nada e enviado a ECU nesses dois momentos. Ao apertar **Enter**, o `TNumericEdit` limita o numero a [minimo;maximo] (so se minimo!=0 ou maximo!=0; 0x96C170) e o handler generico `EditRifLambdaKeyPress` (0x4A6A00) chama `TStreamDati.Set*` com o *codigo de dado* do campo (campo `+0x2EC`, indice `+0x2F0`; amarrados em 0x48A228): o valor e convertido pela formula do caso, gravado no cache do programa e enviado como SetNumber (escalar) ou SetNumber+indice (vetor). Depois o handler le o valor de volta do cache (`Get*`) e redesenha o campo arredondado ao passo (`Precision`, 0x96BB88). Nao ha releitura da ECU.
- **Caixa de marcar (`TCheckBox`)** `[OBS-COD]`: apertar Enter simula um clique do mouse (`CheckTpsBlocKeyPress` 0x4A8928 envia WM_LBUTTONDOWN/UP); o clique chama o handler `Check...Click`, que liga/desliga **um bit** do vetor FLAG_CONF1/2/3 (o programa regrava o vetor inteiro, ver quadros abaixo) e atualiza o que depende da caixa.
- **Lista (`TKeyComboBox`)** `[OBS-COD]`: Enter no combo chama o `...KeyPress` correspondente, que grava o indice escolhido (por codigo de dado `+0x330` ou por bit de FLAG_CONF).
- **Resposta da ECU a toda gravacao** `[OBS-FIO]`: a porta devolve o eco do quadro e a ECU responde `53 00 53` (LOG linhas 48568-48600 e 31284-31300). Em seguida o programa limpa os buffers e retoma a telemetria (`48 01 49`).
- **Conexao**: os valores 'atuais' vem de leituras `09`/`0A`/`29` feitas ao conectar (LOG de 12/08/2026); a tela do programa mostra o cache dessas leituras.

## Mapa de todos os controles (ordem do DFM)

| # | Componente | Tipo | Rotulo (por / eng / ita) | Unidade | Vetor (SC) | Bit | Visivel no DFM |
|---|---|---|---|---|---|---|---|
| 1 | `LabelTinjBenzPerc` | rotulo | - / - / 0,0% | - | - | - | sim |
| 2 | `Label0` | rotulo | - / - / 0% | - | - | - | sim |
| 3 | `LabelPiu30` | rotulo | - / - / 30% | - | - | - | sim |
| 4 | `LabelMeno30` | rotulo | - / - / -30% | - | - | - | sim |
| 5 | `PanelTinjBenz` | painel | - / - / - | - | - | - | sim |
| 6 | `ShapeTinjBenz` | forma | - / - / - | - | - | - | sim |
| 7 | `Bevel0` | moldura | - / - / - | - | - | - | sim |
| 8 | `BevelMeno10` | moldura | - / - / - | - | - | - | sim |
| 9 | `Bevel20` | moldura | - / - / - | - | - | - | sim |
| 10 | `Bevel10` | moldura | - / - / - | - | - | - | sim |
| 11 | `BevelMeno20` | moldura | - / - / - | - | - | - | sim |
| 12 | `DrawGridK` | grade | - / - / - | - | - | - | sim |
| 13 | `ButtonTaratura` | botao | - / - / &Inizia taratura | - | - | - | sim |
| 14 | `ButtonModificaRifTar` | botao | &Modificar ref. mapa / &Modify map refs. / &Mod. rif. mappa | - | - | - | False |
| 15 | `StaticTaratura` | texto | Mod.calibragem / Calibration mod. / Mod. taratura | - | - | - | False |
| 16 | `ButtonCommutatore` | botao | &Comutador / &Switch / &Commutatore | - | - | - | False |
| 17 | `PageControlK` | abas | - / - / - | - | - | - | False |
| 18 | `RadioMapPetrol` | radio | - / - / petrol | - | - | - | False |
| 19 | `RadioMapAlcol` | radio | - / - / alcohol | - | - | FLAG_CONF2[1]&0x0100 | False |
| 20 | `PanelObdData` | painel | - / - / - | - | - | - | False |
| 21 | `LabelObdParam0` | rotulo | - / - / OBD param 0 | - | - | - | False |
| 22 | `LabObdParam0` | rotulo | - / - / n.d. | - | - | - | False |
| 23 | `BevelObd1` | moldura | - / - / OBD param 1 | - | - | - | sim |
| 24 | `LabelObdParam1` | rotulo | - / - / OBD param 1 | - | - | - | False |
| 25 | `LabObdParam1` | rotulo | - / - / n.d. | - | - | - | False |
| 26 | `BevelObd2` | moldura | - / - / OBD param 2 | - | - | - | sim |
| 27 | `LabelObdParam2` | rotulo | - / - / OBD param 2 | - | - | - | False |
| 28 | `LabObdParam2` | rotulo | - / - / n.d. | - | - | - | False |
| 29 | `BevelObd3` | moldura | - / - / OBD param 3 | - | - | - | sim |
| 30 | `LabelObdParam3` | rotulo | - / - / OBD param 3 | - | - | - | False |
| 31 | `LabObdParam3` | rotulo | - / - / n.d. | - | - | - | False |
| 32 | `RadioMapAdatta` | radio | - / - / target OBD | - | - | - | False |

## Controles interativos, um a um

### `DrawGridK` — DrawGridK

Tipo: **grade** (`TDrawGrid`), DFM linha 5110 `[OBS-COD]`. Pai: `TabKCoefficienti`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `None` (associacao None)): ita = "-"; eng = "-"; por = "-" `[DESCONHECIDO]`.
- Posicao no DFM: Left=14 Top=10 Largura=628 Altura=342; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTDRAWGRIDK`: "This table represents the main carburation map. \ The factor (named with the letter K) is given for each combination of values of engine revs-petrol injection time \ which put inside the calculation chain allows the GAS injection times to be determined. \ It is possible to change each parameter inside the table by double-clicking the relevant box.".

**O que isso faz (linguagem simples)**

- Mapa principal de carburacao: rotacao na horizontal, tempo de injecao de gasolina na vertical e o coeficiente K em cada celula. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.24).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `DrawGridKKeyPress` (0x4B18F4) `[OBS-COD]`
- `OnDblClick` -> `DrawGridKDblClick` (0x4B1940) `[OBS-COD]`
- `OnKeyDown` -> `DrawGridKKeyDown` (0x4B2514) `[OBS-COD]`
- `OnDrawCell` -> `DrawGridKDrawCell` (0x4B12EC) `[OBS-COD]`: desenho das celulas da grade (exibicao); nao envia nada a ECU

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Grade: cada celula editada chama um setter indexado (SetIdx) do handler `KeyPress`/`DblClick`; quadros por celula: `14 SC lo hi idx valor ck` (SetNumber+indice). Quadros exatos por celula: **DESCONHECIDO** (nao executados aqui).

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `ButtonTaratura` — &Inizia taratura

Tipo: **botao** (`TButton`), DFM linha 5135 `[OBS-COD]`. Pai: `TabKCoefficienti`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "&Inizia taratura"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=290 Top=362 Largura=186 Altura=32; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTBUTTONTARATURA`: "Start calibration".

**O que isso faz (linguagem simples)**

- Inicia a calibracao manual ponto a ponto do mapa K. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.25).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 0, mascara 0x0800, caso do setter 4, papel "None", condicao "sempre"; origem: handler ButtonTaraturaClick.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 0, mascara 0x1000, caso do setter 5, papel "None", condicao "sempre"; origem: handler ButtonTaraturaClick.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 0 = 0x2486 -> bit 0x0800: **desligado** `[OBS-FIO]` (LOG linha 1102844).
- FLAG_CONF1 palavra 0 = 0x2486 -> bit 0x1000: **desligado** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `ButtonTaraturaClick` (0x4ACFC0) `[OBS-COD]`
  - GetFlag1 caso 4 -> FLAG_CONF1 palavra 0 mascara 0x0800 em `0x4ACFCF`
  - SetFlag1 caso 4 -> FLAG_CONF1 palavra 0 mascara 0x0800 em `0x4ACFE7`
  - muda Caption de `ButtonTaratura` em `0x4ACFF8`
  - muda Visible de `StaticTaratura` em `0x4AD005`
  - muda Visible de `ButtonCommutatore` em `0x4AD012`
  - SetFlag1 caso 5 -> FLAG_CONF1 palavra 0 mascara 0x1000 em `0x4AD031`
  - SetFlag1 caso 5 -> FLAG_CONF1 palavra 0 mascara 0x1000 em `0x4AD047`
  - SetFlag1 caso 4 -> FLAG_CONF1 palavra 0 mascara 0x0800 em `0x4AD05A`
  - muda Caption de `ButtonTaratura` em `0x4AD06B`
  - muda Visible de `StaticTaratura` em `0x4AD07F`
  - muda Visible de `ButtonCommutatore` em `0x4AD093`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 0 mascara 0x0800): 2486 1051 -> 2C86 1051, quadro `35 03 00 86 2C 51 10 4B`.
  - desligar bit (FLAG_CONF1 palavra 0 mascara 0x0800): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - ligar bit (FLAG_CONF1 palavra 0 mascara 0x1000): 2486 1051 -> 3486 1051, quadro `35 03 00 86 34 51 10 53`.
  - desligar bit (FLAG_CONF1 palavra 0 mascara 0x1000): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Caption := calculado` em `0x4ACFF8` (ButtonTaraturaClick) — SE GetFlag1(caso 4) != 0
- `Caption := calculado` em `0x4AD06B` (ButtonTaraturaClick) — SE GetFlag1(caso 4) == 0
- `Caption := calculado` em `0x4B1047` (TabKCoefficientiShow) — incondicional
- `Caption := calculado` em `0x4B2A85` (DrawGridOpenLoopDblClick, DrawGridOpenLoopDblClick via sub_4B2A44; metodo de OUTRA aba/tela) — SE GetFlag1(caso 4) == 0 E cmp dword ptr [esp], 0 >  E cmp dword ptr [esp + 4], 0 > 
- `Caption := calculado` em `0x4B2A85` (DrawGridOpenLoopKeyPress, DrawGridOpenLoopKeyPress via sub_4B2A44; metodo de OUTRA aba/tela) — SE GetFlag1(caso 4) == 0 E tecla == Enter

### `ButtonModificaRifTar` — &Modificar ref. mapa

Tipo: **botao** (`TButton`), DFM linha 5153 `[OBS-COD]`. Pai: `TabKCoefficienti`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "&Mod. rif. mappa"; eng = "&Modify map refs."; por = "&Modificar ref. mapa" `[OBS-COD]`.
- Posicao no DFM: Left=14 Top=362 Largura=270 Altura=32; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTBUTTONMODIFICARIFTAR`: "Modify map references".

**O que isso faz (linguagem simples)**

- Muda os pontos de referencia do mapa (rotacao 200 a 8000 rpm; tempos de injecao 0 a 40 ms); faca antes de calibrar. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.24).
- Manual SW 2.16.4 C, p.24 (linha 1232 de sw.txt): "B Modify map refs.: the following mapping break points can be modified: - RPM (range: 200 - 8000) - Injection times (range: 0 - 40 ms)" `[OBS-COD]`.

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `ButtonModificaRifTarClick` (0x4B10C8) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Botao: sem escrita de valor padrao. O que o handler faz esta na lista de acessos acima; bytes especificos so quando o handler chama `EnviaComando`/setter `[OBS-COD]` (nao executado aqui) -> **DESCONHECIDO** o quadro exato quando nao listado.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `ButtonCommutatore` — &Comutador

Tipo: **botao** (`TButton`), DFM linha 5192 `[OBS-COD]`. Pai: `TabKCoefficienti`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "&Commutatore"; eng = "&Switch"; por = "&Comutador" `[OBS-COD]`.
- Posicao no DFM: Left=654 Top=362 Largura=173 Altura=32; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTBUTTONCOMMUTATORE`: "Switch".

**O que isso faz (linguagem simples)**

- Alterna o comutador gas/gasolina pelo programa. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 0, mascara 0x1000, caso do setter 5, papel "None", condicao "sempre"; origem: handler ButtonCommutatoreClick.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 0 = 0x2486 -> bit 0x1000: **desligado** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `ButtonCommutatoreClick` (0x4B1178) `[OBS-COD]`
  - GetFlag1 caso 5 -> FLAG_CONF1 palavra 0 mascara 0x1000 em `0x4B1187`
  - SetFlag1 caso 5 -> FLAG_CONF1 palavra 0 mascara 0x1000 em `0x4B119E`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 0 mascara 0x1000): 2486 1051 -> 3486 1051, quadro `35 03 00 86 34 51 10 53`.
  - desligar bit (FLAG_CONF1 palavra 0 mascara 0x1000): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := falso` em `0x4AD012` (ButtonTaraturaClick) — SE GetFlag1(caso 4) != 0
- `Visible := calculado` em `0x4AD093` (ButtonTaraturaClick) — SE GetFlag1(caso 4) == 0
- `Visible := calculado` em `0x4B2AAD` (DrawGridOpenLoopDblClick, DrawGridOpenLoopDblClick via sub_4B2A44; metodo de OUTRA aba/tela) — SE GetFlag1(caso 4) == 0 E cmp dword ptr [esp], 0 >  E cmp dword ptr [esp + 4], 0 > 
- `Visible := calculado` em `0x4B2AAD` (DrawGridOpenLoopKeyPress, DrawGridOpenLoopKeyPress via sub_4B2A44; metodo de OUTRA aba/tela) — SE GetFlag1(caso 4) == 0 E tecla == Enter

### `RadioMapPetrol` — petrol

Tipo: **radio** (`TRadioButton`), DFM linha 6255 `[OBS-COD]`. Pai: `TabKCoefficienti`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "petrol"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=650 Top=37 Largura=169 Altura=27; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Mostra/edita o mapa K do combustivel gasolina. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `RadioMapClick` (0x4B23A0) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x42F338` em `0x4B23C2` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x42F340` em `0x4B23ED` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x42F338` em `0x4B241C` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x42F340` em `0x4B2447` (le: -; escreve: -)

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := verdadeiro` em `0x4B0F01` (TabKCoefficientiShow) — incondicional
- `Checked/ItemIndex := calculado` em `0x4B0F1D` (TabKCoefficientiShow) — SE [obj+0x1794] == 0
- `Checked/ItemIndex := calculado` em `0x4B0F60` (TabKCoefficientiShow) — SE sub_4347F8 != 0 E (byte[obj+0x1700] & 0x20) == 0
- `Checked/ItemIndex := calculado` em `0x4B0F72` (TabKCoefficientiShow) — SE (byte[obj+0x1700] & 0x20) == 0 E sub_4347F8 == 0

### `RadioMapAlcol` — alcohol

Tipo: **radio** (`TRadioButton`), DFM linha 6276 `[OBS-COD]`. Pai: `TabKCoefficienti`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "alcohol"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=650 Top=64 Largura=169 Altura=27; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Mostra/edita o mapa K do combustivel alcool. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF2** (SerialCode 192), palavra 1, mascara 0x0100, caso do setter 16, papel "dependente (Visible)", condicao "sempre"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF2 palavra 1 = 0x0000 -> bit 0x0100: **desligado** `[OBS-FIO]` (LOG linha 1102816).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `RadioMapClick` (0x4B23A0) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x42F338` em `0x4B23C2` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x42F340` em `0x4B23ED` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x42F338` em `0x4B241C` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x42F340` em `0x4B2447` (le: -; escreve: -)

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF2 palavra 1 mascara 0x0100): 0001 0000 -> 0001 0100, quadro `35 C0 00 01 00 00 01 F7`.
  - desligar bit (FLAG_CONF2 palavra 1 mascara 0x0100): 0001 0000 -> 0001 0000, quadro `35 C0 00 01 00 00 00 F6`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado por GetFlag2(caso 16)` em `0x4B0ED5` (TabKCoefficientiShow) — incondicional
- `Checked/ItemIndex := calculado` em `0x4B0F4E` (TabKCoefficientiShow) — SE sub_4347F8 != 0 E (byte[obj+0x1838] & 0x10) == 0 E (byte[obj+0x1700] & 0x20) != 0

### `RadioMapAdatta` — target OBD

Tipo: **radio** (`TRadioButton`), DFM linha 6467 `[OBS-COD]`. Pai: `TabKCoefficienti`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "target OBD"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=650 Top=10 Largura=169 Altura=27; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Mostra/edita o mapa K usado como alvo da adaptatividade OBD. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `RadioMapClick` (0x4B23A0) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x42F338` em `0x4B23C2` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x42F340` em `0x4B23ED` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x42F338` em `0x4B241C` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x42F340` em `0x4B2447` (le: -; escreve: -)

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4B0EBA` (TabKCoefficientiShow) — incondicional

## Rotulos com regra de mostrar/esconder ou texto dinamico

### `LabelTinjBenzPerc` — "0,0%"

Texto: ita "0,0%" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=sim.
- `Caption := calculado` em `0x4A1D6B` (TimerDatiTimer, TimerDatiTimer via sub_4A1AEC; metodo de OUTRA aba/tela) — SE test ah, 0x41 ?  E test ah, 5 ==  E remetente(ecx) == [form+0x46c](TabKCoefficienti)
- `Caption := calculado` em `0x4A1E06` (TimerDatiTimer, TimerDatiTimer via sub_4A1AEC; metodo de OUTRA aba/tela) — SE test ah, 5 !=  E test ah, 0x45 ==  E remetente(ecx) == [form+0x46c](TabKCoefficienti)
- `Caption := calculado` em `0x4A1E76` (TimerDatiTimer, TimerDatiTimer via sub_4A1AEC; metodo de OUTRA aba/tela) — SE test ah, 5 !=  E test ah, 0x45 !=  E remetente(ecx) == [form+0x46c](TabKCoefficienti)

### `Label0` — "0%"

Texto: ita "0%" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=sim.
- `Visible := falso` em `0x4A56C0` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration) E remetente(eax) == [form+0x91c](TabAdatta) E sub_4349CC != 0 E sub_434568 != 0
- `Visible := calculado` em `0x4A591F` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration) E remetente(eax) == [form+0x91c](TabAdatta) E sub_4349CC != 0 E sub_434568 == 0
- `Visible := calculado` em `0x4A5962` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration) E remetente(eax) == [form+0x91c](TabAdatta) E sub_434568 == 0 E sub_4349CC == 0

### `LabelPiu30` — "30%"

Texto: ita "30%" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=sim.
- `Visible := falso` em `0x4A56A6` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration) E remetente(eax) == [form+0x91c](TabAdatta) E sub_4349CC != 0 E sub_434568 != 0
- `Visible := calculado` em `0x4A5905` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration) E remetente(eax) == [form+0x91c](TabAdatta) E sub_4349CC != 0 E sub_434568 == 0
- `Visible := calculado` em `0x4A5948` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration) E remetente(eax) == [form+0x91c](TabAdatta) E sub_434568 == 0 E sub_4349CC == 0

### `LabelMeno30` — "-30%"

Texto: ita "-30%" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=sim.
- `Visible := falso` em `0x4A56B3` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration) E remetente(eax) == [form+0x91c](TabAdatta) E sub_4349CC != 0 E sub_434568 != 0
- `Visible := calculado` em `0x4A5912` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration) E remetente(eax) == [form+0x91c](TabAdatta) E sub_4349CC != 0 E sub_434568 == 0
- `Visible := calculado` em `0x4A5955` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration) E remetente(eax) == [form+0x91c](TabAdatta) E sub_434568 == 0 E sub_4349CC == 0

## Lacunas desta aba

- Grades (`TDrawGrid`): o conteudo e desenhado em `OnDrawCell`; o conteudo e os quadros por celula nao foram decodificados celula a celula: `DrawGridK`.
- Visibilidade/habilitacao: as regras listadas sao as que o codigo escreve; a combinacao final depende de dados da ECU (modelo, familia LR, MASK_FUNCTION) que o LOG so mostra em parte. O dono usa familia nao-LR (INFERIDO: o original leu SC 192, nunca 193).
- A associacao rotulo<->campo e por nome/geometria (`INFERIDO`), e a chave da dica (HINT...) segue o nome do componente (`INFERIDO`).

