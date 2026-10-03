# Incidente — live tracing sobrecarrega a WebView na multimídia

Data: 2026-08-09  
Branch: `refactor/ui-clean-slate-1280x720`  
Base funcional preservada: `5aa62c93351e98c8ef14b37ab14beb929ff52e32`

## Sintoma e impacto
Em validação física do proprietário, a tela **Agora** manteve telemetria fluida em tempo real, mas a tela **Aprender** ficou muito lenta quando o live tracing estava visível. A navegação restante permanecia fluida.

Efeito observado:
- telemetria visual atrasada na tela Aprender;
- sensação de congelamento/lag ao acompanhar células;
- demora para refletir evidência nas células;
- uso desproporcional de CPU/memória da WebView em uma multimídia de baixo desempenho.

## Causa imediata confirmada no código
O tracing antigo executava a cada ciclo rápido e mantinha trabalho puramente visual:
- limpava contribuidores anteriores;
- criava conjunto do frame atual;
- mantinha `traceTrail` com até 16 células;
- chamava `Date.now()`;
- recalculava fade por idade;
- percorria o histórico visual;
- ordenava entradas para descartar a mais antiga;
- alterava classes e propriedades CSS continuamente.

Esse trabalho não participa da equivalência, aprendizado, persistência ou sugestão. Era apenas apresentação.

## Causa estrutural
A UI tratava live tracing como animação contínua da grade, em vez de marcador de posição de baixo custo. Assim, uma função visual concorria com coleta, bridge WebView e renderização de evidência numa central multimídia que já executa processos do veículo.

## Por que os testes anteriores não detectaram
Os contratos anteriores verificavam que:
- existia um único Scheduler;
- tracing não sobrescrevia o valor aprendido da célula;
- não havia MutationObserver;
- dados físicos vinham do Kotlin.

Eles não limitavam:
- quantidade de células afetadas por frame;
- existência de trail temporal;
- `Date.now()`/fade/sort no caminho rápido;
- repintura causada por pequenas variações de peso;
- mutações DOM quando dois frames visuais eram equivalentes.

## Correção aplicada
O novo `PhysicalGrid`:
- mantém somente o conjunto atual de contribuidores;
- limita a quatro contribuintes bilineares;
- quantiza peso visual em quatro níveis;
- não mantém trail;
- não usa relógio/fade/sort;
- não pulsa ou anima células;
- compara frame anterior e atual;
- frame visual idêntico produz zero mutações DOM;
- somente células que entram, saem ou mudam materialmente são tocadas;
- dados aprendidos continuam em nós separados e protegidos por assinatura visual.

O `app.js` também:
- busca telemetria rápida apenas em Agora/Aprender;
- não publica `tick` global na Store;
- cria a tela Aprender sob demanda;
- não cria a grade 12×12 no boot se Aprender nunca for aberta;
- mantém full render da evidência fora do ciclo rápido;
- só consulta evidência técnica da Curva K quando o disclosure está aberto.

## Teste de regressão
`tests/ui/live-tracing-budget.test.cjs` prova:
- ausência de `traceTrail`, `Date.now()`, `live-trail` e sort no tracing;
- máximo de quatro contribuintes;
- frame idêntico = zero mutações;
- variação pequena dentro do mesmo bucket visual = zero mutações.

`tests/test_clean_ui_contract.py` impede retorno de timers paralelos, animações contínuas, blur/filtros e estado `tick` global.

## O que não mudou
- frequência de coleta nativa;
- equivalência RPM + MAP;
- referência gasolina;
- `Petrol Inj.` observado no GNV;
- aprendizado Kotlin;
- persistência;
- USB/MP48;
- OBD nativo;
- writers, checkpoint, ACK e readback.

## Risco residual
A redução de custo é comprovável estruturalmente no código e por contratos, mas o ganho real de CPU/latência ainda precisa ser medido na multimídia 1280×720. Até esse teste, o estado é **aguardando validação na multimídia/veículo**.
