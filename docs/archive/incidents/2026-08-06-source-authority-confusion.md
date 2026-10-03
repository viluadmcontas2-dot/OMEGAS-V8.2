# Incidente — fonte funcional incorreta usada no diagnóstico

## Sintoma e impacto
Uma investigação de desempenho foi conduzida inicialmente contra um repositório de geração anterior, embora o APK instalado tivesse origem informada na branch `rebuild/ux-9in-dual-layout` do `OMEGAS-V7`.

O impacto foi misturar código não correspondente ao binário observado, produzindo conclusões que podiam parecer plausíveis sem provar a causa no aplicativo realmente instalado.

## Causa imediata
A origem do APK não foi confirmada no GitHub antes da leitura do código.

## Causa estrutural
Os documentos ainda permitiam usar uma referência funcional externa e um commit histórico como critério de comparação. A regra de fonte única não estava explícita em todos os documentos de entrada e decisão.

## Por que os testes e a governança não detectaram
Os gates verificavam código e contratos do repositório, mas não validavam que cada diagnóstico citasse o mesmo repositório, branch, commit, artifact e SHA-256 do APK analisado.

## Correção
- `OMEGAS-V7` definido como único repositório funcional;
- branch indicada pelo proprietário definida como origem obrigatória da investigação;
- referências externas removidas de estado, decisões, matriz e apresentação do projeto;
- proibição explícita de usar gerações anteriores como referência, doador, fallback ou evidência atual;
- identidade do APK passa a exigir commit, artifact e SHA-256 correspondentes.

## Regressão
Antes de concluir diagnóstico de APK, registrar obrigatoriamente:

1. repositório;
2. branch;
3. commit;
4. artifact;
5. SHA-256 do arquivo instalado ou analisado;
6. correspondência entre essa fotografia e o código auditado.

Sem essa ligação, o resultado deve permanecer `não confirmado`.

## Evidência
- repositório confirmado: `felipetbestkkj-ship-it/OMEGAS-V7`;
- branch confirmada: `rebuild/ux-9in-dual-layout`;
- proprietário informou que o APK da multimídia veio dessa branch;
- documentos de fonte e estado corrigidos na própria branch.

## Risco residual
O commit exato e o SHA-256 do APK atualmente instalado na multimídia ainda não foram ligados ao artifact correspondente. Até essa confirmação, a identidade byte a byte permanece pendente.
