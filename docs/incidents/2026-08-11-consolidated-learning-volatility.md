# Incidente — volatilidade de célula teoricamente consolidada

Data: 2026-08-11  
Estado físico na abertura: **FALHOU** quanto à estabilidade do aprendizado; a versão era a melhor até então, mas o proprietário observou células já aprendidas mudando com evidência pequena/casual.

## Sintoma e impacto
- Uma célula que visualmente parecia aprendida/consolidada podia mudar após pouca evidência nova.
- A sugestão acompanhava a fotografia recente do advisor em vez de uma memória científica consolidada.
- Isso reduzia a confiança operacional: o usuário não conseguia distinguir ruído recente de uma mudança real e persistente da condição do motor.

## Causa imediata
1. `MotorLearningMemory` mantinha regiões agregadas com vários `visitId` e médias atuais de RPM/MAP/Petrol Inj./qualidade.
2. `V7CalibrationCoordinator.ingestLearningSnapshot()` reapresentava cada `visitId` da região usando a média atual daquela região.
3. `V7SessionRuntime` aceitava substituir a evidência de um `visitId` quando chegava uma fotografia posterior/maior qualidade. Assim, uma visita histórica podia ser reinterpretada por uma média regional nova.
4. `V7EquivalenceEngine` carimbava a comparação com o horário da recomputação, não com o timestamp físico da visita, impedindo reconstrução cronológica determinística.
5. O advisor contínuo tinha propositalmente alta responsividade e um teste explicitava que uma única amostra forte podia gerar candidato acionável. Não existia uma autoridade separada `consolidado × recente` com histerese/revalidação.

## Causa estrutural
O sistema separava evidência, comparação e sugestão, mas não possuía uma camada científica explícita de **memória consolidada**. A mesma corrente de evidência recente alimentava estimativa e decisão, portanto o produto confundia:
- aprendizado novo;
- memória já estabelecida;
- possível mudança ainda não confirmada.

## Por que os testes não detectaram
- O contrato antigo permitia substituir uma evidência de mesma visita por uma versão mais nova/“melhor”.
- O contrato antigo exigia que sugestão pendente pudesse mudar magnitude a cada atualização.
- Havia teste afirmando que uma única amostra forte podia gerar proposta no advisor cru.
- Não havia regressões para:
  - imutabilidade de visita histórica;
  - outlier após consolidação;
  - revalidação simétrica;
  - promoção de mudança persistente;
  - invariância à ordem;
  - persistência dos metadados de estabilidade.

## Correção aplicada na branch de trabalho
- `visitId` virou unidade física imutável no runtime V7: o primeiro registro é preservado e snapshots agregados posteriores não reescrevem seus valores.
- Comparações usam `cng.collectedAtMs` como cronologia científica.
- Criado `LearningStabilityV7` com estados:
  - `NO_EVIDENCE`;
  - `LEARNING`;
  - `CONSOLIDATED`;
  - `REVALIDATING`.
- Promoção inicial e revalidação usam os parâmetros científicos já existentes (`confirmedVisits`, consenso de direção, MAD e deadband); equivalência RPM/MAP/temperatura não foi afrouxada.
- Um outlier contraditório entra como candidato recente; o consolidado fica intacto.
- Mudança contraditória repetível pode promover novo consolidado e incrementar a geração.
- Sugestões locais ficam congeladas dentro da mesma geração consolidada; durante revalidação permanecem visíveis, porém não acionáveis.
- Curva K exige, além de consolidação, cobertura em mais de uma faixa de RPM e MAP antes de liberar proposta global madura.
- Schema de sessão atualizado para `OMEGAS_V7_SESSION_6`, mantendo leitura dos schemas 2–5 e persistindo metadados de estabilidade sem apagar arquivo antigo.
- UI de Aprendizado passa a mostrar o consolidado como valor principal e a tendência recente apenas como `Revalidando` no detalhe.
- Live Tracing visual continua removido; a posição atual permanece apenas em texto leve (`RPM + Petrol Inj. + célula`).

## Testes de regressão adicionados/migrados
- visita histórica não pode ser reinterpretada;
- visitas coerentes consolidam;
- um outlier isolado apenas revalida;
- contradição persistente promove nova geração;
- mesma evidência em ordem diferente gera o mesmo estado;
- peso bilinear de uma visita não vira quatro visitas independentes;
- cobertura global distingue condição localizada de tendência abrangente;
- schema 6 preserva estabilidade e lê schema 5 com defaults;
- sugestão mantém alvo na mesma geração consolidada;
- writer via advisor só fica disponível depois de consolidação.

## O que ficou intocado
- protocolo MP48;
- equivalência física;
- tolerâncias de coleta/equivalência;
- referência de gasolina;
- interpolação Kotlin;
- writers de Mapa K e Curva K;
- checkpoint/backup, ACK e readback;
- OBD observacional;
- flutuante;
- Store/Router/Scheduler;
- `main`, Netlify, release e publicação.

## Evidência automatizada
Pendente de gate final do commit consolidado desta branch. Resultados intermediários não fecham este incidente.

## Risco residual
A automação pode provar determinismo, persistência, contratos e ausência de regressões conhecidas. Ainda é obrigatório validar no aparelho/veículo:
- se uma célula consolidada realmente permanece estável sob condução normal;
- se uma mudança física real eventualmente entra em `REVALIDATING` e promove novo consolidado sem demora excessiva;
- CPU/RAM e latência da multimídia em sessão prolongada;
- consistência das sugestões com dados reais;
- comportamento após escrita física/época nova.

Até essa validação, usar **AGUARDANDO VEÍCULO** e não declarar estabilidade física concluída.
