# TabCambio — Mudança do gás

Nome na tela: ita "Cambio GAS" / eng "Gas change-over" / por "Mudança do gás" `[OBS-COD]`. Aba 1 de 16 do grupo D3a. Rotina de abertura (OnShow): `TabCambioShow` em `0x4A6ACC` `[OBS-COD]`. TabVisible de projeto: sim.

Legenda das marcas: `OBS-COD` = lido do DFM/codigo do ProgBase.exe (ou executado no emulador Unicorn); `OBS-FIO` = visto no Portmon da ECU do dono; `DERIVADO` = calculado a partir dessas observacoes; `INFERIDO` = deducao; `DESCONHECIDO` = sem prova.

**Resumo**: 106 controles no DFM (TButton=3, TCheckBox=14, TDrawGrid=4, TKeyComboBox=9, TLabel=50, TNumericEdit=17, TPanel=3, TScrollBox=1, TStaticText=1, TValueEditEx=4); 51 interativos; 11 com codigo de dado e formula; 16 com caixa/bit; 33 com valor lido no LOG. Fonte do .exe sha256 `8a2d297c8c21...`.

## Como o ProgBase trata estes controles (valido para a aba inteira)

- **Campo numerico (`TNumericEdit`)** `[OBS-COD]`: ao entrar no campo, `EditGiriCambioEnter` (0x4A98D8) mostra no rotulo `LabelLimite` o texto `(minimo;maximo)` do proprio campo; ao sair, `EditGiriCambioExit` (0x4A9CC0) esconde esse rotulo. Nada e enviado a ECU nesses dois momentos. Ao apertar **Enter**, o `TNumericEdit` limita o numero a [minimo;maximo] (so se minimo!=0 ou maximo!=0; 0x96C170) e o handler generico `EditRifLambdaKeyPress` (0x4A6A00) chama `TStreamDati.Set*` com o *codigo de dado* do campo (campo `+0x2EC`, indice `+0x2F0`; amarrados em 0x48A228): o valor e convertido pela formula do caso, gravado no cache do programa e enviado como SetNumber (escalar) ou SetNumber+indice (vetor). Depois o handler le o valor de volta do cache (`Get*`) e redesenha o campo arredondado ao passo (`Precision`, 0x96BB88). Nao ha releitura da ECU.
- **Caixa de marcar (`TCheckBox`)** `[OBS-COD]`: apertar Enter simula um clique do mouse (`CheckTpsBlocKeyPress` 0x4A8928 envia WM_LBUTTONDOWN/UP); o clique chama o handler `Check...Click`, que liga/desliga **um bit** do vetor FLAG_CONF1/2/3 (o programa regrava o vetor inteiro, ver quadros abaixo) e atualiza o que depende da caixa.
- **Lista (`TKeyComboBox`)** `[OBS-COD]`: Enter no combo chama o `...KeyPress` correspondente, que grava o indice escolhido (por codigo de dado `+0x330` ou por bit de FLAG_CONF).
- **Resposta da ECU a toda gravacao** `[OBS-FIO]`: a porta devolve o eco do quadro e a ECU responde `53 00 53` (LOG linhas 48568-48600 e 31284-31300). Em seguida o programa limpa os buffers e retoma a telemetria (`48 01 49`).
- **Conexao**: os valores 'atuais' vem de leituras `09`/`0A`/`29` feitas ao conectar (LOG de 12/08/2026); a tela do programa mostra o cache dessas leituras.

## Mapa de todos os controles (ordem do DFM)

| # | Componente | Tipo | Rotulo (por / eng / ita) | Unidade | Vetor (SC) | Bit | Visivel no DFM |
|---|---|---|---|---|---|---|---|
| 1 | `ScrollBoxCambio` | rolagem | - / - / - | - | - | - | sim |
| 2 | `LabelRitardoCambioGas` | rotulo | Atraso na reversão gasolina-gás com temp. Gás / Delay in petrol-gas switch over with gas temp. / Ritardo passaggio benzina-gas con temp. gas | - | - | - | False |
| 3 | `LabelSovrapposizione` | rotulo | Tempo de sobreposição / Overlapping time / Tempo di sovrapposizione | - | - | - | False |
| 4 | `LabelTipoIniettore` | rotulo | Tipo de injetor / Injector type / Tipo iniettore | - | - | - | sim |
| 5 | `LabelTurbo` | rotulo | - / - / Motore | - | - | - | False |
| 6 | `LabelT2` | rotulo | - / - / °C | - | - | - | False |
| 7 | `LabelRpm1` | rotulo | - / - / rpm | - | - | - | sim |
| 8 | `LabelT1` | rotulo | - / - / °C | - | - | - | False |
| 9 | `LabelS1` | rotulo | - / - / s | - | - | - | False |
| 10 | `LabelS15` | rotulo | - / - / s | - | - | - | False |
| 11 | `LabelMs6` | rotulo | - / - / ms | - | - | - | False |
| 12 | `LabelNota` | rotulo | Atenção !!! Os parâmetros em vermelho podem ser modificados somente com sub-chave desligada. / Warning !!! Parameters in red can only be changed with key of-ignition off. / Attenzione! Modificare i parametri in rosso solo con sottochiave disinserito e commutatore spento. | - | - | - | sim |
| 13 | `LabelTInjBenzMaxPerCambio` | rotulo | T.inj gasolina máximo para mudança gás / Max petrol inj. time for gas change-over / Tinj benzina massimo per cambio gas | - | - | - | False |
| 14 | `LabelRitardoCambio` | rotulo | Atraso na passagem gasolina/gás / Switch-over from petrol to gas delay / Ritardo passaggio benzina-gas | - | - | - | False |
| 15 | `LabelTempCambio` | rotulo | Temperatura do gás para o cambio / Gas temperature for change-over / Temperatura gas per il cambio | - | - | FLAG_CONF1[1]&0x0002 | False |
| 16 | `LabelTempMotore` | rotulo | Temperatura da água para o cambio / Water temperature for change-over / Temperatura acqua per il cambio | - | - | - | False |
| 17 | `LabelGiriCambio` | rotulo | Nº de rotações para a mudança / Revs. threshold for change-over / Soglia giri per il cambio | - | - | - | sim |
| 18 | `LabelCambio` | rotulo | Tipo de mudança / Type of change-over / Tipo di cambio | - | - | - | sim |
| 19 | `LabelAccensione` | rotulo | Número de cilindros / No. of cylinders / Tipo di accensione | - | - | - | False |
| 20 | `LabelCilindri` | rotulo | - / - / Numero cilindri | - | - | - | False |
| 21 | `LabelSegnaleGiri` | rotulo | Tipo de sinal rotações / Type of rev signal / Tipo segnale giri | - | - | - | sim |
| 22 | `LabelCilindrata` | rotulo | - / - / Cilindrata (cc) | - | - | - | False |
| 23 | `LabelCarburante` | rotulo | - / - / Tipo carburante | - | - | - | False |
| 24 | `LabelPassaggioGradi` | rotulo | (°C) / (°C) / (°C) | - | - | - | False |
| 25 | `LabelPassaggioSecondi` | rotulo | (seg) / (seconds) / (sec) | - | - | - | False |
| 26 | `LabelPassaggioGasGradi` | rotulo | - / - / (°C) | - | - | - | False |
| 27 | `LabelPassaggioGasSecondi` | rotulo | - / - / (sec) | - | - | - | False |
| 28 | `LabelTaglia` | rotulo | - / - / Taglia | - | - | - | False |
| 29 | `LabelT4` | rotulo | - / - / °C | - | - | - | False |
| 30 | `LabelCicchetto` | rotulo | - / - / Tempo di cicchetto | - | - | - | False |
| 31 | `LabelSogliaGiri` | rotulo | - / - / Soglia | - | - | - | False |
| 32 | `LabSogliaGiri` | rotulo | - / - / V | - | - | - | False |
| 33 | `LabelIstantaneoValue` | rotulo | - / - / Num. iniettate tra cilindri per cambio gas | - | - | - | sim |
| 34 | `LabelCicchettoRpm` | rotulo | RPM threshold for crank / RPM threshold for crank / Soglia giri per crank | - | - | - | False |
| 35 | `LabCicchettoRpm` | rotulo | - / - / rpm | - | - | - | False |
| 36 | `LabelCicchettoInj` | rotulo | Gas injection time in priming / Gas injection time in priming / Tempo di iniezione gas in cicchetto | - | - | - | False |
| 37 | `LabCicchettoInj` | rotulo | - / - / ms | - | - | - | False |
| 38 | `LabelCicchettoNum` | rotulo | Number of gas injections in priming / Number of gas injections in priming / Numero iniettate gas in cicchetto | - | - | - | False |
| 39 | `LabelNumDenti` | rotulo | - / - / n. denti | - | - | - | False |
| 40 | `LabelFullGroup` | rotulo | Full group / Full group / Inj. | - | - | - | sim |
| 41 | `LabelLetturaGiri` | rotulo | - / - / Lettura giri: | - | - | - | False |
| 42 | `DrawDelayGasTemp` | grade | - / - / - | - | - | - | False |
| 43 | `EditSovrapposizione` | edit numerico | Tempo de sobreposição / Overlapping time / Tempo di sovrapposizione | s | TEMPO_SOVRAPPOSIZIONE (109) | - | False |
| 44 | `EditCicchetto` | edit numerico | - / - / Tempo di cicchetto | s | TEMPO_CICCHETTO (110), TEMPO_CICCHETTO_LR (31) | - | False |
| 45 | `EditCicchettoRpm` | edit numerico | RPM threshold for crank / RPM threshold for crank / Soglia giri per crank | rpm | - | - | False |
| 46 | `CheckSegnaleGiri` | caixa de marcar | - / - / Tipo segnale giri | - | - | - | False |
| 47 | `CheckDisabilitaCommutatore` | caixa de marcar | - / - / Disabilita commutatore | - | - | FLAG_CONF2[0]&0x0020 | sim |
| 48 | `CheckValvetronik` | caixa de marcar | Valvetronik / Valvetronik / Valvetronik | - | - | FLAG_CONF2[0]&0x0004, MASK_FUNCTION[24]&0x0080 | sim |
| 49 | `ComboTipoIniettore` | combo | Tipo de injetor / Injector type / Tipo iniettore | Taglia | - | - | sim |
| 50 | `EditTipoIniettore` | edit numerico | Tipo de injetor / Injector type / Tipo iniettore | - | - | - | False |
| 51 | `ComboTurbo` | combo | - / - / Motore | - | - | FLAG_CONF1[1]&0x0020 | False |
| 52 | `ComboSegnaleGiri` | combo | Tipo de sinal rotações / Type of rev signal / Tipo segnale giri | Soglia | - | - | False |
| 53 | `ComboCilindri` | combo | - / - / Numero cilindri | - | - | - | False |
| 54 | `ComboAccensione` | combo | Número de cilindros / No. of cylinders / Tipo di accensione | - | - | - | False |
| 55 | `ComboCambio` | combo | Tipo de mudança / Type of change-over / Tipo di cambio | - | - | - | sim |
| 56 | `EditGiriCambio` | edit numerico | Nº de rotações para a mudança / Revs. threshold for change-over / Soglia giri per il cambio | - | GIRI_MIN_CAMBIO (16) | - | sim |
| 57 | `EditTempMotore` | edit numerico | Temperatura da água para o cambio / Water temperature for change-over / Temperatura acqua per il cambio | °C | TEMP_RID_CAMBIO (15), TEMP_RID_CAMBIO_LR (117) | - | False |
| 58 | `LabelLimite` | texto | - / - / limite | - | - | - | False |
| 59 | `EditTempCambio` | edit numerico | Temperatura do gás para o cambio / Gas temperature for change-over / Temperatura gas per il cambio | °C | TEMP_GAS_CAMBIO (117), TEMP_GAS_CAMBIO_LR (15) | - | False |
| 60 | `EditRitMin` | edit numerico | Atraso na passagem gasolina/gás / Switch-over from petrol to gas delay / Ritardo passaggio benzina-gas | s | RITARDO_CAMBIO (17) | - | False |
| 61 | `EditTInjBenzMaxPerCambio` | edit numerico | T.inj gasolina máximo para mudança gás / Max petrol inj. time for gas change-over / Tinj benzina massimo per cambio gas | ms | T_INJ_BENZ_MAX_CAMBIO (146) | - | False |
| 62 | `ButtonReset` | botao | Redefina a central de comando e retorne aos parâmetros de base / Reset ECU and load default parameters / Azzera centralina e ritorna ai parametri di base | - | - | - | False |
| 63 | `ComboCarburante` | combo | - / - / Tipo carburante | Motore | - | FLAG_CONF1[0]&0x0002 | False |
| 64 | `EditCilindrata` | edit numerico | - / - / Cilindrata (cc) | Inj. | CILINDRATA (75) | - | False |
| 65 | `DrawGridPassaggio` | grade | (seg) / (seconds) / (sec) | - | - | - | False |
| 66 | `DrawGridPassaggioGas` | grade | - / - / (sec) | - | - | - | False |
| 67 | `ComboTaglia` | combo | - / - / Taglia | - | - | - | False |
| 68 | `CheckSpegniBuzzer` | caixa de marcar | - / - / Buzzer a tempo | - | - | FLAG_CONF2[0]&0x0100 | sim |
| 69 | `CheckPartenzaGasCaldo` | caixa de marcar | Partida gás com motor aquecido / Start on gas with hot engine / Partenza a gas a motore caldo | - | - | FLAG_CONF2[0]&0x0002 | sim |
| 70 | `EditTempGasCaldo` | edit numerico | - / - / - | °C | TEMP_GAS_CALDO (195) | - | False |
| 71 | `CheckStartAndStop` | caixa de marcar | Start && Stop / Start && Stop / Start && Stop | - | - | FLAG_CONF2[0]&0x1000, MASK_FUNCTION[28]&0x0040 | sim |
| 72 | `EditSogliaGiri` | edit numerico | - / - / Soglia | V | - | - | False |
| 73 | `EditIstantaneoValue` | edit numerico | - / - / Num. iniettate tra cilindri per cambio gas | - | CHANGE_OVER_CILYNDER_DELAY (226) | - | sim |
| 74 | `CheckFlexFuel` | caixa de marcar | Flex fuel / Flex fuel / Flex fuel | - | - | FLAG_CONF2[1]&0x0100 | sim |
| 75 | `EditCicchettoInj` | edit numerico | Gas injection time in priming / Gas injection time in priming / Tempo di iniezione gas in cicchetto | ms | - | - | False |
| 76 | `EditCicchettoNum` | edit numerico | Number of gas injections in priming / Number of gas injections in priming / Numero iniettate gas in cicchetto | - | - | - | False |
| 77 | `CheckGiriAlberoCamme` | caixa de marcar | - / - / albero a camme | - | - | - | sim |
| 78 | `EditNumDenti` | edit numerico | - / - / n. denti | - | NUM_DENTI_ALBERO_CAMME (249) | - | False |
| 79 | `PanelSequenceAcquisition` | painel | - / - / - | - | - | - | False |
| 80 | `LabRpmTachimetro` | rotulo | - / - / rpm | - | - | - | False |
| 81 | `LabelRpmTachimetro` | rotulo | - / - / Inserire i giri letti da tachimetro e premere RETURN | - | - | - | False |
| 82 | `LabelStatus` | rotulo | - / - / 0x00 | - | - | - | False |
| 83 | `EditRpmTachimetro` | edit numerico | - / - / Inserire i giri letti da tachimetro e premere RETURN | rpm | - | - | False |
| 84 | `ButtonSequenceAcquisition` | botao | - / - / Acquisisci sequenza | - | - | - | sim |
| 85 | `CheckInjAcquisition` | caixa de marcar | - / - / Sequenza di iniezione manuale | - | - | FLAG_CONF2[1]&0x2000 | sim |
| 86 | `ComboFullGroup` | combo | Full group / Full group / Inj. | - | - | - | sim |
| 87 | `ButtonSwitchSettings` | botao | - / - / Switch settings | - | - | - | False |
| 88 | `PanelCambio` | painel | - / - / - | - | - | - | False |
| 89 | `CheckCambioConSovrapp` | caixa de marcar | - / - / Cambio con sovrapposizione | - | - | FLAG_CONF1[0]&0x0020, FLAG_CONF3[0]&0x0010 | sim |
| 90 | `EditParamProgress0` | edit numerico (ValueEditEx) | - / - / - | - | - | FLAG_CONF3[0]&0x0010 | False |
| 91 | `EditParamProgress1` | edit numerico (ValueEditEx) | - / - / - | - | - | - | False |
| 92 | `EditParamProgress3` | edit numerico (ValueEditEx) | - / - / - | - | - | - | False |
| 93 | `EditParamProgress2` | edit numerico (ValueEditEx) | - / - / - | - | - | - | False |
| 94 | `PanelProgressioni` | painel | - / - / - | - | - | - | sim |
| 95 | `LabelProgressRow3` | rotulo | - / - / Altri funz. benz. | - | - | - | sim |
| 96 | `LabelProgressRow2` | rotulo | - / - / Cambio | - | - | - | sim |
| 97 | `LabelProgressRow1` | rotulo | - / - / Minimo | - | - | - | sim |
| 98 | `LabelProgressRow0` | rotulo | - / - / Uscita da cutoff | - | - | - | sim |
| 99 | `LabelProgressCol0` | rotulo | - / - / n.inj.min. | - | - | - | sim |
| 100 | `LabelProgressCol1` | rotulo | - / - / n.inj.prog. | - | - | - | False |
| 101 | `LabelProgressCol2` | rotulo | - / - / dt benz. | - | - | - | False |
| 102 | `CheckProgressioni` | caixa de marcar | - / - / Abilita progressioni | - | - | FLAG_CONF3[0]&0x0004, FLAG_CONF3[0]&0x0010 | sim |
| 103 | `DrawGridProgressioni` | grade | - / - / Cambio | - | - | - | sim |
| 104 | `CheckGiriRuotaFonica` | caixa de marcar | - / - / ruota fonica | - | - | FLAG_CONF3[0]&0x2000 | sim |
| 105 | `chkRitardoPassaggioInMinuti` | caixa de marcar | - / - / Ritardo passaggio in minuti | - | - | FLAG_CONF3[1]&0x0004 | False |
| 106 | `CheckBoxEnableExtLedKLine` | caixa de marcar | - / - / Enable external LED on K-Line | - | - | FLAG_CONF3[1]&0x0008 | sim |

