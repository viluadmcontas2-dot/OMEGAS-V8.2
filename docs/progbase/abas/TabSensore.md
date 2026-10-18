# TabSensore — Nível do gás

Nome na tela: ita "Livello GAS" / eng "Gas level" / por "Nível do gás" `[OBS-COD]`. Aba 4 de 16 do grupo D3a. Rotina de abertura (OnShow): `TabSensoreShow` em `0x4AA5BC` `[OBS-COD]`. TabVisible de projeto: sim.

Legenda das marcas: `OBS-COD` = lido do DFM/codigo do ProgBase.exe (ou executado no emulador Unicorn); `OBS-FIO` = visto no Portmon da ECU do dono; `DERIVADO` = calculado a partir dessas observacoes; `INFERIDO` = deducao; `DESCONHECIDO` = sem prova.

**Resumo**: 59 controles no DFM (TButton=4, TCheckBox=10, TDrawGrid=1, TGroupBox=1, TKeyComboBox=1, TLabel=28, TNumericEdit=13, TScrollBox=1); 29 interativos; 8 com codigo de dado e formula; 7 com caixa/bit; 10 com valor lido no LOG. Fonte do .exe sha256 `8a2d297c8c21...`.

## Como o ProgBase trata estes controles (valido para a aba inteira)

- **Campo numerico (`TNumericEdit`)** `[OBS-COD]`: ao entrar no campo, `EditGiriCambioEnter` (0x4A98D8) mostra no rotulo `LabelLimite` o texto `(minimo;maximo)` do proprio campo; ao sair, `EditGiriCambioExit` (0x4A9CC0) esconde esse rotulo. Nada e enviado a ECU nesses dois momentos. Ao apertar **Enter**, o `TNumericEdit` limita o numero a [minimo;maximo] (so se minimo!=0 ou maximo!=0; 0x96C170) e o handler generico `EditRifLambdaKeyPress` (0x4A6A00) chama `TStreamDati.Set*` com o *codigo de dado* do campo (campo `+0x2EC`, indice `+0x2F0`; amarrados em 0x48A228): o valor e convertido pela formula do caso, gravado no cache do programa e enviado como SetNumber (escalar) ou SetNumber+indice (vetor). Depois o handler le o valor de volta do cache (`Get*`) e redesenha o campo arredondado ao passo (`Precision`, 0x96BB88). Nao ha releitura da ECU.
- **Caixa de marcar (`TCheckBox`)** `[OBS-COD]`: apertar Enter simula um clique do mouse (`CheckTpsBlocKeyPress` 0x4A8928 envia WM_LBUTTONDOWN/UP); o clique chama o handler `Check...Click`, que liga/desliga **um bit** do vetor FLAG_CONF1/2/3 (o programa regrava o vetor inteiro, ver quadros abaixo) e atualiza o que depende da caixa.
- **Lista (`TKeyComboBox`)** `[OBS-COD]`: Enter no combo chama o `...KeyPress` correspondente, que grava o indice escolhido (por codigo de dado `+0x330` ou por bit de FLAG_CONF).
- **Resposta da ECU a toda gravacao** `[OBS-FIO]`: a porta devolve o eco do quadro e a ECU responde `53 00 53` (LOG linhas 48568-48600 e 31284-31300). Em seguida o programa limpa os buffers e retoma a telemetria (`48 01 49`).
- **Conexao**: os valores 'atuais' vem de leituras `09`/`0A`/`29` feitas ao conectar (LOG de 12/08/2026); a tela do programa mostra o cache dessas leituras.

## Mapa de todos os controles (ordem do DFM)

| # | Componente | Tipo | Rotulo (por / eng / ita) | Unidade | Vetor (SC) | Bit | Visivel no DFM |
|---|---|---|---|---|---|---|---|
| 1 | `ScrollBoxLivello` | rolagem | - / - / - | - | - | - | sim |
| 2 | `LabelSensoreLivello` | rotulo | Tipo de sensor de nível do gás / Type of GAS level sensor / Tipo di sensore livello GAS | - | - | - | sim |
| 3 | `LabelRitornoBenzina` | rotulo | Tempo de baixa pressão para retornar / Low pressure time for change back / Tempo di bassa pressione per il ritorno | - | - | - | False |
| 4 | `LabelS12` | rotulo | - / - / s | - | - | - | False |
| 5 | `LabelBassaPressione` | rotulo | Valor da baixa pressão para a reversão / Low pressure value for change back / Valore di bassa pressione per il ritorno | - | - | - | False |
| 6 | `LabBassaPressione` | rotulo | - / - / bar | - | - | - | False |
| 7 | `LabelPressPass` | rotulo | Pressões para a reversão gasolina / Pressure for change back to petrol / Pressioni per il retropassaggio benzina | - | - | - | False |
| 8 | `LabTinjPress` | rotulo | t.inj.gasolina / t.inj.p / t.inj.b | - | - | - | False |
| 9 | `LabPRail` | rotulo | P rail / P rail / P rail | - | - | - | False |
| 10 | `LabelBassaPressioneMp` | rotulo | - / - / Valore di bassa pressione per il ritorno | - | - | - | False |
| 11 | `LabBassaPressioneMp` | rotulo | - / - / bar | - | - | - | False |
| 12 | `LabelAnticipoEV1` | rotulo | EV1 opening advance / EV1 opening advance / Anticipo apertura EV1 | - | - | - | False |
| 13 | `LabelAnticipoEV2` | rotulo | EV2 opening advance / EV2 opening advance / Anticipo apertura EV2 | - | - | - | False |
| 14 | `LabAnticipoEV1` | rotulo | - / - / s | - | - | - | False |
| 15 | `LabAnticipoEV2` | rotulo | - / - / s | - | - | - | False |
| 16 | `LabelRpmAttivPompa` | rotulo | Pump activation RPM for automatic contribution / Pump activation RPM for automatic contribution / RPM attiv. pompa per contrib. automatico | - | - | - | False |
| 17 | `LabRpmAttivPompa` | rotulo | - / - / rpm | - | - | - | False |
| 18 | `LabRpmAnticipoPompa` | rotulo | - / - / rpm | - | - | - | False |
| 19 | `LabelRpmAnticipoPompa` | rotulo | RPM pump activation advance in voluntary contrib. / RPM pump activation advance in voluntary contrib. / RPM anticipo attiv. pompa per contrib. volontario | - | - | - | False |
| 20 | `LabelTempoAttivPompa` | rotulo | Minimum time for petrol pump activation / Minimum time for petrol pump activation / Tempo minimo attiv. pompa benzina | - | - | - | False |
| 21 | `LabTempoAttivPompa` | rotulo | - / - / s | - | - | - | False |
| 22 | `LabelPeriodoRiattivPompa` | rotulo | Pump reactivation period / Pump reactivation period / Periodo riattivazione pompa | - | - | - | False |
| 23 | `LabPeriodoRiattivPompa` | rotulo | - / - / min | - | - | - | False |
| 24 | `ComboSensore` | combo | Tipo de sensor de nível do gás / Type of GAS level sensor / Tipo di sensore livello GAS | - | - | - | sim |
| 25 | `GroupRifSensore` | grupo | Referências de nível não padrão / Non-standard level references / Riferimenti di livello non standard | - | - | - | False |
| 26 | `LabelSensore1` | rotulo | Reserva / Reserve / Riserva | - | - | - | sim |
| 27 | `LabelSensore2` | rotulo | 1/4 / 1/4 / 1/4 | - | - | - | sim |
| 28 | `LabelSensore3` | rotulo | 2/4 / 2/4 / 2/4 | - | - | - | sim |
| 29 | `LabelSensore4` | rotulo | 3/4 / 3/4 / 3/4 | - | - | - | sim |
| 30 | `LabelMinLevel` | rotulo | - / - / 0 | - | - | - | sim |
| 31 | `LabelMaxLevel` | rotulo | - / - / 0 | - | - | - | sim |
| 32 | `EditLivello1` | edit numerico | Reserva / Reserve / Riserva | 1/4 | - | - | sim |
| 33 | `EditLivello2` | edit numerico | 1/4 / 1/4 / 1/4 | 2/4 | - | - | sim |
| 34 | `EditLivello3` | edit numerico | 2/4 / 2/4 / 2/4 | 3/4 | - | - | sim |
| 35 | `EditLivello4` | edit numerico | 3/4 / 3/4 / 3/4 | - | - | - | sim |
| 36 | `ButtonAccetta` | botao | &Aceitar / &Accept / &Conferma | - | - | - | sim |
| 37 | `ButtonMinLevel` | botao | Set min level / Set min level / Set min level | - | - | - | sim |
| 38 | `ButtonMaxLevel` | botao | Set max level / Set max level / Set max level | - | - | - | sim |
| 39 | `ButtonDoLevel` | botao | Find levels / Find levels / Find levels | - | - | - | sim |
| 40 | `CheckRitornoBenzina` | caixa de marcar | Retorno automático para a gasolina / Automatic change back to petrol / &Ritorno automatico a benzina | - | - | - | False |
| 41 | `EditRitornoBenzina` | edit numerico | Tempo de baixa pressão para retornar / Low pressure time for change back / Tempo di bassa pressione per il ritorno | s | TEMPO_RITORNO_BENZINA (62) | - | False |
| 42 | `CheckPassPRail` | caixa de marcar | - / - / Ritorno automatico a benzina con la pressione del rail | - | - | FLAG_CONF1[1]&0x0100 | False |
| 43 | `CheckBassaPressione` | caixa de marcar | Reversão automática por baixa pressão impostável / Automatic change back with low pressure setting possible / Ritorno automatico con bassa pressione impostabile | - | - | FLAG_CONF1[1]&0x0040 | False |
| 44 | `EditBassaPressione` | edit numerico | Valor da baixa pressão para a reversão / Low pressure value for change back / Valore di bassa pressione per il ritorno | bar | PRESS_BASSA_RETROPAS_LR (152) | - | False |
| 45 | `CheckElettrovalvole` | caixa de marcar | Electroválvulas gás abertas com antecedencia / Gas electrovalves opened in advance / Elettrovalvole gas aperte con anticipo | - | - | FLAG_CONF1[0]&0x0080 | False |
| 46 | `CheckAlimentazione` | caixa de marcar | Use of wire EV2 for STAP / Use of wire EV2 for STAP / Utilizzo filo EV2 per STAP | - | - | FLAG_CONF1[1]&0x0800 | False |
| 47 | `DrawGridPressPass` | grade | Pressões para a reversão gasolina / Pressure for change back to petrol / Pressioni per il retropassaggio benzina | - | - | - | False |
| 48 | `EditBassaPressioneMp` | edit numerico | - / - / Valore di bassa pressione per il ritorno | bar | - | - | False |
| 49 | `EditAnticipoEV1` | edit numerico | EV1 opening advance / EV1 opening advance / Anticipo apertura EV1 | s | TEMPI_ANTICIPI_EV (238) | - | False |
| 50 | `EditAnticipoEV2` | edit numerico | EV2 opening advance / EV2 opening advance / Anticipo apertura EV2 | s | TEMPI_ANTICIPI_EV (238) | - | False |
| 51 | `CheckTaglioPompaBenzina` | caixa de marcar | Petrol pump cutting / Petrol pump cutting / Taglio pompa benzina | - | - | FLAG_CONF3[0]&0x0004 | False |
| 52 | `EditRpmAttivPompa` | edit numerico | Pump activation RPM for automatic contribution / Pump activation RPM for automatic contribution / RPM attiv. pompa per contrib. automatico | rpm | TAGLIO_POMPA_BENZINA (268) | - | False |
| 53 | `EditRpmAnticipoPompa` | edit numerico | RPM pump activation advance in voluntary contrib. / RPM pump activation advance in voluntary contrib. / RPM anticipo attiv. pompa per contrib. volontario | rpm | TAGLIO_POMPA_BENZINA (268) | - | False |
| 54 | `EditTempoAttivPompa` | edit numerico | Minimum time for petrol pump activation / Minimum time for petrol pump activation / Tempo minimo attiv. pompa benzina | s | TAGLIO_POMPA_BENZINA (268) | - | False |
| 55 | `EditPeriodoRiattivPompa` | edit numerico | Pump reactivation period / Pump reactivation period / Periodo riattivazione pompa | min | TAGLIO_POMPA_BENZINA (268) | - | False |
| 56 | `CheckEnabledDhlpDiagnosis` | caixa de marcar | - / - / Abilita diagnosi perdite gas | - | - | - | False |
| 57 | `CheckAttivaPompaInRiserva` | caixa de marcar | - / - / Abilita riattivazione pompa in riserva | - | - | FLAG_CONF3[0]&0x0800 | False |
| 58 | `CheckRiconoscimentoPieno` | caixa de marcar | Recognition of full / Recognition of full / Riconoscimento pieno | - | - | FLAG_CONF3[0]&0x1000 | False |
| 59 | `CheckBoxEV2NotConnected` | caixa de marcar | - / - / EV2 non collegata | - | - | - | False |

