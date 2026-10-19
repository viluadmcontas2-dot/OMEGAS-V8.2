# Progresso — workflows e contratos CI (2026-10-19)

Escopo: ci.yml, apk.yml e testes dos workflows; sem alteração da interface e sem gerar APK.

- OBS-CI: run 39206124461, SHA f4383e4be68cc26e7295bcd31a760e240ed3110b, job 117909237456 falhou em tests/ui/autocal-ci-no-apk.test.cjs: contrato ainda exigia execução monolítica de tools/run_checks.py.
- Corrigido contrato para descoberta completa em jobs separados; mantida proibição de APK na CI e autorização manual no workflow de APK.
- CI: integridade via tools/auditoria.py --check, publicação de AUDITORIA.json mesmo em falha, logs efetivamente gravados e nomes de logs atualizados.
- APK: instala navegador e executa run_checks.py --full, incluindo contratos que dependem dos resultados JVM; evita repetir testes Kotlin e limpar seus resultados.
- Verificação local limitada: node --test tests/ui/autocal-ci-no-apk.test.cjs; RED por ausência da auditoria, GREEN 1/1 após correção. Prova estática dos workflows, não execução do APK.

PRÓXIMO PASSO: integrar auditoria.py da frente principal, publicar as alterações e verificar a nova execução da CI no SHA integrado. Execução real do APK continua dependente de aprovação explícita; nenhum APK foi gerado nesta ação.
