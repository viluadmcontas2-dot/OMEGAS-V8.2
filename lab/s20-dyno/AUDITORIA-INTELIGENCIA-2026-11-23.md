# Ômegas — primeira operação do investigador autônomo (23/11/2026)

**Estado do laboratório S20:** APK `1.4.0-aprendizados` (`versionCode=14`), atualizado no mesmo pacote `com.omegas.dyno.lab` com `pm install-create -r`. O UID original `10331` e dados da instalação foram preservados. Certificado de assinatura coincide com a versão anterior. SHA-256 APK: `68aa1355bfc29eff7faa15f7a1a22b83ef3dfc445cce61a4d463d7e05592a33b`. APK arquivado em `/sdcard/Download/Dinamometro-Omegas-1.4.0-aprendizados.apk`.

## O que é realmente executado

- **Captura:** OBD Bluetooth e MP48 USB publicam NDJSON local no S20. Sem comandos de calibração nem alteração da ECU.
- **Investigação determinística:** `agent/discovery_engine.py` usa timestamps monotônicos para RPM, TPS e MAP; identifica cadência, sincronização, mudanças de TPS e associações com respostas de RPM/MAP. Não trata correlação como causa.
- **Conhecimento do ProgBase:** `agent/progbase_evidence.json` contém âncoras versionadas (sha) de decompilação, protocolo e semântica. Cada item tem afirmação comprovada e lacuna explícita; não executa REA por conta própria.
- **Memória:** `agent/research_journal.py` persiste evidência/revisões e análises Gemini em SQLite, separando estados OBSERVADO/EM_INVESTIGACAO/LACUNA. O Gemini não muda estados.
- **Modelo:** `agent/live_agent.py` pergunta periodicamente ao Gemini, com até 40 chamadas diárias e intervalo mínimo de 120 s, focando questões alternadas, usando apenas agregados e fontes versionadas. Resposta fica marcada como interpretação não comprovada. Modelos com falha podem ter fallback. Chave não é versionada no GitHub.
- **Interface:** aba **Aprendizados** exibe as evidências, futuras perguntas e diário do Gemini. Também mantém as abas Agora, Comparar e Meu conhecimento.

## Descobertas concretas nos streams disponíveis nesta execução

- 1.888 pares RPM OBD–MP48 em até 200 ms; erro absoluto mediano 7 RPM.
- 1.888 pares MAP em até 200 ms; diferença absoluta mediana 1,5 kPa.
- TPS OBD aproximadamente 963 ms/amostra; RPM MP48 aproximadamente 135 ms/amostra. Isso impede inferir latência física exata de milissegundos.
- 110 elevações significativas de TPS, 82 seguidas por aumento de RPM, 75 seguidas por aumento de MAP na janela de observação. Em estado MP48 estável: GNV 68 eventos (53 RPM, 46 MAP) e líquido 19 (18 RPM, 15 MAP). O restante não foi rotulado.
- Estado bruto `0x94`: 1.045 quadros nos streams reprocessados. Semântica universal em investigação; a implementação canônica usa predicados físicos de cutoff, não o byte isolado.
- O motor de combustível obteve pares de STFT líquido/GNV, mas o classificador OBD-only ainda informa `SEM_MODELO_VALIDADO`.
- Gemini `gemini-3.6-flash` gerou e arquivou uma avaliação completa TPS/RPM com evidências, incertezas, proposta de fusão assíncrona e próximo estudo passivo. A integração anterior produzia textos truncados; `thinkingLevel` reduzido e `finishReason=STOP` agora obrigatórios.

## Estado e limites

- A atualização in-place 1.4 foi confirmada pelo sistema; o Waze estava em primeiro plano, portanto **não foi interrompido para testar a nova aba visualmente**.
- Na checagem após atualização, `am start-foreground-service` do serviço nativo foi negado pelo Android (`allowStartForeground=DENIED`) enquanto o app estava em segundo plano. **Não afirmar coleta nativa atual nesse momento**. O pesquisador Termux continua ativo com a última telemetria armazenada, mas não fabrica medições novas.
- Sem GPU/LLM instalado localmente: Gemini é remoto, usando a chave armazenada somente no Termux S20; o processamento determinístico e a memória continuam locais.
- O modelo produz hipóteses de investigação; não foi feito fine-tuning de pesos nem há escrita ou comandos de diagnóstico invasivos.
- O REA MCP instalado em outras pastas do S20 não foi invocado nesta rodada; as referências estão ancoradas nos documentos originais do repositório canônico. Para resolução do canal `48 xx` do ΔRPM, programar somente inspeção do REA e comparação das capturas.
- Códigos e relatórios agregados na branch de laboratório; nenhum log bruto privado ou chave do usuário é publicado.
- **Segurança de uso:** observação passiva somente enquanto dirige. Modificar app/autorizações só estacionado.

## Verificações

- **39/39** testes UI/contrato JavaScript aprovados para o APK 1.4.
- **29/29** testes Python aprovados após ampliar as análises TPS→MAP/RPM e a fundamentação ProgBase.
- Compilação Kotlin + D8, assinatura APK verificada, instalação `pm -r` confirmada.

## Próximo ciclo

1. Com o aplicativo seguro em primeiro plano, retomar o serviço foreground e a captura dual e verificar a nova aba visualmente.
2. Produzir comparações TPS/MAP/RPM com condição de carga, estado combustível estável e estratificação por faixa.
3. Extrair mais telemetria MP48 `0x94` e confrontar com fonte ProgBase por código/REA, sem interpretar 0x94 automaticamente como falha.
4. Reduzir o intervalo de polling TPS via decisão ponderada (mais TPS e RPM, menos PIDs) somente se a comunicação com a ECU permitir, e sem alterar parâmetros.
5. Aumentar a durabilidade do processo do Termux no reboot e a observabilidade da falha de foreground service do Android.
