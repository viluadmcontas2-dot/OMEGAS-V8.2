# Incidente — AutoCal: largura dos contadores, limiar de maturidade e ponto prematuro

Data: 2026-08-08  
Status técnico: correção aplicada na branch `feature/ux-didactic-expansion`; regressões atualizadas; execução Kotlin/JVM e validação física ainda pendentes.

## Sintoma e impacto

A evidência AutoCal do OMEGAS podia interpretar de forma incorreta quanto uma região realmente havia aprendido e podia desenhar um ponto antes de a região atingir maturidade.

Efeito prático: uma região ainda em coleta podia parecer visualmente pronta, e os contadores `0x015B/0x015C` eram decodificados com largura incompatível com a resposta real da ECU.

## Causa imediata

1. `NUM_BUF_UPD_PETR (0x015B)` e `NUM_BUF_UPD_GAS (0x015C)` estavam declarados como `U8`, embora a ECU retorne 36 bytes = 18 palavras `U16 little-endian`.
2. GNV e GNV anterior usavam `VECT_AUTOCAL_U8_2 (0x0165:2)` como limiar, embora esse campo seja `MaxAutomatch`.
3. `draw` era verdadeiro também em `COLETANDO` e em parte de `LIMIAR_NAO_LIDO`.

## Causa estrutural

A primeira implementação modelou os campos por nomes/valores observados antes de fechar completamente a gramática oficial por largura e semântica. Como vários limiares da configuração capturada tinham o mesmo valor numérico, o erro de fonte de limiar podia ficar mascarado.

## Por que os testes anteriores não detectaram

`AutoCalProtocolTest` construía 18 bytes e esperava 18 elementos U8, consolidando a largura errada.

`AutoCalAcquisitionTest` possuía um teste cujo nome dizia que ponto abaixo do limiar “não é desenhado”, porém a asserção exigia `draw == true`.

O teste de GNV usava o mesmo campo incorreto como entrada e oráculo, sem valores divergentes que distinguissem `MaxAutomatch` do limiar verdadeiro.

## Evidência

Artefatos originais re-hasheados nesta correção:

- `PortmonAUTOCAL.LOG`: SHA-256 `4a70f5ae79b1d688c05bd169f3e6a588b52105580d24b8a72a5cff398a384c0b`;
- `ProgBase.exe`: SHA-256 `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`.

No Portmon, request `29 5B 01 85` recebe ACK `53` e LEN `0x24` = 36 bytes. A gramática correlacionada do binário oficial classifica os contadores como 18 palavras.

Limiar confirmado por combustível/faixa:

- gasolina, regiões 0..5: `0x0165:1`;
- gasolina, regiões 6..17: `CALIBRATION_VAL_1[2]`;
- GNV/GNV anterior, regiões 0..5: `CALIBRATION_VAL_1[5]`;
- GNV/GNV anterior, regiões 6..17: `CALIBRATION_VAL_1[8]`;
- `0x0165:2`: `MaxAutomatch`, observacional e separado do limiar GNV.

## Correção

- `AutoCalProtocol.kt`: `0x015B/0x015C` agora usam `Encoding.U16_LE`.
- `AutoCalAcquisition.kt`: limiar é escolhido por combustível e faixa; `MaxAutomatch` é separado; `draw` só é verdadeiro para `VALIDO`.
- O JSON de diagnóstico preserva `petrolUpdate/gasUpdate` e adiciona campos explícitos `petrolLow`, `petrolNormal`, `gasLow`, `gasNormal` e `maxAutomatch`.

Nenhum writer, Mapa K, Curva K, OBD, persistência ou regra de escrita automática foi alterado.

## Teste de regressão

`AutoCalProtocolTest` agora:

- usa 36 bytes para os dois contadores;
- exige 18 palavras `U16 little-endian`;
- rejeita payload U16 com largura ímpar.

`AutoCalAcquisitionTest` agora:

- exige `draw=false` abaixo do limiar;
- usa valores deliberadamente distintos (`MaxAutomatch=1`, gasolina baixa=2, gasolina normal=7, GNV baixa=4, GNV normal=9);
- prova que cada combustível/faixa usa a fonte correta sem coincidência numérica.

## Validação pendente e risco residual

Este ambiente não possui checkout completo do OMEGAS V7. Portanto `testDebugUnitTest`, lint e build ainda não foram executados para este HEAD. GitHub Actions também pode estar indisponível por cota.

O próximo ambiente com checkout autenticado deve executar o Local Quality Gate de `docs/LOCAL_BUILD_AND_DRIVE_PROTOCOL.md`. Depois, o comportamento AutoCal continua **AGUARDANDO VEÍCULO** para correlação visual com ECU real.