## Controles interativos, um a um

### `ComboSensore` — Tipo de sensor de nível do gás

Tipo: **combo** (`TKeyComboBox`), DFM linha 3594 `[OBS-COD]`. Pai: `ScrollBoxLivello`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelSensoreLivello` (associacao INFERIDO)): ita = "Tipo di sensore livello GAS"; eng = "Type of GAS level sensor"; por = "Tipo de sensor de nível do gás" `[OBS-COD]`.
- Posicao no DFM: Left=489 Top=10 Largura=301 Altura=37; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Opcoes da lista `[OBS-COD]`: 0 = "A.E.B." / Não padrão; 1 = "0 - 90 ohm" / Não padrão invertido; 2 = "Landi Renzo"; 3 = "Sensata HD"; 4 = "Cartesio"; 5 = "Non standard"; 6 = "Non standard invertito".
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTSENSORELIVELLO`: "The function allows the fuel level sensor installed on the tank to be selected. \ With the “Not standard” options it is possible to calibrate the thresholds of any sensor".

**O que isso faz (linguagem simples)**

- Tipo do sensor de nivel de gas do tanque (AEB, 0-90 ohm, Landi Renzo, Sensata HD ou nao padrao); "nao padrao" deixa voce ajustar os niveis. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.18).
- Manual SW 2.16.4 C, p.18 (linha 860 de sw.txt): "A Type of gas level sensor: This allows you to set the type of gas sensor that has been fitted. The following gas level sensors can be set: • A.E.B.;" `[OBS-COD]`.

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `ComboSensoreKeyPress` (0x4A8C60) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x4304F0` em `0x4A8CA6` (le: TIPO_SENSORE; escreve: -)

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Cada opcao da lista chama o metodo `0x4304F0` `[INFERIDO]` (convencao assumida):
  - opcao 0 "A.E.B." -> quadro(s) `12 24 00 00 36 | 13 25 00 00 13 4B | 13 25 00 01 24 5D | 13 25 00 02 38 72 | 13 25 00 03 73 AE`.
  - opcao 1 "0 - 90 ohm" -> quadro(s) `12 24 00 01 37 | 13 25 00 00 13 4B | 13 25 00 01 24 5D | 13 25 00 02 38 72 | 13 25 00 03 73 AE`.
  - opcao 2 "Landi Renzo" -> quadro(s) `12 24 00 02 38 | 13 25 00 00 0E 46 | 13 25 00 01 2A 63 | 13 25 00 02 47 81 | 13 25 00 03 61 9C`.
  - opcao 3 "Sensata HD" -> quadro(s) `12 24 00 03 39 | 13 25 00 00 0E 46 | 13 25 00 01 2A 63 | 13 25 00 02 47 81 | 13 25 00 03 61 9C`.
  - opcao 4 "Cartesio" -> quadro(s) `12 24 00 04 3A`.
  - opcao 5 "Non standard" -> quadro(s) `12 24 00 05 3B | 13 25 00 00 0E 46 | 13 25 00 01 2A 63 | 13 25 00 02 47 81 | 13 25 00 03 61 9C`.
  - opcao 6 "Non standard invertito" -> quadro(s) `12 24 00 06 3C | 13 25 00 00 28 60 | 13 25 00 01 40 79 | 13 25 00 02 64 9E | 13 25 00 03 89 C4`.
  - Estrutura: igual a acima. Fonte: metodo 0x4304F0 executado no Unicorn com edx = indice da opcao (convencao assumida; o handler ComboSensoreKeyPress o chama em 0x4A8CA6)
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por chamada virtual dword ptr [ecx + 0x60]` em `0x4AA90D` (TabSensoreShow) — incondicional

### `EditLivello1` — Reserva

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 3682 `[OBS-COD]`. Pai: `GroupRifSensore`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelSensore1` (associacao INFERIDO)): ita = "Riserva"; eng = "Reserve"; por = "Reserva" `[OBS-COD]`.
- Unidade ao lado: **1/4** `[OBS-COD]` (rotulo `LabelSensore2`).
- Posicao no DFM: Left=116 Top=39 Largura=72 Altura=32; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 255.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTSENSORE1`: "Reserve".

**O que isso faz (linguagem simples)**

