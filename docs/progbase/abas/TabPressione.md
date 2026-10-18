# TabPressione — Pressione

Nome na tela: ita "Pressione" / eng "None" / por "None" `[OBS-COD]`. Aba 5 de 16 do grupo D3a. Rotina de abertura (OnShow): `TabPressioneShow` em `0x4AE788` `[OBS-COD]`. TabVisible de projeto: False.

Legenda das marcas: `OBS-COD` = lido do DFM/codigo do ProgBase.exe (ou executado no emulador Unicorn); `OBS-FIO` = visto no Portmon da ECU do dono; `DERIVADO` = calculado a partir dessas observacoes; `INFERIDO` = deducao; `DESCONHECIDO` = sem prova.

**Resumo**: 31 controles no DFM (TButton=1, TCheckBox=1, TGroupBox=2, TKeyComboBox=3, TLabel=16, TNumericEdit=8); 13 interativos; 1 com codigo de dado e formula; 3 com caixa/bit; 6 com valor lido no LOG. Fonte do .exe sha256 `8a2d297c8c21...`.

## Como o ProgBase trata estes controles (valido para a aba inteira)

- **Campo numerico (`TNumericEdit`)** `[OBS-COD]`: ao entrar no campo, `EditGiriCambioEnter` (0x4A98D8) mostra no rotulo `LabelLimite` o texto `(minimo;maximo)` do proprio campo; ao sair, `EditGiriCambioExit` (0x4A9CC0) esconde esse rotulo. Nada e enviado a ECU nesses dois momentos. Ao apertar **Enter**, o `TNumericEdit` limita o numero a [minimo;maximo] (so se minimo!=0 ou maximo!=0; 0x96C170) e o handler generico `EditRifLambdaKeyPress` (0x4A6A00) chama `TStreamDati.Set*` com o *codigo de dado* do campo (campo `+0x2EC`, indice `+0x2F0`; amarrados em 0x48A228): o valor e convertido pela formula do caso, gravado no cache do programa e enviado como SetNumber (escalar) ou SetNumber+indice (vetor). Depois o handler le o valor de volta do cache (`Get*`) e redesenha o campo arredondado ao passo (`Precision`, 0x96BB88). Nao ha releitura da ECU.
- **Caixa de marcar (`TCheckBox`)** `[OBS-COD]`: apertar Enter simula um clique do mouse (`CheckTpsBlocKeyPress` 0x4A8928 envia WM_LBUTTONDOWN/UP); o clique chama o handler `Check...Click`, que liga/desliga **um bit** do vetor FLAG_CONF1/2/3 (o programa regrava o vetor inteiro, ver quadros abaixo) e atualiza o que depende da caixa.
- **Lista (`TKeyComboBox`)** `[OBS-COD]`: Enter no combo chama o `...KeyPress` correspondente, que grava o indice escolhido (por codigo de dado `+0x330` ou por bit de FLAG_CONF).
- **Resposta da ECU a toda gravacao** `[OBS-FIO]`: a porta devolve o eco do quadro e a ECU responde `53 00 53` (LOG linhas 48568-48600 e 31284-31300). Em seguida o programa limpa os buffers e retoma a telemetria (`48 01 49`).
- **Conexao**: os valores 'atuais' vem de leituras `09`/`0A`/`29` feitas ao conectar (LOG de 12/08/2026); a tela do programa mostra o cache dessas leituras.

## Mapa de todos os controles (ordem do DFM)

| # | Componente | Tipo | Rotulo (por / eng / ita) | Unidade | Vetor (SC) | Bit | Visivel no DFM |
|---|---|---|---|---|---|---|---|
| 1 | `LabelTipoPressioneMisurata` | rotulo | - / - / Lettura della pressione | - | - | - | sim |
| 2 | `LabelTipoMisuratore` | rotulo | - / - / Sensore di pressione gas | - | - | - | False |
| 3 | `LabelTipoMisuratorePressione` | rotulo | - / - / Misuratore di pressione disponibile | - | - | - | False |
| 4 | `LabelSogliaPress` | rotulo | Limiar de fluxo subsônico / Subsonic flow threshold / Soglia di flusso subsonico | - | - | - | False |
| 5 | `LabelTempAcqua` | rotulo | T. água (°C) / T.water (°C) / T. acqua (°C) | - | - | - | sim |
| 6 | `LabelSogliaMap` | rotulo | Limiar MAP (V) / MAPthreshold (V) / Soglia MAP (V) | - | - | - | sim |
| 7 | `LabelPressRid` | rotulo | P red.(bar) / P red.(bar) / P rid. (bar) | - | - | - | sim |
| 8 | `ComboPressioneMisurata` | combo | - / - / Lettura della pressione | - | - | FLAG_CONF1[0]&0x2000 | sim |
| 9 | `ComboTipoMisuratore` | combo | - / - / Sensore di pressione gas | - | - | - | False |
| 10 | `ComboMisuratorePressione` | combo | - / - / Misuratore di pressione disponibile | - | - | FLAG_CONF1[0]&0x0010 | False |
| 11 | `GroupMisuratorePressione` | grupo | - / - / Misuratore pressione gas | - | - | - | False |
| 12 | `LabelOffsetDiff` | rotulo | - / - / Offset | - | - | - | sim |
| 13 | `LabelPendenzaDiff` | rotulo | - / - / Pendenza | - | - | - | sim |
| 14 | `LabelBarDiff` | rotulo | - / - / mbar | - | - | - | sim |
| 15 | `LabelBarVDiff` | rotulo | - / - / mbar/V | - | - | - | sim |
| 16 | `EditPendenzaDiff` | edit numerico | - / - / Pendenza | mbar/V | - | - | sim |
| 17 | `EditOffsetDiff` | edit numerico | - / - / Offset | mbar | - | - | sim |
| 18 | `GroupParametri` | grupo | - / - / Parametri per il MAP esterno | - | - | - | False |
| 19 | `LabelOffset` | rotulo | offset / Offset / Offset | - | - | - | sim |
| 20 | `LabelPendenza` | rotulo | pendência / Slope / Pendenza | - | - | - | sim |
| 21 | `LabelProcGuidata` | rotulo | - / - / "Dare solo il sotto chiave ed attendere l'apprendimento dei dati necessari..." | - | - | - | False |
| 22 | `LabelBar` | rotulo | mbar / mbar / mbar | - | - | - | sim |
| 23 | `LabelBarV` | rotulo | mbar/V / mbar/V / mbar/V | - | - | - | sim |
| 24 | `ButtonRicercaParametri` | botao | Inicie a procura guiada dos parâmetros do mapa externo / Start guided search for external map parameters / Avvia ricerca guidata parametri map esterno | - | - | - | sim |
| 25 | `EditPendenza` | edit numerico | pendência / Slope / Pendenza | mbar/V | - | - | sim |
| 26 | `EditOffset` | edit numerico | offset / Offset / Offset | mbar | - | - | sim |
| 27 | `CheckMapConfigurato` | caixa de marcar | Map configurado corretamente / Map correctly configured / Map configurato correttamente | - | - | FLAG_CONF1[1]&0x0080 | sim |
| 28 | `EditSogliaPress` | edit numerico | Limiar de fluxo subsônico / Subsonic flow threshold / Soglia di flusso subsonico | - | SOGLIA_FLUSSO_SUBSONICO (222), SOGLIA_FLUSSO_SUBSONICO_LR (106) | - | False |
| 29 | `EditTempAcqua` | edit numerico | T. água (°C) / T.water (°C) / T. acqua (°C) | - | - | - | sim |
| 30 | `EditSogliaMap` | edit numerico | Limiar MAP (V) / MAPthreshold (V) / Soglia MAP (V) | - | - | - | sim |
| 31 | `EditPressRid` | edit numerico | P red.(bar) / P red.(bar) / P rid. (bar) | - | - | - | sim |

