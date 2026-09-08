# Projeto OMEGAS V8.2 Blue

## Objetivo humano
Regular o GNV com o mínimo de esforço humano, usando MP48 como verdade de combustível/calibração e evidência física gasolina↔GNV, sem escrita automática na ECU.

O produto é voltado à multimídia do carro: rápido, legível, didático, resistente a WebViews lentas e com aquisição/aprendizado mantidos pelo serviço Android mesmo sem redraw da tela.

## Contrato científico atual
- `BlueCausalEngine` é a única autoridade de comparação/correção.
- Gasolina é a referência física; `(RPM, MAP)` define condição comparável.
- A célula de Mapa K para GNV usa **RPM atual × Petrol Inj. atual no GNV**.
- MP48 é autoridade de combustível, calibração, writer, ACK e readback.
- OBD é testemunha read-only por STFT no GNV; LTFT não participa da matemática decisória.
- `TRANSITION` ainda é gasolina; `CUT-OFF` é distinto e não é evidência de equivalência.
- Visitas são auditoria/suporte, não confiança por contagem.
- Toda mutação segue preparar → revisar → confirmar → ACK → readback.

## Recuperação sistêmica
A recuperação aberta após a validação física de 2026-09-06 foi implementada e verificada em software. A epic é `#18`, com convergência de `#16` e workstreams `#17/#19/#20/#21/#22/#23`.

O código de recuperação está completo; a declaração externa `READY FOR APK GENERATION` exige uma CI canônica `OMEGAS Blue CI` concluída com sucesso no **HEAD remoto exato** que contém a reconciliação final.

## Gate de artefato
Push normal executa apenas `FAST → JVM/unit → lint` e nunca gera APK. O job de APK é manual e só roda com autorização explícita do owner. Validação física no veículo ocorre depois de um APK autorizado e nunca é inferida da CI.
