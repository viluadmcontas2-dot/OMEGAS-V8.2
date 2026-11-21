# Semântica do anti-apagão — pesquisa de fontes e critérios de segurança

> 2026-11-17. P1/OP-16/OP-20. **Pesquisa/documentação, não uma alteração na ECU nem no aplicativo.** Fonte de verdade do software original não é, isoladamente, prova de efeito na central física do dono.

## Fontes cruzadas

- Original Delphi: `docs/evidence/dfm-original/TFORMCONFIG.txt` e `docs/evidence/2026-10-18-tela-anti-stallo-original.md`, controles `EditAntiStallo0..4` e nomes italianos; vetor `ANTI_STALLO`, SerialCode 307 (0x0133), cinco `u16`, índices 0..4.
- Execução original emulada: `docs/progbase/protocolo/anti-apagao-quadros-original.md`. Testou a aceitação de **1601 rpm** no índice 0 e **5** de histerese no índice 3; o protocolo monta um quadro `14 33 01 índice lo hi soma` por valor, sem obrigatoriedade de múltiplos de 5 ou 50.
- Código do app: `app/src/main/assets/ui/core/anti-apagao-valores.js` e `components/advanced.js` usam passos fixos `50/5/100/5/5`; `AntiStalloParamsCore.kt` valida intervalos mas não múltiplos dos passos.
- Manual externo para compreender conceitos, **não equivalente à MP48**: OMVL DREAM ON, seção 9.4 (“Gestione FPT”), https://it.scribd.com/document/517050359/201801111057-OMVL-0-4-14-0-manuale-sw-00-I-c . Descreve diferença de rotações de um ciclo para o outro, limiar de queda, espera e limiar de recuperação. Os nomes e direções não podem ser transplantados automaticamente.
- Sessões do dono: `docs/progbase/bins/limiares-em-movimento.md`; eventos classificados por limiar de RPM não simulam o efeito da ECU.

| Índice | Original / explicação prudente | Unidade original | Máximo DFM | Referência no vetor | Passo JS | Inconsistência |
|---|---|---|---:|---:|---:|---|
| 0 | Max giri attivazione — rotação máxima de habilitação da proteção | rpm | 6000 | 1600 | 50 | Original emulado aceita 1601; passo de 50 não é limite de protocolo |
| 1 | Delta giri attivazione — diferença de giro por ciclo que habilita proteção | **rpm/ciclo** | 1000 | 100 | 5 | JS indica erradamente `rpm`; sentido do comparador não comprovado na MP48 |
| 2 | Attesa uscita gas — espera para deixar o gás | ms | 10000 | 1500 | 100 | Não confundir com espera de **retorno** ao gás do manual OMVL, geração diferente |
| 3 | Isteresi su soglia attivazione — histerese no limiar | **rpm/ciclo** | 1000 | **2** | **5** | Passo 5 move o padrão 2→7 (variação relevante), sem fonte para justificar |
| 4 | Delta giri disattivazione — diferença de giro por ciclo para encerrar proteção | **rpm/ciclo** | 1000 | 20 | 5 | JS exibe `rpm` sem `/ciclo` e não demonstra direção da comparação |

O DFM registra `Precision = 1.0` em todos os cinco `TNumericEdit` e `MaxValue` conforme tabela, mas não registra `Increment = 5` nem `Increment = 50`. **Precisão numérica de 1.0 não basta para prescrever passo seguro de toque 1, 2, 5 ou 10.** O `MinValue` não aparece: o piso 0 no app decorre do tipo `u16`, não de recomendação de operação.

**Situação real da tela atual:** `FIELDS = SLOTS.filter(f => f.effect)` apresenta somente o campo 0 na UI principal; os índices 1..4 estão presentes no catálogo `SLOTS`/manager nativo e ocultos nesta revisão. Logo, a divergência de incrementos nos extras é latente ou aplicável a APKs anteriores, não afirmar que todos os cinco botões existem no HEAD.

## Hipóteses e gates, SEM mudar writes

1. P1 **semântica**: quando houver campo disponível, nome, unidade, faixa/escala, referência, consequência e origem devem estar corretos; não chamar `rpm/ciclo` de `rpm`.
2. P1 **passo**: testar o arredondamento exato do Delphi `TNumericEdit.GetValue/OnKeyPress` e confirmar os valores permitidos por teste original; não escolher passo 2/5/10 arbitrariamente, nem confundir `Precision` com recomendação mecânica.
3. P2 **efeito**: correlacionar `Delta giri` (quadro `48 xx`, s16 no offset 0x1E) aos campos e ao ciclo do firmware; a OMVL explica o conceito, mas não comprova direções/prazos da MP48.
4. Segurança: não liberar extras sem prova de suporte no perfil do carro; separar readback real de ACK; testes fora da condução, sem escritor ou estratégia nova.
5. **Liberação de APK final bloqueada:** continuar trabalhos P0/P1/P2 e homologação física até completar requisitos; nenhum APK intermediário.

Este marco **não conclui** as Tasks Mestre nem OP-16/20; fornece fonte concreta para os próximos testes RED→GREEN.