## Controles interativos, um a um

### `ComboPressioneMisurata` — Lettura della pressione

Tipo: **combo** (`TKeyComboBox`), DFM linha 4249 `[OBS-COD]`. Pai: `TabPressione`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTipoPressioneMisurata` (associacao INFERIDO)): ita = "Lettura della pressione"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=504 Top=88 Largura=252 Altura=37; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Opcoes da lista `[OBS-COD]`: 0 = "assoluta" / diferencial; 1 = "differenziale" / não presente.
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTTIPOPRESSIONEMISURATA`: "MAP sensor".

**O que isso faz (linguagem simples)**

- Define se existe sensor MAP ligado (fio vermelho/amarelo no sensor original); sem MAP, a volta a gasolina usa a tabela de pressao nos trilhos. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.21).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 0, mascara 0x2000, caso do setter 13, papel "seletor (TKeyComboBox)", condicao "sempre"; origem: flags-do-original.json.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 0, mascara 0x0010, caso do setter 14, papel "None", condicao "sempre"; origem: handler ComboPressioneKeyPress.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 1, mascara 0x0080, caso do setter 31, papel "None", condicao "obj+0x80D!=0"; origem: handler ComboPressioneKeyPress.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 0 = 0x2486 -> bit 0x2000: **LIGADO** `[OBS-FIO]` (LOG linha 1102844).
- FLAG_CONF1 palavra 0 = 0x2486 -> bit 0x0010: **desligado** `[OBS-FIO]` (LOG linha 1102844).
- FLAG_CONF1 palavra 1 = 0x1051 -> bit 0x0080: **desligado** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `ComboPressioneKeyPress` (0x4AE420) `[OBS-COD]`
  - SetFlag1 caso 13 -> FLAG_CONF1 palavra 0 mascara 0x2000 em `0x4AE4A6`
  - SetFlag1 caso 14 -> FLAG_CONF1 palavra 0 mascara 0x0010 em `0x4AE4CA`
  - GetFlag1 caso 14 -> FLAG_CONF1 palavra 0 mascara 0x0010 em `0x4AE4E6`
  - muda Caption de `LabelTipoPressioneMisurata` em `0x4AE529`
  - SetFlag1 caso 31 -> FLAG_CONF1 palavra 1 mascara 0x0080 em `0x4AE5EE`
  - chama o metodo do TStreamDati `0x4310E8` em `0x4AE5F7` (le: cache de TIPO_ACCENS; escreve: -)
  - chama o metodo do TStreamDati `0x4310F8` em `0x4AE60A` (le: TIPO_ACCENS, cache de TIPO_ACCENS; escreve: cache de TIPO_ACCENS)
  - muda Caption de `LabelTipoPressioneMisurata` em `0x4AE64A`
  - GetFlag1 caso 14 -> FLAG_CONF1 palavra 0 mascara 0x0010 em `0x4AE70D`
  - GetFlag1 caso 14 -> FLAG_CONF1 palavra 0 mascara 0x0010 em `0x4AE72D`
  - GetFlag1 caso 13 -> FLAG_CONF1 palavra 0 mascara 0x2000 em `0x4AE73F`
  - GetFlag1 caso 13 -> FLAG_CONF1 palavra 0 mascara 0x2000 em `0x4AE75A`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 0 mascara 0x2000): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - desligar bit (FLAG_CONF1 palavra 0 mascara 0x2000): 2486 1051 -> 0486 1051, quadro `35 03 00 86 04 51 10 23`.
  - ligar bit (FLAG_CONF1 palavra 0 mascara 0x0010): 2486 1051 -> 2496 1051, quadro `35 03 00 96 24 51 10 53`.
  - desligar bit (FLAG_CONF1 palavra 0 mascara 0x0010): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - ligar bit (FLAG_CONF1 palavra 1 mascara 0x0080): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - desligar bit (FLAG_CONF1 palavra 1 mascara 0x0080): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Cada opcao da lista chama o metodo `0x4310E8` `[INFERIDO]` (convencao assumida):
  - opcao 0 "assoluta" -> quadro(s) `(nenhum)`.
  - opcao 1 "differenziale" -> quadro(s) `(nenhum)`.
