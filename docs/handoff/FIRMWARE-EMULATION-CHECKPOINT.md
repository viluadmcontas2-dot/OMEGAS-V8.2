# Firmware — emulação por observação

Estado: transporte original emulado; execução/decifração do bootloader DESCONHECIDA.

## Resultado executado

O Progbase.exe fornecido (SHA-256 `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`) foi mapeado num emulador x86 Unicorn. Não foi lançado como processo Windows. A porta serial foi substituída por um objeto em memória, sem API de dispositivo ou de rede.

As instruções originais de Progflash (`0x92839C`), SendS19flash (`0x9285A8`), SendByte (`0x922C1C`), SendChecksum (`0x923608`), Endflash (`0x92845C`) e ChecksumOk foram executadas. A instalação do frame de exceção Delphi é um hook sem efeito; as funções virtuais da porta são hooks explícitos. Os testes cobrem somente os caminhos exercitados, não exceções Delphi ou todo o aplicativo.

- **OBS-COD:** SendS19flash emite o prefixo ASCII `S` + tipo original, seguido de todos os pares hexadecimais da linha. Não usa o campo de contagem cifrado para determinar tamanho e não reconstrói endereços. Não acrescenta outro checksum à linha; já transporta o último byte original.
- **OBS-COD:** as duas leituras posteriores à linha devem concordar e conter `A7` ou `53`. Progflash espera duas leituras concordantes `A7`, depois do eco do comando `93 93`; não substitui o eco por A7. Este comportamento é mais específico que o resumo do doc 09.
- **OBS-COD:** o ramo cifrado de ScaricaFile (`0x929528..0x9295D1`) descarta uma primeira linha. O driver de ensaio aplica isso à linha da marca ENCRYPTED_ECU_FILE. Essa parte do wrapper foi reimplementada; o wrapper completo não foi executado.
- **OBS-COD/DERIVADO:** a execução das instruções transfere 2.294 registros / 88.814 bytes do EVO-L #00563 e 2.733 registros / 106.451 bytes do EVO #01153, incluindo os comandos standalone de início/finalização. O oráculo compara cada linha emitida byte a byte aos arquivos reais já extraídos da camada 1.
- **INFERIDO:** perfil `contract` fornece o eco e os ACKs esperados pelo host. Ele demonstra o contrato de transporte. Não emula a decifração nem a flash do bootloader.
- **DESCONHECIDO:** perfil `strict` tem somente eco modelado. Envia `93 93` e para na primeira resposta desconhecida do bootloader, sem aceitar nenhum registro.
- **OBS-FIO:** a fixture existente `portmon-autocal-real-sample.json` contém 6 transações; eco, checksum TX e checksum RX passaram 6/6. Nenhuma é de flash. Ela não prova respostas do bootloader.
- **OBS-COD:** o recurso TFORMSCARICA foi lido completamente: 1.710/1.710 bytes, janela 519×166, textos e posições preservados. A data 01/01/2000 é default do recurso, não data confirmada do arquivo.
- **DERIVADO:** preview HTML reproduz a geometria do DFM e faz replay das execuções salvas. O painel de ensaio fora do diálogo é novo. Não há print da janela de firmware para certificar equivalência visual. O print enviado trata do AutoCal.

## Verificações

`python test_progbase_cpu.py`: 7 testes passaram. Inclui os dois firmwares reais, falha de Progflash após 4 ACKs negativos, rejeição de linha após 9 ecos corrompidos/ACKs divergentes e detecção da mutação da instrução `shl edx,4` para `shl edx,3`. No mutante, o peer contratual pode aceitar bytes errados, mas o oráculo dos arquivos detecta a diferença: ACK sintético não comprova atualização.

`python render_report.py`: gera/valida replay em Chromium 1280×720 nos perfis estrito, contrato EVO-L e contrato EVO. Resultados desse comando ficam no recibo de renderização, após execução; não presumir sucesso por existência do HTML.

## Pré-condições e limites

Preparação/identificação de boot, compatibilidade de hardware, baud e erase são pré-condições artificiais do ensaio. PreparaProg/Cancflash e a rotina completa ScaricaFile não foram executadas. Endflash foi testada isoladamente; não se afirma que seja invocada nessa posição pelo fluxo completo. Nenhuma flash virtual em claro, ECU real, readback real, algoritmo de decifração ou validação física foi produzido.

A emulação permite acompanhar e corrigir o transporte; os bytes continuam cifrados quando chegam à porta. A próxima lacuna específica é uma resposta/caminho observado do bootloader e, para código em claro, sua transformação criptográfica. Não foi solicitado novo material ao proprietário.

## Reprodução

Dependências: `pip install unicorn pefile playwright`, `python -m playwright install chromium`.

Entradas externas (não versionadas): Progbase.exe original e os dois .s19 extraídos dos .ple. Para testes portáveis, definir `PROGBASE_EXE` e `FIRMWARE_S19_DIR`.

```
python progbase_cpu.py "$PROGBASE_EXE" "$FIRMWARE_S19_DIR/EVO_L_#00563.s19" --profile strict --out strict-evo-l
python progbase_cpu.py "$PROGBASE_EXE" "$FIRMWARE_S19_DIR/EVO_L_#00563.s19" --profile contract --out contract-evo-l
python progbase_cpu.py "$PROGBASE_EXE" "$FIRMWARE_S19_DIR/EVO_#01153.s19" --profile contract --out contract-evo
python test_progbase_cpu.py
python render_report.py
```

## Fontes consultadas

Skill OMEGAS emular-por-observacao na branch OmegasVermelho, referência consultada `5080d0abc44ed74dfff4f8ec751d35430f305edb`; branch OmegasPlatina observada `a125436e6d2d8bff549eada76ab8ffac6eb94da5`. Nenhum código de produto foi alterado.

PROTOCOLO-LANDI: 00-LEIA-ME, 02, 03, 09, 12, 13, 16; tools/dfm.py. Os resumos dos docs 09/16 que reconstroem S1/S2 pelo count não são adequados aos counts cifrados dos anexos; o código original executado fornece a regra de transporte usada aqui. tools/ecu_emulator.py, targets.txt e DecompTargets.java citados na skill não estavam disponíveis nos caminhos verificados. Foram reaproveitados o inventário, a documentação e o leitor DFM; somente os handlers necessários foram desmontados.
