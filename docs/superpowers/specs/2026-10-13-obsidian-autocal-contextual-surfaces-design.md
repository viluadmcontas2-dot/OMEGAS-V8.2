# OMEGAS Obsidian — AutoCal contextual e superfícies de equivalência

**Estado:** proposta para revisão do proprietário  
**Branch de referência:** `work/platina-obsidian-enxuto`  
**HEAD de referência:** `38d51655be78efaacdf770a55302707dde11a723`  
**Escopo:** replay offline e modo shadow; nenhuma escrita automática na ECU

## 1. Decisão central

O AutoCal nativo e a Curva K são a base do sistema, não um componente a ser substituído e nem uma autoridade isolada.

O princípio nativo é simples e forte:

1. adquirir a relação de referência da gasolina;
2. adquirir a relação observada no GNV;
3. comparar as duas curvas;
4. aproximar a curva do GNV da curva-base por uma correção coerente.

O OMEGAS deve preservar esse princípio e ampliá-lo. Em vez de manter somente uma relação `MAP × tempo`, ele deve aprender uma família de curvas ou superfícies condicionadas pelo regime real do motor: `RPM × MAP × Tpet × contexto × época de calibração`.

Essa ampliação não autoriza o aplicativo a reescrever a calibração por conta própria. O resultado continua sendo uma sugestão explicada, sujeita a revisão humana, ACK e readback.

## 2. Arquitetura aprovada conceitualmente

```text
AutoCal nativo + Curva K
        ↓
Inteligência contextual própria do OMEGAS
(famílias de curvas e superfícies por regime)
        ↓
Residual GNV × gasolina
por RPM, MAP, Tpet, temperatura, pressão e época
        ↓
River em shadow
predição + drift + incerteza
        ↓
Árbitro determinístico
global × local × desconhecido
        ↓
Sugestão explicada ao operador
        ↓
Revisão humana + ACK + readback
```

O River é um desafiante e detector em shadow. Ele não decide a calibração e não produz bytes para a ECU. Modelos descobertos por River, Codex ou Claude só podem influenciar sugestões depois de serem destilados em uma regra determinística, testável e aprovada em sessões físicas independentes.

## 3. Formulação matemática

### 3.1 Curva-base nativa

Para uma época de calibração `e`, a aquisição nativa fornece uma referência observável:

```text
Cₚ,e(MAP) → Tpet de referência
```

e uma aquisição correspondente durante GNV:

```text
Cɢ,e(MAP) → Tpet observado enquanto o motor opera no GNV
```

Os buffers nativos e a Curva K permanecem a referência primária. A equação já versionada no motor refinado,

```text
K_alvo(Tp) = K(Tg) · Tg / Tp
```

é uma hipótese operacional explícita do aplicativo. Ela não deve ser confundida com prova completa da matemática interna do ProgBase ou com equivalência de combustão.

### 3.2 Ampliação contextual

O OMEGAS estima superfícies condicionais:

```text
C_f,e(MAP, RPM, Tpet, θ)
```

onde `f` é o combustível e `θ` contém somente variáveis realmente disponíveis e validadas, por exemplo temperatura, pressão e estado transitório.

Uma observação útil de equivalência exige pareamento contextual entre gasolina e GNV dentro da mesma época de calibração. Um residual candidato é:

```text
r = log(Tpet_GNV_contextual / Tpet_gasolina_contextual)
```

ou sua diferença robusta em milissegundos quando o domínio logarítmico não for apropriado.

Há duas estruturas diferentes que o algoritmo não pode misturar:

- **estrutura da referência:** como `Tpet` muda com RPM e MAP mesmo na gasolina;
- **erro de equivalência:** diferença entre gasolina e GNV em condições comparáveis.

Uma segunda trajetória no gráfico pode ser estrutura normal da referência e não defeito de calibração.

### 3.3 Decomposição da recomendação

O árbitro determinístico testa:

```text
r(contexto) = g(Tpet) + l(RPM, MAP, Tpet) + ruído
```

- `g`: componente global candidato a Curva K;
- `l`: componente local candidato a Mapa K, somente se a semântica e a resposta forem identificáveis;
- `desconhecido`: evidência insuficiente, confundimento por época/transiente ou incerteza excessiva.

Não se presume valor neutro do Mapa K, relação aditiva/multiplicativa ou equivalência entre combinações de mapa e curva. Saturação em 255 só pode entrar em otimização depois que essas propriedades forem demonstradas.

## 4. Evidência empírica já obtida nas sessões OMEGAS

### 4.1 Corpus e bifurcação

- 45 arquivos de eventos de sessão distintos foram inventariados no corpus acessível.
- 23 sessões não apresentaram intervenção de calibração e foram elegíveis para análises limpas.
- 17 sessões cobriram os critérios da comparação ampla, com 13.658 frames elegíveis e 1.712 visitas agregadas.
- Em 14 sessões houve sobreposição suficiente de RPM no mesmo intervalo de MAP.
- Em 13 das 14, a população de RPM mais alta ficou abaixo da população de RPM mais baixa.
- A diferença pareada mediana `alta RPM − baixa RPM` foi `−1,22752 ms`.

