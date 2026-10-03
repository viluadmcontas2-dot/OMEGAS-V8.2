# OMEGAS V7 — OBD completo, segundo plano e telemetria flutuante

Data: 2026-08-08
Branch: `feature/ux-didactic-expansion`
Status desta fotografia: implementação remota; validação automatizada e física devem ser registradas separadamente.

## Objetivo

Fechar a área OBD como uma segunda prova independente da MP48, sem transformar OBD em autoridade de calibração e sem introduzir writers paralelos.

## Autoridades

- MP48: telemetria/calibração/aprendizado principal.
- OBD/ELM327: observação independente da ECU original.
- Android: permissões, foreground service, bateria e overlay.
- UI: apresentação e interação; não recalcula correção nem escreve ECU.

## Conexão OBD

Fluxo local:

`Bluetooth → dispositivos pareados → escolher ELM → conectar → descobrir PIDs → ler ao vivo → acumular evidência`

A lista de dispositivos é recolhível. Desconectado, fica aberta para escolha. Conectado, recolhe e mostra o adaptador ativo; “Trocar dispositivo” reabre a lista.

Omegas Link permanece como fonte OBD remota separada. `off` desativa apenas OBD.

## PIDs padrão

O coletor primeiro descobre o bitmap de suporte anunciado pela ECU e evita pressupor que todos os carros suportam os mesmos PIDs.

Principais leituras:

- 0103 — estado do sistema de combustível / closed loop;
- 0104 — carga calculada;
- 0105 — temperatura do líquido de arrefecimento;
- 0106 — STFT;
- 0107 — LTFT;
- 010B — MAP;
- 010C — RPM;
- 010D — velocidade;
- 010F — temperatura do ar de admissão;
- 0110 — MAF;
- 0111 — borboleta;
- 012F — nível do tanque de gasolina em percentual, somente quando anunciado;
- 0142 — tensão do módulo.

PID ausente deve ser apresentado como não suportado, nunca estimado como se fosse leitura real.

## Mapas OBD próprios

A prova independente possui células próprias:

- eixo X: RPM OBD;
- eixo Y: carga calculada OBD;
- grade: 12 × 12;
- camadas: Gasolina, GNV e Comparação.

Cada célula pode manter:

- STFT;
- LTFT;
- velocidade;
- temperatura;
- MAP;
- MAF;
- borboleta;
- número de amostras;
- timestamp.

A comparação usa a mesma célula OBD e apresenta `STFT GNV − STFT gasolina`, calculado no Kotlin.

O mapa declara explicitamente:

- `source = OBD_ONLY`;
- `observationalOnly = true`;
- `affectsLearning = false`;
- `affectsCalibration = false`.

## Live tracing OBD

A célula atual é localizada no Kotlin por RPM OBD × carga OBD independentemente da decisão de aceitar a amostra.

Assim:

- a UI continua mostrando onde o motor está mesmo em open loop, motor frio ou evidência ainda recusada;
- aceitar uma amostra e localizar a célula são decisões separadas;
- o live tracing mostra célula, RPM, carga, STFT, LTFT, MAP, MAF, combustível e frescor;
- a célula atual recebe destaque visual na grade.

## Condição mínima da evidência acumulada

Para uma amostra entrar no mapa OBD independente:

- célula RPM/carga válida;
- rótulo observacional de combustível disponível;
- closed loop;
- STFT válido;
- se a temperatura estiver disponível, motor acima do mínimo configurado.

A prova OBD não habilita aprendizado MP48 quando MP48 está ausente. Um rótulo manual apenas classifica observação OBD.

## Persistência

O mapa OBD independente é persistido junto ao estado OBD. Gasolina/GNV e épocas históricas da correlação existente permanecem separadas.

Nenhuma importação OBD escreve na ECU.

## Segundo plano

O serviço contínuo permanece um único `TelemetryForegroundService`, `START_STICKY`, classificado como `connectedDevice` e `location` somente quando GPS está ativo.

O app declara `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` e, na primeira execução em Android compatível, abre a confirmação oficial do Android uma única vez. Negar não cria loop. A interface mantém botão manual para abrir novamente a autorização.

## Telemetria flutuante

Overlay opcional com `SYSTEM_ALERT_WINDOW`.

Fluxo:

`Autorizar flutuante → Android “Exibir sobre outros apps” → retornar → serviço ativa se autorizado`

O flutuante:

- é arrastável;
- começa recolhido como botão Ω;
- expande ao toque;
- mostra somente célula OBD atual, STFT, Petrol Inj. e RPM;
- atualiza pelo mesmo serviço, sem novo scheduler/timer;
- limita redesenho a 250 ms;
- não possui referência a writer, mapa K, curva K ou USB writer.

## Invariantes preservados

- nenhuma escrita automática na ECU;
- OBD não escreve Mapa K nem Curva K;
- overlay é leitura somente;
- sugestão/conexão/timer não inicia escrita;
- writers continuam Kotlin e dependem de revisão, confirmação, checkpoint, ACK e readback;
- UI não vira autoridade de matemática crítica;
- apenas um scheduler visual.

## Validação automatizada obrigatória

1. `python -B tools/run_checks.py`;
2. `testDebugUnitTest`;
3. `lintDebug`;
4. `assembleDebug` para o APK autorizado;
5. checksum SHA-256 do APK.

Regressões específicas devem provar:

- mapa OBD usa RPM/carga, não Petrol Inj.;
- célula live existe mesmo quando a amostra não conta;
- amostra recusada não contamina mapa;
- comparação exige gasolina/GNV na mesma célula;
- ausência de writers na superfície OBD e overlay;
- listas de dispositivos e PIDs permanecem recolhíveis;
- bateria usa o prompt oficial do Android;
- overlay usa a permissão oficial e permanece opcional;
- nenhum timer/scheduler paralelo.

## Validação física obrigatória

No S23/multimídia/veículo:

1. prompt de bateria e retorno ao app;
2. execução com tela apagada;
3. perda e retorno do USB;
4. ausência de pedidos USB espúrios para dispositivos alheios;
5. conexão ELM real e descoberta de PIDs;
6. PID 012F: confirmar suporte ou “não suportado”;
7. live tracing RPM/carga/STFT durante rodagem;
8. mapas Gasolina/GNV/Comparação acumulando células corretas;
9. overlay sobre outro app, toque, arraste e atualização;
10. 1024×600: touch, scroll e densidade;
11. Omegas Link OBD remoto;
12. estabilidade térmica/CPU/bateria em sessão longa.

Até essa etapa: `AGUARDANDO CELULAR / VEÍCULO`.
