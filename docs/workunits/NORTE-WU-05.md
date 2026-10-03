# NORTE-WU-05 — Um só caminho para ler e gravar, com Desfazer

## Controle

- Estado: `AGUARDANDO`
- Épico: #131
- Issue (checklist autoritativa): #137
- Spec: `docs/superpowers/specs/2026-10-03-omegas-platina-norte-unico-design.md`
- Plano: `docs/superpowers/plans/2026-10-03-f5-autoridade-unica.md`
- Branch: `work/platina-f5-autoridade-unica`
- PR: —
- Depende de: NORTE-WU-04 (#136)
- Commits levam no corpo: `NORTE-WU-05 · Tarefa 5.<M>`
- GitHub remoto é autoridade.

## Resultado observável

Toda leitura e gravação passa por uma fila única com foto antes, etapas visíveis e Desfazer.

## Prova exigida

Fila serializa; `✗` sem crash; Desfazer byte a byte; ponte sem órfão; **APK**

## Não provado

Preenchido na Issue #137 ao fechar.
