# TFormVisualizza (recurso `TFORMVISUALIZZA`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormVisualizza`; unidade: `@:Visualizza`; ancestral: `TForm`; tamanho de instância: 0x74C; componentes no DFM: 85.
- Título: "Visualizzazione parametri vettura" — eng: "Vehicle parameter display" / por: "Visualização dos parâmetros do veículo"
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

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

| Handler | Endereço | Bytes | Evidência | O que faz | Textos (chaves i18n) | APIs | Chama |
|---|---|---:|---|---|---|---|---|
| `ButtonCommutatoreClick` | 0x0050DD14 | 231 | INFERIDO | Handler `ButtonCommutatoreClick`; chama APIs do Windows: KERNEL32.DLL!Sleep, USER32.DLL!PostMessageW; chama: thunk -> KERNEL32.DLL!Sleep; toca campos/controles: ButtonCommutatore |  | KERNEL32.DLL!Sleep, USER32.DLL!PostMessageW | thunk -> KERNEL32.DLL!Sleep |
| `FormClose` | 0x0050DCEC | 39 | INFERIDO | Handler `FormClose`; sem fatos extraíveis automaticamente (39 bytes) |  |  |  |
| `MenuCominciaDrtClick` (sem evento no DFM) | 0x0050D86C | 739 | INFERIDO | Handler `MenuCominciaDrtClick`; chama APIs do Windows: KERNEL32.DLL!GetTickCount, USER32.DLL!DrawMenuBar; chama: thunk -> KERNEL32.DLL!GetTickCount, thunk -> USER32.DLL!DrawMenuBar |  | KERNEL32.DLL!GetTickCount, USER32.DLL!DrawMenuBar | thunk -> KERNEL32.DLL!GetTickCount, thunk -> USER32.DLL!DrawMenuBar |
| `MenuGraficoClick` (sem evento no DFM) | 0x0050DC08 | 187 | INFERIDO | Handler `MenuGraficoClick`; mostra/usa textos ACTIONESCI, ACTIONSTAMPA, BUTTONANNULLA, BUTTONOK, FINESTRAGESTIONETRACCE, FINESTRAGRAFICO; chama APIs do Windows: USER32.DLL!DeleteMenu, USER32.DLL!GetSystemMenu, USER32.DLL!GetSystemMetrics; toca campos/controles: OpenDialogDrt | ACTIONESCI, ACTIONSTAMPA, BUTTONANNULLA, BUTTONOK, FINESTRAGESTIONETRACCE, FINESTRAGRAFICO | USER32.DLL!DeleteMenu, USER32.DLL!GetSystemMenu, USER32.DLL!GetSystemMetrics |  |
| `MenuTerminaDrtClick` (sem evento no DFM) | 0x0050DB50 | 184 | INFERIDO | Handler `MenuTerminaDrtClick`; chama APIs do Windows: USER32.DLL!DrawMenuBar; chama: thunk -> USER32.DLL!DrawMenuBar; toca campos/controles: SaveDialogDrt |  | USER32.DLL!DrawMenuBar | thunk -> USER32.DLL!DrawMenuBar |
| `Timer1Timer` | 0x0050B408 | 7653 | INFERIDO | Handler `Timer1Timer`; chama APIs do Windows: KERNEL32.DLL!GetTickCount; chama: TStreamDati.TimerIOTimer, thunk -> KERNEL32.DLL!GetTickCount; toca campos/controles: ImageBenzOff, ImageBenzOn, ImageCutoffOff, ImageCutoffOn, ImageGasOff, ImageGasOn, ImageSottoChiaveOff, ImageSottoChiaveOn |  | KERNEL32.DLL!GetTickCount | TStreamDati.TimerIOTimer, thunk -> KERNEL32.DLL!GetTickCount |
