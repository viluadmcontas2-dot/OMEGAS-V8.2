# OMEGAS-RED-WU-001 — Aprendizado contínuo rápido

**Issue:** #9  
**Branch:** `hotfix/v8.0-red-performance`  
**Estado:** `IMPLEMENTING`

## Resultado esperado

Transformar evidência esparsa de gasolina/GNV em superfície contínua explicável que ensina imediatamente, transfere sinal global e local de forma controlada e reduz novas rodadas na gasolina.

## Escopo obrigatório

1. procedência exata do par usado na equivalência;
2. separação entre agregado da célula, observação direta e previsão;
3. tendência global por `Tinj_ref` para Curva K;
4. residual contínuo RPM×MAP;
5. projeção downstream para células físicas;
6. suporte, independência e incerteza separados de contagem bruta;
7. sugestões manuais globais e locais;
8. Mapa K 100–180;
9. ausência de bloqueio por RPM;
10. Auto-Cal acessível no fluxo global;
11. custo bounded fora do hot path.

## Fora de escopo

- escrita automática;
- afirmar mapa ideal sem incerteza;
- misturar funcionalidades da V8.2;
- Notion/Linear como estado operacional;
- alegar economia sem teste físico.

## Fechamento

Cada requisito aponta para código, teste RED/GREEN e SHA remoto final. Limitações físicas permanecem explícitas.
