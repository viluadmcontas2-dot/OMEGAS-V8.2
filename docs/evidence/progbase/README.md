# ProgBase original — evidência retomável

Branch: `evidence/progbase-original`, repositório `viluadmcontas2-dot/OMEGAS-V8.2`.
Missão: documentar transporte, telemetria, parâmetros, AutoCal, Curva/Mapa K e level do original. **A investigação continua; não está encerrada.**

## Escopo e fonte

A decisão de 2026-10-02 no checkpoint 9 prevalece sobre o prompt histórico: **somente DUMP e PortmonLOGNOVO (1).zip**. Consulte `fontes/INDICE-FONTES.md` para ids, hashes, offsets e limites. Sem alteração no app, operação em ECU ou publicação de binários proprietários.

- `PROVADO`: bytes do log ou estrutura/código original verificados; especificar o que exatamente foi provado.
- `INFERIDO`: hipótese com raciocínio, sem promovê-la por plausibilidade.
- `DESCONHECIDO`: lacuna e prova necessária.

Um ACK prova resposta ao comando, não seu efeito completo. Valores de polling não formam snapshot atômico. Ausência na captura não prova impossibilidade no firmware.

## Estado dos temas

| Tema | Arquivo | Estado |
|---|---|---|
| Transporte | protocolo.md | LN completo validado; política de falha/retry ainda parcial |
| Telemetria | telemetria.md | Timer1 reaberto: pressão/tensão/injeção provadas; tabelas de temperatura pendentes |
| Parâmetros | parametros.md | inventário DFM regenerado; consumidores e unidades ainda têm lacunas |
| AutoCal | autocal.md | três épocas e aquisição pós-3/3 provadas; b12 compacto desconhecido |
| Curvas e mapas | curvas-mapas.md | observações preservadas; mecanismo do readback ainda desconhecido |
| Level | level.md | referências e DFM; filtro/enum/conversão ainda parciais |
| Lacunas | lacunas.md | L-01…L-13; acompanhar resolução parcial |
| Registro | registry.json | 126 entradas; provas e limites sincronizados com a revisão de telemetria |

`fontes/parametros-dfm-inventario.json`: **364 componentes SerialCode**, extraídos de quatro DFM originais, com classe, propriedades explícitas, offset de propriedade/valor e hash por fonte. Substitui o inventário herdado de 344 entradas; propriedades ausentes não são defaults provados. `fontes/dfm2txt.py` e `fontes/fontes-manifest.json` permitem reprodução.

## Retomada

Leia `CHECKPOINTS.md`, depois a lacuna e o tema indicado. Commits 10–12: DFM reextraído, fontes completas, três épocas/LN validados e Timer1 reaberto. Próximo passo: identificar seletores +0x80D/+0x271D e recuperar tabelas de temperatura; depois rastrear 48 0B (L-13).

## Agenda de contraste posterior

Não executada nesta missão; a lista antiga não constitui prova sobre o app atual. Cinco questões de maior retorno: semântica escrita/readback de MAP_K; FLAG_CONF1 e bits de edição; eixos ECU SC 55/61; escalas e validade da telemetria; independência entre enable, aquisição e cota AutoMatch. Cada contraste deve usar o HEAD remoto do produto então vigente.
