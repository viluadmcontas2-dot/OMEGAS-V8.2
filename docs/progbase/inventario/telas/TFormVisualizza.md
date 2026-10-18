# TFormVisualizza (recurso `TFORMVISUALIZZA`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

**Papel:** tela de função (OBS-COD). Visualização em tempo real dos parâmetros do veículo e gravação de aquisição (DRT).

- Instância DFM: `FormVisualizza`; unidade: `@:Visualizza`; ancestral: `TForm`; tamanho de instância: 0x74C; componentes no DFM: 85.
- Título: "Visualizzazione parametri vettura" — eng: "Vehicle parameter display" / por: "Visualização dos parâmetros do veículo"
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `TFormMain.ActionVisualizzaExecute` (0x00501568)

## Controles com evento/ação (3)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| FormVisualizza | TFormVisualizza | Visualizzazione parametri vettura / Vehicle parameter display / Visualização dos parâmetros do veículo |  | OnClose→`FormClose` (0x0050DCEC) |
| ButtonCommutatore | TButton | &Commutatore / &Switch / &Comutador |  | OnClick→`ButtonCommutatoreClick` (0x0050DD14) |
| ButtonEsci | TButton | &Esci / &Exit / &Sair |  |  |

## Caixas de diálogo (componentes) (2)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) | Filtro |
|---|---|---|---|---|---|
| OpenDialogDrt | TOpenDialog |  |  |  | Grafico dati in tempo reale\|*.txt |
| SaveDialogDrt | TSaveDialog |  |  |  | Acquisizioni in tempo reale\|*.txt |

## Temporizadores (1)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) | Intervalo ms |
|---|---|---|---|---|---|
| Timer1 | TTimer |  |  | OnTimer→`Timer1Timer` (0x0050B408) | 75 |

## Handlers (6): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `ButtonCommutatoreClick` | 0x0050DD14 | 231 | INFERIDO | Botão "Comutador" (230 B): envia à ECU o comando que alterna gasolina/gás (usa Sleep e PostMessage). Escreve/comanda a ECU: PERIGOSO (muda o combustível em uso). INFERIDO pelo caption. |  | Sleep | PostMessageW |
| `FormClose` | 0x0050DCEC | 39 | INFERIDO | Ao fechar o formulário FormVisualizza «Visualização dos parâmetros do veículo»: corpo de 39 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `MenuCominciaDrtClick` (sem evento no DFM) | 0x0050D86C | 739 | OBS-COD | Menu "Começar DRT": inicia uma aquisição de dados e prepara arquivo XML (<acquisitionData dateTime=…>, formato de data ddmmyyyyhhnnss). OBS-COD (literais). |  | GetTickCount, DrawMenuBar |  |
| `MenuGraficoClick` (sem evento no DFM) | 0x0050DC08 | 187 | DERIVADO | Menu "Gráfico": abre TFormGraficoDrt (visualização gráfica de aquisição, abre arquivo via OpenDialogDrt). OBS-COD. |  |  | ACTIONESCI, ACTIONSTAMPA, BUTTONANNULLA, BUTTONOK, DeleteMenu, GetSystemMenu, GetSystemMetrics |
| `MenuTerminaDrtClick` (sem evento no DFM) | 0x0050DB50 | 184 | DERIVADO | Menu "Terminar DRT": encerra a aquisição e abre SaveDialogDrt para salvar o arquivo. OBS-COD (campo). |  | DrawMenuBar |  |
| `Timer1Timer` | 0x0050B408 | 7653 | DERIVADO | Atualização periódica dos parâmetros do veículo (7653 B): lê TStreamDati.TimerIO e atualiza lâmpadas (benzina/gas/cut-off/sotto chiave), lambda, temperaturas, TPS/MAP, tempos de injeção e tensão dos injetores; durante gravação DRT grava pares tempo/valor (literais "time", "value", "par001"…). OBS-COD. |  | GetTickCount |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `ButtonCommutatoreClick` (0x0050DD14):
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C9C (105 B): chama TAebVector::SetEcuData
  -   sub_43204C (129 B): chama TAebVector::SetEcuData
  -   sub_432464 (178 B): chama TAebVector::SetEcuData
  -   sub_43255C (178 B): chama TAebVector::SetEcuData
  -   sub_4334B4 (169 B): chama TAebVector::SetEcuData
  -   sub_435C6C (1903 B): chama TAebVector::GetEcuData
  -   sub_43734C (2164 B): chama TAebVector::SetEcuData
  -     sub_435BFC (2015 B): chama TAebVector::GetEcuData
  -     sub_4372B8 (2312 B): chama TAebNumber::SetEcuData,TAebVector::SetEcuData
  - (+22 subfunções sem fatos diretos ou fora da lista; telas.json)
- `MenuCominciaDrtClick` (0x0050D86C):
  - (+4 subfunções sem fatos diretos ou fora da lista; telas.json)
- `MenuGraficoClick` (0x0050DC08):
  - sub_4DF6FC (22084 B): textos ACTIONESCI,ACTIONSTAMPA,BUTTONANNULLA,BUTTONOK; APIs DeleteMenu,GetSystemMenu,GetSystemMetrics; chama TAboutBox.Create,TTranslatorExt::GetString
  - (+47 subfunções sem fatos diretos ou fora da lista; telas.json)
- `MenuTerminaDrtClick` (0x0050DB50):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
- `Timer1Timer` (0x0050B408):
  - (+19 subfunções sem fatos diretos ou fora da lista; telas.json)
