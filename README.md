# OMEGAS V8.2

Aplicativo Android para leitura, aprendizado, diagnóstico e ajuste manual assistido de centrais de GNV OMEGAS/MP48, feito para rodar na multimídia do carro (paisagem, 1280x720). Para quem tem carro a gás e quer que o motor se comporte no GNV como na gasolina, com um app simples por fora e rigoroso por dentro.

## O que o app faz
- Lê a central pela USB e mostra, em linguagem simples, o estado do carro e a única próxima ação.
- Oito abas: Agora, Mapa K, Curva K, AutoCal, Ajuste GNV, Sessões, Ferramentas e Diagnóstico.
- Registra sessões, detecta quedas e apagões do motor e sugere ajustes.

## Garantias de segurança
- O app nunca grava na central por conta própria: toda escrita começa num toque do dono.
- Toda gravação termina na leitura de volta da central; "Gravado" só aparece depois dela.
- Há Desfazer; falha de transporte não é confundida com erro da central.
- OBD é só observação.

## Estado
Não validado no carro até o dono confirmar; o que está provado e o que falta fica em `docs/handoff/PENDENTE.md`. Último APK e versão: `STATUS.md`.

## Como testar e contribuir
```bash
python3 -B tools/run_checks.py      # contratos e verificações rápidas
node --test tests/ui/*.test.cjs     # testes de interface
```
A suíte completa roda no GitHub Actions (`.github/workflows/ci.yml`); o APK sai só pelo `apk.yml`, por disparo manual. Leia `AGENTS.md` e `docs/README.md` antes de mudar algo; teste primeiro (RED/GREEN) e não altere os comandos de leitura/escrita da central sem pedido do dono.

## Documentação
Índice em `docs/README.md`. Engenharia reversa do software original em `docs/progbase/` (somente referência; não se versionam .exe, DLL nem arquivos de licença).

## Licença
O repositório ainda não declara licença; sem ela, todos os direitos são reservados ao autor.
