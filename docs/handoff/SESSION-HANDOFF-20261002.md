# Passagem de sessão — 2026-10-02 (WU-006)

Para a próxima sessão do Claude: leia este arquivo e depois `AGENTS.md` → `PROJECT.md` → `STATUS.md` → `docs/workunits/OMEGAS-WU-006.md` → `docs/handoff/PROMPT-GPT-WU-006-FINAL.md` (versão 2).

## Objetivo do dono

Fazer o GNV equivaler à gasolina usando como base o AutoCal nativo da ECU MP48 e, depois, refinar a curva para tirar os trancos. O dono quer conectar o cabo, dirigir e ser avisado. Não quer ficar olhando a tela.

Divisão de trabalho:
- **Claude:** pensa e revisa.
- **GPT:** executa as tarefas longas a partir de prompts escritos pelo Claude.

## Feito (Claude, nesta sessão)

**Motor refinado** (`AutoMatchRefinedEngine`). É a mesma equivalência MAP × Tpet da ECU, só que bem feita:
- descarta bandas outlier e invertidas;
- suaviza com Whittaker robusto;
- aplica a trava de coerência: ±15% por execução e |Δ ln K/Δ ln t| ≤ 0,35;
- falha fechada quando não há evidência.

**Pontos próprios** (`EquivalenceLedger`): leituras estáveis de gasolina e GNV casadas por RPM × MAP.
- Entram como "refino do refino", com peso 0,4.
- Fazem o casamento em grade, em O(n), em `eed795dd`.

**Ciclo fechado:**
- **`RefinementJournal`:** dá um veredito por faixa (CONFIRMADA, PASSOU, CURTA ou PIOROU), aprende o ganho por faixa e oferece restaurar só o trecho que piorou.
- **`RefinementAutopilot`:** espera a ECU terminar o automático e depois conduz as fases Nossos pontos → Proposta → Verificação → Estável, avisando por notificação. Nunca grava.

**Limpeza:** Predictor e OBD removidos (executado pelo GPT, conferido pelo Claude).

**Provas locais:** gate rápido PASS, paridade Kotlin↔Python OK, 691 testes JVM OK, testes Node OK. O último APK provado pelo CI é do SHA `b2df77bd`, de antes do ciclo fechado.

## Em andamento (GPT, com `PROMPT-GPT-WU-006-FINAL.md` v2)

Blocos do §10 já enviados:
1. Destino "Refino" separado do AutoCal (`ca50583b`).
2. Gráfico "Nossa curva": cópia do MAP × Tpet da ECU, mais as bandas densas (`57621910`).
3. AutoCal da ECU com diagnóstico do degrau e referências de coleta (`b167a920`).

Faltam:
4. Agora, notificação levando ao Refino e item em Sugestões.
5. Levels: SC 36/37/276/300/313[0] só leitura, `LevelEstimator` andando de 1 em 1.
6. Placar de consumo por calibração, reaproveitando o `gasPerAir` por época.
7. Roteiro de teste de campo (`docs/V82_REFINO_FIELD_TEST.md`).
8. Docs, CI (um disparo de `omegas-preapk-build.yml`), registro do digest e resposta ao dono.

## Atualização final desta sessão (HEAD `103f5f1f`)

O GPT entregou os blocos 4 a 7 e a documentação:
- `51390ed6`: entradas do Refino;
- `3ded2a1a`: level só leitura;
- `59f6fc57`: placar;
- `ded8cdea`: roteiro de campo;
- `d1d2dc9f` a `103f5f1f`: docs e review.

Ele **não rodou** testes nem CI: estava sem DNS para o github.com e o conector dele não tem `workflow_dispatch`.

O Claude rodou no HEAD `103f5f1f`:
- `QUALITY_GATE_FAST=PASS`;
- JVM integral via kotlinc: **709 testes OK**;
- paridade Kotlin↔Python: **OK**.

Pendências reais registradas pelo GPT em `STATUS.md` e no WU-006:
- **R-01:** `LevelSensorSnapshot.read()` pode publicar um snapshot antigo depois de uma troca de sessão USB. Escrever primeiro a regressão concorrente que falha (RED), depois corrigir pelo menor caminho (GREEN).
- **Capturas e checklist:** as 11 capturas em `docs/evidence/ui-wu006/draft` são rascunho. Regenerar e conferir contra o checklist.
- **CI:** nenhum CI rodou no SHA atual. O Claude dispara pelo MCP: `mcp__github__actions_run_trigger`, workflow `omegas-preapk-build.yml`, ref `claude/brave-darwin-wuliyo`.

## Próximos passos (próxima sessão do Claude)

0. Corrigir o R-01 (RED→GREEN). Revisar os commits do GPT a partir de `eed795dd`, conforme o item 1 abaixo. Disparar o CI uma vez e registrar run, artifact e digest em `STATUS.md`.

1. Quando o dono disser "o GPT terminou": `git pull` e revisar todos os commits do GPT a partir de `eed795dd`. Verificar:
   - a matemática não foi alterada; rodar paridade e o gate;
   - os nomes de campo batem com o §2.3 do prompt;
   - nada de pontos ou porcentagens simulados;
   - nada de polling por tela;
   - a escrita continua com `runWrite` (stale check + readback);
   - o level não chuta a escala.
2. Conferir que o CI está verde e que o digest foi registrado em `STATUS.md`.
3. Abrir o PR `claude/brave-darwin-wuliyo` → `main` só depois disso. Com o PR aberto, cada push dispara o build completo.
4. Teste no carro pelo dono, seguindo o roteiro de campo. Confirmar com as sessões:
   - os tempos do piloto (10/25 min são hipótese);
   - a tolerância de ±3%;
   - se o tranco de 8–9 ms some;
   - o comportamento do level.

## Regras que o dono reforçou

- **Escrita na ECU:** sempre manual (preparar → revisar → confirmar → ACK → readback). Gasto zero.
- **Git e CI:** branch única `claude/brave-darwin-wuliyo`; CI só no fechamento; não abrir PR sem pedido.
- **Tudo sincronizado no GitHub.** Os testes rápidos rodam no container da nuvem; o build pesado roda só no CI.
- **Navegação:** Agora · AutoCal (o da ECU) · Refino (o nosso) · Aprender · Ajuste global · Ajuste local · Sugestões · Ferramentas.
- **UI:** seguir os blueprints do Notion (CUSTOMROM, Omega Dev 4.0, Car Info Next 1280×720). Nada de tela que muda conforme o carro anda ou para.
- **Hierarquia do refino:** a base é MAP × Tpet, como na ECU; RPM × MAP é o refino do refino; o Mapa K só é ajustado se a medição provar dependência de RPM.
- **Comunicação:** em português simples. O dono é leigo e quer funcionalidade, não teoria.
