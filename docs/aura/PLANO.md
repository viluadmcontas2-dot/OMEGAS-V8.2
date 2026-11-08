# Omega Aura — plano canônico (estado vivo; atualizar a cada marco)

**Decisão do dono (2026-10-27):** não recomeçar em repositório novo. Usar UMA branch, `claude/omega-aura` (base: `claude/cool-curie-hs5nk9` verde, que parte de `OMEGASCANONICO`), para enxugar o código e tirar o legado (V7, V8…): tudo objetivo, didático, coerente e com semântica de ponta a ponta. "Omega Aura" é o nome da versão. Motivo da branch: mantém histórico, evidências, CI e segredos; um repositório novo perderia tudo isso sem ganho.

**Pedido (resumo fiel):** (1) refinar o fluxo de TODA a interface (ver `CONTRATO-DE-FLUXO.md`); (2) Conectar automático, com persistência, recuperação e reconexão muito boas; (3) alguma inteligência que verifique se as mudanças fazem sentido (o Refino sugere uma curva, depois outra; o AutoCal faz uma curva diferente da sugerida); (4) delegar (Antigravity não está instalado; o Codex está sem franquia até ~18:27 do fuso dele): usar agentes Sonnet em modo baixo, bem escopados; (5) reconectar a interface nova à lógica de forma limpa, auditar todos os fluxos, testar no GitHub (CI remoto); (6) TDD; (7) gerar o APK e colocar no Drive.

## Regras de trabalho
- RED → GREEN em cada mudança (skill `test-driven-development`); portas de qualidade pela skill `ci-cd-and-automation`. Nada de teste enfraquecido para passar.
- Um dono por arquivo: cada unidade de trabalho (UT) escreve só na sua superfície, em worktree/branch própria `claude/aura-<ut>`, e entra na `claude/omega-aura` por merge feito pelo Claude principal, com a suíte rodando no estado integrado.
- Não alterar bytes nem classes de comando da central (`UsbSerialManager`, `ResponseDrivenEcuEngine`, `AutoCalProtocol`, `KFactorManager`, `KWriteManager`, `AutoCalNativeActionManager`). Reconexão e recuperação entram por classes novas que usam a API pública delas.
- Sem API paga (LLM ou outra). Inteligência = determinística, testável, no aparelho.
- Push sem force; leitura de volta depois de cada envio (`tools/sync-agentes.sh`).

## Inteligência (recomendação, em vez de um LLM dentro do app)
Um LLM no caminho de decisão ou de gravação seria não determinístico, lento, dependente de internet no carro e pago (o dono não quer API paga). O sintoma descrito (sugestões que mudam e o AutoCal fazendo curva diferente) é de **dois controladores mexendo na mesma região sem se coordenar**. Solução: **Diário + Coerência**, ambos determinísticos:
1. **Diário** (append-only, persistente): cada sugestão, gravação, desfazer e resultado, com quem (você, Refino, AutoCal), região, antes → depois e desfecho medido. Também alimenta o Desfazer, a recuperação de gravação interrompida e a exportação.
2. **Coerência** (regras sobre o Diário): detecta oscilação (sinais opostos na mesma região), AutoCal desfazendo o que o Refino gravou, sugestão repetida sem ser aplicada. Ação: congelar a região por **contagem de leituras** (não por tempo) ou deixar o AutoCal decidir, com texto simples ("O AutoCal baixou o que o Refino subiu aqui; vou esperar").
3. **Aprendizado persistente**: por região, guarda quais correções melhoraram e ajusta o tamanho da próxima (estatística simples, auditável).
4. Opcional e fora do carro: "Copiar resumo para o assistente" (texto) para o dono colar no ChatGPT/Claude da assinatura dele. Zero API.

