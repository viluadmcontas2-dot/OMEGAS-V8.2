# Pendente — ledger único (lido no início de cada sessão; atualizado a cada marco)

## Agora (APK urgente: o carro apaga ao sair do lugar e ao parar/pisar na embreagem)
- [ ] Interface para leigo na multimídia (paisagem 1280x720, 1024x600, 800x480): estilo da tela AutoCal, sem jargão
- [ ] Cartão "Anti-apagão" (ligar/desligar + 5 números editáveis, Restaurar fábrica) — fluxo: prévia, 2º toque, ACK, leitura de volta, Desfazer
- [ ] Cartão "Marcha lenta" (Só gás / Segurar com gasolina na parada / Sempre gasolina na parada) — app só SUGERE
- [ ] Leveza (orçamentos de nós/timers/renders) + testes e2e de toque verdes
- [x] FEITO (eb8c9e8, run 39174963145): APK v8 gerado e entregue no chat → APK (verde-apk-now.yml) → STATUS.md v8 → enviar APK extraído ao Drive, pasta OMEGAS (id 1eK0IISPKFeY4FRaCDrYZcSY_zPLsWoIq), nome 'OMEGAS versão nova' (limite do conector: 10 MB; se maior, avisar o dono com o link do artefato da CI)
- [ ] Limpar menções ao fabricante/legado do app/ (comentários, textos, nomes); pesquisa fica fora do app

## Depois do APK (visão do dono: inteligência por trás, interface enxuta)
1. Graphify didático do ProgBase; cada nó de evidência carrega o evidence id do REA e o caminho do doc (um só índice: docs/progbase/INDEX.md → doc → evidence id → função/endereço). Teste de integridade: todo evidence id citado em docs existe no bundle exportado e todo nó do grafo aponta para um arquivo existente (aba → controle → comando → resposta → evidência → equivalente no app)
2. Cobertura: % do original já reconstruído (funções/abas/comandos) e backlog só do que falta
3. Perfil da central: identificar modelo/versão da ECU e só oferecer/enviar o que ela suporta (MASK_FUNCTION, NACK `CA` como sinal)
4. Diagnóstico inteligente: analisa a sessão, acha onde apaga, sugere (nunca aplica sozinho) anti-apagão, marcha lenta, saída do corte
5. Auditoria REA da decompilação (verify_reconstruction, audit_residual_unknowns) + cópia de docs/progbase para o Drive (PROTOCOLO-LANDI) + enxugar repo/CI/testes (ponytail-audit). Codex volta ~23:55: usar ao máximo.
6. Memória entre sessões: cada marco grava aprendizados (skills em .claude/skills, este arquivo)

- [ ] Workflows novos sem legado (ci.yml + apk.yml) + tools/auditoria.py + AUDITORIA.json no job grafo (CONTRATO-CODEX §6)

## Regras permanentes
App nunca grava sozinho (sempre 2 toques + leitura de volta). Nada por tempo. Não burlar licença. Não versionar .exe/DLL. Sem segredos.

## Marcha lenta — escrita (próxima versão)
# PENDENTE — escrita da Marcha lenta (não implementada)
Provado no emulador (tools/emulacao/cpu_setter_original.py --modo-lenta, RadioGasClick 0x4BFAD8 executado; ECU do dono 80D=0):
- Gás:      35 03 00 86 24 51 10 43 ; 14 A1 00 00 00 00 B5
- Volta:    35 03 00 86 24 51 10 43 ; 14 A1 00 00 4C 04 05
- Gasolina: 35 03 00 86 24 51 11 44 ; 14 A1 00 00 4C 04 05   (bit = 0x0100 da palavra 1, NÃO 0x0400)
(w0/w1 do FLAG_CONF1 e GIRI[1] são preservados; GIRI vai por indexado 0x14 só no elemento 0.)
Falta: IdleModeCore/Writer (Kotlin, ordem: rpm 0->1100 primeiro, 1100->0 bit primeiro; 2ª falha = PARTIAL + Desfazer), flow JS, ponte, testes JUnit/ECU emulada.
O handler original NÃO confere a 1ª gravação antes da 2ª (provado).

