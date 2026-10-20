# Regras permanentes do dono (OMEGAS) — fonte canônica

Vale em toda sessão neste repositório. O gancho `.claude/hooks/regras-do-dono.txt` só aponta para cá; o contrato curto está em `AGENTS.md`.
Branch canônica: `OMEGASCANONICO` (padrão do GitHub). Estado vivo: `docs/handoff/PENDENTE.md`.

1. **Graphify sempre.** `graphify query` antes de buscar código/docs. Toda captura, descoberta, função feita ou pendente vira nó (índice `docs/progbase/evidencias/INDEX.json` + `graphify update .`). Dois grafos: PESQUISA (ProgBase/evidências) e PRODUÇÃO (só `app/`), cada um com implementado × falta.
2. **Eficiência sem perder função nem potência.** Respostas curtas; sem narrar ferramenta; agentes com escopo fechado e arquivos disjuntos, até 20 min cada, push a cada tarefa; testes pesados no CI remoto (o CI do SHA é a prova).
3. **Autonomia.** Executar o autorizado; parar só por bloqueio real, ação irreversível ou decisão do dono. Nunca dizer "estou rodando" sem ter lançado (comando, lugar, arquivo de saída).
4. **Evidência.** Dizer como, por quê e como saberei que funcionou; hipótese ≠ fato (marcas OBS-FIO, OBS-COD, DERIVADO, INFERIDO, DESCONHECIDO).
5. **Codex.** Delegar o demorado via `tools/codex-task.sh` se houver login/franquia; senão seguir sozinho sem reclamar. Superpowers quando o dono pedir.
6. **UI.** Mockup PNG por tela antes de reformar; tema escuro único; sem rolagem horizontal; nunca remover botão ou função sem avisar; um APK só com certeza (link + SHA-256).
7. **Central (ECU).** A central é a verdade (margem 4–5%). Travas de escrita: curva 0,8–1,8; mapa > 200 ⇒ K ≤ 1,5; reset nunca pausa o aprendizado; salvar curva só manual; AutoCal sem leitura anterior; apagar ponto manual não desarma a limpeza.
8. **Navegação e telas (dono, 2026-10-20).** No máximo 5 botões de navegação: AutoCal, Refino, Ajuste local, Ajuste global e Ferramentas. Sessões, Diagnóstico e o resto vivem DENTRO de Ferramentas, organizados em grupos com nome claro (organizado não é poluído). A barra do topo é persistente (tensão, MAP, injeção de gasolina, rotação) e ganha o nível do gás como bolinhas, como as do comutador físico; não existe tela "Agora". Calibrar a leitura do nível do gás fica em Ferramentas.
9. **Botões sempre à esquerda (dono, 2026-10-20).** Em multimídia, quanto menos esticar o braço, melhor: toda barra de botões e toda linha de controles (−, +, Espera, Salvar…) fica agrupada à esquerda, sem buraco no meio e sem botão isolado no canto direito.
10. **Aprovação de tela.** Nada de aprovar mockup solto: mandar o conjunto de TODAS as telas e o mapa do fluxo de navegação, e só implementar depois do OK do dono.