- Cada opcao da lista chama o metodo `0x4310F8` `[INFERIDO]` (convencao assumida):
  - opcao 0 "assoluta" -> quadro(s) `12 14 00 C1 E7`.
  - opcao 1 "differenziale" -> quadro(s) `12 14 00 41 67`.
  - Estrutura: igual a acima. Fonte: metodo 0x4310E8 executado no Unicorn com edx = indice da opcao (convencao assumida; o handler ComboPressioneKeyPress o chama em 0x4AE5F7)
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetFlag1(caso 13)` em `0x4AE76B` (ComboPressioneKeyPress) — SE ecx == 0xd E sub_754CCC == 0
- `Checked/ItemIndex := calculado por GetFlag1(caso 13)` em `0x4AEE3D` (TabPressioneShow) — incondicional

### `ComboTipoMisuratore` — Sensore di pressione gas

Tipo: **combo** (`TKeyComboBox`), DFM linha 4265 `[OBS-COD]`. Pai: `TabPressione`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTipoMisuratore` (associacao INFERIDO)): ita = "Sensore di pressione gas"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=504 Top=10 Largura=252 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Opcoes da lista `[OBS-COD]`: 0 = "AEB025"; 1 = "BOSCH"; 2 = "SENSATA"; 3 = "LRE165".
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTTIPOMISURATORE`: "The function allows the type of pressure/temperature sensor installed to be set".

**O que isso faz (linguagem simples)**

- Tipo do sensor de pressao do gas instalado (Bosch/Sensata, LRE 165 Matrix, AEB 025...), conforme a versao do sistema. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.20).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 0, mascara 0x0010, caso do setter 14, papel "None", condicao "sempre"; origem: handler ComboTipoMisuratoreKeyPress.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 0, mascara 0x2000, caso do setter 13, papel "None", condicao "sempre"; origem: handler ComboTipoMisuratoreKeyPress.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 1, mascara 0x0080, caso do setter 31, papel "None", condicao "obj+0x80D!=0"; origem: handler ComboTipoMisuratoreKeyPress.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 0 = 0x2486 -> bit 0x0010: **desligado** `[OBS-FIO]` (LOG linha 1102844).
- FLAG_CONF1 palavra 0 = 0x2486 -> bit 0x2000: **LIGADO** `[OBS-FIO]` (LOG linha 1102844).
- FLAG_CONF1 palavra 1 = 0x1051 -> bit 0x0080: **desligado** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `ComboTipoMisuratoreKeyPress` (0x4BEC34) `[OBS-COD]`
  - muda Text de `ComboTipoMisuratore` em `0x4BECA9`
  - chama o metodo do TStreamDati `0x43048C` em `0x4BECEB` (le: MAP_ESTERNO; escreve: -)
  - chama o metodo do TStreamDati `0x43048C` em `0x4BECFF` (le: MAP_ESTERNO; escreve: -)
  - SetFlag1 caso 14 -> FLAG_CONF1 palavra 0 mascara 0x0010 em `0x4BED12`
  - SetFlag1 caso 13 -> FLAG_CONF1 palavra 0 mascara 0x2000 em `0x4BED22`
  - SetFlag1 caso 31 -> FLAG_CONF1 palavra 1 mascara 0x0080 em `0x4BED32`
  - chama o metodo do TStreamDati `0x430878` em `0x4BED3E` (le: TIPO_SENSORE_TEMPERATURA; escreve: -)
  - muda Text de `ComboTipoMisuratore` em `0x4BED7E`
  - chama o metodo do TStreamDati `0x43048C` em `0x4BEDC0` (le: MAP_ESTERNO; escreve: -)
  - chama o metodo do TStreamDati `0x43048C` em `0x4BEDD4` (le: MAP_ESTERNO; escreve: -)
  - SetFlag1 caso 14 -> FLAG_CONF1 palavra 0 mascara 0x0010 em `0x4BEDE7`
  - SetFlag1 caso 13 -> FLAG_CONF1 palavra 0 mascara 0x2000 em `0x4BEDF7`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 0 mascara 0x0010): 2486 1051 -> 2496 1051, quadro `35 03 00 96 24 51 10 53`.
  - desligar bit (FLAG_CONF1 palavra 0 mascara 0x0010): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - ligar bit (FLAG_CONF1 palavra 0 mascara 0x2000): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - desligar bit (FLAG_CONF1 palavra 0 mascara 0x2000): 2486 1051 -> 0486 1051, quadro `35 03 00 86 04 51 10 23`.
  - ligar bit (FLAG_CONF1 palavra 1 mascara 0x0080): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - desligar bit (FLAG_CONF1 palavra 1 mascara 0x0080): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Cada opcao da lista chama o metodo `0x43048C` `[INFERIDO]` (convencao assumida):
  - opcao 0 "AEB025" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory write (UC_ERR_WRITE_UNMAPPED) em eip=0x42b22a.
  - opcao 1 "BOSCH" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory write (UC_ERR_WRITE_UNMAPPED) em eip=0x42b22a.
  - opcao 2 "SENSATA" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory write (UC_ERR_WRITE_UNMAPPED) em eip=0x42b22a.
  - opcao 3 "LRE165" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory write (UC_ERR_WRITE_UNMAPPED) em eip=0x42b22a.
- Cada opcao da lista chama o metodo `0x430878` `[INFERIDO]` (convencao assumida):
  - opcao 0 "AEB025" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x430887.
  - opcao 1 "BOSCH" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x430887.
  - opcao 2 "SENSATA" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x430887.
  - opcao 3 "LRE165" -> quadro(s) `(nenhum)` ERRO UcError: Invalid memory read (UC_ERR_READ_UNMAPPED) em eip=0x430887.
  - Estrutura: igual a acima. Fonte: metodo 0x43048C executado no Unicorn com edx = indice da opcao (convencao assumida; o handler ComboTipoMisuratoreKeyPress o chama em 0x4BECEB)
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Text := calculado` em `0x4BECA9` (ComboTipoMisuratoreKeyPress) — SE ecx == 0xd E sub_754CCC == 0
- `Text := calculado` em `0x4BED7E` (ComboTipoMisuratoreKeyPress) — SE ecx == 0xd E sub_754CCC == 0 E sub_A4EDD0 == 0
- `Text := calculado` em `0x4BEE53` (ComboTipoMisuratoreKeyPress) — SE ecx == 0xd E sub_754CCC == 0 E sub_A4EDD0 == 0 E sub_A4EDD0 == 0
- `Text := calculado` em `0x4BEF28` (ComboTipoMisuratoreKeyPress) — SE ecx == 0xd E sub_754CCC == 0 E sub_A4EDD0 == 0 E sub_A4EDD0 == 0 E sub_A4EDD0 == 0
- `Checked/ItemIndex := calculado` em `0x4AE87A` (TabPressioneShow) — SE GetCombo(caso 1) == 0 E cmp eax, -0x6c ==  E sub_430470 == 0x21e
- `Checked/ItemIndex := calculado` em `0x4AE8BC` (TabPressioneShow) — SE GetCombo(caso 1) == 0 E sub_430470 != 0x21e E sub_430470 == 0x23 E sub_430470 == 0x33a
- `Checked/ItemIndex := calculado` em `0x4AE8D2` (TabPressioneShow) — SE GetCombo(caso 1) == 0 E sub_430470 != 0x21e E sub_430470 != 0x33a
- `Checked/ItemIndex := calculado` em `0x4AE9D1` (TabPressioneShow) — SE sub_430470 != 0x21e E sub_430470 != 0x33a E GetCombo(caso 1) != 0 E sub_430470 == 0x23 E sub_430470 == 0x33a
- `Checked/ItemIndex := calculado` em `0x4AEA13` (TabPressioneShow) — SE sub_430470 != 0x21e E sub_430470 != 0x33a E GetCombo(caso 1) != 0 E sub_430470 != 0x33a E sub_430470 == 0x7d E sub_430470 == 0x2ee
- `Checked/ItemIndex := calculado` em `0x4AEA51` (TabPressioneShow) — SE sub_430470 != 0x21e E sub_430470 != 0x33a E GetCombo(caso 1) != 0 E sub_430470 != 0x33a E sub_430470 != 0x2ee E sub_430470 == 0 E sub_430470 == 0x3e8
- `Checked/ItemIndex := calculado` em `0x4AEA64` (TabPressioneShow) — SE sub_430470 != 0x21e E sub_430470 != 0x33a E GetCombo(caso 1) != 0 E sub_430470 != 0x33a E sub_430470 != 0x2ee E sub_430470 != 0x3e8

