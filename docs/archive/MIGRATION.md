# Migração limpa para OMEGAS V7

Origem técnica: `OMEGAS-V6:v7/end-to-end-runtime`.

A migração preserva o núcleo Kotlin e os testes comportamentais. Foram removidos da superfície ativa:

- shell visual consolidado;
- assets legados da pasta `hub`;
- navegação duplicada de Sugestões/Calibração;
- editor de mapa em página longa;
- chaves de assinatura históricas armazenadas no repositório;
- contratos de UI específicos de versões anteriores.

A interface ativa passa a existir somente em `app/src/main/assets/ui`.
