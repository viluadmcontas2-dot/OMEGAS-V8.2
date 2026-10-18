# Como retomar a decompilação do ProgBase (para Codex ou qualquer agente)

Objetivo: reconstruir 100% do programa original (telas, botões, funções, comandos e respostas) e nunca mais abrir o .exe.
Regras: nunca alterar o .exe; nunca contornar licença; não versionar .exe/DLL (só texto/JSON); marcas OBS-COD / OBS-FIO / DERIVADO / INFERIDO / DESCONHECIDO em toda afirmação; scripts idempotentes (rodar 2× = mesmos bytes); testes RED→GREEN que pulam se o .exe faltar.

## Onde está o que
- Binário: fora do repo. Baixar com `python3 tools/obter_progbase.py` (metadados em docs/mapa-de-fontes/progbase.json). Variável `PROGBASE_EXE`.
- Já descoberto antes (REAPROVEITAR, não refazer): pasta PROTOCOLO-LANDI (Drive e cópia local /home/user/protocolo-landi: docs 00-22, dfm/, reverse/), docs/evidence/2026-10-18-*.md, docs/evidence/flags-do-original.json, tools/emulacao/*.py.
- Trabalho em curso, por frente (cada pasta tem PROGRESSO.md com o estado exato, o próximo passo e o que falta):
  - docs/progbase/inventario/ — formulários, menus, handlers, i18n, backlog de funções sem nome
  - docs/progbase/protocolo/ — todos os comandos, respostas esperadas, vetores com escala/unidade
  - docs/progbase/abas/ — um arquivo por aba do TFormConfig (controle → vetor → conversão → comando)
  - docs/progbase/decomp/ — desmontagem/pseudocódigo por função, cobertura %, verificação REA (evidence ids)
  - docs/progbase/grafo/ — Graphify específico do ProgBase; INDEX.md liga função → doc → evidence id → nó
- Ferramentas: /tmp/uc/bin/python (unicorn/capstone/pefile); `tools/emulacao/cpu_setter_original.py` executa funções do .exe numa CPU emulada.

## Procedimento de retomada
1. Ler este arquivo e cada PROGRESSO.md.
2. Rodar `python3 -B -m unittest discover -s tests -p "test_progbase_*.py"` — o que está verde está pronto.
3. Continuar SOMENTE a partir do "próximo passo" de cada PROGRESSO.md. Atualizar o PROGRESSO.md a cada marco. Commit + push a cada marco.
4. Ao terminar uma frente: rodar `tools/progbase/grafo_progbase.py`, atualizar INDEX.md e COBERTURA.md.
