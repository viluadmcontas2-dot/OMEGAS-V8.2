# Painel de progresso do Refino

Proposta de UX e contrato Kotlin; não altera o aplicativo nem comprova calibração real. Fontes locais lidas: AGENTS.md, refino.js, autocal-cockpit.js, EquivalenceLedger.kt, RefinementJournal.kt e RefinementAutopilot.kt em /home/user/OMEGAS-V8.2. O mockup usa somente dados fictícios. Os limiares de quantidade abaixo são decisões propostas, a validar por replay de telemetria; não são resultados de campo.

## 1. O que o dono deve entender

**Equivalência GNV×gasolina — 3,2% de diferença. Quanto menor, melhor.** Base: resposta da injeção de gasolina com o motor no GNV comparada à referência de gasolina sob RPM e MAP semelhantes. Não é economia, mistura medida por sonda, potência nem “96,8% concluído”. Meta: desvio de cada lado entre −4% e +4%; tolerância aceita até 5%. Como o indicador usa magnitude absoluta, sua meta é ≤4%, não um número com sinal.

Uma frase orienta o estado e uma única ação fica no rodapé. Os demais textos são rótulos, números e legendas. Nada de JSON, exceções, nomes de enums ou valores K crus na tela. Sem estimativa de duração, relógio, animação de trabalho contínuo ou progresso que avance sozinho.

## 2. Cálculo no Kotlin

1. Usar `Ledger.allPairs()`; `pairs()` exclui grupos locais e NÃO serve para o resultado total. Para cada par válido: `e = 100 × (gasPetrolMs / petrolRefMs − 1)`; `a = abs(e)`. `gasPetrolMs` é o tempo de injeção de gasolina observado no GNV, NÃO `gasMs`. Exigir referência positiva, números finitos, origem e geração conhecidas. O Ledger casa RPM ±150 e MAP ±0,02 bar com mediana de pelo menos duas observações de gasolina; pode usar referência interpolada da ECU. Registrar e distinguir essa origem.
2. Unidade de cálculo: célula `cellKey(rpm,map)` do Ledger (150 rpm × 0,02 bar), subdividida pelo intervalo RPM de apresentação e pela faixa de `petrolRefMs`. Assim nenhuma unidade cruza os limites das mini-barras. Usar até os últimos 30 pares válidos distintos por unidade, na mesma configuração e referência. Mediana matemática: em tamanho par, média dos dois valores centrais.
3. `errorCellPct = median(a)`. Não usar `abs(median(e))`: erros +8% e −8% não podem se cancelar. Guardar também `signedMedianPct` para diagnóstico, sem usá-lo para parar. Unidade com menos de 8 pares = sem dados suficientes; `null`, nunca zero.
4. Ponderação por uso: contar observações estáveis distintas no GNV **antes** do limite de retenção do Ledger, inclusive observações sem par; não usar o tamanho do buffer limitado a 30 como frequência de uso. `usageCount` vem de contador novo por unidade. Observações sem referência ficam em um grupo não classificado e continuam no denominador da cobertura. Uma observação com o mesmo identificador nunca conta duas vezes; consulta de status não conta.
5. Congelar contagens/pesos no começo do ciclo e manter um acumulador separado de novas visitas. Para unidades válidas V: `E = Σ(u_c × errorCellPct) / Σ(u_c)`, c∈V. Repetir a mesma média ponderada em cada faixa RPM e ms. `coveragePct = 100 × uso das unidades válidas / uso total`, incluindo o uso não classificado. Se denominador zero, retornar `null`. Publicar também cobertura por quantidade de unidades e número de pares. E parcial não recebe aparência de resultado final.
6. Não esconder erro localizado na média: estado da faixa considera a pior unidade válida da faixa, além de sua cobertura. Publicar `maxCellErrorPct`. Faixa com parte sem medição pode mostrar número parcial, mas fica marcada “Sem dados suficientes”; se já há unidade >5%, “Fora” prevalece, com cobertura parcial explícita.

Exemplo: erros medianos por unidade 2%, 4%, 8%, usos 50/30/20 → E=3,8%. A unidade de 8% impede “Pronto”. Referências de ECU e próprias nunca se misturam silenciosamente: pesos separados por origem e comparação com origem igual por unidade. O número acompanha “Base: gasolina · condições semelhantes”, mais cobertura.

