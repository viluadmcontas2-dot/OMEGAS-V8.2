# Frente B — checkpoint 2026-10-19

PRONTO: `tools/progbase/abas_b_gerar.py` gera JSON + Markdown das 17 abas restantes: 597 ocorrências de controles (contêineres aninhados aparecem também em suas abas). Propriedades DFM, combos, grades e eventos com endereço RTTI são OBS-COD. IDs MAN-ABAS-B-* são locais, não IDs REA. SHA-256 do binário validado antes de gerar. Fórmulas e efeitos de execução não provados permanecem DESCONHECIDO; inventário não equivale a reconstrução funcional completa.

CORREÇÃO DA EMULAÇÃO: RED reproduziu `Runtime error 203 at 006958E5`, seguido de ExitProcess ao abrir TabIniezione. OBS-COD: `0x6958E0` chama `0x690700`, que despacha ReallocMem por `[0xAAFB8C]`, GetMem por `[0xAAFB84]` e FreeMem por `[0xAAFB88]`. DERIVADO: inicializar a tabela de seis ponteiros do gerenciador Delphi com os stubs já existentes remove a dependência de inicialização do heap real do processo. Só memória da máquina emulada é alterada; arquivo .exe intacto. A execução ultrapassa esse ponto, agora interrompida em `LoadCursorW`: limitação de janela VCL, não erro de memória.

TESTE FOCADO: `python3 tests/test_progbase_abas_b.py` exercita os dois quadros existentes, heap/realloc preservando bytes, regressão TabIniezione e inventário de 17 abas. Pula quando exe/unicorn ausentes. O teste da regressão afirma o limite de memória, não conclusão do handler nem imagem renderizada.

PRÓXIMO PASSO: modelar a fronteira de janela atingida por TabIniezioneShow (LoadCursorW) sem neutralizar lógica; executar os demais OnShow e registrar limites separados. Depois investigar fórmulas/combos/grades e controles Cicchetto/Livello/Pressione/Flash Lube, cruzando evidência existente. Validar efeitos com original Windows/Wine antes de chamar runtime completo. Conectar IDs locais ao INDEX e obter IDs REA reais pelo fluxo de decomp.

AUDITORIA proposta: 2026-10-19 | frente B | inventário das 17 abas com 597 ocorrências de controles; reparo do heap Delphi em memória emulada; RED runtime 203 → GREEN focado; janela LoadCursorW e fórmulas permanecem pendentes.

## Continuação — cursor, limites por aba e isolamento do diagnóstico

- OBS-CPU: o modelo já existente de `LoadCursorW(NULL, 32512)` foi alcançado por TabIniezioneShow; depois o original chega a `GetClassInfoW`, retorno `0x007F2D82`. O modelo mantém handle opaco estável e rejeita recursos não modelados; não desenha uma janela. Não foi acrescentado stub genérico para aceitar `GetClassInfoW`.
- DERIVADO: executar vários cenários revelou que `chamador_erro`/`pilha_erro` sobreviviam à restauração e atribuíam a handlers seguintes a falha antiga. RED: após Iniezione falhar e KVarie retornar, caller ainda era `8334722` (`0x7F2D82`). Correção mínima limpa os dois campos em `Sessao.restaurar`; teste novo reproduz a sequência real e exige diagnóstico vazio no segundo cenário.
- OBS-CPU, varredura sequencial dos 17 nomes de `abas_b_gerar.ABAS`, binário SHA esperado validado pelo contexto, cenário sintético padrão (`cenario()`, sem corpus do dono), nenhum quadro serial emitido. Cada cenário restaura memória/estado. Resultados abaixo são fronteiras de execução, não equivalência visual nem validação da ECU:

| Aba | Resultado | Instruções |
|---|---|---:|
| TabKVarie | retornou sob os stubs existentes | 1231 |
| TabAdvanced | retornou sob os stubs existentes | 525 |
| TabCalibration | retornou sob os stubs existentes | 2902 |
| TabIniezione | GetClassInfoW não modelado; cursor compartilhado carregado | 9641 |
| TabInjSettings | sem OnShow no DFM | — |
| TabInjSettingsSt | sem OnShow no DFM | — |
| TabTaratura | retornou sob os stubs existentes | 1515 |
| TabVariatore | sem OnShow no DFM | — |
| TabBenzina | retornou sob os stubs existentes | 6038 |
| TabAdatta | GetClassInfoW não modelado; cursor compartilhado carregado | 3268 |
| TabConsumometro | GetClassInfoW não modelado; cursor compartilhado carregado | 1539 |
| TabFrest | retornou sob os stubs existentes | 480 |
| TabSwitchToPetrol | retornou sob os stubs existentes | 477 |
| TabDhlpAgaslev | retornou sob os stubs existentes | 468 |
| TabTest | retornou sob os stubs existentes | 906 |
| TabAutoCalNew | limite de instruções; conclusão não demonstrada | 6000001 |
| TabEsci | retornou sob os stubs existentes | 641 |

- Limite metodológico: a varredura ocorreu antes da correção do diagnóstico; caller/pilha herdados em execuções posteriores não são evidência e foram descartados. `erro`, instruções e efeitos são reiniciados pelo código anterior e sustentam a tabela. Retornar sob stubs não demonstra comportamento com dados reais; página nula mapeada e APIs que devolvem zero já existentes continuam limites da reconstrução.
- PRÓXIMO PASSO atualizado: investigar `GetClassInfoW` em `0x7F2D82` no pseudocódigo existente, montar apenas a classe de janela realmente solicitada com estrutura verificável; localizar o laço de TabAutoCalNew sem remover sua lógica. Repetir depois com o estado/corpus do dono. Confirmar interface no Wine antes de chamar runtime completo; fórmulas/combos/grades e evidências REA continuam pendentes.
- GREEN focado: `/tmp/uc/bin/python tests/test_progbase_abas_b.py` com `PROGBASE_EXE` configurado — **7 testes passaram em 19,529 s**, incluindo preservação do heap, recurso de cursor inesperado, isolamento de diagnóstico e os dois quadros de escrita já conhecidos. Não houve suíte ampla local nem nova prova de runtime Windows.
