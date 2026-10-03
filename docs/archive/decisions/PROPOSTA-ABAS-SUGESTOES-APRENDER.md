# Proposta: manter, fundir ou remover as abas Sugestões e Aprender

Status: **proposta, nada foi removido.** A decisão é do proprietário.

## O que cada aba faz hoje

**Sugestões** (menu 07) é a caixa única de decisões pendentes.
- Lista a curva refinada pronta e o trecho a restaurar do Refino, com botão que abre o Refino.
- Lista os ajustes prontos de Mapa K (local) e de Curva K (global), com seleção e revisão.
- É a única tela com "observando" e com o histórico de ajustes aplicados após readback.
- O número do menu conta tudo isso. Abrir nunca escreve na ECU.
- Telas Ajuste local e Ajuste global recebem o contexto `origin: suggestions` daqui.

**Aprender** (menu 02) mostra a evidência por condição física.
- Grade RPM × MAP com quatro camadas: Referência, No GNV, Diferença e Sugestão.
- Alimenta as sugestões de Mapa K e de Curva K pelo aprendizado em Kotlin.
- Não grava nada e não decide nada. É uma tela de leitura.

## Onde há sobreposição

- Aprender e Refino medem a mesma coisa por caminhos diferentes: pares GNV × gasolina por RPM × MAP.
  O aprendizado usa a memória por célula. O Refino usa o livro de pontos de equivalência.
  Hoje as duas telas podem mostrar números diferentes para a mesma condição.
- Sugestões e Refino se tocam só no aviso: o Refino aparece como um cartão em Sugestões.
  Não há duplicação de ação, só de entrada.

## Opções

| Opção | O que muda | Risco |
|---|---|---|
| A. Manter as duas | Nada. Sugestões vira "Decisões" só no nome, se o proprietário quiser. | Duas fontes sobre equivalência continuam. |
| B. Fundir Sugestões no Agora | A caixa de decisões vira um painel no Agora. Menu perde um item. | Mapa K e Curva K perdem o ponto de entrada com contexto. Mexe na UI aprovada. |
| C. Remover Aprender | Sai a grade de evidência. Refino fica como única leitura de equivalência. | Perde a visão por célula que o Ajuste local usa para explicar sugestões. |

## Recomendação

**Opção A agora, com decisão por dados daqui a duas semanas.**

1. Sugestões fica. Ela já junta Refino, local e global, e é o destino do número do menu.
2. Aprender fica, mas é a candidata a ser fundida: o caminho natural é o Refino passar a
   ser a única fonte de equivalência e Aprender virar uma camada dele.
3. Antes de decidir, medir uso real. Proponho um contador local de aberturas por aba
   (só no aparelho, sem rede, visível em Ferramentas). Se Aprender não for aberta em duas
   semanas de uso, a fusão é segura. Se for aberta, a fusão precisa manter a grade.

## O que esta proposta não faz

- Não remove aba, botão, rota nem rolagem da UI aprovada.
- Não muda nenhuma regra de escrita: nada grava na ECU sem revisão, confirmação, ACK e readback.
- Não toca em LEVELS, que continua RAW.

## Decisão pedida ao proprietário

1. Manter as duas por enquanto (A)? Sim ou não.
2. Posso criar o contador local de aberturas por aba para decidir com dados? Sim ou não.
