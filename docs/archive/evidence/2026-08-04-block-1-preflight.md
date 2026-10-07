# Evidência preventiva — Bloco 1

## Alvo
- Repositório: `felipetbestkkj-ship-it/OMEGAS-V7`
- Branch: `rebuild/ux-9in-dual-layout`
- Base comparada: `5f0d74c1de7c3905d7635a33657497560915f2a0`
- Escopo: sessão, telemetria, uso contínuo, governança e regressões relacionadas.

## Inspeção de produtores e consumidores
- `HubJavascriptBridge.getStatus()` já fornece serviço, conexão, permissão USB, engine travado, RPM, erro e idade da telemetria.
- `MainActivity` mantém WebView, serviço e evento `omegas-refresh`.
- O Bloco 1 não alterou Kotlin, serviço, USB, protocolo, aprendizado, persistência ou writer.
- A interface continua com um único `setInterval`.
- `startKBatchWrite` permanece fora de `app.js` e só é alcançado pelo fluxo manual de revisão.

## Verificação local focada
Ambiente parcial de preparação, sem o repositório Android completo:

- `node --check` em `app.js` e `session-state.js`: aprovado;
- 21 testes JavaScript integrados: 21 aprovados, 0 falhas;
- contrato Python focado do Bloco 1: 6 aprovados, 0 falhas;
- matriz de sessão: 500 combinações determinísticas;
- seleção 1–16, bloqueio da 17ª, lote único, writer sem falso sucesso e linha técnica protegida preservados.

## Falha preventiva encontrada
Durante a revisão do diff, foi detectado que uma substituição integral de `app.js` poderia remover uma proteção remota recente contra mapa antigo após falha de releitura.

## Correção preventiva
- proteção restaurada no limite do modelo;
- início de leitura, falha de leitura e desconexão invalidam o mapa anterior;
- confirmação é desmarcada e escrita fica bloqueada;
- regressão específica adicionada;
- cenários completos do shell restaurados;
- `SKILLS.md` passou a proibir sobrescrita integral baseada em leitura parcial.

## Estado da evidência
- Verificação focada local: `PASSOU AUTOMATIZADO` para o conjunto executado.
- CI remota do commit final: ainda sem status consultável no momento desta evidência.
- Gradle, lint, APK, emulador, celular, multimídia e veículo: não confirmados nesta evidência.

## Risco residual
- `condução provável` é inferida por RPM, não por velocidade real.
- o guardião de invalidação precisa ser exercitado também pela CI final e pelo emulador.
- USB físico, ACK e readback continuam dependentes de aparelho e ECU.
