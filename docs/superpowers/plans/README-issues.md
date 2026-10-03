# Work Units do Norte Único

Épico: #131 · Regra de nomes e binding: R10 do índice (`2026-10-03-00-norte-unico-index.md`).

| Work Unit | Issue | Título | Plano | Branch | Binding |
|---|---|---|---|---|---|
| NORTE-WU-00 | #132 | Base do projeto: regras novas, docs limpos e teste no GitHub | `2026-10-03-00-norte-unico-index.md` | `work/platina-f0-norte` | `docs/workunits/NORTE-WU-00.md` |
| NORTE-WU-01 | #133 | Separar o que fica antes de apagar | `2026-10-03-f1-extracoes.md` | `work/platina-f1-extracoes` | `docs/workunits/NORTE-WU-01.md` |
| NORTE-WU-02 | #134 | Tirar o Predictor, o AutoMatch manual e o cérebro V7 | `2026-10-03-f2-poda-1.md` | `work/platina-f2-poda-1` | `docs/workunits/NORTE-WU-02.md` |
| NORTE-WU-03 | #135 | Tirar o aprendizado antigo e limpar o aparelho | `2026-10-03-f3-poda-2.md` | `work/platina-f3-poda-2` | `docs/workunits/NORTE-WU-03.md` |
| NORTE-WU-04 | #136 | Cérebro único: Referência congelada e Curva Própria | `2026-10-03-f4-cerebro-unico.md` | `work/platina-f4-cerebro-unico` | `docs/workunits/NORTE-WU-04.md` |
| NORTE-WU-05 | #137 | Um só caminho para ler e gravar, com Desfazer | `2026-10-03-f5-autoridade-unica.md` | `work/platina-f5-autoridade-unica` | `docs/workunits/NORTE-WU-05.md` |
| NORTE-WU-06 | #138 | Telas Agora, Curva K e Refino | `2026-10-03-f6-ui-agora-curva-refino.md` | `work/platina-f6-ui-agora-curva-refino` | `docs/workunits/NORTE-WU-06.md` |
| NORTE-WU-07 | #139 | Telas AutoCal, Mapa K, Sessões e Ferramentas | `2026-10-03-f7-ui-autocal-mapa-sessoes-ferramentas.md` | `work/platina-f7-ui-autocal-mapa-sessoes-ferramentas` | `docs/workunits/NORTE-WU-07.md` |
| NORTE-WU-08 | #140 | Acabamento premium e APK final | `2026-10-03-f8-acabamento.md` | `work/platina-f8-acabamento` | `docs/workunits/NORTE-WU-08.md` |

## O portão (`.github/workflows/issue-gate.yml`, check `Issue gate`)

Todo PR para `OmegasPlatina` falha se qualquer item for falso:
- corpo do PR tem `Fecha #<n>`; `#<n>` está aberta e tem label `wu`;
- branch `work/platina-f<N>-…` e título da Issue começando com `NORTE-WU-0<N> · `;
- todas as caixas da seção `## Tarefas` da Issue marcadas;
- seção `## Não provado` da Issue preenchida;
- todo commit (exceto merges) com `NORTE-WU-0<N> · Tarefa <N>.<M>` no corpo;
- `docs/workunits/NORTE-WU-0<N>.md` existe na head do PR e cita `#<n>` e a branch.

Marcar uma caixa na Issue reavalia o PR automaticamente só quando o workflow estiver também na branch padrão (`main`); até lá, editar o PR ou empurrar um commit reavalia.
