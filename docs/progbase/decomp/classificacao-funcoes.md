# Classificação das funções sem nome do ProgBase.exe (programa × biblioteca)

Gerado por `tools/progbase/classificar_funcoes.py` (idempotente) a partir de `funcoes.json.gz` + `rtti.json`.

## Resultado (DERIVADO)

- Alvo: **7132** funções sem nome e sem unit.
- biblioteca: **4409**
- desconhecida: **1713**
- programa: **1010**

Por confiança: biblioteca/alta=548, biblioteca/baixa=71, biblioteca/media=3790, desconhecida/None=1713, programa/alta=99, programa/baixa=122, programa/media=789

Todas as 29927 funções (com nome/unit + alvo): programa=3747, biblioteca=24466, desconhecida=1714.

## Critério

1. Âncoras: unit do RTTI (OBS-COD) ou por intervalo (INFERIDO); nome Ghidra com mangling Borland (`@Ns@Unit@…`, `@@Unit@Initialize`, `@TClasse@…`) ou import/CRT (OBS-COD). Namespace Embarcadero/terceiros (System, Vcl, VCLTee, Xml, Id…) = biblioteca; unit sem namespace (forms Landi, componentes Aeb*) = programa.
2. `System.Generics.*` não é âncora: instâncias genéricas ficam dentro da unit que as usa (DERIVADO das faixas).
3. Alvo herda a categoria das âncoras vizinhas quando concordam (INFERIDO; alta = mesma unit, media = mesma categoria).
4. Vizinhos discordam: chamadores unânimes (INFERIDO, baixa). Senão DESCONHECIDO.

## Taxa de confiança (DERIVADO)

Deixa-um-de-fora do passo 3 sobre 8702 âncoras OBS-COD: 8660 acertos = **99.52%**. Mede só a regra de vizinhança; a regra de chamadores (baixa) não tem validação.

Limites: "programa" inclui componentes AEB (fornecedor do ProgBase), não só os forms Landi (INFERIDO). Funções na fronteira entre .obj de categorias diferentes ficam desconhecidas por construção.
