# Incidente — autorização redundante e trabalho deixado local

Data: 2026-08-04

## Sintoma e impacto
O proprietário autorizou aplicar integralmente o corte de UI/UX. Mesmo assim, o trabalho foi preparado localmente e uma nova autorização foi solicitada para levar o mesmo bloco à branch remota. Isso interrompeu o fluxo, criou dúvida sobre o estado real e fez uma mudança parecer concluída sem estar no GitHub.

## Causa imediata
A separação correta entre alteração, commit e publicação foi aplicada de forma mecânica. O agente tratou o commit remoto da própria branch de trabalho como uma nova fronteira, embora a ordem explícita já autorizasse aplicar o bloco completo e o projeto adotasse trabalho remoto primeiro.

## Causa estrutural
Faltavam duas regras explícitas na governança:

1. uma autorização de bloco cobre suas edições, testes, documentação e commits na branch de trabalho;
2. inspeção deve ter limite para não virar ciclo sem entrega.

## Por que as verificações não detectaram
Os testes verificavam comportamento da interface, mas não verificavam a conclusão operacional no remoto. A governança dizia que ações externas exigiam autorização separada, sem distinguir commit na branch aprovada de merge, main, release e publicação.

## Correção
- registrada a seção `Autorizações sem repetição` em `AGENTS.md`;
- registrado o método anti-loop `inspecionar no máximo duas vezes → editar → testar → corrigir falha objetiva → verificar remoto → encerrar`;
- o corte de UI foi aplicado diretamente em `rebuild/ux-9in-dual-layout`;
- `main`, merge, PR, release, APK e publicação continuam separados.

## Teste de regressão
A governança deve ser relida antes do próximo bloco. Quando o proprietário disser `aplique`, `siga` ou `continue`, o bloco deve chegar a um commit remoto identificável sem nova pergunta, salvo mudança real de produto, risco, escopo ou fronteira externa protegida.

## Evidência
Commits da branch `rebuild/ux-9in-dual-layout` em 2026-08-04:

- governança de autorização sem repetição;
- shell automotivo responsivo;
- leitura automática do Mapa K;
- testes de contrato e runtime.

## Risco residual
A interface ainda precisa de CI integral, APK e validação física em multimídia, celular, USB e ECU. A correção de processo reduz repetição de autorização, mas não remove as autorizações específicas para `main`, merge, PR, release, APK, deploy ou publicação.
