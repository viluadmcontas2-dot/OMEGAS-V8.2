# OMEGAS 8.2 — Contrato de experiência (versão inicial)

> Contrato operacional para **produção**; não representa homologação de interação humana nem física. Valores visuais canônicos: [DESIGN.md](DESIGN.md). Regras da central e do dono: `AGENTS.md`, `docs/REGRAS-DO-DONO.md`, `docs/handoff/CONTEXTO-DO-DONO.md`. Matriz funcional: `docs/qualidade/matriz-funcional.json`.

## Pessoa, tarefa e dispositivos
- A pessoa acompanha a central do carro, lê rotações, pressão, aprendizado, decisões de ajuste manual e histórico de sessões. Linguagem pt-BR, sem jargão técnico não explicado.
- **Meta de acessibilidade:** WCAG 2.2 AA onde aplicável ao WebView Android, sem declarar conformidade até testes reais de contraste, teclado, zoom e tecnologias assistivas.
- Dispositivos: somente paisagem (horizontal) no Android: telefone em paisagem, multimídia 800×480/1024×600/1280×720, tablet e ultrawide. A `MainActivity` deve solicitar `screenOrientation=landscape`.

## Mapa de autoridade da interface
| Capacidade | Dono atual | Contrato | Prova |
| --- | --- | --- | --- |
| Cinco rotas e seleção | `core/router.js` | nunca inventar rota ou perder estado em troca de orientação | `tests/ui/adaptive-premium-layout.test.cjs` |
| Estado ao vivo | `components/live-bar.js` e serviço Android | desconhecido ≠ zero; barra sempre visível | `tests/ui/global-live-bar.test.cjs` |
| Cartões Ferramentas | `components/advanced.js` e `core/cartoes-motor.js` | título, efeito, estado e ação legíveis | `tests/ui/ux-fluxo-toque.test.cjs` |
| Navegação interna Ferramentas | `components/drawers.js` | Sessões e Diagnóstico são áreas internas | `tests/ui/ux-fluxo-toque.test.cjs` |
| Ações e leituras da central | Kotlin + bridge canônica | somente funções com evidência e trava; não simular confirmação | `docs/qualidade/matriz-funcional.json` |
| Alertas e resultados | notificação/estado já existente em `app.js` e cards | estado acessível e recuperável, sem toasts redundantes | `tests/ui/copy-sem-ambiguidade.test.cjs` |
| Scroll | `styles.css`, `styles-adaptive.css` | **um dono vertical** por layout; o mapa técnico tem painel de rolagem próprio | `tests/ui/adaptive-premium-layout.test.cjs` |
| Seleção/entrada | controles HTML existentes e `components/advanced.js` | não criar combobox ou máscara paralela; manter números e unidades | `tests/ui/anti-apagao-valores-didatico.test.cjs` |

## Jornada intuitiva verificável
| Passo | O que a pessoa deve reconhecer | Resposta e continuidade | Lacuna |
| --- | --- | --- | --- |
| 1. Sem cabo | Central desconectada / dados sem origem | conectar cabo e conceder permissão; nenhuma gravação disponível | primeiro uso em hardware pendente |
| 2. Central conectada | rotação, pressão, tensão, injeção e estado | barra ao vivo estável, "—" se não houver dado | calibração de alguns dados pendente |
| 3. Ferramentas | categorias com nome leigo e valor no cartão | toque abre o detalhe **sem perder a origem do valor** | testes em tela física pendentes |
| 4. Alteração manual | efeito previsível e alcance restrito | ação só pelo toque explícito; feedback de trabalho/erro | confirmar conflitos ACK vs leitura de volta com dono |
| 5. Conferência ou falha | resposta recebida ≠ valor relido | estado confirmável ou incerto, próximo passo explicável | só alterar copy de escrita com decisão explícita |
| 6. Sessões | dados são do aplicativo, não memória permanente da central | consulta e exportação com confirmação real | verificar retorno de seletor Android |

