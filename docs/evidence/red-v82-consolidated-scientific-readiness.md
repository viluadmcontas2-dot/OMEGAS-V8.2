# OMEGAS V8.2 — consolidação científica no SHA 8132a16

Data: 2026-08-31  
Branch: `work/red-v82-science-blend`  
Source SHA: `8132a16e1af6b557787d25dfd3de26394149c431`  
Source tree: `04b72c76296d0fea434d71080ab8c47b59b95015`  
Baseline RED: `b637f5fff19b1ece93f22d1fced9640618609a60`  
Escopo: consolidação de resultados já existentes; nenhum novo teste científico, reparse de logs, ajuste de ciência, alteração de Predictor/Android ou escrita em ECU.

## Resposta curta

Houve progresso real, mas ainda não existe prova para promover um Predictor novo.

O que está provado é que `RPM × MAP` sustenta conhecimento local útil e que todo frame válido — inclusive transiente — pode aumentar essa evidência local. O RED continua sendo o melhor preditor futuro comprovado: modelos Gaussian, híbrido e campo geométrico não venceram simultaneamente mediana e caudas no holdout. O simulador mecanístico mostrou como transformar uma equivalência gasolina/GNV em demanda combinada e como separar tendência global da Curva K do residual local do Mapa K sem dupla contagem. Porém, ainda falta ligar, numa mesma cronologia comprovada, o estado exato da Curva K e do Mapa K, a intervenção e o resultado posterior. Sem essa ponte, causalidade física, sensibilidade e `P(improve)` permanecem não provadas.

## Gates fechados no mesmo SHA

| Gate | Prova no SHA `8132a16…` | Resultado |
|---|---|---|
| Science Blend | Actions run `33349519902`, run #61 | Todos os jobs e passos GREEN |
| Exhaust Existing Tests | Actions run `33349519909`, run #6 | GREEN |
| Exaustão Python raiz | 37 arquivos; execução direta e discovery | 0 falhas |
| Exaustão Python lab | 18 arquivos; modo suportado e discovery | 0 falhas |
| Exaustão Node | 27 arquivos | 0 falhas |
| Exaustão Android JVM | 83 arquivos de teste | status 0 |
| Android completo | JVM + lint + APK | PASS |
| RED hot path | diff contra baseline fixo | preservado pelo gate |

Recibo Exhaust: artefato `9743168338`, SHA-256 `33ca873bc2f0edd16e09d04fb5ff7dfc5b7b7097e5610647cb06aacd8858372f`.

Recibo Science/Android: run `33349519902`; APK SHA-256 `8c90eeb0b898ed0ff88d0ed3621151f2dde34fb480d76dac1d8c36f8bf84cf28`. O APK é candidato simulado, não instalado e não validado no veículo.

## Regra científica consolidada

Todos os frames válidos contam para **conhecimento local**. Transiente não é descarte automático. Uma passagem rápida por `RPM × MAP` acrescenta uma observação de `Petrol Inj.` naquela vizinhança física.

Isso não torna automaticamente duas passagens causalmente equivalentes. Para transferir uma conclusão entre sessões ou afirmar que uma mudança de calibração causou melhora, também são necessários época compatível, estado de calibração conhecido, suporte independente e cronologia comprovada. Portanto:

- densidade de frames governa o conhecimento local;
- sessões/epochs governam persistência e transferência;
- intervenções confirmadas governam causalidade;
- célula/ponto alterado não é experimento independente;
- ausência de suporte para transferência não apaga o dado local.

## Consolidação por família

### 1. Corpus e cache

O corpus montado contém 38 exports físicos de sessão, 34 sessões lógicas e 5.902.982 variantes de evento após união lógica bounded-memory. Quatro sessões possuem mais de um export; não houve conflito de sequência. Duas linhas JSON truncadas foram preservadas como erro, sem reparo inventado.

O fixture WU-006 de 1.708 episódios é apenas uma visão privacy-safe compacta: 266 episódios gasolina e 1.442 GNV em 33 sessões lógicas. Ele não representa o corpus completo.

Os contratos portáveis do science warehouse passaram no Science Blend. A base SQLite extraída não está versionada no repositório nem materializada neste workspace; por isso esta consolidação usou os manifests, derivados, fixture e logs de CI já existentes, sem reabrir ZIPs ou logs privados. O antigo registro `parser-transfer-red-failure-2026-08-30.md` descreve uma falha anterior já superada pelos contratos GREEN do SHA atual; ele não é o estado atual do parser.

**Provado:** identidade/dedupe, schema cache e ingestão idempotente estão cobertos por contrato.  
**Desconhecido:** F0 do Drive continua incompleto; não há prova de que toda fonte física acessível foi incorporada ao cache disponível.

### 2. MAP ↔ Tinj e conhecimento local

