# Product Contract — OMEGAS Amarelo

## Missão
Construir uma nova linha OMEGAS centrada no AutoCAL nativo da ECU, telemetria leve e aprendizado local cientificamente consistente.

## AutoCAL
`AutoCal` significa o mecanismo nativo da ECU salvo qualificação explícita. O aplicativo observa/orquestra o protocolo comprovado, projeta estado e melhora a experiência; não inventa uma segunda autoridade matemática.

## Estrutura científica
- Gasolina canônica: superfície de referência `RPM × MAP -> petrol_ms esperado`.
- Curva K: componente global nativa/observada.
- Mapa K: 12 × 12 = 144 nós fixos `RPM × Petrol Inj.`.
- Evidência entre nós é interpolada; coordenadas dos nós são imutáveis.
- Sessão USB é metadado operacional, nunca voto científico.
- Erro global é removido antes de estimar residual local.
- Support mass e independência estatística são conceitos separados.
- Diferença instantânea não é estado estável.
- Sugestão exige evidência consolidada e permanece manual.

## Produto separado
Não existe obrigação de compatibilidade arquitetural interna com OmegasVerde. Compatibilidade só é preservada quando serve comportamento, protocolo, dados ou experiência comprovados.

## Performance
Arquitetura deve respeitar a cadência observada no ProgBase:
- live MP48 de alta frequência;
- vetores AutoCAL em ciclo mais lento;
- parsing/ciência fora do hot path visual;
- projeção incremental;
- snapshot coerente;
- UI automotiva 1280×720.

## Release
E2E real-log replay é obrigatório. APK, instalação e validação física são gates separados.