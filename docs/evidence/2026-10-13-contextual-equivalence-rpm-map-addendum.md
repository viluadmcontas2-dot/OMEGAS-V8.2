# Adendo — equivalência contextual correta: RPM × MAP → Tpet

Este adendo corrige a interpretação do replay causal anterior. Para investigação offline de equivalência, a gasolina não precisa ter sido coletada antes do GNV. A ordem só importa para o algoritmo online; cientificamente, observações anteriores e posteriores podem ser comparadas desde que pertençam à mesma época de calibração.

## Definição

Para cada combustível e época `e`, estimamos a superfície:

```text
T_f(r, m, e) = mediana de Tpet
               para combustível f,
               RPM próximo de r e MAP próximo de m
```

O residual de equivalência é:

```text
E(r, m, e) = ln(T_GNV(r,m,e) / T_GASOLINA(r,m,e))
```

Tpet é a resposta observada. Ele não é usado para escolher o par. O efeito local de RPM é a diferença de `E` entre regimes RPM no mesmo MAP e na mesma época.

## O que mudou no replay

- pareamento independente da ordem temporal;
- estado de pareamento: somente RPM × MAP;
- Tpet comparado proporcionalmente após o pareamento;
- fronteiras de calibração continuam obrigatórias;
- observações sobrepostas são consolidadas em visitas de cinco segundos;
- validação de modelos retém sessões físicas inteiras.

No corpus versionado, o replay anterior encontrou 8.255 pares direcionais, mas sua referência precisava existir antes do GNV. O replay contextual consolidado produziu 3.437 comparações independentes; **1.161 usam exclusivamente gasolina coletada depois do GNV** e seriam invisíveis sob a restrição anterior.

No `Sessaoutil`, foram obtidas 194 comparações, das quais 96 dependem de gasolina posterior.

## Bifurcação encontrada

Existe um candidato forte na linhagem `b1c4e83c`, época 2 e MAP em torno de `0,55 bar`:

| regime | gasolina Tpet | GNV Tpet | residual GNV/gasolina | repetição GNV |
|---|---:|---:|---:|---:|
| baixa rotação, ~850–1.000 RPM | ~5,193 ms | ~4,738 ms | aproximadamente −9,3% | 32 visitas, todas negativas |
| rotação maior, ~1.850–1.960 RPM | ~3,541 ms | ~3,705 ms | aproximadamente +3% a +5% | 7 visitas, todas positivas |

Portanto, no mesmo MAP, os dois regimes apresentam sinais opostos de erro de equivalência. Misturá-los numa correção puramente MAP × Tpet pode suavizar um regime contra o outro.

Esse resultado é mais forte do que uma bifurcação visual: o sinal se repete em visitas GNV distintas. Ainda existe uma limitação importante: a referência de gasolina nessa região é esparsa — seis observações consolidadas de baixa rotação e duas de rotação maior, cada conjunto concentrado em uma sessão física. O formato da bifurcação está sustentado; a magnitude final da correção ainda precisa de mais visitas em gasolina.

## MAP isolado versus RPM × MAP

Validação por sessão física completa:

| combustível | modelo | mediana | média | p90 | sessões retidas |
|---|---|---:|---:|---:|---:|
| gasolina | MAP | 6,502% | 12,105% | 38,874% | 7 |
| gasolina | RPM × MAP | 8,848% | 13,082% | 40,016% | 7 |
| GNV | MAP | 5,359% | 7,890% | 19,107% | 35 |
| GNV | RPM × MAP | **5,152%** | **7,097%** | **16,564%** | 35 |

RPM melhora a superfície GNV fora da sessão de treino, principalmente na cauda. Na gasolina, o modelo contextual piora porque o corpus tem pouca cobertura cruzada de RPM; isso é falta de dados de referência, não evidência de que RPM seja irrelevante.

## Conclusão corrigida

Há resultado conclusivo em três níveis:

1. A ordem de aquisição estava descartando equivalências válidas e não deve limitar o replay offline.
2. A relação MAP → Tpet muda materialmente com RPM; o AutoCal nativo continua sendo a base, mas uma única projeção mistura regimes.
3. Em pelo menos uma região real, o residual de equivalência muda de sinal entre baixa e média RPM no mesmo MAP. Essa região deve permanecer separada no refino.

Ainda não é conclusivo o valor exato que deve ser aplicado ao Mapa K. O próximo experimento mínimo é aumentar a cobertura de gasolina na mesma época, repetindo baixa e média RPM no MAP de 0,55 bar. Até isso ocorrer, o algoritmo pode detectar e explicar a bifurcação, mas deve manter a sugestão em shadow/manual.

Nenhuma escrita automática de ECU é autorizada por este resultado.

## Correção hierárquica posterior

O residual acima não deve ser aplicado diretamente ao Mapa K enquanto existir
erro global material. A decomposição e o teste com River sobre o alvo correto
estão registrados em
`docs/evidence/2026-10-13-global-first-hierarchical-residual.md`.

A decisão vigente é: Curva K global primeiro; nova época; somente então o
residual repetível por `RPM × MAP`. Não se aprende nem se preenche a malha de
144 células como condição para calibrar o veículo.
