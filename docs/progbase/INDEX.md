# ProgBase — índice de evidências

Reconstrução **parcial**. Inventário, pseudocódigo, replay e execução visual são provas diferentes. Nenhum percentual abaixo significa equivalência funcional completa.

## Entradas por pergunta

- [Semântica](semantica.md): nomes de telas, opções e ações que não podem ser confundidos.
- [Inventário](inventario/PROGRESSO.md): telas, controles, menus e handlers.
- [Abas 1–16](abas/PROGRESSO-A.md) e [abas 17–33](abas/PROGRESSO-B.md): estado e próximo passo.
- [Protocolo](protocolo/PROGRESSO-D2.md): quadros, conversões, conexão e testes pendentes.
- [Cobertura estática](decomp/COBERTURA.md): 29.927 funções reconhecidas, 25.202 com pseudocódigo; não é cobertura de comportamento.
- [Verificação REA](decomp/rea-verificacao.md): provas e bloqueios da análise nativa.
- [Original sob Wine](decomp/execucao-original.md): capturas existentes, ainda sem prova de central conectada nesse relatório.
- [Grafo](grafo/GRAPH_REPORT.md): relações entre documentos, funções, controles e evidências.

## Evidências versionadas (EV-ÁREA-nnn)

Índice mestre: [evidencias/INDEX.json](evidencias/INDEX.json) (id, afirmação, marca, fonte, versão 2.16.4, commit, quem usa). Auditor: `python3 -B tools/progbase/auditar.py --check` (id duplicado, marca inválida, fonte inexistente, id órfão, contradição); teste: `python3 -B tests/test_progbase_evidencias.py`. Os ids REA e MAN seguem valendo como alias.

## Registros REA existentes

Fonte: `docs/progbase/decomp/evidence-bundle.json`. IDs abaixo são reais; a operação e sua autoridade delimitam o que provam.

| ID | Operação | Autoridade | Limite |
|---|---|---|---|
| `ev_8439fb125abf59a9b3eba3342494ac4a6c6334ec42e8c5873950a651f4bd918b` | `record_unknown` | analyst-inference | Artifact identity is unavailable for this observation. Registry mutation evidence records analyst intent, not proof of the answer. |
| `ev_f2fd067dbbc24e27666eb781d12249b1753bdf9b0e43efc5b472aca7d6b2a161` | `inspect_pe_resources` | shipped-artifact | Static PE32/PE32+ type/name/language resource inventory; payloads other than RT_GROUP_ICON remain opaque. Icon candidates are resource-table relationships. Language fallback, image decoding and actual shell/window loading are not observed. Exact shared byte ranges are retained; non-identical overlapping resource structures or payloads are rejected. |

## Análises manuais locais

Os IDs MAN desta seção são registros locais, separados do bundle REA. Fonte: `docs/progbase/evidencias-locais.json`.

| ID | Documento | Limite |
|---|---|---|
| `MAN-ABAS-B-TabAdatta` | [abas/TabAdatta.md](abas/TabAdatta.md) | Inventário DFM/RTTI; fórmulas e efeitos runtime não comprovados pelo inventário. |
| `MAN-ABAS-B-TabAdvanced` | [abas/TabAdvanced.md](abas/TabAdvanced.md) | Inventário DFM/RTTI; fórmulas e efeitos runtime não comprovados pelo inventário. |
| `MAN-ABAS-B-TabAutoCalNew` | [abas/TabAutoCalNew.md](abas/TabAutoCalNew.md) | Inventário DFM/RTTI; fórmulas e efeitos runtime não comprovados pelo inventário. |
| `MAN-ABAS-B-TabBenzina` | [abas/TabBenzina.md](abas/TabBenzina.md) | Inventário DFM/RTTI; fórmulas e efeitos runtime não comprovados pelo inventário. |
| `MAN-ABAS-B-TabCalibration` | [abas/TabCalibration.md](abas/TabCalibration.md) | Inventário DFM/RTTI; fórmulas e efeitos runtime não comprovados pelo inventário. |
| `MAN-ABAS-B-TabConsumometro` | [abas/TabConsumometro.md](abas/TabConsumometro.md) | Inventário DFM/RTTI; fórmulas e efeitos runtime não comprovados pelo inventário. |
| `MAN-ABAS-B-TabDhlpAgaslev` | [abas/TabDhlpAgaslev.md](abas/TabDhlpAgaslev.md) | Inventário DFM/RTTI; fórmulas e efeitos runtime não comprovados pelo inventário. |
| `MAN-ABAS-B-TabEsci` | [abas/TabEsci.md](abas/TabEsci.md) | Inventário DFM/RTTI; fórmulas e efeitos runtime não comprovados pelo inventário. |
| `MAN-ABAS-B-TabFrest` | [abas/TabFrest.md](abas/TabFrest.md) | Inventário DFM/RTTI; fórmulas e efeitos runtime não comprovados pelo inventário. |
| `MAN-ABAS-B-TabIniezione` | [abas/TabIniezione.md](abas/TabIniezione.md) | Inventário DFM/RTTI; fórmulas e efeitos runtime não comprovados pelo inventário. |
| `MAN-ABAS-B-TabInjSettings` | [abas/TabInjSettings.md](abas/TabInjSettings.md) | Inventário DFM/RTTI; fórmulas e efeitos runtime não comprovados pelo inventário. |
| `MAN-ABAS-B-TabInjSettingsSt` | [abas/TabInjSettingsSt.md](abas/TabInjSettingsSt.md) | Inventário DFM/RTTI; fórmulas e efeitos runtime não comprovados pelo inventário. |
| `MAN-ABAS-B-TabKVarie` | [abas/TabKVarie.md](abas/TabKVarie.md) | Inventário DFM/RTTI; fórmulas e efeitos runtime não comprovados pelo inventário. |
| `MAN-ABAS-B-TabSwitchToPetrol` | [abas/TabSwitchToPetrol.md](abas/TabSwitchToPetrol.md) | Inventário DFM/RTTI; fórmulas e efeitos runtime não comprovados pelo inventário. |
| `MAN-ABAS-B-TabTaratura` | [abas/TabTaratura.md](abas/TabTaratura.md) | Inventário DFM/RTTI; fórmulas e efeitos runtime não comprovados pelo inventário. |
| `MAN-ABAS-B-TabTest` | [abas/TabTest.md](abas/TabTest.md) | Inventário DFM/RTTI; fórmulas e efeitos runtime não comprovados pelo inventário. |
| `MAN-ABAS-B-TabVariatore` | [abas/TabVariatore.md](abas/TabVariatore.md) | Inventário DFM/RTTI; fórmulas e efeitos runtime não comprovados pelo inventário. |
| `MAN-ABAS-B-ONSHOW-LIMITES` | [abas/limites-onshow-b.json](abas/limites-onshow-b.json) | Inventário DFM/RTTI; fórmulas e efeitos runtime não comprovados pelo inventário. |
| `MAN-PROTOCOLO-CONNECT-00923FDC` | [protocolo/conexao.md](protocolo/conexao.md) | Leitura de pseudocódigo retido; sem execução da conexão nesta evidência. |

