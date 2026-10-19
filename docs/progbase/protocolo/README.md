# Protocolo do programa original

**DERIVADO:** esta pasta reúne o que o programa pede à central, o que ela responde e como transforma os números. Pesquisa somente: os scripts não abrem porta serial, não executam o programa e não gravam na central.

| Pergunta | Documento | Tipo de prova |
|---|---|---|
| Quais pedidos e respostas existem? | [comandos.md](comandos.md) e comandos.json | OBS-COD + OBS-FIO, conforme cada entrada |
| Quais campos têm escala, unidade e endereço? | [vetores.md](vetores.md) e vetores.json | OBS-COD; ocorrência no fio quando disponível |
| Quais respostas apareceram nas capturas? | [matriz-comando-resposta.md](matriz-comando-resposta.md) | OBS-FIO; replay da captura não prova conexão ao programa |
| Como a conexão muda de estado? | [conexao.md](conexao.md) | OBS-COD da função 0x00923FDC; limites explícitos |
| O que falta no aplicativo? | [backlog.md](backlog.md) | DERIVADO de busca textual; menção não prova implementação |
| O que ainda falta investigar? | [PROGRESSO-D2.md](PROGRESSO-D2.md) | DESCONHECIDO onde indicado |

## Reprodução

**DERIVADO:** use o Python que tenha `pefile`, `capstone` e `unicorn`. Os caminhos das entradas privadas são informados pelo operador; não copie executável, DLL ou licença para o repositório.

```sh
python3 tools/progbase/protocolo_gerar.py --exe "$PROGBASE_EXE" --log "LN=$PROGBASE_LOG" --log "AC=$PROGBASE_LOG_AC" --destino /tmp/protocolo-reproduzido
python3 -B -m unittest discover -s tests -p test_progbase_protocolo.py
```

**DERIVADO:** o primeiro log alimenta comandos e vetores; todos alimentam a matriz. O orquestrador interrompe no primeiro erro. Os extratores verificam instruções, extraem os recursos, fazem replay e calculam o backlog nessa ordem. Mesmas entradas, mesma ordem dos logs e mesma revisão do app devem gerar os mesmos bytes; conferir duas saídas temporárias antes de substituir os catálogos versionados. Uma falha pode deixar arquivos de etapas anteriores: saída parcial não é validação completa.

**DERIVADO:** sem `PROGBASE_EXE`/`PROGBASE_LOG`, os testes de oráculo/replay são marcados `skip`; os testes sintéticos de framing, fragmentação, falhas e orquestração continuam. Skip não prova o binário. O teste com binário confere SHA-256 e as instruções catalogadas; requer as três dependências acima.

## Limite das conversões

**OBS-COD:** `protocolo_x87.py` referencia raw→físico em `0x009757DC`, físico→raw em `0x009752C8` e coeficientes em `0x009766CC`. O caminho de fórmula é `(raw*A+B)/(raw*C+D)` e o inverso é `(B-físico*D)/(físico*C-A)`.

**DESCONHECIDO:** as 163 divergências de 2.000 casos de `ttNone` citadas na retomada não foram resolvidas nesta entrega. Não extrapolar os testes de fórmula a `ttNone`, tabelas, float32, mesclagem de máscara ou valores fora da faixa. O pseudocódigo em `pc_14.jsonl.gz`, endereço `0x009752C8`, diferencia `ttNone`, sinal e Single; também limita conjuntamente mínimo/máximo, enquanto a referência Python trata cada limite isoladamente. Isso exige vetores executados contra o original antes de corrigir a referência; o pseudocódigo sozinho não determina arredondamento x87.
