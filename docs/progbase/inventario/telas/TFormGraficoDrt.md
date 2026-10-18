# TFormGraficoDrt (recurso `TFORMGRAFICODRT`)

Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.

- Instância DFM: `FormGraficoDrt`; unidade: `@:GraficoDrt`; ancestral: `TForm`; tamanho de instância: 0x424; componentes no DFM: 24.
- Título: "Grafico" — eng: "Graph" / por: "Gráfico"
- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).

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

| Handler | Endereço | Bytes | Evidência | O que faz | Textos (chaves i18n) | APIs | Chama |
|---|---|---:|---|---|---|---|---|
| `ChartDrtClickSeries` | 0x004E6E34 | 36 | INFERIDO | Handler `ChartDrtClickSeries`; sem fatos extraíveis automaticamente (36 bytes) |  |  |  |
| `MenuAnnullaZoomClick` | 0x004E6A24 | 33 | INFERIDO | Handler `MenuAnnullaZoomClick`; toca campos/controles: ChartDrt |  |  |  |
| `MenuEsciClick` | 0x004E6A48 | 6 | INFERIDO | Handler `MenuEsciClick`; sem fatos extraíveis automaticamente (6 bytes) |  |  |  |
| `MenuStampaClick` | 0x004E6A50 | 52 | INFERIDO | Handler `MenuStampaClick`; toca campos/controles: ChartDrt, PrintDialog1 |  |  |  |
| `MenuTracceClick` | 0x004E6A84 | 807 | INFERIDO | Handler `MenuTracceClick`; chama: TFormGestioneTracce.vmt[0x38]; toca campos/controles: ChartDrt, Series1 |  |  | TFormGestioneTracce.vmt[0x38] |
