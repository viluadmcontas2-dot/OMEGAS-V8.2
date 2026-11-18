# OMEGAS 8.2 — Walkthroughs operacionais (OP-17, proposta sem alteração de UI)

> Data 2026-11-18. Modo Intuitive Software Design IMPROVE + User-Task Walkthrough + Decision-Support Design. Evidência: código da branch PR #195 e capturas Chromium CI #2053 (SHA 6a8fa2b); não houve teste com usuário nem ECU real. Escopo: arquitetura e fluxos, não redesign aprovado.

## 1) Primeiro uso / cabo desconectado
Pessoa: dono conduzindo diagnóstico, quer obter leituras antes de agir. Ponto de entrada: abertura em AutoCal, sem ter conhecimento de rotas internas.
| Fase | Conhecimento e sinal visível hoje | Previsão/ação | Resultado provado ou lacuna |
|---|---|---|---|
| Orientar | Barra da central mostra Sem central e “—” para sensor ausente no modelo de estado; leitura não deve ser zero | Identificar que não há ECU pronta | OBS-COD; instrução única “conecte o cabo” segue pendente no PENDENTE.md |
| Reconhecer | Cinco áreas e estado de aquisição; Ferramentas reúne estados locais/USB | Procurar o estado de conexão, sem assumir que ajustes funcionam | Origem de erro USB na WebView real ainda DESCONHECIDA |
| Prever/agir | Conectar central só com equipamento e consentimento; sem quadro válido, CTA de escrita não pode ganhar permissão por inferência | Aguardar projeção de status e idade de quadro | Compatibilidade do perfil físico não homologada |
| Confirmar/continuar | Estado conectado + dados atuais com unidade e proveniência; exportar não equivale a gravar na ECU | Passar ao diagnóstico, ou recuperar da falha de cabo | Precisa teste de reconexão e stale em Android |

Estado pendente: aguardando ligação/leitura; êxito: leitura atual comprovada; falha: cabo indisponível; parcial: status conectado mas dados incompletos; recuperação: reconectar, aguardar releitura, nunca repetir escrita automaticamente. Teste proposto: pedir ao dono “descubra por que a rotação não aparece e obtenha leitura atual” sem nomear botão; registrar escolhas sem inventar acertos.

## 2) Motor perde rotação / anti-apagão
Pessoa: pretende entender quedas e agir somente sobre parâmetro cujo efeito foi demonstrado. Entrada: Ferramentas → Anti-apagão.
| Fase | Pista real e necessidade de decisão | Risco ou lacuna | Contrato de recuperação |
|---|---|---|---|
| Orientar | Rotação atual e combustível na barra, com idade de quadro | Uma queda de rotação não prova sozinha apagão em movimento | Estado indisponível explícito |
| Reconhecer | Interruptor anti-apagão; cartão “Valores” mostra rotação máxima e referência de fábrica | Quatro extras têm rótulos originais, mas sem efeito físico provado por campo | Não oferecer recomendação numérica para extras |
| Prever | Consequência de ligar é intenção de proteção da ECU; não prometer cura | OBS-FIO histórico não valida build presente | Diferenciar comando enviado, ACK e leitura confirmada conforme contrato EXISTENTE, sem alterar writer K |
| Agir | Um toque explícito, nunca automática | USB/busy/NAK podem interromper | Falha traz causa e próximo passo; nenhum toque repetido oculto |
| Confirmar/continuar | Estado lido e opção de Desfazer só se elegível | Não confundir sucesso no simulador com efeito motor | Registrar sessão, comparar situações equivalentes antes de nova proposta |

Testes existentes: ux-fluxo-toque.cjs cobre toque uma vez/NAK/sem cabo/nenhum quadro espúrio na bancada. Teste físico e prova do perfil permanecem BLOCKED.

## 3) Revisar Mapa K / Curva K sem alterar a central
Pessoa: quer comparar curva, mapa e sugestões, sem saber SerialCode. Entrada: Ajuste local ou Ajuste global, a partir da navegação existente.
| Fase | Informação disponível | Decisão legítima | Aceite e incerteza |
|---|---|---|---|
| Orientar | Gráfico / grade, eixos e rótulo do estado da central | Identificar leitura atual vs proposta local | Capturas CI #2053 mostram layout, não validade física |
| Reconhecer | Seleção de célula no Mapa K e pontos na Curva K | Escolher análise local sem acionar writer | Teste toque físico 144 células pendente |
| Prever/agir | Revisar efeito previsto e limites, só operação manual já existente | Não iniciar escrita automática, reset geral, flash ou 0x37 novo | Prévia antes de intenção, segurança serial preservada |
| Confirmar | Semântica K ACK/“Gravado” da decisão histórica de 2026-10-19 vs exigência nova de readback | **BLOQUEIO_DE_DECISÃO** | Não mudar texto de recibo nem correção K automática sem OK expresso |
| Continuar | Salvar sessão, exportar histórico local ou voltar sem perder proposta | Distinguir exportação e write físico | Recuperação de sessão e arquivo exige Android real |

Estados: leitura indisponível → aguardar; proposta não gravada → editar/desistir; comando pendente → bloquear toque duplicado; ACK com readback desconhecido → conservar decisão existente e não alegar prova física; falha → preservar rascunho e caminho de retorno.

## Diagnóstico UI / Flow / Feel separado

- UI: quebra de “Ferramentas” no retrato DPR3 nas quatro telas técnicas (FALHOU VISUAL no CI #2053); Ferramentas em si mantém rótulo inteiro. Não tocar no CSS das telas técnicas sem mockup conjunto e aprovação.
- Flow: cobertura automatizada confirma algumas transições Ferramentas/Sessões; prova de descoberta humana e continuidade Android são NE (não avaliadas).
- Feel/confiança: recibo de sessão exige ok explícito e mantém formulário após erro; ACK de writer K não equivale a readback físico.
- Validação humana futura: o dono consegue explicar, antes do toque, se a ação consulta, altera arquivo local ou grava central e, após erro, recuperar-se sem perder valores. Não atribuir score nem tempo arbitrário.

**Estado OP-17: PARTIAL — walkthrough documental, sem observação de pessoa real.**