## Depois do APK (UX leigo, 2026-10-18)
- AutoCal, Refino, Ajuste local e Ajuste global ainda dizem "ECU" e "RPM"/"MAP" (teste `linguagem-leiga` marca como todo). Trocar por "central"/"Rotação".
- 1024x600 e 800x480: só Sessões e Ferramentas foram redesenhadas; AutoCal/Mapa K/Curva K cortam conteúdo nesses tamanhos (diagnóstico em `tools/ci/sem_rolagem_abas.cjs`).
- Marcha lenta: só leitura + sugestão; gravar o modo fica pendente (escrita não implementada).
- Faixas do anti-apagão: máximo do DFM (OBS-COD), piso 0 (DERIVADO), ordem campo→posição INFERIDA; aviso "Longe do valor de fábrica" a ±50% (INFERIDO).
- Kotlin (AntiStalloParamsCore/Writer + testes JUnit): compilados e rodados localmente (40 testes) com kotlinc 2.0.21; Gradle/lint só na CI.
- Antigos testes do Avançado (avancado-painel-dom, e2e-avancado-toque) foram substituídos por ux-fluxo-toque e campos-editaveis (mesmas garantias na nova tela).

## Limpeza e costura (Codex) — NÃO apagar às cegas
- [ ] Listar com `git grep -il "platina\|vermelho\|legado\|landi\|aeb"` os .md/workflows/testes/nomes legados; para cada um: reescrever (se útil) ou remover (se obsoleto), sempre com teste de contrato atualizado (tests/test_missao_final_contract.py)
- [ ] Mover documentação de pesquisa do fabricante para docs/progbase; app/ sem menção ao fabricante
- [ ] Mock-up didático (CONTRATO-CODEX §9) · ECU simulada no original (§1) · guia curto (§3) · workflows novos + auditoria (§6) · marcha lenta gravável · diagnóstico inteligente · bins do Mapa K
- [ ] Graphify: rodar `graphify update .` + tools/grafo_conhecimento.py + tools/progbase/grafo_progbase.py; INDEX.md com evidence ids (REA + MAN-); REA verify_reconstruction (bloqueado por timeout no .exe: usar CLI em segundo plano)

- [ ] Branches: legadas renomeadas para apagar/* (Codex, ordem do dono); depois que o Codex principal terminar, renomear a branch atual para um nome semântico (ex.: OmegasEsmeralda), parar o autocommit/Codex antes, `git branch -m`, atualizar upstream e a variável de branch nos scripts

- [ ] PRIORIDADE 1: bins de RPM/injeção personalizáveis (original × OMEGAS; 0 ms no ajuste local; adicionar linha) — ver CODEX-AUTONOMO.md
- [ ] PRIORIDADE 2: README profissional + docs/README.md + estrutura enxuta

- [ ] PRIORIDADE 0: log novo omegasaudit.LOG (97 MB, ~810 mil eventos) + capturas do original (Drive CAPTURAS ORIGINAIS): fixtures, matriz comando×resposta, contraste REA, PerfilCentral (app só oferece o que a central suporta)

## Retroativo + contratos (pedido do dono 2026-10-19; Claude com poucos tokens → Codex executa)
- [ ] Log novo: fixtures + pareamento req→resp + matriz nova (docs/progbase/bins/LOG-NOVO-omegasaudit-achados.md tem os 1ºs achados: anti-apagão OBS-FIO; bins RPM = vetor 0x3D por elemento)
- [ ] CONTRATO DE COERÊNCIA (retroativo): para CADA função do app (Ferramentas, Anti-apagão, Marcha lenta, AutoCal, Mapa K, Curva K, Sessões): arquivo JS/Kotlin → ponte → comando → resposta → estado na tela; teste e2e no WebView-like (scripts em ordem, sem cache, console.error = falha) + diagnóstico visível no app (estado claro: 'Central conectada / lendo / sem resposta / não suportado por esta central'). Nenhuma aba pode abrir vazia: erro de módulo mostra cartão de falha com 'Tentar de novo'. Gerar o grafo função→ponte→comando→teste no Graphify e falhar a CI se faltar elo.
- [ ] Bug: aba Ferramentas vazia no WebView real (docs/handoff/PROMPT-CORRECAO-TELA-FERRAMENTAS.md; GPT já mexeu em tools: commit 3e844c2)
- [ ] AutoCal (multimídia): botão ao lado de cada zona para APAGAR os pontos daquela zona, 1 toque, sem confirmação, limpo; balão de informação não pode ficar por cima ao alterar algo
- [ ] Manuais públicos referenciados no grafo (MAN-*), docs sem duplicatas: um .md por assunto, sem arquivos repetidos/contraditórios (auditar docs/ com tools/auditoria.py)
- [ ] Meta: 100% de cobertura do ProgBase (COBERTURA.md) e réplica fiel dos comandos úteis ao app
