# AutoCal: espaço do cursor e AutoMatch em tempo real

Continuação de `001a9551352e8debd93e092b12c06d5b56ee31ee`, na OMEGASCINZA.
O CI anterior 37883029613 terminou SUCCESS; isso não certifica esta alteração.

## Evidência e alteração mínima

Chromium, viewport 1280×720, HTML real e ponte simulada: após RESET_PETROL,
18 pontos GNV permaneceram; nenhuma curva antiga de gasolina, linha artificial
ou marcador fantasma apareceu. A captura revelou AGORA sobre o texto de Z4.
O teste contrário posicionou o cursor sobre os textos das quatro zonas e falhou
com quatro colisões antes da correção.

O AutoCal agora escolhe a posição do rótulo entre alternativas dentro do gráfico,
evitando os textos de zonas e os avisos sobrepostos. A posição do ponto não muda.
O cursor compartilhado e o Refino não foram alterados.

Adendo do dono: AutoMatch X/3 sempre visível e atualizado. O indicador existente
na linha da legenda usa X/M, com M recebido da ECU, sem assumir 3 quando o limite
for diferente ou desconhecido. Contador desconhecido permanece —.
O teste Chromium observa 0/3, 1/3, 2/3, 3/3 e — por revisões da ponte,
sem depender da posição do cursor. A guarda visual da teia foi atualizada para
a notação solicitada, mantendo as verificações de aquisição e comandos.

## Verificação focada

`tests/ui/autocal-reread-petrol-visual.test.cjs`: 2 testes PASS, nenhum skip.
Inclui preservação de pontos, ausência de referência velha, colisões nas quatro
zonas, contador visível no viewport e transições AutoMatch.
`tools/ui_stress/autocal-transport-causality.cjs`: três respostas antigas por
transição, convergência em 193/184/219 ms na bancada anterior à última alteração.
`tools/ui_studio/studio-smoke.cjs`: oito rotas, anotação, edição e exportação,
sem erros JavaScript. CI do SHA final continua obrigatório.

## Limites

Classe 4 parcial: Chromium com ponte simulada. Fixture LOGNOVO preservada;
teste de consumidor JS das três épocas originais passou, sem fabricar telemetria.
Não foi executado novo replay completo do monitor Kotlin nesta bancada.
Nenhum comando, escrita automática nova, lógica nativa ou Curva K foi modificado.
USB/ECU e latência física permanecem sem validação classe 5.

AppDeploy não está disponível nas ferramentas desta sessão. Nenhuma URL pública
nova foi criada, nem o projeto de referência foi sobrescrito. Themely recebeu
o briefing de organização solicitado, preservando o tema aprovado.
