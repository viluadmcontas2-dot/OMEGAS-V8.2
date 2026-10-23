# Painel da multimídia (Car Info): somente leitura

Fonte opcional e passiva. O app pede à central Android do carro (multimídia, app OEM `com.can.activity`) o painel que ela já recebe da CAN: velocidade, odômetro, rotação, marcha, combustível e consumo. Serve para as sessões terem velocidade e km sem depender de GPS ou OBD. Depois vai alimentar km por tanque e a separação carro andando × parado.

Contrato de origem (engenharia reversa estática): `Taytech-/docs/produto/CONTRATO-ICANBUS.md` (interfaces `ICanUI`, `ICanBus`, `ICanBusListener` e o `DashBoardInfo`).

## O que é lido
- Ligação: `ComponentName("com.can.activity", "com.can.ui.CanPopWind")`, `BIND_AUTO_CREATE` → `ICanUI.getCanService("CanBusManager")` (tx 1) → `ICanBus.registerListener` (tx 1). No encerramento: `unregisterListener` (tx 2) e `unbindService`.
- Eventos: `ICanBusListener.onDashBoardInfoChanged` (tx 6) é o painel. As 17 transações do ouvinte respondem (como o cliente OEM); TODAS são contadas e guardadas cruas (ver "Captura crua"); a tx 17 (`onCarPropValuesChanged`) também vira valores, só para campo que o painel não trouxe.
- Foto inicial: `ICanBus.getDashBoardInfo` (tx 18), uma vez por conexão, porque o registro não manda foto.
- Campos (ordem do contrato): `gearShiftPosition`, `odo`, `cruisingRange`, `rotationRate`, `speed`, `runningTime`, `fuelTankage`, `fuel`, `fuelPercentage`, `avgFuelCont`, `instFuelCont`, `coolantTemp`, `engineOilTemp`, `inletTemp`, `ambientTemp`, `accPedal`, `brakePedal`, `throttlePos`.

Onde aparece: `carinfo` no snapshot completo (`full_snapshot` e exports da sessão) com estado (`enabled`, `connected`, `state`, `lastError`, `samples`, `lastSampleAtMs`) e a última amostra; evento `car_info` na sessão, no máximo 1 por segundo, só com os campos conhecidos. Interruptor: `carInfoEnabled` (ligado por padrão, por ser só leitura); ponte `setCarInfoEnabled`.

Na tela: Ferramentas › Mais ferramentas › cartão **Multimídia (Car Info)** › "Ver leituras". Mostra o estado em uma frase (Conectada e lendo / Procurando… tentativa N, próxima em X s / Indisponível: motivo / Desligada), a idade da última leitura e o contador, 9 valores ("—" = desconhecido) e a chave "Ler da multimídia" (1 toque). Leitura pela ponte `getCarInfo` (= `CarInfoSource.json()`), no ciclo normal da tela. Código: `app/src/main/assets/ui/core/car-info-view.js`; teste: `tests/ui/car-info-multimidia.test.cjs`.

## PROVADO em campo (Peugeot 208, APK e99ac6e, 2026-10-21)
- O bind funciona: cartão "Conectada e lendo", 40 leituras. A ordem do Parcel do DashBoardInfo está certa.
- Pelo DashBoardInfo (tx 6) CHEGARAM e BATEM com o painel do carro: **Autonomia 35 km** e **Consumo médio 11,7**.
- Vieram nulos (sentinela): velocidade, odômetro, rotação, marcha, combustível (%), temperatura da água. Conclusão: a central NÃO preenche esses campos no DashBoardInfo deste carro.
- "Consumo agora" mostrou -1,0: valor impossível exibido como número. Corrigido: campo fisicamente impossível (negativo onde não existe, combustível > 100 %, etc.) vira desconhecido ("—"); zero real continua 0.

