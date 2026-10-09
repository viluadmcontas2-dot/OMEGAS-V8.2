# AutoCal: gráfico incorreto após "Reler gasolina" (OMEGASCINZA, 2026-10-09)

## Sintoma comunicado pelo dono

Depois de tocar em **Reler gasolina**, o gráfico mostrava uma curva ligada com zigue-zagues e círculos vazios sobrepostos, ao mesmo tempo que as zonas diziam *gasolina: falta; GNV: ok*. O cursor AGORA avançava independentemente da aquisição.

**Fonte:** captura enviada pelo dono. Trata-se de apresentação com ECU simulada; a captura sozinha não demonstra corrupção dos dados da ECU física.

## Causa localizada no código

`renderReferenceChart` entrava na renderização de época apenas se houvesse `intentPending`, nenhum ponto RV ou `referenceUsable === false`. Uma projeção preparada e ainda em cache podia continuar dizendo `referenceUsable === true` mesmo depois de `liveAcquisitionEpoch.petrolPending === true`. Resultado: desenhava a curva RV da gasolina anterior e calculava os 18 marcadores previstos a partir dela durante uma releitura.

Ao cair no caminho de época, `renderAcquisitionEpochChart` conectava os pontos brutos de GNV ordenando **por tempo de gasolina**, apesar de não haver curva de gasolina válida para equivalência nessa etapa. Essa linha era uma inferência gráfica, não uma leitura que a ECU entregou.

## Correção mínima

1. **Autoridade da época acima do cache:** `epochPending` cobre `petrolPending`, `gasPending`, as referências individuais, `referencePending` e `comparisonAllowed === false`. Força o caminho de aquisição mesmo que a projeção atrasada diga `referenceUsable=true`.
2. **Gasolina nova ainda não válida:** não desenhar curvas antigas ou pontos RV de gasolina; exibir estado pendente.
3. **GNV permanece intacto:** cada ponto atual GNV com contador positivo segue visível e selecionável. Durante o reset da gasolina, **não ligar** esses pontos para fingir uma curva de equivalência válida.
4. **Sem marcadores fantasmas:** `missingBands` depende da referência gasolina atual. Não prever pontos de nenhum combustível se `petrolPending` ou `petrolReferencePending` for verdadeiro.
5. Nenhuma escrita, lógica de aquisição física, contador, timer, curva K ou layout aprovado foi alterado.

## Provas

`tests/ui/autocal-reread-petrol-visual.test.cjs` usa o HTML real em Chromium **1280x720** e os estados da ponte simulada. Injeta oscilação apenas para o teste de desenho, **não** como se fosse um dado LOGNOVO. Reproduz o comando de `RESET_PETROL` no simulador, deixando os 18 pontos GNV aprendidos.

- **Antes:** teste falha por 18 marcadores de gasolina previstos da referência anterior.
- **Depois:** 18 pontos de GNV, 0 pontos de gasolina, 0 linhas falsas GNV/gasolina, 0 curvas RV de gasolina antigas, 0 marcadores inventados, 1 SVG íntegro. Browser sem pageerror.
- `tests/ui/autocal-live-epoch-gate.test.cjs` e `tests/ui/autocal-zone-sync.test.cjs` continuam passando.
- Testes visuais e contratos no GitHub Actions devem ficar VERDES no SHA publicado. O CI `OMEGASCINZA` passa a executar automaticamente esta prova Chrome e anexar a captura como artefato, sem depender da máquina local.

## Limites

**Classe 4 parcial (Chromium com ponte simulada).** A tela reproduz a falha de apresentação e prova seu reparo, mas não mediu latência serial no carro, nem comprovou que os próprios valores físicos de MAP e ms têm ausência de ruído. O LOGNOVO original e os comandos de ECU não foram modificados, e nenhum registro privado foi publicado. Teste físico é classe 5.

## Integração de revisão visual remota

A oficina local já existe na `OMEGASCINZA` (HTML, CSS e JS originais), mas para hospedá-la e editar online via AppDeploy precisa de autorização do AppDeploy na conta do ChatGPT. Não reivindicar um link AppDeploy novo ou alteração do site anterior sem deployment confirmado.
