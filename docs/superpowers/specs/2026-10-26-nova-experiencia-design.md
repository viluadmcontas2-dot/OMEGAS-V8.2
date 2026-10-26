# Nova experiência OMEGAS — spec de desenho (2026-10-26)

Branch: `nova-experiencia` (a partir de `OMEGASCANONICO@28afdaa`). Volta-atrás: Git (sem interruptor no app).
Autorização do dono (2026-10-26): suspende a regra 12 e as travas de layout; "refatoração completa com liberdade criativa". Continua valendo: a central nunca recebe gravação sem toque do dono, com leitura de volta e Desfazer.

## 1. Intenção e sucesso

Objetivo final do app: **regular o GNV o mais rápido e preciso possível**, cruzando o máximo de dados, para quem não sabe nada de carro ou GNV. O usuário não conhece algoritmos; a interface conduz.

Sucesso, medido por teste e captura:
1. Primeira tela diz, em uma frase e um botão, o que fazer agora (nunca mais de uma ação dominante).
2. Qualquer tela se explica sozinha: o que é, o que fazer, o que acontece (ajuda "?" em 1 toque; primeiro uso guiado em 4 cartões).
3. Menos toques: tarefas repetidas têm ação em lote com um só Desfazer.
4. Sem rolagem em 1280×720, 1024×600 e 800×480; alvo de toque ≥ 56 px nas ações principais.
5. Mesmo vocabulário, mesmo estado "Sem central" e mesmos padrões em todas as telas.

## 2. Invariantes (não mudam)

- Protocolo, `core/write-gate.js`, `core/write-verify.js`, `core/native-api.js` e a camada Kotlin ficam intocados. Toda gravação segue prepare → execute / `writeMap` / `writeCurve` com leitura de volta.
- Bomba única de dados (`app.js refreshFast`); telas novas leem `store` e usam `scheduler.addHook`; nada de `equivalence()` a cada quadro.
- Desconhecido é "—", nunca 0. Português leigo ("central", "giros", "pressão"). Sem roxo (teste `sem-roxo`). Campo numérico `inputmode="none"` com lápis.
- Kotlin só chama `OmegasApp.router.navigate('<rota>')` (hoje `refino`) e o evento `omegas-refresh`: as rotas antigas continuam existindo.

## 3. Arquitetura da informação

Navegação inferior: **4 abas**.

| Aba | Para quê | Rota |
|---|---|---|
| Regular | Tela inicial. A jornada em 4 passos e UMA próxima ação. | `regular` (nova) |
| Carro | O que o carro está fazendo agora, em frases e medidores grandes. | `carro` (nova) |
| Ajuste fino | Aprendizado, Automático, Região e Geral numa tela só, com seletor. | `ajuste` (nova, hospeda as telas atuais) |
| Ferramentas | Cópia, gravações, anti-apagão e o resto, em grupos. | `tools` / `sessions` (existentes) |

Rotas internas mantidas: `autocal`, `refino`, `map`, `curve`. Elas viram *visões* dentro de Ajuste fino (seletor com 4 segmentos: Aprendizado · Automático · Região · Geral). Os motores e ids continuam; muda o casco, a ordem e a explicação. `navigate('refino')` abre Ajuste fino no segmento Automático.

Barra do topo (leituras ao vivo) continua persistente; ganha o estado de central único (ver 5.4).

## 4. Telas novas

### 4.1 Regular (início)
- Anel grande de progresso ("Regulagem 62%") e a manchete em uma frase.
- Trilha de 4 passos, cada um com estado (feito / agora / depois):
  1. **Preparar**: central conectada, tensão normal, nível do gás, água quente (de Car Info se existir; senão "não medido").
  2. **Coletar**: "Dirija normalmente". Mapa de cobertura simplificado (faixas de giro × pressão, 4 zonas) de `AutoCalOriginal`/`humanState`.
  3. **Ajustar**: lista "Onde o carro sofre" (de `stalls()` e `autopilot`), ordenada por impacto, com **Aplicar sugestão** em 1 toque quando existe proposta.
  4. **Conferir**: antes e depois em palavras, de `refinement.result` (MELHOROU / PIOROU / MEDINDO).
