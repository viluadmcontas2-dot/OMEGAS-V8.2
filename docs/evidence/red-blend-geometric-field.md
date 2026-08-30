# RED Blend — campo geométrico RPM × MAP

## O que foi aplicado

Foi implementado um estimador científico offline que ajusta, em torno de cada
alvo, o plano local:

`Tinj = centro + inclinação_RPM × ΔRPM + inclinação_MAP × ΔMAP`

Ele usa somente gasolina cronologicamente anterior ao alvo. `window_count`
preserva toda a massa de evidência local; a quantidade de sessões independentes
é informada separadamente para medir transferência. O resultado contém centro,
duas inclinações físicas, massa, sessões, distância e maior ordem temporal usada.

Isto transforma a intuição “0,40 bar em determinada rotação pede certo tempo”
em uma conta falsificável. O transiente não é descartado: todo frame válido pode
fortalecer a distribuição local. Sessão e epoch não apagam essa densidade.

## Teste cego contra RED

O candidato foi congelado antes do holdout e comparado em 103 alvos posteriores.
Não houve leakage.

Em 85 alvos com suporte comum:

| Métrica | RED | Campo geométrico |
|---|---:|---:|
| Mediana | 1,3744% | 1,3727% |
| P90 | 4,3125% | 5,4610% |
| P95 | 5,4062% | 6,7407% |
| Máximo | 7,9856% | 9,0533% |

O campo melhorou a mediana por uma margem pequena, mas piorou a cauda. A decisão
é `DEFER`: ele não altera o Predictor Android. O RED permanece a âncora e o campo
geométrico fica disponível para diagnóstico, explicação e próximos experimentos.

## Didática aplicada no Android

A tela Aprender agora explica cada região na ordem humana:

1. Onde: RPM × MAP;
2. Gasolina esperada;
3. GNV observado;
4. Diferença;
5. Por que confiar;
6. O que isso significa.

Foi corrigido o texto que tratava temperatura como parte obrigatória da
coordenada. RPM × MAP define a condição; temperatura é contexto quando existe
dos dois lados. A tela distingue o par efetivamente calculado do resumo agregado
e mostra massa local, visitas e sessões sem confundi-las.

## Segurança

Nenhuma fórmula nova foi promovida ao hot path, nenhum valor de sugestão foi
alterado e não existe auto-write. Abrir o editor continua sem escrever; revisão
humana, confirmação, ACK e readback permanecem obrigatórios.

`P_IMPROVE_PROVEN=false` e `VEHICLE_PROVEN=false`.

