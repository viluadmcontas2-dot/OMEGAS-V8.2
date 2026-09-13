# OMEGAS V8.0 RED → BLUE — contrato operacional

## Autoridade e linha de trabalho

- O repositório é a fonte canônica de código, requisitos, decisões, status, testes e evidências.
- Fluxo obrigatório: uma Issue canônica → branch BLUE → spec/plan → TDD RED/GREEN → CI exata → checkpoint.
- A linha atual é o épico #30 com filhos #31–#35 na branch `work/omegas-blue-causal-engine`; #25 permanece gate físico separado.
- Não criar genealogias paralelas nem reescrever falhas intermediárias como sucesso.

## Autoridades científicas

- O motor MP48 é autônomo: gasolina × GNV por Petrol Inj. sob RPM × MAP comparáveis.
- UUID identifica evidência/visita; a região científica RPM × MAP é determinística e versionada.
- O motor OBD é autônomo: STFT Bank 1 do GNV por RPM × MAP adquiridos em 010C/010B/0106.
- OBD não usa MP48 para coleta, qualidade, erro ou multiplicador.
- MP48 não exige OBD. Concordância OBD READY, fresca e coerente pode bonificar confiança; ausência ou conflito nunca bloqueiam, recalculam ou alteram alvo MP48.
- LTFT não participa da matemática.
- Evidências, épocas, prontidão, persistência e falhas permanecem isoladas.
- O Mapa K físico usa linha = Petrol Inj. e coluna = RPM. OBD sem Petrol Inj. confiável deve retornar `ADDRESS_UNRESOLVED`, nunca inventar endereço.
- Um resolvedor de endereço só localiza célula; não pode alterar o erro OBD.

## Engenharia

- TDD obrigatório: falha esperada, implementação mínima, teste focado verde e regressão ampla.
- Investigar causa antes de corrigir sintoma.
- Cada comparação/proposta carrega origem, época, região, qualidade e identidade de fonte.
- Ciclo OBD científico só é válido com RPM, MAP e STFT completos, finitos, plausíveis e em até 750 ms.
- Aprendizado OBD exige declaração explícita de GNV em cada vida do serviço.
- Persistência quente é atômica, coalescida e limitada; escrita de evidência não pode dominar o polling.
- Freshness da telemetria é independente de mudança dos valores visuais arredondados; scheduler de UI não pode acumular ticks.

## Segurança de calibração

- Observar, aprender, prever, abrir editor e preparar proposta não escreve.
- Escrita é manual: preparar → revisar → confirmar → ACK → readback.
- Serviço ativo, USB, ECU pronta, telemetria fresca, confirmação humana, ACK e readback continuam requisitos.
- Mapa K editável permanece em 100..180.
- Uma alteração confirmada inicia nova época do motor afetado.
- Falha, ambiguidade ou endereço não resolvido produz abstenção.
- Nenhum componente grava automaticamente.

## Verificação e CI

- GitHub Actions é a execução primária.
- Gate: FAST → JVM/unit → lint → READY FOR APK GENERATION no SHA exato.
- APK requer autorização explícita do owner e workflow manual/re-run autorizado.
- `PROVEN` exige SHA, árvore, run, jobs, artefato e limites em STATUS e Issue.
- CI não substitui veículo: não alegar economia, estabilidade física, ausência de ANR/soak ou compatibilidade ELM real sem teste físico.