O ponto de separação emergente variou aproximadamente de `1331,5` a `1384 RPM`, com mediana `1378,25 RPM`. Isso é evidência contra fixar 1.800 ou 2.000 RPM como fronteira universal. A separação deve emergir da distribuição de cada sessão/época ou ser representada continuamente.

### 4.2 Modelos concorrentes da referência

Validação por sessão física inteira, sem misturar partes da mesma condução entre treino e teste:

| Modelo | Mediana MAE | Resultado |
|---|---:|---|
| A — somente MAP | 0,33694 ms | referência simples |
| B — MAP + RPM aditivo | 0,29827 ms | venceu 16/17 sessões |
| C — duas famílias com corte | 0,31220 ms | venceu 14/17, mas impõe fronteira rígida |
| D — multivariável contextual | 0,32849 ms | venceu 14/17, porém foi menos estável |

Para B contra A, Wilcoxon `p = 0,000144958`, diferença pareada mediana `−0,02281 ms` e intervalo bootstrap de 95% da mediana `[−0,02853; −0,01371] ms`.

Conclusão atual: uma correção contínua/aditiva por RPM é o primeiro candidato determinístico. Duas curvas rígidas são uma visualização útil, não uma regra comprovada.

### 4.3 Evidência direta nos buffers nativos

Na sessão física `session_2026-09-24_20-59-19_b1c4e83c`, seis snapshots ocorreram sem quebra de época ou escrita observada. Exemplos:

| Banda | MAP | Tpet inicial → final | RPM contextual mediana |
|---:|---:|---:|---:|
| 4 | 0,445/0,438 | 3,984 → 3,650 ms | 2111 |
| 5 | 0,474 | 4,234 → 4,074 ms | 2183 |
| 8 | 0,633 | 5,352 → 4,956 ms | 2067 |
| 9 | 0,687 | 5,844 → 5,634 ms | 2224 |

Isso sustenta tratar o AutoCal nativo como processo vivo de aquisição e convergência, e não como uma tabela estática a ser ignorada.

### 4.4 A curva nativa não deve ser substituída

Em holdout de sessões físicas inteiras, usando apenas gasolina e sempre um snapshot causal anterior:

- 7 sessões continham visitas elegíveis;
- 86 visitas foram encontradas e 80 avaliadas em 6 holdouts;
- curva nativa: erro absoluto mediano `0,07099 ms`, média `0,16697 ms`, p90 `0,43355 ms`;
- correção River linear: mediana `1,1889 ms`;
- River HATR: mediana `0,5899 ms`;
- River ARF: mediana `0,8165 ms`.

Os modelos online pioraram a base nativa nesse teste. Portanto, River não deve reconstruir nem substituir `Cₚ`. Seu lugar é aprender o residual contextual onde houver evidência pareada.

### 4.5 River no residual GNV × gasolina

Experimento com River `0.26.1`, código de origem no commit `6af83abba269dbdaf38e9c0eaf61d82c451ad698`, licença BSD-3. Foram usadas 23 sessões limpas, 1.984 visitas e 22 holdouts de sessão física inteira. O alvo foi a razão logarítmica entre o comando de gasolina observado durante GNV e a curva nativa de gasolina.

| Desafiante | Mediana do erro absoluto no log | Resultado |
|---|---:|---|
| zero residual | 5,17498% | baseline |
| residual global | 5,6666% | pior |
| residual por banda | 6,2759% | pior |
| HATR | 6,2799% | pior |
| ARF estável | 4,35951% | venceu 13/22 sessões |

O ARF estável obteve média `6,69585%` e p90 `14,9851%`, contra média `6,99857%` e p90 `15,58266%` do baseline. O ganho é real, mas modesto e não uniforme.

A primeira configuração adaptativa explodiu numericamente ao receber RPM cru e folhas adaptativas. Escalonamento das entradas e folhas por média estabilizaram o teste. Essa falha é parte da evidência: River sem limites e normalização não é aceitável como autoridade de calibração.

O pacote Python também não é adequado para incorporação direta no Android: River ocupa cerca de 9,6 MB, além de NumPy (~42 MB), SciPy (~108 MB), Narwhals (~5,2 MB) e binários nativos específicos. River deve servir inicialmente como oráculo de pesquisa/replay; o algoritmo aprovado deve ser portado de forma pequena e determinística para Kotlin.

## 5. Máquina de estados shadow

Cada amostra recebe:

- identidade da sessão física;
- combustível;
- época de calibração;
- snapshot causal da curva;
- MAP, RPM, Tpet e contexto validado;
- estado estacionário/transitório/desconhecido;
- qualidade e proveniência.

Uma escrita, readback diferente ou mudança comprovada da calibração fecha a época atual. Dados de épocas diferentes não são pareados como se pertencessem ao mesmo sistema.

O pipeline shadow:

