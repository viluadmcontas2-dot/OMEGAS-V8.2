# Lacunas por tela (app vendido a leigos que nunca usaram GNV)

Data 2026-10-20. Só LEITURA do app. Regra 12 (`docs/REGRAS-DO-DONO.md`): AutoCal, Refino, Ajuste local e Ajuste global são INTOCÁVEIS; aqui só se LISTA. Só Ferramentas (e o que ela absorve: Sessões, Diagnóstico) pode ser criada/reformada, e tela nova só após OK do dono (regra 10).
Legenda. Prova: **O** = original/captura (`docs/progbase/capturas/originais/CATALOGO.md`, OBS-FIO), **C** = código do original (DFM/i18n, OBS-COD), **P** = pendente no repo, **SE** = sem evidência. Esforço P/M/G. Mexe na central? sim = escreve/pede bytes novos. Prioridade 1 = leigo trava ou corre risco sem isso.
Fontes: `app/src/main/assets/ui/{index.html,app.js,screens/*,core/*,components/*}`, `docs/grafo/PRODUCAO.md`, `docs/progbase/abas/*.md`, `docs/progbase/inventario/{telas,i18n}.md`.

## Prioridade 1 (resumo)
1. Primeiro uso guiado (o que é GNV, o que cada aba faz, ordem: ligar, conferir, AutoCal). 2. Diagnóstico por item (falhas da central). 3. Pergunta "água em 80-90 °C?" antes do AutoCal. 4. Preferências do AutoCal (botão ao lado de "Ações avançadas"). 5. Quando o gás entra (rpm, água). 6. Volta à gasolina por pressão baixa (1,25 bar, 0 s). 7. Painel ao vivo completo (pressão, T.gás, T.água, nível, lâmpadas). 8. Nível do gás (sensor, referências).

## Transversal (todo o app)
| O que falta | Prova | Esf. | Central? | Pri |
|---|---|---|---|---|
| Primeiro uso / "como funciona" (1 página por tela, sequência única "carro apagando") | P (PENDENTE: guia curto; Notion/mockups) | M | não | 1 |
| Ajuda contextual "?" por cartão (hoje só `hint` em Ferramentas) | SE de texto aprovado; padrão em `cartoes-motor.js` | M | não | 1 |
| Estado "central não conectada / ligue a ignição / cabo USB" igual em todas as abas | P (contrato de coerência); existe só no AutoCal (`autocal-cockpit.js` "ECU desconectada") | M | não | 1 |
| Idiomas: app só em português; original tem 26 idiomas (`inventario/i18n.md`, 1160 chaves) e menu "Idioma" (`__OPT_MENU_LANG`) | C | G | não | 2 (venda mundial) |
| Unidades/formatos (ms, bar, °C, rpm) por região; vocabulário "central", "giros" (regra 11) ainda quebrado nas 4 abas travadas | P ("ECU/RPM/MAP" no teste `linguagem-leiga`) | P | não | 2 |
| Termos de uso, aviso de segurança ("não dirija testando"), versão e "Sobre" | O (janela Sobre 12-01-58, sem versionar) | P | não | 2 |
| Configuração do carro (combustível, cilindrada, cilindros, ignição) para o leigo ver "o que está configurado" | O (F1/F2/F6) | M | não (só leitura) | 2 |
| Salvar/Carregar configuração completa (F6/F7 do original) | O (menu 12-01-44); hoje só backup da curva K | G | sim | 3 |
| Programar central / atualizar esquemas (F8/F9) | O; fora do escopo do leigo | G | sim | 3 (não portar) |

## Barra do topo / rodapé (`components/live-bar.js`)
| O que falta | Prova | Esf. | Central? | Pri |
|---|---|---|---|---|
| Pressão do gás, folga de pressão (Press. − MAP), T.gás, T.água, nível, lâmpadas Key-on/Cutoff/Gas/Petrol (hoje: tensão, MAP, injeção, rpm, nível) | O (rodapé; F2 Display 12-00-04) | M | não (leitura) | 1 |
| Gráfico em tempo real com escolha de traços | O (11-59-27..49) | M | não | 2 |
| Indicador de gás x gasolina sempre visível ("rodando a gás?") | O (rodapé GAS verde) | P | não | 2 |

