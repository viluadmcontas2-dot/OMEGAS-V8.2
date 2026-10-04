# Mutantes do cérebro do Refino (revisão adversarial)

Cada regra nova foi desfeita à mão (uma por vez) e a suíte precisa falhar. Rodados localmente com o `kotlinc` embutido do Gradle
(testes puros; nada de Gradle) e `python3 -m unittest`. O CI roda tudo de novo.

| Regra desfeita | Kotlin | Python |
|---|---|---|
| visitas mínimas 3→0 (P1-1/P2-1) | morto | morto |
| teto de tolerância 5%→20% (P1-1) | morto | morto |
| índice sem o mínimo de 50% do uso (P1-1) | morto | morto |
| GNV puxado para a gasolina (prior) (P1-2) | morto | morto |
| telemetria sobre nativa madura (P2-2) | morto | morto |
| sem banda morta ±4% (P2-3) | morto | morto |
| sem guarda de regressão da proposta (P2-3) | morto | morto |
| sem cobertura interna da faixa (P2-6) | morto | morto |
| sem peso por episódio (P2-1) | morto | morto |
| sem teto de peso por faixa (P2-2) | morto | morto |
| episódio -1 desliga o portão das faixas (P3) | morto | morto |
| janela estável sem teto de ms 10% (P3) | morto | — |
| curva sem impressão digital não falha fechada (P2-4) | morto | — |
| leitura velha da curva anterior vira mudança externa (P2-4) | morto | — |
| tique do serviço não alinha a curva (P2-4) | morto | — |
| `temporalCoherent` ignorado (P2-4) | morto | — |
| prova nunca esgota / ponto não julgado / sem episódios (P3, P1-1) | morto (3) | — |
| ganho aprendido não decai; curta sem melhorar firma (P2-7) | morto (2) | — |
| Desfazer conta passada; diário ensina no Desfazer (P2-7) | morto (2) | — |
| diário sem episódios; AutoMatch nativo não zera o diário (P2-7) | morto (2) | — |
| tolerância da fase 3% (texto/regra) (P2-6) | morto | — |
| `index` como objeto ou 0 no lugar de null (contrato) | morto | — |
| mediana enviesada em amostra par (P1-2) | morto | — |
| veredito sem casar por RPM (P2-5) | morto | — |
| cobertura interna na fase | morto | — |

Sobrevivente justificado (equivalente): remover `dispersion != null` de `PointEvidence.judgeable`. Com `pairs >= 3` a dispersão
(precisa de ≥ 2 pares) é sempre conhecida, então a condição é redundante (defesa em profundidade, inalcançável).
