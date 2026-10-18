# TabCorrettori — Comp. combust.

Nome na tela: ita "Correttori" / eng "Fuel trim" / por "Comp. combust." `[OBS-COD]`. Aba 8 de 16 do grupo D3a. Rotina de abertura (OnShow): `TabCorrettoriShow` em `None` `[OBS-COD]`. TabVisible de projeto: False.

Legenda das marcas: `OBS-COD` = lido do DFM/codigo do ProgBase.exe (ou executado no emulador Unicorn); `OBS-FIO` = visto no Portmon da ECU do dono; `DERIVADO` = calculado a partir dessas observacoes; `INFERIDO` = deducao; `DESCONHECIDO` = sem prova.

**Resumo**: 12 controles no DFM (TGroupBox=2, TLabel=10); 0 interativos; 0 com codigo de dado e formula; 0 com caixa/bit; 0 com valor lido no LOG. Fonte do .exe sha256 `8a2d297c8c21...`.

## Como o ProgBase trata estes controles (valido para a aba inteira)

- **Campo numerico (`TNumericEdit`)** `[OBS-COD]`: ao entrar no campo, `EditGiriCambioEnter` (0x4A98D8) mostra no rotulo `LabelLimite` o texto `(minimo;maximo)` do proprio campo; ao sair, `EditGiriCambioExit` (0x4A9CC0) esconde esse rotulo. Nada e enviado a ECU nesses dois momentos. Ao apertar **Enter**, o `TNumericEdit` limita o numero a [minimo;maximo] (so se minimo!=0 ou maximo!=0; 0x96C170) e o handler generico `EditRifLambdaKeyPress` (0x4A6A00) chama `TStreamDati.Set*` com o *codigo de dado* do campo (campo `+0x2EC`, indice `+0x2F0`; amarrados em 0x48A228): o valor e convertido pela formula do caso, gravado no cache do programa e enviado como SetNumber (escalar) ou SetNumber+indice (vetor). Depois o handler le o valor de volta do cache (`Get*`) e redesenha o campo arredondado ao passo (`Precision`, 0x96BB88). Nao ha releitura da ECU.
- **Caixa de marcar (`TCheckBox`)** `[OBS-COD]`: apertar Enter simula um clique do mouse (`CheckTpsBlocKeyPress` 0x4A8928 envia WM_LBUTTONDOWN/UP); o clique chama o handler `Check...Click`, que liga/desliga **um bit** do vetor FLAG_CONF1/2/3 (o programa regrava o vetor inteiro, ver quadros abaixo) e atualiza o que depende da caixa.
- **Lista (`TKeyComboBox`)** `[OBS-COD]`: Enter no combo chama o `...KeyPress` correspondente, que grava o indice escolhido (por codigo de dado `+0x330` ou por bit de FLAG_CONF).
- **Resposta da ECU a toda gravacao** `[OBS-FIO]`: a porta devolve o eco do quadro e a ECU responde `53 00 53` (LOG linhas 48568-48600 e 31284-31300). Em seguida o programa limpa os buffers e retoma a telemetria (`48 01 49`).
- **Conexao**: os valores 'atuais' vem de leituras `09`/`0A`/`29` feitas ao conectar (LOG de 12/08/2026); a tela do programa mostra o cache dessas leituras.

## Mapa de todos os controles (ordem do DFM)

| # | Componente | Tipo | Rotulo (por / eng / ita) | Unidade | Vetor (SC) | Bit | Visivel no DFM |
|---|---|---|---|---|---|---|---|
| 1 | `GroupBancata0` | grupo | - / - / Bancata 1 | - | - | - | False |
| 2 | `LabelSlow0` | rotulo | - / - / Lento | - | - | - | sim |
| 3 | `LabCorrSlow0` | rotulo | - / - / 0% | - | - | - | False |
| 4 | `LabelCorrettori0` | rotulo | - / - / Correttori | - | - | - | sim |
| 5 | `LabCorrFast0` | rotulo | - / - / 0% | - | - | - | False |
| 6 | `LabelFast0` | rotulo | - / - / Veloce | - | - | - | sim |
| 7 | `GroupBancata1` | grupo | - / - / Bancata 2 | - | - | - | False |
| 8 | `LabelCorrettori1` | rotulo | - / - / Correttori | - | - | - | sim |
| 9 | `LabelSlow1` | rotulo | - / - / Lento | - | - | - | sim |
| 10 | `LabCorrSlow1` | rotulo | - / - / 0% | - | - | - | False |
| 11 | `LabCorrFast1` | rotulo | - / - / 0% | - | - | - | False |
| 12 | `LabelFast1` | rotulo | - / - / Veloce | - | - | - | sim |

## Controles interativos, um a um

## Rotulos com regra de mostrar/esconder ou texto dinamico

### `LabCorrSlow0` — "0%"

Texto: ita "0%" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Caption := calculado` em `0x4A4E1C` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration)
- `Visible := calculado por sub_A4EDD0` em `0x4A4E72` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration)
- `Text := calculado` em `0x4A4E95` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration)

### `LabCorrFast0` — "0%"

Texto: ita "0%" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Caption := calculado` em `0x4A4F72` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration)
- `Visible := calculado por sub_A4EDD0` em `0x4A4FC8` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration)
- `Text := calculado` em `0x4A4FEB` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration)

### `LabCorrSlow1` — "0%"

Texto: ita "0%" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Caption := calculado` em `0x4A527E` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration)
- `Visible := calculado por sub_A4EDD0` em `0x4A52D5` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration)
- `Text := calculado` em `0x4A52F8` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration)

### `LabCorrFast1` — "0%"

Texto: ita "0%" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Caption := calculado` em `0x4A53D5` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration)
- `Visible := calculado por sub_A4EDD0` em `0x4A542C` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration)
- `Text := calculado` em `0x4A544F` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(eax) == [form+0xd84](TabCalibration)

## Lacunas desta aba

- Visibilidade/habilitacao: as regras listadas sao as que o codigo escreve; a combinacao final depende de dados da ECU (modelo, familia LR, MASK_FUNCTION) que o LOG so mostra em parte. O dono usa familia nao-LR (INFERIDO: o original leu SC 192, nunca 193).
- A associacao rotulo<->campo e por nome/geometria (`INFERIDO`), e a chave da dica (HINT...) segue o nome do componente (`INFERIDO`).

