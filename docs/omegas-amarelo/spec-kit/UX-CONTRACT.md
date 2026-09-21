# UX Contract — OMEGAS Amarelo

Norte derivado dos documentos de UX do owner:
- Blueprint Premium UI/UX — Método CUSTOMROM reutilizável.
- Método aplicado — Omega Dev 4.0 Premium UI/UX.

Os princípios necessários estão copiados aqui para a execução permanecer repo-first.

## Princípios
- A UI representa o trabalho humano, não subsistemas técnicos.
- Uma única autoridade de estado.
- Intenção -> ação -> consequência visível.
- Normalidade é compacta; problema ganha espaço.
- Resumo humano primeiro; detalhe técnico sob demanda.
- Cor tem semântica.
- Contexto não é perdido ao navegar.
- Evidência pode ser aprofundada sem poluir a superfície primária.
- Legibilidade automotiva 1280×720 é requisito, não acabamento.

## AutoCAL principal
Gráfico central inspirado na semântica do ProgBase, não em sua estética:
- Y = MAP [bar].
- X = Petrol injection time [ms].
- referência gasolina;
- GNV atual/anterior quando suportado;
- curva/estado nativo;
- fade semântico por idade, validade e confiança;
- região/ponto ativo sem ruído.

Gráfico secundário:
- Curva K / MUL_ACT atual;
- mudança antes/depois quando houver evento comprovado.

## Estados humanos
Exemplos de projeção:
- Aguardando condições.
- Coletando gasolina.
- Coletando GNV.
- Ajustando na ECU.
- Verificando resultado.
- Concluído.
- Degradado / dado stale / desconectado.

Não expor `TAutoCalDM`, endereços ou buffers como modelo mental primário.

## Detalhe técnico
Sob demanda:
- buffers;
- zonas;
- contador AutoMatch;
- MUL_ACT;
- snapshot/hash;
- protocolo/raw bytes;
- timing/proveniência.

## AutoCAL state-to-human projection — binding refinement

The native `PostActionRefresh` labels are refresh-scheduler labels, not user-facing ECU phases.

### Projection rules
- `state_0` / `state_1` / `state_2` are point/maturity refresh work. Do not show them as phases.
- `state_3` refreshes the observed Curva K projection. It does not, by itself, mean the ECU changed calibration.
- `state_4` refreshes acquired-zone projection.
- `state_5` refreshes reference/run geometry.
- `state_acquire_petrol_line` and `state_acquire_gas_line` refresh the slower native reference vectors through independent half-rate gates.
- `state_draw_gas_petrol_curve` redraws host/UI curves from already-read native vectors. A redraw must never produce the human label “Ajustando na ECU”.

### Human states require evidence
- **Aguardando condições** — AutoCAL enabled/available but no fresh qualified acquisition evidence.
- **Coletando gasolina** — fresh petrol point plus petrol maturity/zone progression.
- **Coletando GNV** — fresh gas point plus gas maturity/zone progression.
- **Ajustando na ECU** — only after native adjustment evidence, e.g. a new `MUL_ACT` mutation and/or proven AutoMatch execution evidence.
- **Verificando resultado** — post-adjustment fresh native readback after the mutation/action event.
- **Concluído** — requires a proven completion guard; do not infer completion from curve redraw, scheduler cursor, or absence of change.
- **Degradado / stale / desconectado** — driven by freshness/transport validity with explicit recovery guidance.

### CUSTOMROM / OMEGADEV consequences
- primary surface answers “o que está acontecendo agora?”;
- normal operation stays compact;
- problems and recovery gain space;
- intention -> action -> visible consequence must be preserved;
- technical detail (buffers, addresses, scheduler labels, raw bytes, counters) stays under demand;
- navigation must not erase AutoCAL context;
- color communicates semantic state, never decoration;
- the live plane and slow native-reference plane must expose independent freshness so a ~4 s reference refresh is not falsely shown as a disconnect;
- 1280×720 automotive legibility is a release requirement.
