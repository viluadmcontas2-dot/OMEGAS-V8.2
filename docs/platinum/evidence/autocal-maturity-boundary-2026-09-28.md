# Platina — maturidade AutoCal: correção do limite epistemológico

Data: 2026-09-28  
Status: **CORRIGIDO / SUPERSEDE A LEITURA CONSERVADORA ANTERIOR**

## Por que este documento mudou

Uma etapa anterior tratou o mapeamento de `CALIBRATION_VAL_1[2/5/8]` como não resolvido porque o módulo observado expõe 10 bytes enquanto a grade conceitual do ProgBase possui 12 linhas.

Essa cautela era válida antes da análise do consumidor original. Ela deixou de ser a melhor fonte de verdade quando o DUMP hash-bound e a desassemblagem do próprio ProgBase fecharam **quais campos o código original realmente seleciona em runtime**.

O ponto importante é: não precisamos supor que os 10 bytes correspondem linearmente às 12 linhas. O executável original já diz diretamente quais índices usa.

## Evidência canônica

Autoridade binária:

`ProgBase SHA-256 8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`

Oráculo original-derived versionado:

`tests/fixtures/progbase-autocal-resource-defaults-v1.json`

O consumidor recuperado em torno de `0x00516F64` fecha os seletores operacionais:

- bandas `0..5`, gasolina → `VECT_AUTOCAL_U8_1`;
- bandas `6..17`, gasolina → `CALIBRATION_VAL_1[2]`;
- bandas `0..5`, GNV → `CALIBRATION_VAL_1[5]`;
- bandas `6..17`, GNV → `CALIBRATION_VAL_1[8]`.

A tabela estática em `0x00A9DA1A` contém `05 09 0D`, coerente com os limites inclusivos das quatro zonas: `5 / 9 / 13`.

O DFM também fecha que:

- `VECT_AUTOCAL_U8_1` = `!AUTOCAL_IDLE_MIN_BUF_UPD_PETR_THD`;
- `VECT_AUTOCAL_U8_2` = `MaxAutomatch`.

Portanto `Maxautomatch` **não** é threshold de maturidade.

## Consequência para a Platina

A produção deve promover os seletores acima, lendo os valores reais da ECU e aplicando apenas a escolha de campo já provada pelo ProgBase.

Isto não autoriza o host a reproduzir a fórmula interna de AutoMatch. A separação continua:

- aquisição/maturidade: observar buffers, contadores, zonas e thresholds provados;
- AutoMatch nativo: ECU é a autoridade;
- Curva K: host apenas observa automaticamente; escrita continua exclusivamente manual, confirmada e com readback.

## Implementação vinculada

`AutoCalAcquisition.kt` publica:

- `petrolLow`;
- `petrolNormal`;
- `gasLow`;
- `gasNormal`;
- `calibrationValueMapping = PROGBASE_DUMP_GRID_PROVEN`.

`NativeAutoCalMonitor.kt` consome `gasLow` e `gasNormal` e os entrega ao `NativeAutoCalMaturityTracker`.

Os contratos que impedem regressão incluem:

- `tests/test_platinum_autocal_maturity_boundary.py`;
- `tests/test_native_autocal_contract.py`;
- `tests/test_platinum_dump_autocal_coherence.py`;
- `app/src/test/java/com/omegas/prohub/autocal/AutoCalAcquisitionTest.kt`;
- `app/src/test/java/com/omegas/prohub/autocal/NativeAutoCalMaturityTrackerTest.kt`.

## Limite epistemológico que permanece

Ainda **não** está provada a aritmética interna do firmware que calcula uma nova `MUL_ACT` durante AutoMatch.

Logo, o stop condition correto não é bloquear os thresholds já fechados. É bloquear qualquer tentativa de transformar observação em fórmula de escrita automática sem nova evidência direta do firmware/runtime.

## Regra de regressão

Se uma mudança futura voltar a marcar `CALIBRATION_VAL_1[2/5/8]` como “não resolvido” sem refutar o consumidor original acima, a mudança está regressando a evidência já destilada e deve falhar no gate Platina.
