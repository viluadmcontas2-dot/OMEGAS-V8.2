# Contrato de fluxo (Omega Aura)

Origem: dono, 2026-10-27 — "o botão de gravar aparece, some, às vezes aparece o botão de desfazer… e se eu não quiser desfazer e só quiser gravar a próxima alteração?". Não adianta interface bonita com fluxo travado: um fluxo ruim obriga a refazer o APK inteiro. Vale para TODA tela, do mockup ao app. Não é rígido: se um caso real pedir outra coisa, registre a exceção aqui com o motivo.

## As nove regras
1. **Botão principal fixo.** Cada tela tem UM botão principal, no fundo do painel esquerdo, sempre no mesmo lugar e com o mesmo texto-base ("Gravar correção", "Gravar N células"). Ele não some e não troca de função. Sem ação possível, fica cinza e diz o motivo dentro dele ("Escolha um trecho e ajuste").
2. **Desfazer nunca toma o lugar de Gravar.** Depois de gravar, o painel mostra um bloco **"Última gravação"** (o que mudou, em 1 linha) com o botão **Desfazer**, acima do botão principal. Gravar a próxima mudança NÃO exige desfazer nem confirmar a anterior: a nova gravação passa a ser a "última"; a anterior fica no histórico.
3. **A próxima ação vem pronta.** Depois de gravar, o botão principal já aponta a próxima mudança (se houver) ou fica cinza com "Nada mais para gravar agora" e o motivo.
4. **Estado muda o texto, não a estrutura.** Nenhum controle aparece ou some por estado. O que não se aplica fica desabilitado com o motivo. O layout não pula.
5. **Sem beco sem saída.** Todo erro ou bloqueio traz o próximo passo (Tentar de novo · Deixar assim · Ver detalhes). Todo menu, caixa ou modo fecha com o mesmo gesto (tocar fora ou "Voltar").
6. **Interrupção não perde trabalho.** Se o cabo soltar, o app fechar ou a ignição desligar no meio de uma gravação em etapas, o app lembra o que faltou e oferece concluir quando reconectar ("Mapa K gravado. Curva K ainda não → Concluir").
7. **Um ritual só para gravar:** ver o que vai mudar (antes → depois) → um toque → selo "Gravado e conferido" no topo + "Última gravação" com Desfazer. Sem janela de confirmação.
8. **Histórico sempre alcançável.** "Últimas gravações" (Mais › Histórico) permite desfazer além da última e mostra quem mudou (você, Refino, AutoCal).
9. **Sem relógio para decidir** (regra do projeto): nada depende de minutos; vale a evidência e a contagem de leituras.

## Como cada tela cumpre
| Tela | Botão principal (fixo) | Secundárias (sempre presentes) | Estado "sem ação" |
|---|---|---|---|
| AutoCal | Interruptor "Aprendizado automático" (topo do painel) | Matriz Zona × Combustível (toque apaga) · "Limpar/Apagar N pontos" sob o gráfico · Reaprender GNV · Reaprender gasolina | "Fora da curva: nenhum ponto" (cinza) |
| Refino | "Gravar correção" | Bloco "Última gravação" + Desfazer · Antes/Depois · "Refinar por minha conta" quando a central ainda aprende | Cinza: "Nada mais para gravar agora" / "Espera a central" |
| Ajuste local | "Gravar N células" | "Última gravação" + Desfazer · Selecionar tudo · Limpar | Cinza: "Escolha um trecho e ajuste" |
| Ajuste global | "Gravar N pontos" | "Última gravação" + Desfazer · Nivelar · Suavizar · Mais | Cinza: "Escolha um trecho e ajuste" |
| Curva → Mapa K | "Passar para o Mapa K" | "Última gravação" + Desfazer · Voltar à curva | Cinza: "Curva já passada" |
| Ferramentas (cartões) | Um botão por cartão ("Usar 1.800 giros") | "Desfazer" do cartão sempre visível, cinza quando não há o que desfazer | Cinza com o motivo |
| Conectar | "Conectar" / "Tentar de novo" | "Ver como funciona" · "Ver demonstração" | O app tenta sozinho; o botão só ajuda |

## Teste de aceitação (vale no mockup e no app)
- Percorrer cada fluxo e conferir que **nenhum botão da lista acima aparece, some ou troca de lugar** entre "antes de gravar", "gravando", "gravado" e "desfeito".
- Gravar duas vezes seguidas sem tocar em Desfazer: a segunda funciona, e "Última gravação" passa a descrever a segunda.
- Desfazer e depois gravar de novo funciona.
- Cabo solto no meio de uma gravação em etapas: ao reconectar, o app oferece concluir.
- Teste automático: `tests/ui/contrato-de-fluxo.test.cjs` (a criar, RED primeiro) varre as telas e falha se um botão principal sumir ou mudar de posição entre estados.
