# Grafo de PRODUÇÃO (só `app/`)

Gerado por `python3 -B tools/grafo_producao.py` (determinístico, sem LLM). Não editar à mão. Dados: `docs/grafo/producao.json`.
O grafo de PESQUISA (ProgBase/evidências) é outro: `tools/grafo_conhecimento.py`. Ligação por heurística de texto (nome citado).

Nós: aba 9, ev 77, kt 8, ponte 110, teste 374, ui 30. Arestas: chama 105, cobre 629, exibe 31, sustenta 41, usa 14.

## (a) Por aba

| Aba (rota) | O que faz hoje | UI | Pontes | Testes | Evid. |
|---|---|---|---|---|---|
| Agora (`-`) | Sem aba própria: só a barra viva (tensão, MAP, injeção, RPM) no topo. | 1 | 0 | 8 | 0 |
| Mapa K (`map`) | Grade 12x12 do Mapa K, edição e correção de células, região de apagão. | 5 | 3 | 26 | 1 |
| Curva K (`curve`) | Curva K: edição, suavização, conversão em mapa, gravação com leitura de volta. | 2 | 3 | 18 | 0 |
| AutoCal (`autocal`) | AutoCal: calibração automática, pontos por zona, apagar fora da curva. | 5 | 27 | 51 | 1 |
| Ajuste GNV (`refino`) | Ajuste GNV (rota "refino"): referência de gasolina, recomendações do refino. | 2 | 2 | 16 | 0 |
| Sessões (`sessions`) | Sessões gravadas, exportação em ZIP, resumo. | 1 | 9 | 12 | 0 |
| Ferramentas (`tools`) | Ferramentas: cartões do motor, bits da ECU, anti-apagão, marcha lenta. | 7 | 21 | 28 | 12 |
| Diagnóstico (`-`) | Sem aba própria: estados na barra e cartões de Ferramentas. | 0 | 0 | 0 | 0 |

Pontes e evidências por aba (amostra):
- **Mapa K**: startRead, previewMapAdjustment, startCurveRead; EV-BINS-001
- **Curva K**: startRead, startCurveBackup, startCurveRead; -
- **AutoCal**: cancelRead, clearNativeActionPreparation, executeNativeAction, exportAutoCalSession, getEquivalence, getIdentity …; EV-BINS-001
- **Ajuste GNV**: setRefinementManual, startCurveRead; -
- **Sessões**: getAutoRefineMode, getBufferWriteProbeStatus, setAutoRefineMode, startBufferWriteProbe, exportData, exportLogs …; -
- **Ferramentas**: applyAntiStalloFlag, applyAntiStalloValues, cancelAntiStalloFlag, cancelAntiStalloValues, confirmAntiStalloFlag, confirmAntiStalloValues …; EV-ANTIAPAGAO-003, EV-ANTIAPAGAO-004, EV-ANTIAPAGAO-008 …

## (b) IMPLEMENTADO × FALTA

**IMPLEMENTADO:** 110 pontes, 30 arquivos de UI, 374 testes. Sem rota no app (contrato AGENTS regra 11): Agora, Diagnóstico.
Lacunas do CATALOGO já resolvidas: #11.

**FALTA — lacunas do original (CATALOGO, 13):**
- #1 Quando o gás entra (F1): tipo de troca, rpm mínimo 1200, água 40 °C
- #2 Volta automática à gasolina por pressão baixa (F4): tempo 0 s, valor 1
- #3 Diagnóstico (F3): 14 verificações, estado OK/erro, ação e caixa por li
- #4 Display vivo (F2) e Gráfico: pressão do gás, Press.diff., T.gás, T.águ
- #5 Injetores (F9) com números: abertura mínima 2,5 ms, extra 0,1 ms, avan
- #6 Nível do gás (F4): 7 tipos de sensor, referências Reserva/1/4/2/4/3/4 
- #7 AutoCal pergunta "água em 80-90 °C?" antes de iniciar (11-54-23)
- #8 Tempos mortos (F7 Times): gasolina 0,6 ms; extra 0 ms
- #9 Mapa de contribuição de gasolina (F8): 12 x 12 em % do tempo de gasoli
- #10 Cadastro do carro (F1/F2/F6): combustível, cilindrada, nº de cilindros
- #12 Anti-apagão: original mostra 1 número (1600 rpm)
- #13 F6 Salvar / F7 Carregar configuração, F8 Ecu programming, F9 Update sc
- #14 Lambda (F2) e lubrificante

