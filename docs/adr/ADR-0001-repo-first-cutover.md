# ADR-0001 — Cutover para governança repo-first

- Estado: aceito
- Data: 2026-08-28
- Owner: proprietário do OMEGAS

## Contexto

O código funcional ficou separado da `main`, enquanto regras antigas dividiam autoridade entre GitHub, Notion, Linear e cadeias de auditoria. Isso tornou retomadas ambíguas e produziu branches duplicadas.

## Decisão

O repositório passa a ser a única fonte canônica de engenharia. A `main` deverá conter a árvore completa. Cada mudança material usa uma Issue, uma branch, uma PR e um manifesto de evidências. Notion/Linear deixam de ser dependência de boot.

## Consequências

- retomada determinística por arquivos versionados;
- menos branches e reauditorias;
- CI seletiva, com Actions somente para prova Android/release;
- documentos antigos permanecem históricos, sem autoridade concorrente.