- Ponto de reserva do sensor de nivel (so editavel nos tipos nao padrao); termine apertando Aceitar. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.18).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditRifLambdaKeyPress` (0x4A6A00) `[OBS-COD]`
  - SetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A45` (handler generico)
  - GetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A64` (handler generico)
  - SetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A99` (handler generico)
  - GetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6AB1` (handler generico)
- `OnEnter` -> `EditGiriKFiltroEnter` (0x4BE90C) `[OBS-COD]`
- `OnExit` -> `EditGiriKFiltroExit` (0x4BEABC) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditLivello2` — 1/4

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 3700 `[OBS-COD]`. Pai: `GroupRifSensore`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelSensore2` (associacao INFERIDO)): ita = "1/4"; eng = "1/4"; por = "1/4" `[OBS-COD]`.
- Unidade ao lado: **2/4** `[OBS-COD]` (rotulo `LabelSensore3`).
- Posicao no DFM: Left=255 Top=39 Largura=71 Altura=32; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 255.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTSENSORE2`: "\"1/4\"".

**O que isso faz (linguagem simples)**

- Ponto de 1/4 do tanque no sensor de nivel (so nos tipos nao padrao). `[OBS-COD]` (fonte: manual SW 2.16.4 C p.18).
- Manual SW 2.16.4 C, p.26 (linha 1322 de sw.txt): "cut off the extra injections, set “1.4” in the boxes for “extra injections” and “petrol in-between injection time” and “0” in the “minimum gas extra injection time (ref “B”) “F8 Injectors” window." `[OBS-COD]`.

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditRifLambdaKeyPress` (0x4A6A00) `[OBS-COD]`
  - SetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A45` (handler generico)
  - GetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A64` (handler generico)
  - SetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A99` (handler generico)
  - GetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6AB1` (handler generico)
- `OnEnter` -> `EditGiriKFiltroEnter` (0x4BE90C) `[OBS-COD]`
- `OnExit` -> `EditGiriKFiltroExit` (0x4BEABC) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditLivello3` — 2/4

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 3718 `[OBS-COD]`. Pai: `GroupRifSensore`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelSensore3` (associacao INFERIDO)): ita = "2/4"; eng = "2/4"; por = "2/4" `[OBS-COD]`.
- Unidade ao lado: **3/4** `[OBS-COD]` (rotulo `LabelSensore4`).
- Posicao no DFM: Left=393 Top=39 Largura=72 Altura=32; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 255.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTSENSORE3`: "\"2/4\"".

**O que isso faz (linguagem simples)**

- Ponto de 2/4 do tanque no sensor de nivel (so nos tipos nao padrao). `[OBS-COD]` (fonte: manual SW 2.16.4 C p.18).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditRifLambdaKeyPress` (0x4A6A00) `[OBS-COD]`
  - SetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A45` (handler generico)
  - GetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A64` (handler generico)
  - SetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A99` (handler generico)
  - GetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6AB1` (handler generico)
- `OnEnter` -> `EditGiriKFiltroEnter` (0x4BE90C) `[OBS-COD]`
- `OnExit` -> `EditGiriKFiltroExit` (0x4BEABC) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditLivello4` — 3/4

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 3736 `[OBS-COD]`. Pai: `GroupRifSensore`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelSensore4` (associacao INFERIDO)): ita = "3/4"; eng = "3/4"; por = "3/4" `[OBS-COD]`.
- Posicao no DFM: Left=532 Top=39 Largura=72 Altura=32; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 255.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTSENSORE4`: "\"3/4\"".

**O que isso faz (linguagem simples)**

- Ponto de 3/4 do tanque no sensor de nivel (so nos tipos nao padrao). `[OBS-COD]` (fonte: manual SW 2.16.4 C p.18).
- Manual SW 2.16.4 C, p.3 (linha 90 de sw.txt): "The OMEGAS-EVO version can be installed on 2-3-4 nor- mally aspirated and turbocharged cylinder engines. The MAP sensor that comes with the unit must always be fitted." `[OBS-COD]`.

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditRifLambdaKeyPress` (0x4A6A00) `[OBS-COD]`
  - SetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A45` (handler generico)
  - GetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A64` (handler generico)
  - SetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A99` (handler generico)
  - GetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6AB1` (handler generico)
- `OnEnter` -> `EditGiriKFiltroEnter` (0x4BE90C) `[OBS-COD]`
- `OnExit` -> `EditGiriKFiltroExit` (0x4BEABC) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `ButtonAccetta` — &Aceitar

Tipo: **botao** (`TButton`), DFM linha 3754 `[OBS-COD]`. Pai: `GroupRifSensore`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "&Conferma"; eng = "&Accept"; por = "&Aceitar" `[OBS-COD]`.
- Posicao no DFM: Left=628 Top=38 Largura=132 Altura=31; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTBUTTONACCETTA`: "Confirm non-standard references".

**O que isso faz (linguagem simples)**

