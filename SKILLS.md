# Habilidades obrigatórias

## Camada de decisão

- `code-verification`: investigação, auditoria, diagnóstico e testes sem alteração.
- `code-work`: qualquer edição autorizada.
- `guarded-skill-resolver`: seleciona a menor cadeia suficiente de skills REOPS.
- `evidence-verification`: núcleo obrigatório para conclusões técnicas.
- GitHub: fonte obrigatória para repositório, branches, commits, PRs, CI, artifacts, APKs e releases.

## Regra de carregamento

1. Leia `.reops-lock.yaml`.
2. Carregue o `@Codex Engineering Guardrails` no modo aplicável.
3. Abra o REOPS no commit exato do lock.
4. Leia primeiro apenas `registry.yaml` e os metadados.
5. Carregue integralmente somente as skills escolhidas pelo `guarded-skill-resolver`.
6. Registre skills carregadas e adiadas.

É proibido carregar todas as skills por precaução. Skills listadas em `available_skills` estão disponíveis para seleção, não automaticamente ativas.

## `code-verification`
Usar para investigação, auditoria, diagnóstico, inspeção preventiva e testes sem alteração.

Antes de qualquer bloco:
1. confirmar repositório, branch, commit, PR, CI e artifacts;
2. ler `AGENTS.md`, `.reops-lock.yaml`, `START_HERE.md`, este arquivo, `TESTING_RULES.md`, `docs/TEST_STRATEGY.md`, `PROJECT_STATE.md`, `DECISIONS.md` e `CAPABILITY_MATRIX.md`;
3. cruzar produtores, consumidores, workflows, testes e riscos;
4. montar matriz requisito → comportamento → evidência → resultado;
5. classificar lacunas como confirmadas, prováveis, históricas ou não verificadas.

Depois da implementação, usar novamente para inspeção independente do diff final, testes, contratos, CI, artifacts e riscos residuais.

## `code-work`
Usar para qualquer edição autorizada.

Cada bloco autorizado inclui:
- implementação na branch remota;
- testes focados, de consequências e regressão;
- documentação e incidente quando houver aprendizado reutilizável;
- atualização de `PROJECT_STATE.md` quando o estado observável mudar;
- correção de falha objetiva encontrada na validação do próprio bloco;
- commits necessários na branch de trabalho.

Não inclui automaticamente `main`, merge, PR, tag, release, APK de distribuição, deploy, Netlify ou publicação.

## Precedência

As regras locais do OMEGAS V8 definem o comportamento permitido do produto. O REOPS fornece método técnico. O Codex Engineering Guardrails controla modo, autoridade, risco, testes e evidência.

Nenhuma skill pode autorizar escrita automática na ECU, ignorar confirmação manual, enfraquecer ACK/readback, publicar artefatos, alterar `main`, fazer deploy ou executar validação física sem autorização explícita.

Se uma habilidade obrigatória, o commit fixado ou o registry não estiver disponível, pare como `INCONCLUSIVE`; não substitua por memória ou pela `main` mais recente do REOPS.

## Regra de preservação do remoto
- Antes de substituir um arquivo inteiro, ler o conteúdo remoto completo e comparar com a base usada na edição.
- Leitura parcial, trecho truncado, memória, ZIP antigo ou apenas o SHA não autorizam sobrescrita integral.
- Se o blob remoto mudou desde a preparação, reabrir o arquivo completo, identificar as mudanças novas e integrá-las conscientemente.
- Nunca apagar proteção, teste, correção ou trabalho paralelo apenas para aplicar a própria versão.
- Depois da escrita, comparar o intervalo completo da branch e procurar arquivos ou comportamentos inesperados.

## Método preventivo antes do remoto
Antes de concluir um bloco:
1. validar sintaxe e contratos dos arquivos alterados;
2. cruzar IDs da interface e consumidores JavaScript/Kotlin;
3. confirmar ausência de escrita automática e timers concorrentes;
4. executar o gate rápido;
5. revisar o diff por regressões e arquivos fora de escopo;
6. confirmar que o workflow aplicável consegue executar os novos testes;
7. somente então considerar o commit final candidato ao CI.

## Regras aprendidas com o gate Android
- Testes de ordem devem comparar decisões semanticamente por chave e tolerância numérica; nunca usar o texto integral do JSON como prova de equivalência.
- O teste deve validar o contrato público realmente emitido.
- O Gradle Wrapper deve ser verificado antes da execução por checksum conhecido, URL de distribuição esperada e `validateDistributionUrl=true`.
- Nunca desligar silenciosamente validação de wrapper apenas para obter CI verde.
- Dois workflows que constroem Android devem usar requisitos compatíveis ou registrar claramente por que divergem.

## Método anti-loop
- no máximo duas passagens de inspeção antes da primeira alteração;
- editar → testar → corrigir falha objetiva → verificar remoto → encerrar;
- não repetir leitura sem evidência nova;
- em bloqueio real, parar e declarar o impedimento exato.