### `ComboMisuratorePressione` — Misuratore di pressione disponibile

Tipo: **combo** (`TKeyComboBox`), DFM linha 4287 `[OBS-COD]`. Pai: `TabPressione`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTipoMisuratorePressione` (associacao INFERIDO)): ita = "Misuratore di pressione disponibile"; eng = "-"; por = "-" `[OBS-COD]`.
- Posicao no DFM: Left=504 Top=49 Largura=252 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Opcoes da lista `[OBS-COD]`: 0 = "assoluto" / diferencial; 1 = "differenziale".

**O que isso faz (linguagem simples)**

- Escolhe qual medidor de pressao esta disponivel. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 0, mascara 0x0010, caso do setter 14, papel "seletor (TKeyComboBox)", condicao "sempre"; origem: flags-do-original.json.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 0, mascara 0x2000, caso do setter 13, papel "None", condicao "sempre"; origem: handler ComboPressioneKeyPress.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 1, mascara 0x0080, caso do setter 31, papel "None", condicao "obj+0x80D!=0"; origem: handler ComboPressioneKeyPress.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 0 = 0x2486 -> bit 0x0010: **desligado** `[OBS-FIO]` (LOG linha 1102844).
- FLAG_CONF1 palavra 0 = 0x2486 -> bit 0x2000: **LIGADO** `[OBS-FIO]` (LOG linha 1102844).
- FLAG_CONF1 palavra 1 = 0x1051 -> bit 0x0080: **desligado** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `ComboPressioneKeyPress` (0x4AE420) `[OBS-COD]`
  - SetFlag1 caso 13 -> FLAG_CONF1 palavra 0 mascara 0x2000 em `0x4AE4A6`
  - SetFlag1 caso 14 -> FLAG_CONF1 palavra 0 mascara 0x0010 em `0x4AE4CA`
  - GetFlag1 caso 14 -> FLAG_CONF1 palavra 0 mascara 0x0010 em `0x4AE4E6`
  - muda Caption de `LabelTipoPressioneMisurata` em `0x4AE529`
  - SetFlag1 caso 31 -> FLAG_CONF1 palavra 1 mascara 0x0080 em `0x4AE5EE`
  - chama o metodo do TStreamDati `0x4310E8` em `0x4AE5F7` (le: cache de TIPO_ACCENS; escreve: -)
  - chama o metodo do TStreamDati `0x4310F8` em `0x4AE60A` (le: TIPO_ACCENS, cache de TIPO_ACCENS; escreve: cache de TIPO_ACCENS)
  - muda Caption de `LabelTipoPressioneMisurata` em `0x4AE64A`
  - GetFlag1 caso 14 -> FLAG_CONF1 palavra 0 mascara 0x0010 em `0x4AE70D`
  - GetFlag1 caso 14 -> FLAG_CONF1 palavra 0 mascara 0x0010 em `0x4AE72D`
  - GetFlag1 caso 13 -> FLAG_CONF1 palavra 0 mascara 0x2000 em `0x4AE73F`
  - GetFlag1 caso 13 -> FLAG_CONF1 palavra 0 mascara 0x2000 em `0x4AE75A`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 0 mascara 0x0010): 2486 1051 -> 2496 1051, quadro `35 03 00 96 24 51 10 53`.
  - desligar bit (FLAG_CONF1 palavra 0 mascara 0x0010): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - ligar bit (FLAG_CONF1 palavra 0 mascara 0x2000): 2486 1051 -> 2486 1051, quadro `35 03 00 86 24 51 10 43`.
  - desligar bit (FLAG_CONF1 palavra 0 mascara 0x2000): 2486 1051 -> 0486 1051, quadro `35 03 00 86 04 51 10 23`.
  - ligar bit (FLAG_CONF1 palavra 1 mascara 0x0080): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - desligar bit (FLAG_CONF1 palavra 1 mascara 0x0080): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Cada opcao da lista chama o metodo `0x4310E8` `[INFERIDO]` (convencao assumida):
  - opcao 0 "assoluto" -> quadro(s) `(nenhum)`.
  - opcao 1 "differenziale" -> quadro(s) `(nenhum)`.
- Cada opcao da lista chama o metodo `0x4310F8` `[INFERIDO]` (convencao assumida):
  - opcao 0 "assoluto" -> quadro(s) `12 14 00 C1 E7`.
  - opcao 1 "differenziale" -> quadro(s) `12 14 00 41 67`.
  - Estrutura: igual a acima. Fonte: metodo 0x4310E8 executado no Unicorn com edx = indice da opcao (convencao assumida; o handler ComboPressioneKeyPress o chama em 0x4AE5F7)
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Checked/ItemIndex := calculado por GetFlag1(caso 14)` em `0x4AE71E` (ComboPressioneKeyPress) — SE ecx == 0xd E sub_754CCC == 0
- `Checked/ItemIndex := calculado por GetFlag1(caso 14)` em `0x4AEDF0` (TabPressioneShow) — incondicional