- Confirma os niveis do sensor nao padrao digitados. `[OBS-COD]` (fonte: dica do proprio ProgBase (HINTBUTTONACCETTA)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `ButtonAccettaClick` (0x4A8D74) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x434440` em `0x4A8D91` (le: -; escreve: -)
  - muda Text de um controle nao identificado pela analise estatica em `0x4A8E10`
  - muda Text de um controle nao identificado pela analise estatica em `0x4A8ED7`
  - chama o metodo do TStreamDati `0x4306C0` em `0x4A8F43` (le: RIF_SENSORE; escreve: -)
  - chama o metodo do TStreamDati `0x4306C0` em `0x4A8F62` (le: RIF_SENSORE; escreve: -)
  - chama o metodo do TStreamDati `0x4306C0` em `0x4A8F81` (le: RIF_SENSORE; escreve: -)
  - chama o metodo do TStreamDati `0x4306C0` em `0x4A8FA0` (le: RIF_SENSORE; escreve: -)
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Botao: sem escrita de valor padrao. O que o handler faz esta na lista de acessos acima; bytes especificos so quando o handler chama `EnviaComando`/setter `[OBS-COD]` (nao executado aqui) -> **DESCONHECIDO** o quadro exato quando nao listado.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `ButtonMinLevel` — Set min level

Tipo: **botao** (`TButton`), DFM linha 3766 `[OBS-COD]`. Pai: `GroupRifSensore`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Set min level"; eng = "Set min level"; por = "Set min level" `[OBS-COD]`.
- Posicao no DFM: Left=25 Top=92 Largura=202 Altura=32; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTBUTTONMINLEVEL`: "Set min level".

**O que isso faz (linguagem simples)**

- Grava o nivel minimo do sensor (tanque vazio) lido no momento. `[OBS-COD]` (fonte: dica do proprio ProgBase (HINTBUTTONMINLEVEL)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `ButtonMinLevelClick` (0x4C598C) `[OBS-COD]`
  - GetEsc codigo 14 -> vetor TEST_WORD (SC 18) em `0x4C59E0`
  - SetEsc codigo 14 -> vetor TEST_WORD (SC 18) em `0x4C5A1B`
  - muda Caption de `LabelMinLevel` em `0x4C5A53`
  - muda Text de um controle nao identificado pela analise estatica em `0x4C5A84`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Botao: sem escrita de valor padrao. O que o handler faz esta na lista de acessos acima; bytes especificos so quando o handler chama `EnviaComando`/setter `[OBS-COD]` (nao executado aqui) -> **DESCONHECIDO** o quadro exato quando nao listado.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `ButtonMaxLevel` — Set max level

Tipo: **botao** (`TButton`), DFM linha 3778 `[OBS-COD]`. Pai: `GroupRifSensore`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Set max level"; eng = "Set max level"; por = "Set max level" `[OBS-COD]`.
- Posicao no DFM: Left=286 Top=92 Largura=189 Altura=32; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTBUTTONMAXLEVEL`: "Set max level".

**O que isso faz (linguagem simples)**

- Grava o nivel maximo do sensor (tanque cheio) lido no momento. `[OBS-COD]` (fonte: dica do proprio ProgBase (HINTBUTTONMAXLEVEL)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `ButtonMaxLevelClick` (0x4C5ADC) `[OBS-COD]`
  - GetEsc codigo 14 -> vetor TEST_WORD (SC 18) em `0x4C5B30`
  - SetEsc codigo 14 -> vetor TEST_WORD (SC 18) em `0x4C5B6E`
  - muda Caption de `LabelMaxLevel` em `0x4C5BA6`
  - muda Text de um controle nao identificado pela analise estatica em `0x4C5BD7`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Botao: sem escrita de valor padrao. O que o handler faz esta na lista de acessos acima; bytes especificos so quando o handler chama `EnviaComando`/setter `[OBS-COD]` (nao executado aqui) -> **DESCONHECIDO** o quadro exato quando nao listado.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `ButtonDoLevel` — Find levels

Tipo: **botao** (`TButton`), DFM linha 3790 `[OBS-COD]`. Pai: `GroupRifSensore`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Find levels"; eng = "Find levels"; por = "Find levels" `[OBS-COD]`.
- Posicao no DFM: Left=548 Top=92 Largura=215 Altura=32; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTBUTTONDOLEVEL`: "Find levels".

**O que isso faz (linguagem simples)**

- Procura sozinho os niveis do sensor. `[OBS-COD]` (fonte: dica do proprio ProgBase (HINTBUTTONDOLEVEL)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `ButtonDoLevelClick` (0x4C5C30) `[OBS-COD]`
  - GetEsc codigo 14 -> vetor TEST_WORD (SC 18) em `0x4C5C53`
  - GetEsc codigo 14 -> vetor TEST_WORD (SC 18) em `0x4C5C72`
  - chama o metodo do TStreamDati `0x434440` em `0x4C5C92` (le: -; escreve: -)
  - muda Text de um controle nao identificado pela analise estatica em `0x4C5CBD`
  - muda Text de um controle nao identificado pela analise estatica em `0x4C5DF7`
  - SetEsc codigo 14 -> vetor TEST_WORD (SC 18) em `0x4C5F79`
  - muda Caption de `LabelMinLevel` em `0x4C5FA3`
  - muda Caption de `LabelMaxLevel` em `0x4C5FDB`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Botao: sem escrita de valor padrao. O que o handler faz esta na lista de acessos acima; bytes especificos so quando o handler chama `EnviaComando`/setter `[OBS-COD]` (nao executado aqui) -> **DESCONHECIDO** o quadro exato quando nao listado.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `CheckRitornoBenzina` — Retorno automático para a gasolina

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 3803 `[OBS-COD]`. Pai: `ScrollBoxLivello`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "&Ritorno automatico a benzina"; eng = "Automatic change back to petrol"; por = "Retorno automático para a gasolina" `[OBS-COD]`.
- Posicao no DFM: Left=38 Top=268 Largura=760 Altura=31; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTCHECKRITORNOBENZINA`: "&Automatic change-back to petrol".

**O que isso faz (linguagem simples)**

- Volta sozinho para gasolina quando a pressao do gas cai; sem isso, com tanque vazio o motor roda irregular ate apagar. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.19).
- Manual SW 2.16.4 C, p.19 (linha 892 de sw.txt): "B Automatic change back to petrol: the following mes- sage appears when you select this item: “Low pressure time for change back” (range 0;20“). It is possible to set the time that must elapse before switching to petrol when" `[OBS-COD]`.

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckRitornoBenzinaClick` (0x4A9484) `[OBS-COD]`
  - muda Visible de um controle nao identificado pela analise estatica em `0x4A94B9`
  - muda Visible de um controle nao identificado pela analise estatica em `0x4A94DD`
  - muda Visible de um controle nao identificado pela analise estatica em `0x4A9501`
  - GetEsc codigo 28 -> vetor TEMPO_RITORNO_BENZINA (SC 62) em `0x4A9522`
  - SetEsc codigo 28 -> vetor TEMPO_RITORNO_BENZINA (SC 62) em `0x4A9547`
  - SetEsc codigo 28 -> vetor TEMPO_RITORNO_BENZINA (SC 62) em `0x4A957B`
  - SetEsc codigo 28 -> vetor TEMPO_RITORNO_BENZINA (SC 62) em `0x4A9594`
  - GetEsc codigo 28 -> vetor TEMPO_RITORNO_BENZINA (SC 62) em `0x4A95A7`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetEsc(codigo 28)` em `0x4AAA23` (TabSensoreShow) — incondicional

### `EditRitornoBenzina` — Tempo de baixa pressão para retornar

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 3819 `[OBS-COD]`. Pai: `ScrollBoxLivello`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelRitornoBenzina` (associacao INFERIDO)): ita = "Tempo di bassa pressione per il ritorno"; eng = "Low pressure time for change back"; por = "Tempo de baixa pressão para retornar" `[OBS-COD]`.
- Unidade ao lado: **s** `[OBS-COD]` (rotulo `LabelS12`).
- Posicao no DFM: Left=555 Top=303 Largura=71 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 3.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTRITORNOBENZINA`: "Time of low pressure for change-back".

**O que isso faz (linguagem simples)**

- Tempo (0 a 20 s) de pressao baixa antes de voltar para gasolina. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.19).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 28 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **TEMPO_RITORNO_BENZINA** (SerialCode 62, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x436CAB): `valor_bruto = trunc(v)` com `v` = valor digitado.
    - ECU -> tela (getter 0x4354D2): `valor_na_tela = SD[0x2B94]`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- TEMPO_RITORNO_BENZINA (SC 62): bruto 0; na tela: **0.0** `[OBS-FIO, tela DERIVADO]` (LOG linha 1106302).

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
  - valor atual da ECU do dono: tela = 0.0 -> quadro `12 3E 00 00 50`.
  - maximo da faixa do DFM: tela = 3.0 -> quadro `12 3E 00 03 53`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `CheckPassPRail` — Ritorno automatico a benzina con la pressione del rail

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 3838 `[OBS-COD]`. Pai: `ScrollBoxLivello`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Ritorno automatico a benzina con la pressione del rail"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=38 Top=355 Largura=759 Altura=31; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTCHECKPASSPRAIL`: "Automatic change-back to petrol whit rail pressure".

**O que isso faz (linguagem simples)**

- Volta automatica para gasolina usando a pressao do rail. `[OBS-COD]` (fonte: dica do proprio ProgBase (HINTCHECKPASSPRAIL)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 1, mascara 0x0100, caso do setter 32, papel "caixa (Checked)", condicao "obj+0x80D!=0"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 1 = 0x1051 -> bit 0x0100: **desligado** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckPassPRailClick` (0x4BCB10) `[OBS-COD]`
  - SetFlag1 caso 32 -> FLAG_CONF1 palavra 1 mascara 0x0100 em `0x4BCB39`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 1 mascara 0x0100): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - desligar bit (FLAG_CONF1 palavra 1 mascara 0x0100): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetFlag1(caso 32)` em `0x4AAE7F` (TabSensoreShow) — incondicional
- `Visible := verdadeiro` em `0x4AB356` (TabSensoreShow) — incondicional

### `CheckBassaPressione` — Reversão automática por baixa pressão impostável

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 3852 `[OBS-COD]`. Pai: `ScrollBoxLivello`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Ritorno automatico con bassa pressione impostabile"; eng = "Automatic change back with low pressure setting possible"; por = "Reversão automática por baixa pressão impostável" `[OBS-COD]`.
- Posicao no DFM: Left=38 Top=394 Largura=760 Altura=31; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTCHECKBASSAPRESSIONE`: "Automatic change-back with low pressure setting".

**O que isso faz (linguagem simples)**

- Deixa definir o valor de pressao baixa que dispara a volta para gasolina, independente do item de volta automatica. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.19).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 1, mascara 0x0040, caso do setter 30, papel "caixa (Checked)", condicao "obj+0x80D!=0"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 1 = 0x1051 -> bit 0x0040: **LIGADO** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckBassaPressioneClick` (0x4BCA04) `[OBS-COD]`
  - muda Visible de `LabelBassaPressione` em `0x4BCA34`
  - muda Visible de `EditBassaPressione` em `0x4BCA66`
  - muda Visible de `LabBassaPressione` em `0x4BCA98`
  - SetFlag1 caso 30 -> FLAG_CONF1 palavra 1 mascara 0x0040 em `0x4BCAC2`
  - GetEsc codigo 68 -> vetor PRESS_BASSA_RETROPAS_LR (SC 152) em `0x4BCAE2`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 1 mascara 0x0040): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - desligar bit (FLAG_CONF1 palavra 1 mascara 0x0040): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetFlag1(caso 30)` em `0x4AAE61` (TabSensoreShow) — incondicional
- `Visible := verdadeiro` em `0x4AB471` (TabSensoreShow) — incondicional

### `EditBassaPressione` — Valor da baixa pressão para a reversão

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 3866 `[OBS-COD]`. Pai: `ScrollBoxLivello`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelBassaPressione` (associacao INFERIDO)): ita = "Valore di bassa pressione per il ritorno"; eng = "Low pressure value for change back"; por = "Valor da baixa pressão para a reversão" `[OBS-COD]`.
- Unidade ao lado: **bar** `[OBS-COD]` (rotulo `LabBassaPressione`).
- Posicao no DFM: Left=555 Top=430 Largura=71 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0.005, maximo 5.0, passo (Precision) 0.005. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Pressao (0,4 a 2 bar) abaixo da qual a ECU volta para gasolina, com buzzer e LEDs de gasolina e gas acesos. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.19).
- Manual SW 2.16.4 C, p.19 (linha 902 de sw.txt): "C Low pressure value for change back: (range 0.4;2" `[OBS-COD]`.

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: {"LR ([SD+0x80D]!=0)": 68, "demais ([SD+0x80D]==0)": -1} (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia LR: vetor **PRESS_BASSA_RETROPAS_LR** (SerialCode 152, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x4370A6): `valor_bruto = trunc((v * 1000))` com `v` = valor digitado.
    - ECU -> tela (getter 0x43592F): `valor_na_tela = (SD[0x2D80] * 0.001)`.
  - familia demais: sem codigo nesta familia (-1)

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditRifLambdaKeyPress` (0x4A6A00) `[OBS-COD]`
  - SetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A45` (handler generico)
  - GetIdx com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A64` (handler generico)
  - SetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6A99` (handler generico)
  - GetEsc com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `0x4A6AB1` (handler generico)
- `OnEnter` -> `EditGiriCambioEnter` (0x4A98D8) `[OBS-COD]`
- `OnExit` -> `EditGiriCambioExit` (0x4A9CC0) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := verdadeiro` em `0x4BCA66` (CheckBassaPressioneClick) — incondicional
- `Visible := calculado` em `0x4AB524` (TabSensoreShow) — incondicional

### `CheckElettrovalvole` — Electroválvulas gás abertas com antecedencia

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 3886 `[OBS-COD]`. Pai: `ScrollBoxLivello`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Elettrovalvole gas aperte con anticipo"; eng = "Gas electrovalves opened in advance"; por = "Electroválvulas gás abertas com antecedencia" `[OBS-COD]`.
- Posicao no DFM: Left=38 Top=480 Largura=745 Altura=31; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTCHECKELETTROVALVOLE`: "Gas elettrovalves opened in advance".

**O que isso faz (linguagem simples)**

- Abre as eletrovalvulas de gas uns 5 segundos antes dos injetores; a bomba de gasolina nao e desligada; pode dar problema com chips de avanco. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.19).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 0, mascara 0x0080, caso do setter 19, papel "caixa (Checked)", condicao "sempre"; origem: flags-do-original.json.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF3** (SerialCode 250), palavra 0, mascara 0x0004, caso do setter 1, papel "None", condicao "sempre"; origem: handler CheckElettrovalvoleClick.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 0 = 0x2486 -> bit 0x0080: **LIGADO** `[OBS-FIO]` (LOG linha 1102844).
- FLAG_CONF3 palavra 0 = 0x0000 -> bit 0x0004: **desligado** `[OBS-FIO]` (LOG linha 1102872).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckElettrovalvoleClick` (0x4C5048) `[OBS-COD]`
  - SetFlag1 caso 19 -> FLAG_CONF1 palavra 0 mascara 0x0080 em `0x4C5099`
  - SetIdx codigo 122 -> vetor TEMPI_ANTICIPI_EV (SC 238) em `0x4C50CD`
  - SetIdx codigo 122 -> vetor TEMPI_ANTICIPI_EV (SC 238) em `0x4C50E5`
  - SetIdx codigo 122 -> vetor TEMPI_ANTICIPI_EV (SC 238) em `0x4C50FF`
  - SetIdx codigo 122 -> vetor TEMPI_ANTICIPI_EV (SC 238) em `0x4C5117`
  - GetIdx codigo 122 -> vetor TEMPI_ANTICIPI_EV (SC 238) em `0x4C5128`
  - GetIdx codigo 122 -> vetor TEMPI_ANTICIPI_EV (SC 238) em `0x4C514A`
  - SetIdx codigo 122 -> vetor TEMPI_ANTICIPI_EV (SC 238) em `0x4C518A`
  - SetIdx codigo 122 -> vetor TEMPI_ANTICIPI_EV (SC 238) em `0x4C51A4`
  - GetIdx codigo 122 -> vetor TEMPI_ANTICIPI_EV (SC 238) em `0x4C51B5`
  - GetFlag3 caso 1 -> FLAG_CONF3 palavra 0 mascara 0x0004 em `0x4C51E9`
  - muda Text de um controle nao identificado pela analise estatica em `0x4C520B`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 0 mascara 0x0080): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - desligar bit (FLAG_CONF1 palavra 0 mascara 0x0080): 2486 1051 -> 2406 1051, quadro `35 03 00 06 24 51 10 C3`.
  - ligar bit (FLAG_CONF3 palavra 0 mascara 0x0004): 0000 0000 -> 0004 0000, quadro `35 FA 00 04 00 00 00 33`.
  - desligar bit (FLAG_CONF3 palavra 0 mascara 0x0004): 0000 0000 -> 0000 0000, quadro `35 FA 00 00 00 00 00 2F`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `CheckAlimentazione` — Use of wire EV2 for STAP

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 3900 `[OBS-COD]`. Pai: `ScrollBoxLivello`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Utilizzo filo EV2 per STAP"; eng = "Use of wire EV2 for STAP"; por = "Use of wire EV2 for STAP" `[OBS-COD]`.
- Posicao no DFM: Left=38 Top=628 Largura=745 Altura=32; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Usa o fio da EV2 para alimentar o STAP. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 1, mascara 0x0800, caso do setter 26, papel "caixa (Checked)", condicao "sempre"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 1 = 0x1051 -> bit 0x0800: **desligado** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckAlimentazioneClick` (0x4C6780) `[OBS-COD]`
  - SetFlag1 caso 26 -> FLAG_CONF1 palavra 1 mascara 0x0800 em `0x4C67B0`
  - chama o metodo do TStreamDati `0x434BA0` em `0x4C67BA` (le: -; escreve: -)
  - GetFlag1 caso 26 -> FLAG_CONF1 palavra 1 mascara 0x0800 em `0x4C67CF`
  - chama o metodo do TStreamDati `0x431878` em `0x4C67E5` (le: STATO_DIAGNOSI, STATO_DIAGNOSI_LR; escreve: -)
  - chama o metodo do TStreamDati `0x4318F8` em `0x4C67F7` (le: ABIL_DIAGNOSI, ABIL_DIAGNOSI_LR, STATO_DIAGNOSI, STATO_DIAGNOSI_LR; escreve: -)
  - chama o metodo do TStreamDati `0x431878` em `0x4C680B` (le: STATO_DIAGNOSI, STATO_DIAGNOSI_LR; escreve: -)
  - chama o metodo do TStreamDati `0x4318F8` em `0x4C681D` (le: ABIL_DIAGNOSI, ABIL_DIAGNOSI_LR, STATO_DIAGNOSI, STATO_DIAGNOSI_LR; escreve: -)
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 1 mascara 0x0800): 2486 1051 -> 2486 1851, quadro `35 03 00 86 24 51 18 4B`.
  - desligar bit (FLAG_CONF1 palavra 1 mascara 0x0800): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado` em `0x4C8C80` (CheckTaglioPompaBenzinaClick) — SE [obj+0x1280] == 0 E chamada virtual dword ptr [edx + 0x104] != 0 E sub_434BA0 == 0
- `Caption := calculado` em `0x4AAC52` (TabSensoreShow) — SE sub_434BA0 != 0
- `Caption := calculado` em `0x4AACA3` (TabSensoreShow) — SE sub_434BA0 == 0
- `Visible := verdadeiro` em `0x4AAD1A` (TabSensoreShow) — incondicional
- `Checked/ItemIndex := calculado por GetFlag1(caso 26)` em `0x4AAD49` (TabSensoreShow) — incondicional

### `DrawGridPressPass` — Pressões para a reversão gasolina

Tipo: **grade** (`TDrawGrid`), DFM linha 3914 `[OBS-COD]`. Pai: `ScrollBoxLivello`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelPressPass` (associacao INFERIDO)): ita = "Pressioni per il retropassaggio benzina"; eng = "Pressure for change back to petrol"; por = "Pressões para a reversão gasolina" `[OBS-COD]`.
- Posicao no DFM: Left=113 Top=727 Largura=652 Altura=68; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTPRESSPASS`: "Pressures for change-back to petrol".

**O que isso faz (linguagem simples)**

- Tabela de pressoes (por tempo de injecao) abaixo das quais volta para gasolina; cada valor pode ser mudado em absoluto, linear ou percentual (Landi e Keihin, nao Matrix). `[OBS-COD]` (fonte: manual SW 2.16.4 C p.19).
- Manual SW 2.16.4 C, p.19 (linha 929 de sw.txt): "E Pressure for change back to petrol: LROMEGAS" `[OBS-COD]`.

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `DrawGridPressPassKeyPress` (0x4B6F58) `[OBS-COD]`
- `OnDblClick` -> `DrawGridPressPassDblClick` (0x4B6EF4) `[OBS-COD]`
- `OnKeyDown` -> `DrawGridKKeyDown` (0x4B2514) `[OBS-COD]`
- `OnDrawCell` -> `DrawGridPressPassDrawCell` (0x4B6B1C) `[OBS-COD]`: desenho das celulas da grade (exibicao); nao envia nada a ECU

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Grade: cada celula editada chama um setter indexado (SetIdx) do handler `KeyPress`/`DblClick`; quadros por celula: `14 SC lo hi idx valor ck` (SetNumber+indice). Quadros exatos por celula: **DESCONHECIDO** (nao executados aqui).

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4AB2A0` (TabSensoreShow) — incondicional

