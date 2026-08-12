# Estado oficial do OMEGAS V8

## Fotografia atual — 2026-08-11

### Confirmado agora no GitHub
- Repositório funcional oficial: `viluadmcontas-alt/OMEGAS-V8`.
- Branch deste bloco: `fix/learning-consolidation-20260811`.
- Origem congelada deste bloco: `ef97b4d80ef1e79f6f1ad67781ba4bf46fd0e8e5`, fotografia funcional que gerou o APK considerado pelo proprietário a melhor versão até então.
- **Origem histórica:** `felipetbestkkj-ship-it/OMEGAS-V7` permanece somente proveniência da migração; não é a fonte funcional viva do V8.
- SHA-256 do APK físico de origem: `f6e5ab701dbfcd16519ebbedfe3caf4ab9a867e018c786dc4531f1215921f8c6`.
- `main`, merge, PR, release, Netlify e publicação permanecem fora deste bloco.
- O CI desta branch é a única evidência automatizada válida para o código novo; resultados de candidatos anteriores são apenas histórico.
- Este commit documental congela a fotografia final para o Quality Gate; nenhum novo push de produto deve ocorrer enquanto esse gate estiver em execução.

## Produto e invariantes preservados
OMEGAS V8 continua sendo aplicativo Android para leitura, aprendizado, diagnóstico e ajuste manual assistido de centrais OMEGAS/MP48.

Permanecem invariantes:
- nenhuma escrita automática na ECU;
- gasolina é referência da equivalência;
- tendência global pertence à Curva K;
- residual local pertence ao Mapa K;
- OBD é observacional;
- checkpoint/backup, ACK e readback são obrigatórios;
- falha de ACK/readback nunca é sucesso;
- linha técnica do Mapa K permanece protegida;
- UI não recalcula matemática crítica;
- sugestão, timer, conexão, leitura ou OBD nunca chama writer;
- uma Store, um Router e um Scheduler continuam sendo a autoridade da UI.

### Contratos clean-slate preservados
- **UI clean-slate** permanece a constituição visual ativa; este bloco evolui a mesma autoridade, sem segunda shell.
- **modo oficina removido** continua removido: segurança não depende de chave visual ou modo especial da WebView.
- **gate nativo** continua sendo a autoridade Kotlin para decidir se uma mutação manual pode começar.
- **condução provável** continua sendo uma proteção preventiva por RPM; não é apresentada como velocidade confirmada e não foi alterada neste bloco.

## Problema físico deste bloco
O proprietário confirmou que a versão `ef97b4d…` é a melhor até agora, porém o aprendizado ainda é instável: uma célula aparentemente consolidada pode mudar com pouca evidência nova.

Incidente material:
`docs/incidents/2026-08-11-consolidated-learning-volatility.md`.

### Causa confirmada
- Regiões agregadas guardavam vários `visitId` junto de uma média regional atual.
- A integração V7 reapresentava cada `visitId` usando essa média atual.
- O runtime aceitava substituir a evidência de um mesmo `visitId` por fotografia posterior/maior qualidade.
- Assim, visita histórica podia ser reinterpretada depois.
- Comparações V7 usavam o horário da recomputação em vez do timestamp físico da visita.
- O advisor contínuo era intencionalmente muito responsivo e não existia uma autoridade separada para memória consolidada × evidência recente.

## Estabilização científica — APLICADA NA BRANCH, GATE FINAL EM EXECUÇÃO

### Visita física imutável
`visitId` passa a ser a unidade física independente.
Depois do primeiro registro, RPM, MAP, Petrol Inj., qualidade e timestamp daquela visita não podem ser reescritos por outra média regional.

A limitação histórica é explícita: snapshots antigos possuem IDs de visita e média agregada, mas não preservam todos os valores individuais originais. O aplicativo não inventa retroativamente esses valores; o primeiro registro V7 conhecido é congelado dali em diante.

### Comparação física imutável
Quando uma visita GNV ganha sua primeira comparação válida com uma referência de gasolina compatível, essa comparação passa a ser evidência histórica imutável da visita.

