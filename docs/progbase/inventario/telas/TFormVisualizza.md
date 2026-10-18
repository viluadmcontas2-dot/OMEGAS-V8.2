# TFormVisualizza (recurso `TFORMVISUALIZZA`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

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
| `ButtonCommutatoreClick` | 0x0050DD14 | 231 | INFERIDO | chama APIs do Windows Sleep; toca ButtonCommutatore |  | Sleep | PostMessageW |
| `FormClose` | 0x0050DCEC | 39 | INFERIDO | sem fatos diretos extraíveis (39 bytes; chama 1 funções sem nome) |  |  |  |
| `MenuCominciaDrtClick` (sem evento no DFM) | 0x0050D86C | 739 | INFERIDO | chama APIs do Windows GetTickCount, DrawMenuBar |  | GetTickCount, DrawMenuBar |  |
| `MenuGraficoClick` (sem evento no DFM) | 0x0050DC08 | 187 | INFERIDO | toca OpenDialogDrt |  |  | ACTIONESCI, ACTIONSTAMPA, BUTTONANNULLA, BUTTONOK, DeleteMenu, GetSystemMenu, GetSystemMetrics |
| `MenuTerminaDrtClick` (sem evento no DFM) | 0x0050DB50 | 184 | INFERIDO | chama APIs do Windows DrawMenuBar; toca SaveDialogDrt |  | DrawMenuBar |  |
| `Timer1Timer` | 0x0050B408 | 7653 | INFERIDO | chama APIs do Windows GetTickCount; chama TStreamDati.TimerIOTimer; toca ImageBenzOff, ImageBenzOn, ImageCutoffOff, ImageCutoffOn, ImageGasOff, ImageGasOn, ImageSottoChiaveOff, ImageSottoChiaveOn |  | GetTickCount |  |

### Cadeia de subfunções sem nome alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `ButtonCommutatoreClick` (0x0050DD14):
  - sub_42CFB8 (839 B)
  - sub_42D300 (3089 B): APIs PostMessageW; chama TAebNumber::GetInt,TAebNumber::SetInt,TAebVector::SetEcuData; toca TabEsci
  -   sub_42A788 (358 B)
  -   sub_42A8F4 (235 B)
  -   sub_42B224 (15 B)
  -   sub_430A0C (1008 B)
  -   sub_430DFC (654 B)
  -   sub_4316EC (75 B): chama TAebVector::SetEcuData
  -   sub_431738 (21 B)
  -   sub_431AF8 (124 B)
  -   sub_431B7C (186 B): chama TAebVector::SetEcuData
  -   sub_431C6C (37 B)
  - … +22 (ver telas.json)
- `MenuCominciaDrtClick` (0x0050D86C):
  - sub_40487C (45 B)
  - sub_4E6484 (48 B)
  - sub_4E64B4 (48 B)
  - sub_4E64E4 (48 B)
  - sub_50D524 (162 B)
- `MenuGraficoClick` (0x0050DC08):
  - sub_40487C (45 B)
  - sub_4DF6FC (22084 B): textos ACTIONESCI,ACTIONSTAMPA,BUTTONANNULLA,BUTTONOK; APIs DeleteMenu,GetSystemMenu,GetSystemMetrics; chama TAboutBox.Create,TTranslatorExt::GetString
  -   sub_4047FC (125 B)
  -   sub_4E4EDF (482 B)
  -   sub_4E4F0B (438 B)
  -   sub_4E4FDF (226 B)
  -   sub_4E5040 (129 B)
  -   sub_4E50C4 (61 B)
  -   sub_4E5194 (46 B)
  -   sub_4E51C4 (69 B)
  -   sub_4E520C (83 B)
  -   sub_4E5260 (666 B)
  - … +38 (ver telas.json)
- `MenuTerminaDrtClick` (0x0050DB50):
  - sub_40487C (45 B)
  - sub_50D524 (162 B)
- `Timer1Timer` (0x0050B408):
  - sub_40487C (45 B)
  - sub_404C40 (81 B)
  - sub_404C94 (59 B)
  - sub_405754 (48 B)
  - sub_4057C0 (139 B)
  - sub_40584C (57 B)
  - sub_42A52C (358 B)
  - sub_42A788 (358 B)
  - sub_42CFB8 (839 B)
  - sub_4339B8 (72 B)
  - sub_433A00 (75 B)
  - sub_433FFC (14 B)
  - … +13 (ver telas.json)
