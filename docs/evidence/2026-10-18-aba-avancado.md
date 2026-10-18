# Aba Ferramentas > Avançado (2026-10-18)

Marcas: **OBS-FIO** = visto no Portmon LOGNOVO (12/08/2026); **OBS-COD** = visto/executado no código do ProgBase.exe (sha256 8a2d297c…); **DERIVADO** = conta feita a partir do visto; **INFERIDO** = dedução; **DESCONHECIDO** = sem prova.

## O que a aba faz
- **Leitura (só leitura, por evento):** `FLAG_CONF1` (cód. 3), `FLAG_CONF2` (192), `FLAG_CONF3` (250), `PARAM_PROGRESSIONI` (308), `GIRI_PER_BENZINA` (161), `NUMERO_INJ_BENZINA_CUTOFF` (106) e `ANTI_STALLO` (307) entram no `param_snapshot` existente, na primeira leitura válida da sessão USB. Não lido = "—".
- **Escrita manual (o dono autorizou o botão):** só o bit `0x20` da palavra 0 de `FLAG_CONF3` ("Abilitazione anti-stallo"). Toque → prévia "antes → depois" → toque de novo (confirmação) → SetVector `0x35` → ACK → leitura de volta → só então "gravado". "Desfazer" repete o fluxo com o bit anterior. Nada grava sozinho.
- Texto da tela: anti-apagão no topo ("Evita o motor apagar quando você tira o pé"); bits, códigos e números em "Ver detalhes".

## Fatos por marca
| Item | Marca | Fonte |
|---|---|---|
| A caixa é o bit 0x20 da palavra 0 de FLAG_CONF3 | OBS-COD | `2026-10-18-anti-stallo-caixa-re.md` |
| Quadro `35 FA 00 20 00 00 00 4F` (ligar de 0000 0000), `35 FA 00 00 00 00 00 2F` (desligar), `35 FA 00 30 0C CD AB E3` (0C10 ABCD + marcar) | OBS-COD (execução emulada do setter) — **nunca visto ao vivo no fio** | `2026-10-18-cpu-setter-original.md`; testes `avancado-escrita.test.cjs`, `AntiStalloFlagCoreTest.kt`, `test_avancado_ecu_emulada.py` |
| Formato SetVector de 2 × u16 e checksum (`35 03 00 86 2C 51 10 4B`) | OBS-FIO | LOGNOVO linha 31284 |
| Ordem dos bytes (palavra 0 lo,hi; palavra 1 lo,hi) = a da leitura `29 FA 00 23` → `53 04 20 00 00 00 77` | OBS-FIO (formato da leitura) / DERIVADO (eco `20 00 00 00` da emulação) | `2026-10-18-ecu-emulada.md` |
| Valores da ECU do dono: FLAG_CONF1 2486 1051, FLAG_CONF2 0001 0000, FLAG_CONF3 0000 0000, ANTI_STALLO 1600 100 1500 2 20, GIRI_PER_BENZINA 0 8500, NUMERO_INJ_BENZINA_CUTOFF 0, PARAM_PROGRESSIONI (0,4,500)(20,4,500)(20,4,500)(25,4,500) | OBS-FIO (de 12/08/2026; hoje é lido de novo a cada sessão) | `portmon_ler_vetor.py` |
| Formas de leitura: `29 lo hi` (vetores), `09 6A 00` (escalar), **`2A 34 01 linha`** (PARAM_PROGRESSIONI, 4 linhas × 3 u16) | OBS-FIO | LOGNOVO |
| Bits decodificados: só os com caixa/lista no original, condição "sempre" e sem ambiguidade (47 bits; ver `flags-do-original.json`). Bits ambíguos (FLAG_CONF1 w0 0x8000, w1 0x0800 etc.) e sem caixa ficam "desconhecido" | OBS-COD (tabela) + regra do app | `tools/ci/gerar_flags_avancado.py`, teste refaz a regra sozinho |
| Rótulos em português | DERIVADO (tradução livre dos rótulos italiano/inglês) | — |
| Lista (combo) FLAG_CONF1: o bit é provado; o significado de cada valor da lista | DESCONHECIDO (a tela diz "Bit ligado/desligado") | |
| ANTI_STALLO: os 5 campos (rpm máx p/ ativar, delta rpm p/ ativar, atraso p/ sair do gás em ms, histerese, delta rpm p/ desativar) | INFERIDO (numeração DataIndex 0–4 da tela do original); a tela avisa "ordem inferida" | `2026-10-18-tela-anti-stallo-original.md` |
| PARAM_PROGRESSIONI: 4 linhas × 3 números | OBS-FIO (formato); significado de cada coluna DESCONHECIDO | |
| GIRI_PER_BENZINA = rpm; NUMERO_INJ_BENZINA_CUTOFF = nº de injeções | INFERIDO só pelo nome; papel DESCONHECIDO | |

## O que continua DESCONHECIDO
- Efeito físico: se a ECU passa a atuar no anti-apagão e se isso evita apagões.
- Se a ECU aceita o SetVector 0x35 para o código 250 (nunca visto ao vivo): o app confere por leitura de volta e mostra erro honesto se divergir.
- Se a ECU **mantém** o bit depois de rodar o próprio ciclo (só uma leitura de volta é feita; não há releitura por tempo).
- Um possível **2º quadro identificativo (código 53)** que o original pode mandar depois: **o app NÃO envia**; documentado, não implementado.
- Condição `[obj+0x80D]`/`[obj+0x80E]` do original (modelo de ECU).
- Valor real do FLAG_CONF3 hoje (o log é de 12/08/2026): a aba mostra "—" até a leitura da sessão chegar.

## Como foi provado
- JS (`tests/ui/avancado-leitura.test.cjs`, `avancado-escrita.test.cjs`, 40 testes): tabela = conjunto PROVADO refeito do JSON; "—" para não lido; bits fora da tabela nunca viram 0; quadros; ler-modificar-escrever; leitura de volta igual; fluxo de toque (nunca grava sozinho, token, desarma). Mutações mortas: máscara, sem preservar bits, endianness, readback fraco, confirmação automática, desconhecido como 0.
- Python (`tests/test_avancado_ecu_emulada.py`): quadro do JS alimenta `ecu_emulator.py`; eco+ACK `53 00 53`; leitura `53 04 20 00 00 00 77`; desfazer volta byte a byte ao original; mutações (checksum, bit, ordem de bytes) reprovam.
- Kotlin: `AntiStalloFlagCoreTest` (16), `AdvancedParamsStoreTest` (5), `AntiStalloFlagWriterTest` (9, ECU falsa em bytes), `EcuParameterSnapshotTest` (+6). Compilados e executados localmente com kotlinc 2.0.21 + JUnit (classes puras e o escritor com o scheduler falso); mutações mortas.
- Imagens (Chromium 1280x720, ponte de mentira, nenhuma ECU): `docs/evidence/img/avancado-1-desligado.png`, `-2-previa.png`, `-3-ligado.png`, `-4/-5/-6-detalhes-*.png` (`tools/ci/capturar_avancado.cjs`).
- `tools/ci/sem_rolagem_abas.cjs`: Avançado (principal e 4 abas de detalhes) sem rolagem nem corte a 672 e 720.
