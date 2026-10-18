# PROGRESSO da decompilação do ProgBase.exe (checkpoint D4, 2026-10-18)

## PRONTO (OBS-COD salvo indicado)
- Ghidra 12.1.4 headless FUNCIONA (JDK 21; zip oficial por `tools/rea/install.sh` -> `~/.omegas-rea`). Projeto Ghidra só em scratchpad (não versionado).
- `rtti.json` (1579 classes, 8211 endereços nomeados, 43 DFM, 1376 eventos) — `tools/progbase/decomp_rtti.py` (só pefile).
- `funcoes.json.gz`: **29.927 funções** (endereço, tamanho, nome RTTI, unidade, chamadores/chamados, pistas, propósito) — 63% de .text em função, 20,3% nomeado, 39,8% com unit; **pseudo-C de 25.202 funções** em `pseudocodigo/pc_NN.jsonl.gz` (2,9 MB). Pasta decomp total 7,1 MB (< 40 MB).
- `COBERTURA.md` (+ `cobertura.json`, `backlog.json.gz`, `lacunas.json`, `funcoes-app.md`) com backlog e lacunas classificadas.
- `execucao-original.md` + prints: com o License.dat DO DONO (Drive, conector download_file_content) ao lado do exe, o original abre sob Wine (F1/F2/F3/F4/F5 vistos). Sem patch/keygen.
- Grafo: `tools/progbase/grafo_progbase.py` -> `docs/progbase/grafo/graph.json` (11,9 mil nós, 18,7 mil arestas, 9,4 MB; ingere decomp + D1 telas + D2 comandos/vetores/matriz + docs; 0 erros). `tests/test_progbase_decomp.py` passa (3 testes).
- `manual/manual-2.16.4C.txt` + `paginas.json` (55 páginas, ids MAN-2.16.4-pNNN) via `manual_extrair.py`.
- Regenerar do zero: `PB_EXE=... tools/progbase/decomp_tudo.sh` (~45 min).

## PRÓXIMO PASSO
1. REA: `mcp__rea__open_binary` abre, mas `analyze_function`/CLI `rea function` estouram (330 s CLI / 60 s MCP) porque a análise Ghidra interna do REA leva >13 min. Tentar: chamar `analyze_function` repetidamente até o MCP terminar a análise em segundo plano; depois `export_evidence_bundle` para `docs/progbase/decomp/evidence-bundle.json`, `compare_functions`, `verify_reconstruction`, `build_reconstruction_obligation_ledger`, `evaluate_reconstruction_coverage`, `audit_residual_unknowns` para cpu_setter_original (âncoras 0x42E8D8, 0x978F8C, 0x92617C, 0x923608, 0x91DF00, 0x923860), flags_do_original (0x42CFB8, 0x42D300, 0x978F8C), ecu_emulator (matriz). Escrever `rea-verificacao.md` (hoje só o registro do bloqueio).
2. Triar `lacunas.json` (≈2,25 MB "indefinido:resto") e as 6,3 mil funções sem nome/unit (`backlog.json.gz`); função gigante 0x00439E94 (289 KB, chama SendCommand 0x04 em 0x0043C761).
3. Grafo: ligar manual (nós `manual:2.16.4:pN`, ids MAN-) a controles/docs; nós de evidência `ev_`; `docs/progbase/INDEX.md`, `LEIA-ME.md`, `tests/test_progbase_integridade.py` (ids do bundle), integração em `tools/mapa_de_fontes.py` e job `grafo` do ci.yml; rodar grafo de novo quando D3 (abas) terminar.

## BLOQUEIOS / ARMADILHAS
- REA (MCP 60 s / CLI 330 s) vs. análise Ghidra de 12 MB: sem bundle de evidência ainda => nenhum evidence id novo foi gerado/inventado.
- `CriarFuncoesOrfas.java` converteu ~900 KB de "instruções soltas" de volta em indefinido (provavelmente dados); não repetir sem triagem.
- Ghidra e o projeto ficam em scratchpad; o exe/DLL/License.dat NÃO são versionados.
- Não commitado (ordem do coordenador).
