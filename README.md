# OMEGAS V8.2

Aplicativo Android para leitura, aprendizado, diagnóstico e ajuste manual assistido de centrais OMEGAS/MP48.

Esta baseline preserva o código funcional testado usado como ponto de partida da linha V8.2. A governança operacional viva fica no Notion; `AGENTS.md` contém apenas o contrato estável necessário para operar o repositório com segurança.

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
