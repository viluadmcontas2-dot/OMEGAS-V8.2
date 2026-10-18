# TabKPressDiff — Pressione

Nome na tela: ita "Pressione" / eng "None" / por "None" `[OBS-COD]`. Aba 13 de 16 do grupo D3a. Rotina de abertura (OnShow): `TabKPressDiffShow` em `None` `[OBS-COD]`. TabVisible de projeto: False.

Legenda das marcas: `OBS-COD` = lido do DFM/codigo do ProgBase.exe (ou executado no emulador Unicorn); `OBS-FIO` = visto no Portmon da ECU do dono; `DERIVADO` = calculado a partir dessas observacoes; `INFERIDO` = deducao; `DESCONHECIDO` = sem prova.

**Resumo**: 4 controles no DFM (TDrawGrid=1, TLabel=3); 1 interativos; 0 com codigo de dado e formula; 0 com caixa/bit; 0 com valor lido no LOG. Fonte do .exe sha256 `8a2d297c8c21...`.

## Como o ProgBase trata estes controles (valido para a aba inteira)

- **Campo numerico (`TNumericEdit`)** `[OBS-COD]`: ao entrar no campo, `EditGiriCambioEnter` (0x4A98D8) mostra no rotulo `LabelLimite` o texto `(minimo;maximo)` do proprio campo; ao sair, `EditGiriCambioExit` (0x4A9CC0) esconde esse rotulo. Nada e enviado a ECU nesses dois momentos. Ao apertar **Enter**, o `TNumericEdit` limita o numero a [minimo;maximo] (so se minimo!=0 ou maximo!=0; 0x96C170) e o handler generico `EditRifLambdaKeyPress` (0x4A6A00) chama `TStreamDati.Set*` com o *codigo de dado* do campo (campo `+0x2EC`, indice `+0x2F0`; amarrados em 0x48A228): o valor e convertido pela formula do caso, gravado no cache do programa e enviado como SetNumber (escalar) ou SetNumber+indice (vetor). Depois o handler le o valor de volta do cache (`Get*`) e redesenha o campo arredondado ao passo (`Precision`, 0x96BB88). Nao ha releitura da ECU.
- **Caixa de marcar (`TCheckBox`)** `[OBS-COD]`: apertar Enter simula um clique do mouse (`CheckTpsBlocKeyPress` 0x4A8928 envia WM_LBUTTONDOWN/UP); o clique chama o handler `Check...Click`, que liga/desliga **um bit** do vetor FLAG_CONF1/2/3 (o programa regrava o vetor inteiro, ver quadros abaixo) e atualiza o que depende da caixa.
- **Lista (`TKeyComboBox`)** `[OBS-COD]`: Enter no combo chama o `...KeyPress` correspondente, que grava o indice escolhido (por codigo de dado `+0x330` ou por bit de FLAG_CONF).
- **Resposta da ECU a toda gravacao** `[OBS-FIO]`: a porta devolve o eco do quadro e a ECU responde `53 00 53` (LOG linhas 48568-48600 e 31284-31300). Em seguida o programa limpa os buffers e retoma a telemetria (`48 01 49`).
- **Conexao**: os valores 'atuais' vem de leituras `09`/`0A`/`29` feitas ao conectar (LOG de 12/08/2026); a tela do programa mostra o cache dessas leituras.

## Mapa de todos os controles (ordem do DFM)

| # | Componente | Tipo | Rotulo (por / eng / ita) | Unidade | Vetor (SC) | Bit | Visivel no DFM |
|---|---|---|---|---|---|---|---|
| 1 | `LabelCorrezioniPressione` | rotulo | Correções da pressão (bar) / Pressure corrections (bar) / Correzioni pressione (bar) | - | - | - | sim |
| 2 | `LabelCorrPressAbs` | rotulo | - / - / Correzione pressione assoluta | - | - | - | False |
| 3 | `LabCorrPressAbs` | rotulo | - / - / 0% | - | - | - | False |
| 4 | `DrawGridPressDiff` | grade | - / - / - | - | - | - | sim |

