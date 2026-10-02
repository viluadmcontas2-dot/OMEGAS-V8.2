# Prompt de execução — OMEGAS WU-007 (sessões inteligentes, aprender com sessões, Ferramentas e polimento de UI)

Cole tudo abaixo no executor (GPT/Codex com acesso ao GitHub e ao Notion).

---

Você é o executor do OMEGAS V8.2: app Android para a ECU MP48 / Omega Platinum (GNV), usado na multimídia 1280×720 do carro.

- Repositório: `viluadmcontas2-dot/OMEGAS-V8.2`.
- Branch: `claude/brave-darwin-wuliyo`, a partir do HEAD atual.
- A WU-006 está fechada com APK verde: run `37063541243`, SHA `dcd601c5`.
- Não crie branch e não abra PR.
- A **única** remoção de branch permitida é a do Bloco 0, exatamente como está em `docs/governance/BRANCH-CLEANUP-20261002.md`.

## 0. Leitura obrigatória

1. Leia, nesta ordem:
   - `AGENTS.md`
   - `PROJECT.md`
   - `STATUS.md`
   - `docs/handoff/SESSION-HANDOFF-20261002.md`
   - `docs/workunits/OMEGAS-WU-006.md`
   - `docs/handoff/PROMPT-GPT-WU-006-FINAL.md`: vale como referência de regras, contratos de dados e armadilhas (§1, §2, §3).
2. No Notion, releia por inteiro os blueprints abaixo. O Car Info Next é o contrato 1280×720.
   - "Blueprint Premium UI/UX — Método CUSTOMROM reutilizável";
   - "Método aplicado — Omega Dev 4.0 Premium UI/UX";
   - "18 — Blueprint Car Info Next — Método OMEGAS + CUSTOMROM".
3. Abra `docs/workunits/OMEGAS-WU-007.md` com o escopo abaixo antes de codar.

## 1. Foco do produto (não inverter)

**O foco é igualar o tempo de injeção do GNV ao da gasolina**, e tudo existe para isso.

- A base é a mesma equivalência da ECU: MAP × Tpet, 18 bandas.
- O refino do refino é RPM × MAP.
- O ciclo fechado é feito pelo diário e pelo piloto.

Level, consumo e Ferramentas são **bônus**. Nunca podem bloquear, atrasar ou poluir o fluxo principal (AutoCal → Refino → gravar → verificar).

## 2. Regras invioláveis

- **ECU:** nunca gravar automaticamente. O fluxo é sempre preparar → revisar → confirmar → ACK → readback.
- **Matemática congelada:** não altere constantes nem fórmulas de `AutoMatchRefinedEngine`, `EquivalenceLedger`, `RefinementJournal`, `RefinementAutopilot` e `tools/autocal_refine/*`. Funções novas devem ser puras e ter teste.
- **Nada inventado** na UI: nenhum ponto, porcentagem ou litro inventado. Mantenha `PHYSICAL_VALIDATION_CLAIMED=false`.
- **Desempenho:** a multimídia é fraca.
  - Nada de polling por tela; use o Store/Scheduler central.
  - Nada de busca O(n²).
  - Nada pesado na thread de UI ou no bridge síncrono. Arquivos e ZIP rodam em background.
- **Custo zero.** CI só no fechamento: um disparo de `omegas-preapk-build.yml` e as correções necessárias.
- **Testes nunca são pulados nem desligados.**
- **Lint:** toda chamada de notificação precisa checar a permissão (o erro `MissingPermission` já derrubou um build).
- **Ritmo de commits:** um commit por bloco. Rode o gate (`python3 -B tools/run_checks.py`) antes de cada push.
- **Quando parar:** quando o dono ou o Claude mandar parar, **não faça mais push**. Isso já aconteceu uma vez e não pode repetir.

## 3. Blocos (nesta ordem)

### Bloco 0 — Limpeza das branches (aprovada pelo dono)

Execute `docs/governance/BRANCH-CLEANUP-20261002.md` à risca:
1. Para cada uma das 33 branches, crie a tag `archive/<nome>` no SHA da tabela.
2. Confira a tag.
3. Só então apague a branch.

