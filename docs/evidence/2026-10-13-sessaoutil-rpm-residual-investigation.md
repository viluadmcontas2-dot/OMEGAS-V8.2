# OMEGAS Obsidian — AutoCal nativo, `Sessaoutil` e residual por RPM

Data da análise: 2026-10-13  
Branch de autoridade: `work/platina-obsidian-enxuto`  
HEAD usado no replay: `a567b8818fcc0247bb11f4e6b2e5025c3a8e6a0f`

## Resposta executiva

O `Sessaoutil.zip` é valioso e muda a qualidade da investigação. Ele não contém apenas telemetria: preserva snapshots dos buffers internos do AutoCal nativo, correlações de aquisição, contadores de banda, mudanças de `MUL_ACT`, resets e ciclos completos de AutoMatch.

O fato central observado é compatível com a intuição do proprietário: **uma mesma curva geométrica MAP × ms recebe pontos adquiridos em regimes de RPM distintos**. Isso aparece diretamente nos snapshots e em 53 eventos nos quais o próprio aplicativo registrou a correlação RPM da aquisição nativa.

Isso ainda não prova que exista uma segunda correção RPM estável. Quando sessão física e época de calibração são preservadas, o replay do residual classificou **zero grupos como `LOCAL`**. No corpus versionado, os 22 grupos permaneceram desconhecidos por falta de comparador, poucas amostras ou visitas instáveis. Portanto:

1. AutoCal nativo e Curva K continuam sendo a base.
2. O refino atual da Obsidian continua sendo o incumbente; o replay não autoriza substituí-lo.
3. RPM é um candidato real para explicar estrutura residual local, mas precisa ficar em modo shadow até repetir o efeito em visitas independentes e maduras.
4. Nenhuma gravação automática na ECU foi adicionada ou executada.

## Proveniência do `Sessaoutil`

- Pasta canônica do Drive: `SESSOES OMEGAS` (`1Lc94RKkdgSzY3UOKxGNpU-W-s0pxiVSE`).
- Arquivo: `Sessaoutil.zip` (`16mu0ywMhSDNyWscXd0AvLf3V0UazHS28`).
- Tamanho: 4.928.780 bytes.
- SHA-256: `ba3f31216330fea6fb83ec4f7a69a2529545b3792b38c206c5af8d1ac46e116c`.
- Integridade: ZIP externo e cinco ZIPs internos passaram no teste integral.
- Conteúdo: cinco sessões físicas, aproximadamente 92 minutos, 15.807 amostras de telemetria e 137 snapshots nativos do AutoCal.

| sessão física | SHA-256 do ZIP interno |
|---|---|
| `18-57-01-330_n001_88ac9f35` | `c1216658c60c28e092a44d59cfe42a1d14aa6f4c3560e1b9efd56f8efd60728c` |
| `18-59-24_f1dbfd35` | `8dbafcea15b15228098e513c1cdeed578b81b5a5e9602ef3bba4ff369c332af3` |
| `19-21-14-917_n001_2a46ed06` | `cd09911bcdddd69ce7cfbaebd6bb01afc3ca9633db78c962e73d3fc59079b85d` |
| `20-09-28_8c9e340c` | `2039e4e166378faaaba74eec11b2c7fa1d399683aa88623b977225a9f58da760` |
| `20-30-45_8c9e340c` | `099b33b557ac79742fedd88b680d9e7ebf069b0aa391be2bb3a4cea441562a17` |

As marcações `partial` dos snapshots não invalidam as curvas desta análise: os campos de buffer, contadores, `MUL_ACT` e limites usados aqui estavam `VALID`; o campo indisponível era `VECT_AUTOCAL_EE`.

## Reconstrução do AutoCal nativo

Na sessão longa de 53,1 minutos foram observados 93 snapshots, seis eventos explícitos de mudança de calibração nativa e dois ciclos completos de contador AutoMatch `0 → 1 → 2 → 3`. Os limites lidos da ECU incluem:

- RPM máximo para AutoCal: 3.000 RPM;
- diferença de rotação: 400 RPM;
- delta de rotação: 200 RPM;
- delta de MAP: `0,0498046875 bar`;
- delta de tempo de injeção de gasolina: `1 ms`;
- máximo de AutoMatch: 3.

O replay extraiu 281 mudanças de aquisição com contexto RPM: 53 correlações nativas explícitas e 228 associações conservadoras ao intervalo imediatamente anterior, exigindo mesmo combustível, MAP próximo e tempo próximo. O gráfico `native-curves-by-rpm.png` mostra que a curva MAP × ms mistura cores de RPM.

Há diferenças visíveis dentro de mesma sessão/época/banda. Porém, após exigir MAP a até 0,02 bar, diferença de RPM de pelo menos 500 e estágio do contador a no máximo uma amostra de distância, restaram 17 comparações: 11 positivas, quatro negativas e duas nulas. A mediana assinada foi `+0,0547 ms`, mas a faixa foi de `-0,1484` a `+0,2266 ms`. Nenhuma comparação repetida atingiu maturidade de contador suficiente para isolar RPM de histórico/transiente.

Conclusão: **a ramificação visual existe; sua causalidade exclusiva por RPM ainda não está provada**. Aquisição progressiva, transiente e histórico do buffer ainda são explicações concorrentes.

