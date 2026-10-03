# Evidência remota por PR autorizada
Contrato antes da alteração dos workflows, 2026-10-03.
O dono autorizou APK com build_apk=true, render 1280×720 e digest por PR relevante.
O conector GitHub desta execução não expõe workflow_dispatch; buscar credenciais ou usar
máquina do dono não faz parte da autorização. Chamadas reutilizáveis ao MESMO workflow
canônico obtêm a evidência em Actions. ci.yml continua somente teste/lint.
RED de configuração: run 37106245184 / job 111155234966, fonte 799651180515:
capacidade workflow_call ausente. Classe 1; este teste não prova APK/render.
Implementação: workflow_call com SHA obrigatório imutável de 40 hex, opt-in build_apk
mantido default false; caller fornece true somente em PR do próprio repositório com
branch work/platina-*, permissão contents:read, sem secrets e sem pull_request_target.
Não há APK no push. Render depende do gate APK (contratos, JVM, lint e assemble).
Ambos comprovam checkout do HEAD solicitado; recibo/digest são por esse HEAD.
Cancelamento por número da PR elimina provas antigas em execução; falha bloqueia merge.
Rollback: remover caller e as entradas workflow_call; preserva dispatch manual original.
Riscos: custo do CI/emulador e limite de paralelismo existente (16). Nenhuma alteração
de produto, protocolo ou autorização de escrita. Nenhuma alegação classe 5.
Radar: 1 tela inalterada; 2 falha CI não entrega artefato; 3 jobs 45/35min; 4 nenhum
número de produto; 5 workflows canônicos únicos; 6 SHA explícito; 7 RED configuração,
execução real obrigatória; 8 blueprint F; 9 render pendente; 10 nenhuma ação do dono;
11 fork/ref mutável/permissão ampla bloqueados pelo caller e identidade.
