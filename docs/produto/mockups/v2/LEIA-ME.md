# Mockups v2: o app inteiro, para aprovar o conjunto (só desenho; o app NÃO foi alterado)

Responde à rodada 1 (reprovada em parte) e às regras 8, 9 e 10 de `docs/REGRAS-DO-DONO.md`. PNG em 1280x720 e 800x480 (`NN-nome-LxA.png`).
Fonte: `fonte/gen.js` (gera os HTML) → `fonte/render.js` (fotos + medição: sem rolagem, nada cortado, botões com 58 px começando à esquerda; 0 problemas). Tokens de `assets/ui/styles.css`.
Comece por `00-mapa-do-fluxo` (só 1280). Marcas: EXISTE = já está no app · NOVO = não existe · **PRECISA OK DO DONO** = muda o que a central recebe ou pede leitura nova.

## Barras (todas as telas)
- **01-barras**: topo = Tensão, Pressão da admissão, Inj. gasolina, Rotação (EXISTE, `live-bar.js`) + **Nível**: 5 bolinhas Reserva (vermelha), ¼, ½, ¾, cheio (verdes), 6 estados; + lâmpada "No gás / Na gasolina / Sem central" (NOVO). Nível = número cru do medidor (Level 125 no original, F2/F4) convertido pelas marcas da tela 10. **IMPLEMENTADO (2026-10-20, aprovado)**: o app já recebe o nível (`level_raw` = byte 13 do 48 01); `live-bar.js` usa pontos de corte PROVISÓRIOS no meio das marcas do original (Reserva 39, ¼ 93, ½ 143, ¾ 219: cortes 66/118/181/237); a escala será regulável em Ferramentas (tela 10). Sem leitura = "—" e bolinhas apagadas. Embaixo: 5 botões, mais nada.

## Ferramentas (02 a 14)
- **02 / 02b-02f**: 6 grupos à esquerda, cartões do grupo à direita (um grupo por vez). Cartão = ação à esquerda + nome + estado em uma frase. Grupos: Evitar apagão · Gasolina ⇄ gás · Peças do gás · Diagnóstico · Sessões e arquivos · Ajustes do app. Todos os cartões de hoje (págs. 1 e 2 de Ferramentas e a aba Sessões) estão aqui; nada sumiu.
- **03 Anti-apagão** (EXISTE cartões "Anti-apagão" + "Valores"; original 11-52-02 "Max RPM 1600"): interruptor + Rotação máxima 1.600. Grava bit FLAG_CONF3 e vetor ANTI_STALLO (EXISTE, com conferência). Botões: `‹ Voltar` `−` `+` `Salvar na central` `Voltar ao de fábrica` `Outros valores ›`.
- **03b Outros valores** (EXISTE, os 4 extras, marcados "Em teste"): mesmos botões, sem `Outros valores ›`.
- **04 Saída do corte** (EXISTE cartão; original 11-45-59/11-52-02): lê injeções e corte das extras. "Gás menos forte na saída do corte 0 %" é NOVO (leitura **PRECISA OK DO DONO**). Botões: `‹ Voltar` `Ler de novo`.
- **05 Marcha lenta** (EXISTE, `idle-mode.js`): só lê modo e rotação; sugestão em texto. `‹ Voltar` `Ler de novo`.
- **06 Troca de gasolina para gás** (EXISTE, 5 bits, só leitura). `‹ Voltar` `Quando o gás entra ›` `Ler de novo`.
- **07 Injetores** (EXISTE 3 bits; NOVO 2,5 ms / 0,1 ms / 50 do original 11-52-02, leitura **PRECISA OK DO DONO**). `‹ Voltar` `Ler de novo`.
- **08 Volta para gasolina** (NOVO; original 11-46-05: 1,25 bar, 0 s): pressão ao vivo + 2 valores. Salvar **PRECISA OK DO DONO** (endereço não provado; 1,25 comparado à diferença de pressão = INFERIDO). O bit atual "gasolina em rotação alta" não foi apagado: muda de lugar para esta tela (decidir com o dono). `‹ Voltar` `−` `+` `Pressão` `Espera` `Salvar na central`.
- **09 Quando o gás entra** (NOVO; original 11-45-41: ao acelerar, 1200, 40 °C): 3 condições com ✓/✕ ao vivo. Salvar **PRECISA OK DO DONO**; temperatura da água ao vivo também é leitura nova. `‹ Voltar` `−` `+` `Giros` `Água` `Salvar na central`.
- **10 Nível do gás** (NOVO; original 11-46-34: 39/93/143/219, live 125) e **10b Tipo do medidor** (7 tipos, 11-46-41). Gravar marcas/tipo **PRECISA OK DO DONO**. 10: `‹ Voltar` `Trocar medidor ›` `Marcar vazio` `Marcar cheio` `Achar marcas` `Salvar marcas`; 10b: `‹ Voltar` `Salvar na central`.
- **11 Avisos da central** (NOVO; original 11-58-22, 14 linhas): cor + palavra (OK / Atenção / Falha) + o que fazer no topo. Só lê (ler os 14 = **PRECISA OK DO DONO**, comando novo). `Apagar avisos de falha` = EXISTE ("Apagar erros OBD"). `‹ Voltar` `Ver detalhes` `Apagar avisos de falha`.
- **11b Detalhes**: o que a central faz em cada falha (→ Gasolina / Só avisa), só leitura. Editar = **PRECISA OK DO DONO**.
- **12 Sessões** (EXISTE aba Sessões → vira cartão): lista + resumo da selecionada. `‹ Voltar` `Exportar esta sessão` `Apagar esta sessão`. **12b**: depois de apagar, um toque: aviso + `Desfazer`.
- **13 Exportar e sobre** (EXISTE exportar ZIP e salvar registro; "Sobre" NOVO, sem código de licença). `‹ Voltar` `Exportar tudo` `Salvar registro do app`.
- **14 Registro do app** (EXISTE). `‹ Voltar` `Testar o app` `Salvar registro`.
- No cartão, um toque (EXISTE): `Testar` (teste de gravação), `Guardar cópia`, interruptores Refino automático e Balão, Bateria `Permitir`.

## Abas travadas (15-18)
AutoCal, Refino, Ajuste local (Mapa K), Ajuste global (Curva K): foto do app real; muda só as duas barras. Achado: em 800x480 essas 4 telas já hoje cortam à direita e embaixo (não é desta rodada; não mexi, regra 13).

## Decisões (1 linha cada)
- Grupos à esquerda + cartões à direita: 6 grupos cabem em 800x480 sem rolagem e só 2-3 cartões aparecem por vez (organizado, não poluído).
- Ação do cartão à esquerda (regra 9) e uma só; o interruptor do anti-apagão foi para a tela 03 (1 toque a mais) para o cartão ter uma ação.
- `‹ Voltar` é sempre o 1º botão da barra; em 800x480 vira `‹` e os textos longos encurtam (`Salvar`, `Vazio`, `Exportar`).
- `−` `+` ficam na barra de baixo e agem no valor marcado (`Pressão`/`Espera`, `Giros`/`Água`), como o dono descreveu.
- Lâmpada e conexão saíram da barra de baixo para a de cima: a de baixo fica só com os 5 botões (texto 22 px).
- Marca "OMEGAS" saiu da barra de baixo; aparece em "Exportar e sobre".
- "Sensor da temperatura do gás" virou "Termômetro do gás" (cabe em 800 e é mais simples).
- Reserva = só a bolinha vermelha, como no comutador; sem leitura = bolinhas apagadas e "—" (nunca 0).
- Números das telas 07-11 vêm das capturas do original; o carro do dono pode ter outros.
