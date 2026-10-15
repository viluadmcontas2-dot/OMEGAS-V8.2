# OMEGAS para oficina — plano de produto (2026-10-15)

Pedido do dono: o app tem muita inteligência, mas só observa. Para vender a oficina ele precisa regular rápido, dizer o que faz e por quê, e ser usável por um mecânico leigo. Mantém: correção pela curva K + ajuste local pontual do GNV. Marcas: OBS (medido) · DERIVADO · INFERIDO · DESCONHECIDO.

## 1. Diagnóstico (o que trava hoje)

| # | Problema | Evidência |
|---|---|---|
| 1 | **Falta de cobertura, não de algoritmo.** O motor só propõe uma faixa com ≥ 8 janelas estáveis (1,2 s, RPM ±150, MAP ±0,03) e gasolina de referência. Em uso real quase nunca chega. | DERIVADO do corpus `sessoes-uteis`: de 78 sessões, **6 (8%)** cobriram as 5 faixas de injeção; faixas de 6–9 ms: 14 e 20 sessões. Sessão mediana: 9 min. (Critério aproximado; o real exige par gasolina×GNV no mesmo RPM×MAP, então é mais difícil, não menos.) |
| 2 | **O app espera a ECU.** Sem a ECU terminar o automático (contador, aquisição completa ou "por minha conta"), não propõe nem grava. Depois ainda mede 100 leituras novas (até 500) para dar o veredito. | OBS-COD: `write-gate.js`, `RefinementAutopilot.kt` (`ecuDone`), `RefinementJournal.VERIFY_*`. |
| 3 | **A coleta não vira instrução.** O app sabe quais faixas faltam (`missing`) e escreve "rode no GNV passando por 4,5–6,0 ms", texto pequeno que o mecânico não age. | OBS-COD: `RefinementAutopilot.nextStep`. |
| 4 | **Estados confusos.** Cinco fases + chip + frase + botões que mudavam de lugar; o dono precisava observar para entender. | Corrigido o piscar do botão (`cbb84c2`); falta o desenho único de estados (seção 3). |
| 5 | **Peso de código legado.** LAN, link entre aparelhos, OBD/Bluetooth e dezenas de métodos sem tela. | Poda em andamento (seção 5). |
| 6 | **Oficina = carro parado.** Parado/em marcha lenta só se visitam as faixas baixas; carga alta exige rua ou rolo. | INFERIDO (corpus: faixa 3–4,5 ms em 71 sessões, 6–7,5 ms em 14). Precisa de teste na oficina para virar OBS. |

## 2. Visão: "Regulagem guiada" — uma tela, um passo, um botão

O mecânico não interpreta gráfico; ele segue um **roteiro** que o app conduz e mede.

1. **Gasolina** — "Rode na gasolina até a barra encher." (referência)
2. **GNV** — "Troque para GNV. Acelere até a faixa 4,5–6,0 ms e segure." O app mostra, ao vivo, a **faixa alvo**, o quanto falta (leituras x/8) e quando passou ("✓ faixa 4,5–6,0").
3. **Aplicar** — "Curva pronta: 12 pontos. [Gravar]". Um botão. Conferência por leitura de volta e **Desfazer** sempre ao lado.
4. **Conferir** — "Rode de novo nas faixas gravadas. Melhorou 9 de 12." Só evidência, sem relógio.
5. **Pontual** — se sobrar diferença que a curva não resolve (separável por RPM): "Ajuste local em 4 células do Mapa K. [Revisar]".
6. **Pronto** — "GNV equivalente à gasolina em 5 de 5 faixas. Pode entregar o carro." + relatório do atendimento (cliente/placa/antes×depois) para a oficina.

Princípio: **a coleta passa a ter dono e destino**. Cada leitura acende uma faixa; cada faixa cheia libera a próxima ação.

## 3. Estados e frases (um desenho só)

