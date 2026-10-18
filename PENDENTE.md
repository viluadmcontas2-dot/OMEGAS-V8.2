# PENDENTE — escrita da Marcha lenta (não implementada)
Provado no emulador (tools/emulacao/cpu_setter_original.py --modo-lenta, RadioGasClick 0x4BFAD8 executado; ECU do dono 80D=0):
- Gás:      35 03 00 86 24 51 10 43 ; 14 A1 00 00 00 00 B5
- Volta:    35 03 00 86 24 51 10 43 ; 14 A1 00 00 4C 04 05
- Gasolina: 35 03 00 86 24 51 11 44 ; 14 A1 00 00 4C 04 05   (bit = 0x0100 da palavra 1, NÃO 0x0400)
(w0/w1 do FLAG_CONF1 e GIRI[1] são preservados; GIRI vai por indexado 0x14 só no elemento 0.)
Falta: IdleModeCore/Writer (Kotlin, ordem: rpm 0->1100 primeiro, 1100->0 bit primeiro; 2ª falha = PARTIAL + Desfazer), flow JS, ponte, testes JUnit/ECU emulada.
O handler original NÃO confere a 1ª gravação antes da 2ª (provado).
