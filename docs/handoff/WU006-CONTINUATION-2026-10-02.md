# WU-006 — handoff de continuação, 02/10/2026

Este documento é um checkpoint solicitado pelo proprietário. **Não é declaração de pronto nem substitui o prompt canônico.** Sua finalidade é permitir a outro agente continuar sem reconstruir a sessão. A solicitação de handoff interrompeu a implementação depois do bloco 6.

## 1. Missão, autoridade e proibições

- Repositório: `viluadmcontas2-dot/OMEGAS-V8.2`.
- Única branch autorizada: `claude/brave-darwin-wuliyo`. Não criar PR, outra branch, merge ou release por iniciativa própria.
- Preservar `0da6454a3fa0fc1101a2e605085609fc4e2e36c3` e todas as alterações posteriores do proprietário.
- Fonte de requisitos: `docs/handoff/PROMPT-GPT-WU-006-FINAL.md`, versão 2, com a correção mais recente do proprietário em `8656b75cc5cc5fcca774b4cd23f2c2e35616700c`.
- `AGENTS.md`: mutação de source **pela API remota do GitHub**; checkout efêmero serve para testar o SHA remoto exato. Não editar source local e depois fazer `git push` como substituição desse fluxo.
- Custo monetário zero. Não comprar serviços, assinar/upgradear ferramentas ou usar CI a cada commit.
- Nenhuma escrita automática na ECU. Preservar preparar → revisar → confirmar → ACK → readback. ACK sozinho nunca representa sucesso.
- Não alterar a matemática congelada de `AutoMatchRefinedEngine`, `EquivalenceLedger`, `RefinementJournal`, `RefinementAutopilot` ou `tools/autocal_refine`. Novas consultas puras/apresentação foram permitidas; não converter isso em autorização para mudar inferência científica.
- `PHYSICAL_VALIDATION_CLAIMED=false`; nenhuma ECU real, instalação ou rodagem foi executada nesta sessão.
- Resultado final original: APK com link, provas vinculadas ao SHA, STATUS/WorkUnit/evidence atualizados, resposta simples em português ≤12 linhas.

### Correções do proprietário que devem continuar prevalecendo

1. **Base:** equivalência MAP × Tpet das 18 bandas nativas por combustível, igual à ECU. Ela produz a proposta EQUIVALENCE.
2. **Visualização:** nossas bandas densas MAP × Tpet, mediana de leituras estáveis por MAP. Não alimentam o motor.
3. **Complemento:** pares RPM × MAP, peso 0,4 a partir de 3 ms. São “refino do refino”; nunca habilitam equivalência sozinhos.
4. **Depois da gravação manual:** verificação por faixa pelo diário existente.
5. “Tranco no GNV”: hipótese de ciclo-limite **descartada**, pois a alternância 8↔9 ms ocorre também na gasolina. Falar em **degrau que tira a linearidade da puxada**. Não prometer que o tranco desaparece.
6. Há contradição residual no §8 do prompt, que ainda diz “o tranco ... deve sumir”. Aplicar a correção explícita e posterior do §2.1/commit 8656b75, sem prometer desaparecimento.

## 2. Boot recomendado ao novo agente

1. Confira remote, branch, árvore limpa e HEAD. Faça `git pull --ff-only origin claude/brave-darwin-wuliyo` no checkout da branch; não descarte alterações de outro agente.
2. Leia `AGENTS.md` → `PROJECT.md` → `STATUS.md` → `docs/workunits/OMEGAS-WU-006.md`.
3. Leia **integralmente** o prompt canônico, inclusive sua ordem de referências do §0 e os critérios do §11. Depois leia este checkpoint.
4. Compare o HEAD encontrado com o último source abaixo; este próprio checkpoint adiciona um commit documental posterior. Se houver mudanças do proprietário após ele, preserve-as e adapte os passos, sem forçar a ref.
5. Leia os arquivos afetados antes de editar. Atualize ref pela API só após gate verde do SHA candidato e nova consulta ao HEAD remoto.