### `EditBassaPressioneMp` — Valore di bassa pressione per il ritorno

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 3941 `[OBS-COD]`. Pai: `ScrollBoxLivello`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelBassaPressioneMp` (associacao INFERIDO)): ita = "Valore di bassa pressione per il ritorno"; eng = "-"; por = "-" `[OBS-COD]`.
- Unidade ao lado: **bar** `[OBS-COD]` (rotulo `LabBassaPressione`).
- Posicao no DFM: Left=555 Top=430 Largura=71 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0.2, maximo 2.0, passo (Precision) 0.005. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTBASSAPRESSIONEMP`: "Value of low pressure for change-back".

**O que isso faz (linguagem simples)**

- Valor de pressao baixa para a volta a gasolina quando o sensor MAP esta configurado. `[OBS-COD]` (fonte: dica do proprio ProgBase (HINTBASSAPRESSIONEMP)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditBassaPressioneMpKeyPress` (0x4C526C) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x43277C` em `0x4C52E4` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x4327C0` em `0x4C5415` (le: RIF_PRESS_DIFF; escreve: -)
  - chama o metodo do TStreamDati `0x432670` em `0x4C5429` (le: COEFF_PRESS_DIFF; escreve: -)
- `OnEnter` -> `EditGiriCambioEnter` (0x4A98D8) `[OBS-COD]`
- `OnExit` -> `EditGiriCambioExit` (0x4A9CC0) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4AB59D` (TabSensoreShow) — incondicional

### `EditAnticipoEV1` — EV1 opening advance

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 3959 `[OBS-COD]`. Pai: `ScrollBoxLivello`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelAnticipoEV1` (associacao INFERIDO)): ita = "Anticipo apertura EV1"; eng = "EV1 opening advance"; por = "EV1 opening advance" `[OBS-COD]`.
- Unidade ao lado: **s** `[OBS-COD]` (rotulo `LabAnticipoEV1`).
- Posicao no DFM: Left=555 Top=516 Largura=71 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 10.0, passo (Precision) 0.1. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Quanto antes a eletrovalvula 1 abre em relacao a injecao. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: {"LR ([SD+0x80D]!=0)": -1, "demais ([SD+0x80D]==0)": 122}, indice 0 (campo +0x2EC (DataCode do TNumericEdit)).
  - familia LR: sem codigo nesta familia (-1)
  - Familia demais: vetor **TEMPI_ANTICIPI_EV** (SerialCode 238, TAebVector, indexado) `[OBS-COD]`.
    - tela -> ECU (setter 0x437A0A): `valor_bruto = trunc((v * 10))` com `v` = valor digitado.
    - ECU -> tela (getter 0x4362BB): `valor_na_tela = (SD[0x2B50 + i*4] * 0.1)`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- TEMPI_ANTICIPI_EV (SC 238): bruto [0, 50] (elemento 0 = 0); na tela: **nao calculado** `[OBS-FIO, tela DERIVADO]` (LOG linha 1110808).

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
  - maximo da faixa do DFM: tela = 10.0 -> quadro `13 EE 00 00 64 65`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditAnticipoEV2` — EV2 opening advance

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 3976 `[OBS-COD]`. Pai: `ScrollBoxLivello`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelAnticipoEV2` (associacao INFERIDO)): ita = "Anticipo apertura EV2"; eng = "EV2 opening advance"; por = "EV2 opening advance" `[OBS-COD]`.
- Unidade ao lado: **s** `[OBS-COD]` (rotulo `LabAnticipoEV2`).
- Posicao no DFM: Left=555 Top=556 Largura=71 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 10.0, passo (Precision) 0.1. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Quanto antes a eletrovalvula 2 abre em relacao a injecao. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: {"LR ([SD+0x80D]!=0)": -1, "demais ([SD+0x80D]==0)": 122}, indice 1 (campo +0x2EC (DataCode do TNumericEdit)).
  - familia LR: sem codigo nesta familia (-1)
  - Familia demais: vetor **TEMPI_ANTICIPI_EV** (SerialCode 238, TAebVector, indexado) `[OBS-COD]`.
    - tela -> ECU (setter 0x437A0A): `valor_bruto = trunc((v * 10))` com `v` = valor digitado.
    - ECU -> tela (getter 0x4362BB): `valor_na_tela = (SD[0x2B50 + i*4] * 0.1)`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- TEMPI_ANTICIPI_EV (SC 238): bruto [0, 50] (elemento 1 = 50); na tela: **nao calculado** `[OBS-FIO, tela DERIVADO]` (LOG linha 1110808).

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
  - maximo da faixa do DFM: tela = 10.0 -> quadro `13 EE 00 01 64 66`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `CheckTaglioPompaBenzina` — Petrol pump cutting

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 3994 `[OBS-COD]`. Pai: `ScrollBoxLivello`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Taglio pompa benzina"; eng = "Petrol pump cutting"; por = "Petrol pump cutting" `[OBS-COD]`.
- Posicao no DFM: Left=38 Top=598 Largura=735 Altura=32; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Corta (desliga) a bomba de gasolina enquanto roda a gas. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF3** (SerialCode 250), palavra 0, mascara 0x0004, caso do setter 1, papel "caixa (Checked)", condicao "sempre"; origem: flags-do-original.json.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF2** (SerialCode 192), palavra 0, mascara 0x0200, caso do setter 9, papel "None", condicao "sempre"; origem: handler CheckTaglioPompaBenzinaClick.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 1, mascara 0x4000, caso do setter 24, papel "None", condicao "sempre"; origem: handler CheckTaglioPompaBenzinaClick.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF3 palavra 0 = 0x0000 -> bit 0x0004: **desligado** `[OBS-FIO]` (LOG linha 1102872).
- FLAG_CONF2 palavra 0 = 0x0001 -> bit 0x0200: **desligado** `[OBS-FIO]` (LOG linha 1102816).
- FLAG_CONF1 palavra 1 = 0x1051 -> bit 0x4000: **desligado** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckTaglioPompaBenzinaClick` (0x4C8B68) `[OBS-COD]`
  - SetFlag3 caso 1 -> FLAG_CONF3 palavra 0 mascara 0x0004 em `0x4C8BA8`
  - SetFlag2 caso 9 -> FLAG_CONF2 palavra 0 mascara 0x0200 em `0x4C8BCE`
  - SetIdx codigo 111 -> vetor CHANGE_OVER_CILYNDER_DELAY (SC 226) em `0x4C8C00`
  - SetEsc codigo 41 -> vetor NUMERO_INJ_BENZINA_CUTOFF (SC 106) em `0x4C8C13`
  - chama o metodo do TStreamDati `0x43400C` em `0x4C8C3D` (le: -; escreve: -)
  - SetFlag1 caso 24 -> FLAG_CONF1 palavra 1 mascara 0x4000 em `0x4C8C62`
  - chama o metodo do TStreamDati `0x434BA0` em `0x4C8C6C` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x434C54` em `0x4C8C8B` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x431878` em `0x4C8CA5` (le: STATO_DIAGNOSI, STATO_DIAGNOSI_LR; escreve: -)
  - chama o metodo do TStreamDati `0x4318F8` em `0x4C8CBA` (le: ABIL_DIAGNOSI, ABIL_DIAGNOSI_LR, STATO_DIAGNOSI, STATO_DIAGNOSI_LR; escreve: -)
  - chama o metodo do TStreamDati `0x434A14` em `0x4C8CC4` (le: -; escreve: -)
  - chama o metodo do TStreamDati `0x434A38` em `0x4C8D3C` (le: -; escreve: -)
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF3 palavra 0 mascara 0x0004): 0000 0000 -> 0004 0000, quadro `35 FA 00 04 00 00 00 33`.
  - desligar bit (FLAG_CONF3 palavra 0 mascara 0x0004): 0000 0000 -> 0000 0000, quadro `35 FA 00 00 00 00 00 2F`.
  - ligar bit (FLAG_CONF2 palavra 0 mascara 0x0200): 0001 0000 -> 0201 0000, quadro `35 C0 00 01 02 00 00 F8`.
  - desligar bit (FLAG_CONF2 palavra 0 mascara 0x0200): 0001 0000 -> 0001 0000, quadro `35 C0 00 01 00 00 00 F6`.
  - ligar bit (FLAG_CONF1 palavra 1 mascara 0x4000): 2486 1051 -> 2486 5051, quadro `35 03 00 86 24 51 50 83`.
  - desligar bit (FLAG_CONF1 palavra 1 mascara 0x4000): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := verdadeiro` em `0x4AAB62` (TabSensoreShow) — incondicional
- `Checked/ItemIndex := calculado por GetFlag3(caso 1)` em `0x4AAB7F` (TabSensoreShow) — incondicional

### `EditRpmAttivPompa` — Pump activation RPM for automatic contribution

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 4014 `[OBS-COD]`. Pai: `ScrollBoxLivello`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelRpmAttivPompa` (associacao INFERIDO)): ita = "RPM attiv. pompa per contrib. automatico"; eng = "Pump activation RPM for automatic contribution"; por = "Pump activation RPM for automatic contribution" `[OBS-COD]`.
- Unidade ao lado: **rpm** `[OBS-COD]` (rotulo `LabRpmAttivPompa`).
- Posicao no DFM: Left=643 Top=818 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 9000.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Rotacao em que a bomba de gasolina e reativada na contribuicao automatica de gasolina. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 125, indice 0 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **TAGLIO_POMPA_BENZINA** (SerialCode 268, TAebVector, indexado) `[OBS-COD]`.
    - tela -> ECU (setter 0x437B52): `valor_bruto = trunc(v)` com `v` = valor digitado.
    - ECU -> tela (getter 0x4363B2): `valor_na_tela = SD[0x2B5C + i*4]`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- TAGLIO_POMPA_BENZINA (SC 268): o LOG nao traz leitura deste SerialCode `[DESCONHECIDO]`.

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
  - maximo da faixa do DFM: tela = 9000.0 -> quadro `14 0C 01 00 28 23 6C`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4AB619` (TabSensoreShow) — incondicional

### `EditRpmAnticipoPompa` — RPM pump activation advance in voluntary contrib.

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 4038 `[OBS-COD]`. Pai: `ScrollBoxLivello`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelRpmAnticipoPompa` (associacao INFERIDO)): ita = "RPM anticipo attiv. pompa per contrib. volontario"; eng = "RPM pump activation advance in voluntary contrib."; por = "RPM pump activation advance in voluntary contrib." `[OBS-COD]`.
- Unidade ao lado: **rpm** `[OBS-COD]` (rotulo `LabRpmAnticipoPompa`).
- Posicao no DFM: Left=643 Top=858 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 9000.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Rotacao de antecipacao para reativar a bomba na contribuicao voluntaria. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 125, indice 1 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **TAGLIO_POMPA_BENZINA** (SerialCode 268, TAebVector, indexado) `[OBS-COD]`.
    - tela -> ECU (setter 0x437B52): `valor_bruto = trunc(v)` com `v` = valor digitado.
    - ECU -> tela (getter 0x4363B2): `valor_na_tela = SD[0x2B5C + i*4]`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- TAGLIO_POMPA_BENZINA (SC 268): o LOG nao traz leitura deste SerialCode `[DESCONHECIDO]`.

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
  - maximo da faixa do DFM: tela = 9000.0 -> quadro `14 0C 01 01 28 23 6D`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4AB6B0` (TabSensoreShow) — incondicional

### `EditTempoAttivPompa` — Minimum time for petrol pump activation

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 4063 `[OBS-COD]`. Pai: `ScrollBoxLivello`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTempoAttivPompa` (associacao INFERIDO)): ita = "Tempo minimo attiv. pompa benzina"; eng = "Minimum time for petrol pump activation"; por = "Minimum time for petrol pump activation" `[OBS-COD]`.
- Unidade ao lado: **s** `[OBS-COD]` (rotulo `LabTempoAttivPompa`).
- Posicao no DFM: Left=643 Top=898 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 65.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Tempo minimo que a bomba de gasolina fica ligada quando reativada. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 125, indice 2 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **TAGLIO_POMPA_BENZINA** (SerialCode 268, TAebVector, indexado) `[OBS-COD]`.
    - tela -> ECU (setter 0x437B52): `valor_bruto = trunc(v)` com `v` = valor digitado.
    - ECU -> tela (getter 0x4363B2): `valor_na_tela = SD[0x2B5C + i*4]`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- TAGLIO_POMPA_BENZINA (SC 268): o LOG nao traz leitura deste SerialCode `[DESCONHECIDO]`.

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
  - maximo da faixa do DFM: tela = 65.0 -> quadro `14 0C 01 02 41 00 64`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4AB747` (TabSensoreShow) — incondicional

### `EditPeriodoRiattivPompa` — Pump reactivation period

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 4088 `[OBS-COD]`. Pai: `ScrollBoxLivello`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelPeriodoRiattivPompa` (associacao INFERIDO)): ita = "Periodo riattivazione pompa"; eng = "Pump reactivation period"; por = "Pump reactivation period" `[OBS-COD]`.
- Unidade ao lado: **min** `[OBS-COD]` (rotulo `LabPeriodoRiattivPompa`).
- Posicao no DFM: Left=643 Top=938 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 100.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Periodo entre reativacoes da bomba de gasolina. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: 125, indice 4 (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia unico: vetor **TAGLIO_POMPA_BENZINA** (SerialCode 268, TAebVector, indexado) `[OBS-COD]`.
    - tela -> ECU (setter 0x437B52): `valor_bruto = trunc(v)` com `v` = valor digitado.
    - ECU -> tela (getter 0x4363B2): `valor_na_tela = SD[0x2B5C + i*4]`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- TAGLIO_POMPA_BENZINA (SC 268): o LOG nao traz leitura deste SerialCode `[DESCONHECIDO]`.

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
  - maximo da faixa do DFM: tela = 100.0 -> quadro `14 0C 01 04 64 00 89`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4AB7DE` (TabSensoreShow) — incondicional

### `CheckEnabledDhlpDiagnosis` — Abilita diagnosi perdite gas

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 4113 `[OBS-COD]`. Pai: `ScrollBoxLivello`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Abilita diagnosi perdite gas"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=38 Top=230 Largura=435 Altura=31; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Liga o diagnostico de vazamento de gas. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckEnabledDhlpDiagnosisClick` (0x4C975C) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x431878` em `0x4C978C` (le: STATO_DIAGNOSI, STATO_DIAGNOSI_LR; escreve: -)
  - chama o metodo do TStreamDati `0x4318F8` em `0x4C97A1` (le: ABIL_DIAGNOSI, ABIL_DIAGNOSI_LR, STATO_DIAGNOSI, STATO_DIAGNOSI_LR; escreve: -)
  - chama o metodo do TStreamDati `0x431878` em `0x4C97BB` (le: STATO_DIAGNOSI, STATO_DIAGNOSI_LR; escreve: -)
  - chama o metodo do TStreamDati `0x4318F8` em `0x4C97D0` (le: ABIL_DIAGNOSI, ABIL_DIAGNOSI_LR, STATO_DIAGNOSI, STATO_DIAGNOSI_LR; escreve: -)
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por sub_431878` em `0x4AAEA8` (TabSensoreShow) — incondicional

### `CheckAttivaPompaInRiserva` — Abilita riattivazione pompa in riserva

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 4127 `[OBS-COD]`. Pai: `ScrollBoxLivello`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Abilita riattivazione pompa in riserva"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=38 Top=983 Largura=745 Altura=32; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Reativa a bomba de gasolina quando o gas esta na reserva. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF3** (SerialCode 250), palavra 0, mascara 0x0800, caso do setter 8, papel "caixa (Checked)", condicao "sempre"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF3 palavra 0 = 0x0000 -> bit 0x0800: **desligado** `[OBS-FIO]` (LOG linha 1102872).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckAttivaPompaInRiservaClick` (0x4CB030) `[OBS-COD]`
  - SetFlag3 caso 8 -> FLAG_CONF3 palavra 0 mascara 0x0800 em `0x4CB059`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF3 palavra 0 mascara 0x0800): 0000 0000 -> 0800 0000, quadro `35 FA 00 00 08 00 00 37`.
  - desligar bit (FLAG_CONF3 palavra 0 mascara 0x0800): 0000 0000 -> 0000 0000, quadro `35 FA 00 00 00 00 00 2F`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetFlag3(caso 8)` em `0x4AAEC6` (TabSensoreShow) — incondicional
- `Visible := verdadeiro` em `0x4AB863` (TabSensoreShow) — incondicional

### `CheckRiconoscimentoPieno` — Recognition of full

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 4140 `[OBS-COD]`. Pai: `ScrollBoxLivello`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Riconoscimento pieno"; eng = "Recognition of full"; por = "Recognition of full" `[OBS-COD]`.
- Posicao no DFM: Left=489 Top=230 Largura=326 Altura=31; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Reconhece quando o tanque de gas foi cheio. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF3** (SerialCode 250), palavra 0, mascara 0x1000, caso do setter 9, papel "caixa (Checked)", condicao "sempre"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF3 palavra 0 = 0x0000 -> bit 0x1000: **desligado** `[OBS-FIO]` (LOG linha 1102872).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckRiconoscimentoPienoClick` (0x4CB12C) `[OBS-COD]`
  - SetFlag3 caso 9 -> FLAG_CONF3 palavra 0 mascara 0x1000 em `0x4CB155`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF3 palavra 0 mascara 0x1000): 0000 0000 -> 1000 0000, quadro `35 FA 00 00 10 00 00 3F`.
  - desligar bit (FLAG_CONF3 palavra 0 mascara 0x1000): 0000 0000 -> 0000 0000, quadro `35 FA 00 00 00 00 00 2F`.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := verdadeiro` em `0x4AABE7` (TabSensoreShow) — incondicional
- `Checked/ItemIndex := calculado por GetFlag3(caso 9)` em `0x4AAC04` (TabSensoreShow) — incondicional

### `CheckBoxEV2NotConnected` — EV2 non collegata

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 4153 `[OBS-COD]`. Pai: `ScrollBoxLivello`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "EV2 non collegata"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=38 Top=657 Largura=745 Altura=32; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Informa que a eletrovalvula 2 nao esta ligada. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckBoxEV2NotConnectedClick` (0x4C6710) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x431878` em `0x4C673D` (le: STATO_DIAGNOSI, STATO_DIAGNOSI_LR; escreve: -)
  - chama o metodo do TStreamDati `0x4318F8` em `0x4C674F` (le: ABIL_DIAGNOSI, ABIL_DIAGNOSI_LR, STATO_DIAGNOSI, STATO_DIAGNOSI_LR; escreve: -)
  - chama o metodo do TStreamDati `0x431878` em `0x4C6766` (le: STATO_DIAGNOSI, STATO_DIAGNOSI_LR; escreve: -)
  - chama o metodo do TStreamDati `0x4318F8` em `0x4C6778` (le: ABIL_DIAGNOSI, ABIL_DIAGNOSI_LR, STATO_DIAGNOSI, STATO_DIAGNOSI_LR; escreve: -)
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := verdadeiro` em `0x4AADF1` (TabSensoreShow) — incondicional
- `Checked/ItemIndex := calculado por sub_431878` em `0x4AAE43` (TabSensoreShow) — incondicional

