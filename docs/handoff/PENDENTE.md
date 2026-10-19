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

- [ ] CORREÇÃO DE ESCOPO (dono, vendo as capturas): o cartão Anti-apagão do app deve ter INTERRUPTOR + UM número ('Rotação máxima para ativar', 1600 rpm), como no original (docs/progbase/capturas/originais/LEIA-ME.md). Remover da interface os outros 4 números (não existem na tela original; não foram vistos funcionando). Regra geral do dono: só mexer no que foi visto funcionando nas telas do original. Testes: card mostra 1 campo; escrita do elemento 0 do vetor 307 (`14 33 01 00 lo hi cs`) com 2 toques+readback — CONFIRMAR o quadro no log antes (o dono não editou o número no log; 0 escritas `14 33 01`).
- [ ] Bins: dar ao Mapa K a janela 'Centros das células' (12 rpm × 12 ms editáveis, OK/Cancelar) e testar se o original aceita 0 ms (capturar).
- [x] 2026-10-19 (dono): APK com cartões novos (Anti-apagão 1 número + extras experimentais, Volta automática para gasolina, bins no Mapa K com teste de aceitação 0 ms por leitura de volta), SEM carga sob demanda de telas, gravação rápida Mapa K/Curva K, nível do gás+pressão registrados — agentes em PROGRESSO-CARTOES.md e PROGRESSO-VELOCIDADE.md
- Evidência 'volta por pressão baixa' (log novo): byte de nível (13) = 125 em TODAS as 14 932 amostras (tanque cheio) e pressão ≥ ~1,97 bar (limiar do dono 1,25 bar) → este log NÃO tem volta por pressão baixa; falta sessão com tanque na reserva. O app passa a registrar nível+pressão+rpm no instante de cada troca gás→gasolina.
- [x] EVIDÊNCIA volta por pressão baixa (corpus de 89 sessões, tools/sessoes/corpus.py): pressão normal do gás em GNV ≈ 2,3–2,5 bar (p5=2,30). Das 28 trocas GNV→gasolina, 8 ocorreram com pressão MUITO abaixo do normal (0,26; 0,48; 0,58; 0,70; 0,93; 1,07; 1,28; 1,34 bar; sessões 080, 071, 056) = a função 'Automatic change back to petrol' (limiar do dono 1,25 bar). Correção da nota acima: o log novo é de tanque cheio, mas o corpus TEM o caso. Hipótese do dono plausível: baixar o limiar (ex.: 1,25→1,0 bar) segura o gás mais tempo na reserva; o cartão novo permite editar; o app registra nível+pressão a cada troca.

## Cartões Anti-apagão / Volta à gasolina / Bins (2026-10-19) — detalhes em PROGRESSO-CARTOES.md
- Volta automática à gasolina: caixa F4 não é bit de FLAG_CONF1 (o 0x0040 da palavra 1 é "pressão baixa impostável"); decodificar CheckRitornoBenzinaClick 0x4A9484 e EditBassaPressioneMpKeyPress 0x4C526C (Unicorn) antes de gravar. Tempo = SC 62 u8.
- Bins K: RPM = SC 61 (`14 3D 00 idx lo hi`); linhas de ms: vetor NÃO provado (SC 120 é MAP_ESTERNO). Editar só RPM quando o escritor do Mapa K estiver livre; teste "A central aceitou 0 ms".
- Nível do gás e pressão no estado + evento `voltou_para_gasolina` nas sessões.
