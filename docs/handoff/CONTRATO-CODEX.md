# Contrato para o Codex (e qualquer agente) — pedidos do dono, sem deixar nada passar

Leia antes: AGENTS.md, docs/handoff/PENDENTE.md, docs/progbase/RETOMAR.md, .claude/skills/emular-por-observacao/SKILL.md, .claude/skills/entender-dono/SKILL.md.
Regras permanentes: app NUNCA grava sozinho (2 toques + leitura de volta); nada por tempo; não burlar licença; não versionar .exe/DLL/License.dat; TDD RED→GREEN; idempotente; marcas OBS-COD/OBS-FIO/DERIVADO/INFERIDO/DESCONHECIDO; commit+push a cada marco; atualizar PENDENTE.md e PROGRESSO.md.

## 1. ECU SIMULADA para rodar o original (prioridade do dono)
O ProgBase roda sob Wine com a licença do dono (tools/progbase/executar_original.sh). Sem ECU, as telas ficam vazias e funções (ex.: "Calibração automática da central", TFormCalibra) não aparecem. Fazer: porta serial virtual (socat/pty) ligada ao tools/emulacao/ecu_emulator.py (já regenera 100% da captura) → o original "vê" a central do dono. Então capturar TUDO: cada tela, botão, aba que aparece, campo que expande, alerta, causa→efeito (print + quadros enviados/recebidos por ação), inclusive F2 visualização, F3 diagnóstico, calibração rápida, AutoCal, gráficos, mapa/coeficientes K, bins personalizados. Saída: docs/progbase/capturas/<tela>/<acao>.png + .json (quadros) + .md leigo.
## 2. Artefato explorável para o DONO (leigo) e para agentes
HTML único (docs/progbase/explorar/index.html) que emula cada tela do original (do DFM + capturas), com cada botão/campo explicando em 1 frase: o que é, o que acontece ao mudar, causa→efeito, e o equivalente no OMEGAS (se existe). Mesma fonte alimenta o grafo (graph.json) — um dado, duas vistas. Evidence ids (REA e MAN-) coerentes no INDEX.md.
## 3. Guia do app: reescrever curto e objetivo
O PDF atual (docs/manual) está longo e ramificado. Regra: 1 página por tela; cap. "Carro apagando" com UMA sequência: o que apertar, em que ordem, o que esperar, como saber se melhorou. Responder explicitamente: consumo de gasolina sobe? quando o motor usa só gasolina? Sem ramificações. Preferir o app autoexplicativo ao manual.
## 4. Inteligência por trás, interface enxuta (próximas versões)
- Diagnóstico automático das sessões: detectar os apagões/quase-apagões (embreagem pisada: parada, ré, quebra-mola, redução), classificar, e SUGERIR o ajuste (anti-apagão e seus 5 números, marcha lenta, troca 1200→1600) com botão "Aplicar" (2 toques). Nunca aplicar sozinho.
- Marcha lenta: escrita (duas gravações, ordem segura: de Gás→outro modo a rotação vai primeiro; para Gás o bit vai primeiro) com Desfazer.
- Perfil da central: identificar modelo/versão (ex.: OMEGAS 4) e só mostrar/enviar o que ela suporta (MASK_FUNCTION; NACK CA como sinal).
- Mapa K: ler bins de RPM e injeção direto da ECU; mostrar 0 ms no ajuste local (hoje só 2–18 ms; 0 ms só no global); bins personalizados como no original (adicionar linha) — amarrar com a Curva K.
- Anti-apagão inteligente: usar a telemetria para propor os 5 números a partir dos apagões reais do dono.
- Telas do original úteis ainda não no app: calibração rápida, diagnóstico (erros, tempos de funcionamento), visualização F2.
## 5. Repositório
Limpar menções ao fabricante/legado em app/ (comentários/textos); pesquisa fica em docs/progbase. Enxugar CI/testes (ponytail-audit). Copiar docs/progbase para o Drive (PROTOCOLO-LANDI). APK extraído no Drive pasta OMEGAS como "OMEGAS versão nova".
## Síntese das mensagens do dono (ordem)
1. O carro apaga quando pisa na embreagem: parar, ré, quebra-mola, reduzir. Quer resolver isso primeiro.
2. Interface para leigo, autoexplicativa como a tela AutoCal; zero jargão; campo editável com lápis; interruptor Ligado/Desligado.
3. Nunca mais abrir o .exe: tudo destrinchado, versionado, com evidence ids coerentes, grafo e artefato explorável.
4. Pensar como 2026: usar telemetria para automatizar o que no original é manual, sem encher o app de função inútil.
5. Economizar tokens: Codex ao máximo com inteligência; checkpoints para nunca refazer.

