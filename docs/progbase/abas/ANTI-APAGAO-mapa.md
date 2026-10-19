# Anti-apagão (anti-stallo): mapa de conexões

Um só lugar liga: **tela do original → bytes → app → teste → evidência do log**. Marcas: OBS-FIO (visto no fio), OBS-COD (provado no código do ProgBase), INFERIDO.

```mermaid
flowchart LR
  subgraph ORIGINAL[Tela do original - aba F9 Injetores]
    CK[CHECKANTISTALLO - Enable un-stalling strategy]
    L0[LabelAntiStallo0 - Max RPM to enable: rpm] 
    L1[LabelAntiStallo1 - Delta RPM to enable: rpm/ciclo]
    L2[LabelAntiStallo2 - Delay to exit gas mode: ms]
    L3[LabelAntiStallo3 - Hysteresis: rpm/ciclo]
    L4[LabelAntiStallo4 - Delta RPM to disenable: rpm/ciclo]
  end
  subgraph FIO[Bytes - OBS-FIO]
    F3[FLAG_CONF3 serial 250: palavra0 bit 0x20 = caixa]
    V307[ANTI_STALLO vetor 307: 5 x u16 = 1600 100 1500 2 20]
    Q35[35 FA 00 w0 w1 cs - liga/desliga]
  end
  subgraph APP[App - aba Ferramentas]
    SW[Interruptor 1 toque, sem pergunta]
    VAL[Valores: numero principal + extras experimentais, botoes -/+]
    CF[Como funciona: quando age e o que NAO e]
  end
  subgraph EVID[Evidencia no log novo - OBS-FIO]
    LOG[Ligado: pos-corte min 680 rpm, 0 vezes abaixo de 600. Desligado: min 447, 26 vezes]
  end
  CUT[Corte de injecao - cartao Saida do corte: NUMERO_INJ_BENZINA_CUTOFF 0x6A, GIRI_TEMPO_CUTOFF 0x32]
  CK --> F3 --> Q35 --> SW
  L0 --> V307
  L1 --> V307
  L2 --> V307
  L3 --> V307
  L4 --> V307
  V307 --> VAL
  VAL --> CF
  CUT -. outra funcao; os dois se encontram: apos o corte a rotacao cai .-> LOG
  SW --> LOG
```

## Resposta curta (para o dono)
- **Age sempre no corte?** Não. É função própria. Só entra quando a rotação está **abaixo** da "Rotação máxima para agir" **e** cai **mais rápido** que "Queda para agir" (rpm por ciclo). Embreagem pisada, parada, ré, quebra-molas, redução, ou o afundo depois de um corte: todos produzem essa queda rápida; o anti-apagão olha só a queda, não a causa (INFERIDO do rótulo e do log).
- **O corte de injeção é afetado?** Os ajustes do corte (Saída do corte) continuam separados e não são alterados por estes números. No log, o anti-apagão segurou a rotação *depois* dos cortes (OBS-FIO).
- **Unidades:** campos 1, 3 e 4 são **rpm por ciclo** no original (UNITARPMCICLOMOTORE), não rpm simples (OBS-COD, i18n 970).
- **Ainda INFERIDO:** efeito exato de "Espera para sair do gás" (campo 2) e dos extras; o original só mostra o campo 0 (captura F9).

Testes: `tests/ui/anti-apagao-valores-didatico.test.cjs`, `tests/ui/anti-apagao-toque-real.test.cjs`. Código: `app/src/main/assets/ui/core/anti-apagao-valores.js`, `components/advanced.js`.
