# ANTI_STALLO e parâmetros vizinhos da ECU MP48 — o que se sabe (2026-10-18)

Pesquisa só de leitura. Resultado curto: **o significado de cada um dos 5 números de ANTI_STALLO é DESCONHECIDO.** Os repositórios e documentos consultados só provam o nome, o endereço, o formato (5 valores de 16 bits) e os valores de fábrica lidos. Nenhum documento diz o que cada posição faz.

## Fontes consultadas e limites

- `/home/user/protocolo-landi` **não existe nesta máquina** (só existe `/home/user/OMEGAS-V8.2`). Pelo `AGENTS.md:60` é o repositório GitHub `viluadmcontas2-dot/PROTOCOLO-LANDI`. Tentei anexá-lo à sessão e o pedido foi negado pelo classificador de permissões. Por isso o índice `00-LEIA-ME.md` e o `grep` nele **não foram feitos**. Se existir texto sobre ANTI_STALLO lá, este relatório não o viu.
- `docs/` do OMEGAS-V8.2: `grep -ri 'ANTI_STALLO|ANTISTALLO|STALL'` não achou nenhuma explicação de ANTI_STALLO. As únicas ocorrências são o catálogo, o JSON de parâmetros e a nota de valores (abaixo). Os "STALL" restantes são sobre apagão do motor (`engine_stall`), sem relação com este parâmetro.
- `docs/evidence/2026-10-16-*`: não citam nenhum parâmetro da ECU. Provam só o risco de apagão ao retomar a injeção (tabela abaixo).
- `ProgBase.exe` e ferramentas REA: não usados, como pedido.

## Tabela principal: os 5 números de ANTI_STALLO

Endereço (SerialCode) `0x0133` = 307, tipo U16, 5 elementos, no bloco TSTREAMDATI (`docs/reference/progbase/CATALOGO.md:385`; `docs/reference/progbase/parametros.json:5361-5376`). Leitura em vetor de 5 × 2 bytes (little-endian), em `app/src/main/java/com/omegas/prohub/autocal/EcuParameterSnapshot.kt:45`. Valores de fábrica 1600·100·1500·2·20 (`docs/handoff/AUTOMATCH-MANUAL-2026-10-17.md:53`; decodificação conferida no teste `app/src/test/java/com/omegas/prohub/autocal/EcuParameterSnapshotTest.kt:15-17`).

O JSON diz literalmente "sem confirmação de comportamento de leitura/escrita por ação", `unidade`/`faixa`/`padrão` = `null`, `funcao` = "outro" (`parametros.json:5373`).

| Posição | Valor de fábrica | O que se sabe | Certeza | Fonte |
|---|---|---|---|---|
| ANTI_STALLO[0] | 1600 | É um número de 16 bits lido da ECU. Unidade e papel não documentados. 1600 está na mesma ordem de grandeza das rotações de retomada de injeção (ver tabela de risco), mas isso é coincidência de escala, não prova de que seja rpm. | DESCONHECIDO | `EcuParameterSnapshot.kt:45`; `parametros.json:5373` |
| ANTI_STALLO[1] | 100 | Idem. Nada indica unidade. | DESCONHECIDO | idem |
| ANTI_STALLO[2] | 1500 | Idem. Mesma escala de [0]; nada prova que seja rpm. | DESCONHECIDO | idem |
| ANTI_STALLO[3] | 2 | Idem. Valor pequeno; pode ser contagem, ms ou flag, sem prova. | DESCONHECIDO | idem |
| ANTI_STALLO[4] | 20 | Idem. | DESCONHECIDO | idem |
| Conjunto (nome + grupo) | — | O nome é italiano "anti-stallo" (anti-engasgo/apagão). O app agrupa o parâmetro sob o comentário "Anti-engasgo e troca de combustível" (decisão do autor do app, não da fabricante). Que os 5 valores componham uma regra contra apagão é só leitura do nome. | INFERIDO (só pelo nome) | `EcuParameterSnapshot.kt:44-45` |
| Valores de fábrica | 1600·100·1500·2·20 | Lidos da ECU do dono no Portmon `LOGNOVO` de 12/08/2026 e descritos como "padrão de fábrica". O "padrão de fábrica" é afirmação do handoff; o JSON do catálogo tem `padrao: null`. | PROVADO que foram lidos; INFERIDO que sejam de fábrica | `AUTOMATCH-MANUAL-2026-10-17.md:53`; `parametros.json:5372` |

