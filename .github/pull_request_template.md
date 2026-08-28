## WorkUnit e objetivo

- Issue:
- WorkUnit:
- Resultado observável:

## Escopo

- [ ] Uma branch e um PR para a WorkUnit
- [ ] UI/UX congelada, salvo decisão explícita do owner
- [ ] Sem escrita automática na ECU
- [ ] Mapa K e Curva K continuam separados
- [ ] Predictor falha fechado sem suporte/confiança
- [ ] Sem autoridade técnica duplicada fora do repositório

## Evidência

- [ ] `python3 -B tools/run_checks.py`
- [ ] simulações/testes afetados
- [ ] `testDebugUnitTest`
- [ ] `lintDebug`
- [ ] `assembleDebug`
- [ ] artifact, SHA do source e SHA-256 do APK registrados

## Segurança de escrita, quando afetada

- [ ] leitura antes da edição
- [ ] preparar e revisar não escrevem
- [ ] confirmação humana inequívoca
- [ ] falha de ACK não é sucesso
- [ ] readback divergente não é sucesso
- [ ] tela reflete o readback real

## Limites e rollback

- Validação física:
- Riscos residuais:
- Estratégia de rollback:
