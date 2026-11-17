# OMEGAS 8.2 — especificação de produto fiel e adaptativo

> **Estado:** proposta de projeto, não aprovação de gravação na central, alteração do motor ou merge. Criada pela revisão de 2026-11-17, com referência a `OMEGASCANONICO` no commit `28afdaa74df1cb81e84d9f4ee3261d92050ad976` e ao PR #195. Reconciliar o HEAD remoto em cada execução.

## Intenção, pessoa, problema e sucesso

A pessoa quer observar, compreender e ajustar **manualmente** a sua central de GNV OMEGAS/MP48, sobretudo diante de apagões/engasgos, com comunicação fiel ao programa original ProgBase 2.16.4, sem aprender protocolos nem assumir risco evitável. A experiência deve funcionar em multimídia automotiva 1280×720, 1024×600 e 800×480, bem como retrato 9:16, tablet, janelas estreitas, desktop e ultrawide. O mesmo trabalho deve manter contexto, significado e segurança em telas diferentes, sem prometer sincronização que não existe.

**Resultado:** todo fluxo incluído na versão é rastreável a origem/limites/capacidade; uma pessoa consegue identificar estado, tomar uma decisão, agir por um toque **intencional** e ver o resultado real ou recuperar-se; nenhuma ação de escrita ocorre sem gesto deliberado e sem as travas do escritor nativo. Gráficos e controle não podem ser cortados sem alternativa de acesso.

## Abordagens avaliadas

1. Clonar telas Delphi e transportar todos os comandos: rejeitada; conserva UX antiga e disponibiliza funções sem confirmação da central.
2. Reescrever do zero Kotlin/protocolo e criar UI moderna: rejeitada; aumenta risco, invalida trabalho comprovado e duplica autoridade serial/estado.
3. **Escolhida como recomendação de engenharia, pendente de aprovação visual:** auditoria guiada por prova + endurecimento de segurança + catálogo de capacidades + experiência adaptativa e orientada à tarefa. Reutilizar Kotlin, serial, WebView e replay existentes; alterar semântica só mediante evidência; reestruturar UI com autorização dos mockups de conjunto.

## Regras e limites vinculantes

- Autoridade técnica: GitHub `OMEGASCANONICO`; mudanças isoladas em branch; CI remoto do SHA exato, nunca usar trabalhos locais como fonte.
- O original é oráculo de comportamento/semântica de comando, **não** de estética. A ECU física é autoridade final de suportes e efeitos; emulação, Ghidra e Portmon não equivalem a validação no carro.
- Uma ação visível da central só entra se houver finalidade clara e um contrato de evidência: ação/tela original, versão/perfil, método, endereço ou código, unidade/escala, pedido, eco/ACK/NAK, pós-leitura, limites e falhas. Se uma dimensão não está provada, ficar leitura/sugestão ou oculto com explicação apropriada.
- Nunca permitir comandos de escrita por endereço arbitrário, firmware/boot, SetDuty atuadores, reset geral, testes de saída ou funções sem origem autorizada. Não ativar recursos só porque aparecem em enum, decompilação ou no catálogo.
- O app nunca escreve K/Mapa automaticamente, nunca confunde ACK com conferência dos dados e não publica “Gravado” antes da leitura de volta correspondente; sem auto-regravação que contorne a intenção do toque. Diferenciar aceito, conferindo, confirmado, parcial, falhou e estado incerto.
- Ações manuais com um toque intencional e feedback, sem confirmação modal redundante; intertravamento é transparente. Histórico/desfazer quando efetivamente possível. Não inventar desfazer para limpeza irreversível de buffer.
- Nenhuma inferência de “veículo parado” só por rotação ou telemetria sem velocidade; evitar interações que incentivem uso perigoso ao dirigir.
- Apenas cinco áreas principais: AutoCal, Refino, Ajuste local, Ajuste global e Ferramentas. Sessões/Diagnóstico dentro de Ferramentas; barra de leituras persistente; linguagem simples (“central”, “rotação do motor”, “pressão”).
- Não tomar decisões científicas por relógio; usar fatos/eventos de captura e respostas. Não criar segunda autoridade JS para ciência/serial. Telemetria visual latest-only sem perder fatos científicos.
- Mudanças em AutoCal/Refino/Mapa/Curva anteriormente protegidas: o pedido atual autoriza **planejar** revisão, não implementação visual sem **um conjunto completo de mockups por tela + mapa do fluxo + OK do dono**, conforme `docs/REGRAS-DO-DONO.md`.
- Sem APK, merge, gravação na ECU, direção assistida, bypass de licença, instalação ou alteração de propriedades do carro na execução deste plano sem autorização adequada.