## Tabela dos parâmetros vizinhos

Nenhum destes tem descrição de função no catálogo; todos dizem "sem confirmação de comportamento" (`parametros.json`, campo `descricao_pt`). Os valores vêm do Portmon e das leituras do dono.

| Parâmetro | Endereço / tipo | Valor | O que se sabe | Certeza | Fonte |
|---|---|---|---|---|---|
| GIRI_MIN_CAMBIO | `0x0010` (16), U16 ×1 | 1200 | Prefixo `GIRI_` = rotação no glossário do app, que o lista na categoria RPM. Valor 1200 lido como "rpm" no handoff. Que seja o "rpm mínimo para trocar de combustível" é só leitura do nome (cambio = troca). `funcao` = "comutacao". | INFERIDO (rpm, troca de combustível); PROVADO só o valor lido | `parametros.json:326-341`; `CATALOGO.md:137`; `tools/protocolo-artifact/template.html:949`; `AUTOMATCH-MANUAL-2026-10-17.md:53` |
| RITARDO_CAMBIO | `0x0011` (17), tipo não expresso ×1; app lê 1 byte | 35 (valor informado no pedido; **não encontrei esse 35 em nenhum documento do repo**) | "ritardo" = atraso, "cambio" = troca: provável atraso da troca de combustível. Unidade e tipo desconhecidos. Há só uma sugestão de uso futuro: "excluir janelas de troca do cálculo de economia", marcada como hipótese. | INFERIDO (pelo nome); unidade DESCONHECIDA | `parametros.json:345-360`; `CATALOGO.md:138`; `EcuParameterSnapshot.kt:47`; `docs/reference/progbase/OPORTUNIDADES.md:63-66` |
| SMAGRIMENTO_RIENTRO_CUTOFF | `0x0088` (136), tipo não expresso ×1; app lê 1 byte | 100 | "smagrimento" = empobrecimento da mistura; "rientro cutoff" = retorno do corte. Pelo nome, é intensidade do empobrecimento no retorno do corte. O próprio doc diz que o significado exato de cada byte "não foi confirmado". Unidade (100 = %?) DESCONHECIDA. | INFERIDO (pelo nome) | `parametros.json:2701-2716`; `CATALOGO.md:157`; `docs/evidence/2026-10-13-retorno-do-corte-e-inteligencia-local.md:18` |
| NUMERO_INIETTATE_SMAGRIMENTO | `0x0089` (137), tipo não expresso ×1; app lê 1 byte | 0 | Pelo nome, quantas injeções de empobrecimento. Com valor 0, o doc conclui que o empobrecimento **não** parece vir daí, marcado INFERIDO. `funcao` = "injetor". | INFERIDO | `parametros.json:2720-2735`; `CATALOGO.md:62`; `2026-10-13-retorno-do-corte-e-inteligencia-local.md:18` |
| SMAGRIMENTO_EXTRA | `0x0016` (22), tipo não expresso ×1; app lê 1 byte | 5 | Pelo nome, empobrecimento adicional. Sem unidade, sem texto de função (`funcao` = "mapa"). Valor 5 lido da ECU do dono. | DESCONHECIDO (papel); INFERIDO só o nome | `parametros.json:440-455`; `CATALOGO.md:182`; `2026-10-13-retorno-do-corte-e-inteligencia-local.md:18` |

## O que está PROVADO sobre apagão (contexto, não explica os parâmetros)

Risco de apagar ao retomar a injeção depois de um corte (n = 1011 retomadas em GNV, 50+ sessões; `docs/evidence/2026-10-16-sessao-18-07-apagoes.md:~48-56`, seção "Apagões × retomada após corte"):

| rpm na retomada | retomadas GNV | quase-apagou |
|---|---|---|
| < 1550 | 240 | 24 (10,0%) |
| 1550–1700 | 136 | 8 (5,9%) |
| ≥ 1700 | 635 | 4 (0,6%) |