## Estados e recuperação
- **Sem leitura**: número "—"; não usar vermelho de erro nem "desligado" para dado desconhecido.
- **Ocupado**: manter dimensões do botão e contexto; duplicação de toque não pode iniciar gravação simultânea.
- **Sucesso, falha, parcial, incerto**: mensagens não equivalentes; separar retorno do comando e verificação posterior.
- **Desconectado/sessão mudou**: não reutilizar dado antigo de outra sessão como leitura da central atual.
- **Voltar**: devolver ao pai Ferramentas com contexto seguro; erro de cartão não deve esvaziar a aba inteira.
- **Preferências não aprovadas**: não reestruturar as quatro telas técnicas nem inserir função falsa.

## Responsividade e acessibilidade
- Barra de cinco rotas não some; rótulos inteiros e legíveis; teclas Tab/Enter/Space onde aplicável.
- Em Ferramentas na paisagem compacta, cartões e ações devem caber ou rolar dentro da área de trabalho sem corte horizontal; em 1920/2560px os cartões não devem crescer verticalmente só porque há altura livre. Testes de geometria real no Chromium em `tests/ui/adaptive-premium-layout.test.cjs`.
- **Escopo ativo:** somente paisagem, com referência canônica 1280×720 e cenários compactos de 640×360 (DPR 3), 740×360 e 800×480. O antigo CI #1957 comprovou cenários retrato, mas são evidências históricas, não critérios atuais. Capturas Chromium **não constituem homologação de WebView/ECU em hardware real**; a aceitação física permanece separada.
- Alvos prioritários em multimídia: preferencialmente 56px; em telefone manter pelo menos área de toque prática e foco visível.
- Textos longos de alerta/erro devem quebrar; em paisagem compacta, Ferramentas deve permitir rolagem vertical interna sem esconder ações.
- Dado técnico só pode ficar em painel recolhido se a decisão principal continuar compreensível; nunca ocultar aviso que muda o sentido de um botão.
- Movimento reduzido e offline devem funcionar sem rede externa. A aplicação não carrega fontes remotas.
- Zoom da WebView atualmente é desativado por `MainActivity.kt`: **lacuna de acessibilidade a investigar**, não declarar conformidade.

## Aceite do pacote de tema
- Contraste e estados visuais aplicados **somente em Ferramentas**, sem reescrever autoras de interação.
- Nomes/rotas/ordem dos comandos não alterados; 8 formatos testados no CI do SHA do PR.
- Capturas de cada rota e fluxo com USB ausente, ativo e falha **ainda precisam** de revisão visual e confirmação em hardware.
- Assuntos que exigem dono: mudança de semântica de gravação e conjunto completo de mockups para redesenho de abas técnicas. Nenhum merge/APK automático.


### Recuperação local após cancelar exportação (OP-3, Task 17)

A cópia, o registro do app e as exportações de Sessão/Resumo abrem o seletor **Android**. Fechar o seletor sem escolher arquivo é cancelamento, nunca salvamento; o usuário recebe informação explícita e pode tentar novamente sem carregar o ID de sessão descartado. O texto “exportado” só pode vir depois de salvar efetivamente no URI. Evidência atual limitada a OBS-COD + CI de Kotlin/teste de contrato; sucesso em aparelho Android ainda **NÃO VERIFICADO**.


### Estado desconhecido da bateria (Ferramentas, OP-3 / Task 14)
A API de bateria da central Android distingue: `supported=true` e não isento → exibir risco e opção de permissão; `ignoringOptimizations=true` → isenção de otimização reportada; ponte ausente `supported=false && ignoringOptimizations=false` → **Sem leitura**, jamais “Sem limite”. A interface não afirma que o Android jamais interromperá o app. Sem bridge disponível, não há CTA de permissão fictícia; orientar verificar o serviço do app e tentar novamente. RED do texto falso antes da mudança; GREEN exige Chromium no SHA, Android físico não verificado.


### Refino local SHADOW/OFF — recibo de configuração
O interruptor de Ferramentas só reflete a alteração **depois** que a bridge do serviço retorna `{ok:true,mode:valorSolicitado}`. Resultado parcial/ausente, erro ou modo diferente mantém o estado anterior, informa falha e oferece continuidade sem gravação de K. O modo `SHADOW` apenas sugere depois do AutoMatch; `OFF` desliga a análise. Não oferecer `ON` de escrita automática. Evidência até agora: OBS-COD e teste Chromium; Android físico e homologação pendentes.