1. preserva a aquisição nativa e sua curva;
2. agrega visitas estáveis, sem transformar frames correlacionados em amostras independentes;
3. encontra vizinhanças comparáveis de gasolina e GNV;
4. estima residual e incerteza;
5. mantém River como desafiante online e detector de drift;
6. compara River com baselines determinísticos;
7. classifica o padrão como global, local ou desconhecido;
8. produz uma explicação, nunca uma gravação.

## 6. Gates determinísticos

Uma sugestão só pode existir se todos os gates relevantes passarem:

- mesma época de calibração ou transformação causal comprovada;
- cobertura mínima de visitas e de tempo, não apenas frames;
- replicação em mais de uma sessão física quando a afirmação for geral;
- estabilidade fora de transientes;
- ganho em holdout por sessão física inteira;
- intervalo de incerteza que exclua efeito irrelevante;
- ausência de saturação/ambiguidade não resolvida;
- efeito contrafactual esperado da mudança;
- preservação das regiões já coerentes.

Falhar em qualquer gate relevante resulta em `DESCONHECIDO`, com a causa mostrada ao operador.

## 7. Curva K, Mapa K e limites de atuação

- Desvio suave que acompanha `Tpet` em vários regimes é candidato global para Curva K.
- Desvio reproduzível restrito a uma vizinhança de `RPM × MAP` é candidato local para Mapa K.
- Separação causada apenas pela referência natural da gasolina não é correção.
- Confundimento por temperatura, pressão, transiente ou época é desconhecido até ser desambiguado.
- Redistribuição entre Curva K e Mapa K perto de 255 permanece hipótese não identificada.

Antes de sugerir qualquer redistribuição, devem ser provados valor neutro, interpolação, composição mapa-curva, limites e resposta das regiões vizinhas. Se múltiplas decomposições produzirem a mesma saída observável, o sistema deve declarar não-identificabilidade.

## 8. Papel do Claude

Claude pode atuar futuramente como pesquisador de hipóteses:

- receber resumos compactos e evidências, não controle da ECU;
- propor modelos concorrentes e experimentos de falsificação;
- ajudar a explicar casos complexos;
- nunca fornecer diretamente uma calibração executável;
- não ter chave embutida no APK;
- não iniciar backend nesta fase.

O valor só será demonstrado se hipóteses geradas por Claude superarem o processo determinístico offline em testes independentes. Até lá, a arquitetura funciona integralmente sem API externa.

## 9. Plano experimental após aprovação deste desenho

### Fase 1 — Replay científico reproduzível

Versionar manifesto, extratores, gráficos e recibos dos experimentos acima. Incluir snapshots causais, identificação de épocas e holdout por sessão física.

### Fase 2 — Contrato Kotlin em RED

Criar testes falhando para um componente shadow mínimo que:

- preserve a curva-base nativa;
- separe referência de residual;
- não imponha corte fixo de RPM;
- produza `GLOBAL`, `LOCAL` ou `UNKNOWN` com justificativa;
- jamais chame writers.

### Fase 3 — Motor shadow mínimo

Implementar somente o necessário para satisfazer os testes. O componente observa eventos existentes, mantém épocas e emite artefatos de diagnóstico. Não altera protocolo, aquisição nativa, Curva K ou Mapa K.

### Fase 4 — Comparação de desafiantes

Rodar baselines robustos e modelos River contra o mesmo replay congelado. Só destilar um modelo para Kotlin se ele vencer de forma reproduzível em sessões independentes e mantiver limites numéricos.

### Fase 5 — Sugestão explicada

Somente após identificação suficiente da resposta da calibração, expor uma sugestão manual com evidência, incerteza, regiões afetadas e resultado esperado. Aplicação continua dependente de revisão humana, ACK e readback.

## 10. Critérios de aceitação

- Nenhuma mudança em writer ou comando MP48.
- Nenhum APK necessário para provar o modelo offline.
- AutoCal e Curva K nativos preservados como base.
- Nenhuma fronteira fixa de RPM sem demonstração independente.
- Validação por sessão física, sem vazamento entre partes.
- Épocas de calibração obrigatórias.
- River sempre shadow e comparado com baseline simples.
- `UNKNOWN` é resultado legítimo e obrigatório quando faltar evidência.
- Sugestão local somente depois de identificar a semântica do Mapa K.
- Toda conclusão rastreável a eventos reais versionados/autorizados.

## 11. Questões que permanecem abertas

1. A semântica física e numérica exata de cada célula do Mapa K ainda não está demonstrada.
2. A composição Curva K × Mapa K e o valor neutro efetivo ainda precisam de evidência binária e de replay.
3. Nem todas as sessões possuem contexto suficiente de temperatura/pressão para separar seus efeitos.
4. O ganho modesto do ARF precisa sobreviver a um corpus congelado e a ablações antes de qualquer port.
5. Equivalência de tempos de injeção não prova, sozinha, equivalência de combustão.

## 12. Decisão solicitada ao proprietário

Confirmar este contrato: o OMEGAS preserva a inteligência nativa de duas curvas como base e a amplia por superfícies contextuais; River pesquisa o residual em shadow; um árbitro determinístico decide se há evidência global, local ou desconhecida; qualquer ação continua manual e verificável.