## Classificação de capacidade e evidência

Classificar cada função por duas dimensões independentes:

- **Origem**: OBS-FIO, OBS-COD, DERIVADO, INFERIDO, DESCONHECIDO; preservar identificadores, fonte e versões. “Derivado” ou “inferido” nunca vira “observado”.
- **Capacidade da ECU**: CONFIRMADA_NO_PERFIL (leitura/comando aceito e conferido com a central do perfil), ORIGINAL_SEM_PROVA_NA_ECU (fluxo original conhecido, central ainda não comprovada), NÃO_SUPORTADA (resposta explícita), DESCONHECIDA (nenhum teste). Separar leitura de escrita e transport failure de NAK da central.

Regras da superfície:
- CONFIRMADA_NO_PERFIL + comando aprovado + travas: exibir ação manual pertinente.
- ORIGINAL_SEM_PROVA_NA_ECU: mostrar contexto **somente de leitura** quando seguro, e marcar “não verificado neste carro”; bloquear escrita até confirmação específica.
- NÃO_SUPORTADA/DESCONHECIDA: não oferecer CTA de gravação nem simular existência da função. Diagnóstico técnico pode mostrar indisponibilidade sem induzir ação.
- **Funcionalidade exclusiva do app**, sem comando correspondente na ECU (sessões, organização, comparação, exportação, ajuda, sugestão): rotular como análise/apoio do OMEGAS; não apresentar como botão/programação da central.

## Base factual e fronteiras da auditoria

Arquivos de autoridade: `AGENTS.md`, `PROJECT.md`, `docs/REGRAS-DO-DONO.md`, `docs/handoff/PENDENTE.md`, `docs/progbase/COBERTURA.md`, `docs/progbase/decomp/COBERTURA.md`, `docs/progbase/protocolo/{comandos,vetores,matriz-comando-resposta,backlog}.*`, `docs/progbase/evidencias/INDEX.json`, `docs/produto/lacunas-por-tela.md` e `docs/ux/mapa-de-telas.md`.

Em 2026-10-18 a cobertura gerada documenta 98 evidências (37 OBS-COD, 27 OBS-FIO, 10 DERIVADO, 10 INFERIDO e 14 DESCONHECIDO); 389 vetores, 222 observados no fio; 75,5% é **métrica composta do catálogo**, não percentagem de produto pronto. A decompilação cobre 2.329 funções de unidades do programa, 7.132 sem nome/unidade e 3 rotinas sem pseudo-C; não significa que todas as funções devam virar UI. O original registra 39.524 transações na captura LN e a emulação reproduz 21.506 pares distintos sem divergência nessa fonte; isso não prova o comportamento físico na central do dono.

Achados de auditoria de código:
- `calibration/KWriteManager.kt` reporta “Gravado” ao ACK e verifica linhas depois, chegando a reescrever divergências como correção da mesma operação: conferir com a regra humana “nunca grava sozinho” e recuperar sem promessa incorreta;
- `usb/UsbProtocolReply.kt` classifica NAK por igualdade de código, ao passo que o catálogo do original descreve máscara de bit 0x10; testar respostas alternativas e contrato da versão MP48;
- o repositório registra diferenças conhecidas no enlace, ttNone 163/2000, bins ms não provados, 4 parâmetros extras anti-apagão não interpretados com segurança, função da volta por pressão e testes físicos pendentes;
- PR #195 possui camada adaptativa, mas CI 1905 falhou em teste Ferramentas pelo uso de `document.querySelector` sem shim no teste (3 falhas); demais jobs terminaram com sucesso. Não alegar que a UI está homologada.

## Modelo mental e jornada pretendidos

