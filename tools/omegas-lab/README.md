# Dinamômetro Ômegas · workflow rápido

## Objetivo
Laboratório Android independente baseado no PR #195 da interface adaptativa do Ômegas. O S20 FE e o Oppo A40 podem ter a mesma base de código. O **Oppo é o construtor local preferencial** quando o S20 estiver sendo usado para Uber, sem interferir no app em primeiro plano.

## Uso no Termux
```bash
bash tools/omegas-lab/fast-build.sh status
bash tools/omegas-lab/fast-build.sh graph "O que chama TelemetryHub?"
bash tools/omegas-lab/fast-build.sh ui
bash tools/omegas-lab/fast-build.sh hot
bash tools/omegas-lab/fast-build.sh qa
```

`hot` usa o Gradle 8.9 do wrapper, os caches já baixados, SDK Android 35 e o `aapt2` ARM64 do Termux; **somente compila**. `qa` executa os testes JavaScript, JUnit Android e monta o APK. `OMEGAS_OFFLINE=1` desativa a rede do Gradle quando as dependências já estiverem no cache. Logs ficam em `~/.cache/omegas_workflow/builds/`.

Não executar `am start`, abrir a interface, instalar silenciosamente por padrão ou solicitar permissão que interrompa o motorista. Para instalar uma compilação de laboratório em cada aparelho, usar sua **mesma assinatura privada preexistente** do laboratório daquele aparelho, nunca uma chave descartável por build. A chave de assinatura nunca vai ao GitHub.

## Contrato rigoroso com o veículo
**Zero transmissão ao OBD, adaptador ELM327 ou MP48**, incluindo `AT*`, `01xx`, handshake e consulta `48 01`. Recepção passiva de um produtor já existente é permitida; se não houver produtor, mostrar “sem telemetria”, não inventar leitura. Não alterar a ECU, nem limpar DTC, nem gravar curva K. Os leitores experimentais antigos permanecem isolados e desativados pela interface.

A detecção da porta deve observar a lista **ativa** do `UsbManager.deviceList`/host manager Android em cada aparelho, não os `vendor_id` constantes dos filtros de atividades de apps no `dumpsys usb`.

## Conhecimento e continuidade
- Fonte de verdade do código: branch `lab/dinamometro-omegas-20261121`, isolada de `OMEGASCANONICO` e PR #195.
- Grafo do código: `graphify-out/graph.json` local; antes de pesquisar arquivos, `graphify query`. A CI `dyno-graphify.yml` gera o índice AST auditável; sua cópia nos aparelhos deve ser renovada após mudanças de código. Não executar extração local pesada enquanto as bibliotecas ARM64 não estiverem prontas.
- Vault do app: SQLite privado versionado, persistência por atualização normal de mesmo `applicationId` e certificado, exportação ZIP. **Sincronização criptografada S20↔Oppo ainda não implementada**; não pressupor compartilhamento de dados só porque os dois aparelhos têm código igual.
- Curva K: apenas recomendações comprovadas, com sensor fresco, regime equivalente e estado do combustível verificado. Evidência insuficiente = abstenção.
- Integração oficial do Ômegas somente após testes físicos e revisão; nunca mesclar um experimento não validado diretamente.

## Atualizações e segurança
As compilações experimentais no GitHub Actions são usadas para QA final e artefatos reproduzíveis. O workflow do APK está filtrado por `dyno/**` e arquivos de build; o Graphify roda em código/documentação quando necessário. **Não** enviar o banco de telemetria privado ao repositório ou ao índice público.
