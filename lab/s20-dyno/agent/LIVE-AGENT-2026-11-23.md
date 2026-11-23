# Ômegas Intelligence Agent — 23/11/2026

## Entrega
Motor Python em `agent/fuel_engine.py` e serviço local `agent/live_agent.py`, rodando no Termux S20 em `127.0.0.1:18765`. Captura histórica de 21/11 reprocessada: 1130 OBD + 2774 MP48; 26 janelas OBD rotuláveis como GNV e zero rotuladas como gasolina, nenhuma comparação gasolina/GNV validável. **Não existe ainda modelo confiável de detecção de combustível apenas com OBD**. Estado `0x88` jamais rotulado como gasolina.

## Funcionamento
- O APK 1.1 salva stream NDJSON somente local em `Download/Omegas/Inteligencia/stream-*.ndjson` (quando recebe telemetria real).
- Agent segue o stream com cursor, gera `latest-analysis.json` e endpoint local `GET /status`.
- `fuel_engine.py` procura variações de STFT, mudança de estado e compara observações gasolina/GNV com controle de RPM, MAP, TPS e temperatura; não utiliza correlação como prova.
- `rules.json` pode mudar tolerâncias e janelas sem recompilar o APK. Não avalia Python remoto nem executa qualquer comando sobre a ECU.
- Gemini é opcional, sob chave do usuário em `~/.config/omegas/gemini.key` com modo 0600. Campo de chave via diálogo do próprio APK encaminha por loopback; não é hardcoded. Analisa somente JSON de agregados/eventos (nenhum quadro bruto) com limite de 1 solicitação / 120s e 40/dia, teto de tokens, sempre marcado `SUGESTAO_NAO_E_PROVA`.
- API Gemini padrão configurado para `gemini-3.8-flash`; disponibilidade depende do projeto e deve ser confirmada na primeira chamada. O backend não está apto a inferir composição de etanol se o OBD não expuser PID 0152.
- OBD só consulta modos 01 de leitura, incluindo 0142 (tensão), 0151 (tipo declarado) e 0152 (percentual etanol) **se forem anunciados via bitmap de suporte 0140**.

## Segurança e observação
- Recepção e análise são passivas, sem alteração de mapas, calibrações, reset ou gravação na central.
- O APK usa Android `MediaStore.Downloads` para exportar telemetria no próprio S20. Nenhum dado bruto foi versionado no GitHub ou enviado à API.
- **Bloqueio observado durante instalação v11:** Android negou inicialização do foreground service a partir de shell com o 99 em primeiro plano (`allowStartForeground=DENIED`). Não interrompemos a navegação para abrir o APK. A captura ao vivo ainda aguarda o primeiro lançamento do aplicativo a partir do primeiro plano, em condição segura, seguido do serviço persistente.
- v11 instalou in-place (UID 10331, mesma assinatura, sem `pm uninstall`). A inteligência local está executando e recebeu apenas o arquivo anterior; precisa do fluxo novo para ficar verdadeiramente ao vivo.
- Ativação do Gemini precisa de chave real do usuário, que não está presente. Não declarar Gemini online sem essa credencial.

## Validação
6 testes Python cobrindo rótulo, pareamento, confusão de temperatura, ausência MP48 e eventos STFT; 25 testes JS de interface e segurança; compilação Kotlin e assinatura do APK.
