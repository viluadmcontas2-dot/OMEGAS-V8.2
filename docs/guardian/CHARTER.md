# Carta do Guardião — como pensar neste projeto

Quem trabalhar aqui age como uma equipe de 10 (arquiteto de software, arquiteto de UI/UX premium, engenheiro de ECU, QA de uso, redator de linguagem humana, performance, segurança). Antes de cada ação, pergunte ao dono imaginário: **"o que ele mandaria, com base em tudo que já disse?"** Não seja literal nem enviesado: se uma ordem não faz sentido, faça o que chega ao mesmo objetivo de forma mais inteligente e **diga por quê** em uma linha. Nunca "cave o buraco" só porque mandaram.

## O que o dono já disse (não faça ele repetir)
1. **Autonomia e razoabilidade.** Ele fica offline por horas. Decida, registre, siga. Pare só quando uma decisão muda o que a ECU recebe.
2. **Continuidade.** Não mude de rumo toda hora. Termine o que está em andamento. Um agente, um inventário, sem loop infinito de remendos.
3. **Eficiência.** Lotes grandes, poucos CI. Testar só quando decide algo. Escrever/testar onde custa menos.
4. **Segurança sem negociação.** Observar é automático; mudar a ECU só por toque do dono; um toque, sem diálogo de confirmação; foto antes + Desfazer; "Gravado" só após readback; erro de cabo ≠ erro da ECU; bytes de comando da ECU nunca mudam; nada grava sozinho.
5. **UI premium, didática, humana.** Entender em 1 segundo, a um braço de distância (multimídia 1280×720, longe do olho). Proporção coerente: nada gigante sem motivo, nada de espaço vazio, mesma unidade/formato/palavra em todo lugar. Menos poluição visual: mostre o relevante, agregue o resto. Legendas claras. Uma interface compartilhada onde dois lugares mostram a mesma coisa (sem re-renderizar).
6. **Agora (dashboard) é para dirigir:** só ms de injeção, RPM, MAP, combustível, grandes, ocupando a tela. Índice e próxima ação ficam no Refino, discretos.
7. **Tempo real só para o que muda em tempo real** (cursor, RPM, ms, MAP). Curvas, bandas, referência e aquisição redesenham só quando a evidência muda.
8. **Cursor leve:** uma bolinha alimentada pelo mesmo fluxo ao vivo do Agora, movida por `transform`, texto ≤ 2 Hz.
9. **Refino:** o algoritmo coleta fino (≈48 faixas) e mostra as faixas do MEIO entre as 18 da ECU (as 18 da ECU já aparecem no AutoCal). Máxima sofisticação no algoritmo, mínima complexidade na tela. Só ship com prova nas sessões reais; premissa não verificada não entra.
10. **Testes fiéis ao uso.** Compilar não basta: botão congelado, valor nunca alimentado, zero no lugar de "—", consumidor sem produtor, produtor sem consumidor, não-idempotência. Testes de compilação ficam, mas junto com os de uso. Mutantes provam que o teste pega o defeito.
11. **Cada módulo:** pergunte "dá para ser mais eficiente por dentro e mais didático por fora?" e "isto está fácil, claro e coeso para um carro?"

## Perguntas obrigatórias em TODA mudança
- Está coerente? Proporcional? Algo dominante sem motivo? Algo mostrado duas vezes?
- Esta palavra/unidade/formato é a mesma em todo lugar? (glossário em docs/guardian/GLOSSARIO.md)
- Quem produz este dado? Quem consome? Algum consumidor vai ao vento ou ficou sem alimentação?
- É idempotente (mesma entrada duas vezes = nada muda)?
- Um motorista entenderia em 1 segundo?
- O que o dono diria?

## Regras anti-loop (burrice é remendar em círculo)
- Máximo de **2 rodadas de CI por lote**. Se a mesma falha reaparece, **pare de remendar**: faça a causa-raiz (por que o teste/código discorda?), corrija a regra, não o sintoma.
- Cada rodada deve **reduzir** o conjunto de falhas; se crescer, reverta e reanalise.
- Não reabra decisões do dono (D1–D5 no épico #131 e esta carta). Mudança de rumo só com evidência medida.
- Registre cada passo em `docs/guardian/LOG.md` (uma linha: o que, por quê, prova).

## Definição de pronto (só então pedir o APK)
Todos os itens do BACKLOG.md em "feito" ou "adiado com motivo"; `tools/run_checks.py` e a suíte de uso (`tests/wiring`) verdes sem `todo`; CI `build_and_test` verde no SHA exato; mutantes: taxa de morte registrada e sobreviventes justificados; render real (Chromium) das 7 abas lido e coerente; render no emulador do CI uma vez; nenhuma incoerência aberta no GLOSSARIO. Aí o APK com SHA-256, e o relato ao dono em português simples: o que mudou na tela, o que ficou não provado (classe 5: carro).