`STATUS.md` e a WorkUnit ainda contêm frases/contagens/estado Android históricos. **Não usar essas declarações como prova Android do source atual.** O fechamento documental continua pendente.

### Blueprints já lidos integralmente nesta execução

- CUSTOMROM: https://app.notion.com/p/3b68ee52ac5481839046f36b482aab44
- Omega Dev 4.0: https://app.notion.com/p/3b78ee52ac548170b5c1fb69606ced21
- Car Info Next: https://app.notion.com/p/3b88ee52ac5481988c28db1600330335
- Adaptação registrada em `docs/product/UX-BLUEPRINT-CHECKLIST.md`.

Car Info Next: fundo #0B0F14; superfícies #141B24/#19222D/#202C39; borda #2B3948; texto #F6F8FB/#98A4B3; accent #5CC4FF; verde #68DBA0; amarelo #FFC25C; vermelho #FF7474. Área segura 1280×644; Refino sem rolagem; títulos 26–30 px, métricas 20–26 px, texto essencial ≥12 px, toque 56–68 px, raios 16–22 px. Conferir fallback/900 px, não só desktop largo.

## 3. Estado publicado: preservar e não refazer

| Bloco do §10 | Commit | Estado e comportamento |
|---|---|---|
| Trabalho anterior | `0da6454a3fa0fc1101a2e605085609fc4e2e36c3` | Preservado; não reiniciar a WU |
| 1. Navegação + Refino | `ca50583bb4e838f5c18b35c52c48bb499a0d06f9` | Refino independente; oito destinos; Aprender restaurado; mount imediato; scheduler central; EQ em até 3 s quando visível; painel por fase |
| 2. Nossa curva/dados densos | `57621910779cc72c0777880e0eae7f6046ab1317f9` | Consulta/cache densos por época; MAP×Tpet nativo + pontos próprios; consulta pura; testes e paridade |
| 3. AutoCal | `b167a9208fcf2beab10692aa0a219c1bce7917f9` | Diagnóstico com dados reais, bandas rejeitadas, contagens e medianas típicas; falta de dados explícita |
| Documentos do proprietário | `d16a576` e `8656b75cc5cc5fcca774b4cd23f2c2e35616700c` | Preservados; correção causal incorporada |
| 4. Agora/notificação/Sugestões | `51390ed68b9352292dea943b52f10593cc8d1e37` | Entradas abrem Refino/revisão existente; cold/warm intent; gráfico da revisão corresponde à proposta preparada e fica congelado |
| 5. Levels | `3ded2a1a96aa30955e51f4d28e7829b84e964c99` | Leitura somente leitura de metadados; fallback explícito e seguro; **fidelidade nativa do sensor ainda não comprovada** |
| 6. Placar | `59f6fc57443e3e3eb3298f5777ee322e702c5f98` | Épocas confirmadas, arquivo do gás/ar existente antes de reset; GPS contínuo; comparação ≥5 km por época; apresentação e correção de sobreposição visual |
| 7. Roteiro | Ainda não publicado | Rascunho completo neste checkpoint, §9 |
| 8. Docs/prova Android/APK | Pendente | Não existe APK desta nova árvore nem CI disparado nesta sessão |

**Último source publicado:** `59f6fc57443e3e3eb3298f5777ee322e702c5f98`. O commit deste checkpoint será posterior e apenas documental/evidência de passagem.

### Detalhes técnicos que evitam regressões

