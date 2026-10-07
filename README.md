# OMEGAS Diamante (V8.2)

Aplicativo Android para leitura, aprendizado, diagnóstico e ajuste manual assistido de centrais OMEGAS/MP48.

A branch canônica é `OmegasDiamante`. A continuidade vive no repositório: `AGENTS.md` (contrato do agente), `PROJECT.md`, `STATUS.md` (último APK e último CI), Issues e os planos em `docs/superpowers/`. Notion é referência somente leitura para critérios de UX/produto.

## Contratos duráveis do produto

- nenhuma sugestão ou conexão grava na ECU sozinha; a única exceção é o apagamento automático de pontos fora da curva do GNV e da gasolina (regra 1 de `AGENTS.md`), com readback e registro; Curva K continua só com o dono;
- toda outra escrita é iniciada manualmente e depende de ACK e readback;
- falha de ACK ou readback divergente não é sucesso;
- OBD permanece observacional;
- Mapa K e Curva K permanecem separados;
- a linha técnica do Mapa K não é editável;
- matemática e protocolo críticos permanecem no Kotlin.

## Verificação

A fonte de verdade é o CI no GitHub Actions (`.github/workflows/ci.yml`; resumo de todos os workflows em `docs/ci/WORKFLOWS.md`). Sessões também podem rodar testes locais:

```bash
./gradlew testDebugUnitTest
python -B tools/run_checks.py
```