## AutoCal (`screens/autocal-cockpit.js`, `autocal-original.js`) INTOCÁVEL, só lista
| O que falta | Prova | Esf. | Central? | Pri |
|---|---|---|---|---|
| Botão "Preferências" ao lado de "Ações avançadas" (`<summary>Ações avançadas</summary>`, linha ~736), abrindo o diálogo de opções do original | C (`SettingsImgClick` 0x00519708 abre `TAutoCalSettings`; `BtnMenu`, `SettingsImg` em `TabAutoCalNew.md`) | M | não (opções locais) | 1 |
| Preferências: tempo de atualização (`RefreshTime`), mostrar zonas, mostrar pontos, mostrar mensagens de estado, cores (`BtnChangeColor`/`TAutocalColorSettings`), valor de calibração (`CalibrationVal`, grava vetor) | C (`FormClose` grava `showstatusmessage`, `showchartzones`, `showchartpoint`; i18n `AUTOCALSETTINGS`, `...SHOWSTATUS`) | M | só `CalibrationVal`: sim; resto não | 1 |
| Número de AutoMatch (`EditNumAutomatch`, rótulo "Num automatch" `LABELNUMAUTOMATCH`) editável | C (campo no DFM); efeito/limites: sem evidência | P | provável (a confirmar) | 2 |
| Limites de MAP (`CursorLimitMAP_MIN`/`MAX`) e áreas de aquisição (`AcqusitionAreas`) ajustáveis | C (séries do gráfico); onde se editam: SE | M | SE | 2 |
| Pergunta "A água do motor chegou a ~80-90 °C?" + Continuar antes de iniciar | O (11-54-23) + C (i18n 688 `LABELTEMPERATURAMONOPLUG`, `ButtonProsegui`); no original vem do fluxo `Calibra` (F11), não provado na `TAutoCalUI` | P | não | 1 |
| Explicar zonas, "#n/d" e botões de reset em linguagem simples (balão sem cobrir o gráfico) | P ("balão de informação não pode ficar por cima") | P | não | 1 |
| Painel de partida do original (água, gás, tensão dos injetores, tempos, pressão) como pré-checagem | O (11-54-23) | P | não | 2 |
| Botão por zona para apagar pontos (1 toque) | P | M | sim | 2 |
| Atribuição por rpm do Refino no AutoCal | P (prévia PNG antes) | M | não | 3 |
| Exportar/compartilhar a sessão a partir da tela | P (exportação em 1 toque) | P | não | 2 |

## Refino (`screens/refino.js`, `core/map-fix.js`) INTOCÁVEL
| O que falta | Prova | Esf. | Central? | Pri |
|---|---|---|---|---|
| Explicar as fases e o que significa "Refinar por minha conta" (1 frase por fase) | SE (não há texto no original; é do OMEGAS) | P | não | 1 |
| Estado vazio ("ainda sem trancos detectados, dirija mais") | SE | P | não | 2 |
| Tamanhos 1024x600 e 800x480 cortam conteúdo | P (`tools/ci/sem_rolagem_abas.cjs`) | M | não | 2 |
| Troca "ECU/RPM/MAP" por "central/giros/pressão" | P | P | não | 2 |

