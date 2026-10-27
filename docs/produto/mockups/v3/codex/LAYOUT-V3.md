
# LAYOUT V3 (dono, 2026-10-27): aproveitar o espaço. Vale para TODA tela 1280×720 nova ou refeita.

O dono viu as telas e pediu: gráficos e cartões esticados na vertical até perto da navegação, sem faixa de ações sobrando, ações perto de quem as usa. Regras:

1. A barra do topo (56 px) e a navegação de baixo (`top:636px`) JÁ ESTÃO no arquivo e na ordem certa (No gás · Nível · Inj. gasolina · MAP · RPM · Tensão). NÃO mexa nelas. Só mude qual aba está ativa se a tela pedir.
2. NÃO existe mais barra de ações embaixo (`top:538px`). O conteúdo vai de `top:128px` até `top:624px`: painel esquerdo e cartão direito com `height:496px`.
3. Cabeçalho `left:32px;top:64px;width:1216px;height:56px`: em SUB-TELAS (10, 11, 12, 13, 19, 20) o 1º elemento é o botão "Voltar" (`<a>` 56×56, `aria-label="Voltar"`, ícone de seta para a esquerda, `border:1px solid var(--line);background:var(--surface2);border-radius:12px`, link para a tela de origem), depois o título (30 px), o chip de estado e a pílula de UMA frase (`flex:1;min-width:0`, sem cortar texto).
4. As AÇÕES da tela ficam no FUNDO DO PAINEL ESQUERDO (`margin-top:auto`), botões de 58 px com a largura toda do painel, no máximo 2 empilhados (vão 8 px). Ação secundária pequena vira linha de 48 px com ícone e texto dentro do painel. Nenhum botão solto no canto direito.
5. O gráfico/cartão da direita usa a área inteira (830×496) e o espaço para dentro: sem margem inútil; o eixo começa a ~56 px da borda esquerda do cartão; as barras/linhas crescem na vertical.
6. Aviso fixo "Gravado e conferido pela central: …": linha dentro do painel esquerdo, logo acima dos botões (não numa faixa embaixo). E o ATIVIDADE DA CENTRAL no topo, ao lado direito da barra (ver 7).
7. ATIVIDADE DA CENTRAL (gravar NÃO trava a tela; dono: "um avisozinho sutil… e um sinal quando acabar"). Um selo no canto direito da barra do topo, `position:absolute;right:16px;top:8px;height:40px;box-sizing:border-box;padding:0 14px;display:flex;align-items:center;gap:8px;border-radius:10px;font-size:16px;font-weight:700;white-space:nowrap`, SEM janela e SEM bloquear nada:
   - gravando: `background:var(--accent-soft);border:1px solid #2a4a7e;color:var(--text)`, ícone de setas girando (estático) + "Gravando Curva K · 5 pontos"
   - conferindo: mesma cor + "Conferindo na central · 3 de 5"
   - pronto: `background:var(--ok-soft);border:1px solid #1d5a46;color:var(--ok)`, ícone de visto + "Gravado e conferido"
   - erro: `background:var(--danger-soft);border:1px solid #7a3036;color:var(--danger)`, ícone de aviso + "Não gravou" (o painel diz o que fazer).
   Na prévia, tocar no botão de gravar liga o selo direto em "pronto" (sem timers). Fica até a próxima ação.
8. Vocabulário novo do dono para RÓTULOS CURTOS: "MAP" (pressão da admissão) e "RPM" (rotação). Em frases corridas continue em português simples ("giros", "pressão do gás").
9. Texto de corpo ≥ 17 px; legenda ≥ 14 px; botão ≥ 58 px de altura; contraste e cores como nos modelos. Sem roxo.
