# TFormGraficoDrt (recurso `TFORMGRAFICODRT`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

**Papel:** tela de função (DERIVADO). Gráfico de aquisição gravada (traços, zoom, imprimir).

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
| `MenuAnnullaZoomClick` | 0x004E6A24 | 33 | OBS-COD | Menu Cancelar zoom (Ctrl+Z): restaura o zoom do gráfico. OBS-COD. |  |  |  |
| `MenuEsciClick` | 0x004E6A48 | 6 | OBS-COD | Menu Sair (Esc): fecha a janela do gráfico (6 B). OBS-COD. |  |  |  |
| `MenuStampaClick` | 0x004E6A50 | 52 | DERIVADO | Menu Imprimir (Ctrl+P): chama PrintDialog1 e imprime o gráfico (ChartDrt). OBS-COD (campos). |  |  |  |
| `MenuTracceClick` | 0x004E6A84 | 807 | DERIVADO | Menu "Gestão de traços": abre TFormGestioneTracce (lista "CheckTrack" para mostrar/ocultar séries do gráfico). OBS-COD. |  |  |  |

### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)

- `MenuTracceClick` (0x004E6A84):
  - (+1 subfunções sem fatos diretos ou fora da lista; telas.json)