## Controles interativos, um a um

### `DrawDelayGasTemp` — DrawDelayGasTemp

Tipo: **grade** (`TDrawGrid`), DFM linha 883 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `None` (associacao None)): ita = "-"; eng = "-"; por = "-" `[DESCONHECIDO]`.
- Posicao no DFM: Left=37 Top=185 Largura=653 Altura=91; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Tabela de espera: quantos segundos o sistema aguarda, conforme a temperatura, antes de passar da gasolina para o gas (variante por temperatura do gas). `[OBS-COD]` (fonte: manual SW 2.16.4 C p.13).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `DrawDelayGasTempKeyPress` (0x4C42D4) `[OBS-COD]`
- `OnDblClick` -> `DrawDelayGasTempDblClick` (0x4C42E4) `[OBS-COD]`
- `OnKeyDown` -> `DrawGridKKeyDown` (0x4B2514) `[OBS-COD]`
- `OnDrawCell` -> `DrawDelayGasTempDrawCell` (0x4C3EA4) `[OBS-COD]`: desenho das celulas da grade (exibicao); nao envia nada a ECU

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Grade: cada celula editada chama um setter indexado (SetIdx) do handler `KeyPress`/`DblClick`; quadros por celula: `14 SC lo hi idx valor ck` (SetNumber+indice). Quadros exatos por celula: **DESCONHECIDO** (nao executados aqui).

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditSovrapposizione` — Tempo de sobreposição

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 913 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelSovrapposizione` (associacao INFERIDO)): ita = "Tempo di sovrapposizione"; eng = "Overlapping time"; por = "Tempo de sobreposição" `[OBS-COD]`.
- Unidade ao lado: **s** `[OBS-COD]` (rotulo `LabelS15`).
- Posicao no DFM: Left=548 Top=282 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 2.0, passo (Precision) 0.02. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTSOVRAPPOSIZIONE`: "It is the time in which petrol injectors and gas injectors are both opened \ to allow the gas to fill the injector-manifold pipes and deplete any after-change transients, \ thus preventing from shakes of the car".

**O que isso faz (linguagem simples)**

- Tempo (0 a 2 s) em que a injecao de gasolina e a de gas ficam abertas juntas na troca, para o gas encher o regulador e evitar tranco depois; normalmente fica em 0. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.14).
- Manual SW 2.16.4 C, p.14 (linha 688 de sw.txt): "T Overlapping times (range 0 - 2”); Overlap occurs when the petrol injectors and gas solenoids are open at the same time. The purpose of this occurrence is to allow gas to flow into the regulator/injectors and to pre-" `[OBS-COD]`.

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 46 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **TEMPO_SOVRAPPOSIZIONE** (SerialCode 109, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x436A7D): `valor_bruto = trunc(((50 * v) + 0.5))` com `v` = valor digitado.
    - ECU -> tela (getter 0x435226): `valor_na_tela = (SD[0x2AF8] * 0.02)`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- TEMPO_SOVRAPPOSIZIONE (SC 109): bruto 0; na tela: **0.0** `[OBS-FIO, tela DERIVADO]` (LOG linha 1106986).

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
  - valor atual da ECU do dono: tela = 0.0 -> quadro `12 6D 00 00 7F`.
  - maximo da faixa do DFM: tela = 2.0 -> quadro `12 6D 00 64 E3`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4A91AB` (ComboCambioKeyPress, ComboCambioKeyPress via sub_4A90B8) — SE tecla == Enter E sub_754CCC == 0
- `Visible := calculado` em `0x4A7FAC` (TabCambioShow) — incondicional

### `EditCicchetto` — Tempo di cicchetto

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 934 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelCicchetto` (associacao INFERIDO)): ita = "Tempo di cicchetto"; eng = "-"; por = "-" `[OBS-COD]`.
- Unidade ao lado: **s** `[OBS-COD]` (rotulo `LabelS15`).
- Posicao no DFM: Left=548 Top=282 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 4.8, passo (Precision) 0.02. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTCICCHETTO`: "Priming time".

**O que isso faz (linguagem simples)**

- Tempo da pequena injecao de gas de "escorva" (priming) que prepara o circuito antes de rodar a gas. `[OBS-COD]` (fonte: dica do proprio ProgBase (HINTCICCHETTO)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: {"LR ([SD+0x80D]!=0)": 20, "demais ([SD+0x80D]==0)": 47} (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia LR: vetor **TEMPO_CICCHETTO_LR** (SerialCode 31, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x436ACB): `valor_bruto = trunc(((50 * v) + 0.5))` com `v` = valor digitado.
    - ECU -> tela (getter 0x435270): `valor_na_tela = (SD[0x2AFC] * 0.02)`.
  - Familia demais: vetor **TEMPO_CICCHETTO** (SerialCode 110, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x436AA4): `valor_bruto = trunc(((50 * v) + 0.5))` com `v` = valor digitado.
    - ECU -> tela (getter 0x43524B): `valor_na_tela = (SD[0x2AFC] * 0.02)`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- TEMPO_CICCHETTO (SC 110): bruto 75; na tela: **1.5** `[OBS-FIO, tela DERIVADO]` (LOG linha 1107008).

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
  - valor atual da ECU do dono: tela = 1.5 -> quadro `12 6E 00 4B CB`.
  - maximo da faixa do DFM: tela = 4.8 -> quadro `12 6E 00 F0 70`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4A92C7` (ComboCambioKeyPress, ComboCambioKeyPress via sub_4A90B8) — SE tecla == Enter E sub_754CCC == 0
- `Visible := calculado` em `0x4A8315` (TabCambioShow) — incondicional

### `EditCicchettoRpm` — RPM threshold for crank

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 961 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelCicchettoRpm` (associacao INFERIDO)): ita = "Soglia giri per crank"; eng = "RPM threshold for crank"; por = "RPM threshold for crank" `[OBS-COD]`.
- Unidade ao lado: **rpm** `[OBS-COD]` (rotulo `LabCicchettoRpm`).
- Posicao no DFM: Left=548 Top=376 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 500.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Rotacao abaixo da qual vale a injecao de escorva (limiar de rpm "crank", durante a partida). [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditAdvParamInjGasKeyPress` (0x4C7150) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x432BF4` em `0x4C7194` (le: ADV_PARAM_INJ; escreve: -)
  - chama o metodo do TStreamDati `0x432BE4` em `0x4C71A7` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x432BF4` em `0x4C71E6` (le: ADV_PARAM_INJ; escreve: -)
  - chama o metodo do TStreamDati `0x432BE4` em `0x4C71F9` (le: -; escreve: -)
- `OnEnter` -> `EditGiriCambioEnter` (0x4A98D8) `[OBS-COD]`
- `OnExit` -> `EditGiriCambioExit` (0x4A9CC0) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4A935D` (ComboCambioKeyPress, ComboCambioKeyPress via sub_4A90B8) — SE tecla == Enter E sub_754CCC == 0
- `Visible := calculado` em `0x4A83AF` (TabCambioShow) — incondicional

### `CheckSegnaleGiri` — Tipo segnale giri

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 986 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Tipo segnale giri"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=38 Top=-535 Largura=333 Altura=52; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTSEGNALEGIRI`: "Type of RPM signal: \"standard\" normal signal, \"weak\" low voltage signal, \"custom\" to insert a voltage threshold".

**O que isso faz (linguagem simples)**

- Marcada: a ECU le a rotacao pelo fio do sinal; desmarcada: o fio marrom fica solto e a rotacao e calculada pelos tempos de injecao (e o tipo de ignicao deixa de valer). `[OBS-COD]` (fonte: manual SW 2.16.4 C p.11).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF3** (SerialCode 250), palavra 0, mascara 0x2000, caso do setter 10, papel "None", condicao "sempre"; origem: handler CheckSegnaleGiriClick.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 0, mascara 0x0040, caso do setter 18, papel "None", condicao "sempre"; origem: handler CheckSegnaleGiriClick.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF2** (SerialCode 192), palavra 1, mascara 0x0040, caso do setter 27, papel "None", condicao "sempre"; origem: handler CheckSegnaleGiriClick.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF3 palavra 0 = 0x0000 -> bit 0x2000: **desligado** `[OBS-FIO]` (LOG linha 1102872).
- FLAG_CONF1 palavra 0 = 0x2486 -> bit 0x0040: **desligado** `[OBS-FIO]` (LOG linha 1102844).
- FLAG_CONF2 palavra 1 = 0x0000 -> bit 0x0040: **desligado** `[OBS-FIO]` (LOG linha 1102816).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckSegnaleGiriClick` (0x4C5494) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x4310F8` em `0x4C54CF` (le: TIPO_ACCENS, cache de TIPO_ACCENS; escreve: cache de TIPO_ACCENS)
  - SetEsc codigo 124 -> vetor NUM_DENTI_ALBERO_CAMME (SC 249) em `0x4C54F1`
  - SetFlag3 caso 10 -> FLAG_CONF3 palavra 0 mascara 0x2000 em `0x4C5504`
  - chama o metodo do TStreamDati `0x42B834` em `0x4C550E` (le: AebConnection; escreve: -)
  - muda Text de um controle nao identificado pela analise estatica em `0x4C553F`
  - chama o metodo do TStreamDati `0x434AA4` em `0x4C558A` (le: -; escreve: -)
  - GetFlag3 caso 10 -> FLAG_CONF3 palavra 0 mascara 0x2000 em `0x4C55C6`
  - chama o metodo do TStreamDati `0x4348F4` em `0x4C55E0` (le: -; escreve: -)
  - GetEsc codigo 124 -> vetor NUM_DENTI_ALBERO_CAMME (SC 249) em `0x4C561A`
  - GetFlag3 caso 10 -> FLAG_CONF3 palavra 0 mascara 0x2000 em `0x4C563C`
  - muda Visible de `LabelNumDenti` em `0x4C56DF`
  - muda Visible de `EditNumDenti` em `0x4C5728`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF3 palavra 0 mascara 0x2000): 0000 0000 -> 2000 0000, quadro `35 FA 00 00 20 00 00 4F`.
  - desligar bit (FLAG_CONF3 palavra 0 mascara 0x2000): 0000 0000 -> 0000 0000, quadro `35 FA 00 00 00 00 00 2F`.
  - ligar bit (FLAG_CONF1 palavra 0 mascara 0x0040): 2486 1051 -> 24C6 1051, quadro `35 03 00 C6 24 51 10 83`.
  - desligar bit (FLAG_CONF1 palavra 0 mascara 0x0040): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - ligar bit (FLAG_CONF2 palavra 1 mascara 0x0040): 0001 0000 -> 0001 0040, quadro `35 C0 00 01 00 40 00 36`.
  - desligar bit (FLAG_CONF2 palavra 1 mascara 0x0040): 0001 0000 -> 0001 0000, quadro `35 C0 00 01 00 00 00 F6`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por sub_4310E8` em `0x4A7630` (TabCambioShow) — incondicional
- `Visible := verdadeiro` em `0x4A797A` (TabCambioShow) — incondicional

### `CheckDisabilitaCommutatore` — Disabilita commutatore

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 1010 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Disabilita commutatore"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=455 Top=-34 Largura=365 Altura=31; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTDISABILITACOMMUTATORE`: "Disable switch".

**O que isso faz (linguagem simples)**

- Forca o motor a rodar so a gas (menos na partida a frio, com erro de diagnostico salvo ou sem combustivel), ignorando o botao de troca do painel. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.13).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF2** (SerialCode 192), palavra 0, mascara 0x0020, caso do setter 5, papel "caixa (Checked)", condicao "sempre"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF2 palavra 0 = 0x0001 -> bit 0x0020: **desligado** `[OBS-FIO]` (LOG linha 1102816).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckDisabilitaCommutatoreClick` (0x4C1C04) `[OBS-COD]`
  - SetFlag2 caso 5 -> FLAG_CONF2 palavra 0 mascara 0x0020 em `0x4C1C2D`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF2 palavra 0 mascara 0x0020): 0001 0000 -> 0021 0000, quadro `35 C0 00 21 00 00 00 16`.
  - desligar bit (FLAG_CONF2 palavra 0 mascara 0x0020): 0001 0000 -> 0001 0000, quadro `35 C0 00 01 00 00 00 F6`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetFlag2(caso 5)` em `0x4A8559` (TabCambioShow) — incondicional
- `Checked/ItemIndex := calculado por GetFlag2(caso 5)` em `0x4907D8` (FormShow; metodo de OUTRA aba/tela) — incondicional

### `CheckValvetronik` — Valvetronik

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 1031 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Valvetronik"; eng = "Valvetronik"; por = "Valvetronik" `[OBS-COD]`.
- Posicao no DFM: Left=38 Top=-63 Largura=186 Altura=31; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTVALVETRONIK`: "Valvetronik".

**O que isso faz (linguagem simples)**

- Faz o sistema continuar funcionando quando o sinal de rotacao some (motores Valvetronic), considerando so a ignicao ligada. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.12).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF2** (SerialCode 192), palavra 0, mascara 0x0004, caso do setter 2, papel "caixa (Checked)", condicao "sempre"; origem: flags-do-original.json.
- Bit de configuracao `[OBS-COD]`: **MASK_FUNCTION** (SerialCode 194), palavra 24, mascara 0x0080, caso do setter None, papel "dependente (Enabled)", condicao "obj+0x2A64!=0"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF2 palavra 0 = 0x0001 -> bit 0x0004: **desligado** `[OBS-FIO]` (LOG linha 1102816).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckValvetronikClick` (0x4C2B14) `[OBS-COD]`
  - SetFlag2 caso 2 -> FLAG_CONF2 palavra 0 mascara 0x0004 em `0x4C2B51`
  - muda Text de um controle nao identificado pela analise estatica em `0x4C2B81`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF2 palavra 0 mascara 0x0004): 0001 0000 -> 0005 0000, quadro `35 C0 00 05 00 00 00 FA`.
  - desligar bit (FLAG_CONF2 palavra 0 mascara 0x0004): 0001 0000 -> 0001 0000, quadro `35 C0 00 01 00 00 00 F6`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetFlag2(caso 2)` em `0x4A85C5` (TabCambioShow) — incondicional
- `Checked/ItemIndex := calculado por GetFlag2(caso 2)` em `0x490814` (FormShow; metodo de OUTRA aba/tela) — incondicional

### `ComboTipoIniettore` — Tipo de injetor

Tipo: **combo** (`TKeyComboBox`), DFM linha 1046 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTipoIniettore` (associacao INFERIDO)): ita = "Tipo iniettore"; eng = "Injector type"; por = "Tipo de injetor" `[OBS-COD]`.
- Unidade ao lado: **Taglia** `[OBS-COD]` (rotulo `LabelTaglia`).
- Posicao no DFM: Left=305 Top=-616 Largura=224 Altura=37; visivel de projeto: sim; habilitado de projeto: False `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTTIPOINIETTORE`: "Type of installed injectors rail".

**O que isso faz (linguagem simples)**

