# Recon map: OMEGAS Platina V8 (Android, WebView 1280×720 + Kotlin nativo)

Scope: o app inteiro do dono como está na branch `ccr-7eed5bd3-251im7` — as 5 abas (AutoCal, Refino, Ajuste local, Ajuste global, Ferramentas), a barra ao vivo e a ponte nativa. Alvo: calibração de GNV na ECU MP48/AEB.
For: o próprio dono (produto dele). Clean-room não se aplica: a fonte é o código dele.
Date: 2026-10-15
Método: leitura do código do repositório (`index.html`, `router.js`, `native-api.js`, `HubJavascriptBridge.kt`, `AutoCalJavascriptBridge.kt`, `drawers.js`), `PROJECT.md`, `STATUS.md`, `AGENTS.md`. As 5 abas foram renderizadas em 1280×720 com `tools/ci/capturar_abas.cjs` (ponte falsa, sem ECU: **dados de demonstração**, "Simulação de interface · nenhuma escrita real"), em `screens/01-autocal.png` … `05-tools.png`. Nenhum fluxo foi executado nem testado. Estados marcados "lido no código" não foram vistos em tela.

## Sources

| # | source | caminho | notes |
| --- | --- | --- | --- |
| 1 | contrato do projeto | `AGENTS.md`, `PROJECT.md`, `STATUS.md` | regras, invariantes, decisões de campo do dono |
| 2 | shell da UI | `app/src/main/assets/ui/index.html`, `app.js`, `core/router.js` | 5 rotas, barra ao vivo, camadas de operação |
| 3 | telas | `ui/screens/{autocal-cockpit,autocal-original,refino,map,curve}.js`, `ui/components/{drawers,live-bar}.js` | |
| 4 | ponte nativa | `ui/core/native-api.js`, `web/HubJavascriptBridge.kt`, `autocal/AutoCalJavascriptBridge.kt` | ~100 métodos `@JavascriptInterface` |
| 5 | protocolo | repo privado `PROTOCOLO-LANDI` (índice `00-LEIA-ME.md`) | não lido nesta rodada |
| 6 | sessões reais | `tests/fixtures/sessoes-uteis/` | corpus compacto |

## Core loop

Ler a ECU → deixar o AutoCal nativo da ECU aprender (gasolina × GNV) → o Refino mostra onde o GNV não casa e propõe correção → o dono grava no Mapa K ou na Curva K com um toque, ACK e leitura de volta.

## Screens

| ID | screen | route / how to reach | purpose | key components | states seen |
| --- | --- | --- | --- | --- | --- |
| S01 | AutoCal | aba 01, `data-route=autocal` (padrão ao abrir) — `screens/01-autocal.png` | acompanhar a aquisição da ECU em tempo real (zonas, `#n/d` por faixa, eixos, curva K com teto 1,8, bolinhas Gasolina/GNV/Ajustes, "Ajustes automáticos 2/3", chip "Zona Z1") | gráfico do AutoCal original, medidor "Aquisição", legenda, **4 botões: Pausar aprendizado, Reaprender GNV (apaga o atual), Reaprender gasolina (apaga o atual), Ações avançadas**, dica "Toque em um ponto" | ECU offline, coletando, completa, cota 3/3, aquisição religada (lido no código) |
| S02 | Refino | aba 02 — `screens/02-refino.png` | fases do refino, trechos que travam, proposta de ajuste | mesmo gráfico do AutoCal, chip "Curva pronta", frase "12 pontos para ajustar", relato de engasgos ("engasgou 6 vezes no GNV…"), marcas Apagou/Quase apagou, botões **Ver alterações** e **Desfazer última gravação** (+ diálogo S08, interruptor "Refinar por minha conta") | curva pronta com engasgos (visto); automático/manual (lido no código) |
| S03 | Ajuste local (Mapa K) | aba 03 — `screens/03-map.png` | corrigir regiões do Mapa K | grade `mapGrid`, seleção de células, modo percentual/somar, botões −5/−1/+1/+5, "Gravar na ECU", "Curva K → Mapa K" | lido (visto: grade 12 faixas de injeção × 12 RPM, 13ª linha só leitura, "Mapa lido da ECU"); não lido, seleção, escrevendo, resultado (lido no código) |
| S04 | Ajuste global (Curva K) | aba 04 — `screens/04-curve.png` | editar a curva K ponto a ponto | `curveChart` com arrasto de nó, painel "Ponto ativo", pontos preparados, rodapé (Reler ECU, Guardar backup, Backups, Restaurar, "Curva K toda em 1,0") | lida (visto: "ECU confirmada · 30 pontos", ponto 1 = 1,02, Backups (0), Restaurar desabilitado); ponto preparado, escrevendo, resultado (lido no código) |
| S05 | Ferramentas | aba 05 — `screens/05-tools.png` | backup, sessões e saúde do app | 3 cartões: SAÚDE DO APP (Bateria, Telemetria flutuante, "Log do sistema"), REGISTRO DE SESSÃO (Registrar sessão/Parar, Sessões, "Tamanho e retenção"), APRENDIZADO (48 regiões gasolina/GNV); rodapé: Exportar backup completo, Exportar/Importar aprendizado | visto: serviço OK, registro parado, 0 sessões, overlay desativado |
| S06 | Barra ao vivo | topo, em todas as abas | tensão da ECU (alerta 14,1 V, escala provisória), MAP, Inj. gasolina, RPM | 4 `live-cell` | desconhecido "—", normal, alerta de tensão |
| S07 | Camadas de operação | sobre S03/S04 | escrita em andamento e resultado | `operation-layer writing/result`, `operation-result` | escrevendo, ok, erro |
| S08 | Diálogo "O que vai mudar" | dentro do Refino | revisar antes → depois da proposta | `refino-card` | aberto, confirmado, cancelado |
| S09 | Barra de navegação + status | **embaixo**, sempre (no HTML é `side-rail`, no 1280×720 aparece na base) | navegar entre as 5 abas; marca OMEGAS · demo à esquerda; "ECU online" + combustível à direita | `side-rail`, `globalEcu`, `globalFuel` | online (visto), offline |
| S10 | Aviso flutuante (toast) | `alertToast` | alertas curtos | toast com fechar | oculto, visível |