Um indicador de passo (1–6 acima) + **uma frase de situação** + **uma frase de motivo** + **um botão**. Nada mais na barra de ações. Cada fase tem texto fixo, curto, no padrão: *o que o app está fazendo / por quê / o que você faz agora*. Exemplos: "Esperando a ECU terminar o automático (2 de 3). Gravar agora seria desfeito por ela. → Dirija normalmente." / "Faltam 2 faixas. Sem elas a curva fica incompleta. → Segure o motor em 6–7,5 ms."

Regra de ouro (dono, 2026-10-14): só evidência, nunca relógio.

## 4. Aprender mais rápido (sem mudar o que é seguro)

Alavancas, em ordem de ganho esperado/custo:

1. **Coaching por faixa** (UI + `missing` do Kotlin): meta ao vivo por faixa, alvo e progresso. Ganho: ataca o problema 1 e 3, custo baixo, sem risco de ECU. **Primeira fatia.**
2. **Usar a primeira passada.** Hoje exigimos pares gasolina×GNV no mesmo RPM×MAP. Avaliar no corpus quantos pares a mais saem se a referência de gasolina vier da curva da ECU desde o começo (já existe `ecuShare`). Medir antes de mudar.
3. **Reduzir a espera da ECU** com decisão do dono: oferecer o modo "por minha conta" como padrão da oficina (a ECU é a que sobrescreve, não o app). Risco conhecido: PROTOCOLO-LANDI 17/23. **Decisão do dono.**
4. **Veredito mais cedo, com margem honesta.** `VERIFY_PARTIAL_SAMPLES=100` / `GIVE_UP=500` foram escolhidos sem corpus: reproduzir no replay e propor valor menor só se o veredito não mudar em ≥ 95% dos casos.
5. **Modo oficina com roteiro de bancada**: sequência de patamares de RPM/carga que o carro parado alcança, com aviso explícito do que só a rua cobre (problema 6).

Critério de sucesso (medido no replay + emulador, não em opinião): **tempo até cobrir ≥ 4 de 5 faixas** e **nº de leituras até a 1ª proposta**, comparados com a linha de base (8% das sessões cobrem 5/5).

## 5. Poda para produção

- Feito: métodos sem chamador (UI e Kotlin), 9 arquivos mortos + testes, recurso de link LAN, servidor LAN, OBD/Bluetooth (config, permissões, arquivo `.omegas` sem o campo `obd`).
- Verificação: build Android real (`testDebugUnitTest` + `lintDebug`).
- Falta: CSS órfão (poda automática quebrou a tela "ponto tocado"; fazer com captura), 24 métodos da ponte restantes que só simuladores usam, `ExistingCalibrationWriterV7` e `OrderedBackgroundPipeline` (travados por testes de contrato), pacote `v7` inteiro (a UI não o expõe), classes de comando da ECU (só com autorização do dono).

## 6. Fatias, em ordem (cada uma com prova)

| Fatia | Entrega | Prova |
|---|---|---|
| A | Coaching por faixa no Refino (meta, alvo, x/8, ✓) | teste de UI + captura 1280×720 |
| B | Desenho único de estados (passo + 2 frases + 1 botão) | `tests/ui/*`, `sem_rolagem_abas`, capturas |
| C | Relatório de atendimento (antes×depois, exporta) | fixture + teste |
| D | Medir 2 e 4 no replay do corpus; propor números | relatório em `docs/evidence/` |
| E | Modo oficina (roteiro de bancada) | emulador + validação com o dono na oficina |

## 7. Decisões que são do dono

1. "Refinar por minha conta" como padrão no modo oficina? (item 3)
2. Cliente final vê a tela de calibração ou só o mecânico? (muda a linguagem)
3. Relatório do atendimento: só PDF/arquivo, ou também envio? (envio = serviço externo)
4. Oficina real para a fatia E (carro parado, GNV, regulador presente).

## 8. Fora do escopo agora

Reabrir bytes de comando da ECU, trocar a matemática da curva K, nuvem/contas, qualquer escrita automática (invariante do projeto).
