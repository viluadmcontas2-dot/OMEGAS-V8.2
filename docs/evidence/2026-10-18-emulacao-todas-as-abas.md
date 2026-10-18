# Emulação de todas as abas da tela de configuração do ProgBase (TFormConfig) — 2026-10-18

Marcas: **OBS-COD** = está no recurso/código do ProgBase.exe (o DFM é recurso do executável) · **OBS-FIO** = visto no log Portmon · **INFERIDO** = dedução · **DESCONHECIDO** = sem prova.

## Em uma frase
Agora dá para "abrir" qualquer uma das 33 abas da tela de configuração do ProgBase original sem rodar o programa: o desenho sai do próprio formulário (DFM) que está dentro do executável, os textos saem do arquivo de traduções, e o que depende de código fica numa tabela de regras separada. **Não há print real do ProgBase para comparar** — estas imagens são reconstrução, não foto.

## Por que não rodamos o original
O ProgBase original não roda aqui sem licença, e **não vamos contornar a licença**. Por isso a receita é a da skill `emular-por-observacao`: emular pelo que já temos (DFM, traduções, log, código analisado).

## O que foi entregue
| Arquivo | Para que serve |
|---|---|
| `tools/emulacao/dfm_renderer.py` | Renderer genérico de DFM (só Python padrão, mesma entrada → mesmos bytes). |
| `tools/emulacao/regras_tformconfig.json` | Tabela de regras que dependem de código, cada uma com marca e fonte. |
| `tests/test_dfm_renderer.py` | 22 testes (todos verdes). |
| `docs/evidence/img/config-abas/<aba>.png` | 33 fotos (Chromium headless), uma por aba, com a legenda **EMULADO DO DFM**. |

Uso:
```
python3 tools/emulacao/dfm_renderer.py --aba todas --valores tests/fixtures/config-iniettori-lognovo-v1.json --saida /tmp/abas --png
python3 tools/emulacao/dfm_renderer.py --aba TabIniezione --valores v.json --saida tela.html
```
`--valores` aceita a fixture do log (`vetores` com `u16`) e, para simulação, `controles` (ex.: `{"CheckAntiStallo": {"Enabled": true}}`); o que vem daí é marcado `VALOR-DADO` no HTML.
Testes: `python3 -B -m unittest tests.test_dfm_renderer` (o emulador antigo `orig_config_emulator.py` não foi tocado; seus 10 testes seguem verdes).