- `screens/refinement.js` mantém controlador/painel persistente e escuta store com emissão inicial; contexto de revisão é consumido uma vez. Não recriar instância a cada tick.
- `AutoCalRefinePanel.refresh()` não recalcula/rerenderiza durante leitura, escrita **ou revisão aberta**. `reviewPoints` alinha a curva desenhada aos `targetRaw/targetFactor` efetivamente preparados; pontos não enviados permanecem mantidos.
- **Não tocar em `runWrite`:** releitura e comparação da curva, confirmação humana Android, lote, ACK e readback continuam no fluxo existente. A espera de operação já existia; não adicionar polling de tela.
- `SEM_ECU` não desenha snapshot antigo como atual. ESTAVEL não oferece botão de gravar; RESTAURAR_TRECHO oferece restauração manual; ECU_TRABALHANDO permite revisão com aviso de sobrescrita.
- `EquivalenceLedger.denseBandsJson(0.025,5)`: separa gasolina/GNV da época atual, agrupa MAP, calcula medianas com `PresentationMedian` em O(n), cache invalidado junto de `cachedIndex` ao entrar observação/reset. Retorna cópia do JSON para evitar mutação do cache. Nenhuma banda densa entrou na inferência.
- `TypicalInjectionBands` é consulta pura dos pontos de gasolina em faixas de 5 ms; vazio/desconhecido fica desconhecido. Ponte `typicalBands` e dependências de compilação no harness de paridade já incluídas.
- `screens/our-curve.js`: X=Tpet ms, Y=MAP bar; nativos adquiridos cheios/coletando vazados; próprios menores; detalhe fixo com proveniência; não fabricar RPM/contagem nos pontos nativos sem esses campos.
- `core/refinement-launch.js` centraliza consumo da rota pendente e sugestão; `ui/NotificationRoute.kt` limita extra Android a `refino`. MainActivity trata intenção fria, quente e página pronta. Não contornar por timer ou executar escrita ao abrir.
- `app.js` reaproveita fila/revisão existentes em Sugestões. Não duplicar wizard ou deixar item antigo depois da fase mudar.
- Bloco 6 também retirou rótulo `Anti-tranco` → `Degrau limitado` e comentário causal incorreto; risco visual ficou compacto (“Acentuado”/“Baixo”) sob “Linearidade da puxada”.

## 4. Levels: o que foi feito e o que continua sem prova

Arquivos novos principais:
- `app/src/main/java/com/omegas/prohub/telemetry/LevelEstimator.kt`
- `app/src/main/java/com/omegas/prohub/telemetry/LevelSensorSnapshot.kt`
- testes correspondentes em `app/src/test/java/com/omegas/prohub/telemetry/`
- `app/src/main/assets/ui/components/level-panel.js` e `tests/ui/level-panel.test.cjs`

Integração em `TelemetryForegroundService`, `HubJavascriptBridge`, `core/native-api.js`, `app.js`, index e CSS. Ferramentas usa contexto/scheduler existente para atualizar a telemetria; botão de leitura usa executor existente e serial scheduler READ_ONLY. Sessão USB nova/desconectada limpa metadados/leitura. Telemetria do nível expira após 2 s; ausência não vira zero.

### Resultado real da leitura

| Endereço | Tratamento atual | Limite |
|---|---|---|
| SC36 TIPO_SENSORE | Preserva bytes, “não decodificado” | Enum/direção e largura não comprovados |
| SC37 RIF_SENSORE | Preserva bytes, “não decodificado” | Formato/largura inferidos e não há quinto ponto cheio provado |
| SC276 filtros | Shape esperado 2×U16LE; registra coeficientes raw/32768 quando tamanho/ACK coincidem | Coeficientes não provam equação nem escolha FAST/SLOW |
| SC300 LEDs | Shape esperado 5×U8; registra valores | Predicado/direção/histerese efetivos não comprovados |
| SC313 TANK_VOL | **Somente índice 0**, U16LE; metadado nominal 1000×raw/32768 se válido | Não é volume medido; índice 1 é INJR_GAS_FLOW e nunca é solicitado |
| Sem resposta/ACK/tamanho válido | “não lido” ou raw “não decodificado” | Nunca 0, cheio padrão ou capacidade inventada |

