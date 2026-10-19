# Handoff do Codex: engenharia reversa COMPLETA do ProgBase (nunca mais descompilar)

Dono (2026-10-19): "a gente nunca mais quer ter que abrir ou fazer esse trabalho de descompilação do ProgBase novamente". Meta: o repositório vira a fonte única; depois desta rodada ninguém abre o .exe. Autonomia total, sem perguntar; commit+push a cada ação; 1 comentário por feature no issue #182.

## Ordem de leitura
AGENTS.md → docs/handoff/CONTEXTO-DO-DONO.md (regras **sem-atrito** e **didática para leigo**) → PENDENTE.md → CODEX-AUTONOMO.md → docs/progbase/RETOMAR.md → docs/progbase/abas/ANTI-APAGAO-mapa.md (modelo do que entregar por função).

## O que já existe (não refaça)
docs/progbase/: inventário (classes, funções, i18n), protocolo (vetores.json 389, comandos.json 34), abas 1–16 (B parcial), decomp (Ghidra 29.927 funções, COBERTURA.md), grafo, manual (MAN-2.16.4-pNNN), capturas/originais, bins (log novo). Emulação: tools/emulacao/ (CPU Unicorn do código original, ECU simulada).

## Trabalho (em ordem)
1. **Fechar a cobertura.** Abra docs/progbase/decomp/COBERTURA.md e liste o que NÃO tem pseudocódigo, nome ou "o que faz". Meta: toda função ligada a uma tela, vetor ou comando com (a) nome, (b) o que faz em 1 frase leiga, (c) evidence id, (d) marca OBS-FIO / OBS-COD / DERIVADO / INFERIDO / DESCONHECIDO. Pontos já conhecidos em aberto: rotinas 0x4A9484 e 0x4C526C (volta automática à gasolina, "pressão baixa 1,25 bar"), bins do Mapa K (vetor de ms; janela "Centros das células" 12×12), aba B.
2. **Evidence IDs versionados e coerentes.** Esquema único `EV-<área>-<nnn>` (REA/MAN mantêm os próprios prefixos como alias). Arquivo mestre docs/progbase/evidencias/INDEX.json com: id, afirmação, marca, fonte (endereço/captura/página do manual), versão do ProgBase (2.16.4), data, SHA do commit, quem a usa (arquivo do app/teste). Teste de CI que falha se: id citado em doc/código não existe no INDEX; id sem fonte; afirmação INFERIDA usada como fato na interface.
3. **Auto-auditoria contínua.** A cada ação 1 linha em docs/handoff/AUDITORIA.md (data, ação, teste RED→GREEN, evidence id, risco). Script `tools/progbase/auditar.py` roda: ids órfãos, funções sem dono, afirmações sem fonte, contradições entre docs (ex.: dois valores diferentes para o mesmo vetor). Corrija sozinho o que achar; o que não der, registre em PENDENTE.md.
4. **Costurar no grafo.** Cada função/vetor/tela/teste/evidência é nó; arestas "tela → vetor → quadro → app → teste → evidência". `graphify update .` + tools/grafo_conhecimento.py + tools/progbase/grafo_progbase.py. O grafo é a forma padrão de responder "isso afeta o quê?" (o dono pergunta isso o tempo todo).
5. **Perfil da central.** O app só oferece o que a central conectada suporta (tabela por modelo/versão derivada de vetores + capturas).

## Funções de referência da AutoCal → aba Ferramentas ("Avançado")
Dono quer as funções de referência/configuração da autocalibração em Ferramentas, com mais incisividade e autonomia. Faça nesta ordem:
- Inventário: liste TODOS os parâmetros e ações do AutoCal no original (TFormAutoCal e correlatas: AUTO_CAL_ENABLE, referências usadas para aprender, ações 02 24 04 <modo>, zonas, aquisição). Marque o que o dono já VIU funcionando em captura/print; só entra no app o que foi visto funcionar (regra do dono), o resto fica "experimental" recolhido.
- Para cada um: cartão em Ferramentas no padrão do anti-apagão (interruptor ou número com − / +; **1 toque grava, sem pergunta**; conferência em segundo plano; Desfazer no cartão; texto "Como funciona / Se precisar de mais / Se atrapalhar" com a evidência real, ligando a função às vizinhas: o que mais ela afeta, o que NÃO é).
- Teste RED→GREEN por cartão (modelo: tests/ui/anti-apagao-toque-real.test.cjs e anti-apagao-valores-didatico.test.cjs) + screenshots 1280x720, 1024x600, 800x480 olhados antes de entregar.

## Regras que nunca mudam
- Pergunta-guia de toda interface: "uma pessoa que nunca mexeu nisso entende, sabe para que lado mexer e o que o carro dela precisa?" Se não, não está pronto.
- Zero atrito: nada de "tem certeza?", nada de 2º toque, nada de botão "tentar de novo", nada por tempo. O app nunca grava sozinho: o toque do dono é a ordem.
- Não burlar licença; não versionar .exe/DLL/License.dat; não apagar branches nem force push; não expor tokens.
- Desconhecido é "—", nunca 0. Nunca "feito" sem teste verde e prova; "UNKNOWN" não é concluído.
- Teste local mínimo (1 arquivo); suíte inteira na CI. Teste que exige o fluxo antigo de 2 toques deve ser reescrito, não enfraquecido.