## Ajuste local / Mapa K (`screens/map.js`) INTOCÁVEL
| O que falta | Prova | Esf. | Central? | Pri |
|---|---|---|---|---|
| Editar as referências do mapa ("Modify map refs.": 12 rpm x 12 tempos, OK/Cancelar) | O (11-50-09, 11-57-05); vetor das linhas de ms não provado | G | sim | 2 |
| Legenda de cores/valores crus (226-253) para o leigo; "o que é Mapa K" em uma frase | O (11-47-54) para a grade; texto SE | P | não | 1 |
| Tempos mortos (gasolina 0,6 ms; extra 0 ms; aba Times) | O (11-50-40) | M | sim | 3 |
| Mapa de contribuição de gasolina 12x12 em % (desligado no original) | O (11-50-49) | G | sim | 3 |
| Renormalizar quando perto de 255 (1 toque, Desfazer) | P | M | sim | 2 |
| Cortes em 1024x600 e 800x480 | P | M | não | 2 |

## Ajuste global / Curva K (`screens/curve.js`) INTOCÁVEL
| O que falta | Prova | Esf. | Central? | Pri |
|---|---|---|---|---|
| Aviso "só mexa se...": reset da curva longe de 1,0 pode desligar o GNV acima de 14,1 V (regra de projeto) | AGENTS (decisão de campo 2026-10-13) | P | não | 1 |
| Explicação do que é a curva e de "Curva K → Mapa K" para leigo | SE de texto | P | não | 2 |
| Gravação rápida (1 quadro de vetor 0x37) | P (precisa de OK do dono e ECU real) | M | sim | 3 |
| Cortes em 1024x600 e 800x480 | P | M | não | 2 |

## Sessões (`#sessionsScreen`, será absorvida por Ferramentas)
| O que falta | Prova | Esf. | Central? | Pri |
|---|---|---|---|---|
| Exportar em 1 toque abrindo direto o resumo, com nome do carro e data | P | M | não | 1 |
| Compartilhar (mensagem/e-mail) e "enviar ao instalador" | SE | M | não | 2 |
| Estado vazio que convida a agir ("Nenhuma sessão registrada ainda" hoje é texto seco) | código (`autocal-cockpit.js:1188`) | P | não | 2 |
| Privacidade: espelho público em Download/Omegas | P | P | não | 2 |

## Ferramentas (`core/ferramentas-grupos.js`, `cartoes-motor.js`; grupos: Anti-apagão, Volta para gasolina, Troca, Marcha lenta, Outras)
| O que falta | Prova | Esf. | Central? | Pri |
|---|---|---|---|---|
| Diagnóstico por item (14 verificações, OK/erro, ação, reset de erros); mockup 01 existe, sem OK | O (11-58-22); P | G | sim (reset) | 1 |
| Quando o gás entra: tipo, rpm mínimo 1200, água 40 °C (hoje só bits) | O (11-45-41) | M | sim | 1 |
| Volta por pressão baixa: tempo (0 s) + valor (1,25 bar); hoje o cartão fala de rotação | O (11-46-05); P (decodificar 0x4A9484, 0x4C526C) | G | sim | 1 |
| Nível do gás: 7 tipos de sensor, Reserva/1/4/2/4/3/4, mín./máx., "Find levels" | O (11-46-34..52) | G | sim | 1 |
| Painel ao vivo / gráfico dentro de Ferramentas | O (12-00-04) | M | não | 2 |
| Injetores com números (abertura mínima 2,5 ms, extra 0,1 ms, corte, enriquecimento 50) | O (11-52-02) | M | sim | 2 |
| Marcha lenta: escrita (hoje só leitura/sugestão) | P (provado no emulador) | G | sim | 2 |
| Anti-apagão: 1 interruptor + 1 número; 4 extras recolhidos como experimental | O (11-52-02); P | P | sim | 2 |
| Lambda/sonda e lubrificante (só um bit hoje) | O (11-45-51) | M | sim | 3 |
| Diagnóstico inteligente (sugere, nunca aplica) | P | G | não | 3 |
| Tela vazia "Ferramentas vazia" no aparelho | P (confirmar) | P | não | 1 |

## Fora do que se pode afirmar
Valores por país, certificação, loja/licença, conta de usuário e telemetria na nuvem: sem evidência no repo.