`FORMULAS.md`, `parametros.json` e documentos de lacunas não fecham todo o perfil nativo. **Não assumir cheio=0/255, enum invertido, filtro IIR padrão ou LEDs iguais ao original.**

`LevelEstimator` contém caminho puro condicionado a cinco âncoras explícitas, sentido, coeficientes, modo e flags de política comprovada; há testes sintéticos desse caminho. **O leitor real não habilita essas flags.** Portanto o produto desta árvore usa `Mp48TelemetryScale.levelPercentage` inalterado, rotulado proxy, sem litros nem LEDs calculados. Os testes sintéticos não comprovam equivalência com firmware nativo.

Conseguir prova de formato/regra pode permitir completar o §7.2; até lá o fechamento deve registrar essa lacuna, não declarar filtro/LEDs nativos prontos. Revisar a acessibilidade/UX do estado calibrado se ele vier a ser habilitado: o painel atual foi deliberadamente escrito para o fallback que existe, não para declarar uma calibração futura.

### Pontos a inspecionar na revisão final

- Capture concorrente e troca de sessão: resposta antiga nunca pode reaparecer como atual. Existem testes de troca durante read, mas revisar também corrida de reset/publicação final e marcação READING.
- O serviço bloqueia leitura durante writers; serial scheduler arbitra outros leitores. Conferir exclusão/erro real com snapshot/ação AutoCal, sem inventar API `nativeAutoCal.isBusy()` (ela não existe).
- `ConsumptionTracker` é legado e ainda recebe `level_raw`; verificar onde a UI antiga mostra consumo/volume. Não alegar que esse estimador virou medida calibrada. Não foi alterado neste bloco.
- Todas as variantes novas Node estão registradas em `tools/run_checks.py`.

## 5. Placar: contrato implementado e limites

- `CalibrationScoreboard.kt` observa e persiste `calibration_scoreboard.json` em `paths.runtimeRoot`; histórico limitado a 32 épocas.
- Só abre época para Curva K/Mapa K com `humanConfirmed=true`, `readbackValid=true` e adjustmentId não vazio/não duplicado. Callback de leitura `onConfirmedWrite` **não** abre época.
- Arquiva `equivalence.gasPerAir()` antes de `resetGas`, nos mesmos pontos do serviço: Mapa K `MAPA_K_GRAVADO`; Curva K no `finally` de `recordCurveExperiment`, `CURVA_K_GRAVADA`. Não existe segundo somatório de gás/ar.
- Novo `EquivalenceLedger.gasEpochToken()` é accessor puro (hora+motivo). Após reset confirmado, bind da nova época. Mudança externa do token interrompe comparação; AutoMatch nativo interrompe explicitamente.
- Distância: GPS habilitado, timestamp ≤15 s e precisão ≤100 m; diferenças não negativas apenas entre quadros contínuos GNV. Não conta lacuna GPS, troca de combustível, reset de contador ou intervalo >15 s como percurso observado.
- N=5 km **em ambas** as épocas. Justificativa: janela operacional mínima para reduzir peso de trechos curtos; não representa prova estatística nem economia garantida.
- Queda de nível só recebe posição **não proxy**. Com o perfil atual desconhecido, queda/km por degrau ficam null/indisponíveis. Há proteção contra possível abastecimento e interrupção.
- UI: Ferramentas mostra detalhes; Agora uma linha compacta. Sem ação automática.
- Testes: confirmação/recibo duplicado, fechamento copia métrica existente, duas épocas ≥N, GPS/fuel gaps/reset, persistência, abastecimento e nível desconhecido. Ainda ampliar na revisão, se necessário, os casos de token inesperado/concorrência entre callbacks e telemetria.

## 6. Evidência disponível e interpretação correta