- Tipo de injetor de gas instalado (Matrix, Keihin, Landi...); a ECU o reconhece pela fiacao e a lista so confirma. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.10).
- Manual SW 2.16.4 C, p.10 (linha 437 de sw.txt): "D Injector type: Matrix, Keihin, Landi, OMEGAS- EVO only - AEB, Lovato J, Lovato K. D Detection of type of injector connected to the ECU is perfor- med automatically thanks to the wiring system used. AEB" `[OBS-COD]`.

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 1, mascara 0x0020, caso do setter 16, papel "None", condicao "sempre"; origem: handler ComboTipoIniettoreKeyPress.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 0, mascara 0x0002, caso do setter 1, papel "None", condicao "sempre"; origem: handler ComboTipoIniettoreKeyPress.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 1 = 0x1051 -> bit 0x0020: **desligado** `[OBS-FIO]` (LOG linha 1102844).
- FLAG_CONF1 palavra 0 = 0x2486 -> bit 0x0002: **LIGADO** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `ComboTipoIniettoreKeyPress` (0x4BA4B4) `[OBS-COD]`
  - muda Text de um controle nao identificado pela analise estatica em `0x4BA53B`
  - GetCombo em `0x4BA5DA`
  - GetCombo em `0x4BA5F1`
  - GetCombo em `0x4BA601`
  - GetCombo em `0x4BA611`
  - GetCombo em `0x4BA636`
  - GetCombo em `0x4BA645`
  - GetCombo em `0x4BA655`
  - GetCombo em `0x4BA665`
  - muda Text de um controle nao identificado pela analise estatica em `0x4BA6A9`
  - chama o metodo do TStreamDati `0x42FFD8` em `0x4BA731` (le: TIPO_INIETTORE; escreve: -)
  - GetCombo em `0x4BA73F`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 1 mascara 0x0020): 2486 1051 -> 2486 1071, quadro `35 03 00 86 24 71 10 63`.
  - desligar bit (FLAG_CONF1 palavra 1 mascara 0x0020): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - ligar bit (FLAG_CONF1 palavra 0 mascara 0x0002): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - desligar bit (FLAG_CONF1 palavra 0 mascara 0x0002): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado` em `0x4BA59B` (ComboTipoIniettoreKeyPress) — SE tecla == Enter E sub_754CCC == 0 E cmp eax, dword ptr [ebp - 0x50] !=  E sub_A4EDD0 != 0
- `Checked/ItemIndex := calculado` em `0x4BA712` (ComboTipoIniettoreKeyPress) — SE tecla == Enter E sub_754CCC == 0 E cmp eax, dword ptr [ebp - 0x50] !=  E sub_A4EDD0 == 0 E cmp edi, eax !=  E cmp byte ptr [ebp - 0x51], 0 ==  E sub_A4EDD0 != 0
- `Checked/ItemIndex := calculado por chamada virtual dword ptr [ecx + 0x60]` em `0x4A6B58` (TabCambioShow) — incondicional

### `EditTipoIniettore` — Tipo de injetor

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 1066 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTipoIniettore` (associacao INFERIDO)): ita = "Tipo iniettore"; eng = "Injector type"; por = "Tipo de injetor" `[OBS-COD]`.
- Posicao no DFM: Left=241 Top=-616 Largura=58 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 255.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTTIPOINIETTORE`: "Type of installed injectors rail".

**O que isso faz (linguagem simples)**

- Valor numerico do tipo de injetor de gas instalado (a mesma escolha da lista "Tipo de injetor"). `[OBS-COD]` (fonte: manual SW 2.16.4 C p.10).
- Manual SW 2.16.4 C, p.10 (linha 437 de sw.txt): "D Injector type: Matrix, Keihin, Landi, OMEGAS- EVO only - AEB, Lovato J, Lovato K. D Detection of type of injector connected to the ECU is perfor- med automatically thanks to the wiring system used. AEB" `[OBS-COD]`.

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditTipoIniettoreKeyPress` (0x4BACE0) `[OBS-COD]`
  - muda Text de um controle nao identificado pela analise estatica em `0x4BAD32`
  - GetCombo em `0x4BADB0`
  - muda Text de um controle nao identificado pela analise estatica em `0x4BADE3`
  - chama o metodo do TStreamDati `0x42FFD8` em `0x4BAE6E` (le: TIPO_INIETTORE; escreve: -)

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `ComboTurbo` — Motore

Tipo: **combo** (`TKeyComboBox`), DFM linha 1086 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTurbo` (associacao INFERIDO)): ita = "Motore"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=659 Top=-661 Largura=129 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Opcoes da lista `[OBS-COD]`: 0 = "Aspirato"; 1 = "Turbo".
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTTURBO`: "This function allows to select the engine type (aspirated or turbo)".

**O que isso faz (linguagem simples)**

- Informa se o motor e aspirado ou turbo. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.10).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 1, mascara 0x0020, caso do setter 16, papel "seletor (TKeyComboBox)", condicao "sempre"; origem: flags-do-original.json.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 0, mascara 0x0002, caso do setter 1, papel "None", condicao "sempre"; origem: handler ComboTurboKeyPress.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 1 = 0x1051 -> bit 0x0020: **desligado** `[OBS-FIO]` (LOG linha 1102844).
- FLAG_CONF1 palavra 0 = 0x2486 -> bit 0x0002: **LIGADO** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `ComboTurboKeyPress` (0x4B9C44) `[OBS-COD]`
  - muda Text de um controle nao identificado pela analise estatica em `0x4B9CB6`
  - SetFlag1 caso 16 -> FLAG_CONF1 palavra 1 mascara 0x0020 em `0x4B9D4E`
  - chama o metodo do TStreamDati `0x43362C` em `0x4B9D8E` (le: -; escreve: -)
  - GetCombo em `0x4B9D9E`
  - GetFlag1 caso 16 -> FLAG_CONF1 palavra 1 mascara 0x0020 em `0x4B9DAD`
  - GetFlag1 caso 1 -> FLAG_CONF1 palavra 0 mascara 0x0002 em `0x4B9DBE`
  - muda Caption de um controle nao identificado pela analise estatica em `0x4B9E22`
  - muda Caption de um controle nao identificado pela analise estatica em `0x4B9E39`
  - chama o metodo do TStreamDati `0x439E94` em `0x4B9F0F` (le: AebConnection, TimerIO; escreve: -)
  - GetCombo em `0x4B9F51`
  - GetCombo em `0x4B9F68`
  - GetCombo em `0x4B9F7F`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 1 mascara 0x0020): 2486 1051 -> 2486 1071, quadro `35 03 00 86 24 71 10 63`.
  - desligar bit (FLAG_CONF1 palavra 1 mascara 0x0020): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - ligar bit (FLAG_CONF1 palavra 0 mascara 0x0002): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - desligar bit (FLAG_CONF1 palavra 0 mascara 0x0002): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Cada opcao da lista chama o metodo `0x43362C` `[INFERIDO]` (convencao assumida):
  - opcao 0 "Aspirato" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x433632.
  - opcao 1 "Turbo" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x433632.
- Cada opcao da lista chama o metodo `0x439E94` `[INFERIDO]` (convencao assumida):
  - opcao 0 "Aspirato" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x439ecb.
  - opcao 1 "Turbo" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x439ecb.
  - Estrutura: igual a acima. Fonte: metodo 0x43362C executado no Unicorn com edx = indice da opcao (convencao assumida; o handler ComboTurboKeyPress o chama em 0x4B9D8E)
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado` em `0x4B9D1B` (ComboTurboKeyPress) — SE tecla == Enter E sub_754CCC == 0 E cmp eax, ecx !=  E sub_A4EDD0 != 0
- `Checked/ItemIndex := calculado por GetFlag1(caso 16)` em `0x4A7DF1` (TabCambioShow) — incondicional

### `ComboSegnaleGiri` — Tipo de sinal rotações

Tipo: **combo** (`TKeyComboBox`), DFM linha 1103 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelSegnaleGiri` (associacao INFERIDO)): ita = "Tipo segnale giri"; eng = "Type of rev signal"; por = "Tipo de sinal rotações" `[OBS-COD]`.
- Unidade ao lado: **Soglia** `[OBS-COD]` (rotulo `LabelSogliaGiri`).
- Posicao no DFM: Left=372 Top=-530 Largura=157 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Opcoes da lista `[OBS-COD]`: 0 = "Standard" / Padrão; 1 = "Debole" / Fraco; 2 = "Custom".
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTSEGNALEGIRI`: "Type of RPM signal: \"standard\" normal signal, \"weak\" low voltage signal, \"custom\" to insert a voltage threshold".

**O que isso faz (linguagem simples)**

- Tipo do sinal de rotacao: "Padrao" para sinais de 5 a 12 V e "Fraco" para sinais de 2 a 5 V (ex.: transistor da bobina). `[OBS-COD]` (fonte: manual SW 2.16.4 C p.11).
- Manual SW 2.16.4 C, p.11 (linha 472 de sw.txt): "G Type of rev signal: Standard/Weak . Leave the “Standard” setting with signals that fluctuate from 5V to 12V. Select “Weak” for signals that vary from 2V to 5V. Note" `[OBS-COD]`.

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `ComboSegnaleGiriKeyPress` (0x4ACDC8) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x43109C` em `0x4ACE2F` (le: TIPO_ACCENS, cache de TIPO_ACCENS; escreve: cache de TIPO_ACCENS)
  - chama o metodo do TStreamDati `0x43109C` em `0x4ACE55` (le: TIPO_ACCENS, cache de TIPO_ACCENS; escreve: cache de TIPO_ACCENS)
  - chama o metodo do TStreamDati `0x43109C` em `0x4ACE67` (le: TIPO_ACCENS, cache de TIPO_ACCENS; escreve: cache de TIPO_ACCENS)
  - SetEsc codigo 106 -> vetor SOGLIA_LETTURA_GIRI_LR (SC 214) em `0x4ACE7F`
  - muda Visible de `LabelSogliaGiri` em `0x4ACEB2`
  - muda Visible de `EditSogliaGiri` em `0x4ACEC6`
  - muda Visible de `LabSogliaGiri` em `0x4ACEDA`
  - chama o metodo do TStreamDati `0x43109C` em `0x4ACF1C` (le: TIPO_ACCENS, cache de TIPO_ACCENS; escreve: cache de TIPO_ACCENS)
  - muda Visible de `LabelSogliaGiri` em `0x4ACF29`
  - muda Visible de `EditSogliaGiri` em `0x4ACF36`
  - muda Visible de `LabSogliaGiri` em `0x4ACF43`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Cada opcao da lista chama o metodo `0x43109C` `[INFERIDO]` (convencao assumida):
  - opcao 0 "Standard" -> quadro(s) `12 14 00 01 27`.
  - opcao 1 "Debole" -> quadro(s) `12 14 00 41 67`.
  - opcao 2 "Custom" -> quadro(s) `12 14 00 41 67`.
  - Estrutura: igual a acima. Fonte: metodo 0x43109C executado no Unicorn com edx = indice da opcao (convencao assumida; o handler ComboSegnaleGiriKeyPress o chama em 0x4ACE2F)
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado` em `0x4A7B56` (TabCambioShow) — SE [obj+0x2BD4] != 0 E SD.familia_LR[+0x80D] != 0 E edi == 0xff
- `Checked/ItemIndex := calculado` em `0x4A7B73` (TabCambioShow) — SE [obj+0x2BD4] != 0 E SD.familia_LR[+0x80D] != 0 E edi != 0xff E edi == 0x85
- `Checked/ItemIndex := calculado` em `0x4A7B88` (TabCambioShow) — SE [obj+0x2BD4] != 0 E SD.familia_LR[+0x80D] != 0 E edi != 0xff E edi != 0x85
- `Checked/ItemIndex := calculado por sub_43108C` em `0x4A7C26` (TabCambioShow) — SE SD.familia_LR[+0x80D] == 0

### `ComboCilindri` — Numero cilindri

Tipo: **combo** (`TKeyComboBox`), DFM linha 1126 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelCilindri` (associacao INFERIDO)): ita = "Numero cilindri"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=548 Top=-361 Largura=240 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Opcoes da lista `[OBS-COD]`: 0 = "3 cilindri"; 1 = "4 cilindri"; 2 = "5 cilindri"; 3 = "6 cilindri"; 4 = "8 cilindri".
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTCILINDRI`: "The function allows to set the number of cylinders. Allowed values are 2, 3 and 4 cylinders. 5,6 or cylinders selection enabled only when connected to a 5,6, 8 cylinders ECU".

**O que isso faz (linguagem simples)**

- Numero de cilindros do motor (2, 3, 4, 4 com 2 injetores, 5, 6 ou 8); a ECU usa para calcular o regime. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.11).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 0, mascara 0x0020, caso do setter 27, papel "None", condicao "sempre"; origem: handler ComboCilindriKeyPress.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 0 = 0x2486 -> bit 0x0020: **desligado** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `ComboCilindriKeyPress` (0x4BA2EC) `[OBS-COD]`
  - SetEsc codigo 23 -> vetor TIPO_INIEZIONE (SC 52) em `0x4BA350`
  - SetEsc codigo 15 -> vetor TIPO_ACCENS (SC 20) em `0x4BA3AD`
  - SetFlag1 caso 27 -> FLAG_CONF1 palavra 0 mascara 0x0020 em `0x4BA3C4`
  - chama o metodo do TStreamDati `0x4341BC` em `0x4BA3DA` (le: cache de TIPO_INIEZIONE; escreve: -)
  - chama o metodo do TStreamDati `0x43414C` em `0x4BA3EB` (le: cache de TIPO_INIEZIONE; escreve: -)

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 0 mascara 0x0020): 2486 1051 -> 24A6 1051, quadro `35 03 00 A6 24 51 10 63`.
  - desligar bit (FLAG_CONF1 palavra 0 mascara 0x0020): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Cada opcao da lista chama o metodo `0x4341BC` `[INFERIDO]` (convencao assumida):
  - opcao 0 "3 cilindri" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x4341cc.
  - opcao 1 "4 cilindri" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x4341cc.
  - opcao 2 "5 cilindri" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x4341cc.
  - opcao 3 "6 cilindri" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x4341cc.
  - opcao 4 "8 cilindri" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x4341cc.
- Cada opcao da lista chama o metodo `0x43414C` `[INFERIDO]` (convencao assumida):
  - opcao 0 "3 cilindri" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x43415c.
  - opcao 1 "4 cilindri" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x43415c.
  - opcao 2 "5 cilindri" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x43415c.
  - opcao 3 "6 cilindri" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x43415c.
  - opcao 4 "8 cilindri" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x43415c.
  - Estrutura: igual a acima. Fonte: metodo 0x4341BC executado no Unicorn com edx = indice da opcao (convencao assumida; o handler ComboCilindriKeyPress o chama em 0x4BA3DA)
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por chamada virtual dword ptr [ecx + 0x60]` em `0x4A7D73` (TabCambioShow) — incondicional

### `ComboAccensione` — Número de cilindros

Tipo: **combo** (`TKeyComboBox`), DFM linha 1149 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelAccensione` (associacao INFERIDO)): ita = "Tipo di accensione"; eng = "No. of cylinders"; por = "Número de cilindros" `[OBS-COD]`.
- Posicao no DFM: Left=548 Top=-321 Largura=240 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Opcoes da lista `[OBS-COD]`: 0 = "Monobobina"; 1 = "Bibobina"; 2 = "Contagiri"; 3 = "Contagiri 2".
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTACCENSIONE`: "Once the right number of cylinders is set, the information is used by the ECU to correctly calculate the engine revs starting from the period read on the revs input. \ SINGLE IGNITION COIL is selected if the car has a coil for each cylinder and the signal is taken from the negative or transistor of the coil. \ DOUBLE IGNITION COIL is selected if the car has a coil that operates two cylinders and the signal is taken from the negative or transistor of the coil. \ The REV-COUNTER option is used if the wire is connected to the original rev-counter of the car with 4-cylinder engine".

**O que isso faz (linguagem simples)**