**Diferença para o existente:** `index().ratio` é mediana de razões em log, não esta métrica; `index().bands` não permite recuperar mediana absoluta por célula nem frequência de uso. É necessário agregar pares e persistir contadores novos. Os snapshots atuais do Journal com `indexBefore/indexAfter` também não bastam para reconstruir esta comparação. Não converter esses índices em um falso percentual de progresso.

## 3. Domínio e suficiência

RPM: [850,1250), [1250,1750), [1750,2250), [2250,2750), [2750,3500], último extremo inclusivo. Injeção de referência: 3,0–4,5; 4,5–6,0; 6,0–7,5; 7,5–9,0; 9,0–12,0 ms (início inclusivo, fim exclusivo) — estes são os cinco intervalos existentes do Ledger, escritos com vírgula decimal. Na implementação usar limites numéricos 3.0/4.5/6.0/7.5/9.0/12.0. Valores externos não são encaixados à força; contar em `outsideDomainReads` e rotular “Fora das faixas deste painel”.

Escopo de conclusão: unidades visitadas no perfil de uso congelado, mínimo de 3 faixas ms distintas e 3 células RPM×MAP distintas no total. Exigir 100% de cobertura desse escopo, 8 pares por unidade, todos os erros de unidade e E ≤4%. Faixas nunca visitadas permanecem cinza; “Pronto nas faixas medidas” não afirma cobertura de todo o motor. Domínio externo visitado fica explicitamente pendente e impede conclusão global; o produto deve ampliar o domínio antes de prometer equivalência ali.

## 4. Parada, retorno e histerese (só evidência)

- **Medindo:** faltam referência, diversidade ou leituras; nenhuma gravação apenas para preencher uma barra. Frase: “Ainda faltam leituras para comparar com a gasolina.”
- **Corrigindo:** evidência suficiente fora da meta, proposta nativa válida e gravação elegível em andamento. Frase: “O app está ajustando as faixas que ficaram diferentes.” Só entrar após despacho real da operação, não por haver proposta. Respeitar habilitação do Refino e travas existentes. Sem proposta/ECU indisponível: Medindo com motivo objetivo, sem fingir trabalho.
- **Conferindo:** gravação confirmada por ACK e leitura de volta igual; coleta somente pares posteriores à configuração confirmada. Frase: “O ajuste foi gravado; falta conferir nas mesmas faixas.” Nenhuma nova gravação nas faixas ainda sob conferência.
- **Pronto:** todos os critérios de escopo e meta atendidos em **dois lotes independentes**, cada um com pelo menos 8 pares novos por unidade; zero proposta/gravação/restauração local pendente e nenhuma conferência inconclusiva no escopo. Frase: “A meta foi atingida nas faixas medidas; o Refino parou de ajustar.” `refining=false`, `monitoring=true`; bloquear novas escritas de polimento e propostas antigas também no caminho automático. Não desligar o aprendizado nativo da ECU.
- **Sem dados nesta faixa:** estado da faixa selecionada, não “falha” nem 0%. Frase: “Ainda não há comparação suficiente nesta faixa.” Sem base: ação “Ver referência necessária”; com base: “Ver faixa que falta”. Não instruir o motorista a perseguir RPM durante a condução.
- Após Pronto, erro **>4% e ≤5%** mantém a parada; mostrar “Dentro do aceito · meta 4%”. Atingir 5,0% não retoma. **>5%** em uma unidade do escopo, confirmado por dois novos lotes independentes de 8 pares nessa unidade, retoma avaliação/correção. Um único lote vira Medindo para confirmar, sem gravar e sem afirmar meta atual. Erros são avaliados sem arredondar.
- Após retorno, só parar novamente em ≤4% nos dois lotes de todas as unidades exigidas. No intervalo 4–5%, permanecer fora da meta; se não houver correção elegível, Medindo: “Está dentro do aceito; a meta ainda não foi confirmada.” Não declarar Pronto pela ausência de proposta. Essa regra é mais estrita que o `ESTAVEL` atual do piloto (3 faixas, tolerância e latch); requer alteração no Kotlin, não remapeamento de texto no JS.
- Nova unidade visitada após Pronto: incorporá-la ao escopo, congelar novos pesos, ir para Medindo; ela não herda sucesso. Mudança da referência, AutoMatch, mapa/curva externos ou readquisição invalidam evidências afetadas e lotes; ir para Medindo/Conferindo conforme o recibo. Não atribuir isso a piora causada pelo app. Perda de conexão: indicador atual “—”, Medindo, frase “Conecte a ECU para continuar a medição”; preservar histórico com seu rótulo.
- Dois lotes têm identificadores de observações disjuntos e não são duas consultas da mesma janela. Sem novas observações nada avança. Nenhum timeout produz sucesso. Tempo de transporte/cache pode existir, mas não decide equivalência; o filtro atual de estabilidade do Ledger usa janela de 1,2 s como validade de aquisição, não como progresso ou critério de parada.