| Fonte testada | Prova | Limite |
|---|---|---|
| Bloco 2 | Gate/paridade PASS; suíte local 695 JVM PASS | Harness fora do Gradle, não Android build |
| Bloco 3 | Gate/paridade PASS; 13 JVM afetados PASS | Mesma limitação |
| Bloco 4 | Gate exato `51390ed6` PASS; teste de review RED→GREEN; 698 JVM no candidato anterior com Kotlin idêntico | Não dizer que 698 foi Gradle do SHA final |
| Bloco 5 `3ded2a1a` | Gate exato PASS; suíte integral local **704** PASS | Android stubs/executor local |
| Primeiro candidato do bloco 6 `22c6a8b7238dfce5d66d80d7201dcaf6c7d3004f` | Gate PASS; suíte integral local **709** PASS | Não é o SHA source publicado final |
| Bloco 6 final `59f6fc57` | Gate exato PASS; **5** testes JVM afetados de CalibrationScoreboard PASS após ajuste de continuidade; capturas refeitas | Não rerodou suíte integral 709 após a pequena alteração; full Android/lint/APK pendentes |

O gate foi executado com PATH do kotlinc **e** `ORG_JSON_JAR`; assim a paridade realmente roda, não é marcada skip. Guardar logs, não substituir por uma contagem que parece equivalente.

### Ambiente desta sessão (efêmero; pode desaparecer)

Checkout: `/workspace/scratch/76646e3208c5/omegas`.

```bash
cd /workspace/scratch/76646e3208c5/omegas
PATH=/tmp/omegas-jvm/kotlinc/bin:$PATH \
ORG_JSON_JAR=/tmp/omegas-jvm/android-all.jar \
python3 -B tools/run_checks.py

bash /tmp/omegas-jvm/verify.sh \
  /workspace/scratch/76646e3208c5/omegas \
  /tmp/wu006-final-jvm
```

Harness: JDK 17 em `/tmp/omegas-jvm/jdk-17.0.16+8`; kotlinc `/tmp/omegas-jvm/kotlinc/bin/kotlinc`; android-all.jar API35, JUnit/hamcrest/coroutines e stubs AndroidX/USB/BuildConfig em `/tmp/omegas-jvm/`. `javac` não está no PATH; usar caminho do JDK. `verify.sh` compila todos os main/test Kotlin e executa todas as classes `*Test`, com friend paths. Precisa rodar com cwd do repo: testes de fixtures falham artificialmente fora dele.

Stubs externos de NotificationCompat precisaram CATEGORY_STATUS/setAutoCancel. BuildConfig do harness tem valores artificiais e não é recibo de APK. Não modificar produto para acomodar os stubs; se faltar API, corrija o harness. Prova definitiva é Gradle Android/CI do source exato.

Logs preservados no pacote de evidência deste checkpoint (ver §7). Originais efêmeros importantes:
- `/tmp/wu006-review-red.log`, `/tmp/wu006-block4-green.log`
- `/tmp/wu006-level-red.log`, `/tmp/wu006-level-gate.log`, `/tmp/wu006-level-all.log`
- `/tmp/wu006-score-red.log`, `/tmp/wu006-score-all.log`
- `/tmp/wu006-score-final-gate.log`, `/tmp/wu006-score-targeted.log`
- `/tmp/wu006-all-layout.json`, `/tmp/wu006-all-layout.err`

## 7. Capturas e prova visual: preservar, mas completar

Capturas atuais e script estão em `docs/evidence/ui-wu006/draft/` e `docs/evidence/ui-wu006/capture-wu006.cjs`, preservados com este checkpoint. **São fixtures de bridge falso, não dados de carro nem teste E2E da aplicação inteira.**

