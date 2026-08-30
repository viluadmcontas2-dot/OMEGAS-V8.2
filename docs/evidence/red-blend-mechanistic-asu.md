# RED Blend — simulador mecanístico ASU offline

## Resultado

Foi implementado um método offline que transforma um par comparável gasolina/GNV em demanda relativa de calibração sem inventar causalidade, estado da ECU ou probabilidade de melhora.

Para uma condição física `RPM × MAP`:

`multiplicador combinado = Tinj_GNV_observado / Tinj_gasolina_referência`

Se o conjunto atual de calibração efetiva é:

`K_efetivo = CurvaK(Tinj_ref) × MapaK(RPM,Tinj_ref) / 100`

então o alvo mecanístico ideal é:

`K_efetivo_novo = K_efetivo_atual × multiplicador combinado`

O sinal tem interpretação direta: razão menor que 1 pede redução de combustível GNV; razão maior que 1 pede aumento. Isso depende de observação estável e comparável e não autoriza escrita automática.

## Separação Curva × Mapa

A identidade é calculada em log:

`ln(multiplicador combinado) = correção global por Tinj + residual local RPM×MAP`

- A Curva K recebe somente a tendência robusta repetida em pelo menos três regiões físicas e duas sessões anteriores na mesma faixa de `Tinj_ref`.
- O Mapa K recebe o residual depois da tendência global.
- Sem suporte transversal, a Curva permanece neutra e toda a demanda fica local.
- Evidência densa mantém peso dentro de cada região/sessão; regiões e sessões independentes governam transferência.
- A projeção final para a ECU usa os eixos físicos `RPM × Petrol Inj.`; `RPM × MAP` continua sendo a coordenada de equivalência.

Essa decomposição impede dupla contagem porque:

`multiplicador_curva × multiplicador_mapa = multiplicador_combinado`

## Prova sobre os dados já governados

O simulador usou apenas o fixture privacy-safe já presente no repositório. A referência gasolina de cada episódio GNV foi calculada pelo RED neighbor baseline vencedor, usando somente sessões de gasolina anteriores.

- 1.442 episódios GNV disponíveis;
- 1.273 pares comparáveis;
- 17 sessões GNV independentes;
- zero leakage temporal;
- 1.235 pares com suporte transversal suficiente para decomposição global/local;
- 38 pares mantidos inteiramente locais;
- mediana do multiplicador combinado: `0,9500`;
- mediana ponderada pela massa local: `0,9471`;
- P10: `0,8824`; P90: `1,0190`;
- deadband de ±2%: 992 demandas de redução, 158 dentro da faixa e 123 demandas de aumento.

A mediana perto de −5% não é uma recomendação global. A distribuição possui regiões com sinais opostos, e as fotos mostram desvios locais muito maiores.

## Falsificador observado nas fotos

As equivalências visíveis incluem `4,19→3,49` (−16,8%), `4,50→4,43` (−1,7%), `5,72→5,14` (−10,3%), `5,21→5,69` (+9,3%) e `5,21→7,06` (+35,6%).

Os dois últimos pares compartilham aproximadamente o mesmo `Tinj_ref`, mas exigem correções muito diferentes. Um único ponto da Curva K não consegue representar ambos; MAP/regime/estado oculto precisa explicar a diferença. Isso dá prioridade ao residual espacial e à detecção de regime, não a uma correção global cega.

## Experimentos recomendados

1. **Matched-MAP dentro do mesmo Tinj — TEST primeiro.** Comparar regiões com `Tinj_ref` semelhante e MAP diferente em sessões futuras. Falsifica o residual local se a direção não persistir fora da sessão.
2. **Ablation Curva+Mapa contra RED — TEST.** Em walk-forward cego, comparar RED puro com a decomposição mecanística. Só avança se mediana não piorar e P90/P95 não piorarem materialmente.
3. **Repetição por época da mesma região — TEST.** Verificar se o mesmo `RPM×MAP` preserva a correção após mudança de sessão; mudança de modo vira regime oculto, não média única.
4. **Resposta ao estado real de calibração — DEFER.** Quando Curva K 30 e Mapa K 12×12 estiverem explicitamente ligados ao mesmo instante da observação, testar `K_novo/K_antigo` contra a mudança posterior do erro.
5. **AutoCal 18 zonas como explicador — DEFER.** Testar se zona/buffer/AutoMatch explica multimodalidade somente após o contrato 18-zonas versus vetores 30-pontos estar provado por fixture.

## Limites

Este slice é `MECHANISTIC_ASU_SIMULATION_OFFLINE_NOT_PRODUCTION`. Ele não altera Android, não emite valor absoluto sem estado atual da calibração, não calcula `P(improve)`, não prova causalidade física e não escreve na ECU.

Comando de verificação focada:

`python -m unittest lab.red_blend.test_mechanistic_calibration -v`