A única ação normal: “Ver faixas pendentes” durante medição/conferência; “Ver ajuste” durante correção; “Ver resultado” quando Pronto. São detalhes em painel substituto sem rolagem, com botão único “Voltar”. Em desconexão: “Como conectar”. O interruptor único do Refino continua na superfície existente, sem criar botão de pausar ECU neste painel.

## 5. Última gravação: antes → depois

Sempre fixar `experimentId` e sequência da **última gravação confirmada**, nunca usar `journal.result` de outra gravação como se fosse a última. Antes = snapshot imutável das unidades afetadas, sua referência e pesos imediatamente anteriores ao comando; depois = pares posteriores ao readback da mesma configuração. Para mapa, registrar células alteradas; para curva, usar faixas afetadas do Journal, incluindo interpolação/margem. Persistir dados necessários no momento da gravação.

Comparar a mesma lista de unidades, mesmos pesos e mesma origem/token de referência. Não remover uma unidade sem dados do “depois” para produzir melhora. Exigir 8 pares por unidade antes e dois lotes de 8 depois. Se só parte comparável, rotular parcial, publicar cobertura e deixar o resultado completo desconhecido. Exibir “7,8% → 3,2% · diferença caiu 4,6 pontos percentuais”, com rótulo “Nas faixas alteradas”. Redução = antes − depois; não chamar de economia. Neste mockup as faixas alteradas coincidem com o escopo medido.

Para o resumo descritivo proposto: queda ≥2 p.p. = “Melhorou”; aumento ≥2 p.p. = “Piorou”; entre eles = “Sem mudança clara”. Só após suficiência e comparabilidade. Isso não substitui as regras mais conservadoras do Journal para oferecer restauração (ruído em log, erro mínimo, diversidade e quantidade). Havendo piora em alguma unidade, expor “Melhorou no conjunto; uma faixa piorou”, nunca ocultá-la na média.

Sem antes: “— → 3,2% · sem base anterior”; sem depois: “7,8% → — · conferindo”; referência mudou: “Comparação indisponível: a referência mudou”. Orçamentos atuais do Journal (100 leituras para parcial; 500 para encerrar inconclusivo) não viram barra de conclusão nem sucesso; apenas encerram experimento sem prova. A exibição antes/depois é exclusiva do Refino; AutoCal continua sem comparação histórica.

## 6. Contrato JSON proposto: `equivalence.progress`

Todos os cálculos, estados, ações e textos vêm do Kotlin; JS apenas formata em pt-BR e desenha. Campos abaixo são **novos**, salvo fontes explicitadas. Percentuais em unidade 0–100 (erro pode superar 100), contagens inteiras não negativas, valores desconhecidos `null`; sem NaN/Infinity. Limiares serializados para evitar divergência visual. Enum interno nunca aparece ao dono.

