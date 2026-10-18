# Pendente — ledger único (lido no início de cada sessão; atualizado a cada marco)

## Agora (APK urgente: o carro apaga ao sair do lugar e ao parar/pisar na embreagem)
- [ ] Interface para leigo na multimídia (paisagem 1280x720, 1024x600, 800x480): estilo da tela AutoCal, sem jargão
- [ ] Cartão "Anti-apagão" (ligar/desligar + 5 números editáveis, Restaurar fábrica) — fluxo: prévia, 2º toque, ACK, leitura de volta, Desfazer
- [ ] Cartão "Marcha lenta" (Só gás / Segurar com gasolina na parada / Sempre gasolina na parada) — app só SUGERE
- [ ] Leveza (orçamentos de nós/timers/renders) + testes e2e de toque verdes
- [ ] CI verde → APK (verde-apk-now.yml) → STATUS.md v8
- [ ] Limpar menções ao fabricante/legado do app/ (comentários, textos, nomes); pesquisa fica fora do app

## Depois do APK (visão do dono: inteligência por trás, interface enxuta)
1. Graphify didático do ProgBase (aba → controle → comando → resposta → evidência → equivalente no app)
2. Cobertura: % do original já reconstruído (funções/abas/comandos) e backlog só do que falta
3. Perfil da central: identificar modelo/versão da ECU e só oferecer/enviar o que ela suporta (MASK_FUNCTION, NACK `CA` como sinal)
4. Diagnóstico inteligente: analisa a sessão, acha onde apaga, sugere (nunca aplica sozinho) anti-apagão, marcha lenta, saída do corte
5. Auditoria REA da decompilação (verify_reconstruction, audit_residual_unknowns) + cópia de docs/progbase para o Drive (PROTOCOLO-LANDI) + enxugar repo/CI/testes (ponytail-audit). Codex volta ~23:55: usar ao máximo.
6. Memória entre sessões: cada marco grava aprendizados (skills em .claude/skills, este arquivo)

## Regras permanentes
App nunca grava sozinho (sempre 2 toques + leitura de volta). Nada por tempo. Não burlar licença. Não versionar .exe/DLL. Sem segredos.
