# Frente B — checkpoint 2026-10-19

PRONTO: `tools/progbase/abas_b_gerar.py` gera JSON + Markdown das 17 abas restantes: 597 ocorrências de controles (contêineres aninhados aparecem também em suas abas). Propriedades DFM, combos, grades e eventos com endereço RTTI são OBS-COD. IDs MAN-ABAS-B-* são locais, não IDs REA. SHA-256 do binário validado antes de gerar. Fórmulas e efeitos de execução não provados permanecem DESCONHECIDO; inventário não equivale a reconstrução funcional completa.

CORREÇÃO DA EMULAÇÃO: RED reproduziu `Runtime error 203 at 006958E5`, seguido de ExitProcess ao abrir TabIniezione. OBS-COD: `0x6958E0` chama `0x690700`, que despacha ReallocMem por `[0xAAFB8C]`, GetMem por `[0xAAFB84]` e FreeMem por `[0xAAFB88]`. DERIVADO: inicializar a tabela de seis ponteiros do gerenciador Delphi com os stubs já existentes remove a dependência de inicialização do heap real do processo. Só memória da máquina emulada é alterada; arquivo .exe intacto. A execução ultrapassa esse ponto, agora interrompida em `LoadCursorW`: limitação de janela VCL, não erro de memória.

TESTE FOCADO: `python3 tests/test_progbase_abas_b.py` exercita os dois quadros existentes, heap/realloc preservando bytes, regressão TabIniezione e inventário de 17 abas. Pula quando exe/unicorn ausentes. O teste da regressão afirma o limite de memória, não conclusão do handler nem imagem renderizada.

PRÓXIMO PASSO: modelar a fronteira de janela atingida por TabIniezioneShow (LoadCursorW) sem neutralizar lógica; executar os demais OnShow e registrar limites separados. Depois investigar fórmulas/combos/grades e controles Cicchetto/Livello/Pressione/Flash Lube, cruzando evidência existente. Validar efeitos com original Windows/Wine antes de chamar runtime completo. Conectar IDs locais ao INDEX e obter IDs REA reais pelo fluxo de decomp.

AUDITORIA proposta: 2026-10-19 | frente B | inventário das 17 abas com 597 ocorrências de controles; reparo do heap Delphi em memória emulada; RED runtime 203 → GREEN focado; janela LoadCursorW e fórmulas permanecem pendentes.
