# OMEGAS V8 — Estratégia de testes e qualidade

## Objetivo
Transformar requisitos de segurança e produto em evidência executável. Um resultado verde significa somente que os comportamentos listados foram exercitados; nunca substitui validação no celular, na multimídia ou no veículo.

## Pirâmide prática
1. **Governança executável** — arquivos obrigatórios, invariantes, ausência de segredos e configuração da CI.
2. **Testes focados** — reproduzem defeitos e regras críticas com execução rápida.
3. **Contratos de interface** — ponte WebView/Kotlin, formatos, consumidores e fronteiras de escrita.
4. **Testes de componente** — Kotlin/JVM e JavaScript por comportamento público.
5. **Lint/análise estática** — Android, recursos e integrações frágeis.
6. **Build debug** — somente quando a fronteira APK/build estiver explicitamente autorizada.
7. **Integração Android** — WebView, serviço, ciclo de vida, USB simulado e persistência.
8. **Dispositivo/veículo** — multimídia, celular, MP48, ACK, readback, desempenho contínuo e operação física.

## Portões
### Gate rápido — sempre
- governança;
- contratos Python;
- testes JavaScript;
- sintaxe JavaScript;
- segredos;
- contrato de que snapshots substituíveis podem ser coalescidos sem converter isso em descarte de evidência;
- contrato executável da consolidação: visita/comparação imutáveis, estados `LEARNING/CONSOLIDATED/REVALIDATING`, sugestão estável e Live Tracing visual ausente.

### Gate Android sem APK — mudanças Android normais
- `testDebugUnitTest`;
- `lintDebug`;
- teste JVM do persistidor coalescido e fechamento/flush da fotografia mais nova;
- testes determinísticos do motor de estabilidade e persistência V7;
- relatórios;
- **não executa `assembleDebug` em push/PR**.

### Gate build/APK — fronteira separada
Somente depois de autorização explícita e execução apropriada via `workflow_dispatch`:
- `assembleDebug`;
- APK de teste;
- SHA-256;
- artifact ligado ao commit.

Gerar APK não autoriza release, publicação, instalação ou validação física.

### Gate profundo do aprendizado
Antes de aceitar mudança em memória, advisor, sugestões ou projeção, provar:
- `visitId` já conhecido não pode ser reinterpretado por snapshot agregado posterior;
- a primeira comparação válida de uma visita GNV permanece imutável; gasolina nova pode resolver visita ainda pendente, não reescrever comparação antiga;
- comparação usa timestamp físico da visita;
- visitas repetíveis promovem `CONSOLIDATED`;
- outlier isolado produz `REVALIDATING` sem mover o consolidado;
- mudança contraditória repetível pode promover nova geração;
- a mesma coleção de evidências em outra ordem produz o mesmo estado;
- influência bilinear conserva o peso físico e não cria visitas independentes artificiais;
- volume acima da fila histórica bruta não derruba a memória consolidada;
- sugestão fica com magnitude estável enquanto a mesma geração consolidada vigora;
- durante `REVALIDATING`, sugestão permanece visível porém não acionável;
- Curva K global exige cobertura em mais de uma condição de RPM e MAP; evidência localizada permanece no Mapa K;
- snapshot atual persiste metadados de estabilidade e continua lendo versões anteriores sem migração destrutiva;
- nenhuma dessas regras afrouxa equivalência RPM/MAP/temperatura nem altera writer/ACK/readback.

### Gate físico
Obrigatório antes de declarar pronto para uso real:
- instalação/atualização no aparelho;
- multimídia horizontal e celular vertical;
- suspensão, retomada e execução prolongada;
- conexão/desconexão/reconexão USB;
- durante sessão prolongada, `learningPipeline.pending` não pode crescer continuamente nem produzir defasagem de minutos;
- persistência auxiliar deve permanecer assíncrona/coalescida sem perder a fotografia final;
- Gasolina/GNV devem exibir `Petrol Inj.` médio, RPM e MAP coerentes com a evidência real da sessão;
- **Live Tracing visual deve permanecer removido**; a WebView não persegue halo/trail/pesos bilineares, enquanto a interpolação científica continua no Kotlin;
- contexto rápido deve mostrar apenas RPM + Petrol Inj. + célula atual sem degradar fluidez;
- célula consolidada deve permanecer estável sob ruído/passagem casual;
- mudança real deve aparecer como `REVALIDATING` e eventualmente consolidar sem demora excessiva;
- reiniciar o app deve preservar o mesmo consolidado e o mesmo estado de sugestão;
- comparação deve aparecer quando o núcleo possuir par equivalente, sem afrouxar RPM/MAP ou tolerâncias para ganhar velocidade;
- leitura real do Mapa K;
- intenção de 1–144 células e blocos internos;
- cancelamento sem escrita;
- falha de ACK;
- readback divergente;
- falha parcial explícita;
- Curva K preparada por sugestão;
- confirmação apenas por readback real.

## Estados oficiais
- **PASSOU AUTOMATIZADO:** todos os gates automatizados **autorizados e aplicáveis** ao commit passaram.
- **PARCIAL:** uma camada relevante aplicável ainda não foi executada/autorizada.
- **FALHOU:** evidência direta mostra quebra de requisito.
- **AGUARDANDO CELULAR:** automação aplicável passou, mas falta aparelho.
- **AGUARDANDO VEÍCULO:** falta MP48/ECU/OBD ou validação física.

## Evidência mínima
- repositório, branch e commit;
- data e ambiente;
- comandos/jobs executados;
- resultados por camada;
- arquivos alterados;
- artifact e SHA-256 somente quando houver APK autorizado;
- limitações e validações pendentes.

## Regressão
Todo defeito confirmado deve produzir teste que falhe pelo motivo correto antes da correção, quando tecnicamente viável. O incidente só fecha com causa, correção, teste, documentação e validação necessária.
