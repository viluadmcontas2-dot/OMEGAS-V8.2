# Progresso do inventário (D1) — checkpoint

PRONTO (gerado, idempotente byte a byte, testado com PYTHONHASHSEED diferente): classes.json/md (1752 classes, 101 do programa, eventos DFM→handler), dfm/ (43 DFMs + INDICE), i18n.json/md (1160 chaves, 26 idiomas, 1201 usos no código por índice), telas.json/md + telas/ (43 telas, 552 handlers, 161 curados), menus_atalhos.md, dialogos.md, funcionalidades.md (28 itens, evidência verificada), funcoes.json/md, backlog*.md.
Regerar: `PROGBASE_EXE=... python3 tools/progbase/inventario_tudo.py` (~40 s). Teste: `python3 tests/test_progbase_inventario.py` (pula sem o .exe).
PRÓXIMO: curar os ~133 handlers automáticos fora de TFormConfig (backlog.md §5) em tools/progbase/dados/descricoes_handlers.json; nomear sub_ da Prioridade A (nomes_manuais.json); ampliar testes (RED→GREEN formal, idempotência).
FALTA: testes só básicos; RT_BITMAP/ICON/VERSION não catalogados; 133 chaves i18n sem ligação; commit (não feito).
ARMADILHAS: `python -I` não põe o cwd no path (rodar testes por caminho); unidades sem marcador @@X@Initialize fundem na seguinte; limite 0x405DC0 é INFERIDO; tools/progbase/abas_a.py é de outro agente.
NOTA: gravado aqui (não em docs/progbase/protocolo) para não colidir com o D2.
