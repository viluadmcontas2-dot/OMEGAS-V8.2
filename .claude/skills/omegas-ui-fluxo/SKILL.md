---
name: omegas-ui-fluxo
description: OBRIGATÓRIA ao mexer em qualquer tela, botão, alinhamento ou CSS do OMEGAS. Fluxo medido (dizer, mudar, medir, olhar, corrigir, só então mostrar) e a regra visual única das barras de botões.
---

# OMEGAS — fluxo de UI medido (obrigatório)

O dono pediu este nível de raciocínio e coerência como regra. Nada de "ficou alinhado" sem medida.

## Fluxo (sempre, nesta ordem)
1. **Dizer** em uma linha o que vai mudar (tela, arquivo, qual regra visual se aplica).
2. **Mudar** o mínimo necessário, reaproveitando os elementos reais (ids existentes; nunca remover botão/função sem avisar).
3. **Medir** no Chromium real (1280×720): `node tools/ui-measure-bar.cjs <rota> <seletor-da-barra> <saida.png>` imprime largura, altura, topo, esquerda/direita e overflow de cada botão visível.
4. **Olhar a captura** (Read do PNG). A medida e o olho têm que concordar.
5. **Corrigir** o que a medida ou a captura mostrarem (o CSS antigo costuma vencer por especificidade: conferir `width`, `flex`, `margin`, `[hidden]`) e voltar ao passo 3. Dizer ao dono o que estava errado.
6. **Só então** mostrar a captura ao dono, com o que foi medido. Mudança de tela inteira (reforma) = mockup e OK antes.

## Regra visual única: barra de botões (`.btn-bar`)
- Cada botão com o **tamanho natural do texto** (como "Sugestões"), altura **58 px**, **8 px** entre eles, alinhados na **mesma linha** e à esquerda (ou à direita, em barra de topo), **nunca esticados** para ocupar a linha.
- Botão com `hidden` não ocupa lugar. Sem rolagem lateral; se faltar espaço, a barra quebra para uma segunda linha.
- Toda barra nova usa a classe `.btn-bar` (definida em `app/src/main/assets/ui/styles-diamante.css`). O teste `tests/ui/btn-bar-rule.test.cjs` mede todas as abas e quebra o CI se uma barra fugir da regra.

## Princípio: rodapé único (dono, 2026-10-07)
Em toda tela, qualquer botão que possa ser realocado vai para **um rodapé único** (uma `.btn-bar`, uma linha, junto do Desfazer quando houver). Cabeçalhos de título e botões soltos no topo saem; o espaço liberado vai para o gráfico/grade. Estado vira uma frase curta sobre o conteúdo, não um cabeçalho. Ao revisar uma tela, perguntar sempre: "que botão aqui pode descer para o rodapé?".

## Disciplina
- Mudou estrutura de tela → rodar `node --test tests/ui/*.test.cjs` e atualizar os testes que afirmavam a estrutura antiga (de propósito, dizendo no commit).
- Salvar no GitHub (commit + push) a cada etapa; branch de trabalho temporária some quando entra na Diamante.
- Relatório: o que mudou, classe de prova (1 contrato, 4 render) e o que NÃO foi provado (classe 5 só com o dono no carro).