## Unidades de trabalho (UT)
| UT | O quê | Superfície de escrita | Depende de | Estado |
|---|---|---|---|---|
| F0 | Branch, plano, contrato de fluxo | `docs/aura/*` | — | feito |
| A1–A4 | Auditorias (fluxos da UI, conexão, nomes e código morto, coerência) | `docs/aura/auditoria/*` | F0 | em andamento |
| D1 | Contrato de fluxo aplicado nas 23 telas do canvas | `docs/produto/mockups/v3/**` | F0 | a fazer |
| D2 | Conectar: primeira vez, reconectando, retomada, problema | mockup 01, 01b–01d | F0, A2 | a fazer |
| D3 | Memória e coerência (tela 21) | mockup 21 | F0, A4 | a fazer |
| I0 | Núcleo do fluxo: botão principal fixo, "Última gravação", selo de atividade; teste `contrato-de-fluxo.test.cjs` | `ui/core/fluxo.js`, `ui/components/acao-principal.js`, `tests/ui/contrato-de-fluxo.test.cjs` | A1 | a fazer |
| I1 | Barra do topo na ordem do dono + MAP/RPM + selo | `ui/components/live-bar.js` | I0 | a fazer |
| I2 | AutoCal: toque apaga, botões sob o gráfico, arrastar escolhe | `ui/screens/autocal-*.js`, `ui/core/ponto-limpeza.js`, `ui/core/low-rpm-filter.js` | I0 | a fazer |
| I3 | Refino: fluxo único, Última gravação, 05d | `ui/screens/refino.js`, `ui/core/map-fix.js`, `ui/core/stall-region.js` | I0 | a fazer |
| I4 | Ajuste local, Ajuste global, Curva → Mapa K | `ui/screens/map.js`, `ui/screens/curve.js`, `ui/map-editor.js`, `ui/core/curve-to-map.js`, `ui/core/balance-runner.js` | I0 | a fazer |
| I5 | Ferramentas: cartões com Desfazer fixo | `ui/components/advanced.js`, `ui/components/drawers.js`, `ui/core/ferramentas-grupos.js` | I0 | a fazer |
| I6 | Conectar: supervisor de conexão e recuperação (Kotlin novo + tela) | Kotlin novo em `service/`, `ui` do 01 | A2, D2 | a fazer |
| I7 | Diário e Coerência (JS puro + ponte de persistência) | `ui/core/diario.js`, `ui/core/coerencia.js`, Kotlin de armazenamento novo | A4, D3, I0 | a fazer |
| I8 | Aura: nomes e legado (V7/V8), código morto, versão | Kotlin/tests/tools/config (ordem e riscos: `NOMES.md`) | A3 | a fazer |
| Q | Integração, suíte completa, CI remoto | — | todas | a fazer |
| R | Prévias (imagens da interface real), APK, Drive | `.github/workflows/apk.yml` (gatilho) | Q verde | a fazer |

## APK
Regra do dono (2026-10-13): APK só com aprovação explícita. Em 2026-10-27 o dono pediu: gerar o APK com testes bem feitos e colocar no Drive. Condições antes de disparar `apk.yml` com `build_apk=true`: (a) CI verde no SHA exato (contratos Python, JVM, lint, JS e navegador); (b) prévias da interface real publicadas; (c) lista do que NÃO entrou. A aprovação vale para esta rodada, não para a seguinte.

## Riscos conhecidos
- `applicationId` hoje é `com.omegas.v7.test` e o nome do produto é "OMEGAS V8 TEST": trocar cria **outro app** no aparelho (dados e permissão USB não migram). Recomendado para o Aura (começo limpo), com os dois apps convivendo. Decisão e migração em `NOMES.md`.
- Persistência com nomes antigos (`learningSchema` etc.): renomear exige migração.
- O corpo grande do AutoCal (`autocal-cockpit.js`, ~2.250 linhas) é o maior risco de regressão; testes atuais travam muito texto da interface antiga e serão trocados na mesma hora do código (RED primeiro).
- Itens "PRECISA OK" do canvas (ver `docs/handoff/PENDENTE.md`) que mudam o que a central recebe só entram com o dono sabendo.

## Decisões tomadas (dono deu carta branca, 2026-10-27)
- `RESET_ALL` ("Recomeçar tudo"): mantido como está; revisar só com prova no carro.
- Desfazer da matriz do AutoCal: fica cinza com motivo até existir comando nativo de restaurar pontos (toca classe protegida).
- `applicationId`: mantido `com.omegas.v7.test`; trocar instala outro app e perde a memória do atual.
