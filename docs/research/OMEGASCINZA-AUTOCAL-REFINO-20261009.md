# OMEGASCINZA | AutoCal e Refino independente | 2026-10-09

## Invariantes de produto

- O contador AutoMatch `N/MAX` descreve a execução da ECU; as quatro zonas de gasolina e GNV descrevem **a aquisição atual**, individualmente. Após 1/3, 2/3 ou 3/3 continuam visíveis, inclusive quando faltam dados. Leitura ausente = desconhecida, não adquirida.
- A barra de zonas vive fora do SVG no `autocal-cockpit.js`. Reset, curva vazia, falta de limiar MAP e alteração do domínio não a removem. Os estados confirmados têm uma única origem (`EcuAcquisitionTruth`, projeção nativa); telemetria move apenas o cursor AGORA.
- Refino constrói seus próprios pares gasolina/GNV por RPM/MAP e regime. Não usa aquisição nativa como evidência, não usa curva nativa como prior, não descarta pares porque a ECU zerou os buffers do AutoCal.
- Regiões visíveis são criadas dinamicamente a cada 0,25 ms observado, separando LENTA e CONDUCAO. Não há teto de 12, 13 ou 22 ms nem máximo de 17/36 regiões; não há exigência de cobrir vizinhos para medir e julgar um ponto. Acima do último nó real da Curva K, continua sendo possível **medir**, mas não extrapolar uma proposta de escrita.
- O eixo do gráfico parte de zero, mas zero efetivo é corte de injeção, não amostra estável comparável. A aquisição estável aceita a partir de 1 ms. Região abaixo desse limite não gera equivalência fictícia.
- AutoCal completado e ativo (`autoMatchCount >= maxAutomatch`, `autoCalEnabled == 1`) é a única condição **nativa** de autorização para propostas da Curva K. A escrita K é manual e exige curva atual, limites de passo/K, filtro de transientes, avaliação da regressão, foto e readback.
- Refino próprio usa `AutoMatchRefinedEngine.Input.independentRefino=true` com células locais de 0,5 ms e uma região validada; motor legado conserva `false` (paridade e testes antigos preservados).

## Estados e provas

| Situação | Comportamento esperado |
|---|---|
| AutoMatch 1/3, zonas GNV Z1,Z2 faltando | Contador 1/3; Z1/Z2 faltando, Z3/Z4 conforme leitura; gasolina separada |
| AutoMatch 2/3 e 3/3, nova aquisição parcial | Nenhuma zona some por causa do contador |
| Curva ainda sem domínio válido | Barra Z1–Z4 continua visível, estados desconhecidos sem fabricar dados |
| Apenas 1 região madura a 13,5 ms | Refino registra/julga região sem aguardar preenchimento das 17 bandas nativas |
| Leituras a 1,5 ms e 22,5 ms | Aparecem na curva própria; nunca inventar correção fora dos nós K reais |
| Marcha lenta e condução no mesmo tempo | Medições segregadas por regime, sem produzir média enganosa |
| AutoCal ainda 1/3 ou pausado | Medições próprias seguem; nenhuma sugestão é gravável |
| AutoCal 3/3 habilitado e ponto próprio julgado | Proposta manual possível se filtros matemáticos e regressão aprovarem |
| Reset de buffers nativos | Não apaga observações próprias; reset verdadeiro da Curva K invalida os pares GNV antigos |

## Testes adicionados/atualizados

- `RefinoIndependenteRegressoesTest.kt`: faixas 1,5 / 13,5 / 22,5 ms, única região, gate AutoCal e modo independente do motor.
- `EcuAcquisitionTruthTest.kt`: 1/3, 2/3, 3/3 preservam a enumeração de zonas faltantes.
- `tests/ui/autocal-refino-independence.test.cjs`: barra de zonas separada do SVG e apresentação de 100 regiões locais.
- `RefinoWhyNoProposalTest.kt`, `RefinementLifecycleRegressionTest.kt`, `tests/ui/autocal-teia.test.cjs`, `tests/ui/autocal-reread-petrol-visual.test.cjs` e scripts de estresse do consumidor real.

## Evidência faltante e condições de fechamento

- O GitHub Actions `ci.yml` no SHA final deve passar testes JVM, Python, Node, lint e verificação da teia. Alteração publicada não equivale a CI aprovado.
- Chromium visual real 1280×720 deve confirmar largura do cabeçalho com 4 zonas, todos os botões, sem overflow, AutoMatch 0/3–3/3 e readquisição.
- Validação física classe 5 continua pendente: carro, ECU MP48, telemetria real, antes/depois da gravação manual, sem piorar lenta, cruzeiro ou trancos.
- Parear lenta e condução é observação; ajuste global de Curva K não deve ser oferecido se melhora um regime e piora outro. Proposta local no Mapa K só com causalidade demonstrada.