- Tipo de ignicao (bobina simples, dupla, tacometro, tacometro 2) para a ECU calcular a rotacao a partir do sinal. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.11).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `ComboAccensioneKeyPress` (0x4BA230) `[OBS-COD]`
  - SetEsc codigo 15 -> vetor TIPO_ACCENS (SC 20) em `0x4BA2AB`
  - SetEsc codigo 15 -> vetor TIPO_ACCENS (SC 20) em `0x4BA2DD`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado` em `0x4A7CED` (TabCambioShow) — incondicional

### `ComboCambio` — Tipo de mudança

Tipo: **combo** (`TKeyComboBox`), DFM linha 1172 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelCambio` (associacao INFERIDO)): ita = "Tipo di cambio"; eng = "Type of change-over"; por = "Tipo de mudança" `[OBS-COD]`.
- Posicao no DFM: Left=548 Top=-279 Largura=240 Altura=37; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Opcoes da lista `[OBS-COD]`: 0 = "In accelerazione" / Em aceleração; 1 = "In decelerazione" / Em desaceleração; 2 = "Partenza a gas" / Partida a gás.
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTCAMBIO`: "The function allows to select if petrol/gas changeover will be made in acceleration or deceleration. \  The choice can be made based on the characteristics of the car or particular request form the owner of the car".

**O que isso faz (linguagem simples)**

- Quando trocar de gasolina para gas: acelerando (passou da rotacao de troca) ou desacelerando (rotacao acima do limite e corte de gasolina), conforme o motor. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.12).
- Manual SW 2.16.4 C, p.12 (linha 543 de sw.txt): "J J Type of change over: • In acceleration, the number of engine revs must exce- ed the previously set threshold which is mentioned later (Rev threshold for change-over);" `[OBS-COD]`.

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 0, mascara 0x0100, caso do setter 0, papel "None", condicao "sempre"; origem: handler ComboCambioKeyPress.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 0 = 0x2486 -> bit 0x0100: **desligado** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `ComboCambioKeyPress` (0x4A8FB8) `[OBS-COD]`
  - SetCombo em `0x4A9017`
  - SetFlag1 caso 0 -> FLAG_CONF1 palavra 0 mascara 0x0100 em `0x4A9028`
  - muda Text de um controle nao identificado pela analise estatica em `0x4A9048`
  - SetFlag1 caso 0 -> FLAG_CONF1 palavra 0 mascara 0x0100 em `0x4A909A`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 0 mascara 0x0100): 2486 1051 -> 2586 1051, quadro `35 03 00 86 25 51 10 44`.
  - desligar bit (FLAG_CONF1 palavra 0 mascara 0x0100): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Cada opcao da lista chama o metodo `0x43074C` `[INFERIDO]` (convencao assumida):
  - opcao 0 "In accelerazione" -> quadro(s) `13 10 00 B0 04 D7`.
  - opcao 1 "In decelerazione" -> quadro(s) `13 10 00 B1 04 D8`.
  - opcao 2 "Partenza a gas" -> quadro(s) `13 10 00 00 00 23`.
  - Estrutura: igual a acima. Fonte: metodo 0x43074C executado no Unicorn com edx = indice da opcao (convencao assumida; o handler ComboCambioKeyPress o chama em 0x4A9017)
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado` em `0x4A7D95` (TabCambioShow) — SE GetFlag1(caso 0) != 0
- `Checked/ItemIndex := calculado por sub_430734` em `0x4A7DB0` (TabCambioShow) — SE GetFlag1(caso 0) == 0

### `EditGiriCambio` — Nº de rotações para a mudança

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 1188 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelGiriCambio` (associacao INFERIDO)): ita = "Soglia giri per il cambio"; eng = "Revs. threshold for change-over"; por = "Nº de rotações para a mudança" `[OBS-COD]`.
- Posicao no DFM: Left=548 Top=-237 Largura=72 Altura=37; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 550.0, maximo 3000.0, passo (Precision) 50.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTGIRICAMBIO`: "The function allows the number of engine revs above which the gas switch takes place to be selected".

**O que isso faz (linguagem simples)**

- Rotacao minima (550 a 3000 rpm, padrao 1600) acima da qual a ECU troca de gasolina para gas. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.12).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 12 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **GIRI_MIN_CAMBIO** (SerialCode 16, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x43698B): `valor_bruto = ((trunc(v) & 65534) | (SD[0x2AEC] & 1))` com `v` = valor digitado.
    - ECU -> tela (getter 0x43513F): `valor_na_tela = (SD[0x2AEC] & 65534)`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- GIRI_MIN_CAMBIO (SC 16): bruto 1200; na tela: **0.0** `[OBS-FIO, tela DERIVADO]` (LOG linha 1105412).

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
  - valor atual da ECU do dono: tela = 0.0 -> quadro `13 10 00 00 00 23`.
  - maximo da faixa do DFM: tela = 3000.0 -> quadro `13 10 00 B8 0B E6`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4A912A` (ComboCambioKeyPress, ComboCambioKeyPress via sub_4A90B8) — SE tecla == Enter E sub_754CCC == 0
- `Visible := calculado` em `0x4A912A` (TabCambioShow, TabCambioShow via sub_4A90B8) — incondicional

### `EditTempMotore` — Temperatura da água para o cambio

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 1208 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTempMotore` (associacao INFERIDO)): ita = "Temperatura acqua per il cambio"; eng = "Water temperature for change-over"; por = "Temperatura da água para o cambio" `[OBS-COD]`.
- Unidade ao lado: **°C** `[OBS-COD]` (rotulo `LabelT2`).
- Posicao no DFM: Left=548 Top=-195 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo -40.0, maximo 120.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTTEMPMOTORE`: "If water temperature is over this value the ECU switches to gas".

**O que isso faz (linguagem simples)**

- Temperatura da agua (40 a 90 graus C) que, junto com a rotacao, libera a troca de gasolina para gas. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.12).
- Manual SW 2.16.4 C, p.12 (linha 557 de sw.txt): "LWater temperature for change-over: (range 40-90 °C) Together with the fuel change rpm threshold, this pa- rameter is evaluated by the ECU for changing the fuel feed from petrol to gas." `[OBS-COD]`.

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: {"LR ([SD+0x80D]!=0)": 52, "demais ([SD+0x80D]==0)": 10} (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia LR: vetor **TEMP_RID_CAMBIO_LR** (SerialCode 117, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x43681E): `valor_bruto = f_42A8F4(v)` com `v` = valor digitado.
    - ECU -> tela (getter 0x435020): `valor_na_tela = f_42A788(SD[0x29C4])`.
    - Usa funcoes auxiliares do .exe: 0x42A788, 0x42A8F4 (tabelas de conversao; ver docs/progbase/abas/_funcoes_auxiliares.json) `[OBS-COD]`.
  - Familia demais: vetor **TEMP_RID_CAMBIO** (SerialCode 15, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x4367B8): `valor_bruto = f_42A8F4(v)` com `v` = valor digitado.
    - ECU -> tela (getter 0x434FEA): `valor_na_tela = f_42A788(SD[0x29C4])`.
    - Usa funcoes auxiliares do .exe: 0x42A788, 0x42A8F4 (tabelas de conversao; ver docs/progbase/abas/_funcoes_auxiliares.json) `[OBS-COD]`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- TEMP_RID_CAMBIO (SC 15): bruto 88; na tela: **5.0** `[OBS-FIO, tela DERIVADO]` (LOG linha 1105390).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditTempMotoreKeyPress` (0x4C8F84) `[OBS-COD]`
  - GetEsc codigo 10 -> vetor TEMP_RID_CAMBIO (SC 15) em `0x4C8FBB`
  - chama o metodo do TStreamDati `0x42A8F4` em `0x4C8FCF` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x42A788` em `0x4C8FEC` (le: -; escreve: -)
  - SetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4C902F` (handler generico)
  - GetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4C9047` (handler generico)
  - chama o metodo do TStreamDati `0x42A8F4` em `0x4C9097` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x42A8F4` em `0x4C90BC` (le: -; escreve: -)
- `OnEnter` -> `EditGiriCambioEnter` (0x4A98D8) `[OBS-COD]`
- `OnExit` -> `EditGiriCambioExit` (0x4A9CC0) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Gravar valor (executado no Unicorn com o codigo original; `[OBS-COD]`):
  - valor atual da ECU do dono: tela = 5.0 -> quadro `12 0F 00 B6 D7`.
  - maximo da faixa do DFM: tela = 120.0 -> quadro `12 0F 00 09 2A`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditTempCambio` — Temperatura do gás para o cambio

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 1248 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTempCambio` (associacao INFERIDO)): ita = "Temperatura gas per il cambio"; eng = "Gas temperature for change-over"; por = "Temperatura do gás para o cambio" `[OBS-COD]`.
- Unidade ao lado: **°C** `[OBS-COD]` (rotulo `LabelT1`).
- Posicao no DFM: Left=548 Top=-111 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo -40.0, maximo 120.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTTEMPCAMBIO`: "If gas temperature is over this value the ECU switches to gas".

**O que isso faz (linguagem simples)**

- Temperatura do gas acima da qual a troca para gas e permitida (quando a troca por temperatura do gas esta ligada). `[OBS-COD]` (fonte: manual SW 2.16.4 C p.22).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: {"LR ([SD+0x80D]!=0)": 11, "demais ([SD+0x80D]==0)": 51} (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia LR: vetor **TEMP_GAS_CAMBIO_LR** (SerialCode 15, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x4367DA): `valor_bruto = f_42A698(v)` com `v` = valor digitado.
    - ECU -> tela (getter 0x434FFC): `valor_na_tela = f_42A52C(SD[0x29C0])`.
    - Usa funcoes auxiliares do .exe: 0x42A52C, 0x42A698 (tabelas de conversao; ver docs/progbase/abas/_funcoes_auxiliares.json) `[OBS-COD]`.
  - Familia demais: vetor **TEMP_GAS_CAMBIO** (SerialCode 117, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x4367FC): `valor_bruto = f_42A698(v)` com `v` = valor digitado.
    - ECU -> tela (getter 0x43500E): `valor_na_tela = f_42A52C(SD[0x29C0])`.
    - Usa funcoes auxiliares do .exe: 0x42A52C, 0x42A698 (tabelas de conversao; ver docs/progbase/abas/_funcoes_auxiliares.json) `[OBS-COD]`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- TEMP_GAS_CAMBIO (SC 117): bruto 182; na tela: **5.0** `[OBS-FIO, tela DERIVADO]` (LOG linha 1107838).

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
  - valor atual da ECU do dono: tela = 5.0 -> quadro `12 75 00 B6 3D`.
  - maximo da faixa do DFM: tela = 120.0 -> quadro `12 75 00 09 90`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado por GetFlag1(caso 9)` em `0x4B8E6B` (CheckTempRiduttoreClick; metodo de OUTRA aba/tela) — incondicional

### `EditRitMin` — Atraso na passagem gasolina/gás

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 1269 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelRitardoCambio` (associacao INFERIDO)): ita = "Ritardo passaggio benzina-gas"; eng = "Switch-over from petrol to gas delay"; por = "Atraso na passagem gasolina/gás" `[OBS-COD]`.
- Unidade ao lado: **s** `[OBS-COD]` (rotulo `LabelS1`).
- Posicao no DFM: Left=548 Top=43 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 240.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTRITARDOCAMBIO`: "Management of the petrol-gas switch delay is based on the engine water temperature. \ It can be detected by the sensor on the reducer heating pipe or directly on the original sensor of the engine. \ This delay can be managed by a string in which it is possible to associate a delay time with each given temperature. \ The ECU detects the instantaneous water temperature with the ignition on, enabling the petrol-gas switch when the set delay has elapsed and the conditions on the type of switch and on the revs threshold are verified".

**O que isso faz (linguagem simples)**

- Atraso minimo (35 a 240 s) depois da partida antes de passar de gasolina para gas. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.13).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 13 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **RITARDO_CAMBIO** (SerialCode 17, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x4369FF): `valor_bruto = trunc(v)` com `v` = valor digitado.
    - ECU -> tela (getter 0x435161): `valor_na_tela = SD[0x2AF0]`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- RITARDO_CAMBIO (SC 17): bruto 35; na tela: **35.0** `[OBS-FIO, tela DERIVADO]` (LOG linha 1105436).

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
  - valor atual da ECU do dono: tela = 35.0 -> quadro `12 11 00 23 46`.
  - maximo da faixa do DFM: tela = 240.0 -> quadro `12 11 00 F0 13`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditTInjBenzMaxPerCambio` — T.inj gasolina máximo para mudança gás

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 1288 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTInjBenzMaxPerCambio` (associacao INFERIDO)): ita = "Tinj benzina massimo per cambio gas"; eng = "Max petrol inj. time for gas change-over"; por = "T.inj gasolina máximo para mudança gás" `[OBS-COD]`.
- Unidade ao lado: **ms** `[OBS-COD]` (rotulo `LabelMs6`).
- Posicao no DFM: Left=548 Top=583 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 2.0, maximo 20.0, passo (Precision) 0.02. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTTINJBENZMAXPERCAMBIO`: "This parameter is the petrol injection time value above which the switch to gas is temporarily prohibited. \ In this way abrupt switches to gas during acceleration are prevented".

**O que isso faz (linguagem simples)**

- Tempo de injecao de gasolina acima do qual a troca para gas fica temporariamente proibida (motor muito exigido). `[OBS-COD]` (fonte: dica do proprio ProgBase (HINTTINJ...)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 64 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **T_INJ_BENZ_MAX_CAMBIO** (SerialCode 146, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x4368F0): `valor_bruto = trunc(((v / ((SD[0x2980] * 0.00025) * 0.004)) + 0.5))` com `v` = valor digitado.
    - ECU -> tela (getter 0x43506C): `valor_na_tela = (((SD[0x2980] * 0.00025) * 0.004) * SD[0x2AA8])`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- T_INJ_BENZ_MAX_CAMBIO (SC 146): bruto 4687; na tela: **11.99872** `[OBS-FIO, tela DERIVADO]` (LOG linha 1108654).

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
  - valor atual da ECU do dono: tela = 11.99872 -> quadro `13 92 00 4F 12 06`.
  - maximo da faixa do DFM: tela = 20.0 -> quadro `13 92 00 85 1E 48`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `ButtonReset` — Redefina a central de comando e retorne aos parâmetros de base

Tipo: **botao** (`TButton`), DFM linha 1310 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Azzera centralina e ritorna ai parametri di base"; eng = "Reset ECU and load default parameters"; por = "Redefina a central de comando e retorne aos parâmetros de base" `[OBS-COD]`.
- Posicao no DFM: Left=39 Top=638 Largura=748 Altura=38; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTBUTTONRESET`: "Reset ECU and load default parameters".

**O que isso faz (linguagem simples)**

- Apaga todos os parametros e volta aos parametros de base da central; o programa pede confirmacao antes. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.14).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `ButtonResetClick` (0x4BA188) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Botao: sem escrita de valor padrao. O que o handler faz esta na lista de acessos acima; bytes especificos so quando o handler chama `EnviaComando`/setter `[OBS-COD]` (nao executado aqui) -> **DESCONHECIDO** o quadro exato quando nao listado.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `ComboCarburante` — Tipo carburante

Tipo: **combo** (`TKeyComboBox`), DFM linha 1329 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelCarburante` (associacao INFERIDO)): ita = "Tipo carburante"; eng = "-"; por = "-" `[OBS-COD]`.
- Unidade ao lado: **Motore** `[OBS-COD]` (rotulo `LabelTurbo`).
- Posicao no DFM: Left=372 Top=-661 Largura=157 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Opcoes da lista `[OBS-COD]`: 0 = "GPL" / GLP; 1 = "Metano".
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTCARBURANTE`: "The function allows to select the fuel used, LPG or CNG".

**O que isso faz (linguagem simples)**

- Escolhe o combustivel usado: GLP ou metano (GNV). `[OBS-COD]` (fonte: manual SW 2.16.4 C p.10).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 0, mascara 0x0002, caso do setter 1, papel "seletor (TKeyComboBox)", condicao "sempre"; origem: flags-do-original.json.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 1, mascara 0x0020, caso do setter 16, papel "None", condicao "sempre"; origem: handler ComboCarburanteKeyPress.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 0 = 0x2486 -> bit 0x0002: **LIGADO** `[OBS-FIO]` (LOG linha 1102844).
- FLAG_CONF1 palavra 1 = 0x1051 -> bit 0x0020: **desligado** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `ComboCarburanteKeyPress` (0x4B96DC) `[OBS-COD]`
  - muda Text de um controle nao identificado pela analise estatica em `0x4B974E`
  - SetFlag1 caso 1 -> FLAG_CONF1 palavra 0 mascara 0x0002 em `0x4B97E6`
  - chama o metodo do TStreamDati `0x43362C` em `0x4B9826` (le: -; escreve: -)
  - GetCombo em `0x4B9836`
  - GetFlag1 caso 16 -> FLAG_CONF1 palavra 1 mascara 0x0020 em `0x4B9845`
  - GetFlag1 caso 1 -> FLAG_CONF1 palavra 0 mascara 0x0002 em `0x4B9856`
  - muda Caption de um controle nao identificado pela analise estatica em `0x4B98BA`
  - muda Caption de um controle nao identificado pela analise estatica em `0x4B98D1`
  - chama o metodo do TStreamDati `0x439E94` em `0x4B99A7` (le: AebConnection, TimerIO; escreve: -)
  - GetCombo em `0x4B99E9`
  - GetCombo em `0x4B9A00`
  - GetCombo em `0x4B9A17`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 0 mascara 0x0002): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - desligar bit (FLAG_CONF1 palavra 0 mascara 0x0002): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - ligar bit (FLAG_CONF1 palavra 1 mascara 0x0020): 2486 1051 -> 2486 1071, quadro `35 03 00 86 24 71 10 63`.
  - desligar bit (FLAG_CONF1 palavra 1 mascara 0x0020): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Cada opcao da lista chama o metodo `0x43362C` `[INFERIDO]` (convencao assumida):
  - opcao 0 "GPL" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x433632.
  - opcao 1 "Metano" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x433632.