### `EditPendenzaDiff` — Pendenza

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 4352 `[OBS-COD]`. Pai: `GroupMisuratorePressione`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelPendenzaDiff` (associacao INFERIDO)): ita = "Pendenza"; eng = "-"; por = "-" `[OBS-COD]`.
- Unidade ao lado: **mbar/V** `[OBS-COD]` (rotulo `LabelBarVDiff`).
- Posicao no DFM: Left=562 Top=38 Largura=87 Altura=37; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 4000.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Inclinacao (ganho) da reta tensao-pressao do sensor de pressao diferencial. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditOffsetKeyPress` (0x4AF360) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x43048C` em `0x4AF380` (le: MAP_ESTERNO; escreve: -)
  - chama o metodo do TStreamDati `0x430470` em `0x4AF395` (le: -; escreve: -)

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditOffsetDiff` — Offset

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 4375 `[OBS-COD]`. Pai: `GroupMisuratorePressione`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelOffsetDiff` (associacao INFERIDO)): ita = "Offset"; eng = "-"; por = "-" `[OBS-COD]`.
- Unidade ao lado: **mbar** `[OBS-COD]` (rotulo `LabelBarDiff`).
- Posicao no DFM: Left=189 Top=38 Largura=87 Altura=37; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo -2000.0, maximo 2000.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Deslocamento (offset) da reta tensao-pressao do sensor de pressao diferencial. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditOffsetKeyPress` (0x4AF360) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x43048C` em `0x4AF380` (le: MAP_ESTERNO; escreve: -)
  - chama o metodo do TStreamDati `0x430470` em `0x4AF395` (le: -; escreve: -)

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `ButtonRicercaParametri` — Inicie a procura guiada dos parâmetros do mapa externo

Tipo: **botao** (`TButton`), DFM linha 4464 `[OBS-COD]`. Pai: `GroupParametri`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Avvia ricerca guidata parametri map esterno"; eng = "Start guided search for external map parameters"; por = "Inicie a procura guiada dos parâmetros do mapa externo" `[OBS-COD]`.
- Posicao no DFM: Left=20 Top=121 Largura=727 Altura=32; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Dica ao tocar (balao) `[INFERIDO]` chave `HINTBUTTONRICERCAPARAMETRI`: "The function allows to set the use of the MAP sensor (absolute pressure of the intake manifold). \ When activated, the start button of the self-learning procedure of original sensor characteristic automatically appears. \ Is important to have a safe key-on, because the first part of the self-learning procedure has to be made \ without RPM signal and the gas ECU doesn't have to switch off.".

**O que isso faz (linguagem simples)**

- Procedimento guiado que aprende o sensor MAP original do carro: chave ligada (1 bar), troca a 3000 rpm e leitura em marcha lenta. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.21).
- Manual SW 2.16.4 C, p.21 (linha 1019 de sw.txt): "D Start guided search for external MAP parameters. LROMEGAS The MAP sensor parameterisation procedure acquires the" `[OBS-COD]`.

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 1, mascara 0x0080, caso do setter 31, papel "None", condicao "obj+0x80D!=0"; origem: handler ButtonRicercaParametriClick.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 1 = 0x1051 -> bit 0x0080: **desligado** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `ButtonRicercaParametriClick` (0x4AF3A8) `[OBS-COD]`
  - muda Text de um controle nao identificado pela analise estatica em `0x4AF3E5`
  - GetIdx codigo 61 -> vetor PARAM_MAP_ESTERNO_LR (SC 140) em `0x4AF489`
  - chama o metodo do TStreamDati `0x42A8F4` em `0x4AF497` (le: -; escreve: -)
  - GetIdx codigo 61 -> vetor PARAM_MAP_ESTERNO_LR (SC 140) em `0x4AF4B0`
  - GetIdx codigo 61 -> vetor PARAM_MAP_ESTERNO_LR (SC 140) em `0x4AF4D9`
  - muda Caption de `ButtonRicercaParametri` em `0x4AF547`
  - muda Caption de `LabLambda` em `0x4AF5A5`
  - muda Caption de `LabApMot` em `0x4AF5B6`
  - muda Caption de `LabApMotBenz` em `0x4AF5C7`
  - muda Caption de `LabPressione` em `0x4AF5D8`
  - muda Caption de `LabPressColl` em `0x4AF5E9`
  - muda Caption de `LabPressDiff` em `0x4AF5FA`
- `OnMouseDown` -> `ControlMouseDown` (0x4C6020) `[OBS-COD]`: ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 1 mascara 0x0080): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - desligar bit (FLAG_CONF1 palavra 1 mascara 0x0080): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Caption := calculado` em `0x4AF547` (ButtonRicercaParametriClick) — SE sub_A4EDD0 == 0
- `Visible := verdadeiro` em `0x4AF095` (CheckMapConfiguratoClick, CheckMapConfiguratoClick via sub_4AF018) — SE [obj+0x1280] == 0
- `Visible := verdadeiro` em `0x4AF095` (ComboPressioneKeyPress, ComboPressioneKeyPress via sub_4AF018) — SE ecx == 0xd E sub_754CCC == 0
- `Visible := verdadeiro` em `0x4AF095` (TabPressioneShow, TabPressioneShow via sub_4AF018) — incondicional
- `Visible := verdadeiro` em `0x4AF095` (TabCambioShow, TabCambioShow via sub_4AF018; metodo de OUTRA aba/tela) — incondicional

### `EditPendenza` — pendência

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 4482 `[OBS-COD]`. Pai: `GroupParametri`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelPendenza` (associacao INFERIDO)): ita = "Pendenza"; eng = "Slope"; por = "pendência" `[OBS-COD]`.
- Unidade ao lado: **mbar/V** `[OBS-COD]` (rotulo `LabelBarV`).
- Posicao no DFM: Left=562 Top=38 Largura=87 Altura=37; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 2000.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Inclinacao (ganho) da reta tensao-pressao do sensor MAP externo. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditOffsetKeyPress` (0x4AF360) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x43048C` em `0x4AF380` (le: MAP_ESTERNO; escreve: -)
  - chama o metodo do TStreamDati `0x430470` em `0x4AF395` (le: -; escreve: -)

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `EditOffset` — offset

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 4505 `[OBS-COD]`. Pai: `GroupParametri`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelOffset` (associacao INFERIDO)): ita = "Offset"; eng = "Offset"; por = "offset" `[OBS-COD]`.
- Unidade ao lado: **mbar** `[OBS-COD]` (rotulo `LabelBar`).
- Posicao no DFM: Left=189 Top=38 Largura=87 Altura=37; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo -2000.0, maximo 2000.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Deslocamento (offset) da reta tensao-pressao do sensor MAP externo. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditOffsetKeyPress` (0x4AF360) `[OBS-COD]`
  - chama o metodo do TStreamDati `0x43048C` em `0x4AF380` (le: MAP_ESTERNO; escreve: -)
  - chama o metodo do TStreamDati `0x430470` em `0x4AF395` (le: -; escreve: -)

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

### `CheckMapConfigurato` — Map configurado corretamente

Tipo: **caixa de marcar** (`TCheckBox`), DFM linha 4529 `[OBS-COD]`. Pai: `GroupParametri`.

**O que a pessoa vê**

- Texto (do proprio controle): ita = "Map configurato correttamente"; eng = "Map correctly configured"; por = "Map configurado corretamente" `[OBS-COD]`.
- Posicao no DFM: Left=23 Top=81 Largura=735 Altura=37; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Indica que o sensor MAP externo ja foi configurado corretamente. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.
- Bit de configuracao `[OBS-COD]`: **FLAG_CONF1** (SerialCode 3), palavra 1, mascara 0x0080, caso do setter 31, papel "caixa (Checked)", condicao "obj+0x80D!=0"; origem: flags-do-original.json.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- FLAG_CONF1 palavra 1 = 0x1051 -> bit 0x0080: **desligado** `[OBS-FIO]` (LOG linha 1102844).

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnClick` -> `CheckMapConfiguratoClick` (0x4BCB40) `[OBS-COD]`
  - SetFlag1 caso 31 -> FLAG_CONF1 palavra 1 mascara 0x0080 em `0x4BCB68`