**Ficam somente `main` e `claude/brave-darwin-wuliyo`.** Se o SHA atual de uma branch não bater com o da tabela, pule essa branch e registre.

No fim, registre o resultado no próprio arquivo e faça um commit só de documentação, sem CI.

### Bloco A — Sessão vira um ZIP inteligente

Hoje as sessões são exportadas automaticamente em pastas (Downloads). O bug de duplicação já foi corrigido.

1. **Formato:** cada sessão encerrada vira **um único** `OMEGAS_<data>_<hora>_<id>.zip` em Downloads, no mesmo lugar onde hoje fica a pasta.
   - Gere o ZIP em background quando a sessão fechar.
   - Escreva num `.tmp` e renomeie no fim (escrita atômica).
   - Não deixe a pasta solta depois de confirmar o ZIP, mas mantenha a pasta se a geração do ZIP falhar.
   - Se houver um lugar no app que hoje leia essas pastas, ele precisa continuar funcionando.
2. **Conteúdo:**
   - os dados brutos atuais, sem perder nada;
   - `manifest.json`: formato, versão do app, ID da ECU, SHA-256 de cada arquivo;
   - **`resumo.md` + `resumo.json`**, a inteligência da sessão:
     - duração;
     - tempo em gasolina, GNV, marcha lenta e condução;
     - faixas de RPM × MAP visitadas;
     - bandas da ECU válidas ao início e ao fim;
     - contagem de AutoMatch nativo;
     - fases do piloto (eventos `refinement_phase`), com o motivo de cada mudança;
     - GNV ÷ gasolina por faixa (5 faixas) e global;
     - "gás por ar";
     - gravações de Curva K e Mapa K, com o antes e depois e o veredito do diário;
     - avisos: banda rejeitada, degrau, leitura falhou, sessão USB caiu;
     - o que faltou coletar.

     Escreva em português simples no `.md`. O `.json` deve ser máquina-legível.
3. **Testes:**
   - JVM do gerador de resumo, com sessão sintética e com fixture real reduzida;
   - escrita atômica e não duplicação;
   - resumo correto sem GNV, sem gasolina ou sem ECU.

### Bloco B — O app aprende com as próprias sessões

1. **Fonte:** em Ferramentas, ação "Aprender com sessões". O usuário escolhe ZIPs ou pastas de sessão (seletor de arquivo do Android), que alimentam o **`EquivalenceLedger`**.
   - As leituras de gasolina entram como referência por RPM × MAP.
   - As leituras de GNV **só entram se a impressão digital da Curva K da sessão** (MUL_ACT do snapshot) **for igual à curva atual da ECU**, e também o Mapa K. GNV de curva antiga não pode ensinar a curva nova: essa é a regra de época que já existe (`resetGas` / `alignCurve`).
   - Use exatamente o mesmo critério de leitura estável do ledger (3 quadros, ≤1,2 s, RPM ±150, MAP ±0,03) e a função existente, **sem duplicar a lógica**.
   - Impeça a mesma sessão de entrar duas vezes: guarde o hash do ZIP ou o ID da sessão.
2. **Relatório:** depois de importar, mostre "X leituras de gasolina, Y de GNV aceitas, Z de GNV descartadas (curva diferente)". Isso alimenta automaticamente o Refino e o gráfico "Nossa curva".
3. **Diário:** os vereditos de gravações antigas que existirem nas sessões entram como **histórico**. Não recalculam o `bandScale`: só experimentos com antes e depois medidos ajustam o ganho.
4. **Testes:**
   - importação aceita gasolina;
   - descarta GNV de outra curva;
   - não duplica a mesma sessão;
   - um ZIP corrompido falha fechado com mensagem.

### Bloco C — Ferramentas refeita (blueprint)

Hoje a aba está bagunçada:
- botões soltos, como "Exportar backup", "Exportar aprendizado", "Importar", "Logs" e "Autoteste";
- textos técnicos expostos;
- interação ruim e bugs.

Reorganize por **intenção humana**, com uma tela sem rolagem em 1280×644 e grupos claros:

