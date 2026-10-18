# TFormCorrCalibra (recurso `TFORMCORRCALIBRA`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormCorrCalibra`; unidade: `@:CorrCalibra`; ancestral: `TForm`; tamanho de instância: 0x524; componentes no DFM: 21.
- Título: "Modifica calibrazione" — eng: "Modify calibration" / por: "Modificar calibração"
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

## Ações (3)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| ActionListCorrCalibra/ActionIncrementaZona | TAction | &Incrementa zona (PgUp) / &Increase zone (PgUp) / &Incrementar zona (PgUp) | PgUp | OnExecute→`ActionIncrementaZonaExecute` (0x004CCFFC) |
| ActionListCorrCalibra/ActionDecrementaZona | TAction | &Decrementa zona (PgDn) / &Decrease zone (PgDn) / &Decrementar zona (PgDn) | PgDn | OnExecute→`ActionDecrementaZonaExecute` (0x004CD134) |
| ActionListCorrCalibra/ActionEsci | TAction | &Esci (Esc) / &Exit (Esc) / &Sair (Esc) | Esc | OnExecute→`ActionEsciExecute` (0x004CD26C) |

## Controles com evento/ação (5)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| FormCorrCalibra | TFormCorrCalibra | Modifica calibrazione / Modify calibration / Modificar calibração |  | OnClose→`FormClose` (0x004CC63C) |
| PanelMessaggio/ButtonIncrementa | TButton | &Incrementa zona (PgUp) / &Increase zone (PgUp) / &Incrementar zona (PgUp) |  | ActionIncrementaZona.OnExecute→`ActionIncrementaZonaExecute` (0x004CCFFC) |
| PanelMessaggio/ButtonDecrementa | TButton | &Decrementa zona (PgDn) / &Decrease zone (PgDn) / &Decrementar zona (PgDn) |  | ActionDecrementaZona.OnExecute→`ActionDecrementaZonaExecute` (0x004CD134) |
| PanelMessaggio/ButtonEsci | TButton | &Esci (Esc) / &Exit (Esc) / &Sair (Esc) |  | ActionEsci.OnExecute→`ActionEsciExecute` (0x004CD26C) |
| PanelMappa/DrawGridK | TDrawGrid |  |  | OnDrawCell→`DrawGridKDrawCell` (0x004CCD08) |

## Temporizadores (1)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) | Intervalo ms |
|---|---|---|---|---|---|
| TimerBlink | TTimer |  |  | OnTimer→`TimerBlinkTimer` (0x004CC664) | 50 |

## Handlers (6): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos (chaves i18n) | APIs | Chama |
|---|---|---:|---|---|---|---|---|
| `ActionDecrementaZonaExecute` | 0x004CD134 | 312 | INFERIDO | Handler `ActionDecrementaZonaExecute`; chama APIs do Windows: USER32.DLL!InvalidateRect; chama: thunk -> USER32.DLL!InvalidateRect; toca campos/controles: DrawGridK, TimerBlink |  | USER32.DLL!InvalidateRect | thunk -> USER32.DLL!InvalidateRect |
| `ActionEsciExecute` | 0x004CD26C | 6 | INFERIDO | Handler `ActionEsciExecute`; sem fatos extraíveis automaticamente (6 bytes) |  |  |  |
| `ActionIncrementaZonaExecute` | 0x004CCFFC | 312 | INFERIDO | Handler `ActionIncrementaZonaExecute`; chama APIs do Windows: USER32.DLL!InvalidateRect; chama: thunk -> USER32.DLL!InvalidateRect; toca campos/controles: DrawGridK, TimerBlink |  | USER32.DLL!InvalidateRect | thunk -> USER32.DLL!InvalidateRect |
| `DrawGridKDrawCell` | 0x004CCD08 | 754 | INFERIDO | Handler `DrawGridKDrawCell`; toca campos/controles: DrawGridK |  |  |  |
| `FormClose` | 0x004CC63C | 40 | INFERIDO | Handler `FormClose`; sem fatos extraíveis automaticamente (40 bytes) |  |  |  |
| `TimerBlinkTimer` | 0x004CC664 | 940 | INFERIDO | Handler `TimerBlinkTimer`; chama APIs do Windows: USER32.DLL!InvalidateRect; chama: TStreamDati.TimerIOTimer; toca campos/controles: LabApMotBenz, LabGas, LabLambda, LabelLambda |  | USER32.DLL!InvalidateRect | TStreamDati.TimerIOTimer |
