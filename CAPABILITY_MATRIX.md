# Matriz de capacidades e validação — OMEGAS V8

| Área | Estado nesta branch | Validação física |
|---|---|---|
| Telemetria MP48 | núcleo preservado; UI continua consumindo telemetria nativa; caminho rápido mostra somente RPM + Petrol Inj. + célula atual | pendente |
| Backpressure multimídia | thread ECU permanece desacoplada; buffer quente limitado/coalescido e persistência auxiliar assíncrona continuam preservados | **aguardando sessão prolongada no aparelho** |
| Aprendizado gasolina/GNV | equivalência física preservada; gasolina continua referência; GNV é comparado por condição física; nenhum limiar RPM/MAP/temperatura foi afrouxado | pendente no carro |
| Evidência por visita | `visitId` tornou-se unidade física imutável no runtime V7; snapshot posterior não reescreve RPM/MAP/Petrol Inj./qualidade/timestamp da visita já conhecida | pendente com histórico real prolongado |
| Cronologia científica | comparação V7 usa o timestamp físico da visita GNV, permitindo reconstrução determinística após reinício | pendente no aparelho |
| Memória consolidada | `LearningStabilityV7` separa `NO_EVIDENCE / LEARNING / CONSOLIDATED / REVALIDATING`; outlier isolado não substitui consolidado | pendente no carro |
| Revalidação | tendência contraditória fica recente até também provar repetibilidade/consenso/MAD; quando comprovada pode promover nova geração consolidada | pendente no carro |
| Invariância | cálculo de estabilidade ordena por timestamp + visitId; mesma coleção de evidências deve gerar o mesmo estado independentemente da ordem de entrada | automatizado em teste; pendente com arquivo real |
| Interpolação contínua | bilinear/trilinear continuam no Kotlin; peso reparte influência entre pontos sem criar múltiplas visitas independentes | pendente no carro |
| Qualidade de amostra | RPM, MAP, Petrol Inj., pressão e temperatura continuam usando a política Kotlin vigente | pendente no carro |
| Aprendizado explicável | grade mostra `Aprendendo`, `Consolidado` ou `Revalidando`; quando consolidado existe, ele é o número principal; candidato recente fica no detalhe | pendente no carro/touchscreen |
| Tolerâncias didáticas | controles nativos/presets continuam sem tocar ECU; não foram usados como atalho para estabilizar o aprendizado | pendente no carro |
| Modelo de épocas | gasolina preservada; GNV/calibração revalidam conforme ajuste confirmado; época antiga não vira evidência atual | pendente |
| Live Tracing visual | **removido** por decisão do proprietário para preservar fluidez; nenhum halo/trail/4 pesos são perseguidos no DOM; apenas contexto textual leve permanece | pendente de desempenho no aparelho |
| Persistência auxiliar do aprendizado | `CoalescedSnapshotWriter` continua preservando a fotografia mais nova sem converter coalescência em descarte de evidência física | pendente sob carga real |
| Snapshot V7 | schema `OMEGAS_V7_SESSION_6` persiste metadados de estabilidade e continua lendo schemas 2–5 sem migração destrutiva | automatizado em teste; pendente com arquivos reais |
| Sugestão local | fila persistente depende do consolidado; alvo permanece congelado na mesma geração; durante `REVALIDATING` fica visível porém não acionável | pendente no carro/touchscreen |
| Curva K por sugestão | além de consolidação, proposta global exige cobertura em mais de uma condição de RPM e MAP; evidência localizada permanece local | pendente no carro |
| Curva K manual | editor de 30 pontos + prévia Kotlin + writer com backup/ACK/readback preservados | pendente |
| Mapa K manual | 1 a 144 células por intenção humana; editor oficial continua acessível diretamente pelo Aprendizado; writer mantém blocos internos de segurança | pendente |
| Linha técnica Mapa K | 13ª linha preservada e fora da edição | pendente |
| Autoridade comum de escrita | `CalibrationWriteSafetyPolicy` preservada; nenhuma mudança deste bloco contorna o gate nativo | pendente |
| Writer Mapa K | backup, conferência, ACK por passo, readback e falha parcial explícita permanecem intocados | aguardando ECU |
| Writer Curva K | backup, conferência dos 30 pontos, ACK/readback e confirmação final permanecem intocados | aguardando ECU |
| AutoCal / protocolo | fora do escopo deste bloco; comportamento anterior preservado | aguardando ECU/variantes |
| OBD local Bluetooth | observacional e preservado; nenhuma mudança deste bloco | pendente com ELM real |
| OBD remoto | Omegas Link observacional preservado | pendente com dois aparelhos |
| OBD desligado | `off` preservado | pendente |
| Foreground service / flutuante | comportamento da melhor versão preservado; nenhuma alteração neste bloco | aguardando celular/OEM |
| Sessões diagnósticas e logs | `SessionRecorder`/`RingLog` preservados; sem logger paralelo | pendente no aparelho |
| Arquivo `.omegas` | import/export nativos preservados; importação não escreve ECU | pendente com arquivos reais |
| UI autoridade única | uma Store, um Router e um Scheduler; nenhuma nova shell/timer/MutationObserver; estabilidade chega como JSON nativo contextual | pendente no aparelho |
| Método CUSTOMROM | aplicado por intenção primeiro, estado humano claro e detalhe técnico sob demanda; Blueprint permanece somente leitura | validação UX contínua |
| UI horizontal 9" | base clean-slate preservada; nenhuma animação de tracing foi reintroduzida | pendente 1280×720/1024×600 |
| UI vertical | fallback responsivo preservado | pendente |
| Netlify/navegador | somente simulação; não prova Android/USB/ECU | não se aplica |
| APK desta branch | fronteira separada; nenhum APK novo autorizado/gerado neste bloco até nova ordem explícita | não autorizado |

## Leitura correta
- **Aplicado na branch** = código/documento existe no GitHub remoto desta branch.
- **PASSOU AUTOMATIZADO** só pode ser usado depois de gate atual do mesmo commit.
- **AGUARDANDO CELULAR/VEÍCULO** significa que teste automatizado não prova hardware, WebView real, USB, ECU, ELM ou estabilidade física do aprendizado.
- CI verde prova somente as superfícies exercitadas.