- 1280×720: sete fases de Refino, AutoCal “normal”/“problem”, Agora, Ferramentas/Sensor.
- Métricas atuais de Refino: host/doc sem scroll nas sete fases, botão primário 56 px quando existe; SEM_ECU sem gráfico do cache.
- Corrigida sobreposição real de legenda/detalhes; imagem PROPOSTA_PRONTA posterior ao ajuste foi inspecionada visualmente.
- Capturas antigas 1280×644 existiram antes do último CSS; **precisam ser refeitas depois dele**. Layout report sem scroll não detecta todos os cortes/overlaps; inspecionar imagem e bounding boxes, não só scrollHeight.
- **Fixtures AutoCal precisam ajuste antes de virar aceite:** “normal” reutiliza risco HIGH, e o trecho problem usa chaves `fromTimeMs/toTimeMs` enquanto a UI lê `from/to`. Usar LOW/sem rejeições para normal; HIGH + rejeições e trecho correto para problema. Não trocar ciência do produto para fazer screenshot bonito.
- Conferir revisão aberta (gráfico K atual/proposta preparado), restauração, operação/falha/readback divergente e navegação de ida/volta sem perder contexto.
- Conferir Agora/Tools na montagem real (controller/backend falso), não só dados renderizados isoladamente. Current harness cria componentes reais com fixtures, mas não valida inteiro o startup/route/scheduler.
- Conferir 900 px, fallback 1024×600 e área segura 1280×644. Conferir targets, foco, texto ≥12 e ausência de rolagem horizontal.
- Checklist `docs/product/UX-BLUEPRINT-CHECKLIST.md` ainda está desmarcado; registrar resultado **item a item**, com captura/teste e limite quando não provado.

### Chromium reproduzível

Playwright é instalado no runtime. Browser padrão via CDN não baixou; Chromium @sparticuz 153 falhou. **131.0.1 funciona** com binário e libs Swiftshader da mesma versão, no mesmo diretório.

Efêmeros atuais:
- pacote `/tmp/wu006-browser-stable/node_modules/@sparticuz/chromium` (131.0.1)
- executável `/tmp/wu006-chrome-stable/chromium`
- ao lado dele: libEGL.so, libGLESv2.so, libvk_swiftshader.so, libvulkan.so.1, vk_swiftshader_icd.json
- fontes: `/tmp/wu006-chrome/fonts/fonts/Open_Sans`; config `/tmp/wu006-chrome/fonts/fonts.conf`

```bash
FONTCONFIG_FILE=/tmp/wu006-chrome/fonts/fonts.conf \
node docs/evidence/ui-wu006/capture-wu006.cjs
```

O script aceita `V82_CAPTURE_HEIGHT=644`. Atenção: seus nomes AutoCal/Agora/Tools ainda usam sufixo 720 fixo; ajustar antes de guardar variante 644 para não sobrescrever evidência. Recebe paths por env descritos no script. Não iniciar browser da conta do usuário para screenshots: é Chromium local com bridge falso.

## 8. Próximos passos: ordem, saída esperada e gate