| Campo / tipo | Conteúdo obrigatório |
|---|---|
| `schemaVersion: integer` | 1 |
| `revision: integer`, `observationSequence: integer` | Revisão do snapshot e última observação distinta incorporada; polling não incrementa sequência |
| `state: string`, `reason: string`, `headline: string` | MEDINDO, CORRIGINDO, CONFERINDO, PRONTO ou SEM_DADOS_FAIXA; motivo estável e frase leiga |
| `refining, monitoring, connected: boolean` | Trabalho real, monitoramento habilitado e conexão; não confundir habilitação com atividade |
| `action: object` | `kind`, `label`, `enabled`, `targetBandId` opcional; uma única ação, somente destinos conhecidos |
| `scope: object` | `id`, `profileId`, `unitIds[]`, `label`, `rpmMin:850`, `rpmMax:3500`, `outsideDomainReads`, `unclassifiedReads`, `complete:boolean` |
| `reference: object` | `token` (Ledger.referenceToken), `petrolEpoch`, `ecuReference`, `configurationId` (mapa+curva+época nativa), `source: OWN/ECU/MIXED/NONE`, `ownPairs`, `ecuPairs`, `label` |
| `metric: object` | `errorPct`, `maxCellErrorPct`, `targetPct:4`, `acceptedPct:5`, `coveragePct`, `measuredUnits`, `requiredUnits`, `validPairs`, `partial:boolean`, `label` |
| `evidencePolicy: object` | `pairsPerUnit:8`, `independentBatches:2`, `minMsBands:3`, `minPhysicalCells:3`, `windowPairsPerUnit:30`, `usageProfileId` |
| `rpmBands[], msBands[]: array` | Cada item: `id`, `from`, `to`, `upperInclusive`, `label`, `errorPct`, `maxCellErrorPct`, `coveragePct`, `validPairs`, `measuredUnits`, `requiredUnits`, `usageCount`, `state: WITHIN/OUTSIDE/NO_DATA`, `targetReached`, `partial`, `reason`, `stateLabel` |
| `currentBand: object/null` | `rpmBandId`, `msBandId`; marcador de leitura atual, sem transformar visita em validação |
| `verification: object/null` | `experimentId`, `unitsConfirmed`, `unitsRequired`, `batchesComplete`, `batchesRequired`, `newValidPairs`, `missingUnitIds[]`; sem ETA |
| `lastWrite: object/null` | `experimentId`, `sequence`, `kind`, `ack`, `readbackMatches`, `configurationBefore`, `configurationAfter`, `scopeLabel`, `unitIds[]`, `weights[]`, `referenceToken`, `beforeErrorPct`, `afterErrorPct`, `deltaPp`, `comparable`, `coveragePct`, `outcome`, `reason`, `label` |
| `stop: object` | `latched`, `reason`, `entryPct:4`, `resumeAbovePct:5`, `confirmationBatches:2`, `confirmedBatches`, `pendingUnitIds[]`; estado persistido por escopo/configuração |

Agregação interna auditável (não renderizar como código): por unidade guardar limites RPM/MAP/ms, origem, identificadores dos pares, mediana absoluta, contagem de uso anterior ao cap e peso congelado. Persistir o snapshot de comparação no Journal; reinício não inventa antes/depois. Publicar JSON atomicamente para evitar indicador de uma época e barras de outra.

## 7. Cores, layout e acessibilidade

1280×720, fundo #0B111B, cartões #141E2C, texto #F3F6FC e secundário #B1BED0. Topo persistente com tensão ECU, MAP, injeção e RPM; navegação com Refino selecionado. Conteúdo em duas colunas: métrica+última gravação à esquerda; duas fileiras de mini-barras à direita. Rodapé com uma frase e botão `.btn-bar` de 58 px. Sem rolagem, sem texto cortado para caber, sem dependência de rede/fonte externa.

- Verde #5DE0AD + ✓: ≤4%, cobertura suficiente, legenda “Na meta”.
- Amarelo #F3CB75 + ≈: >4% até 5%, cobertura suficiente, “Aceito”; é subtipo WITHIN, mas não uma nova conquista da meta.
- Coral #FF9D91 + !: >5%, “Fora”; no retorno, continua com marca de pendência até ≤4%, rótulo “Voltando à meta” no intervalo aceito.
- Cinza #8393AA + — e hachura: sem dados/suficiência; jamais barra vazia que pareça 0% de erro.
- Azul #80BFFF para Medindo/Conferindo; Corrigindo amarelo; Pronto verde. Cor nunca é a única informação.

Mini-barras são segmentos de estado de largura igual, não progresso por tempo nem proporção de uso. Cada segmento mostra intervalo, diferença numérica e símbolo. A legenda associa símbolos aos estados; detalhe acessível informa motivo, cobertura e contagens. Barras RPM e ms são duas projeções dos mesmos pares, não somar suas contagens. Não esconder faixa ruim em média verde.

## 8. Critérios de aceite

Sem novas observações: nenhum incremento de progresso, lote ou transição. +8/−8% não resulta em zero. Buffer cheio não torna uso uniforme. 5,0% mantém parada; 5,01% só retoma após dois lotes; 4,01% não conclui; 4,0% conclui com demais requisitos. Uma célula de 8% bloqueia Pronto mesmo com global 3,8%. Origem/época diferente impede antes/depois. Gravação sem readback não aparece como gravada. Faixa ausente não recebe verde. Dados parciais não somem do denominador. Conferência inconclusiva não vira Pronto. Reabrir app conserva latch apenas para a configuração e escopo válidos. Validar implementação futura com replay real e WebView em 1280×720; este HTML demonstra layout, não prova ECU.

Validação do artefato: HTML independente com CSS/JS embutidos e ação Ver resultado/Voltar; tentativa de renderização pelo Playwright bloqueada pelo sandbox ao iniciar Chromium. Sem alegação de validação visual ou de ECU.
