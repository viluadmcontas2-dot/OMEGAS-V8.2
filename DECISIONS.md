# Decisões vigentes — OMEGAS V8

1. `viluadmcontas-alt/OMEGAS-V8` é o repositório funcional e de trabalho oficial atual. `felipetbestkkj-ship-it/OMEGAS-V7` é apenas origem histórica/proveniência da migração.
2. Somente a branch expressamente indicada pelo proprietário pode servir de origem para diagnóstico, alteração, teste, APK e conclusão técnica.
3. Estado mutável deve ser confirmado no GitHub vivo. Documento, Notion, memória ou repositório histórico não substituem branch/commit/CI atuais.
4. A governança é global e deve existir na `main`, sendo herdada por toda branch futura após promoção autorizada.
5. Multimídia/tablet 9" horizontal é o modo principal; celular vertical é modo rápido.
6. Nenhuma escrita automática na ECU.
7. Merge, release, publicação, Netlify, APK de entrega e produção exigem autorização separada.
8. A navegação principal permanece `Dashboard | Aprendizado | Mapa K | Curva K | OBD`; ferramentas secundárias e sugestões não criam uma sexta área principal.
9. A UI clean-slate existente é a constituição visual de base. Evolução adiciona clareza, diagnóstico e ação contextual sem segunda shell.
10. O método CUSTOMROM do Notion é obrigatório para novas melhorias de UI/UX como referência de **processo**, não de visual: intenção humana primeiro, complexidade sob demanda, feedback imediato, estado claro, segurança contextual e uma autoridade por fluxo.
11. Backend/Kotlin/USB/writers comprovados são preservados. Não se refatora motor ou protocolo por conveniência estética.
12. O frontend ativo possui uma Store, um Router e um Scheduler. Não são permitidos shells empilhados, timers concorrentes, `MutationObserver` reorganizando a UI ou interceptadores que criem outra autoridade.
13. `CalibrationWriteSafetyPolicy` é a política comum para decidir se uma mutação manual pode começar. Superfícies mutáveis conhecidas devem consultar essa mesma autoridade.
14. A condição segura para iniciar mutação exige serviço ativo, USB conectado, permissão resolvida, engine rodando/pronta/não travada, telemetria direta fresca e RPM abaixo de 1200.
15. O botão explícito `Gravar ... na ECU`, após revisão, é a confirmação humana. Não existe checkbox redundante de confirmação.
16. Sugestão nunca é writer. `applySuggestion` legado é somente compatibilidade de revisão/preparo e não pode chegar diretamente à ECU.
17. Curva K global: `sugestão → Preparar sugestão → prévia Kotlin → revisão → confirmação → writer → ACK → readback`.
18. O usuário pode selecionar de **1 a 144 células graváveis do Mapa K** numa única intenção humana. A linha técnica permanece protegida.
19. O writer do Mapa K pode manter blocos internos de até 16 células como detalhe nativo de segurança/protocolo. A UI não divide lotes nem coordena transações internas.
20. A segurança deve ser reavaliada entre blocos internos de uma intenção grande. Uma falha interrompe a sequência.
21. Uma intenção de Mapa K só termina como sucesso quando todas as células forem confirmadas. Escrita parcial é `BATCH_PARTIAL_FAILED`, nunca sucesso.
22. Curva K e Mapa K continuam separados: tendência global pertence à Curva K; resíduo/local pertence ao Mapa K.
23. Aprendizado explicável deve mostrar a decisão que o Kotlin já tomou — estado, motivo, progresso, combustível, qualidade e limites — sem duplicar critérios de aceitação no JavaScript.
24. **Live Tracing visual está removido por decisão do proprietário para preservar fluidez na multimídia.** A interpolação bilinear/trilinear continua no Kotlin; a UI rápida mostra somente RPM + Petrol Inj. + célula atual, sem perseguir halos, pesos ou células no DOM.
25. OBD possui fontes `local`, `remote` e `off` e continua observacional. Combustível manual sem MP48 é apenas rótulo e não habilita calibração.
26. `TelemetryForegroundService` representa comunicação contínua com dispositivo conectado. `connectedDevice` é permanente e `location` só entra quando GPS está ativo; `dataSync` não deve voltar ao serviço contínuo.
27. Segundo plano só pode ser declarado validado após teste real no aparelho.
28. Logs e sessões usam `SessionRecorder`/`RingLog`; a UI não cria logger paralelo.
29. `MODULE_VERSION 0x0173` orienta a forma dos quatro vetores AutoCal dinâmicos: versão 4 → 30; versões conhecidas diferentes → 18. Contadores `0x015B/0x015C` permanecem 18×U16.
30. Respostas `0xCA` preservam status/payload/frame bruto. `CA 01 08` é retryable conforme corpus observado; `CA 01 10` é non-retryable; CA desconhecido não ganha retry por suposição.
31. Um status non-retryable no handshake não entra em laço de tentativa cega; nova tentativa exige nova sessão USB física.
32. AutoCal nativo mutável continua manual, preparado e confirmado em diálogo Android. Também deve respeitar a mesma política comum de condição segura.
33. Identificadores persistentes como `applicationId`, formatos de aprendizado e schemas não são renomeados apenas por branding V8. Mudança exige migração explícita e rollback.
34. Rótulos, governança, documentação e produto visível devem identificar OMEGAS V8 quando isso não quebra compatibilidade.
35. Preparar, visualizar, ler, conectar ou receber sugestão nunca autoriza escrita.
36. CI verde prova apenas o que os testes exercitaram; Android/USB/ECU/OBD continuam aguardando validação física quando aplicável.
37. **Acelerar aprendizado não autoriza afrouxar equivalência.** RPM + MAP e demais critérios físicos continuam sendo decididos pelo núcleo; desempenho é otimizado removendo trabalho/I/O inútil, não aceitando condições piores.
38. Arquivo que representa somente a fotografia mais recente pode usar persistência assíncrona coalescida. Logs, eventos e amostras físicas que representam evidência independente não podem ser descartados sob essa regra.
39. Na grade de Aprendizado, Gasolina e GNV devem priorizar o `Petrol Inj.` médio real da região; contagem, confiança, RPM e MAP ficam como contexto. Comparação deve permitir entender `referência → observado` sem exigir interpretação de um percentual isolado.
40. O contexto ao vivo da grade é somente textual e leve. Pesos bilineares continuam disponíveis ao núcleo Kotlin para cálculo, mas não são desenhados, animados nem usados para substituir o conteúdo aprendido da célula.
41. **`visitId` é evidência física imutável.** Depois de registrada, a mesma visita não pode receber novos RPM, MAP, Petrol Inj., qualidade ou timestamp por causa de uma média regional atualizada.
42. Comparações da sessão V7 usam o timestamp físico da visita GNV; reinício/reprocessamento não pode mudar a cronologia científica.
43. O aprendizado distingue explicitamente `NO_EVIDENCE`, `LEARNING`, `CONSOLIDATED` e `REVALIDATING`.
44. `CONSOLIDATED` é a memória operacional estável. Nova evidência compatível reforça essa memória; um outlier isolado não desloca seu valor.
45. Evidência contraditória entra primeiro em `REVALIDATING`. O consolidado só muda quando a tendência recente também satisfaz repetibilidade, consenso e dispersão exigidos pelo núcleo; uma mudança real não fica congelada eternamente.
46. Consolidação/revalidação reutilizam os parâmetros científicos já vigentes (`confirmedVisits`, consenso, MAD e deadband). Esta decisão não afrouxa equivalência RPM/MAP/temperatura.
47. A mesma coleção de visitas deve produzir o mesmo consolidado independentemente da ordem de entrada; peso bilinear reparte influência sem criar visitas independentes artificiais.
48. Sugestão persistente depende do consolidado: dentro da mesma geração sua magnitude fica estável; durante `REVALIDATING` permanece visível, porém não acionável; uma nova geração consolidada pode atualizar a magnitude.
49. Curva K global exige tendência consolidada com cobertura em mais de uma condição de RPM e MAP. Evidência localizada continua pertencendo ao Mapa K residual.
50. O schema de sessão evolui de forma retrocompatível. Formato novo pode ler snapshots anteriores; atualização não apaga nem reinterpreta destrutivamente aprendizado antigo sem migração explícita/rollback.
51. Na tela Aprendizado, quando existir memória consolidada, esse valor é a autoridade visual principal. A estimativa recente contraditória aparece como `Revalidando` no detalhe e não faz a célula consolidada oscilar visualmente.