**Continuidade do seletor:** uma escolha de arquivo pertence à sessão/resumo selecionados naquele toque. O ID é congelado antes de iniciar o trabalho em segundo plano e descartado do estado pendente, inclusive se a API Android não conseguir obter o serviço. Ao cancelar, nenhum ZIP é declarado salvo. Evidência física em Android ainda pendente.


### Android — solicitar bateria e balão (Ferramentas)
No cartão Bateria e no Balão, `ok:false`/retorno ausente mostra motivo de erro; iniciar Settings/Permissões `launched:true` **não** concede privilégio nem valida overlay ligado, apenas exibe “Aguardando...” até status real. Se o serviço de overlay não responde (`ok:false`), mostrar **Sem leitura** em vez de um botão impossível. O resultado da configuração só se conhece quando o Android reporta estado. Testado com bridge emulada no Chromium, homologação no Android físico ainda pendente.


### Permissões Android ao retornar do sistema (CI #2071)
O evento `omegas-refresh` do Android, emitido no `MainActivity.onResume()`, deve atualizar os cartões **mesmo que um botão de Ferramentas tenha retido foco**. O scheduler comum ainda evita reconstruir a tela enquanto uma edição/toque está ativo. A escolha “Balão ligado” representa `requestedEnabled=true` com `permissionGranted=true`; `visible=false` enquanto o OMEGAS está em primeiro plano não significa “desligado”. Recusa real do serviço `ok:false` nunca vira pedido concedido. Evidências: OBS-COD, testes em Chromium + Kotlin CI pendente; interação Android física ainda não verificada.


### Redesenho vs continuidade do trabalho em Ferramentas
Em `omegas-refresh` (volta do Android), botões tocados podem atualizar status para mostrar concessão real sem exigir outro toque; campos `INPUT` e filtros `SELECT` ainda em edição preservam **nó DOM, foco e valor** até a pessoa concluir a escolha. Teste de regressão Chromium protege o fluxo de Registro do app, sem alegar medição em Android. Nenhuma mudança na navegação ou estilo das telas técnicas protegidas.


### Recibos reais das gravações e multimídia (IMPROVE)
- **Sessão:** `ok:true` sem `recording:true` não significa gravando; `ok:true` sem `recording:false` não significa encerrada. Exibir aviso e manter o último estado confirmado.
- **Ajustes:** `ok:true` sem os cinco valores salvos ou com valores divergentes não confirma persistência. Exibir motivo próximo ao botão, preservar edição e permitir reconsulta antes de tentar novamente.
- **Multimídia:** `enabled` retornado precisa corresponder à escolha solicitada; resposta ausente ou contraditória exibe aviso, sem fingir leitura da ECU. Estes dados são Android/app, não leitura física da MP48.
- **Limite:** testes de código e Chromium não equivalem a homologação do seletor, foco ou persistência no Android físico.


### Car Info e status do balão — verdade operacional (IMPROVE)
Somente mostrar valores da multimídia enquanto `connected=true`; após perda de serviço, substituir valores históricos por “—”. A próxima ligação inicia sem amostra até receber leitura nova. O cartão Balão não ganha ação quando não existe serviço/controlador válido: `ok:false` exibe “Sem leitura”. UI: estado reconhecível; Flow: permanecer na área e aguardar nova leitura; confiança: não declarar velocidade antiga ou permissão inexistente como sucesso. Prova atual de código/testes, Android físico não homologado.


### Sessões locais — dados incompletos
Em listagem de gravações, **ausência de tamanho não significa arquivo de zero bytes** e ausência de data não significa 01/01/1970. Mostrar “—” e “Data não informada” respectivamente, mantendo `0 B` quando o backend fornece exatamente zero. Esta regra não altera exportação, gravação ou retenção de arquivos, e não prova a integridade física dos arquivos em Android.


### Registro do app — categoria que desapareceu
Se a categoria escolhida não existir mais no conjunto atual de avisos, restaurar **Todos os assuntos**, voltar à primeira página e mostrar as mensagens restantes, em vez de deixar um filtro invisível e uma tela vazia. Categorias ainda presentes e seleção explícita de nível continuam preservadas. Ações Android de balão mostram falha sem inverter a intenção humana. Testes VM/Chromium validam o caminho de código, não a tela física.
