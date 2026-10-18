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