- `OnKeyPress` -> `CheckTpsBlocKeyPress` (0x4A8928) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):
  - ligar bit (FLAG_CONF1 palavra 1 mascara 0x0080): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - desligar bit (FLAG_CONF1 palavra 1 mascara 0x0080): 2486 1051 -> 2486 1051, quadro **nenhum (o bit ja estava nesse estado: o programa nao regrava)**.
  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := verdadeiro` em `0x4AF356` (CheckMapConfiguratoClick, CheckMapConfiguratoClick via sub_4AF018) — SE [obj+0x1280] == 0
- `Visible := verdadeiro` em `0x4AF356` (ComboPressioneKeyPress, ComboPressioneKeyPress via sub_4AF018) — SE ecx == 0xd E sub_754CCC == 0
- `Checked/ItemIndex := calculado por GetFlag1(caso 31)` em `0x4AEE62` (TabPressioneShow) — incondicional
- `Visible := verdadeiro` em `0x4AF356` (TabPressioneShow, TabPressioneShow via sub_4AF018) — incondicional
- `Visible := verdadeiro` em `0x4AF356` (TabCambioShow, TabCambioShow via sub_4AF018; metodo de OUTRA aba/tela) — incondicional

### `EditSogliaPress` — Limiar de fluxo subsônico

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 4541 `[OBS-COD]`. Pai: `TabPressione`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelSogliaPress` (associacao INFERIDO)): ita = "Soglia di flusso subsonico"; eng = "Subsonic flow threshold"; por = "Limiar de fluxo subsônico" `[OBS-COD]`.
- Posicao no DFM: Left=460 Top=233 Largura=72 Altura=37; visivel de projeto: False; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0, maximo 1.0, passo (Precision) 0.01. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Limiar de fluxo subsonico (condicao de escoamento do gas no injetor). [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Codigo de dado do formulario `[OBS-COD]`: {"LR ([SD+0x80D]!=0)": 42, "demais ([SD+0x80D]==0)": 110} (campo +0x2EC (DataCode do TNumericEdit)).
  - Familia LR: vetor **SOGLIA_FLUSSO_SUBSONICO_LR** (SerialCode 106, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x436A5C): `valor_bruto = trunc((v * 100))` com `v` = valor digitado.
    - ECU -> tela (getter 0x4351CA): `valor_na_tela = (SD[0x24E8] * 0.01)`.
  - Familia demais: vetor **SOGLIA_FLUSSO_SUBSONICO** (SerialCode 222, TAebNumber, escalar) `[OBS-COD]`.
    - tela -> ECU (setter 0x437266): `valor_bruto = trunc((v * 100))` com `v` = valor digitado.
    - ECU -> tela (getter 0x435BAE): `valor_na_tela = (SD[0x24E8] * 0.01)`.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- SOGLIA_FLUSSO_SUBSONICO (SC 222): bruto 54; na tela: **0.54** `[OBS-FIO, tela DERIVADO]` (LOG linha 1110360).

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
  - valor atual da ECU do dono: tela = 0.54 -> quadro `12 DE 00 36 26`.
  - maximo da faixa do DFM: tela = 1.0 -> quadro `12 DE 00 64 54`.
  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.
- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).
- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := verdadeiro` em `0x4AF1F9` (CheckMapConfiguratoClick, CheckMapConfiguratoClick via sub_4AF018) — SE [obj+0x1280] == 0
- `Visible := verdadeiro` em `0x4AF1F9` (ComboPressioneKeyPress, ComboPressioneKeyPress via sub_4AF018) — SE ecx == 0xd E sub_754CCC == 0
- `Visible := verdadeiro` em `0x4AF1F9` (TabPressioneShow, TabPressioneShow via sub_4AF018) — incondicional
- `Visible := verdadeiro` em `0x4AF1F9` (TabCambioShow, TabCambioShow via sub_4AF018; metodo de OUTRA aba/tela) — incondicional

### `EditTempAcqua` — T. água (°C)

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 4557 `[OBS-COD]`. Pai: `TabPressione`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelTempAcqua` (associacao INFERIDO)): ita = "T. acqua (°C)"; eng = "T.water (°C)"; por = "T. água (°C)" `[OBS-COD]`.
- Posicao no DFM: Left=202 Top=476 Largura=65 Altura=37; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 40.0, maximo 100.0, passo (Precision) 1.0. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Temperatura da agua usada na verificacao do MAP. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditTempAcquaKeyPress` (0x4BCB78) `[OBS-COD]`
  - SetIdx codigo 61 -> vetor PARAM_MAP_ESTERNO_LR (SC 140) em `0x4BCBD3`
  - GetIdx codigo 61 -> vetor PARAM_MAP_ESTERNO_LR (SC 140) em `0x4BCBF9`
  - SetIdx codigo 61 -> vetor PARAM_MAP_ESTERNO_LR (SC 140) em `0x4BCC6E`
  - GetIdx codigo 61 -> vetor PARAM_MAP_ESTERNO_LR (SC 140) em `0x4BCC93`
  - SetIdx codigo 61 -> vetor PARAM_MAP_ESTERNO_LR (SC 140) em `0x4BCD01`
  - GetIdx codigo 61 -> vetor PARAM_MAP_ESTERNO_LR (SC 140) em `0x4BCD27`
- `OnEnter` -> `EditGiriKFiltroEnter` (0x4BE90C) `[OBS-COD]`
- `OnExit` -> `EditGiriKFiltroExit` (0x4BEABC) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4AF2AF` (CheckMapConfiguratoClick, CheckMapConfiguratoClick via sub_4AF018) — SE [obj+0x1280] == 0
- `Visible := calculado` em `0x4AF2AF` (ComboPressioneKeyPress, ComboPressioneKeyPress via sub_4AF018) — SE ecx == 0xd E sub_754CCC == 0
- `Visible := calculado` em `0x4AF2AF` (TabPressioneShow, TabPressioneShow via sub_4AF018) — incondicional
- `Visible := calculado` em `0x4AF2AF` (TabCambioShow, TabCambioShow via sub_4AF018; metodo de OUTRA aba/tela) — incondicional