## Controles interativos, um a um

### `DrawGridPressDiff` — DrawGridPressDiff

Tipo: **grade** (`TDrawGrid`), DFM linha 5815 `[OBS-COD]`. Pai: `TabKPressDiff`.

**O que a pessoa vê**

- Texto (do rotulo vizinho `None` (associacao None)): ita = "-"; eng = "-"; por = "-" `[DESCONHECIDO]`.
- Posicao no DFM: Left=4 Top=40 Largura=724 Altura=68; visivel de projeto: sim; habilitado de projeto: sim `[OBS-COD]` (o programa pode mudar isso ao abrir a aba; ver "Regras").

**O que isso faz (linguagem simples)**

- Compensacao do tempo de injecao do gas pela pressao do gas. `[OBS-COD]` (fonte: manual SW 2.16.4 C p.24).

**Ligacao com a ECU e conversao**

- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.

**Valor atual na ECU do dono (LOG de 12/08/2026)**

- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.

**Ao tocar / usar, o que o programa faz (handlers)**

- `OnKeyPress` -> `DrawGridPressDiffKeyPress` (0x4B5B58) `[OBS-COD]`
- `OnDblClick` -> `DrawGridPressDiffDblClick` (0x4B5B68) `[OBS-COD]`
- `OnKeyDown` -> `DrawGridKKeyDown` (0x4B2514) `[OBS-COD]`
- `OnDrawCell` -> `DrawGridPressDiffDrawCell` (0x4B5880) `[OBS-COD]`: desenho das celulas da grade (exibicao); nao envia nada a ECU

**Comando enviado (bytes) e o que acontece entre um comando e outro**

- Grade: cada celula editada chama um setter indexado (SetIdx) do handler `KeyPress`/`DblClick`; quadros por celula: `14 SC lo hi idx valor ck` (SetNumber+indice). Quadros exatos por celula: **DESCONHECIDO** (nao executados aqui).

**Regras de habilitar/mostrar/esconder `[OBS-COD]`**

- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (visivel) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.

## Rotulos com regra de mostrar/esconder ou texto dinamico

### `LabCorrPressAbs` — "0%"

Texto: ita "0%" / eng "-" / por "-" `[OBS-COD]`. Estado de projeto: visivel=False.
- `Caption := calculado` em `0x4A48C8` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(ecx) == [form+0x46c](TabKCoefficienti) E remetente(edx) != [form+0x518](TabKTempRid) E remetente(eax) != [form+0x5e8](TabKTempMot) E remetente(edx) == [form+0x7dc](TabPressAss) E edx == 0x5e E (byte[obj+0x1838] & 0x10) != 0 E test ecx, ecx != 
- `Caption := calculado` em `0x4A4930` (TimerDatiTimer; metodo de OUTRA aba/tela) — SE remetente(ecx) == [form+0x46c](TabKCoefficienti) E remetente(edx) != [form+0x518](TabKTempRid) E remetente(eax) != [form+0x5e8](TabKTempMot) E remetente(edx) == [form+0x7dc](TabPressAss) E edx == 0x5e E test ecx, ecx == 

## Lacunas desta aba

- Grades (`TDrawGrid`): o conteudo e desenhado em `OnDrawCell`; o conteudo e os quadros por celula nao foram decodificados celula a celula: `DrawGridPressDiff`.
- Visibilidade/habilitacao: as regras listadas sao as que o codigo escreve; a combinacao final depende de dados da ECU (modelo, familia LR, MASK_FUNCTION) que o LOG so mostra em parte. O dono usa familia nao-LR (INFERIDO: o original leu SC 192, nunca 193).
- A associacao rotulo<->campo e por nome/geometria (`INFERIDO`), e a chave da dica (HINT...) segue o nome do componente (`INFERIDO`).