- Cada opcao da lista chama o metodo `0x439E94` `[INFERIDO]` (convencao assumida):
  - opcao 0 "GPL" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x439ecb.
  - opcao 1 "Metano" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x439ecb.
  - Estrutura: igual a acima. Fonte: metodo 0x43362C executado no Unicorn com edx = indice da opcao (convencao assumida; o handler ComboCarburanteKeyPress o chama em 0x4B9826)
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado` em `0x4B97B3` (ComboCarburanteKeyPress) — SE tecla == Enter E sub_754CCC == 0 E cmp eax, ecx !=  E sub_A4EDD0 != 0
- `Checked/ItemIndex := calculado por GetFlag1(caso 1)` em `0x4A6B09` (TabCambioShow) — incondicional

### `EditCilindrata` — Cilindrata (cc)

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 1352 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelCilindrata` (associacao INFERIDO)): ita = "Cilindrata (cc)"; eng = "-"; por = "-" `[OBS-COD]`.
- Unidade ao lado: **Inj.** `[OBS-COD]` (rotulo `LabelFullGroup`).
- Posicao no DFM: Left=457 Top=-572 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 900.0, maximo 8000.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTCILINDRATA`: "The function allows to set the displacement of the car in cm3. \ On the base of this value and number of cylinders, the software load \ a default configuration from which autocalibration will start.".

**O que isso faz (linguagem simples)**

- Cilindrada do motor em cm3 (900 a 8000); o programa calcula a cilindrada por cilindro e manda um mapa-base para a calibracao partir de um ponto razoavel. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.11).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 34 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **CILINDRATA** (SerialCode 75, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x436DD9): `valor_bruto = trunc((v + 0.5))` com `v` = valor digitado.
    - ECU -> tela (getter 0x4355BC): `valor_na_tela = SD[0x2D64]`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- CILINDRATA (SC 75): bruto 1000; na tela: **1000.0** `[OBS-FIO, tela DERIVADO]` (LOG linha 1106442).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditRifLambdaKeyPress` (0x4A6A00) `[OBS-COD]`
  - SetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A45` (handler generico)
  - GetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A64` (handler generico)
  - SetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A99` (handler generico)
  - GetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6AB1` (handler generico)

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Gravar valor (executado no Unicorn com o codigo original; `[OBS-COD]`):
  - valor atual da ECU do dono: tela = 1000.0 -> quadro `13 4B 00 E8 03 49`.
  - maximo da faixa do DFM: tela = 8000.0 -> quadro `13 4B 00 40 1F BD`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `DrawGridPassaggio` — (seg)

Tipo: **grade** (`TDrawGrid`), DFM linha 1371 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelPassaggioSecondi` (associacao INFERIDO)): ita = "(sec)"; eng = "(seconds)"; por = "(seg)" `[OBS-COD]`.
- Posicao no DFM: Left=114 Top=85 Largura=590 Altura=68; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Tabela de espera: quantos segundos o sistema aguarda, conforme a temperatura da agua do motor, antes de passar da gasolina para o gas; cada celula pode ser mudada em modo absoluto, linear ou percentual. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.13).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `DrawGridPassaggioKeyPress` (0x4B3118) `[OBS-COD]`
- `OnDblClick` -> `DrawGridPassaggioDblClick` (0x4B3128) `[OBS-COD]`
- `OnKeyDown` -> `DrawGridKKeyDown` (0x4B2514) `[OBS-COD]`
- `OnDrawCell` -> `DrawGridPassaggioDrawCell` (0x4B2D64) `[OBS-COD]`: desenho das celulas da grade (exibicao); nao envia nada a ECU

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Grade: cada celula editada chama um setter indexado (SetIdx) do handler `KeyPress`/`DblClick`; quadros por celula: `14 SC lo hi idx valor ck` (SetNumber+indice). Quadros exatos por celula: **DESCONHECIDO** (nao executados aqui).

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `DrawGridPassaggioGas` — (sec)

Tipo: **grade** (`TDrawGrid`), DFM linha 1399 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelPassaggioGasSecondi` (associacao INFERIDO)): ita = "(sec)"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=113 Top=186 Largura=593 Altura=68; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Tabela de espera pela temperatura do gas (usada quando nao ha sensor de agua): segundos de atraso antes de passar da gasolina para o gas. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.13).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `DrawGridPassaggioGasKeyPress` (0x4B3A14) `[OBS-COD]`
- `OnDblClick` -> `DrawGridPassaggioGasDblClick` (0x4B3A24) `[OBS-COD]`
- `OnKeyDown` -> `DrawGridKKeyDown` (0x4B2514) `[OBS-COD]`
- `OnDrawCell` -> `DrawGridPassaggioGasDrawCell` (0x4B3660) `[OBS-COD]`: desenho das celulas da grade (exibicao); nao envia nada a ECU

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Grade: cada celula editada chama um setter indexado (SetIdx) do handler `KeyPress`/`DblClick`; quadros por celula: `14 SC lo hi idx valor ck` (SetNumber+indice). Quadros exatos por celula: **DESCONHECIDO** (nao executados aqui).

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `ComboTaglia` — Taglia

Tipo: **combo** (`TKeyComboBox`), DFM linha 1427 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTaglia` (associacao INFERIDO)): ita = "Taglia"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=659 Top=-616 Largura=129 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Opcoes da lista `[OBS-COD]`: 0 = "XS"; 1 = "S"; 2 = "M"; 3 = "L"; 4 = "XL"; 5 = "XXL".

**O que isso faz (linguagem simples)**

- Tamanho do injetor instalado (pequeno, medio ou grande nos Landi), conforme a potencia por cilindro. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.10).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 0, mascara 0x0002, caso do setter 1, papel "None", condicao "sempre"; origem: handler ComboTagliaKeyPress.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 0 = 0x2486 -> bit 0x0002: **LIGADO** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `ComboTagliaKeyPress` (0x4BCD94) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x43363C` em `0x4BCDE2` (le: TAGLIA_INIETTORE, TAGLIA_INIETTORE_LR; escreve: -)
  - chama o metodo do TStreamDati `0x43400C` em `0x4BCDFF` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x43362C` em `0x4BCE2B` (le: -; escreve: -)
  - GetCombo em `0x4BCE3F`
  - GetFlag1 caso 1 -> FLAG_CONF1 palavra 0 mascara 0x0002 em `0x4BCE53`
  - chama o metodo do TStreamDati `0x439358` em `0x4BCE5A` (le: INJR_GAS_FLOW, INJR_TOFS_PTR_H, INJR_TOFS_PTR_V, INJR_TOFS_TBL, NORM_PRESS, NORM_TEMP; escreve: CORRENTE_MANTENIMENTO, TEMPO_GAS)
  - chama o metodo do TStreamDati `0x42B834` em `0x4BCF11` (le: AebConnection; escreve: -)

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 0 mascara 0x0002): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - desligar bit (FLAG_CONF1 palavra 0 mascara 0x0002): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Cada opcao da lista chama o metodo `0x43363C` `[INFERIDO]` (convencao assumida):
  - opcao 0 "XS" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x433647.
  - opcao 1 "S" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x433647.
  - opcao 2 "M" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x433647.
  - opcao 3 "L" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x433647.
  - opcao 4 "XL" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x433647.
  - opcao 5 "XXL" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x433647.
- Cada opcao da lista chama o metodo `0x43400C` `[INFERIDO]` (convencao assumida):
  - opcao 0 "XS" -> quadro(s) `(nenhum)`.
  - opcao 1 "S" -> quadro(s) `(nenhum)`.
  - opcao 2 "M" -> quadro(s) `(nenhum)`.
  - opcao 3 "L" -> quadro(s) `(nenhum)`.
  - opcao 4 "XL" -> quadro(s) `(nenhum)`.
  - opcao 5 "XXL" -> quadro(s) `(nenhum)`.
- Cada opcao da lista chama o metodo `0x43362C` `[INFERIDO]` (convencao assumida):
  - opcao 0 "XS" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x433632.
  - opcao 1 "S" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x433632.
  - opcao 2 "M" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x433632.
  - opcao 3 "L" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x433632.
  - opcao 4 "XL" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x433632.
  - opcao 5 "XXL" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x433632.
- Cada opcao da lista chama o metodo `0x439358` `[INFERIDO]` (convencao assumida):
  - opcao 0 "XS" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x439c95.
  - opcao 1 "S" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x439c95.
  - opcao 2 "M" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x439c95.
  - opcao 3 "L" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x439c95.
  - opcao 4 "XL" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x439c95.
  - opcao 5 "XXL" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x439c95.
- Cada opcao da lista chama o metodo `0x42B834` `[INFERIDO]` (convencao assumida):
  - opcao 0 "XS" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x42b848.
  - opcao 1 "S" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x42b848.
  - opcao 2 "M" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x42b848.
  - opcao 3 "L" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x42b848.
  - opcao 4 "XL" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x42b848.
  - opcao 5 "XXL" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x42b848.
  - Estrutura: igual a acima. Fonte: metodo 0x43363C executado no Unicorn com edx = indice da opcao (convencao assumida; o handler ComboTagliaKeyPress o chama em 0x4BCDE2)
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por chamada virtual dword ptr [ecx + 0x60]` em `0x4A74F6` (TabCambioShow) — incondicional

### `CheckSpegniBuzzer` — Buzzer a tempo

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 1444 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Buzzer a tempo"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=455 Top=-63 Largura=354 Altura=31; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTSPEGNIBUZZER`: "Time limited buzzer".

**O que isso faz (linguagem simples)**

- Buzzer com tempo: o alarme toca so 5 segundos quando o sistema volta para gasolina por falta de gas ou falha. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.13).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF2** (SerialCode 192), palavra 0, mascara 0x0100, caso do setter 8, papel "caixa (Checked)", condicao "sempre"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF2 palavra 0 = 0x0001 -> bit 0x0100: **desligado** `[OBS-FIO]` (LOG linha 1102816).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckSpegniBuzzerClick` (0x4C1C34) `[OBS-COD]`
  - SetFlag2 caso 8 -> FLAG_CONF2 palavra 0 mascara 0x0100 em `0x4C1C5D`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF2 palavra 0 mascara 0x0100): 0001 0000 -> 0101 0000, quadro `35 C0 00 01 01 00 00 F7`.
  - desligar bit (FLAG_CONF2 palavra 0 mascara 0x0100): 0001 0000 -> 0001 0000, quadro `35 C0 00 01 00 00 00 F6`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetFlag2(caso 8)` em `0x4A858F` (TabCambioShow) — incondicional
- `Checked/ItemIndex := calculado por GetFlag2(caso 8)` em `0x4907F6` (FormShow; metodo de OUTRA aba/tela) — incondicional

### `CheckPartenzaGasCaldo` — Partida gás com motor aquecido

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 1465 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Partenza a gas a motore caldo"; eng = "Start on gas with hot engine"; por = "Partida gás com motor aquecido" `[OBS-COD]`.
- Posicao no DFM: Left=38 Top=-148 Largura=484 Altura=26; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTPARTENZAGASCALDO`: "If the temperature of the engine when starting the car is over the set value, then start directly on gas".

**O que isso faz (linguagem simples)**

- Se a agua estiver acima da temperatura definida (50 a 90 graus C) na partida, o motor ja parte direto a gas. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.12).
- Manual SW 2.16.4 C, p.12 (linha 562 de sw.txt): "K M Start on gas with hot engine (range 50-90°C): select L the heading and set the water temperature above which the engine will be fed directly with gas at start-up. M" `[OBS-COD]`.

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF2** (SerialCode 192), palavra 0, mascara 0x0002, caso do setter 1, papel "caixa (Checked)", condicao "sempre"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF2 palavra 0 = 0x0001 -> bit 0x0002: **desligado** `[OBS-FIO]` (LOG linha 1102816).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckPartenzaGasCaldoClick` (0x4C4FB8) `[OBS-COD]`
  - SetFlag2 caso 1 -> FLAG_CONF2 palavra 0 mascara 0x0002 em `0x4C4FE0`
  - muda Visible de `EditTempGasCaldo` em `0x4C502D`
  - muda Visible de `LabelT4` em `0x4C5041`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF2 palavra 0 mascara 0x0002): 0001 0000 -> 0003 0000, quadro `35 C0 00 03 00 00 00 F8`.
  - desligar bit (FLAG_CONF2 palavra 0 mascara 0x0002): 0001 0000 -> 0001 0000, quadro `35 C0 00 01 00 00 00 F6`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetFlag2(caso 1)` em `0x4A861F` (TabCambioShow) — incondicional
- `Checked/ItemIndex := calculado por GetFlag2(caso 1)` em `0x4907BA` (FormShow; metodo de OUTRA aba/tela) — incondicional

### `EditTempGasCaldo` — EditTempGasCaldo

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 1482 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `None` (associacao None)): ita = "-"; eng = "-"; por = "-" `[DESCONHECIDO]`.
- Unidade ao lado: **°C** `[OBS-COD]` (rotulo `LabelT4`).
- Posicao no DFM: Left=548 Top=-153 Largura=73 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 50.0, maximo 90.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Temperatura da agua (50 a 90 graus C) acima da qual a partida e feita direto a gas. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.12).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 100 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **TEMP_GAS_CALDO** (SerialCode 195, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x436840): `valor_bruto = f_42A8F4(v)` com `v` = valor digitado.
    - ECU -> tela (getter 0x435032): `valor_na_tela = f_42A788(SD[0x29C8])`.
    - Usa funcoes auxiliares do .exe: 0x42A788, 0x42A8F4 (tabelas de conversao; ver docs/progbase/abas/_funcoes_auxiliares.json) `[OBS-COD]`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- TEMP_GAS_CALDO (SC 195): bruto 50; na tela: **60.0** `[OBS-FIO, tela DERIVADO]` (LOG linha 1109462).

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
  - valor atual da ECU do dono: tela = 60.0 -> quadro `12 C3 00 32 07`.
  - maximo da faixa do DFM: tela = 90.0 -> quadro `12 C3 00 15 EA`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := verdadeiro` em `0x4C502D` (CheckPartenzaGasCaldoClick) — incondicional
- `Visible := verdadeiro` em `0x4A886E` (TabCambioShow) — incondicional

### `CheckStartAndStop` — Start && Stop

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 1509 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Start && Stop"; eng = "Start && Stop"; por = "Start && Stop" `[OBS-COD]`.
- Posicao no DFM: Left=258 Top=-63 Largura=192 Altura=31; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTSTARTANDSTOP`: "This strategy must be enable on Start&Stop vehicles and also on vehicles in which RPM signal get loose during cutoff state. \ After lossing of RPM signal system still operates in gas mode (green switch) until the +15V on white/red wire is present. \ The key-on signal (white/red wire) must be present even when the engine is off and ignition key is turned on. \ In case of accident the inertial sensor must disable this signal.".

**O que isso faz (linguagem simples)**

- Para carros Start & Stop: o sistema segue funcionando quando o motor para e o sinal de rotacao some, e o motor religa a gas sem demora. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.12).
- Manual SW 2.16.4 C, p.12 (linha 576 de sw.txt): "O Start & Stop: with this parameter enabled, the system N continues to operate despite the loss of rpm signals reco- O gnising only the ignition ON as the operating condition (15). Allows rpm signals to be read even while connected to the" `[OBS-COD]`.

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF2** (SerialCode 192), palavra 0, mascara 0x1000, caso do setter 12, papel "caixa (Checked)", condicao "sempre"; origem: flags-do-original.json.
- Bit de configuracao `[OBS-COD]`: **MASK_FUNCTION** (SerialCode 194), palavra 28, mascara 0x0040, caso do setter None, papel "dependente (Enabled)", condicao "obj+0x2A64!=0"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF2 palavra 0 = 0x0001 -> bit 0x1000: **desligado** `[OBS-FIO]` (LOG linha 1102816).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckStartAndStopClick` (0x4C2BD8) `[OBS-COD]`
  - SetFlag2 caso 12 -> FLAG_CONF2 palavra 0 mascara 0x1000 em `0x4C2C15`
  - muda Text de um controle nao identificado pela analise estatica em `0x4C2C45`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF2 palavra 0 mascara 0x1000): 0001 0000 -> 1001 0000, quadro `35 C0 00 01 10 00 00 06`.
  - desligar bit (FLAG_CONF2 palavra 0 mascara 0x1000): 0001 0000 -> 0001 0000, quadro `35 C0 00 01 00 00 00 F6`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetFlag2(caso 12)` em `0x4A85E3` (TabCambioShow) — incondicional
- `Checked/ItemIndex := calculado por GetFlag2(caso 12)` em `0x490832` (FormShow; metodo de OUTRA aba/tela) — incondicional

### `EditSogliaGiri` — Soglia

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 1524 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelSogliaGiri` (associacao INFERIDO)): ita = "Soglia"; eng = "-"; por = "-" `[OBS-COD]`.
- Unidade ao lado: **V** `[OBS-COD]` (rotulo `LabSogliaGiri`).
- Posicao no DFM: Left=681 Top=-530 Largura=71 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 5.0, passo (Precision) 0.01. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTSOGLIAGIRI`: "Threshold of RPM signal".

**O que isso faz (linguagem simples)**

- Limiar de tensao do sinal de rotacao (usado quando o tipo de sinal e "Personalizado"). `[OBS-COD]` (fonte: dica do proprio ProgBase (HINTSOGLIAGIRI)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditSogliaGiriKeyPress` (0x4C58DC) `[OBS-COD]`
  - SetEsc codigo 106 -> vetor SOGLIA_LETTURA_GIRI_LR (SC 214) em `0x4C592B`
  - GetEsc codigo 106 -> vetor SOGLIA_LETTURA_GIRI_LR (SC 214) em `0x4C593D`