### `EditSogliaMap` — Limiar MAP (V)

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 4580 `[OBS-COD]`. Pai: `TabPressione`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelSogliaMap` (associacao INFERIDO)): ita = "Soglia MAP (V)"; eng = "MAPthreshold (V)"; por = "Limiar MAP (V)" `[OBS-COD]`.
- Posicao no DFM: Left=489 Top=476 Largura=65 Altura=37; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0.01, maximo 5.0, passo (Precision) 0.01. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Limiar de tensao do MAP. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditTempAcquaKeyPress` (0x4BCB78) `[OBS-COD]`
  - SetIdx codigo 61 -> vetor PARAM_MAP_ESTERNO_LR (SC 140) em `0x4BCBD3`
  - GetIdx codigo 61 -> vetor PARAM_MAP_ESTERNO_LR (SC 140) em `0x4BCBF9`
  - SetIdx codigo 61 -> vetor PARAM_MAP_ESTERNO_LR (SC 140) em `0x4BCC6E`
  - GetIdx codigo 61 -> vetor PARAM_MAP_ESTERNO_LR (SC 140) em `0x4BCC93`
  - SetIdx codigo 61 -> vetor PARAM_MAP_ESTERNO_LR (SC 140) em `0x4BCD01`
  - GetIdx codigo 61 -> vetor PARAM_MAP_ESTERNO_LR (SC 140) em `0x4BCD27`
- `OnEnter` -> `EditGiriKFiltroEnter` (0x4BE90C) `[OBS-COD]`
- `OnExit` -> `EditGiriKFiltroExit` (0x4BEABC) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4AF2D7` (CheckMapConfiguratoClick, CheckMapConfiguratoClick via sub_4AF018) — SE [obj+0x1280] == 0
- `Visible := calculado` em `0x4AF2D7` (ComboPressioneKeyPress, ComboPressioneKeyPress via sub_4AF018) — SE ecx == 0xd E sub_754CCC == 0
- `Visible := calculado` em `0x4AF2D7` (TabPressioneShow, TabPressioneShow via sub_4AF018) — incondicional
- `Visible := calculado` em `0x4AF2D7` (TabCambioShow, TabCambioShow via sub_4AF018; metodo de OUTRA aba/tela) — incondicional

### `EditPressRid` — P red.(bar)

Tipo: **edit numerico** (`TNumericEdit`), DFM linha 4604 `[OBS-COD]`. Pai: `TabPressione`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `LabelPressRid` (associacao INFERIDO)): ita = "P rid. (bar)"; eng = "P red.(bar)"; por = "P red.(bar)" `[OBS-COD]`.
- Posicao no DFM: Left=723 Top=476 Largura=65 Altura=37; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").
- Faixa permitida `[OBS-COD]`: minimo 0.01, maximo 5.0, passo (Precision) 0.01. DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 (TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)

**O que isso faz (linguagem simples)**

- Pressao do redutor (regulador) em bar. [so pelo rotulo; sem fonte alem dele] `[INFERIDO]` (fonte: sem fonte (so o rotulo do componente)).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `EditTempAcquaKeyPress` (0x4BCB78) `[OBS-COD]`
  - SetIdx codigo 61 -> vetor PARAM_MAP_ESTERNO_LR (SC 140) em `0x4BCBD3`
  - GetIdx codigo 61 -> vetor PARAM_MAP_ESTERNO_LR (SC 140) em `0x4BCBF9`
  - SetIdx codigo 61 -> vetor PARAM_MAP_ESTERNO_LR (SC 140) em `0x4BCC6E`
  - GetIdx codigo 61 -> vetor PARAM_MAP_ESTERNO_LR (SC 140) em `0x4BCC93`
  - SetIdx codigo 61 -> vetor PARAM_MAP_ESTERNO_LR (SC 140) em `0x4BCD01`
  - GetIdx codigo 61 -> vetor PARAM_MAP_ESTERNO_LR (SC 140) em `0x4BCD27`
- `OnEnter` -> `EditGiriKFiltroEnter` (0x4BE90C) `[OBS-COD]`
- `OnExit` -> `EditGiriKFiltroExit` (0x4BEABC) `[OBS-COD]`

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- `Visible := calculado` em `0x4AF2FF` (CheckMapConfiguratoClick, CheckMapConfiguratoClick via sub_4AF018) — SE [obj+0x1280] == 0
- `Visible := calculado` em `0x4AF2FF` (ComboPressioneKeyPress, ComboPressioneKeyPress via sub_4AF018) — SE ecx == 0xd E sub_754CCC == 0
- `Visible := calculado` em `0x4AF2FF` (TabPressioneShow, TabPressioneShow via sub_4AF018) — incondicional
- `Visible := calculado` em `0x4AF2FF` (TabCambioShow, TabCambioShow via sub_4AF018; metodo de OUTRA aba/tela) — incondicional

## Rotulos com regra de mostrar/esconder ou texto dinamico

### `LabelTipoPressioneMisurata` — "Lettura della pressione"

Texto: ita "Lettura della pressione" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=sim.
- `Caption := calculado` em `0x4AE529` (ComboPressioneKeyPress) — SE ecx == 0xd E sub_754CCC == 0 E GetFlag1(caso 14) == 0
- `Caption := calculado` em `0x4AE64A` (ComboPressioneKeyPress) — SE ecx == 0xd E sub_754CCC == 0 E sub_4310E8 == 0 E GetFlag1(caso 14) != 0
- `Visible := verdadeiro` em `0x4AEB83` (TabPressioneShow) — incondicional
- `Caption := calculado` em `0x4AEBF6` (TabPressioneShow) — SE GetFlag1(caso 14) == 0
- `Caption := calculado` em `0x4AECEB` (TabPressioneShow) — SE GetFlag1(caso 14) != 0

### `LabelTipoMisuratore` — "Sensore di pressione gas"

Texto: ita "Sensore di pressione gas" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado por GetCombo(caso 1)` em `0x4AEA85` (TabPressioneShow) — incondicional

### `LabelTipoMisuratorePressione` — "Misuratore di pressione disponibile"

