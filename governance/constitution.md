# OMEGAS V8.2 — Constituição

## Missão

Aprender com o histórico do próprio veículo e transformar evidência em orientação manual auditável para calibração GNV, sem alegar economia ou melhoria física antes de validação no carro.

## Princípios imutáveis

1. O repositório e a Issue #11 são a autoridade operacional.
2. RED permanece baseline e fallback.
3. Todo frame válido é evidência local; sessão e epoch medem persistência e transferência.
4. RPM × MAP define a condição física. Petrol Inj. é a resposta observada nessa condição.
5. AutoCal 18 zonas, Curva K 30 pontos Q14 e Mapa K 12×12 são dimensões distintas.
6. Observação não é causalidade. Confiança não é probabilidade de melhora.
7. Nenhuma sugestão grava ECU. Escrita exige revisão humana, confirmação, ACK e readback.
8. P_IMPROVE_PROVEN=false e VEHICLE_PROVEN=false até prova específica.
9. UNKNOWN permanece UNKNOWN; não há forward-fill sem cadeia provada.
10. Um release só existe quando código, testes, identidade, artefato e SHA pertencem ao mesmo commit.
