# TFormGraficoDrt (recurso `TFORMGRAFICODRT`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormGraficoDrt`; unidade: `@:GraficoDrt`; ancestral: `TForm`; tamanho de instância: 0x424; componentes no DFM: 24.
- Título: "Grafico" — eng: "Graph" / por: "Gráfico"
- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): `TFormVisualizza.MenuGraficoClick` (0x0050DC08)

## Menus (7)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| MainMenu1/MenuGraficoDrt | TMenuItem | &Grafico / &Graph / &Gráfico |  |  |
| MainMenu1/MenuGraficoDrt/MenuTracce | TMenuItem | Gestione tracce |  | OnClick→`MenuTracceClick` (0x004E6A84) |
| MainMenu1/MenuGraficoDrt/MenuStampa | TMenuItem | &Stampa / &Print / &Imprimir | Ctrl+P | OnClick→`MenuStampaClick` (0x004E6A50) |
| MainMenu1/MenuGraficoDrt/N2 | TMenuItem | - |  |  |
| MainMenu1/MenuGraficoDrt/MenuAnnullaZoom | TMenuItem | &Annulla zoom / &Cancel zoom / &Cancelar zoom | Ctrl+Z | OnClick→`MenuAnnullaZoomClick` (0x004E6A24) |
| MainMenu1/MenuGraficoDrt/N1 | TMenuItem | - |  |  |
| MainMenu1/MenuGraficoDrt/MenuEsci | TMenuItem | &Esci / &Exit / &Sair | Esc | OnClick→`MenuEsciClick` (0x004E6A48) |

## Controles com evento/ação (1)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) |
|---|---|---|---|---|
| ChartDrt | TChart |  |  | OnClickSeries→`ChartDrtClickSeries` (0x004E6E34) |

## Caixas de diálogo (componentes) (1)

| Caminho/componente | Classe | Legenda (ita / eng / por) | Atalho | Eventos → handler (endereço) | Filtro |
|---|---|---|---|---|---|
| PrintDialog1 | TPrintDialog |  |  |  |  |

## Handlers (5): o que cada um faz

| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |
|---|---|---:|---|---|---|---|---|
| `ChartDrtClickSeries` | 0x004E6E34 | 36 | INFERIDO | Ao clicar numa série de ChartDrt: corpo de 36 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `MenuAnnullaZoomClick` | 0x004E6A24 | 33 | INFERIDO | Ao clicar em MenuAnnullaZoom «&Cancelar zoom»: mexe em ChartDrt. |  |  |  |
| `MenuEsciClick` | 0x004E6A48 | 6 | INFERIDO | Ao clicar em MenuEsci «&Sair»: corpo de 6 bytes sem fatos diretos (chama 1 funções sem nome). |  |  |  |
| `MenuStampaClick` | 0x004E6A50 | 52 | INFERIDO | Ao clicar em MenuStampa «&Imprimir»: mexe em ChartDrt, PrintDialog1. |  |  |  |
| `MenuTracceClick` | 0x004E6A84 | 807 | INFERIDO | Ao clicar em MenuTracce «Gestione tracce»: chama TFormGestioneTracce.Create; mexe em ChartDrt, Series1. |  |  |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `MenuTracceClick` (0x004E6A84):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
