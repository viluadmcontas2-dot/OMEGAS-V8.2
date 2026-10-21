# Painel da multimídia (Car Info): somente leitura

Fonte opcional e passiva. O app pede à central Android do carro (multimídia, app OEM `com.can.activity`) o painel que ela já recebe da CAN: velocidade, odômetro, rotação, marcha, combustível e consumo. Serve para as sessões terem velocidade e km sem depender de GPS ou OBD. Depois vai alimentar km por tanque e a separação carro andando × parado.

Contrato de origem (engenharia reversa estática): `Taytech-/docs/produto/CONTRATO-ICANBUS.md` (interfaces `ICanUI`, `ICanBus`, `ICanBusListener` e o `DashBoardInfo`).

## O que é lido
- Ligação: `ComponentName("com.can.activity", "com.can.ui.CanPopWind")`, `BIND_AUTO_CREATE` → `ICanUI.getCanService("CanBusManager")` (tx 1) → `ICanBus.registerListener` (tx 1). No encerramento: `unregisterListener` (tx 2) e `unbindService`.
- Eventos: `ICanBusListener.onDashBoardInfoChanged` (tx 6). As 17 transações do ouvinte respondem (como o cliente OEM); só a 6 é lida.
- Foto inicial: `ICanBus.getDashBoardInfo` (tx 18), uma vez por conexão, porque o registro não manda foto.
- Campos (ordem do contrato): `gearShiftPosition`, `odo`, `cruisingRange`, `rotationRate`, `speed`, `runningTime`, `fuelTankage`, `fuel`, `fuelPercentage`, `avgFuelCont`, `instFuelCont`, `coolantTemp`, `engineOilTemp`, `inletTemp`, `ambientTemp`, `accPedal`, `brakePedal`, `throttlePos`.

Onde aparece: `carinfo` no snapshot completo (`full_snapshot` e exports da sessão) com estado (`enabled`, `connected`, `state`, `lastError`, `samples`, `lastSampleAtMs`) e a última amostra; evento `car_info` na sessão, no máximo 1 por segundo, só com os campos conhecidos. Interruptor: `carInfoEnabled` (ligado por padrão, por ser só leitura); ponte `setCarInfoEnabled`.

Na tela: Ferramentas › Mais ferramentas › cartão **Multimídia (Car Info)** › "Ver leituras". Mostra o estado em uma frase (Conectada e lendo / Procurando… tentativa N, próxima em X s / Indisponível: motivo / Desligada), a idade da última leitura e o contador, 9 valores ("—" = desconhecido) e a chave "Ler da multimídia" (1 toque). Leitura pela ponte `getCarInfo` (= `CarInfoSource.json()`), no ciclo normal da tela. Código: `app/src/main/assets/ui/core/car-info-view.js`; teste: `tests/ui/car-info-multimidia.test.cjs`.

## O que NÃO é feito
- Nenhuma escrita: nada de `setConfig`, `set*Property`, `setCarProperty`, `setDiagnose`, nenhum pacote para a CAN. A ECU do GNV não é tocada por aqui.
- Nada de `getCarPropValues` (tx 30): no código OEM ele pode transmitir pacotes de consulta na primeira chamada.

## O que é INCERTO
- **O bind em runtime nunca foi testado no carro.** O código Java do OEM não checa quem chama, mas SELinux, assinatura ou a versão da central podem negar. Falha = estado `indisponivel`, sem travar o app, com nova tentativa espaçada (10 s, 20 s, 40 s... até 10 min).
- **tx 18 (`getDashBoardInfo`)**: sem efeito colateral visto no código inspecionado, mas não confirmado no carro.
- **Quais campos o perfil Peugeot 208 preenche** e **as unidades** (velocidade provável km/h, odômetro provável km, rotação provável rpm; consumo e combustível sem unidade certa). Só uma captura com o carro andando responde.

## Como ler `null` / "—"
A multimídia marca "não sei" com `Integer.MIN_VALUE` (inteiros) e `-2.14748365E9` (decimais). O app transforma isso em `null` no JSON e "—" na tela, **nunca 0**. Um 0 que aparece é 0 de verdade (ex.: pedal de freio solto). `complete: false` = o pacote chegou cortado; os campos que faltaram ficam `null`.

Código: `app/src/main/java/com/omegas/prohub/carinfo/` (decodificador puro, supervisor de estado e cliente do binder). Testes: `DashBoardInfoDecoderTest.kt`, `CarInfoSupervisorTest.kt`, `diagnostics/MinIntervalGateTest.kt`.