## O que bate com o DFM (provado por teste)
- **33 abas**, na ordem do DFM (21 no PageControl principal + 10 em `PageControlK` + 2 em `PageControlInj`).
- **Contagem de controles por aba** igual à do DFM, contada por um leitor separado, linha a linha, dentro do teste. Nas abas que têm sub-abas, entra só a sub-aba ativa (`ActivePage` do DFM). Menus, timers, dicas e séries de gráfico não contam (não são controles na tela; as séries aparecem listadas dentro do gráfico).
- **Posição e tamanho** (Left/Top/Width/Height) de todo controle sem `Align` em 6 abas, conferidos um a um. Os scrollboxes estavam rolados no projeto (`VertScrollBar.Position = 671` na aba Cambio): o renderer desfaz a rolagem e mostra o conteúdo inteiro, de cima a baixo (por isso as fotos são mais altas que a janela de 1162×849).
- `Align` (alTop/alBottom/alLeft/alRight/alClient, com margens) e `TGridPanel` (colunas/linhas em %), como a VCL faz.
- Fonte (nome, tamanho em px a partir de `Font.Height`, negrito, cor) com herança `ParentFont`; cores TColor convertidas de BGR (10158080 → #00009B).
- Textos: `i18n_all.json` pela chave = nome do componente em maiúsculas, idioma `por`, senão `eng`, senão o Caption italiano do DFM. Cada controle diz de onde veio o texto (`data-texto-fonte`). O arquivo de traduções tem acentos quebrados (UTF-8 lido como Latin-1, "MudanÃ§a"); o renderer conserta ("Mudança do gás").
- Itens dos combos: `Items.Strings` do DFM, traduzidos pela chave `NOME1`, `NOME2`… quando existe.
- A aba AutoCal usa um *frame* (`TAutoCalUI`): o renderer junta o DFM-base `TAUTOCALUI.txt` com as sobrescritas do TFormConfig (18 controles vêm do frame e são marcados `data-origem="frame"`).
- Teste de mutação: mudar o Left de um controle no DFM muda a tela; apagar um controle quebra a contagem; mudar a máscara de bit da regra inverte a caixa; regra com marca inválida é recusada.
- Idempotência: duas rodadas de `--aba todas` geram os 33 HTML com o mesmo hash.

## Regras que dependem de código (tabela `regras_tformconfig.json`)
| Regra | Marca |
|---|---|
| Caixa anti-stallo marcada ⇔ FLAG_CONF3[0] bit 0x20 | OBS-COD |
| Caixa anti-stallo habilitada (capacidade `[obj+0x2A24]`) | **DESCONHECIDO** → contorno tracejado |
| 5 edits/rótulos/unidades do anti-stallo só com a caixa marcada **e** habilitada | OBS-COD (lógica de 3 estados: se a capacidade é desconhecida e a caixa está marcada, os edits ficam tracejados, não inventados) |
| EditAntiStalloN mostra ANTI_STALLO[N] | OBS-COD |
| Caixas Progressioni (FLAG_CONF3 w0 0x10), Taglio extrainiettate (FLAG_CONF2 w0 0x0001), Incrementa max corrente (FLAG_CONF2 w1 0x0400), Bassa pressione (FLAG_CONF1 w1 0x0040), Passaggio sequenziale (FLAG_CONF1 w1 0x1000) | OBS-COD (endereços no catálogo `2026-10-18-catalogo-funcoes-originais.md`) |
| Combo "tipo de troca": qual item é o bit 0 | DESCONHECIDO → "—" |
| Controle com `Visible=False` no DFM e sem regra própria | DESCONHECIDO → desenhado tracejado e apagado, com o nome |
| Aba com `TabVisible=False` | DESCONHECIDO → cabeçalho tracejado (15 das 21 abas principais; o código mostra conforme a ECU/família) |
| Valor de edit/combo sem regra nem valor dado | DESCONHECIDO → "—" (o `Text` do DFM é só placeholder) |
| Caixa sem bit mapeado | DESCONHECIDO → "?" tracejado |
| Controle sem `Visible=False` | INFERIDO visível (o código pode esconder) |

Com os valores reais do log (`config-iniettori-lognovo-v1.json`): caixa anti-stallo desmarcada, edits ocultos; "Passaggio sequenziale" marcada; "Taglio extrainiettate" marcada. No total das 33 abas: 414 controles visíveis, 483 com visibilidade DESCONHECIDA (tracejados), 15 ocultos por regra provada.

## O que é desconhecido ou aproximado
- **Não há print real para comparar.** O passo "comparar com o print do dono" da receita não foi feito; nenhuma foto do ProgBase rodando existe nas fontes.
- Para mostrar uma aba que o DFM esconde (`TabVisible=False`) ou um painel escondido que a contém (ex.: `PageControlK`), ela é **forçada para a foto**; no programa real ela só aparece se o código mandar.
- Desenho das abas: o PageControl principal é `OwnerDraw` (desenhado pelo código, `PageControlDrawTab`) — aparência, tamanho dos botões das sub-abas e cores de sistema (cl3DLight = #E3E3E3 etc.) são INFERIDOS (Windows 10 padrão). Os botões das sub-abas não cabem numa linha só; no real haveria setas de rolagem.
- Conteúdo de grades (`TDrawGrid`, desenhadas no `OnDrawCell`), gráficos (`TChart`) e imagens (`Picture.Data` truncado no texto do DFM): só a moldura; o conteúdo é DESCONHECIDO.
- Frames `TStrategiaPandaUI` e `TStrategiaTempiMortiUI`: o DFM-base deles não está nas fontes; os filhos aparecem como fichas tracejadas "geometria desconhecida".
- Rótulos com `AutoSize` crescem com o texto traduzido e podem passar por cima do vizinho (o português é mais longo que o italiano do projeto); o real faria o mesmo, mas não foi conferido.
- A barra de baixo (`PanelBarra`) aparece em todas as fotos com os valores de projeto ("n.d.", "0"), não com leitura da ECU.
- O menu principal (`MainMenu1`) não é desenhado.