1. **Sessões:** lista das últimas sessões (ZIPs), cada uma com o resumo de uma linha (data, km/tempo, GNV÷gasolina, se houve gravação). Ações: "Ver resumo", "Compartilhar", "Aprender com sessões".
2. **Backup e restauração:** exportar e importar o backup completo e o aprendizado, cada um com uma frase do que leva. A importação nunca escreve na ECU.
3. **Saúde do app:** USB, ECU, serviço em segundo plano, bateria e telemetria flutuante. Normalidade em uma linha; problema ganha espaço com o próximo passo.
4. **Sensor de nível e consumo (bônus):** compacto.
5. **Diagnóstico:** autoteste e logs, em um painel fechado ("Detalhes técnicos").

Cada botão diz o efeito. Remova os textos de implementação, como "ForegroundService", "WebView", "live-onlyGasolina" e "época 2", do nível 1; eles podem ir para o painel fechado.

Corrija os bugs que achar e registre cada um em `docs/workunits/OMEGAS-WU-007.md`, com teste quando for lógica.

### Bloco D — Polimento geral de UI (blueprint, sem mudar o foco)

1. **Checklist:** passe o `docs/product/UX-BLUEPRINT-CHECKLIST.md` em **todas** as telas (Agora, AutoCal, Refino, Aprender, Ajuste global, Ajuste local, Sugestões, Ferramentas). Corrija o que falhar:
   - hierarquia;
   - uma ação primária;
   - targets 56–68dp;
   - texto ≥12sp;
   - cores semânticas;
   - normalidade compacta;
   - técnico sob demanda;
   - estados vazio, carregando e falha.
2. **Navegação:** a ordem fica mantida: Agora · AutoCal · Refino · Aprender · Ajuste global · Ajuste local · Sugestões · Ferramentas.
   - Trocar de aba não pode perder revisão nem operação em andamento.
   - Garanta que **nenhuma aba trava ao entrar**. O caso conhecido é Sugestões; o padrão de correção é ter um dono só por lista, sem chamar `api.learning()` sem necessidade e sem redesenhar quando nada mudou. Procure o mesmo padrão nas outras abas, especialmente Aprender, Ajuste global e Ferramentas.
   - Meça o custo de entrar em cada aba e registre.
3. **Sugestões:** vira a **fila de decisões**: curva refinada pronta (leva ao Refino), ajustes de Mapa K e de Curva K, sempre com uma frase do porquê e o tamanho da mudança.
4. **Screenshots:** Playwright, 1280×720, bridge falso, de cada tela em estado normal e em estado de problema. Salve em `docs/evidence/ui-wu007/`.

### Bloco E — Fechamento

1. **Gate e testes:** gate rápido, JVM e paridade (`tests/test_refined_autocal_kotlin_parity.py` com `KOTLINC` e `ORG_JSON_JAR`, se você tiver), e testes Node.
2. **CI:** **um** disparo de `omegas-preapk-build.yml` na ref `claude/brave-darwin-wuliyo`. Se falhar, ache a causa raiz e corrija. Mesmo erro duas vezes → pare e reporte.
3. **Registro:** SHA, run id, artifact e digest em `STATUS.md` e `docs/evidence/`.
4. **Resposta ao dono** em português simples, no máximo 12 linhas:
   - o que mudou em cada tela;
   - como funciona o ZIP e o "aprender com sessões";
   - o link do APK;
   - o que só o carro prova.

## 4. Onde você pode falhar

- **ZIP na thread errada:** gerar o ZIP na thread de UI ou dentro de uma chamada síncrona do bridge trava a multimídia.
- **Seletor de arquivo:** importar sessões exige o seletor do Android (SAF). Não peça permissão ampla de armazenamento.
- **Duplicação:** misturar GNV de curva antiga, ou não deduplicar a importação, contamina o refino. Esse é o erro mais grave possível neste bloco.
- **Lógica duplicada:** não reimplemente a lógica de leitura estável. Reutilize o `EquivalenceLedger.accept`.
- **Desempenho na importação:** carregar milhares de quadros pode estourar a memória. Faça streaming (linha a linha do JSONL) e mostre progresso real em número de quadros, não porcentagem inventada.
- **Contexto ao navegar:** trocar de aba no meio de uma importação não pode cancelá-la nem perder o resultado.
- **Nomes de campo:** os dados de `getEquivalence` estão no §2.3 do prompt da WU-006. Não invente nomes.
