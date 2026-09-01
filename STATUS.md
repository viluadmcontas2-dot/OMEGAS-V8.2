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


## Completion checkpoint — 2026-09-01

- Branch de revisão: `work/red-v82-completion`
- PR: #13
- SHA de produto verificado: `e3e40ed2cf50231020c9620542d83adc6d5bbcbf`
- GitHub Actions: `33498106165` — SUCCESS
- APK SHA-256: `602dc87990953c637a73f70e8101020363a7f2d8bf0754aae8e375861d56b3d1`
- Artifact ID: `9796619524`
- Artifact digest: `sha256:6831f7228538411bee94cb540ed9610d93a6d882f952c802e9984be5dd880051`

### Corrigido e provado

1. Combustível deixa de permanecer `DESCONHECIDO` quando o código bruto não é um dos três códigos canônicos, mas o próprio frame contém pulso físico inequívoco.
2. Códigos `0x80/0x88/0x90` continuam prioritários; o byte bruto é preservado e a origem fica auditável em `fuel_source`.
3. Sem código canônico e sem pulso físico, o estado continua `UNKNOWN`; não existe chute nem forward-fill.
4. Todas as oito rotas foram renderizadas em Chrome a 1280×720. O gate falhou inicialmente em quatro alvos de 38/40/42 px; todos foram corrigidos para o mínimo operacional e o gate ficou GREEN.
5. O Predictor explica em linguagem operacional de onde veio a estimativa, quantas passagens e leituras nativas a sustentam e qual é o próximo passo. A matemática e a proibição de escrita automática permaneceram intactas.

### Limites honestos

- A correção de combustível está provada por protocolo/testes e ainda exige confirmação física no veículo.
- O gate de UI prova layout renderizado em 1280×720 no browser do CI; legibilidade e ergonomia finais ainda exigem observação na central real.
- Nenhum novo algoritmo de Predictor foi promovido: os candidatos Gaussian/híbridos anteriores não provaram ganho held-out.
- `P_IMPROVE_PROVEN=false`, `VEHICLE_PROVEN=false`, `AUTO_WRITE_ECU=false`.


### Follow-up de preservação de evidência

- SHA final de produto: `1e19be36533471375491df2f34580bb8e7ecf222`
- GitHub Actions: `33498784976` — SUCCESS
- APK SHA-256 final: `ecb043ac01314b5afcdc85420d02f80f216e9920e3d5a5484c654c6c81c23a2a`
- Artifact ID: `9796915806`
- Artifact digest: `sha256:d3022cda4d47dc643c207f6db5519ae3b1eb3a1f21bcd58e848f6be86a1918f7`
- `map_raw` foi restaurado e protegido por teste após revisão do diff; nenhum campo bruto de MAP é perdido no JSON.