- `OnEnter` -> `EditGiriCambioEnter` (0x4A98D8) `[OBS-COD]`
- `OnExit` -> `EditGiriCambioExit` (0x4A9CC0) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4ACEC6` (ComboSegnaleGiriKeyPress) — SE eax == 0xd E sub_754CCC == 0 E [obj+0x2BD4] != 0 E SD.familia_LR[+0x80D] != 0
- `Visible := falso` em `0x4ACF36` (ComboSegnaleGiriKeyPress) — SE eax == 0xd E sub_754CCC == 0 E SD.familia_LR[+0x80D] == 0
- `Visible := calculado` em `0x4A7BCD` (TabCambioShow) — SE [obj+0x2BD4] != 0 E SD.familia_LR[+0x80D] != 0
- `Visible := falso` em `0x4A7C41` (TabCambioShow) — SE SD.familia_LR[+0x80D] == 0

### `EditIstantaneoValue` — Num. iniettate tra cilindri per cambio gas

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 1549 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelIstantaneoValue` (associacao INFERIDO)): ita = "Num. iniettate tra cilindri per cambio gas"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=548 Top=324 Largura=72 Altura=37; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 15.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTISTANTANEOVALUE`: "Number of engine cycles for petrol-gas changeover. \ Use an higher value if the change-over is too rough. \ If manual gas injectors sequence is enalbled, this parameter has to be set to zero.".

**O que isso faz (linguagem simples)**

- Numero de ciclos do motor para passar de gasolina a gas; aumente se a troca estiver brusca. `[OBS-COD]` (fonte: dica do proprio ProgBase (HINTISTANTANEOVALUE)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 111, indice 2 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **CHANGE_OVER_CILYNDER_DELAY** (SerialCode 226, TAebVector, indexado) `[OBS-COD]`.
    - tela -> ECU (setter 0x437879): `valor_bruto = trunc(v)` com `v` = valor digitado.
    - ECU -> tela (getter 0x436114): `valor_na_tela = SD[0x2CD4 + i*4]`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- CHANGE_OVER_CILYNDER_DELAY (SC 226): bruto [0, 0, 0, 0] (elemento 2 = 0); na tela: **nao calculado** `[OBS-FIO, tela DERIVADO]` (LOG linha 1110534).

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
  - maximo da faixa do DFM: tela = 15.0 -> quadro `14 E2 00 02 0F 00 07`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `CheckFlexFuel` — Flex fuel

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 1575 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Flex fuel"; eng = "Flex fuel"; por = "Flex fuel" `[OBS-COD]`.
- Posicao no DFM: Left=258 Top=-34 Largura=167 Altura=31; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTFLEXFUEL`: "Enable flex fuel".

**O que isso faz (linguagem simples)**

- Liga o modo flex fuel. `[OBS-COD]` (fonte: dica do proprio ProgBase (HINTFLEXFUEL)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF2** (SerialCode 192), palavra 1, mascara 0x0100, caso do setter 16, papel "caixa (Checked)", condicao "sempre"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF2 palavra 1 = 0x0000 -> bit 0x0100: **desligado** `[OBS-FIO]` (LOG linha 1102816).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckFlexFuelClick` (0x4C2C9C) `[OBS-COD]`
  - SetFlag2 caso 16 -> FLAG_CONF2 palavra 1 mascara 0x0100 em `0x4C2CC5`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF2 palavra 1 mascara 0x0100): 0001 0000 -> 0001 0100, quadro `35 C0 00 01 00 00 01 F7`.
  - desligar bit (FLAG_CONF2 palavra 1 mascara 0x0100): 0001 0000 -> 0001 0000, quadro `35 C0 00 01 00 00 00 F6`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetFlag2(caso 16)` em `0x4A8601` (TabCambioShow) — incondicional
- `Checked/ItemIndex := calculado por GetFlag2(caso 16)` em `0x490850` (FormShow; metodo de OUTRA aba/tela) — incondicional

### `EditCicchettoInj` — Gas injection time in priming

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 1590 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelCicchettoInj` (associacao INFERIDO)): ita = "Tempo di iniezione gas in cicchetto"; eng = "Gas injection time in priming"; por = "Gas injection time in priming" `[OBS-COD]`.
- Unidade ao lado: **ms** `[OBS-COD]` (rotulo `LabCicchettoInj`).
- Posicao no DFM: Left=548 Top=417 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 2.0, maximo 10.0, passo (Precision) 0.01. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Tempo de injecao do gas na escorva (cicchetto). [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditAdvParamInjGasKeyPress` (0x4C7150) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x432BF4` em `0x4C7194` (le: ADV_PARAM_INJ; escreve: -)
  - chama o metodo do TStreamDati `0x432BE4` em `0x4C71A7` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x432BF4` em `0x4C71E6` (le: ADV_PARAM_INJ; escreve: -)
  - chama o metodo do TStreamDati `0x432BE4` em `0x4C71F9` (le: -; escreve: -)
- `OnEnter` -> `EditGiriCambioEnter` (0x4A98D8) `[OBS-COD]`
- `OnExit` -> `EditGiriCambioExit` (0x4A9CC0) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4A93B4` (ComboCambioKeyPress, ComboCambioKeyPress via sub_4A90B8) — SE tecla == Enter E sub_754CCC == 0
- `Visible := calculado` em `0x4A8406` (TabCambioShow) — incondicional

### `EditCicchettoNum` — Number of gas injections in priming

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 1616 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelCicchettoNum` (associacao INFERIDO)): ita = "Numero iniettate gas in cicchetto"; eng = "Number of gas injections in priming"; por = "Number of gas injections in priming" `[OBS-COD]`.
- Posicao no DFM: Left=548 Top=459 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 5.0, maximo 50.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Quantidade de injecoes de gas na escorva (cicchetto). [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditAdvParamInjGasKeyPress` (0x4C7150) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x432BF4` em `0x4C7194` (le: ADV_PARAM_INJ; escreve: -)
  - chama o metodo do TStreamDati `0x432BE4` em `0x4C71A7` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x432BF4` em `0x4C71E6` (le: ADV_PARAM_INJ; escreve: -)
  - chama o metodo do TStreamDati `0x432BE4` em `0x4C71F9` (le: -; escreve: -)
- `OnEnter` -> `EditGiriCambioEnter` (0x4A98D8) `[OBS-COD]`
- `OnExit` -> `EditGiriCambioExit` (0x4A9CC0) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4A9422` (ComboCambioKeyPress, ComboCambioKeyPress via sub_4A90B8) — SE tecla == Enter E sub_754CCC == 0
- `Visible := calculado` em `0x4A847D` (TabCambioShow) — incondicional

### `CheckGiriAlberoCamme` — albero a camme

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 1642 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "albero a camme"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=205 Top=-484 Largura=205 Altura=31; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Usa o sinal do arvore de comando (came) para medir a rotacao. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF3** (SerialCode 250), palavra 0, mascara 0x2000, caso do setter 10, papel "None", condicao "sempre"; origem: handler CheckGiriAlberoCammeClick.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF3 palavra 0 = 0x0000 -> bit 0x2000: **desligado** `[OBS-FIO]` (LOG linha 1102872).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckGiriAlberoCammeClick` (0x4C850C) `[OBS-COD]`
  - SetFlag3 caso 10 -> FLAG_CONF3 palavra 0 mascara 0x2000 em `0x4C8549`
  - SetEsc codigo 124 -> vetor NUM_DENTI_ALBERO_CAMME (SC 249) em `0x4C8571`
  - SetEsc codigo 124 -> vetor NUM_DENTI_ALBERO_CAMME (SC 249) em `0x4C858A`
  - chama o metodo do TStreamDati `0x42B834` em `0x4C859B` (le: AebConnection; escreve: -)
  - muda Visible de `LabelNumDenti` em `0x4C8624`
  - muda Visible de `EditNumDenti` em `0x4C866D`
  - GetEsc codigo 124 -> vetor NUM_DENTI_ALBERO_CAMME (SC 249) em `0x4C867D`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF3 palavra 0 mascara 0x2000): 0000 0000 -> 2000 0000, quadro `35 FA 00 00 20 00 00 4F`.
  - desligar bit (FLAG_CONF3 palavra 0 mascara 0x2000): 0000 0000 -> 0000 0000, quadro `35 FA 00 00 00 00 00 2F`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado` em `0x4C86F5` (CheckGiriRuotaFonicaClick) — SE [obj+0x1280] == 0
- `Checked/ItemIndex := calculado` em `0x4C5656` (CheckSegnaleGiriClick) — incondicional
- `Visible := calculado por GetFlag3(caso 10)` em `0x4A76DA` (TabCambioShow) — incondicional
- `Checked/ItemIndex := calculado` em `0x4A775A` (TabCambioShow) — incondicional

### `EditNumDenti` — n. denti

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 1659 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelNumDenti` (associacao INFERIDO)): ita = "n. denti"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=714 Top=-487 Largura=73 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 1.0, maximo 60.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Numero de dentes da roda do comando usada para medir a rotacao. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 124 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **NUM_DENTI_ALBERO_CAMME** (SerialCode 249, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x437284): `valor_bruto = trunc(v)` com `v` = valor digitado.
    - ECU -> tela (getter 0x435BD2): `valor_na_tela = SD[0x2BDC]`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- NUM_DENTI_ALBERO_CAMME (SC 249): bruto 0; na tela: **0.0** `[OBS-FIO, tela DERIVADO]` (LOG linha 1110980).

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
  - valor atual da ECU do dono: tela = 0.0 -> quadro `12 F9 00 00 0B`.
  - maximo da faixa do DFM: tela = 60.0 -> quadro `12 F9 00 3C 47`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4C866D` (CheckGiriAlberoCammeClick) — incondicional
- `Visible := calculado` em `0x4C8842` (CheckGiriRuotaFonicaClick) — incondicional
- `Visible := calculado` em `0x4C5728` (CheckSegnaleGiriClick) — incondicional
- `Visible := calculado` em `0x4A782C` (TabCambioShow) — incondicional

### `EditRpmTachimetro` — Inserire i giri letti da tachimetro e premere RETURN

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 1745 `[OBS-COD]`. Pai: `PanelSequenceAcquisition`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelRpmTachimetro` (associacao INFERIDO)): ita = "Inserire i giri letti da tachimetro e premere RETURN"; eng = "-"; por = "-" `[OBS-COD]`.
- Unidade ao lado: **rpm** `[OBS-COD]` (rotulo `LabRpmTachimetro`).
- Posicao no DFM: Left=548 Top=42 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 500.0, maximo 3000.0, passo (Precision) 50.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Voce digita a rotacao que o conta-giros do carro mostra e aperta Enter, para o programa ajustar a leitura do sinal de rotacao. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditRpmTachimetroKeyPress` (0x4A647C) `[OBS-COD]`
  - muda Text de `EditRpmTachimetro` em `0x4A64D4`
  - envia comando direto ao protocolo (id 39, argumentos [5, 'eax']) em `0x4A6548`
  - muda Caption de `LabelRpmTachimetro` em `0x4A65D0`
  - muda Visible de `EditRpmTachimetro` em `0x4A65DD`
  - muda Visible de `LabRpmTachimetro` em `0x4A65EA`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := falso` em `0x4A6344` (ButtonSequenceAcquisitionClick) — SE sub_921FCC != 0 E [obj+0x69] != 0
- `Caption := calculado` em `0x4A63B5` (ButtonSequenceAcquisitionClick) — SE sub_921FCC != 0 E [obj+0x69] == 0
- `Visible := calculado por sub_A4EDD0` em `0x4A63D3` (ButtonSequenceAcquisitionClick) — SE sub_921FCC != 0 E [obj+0x69] == 0
- `Visible := falso` em `0x4A622D` (CheckInjAcquisitionClick) — incondicional
- `Text := calculado` em `0x4A64D4` (EditRpmTachimetroKeyPress) — SE tecla == Enter E sub_921FCC != 0
- `Visible := falso` em `0x4A65DD` (EditRpmTachimetroKeyPress) — SE tecla == Enter E sub_921FCC != 0 E EnviaComando != 0
- `Visible := falso` em `0x490B00` (FormShow; metodo de OUTRA aba/tela) — incondicional
- `Visible := falso` em `0x4A5EBC` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0x3dc](TabCambio) E sub_921FCC != 0 E [obj+0x1855] != 0 E EnviaComando != 0 E test byte ptr [ebp - 0x1ca], 0x40 != 

### `ButtonSequenceAcquisition` — Acquisisci sequenza

Tipo: **botao** (`TButton`), DFM linha 1769 `[OBS-COD]`. Pai: `PanelSequenceAcquisition`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Acquisisci sequenza"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=487 Top=1 Largura=300 Altura=38; visivel de projeto: sim; habilitado de projeto: False `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Inicia a aquisicao (leitura) da sequencia de injecao do motor. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `ButtonSequenceAcquisitionClick` (0x4A62A8) `[OBS-COD]`
  - envia comando direto ao protocolo (id 39, argumentos [2, 'eax']) em `0x4A6319`
  - muda Caption de `ButtonSequenceAcquisition` em `0x4A632A`
  - muda Visible de `LabelRpmTachimetro` em `0x4A6337`
  - muda Visible de `EditRpmTachimetro` em `0x4A6344`
  - muda Visible de `LabRpmTachimetro` em `0x4A6351`
  - muda Caption de `ButtonSequenceAcquisition` em `0x4A6367`
  - muda Caption de `LabelRpmTachimetro` em `0x4A6378`
  - muda Visible de `LabelRpmTachimetro` em `0x4A6385`
  - muda Caption de `EditRpmTachimetro` em `0x4A63B5`
  - muda Visible de `EditRpmTachimetro` em `0x4A63D3`
  - muda Visible de `LabRpmTachimetro` em `0x4A63F9`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Botao: sem escrita de valor padrao. O que o handler faz esta na lista de acessos acima; bytes especificos so quando o handler chama `EnviaComando`/setter `[OBS-COD]` (nao executado aqui) -> **DESCONHECIDO** o quadro exato quando nao listado.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Caption := calculado` em `0x4A632A` (ButtonSequenceAcquisitionClick) — SE sub_921FCC != 0 E [obj+0x69] != 0
- `Caption := calculado` em `0x4A6367` (ButtonSequenceAcquisitionClick) — SE sub_921FCC != 0 E [obj+0x69] == 0
- `Caption := calculado` em `0x4A6213` (CheckInjAcquisitionClick) — incondicional
- `Caption := calculado` em `0x490AE6` (FormShow; metodo de OUTRA aba/tela) — incondicional
- `Caption := calculado` em `0x4A5EA2` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0x3dc](TabCambio) E sub_921FCC != 0 E [obj+0x1855] != 0 E EnviaComando != 0 E test byte ptr [ebp - 0x1ca], 0x40 != 

### `CheckInjAcquisition` — Sequenza di iniezione manuale

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 1786 `[OBS-COD]`. Pai: `PanelSequenceAcquisition`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Sequenza di iniezione manuale"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=38 Top=1 Largura=442 Altura=42; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Marca para informar a sequencia de injecao manualmente em vez de adquirir. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF2** (SerialCode 192), palavra 1, mascara 0x2000, caso do setter 29, papel "caixa (Checked)", condicao "sempre"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF2 palavra 1 = 0x0000 -> bit 0x2000: **desligado** `[OBS-FIO]` (LOG linha 1102816).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckInjAcquisitionClick` (0x4A61AC) `[OBS-COD]`
  - SetFlag2 caso 29 -> FLAG_CONF2 palavra 1 mascara 0x2000 em `0x4A61E4`
  - muda Caption de `ButtonSequenceAcquisition` em `0x4A6213`
  - muda Visible de `LabelRpmTachimetro` em `0x4A6220`
  - muda Visible de `EditRpmTachimetro` em `0x4A622D`
  - muda Visible de `LabRpmTachimetro` em `0x4A623A`
  - envia comando direto ao protocolo (id 39, argumentos [2, 'ecx']) em `0x4A628C`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF2 palavra 1 mascara 0x2000): 0001 0000 -> 0001 2000, quadro `35 C0 00 01 00 00 20 16`.
  - desligar bit (FLAG_CONF2 palavra 1 mascara 0x2000): 0001 0000 -> 0001 0000, quadro `35 C0 00 01 00 00 00 F6`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por sub_43414C` em `0x4BA3FB` (ComboCilindriKeyPress) — SE eax == 0xd E sub_754CCC == 0
- `Checked/ItemIndex := calculado por chamada virtual dword ptr [edx + 0x104]` em `0x4C6936` (ComboFullGroupKeyPress) — SE eax == 0xd E sub_754CCC == 0 E cmp eax, dword ptr [ebx + 0x1258] != 
- `Checked/ItemIndex := calculado por GetFlag2(caso 29)` em `0x4A863D` (TabCambioShow) — incondicional
- `Checked/ItemIndex := calculado por GetFlag2(caso 29)` em `0x4908C8` (FormShow; metodo de OUTRA aba/tela) — incondicional

### `ComboFullGroup` — Full group

Tipo: **combo** (`TKeyComboBox`), DFM linha 1806 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelFullGroup` (associacao INFERIDO)): ita = "Inj."; eng = "Full group"; por = "Full group" `[OBS-COD]`.
- Posicao no DFM: Left=596 Top=-572 Largura=194 Altura=37; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Opcoes da lista `[OBS-COD]`: 0 = "Sequenziale"; 1 = "Full group"; 2 = "Half group"; 3 = "Half group x1".

