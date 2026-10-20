# OBD: escopo de runtime e evidência independente

> Fundido em 2026-10-20 de `OBD_COMPLETE_RUNTIME_SCOPE.md` e `OBD_INDEPENDENT_EVIDENCE.md`; texto preservado.

---

## OMEGAS V7 — OBD completo, segundo plano e telemetria flutuante

Data: 2026-08-08
Branch: `feature/ux-didactic-expansion`
Status desta fotografia: implementação remota; validação automatizada e física devem ser registradas separadamente.

### Objetivo

Fechar a área OBD como uma segunda prova independente da MP48, sem transformar OBD em autoridade de calibração e sem introduzir writers paralelos.

### Autoridades

- MP48: telemetria/calibração/aprendizado principal.
- OBD/ELM327: observação independente da ECU original.
- Android: permissões, foreground service, bateria e overlay.
- UI: apresentação e interação; não recalcula correção nem escreve ECU.

### Conexão OBD

Fluxo local:

`Bluetooth → dispositivos pareados → escolher ELM → conectar → descobrir PIDs → ler ao vivo → acumular evidência`

A lista de dispositivos é recolhível. Desconectado, fica aberta para escolha. Conectado, recolhe e mostra o adaptador ativo; “Trocar dispositivo” reabre a lista.

Omegas Link permanece como fonte OBD remota separada. `off` desativa apenas OBD.

### PIDs padrão

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

### Mapas OBD próprios

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

### Live tracing OBD

A célula atual é localizada no Kotlin por RPM OBD × carga OBD independentemente da decisão de aceitar a amostra.

Assim:

- a UI continua mostrando onde o motor está mesmo em open loop, motor frio ou evidência ainda recusada;
- aceitar uma amostra e localizar a célula são decisões separadas;
- o live tracing mostra célula, RPM, carga, STFT, LTFT, MAP, MAF, combustível e frescor;
- a célula atual recebe destaque visual na grade.

### Condição mínima da evidência acumulada

Para uma amostra entrar no mapa OBD independente:

- célula RPM/carga válida;
- rótulo observacional de combustível disponível;
- closed loop;
- STFT válido;
- se a temperatura estiver disponível, motor acima do mínimo configurado.

A prova OBD não habilita aprendizado MP48 quando MP48 está ausente. Um rótulo manual apenas classifica observação OBD.

### Persistência

O mapa OBD independente é persistido junto ao estado OBD. Gasolina/GNV e épocas históricas da correlação existente permanecem separadas.

Nenhuma importação OBD escreve na ECU.

### Segundo plano

O serviço contínuo permanece um único `TelemetryForegroundService`, `START_STICKY`, classificado como `connectedDevice` e `location` somente quando GPS está ativo.

O app declara `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` e, na primeira execução em Android compatível, abre a confirmação oficial do Android uma única vez. Negar não cria loop. A interface mantém botão manual para abrir novamente a autorização.

### Telemetria flutuante

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

### Invariantes preservados

- nenhuma escrita automática na ECU;
- OBD não escreve Mapa K nem Curva K;
- overlay é leitura somente;
- sugestão/conexão/timer não inicia escrita;
- writers continuam Kotlin e dependem de revisão, confirmação, checkpoint, ACK e readback;
- UI não vira autoridade de matemática crítica;
- apenas um scheduler visual.

### Validação automatizada obrigatória

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

### Validação física obrigatória

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

---

## OBD — segunda prova independente

Data: 2026-08-08
Estado: implementado na branch `feature/ux-didactic-expansion`; CI Android e validação física pendentes.

### Objetivo

Transformar a área OBD em uma segunda fonte de evidência do comportamento do motor, separada do aprendizado MP48 e incapaz de escrever calibração.

### Duas superfícies OBD

1. **Correlação OBD × MP48 existente**
   - eixos: RPM e tempo de injeção gasolina da MP48;
   - conteúdo: STFT/LTFT e contexto OBD;
   - finalidade: correlacionar a ECU original com a célula física usada pelo núcleo.

2. **Mapa OBD independente**
   - eixo X: RPM OBD;
   - eixo Y: carga calculada OBD, PID `0104`;
   - conteúdo: STFT, LTFT, velocidade, temperatura, MAP, MAF e borboleta vindos do OBD;
   - camadas: Gasolina, GNV e Comparação;
   - comparação: diferença de STFT GNV − gasolina calculada no Kotlin apenas quando há amostras suficientes na mesma célula OBD.

O segundo mapa não usa tempo de injeção como eixo e não participa do aprendizado ou da calibração.

### PIDs

Críticos em todo ciclo:
- `0103` — estado do sistema de combustível / closed loop;
- `0106` — STFT banco 1;
- `010C` — RPM.

Contexto:
- `0104` — carga calculada;
- `0105` — temperatura do líquido de arrefecimento;
- `0107` — LTFT banco 1;
- `010B` — pressão absoluta do coletor (MAP);
- `010D` — velocidade;
- `010F` — temperatura do ar de admissão;
- `0110` — MAF;
- `0111` — posição da borboleta;
- `012F` — nível do tanque em percentual;
- `0142` — tensão do módulo de controle.

A descoberta Mode 01 continua sendo a autoridade de disponibilidade. Um PID não anunciado pela ECU é exibido como não suportado e não deve ser inventado. O PID `012F` fornece percentual; esta implementação não converte para litros sem conhecer a capacidade real do tanque.

### Gate da coleta independente

Uma amostra entra no mapa OBD independente somente quando:
- há rótulo observacional de combustível;
- a ECU original está em closed loop;
- RPM OBD é válido;
- carga OBD está entre 0 e 100%;
- STFT é válido;
- quando a temperatura OBD está disponível, o motor não está abaixo do mínimo configurado.

O rótulo de combustível pode vir da MP48 ou, se ela não estiver disponível, do operador. Rótulo manual não habilita aprendizado MP48 e não concede autoridade de escrita.

### Persistência e épocas

O mapa independente é persistido dentro de `obd_assist_v1.json` em um componente próprio. Após um ajuste manual confirmado por readback, a fotografia anterior é preservada no histórico da época e uma nova coleta ativa começa, evitando misturar evidência antes/depois.

### Invariantes

- OBD é somente observação;
- nenhuma chamada a `KWriteManager`, `KFactorManager`, `startKWrite`, `startKBatchWrite` ou `startKFactorWrite` existe na superfície OBD;
- nenhuma sugestão OBD inicia escrita;
- nenhuma matemática de Mapa K ou Curva K foi alterada;
- nenhuma gravação automática na ECU foi adicionada;
- a comparação do mapa independente é informativa e não vira correção automática.

### Evidência automatizada adicionada

- `ObdIndependentEvidenceMapTest.kt`;
- `tests/test_obd_independent_evidence_contract.py`;
- `tests/ui/obd-independent-map.test.cjs`;
- os contratos rápidos foram adicionados a `tools/run_checks.py`.

### Validação física ainda necessária

No veículo/aparelho real confirmar:
- quais PIDs o carro anuncia;
- se `012F` existe e representa corretamente o nível do tanque;
- estabilidade/latência do ELM327 com o grupo ampliado;
- preenchimento das células RPM × carga durante condução;
- separação Gasolina/GNV;
- comportamento via Omegas Link;
- layout e toque na multimídia 1024×600.
