# OMEGAS V8.2

Aplicativo Android para leitura, aprendizado, diagnóstico e ajuste manual assistido de centrais OMEGAS/MP48.

Esta linha usa governança **repo-first**: GitHub remoto, Issues, `PROJECT.md`, `STATUS.md`, Spec Kit e WorkUnit ativa formam a superfície canônica de continuidade. Notion é referência read-only quando explicitamente necessário para critérios de UX/produto.

## Contratos duráveis do produto

- nenhuma sugestão ou conexão grava automaticamente na ECU;
- toda escrita é iniciada manualmente e depende de revisão/confirmação, ACK e readback;
- falha de ACK ou readback divergente não é sucesso;
- OBD permanece observacional;
- Mapa K e Curva K permanecem separados;
- a linha técnica do Mapa K não é editável;
- matemática e protocolo críticos permanecem no Kotlin.

## Verificação local

Use testes proporcionais ao escopo. O gate rápido disponível nesta baseline é:

```bash
python -B tools/run_checks.py
```

GitHub Actions não fazem parte deste bootstrap inicial.


## Programa ativo — OMEGAS Verde

O programa atual está indexado em `docs/spec-kits/OMEGAS-SK-001.md` e rastreado pela Issue #81. O foco é paridade observável do AutoCal com o ProgBase original, replay derivado de logs reais e gate visual/runtime. SIL/CIU permanece fora do escopo até autorização explícita.