## Replay do residual GNV × gasolina

O replay imita a causalidade de `EquivalenceLedger` e a classificação de `EquivalenceAttribution`: três frames estáveis, referência de gasolina somente anterior, separação por época, mínimo de pares/visitas e comparador em outra coluna RPM no mesmo Tpet.

| conjunto | sessões | observações estáveis | pares causais | grupos | `LOCAL` |
|---|---:|---:|---:|---:|---:|
| corpus versionado | 45 | 43.066 | 8.255 | 22 | 0 |
| `Sessaoutil` isolado | 5 | 8.051 | 132 | 8 | 0 |

Motivos no corpus versionado: 10 com poucos pares, sete sem outra RPM no mesmo Tpet, quatro com visitas instáveis e um equivalente às demais colunas. Isso significa “evidência insuficiente”, não “RPM não importa”.

## River em modo shadow

Os modelos foram avaliados com River 0.26.1 por sessão física inteira: uma sessão só entra no treino depois que todas as suas previsões foram medidas. Foram avaliados 7.222 pares em dez sessões, sem vazamento de frames da sessão de teste.

| modelo | mediana | média | p90 | mediana por sessão | vitórias vs global |
|---|---:|---:|---:|---:|---:|
| incumbente/global | 3,325% | 4,736% | 11,004% | 2,651% | — |
| mediana por faixa RPM | **3,269%** | 4,659% | 10,865% | **2,282%** | 2/10 |
| RPM contínuo | 3,590% | 5,841% | 13,185% | 3,593% | 2/10 |
| River ARF shadow | 3,508% | **4,392%** | **8,483%** | 3,555% | 6/10 |

O River reduziu bem a cauda de erro, mas piorou a mediana e a consistência entre sessões. É promissor para detecção de contexto/drift, não está aprovado para sugerir correção por conta própria. O gráfico `model-comparison.png` documenta esse trade-off.

## Execução efetiva do REA

O REA 6.4.0 foi executado, não apenas localizado.

- Registro Codex/MCP: alinhado; catálogo com 170 ferramentas.
- JADX headless 0.7.1: operacional, JAR SHA-256 `6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75`.
- Ghidra 12.1.4: arquivos e decompilador nativo presentes, mas o provedor REA ficou indisponível porque o runtime Debian oferece JRE 21 sem `javac`; isso foi registrado como `config_drift`, sem alegar uso do Ghidra.
- APK disponível no runtime: SHA-256 `bd563553acfef57193697033e81c8bcd2776ff4af46adbfd6c23b76489a2ae86`, explicitamente da revisão antiga `39cbe363`, usado somente como baseline empacotado — não como prova do HEAD atual.
- Evidências REA/JADX: pacote `com.omegas.v7.test`, 2.566 classes; classe `AutoMatchRefinedEngine` encontrada e método `refine` decompilado. IDs: `ev_6a7cc7dec477587f4173a59696b2aa91566a2b017e7c59b35392d53b1ca691fb`, `ev_e3db6f59d89ecc30d6c2887292ba8462e47e0b8be29e5fab5e1638f35de99361`, `ev_b2682e2e8447eaf3c09db388a1c415c217d6e37ed045bf2cbbc0bbd22dd8981b`.

O método empacotado confirma buffers nativos de 18 bandas, eixo de 30 pontos em escala `/512`, K em escala `/16384`, ajuste monotônico e suavização/limites. Isso é evidência estática do APK antigo; a fonte versionada continua sendo a autoridade do HEAD.

## Algoritmo recomendado agora

```text
AutoCal nativo MAP × ms + Curva K
                ↓
refino atual da Obsidian (incumbente preservado)
                ↓
residual causal, separado por época de calibração
                ↓
RPM × MAP × Tpet como contexto local
                ↓
River shadow: predição, drift e incerteza
                ↓
gate determinístico: GLOBAL / LOCAL / DESCONHECIDO
                ↓
sugestão explicada + revisão humana + ACK + readback
```

River nunca deve redefinir a curva base. Uma sugestão local só deve existir após repetição em pelo menos três visitas independentes, comparador em outra RPM no mesmo Tpet, sinal estável e melhoria fora da sessão usada para aprender. Temperatura e pressão devem ser gates/contexto antes de virarem dimensões do modelo.

## Menor próximo experimento útil

Com uma calibração congelada e sem escrita automática:

1. adquirir gasolina antes e depois do bloco GNV;
2. alternar pelo menos três visitas em baixa RPM e três em RPM maior;
3. manter a mesma faixa MAP/Tpet, temperatura e pressão;
4. esperar o contador da banda amadurecer antes de comparar;
5. registrar snapshots nativos e telemetria em uma única época;
6. aprovar um efeito RPM apenas se o sinal repetir em outra sessão física.

Esse experimento falsifica diretamente a hipótese. Se o desvio desaparecer ao controlar maturidade e transiente, não há segunda superfície útil. Se repetir fora da sessão, o candidato RPM pode avançar de shadow para sugestão manual.

## Limites

- O replay não prova consumo, ausência de trancos ou equivalência física de combustão.
- Os ganhos relatados do refino atual são observação de campo do proprietário, não resultado deste replay.
- Não houve teste autorizado no veículo.
- Não houve escrita de Curva K/Mapa K, alteração de protocolo, backend Claude ou integração operacional Android.
