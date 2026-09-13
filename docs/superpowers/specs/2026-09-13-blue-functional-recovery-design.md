# OMEGAS Blue — Functional Recovery Design — 2026-09-13

## Escopo

Fechar o gate de software das Issues #30–#35 sem recomeçar a arquitetura e sem alterar o gate físico #25. A branch canônica permanece `work/omegas-blue-causal-engine`; GitHub remoto é a autoridade.

## Contratos científicos

### MP48
- UUID identifica a evidência/visita; não identifica a região científica.
- Região científica é uma identidade determinística e versionada derivada de RPM × MAP.
- O pareamento gasolina → GNV continua temporal e fisicamente próximo; Petrol Inj. é resposta medida, não parte da chave de região.
- Causalidade exige ACK + readback, uma única atuação, linhagem de revisão válida, mesma região física e o mesmo endereço do atuador.
- Mapa K só atribui ganho quando a mesma célula física participou antes/depois; Curva K só atribui ganho ao ponto participante.
- Ledger novo é v2 e dados v1 sem identidade confiável falham fechados.
- Evidência gasolina permanece reutilizável entre épocas; evidência GNV pertence à revisão em que foi coletada.

### OBD
- Learner autônomo: somente 010C/RPM, 010B/MAP e 0106/STFT em GNV explicitamente declarado.
- Ciclos duplicados, stale, sessão/época incorretas e ciclos iniciados antes de uma rotação são rejeitados.
- Witness só pode aumentar confiança MP48 se READY, GNV, fresco e coerente com sessão, época, calibração e região física.
- OBD nunca altera o erro MP48. MP48 pode servir apenas como ponte opcional de Petrol Inj. para endereçar Mapa K.
- Endereço físico do Mapa K: linha = Petrol Inj.; coluna = RPM.
- Sem readback/endereço confiável a porcentagem OBD pode existir, mas a proposta de escrita abstém. Nunca há escrita automática.

## Fluidez e telemetria
- Freshness (revision/sequence/age) deve avançar mesmo quando os valores visuais arredondados não mudarem.
- Scheduler de UI é self-paced com `setTimeout`; não pode acumular callbacks como `setInterval`.
- Persistência OBD é coalescida fora do callback quente; snapshot de estado é copiado sob lock curto e serializado fora dele.
- Overlay/notificação são coalescidos/limitados antes de construir snapshots pesados.
- Tela OBD usa endpoint estreito de witness/status, não `fullSnapshot` periódico.

## Consumo
- `ConsumptionEvidenceEngine` é puro.
- Economia confirmada só nasce de `distanceKm / addedM3` válidos.
- Pressão gera somente estimativa de volume com origem, qualidade e incerteza explícitas.
- Timestamp regressivo é rejeitado.
- Baseline pré-abastecimento não é sobrescrito durante atualizações normais.

## Segurança e limites
- Preparar/revisar proposta não escreve ECU.
- Escrita continua manual e dependente dos gates existentes de serviço, USB, ECU, confirmação humana, ACK e readback.
- CI prova software, não veículo, ELM universal, economia real, soak/ANR, bateria ou API 26–35 física.