Não-telas relevantes: balão de telemetria flutuante (`TelemetryOverlayController`), notificação do serviço em primeiro plano.

## Flows

```
F01 Acompanhar a aquisição do AutoCal
    S09 -> S01 (ECU aprende; leituras em tempo real, sem "antes × depois")
    happy path clicks: 0–1
    edge: ECU offline; cota de AutoMatch 3/3 mas aquisição continua; religar aquisição que caiu desfaz "ECU terminou"

F02 Resetar o GNV
    S01 -> ação nativa (modo 2) -> backup completo -> reset -> readback -> AUTO_CAL_ENABLE religado até = 1
    happy path clicks: 1 (um toque, à mão)
    edge: timeout/NAK ainda exige religar AUTO_CAL_ENABLE; RESET_ALL não é exposto

F03 Refinar o GNV e gravar no Mapa K
    S02 -> trecho travado -> S08 (O que vai mudar) -> gravar no Mapa K sem sair do Refino -> ACK + readback
    edge: "Refinar por minha conta" (dono grava quando quer e manda a ECU reler o GNV); nunca grava sozinho

F04 Ajuste local manual do Mapa K
    S03 (lê ao abrir) -> selecionar células -> modo + valor -> "Gravar na ECU" -> S07 escrevendo -> resultado
    edge: piso de 100; Mapa K não lido; falha de ACK

F05 Editar a Curva K
    S04 -> tocar/arrastar ponto -> "Preparar este ponto" -> revisar antes → depois -> "Gravar na ECU" -> S07
    edge: teto de escrita 1,8; leitura vai até o limite do protocolo; soltar só prepara

F06 Backup e restauração da Curva K
    S04 -> "Guardar backup" -> "Backups" -> "Restaurar backup" (respeita teto 1,8) ; "Curva K toda em 1,0" avisa só se longe de 1,0

F07 Curva K → Mapa K
    S03 -> botão "Curva K → Mapa K" -> relê mapa e curva -> propõe produto refatorado -> grava mapa, depois curva neutra, cada etapa com ACK + readback

F08 Exportar/importar dados
    S05 -> Exportar backup completo | Exportar aprendizado (.omegas) | Importar aprendizado (confere o arquivo, nunca grava na ECU)

F09 Registrar sessão
    S05 -> REGISTRO DE SESSÃO -> iniciar/parar -> exportar sessão
```

## Components

| component | variants | states | used on |
| --- | --- | --- | --- |
| Botão | primary, secondary, quiet, wide, nudge | default, disabled | S03–S05 |
| Seleção de célula (grade) | célula, linha, tudo | selecionada, ativa, sem leitura | S03 |
| Gráfico de nós (Curva K) | nó, linha atual, linha proposta | ativo, preparado, arrastando | S04 |
| Gráfico AutoCal | zonas 0–4/5–8/9–12/13–17, linha de teto 1,8, K de 0,4 a 2,0 | coletando, completa | S01, S02 |
| Chip de combustível | gasolina (vermelho), GNV (verde) | — | S01, S02, S09 |
| Bolinhas de estado | verde completa, azul coletando, laranja falta | uma por AutoMatch | S01 |
| Live cell | tensão, MAP, inj., RPM | desconhecido, normal, alerta | S06 |
| Camada de operação | escrevendo, resultado | ok, aviso, erro | S03, S04 |
| Cartão de ferramenta | serviço, bateria, overlay, sessão, aprendizado | | S05 |
| Estado vazio | `empty-state` | — | S03, S04 |
| Toast | alerta | visível/oculto | todas |