1. **Auditar o limite do bloco 5/6.** Ler os arquivos e lacunas acima; conferir que não há falsas alegações de sensor/consumo. Corrigir bugs materiais se encontrados, por API e testes baratos RED→GREEN. Não substituir lacunas por suposição. Não reiniciar blocos 1–4.
2. **Bloco 7 — roteiro.** Publicar `docs/V82_REFINO_FIELD_TEST.md` com texto do §9 abaixo, revendo-o contra §8 do prompt e correção causal mais recente. Gate antes de atualizar ref. Só documentação não justifica build pesado.
3. **Concluir QA visual.** Ajustar fixtures, repetir tamanhos/estados acima, salvar capturas finais e resultado item a item. Manter rascunhos distinguíveis ou substituir explicitamente por evidência final válida.
4. **Revisão independente final Superpowers.** Ainda não houve reviewer. Skill `executing-plans` exige um reviewer novo para a branch inteira; `requesting-code-review` fornece template. Enviar spec + intervalo de commits + decisões/limites precisos, sem histórico da conversa; review read-only, sem outro subagente. Corrigir Critical/Important com testes; registrar aquilo que o reviewer deixar fora do escopo e a decisão. Não abrir PR.
5. **Bloco 8 — atualizar docs.** STATUS, WorkUnit, checklist e evidence: fatos, hipóteses e pendências separados; atualizar contagens reais; remover linguagem de ciclo-limite/garantia de ausência de tranco e hierarquia invertida da apresentação atual. Preservar registros históricos rotulados como históricos. Não atribuir APK antigo à árvore nova.
6. **Verificação local final do SHA candidato:** gate com paridade efetiva, testes afetados, suíte JVM adequada. Se revisão mudar Kotlin/integrar callbacks, rodar suíte integral de novo. É necessário saber SHA/árvore exatos; não depender de logs de candidato diferente.
7. **CI seletivo solicitado:** `.github/workflows/omegas-preapk-build.yml`, ref `claude/brave-darwin-wuliyo`. Uma rodada final; correção da causa raiz se falhar; mesmo erro duas vezes → reportar ao proprietário. Não acrescentar trigger de push nem criar PR para contornar acesso.
8. **APK:** `testDebugUnitTest lintDebug assembleDebug` com Gradle, identidade do SHA, artifact, SHA-256 do APK e do ZIP, package/assinatura/ABI e resultado físico false. Baixar/verificar; disponibilizar link real. Registrar run/job/artifact IDs e source SHA/árvore em STATUS/evidence. Commit apenas documental posterior não muda SHA do APK.
9. **Conferir §11 e responder em português ≤12 linhas.** Se fidelidade sensor estiver pendente, declarar proxy claramente. Não declarar todo o contrato cumprido/ANDROID_PROVEN sem prova. Não alegar melhora de consumo, desaparecimento do tranco ou validação física.

### Limite de acesso ao CI que deve ser resolvido sem surpresa

Nesta sessão **nenhum workflow foi disparado**. Plugin GitHub permite objetos/ref, fetch de runs/jobs/artifacts, download e rerun; não foi encontrada capacidade `workflow_dispatch`. SDK Android não está instalado neste executor. Acesso HTTPS público a api.github.com foi observado, mas **não foi testado POST autenticado**; não presumir credencial nem procurar/expor tokens.

O próximo agente deve verificar suas capacidades atuais. Se não houver dispatch autenticado disponível, concluir o trabalho concreto e pedir autorização para fallback ao GitHub pelo navegador (a política da ferramenta exige aprovação quando o plugin é insuficiente). Alternativa é o proprietário disparar o workflow na ref correta. Não confundir autorização geral para CI com capacidade técnica disponível, não reutilizar build antigo como entrega atual e não modificar workflow por conveniência.

## 9. Rascunho pronto do bloco 7 (ainda não é arquivo publicado)

Destino: `docs/V82_REFINO_FIELD_TEST.md`.

