# Contexto do dono e forma de trabalho (LEIA PRIMEIRO — vale para Claude, Codex/GPT e qualquer agente)

## Quem é e como fala
Dono do OMEGAS: leigo em programação ("vibe coder"), escreve rápido, erra termos, usa metáforas (ex.: "app de banco" = simples para quem usa, complexidade escondida). Entenda a INTENÇÃO, corrija termos em silêncio, questione quando houver caminho melhor (inclusive reaproveitar código pronto). Responder em português do Brasil, simples, curto, objetivo. Nunca jargão (bit, flag, hex, rpm/ciclo) na conversa com ele nem na interface. Detesta repetir pedidos: tudo aqui já foi pedido.

## Problema real (prioridade 1)
O carro (GNV, central OMEGAS 4/MP48) APAGA ao pisar na embreagem: parando, dando ré, saindo de quebra-molas, reduzindo, saindo do lugar. APK v8 (commit eb8c9e8, run 39174963145) liga o ANTI-APAGÃO (botão manual). Só o carro confirma o efeito. 2º passo: modo "Segurar com gasolina na parada" (marcha lenta), troca 1200→1600 rpm só se a evidência das sessões apoiar.

## Pedidos permanentes (síntese cronológica das mensagens longas)
1. APP NUNCA GRAVA SOZINHO: toda escrita na central = ação manual com 2 toques + leitura de volta + Desfazer. AutoMatch manual continua manual. O app só SUGERE.
2. NADA POR TEMPO: tudo por evento/gatilho (primeiro quadro válido, mudança de contagem, resposta da central, toque). Esperar a central = esperar RESPOSTA.
3. INTERFACE DIDÁTICA, premium e leve, para quem nunca ouviu falar de GNV/ECU. Modelo de coerência: tela AutoCal (botão diz o que faz + estado escrito, 1 linha de apoio). Campo digitável com lápis; interruptor Ligado/Desligado; cartões por assunto; Sessões em aba própria; sem guia de termos na interface (auto-explicativa); zero jargão. Alvo: MULTIMÍDIA de carro, paisagem 1280x720/1024x600/800x480, alvos de toque grandes. Menos carga cognitiva: o app entrega funcionalidade, não carga.
4. INTELIGÊNCIA POR TRÁS, interface enxuta (pensar como 2026): usar a telemetria para automatizar o que no original é manual — diagnóstico das sessões (onde apaga, por quê), sugerir anti-apagão/marcha lenta/troca, perfil da central (só oferecer o que ela suporta), leitura automática dos bins de RPM/injeção, Mapa K com 0 ms no ajuste local e bins personalizáveis (adicionar linha). Só o que for útil e didático; nunca encher o app.
5. NUNCA MAIS ABRIR O .EXE: decompilar TUDO (telas, botões, comandos, respostas, funções), versionar (docs/progbase), evidence ids coerentes (REA + MAN-), Graphify específico, artefato explorável. Sempre invocar a skill reverse-engineer-anything e as ferramentas REA (verify_reconstruction, audit_residual_unknowns, compare_functions...). Emular por observação (CPU emulada Unicorn + ECU simulada), NUNCA contornar a licença; o original roda sob Wine com o License.dat do dono (não versionar). Falta: ECU simulada ligada ao original para capturar cada tela/ação.
6. REPOSITÓRIO LIMPO E AUTORAL: app/ sem menção ao fabricante/legado (pesquisa fica em docs/progbase); sem workflows/arquivos legados; 2 workflows novos (ci.yml, apk.yml) sem nomes legados; docs só atualizados.
7. EFICIÊNCIA: testes locais mínimos (só o impossível no GitHub; 1 arquivo, 1 vez, RED→GREEN); suíte inteira na CI remota, dividida em jobs paralelos (sem poda); tarefas longas divididas em subagentes paralelos com prazo, checkpoint em arquivo e ping aos 15 min; usar Codex ao máximo com inteligência; economizar tokens.
8. TDD RED→GREEN, mutação, testes de fluxo/esperado/inesperado, evidência (OBS-COD/OBS-FIO/DERIVADO/INFERIDO/DESCONHECIDO). Idempotente, autônomo, contratual: nada feito duas vezes, nada pulado. Cada execução se auto-audita (tools/auditoria.py → AUDITORIA.json).
9. PDF/guia: curto e objetivo (1 página por tela; capítulo "Carro apagando" com UMA sequência; responder: consumo de gasolina sobe? quando usa só gasolina?).
10. Cobrir pontos cegos (ex.: simular a ECU), questionar o pedido, gravar aprendizados na memória (skills em .claude/skills).

## Onde está o resto
AGENTS.md · docs/handoff/{PENDENTE,CONTRATO-CODEX,HANDOFF-CODEX}.md · docs/progbase/{RETOMAR,INDEX}.md (+PROGRESSO*.md por frente) · docs/mapa-de-fontes/MAPA.md · docs/manual/OMEGAS-guia-do-app.pdf · docs/evidence/ · STATUS.md · .claude/skills/{entender-dono,emular-por-observacao}.


## Regra sem-atrito (2026-10-19)
Um toque = ação feita. Sem pergunta de confirmação, sem janela, sem 2º toque, sem botão "tentar de novo". Conferência em segundo plano, autocorreção, aviso sutil só se falhar, Desfazer no cartão. Teste: `tests/ui/anti-apagao-toque-real.test.cjs`. Vale para toda função nova (volta automática, marcha lenta, cartões).