## Captura crua (uma volta de carro responde tudo)
Para não precisar de outro ciclo de teste, o app grava o que a central mandou:
- `carinfo.channels` no snapshot: `channelsActive` de `channelsTotal` (17), por transação `tx{n:{n,bytes}}`, campos crus do painel ANTES da sentinela (`dashboard`), `dashboardHex`, `initialHex` (tx 18), `props` (tx 17: `propId/areaId=valor`), `cabin` (tx 2), `propertyList` (tx 14: propIds) e `hex` curto (48 bytes) das demais.
- Evento `car_info_raw` na sessão: mesmo conteúdo, no máximo 1 a cada 5 s, só quando o conteúdo muda (contador não conta), teto de 720 por ligação. O `car_info` continua no máximo 1 por segundo.
- Decodificação tolerante (`CarInfoExtraDecoders`, `ParcelBytes`): payload cortado ou lixo não estoura nem trava o binder; parcial = `complete:false`. Ordem = a de ESCRITA do contrato (o reader do cliente OEM é diferente, §5.1 do contrato).
- Fusão (`CarInfoAggregator`): DashBoardInfo manda. Um valor da tx 17 só entra se o painel está nulo para o campo; o canal (`tx6`/`tx17`) aparece em Detalhes e em `carinfo.sample.sources`.

## Cartão (Ferramentas › Multimídia)
Linha "Canais ativos: N de 17"; **Detalhes** (fechado) mostra o canal de cada valor e as chamadas por canal; dica "A central não mandou velocidade. Os dados brutos foram gravados na sessão." quando, após 60 s de leitura, rotação > 1000 (a da multimídia, ou a da ECU se a multimídia não mandou) e velocidade e odômetro seguem "—".

## O que NÃO é feito
- Nenhuma escrita: nada de `setConfig`, `set*Property`, `setCarProperty`, `setDiagnose`, nenhum pacote para a CAN. A ECU do GNV não é tocada por aqui.
- Nada de `getCarPropValues` (tx 30): no código OEM ele pode transmitir pacotes de consulta na primeira chamada.

## O que é INCERTO
- Bind: falha = estado `indisponivel`, sem travar o app, com nova tentativa espaçada (10 s, 20 s, 40 s... até 10 min).
- **tx 18 (`getDashBoardInfo`)**: chamada sem erro no campo; sem efeito colateral visto no código.
- **Velocidade, odômetro, rotação, marcha, combustível (%), temperatura da água**: não vêm no DashBoardInfo deste carro. Podem vir pela tx 17, mas o contrato só mostra a tx 17 no ramo `maintenance` (manutenção) e NÃO traz a tabela de propId do Peugeot; a CabinInfo (tx 2) é portas/vidros/bancos/cintos, sem velocidade. O mapa de propId da tx 17 usa ids no estilo VHAL do Android Automotive (velocidade 0x11600207 em m/s, odômetro 0x11600204 em km, rotação 0x11600305, temperatura da água 0x11600301): **INCERTO**, escolhido porque é o padrão do Android Automotive; se a captura mostrar outros ids, ajustar `CarInfoAggregator.PROP_MAP`.
- **Unidades** do que vem no painel: autonomia e consumo médio batem com o painel do carro, mas a unidade do consumo (L/100 km?) segue sem confirmação.
- **Lacuna documentada**: a tela de Trip do Car Info OEM usa `CarinfoGeneralBean` por Messenger (fora do ICanBus; contrato §4.3 mostra a rota Messenger, mas não o serviço nem as mensagens de leitura com precisão). Não implementado.

## Como ler `null` / "—"
A multimídia marca "não sei" com `Integer.MIN_VALUE` (inteiros) e `-2.14748365E9` (decimais). O app transforma isso em `null` no JSON e "—" na tela, **nunca 0**. Um 0 que aparece é 0 de verdade (ex.: pedal de freio solto). `complete: false` = o pacote chegou cortado; os campos que faltaram ficam `null`.

Código: `app/src/main/java/com/omegas/prohub/carinfo/` (decodificador puro, supervisor de estado e cliente do binder). Testes: `DashBoardInfoDecoderTest.kt`, `CarInfoSupervisorTest.kt`, `diagnostics/MinIntervalGateTest.kt`.
