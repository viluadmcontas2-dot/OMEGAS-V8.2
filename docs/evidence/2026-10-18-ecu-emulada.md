# ECU emulada a partir da captura Portmon (ProgBase × MP48)

Data: 2026-10-18. Skill `emular-por-observacao`, passo 6.

- Código: `tools/emulacao/ecu_emulator.py` (classe `EcuEmulada`, só stdlib)
- Testes: `tests/test_ecu_emulator.py` (12 testes, cerca de 3 s)
- Captura: `PortmonLOGNOVO.LOG`. Ela não fica no repo; o caminho vem da variável `ECU_CAPTURA`.

Marcas usadas: **OBS-FIO** (visto no fio), **DERIVADO** (tirado da doc do protocolo e conferido com a captura), **INFERIDO** (palpite razoável, sem prova) e **NÃO MODELADO** (decisão do firmware que a captura não mostra; o emulador não inventa nada aqui).

## O que tem na captura

- 20.288 pedidos na porta `Silabser0`, 567 pedidos distintos e 9.856 pares pedido→resposta distintos.
- Os pedidos mais comuns:

  | Pedido | Vezes |
  |---|---|
  | `48` (telemetria) | 12.765 |
  | `00 14` (status) | 4.693 |
  | `29` | 1.556 |
  | `09` | 502 |
  | `0A` | 299 |
  | `14` | 216 |
  | `2A` | 207 |
  | `35` | 19 |
  | `12` | 4 |
  | `02 24 04 04` (AutoCal) | 1 |

  Também aparecem comandos de sistema: `00 01/02/13/25` e `01 00/04/12`.
- Os 20.287 pedidos com mais de 1 byte têm checksum certo. As 20.287 respostas também: eco + ACK + n + dados + checksum. **OBS-FIO**
- O byte de acordar `00`, sozinho, recebe `80` sem eco. **OBS-FIO**

## Regras do emulador

1. **Formato do pedido.** O pedido é `op, parâmetros, checksum`, e o checksum é a soma dos bytes anteriores mod 256. O tamanho é `(op & 7) + 3`; quando `op & 7 == 7`, o segundo byte traz o tamanho. Isso bate com 100% dos pedidos. **DERIVADO**
2. **Formato da resposta.** A resposta é o eco do pedido, depois `53` (ok) ou `CA` (recusa), depois `n`, os `n` bytes de dados e o checksum. O checksum soma ACK, n e os dados, sem o eco. **OBS-FIO**
   - Conta de exemplo: `53+04+86+24+51+10 = 0x162`, então o checksum é `62`.
   - A única recusa vista é `CA 01 10 DB` (3.499 vezes). Ela só é devolvida para o pedido em que foi vista.
3. **Leituras** (`09`, `0A`, `29`, `2A`, `48 01`, `00 14` e os comandos de sistema vistos). O estado de cada pedido é a última resposta vista, mais o que as escritas mudaram depois dela. O quadro é remontado a partir de (ACK, dados), com o checksum recalculado. **OBS-FIO**
4. **Escritas.** Todas respondem `53 00 53`. Vistas: 19 × `35`, 216 × `14` e 4 × `12`, todas com ACK. **OBS-FIO**
   - `35`/`30..36 lo hi dados` grava o vetor inteiro. Muda a leitura `29 lo hi` e as `0A lo hi idx` já vistas. **OBS-FIO**
   - `37` (forma estendida): **DERIVADO** da doc 07; não aparece na captura.
   - `14 lo hi idx v_lo v_hi`: vetor de u16. Por exemplo, `14 3D 00 ...` aparece depois em `29 3D 00`. **OBS-FIO**
   - `14 lo hi linha coluna v`: matriz de 1 byte (endereço `0x54`, lida com `2A 54 00 linha`). **OBS-FIO**
   - O mesmo opcode `14` serve para as duas formas. O emulador escolhe pelo que já leu naquele endereço: se já leu `2A` → matriz; se já leu `29` → vetor; se já leu `09` com o mesmo tamanho → escalar. **INFERIDO**
   - `12 4A 01 v`: escalar de 1 byte. Bate com `09 4A 01` lido depois. **OBS-FIO**
5. **AutoCal** `02 24 04 <modo>` responde `53 00 53`, como visto. O efeito fica rotulado **NÃO MODELADO** (`ultimo_rotulo`, `efeitos_nao_modelados`).
6. **Pedido nunca visto, ou com checksum errado**: o emulador devolve só o eco, sem `53` nem `CA`. **NÃO MODELADO**
   - A captura não tem nenhum pedido com checksum errado, então a reação real da ECU é desconhecida.
   - Supor que o eco sai mesmo assim é **INFERIDO**.

## Resultados

**(a) Regeneração byte a byte: 9.856 de 9.856 pares distintos (100%).** Todas as 20.288 transações batem.

- Regra do instante: no pedido *i*, o estado tem todas as transações anteriores. Numa leitura, o valor da ECU no instante *i* é o que ela respondeu em *i*.
- Na leitura, o teste confere a remontagem do quadro e o checksum. Não confere a previsão do valor.
- As escritas não usam a resposta vista: saem da regra. São 239, incluindo as 19 `35`.

**Previsão estrita** (sem olhar a resposta do instante):

| Resultado | Leituras |
|---|---|
| Iguais | 10.098 |
| Mudaram | 9.561 |
| Primeira vez | 390 |

- Quase tudo o que mudou é telemetria `48 01` (9.161) e status `00 14` (278): são valores ao vivo.
- **Leitura logo depois de uma escrita: 3 de 16 batem.** Os 3 que batem são `29 03 00` depois de `35 03`, `29 3D 00` depois de `14 3D` e `09 4A 01` depois de `12 4A 01`.
- Os 13 que não batem:
  - 12 são linhas da matriz `0x54`. Ela foi gravada toda com `64` e voltou como `95…`. No meio houve o AutoCal `02 24 04 04` e uma reconexão. A matriz foi mudada pelo firmware. **NÃO MODELADO**
  - 1 é `29 03 00`. A última escrita foi `86 34 51 10` e a leitura voltou `86 24 51 10`: o bit `0x10` do byte 1 sumiu. No meio houve `01 12 00` e uma reconexão. **NÃO MODELADO**

**(b) Escrita de FLAG_CONF3** `35 FA 00 20 00 00 00 4F`:

- O checksum confere: `35+FA+20 = 0x14F`, então `4F`.
- A resposta é eco + `53 00 53`.
- A leitura seguinte `29 FA 00 23` devolve eco + `53 04 20 00 00 00 77`. O checksum confere: `53+04+20 = 0x77`.
- Na captura, `29 FA 00` sempre devolveu `53 04 00 00 00 00 57`.
- Os dados voltam como 4 bytes (`20 00 00 00`), com `n = 04`.

**(c) Mutação.** Com 1 a mais no checksum do pedido, a resposta é só o eco e o estado não muda. O rótulo é `NAO_MODELADO`.

**(d) Idempotência.** Carregar a captura duas vezes dá o mesmo estado. Repetir a mesma escrita ou a mesma leitura dá os mesmos bytes e o mesmo estado.

**Servidor TCP.** Ouve em `127.0.0.1`, separa os quadros pelo tamanho que vem no opcode e aceita quadros colados. Há um teste ponta a ponta.

## Como usar

```
python3 tools/emulacao/ecu_emulator.py --captura PortmonLOGNOVO.LOG --porta 5555
ECU_CAPTURA=/caminho/PortmonLOGNOVO.LOG python3 -B -m unittest tests.test_ecu_emulator
```
