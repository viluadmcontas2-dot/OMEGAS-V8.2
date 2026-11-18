# OMEGAS 8.2 — Contrato de experiência (versão inicial)

> Contrato operacional para **produção**; não representa homologação de interação humana nem física. Valores visuais canônicos: [DESIGN.md](DESIGN.md). Regras da central e do dono: `AGENTS.md`, `docs/REGRAS-DO-DONO.md`, `docs/handoff/CONTEXTO-DO-DONO.md`. Matriz funcional: `docs/qualidade/matriz-funcional.json`.

## Pessoa, tarefa e dispositivos
- A pessoa acompanha a central do carro, lê rotações, pressão, aprendizado, decisões de ajuste manual e histórico de sessões. Linguagem pt-BR, sem jargão técnico não explicado.
- **Meta de acessibilidade:** WCAG 2.2 AA onde aplicável ao WebView Android, sem declarar conformidade até testes reais de contraste, teclado, zoom e tecnologias assistivas.
- Dispositivos: 9:16, paisagem multimídia 800×480/1024×600/1280×720, tablet e ultrawide; dedos e mouse/teclado quando disponíveis.

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
- Em Ferramentas até 390px a palavra "Ferramentas" não pode se dividir entre linhas; em 1920/2560px os cartões não devem crescer verticalmente só porque há altura livre. Testes com geometria real no Chromium em `tests/ui/adaptive-premium-layout.test.cjs`.
- **Full HD retrato — prova automatizada:** 1080×1920 CSS e 360×640 CSS em DPR 3 (1080×1920 físicos) estão na suíte de cinco rotas; o Mapa K também verifica 13 colunas/144 células, alinhamento e DPR efetivo no caso estreito. CI #1957, SHA `cd7929ebc7bcb614638814a959e93383a1b4585f`, **verde** em 2026-11-17; [execução](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44057081419). Capturas são do Chromium automatizado, **não** homologação de WebView/ECU em hardware real. A aceitação física segue `BLOCKED` até consentimento e dispositivo.
- Alvos prioritários em multimídia: preferencialmente 56px; em telefone manter pelo menos área de toque prática e foco visível.
- Longos textos de alerta/erro devem quebrar; no smartphone, Ferramentas usa rolagem vertical, sem esconder ações.
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
