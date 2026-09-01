# Status — OMEGAS V8.2 RED Science Blend

- Issue: #11
- Branch alvo: `work/red-v82-science-blend`
- Branch verificada: `work/red-v82-global-evidence-fix`
- PR: #12
- SHA de produto verificado: `54392b43c4773d9f167ce9d3f8d00c86ababaf85`
- GitHub Actions run: `33471553191` — SUCCESS
- Identidade: `OMEGAS V8.2 RED` / `8.2.0-red` / versionCode `820`
- APK SHA-256: `214a49e51f3b59aac033d5106a2e6e2519df2c85cca4fe86e6cf46907d8f8180`
- Artifact ID: `9786741545`
- Artifact digest: `sha256:1ec49fc78f6d13f06a7fc9abc7cd843ccf249cbe69214fcc2a30a1debaefde87`
- AUTO_WRITE_ECU: `false`
- P_IMPROVE_PROVEN: `false`
- VEHICLE_PROVEN: `false`

## Provado neste checkpoint

1. Sugestão global exige pelo menos 2 visitas e 2 regiões físicas RPM×MAP.
2. A região física é derivada dos eixos canônicos RPM×MAP, não de um ID de referência.
3. Evidência global não aparece como sugestão local.
4. AutoCal possui painel alcançável e leitura nativa; nenhuma ação de escrita foi exposta.
5. Aprender mostra primeiro gasolina esperada, GNV observado, diferença aprendida, situação e ação.
6. Procedência, massa e predição permanecem auditáveis em “Detalhes técnicos”.
7. Alvos de toque têm 48 px e existe adaptação explícita para 720 px.
8. A identidade não é mais “V8.0 TEST”; o applicationId foi preservado para atualização do app instalado.
9. Todos os testes JavaScript existentes passaram.
10. Todos os contratos Python existentes passaram.
11. Todos os 420 testes Android passaram e o APK foi montado no mesmo SHA.

## Falsificado/corrigido

- `reference_region_id` não prova região física independente; a coordenada RPM×MAP agora é a autoridade.
- O botão AutoCal antigo não abria uma tela funcional.
- A aba Aprender expunha tolerâncias e linguagem interna como navegação primária.
- O contrato de governança ainda apontava para V8.0, Issue #9 e branch antiga.

## Ainda desconhecido

- Economia real de combustível.
- Ganho de dirigibilidade.
- Estabilidade do novo fluxo no veículo.
- Superioridade held-out de qualquer novo Predictor.

Nenhum desses quatro itens pode ser promovido sem teste específico no carro ou avaliação cega aplicável.