- Botão principal único. Ele chama a mesma ação do cockpit atual (preparar → executar), nunca grava por conta própria; a leitura de volta e o Desfazer aparecem no próprio cartão.
- Estados vazios convidam a agir ("Ligue a ignição e conecte o cabo USB").

### 4.2 Carro (ao vivo)
- 4 medidores grandes (giros, pressão da admissão, injeção de gasolina, tensão) com faixa boa/atenção escrita.
- Linha de situação em palavras: "Rodando a gás", "Em gasolina", "Em transição", "Motor apagou 2 vezes hoje".
- Nível do gás, fonte dos dados e diagnóstico simples (o que está estranho e o que fazer).
- Só lê; nenhuma gravação nesta tela.

### 4.3 Ajuste fino
- Seletor de 4 segmentos com frase de uma linha do que cada um faz e quando usar.
- Recomendação em destaque quando um segmento resolve o problema atual ("Para o motor que apaga na parada, use Automático").
- Cada valor editável diz o que é e o efeito de subir ou descer (regra 14 mantida).

## 5. Mecânicas que reduzem toques

### 5.1 Seleção e ação em lote
- **Curva K**: arrastar sobre o gráfico seleciona uma faixa de pontos; atalhos "Faixa baixa / média / alta"; **Preparar selecionados** adiciona vários à proposta de uma vez; **Suavizar seleção**; **Voltar seleção a 1,0**. Revisão e gravação continuam as de hoje (um Desfazer cobre o lote).
- **Mapa K**: atalhos por situação ("Marcha lenta", "Cruzeiro", "Aceleração forte"), tocar no cabeçalho de linha/coluna seleciona, "Inverter seleção".
- **Aprendizado**: o que já existe (marcar vários pontos, limpar zona) fica no primeiro nível da tela, com "Limpar pontos ruins" que seleciona os suspeitos de uma vez.

### 5.2 Ajuda em 1 toque
`components/ajuda.js`: botão "?" fixo em cada tela abre um cartão de 3 linhas (O que é · O que fazer · O que acontece). Textos em um único arquivo de conteúdo, em português leigo.

### 5.3 Primeiro uso
4 cartões (o que é regular o GNV · ligar e conferir · dirigir · aplicar e conferir), mostrados uma vez e reabríveis em Ferramentas › Como funciona.

### 5.4 Estado da central
Um único componente "Sem central / Conectada" com o próximo passo ("Conecte o cabo USB e ligue a ignição"), igual em todas as telas.

## 6. Sistema visual

Camada `styles-novo.css` carregada por último, com variáveis do design system OMEGAS V8.2 (tema escuro único, `--bg`, `--surface`, `--accent`…): cartões de raio 14, alvos ≥ 56 px, uma tipografia (títulos 22/20/15, texto 14, números tabulares), cor só para estado e sempre com palavra, movimento ≤ 120 ms. Tudo cabe em 1280×720 sem rolagem.

## 7. Testes e entrega

- Atualizar os contratos de navegação (`test_clean_ui_contract.py`, `e2e-navegacao`, `navegacao-sem-rota-morta`, `todas-as-telas-no-arranque`, `app-shell-runtime`) para as 4 abas, sem enfraquecer: continuam exigindo todas as telas, sem rolagem e sem texto cortado.
- Testes novos por tela (Regular, Carro, Ajuste fino, lote, ajuda) com Chromium + ponte falsa (`tests/ui/helpers/browser.cjs`).
- Capturas 1280×720, 1024×600, 800×480.
- Suíte completa uma vez antes do PR; CI no pull request.
- Itens fora desta entrega: reescrever o gráfico AutoCal; novos comandos para a central; idiomas.