## 6. Workflows e auto-auditoria (pedido do dono)
- Substituir os workflows legados (`ci.yml` "OMEGAS VERMELHO/PLATINA", `verde-apk-now.yml`) por DOIS workflows novos, limpos e sem nome legado: `ci.yml` (testes JS/Python/Kotlin/lint + grafo) e `apk.yml` (build + artefato nomeado `OMEGAS-<versão>-<sha>.apk`). Atualizar tests/test_missao_final_contract.py (só 2 workflows). Remover todo texto/nome legado dos workflows.
- Auto-auditoria barata a cada execução: `tools/auditoria.py` (idempotente, < 30 s) que gera `docs/handoff/AUDITORIA.json` com: testes verdes/vermelhos por grupo, itens de PENDENTE.md por estado, cobertura % da decompilação (COBERTURA.md), nº de evidence ids citados × existentes, nós do grafo × arquivos existentes, último SHA/CI. O job `grafo` do ci.yml roda isso e falha se algo citado não existir. PENDENTE.md é o único ledger: nenhum item pode ser removido — só marcado FEITO (com SHA/teste) ou BLOQUEADO (com causa + menor próximo passo).
- Contrato de não-duplicação: antes de criar arquivo/função/teste, `graphify query` + grep; se existir, reaproveitar. Checkpoint PROGRESSO*.md atualizado antes de parar qualquer agente.

## 7. Velocidade do ciclo mensagem → APK (pedido do dono)
- Testes locais só os do arquivo tocado (RED→GREEN); a suíte completa roda no GitHub (remoto, sem gastar tokens). Nunca rodar a suíte inteira localmente só para "conferir".
- CI paralela por grupo (JS / Python / Kotlin / e2e) e cache do Gradle; APK: build enxuto (um ABI/arm64, sem debug symbols, R8), meta < 6 min.
- Agentes: 1 por arquivo/tema, prazo máximo, checkpoint em arquivo; relatórios em ≤10 linhas; controlador não lê transcript, só o relatório.
- Testes com criatividade: fluxo (toque a toque), esperado × inesperado (NAK, silêncio, USB caído, ECU mudou no meio), mutação (estrague a regra e o teste tem que falhar), evidência citada.
- Medir o ciclo: tools/auditoria.py registra tempo da CI, do APK e nº de rodadas de correção por entrega; meta cair a cada versão.

## 8. Testes: o mínimo local (pedido do dono)
- Regra: NÃO rodar testes localmente, salvo os impossíveis no GitHub (ex.: precisa do .exe/log fora do repo). Teste local permitido só: 1 arquivo, 1 vez, para provar RED→GREEN do que acabou de ser escrito. Suíte completa = só na CI.
- GitHub Actions (repo privado, 10.000 min/mês): SEM poda de testes e sem meta fixa de minutos. Eficiência vem de DIVIDIR: um job de 20 min vira 4 jobs paralelos de 5 min (matrix por grupo), cache Gradle/npm, e nada repetido. Mesmo princípio localmente: tarefa longa = várias partes em paralelo com subagentes.
- tools/auditoria.py registra minutos de Actions gastos por execução (via API) e alerta aos 70% do orçamento.

## 9. Artefato MOCK-UP didático (pedido do dono, prioridade logo após ECU simulada)
HTML único e idempotente (docs/progbase/explorar/index.html, gerado por tools/progbase/gerar_explorar.py a partir do DFM + docs/progbase/abas + capturas + grafo): simula cada tela do ORIGINAL (todas as abas/funções) de forma interativa e hiper-didática — cada botão/campo explica, em português simples, o que é, o que acontece ao mudar cada valor (causa→efeito), comando enviado e resposta esperada (com evidence id), e o equivalente/ideia de inteligência para o OMEGAS. Seção separada "Mock-up do OMEGAS" com as telas atuais + as possibilidades inteligentes (diagnóstico, perfil da central, bins), sem transbordar para o app real. Navegação por partes/abas como no original; busca; visão para leigo (padrão) e para agente (detalhes).
