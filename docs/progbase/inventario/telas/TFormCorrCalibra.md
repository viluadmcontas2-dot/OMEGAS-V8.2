# TFormCorrCalibra (recurso `TFORMCORRCALIBRA`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormCorrCalibra`; unidade: `@:CorrCalibra`; ancestral: `TForm`; tamanho de instância: 0x524; componentes no DFM: 21.
- Título: "Modifica calibrazione" — eng: "Modify calibration" / por: "Modificar calibração"
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `TFormMain.ActionCorrTaraExecute` (0x00500DE4)

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

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `ActionDecrementaZonaExecute` | 0x004CD134 | 312 | INFERIDO | chama APIs do Windows InvalidateRect; toca DrawGridK, TimerBlink |  | InvalidateRect |  |
| `ActionEsciExecute` | 0x004CD26C | 6 | INFERIDO | sem fatos diretos extraíveis (6 bytes; chama 1 funções sem nome) |  |  |  |
| `ActionIncrementaZonaExecute` | 0x004CCFFC | 312 | INFERIDO | chama APIs do Windows InvalidateRect; toca DrawGridK, TimerBlink |  | InvalidateRect |  |
| `DrawGridKDrawCell` | 0x004CCD08 | 754 | INFERIDO | toca DrawGridK |  |  |  |
| `FormClose` | 0x004CC63C | 40 | INFERIDO | sem fatos diretos extraíveis (40 bytes; chama 2 funções sem nome) |  |  |  |
| `TimerBlinkTimer` | 0x004CC664 | 940 | INFERIDO | chama TStreamDati.TimerIOTimer; toca LabApMotBenz, LabGas, LabLambda, LabelLambda |  |  | InvalidateRect |

### Cadeia de subfunções sem nome alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `ActionDecrementaZonaExecute` (0x004CD134):
  - sub_42F4AC (186 B)
  -   sub_42EC94 (65 B)
  -   sub_42ECD8 (181 B): chama TAebMatrix::SetEcuData
  -   sub_42F338 (7 B)
  -   sub_42F340 (7 B)
  -   sub_42F348 (27 B)
  -   sub_42F364 (135 B): chama TAebMatrix::SetEcuData
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
- `ActionIncrementaZonaExecute` (0x004CCFFC):
  - sub_42F3EC (189 B)
  -   sub_42EC94 (65 B)
  -   sub_42ECD8 (181 B): chama TAebMatrix::SetEcuData
  -   sub_42F338 (7 B)
  -   sub_42F340 (7 B)
  -   sub_42F348 (27 B)
  -   sub_42F364 (135 B): chama TAebMatrix::SetEcuData
  -     sub_42B224 (15 B)
  -     sub_4316EC (75 B): chama TAebVector::SetEcuData
  -     sub_431738 (21 B)
- `DrawGridKDrawCell` (0x004CCD08):
  - sub_40487C (45 B)
  - sub_42F348 (27 B)
- `FormClose` (0x004CC63C):
  - sub_4381F4 (7 B)
- `TimerBlinkTimer` (0x004CC664):
  - sub_40487C (45 B)
  - sub_42A52C (358 B)
  - sub_42A788 (358 B)
  - sub_433FFC (14 B)
  - sub_434530 (21 B)
  - sub_4381FC (6 B)
  - sub_480908 (126 B)
  - sub_4CCB58 (430 B): APIs InvalidateRect; toca DrawGridK
  -   sub_42F338 (7 B)
  -   sub_42F340 (7 B)
  -   sub_42F640 (101 B)
