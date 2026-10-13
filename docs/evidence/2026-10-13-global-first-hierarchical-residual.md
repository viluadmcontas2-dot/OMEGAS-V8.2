# AutoCal hierárquico — Curva K primeiro, RPM × MAP somente no residual

## Decisão corrigida pelo proprietário

O OMEGAS não deve aprender gasolina e GNV em cada uma das 144 células do Mapa K.
Esse caminho é lento, exige cobertura impraticável e repete o trabalho que o
AutoCal nativo já resolve de forma rápida no gráfico `MAP × Tpet`.

A hierarquia correta é:

```text
AutoCal nativo desenha as curvas MAP × Tpet
                 ↓
Curva K absorve o erro proporcional global
                 ↓
nova época de calibração e nova observação
                 ↓
RPM × MAP explica somente o residual local restante
                 ↓
sem suporte local suficiente = UNKNOWN, nunca preencher 144 células
```

O relato operacional de que o AutoCal resolve aproximadamente 80% e o refino
local os 20% restantes define a prioridade arquitetural. Os percentuais exatos
não são declarados como medição do corpus.

## Formulação

Para observações comparáveis no mesmo `RPM × MAP` e na mesma época:

```text
e = ln(Tpet_GNV / Tpet_gasolina)
```

O termo global é o centro robusto de `e` ao longo da posição correspondente da
curva nativa. Cada regime de RPM recebe um voto, para que uma região densamente
amostrada não transforme um defeito local em correção global:

```text
g(Tpet) = mediana robusta entre regimes comparáveis
```

O único alvo permitido para o refinador contextual é:

```text
l(RPM, MAP) = e - g(Tpet)
```

Se `|g| > 5%`, a prioridade é `GLOBAL_FIRST`, mesmo quando há sinal local. A
correção local fica retida até a Curva K ser revista e uma nova época fornecer
evidência. O mapa local exige vizinhança observada, pelo menos oito pares, três
visitas e sinal estável; fora dela o resultado é `UNKNOWN`.

## Resultado nas sessões OMEGAS versionadas

O replay independente da ordem produziu 126 regiões contextuais:

| classificação | regiões |
|---|---:|
| `GLOBAL_FIRST` | 55 |
| `LOCAL_RESIDUAL` | 2 |
| `ALIGNED` | 2 |
| `UNKNOWN` | 67 |

Isso não significa que apenas duas regiões possam ter comportamento por RPM.
Significa que, sob os gates atuais, somente duas podem ser chamadas de residual
local antes de uma nova passagem global; a maioria ainda está dominada pelo
global ou carece de cobertura independente.

### Bifurcação em MAP próximo de 0,55 bar

Na linhagem `b1c4e83c`, época 2:

| regime | erro bruto | termo global | residual pós-global | decisão |
|---|---:|---:|---:|---|
| ~850 RPM | −9,34% | −5,45% | −4,12% | Curva K primeiro |
| ~1850 RPM | +3,20% | −5,45% | +9,15% | Curva K primeiro; reavaliar depois |

A diferença entre regimes é real e material, mas o global ainda não está
resolvido. Aplicar primeiro o residual de +9,15% misturaria duas etapas e não
permitiria saber quanto realmente sobrou depois da nova Curva K.

### `Sessaoutil`

Nas 45 regiões obtidas do arquivo:

| classificação | regiões |
|---|---:|
| `GLOBAL_FIRST` | 13 |
| `LOCAL_RESIDUAL` | 1 |
| `UNKNOWN` | 31 |

O único candidato local direto está em `f1dbfd35`, época 2, aproximadamente
1850 RPM e 0,80 bar, com residual pós-global de −9,15%. Ele continua sendo
hipótese shadow/manual: não prova a semântica nem a magnitude de uma escrita no
Mapa K.

## River no alvo correto

River 0.26.1 foi executado como desafiante sobre `l`, não sobre o erro bruto.
As features foram apenas RPM, MAP, Tpet de referência e interação RPM×MAP; Tpet
do GNV e a razão-alvo não entraram como features. A validação reteve sessões
físicas completas e só as adicionou ao histórico depois da predição.

| modelo do residual | pares | sessões | mediana | média | p90 | vitórias por sessão contra global |
|---|---:|---:|---:|---:|---:|---:|
| global sem correção local | 2617 | 13 | **5,836%** | **6,798%** | **13,915%** | — |
| River ARF shadow | 2617 | 13 | 6,497% | 7,295% | 14,121% | 4/13 |
| vizinhança esparsa RPM×MAP | 2617 | 13 | 6,488% | 7,633% | 15,334% | 2/13 |

River não superou o baseline global fora da amostra. Portanto não há promoção
para Kotlin, APK ou sugestão operacional. Seu valor atual é de pesquisa em
shadow, principalmente para drift e incerteza após novas épocas mais bem
cobertas.

## Implementação experimental

- `contextual_equivalence_replay.py` agora emite
  `hierarchical-residual-groups.csv` e a alocação global/local/desconhecida.
- `hierarchical_residual_models.py` compara global, vizinhança esparsa e River
  sobre o residual correto.
- testes impedem que erro proporcional uniforme seja repartido por células;
  impedem sugestão fora da região observada; e impedem vazamento do alvo para as
  features do River.

Nenhum writer, protocolo Android, UI ou comando de ECU foi alterado. Não há
autorização de escrita automática nem alegação de melhora física no veículo.