O modelo observacional confirmado é `Petrol Inj. | RPM, MAP`, não `Tinj = f(MAP)` isoladamente. O clue observado `MAP 0,438 → 4,76 ms` e `MAP 0,918 → 10,30 ms` é aproximadamente proporcional (razões 2,096× e 2,164×; diferença de cerca de 3,2% em `Tinj/MAP`), mas duas observações não provam uma lei global.

No fixture real, gasolina teve 252 episódios analisáveis em sete regiões: quatro unimodais, duas ambíguas e uma candidata multimodal muito esparsa (`n=6`, duas sessões). GNV teve 1.387 episódios analisáveis em 15 regiões: cinco unimodais, seis ambíguas e quatro candidatas multimodais. As regiões densas tendem a ser estáveis ou amplas sem separação física suficiente; os sinais multimodais mais fortes aparecem sobretudo em regiões pequenas.

**Provado:** RPM e MAP juntos possuem poder preditivo local; transientes válidos contribuem.  
**Falsificado:** MAP sozinho como lei universal e GMM como upgrade automático.  
**Desconhecido:** quais variáveis ocultas explicam as regiões ambíguas — estado de calibração, AutoCal, temperatura, pressão, dinâmica ou mistura desses fatores.

### 3. Walk-forward, Gaussian e híbrido

No walk-forward cego de gasolina, o RED suportou 213/247 alvos (cobertura 86,23%), com erro relativo absoluto mediano 1,253%, P90 5,406% e P95 8,014%.

O Gaussian pooled aumentou cobertura para 93,52%, mas piorou mediana para 2,281%, P90 para 11,084% e P95 para 15,668%. O Gaussian balanceado por sessão também ficou pior: mediana 2,213%, P90 11,969% e P95 15,668%.

No holdout aninhado de 103 alvos a partir da ordem 26, o RED suportou 89 (86,41%), com mediana 1,468%, P90 5,373% e P95 7,604%. O híbrido não acrescentou nenhum fallback: 14 abstentions, mesmas 89 previsões e mesmas métricas do RED.

**Provado:** o RED é a âncora preditiva vencedora atual.  
**Falsificado:** maior cobertura Gaussian implica melhor previsão; o híbrido atual melhora o RED.  
**Desconhecido:** se um contexto de calibração explicitamente alinhado permite ganho sem piorar P90/P95.

### 4. Campo geométrico

O candidato ajusta localmente `Tinj = centro + inclinação_RPM × ΔRPM + inclinação_MAP × ΔMAP`, usando somente passado. Em 85 alvos com suporte comum, a mediana melhorou marginalmente de 1,3744% para 1,3727%, mas P90 piorou de 4,3125% para 5,4610%, P95 de 5,4062% para 6,7407% e máximo de 7,9856% para 9,0533%.

**Decisão:** `DEFER`. É útil para diagnóstico e explicação, mas não ganhou direito de entrar no Predictor.

### 5. Simulador mecanístico ASU

Para um par comparável gasolina/GNV:

`multiplicador_combinado = Tinj_GNV_observado / Tinj_gasolina_referência`

e, quando o estado atual é conhecido:

`K_efetivo = CurvaK(Tinj_ref) × MapaK(RPM,Tinj_ref) / 100`

`K_efetivo_alvo = K_efetivo_atual × multiplicador_combinado`

A decomposição em log evita dupla contagem:

`ln(combinado) = tendência_global_da_curva(Tinj_ref) + residual_local(RPM,MAP)`.

No fixture governado: 1.273 de 1.442 episódios GNV formaram pares comparáveis, em 17 sessões independentes e com zero leakage; 1.235 tiveram suporte transversal para decomposição e 38 permaneceram somente locais. A mediana do multiplicador combinado foi 0,9500; mediana ponderada 0,9471; P10 0,8824; P90 1,0190. Com deadband ±2%, houve 992 demandas de redução, 158 neutras e 123 de aumento.

Isso não é recomendação global de −5%. É distribuição de demandas locais simuladas, ainda sem o estado temporal completo da ECU.

**Provado:** identidade mecanística, sinal da correção e decomposição Curva/Mapa sem dupla contagem.  
**Falsificado:** uma única correção global ou a Curva K sozinha. Nas fotos, o mesmo `Tinj_ref≈5,21 ms` aparece com +9,3% e +35,6%, exigindo contexto local/regime.  
**Desconhecido:** valor absoluto novo de Curva/Mapa e efeito causal posterior.

### 6. Residual contínuo existente

O runtime atual já contém um campo residual que remove a tendência global e estima o restante em `RPM × MAP`, projetando depois para os eixos físicos `RPM × Petrol Inj.` do Mapa K. Ele distingue suporte `DIRECT`, `NEAR` e `GLOBAL_ONLY`, combina incerteza, preserva confirmação humana e não possui writer automático.

