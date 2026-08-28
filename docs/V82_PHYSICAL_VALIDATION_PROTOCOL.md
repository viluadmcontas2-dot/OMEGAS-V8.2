# OMEGAS V8.2 — Protocolo de validação física final

Status deste documento: **AGUARDANDO APK AUTORIZADO + CELULAR/VEÍCULO**.

Este protocolo é a Etapa 42 do Programa Mestre. Ele não autoriza build, instalação ou escrita automática na ECU. Serve para que a prova física futura seja repetível e não dependa de memória ou improviso.

## Pré-condições obrigatórias

1. APK da mesma árvore remota registrada, gerado somente após autorização específica.
2. SHA-256 do APK registrado antes da instalação.
3. Branch, commit e tree exatos registrados.
4. Export/backup do Learning atual antes do teste, sem limpar dados.
5. Logcat e métricas de serviço disponíveis para coleta.
6. Qualquer escrita de Mapa K, Curva K ou ação AutoCal continua dependendo de revisão humana e confirmação explícita.
7. AutoMatch manual continua inexistente; AutoMatch observado é nativo da ECU.

## Fase A — startup com passado acumulado

Objetivo: provar que o passado não torna a abertura proporcionalmente mais cara nem exige `Limpar dados`.

Procedimento:
- abrir com base de Learning já acumulada;
- registrar tempo até UI operacional e primeira telemetria válida;
- confirmar que `LEARNING_RESTORING` pode coexistir com telemetria atual;
- fechar/forçar parada e reabrir sem apagar dados;
- repetir após a sessão longa deste protocolo.

PASS quando:
- o app abre sem limpar dados;
- telemetria aparece antes do fim do restore pesado;
- restore não bloqueia navegação/estado mínimo;
- não ocorre OOM, loop de crash ou crescimento de tempo de abertura a cada nova reabertura.

## Fase B — sessão longa e tendência temporal

Executar progressivamente: aproximadamente 30 min, depois uma janela de horas quando possível, e finalmente reabrir após o veículo ficar parado.

Registrar em marcos regulares:
- idade/frescor da telemetria;
- backlog/pending visual;
- uso de memória do processo;
- GC e pausas longas;
- tamanho dos arquivos de Learning/sidecar/checkpoints;
- contagens de regiões, comparações, sessões, âncoras e proveniência;
- erros/reconnects USB;
- resposta a toque/navegação.

PASS quando:
- não existe tendência monotônica de atraso visual;
- não existe fila histórica crescente no caminho live;
- memória e arquivos respeitam budgets/compactação definidos;
- o app continua abrindo após a coleta acumulada;
- não surge necessidade de `Limpar dados` como rotina de recuperação.

## Fase C — telemetria + Learning + AutoCal simultâneos

Com motor em condições seguras:
- manter telemetria atual;
- manter Learning habilitado;
- observar AutoCal nativo no cockpit;
- confirmar que snapshots pesados são event-driven/manual, não por frame;
- observar maturidade das bandas GNV;
- verificar eventos `NATIVE_BAND_MATURED`.

Para cada maturidade:
- `CORRELATED` só pode existir com mesma sessão, GNV plausível e contexto físico confiável;
- âncora deve carregar epoch, sessão, RPM, Petrol Inj., Gas diagnóstico, MAP, sequência, instante e lag;
- `NO_RELIABLE_CORRELATION` deve permanecer sem posição inventada e sem NativeLearningAnchor.

## Fase D — AutoMatch real e epochs

- observar AutoMatch executado pela própria ECU;
- confirmar alteração nativa somente quando readback/estado real demonstrar mudança;
- confirmar criação de nova epoch quando a calibração efetivamente muda;
- gasolina de referência permanece preservada;
- evidência GNV antiga não volta a votar como atual;
- âncoras da epoch anterior não validam a superfície atual.

Não existe botão de AutoMatch manual no OMEGAS.

## Fase E — Predictor e live tracing

Na rota Predictor:
- confirmar atualização da célula `AGORA` com telemetria em movimento;
- confirmar no máximo os quatro pesos atuais vindos do Kotlin;
- desligar `Tracing ao vivo` e comprovar que o destaque visual para;
- manter Learning coletando com tracing OFF;
- religar tracing e comprovar que não aparece trilha histórica acumulada;
- tocar células VALIDADO/OBSERVADO/PREVISTO/DESCONHECIDO e conferir razão/proveniência;
- célula sem suporte continua sem previsão.

PASS quando:
- tracing visual não seleciona célula;
- tracing não chama writer;
- tracing OFF não interrompe Learning;
- Predictor não usa uma previsão para gerar confiança de outra previsão.

## Fase F — Mapa K e Curva K: escrita manual segura

Só executar se houver intenção humana real de calibrar.

Mapa K:
1. selecionar/preparar;
2. obter prévia Kotlin;
3. abrir revisão;
4. conferir `before/after`;
5. confirmar manualmente;
6. exigir ACK + readback;
7. conferir nova epoch/revalidação localizada.

Curva K:
1. confirmar leitura real dos 30 pontos;
2. distinguir atual da ECU de previsão OMEGAS;
3. revisar proposta;
4. confirmar manualmente;
5. exigir ACK + readback.

Cancelar em qualquer ponto anterior ao envio deve deixar a ECU intacta.

## Fase G — split-screen e flutuante

- redimensionar/entrar em multi-window durante telemetria;
- navegar Predictor → Mapa K → Curva K → Learning;
- verificar que seleção/contexto/revisão válidos sobrevivem ao reflow;
- verificar o flutuante observacional com RPM, Petrol, Gas, célula, frescor e estado ECU;
- recolher/abrir o flutuante.

PASS quando:
- nenhum segundo Store/Router/WebView/serviço nasce;
- resize só reorganiza layout;
- flutuante não possui ação de escrita;
- estado exibido é o mesmo estado atual do app.

## Fase H — reconexão e fail-closed

- desconectar/reconectar USB em condição controlada;
- confirmar nova sessão física;
- filas antigas devem encerrar deterministicamente;
- AutoCal não pode reaplicar enable silenciosamente;
- leitura real deve ser refeita quando necessário;
- uma sessão antiga nunca pode receber write/readback da nova sessão.

## Evidências mínimas a anexar ao checkpoint físico

- APK + SHA-256;
- branch/commit/tree;
- modelo do dispositivo/multimídia e Android;
- duração de cada fase;
- logcat dos marcos e de qualquer falha;
- métricas de memória/frescor/backlog/tamanho de arquivos;
- snapshots Learning/AutoCal antes e depois;
- recibos de ACK/readback de qualquer escrita manual;
- screenshots ou gravação curta do Predictor, split e flutuante;
- resultado por fase: PASS / FAIL / INCONCLUSIVE.

## Critério de encerramento V8.2

A V8.2 só recebe validação física final quando as Fases A–H relevantes estiverem documentadas e nenhuma delas depender de limpar dados, esconder atraso, usar previsão como ciência, criar writer paralelo ou pular confirmação humana.

Se a Etapa 41 continuar bloqueada por ambiente/build ou APK não estiver especificamente autorizado, este protocolo permanece **AGUARDANDO VALIDAÇÃO FÍSICA** e nenhum PASS físico pode ser declarado.