Nova evidência de gasolina pode permitir a primeira comparação de uma visita GNV ainda pendente, mas não reescreve retroativamente `petrolTargetMs`, erro, direção ou qualidade de uma comparação já formada.

### Cronologia determinística
`V7EquivalenceEngine` usa `cng.collectedAtMs` como timestamp da comparação.
Reprocessar ou reiniciar a sessão não deve mudar a ordem científica das evidências.

### Estados da memória
Criado `LearningStabilityV7`:
- `NO_EVIDENCE` — sem evidência suficiente;
- `LEARNING` — formando a primeira memória;
- `CONSOLIDATED` — memória repetível e operacional;
- `REVALIDATING` — tendência recente diferente sendo verificada sem substituir ainda o consolidado.

A promoção/revalidação reutiliza critérios já existentes do núcleo: `confirmedVisits`, consenso de direção, MAD e deadband. RPM/MAP/temperatura/equivalência não foram afrouxados nem trocados.

### Histerese
- Evidência compatível reforça o consolidado.
- Um outlier isolado não desloca o consolidado.
- Evidência contraditória entra como candidato recente em `REVALIDATING`.
- Se a tendência contraditória também se torna repetível, ela pode promover uma nova geração consolidada.
- A mesma coleção de visitas deve produzir o mesmo resultado independentemente da ordem de entrada.
- A interpolação bilinear continua repartindo influência, mas não transforma uma visita em quatro visitas independentes.

## Sugestões — estabilizadas por geração
A fila persistente permanece `PENDING / OBSERVING / APPLIED / SUPERSEDED`.

Novo contrato:
- sugestão local só é liberada quando sua célula possui memória `CONSOLIDATED`;
- dentro da mesma geração consolidada, a magnitude fica congelada e não acompanha cada oscilação do advisor;
- durante `REVALIDATING`, a sugestão permanece visível, mas vira `OBSERVING` e não é acionável;
- uma nova geração consolidada pode atualizar a magnitude;
- aplicação continua manual e passa pelo mesmo editor/writer oficial.

## Curva K global — cobertura mais forte
A Curva K global continua separada do Mapa K.

Além de consolidação, proposta global madura agora exige cobertura em mais de uma condição de RPM e MAP. Uma condição física localizada pode alimentar aprendizado local, mas não deve mudar uma faixa global inteira da Curva K.

Nenhuma fórmula de equivalência foi relaxada para cumprir esta regra.

## Persistência
O snapshot V7 evoluiu para `OMEGAS_V7_SESSION_6`.

- lê schemas 2–5;
- persiste geração/estado/erro consolidado/erro recente das sugestões;
- não apaga snapshot anterior;
- não exige migração destrutiva;
- reinício deve reconstruir o mesmo estado científico a partir das visitas/comparações persistidas.

## Desempenho da projeção consolidada
A projeção `learningStability` é calculada somente para células/pontos que possuem comparação ativa.

Além disso, `LearningStabilityJsonV7` mantém cache fraco por runtime e invalida somente quando muda revisão ou identidade/quantidade das comparações. Assim, leituras contextuais repetidas da WebView não recalculam a grade consolidada apenas porque a telemetria ao vivo mudou.

A auditoria do consumidor confirmou que `ingestLearningSnapshot()` é uma operação explícita da ponte V7 e não aparece no hot path de `native-api.js`. Por isso não foi feita uma reescrita arriscada do runtime grande apenas para otimizar duplicatas sem evidência de custo material.

## UI Aprendizado — autoridade visual do consolidado
A grade continua usando os mesmos eixos físicos RPM × Petrol Inj.

Na camada Comparação:
- antes do consolidado, mostra aprendizado em formação;
- quando existe `CONSOLIDATED`, o número principal é o erro consolidado;
- se surgirem dados contraditórios, a célula mantém o número consolidado e mostra `revalidando`;
- o valor recente aparece somente no detalhe da célula junto de número de visitas recentes e decisão do Kotlin.

A célula continua podendo abrir diretamente o editor oficial do Mapa K. Abrir/revisar nunca escreve automaticamente.

## Live Tracing — decisão atual
**Live Tracing visual permanece removido.**