**Provado:** contrato estrutural, separação global/local, bounded support e segurança.  
**Não provado:** que suas sugestões melhoram o veículo ou vencem o RED held-out. Passar teste de contrato não é prova de desempenho científico.

### 7. Mapa K

Mapa K é a superfície 12×12 em `RPM × Petrol Inj.`, distinta da coordenada observacional `RPM × MAP`.

No corpus montado existem 70 intervenções/batches independentes válidos e 357 alterações confirmadas de célula. No fixture público causal, mais restrito, existem 11 intervenções e 133 células com confirmação, ACK/readback e finalização. Portanto, 357 células não são 357 experimentos.

O PortMon de escrita registrou 39.524 tentativas físicas, 39.522 no dispositivo suportado, 39.517 transações confirmadas e cinco tentativas finais não confirmadas. Foram observadas 144 escritas MAP_K, cobrindo as 144 células, todas com valor 100.

**Provado:** intervenção física, unidade batch, readback e cobertura estrutural.  
**Não provado:** efeito real pós-intervenção; o fixture de episódios e o de intervenções não têm clock domain comum demonstrado.

### 8. Curva K / K-factor

Curva K é independente do Mapa K: 30 pontos Q14 no endereço `0x0161`, com eixo próprio de Petrol Inj. No corpus montado há 29 intervenções válidas e 101 alterações confirmadas de pontos.

O PortMon analisado não identificou escrita direta de K-factor, embora o histórico de sessão contenha batches confirmados. Isso limita a triangulação física do writer, não invalida automaticamente os eventos confirmados.

**Provado:** dimensão, formato e histórico de intervenções no corpus.  
**Falsificado:** tratar Mapa K e Curva K como uma variável única; tratar Curva K como suficiente para todo erro local.  
**Desconhecido:** sensibilidade causal por ponto e curva completa vigente em cada episódio.

### 9. AutoCal e native anchor

Devem permanecer separadas cinco entidades: 18 zonas de aquisição AutoCal; Curva K de 30 pontos; Mapa K 12×12; curvas observacionais gasolina/GNV; sugestões OMEGAS.

O corpus montado contém 12 snapshots AutoCal distintos, todos parciais e nenhum temporalmente coerente no checkpoint existente. O checkpoint antigo marcou quatro campos de 30 elementos como inválidos porque `MODULE_VERSION=100` era convertido em expectativa de 18. Essa interpretação está **falsificada pela correção de domínio**: `PETR_INJ_TBP`, `MUL_ACT`, `PETR_MNFLD_PRESS_RV` e `GAS_MNFLD_PRESS_RV` pertencem à família de 30 pontos e não podem ser confundidos com as 18 zonas.

O código atual já declara esses quatro campos com tamanho base 30, mas `expectedElements()` ainda reduz campos module-sized a 18 quando a versão não é 4. Assim, o contrato permanece internamente inconsistente para `MODULE_VERSION=100`. Como esta missão proíbe alteração Android sem ganho/prova, o decoder não foi modificado; os quatro campos históricos devem ser tratados como `UNKNOWN_PENDING_PROTOCOL_PROOF`, não como corrupção.

O PortMon AutoCal possui 36.463 transações confirmadas e 21 comandos, predominantemente de leitura/estado, sem escrita MAP_K ou K-factor identificada. O PortMon de calibração mostra oito toggles AutoCal, oito inserções K ON e quatro OFF. A captura também mostrou evolução de contador/MUL_ACT sem comando manual AutoMatch, compatível com aprendizado interno da ECU.

O native anchor do app somente nasce de evento AutoCal válido correlacionado à telemetria GNV na mesma sessão. Ele é deduplicado, limitado, versionado por epoch, não vira voto gasolina×GNV e não tem acesso a writer.

**Provado:** contrato de segurança e correlação do anchor; evidência observacional da atividade AutoCal.  
**Não provado:** significado físico completo das 18 zonas, eixo de cada buffer, efeito explicativo/causal dos anchors e decodificação correta da família 30 para versão 100.

### 10. Calibration epochs e OOD

O app separa gasolina persistente de GNV por época de calibração; mudança confirmada de calibração inicia nova epoch e impede anchor/GNV antigo de validar a superfície atual. O gate OOD também abstém quando há telemetria inválida, regime local ambíguo, poucas sessões independentes, epoch incompatível ou ausência de vizinho RED.

Os testes OOD provaram o comportamento fail-closed em fixtures. Não há no artefato atual uma auditoria quantitativa OOD real por epoch. A cronologia mestre julho→agosto, contendo em cada instante hashes de Mapa/Curva, snapshots e suporte de telemetria, ainda não foi concluída.

**Provado:** isolamento lógico entre epochs e política de abstention.  
**Desconhecido:** quantas epochs físicas existiram, quais estados completos pertencem a cada uma e quanto do drift observado elas explicam.

