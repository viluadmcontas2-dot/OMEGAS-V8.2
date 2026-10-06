# AutoCal — coerência operacional (2026-10-06)

Relato físico do proprietário: gasolina reiniciada sem intenção clara, zonas escondidas, GNV anunciado indevidamente e AGORA aparentando congelamento; Mais opções sem estado visível.

Correções limitadas à HMI, base de produto bf744566:
- Vetores vazios/parciais da projeção são desconhecidos, não quatro zonas faltantes; não recorre a flags antigas quando a projeção publicou a cobertura.
- Cobertura gasolina e GNV sempre na superfície principal: ✓ adquirido, FALTA pendente, — não confirmado. Sem navegar em menus.
- Frase usa combustível atual e aquisição habilitada. Durante comando, informa alvo e conferência; RESET não troca combustível nem habilita aquisição sozinho.
- Espera de curva respeita petrolReferencePending e gasReferencePending depois de novos contadores; não presume GNV.
- Gráfico de época utiliza a mesma camada viva, escala física e EaseCursor da telemetria rápida. Sem novo polling/timer.
- Mais opções tem destaque ao abrir, aria-expanded, indicação Fechar e botão explícito. Histórico permanece secundário.
- Reaquisição usa o mesmo construtor de gráfico, eixos, grade, zonas e estilos da comparação normal. Revisões iguais preservam SVG/cursor; sem pontos novos, a última escala permanece como régua, sem reapresentar curvas invalidadas.
- Prova visual adicional: normal→reset gasolina/GNV→reaquisição→referência nova, com eixos legíveis, cursor em movimento e identidade da tela/SVG preservada nas atualizações sem mudança de geometria. Cenário sintético da máscara sobre vetores de captura real; não prova USB físico.

Protocolos, comandos, leitores/escritores nativos e algoritmos de calibração preservados. Testes novos reproduzem desconhecido, combustível, comando em conferência, desconexão, fechamento do painel, cursor durante reaquisição e normal→reset gasolina→referência nova sem sessão/hash novos.

Prova local: contratos/modelo/mini-DOM. Browser local indisponível (Chromium Playwright não instalado); layout real 1280×720 e área reduzida executados na CI, além do emulador Android pelo fluxo existente. Validação USB/ECU física depende do proprietário.

Achado adjacente, não alterado nesta fatia: memo acquisitionMemo de NativeAutoCalMonitor depende do snapshot e de comparisonAllowed, podendo conservar aquisição antiga quando só a máscara da época muda. Não é fonte da cobertura primária desta HMI; requer regressão nativa específica antes de alterar.

### Comandos fora dos cartões (direcionamento final do dono)
Resetar Curva K e Ver sessões ficam na barra principal do AutoCal. Sessões abre a rota própria; o exportador nativo foi preservado em uma barra antes da lista, com seleção persistente durante atualizações. Reiniciar medições GNV e Leitura da ECU ficam na barra principal do Refino. Os painéis de detalhes explicam os estados e não escondem esses comandos. A prova no browser verifica o alcance dos comandos adicionais e mantém o piso de altura do gráfico.
