# Auditoria de interface — 6 abas (2026-10-13)

Escopo: AutoCal, Refino, Refino local, Ajuste local, Ajuste global, Ferramentas + barra do topo persistente. Base: prévias `docs/evidence/img/previas-2026-10-13e/` (Chromium 1280×720, dados de exemplo) e medições `tools/ci/autocal_abertura.cjs` (CPU 4× mais lenta). Não é prova de aparelho.

| Pilar | Nota (1–4) | Veredito | Achado principal |
|---|---|---|---|
| Texto | 3 | PASS | Frases curtas em português; "Refino local" tem uma frase e um botão. Resta jargão em Ferramentas ("arquivo .omegas", "regiões"). |
| Visual | 3 | PASS | Mesmo gráfico em AutoCal e Refino; barra do topo igual em todas as abas. Ferramentas tem cartões com espaço vazio. |
| Cor | 3 → 4 | **corrigido** | Havia roxo/violeta (destaque da navegação, botões, faixa de MAP magenta): violava a regra "sem roxo". Trocado por azul/branco; teste novo `tests/ui/sem-roxo.test.cjs` varre CSS/JS. |
| Tipografia | 2 | FLAG | Números do gráfico (10–11 px) e legenda (13 px) pequenos para multimídia. Barra do topo está legível (22 px). |
| Espaço | 3 | PASS | Sem rolagem nas 6 abas a 1280×672 e 1280×720 (`sem_rolagem_abas.cjs`); barra custa 44 px. Legenda "Curva K/Teto 1,8" do AutoCal pode cortar ao tocar num ponto (já existia antes; intermitente). |
| Experiência | 3 | PASS | Abas "Agora" e "Sugestões" podadas (redundante/vazia); leituras ao vivo persistentes; abrir abas ≤ 55 ms (4× mais lento), sem tarefa longa. |

## Podado nesta rodada
Aba Agora (+ `dashboard.js`, CSS), aba Sugestões (+ `suggestion-model.js`, lista, selo), faixa `vehicle-status-strip` (substituída por `live-bar.js`), `curve-prediction-state.js` (selo escondido que rodava a cada quadro), `split-layout.js` (nunca ativava em 1280 px), CSS correspondente e 14 testes de telas que não existem mais.

## Abertos
- Tipografia do gráfico (FLAG) — decidir tamanho com o dono olhando no aparelho.
- Gates antigos de render em aparelho (`.github/workflows/verde-android-render-evidence.yml`, `DashboardLevelsRenderTest.kt`) citam telas que não existem (Agora, Aprender, OBD): já estavam desatualizados; adaptar ou remover quando forem usados.
- Aquisição GNV/gasolina em qualquer ordem: a parte do app foi corrigida no Kotlin (ver checkpoint); se a ECU em si não adquire parada em marcha lenta, isso só se confirma no carro.