Texto: ita "Misuratore di pressione disponibile" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado por sub_48944C` em `0x4AEAC1` (TabPressioneShow) — incondicional

### `LabelSogliaPress` — "Limiar de fluxo subsônico"

Texto: ita "Soglia di flusso subsonico" / eng "Subsonic flow threshold" / por "Limiar de fluxo subsônico" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Visible := calculado` em `0x4AF20D` (CheckMapConfiguratoClick, CheckMapConfiguratoClick via sub_4AF018) — SE [obj+0x1280] == 0
- `Visible := calculado` em `0x4AF20D` (ComboPressioneKeyPress, ComboPressioneKeyPress via sub_4AF018) — SE ecx == 0xd E sub_754CCC == 0
- `Visible := calculado` em `0x4AF20D` (TabPressioneShow, TabPressioneShow via sub_4AF018) — incondicional
- `Visible := calculado` em `0x4AF20D` (TabCambioShow, TabCambioShow via sub_4AF018; metodo de OUTRA aba/tela) — incondicional

### `LabelTempAcqua` — "T. água (°C)"

Texto: ita "T. acqua (°C)" / eng "T.water (°C)" / por "T. água (°C)" `[OBS-COD]`. Estado de projeto: visivel=sim.
- `Visible := verdadeiro` em `0x4AF29B` (CheckMapConfiguratoClick, CheckMapConfiguratoClick via sub_4AF018) — SE [obj+0x1280] == 0
- `Visible := verdadeiro` em `0x4AF29B` (ComboPressioneKeyPress, ComboPressioneKeyPress via sub_4AF018) — SE ecx == 0xd E sub_754CCC == 0
- `Visible := verdadeiro` em `0x4AF29B` (TabPressioneShow, TabPressioneShow via sub_4AF018) — incondicional
- `Visible := verdadeiro` em `0x4AF29B` (TabCambioShow, TabCambioShow via sub_4AF018; metodo de OUTRA aba/tela) — incondicional

### `LabelSogliaMap` — "Limiar MAP (V)"

Texto: ita "Soglia MAP (V)" / eng "MAPthreshold (V)" / por "Limiar MAP (V)" `[OBS-COD]`. Estado de projeto: visivel=sim.
- `Visible := calculado` em `0x4AF2C3` (CheckMapConfiguratoClick, CheckMapConfiguratoClick via sub_4AF018) — SE [obj+0x1280] == 0
- `Visible := calculado` em `0x4AF2C3` (ComboPressioneKeyPress, ComboPressioneKeyPress via sub_4AF018) — SE ecx == 0xd E sub_754CCC == 0
- `Visible := calculado` em `0x4AF2C3` (TabPressioneShow, TabPressioneShow via sub_4AF018) — incondicional
- `Visible := calculado` em `0x4AF2C3` (TabCambioShow, TabCambioShow via sub_4AF018; metodo de OUTRA aba/tela) — incondicional

### `LabelPressRid` — "P red.(bar)"

Texto: ita "P rid. (bar)" / eng "P red.(bar)" / por "P red.(bar)" `[OBS-COD]`. Estado de projeto: visivel=sim.
- `Visible := calculado` em `0x4AF2EB` (CheckMapConfiguratoClick, CheckMapConfiguratoClick via sub_4AF018) — SE [obj+0x1280] == 0
- `Visible := calculado` em `0x4AF2EB` (ComboPressioneKeyPress, ComboPressioneKeyPress via sub_4AF018) — SE ecx == 0xd E sub_754CCC == 0
- `Visible := calculado` em `0x4AF2EB` (TabPressioneShow, TabPressioneShow via sub_4AF018) — incondicional
- `Visible := calculado` em `0x4AF2EB` (TabCambioShow, TabCambioShow via sub_4AF018; metodo de OUTRA aba/tela) — incondicional

### `LabelProcGuidata` — ""Dare solo il sotto chiave ed attendere l'apprendimento dei dati necessari...""

Texto: ita ""Dare solo il sotto chiave ed attendere l'apprendimento dei dati necessari..."" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Caption := calculado` em `0x4AF827` (ButtonRicercaParametriClick) — SE sub_A4EDD0 == 0
- `Visible := calculado por sub_A4EDD0` em `0x4AF845` (ButtonRicercaParametriClick) — SE sub_A4EDD0 == 0
- `Caption := calculado` em `0x4AFFDA` (ButtonRicercaParametriClick) — SE sub_A4EDD0 == 0 E sub_925894 != 0 E sub_925894 != 0 E cmp word ptr [ebp - 0x10a], 0 ==  E cmp word ptr [ebp - 0x10a], 0 == 
- `Visible := calculado por sub_A4EDD0` em `0x4AFFF8` (ButtonRicercaParametriClick) — SE sub_A4EDD0 == 0 E sub_925894 != 0 E sub_925894 != 0 E cmp word ptr [ebp - 0x10a], 0 ==  E cmp word ptr [ebp - 0x10a], 0 == 
- `Caption := calculado` em `0x4B0359` (ButtonRicercaParametriClick) — SE sub_A4EDD0 == 0 E sub_925894 != 0 E sub_925894 != 0 E cmp word ptr [ebp - 0x10a], 0 ==  E cmp word ptr [ebp - 0x10a], 0 ==  E sub_925894 != 0 E cmp ecx, dword ptr [ebp - 0x1d8] <  E test byte ptr [ebp - 0xd4], 0x10 != 
- `Visible := calculado por sub_A4EDD0` em `0x4B0377` (ButtonRicercaParametriClick) — SE sub_A4EDD0 == 0 E sub_925894 != 0 E sub_925894 != 0 E cmp word ptr [ebp - 0x10a], 0 ==  E cmp word ptr [ebp - 0x10a], 0 ==  E sub_925894 != 0 E cmp ecx, dword ptr [ebp - 0x1d8] <  E test byte ptr [ebp - 0xd4], 0x10 != 
- `Visible := falso` em `0x4B0DFC` (ButtonRicercaParametriClick) — SE sub_A4EDD0 == 0

## Lacunas desta aba

- Edits sem codigo de dado na rotina 0x48A228 (o valor passa por metodo proprio do TStreamDati; formula nao decodificada aqui, `DESCONHECIDO`): `EditPendenzaDiff`, `EditOffsetDiff`, `EditPendenza`, `EditOffset`, `EditTempAcqua`, `EditSogliaMap`, `EditPressRid`.
- Combos cujo valor e gravado por metodo proprio (sem bit nem codigo generico): `ComboTipoMisuratore`. Os vetores tocados aparecem na lista de acessos do handler; o significado de cada opcao no fio e `DESCONHECIDO` quando nao ha formula.
- Visibilidade/habilitacao: as regras listadas sao as que o codigo escreve; a combinacao final depende de dados da ECU (modelo, familia LR, MASK_FUNCTION) que o LOG so mostra em parte. O dono usa familia nao-LR (INFERIDO: o original leu SC 192, nunca 193).
- A associacao rotulo<->campo e por nome/geometria (`INFERIDO`), e a chave da dica (HINT...) segue o nome do componente (`INFERIDO`).