### 11. Causalidade, sensibilidade e P(improve)

O laboratório causal agrupa por ajuste manual, exige confirmação/readback e congela suporte comparável. Contudo, os 11 ajustes do fixture causal resultaram em zero intervenções comparáveis e 11 abstentions porque não existe ponte comprovada entre o relógio das intervenções e o dos episódios.

Estado correto:

- `CAUSAL_MAP_K_REAL=ABSTAIN_UNPROVEN_COMMON_TIMEBASE`;
- `SENSITIVITY_PROVEN=false`;
- `P_IMPROVE_PROVEN=false`;
- `VEHICLE_PROVEN=false`;
- `AUTO_WRITE_ECU=false`.

Experimentos sintéticos provam que o método detecta direção de melhora/piora quando a verdade é conhecida; não substituem resultados físicos held-out.

## O que foi provado, falsificado e permanece desconhecido

| Provado | Falsificado | Desconhecido |
|---|---|---|
| Todo frame válido pode fortalecer evidência local RPM×MAP | Transiente deve ser descartado | Persistência de cada regime em novas epochs |
| RED é o melhor baseline held-out atual | Gaussian/GMM merece promoção por sofisticação/cobertura | Feature set que melhora RED sem piorar caudas |
| Mecanismo combinado Curva×Mapa pode ser decomposto sem dupla contagem | Curva-only e correção global única | Estado exato Curva/Mapa por episódio |
| 70 batches MAP_K e 29 batches Curva K no corpus montado | Célula/ponto = experimento | Sensibilidade causal de cada região/ponto |
| Native anchor/epoch/abstention têm contratos seguros | Anchor AutoCal é voto de equivalência | Poder explicativo real das 18 zonas |
| Dois clock domains não podem ser unidos por suposição | Correlação temporal inferida é causalidade | Ponte temporal intervenção→resultado |
| Science Blend e toda bateria existente estão GREEN no mesmo SHA | Teste GREEN significa economia física | Economia, emissões e comportamento no veículo |

## Três próximos experimentos de maior valor

### 1. Ponte cronológica intervenção → resultado comparável

**Pergunta:** cada batch confirmado de Mapa K/Curva K pode ser ligado, sem adivinhação, ao estado anterior, ao estado posterior e a regiões `RPM × MAP` comparáveis?  
**Dados:** cache existente, IDs de sessão/adjustment, hashes de Mapa/Curva, timestamps com clock-domain declarado, ACK/readback e episódios posteriores.  
**Teste:** matched pre/post por batch, suporte congelado, placebo temporal e falsificador de relógio.  
**Claim máximo:** efeito observacional associado; causal somente onde a ponte e comparabilidade forem explícitas.  
**Valor:** desbloqueia sensibilidade real e eventualmente `P(improve)`.  
**Decisão:** `TEST_FIRST`; se a ponte não existir no cache, registrar ausência e planejar uma captura governada, sem inferir timestamps.

### 2. Ablation cega RED vs RED + mecanismo Curva/Mapa

**Pergunta:** o contexto de calibração e a decomposição `global por Tinj + residual RPM×MAP` melhoram previsão futura sobre o RED?  
**Dados:** somente episódios anteriores, estado explícito de Curva/Mapa e split por sessão/epoch.  
**Teste:** walk-forward e leave-one-epoch-out com suporte comum; comparar cobertura, mediana, P90 e P95.  
**Falsificador:** qualquer piora material de P90/P95 ou leakage.  
**Valor:** é o teste que pode dar direito científico a um Predictor mais sofisticado.  
**Decisão:** `DEFER_UNTIL_EXPLICIT_STATE_ALIGNMENT`; depois `TEST`, sempre offline.

### 3. AutoCal 18 zonas como explicador de regime/OOD

**Pergunta:** zona, buffer, acquired state e AutoMatch explicam a multimodalidade e o drift residual que RPM×MAP não explica?  
**Dados:** snapshots decodificados com contrato 18-vs-30 correto, correlação na mesma sessão, epochs e telemetria local.  
**Teste:** ablation leave-one-session/epoch-out `RED` vs `RED + estado AutoCal`, medindo erro e abstention calibrada.  
**Falsificador:** ganho só dentro da sessão, piora de cauda ou dependência de campos inválidos/forward-filled.  
**Valor:** pode transformar o AutoCal em contexto causal/explicativo sem torná-lo writer.  
**Decisão:** `DEFER_UNTIL_PROTOCOL_FIXTURE_PROVEN`; não alterar runtime para antecipar o resultado.

## Decisão de produto

Não promover modelo novo, não alterar Predictor/Android e não escrever na ECU. O caminho de maior valor é primeiro provar a cronologia de calibração; depois executar a ablation mecanística cega; só então avaliar AutoCal como contexto. RED permanece baseline e fallback obrigatório.