## Rotulos com regra de mostrar/esconder ou texto dinamico

### `LabelBassaPressione` — "Valor da baixa pressão para a reversão"

Texto: ita "Valore di bassa pressione per il ritorno" / eng "Low pressure value for change back" / por "Valor da baixa pressão para a reversão" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := verdadeiro` em `0x4BCA34` (CheckBassaPressioneClick) — incondicional
- `Visible := verdadeiro` em `0x4AB510` (TabSensoreShow) — incondicional

### `LabBassaPressione` — "bar"

Texto: ita "bar" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := verdadeiro` em `0x4BCA98` (CheckBassaPressioneClick) — incondicional
- `Visible := calculado` em `0x4AB538` (TabSensoreShow) — incondicional

### `LabelPressPass` — "Pressões para a reversão gasolina"

Texto: ita "Pressioni per il retropassaggio benzina" / eng "Pressure for change back to petrol" / por "Pressões para a reversão gasolina" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := verdadeiro` em `0x4AB28C` (TabSensoreShow) — incondicional

### `LabTinjPress` — "t.inj.gasolina"

Texto: ita "t.inj.b" / eng "t.inj.p" / por "t.inj.gasolina" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4AB2B4` (TabSensoreShow) — incondicional

### `LabPRail` — "P rail"

