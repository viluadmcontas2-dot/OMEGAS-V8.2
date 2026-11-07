# Gate verificável de ajuda seletiva (2026-11-07)

## Base verificada nesta execução
- Canônica `28afdaa74df1cb81e84d9f4ee3261d92050ad976`; Aura `9d63a4a331a9a92062cfaf2a354c298594a7a16c`; canvas `c00717c75f100581b7f221c4438d9453fefdb9d8`; alternativa `96c5a4b26469158eedd62ad4eded69e52888a0f4`.
- PR #185 aberto, mergeable no momento da consulta, **não aprovado para merge**.
- CI #1669, run `42349572660`, **success** no SHA Aura `9d63a4a` (2026-11-07 07:08 UTC). CI não é prova física.

## Matriz requisitos → fonte → prova exigida
| Requisito | Fonte | Critério de aceitação |
|---|---|---|
| Cinco áreas intactas | REGRAS-DO-DONO §8; alternativa `core/ajuda-textos.js` | Nenhuma rota `regular`/`carro` independente; `autocal`, `refino`, `map`, `curve`, `tools` preservadas |
| Ajuda sem promessas indevidas | alternativa `ajuda-textos.js`, `tools.acontece` | Não dizer que cada cartão pede confirmação ou possui Desfazer sem teste específico |
| Sem gravação automática | alternativa `primeiroUso[2]` | Distinguir aprendizado da central de gravação manual do app; não prometer que nada é gravado ao dirigir |
| Leitura de volta verdadeira | alternativa `refino.acontece`, `map.acontece` | Para cada ação manual, demonstrar ACK, readback e falha; não generalizar para caminhos não verificados |
| Tensão 14,1 V | alternativa `curve.acontece`; AGENTS.md | Identificar como relato de campo e escala provisória, não limite universal |
| Conexão real | alternativa `primeiroUso[1]` | USB presente ≠ handshake ≠ telemetria válida; somente exibir conectada após evidência |
| Layout | `tests/ui/nova-ajuda.test.cjs` da alternativa | Adaptar para cinco rotas; testar foco, Esc, alvo de toque, ausência de corte em 1280×720, 1024×600, 800×480 |
| Dados existentes | `applicationId=com.omegas.v7.test` | Não alterar package ID, persistência ou permissões USB |
| Segurança ECU | AGENTS.md; plano de fechamento | Nenhuma mudança de protocolo, writers, USB ou execução de gravação física |

## Sequência TDD proposta
1. Criar teste RED de conteúdo/rotas para ajuda canônica sem copiar as expectativas de quatro abas.
2. Adaptar apenas textos e componente de ajuda com auditoria de DOM; não copiar `router.js`, `regular.js` ou `carro.js`.
3. GREEN focal, revisão independente de diffs, CI completo e capturas renderizadas reais nas três resoluções **no mesmo SHA**.
4. Somente então considerar a ajuda pronta. Este documento é auditoria de critérios, **não implementação nem teste executado**.

## Bloqueios que permanecem
- Aprovação de conjunto de telas e mapa de navegação; validação visual renderizada, teste físico e pacote APK canônico ainda não demonstrados.
- A implementação da alternativa não foi importada nesta execução; nenhuma alteração de ECU/USB, merge ou APK.
