# Checklist de aceite WU-006

Fontes lidas em 2026-10-02:
- CUSTOMROM: https://app.notion.com/p/3b68ee52ac5481839046f36b482aab44
- Omega Dev 4.0: https://app.notion.com/p/3b78ee52ac548170b5c1fb69606ced21

Os blueprints são método de decisão, não template mobile a copiar. O handoff define a adaptação para multimídia landscape 1280×720 e largura 900.

## Car Info Next: adaptação vinculante

Fonte integral: https://app.notion.com/p/3b88ee52ac5481988c28db1600330335
- Área segura 1280×644; Refino sem rolagem; conferir também 900 px.
- Título 26–30 px; métricas 20–26 px; essencial ≥12 px; targets 56–68 px.
- Tokens Car Info Next substituem a paleta inicial: fundo #0B0F14, superfícies #141B24/#19222D/#202C39, borda #2B3948, texto #F6F8FB/#98A4B3, accent #5CC4FF, verde #68DBA0, amarelo #FFC25C, vermelho #FF7474.
- StatusPill, HeroValue, MetricRow, ActionChip, DisclosurePanel, EmptyState e WarningBlock: função antes de decoração; máximo três níveis de superfície; sem reconstrução contínua por telemetria.

## Tokens iniciais (histórico; substituídos pelo Car Info Next acima)
- Fundo #080c12; superfícies #101721/#161f2c/#1c2737; borda #293749.
- Texto #f5f8fc, secundário #96a6bb; navegação #745cff.
- Verde #38d39f: leitura/análise/saudável; amarelo #f7b955: rascunho/recoleta; vermelho #ff6b6b: gravação/falha.
- Espaços 4/8/12/16/24 px; raio de superfície 12 px, controles 9 px; pills arredondadas.
- Tipografia por função: título 20–22 px, corpo 14 px, status 13 px; técnico sob demanda. Targets 48 px; foco visível.
- Ícones e labels identificam intenção; estado selecionado persistente; sem animação decorativa ou progresso fabricado.

## Hierarquia do refino — contrato do §2.1

1. Base: equivalência MAP × Tpet das 18 bandas nativas por combustível; produz a proposta EQUIVALENCE, descartando outliers e limitando degraus.
2. Nossas bandas densas MAP × Tpet: consulta visual das leituras estáveis; não entram no motor.
3. Refino do refino: pares RPM × MAP com peso 0,4 a partir de 3 ms; complementam a base e nunca habilitam equivalência sozinhos.
4. Verificação por faixa: RefinementJournal, após gravação manual confirmada.

## Hierarquia e linguagem
- [ ] Agora · AutoCal · Refino · Aprender · Ajuste global · Ajuste local · Sugestões · Ferramentas.
- [ ] Uma ação primária por passo; operações perigosas com revisão e confirmação explícitas.
- [ ] Linha do tempo AutoCal domina: ECU no automático → nossos pontos → refino → verificação → estável.
- [ ] Agora usa o mesmo vocabulário do piloto; números técnicos não dominam o primeiro nível.
- [ ] MUL_ACT → Curva de equivalência; AutoMatch → Automático da ECU; SUPPORTED/BLENDED/HELD → Medido/Transição/Mantido; RESET_GAS → Recoletar GNV.
- [ ] Normalidade compacta; falha explica o que ocorreu, impacto e próximo passo.

## Estados e prova
- [ ] Toda tela: sem ECU, lendo, coletando, dados insuficientes, pronto, gravando, verificado e divergente, quando aplicável à sua operação.
- [ ] ACK sem readback válido nunca significa sucesso. Sem operação na tela, não inventar estados de gravação.
- [ ] Contexto e sessão preservados ao navegar; backend e matemática do refino preservados.
- [ ] Screenshots Chromium de cada tela/estado aplicável em 1280×720 e conferência de 900 px.
- [ ] Gate rápido → testes afetados → Android/JVM → lint → APK SHA-bound.
- [ ] Insets, toque em multimídia, sensor e rodagem continuam exigindo validação física.

## Resultado item a item na continuação (auditoria remota, 2026-10-02)

Estados: **ESTÁTICO** = verificado no source remoto; **DRAFT VISUAL** = imagem com bridge falso 1280×720 inspecionada; **PENDENTE** = aceite não produzido. Nenhum dos itens abaixo constitui teste da multimídia Android.

