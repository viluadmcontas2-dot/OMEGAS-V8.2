# Bloco 2 — Aprendizado, cobertura e comparação

## Status
Contrato técnico preparado. Este documento não autoriza implementação, merge, release, publicação, Netlify, instalação, escrita na ECU ou alteração da `main`.

## Origem e destino
- Origem: estado atual da branch `rebuild/ux-9in-dual-layout` após o Bloco 1.
- Destino observável: uma área de Aprendizado que explique, sem ambiguidade, o que já foi aprendido, onde há cobertura, onde faltam dados e quais comparações gasolina × GNV são válidas na época atual.

## Resultado observável
Ao abrir Aprendizado, o usuário deve conseguir responder:
1. Quais regiões do mapa possuem evidência suficiente?
2. Quais regiões têm apenas gasolina, apenas GNV ou ambos?
3. Quais células estão prontas para comparação?
4. Por que uma célula ainda não está pronta?
5. A evidência pertence à época atual do Mapa K?
6. O que mudou entre gasolina e GNV sem transformar isso automaticamente em escrita?

## Escopo autorizado somente após aprovação de implementação
- visualização de cobertura na grade física do Mapa K;
- distinção clara entre gasolina, GNV e região comparável;
- contagem e qualidade das amostras por célula/região;
- explicação de prontidão e insuficiência;
- separação por época do Mapa K;
- exclusão de evidência antiga das decisões da época atual, sem apagar o histórico;
- preservação do contexto ao abrir uma região;
- testes de consequências do aprendizado;
- documentação e incidente quando houver defeito confirmado.

## Fora de escopo
- alterar matemática central do aprendizado;
- alterar protocolo MP48, USB, ACK ou readback;
- alterar escrita do Mapa K;
- reconstruir Curva K;
- aceitar ou aplicar sugestões automaticamente;
- alterar OBD;
- migrar persistência sem contrato separado;
- refatoração geral da interface;
- merge, release, publicação ou Netlify.

## Invariantes
- nenhuma escrita automática na ECU;
- abrir Aprendizado, tocar célula, receber sugestão ou mudar época nunca inicia escrita;
- OBD permanece observacional;
- Curva K e Mapa K mantêm responsabilidades separadas;
- gasolina é preservada como referência;
- UI apenas apresenta dados produzidos pelo núcleo Kotlin;
- linha técnica do Mapa K permanece protegida;
- histórico antigo pode ser consultado, mas não pode contaminar decisão da época atual;
- mesma autoridade de estado deve controlar seleção, detalhe, filtros e renderização.

## Áreas e consumidores a inspecionar antes da implementação
- produtores Kotlin de regiões, células projetadas, comparações, épocas e sugestões;
- exportação/importação `.omegas` relacionada ao aprendizado;
- WebView e adaptador nativo;
- tela atual de Aprendizado e seus consumidores JavaScript;
- `LearningGridProjection`;
- `AssistedCalibrationAdvisor`;
- persistência das épocas e relação com alterações do Mapa K;
- workflows e testes Android aplicáveis.

## Critérios de aceite
### Cobertura
- grade física mantém 12 × 12 células visuais;
- célula sem evidência não aparece como pronta;
- gasolina, GNV e comparável usam estados diferentes e explicados;
- cobertura não é confundida com sugestão ou autorização de escrita.

### Prontidão
- o motivo de não prontidão é mostrado em linguagem simples;
- quantidade, qualidade, estabilidade, sessões/visitas e época são consideradas conforme o contrato existente do núcleo;
- a interface não inventa limites próprios.

### Épocas
- após alteração confirmada do Mapa K, nova evidência GNV pertence à nova época;
- evidência GNV de época antiga não influencia sugestão da época atual;
- referência de gasolina continua preservada;
- histórico antigo permanece acessível como histórico, não como decisão atual.

### Comparação
- comparação só ocorre quando gasolina e GNV são compatíveis na mesma região;
- ordem dos dados não altera a decisão;
- valores inválidos são ignorados de forma auditável;
- tendência global e erro residual local permanecem separados;
- nenhuma comparação inicia escrita.

### Interface
- tocar uma célula abre detalhe visível sem trocar para uma área escondida;
- seleção e contexto são preservados ao voltar;
- horizontal 9" é a experiência principal;
- celular vertical continua funcional e concentrado;
- não criar shell, timer ou autoridade concorrente.

## Testes obrigatórios
1. célula vazia, apenas gasolina, apenas GNV e comparável;
2. preenchimento entre eixos conserva toda a evidência;
3. evidência insuficiente mostra motivo correto;
4. evidência antiga não contamina época atual;
5. gasolina permanece disponível após mudança de época;
6. ordem das comparações não muda decisões;
7. dados inválidos não geram prontidão ou sugestão;
8. erro global não vira correção residual local falsa;
9. anomalia local permanece local após remover tendência global;
10. abrir detalhe não escreve e não altera o Mapa K;
11. UI usa os eixos e valores produzidos pelo Kotlin;
12. estado e seleção sobrevivem à navegação e renderização;
13. gate rápido, testes Kotlin/JVM, lint e build Android;
14. APK e SHA-256 ligados ao commit final;
15. validação no celular e, quando envolver ECU, validação física separada.

## Riscos principais
- misturar histórico antigo com época atual;
- UI recalcular prontidão de forma diferente do Kotlin;
- cores sugerirem prontidão inexistente;
- confundir cobertura com recomendação de ajuste;
- perder referência de gasolina ao mudar época;
- introduzir segunda autoridade de seleção/renderização;
- alterar matemática enquanto se corrige apresentação.

## Rollback
- reverter apenas os commits do Bloco 2 na branch de trabalho;
- preservar integralmente o commit validado do Bloco 1 `411a7188f4b4858edb379d7bf7f037087c646709`;
- não migrar ou apagar dados sem plano específico de rollback.

## Evidência necessária para conclusão
- diff remoto restrito ao Bloco 2;
- testes focados e de consequências verdes;
- gate rápido verde;
- Kotlin/JVM, lint e assembleDebug verdes;
- artifact e SHA-256 do commit final;
- validação no celular;
- qualquer teste com USB/MP48/ECU declarado separadamente como validação física.
