# Incidentes — índice de lições

| Data | Título | Lição |
|---|---|---|
| 2026-08-04 | gate Android bloqueado por wrapper e comparação textual | Provar equivalência semântica e integridade do wrapper no gate completo. |
| 2026-08-04 | autorização redundante e trabalho deixado local | Executar autorização já concedida e distinguir commit, merge e publicação. |
| 2026-08-04 | estado de sessão implícito na interface | Explicitar sessão e suspender modo oficina diante de telemetria insegura. |
| 2026-08-04 | mapa antigo permanecia utilizável após falha de releitura | Invalidar mapa anterior antes da releitura; falha nunca libera escrita. |
| 2026-08-06 | backpressure da telemetria na multimídia 9" | Retirar processamento pesado da thread ECU e drenar nas fronteiras de sessão. |
| 2026-08-06 | fonte funcional incorreta usada no diagnóstico | Confirmar repositório, branch e SHA do APK antes do diagnóstico. |
| 2026-08-08 | AutoCal: largura dos contadores, limiar de maturidade e ponto prematuro | Interpretar largura e maturidade por combustível; MaxAutomatch não é limiar de maturidade. |
| 2026-08-08 | Foreground service classificado como `dataSync` | Declarar foreground connectedDevice e acrescentar location somente com GPS ativo. |
| 2026-08-08 | pedidos repetitivos de permissão USB | Filtrar VID/PID antes da permissão e limitar retries preservando a sessão lógica. |
| 2026-08-09 | forma AutoCal sem versão + status 0xCA genérico | Obter versão antes da forma e classificar status estendido sem retry cego. |
| 2026-08-09 | sugestão global da Curva K era apenas informativa | Preparar prévia da Curva K após leitura real; revisão humana precede o writer. |
| 2026-08-09 | backlog do aprendizado e células pouco didáticas | Coalescer persistência fora da telemetria e limitar trabalho visual ao estado necessário. |
| 2026-08-09 | aprendizado atrasado por backlog quente acumulativo | Limitar backlog quente e impedir publicação de gerações de sessões encerradas. |
| 2026-08-09 | live tracing sobrecarrega a WebView na multimídia | Atualizar somente diferenças visuais e evitar animações, trilhas e ticks globais. |
| 2026-08-09 | autoridades múltiplas de mutação da ECU | Centralizar política de escrita e revalidar antes de cada operação mutável. |
| 2026-08-11 | volatilidade de célula teoricamente consolidada | Preservar visitas imutáveis e exigir contradição repetível para promover novo consolidado. |
| 2026-08-12 | Advisor recalculado com frequência maior que a revisão científica | Recalcular Advisor por revisão científica e publicar somente o derivado mais recente. |
| 2026-08-12 | AutoCal expunha Manual AutoMatch sem fidelidade ao comportamento observado | Observar AutoMatch nativo por readback; observação não autoriza escrita do app. |
| 2026-08-12 | checkpoint portátil automático no caminho quente | Retirar checkpoint portátil do caminho quente; manter checkpoints manuais e críticos. |
| 2026-08-12 | sidecar de evidência do Learning crescia sem orçamento | Limitar proveniência e snapshots derivados sem descartar evidência científica. |
| 2026-08-12 | crescimento de proveniência na memória principal do Learning | Compactar proveniência textual preservando contagens e estatísticas científicas. |
| 2026-08-12 | Learning pesado no caminho crítico de startup | Restaurar aprendizado fora do startup e rejeitar operações científicas até READY. |
| 2026-08-12 | entrega visual tratava estado ao vivo como histórico ordenado | Coalescer estado visual ao vivo com no máximo um pendente, sem interromper transação. |
| 2026-08-12 | autoridade serial MP48 fragmentada | Usar scheduler serial único com escrita manual e readback indivisíveis. |
| 2026-08-18 | owner 089 deixou matemática duplicada no reconciliador | Reutilizar FuelEquivalenceObjective e recusar comparação inválida. |
| 2026-08-19 | âncora AutoCal perdida durante restauração do Learning | Reproduzir operações materiais em ordem durante restore, com fila limitada e saturação explícita. |