## Inferred data model

```
EcuLink        usb device, estado online, combustível atual (gasolina|GNV), build/release identity
               evidence: S09, getStatus, connectUsb, listUsbDevices          confidence: high
AutoCalSnapshot  zonas 0–17, n/d por zona, série por combustível, flags de AutoMatch (NUM_AUTOMATCH_EXECUTED/MAX), AUTO_CAL_ENABLE
               evidence: S01, AutoCalSnapshot.kt, STATUS.md                   confidence: high
MapK           grade de células (linha × coluna), piso 100, escala raw
               evidence: S03, startKMapRead, writeMap                         confidence: high
CurveK         pontos (index, fator), teto de escrita 1,8, leitura até limite do protocolo
               evidence: S04, readKFactorCurve, KFactorProtocol               confidence: high
CurveBackup    arquivo por rótulo, data, pontos
               evidence: S04 rodapé, startCurveBackup, curveBackups           confidence: medium
WriteRecord    alvo (mapa|curva|reset), valores antes/depois, ACK, readback, motivo
               evidence: S07, getKWriteHistory, getKFactorHistory             confidence: medium
RefinementState  fase, trechos travados (StallWatch), proposta, modo manual
               evidence: S02, getRefinementPhase, getStalls                   confidence: medium
Session        id, motivo, telemetria CSV, eventos de calibração (jsonl)
               evidence: S05, sessoes-uteis/, SessionRecorder                 confidence: high
LearningArchive  arquivo .omegas (aprendizado, mapas, tolerâncias, temperatura mínima da água)
               evidence: S05, exportLearning, getLearningMaps                 confidence: medium
Telemetry      tensão (byte 19 ÷ 16, provisório), MAP, injeção gasolina, RPM, GPS opcional
               evidence: S06, getLiveTelemetry                                confidence: medium (escala da tensão é palpite)
Settings       overlay flutuante, bateria, GPS, LAN, capacidade de GNV, reabastecimentos
               evidence: S05, setGnvSettings, registerRefuel                  confidence: low (tela não vista)
OmegasLink     pareamento por código, main/secundário, sincronização
               evidence: OmegasLinkManager, bridge; sem tela nas 5 abas       confidence: low
```

Relationships: EcuLink 1-n Session; EcuLink 1-1 MapK; EcuLink 1-1 CurveK; CurveK 1-n CurveBackup; MapK/CurveK 1-n WriteRecord; Session 1-n eventos; LearningArchive n-1 app.

## Feature matrix

Ver `features.csv`. Must: 21, should: 9, could: 4, skip: 4 (38 funções).

## Out of scope (cannot or should not be cloned)

- A ECU MP48/AEB e o firmware: o app depende do hardware real; só emulação por observação.
- O ProgBase.exe original (Delphi): é referência de comportamento, não de interface.
- Validação física (carro, ECU real): só com o dono.
- Escrita automática em Mapa K ou Curva K: proibida por invariante.
- `RESET_ALL` exposto na UI: proibido por invariante.

## Size

Screens 10 (5 abas + 5 camadas/componentes), flows 9, entities 12. Hard parts: (1) protocolo serial da ECU e arbitragem USB (`Mp48SerialScheduler`, backpressure); (2) gravar com ACK + readback e `reset-nunca-pausa-aprendizado`; (3) AutoCal/Refino em tempo real sem relógio, só por evidência. Size: **XL** para reproduzir do zero; o app já existe, então este mapa serve de índice e de checklist de paridade.

## Divergências achadas ao renderizar

- **"Pausar aprendizado" existe no AutoCal** (`autocal-cockpit.js:1129`, ação `DISABLE_AUTO_CAL`, vira "Retomar aprendizado"), mas o AGENTS.md diz "Sem botão de pausar a ECU (desliga a curva)". Pode ser regra antiga ou botão que sobrou; **decisão do dono**. Na matriz está como `unknown`.
- Minha primeira versão do mapa dizia "menu lateral"; a imagem mostra barra inferior (corrigido em S09).

## Lacunas desta rodada (UNKNOWN)

- As capturas usam ponte falsa e dados de demonstração; não provam comportamento com ECU real. Estados de erro, escrita em andamento, diálogo "O que vai mudar" e "Ações avançadas" não foram capturados (`tools/ci/capturar_estados.cjs` pode gerar).
- `PROTOCOLO-LANDI` não foi lido (exige `add_repo`).
- Telas de configuração (GNV, Link, LAN, GPS) existem na ponte, mas não achei onde aparecem na UI.