- Provado: o risco cai cerca de 16 vezes entre retomar abaixo de 1550 rpm e acima de 1700 rpm.
- O mesmo arquivo diz: "Falta: (1) saber se a ECU expõe esse limiar de retomada (o app não o altera hoje)". Ou seja, **ninguém ligou esse limiar a nenhum parâmetro**. Não há evidência de que ANTI_STALLO[0]=1600 ou [2]=1500 sejam esse limiar, apesar da proximidade numérica. Não afirmar isso.
- O Handoff `docs/handoff/TEIA-DO-APP-2026-10-17.md:34` repete o ponto como "aberto: experimento controlado".

## O que resolveria o DESCONHECIDO

1. Ler o repositório PROTOCOLO-LANDI (`00-LEIA-ME.md`, `12-MAPA-OPCODES.md`) e rodar o `grep` nele. Esta pesquisa não conseguiu acessá-lo. É o primeiro passo mais barato.
2. Experimento controlado com a ECU e o dono dirigindo: gravar ANTI_STALLO (um valor por vez, com leitura de volta) e medir a rotação de retomada e a taxa de quase-apagão em retomadas pós-corte (~20 por valor). A captura precisa ter `param_snapshot` (já gravado a cada conexão, `SessionRecorder.kt:893`) mais os quadros `48 01` ao redor do corte. Só isso separa qual posição mexe na retomada. Faz alterar a ECU: exige decisão do dono.
3. Para unidade de RITARDO_CAMBIO e SMAGRIMENTO_*: capturar o Portmon do ProgBase original ao mudar só esse campo na tela (a tela mostra a unidade). Também ajudaria um dump do Delphi com `escala`/`unidade`, hoje `null` em todo o catálogo.
4. Medir o tempo entre troca gasolina → GNV com RITARDO_CAMBIO em dois valores para obter a unidade (precisa de captura com os dois valores).

## Atualização (fonte que o subagente não alcançou): rótulos da tela do ProgBase original

Fonte: `protocolo-landi/dfm/i18n_all.json` (chaves `CHECKANTISTALLO`, `LABELANTISTALLO0..4`) e `dfm/TSTREAMDATI.txt` (objeto `ANTI_STALLO`, SerialCode 307, 5 × u16, padrão `1600, 100, 1500, 2, 20`). Os rótulos numerados 0–4 são os campos da tela do original e batem em número com as 5 posições do vetor; a ORDEM posição → rótulo é INFERIDA pela numeração (não há captura que troque um campo só).

| Posição | Valor de fábrica (e da ECU do dono) | Rótulo do original (inglês / italiano) | Certeza |
|---|---|---|---|
| (liga/desliga) | — | "Enable un-stalling strategy" / "Abilitazione anti-stallo" (`CHECKANTISTALLO`, caixa de seleção) | PROVADO que existe; onde fica gravado: DESCONHECIDO |
| 0 | 1600 | "Max RPM to enable un-stalling" / "Max giri attivazione anti-stallo" | INFERIDO (numeração) |
| 1 | 100 | "Delta RPM to enable un-stalling" / "Delta giri attivazione anti-stallo" | INFERIDO |
| 2 | 1500 | "Delay to exit gas mode" / "Attesa uscita gas" (unidade DESCONHECIDA) | INFERIDO |
| 3 | 2 | "Hysteresys for enabling threshold" / "Isteresi su soglia attivazione" | INFERIDO |
| 4 | 20 | "Delta RPM to disenable un-stalling" / "Delta giri disattivazione anti-stallo" | INFERIDO |

Leitura em português simples: é a estratégia que, com a rotação baixa (até ~1600 rpm) e queda de rotação (~100 rpm), tira o motor do GNV para evitar o apagão e só volta depois de uma espera. Isso combina com a evidência de apagões (risco ~16× menor ao retomar acima de 1700 rpm). Ainda NÃO provado: a unidade da espera (campo 2) e qual campo é qual — só um teste mudando um campo por vez, ou um Portmon do original, fecha.