**O que isso faz (linguagem simples)**

- Escolhe o modo de grupo de injetores (full group). [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 0, mascara 0x0040, caso do setter 18, papel "None", condicao "sempre"; origem: handler ComboFullGroupKeyPress.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF2** (SerialCode 192), palavra 1, mascara 0x0040, caso do setter 27, papel "None", condicao "sempre"; origem: handler ComboFullGroupKeyPress.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 0 = 0x2486 -> bit 0x0040: **desligado** `[OBS-FIO]` (LOG linha 1102844).
- FLAG_CONF2 palavra 1 = 0x0000 -> bit 0x0040: **desligado** `[OBS-FIO]` (LOG linha 1102816).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `ComboFullGroupKeyPress` (0x4C6830) `[OBS-COD]`
  - SetFlag1 caso 18 -> FLAG_CONF1 palavra 0 mascara 0x0040 em `0x4C689D`
  - SetFlag2 caso 27 -> FLAG_CONF2 palavra 1 mascara 0x0040 em `0x4C68AD`
  - SetFlag1 caso 18 -> FLAG_CONF1 palavra 0 mascara 0x0040 em `0x4C68BF`
  - SetFlag2 caso 27 -> FLAG_CONF2 palavra 1 mascara 0x0040 em `0x4C68CF`
  - SetFlag1 caso 18 -> FLAG_CONF1 palavra 0 mascara 0x0040 em `0x4C68E1`
  - SetFlag2 caso 27 -> FLAG_CONF2 palavra 1 mascara 0x0040 em `0x4C68F1`
  - SetFlag1 caso 18 -> FLAG_CONF1 palavra 0 mascara 0x0040 em `0x4C6903`
  - SetFlag2 caso 27 -> FLAG_CONF2 palavra 1 mascara 0x0040 em `0x4C6913`
  - muda Visible de `PanelSequenceAcquisition` em `0x4C6960`
  - chama o metodo do TStreamDati `0x4348AC` em `0x4C696A` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x4310E8` em `0x4C6978` (le: cache de TIPO_ACCENS; escreve: -)
  - GetFlag1 caso 18 -> FLAG_CONF1 palavra 0 mascara 0x0040 em `0x4C698A`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 0 mascara 0x0040): 2486 1051 -> 24C6 1051, quadro `35 03 00 C6 24 51 10 83`.
  - desligar bit (FLAG_CONF1 palavra 0 mascara 0x0040): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - ligar bit (FLAG_CONF2 palavra 1 mascara 0x0040): 0001 0000 -> 0001 0040, quadro `35 C0 00 01 00 40 00 36`.
  - desligar bit (FLAG_CONF2 palavra 1 mascara 0x0040): 0001 0000 -> 0001 0000, quadro `35 C0 00 01 00 00 00 F6`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Cada opcao da lista chama o metodo `0x4348AC` `[INFERIDO]` (convencao assumida):
  - opcao 0 "Sequenziale" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x4348b2.
  - opcao 1 "Full group" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x4348b2.
  - opcao 2 "Half group" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x4348b2.
  - opcao 3 "Half group x1" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x4348b2.
- Cada opcao da lista chama o metodo `0x4310E8` `[INFERIDO]` (convencao assumida):
  - opcao 0 "Sequenziale" -> quadro(s) `(nenhum)`.
  - opcao 1 "Full group" -> quadro(s) `(nenhum)`.
  - opcao 2 "Half group" -> quadro(s) `(nenhum)`.
  - opcao 3 "Half group x1" -> quadro(s) `(nenhum)`.
  - Estrutura: igual a acima. Fonte: metodo 0x4348AC executado no Unicorn com edx = indice da opcao (convencao assumida; o handler ComboFullGroupKeyPress o chama em 0x4C696A)
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado` em `0x4A752A` (TabCambioShow) — SE GetFlag1(caso 18) == 0 E GetFlag2(caso 27) == 0
- `Checked/ItemIndex := calculado` em `0x4A7566` (TabCambioShow) — SE GetFlag2(caso 27) != 0 E GetFlag1(caso 18) != 0 E GetFlag2(caso 27) == 0
- `Checked/ItemIndex := calculado` em `0x4A75A2` (TabCambioShow) — SE GetFlag2(caso 27) != 0 E GetFlag2(caso 27) != 0 E GetFlag1(caso 18) == 0 E GetFlag2(caso 27) != 0
- `Checked/ItemIndex := calculado` em `0x4A75DB` (TabCambioShow) — SE GetFlag2(caso 27) != 0 E GetFlag2(caso 27) != 0 E GetFlag2(caso 27) == 0 E GetFlag1(caso 18) != 0 E GetFlag2(caso 27) != 0
- `Checked/ItemIndex := calculado` em `0x4A75EE` (TabCambioShow) — SE GetFlag2(caso 27) != 0 E GetFlag2(caso 27) != 0 E GetFlag2(caso 27) == 0 E GetFlag2(caso 27) == 0
- `Checked/ItemIndex := calculado` em `0x49099C` (FormShow; metodo de OUTRA aba/tela) — SE GetFlag1(caso 18) == 0 E GetFlag2(caso 27) == 0
- `Checked/ItemIndex := calculado` em `0x4909D8` (FormShow; metodo de OUTRA aba/tela) — SE GetFlag2(caso 27) != 0 E GetFlag1(caso 18) != 0 E GetFlag2(caso 27) == 0
- `Checked/ItemIndex := calculado` em `0x490A14` (FormShow; metodo de OUTRA aba/tela) — SE GetFlag2(caso 27) != 0 E GetFlag2(caso 27) != 0 E GetFlag1(caso 18) == 0 E GetFlag2(caso 27) != 0
- `Checked/ItemIndex := calculado` em `0x490A4D` (FormShow; metodo de OUTRA aba/tela) — SE GetFlag2(caso 27) != 0 E GetFlag2(caso 27) != 0 E GetFlag2(caso 27) == 0 E GetFlag1(caso 18) != 0 E GetFlag2(caso 27) != 0
- `Checked/ItemIndex := calculado` em `0x490A60` (FormShow; metodo de OUTRA aba/tela) — SE GetFlag2(caso 27) != 0 E GetFlag2(caso 27) != 0 E GetFlag2(caso 27) == 0 E GetFlag2(caso 27) == 0

### `ButtonSwitchSettings` — Switch settings

Tipo: **botao** (`TButton`), DFM linha 1828 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Switch settings"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=19 Top=-26 Largura=214 Altura=38; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Abre os ajustes do comutador (switch). [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `ButtonSwitchSettingsClick` (0x4C90E0) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x434AC8` em `0x4C9309` (le: -; escreve: -)
  - muda Text de um controle nao identificado pela analise estatica em `0x4C9349`
  - envia comando direto ao protocolo (id 0, argumentos [5, 'edx']) em `0x4C940E`
  - envia comando direto ao protocolo (id 0, argumentos [5, 'ecx']) em `0x4C94D3`
  - muda Text de um controle nao identificado pela analise estatica em `0x4C95BE`
  - muda Text de um controle nao identificado pela analise estatica em `0x4C9635`
  - muda Text de um controle nao identificado pela analise estatica em `0x4C9692`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Botao: sem escrita de valor padrao. O que o handler faz esta na lista de acessos acima; bytes especificos so quando o handler chama `EnviaComando`/setter `[OBS-COD]` (nao executado aqui) -> **DESCONHECIDO** o quadro exato quando nao listado.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `CheckCambioConSovrapp` — Cambio con sovrapposizione

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 1855 `[OBS-COD]`. Pai: `PanelCambio`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Cambio con sovrapposizione"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=38 Top=4 Largura=735 Altura=31; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Troca com sobreposicao: gasolina e gas abertos juntos durante a troca, para evitar irregularidade depois dela (nesta ECU o parametro nao respondeu no LOG). `[OBS-COD]` (fonte: manual SW 2.16.4 C p.14).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 0, mascara 0x0020, caso do setter 27, papel "dependente (Enabled)", condicao "sempre"; origem: flags-do-original.json.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF3** (SerialCode 250), palavra 0, mascara 0x0010, caso do setter 3, papel "caixa (Checked)", condicao "sempre"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 0 = 0x2486 -> bit 0x0020: **desligado** `[OBS-FIO]` (LOG linha 1102844).
- FLAG_CONF3 palavra 0 = 0x0000 -> bit 0x0010: **desligado** `[OBS-FIO]` (LOG linha 1102872).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckCambioConSovrappClick` (0x4C8B04) `[OBS-COD]`
  - SetFlag3 caso 3 -> FLAG_CONF3 palavra 0 mascara 0x0010 em `0x4C8B2C`
  - GetFlag3 caso 3 -> FLAG_CONF3 palavra 0 mascara 0x0010 em `0x4C8B3E`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 0 mascara 0x0020): 2486 1051 -> 24A6 1051, quadro `35 03 00 A6 24 51 10 63`.
  - desligar bit (FLAG_CONF1 palavra 0 mascara 0x0020): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - ligar bit (FLAG_CONF3 palavra 0 mascara 0x0010): 0000 0000 -> 0010 0000, quadro `35 FA 00 10 00 00 00 3F`.
  - desligar bit (FLAG_CONF3 palavra 0 mascara 0x0010): 0000 0000 -> 0000 0000, quadro `35 FA 00 00 00 00 00 2F`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetFlag3(caso 3)` em `0x4A865B` (TabCambioShow) — incondicional
- `Checked/ItemIndex := calculado por GetFlag3(caso 3)` em `0x4908AA` (FormShow; metodo de OUTRA aba/tela) — incondicional

### `EditParamProgress0` — EditParamProgress0

Tipo: **edit numerico (ValueEditEx)** (`TValueEditEx`), DFM linha 1872 `[OBS-COD]`. Pai: `PanelCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `None` (associacao None)): ita = "-"; eng = "-"; por = "-" `[DESCONHECIDO]`.
- Posicao no DFM: Left=548 Top=42 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo None, passo (Precision) None. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Numero da tabela de progressoes (troca gradual) - significado DESCONHECIDO. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF3** (SerialCode 250), palavra 0, mascara 0x0010, caso do setter 3, papel "dependente (Visible)", condicao "sempre"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF3 palavra 0 = 0x0000 -> bit 0x0010: **desligado** `[OBS-FIO]` (LOG linha 1102872).

**Ao tocar / usar, o que o programa faz (handlers)**

- Sem evento ligado a este controle no DFM (so exibicao).

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF3 palavra 0 mascara 0x0010): 0000 0000 -> 0010 0000, quadro `35 FA 00 10 00 00 00 3F`.
  - desligar bit (FLAG_CONF3 palavra 0 mascara 0x0010): 0000 0000 -> 0000 0000, quadro `35 FA 00 00 00 00 00 2F`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditParamProgress1` — EditParamProgress1

Tipo: **edit numerico (ValueEditEx)** (`TValueEditEx`), DFM linha 1896 `[OBS-COD]`. Pai: `PanelCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `None` (associacao None)): ita = "-"; eng = "-"; por = "-" `[DESCONHECIDO]`.
- Posicao no DFM: Left=548 Top=83 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo None, passo (Precision) None. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Numero da tabela de progressoes (troca gradual) - significado DESCONHECIDO. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- Sem evento ligado a este controle no DFM (so exibicao).

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditParamProgress3` — EditParamProgress3

Tipo: **edit numerico (ValueEditEx)** (`TValueEditEx`), DFM linha 1934 `[OBS-COD]`. Pai: `PanelCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `None` (associacao None)): ita = "-"; eng = "-"; por = "-" `[DESCONHECIDO]`.
- Posicao no DFM: Left=548 Top=166 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo None, passo (Precision) None. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Numero da tabela de progressoes (troca gradual) - significado DESCONHECIDO. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- Sem evento ligado a este controle no DFM (so exibicao).

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditParamProgress2` — EditParamProgress2

Tipo: **edit numerico (ValueEditEx)** (`TValueEditEx`), DFM linha 1972 `[OBS-COD]`. Pai: `PanelCambio`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `None` (associacao None)): ita = "-"; eng = "-"; por = "-" `[DESCONHECIDO]`.
- Posicao no DFM: Left=548 Top=125 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo None, passo (Precision) None. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Numero da tabela de progressoes (troca gradual) - significado DESCONHECIDO. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- Sem evento ligado a este controle no DFM (so exibicao).

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `CheckProgressioni` — Abilita progressioni

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 2128 `[OBS-COD]`. Pai: `PanelProgressioni`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Abilita progressioni"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=38 Top=4 Largura=689 Altura=31; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Liga a troca gradual de combustivel ("progressoes") nas situacoes da tabela (saida do corte, marcha lenta, troca...). [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF3** (SerialCode 250), palavra 0, mascara 0x0004, caso do setter 1, papel "dependente (Enabled)", condicao "sempre"; origem: flags-do-original.json.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF3** (SerialCode 250), palavra 0, mascara 0x0010, caso do setter 3, papel "caixa (Checked)", condicao "sempre"; origem: flags-do-original.json.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 0, mascara 0x0020, caso do setter 27, papel "None", condicao "sempre"; origem: handler CheckProgressioniClick.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF3 palavra 0 = 0x0000 -> bit 0x0004: **desligado** `[OBS-FIO]` (LOG linha 1102872).
- FLAG_CONF3 palavra 0 = 0x0000 -> bit 0x0010: **desligado** `[OBS-FIO]` (LOG linha 1102872).
- FLAG_CONF1 palavra 0 = 0x2486 -> bit 0x0020: **desligado** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckProgressioniClick` (0x4CA544) `[OBS-COD]`
  - SetFlag3 caso 3 -> FLAG_CONF3 palavra 0 mascara 0x0010 em `0x4CA570`
  - SetIdx codigo 111 -> vetor CHANGE_OVER_CILYNDER_DELAY (SC 226) em `0x4CA58D`
  - GetFlag3 caso 3 -> FLAG_CONF3 palavra 0 mascara 0x0010 em `0x4CA59E`
  - GetFlag1 caso 27 -> FLAG_CONF1 palavra 0 mascara 0x0020 em `0x4CA6A7`
  - chama o metodo do TStreamDati `0x43481C` em `0x4CA6B5` (le: -; escreve: -)
  - GetFlag3 caso 3 -> FLAG_CONF3 palavra 0 mascara 0x0010 em `0x4CA6D5`
  - GetIdx codigo 111 -> vetor CHANGE_OVER_CILYNDER_DELAY (SC 226) em `0x4CA709`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF3 palavra 0 mascara 0x0004): 0000 0000 -> 0004 0000, quadro `35 FA 00 04 00 00 00 33`.
  - desligar bit (FLAG_CONF3 palavra 0 mascara 0x0004): 0000 0000 -> 0000 0000, quadro `35 FA 00 00 00 00 00 2F`.
  - ligar bit (FLAG_CONF3 palavra 0 mascara 0x0010): 0000 0000 -> 0010 0000, quadro `35 FA 00 10 00 00 00 3F`.
  - desligar bit (FLAG_CONF3 palavra 0 mascara 0x0010): 0000 0000 -> 0000 0000, quadro `35 FA 00 00 00 00 00 2F`.
  - ligar bit (FLAG_CONF1 palavra 0 mascara 0x0020): 2486 1051 -> 24A6 1051, quadro `35 03 00 A6 24 51 10 63`.
  - desligar bit (FLAG_CONF1 palavra 0 mascara 0x0020): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetFlag3(caso 3)` em `0x4A8679` (TabCambioShow) — incondicional
- `Checked/ItemIndex := calculado por GetFlag3(caso 3)` em `0x490904` (FormShow; metodo de OUTRA aba/tela) — incondicional

### `DrawGridProgressioni` — Cambio

