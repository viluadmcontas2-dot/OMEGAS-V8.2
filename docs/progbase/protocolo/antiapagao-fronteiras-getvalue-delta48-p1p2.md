# P1/P2 — arredondamento, Enter e Delta RPM do ProgBase (2026-11-17)

Trabalho de pesquisa sem escrever ECU e sem modificar codigo de producao ou writers. Execucao Python nesta sessao: **replica observacional**, nao reexecucao do arquivo ProgBase.exe no Unicorn; o executavel nao apareceu entre os arquivos do repositorio nem na busca feita no Drive. O projeto ja possui execucao original anterior documentada em tools/emulacao/antiapagao_original.py.

## Referencias e provas

1. OBS-COD: EditAntiStalloKeyPress 0x4C97D8 usa GetValue 0x96B924, DataIndex 0..4, e 0x979E48 para um SetNumber indexado. Sete quadros Python foram comparados aos quadros gerados anteriormente ao executar o original no Unicorn. Todos iguais: 1600, 1601, 1800, delta ativacao 150, espera 1000, histerese 5 e delta desativacao 30.
2. DFM: Precision=1.0 e MaxValue 6000/1000/10000/1000/1000. NAO existe incremento de botao de 50 ou 5. O original manda o valor com Enter inclusive se igual ao anterior; OMEGAS elimina esse write redundante.
3. HIPOTESE: a nota da engenharia reversa descreve somar +/-0,5 antes de truncar para inteiro. A replica Python de empates sugere 0,5→1, 2,5→3, -0,5→-1, -2,5→-3. **Esses casos fracionarios nao foram executados no binario original agora**; ainda falta confirmar locale e tratamento exato de GetValue.
4. OBS-COD: 0x4A33FC extrai s16 little-endian bruto de TStreamDati+0x293C+0x1E e IntToStr exibe sem escala. A associacao da instancia desse buffer ao subtipo 48 xx continua sem prova.
5. OBS-FIO: foram analisadas tres capturas do ProgBase no Drive. 5 + 15 + 1169 = **1189** respostas de 48 01 com eco, ACK 53, payload de 34 bytes e soma valida. Os bytes do payload no offset 30-31 sao 00 00 em todas. Em duas delas, RPM bruto=1478 e 1017. Isso NAO comprova que o firmware calcula sempre delta zero nem que 48 01 alimenta aquele campo da tela.
6. No log longo tambem aparecem 48 04 (17 respostas, 8 bytes), 48 05 (17, 8 bytes), 48 08 (644, 1 byte), 48 0B (3, 14 bytes): nao possuem o offset +0x1E.
7. DERIVADO/SINTETICO: mutacoes de quadro 48 foram usadas para verificar sinal em -32768..32767, checksum e rejeicao de payload errado. Valores negativos de teste NAO sao mudancas de calibracao ou dados da central.

## RED/GREEN

RED local: importacao do modulo novo ausente falhou como esperado. GREEN local: suite de investigacao com 11 testes passou; 7 quadros originais coincidiram. O CI do novo SHA e gate separado antes de declarar integraçao completa.

## Proximas dependencias

- P1: executar no original GetValue 0x96B924 com entradas reais +/-0,5, 2,5, valores acima do MaxValue, separador decimal e Enter; nao escolher incremento UI baseado so em Precision.
- P2: rastrear [obj+0x64] na rotina 0x42AF84 ate o subtipo 48 xx efetivamente usado pelo formulario; testar delta nao-zero e correlacionar a [form+0x1820] sem inventar periodo temporal.
- OP-16 e OP-20 permanecem PARTIAL. Nada de novo writer, ECU, merge ou APK incompleto.
