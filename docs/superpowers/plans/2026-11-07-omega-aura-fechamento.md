# Omega Aura: plano de conciliação e entrega

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans task-by-task, plus Codex Engineering Guardrails verification at every gate. No blind merges.

**Goal:** Consolidar o trabalho disperso no Omega Aura, comprová-lo em CI e interface real, integrar na OMEGASCANONICO e publicar APK candidato compatível com a multimídia ARM32 do dono.

**Architecture:** GitHub remoto é a autoridade técnica; `claude/omega-aura` é a única branch de integração pré-canônica. `claude/cool-curie-hs5nk9` já é ancestral do Aura; `nova-experiencia` divergiu e só fornece componentes selecionados, nunca substitui as cinco áreas por quatro. Cada unidade produz prova e commit pequeno, sem alterar bytes ou protocolos da ECU.

**Tech Stack:** Kotlin Android + WebView JavaScript/CSS + CI GitHub Actions + Python contracts + Playwright.

**Spec:** `docs/aura/PLANO.md`; `docs/aura/CONTRATO-DE-FLUXO.md`; `docs/produto/mockups/v3/LEIA-ME.md`; `AGENTS.md`; `docs/REGRAS-DO-DONO.md`.

## Global Constraints
- Canônica: `OMEGASCANONICO`; integrar só via PR #185 <https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/pull/185> quando liberar gates.
- Fluxo de escrita: somente por toque, ACK + readback; nenhuma escrita autônoma, nenhum teste de gravação na ECU sem o dono.
- Não tocar `UsbSerialManager`, `ResponseDrivenEcuEngine`, `AutoCalProtocol`, `KFactorManager`, `KWriteManager`, `AutoCalNativeActionManager` ou bytes de comando sem autorização individual.
- Nenhum force push, alteração irreversível, remoção de branch ou mudança de `applicationId=com.omegas.v7.test`; dados e USB precisam continuar acessíveis.
- Manter cinco áreas: AutoCal, Refino, Ajuste local, Ajuste global e Ferramentas; sessões, diagnóstico, viagens e carro dentro de Ferramentas.
- PNGs da interface real e mapa completo de navegação antes de liberar redesign; 1280×720, 1024×600 e 800×480.
- Verificar HEAD canônico e Aura antes de cada escrita. Sem uso de API paga extra.

## Review Focus
1. USB conectada mas ECU sem handshake: mostrar estado correto, não 'central conectada'.
2. Curva K → Mapa K interrompida entre etapas: preservar intenção e evitar dose dupla, jamais retomar escrita automaticamente.
3. Dois ajustes seguidos: botão Gravar não some, Desfazer da última não impede próximo ajuste.
4. AutoCal baixa RPM: não apagar bandas erradas ao voltar do segundo plano; exigir replay determinístico.
5. Viewport 800×480: nenhum botão crítico cortado ou oculto, nível desconhecido não vira zero.

## Histórico de reconciliação (verificado 07/11/2026)
- Canônica inicial `28afdaa74df1cb81e84d9f4ee3261d92050ad976`.
- Aura inicial `27f3edc223ad42a769c4e8591c084f91c600febc`, 65 commits à frente, 0 atrás; CI anterior de 28/10/2026 verde no SHA inicial.
- Canvas v3 `claude/cool-curie-hs5nk9` é ancestral: não mesclar novamente.
- `nova-experiencia` 4 áreas é divergente: selecionar ajuda/primeiro uso e componentes úteis com revisão semântica e visual; não copiar roteamento ou CSS inteiro.
- 07/11: PR draft #185 criado; teste `tests/test_aura_arm32_apk_contract.py` (commit `8cfa92d`) e workflow do APK ARM32 (commit `e97c232`) publicados no Aura. A leitura de volta confirma ambas as ocorrências `-PomegasAbis=armeabi-v7a`, nenhuma ocorrência `arm64-v8a` no workflow e geração ainda manual. **CI do novo SHA em andamento** na abertura deste documento. Não afirmar build/APK realizado.

## Checklist de execução, um gate por unidade

