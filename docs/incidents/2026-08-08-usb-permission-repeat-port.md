# Incidente — pedidos repetitivos de permissão USB

Data: 2026-08-08
Estado: port aplicado em `feature/ux-didactic-expansion`; validação física pendente

## Sintoma e impacto
O aplicativo podia tratar qualquer adaptador serial reconhecido pela biblioteca USB como candidato OMEGAS. Isso permitia que USBs alheios chegassem ao fluxo de `requestPermission()` e também tornava reconexões mais invasivas do que o necessário.

## Causa imediata
`UsbSerialManager` filtrava apenas por existência de driver serial. O `device_filter.xml` aceitava vendors inteiros (CP210x, CH340/CH341, FTDI e Prolific), sem restringir produto.

## Causa estrutural
O produto confundia "há driver serial" com "é a interface OMEGAS autorizada" e tratava falha transitória de transporte como desconexão lógica completa.

## Correção portada
- identidade OMEGAS fixada em VID `0x10C4` / PID `0xEA60`;
- `device_filter.xml` aceita somente esse par;
- enumeração e seleção usam a mesma allowlist;
- `requestPermission()` só é alcançado depois de `isOmegasDevice`;
- USBs seriais alheios são ignorados;
- falha transitória preserva a sessão lógica enquanto tenta reabrir a porta;
- retries limitados: 250 ms, 750 ms e 1500 ms;
- detach físico e desconexão manual continuam sendo hard disconnect;
- transações durante recuperação retornam falha explícita.

## Regressão
- `app/src/test/java/com/omegas/prohub/usb/UsbPoliciesTest.kt`;
- `tests/test_usb_permission_identity_contract.py`;
- contrato incluído em `tools/run_checks.py`.

## Invariantes preservados
Nenhuma escrita automática; protocolo MP48, matemática, Mapa K, Curva K, OBD, writers, ACK e readback não foram alterados.

## Risco residual
Outro equipamento que use exatamente o mesmo CP2102 `10C4:EA60` ainda não pode ser distinguido antes da permissão; a confirmação por handshake MP48 só é possível depois do acesso. A persistência da permissão USB depende também do Android/OEM e exige teste físico.

## Validação necessária
Instalar APK desta branch e testar: primeira autorização, fechar/reabrir app, segundo plano, reconexão transitória, detach/reatach físico e conexão de USB serial não OMEGAS.