**FALTA — pendências abertas (PENDENTE.md, 34; 12 primeiras):**
- Sugestão ADAPTATIVA do anti-apagão, recalculada por evento (fim de sessão) com as sessões 
- Semântica dos 4 extras do anti-apagão e unidade de "Queda de rotação" (rpm por ciclo = qua
- Separar apagão de motor desligado nos logs (17 quedas ambíguas).
- Provar no fio o quadro 37 33 0C 01 ... do anti-apagão (maior risco): pedir ao dono UMA cap
- CORREÇÃO DE ESCOPO do dono: o cartão Anti-apagão deve ter INTERRUPTOR + UM número ("Rotaçã
- Exportação de sessões em um toque, abrindo direto o resumo, com nome do carro e data (o ZI
- Copy para leigo: revisar todos os erros e avisos e os três tamanhos (1280x720, 1024x600, 8
- Validar integração Android e suíte completa na CI do SHA enviado. Nenhum novo APK sem orde
- APK na pasta OMEGAS do Drive: o conector não sobe 4,9 MB; o dono arrasta ou libera gh rele
- Interface leiga na multimídia (1280x720, 1024x600, 800x480): estilo da tela AutoCal, sem j
- AutoCal, Refino, Ajuste local e Ajuste global ainda dizem "ECU", "RPM" e "MAP" (o teste li
- Bug "Ferramentas vazia" no WebView real: a causa provável (carga preguiçosa) foi removida 

**FALTA — evidências DESCONHECIDO sem uso (12):** EV-ANTIAPAGAO-011, EV-ANTIAPAGAO-014, EV-ANTIAPAGAO-020, EV-ANTIAPAGAO-022, EV-ANTIAPAGAO-023, EV-BINS-005, EV-BINS-006, EV-BINS-008, EV-COBERTURA-002, EV-TRANSPORTE-010, EV-VOLTAGASOLINA-005, EV-VOLTAGASOLINA-006

## (c) Elos quebrados

ESSENCIAIS (a CI falha se houver; critério no cabeçalho de `tools/grafo_producao.py`):
- E1_ui_chama_ponte_inexistente: 0
- E2_evidencia_aponta_arquivo_inexistente: 0
- E3_ponte_de_escrita_chamada_sem_teste: 0

INFORMATIVOS (só relatam):
- ponte_sem_teste: 28 — OmegasAutoCal.getRefinedAnalysis, OmegasAutoCal.getRefinementPhase, OmegasAutoCal.getStalls, OmegasAutoCal.setRefinementManual, OmegasNative.getEngineMetrics, OmegasNative.getKFactorHistory …
- ponte_sem_chamador: 31 — OmegasNative.getEngineMetrics, OmegasNative.getKFactorHistory, OmegasNative.getKFactorStatus, OmegasNative.getKWriteHistory, OmegasNative.getKWriteStatus, OmegasNative.getLearningCheckpointStatus …
- ui_de_aba_sem_ponte: 11 — live-bar.js, advanced-flags-data.js, cartoes-motor.js, curve-smoothing.js, curve-to-map.js, gasoline-reference.js …
- evidencia_orfa: 48 — EV-ANTIAPAGAO-011, EV-ANTIAPAGAO-014, EV-ANTIAPAGAO-019, EV-ANTIAPAGAO-020, EV-ANTIAPAGAO-022, EV-ANTIAPAGAO-023 …
