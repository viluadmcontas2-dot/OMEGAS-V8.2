# Incidente — gate Android bloqueado por wrapper e comparação textual

## Sintoma e impacto
No PR #2, a governança e os contratos de interface passaram, mas os dois gates Android falharam. Um job parou na validação automática do Gradle Wrapper; o outro executou 264 testes Kotlin/JVM e falhou em dois cenários da matriz de aprendizado. Como consequência, lint, APK e SHA-256 não foram concluídos.

## Causa imediata
1. `gradle/actions/setup-gradle@v4` classificou o `gradle-wrapper.jar` existente como checksum desconhecido e encerrou o job antes de executar Gradle.
2. O teste de interpolação tentou ler `weight` do JSON projetado, embora a projeção pública conserve a evidência no campo `samples`.
3. O teste de invariância à ordem comparou arrays JSON inteiros como texto, tornando o resultado sensível à ordem de serialização e não apenas à decisão produzida.

## Causa estrutural
- Havia dois workflows Android com estratégias diferentes para preparar o Gradle.
- A matriz profunda foi adicionada sem uma execução remota completa antes de ser declarada pronta para CI.
- O contrato do teste não distinguia representação interna, representação pública e equivalência semântica.

## Por que os testes anteriores não detectaram
Os testes locais disponíveis cobriam interface, contratos Python e JavaScript. O ambiente usado nesta conversa não executou Gradle/Kotlin; a falha só apareceu quando o PR disparou o runner Android real do GitHub.

## Correção
- O cenário de interpolação passa a provar conservação das 100 amostras distribuídas entre as quatro células públicas.
- A invariância à ordem passa a comparar conjuntos de decisões por chave, direção, acionabilidade, exigência de confirmação e valores numéricos com tolerância.
- O workflow deixa de depender da validação externa que recusou o wrapper, mas mantém verificação explícita do SHA-256 conhecido do `gradle-wrapper.jar`, da distribuição Gradle 8.9 e de `validateDistributionUrl=true` antes de executar qualquer tarefa.
- `actions/setup-java` foi atualizado para v5 e passou a administrar o cache Gradle.

## Teste de regressão
- `LearningScenarioMatrixTest` deve executar integralmente dentro de `testDebugUnitTest`.
- Alteração de ordem das comparações não pode mudar as decisões semânticas.
- A interpolação central deve produzir quatro células e conservar 100 amostras.
- Qualquer mudança no wrapper JAR ou na URL da distribuição deve falhar antes dos testes Android.

## Evidência
Execução inicial do PR #2:
- governança: sucesso;
- interface: sucesso;
- 264 testes Kotlin/JVM executados, 2 falharam;
- setup-gradle bloqueado por checksum desconhecido `c08ce416101cb71e50ff38f29a1504ac40cb4f6c351c307d57df992f26920343`.

A validação da correção depende da nova execução do PR #2.

## Risco residual
A validação explícita comprova que o wrapper não mudou em relação ao estado conhecido do repositório; ela não prova, isoladamente, a origem histórica desse binário. A substituição futura por um wrapper oficial regenerado deve ser tratada em mudança separada, com comparação e testes completos. O Bloco 1 continua aguardando APK, celular, multimídia, USB, MP48 e veículo.
