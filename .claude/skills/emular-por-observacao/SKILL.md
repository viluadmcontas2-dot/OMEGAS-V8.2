---
name: emular-por-observacao
description: Use quando um programa, ECU ou tela alvo não pode ser executado aqui (Windows, hardware) e é preciso entender, reproduzir e testar o comportamento só por observação — capturas seriais, recursos do executável, decompilação e prints. Ensina a montar emuladores em Python, renderizar a tela original e rodar o app novo com os dados reais, em vez de pedir ao dono para gravar mais coisa.
---

# Emular por observação

Regra de ouro: **se não dá para rodar o original, emule o que ele observa e o que ele responde.** Não peça captura nova ao dono antes de esgotar o que já existe (capturas, recursos do .exe, código decompilado, prints). Foi assim que o AutoCal foi reconstruído (PROTOCOLO-LANDI docs 17–26).

## Receita (nesta ordem; pare quando a pergunta estiver respondida)

1. **Inventário do que já existe.** Comece por `docs/mapa-de-fontes/MAPA.md` (onde está cada arquivo: repo, PROTOCOLO-LANDI, Drive) — nunca peça ao dono o que está lá; `python3 tools/obter_progbase.py` baixa o ProgBase.exe e `python3 tools/baixar_drive.py <id>` qualquer arquivo da pasta OMEGAS do Drive (link direto; o conector não baixa >10 MB). Capturas (Portmon etc.), recursos do executável (formulários DFM/RCDATA), strings, ini, prints do dono. Só depois pense em abrir o binário.
2. **Capturas → transações.** Parser que junta `WRITE` + `READ` em pares requisição/resposta (`tools/portmon_transacoes.py`; para logs com várias portas: `tools/portmon_dispositivo_ecu.py`, filtra só o dispositivo da ECU). Valide: checksum 100%, tamanho de quadro, ecos. Portmon não tem relógio: o tempo é a soma das durações.
3. **Esquema pelos recursos.** Leitor de formulários Delphi (`tools/dfm.py`) → tabela de objetos (endereço, escala, tamanho). Isso dá nomes e unidades de graça e mostra o que é de outro módulo (ex.: `0x017C` era Estratégia Panda, não AutoCal).
4. **Decompilar só os handlers necessários.** Ghidra headless com **projeto salvo** (`tools/ghidra/run.sh` + `DecompTargets.java`; a 1ª análise leva ~12 min, as seguintes segundos). Rode em segundo plano. Código com ponto flutuante x87 sai quebrado: desmonte com `capstone` e leia a conta (foi assim que saiu a fórmula do `ExportToK`). Procure o início da função voltando até `55 8B EC`.
5. **Estado no instante t.** `capture_state.py`/`orig_emulator.state_at`: reconstrói os vetores como a ECU os mostrava naquele momento.
6. **Emulador do aparelho** (`tools/ecu_emulator.py`): framing, eco, ACK, NACK, leituras, escritas, ações. **Teste que regenera as respostas da captura byte a byte** e reproduz efeitos observados (ex.: Reset all). O que o firmware decide e não foi visto fica "não modelado" — nunca invente.
7. **Emulador da tela original** (`tools/orig_emulator.py`): monte SVG/HTML a partir do esquema + regras provadas no código; tire foto com Chromium (`--screenshot`) e **compare com o print do dono**. Se bate em 3 instantes, as regras estão certas.
8. **Rode o app novo de verdade:** Playwright/Chromium com a ponte nativa trocada por um stub alimentado pelo estado da captura (`tools/render_omegas.cjs`). Ache bug de tela vendo o PNG, não só lendo código. Viewport 1280x720.
9. **Teste com dado real:** fixture tirada da captura; teste de **mutação** (estrague a regra de propósito e veja o teste falhar — senão o teste não prova nada).
10. **Documente cada afirmação** com uma marca: OBS-FIO (visto no fio) · OBS-COD (provado no código) · DERIVADO · INFERIDO · DESCONHECIDO. Desconhecido nunca vira "0" nem "regra". Padrão visto em poucos eventos é **observação**, não regra (o dono corrigiu isso uma vez).
11. **Atualize o artefato e o checkpoint** a cada avanço; commit + push; leia o remoto para confirmar.

## Armadilhas já pagas

- `pkill -f ghidra` mata o seu próprio shell. Mate por PID.
- O MCP do REA estoura 60 s no `ProgBase.exe`; use o CLI/Ghidra em segundo plano.
- Testes em `vm` do Node: objetos de outro "realm" falham em `deepEqual`; normalize com `JSON.parse(JSON.stringify(x))`.
- Não sobrescreva texto de rótulos/inscrições do print do dono: marcas (como "+" verde) podem ser anotação dele.
- Antes de culpar o app, ache o estado real: "gráfico trava" pode ser porta lógica (referência inutilizável), não erro de JS.
- Clones parciais (sparse) exigem `git add --sparse`.
- Não cite resultado sem ter rodado. Teste que não exercita o comportamento observado não é PASS.

- Caixa/flag de tela Delphi sem `DataIndex` no DFM: o liga/desliga é um bit de `FLAG_CONFx`. Ache o método publicado na tabela de métodos da classe (entrada = tamanho u16, endereço u32, nome em shortstring), siga os acessos ao campo `[reg+offset]` do controle (tabela de campos: offset u32, tipo u16, nome) e os getters/setters genéricos (`0x42E754`/`0x42E8D8`, caso = índice do bit). Foi assim que o anti-stallo saiu (bit 0x20 da palavra 0 de FLAG_CONF3, cód. 250).
- Valide todo parser de captura contra um valor já conhecido antes de confiar (ex.: ANTI_STALLO lido do log = 1600,100,1500,2,20) e confira o checksum de cada resposta.
- Link direto do Drive devolve página HTML de login quando o arquivo não está compartilhado: confira os 4 primeiros bytes (PK/MZ) antes de usar.

## Quando ainda assim faltar dado

Entregue o menor pedido possível ao dono (um comando, um botão, uma captura) **depois** de ter preparado tudo, e diga exatamente o que a captura vai provar. Registre o resto como DESCONHECIDO.

## Lição: reproduzir o código byte a byte (sem rodar o programa)
Quando o programa não roda (licença/Windows), execute só as FUNÇÕES dele numa CPU emulada (Unicorn, x86-32): mapeie o .exe, monte um objeto falso com os campos que o código lê, stube as chamadas de sistema e capture o ponto de escrita serial. Prova: o quadro sai do código executado (teste de mutação muda 1 byte e o quadro muda) e bate com o fio. Nunca modifique o executável nem contorne licença. Ver tools/emulacao/cpu_setter_original.py.

## Regra: engenharia reversa sempre com REA
Toda vez que abrir/decompilar o .exe: invoque a skill `reverse-engineer-anything` e use as ferramentas do MCP REA que couberem (`verify_reconstruction`, `build_reconstruction_obligation_ledger`, `evaluate_reconstruction_coverage`, `audit_residual_unknowns`, `compare_functions`/`compare_application_versions`, `capture_process_scenario`). Se o MCP estourar tempo no ProgBase, use o CLI (`npx -y rea-agents@6.4.0`) em segundo plano. Registre evidence ids em docs/progbase/decomp/rea-verificacao.md. Meta: nunca mais abrir o .exe — toda dúvida se resolve em docs/progbase/ (repo) e na pasta PROTOCOLO-LANDI do Drive.
