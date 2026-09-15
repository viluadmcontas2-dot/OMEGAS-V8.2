# OMEGAS VERDE — Confidence Surface offline

**Resultado científico: FAIL. Runtime não autorizado por este estudo.**

WorkUnit `VERDE-CONFIDENCE-OFFLINE-001`, [issue #47](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/issues/47). Autoridade: GitHub remoto, exclusivamente `OmegasVerde`. Base anterior à missão: `9575d5ee944bc520860c9985adc2c139973d94fb`.

## Resultado

O score selecionado separou segurança no LOSO, mas a ordenação não se manteve no holdout externo. TRUST teve apenas duas regiões externas, acertou uma pelo critério combinado e errou o único caso direcional avaliável. A incerteza estatística é grande: isto reprova a promoção do candidato, sem provar que toda Confidence Surface seja inviável.

| Fase / classe | N regiões por sessão | Fontes de avaliação | Cobertura | Safe-rate | MAE ms | P90 ms | Direção correta |
|---|---:|---:|---:|---:|---:|---:|---:|
| LOSO TRUST | 10 | 7 | 2,75% | 90,00% | 0,115554 | 0,257180 | 4/4 |
| LOSO PROVISIONAL | 123 | 16 | 33,88% | 73,17% | 0,169762 | 0,383232 | 35/57 |
| LOSO WAIT | 230 | 14 | 63,36% | 53,04% | 0,381166 | 0,862992 | 98/159 |
| 21-25 TRUST | 2 | 1 | 0,91% | 50,00% | 0,187708 | 0,288078 | 0/1 |
| 21-25 PROVISIONAL | 47 | 1 | 21,36% | 65,96% | 0,383718 | 0,454363 | 25/29 |
| 21-25 WAIT | 171 | 1 | 77,73% | 38,60% | 0,865714 | 1,386630 | 89/141 |

O bootstrap LOSO por sessão (2.000 réplicas, seed 470015) deu intervalo percentil de 95% de **+0,06389 a +0,56385** para safe-rate TRUST menos WAIT. É evidência interna pós-seleção, não uma estimativa externa sem viés de seleção. Nenhum frame aleatório foi usado como unidade principal de validação.

### Posterior descritivo externo

Critério combinado: erro absoluto <= 0,30 ms e direção correta quando |residual verdadeiro relativo ao prior carregado| > 0,12 ms. Posterior de safe-rate: `Beta(k+0.5,N-k+0.5)`.

| Classe | Posterior | Intervalo central 95% |
|---|---|---|
| TRUST | Beta(1,5; 1,5) | 6,08%–93,92% |
| PROVISIONAL | Beta(31,5; 16,5) | 51,78%–78,24% |
| WAIT | Beta(66,5; 105,5) | 31,54%–46,03% |

Esses intervalos tratam regiões como ensaios Bernoulli e são somente descritivos. Regiões da mesma viagem são correlacionadas; há **uma** sessão externa, não 220 replicações independentes.

## Corpus e deduplicação

- Origem única dos registros: `G:\Meu Drive\OMEGAS`.
- 36 ZIPs pré-holdout enumerados, 5 cópias exatas por SHA-256; 31 arquivos distintos agrupados em 26 IDs reais de sessão.
- 215.660 registros de telemetria após deduplicação por sessão/frame; 14.789 frames gasolina elegíveis, provenientes de 16 sessões; 363 regiões por sessão para LOSO.
- As 10 sessões restantes foram inventariadas e não entraram como evidência gasolina elegível.
- 19-12 está incluído: 390 frames gasolina aceitos. O valor de S19=1,065612 é o prior pré-holdout fornecido pelo proprietário, explicitamente registrado; não foi substituído pelo oracle externo.
- 21-25: 6.777 registros (6.775 gasolina, 2 GNV), 3.104 frames gasolina elegíveis, 220 regiões.
- Cópias parciais da mesma sessão compartilham uma unidade de independência. Segmentos em cache são identificados por sessão, nome, CRC e tamanho; os ZIPs e caches usados têm SHA-256 no manifest.
- Intervenções K foram inventariadas a partir dos estados gravados `BATCH_WRITING`, `WRITING_POINT`, `BATCH_CONFIRMED`, verificações e falhas. As observações detalhadas estão em `corpus_audit.json`. Não constituem uma nova prova causal nem validação física.

Elegibilidade fixa deste ensaio: gasolina, `SAMPLE_ACCEPTED`, plausibilidade não falsa, RPM 600–4500, MAP 0,20–1,05 bar, petrol 0,7–30 ms, água >=40°C. Regiões: bins de 100 rpm × 0,025 bar; coordenadas do representante são de um frame real próximo da mediana espacial, alvo é a mediana de petrol dos frames aceitos da região. A distância usa os representantes reais retidos; pode superestimar conservadoramente a distância até o conjunto completo de frames.

**Limitação material de continuidade:** este subconjunto de exportações nativas elegíveis não reproduziu a tabela histórica de quatro fontes citada no briefing. Não foram importadas superfícies aprendidas, caches sem proveniência, ZIPs agregados/nested ou novos decodes de Portmon. O resultado pertence ao corpus explicitamente manifestado; não substitui a pesquisa histórica ausente. O filtro de aceitação vem de diferentes versões do gravador e pode excluir observações úteis na estrada.

## Fórmula e gate congelados

Para cada fonte, ajustar somente o residual local, com escalas 240 rpm / 0,06 bar, até 60 vizinhos em distância normalizada <=2,5, pesos `exp(-d²/2)`, ridge 0,001 nas cinco colunas não constantes. Base `[1,u,v,u²,v²,uv]`; intercepto não penalizado. Menos de seis regiões: média ponderada explícita. Combinação entre fontes: mediana das estimativas por fonte. Sem suporte: residual estimado zero, referência prior disponível, autoridade WAIT.

Cada fonte é normalizada pela mediana de `petrol/F2` de sua gasolina aceita, com a exceção explícita S19 fornecida pelo proprietário. Cada alvo LOSO usa S da fonte cronologicamente anterior (primeiro alvo: 1). Fontes de treino de qualquer data anterior ao holdout podem participar do LOSO; portanto não é um replay prospectivo completo. No externo, `S_carry=1.065612` em todas as consultas. As colunas diagnósticas S/residual calculadas sobre o alvo não participam das predições; a invariância foi testada alterando artificialmente essas colunas.

F2 permaneceu congelada, com coeficientes `[d,a,b,c,e,f,g,h,i,j,k]`:

`[2.14396620,0.34083653,1.87748601,-0.66741389,-0.54659976,1.24856920,-0.32307503,1.27193350,2.00071259,0.47671656,-0.25959427]`.

Para `n` fontes, `k` sinais majoritários entre as estimativas não nulas e `m` estimativas com sinal não nulo:

```
d = sqrt(((rpm-rpm_real)/240)^2 + ((MAP-MAP_real)/0.06)^2)
D = exp(-min(d)^2/2)
N = min(n/4,1)
p = P(Beta(k+0.5,m-k+0.5) > 0.5)
J = max(0,2*p-1)
MADs = 1.4826 * median(abs(residual_session - median(residual_session)))
R = exp(-MADs/0.25)
M = mean_over_sources(min(nearby_5_second_region_visits,4)/4)
C = exp((ln(max(D,1e-15)) + ln(max(N,1e-15))
        + 2*ln(max(J,1e-15)) + 2*ln(max(R,1e-15))
        + ln(max(M,1e-15)))/7)
```

Sem fontes, os componentes de suporte são zero. Zero residual é neutro para o posterior de sinal. Uma visita de cinco segundos por região é uma medida de maturidade, não uma sessão independente. A fração de sinais e a dispersão são calculadas entre estimativas de fontes; não são nova observação física.

- **TRUST:** C>=0,90, n>=3 e p>=0,80.
- **PROVISIONAL:** se não TRUST, C>=0,70 e n>=2.
- **WAIT:** demais consultas, inclusive referência disponível sem autoridade suficiente.

Compararam-se exatamente três famílias: geométrica balanceada, geométrica com pesos `[1,1,2,2,1]`, mínimo. Cada uma recebeu os três pares prefixados `(0,50;0,70)`, `(0,60;0,80)`, `(0,70;0,90)`. Seleção lexicográfica: suporte mínimo de 10 regiões e duas sessões por classe, safe-rate macro por sessão em TRUST, diferença TRUST–WAIT, menor MAE TRUST. Não houve busca adicional de pesos, kernels ou thresholds após o congelamento. Estes números são de pesquisa, não constantes de produção.

## Ablation e falsificação

| Remoção / controle | N TRUST | Safe-rate TRUST | MAE TRUST ms |
|---|---:|---:|---:|
| Nenhuma | 10 | 90,0% | 0,115554 |
| Distance | 11 | 81,8% | 0,119090 |
| Independent source count | 8 | 100% | 0,064434 |
| Sign consensus | 13 | 92,3% | 0,085821 |
| MAD/spread | 81 | 70,4% | 0,192485 |
| Maturity | 8 | 100% | 0,064434 |
| Permutar features entre regiões | 10 | 70,0% | 0,242226 |

MAD/spread e o controle permutado mostram informação útil internamente. Distance apresenta perda menor ao ser removida. Não se demonstrou ganho incremental de source count ou maturity neste corpus. Remover sign melhora algumas métricas de erro/safe-rate, mas direção TRUST cai de 4/4 para 4/5. Não há base para dizer que todas as features são indispensáveis.

As ablations mantêm thresholds e renormalizam os pesos restantes; mudam também cobertura e composição das classes. Portanto não isolam utilidade a cobertura igual. As amostras pequenas impedem uma redução de complexidade definitiva. Nenhuma ablation foi selecionada após ver o externo.

## Sensibilidade e reliability

Erro absoluto: 0,20 / 0,25 / 0,30 / 0,35 / 0,40 ms. Deadband: 0,08 / 0,10 / 0,12 / 0,15 / 0,20 ms. Classes permanecem congeladas.

- Pré-holdout: ordem estrita de safe-rate TRUST > PROVISIONAL > WAIT em **25/25** combinações.
- Externo: ordem estrita em **0/25**; TRUST > WAIT em 25/25, mas TRUST não supera PROVISIONAL.
- Reliability LOSO: faixa 0,5–0,6 tem 56,9% safe; 0,8–0,9 tem 71,9%; 0,9–1,0 tem 90,0%. Existem inversões locais e bins com N muito pequeno.
- Reliability externo: 0,8–0,9 tem 81,25% safe (N=16); 0,9–1,0 tem 50,0% (N=2). O topo não sustenta calibração externa.

O score é um índice epistemológico, não uma probabilidade calibrada de segurança. Não foi aplicado calibrador opaco nem pós-processamento externo.

## Auditoria MAP≈0,775

Os quatro pontos foram classificados antes de abrir o externo:

| RPM | Score | Classe | Sinais + / - entre estimativas | Residual previsto ms | Residual externo mediano próximo ms |
|---|---:|---|---|---:|---:|
| 1900 | 0,5910 | WAIT | 3 / 6 | -0,01836 | +0,44237 |
| 2100 | 0,3971 | WAIT | 4 / 5 | -0,06596 | -0,01765 |
| 2300 | 0,4302 | WAIT | 5 / 4 | +0,05551 | +0,01664 |
| 2500 | 0,4380 | WAIT | 5 / 4 | +0,06828 | -0,25330 |

As estatísticas externas usam regiões retidas dentro de ±100 rpm e ±0,025 bar; não são medições exatas nos quatro pontos. Em 2300, a mediana +0,01664 está dentro do deadband e não confirma o sinal negativo relatado no briefing. Não alterar essa definição depois do holdout para tentar reproduzir o sinal esperado.

2500 recebeu autoridade baixa, conforme desejado. Contudo, o consenso entre nove estimativas obtidas numa vizinhança ampla não reproduz o consenso das fontes históricas diretas. `map_0775_direct_observations.csv` explicita o subconjunto de observações reais mais próximas, separado da interpolação. Essa diferença limita a validade científica da feature de consenso; não foi corrigida usando o holdout.

## Holdout e proveniência temporal

- Frozen gate: `4dcd64dc1681e34841b01c54f050b002efd7702ae68d693886496e1a414f4a76`.
- Congelamento: **2026-09-15 14:18:21.885256 UTC** (11:18:21 BRT).
- Commit remoto imutável anterior à abertura: `946373c179a1ee313a3c66ecc90a3f53a0881324`.
- Abertura do ZIP iniciada: **2026-09-15 14:22:54.257806 UTC**.
- Avaliação registrada: **2026-09-15 14:24:24.774333 UTC**.
- SHA-256 ZIP 21-25: `95c8fcf4637e1fb3dbe81e4f6892a2bb8eb8e697c5ca114e8813ba0eae91ea5b`.
- Gate e executores foram recuperados por SHA do remoto e comparados byte a byte antes da abertura.
- `freeze` lê somente `preholdout_frames.csv.gz`, e o builder pré-holdout exclui o nome 21-25 e rejeita sessão/timestamps no embargo. O manifest congelado contém somente inputs anteriores.
- O ZIP externo foi descompactado em streaming uma vez. A verificação posterior reutilizou o cache, sem nova seleção.

**Limite de cegamento:** o briefing já continha S oracle e alguns sinais externos. Portanto há prova operacional de isolamento de arquivos/seleção e ordem temporal nesta execução, mas não se pode certificar cegamento humano absoluto, ausência de conhecimento externo anterior, nem ausência de acesso por outros processos. Esses valores externos informados não foram introduzidos na seleção do script. Não chamar isso de holdout historicamente virgem.

## Verificação e reprodução

Fonte científica no GitHub; diretório de execução/cache sem checkout: `E:\Documentos\Documentos\MULTIUSO GPT\confidence-surface-offline`. Um checkout preexistente foi consultado em leitura no bootstrap; após a correção do proprietário, seu uso foi encerrado. Nenhum checkout, branch switch, worktree ou clone foi criado ou alterado.

Sequência do experimento original (o replay pré-holdout foi executado pelo mesmo harness Python posteriormente materializado em `verify_replay.py`):

```
python build_cache.py
python confidence_surface.py check
python confidence_surface.py freeze
python verify_replay.py preholdout > verification_preholdout.log 2>&1
# Publicar/verificar frozen_gate e seu SHA remoto ANTES da linha seguinte.
python build_cache.py --holdout
python confidence_surface.py evaluate
python -m unittest -v test_confidence_surface > tests.log 2>&1
python verify_replay.py external > verification_external.log 2>&1
python audit_corpus.py
```

O estudo entregue já tem freeze/evaluate concluídos: não executar novamente esses comandos na pasta original. Para reproduzir a entrega, recuperar os scripts do SHA remoto final, colocar os artefatos publicados em `results/`, reutilizar o cache autorizado em `cache/` e executar somente os dois modos de `verify_replay.py`, os testes e `audit_corpus.py`, com os redirecionamentos acima, num diretório de executor com `verification/` inicialmente vazio. Os modos verificam o hash do gate **original publicado**; não substituir esse gate por um novo freeze de timestamp diferente. Os entrypoints normais recusam segunda execução. Se o cache não estiver disponível, o builder reconstrói os dados dos mesmos ZIPs manifestados; a decodificação externa exige primeiro o gate original. Alteração do corpus, dos scripts congelados ou de seus hashes invalida a reprodução e exige diagnóstico, nunca tuning externo.

Resultados verificados: 10 testes sintéticos PASS; seis checks básicos PASS; preholdout reexecutado a partir do cache com todos os hashes iguais, exceto timestamp do novo recibo de freeze; cinco CSVs externos reproduzidos byte a byte; campos oracle diagnósticos do alvo comprovadamente não influenciam a predição; repetição normal de freeze/evaluate rejeitada. Autorrevisão do principal com Codex Engineering Guardrails e Superpowers; não se declara revisão independente por outro agente.

Os hashes pré-holdout em `frozen_gate.json` referem-se aos arquivos no commit de congelamento. `sensitivity.csv`, `reliability.csv` e `map_0775_audit.csv` receberam linhas/colunas de avaliação posteriormente; seus hashes finais estão no `manifest.json`. O próprio frozen gate não foi alterado.

## Hipótese quebrada e único próximo experimento

**Hipótese principal:** concordância de sinal entre resíduos locais estimados de diferentes sessões é suficiente para transferir autoridade direcional a uma sessão futura sob S carry-forward.

Contraexemplo: **1631 rpm / MAP 0,207**, score 0,908910, três fontes com sinal negativo, posterior 0,966854, MAD 0,007603 ms. Residual previsto -0,052213 ms; residual externo +0,260957 ms; erro absoluto 0,313171 ms. Baixa dispersão não excluiu erro compartilhado.

**Experimento mínimo:** uma nova sessão gasolina independente com visitas estáveis repetidas nessa região, confrontada com observações históricas reais próximas e a previsão negativa já congelada. Testar a transferência de sinal antes de escolher qualquer score novo. O resultado atual de 21-25 fica encerrado e não pode ser reutilizado como novo holdout de tuning.

Preservadas para eventual replay futuro as conclusões Curva global / Mapa residual e Suggestion Optimizer 0,75 → readback/nova época → aproximadamente 0,90 somente após confirmação causal. Nada disso foi implementado ou validado fisicamente nesta missão.
