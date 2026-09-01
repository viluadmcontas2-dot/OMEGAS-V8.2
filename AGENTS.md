# OMEGAS V8.2 RED — contrato operacional

## Autoridade

- O repositório, a branch `work/red-v82-science-blend` e a Issue #11 são a autoridade operacional.
- Retomada: `AGENTS.md` → `PROJECT.md` → `STATUS.md` → `governance/` → WorkUnit ativa → Issue #11.
- Trabalho mutável acontece em branch isolado, com comparação fresh de parent/head.
- RED `b637f5fff19b1ece93f22d1fced9640618609a60` permanece baseline/fallback.
- Chat e memória externa são contexto, nunca autoridade técnica.

## Contrato físico

- RPM × MAP identifica a condição física; Petrol Inj. é a resposta observada.
- Todo frame válido conta para conhecimento local. Sessão/epoch mede persistência, drift e transferência.
- Não misturar: AutoCal 18 zonas; Curva K 30 pontos Q14; Mapa K 12×12; superfícies observacionais; sugestões.
- Curva K é tendência global por Petrol Inj.; Mapa K é residual local depois da tendência global.
- Sugestão global exige pelo menos 2 visitas e 2 regiões físicas RPM×MAP.
- Evidência global nunca vira sugestão local.
- UNKNOWN não é preenchido por conveniência.

## Engenharia

- TDD: RED pelo motivo esperado → implementação mínima → GREEN → regressão completa.
- Falha inesperada exige systematic debugging.
- Não corrigir dado nem afrouxar ciência para satisfazer teste.
- Reusar cache verificado; não reparsear corpus sem necessidade.
- Antes de concluir, todos os testes JavaScript, Python e Android e o APK devem pertencer ao mesmo SHA.

## Segurança

- Observar, aprender, prever, abrir editor e preparar proposta não escreve na ECU.
- Escrita é manual: preparar → revisar → confirmar → ACK → readback.
- AUTO_WRITE_ECU=false.
- Predictor só muda runtime após ganho held-out, zero leakage e caudas não degradadas.
- P_IMPROVE_PROVEN=false e VEHICLE_PROVEN=false até prova específica no carro.

## UI operacional

- Alvo 1280×720, toque mínimo 48 px, texto legível e sem truncamento crítico.
- Um dono de rolagem por painel; sem rolagem horizontal operacional.
- Mostrar primeiro condição, evidência, diferença, situação e próximo passo.
- Detalhes estatísticos e procedência ficam recolhidos, mas auditáveis.