### UT 1: Auditoria visual e escolha dos componentes reaproveitáveis
**Files:** `docs/produto/mockups/v3/**`, `app/src/main/assets/ui/index.html`, `app/src/main/assets/ui/core/router.js`, `nova-experiencia` (somente leitura primeiro).
- [ ] Capturar telas reais do Aura antes de alterar, com roteiro reproduzível; comparar com as 23 telas do canvas e contrato de cinco áreas.
- [ ] Catalogar por componente: já integrado | precisa adaptar | contradiz a decisão do dono. Provar a cada item a origem e destino.
- [ ] Demonstrar a proposta integrada de todas as telas + navegação. Aplicar PFD (L0–L4) e critérios de acessibilidade, tocabilidade e espaço útil.

### UT 2: Conexão fiel ao estado da central
**Files:** `service/ConnectionSupervisor.kt`, `service/TelemetryForegroundService.kt`, `web/HubJavascriptBridge.kt`, UI de conexão; testes Kotlin/JVM e UI novos.
- [ ] Escrever teste RED distinguindo cabo, permissão, handshake, dados válidos e reconexão.
- [ ] Acoplar supervisor ao serviço **sem mudar USB/protocolo protegido**, expor estado na ponte e apresentar próximo passo.
- [ ] GREEN JVM e JS focal; CI no SHA, revisar reconexão real apenas quando houver aparelho.

### UT 3: Gravações e Desfazer com continuidade
**Files:** `ui/core/fluxo.js`, `ui/screens/map.js`, `ui/screens/curve.js`, `ui/screens/refino.js`, `ui/core/curve-to-map.js` e testes correspondentes.
- [ ] RED: escrita manual A, escrita manual B, Desfazer, falha ACK/readback, crash após etapa 1, cabo desconectado, retomada sem escrita automática.
- [ ] GREEN: persistir plano de duas fases, manter botão principal e selo não enganoso; checar backups e dados.
- [ ] Revisão independente de todos os caminhos de escrita; não alterar qualquer frame da ECU.

### UT 4: Diário + Coerência determinísticos
**Files:** `ui/core/diario.js`, `ui/core/coerencia.js`, `session/DiarioStore.kt`, adaptadores de evento e testes.
- [ ] RED replay real/fixtures com sugestão, gravação manual, readback, resultado e nova sugestão conflitante.
- [ ] GREEN: diário persistente, regras de coerência e UI 'aguardando mais dados'; nunca usar LLM ou escrever automaticamente.

### UT 5: Interface/performance e provas de três resoluções
**Files:** UI/CSS existentes, helpers Playwright, `tools/ci/sem_rolagem_abas.cjs`, contratos de leveza; testes focados.
- [ ] Integrar blocos UX selecionados do canvas e alternativa sem reduzir cinco áreas e sem remover controles.
- [ ] Medir baseline/candidata: primeira tela, toque, gráfico, mutações de aba oculta e Ferramentas; não confundir contagem de scripts com fluidez real.
- [ ] Capturas e checagem de texto/alvos/ação e mudança de estados em 1280×720, 1024×600 e 800×480.

### UT 6: APK ARM32 e integração final
**Files:** `.github/workflows/apk.yml`, `tests/test_aura_arm32_apk_contract.py`, testes/CI e metadados release.
- [x] Redefinir ABI do workflow de build/lint para `armeabi-v7a` com contrato estático; commits `8cfa92d`, `e97c232`.
- [ ] Verificar resultado completo da CI no SHA e testes de arquitetura/índices do pacote. Testar APK com `aapt` e `unzip` quando gerado.
- [ ] Rever PR #185, verificar que todo o checklist está cumprido, atualizar planos vivos e só então marcar PR pronto e fazer merge com SHA travado.
- [ ] Executar CI da **canônica após merge**, e gerar APK **da canônica exata**, com checksum, link e metadados no Drive/APKS.
- [ ] Distinguir claramente 'APK construído/testes automatizados' de 'instalado e provado na RK3326'.
- [ ] Após publicação comprovada, suspender a automação `Ômegas Aura | fechamento` (horária).

## Estado e retomada
O ledger de pendências é `docs/handoff/PENDENTE.md`; a cada marco actualizar lá uma linha curta de estado com SHA, teste/prova e bloqueio. Não apagar itens abertos nem transformar hipótese em prova. Automação horária já criada. Este plano é operacional, não afirma que o app foi finalizado.
