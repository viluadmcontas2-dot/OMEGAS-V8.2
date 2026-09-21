# OMEGAS Verde — ProgBase AutoCal parity implementation plan

**Spec:** `docs/superpowers/specs/2026-09-21-omegas-verde-progbase-autocal-parity-design.md`  
**WorkUnit:** `OMEGAS-WU-006`  
**Epic:** #81

## Regra de execução

Não portar SIL/CIU. Não reauditar blocos já fechados sem evidência nova. Usar AgentRed em paralelo somente para tarefas independentes; integração é serial.

---

## Task 0 — congelar evidência e control surface

### Início
- [ ] resolver HEAD remoto;
- [ ] confirmar CI do HEAD;
- [ ] confirmar hash do ProgBase;
- [ ] confirmar corpus Portmon/fixture #68.

### Meio
- [ ] registrar hashes/origens em `docs/evidence/OMEGAS-WU-006.json`;
- [ ] reconciliar Issues #81–#86.

### Fim
- [ ] todos os agentes conseguem iniciar por Spec Kit/WU sem chat.

---

## Task 1 — mapa byte/consumer do ProgBase (#82)

### Início
- [ ] enumerar `TAutoCalUI`, `TAutoCalDM`, `TFormRifAutocal`;
- [ ] extrair nomes de séries, estados, timers/events;
- [ ] enumerar writes/reads Portmon relevantes.

### Meio
Para cada consumer:
- [ ] identificar bytes/endereço;
- [ ] identificar shape/length;
- [ ] medir cadência;
- [ ] identificar ordem relativa;
- [ ] identificar consumer VCL;
- [ ] cruzar binário vs Portmon;
- [ ] marcar desconhecido quando não houver prova.

Sub-blocos:
- [ ] PetrolCurve;
- [ ] GasCurve;
- [ ] RunPoint;
- [ ] CurrentBand;
- [ ] PollingPetrol;
- [ ] PollingGas;
- [ ] ACQUIRED_ZONES;
- [ ] NUM_BUF / maturidade;
- [ ] LEVELS;
- [ ] Start/Pause/enable.

### Fim
- [ ] publicar matriz original;
- [ ] revisão falsificadora independente;
- [ ] nenhum código OMEGAS alterado nesta Task.

---

## Task 2 — mapa consumer OMEGAS (#83)

### Início
Traçar:
`serial/runtime -> NativeAutoCalMonitor -> snapshot/projection -> bridge -> API JS -> cockpit`.

### Meio
- [ ] parear cada item da Task 1;
- [ ] classificar MATCH / INTENTIONAL_IMPROVEMENT / MISSING / WRONG / INCONCLUSIVE;
- [ ] registrar efeito visual/operacional.

### Fim
- [ ] lista fechada de divergências provadas;
- [ ] cada divergência tem um RED proposto;
- [ ] nenhuma correção por intuição.

---

## Task 3 — RED da curva contínua

### Início
- [ ] montar cenário de replay com cadence/ordem do Portmon;
- [ ] alimentar o maior runtime real aplicável;
- [ ] abrir AutoCal em assets reais.

### Meio
- [ ] provar visualmente se PetrolCurve/GasCurve desaparecem;
- [ ] provar AGORA com telemetria fresca;
- [ ] registrar screenshot `1280x720`;
- [ ] registrar estado/projeção que causou o resultado.

### Fim
- [ ] RED falha pelo sintoma físico relatado, não por grep.

---

## Task 4 — correção mínima AutoCal

Só inicia após Task 3.

### Início
- [ ] escolher uma hipótese causal confirmada.

### Meio
- [ ] alterar uma única autoridade/scheduler/consumer por slice;
- [ ] manter telemetria viva;
- [ ] não criar polling científico em JS;
- [ ] preservar escala física.

### Fim
- [ ] RED -> GREEN;
- [ ] screenshot/runtime demonstra curva + AGORA;
- [ ] CI canônica verde.

---

## Task 5 — gate global real-log + render (#84)

### Início
- [ ] catálogo de cenários por tela;
- [ ] classificar cada fonte pesada como `RAW_LOCAL_ONLY` ou `FIXTURE_VERSIONED`;
- [ ] extrair dos logs/EXE apenas os comandos, respostas, cadências, shapes e valores necessários;
- [ ] registrar hash/proveniência de cada fixture compacto;
- [ ] definir matriz de GitHub Actions por cenário/tela e viewport `1280x720`.

### Meio
- [ ] Dashboard/Agora;
- [ ] AutoCal;
- [ ] Learning;
- [ ] Map;
- [ ] Curve;
- [ ] OBD;
- [ ] reconnect/session.

Para cada:
- [ ] fixture derivado de log real;
- [ ] runtime/bridge real aplicável;
- [ ] render real;
- [ ] assertion visual/estado;
- [ ] screenshot + receipt como artifact;
- [ ] job GitHub Actions independente quando não houver dependência serial.

### Fim
- [ ] CI não promove PASS visual só por contrato textual;
- [ ] replay/render não depende da MMMACHINE quando o fixture já está versionado;
- [ ] falha visual bloqueia promoção mesmo que contratos estáticos estejam verdes.

---

## Task 6 — LEVELS RAW no Dashboard (#85)

### Início
- [ ] RED renderizado com LEVELS fresco ausente do Dashboard.

### Meio
- [ ] consumir autoridade existente;
- [ ] manter RAW;
- [ ] estado stale/invalid explícito;
- [ ] não remover uso válido em AutoCal.

### Fim
- [ ] Dashboard mostra LEVELS RAW junto ao contexto principal;
- [ ] 1280x720 continua legível.

---

## Task 7 — sessão única (#86)

### Início
- [ ] mapear SessionRecorder/export/persistência;
- [ ] mapear AutoCal persistence paralela.

### Meio
- [ ] provar duplicidade ou divergência;
- [ ] desenhar compatibilidade/migração mínima;
- [ ] integrar evidência AutoCal à sessão canônica.

### Fim
- [ ] um único conceito de sessão para o operador;
- [ ] persistência interna automática;
- [ ] export ZIP continua sendo export, não requisito para salvar AutoCal.

---

## Task 8 — produto e UX

### Início
- [ ] replay/render de normal, parcial, erro, stale, reconnect.

### Meio
Aplicar:
- intenção antes de subsistema;
- superfície dominante;
- normal compacto;
- problema ganha espaço;
- detalhe técnico sob demanda;
- ação principal óbvia;
- preservar contexto.

### Fim
- [ ] review 1280x720 independente;
- [ ] nenhuma aba AGORA/Referência;
- [ ] curva continua dominante.

---

## Task 9 — fechamento

### Início
- [ ] reconciliar HEAD/diff/issues/spec/WU/evidence.

### Meio
- [ ] focused tests;
- [ ] real-log replay;
- [ ] rendered gate;
- [ ] JVM/lint/fast contracts;
- [ ] independent verification.

### Fim
Relatório:
- PASS
- PARTIAL
- FAIL
- INCONCLUSIVE

E conclusão:
`READY FOR PHYSICAL TEST` ou `NOT READY FOR PHYSICAL TEST`.