Texto: ita "P rail" / eng "P rail" / por "P rail" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4AB2C8` (TabSensoreShow) — incondicional

### `LabelBassaPressioneMp` — "Valore di bassa pressione per il ritorno"

Texto: ita "Valore di bassa pressione per il ritorno" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := verdadeiro` em `0x4AB589` (TabSensoreShow) — incondicional

### `LabBassaPressioneMp` — "bar"

Texto: ita "bar" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4AB5B1` (TabSensoreShow) — incondicional

### `LabelRpmAttivPompa` — "Pump activation RPM for automatic contribution"

Texto: ita "RPM attiv. pompa per contrib. automatico" / eng "Pump activation RPM for automatic contribution" / por "Pump activation RPM for automatic contribution" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := verdadeiro` em `0x4AB605` (TabSensoreShow) — incondicional

### `LabRpmAttivPompa` — "rpm"

Texto: ita "rpm" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4AB64F` (TabSensoreShow) — incondicional

### `LabRpmAnticipoPompa` — "rpm"

Texto: ita "rpm" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4AB6E6` (TabSensoreShow) — incondicional

### `LabelRpmAnticipoPompa` — "RPM pump activation advance in voluntary contrib."

Texto: ita "RPM anticipo attiv. pompa per contrib. volontario" / eng "RPM pump activation advance in voluntary contrib." / por "RPM pump activation advance in voluntary contrib." `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := verdadeiro` em `0x4AB69C` (TabSensoreShow) — incondicional

