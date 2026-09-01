# Guardrails de engenharia — OMEGAS V8.2

## Mudança

- Fetch/compare fresh antes de mutar; nunca sobrescrever mudança concorrente.
- Branch isolado até a bateria completa ficar GREEN no mesmo SHA.
- TDD para comportamento novo; falha inesperada exige investigação de causa.
- Não alterar ciência para satisfazer teste obsoleto. Corrigir harness quando a autoridade mudou.
- Não reparsear logs quando o cache derivado e verificado já responde à pergunta.

## Ciência

- Preservar densidade local; não reduzir milhares de frames a uma sessão.
- Separar conhecimento local de persistência/transferência.
- Sugestão global requer visitas independentes e cobertura em pelo menos duas regiões físicas RPM×MAP.
- Tendência global nunca aparece como correção local.
- Curva K e Mapa K nunca viram uma variável única.
- Predictor só entra no Android após ganho held-out, zero leakage e caudas não degradadas.

## Produto e UI

- Um Store, um Router, um Scheduler e uma política de escrita.
- Alvos de toque de 48 px; texto operacional legível; sem truncamento crítico.
- Um dono de rolagem por painel; nenhuma rolagem horizontal operacional.
- Ações primárias permanecem visíveis em 1280×720.
- Mostrar primeiro: condição, evidência, diferença, situação e próximo passo.
- Jargão, procedência e massa estatística ficam em divulgação técnica recolhida.

## Release

- Rodar todos os testes JavaScript, contratos Python, testes Android e assemble no mesmo SHA.
- Publicar APK e SHA-256 somente após GREEN.
- AUTO_WRITE_ECU=false permanece obrigatório.
- Sem teste no veículo: nenhum claim de economia, dirigibilidade ou calibração ótima.
