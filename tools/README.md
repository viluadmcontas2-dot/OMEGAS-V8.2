# OMEGAS Blue — ferramentas permanentes

Este diretório contém apenas ferramentas de verificação/manutenção que fazem parte do repositório.

## Regra de finalização

- `tools/run_checks.py` é a entrada rápida usada pelo CI canônico Blue.
- Ferramentas temporárias de aplicação, migração ou mutação remota devem ser removidas depois do uso.
- O workflow canônico continua sendo `.github/workflows/blue-ci.yml`.
- Scripts históricos não são autoridade de runtime; a autoridade é o código atual do branch e seus testes.

Este arquivo também torna a higiene do diretório explícita para agentes futuros, evitando reexecução acidental de patches já consumidos.