A interpolação bilinear/trilinear continua no Kotlin para aprendizado e projeção científica, porém a WebView não pinta halo, trail ou quatro pesos em tempo real.

O caminho rápido mostra apenas:
`RPM + Petrol Inj. + célula atual`.

Essa é a regra atual para preservar fluidez na multimídia de 9".

## UI/OBD/Mapa K/Curva K preservados
Permanecem da melhor versão aprovada:
- UI clean-slate;
- edição da célula a partir do Aprendizado usando o editor oficial do Mapa K;
- seleção de 1–144 células por intenção humana e seleção por faixa quando aplicável;
- Curva K manual com prévia Kotlin;
- OBD observacional RPM × Petrol Inj.;
- flutuante/serviço em segundo plano sem alteração neste bloco;
- writers de Mapa K/Curva K e segurança de ACK/readback sem alteração.

## Evidência automatizada deste bloco
Um gate intermediário da branch em `a9749e1973519ee96a3a54b849f5be9717c7aeae` teve:
- fast gate: **success**;
- produção Kotlin: compilou;
- Android unit tests: **failure**, 6 testes antigos ainda exigindo semântica volátil (`mesma visita pode ser substituída`, `sugestão muda magnitude a cada refresh`, writer sem setup consolidado);
- lint: não executado nessa rodada porque testes falharam.

Um gate posterior em `87e61212b258229ca39f20530874efced6d1226a` falhou somente na governança documental porque esta fotografia havia omitido expressões literais obrigatórias (`origem histórica`, `modo oficina removido` e contratos clean-slate correlatos). Este documento restaura explicitamente esses contratos; esse FAIL não prova defeito de produção.

O gate `31513304696` do commit `ca5f87205ffb2659581874c00910137e0f28b3f2` passou governança e falhou somente porque a primeira versão do novo contrato estático procurava um marcador textual inexistente. Esse marcador já foi substituído por uma expressão Kotlin real; Android foi pulado naquele run.

Os contratos antigos de lógica foram migrados para a nova especificação sem remover as provas de writer/checkpoint/readback.

**O estado automatizado final permanece PENDENTE até existir recibo verde desta fotografia congelada.** Não reutilizar gate verde de outro commit como prova deste bloco.

## Testes novos/migrados deste bloco
Protegem, entre outros:
- visita histórica imutável;
- comparação já formada imutável mesmo após nova gasolina;
- visita GNV pendente pode ganhar sua primeira comparação quando chega referência compatível;
- consolidação após evidência repetível;
- outlier isolado → `REVALIDATING` sem mover consolidado;
- tendência contraditória persistente → nova geração;
- invariância à ordem;
- conservação do peso bilinear como uma visita física;
- mais de 600 visitas não derrubam a memória consolidada;
- cobertura localizada × global;
- persistência schema 6 + leitura schema 5;
- sugestão congelada na mesma geração e bloqueada durante revalidação;
- advisor só alcança writer após consolidação;
- cache contextual invalida somente quando nasce comparação/revisão nova;
- fluxo de writer/checkpoint/ACK/readback continua separado.

## Estado físico
**AGUARDANDO VEÍCULO.**

Mesmo com gate automatizado verde, ainda será necessário validar no aparelho/veículo:
- célula consolidada realmente não oscila por passagem casual;
- mudança real entra em `REVALIDATING` e eventualmente consolida sem atraso excessivo;
- aprendizado continua aparecendo em segundos;
- fila de aprendizado permanece curta em sessão prolongada;
- CPU/RAM da multimídia não degradam progressivamente;
- reinício mantém o mesmo consolidado;
- sugestões reais permanecem estáveis e adaptam somente após nova consolidação;
- comportamento após escrita física e nova época.

## Notion
Escopo auditável deste bloco:
`OMEGAS V8 — estabilização científica do aprendizado — execução integral`.

O Blueprint CUSTOMROM continua somente leitura e não foi alterado.

## Próxima leitura obrigatória
Antes de qualquer conclusão ou nova mudança, confirmar no GitHub a branch, o head atual, o recibo de CI e artifacts. Documento histórico não substitui evidência viva.