Tipo: **grade** (`TDrawGrid`), DFM linha 2143 `[OBS-COD]`. Pai: `PanelProgressioni`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelProgressRow2` (associacao INFERIDO)): ita = "Cambio"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=252 Top=70 Largura=368 Altura=132; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Tabela das progressoes: para cada situacao (saida do corte, minimo, troca, outras funcoes de gasolina), numeros de injecoes - significado de cada coluna DESCONHECIDO. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `DrawGridProgressioniKeyPress` (0x4CA990) `[OBS-COD]`
- `OnDblClick` -> `DrawGridProgressioniDblClick` (0x4CA9A0) `[OBS-COD]`
- `OnKeyDown` -> `DrawGridKKeyDown` (0x4B2514) `[OBS-COD]`
- `OnDrawCell` -> `DrawGridProgressioniDrawCell` (0x4CA74C) `[OBS-COD]`: desenho das celulas da grade (exibicao); nao envia nada a ECU

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Grade: cada celula editada chama um setter indexado (SetIdx) do handler `KeyPress`/`DblClick`; quadros por celula: `14 SC lo hi idx valor ck` (SetNumber+indice). Quadros exatos por celula: **DESCONHECIDO** (nao executados aqui).

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `CheckGiriRuotaFonica` — ruota fonica

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 2172 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "ruota fonica"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=420 Top=-484 Largura=174 Altura=31; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Usa a roda fonica (roda dentada) como fonte do sinal de rotacao. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF3** (SerialCode 250), palavra 0, mascara 0x2000, caso do setter 10, papel "caixa (Checked)", condicao "sempre"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF3 palavra 0 = 0x0000 -> bit 0x2000: **desligado** `[OBS-FIO]` (LOG linha 1102872).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckGiriRuotaFonicaClick` (0x4C86D4) `[OBS-COD]`
  - SetFlag3 caso 10 -> FLAG_CONF3 palavra 0 mascara 0x2000 em `0x4C871E`
  - SetEsc codigo 124 -> vetor NUM_DENTI_ALBERO_CAMME (SC 249) em `0x4C8747`
  - SetEsc codigo 124 -> vetor NUM_DENTI_ALBERO_CAMME (SC 249) em `0x4C875F`
  - chama o metodo do TStreamDati `0x42B834` em `0x4C8770` (le: AebConnection; escreve: -)
  - muda Visible de `LabelNumDenti` em `0x4C87F9`
  - muda Visible de `EditNumDenti` em `0x4C8842`
  - GetEsc codigo 124 -> vetor NUM_DENTI_ALBERO_CAMME (SC 249) em `0x4C8852`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF3 palavra 0 mascara 0x2000): 0000 0000 -> 2000 0000, quadro `35 FA 00 00 20 00 00 4F`.
  - desligar bit (FLAG_CONF3 palavra 0 mascara 0x2000): 0000 0000 -> 0000 0000, quadro `35 FA 00 00 00 00 00 2F`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado` em `0x4C852D` (CheckGiriAlberoCammeClick) — SE [obj+0x1280] == 0
- `Checked/ItemIndex := calculado por GetFlag3(caso 10)` em `0x4C55D5` (CheckSegnaleGiriClick) — incondicional
- `Visible := calculado` em `0x4A7670` (TabCambioShow) — incondicional
- `Checked/ItemIndex := calculado por GetFlag3(caso 10)` em `0x4A76C5` (TabCambioShow) — incondicional

### `chkRitardoPassaggioInMinuti` — Ritardo passaggio in minuti

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 2189 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Ritardo passaggio in minuti"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=38 Top=24 Largura=365 Altura=31; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Faz a tabela de atraso da troca ser lida em minutos em vez de segundos. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF3** (SerialCode 250), palavra 1, mascara 0x0004, caso do setter 15, papel "caixa (Checked)", condicao "sempre"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF3 palavra 1 = 0x0000 -> bit 0x0004: **desligado** `[OBS-FIO]` (LOG linha 1102872).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `chkRitardoPassaggioInMinutiClick` (0x4CB300) `[OBS-COD]`
  - SetFlag3 caso 15 -> FLAG_CONF3 palavra 1 mascara 0x0004 em `0x4CB320`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF3 palavra 1 mascara 0x0004): 0000 0000 -> 0000 0004, quadro `35 FA 00 00 00 04 00 33`.
  - desligar bit (FLAG_CONF3 palavra 1 mascara 0x0004): 0000 0000 -> 0000 0000, quadro `35 FA 00 00 00 00 00 2F`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetFlag3(caso 15)` em `0x4A88C0` (TabCambioShow) — incondicional
- `Visible := calculado` em `0x4A88D7` (TabCambioShow) — SE test byte ptr [0xaed13d], 8 != 
- `Visible := falso` em `0x4A88E6` (TabCambioShow) — SE test byte ptr [0xaed13d], 8 == 

### `CheckBoxEnableExtLedKLine` — Enable external LED on K-Line

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 2211 `[OBS-COD]`. Pai: `ScrollBoxCambio`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Enable external LED on K-Line"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=258 Top=-5 Largura=446 Altura=31; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Liga um LED externo ligado a linha K. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF3** (SerialCode 250), palavra 1, mascara 0x0008, caso do setter 16, papel "caixa (Checked)", condicao "sempre"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF3 palavra 1 = 0x0000 -> bit 0x0008: **desligado** `[OBS-FIO]` (LOG linha 1102872).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckBoxEnableExtLedKLineClick` (0x4CB328) `[OBS-COD]`
  - SetFlag3 caso 16 -> FLAG_CONF3 palavra 1 mascara 0x0008 em `0x4CB351`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF3 palavra 1 mascara 0x0008): 0000 0000 -> 0000 0008, quadro `35 FA 00 00 00 08 00 37`.
  - desligar bit (FLAG_CONF3 palavra 1 mascara 0x0008): 0000 0000 -> 0000 0000, quadro `35 FA 00 00 00 00 00 2F`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetFlag3(caso 16)` em `0x49086E` (FormShow; metodo de OUTRA aba/tela) — incondicional

## Rotulos com regra de mostrar/esconder ou texto dinamico

### `LabelSovrapposizione` — "Tempo de sobreposição"

Texto: ita "Tempo di sovrapposizione" / eng "Overlapping time" / por "Tempo de sobreposição" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := verdadeiro` em `0x4A9197` (ComboCambioKeyPress, ComboCambioKeyPress via sub_4A90B8) — SE tecla == Enter E sub_754CCC == 0
- `Visible := verdadeiro` em `0x4A7F98` (TabCambioShow) — incondicional

### `LabelRpm1` — "rpm"

Texto: ita "rpm" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=sim.
- `Visible := calculado` em `0x4A913E` (ComboCambioKeyPress, ComboCambioKeyPress via sub_4A90B8) — SE tecla == Enter E sub_754CCC == 0
- `Visible := calculado` em `0x4A913E` (TabCambioShow, TabCambioShow via sub_4A90B8) — incondicional

### `LabelT1` — "°C"

Texto: ita "°C" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4B8E7F` (CheckTempRiduttoreClick; metodo de OUTRA aba/tela) — incondicional

### `LabelS15` — "s"

Texto: ita "s" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := verdadeiro` em `0x4A9469` (ComboCambioKeyPress, ComboCambioKeyPress via sub_4A90B8) — SE tecla == Enter E sub_754CCC == 0
- `Visible := verdadeiro` em `0x4A84C4` (TabCambioShow) — incondicional

### `LabelTempCambio` — "Temperatura do gás para o cambio"

Texto: ita "Temperatura gas per il cambio" / eng "Gas temperature for change-over" / por "Temperatura do gás para o cambio" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado por GetFlag1(caso 9)` em `0x4B8E57` (CheckTempRiduttoreClick; metodo de OUTRA aba/tela) — incondicional

### `LabelGiriCambio` — "Nº de rotações para a mudança"

Texto: ita "Soglia giri per il cambio" / eng "Revs. threshold for change-over" / por "Nº de rotações para a mudança" `[OBS-COD]`. Estado de projeto: visivel=sim.
- `Visible := verdadeiro` em `0x4A9116` (ComboCambioKeyPress, ComboCambioKeyPress via sub_4A90B8) — SE tecla == Enter E sub_754CCC == 0
- `Visible := verdadeiro` em `0x4A9116` (TabCambioShow, TabCambioShow via sub_4A90B8) — incondicional

### `LabelSegnaleGiri` — "Tipo de sinal rotações"

Texto: ita "Tipo segnale giri" / eng "Type of rev signal" / por "Tipo de sinal rotações" `[OBS-COD]`. Estado de projeto: visivel=sim.
- `Visible := verdadeiro` em `0x4A79DA` (TabCambioShow) — incondicional

### `LabelTaglia` — "Taglia"

Texto: ita "Taglia" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := verdadeiro` em `0x4A74B3` (TabCambioShow) — incondicional

### `LabelT4` — "°C"

Texto: ita "°C" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4C5041` (CheckPartenzaGasCaldoClick) — incondicional

### `LabelCicchetto` — "Tempo di cicchetto"

Texto: ita "Tempo di cicchetto" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := verdadeiro` em `0x4A92B3` (ComboCambioKeyPress, ComboCambioKeyPress via sub_4A90B8) — SE tecla == Enter E sub_754CCC == 0
- `Visible := verdadeiro` em `0x4A8301` (TabCambioShow) — incondicional

### `LabelSogliaGiri` — "Soglia"

Texto: ita "Soglia" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := verdadeiro` em `0x4ACEB2` (ComboSegnaleGiriKeyPress) — SE eax == 0xd E sub_754CCC == 0 E [obj+0x2BD4] != 0 E SD.familia_LR[+0x80D] != 0
- `Visible := falso` em `0x4ACF29` (ComboSegnaleGiriKeyPress) — SE eax == 0xd E sub_754CCC == 0 E SD.familia_LR[+0x80D] == 0
- `Visible := verdadeiro` em `0x4A7BB9` (TabCambioShow) — SE [obj+0x2BD4] != 0 E SD.familia_LR[+0x80D] != 0
- `Visible := falso` em `0x4A7C34` (TabCambioShow) — SE SD.familia_LR[+0x80D] == 0

### `LabSogliaGiri` — "V"

Texto: ita "V" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4ACEDA` (ComboSegnaleGiriKeyPress) — SE eax == 0xd E sub_754CCC == 0 E [obj+0x2BD4] != 0 E SD.familia_LR[+0x80D] != 0
- `Visible := falso` em `0x4ACF43` (ComboSegnaleGiriKeyPress) — SE eax == 0xd E sub_754CCC == 0 E SD.familia_LR[+0x80D] == 0
- `Visible := calculado` em `0x4A7BE1` (TabCambioShow) — SE [obj+0x2BD4] != 0 E SD.familia_LR[+0x80D] != 0
- `Visible := falso` em `0x4A7C4E` (TabCambioShow) — SE SD.familia_LR[+0x80D] == 0

### `LabelCicchettoRpm` — "RPM threshold for crank"

Texto: ita "Soglia giri per crank" / eng "RPM threshold for crank" / por "RPM threshold for crank" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := verdadeiro` em `0x4A9349` (ComboCambioKeyPress, ComboCambioKeyPress via sub_4A90B8) — SE tecla == Enter E sub_754CCC == 0
- `Visible := verdadeiro` em `0x4A839B` (TabCambioShow) — incondicional

### `LabCicchettoRpm` — "rpm"

Texto: ita "rpm" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado por sub_432BE4` em `0x4A938C` (ComboCambioKeyPress, ComboCambioKeyPress via sub_4A90B8) — SE tecla == Enter E sub_754CCC == 0
- `Visible := calculado por sub_432BE4` em `0x4A83DE` (TabCambioShow) — incondicional

### `LabelCicchettoInj` — "Gas injection time in priming"

Texto: ita "Tempo di iniezione gas in cicchetto" / eng "Gas injection time in priming" / por "Gas injection time in priming" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4A93A0` (ComboCambioKeyPress, ComboCambioKeyPress via sub_4A90B8) — SE tecla == Enter E sub_754CCC == 0
- `Visible := calculado` em `0x4A83F2` (TabCambioShow) — incondicional

### `LabCicchettoInj` — "ms"

Texto: ita "ms" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4A93FA` (ComboCambioKeyPress, ComboCambioKeyPress via sub_4A90B8) — SE tecla == Enter E sub_754CCC == 0
- `Visible := calculado` em `0x4A8455` (TabCambioShow) — incondicional

### `LabelCicchettoNum` — "Number of gas injections in priming"

Texto: ita "Numero iniettate gas in cicchetto" / eng "Number of gas injections in priming" / por "Number of gas injections in priming" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4A940E` (ComboCambioKeyPress, ComboCambioKeyPress via sub_4A90B8) — SE tecla == Enter E sub_754CCC == 0
- `Visible := calculado` em `0x4A8469` (TabCambioShow) — incondicional

### `LabelNumDenti` — "n. denti"

Texto: ita "n. denti" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := verdadeiro` em `0x4C8624` (CheckGiriAlberoCammeClick) — incondicional
- `Visible := verdadeiro` em `0x4C87F9` (CheckGiriRuotaFonicaClick) — incondicional
- `Visible := verdadeiro` em `0x4C56DF` (CheckSegnaleGiriClick) — incondicional
- `Visible := verdadeiro` em `0x4A77E3` (TabCambioShow) — incondicional

### `LabelLetturaGiri` — "Lettura giri:"

Texto: ita "Lettura giri:" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := falso` em `0x4A765C` (TabCambioShow) — incondicional

### `LabRpmTachimetro` — "rpm"

Texto: ita "rpm" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := falso` em `0x4A6351` (ButtonSequenceAcquisitionClick) — SE sub_921FCC != 0 E [obj+0x69] != 0
- `Visible := calculado por sub_752EDC` em `0x4A63F9` (ButtonSequenceAcquisitionClick) — SE sub_921FCC != 0 E [obj+0x69] == 0
- `Visible := falso` em `0x4A623A` (CheckInjAcquisitionClick) — incondicional
- `Visible := falso` em `0x4A65EA` (EditRpmTachimetroKeyPress) — SE tecla == Enter E sub_921FCC != 0 E EnviaComando != 0
- `Visible := falso` em `0x490B0D` (FormShow; metodo de OUTRA aba/tela) — incondicional
- `Visible := falso` em `0x4A5EC9` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0x3dc](TabCambio) E sub_921FCC != 0 E [obj+0x1855] != 0 E EnviaComando != 0 E test byte ptr [ebp - 0x1ca], 0x40 != 

### `LabelRpmTachimetro` — "Inserire i giri letti da tachimetro e premere RETURN"

Texto: ita "Inserire i giri letti da tachimetro e premere RETURN" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := falso` em `0x4A6337` (ButtonSequenceAcquisitionClick) — SE sub_921FCC != 0 E [obj+0x69] != 0
- `Caption := calculado` em `0x4A6378` (ButtonSequenceAcquisitionClick) — SE sub_921FCC != 0 E [obj+0x69] == 0
- `Visible := calculado` em `0x4A6385` (ButtonSequenceAcquisitionClick) — SE sub_921FCC != 0 E [obj+0x69] == 0
- `Visible := falso` em `0x4A6220` (CheckInjAcquisitionClick) — incondicional
- `Caption := calculado` em `0x4A65D0` (EditRpmTachimetroKeyPress) — SE tecla == Enter E sub_921FCC != 0 E EnviaComando != 0
- `Visible := falso` em `0x490AF3` (FormShow; metodo de OUTRA aba/tela) — incondicional
- `Visible := falso` em `0x4A5EAF` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0x3dc](TabCambio) E sub_921FCC != 0 E [obj+0x1855] != 0 E EnviaComando != 0 E test byte ptr [ebp - 0x1ca], 0x40 != 

### `LabelProgressCol1` — "n.inj.prog."

Texto: ita "n.inj.prog." / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado por sub_784340` em `0x4A81EB` (TabCambioShow) — SE sub_404C40 == 0 E sub_404C40 != 0

## Lacunas desta aba

- Edits sem codigo de dado na rotina 0x48A228 (o valor passa por metodo proprio do TStreamDati; formula nao decodificada aqui, `DESCONHECIDO`): `EditCicchettoRpm`, `EditTipoIniettore`, `EditSogliaGiri`, `EditCicchettoInj`, `EditCicchettoNum`, `EditRpmTachimetro`, `EditParamProgress1`, `EditParamProgress3`, `EditParamProgress2`.
- Combos cujo valor e gravado por metodo proprio (sem bit nem codigo generico): `ComboTipoIniettore`, `ComboSegnaleGiri`, `ComboCilindri`, `ComboAccensione`, `ComboCambio`, `ComboTaglia`, `ComboFullGroup`. Os vetores tocados aparecem na lista de acessos do handler; o significado de cada opcao no fio e `DESCONHECIDO` quando nao ha formula.
- Grades (`TDrawGrid`): o conteudo e desenhado em `OnDrawCell`; o conteudo e os quadros por celula nao foram decodificados celula a celula: `DrawDelayGasTemp`, `DrawGridPassaggio`, `DrawGridPassaggioGas`, `DrawGridProgressioni`.
- Visibilidade/habilitacao: as regras listadas sao as que o codigo escreve; a combinacao final depende de dados da ECU (modelo, familia LR, MASK_FUNCTION) que o LOG so mostra em parte. O dono usa familia nao-LR (INFERIDO: o original leu SC 192, nunca 193).
- A associacao rotulo<->campo e por nome/geometria (`INFERIDO`), e a chave da dica (HINT...) segue o nome do componente (`INFERIDO`).

