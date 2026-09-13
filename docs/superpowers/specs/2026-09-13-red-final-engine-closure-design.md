# OMEGAS RED — Final Engine Closure Design

## Objetivo

Fechar a engine existente do OMEGAS RED sem reconstrução paralela, preservando o que já funciona e removendo as regressões que tornaram o aprendizado inconsistente. O resultado deve ser didático, funcional, prático e rápido: gasolina como superfície-base permanente, equivalência física contínua, Predictor como superfície estimada rápida, convergência progressiva pela evidência e sugestões sempre manuais.

## Autoridade científica

1. **Gasolina é referência permanente.** Evidência válida de gasolina não expira por tempo, troca de combustível, epoch de GNV, mudança de Curva K ou mudança de Mapa K.
2. **Equivalência científica primária:** `RPM × MAP -> Petrol Inj. esperado na gasolina`.
3. **GNV atual é avaliado contra essa superfície:** `erro = PetrolInj_GNV / PetrolInj_GasolinaEsperado - 1`.
4. **Endereço do Mapa K permanece separado da equivalência:** para GNV, a célula física é localizada por `RPM atual do GNV × Petrol Inj. atual do GNV`.
5. **Curva K** representa tendência global por Petrol Inj.; **Mapa K** representa residual/localidade física após a tendência global.
6. Janela temporal curta nunca decide se uma referência de gasolina existe. Tempo curto só pode existir em um experimento causal de antes/depois de alteração K.
7. Nenhuma camada escreve automaticamente na ECU. Sugestão, Predictor, aprendizado e edição são observacionais até a revisão/confirmação humana já existente.

## Superfície de gasolina

A memória de gasolina deve ser tratada como uma superfície contínua `T_petrol = f(RPM, MAP)` construída a partir de evidência observada.

### Estados de suporte

- `OBSERVED`: há evidência real suficientemente próxima/forte naquele ponto/região.
- `INTERPOLATED`: valor estimado dentro do domínio suportado pela evidência observada.
- `UNSUPPORTED`: não existe suporte geométrico suficiente; não extrapolar agressivamente.

Interpolação não significa apenas preencher uma célula vazia entre duas preenchidas. Deve funcionar em qualquer direção da vizinhança física — horizontal, vertical, diagonal e combinações — usando distância e suporte em espaço contínuo. Uma região cercada por evidência coerente não deve aparecer vazia apenas porque nenhum frame caiu exatamente no centro geométrico da célula.

A interpolação deve ser limitada ao **domínio suportado**. Pontos muito distantes da evidência permanecem `UNSUPPORTED`; não se inventa conhecimento fora do hull/alcance aceito.

## Relação MAP ↔ Petrol Inj. e geometria

O sistema deve explorar a correlação física já observada entre MAP e tempo de injeção, sem assumir linearidade global cega.

- Superfície direta: `(RPM, MAP) -> Petrol Inj. gasolina`.
- Para projeção na grade física do Mapa K, usar a geometria/âncoras existentes para relacionar `(RPM, Petrol Inj.)` ao MAP correspondente somente dentro de suporte válido.
- A grade é projeção da ciência; ela não é a autoridade que define equivalência.

A matemática contínua existente (`ContinuousLearningMath`) deve ser reutilizada e fechada, não substituída por uma engine concorrente.

## Predictor

O Predictor volta a ter a função original: entregar cedo uma **superfície estimada útil** antes de haver cobertura observada completa.

Modelo conceitual:

`error_ratio = global_curve(petrol_target_ms) + local_residual(rpm, map_bar) + noise`

- `global_curve`: tendência ampla por Petrol Inj., útil mesmo com cobertura local ainda limitada.
- `local_residual`: correção espacial contínua em RPM×MAP, usada somente onde existe suporte suficiente.
- O Predictor pode publicar uma estimativa em regiões ainda não observadas diretamente **desde que estejam dentro do domínio suportado/interpolável**.
- Conforme chega evidência real, ela ancora/substitui a estimativa e reduz incerteza.
- Evidência observada e valor predito/interpolado nunca podem ser apresentados como a mesma coisa.
- Fora de suporte: `GLOBAL_ONLY` ou `UNSUPPORTED`, nunca observação inventada.

O Predictor não cria uma matemática paralela de calibração; ele é uma projeção estimativa da mesma autoridade científica de gasolina/equivalência e da mesma superfície de erro.

## Desvio medido e sugestões

Quando houver GNV atual com referência de gasolina suportada:

1. obter `PetrolInj_GasolinaEsperado = f(RPM, MAP)`;
2. comparar com `PetrolInj_GNV` atual;
3. publicar desvio medido e confiança;
4. separar o valor medido do valor previsto;
5. gerar intenção de correção Curva K/Mapa K usando a arquitetura já existente;
6. manter proposta manual, sem writer automático.

Uma alteração confirmada de Curva/Mapa K inicia novo estado/epoch do **GNV afetado**, mas não invalida nem reinicia a superfície permanente de gasolina.

## UI/UX do Aprender

A tela deve ser operacional, não um painel de engenharia.

### Célula / região principal

Mostrar somente o necessário:

- RPM / MAP de contexto;
- gasolina esperada;
- GNV observado;
- diferença/desvio;
- confiança;
- origem do valor (`medido` ou `interpolado/predito`);
- ajuste sugerido quando existir.

Remover do painel principal termos e blocos como `BlueCausalEngine`, histórico de epochs, calibration state, suporte interno detalhado, ACK/readback, IDs e explicações de engenharia. Esses dados podem continuar disponíveis em diagnóstico/ferramentas, não na operação diária.

A própria grade deve evitar texto repetitivo dentro de cada célula. Valor principal e estado visual são suficientes.

## Desempenho

- Nenhuma reconstrução global pesada por frame.
- Superfícies/predições são recalculadas por revisão semântica/evidência nova relevante, fora do hot path de telemetria.
- Cache de projeção permitido e recomendado.
- Scheduler permanece auto-cadenciado, sem backlog.
- Persistência continua coalescida/atômica.
- Interpolação usa vizinhança limitada e estruturas existentes; sem varrer histórico inteiro a cada render.

## Invariantes de não regressão

1. Gasolina coletada há segundos, horas ou dias continua válida como referência se o suporte físico for compatível.
2. Não existe `return null` por ausência de gasolina nos últimos 30 segundos na equivalência.
3. Evidência de gasolina não é invalidada por epoch/calibração GNV.
4. Uma região interna cercada por suporte coerente em 2D/diagonal recebe estimativa interpolada; não fica vazia só por falta de amostra exatamente no centro.
5. Regiões fora de suporte suficiente não são extrapoladas como verdade.
6. Observado, interpolado e previsto permanecem distinguíveis.
7. GNV usa a superfície de gasolina em `(RPM, MAP)` para erro e usa `(RPM_GNV, PetrolInj_GNV)` para endereço do Mapa K.
8. Predictor converge para evidência: aumentar cobertura real reduz dependência de estimativa, nunca o contrário.
9. Nenhuma sugestão escreve automaticamente na ECU.
10. UI operacional não expõe jargão interno como requisito para o usuário entender o que fazer.

## Testes adversariais obrigatórios

- gasolina antiga + GNV atual na mesma região física -> comparação válida;
- mesma gasolina com 31 s, 30 min, 24 h e vários dias -> referência não expira por tempo;
- vizinhos acima/abaixo com lacuna interna -> interpolação válida;
- vizinhos diagonais e combinação 2D -> interpolação válida;
- múltiplos pontos envolvendo RPM e MAP -> superfície contínua sem buraco artificial;
- ponto fora do hull/suporte -> `UNSUPPORTED`/abstenção;
- evidência observada contradiz previsão -> observado vence e incerteza aumenta/recalibra;
- mudança de Curva K/Mapa K -> gasolina preservada, GNV entra em novo epoch;
- predictor com pouca cobertura -> superfície parcial/estimada útil com incerteza explícita;
- predictor com cobertura crescente -> convergência e redução de incerteza;
- endereço Map K sempre usa Petrol Inj. atual do GNV;
- deadband não apaga evidência medida;
- UI da célula contém apenas os campos operacionais definidos acima;
- desempenho: nenhuma interpolação/predição pesada é executada por frame no hot path.

## Não objetivos

- Não criar uma segunda engine concorrente.
- Não reescrever a arquitetura inteira.
- Não voltar a usar tempo como validade da gasolina.
- Não inventar extrapolação ampla fora de suporte.
- Não automatizar escrita de Curva K/Mapa K.
- Não transformar a tela Aprender em diagnóstico técnico.

## Critério de fechamento

A fase só está pronta para APK quando a mesma branch/SHA provar: testes RED específicos -> GREEN focado -> FAST completo -> JVM/unit -> lint, mais revisão adversarial independente. O APK permanece gate separado e manual. Validação física no carro continua separada e não pode ser alegada por CI.
