# Incidente — autoridades múltiplas de mutação da ECU

## Sintoma e impacto
A proteção completa de condição segura para escrita existia na ponte de compatibilidade V7, mas outras superfícies públicas (`OmegasNative` e ações AutoCal nativas) podiam chegar a operações mutáveis sem consultar exatamente a mesma decisão de serviço/USB/engine/telemetria/RPM.

O efeito prático era uma arquitetura com mais de uma autoridade de segurança. Uma nova interface podia respeitar ACK/readback no writer e ainda assim iniciar a operação numa condição que a ponte V7 teria bloqueado.

## Causa imediata
As regras `serviço ativo + USB conectado + permissão resolvida + engine pronta/não travada + telemetria fresca + RPM abaixo de 1200` estavam implementadas localmente em `V7JavascriptBridge`, em vez de existir como política nativa única reutilizada por todas as superfícies mutáveis.

## Causa estrutural
Bridges cresceram em momentos diferentes e cada uma passou a validar sua própria fronteira. Os writers de Mapa K e Curva K continuaram fortes em backup, ACK e readback, mas o gate de **quando é permitido começar** não tinha uma única fonte.

Também permaneceu uma API legada `applySuggestion()` que podia alcançar o caminho antigo de aplicação direta, em desacordo com a regra atual `sugestão → preparar → revisar → confirmar → escrever`.

## Por que os testes não detectaram
`tests/test_v7_map_batch_contract.py` verificava apenas a presença do gate dentro de `V7JavascriptBridge`. Ele não exigia que `HubJavascriptBridge` e AutoCal usassem a mesma autoridade. Assim o contrato podia ficar verde mesmo com caminhos paralelos.

## Correção
- criada `CalibrationWriteSafetyPolicy`, pura e testável;
- `V7JavascriptBridge`, `HubJavascriptBridge` e `AutoCalNativeActionManager` passam a consultar a mesma política;
- a Curva K revalida a condição no executor antes de chamar o writer;
- o Mapa K continua revalidando antes de cada bloco interno de até 16 células;
- ações AutoCal revalidam na preparação, na confirmação e imediatamente antes das etapas mutáveis;
- `applySuggestion()` foi selado como compatibilidade **prepare/review only**, sem chamada de writer;
- a autonomia humana do Mapa K permanece 1–144 células; somente o writer interno continua em blocos de até 16.

## Teste de regressão
- `CalibrationWriteSafetyPolicyTest`: fronteiras de serviço, USB, permissão, engine, idade da telemetria e RPM;
- `tests/test_v7_map_batch_contract.py`: exige a mesma política nas três superfícies e proíbe `v7ApplySuggestion` na bridge pública;
- writers existentes continuam protegidos por seus testes de backup, ACK, readback e falha parcial.

## Evidência
Branch de implementação: `fix/v8-consistency-safety`.
Base: `c9792fc24b597deed985574ad89b083f85d989d5`.

## Risco residual
A prova atual é estática/automatizada. Android/WebView, USB real, ações AutoCal, Map K 144 células e rechecagem entre blocos continuam **AGUARDANDO CELULAR/VEÍCULO** antes de qualquer declaração de segurança física.