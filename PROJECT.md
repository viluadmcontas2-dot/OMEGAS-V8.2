# Projeto OMEGAS V8.2 RED Science Blend

## Objetivo

Aprender com o histórico do próprio carro para reduzir a diferença gasolina × GNV e acelerar uma calibração manual segura. Economia de combustível permanece objetivo experimental, não claim provado.

## Modelo

- Condição física: `RPM × MAP`.
- Resposta: distribuição de `Petrol Inj.` observada nessa condição.
- Local: densidade, centro robusto, dispersão e multimodalidade.
- Persistência: sessões, epochs, walk-forward, drift e mudança de calibração.
- Curva K: 30 pontos, tendência global por Petrol Inj.
- Mapa K: 12×12, residual local projetado no eixo físico da ECU.
- AutoCal: 18 zonas de aquisição, separadas de Curva K e Mapa K.

## Produto

- Aprender responde: gasolina esperada, GNV observado, diferença aprendida, situação e próximo passo.
- AutoCal mostra estado nativo em modo somente leitura.
- Sugestão global só nasce com cobertura em duas regiões físicas; não é replicada como correção local.
- Predictor continua experimental e RED permanece fallback.

## Segurança

Nenhuma escrita automática. Toda escrita continua em fluxo manual com revisão, confirmação, ACK e readback.

## Governança ativa

- Branch alvo: `work/red-v82-science-blend`
- Branch de revisão: `work/red-v82-global-evidence-fix`
- PR: #12
- Issue: #11
- Constituição e guardrails: `governance/`