## Manual histórico: páginas rastreáveis

O manual 2.16.4 é de 2012 e não prova equivalência com a versão do executável estudado. Texto em `docs/progbase/manual/manual-2.16.4C.txt`; hashes por página em `docs/progbase/manual/paginas.json`. Associação a controles específicos só deve ser criada com evidência, não pela semelhança do nome.

| ID | Página no texto |
|---|---|
| `MAN-2.16.4-p001` | 1 |
| `MAN-2.16.4-p002` | 2 |
| `MAN-2.16.4-p003` | 3 |
| `MAN-2.16.4-p004` | 4 |
| `MAN-2.16.4-p005` | 5 |
| `MAN-2.16.4-p006` | 6 |
| `MAN-2.16.4-p007` | 7 |
| `MAN-2.16.4-p008` | 8 |
| `MAN-2.16.4-p009` | 9 |
| `MAN-2.16.4-p010` | 10 |
| `MAN-2.16.4-p011` | 11 |
| `MAN-2.16.4-p012` | 12 |
| `MAN-2.16.4-p013` | 13 |
| `MAN-2.16.4-p014` | 14 |
| `MAN-2.16.4-p015` | 15 |
| `MAN-2.16.4-p016` | 16 |
| `MAN-2.16.4-p017` | 17 |
| `MAN-2.16.4-p018` | 18 |
| `MAN-2.16.4-p019` | 19 |
| `MAN-2.16.4-p020` | 20 |
| `MAN-2.16.4-p021` | 21 |
| `MAN-2.16.4-p022` | 22 |
| `MAN-2.16.4-p023` | 23 |
| `MAN-2.16.4-p024` | 24 |
| `MAN-2.16.4-p025` | 25 |
| `MAN-2.16.4-p026` | 26 |
| `MAN-2.16.4-p027` | 27 |
| `MAN-2.16.4-p028` | 28 |
| `MAN-2.16.4-p029` | 29 |
| `MAN-2.16.4-p030` | 30 |
| `MAN-2.16.4-p031` | 31 |
| `MAN-2.16.4-p032` | 32 |
| `MAN-2.16.4-p033` | 33 |
| `MAN-2.16.4-p034` | 34 |
| `MAN-2.16.4-p035` | 35 |
| `MAN-2.16.4-p036` | 36 |
| `MAN-2.16.4-p037` | 37 |
| `MAN-2.16.4-p038` | 38 |
| `MAN-2.16.4-p039` | 39 |
| `MAN-2.16.4-p040` | 40 |
| `MAN-2.16.4-p041` | 41 |
| `MAN-2.16.4-p042` | 42 |
| `MAN-2.16.4-p043` | 43 |
| `MAN-2.16.4-p044` | 44 |
| `MAN-2.16.4-p045` | 45 |
| `MAN-2.16.4-p046` | 46 |
| `MAN-2.16.4-p047` | 47 |
| `MAN-2.16.4-p048` | 48 |
| `MAN-2.16.4-p049` | 49 |
| `MAN-2.16.4-p050` | 50 |
| `MAN-2.16.4-p051` | 51 |
| `MAN-2.16.4-p052` | 52 |
| `MAN-2.16.4-p053` | 53 |
| `MAN-2.16.4-p054` | 54 |
| `MAN-2.16.4-p055` | 55 |

## Próximo passo

Completar os fluxos das abas e a conexão sob Wine; obter dossiers nativos e comparação REA; relacionar páginas a controles mediante evidência. A auditoria verifica existência das referências, não a verdade de uma reconstrução.
