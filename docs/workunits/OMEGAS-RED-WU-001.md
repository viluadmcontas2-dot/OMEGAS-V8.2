# OMEGAS-RED-WU-001 — Aprendizado contínuo rápido

**Issue:** #9  
**Branch:** `hotfix/v8.0-red-performance`  
**Estado:** `ENGINEERING_COMPLETE`  
**SHA provado:** `e8c446b3cbd54194a8bc8b805b44e2770e252a93`

## Resultado

A WorkUnit entrega uma superfície explicável que reutiliza imediatamente cada comparação válida:

- a Curva K recebe a tendência global por Petrol Inj.;
- o Mapa K recebe somente o residual local previsto em RPM × MAP;
- células próximas herdam sinal com decaimento e incerteza;
- células distantes ficam `GLOBAL_ONLY` e não recebem correção local;
- cada tela distingue agregado, par observado e previsão;
- suporte e visitas independentes são exibidos sem transformar repetição bruta em certeza.

## Requisitos fechados

1. procedência exata do par: `PASS`;
2. agregado versus observação versus previsão: `PASS`;
3. tendência global: `PASS`;
4. residual contínuo RPM × MAP: `PASS`;
5. projeção física 12 × 12: `PASS`;
6. suporte, independência e incerteza: `PASS`;
7. sugestões manuais globais e locais: `PASS`;
8. política de novos alvos 100–180: `PASS`;
9. ausência de bloqueio por RPM: `PASS`;
10. Auto-Cal acessível e abrindo de forma estável: `PASS`;
11. cálculo bounded fora do hot path: `PASS`.

## Segurança preservada

Não existe escrita automática. O protocolo continua U8 0–255, mas todo novo alvo preparado pelo aplicativo é limitado a 100–180. Confirmação humana, ACK e readback permanecem obrigatórios.

## Prova

A execução remota [33313235501](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/33313235501) passou contratos rápidos, regressões Kotlin, JVM completo, lint e APK. Node local passou 90/90; contratos Python passaram 36/36.

## Limitação

A engenharia está concluída. Economia de combustível e dirigibilidade ainda exigem validação física controlada no mesmo veículo; nenhuma melhoria de consumo foi inventada a partir dos testes de software.
