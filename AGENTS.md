# OMEGAS V8.0 RED → BLUE — contrato operacional

## Autoridade e linha de trabalho

- O repositório é a fonte canônica de código, requisitos, decisões, status, testes e evidências.
- Fluxo obrigatório: uma Issue canônica → branch BLUE → spec/plan → TDD RED/GREEN → CI exata → checkpoint.
- A linha atual é a Issue #29 na branch `work/omegas-blue-causal-engine`.
- Não criar genealogias paralelas nem reescrever falhas intermediárias como sucesso.

## Autoridades científicas

- O motor MP48 é autônomo: gasolina × GNV por Petrol Inj. sob RPM × MAP comparáveis.
- O motor OBD é autônomo: STFT Bank 1 do GNV por RPM × MAP adquiridos em 010C/010B/0106.
- OBD não usa MP48 para coleta, qualidade, erro ou multiplicador.
- MP48 não exige OBD. Concordância OBD pode bonificar confiança; ausência ou conflito nunca bloqueiam, recalculam ou alteram alvo MP48.
- LTFT não participa da matemática.
- Evidências, épocas, prontidão, persistência e falhas permanecem isoladas.
- O Mapa K físico é RPM × Petrol Inj. OBD sem Petrol Inj. confiável deve retornar `ADDRESS_UNRESOLVED`, nunca inventar coluna.
- Um resolvedor de endereço só localiza célula; não pode alterar o erro OBD.

## Engenharia

- TDD obrigatório: falha esperada, implementação mínima, teste focado verde e regressão ampla.
- Investigar causa antes de corrigir sintoma.
- Cada comparação/proposta carrega origem, época, região, qualidade e identidade de fonte.
- Ciclo OBD científico só é válido com RPM, MAP e STFT completos, finitos, plausíveis e em até 750 ms.
- Aprendizado OBD exige declaração explícita de GNV em cada vida do serviço.
- Persistência quente é atômica e limitada; escrita de evidência não pode dominar o polling.

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
- APK requer autorização explícita do owner e workflow manual.
- `PROVEN` exige SHA, árvore, run, jobs, artefato e limites em STATUS e Issue.
- CI não substitui veículo: não alegar economia, estabilidade física ou compatibilidade ELM real sem teste físico.