| Item do checklist acima, na mesma ordem | Resultado e prova atual | Falta para aceite |
|---|---|---|
| 1. Oito destinos | ESTÁTICO: `core/router.js` e `index.html` têm Agora, AutoCal, Refino, Aprender, Ajuste global, Ajuste local, Sugestões, Ferramentas | startup/volta real |
| 2. Uma ação por passo; revisão manual | ESTÁTICO: `autocal-refine.js` tem revisão, comparação de snapshot e readback; DRAFT VISUAL: PROPOSTA_PRONTA e RESTAURAR_TRECHO | Android dialog + erro/ACK/readback divergente |
| 3. Linha do tempo do piloto | DRAFT VISUAL: sete fases de Refino 1280×720 guardadas em `docs/evidence/ui-wu006/draft` | regenerar e auditar 1280×644 e 900 |
| 4. Agora vocabulário compacto | DRAFT VISUAL: `agora-720.png` | montagem de app completo e navegação com Store |
| 5. Vocabulário humano | ESTÁTICO: componentes Refino/AutoCal; “degrau que tira a linearidade da puxada”; sem promessa de sumiço | conferir todos os rótulos em tela real |
| 6. Normalidade compacta, falha explicativa | ESTÁTICO: `autocal-evidence.js`; draft antigo “normal” tinha HIGH indevido | regenerar fixtures corrigidas em `capture-wu006.cjs`; inspecionar as duas imagens |
| 7. Estados aplicáveis | DRAFT VISUAL: SEM_ECU, ECU_TRABALHANDO, COLETANDO_NOSSOS, PROPOSTA_PRONTA, VERIFICANDO, RESTAURAR_TRECHO, ESTAVEL | ler/gravar, falha e divergência, review aberto; viewport compacto |
| 8. ACK não equivale a sucesso | ESTÁTICO: `autocal-refine.js` condiciona “Gravada e conferida” a BATCH_CONFIRMED + readbackValid | integração Android e testes negativos atuais |
| 9. Contexto preservado | ESTÁTICO: `refinement-screen.js` controlador persistente; testes Node de histórico no handoff | teste integrado ida/volta com operação ativa |
| 10. Screenshot 1280×720 e 900 | DRAFT VISUAL: 11 PNGs 1280×720, não E2E; script agora nomeia por dimensão sem sobregravar | 1280×720 final, 1280×644, 900, 1024×600, screenshots inspecionados e bounding boxes |
| 11. Gate→JVM→lint→APK | Históricos do handoff são de SHAs anteriores; workflow `omegas-preapk-build.yml` contém etapas exigidas | gate/paridade e Gradle no SHA exato, digest/identidade do APK |
| 12. Insets, toque, sensor, rodagem | PENDENTE físico. Sensor real permanece proxy explícito | carro/ECU e perfil nativo comprovado |
| Car Info Next: cores, ≥12px, targets 56–68px, Refino sem rolagem | DRAFT VISUAL: paleta, sete fases 1280×720 e botão primário 56px registrados no handoff | imagens novas 644/900, clipping/overlap, foco, todos os targets e textos |

**Achado de release fora do aspecto visual:** `LevelSensorSnapshot.read()` deve ganhar regressão RED→GREEN para impedir que captura USB antiga republique seu estado após troca de sessão. Detalhes em `docs/evidence/WU-006-FINAL-REVIEW-20261002.md`.

Os checkboxes originais permanecem desmarcados onde o aceite completo não foi demonstrado. Captura de componentes com bridge falso não é teste E2E. `PHYSICAL_VALIDATION_CLAIMED=false`.

PHYSICAL_VALIDATION_CLAIMED=false

CSS removido nesta fatia (classes sem referência em HTML/JS):
- `.obd-quick`
- `.obd-quick>div`
- `.obd-quick>div+div`
- `.obd-quick b`
- `.obd-quick>span`
- `/* OBD */
.obd-primary`
- `.obd-connection-strip`
- `.obd-note`
- `.obd-actions`
- `.obd-mode-buttons`
- `.obd-mode-buttons button`
- `.obd-runtime-controls`
- `.obd-live-trace`
- `.obd-live-trace>div`
- `.obd-live-trace b`
- `.obd-map-toolbar`
- `.obd-map-tabs`
- `.obd-map-tabs button`
- `.obd-map-summary`
- `.obd-independent-map-wrap`
- `.obd-map-y-label`
- `.obd-map-x-label`
- `.obd-map-grid`
- `.obd-map-cell`
- `.obd-map-cell b`
- `.obd-map-cell small`
- `.obd-map-cell[data-state="positive"]`
- `.obd-map-cell[data-state="negative"]`
- `.obd-map-legend`
- `.obd-cell-detail`
- `.obd-cell-facts`
- `.obd-independent-status`