Objetos que a pessoa compreende: **meu carro**, **central conectada**, **aprendizado**, **pontos de gasolina/GNV**, **proposta**, **alteração manual**, **resultado confirmado**, **sessão** e **cópia**. Um objeto de proposta não é mudança na central; “enviou” não é “gravou”; consulta de dados não é suporte de escrita.

Jornada inicial (a validar em teste de uso): conectar cabo → conferir central e frescor das leituras → entender trabalho atual/única próxima ação → consultar gráfico/ajuste → receber proposta explicada (não aplicada) → tocar intencionalmente, se permitido → observar aceitação/conferência → confirmação real ou falha/estado incerto → consultar recibo/sessão e continuar. Manter trabalho pendente após orientação ou USB interrompida; sem atalhos que saltem a etapa de contexto.

## Direção de UI/UX premium e multiformato

- Priorizar uma pergunta por tela: “O que o carro está fazendo?”, “Está aprendendo?”, “Onde difere?”, “O que posso ajustar com segurança?”, “Onde estão registros e ferramentas?”.
- Superfície dominante: gráfico no AutoCal/Refino, mapa/curva nas edições, decisões/estado nas Ferramentas. Detalhe técnico sob demanda, não ocultação de risco.
- Reorganizar por **espaço disponível** e forma de entrada, não por nomes de dispositivos. Em telas 16:9 automotivas: elementos grandes, controles alcançáveis, sem conteúdo cortado e sem animações contínuas. Em retrato: fluxo vertical e navegação fixa; área técnica pode deslizar dentro de painel explicitamente indicado, sem rolagem horizontal global; toolbars não desaparecem.
- Requisitos de teste: 360×800, 390×844, 768×1024, 800×480, 1024×600, 1280×720, 1920×1080, 2560×1080; também rotação, zoom/acessibilidade, teclado e safe-areas. Testar cada estado de cada rota, não apenas tela vazia.
- Microinterações discretas de foco/tocar/estado; sem loading infinito, sem pop-ups para “já está assim”; usar confirmação de resultado verificável no cartão. Respeitar redução de movimento e dispositivos lentos.
- Navegação previsível e ações com verbo/objeto; grupos curtos. Contexto retido entre rotas sem repetir leitura desnecessária.
- Produtos modernos sem recursos inventados: propostas explicáveis por eventos das sessões; diagnóstico guiado com evidência, indicação de incerteza; comparação visual gasolina × GNV; backup e recuperação guiados. Nenhuma dessas análises vira comando automático.

## Critérios de aceite global

1. Matriz requisito→fonte→função original→endereço/escala/forma→comando→resposta→perfil da ECU→Kotlin/ponte→tela→teste→recibo preenchida para **100% das funções expostas no escopo atual**; desconhecidos explicitamente retidos no backlog.
2. Zero gravações implícitas, zero sucesso afirmado só por ACK, zero recurso perigoso acessível sem prova; testes de desconexão, NAK, timeout, mistura de sessões e rollback coerente.
3. 100% dos estados relevantes das cinco rotas testados nos tamanhos-alvo: sem perda funcional, sem sobreposição, com foco/navegação, leitura e ações acessíveis; aprovação de todas as telas como conjunto.
4. CI completo no mesmo SHA verde, testes de replay diferenciais verificados, capturas DOM/PNG e logs auditados; SKIP não equivale a PASS. Tempo de resposta, memória e carga real aferidos em multimídia autorizada.
5. Evidência em dispositivo Android e com central física do dono para recursos de escrita; se faltar, status “VALIDAÇÃO_FÍSICA_PENDENTE” e **não declarar 100%**.
6. Publicação só com ordem do dono, APK rastreável com SHA-256, plano de recuperação e sem alterar `OMEGASCANONICO` por automatismo.

## Aceite da proposta

Plano de implementação em `docs/superpowers/plans/2026-11-17-omegas-v82-programa-mestre.md`. Antes de reformar as telas existentes, apresentar o conjunto visual e fluxo, recolher aprovação explícita; antes de um novo writer, exigir evidência do comando e autorização segura, de preferência com teste físico acompanhado pelo dono.
