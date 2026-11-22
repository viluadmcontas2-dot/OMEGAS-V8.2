# Laboratório S20 — OBD + MP48 (branch isolada)

**Estado:** protótipo de leitura e experimentação; NÃO integrado à compilação principal do Ômegas. Código Android separado em `lab/s20-dyno/dyno/`.

## Objetivo — O Ômegas inteiro
Correlacionar eventos da ECU original via OBD-II e da central MP48 para alimentar **AutoCal (aquisição e zonas), Refino, Mapa K, Curva K, Ferramentas (anti-apagão, retorno à gasolina, marcha lenta, diagnósticos), Sessões e perfil da central**, além de descobrir campos e interpretações incorretas. Não reduzir a análise a aceleração.

**A leitura do OBD não autoriza escrita na ECU/MP48.** Qualquer gravação na central só por intenção explícita do dono e readback; este laboratório é somente-leitura.

## Contrato de aquisição
1. Preservar original de cada resposta e seu relógio monotônico, fonte, solicitação, latência observável, erro e perda.
2. Manter campos **CONFIRMADO / HIPÓTESE / DESCONHECIDO**, com proveniência. Não converter silêncio em zero nem inventar nível de tanque.
3. Correlacionar tempo de injeção, RPM, MAP, TPS, trim, temperatura, velocidade, pressão do gás, estado de combustível, tensão e eventos de apagão/retorno.
4. Cautela: resposta OBD consultada não significa sniffer de tráfego CAN. O ELM327 pode ocultar cabeçalhos; um PID por vez é assíncrono.
5. Diagnóstico de quedas, apagões, transição gasolina/GNV e hipóteses de calibração são sugestões, nunca escritas automáticas.
6. Arquivo de dados reais fica no S20 ou destino explicitamente autorizado. **Não publicar captura bruta neste repositório público.**

## Primeira evidência no carro, 2026-11-21
Arquivo íntegro exportado do app no S20 para `/sdcard/Download/Omegas/dyno-evidencias/`. Uma sessão em andamento, 3.904 observações (1.130 OBD e 2.774 MP48) entre 21:38:02 e 21:40:21, horário do aparelho.
- OBD: 7 PIDs conhecidos (RPM, velocidade, MAP, TPS, STFT, LTFT, ECT).
- MP48: frame 34 bytes, 2.774 respostas; intervalo mediano de recepção ~44,7 ms.
- **Hipótese a investigar:** byte de estado `0x88` apareceu em 2.113 respostas sem tempo GNV e foi rotulado `transição`; depois `0x90` em 661 respostas com pulso GNV em 636. Houve **uma mudança de 0x88 para 0x90**. O rótulo de `0x88` pode estar errado. Preservar amostras, comparar registros originais e o ProgBase/PROTOCOLO-LANDI antes de corrigir decoder.
- A série MP48 e a série OBD têm cadências diferentes, portanto não pressupor simultaneidade; usar timestamps monotônicos e qualidade do alinhamento.
- Durante captura o app exibiu `0 amostras` na lista de sessões embora o ZIP comprovasse milhares: cache da listagem só se atualiza ao abrir/parar. Investigar UI sem perder amostras.

## Atualizações sem perda de dados
- Preservar **applicationId** `com.omegas.dyno.lab`, certificado de assinatura, `versionCode` estritamente crescente e dados privados.
- Atualizar por instalação **in-place**, sem `pm uninstall` e sem limpar dados; as versões 7→8→9 foram verificadas por `pm install-create -r`, `pm install-write`, `pm install-commit`.
- **Não assumir garantia futura:** `VaultStore.onUpgrade` atualmente falha intencionalmente em mudança de versão do esquema. Antes de mudar schema, implementar migração reversível por backup, upgrade testado de dados reais e teste de regressão de preservação. Exportar ZIP antes da primeira migração.
- Keystore e senha de assinatura permanecem **fora do GitHub**. O repo **não** contém segredos.

## Próximas etapas
1. Sincronização de código no branch isolado, sem modificar `OMEGASCANONICO`.
2. Auditar decoder MP48 versus corpus/ProgBase, particularmente `0x88`.
3. Capturar bytes desconhecidos, erro e tempo da consulta sem derrubar leitor.
4. Exportar observações e eventos para o Ômegas com schema/proveniência definidos.
5. Corrigir listagem em tempo real e fechar testes de migração/assinatura.
6. Integração com o app principal somente após testes e revisão de segurança.

**Não instalar APK novo durante uma captura em andamento.** A branch canônica `OMEGASCANONICO` permanece intocada.
