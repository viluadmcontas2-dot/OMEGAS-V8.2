# Blueprint destilado — princípios para o OMEGAS Platina

Fonte: Blueprint CUSTOMROM e Omega Dev 4.0 (Notion, só leitura). Eram de outros apps; aqui ficam só os princípios. Tela 1280×720, motorista dirigindo, dono leigo.

## Princípios
1. A tela mostra o que a pessoa quer fazer, não como o app funciona por dentro.
2. Cada aba é um trabalho humano, não um módulo do código.
3. Uma tela, uma pergunta, uma próxima ação. Só um botão parece principal.
4. Menos é mais: se tudo tem o mesmo peso, nada é visto. Entender em 2 segundos.
5. Normal é pequeno; problema cresce, diz o que houve e o que fazer.
6. Todo estado é desenhado: sem dados, procurando, executando, ok, falhou, retomando.
7. Todo toque responde na hora (recebido, fazendo, feito, falhou). Nunca inventar % sem progresso real.
8. Erro sempre diz: o que houve, se há risco, o que fazer agora. Nunca app caído.
9. Cor tem significado: acento = ação, verde = ok, amarelo = atenção, vermelho = falha. Nunca enfeite.
10. Dois níveis: frase humana primeiro; bytes, comando e readback em "Detalhes técnicos".
11. Quem desfaz fica ao lado do feito ("Gravado · Desfazer").
12. Na dúvida se algo é crítico, proteja. Observar é livre; mudar é do dono.
13. Cartão só para algo que é um objeto (um refino, uma sessão). Nada de cartão por número.
14. Premium = menos esforço mental para a mesma tarefa, com mais confiança. Não é efeito visual.
15. Mudar de aba não perde o contexto. Preservar o motor bom; mudar a experiência.

## Regras de ouro por tipo de tela
- Status (Agora): uma frase de situação + um botão. Números ficam em segundo plano.
- Dados (Mapa K, Curva K): ver primeiro "está bom ou falta o quê?"; a tabela vem depois. Legenda de cor curta.
- Ação (Refino, AutoCal): resultado esperado em cima, um botão grande, progresso real, readback ao final.
- Histórico (Sessões): lista por data com resultado em uma frase; detalhe técnico ao abrir.
- Ferramentas/Diagnóstico: itens por intenção ("Conferir conexão"), risco visível, técnico escondido.
- Erro em qualquer tela: ocupa o topo, em vermelho/amarelo, com a próxima ação em botão.
- Em movimento: texto crítico grande, poucos itens, nada que exija ler parágrafo.

## Padrões de texto
Estado: o que está acontecendo + o que significa. Botão: verbo de efeito. Erro: o que houve + o que fazer.

Bons
- Refino: "Falta 1 ajuste para chegar perto da gasolina" / botão "Aplicar ajuste" / "Gravado e conferido na central · Desfazer".
- AutoCal: "Aprendendo: dirija normal, falta pouco" / "Aprendizado completo, 3 pontos prontos" / botão "Usar aprendizado".
- Ferramentas: "Conferir conexão" (verde) / "Cabo sem resposta. Troque a porta USB e toque em Tentar de novo" / "Salvar foto da central".

Ruins
- Refino: "K-factor delta aplicado, ACK 0x06 recebido" (técnico, sem consequência para o dono).
- AutoCal: "Estado: LEARN_PARTIAL (42%)" (jargão e % que o motor não mede de verdade).
- Ferramentas: "Erro: timeout na porta serial /dev/ttyUSB0" (culpa o transporte sem dizer o que fazer).

## O que NÃO mostrar ao leigo
Bytes e comandos, nomes de classes e protocolos, códigos ACK/NAK, hex, revisão de snapshot, caminhos de arquivo, SHA, stacktrace, nomes internos de estado, tabelas inteiras sem resumo, porcentagem inventada, dois avisos competindo, confirmação "tem certeza?" (usar Desfazer), o botão de uma ação que não pode ser feita agora (mostrar o motivo).

## Checklist de 10 itens para revisar uma tela
1. Entendo a finalidade em 2 segundos?
2. Há uma única ação principal, claramente destacada?
3. O botão diz o efeito (verbo), não a implementação?
4. Algum campo ou termo técnico aparece sem pedir?
5. Os estados vazio, executando, falhou e concluído foram desenhados?
6. O erro diz o que houve e o que fazer, e a ECU está separada do transporte?
7. Há feedback imediato ao toque, sem progresso falso?
8. A cor segue o significado e o texto crítico tem 24 px ou mais, toque 76 px ou mais?
9. O que foi feito pode ser desfeito e fica claro o que foi gravado e lido de volta?
10. Funciona com texto longo e sem dados, e a aba mantém o contexto ao voltar?
