# OMEGAS — Brainstorm de produto (2026-10-02)

**Pergunta central:** como fazer o tempo de injeção do GNV igualar o da gasolina original, partindo da calibração automática da própria ECU, e entregar isso com uma experiência premium e sem carga cognitiva?

**Ordem definida pelo proprietário:** primeiro funcionar perfeitamente para ele; só depois refinar para vender.

## 1. AutoCal de ponta a ponta (próximas WorkUnits, em ordem)

1. **Laço pós-gravação.** Gravar a curva refinada, recoletar só o GNV, medir o resíduo (razão por faixa) e então aceitar ou restaurar. Hoje o app só recomenda recoletar; o laço deve virar um fluxo guiado de 1 toque, com critério objetivo: resíduo menor que 2% e nenhum tranco detectado.
2. **Detector de tranco ao vivo.** Medir a oscilação de `petrol_ms` em GNV com RPM e MAP estáveis (a assinatura medida na sessão de 01/10 às 17:19) e mostrar na tela Agora em qual faixa de ms acontece. Reaproveitar a janela do `MotorSampleAnalyzer`.
3. **Coaching de coleta.** Mostrar em tempo real qual banda de MAP falta em gasolina e em GNV ("segure ~0,6 bar, 2000 rpm"), a partir de `AutoCalAcquisition` e `NativeAutoCalProgression`.
4. **Readquirir só a banda suspeita.** Quando o ajuste isotônico descarta uma banda (como a banda 9 em 01/10), propor apagar e recoletar só ela. Isso depende de portar o `AutoCalPointDeleteProtocol` da linha Platina.
5. **Resíduo contínuo da Curva K.** Usar as lanes `PETROL_REFERENCE` × `CNG_PETROL_OBSERVED` da `EquivalenceSurface` para validar a curva fora das 18 bandas nativas, e projetar o resíduo nos 30 nós como um semáforo.
6. **Mapa K (Ajuste local).** Fazer o resíduo depois da Curva K usar o mesmo eixo (`PETR_INJ_TBP`) e uma suavização espacial do mesmo tipo (Whittaker 2D, com a mesma trava de elasticidade).
7. **Desligar o AutoMatch nativo como fonte de verdade.** A evidência de 01/10 mostrou ganho total sobre bandas com 1 ou 2 amostras. Documentar a recomendação de usar a coleta nativa e a equivalência refinada do app. Não alterar `MAX_AUTOMATCH` sem uma decisão explícita.
8. **Mesclar `fix/autocal-previous-gas-readonly-20261002`** (que corrige o GAS_PREV herdando contadores) e portar da linha Platina, em PRs pequenos:
   - readback com testemunhas por ação e settle de 1 s;
   - escrita de Curva K e Mapa K em lote único;
   - export de sessão incremental;
   - epoch e vida útil de referência por combustível.

## 2. Experiência (método CUSTOMROM / Omega Dev)

- Navegação por intenção: **Agora · AutoCal · Aprender · Ajuste global · Ajuste local · Sugestões · Ferramentas**. Esta WU já aplicou isso; Predictor e OBD estão fora.
- Linguagem humana: "Curva de equivalência" em vez de MUL_ACT, "Recoletar GNV" em vez de RESET_GAS. Os termos técnicos ficam em "Detalhes técnicos".
- Normalidade compacta e problema com espaço; uma ação primária por tela; reversibilidade visível (Restaurar); semântica de cor fixa (verde = leitura, amarelo = reversível, vermelho = ECU).
- Onboarding na primeira conexão: identificar a ECU, fazer backup automático de Curva K e Mapa K, e mostrar um checklist (motor quente, pressão do gás).
- Histórico de calibrações com diff e restauração em 1 toque. Relatório PDF para o cliente do instalador.

## 3. Peso e desempenho (vitórias rápidas)

- Dashboard: re-renderizar só quando a assinatura muda (`app.js`, hoje re-renderiza a cada 1 s).
- Tirar `api.obd()` do status de 1 s e o OBD do snapshot de 3 s.
- `publishLearningState`: hoje copia o snapshot inteiro (string → parse) a cada frame. Trocar por snapshot tipado e serializar só quando a UI pedir.
- Desligar o `adaptiveShadowPipeline` por frame (não tem consumidor de produto).
- Trocar `AppCompatActivity` por `ComponentActivity`: remove o appcompat, com ganho estimado de 0,5 a 1 MB no APK.

## 4. Repositório

**Seguro apagar** (em um PR de limpeza próprio):
- 8 workflows `atlas-*`, que são provas forenses de SHAs fixos da OmegasAtlas;
- `ci/`, `tools/collect-122a*.bat`, `lab/contracts`, `docs/superpowers`;
- os freezes 068x e seus testes;
- os CSS `styles-expansion*`/`styles-obd-evidence`, que nunca são carregados;
- os `portmon-*.js`, que vão para `tests/`;
- cerca de 38 arquivos Kotlin sem referência (3,6 mil linhas).

**Precisa de decisão:**
- apagar de vez o código do Predictor (cerca de 8,8 mil linhas com testes) e do OBD;
- converter cerca de 72 testes Python que só leem o código Kotlin como texto em testes JVM de comportamento.

**Branches:** 34 no remoto, e a `OMEGAS-SPEED` **não deve ser mesclada**:
- é outra linhagem (merge-base vazio, 169 conflitos de arquivo inteiro);
- o "speed" é benchmark Python offline, sem efeito no APK.
Política sugerida: arquivar como tag e apagar `verify/*`/`build/*` após 7 dias e `work/*` após 14 dias sem PR.

## 5. Comercial (depois de comprovado no carro do proprietário)

- Identidade definitiva: applicationId, nome e ícone (hoje `com.omegas.v7.test` / "OMEGAS V8 TEST").
- Keystore de release e Play Console. O manifesto exige USB host.
- Assinatura via Play Billing com verificação offline e período de carência, porque no carro muitas vezes não há rede. Licença por instalador ou por veículo.
- Crash reporting opt-in, extração de textos para i18n (pt-BR, depois es-AR e es-CO), termos de uso e responsabilidade sobre a gravação na ECU.
- Diferencial vendável: "a ECU calibra, o OMEGAS refina". Mostrar o antes e depois mensurável (linearidade da puxada, fidelidade à medição, resíduo) e um relatório exportável.