### `LabelTempoAttivPompa` — "Minimum time for petrol pump activation"

Texto: ita "Tempo minimo attiv. pompa benzina" / eng "Minimum time for petrol pump activation" / por "Minimum time for petrol pump activation" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := verdadeiro` em `0x4AB733` (TabSensoreShow) — incondicional

### `LabTempoAttivPompa` — "s"

Texto: ita "s" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4AB77D` (TabSensoreShow) — incondicional

### `LabelPeriodoRiattivPompa` — "Pump reactivation period"

Texto: ita "Periodo riattivazione pompa" / eng "Pump reactivation period" / por "Pump reactivation period" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := verdadeiro` em `0x4AB7CA` (TabSensoreShow) — incondicional

### `LabPeriodoRiattivPompa` — "min"

Texto: ita "min" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4AB814` (TabSensoreShow) — incondicional

### `LabelMinLevel` — "0"

Texto: ita "0" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=sim.
- `Caption := calculado` em `0x4C5FA3` (ButtonDoLevelClick) — SE cmp esi, edi >  E cmp esi, edi < 
- `Caption := calculado` em `0x4C5A53` (ButtonMinLevelClick) — SE sub_921FCC != 0 E (byte[obj+0x1838] & 0x10) == 0 E [obj+0x1818] != 0
- `Caption := calculado` em `0x4AA982` (TabSensoreShow) — incondicional

### `LabelMaxLevel` — "0"

Texto: ita "0" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=sim.
- `Caption := calculado` em `0x4C5FDB` (ButtonDoLevelClick) — SE cmp esi, edi >  E cmp esi, edi < 
- `Caption := calculado` em `0x4C5BA6` (ButtonMaxLevelClick) — SE sub_921FCC != 0 E (byte[obj+0x1838] & 0x10) == 0 E [obj+0x1818] != 0
- `Caption := calculado` em `0x4AA9C6` (TabSensoreShow) — incondicional

## Lacunas desta aba

- Edits sem codigo de dado na rotina 0x48A228 (o valor passa por metodo proprio do TStreamDati; formula nao decodificada aqui, `DESCONHECIDO`): `EditLivello1`, `EditLivello2`, `EditLivello3`, `EditLivello4`, `EditBassaPressioneMp`.
- Combos cujo valor e gravado por metodo proprio (sem bit nem codigo generico): `ComboSensore`. Os vetores tocados aparecem na lista de acessos do handler; o significado de cada opcao no fio e `DESCONHECIDO` quando nao ha formula.
- Grades (`TDrawGrid`): o conteudo e desenhado em `OnDrawCell`; o conteudo e os quadros por celula nao foram decodificados celula a celula: `DrawGridPressPass`.
- Ligados a vetor mas sem leitura no LOG do dono (a ECU nao foi consultada nesse SerialCode): `EditBassaPressione`, `EditRpmAttivPompa`, `EditRpmAnticipoPompa`, `EditTempoAttivPompa`, `EditPeriodoRiattivPompa`.
- Visibilidade/habilitacao: as regras listadas sao as que o codigo escreve; a combinacao final depende de dados da ECU (modelo, familia LR, MASK_FUNCTION) que o LOG so mostra em parte. O dono usa familia nao-LR (INFERIDO: o original leu SC 192, nunca 193).
- A associacao rotulo<->campo e por nome/geometria (`INFERIDO`), e a chave da dica (HINT...) segue o nome do componente (`INFERIDO`).