> # Teste do Refino no carro
>
> 1. Instale o APK desta entrega no multimídia e confira a versão/commit em Ferramentas. Conecte a MP48 e espere **ECU online** e telemetria recente. Sem conexão ou leitura válida, não grave.
> 2. Na primeira rodagem, deixe a ECU terminar o automático. Rode na gasolina e no GNV em condições comparáveis. Abra **Refino**: a linha do tempo mostra a fase e o próximo passo. A base é MAP × Tpet da ECU; nossos pontos densos mostram mais detalhe; RPM × MAP complementa a precisão.
> 3. Em **Curva refinada pronta**, pare em local seguro. Toque **Revisar e gravar**, confira curva atual, proposta e pontos, e confirme **Gravar na ECU**. Só “Gravada e conferida” com readback válido indica conclusão. ACK isolado/divergência não é sucesso. Com ECU ainda no automático, ela pode sobrescrever a curva.
> 4. Depois observe **Verificando**. Compare a linearidade na região de 8–9 ms. O degrau pode tirar a linearidade; a hipótese de ciclo-limite foi descartada. Software não prova que o tranco desapareceu: registre o que aconteceu na rodagem.
> 5. Em **Trecho piorou**, pare e use **Restaurar trecho que piorou**. Confira pontos e confirme manualmente. Não há restauração automática. Se gravação/readback falhar, guarde sessão e estado.
> 6. Em **Ferramentas → Sensor de nível**, use **Ler parâmetros da ECU**. “Não lido” significa sem resposta; “não decodificado” preserva bytes sem inventar escala. Barra atual é **proxy**, sem litros. Cheio, sentido, filtro e LEDs precisam de prova do perfil do carro.
> 7. **Esta calibração × anterior** observa gás/ar e GPS; exige 5 km em cada calibração. Abaixo disso, “dados insuficientes”. Sem nível calibrado, queda/km por degrau indisponíveis. Índice sozinho não comprova economia.
> 8. Ative gravação de sessão em Ferramentas antes de rodar. Envie a pasta inteira, incluindo telemetria, gravações/readbacks e `refinement_phase`. Informe combustível e condição da puxada quando sentiu o degrau.
>
> Sessões vão confirmar/corrigir janelas 10/25 minutos, tolerância ±3% e comportamento do sensor. Registre o motivo se reiniciar a aquisição. Insets, toque, ECU real e rodagem ainda não validados.
>
> PHYSICAL_VALIDATION_CLAIMED=false

## 10. Operação GitHub remota e armadilhas da sessão

Fluxo usado: `github_create_tree(base_tree_sha=HEAD)` → `github_create_commit(parent_sha=HEAD)` → fetch/checkout do candidato → gate → releitura da ref → `github_update_ref(force=false)` na única branch. RED candidates eram objetos remotos sem ref, não branches extras. Não restaurar candidate antigo sobre documentos novos do proprietário.

Na sessão, helper efêmero `makeCandidate` estava armazenado no functions.exec e lia JSON de paths/content de `/tmp/wu006-drafts`. Isso não é necessário para retomar: montar tree pela API a partir do HEAD atual. Não depender de store/load de outra sessão.

Preparações antigas **não usar cegamente**:
- candidato v1 `748ef38a...` de levels nunca publicado; pressupunha endpoint cheio e não deve ser integrado;
- `/tmp/wu006-drafts/prepare-level.py` é preparação antiga combinada levels/placar; reaplicá-la sobrescreveria decisões posteriores;
- `/tmp/wu006-drafts/block4.json`, `block5.json`, `block6.json` têm conteúdos completos da época; não reaplicar sobre HEAD novo sem diff;
- `/tmp/wu006-drafts/prepare-level-v2.py`, `prepare-score.py` foram meios de preparo, não autoridade;
- `22c6a8b7` é candidato superado do bloco 6; **source correto é 59f6fc57**;
- screenshots sem sufixo/antigos 644 podem ter fonte/paleta/labels antigos.

Nenhum teste foi desabilitado para obter verde. Um assert de label “Puxada no GNV” foi atualizado legitimamente para “Linearidade da puxada” conforme nova exigência. RED da revisão detectou 2 leituras em vez de 1 e foi corrigido com congelamento do contexto; RED levels/placar falhou por módulo/capacidade ainda ausente antes do GREEN.

## 11. Definição inequívoca do ponto de parada

- Source dos blocos 1–6 publicado e preservado na branch autorizada.
- Sem revisão independente final, sem encerramento documental, sem CI/APK desta árvore.
- Sensor: consulta + proxy honesto, **não equivalência nativa provada**.
- Visual: capturas/inspeção preliminares persistidas; tamanhos/fixtures/checklist completos ainda pendentes.
- Próxima entrega de implementação: **bloco 7**, seguida da revisão/QA/docs e CI/APK; não voltar ao §3 para refazer tudo.
- Se este checkout ou `/tmp` desaparecerem, source, checkpoint, script/capturas e logs essenciais estão no GitHub. Não há segredo necessário para interpretar o trabalho.

PHYSICAL_VALIDATION_CLAIMED=false
